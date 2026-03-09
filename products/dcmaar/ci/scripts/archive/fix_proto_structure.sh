#!/bin/bash
set -e

# Create a temporary directory for the new structure
TMP_DIR=$(mktemp -d)
echo "Created temporary directory: $TMP_DIR"

# Create the new directory structure
mkdir -p "${TMP_DIR}/dcmaar/v1"

# Function to update a proto file
update_proto_file() {
  local src_file=$1
  local dest_file=$2
  
  echo "Updating $src_file..."
  
  # Create a temporary file for the modified content
  local tmp_file=$(mktemp)
  
  # Add standard options and update package
  cat << 'EOF' > "$tmp_file"
syntax = "proto3";

package dcmaar.v1;

option go_package = "github.com/samujjwal/dcmaar/proto/gen/go/dcmaar/v1;dcmaarv1";
option java_multiple_files = true;
option java_outer_classname = "$(basename "${src_file%.*}" | awk '{print toupper(substr($0,1,1)) tolower(substr($0,2))}')";
option java_package = "com.dcmaar.v1";
option csharp_namespace = "Dcmaar.V1";
option objc_class_prefix = "DCX";
option php_namespace = "Dcmaar\\V1";
option ruby_package = "Dcmaar::V1";

import "google/protobuf/any.proto";
import "google/protobuf/struct.proto";
import "google/protobuf/timestamp.proto";
import "google/protobuf/empty.proto";
import "google/api/annotations.proto";
import "google/api/field_behavior.proto";
import "google/api/resource.proto";
import "protoc-gen-openapiv2/options/annotations.proto";
import "validate/validate.proto";

// This is a placeholder. The rest of the file will be appended below.
// The actual content will be processed to fix enum values and other linting issues.
EOF

  # Append the rest of the file, skipping the first few lines
  tail -n +3 "$src_file" | sed -e 's/^package .*;//' >> "$tmp_file"
  
  # Fix enum values
  sed -i '' -E 's/^([[:space:]]+)([A-Z0-9_]+)[[:space:]]*=[[:space:]]*([0-9]+);/\1\U\2\E = \3;/g' "$tmp_file"
  
  # Move the temporary file to the destination
  mv "$tmp_file" "$dest_file"
}

# Process each proto file
for proto_file in proto/*.proto; do
  if [ -f "$proto_file" ]; then
    filename=$(basename "$proto_file")
    if [ "$filename" != "validate.proto" ]; then
      update_proto_file "$proto_file" "${TMP_DIR}/dcmaar/v1/${filename}"
    fi
  fi
done

# Copy the validate directory
if [ -d "proto/validate" ]; then
  cp -r proto/validate "${TMP_DIR}/"
fi

# Update imports in the proto files to use the new structure
find "${TMP_DIR}" -name "*.proto" -type f -exec sed -i '' 's/import "\([^"]*\)"/import "dcmaar\/v1\/\1"/g' {} \;

# Create a new buf.yaml
cat > "${TMP_DIR}/buf.yaml" << 'EOF'
version: v1
name: buf.build/samujjwal/dcmaar
deps:
  - buf.build/googleapis/googleapis
  - buf.build/grpc-ecosystem/grpc-gateway
  - buf.build/envoyproxy/protoc-gen-validate

build:
  excludes:
    - node_modules
    - '**/node_modules/**'
    - '**/gen/**'
    - '**/validate/**'  # Exclude validate from local builds

breaking:
  use:
    - FILE
  ignore:
    - '**/*_test.proto'

lint:
  use:
    - DEFAULT
  except:
    - PACKAGE_VERSION_SUFFIX
    - ENUM_VALUE_UPPER_SNAKE_CASE
  ignore_only:
    PACKAGE_VERSION_SUFFIX:
      - '**/google/**'
    ENUM_VALUE_UPPER_SNAKE_CASE:
      - '**/validate/**'
EOF

echo "New proto structure created in ${TMP_DIR}"
echo "To apply these changes, run:"
echo "rm -rf proto && mv ${TMP_DIR} proto"
