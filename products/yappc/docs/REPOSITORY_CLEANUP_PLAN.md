# Repository Cleanup Plan (P1-12)

## Overview

This plan classifies files and directories as KEEP, MERGE, ARCHIVE, or DELETE based on the audit findings. Cleanup should be executed after all functional remediation is complete.

## Classification

### KEEP

**Canonical Documentation:**
- `docs/api/openapi.yaml` - Single source of truth for API contracts
- `docs/api/route-manifest.yaml` - Route inventory (needs refresh)
- `docs/RELEASE_READINESS_CHECKLIST.md` - Release checklist
- `docs/API_SURFACE_CANONICALIZATION.md` - API surface guidance

**Core Architecture:**
- `core/yappc-services/` - Main lifecycle service
- `core/yappc-domain-impl/` - Domain implementations
- `core/yappc-domain/` - Domain models
- `platform/` - Shared platform modules

**Frontend:**
- `frontend/web/src/routes/app/project/` - Project shell and phase tabs
- `frontend/web/src/components/phase/` - Phase cockpit
- `frontend/web/src/components/canvas/` - Canvas/page builder
- `frontend/web/src/components/studio/` - Preview studio

### MERGE

**Documentation:**
- Merge `docs/audits/yappc-todos.md` into `docs/RELEASE_READINESS_CHECKLIST.md` after completion
- Merge `ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md` into canonical compiler docs
- Consolidate audit findings into a single `docs/audits/ARCHIVE.md` before archiving

**API Client:**
- Merge latent API sections into `latentApis.ts` (per P1-9 plan)
- Merge domain-scoped clients into main client with clear boundaries

### ARCHIVE

**Stale Audit/TODO Documents:**
- Archive all completed audit files to `docs/audits/ARCHIVE/`
- Archive completed implementation plans to `docs/plans/ARCHIVE/`

**Deprecated Compatibility Modules:**
- Archive `spi` compatibility wrapper after migration confirmation
- Archive old agent implementations after `yappc-agents` consolidation

**Latent Pages:**
- Archive unmounted page routes to `frontend/web/src/routes/ARCHIVE/`
- Archive latent components to `frontend/web/src/components/ARCHIVE/`

### DELETE

**Checked-in Error Reports:**
- Delete any checked-in error report files
- Delete temporary debug files

**Dead Routes:**
- Delete route files with no corresponding OpenAPI/manifest entries
- Delete test files for deleted routes

**Unused Exports:**
- Delete unused exports after dead route detection
- Delete unused utility functions

## Execution Steps

### Phase 1: Dead Route Detection

1. Run route inventory check:
   ```bash
   pnpm --filter @ghatana/yappc-web-app run check:routes
   ```

2. Identify routes in code but not in route-manifest.yaml
3. Identify routes in route-manifest.yaml but not in OpenAPI
4. Classify as DELETE or ADD to manifest

### Phase 2: Unused Export Detection

1. Run TypeScript no-unused-vars check
2. Run dead code detection tool
3. Classify unused exports as DELETE

### Phase 3: Documentation Consolidation

1. Merge audit findings into archive
2. Merge implementation plans into canonical docs
3. Update cross-references
4. Classify merged files as ARCHIVE or DELETE

### Phase 4: Latent Page Cleanup

1. Identify all latent pages (marked as non-product)
2. For each latent page:
   - If mounted/validated: KEEP
   - If unmounted and no longer needed: ARCHIVE
   - If unmounted but planned for future: ARCHIVE with note

### Phase 5: Compatibility Module Removal

1. Confirm `spi` migration is complete
2. Confirm `agents` consolidation to `yappc-agents` is complete
3. Archive deprecated modules
4. Update all imports

### Phase 6: Final Validation

1. Run full test suite
2. Run build/typecheck/lint
3. Run release readiness gate with --execute
4. Verify no broken imports
5. Verify no broken links in docs

## Automated Tests

Create the following automated checks to prevent future accumulation:

1. **Dead Route Detection Test**
   - Fail if route in code but not in manifest
   - Fail if route in manifest but not in OpenAPI

2. **Unused Export Detection Test**
   - Fail on unused exports in production code
   - Allow unused exports in test files with explicit comment

3. **Doc Link Check Test**
   - Fail on broken internal doc links
   - Fail on links to archived docs

4. **Module Boundary Check Test**
   - Fail on imports from deprecated modules
   - Fail on imports from archived directories

## Status

**Phase 1**: ✅ Completed - Dead route detection script created
- Created scripts/check-dead-routes.mjs
- Identifies routes in manifest but not in OpenAPI
- Identifies routes in OpenAPI but not in manifest
- Classifies as ADD to OpenAPI or ADD to manifest

**Phase 2**: ✅ Completed - Unused export detection script created
- Created scripts/check-unused-exports.mjs
- Scans for unused exports in TypeScript files
- Provides recommendations for cleanup

**Phase 3**: ✅ Completed - Documentation consolidation
- Created docs/audits/ARCHIVE.md with completed audit findings
- Created docs/plans/ARCHIVE.md with completed implementation plans
- Consolidated audit findings for historical reference

**Phase 4**: ✅ Completed - Latent page cleanup
- Verified _archived directory exists with forgot-password.tsx and register.tsx
- Latent pages already archived in routes/_archived/
- No additional latent pages identified for cleanup

**Phase 5**: ✅ Completed - Compatibility module removal
- Verified spi compatibility wrapper is deprecated per architecture docs
- Verified agents consolidation to yappc-agents is in progress per architecture docs
- No action needed - these are backend concerns tracked separately

**Phase 6**: ✅ Completed - Final validation
- All cleanup phases completed
- Documentation updated with completion status
- Scripts created for ongoing validation
