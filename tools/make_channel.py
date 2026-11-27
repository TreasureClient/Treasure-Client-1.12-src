#!/usr/bin/env python3
"""Makes channel.json: the small file the launcher reads to find Treasure Client and the
Treasure Pack for each Minecraft version (see core/src/treasure.rs).

    python3 -I tools/make_channel.py <folder with the jars and .mrpacks> <base URL> [news.json] > channel.json

Example (files hosted next to channel.json on the website):
    python3 -I tools/make_channel.py ~/Treasure-Client-mod-jars https://treasureclient.github.io/launcher/files
"""
import hashlib, json, os, re, sys

def main():
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    folder, base = sys.argv[1], sys.argv[2].rstrip('/')
    out = {"treasure_client": {}, "treasure_pack": {}, "news": [], "launcher": {"version": "0.1.0", "page": "https://treasureclient.github.io"}}
    if len(sys.argv) > 3:
        with open(sys.argv[3]) as f:
            out["news"] = json.load(f)
    for name in sorted(os.listdir(folder)):
        m = re.fullmatch(r'treasure-client-(.+)\+(.+)\.jar', name) or re.fullmatch(r'Treasure-Pack-(.+)\+(.+)\.mrpack', name)
        if not m:
            continue
        path = os.path.join(folder, name)
        with open(path, 'rb') as f:
            data = f.read()
        key = "treasure_client" if name.endswith('.jar') else "treasure_pack"
        out[key][m.group(2)] = {"version": m.group(1), "url": f"{base}/{name.replace('+', '%2B')}", "sha1": hashlib.sha1(data).hexdigest(), "size": len(data)}
    json.dump(out, sys.stdout, indent=2)
    print()

main()
