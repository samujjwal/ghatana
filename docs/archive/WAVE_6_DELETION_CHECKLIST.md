# Deprecation Shutdown Checklist - Wave 6

## Overview
This checklist documents all deprecated artifacts identified for deletion during the Shared Libraries Remediation work.

## Critical: Confirm Zero Consumers Before Deletion

### TypeScript Packages

#### 1. @ghatana/ui (platform/typescript/ui/)
**Status:** DEPRECATED - Ready for deletion pending consumer migration verification
**Location:** `/Users/samujjwal/Development/ghatana/platform/typescript/ui/`
**Migration Target:** @ghatana/design-system
**Consumers Migrated:**
- ✅ Tutorputor admin/web/student apps (Wave 3)
- ✅ Software-org web (Wave 2 - ESLint rule retired)
- ✅ YAPPC already uses @ghatana/yappc-ui
**Verification Command:**
```bash
rg "@ghatana/ui" --type ts --type tsx --type json -g '!node_modules' -g '!pnpm-lock.yaml' -g '!**/dist/**' -g '!**/.turbo/**' ./products ./platform
```
**Deletion Command:**
```bash
rm -rf /Users/samujjwal/Development/ghatana/platform/typescript/ui/
```

#### 2. @ghatana/dcmaar-shared-ui-tailwind
**Status:** DEPRECATED - Marked for deletion
**Location:** `/Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-tailwind/`
**Migration Target:** @ghatana/design-system
**Documentation:** MIGRATION.md updated with correct migration path
**Verification:** Check for remaining imports in dcmaar apps
**Deletion Command:**
```bash
rm -rf /Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-tailwind/
```

#### 3. @ghatana/dcmaar-shared-ui-core
**Status:** DEPRECATED - Dependency of shared-ui-tailwind
**Location:** `/Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-core/`
**Deletion Command:**
```bash
rm -rf /Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-core/
```

#### 4. @ghatana/dcmaar-shared-ui-charts
**Status:** DEPRECATED 
**Location:** `/Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-charts/`
**Migration Target:** @ghatana/charts
**Deletion Command:**
```bash
rm -rf /Users/samujjwal/Development/ghatana/products/dcmaar/libs/typescript/shared-ui-charts/
```

### Deprecated Files to Remove

#### 5. Tutorputor Migration Script (post-execution)
**Status:** Can be archived after successful migration
**Location:** `/Users/samujjwal/Development/ghatana/products/tutorputor/scripts/migrate-from-deprecated-ui.sh`
**Note:** Keep for reference until Wave 7 complete

#### 6. Software-Org ESLint Rule (already disabled)
**Status:** Rule retired - can be deleted
**Location:** `/Users/samujjwal/Development/ghatana/products/software-org/client/web/eslint-local-rules/rules/prefer-ghatana-ui.ts`
**Deletion Command:**
```bash
rm /Users/samujjwal/Development/ghatana/products/software-org/client/web/eslint-local-rules/rules/prefer-ghatana-ui.ts
```

### Verification Gates Before Deletion

1. **Run consumer scan:**
   ```bash
   pnpm run check:deprecated-ui
   ```

2. **Verify type-checks pass:**
   ```bash
   pnpm -r --filter '@ghatana/tutorputor-*' type-check
   pnpm -r --filter '@ghatana/software-org-*' type-check
   ```

3. **Run CI verification:**
   ```bash
   ./scripts/check-deprecated-ui.sh
   ```

4. **Build verification:**
   ```bash
   pnpm -r --filter '@ghatana/design-system' --filter '@ghatana/theme' --filter '@ghatana/tokens' build
   ```

### Post-Deletion Cleanup

1. Remove from pnpm-workspace.yaml references (if any)
2. Update root package.json type-check filters
3. Clean up pnpm-lock.yaml
4. Update documentation references
5. Archive migration guides

### Rollback Plan

If deletion causes issues:
1. Package can be restored from git history
2. Re-add to workspace if needed
3. Re-run migration script

### Approval Required

- [ ] Platform Team Lead approval
- [ ] Product team sign-off (Tutorputor)
- [ ] Product team sign-off (DCMAAR)
- [ ] CI passes after deletion

## Deletion Execution Order

1. **Phase 1 - Safe Deletions:**
   - ✅ Software-Org ESLint rule (already disabled) - DELETED
   - ✅ DCMAAR shared-ui-charts (if no consumers) - DELETED

2. **Phase 2 - After Consumer Verification:**
   - ✅ @ghatana/ui (verify zero consumers) - DELETED
   - ✅ DCMAAR shared-ui-tailwind - DELETED
   - ✅ DCMAAR shared-ui-core - DELETED

3. **Phase 3 - Final Cleanup:**
   - ✅ Migration scripts (post-validation) - ARCHIVED as .completed
   - ✅ Documentation archives - COMPLETED
