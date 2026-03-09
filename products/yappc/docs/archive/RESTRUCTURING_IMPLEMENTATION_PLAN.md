# YAPPC Codebase Restructuring: Migration Guide

> **Version:** 1.0.0  
> **Last Updated:** 2026-02-05  
> **Source:** `ghatana/products/yappc` (legacy monorepo)  
> **Destination:** `ghatana-new/products/yappc` (consolidated architecture)  
> **Target:** Migrate from fragmented, duplicate-heavy codebase to unified, high-cohesion architecture  

---

## 🎯 Migration Overview

### Benefits of Restructuring

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| **Code Duplication** | 46 dual repos + 32 dup components | 0 duplicates | **100% eliminated** |
| **Build Time** | 5 min (Gradle) + 30s (frontend) | 3 min + 15s | **40% faster** |
| **Bundle Size** | 2.5MB | 1.5MB | **40% smaller** |
| **Module Count** | 30+ Gradle + 86 npm scripts | 15 Gradle + 30 scripts | **50% simpler** |
| **Test Coverage** | 70% | 85% | **+15% increase** |

### Migration Strategy

**Strategy:** Gradual Consolidation with Full Backward Compatibility

The restructuring is **non-breaking** with a phased approach:

1. **Phase 1:** Safe deletions (unregistered routes, dead code) - No risk
2. **Phase 2:** Component consolidation with feature flags - Gradual rollout
3. **Phase 3:** Build system simplification - Parallel operation
4. **Phase 4:** Architecture refactoring - Domain-by-domain

**No immediate breaking changes** - existing code continues to work during migration.

---

## 📋 Prerequisites

### Before You Start

- [ ] **Full build verification** - All products must compile independently
- [ ] **Test baseline** - Record current test coverage and pass rates
- [ ] **Backup branch** - Create `archive/pre-restructure-2026-02` tag
- [ ] **Downtime plan** - Coordinate with team for component consolidation
- [ ] **Rollback plan** - Document procedure to revert each phase

### Build Verification Commands

```bash
# Verify Java backend compiles (ghatana-new structure)
cd ~/Development/ghatana-new
./gradlew :products:yappc:services:compileJava
# Expected: BUILD SUCCESSFUL

# Verify frontend builds (after migration to ghatana-new)
cd ~/Development/ghatana-new/products/yappc/frontend
pnpm build
# Expected: Build completes without errors

# Verify tests pass
pnpm test:run
# Expected: All tests passing (current baseline: 70% coverage)
```

```
ghatana/products/yappc/              (SOURCE - Legacy Structure)
├── backend/api/                      # Java ActiveJ HTTP API server
│   ├── src/main/java/               # ~294 Java files
│   │   ├── controller/              # HTTP controllers (ActiveJ servlets)
│   │   ├── service/                # Business logic services
│   │   ├── repository/             # Data access (InMemory + JDBC dual impl)
│   │   └── config/                 # DI modules (ProductionModule, DevelopmentModule)
│   └── build.gradle.kts            # Standalone build
├── core/                            # Java multi-module Gradle project (30+ modules)
│   ├── framework/                   # 3 submodules (api, core, integration-test)
│   ├── knowledge-graph/             # Consolidated KG module
│   ├── ai-requirements/             # 4 submodules
│   ├── scaffold/                    # 5 submodules
│   ├── refactorer-consolidated/     # 6 modules
│   └── ...
└── frontend/                        # Node.js/TypeScript monorepo
    └── apps/, libs/, packages/

ghatana-new/products/yappc/         (DESTINATION - Consolidated Structure)
├── build.gradle.kts                 # Aggregate project
├── platform/                        # YAPPC platform dependencies
│   └── build.gradle.kts
├── services/                        # Service layer with app plugin
│   ├── build.gradle.kts
│   └── src/main/java/              # Migrated from backend/api + core
└── frontend/                        # Migrated from ghatana/products/yappc/frontend
```

### 1.2 Critical Issues Identified

#### **HIGH PRIORITY - Code Duplication**

| Category | Count | Impact | Files |
|----------|-------|--------|-------|
| **Dual Repository Implementations** | 46 | HIGH | InMemory + JDBC pairs for each entity |
| **Duplicate Dashboard Components** | 12 | HIGH | UnifiedPersonaDashboard (2), OperationsDashboard (3), KPICard (2) |
| **Legacy DevSecOps Routes** | 32 | MEDIUM | Unregistered but present in codebase |
| **Canvas Components** | 8 | MEDIUM | Multiple canvas implementations |
| **Performance Dashboards** | 4 | MEDIUM | Scattered across libs |

**Specific Duplicates Found:**
- `UnifiedPersonaDashboard`: `components/devsecops/` (575 lines) vs `routes/devsecops/components/` (681 lines)
- `OperationsDashboard`: `routes/devsecops/components/` (239 lines) vs `components/devsecops/` (663 lines)
- `PerformanceDashboard`: libs/canvas/ (715 lines), libs/ui/components/ (319 lines), libs/ui/Performance/ (504 lines), libs/performance-monitor/ (260 lines)
- `KPICard`: `components/dashboard/widgets/` (140 lines) vs `libs/ui/DevSecOps/KPICard/` (124 lines)
- Repository pairs: InMemory*Repository + Jdbc*Repository for each entity type

#### **MEDIUM PRIORITY - Coupling Issues**

| Issue | Severity | Description |
|-------|----------|-------------|
| **Backend-Frontend Coupling** | HIGH | Tight coupling through shared types, GraphQL schemas |
| **Service Interdependencies** | MEDIUM | Circular dependencies between service layer components |
| **Module Cross-References** | MEDIUM | Core modules referencing each other |
| **State Management Fragmentation** | MEDIUM | Jotai atoms scattered, Redux remnants, local state mixing |

#### **LOW PRIORITY - Technical Debt**

| Issue | Count | Location |
|-------|-------|----------|
| Unused imports | ~200 | Throughout frontend |
| Commented code blocks | ~50 | Routes, components |
| Any types | ~150 | TypeScript files |
| Inline styles | ~80 | Components |

### 1.3 Build System Complexity

#### Gradle (Java Backend)
```
settings.gradle.kts: 139 lines, 30+ module includes
- Version catalog from parent workspace
- Complex project directory mappings
- Conditional subproject configuration
- Dependency locking enabled
```

#### Issues:
- 30+ Java modules with deep nesting (6+ levels)
- Manual project directory mappings required
- Version catalog external dependency
- Complex module path structure

#### pnpm/Node.js (Frontend)
```
package.json: 214 lines
- Workspaces: apps/*, libs/*, packages/*
- 86 scripts (many overlapping)
- Mixed tooling: ESLint, Prettier, Vitest, Playwright, Lighthouse, jscpd
- 21 dependencies, 83 devDependencies
```

#### Issues:
- 86 npm scripts (many redundant)
- Overlapping lint/format/test configurations
- Unused dev dependencies (Storybook referenced but empty)
- Complex TypeScript project references
- Mixed state management libraries

---

## 2. Industry Best Practices Benchmark

### 2.1 Modern Architecture Patterns

| Pattern | Current State | Industry Standard | Gap |
|---------|--------------|-------------------|-----|
| **Clean Architecture** | Partial (some layering) | Hexagonal/Onion with clear boundaries | Ports/adapters not well defined |
| **Domain-Driven Design** | Some DDD concepts | Full Ubiquitous Language, Aggregates, Value Objects | Limited tactical patterns |
| **Micro-frontends** | Single SPA | Module Federation / Native Federation | Monolithic frontend |
| **CQRS** | Not implemented | Command/Query separation with event sourcing | Missing entirely |
| **Event Sourcing** | Not implemented | Event store as source of truth | Missing entirely |
| **API Gateway** | Reverse proxy | BFF pattern with composition | Direct service calls |

### 2.2 Modern Tech Stack Recommendations

#### Java Backend
| Current | Modern Alternative | Benefit |
|---------|-------------------|---------|
| ActiveJ (custom HTTP) | Spring Boot 3.x + WebFlux | Ecosystem, developer experience, monitoring |
| Manual DI modules | Spring Context + Spring AI | Reduced boilerplate, AI-native |
| Dual repository impl | Spring Data JPA + QueryDSL | Single source, type-safe queries |
| In-memory dev stores | Testcontainers + real DB | Production parity |
| Custom auth | Spring Security 6 + OAuth2 | Standards-compliant, battle-tested |
| Manual metrics | Micrometer + Spring Boot Actuator | Auto-configuration, observability |

#### Frontend (TypeScript/React)
| Current | Modern Alternative | Benefit |
|---------|-------------------|---------|
| React Router 6 | TanStack Router | Type-safe routing, file-based |
| Jotai (atom-based) | Zustand or Redux Toolkit | Better devtools, persistence |
| Custom hooks | TanStack Query (React Query) | Server state caching, sync |
| GraphQL (partial) | tRPC or REST with OpenAPI | End-to-end type safety |
| Custom styling | Tailwind + Radix/Shadcn | Consistency, accessibility |
| Manual canvas | TLDraw or Excalidraw libs | Production-ready canvas |

### 2.3 Code Quality Standards

| Metric | Current | Industry Target | Action |
|--------|---------|-----------------|--------|
| Test Coverage | ~70% | 85%+ | Increase coverage |
| Cyclomatic Complexity | High in controllers | <10 per function | Refactor controllers |
| Duplicate Code | ~2000 lines | <500 lines | Consolidation plan |
| Bundle Size | ~2.5MB | <1.5MB | Tree-shaking, code splitting |
| Type Safety | 85% | 98%+ | Eliminate `any` types |
| API Documentation | OpenAPI (partial) | Full OpenAPI 3.1 | Complete spec |

---

## 3. Restructuring Implementation Plan

### Phase 1: Foundation Cleanup (Weeks 1-4)

#### Week 1: Repository Consolidation (Java)
**Goal:** Eliminate dual InMemory/JDBC repository implementations

**Actions:**
1. **Create Repository Abstraction Layer**
   - Define `Repository<T, ID>` interface in `core/domain/repository/`
   - Create `RepositoryFactory` for environment-specific implementations
   - Move all InMemory repos to `test/` scope only

2. **Consolidate to Single JDBC Implementation**
   - Keep only Jdbc*Repository implementations
   - Use H2 for development (production parity)
   - Add Testcontainers for integration tests

3. **Refactor DI Configuration**
   - Remove `SharedBaseModule` repository providers
   - Use Spring Data JPA if migrating, or single factory pattern

**Files to Modify:**
```
backend/api/src/main/java/com/ghatana/yappc/api/repository/
├── Keep only: RequirementRepository.java (interface)
├── Delete: InMemoryRequirementRepository.java
├── Delete: JdbcRequirementRepository.java (move impl detail)
└── New: JpaRequirementRepository.java (if migrating to JPA)
```

**Expected Reduction:** ~2300 lines of duplicate repository code

**BEFORE/AFTER Patterns:**

**BEFORE (Dual Repository Implementations):**

```java
// InMemoryRequirementRepository.java
@Repository
public class InMemoryRequirementRepository implements RequirementRepository {
  // In-memory implementation
}

// JdbcRequirementRepository.java
@Repository
public class JdbcRequirementRepository implements RequirementRepository {
  // JDBC implementation
}
```

**AFTER (Single JPA Implementation with Library Mode):**

```java
// AFTER - Single JPA implementation with H2 for dev

// Interface (unchanged)
public interface RequirementRepository extends Repository<Requirement, String> {
  // Spring Data JPA or custom methods
}

// Single implementation using Spring Data JPA
@Repository
public interface JpaRequirementRepository extends 
    RequirementRepository, 
    JpaRepository<Requirement, String> {
  // Derived queries automatically implemented
}

// Library Mode configuration - profile-based
type: custom
metadata:
  name: yappc-backend
modules:
  - id: persistence
    pack: java-jpa-h2
    path: "./backend/persistence"
    variables:
      database: "${ENV:H2_FILE}"  # H2 for dev, PostgreSQL for prod
      hibernate.ddl-auto: "validate"
```

#### Week 2: Service Layer Refactoring (Java)
**Goal:** Apply Clean Architecture, decouple services

**Actions:**
1. **Define Service Interfaces**
   - Create `RequirementService` interface in domain
   - Create `RequirementUseCase` for application layer
   - Implement in service layer

2. **Apply Dependency Inversion**
   - Services depend on repository interfaces, not implementations
   - Controllers depend on service interfaces
   - Use constructor injection exclusively

3. **Create Application Layer**
   - Move business logic from controllers to services
   - Create DTOs for API boundaries
   - Implement mappers (MapStruct)

**New Package Structure:**
```
com.ghatana.yappc.api/
├── domain/                      # Domain entities, value objects, domain services
│   ├── requirement/
│   │   ├── Requirement.java
│   │   ├── RequirementId.java   # Value object
│   │   └── RequirementRepository.java
├── application/                 # Use cases, DTOs, application services
│   ├── requirement/
│   │   ├── CreateRequirementUseCase.java
│   │   ├── RequirementDto.java
│   │   └── RequirementMapper.java
├── infrastructure/             # Persistence, external APIs, messaging
│   ├── persistence/
│   │   └── JpaRequirementRepository.java
└── interfaces/                 # Controllers, presenters
    └── http/
        └── RequirementController.java
```

#### Week 3-4: Frontend Component Consolidation
**Goal:** Eliminate 32+ duplicate components, establish single sources of truth

**Actions:**

**Day 1-2: DevSecOps Route Removal**
```bash
# Safe removal - routes already unregistered
rm -rf frontend/apps/web/src/routes/devsecops/
# Update any remaining imports
```

**Day 3-4: Dashboard Component Consolidation**
1. **UnifiedPersonaDashboard**
   - Keep: `routes/devsecops/components/UnifiedPersonaDashboard.tsx` (AI-integrated)
   - Delete: `components/devsecops/UnifiedPersonaDashboard.tsx`
   - Update imports in 15+ files

2. **OperationsDashboard**
   - Keep: `components/devsecops/OperationsDashboard.tsx` (AnomalyBanner, SmartSuggestions)
   - Delete: `routes/devsecops/components/OperationsDashboard.tsx`
   - Delete: duplicate in other locations

3. **PerformanceDashboard**
   - Create: `libs/ui/src/components/Performance/PerformanceDashboard.tsx` (canonical)
   - Extract shared metrics components
   - Delete: libs/canvas/, libs/ui/components/, libs/performance-monitor/ versions

**Day 5: Persona Components**
1. **PersonaSelector**
   - Consolidate to `components/persona/PersonaSelector.tsx`
   - Remove `PersonaSwitcherCompact` (use variant="compact")
   - Update `_shell.tsx` to use single component

2. **KPICard**
   - Consolidate to `libs/ui/src/components/DevSecOps/KPICard/KPICard.tsx`
   - Delete: `components/dashboard/widgets/KPICard.tsx`

**Canonical Component Registry (Single Sources of Truth):**

| Component Type | Canonical Location | Import Path |
|---------------|-------------------|-------------|
| PersonaType | `libs/types/src/devsecops/persona.ts` | `@yappc/types/devsecops` |
| usePersonas | `libs/ui/src/hooks/useConfigData.ts` | `@yappc/ui/hooks/useConfigData` |
| UnifiedPersonaDashboard | `routes/devsecops/components/` | Local import |
| OperationsDashboard | `components/devsecops/` | Local import |
| PersonaSelector | `components/persona/` | Local import |
| KPICard | `libs/ui/DevSecOps/KPICard/` | `@yappc/ui/components/DevSecOps` |
| TopNav | `libs/ui/components/DevSecOps/TopNav/` | `@yappc/ui/components/DevSecOps` |
| DataTable | `libs/ui/components/DevSecOps/DataTable/` | `@yappc/ui/components/DevSecOps` |
| KanbanBoard | `libs/ui/components/DevSecOps/KanbanBoard/` | `@yappc/ui/components/DevSecOps` |
| PerformanceDashboard | `libs/ui/components/Performance/` | `@yappc/ui/components/Performance` |

---

### Phase 2: Build System Simplification (Weeks 5-6)

#### Week 5: Gradle Build Optimization (Java)
**Goal:** Simplify 30+ module structure, reduce build complexity

**Actions:**

1. **Consolidate Core Modules**
   - Merge `core/framework/framework-api`, `framework-core` into single `core/framework`
   - Merge `core/ai-requirements/` submodules into single `core/ai-requirements`
   - Merge `core/scaffold/` submodules into `core/scaffold`

2. **Simplify Refactorer Structure**
   - Evaluate if 6 consolidated modules can be reduced further to 3-4
   - Consider merging `refactorer-adapters` into `refactorer-engine`

**New Simplified Settings:**
```kotlin
// settings.gradle.kts - Simplified from 30+ to 15 includes
include 'core:framework'
include 'core:knowledge-graph'
include 'core:cli-tools'
include 'core:ai-requirements'        // Merged submodules
include 'core:ai'
include 'core:canvas-ai'
include 'core:lifecycle'
include 'core:scaffold'               // Merged submodules
include 'core:sdlc-agents'
include 'core:agent-integration'
include 'core:yappc-client-api'
include 'core:yappc-plugin-spi'
include 'core:refactorer'             // Further consolidated
include 'infrastructure:datacloud'
```

3. **Enable Gradle Build Cache**
   - Configure remote build cache
   - Enable configuration cache
   - Parallel project execution

**Expected Outcome:**
- 50% reduction in configuration time
- 30% faster incremental builds
- Simpler dependency management

#### Week 6: Frontend Build System (Node.js)
**Goal:** Simplify 86 scripts, consolidate configurations

**Actions:**

1. **Consolidate npm Scripts**
   - Reduce 86 scripts to 30 essential ones
   - Group by category: dev, build, test, lint, verify

```json
{
  "scripts": {
    "dev": "pnpm --filter web dev",
    "dev:desktop": "pnpm --filter desktop tauri dev",
    "build": "pnpm --filter web build",
    "build:all": "pnpm -r build",
    "test": "vitest run",
    "test:watch": "vitest",
    "test:e2e": "playwright test",
    "lint": "eslint 'apps/**/src/**/*.{ts,tsx}' 'libs/**/src/**/*.{ts,tsx}'",
    "lint:fix": "pnpm lint --fix",
    "format": "prettier --write \"**/*.{ts,tsx,js,jsx,json,md,css,scss}\"",
    "typecheck": "tsc --noEmit",
    "verify": "pnpm typecheck && pnpm lint && pnpm test",
    "clean": "pnpm -r clean && rm -rf node_modules"
  }
}
```

2. **Simplify ESLint Configuration**
   - Single `eslint.config.mjs` (already exists)
   - Remove `.eslintignore.internal`
   - Consolidate rules

3. **Remove Unused Dependencies**
   - Audit and remove unused dev dependencies
   - Consider removing Storybook (empty directory)
   - Evaluate Lighthouse CI necessity

4. **TypeScript Project References**
   - Simplify `tsconfig.refs.json`
   - Ensure proper incremental builds

---

### Phase 3: State Management & Data Layer (Weeks 7-8)

#### Week 7: State Management Unification (Frontend)
**Goal:** Consolidate Jotai, Redux remnants, and local state into single pattern

**Actions:**

1. **Evaluate Migration to Zustand**
   - Zustand provides simpler API than Jotai
   - Better devtools support
   - Easier persistence

2. **Create State Registry**
   - Single `stores/` directory structure
   - Feature-based slices
   - Clear separation: client state vs server state

**New State Structure:**
```
libs/state/src/
├── client/                     # UI/client-only state
│   ├── canvas.store.ts
│   ├── ui.store.ts
│   └── theme.store.ts
├── server/                     # Server state (TanStack Query)
│   ├── requirements.query.ts
│   ├── projects.query.ts
│   └── workspaces.query.ts
├── domain/                     # Domain state (cross-cutting)
│   ├── auth.store.ts
│   └── persona.store.ts
└── index.ts
```

3. **Migrate Server State to TanStack Query**
   - Replace custom fetch hooks with `useQuery`, `useMutation`
   - Automatic caching, background updates
   - Optimistic updates

#### Week 8: API Layer Standardization
**Goal:** Unify REST, GraphQL, and WebSocket APIs

**Actions:**

1. **Choose Primary API Pattern**
   - Recommendation: REST with OpenAPI (simpler caching, tooling)
   - Keep GraphQL for complex queries only
   - WebSocket for real-time features

2. **Generate API Clients**
   - Use OpenAPI generator for TypeScript clients
   - Single source of truth from Java backend
   - Automatic type generation

3. **Create API Client Library**
   - `libs/api/src/` - Auto-generated clients
   - Typed request/response
   - Error handling middleware

---

### Phase 4: Testing & Quality (Weeks 9-10)

#### Week 9: Testing Strategy Consolidation
**Goal:** Unify testing approach, increase coverage

**Actions:**

1. **Consolidate Test Runners**
   - Primary: Vitest (fast, modern)
   - E2E: Playwright (keep)
   - Remove Jest configuration if redundant

2. **Testing Pyramid**
   - Unit tests: 70% (components, utilities)
   - Integration tests: 20% (API integration, state)
   - E2E tests: 10% (critical user flows)

3. **Coverage Gates**
   - Current: 80% line coverage
   - Target: 85% line, 80% branch
   - Enforce in CI

4. **Mock Strategy**
   - MSW (Mock Service Worker) for API mocking
   - Centralized mock data
   - Shared test utilities

#### Week 10: Code Quality Automation
**Goal:** Prevent new technical debt

**Actions:**

1. **Pre-commit Hooks**
   - Husky + lint-staged (already configured)
   - Add type checking
   - Add test running for staged files

2. **CI/CD Pipeline**
   - Build verification
   - Lint and format checks
   - Test execution
   - Coverage reporting
   - Bundle size monitoring

3. **Dependency Management**
   - Dependabot or Renovate for automated updates
   - Security vulnerability scanning
   - License compliance checking

---

### Phase 5: Advanced Architecture (Weeks 11-16)

#### Week 11-12: Module System Refactoring
**Goal:** Implement proper modular architecture

**Actions:**

1. **Java: Implement Hexagonal Architecture**
   - Clear domain boundaries
   - Port/adapter pattern
   - Testable without Spring context

2. **Frontend: Feature-Based Organization**
   - Co-locate feature code (component, hooks, types, tests)
   - Public API via `index.ts` exports
   - Private implementation details hidden

**Feature-Based Structure:**
```
libs/features/src/
├── canvas/
│   ├── components/
│   ├── hooks/
│   ├── types/
│   ├── utils/
│   ├── stores/
│   ├── api/
│   └── index.ts              # Public API
├── requirements/
├── projects/
└── devsecops/
```

#### Week 13-14: Performance Optimization
**Goal:** Achieve industry-leading performance

**Actions:**

1. **Code Splitting**
   - Route-based splitting (React Router already supports)
   - Component lazy loading
   - Dynamic imports for heavy features

2. **Bundle Analysis**
   - Regular bundle analysis
   - Tree-shaking verification
   - Dependency size monitoring

3. **Canvas Optimization**
   - Virtualization for large canvases
   - Web Workers for heavy computations
   - Offscreen canvas where supported

4. **Java Backend Performance**
   - Connection pooling optimization
   - Caching layer (Redis/Caffeine)
   - Async processing for heavy operations

---

## 🚚 Migration Path Mapping

### Source to Destination File Mapping

| Source (`ghatana/products/yappc`) | Destination (`ghatana-new/products/yappc`) | Action |
|-----------------------------------|-------------------------------------------|--------|
| `backend/api/src/main/java/` | `services/src/main/java/` | Migrate with consolidation |
| `core/*/src/main/java/` | `services/src/main/java/` | Merge into services layer |
| `frontend/apps/` | `frontend/apps/` | Copy as-is, then consolidate |
| `frontend/libs/` | `frontend/libs/` | Copy as-is, deduplicate after |
| `frontend/package.json` | `frontend/package.json` | Simplify 86 scripts to 30 |
| `settings.gradle.kts` | `build.gradle.kts` | Replace with simplified structure |

### Migration Commands

```bash
# 1. Copy frontend (preserving git history)
cd ~/Development/ghatana-new/products/yappc
rsync -av --exclude='node_modules' --exclude='dist' \
  ~/Development/ghatana/products/yappc/frontend/ \
  ./frontend/

# 2. Migrate Java source (selective copy with restructuring)
mkdir -p services/src/main/java/com/ghatana/yappc
cp -r ~/Development/ghatana/products/yappc/backend/api/src/main/java/* \
  services/src/main/java/com/ghatana/yappc/

# 3. Build verification in new location
cd ~/Development/ghatana-new
./gradlew :products:yappc:services:compileJava
```

### Current Dependency Graph (Fragmented)

```
┌─────────────────────────────────────────────────────────────────┐
│                     YAPPC Frontend                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   canvas     │  │     ui       │  │    types     │          │
│  │  (563 items) │  │  (733 items) │  │  (16 items)  │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                    │
│         ▼                 ▼                 ▼                  │
│  ┌────────────────────────────────────────────────────┐         │
│  │          DUPLICATE COMPONENTS (32 files)            │         │
│  │  • UnifiedPersonaDashboard (2 versions)            │         │
│  │  • OperationsDashboard (3 versions)                │         │
│  │  • PerformanceDashboard (4 versions)                │         │
│  │  • KPICard (2 versions)                            │         │
│  └────────────────────────────────────────────────────┘         │
│                                                                  │
│  ┌────────────────────────────────────────────────────┐         │
│  │         LEGACY ROUTES (unregistered)                │         │
│  │  • routes/devsecops/ (32 files)                    │         │
│  └────────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────┘
```

### Target Dependency Graph (Consolidated - Library Mode)

```
┌─────────────────────────────────────────────────────────────────┐
│                     YAPPC Frontend (Consolidated)                │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   canvas     │  │     ui       │  │    types     │          │
│  │  (400 items) │  │  (600 items) │  │  (16 items)  │          │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘          │
│         │                 │                 │                    │
│         └─────────────────┼─────────────────┘                    │
│                           │                                      │
│                           ▼                                      │
│  ┌────────────────────────────────────────────────────┐         │
│  │         CANONICAL COMPONENTS (single source)          │         │
│  │  • @yappc/ui/components/DevSecOps/*                │         │
│  │  • @yappc/ui/components/Performance/*              │         │
│  │  • @yappc/types/devsecops                          │         │
│  └────────────────────────────────────────────────────┘         │
│                                                                  │
│  ┌────────────────────────────────────────────────────┐         │
│  │              apps/web/ (clean routes)                │         │
│  │  • No duplicate component definitions              │         │
│  │  • All imports from @yappc/* libraries             │         │
│  └────────────────────────────────────────────────────┘         │
└─────────────────────────────────────────────────────────────────┘

Legend:
─── Import dependency
Numbers indicate file count reduction after consolidation
```

#### Week 15-16: Documentation & Developer Experience
**Goal:** Exceed industry standards for documentation

**Actions:**

1. **Architecture Decision Records (ADRs)**
   - Document major architectural decisions
   - Why choices were made
   - Trade-offs considered

2. **API Documentation**
   - Complete OpenAPI specification
   - Interactive documentation (Swagger UI or Scalar)
   - Code examples

3. **Developer Guides**
   - Onboarding guide
   - Contribution guidelines
   - Architecture overview
   - Component usage examples (Storybook or similar)

4. **Automated Documentation**
   - TypeDoc for TypeScript
   - Javadoc for Java
   - Generated from code comments

---

## 4. Detailed File Modifications

### 4.1 Files to Delete (35+ files)

```bash
# DevSecOps Routes (unregistered, safe to remove)
frontend/apps/web/src/routes/devsecops/
├── _layout.tsx
├── index.tsx
├── admin.tsx
├── canvas.tsx
├── detail-pages.tsx
├── domains.tsx
├── operations.tsx
├── phases.tsx
├── reports.tsx
├── settings.tsx
├── task-board.tsx
├── tasks.tsx
├── team-board.tsx
├── templates.tsx
├── workflows.tsx
├── diagram/
├── item/
├── persona/
├── phase/
├── reports/
├── task/
└── components/
    ├── AdvancedFilter.tsx
    ├── AnalyticsChart.tsx
    ├── ExportButton.tsx
    ├── PersonaSelector.tsx
    ├── UnifiedPersonaDashboard.tsx
    └── UserPreferences.tsx

# Duplicate Dashboard Components
frontend/apps/web/src/components/devsecops/
├── UnifiedPersonaDashboard.tsx
├── OperationsDashboard.tsx
├── PersonaSelector.tsx
└── DevSecOpsTopBar.tsx (if duplicate exists)

frontend/apps/web/src/components/dashboard/widgets/KPICard.tsx

# Duplicate Performance Dashboards
frontend/libs/canvas/src/components/PerformanceDashboard.tsx
frontend/libs/ui/src/components/PerformanceDashboard.tsx
frontend/libs/performance-monitor/src/components/PerformanceDashboard.tsx

# Legacy Routes
frontend/apps/web/src/routes/canvas-redirect.tsx
frontend/apps/web/src/routes/page-designer.tsx (verify usage first)

# Legacy Tests (review first)
frontend/apps/web/src/routes/__tests__/canvas-test.*.spec.tsx
```

### 4.2 Files to Consolidate

| From | To | Action |
|------|-----|--------|
| `routes/devsecops/components/UnifiedPersonaDashboard.tsx` | `components/devsecops/UnifiedPersonaDashboard.tsx` | Merge AI features into canonical version |
| `routes/devsecops/components/OperationsDashboard.tsx` | `components/devsecops/OperationsDashboard.tsx` | Merge, keep AnomalyBanner, SmartSuggestions |
| `libs/canvas/src/components/PerformanceDashboard.tsx` | `libs/ui/src/components/Performance/` | Extract, consolidate all versions |
| `libs/ui/src/components/PerformanceDashboard.tsx` | `libs/ui/src/components/Performance/` | Merge into canonical |
| `components/dashboard/widgets/KPICard.tsx` | `libs/ui/DevSecOps/KPICard/` | Merge, export from @yappc/ui |
| `components/persona/PersonaSwitcherCompact` | `components/persona/PersonaSwitcher.tsx` | Add variant="compact" prop |

### 4.3 Import Statement Updates (15+ files)

Example migration:
```typescript
// BEFORE
import { UnifiedPersonaDashboard } from '../components/devsecops/UnifiedPersonaDashboard';
import { PersonaType } from '../components/devsecops/types';
import { KPICard } from '../components/dashboard/widgets/KPICard';

// AFTER
import { UnifiedPersonaDashboard } from './components/UnifiedPersonaDashboard';
import { PersonaType } from '@yappc/types/devsecops';
import { KPICard } from '@yappc/ui/components/DevSecOps';
```

---

## 5. Success Metrics

### 5.1 Code Quality Metrics

| Metric | Before | After | Target |
|--------|--------|-------|--------|
| Lines of Code (Java) | ~50,000 | ~35,000 | 30% reduction |
| Lines of Code (TS/JS) | ~150,000 | ~100,000 | 33% reduction |
| Duplicate Code | ~3000 lines | <500 lines | 83% reduction |
| Test Coverage | 70% | 85% | +15% increase |
| Cyclomatic Complexity | High | <15 avg | Reduced by 50% |
| Bundle Size | 2.5MB | 1.5MB | 40% reduction |

### 5.2 Build & Performance Metrics

| Metric | Before | After | Target |
|--------|--------|-------|--------|
| Gradle Build Time | 5 min | 3 min | 40% faster |
| Frontend Dev Start | 30 sec | 15 sec | 50% faster |
| Test Execution | 10 min | 5 min | 50% faster |
| CI Pipeline | 20 min | 12 min | 40% faster |
| Bundle Load Time | 3 sec | 1.5 sec | 50% faster |
| Time to Interactive | 4 sec | 2 sec | 50% faster |

### 5.3 Developer Experience Metrics

| Metric | Before | After | Target |
|--------|--------|-------|--------|
| npm Script Count | 86 | 30 | 65% reduction |
| Gradle Module Count | 30+ | 15 | 50% reduction |
| Component Duplicates | 12 | 0 | 100% elimination |
| Average Import Path Depth | 5 levels | 3 levels | 40% reduction |
| Onboarding Time | 2 weeks | 3 days | 79% reduction |

---

## ✅ Post-Migration Checklist

After each phase, verify:

### Phase 1 Verification (Safe Deletions)

- [ ] **Build Status:** `pnpm build` succeeds
- [ ] **Test Status:** All tests passing (should be same as baseline)
- [ ] **Bundle Size:** Reduced by ~10% (duplicate code removed)
- [ ] **Import Check:** No broken imports in source
- [ ] **Route Check:** Application routes still functional

**Verification Commands:**
```bash
# Full build
pnpm build

# Test execution
pnpm test:run

# Bundle analysis
pnpm build && ls -lh frontend/apps/web/dist/
# Expected: ~2.2MB (down from 2.5MB)

# Import verification
grep -r "from.*devsecops.*components" frontend/apps/web/src/ || echo "Clean"
```

### Phase 2 Verification (Component Consolidation)

- [ ] **Component Count:** 12 → 0 duplicates
- [ ] **Import Depth:** Average path depth reduced
- [ ] **Bundle Size:** Reduced to ~2.0MB
- [ ] **Functionality:** All dashboards operational
- [ ] **Feature Flags:** New consolidated components work in both modes

### Phase 3 Verification (Build Simplification)

- [ ] **Script Count:** 86 → 30 npm scripts
- [ ] **Gradle Modules:** 30+ → 15 modules
- [ ] **Build Time:** Gradle under 3 min, frontend under 15s
- [ ] **Test Time:** Under 5 minutes

---

## 🚨 Rollback Plan

### Quick Rollback (Per Phase)

If issues encountered after Phase 1 (Safe Deletions):

```bash
# Restore from git
git checkout archive/pre-restructure-2026-02 -- frontend/apps/web/src/routes/devsecops/
git checkout archive/pre-restructure-2026-02 -- frontend/apps/web/src/components/devsecops/

# Verify restoration
pnpm build
pnpm test:run
```

### Rollback Checklist

- [ ] Stop current migration phase
- [ ] Restore files from backup branch
- [ ] Rebuild and verify
- [ ] Verify all tests pass
- [ ] Notify team of rollback
- [ ] Document issue for future attempt

---

## 🐛 Common Migration Issues

### Issue 1: Missing Imports After Component Consolidation

**Symptom:**
```
Error: Cannot find module '../components/devsecops/UnifiedPersonaDashboard'
```

**Cause:** Import paths not updated after file move.

**Solution:**
```bash
# Find all broken imports
grep -r "from.*components/devsecops" frontend/apps/web/src/

# Bulk update with sed
find frontend/apps/web/src -name "*.tsx" -exec sed -i \
  's|from "../components/devsecops/|from "@yappc/ui/components/DevSecOps/|g' {} \;
```

### Issue 2: Gradle Module Path Errors

**Symptom:**
```
Project with path ':core:framework:framework-api' could not be found
```

**Cause:** Old module paths referenced after consolidation.

**Solution:**
```kotlin
// Update build.gradle.kts dependencies
// BEFORE
dependencies {
  implementation(project(":core:framework:framework-api"))
}

// AFTER
dependencies {
  implementation(project(":core:framework"))  // Merged module
}
```

### Issue 3: npm Script Conflicts

**Symptom:**
```
ERR_PNPM_NO_SCRIPT: Missing script: test:coverage
```

**Cause:** Scripts referenced in CI but removed in consolidation.

**Solution:**
```json
// Add compatibility aliases during transition
{
  "scripts": {
    "test:coverage": "vitest run --coverage",  // Redirect to new command
    "test": "vitest run"                       // Primary command
  }
}
```

---

## 6. Risk Mitigation

### 6.1 High-Risk Items

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| Breaking changes in consolidation | High | High | Comprehensive test suite, feature flags, gradual rollout |
| Missing dependencies after deletions | Medium | Medium | Dependency analysis, incremental builds, CI verification |
| Performance regression | Low | High | Performance benchmarks, bundle analysis, A/B testing |
| Knowledge loss | Medium | Medium | Documentation, ADRs, pair programming, code review |

### 6.2 Rollback Strategy

1. **Feature Flags**
   - Wrap new consolidated components in feature flags
   - Enable gradual rollout (5% → 25% → 50% → 100%)
   - Instant rollback capability

2. **Git Strategy**
   - Create feature branch for each phase
   - Tag stable releases before major changes
   - Keep backup branches for 30 days

3. **Testing Gates**
   - Unit tests must pass
   - Integration tests must pass
   - E2E smoke tests must pass
   - Manual QA verification for critical flows

---

## 7. Implementation Timeline

```
Week 1-4:   Phase 1 - Foundation Cleanup
            ├── Week 1: Repository consolidation (Java)
            ├── Week 2: Service layer refactoring (Java)
            ├── Week 3: Component consolidation part 1
            └── Week 4: Component consolidation part 2

Week 5-6:   Phase 2 - Build System Simplification
            ├── Week 5: Gradle optimization
            └── Week 6: Frontend build simplification

Week 7-8:   Phase 3 - State Management & Data Layer
            ├── Week 7: State management unification
            └── Week 8: API layer standardization

Week 9-10:  Phase 4 - Testing & Quality
            ├── Week 9: Testing strategy
            └── Week 10: Quality automation

Week 11-16: Phase 5 - Advanced Architecture
            ├── Week 11-12: Module system refactoring
            ├── Week 13-14: Performance optimization
            └── Week 15-16: Documentation & DX
```

---

## 8. Immediate Actions (This Week)

### Day 1-2: Preparation
- [ ] Create feature branch: `refactor/codebase-consolidation`
- [ ] Run full test suite, establish baseline
- [ ] Backup critical configuration files
- [ ] Set up monitoring for build times, bundle sizes

### Day 3-4: Safe Deletions
- [ ] Remove DevSecOps routes directory
- [ ] Build and verify (expect success)
- [ ] Commit: "Remove unused DevSecOps routes (32 files)"

### Day 5: Component Consolidation Start
- [ ] Delete duplicate UnifiedPersonaDashboard
- [ ] Update imports in dependent files
- [ ] Run tests, verify functionality
- [ ] Commit: "Consolidate UnifiedPersonaDashboard to single source of truth"

---

## 9. Conclusion

This restructuring plan addresses all critical issues identified in the YAPPC codebase:

1. **Eliminates 32+ duplicate components** - Single sources of truth established
2. **Consolidates 46 dual repository implementations** - Single JDBC with H2 for dev
3. **Simplifies 30+ module build system** - 50% reduction in complexity
4. **Reduces 86 npm scripts to 30** - Focused, maintainable scripts
5. **Implements Clean Architecture** - High cohesion, low coupling
6. **Exceeds industry standards** - 85% test coverage, 1.5MB bundle target

**Expected Total Reduction:**
- 60% code reduction in redundant areas
- 50% build time improvement
- 40% bundle size reduction
- 100% duplicate elimination

**Investment:** 16 weeks (4 months)
**ROI:** Reduced maintenance, faster onboarding, fewer bugs, improved performance

---

**Document Version:** 1.0.0  
**Created:** February 2026  
**Author:** Principal Software Engineer Review  
**Status:** Ready for implementation
