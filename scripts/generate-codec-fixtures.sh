#!/usr/bin/env bash
# Synthetic sine/test-pattern fixtures created by TN-Player; no third-party movie/music content.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
mkdir -p .native/dist/fixtures
for pair in 'ac3 ac3' 'eac3 eac3' 'dts dca' 'truehd truehd' 'unsupported vorbis'; do
  read -r name encoder <<< "$pair"
  if [ "$encoder" = vorbis ]; then encoder=libvorbis; fi
  ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'sine=frequency=440:sample_rate=48000:duration=4' \
    -ac 2 -c:a "$encoder" -strict -2 ".native/dist/fixtures/$name.mka"
done
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=160x90:rate=12:duration=3' -an -c:v libvpx-vp9 -b:v 80k .native/dist/fixtures/vp9.webm
ffmpeg -hide_banner -loglevel error -y -f lavfi -i 'testsrc2=size=160x90:rate=12:duration=3' -an -c:v libaom-av1 -cpu-used 8 -b:v 80k .native/dist/fixtures/av1.mkv
