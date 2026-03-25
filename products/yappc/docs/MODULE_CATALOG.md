# YAPPC Module Catalog

**Status:** Authoritative  
**Last Updated:** 2026-03-24  
**Owner:** Architecture Team

This is the single source of truth for every active YAPPC module. Every module has
an explicit architectural role. Modules not listed here are either retired, merged into
another module, or pending removal.

---

## Module Roles

| Role | Description | Dependency rules |
|------|-------------|-----------------|
| **app** | Deployable runtime entry point. Wires all dependencies, exposes a running process. | May depend on all roles except `app`. |
| **domain** | Internal domain implementation — services, orchestration, repositories. Not for external consumers. | May depend on `contract`, `support`, `platform`. Must NOT depend on `adapter` or `app`. |
| **contract** | Stable public API surface: value types, interfaces, events, DTOs. Consumed by multiple modules. | May depend on `platform` contracts only. |
| **capability** | Implements a specific product capability (AI, Agents, Scaffold, etc.). | May depend on `domain`, `contract`, `support`, `platform`. Must NOT directly import `adapter` internals — use port interfaces. |
| **adapter** | Binds an external product (AEP, DataCloud, HTTP) to a YAPPC port. | May depend on external products. Must NOT be depended on by `domain` or `capability`. |
| **support** | Cross-cutting utilities shared across YAPPC (not domain-specific). | May depend on `platform`. Must NOT depend on `domain`, `capability`, or `adapter`. |
| **compat** | Deprecated shim. Exists only for backwards compatibility during migration. | Will be removed. Do not add new code here. |
| **platform** | Monorepo platform libraries — do NOT modify from within YAPPC. | N/A |

---

## Backend Modules

### Application Entry Points (role: `app`)

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:services` | `services/` | **app** | **The only deployable.** Has `application` plugin. Wires all sub-modules together. |

---

### Core Reusable Service Libraries (role: `domain`)

> **Rule**: These modules were moved from `services/` to `core/` in 2026-03-24 (Phase 2)
> to make the `services/` directory contain only the deployable app.

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:core:services-platform` | `core/services-platform/` | **domain** | HTTP platform wiring, application-facing service composition. Was: `services:platform`. |
| `:products:yappc:core:services-lifecycle` | `core/services-lifecycle/` | **domain** | SDLC lifecycle orchestration, AEP integration, startup/shutdown. Was: `services:lifecycle`. |

---

### Core Domain Modules (role: `domain`)

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:core:yappc-domain-impl` | `core/yappc-domain-impl/` | **domain** | Internal domain model: task types, agent types, workflow, vector services. **NOT** for external consumers. Renamed from `core:yappc-domain` in 2026-03-24. |
| `:products:yappc:core:yappc-services` | `core/yappc-services/` | **domain** | Business orchestration services: task dispatch, agent orchestration, project lifecycle. |
| `:products:yappc:core:yappc-infrastructure` | `core/yappc-infrastructure/` | **domain** | Repository implementations, Data-Cloud adapters, infrastructure binding. |
| `:products:yappc:core:yappc-agents` | `core/yappc-agents/` | **domain** | High-level agent façade consumed by services. Aggregates specialist agent modules. |
| `:products:yappc:core:yappc-api` | `core/yappc-api/` | **domain** | Internal API contracts and request/response types for YAPPC HTTP surface. |
| `:products:yappc:core:yappc-shared` | `core/yappc-shared/` | **support** | Shared internal utilities used across yappc-* modules. |

---

### Public Contracts (role: `contract`)

> **Rule**: There is exactly ONE canonical domain contract module: `libs:java:yappc-domain`.
> Do NOT add a dependency on `core:yappc-domain-impl` from outside YAPPC.

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:libs:java:yappc-domain` | `libs/java/yappc-domain/` | **contract** | **Canonical public contract.** Domain models, enums, DTOs, events, repository interfaces shared across YAPPC and peer products. |

---

### Capability Modules (role: `capability`)

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:core:ai` | `core/ai/` | **capability** | LLM integration: prompt templates, model clients, completion strategies. |
| `:products:yappc:core:agents` | `core/agents/` | **capability** | Aggregator for all agent modules. Exposes unified agent API. |
| `:products:yappc:core:agents:runtime` | `core/agents/runtime/` | **capability** | Agent execution runtime (ActiveJ). ⚠️ `TODO(ADAPTER-SEAM)`: direct AEP dep. |
| `:products:yappc:core:agents:workflow` | `core/agents/workflow/` | **capability** | Multi-step SDLC workflow engine. ⚠️ `TODO(ADAPTER-SEAM)`: direct data-cloud dep. |
| `:products:yappc:core:agents:common` | `core/agents/common/` | **capability** | Shared agent utilities and base types. |
| `:products:yappc:core:agents:code-specialists` | `core/agents/code-specialists/` | **capability** | Code analysis, language experts, debug agents. |
| `:products:yappc:core:agents:delivery-specialists` | `core/agents/delivery-specialists/` | **capability** | Release, DevOps, compliance, security agents. |
| `:products:yappc:core:agents:architecture-specialists` | `core/agents/architecture-specialists/` | **capability** | Architecture review and governance agents. |
| `:products:yappc:core:agents:testing-specialists` | `core/agents/testing-specialists/` | **capability** | Testing strategy and quality agents. |
| `:products:yappc:core:scaffold` | `core/scaffold/` | **capability** | Project scaffolding sub-system root. |
| `:products:yappc:core:scaffold:api` | `core/scaffold/api/` | **capability** | Scaffold public API contracts. |
| `:products:yappc:core:scaffold:core` | `core/scaffold/core/` | **capability** | Scaffold aggregator (re-exports templates + engine + generators). |
| `:products:yappc:core:scaffold:templates` | `core/scaffold/templates/` | **capability** | Template models, errors, IO, RCA, docs. |
| `:products:yappc:core:scaffold:engine` | `core/scaffold/engine/` | **capability** | AI, cache, config, telemetry orchestration. |
| `:products:yappc:core:scaffold:generators` | `core/scaffold/generators/` | **capability** | Language generators, pack/plugin/CI generators. |
| `:products:yappc:core:knowledge-graph` | `core/knowledge-graph/` | **capability** | Knowledge graph engine. ⚠️ `TODO(ADAPTER-SEAM)`: direct data-cloud dep. |
| `:products:yappc:core:refactorer:api` | `core/refactorer/api/` | **capability** | Refactoring request/response contracts. |
| `:products:yappc:core:refactorer:engine` | `core/refactorer/engine/` | **capability** | Code analysis and transformation engine. |
| `:products:yappc:core:spi` | `core/spi/` | **capability** | Extension SPI — pluggable capability points. |
| `:products:yappc:core:cli-tools` | `core/cli-tools/` | **capability** | CLI tooling utilities. |

---

### Adapter Modules (role: `adapter`)

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:infrastructure:datacloud` | `infrastructure/datacloud/` | **adapter** | Data-Cloud SPI binding. Owns all `products:data-cloud:*` imports for YAPPC. |

> **TODO(Phase 4)**: An `infrastructure:aep` adapter module should be introduced to own all
> `products:aep:*` imports currently scattered across capability modules. See `TODO(ADAPTER-SEAM)`
> annotations in build files.

---

### Platform Modules

| Module path | Directory | Role | Notes |
|-------------|-----------|------|-------|
| `:products:yappc:platform` | `platform/` | **platform** | YAPPC platform bootstrap — DO NOT MODIFY from products. |

---

## Frontend Packages

### Canonical Packages (first-class, in `frontend/libs/`)

| Package name | Directory | Notes |
|--------------|-----------|-------|
| `@yappc/ui` | `frontend/libs/yappc-ui/` | Primary UI component library. |
| `@yappc/canvas` | `frontend/libs/yappc-canvas/` | Canvas/whiteboard components. |
| `@yappc/ai` | `frontend/libs/yappc-ai/` | AI interaction components. |
| `@yappc/state` | `frontend/libs/yappc-state/` | Jotai + TanStack Query state management. |
| `@yappc/core` | `frontend/libs/yappc-core/` | Shared utilities, hooks, types. |

### Product-Specific Libraries (active, in `frontend/libs/`)

These are active product libraries that are not "canonical framework packages" but are
still actively used. They should eventually be consolidated into the canonical packages above.

| Package name | Directory |
|--------------|-----------|
| `@yappc/api` | `frontend/libs/api/` |
| `@yappc/auth` | `frontend/libs/auth/` |
| `@yappc/chat` | `frontend/libs/chat/` |
| `@yappc/code-editor` | `frontend/libs/code-editor/` |
| `@yappc/collab` | `frontend/libs/collab/` |
| `@yappc/config` | `frontend/libs/config/` |
| `@yappc/ide` | `frontend/libs/ide/` |
| `@yappc/mobile` | `frontend/libs/mobile/` |
| `@yappc/mocks` | `frontend/libs/mocks/` |
| `@yappc/shortcuts` | `frontend/libs/shortcuts/` |
| `@yappc/testing` | `frontend/libs/testing/` |
| `@yappc/aep-config` | `frontend/libs/aep-config/` |

### Compatibility / Deprecated Packages (in `frontend/compat/`)

> **Rule**: These packages exist only for backward compatibility. Do NOT import them in new code.
> Each should import from the canonical `@yappc/*` packages above.

| Package name | Directory | Migration target |
|--------------|-----------|-----------------|
| `@yappc/base-ui` | `frontend/compat/base-ui/` | → `@yappc/ui` |
| `@yappc/theme` | `frontend/compat/theme/` | → `@yappc/ui` (theming) |
| `@yappc/development-ui` | `frontend/compat/development-ui/` | → `@yappc/ui` |
| `@yappc/initialization-ui` | `frontend/compat/initialization-ui/` | → `@yappc/ui` |
| `@yappc/navigation-ui` | `frontend/compat/navigation-ui/` | → `@yappc/ui` |
| `@yappc/messaging` | `frontend/compat/messaging/` | → `@yappc/core` |
| `@yappc/realtime` | `frontend/compat/realtime/` | → `@yappc/core` |
| `@yappc/notifications` | `frontend/compat/notifications/` | → `@yappc/ui` |
| `@yappc/config-hooks` | `frontend/compat/config-hooks/` | → `@yappc/state` |
| `@yappc/crdt` | `frontend/compat/crdt/` | → `@yappc/core` |
| `@yappc/types` | `frontend/compat/types/` | → `@yappc/core` |
| `@yappc/utils` | `frontend/compat/utils/` | → `@yappc/core` |

---

## Retired / Removed Modules

| Former module path | Retired | Reason |
|-------------------|---------|--------|
| `:products:yappc:services:platform` | 2026-03-24 | Renamed → `core:services-platform` (Phase 2) |
| `:products:yappc:services:lifecycle` | 2026-03-24 | Renamed → `core:services-lifecycle` (Phase 2) |
| `:products:yappc:core:yappc-domain` | 2026-03-24 | Renamed → `core:yappc-domain-impl` (Phase 3) |
| `:products:yappc:backend:api` | 2026-03-23 | Consolidated into core modules |
| `:products:yappc:backend:websocket` | 2026-03-23 | Consolidated into core modules |
| `:products:yappc:backend:persistence` | 2026-03-23 | Consolidated into core modules |
| `:products:yappc:backend:auth` | 2026-03-23 | Consolidated into core modules |
| `:products:yappc:services:ai` | 2026-03-23 | Merged into `services:lifecycle` |
| `:products:yappc:services:scaffold` | 2026-03-23 | Merged into `services:lifecycle` |
| `:products:yappc:core:lifecycle` | 2026-03-24 | Merged into `core:yappc-services` |
| `:products:yappc:core:framework` | 2026-03-24 | Merged into `core:yappc-infrastructure` |
| `:products:yappc:core:domain` | 2026-03-24 | Merged into `core:yappc-domain-impl` |
| `:products:yappc:core:scaffold:packs` | 2026-03-24 | Merged into `core:scaffold:core` |
| `:products:yappc:core:agents:specialists` | 2026-03-23 | Distributed to code/delivery/architecture/testing-specialists |
| `:products:yappc:infrastructure:security` | 2026-03-23 | Consolidated |

---

## Pending Structural Work

### Phase 4: Adapter Seam Introduction (TODO)

The following capability modules have direct AEP/DataCloud imports that violate
the adapter boundary rule. Each is annotated with `TODO(ADAPTER-SEAM)` in its
`build.gradle.kts`. The remediation plan:

1. Define port interfaces in YAPPC (e.g., `AgentRuntimePort`, `DataStorePort`, `OperatorCatalogPort`)
2. Create `infrastructure:aep` adapter module implementing those ports
3. Update capability modules to use port interfaces instead of direct AEP/DC imports

| Module | Violating dep | Future fix |
|--------|--------------|------------|
| `core:agents` | `aep:aep-registry`, `aep:aep-agent-runtime` | Use `AgentRegistryPort` |
| `core:agents:runtime` | `aep:aep-agent-runtime`, `data-cloud:spi`, `aep:aep-operator-contracts` | Use `AgentRuntimePort`, `DataStorePort`, `OperatorCatalogPort` |
| `core:agents:workflow` | `data-cloud:spi` | Use `DataStorePort` |
| `core:knowledge-graph` | `data-cloud:platform` | Use `DataStorePort` |
| `core:yappc-services` | `data-cloud:platform` | Use `DataStorePort` |
