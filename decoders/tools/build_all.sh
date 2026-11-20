#!/usr/bin/env bash
# Fetches pinned sources and builds FFmpeg (shared, audio only, LGPL) and dav1d (static) for the given ABIs.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "${HERE}/common.sh"
fetch_pinned "${CPU_FEATURES_REPO}" "${CPU_FEATURES_TAG}" "${CPU_FEATURES_COMMIT}" "${THIRD_PARTY}/cpu_features"
"${HERE}/build_ffmpeg.sh" "$@"
"${HERE}/build_dav1d.sh" "$@"
echo "Native decoders built for: $(abi_list "$@")"
