# TutorPutor Session 11 - Action Plan

**Date:** March 22, 2026  
**Priority:** P0 - CRITICAL  
**Estimated Duration:** 8-12 hours

---

## Objective

Resolve systemic pnpm workspace and TypeScript infrastructure issues blocking TutorPutor builds, then proceed with frontend application builds.

---

## Phase 1: Workspace Infrastructure (3-4 hours)

### 1.1 Fix pnpm Workspace Configuration

**Goal:** Ensure all workspace packages properly symlinked

**Steps:**
```bash
# 1. Clean all caches and node_modules
cd /home/samujjwal/Developments/ghatana
rm -rf node_modules/.pnpm
find . -name "node_modules" -type d -prune -exec rm -rf {} +
find . -name "dist" -type d -prune -exec rm -rf {} +
find . -name ".turbo" -type d -prune -exec rm -rf {} +

# 2. Verify pnpm configuration
cat .npmrc
# Should have: shamefully-hoist=true or public-hoist-pattern[]=*

# 3. Reinstall with force
pnpm install --force --shamefully-hoist

# 4. Verify contracts symlink created
ls products/tutorputor/libs/simulation-engine/node_modules/@tutorputor/
# Should show: ai-proxy  contracts  db
```

**Validation:**
- [ ] Contracts symlink exists in simulation-engine
- [ ] All workspace packages have proper symlinks
- [ ] No "Cannot find module" errors

### 1.2 Fix TypeScript Binary Resolution

**Goal:** Ensure TypeScript binary accessible to all packages

**Option A - Global Hoisting:**
```bash
# Add to root .npmrc
echo "public-hoist-pattern[]=typescript" >> .npmrc
pnpm install --force
```

**Option B - Root TypeScript:**
```bash
# Install TypeScript at monorepo root
cd /home/samujjwal/Developments/ghatana
pnpm add -D -w typescript@5.9.3
```

**Option C - Update Build Scripts:**
```json
// In each package.json
{
  "scripts": {
    "build": "npx -y typescript@5.9.3 tsc"
  }
}
```

**Validation:**
- [ ] `pnpm build --filter=@ghatana/design-system` succeeds
- [ ] `pnpm build --filter=@ghatana/charts` succeeds
- [ ] No "Cannot find module typescript/bin/tsc" errors

---

## Phase 2: simulation-engine Refactoring (4-5 hours)

### 2.1 Verify Contracts Resolution

**Goal:** Confirm TypeScript can resolve contracts imports

**Steps:**
```bash
cd products/tutorputor/libs/simulation-engine

# Clear cache
rm -rf dist tsconfig.tsbuildinfo

# Test compilation
npx tsc --noEmit 2>&1 | grep -c "error TS"
# Target: < 100 errors (down from 526)
```

### 2.2 Systematic Error Resolution

**Strategy:** Fix errors by category, highest impact first

**Priority 1: Module Resolution (TS2305, TS2724) - 80 errors**
```typescript
// Check if these are still failing after workspace fix
import { SimulationDomain } from "@tutorputor/contracts/v1/simulation/types";
```

**Priority 2: Object Literal Types (TS2353) - 106 errors**
```typescript
// Fix: Add explicit type annotations
const config: SimulationConfig = {
  domain: "PHYSICS",
  // ...
};
```

**Priority 3: Possibly Undefined (TS18048) - 92 errors**
```typescript
// Fix: Add null checks or non-null assertions
if (manifest.canvas) {
  const width = manifest.canvas.width;
}
```

**Priority 4: Property Access (TS2339) - 64 errors**
```typescript
// Fix: Ensure types are properly imported and defined
import type { SimulationManifest } from "@tutorputor/contracts/v1/simulation/types";
```

**Priority 5: Implicit Any (TS7006) - 35 errors**
```typescript
// Fix: Add explicit type annotations
function processEntity(entity: SimEntity): void {
  // ...
}
```

### 2.3 Consider Module Splitting

**If errors persist > 200:**

Split simulation-engine into smaller packages:
```
libs/
  simulation-engine-core/     # Core types and interfaces
  simulation-engine-author/   # Authoring service
  simulation-engine-runtime/  # Runtime service
  simulation-engine-nl/       # Natural language processing
```

**Benefits:**
- Smaller TypeScript compilation units
- Better module resolution
- Easier to maintain
- Parallel builds

---

## Phase 3: Build Workspace Packages (1 hour)

### 3.1 Build Platform Packages

**Goal:** Build all required workspace dependencies

**Steps:**
```bash
cd /home/samujjwal/Developments/ghatana

# Build foundation packages
pnpm build --filter=@ghatana/theme
pnpm build --filter=@ghatana/tokens
pnpm build --filter=@ghatana/platform-utils

# Build design system
pnpm build --filter=@ghatana/design-system

# Build charts
pnpm build --filter=@ghatana/charts
```

**Validation:**
- [ ] All packages have `dist/` directories
- [ ] Type declarations (.d.ts) generated
- [ ] No build errors

---

## Phase 4: Frontend Applications (2-3 hours)

### 4.1 Install Frontend Dependencies

**Goal:** Ensure all frontend dependencies installed

**Steps:**
```bash
cd /home/samujjwal/Developments/ghatana

# Install tutorputor-web dependencies
pnpm install --filter=@tutorputor/web

# Install tutorputor-admin dependencies
pnpm install --filter=@tutorputor/admin
```

### 4.2 Build Frontend Applications

**Goal:** Successfully build frontend apps

**Steps:**
```bash
# Build tutorputor-web
pnpm build --filter=@tutorputor/web

# Build tutorputor-admin
pnpm build --filter=@tutorputor/admin
```

**Expected Issues:**
- Missing workspace package dependencies
- Type resolution errors
- Import path issues

**Resolution Strategy:**
1. Check error messages for missing packages
2. Verify workspace packages built
3. Add missing dependencies to package.json
4. Fix import paths

---

## Phase 5: Validation & Documentation (1 hour)

### 5.1 Comprehensive Build Test

**Goal:** Verify all critical modules build

**Steps:**
```bash
cd /home/samujjwal/Developments/ghatana/products/tutorputor

# Test all critical modules
pnpm build --filter=@tutorputor/contracts
pnpm build --filter=@tutorputor/learning-kernel
pnpm build --filter=@tutorputor/physics-simulation
pnpm build --filter=@tutorputor/simulation-engine
pnpm build --filter=@tutorputor/web
pnpm build --filter=@tutorputor/admin
```

### 5.2 Update Progress Report

**Goal:** Document final status and scores

**Update:**
- Build success rates
- Remaining issues
- Score recalculation
- Next priorities

---

## Success Criteria

### Must Have (P0)
- [ ] pnpm workspace properly configured
- [ ] Contracts symlinks created automatically
- [ ] TypeScript binary accessible to all packages
- [ ] simulation-engine errors < 100 (down from 526)
- [ ] @ghatana/design-system builds successfully
- [ ] @ghatana/charts builds successfully

### Should Have (P1)
- [ ] tutorputor-web builds successfully
- [ ] tutorputor-admin builds successfully
- [ ] All workspace packages build in parallel
- [ ] Build time < 5 minutes for full rebuild

### Nice to Have (P2)
- [ ] simulation-engine errors = 0
- [ ] Automated workspace validation
- [ ] CI/CD pipeline for builds

---

## Risk Mitigation

### Risk 1: pnpm Workspace Still Broken
**Mitigation:** Switch to npm workspaces or Yarn
**Effort:** 2-3 hours
**Impact:** HIGH

### Risk 2: TypeScript Errors Persist
**Mitigation:** Split simulation-engine into smaller modules
**Effort:** 4-6 hours
**Impact:** MEDIUM

### Risk 3: Frontend Dependencies Missing
**Mitigation:** Manual dependency audit and installation
**Effort:** 1-2 hours
**Impact:** LOW

---

## Rollback Plan

If workspace fixes break existing builds:

```bash
# 1. Restore from git
git checkout products/tutorputor/contracts/package.json
git checkout products/tutorputor/libs/simulation-engine/package.json

# 2. Restore manual symlink
cd products/tutorputor/libs/simulation-engine/node_modules/@tutorputor
ln -sf ../../../../contracts contracts

# 3. Continue with partial builds
pnpm build --filter=@tutorputor/learning-kernel
pnpm build --filter=@tutorputor/physics-simulation
```

---

## Monitoring & Metrics

Track these metrics during Session 11:

1. **Build Success Rate**
   - Target: 90% of packages build successfully
   - Current: ~60%

2. **TypeScript Error Count**
   - Target: < 50 errors across all packages
   - Current: 526 in simulation-engine alone

3. **Build Time**
   - Target: < 5 minutes for full rebuild
   - Current: N/A (builds failing)

4. **Workspace Integrity**
   - Target: 100% symlinks created
   - Current: ~80% (contracts missing)

---

## Next Session Preparation

Before Session 11:

1. **Review Documentation**
   - Read `SESSION_10_FINDINGS.md`
   - Review pnpm workspace docs
   - Check TypeScript module resolution docs

2. **Prepare Environment**
   - Ensure clean git state
   - Backup current node_modules structure
   - Have rollback plan ready

3. **Time Allocation**
   - Block 8-12 hours for focused work
   - Minimize context switching
   - Have debugging tools ready

---

## References

- [SESSION_10_FINDINGS.md](./SESSION_10_FINDINGS.md) - Detailed issue analysis
- [pnpm Workspaces](https://pnpm.io/workspaces)
- [TypeScript Module Resolution](https://www.typescriptlang.org/docs/handbook/module-resolution.html)
- [Monorepo Tools](https://monorepo.tools/)

---

**Plan Created:** March 22, 2026  
**Target Completion:** Session 11  
**Priority:** P0 - CRITICAL
