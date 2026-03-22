# Monorepo Boundary Right-Sizing: Detailed Execution Plan

> Generated from: `docs/audits/MONOREPO_BOUNDARY_RIGHT_SIZING_AUDIT_2026-03-22.md`
> Date: 2026-03-22

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [Phase P0: Stop Active Drift (0-2 weeks)](#phase-p0-stop-active-drift-0-2-weeks)
3. [Phase P1: Clean Major Boundaries (2-6 weeks)](#phase-p1-clean-major-boundaries-2-6-weeks)
4. [Phase P2: Reduce Package Sprawl (1-3 months)](#phase-p2-reduce-package-sprawl-1-3-months)
5. [Phase P3: Governance (Ongoing)](#phase-p3-governance-ongoing)
6. [Appendix: Reference Commands](#appendix-reference-commands)

---

## Executive Summary

This execution plan transforms the audit findings into actionable, file-by-file steps. Each phase contains:
- **Prerequisites**: What must be completed before starting
- **Steps**: Detailed file operations with exact paths
- **Verification**: How to confirm success
- **Rollback**: How to recover if issues arise

**Overall Goal**: Simplify the monorepo graph until the code layout says exactly one thing:
- Platform is for primitives
- Products own product logic
- Product-owned shared stays with its product
- Shared-services are rare, runtime-real, and explicitly owned

---

## Phase P0: Stop Active Drift (0-2 weeks)

### Goal
Stop active boundary drift by removing stale shared-services, fixing wrong package dependencies, and archiving app-platform decision.

### P0.1: Remove App-Platform Code

**Prerequisites**: 
- Confirm Finance migration is complete (code now lives in `products/finance/`)
- Confirm no active CI dependencies on `products/app-platform`
- Verify no other products reference app-platform modules

**Step-by-Step:**

1. **Verify no consumers remain**
   ```bash
   grep -r "app-platform" products/ platform/ shared-services/ \
     --include="build.gradle.kts" --include="settings.gradle.kts" \
     --include="package.json" 2>/dev/null | grep -v "DEPRECATED\|README" || echo "No consumers found"
   ```

2. **Delete live code directories**
   ```bash
   rm -rf products/app-platform/admin-portal
   rm -rf products/app-platform/infra
   rm -rf products/app-platform/docker-compose.yml
   rm -rf products/app-platform/Dockerfile
   rm -rf products/app-platform/.env.example
   rm -rf products/app-platform/.gitea/
   ```

3. **Update root settings.gradle.kts**
   ```kotlin
   // File: settings.gradle.kts
   // Find and remove:
   // include("products:app-platform:admin-portal")
   // include("products:app-platform:infra")
   ```

4. **Clean up references in other build files**
   ```bash
   sed -i '/app-platform/d' build.gradle.kts 2>/dev/null || true
   sed -i '/app-platform/d' settings.gradle.kts 2>/dev/null || true
   ```

5. **Move documentation to archive (retain for reference only)**
   ```bash
   mkdir -p docs/archive/app-platform-2026-03
   mv products/app-platform/docs/* docs/archive/app-platform-2026-03/ 2>/dev/null || true
   mv products/app-platform/finance-ghatana-integration-plan.md docs/archive/app-platform-2026-03/ 2>/dev/null || true
   mv products/app-platform/DEPRECATED.md docs/archive/app-platform-2026-03/ 2>/dev/null || true
   ```

6. **Remove empty product directory**
   ```bash
   rmdir products/app-platform 2>/dev/null || \
     (echo "Remaining files:" && ls -la products/app-platform/)
   ```

**Verification:**
```bash
# Confirm app-platform only has docs folder remaining
ls -la products/app-platform/
# Expected: docs/, DEPRECATED.md, OWNER.md, README.md (if any)

# Verify build doesn't reference app-platform
grep -r "app-platform" settings.gradle.kts || echo "No references found - GOOD"
```

---

### P0.2: Remove shared-services/auth-service

**Prerequisites**: 
- Confirm all auth traffic routes through `shared-services/auth-gateway`
- Database migration complete (if any auth-service specific data)

**Step-by-Step:**

1. **Backup auth-service code**
   ```bash
   tar -czf docs/archive/auth-service-backup-2026-03.tar.gz shared-services/auth-service/
   ```

2. **Remove auth-service from build**
   ```kotlin
   // File: settings.gradle.kts
   // Remove line:
   // include("shared-services:auth-service")
   ```

3. **Remove auth-service directory**
   ```bash
   rm -rf shared-services/auth-service
   ```

4. **Update shared-services build.gradle.kts**
   ```kotlin
   // File: shared-services/build.gradle.kts
   // Remove auth-service from subproject dependencies if any
   ```

5. **Update docker-compose if referenced**
   ```bash
   # File: shared-services/docker-compose.yml
   # Remove auth-service service definition
   sed -i '/auth-service/,/^  [a-z]/d' shared-services/docker-compose.yml
   ```

6. **Update any README references**
   ```bash
   find shared-services -name "README.md" -exec \
     sed -i 's/auth-service/auth-gateway/g' {} \;
   ```

**Verification:**
```bash
# Check no references remain
grep -r "auth-service" shared-services/ --include="*.md" --include="*.yml" --include="*.gradle*" || echo "Clean"

# Verify build works
./gradlew :shared-services:auth-gateway:build --dry-run
```

---

### P0.3: Remove shared-services/ai-registry

**Prerequisites**: 
- ADR-013 decision confirmed
- Model registry consolidated into appropriate service

**Step-by-Step:**

1. **Backup ai-registry**
   ```bash
   tar -czf docs/archive/ai-registry-backup-2026-03.tar.gz shared-services/ai-registry/
   ```

2. **Remove from settings.gradle.kts**
   ```kotlin
   // settings.gradle.kts
   // Remove: include("shared-services:ai-registry")
   ```

3. **Remove directory**
   ```bash
   rm -rf shared-services/ai-registry
   ```

4. **Update docker-compose.yml**
   ```bash
   sed -i '/ai-registry/,/^  [a-z]/d' shared-services/docker-compose.yml
   ```

**Verification:**
```bash
./gradlew projects | grep -i "ai-registry" || echo "ai-registry removed successfully"
```

---

### P0.4: Fix Data-Cloud UI Dependency on @yappc/code-editor

**Prerequisites**: 
- Data-Cloud UI code located
- Alternative editor solution identified or local adapter created

**Step-by-Step:**

1. **Identify the dependency**
   ```bash
   # File: products/data-cloud/ui/package.json
   cat products/data-cloud/ui/package.json | grep -A2 -B2 "@yappc/code-editor"
   ```

2. **Create local Data-Cloud editor adapter**
   ```bash
   mkdir -p products/data-cloud/ui/src/components/editor
   ```

3. **Create minimal local editor component**
   ```typescript
   // File: products/data-cloud/ui/src/components/editor/DataCloudEditor.tsx
   // Local editor implementation - copy only the interface surface needed from YAPPC
   // This breaks the cross-product dependency
   
   export interface DataCloudEditorProps {
     value: string;
     onChange: (value: string) => void;
     language?: string;
   }
   
   export const DataCloudEditor: React.FC<DataCloudEditorProps> = (props) => {
     // Local implementation or wrapper around Monaco/CodeMirror
     // Do NOT import from @yappc/code-editor
   };
   ```

4. **Update imports in Data-Cloud UI**
   ```bash
   find products/data-cloud/ui/src -type f -name "*.tsx" -o -name "*.ts" | \
     xargs grep -l "@yappc/code-editor" | \
     xargs sed -i 's|from "@yappc/code-editor"|from "../components/editor/DataCloudEditor"|g'
   ```

5. **Remove dependency from package.json**
   ```json
   // File: products/data-cloud/ui/package.json
   {
     "dependencies": {
       // Remove: "@yappc/code-editor": "workspace:*"
     }
   }
   ```

6. **Run pnpm install to update lockfile**
   ```bash
   pnpm install
   ```

**Verification:**
```bash
# Verify no imports remain
grep -r "@yappc/code-editor" products/data-cloud/ui/ || echo "Clean"

# Build Data-Cloud UI
cd products/data-cloud/ui && pnpm build
```

---

### P0.5: Fix Software-Org Frontend Stale Dependencies

**Prerequisites**: 
- Software-Org frontend code located at `products/software-org/client/web/`

**Step-by-Step:**

1. **Identify stale dependencies**
   ```bash
   cat products/software-org/client/web/package.json | grep -E "@ghatana/yappc-state|@yappc/core/types"
   ```

2. **Check if packages exist in monorepo**
   ```bash
   find . -name "package.json" -path "*/yappc-state/*" 2>/dev/null
   find . -name "package.json" -path "*/yappc/core/*" 2>/dev/null
   ```

3. **Create local state management**
   ```bash
   mkdir -p products/software-org/client/web/src/state
   ```

4. **Create minimal state interfaces**
   ```typescript
   // File: products/software-org/client/web/src/state/SoftwareOrgState.ts
   // Local state definitions replacing missing @ghatana/yappc-state
   
   export interface SoftwareOrgState {
     // Define only what Software-Org needs
   }
   ```

5. **Update package.json - remove stale deps**
   ```json
   // File: products/software-org/client/web/package.json
   {
     "dependencies": {
       // Remove these lines:
       // "@ghatana/yappc-state": "workspace:*",
       // "@yappc/core/types": "workspace:*"
     }
   }
   ```

6. **Update all imports**
   ```bash
   find products/software-org/client/web/src -type f \( -name "*.ts" -o -name "*.tsx" \) | \
     xargs grep -l "@ghatana/yappc-state\|@yappc/core" | \
     while read file; do
       # Replace with local imports
       sed -i 's|from "@ghatana/yappc-state"|from "../../state/SoftwareOrgState"|g' "$file"
       sed -i 's|from "@yappc/core/types"|from "../../types"|g' "$file"
     done
   ```

**Verification:**
```bash
# Check for any remaining stale imports
grep -r "@ghatana/yappc-state\|@yappc/core" products/software-org/client/web/src || echo "Clean"

# Build to verify
pnpm --filter @software-org/web build
```

---

### P0.6: Delete platform/typescript/ui (Deprecated Shim)

**Prerequisites**: 
- All consumers migrated to `@ghatana/design-system`
- No remaining imports of `@ghatana/ui`

**Step-by-Step:**

1. **Verify no consumers remain**
   ```bash
   grep -r "@ghatana/ui" products/ platform/ shared-services/ --include="package.json" --include="*.ts" --include="*.tsx" || echo "No consumers - safe to delete"
   ```

2. **Backup the package**
   ```bash
   tar -czf docs/archive/ghatana-ui-shim-backup.tar.gz platform/typescript/ui/
   ```

3. **Remove from pnpm-workspace.yaml**
   ```yaml
   # File: pnpm-workspace.yaml
   packages:
     # Remove: - 'platform/typescript/ui'
   ```

4. **Remove directory**
   ```bash
   rm -rf platform/typescript/ui
   ```

5. **Update any references in build files**
   ```bash
   sed -i '/platform\/typescript\/ui/d' platform/typescript/build.gradle 2>/dev/null || true
   ```

**Verification:**
```bash
# Verify package is not in workspace
pnpm list | grep -i "@ghatana/ui" || echo "Package removed"

# Verify build still works
pnpm install
```

---

### P0.7: Delete @yappc/component-traceability (Shim)

**Prerequisites**: 
- Migration to replacement complete

**Step-by-Step:**

1. **Check for consumers**
   ```bash
   grep -r "@yappc/component-traceability" products/ --include="package.json" --include="*.ts" --include="*.tsx" || echo "No consumers"
   ```

2. **Backup**
   ```bash
   tar -czf docs/archive/component-traceability-backup.tar.gz products/yappc/frontend/libs/component-traceability/
   ```

3. **Remove package**
   ```bash
   rm -rf products/yappc/frontend/libs/component-traceability
   ```

4. **Update YAPPC libs index exports if exists**
   ```bash
   # File: products/yappc/frontend/libs/index.ts or similar
   # Remove exports for component-traceability
   ```

**Verification:**
```bash
pnpm --filter "*" list 2>/dev/null | grep component-traceability || echo "Removed"
```

---

### P0 Phase Verification

```bash
# Run these verification commands
echo "=== P0 Verification ==="
echo "1. App-platform archived:"
ls -la products/app-platform/ 2>/dev/null || echo "  ✓ Removed"

echo "2. Auth-service removed:"
ls -d shared-services/auth-service 2>/dev/null || echo "  ✓ Removed"

echo "3. AI-registry removed:"
ls -d shared-services/ai-registry 2>/dev/null || echo "  ✓ Removed"

echo "4. Data-Cloud no longer depends on YAPPC editor:"
grep "@yappc/code-editor" products/data-cloud/ui/package.json || echo "  ✓ Clean"

echo "5. Software-Org has no stale YAPPC deps:"
grep "@ghatana/yappc-state\|@yappc/core" products/software-org/client/web/package.json || echo "  ✓ Clean"

echo "6. @ghatana/ui deleted:"
ls -d platform/typescript/ui 2>/dev/null || echo "  ✓ Removed"

echo "7. @yappc/component-traceability deleted:"
ls -d products/yappc/frontend/libs/component-traceability 2>/dev/null || echo "  ✓ Removed"
```

---

## Phase P1: Clean Major Boundaries (2-6 weeks)

### Goal
Split major bundles, collapse duplicate layers, and establish clean contracts.

### P1.1: Split AEP platform-bundle

**Prerequisites**: 
- All consumers identified (YAPPC, Virtual-Org, Software-Org)
- Narrow contract interface designed

**Step-by-Step:**

1. **Analyze current bundle contents**
   ```bash
   ls -la products/aep/platform-bundle/src/main/java/com/ghatana/aep/
   find products/aep/platform-bundle -name "*.java" | head -20
   ```

2. **Create new contract module structure**
   ```bash
   mkdir -p products/aep/aep-contracts/src/main/java/com/ghatana/aep/contracts
   mkdir -p products/aep/aep-contracts/src/main/java/com/ghatana/aep/operator
   mkdir -p products/aep/aep-contracts/src/main/java/com/ghatana/aep/pipeline
   ```

3. **Create build file for contracts**
   ```kotlin
   // File: products/aep/aep-contracts/build.gradle.kts
   plugins {
       id("com.ghatana.java-conventions")
   }
   
   group = "com.ghatana.aep"
   
   dependencies {
       // Only platform primitives - no AEP runtime
       implementation(platform("platform:java:core"))
       implementation(platform("platform:java:http"))
   }
   ```

4. **Extract operator contracts**
   ```java
   // File: products/aep/aep-contracts/src/main/java/com/ghatana/aep/operator/OperatorId.java
   // Move from platform-bundle: the ID/identifier class only
   
   package com.ghatana.aep.contracts;
   
   import java.util.UUID;
   
   public final class OperatorId {
       private final UUID value;
       // ... contract surface only
   }
   ```

5. **Extract pipeline contracts**
   ```java
   // File: products/aep/aep-contracts/src/main/java/com/ghatana/aep/pipeline/PipelineDefinition.java
   // Interface/POJO only - no implementation
   
   package com.ghatana.aep.contracts;
   
   public interface PipelineDefinition {
       String getName();
       List<OperatorDefinition> getOperators();
       // ... contract only
   }
   ```

6. **Update platform-bundle to depend on contracts**
   ```kotlin
   // File: products/aep/platform-bundle/build.gradle.kts
   dependencies {
       api(project(":products:aep:aep-contracts"))
       // Keep runtime implementations
   }
   ```

7. **Migrate consumer dependencies gradually**
   
   **Virtual-Org**:
   ```kotlin
   // File: products/virtual-org/build.gradle.kts
   // Change FROM:
   // implementation(project(":products:aep:platform-bundle"))
   // Change TO:
   implementation(project(":products:aep:aep-contracts"))
   ```
   
   **Software-Org**:
   ```kotlin
   // File: products/software-org/build.gradle.kts
   // Change FROM:
   // implementation(project(":products:aep:platform-bundle"))
   // Change TO:
   implementation(project(":products:aep:aep-contracts"))
   ```
   
   **YAPPC**:
   ```kotlin
   // File: products/yappc/backend/build.gradle.kts
   // Narrow dependency:
   implementation(project(":products:aep:aep-contracts"))
   // Only add runtime if truly needed:
   // runtimeOnly(project(":products:aep:runtime"))
   ```

8. **Update settings.gradle.kts**
   ```kotlin
   // Add: include("products:aep:aep-contracts")
   // Keep: include("products:aep:platform-bundle") // Will shrink over time
   ```

9. **Build and verify**
   ```bash
   ./gradlew :products:aep:aep-contracts:build
   ./gradlew :products:aep:platform-bundle:build
   ```

**Verification:**
```bash
# Check contract module compiles independently
./gradlew :products:aep:aep-contracts:dependencies --configuration runtimeClasspath | grep -E "aep|products" || echo "No AEP product deps in contracts - GOOD"
```

---

### P1.2: Split platform/java/kernel-capabilities

**Prerequisites**: 
- Capability domains identified (auth, config, db, event, audit, resilience)
- Consumer dependencies analyzed

**Step-by-Step:**

1. **Analyze current structure**
   ```bash
   find platform/java/kernel-capabilities/src/main/java -type d
   ls platform/java/kernel-capabilities/src/main/java/com/ghatana/kernel/
   ```

2. **Create new capability modules**
   ```bashn   mkdir -p platform/java/auth-primitives/src/main/java/com/ghatana/auth
   mkdir -p platform/java/config-primitives/src/main/java/com/ghatana/config
   mkdir -p platform/java/database-primitives/src/main/java/com/ghatana/db
   mkdir -p platform/java/event-primitives/src/main/java/com/ghatana/event
   mkdir -p platform/java/audit-primitives/src/main/java/com/ghatana/audit
   mkdir -p platform/java/resilience-primitives/src/main/java/com/ghatana/resilience
   ```

3. **Create build files for each**
   ```kotlin
   // Template: platform/java/auth-primitives/build.gradle.kts
   plugins {
       id("com.ghatana.java-conventions")
   }
   
   dependencies {
       implementation(project(":platform:java:core"))
   }
   ```

4. **Move auth capabilities**
   ```bash
   # Move auth-related files
   find platform/java/kernel-capabilities -name "*Auth*.java" -o -name "*auth*" -type f | \
     while read f; do
       mv "$f" platform/java/auth-primitives/src/main/java/com/ghatana/auth/
     done
   ```

5. **Move config capabilities**
   ```bash
   find platform/java/kernel-capabilities -name "*Config*.java" -o -name "*Configuration*.java" | \
     while read f; do
       mv "$f" platform/java/config-primitives/src/main/java/com/ghatana/config/
     done
   ```

6. **Repeat for db, event, audit, resilience**

7. **Update kernel-capabilities to aggregate**
   ```kotlin
   // File: platform/java/kernel-capabilities/build.gradle.kts
   // Change to aggregation:
   dependencies {
       api(project(":platform:java:auth-primitives"))
       api(project(":platform:java:config-primitives"))
       api(project(":platform:java:database-primitives"))
       api(project(":platform:java:event-primitives"))
       api(project(":platform:java:audit-primitives"))
       api(project(":platform:java:resilience-primitives"))
   }
   ```

8. **Update settings.gradle.kts**
   ```kotlin
   include("platform:java:auth-primitives")
   include("platform:java:config-primitives")
   include("platform:java:database-primitives")
   include("platform:java:event-primitives")
   include("platform:java:audit-primitives")
   include("platform:java:resilience-primitives")
   ```

9. **Migrate consumers to specific primitives**
   ```kotlin
   // Instead of: implementation(project(":platform:java:kernel-capabilities"))
   // Use specific:
   implementation(project(":platform:java:auth-primitives"))
   implementation(project(":platform:java:event-primitives"))
   ```

**Verification:**
```bash
# Build all new modules
./gradlew :platform:java:*-primitives:build

# Verify kernel-capabilities still works as aggregate
./gradlew :platform:java:kernel-capabilities:build
```

---

### P1.3: Collapse Finance Duplicate Aggregation

**Prerequisites**: 
- Understand difference between `products/finance/` and `products/finance/product/`

**Step-by-Step:**

1. **Analyze structure**
   ```bash
   ls -la products/finance/
   ls -la products/finance/product/ 2>/dev/null || echo "No product/ directory"
   ```

2. **If both directories exist with overlapping content:**
   ```bashn   # Identify which has more recent/active code
   git log --oneline --since="2026-01-01" products/finance/product/ | wc -l
   git log --oneline --since="2026-01-01" products/finance/domains/ | wc -l
   ```

3. **Merge contents into single structure**
   ```bash
   # Move product/ contents up or into domains/
   mv products/finance/product/src/* products/finance/domains/*/src/ 2>/dev/null || true
   mv products/finance/product/build.gradle.kts products/finance/build.gradle.kts.new
   ```

4. **Consolidate build file**
   ```kotlin
   // File: products/finance/build.gradle.kts
   plugins {
       id("com.ghatana.java-conventions")
   }
   
   // Aggregate all finance domains
   dependencies {
       api(project(":products:finance:domains:oms"))
       api(project(":products:finance:domains:accounting"))
       api(project(":products:finance:domains:reporting"))
       // etc.
   }
   ```

5. **Remove duplicate**
   ```bash
   rm -rf products/finance/product/
   ```

6. **Update settings.gradle.kts**
   ```kotlin
   // Remove: include("products:finance:product")
   ```

**Verification:**
```bash
./gradlew :products:finance:build
./gradlew :products:finance:domains:*:build
```

---

### P1.4: Consolidate Software-Org Error Boundaries

**Step-by-Step:**

1. **Find error boundary files**
   ```bash
   find products/software-org -name "*ErrorBoundary*" -type f
   ```

2. **Select canonical location**
   ```bash
   mkdir -p products/software-org/client/web/src/components/error
   ```

3. **Merge implementations into single component**
   ```typescript
   // File: products/software-org/client/web/src/components/error/ErrorBoundary.tsx
   // Consolidated version taking best of both implementations
   ```

4. **Update all imports to use canonical**
   ```bash
   find products/software-org/client -name "*.tsx" | \
     xargs sed -i 's|from "./shared/ErrorBoundary"|from "../components/error/ErrorBoundary"|g'
   ```

5. **Remove duplicate files**
   ```bash
   rm -f products/software-org/client/web/src/components/shared/ErrorBoundary.tsx
   ```

---

### P1 Phase Verification

```bash
echo "=== P1 Verification ==="

# AEP contracts module exists
ls -d products/aep/aep-contracts 2>/dev/null && echo "  ✓ AEP contracts module created"

# kernel-capabilities split into primitives
ls -d platform/java/auth-primitives 2>/dev/null && echo "  ✓ Auth primitives created"
ls -d platform/java/config-primitives 2>/dev/null && echo "  ✓ Config primitives created"

# Finance consolidated
ls -d products/finance/product 2>/dev/null || echo "  ✓ Finance duplicate layer removed"
```

---

## Phase P2: Reduce Package Sprawl (1-3 months)

### Goal
Consolidate YAPPC FE, rename misleading paths, resolve Virtual-Org/Software-Org ownership.

### P2.1: Rename AEP platform-* Modules

**Step-by-Step:**

1. **Identify modules to rename**
   ```bash
   find products/aep -name "platform-*" -type d
   ```

2. **Rename platform-core to aep-runtime-core**
   ```bash
   git mv products/aep/platform-core products/aep/aep-runtime-core
   ```

3. **Update settings.gradle.kts**
   ```kotlin
   // Change: include("products:aep:platform-core")
   // To:     include("products:aep:aep-runtime-core")
   ```

4. **Update all build.gradle.kts references**
   ```bash
   find products/ -name "build.gradle.kts" | \
     xargs grep -l "products:aep:platform-core" | \
     xargs sed -i 's|products:aep:platform-core|products:aep:aep-runtime-core|g'
   ```

5. **Rename other platform-* modules similarly**

**Verification:**
```bash
grep -r "platform-" products/aep/*/build.gradle.kts || echo "No platform-* references - GOOD"
```

---

### P2.2: Align FE Shared Folder Names

**Step-by-Step:**

1. **Move capabilities packages to flat structure**
   ```bashn   # Option A: Move to match package names
   git mv platform/typescript/capabilities/design-system platform/typescript/design-system
   git mv platform/typescript/capabilities/realtime-engine platform/typescript/realtime
   git mv platform/typescript/capabilities/canvas-core platform/typescript/canvas
   ```

2. **Remove empty capability shells**
   ```bash
   rmdir platform/typescript/capabilities/design-system 2>/dev/null || true
   rmdir platform/typescript/capabilities/realtime 2>/dev/null || true
   rmdir platform/typescript/capabilities/canvas 2>/dev/null || true
   ```

3. **Update pnpm-workspace.yaml**
   ```yaml
   packages:
     - 'platform/typescript/design-system'
     - 'platform/typescript/realtime'
     - 'platform/typescript/canvas'
     # Remove old paths
   ```

4. **Update all package.json references**
   ```bash
   find . -name "package.json" -path "*/platform/*" | \
     xargs grep -l "@ghatana/capabilities" | \
     while read f; do
       sed -i 's|@ghatana/capabilities/design-system|@ghatana/design-system|g' "$f"
     done
   ```

---

### P2.3: Split YAPPC Frontend Libraries

**Step-by-Step:**

1. **Analyze libs/ui structure**
   ```bash
   ls -la products/yappc/frontend/libs/ui/src/
   find products/yappc/frontend/libs/ui/src -type d
   ```

2. **Create new focused packages**
   ```bash
   mkdir -p products/yappc/frontend/libs/yappc-ui-core
   mkdir -p products/yappc/frontend/libs/yappc-state
   mkdir -p products/yappc/frontend/libs/yappc-auth
   mkdir -p products/yappc/frontend/libs/yappc-canvas-core
   ```

3. **Move UI primitives to yappc-ui-core**
   ```bash
   # Create package structure
   mkdir -p products/yappc/frontend/libs/yappc-ui-core/src/components
   mkdir -p products/yappc/frontend/libs/yappc-ui-core/src/hooks
   
   # Move components (example)
   mv products/yappc/frontend/libs/ui/src/components/Button* products/yappc/frontend/libs/yappc-ui-core/src/components/
   mv products/yappc/frontend/libs/ui/src/components/Input* products/yappc/frontend/libs/yappc-ui-core/src/components/
   ```

4. **Create package.json for each new lib**
   ```json
   // File: products/yappc/frontend/libs/yappc-ui-core/package.json
   {
     "name": "@yappc/ui-core",
     "version": "1.0.0",
     "main": "./src/index.ts",
     "dependencies": {
       "@ghatana/design-system": "workspace:*"
     }
   }
   ```

5. **Gradually migrate and remove old libs/ui**

---

### P2.4: Resolve Virtual-Org Framework Ownership

**Decision Required**: Is Virtual-Org a product or shared framework?

**Option A: Make it Product-Owned Shared**
```bash
# Rename to indicate shared framework status
git mv products/virtual-org platform/java/org-framework

# Or keep in products but clarify:
# products/virtual-org/framework/
```

**Option B: Move extension into Software-Org**
```bash
# Move the framework extension into Software-Org
mkdir -p products/software-org/framework-extension
git mv products/virtual-org/modules/framework/* products/software-org/framework-extension/
```

**Update build dependencies accordingly**

---

### P2.5: Consolidate DCMAAR Thin Shared-UI Wrappers

**Step-by-Step:**

1. **Analyze thin packages**
   ```bash
   ls -la products/dcmaar/libs/typescript/
   ```

2. **Merge where no API value**
   ```bash
   # If shared-ui-core, shared-ui-tailwind, shared-ui-charts are thin wrappers
   mkdir -p products/dcmaar/libs/typescript/dcmaar-ui
   
   # Move contents
   mv products/dcmaar/libs/typescript/shared-ui-core/src/* products/dcmaar/libs/typescript/dcmaar-ui/src/
   mv products/dcmaar/libs/typescript/shared-ui-tailwind/src/* products/dcmaar/libs/typescript/dcmaar-ui/src/tailwind/
   mv products/dcmaar/libs/typescript/shared-ui-charts/src/* products/dcmaar/libs/typescript/dcmaar-ui/src/charts/
   ```

3. **Remove empty shells**
   ```bash
   rm -rf products/dcmaar/libs/typescript/shared-ui-core
   rm -rf products/dcmaar/libs/typescript/shared-ui-tailwind
   rm -rf products/dcmaar/libs/typescript/shared-ui-charts
   ```

---

## Phase P3: Governance (Ongoing)

### P3.1: Add CI Guardrails

**File: .github/workflows/boundary-check.yml**
```yaml
name: Monorepo Boundary Check

on: [pull_request]

jobs:
  boundary-check:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Check for deprecated package usage
        run: |
          # Fail if deprecated packages are still imported
          if grep -r "@ghatana/ui" products/ platform/ 2>/dev/null; then
            echo "ERROR: Deprecated @ghatana/ui still referenced"
            exit 1
          fi
          
          if grep -r "@yappc/component-traceability" products/ 2>/dev/null; then
            echo "ERROR: Deprecated @yappc/component-traceability still referenced"
            exit 1
          fi
      
      - name: Check cross-product FE dependencies
        run: |
          # Prevent new product-to-product FE imports
          # Data-Cloud should NOT depend on YAPPC
          if grep -r "@yappc/" products/data-cloud/ui/package.json 2>/dev/null; then
            echo "ERROR: Data-Cloud UI depends on YAPPC packages"
            exit 1
          fi
      
      - name: Verify shared-service ownership
        run: |
          # Every shared-service must have OWNER.md
          for dir in shared-services/*/; do
            if [ ! -f "$dir/OWNER.md" ]; then
              echo "ERROR: $dir missing OWNER.md"
              exit 1
            fi
          done
```

---

### P3.2: Establish Quarterly Review Process

**Document: docs/process/QUARTERLY_BOUNDARY_REVIEW.md**
```markdown
# Quarterly Boundary Review Process

## Schedule
- Every quarter, week 1
- Attendees: Platform leads, Product architects, DevEx

## Checklist
- [ ] Shared-services admission criteria review
- [ ] New cross-product dependencies audit
- [ ] Deprecated package sunset verification
- [ ] OWNER.md currency check
- [ ] Boundary score trend analysis

## Tools
```bash
# Generate boundary health report
./scripts/audit-boundaries.sh --report
```

---

## Appendix: Reference Commands

### Quick Reference: File Operations

```bash
# Search for imports across all TypeScript files
grep -r "from \"@yappc" products/ --include="*.ts" --include="*.tsx"

# Find all Java dependencies on a module
find . -name "build.gradle.kts" | xargs grep "products:aep:platform-bundle"

# List all packages in a workspace directory
ls -la platform/typescript/*/package.json 2>/dev/null | grep package

# Find orphaned files (no references)
find products/yappc/frontend/libs -name "*.ts" | while read f; do
  name=$(basename "$f" .ts)
  if ! grep -r "$name" products/yappc/frontend --include="*.ts" --include="*.tsx" > /dev/null; then
    echo "Possibly orphaned: $f"
  fi
done
```

### Quick Reference: Build Commands

```bash
# Build specific module
./gradlew :products:aep:aep-contracts:build

# Build all of a product
./gradlew :products:aep:build

# Build platform only
./gradlew :platform:java:build

# FE build commands
pnpm --filter @ghatana/design-system build
pnpm --filter @yappc/web build

# Check all FE dependencies
pnpm list --depth=10
```

### Quick Reference: Verification Checklist

**After P0:**
- [ ] `products/app-platform` only contains docs
- [ ] `shared-services/auth-service` deleted
- [ ] `shared-services/ai-registry` deleted
- [ ] Data-Cloud UI doesn't import `@yappc/code-editor`
- [ ] Software-Org doesn't import stale YAPPC packages
- [ ] `platform/typescript/ui` deleted
- [ ] `@yappc/component-traceability` deleted

**After P1:**
- [ ] `products:aep:aep-contracts` module exists and builds
- [ ] Consumers use contracts instead of platform-bundle where possible
- [ ] `platform:java:*-primitives` modules created
- [ ] Finance has single aggregation layer
- [ ] Software-Org has single ErrorBoundary

**After P2:**
- [ ] AEP modules renamed (no more platform-*)
- [ ] FE folder structure flattened
- [ ] YAPPC libs split by concern
- [ ] Virtual-Org ownership clarified
- [ ] DCMAAR thin wrappers consolidated

**P3 Ongoing:**
- [ ] CI boundary checks passing
- [ ] Quarterly reviews scheduled
- [ ] Deprecation dates enforced

---

## Rollback Procedures

### If P0 Changes Cause Issues

```bash
# Restore from backup
cd docs/archive
tar -xzf app-platform-backup-2026-03.tar.gz -C ../../

# Or use git
git revert --no-commit HEAD~5..HEAD  # Adjust range as needed
```

### If P1 Split Causes Dependency Issues

```bash
# Temporary: restore aggregate dependency
# In consumer build.gradle.kts:
# implementation(project(":platform:java:kernel-capabilities"))
# Instead of individual primitives

./gradlew :platform:java:kernel-capabilities:publishToMavenLocal
```

---

*Document generated: 2026-03-22*
*Based on audit: docs/audits/MONOREPO_BOUNDARY_RIGHT_SIZING_AUDIT_2026-03-22.md*
