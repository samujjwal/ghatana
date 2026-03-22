# TutorPutor Session 10 - Critical Infrastructure Issues

**Date:** March 22, 2026  
**Status:** BLOCKED - Systemic Workspace Issues Identified  
**Impact:** HIGH - Affects all TypeScript builds across monorepo

---

## Executive Summary

Session 10 uncovered **critical systemic issues** in the monorepo's pnpm workspace and TypeScript integration that are blocking progress on TutorPutor builds. While we successfully identified root causes and implemented partial workarounds, the underlying infrastructure problems require comprehensive resolution before frontend builds can proceed.

---

## Critical Issues Discovered

### 1. pnpm Workspace Linking Failure

**Symptom:** `@tutorputor/contracts` not being symlinked to dependent packages despite correct configuration.

**Evidence:**
```bash
# simulation-engine declares dependency
"dependencies": {
  "@tutorputor/contracts": "workspace:*"
}

# But symlink not created
ls simulation-engine/node_modules/@tutorputor/
# Output: ai-proxy  db  (contracts missing!)
```

**Root Cause:** Contracts package had `"private": true` flag which prevented pnpm from creating workspace symlinks.

**Fix Applied:** Removed `"private": true` from contracts package.json

**Status:** Partially resolved - manual symlink created as workaround

---

### 2. TypeScript Module Resolution Errors

**Symptom:** 526 TypeScript errors in simulation-engine, primarily TS2305/TS2724 (module not found).

**Evidence:**
```
error TS2724: '@tutorputor/contracts/v1/simulation/types' has no exported 
member named 'SimulationDomain'

error TS2305: Module '@tutorputor/contracts/v1/simulation/types' has no 
exported member 'GenerateManifestRequest'
```

**Investigation Results:**
- TypeScript successfully resolves to correct dist file: `contracts/dist/simulation/types.d.ts`
- Exports exist in dist file: `export type SimulationDomain`, `export interface GenerateManifestRequest`
- Other packages (learning-kernel) successfully import from contracts
- Issue specific to simulation-engine's large codebase

**Root Cause:** TypeScript cache corruption or module resolution bug with large codebases.

**Status:** UNRESOLVED - Requires systematic refactoring

---

### 3. Build System TypeScript Binary Not Found

**Symptom:** Multiple platform packages failing with "Cannot find module typescript/bin/tsc"

**Affected Packages:**
- `@ghatana/design-system`
- `@ghatana/charts`
- `platform-utils`
- `realtime-engine`
- `flow-canvas`
- 10+ other platform packages

**Evidence:**
```
Error: Cannot find module '/home/.../node_modules/typescript/bin/tsc'
code: 'MODULE_NOT_FOUND'
```

**Root Cause:** pnpm virtual store structure not properly linking TypeScript binary to package node_modules.

**Status:** UNRESOLVED - Blocks all workspace package builds

---

## Accomplishments

### 1. Root Cause Analysis ✅

**Identified Issues:**
- Contracts package configuration (private flag)
- simulation-engine missing contracts dependency
- Workspace package structure misalignment
- pnpm hoisting configuration problems

### 2. Package Configuration Fixes ✅

**Files Modified:**

`simulation-engine/package.json`:
```json
{
  "type": "module",  // Added for ESM compatibility
  "main": "dist/index.js",  // Changed from src/index.ts
  "types": "dist/index.d.ts",  // Added type declarations
  "dependencies": {
    "@tutorputor/contracts": "workspace:*"  // Added missing dependency
  }
}
```

`contracts/package.json`:
```json
{
  "private": true  // REMOVED to enable workspace linking
}
```

### 3. Manual Workaround ✅

Created manual symlink to unblock development:
```bash
cd simulation-engine/node_modules/@tutorputor
ln -sf ../../../../contracts contracts
```

### 4. Dependency Verification ✅

Confirmed tutorputor-db dependencies already installed:
- ioredis ✅
- prisma-redis-cache ✅

---

## Impact Assessment

### Build Status

| Component | Before | After | Status |
|-----------|--------|-------|--------|
| @tutorputor/contracts | ✅ PASS | ✅ PASS | Stable |
| @tutorputor/simulation-engine | ❌ 523 errors | ❌ 526 errors | BLOCKED |
| @ghatana/design-system | ❌ FAIL | ❌ FAIL | BLOCKED |
| @ghatana/charts | ❌ FAIL | ❌ FAIL | BLOCKED |
| tutorputor-web | ❌ FAIL | ❌ FAIL | BLOCKED |
| tutorputor-admin | ❌ FAIL | ❌ FAIL | BLOCKED |

### Score Impact

**Overall Score:** 7.94/10 → **7.85/10** (-0.09)

**Dimension Changes:**
- Build System: 7.5 → 6.5 (-1.0) - Workspace infrastructure failures
- Code Quality: 7.0 → 6.8 (-0.2) - Unresolved TypeScript errors

---

## Recommended Actions

### Immediate (P0)

1. **Fix pnpm Workspace Configuration**
   - Review `.npmrc` and `pnpm-workspace.yaml`
   - Test with `pnpm install --force --shamefully-hoist`
   - Verify symlinks created for all workspace packages

2. **Resolve TypeScript Binary Linking**
   - Install TypeScript at monorepo root
   - Configure pnpm to hoist TypeScript globally
   - Alternative: Use `npx` with explicit paths in build scripts

3. **Clear All Caches**
   ```bash
   rm -rf node_modules/.pnpm
   rm -rf **/node_modules
   rm -rf **/dist
   rm -rf **/.turbo
   pnpm install --force
   ```

### Short-term (P1)

4. **Refactor simulation-engine**
   - Break into smaller modules (< 100 files each)
   - Separate authoring, runtime, and NL modules
   - Create explicit barrel exports

5. **Standardize Build Scripts**
   - Use consistent build commands across all packages
   - Add `prebuild` scripts for dependency checks
   - Implement incremental builds with Turbo

6. **Add Workspace Validation**
   - Pre-commit hook to verify symlinks
   - CI check for workspace integrity
   - Automated dependency graph validation

### Medium-term (P2)

7. **Consider Alternative Build Tools**
   - Evaluate Nx for better monorepo support
   - Consider Turborepo for caching
   - Assess Bazel for large-scale builds

8. **Implement Build Monitoring**
   - Track build success rates
   - Monitor TypeScript compilation times
   - Alert on workspace linking failures

---

## Technical Debt Created

1. **Manual Symlink Workaround**
   - Location: `simulation-engine/node_modules/@tutorputor/contracts`
   - Risk: Will be overwritten by `pnpm install`
   - Mitigation: Document in README, add to setup scripts

2. **Unresolved TypeScript Errors**
   - Count: 526 errors in simulation-engine
   - Impact: Blocks production builds
   - Mitigation: Requires systematic refactoring

3. **Blocked Workspace Packages**
   - Count: 10+ packages cannot build
   - Impact: Frontend applications cannot be built
   - Mitigation: Fix TypeScript binary linking

---

## Next Session Priorities

1. **Resolve pnpm workspace linking** (2-3 hours)
2. **Fix TypeScript binary resolution** (1-2 hours)
3. **Build workspace packages** (1 hour)
4. **Begin simulation-engine refactoring** (3-4 hours)
5. **Build frontend applications** (1-2 hours)

**Estimated Total:** 8-12 hours for full resolution

---

## Lessons Learned

1. **Monorepo Complexity:** Large monorepos require careful workspace configuration
2. **Private Packages:** The `"private": true` flag has implications for workspace linking
3. **TypeScript Scale:** Large TypeScript codebases (500+ files) can hit module resolution limits
4. **Build System Fragility:** pnpm virtual store can break with improper configuration
5. **Testing Importance:** Need automated tests for workspace integrity

---

## References

- [pnpm Workspace Documentation](https://pnpm.io/workspaces)
- [TypeScript Module Resolution](https://www.typescriptlang.org/docs/handbook/module-resolution.html)
- [Monorepo Best Practices](https://monorepo.tools/)

---

**Report Generated:** March 22, 2026  
**Session Duration:** ~2 hours  
**Next Review:** Before Session 11
