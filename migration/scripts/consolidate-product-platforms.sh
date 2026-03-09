#!/bin/bash
set -e

# Script to consolidate product modules into single platform modules
BASE_DIR="."

# Function to consolidate submodules into the parent module
# verify_consolidation "parent_module_path" "submodule1" "submodule2" ...
consolidate_modules() {
    local parent_path=$1
    shift
    local submodules=("$@")

    echo "Consolidating into $parent_path..."

    # Ensure parent source dirs exist
    mkdir -p "$BASE_DIR/$parent_path/src/main/java"
    mkdir -p "$BASE_DIR/$parent_path/src/test/java"
    mkdir -p "$BASE_DIR/$parent_path/src/main/resources"
    mkdir -p "$BASE_DIR/$parent_path/src/test/resources"

    for sub in "${submodules[@]}"; do
         sub_path="$BASE_DIR/$parent_path/$sub"
         
         if [ -d "$sub_path" ]; then
            echo "  Merging $sub..."
            
            # Merge Main Java
            if [ -d "$sub_path/src/main/java" ]; then
                cp -R "$sub_path/src/main/java/" "$BASE_DIR/$parent_path/src/main/java/"
            fi
            
            # Merge Test Java
             if [ -d "$sub_path/src/test/java" ]; then
                cp -R "$sub_path/src/test/java/" "$BASE_DIR/$parent_path/src/test/java/"
            fi
            
            # Merge Resources
             if [ -d "$sub_path/src/main/resources" ]; then
                cp -R "$sub_path/src/main/resources/" "$BASE_DIR/$parent_path/src/main/resources/"
            fi
             if [ -d "$sub_path/src/test/resources" ]; then
                cp -R "$sub_path/src/test/resources/" "$BASE_DIR/$parent_path/src/test/resources/"
            fi

            # Clean up the submodule
            rm -rf "$sub_path"
         else
            echo "  Warning: Submodule dir $sub_path not found."
         fi
    done
}

# 1. AEP
consolidate_modules "products/aep/platform/java" "agents" "operators" "events" "workflow"

# 2. Data Cloud
consolidate_modules "products/data-cloud/platform/java" "governance" "ingestion" "storage"

# 3. Shared Services
consolidate_modules "products/shared-services/platform/java" "ai" "connectors"

# 4. Security Gateway
consolidate_modules "products/security-gateway/platform/java" "auth"

# 5. Flashit
consolidate_modules "products/flashit/platform/java" "context"

echo "Consolidation complete."
