# Ghatana Frontend/TypeScript Library Architecture - Copilot Instructions

> **Purpose**: Definitive guide for AI-assisted changes to frontend and TypeScript libraries.
> **Last Updated**: 2026-04-10
> **Status**: Active - All library changes must follow these instructions

---

## 1. Library Architecture Principles

### 1.1 Core Principles

| Principle | Rule | Rationale |
|-----------|------|-----------|
| **Reuse First** | Before creating new code, check if it exists in platform libraries | Prevents duplication |
| **Downward Dependencies Only** | Products may depend on Platform; Platform NEVER depends on Products | Enforces layering |
| **Single Responsibility** | Each library has ONE clear purpose documented in one sentence | Prevents god libraries |
| **No Thin Wrappers** | Product libraries must add substantial value, not just re-export platform | Eliminates overhead |
| **Framework Peer Dependencies** | React/Vue/etc must be peerDependencies, never regular dependencies | Prevents version conflicts |

### 1.2 Library Categories

```
┌─────────────────────────────────────────────────────────────┐
│  PLATFORM LIBRARIES (@ghatana/*)                              │
│  Location: platform/typescript/*                            │
│                                                               │
│  UI Layer:                                                    │
│  - @ghatana/design-system      → Atomic components            │
│  - @ghatana/domain-components  → Privacy, security, voice, NLP  │
│  - @ghatana/canvas-*          → Canvas (split: core, react,    │
│                                  plugins, chrome, tools)      │
│  - @ghatana/forms             → Form primitives + validation  │
│  - @ghatana/data-grid         → Tables with pagination        │
│  - @ghatana/wizard            → Stepper/wizard patterns       │
│  - @ghatana/charts            → Chart primitives (Recharts) │
│                                                               │
│  State Layer:                                                 │
│  - @ghatana/state             → Jotai atoms (ALL platform)     │
│                                                               │
│  Communication Layer:                                         │
│  - @ghatana/api               → HTTP client                  │
│  - @ghatana/events            → Event system (base)          │
│  - @ghatana/browser-events    → Browser extension events     │
│  - @ghatana/realtime          → WebSocket/SSE                │
│                                                               │
│  Cross-Cutting:                                               │
│  - @ghatana/tokens            → Design tokens                │
│  - @ghatana/theme             → Theme system (split: core,   │
│                                  react)                       │
│  - @ghatana/i18n              → Internationalization          │
│  - @ghatana/accessibility     → A11y testing + components    │
│  - @ghatana/sso-client        → Authentication               │
│  - @ghatana/platform-utils    → cn(), formatters            │
│  - @ghatana/testing           → Test utilities               │
└────────────────────┬────────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────────┐
│  PRODUCT LIBRARIES (@[product]/*)                            │
│  Location: products/[product]/libs/*                         │
│                                                               │
│  Allowed Types:                                               │
│  - Types libraries (@dcmaar/types, @audio-video/types)        │
│  - Domain-specific logic (@dcmaar/connectors)                 │
│  - Product business logic (@yappc/ai, @yappc/collab)         │
│                                                               │
│  PROHIBITED:                                                  │
│  - UI wrappers around @ghatana/design-system                  │
│  - State wrappers around @ghatana/state                       │
│  - Thin API wrappers around @ghatana/api                    │
│  - Re-implementations of platform utilities                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Consolidation Decisions

### 2.1 Merges (Execute Immediately)

| Source | Target | Action | Rationale |
|--------|--------|--------|-----------|
| `@yappc/state` | `@ghatana/state` | Merge YAPPC atoms into platform | State is platform concern |
| `@yappc/auth` | `@ghatana/sso-client` | Merge auth into SSO client | Auth is platform concern |
| `@yappc/api` | `@ghatana/api` | Delete wrapper, use platform directly | Thin wrapper adds no value |
| `@yappc/a11y` | `@ghatana/accessibility` | Merge into single a11y library | 3 libraries → 1 |
| `@ghatana/audit-components` | `@ghatana/accessibility` | Merge into single a11y library | Duplication |
| `@ghatana/accessibility-audit` | `@ghatana/accessibility` | Merge into single a11y library | Duplication |
| `@dcmaar/ui` | (delete) | Consume design-system directly | Thin wrapper |
| `@yappc/ui` | (delete) | Consume design-system directly | Thin wrapper |
| `@tutorputor/ui` | (delete) | Consume design-system directly | Thin wrapper |

### 2.2 Splits (High Priority)

| Source | Split Into | Priority | Rationale |
|--------|------------|----------|-----------|
| `@ghatana/canvas` (146 items) | `@ghatana/canvas-core`, `@ghatana/canvas-react`, `@ghatana/canvas-plugins`, `@ghatana/canvas-chrome`, `@ghatana/canvas-tools` | P0 | God library - unmaintainable |
| `@ghatana/design-system` (236 items) | Keep but remove domain exports | P1 | Domain components already moved |
| `@ghatana/theme` | `@ghatana/theme-core`, `@ghatana/theme-react` | P2 | Framework decoupling |

### 2.3 Product Library Refactoring

| Library | Action | New Structure |
|---------|--------|---------------|
| `@yappc/core` | Split | Keep core utils; move auth→sso-client, chat→new lib |
| `@yappc/ui` | Split | Keep generic UI; move initialization-ui, development-ui to separate libs |
| `@yappc/api` | Split | Keep generic API; move devsecops to separate lib |

---

## 3. Build Tool Standards

### 3.1 Platform Libraries

```json
{
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "clean": "rm -rf dist",
    "type-check": "tsc --noEmit",
    "test": "vitest run"
  }
}
```

- **Build Tool**: `tsc` only (per LIBRARY_GOVERNANCE.md)
- **Output**: `dist/` directory
- **Source**: `src/` directory
- **Entry**: `src/index.ts` barrel export

### 3.2 Product Libraries

- **Build Tool**: `tsc` for libraries, `tsup` allowed for app bundles only
- **Exports**: Must use explicit export map in package.json
- **No deep imports**: Consumers must not import from `dist/internal`

---

## 4. Dependency Rules

### 4.1 Allowed Dependencies

| Library | Can Depend On |
|---------|---------------|
| `@ghatana/*` | Other `@ghatana/*`, `zod`, `clsx`, `tailwind-merge` |
| `@dcmaar/*` | `@ghatana/*`, `@dcmaar/*` (same product), `zod` |
| `@yappc/*` | `@ghatana/*`, `@yappc/*` (same product), `zod` |

### 4.2 Prohibited Patterns

```typescript
// ❌ NEVER: Product library reimplementing platform utility
// In @yappc/ui/src/utils/cn.ts:
export function cn(...inputs: ClassValue[]) { ... } // DUPLICATE

// ✅ ALWAYS: Use platform utility
import { cn } from '@ghatana/platform-utils';

// ❌ NEVER: Framework in dependencies (must be peerDependencies)
"dependencies": {
  "react": "^19.2.4"  // WRONG
}

// ✅ ALWAYS: Framework as peer dependency
"peerDependencies": {
  "react": "^19.2.4"
}

// ❌ NEVER: Circular dependency
// @ghatana/state → @ghatana/canvas → @ghatana/state

// ❌ NEVER: Platform depends on Product
// @ghatana/design-system → @yappc/core  // VIOLATION
```

---

## 5. Event Library Pattern (Reference Model)

The event libraries demonstrate the **correct** way to compose platform abstractions:

```typescript
// @ghatana/events - Base types (canonical ownership)
export interface PlatformEvent<T> {
  readonly type: string;
  readonly data: T;
  readonly source: EventSource;
}

// @ghatana/browser-events - Extension via inheritance
import { PlatformEvent } from '@ghatana/events';

export interface BrowserEventSource extends EventSource {
  readonly type: "browser";
}

export interface TabEvent extends PlatformEvent<TabEventData> {
  readonly type: "tab.created" | "tab.updated" | ...;
  readonly source: BrowserEventSource;
}
```

**Rules for Extension:**
1. Extend base types via inheritance, never reimplement
2. Import schemas from base library, never redefine
3. Add only domain-specific orchestration
4. Use discriminated unions for type narrowing

---

## 6. State Management Pattern

### 6.1 Ownership Hierarchy

| Level | Location | Examples |
|-------|----------|----------|
| Platform | `@ghatana/state` | authAtom, tenantAtom, notificationAtom |
| Canvas | `@ghatana/canvas` | canvas-specific: viewport, selection, tools |
| Product | Product lib | Product-specific business logic |
| App | App code | App-specific transient state |

### 6.2 Atom Creation

```typescript
// ✅ ALWAYS: Use platform atom creation
import { createAtom, createPersistentAtom } from '@ghatana/state';

export const userPreferencesAtom = createPersistentAtom(
  'user-preferences',
  defaultPreferences,
  { storage: 'localStorage' },
  'User UI preferences'
);

// ❌ NEVER: Raw Jotai in product libraries
import { atom } from 'jotai';
export const myAtom = atom(null);  // Bypasses platform patterns
```

---

## 7. AI Assistant Rules

When working with Ghatana frontend/TypeScript libraries:

### Before Making Changes

1. **Check for existing implementations**: Search in `@ghatana/*` before creating new utilities
2. **Verify dependency direction**: Ensure no Platform→Product dependencies
3. **Check library size**: If library has >50 items, flag as potential god library
4. **Verify build tool**: Platform libraries must use `tsc`, not `tsup`

### When Creating New Libraries

1. **Proposal required**: Document purpose, consumers, why existing libs insufficient
2. **Single purpose**: Library description must fit in one sentence
3. **Multiple consumers**: At least 2 packages must use it
4. **Follow naming**: `@ghatana/[kebab-case]` for platform, `@[product]/[name]` for products

### When Modifying Existing Libraries

1. **Check export conflicts**: Run `pnpm type-check` before committing
2. **Update barrel exports**: Ensure `src/index.ts` exports new functionality
3. **No breaking changes without plan**: Breaking changes require migration path
4. **Add tests**: New functionality requires test coverage

### Prohibited Actions

| Action | Reason |
|--------|--------|
| Create local `cn()` implementation | Use `@ghatana/platform-utils` |
| Create product UI wrapper library | Consume `@ghatana/design-system` directly |
| Add framework to dependencies | Must be peerDependencies |
| Create state library that wraps `@ghatana/state` | Merge into platform instead |
| Add domain components to `@ghatana/design-system` | Use `@ghatana/domain-components` |
| Create god library (>100 items) | Split into focused libraries |

---

## 8. Implementation Phases

### Phase 1: Critical Consolidation (Weeks 1-2)

**Goal**: Eliminate critical duplication and god libraries

1. **Split @ghatana/canvas** into 5 focused libraries
2. **Merge accessibility libraries** (3→1)
3. **Merge @yappc/state into @ghatana/state**
4. **Merge @yappc/auth into @ghatana/sso-client**
5. **Delete @yappc/api** (use @ghatana/api directly)
6. **Delete product UI wrappers** (@dcmaar/ui, @yappc/ui, @tutorputor/ui)

### Phase 2: Product Library Cleanup (Weeks 3-4)

**Goal**: Split YAPPC god libraries

1. **Split @yappc/core** → core, auth (merged), chat
2. **Split @yappc/ui** → ui, initialization-ui, development-ui
3. **Split @yappc/api** → api, devsecops
4. **Consolidate code editors** → @ghatana/code-editor

### Phase 3: Platform Hardening (Weeks 5-6)

**Goal**: Complete platform library suite

1. **Finish @ghatana/forms** implementation
2. **Complete @ghatana/data-grid** implementation
3. **Implement @ghatana/wizard**
4. **Split @ghatana/theme** → theme-core, theme-react
5. **Standardize builds** → all use tsc

### Phase 4: Governance (Week 7-8)

**Goal**: Prevent future sprawl

1. **Enforce LIBRARY_GOVERNANCE.md** review process
2. **Add automated checks** for library size, dependency direction
3. **Document all canonical ownership** patterns
4. **Create migration guides** for product teams

---

## 9. Canonical Ownership Reference

| Concept | Canonical Library | Never Duplicate |
|---------|-------------------|-----------------|
| Event types | `@ghatana/events` | ❌ |
| Event dispatcher | `@ghatana/events` | ❌ |
| State atoms | `@ghatana/state` | ❌ |
| UI primitives | `@ghatana/design-system` | ❌ |
| Canvas | `@ghatana/canvas-*` | ❌ |
| cn() utility | `@ghatana/platform-utils` | ❌ |
| API client | `@ghatana/api` | ❌ |
| Auth/SSO | `@ghatana/sso-client` | ❌ |
| Accessibility | `@ghatana/accessibility` | ❌ |
| Design tokens | `@ghatana/tokens` | ❌ |
| Theme | `@ghatana/theme` | ❌ |
| Forms | `@ghatana/forms` | ❌ |
| Data grid | `@ghatana/data-grid` | ❌ |

---

## 10. Validation Checklist

Before marking any library refactoring as complete:

- [ ] All imports updated across codebase
- [ ] All tests passing
- [ ] Type checking passes (`tsc --noEmit`)
- [ ] No circular dependencies
- [ ] No imports from removed libraries
- [ ] Library has single clear purpose
- [ ] Exports are minimal and intentional
- [ ] Documentation updated
- [ ] Consumer libraries migrated

---

## 11. Key Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-10 | Merge @yappc/state into @ghatana/state | State is platform concern, eliminate duplication |
| 2026-04-10 | Merge @yappc/auth into @ghatana/sso-client | Auth is platform concern |
| 2026-04-10 | Delete @yappc/api | Thin wrapper, no added value |
| 2026-04-10 | Merge 3 a11y libraries into 1 | Eliminate duplication |
| 2026-04-10 | Delete product UI wrappers | Consume design-system directly |
| 2026-04-10 | Split canvas into 5 libraries | God library anti-pattern |
| 2026-04-10 | Standardize on tsc for libraries | LIBRARY_GOVERNANCE.md requirement |
| 2026-04-10 | Event libraries as reference model | Proper composition pattern |

---

## 12. Java Libraries and Modules Architecture

### 12.1 Core Principles for Java Modules

| Principle | Rule | Rationale |
|-----------|------|-----------|
| **Reuse First** | Before creating new code, check `platform:java:*` and `platform-kernel:*` | Prevents duplication |
| **Downward Dependencies Only** | Products depend on Platform; Platform NEVER depends on Products | Enforces layering |
| **Single Responsibility** | Each module has ONE clear purpose documented in `description = "..."` | Prevents god modules |
| **Canonical Ownership** | One source of truth per concern (Repository, Event, DTO patterns) | Prevents contract drift |
| **No Product Logic in Platform** | Platform modules are product-agnostic | Maintains purity |
| **Explicit API Surface** | Public APIs in `*.api` packages, internal in `*.internal` | Encapsulation |

### 12.2 Java Module Categories

```
platform/java/
├── foundation/              # Minimal dependencies
│   ├── core/              # Types, Promises, JSON, validation
│   ├── testing/           # Test fixtures, JUnit helpers
│   └── contracts/         # Protobuf schemas
├── infrastructure/        # Technical concerns
│   ├── database/          # Persistence (includes cache)
│   ├── http/              # HTTP client/server
│   ├── messaging/         # Kafka, connectors, events
│   ├── observability/     # Metrics, tracing
│   └── config/            # Configuration
├── domain/               # Business abstractions
│   ├── domain/            # Events, auth models, base types
│   ├── security/          # Auth, encryption (includes identity)
│   ├── governance/        # Policies (includes data-gov, policy-as-code)
│   ├── workflow/          # Promise-based workflows
│   └── ai-integration/    # AI/LLM abstractions
└── agent/                # Agent framework
    └── agent-core/        # Contracts, memory, dispatch, runtime

platform-kernel/           # Module system
├── kernel-core/           # Lifecycle, context
├── kernel-plugin/         # Plugin SPI
└── kernel-bom/            # Dependency versions

platform-plugins/          # Cross-cutting plugins
├── plugin-audit-trail/
├── plugin-billing-ledger/
├── plugin-compliance/
├── plugin-consent/
├── plugin-fraud-detection/
└── plugin-risk-management/
```

### 12.3 Critical Java Patterns

#### Repository Pattern (Canonical)

```java
// ✅ CORRECT: Extend platform canonical Repository
// In platform:java:database
public interface Repository<T, ID> {
    Promise<Optional<T>> findById(ID id);
    Promise<T> save(T entity);
    Promise<Void> deleteById(ID id);
}

// In product module - extend, don't redefine
public interface OrderRepository extends Repository<Order, OrderId> {
    Promise<List<Order>> findByCustomerId(CustomerId id);
}
```

**NEVER define a new Repository interface in a product module.**

#### Event Pattern (Canonical)

```java
// ✅ CORRECT: Extend platform DomainEvent
// In platform:java:domain
public abstract class DomainEvent {
    private final EventId id;
    private final Instant occurredOn;
    private final TenantId tenantId;
}

// In product module - extend, don't redefine
public class OrderCreatedEvent extends DomainEvent {
    private final OrderId orderId;
    private final CustomerId customerId;
}
```

**NEVER create a new event base class in a product module.**

#### JSON Utilities (Canonical)

```java
// ✅ CORRECT: Use platform JsonUtils
import com.ghatana.platform.core.util.JsonUtils;

String json = JsonUtils.toJson(object);
MyType obj = JsonUtils.fromJson(json, MyType.class);
```

**NEVER create a local JsonUtils class.**

### 12.4 Prohibited Java Patterns

```java
// ❌ NEVER: Product module defining own Repository interface
public interface ProductRepository { ... } // WRONG - use platform:database

// ❌ NEVER: Product module defining own event base class
public abstract class ProductEvent { ... } // WRONG - extend DomainEvent

// ❌ NEVER: Local JsonUtils implementation
public class JsonUtils { ... } // WRONG - use platform:core

// ❌ NEVER: Platform module depending on Product
// In platform:java:security build.gradle.kts:
dependencies {
    api(project(":products:yappc:libs:java:yappc-domain")) // VIOLATION
}

// ❌ NEVER: Product-specific logic in shared module
// In platform:java:security:
public class YAPPCSecurityFilter { ... } // VIOLATION - belongs in YAPPC

// ❌ NEVER: Framework types in public API without abstraction
// In platform module public API:
public Promise<HttpResponse> handle(HttpRequest request); // Leaks ActiveJ
```

### 12.5 Java Canonical Ownership Reference

| Concept | Canonical Module | Never Duplicate |
|---------|------------------|-----------------|
| Repository interface | `platform:java:database` | ❌ |
| DomainEvent base class | `platform:java:domain` | ❌ |
| JsonUtils | `platform:java:core` | ❌ |
| Promise/async | `platform:java:core` (ActiveJ) | ❌ |
| TenantId, Offset | `platform:java:core` | ❌ |
| Agent contracts | `platform:java:agent-core` | ❌ |
| Test fixtures | `platform:java:testing` | ❌ |
| Protobuf schemas | `platform:contracts` | ❌ |
| Plugin SPI | `platform-kernel:kernel-plugin` | ❌ |
| Auth/Security | `platform:java:security` | ❌ |
| Observability | `platform:java:observability` | ❌ |

### 12.6 Java Dependency Rules

```kotlin
// ✅ CORRECT: Product depends on platform
dependencies {
    api(project(":platform:java:core"))
    api(project(":platform:java:domain"))
    api(project(":platform:java:database"))
}

// ✅ CORRECT: Platform module minimal dependencies
dependencies {
    api(project(":platform:java:core"))  // Foundation only
    // No product dependencies
}

// ❌ NEVER: Circular dependency
// Module A -> Module B -> Module A

// ❌ NEVER: Deep dependency chains
// Module -> 5+ transitive dependencies
```

### 12.7 Java Module Health Checklist

Before creating or modifying Java modules:

- [ ] Check existing platform modules for similar functionality
- [ ] Verify dependency direction (Product→Platform only)
- [ ] Ensure single clear purpose (one sentence description)
- [ ] Public API in `*.api` package
- [ ] No framework types leaked in public API
- [ ] Extends canonical interfaces (Repository, DomainEvent)
- [ ] Uses canonical utilities (JsonUtils from core)
- [ ] No product-specific logic in platform modules
- [ ] Tests use `platform:java:testing` fixtures
- [ ] Documentation tags present (`@doc.type`, `@doc.purpose`)

### 12.8 Java Key Decisions Log

| Date | Decision | Rationale |
|------|----------|-----------|
| 2026-04-10 | Repository pattern owned by `platform:database` | Single source of truth |
| 2026-04-10 | Event base classes in `platform:domain` | Serialization consistency |
| 2026-04-10 | Merge `platform:cache` into `platform:database` | Cache is persistence concern |
| 2026-04-10 | Merge `platform:identity` into `platform:security` | Identity is security concern |
| 2026-04-10 | AEP runtime modules merged (4→1) | Eliminate sprawl |
| 2026-04-10 | Remove `.archived/` kernel after grace period | Clean codebase |

---

## 13. Related Documents

- `LIBRARY_GOVERNANCE.md` - Creation criteria and review process
- `LIBRARY_REFACTORING_IMPLEMENTATION_PLAN_DONE.md` - Detailed implementation steps
- `PACKAGE_NAMING_STANDARD.md` - Naming conventions
- `JAVA_LIBRARIES_AND_MODULES_AUDIT_REPORT.md` - Java module audit (April 2026)
- This file (`copilot-instructions.md`) - AI assistant guidance

---

**Enforcement**: These instructions are mandatory for all AI-assisted library changes. Violations must be flagged and corrected before merge.
