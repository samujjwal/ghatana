# Doc 1 — PHR + Digital Marketing release-readiness review with Kernel/Data Cloud/foundations

## 1. Release strategy decision

Use **PHR as the first production-release hardening driver**, then **Digital Marketing as the second production-release hardening driver**.

The rule should be:

> A product release is allowed only when every Kernel, Data Cloud, plugin, shared library, contract, UI foundation, and runtime capability used by that product’s production path is production-ready for that product’s usage profile. Unused foundation capabilities may remain incomplete only if they are disabled, hidden, and not advertised as live.

This avoids blocking PHR on all future platform capabilities, while also preventing PHR from shipping on top of non-production Kernel/Data Cloud/foundation slices.

## 2. Current maturity scorecard

| Area                                    |  Score | Current state                                                                                                                                                                                                                                                                                    |
| --------------------------------------- | -----: | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| PHR product readiness                   | 6.4/10 | Strong product architecture, Kernel composition, schema contracts, release gate tasks, consent/break-glass flow. Blocked by production-safety gaps such as default `test-tenant`/`test-user`, in-memory consent cache defaults, local-only deployment target, and incomplete rollback readiness. |
| Digital Marketing readiness             | 6.8/10 | Cleaner modular architecture and stronger Kernel bridge adapter. Still local-only deployment target, coverage threshold reductions, disabled feature-flag generation, runtime module false, and unproven full UI→API→domain→persistence→connector→audit flow.                                    |
| Kernel usage readiness for PHR/DMOS     | 6.2/10 | Correct layered architecture and plugin boundary intent, but product releases need stricter evidence that used lifecycle/plugin slices are production-ready.                                                                                                                                     |
| Data Cloud usage readiness for PHR/DMOS | 6.1/10 | Correct product-family positioning: Data Cloud is the customer-facing operational data fabric and AEP is Action Plane runtime. Need stronger proof that product-used durable state, policy, evidence, and runtime truth are production-grade.                                                    |
| Foundation/platform library readiness   | 6.3/10 | Platform modules and plugin model are directionally right. Must harden only the slices used by PHR/DMOS releases first.                                                                                                                                                                          |
| Release evidence maturity               | 5.8/10 | There are release gate scripts and product registry metadata, but local-only deployment and partial rollback evidence prevent production confidence.                                                                                                                                             |
| Reusable asset inventory maturity       | 3.5/10 | Conceptually needed; not yet a first-class inventory with maturity, usage, tests, promotion path, and product lineage.                                                                                                                                                                           |

## 3. PHR findings

### What is strong

PHR is clearly intended as a Kernel-backed healthcare product. Its architecture describes FHIR R4 interoperability, Nepal healthcare compliance, consent, emergency break-glass, patient/provider/admin surfaces, AI clinical decision support, audit, and Kernel/DataCloud integration. 

The registry marks PHR as an active business product with backend and web surfaces implemented, lifecycle enabled, required healthcare gates, and lifecycle execution allowed. It also records required gates such as consent, PII classification, audit evidence, FHIR contract validation, and tenant data sovereignty. 

`PhrKernelModule` is a real composition root: it declares PHR capabilities, Kernel dependencies, optional FHIR/HL7 external dependencies, creates domain services, starts/stops lifecycle-aware services, registers HTTP/FHIR/HIE routes, and registers schema contracts for patient records, consent, medications, labs, immunizations, notes, imaging, referrals, billing, telemedicine, caregivers, and emergency access.  

The PHR Gradle build has explicit Kernel/platform/plugin dependencies and release-oriented tasks: `phrReleaseGate`, `checkApiContractConformance`, benchmark task, product-pack validation, and policy pack test patterns. 

### Production blockers

The biggest PHR blocker is that `PhrServiceBase` silently injects default `tenantId = test-tenant` and `principalId = test-user` when metadata is absent. That is acceptable only in isolated tests, not production product code. It directly conflicts with the product-family rule that production foundations must fail closed on missing tenant/principal. 

`ConsentManagementService` has strong consent behavior: distributed-cache constructor, rate limiting, grant creation/revocation, access decisions, emergency access, notifications, and audit calls. But the public convenience constructor still defaults to `InMemoryCacheAdapter`, and `PhrKernelModule` uses `new ConsentManagementService(context)` instead of explicitly requiring a production distributed cache.  

PHR’s registry deployment target is only `compose-local`, supported environment is only `local`, and rollback readiness is `target-partial`. That means PHR is not yet production-deployable even if many functional components exist. 

PHR’s build file says OpenAPI validation task was removed because the plugin is unavailable. There is a contract conformance test task, which is good, but release readiness should not depend on a removed OpenAPI gate without an equivalent enforced replacement. 

### PHR implementation plan

| Priority | Area                            | What to change                                                                                                                                                                             | Where                                                                           |
| -------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------- |
| P0       | Tenant/principal fail-closed    | Remove production fallback to `test-tenant` and `test-user`. Move test defaults into test fixtures only. Production mutations must require explicit tenant/principal from trusted context. | `products/phr/src/main/java/com/ghatana/phr/kernel/service/PhrServiceBase.java` |
| P0       | Consent cache production wiring | Require distributed cache for production. Make in-memory cache available only through test/dev profile with explicit flag and health warning.                                              | `ConsentManagementService`, `PhrKernelModule`                                   |
| P0       | PHR release profile             | Replace registry `local`-only deployment with staged targets: local, dev, staging, prod. Keep prod disabled until all evidence gates pass.                                                 | `config/canonical-product-registry.json`, `products/phr/kernel-product.yaml`    |
| P0       | Healthcare gate evidence        | Make consent, PII, audit, FHIR, and data-sovereignty gates executable and evidence-backed, not only manifest-backed.                                                                       | `products/phr/lifecycle/gate-packs/*`, `.kernel/evidence/phr/*`                 |
| P0       | Contract parity                 | Reinstate or replace OpenAPI validation with enforced route↔OpenAPI↔handler parity.                                                                                                        | `products/phr/build.gradle.kts`, PHR API tests                                  |
| P1       | Emergency access workflow       | Ensure break-glass requires post-hoc review, immutable audit, notification, and patient visibility. Add negative-path tests.                                                               | `EmergencyAccessLogService`, `EmergencyAccessReviewWorkflow`, consent tests     |
| P1       | FHIR production proof           | Add FHIR R4 golden fixtures and contract tests for patient, observation, medication, immunization, imaging, notes, referrals.                                                              | `products/phr/src/test`, schema packs                                           |
| P1       | Observability                   | Add end-to-end trace/correlation from PHR API → service → Data Cloud/Kernel → audit.                                                                                                       | PHR HTTP/API/services, platform observability                                   |
| P1       | UI readiness                    | Verify patient/provider/admin route access, consent states, empty/error/unauthorized/degraded states, i18n/a11y.                                                                           | `products/phr/apps/web`                                                         |
| P2       | Reusable extraction             | Promote PHR consent panel, audit trail, FHIR validation gate, break-glass workflow, tenant-scoped repository pattern into asset registry candidates.                                       | New Product Family Asset Registry                                               |

## 4. Digital Marketing findings

### What is strong

Digital Marketing has a clearer product-boundary document than PHR. It explicitly says DMOS owns marketing domain logic, routes, contracts, persistence, connector adapters, tests, runbooks, and dashboards, while Kernel/platform owns generic identity, feature flags, audit, approvals, telemetry, notification, compliance, and policy primitives. 

The registry marks Digital Marketing as active, lifecycle enabled, backend/web implemented, bridge conformance true, bridge adapter tests present, agent definitions true, mastery bindings true, evaluation packs true, and lifecycle execution allowed. 

`DigitalMarketingKernelAdapterImpl` is a strong product-owned adapter over Kernel/plugin ports. It constructor-injects authorization, audit, consent, approval, audit trail, risk, notification, and feature-flag plugins. It uses authorization checks before consent, approval, audit, risk, feature flag, and notification operations. It also redacts sensitive values and has production-mode fail-closed behavior for risk and notification failures.  

### Production blockers

Digital Marketing is still registry-local only: deployment target is `compose-local`, supported environment is `local`, even though the architecture describes production deployment with PostgreSQL, secrets, OTLP, rate limits, retention/DSAR, connector credentials, and production bootstrap validation.  

`dm-application` has feature flag generation disabled “temporarily due to build issues,” and application coverage gates are reduced to 60% line / 50% branch. That is not production-grade for a release candidate. 

`dm-persistence` has PostgreSQL/Flyway/Testcontainers dependencies, which is good, but coverage verification is only enabled when `CI=true`. Production release gates should not depend on environment ambiguity; release profile should enforce persistence coverage and migration validation deterministically. 

The registry says `runtimeModule: false` for Digital Marketing, which means the product is not yet a full Kernel runtime module even though it is a lifecycle pilot. 

### Digital Marketing implementation plan

| Priority | Area                       | What to change                                                                                                                                                            | Where                                                                                      |
| -------- | -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| P0       | Production release profile | Add staging/prod profiles, secrets validation, PostgreSQL/Flyway migration gate, connector credential validation, OTLP config, rollback readiness.                        | `config/canonical-product-registry.json`, `products/digital-marketing/kernel-product.yaml` |
| P0       | Feature flags              | Re-enable canonical feature flag generation and make compile/check depend on it.                                                                                          | `products/digital-marketing/dm-application/build.gradle.kts`                               |
| P0       | Coverage gate              | Restore production-grade thresholds for app/domain/bridge/persistence, or define strict per-package gates with evidence.                                                  | `dm-application`, `dm-domain`, `dm-kernel-bridge`, `dm-persistence` builds                 |
| P0       | Persistence proof          | Make migration validation, tenant/workspace query enforcement, idempotency constraints, external ID constraints, and repository integration tests required release gates. | `dm-persistence`                                                                           |
| P0       | Connector execution        | Prove Google Ads connector: OAuth validation, idempotency, retries, DLQ, compensation, external ID persistence, audit.                                                    | `dm-connector-google-ads`, `dm-application`                                                |
| P1       | Runtime module             | Decide whether DMOS needs `runtimeModule: true` for release. If yes, implement; if no, document why lifecycle execution works without it.                                 | Registry + Kernel product config                                                           |
| P1       | UI runtime truth           | Ensure UI route/cards/actions come from backend capabilities, not frontend assumptions. Add empty/error/unauthorized/locked/degraded states.                              | `products/digital-marketing/ui`                                                            |
| P1       | AI action evidence         | Persist prompt/input/model/provider/confidence/rationale/risk/policy/approval outcome.                                                                                    | `dm-domain`, `dm-application`, `dm-persistence`                                            |
| P1       | Observability              | Add trace/correlation across UI → API → app → persistence/connector → Kernel bridge → audit/notification.                                                                 | API/application/bridge                                                                     |
| P2       | Reusable extraction        | Promote approval queue, connector adapter pattern, AI transparency panel, campaign dashboard, notification retry/DLQ pattern into asset registry candidates.              | Product Family Asset Registry                                                              |

## 5. Kernel/Data Cloud/foundation usage plan for PHR and Digital Marketing

### Required production slices for PHR release

| Foundation             | Required slice                                                                                                                |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| Kernel                 | lifecycle validate/build/package/deploy/verify; product manifest; capability registry; schema registration; rollback evidence |
| Kernel plugins         | consent, audit, compliance, human approval, ledger only if billing is in release                                              |
| Data Cloud             | patient/consent/audit/evidence records; tenant-scoped mutations and queries; runtime truth for product surfaces               |
| Platform security      | auth, route entitlements, RBAC, tenant isolation, rate limiting                                                               |
| Platform observability | correlation IDs, health, metrics, audit trail                                                                                 |
| YAPPC                  | release visibility only if used; must not claim runtime truth from local/fake data                                            |
| Audio-Video            | exclude unless PHR uses telemedicine/media in production                                                                      |

### Required production slices for Digital Marketing release

| Foundation             | Required slice                                                                                               |
| ---------------------- | ------------------------------------------------------------------------------------------------------------ |
| Kernel                 | lifecycle profile, plugin bridge, capability/feature flag gates                                              |
| Kernel plugins         | audit, approval, notification, risk, feature flag, compliance                                                |
| Data Cloud             | campaign/activity/event/evidence/runtime truth if used; otherwise product-owned PostgreSQL must be canonical |
| Platform security      | trusted server identity, backend authZ, tenant/workspace scope                                               |
| Platform observability | campaign/connector/AI action traceability                                                                    |
| AEP/agents             | only recommendation/action flows used by release                                                             |
| YAPPC                  | onboarding/visibility/control only if used by release                                                        |

## 6. Cross-product TODO list

| ID      | Priority | TODO                                                                                                                                                                                  |
| ------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| FND-001 | P0       | Add Product Foundation Usage Profile files for `phr` and `digital-marketing`; release gates should evaluate only used production slices, but fail any used non-production capability. |
| FND-002 | P0       | Update release readiness script to fail if registry says `implemented/ready/enabled` but deployment target is only `local` for a release candidate.                                   |
| FND-003 | P0       | Add a production-fallback scanner for `test-tenant`, `test-user`, `InMemory*`, `Noop*`, `fallback`, `disabled`, `sample`, and `local-only` in production paths.                       |
| FND-004 | P0       | Make docs/registry/code truth consistent: `implemented` must mean executable and evidence-backed, not merely present.                                                                 |
| FND-005 | P1       | Implement Product Family Asset Registry schema and begin extracting PHR + DMOS reusable assets.                                                                                       |
| FND-006 | P1       | Add release evidence pack per product: build/test, API parity, tenant isolation, auth matrix, policy gates, audit, observability, i18n/a11y, deployment, rollback.                    |
| FND-007 | P1       | Add reusable asset promotion policy: candidate → hardened → production → shared package/plugin/template/schema.                                                                       |
| FND-008 | P2       | Add YAPPC UI for reusable asset discovery, compatibility, maturity, and guided reuse.                                                                                                 |

---