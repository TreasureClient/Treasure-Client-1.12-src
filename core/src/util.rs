//! Small helpers: hashes, JSON files, safe paths, Maven names.

use std::fs;
use std::io::Read;
use std::path::{Component, Path, PathBuf};

use anyhow::{Context, Result};
use serde::de::DeserializeOwned;
use serde::Serialize;

pub fn sha1_bytes(b: &[u8]) -> String {
    sha1_smol::Sha1::from(b).digest().to_string()
}

pub fn sha1_file(p: &Path) -> Result<String> {
    let mut f = fs::File::open(p)?;
    let mut h = sha1_smol::Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(h.digest().to_string())
}

pub fn is_sha1(s: &str) -> bool {
    s.len() == 40 && s.bytes().all(|b| b.is_ascii_hexdigit())
}

pub fn read_json<T: DeserializeOwned>(p: &Path) -> Result<T> {
    let t = fs::read_to_string(p).with_context(|| format!("couldn't read {}", p.display()))?;
    serde_json::from_str(&t).with_context(|| format!("{} is damaged", p.display()))
}

/// Writes the file next to its final name first and then swaps it in, so a crash never leaves half a file.
pub fn write_bytes(p: &Path, bytes: &[u8]) -> Result<()> {
    if let Some(d) = p.parent() {
        fs::create_dir_all(d)?;
    }
    let tmp = p.with_extension(format!("{}.tmp", p.extension().and_then(|e| e.to_str()).unwrap_or("")));
    fs::write(&tmp, bytes).with_context(|| format!("couldn't write {}", tmp.display()))?;
    fs::rename(&tmp, p).with_context(|| format!("couldn't write {}", p.display()))?;
    Ok(())
}

pub fn write_json<T: Serialize>(p: &Path, v: &T) -> Result<()> {
    write_bytes(p, &serde_json::to_vec_pretty(v)?)
}

/// Joins a relative path from a download list or a modpack onto `base`, refusing anything that
/// could escape it ("../", absolute paths, drive letters, backslashes).
pub fn safe_join(base: &Path, rel: &str) -> Option<PathBuf> {
    if rel.is_empty() || rel.contains('\\') || rel.contains(':') || rel.contains('\0') {
        return None;
    }
    let p = Path::new(rel);
    if p.is_absolute() {
        return None;
    }
    let mut out = base.to_path_buf();
    let mut any = false;
    for c in p.components() {
        match c {
            Component::Normal(s) => {
                out.push(s);
                any = true;
            }
            Component::CurDir => {}
            _ => return None,
        }
    }
    any.then_some(out)
}

/// A plain file name with no folders in it (for files the app asks us to rename or delete).
pub fn plain_name(name: &str) -> bool {
    !name.is_empty() && name != "." && name != ".." && !name.contains(['/', '\\', ':', '\0'])
}

/// "group:artifact:version[:classifier][@ext]" -> "group/as/path/artifact/version/artifact-version[-classifier].ext"
pub fn maven_path(name: &str) -> Option<String> {
    let (coord, ext) = name.split_once('@').unwrap_or((name, "jar"));
    let p: Vec<&str> = coord.split(':').collect();
    if p.len() < 3 || p.iter().any(|s| s.is_empty() || s.contains("..") || s.contains('/')) {
        return None;
    }
    let (g, a, v) = (p[0], p[1], p[2]);
    let file = match p.get(3) {
        Some(c) => format!("{a}-{v}-{c}.{ext}"),
        None => format!("{a}-{v}.{ext}"),
    };
    Some(format!("{}/{a}/{v}/{file}", g.replace('.', "/")))
}

/// "group:artifact[:classifier]", used to spot the same library in two version files.
pub fn lib_key(name: &str) -> String {
    let p: Vec<&str> = name.split('@').next().unwrap_or(name).split(':').collect();
    match p.len() {
        0..=2 => name.to_string(),
        3 => format!("{}:{}", p[0], p[1]),
        _ => format!("{}:{}:{}", p[0], p[1], p[3]),
    }
}

/// Minecraft's name for this operating system.
pub fn os_name() -> &'static str {
    if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

pub fn now() -> u64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// A folder-safe id from a name: "My Bedwars!" -> "my-bedwars".
pub fn slug(name: &str) -> String {
    let mut s = String::new();
    for c in name.chars().flat_map(|c| c.to_lowercase()) {
        if c.is_ascii_alphanumeric() {
            s.push(c);
        } else if (c == '.' || c == '-' || c == '_' || c == ' ') && !s.ends_with('-') && !s.is_empty() {
            s.push('-');
        }
        if s.len() >= 40 {
            break;
        }
    }
    let s = s.trim_end_matches('-').to_string();
    if s.is_empty() {
        "instance".into()
    } else {
        s
    }
}

/// Total size of a folder in bytes (0 if it doesn't exist).
pub fn dir_size(p: &Path) -> u64 {
    let Ok(rd) = fs::read_dir(p) else { return 0 };
    rd.flatten()
        .map(|e| match e.file_type() {
            Ok(t) if t.is_dir() => dir_size(&e.path()),
            Ok(t) if t.is_file() => e.metadata().map(|m| m.len()).unwrap_or(0),
            _ => 0,
        })
        .sum()
}

pub fn copy_dir(from: &Path, to: &Path) -> Result<()> {
    fs::create_dir_all(to)?;
    for e in fs::read_dir(from)?.flatten() {
        let t = e.file_type()?;
        let dest = to.join(e.file_name());
        if t.is_dir() {
            copy_dir(&e.path(), &dest)?;
        } else if t.is_file() {
            fs::copy(e.path(), dest)?;
        }
    }
    Ok(())
}

const B64: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

pub fn base64(data: &[u8]) -> String {
    let mut s = String::with_capacity(data.len().div_ceil(3) * 4);
    for c in data.chunks(3) {
        let n = (c[0] as u32) << 16 | (*c.get(1).unwrap_or(&0) as u32) << 8 | *c.get(2).unwrap_or(&0) as u32;
        s.push(B64[(n >> 18) as usize & 63] as char);
        s.push(B64[(n >> 12) as usize & 63] as char);
        s.push(if c.len() > 1 { B64[(n >> 6) as usize & 63] as char } else { '=' });
        s.push(if c.len() > 2 { B64[n as usize & 63] as char } else { '=' });
    }
    s
}

pub fn unbase64(s: &str) -> Option<Vec<u8>> {
    let mut out = Vec::with_capacity(s.len() / 4 * 3);
    let mut acc = 0u32;
    let mut bits = 0;
    for b in s.bytes() {
        let v = match b {
            b'A'..=b'Z' => b - b'A',
            b'a'..=b'z' => b - b'a' + 26,
            b'0'..=b'9' => b - b'0' + 52,
            b'+' | b'-' => 62,
            b'/' | b'_' => 63,
            b'=' | b'\n' | b'\r' | b' ' => continue,
            _ => return None,
        } as u32;
        acc = acc << 6 | v;
        bits += 6;
        if bits >= 8 {
            bits -= 8;
            out.push((acc >> bits) as u8);
        }
    }
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn maven() {
        assert_eq!(maven_path("net.fabricmc:fabric-loader:0.17.2").unwrap(), "net/fabricmc/fabric-loader/0.17.2/fabric-loader-0.17.2.jar");
        assert_eq!(maven_path("org.lwjgl:lwjgl:3.3.3:natives-linux").unwrap(), "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar");
        assert_eq!(maven_path("a.b:c:1@zip").unwrap(), "a/b/c/1/c-1.zip");
        assert!(maven_path("a:../../x:1").is_none());
        assert_eq!(lib_key("org.lwjgl:lwjgl:3.3.3:natives-linux"), "org.lwjgl:lwjgl:natives-linux");
        assert_eq!(lib_key("org.ow2.asm:asm:9.8"), "org.ow2.asm:asm");
    }

    #[test]
    fn safe_paths() {
        let b = Path::new("/base");
        assert_eq!(safe_join(b, "mods/a.jar").unwrap(), Path::new("/base/mods/a.jar"));
        assert!(safe_join(b, "../a").is_none());
        assert!(safe_join(b, "mods/../../a").is_none());
        assert!(safe_join(b, "/etc/passwd").is_none());
        assert!(safe_join(b, "C:/x").is_none());
        assert!(safe_join(b, "mods\\..\\x").is_none());
        assert!(safe_join(b, "").is_none());
        assert!(!plain_name("../x") && plain_name("sodium.jar"));
    }

    #[test]
    fn b64() {
        for s in ["", "a", "ab", "abc", "abcd", "hello world!"] {
            assert_eq!(unbase64(&base64(s.as_bytes())).unwrap(), s.as_bytes());
        }
        assert_eq!(base64(b"Man"), "TWFu");
        assert_eq!(slug("My Bedwars!! 26.3"), "my-bedwars-26-3");
    }
}
