#!/bin/bash

set -euo pipefail

# Create necessary directories
mkdir -p proto/gen/rust/src

# Generate Rust code using protoc and tonic-build
for proto_file in $(find proto -name "*.proto"); do
  echo "Generating code for $proto_file..."
  protoc \
    --prost_out=proto/gen/rust/src \
    --tonic_out=proto/gen/rust/src \
    --prost_opt=paths=source_relative \
    --tonic_opt=paths=source_relative \
    -Iproto \
    -I$(go env GOPATH)/src/github.com/grpc-ecosystem/grpc-gateway/third_party/googleapis \
    $proto_file
done

# Create a lib.rs file if it doesn't exist
if [ ! -f proto/gen/rust/src/lib.rs ]; then
  cat > proto/gen/rust/src/lib.rs << 'EOF'
//! Generated protocol buffer code

// Include generated code
include!("dcmaar.v1.rs");

// Re-export the generated modules
pub mod dcmaar {
    pub mod v1 {
        include!("dcmaar.v1.rs");
    }
}
EOF
fi

echo "Rust code generation complete!"
