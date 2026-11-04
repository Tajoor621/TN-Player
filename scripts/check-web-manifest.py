#!/usr/bin/env python3
"""Check TN-Player install assets over HTTP and mirror PWABuilder manifest points."""
import json
import struct
import sys
from html.parser import HTMLParser
from urllib.parse import urljoin
from urllib.request import Request, urlopen


class Links(HTMLParser):
    def __init__(self):
        super().__init__()
        self.manifests = []

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if tag == "link" and attrs.get("rel") == "manifest":
            self.manifests.append(attrs["href"])


def read(url, method="GET"):
    with urlopen(Request(url, method=method), timeout=15) as response:
        assert response.status == 200, (url, response.status)
        return response.read()


base = sys.argv[1]
assert base.endswith("/"), "Pass the site directory URL with its trailing slash."
links = Links()
links.feed(read(base).decode())
assert len(links.manifests) == 1
manifest_url = urljoin(base, links.manifests[0])
manifest = json.loads(read(manifest_url))
scope = urljoin(manifest_url, manifest["scope"])
assert scope == base
start = urljoin(manifest_url, manifest["start_url"])
assert start.startswith(scope)
player = Links()
player.feed(read(start).decode())
assert [urljoin(start, item) for item in player.manifests] == [manifest_url]

def verify_icon(icon):
    src = urljoin(manifest_url, icon["src"])
    assert src.startswith(scope)
    assert icon["type"] == "image/png"
    png = read(src)
    assert png[:16] == b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"
    width, height = struct.unpack(">II", png[16:24])
    assert icon["sizes"] == f"{width}x{height}", src
    return width == height and width >= 512

large = [i for i in manifest["icons"] if verify_icon(i)]
ordinary = next(i for i in large if i["purpose"] == "any")
read(urljoin(manifest_url, ordinary["src"]), method="HEAD")
assert any(i["purpose"] == "maskable" for i in large)
for shortcut in manifest["shortcuts"]:
    url = urljoin(manifest_url, shortcut["url"])
    assert url.startswith(scope)
    read(url)
    for icon in shortcut["icons"]:
        verify_icon(icon)
for filename in ("sw.js", "pwa.js"):
    read(urljoin(scope, filename))

# Seven required checks plus nine applicable recommendations. See docs/PWA.md.
assert manifest["name"].strip() and manifest["short_name"].strip()
assert manifest["display"] == "standalone" and manifest["orientation"] == "any"
assert all(manifest[k] for k in ("theme_color", "background_color", "shortcuts", "categories"))
assert not any(manifest.get(k) for k in ("screenshots", "iarc_rating_id", "related_applications"))
print("Manifest assets and links passed. PWABuilder published manifest-finder rules: 160 points (70 required + 90 recommended).")
