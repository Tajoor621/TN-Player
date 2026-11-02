#!/usr/bin/env python3
"""Offline synthetic references. Host encoder tools are not packaged in the app."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def probe(path):
    return json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(path)]))


def metadata(path, expected):
    data = probe(path)
    video = next(s for s in data["streams"] if s["codec_type"] == "video")
    depth = 10 if "10" in video.get("pix_fmt", "") else 8
    for key in ("width", "height"):
        if video[key] != expected[key]:
            raise ValueError(f"{path.name}: unexpected {key}")
    if (video["codec_name"] != expected["codec"] or depth != expected["bit_depth"]
            or video.get("color_transfer") != expected["transfer"]):
        raise ValueError(f"{path.name}: codec/bit-depth/transfer mismatch")
    if depth == 10 and (video.get("color_primaries") != "bt2020"
                        or video.get("color_space") != "bt2020nc"):
        raise ValueError("HDR reference must have BT.2020 color information")
    with path.open("rb") as source:
        sha = hashlib.file_digest(source, "sha256").hexdigest()
    return dict(expected, sha256=sha,
                size_bytes=path.stat().st_size, duration_s=float(data["format"]["duration"]),
                pix_fmt=video["pix_fmt"], profile=video.get("profile", ""),
                primaries=video.get("color_primaries", ""), matrix=video.get("color_space", ""))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--verify-only", action="store_true")
    parser.add_argument("--directory", type=Path, default=Path("test-assets/benchmarks/media"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parent.parent
    refs = json.loads((root / "test-assets/benchmarks/references.json").read_text())["references"]
    args.directory.mkdir(parents=True, exist_ok=True)
    manifest = []
    for ref in refs:
        path = args.directory / ref["name"]
        if not args.verify_only:
            hdr = ref["bit_depth"] == 10
            # Convert the SDR synthetic source through linear light into BT.2020 PQ,
            # rather than merely relabel SDR bytes as HDR. libzimg is a host requirement.
            filters = ("format=yuv420p,zscale=pin=bt709:tin=bt709:min=bt709:rin=limited:"
                       "p=bt2020:t=smpte2084:m=bt2020nc:r=limited,format=yuv420p10le") if hdr else "format=yuv420p"
            cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-threads", "2",
                   "-f", "lavfi", "-i", f"testsrc2=size={ref['width']}x{ref['height']}:rate=24:duration=15",
                   "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=48000:duration=15",
                   "-filter_threads", "1", "-vf", filters, "-c:v", "libx264" if ref["codec"] == "h264" else "libx265",
                   "-preset", "ultrafast", "-crf", "26", "-g", "24", "-threads:v", "2",
                   "-color_primaries", "bt2020" if hdr else "bt709", "-color_trc", ref["transfer"],
                   "-colorspace", "bt2020nc" if hdr else "bt709", "-color_range", "tv"]
            if ref["codec"] == "hevc":
                params = "pools=2:frame-threads=2:log-level=error"
                if hdr:
                    params += ":hdr10=1:repeat-headers=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,1):max-cll=1000,400"
                cmd += ["-x265-params", params, "-tag:v", "hvc1"]
            cmd += ["-c:a", "aac", "-b:a", "96k", "-ac", "2", "-movflags", "+faststart", "-shortest", str(path)]
            subprocess.run(cmd, check=True)
        manifest.append(metadata(path, ref))
    (args.directory / "manifest.json").write_text(json.dumps({"schema": 1, "references": manifest}, indent=2) + "\n")
    print(f"Verified {len(manifest)} offline references in {args.directory}")


if __name__ == "__main__":
    main()
