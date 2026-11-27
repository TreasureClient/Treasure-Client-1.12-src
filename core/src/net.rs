//! Web requests and file downloads. Every download is written to a ".part" file, checked
//! (size and SHA-1 when known) and only then renamed into place, so a broken download never
//! looks finished. Several files download at once.

use std::collections::HashSet;
use std::fs;
use std::io::{Read, Write};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, AtomicUsize, Ordering};
use std::sync::{Mutex, OnceLock};
use std::time::{Duration, Instant};

use anyhow::{bail, Context, Result};

use crate::{report, util, ProgressFn};

/// Identifies the launcher to Mojang, Modrinth etc. (Modrinth asks every app to send one.)
pub const USER_AGENT: &str = concat!("TreasureLauncher/", env!("CARGO_PKG_VERSION"), " (+https://treasureclient.github.io)");

static AGENT: OnceLock<ureq::Agent> = OnceLock::new();
static THREADS: AtomicUsize = AtomicUsize::new(8);

pub fn agent() -> &'static ureq::Agent {
    AGENT.get_or_init(|| {
        ureq::Agent::config_builder()
            .user_agent(USER_AGENT)
            .timeout_connect(Some(Duration::from_secs(20)))
            .timeout_recv_response(Some(Duration::from_secs(60)))
            .timeout_recv_body(Some(Duration::from_secs(600)))
            .build()
            .into()
    })
}

/// How many files download at the same time.
pub fn set_threads(n: usize) {
    THREADS.store(n.clamp(1, 32), Ordering::Relaxed);
}

fn permanent(e: &anyhow::Error) -> bool {
    matches!(e.downcast_ref::<ureq::Error>(), Some(ureq::Error::StatusCode(c)) if (400..500).contains(c) && *c != 429 && *c != 408)
}

/// Tries a few times with a growing pause, except for errors that won't fix themselves (404 etc.).
pub fn retry<T>(mut f: impl FnMut() -> Result<T>) -> Result<T> {
    let mut last = None;
    for i in 0..4 {
        match f() {
            Ok(v) => return Ok(v),
            Err(e) => {
                if permanent(&e) {
                    return Err(e);
                }
                last = Some(e);
                if i < 3 {
                    std::thread::sleep(Duration::from_millis(600 << i));
                }
            }
        }
    }
    Err(last.unwrap())
}

pub fn get_text(url: &str) -> Result<String> {
    retry(|| {
        let mut r = agent().get(url).call()?;
        Ok(r.body_mut().with_config().limit(64 << 20).read_to_string()?)
    })
    .with_context(|| format!("couldn't load {url}"))
}

pub fn get_json<T: serde::de::DeserializeOwned>(url: &str) -> Result<T> {
    let t = get_text(url)?;
    serde_json::from_str(&t).with_context(|| format!("unexpected answer from {url}"))
}

pub fn post_json<T: serde::de::DeserializeOwned>(url: &str, body: &serde_json::Value) -> Result<T> {
    let body = body.to_string();
    let t = retry(|| {
        let mut r = agent().post(url).header("Content-Type", "application/json").header("Accept", "application/json").send(body.as_str())?;
        Ok(r.body_mut().with_config().limit(64 << 20).read_to_string()?)
    })
    .with_context(|| format!("couldn't reach {url}"))?;
    serde_json::from_str(&t).with_context(|| format!("unexpected answer from {url}"))
}

/// One file to download.
#[derive(Clone, Debug)]
pub struct Download {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
    pub executable: bool,
}

/// How carefully to check files that are already there.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Check {
    /// The file exists and has the right size (fast, used before every launch).
    Quick,
    /// Also re-check the SHA-1 of every file ("Repair").
    Full,
}

impl Download {
    pub fn new(url: impl Into<String>, path: PathBuf, sha1: Option<String>, size: Option<u64>) -> Self {
        Download { url: url.into(), path, sha1: sha1.filter(|s| util::is_sha1(s)), size, executable: false }
    }

    pub fn present(&self, check: Check) -> bool {
        let Ok(m) = fs::metadata(&self.path) else { return false };
        if !m.is_file() || self.size.is_some_and(|s| s != m.len()) {
            return false;
        }
        match (&self.sha1, check) {
            (Some(sha), Check::Full) => util::sha1_file(&self.path).map(|h| h.eq_ignore_ascii_case(sha)).unwrap_or(false),
            _ => true,
        }
    }
}

fn part_path(p: &Path) -> PathBuf {
    let mut s = p.as_os_str().to_owned();
    s.push(".part");
    PathBuf::from(s)
}

fn fetch(d: &Download, on_bytes: &dyn Fn(u64)) -> Result<()> {
    if let Some(parent) = d.path.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = part_path(&d.path);
    let mut resp = agent().get(&d.url).call()?;
    let mut reader = resp.body_mut().with_config().limit(u64::MAX).reader();
    let mut f = fs::File::create(&tmp).with_context(|| format!("couldn't write {}", tmp.display()))?;
    let mut h = sha1_smol::Sha1::new();
    let mut n: u64 = 0;
    let mut buf = vec![0u8; 64 * 1024];
    let res: Result<()> = (|| {
        loop {
            let k = reader.read(&mut buf)?;
            if k == 0 {
                break;
            }
            h.update(&buf[..k]);
            f.write_all(&buf[..k])?;
            n += k as u64;
            on_bytes(k as u64);
        }
        f.flush()?;
        Ok(())
    })();
    drop(f);
    let check = res.and_then(|_| {
        if let Some(s) = d.size {
            if s != n {
                bail!("{} came out as {n} bytes instead of {s}", d.url);
            }
        }
        if let Some(sha) = &d.sha1 {
            if !h.digest().to_string().eq_ignore_ascii_case(sha) {
                bail!("{} failed its checksum", d.url);
            }
        }
        Ok(())
    });
    if let Err(e) = check {
        let _ = fs::remove_file(&tmp);
        return Err(e);
    }
    #[cfg(unix)]
    if d.executable {
        use std::os::unix::fs::PermissionsExt;
        fs::set_permissions(&tmp, fs::Permissions::from_mode(0o755))?;
    }
    fs::rename(&tmp, &d.path).with_context(|| format!("couldn't write {}", d.path.display()))?;
    Ok(())
}

/// Downloads one file (with retries), skipping it if it's already there.
pub fn download(d: &Download) -> Result<()> {
    if d.present(Check::Quick) {
        return Ok(());
    }
    retry(|| fetch(d, &|_| {})).with_context(|| format!("couldn't download {}", d.url))
}

/// Downloads every missing file in `list`, several at once, reporting progress as `stage`.
pub fn download_all(list: Vec<Download>, check: Check, stage: &str, progress: &ProgressFn) -> Result<()> {
    let mut seen = HashSet::new();
    let todo: Vec<Download> = list.into_iter().filter(|d| seen.insert(d.path.clone())).filter(|d| !d.present(check)).collect();
    if todo.is_empty() {
        return Ok(());
    }
    let total: u64 = todo.iter().map(|d| d.size.unwrap_or(0)).sum();
    let done = AtomicU64::new(0);
    let next = AtomicUsize::new(0);
    let failed: Mutex<Option<anyhow::Error>> = Mutex::new(None);
    let last = Mutex::new(Instant::now() - Duration::from_secs(1));
    let tell = |force: bool| {
        let mut l = last.lock().unwrap();
        if force || l.elapsed() >= Duration::from_millis(120) {
            *l = Instant::now();
            report(progress, stage, done.load(Ordering::Relaxed).min(total), total);
        }
    };
    tell(true);
    let threads = THREADS.load(Ordering::Relaxed).min(todo.len());
    std::thread::scope(|s| {
        for _ in 0..threads {
            s.spawn(|| loop {
                if failed.lock().unwrap().is_some() {
                    break;
                }
                let i = next.fetch_add(1, Ordering::Relaxed);
                let Some(d) = todo.get(i) else { break };
                let r = retry(|| {
                    let got = AtomicU64::new(0);
                    let r = fetch(d, &|k| {
                        got.fetch_add(k, Ordering::Relaxed);
                        done.fetch_add(k, Ordering::Relaxed);
                        tell(false);
                    });
                    if r.is_err() {
                        done.fetch_sub(got.load(Ordering::Relaxed), Ordering::Relaxed);
                    }
                    r
                });
                if let Err(e) = r {
                    let mut f = failed.lock().unwrap();
                    if f.is_none() {
                        *f = Some(e.context(format!("couldn't download {}", d.url)));
                    }
                    break;
                }
            });
        }
    });
    if let Some(e) = failed.into_inner().unwrap() {
        return Err(e);
    }
    tell(true);
    Ok(())
}

/// The total size of the files in `list` that still need downloading.
pub fn missing_bytes(list: &[Download]) -> (usize, u64) {
    let mut seen = HashSet::new();
    list.iter()
        .filter(|d| seen.insert(d.path.clone()) && !d.present(Check::Quick))
        .fold((0, 0), |(n, b), d| (n + 1, b + d.size.unwrap_or(0)))
}
