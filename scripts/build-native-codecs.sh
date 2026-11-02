#!/usr/bin/env bash
# Reproducible source builds: no downloaded AARs, GPL/nonfree components or encoders.
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)
cd "$root"
: "${ANDROID_HOME:?Set ANDROID_HOME to the Android SDK}"
ndk="$ANDROID_HOME/ndk/27.2.12479018"
cmake="$ANDROID_HOME/cmake/3.22.1/bin/cmake"
tools="$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin"
export PATH="$ANDROID_HOME/cmake/3.22.1/bin:$tools:$PATH"
work="$root/.native"; sources="$work/sources"; dist="$work/dist"
mkdir -p "$sources" "$dist/jniLibs" "$dist/notices" "$work/config"
python3 - <<'PY'
import json,subprocess,pathlib,os
lock=json.loads(pathlib.Path('software-codecs/native-lock.json').read_text())
for name in ['ffmpeg','libvpx','libgav1','cpu_features']:
 item=lock[name];p=pathlib.Path('.native/sources')/name
 if os.environ.get('CODEC_SOURCE_MODE')=='local':
  assert p.is_dir(),f'Extract corresponding sources first: {p}'
  continue
 if not (p/'.git').exists():
  p.mkdir(exist_ok=True);subprocess.run(['git','init','-q',str(p)],check=True)
  subprocess.run(['git','-C',str(p),'remote','add','origin',item['url']],check=True)
 subprocess.run(['git','-C',str(p),'fetch','--depth=1','origin',item['commit']],check=True)
 subprocess.run(['git','-C',str(p),'checkout','--detach','--force','FETCH_HEAD'],check=True)
 assert subprocess.check_output(['git','-C',str(p),'rev-parse','HEAD'],text=True).strip()==item['commit']
PY
jobs=${CODEC_JOBS:-4}
for abi in armeabi-v7a arm64-v8a x86 x86_64; do
  case "$abi" in
    armeabi-v7a) arch=arm; cpu=armv7-a; triple=armv7a-linux-androideabi; vpx=armv7; asm="$tools/armv7a-linux-androideabi21-clang" ;;
    arm64-v8a) arch=aarch64; cpu=armv8-a; triple=aarch64-linux-android; vpx=arm64; asm="$tools/aarch64-linux-android21-clang" ;;
    x86) arch=x86; cpu=i686; triple=i686-linux-android; vpx=x86; asm=yasm ;;
    x86_64) arch=x86_64; cpu=x86-64; triple=x86_64-linux-android; vpx=x86_64; asm=yasm ;;
  esac
  prefix="$work/prefix/$abi"; build="$work/build/$abi"
  mkdir -p "$prefix" "$build/ffmpeg" "$build/vpx" "$dist/jniLibs/$abi" "$work/config/$abi"
  ffmpeg_arch_options=()
  if [[ "$abi" == x86* ]]; then ffmpeg_arch_options+=(--disable-asm); fi
  (
    cd "$build/ffmpeg"
    "$sources/ffmpeg/configure" --prefix="$prefix" --target-os=android --arch="$arch" --cpu="$cpu" \
      --enable-cross-compile --cc="$tools/${triple}21-clang" --cxx="$tools/${triple}21-clang++" \
      --ar="$tools/llvm-ar" --nm="$tools/llvm-nm" --ranlib="$tools/llvm-ranlib" --strip="$tools/llvm-strip" \
      --enable-pic --enable-static --disable-shared --disable-everything --disable-autodetect \
      --disable-gpl --disable-nonfree --disable-version3 --disable-network --disable-programs --disable-doc \
      --disable-avdevice --disable-avformat --disable-avfilter --disable-swscale --disable-postproc \
      --enable-swresample --enable-decoder=ac3,eac3,dca,truehd \
      --extra-cflags="-O2 -fPIC -ffunction-sections -fdata-sections" --extra-ldflags="-Wl,-z,max-page-size=16384" \
      --disable-vulkan --disable-symver "${ffmpeg_arch_options[@]}"
    make -j"$jobs"; make install
    cp config.h config_components.h ffbuild/config.mak "$work/config/$abi/"
    if grep -Eq '^#define CONFIG_(GPL|NONFREE|VERSION3) 1' config.h; then echo 'Unexpected license configuration' >&2; exit 1; fi
  )
  (
    cd "$build/vpx"
    CC="$tools/${triple}21-clang" CXX="$tools/${triple}21-clang++" AR="$tools/llvm-ar" \
      LD="$tools/${triple}21-clang" AS="$asm" STRIP="$tools/llvm-strip" \
      "$sources/libvpx/configure" --target="$vpx-android-gcc" --prefix="$prefix" \
      --enable-pic --enable-static --disable-shared --disable-vp8 --disable-vp9-encoder \
      --enable-vp9-highbitdepth --disable-examples --disable-tools --disable-docs \
      --disable-unit-tests --disable-webm-io --disable-libyuv
    make -j"$jobs"; make install
    cp vpx_config.h "$work/config/$abi/"
  )
  "$cmake" -S software-codecs/src/main/cpp -B "$build/jni" -G Ninja \
    -DCMAKE_TOOLCHAIN_FILE="$ndk/build/cmake/android.toolchain.cmake" -DANDROID_ABI="$abi" \
    -DANDROID_PLATFORM=android-21 -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release \
    -DCODEC_PREFIX="$prefix" -DCODEC_SOURCES="$sources"
  "$cmake" --build "$build/jni" --target ffmpegJNI vpxV2JNI gav1JNI --parallel "$jobs"
  for library in ffmpegJNI vpxV2JNI gav1JNI; do
    cp "$build/jni/lib$library.so" "$dist/jniLibs/$abi/"
    "$tools/llvm-strip" --strip-unneeded "$dist/jniLibs/$abi/lib$library.so"
    "$tools/llvm-readelf" -l "$dist/jniLibs/$abi/lib$library.so" > "$work/config/$abi/$library-elf.txt"
  done
  # Preserve the complete JNI translation unit for LGPL relinking in addition to source.
  find "$build/jni" -name '*ffmpeg_jni.cc.o' -exec cp '{}' "$work/config/$abi/ffmpeg_jni.cc.o" \;
done
mkdir -p "$sources/ndk_cpu_features"
cp -R "$ndk/sources/android/cpufeatures/." "$sources/ndk_cpu_features/"
bash scripts/generate-codec-fixtures.sh
python3 scripts/package-native-sources.py
bash scripts/install-native-codecs.sh
