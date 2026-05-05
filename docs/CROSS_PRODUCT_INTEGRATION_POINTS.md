# Allowed Integration Points — Data Cloud ↔ AEP

> **Status:** Canonical
> **Enforced by:** `scripts/check-cross-workspace-deps.mjs` + ArchUnit tests
> **Last Updated:** 2026-05-02

---

## Rule

Data Cloud and AEP are **sibling products**. They must not import each other's
internal packages directly (no `workspace:*` peer imports between product trees,
no Java imports crossing product boundaries).

All legitimate cross-product communication must flow through one of the
**explicitly allowed integration points** listed below.

---

## Allowed Integration Points

### 1. Shared Platform Contracts (`platform/contracts/`)

Data Cloud and AEP both depend on platform contracts. Neither product depends on
the other's internal model layer.

| Contract | Used By | Description |
|---|---|---|
| `capability-schema/aep-capabilities.v1.json` | AEP UI, AEP gateway | Canonical capability manifest |
| `platform/java/core/` domain primitives | Both | Tenant context, common value objects |
| `platform/java/http/` | Both | HTTP client / server abstractions |

### 2. Data Cloud Public REST API

AEP may call Data Cloud only via Data Cloud's public REST API:

| Method | Path | Caller | Purpose |
|---|---|---|---|
| `GET` | `/api/v1/collections` | AEP gateway / agents | List available collections |
| `GET` | `/api/v1/collections/{id}` | AEP gateway | Fetch collection details |
| `POST` | `/api/v1/collections/{id}/query` | AEP agents | Query collection data |
| `GET` | `/api/v1/workflows/templates` | AEP pipeline builder | List workflow templates |
| `POST` | `/api/v1/workflows/runs` | AEP orchestrator | Trigger a Data Cloud workflow |
| `GET` | `/api/v1/workflows/runs/{id}` | AEP orchestrator | Poll run status |
| `GET` | `/api/v1/fabric/topology` | AEP fabric view | Read Data Fabric topology |

**AEP must NOT call any `/internal/` or non-versioned Data Cloud endpoints.**

### 3. Platform Event Bus (`@ghatana/events` / AEP event infrastructure)

Data Cloud publishes platform events that AEP agents may subscribe to:

| Event Type | Publisher | Consumer |
|---|---|---|
| `WorkflowRunCompleted` | Data Cloud | AEP orchestrator (trigger downstream agents) |
| `WorkflowStepCompleted` | Data Cloud | AEP monitoring (update operation center) |
| `CollectionUpdated` | Data Cloud | AEP cache invalidation |
| `FabricAnomalyDetected` | Data Cloud | AEP alert system |

AEP must not publish events that Data Cloud subscribes to without an explicit
cross-product design review.

### 4. TypeScript Shared Packages

The AEP UI and Data Cloud UI both depend on canonical platform TypeScript
packages. They must not import from each other's product UI trees.

| Allowed | Forbidden |
|---|---|
| `@ghatana/design-system` | `@ghatana/data-cloud-*` |
| `@ghatana/platform-utils` | `@ghatana/aep-*` |
| `@ghatana/api` | Any `products/data-cloud/ui/src/...` import |
| `@ghatana/state` | Any `products/aep/ui/src/...` import |

---

## Forbidden Patterns

1. **Direct package dependency** between product trees in `pnpm-workspace.yaml`
   (e.g., `aep-ui` declaring `workspace:*` on `data-cloud-ui`).
2. **Java import crossing product boundary** (e.g.,
   `import com.ghatana.datacloud.*` inside `products/aep/`).
3. **Shared in-process service objects** — no Data Cloud Spring/ActiveJ bean
   injected into an AEP bean or vice versa.
4. **Shared database schema** — each product owns its own Postgres schema.
   Cross-product reads via SQL joins are forbidden; use the REST API.

---

## Enforcement

| Check | Where | How |
|---|---|---|
| TypeScript cross-product imports | CI | `node scripts/check-cross-workspace-deps.mjs` |
| Java cross-product imports | CI | ArchUnit `CrossProductArchTest` |
| Event schema drift | CI | Contract test in `integration-tests/` |
| REST API surface drift | CI | OpenAPI diff gate (ARCH-P1-002) |

---

## How to Add a New Integration Point

1. Open a design issue labelled `cross-product-integration`.
2. Agree on the protocol (REST, event, or shared contract).
3. Update this document and the relevant OpenAPI / event schema.
4. Add an integration test in `integration-tests/cross-product/`.
5. Update `scripts/check-cross-workspace-deps.mjs` allowlist if needed.
