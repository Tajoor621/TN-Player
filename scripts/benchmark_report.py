#!/usr/bin/env python3
"""Report observed device rows; incomplete or unrecognized evidence never passes."""
import argparse
import csv
import math
import re
from pathlib import Path

START = "<!-- DEVICE_RESULTS_START -->"
END = "<!-- DEVICE_RESULTS_END -->"
MAX_SAMPLE_GAP_S = 30  # Harness samples every 10 s; tolerate scheduling jitter, not missing minutes.


def cell(value):
    return str(value).replace("|", "/").replace("\n", " ").replace("\r", " ").replace("<", "&lt;").replace(">", "&gt;")


def number(row, key, minimum=0, integer=False):
    try:
        value = float(row.get(key, ""))
    except (TypeError, ValueError):
        return None
    if not math.isfinite(value) or value < minimum or (integer and not value.is_integer()):
        return None
    return value


def identity(rows):
    if not rows:
        raise ValueError("No device measurements in CSV")
    devices = {(r.get("device", ""), r.get("android", ""), r.get("sdk", ""), r.get("source_revision", "")) for r in rows}
    if len(devices) != 1:
        raise ValueError("Mixed devices/Android/source revisions in one run")
    device, android, sdk, revision = next(iter(devices))
    if not all((device.strip(), android.strip(), sdk.strip())) or not re.fullmatch("[0-9a-f]{40}", revision):
        raise ValueError("Missing device/Android identity or exact APK source revision")
    return device, android, sdk, revision


def valid_reference(row):
    return (row.get("status") == "pass" and not row.get("error")
            and bool(re.fullmatch("[0-9a-f]{64}", row.get("sha256", "")))
            and all(number(row, key) is not None for key in ("ttff_ms", "seek_ms", "cpu_ms", "cpu_percent_one_core"))
            and number(row, "dropped_frames", integer=True) is not None
            and all(number(row, key, minimum=1) is not None for key in ("wall_ms", "duration_s", "size_bytes")))


def continuous_background(samples):
    previous = 0
    previous_position = None
    previous_cpu = 0
    previous_wall = 0
    for row in samples:
        elapsed = number(row, "elapsed_s", minimum=1, integer=True)
        if (elapsed is None or not 0 < elapsed - previous <= MAX_SAMPLE_GAP_S
                or row.get("status") != "pass" or row.get("error")
                or row.get("playing", "").lower() != "true"
                or number(row, "position_ms") is None
                or any(number(row, key) is None for key in ("cpu_ms", "cpu_percent_one_core"))
                or number(row, "wall_ms", minimum=1) is None
                or number(row, "position_ms") == previous_position
                or number(row, "cpu_ms") < previous_cpu
                or number(row, "wall_ms") <= previous_wall):
            return False
        previous = elapsed
        previous_position = number(row, "position_ms")
        previous_cpu = number(row, "cpu_ms")
        previous_wall = number(row, "wall_ms")
    return previous >= 1800


def comparison(rows, baseline):
    current_id = identity(rows)
    baseline_id = identity(baseline)
    if current_id[:3] != baseline_id[:3]:
        raise ValueError("Baseline device/Android version differs")
    current_fingerprints = {r.get("build_fingerprint", "") for r in rows}
    baseline_fingerprints = {r.get("build_fingerprint", "") for r in baseline}
    if (len(current_fingerprints) != 1 or "" in current_fingerprints
            or current_fingerprints != baseline_fingerprints):
        raise ValueError("Baseline OS build fingerprint differs or is missing")
    current = [r for r in rows if r.get("kind") == "reference"]
    old = [r for r in baseline if r.get("kind") == "reference"]
    if (len(current) != 3 or len(old) != 3 or not all(valid_reference(r) for r in current + old)
            or len({r.get("file") for r in current}) != 3 or len({r.get("file") for r in old}) != 3):
        raise ValueError("Both runs need three distinct passing, finite reference measurements")
    old = {r["file"]: r for r in old}
    signature = ("sha256", "size_bytes", "duration_s", "codec", "width", "height", "bit_depth", "transfer")
    for row in current:
        other = old.get(row["file"])
        if other is None or any(row.get(key) != other.get(key) for key in signature):
            raise ValueError("Baseline reference bytes or file details differ")
    def delta(before, after):
        before, after = float(before), float(after)
        relative = f"{(after / before - 1) * 100:+.1f}%" if before else "relative delta unavailable (zero baseline)"
        return f"{before:g} → {after:g} ({relative})"
    lines = ["", "### Matched timing comparison", "",
             f"Baseline source: `{baseline_id[3]}`; candidate source: `{current_id[3]}`.",
             "Device model, Android build and reference hashes match. Confirm the same physical device was used. These are single observations; thermal state, charging and OS page caches are not controlled by CSV identity.",
             "Positive timing/CPU changes indicate higher observed cost, not a statistically established regression. A zero baseline has no percentage delta.", "",
             "| Reference | TTFF ms (baseline → current) | Seek ms | CPU % of one core | Dropped frames |",
             "|---|---|---|---|---|"]
    for row in current:
        before = old[row["file"]]
        values = [row["file"]] + [delta(before[key], row[key]) for key in ("ttff_ms", "seek_ms", "cpu_percent_one_core")]
        values += [f"{before['dropped_frames']} → {row['dropped_frames']}"]
        lines.append("| " + " | ".join(cell(v) for v in values) + " |")
    for row in current:
        before = old[row["file"]]
        config = lambda r: "/".join(r.get(key, "unknown") for key in ("tunnel_requested", "tunnel_active", "preload"))
        lines.append(f"\n{cell(row['file'])} requested tunnel / active tunnel / preload: baseline {cell(config(before))}; candidate {cell(config(row))}.")
    return "\n".join(lines)


def summarize(rows, baseline=None):
    device, android, sdk, revision = identity(rows)
    refs = [r for r in rows if r["kind"] == "reference"]
    samples = [r for r in rows if r["kind"] == "background_sample"]
    release = [r for r in rows if r["kind"] == "background_release"]
    complete = (len(refs) == 3 and len({r.get("file") for r in refs}) == 3
                and all(valid_reference(r) for r in refs)
                and any(r.get("codec") == "hevc" and r.get("width") == "3840" and r.get("height") == "2160" for r in refs)
                and any(r.get("bit_depth") == "10" and r.get("transfer") == "smpte2084" for r in refs)
                and continuous_background(samples)
                and len(release) == 1 and release[0].get("status") == "pass" and not release[0].get("error")
                and number(release[0], "elapsed_s", minimum=1800) is not None
                and 0 <= float(release[0]["elapsed_s"]) - float(samples[-1]["elapsed_s"]) <= 60
                and all(release[0].get(k) == "0" for k in ("active_audio", "active_video", "wake_locks"))
                and all(r.get("kind") in {"reference", "background_sample", "background_release"}
                        and r.get("status") == "pass" and not r.get("error") for r in rows))
    compared = ""
    if baseline is not None:
        try:
            compared = comparison(rows, baseline)
        except ValueError as error:
            compared = f"\nBaseline comparison **inconclusive**: {cell(error)}. No regression conclusion is made."
            complete = False
    lines = [f"Device: **{cell(device)}**; Android **{cell(android)}** (API {cell(sdk)}).",
             f"APK source: `{revision}`. Acceptance: **{'PASS' if complete else 'PENDING / FAILED / INCONCLUSIVE'}**.",
             "", "| Reference | Codec / pixels / bits / transfer | TTFF ms | Seek ms | Dropped frames | CPU % of one core | Status |",
             "|---|---|---:|---:|---:|---:|---|"]
    for r in refs:
        details = " / ".join(r.get(key, "unknown") for key in ("codec", "width", "height", "bit_depth", "transfer"))
        lines.append("| " + " | ".join(cell(v) for v in (r.get("file", ""), details, r.get("ttff_ms", ""), r.get("seek_ms", ""), r.get("dropped_frames", ""), r.get("cpu_percent_one_core", ""), r["status"])) + " |")
    for r in refs:
        lines += [f"\n{cell(r.get('file', ''))}: {cell(r.get('size_bytes', ''))} bytes, {cell(r.get('duration_s', ''))} s, SHA-256 `{cell(r.get('sha256', ''))}`. Tunneling requested/active: {cell(r.get('tunnel_requested', 'unknown'))}/{cell(r.get('tunnel_active', 'unknown'))}; preload: {cell(r.get('preload', 'unknown'))}."]
    if release:
        r = release[-1]
        lines += ["", f"Background run: {cell(r.get('elapsed_s', ''))} s; release status **{cell(r['status'])}**; active audio/video decoders {cell(r.get('active_audio', ''))}/{cell(r.get('active_video', ''))}; current ExoPlayer wake locks {cell(r.get('wake_locks', ''))}."]
    else:
        lines += ["", "Background release measurement: **missing; acceptance pending**."]
    if not continuous_background(samples):
        lines += ["", "Continuous background evidence **incomplete**: require positive, ordered samples no more than 30 s apart, starting within 30 s and spanning at least 1,800 s, with playing/progress/CPU fields present."]
    for r in rows:
        if r.get("error"):
            lines.append(f"- {cell(r['kind'])}: {cell(r['error'])}")
    for key, label in (("pss_kb", "PSS KB"), ("fd_count", "FD count")):
        values = [number(r, key, integer=True) for r in samples]
        if values and all(v is not None for v in values):
            lines += [f"\nBackground {label} range: {int(min(values))}–{int(max(values))}. Observations do not prove heap-leak absence."]
        elif values:
            lines += [f"\nBackground {label}: **incomplete / unavailable**."]
    lines += [compared] if baseline is not None else ["", "No matched baseline comparison supplied; performance regressions are **not established**. Keep the original CSV and fixture manifest with this report."]
    return "\n".join(lines), complete


def read_csv(path):
    with path.open(newline="") as source:
        return list(csv.DictReader(source))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, help="Optional previous CSV from the same device/build and reference bytes")
    args = parser.parse_args()
    report, complete = summarize(read_csv(args.csv), read_csv(args.baseline) if args.baseline else None)
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
