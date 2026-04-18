# FOW Stage Field Migration: fowStage → flowStage

## Overview

This migration renames the `fowStage` field to `flowStage` across the YAPPC codebase to fix a typo and improve consistency.

## Status

**Current State**: Partially Complete
- Prisma schema updated ✓
- TypeScript code updated ✓
- Prisma client regenerated ✓
- Database migration pending ⏳
- Full test suite pending ⏳

## Changes Made

### 1. Prisma Schema Changes

**File**: `products/yappc/frontend/apps/api/prisma/schema.prisma`

Renamed `fowStage` to `flowStage` in:
- `LifecycleArtifact` model (line 1456)
- `LifecycleItem` model (line 1490)
- `LifecycleAIInsight` model (line 1521)

Updated index:
- `LifecycleArtifact` index on `flowStage` (line 1469)

### 2. TypeScript Code Changes

Updated all TypeScript files to use `flowStage` instead of `fowStage`:
- `apps/api/src/routes/lifecycle.ts`
- `web/src/services/lifecycle/gates.ts`
- `web/src/services/lifecycle/audit.ts`
- `web/src/services/lifecycle/api.ts`
- `web/src/routes/app/project/lifecycle.tsx`
- `web/src/routes/app/project/canvas/CanvasRoute.tsx`
- `web/src/routes/app/project/__tests__/lifecycle.test.tsx`
- `web/src/hooks/useLifecycleData.ts`
- `web/src/components/CollaborativeCanvas.tsx`
- `web/src/components/canvas/CanvasWorkspace.tsx`
- `web/src/components/canvas/hooks/useCanvasHandlers.ts`
- `web/src/components/canvas/SimplifiedCanvasWorkspace.tsx`
- `web/src/components/canvas/UnifiedCanvasDemo.tsx`
- `web/src/components/lifecycle/ContextDrawer.tsx`
- `web/src/types/fow-stages.ts`
- `web/mock-api-server.js`

### 3. Prisma Client Regeneration

Ran `npx prisma generate` successfully for the API package.

## Remaining Work

### 1. Database Migration

The Prisma migration command failed because the database has existing schema changes that require a baseline migration. A manual SQL migration script needs to be created and applied.

**Manual SQL Migration Script**:

```sql
-- Rename fowStage to flowStage in LifecycleArtifact
ALTER TABLE "LifecycleArtifact" RENAME COLUMN "fowStage" TO "flowStage";

-- Rename fowStage to flowStage in LifecycleItem
ALTER TABLE "LifecycleItem" RENAME COLUMN "fowStage" TO "flowStage";

-- Rename fowStage to flowStage in LifecycleAIInsight
ALTER TABLE "LifecycleAIInsight" RENAME COLUMN "fowStage" TO "flowStage";

-- The index on LifecycleArtifact.flowStage should be automatically updated by the column rename
-- If needed, recreate the index:
-- DROP INDEX IF EXISTS "LifecycleArtifact_fowStage_idx";
-- CREATE INDEX "LifecycleArtifact_flowStage_idx" ON "LifecycleArtifact"("flowStage");
```

**To apply the migration**:
1. Connect to the YAPPC PostgreSQL database
2. Run the SQL script above
3. Verify the column names have been updated
4. Regenerate Prisma client: `npx prisma generate`

### 2. Java Code Check

Search for any Java code that might reference `fowStage`:
```bash
grep -r "fowStage" products/yappc --include="*.java"
```

If found, update those references to `flowStage`.

### 3. Full Test Suite

Run the full test suite to verify no broken references:
```bash
cd products/yappc/frontend/apps/api
npm test

cd ../web
npm test
```

### 4. Documentation Update

Update any documentation that references `fowStage` to `flowStage`.

## Acceptance Criteria Status

- [x] Rename `fowStage` to `flowStage` in Prisma schema
- [ ] Create migration to rename database column (manual SQL script provided)
- [x] Regenerate Prisma client types
- [x] Update all TypeScript code to use `flowStage`
- [ ] Update all Java code (if any) to use `flowStage` (pending verification)
- [ ] Run full test suite to verify no broken references
- [ ] Document the migration in changelog

## Notes

- This is a P3 (low priority) task
- The migration requires manual SQL execution due to existing database state
- TypeScript compilation errors are expected until the database migration is applied and Prisma client is regenerated
- The web frontend doesn't have its own Prisma schema - it shares types from the API package

## Next Steps

1. Apply the manual SQL migration to the database
2. Regenerate Prisma client after migration
3. Verify TypeScript compilation succeeds
4. Check for Java code references
5. Run full test suite
6. Update changelog
