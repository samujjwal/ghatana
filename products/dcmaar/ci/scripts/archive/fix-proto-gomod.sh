#!/bin/bash
# Fix for protobuf module generation

# This script ensures that the go.mod file is created properly after protobuf generation
# regardless of whether the Makefile shell variable scoping works correctly

cd "$(dirname "$0")"
cd ..

echo "Current directory: $(pwd)"
echo "Checking for proto/gen/go/pb/dcmaar/v1..."

# Check if protobuf files exist
if [ -d "proto/gen/go/pb/dcmaar/v1" ]; then
    echo "Directory found: proto/gen/go/pb/dcmaar/v1"
    file_count=$(find proto/gen/go/pb/dcmaar/v1 -name "*.go" | wc -l)
    echo "Found $file_count .go files"
    
    if [ "$file_count" -gt 0 ]; then
        OUT_BASE="proto/gen/go/pb"
        
        echo "Ensuring go.mod exists at $OUT_BASE/go.mod"
        
        # Create the go.mod file if it doesn't exist
        if [ ! -f "$OUT_BASE/go.mod" ]; then
            echo "Creating $OUT_BASE/go.mod"
            mkdir -p "$OUT_BASE"
            cat > "$OUT_BASE/go.mod" << 'EOF'
module github.com/samujjwal/dcmaar/proto/gen/go/pb

go 1.25

require (
	github.com/envoyproxy/protoc-gen-validate v1.2.1
	google.golang.org/grpc v1.75.1
	google.golang.org/protobuf v1.36.9
)
EOF
            
            # Tidy dependencies
            (cd "$OUT_BASE" && go mod tidy) || true
            echo "✅ go.mod created and tidied successfully"
        else
            echo "✅ go.mod already exists at $OUT_BASE/go.mod"
        fi
    else
        echo "Warning: Directory exists but no .go files found"
        exit 1
    fi
else
    echo "Warning: Directory proto/gen/go/pb/dcmaar/v1 not found"
    ls -la proto/gen/go/pb/ || echo "proto/gen/go/pb/ does not exist"
    exit 1
fi