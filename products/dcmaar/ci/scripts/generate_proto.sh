#!/bin/bash

set -e

# Create output directory
mkdir -p proto/src/generated

# Generate Rust code using protoc and tonic-build
for proto_file in $(find proto -name "*.proto"); do
  echo "Generating code for $proto_file..."
  protoc \
    --prost_out=proto/src/generated \
    --tonic_out=proto/src/generated \
    -Iproto \
    -I$(go env GOPATH)/src/github.com/grpc-ecosystem/grpc-gateway/third_party/googleapis \
    $proto_file
done

echo "Rust code generation complete!"
