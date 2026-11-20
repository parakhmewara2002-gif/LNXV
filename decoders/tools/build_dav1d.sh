#!/usr/bin/env bash
# Builds dav1d (BSD-2-Clause) as a static library per ABI; it is linked into libdav1dJNI.so by CMake.
# Usage: build_dav1d.sh [abi ...]   Requires: meson, ninja.
set -euo pipefail
# shellcheck source=common.sh
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"
resolve_ndk
command -v meson >/dev/null || { echo "meson not found (pip install meson)"; exit 1; }
command -v ninja >/dev/null || { echo "ninja not found (pip install ninja)"; exit 1; }

SRC="${THIRD_PARTY}/dav1d"
fetch_pinned "${DAV1D_REPO}" "${DAV1D_TAG}" "${DAV1D_COMMIT}" "${SRC}" "${DAV1D_REPO_MIRROR}"

for abi in $(abi_list "$@"); do
  case "${abi}" in
    armeabi-v7a) CROSS="arm-android.meson";     TRIPLE="armv7a-linux-androideabi"; AR_PREFIX="arm-linux-androideabi" ;;
    arm64-v8a)   CROSS="aarch64-android.meson"; TRIPLE="aarch64-linux-android";    AR_PREFIX="aarch64-linux-android" ;;
    x86_64)      CROSS="x86_64-android.meson";  TRIPLE="x86_64-linux-android";     AR_PREFIX="x86_64-linux-android" ;;
    *) echo "Unsupported ABI: ${abi}" >&2; exit 1 ;;
  esac
  BUILD="${THIRD_PARTY}/build/dav1d/${abi}"
  DEST="${OUT_DIR}/dav1d/${abi}"
  rm -rf "${BUILD}" "${DEST}"
  mkdir -p "${BUILD}" "${DEST}"

  CROSS_FILE="${BUILD}/cross.meson"
  sed -e "s|^c = .*|c = '${TOOLCHAIN_PREFIX}/${TRIPLE}${ANDROID_API}-clang'|" \
      -e "s|^cpp = .*|cpp = '${TOOLCHAIN_PREFIX}/${TRIPLE}${ANDROID_API}-clang++'|" \
      -e "s|^ar = .*|ar = '${TOOLCHAIN_PREFIX}/llvm-ar'|" \
      -e "s|^strip = .*|strip = '${TOOLCHAIN_PREFIX}/llvm-strip'|" \
      "${SRC}/package/crossfiles/${CROSS}" > "${CROSS_FILE}"
  # Page size for 16 KB devices only matters for the final .so (set in CMake); static lib needs -fPIC.
  echo "=== dav1d ${DAV1D_TAG} for ${abi} ==="
  cat "${CROSS_FILE}"
  meson setup "${BUILD}/b" "${SRC}" --cross-file="${CROSS_FILE}" \
    --default-library=static --buildtype=release \
    -Denable_tools=false -Denable_tests=false -Denable_examples=false \
    -Db_pie=true -Dc_args=-fPIC
  ninja -C "${BUILD}/b"
  cp "${BUILD}/b/src/libdav1d.a" "${DEST}/libdav1d.a"
  # version.h is generated into the build tree; JNI needs it next to the sources' include dir.
  mkdir -p "${DEST}/include/dav1d"
  cp "${SRC}"/include/dav1d/*.h "${DEST}/include/dav1d/"
  [[ -f "${BUILD}/b/include/dav1d/version.h" ]] && cp "${BUILD}/b/include/dav1d/version.h" "${DEST}/include/dav1d/version.h"
  [[ -f "${DEST}/include/dav1d/version.h" ]] || { echo "ERROR: dav1d version.h missing"; exit 1; }
  ls -l "${DEST}/libdav1d.a"
done
