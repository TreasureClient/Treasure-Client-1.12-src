#!/usr/bin/env python3
"""
Builds the Treasure Pack: one .mrpack (Modrinth modpack) per Minecraft version.

A .mrpack is a zip with modrinth.index.json (every mod's official Modrinth download link and
its checksums) and an overrides/ folder (copied into the game folder as-is). No other
author's mod is put inside it; launchers download each one from Modrinth. Treasure Client
itself goes in overrides/mods/.

    python3 tools/build_pack.py                 # all versions
    python3 tools/build_pack.py 26.3 1.21.11    # only these
    python3 tools/build_pack.py --check 26.3    # also download every jar to a temp folder and check
                                                # each mod's own "depends" / "breaks" rules

The mod list is pack/mods.txt. Libraries the mods need are added on their own.
Writes builds/pack/Treasure-Pack-<pack>+<mc>.mrpack, a report next to it, and a copy of
the .mrpack in ~/Treasure-Client-mod-jars/.
"""
import hashlib, io, json, os, re, shutil, sys, tempfile, time, urllib.parse, urllib.request, zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VERSIONS = ["1.21.11", "26.1", "26.1.2", "26.2", "26.3"]
PACK_VERSION = "1.0.0"
TC_VERSION = "1.5.0"
UA = "treasure-pack-builder/1.0"
API = "https://api.modrinth.com/v2"
OUT = os.path.join(ROOT, "builds", "pack")
JARS = os.path.expanduser("~/Treasure-Client-mod-jars")


def get(url, tries=4):
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=60) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            if e.code == 429 or e.code >= 500:
                time.sleep(2 + i * 3)
                continue
            raise
        except (urllib.error.URLError, TimeoutError):
            time.sleep(2 + i * 3)
    raise RuntimeError("could not reach " + url)


def read_list():
    mods = []
    for line in open(os.path.join(ROOT, "pack", "mods.txt"), encoding="utf-8"):
        line = line.split("#", 1)[0].strip()
        if line:
            slug, group = line.split()
            mods.append((slug, group))
    return mods


_projects = {}


def project(pid):
    if pid not in _projects:
        _projects[pid] = get(f"{API}/project/{pid}")
    return _projects[pid]


RANK = {"release": 0, "beta": 1, "alpha": 2}


def best_version(pid, mc, newest=False, back=0):
    """The newest Fabric file for this Minecraft version (releases before betas before alphas,
    unless newest: then simply the newest, for when another mod needs a version only in beta).
    back = how many files to step back (when the newest needs something this version doesn't have)."""
    q = urllib.parse.urlencode({"loaders": '["fabric"]', "game_versions": json.dumps([mc])})
    vs = get(f"{API}/project/{pid}/version?{q}") or []
    if not vs:
        return None
    if back:
        vs.sort(key=lambda v: v["date_published"], reverse=True)
        return vs[back] if back < len(vs) else None
    if newest:
        return max(vs, key=lambda v: v["date_published"])
    vs.sort(key=lambda v: (RANK.get(v["version_type"], 3), v["date_published"]), reverse=False)
    best = min(RANK.get(v["version_type"], 3) for v in vs)
    same = [v for v in vs if RANK.get(v["version_type"], 3) == best]
    return max(same, key=lambda v: v["date_published"])


def primary_file(v):
    fs = v["files"]
    for f in fs:
        if f.get("primary"):
            return f
    return fs[0]


def resolve(mc, leave_out=(), newest=(), back=None):
    """Every mod (and library) for this version: {project_id: entry}, plus problems found."""
    picked, problems, missing = {}, [], []
    queue = []
    for slug, group in read_list():
        if slug in leave_out:
            continue
        p = project(slug)
        if not p:
            problems.append(f"{slug}: not on Modrinth")
            continue
        queue.append((p["id"], group, slug, None))
    while queue:
        pid, group, why, want_version = queue.pop(0)
        if pid in picked:
            e = picked[pid]
            if e["group"] == "opt" and group != "opt":
                e["group"] = group  # an always-on mod needs it: it isn't optional any more
            continue
        p = project(pid)
        if want_version:
            v = get(f"{API}/version/{want_version}")
            if v and ("fabric" not in v["loaders"] or mc not in v["game_versions"]):
                v = best_version(pid, mc, p["slug"] in newest, (back or {}).get(p["slug"], 0))
        else:
            v = best_version(pid, mc, p["slug"] in newest, (back or {}).get(p["slug"], 0))
        if not v:
            (missing if group in ("perf", "qol", "opt") else problems).append(
                f"{p['slug']}: no Fabric build for {mc}" + ("" if group in ("perf", "qol", "opt") else f" (needed by {why})"))
            continue
        f = primary_file(v)
        picked[pid] = {
            "slug": p["slug"], "title": p["title"], "group": group, "neededBy": why if group == "lib" else None,
            "version": v["version_number"], "type": v["version_type"], "versionId": v["id"],
            "client": p["client_side"], "server": p["server_side"],
            "file": f["filename"], "url": f["url"], "sha1": f["hashes"]["sha1"], "sha512": f["hashes"]["sha512"],
            "size": f["size"], "deps": v.get("dependencies", []), "license": p["license"]["id"],
        }
        for d in v.get("dependencies", []):
            if d["dependency_type"] == "required" and d.get("project_id"):
                queue.append((d["project_id"], "lib" if group != "opt" else "opt", p["slug"], d.get("version_id")))
    # Modrinth's own "doesn't work with" marks
    for e in picked.values():
        for d in e["deps"]:
            if d["dependency_type"] == "incompatible" and d.get("project_id") in picked:
                problems.append(f"{e['slug']} says it doesn't work with {picked[d['project_id']]['slug']}")
    return picked, problems, missing


def env(e):
    side = lambda s: "required" if s in ("required", "optional") else "unsupported"
    if e["group"] == "opt":
        return {"client": "optional", "server": "optional" if e["server"] != "unsupported" else "unsupported"}
    return {"client": "required", "server": side(e["server"])}


def loader_version(mc):
    L = get(f"https://meta.fabricmc.net/v2/versions/loader/{mc}") or []
    for x in L:
        if x["loader"].get("stable"):
            return x["loader"]["version"]
    return L[0]["loader"]["version"]


# ------------------------------------------------------------------ checking the mods' own rules

def _ver_key(s):
    s = s.split("+", 1)[0]
    main, _, pre = s.partition("-")
    parts = []
    for x in main.split("."):
        m = re.match(r"(\d+)", x)
        parts.append(int(m.group(1)) if m else 0)
    return parts, pre


def _cmp(a, b):
    pa, pre_a = _ver_key(a)
    pb, pre_b = _ver_key(b)
    n = max(len(pa), len(pb))
    pa += [0] * (n - len(pa))
    pb += [0] * (n - len(pb))
    if pa != pb:
        return -1 if pa < pb else 1
    if pre_a == pre_b:
        return 0
    if not pre_a:
        return 1
    if not pre_b:
        return -1
    return -1 if pre_a < pre_b else 1


def _one(pred, have):
    pred = pred.strip()
    if pred in ("*", ""):
        return True
    for op in (">=", "<=", ">", "<", "=", "~", "^"):
        if pred.startswith(op):
            want = pred[len(op):].strip()
            break
    else:
        op, want = "=", pred
    want = want.split("+", 1)[0]
    if want.endswith(".x") or want.endswith(".*") or (op == "=" and "x" in want.split(".")):
        base = [x for x in want.split(".") if x not in ("x", "*", "X")]
        return _ver_key(have)[0][:len(base)] == [int(x) for x in base if x.isdigit()]
    c = _cmp(have, want)
    if op == ">=":
        return c >= 0
    if op == "<=":
        return c <= 0
    if op == ">":
        return c > 0
    if op == "<":
        return c < 0
    if op == "=":
        return c == 0
    wk = _ver_key(want)[0]
    hk = _ver_key(have)[0]
    if op == "~":
        return c >= 0 and hk[:2] == wk[:2]
    if op == "^":
        return c >= 0 and hk[:1] == wk[:1]
    return True


def matches(rule, have):
    rules = rule if isinstance(rule, list) else [rule]
    for r in rules:  # any of the list
        if all(_one(p, have) for p in r.split()):  # all of the space-separated parts
            return True
    return False


NOTES = []


def download(url, path, tries=4):
    """Downloads to path, trying again a few times if the connection drops."""
    for i in range(tries):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": UA})
            with urllib.request.urlopen(req, timeout=120) as r, open(path + ".part", "wb") as out:
                shutil.copyfileobj(r, out)
            os.replace(path + ".part", path)
            return
        except (urllib.error.URLError, TimeoutError, ConnectionError, OSError):
            if i == tries - 1:
                raise
            time.sleep(3 + i * 5)
CACHE = tempfile.mkdtemp(prefix="tcpack-cache-")  # jars downloaded while checking (deleted at the end)


def check(mc, picked, tc_jar, loader):
    """Downloads every jar and checks each mod's fabric.mod.json depends / breaks against the pack."""
    tmp = tempfile.mkdtemp(prefix="tcpack-")
    java = "21" if mc.startswith("1.") else "25"  # Minecraft 26.x runs on Java 25
    found = {"minecraft": mc, "fabricloader": loader, "java": java, "fabric": "1"}
    owner = {}  # mod id -> pack slug
    metas = []
    try:
        jars = [(e["slug"], e["url"], e["sha512"]) for e in picked.values()]
        for slug, url, sha in jars:
            path = os.path.join(CACHE, sha[:32] + ".jar")
            if not os.path.exists(path):
                download(url, path)
            if hashlib.sha512(open(path, "rb").read()).hexdigest() != sha:
                return [(f"{slug}: download didn't match its checksum", {slug}, None)]
            metas += read_meta(path, slug)
        metas += read_meta(tc_jar, "treasure-client")
        lj = loader_jar(loader)
        if lj:
            metas += [m for m in read_meta(lj, "fabric-loader") if m.get("id") != "fabricloader"]
        def have(mid, ver, slug):
            # with several copies of a library (nested in different mods), Fabric uses the newest
            if mid not in found or (mid not in ("minecraft", "fabricloader", "java") and _cmp(ver, found[mid]) > 0):
                found[mid] = ver
            owner.setdefault(mid, slug)
        for m in metas:
            slug = m["_from"].replace(" (inside)", "")
            have(m["id"], m["version"], slug)
            for p in m.get("provides", []):
                have(p if isinstance(p, str) else p.get("id"), m["version"], slug)
        problems = []
        needed = {d for m in metas if not m["_from"].endswith("(inside)") for d in (m.get("depends") or {})}
        for m in metas:
            slug = m["_from"].replace(" (inside)", "")
            inside = m["_from"].endswith("(inside)")
            for dep, rule in (m.get("depends") or {}).items():
                ok = dep in found and matches(rule, found[dep])
                if ok:
                    continue
                if inside and m["id"] not in needed:
                    # an optional part packed inside a mod: Fabric just doesn't load that part
                    NOTES.append(f"{slug}: its optional part {m['id']} is skipped (needs {dep} {rule})")
                    continue
                if dep not in found:
                    problems.append((f"{m['_from']} ({m['id']}) needs {dep} {rule}, which isn't in the pack", {slug}, None))
                else:
                    problems.append((f"{m['_from']} ({m['id']}) needs {dep} {rule}, the pack has {found[dep]}",
                                     {slug, owner.get(dep, dep)}, (owner.get(dep), slug)))
            for key in ("breaks", "conflicts"):
                for dep, rule in (m.get(key) or {}).items():
                    if dep in found and matches(rule, found[dep]):
                        problems.append((f"{m['_from']} ({m['id']}) {key} {dep} {rule} (pack has {found[dep]})",
                                         {slug, owner.get(dep, dep)}, None))
        return problems
    finally:
        shutil.rmtree(tmp, ignore_errors=True)


def loader_jar(loader):
    """Fabric Loader's own jar (it carries MixinExtras), from Gradle's cache or Fabric's maven."""
    base = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1/net.fabricmc/fabric-loader/" + loader)
    for d, _, fs in os.walk(base):
        for f in fs:
            if f == f"fabric-loader-{loader}.jar":
                return os.path.join(d, f)
    url = f"https://maven.fabricmc.net/net/fabricmc/fabric-loader/{loader}/fabric-loader-{loader}.jar"
    path = os.path.join(tempfile.gettempdir(), f"fabric-loader-{loader}.jar")
    try:
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as out:
            shutil.copyfileobj(r, out)
        return path
    except OSError:
        return None


def read_meta(path, slug):
    """fabric.mod.json of a jar and of every jar nested inside it."""
    out = []
    try:
        with zipfile.ZipFile(path) as z:
            out += _meta_from_zip(z, slug)
    except zipfile.BadZipFile:
        pass
    return out


def _meta_from_zip(z, slug):
    out = []
    names = z.namelist()
    if "fabric.mod.json" in names:
        try:
            m = json.loads(z.read("fabric.mod.json").decode("utf-8", "replace"), strict=False)
            m["_from"] = slug
            out.append(m)
        except ValueError:
            pass
    for n in names:
        if n.startswith("META-INF/jars/") and n.endswith(".jar"):
            try:
                with zipfile.ZipFile(io.BytesIO(z.read(n))) as inner:
                    for m in _meta_from_zip(inner, slug):
                        m["_from"] = slug + " (inside)"
                        out.append(m)
            except zipfile.BadZipFile:
                pass
    return out


# ------------------------------------------------------------------ writing the pack

def build(mc, do_check):
    tc_jar = os.path.join(ROOT, "builds", f"treasure-client-{TC_VERSION}+{mc}.jar")
    if not os.path.exists(tc_jar):
        print(f"{mc}: FAILED, {tc_jar} is missing (run tools/build_all.sh first)")
        return False
    loader = loader_version(mc)
    leave_out, dropped, newest, back = set(), [], set(), {}
    optional = {slug for slug, g in read_list() if g == "opt"}
    for _ in range(14):
        NOTES.clear()
        picked, problems, missing = resolve(mc, leave_out, newest, back)
        if not do_check:
            break
        found = check(mc, picked, tc_jar, loader)
        # 1) a mod needs a newer version of another: take that one's newest file (even a beta);
        #    if that was tried already, step the needing mod back to an older file instead
        up, step = set(), set()
        for _, who, u in found:
            if not u or (who & optional):
                continue
            dep, needer = u
            if dep and dep not in newest:
                up.add(dep)
            elif needer:
                step.add(needer)
        if up or step:
            newest |= up
            for n in step:
                back[n] = back.get(n, 0) + 1
            continue
        # 2) an optional extra that doesn't work here is simply left out
        clash = {}
        for text, who, _ in found:
            for slug in who & optional:
                clash.setdefault(slug, text)
        if not clash:
            problems += [t for t, _, _ in found]
            break
        for slug, text in clash.items():
            if back.get(slug, 0) < 3:  # first try a few older files of the optional extra
                back[slug] = back.get(slug, 0) + 1
                continue
            back.pop(slug, None)
            leave_out.add(slug)
            dropped.append(f"{slug}: {text}")
    else:
        problems.append("still clashing after leaving out optional extras")
    files = []
    for e in sorted(picked.values(), key=lambda e: e["slug"]):
        files.append({
            "path": "mods/" + e["file"],
            "hashes": {"sha1": e["sha1"], "sha512": e["sha512"]},
            "env": env(e),
            "downloads": [e["url"]],
            "fileSize": e["size"],
        })
    index = {
        "formatVersion": 1,
        "game": "minecraft",
        "versionId": f"{PACK_VERSION}+{mc}",
        "name": "Treasure Pack",
        "summary": "Treasure Client with a fast, full-quality set of performance and quality-of-life mods.",
        "files": files,
        "dependencies": {"minecraft": mc, "fabric-loader": loader},
    }
    os.makedirs(OUT, exist_ok=True)
    name = f"Treasure-Pack-{PACK_VERSION}+{mc}.mrpack"
    path = os.path.join(OUT, name)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("modrinth.index.json", json.dumps(index, indent=2))
        z.write(tc_jar, f"overrides/mods/{os.path.basename(tc_jar)}")
        ov = os.path.join(ROOT, "pack", "overrides")
        for base, _, fs in os.walk(ov):
            for f in fs:
                full = os.path.join(base, f)
                z.write(full, "overrides/" + os.path.relpath(full, ov))
    # the report
    groups = {"perf": "Performance", "qol": "Quality of life", "opt": "Optional extras", "lib": "Libraries"}
    lines = [f"Treasure Pack {PACK_VERSION} for Minecraft {mc} (Fabric Loader {loader})", ""]
    for g, title in groups.items():
        es = sorted((e for e in picked.values() if e["group"] == g), key=lambda e: e["slug"])
        lines.append(f"{title} ({len(es)}):")
        for e in es:
            extra = f"  [needed by {e['neededBy']}]" if e["neededBy"] else ""
            lines.append(f"  {e['slug']:32} {e['version']:40} {e['type']}{extra}")
        lines.append("")
    # an optional extra stepped back past its oldest file: no file of it works with the rest of the pack
    have = {e["slug"] for e in picked.values()}
    for slug in [k for k in back if k not in have]:
        missing = [m for m in missing if not m.startswith(slug + ":")]
        dropped.append(f"{slug}: none of its files for {mc} work with the rest of the pack")
        back.pop(slug)
    lines.append("Not in this version (no Fabric build yet):" if missing else "Every mod in the list has a build for this version.")
    lines += ["  " + m for m in missing]
    if dropped:
        lines += ["", "Optional extras left out because they clash with the pack on this version:"]
        lines += ["  " + d for d in dropped]
    used_newest = sorted(n for n in newest if any(e["slug"] == n and e["type"] != "release" for e in picked.values()))
    if used_newest:
        lines += ["", "Newest (beta) file taken because another mod needs it: " + ", ".join(used_newest)]
    if any(e["slug"] in back for e in picked.values()):
        lines += ["", "Older file taken so it works with the rest of the pack: "
                  + ", ".join(f"{k} ({next(e['version'] for e in picked.values() if e['slug'] == k)})" for k in sorted(back)
                              if any(e["slug"] == k for e in picked.values()))]
    if NOTES:
        lines += ["", "Notes:"] + ["  " + n for n in sorted(set(NOTES))]
    lines.append("")
    lines.append("Problems:" if problems else ("Checked: every mod's own rules are met." if do_check else "No problems found (jars not checked)."))
    lines += ["  " + p for p in problems]
    open(path[:-7] + ".txt", "w").write("\n".join(lines) + "\n")
    os.makedirs(JARS, exist_ok=True)
    shutil.copy(path, os.path.join(JARS, name))
    n = {g: sum(1 for e in picked.values() if e["group"] == g) for g in groups}
    print(f"{mc} OK: {n['perf']} performance, {n['qol']} QoL, {n['opt']} optional, {n['lib']} libraries;"
          f" {len(missing)} not available; {len(dropped)} optional left out; {len(problems)} problems")
    return True


if __name__ == "__main__":
    args = sys.argv[1:]
    do_check = "--check" in args
    vs = [a for a in args if not a.startswith("--")] or VERSIONS
    try:
        ok = all([build(v, do_check) for v in vs])
    finally:
        shutil.rmtree(CACHE, ignore_errors=True)
    sys.exit(0 if ok else 1)
