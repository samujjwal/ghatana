I executed the review statically against commit `4bfa0c28edf05ca714ba56a936628874a3673622`. I did **not** run tests locally; I inspected repo code, manifests, registry, and checked-in release evidence. The commit itself is a changelog-only `[skip ci]` commit, but the snapshot includes meaningful prior changes referenced by the changelog.

## Score summary versus last run

| Review area                                    |                 Last run score |  Current score | Direction              | Release verdict          |
| ---------------------------------------------- | -----------------------------: | -------------: | ---------------------- | ------------------------ |
| PHR + used foundations                         |                         6.4/10 |     **7.2/10** | Improved               | Still blocked            |
| Digital Marketing + used foundations           |                         6.8/10 |     **7.5/10** | Improved               | Still blocked            |
| YAPPC-only                                     |                         6.0/10 |     **6.9/10** | Improved               | Not production-ready yet |
| Repo release evidence score: PHR               | Not used in prior static score | **4.84/5 avg** | Good, but failing gate | Fail                     |
| Repo release evidence score: Digital Marketing | Not used in prior static score | **4.84/5 avg** | Good, but failing gate | Fail                     |

The checked-in release evidence gives both PHR and Digital Marketing an average score of `4.84/5` with no below-target dimensions, but both still fail because `./scripts/check-production-stubs.mjs` failed.

# Doc 1 — PHR + Digital Marketing implementation plan with Kernel/Data Cloud/foundations

## Current state

The biggest improvement since the last run is that PHR and Digital Marketing are no longer registry-local-only. Both now list `compose-local`, `dev`, `staging`, and `prod`, with `dev` as default.

PHR also improved materially: `PhrServiceBase` no longer injects default `test-tenant` and `test-user` into production service mutations, removing a major fail-closed blocker from the last review.

PHR Kernel wiring now resolves a required `DistributedCachePort` for consent cache and throws if it is unavailable, instead of wiring the consent service through the in-memory convenience path. That is a real production-readiness improvement.

Digital Marketing also improved: feature-flag generation is re-enabled and `compileJava` now depends on `generateFeatureFlags`; app coverage thresholds were restored to `0.85` line and `0.70` branch.

Digital Marketing persistence also improved: coverage verification is no longer CI-only, and `validateFlywayMigrations` is now part of `check`.

## Remaining blockers

The shared release verdict is still **fail**, caused by `check-production-stubs.mjs`. This should be treated as the current P0 release blocker for both PHR and Digital Marketing, even though the average score is strong.

PHR has new event handlers for lifecycle, audit, and consent, but code comments still say “in production, this would write to Data Cloud evidence store/audit sink” and currently publish to Kernel event bus. That is better than nothing, but not yet durable evidence.

PHR still has rollback readiness as `target-partial`, with prerequisites like stable deployment manifest history, previous artifact selection policy, healthcare post-rollback gates, and rollback approval contract.

Digital Marketing has stronger registry readiness now, including explicit lifecycle gates for bridge compliance, marketing consent boundary, persistence proof, and Google Ads connector proof. Those must become executable release gates with concrete evidence, not just declared metadata.

## PHR implementation plan

| Priority | Work item                               | What to do                                                                                                                                        | Where                                                     |
| -------- | --------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------- |
| P0       | Fix production-stubs gate               | Open the failing `check-production-stubs.mjs` output and remove or quarantine every production-reachable stub/fallback/sample path.               | Repo-wide, starting with PHR + used foundations           |
| P0       | Make PHR event evidence durable         | Replace “would write to Data Cloud” event comments with actual Data Cloud evidence/audit writes for lifecycle, audit, and consent events.         | `PhrKernelModule`, PHR event handlers, Data Cloud adapter |
| P0       | Close rollback readiness                | Implement stable deployment manifest history, previous artifact selection policy, healthcare post-rollback gates, and rollback approval contract. | `products/phr/lifecycle/rollback/*`, Kernel lifecycle     |
| P0       | Prove consent cache production behavior | Add tests proving PHR fails startup without `DistributedCachePort` in production and uses distributed cache in release profile.                   | `PhrKernelModuleTest`, consent tests                      |
| P0       | Healthcare gate execution               | Ensure consent, PII classification, audit evidence, FHIR validation, and tenant data sovereignty are executable gates with checked-in evidence.   | `products/phr/lifecycle/gate-packs/*`                     |
| P1       | FHIR golden contract tests              | Add/expand FHIR R4 fixtures for Patient, Observation, Medication, Immunization, ImagingStudy, DocumentReference, Encounter.                       | PHR FHIR tests                                            |
| P1       | Patient/provider/admin journeys         | Add UI/API tests for patient self-access, provider consent access, denied access, break-glass access, caregiver delegation.                       | PHR web + API tests                                       |
| P1       | Observability trace                     | Add end-to-end correlation across API → PHR service → Data Cloud/Kernel → audit/evidence.                                                         | PHR API/services/foundations                              |
| P2       | Reusable asset extraction               | Register reusable PHR assets: consent gate, audit panel, break-glass workflow, FHIR validation gate, tenant-scoped data pattern.                  | Product Family Asset Registry                             |

## Digital Marketing implementation plan

| Priority | Work item                       | What to do                                                                                                                                                                                        | Where                                                           |
| -------- | ------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------- |
| P0       | Fix production-stubs gate       | Remove/quarantine production-reachable stubs in DMOS and used foundations.                                                                                                                        | DMOS + shared foundations                                       |
| P0       | Make lifecycle gates executable | Convert registry gates into deterministic tests/evidence: registry validation, manifest validation, lifecycle contract, bridge compliance, consent boundary, persistence proof, Google Ads proof. | `products/digital-marketing/lifecycle/gate-packs/*`             |
| P0       | Complete connector proof        | Prove Google Ads OAuth, idempotency, retry, DLQ, external ID persistence, audit, and compensation/rollback.                                                                                       | `dm-connector-google-ads`, `dm-application`, `dm-persistence`   |
| P0       | Production bootstrap validation | Fail startup if prod profile lacks DB, secrets, feature flag backend, OTLP, connector credentials, encryption/HMAC keys.                                                                          | `dm-api`, `dm-application`, deployment config                   |
| P1       | Runtime module decision         | Registry still has `runtimeModule: false`; decide if DMOS release requires runtime module. Implement or explicitly justify.                                                                       | `config/canonical-product-registry.json`, `kernel-product.yaml` |
| P1       | UI runtime truth                | Ensure pages/actions/cards are backend capability-driven and show locked/degraded states from backend, not local assumptions.                                                                     | `products/digital-marketing/ui`                                 |
| P1       | AI action evidence              | Persist prompt/input/model/provider/confidence/rationale/risk/policy/approval/action outcome.                                                                                                     | `dm-domain`, `dm-application`, `dm-persistence`                 |
| P2       | Reusable asset extraction       | Register campaign dashboard, approval queue, connector adapter pattern, notification retry/DLQ, AI transparency panel.                                                                            | Product Family Asset Registry                                   |

## Foundation tasks for both products

| Priority | Foundation     | Task                                                                                                                                  |
| -------- | -------------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| P0       | Release gates  | Make `check-production-stubs.mjs` failure actionable in product-specific evidence: exact file, symbol, production path, required fix. |
| P0       | Kernel         | Product release should fail if any used Kernel/plugin slice is in-memory/noop/local-only without explicit non-prod profile.           |
| P0       | Data Cloud     | Used product evidence/audit/runtime truth must be durable and tenant-scoped.                                                          |
| P1       | Registry       | Add `foundationUsageProfile` for PHR and Digital Marketing.                                                                           |
| P1       | YAPPC          | Surface release readiness, blockers, foundation usage, doc-truth warnings, and asset candidates in YAPPC.                             |
| P1       | Asset registry | Store reusable asset records in Data Cloud with maturity, source paths, tests, dependencies, promotion path.                          |

# Doc 2 — YAPPC-only implementation plan

## Current state

YAPPC improved significantly. `YappcLifecycleService` now wires `ProductFamilyControlPlaneController`, and the API exposes product-family routes for release readiness, assets, asset promotion, doc-truth warnings, guided reuse, and Kernel timeline.

The old explicit SLF4J-only audit fallback binding is gone from `YappcLifecycleService`, which addresses a prior audit concern.

`ProductFamilyControlPlaneController` is now a real Data Cloud-backed controller for product release readiness, product-family assets, doc-truth checks, reuse recommendations, and asset promotion history. It requires tenant context and returns blocked/not-ready states when release records are missing.

## YAPPC current score

| Area                         | Last run |    Current | Direction          |
| ---------------------------- | -------: | ---------: | ------------------ |
| Product-family control plane |   3.5/10 | **6.5/10** | Major improvement  |
| Release cockpit backend      |   4.5/10 | **6.8/10** | Improved           |
| Asset registry backend       |   3.5/10 | **6.3/10** | Improved           |
| Kernel timeline visibility   |   4.0/10 | **5.8/10** | Improved           |
| Lifecycle phase maturity     |   6.0/10 | **6.3/10** | Slight improvement |
| Overall YAPPC                |   6.0/10 | **6.9/10** | Improved           |

## Remaining YAPPC blockers

The product-family control plane is backend-present, but it still depends on Data Cloud records being populated. If no release readiness record exists, it returns `NOT_READY` and `BLOCKED`, which is correct behavior but means ingestion/population pipelines are now required.

Asset promotion has useful validation, but it needs stronger lifecycle integration: promotion should require evidence packs, test gates, compatibility checks, and owner approval before becoming production/shared.

The lifecycle phase endpoints still include direct route definitions and hardcoded phase lists in the service. That is acceptable short-term but should move to generated manifest-backed routing to avoid drift.

## YAPPC-only implementation plan

| Priority | Work item                          | What to do                                                                                                                | Where                                                 |
| -------- | ---------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------- |
| P0       | Populate release readiness records | Build ingestion from `.kernel/evidence/product-release-readiness.*.json` into Data Cloud `product_release_readiness`.     | YAPPC services + Data Cloud                           |
| P0       | Product-family cockpit UI          | Add UI for PHR and Digital Marketing release readiness: score, verdict, blockers, gates, foundation usage, evidence refs. | YAPPC frontend                                        |
| P0       | Production-stub visibility         | Show `check-production-stubs.mjs` failure with exact files, symbols, product ownership, and required fix.                 | YAPPC product-family release view                     |
| P0       | Asset registry ingestion           | Ingest candidate assets from PHR/DMOS manifests or generated scan output into Data Cloud `product_family_assets`.         | `ProductFamilyControlPlaneController`, ingestion jobs |
| P1       | Asset promotion governance         | Require tests, evidence refs, owner, dependencies, compatibility, and approval for production/shared promotion.           | Asset promotion workflow                              |
| P1       | Kernel timeline population         | Populate `kernel-timeline` from Kernel lifecycle events, not static/local artifacts.                                      | Kernel events + YAPPC control plane                   |
| P1       | Doc-truth warnings                 | Connect doc/registry/code truth checker output into `yappc_truth_checks` and UI.                                          | YAPPC control plane                                   |
| P1       | Guided reuse                       | For new products like Tutorputor/FlashIt, recommend reusable assets by domain, surface, capabilities, and maturity.       | `product_family_reuse_recommendations`                |
| P1       | Manifest-backed routes             | Generate product-family routes from YAPPC route manifest/OpenAPI, not manually maintained route strings.                  | YAPPC API routing                                     |
| P2       | Product onboarding experience      | Add flow: choose archetype → select assets → generate ProductUnitIntent → Kernel lifecycle → release evidence.            | YAPPC UX                                              |

## Most important next step

Fix the **one current release blocker** first:

```text
./scripts/check-production-stubs.mjs
```

Both checked-in release evidence files have strong average scores and no below-target dimensions, but both fail on this gate. Until that gate is clean, neither PHR nor Digital Marketing should be treated as releasable.
