#!/usr/bin/env bash
# Shared helpers for the native decoder build scripts. Source this file; do not execute it.
set -euo pipefail

TOOLS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$(cd "${TOOLS_DIR}/.." && pwd)"
JNI_DIR="${MODULE_DIR}/src/main/jni"
THIRD_PARTY="${JNI_DIR}/third_party"
OUT_DIR="${THIRD_PARTY}/out"
JNILIBS_DIR="${MODULE_DIR}/src/main/jniLibs"

# shellcheck source=versions.env
source "${TOOLS_DIR}/versions.env"

case "$(uname -s)" in
  Linux)  HOST_PLATFORM="linux-x86_64" ;;
  Darwin) HOST_PLATFORM="darwin-x86_64" ;;
  *) echo "Unsupported host OS: $(uname -s)" >&2; exit 1 ;;
esac

resolve_ndk() {
  local candidates=()
  [[ -n "${NDK_PATH:-}" ]] && candidates+=("${NDK_PATH}")
  [[ -n "${ANDROID_HOME:-}" ]] && candidates+=("${ANDROID_HOME}/ndk/${NDK_VERSION}")
  [[ -n "${ANDROID_SDK_ROOT:-}" ]] && candidates+=("${ANDROID_SDK_ROOT}/ndk/${NDK_VERSION}")
  local c
  for c in "${candidates[@]}"; do
    if [[ -d "${c}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin" ]]; then
      NDK_PATH="${c}"
      TOOLCHAIN_PREFIX="${c}/toolchains/llvm/prebuilt/${HOST_PLATFORM}/bin"
      echo "Using NDK: ${NDK_PATH}"
      return 0
    fi
  done
  echo "NDK ${NDK_VERSION} not found. Install it (sdkmanager \"ndk;${NDK_VERSION}\") or set NDK_PATH." >&2
  return 1
}

# fetch_pinned <repo-url> <tag> <expected-commit> <dest-dir> [mirror-url]
# Shallow-clones a tag and refuses to continue unless HEAD is exactly the pinned commit.
fetch_pinned() {
  local repo="$1" tag="$2" commit="$3" dest="$4" mirror="${5:-}"
  if [[ -d "${dest}/.git" ]] && [[ "$(git -C "${dest}" rev-parse HEAD 2>/dev/null || true)" == "${commit}" ]]; then
    echo "Already at ${commit}: ${dest}"
    return 0
  fi
  rm -rf "${dest}"
  mkdir -p "$(dirname "${dest}")"
  if ! git clone --quiet --depth 1 --branch "${tag}" "${repo}" "${dest}"; then
    if [[ -n "${mirror}" ]]; then
      echo "Primary clone failed, trying mirror ${mirror}" >&2
      rm -rf "${dest}"
      git clone --quiet --depth 1 --branch "${tag}" "${mirror}" "${dest}"
    else
      return 1
    fi
  fi
  local actual
  actual="$(git -C "${dest}" rev-parse HEAD)"
  if [[ "${actual}" != "${commit}" ]]; then
    echo "ERROR: ${repo} tag ${tag} resolved to ${actual}, expected ${commit}. Refusing to build." >&2
    exit 1
  fi
  echo "Fetched ${repo} ${tag} @ ${actual}"
}

abi_list() {
  # Default ABIs for release. x86_64 (emulator) is only built when requested.
  if [[ $# -gt 0 ]]; then echo "$@"; else echo "arm64-v8a armeabi-v7a"; fi
}
