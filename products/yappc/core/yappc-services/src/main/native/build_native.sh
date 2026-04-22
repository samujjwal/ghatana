#!/bin/bash
#
# Build script for the Tree-sitter JNI native bridge.
#
# Prerequisites:
#   - cmake >= 3.16
#   - C compiler (clang / gcc)
#   - tree-sitter core library and headers installed
#     (e.g. brew install tree-sitter, or build from source)
#   - JAVA_HOME set correctly
#
# Usage:
#   ./build_native.sh [platform] [build_dir]
#
set -euo pipefail

PLATFORM=${1:-$(uname -s | tr '[:upper:]' '[:lower:]')}
BUILD_DIR=${2:-build}
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Building tree-sitter JNI bridge for platform: ${PLATFORM}"
echo "Build directory: ${SCRIPT_DIR}/${BUILD_DIR}"

mkdir -p "${SCRIPT_DIR}/${BUILD_DIR}"
cd "${SCRIPT_DIR}"

cmake -S . -B "${BUILD_DIR}" \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="${SCRIPT_DIR}/${BUILD_DIR}"

cmake --build "${BUILD_DIR}" --parallel

echo ""
echo "Build complete. Native library should be at:"
find "${SCRIPT_DIR}/${BUILD_DIR}" -name "libtree_sitter_jni.*" -o -name "tree_sitter_jni.dll" || true
echo ""
echo "Copy the library to a location on java.library.path, or add the build dir to -Djava.library.path"
