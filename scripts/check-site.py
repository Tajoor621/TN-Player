#!/usr/bin/env python3
"""Static checks for the TN-Player project site. No network and no APK build."""
import hashlib
import json
import re
import struct
import sys
from html.parser import HTMLParser
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
errors = []


def check(condition, message):
    if not condition:
        errors.append(message)


class Page(HTMLParser):
    def __init__(self):
        super().__init__()
        self.h1 = 0
        self.manifests = []
        self.links = []
        self.metas = []
        self.ids = []
        self.images = []
        self.title = []
        self._in_title = False
        self.html_lang = ""

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "html":
            self.html_lang = attrs.get("lang", "")
        if tag == "h1":
            self.h1 += 1
        if tag == "link" and attrs.get("rel") == "manifest":
            self.manifests.append(attrs.get("href", ""))
        href = attrs.get("href")
        if tag in {"a", "link"} and href:
            self.links.append(href)
        if tag == "img":
            self.images.append(attrs.get("src", ""))
        if tag == "meta":
            self.metas.append((attrs.get("name") or attrs.get("property"), attrs.get("content", "")))
        if attrs.get("id"):
            self.ids.append(attrs["id"])
        if tag == "title":
            self._in_title = True

    def handle_endtag(self, tag):
        if tag == "title":
            self._in_title = False

    def handle_data(self, data):
        if self._in_title:
            self.title.append(data)


def parse(name):
    text = (ROOT / name).read_text(encoding="utf-8")
    page = Page()
    page.feed(text)
    page.text = text
    return page


def local_path(url):
    if not url or re.match(r"^[a-z]+:", url) or url.startswith("#"):
        return None
    path = url.split("#", 1)[0].split("?", 1)[0]
    if path in {"", "./", "/"}:
        return ROOT / "index.html"
    return ROOT / path.lstrip("./")


def png_size(path):
    data = path.read_bytes()
    check(data[:16] == b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR", f"{path} is not a PNG")
    return struct.unpack(">II", data[16:24])


index = parse("index.html")
player = parse("player.html")

for name, page in (("index.html", index), ("player.html", player)):
    check(page.html_lang == "en", f"{name} must set html lang=en")
    check(page.h1 == 1, f"{name} must contain one h1, found {page.h1}")
    check(page.manifests == ["./manifest.webmanifest"], f"{name} must link one manifest")
    check('id="main"' in page.text and 'href="#main"' in page.text, f"{name} needs a skip link to #main")
    check('navigator.serviceWorker.register("./sw.js")' in page.text, f"{name} must register ./sw.js inline")
    check("./site.css" in page.text, f"{name} must use site.css")
    check("Player9923" not in page.text, f"{name} still has the informal repository name")
    check("Netflix" not in page.text and "Spotify" not in page.text, f"{name} must not name streaming services")
    for tag in re.findall(r"<[^>]+>", page.text):
        check(len(re.findall(r'\sid="', tag)) <= 1, f"{name} has a tag with two ids: {tag[:80]}")
    metas = dict(page.metas)
    for key in ("description", "robots", "og:title", "og:description", "og:image", "twitter:card"):
        check(metas.get(key, "").strip(), f"{name} missing meta {key}")
    check('rel="canonical"' in page.text, f"{name} needs a canonical link")
    for url in page.links + page.images:
        target = local_path(url)
        if target is not None:
            check(target.is_file(), f"{name} links to missing file {url}")

check("Noor E Sahar" in index.text, "index should carry the Noor E Sahar identity")
check("never uploaded" in player.text, "player must say files are never uploaded")
check("does not host a catalog" in index.text, "index must say there is no catalog")
check("downloads/TN-Player-1.0.0-debug.apk" in index.text, "index must link the testing APK")
check("TN-Player-native-sources.tar.xz" not in index.text, "index must leave room for the publication credit")
check("</main>" in index.text, "index must keep the publication insertion point")
check("Universal Media Player" not in index.text and "Universal Media Player" not in player.text, "site must not claim a universal player")
for element_id in ("media-file", "media", "status", "install", "queue", "rate", "repeat", "sleep", "pip", "subtitle-file"):
    check(f'id="{element_id}"' in player.text, f"player is missing #{element_id}")

manifest = json.loads((ROOT / "manifest.webmanifest").read_text(encoding="utf-8"))
check(manifest["name"].strip() and manifest["short_name"].strip(), "manifest name required")
check(manifest["display"] == "standalone" and manifest["orientation"] == "any", "manifest display/orientation")
check(all(manifest.get(key) for key in ("theme_color", "background_color", "shortcuts", "categories")), "manifest recommendations")
check(not any(manifest.get(key) for key in ("screenshots", "iarc_rating_id", "related_applications")), "manifest must omit store/screenshot keys")
check(manifest["start_url"].endswith("player.html"), "installed app opens the web player")
check(manifest["scope"] == "./", "manifest scope stays on this site")
for icon in manifest["icons"]:
    path = local_path(icon["src"])
    width, height = png_size(path)
    check(icon["sizes"] == f"{width}x{height}", f"{icon['src']} size does not match the file")
    check(icon["type"] == "image/png", "icons must be PNG")
check(any(icon["purpose"] == "maskable" and png_size(local_path(icon["src"]))[0] >= 512 for icon in manifest["icons"]), "maskable icon")
check(any(icon["purpose"] == "any" and png_size(local_path(icon["src"]))[0] >= 512 for icon in manifest["icons"]), "large any-purpose icon")

data = json.loads(re.search(r'<script type="application/ld\+json">\s*(\{.*?\})\s*</script>', index.text, re.S).group(1))
check(data["name"] == "TN-Player" and data["alternateName"] == "Noor E Sahar", "structured data identity")
check("TN-Player-1.0.0-debug.apk" in data["downloadUrl"], "structured data download")
check("cloud library" in data["description"], "structured data stays honest about no cloud library")

shell = re.search(r"const shell = \[(.*?)\]", (ROOT / "sw.js").read_text(encoding="utf-8"), re.S).group(1)
shell_paths = re.findall(r'"(\./[^"]*)"', shell)
check("v3" in (ROOT / "sw.js").read_text(encoding="utf-8"), "service worker cache must be v3")
check(shell_paths, "service worker shell is empty")
for path in shell_paths:
    check("downloads/" not in path, f"service worker must not cache {path}")
    target = local_path(path)
    check(target is None or target.is_file() or path == "./", f"shell file missing: {path}")
    if path not in {"./", "./index.html"}:
        check(local_path(path).is_file(), f"shell file missing: {path}")

readme = (ROOT / "README.md").read_text(encoding="utf-8")
check("Noor E Sahar" in readme, "README should name Noor E Sahar")
check("https://tajoor621.github.io/TN-Player/" in readme, "README should link the project site")
check("Player9923" not in readme, "README still has the informal repository name")
check("site.css" in (ROOT / "scripts" / "package-pages.py").read_text(encoding="utf-8"), "Pages package must include site.css")

apk = ROOT / "downloads" / "TN-Player-1.0.0-debug.apk"
digest = hashlib.sha256(apk.read_bytes()).hexdigest()
sums = (ROOT / "downloads" / "SHA256SUMS.txt").read_text(encoding="utf-8")
info = json.loads((ROOT / "build-info.json").read_text(encoding="utf-8"))
check(sums.startswith(digest), "SHA256SUMS.txt does not match the APK")
check(info["apk_sha256"] == digest and info["apk_bytes"] == apk.stat().st_size, "build-info.json does not match the APK")
check((ROOT / "robots.txt").read_text(encoding="utf-8").startswith("User-agent:"), "robots.txt")
sitemap = (ROOT / "sitemap.xml").read_text(encoding="utf-8")
check("https://tajoor621.github.io/TN-Player/</loc>" in sitemap and "player.html</loc>" in sitemap, "sitemap.xml")

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(f"{len(errors)} site check(s) failed")
print("TN-Player site checks passed.")
