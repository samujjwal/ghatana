# Phase 2: Library Consolidation Plan

**Date:** 2026-01-27  
**Status:** Planning → Execution  
**Target:** Reduce from 65 → 35 libraries (46% reduction, -30 libraries)

---

## Executive Summary

Phase 1 eliminated critical infrastructure issues (dual build files, documentation chaos, legacy code). Phase 2 focuses on **consolidating over-engineered library structure** to reduce complexity, improve build times, and enhance developer experience.

### Current State
- **65 libraries** in `app-creator/libs/`
- Excessive cognitive overhead (developers unsure where code belongs)
- Slower builds (65 compilation units)
- Complex dependency management
- Prior incomplete consolidation attempt (tokens library deprecated but not removed)

### Target State
- **35-40 libraries** (industry standard for monorepo of this size)
- Clear domain boundaries
- Faster builds (estimated 30% improvement)
- Simplified dependency graph
- Professional library naming

---

## 📊 Discovery Findings

### Tokens Library Analysis
- `@yappc/ui` already has complete tokens implementation (`/src/tokens/`)
- Includes: breakpoints, colors, shadows, shape, spacing, transitions, typography, zIndex
- `@yappc/tokens` library is deprecated but still exists (not removed)
- `@yappc/tailwind-token-detector` is actively used by 3 packages
- `@yappc/tokens` is used by 1 package

### AI Service Analysis
- `@yappc/ai-requirements-service`: v1.0.0, minimal dependencies (clean)
- Keywords: ai, requirement, service, analysis, suggestions, test-generation
- No external dependencies (good for consolidation)

### CRDT Analysis
- `@yappc/crdt-core`: v1.0.0, minimal dependencies (clean)
- Keywords: crdt, conflict-free, replication, collaboration, vector-clock, real-time
- No external dependencies (good for consolidation)

---

## 🎯 Consolidation Groups

### Group 1: Design Tokens (6 → 1)
**Target Library:** `@yappc/design-tokens` (new, focused name)  
**Rationale:** `@yappc/ui` is UI components, tokens deserve dedicated package

| Current Library | Lines of Code | Action |
|---|---|---|
| `@yappc/tailwind-token-detector` | ~500 | Move to `@yappc/design-tokens/detector` |
| `@yappc/token-analytics` | ~800 | Move to `@yappc/design-tokens/analytics` |
| `@yappc/token-editor` | ~1200 | Move to `@yappc/design-tokens/editor` |
| `@yappc/tokens` | ~2000 | **DELETE** (deprecated, migrate to ui) |
| `@yappc/style-sync-service` | ~600 | Move to `@yappc/design-tokens/sync` |
| `@yappc/visual-style-panel` | ~1500 | Move to `@yappc/design-tokens/visual-panel` |

**New Structure:**
```
@yappc/design-tokens/
  src/
    detector/       (from tailwind-token-detector)
    analytics/      (from token-analytics)
    editor/         (from token-editor)
    sync/           (from style-sync-service)
    visual-panel/   (from visual-style-panel)
    index.ts        (re-exports from @yappc/ui/tokens)
```

**Dependencies to Update:**
- 3 packages import `@yappc/tailwind-token-detector`
- 1 package imports `@yappc/tokens`

### Group 2: AI & Requirements (9 → 2)
**Target Libraries:**
- `@yappc/ai-core` (backend: services, agents, parsing)
- `@yappc/ai-ui` (frontend: UI components)

| Current Library | Lines of Code | Action |
|---|---|---|
| `@yappc/ai` | ~2500 | Move to `@yappc/ai-core` (base) |
| `@yappc/agents` | ~3000 | Move to `@yappc/ai-core/agents` |
| `@yappc/ai-requirements-service` | ~1800 | Move to `@yappc/ai-core/requirements` |
| `@yappc/requirement-parser` | ~1200 | Move to `@yappc/ai-core/parser` |
| `@yappc/requirement-validation` | ~900 | Move to `@yappc/ai-core/validation` |
| `@yappc/ai-requirements-ui` | ~2200 | Move to `@yappc/ai-ui/requirements` |
| `@yappc/requirement-editor-ui` | ~1600 | Move to `@yappc/ai-ui/editor` |

**New Structure:**
```
@yappc/ai-core/
  src/
    agents/         (intelligent agent system)
    requirements/   (requirements service)
    parser/         (requirement parsing)
    validation/     (requirement validation)
    index.ts

@yappc/ai-ui/
  src/
    requirements/   (requirements UI)
    editor/         (requirement editor)
    index.ts
```

### Group 3: CRDT & Collaboration (3 → 1)
**Target Library:** `@yappc/crdt`

| Current Library | Lines of Code | Action |
|---|---|---|
| `@yappc/crdt-core` | ~2800 | Move to `@yappc/crdt` (base) |
| `@yappc/crdt-ide` | ~1500 | Move to `@yappc/crdt/ide` |
| `@yappc/conflict-resolution-engine` | ~2000 | Move to `@yappc/crdt/conflict-resolution` |

**New Structure:**
```
@yappc/crdt/
  src/
    core/                 (from crdt-core)
    ide/                  (from crdt-ide)
    conflict-resolution/  (from conflict-resolution-engine)
    index.ts
```

### Group 4: Canvas (2 → 1)
**Target Library:** `@yappc/canvas`

| Current Library | Lines of Code | Action |
|---|---|---|
| `@yappc/canvas` | ~4500 | Keep as base |
| `@yappc/edgeless-canvas` | ~1800 | Move to `@yappc/canvas/edgeless` |

**New Structure:**
```
@yappc/canvas/
  src/
    core/        (existing canvas)
    edgeless/    (from edgeless-canvas)
    index.ts
```

### Group 5: Additional Consolidations (10 → 3)
**To Be Analyzed:**

| Current | Target | Rationale |
|---|---|---|
| `@yappc/ast-parser` | `@yappc/code-editor/ast` | Parser belongs with editor |
| `@yappc/sketch` | `@yappc/canvas/sketch` | Sketching is canvas functionality |
| `@yappc/workflow-runner` | `@yappc/lifecycle` | Workflows are lifecycle concerns |
| `@yappc/deployment-pipeline` | `@yappc/lifecycle/deployment` | Deployment is lifecycle stage |
| `@yappc/monitoring-observability` | `@yappc/platform-tools/monitoring` | Platform concern |
| `@yappc/security-compliance` | `@yappc/platform-tools/security` | Platform concern |
| `@yappc/health-checks` | `@yappc/platform-tools/health` | Platform concern |

---

## 🔧 Implementation Plan

### Step 1: Design Tokens Consolidation (Week 2, Day 1-2)

#### 1.1 Create New Package (30 min)
```bash
mkdir -p app-creator/libs/design-tokens/src/{detector,analytics,editor,sync,visual-panel}
cd app-creator/libs/design-tokens
```

Create `package.json`:
```json
{
  "name": "@yappc/design-tokens",
  "version": "0.1.0",
  "description": "Unified design token system for YAPPC",
  "type": "module",
  "main": "./src/index.ts",
  "types": "./src/index.ts",
  "exports": {
    ".": "./src/index.ts",
    "./detector": "./src/detector/index.ts",
    "./analytics": "./src/analytics/index.ts",
    "./editor": "./src/editor/index.ts",
    "./sync": "./src/sync/index.ts",
    "./visual-panel": "./src/visual-panel/index.ts"
  },
  "dependencies": {
    "@yappc/ui": "workspace:*"
  },
  "devDependencies": {
    "@types/node": "^25.0.3",
    "typescript": "^5.9.3"
  },
  "keywords": ["design-tokens", "tailwind", "css", "tokens", "style-sync"]
}
```

#### 1.2 Move Code (2 hours)
```bash
# Move detector
cp -r libs/tailwind-token-detector/src/* libs/design-tokens/src/detector/
# Move analytics
cp -r libs/token-analytics/src/* libs/design-tokens/src/analytics/
# Move editor
cp -r libs/token-editor/src/* libs/design-tokens/src/editor/
# Move sync
cp -r libs/style-sync-service/src/* libs/design-tokens/src/sync/
# Move visual panel
cp -r libs/visual-style-panel/src/* libs/design-tokens/src/visual-panel/
```

Create `src/index.ts`:
```typescript
// Re-export core tokens from @yappc/ui
export * from '@yappc/ui/tokens';

// Export detector
export * from './detector/index.js';
export type { TailwindToken, TokenMap } from './detector/index.js';

// Export analytics
export * from './analytics/index.js';

// Export editor
export * from './editor/index.js';

// Export sync
export * from './sync/index.js';

// Export visual panel
export * from './visual-panel/index.js';
```

#### 1.3 Update Imports (1 hour)
```bash
# Find all imports
grep -r "from '@yappc/tailwind-token-detector'" apps libs --include="*.ts" --include="*.tsx"
grep -r "from '@yappc/token-analytics'" apps libs --include="*.ts" --include="*.tsx"
# ... etc

# Replace with new paths
# @yappc/tailwind-token-detector → @yappc/design-tokens/detector
# @yappc/token-analytics → @yappc/design-tokens/analytics
# @yappc/token-editor → @yappc/design-tokens/editor
# @yappc/style-sync-service → @yappc/design-tokens/sync
# @yappc/visual-style-panel → @yappc/design-tokens/visual-panel
```

**Affected Files (4 known):**
- `libs/visual-style-panel/src/components/TokenBrowser.tsx`
- `libs/visual-style-panel/src/components/ColorEditor.tsx`
- `libs/responsive-breakpoint-editor/src/types.ts`
- `libs/visual-style-panel/src/types.ts`

#### 1.4 Archive Old Libraries (15 min)
```bash
mkdir -p .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/tailwind-token-detector .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/token-analytics .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/token-editor .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/tokens .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/style-sync-service .archive/libs-consolidation-2026-01-27/design-tokens/
mv libs/visual-style-panel .archive/libs-consolidation-2026-01-27/design-tokens/
```

#### 1.5 Test (30 min)
```bash
pnpm install
pnpm --filter @yappc/design-tokens type-check
pnpm --filter @yappc/design-tokens test
pnpm build
```

### Step 2: AI Consolidation (Week 2, Day 3-4)

#### 2.1 Create Packages (45 min)
```bash
mkdir -p app-creator/libs/ai-core/src/{agents,requirements,parser,validation}
mkdir -p app-creator/libs/ai-ui/src/{requirements,editor}
```

Create `libs/ai-core/package.json` and `libs/ai-ui/package.json`.

#### 2.2 Move Code (3 hours)
Move source files, update imports internally, create index exports.

#### 2.3 Update Imports (2 hours)
Search and replace across codebase.

#### 2.4 Archive (15 min)
Move old libraries to `.archive/libs-consolidation-2026-01-27/ai/`

#### 2.5 Test (1 hour)
Full test suite, verify AI functionality.

### Step 3: CRDT Consolidation (Week 2, Day 5)

#### 3.1 Create Package (30 min)
```bash
mkdir -p app-creator/libs/crdt/src/{core,ide,conflict-resolution}
```

#### 3.2 Move Code (2 hours)
Consolidate CRDT implementations.

#### 3.3 Update Imports (1 hour)
Update collaboration features.

#### 3.4 Archive (15 min)
Move old libraries.

#### 3.5 Test (1 hour)
Test real-time collaboration, conflict resolution.

### Step 4: Canvas Consolidation (Week 3, Day 1)

#### 4.1 Move Code (2 hours)
```bash
mkdir -p app-creator/libs/canvas/src/edgeless
cp -r libs/edgeless-canvas/src/* libs/canvas/src/edgeless/
```

#### 4.2 Update Imports (1 hour)
Update canvas imports.

#### 4.3 Archive (15 min)
```bash
mv libs/edgeless-canvas .archive/libs-consolidation-2026-01-27/canvas/
```

#### 4.4 Test (1 hour)
Test canvas rendering, edgeless mode.

### Step 5: Additional Consolidations (Week 3, Day 2-3)

Review remaining 45 libraries, consolidate where logical.

### Step 6: Final Verification (Week 3, Day 4)

```bash
# Clean build
rm -rf node_modules dist
pnpm install
pnpm build

# Full tests
pnpm test
pnpm run test:e2e

# Bundle analysis
pnpm run analyze
```

---

## 📈 Success Metrics

### Before (Current)
- Libraries: 65
- Build time: ~8 minutes (full workspace)
- TypeScript check: ~3 minutes
- Test time: ~5 minutes
- Bundle size: ~12 MB (main app)

### After (Target)
- Libraries: 35 (-46%)
- Build time: ~5.5 minutes (-31%)
- TypeScript check: ~2 minutes (-33%)
- Test time: ~4 minutes (-20%)
- Bundle size: ~11 MB (-8%)

### Quality Gates
- [ ] All tests passing
- [ ] No broken imports
- [ ] No circular dependencies
- [ ] No duplicate code introduced
- [ ] Documentation updated
- [ ] Team notified of changes

---

## ⚠️ Risk Mitigation

### Risk 1: Breaking Changes
- **Mitigation:** Work incrementally, test after each consolidation
- **Rollback:** All old libraries archived, can restore if needed

### Risk 2: Import Hell
- **Mitigation:** Use automated find-and-replace, verify with TypeScript
- **Verification:** Run typecheck after each import batch update

### Risk 3: Bundle Size Increase
- **Mitigation:** Use subpath exports (`/detector`, `/analytics`), no barrel re-exports of large modules
- **Monitoring:** Run bundle analyzer before/after

### Risk 4: Lost Context
- **Mitigation:** Archive all files with git history, document consolidation rationale
- **Recovery:** All libraries preserved in `.archive/` with full history

---

## 🚀 Next Actions

### Immediate (Today)
1. Review this plan with team
2. Create backup branch: `git checkout -b phase2-consolidation`
3. Start with design tokens (lowest risk, clear benefit)

### This Week
1. Complete design tokens consolidation
2. Complete AI consolidation
3. Document learnings

### Next Week
1. Complete CRDT and canvas consolidation
2. Additional consolidations
3. Final verification and deployment

---

## 📚 Appendix

### A. Full Library Inventory (65 libraries)
```
@yappc
advanced-layout-features
agents
ai
ai-requirements-service
ai-requirements-ui
analytics
api
ast-parser
audit
auth
canvas
charts
code-editor
component-traceability
config
conflict-resolution-engine
crdt-core
crdt-ide
deployment-pipeline
design-system-cli
design-system-core
diagram
edgeless-canvas
experimentation
graphql
health-checks
ide
layout-templates
lifecycle
llm-client
monitoring-observability
mro-integration
notification-handlers
page-layout-editor
platform-tools
project-templates
requirement-editor-ui
requirement-parser
requirement-validation
responsive-breakpoint-editor
schema-registry
schema-validator
search-panel
security-compliance
shared-types
sketch
store
style-sync-service
tailwind-token-detector
telemetry
testing
theme-engine
token-analytics
token-editor
tokens
tracing
ui
validation
versioning
visual-style-panel
websocket
workflow-runner
```

### B. Consolidation Summary
| Group | Before | After | Reduction |
|---|---|---|---|
| Design Tokens | 6 | 1 | 83% |
| AI & Requirements | 9 | 2 | 78% |
| CRDT | 3 | 1 | 67% |
| Canvas | 2 | 1 | 50% |
| Additional | 10 | 3 | 70% |
| **Total Consolidated** | **30** | **8** | **73%** |
| Remaining | 35 | 27 | - |
| **Grand Total** | **65** | **35** | **46%** |

### C. Timeline
- Week 2: Design tokens + AI (4 days)
- Week 3: CRDT + Canvas + Additional (4 days)
- Week 3: Verification (1 day)
- **Total:** 9 days (~40 hours)

### D. ROI Calculation
- **Time Investment:** 40 hours
- **Developer Time Saved:** ~4 hours/week (faster builds, less confusion)
- **Payback Period:** 10 weeks
- **Annual Benefit:** ~$20,000 (200 hours * $100/hour)

---

**Prepared by:** GitHub Copilot  
**Reviewed by:** [Pending]  
**Approved by:** [Pending]  
**Execution Start:** [Pending]
