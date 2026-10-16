"""Scan every file inside an APK for OpenRouter keys. Prints counts only, never a key.

Usage: python tools/scan-apk-for-keys.py app/build/outputs/apk/release/app-release.apk

Checks for the owner's key (from OPENROUTER_API_KEY, if set) and any full sk-or-v1-<hex> key. A hit on
the bare "sk-or-" prefix in a classes*.dex is expected: the app's own key-format check
(OpenRouterConnect.looksLikeKey). A release must show 0 for the other lines."""
import os
import sys
import zipfile

apk = sys.argv[1]
owner_key = (os.environ.get("OPENROUTER_API_KEY") or "").strip().encode()
needles = {"sk-or- prefix": b"sk-or-"}
if owner_key:
    needles["owner's key"] = owner_key
    needles["owner's key (UTF-16)"] = owner_key.decode().encode("utf-16-le")

import re
full_key = re.compile(rb"sk-or-v1-[0-9a-fA-F]{32,}")
hits = {name: [] for name in needles}
hits["full key pattern sk-or-v1-<hex>"] = []
with zipfile.ZipFile(apk) as z:
    entries = z.infolist()
    for info in entries:
        data = z.read(info)
        for name, needle in needles.items():
            if needle in data:
                hits[name].append(info.filename)
        if full_key.search(data):
            hits["full key pattern sk-or-v1-<hex>"].append(info.filename)

print(f"entries scanned: {len(entries)}; owner key available to check: {bool(owner_key)}")
for name, files in hits.items():
    print(f"{name}: {len(files)} file(s){' -> ' + ', '.join(files[:10]) if files else ''}")
owner_key = None
