#!/bin/bash

# Bundle Analysis Script
# Usage: ./analyze-bundle.sh [app_path]

APP_PATH=${1:-"apps/tutorputor-admin"}
MAX_BUNDLE_SIZE=500 # KB

echo "🔍 Starting Bundle Analysis for $APP_PATH..."

cd $APP_PATH

# 1. Build the project
echo "📦 Building project..."
pnpm build

# 2. Check output directory
DIST_DIR="dist/assets"
if [ ! -d "$DIST_DIR" ]; then
    echo "❌ Build failed or dist directory not found."
    exit 1
fi

# 3. Analyze JS files
echo "📊 Analyzing JavaScript chunks..."
LARGE_CHUNKS=0

for file in $DIST_DIR/*.js; do
    if [ -f "$file" ]; then
        SIZE_KB=$(du -k "$file" | cut -f1)
        FILENAME=$(basename "$file")
        
        if [ $SIZE_KB -gt $MAX_BUNDLE_SIZE ]; then
            echo "⚠️  WARNING: $FILENAME is ${SIZE_KB}KB (Exceeds ${MAX_BUNDLE_SIZE}KB limit)"
            LARGE_CHUNKS=$((LARGE_CHUNKS + 1))
        else
            echo "✅ $FILENAME: ${SIZE_KB}KB"
        fi
    fi
done

# 4. Summary
echo "----------------------------------------"
if [ $LARGE_CHUNKS -eq 0 ]; then
    echo "🎉 Success! All chunks are within the ${MAX_BUNDLE_SIZE}KB limit."
    exit 0
else
    echo "❌ Failure: $LARGE_CHUNKS chunks exceed the size limit."
    exit 1
fi
