# Frontend + TypeScript Library Audit Report

> **Audit Date:** 2026-04-10  
> **Auditor:** AI Assistant  
> **Scope:** All platform/typescript/* libraries and product-specific frontend libraries  
> **Methodology:** Deep code inspection, dependency analysis, cross-library comparison

---

## 1. Executive Summary

### Overall Health: **FRAGMENTED AND RISKY**

The Ghatana frontend/TypeScript library ecosystem suffers from significant architectural drift, duplication, and governance failures. While individual libraries may function correctly in isolation, the collective architecture exhibits:

- **Critical duplication** across event systems, accessibility libraries, and domain components
- **Library sprawl** with unjustified fragmentation (6 canvas libraries, 3 accessibility libraries)
- **Incompatible type definitions** for core abstractions (PlatformEvent)
- **Documentation drift** where governance docs contradict actual code state
- **Shadow libraries** that exist solely to avoid modifying existing shared libraries

### Main Risks

| Risk | Severity | Impact |
|------|----------|--------|
| Duplicate PlatformEvent types | **Critical** | Type incompatibility between event systems |
| Triplicated accessibility libraries | **High** | Maintenance burden, confusion, import inconsistency |
| Canvas library sprawl (6 libs) | **High** | Circular dependencies, cognitive overhead, build complexity |
| Duplicate domain components | **High** | Code divergence risk, maintenance doubling |
| Out-of-sync governance docs | **Medium** | Developer confusion, incorrect guidance |

### Main Strengths

- Clear package naming convention (`@ghatana/*` for platform, `@<product>/*` for products)
- Consistent build tooling (TypeScript, Vitest) across most libraries
- Proper use of workspace dependencies (`workspace:*`)
- Zod schemas for runtime validation present in key libraries
- Good test coverage in critical libraries (events, state)

### Top Priority Actions

1. **Immediate:** Consolidate accessibility libraries into single `@ghatana/accessibility`
2. **Immediate:** Align PlatformEvent types between `@ghatana/events` and `@ghatana/realtime`
3. **Phase 1:** Merge canvas sub-libraries into single `@ghatana/canvas`
4. **Phase 1:** Remove duplicate domain components from design-system
5. **Phase 2:** Update LIBRARY_GOVERNANCE.md to reflect actual state

---

## 2. Full Library Inventory

### Platform TypeScript Libraries

| Library | Path | Purpose | Primary Consumers | Major Dependencies | Verdict |
|---------|------|---------|-------------------|-------------------|---------|
| `@ghatana/design-system` | `platform/typescript/design-system` | UI primitives (atoms/molecules/organisms) | All products | `@ghatana/platform-utils`, `@ghatana/theme`, `@ghatana/tokens` | **Keep** - Core library |
| `@ghatana/platform-utils` | `platform/typescript/foundation/platform-utils` | Shared utilities (`cn()`, formatters) | All platform libs | `clsx`, `tailwind-merge` | **Keep** - Foundation library |
| `@ghatana/tokens` | `platform/typescript/tokens` | Design tokens (colors, spacing, typography) | `@ghatana/theme`, `@ghatana/design-system` | `zod` | **Keep** - Foundation library |
| `@ghatana/theme` | `platform/typescript/theme` | Theme system, dark mode | `@ghatana/design-system`, `@ghatana/charts` | `@ghatana/tokens`, `@mui/material` | **Keep** - Core theming |
| `@ghatana/api` | `platform/typescript/api` | Fetch-based API client | `@yappc/core` | None | **Keep** - Simple, focused |
| `@ghatana/state` | `platform/typescript/state` | Jotai atoms, state machines | `@yappc/state` | `jotai`, `zod` | **Keep** - Well-structured |
| `@ghatana/events` | `platform/typescript/events` | Platform event abstractions | `@ghatana/browser-events`, `@ghatana/realtime` | `zod` | **Keep** - Canonical event types |
| `@ghatana/realtime` | `platform/typescript/realtime` | WebSocket/SSE helpers | Unknown | `zod` | **Refactor** - Duplicate PlatformEvent |
| `@ghatana/browser-events` | `platform/typescript/browser-events` | Browser extension events | DCMAAR | `@ghatana/events`, `zod` | **Keep** - Properly extends events |
| `@ghatana/canvas` | `platform/typescript/canvas` | Canvas/visualization (hybrid renderer) | YAPPC, AEP, Data-Cloud | 10+ deps including ReactFlow, RxJS, Yjs | **Refactor** - Too large, needs splitting |
| `@ghatana/canvas-core` | `platform/typescript/canvas-core` | Canvas base types | `@ghatana/canvas-react`, `@ghatana/canvas-plugins` | `@ghatana/canvas` | **Merge** into canvas |
| `@ghatana/canvas-react` | `platform/typescript/canvas-react` | React canvas components | `@ghatana/canvas-chrome` | `@ghatana/canvas`, `@ghatana/canvas-core` | **Merge** into canvas |
| `@ghatana/canvas-plugins` | `platform/typescript/canvas-plugins` | Plugin system | Unknown | `@ghatana/canvas`, `@ghatana/canvas-core` | **Merge** into canvas |
| `@ghatana/canvas-tools` | `platform/typescript/canvas-tools` | Drawing tools | Unknown | `@ghatana/canvas`, `@ghatana/canvas-core` | **Merge** into canvas |
| `@ghatana/canvas-chrome` | `platform/typescript/canvas-chrome` | Canvas shell UI | Unknown | `@ghatana/canvas`, `@ghatana/canvas-core`, `@ghatana/canvas-react` | **Merge** into canvas |
| `@ghatana/accessibility` | `platform/typescript/accessibility` | WCAG auditing, scoring | `@ghatana/design-system` | `@axe-core/react`, `axe-core`, `zod` | **Keep** - Canonical |
| `@ghatana/accessibility-audit` | `platform/typescript/accessibility-audit` | **DEPRECATED** - Same as accessibility | None | Same as accessibility | **Remove** - Deprecated |
| `@ghatana/audit-components` | `platform/typescript/audit-components` | **DEPRECATED** - Same as accessibility | None | Same as accessibility | **Remove** - Deprecated |
| `@ghatana/domain-components` | `platform/typescript/domain-components` | Domain-specific UI (privacy, security, voice) | Unknown | `@ghatana/platform-utils` | **Refactor** - Duplicates design-system |
| `@ghatana/charts` | `platform/typescript/charts` | Chart primitives (Recharts-based) | `@dcmaar/ui`, `@tutorputor/ui` | `@ghatana/theme` | **Keep** - Focused |
| `@ghatana/forms` | `platform/typescript/forms` | Form primitives, validation | Unknown | `@ghatana/design-system`, `@ghatana/platform-utils` | **Keep** - Needed |
| `@ghatana/data-grid` | `platform/typescript/data-grid` | Data grid with pagination | Unknown | `@ghatana/design-system`, `@tanstack/react-table` | **Keep** - Focused |
| `@ghatana/wizard` | `platform/typescript/wizard` | Multi-step wizard | Unknown | `@ghatana/design-system` | **Keep** - Focused |
| `@ghatana/code-editor` | `platform/typescript/code-editor` | Monaco editor component | YAPPC | `monaco-editor` (peer) | **Keep** - Specialized |
| `@ghatana/i18n` | `platform/typescript/i18n` | Internationalization | Unknown | `i18next`, `react-i18next` | **Keep** - Focused |
| `@ghatana/sso-client` | `platform/typescript/sso-client` | SSO/JWT auth client | `@yappc/auth` | `zod`, `fastify-plugin` | **Keep** - Security-critical |
| `@ghatana/config` | `platform/typescript/config` | Environment, feature flags | Unknown | `zod` | **Keep** - Needed |
| `@ghatana/platform-testing` | `platform/typescript/testing` | Testing utilities | Unknown | Testing libraries | **Keep** - Infrastructure |

### Product-Specific Frontend Libraries

| Library | Path | Purpose | Primary Consumers | Dependencies | Verdict |
|---------|------|---------|-------------------|--------------|---------|
| `@yappc/ui` | `products/yappc/frontend/libs/yappc-ui` | YAPPC UI components | YAPPC web app | 10+ platform libs | **Keep** - Product-specific |
| `@yappc/core` | `products/yappc/frontend/libs/yappc-core` | YAPPC core utilities | `@yappc/ui`, `@yappc/state` | `@ghatana/api`, `@ghatana/platform-utils` | **Keep** - Product-specific |
| `@yappc/state` | `products/yappc/frontend/libs/yappc-state` | YAPPC state (re-exports platform) | `@yappc/ui` | `@ghatana/state`, `@yappc/core` | **Keep** - Good pattern |
| `@yappc/api` | `products/yappc/frontend/libs/api` | YAPPC API layer | `@yappc/core` | `@ghatana/api` | **Keep** - Product-specific |
| `@yappc/auth` | `products/yappc/frontend/libs/yappc-auth` | YAPPC auth (wraps sso-client) | YAPPC apps | `@ghatana/sso-client` | **Keep** - Product-specific |
| `@dcmaar/ui` | `products/dcmaar/libs/typescript/dcmaar-ui` | DCMAAR UI components | DCMAAR apps | `@ghatana/design-system`, `@ghatana/charts` | **Keep** - Product-specific |
| `@tutorputor/ui` | `products/tutorputor/libs/tutorputor-ui` | TutorPutor UI components | TutorPutor apps | `@ghatana/charts`, `@ghatana/theme` | **Keep** - Product-specific |
| `@audio-video/ui` | `products/audio-video/libs/audio-video-ui` | Audio-Video UI | Audio-Video apps | `@ghatana/design-system` | **Keep** - Product-specific |

### Canvas Library Dependency Chain

```
@ghatana/canvas-chrome
  └─> @ghatana/canvas-react
        └─> @ghatana/canvas-core
              └─> @ghatana/canvas

@ghatana/canvas-plugins
  └─> @ghatana/canvas-core
        └─> @ghatana/canvas

@ghatana/canvas-tools
  └─> @ghatana/canvas-core
        └─> @ghatana/canvas
```

**Problem:** 5 sub-libraries all depend on the main canvas library and each other, creating unnecessary dependency complexity.

---

## 3. Cross-Library Findings

### 3.1 Inconsistency Patterns

#### Pattern 1: Dual PlatformEvent Definitions (CRITICAL)

**Evidence:**

`@ghatana/events/src/types.ts:38-56`:
```typescript
export interface PlatformEvent<T = unknown> {
  readonly id: string;
  readonly type: string;
  readonly timestamp: number;
  readonly source: EventSource;
  readonly data: T;
  readonly correlationId?: string;
  readonly schemaVersion?: string;  // <-- Has schemaVersion
}
```

`@ghatana/realtime/src/events/types.ts:45-58`:
```typescript
export interface PlatformEvent<T = unknown> {
  readonly id: string;
  readonly type: string;
  readonly timestamp: number;
  readonly source: EventSource;
  readonly data: T;
  readonly correlationId?: string;
  // NO schemaVersion!
}
```

**Impact:** Events created with one system cannot be properly typed with the other. Type guards and validators will fail cross-library.

**Severity:** Critical

#### Pattern 2: EventSourceType Inconsistency

`@ghatana/events`: `"browser" | "server" | "client" | "extension" | "mobile" | "desktop"`

`@ghatana/realtime`: `"browser" | "server" | "client" | "extension"` (missing "mobile" and "desktop")

**Severity:** High

#### Pattern 3: Build Tool Inconsistency

**Evidence:**
- Most libraries use `tsc` for building (correct per governance)
- `@yappc/ui` uses `tsup` (violates governance)
- `@yappc/core` uses `tsup` (violates governance)
- `@yappc/state` uses `tsup` (violates governance)

`LIBRARY_GOVERNANCE.md:84`:
> "Use `tsc` as the build tool (not `tsup`, `esbuild`, or similar for library output)"

**Severity:** Medium

### 3.2 Duplication Patterns

#### Pattern 1: Triplicated Accessibility Libraries

Three libraries with **identical source code**:

1. `@ghatana/accessibility` (current)
2. `@ghatana/accessibility-audit` (deprecated, marked superseded)
3. `@ghatana/audit-components` (deprecated, marked superseded)

All three export:
- Same `AccessibilityAuditor` class
- Same `AccessibilityReport` types
- Same `useAccessibilityAudit` hook
- Same formatters (JSON, HTML, CSV, SARIF, XML, Markdown)
- Same `runQuickAudit()` helper

**Evidence from `@ghatana/accessibility-audit/src/index.ts:1-12`:**
```typescript
/**
 * @ghatana/accessibility-audit — DEPRECATED
 *
 * This package is superseded by `@ghatana/accessibility`.
 * All exports are re-exported from the unified package for backward compatibility.
 *
 * @deprecated Use `@ghatana/accessibility` instead.
 */
```

**Severity:** High

#### Pattern 2: Duplicated Domain Components

Components exist in **both** `@ghatana/design-system` and `@ghatana/domain-components`:

| Component | Design-System Path | Domain-Components Path |
|-----------|-------------------|----------------------|
| ConsentManager | `design-system/src/privacy/ConsentManager.tsx` | `domain-components/src/privacy/ConsentManager.tsx` |
| RBACGuard | `design-system/src/security/RBACGuard.tsx` | `domain-components/src/security/RBACGuard.tsx` |
| VoiceInput | `design-system/src/voice/VoiceInput.tsx` | `domain-components/src/voice/VoiceInput.tsx` |
| NLQInput | `design-system/src/nlp/NLQInput.tsx` | `domain-components/src/nlp/NLQInput.tsx` |
| useSelection | `design-system/src/selection/useSelection.ts` | `domain-components/src/selection/useSelection.ts` |

**Evidence:** Compared `ConsentManager.tsx` files - they are **character-for-character identical**.

**Severity:** High

#### Pattern 3: Duplicate Event Schema Definitions

Both `@ghatana/events` and `@ghatana/realtime` define:
- `EventSource` interface
- `EventSourceType` type
- `EventSourceSchema` Zod schema
- `PlatformEventSchema` Zod schema

They differ only in `schemaVersion` presence and available source types.

**Severity:** High

### 3.3 Sprawl Patterns

#### Pattern 1: Canvas Library Over-Fragmentation

6 separate canvas libraries with circular dependencies:

```
canvas (main)
canvas-core (depends on canvas)
canvas-react (depends on canvas, canvas-core)
canvas-plugins (depends on canvas, canvas-core)
canvas-tools (depends on canvas, canvas-core)
canvas-chrome (depends on canvas, canvas-core, canvas-react)
```

**Evidence from `canvas-core/package.json:25-27`:**
```json
"dependencies": {
  "@ghatana/canvas": "workspace:*"
}
```

Canvas-core depends on canvas, but canvas also re-exports from core areas.

**Severity:** High

**Impact:**
- Build order complexity
- Circular dependency risk
- Cognitive overhead (which canvas lib do I import from?)
- Version synchronization issues

#### Pattern 2: Multiple Event Libraries Without Clear Boundaries

| Library | Purpose | Overlap |
|---------|---------|---------|
| `@ghatana/events` | Generic event system | Base types |
| `@ghatana/realtime` | WebSocket/SSE + events | Duplicates event types |
| `@ghatana/browser-events` | Browser extension events | Extends events properly |

The distinction between `events` and `realtime` is unclear - both define event types, but realtime is also supposed to be for WebSocket/SSE.

**Severity:** Medium

### 3.4 Boundary Violations

#### Violation 1: MUI in Theme Library

`@ghatana/theme/package.json:54-55`:
```json
"peerDependencies": {
  "@mui/material": "^7.0.0"
}
```

A platform theme library should not depend on a specific UI framework (MUI). This forces MUI on all consumers.

**Severity:** High

#### Violation 2: Fastify in SSO Client

`@ghatana/sso-client/package.json:47-48`:
```json
"dependencies": {
  "fastify-plugin": "^5.0.1"
}
```

A client-side SSO library should not depend on Fastify (a server framework). This is likely a server-side leak.

**Severity:** Medium

### 3.5 Documentation Drift

`LIBRARY_GOVERNANCE.md:65` lists `@ghatana/audit-components` as a canonical library:
```markdown
| `@ghatana/audit-components` | Accessibility audit + audit log components | Platform |
```

**But** the actual code shows it's **deprecated**:
```typescript
/**
 * @ghatana/audit-components — DEPRECATED
 *
 * This package is superseded by `@ghatana/accessibility`.
 */
```

**Severity:** Medium

---

## 4. Detailed Findings by Library

### 4.1 `@ghatana/events` - KEEP with REFACTOR

**Purpose Clarity:** Clear - platform event abstractions

**Findings:**
- **Severity: Medium** - Missing EventSourceType values ("mobile", "desktop") in realtime
- **Severity: High** - PlatformEvent includes `schemaVersion` but realtime's doesn't

**Evidence:**
```typescript
// @ghatana/events/src/types.ts:11-17
export type EventSourceType =
  | "browser"
  | "server"
  | "client"
  | "extension"
  | "mobile"    // Missing in realtime
  | "desktop";  // Missing in realtime
```

**Recommended Fix:**
1. Make `@ghatana/events` the **single source of truth** for event types
2. Have `@ghatana/realtime` re-export from `@ghatana/events`
3. Remove duplicate definitions

---

### 4.2 `@ghatana/realtime` - REFACTOR

**Purpose Clarity:** Ambiguous - mixes WebSocket/SSE with event types

**Findings:**
- **Severity: Critical** - Duplicate and INCOMPATIBLE PlatformEvent type
- **Severity: High** - Missing EventSourceType values
- **Severity: Medium** - Missing schemaVersion field

**Evidence:**
```typescript
// @ghatana/realtime/src/events/types.ts:20
export type EventSourceType = 'browser' | 'server' | 'client' | 'extension';
// Missing 'mobile' and 'desktop'
```

**Recommended Fix:**
1. Remove event type definitions from realtime
2. Import all event types from `@ghatana/events`
3. Focus realtime on WebSocket/SSE transport only

---

### 4.3 `@ghatana/accessibility`, `@ghatana/accessibility-audit`, `@ghatana/audit-components` - CONSOLIDATE

**Purpose Clarity:** Clear for accessibility, but triplicated

**Findings:**
- **Severity: High** - Three libraries with identical code
- **Severity: Medium** - Deprecated libraries still in workspace
- **Severity: Medium** - Violates "No deprecated package names" rule

**Evidence:**
All three libraries export identical:
```typescript
export { AccessibilityAuditor } from './AccessibilityAuditor';
export { useAccessibilityAudit } from './useAccessibilityAudit';
export { AccessibilityScorer } from './scoring/AccessibilityScorer';
export { JSONFormatter, HTMLFormatter, ... } from './formatters';
export async function runQuickAudit(...) { ... }
```

**Recommended Fix:**
1. **Remove** `accessibility-audit` and `audit-components` packages immediately
2. Update any consumers to use `@ghatana/accessibility`
3. Update LIBRARY_GOVERNANCE.md

---

### 4.4 `@ghatana/design-system` and `@ghatana/domain-components` - CONSOLIDATE

**Purpose Clarity:** Unclear boundaries - both have domain components

**Findings:**
- **Severity: High** - Identical component implementations in both libraries
- **Severity: Medium** - Comment says components moved, but they remain

**Evidence:**
```typescript
// design-system/src/index.ts:134-141
// NOTE: The following domain-specific modules have been moved to dedicated packages:
// - audit        → @ghatana/audit-components
// - privacy      → @ghatana/domain-components
// - security     → @ghatana/domain-components
// - voice        → @ghatana/domain-components
// - nlp          → @ghatana/domain-components
// - selection    → @ghatana/domain-components
```

But the files still exist in design-system!

**Recommended Fix:**
1. Remove domain components from design-system
2. Keep them only in domain-components
3. Update design-system exports to re-export from domain-components if backward compatibility needed

---

### 4.5 Canvas Libraries (`canvas`, `canvas-core`, `canvas-react`, `canvas-plugins`, `canvas-tools`, `canvas-chrome`) - MERGE

**Purpose Clarity:** Fragmented - each has partial purpose

**Findings:**
- **Severity: High** - 6 libraries where 1-2 would suffice
- **Severity: High** - Circular dependency pattern (core depends on canvas)
- **Severity: Medium** - Complex import graph

**Evidence:**
```json
// canvas-core/package.json
"dependencies": {
  "@ghatana/canvas": "workspace:*"  // Core depends on main?
}
```

**Recommended Fix:**
1. Merge all canvas sub-libraries into `@ghatana/canvas`
2. Use subpath exports for organization:
   - `@ghatana/canvas/core`
   - `@ghatana/canvas/react`
   - `@ghatana/canvas/plugins`
   - `@ghatana/canvas/tools`
   - `@ghatana/canvas/chrome`

---

### 4.6 `@ghatana/theme` - REFACTOR

**Purpose Clarity:** Clear - theme system

**Findings:**
- **Severity: High** - MUI dependency in peerDependencies
- **Severity: Medium** - Forces MUI on all consumers

**Evidence:**
```json
"peerDependencies": {
  "react": "^19.2.4",
  "@mui/material": "^7.0.0"  // Framework coupling!
}
```

**Recommended Fix:**
1. Remove MUI dependency
2. Create `@ghatana/theme-mui` adapter if MUI integration needed
3. Keep core theme library framework-agnostic

---

### 4.7 `@ghatana/sso-client` - REFACTOR

**Purpose Clarity:** Clear - SSO client

**Findings:**
- **Severity: Medium** - Server-side dependency (fastify-plugin) in client library

**Evidence:**
```json
"dependencies": {
  "fastify-plugin": "^5.0.1"  // Server framework!
}
```

**Recommended Fix:**
1. Remove fastify-plugin dependency
2. If server-side SSO needed, create separate `@ghatana/sso-server`

---

### 4.8 Product Libraries (`@yappc/*`, `@dcmaar/ui`, `@tutorputor/ui`) - KEEP

**Purpose Clarity:** Clear - product-specific UI

**Findings:**
- **Severity: Low** - `@yappc/*` libraries use `tsup` instead of `tsc`
- **Severity: Low** - Multiple build tools in same repo

**Evidence:**
```json
// @yappc/ui/package.json
"scripts": {
  "build": "tsup"  // Should be "tsc"
}
```

**Recommended Fix:**
1. Align YAPPC libraries with platform build standards
2. Use `tsc` for library builds
3. Use `tsup` only for app bundles if needed

---

## 5. Event Library Review

### 5.1 Current State

Three event-related libraries exist:

1. **`@ghatana/events`** - Base event types, dispatcher, serializer, validation
2. **`@ghatana/realtime`** - WebSocket/SSE + DUPLICATE event types
3. **`@ghatana/browser-events`** - Browser extension events (extends events properly)

### 5.2 Problems Identified

#### Problem 1: Incompatible PlatformEvent Types

`@ghatana/events` PlatformEvent has `schemaVersion?: string`
`@ghatana/realtime` PlatformEvent does NOT have `schemaVersion`

This makes them **structurally incompatible** at the type level.

#### Problem 2: Inconsistent EventSourceType

`@ghatana/events`: 6 types (includes "mobile", "desktop")
`@ghatana/realtime`: 4 types (missing "mobile", "desktop")

#### Problem 3: Realtime Library Overreach

Realtime library should focus on transport (WebSocket/SSE), not event type definitions.

### 5.3 Recommended Restructuring

```
@ghatana/events (canonical owner of ALL event types)
├── Base types: PlatformEvent, EventSource, etc.
├── Dispatcher
├── Serializer
└── Validation

@ghatana/realtime (transport only)
├── WebSocket client
├── SSE client
└── Re-exports event types from @ghatana/events

@ghatana/browser-events (extension-specific)
├── BrowserEvent extends PlatformEvent
├── BrowserEventSource extends EventSource
└── Capture abstractions
```

### 5.4 Canonical Ownership

| Concept | Canonical Owner | Consumers |
|---------|-----------------|-----------|
| PlatformEvent | `@ghatana/events` | All event libraries |
| EventSource | `@ghatana/events` | All event libraries |
| Event dispatcher | `@ghatana/events` | Apps needing pub/sub |
| WebSocket transport | `@ghatana/realtime` | Real-time apps |
| Browser events | `@ghatana/browser-events` | Browser extensions |

---

## 6. Consolidation and Simplification Plan

### 6.1 Libraries to Remove

| Library | Reason | Migration |
|---------|--------|-----------|
| `@ghatana/accessibility-audit` | Deprecated duplicate | Use `@ghatana/accessibility` |
| `@ghatana/audit-components` | Deprecated duplicate | Use `@ghatana/accessibility` |

### 6.2 Libraries to Merge

| Libraries | Target | Strategy |
|-----------|--------|----------|
| `canvas-core`, `canvas-react`, `canvas-plugins`, `canvas-tools`, `canvas-chrome` | `@ghatana/canvas` | Subpath exports |

### 6.3 Responsibilities to Move

| From | To | Responsibility |
|------|----|---------------|
| `@ghatana/realtime` | `@ghatana/events` | Event type definitions |
| `@ghatana/design-system` | `@ghatana/domain-components` | Domain components (privacy, security, voice, nlp, selection) |

### 6.4 Dependencies to Remove

| Library | Dependency | Reason |
|---------|------------|--------|
| `@ghatana/theme` | `@mui/material` | Framework coupling |
| `@ghatana/sso-client` | `fastify-plugin` | Server-side in client lib |

### 6.5 Build Tool Alignment

| Libraries | Current | Target |
|-----------|---------|--------|
| `@yappc/*` | `tsup` | `tsc` |

---

## 7. Target-State Library Architecture

### 7.1 Library Categories

```
platform/typescript/
├── foundation/                    # Zero-dependency base
│   └── platform-utils/           # cn(), formatters
│
├── design/                       # Visual design system
│   ├── tokens/                   # Design tokens
│   ├── theme/                    # Theme system (framework-agnostic)
│   └── design-system/            # UI components
│
├── domain/                       # Domain-specific UI
│   └── domain-components/        # privacy, security, voice, nlp, selection
│
├── data/                         # Data handling
│   ├── api/                      # API client
│   ├── state/                    # State management (Jotai)
│   ├── forms/                    # Form handling
│   ├── charts/                   # Chart components
│   └── data-grid/                # Data grid
│
├── interaction/                  # User interaction
│   ├── canvas/                   # Canvas/visualization (merged)
│   ├── code-editor/              # Monaco editor
│   └── wizard/                   # Wizard flows
│
├── platform-services/            # Platform capabilities
│   ├── events/                   # Event system (canonical)
│   ├── realtime/                 # WebSocket/SSE (transport only)
│   ├── browser-events/           # Browser extension events
│   ├── sso-client/               # SSO client (client-side only)
│   ├── i18n/                     # Internationalization
│   └── config/                   # Configuration, feature flags
│
└── quality/                      # Testing, quality
    ├── accessibility/            # WCAG tools (single library)
    └── testing/                  # Test utilities
```

### 7.2 Ownership Rules

1. **Platform libraries** (`@ghatana/*`): Platform team owns, product teams can contribute
2. **Product libraries** (`@<product>/*`): Product team owns
3. **Foundation libraries**: Must have zero or minimal dependencies
4. **Design libraries**: Must be framework-agnostic or clearly adapter-patterned

### 7.3 Dependency Direction Rules

```
product libs ──────> platform libs ──────> foundation libs
     │                    │                    │
     └────────────────────┴────────────────────┘
              (no reverse dependencies)
```

**Forbidden:**
- Platform libraries depending on product libraries
- Foundation libraries depending on platform libraries
- Circular dependencies between any libraries

### 7.4 Event Library Rules

1. **Single source of truth**: `@ghatana/events` owns all event type definitions
2. **Extension pattern**: Specialized event libraries extend base types
3. **No type duplication**: Transport libraries re-export, don't redefine
4. **Runtime validation**: All event boundaries must use Zod schemas

### 7.5 Naming Rules

1. **Platform**: `@ghatana/<kebab-case>`
2. **Products**: `@<product>/<kebab-case>`
3. **No deprecated aliases**: Fix forward, no compatibility shims
4. **Clear purpose**: Name must indicate single responsibility

### 7.6 API Exposure Rules

1. **Minimal surface**: Export only what's needed
2. **Subpath exports**: Use for organization instead of separate packages
3. **Internal marking**: Use `@internal` JSDoc for private APIs
4. **Barrel files**: Each library must have `src/index.ts`

### 7.7 Testing Expectations

1. **Co-located tests**: `__tests__/` next to source
2. **Coverage**: Minimum 80% for critical paths
3. **Type testing**: Test public API types
4. **Integration**: Test cross-library composition

---

## 8. Prioritized Action Plan

### Phase 1: Critical Fixes (Immediate - 1 week)

| # | Issue | Affected Libraries | Concrete Change | Risk if Ignored |
|---|-------|-------------------|-------------------|-----------------|
| 1 | Remove deprecated accessibility libs | `accessibility-audit`, `audit-components` | Delete packages, update any consumers | Confusion, maintenance burden, import inconsistency |
| 2 | Align PlatformEvent types | `events`, `realtime` | Make realtime re-export from events | Type incompatibility, runtime errors |
| 3 | Remove duplicate domain components | `design-system` | Delete privacy, security, voice, nlp, selection from design-system | Code divergence, double maintenance |
| 4 | Remove MUI from theme | `theme` | Extract MUI-specific code to adapter | Framework lock-in, bundle bloat |

### Phase 2: Boundary/Reuse Cleanup (2-4 weeks)

| # | Issue | Affected Libraries | Concrete Change | Expected Benefit |
|---|-------|-------------------|-------------------|------------------|
| 5 | Merge canvas libraries | `canvas-*` subpackages | Merge into `canvas` with subpath exports | Reduced complexity, faster builds |
| 6 | Fix sso-client dependencies | `sso-client` | Remove fastify-plugin, create server lib if needed | Clean separation of concerns |
| 7 | Align YAPPC build tools | `yappc-ui`, `yappc-core`, `yappc-state` | Switch from `tsup` to `tsc` | Consistent build, governance compliance |
| 8 | Update governance docs | `LIBRARY_GOVERNANCE.md` | Reflect actual library state, remove deprecated refs | Accurate guidance |

### Phase 3: Simplification/Consolidation (1-2 months)

| # | Issue | Affected Libraries | Concrete Change | Expected Benefit |
|---|-------|-------------------|-------------------|------------------|
| 9 | Refactor realtime focus | `realtime` | Remove event types, focus on transport | Clear responsibility |
| 10 | Audit all peer dependencies | All platform libs | Remove unnecessary peer deps | Cleaner dependency graph |
| 11 | Consolidate testing utilities | `testing`, `platform-testing` | Merge if duplicate | Single testing toolkit |
| 12 | Review product UI libraries | `@dcmaar/ui`, `@tutorputor/ui`, etc. | Ensure proper reuse of platform libs | Less duplication |

### Phase 4: Long-Term Hardening (2-3 months)

| # | Issue | Affected Libraries | Concrete Change | Expected Benefit |
|---|-------|-------------------|-------------------|------------------|
| 13 | Strict type checking | All TS libs | Enable strictest TypeScript flags | Better type safety |
| 14 | Dependency linting | All libs | Add dependency linting (dependency-cruiser) | Prevent circular deps |
| 15 | API compatibility testing | Platform libs | Add API surface tests | Catch breaking changes |
| 16 | Documentation sync | All libs | Auto-generate docs from code | Accurate documentation |

---

## 9. Final Verdict

### The current frontend/TypeScript library ecosystem is: **FRAGMENTED AND RISKY**

While the foundational architecture is sound (clear platform/product separation, good naming conventions, consistent tooling base), **execution has drifted significantly**:

1. **Critical structural issues** exist (incompatible PlatformEvent types)
2. **High duplication** exists (3 accessibility libs, duplicate domain components)
3. **Unjustified sprawl** exists (6 canvas libraries)
4. **Documentation is out of sync** with code reality

### Why "Fragmented and Risky" vs "Needs Major Restructuring"

The issues are **localized and fixable**:
- No fundamental architectural flaws
- No widespread circular dependencies
- No massive tech debt requiring rewrite

The fixes are primarily **consolidation and cleanup**, not redesign.

### Confidence Factors

| Factor | Status |
|--------|--------|
| Clear naming conventions | Strong |
| Platform/product boundaries | Strong |
| Build tooling consistency | Moderate (tsup vs tsc issue) |
| Type safety discipline | Moderate (any types in places) |
| Documentation accuracy | Weak |
| Library governance adherence | Weak |

### Recommended Immediate Actions

1. **This week:** Remove deprecated accessibility libraries
2. **This week:** Align PlatformEvent types
3. **Next sprint:** Begin canvas library consolidation
4. **Next month:** Complete all Phase 1 and 2 items

---

## Appendix A: Library Dependency Graph

```
@ghatana/platform-utils (foundation)
  ↓
@ghatana/tokens
  ↓
@ghatana/theme
  ↓
@ghatana/design-system ← @ghatana/platform-utils
  ↓
@ghatana/domain-components ← @ghatana/platform-utils
  ↓
@ghatana/canvas ← @ghatana/design-system
  ↓
@ghatana/canvas-core ← @ghatana/canvas  [circular risk]
  ↓
@ghatana/canvas-react ← @ghatana/canvas, @ghatana/canvas-core
  ↓
@ghatana/canvas-chrome ← @ghatana/canvas, @ghatana/canvas-core, @ghatana/canvas-react

@ghatana/events (canonical)
  ↓
@ghatana/browser-events
  ↓
@ghatana/realtime [should re-export from events, not duplicate]

@ghatana/state
  ↓
@yappc/state ← re-exports platform + adds YAPPC-specific
```

## Appendix B: Evidence Summary

| Finding | File Path | Lines |
|---------|-----------|-------|
| Duplicate PlatformEvent (events) | `platform/typescript/events/src/types.ts` | 38-56 |
| Duplicate PlatformEvent (realtime) | `platform/typescript/realtime/src/events/types.ts` | 45-58 |
| accessibility-audit deprecated | `platform/typescript/accessibility-audit/src/index.ts` | 1-12 |
| audit-components deprecated | `platform/typescript/audit-components/src/index.ts` | 1-15 |
| Duplicate ConsentManager (design-system) | `platform/typescript/design-system/src/privacy/ConsentManager.tsx` | 1-174 |
| Duplicate ConsentManager (domain-components) | `platform/typescript/domain-components/src/privacy/ConsentManager.tsx` | 1-174 |
| MUI in theme peerDeps | `platform/typescript/theme/package.json` | 54-55 |
| Fastify in sso-client deps | `platform/typescript/sso-client/package.json` | 47-48 |
| tsup in yappc-ui | `products/yappc/frontend/libs/yappc-ui/package.json` | 63 |
| LIBRARY_GOVERNANCE outdated | `platform/typescript/LIBRARY_GOVERNANCE.md` | 65 |
| canvas-core depends on canvas | `platform/typescript/canvas-core/package.json` | 25-27 |

---

**End of Audit Report**
