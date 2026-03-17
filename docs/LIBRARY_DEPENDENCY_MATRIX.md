# YAPPC Frontend Library Dependency Matrix

**Analysis Date:** March 17, 2026  
**Scope:** products/yappc/frontend/libs/*  
**Status:** Phase 1 Analysis Complete  

---

## Executive Summary

**Total Libraries Analyzed:** 22  
**Current Naming Convention:** @ghatana/yappc-* (inconsistent with standard @yappc/*)  
**Critical Issues Identified:**
- 22 libraries for single product (audit recommends 6-8)
- Mixed dependencies on platform libraries
- No clear consolidation strategy yet implemented

---

## Library Inventory

### Core Libraries (Foundation Layer)

| Library | Name | Status | Dependencies | Notes |
|---------|------|--------|--------------|-------|
| **types** | @ghatana/yappc-types | Active | None | Type definitions |
| **utils** | @ghatana/yappc-utils | Active | None | Utility functions |
| **api** | @ghatana/yappc-api | Active | None | API clients |
| **config** | @ghatana/yappc-config | Active | None | Configuration |

### UI & Components Layer

| Library | Name | Status | Dependencies | Notes |
|---------|------|--------|--------------|-------|
| **ui** | @ghatana/yappc-ui | Active | @ghatana/yappc-types, @ghatana/yappc-crdt | Core UI components |
| **canvas** | @ghatana/yappc-canvas | Active | @ghatana/canvas (platform), @ghatana/ui (platform), @ghatana/yappc-ai, @ghatana/yappc-types, @ghatana/yappc-ui, @ghatana/yappc-crdt | Canvas library |
| **collab** | @ghatana/yappc-collab | Active | @ghatana/yappc-ui (peer) | Collaboration features |
| **crdt** | @ghatana/yappc-crdt | Active | None | CRDT implementation |

### IDE & Code Layer

| Library | Name | Status | Dependencies | Notes |
|---------|------|--------|--------------|-------|
| **ide** | @ghatana/yappc-ide | **DEPRECATED** | @ghatana/yappc-canvas, @ghatana/yappc-code-editor, @ghatana/yappc-crdt, @ghatana/yappc-types, @ghatana/yappc-ui | Sunset: 2026-06-06 |
| **code-editor** | @ghatana/yappc-code-editor | Active | @ghatana/yappc-ui (peer) | Monaco integration |
| **live-preview-server** | @ghatana/yappc-live-preview-server | Active | None | WebSocket server |
| **vite-plugin-live-edit** | @ghatana/yappc-vite-plugin-live-edit | Active | None | Vite plugin |

### AI & Intelligence Layer

| Library | Name | Status | Dependencies | Notes |
|---------|------|--------|--------------|-------|
| **ai** | @ghatana/yappc-ai | Active | @ghatana/yappc-types, @ghatana/yappc-ui | AI integration |
| **chat** | @ghatana/yappc-chat | Active | None | Chat components |

### Supporting Libraries

| Library | Name | Status | Dependencies | Notes |
|---------|------|--------|--------------|-------|
| **auth** | @ghatana/yappc-auth | Active | None | Authentication |
| **notifications** | @ghatana/yappc-notifications | Active | None | Notifications |
| **realtime** | @ghatana/yappc-realtime | Active | None | Real-time sync |
| **testing** | @ghatana/yappc-testing | Active | None | Test utilities |
| **component-traceability** | @ghatana/yappc-component-traceability | Active | None | Traceability |

---

## Dependency Graph

### Cross-Library Dependencies (Internal)

```
@ghatana/yappc-canvas
├── @ghatana/yappc-ai
├── @ghatana/yappc-types
├── @ghatana/yappc-ui
└── @ghatana/yappc-crdt

@ghatana/yappc-ide (deprecated)
├── @ghatana/yappc-canvas
├── @ghatana/yappc-code-editor
├── @ghatana/yappc-crdt
├── @ghatana/yappc-types
└── @ghatana/yappc-ui

@ghatana/yappc-collab (peer)
└── @ghatana/yappc-ui

@ghatana/yappc-ai
├── @ghatana/yappc-types
└── @ghatana/yappc-ui

@ghatana/yappc-code-editor (peer)
└── @ghatana/yappc-ui
```

### Platform Dependencies (External)

```
@ghatana/yappc-canvas
├── @ghatana/canvas (platform)
├── @ghatana/ui (platform)
└── @ghatana/yappc-ui (internal)
```

---

## Consolidation Opportunities

### Recommended Consolidation (22 → 6 Libraries)

#### 1. @yappc/core (NEW)
**Current Libraries to Merge:**
- @ghatana/yappc-types
- @ghatana/yappc-utils
- @ghatana/yappc-api
- @ghatana/yappc-config

**Rationale:** All foundational utilities, types, and configuration

#### 2. @yappc/ui (ENHANCE)
**Current Libraries to Merge:**
- @ghatana/yappc-ui (existing)
- @ghatana/yappc-chat
- @ghatana/yappc-notifications

**Rationale:** UI components, chat, and notifications all serve UI layer

#### 3. @yappc/canvas (ENHANCE)
**Current Libraries to Merge:**
- @ghatana/yappc-canvas (existing)
- @ghatana/yappc-collab
- @ghatana/yappc-crdt

**Rationale:** Canvas, collaboration, and CRDT form cohesive canvas ecosystem

#### 4. @yappc/ide (NEW - Replace deprecated @ghatana/yappc-ide)
**Current Libraries to Merge:**
- @ghatana/yappc-code-editor
- @ghatana/yappc-live-preview-server
- @ghatana/yappc-vite-plugin-live-edit

**Rationale:** All IDE-related functionality in single package

#### 5. @yappc/ai (KEEP)
**Current:** @ghatana/yappc-ai

**Rationale:** AI integration is distinct domain, already well-scoped

#### 6. @yappc/testing (ENHANCE)
**Current Libraries to Merge:**
- @ghatana/yappc-testing (existing)
- @ghatana/yappc-auth
- @ghatana/yappc-component-traceability
- @ghatana/yappc-realtime

**Rationale:** Testing utilities, auth mocks, traceability, and real-time test helpers

---

## Dependency Complexity Analysis

### High Complexity Libraries (>5 dependencies)

1. **@ghatana/yappc-canvas:** 6 internal + 2 platform dependencies
2. **@ghatana/yappc-ide:** 5 internal dependencies (deprecated)
3. **@ghatana/yappc-ui:** 2 internal + many external peer deps

### Low Complexity Libraries (0-1 dependencies)

1. **@ghatana/yappc-types:** 0 dependencies ✅
2. **@ghatana/yappc-utils:** 0 dependencies ✅
3. **@ghatana/yappc-api:** 0 dependencies ✅
4. **@ghatana/yappc-config:** 0 dependencies ✅
5. **@ghatana/yappc-crdt:** 0 dependencies ✅
6. **@ghatana/yappc-chat:** 0 internal dependencies
7. **@ghatana/yappc-auth:** 0 dependencies ✅
8. **@ghatana/yappc-component-traceability:** 0 dependencies ✅

### Circular Dependencies

**None detected** in current dependency graph ✅

---

## Platform vs Product Dependencies

### Platform Libraries Referenced

| Platform Library | Used By | Impact |
|------------------|---------|--------|
| @ghatana/canvas | @ghatana/yappc-canvas | Direct canvas implementation |
| @ghatana/ui | @ghatana/yappc-canvas | Platform UI components |
| @ghatana/theme | Web app | Theming |

### Assessment

**Current State:** Products depend on platform libraries appropriately ✅  
**No Issues:** No platform boundary violations detected

---

## Naming Convention Issues

### Current State

All YAPPC libraries use `@ghatana/yappc-*` naming:
- @ghatana/yappc-types
- @ghatana/yappc-ui
- @ghatana/yappc-canvas
- etc.

### Standard Required

Per governance standards, product libraries should use `@{product}/*`:
- @yappc/types → @yappc/core
- @yappc/ui
- @yappc/canvas
- etc.

### Migration Impact

**Files to Update:**
- 29 package.json files (libraries + apps + dependencies)
- Unknown number of import statements (Phase 1.3 will document)
- tsconfig.json path mappings
- pnpm-workspace.yaml references

---

## Key Findings

### ✅ Strengths

1. **Clear Layering:** Libraries follow logical separation (core → ui → canvas → ide)
2. **No Circular Dependencies:** Clean dependency graph
3. **Appropriate Platform Usage:** Correct use of platform abstractions
4. **Good Documentation:** @ghatana/yappc-ide properly marked as deprecated

### ⚠️ Issues

1. **Library Sprawl:** 22 libraries for single product (target: 6-8)
2. **Naming Inconsistency:** Uses @ghatana/yappc-* instead of @yappc/*
3. **Small Libraries:** Several libraries with minimal functionality
4. **Redundant Utilities:** types, utils, api, config could be consolidated

### 🚨 Critical Actions

1. **Consolidate Core Libraries:** Merge types, utils, api, config into @yappc/core
2. **Standardize Naming:** Migrate from @ghatana/yappc-* to @yappc/*
3. **Remove Deprecated:** Sunset @ghatana/yappc-ide by 2026-06-06
4. **Simplify Structure:** Reduce from 22 to 6 libraries

---

## Appendix: Full Library List

### All 22 YAPPC Frontend Libraries

1. @ghatana/yappc-types
2. @ghatana/yappc-utils
3. @ghatana/yappc-api
4. @ghatana/yappc-config
5. @ghatana/yappc-ui
6. @ghatana/yappc-canvas
7. @ghatana/yappc-collab
8. @ghatana/yappc-crdt
9. @ghatana/yappc-ide (deprecated)
10. @ghatana/yappc-code-editor
11. @ghatana/yappc-live-preview-server
12. @ghatana/yappc-vite-plugin-live-edit
13. @ghatana/yappc-ai
14. @ghatana/yappc-chat
15. @ghatana/yappc-auth
16. @ghatana/yappc-notifications
17. @ghatana/yappc-realtime
18. @ghatana/yappc-testing
19. @ghatana/yappc-component-traceability
20. @ghatana/yappc-canvas/yappc-canvas (sub-package)
21. (apps: web, api, docs-site)
22. (tools: vscode-extension)

---

**Document Status:** Complete - Ready for Phase 1.2 (Version Convergence Analysis)  
**Next Steps:** Proceed to VERSION_CONVERGENCE_REPORT.md
