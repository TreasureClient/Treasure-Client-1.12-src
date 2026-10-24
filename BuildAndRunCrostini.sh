#!/usr/bin/env bash
#
# Treasure Client (WASM) — one-command build + run for Crostini / Linux.
#
# Usage:   bash BuildAndRunCrostini.sh
#
# It compiles the whole WASM client from source, then serves the result on a
# local web server so you can open it in Chrome. Press Ctrl+C to stop the server.
#
set -e

# Always work from the folder this script lives in (the repo root).
cd "$(dirname "$0")"
ROOT="$(pwd)"
cd "$ROOT/wasm_gc_teavm"

echo "=============================================="
echo " Treasure Client — building the WASM client"
echo "=============================================="

# GitHub's ZIP download loses the executable bit, so set it back.
chmod +x gradlew *.sh 2>/dev/null || true

# Delete old builds first so only the fresh one is left.
rm -f "$ROOT"/wasm_gc_teavm/javascript_dist/*.html "$ROOT"/javascript/Eaglercraft_1.12_Offline_*.html

echo; echo ">>> [1/4] Packing assets (CompileEPK)"
./CompileEPK.sh

echo; echo ">>> [2/4] Compiling Java -> WASM (CompileWASM) — first run downloads TeaVM, be patient"
./CompileWASM.sh

echo; echo ">>> [3/4] Building runtime JS (CompileEagRuntimeJS)"
./CompileEagRuntimeJS.sh

echo; echo ">>> [4/4] Bundling the offline download (MakeWASMClientBundle)"
./MakeWASMClientBundle.sh

# One output file, named the way the GitHub Pages site expects it.
mv -f "$ROOT/wasm_gc_teavm/javascript_dist/Eaglercraft_1.12.2_WASM_Offline_Download.html" \
      "$ROOT/wasm_gc_teavm/javascript_dist/Treasure-Client_WASM.html"
OUT="$ROOT/wasm_gc_teavm/javascript_dist/Treasure-Client_WASM.html"
echo
echo "=============================================="
echo " Build finished."
echo " File: $OUT"
echo "=============================================="
echo
echo " Starting a local server. In Chrome, open:"
echo
echo "     http://penguin.linux.test:8000/Treasure-Client_WASM.html"
echo
echo " (if that doesn't load, try  http://localhost:8000/Treasure-Client_WASM.html )"
echo
echo " Press Ctrl+C here to stop the server when you're done."
echo

cd "$ROOT/wasm_gc_teavm/javascript_dist"
exec python3 -m http.server 8000 --bind 0.0.0.0
