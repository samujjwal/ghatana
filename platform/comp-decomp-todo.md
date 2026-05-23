Below are the two updated implementation-plan docs for commit `00d308e10f0542d9e4a35cce22a370bb446fdea6`.

I treated the commit snapshot as source of truth. I did **not** execute builds/tests locally. The target commit itself is a YAPPC changelog-only commit marked `[skip ci]`, so this is a static code/docs/manifest audit rather than runtime-verified CI evidence. 

# Doc 1 — PHR + Digital Marketing release plan with Kernel/Data Cloud/foundation hardening

## 1. Executive status

The snapshot has materially improved compared with the previous commit review:

PHR now lists `compose-local`, `dev`, `staging`, and `prod` deployment targets with default environment `dev`, instead of only local. It remains lifecycle-enabled and releaseable in registry terms. 

Digital Marketing also now lists `compose-local`, `dev`, `staging`, and `prod`, and it adds explicit lifecycle readiness gates for registry validation, manifest validation, lifecycle contract validation, bridge compliance, marketing consent boundary, persistence proof, and Google Ads connector proof. 

PHR also fixed two major prior blockers: `PhrServiceBase` no longer injects fallback `test-tenant` / `test-user`, and `PhrKernelModule` now requires a `DistributedCachePort` for consent cache wiring instead of constructing the consent service with an in-memory default.  

Digital Marketing fixed prior application/persistence gate weaknesses: feature flag generation is now enabled before `compileJava`, application coverage is restored to 85% line / 70% branch, persistence coverage is 85% line / 75% branch, and Flyway migration validation is wired into `check`.  

## 2. Current maturity scorecard

| Area                                  |      Score | Current maturity                                                                                                                                                                                                                                                                 |
| ------------------------------------- | ---------: | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| PHR product readiness                 | **7.2/10** | Strong Kernel composition, strict consent cache dependency, no test tenant fallback, release gates, contract conformance task, dev/staging/prod targets. Still needs production runtime evidence and rollback completion.                                                        |
| Digital Marketing readiness           | **7.3/10** | Strong modular architecture, production-like bridge adapter, restored feature flag generation, restored coverage gates, Flyway validation, dev/staging/prod targets. Still needs real release evidence for connector, persistence, UI route gating, and runtime module decision. |
| Kernel slice readiness for PHR/DMOS   | **6.8/10** | Product usage is clearer, but release needs evidence that used lifecycle/plugin capabilities are production-ready, not just included.                                                                                                                                            |
| Data Cloud/foundation slice readiness | **6.6/10** | Data Cloud remains the intended operational fabric; product release needs proof that the used Data Cloud collections/read models/evidence are durable and tenant-scoped.                                                                                                         |
| Reusable product-family asset model   | **5.4/10** | YAPPC now has a product-family control-plane API with asset registry and promotion surfaces, but it still depends on populated Data Cloud records and needs release evidence.                                                                                                    |
| Overall release-family maturity       | **7.0/10** | Strong progress. PHR can be first release candidate once production evidence, rollback, and deployment proofs are closed.                                                                                                                                                        |

## 3. PHR release-readiness findings

### Strengths

PHR’s registry entry now supports local/dev/staging/prod environments and keeps lifecycle execution allowed. Its required healthcare gates remain consent, PII classification, audit evidence, FHIR contract validation, and tenant data sovereignty. 

`PhrServiceBase` now delegates directly to `ProductDataServiceBase` with metadata enrichment and owner-scope strategy; the previous production-risky defaulting of missing tenant/principal to test values is gone. 

`PhrKernelModule` now resolves `DistributedCachePort` from `KernelContext` and fails if it is unavailable, explicitly stating that in-memory consent cache is not allowed in production wiring. That is a strong production-readiness improvement for regulated consent correctness. 

`ConsentManagementService` now only exposes the distributed-cache constructor path; the previous convenience in-memory constructor is gone. The service keeps rate limiting, grant create/revoke, access decisions, emergency grants, notification, and audit behaviors. 

PHR’s build now wires `checkApiContractConformance` into `check`, even though the OpenAPI plugin itself is still not available. That is a better release gate than the prior state, but it should be treated as the temporary canonical route/API parity gate until OpenAPI validation is restored. 

### Remaining blockers

| ID         | Priority | Finding                                                                                                                                                                                                      | Why it matters                                                                       | Where                                                                                                  |
| ---------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------ |
| PHR-P0-001 | P0       | Production deployment targets exist in registry, but there is not enough inspected evidence that staging/prod bootstrap, secrets, storage, distributed cache, audit sink, and rollback are fully executable. | Registry readiness must not outrun executable release evidence.                      | `config/canonical-product-registry.json`, `products/phr/kernel-product.yaml`, `.kernel/evidence/phr/*` |
| PHR-P0-002 | P0       | Rollback remains `target-partial` with required-before-enablement items.                                                                                                                                     | PHR should not be released to prod until rollback evidence is concrete.              | `rollbackReadiness` in registry and PHR rollback evidence files                                        |
| PHR-P0-003 | P0       | `registerEventHandlers` is still a placeholder for promoted kernel contracts.                                                                                                                                | A regulated product needs event/audit/lifecycle truth, not only service composition. | `PhrKernelModule.registerEventHandlers`                                                                |
| PHR-P0-004 | P0       | Contract conformance exists, but OpenAPI plugin validation remains removed.                                                                                                                                  | Release needs hard route/API/schema parity.                                          | `products/phr/build.gradle.kts`                                                                        |
| PHR-P1-005 | P1       | Need evidence that `DistributedCachePort` is backed by production-grade cache in staging/prod profiles.                                                                                                      | Consent invalidation across nodes is safety-critical.                                | Kernel context/bootstrap, PHR deployment config                                                        |
| PHR-P1-006 | P1       | Emergency access path returns emergency grant with post-hoc justification instruction, but release needs an end-to-end post-hoc review gate.                                                                 | Break-glass without durable review evidence is not production-safe.                  | `ConsentManagementService`, `EmergencyAccessReviewWorkflow`, audit tests                               |
| PHR-P1-007 | P1       | Need full UI route/access validation for patient/provider/admin surfaces.                                                                                                                                    | PHR’s release risk is not only backend correctness; it is privacy-safe UX.           | `products/phr/apps/web`, route entitlement tests                                                       |

## 4. PHR implementation plan

| Priority | Work item                          | Implementation details                                                                                                                           | Acceptance criteria                                                                              |
| -------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| P0       | Finalize PHR release evidence pack | Generate `.kernel/evidence/phr/phr-release-readiness.json` with build/test/API/FHIR/consent/audit/tenant/cache/rollback/deployment evidence.     | PHR release gate fails if any evidence ref is missing or stale.                                  |
| P0       | Make rollback production-ready     | Complete stable deployment manifest history, previous artifact selection policy, healthcare post-rollback gates, and rollback approval contract. | `rollbackReadiness.status` can move from `target-partial` to `ready` only with executable tests. |
| P0       | Restore strict API/OpenAPI parity  | Keep `checkApiContractConformance` but add OpenAPI schema validation or deterministic replacement.                                               | `check` proves route↔OpenAPI↔handler parity.                                                     |
| P0       | Production cache proof             | Add staging/prod test proving `DistributedCachePort` is real and consent invalidation propagates across simulated nodes.                         | In-memory cache cannot satisfy staging/prod profile.                                             |
| P0       | Event handler contracts            | Promote PHR lifecycle/audit/consent events into Kernel/Data Cloud contracts and wire `registerEventHandlers`.                                    | Patient access, consent changes, emergency access, and FHIR export emit durable events.          |
| P1       | Full break-glass E2E               | Add E2E/integration test: emergency access → patient notification → audit → post-hoc review → compliance evidence.                               | Break-glass flow cannot complete without review evidence.                                        |
| P1       | FHIR golden tests                  | Add golden FHIR R4 fixtures for patient, observation/lab, medication, immunization, imaging, notes, referrals.                                   | FHIR contract validation gate has real fixtures.                                                 |
| P1       | UI privacy hardening               | Verify patient/provider/admin navigation, unauthorized states, consent-denied states, emergency access warnings, i18n/a11y.                      | UI cannot reveal patient data without backend-granted access.                                    |
| P2       | Reusable asset extraction          | Register PHR reusable assets: consent panel, audit trail panel, FHIR validation gate, break-glass flow, tenant-scoped repository pattern.        | Assets appear in YAPPC product-family asset registry.                                            |

## 5. Digital Marketing release-readiness findings

### Strengths

Digital Marketing now has explicit lifecycle readiness gates for bridge compliance, marketing consent boundary, persistence proof, and Google Ads connector proof, in addition to registry/manifest/lifecycle contract validation. 

The application build now generates feature flags from the canonical manifest before `compileJava`, which fixes the prior “temporarily disabled” weakness. 

Application coverage gates are restored to 85% line and 70% branch; persistence gates are 85% line and 75% branch, with Flyway migration validation wired into `check`.  

The registry still records bridge conformance as true and points to `DigitalMarketingKernelAdapterImpl` and bridge tests. 

### Remaining blockers

| ID          | Priority | Finding                                                                                                                                              | Why it matters                                                                                                                                            | Where                                                                                            |
| ----------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| DMOS-P0-001 | P0       | Lifecycle gates exist in registry, but connector and persistence proof must be executable and release-blocking.                                      | Marketing release depends on external action correctness.                                                                                                 | `products/digital-marketing/lifecycle/gate-packs/*`, `dm-persistence`, `dm-connector-google-ads` |
| DMOS-P0-002 | P0       | `runtimeModule` remains false.                                                                                                                       | Either DMOS is lifecycle-managed without runtime module by design, or runtime module support must be implemented. Ambiguity blocks production confidence. | Registry conformance                                                                             |
| DMOS-P0-003 | P0       | Need staging/prod deployment bootstrap proof for PostgreSQL, Flyway, connector credentials, feature flags, rate limits, secrets, OTLP, and rollback. | Registry now advertises prod target, so prod bootstrap must be real.                                                                                      | Deployment configs, release evidence                                                             |
| DMOS-P1-004 | P1       | UI route/action gating still needs proof against backend capabilities.                                                                               | UI should not expose connector/campaign/approval actions that backend will reject or that are not enabled.                                                | `products/digital-marketing/ui`                                                                  |
| DMOS-P1-005 | P1       | AI action evidence needs full persistence proof.                                                                                                     | AI-assisted recommendations/actions require prompt/input/model/provider/confidence/risk/policy/approval audit.                                            | `dm-domain`, `dm-application`, `dm-persistence`                                                  |
| DMOS-P1-006 | P1       | Need connector E2E: OAuth → command/outbox → retry/DLQ → external ID → audit/analytics feedback.                                                     | Google Ads workflow is a production trust boundary.                                                                                                       | `dm-connector-google-ads`, `dm-application`, integration tests                                   |

## 6. Digital Marketing implementation plan

| Priority | Work item                                 | Implementation details                                                                                                                        | Acceptance criteria                                       |
| -------- | ----------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| P0       | Make lifecycle readiness gates executable | Ensure all listed gates have real tests: registry, manifest, lifecycle contract, bridge, consent boundary, persistence, Google Ads connector. | `check` or release gate fails if any gate lacks evidence. |
| P0       | Resolve `runtimeModule: false`            | Decide and document: either implement runtime module or mark not required with explicit lifecycle architecture proof.                         | Registry truth matches execution model.                   |
| P0       | Production bootstrap validation           | Add startup validation for PostgreSQL, migrations, connector credentials, secrets, OTLP, rate limits, feature flags, and kill switches.       | Staging/prod fail fast when required config is absent.    |
| P0       | Connector proof                           | Add Google Ads connector tests for OAuth, idempotency, retry, DLQ, compensation, external ID persistence, audit.                              | Connector gate cannot pass with mocks only.               |
| P1       | Persistence proof                         | Validate tenant/workspace filters, FK/unique constraints, idempotency keys, created/updated timestamps, immutable audit/approval records.     | Repository tests prove isolation and data integrity.      |
| P1       | UI runtime-truth gating                   | Fetch capabilities/entitlements from backend and gate routes/cards/actions accordingly.                                                       | No frontend-only permission gating.                       |
| P1       | AI action log                             | Persist and expose AI prompt/input metadata, model/version, provider, confidence, rationale, policy decision, approval outcome.               | Every AI-influenced action has audit/evidence.            |
| P2       | Reusable asset extraction                 | Register reusable approval queue, connector workflow, AI transparency panel, campaign dashboard, notification retry/DLQ pattern.              | Assets appear as candidate/hardened in YAPPC registry.    |

## 7. Cross-foundation release TODOs

| ID      | Priority | TODO                                                                                                                                     | Where                                                |
| ------- | -------- | ---------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------- |
| FND-001 | P0       | Add product-specific Foundation Usage Profile for PHR and Digital Marketing.                                                             | `products/*/lifecycle/foundation-usage-profile.yaml` |
| FND-002 | P0       | Update release checker to compare registry prod target against evidence files and fail if prod target lacks bootstrap/rollback evidence. | `scripts/check-product-release-readiness.mjs`        |
| FND-003 | P0       | Add scanner for production-reachable `InMemory`, `Noop`, `fallback`, `sample`, `local`, `test-*` patterns.                               | `scripts/check-production-fallbacks.mjs`             |
| FND-004 | P1       | Make Data Cloud evidence collections canonical for release readiness and reusable assets.                                                | Data Cloud schemas + YAPPC control-plane collections |
| FND-005 | P1       | Add product-family asset promotion policy.                                                                                               | YAPPC + Data Cloud + Kernel gates                    |
| FND-006 | P1       | Add release cockpit evidence for PHR and Digital Marketing.                                                                              | YAPPC product-family control plane                   |

---

# Doc 2 — YAPPC-only review and implementation plan

## 1. Executive status

YAPPC has improved meaningfully in this commit range. `YappcLifecycleService` now wires a `ProductFamilyControlPlaneController`, which directly aligns with the product-family release strategy and reusable asset registry goal. 

The product-family controller adds backend-owned read models for product release readiness, reusable assets, doc-truth warnings, guided reuse, asset promotion, and Kernel lifecycle timeline. It uses Data Cloud collections such as `product_release_readiness`, `product_family_assets`, `yappc_truth_checks`, `product_family_reuse_recommendations`, and `kernel_lifecycle_truth`.  

YAPPC’s architecture still explicitly admits uneven maturity across the lifecycle: Intent/Shape/Validate/Generate are partial, while Run/Observe/Learn/Evolve remain early. That remains the main reason YAPPC should be treated as an improving control-plane product, not yet a complete production-grade product-family automation platform. 

## 2. YAPPC maturity scorecard

| Area                           |      Score | Current maturity                                                                                                    |
| ------------------------------ | ---------: | ------------------------------------------------------------------------------------------------------------------- |
| Product vision                 | **7.8/10** | Strong lifecycle and Kernel/Data Cloud boundary.                                                                    |
| Lifecycle backend              | **6.8/10** | Service wiring is real; controllers and product-family control plane are now included.                              |
| Product-family release cockpit | **6.2/10** | Backend endpoints exist, but readiness depends on populated Data Cloud records and release evidence.                |
| Reusable asset registry        | **6.0/10** | Controller supports listing/promoting assets and promotion history. Needs schema, seed/evidence, UI, quality gates. |
| Kernel timeline visibility     | **5.8/10** | Backend endpoint reads `kernel_lifecycle_truth`; needs producer-side guarantees and UI.                             |
| Doc-truth warnings             | **5.7/10** | Backend read surface exists; needs automated truth-check ingestion and UI.                                          |
| Guided reuse                   | **5.5/10** | Backend read surface exists; needs recommendation generation and compatibility scoring.                             |
| Lifecycle phase completeness   | **5.8/10** | Architecture still says phases are partial/early.                                                                   |
| Overall YAPPC readiness        | **6.4/10** | Better direction and control-plane foundation, but not production-complete.                                         |

## 3. YAPPC findings

### Strengths

YAPPC correctly defines itself as a creator, visibility, intelligence, health, and control-plane layer over Kernel and platform components. It says YAPPC must not duplicate Kernel execution/deployment/artifact/gate logic; it should generate ProductUnitIntent and consume Kernel public events/manifests/snapshots/APIs/CLI results. 

`YappcLifecycleService` now resolves `ProductFamilyControlPlaneController` from DI and includes it in the secured API servlet. This is the right architectural move for product release cockpits and foundation-readiness views. 

`ProductFamilyControlPlaneController` has the right backend-owned surfaces:

* release readiness lookup by product
* asset listing/filtering
* asset promotion
* doc-truth warnings
* guided reuse
* Kernel timeline/rollback visibility
  It returns `NOT_READY` when records are missing, which is correct for runtime truth rather than fake readiness.  

### Remaining blockers

| ID           | Priority | Finding                                                                                                                                                      | Why it matters                                                                                                           |
| ------------ | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| YAPPC-P0-001 | P0       | Product-family control-plane endpoints depend on Data Cloud records, but the producer/generator pipeline for those records is not proven in inspected files. | Cockpit cannot be trusted unless release/evidence/asset records are generated deterministically.                         |
| YAPPC-P0-002 | P0       | Asset registry exists as API surface, but no verified canonical schema/migration/evidence seed was inspected.                                                | Reusable assets need schema, versioning, validation, and quality gates.                                                  |
| YAPPC-P0-003 | P0       | `resolveTenantId` accepts `X-Tenant-Id` header fallback if principal tenant is absent.                                                                       | This may be acceptable for service/internal calls, but production user paths should derive tenant from trusted identity. |
| YAPPC-P1-004 | P1       | Asset promotion supports state transitions, but approval/governance gates are not visible in the controller.                                                 | Promotion from candidate → production/shared asset must require evidence and approval.                                   |
| YAPPC-P1-005 | P1       | Kernel timeline reads `kernel_lifecycle_truth`, but producer-side Kernel event ingestion needs proof.                                                        | Timeline must not become another passive empty surface.                                                                  |
| YAPPC-P1-006 | P1       | Lifecycle phases remain partial/early by canonical architecture.                                                                                             | Product-family cockpit cannot replace completing core lifecycle.                                                         |
| YAPPC-P1-007 | P1       | UI experience for these new backend surfaces is not proven here.                                                                                             | Product managers/developers need cockpit UX, not just APIs.                                                              |

## 4. YAPPC implementation plan

### Phase A — Make product-family control plane production-real

| Priority | Work item                             | Implementation details                                                                                                                                                                        | Acceptance criteria                                                    |
| -------- | ------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------- |
| P0       | Define Data Cloud schemas             | Add schemas for `product_release_readiness`, `product_family_assets`, `product_family_asset_history`, `yappc_truth_checks`, `product_family_reuse_recommendations`, `kernel_lifecycle_truth`. | Controller reads typed, validated collections; schema drift fails CI.  |
| P0       | Add producer jobs                     | Generate release readiness records from registry, release gates, Kernel evidence, product evidence, and CI.                                                                                   | PHR/DMOS cockpit shows real readiness, not missing-record `NOT_READY`. |
| P0       | Harden tenant resolution              | Require `Principal.tenantId` for user-facing API calls; allow `X-Tenant-Id` only for signed service calls or test profile.                                                                    | User requests cannot spoof tenant via header.                          |
| P0       | Release cockpit for PHR               | Populate and render PHR foundation usage: Kernel, Data Cloud, consent, audit, FHIR, rollback, deployment.                                                                                     | PHR release page shows blockers/evidence/readiness verdict.            |
| P0       | Release cockpit for Digital Marketing | Populate and render DMOS gates: bridge, persistence, connector, approval, AI action, deployment.                                                                                              | DMOS release page shows blockers/evidence/readiness verdict.           |

### Phase B — Make reusable asset registry governed

| Priority | Work item                  | Implementation details                                                                                                                                     | Acceptance criteria                                          |
| -------- | -------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------ |
| P0       | Asset schema               | Define required fields: assetId, type, sourceProduct, paths, maturity, reuseMode, dependencies, tests, owner, productUsage, compatibility, promotionState. | Invalid asset record cannot be saved/promoted.               |
| P0       | Promotion gates            | Require evidence refs, owner, tests, compatibility, and approval before promotion.                                                                         | Candidate cannot jump to production/shared without evidence. |
| P1       | Asset extraction from PHR  | Register consent panel, audit trail, FHIR gate, break-glass workflow, tenant-scoped repository pattern.                                                    | PHR assets appear as candidate/hardened.                     |
| P1       | Asset extraction from DMOS | Register approval queue, connector workflow, campaign dashboard, AI transparency panel, retry/DLQ pattern.                                                 | DMOS assets appear as candidate/hardened.                    |
| P1       | Asset UI                   | Build searchable/filterable YAPPC page for product/domain/type/maturity/reuseMode/compatibility.                                                           | Users can discover assets from YAPPC.                        |
| P2       | Guided reuse               | Generate recommendations for Tutorputor/FlashIt based on product intent and compatibility.                                                                 | YAPPC suggests assets with reasons and required adaptations. |

### Phase C — Complete lifecycle phase readiness

| Priority | Work item                        | Implementation details                                                                             | Acceptance criteria                                         |
| -------- | -------------------------------- | -------------------------------------------------------------------------------------------------- | ----------------------------------------------------------- |
| P0       | Phase state persistence          | Ensure all eight phases persist state/history/evidence in Data Cloud.                              | No phase relies only on local/in-memory/frontend state.     |
| P0       | Phase gate context               | Build gate context from artifacts, policies, evidence, runtime truth, feature flags, entitlements. | Phase transition cannot pass with empty/simplified context. |
| P0       | Kernel ProductUnitIntent handoff | Use typed public Kernel contracts only.                                                            | YAPPC never mutates private Kernel registry files.          |
| P1       | Kernel timeline producer         | Ensure Kernel emits lifecycle events into `kernel_lifecycle_truth`.                                | YAPPC timeline is complete and traceable.                   |
| P1       | Doc-truth ingestion              | Run doc/registry/code truth checkers and write warnings into `yappc_truth_checks`.                 | YAPPC shows stale/contradictory docs.                       |
| P1       | UI runtime truth                 | Navigation/actions come from backend state and capability truth.                                   | No fake live features.                                      |

## 5. YAPPC TODO list

| ID        | Priority | TODO                                                                                    | Where                                 |
| --------- | -------- | --------------------------------------------------------------------------------------- | ------------------------------------- |
| YAPPC-001 | P0       | Add canonical Data Cloud schema/migration for product-family control-plane collections. | Data Cloud contracts/schemas          |
| YAPPC-002 | P0       | Add release readiness producer for PHR and DMOS.                                        | YAPPC service + scripts               |
| YAPPC-003 | P0       | Harden tenant resolution; remove unsigned `X-Tenant-Id` fallback from user paths.       | `ProductFamilyControlPlaneController` |
| YAPPC-004 | P0       | Add PHR release cockpit UI.                                                             | YAPPC frontend                        |
| YAPPC-005 | P0       | Add DMOS release cockpit UI.                                                            | YAPPC frontend                        |
| YAPPC-006 | P1       | Add asset schema validation and promotion gate enforcement.                             | Product-family asset registry         |
| YAPPC-007 | P1       | Register first PHR reusable assets.                                                     | Asset seed/evidence producer          |
| YAPPC-008 | P1       | Register first DMOS reusable assets.                                                    | Asset seed/evidence producer          |
| YAPPC-009 | P1       | Add Kernel timeline event producer and trace/evidence linking.                          | Kernel + YAPPC                        |
| YAPPC-010 | P1       | Add doc-truth ingestion and warnings UI.                                                | scripts + YAPPC UI                    |
| YAPPC-011 | P1       | Complete durable state/evidence for all lifecycle phases.                               | YAPPC lifecycle backend               |
| YAPPC-012 | P2       | Add guided reuse recommendations for Tutorputor and FlashIt.                            | YAPPC reuse engine                    |

## 6. Updated release path

```text
PHR first
  → close rollback/evidence/deployment/cache/FHIR/audit gates
  → register regulated reusable assets
  → expose PHR release cockpit in YAPPC

Digital Marketing second
  → close connector/persistence/approval/AI-action/deployment gates
  → register SaaS/connector reusable assets
  → expose DMOS release cockpit in YAPPC

Then Tutorputor and FlashIt
  → start from YAPPC-guided reusable assets
  → harden education/mobile/media-specific foundation slices
```

The main change at `00d308e...` is that the plan is no longer just conceptual: registry entries, PHR production-safety improvements, DMOS build/release gates, and YAPPC product-family control-plane APIs now exist. The next step is to make those surfaces **evidence-producing and release-blocking**, not just present.
