# TutorPutor Package & Module Consolidation Plan

## Executive Summary

This document provides a detailed analysis of the current TutorPutor package/module structure and a comprehensive plan to consolidate from **12 libraries → 5 libraries** and **7 apps → 3 apps**, eliminating redundancy and simplifying the architecture.

---

## Current State Analysis

### Libraries (12 total)

| # | Library | Purpose | Size | Status |
|---|---------|---------|------|--------|
| 1 | `animator` | Animation engine with timeline, React Router v7 | 71 lines pkg | Active |
| 2 | `assessments` | Assessment helpers, simulation-based items | 21 lines pkg | Minimal |
| 3 | `charts` | Recharts-based charting components | 33 lines pkg | Simple |
| 4 | `content-studio-agents` | AI agents for content (Gradle/Kotlin) | N/A | Gradle-based |
| 5 | `learning-kernel` | Core orchestration kernel | 25 lines pkg | Active |
| 6 | `physics-simulation` | Cross-product physics with Konva | 80 lines pkg | Large |
| 7 | `sim-renderer` | PixiJS/Three.js rendering | 51 lines pkg | Large |
| 8 | `simulation-engine` | Runtime/authoring/NL engine | 29 lines pkg | Active |
| 9 | `testing` | Testing utilities | 1 file | Minimal |
| 10 | `tracing` | Tracing utilities | 1 file | Minimal |
| 11 | `tutorputor-ai-proxy` | AI proxy service | N/A | Service overlap |
| 12 | `tutorputor-db` | Prisma data access | 54 lines pkg | Critical |
| 13 | `tutorputor-sim-sdk` | Simulation SDK | 6 items | Small |
| 14 | `tutorputor-ui-shared` | Shared UI utilities | 36 lines pkg | Active |

### Apps (7 total)

| # | App | Purpose | Size | Status |
|---|-----|---------|------|--------|
| 1 | `api-gateway` | Fastify API gateway | 36 lines pkg | Critical |
| 2 | `content-explorer` | Content discovery | 0 items | **EMPTY** |
| 3 | `tutorputor-admin` | Admin dashboard | 92 items | Active |
| 4 | `tutorputor-explorer` | Content exploration | 32 items | Active |
| 5 | `tutorputor-mobile` | React Native mobile | 45 lines pkg | Active |
| 6 | `tutorputor-student` | Student interface | 2 items | Minimal |
| 7 | `tutorputor-web` | Main web frontend | 223 items | **Primary** |

### Services (14 total - Java/Kotlin)

| # | Service | Purpose |
|---|---------|---------|
| 1 | `tutorputor-ai-agents` | AI agent implementations |
| 2 | `tutorputor-ai-proxy` | AI proxy (duplicates lib) |
| 3 | `tutorputor-assessment` | Assessment engine |
| 4 | `tutorputor-content` | Content management |
| 5 | `tutorputor-content-generation` | AI content generation |
| 6 | `tutorputor-content-studio` | Content studio |
| 7 | `tutorputor-content-studio-grpc` | gRPC content studio |
| 8 | `tutorputor-db` | Database service (duplicates lib) |
| 9 | `tutorputor-domain-loader` | Domain data loader |
| 10 | `tutorputor-kernel-registry` | Kernel service registry |
| 11 | `tutorputor-lti` | LTI integration |
| 12 | `tutorputor-payments` | Payment processing |
| 13 | `tutorputor-platform` | Core platform (188 items) |
| 14 | `tutorputor-simulation` | Simulation service |
| 15 | `tutorputor-vr` | VR integration |

---

## Consolidation Strategy

### Phase 1: Library Consolidation (12 → 5)

#### Proposed New Library Structure

```
libs/
├── tutorputor-core/          # MERGED: Core functionality
│   ├── data/                 # From: tutorputor-db
│   ├── kernel/               # From: learning-kernel
│   ├── config/               # From: learning-kernel orchestration
│   └── contracts/            # Re-export from contracts/
│
├── tutorputor-simulation/    # MERGED: All simulation-related
│   ├── engine/               # From: simulation-engine
│   ├── renderer/             # From: sim-renderer
│   ├── physics/              # From: physics-simulation
│   ├── animator/             # From: animator
│   └── sdk/                  # From: tutorputor-sim-sdk
│
├── tutorputor-ui/            # MERGED: UI components & utilities
│   ├── components/           # From: tutorputor-ui-shared
│   ├── charts/               # From: charts
│   ├── assessment-ui/        # From: assessments
│   └── testing/              # From: testing, tracing
│
├── tutorputor-ai/            # MERGED: AI functionality
│   ├── proxy/                # From: tutorputor-ai-proxy
│   ├── content-agents/       # From: content-studio-agents
│   └── generation/           # New: consolidate content-generation logic
│
└── contracts/                # KEEP: API contracts (existing)
```

#### Detailed Merge Plan

##### 1. Create `tutorputor-core` (Merge: db + learning-kernel + contracts integration)

**Rationale:**
- `tutorputor-db` (Prisma) and `learning-kernel` (orchestration) are always used together
- Both are foundational - no app works without them
- Simplifies dependency graph from 2 packages → 1

**Implementation:**
```json
{
  "name": "@tutorputor/core",
  "exports": {
    ".": "./dist/index.js",
    "./db": "./dist/db/index.js",
    "./kernel": "./dist/kernel/index.js",
    "./contracts": "./dist/contracts/index.js"
  }
}
```

**Migration Path:**
- Move `tutorputor-db/src/` → `tutorputor-core/src/db/`
- Move `learning-kernel/src/` → `tutorputor-core/src/kernel/`
- Update imports: `@tutorputor/db` → `@tutorputor/core/db`
- Update imports: `@tutorputor/learning-kernel` → `@tutorputor/core/kernel`

**Estimated Effort:** 2-3 days
**Risk Level:** Low (mechanical refactoring)

---

##### 2. Create `tutorputor-simulation` (Merge: simulation-engine + sim-renderer + physics-simulation + animator + sim-sdk)

**Rationale:**
- All 5 libraries deal with simulation/rendering/animation
- Heavy overlap in dependencies (PixiJS, Three.js, Konva, React)
- Physics-simulation imports from sim-renderer
- Animator is used exclusively by simulation UIs
- Reduces build complexity and version conflicts

**Implementation:**
```json
{
  "name": "@tutorputor/simulation",
  "exports": {
    ".": "./dist/index.js",
    "./engine": "./dist/engine/index.js",
    "./renderer": "./dist/renderer/index.js",
    "./physics": "./dist/physics/index.js",
    "./animator": "./dist/animator/index.js",
    "./sdk": "./dist/sdk/index.js"
  }
}
```

**Migration Path:**
- Merge all source directories under `tutorputor-simulation/src/`
- Create unified build configuration
- Consolidate PixiJS/Three.js dependencies
- Merge Storybook configs

**Estimated Effort:** 4-5 days
**Risk Level:** Medium (complex dependency merging)

---

##### 3. Create `tutorputor-ui` (Merge: ui-shared + charts + assessments + testing + tracing)

**Rationale:**
- All UI-related utilities and components
- `charts` is simple and only used by UI apps
- `assessments` is small helpers for UI
- `testing` and `tracing` are utilities used by UI components
- Creates single UI package for all frontend needs

**Implementation:**
```json
{
  "name": "@tutorputor/ui",
  "exports": {
    ".": "./dist/index.js",
    "./components": "./dist/components/index.js",
    "./charts": "./dist/charts/index.js",
    "./assessment": "./dist/assessment/index.js",
    "./testing": "./dist/testing/index.js",
    "./utils": "./dist/utils/index.js"
  }
}
```

**Migration Path:**
- Move UI components from all packages
- Merge Tailwind/design system configurations
- Consolidate test utilities

**Estimated Effort:** 2 days
**Risk Level:** Low (mostly UI components)

---

##### 4. Create `tutorputor-ai` (Merge: ai-proxy + content-studio-agents + AI utilities)

**Rationale:**
- `tutorputor-ai-proxy` (lib) duplicates `tutorputor-ai-proxy` (service)
- `content-studio-agents` is AI-specific
- Creates single AI package for TypeScript/JVM boundary

**Implementation:**
```json
{
  "name": "@tutorputor/ai",
  "exports": {
    ".": "./dist/index.js",
    "./proxy": "./dist/proxy/index.js",
    "./agents": "./dist/agents/index.js"
  }
}
```

**Migration Path:**
- Consolidate AI proxy client code
- Keep service implementations in Java/Kotlin
- Create clear TypeScript/Java boundary

**Estimated Effort:** 2-3 days
**Risk Level:** Medium (requires service coordination)

---

### Phase 2: App Consolidation (7 → 3)

#### Proposed New App Structure

```
apps/
├── tutorputor-web/           # MERGED: Main web experience
│   ├── student/              # From: tutorputor-student
│   ├── explorer/             # From: tutorputor-explorer
│   ├── admin/                # From: tutorputor-admin (reduced)
│   └── shared/               # Common web components
│
├── tutorputor-mobile/        # KEEP: React Native (separate platform)
│
└── tutorputor-gateway/       # MERGED: API gateway + services facade
    ├── gateway/              # From: api-gateway
    └── services/             # Service routing layer
```

#### Detailed Merge Plan

##### 1. Merge Web Apps (tutorputor-web + tutorputor-explorer + tutorputor-admin + tutorputor-student)

**Current Problem:**
- `tutorputor-web` (223 items) - Primary web app
- `tutorputor-explorer` (32 items) - Content exploration
- `tutorputor-admin` (92 items) - Admin dashboard
- `tutorputor-student` (2 items) - Minimal, likely incomplete
- `content-explorer` (0 items) - Empty

**Rationale:**
- All are web-based React applications
- Share 90%+ of dependencies (React Router, React Query, design system)
- Admin features can be route-guarded within single app
- Reduces build/maintenance overhead

**Implementation:**
```
apps/tutorputor-web/
├── src/
│   ├── student/              # Student learning interface
│   │   ├── pages/
│   │   ├── components/
│   │   └── routes.tsx
│   ├── explorer/             # Content exploration (merge tutorputor-explorer)
│   │   ├── pages/
│   │   ├── components/
│   │   └── routes.tsx
│   ├── admin/                # Admin features (merge tutorputor-admin)
│   │   ├── pages/            # Reduced: only essential admin
│   │   ├── components/
│   │   └── routes.tsx
│   └── shared/               # Common components
│       ├── layouts/
│       ├── components/
│       └── hooks/
```

**Route Structure:**
```tsx
// Single app with route-based access control
<Route path="/" element={<StudentLayout />}>
  <Route index element={<StudentDashboard />} />
  <Route path="learn/:id" element={<LearningInterface />} />
</Route>

<Route path="/explore" element={<ExplorerLayout />}>
  <Route index element={<ContentExplorer />} />
</Route>

<Route path="/admin" element={<AdminGuard />}>
  <Route index element={<AdminDashboard />} />
  {/* Reduced admin features */}
</Route>
```

**Admin Scope Reduction:**
Move advanced admin features to separate "Studio" app or service layer:
- Content authoring → `tutorputor-content-generation` service
- Complex simulations → `physics-simulation` lib
- User management → API gateway admin endpoints
- Analytics → Standalone service

**Estimated Effort:** 5-7 days
**Risk Level:** Medium (requires auth/routing rework)

---

##### 2. Keep `tutorputor-mobile` Separate

**Rationale:**
- React Native has fundamentally different build system
- Mobile-specific dependencies (AsyncStorage, Navigation, MMKV)
- Different deployment lifecycle (App Store vs Web)
- Worth keeping as separate package

**Optimization:**
- Share only `contracts` and `core` types via workspace dependencies
- Keep mobile-specific code isolated

**Estimated Effort:** 0 days (keep as-is)
**Risk Level:** None

---

##### 3. Consolidate API Layer

**Current:**
- `api-gateway` - Fastify gateway
- Multiple services with duplicated proxy logic

**Rationale:**
- Gateway is already the entry point
- Service consolidation should happen at Java/Kotlin level (separate effort)
- Keep TypeScript gateway focused

**Optimization:**
- Keep `api-gateway` as-is
- Ensure all services route through it
- Add service discovery layer

**Estimated Effort:** 1 day (configuration)
**Risk Level:** Low

---

### Phase 3: Service Consolidation (14 → 6)

**Note:** Services are Java/Kotlin-based. This is a separate but related effort.

```
services/
├── tutorputor-platform/      # KEEP: Core platform (already 188 items)
│   └── absorbs: kernel-registry, domain-loader
│
├── tutorputor-content/       # MERGED: All content-related
│   ├── content-service/        # From: tutorputor-content
│   ├── generation/           # From: tutorputor-content-generation
│   ├── studio/               # From: tutorputor-content-studio
│   └── grpc/                 # From: tutorputor-content-studio-grpc
│
├── tutorputor-ai/            # MERGED: All AI-related
│   ├── agents/               # From: tutorputor-ai-agents
│   ├── proxy/                # From: tutorputor-ai-proxy (service)
│   └── inference/            # New: consolidate AI logic
│
├── tutorputor-assessment/    # KEEP: Assessment engine (specialized)
│
├── tutorputor-simulation/    # MERGED: Simulation services
│   ├── runtime/              # From: tutorputor-simulation
│   └── sdk-service/          # From: tutorputor-sim-sdk
│
└── tutorputor-integrations/ # MERGED: External integrations
    ├── lti/                  # From: tutorputor-lti
    ├── payments/             # From: tutorputor-payments
    └── vr/                   # From: tutorputor-vr
```

---

## Implementation Roadmap

### Week 1: Preparation & Foundation

**Day 1-2: Audit & Preparation**
- [ ] Complete dependency graph analysis
- [ ] Identify circular dependencies
- [ ] Create migration scripts
- [ ] Set up feature flags for gradual rollout

**Day 3-5: Core Library Creation**
- [ ] Create `tutorputor-core` package structure
- [ ] Move `tutorputor-db` code
- [ ] Move `learning-kernel` code
- [ ] Update imports in 1-2 pilot apps
- [ ] Verify build/test pass

### Week 2: UI & AI Consolidation

**Day 1-3: UI Library**
- [ ] Create `tutorputor-ui` package
- [ ] Migrate `ui-shared` components
- [ ] Migrate `charts` components
- [ ] Migrate `assessments` utilities
- [ ] Consolidate testing utilities

**Day 4-5: AI Library**
- [ ] Create `tutorputor-ai` package
- [ ] Migrate `ai-proxy` client code
- [ ] Define AI service boundaries
- [ ] Update service configurations

### Week 3: Simulation Consolidation (Complex)

**Day 1-5: Simulation Library**
- [ ] Create `tutorputor-simulation` package
- [ ] Merge `simulation-engine` sources
- [ ] Merge `sim-renderer` (PixiJS/Three.js)
- [ ] Merge `physics-simulation` (Konva)
- [ ] Merge `animator` timeline code
- [ ] Merge `sim-sdk` utilities
- [ ] Resolve dependency conflicts
- [ ] Unified build configuration

### Week 4: App Consolidation

**Day 1-3: Web App Merge**
- [ ] Create new route structure
- [ ] Move `tutorputor-explorer` → `/explore`
- [ ] Move `tutorputor-student` → `/`
- [ ] Move essential `tutorputor-admin` → `/admin`
- [ ] Implement route guards

**Day 4-5: Cleanup & Verification**
- [ ] Remove empty `content-explorer`
- [ ] Update all workspace dependencies
- [ ] Run full test suite
- [ ] Verify builds pass
- [ ] Update documentation

---

## Dependency Graph After Consolidation

### Before (Complex)
```
                ┌──────────────────────────────────────┐
                │         Apps (7 packages)            │
                └──────────┬───────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   12 Libs    │  │   Contracts  │  │   Services   │
│  (complex)   │  │   (shared)   │  │   (14 svc)   │
└──────────────┘  └──────────────┘  └──────────────┘
```

### After (Simplified)
```
                ┌──────────────────────────────────────┐
                │         Apps (3 packages)            │
                │  web, mobile, gateway                │
                └──────────┬───────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   5 Libs     │  │   Contracts  │  │   6 Services │
│ core         │  │   (shared)   │  │   (simplified│
│ simulation   │  │              │  │              │
│ ui, ai       │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## Benefits Summary

### Immediate Benefits

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Libraries** | 12 | 5 | **58% reduction** |
| **Apps** | 7 | 3 | **57% reduction** |
| **Services** | 14 | 6 | **57% reduction** |
| **Package.json files** | 33 | 14 | **58% reduction** |
| **Build configs** | 33 | 14 | **58% reduction** |
| **Dependencies to track** | ~200+ | ~80 | **60% reduction** |

### Operational Benefits

1. **Faster Builds:** Fewer packages = fewer build steps = faster CI/CD
2. **Simpler Updates:** Update dependencies in 5 places instead of 12
3. **Reduced Conflicts:** Less version mismatch between related packages
4. **Easier Onboarding:** New developers understand 5 packages vs 12
5. **Better Testing:** Unified test configurations, shared test utilities

### Code Quality Benefits

1. **Clear Boundaries:** Each library has a distinct purpose
2. **Reduced Duplication:** Shared code in `core` and `ui`
3. **Consistent Patterns:** Single build/test setup per library
4. **Easier Refactoring:** Changes in one place affect all consumers

---

## Risk Assessment & Mitigation

### High Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Simulation merge breaks rendering | High | Extensive visual regression testing, feature flags |
| Admin merge exposes features | High | Route guards, role-based access, admin-only builds |
| Database/Kernel merge breaks apps | High | Pilot with 1 app first, extensive testing |

### Medium Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| Import path changes break builds | Medium | Automated codemod scripts, IDE refactoring |
| Service consolidation affects API | Medium | API versioning, backward compatibility layer |
| Test configuration merge issues | Medium | Parallel test runs during transition |

### Low Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| UI component consolidation | Low | Component storybook, visual testing |
| Charts merge | Low | Simple package, few dependencies |
| Mobile app separation | Low | Keep isolated, no changes needed |

---

## Migration Scripts Needed

### 1. Import Path Codemod
```bash
# Automated import rewriting
npx jscodeshift -t migrate-imports.ts \
  --from="@tutorputor/db" \
  --to="@tutorputor/core/db" \
  src/
```

### 2. Package.json Dependency Updater
```bash
# Update all package.json files
node scripts/update-dependencies.js \
  --old=@tutorputor/db \
  --new=@tutorputor/core \
  --old=@tutorputor/learning-kernel \
  --new=@tutorputor/core
```

### 3. Build Configuration Merger
```bash
# Merge tsconfig, vite.config, etc.
node scripts/merge-build-configs.js \
  --packages=simulation-engine,sim-renderer,physics-simulation \
  --target=tutorputor-simulation
```

---

## Success Criteria

### Technical Metrics
- [ ] All 14 original libs/apps build successfully
- [ ] All 5 new libs/apps build successfully
- [ ] Test coverage maintained or improved
- [ ] Bundle size reduced or maintained
- [ ] Build time reduced by 30%+

### Functional Metrics
- [ ] All user flows work (student, explorer, admin)
- [ ] Simulations render correctly
- [ ] AI features function properly
- [ ] Mobile app unchanged

### Maintenance Metrics
- [ ] New developers onboard in < 1 day
- [ ] Dependency updates take < 30 minutes
- [ ] Build failures reduced by 50%+

---

## Appendix: Package Details

### A. Library Analysis

#### animator (Keep - merge into simulation)
- **Purpose:** Animation engine with timeline
- **Dependencies:** React, React Router, Zod, Jotai
- **Exports:** Main, presets, authoring, auto
- **Merge Target:** `tutorputor-simulation/animator`

#### assessments (Merge into ui)
- **Purpose:** Assessment helper functions
- **Dependencies:** contracts only
- **Size:** Minimal (21 lines package.json)
- **Merge Target:** `tutorputor-ui/assessment`

#### charts (Merge into ui)
- **Purpose:** Recharts wrapper
- **Dependencies:** React, Recharts
- **Size:** Simple (33 lines package.json)
- **Merge Target:** `tutorputor-ui/charts`

#### content-studio-agents (Merge into ai)
- **Purpose:** AI content generation agents
- **Type:** Gradle/Kotlin project
- **Merge Target:** `tutorputor-ai/agents` (Java/TypeScript boundary)

#### learning-kernel (Merge into core)
- **Purpose:** Orchestration kernel
- **Dependencies:** contracts
- **Merge Target:** `tutorputor-core/kernel`

#### physics-simulation (Merge into simulation)
- **Purpose:** Physics with Konva rendering
- **Dependencies:** Konva, React-Konva, React-DnD, Yjs
- **Exports:** 7 submodules (entities, rendering, etc.)
- **Merge Target:** `tutorputor-simulation/physics`

#### sim-renderer (Merge into simulation)
- **Purpose:** PixiJS/Three.js rendering
- **Dependencies:** PixiJS, Three.js, D3, React-Three-Fiber
- **Merge Target:** `tutorputor-simulation/renderer`

#### simulation-engine (Merge into simulation)
- **Purpose:** Runtime/authoring/NL engine
- **Dependencies:** Fastify, contracts, db, ai-proxy
- **Merge Target:** `tutorputor-simulation/engine`

#### testing (Merge into ui)
- **Purpose:** Test utilities
- **Size:** 1 file
- **Merge Target:** `tutorputor-ui/testing`

#### tracing (Merge into ui)
- **Purpose:** Tracing utilities
- **Size:** 1 file
- **Merge Target:** `tutorputor-ui/utils`

#### tutorputor-ai-proxy (Merge into ai)
- **Purpose:** AI proxy service client
- **Merge Target:** `tutorputor-ai/proxy`

#### tutorputor-db (Merge into core)
- **Purpose:** Prisma data access
- **Dependencies:** Prisma, LibSQL, Redis
- **Exports:** Main, testing
- **Merge Target:** `tutorputor-core/db`

#### tutorputor-sim-sdk (Merge into simulation)
- **Purpose:** Simulation SDK
- **Size:** Small (6 items)
- **Merge Target:** `tutorputor-simulation/sdk`

#### tutorputor-ui-shared (Merge into ui)
- **Purpose:** Shared UI utilities
- **Dependencies:** clsx, tailwind-merge
- **Merge Target:** `tutorputor-ui/components`

### B. App Analysis

#### api-gateway (Keep - consolidate services)
- **Purpose:** Fastify API gateway
- **Dependencies:** Fastify, OpenTelemetry, contracts, db, platform
- **Action:** Keep as-is, ensure services route through it

#### content-explorer (Delete)
- **Purpose:** Content discovery
- **Status:** Empty directory (0 items)
- **Action:** Delete

#### tutorputor-admin (Merge into web)
- **Purpose:** Admin dashboard
- **Size:** 92 items
- **Action:** Move essential features to `/admin` route, move advanced to services

#### tutorputor-explorer (Merge into web)
- **Purpose:** Content exploration
- **Size:** 32 items
- **Action:** Move to `/explore` route in web app

#### tutorputor-mobile (Keep)
- **Purpose:** React Native mobile
- **Dependencies:** React Native specific
- **Action:** Keep separate

#### tutorputor-student (Merge into web)
- **Purpose:** Student interface
- **Size:** 2 items (minimal)
- **Action:** Merge into web app default route `/`

#### tutorputor-web (Expand)
- **Purpose:** Main web frontend
- **Size:** 223 items
- **Action:** Expand to absorb explorer, student, essential admin

---

## Conclusion

This consolidation plan reduces the TutorPutor TypeScript/JavaScript footprint from **33 packages (12 libs + 7 apps + 14 services)** to **14 packages (5 libs + 3 apps + 6 services)**, a **58% reduction**.

The simplified architecture will:
- Reduce build and deployment complexity
- Improve developer onboarding
- Lower maintenance overhead
- Create clearer domain boundaries
- Enable faster iteration

**Recommended Next Steps:**
1. Review this plan with the team
2. Prioritize based on current pain points
3. Begin with `tutorputor-core` (lowest risk)
4. Execute in weekly sprints
5. Measure improvement metrics
