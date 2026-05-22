# Production-Ready Polyglot Kernel Deep/Wide Audit and Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Target commit:** `c240177a558fe2407419fae4f6ad489db130d0d2`  
**Commit message:** `dafd fdsaf`  
**Audit mode:** source-grounded expert audit, feature-completeness review, stability review, and implementation plan.  
**Verification stance:** long-running validations are intentionally deferred. This report does **not** claim local execution. It uses committed source, manifests, contracts, scripts, and evidence artifacts to identify bugs, missing capabilities, feature gaps, stability issues, and production-readiness work.

---

## 1. Executive Verdict

The platform is making strong progress toward a production-grade Product Development Kernel. At this commit, the repository contains broad release-readiness evidence, multi-language adapter registrations, product-to-product interaction contracts, request/response and event brokers, interaction runtime truth checks, release-profile evidence, and coverage across 47 implementation-plan dimensions.

However, the product is **not yet production-ready as a fully seamless “build/deploy any app in any supported language” platform**. The current state is best described as:

> **Production-readiness foundation is strong; production-grade feature completeness and operational hardening remain incomplete.**

The most important progress since earlier snapshots:

1. **Polyglot adapter foundation exists.** Java, TypeScript web, TypeScript Node API, Rust/Cargo, Python/pyproject, Docker Buildx, and Compose adapters are registered in the default toolchain registry.
2. **Product interaction has moved from contract-only to broker-backed architecture.** Kernel now has request/response and event brokers with validation, policy hooks, timeout/metrics/evidence hooks, and handler discovery.
3. **PHR and Digital Marketing product interaction handlers are no longer purely hardcoded examples.** The PHR consent handler depends on a `ConsentService`; DMOS notification preference handler depends on a `NotificationPreferenceService`.
4. **Release-readiness evidence is broader.** The committed evidence indicates pass status for release readiness journeys, strict release gates, cross-product interaction flows, runtime failure injection, route entitlement, observability, i18n/a11y/AI/performance gates, and 47 implementation-plan dimensions.

The most important remaining risks:

1. **Evidence artifacts are not equivalent to real product completeness.** Several gates prove shape, coverage, scripts, or manifests, not full business workflow correctness.
2. **Kernel platform mode is still not seamless enough.** The CLI still exposes provider mode and platform-mode wiring concerns. Product teams should not need to know provider internals.
3. **Product interaction broker has production-hardening gaps.** Default no-op evidence writer, caller-supplied policy context, unbounded in-memory replay cache, incomplete manifest-policy enforcement, and limited circuit-breaker/backpressure posture are not production-grade defaults.
4. **Rust/Python support exists, but needs deeper production proof.** The adapters are present and valuable, but require real product fixtures, language-specific output parsing, security/audit evidence, package provenance, and environment classification hardening.
5. **PHR i18n is still a known gap.** Wave 2 scorecard marks PHR i18n as false.
6. **Some root scripts may be portability fragile.** Several scripts use `gradlew` rather than `./gradlew`, which can fail outside environments where `gradlew` is on `PATH`.

---

## 2. Current-State Classification

| Area                                     |                     Classification | Expert assessment                                                                                                                 |
| ---------------------------------------- | ---------------------------------: | --------------------------------------------------------------------------------------------------------------------------------- |
| Kernel CLI lifecycle commands            |               Existing but partial | Good command surface exists; explain/recover support exists, but UX still exposes provider details and internal modes.            |
| Kernel lifecycle planner/executor        |               Existing but partial | Handles plan/explain and adapter steps; needs stronger phase recovery, affected-surface optimization, and production mode wiring. |
| Product registry and release profiles    |            Existing and executable | Release profile evidence exists and shows affected products: Digital Marketing, Finance, FlashIt, PHR.                            |
| Java adapter                             |            Existing and executable | Baseline adapter is registered and used by pilot backends. Needs richer parsing, failure classification, and service conventions. |
| TypeScript React adapter                 |            Existing and executable | Web adapter is registered and used by PHR/DMOS. Needs stronger route/a11y/i18n/bundle evidence.                                   |
| TypeScript Node API adapter              |            Existing and executable | Adapter exists; needs product fixture proof and production service conventions.                                                   |
| Rust/Cargo adapter                       |                 Existing but early | Adapter exists and maps validate/test/build/package to Cargo. Needs real product fixture and output/test parsing.                 |
| Python/pyproject adapter                 |                 Existing but early | Adapter exists and supports configured commands. Needs package manager/environment matrix and production fixtures.                |
| Docker Buildx adapter                    | Existing but environment-sensitive | Must keep environment-blocked classification honest.                                                                              |
| Compose adapter                          |            Existing and executable | Needs deployment topology, secrets, health checks, and rollback proof per product.                                                |
| Product interaction contracts            |            Existing and executable | Manifest declarations and schema hashes exist for PHR/DMOS interactions.                                                          |
| Product interaction broker               |               Existing but partial | Strong foundation; not yet production-hard due to default no-op evidence and manifest-policy enforcement gaps.                    |
| Product interaction event broker         |               Existing but partial | Event path exists; needs durable provider, backpressure, timeout, retry, replay, idempotency, and subscriber isolation.           |
| Data Cloud interaction evidence provider |               Existing but partial | Provider exists; needs end-to-end wiring and schema-level evidence validation.                                                    |
| Plugin interaction                       |               Existing but partial | Plugin interaction checks exist; needs production broker parity with product interaction broker.                                  |
| PHR product                              |               Existing but partial | Lifecycle-enabled, healthcare gates declared, interactions exist; needs complete healthcare workflow and i18n completion.         |
| Digital Marketing product                |               Existing but partial | Lifecycle-enabled, interactions exist, platform bridge usage improving; needs full DMOS domain workflow completion.               |
| Studio developer experience              |               Existing but partial | Good artifact workflow foundation; needs lifecycle/product interaction UX and production provider mode abstraction.               |
| Common platform feature bridge           |               Existing but partial | Identity/audit/consent/risk/notification bridges exist in places; usage must become uniform, mandatory, and easy.                 |
| Production evidence                      |  Existing but not sufficient alone | Pass artifacts exist; still need real behavior and domain workflow proofs.                                                        |

---

## 3. Evidence-Based Observations

### 3.1 Release readiness evidence is broad but should not be over-trusted

The committed release readiness artifact reports `pass: true` and includes journeys for vision/coherence, workflow/runtime proof, security/privacy/governance, quality/experience/release, and strict release coverage. It records several checks as status `0`, including product registry, cross-product interaction boundaries, cross-product interaction flows, interaction runtime truth, runtime failure injection, route entitlement, observability, i18n, AI governance, performance workflows, and affected product release profile.

**Expert interpretation:** this is valuable regression evidence, but not full production readiness. Many scripts are static checks or evidence-shape checks. They do not necessarily prove every end-user journey, every product-specific workflow, every deployment topology, or every runtime data integrity rule.

### 3.2 Wave 2 scorecard reveals at least one product quality gap

The Wave 2 product quality scorecard marks all areas true for most products, but PHR has `i18n: false` and a score ratio of `0.8`.

**Production implication:** PHR cannot be marked production-complete for regulated healthcare UX while i18n remains incomplete. This is especially important for patient-facing healthcare workflows.

### 3.3 Atomic workflow posture is strong for Data Cloud mutating routes

Atomic workflow evidence reports 155 mutating routes, 53 critical mutating routes, rollback and retry route presence, and zero violations. Critical mutating routes require policy and blocking audit.

**Production implication:** good foundation for platform action-plane governance. Still, endpoint shape proof must be complemented by runtime tests proving rollback/retry semantics and domain state correctness.

### 3.4 Product interaction architecture has matured

Digital Marketing and PHR `kernel-product.yaml` files now declare explicit interaction contracts with request/response schema refs, schema hashes, policy blocks, lifecycle phases, evidence refs, and retention policies. This is a strong move toward safe product-to-product interaction.

**Production implication:** contract declarations are strong, but runtime enforcement must be tied to the manifest policy, not only caller-supplied `policyContext`.

### 3.5 Polyglot Kernel support is now real but early

The default toolchain registry registers `GradleJavaServiceAdapter`, `PnpmViteReactAdapter`, `PnpmNodeApiAdapter`, `CargoRustAdapter`, `PythonPyprojectAdapter`, `DockerBuildxAdapter`, and `ComposeLocalAdapter`. Rust and Python adapters have real implementations.

**Production implication:** this moves Rust/Python from “target architecture” to “early executable support.” The next step is real product fixture proof and production output validation.

---

## 4. P0 Findings — Must Fix Before Production Claims

### P0-01 — Product interaction broker still allows no-op evidence by default

**Current state:** `ProductInteractionBroker.Builder` defaults to `ProductInteractionEvidenceWriter.noop()`.

**Why this is a production blocker:** A product interaction can return a successful outcome and satisfy “evidenceRefs exist” while the broker persists no actual evidence record. For regulated interactions such as PHR consent or cross-product healthcare/marketing interactions, production must fail closed if evidence cannot be written.

**Required fix:**

- Replace default no-op writer in production/runtime broker factories.
- Add explicit `BrokerMode` or environment/profile setting.
- Allow no-op only in unit tests with a named test helper.
- Add `ProductInteractionBrokerFactory` that requires a real evidence writer for non-test profiles.
- Add a check that rejects production broker construction without evidence writer.

**Likely files:**

- `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/interaction/ProductInteractionBroker.java`
- `platform-kernel/kernel-core/src/main/java/com/ghatana/kernel/interaction/ProductInteractionEvidenceWriter.java`
- `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudProductInteractionEvidenceProvider.java`
- `platform-kernel/kernel-core/src/test/java/com/ghatana/kernel/interaction/ProductInteractionBrokerTest.java`

**Tests:**

- broker construction fails in production profile without evidence writer
- broker allows no-op only with explicit test profile
- evidence writer failure blocks interaction
- Data Cloud evidence provider validates full evidence schema

---

### P0-02 — Product interaction policy evaluator relies too much on caller-supplied `policyContext`

**Current state:** `ProductInteractionPolicyEvaluator.ComprehensivePolicyEvaluator` checks `actor`, `tenantId`, `workspaceId`, `purpose`, `authorized`, and `consentGranted` in the request `policyContext` map.

**Why this is a production blocker:** A caller can claim `authorized=true` or `consentGranted=true` in request metadata unless the broker injects these fields from trusted providers. Policy must be evaluated from trusted identity, entitlement, consent, and product interaction contract metadata.

**Required fix:**

- Introduce `ProductInteractionPolicyContextResolver` that builds policy context from trusted platform providers.
- Attach manifest-declared `ProductInteractionContract` to broker execution.
- Enforce `allowedCallerRoles`, `allowedPurposes`, `tenantScope`, `requiresConsent`, and `piiClassification` from manifest policy.
- Reject request-supplied `authorized` and `consentGranted` as authoritative input.
- Record policy decision source in evidence.

**Likely files:**

- `ProductInteractionPolicyEvaluator.java`
- `ProductInteractionBroker.java`
- `ProductInteractionContract` TS schema and Java mirror if present
- `scripts/check-product-interaction-contracts.mjs`
- `scripts/check-interaction-runtime-truth.mjs`

**Tests:**

- request with forged `authorized=true` is denied when platform auth provider denies
- purpose outside manifest `allowedPurposes` is blocked
- role outside `allowedCallerRoles` is blocked
- consent-required interaction calls consent provider
- evidence includes policy decision and trusted decision sources

---

### P0-03 — Product interaction replay key is too narrow

**Current state:** broker replay key is `contractId::interactionId::tenantId::workspaceId`.

**Risk:** If an interaction ID is reused with a different payload, provider, consumer, product unit, purpose, or actor in the same tenant/workspace, the cached outcome can be replayed incorrectly.

**Required fix:**

- Include provider product ID, consumer product ID, product unit ID, contract version, and payload hash in replay key.
- Alternatively, treat `interactionId` as globally unique and reject same interaction ID with non-identical fingerprint.
- Persist replay fingerprint in evidence.

**Tests:**

- same interaction ID + different payload is blocked as idempotency conflict
- same interaction ID + same fingerprint replays
- same interaction ID + different consumer is blocked

---

### P0-04 — Product interaction event broker needs production delivery semantics

**Current state:** event broker dispatches subscribers sequentially and writes evidence. It has policy and basic metrics, but durable delivery/backpressure/retry/replay/idempotency are not visible as full production behavior.

**Required fix:**

- Add durable event provider abstraction.
- Add per-subscriber timeout and retry policy.
- Add backpressure handling.
- Add idempotency key per event/subscriber.
- Add dead-letter handling.
- Add replay support.
- Add event schema validation before publish.
- Add subscriber isolation so one subscriber cannot block all others indefinitely.

**Tests:**

- one subscriber failure does not corrupt other subscriber evidence
- duplicate event does not double-apply side effects
- retry and DLQ produce auditable evidence
- event schema mismatch is blocked

---

### P0-05 — PHR i18n is incomplete

**Current state:** scorecard marks PHR `i18n: false`.

**Required fix:**

- Complete all PHR route strings through i18n keys.
- Add patient-facing and healthcare-specific glossary coverage.
- Add missing translation namespaces.
- Add i18n route matrix gate for PHR.
- Add component tests for i18n fallback and missing-key failure behavior.

**Done criteria:** PHR scorecard shows i18n true, and patient-facing workflows have no hardcoded strings outside accepted allowlists.

---

### P0-06 — Platform mode is still not seamless enough

**Current state:** Kernel CLI supports bootstrap/platform mode, but platform provider bridge registration is still exposed as a concern to the caller in places.

**Required fix:**

- Product team should not know bootstrap vs platform provider details.
- CLI and Studio should resolve provider mode from environment/profile.
- Platform mode missing provider bridge should present one clear recovery action, not raw implementation detail.
- Add `kernel doctor` or `product doctor` for provider readiness.

**Done criteria:** product team can run one command and get actionable recovery if platform providers are unavailable.

---

### P0-07 — Root scripts appear portable-risky with `gradlew` instead of `./gradlew`

**Current state:** Some package scripts use `gradlew` directly. This can fail on Unix/macOS/CI unless `gradlew` is in PATH.

**Required fix:**

- Normalize scripts to `./gradlew` or a cross-platform wrapper that resolves the local Gradle wrapper.
- Add a script governance check for accidental bare `gradlew` usage.

**Tests:**

- script lint rejects bare `gradlew` in root scripts unless explicitly allowlisted.

---

## 5. P1 Findings — Required for High-Confidence Production

### P1-01 — Rust adapter exists but needs production fixture coverage

**Current state:** `CargoRustAdapter` supports validate/test/build/package and runs `cargo fmt --check`, `cargo check`, `cargo clippy -- -D warnings`, `cargo test`, and release build.

**Needed:**

- Add real Rust fixture product with service, worker, library, and CLI variants.
- Parse `cargo test` output into structured test results.
- Add target triple and binary metadata to artifact manifest.
- Add security/license/dependency audit hook if a repo-standard tool is chosen.
- Add missing toolchain/environment blocked tests.

---

### P1-02 — Python adapter exists but needs package-manager and runtime matrix

**Current state:** `PythonPyprojectAdapter` supports pyproject, configured commands, compileall fallback, pytest fallback, and `python -m build`.

**Needed:**

- Support uv/poetry/pip modes through explicit config.
- Add fixture products for FastAPI service, worker, and library.
- Parse pytest output into structured test results.
- Validate venv isolation and Python version requirements.
- Add dependency vulnerability/license gate if repo-standard tool exists.
- Add environment-blocked classifications for missing build/pytest modules.

---

### P1-03 — Toolchain artifact fingerprinting is still too shallow in bridge conversion

**Current state:** adapter bridge often uses artifact path as fingerprint.

**Needed:**

- Compute real file hashes for file artifacts.
- Capture container digest for image artifacts.
- Capture size, source ref, build command, runtime, target, and language.
- Make fingerprint missing a warning or blocker depending artifact criticality.

---

### P1-04 — Evidence passes should include behavioral proof density, not just gate existence

**Current state:** kernel implementation plan coverage reports 47/47 dimensions covered.

**Needed:**

- Distinguish “gate exists” from “gate proves meaningful runtime behavior.”
- Add maturity levels: declared, static-checked, contract-tested, runtime-tested, failure-injected, production-observed.
- Report dimensions with maturity, not boolean coverage only.

---

### P1-05 — Product interaction handlers need full product workflow integration

**Current state:** PHR/DMOS handlers are service-backed boundaries, but full end-user workflows must invoke them consistently.

**Needed:**

- DMOS campaign activation must call PHR consent interaction through the broker.
- DMOS audience eligibility must respond to PHR consent revoke events.
- PHR care-plan notification flow must call DMOS notification preference through the broker.
- All such interactions must be visible in lifecycle/runtime evidence and Studio.

---

### P1-06 — Studio needs product/interaction/lifecycle control-tower UX

**Needed Studio capabilities:**

- Product registry view with release-readiness score.
- ProductUnit detail with surfaces, language/runtime, adapters, interactions, gates.
- Lifecycle plan/explain/execute/recover UI.
- Product interaction graph: provider, consumer, contract, status, evidence, failures.
- Plugin interaction graph.
- Runtime truth and evidence viewers.
- PHR-specific healthcare gate evidence viewer.
- DMOS consent/notification interaction evidence viewer.
- Simple summaries first, advanced details on demand.

---

## 6. Feature Completeness Analysis

### 6.1 Kernel platform

| Capability                 |          Status | Missing for production                                                 |
| -------------------------- | --------------: | ---------------------------------------------------------------------- |
| Product registry           |          Strong | registry generation UX and conflict recovery                           |
| Product lifecycle CLI      | Good foundation | hide provider internals, better doctor/recover UX                      |
| Product lifecycle Studio   |         Partial | full control tower UX                                                  |
| Polyglot adapters          | Good foundation | real fixtures, structured output parsing, artifact hashes              |
| Product interaction broker | Good foundation | trusted policy context, default real evidence, durable runtime truth   |
| Product event broker       |         Partial | durable event provider, replay, DLQ, backpressure                      |
| Plugin interaction         |         Partial | broker parity, graph/cycle controls, evidence                          |
| Artifact manifests         |         Partial | strong hash/provenance, SBOM only when real                            |
| Deployment                 |         Partial | environment topology, rollout strategy, rollback proof                 |
| Release/promotion/rollback |         Partial | approval/risk integration, impact analysis, post-rollback verification |
| Observability              |         Partial | product/interaction/plugin SLO dashboards                              |
| Performance                |         Partial | affected-surface execution and caching proof                           |

### 6.2 Digital Marketing

| Workflow                 |         Status | Missing                                               |
| ------------------------ | -------------: | ----------------------------------------------------- |
| Lifecycle surfaces       |       Existing | full workflow correctness proof                       |
| Campaign activation      |        Partial | broker-backed PHR consent enforcement                 |
| Lead capture event       |       Declared | durable event publish/subscribe workflow              |
| Notification preferences | Handler exists | real persistence/domain integration proof             |
| Google Ads connector     |        Partial | production connector readiness, retries, auth/secrets |
| Reporting/dashboard      |        Partial | data correctness and E2E visual verification          |
| Consent/privacy          |      Improving | full marketing consent lifecycle proof                |

### 6.3 PHR

| Workflow                |           Status | Missing                                                     |
| ----------------------- | ---------------: | ----------------------------------------------------------- |
| Lifecycle surfaces      |         Existing | full healthcare workflow proof                              |
| FHIR                    |          Partial | stricter R4 validation and provider conformance             |
| Consent                 |        Improving | patient-facing consent workflow and broker usage everywhere |
| PII classification      | Declared/partial | runtime enforcement and tests                               |
| Audit evidence          | Declared/partial | complete user access history and evidence UX                |
| Tenant data sovereignty | Declared/partial | runtime data placement/provenance proof                     |
| i18n                    |       Incomplete | PHR scorecard shows i18n false                              |
| Rollback                |   Target-partial | healthcare post-rollback gates and approval contract        |

---

## 7. Stability and Bug Risk Register

| ID      | Severity | Risk                                                               | Evidence / reason                                            | Fix                                                        |
| ------- | -------: | ------------------------------------------------------------------ | ------------------------------------------------------------ | ---------------------------------------------------------- |
| BUG-001 |       P0 | Product interaction evidence can be no-op by default               | Broker builder default writer is no-op                       | Require real writer outside tests                          |
| BUG-002 |       P0 | Caller can influence policy result through `policyContext`         | Policy evaluator trusts map values                           | Resolve policy from trusted providers/contracts            |
| BUG-003 |       P0 | Replay cache may return wrong result for reused interaction ID     | Replay key lacks payload hash/actor/provider/consumer detail | Add request fingerprint and idempotency conflict detection |
| BUG-004 |       P1 | In-memory completed interaction cache can grow unbounded           | `ConcurrentHashMap` no retention visible                     | Add TTL/retention and persistent idempotency store         |
| BUG-005 |       P1 | Event broker lacks durable delivery semantics                      | Sequential dispatch only                                     | Add durable event provider, retry/DLQ/replay               |
| BUG-006 |       P1 | Some root scripts may fail without `gradlew` in PATH               | bare `gradlew` scripts                                       | Normalize wrapper invocation                               |
| BUG-007 |       P1 | Evidence scorecards may hide shallow checks                        | Boolean dimension coverage                                   | Add maturity-weighted evidence scoring                     |
| BUG-008 |       P1 | PHR i18n incomplete                                                | scorecard marks false                                        | Complete i18n and add gate                                 |
| BUG-009 |       P1 | Rust/Python adapters may pass without deep artifact/test semantics | early adapters                                               | Add real fixtures and structured parsers                   |
| BUG-010 |       P2 | CLI summaries still reveal implementation internals                | provider mode visible                                        | Introduce simple UX and advanced mode                      |

---

## 8. Expanded Scope Recommendations

The user asked to expand scope if needed. Based on the current state, production readiness requires expanding beyond Kernel mechanics into these areas:

### 8.1 Product workflow correctness

Add feature-completeness audits for:

- DMOS campaign lifecycle from plan → consent → activation → performance → reporting.
- PHR patient record lifecycle from data entry/import → consent → sharing → audit history.
- Cross-product consent revoke → DMOS audience disable.
- PHR care plan notification → DMOS preference lookup.

### 8.2 Data correctness and domain invariants

Add product-specific invariant tests:

- no campaign activation without required consent posture
- no PHR sharing without consent and data sovereignty policy
- no notification preference access across tenants/workspaces
- no stale interaction evidence used for a new purpose

### 8.3 Production operational readiness

Add operational tests:

- broker evidence writer outage
- Data Cloud provider unavailable
- event subscriber failure
- Docker unavailable
- Rust/Python toolchain missing
- partial deploy failure
- rollback failure and post-rollback verification failure

### 8.4 Performance and scalability

Add metrics and budgets:

- lifecycle plan latency
- adapter preflight latency
- product interaction p95/p99 latency
- event broker throughput
- evidence persistence latency
- affected-surface detection time
- large repo registry generation time

---

## 9. Prioritized Implementation Plan

### Phase 0 — No new features; production truth hardening

**Goal:** make current “pass” evidence more trustworthy.

Tasks:

1. Add maturity levels to `kernel-implementation-plan-progress.json`.
2. Add gate proof type: static, contract, runtime, failure-injected, production-observed.
3. Normalize `gradlew` wrapper invocation.
4. Add evidence freshness/staleness checks.
5. Add “source changed but evidence not regenerated” detection.

Validation:

- `pnpm check:kernel-implementation-plan-coverage`
- `pnpm check:product-release-readiness`
- new `pnpm check:evidence-maturity`
- new `pnpm check:script-portability`

### Phase 1 — Product interaction production hardening

Tasks:

1. Require real evidence writer in non-test broker profile.
2. Add trusted policy context resolver.
3. Enforce manifest policy at broker level.
4. Add payload/request fingerprint to replay key.
5. Add TTL and retention to completed interaction cache.
6. Add Data Cloud-backed evidence writer integration.
7. Add broker factory per environment.

Validation:

- `pnpm check:product-interaction-broker`
- `pnpm check:interaction-runtime-truth`
- `pnpm check:cross-product-interaction-flows`
- new forged-policy-context tests
- new idempotency conflict tests

### Phase 2 — Event interaction production hardening

Tasks:

1. Add durable event provider.
2. Add topic registry and schema validation.
3. Add event idempotency.
4. Add retry/DLQ.
5. Add per-subscriber timeout and isolation.
6. Add replay proof.
7. Add PHR consent-revoked → DMOS audience disabled flow.

Validation:

- `pnpm check:cross-product-interaction-flows`
- new `pnpm check:product-event-interaction-broker`
- event replay/DLQ tests

### Phase 3 — Polyglot production proof

Tasks:

1. Add four-language fixture product.
2. Add Rust service, worker, SDK fixture.
3. Add Python FastAPI service, worker, library fixture.
4. Parse cargo/pytest outputs into normalized lifecycle test results.
5. Add artifact hash/provenance for Rust/Python outputs.
6. Add environment blocked tests for missing toolchains.

Validation:

- `pnpm check:rust-adapter-conformance`
- `pnpm check:python-adapter-conformance`
- `pnpm check:polyglot-product-fixture`

### Phase 4 — Studio and CLI usability

Tasks:

1. Product control tower page.
2. Lifecycle action launcher.
3. Product interaction graph.
4. Plugin interaction graph.
5. Evidence and runtime-truth viewers.
6. Recovery guidance view.
7. Hide provider mode unless advanced mode is enabled.

Validation:

- Studio component tests
- Studio Playwright lifecycle journey
- Studio interaction graph E2E

### Phase 5 — Product workflow feature completeness

Tasks:

1. DMOS campaign activation uses brokered PHR consent status.
2. DMOS lead-captured event is durable and auditable.
3. PHR consent revoke triggers governed event.
4. PHR care-plan notification checks DMOS preference.
5. PHR i18n completion.
6. PHR rollback readiness: stable deployment history, artifact selection, healthcare verification gates, approval contract.

Validation:

- DMOS E2E workflows
- PHR healthcare workflows
- cross-product interaction workflows
- PHR i18n gate
- rollback proof tests

---

## 10. Focused Validation Plan, Deferring Long-Running Work

Do not run full `check:phase8` repeatedly during fix cycles. Use focused checks:

### For product interaction fixes

```bash
pnpm check:product-interaction-contracts
pnpm check:product-interaction-broker
pnpm check:interaction-runtime-truth
pnpm check:cross-product-interaction-flows
```

### For polyglot adapter fixes

```bash
pnpm check:java-adapter-conformance
pnpm check:typescript-web-adapter-conformance
pnpm check:rust-adapter-conformance
pnpm check:python-adapter-conformance
pnpm check:polyglot-product-fixture
```

### For release evidence fixes

```bash
pnpm check:product-registry
pnpm check:affected-product-strict-release-profile
pnpm check:kernel-implementation-plan-coverage
pnpm check:product-release-readiness
```

### For product UX/completeness fixes

```bash
pnpm check:product-ui-contracts
pnpm check:product-a11y-route-matrix
pnpm check:i18n-conformance
pnpm check:audited-e2e-workflow
```

### Deferred until final stabilization

```bash
pnpm check:phase8
pnpm check:world-class-platform-readiness
```

Run these only after focused failures are fixed.

---

## 11. Definition of Production-Ready for This Platform

The platform can claim production-ready Kernel status only when:

1. Product teams can register and run products without knowing platform internals.
2. Java, TypeScript, Rust, and Python surfaces have real fixture products and lifecycle proofs.
3. All critical product interactions go through the broker.
4. Broker policy is resolved from trusted providers and manifest policy, not caller-supplied flags.
5. Evidence persistence is mandatory in production.
6. Product event interactions support durable delivery, retry, DLQ, replay, and idempotency.
7. PHR i18n is complete.
8. PHR rollback readiness is no longer target-partial.
9. Release scorecards measure maturity and behavior, not only gate existence.
10. Studio and CLI provide simple plan/explain/execute/recover flows.
11. No product implements platform lifecycle code locally.
12. No product imports another product’s internals.
13. No environment-blocked action is reported as success.
14. Performance budgets exist and are enforced for lifecycle, interaction, and adapter execution.

---

## 12. Final Expert Assessment

This commit is a strong evidence refresh and shows the platform is converging. The Kernel is no longer just a lifecycle script layer: it now has polyglot adapters, product interaction brokers, event brokers, interaction evidence concepts, product manifests with schema hashes, and release readiness evidence.

The next phase must shift from “gate coverage” to “runtime production behavior.” The biggest production-readiness work is not adding more checklist scripts; it is hardening the broker/runtime paths, replacing trusted caller metadata with platform-resolved policy, making evidence persistence fail-closed, completing product workflows, finishing PHR i18n, and proving Rust/Python with real products.
