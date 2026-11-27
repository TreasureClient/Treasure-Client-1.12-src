//! Builds the Java command line for a version and starts the game.
//!
//! The access token is on the command line (that's how Minecraft receives it, the official
//! launcher does the same), so it's cut out of anything we print or log.

use std::collections::HashMap;
use std::fs;
use std::io::{BufRead, BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::{Child, Command, Stdio};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};

use anyhow::{Context, Result};

use crate::install::{self, Resolved};
use crate::instance::{self, Instance};
use crate::mojang::{self, Features};
use crate::{paths, VERSION};

/// Who is playing.
#[derive(Clone, Debug)]
pub struct Auth {
    pub name: String,
    pub uuid: String,
    pub access_token: String,
    pub xuid: String,
    pub client_id: String,
}

/// Start straight into a world or server (Minecraft 1.20 and newer).
#[derive(Clone, Debug, Default)]
pub struct LaunchOptions {
    pub server: Option<String>,
    pub world: Option<String>,
}

#[derive(Clone, Debug)]
pub struct LaunchCommand {
    pub java: PathBuf,
    pub args: Vec<String>,
    pub cwd: PathBuf,
    secrets: Vec<String>,
}

impl LaunchCommand {
    /// The command as text with the access token replaced by <hidden>.
    pub fn redacted(&self) -> String {
        let mut parts = vec![self.java.to_string_lossy().into_owned()];
        parts.extend(self.args.iter().map(|a| if a.contains(' ') { format!("\"{a}\"") } else { a.clone() }));
        hide(&parts.join(" "), &self.secrets)
    }
}

fn hide(s: &str, secrets: &[String]) -> String {
    let mut s = s.to_string();
    for sec in secrets {
        if sec.len() >= 8 && s.contains(sec.as_str()) {
            s = s.replace(sec.as_str(), "<hidden>");
        }
    }
    s
}

/// Replaces every ${name} in `arg` that `vars` knows.
fn fill(arg: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = String::with_capacity(arg.len());
    let mut rest = arg;
    while let Some(i) = rest.find("${") {
        out.push_str(&rest[..i]);
        match rest[i..].find('}') {
            Some(j) => {
                let key = &rest[i + 2..i + j];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => out.push_str(&rest[i..i + j + 1]),
                }
                rest = &rest[i + j + 1..];
            }
            None => {
                out.push_str(&rest[i..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

pub fn build(inst: &Instance, res: &Resolved, java: &Path, auth: &Auth, opts: &LaunchOptions, memory_mb: u32) -> Result<LaunchCommand> {
    let game_dir = instance::game_dir(&inst.id)?;
    let natives = install::natives_dir(&res.version_name);
    let sep = if cfg!(windows) { ";" } else { ":" };
    let p = |x: &Path| x.to_string_lossy().into_owned();
    let cp = res.classpath.iter().map(|x| p(x)).collect::<Vec<_>>().join(sep);
    let feats = Features {
        custom_resolution: inst.width.is_some() && inst.height.is_some(),
        quick_play_multiplayer: opts.server.is_some(),
        quick_play_singleplayer: opts.world.is_some() && opts.server.is_none(),
    };
    let token = if auth.access_token.is_empty() { "0".to_string() } else { auth.access_token.clone() };
    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", auth.name.clone()),
        ("version_name", res.version_name.clone()),
        ("game_directory", p(&game_dir)),
        ("assets_root", p(&paths::assets())),
        ("game_assets", p(&paths::assets())),
        ("assets_index_name", res.assets_index.clone()),
        ("auth_uuid", auth.uuid.clone()),
        ("auth_access_token", token.clone()),
        ("auth_session", format!("token:{token}:{}", auth.uuid)),
        ("clientid", auth.client_id.clone()),
        ("auth_xuid", auth.xuid.clone()),
        ("user_type", "msa".into()),
        ("user_properties", "{}".into()),
        ("version_type", res.version.kind.clone().unwrap_or_else(|| "release".into())),
        ("natives_directory", p(&natives)),
        ("launcher_name", "treasure-launcher".into()),
        ("launcher_version", VERSION.into()),
        ("classpath", cp),
        ("classpath_separator", sep.into()),
        ("library_directory", p(&paths::libraries())),
        ("resolution_width", inst.width.unwrap_or(1280).to_string()),
        ("resolution_height", inst.height.unwrap_or(720).to_string()),
        ("quickPlayMultiplayer", opts.server.clone().unwrap_or_default()),
        ("quickPlaySingleplayer", opts.world.clone().unwrap_or_default()),
        ("quickPlayPath", p(&instance::dir(&inst.id)?.join("quickPlay.json"))),
    ]);

    let mut args = vec![format!("-Xmx{memory_mb}M"), "-Xms512M".to_string()];
    if let Some(extra) = &inst.jvm_args {
        args.extend(extra.split_whitespace().map(String::from));
    }
    let jvm = res.version.arguments.as_ref().map(|a| mojang::expand_args(&a.jvm, &feats)).filter(|a| !a.is_empty());
    match jvm {
        Some(a) => args.extend(a),
        None => args.extend(["-Djava.library.path=${natives_directory}".to_string(), "-cp".into(), "${classpath}".into()]),
    }
    if let Some(l) = &res.log_arg {
        args.push(l.clone());
    }
    args.push(res.version.main_class.clone().context("the version file has no main class")?);
    match res.version.arguments.as_ref().map(|a| mojang::expand_args(&a.game, &feats)).filter(|a| !a.is_empty()) {
        Some(a) => args.extend(a),
        None => args.extend(res.version.minecraft_arguments.clone().unwrap_or_default().split_whitespace().map(String::from)),
    }
    let args = args.iter().map(|a| fill(a, &vars)).collect();
    Ok(LaunchCommand { java: java.to_path_buf(), args, cwd: game_dir, secrets: vec![auth.access_token.clone()] })
}

/// A running game.
#[derive(Clone)]
pub struct Game {
    child: Arc<Mutex<Child>>,
    pub pid: u32,
}

impl Game {
    pub fn stop(&self) {
        let _ = self.child.lock().unwrap().kill();
    }

    pub fn running(&self) -> bool {
        matches!(self.child.lock().unwrap().try_wait(), Ok(None))
    }
}

pub type LineFn = Arc<dyn Fn(&str, bool) + Send + Sync>;

/// Starts the game. `on_line` gets every line it prints (true = error output) and `on_exit` gets
/// the exit code and how many seconds it ran.
pub fn spawn(cmd: &LaunchCommand, on_line: LineFn, on_exit: Box<dyn FnOnce(Option<i32>, u64) + Send>) -> Result<Game> {
    fs::create_dir_all(&cmd.cwd)?;
    let mut c = Command::new(&cmd.java);
    c.args(&cmd.args).current_dir(&cmd.cwd).stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        c.creation_flags(0x0800_0000); // no console window
    }
    let mut child = c.spawn().with_context(|| format!("couldn't start Java ({})", cmd.java.display()))?;
    let streams: [(Option<Box<dyn Read + Send>>, bool); 2] = [
        (child.stdout.take().map(|s| Box::new(s) as Box<dyn Read + Send>), false),
        (child.stderr.take().map(|s| Box::new(s) as Box<dyn Read + Send>), true),
    ];
    for (s, err) in streams {
        let Some(s) = s else { continue };
        let cb = on_line.clone();
        let secrets = cmd.secrets.clone();
        std::thread::spawn(move || {
            let mut r = BufReader::new(s);
            let mut buf = Vec::new();
            loop {
                buf.clear();
                match r.read_until(b'\n', &mut buf) {
                    Ok(0) | Err(_) => break,
                    Ok(_) => {
                        let line = String::from_utf8_lossy(&buf);
                        cb(&hide(line.trim_end(), &secrets), err);
                    }
                }
            }
        });
    }
    let pid = child.id();
    let child = Arc::new(Mutex::new(child));
    let watch = child.clone();
    let started = Instant::now();
    std::thread::spawn(move || loop {
        let st = watch.lock().unwrap().try_wait();
        match st {
            Ok(Some(s)) => {
                on_exit(s.code(), started.elapsed().as_secs());
                break;
            }
            Ok(None) => std::thread::sleep(Duration::from_millis(400)),
            Err(_) => {
                on_exit(None, started.elapsed().as_secs());
                break;
            }
        }
    });
    Ok(Game { child, pid })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn filling() {
        let v = HashMap::from([("a", "1".to_string()), ("bb", "two".to_string())]);
        assert_eq!(fill("x${a}y${bb}z${c}", &v), "x1ytwoz${c}");
        assert_eq!(fill("${a", &v), "${a");
        assert_eq!(hide("--accessToken abcdefghijk", &["abcdefghijk".into()]), "--accessToken <hidden>");
    }
}
