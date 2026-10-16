"""Create the GitHub release for a tag, attach the APK, publish. Uses git's stored GitHub login
(git credential fill); never prints it. Draft first, so nothing is public without its APK.

Usage (the tag must already be pushed):
    python tools/publish-github-release.py v1.0.32 "BooxDay 1.0.32" ../booxday-release/booxday-1.0.32.apk notes.md

Re-running reuses the draft for the tag and replaces a half-uploaded APK; it refuses to touch a
release that is already published. Needs no gh CLI (not installed on this PC)."""
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

REPO = "fortolano/booxday"
TAG, TITLE, APK = sys.argv[1], sys.argv[2], sys.argv[3]
BODY = open(sys.argv[4], encoding="utf-8").read().strip()
ASSET_NAME = os.path.basename(APK)

cred = subprocess.run(["git", "credential", "fill"], input="protocol=https\nhost=github.com\n\n",
                      capture_output=True, text=True, env={**os.environ, "GIT_TERMINAL_PROMPT": "0"}).stdout
TOKEN = dict(l.split("=", 1) for l in cred.splitlines() if "=" in l).get("password")
if not TOKEN:
    sys.exit("No GitHub login found in git's credential manager.")


def call(method, url, data=None, content_type="application/json"):
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Authorization", f"Bearer {TOKEN}")
    req.add_header("Accept", "application/vnd.github+json")
    req.add_header("X-GitHub-Api-Version", "2022-11-28")
    if data is not None:
        req.add_header("Content-Type", content_type)
    try:
        with urllib.request.urlopen(req, timeout=900) as r:
            raw = r.read()
            return r.status, (json.loads(raw) if raw else None)
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read() or b"{}")


api = f"https://api.github.com/repos/{REPO}"

# Reuse a draft or release for this tag if one exists
status, releases = call("GET", f"{api}/releases?per_page=20")
if status != 200:
    sys.exit(f"Listing releases failed: {status} {releases}")
release = next((r for r in releases if r["tag_name"] == TAG), None)
if release and not release["draft"]:
    sys.exit(f"{TAG} is already published: {release['html_url']}")
if release is None:
    status, release = call("POST", f"{api}/releases", json.dumps(
        {"tag_name": TAG, "name": TITLE, "body": BODY, "draft": True}).encode())
    if status != 201:
        sys.exit(f"Creating the draft failed: {status} {release}")
    print(f"draft created: id {release['id']}")
else:
    print(f"reusing draft: id {release['id']}")

# Upload the APK (replace a partial or older copy)
for asset in release.get("assets", []):
    if asset["name"] == ASSET_NAME:
        call("DELETE", f"{api}/releases/assets/{asset['id']}")
size = os.path.getsize(APK)
with open(APK, "rb") as f:
    data = f.read()
status, asset = call("POST", f"https://uploads.github.com/repos/{REPO}/releases/{release['id']}/assets?name={ASSET_NAME}",
                     data, "application/vnd.android.package-archive")
if status != 201:
    sys.exit(f"Upload failed: {status} {asset}  (the draft is left unpublished)")
if asset["size"] != size or asset["state"] != "uploaded":
    sys.exit(f"Upload incomplete: {asset['size']} of {size} bytes, state {asset['state']} (draft left unpublished)")
print(f"uploaded {ASSET_NAME}: {asset['size']} bytes (local {size})")

# Publish
status, release = call("PATCH", f"{api}/releases/{release['id']}", json.dumps(
    {"draft": False, "name": TITLE, "body": BODY, "make_latest": "true"}).encode())
if status != 200:
    sys.exit(f"Publishing failed: {status} {release}")
print(f"published: {release['html_url']}")
TOKEN = None
