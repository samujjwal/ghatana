# YAPPC Documentation — Canonical Index

> **F-Y039 / K-Y16 resolution** — single entry point for all YAPPC documentation.
> 
> All significant docs MUST be linked from this index.  
> The canonical docs tree is `products/yappc/docs/`.  
> Scattered docs in submodules should be either moved here or linked.

---

## Start Here

| Document | Purpose |
|---|---|
| [README.md](README.md) | Product overview, quick start |
| [START_HERE_ARCHITECTURE.md](START_HERE_ARCHITECTURE.md) | Architecture entry point |
| [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) | Developer onboarding |
| [ONBOARDING.md](onboarding/) | Full onboarding guide |

---

## Architecture

| Document | Purpose |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Top-level architecture |
| [CORE_ARCHITECTURE.md](CORE_ARCHITECTURE.md) | Core engine architecture |
| [DESIGN_ARCHITECTURE.md](DESIGN_ARCHITECTURE.md) | Design decisions |
| [architecture/](architecture/) | Architecture ADRs and diagrams |
| [EVENT_DRIVEN_ARCHITECTURE.md](EVENT_DRIVEN_ARCHITECTURE.md) | Event streaming design |
| [MODULE_CATALOG.md](MODULE_CATALOG.md) | All YAPPC modules |
| [DOMAIN_MODEL_REGISTRY.md](../DOMAIN_MODEL_REGISTRY.md) | Entity domain model registry *(root-level, to be moved here)* |

---

## API & Contracts

| Document | Purpose |
|---|---|
| [api/openapi.yaml](api/openapi.yaml) | REST OpenAPI spec (canonical) |
| [API_STANDARDIZATION_GUIDE.md](API_STANDARDIZATION_GUIDE.md) | API design guide |
| [API_SURFACE_CANONICALIZATION.md](API_SURFACE_CANONICALIZATION.md) | GraphQL ↔ REST canonical surface map |
| [adr/ADR-GENERATED-API-CLIENTS.md](adr/ADR-GENERATED-API-CLIENTS.md) | Generated clients only (SIMP-Y19) |
| [adr/](adr/) | All Architecture Decision Records |

---

## Package Governance

| Document | Purpose |
|---|---|
| [../frontend/YAPPC_PACKAGE_CLASSIFICATION.md](../frontend/YAPPC_PACKAGE_CLASSIFICATION.md) | `@yappc/*` package classification |
| [../frontend/web/src/test-utils/TESTING_PATTERNS.md](../frontend/web/src/test-utils/TESTING_PATTERNS.md) | Canonical test scaffolding pattern |

---

## Data and Persistence

| Document | Purpose |
|---|---|
| [PERSISTENCE_OWNERSHIP.md](PERSISTENCE_OWNERSHIP.md) | Per-entity persistence ownership matrix |
| [database/](database/) | Database schemas and migration guides |
| [BACKUP_RESTORE_STRATEGY.md](BACKUP_RESTORE_STRATEGY.md) | Backup and restore strategy |

---

## Security & Auth

| Document | Purpose |
|---|---|
| [JWT_AUTHENTICATION.md](JWT_AUTHENTICATION.md) | JWT authentication design |
| [JWT_HTTPONLY_COOKIE_MIGRATION_PLAN.md](JWT_HTTPONLY_COOKIE_MIGRATION_PLAN.md) | Cookie migration plan |
| [security/](security/) | Security docs and threat models |

---

## Agent System

| Document | Purpose |
|---|---|
| [01-extended-agent-catalog.md](01-extended-agent-catalog.md) | All agents |
| [02-persona-to-agent-matrix.md](02-persona-to-agent-matrix.md) | Persona ↔ agent mapping |
| [03-lifecycle-phase-to-agent-matrix.md](03-lifecycle-phase-to-agent-matrix.md) | Phase ↔ agent mapping |
| [06-agent-execution-contract.md](06-agent-execution-contract.md) | Agent execution contract |
| [PERSISTENT_AGENT_REGISTRY.md](PERSISTENT_AGENT_REGISTRY.md) | Agent registry |
| [LLM_INTEGRATION_GUIDE.md](LLM_INTEGRATION_GUIDE.md) | LLM integration |
| [LLM_OBSERVABILITY.md](LLM_OBSERVABILITY.md) | LLM observability |
| [05-platform-mapping-yappc-aep.md](05-platform-mapping-yappc-aep.md) | AEP integration map |

---

## Testing

| Document | Purpose |
|---|---|
| [TESTING.md](TESTING.md) | Testing strategy |
| [testing/](testing/) | Test guides and fixtures |
| [../frontend/web/src/test-utils/TESTING_PATTERNS.md](../frontend/web/src/test-utils/TESTING_PATTERNS.md) | Frontend test scaffolding |

---

## Operations & Deployment

| Document | Purpose |
|---|---|
| [OPERATIONS.md](OPERATIONS.md) | Operations guide |
| [deployment/](deployment/) | Deployment guides |
| [RELEASE_READINESS_CHECKLIST.md](RELEASE_READINESS_CHECKLIST.md) | Release checklist |
| [PRODUCT_BUILD_ISOLATION.md](PRODUCT_BUILD_ISOLATION.md) | Build isolation strategy |

---

## Scattered Docs — To Be Consolidated

The following documents are outside `docs/` and should be moved here in a follow-up PR:

| Current Location | Target |
|---|---|
| `products/yappc/ARTIFACT_COMPILER_IMPLEMENTATION_PLAN.md` | `docs/implementation-plans/` |
| `products/yappc/DOMAIN_MODEL_REGISTRY.md` | `docs/DOMAIN_MODEL_REGISTRY.md` |
| `products/yappc/core/yappc-api/TEST_*.md` | `docs/audits/` |
| `products/yappc/core/cli-tools/docs/` | `docs/guides/cli/` |
| `products/yappc/tools/vscode-extension/*.md` | `docs/guides/vscode-extension/` |

---

## Auto-Publishing (Future)

Tracked by F-Y039. Planned tooling: MkDocs or Docusaurus CI job that publishes this tree on merge to main.

---

## Frontend Implementation Docs (Frontend-Local)

These documents live in `products/yappc/frontend/web/docs/` and cover frontend-only implementation concerns.
They are **not** product-wide docs — they are scoped to the web frontend implementation.
New product-wide docs must go in `products/yappc/docs/`, not in this sub-tree.

| Document | Purpose |
|---|---|
| [../frontend/web/docs/README.md](../frontend/web/docs/README.md) | Frontend web overview |
| [../frontend/web/docs/CANVAS_IMPLEMENTATION.md](../frontend/web/docs/CANVAS_IMPLEMENTATION.md) | Canvas system implementation |
| [../frontend/web/docs/DESIGN_ARCHITECTURE.md](../frontend/web/docs/DESIGN_ARCHITECTURE.md) | Frontend design decisions |
| [../frontend/web/docs/architecture/](../frontend/web/docs/architecture/) | Frontend architecture diagrams |
| [../frontend/web/docs/guidelines/](../frontend/web/docs/guidelines/) | Coding guidelines (frontend) |
| [../frontend/web/docs/operations/](../frontend/web/docs/operations/) | Frontend ops playbooks |
| [../frontend/web/docs/usage/](../frontend/web/docs/usage/) | Component usage guides |
| [../frontend/web/docs/route-inventory.md](../frontend/web/docs/route-inventory.md) | All frontend routes |

### Doc Ownership Rules

| Type of doc | Goes in |
|---|---|
| Product-wide architecture, API contracts, ADRs, agent docs | `products/yappc/docs/` |
| Frontend implementation details, component guides, route lists | `products/yappc/frontend/web/docs/` |
| Module-local README | Co-located with the module (`<module>/README.md`) |
| Never in | `products/yappc/frontend/apps/api/docs/` (no free-form docs there — use `products/yappc/docs/`) |
