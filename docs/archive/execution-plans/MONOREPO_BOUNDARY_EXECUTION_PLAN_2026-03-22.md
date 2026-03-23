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

## Execution Status Snapshot

Last updated: 2026-03-22

### Completed
- Phase P0 is complete.
- P1.1 is complete, but the implementation landed as `products:aep:platform-contracts` plus `products:aep:platform-central-runtime` rather than the draft `aep-contracts` naming in this document.
- P1.2 is complete (consumer narrowing; `platform:java:kernel-capabilities` has zero direct product consumers).
- P1.3 is complete.
- P1.4 is complete.
- P2.1 is complete (AEP `platform-*` modules renamed to `aep-*`).
- P2.2 is complete (FE capabilities flattened: `design-system`, `realtime`, `canvas`).
- P2.3 is complete (YAPPC `libs/ui` decomposed into `state`, `theme`, `shortcuts`, `base-ui`, `development-ui`, `initialization-ui`, `config-hooks`, `navigation-ui`; no remaining live external `@yappc/ui` imports in `web` or `libs/canvas`).
- P2.4 is complete (`products/virtual-org/OWNER.md` created; `modules:framework` formalised as a Product-Owned Shared module; approved cross-product consumer list documented; CI guardrail added).
- P2.5 is complete (`@dcmaar/shared-ui-core`, `@dcmaar/shared-ui-tailwind`, `@dcmaar/shared-ui-charts` consolidated into `@dcmaar/ui`; consumer `device-health` updated; old packages deleted; `pnpm install` green).
- P3.1 is complete (`.github/workflows/boundary-check.yml` covers deprecated-UI, architecture-compliance, cross-product Gradle, Data-Cloud isolation, shared-service OWNER.md, DCMAAR thin-UI re-introduction guard, and cross-product module OWNER.md check).
- P3.2 is complete (`docs/process/QUARTERLY_BOUNDARY_REVIEW.md` created with agenda, health-score rubric, and reference commands).
- Root validation is green after these changes: `./gradlew build --console=plain`.

### In Progress
- None. All planned phases are complete.

### Deferred
- TutorPutor broad cleanup remains deferred (P3.4 / Session 1 backlog).
- Future: promote `products:virtual-org:modules:framework` to `platform/java/org-framework` if consumer count grows beyond two products (policy in `products/virtual-org/OWNER.md`).

### Current Repo State
- `products:aep:platform-bundle` has been fully retired and deleted.
- `settings.gradle.kts` now uses the renamed AEP modules `:products:aep:aep-operator-contracts`, `:products:aep:aep-central-runtime`, `:products:aep:aep-runtime-core`, `:products:aep:aep-engine`, `:products:aep:aep-registry`, `:products:aep:aep-analytics`, `:products:aep:aep-security`, `:products:aep:aep-connectors`, `:products:aep:aep-agent`, `:products:aep:aep-api`, and `:products:aep:aep-scaling`.
- `platform:java:kernel-capabilities` now has zero direct product consumers; it is a legacy-only compatibility aggregate.
- YAPPC frontend has been decomposed into focused `libs/*` packages; `@yappc/ui` is an internal compatibility facade only.
- DCMAAR UI is consolidated under `@dcmaar/ui` (`products/dcmaar/libs/typescript/dcmaar-ui`).
- `products/virtual-org/OWNER.md` governs the cross-product shared framework policy.
- Quarterly boundary review process is documented at `docs/process/QUARTERLY_BOUNDARY_REVIEW.md`.

---

## Phase P0: Stop Active Drift (0-2 weeks)

### Goal
Stop active boundary drift by removing stale shared-services, fixing wrong package dependencies, and archiving app-platform decision.

Status: complete.

### P0.1: Remove App-Platform Code

Status: complete.

Progress update:
- App-Platform was retired to docs-only and removed from active workspace, CI, and policy wiring.
- The monorepo no longer treats App-Platform as a live product module.

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

Status: complete.

Progress update:
- `shared-services/auth-service` was removed.
- Related docker-compose and shared-services references were updated.

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

Status: complete.

Progress update:
- `shared-services/ai-registry` was removed.
- Related infrastructure references were cleaned up.

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

Status: complete.

Progress update:
- Data-Cloud no longer depends on the YAPPC editor package.
- The stale cross-product frontend edge was removed and the UI build was revalidated.

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

Status: complete.

Progress update:
- Stale YAPPC frontend references were removed from Software-Org.
- Product-local replacements were introduced where needed.

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

Status: complete.

Progress update:
- The deprecated `platform/typescript/ui` shim was removed.
- Active consumers were migrated to `@ghatana/design-system` or product-local UI packages.

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

Status: complete.

Progress update:
- `products/yappc/frontend/libs/component-traceability` was removed.
- The archive backup and associated docs updates are complete.

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

Status: complete.

Progress update:
- The original draft here is outdated in module naming, but the architectural intent is complete.
- Shared AEP contract surfaces were extracted into `products:aep:platform-contracts`.
- Central AEP catalog/runtime services were extracted into `products:aep:platform-central-runtime`.
- Implementation-heavy operator and engine code remains in `products:aep:platform-engine`.
- `products:aep:platform-bundle` was fully retired, removed from `settings.gradle.kts`, and deleted from disk.
- Consumers were rewired to the narrowest valid modules and the root build remained green.

Implementation note:
- Treat `platform-contracts` as the completed replacement for the draft `aep-contracts` module referenced below.
- Do not reintroduce `platform-bundle`; future work should continue from the split state now in the repo.

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

Status: complete for consumer narrowing.

Progress update:
- Direct consumer inventory is complete.
- The current `platform:java:kernel-capabilities` module is still a broad aggregate over `kernel`, `security`, `config`, `database`, `observability`, `audit`, and `event-cloud`.
- Current direct consumers are concentrated in Finance plus `products:phr`.
- Finance inventory found 27 direct consumers. All of them have now been rewired off the aggregate.
- The first rewiring wave completed for `products/finance/platform-sdk`, `products/finance/domains/corporate-actions`, and `products/finance/domains/pricing`, then expanded successfully across the remaining Finance modules.
- Finance now builds green with `kernel-capabilities` removed from every direct consumer, including `products/finance/extensions`.
- `products/phr` has also been rewired off `kernel-capabilities` and its module build is green.
- The narrowing wave confirmed one important classification rule for Finance: modules importing `com.ghatana.platform.audit.*` need an explicit `:platform:java:audit` edge when `kernel-capabilities` is removed.
- The current evidence does not justify introducing new wrapper modules such as `auth-primitives` or `event-primitives`; existing platform modules are sufficient.
- Repo-wide validation now shows no direct `build.gradle.kts` consumer of `:platform:java:kernel-capabilities` outside the module itself.

Execution strategy update:
- Do not start by moving Java source files blindly.
- Start by narrowing consumer dependencies in a build-safe sequence, then extract or formalize primitive modules only where the narrower API is proven.
- `platform:java:kernel-capabilities` can now be treated as a legacy-only compatibility module with no active direct consumers.

### Consumer Inventory Snapshot

- `products/phr`: complete. Direct aggregate dependency removed; current sources rely on existing `kernel`, `security`, and `database` modules.
- `products/finance/extensions`: complete. Legacy `com.ghatana.kernel.modules.*` lookups were replaced by kernel capability checks plus `AuditBusPort`.
- `products/finance/domains/corporate-actions`, `products/finance/domains/pricing`, `products/finance/domains/post-trade`, `products/finance/domains/surveillance`, `products/finance/domains/oms`, and `products/finance/domains/reconciliation`: now carry explicit `:platform:java:audit` dependencies instead of inheriting audit transitively through the aggregate.
- All direct Finance consumers now rely on existing narrow platform modules without `kernel-capabilities`.

### Next Tasks for P1.2

1. If desired, retire or collapse legacy package surfaces inside `platform:java:kernel-capabilities` itself now that no direct product consumers remain.
2. Move on to Phase P2 renames and structural cleanup, using the narrowed dependency graph as the new baseline.
3. Revisit wrapper-module creation only if a future consumer appears that cannot be expressed using the existing `kernel`, `audit`, `security`, `config`, `database`, `event-cloud`, or `observability` modules.

### Initial Target Set

- `products/phr/build.gradle.kts`
- `products/finance/build.gradle.kts`
- `products/finance/platform-sdk/build.gradle.kts`
- `products/finance/domains/*/build.gradle.kts`
- `products/finance/*/build.gradle.kts` that still depend directly on `:platform:java:kernel-capabilities`

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

Status: complete.

Progress update:
- The duplicate `products/finance/product` layer was collapsed into the root Finance aggregation.
- The redundant module include was removed and the build was revalidated.

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

Status: complete.

Progress update:
- Software-Org now uses a single canonical ErrorBoundary implementation.
- Duplicate boundary implementations were removed and imports were consolidated.

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

# AEP split is complete
ls -d products/aep/platform-contracts 2>/dev/null && echo "  ✓ AEP contracts module created"
ls -d products/aep/platform-central-runtime 2>/dev/null && echo "  ✓ AEP central runtime module created"
ls -d products/aep/platform-bundle 2>/dev/null || echo "  ✓ AEP platform-bundle removed"

# kernel-capabilities split remains pending
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

Status: complete.

Progress update:
- `platform-bundle` is already gone.
- Remaining `products/aep/platform-*` modules were renamed to `aep-*` names to make product ownership explicit.
- Applied rename map:
   - `platform-contracts` -> `aep-operator-contracts`
   - `platform-central-runtime` -> `aep-central-runtime`
   - `platform-core` -> `aep-runtime-core`
   - `platform-engine` -> `aep-engine`
   - `platform-registry` -> `aep-registry`
   - `platform-analytics` -> `aep-analytics`
   - `platform-security` -> `aep-security`
   - `platform-connectors` -> `aep-connectors`
   - `platform-agent` -> `aep-agent`
   - `platform-api` -> `aep-api`
   - `platform-scaling` -> `aep-scaling`
- Root settings, standalone YAPPC settings, cross-product Gradle references, and CI workflow wiring were all rewired to the new project paths.
- Directory renames are complete on disk under `products/aep/`.
- Targeted validation is green for the renamed AEP modules plus external consumers: `./gradlew :products:aep:aep-operator-contracts:build :products:aep:aep-central-runtime:build :products:aep:aep-runtime-core:build :products:aep:aep-engine:build :products:aep:aep-registry:build :products:aep:aep-agent:build :products:aep:aep-connectors:build :products:aep:aep-api:build :products:aep:aep-security:build :products:aep:aep-analytics:build :products:aep:aep-scaling:build :products:aep:orchestrator:build :products:aep:server:build :products:yappc:services:lifecycle:build :products:yappc:backend:api:build :products:yappc:core:agents:runtime:build :products:virtual-org:modules:framework:build :products:virtual-org:modules:integration:build :products:software-org:engine:modules:domain-model:build --console=plain`.

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

Status: complete.

Progress update:
- The capability packages already published the flat package names `@ghatana/design-system`, `@ghatana/realtime`, and `@ghatana/canvas`, but their sources still lived under `platform/typescript/capabilities/*`.
- `design-system`, `realtime-engine`, and `canvas-core` have now been flattened into `platform/typescript/design-system`, `platform/typescript/realtime`, and `platform/typescript/canvas` respectively.
- `pnpm-workspace.yaml` now points to the flattened `flow-canvas` path under `platform/typescript/canvas/flow-canvas` and no longer relies on the old `capabilities/*` workspace glob.
- Package names and consumer manifests did not need changes because they were already using the flat public package names.
- Follow-up validation confirmed the path move itself is healthy after a workspace refresh: `pnpm install` completed, the moved `design-system` tsconfig was corrected, and a lightweight `@ghatana/utils` compatibility package was restored under `platform/typescript/utils` to satisfy existing imports.
- Package-local TypeScript cleanup is complete for the flattened packages:
   - `@ghatana/design-system`: build green after strict-nullability, ref, and utility typing fixes.
   - `@ghatana/realtime`: build green after excluding test sources from the library build and tightening the internal WebSocket handler typing.
   - `@ghatana/canvas`: build green after task-panel/import typing fixes.
- Important implementation gotcha: the folder move had copied stale package-local `node_modules` symlinks from the old deeper path. The final fix was to remove `platform/typescript/{design-system,realtime,canvas}/node_modules` and rerun `pnpm install` so pnpm recreated correct links for the flattened package roots.

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

Status: in progress.

Progress update:
- The execution-plan text was stale relative to the repo: focused YAPPC frontend libraries such as `auth`, `canvas`, `realtime`, `core`, and `notifications` already exist.
- The remaining broad frontend package is `products/yappc/frontend/libs/ui`.
- Initial extraction wave started by moving `products/yappc/frontend/libs/ui/src/state` into the new `products/yappc/frontend/libs/state` package and keeping `@yappc/ui` as a compatibility re-export surface.
- `@yappc/state` now validates independently with `pnpm exec tsc -p libs/state/tsconfig.json --pretty false`.
- The remaining pure state dependency edge from `products/yappc/frontend/libs/canvas` was rewired from `@yappc/ui` to `@yappc/state`.
- Storybook-only state utilities now import from `@yappc/state/storybook`, so the compatibility facade no longer carries those state-specific helpers.
- No remaining direct imports of the known state surfaces (`StateManager`, global-state hooks, `StateProvider`, `StorybookProvider`, `storybookWorkspacesAtom`) were found coming from `@yappc/ui` outside the compatibility package itself.
- Second extraction wave created `products/yappc/frontend/libs/theme` from the theme/token utility surface previously housed under `libs/ui`.
- `@yappc/theme` now validates independently with `pnpm exec tsc -p libs/theme/tsconfig.json --pretty false`.
- Direct non-provider theme consumers in `web` and `libs/canvas` now import `palette`, `lightTheme`, `darkTheme`, `useTheme`/`useMuiTheme`, and `resolveMuiColor` from `@yappc/theme` instead of `@yappc/ui`.
- The remaining provider-oriented theme layer (`ThemeProvider`, `EnhancedThemeProvider`, `MultiLayerThemeContext`) was moved into `products/yappc/frontend/libs/theme`, and `@yappc/ui` now keeps only thin compatibility re-exports for that surface.
- The last direct external `ThemeProvider` consumer (`products/yappc/frontend/libs/state/src/storybook/StorybookProvider.tsx`) now imports from `@yappc/theme` instead of `@yappc/ui`.
- A follow-up narrowing wave rewired external canvas/web consumers that only used MUI accordion primitives or theme tokens/helpers so they now import from `@ghatana/design-system` and `@yappc/theme` directly rather than through `@yappc/ui`.
- Third extraction wave created `products/yappc/frontend/libs/shortcuts` for `CommandPalette`, `ShortcutHelper`, `useKeyboardShortcuts`, and the shortcut registry/types surface.
- `@yappc/shortcuts` now validates independently with `pnpm exec tsc -p libs/shortcuts/tsconfig.json --pretty false`.
- The live shortcuts consumer in `products/yappc/frontend/web/src/contexts/ShortcutContext.tsx` now imports from `@yappc/shortcuts`, and no remaining direct external shortcuts imports route through `@yappc/ui`.
- Fourth extraction wave created `products/yappc/frontend/libs/base-ui` for the YAPPC-specific `Popover` and `SelectTailwind` wrappers plus the shared `cn` utility they rely on.
- `@yappc/base-ui` now validates independently with `pnpm exec tsc -p libs/base-ui/tsconfig.json --pretty false`.
- The live `Popover` and `SelectTailwind` canvas consumers now import from `@yappc/base-ui`, and `@yappc/ui` keeps only compatibility re-exports for those wrappers.
- Fifth extraction wave created `products/yappc/frontend/libs/development-ui` for the self-contained `StoryCard`, `VelocityChart`, and `BurndownChart` widgets.
- `@yappc/development-ui` now validates independently with `pnpm exec tsc -p libs/development-ui/tsconfig.json --pretty false`.
- The live development-page consumers now import from `@yappc/development-ui`, and `@yappc/ui` root exports now point at the focused package for those widgets.
- Sixth extraction wave created `products/yappc/frontend/libs/initialization-ui` for `PresetCard` and `ResourcesList`.
- `@yappc/initialization-ui` now validates independently with `pnpm exec tsc -p libs/initialization-ui/tsconfig.json --pretty false`.
- The live initialization-page consumers now import from `@yappc/initialization-ui`, and both the UI root barrel and the initialization component barrel keep compatibility re-exports.
- Seventh extraction wave created `products/yappc/frontend/libs/config-hooks` for the React Query-driven config-data surface (`usePersonas`, `useDomains`, `useTemplates`, `useWorkflows`, `useTasks`, and related types/query keys).
- `@yappc/config-hooks` now validates independently with `pnpm exec tsc -p libs/config-hooks/tsconfig.json --pretty false` after making the package self-sufficient with explicit env typing and a local GraphQL helper instead of ambient workspace context.
- The live `usePersonas` consumers now import from `@yappc/config-hooks`, and `@yappc/ui` now exposes that hook surface via explicit compatibility exports instead of relying on stale declarations.
- Eighth extraction wave created `products/yappc/frontend/libs/navigation-ui` for `Breadcrumb`, `TabNavigation`, `StageNavigation`, and their local dependencies (`Tabs`, `LifecycleStage`, `cn`).
- `@yappc/navigation-ui` now validates independently with `pnpm exec tsc -p libs/navigation-ui/tsconfig.json --pretty false`.
- The live workspace/lifecycle consumers now import from `@yappc/navigation-ui`, and the old UI component barrels keep thin compatibility re-exports for that surface.
- The last example-only `DraggableCanvas` usage was repointed from `@yappc/ui` to `@yappc/canvas` via a canvas-level compatibility re-export.
- Follow-up validation debt in `products/yappc/frontend/libs/types` is now cleared: the package explicitly declares its public `zod`/`react` type dependencies, and the stray `export * from '../../state/src'` boundary leak was removed from `libs/types/src/index.ts`.
- `pnpm exec tsc -p libs/types/tsconfig.json --pretty false` now passes, and the focused frontend validation chain for `navigation-ui`, `initialization-ui`, `config-hooks`, `development-ui`, `base-ui`, `shortcuts`, `theme`, and `state` is green again.
- Result: no remaining live external `@yappc/ui` imports exist in the YAPPC `web` or `libs/canvas` trees; the remaining work under P2.3 is now internal ownership cleanup rather than consumer narrowing.

Next concrete slice:
- Decide whether to continue shrinking `products/yappc/frontend/libs/ui` for internal ownership clarity even though live web/canvas consumers no longer depend on it.
- If P2.3 continues, the next work should be package-internal decomposition or compatibility cleanup inside `libs/ui`, not more consumer-driven seam selection.

---

### P2.4: Resolve Virtual-Org Framework Ownership

Status: complete.

Progress update:
- Decision made: Virtual-Org is treated as a **Product with a Product-Owned Shared Framework layer**.
- `products/virtual-org/modules/framework` is the approved shared module because Virtual-Org is the authoritative owner of the org-graph domain model and Software-Org is a read/compose consumer.
- Moving to `platform/java/org-framework` was deliberately deferred; the policy in `products/virtual-org/OWNER.md` commits to that promotion only if the consumer count grows beyond two products.
- `products/virtual-org/OWNER.md` was created with the approved consumer list (`products/software-org`) and governance rules.
- A CI guardrail in `.github/workflows/boundary-check.yml` now enforces that the OWNER.md is present.

**Option A (chosen): Keep in products, document policy in OWNER.md**

Decision: `products/virtual-org/modules/framework` remains under Virtual-Org with `OWNER.md` governing approved cross-product consumers.

**Option B: Move extension into Software-Org** — deferred; not justified given current
two-product boundary; reinstate if Software-Org needs to extend the framework's API surface.

---

### P2.5: Consolidate DCMAAR Thin Shared-UI Wrappers

Status: complete.

Progress update:
- The three thin wrapper packages (`@dcmaar/shared-ui-core`, `@dcmaar/shared-ui-tailwind`, `@dcmaar/shared-ui-charts`) were consolidated into a single `@dcmaar/ui` package at `products/dcmaar/libs/typescript/dcmaar-ui`.
- The sole consumer (`products/dcmaar/apps/device-health`) had its `package.json` updated from three `workspace:*` deps to one `@dcmaar/ui: workspace:*` dep.
- All source imports across ~20 files in `device-health/src/` were updated with a bulk `sed` pass.
- `@dcmaar/ui` exposes three subpath exports: `@dcmaar/ui` (all), `@dcmaar/ui/core` (types + utils), `@dcmaar/ui/components` (design-system adapters), `@dcmaar/ui/charts` (charts adapter).
- The three old packages were deleted from disk. `pnpm install` is green.
- A CI step in `boundary-check.yml` guards against re-introduction of the deleted package names.

---

## Phase P3: Governance (Ongoing)

### P3.1: Add CI Guardrails

Status: complete.

Progress update:
- `.github/workflows/boundary-check.yml` exists and covers all planned boundary checks.
- Steps included: deprecated-package check, architecture-compliance script, cross-product Gradle dep check, Data-Cloud UI isolation, shared-service OWNER.md presence, DCMAAR thin-UI re-introduction guard, and cross-product shared-module OWNER.md check.
- The workflow runs on every `pull_request` and `push` to `main`/`master`/`develop`.


on:
   pull_request:
      branches: [main, master, develop]
   push:
      branches: [main, master, develop]

jobs:
  boundary-check:
      name: Boundary Check
    runs-on: ubuntu-latest
    steps:
         - name: Checkout repository
            uses: actions/checkout@v4

         - name: Setup Node.js
            uses: actions/setup-node@v4
            with:
               node-version: '20'

         - name: Setup pnpm
            uses: pnpm/action-setup@v4
            with:
               version: 9

         - name: Install dependencies
            run: pnpm install --frozen-lockfile

         - name: Check deprecated package usage
            run: ./scripts/check-deprecated-ui.sh

         - name: Check package dependency policy
            run: node scripts/check-architecture-compliance.js

         - name: Check cross-product Gradle dependencies
            run: bash scripts/check-cross-product-deps.sh
      
      - name: Check cross-product FE dependencies
        run: |
               if rg '@yappc/' products/data-cloud/ui/package.json --no-heading --line-number -n; then
                  echo 'ERROR: Data-Cloud UI depends on YAPPC packages'
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

Status: complete.

Progress update:
- `docs/process/QUARTERLY_BOUNDARY_REVIEW.md` was created with a full agenda (drift scan, admission review, deprecation enforcement, health score update, action items), a five-dimension health scoring rubric, reference commands, and a historical record table seeded with Q1 2026.
- The document is the single source of truth for the quarterly review process going forward.

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
- [x] AEP modules renamed (no more platform-*)
- [x] FE folder structure flattened
- [x] YAPPC libs split by concern
- [x] Virtual-Org ownership clarified (`products/virtual-org/OWNER.md`)
- [x] DCMAAR thin wrappers consolidated (`@dcmaar/ui`)

**P3 Ongoing:**
- [x] CI boundary checks passing (`.github/workflows/boundary-check.yml`)
- [x] Quarterly review process document created (`docs/process/QUARTERLY_BOUNDARY_REVIEW.md`)
- [ ] Deprecation dates enforced (review quarterly per P3.2)

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
