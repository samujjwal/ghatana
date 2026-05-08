# Data Cloud SPI vs Platform/Kernel Boundary Guide

This document defines what belongs in each layer and where to put new code.

---

## Layers

```
platform/java/*          ← generic, product-agnostic infrastructure
products/data-cloud/     ← Data Cloud product code
  planes/shared-spi/     ← Data Cloud SPI (contracts between planes)
  planes/*/              ← plane-specific implementations
  delivery/              ← runtime composition
platform-kernel/         ← low-level kernel plugin infrastructure
```

---

## Decision Table

| Concern | Where It Lives | Rationale |
| --- | --- | --- |
| Generic HTTP client/server primitives | `platform/java/http` | Product-agnostic |
| Generic async Promise utilities | `platform/java/core` | Product-agnostic |
| Generic observability (metrics, traces) | `platform/java/observability` | Product-agnostic |
| Generic security primitives (JWT, RBAC) | `platform/java/security` | Product-agnostic |
| Generic database utilities (HikariCP, Flyway) | `platform/java/database` | Product-agnostic |
| Generic AI integration contracts | `platform/java/ai-integration` | Product-agnostic |
| Data Cloud entity/event contracts | `planes/shared-spi` | DC-specific, cross-plane |
| Data Cloud tenant isolation model | `planes/shared-spi` | DC-specific |
| Data Cloud entity store implementations | `planes/data/entity` | DC-specific implementation |
| Data Cloud event log implementations | `planes/event/*` | DC-specific implementation |
| Data Cloud analytics / scoring | `planes/intelligence/analytics` | DC-specific |
| Data Cloud HTTP routing + handlers | `delivery/runtime-composition` | DC composition layer |
| Data Cloud audit integration | `platform/java/audit` | Shared audit, DC uses it |
| Kernel plugin lifecycle | `platform-kernel/kernel-plugin` | Kernel-owned |

---

## Rules

### What MUST go in `platform/java/*`

- The code has zero references to any `com.ghatana.datacloud` class.
- The code is already used by, or clearly useful to, two or more products without modification.
- The code manages infrastructure concerns (HTTP, DB, security, observability, config, AI transport).

### What MUST go in `planes/shared-spi`

- Contracts (interfaces, records, enums) shared by two or more Data Cloud planes.
- The `DataCloudClient` facade.
- `TenantContext`, `Offset`, `TailRequest`, `Event`, `EntityQuery`, and similar domain value types.
- **Never** implementations. `shared-spi` is contracts only — no `Impl` classes.

### What MUST go in `planes/<plane>/*`

- Implementations of `shared-spi` contracts specific to that plane.
- e.g., `InMemoryEntityStore`, `H2EntityStore`, `RocksDbEventLogStore` live in their respective plane modules.

### What MUST go in `delivery/runtime-composition`

- Wiring: assembling platform modules + SPI implementations into a running server.
- HTTP handler registration.
- Profile-aware store/provider selection.
- Nothing that other modules need to depend on — this module is a leaf node.

### What MUST stay in `platform-kernel`

- Plugin lifecycle hooks (`KernelPlugin`, `KernelPluginContext`).
- Cross-product plugin bus.
- Data Cloud uses kernel plugins but does not own kernel code.

---

## Anti-Patterns

| Anti-Pattern | Correct Action |
| --- | --- |
| Adding `DataCloudClient` to `platform/java/*` | Keep in `planes/shared-spi` — it's DC-specific |
| Putting an entity store `Impl` in `shared-spi` | Move to the correct `planes/*` module |
| Importing `platform-kernel` from a Data Cloud plane | Only `delivery/runtime-composition` may depend on kernel |
| Creating a new generic HTTP abstraction in `delivery` | Add to `platform/java/http` instead |
| Referencing product-specific types in `platform/java/*` | Product-specific code must stay in `products/data-cloud/` |

---

## Adding New Code

1. Is it product-agnostic? → `platform/java/<concern>`
2. Is it a cross-plane DC contract? → `planes/shared-spi`
3. Is it a plane implementation? → `planes/<plane>/<module>`
4. Is it runtime wiring? → `delivery/runtime-composition`
5. Is it a kernel concern? → `platform-kernel` (coordinate with kernel owners)
