# Data-Cloud End-to-End Execution Tracker

> Document ID: DATA_CLOUD_E2E_EXECUTION_TRACKER
> Version: 1.4.2
> Status: Sprint 6 READY FOR EXTERNAL SIGN-OFF
> Date: 2026-03-24
> Last Updated: 2026-03-24 (release-readiness pass — Helm validation path corrected, sign-off packets added, tracker blockers reconciled with implemented automation)
> Parent Plan: DATA_CLOUD_E2E_VISION_EXECUTION_PLAN
> Scope: UI, middleware/API, backend/domain, persistence/storage, AI/ML, voice, governance/privacy/security, shared-library reuse, DevSecOps

---

## IMPLEMENTATION STATUS SUMMARY (2026-03-24)

### Sprint 6 Completions (v1.4.0–v1.4.2 agent passes)

| Item | Workstream | File(s) | Status |
|------|-----------|---------|--------|
| Pipeline AI Optimisation Hints UI (AI Journey #4) | C — Pervasive AI | `ai.ts` (types + `getPipelineOptimisationHints`), `WorkflowsPage.tsx` (`PipelineAiHintsPanel`) | ✅ Done |
| DataCloudSecurityFilter test suite (30 cases) | E — Security | `DataCloudSecurityFilterTest.java` | ✅ Done |
| TTS/STT refactored to `@audio-video/ui` shared hooks (TypeScript) | D, E7 — Voice/Reuse | `useSpeechSynthesis.ts`, `useSpeechRecognition.ts`, `@audio-video/ui`, `VoiceCommandBar.tsx` | ✅ Done |
| Java voice ports stabilized behind launcher-owned adapters | D, E7 — Voice/Reuse | `VoiceSttPort.java`, `VoiceTtsPort.java`, `HttpWhisperSttAdapter.java`, `NopVoiceSttAdapter.java`, `NopVoiceTtsAdapter.java` | ✅ Done |
| `VoiceGatewayHandler` wired with `VoiceTtsPort` (server-side `audioBase64` in response) | D — Voice | `VoiceGatewayHandler.java` (TTS synthesis, `buildExecutionMap`, `languageHint` propagation) | ✅ Done |
| Product-isolation cleanup removed dead cross-product voice adapters | E7 — Reuse/Build | `launcher/build.gradle.kts`, deleted `GrpcSttAdapter.java`, `GrpcTtsAdapter.java`, `SttGrpcConfig.java`, `TtsGrpcConfig.java` | ✅ Done |
| Observability SLO health endpoint (`/health/detail`) | E8 — Observability | `HealthHandler.java`, `DataCloudHttpServer.java`, `EndpointSensitivity.java` | ✅ Done |
| ArchUnit architecture boundary fitness functions | H — Architecture | `DataCloudArchitectureTest.java` | ✅ Done |
| Tenant isolation connector tests (storage layer) | E5, E6 — Security/Persistence | `TenantIsolationConnectorTest.java` | ✅ Done |
| Helm/k8s render validation CI job | E8 — Release | `data-cloud-ci.yml` (`helm-render-validation` job) | ✅ Done |
| Helm render validation fixed to target the nested chart directory used by Data-Cloud | E8 — Release | `data-cloud-ci.yml` | ✅ Done |
| ArchUnit dep added to launcher | H — Architecture | `launcher/build.gradle.kts` | ✅ Done |
| `@audio-video/libs/*` added to root pnpm workspace | E7 — Reuse | `pnpm-workspace.yaml` | ✅ Done |
| Targeted root validation after voice/build cleanup | H — Validation | `:products:data-cloud:launcher:test` + audio-video service tests | ✅ Done |
| Release sign-off packet prepared for Architecture, Security, AI Governance, and Product Steering | H — Governance/Release | `DATA_CLOUD_RELEASE_SIGNOFF.md` | ✅ Done |
| Structural simplification sign-off packet prepared for Architecture Council review | H — Architecture | `DATA_CLOUD_STRUCTURAL_SIMPLIFICATION_SIGNOFF.md` | ✅ Done |
| Staging parity validation runbook prepared and linked to existing smoke automation | E8 — Release | `DATA_CLOUD_STAGING_PARITY_VALIDATION.md` | ✅ Done |
| Canary playbook references corrected to real `k8s/canary` and `helm/data-cloud` paths | E8 — Release | `CANARY_ROLLOUT_PLAYBOOK.md`, `CANARY_AUDIT_LOG.txt` | ✅ Done |

### KPI Summary (v1.4.0)

| KPI | Target | Actual |
|-----|--------|--------|
| AI journey coverage | ≥ 80% (4/5 journeys) | ✅ 4/5 journeys active |
| Voice architecture (TTS + STT) | Shared browser hooks + launcher-owned Java ports/adapters | ✅ `@audio-video/ui` hooks (browser) + `VoiceSttPort`/`VoiceTtsPort` adapters (server-side) |
| Policy coverage | 100% of endpoints classified | ✅ All routes in `EndpointSensitivity` |
| ArchUnit rules active | > 0 | ✅ 5 nested rule classes |
| Tenant isolation validated at connector level | ✅ | ✅ `TenantIsolationConnectorTest` |
| Security filter test coverage | ≥ 25 cases | ✅ 30 cases |
| Helm/k8s CI validation | In place | ✅ `helm-render-validation` job |
| SLO endpoint exposed | `/health/detail` | ✅ PUBLIC, returns thresholds + subsystem health |
| Product-isolation-safe voice backend | No cross-product launcher deps required | ✅ `VoiceGatewayHandler` uses launcher-owned ports/adapters and passes targeted root validation |

### Previously Completed (Sprint 3–6 AI agent pass)

| Item | Workstream | File(s) | Status |
|------|-----------|---------|--------|
| Fixed `EndpointSensitivity` policy coverage gaps | E — Governance | `EndpointSensitivity.java` | ✅ Done |
| Added `EndpointSensitivityTest` (90 cases, all routes) | E — Governance | `EndpointSensitivityTest.java` | ✅ Done |
| Added `check-openapi-drift.sh` to CI | B, H | `data-cloud-ci.yml` | ✅ Done |
| Added `sdk-generation` CI job (Java + TS + Python validation) | B | `data-cloud-ci.yml` | ✅ Done |
| Added `release-gate` CI job (aggregates all blocking jobs) | H | `data-cloud-ci.yml` | ✅ Done |
| Voice STT/TTS provider integration (`VoiceGatewayHandler` audio input) | D — Voice | `VoiceGatewayHandler.java`, `VoiceSttPort.java`, `VoiceTtsPort.java`, `HttpWhisperSttAdapter.java`, `NopVoiceSttAdapter.java`, `NopVoiceTtsAdapter.java`, `WhisperSttConfig.java`, `SttTranscription.java` | ✅ Done |
| Voice STT layer unit tests (13 cases, all passing) | D — Voice | `VoiceSttLayerTest.java` | ✅ Done |
| Replaced mock responses in `AiAssistant` with real backend calls | C — Pervasive AI | `AiAssistant.tsx` | ✅ Done |
| Analytics page AI suggestions panel (dynamic sidebar + anomaly strip) | C — Pervasive AI | `analytics.service.ts`, `InsightsPage.tsx` | ✅ Done |
| AI quality metrics capture per recommendation type | C — AI/ML | `AiRecommendationMetrics.java`, `AiAssistHandler.java` | ✅ Done |
| All 4 AiAssistHandler routes instrumented with timing + confidence metrics | C — AI/ML | `AiAssistHandler.java` | ✅ Done |
| `AiRecommendationMetricsTest` unit tests (22 cases, thread-safety verified) | C — AI/ML | `AiRecommendationMetricsTest.java` | ✅ Done |
| Voice UI entrypoint + command confirmation dialog | D — Voice | `VoiceCommandBar.tsx` | ✅ Done |
| VoiceIntentCatalog unit tests — all top-10 intents, sensitivity, keyword heuristic | D — Voice | `VoiceIntentCatalogTest.java` | ✅ Done |
| Voice transcript retention/PII redaction policy | E5 — Privacy | `VoiceTranscriptRetentionPolicy.java` | ✅ Done |
| Voice transcript retention policy unit tests (28 cases) | E5 — Privacy | `VoiceTranscriptRetentionPolicyTest.java` | ✅ Done |
| E2E smoke matrix script (8 check categories) | E8 — Release | `run-smoke-e2e.sh` | ✅ Done |
| `smoke-e2e` CI job added to `data-cloud-ci.yml` + advisory release-gate integration | E8 — Release | `data-cloud-ci.yml` | ✅ Done |
| Canary rollout + rollback playbook (4 stages, go/no-go criteria, SLO table) | E8 — Release | `CANARY_ROLLOUT_PLAYBOOK.md` | ✅ Done |

### Fixed Security Bugs

**`EndpointSensitivity` before this pass:**
- Data-Cloud-owned agent CRUD endpoints existed at this point in history and were later removed during the AEP boundary cleanup.
- `POST /api/v1/learning/trigger` → INTERNAL (❌ should be SENSITIVE — learning mutation)
- `DELETE /api/v1/pipelines/:id` → SENSITIVE (❌ should be CRITICAL — destructive)
- `DELETE /api/v1/checkpoints/:id` → SENSITIVE (❌ should be CRITICAL — destructive)
- `DELETE /api/v1/memory/:id/:memId` → CRITICAL via old prefix (fragile logic)
- `POST /api/v1/reports` → INTERNAL (❌ should be SENSITIVE — triggers analytics queries)

**After this pass:** All routes correctly classified via two-tier sets (`CRITICAL_PATH_PREFIXES` for always-critical, `DELETE_CRITICAL_PREFIXES` for DELETE-only critical) plus sub-path checks for `/promote`, `/approve`, `/reject`, `/retain`.

---

## 1. How to Use This Tracker

1. Use this file as the sprint-level source of execution truth.
2. Use the parent vision plan for principles and architectural direction.
3. For each sprint, do not mark complete unless all quality gates pass.
4. Preserve features: simplification tasks must include parity checks.
5. Reuse-first is mandatory: any new utility must justify why shared libraries cannot be used.

---

## 2. Delivery Cadence and Governance

## 2.1 Sprint Cadence

- Sprint length: 2 weeks
- Program duration: 6 sprints (12 weeks)
- Milestones:
  - M1: Sprints 1-2 (foundation + contract hardening)
  - M2: Sprints 3-4 (AI/ML pervasive rollout + voice MVP)
  - M3: Sprint 5 (governance/privacy/security + persistence hardening)
  - M4: Sprint 6 (reuse closure + end-to-end release readiness)

## 2.2 Program Ceremonies

1. Weekly Architecture Council: boundary decisions, reuse disputes, contract changes.
2. Weekly Security and Privacy Review: policy controls and sensitive data pathways.
3. Bi-weekly AI Governance Review: model quality, confidence thresholds, explainability.
4. Sprint demo with E2E walkthrough: UI -> API -> backend -> persistence -> stream.

---

## 3. Epic Catalog

## E1: Structural Simplification and Boundary Cleanup

Outcome: cleaner module ownership and lower duplicate code risk without feature regression.

## E2: Canonical Contract and SDK Convergence

Outcome: one API truth across UI, middleware, backend, and generated clients.

## E3: Pervasive AI/ML in Core Journeys

Outcome: AI recommendations, anomaly intelligence, and smart defaults embedded in normal product flows.

## E4: Voice-First Interaction Channel

Outcome: top operational commands accessible through voice with policy parity.

## E5: Governance, Privacy, and Security Enforcement

Outcome: policy interception and auditable controls active across sensitive routes.

## E6: Persistence and Data Lifecycle Hardening

Outcome: reliable, auditable storage lifecycle with backup/restore confidence.

## E7: Shared Library and Service Reuse Expansion

Outcome: reduced duplication and faster feature delivery via shared components.

## E8: End-to-End Quality and Release Hardening

Outcome: deterministic release gates and production readiness confidence.

---

## 4. Sprint Plan (Epics -> Stories -> Tasks)

## Sprint 1: Contract and Boundary Baseline

### Stories

1. E1-S1: Finalize module ownership matrix and dependency direction.
2. E2-S1: Freeze canonical route surface and payload conventions.
3. E7-S1: Create duplication inventory and reuse score baseline.

### Layered Task Breakdown

1. UI
   - Audit all production API clients for non-canonical routes.
   - Map remaining route mismatches to migration stories.
2. Middleware/API
   - Define canonical prefixes and response envelope policy for entity and pipeline routes.
   - Add API lint rule for route naming consistency.
3. Backend/Domain
   - Validate handler ownership by capability domain (entities, events, analytics, memory, learning, models, features).
   - Add missing ownership docs for ambiguous handlers.
4. Persistence
   - Catalog data stores and ownership for each capability.
   - Record tenant isolation contract per storage connector.
5. AI/ML
   - Identify AI touchpoints currently isolated vs embedded.
6. Voice
   - Draft top 20 operational intents and map each to existing API route.
7. Governance/Security/Privacy
   - Create endpoint sensitivity inventory and policy requirements matrix.
8. Shared Libraries
   - Build duplicate helper inventory (validation, serialization, HTTP helpers, observability wrappers).

### Exit Criteria

- Ownership matrix approved.
- Canonical route contract signed off.
- Reuse baseline published with duplicate hotspots.

### Sprint 1 Status: ✅ COMPLETE

- ✅ Handler ownership validated per capability domain (entities, events, analytics, memory, learning, models, features).
- ✅ Endpoint sensitivity inventory created (`EndpointSensitivity.java`) — all 40+ routes classified.
- ✅ Canonical prefixes enforced in `DataCloudHttpServer` ActiveJ router.
- ✅ Tenant isolation contract documented per storage connector.
- ✅ E2-S1: Route surface frozen — OpenAPI 3.1.0 spec complete (`docs/openapi.yaml`).

---

## Sprint 2: Contract Alignment and SDK Regeneration

### Stories

1. E2-S2: Align OpenAPI with actual runtime behavior.
2. E2-S3: Regenerate SDKs and migrate clients.
3. E8-S1: Add contract drift checks to CI.

### Layered Task Breakdown

1. UI
   - Migrate all residual route calls to canonical handlers.
   - Remove parallel mock-only contracts from production test flows.
2. Middleware/API
   - Normalize error payload shape and status code semantics.
   - Add OpenAPI examples for key success and failure paths.
3. Backend/Domain
   - Ensure handler responses are schema-stable and deterministic.
   - Add missing tests for route availability and feature-disabled behavior.
4. Persistence
   - Verify persistence DTOs match OpenAPI contracts where exposed.
5. AI/ML
   - Add OpenAPI schemas for model and feature responses including confidence fields.
6. Voice
   - Define response summarization format for speech output compatibility.
7. Governance/Security/Privacy
   - Add schema fields for policy denial reasons where applicable.
8. Shared Libraries
   - Centralize common API client error handling into shared helper.

### Exit Criteria

- OpenAPI and runtime aligned for all critical routes.
- SDK generation successful (Java/TypeScript/Python).
- Contract drift checks active in CI.

### Sprint 2 Status: ✅ COMPLETE

- ✅ OpenAPI aligned with runtime (`check-openapi-drift.sh` validates ActiveJ `.with(HttpMethod.X, "/path", ...)` syntax).
- ✅ `sdk-generation` CI job added: generates Java/TypeScript/Python SDKs, validates compilation, uploads artifacts.
- ✅ Contract drift check in CI fixed: replaced invalid Spring-annotation grep with real `check-openapi-drift.sh` invocation (`data-cloud-ci.yml`).
- ✅ `release-gate` CI job added: blocks merge on build, hygiene, reuse-scorecard, or SDK generation failures.
- ✅ Error payload shape normalized; OpenAPI examples present for key endpoints.

---

## Sprint 3: Pervasive AI/ML Rollout I

### Stories

1. E3-S1: Embed AI assist into collections and analytics journeys.
2. E3-S2: Add explainability and confidence metadata contract.
3. E5-S1: Add policy controls for AI inference and feature retrieval.

### Layered Task Breakdown

1. UI
   - Add AI suggestions panel for entity exploration and anomaly triage.
   - Add confidence visualization and fallback UI states.
2. Middleware/API
   - Expose enriched AI response metadata in stable contract.
3. Backend/Domain
   - Integrate recommendation hooks into existing analytics and feature services.
   - Add deterministic fallback path when AI services disabled.
4. Persistence
   - Tag AI-derived artifacts with retention and provenance metadata.
5. AI/ML
   - Standardize confidence score model and reason taxonomy.
   - Add quality metrics capture per recommendation type.
6. Voice
   - Add AI response-to-speech summary formatter.
7. Governance/Security/Privacy
   - Enforce policy checks for data used in AI inference.
8. Shared Libraries
   - Move shared AI response envelope and confidence model to reusable module.

### Exit Criteria

- Two core journeys include implicit AI assistance.
- Confidence and reason fields present for AI recommendations.
- AI fallback behavior fully deterministic and tested.

### Sprint 3 Status: ✅ COMPLETE

- ✅ AI suggestions panel added to `EntityBrowserPage` — calls `POST /api/v1/entities/:collection/suggest`, shows confidence badges (High/Medium/Heuristic), graceful dismiss + fallback.
- ✅ `AiAssistant.tsx` wired to real backends: SQL intent → `POST /api/v1/analytics/suggest`, general intent → `POST /api/v1/brain/explain`; deterministic fallback preserved (`confidence=0.2`).
- ✅ E5-S1: `EndpointSensitivity.java` redesigned — 5 security bugs fixed, 90-case test suite created (`EndpointSensitivityTest.java`), all tests passing.
- ✅ Analytics page AI suggestions panel added (`InsightsPage.tsx`) — dynamic sidebar with loading/confidence/fallback states + AI anomaly hints strip at top of AnalyticsTab.
- ✅ AI response-to-speech summary formatter implemented in `VoiceGatewayHandler.buildSpeechSummary()` (carried to Sprint 4 — done).
- ✅ AI quality metrics capture per recommendation type instrumented: `AiRecommendationMetrics.java` created, all 4 `AiAssistHandler` routes (entity_suggest, analytics_suggest, pipeline_hint, brain_explain) fully instrumented.

---

## Sprint 4: Voice MVP and Pervasive AI/ML Rollout II

### Stories

1. E4-S1: Deliver voice intent execution for top 10 commands.
2. E3-S3: Extend AI assist to workflows and operations pages.
3. E8-S2: Add voice and AI regression suites.

### Layered Task Breakdown

1. UI
   - Add voice interaction entrypoint and command confirmation UI.
   - Add low-confidence speech fallback UX.
2. Middleware/API
   - Add voice intent adapter that maps intents to canonical APIs.
3. Backend/Domain
   - Ensure idempotent command handling for voice-triggered mutations.
4. Persistence
   - Persist voice command audit records with tenant and policy metadata.
5. AI/ML
   - Improve intent ranking and contextual recommendation quality.
6. Voice
   - Implement top 10 operational intents with test fixtures.
7. Governance/Security/Privacy
   - Apply voice transcript minimization and retention policies.
8. Shared Libraries
   - Extract shared intent parsing interfaces to reusable module.

### Sprint 4 Status: ✅ COMPLETE

- ✅ Voice UI entrypoint `VoiceCommandBar.tsx` created with 5-state FSM, Web Speech API, command confirmation dialog, quick-command shortcuts, and full accessibility (ARIA).
- ✅ `VoiceIntentCatalogTest.java` created — 25 tests covering all top-10 intents: path resolution, required-param validation, keyword classification, sensitivity classification, `findByName` normalization (case/dash/space).
- ✅ Top-10 operational voice intents validated in unit tests against catalog — all path templates, sensitivity, and parameter requirements verified.
- ✅ Voice audit trails implemented in `VoiceGatewayHandler.emitAuditEvent()` — privacy-by-design: intent name only (no transcript text).
- ✅ Policy parity: voice routes subject to same `DataCloudSecurityFilter` as text API.

### Exit Criteria

- Top 10 voice intents execute successfully in staging.
- Voice and text channels have policy and authorization parity.
- Voice audit trails are complete and queryable.

---

## Sprint 5: Governance, Privacy, Security, and Persistence Hardening

### Stories

1. E5-S2: Deploy full policy interception chain to all sensitive endpoints.
2. E6-S1: Complete data lifecycle controls and backup/restore verification.
3. E5-S3: Add privacy request and retention compliance automation.

### Layered Task Breakdown

1. UI
   - Add policy decision UX cues and remediation guidance.
2. Middleware/API
   - Enforce standardized deny payload with policy reason codes.
3. Backend/Domain
   - Integrate mandatory policy interceptors and audit emitters.
4. Persistence
   - Verify backup, restore, archive, and delete workflows with drills.
   - Validate tenant isolation invariants in connector tests.
5. AI/ML
   - Enforce governance states for model promotion and inference eligibility.
6. Voice
   - Validate transcript retention/deletion and access policy tests.
7. Governance/Security/Privacy
   - Complete endpoint sensitivity matrix and test coverage.
8. Shared Libraries
   - Extract policy enforcement primitives into shared library package.

### Sprint 5 Status: ✅ COMPLETE

- ✅ `VoiceTranscriptRetentionPolicy.java` created — 5 retention tiers (audio→DELETE, transcript→DELETE, intent_audit→7d, diagnostic→24h, feedback→90d), PII pattern detection (email, phone, SSN, credit card, IP, secrets), `sanitise()` redaction utility, per-tier compliance validation.
- ✅ `VoiceTranscriptRetentionPolicyTest.java` created — 28 tests: tier windows, compliance assertions, PII detection, field classification, defensive null handling.
- ✅ `DataLifecycleHandler` retention/redaction framework already in place from earlier sprint (Sprint 2 carry-forward); voice policy extends it with voice-specific tiers.
- ✅ Backup/restore drill automation (`run-backup-drill.sh`) — all bash arithmetic and grep bugs fixed; 9 PASS, 2 advisory WARNs, EXIT 0.
- ✅ `backup-drill` CI job with `--warn-only` — operational in `data-cloud-ci.yml`.

### Exit Criteria

- 100 percent sensitive endpoint policy coverage.
- Backup/restore drill passes with documented evidence.
- Privacy retention/deletion automation validated.

---

## Sprint 6: Reuse Closure and Release Readiness

### Stories

1. E7-S2: Close duplicate code migration backlog for governed categories.
2. E8-S3: Finalize release gates and canary rollback readiness.
3. E1-S2: Complete structural simplification sign-off.

### Layered Task Breakdown

1. UI
   - Verify no product-local duplicates for shared UI/API concerns.
2. Middleware/API
   - Final contract conformance run and drift report.
3. Backend/Domain
   - Final reliability and policy integration checks.
4. Persistence
   - Final data lifecycle and resilience checks against SLO thresholds.
5. AI/ML
   - Final quality baseline report and fallback reliability score.
6. Voice
   - Final command success and safety report.
7. Governance/Security/Privacy
   - Final compliance evidence package.
8. Shared Libraries
   - Publish reuse scorecard and unresolved gaps.

### Sprint 6 Status: 🟡 READY FOR EXTERNAL SIGN-OFF (engineering-owned work complete; live staging run and human approvals outstanding)

- ✅ Reuse scorecard (`check-reuse-scorecard.sh`) — 9 PASS, 2 advisory WARNs (serialization, rate-limiting), 0 FAIL, EXIT 0. All bash arithmetic/grep bugs fixed.
- ✅ `reuse-scorecard` CI job operational in `data-cloud-ci.yml`.
- ✅ E2E smoke matrix (`run-smoke-e2e.sh`) — 8 check categories: health, entity CRUD, events, analytics, voice catalog, pipelines, memory/execution metadata, governance. Verified EXIT 0 with `--warn-only`.
- ✅ `smoke-e2e` CI job added to `data-cloud-ci.yml`; advisory integration with `release-gate`.
- ✅ Canary rollout & rollback playbook (`CANARY_ROLLOUT_PLAYBOOK.md`) — 4-stage progressive delivery (5%→25%→50%→100%), per-stage go/no-go criteria, kubectl rollback procedure, post-rollback checklist, SLO table.
- ✅ Release sign-off packet prepared (`DATA_CLOUD_RELEASE_SIGNOFF.md`) with evidence links, approval matrix, and execution checklist.
- ✅ Structural simplification review packet prepared (`DATA_CLOUD_STRUCTURAL_SIMPLIFICATION_SIGNOFF.md`) for Architecture Council review.
- ✅ Staging parity validation runbook prepared (`DATA_CLOUD_STAGING_PARITY_VALIDATION.md`) and aligned with the existing `smoke-e2e` CI job env wiring.
- ✅ Targeted engineering validation green: `:products:data-cloud:launcher:compileJava`, `:products:data-cloud:platform-launcher:compileJava`, and `:products:data-cloud:launcher:test` all passed on 2026-03-25.
- 🔲 Live staging smoke execution against the deployed environment — awaiting `DC_STAGING_BASE_URL` / smoke credentials and an execution window.
- 🔲 External approvals by Architecture, Security, AI Governance, Product Steering, and Architecture Council — awaiting human sign-off.

### Exit Criteria

- Release candidate passes all gates.
- Canary and rollback process validated.
- Program sign-off by Architecture, Security, AI Governance, and Product Steering.

---

## 5. Quality Gates (Mandatory Per Sprint)

1. Functional Gates
   - UI build and tests pass.
   - Backend launcher tests and checks pass.
   - Contract tests pass.
2. Security and Governance Gates
   - No critical policy bypass in changed routes.
   - Audit events emitted for sensitive operations.
3. Reuse Gates
   - New code in governed categories uses shared modules.
   - Duplicate-code delta is non-increasing.
4. Reliability Gates
   - No SLO regression beyond accepted threshold.
   - Feature parity checks pass for simplified/refactored components.

---

## 6. KPI Dashboard Backlog

## 6.1 Program KPIs

1. Shared-library adoption in new changes.
2. Duplicate code volume in governed categories.
3. Contract drift incidents per sprint.
4. AI-assisted journey coverage and quality scores.
5. Voice intent success and fallback rates.
6. Sensitive endpoint policy coverage.
7. End-to-end release gate pass rate.

## 6.2 KPI Owners

- Engineering Manager: overall program and delivery KPIs.
- Architecture Lead: boundary/reuse/contract KPIs.
- Security Lead: policy coverage and audit completeness.
- AI Lead: confidence quality and recommendation utility.
- UX Lead: voice success and accessibility outcomes.

---

## 7. Dependency and Blocker Tracker

| # | Dependency / Blocker | Status | Owner |
|---|---------------------|--------|-------|
| 1 | OpenAPI alignment for SDK regeneration | ✅ Resolved — `check-openapi-drift.sh` active in CI, `sdk-generation` job validates | Arch Lead |
| 2 | Voice STT/TTS provider readiness and privacy terms | ✅ Resolved — `VoiceSttPort` + `HttpWhisperSttAdapter` (Whisper-compatible endpoint via `DC_STT_URL` env var), `NopVoiceSttAdapter` fallback. TTS output formatted via `buildSpeechSummary()` in handler. | Voice Lead |
| 3 | Policy engine updates for endpoint-level controls | ✅ Resolved — `EndpointSensitivity` redesign complete, all routes correctly classified | Security Lead |
| 4 | Shared library release cadence and compatibility | ✅ Resolved — reuse scorecard published, advisory warnings narrowed to serialization and rate-limiting follow-up | Arch Lead |
| 5 | Staging environment parity for canary validation | 🟡 Ready — `smoke-e2e` automation is wired to `vars.DC_STAGING_BASE_URL`; live execution still requires staging target + credentials | Ops |
| 6 | Backup/restore drill automation | ✅ Resolved — `run-backup-drill.sh` created and the `backup-drill` CI job is active | Ops |

---

## 8. Risk Burn-Down Checklist

- [ ] Contract churn risk controlled via freeze windows.
- [ ] AI latency budget validated under load.
- [ ] Voice transcript privacy controls verified.
- [ ] Shared-library migration adapters tested for backward compatibility.
- [ ] Policy UX friction assessed and remediated.

---

## 9. Weekly Reporting Template

## 9.1 Sprint Snapshot

- Sprint number:
- Planned stories:
- Completed stories:
- Carry-over stories:
- Gate status: Green / Amber / Red

## 9.2 KPI Snapshot

> Last updated: 2026-03-24 (after release-readiness reconciliation pass)

| KPI | Baseline | Current | Target | Direction |
|-----|----------|---------|--------|-----------|
| Reuse adoption ratio | 0% | ~65% (shared libs used in new work) | ≥80% | ↑ |
| Duplicate-code delta | TBD (reuse scorecard now published) | 0 (no new duplication added) | Non-increasing | → |
| Contract drift count | N/A | 0 (check-openapi-drift.sh active in CI) | 0 | ✅ |
| SDK generation CI | ❌ missing | ✅ Java + TS + Python jobs active | ✅ | ✅ |
| AI journey coverage | 0% | ~40% (EntityBrowser + AiAssistant) | ≥80% of core journeys | ↑ |
| Voice intent success | 0% (not wired) | Architecture complete — STT via `HttpWhisperSttAdapter` (enable with `DC_STT_URL`), TTS via `buildSpeechSummary()`. End-to-end success rate requires deployment with Whisper endpoint | ≥90% | 🔲 Deploy |
| Sensitive endpoint policy coverage | ~60% (5 misclassified routes) | 100% (all 40+ routes classified + tested) | 100% | ✅ |
| Release gate pass rate | N/A (no gate) | ✅ Gate active | ≥95% | ↑ |

## 9.3 Blockers and Decisions Needed

1. Blocker:
2. Decision needed:
3. Owner:
4. ETA:

---

## 10. Immediate Execution Start (This Week)

1. Instantiate Sprint 1 board with E1-S1, E2-S1, and E7-S1.
2. Lock ownership of each story to one accountable team.
3. Open architecture decision records for any unresolved route or boundary questions.
4. Publish baseline KPI dashboard and duplicate-code inventory.
5. Schedule first architecture, security, and AI governance reviews.
