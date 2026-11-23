#!/usr/bin/env bash
# Builds FFmpeg (LGPL-2.1+, audio decoders only) as shared libraries for the given ABIs.
# Usage: build_ffmpeg.sh [abi ...]   (default: arm64-v8a armeabi-v7a)
# Output: third_party/out/ffmpeg/<abi>/{include,lib}; the .so files are also copied to src/main/jniLibs/<abi>/.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
resolve_ndk

SRC="${THIRD_PARTY}/ffmpeg"
fetch_pinned "${FFMPEG_REPO}" "${FFMPEG_TAG}" "${FFMPEG_COMMIT}" "${SRC}"

JOBS="$(nproc 2>/dev/null || echo 4)"
LINK_FLAGS="-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384"

for abi in $(abi_list "$@"); do
  case "${abi}" in
    armeabi-v7a)
      ARCH_FLAGS=(--arch=arm --cpu=armv7-a)
      TRIPLE="armv7a-linux-androideabi"
      TOOL_PREFIX="arm-linux-androideabi"
      EXTRA_CFLAGS="-march=armv7-a -mfloat-abi=softfp"
      EXTRA_LDFLAGS="${LINK_FLAGS} -Wl,--fix-cortex-a8"
      ;;
    arm64-v8a)
      ARCH_FLAGS=(--arch=aarch64 --cpu=armv8-a)
      TRIPLE="aarch64-linux-android"
      TOOL_PREFIX="aarch64-linux-android"
      EXTRA_CFLAGS=""
      EXTRA_LDFLAGS="${LINK_FLAGS}"
      ;;
    x86_64)
      ARCH_FLAGS=(--arch=x86_64 --cpu=x86-64 --disable-asm)
      TRIPLE="x86_64-linux-android"
      TOOL_PREFIX="x86_64-linux-android"
      EXTRA_CFLAGS=""
      EXTRA_LDFLAGS="${LINK_FLAGS}"
      ;;
    *) echo "Unsupported ABI: ${abi}" >&2; exit 1 ;;
  esac

  BUILD="${THIRD_PARTY}/build/ffmpeg/${abi}"
  PREFIX="${OUT_DIR}/ffmpeg/${abi}"
  rm -rf "${BUILD}" "${PREFIX}"
  mkdir -p "${BUILD}" "${PREFIX}"

  DECODER_FLAGS=()
  for d in ${FFMPEG_DECODERS}; do DECODER_FLAGS+=("--enable-decoder=${d}"); done

  echo "=== FFmpeg ${FFMPEG_TAG} for ${abi} ==="
  (
    cd "${BUILD}"
    "${SRC}/configure" \
      --prefix="${PREFIX}" \
      --target-os=android \
      "${ARCH_FLAGS[@]}" \
      --enable-cross-compile \
      --cross-prefix="${TOOLCHAIN_PREFIX}/${TOOL_PREFIX}-" \
      --cc="${TOOLCHAIN_PREFIX}/${TRIPLE}${ANDROID_API}-clang" \
      --cxx="${TOOLCHAIN_PREFIX}/${TRIPLE}${ANDROID_API}-clang++" \
      --nm="${TOOLCHAIN_PREFIX}/llvm-nm" \
      --ar="${TOOLCHAIN_PREFIX}/llvm-ar" \
      --ranlib="${TOOLCHAIN_PREFIX}/llvm-ranlib" \
      --strip="${TOOLCHAIN_PREFIX}/llvm-strip" \
      --extra-cflags="${EXTRA_CFLAGS}" \
      --extra-ldflags="${EXTRA_LDFLAGS}" \
      --enable-shared --disable-static \
      --disable-doc --disable-programs --disable-everything \
      --disable-avdevice --disable-avformat --disable-swscale --disable-postproc --disable-avfilter \
      --disable-symver --disable-v4l2-m2m --disable-vulkan \
      --enable-swresample \
      "${DECODER_FLAGS[@]}"

    # License guard: refuse to continue unless this is a plain LGPL build.
    grep -q '#define FFMPEG_LICENSE "LGPL version 2.1 or later"' config.h \
      || { echo "ERROR: FFmpeg is not configured as LGPL 2.1+ (see config.h)"; grep FFMPEG_LICENSE config.h; exit 1; }
    grep -q '#define CONFIG_GPL 0' config.h || { echo "ERROR: CONFIG_GPL is enabled"; exit 1; }
    grep -q '#define CONFIG_NONFREE 0' config.h || { echo "ERROR: CONFIG_NONFREE is enabled"; exit 1; }
    grep -q '#define CONFIG_VERSION3 0' config.h || { echo "ERROR: CONFIG_VERSION3 is enabled"; exit 1; }
    echo "License check passed: $(grep FFMPEG_LICENSE config.h)"

    make -j"${JOBS}"
    make install-libs install-headers
  )

  # Shared libraries without version suffixes (Android only loads names ending in .so).
  mkdir -p "${JNILIBS_DIR}/${abi}"
  for lib in avcodec avutil swresample; do
    f="$(ls "${PREFIX}"/lib/lib${lib}*.so 2>/dev/null | head -n1 || true)"
    [[ -n "${f}" ]] || { echo "ERROR: lib${lib}.so not produced for ${abi}"; ls -la "${PREFIX}/lib"; exit 1; }
    cp -L "${f}" "${JNILIBS_DIR}/${abi}/lib${lib}.so"
    "${TOOLCHAIN_PREFIX}/llvm-strip" --strip-unneeded "${JNILIBS_DIR}/${abi}/lib${lib}.so"
  done
  ls -l "${JNILIBS_DIR}/${abi}"
done
