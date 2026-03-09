#!/bin/bash
set -e

# Script to generate TypeScript gRPC clients from proto files
# This script generates clients for TutorPutor AI agents

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONTRACTS_DIR="$(dirname "$SCRIPT_DIR")"
PROTO_DIR="$CONTRACTS_DIR/../../../aep/services/tutorputor-ai-agents/src/main/proto"
OUTPUT_DIR="$CONTRACTS_DIR/generated/tutorputor"

echo "=== TutorPutor gRPC Client Generator ==="
echo "Proto directory: $PROTO_DIR"
echo "Output directory: $OUTPUT_DIR"

# Check if proto directory exists
if [ ! -d "$PROTO_DIR" ]; then
    echo "Error: Proto directory not found: $PROTO_DIR"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

# Install dependencies if needed
if ! command -v grpc_tools_node_protoc &> /dev/null; then
    echo "Installing grpc-tools..."
    npm install -g grpc-tools
fi

if ! command -v protoc-gen-ts &> /dev/null; then
    echo "Installing grpc_tools_node_protoc_ts..."
    npm install -g grpc_tools_node_protoc_ts
fi

# Generate TypeScript code from proto files
echo "Generating TypeScript gRPC clients..."

for proto_file in "$PROTO_DIR"/*.proto; do
    if [ -f "$proto_file" ]; then
        echo "Processing: $(basename "$proto_file")"
        
        # Generate JavaScript code
        grpc_tools_node_protoc \
            --js_out=import_style=commonjs,binary:"$OUTPUT_DIR" \
            --grpc_out=grpc_js:"$OUTPUT_DIR" \
            --plugin=protoc-gen-grpc="$(which grpc_tools_node_protoc_plugin)" \
            -I "$PROTO_DIR" \
            "$proto_file"
        
        # Generate TypeScript definitions
        grpc_tools_node_protoc \
            --ts_out=grpc_js:"$OUTPUT_DIR" \
            --plugin=protoc-gen-ts="$(which protoc-gen-ts)" \
            -I "$PROTO_DIR" \
            "$proto_file"
    fi
done

echo "✓ gRPC clients generated successfully in $OUTPUT_DIR"
echo ""
echo "To use in TypeScript:"
echo "  import { ContentGenerationServiceClient } from '@tutorputor/contracts/generated/tutorputor/tutorputor_content_generation_grpc_pb';"
echo "  import { GenerateClaimsRequest } from '@tutorputor/contracts/generated/tutorputor/tutorputor_content_generation_pb';"
