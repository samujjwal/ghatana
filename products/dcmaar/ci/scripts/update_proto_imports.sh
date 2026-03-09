#!/usr/bin/env bash
set -euo pipefail

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

PROTO_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../proto" && pwd)"

echo -e "${GREEN}🔍 Updating proto imports...${NC}"

# Fix import paths in all proto files
find "$PROTO_DIR/dcmaar/v1" -name "*.proto" -type f | while read -r file; do
  echo -e "${YELLOW}Processing $file...${NC}"
  
  # Update common.proto import
  sed -i '' 's|import "common.proto";|import "dcmaar/v1/common.proto";|g' "$file"
  
  # Update validate/validate.proto import
  sed -i '' 's|import "validate/validate.proto";|import "validate/validate.proto";|g' "$file"
  
  # Update google/protobuf imports
  sed -i '' 's|import "google/protobuf/|import "google/protobuf/|g' "$file"
  
  # Update go_package to include the full path
  sed -i '' 's|option go_package = "github.com/samujjwal/dcmaar/proto/gen/go/pb;pb";|option go_package = "github.com/samujjwal/dcmaar/proto/gen/go/dcmaar/v1;dcmaarv1";|g' "$file"
  
  # Update package to include version
  sed -i '' 's|package dcmaar;|package dcmaar.v1;|g' "$file"
  
  # Add standard options if not present
  if ! grep -q "option java_multiple_files" "$file"; then
    sed -i '' '/^package/a \
option java_multiple_files = true;\
option java_outer_classname = "'$(basename "$file" .proto | tr '[:lower:]' '[:upper:]' | tr -d '.')'Proto";\
option java_package = "com.dcmaar.v1";\
option csharp_namespace = "Dcmaar.V1";\
option objc_class_prefix = "DCX";\
option php_namespace = "Dcmaar\\V1";\
option ruby_package = "Dcmaar::V1";' "$file"
  fi
done

echo -e "${GREEN}✅ Proto imports updated successfully!${NC}"
