# Ghatana Monorepo: Detailed Implementation Plan

**Based on:** Part 9 Execution Plan (Items 59, 60, 61, 63, 64, 65, 66, 67, 68)  
**Document Version:** 1.0  
**Created:** March 21, 2026  
**Status:** Draft — Pending Review

---

## Table of Contents

1. [Overview](#1-overview)
2. [Phase 1: Immediate Moves (Week 1-2)](#2-phase-1-immediate-moves-week-1-2)
3. [Phase 2: Short-Term Refactors (Month 1-2)](#3-phase-2-short-term-refactors-month-1-2)
4. [Phase 3: Medium-Term Structural Changes (Month 3-6)](#4-phase-3-medium-term-structural-changes-month-3-6)
5. [Consolidated Implementation Plans](#5-consolidated-implementation-plans)
   - 5.1 [Merge Plan (Item 63)](#51-merge-plan-item-63)
   - 5.2 [Split Plan (Item 64)](#52-split-plan-item-64)
   - 5.3 [Move Plan (Item 65)](#53-move-plan-item-65)
   - 5.4 [Rename Plan (Item 66)](#54-rename-plan-item-66)
   - 5.5 [Delete/Deprecate Plan (Item 67)](#55-deletedeprecate-plan-item-67)
6. [Guardrails & Governance (Item 68)](#6-guardrails--governance-item-68)
7. [Dependencies & Critical Path](#7-dependencies--critical-path)
8. [Success Metrics & Exit Criteria](#8-success-metrics--exit-criteria)

---

## 1. Overview

### 1.1 Scope

This implementation plan covers the **short and medium-term execution items** from the Boundary, Right-Sizing, and Code Placement Audit. The deferred long-term item (62) will be addressed in a separate document.

### 1.2 Timeline Summary

| Phase | Duration | Key Deliverables |
|-------|----------|------------------|
| Phase 1 | Week 1-2 | Delete empty modules, merge platform submodules |
| Phase 2 | Month 1-2 | Fix builds, consolidate YAPPC, fix Lombok |
| Phase 3 | Month 3-6 | Structural moves, ownership clarification |

### 1.3 Resource Requirements

| Team | Phase 1 | Phase 2 | Phase 3 |
|------|---------|---------|---------|
| Platform Team | 2 engineers | 4 engineers | 3 engineers |
| YAPPC Team | 2 engineers | 4 engineers | 2 engineers |
| Data-Cloud Team | 1 engineer | 2 engineers | 2 engineers |
| TutorPutor Team | - | 3 engineers | 2 engineers |
| AEP Team | - | - | 2 engineers |

---

## 2. Phase 1: Immediate Moves (Week 1-2)

### 2.1 Delete `domain/yappc/`

**Owner:** Platform Team  
**Effort:** XS (1-2 days)  
**Priority:** P0

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P1-T1 | Audit `domain/yappc/` contents | Platform Lead | 2 hours | - |
| P1-T2 | Verify no active references | Platform Engineer | 4 hours | P1-T1 |
| P1-T3 | Move any reusable code to `products:yappc/core/domain` | Platform Engineer | 4 hours | P1-T2 |
| P1-T4 | Delete directory and update `settings.gradle.kts` | Platform Engineer | 2 hours | P1-T3 |
| P1-T5 | Verify monorepo builds | Platform Engineer | 2 hours | P1-T4 |

#### Exit Criteria
- [ ] `domain/yappc/` directory deleted
- [ ] No build failures
- [ ] No references in code search
- [ ] PR approved and merged

---

### 2.2 Delete `yappc/services:domain`

**Owner:** YAPPC Team  
**Effort:** XS (1-2 days)  
**Priority:** P0

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P1-T6 | Identify active code in service | YAPPC Lead | 4 hours | - |
| P1-T7 | Migrate imports to `platform:java:domain` | YAPPC Engineer | 4 hours | P1-T6 |
| P1-T8 | Update `yappc/services/build.gradle.kts` | YAPPC Engineer | 2 hours | P1-T7 |
| P1-T9 | Delete service module | YAPPC Engineer | 1 hour | P1-T8 |
| P1-T10 | Verify dependent services build | YAPPC Engineer | 2 hours | P1-T9 |

#### Exit Criteria
- [ ] Module deleted
- [ ] All imports migrated to `platform:java:domain`
- [ ] No compilation errors
- [ ] Tests pass

---

### 2.3 Delete `yappc/services:infrastructure`

**Owner:** YAPPC Team  
**Effort:** XS (1-2 days)  
**Priority:** P0

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P1-T11 | Identify active code in service | YAPPC Lead | 4 hours | - |
| P1-T12 | Migrate to `platform:java:*` equivalents | YAPPC Engineer | 4 hours | P1-T11 |
| P1-T13 | Update `yappc/services/build.gradle.kts` | YAPPC Engineer | 2 hours | P1-T12 |
| P1-T14 | Delete service module | YAPPC Engineer | 1 hour | P1-T13 |
| P1-T15 | Verify build | YAPPC Engineer | 2 hours | P1-T14 |

#### Exit Criteria
- [ ] Module deleted
- [ ] All infrastructure code migrated to platform libraries
- [ ] No compilation errors

---

### 2.4 Merge `data-cloud:platform-*` into platform

**Owner:** Data-Cloud Team  
**Effort:** S (3-5 days)  
**Priority:** P1

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P1-T16 | Analyze 4 empty platform submodules | Data-Cloud Lead | 4 hours | - |
| P1-T17 | Verify submodules only re-export main platform | Data-Cloud Engineer | 4 hours | P1-T16 |
| P1-T18 | Update imports in dependent modules | Data-Cloud Engineer | 8 hours | P1-T17 |
| P1-T19 | Delete `platform-entity`, `platform-event`, `platform-config`, `platform-analytics` | Data-Cloud Engineer | 4 hours | P1-T18 |
| P1-T20 | Update `data-cloud/platform/build.gradle` | Data-Cloud Engineer | 2 hours | P1-T19 |
| P1-T21 | Verify data-cloud build | Data-Cloud Engineer | 4 hours | P1-T20 |

#### Exit Criteria
- [ ] 4 empty submodules deleted
- [ ] All imports use `data-cloud:platform` directly
- [ ] Data-Cloud builds successfully
- [ ] No functionality lost

---

### 2.5 Move `apps/yappc-web/` to Product

**Owner:** YAPPC Team  
**Effort:** S (3-5 days)  
**Priority:** P1

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P1-T22 | Analyze `apps/yappc-web/` structure | YAPPC Lead | 4 hours | - |
| P1-T23 | Create `products/yappc/frontend/web/` directory | YAPPC Engineer | 1 hour | P1-T22 |
| P1-T24 | Move all source files | YAPPC Engineer | 8 hours | P1-T23 |
| P1-T25 | Update import paths | YAPPC Engineer | 4 hours | P1-T24 |
| P1-T26 | Update `pnpm-workspace.yaml` | YAPPC Engineer | 2 hours | P1-T25 |
| P1-T27 | Update root `settings.gradle.kts` | YAPPC Engineer | 2 hours | P1-T26 |
| P1-T28 | Delete `apps/yappc-web/` | YAPPC Engineer | 1 hour | P1-T27 |
| P1-T29 | Verify frontend build | YAPPC Engineer | 4 hours | P1-T28 |

#### Exit Criteria
- [ ] Web app moved to `products/yappc/frontend/web/`
- [ ] All imports updated
- [ ] Build passes
- [ ] Original directory deleted

---

## 3. Phase 2: Short-Term Refactors (Month 1-2)

### 3.1 Consolidate YAPPC Scaffold Modules

**Owner:** YAPPC Team  
**Effort:** M (2-3 weeks)  
**Priority:** P0

#### Context

YAPPC has 48+ Gradle modules with excessive granularity. Scaffold modules alone include:
- `yappc:core:scaffold:api:http`
- `yappc:core:scaffold:api:grpc`
- `yappc:core:scaffold:cli`
- `yappc:core:scaffold:adapters`
- `yappc:core:scaffold:core`

These will be consolidated into 2 modules.

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P2-T1 | Document current scaffold module boundaries | YAPPC Architect | 2 days | - |
| P2-T2 | Design target module structure (`scaffold-api`, `scaffold-core`) | YAPPC Architect | 2 days | P2-T1 |
| P2-T3 | Create `yappc:core:scaffold-api` module | YAPPC Engineer | 3 days | P2-T2 |
| P2-T4 | Migrate `api:http` and `api:grpc` to `scaffold-api` | YAPPC Engineer | 4 days | P2-T3 |
| P2-T5 | Create `yappc:core:scaffold-core` module | YAPPC Engineer | 3 days | P2-T4 |
| P2-T6 | Migrate `cli`, `adapters`, `core` to `scaffold-core` | YAPPC Engineer | 5 days | P2-T5 |
| P2-T7 | Update all dependent imports | YAPPC Engineer | 3 days | P2-T6 |
| P2-T8 | Delete old modules | YAPPC Engineer | 2 days | P2-T7 |
| P2-T9 | Update `settings.gradle.kts` | YAPPC Engineer | 1 day | P2-T8 |
| P2-T10 | Full regression test | QA Team | 3 days | P2-T9 |

#### Exit Criteria
- [ ] 5 scaffold modules → 2 modules
- [ ] All builds green
- [ ] No functionality lost
- [ ] Tests pass

---

### 3.2 Merge Platform Agent Modules

**Owner:** Platform Team + AEP Teams  
**Effort:** M (2-3 weeks)  
**Priority:** P1

#### Context

Platform agent modules need consolidation:
- `platform:java:agent-api` → `agent-core`
- `platform:java:agent-spi` → `agent-core`
- `platform:java:agent-framework` → `agent-core` (later move to AEP)

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P2-T11 | Analyze agent module dependencies | Platform Architect | 2 days | - |
| P2-T12 | Create `platform:java:agent-core` | Platform Engineer | 3 days | P2-T11 |
| P2-T13 | Migrate `agent-api` to `agent-core` | Platform Engineer | 3 days | P2-T12 |
| P2-T14 | Migrate `agent-spi` to `agent-core` | Platform Engineer | 3 days | P2-T13 |
| P2-T15 | Update all dependent modules | Platform Engineer | 4 days | P2-T14 |
| P2-T16 | Delete old modules | Platform Engineer | 2 days | P2-T15 |
| P2-T17 | Verify build | Platform Engineer | 2 days | P2-T16 |

#### Exit Criteria
- [ ] 3 modules → 1 module
- [ ] Clear agent API boundary established
- [ ] Builds pass

---

### 3.3 Fix Data-Cloud Lombok Builders

**Owner:** Data-Cloud Team  
**Effort:** M (2-3 weeks)  
**Priority:** P0

#### Context

Lombok annotation processor not generating builder classes. ~50+ files affected.

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P2-T18 | Diagnose Lombok processor issue | Data-Cloud Lead | 3 days | - |
| P2-T19 | Compare with working modules (common-utils) | Data-Cloud Engineer | 2 days | P2-T18 |
| P2-T20 | Fix build configuration | Data-Cloud Engineer | 5 days | P2-T19 |
| P2-T21 | Verify `FieldDefinitionBuilder` generates | Data-Cloud Engineer | 1 day | P2-T20 |
| P2-T22 | Restore disabled modules from `disabled/` folder | Data-Cloud Engineer | 3 days | P2-T21 |
| P2-T23 | Fix any remaining compilation errors | Data-Cloud Engineer | 5 days | P2-T22 |
| P2-T24 | Full test suite verification | QA Team | 3 days | P2-T23 |

#### Exit Criteria
- [ ] Lombok builders generate correctly
- [ ] All disabled modules restored
- [ ] 100% build success
- [ ] Tests pass

---

### 3.4 Fix TutorPutor TypeScript Builds

**Owner:** TutorPutor Team  
**Effort:** L (4-6 weeks)  
**Priority:** P0

#### Context

16/18 TypeScript modules failing build. Critical blocker.

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P2-T25 | Document all 16 failing modules | TutorPutor Lead | 2 days | - |
| P2-T26 | Categorize failures (deps, config, types) | TutorPutor Engineer | 2 days | P2-T25 |
| P2-T27 | Fix dependency resolution issues | TutorPutor Engineer | 5 days | P2-T26 |
| P2-T28 | Fix TypeScript configuration | TutorPutor Engineer | 3 days | P2-T27 |
| P2-T29 | Fix type errors module by module | TutorPutor Engineer | 10 days | P2-T28 |
| P2-T30 | Fix import path issues | TutorPutor Engineer | 5 days | P2-T29 |
| P2-T31 | Verify all 18 modules build | TutorPutor Engineer | 2 days | P2-T30 |
| P2-T32 | Run full test suite | QA Team | 3 days | P2-T31 |

#### Exit Criteria
- [ ] 18/18 TypeScript modules build successfully
- [ ] All tests pass
- [ ] CI/CD green

---

### 3.5 Flatten YAPPC Core/Domain

**Owner:** YAPPC Team  
**Effort:** S (1-2 weeks)  
**Priority:** P1

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P2-T33 | Analyze `yappc/core` structure | YAPPC Lead | 2 days | - |
| P2-T34 | Merge `core:domain` into `core` | YAPPC Engineer | 3 days | P2-T33 |
| P2-T35 | Update imports | YAPPC Engineer | 3 days | P2-T34 |
| P2-T36 | Delete flattened submodules | YAPPC Engineer | 2 days | P2-T35 |
| P2-T37 | Verify build | YAPPC Engineer | 2 days | P2-T36 |

#### Exit Criteria
- [ ] Flatter hierarchy achieved
- [ ] No lost code
- [ ] Builds pass

---

## 4. Phase 3: Medium-Term Structural Changes (Month 3-6)

### 4.1 Split AEP Platform-Core

**Owner:** AEP Team  
**Effort:** M (3-4 weeks)  
**Priority:** P1

#### Context

`aep:platform-core` is a god module. Split into:
- `engine`
- `registry`
- `connectors`
- `security`

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P3-T1 | Analyze `platform-core` boundaries | AEP Architect | 3 days | - |
| P3-T2 | Design 4-module split | AEP Architect | 2 days | P3-T1 |
| P3-T3 | Create `aep:engine` module | AEP Engineer | 4 days | P3-T2 |
| P3-T4 | Create `aep:registry` module | AEP Engineer | 4 days | P3-T3 |
| P3-T5 | Create `aep:connectors` module | AEP Engineer | 4 days | P3-T4 |
| P3-T6 | Create `aep:security` module | AEP Engineer | 4 days | P3-T5 |
| P3-T7 | Migrate code to new modules | AEP Engineer | 10 days | P3-T6 |
| P3-T8 | Update imports across AEP | AEP Engineer | 5 days | P3-T7 |
| P3-T9 | Delete old `platform-core` | AEP Engineer | 2 days | P3-T8 |
| P3-T10 | Verify build and tests | AEP Engineer | 3 days | P3-T9 |

#### Exit Criteria
- [ ] 1 module → 4 focused modules
- [ ] Clear responsibilities per module
- [ ] All tests pass

---

### 4.2 Consolidate TutorPutor Services

**Owner:** TutorPutor Team  
**Effort:** L (4-6 weeks)  
**Priority:** P1

#### Context

Consolidate many services into 3:
- `platform`
- `ai`
- `content`

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P3-T11 | Document current service structure | TutorPutor Lead | 3 days | P2-T32 |
| P3-T12 | Design 3-service consolidation | TutorPutor Architect | 3 days | P3-T11 |
| P3-T13 | Create `tutorputor:services:platform` | TutorPutor Engineer | 5 days | P3-T12 |
| P3-T14 | Create `tutorputor:services:ai` | TutorPutor Engineer | 5 days | P3-T13 |
| P3-T15 | Create `tutorputor:services:content` | TutorPutor Engineer | 5 days | P3-T14 |
| P3-T16 | Consolidate existing services | TutorPutor Engineer | 15 days | P3-T15 |
| P3-T17 | Update all imports | TutorPutor Engineer | 5 days | P3-T16 |
| P3-T18 | Delete old service modules | TutorPutor Engineer | 3 days | P3-T17 |
| P3-T19 | Full test verification | QA Team | 5 days | P3-T18 |

#### Exit Criteria
- [ ] Services consolidated to 3
- [ ] Clear boundaries per service
- [ ] All tests pass

---

### 4.3 Move Agent-Framework to AEP

**Owner:** Platform Team + AEP Teams  
**Effort:** M (3-4 weeks)  
**Priority:** P1

#### Context

`platform:java:agent-framework` belongs to AEP, not Platform.

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P3-T20 | Analyze agent-framework dependencies | Platform Architect | 2 days | P2-T17 |
| P3-T21 | Create `products:aep:agent-framework` | AEP Engineer | 3 days | P3-T20 |
| P3-T22 | Migrate code from platform | AEP Engineer | 5 days | P3-T21 |
| P3-T23 | Update all platform imports | Platform Engineer | 3 days | P3-T22 |
| P3-T24 | Update all AEP imports | AEP Engineer | 3 days | P3-T23 |
| P3-T25 | Delete from platform | Platform Engineer | 2 days | P3-T24 |
| P3-T26 | Verify builds | Both Teams | 3 days | P3-T25 |

#### Exit Criteria
- [ ] Module moved to correct ownership
- [ ] All imports updated
- [ ] Builds pass

---

### 4.4 ~~Move Canvas to TutorPutor~~ — CANCELLED (Keep in Platform)

**Status:** ❌ CANCELLED — Audit finding was incorrect  
**Original Owner:** Platform Team + TutorPutor Teams  
**Original Effort:** M (3-4 weeks)

#### Context — CORRECTED

The original audit claimed `platform:typescript/capabilities/canvas-core` is "used only by TutorPutor."

**Investigation (2026-03-21) proved this is FACTUALLY INCORRECT:**

| Product | Uses @ghatana/canvas? | Uses @ghatana/flow-canvas? | Evidence |
|---------|----------------------|---------------------------|----------|
| **YAPPC** | ✅ YES (4 packages) | ✅ YES | Primary consumer — 80+ export paths |
| **Data-Cloud** | ✅ YES (1 package) | ✅ YES (13+ source files) | Workflow nodes, event topology, lineage |
| **TutorPutor** | ❌ **NO** | ❌ **NO** | Zero imports, zero dependencies |
| **AEP** | ❌ NO | ❌ NO | Uses @xyflow/react directly |

**Decision:** Canvas is a legitimate shared platform library used by 2+ products.
Moving it to any single product would break other products and create backward dependencies.

#### Replacement Action: Formalize Canvas as Platform-Owned Shared Library

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P3-T27 | Fix `pnpm-workspace.yaml` canvas registration | Platform Engineer | Done | - |
| P3-T28 | Add canvas to shared-library-registry | Platform Architect | 1 day | G-T5 |
| P3-T29 | Document canvas ownership (Platform) + consumers (YAPPC, Data-Cloud) | Platform Lead | 1 day | P3-T28 |

#### Exit Criteria
- [x] Canvas workspace registration corrected
- [ ] Canvas documented in shared-library-registry with correct ownership
- [ ] No move performed — canvas remains in `platform/typescript/capabilities/canvas-core/`

---

### 4.5 Deprecate App-Platform

**Owner:** App-Platform Team + YAPPC Team  
**Effort:** L (6-8 weeks)  
**Priority:** P2

#### Context

`app-platform/` is being replaced by `yappc/`. 6-month deprecation timeline.

#### Tasks

| ID | Task | Assignee | Duration | Dependencies |
|----|------|----------|----------|--------------|
| P3-T34 | Document app-platform usages | App-Platform Lead | 5 days | - |
| P3-T35 | Create migration guide to YAPPC | App-Platform Architect | 5 days | P3-T34 |
| P3-T36 | Mark as deprecated in documentation | App-Platform Lead | 2 days | P3-T35 |
| P3-T37 | Add deprecation warnings | App-Platform Engineer | 3 days | P3-T36 |
| P3-T38 | Migrate critical consumers to YAPPC | App-Platform Engineer | 20 days | P3-T37 |
| P3-T39 | Monitor usage metrics | App-Platform Lead | Ongoing | P3-T38 |
| P3-T40 | Final deletion (Month 6) | App-Platform Team | 5 days | - |

#### Exit Criteria
- [ ] Deprecation announced
- [ ] Migration guide published
- [ ] Consumers migrated
- [ ] Module deleted (Month 6)

---

## 5. Consolidated Implementation Plans

### 5.1 Merge Plan (Item 63)

| Merge | Source | Target | Timeline | Owner | Effort |
|-------|--------|--------|----------|-------|--------|
| 1 | `yappc:core:scaffold:api:http`, `api/grpc` | `scaffold-api` | Week 2 | YAPPC Team | S |
| 2 | `yappc:core:scaffold:cli`, `adapters`, `core` | `scaffold-core` | Week 3 | YAPPC Team | M |
| 3 | `platform:java:agent-api`, `agent-spi`, `agent-framework` | `agent-core` | Week 4 | Platform + AEP | M |
| 4 | `data-cloud:platform-entity`, `platform-event`, `platform-config`, `platform-analytics` | `platform` | Week 1 | Data-Cloud Team | S |
| 5 | `platform:java:agent-memory`, `agent-learning` | `agent-runtime` | Week 6 | Platform Team | M |
| 6 | `platform:java:ai-integration:registry`, `observability`, `feature-store` | `ai-integration` | Week 6 | Data-Cloud Team | S |

#### Detailed Merge Steps (Template)

For each merge:

1. **Pre-Merge** (Day 1-2)
   - Analyze source module dependencies
   - Create target module structure
   - Design new public API

2. **Migration** (Day 3-8)
   - Copy source code to target
   - Merge overlapping code
   - Update internal references

3. **Consumer Updates** (Day 9-12)
   - Update all imports
   - Fix build configurations
   - Run compilation checks

4. **Cleanup** (Day 13-15)
   - Delete source modules
   - Update settings.gradle.kts
   - Verify builds
   - Run tests

---

### 5.2 Split Plan (Item 64)

| Split | Source | Target Modules | Timeline | Owner | Effort |
|-------|--------|---------------|----------|-------|--------|
| 1 | `aep:platform-core` | `engine`, `registry`, `connectors`, `security` | Month 2-3 | AEP Team | M |
| 2 | `yappc:core:scaffold` | `scaffold-runtime`, `scaffold-cli` | Month 2 | YAPPC Team | M |
| 3 | `tutorputor:services` | `platform`, `ai`, `content` | Month 3-4 | TutorPutor Team | L |

#### Detailed Split Steps (Template)

For each split:

1. **Analysis** (Week 1)
   - Map current code to target modules
   - Identify shared components
   - Design module boundaries

2. **Creation** (Week 2)
   - Create target modules
   - Set up build configurations
   - Define public APIs

3. **Migration** (Week 3-4)
   - Move code to appropriate modules
   - Extract shared components
   - Update internal references

4. **Integration** (Week 5)
   - Update all consumers
   - Fix import statements
   - Update settings.gradle.kts

5. **Cleanup** (Week 6)
   - Delete source module
   - Verify builds
   - Run full test suite

---

### 5.3 Move Plan (Item 65)

| Move | Source | Target | Timeline | Owner | Effort |
|------|--------|--------|----------|-------|--------|
| 1 | `platform:java:agent-*` | `products:aep:agent-*` | Month 2-3 | Platform + AEP | M |
| 2 | ~~`platform:typescript/capabilities:canvas-core`~~ | ~~`products:tutorputor:libs:canvas`~~ | ❌ CANCELLED | — | — |
| 3 | `apps/yappc-web/` | `products:yappc/frontend/web` | Week 2 | YAPPC Team | S |
| 4 | `domain/yappc/` | Delete (merge with product) | Week 1 | Platform Team | XS |
| 5 | `platform:java:ai-integration` | Near `data-cloud` (ownership) | Month 3 | Platform + Data-Cloud | S |

#### Detailed Move Steps (Template)

For each move:

1. **Pre-Move** (Day 1-2)
   - Analyze all dependencies
   - Identify consumers
   - Plan import path changes

2. **Creation** (Day 3)
   - Create target directory structure
   - Set up build configuration

3. **Migration** (Day 4-6)
   - Move source files
   - Update package declarations
   - Update build files

4. **Consumer Updates** (Day 7-10)
   - Update all import references
   - Update workspace configurations
   - Verify dependent builds

5. **Cleanup** (Day 11-12)
   - Delete source location
   - Update documentation
   - Final verification

---

### 5.4 Rename Plan (Item 66)

| Rename | From | To | Timeline | Owner | Effort |
|--------|------|-----|----------|-------|--------|
| 1 | `yappc` (docs) | `product-creator` | Month 3 | YAPPC Team | S |
| 2 | `dcmaar` (docs) | `ai-guardian` | Month 3 | DCMAAR Team | S |
| 3 | `kernel` | `core` | Month 4 | Platform Team | S |
| 4 | `app-platform` | `legacy-app-platform` | Week 1 | App-Platform Team | XS |

#### Detailed Rename Steps

1. **Pre-Rename**
   - Search for all references
   - Identify breaking changes
   - Plan migration strategy

2. **Directory Rename** (Week 1)
   - Rename directories
   - Update build files
   - Update workspace configs

3. **Code References** (Week 2)
   - Update imports
   - Update documentation
   - Update CI/CD configs

4. **Verification**
   - Full search for old names
   - Build verification
   - Test execution

---

### 5.5 Delete/Deprecate Plan (Item 67)

| Action | Item | Timeline | Replacement | Owner |
|--------|------|----------|-------------|-------|
| Delete | `domain/yappc/` | Week 1 | `products:yappc/core/domain` | Platform Team |
| Delete | `yappc/services:domain` | Week 1 | `platform:java:domain` | YAPPC Team |
| Delete | `yappc/services:infrastructure` | Week 1 | `platform:java:*` | YAPPC Team |
| Merge | `data-cloud:platform-*` | Week 1 | `data-cloud:platform` | Data-Cloud Team |
| Deprecate | `app-platform/` | 6 months | `yappc/` | App-Platform Team |
| Delete | `platform/java/kernel/modules/*` | Month 3 | `platform:java:*` | Platform Team |

#### Deletion Checklist

For each deletion:

- [ ] Verify no active consumers
- [ ] Move any reusable code
- [ ] Update all references
- [ ] Remove from settings.gradle.kts
- [ ] Remove from pnpm-workspace.yaml (if applicable)
- [ ] Update documentation
- [ ] Verify build after deletion
- [ ] Archive if needed for compliance

---

## 6. Guardrails & Governance (Item 68)

### 6.1 Module Admission Checklist

All new modules require approval through this checklist:

| Check | Requirement | Verification Method |
|-------|-------------|---------------------|
| Ownership | Clear owner declared | Owner documented in README |
| Consumers | Consumer list (if shared) | Listed in module doc |
| Classification | Boundary type defined | Local / Owned-Shared / Global |
| Build | Module builds in isolation | CI build verification |
| Tests | Tests run without siblings | CI test verification |
| Dependencies | Dependencies justified | PR review approval |

**Implementation:**

| ID | Task | Assignee | Duration | Timeline |
|----|------|----------|----------|----------|
| G-T1 | Create module admission template | Platform Architect | 2 days | Week 1 |
| G-T2 | Add CI gate for new modules | Platform Engineer | 3 days | Week 2 |
| G-T3 | Document process in CONTRIBUTING.md | Platform Lead | 1 day | Week 2 |
| G-T4 | Train team leads on process | Platform Architect | 1 day | Week 3 |

### 6.2 Shared Library Governance

| Type | Approval Required | Max Count |
|------|-------------------|-----------|
| Product-Owned Shared | Product Team Lead | Unlimited |
| Global Shared | Platform Team + Architecture Board | 20 (currently ~30) |

**Governance Steps:**

1. Product submits request with justification
2. Architecture Board reviews (weekly meeting)
3. Decision documented in shared-library-registry.md
4. Added to dependency graph

**Implementation:**

| ID | Task | Assignee | Duration | Timeline |
|----|------|----------|----------|----------|
| G-T5 | Create shared-library-registry.md | Platform Architect | 2 days | Week 1 |
| G-T6 | List current global shared libraries | Platform Engineer | 3 days | Week 1 |
| G-T7 | Identify 10+ libraries to consolidate | Platform Architect | 2 days | Week 2 |
| G-T8 | Create consolidation plan | Platform Team | 1 week | Week 3 |

### 6.3 Cross-Product Dependency Review

| Requirement | Implementation | Owner |
|-------------|----------------|-------|
| Explicit approval | PR requires Platform Team sign-off | Platform Team |
| Dependency graph | Auto-generated weekly | Platform Engineer |
| Circular dependency blocking | CI gate using Gradle | Platform Engineer |

**Implementation:**

| ID | Task | Assignee | Duration | Timeline |
|----|------|----------|----------|----------|
| G-T9 | Set up dependency graph generation | Platform Engineer | 3 days | Week 2 |
| G-T10 | Create CI gate for cross-product deps | Platform Engineer | 3 days | Week 3 |
| G-T11 | Configure circular dependency detection | Platform Engineer | 2 days | Week 3 |
| G-T12 | Document process | Platform Lead | 1 day | Week 4 |

### 6.4 Naming Conventions

| Rule | Enforcement | Status |
|------|-------------|--------|
| No acronyms in new product names | PR review check | New only |
| No metaphors (kernel, scaffold) | PR review check | Applies to new code |
| Explicit, descriptive naming | Code review requirement | All code |

**Implementation:**

| ID | Task | Assignee | Duration | Timeline |
|----|------|----------|----------|----------|
| G-T13 | Document naming conventions | Platform Lead | 1 day | Week 1 |
| G-T14 | Add linting rules for naming | Platform Engineer | 2 days | Week 2 |
| G-T15 | Update code review checklist | Platform Lead | 1 day | Week 2 |

### 6.5 Regular Boundary Audits

| Audit Type | Frequency | Owner | Scope |
|------------|-----------|-------|-------|
| Health check | Quarterly | Platform Team | New modules, dependencies |
| Comprehensive | Annual | Principal Engineering | Full audit |
| Automated detection | Continuous | CI/CD | Boundary violations |

**Implementation:**

| ID | Task | Assignee | Duration | Timeline |
|----|------|----------|----------|----------|
| G-T16 | Set up automated boundary violation detection | Platform Engineer | 5 days | Month 2 |
| G-T17 | Create quarterly audit checklist | Platform Architect | 2 days | Week 4 |
| G-T18 | Schedule first quarterly audit | Platform Lead | 1 day | Week 4 |

---

## 7. Dependencies & Critical Path

### 7.1 Task Dependencies Graph

```
P1-T1 (Delete domain/yappc)
  └── P1-T4 (Delete yappc/services:domain)
        └── P2-T1 (Consolidate YAPPC scaffold)
              └── P3-T12 (Split AEP platform-core)

P2-T18 (Fix Lombok)
  └── P2-T22 (Restore disabled modules)
        └── P4 (All Data-Cloud structural changes)

P2-T25 (Fix TutorPutor builds)
  └── P3-T11 (Consolidate TutorPutor services)
        └── P3-T28 (Move canvas)
```

### 7.2 Critical Path

The critical path for achieving "GO" status:

1. **Week 1-2:** Delete empty modules (P1) → Unblocks YAPPC consolidation
2. **Month 1-2:** Fix Data-Cloud Lombok (P2-T18) → Unblocks Data-Cloud work
3. **Month 1-2:** Fix TutorPutor builds (P2-T25) → Unblocks TutorPutor consolidation
4. **Month 2:** Consolidate YAPPC (P2-T1) → Major milestone
5. **Month 3-4:** Move misplaced code (P4) → Ownership clarity
6. **Month 6:** All structural changes complete

### 7.3 Parallel Work Streams

| Stream 1: Platform | Stream 2: YAPPC | Stream 3: Data-Cloud | Stream 4: TutorPutor |
|-------------------|-----------------|---------------------|---------------------|
| Delete domain/yappc | Delete service stubs | Merge platform submodules | Wait for Stream 2 |
| Merge agent modules | Consolidate scaffold | Fix Lombok | Fix TypeScript builds |
| Move agent to AEP | Flatten core/domain | - | Consolidate services |
| Move canvas | - | - | - |

---

## 8. Success Metrics & Exit Criteria

### 8.1 Phase Exit Criteria

| Phase | Exit Criteria | Target Date |
|-------|---------------|-------------|
| Phase 1 | 5 empty modules deleted, builds green | End of Week 2 |
| Phase 2 | TutorPutor builds, Lombok fixed, YAPPC consolidated | End of Month 2 |
| Phase 3 | All structural moves complete, ownership clear | End of Month 6 |

### 8.2 Success Metrics

| Metric | Baseline | Target | Measurement |
|--------|----------|--------|-------------|
| Build success rate | ~70% | 100% | CI dashboard |
| Modules per product | YAPPC: 48+ | YAPPC: <20 | settings.gradle.kts |
| Global shared libraries | ~30 | <20 | shared-library-registry.md |
| Circular dependencies | 1+ | 0 | Gradle dependency report |
| TypeScript build failures | 16/18 | 0/18 | pnpm build output |
| Code with clear ownership | ~60% | 100% | Ownership registry |

### 8.3 "GO" Criteria

For the monorepo to achieve "GO" status:

1. ✅ All TypeScript modules build successfully (100%)
2. ✅ Data-Cloud modules re-enabled with working builds
3. ✅ YAPPC consolidated to <20 modules
4. ✅ All circular dependencies resolved
5. ✅ Clear ownership established for all shared code
6. ✅ Guardrails in place and enforced
7. ✅ All tests passing
8. ✅ Documentation updated

---

## Appendix A: Risk Register

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Lombok fix takes longer than expected | Medium | High | Manual builder creation as fallback |
| TutorPutor build fixes uncover deeper issues | Medium | High | Parallel track to rebuild from clean state |
| Consumer resistance to module moves | Low | Medium | Clear migration guides, gradual deprecation |
| Circular dependencies resist resolution | Medium | High | Interface extraction, adapter pattern |
| CI/CD performance degradation | Low | Medium | Incremental builds, caching optimization |

## Appendix B: Communication Plan

| Audience | Message | Timing |
|----------|---------|--------|
| All Engineers | Phase 1 kickoff, immediate changes | Week 1 |
| Team Leads | Detailed plans, resource needs | Week 1-2 |
| Executives | Monthly progress reports | Monthly |
| All Hands | Phase completions, milestones | End of each Phase |

---

**Document Owner:** Principal Engineering Team  
**Last Updated:** March 21, 2026  
**Next Review:** April 4, 2026 (bi-weekly)
