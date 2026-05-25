# Digital Marketing Operating System (DMOS) — Implementation Plan

**Status:** MVP Pilot
**Lifecycle stage:** Local pilot — `validate`, `test`, `build`, `package`, `deploy:local`, `verify:local`
**Target:** Production-track marketing platform pilot under Ghatana Kernel lifecycle governance
**Classification:** Canonical implementation plan. **Evidence:** `products/digital-marketing/kernel-product.yaml`, `.kernel/evidence/digital-marketing/digital-marketing-lifecycle-evidence-pack.json`

---

## Pilot Identity

| Attribute                   | Value                                                                                           |
| --------------------------- | ----------------------------------------------------------------------------------------------- |
| Product ID                  | `digital-marketing`                                                                             |
| Kind                        | `business-product`                                                                              |
| Lifecycle status            | `enabled` — Existing and executable. Evidence: `products/digital-marketing/kernel-product.yaml` |
| Lifecycle execution allowed | `true`                                                                                          |
| Deployment target           | `compose-local`                                                                                 |
| Surfaces                    | `backend-api` (Gradle Java), `web` (pnpm Vite React)                                            |
| Bridge module               | `products/digital-marketing/dm-kernel-bridge`                                                   |

---

## Capability Status

### Implemented (MVP)

| Capability                                 | Module                    | Notes                                                                          |
| ------------------------------------------ | ------------------------- | ------------------------------------------------------------------------------ |
| Core typed IDs and operation context       | `dm-core-contracts`       | `DmOperationContext`, actor/context propagation                                |
| Domain model and aggregates                | `dm-domain`               | Campaign, workspace, approval, AI action log                                   |
| Boundary policy rules and compliance packs | `dm-domain-packs`         | Plugin startup bindings, pack validations                                      |
| Kernel bridge adapter                      | `dm-kernel-bridge`        | Feature flags, audit, compliance, human-approval plugin                        |
| Application services (scaffold)            | `dm-application`          | Campaign, strategy, budget, approval, content, connector, workflow, AI actions |
| In-memory adapters (dev/test)              | `dm-infra`                | `ConcurrentHashMap`-backed; production uses PostgreSQL                         |
| Google Ads connector (OkHttp)              | `dm-connector-google-ads` | OAuth, campaign creation, performance retrieval (REST API v14)                 |
| Backend API (ActiveJ HTTP)                 | `dm-api`                  | Rate limiting, correlation ID, tenant header enforcement                       |
| Web UI (React 19)                          | `ui`                      | Login, dashboard, approvals; E2E pending                                       |

### Partial / Planned

| Capability                           | Status   | Blocking Factor                                                       |
| ------------------------------------ | -------- | --------------------------------------------------------------------- |
| Production persistence (PostgreSQL)  | Partial  | `dm-persistence` Flyway migrations ready; adapter integration partial |
| CRM connector                        | Scaffold | Interface defined, implementation pending                             |
| Meta Ads connector                   | Scaffold | Interface defined, implementation pending                             |
| E2E Playwright tests                 | Partial  | Config exists; full campaign workflow E2E pending                     |
| `dm-application` production adapters | Blocked  | `ProductionBootstrapValidator` P0-006 guard active                    |

---

## Kernel Bridge Adapter

The `dm-kernel-bridge` module is the product-owned adapter over Kernel bridge ports:

| Bridge Capability        | Implementation                                                              |
| ------------------------ | --------------------------------------------------------------------------- |
| Authorization (OPA)      | `OpaAuthorizationService` — typed request/response, explicit error handling |
| Consent enforcement      | `DigitalMarketingKernelAdapterImpl` — marketing consent boundary            |
| Campaign approval        | Human-approval plugin integration                                           |
| Audit logging            | Campaign event sourcing adapter                                             |
| Notification retry + DLQ | `NotificationRetryAndDlqTest` covers 3-retry behavior                       |
| Workspace region routing | `DmosWorkspaceRegionRouter`                                                 |
| AI model governance      | `DmosAiModelGovernanceAdapter`                                              |
| Risk evaluation          | `DmosRiskEvaluatorRegistrar`                                                |
| Workflow engine          | `DmWorkflowEngineAdapter`                                                   |

Bridge compliance is validated by: `pnpm check:bridge-compliance` and `./gradlew :products:digital-marketing:dm-kernel-bridge:test`.

---

## Policy Packs

DMOS has the following product-configured policy packs (configured by DMOS, not implemented in Kernel):

| Policy Pack                | Description                                         |
| -------------------------- | --------------------------------------------------- |
| Web/API security baseline  | Standard security controls for web + API surfaces   |
| Container image integrity  | Image signing and provenance verification           |
| Customer data minimization | PII minimization for marketing data                 |
| Marketing consent boundary | Consent enforcement for campaign audience targeting |

---

## Lifecycle Commands

All lifecycle execution uses the Kernel product runner. DMOS must not implement its own lifecycle runner.

```bash
pnpm plan:validate:digital-marketing  &&  pnpm validate:digital-marketing
pnpm plan:test:digital-marketing      &&  pnpm test:digital-marketing
pnpm plan:build:digital-marketing     &&  pnpm build:digital-marketing
pnpm plan:package:digital-marketing   &&  pnpm package:digital-marketing
pnpm plan:deploy:local:digital-marketing   &&  pnpm deploy:local:digital-marketing
pnpm plan:verify:local:digital-marketing   &&  pnpm verify:local:digital-marketing
```

| Phase          | What it does                                                                          |
| -------------- | ------------------------------------------------------------------------------------- |
| `validate`     | Runs type-check and compilation for backend and web surfaces                          |
| `test`         | Runs all unit and integration tests including bridge compliance                       |
| `build`        | Produces deployable artifacts (JAR for backend, static bundle for web)                |
| `package`      | Builds Docker images via `docker-buildx` adapter                                      |
| `deploy:local` | Deploys via Docker Compose to local environment                                       |
| `verify:local` | Runs health checks against `/health/live` and `/health/ready` (backend) and `/` (web) |

---

## Approvals

Lifecycle promote, deploy, and rollback phases require approvals:

| Operation              | Approval required                               |
| ---------------------- | ----------------------------------------------- |
| Deploy to production   | Yes — human approval via kernel approval plugin |
| Promote beyond staging | Yes — architecture team sign-off                |
| Rollback               | Yes — incident commander approval               |

---

## Evidence Output Paths

Lifecycle evidence is generated under:

```text
.kernel/out/products/digital-marketing/<phase>/latest/
.kernel/evidence/digital-marketing/<runId>/
```

Bridge compliance evidence is in:

```text
products/digital-marketing/dm-kernel-bridge/src/test/java/
```

---

## Rollout Criteria (Local Pilot)

DMOS is rollout-ready for local pilot only when:

1. `validate`, `test`, `build` phases all succeed
2. Bridge compliance check passes
3. Campaign workflow tests pass (create/update/approve/reject cases)
4. `deploy:local` + `verify:local` succeed or report `NOT_READY` with actionable reason
5. Lifecycle evidence pack generated under `.kernel/evidence/digital-marketing/<runId>/`
6. No DMOS-specific lifecycle code leaks into Kernel
7. Connector missing credentials reports `NOT_READY`, not fake success

---

## Promotion Criteria (Beyond Local Pilot)

- `dm-application` production adapters released (P0-006 guard lifted)
- PostgreSQL adapters from `dm-persistence` fully integrated
- CRM and Meta Ads connectors implemented with real credential validation
- Full E2E Playwright campaign workflow suite passing in CI
- Production deployment manifest reviewed by infrastructure team
- Customer data minimization compliance audit completed
