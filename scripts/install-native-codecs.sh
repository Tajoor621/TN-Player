#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
test -f .native/dist/TN-Player-native-sources.tar.xz
mkdir -p software-codecs/src/main/jniLibs software-codecs/src/main/assets/native-notices
cp -R .native/dist/jniLibs/. software-codecs/src/main/jniLibs/
cp -R .native/dist/notices/. software-codecs/src/main/assets/native-notices/
