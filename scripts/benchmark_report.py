#!/usr/bin/env python3
"""Render only observed device rows. A short/unknown/failed run never passes the 30-minute gate."""
import argparse
import csv
import re
from pathlib import Path

START = "<!-- DEVICE_RESULTS_START -->"
END = "<!-- DEVICE_RESULTS_END -->"


def cell(value):
    return str(value).replace("|", "/").replace("\n", " ").replace("\r", " ").replace("<", "&lt;").replace(">", "&gt;")


def summarize(rows):
    if not rows:
        raise ValueError("No device measurements in CSV")
    devices = {(r["device"], r["android"], r["sdk"], r["source_revision"]) for r in rows}
    if len(devices) != 1:
        raise ValueError("Mixed devices/Android/source revisions in one run")
    device, android, sdk, revision = next(iter(devices))
    if not re.fullmatch("[0-9a-f]{40}", revision):
        raise ValueError("Missing exact APK source revision")
    refs = [r for r in rows if r["kind"] == "reference"]
    samples = [r for r in rows if r["kind"] == "background_sample"]
    release = [r for r in rows if r["kind"] == "background_release"]
    complete = (len(refs) == 3 and len({r["file"] for r in refs}) == 3
                and all(r["status"] == "pass" and float(r["ttff_ms"]) >= 0 and float(r["seek_ms"]) >= 0 for r in refs)
                and any(r["codec"] == "hevc" and r["width"] == "3840" and r["height"] == "2160" for r in refs)
                and any(r["bit_depth"] == "10" and r["transfer"] == "smpte2084" for r in refs)
                and bool(samples) and all(r["status"] == "pass" for r in samples)
                and len(release) == 1 and release[0]["status"] == "pass"
                and int(release[0]["elapsed_s"]) >= 1800
                and max(int(r["elapsed_s"]) for r in samples) >= 1800
                and all(release[0][k] == "0" for k in ("active_audio", "active_video", "wake_locks"))
                and not any(r["status"] == "fail" for r in rows))
    lines = [f"Device: **{cell(device)}**; Android **{cell(android)}** (API {cell(sdk)}).",
             f"APK source: `{revision}`. Acceptance: **{'PASS' if complete else 'PENDING / FAILED / INCONCLUSIVE'}**.",
             "", "| Reference | Codec / pixels / bits / transfer | TTFF ms | Seek ms | Dropped frames | CPU % of one core | Status |",
             "|---|---|---:|---:|---:|---:|---|"]
    for r in refs:
        details = f"{r['codec']} / {r['width']}×{r['height']} / {r['bit_depth']} / {r['transfer']}"
        lines.append("| " + " | ".join(cell(v) for v in (r["file"], details, r["ttff_ms"], r["seek_ms"], r["dropped_frames"], r["cpu_percent_one_core"], r["status"])) + " |")
    for r in refs:
        lines += [f"\n{cell(r['file'])}: {cell(r['size_bytes'])} bytes, {cell(r['duration_s'])} s, SHA-256 `{cell(r['sha256'])}`. Tunneling requested/active: {cell(r.get('tunnel_requested', 'unknown'))}/{cell(r.get('tunnel_active', 'unknown'))}; preload: {cell(r.get('preload', 'unknown'))}."]
    if release:
        r = release[-1]
        lines += ["", f"Background run: {cell(r['elapsed_s'])} s; release status **{cell(r['status'])}**; active audio/video decoders {cell(r['active_audio'])}/{cell(r['active_video'])}; current ExoPlayer wake locks {cell(r['wake_locks'])}."]
    else:
        lines += ["", "Background release measurement: **missing; acceptance pending**."]
    for r in rows:
        if r.get("error"):
            lines.append(f"- {cell(r['kind'])}: {cell(r['error'])}")
    if samples:
        lines += ["", f"Background PSS range: {min(int(r['pss_kb']) for r in samples)}–{max(int(r['pss_kb']) for r in samples)} KB; FD range: {min(int(r['fd_count']) for r in samples)}–{max(int(r['fd_count']) for r in samples)}. Ranges are observations, not a heap-leak proof."]
    lines += ["", "No matched baseline comparison supplied; performance regressions are **not established**. Keep the original CSV and fixture manifest with this report."]
    return "\n".join(lines), complete


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    with args.csv.open(newline="") as source:
        report, complete = summarize(list(csv.DictReader(source)))
    existing = args.output.read_text()
    if existing.count(START) != 1 or existing.count(END) != 1:
        raise ValueError("Expected one device-results marker pair")
    before, rest = existing.split(START)
    _, after = rest.split(END)
    args.output.write_text(before + START + "\n" + report + "\n" + END + after)
    if not complete:
        raise SystemExit("Results recorded; full performance acceptance has not passed")


if __name__ == "__main__":
    main()
