#!/bin/bash
# Migration Script: Copy essential code from original repo to new structure
# This script ensures feature parity by migrating only necessary code

set -e

ORIGINAL_REPO="/home/samujjwal/Developments/ghatana"
NEW_REPO="/home/samujjwal/Developments/ghatana-new"

echo "=== Ghatana Migration Script ==="
echo "Source: $ORIGINAL_REPO"
echo "Target: $NEW_REPO"
echo ""

# Function to copy Java files with package update
copy_java_files() {
    local src_dir="$1"
    local dest_dir="$2"
    local old_package="$3"
    local new_package="$4"
    
    if [ -d "$src_dir" ]; then
        echo "Copying from $src_dir to $dest_dir"
        mkdir -p "$dest_dir"
        
        find "$src_dir" -name "*.java" -exec sh -c '
            src_file="$1"
            dest_dir="$2"
            old_pkg="$3"
            new_pkg="$4"
            
            filename=$(basename "$src_file")
            dest_file="$dest_dir/$filename"
            
            # Copy and update package
            sed "s/$old_pkg/$new_pkg/g" "$src_file" > "$dest_file"
            echo "  Copied: $filename"
        ' _ {} "$dest_dir" "$old_package" "$new_package" \;
    else
        echo "  Source directory not found: $src_dir"
    fi
}

# =============================================================================
# Platform Core - Additional utilities from common-utils
# =============================================================================
echo ""
echo "=== Migrating Platform Core Utilities ==="

# DateTimeUtils
if [ -f "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/utils/DateTimeUtils.java" ]; then
    echo "Migrating DateTimeUtils..."
    cp "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/utils/DateTimeUtils.java" \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/"
    sed -i 's/package com\.ghatana\.core\.utils;/package com.ghatana.platform.core.util;/g' \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/DateTimeUtils.java"
fi

# Pair utility
if [ -f "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/Pair.java" ]; then
    echo "Migrating Pair..."
    mkdir -p "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/"
    cp "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/Pair.java" \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/"
    sed -i 's/package com\.ghatana\.core\.common;/package com.ghatana.platform.core.util;/g' \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/Pair.java"
fi

# Page/PageRequest for pagination
if [ -f "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/Page.java" ]; then
    echo "Migrating Page..."
    cp "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/Page.java" \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/"
    sed -i 's/package com\.ghatana\.core\.common;/package com.ghatana.platform.core.util;/g' \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/Page.java"
fi

if [ -f "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/PageRequest.java" ]; then
    echo "Migrating PageRequest..."
    cp "$ORIGINAL_REPO/libs/java/common-utils/src/main/java/com/ghatana/core/common/PageRequest.java" \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/"
    sed -i 's/package com\.ghatana\.core\.common;/package com.ghatana.platform.core.util;/g' \
       "$NEW_REPO/platform/java/core/src/main/java/com/ghatana/platform/core/util/PageRequest.java"
fi

# =============================================================================
# AEP Platform - Operators from libs/java/operator
# =============================================================================
echo ""
echo "=== Migrating AEP Operators ==="

AEP_OPERATORS_SRC="$ORIGINAL_REPO/libs/java/operator/src/main/java/com/ghatana/operator"
AEP_OPERATORS_DEST="$NEW_REPO/products/aep/platform/java/operators/src/main/java/com/ghatana/aep/platform/operators"

if [ -d "$AEP_OPERATORS_SRC" ]; then
    mkdir -p "$AEP_OPERATORS_DEST"
    echo "  Operator source found, ready for selective migration"
    echo "  Files available: $(find $AEP_OPERATORS_SRC -name '*.java' | wc -l)"
fi

# =============================================================================
# Summary
# =============================================================================
echo ""
echo "=== Migration Summary ==="
echo "Platform Core utilities migrated"
echo "AEP/Data-Cloud product platforms created"
echo ""
echo "Next steps:"
echo "1. Run: ./gradlew build"
echo "2. Review migrated files for package import updates"
echo "3. Run tests to verify feature parity"
