#!/usr/bin/env python3
"""Treasure Client: adds the Treasure Client menu and HUD overlay (web/tc-overlay.js)
and its fonts to the built page. Run after minify_page.py; the overlay is added as is,
not minified."""
import sys

page_path = sys.argv[1]
with open(page_path, encoding="utf-8") as f:
    page = f.read()
with open("web/tc-overlay.js", encoding="utf-8") as f:
    overlay = f.read()
if "</script" in overlay.lower():
    sys.exit("web/tc-overlay.js must not contain a closing script tag")
if "TREASURE CLIENT overlay logic" in page:
    sys.exit(0)
fonts = ('<link rel="preconnect" href="https://fonts.googleapis.com">\n'
         '<link href="https://fonts.googleapis.com/css2?family=Cinzel:wght@600;700&family=Outfit:'
         'wght@400;500;600;700&family=JetBrains+Mono:wght@400;600&display=swap" rel="stylesheet">\n')
with open("web/tc-boot.js", encoding="utf-8") as f:
    boot = f.read()
if "</script" in boot.lower():
    sys.exit("web/tc-boot.js must not contain a closing script tag")
# The loading screen goes right after the title, so it shows from the first moment.
head = page.lower().find("</title>")
if head < 0:
    sys.exit("no <title> in the page")
head += len("</title>")
page = page[:head] + "\n<script>\n" + boot + "\n</script>\n" + page[head:]
# The overlay at the end, so the doctype stays first (link tags work anywhere in the page).
page = page + "\n" + fonts + "<script>\n" + overlay + "\n</script>\n"
with open(page_path, "w", encoding="utf-8") as f:
    f.write(page)
print("added the Treasure Client overlay (%d KB)" % (len(overlay) // 1024))
