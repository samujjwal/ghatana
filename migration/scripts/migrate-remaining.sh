#!/bin/bash
set -e

# Migration Script for Remaining Modules
# This script executes the moves defined in REMAINING_MIGRATION_PLAN.md

SOURCE_ROOT="../ghatana/libs/java"
TARGET_ROOT="."

echo "Starting migration of remaining modules..."

# Helper function to move source to target
# Usage: move_module "source_module_name" "target_relative_path"
move_module() {
    local source_mod=$1
    local target_path=$2
    
    echo "Migrating $source_mod -> $target_path"
    
    # Create target directories
    mkdir -p "$TARGET_ROOT/$target_path/src/main/java"
    mkdir -p "$TARGET_ROOT/$target_path/src/test/java"
    mkdir -p "$TARGET_ROOT/$target_path/src/main/resources"
    mkdir -p "$TARGET_ROOT/$target_path/src/test/resources"

    # Copy Main Java
    if [ -d "$SOURCE_ROOT/$source_mod/src/main/java" ]; then
        cp -R "$SOURCE_ROOT/$source_mod/src/main/java/" "$TARGET_ROOT/$target_path/src/main/java/"
    fi
    # Copy Test Java
    if [ -d "$SOURCE_ROOT/$source_mod/src/test/java" ]; then
        cp -R "$SOURCE_ROOT/$source_mod/src/test/java/" "$TARGET_ROOT/$target_path/src/test/java/"
    fi
    # Copy Resources if exist
    if [ -d "$SOURCE_ROOT/$source_mod/src/main/resources" ]; then
        cp -R "$SOURCE_ROOT/$source_mod/src/main/resources/" "$TARGET_ROOT/$target_path/src/main/resources/"
    fi
     if [ -d "$SOURCE_ROOT/$source_mod/src/test/resources" ]; then
        cp -R "$SOURCE_ROOT/$source_mod/src/test/resources/" "$TARGET_ROOT/$target_path/src/test/resources/"
    fi
}

# Helper for merging subdirectories (like ai-platform/promotion)
move_submodule() {
    local source_mod_path=$1
    local target_path=$2
    
    echo "Migrating submodule $source_mod_path -> $target_path"
    
    mkdir -p "$TARGET_ROOT/$target_path/src/main/java"
    mkdir -p "$TARGET_ROOT/$target_path/src/test/java"

     if [ -d "$SOURCE_ROOT/$source_mod_path/src/main/java" ]; then
        cp -R "$SOURCE_ROOT/$source_mod_path/src/main/java/" "$TARGET_ROOT/$target_path/src/main/java/"
    fi
    if [ -d "$SOURCE_ROOT/$source_mod_path/src/test/java" ]; then
        cp -R "$SOURCE_ROOT/$source_mod_path/src/test/java/" "$TARGET_ROOT/$target_path/src/test/java/"
    fi
}

# --- 1. Platform Modules ---

# Domain
move_module "domain-models" "platform/java/domain"

# Runtime
move_module "activej-runtime" "platform/java/runtime"

# HTTP (Consolidation)
move_module "http-client" "platform/java/http"
if [ -d "$SOURCE_ROOT/activej-websocket" ]; then
    move_module "activej-websocket" "platform/java/http"
fi

# Observability (Consolidation)
move_module "audit" "platform/java/observability"
move_module "observability-http" "platform/java/observability"
move_module "observability-clickhouse" "platform/java/observability"

# Plugin
move_module "plugin-framework" "platform/java/plugin"

# Config
move_module "config-runtime" "platform/java/config"

# Testing (Consolidation)
move_module "architecture-tests" "platform/java/testing"
if [ -d "$SOURCE_ROOT/platform-architecture-tests" ]; then
    move_module "platform-architecture-tests" "platform/java/testing"
fi

# --- 2. Product: AEP ---

AEP_PATH="products/aep/platform/java"

# Agents
move_module "agent-api" "$AEP_PATH/agents"
move_module "agent-core" "$AEP_PATH/agents"
move_module "agent-framework" "$AEP_PATH/agents"
move_module "agent-runtime" "$AEP_PATH/agents"

# Operators
move_module "operator" "$AEP_PATH/operators"
move_module "operator-catalog" "$AEP_PATH/operators"

# Events
move_module "event-cloud" "$AEP_PATH/events"
move_module "event-cloud-contract" "$AEP_PATH/events"
move_module "event-cloud-factory" "$AEP_PATH/events"
move_module "event-runtime" "$AEP_PATH/events"
move_module "event-spi" "$AEP_PATH/events"

# Workflow
move_module "workflow-api" "$AEP_PATH/workflow"

# --- 3. Product: Data Cloud ---

DC_PATH="products/data-cloud/platform/java"

move_module "governance" "$DC_PATH/governance"
move_module "ingestion" "$DC_PATH/ingestion"
move_module "state" "$DC_PATH/storage"

# --- 4. Product: Shared Services ---

SHARED_PATH="products/shared-services/platform/java"

# AI - Merge ai-integration and all ai-platform submodules
move_module "ai-integration" "$SHARED_PATH/ai"

# AI Platform submodules
# Listing submodules based on common structure
for mod in promotion serving learning training observability feature-store testing batch registry evaluation gateway; do
    if [ -d "$SOURCE_ROOT/ai-platform/$mod" ]; then
        move_submodule "ai-platform/$mod" "$SHARED_PATH/ai"
    fi
done

move_module "connectors" "$SHARED_PATH/connectors"


# --- 5. Product: Security Gateway ---

SEC_PATH="products/security-gateway/platform/java"

# Auth - Merge auth-platform submodules and security lib
# Handle auth-platform/core and auth-platform/oauth/core specially if needed, or just cp -R if they are standard structure
# The find command showed: ghatana/libs/java/auth-platform/oauth/core/src/main/java...
# and ghatana/libs/java/auth-platform/core/src/main/java...

move_submodule "auth-platform/core" "$SEC_PATH/auth"
move_submodule "auth-platform/oauth/core" "$SEC_PATH/auth"

move_module "security" "$SEC_PATH/auth"

# --- 6. Product: Flashit ---

move_module "context-policy" "products/flashit/platform/java/context"

echo "Migration of source files complete."
echo "Please verify the contents."
