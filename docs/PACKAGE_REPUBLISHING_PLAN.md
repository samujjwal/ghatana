# Package Republishing Plan

**Date**: March 17, 2026  
**Purpose**: Resolve SCOPE_MISMATCH and DEPRECATED_NAMING violations by republishing packages with correct names

---

## Current Violations Summary

| Type | Count | Example |
|------|-------|---------|
| SCOPE_MISMATCH | 86 | `@ghatana/data-cloud-*` in data-cloud folder |
| DEPRECATED_NAMING | 26 | `@ghatana/yappc-*` → `@yappc/*` |

---

## Phase 1: Rename YAPPC Packages (26 packages)

### Critical Path - High Impact

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/yappc-ui` | `@yappc/ui` | `products/yappc/frontend/libs/ui` |
| `@ghatana/yappc-ai` | `@yappc/ai` | `products/yappc/frontend/libs/ai` |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | `products/yappc/frontend/libs/canvas` |
| `@ghatana/yappc-chat` | `@yappc/chat` | `products/yappc/frontend/libs/chat` |
| `@ghatana/yappc-notifications` | `@yappc/notifications` | `products/yappc/frontend/libs/notifications` |
| `@ghatana/yappc-core` | `@yappc/core` | `products/yappc/frontend/libs/core` |
| `@ghatana/yappc-crdt` | `@yappc/crdt` | `products/yappc/frontend/libs/crdt` |
| `@ghatana/yappc-ide` | `@yappc/ide` | `products/yappc/frontend/libs/ide` |
| `@ghatana/yappc-realtime` | `@yappc/realtime` | `products/yappc/frontend/libs/realtime` |
| `@ghatana/yappc-testing` | `@yappc/testing` | `products/yappc/frontend/libs/testing` |
| `@ghatana/yappc-types` | `@yappc/types` | `products/yappc/frontend/libs/types` |
| `@ghatana/yappc-utils` | `@yappc/utils` | `products/yappc/frontend/libs/utils` |
| `@ghatana/yappc-config` | `@yappc/config` | `products/yappc/frontend/libs/config` |
| `@ghatana/yappc-api-app` | `@yappc/api-app` | `products/yappc/frontend/apps/api` |
| `@ghatana/yappc-web-app` | `@yappc/web-app` | `products/yappc/frontend/apps/web` |

### Tools & Config

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/yappc-live-preview-server` | `@yappc/live-preview-server` | `products/yappc/tools/live-preview-server` |
| `@ghatana/yappc-canvas-sync` | `@yappc/canvas-sync` | `products/yappc/tools/vscode-extension` |
| `@ghatana/yappc-docs` | `@yappc/docs` | `products/yappc/frontend/docs-site` |
| `@ghatana/yappc-eslint-config-custom` | `@yappc/eslint-config-custom` | `products/yappc/frontend/packages/eslint-config-custom` |
| `@ghatana/yappc-tsconfig` | `@yappc/tsconfig` | `products/yappc/frontend/packages/tsconfig` |
| `@ghatana/yappc-vite-plugin-live-edit` | `@yappc/vite-plugin-live-edit` | `products/yappc/frontend/packages/vite-plugin-live-edit` |

---

## Phase 2: Rename Product-Scoped Packages (SCOPE_MISMATCH fixes)

### Data Cloud (5 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/data-cloud-ui` | `@data-cloud/ui` | `products/data-cloud/ui` |

### DCMAAR (8 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/dcmaar-desktop` | `@dcmaar/desktop` | `products/dcmaar/modules/desktop` |
| `@ghatana/dcmaar-connectors` | `@dcmaar/connectors` | `products/dcmaar/libs/typescript/connectors` |
| `@ghatana/dcmaar-dashboard-core` | `@dcmaar/dashboard-core` | `products/dcmaar/libs/guardian-dashboard-core` |
| `@ghatana/dcmaar-parent-mobile` | `@dcmaar/parent-mobile` | `products/dcmaar/apps/parent-mobile` |
| `@ghatana/dcmaar-child-mobile` | `@dcmaar/child-mobile` | `products/dcmaar/apps/child-mobile` |
| `@ghatana/dcmaar-parent-dashboard` | `@dcmaar/parent-dashboard` | `products/dcmaar/apps/parent-dashboard` |

### AEP (2 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/aep-ui` | `@aep/ui` | `products/aep/ui` |

### Flashit (2 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/flashit-web` | `@flashit/web` | `products/flashit/client/web` |
| `@ghatana/flashit-mobile` | `@flashit/mobile` | `products/flashit/client/mobile` |

### TutorPutor (15+ packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/tutorputor-platform` | `@tutorputor/platform` | `products/tutorputor/services/tutorputor-platform` |
| `@ghatana/tutorputor-web` | `@tutorputor/web` | `products/tutorputor/apps/tutorputor-web` |
| `@ghatana/tutorputor-db` | `@tutorputor/db` | `products/tutorputor/libs/db` |
| `@ghatana/tutorputor-contracts` | `@tutorputor/contracts` | `products/tutorputor/contracts` |
| `@ghatana/tutorputor-learning-kernel` | `@tutorputor/learning-kernel` | `products/tutorputor/libs/learning-kernel` |
| `@ghatana/tutorputor-sim-renderer` | `@tutorputor/sim-renderer` | `products/tutorputor/libs/sim-renderer` |
| `@ghatana/tutorputor-simulation-engine` | `@tutorputor/simulation-engine` | `products/tutorputor/services/tutorputor-sim-runtime` |
| `@ghatana/tutorputor-ui-shared` | `@tutorputor/ui-shared` | `products/tutorputor/libs/ui-shared` |

### Software-Org (3 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/software-org-web` | `@software-org/web` | `products/software-org/client/web` |
| `@ghatana/software-org-backend` | `@software-org/backend` | `products/software-org/services/management-api` |

### App-Platform (3 packages)

| Old Package Name | New Package Name | Location |
|-----------------|------------------|----------|
| `@ghatana/app-platform-*` | `@app-platform/*` | `products/app-platform/**` |

---

## Phase 3: Execution Plan

### Step 1: Create Package Rename Script (30 min)
- Build codemod to update all package.json name fields
- Update all dependent imports
- Update pnpm-workspace.yaml if needed

### Step 2: Rename YAPPC Packages (2 hours)
- Run rename script on YAPPC packages
- Update import statements across all files
- Verify with `pnpm install`

### Step 3: Rename Product Packages (4 hours)
- Run rename script product by product
- Update cross-product references
- Verify builds

### Step 4: Update CI/CD (30 min)
- Update CI workflow to handle new package names
- Update deployment scripts

### Step 5: Documentation (30 min)
- Update README files
- Update migration guides

---

## Total Estimated Effort

| Phase | Time |
|-------|------|
| Script Development | 30 min |
| YAPPC Renames | 2 hours |
| Product Renames | 4 hours |
| CI/CD Updates | 30 min |
| Documentation | 30 min |
| **Total** | **~8 hours** |

---

## Risk Mitigation

1. **Breaking Changes**: Use codemods for automated renames
2. **Cross-References**: Update all imports in single PR per product
3. **Build Failures**: Verify each product builds after rename
4. **CI Failures**: Update CI before merging rename PRs

---

## Success Criteria

- [ ] All 26 DEPRECATED_NAMING violations resolved
- [ ] All 86 SCOPE_MISMATCH violations resolved
- [ ] `pnpm install` succeeds without errors
- [ ] All products build successfully
- [ ] CI passes with new package names
- [ ] Architecture compliance check shows 0 violations

---

**Next Action**: Execute Phase 1 - create rename script
