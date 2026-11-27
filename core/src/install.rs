//! Works out every file a version needs and downloads the missing ones.

use std::fs;
use std::io::Read;
use std::path::PathBuf;

use anyhow::{bail, Context, Result};

use crate::instance::{self, Instance, Loader};
use crate::mojang::{self, AssetIndex, Features, VersionJson};
use crate::net::{self, Check, Download};
use crate::{fabric, java, paths, report, settings, treasure, util, ProgressFn};

/// Everything needed to start one version.
#[derive(Clone, Debug)]
pub struct Resolved {
    pub version: VersionJson,
    /// "26.3" or "fabric-loader-0.17.2-26.3"
    pub version_name: String,
    pub mc: String,
    pub client_jar: PathBuf,
    pub classpath: Vec<PathBuf>,
    /// Old versions only: jars whose native files get unpacked before launch (with excluded folders).
    pub native_jars: Vec<(PathBuf, Vec<String>)>,
    pub downloads: Vec<Download>,
    pub assets_index: String,
    pub log_arg: Option<String>,
    pub java_component: String,
    pub java_major: u32,
}

pub fn natives_dir(version_name: &str) -> PathBuf {
    paths::versions().join(version_name).join("natives")
}

/// Reads the version files (Mojang's, plus Fabric's when `fabric_loader` is set) and lists every file.
pub fn resolve(mc: &str, fabric_loader: Option<&str>) -> Result<Resolved> {
    let vanilla = mojang::vanilla(mc)?;
    let client = vanilla.downloads.as_ref().and_then(|d| d.get("client")).cloned().with_context(|| format!("Minecraft {mc} has no game download"))?;
    let v = match fabric_loader {
        Some(l) => mojang::merge(fabric::profile(mc, l)?, vanilla),
        None => vanilla,
    };
    let client_jar = paths::versions().join(mc).join(format!("{mc}.jar"));
    let mut downloads = vec![Download::new(client.url.clone(), client_jar.clone(), client.sha1.clone(), client.size)];
    let feats = Features::default();
    let mut classpath = Vec::new();
    let mut native_jars = Vec::new();
    let arch = if cfg!(target_pointer_width = "64") { "64" } else { "32" };
    for lib in &v.libraries {
        if !mojang::rules_allow(lib.rules.as_ref(), &feats) {
            continue;
        }
        if let Some(cls) = lib.natives.as_ref().and_then(|n| n.get(util::os_name())) {
            let cls = cls.replace("${arch}", arch);
            if let Some(f) = lib.downloads.as_ref().and_then(|d| d.classifiers.as_ref()).and_then(|c| c.get(&cls)) {
                let rel = f.path.clone().or_else(|| util::maven_path(&format!("{}:{cls}", lib.name)));
                if let Some(path) = rel.and_then(|r| util::safe_join(&paths::libraries(), &r)) {
                    downloads.push(Download::new(f.url.clone(), path.clone(), f.sha1.clone(), f.size));
                    native_jars.push((path, lib.extract.as_ref().map(|e| e.exclude.clone()).unwrap_or_default()));
                }
            }
        }
        let art = lib.downloads.as_ref().and_then(|d| d.artifact.as_ref());
        let (url, rel, sha1, size) = match art {
            Some(a) => (a.url.clone(), a.path.clone().or_else(|| util::maven_path(&lib.name)), a.sha1.clone(), a.size),
            None if lib.natives.is_some() || lib.downloads.is_some() => continue,
            None => {
                let rel = util::maven_path(&lib.name);
                let mut base = lib.url.clone().unwrap_or_else(|| "https://libraries.minecraft.net/".into());
                if !base.ends_with('/') {
                    base.push('/');
                }
                (rel.as_ref().map(|r| format!("{base}{r}")).unwrap_or_default(), rel, lib.sha1.clone(), lib.size)
            }
        };
        let Some(path) = rel.and_then(|r| util::safe_join(&paths::libraries(), &r)) else { continue };
        if !url.is_empty() {
            downloads.push(Download::new(url, path.clone(), sha1, size));
        }
        if !classpath.contains(&path) {
            classpath.push(path);
        }
    }
    classpath.push(client_jar.clone());

    let ai = v.asset_index.clone().with_context(|| format!("Minecraft {mc} has no asset list"))?;
    if !util::plain_name(&ai.id) {
        bail!("bad asset list name");
    }
    let idx_path = paths::assets().join("indexes").join(format!("{}.json", ai.id));
    let idx_dl = Download::new(ai.url.clone(), idx_path.clone(), Some(ai.sha1.clone()), Some(ai.size));
    net::download(&idx_dl)?;
    downloads.push(idx_dl);
    let idx: AssetIndex = util::read_json(&idx_path)?;
    for o in idx.objects.values() {
        if !util::is_sha1(&o.hash) {
            continue;
        }
        let pre = &o.hash[..2];
        downloads.push(Download::new(
            format!("https://resources.download.minecraft.net/{pre}/{}", o.hash),
            paths::assets().join("objects").join(pre).join(&o.hash),
            Some(o.hash.clone()),
            Some(o.size),
        ));
    }

    let mut log_arg = None;
    if let Some(lc) = v.logging.as_ref().and_then(|l| l.client.as_ref()) {
        if util::plain_name(&lc.file.id) {
            let p = paths::assets().join("log_configs").join(&lc.file.id);
            downloads.push(Download::new(lc.file.url.clone(), p.clone(), Some(lc.file.sha1.clone()), Some(lc.file.size)));
            log_arg = Some(lc.argument.replace("${path}", &p.to_string_lossy()));
        }
    }
    let (java_component, java_major) = v.java_version.as_ref().map(|j| (j.component.clone(), j.major_version)).unwrap_or(("jre-legacy".into(), 8));
    Ok(Resolved {
        version_name: v.id.clone(),
        mc: mc.to_string(),
        version: v,
        client_jar,
        classpath,
        native_jars,
        downloads,
        assets_index: ai.id,
        log_arg,
        java_component,
        java_major,
    })
}

/// Downloads the game files and unpacks native files (old versions).
pub fn install_game(res: &Resolved, check: Check, progress: &ProgressFn) -> Result<()> {
    net::download_all(res.downloads.clone(), check, &format!("Downloading Minecraft {}", res.mc), progress)?;
    if !res.native_jars.is_empty() {
        let dir = natives_dir(&res.version_name);
        fs::create_dir_all(&dir)?;
        for (jar, exclude) in &res.native_jars {
            let mut z = zip::ZipArchive::new(fs::File::open(jar)?).with_context(|| format!("{} is damaged", jar.display()))?;
            for i in 0..z.len() {
                let mut f = z.by_index(i)?;
                if f.is_dir() || exclude.iter().any(|e| f.name().starts_with(e.as_str())) {
                    continue;
                }
                let Some(rel) = f.enclosed_name() else { continue };
                let out = dir.join(rel);
                if let Some(p) = out.parent() {
                    fs::create_dir_all(p)?;
                }
                let mut buf = Vec::new();
                f.read_to_end(&mut buf)?;
                fs::write(out, buf)?;
            }
        }
    }
    Ok(())
}

/// Gets an instance completely ready to start: its modpack, Fabric, Treasure Client, the game
/// files and Java. Returns what `launch` needs.
pub fn prepare(id: &str, check: Check, progress: &ProgressFn) -> Result<(Instance, Resolved, PathBuf)> {
    let mut inst = instance::get(id)?;
    let s = settings::load();
    net::set_threads(s.downloads_at_once as usize);
    // 1. a Treasure Pack instance gets its pack the first time it's played
    if inst.pack.as_ref().is_some_and(|p| p.kind == "treasure-pack" && !p.installed) {
        treasure::install_pack(&mut inst, progress)?;
    }
    // 2. pick a Fabric Loader once (so updates never change it under you)
    if inst.loader == Loader::Fabric && inst.loader_version.is_none() {
        report(progress, "Getting Fabric", 0, 0);
        inst.loader_version = Some(fabric::latest_loader(&inst.mc_version)?);
        instance::save(&inst)?;
    }
    // 3. Treasure Client + Fabric API, if this instance wants them and doesn't have them
    if inst.treasure_client && !treasure::has_tc(&inst)? {
        treasure::install_tc(&inst, progress)?;
    }
    // 4. the game and Java
    report(progress, "Checking game files", 0, 0);
    let res = resolve(&inst.mc_version, if inst.loader == Loader::Fabric { inst.loader_version.as_deref() } else { None })?;
    install_game(&res, check, progress)?;
    let custom = inst.java_path.clone().or(s.java_path).filter(|p| !p.trim().is_empty());
    let java = match custom {
        Some(p) => {
            let p = PathBuf::from(p.trim());
            if !p.is_file() {
                bail!("the Java set in Settings ({}) doesn't exist", p.display());
            }
            p
        }
        None => java::ensure(&res.java_component, check, progress)?,
    };
    Ok((inst, res, java))
}
