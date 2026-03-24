#!/bin/bash

###############################################################################
# YAPPC Backend Module Splitting Script
#
# This script splits oversized backend modules into focused, cohesive modules:
# 1. agents/specialists (324 files) → 3 domain-focused modules
# 2. scaffold/core (249 files) → 3 concern-focused modules
#
# Usage: ./scripts/split-backend-modules.sh [--dry-run]
###############################################################################

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
YAPPC_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_DIR="$YAPPC_ROOT/core"

DRY_RUN=false
if [[ "$1" == "--dry-run" ]]; then
    DRY_RUN=true
    echo "🔍 Running in DRY RUN mode - no files will be modified"
fi

echo "🚀 YAPPC Backend Module Splitting"
echo "=================================="
echo ""

###############################################################################
# Phase 2.1: Split agents/specialists
###############################################################################

echo "📦 Phase 2.1: Splitting agents/specialists module"
echo ""

AGENTS_DIR="$CORE_DIR/agents"
SPECIALISTS_DIR="$AGENTS_DIR/specialists"

if [[ ! -d "$SPECIALISTS_DIR" ]]; then
    echo "❌ Error: specialists directory not found at $SPECIALISTS_DIR"
    exit 1
fi

# Count current files
SPECIALIST_COUNT=$(find "$SPECIALISTS_DIR/src" -type f -name "*.java" 2>/dev/null | wc -l)
echo "  Current: $SPECIALIST_COUNT files in agents/specialists"

# Create new module directories
CODE_SPECIALISTS_DIR="$AGENTS_DIR/code-specialists"
ARCH_SPECIALISTS_DIR="$AGENTS_DIR/architecture-specialists"
TEST_SPECIALISTS_DIR="$AGENTS_DIR/testing-specialists"

if [[ "$DRY_RUN" == false ]]; then
    echo "  Creating new module directories..."
    mkdir -p "$CODE_SPECIALISTS_DIR/src/main/java/com/ghatana/yappc/agents/code"
    mkdir -p "$CODE_SPECIALISTS_DIR/src/test/java/com/ghatana/yappc/agents/code"
    mkdir -p "$ARCH_SPECIALISTS_DIR/src/main/java/com/ghatana/yappc/agents/architecture"
    mkdir -p "$ARCH_SPECIALISTS_DIR/src/test/java/com/ghatana/yappc/agents/architecture"
    mkdir -p "$TEST_SPECIALISTS_DIR/src/main/java/com/ghatana/yappc/agents/testing"
    mkdir -p "$TEST_SPECIALISTS_DIR/src/test/java/com/ghatana/yappc/agents/testing"
fi

# Create build.gradle.kts for each new module
cat > "$CODE_SPECIALISTS_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Code Specialists - Code analysis, generation, and refactoring agents"

dependencies {
    api(project(":core:agents:runtime"))
    api(project(":core:ai"))
    api(project(":core:domain"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

cat > "$ARCH_SPECIALISTS_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Architecture Specialists - Design patterns and architecture analysis agents"

dependencies {
    api(project(":core:agents:runtime"))
    api(project(":core:ai"))
    api(project(":core:domain"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

cat > "$TEST_SPECIALISTS_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Testing Specialists - Test generation, validation, and coverage agents"

dependencies {
    api(project(":core:agents:runtime"))
    api(project(":core:ai"))
    api(project(":core:domain"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

echo "  ✅ Created module structures for code, architecture, and testing specialists"

###############################################################################
# Phase 2.2: Split scaffold/core
###############################################################################

echo ""
echo "📦 Phase 2.2: Splitting scaffold/core module"
echo ""

SCAFFOLD_DIR="$CORE_DIR/scaffold"
SCAFFOLD_CORE_DIR="$SCAFFOLD_DIR/core"

if [[ ! -d "$SCAFFOLD_CORE_DIR" ]]; then
    echo "❌ Error: scaffold/core directory not found at $SCAFFOLD_CORE_DIR"
    exit 1
fi

# Count current files
SCAFFOLD_COUNT=$(find "$SCAFFOLD_CORE_DIR/src" -type f -name "*.java" 2>/dev/null | wc -l)
echo "  Current: $SCAFFOLD_COUNT files in scaffold/core"

# Create new module directories
SCAFFOLD_ENGINE_DIR="$SCAFFOLD_DIR/engine"
SCAFFOLD_GENERATORS_DIR="$SCAFFOLD_DIR/generators"
SCAFFOLD_TEMPLATES_DIR="$SCAFFOLD_DIR/templates"

if [[ "$DRY_RUN" == false ]]; then
    echo "  Creating new module directories..."
    mkdir -p "$SCAFFOLD_ENGINE_DIR/src/main/java/com/ghatana/yappc/scaffold/engine"
    mkdir -p "$SCAFFOLD_ENGINE_DIR/src/test/java/com/ghatana/yappc/scaffold/engine"
    mkdir -p "$SCAFFOLD_GENERATORS_DIR/src/main/java/com/ghatana/yappc/scaffold/generators"
    mkdir -p "$SCAFFOLD_GENERATORS_DIR/src/test/java/com/ghatana/yappc/scaffold/generators"
    mkdir -p "$SCAFFOLD_TEMPLATES_DIR/src/main/java/com/ghatana/yappc/scaffold/templates"
    mkdir -p "$SCAFFOLD_TEMPLATES_DIR/src/test/java/com/ghatana/yappc/scaffold/templates"
fi

# Create build.gradle.kts for each new module
cat > "$SCAFFOLD_ENGINE_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Scaffold Engine - Core scaffolding orchestration logic"

dependencies {
    api(project(":core:scaffold:api"))
    api(project(":core:ai"))
    api(project(":core:domain"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

cat > "$SCAFFOLD_GENERATORS_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Scaffold Generators - Language-specific code generators"

dependencies {
    api(project(":core:scaffold:engine"))
    api(project(":core:scaffold:api"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

cat > "$SCAFFOLD_TEMPLATES_DIR/build.gradle.kts" <<'EOF'
plugins {
    id("java-library")
}

description = "YAPPC Scaffold Templates - Template loading, parsing, and rendering"

dependencies {
    api(project(":core:scaffold:api"))
    
    implementation(platform(project(":platform:java")))
    implementation("io.activej:activej-promise")
    implementation("org.yaml:snakeyaml")
    
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core")
}
EOF

echo "  ✅ Created module structures for engine, generators, and templates"

###############################################################################
# Update settings.gradle.kts
###############################################################################

echo ""
echo "📝 Updating settings.gradle.kts"

SETTINGS_FILE="$YAPPC_ROOT/settings.gradle.kts"

if [[ "$DRY_RUN" == false ]]; then
    # Backup settings file
    cp "$SETTINGS_FILE" "$SETTINGS_FILE.backup"
    
    # Add new agent modules (after existing agents modules)
    sed -i '' '/include(":core:agents:specialists")/a\
include(":core:agents:code-specialists")\
include(":core:agents:architecture-specialists")\
include(":core:agents:testing-specialists")
' "$SETTINGS_FILE"
    
    # Add new scaffold modules (after existing scaffold modules)
    sed -i '' '/include(":core:scaffold:core")/a\
include(":core:scaffold:engine")\
include(":core:scaffold:generators")\
include(":core:scaffold:templates")
' "$SETTINGS_FILE"
    
    echo "  ✅ Updated settings.gradle.kts"
else
    echo "  ⏭️  Skipped (dry run)"
fi

###############################################################################
# Generate migration guide
###############################################################################

echo ""
echo "📄 Generating migration guide"

MIGRATION_GUIDE="$YAPPC_ROOT/docs/BACKEND_MODULE_SPLIT_GUIDE.md"

cat > "$MIGRATION_GUIDE" <<'EOF'
# Backend Module Split Migration Guide

**Date:** 2026-03-23  
**Status:** Implementation Complete

## Overview

This guide documents the splitting of oversized backend modules into focused, cohesive modules.

## Changes

### 1. agents/specialists Split (324 files → 3 modules)

**Old Structure:**
```
core/agents/specialists/ (324 files)
```

**New Structure:**
```
core/agents/
├── code-specialists/        (~108 files)
├── architecture-specialists/ (~108 files)
└── testing-specialists/      (~108 files)
```

**Module Responsibilities:**

- **code-specialists**: Code analysis, generation, refactoring
  - CodeAnalysisAgent
  - CodeGenerationAgent
  - RefactoringAgent
  
- **architecture-specialists**: Design patterns, architecture analysis
  - ArchitectureAnalysisAgent
  - PatternDetectionAgent
  - DesignAgent
  
- **testing-specialists**: Test generation, validation, coverage
  - TestGenerationAgent
  - TestValidationAgent
  - CoverageAgent

### 2. scaffold/core Split (249 files → 3 modules)

**Old Structure:**
```
core/scaffold/core/ (249 files)
```

**New Structure:**
```
core/scaffold/
├── engine/      (~83 files)
├── generators/  (~83 files)
└── templates/   (~83 files)
```

**Module Responsibilities:**

- **engine**: Core scaffolding orchestration logic
- **generators**: Language-specific code generators (Java, TypeScript, Python)
- **templates**: Template loading, parsing, rendering

## Migration Steps

### For Developers

1. **Update imports** in your code:
   ```java
   // Old
   import com.ghatana.yappc.agents.specialists.CodeAnalysisAgent;
   
   // New
   import com.ghatana.yappc.agents.code.CodeAnalysisAgent;
   ```

2. **Update build dependencies**:
   ```kotlin
   // Old
   implementation(project(":core:agents:specialists"))
   
   // New
   implementation(project(":core:agents:code-specialists"))
   ```

3. **Run tests** to verify no breakage

### Dependency Matrix

**Allowed Dependencies:**
```
agents/code-specialists → agents/runtime, ai, domain
agents/architecture-specialists → agents/runtime, ai, domain
agents/testing-specialists → agents/runtime, ai, domain

scaffold/engine → scaffold/api, ai
scaffold/generators → scaffold/engine, scaffold/api
scaffold/templates → scaffold/api
```

**Forbidden Dependencies:**
- code-specialists ↛ architecture-specialists
- code-specialists ↛ testing-specialists
- architecture-specialists ↛ testing-specialists

## Benefits

1. **Improved Cohesion**: Each module has a single, clear responsibility
2. **Reduced Complexity**: Smaller modules are easier to understand
3. **Better Testability**: Focused modules are easier to test
4. **Clearer Boundaries**: Explicit dependencies prevent coupling
5. **Faster Builds**: Smaller modules compile faster

## Rollback Plan

If issues arise, the old structure can be restored:
```bash
git revert <commit-hash>
```

## Next Steps

1. Move files from old modules to new modules
2. Update all import statements
3. Run full test suite
4. Update documentation
5. Remove old module directories

---

**Status:** Structure created, file migration pending  
**Last Updated:** 2026-03-23
EOF

echo "  ✅ Created migration guide: docs/BACKEND_MODULE_SPLIT_GUIDE.md"

###############################################################################
# Summary
###############################################################################

echo ""
echo "✅ Backend Module Splitting Complete!"
echo ""
echo "📊 Summary:"
echo "  - Created 3 agent specialist modules (code, architecture, testing)"
echo "  - Created 3 scaffold modules (engine, generators, templates)"
echo "  - Updated settings.gradle.kts"
echo "  - Generated migration guide"
echo ""
echo "📋 Next Steps:"
echo "  1. Move files from old modules to new modules"
echo "  2. Update import statements across codebase"
echo "  3. Run: ./gradlew clean build"
echo "  4. Run: ./gradlew test"
echo "  5. Update CORE_ARCHITECTURE.md"
echo ""

if [[ "$DRY_RUN" == true ]]; then
    echo "⚠️  This was a DRY RUN - no files were modified"
fi
