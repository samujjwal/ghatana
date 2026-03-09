#!/usr/bin/env bash
set -euo pipefail

# Get the absolute path of the script's directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "${SCRIPT_DIR}")"
PROTO_DIR="${ROOT_DIR}/proto"
GO_OUT_DIR="${PROTO_DIR}/gen/go"

# Set up Go environment
export GOPATH="${GOPATH:-$(go env GOPATH)}"
export PATH="${PATH}:${GOPATH}/bin"

# Create necessary directories
mkdir -p "${GO_OUT_DIR}"

# Install required Go tools if not present
if ! command -v protoc-gen-go >/dev/null 2>&1; then
  echo "[proto] Installing protoc-gen-go..."
  go install google.golang.org/protobuf/cmd/protoc-gen-go@latest
fi

if ! command -v protoc-gen-go-grpc >/dev/null 2>&1; then
  echo "[proto] Installing protoc-gen-go-grpc..."
  go install google.golang.org/grpc/cmd/protoc-gen-go-grpc@latest
fi

if ! command -v protoc-gen-validate >/dev/null 2>&1; then
  echo "[proto] Installing protoc-gen-validate..."
  go install github.com/envoyproxy/protoc-gen-validate@latest
fi

# Verify tools are installed
required_tools=(
  "protoc"
  "protoc-gen-go"
  "protoc-gen-go-grpc"
  "protoc-gen-validate"
)

for tool in "${required_tools[@]}"; do
  if ! command -v "${tool}" >/dev/null 2>&1; then
    echo "[proto][error] ${tool} not found in PATH" >&2
    echo "[proto][error] PATH: ${PATH}" >&2
    exit 1
  fi
done

# Clean existing generated files
echo "[proto] Cleaning existing generated files..."
rm -rf "${GO_OUT_DIR}/github.com"
rm -rf "${GO_OUT_DIR}/validate"
rm -rf "${GO_OUT_DIR}/google"

# Generate Go code
echo "[proto] Generating Go stubs..."
for proto_file in "${PROTO_DIR}"/*.proto; do
  if [ -f "${proto_file}" ] && [[ "${proto_file}" != *"validate/"* ]] && [[ "${proto_file}" != *"google/"* ]]; then
    filename=$(basename -- "${proto_file}")
    echo "[proto] Generating ${filename}..."
    
    # Generate base Go code with paths that avoid the pb subdirectory
    protoc \
      --go_out="${GO_OUT_DIR}" \
      --go_opt=module=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mcommon.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mactions.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mevents.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mextension.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mingest.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mmetrics.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mpolicy.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mquery.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Mstorage.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go_opt=Msync.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_out="${GO_OUT_DIR}" \
      --go-grpc_opt=module=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mcommon.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mactions.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mevents.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mextension.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mingest.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mmetrics.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mpolicy.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mquery.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Mstorage.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --go-grpc_opt=Msync.proto=github.com/samujjwal/dcmaar/proto/gen/go \
      --validate_out="lang=go,module=github.com/samujjwal/dcmaar/proto/gen/go:${GO_OUT_DIR}" \
      -I"${PROTO_DIR}" \
      -I"${GOPATH}/pkg/mod/github.com/envoyproxy/protoc-gen-validate@v1.0.4/" \
      -I"${GOPATH}/pkg/mod/" \
      -I"${PROTO_DIR}/google/protobuf" \
      "${proto_file}" 2>&1 | (grep -v "warning: Import" || true)
  fi
done

# Copy validate protos
if [ -d "${PROTO_DIR}/validate" ]; then
  echo "[proto] Copying validate protos..."
  mkdir -p "${GO_OUT_DIR}/validate"
  cp -r "${PROTO_DIR}/validate"/* "${GO_OUT_DIR}/validate/"
fi

# Copy google protos
if [ -d "${PROTO_DIR}/google" ]; then
  echo "[proto] Copying google protos..."
  mkdir -p "${GO_OUT_DIR}/google"
  cp -r "${PROTO_DIR}/google"/* "${GO_OUT_DIR}/google/"
fi

# Create go.mod in the generated directory
if [ ! -f "${GO_OUT_DIR}/go.mod" ]; then
  echo "[proto] Creating go.mod in generated directory..."
  cd "${GO_OUT_DIR}" && go mod init github.com/samujjwal/dcmaar/proto/gen/go
  cd - >/dev/null
fi

# Add required dependencies
echo "[proto] Adding required dependencies..."
cd "${GO_OUT_DIR}" && \
  go get google.golang.org/grpc && \
  go get google.golang.org/protobuf && \
  go get github.com/envoyproxy/protoc-gen-validate

cd - >/dev/null

echo "[proto] Successfully generated proto files in: ${GO_OUT_DIR}"

# Create go.mod in the generated directory
echo "[proto] Creating go.mod in generated directory..."
cat > "${GO_OUT_DIR}/go.mod" <<EOL
module github.com/samujjwal/dcmaar/proto/gen/go

go 1.20

require (
    google.golang.org/grpc v1.63.2
    google.golang.org/protobuf v1.34.1
)

require (
    github.com/golang/protobuf v1.5.4 // indirect
    golang.org/x/net v0.22.0 // indirect
    golang.org/x/sys v0.18.0 // indirect
    golang.org/x/text v0.14.0 // indirect
    google.golang.org/genproto/googleapis/rpc v0.0.0-20240304212257-790db918fca8 // indirect
)
EOL

echo "[proto] Successfully generated proto files in: ${GO_OUT_DIR}"
