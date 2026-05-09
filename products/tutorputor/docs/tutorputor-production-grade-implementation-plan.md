# TutorPutor Production-Grade Implementation Plan

**Repository:** `samujjwal/ghatana`  
**Product scope:** `products/tutorputor/**`  
**Audit commit:** `482df02f9b4bbb7d25821f093abd6320fb7ae315`  
**Plan basis:** 45 TutorPutor audit TODOs covering automated content generation, TutorPutor learner/admin/mobile apps, AI/ML, i18n, privacy, security, simulations, assessment, telemetry, backend contracts, DevOps, and testing.

---

## 0. Execution Contract

This implementation plan must be executed with **zero production compromises**.

### 0.1 Non-Negotiable Engineering Rules

1. **Correctness first**
   - Every behavior must be deterministic where determinism is required.
   - Every edge case must be explicitly handled.
   - Every computation, route, validation, scoring rule, content-generation decision, tenant boundary, and user-visible state must be testable and validated.

2. **No duplication**
   - Reuse existing `@ghatana/*` packages and TutorPutor modules.
   - Do not create parallel API clients, validation schemas, design components, AI wrappers, simulation contracts, event schemas, route registries, or scoring implementations.
   - If duplicate code exists, consolidate before expanding functionality.

3. **Composition over repetition**
   - Build small composable modules: validation engines, schema registries, generation pipeline stages, SimKit domain adapters, telemetry emitters, i18n providers, consent policies, dashboard widgets, and scoring services.
   - Avoid “god components,” “god services,” and mixed-responsibility utilities.

4. **Strict consistency**
   - Follow existing repo naming, package boundaries, route naming, schema naming, test naming, UI design-system patterns, folder conventions, and API error envelopes.
   - Any new structure must be documented and enforced with tests or lint checks.

5. **No mocks, stubs, placeholders, or fake data in production**
   - No `Math.random()` production metrics.
   - No “demo” values in dashboards.
   - No fake AI responses in production.
   - No fake content-generation statuses.
   - If a capability is incomplete, hide it behind a feature flag and expose a typed unavailable state.

6. **No shortcuts or hacks**
   - No frontend-only authorization, scoring, tenant filtering, i18n, or validation for production behavior.
   - No swallowing errors.
   - No broad `try/catch` that converts failures into successful empty states.
   - No untyped `Json` without schema validation.
   - No `any` or `ts-ignore` unless already allowed by a documented temporary allowlist with a removal task.

7. **Test rigor**
   - Tests must validate behavior, not implementation theater.
   - Every implementation PR must include meaningful unit, integration, contract, accessibility, and E2E coverage appropriate to the change.
   - Use deterministic seeds and golden datasets for generation, scoring, simulation, AI grading, and telemetry.

8. **Observability and resilience**
   - Every production path must have structured logs, correlation IDs, typed errors, retry/failure semantics, metrics, and alertable signals where applicable.
   - AI/ML, content generation, simulation telemetry, and privacy/security workflows must be observable from day one.

---

## 1. Target Architecture Principles

### 1.1 Canonical Product Boundaries

| Surface | Canonical Location | Rule |
|---|---|---|
| Learner web app | `products/tutorputor/apps/tutorputor-web/**` | Uses shared route/data/i18n/API/design-system abstractions only |
| Admin/CMS app | `products/tutorputor/apps/tutorputor-admin/**` | No random/fake operational metrics; all panels API-backed |
| Mobile app | `products/tutorputor/apps/tutorputor-mobile/**` | Must have real tests; no `passWithNoTests` |
| Platform backend | `products/tutorputor/services/tutorputor-platform/**` | Source of truth for scoring, AI governance, validation, consent, tenant scope |
| Content-generation services | `products/tutorputor/services/tutorputor-content-generation/**` and platform workers | No auto-publish without gated validation |
| Core/domain library | `products/tutorputor/libs/tutorputor-core/**` | Domain types, validation contracts, canonical scoring/simulation/telemetry definitions |
| UI library | `products/tutorputor/libs/tutorputor-ui/**` | Shared TutorPutor UI patterns composed from `@ghatana/*` |
| AI library | `products/tutorputor/libs/tutorputor-ai/**` | Canonical AI request/response/prompt/versioning interfaces |
| API contracts | `products/tutorputor/api/**` and generated clients | Every route must be contract-owned and tested |
| Docs | `products/tutorputor/docs/**` | Architecture/design/vision docs only; no stale execution notes |

### 1.2 Canonical Cross-Cutting Services

Implement or consolidate these as single-source modules:

1. `TutorPutorI18nProvider`
2. `TutorPutorApiClient`
3. `TenantContextResolver`
4. `ConsentPolicyEngine`
5. `AiGovernanceAuditService`
6. `GeneratedContentValidationPipeline`
7. `GenerationFeatureFlagPolicy`
8. `SimKitTemplateRegistry`
9. `SimulationTelemetrySchemaRegistry`
10. `AssessmentScoringService`
11. `ConfidenceCalibrationService`
12. `LearningEvidenceService`
13. `LearningTelemetryIngestService`
14. `RouteOwnershipRegistry`
15. `DashboardMetricsService`
16. `PrivacyRedactionService`
17. `CriticalJourneySeedDataFactory`

---

## 2. Delivery Phases

This work should be delivered in phases that reduce risk and prevent partially working production flows.

### Phase 1 — Product Safety Baseline

**Goal:** Stop unsafe/fake/incomplete production behavior before expanding features.

Scope:
- Disable generated content auto-publish unless fully gated.
- Remove fake/random production metrics from admin UI.
- Add feature flags for incomplete autonomous generation, micro-viva, localization rollout, and mobile production readiness.
- Add tenant context enforcement for knowledge base routes.
- Add AI audit redaction.
- Add CI failures for `passWithNoTests` in product apps.

Exit criteria:
- No production dashboard uses random values.
- No generated content can publish without policy gates.
- No knowledge-base route uses default tenant scope.
- AI audit logs are redacted.
- CI fails for zero-test mobile execution.

Mapped TODOs:
- TODO 1, 5, 9, 14, 37, 44

### Phase 2 — Canonical Contracts and Infrastructure

**Goal:** Establish shared contracts before feature implementation.

Scope:
- i18n provider, catalogs, locale propagation.
- OpenAPI/route ownership verification.
- API client generation or shared typed client adoption.
- Telemetry schema registry.
- CBM scoring contract.
- SimKit template registry.
- Consent policy matrix.
- JSON schema validation for string/JSON payload fields.

Exit criteria:
- Every public API route has contract ownership.
- Every learner/admin app is under i18n context.
- Telemetry events are versioned and schema-validated.
- CBM scoring rules are centralized and golden-tested.
- Generated simulations come from domain templates.
- Consent categories are explicit and testable.

Mapped TODOs:
- TODO 3, 8, 17, 18, 20, 22, 28, 38, 39, 40, 41

### Phase 3 — Learning Correctness and AI Grounding

**Goal:** Make learning, scoring, AI tutor, and generated content evidence-based.

Scope:
- Ground AI tutor in canonical module/content/claim/evidence/simulation state.
- Fix domain/difficulty mapping.
- Ground question generation in content evidence.
- Implement AI fallback-to-human-review for grading.
- Add mastery update from canonical claim/evidence/concept IDs.
- Replace heuristic knowledge validation with governed semantic evidence search.
- Implement generated-content review screen.

Exit criteria:
- AI prompts include canonical, tenant-scoped learning context.
- AI-generated questions fail closed when evidence is insufficient.
- Failed AI grading never penalizes learners.
- Mastery is updated from real evidence, not heuristics.
- Reviewer can inspect generated content provenance, model version, prompt version, evidence, validation, accessibility, and publish eligibility.

Mapped TODOs:
- TODO 7, 10, 11, 12, 13, 15, 16, 25, 26

### Phase 4 — Simulation-First Content and Assessment

**Goal:** Make SimKit, CBM, and assessment flows production-grade.

Scope:
- Domain-specific SimKit templates.
- Deterministic simulation seeds.
- Multi-item generated assessments.
- Simulation-based assessment across all relevant domains.
- Frontend confidence capture.
- Assessment results route.
- Assessment status vocabulary alignment.
- Accessible simulations.

Exit criteria:
- Generated simulation artifacts are deterministic and domain-specific.
- Assessments include simulation/process evidence where applicable.
- Learner cannot submit answers without confidence.
- Results route exists and is tested.
- Status vocabulary is aligned across DB, API, and UI.
- Simulations pass keyboard, screen-reader, reduced-motion, and high-contrast tests.

Mapped TODOs:
- TODO 2, 3, 4, 21, 23, 24, 35

### Phase 5 — Product Journeys and Dashboards

**Goal:** Make the TutorPutor app complete across learner, instructor, admin, and content-author journeys.

Scope:
- Learner critical journey.
- Instructor analytics.
- Admin content-generation dashboard backed by real metrics.
- Split `UnifiedContentStudio`.
- Remove placeholder/duplicate routes.
- Apply shared design system.
- Implement or complete micro-viva.
- Offline/PWA sync.

Exit criteria:
- Learner journey passes end-to-end.
- Instructor can review mastery, risk, CBM calibration, simulation evidence, and viva queue.
- Admin dashboard values reconcile with backend metrics.
- Content studio is modular and testable.
- No dead placeholder learner route.
- No duplicate admin route.
- Offline sync supports progress, simulation telemetry, and assessments.

Mapped TODOs:
- TODO 6, 27, 29, 30, 31, 32, 33, 34, 36

### Phase 6 — Compliance, Social Safety, Mobile, Observability, and Hardening

**Goal:** Complete production readiness across privacy, security, mobile, social, monitoring, and critical journey tests.

Scope:
- Granular consent enforcement.
- Social/child-safety moderation.
- Mobile tests and production readiness.
- Product-level critical E2E suites.
- Content-generation observability and alerts.
- Full CI enforcement.

Exit criteria:
- Consent behavior is tested by endpoint and data type.
- Child-safety restrictions are enforced end-to-end.
- Mobile has meaningful tests.
- Critical journeys are required in CI.
- Content-generation operations are observable and alertable.

Mapped TODOs:
- TODO 37, 42, 43, 44, 45

---

## 3. Workstream A — Automated Content Generation

### A1. Auto-Publish Safety Gate

Mapped TODO:
- TODO 1

Implementation tasks:
1. Create `GenerationPublishPolicy`.
2. Add production feature flag `TUTORPUTOR_AUTONOMOUS_CONTENT_AUTO_PUBLISH_ENABLED`.
3. Add policy inputs: validation score, semantic evidence score, simulation validation status, assessment validation status, accessibility status, source/citation completeness, SME/human-review requirement, AI risk score, tenant policy, age-band policy, and content domain risk.
4. Make `executeEvaluation(...)` return a typed publish decision: `publishable`, `review_required`, `blocked`, or `invalid`.
5. Store publish decision with model version, prompt version, evaluator version, validation version, timestamp, and reviewer override metadata.
6. Ensure production cannot auto-publish unless the feature flag is enabled, all gates pass, tenant policy allows it, content type is eligible, and no safety/compliance issue exists.

Acceptance criteria:
- Default behavior is review-required, not publish.
- Auto-publish cannot be enabled by code path alone; it requires explicit config and policy pass.
- All failed gates are visible to reviewers.

Tests:
- Unit tests for every policy branch.
- Integration test for generation job lifecycle.
- E2E admin review test.
- Regression test proving low-score content cannot publish.
- Test proving feature flag off overrides otherwise valid content.

### A2. Domain-Specific SimKit Template Registry

Mapped TODOs:
- TODO 2
- TODO 3
- TODO 35

Implementation tasks:
1. Create `SimKitTemplateRegistry`.
2. Define domain adapters: `MathGraphSimulationTemplate`, `PhysicsMotionSimulationTemplate`, `ChemistryMoleculeSimulationTemplate`, `BiologyVitalsSimulationTemplate`, `TechnologyLogicSchedulingTemplate`, `BusinessSupplyDemandTemplate`, and `EngineeringConstraintDesignTemplate`.
3. Each template must define learning claim types, controls, parameter bounds, seed strategy, outputs, telemetry events, failure states, accessibility behavior, and export behavior.
4. Replace generic `primary_parameter` generation with registry lookup.
5. Use stable seed derivation: `hash(tenantId + generationRequestId + claimId + templateId + version)`.
6. Add simulation versioning: `templateId`, `templateVersion`, and `schemaVersion`.
7. Add accessibility metadata: keyboard map, reduced-motion behavior, chart/table text alternative, screen-reader summary, high-contrast encoding, and caption/transcript requirements for animations.

Acceptance criteria:
- No generated simulation uses generic placeholder controls.
- Same request produces the same generated simulation config.
- Every generated simulation is accessible by contract.

Tests:
- Golden generated output tests per domain.
- Deterministic seed test.
- Invalid bounds test.
- Keyboard navigation E2E for at least one simulation per domain.
- Accessibility audit for generated simulation player.

### A3. Generated Assessment Expansion

Mapped TODO:
- TODO 4

Implementation tasks:
1. Update generation request schema to include requested item count, required evidence IDs, required claim IDs, domain, difficulty, age band, and assessment mode.
2. Generate an item set, not a single item.
3. Required item mix: prediction, constructed response, simulation task, multiple choice where useful, and reflection/explanation.
4. Always include CBM metadata.
5. Store item generation provenance.
6. Add risk scoring for AI-generated assessments.
7. Require human review for high-stakes domains, medicine/clinical content, underage learner assessment, low evidence grounding, and new template versions.

Acceptance criteria:
- Assessment generation returns a coherent assessment bundle.
- Items link to claims and evidence.
- Simulation-based tasks are available across all simulation-enabled domains.

Tests:
- Assessment bundle schema tests.
- Golden assessment tests.
- Simulation-based item tests.
- Human-review gating tests.

### A4. Generated Content Review UI

Mapped TODO:
- TODO 7

Implementation tasks:
1. Add admin route `/content/review/generated/:artifactId`.
2. Show generated artifact, prompt version, model version, source evidence, evidence score, curriculum alignment, simulation config, assessment config, validation errors, accessibility report, i18n readiness status, privacy/security flags, and publish policy decision.
3. Add actions: approve, reject, request changes, send to SME, regenerate with constraints, and publish when eligible.
4. Add audit log for every reviewer action.

Acceptance criteria:
- Reviewer can make an informed decision without reading logs.
- Every action is persisted and auditable.
- Publish action is disabled unless policy allows.

Tests:
- Admin E2E review tests.
- Access-control tests.
- Audit-log tests.
- Publish-disabled state tests.

---

## 4. Workstream B — Knowledge Base, Evidence, and Validation

### B1. Extend Validation API to Simulations and Animations

Mapped TODO:
- TODO 8

Implementation tasks:
1. Update route body schema for validation content types: `claim`, `example`, `explanation`, `task`, `simulation`, and `animation`.
2. Add `SimulationContentValidator` and `AnimationContentValidator`.
3. Validate scientific/math correctness, parameter bounds, failure states, evidence linkage, accessibility metadata, telemetry contract, deterministic seed, and export behavior.
4. Return typed validation results.

Acceptance criteria:
- Simulation and animation validation cannot be bypassed.
- Failed validation blocks auto-publish.

Tests:
- API schema tests.
- Simulation validation negative tests.
- Animation validation accessibility tests.

### B2. Tenant Scope Enforcement

Mapped TODO:
- TODO 9

Implementation tasks:
1. Add `TenantContextResolver` for knowledge-base routes.
2. Remove all fallback usage of tenant `default` in production request paths.
3. Pass tenant context into concept search, examples search, curriculum search, evidence bundle lookup, and validation context.
4. Add failure behavior when tenant is missing.

Acceptance criteria:
- Cross-tenant retrieval is impossible.
- Missing tenant returns a typed error.

Tests:
- Cross-tenant negative tests.
- Missing tenant tests.
- Route integration tests.

### B3. Governed Knowledge and Curriculum Sources

Mapped TODO:
- TODO 10

Implementation tasks:
1. Replace empty retrieval functions with governed adapters.
2. Every source must define source type, trust level, license, curriculum standard, version, retrieval timestamp, and canonical URL or internal ID.
3. If no evidence exists, validation must return `review_required` and `insufficient_evidence`.
4. Do not silently pass.

Acceptance criteria:
- Empty evidence cannot be interpreted as valid.
- Every validation result explains evidence sufficiency.

Tests:
- Missing-evidence tests.
- Trusted-source tests.
- Expired-source tests.
- Low-trust-source tests.

### B4. Semantic Evidence Search

Mapped TODO:
- TODO 11

Implementation tasks:
1. Add canonical embedding abstraction or reuse existing platform AI integration.
2. Store evidence embeddings in pgvector.
3. Add semantic retrieval for claims, examples, explanations, tasks, misconceptions, and curriculum standards.
4. Add contradiction/near-miss scoring.
5. Keep deterministic lexical fallback for degraded mode.

Acceptance criteria:
- Paraphrased claims retrieve correct evidence.
- Contradictory claims are flagged.
- Tenant scope is preserved.

Tests:
- Semantic search golden tests.
- Contradiction tests.
- Tenant-scope vector tests.
- Degraded-mode tests.

---

## 5. Workstream C — AI Tutor, AI/ML, and Governance

### C1. Canonical Tutor Context Retrieval

Mapped TODOs:
- TODO 12
- TODO 13

Implementation tasks:
1. Replace regex parsing in `getModuleContext(...)`.
2. Build `LearningContextAssembler`.
3. Inputs: tenant ID, learner ID, module ID, current lesson/content block, simulation run ID, recent attempts, mastery profile, misconceptions, consent state, and locale.
4. Retrieved context: module title, domain, difficulty, objectives, claims, evidence, content block excerpts, simulation state, learner progress, and recent mistakes.
5. Fix domain mapping from canonical module domain, not `difficultyLevel`.

Acceptance criteria:
- AI tutor prompt is grounded in real module data.
- Domain and difficulty are separate.
- Missing context fails gracefully.

Tests:
- Prompt assembly snapshot tests.
- Multi-domain tests.
- Cross-tenant tests.
- Missing module tests.
- Consent-denied tests.

### C2. AI Audit Redaction

Mapped TODO:
- TODO 14

Implementation tasks:
1. Create `PrivacyRedactionService`.
2. Apply to learner prompt, AI response, simulation state, recent attempts, misconception summaries, and audit payload.
3. Redaction rules: PII pattern removal, size limits, sensitive category masking, and safe structured metadata retention.
4. Add audit payload versioning.

Acceptance criteria:
- Audit logs preserve governance without storing raw sensitive data.
- Redaction is deterministic and testable.

Tests:
- PII redaction tests.
- Large payload truncation tests.
- AI audit integration tests.

### C3. Grounded Question Generation

Mapped TODO:
- TODO 15

Implementation tasks:
1. Require module evidence before generating questions.
2. Generate from objectives, claims, content excerpts, simulations, and misconceptions.
3. Fail closed if grounding is insufficient.
4. Store prompt version, model version, evidence IDs, grounding score, and reviewer status.

Acceptance criteria:
- No generated question exists without evidence linkage.
- Content-poor modules produce review-required errors.

Tests:
- Rich-content generation tests.
- Content-poor failure tests.
- Cross-tenant evidence tests.

### C4. Locale-Aware AI Policy

Mapped TODO:
- TODO 16

Implementation tasks:
1. Add `AiLocalePolicy`.
2. Locale policy controls response language, units, reading level, cultural examples, citation style, and fallback behavior.
3. Remove ad hoc prompt suffixes.
4. Add locale into AI request context.

Acceptance criteria:
- AI behavior is locale-deterministic.
- Locale fallback is explicit.

Tests:
- AI response metadata tests for `en`, `es`, `hi`, and `zh`.
- Missing locale fallback tests.

### C5. Granular AI Consent

Mapped TODO:
- TODO 41

Implementation tasks:
1. Extend consent categories: AI tutor, AI grading, AI personalization, generated content interaction, simulation telemetry, learning analytics, and marketing analytics.
2. Enforce per endpoint.
3. Add denial behavior: explain unavailable feature, offer consent settings link, and do not collect disallowed data.

Acceptance criteria:
- Consent is not a single global AI switch.
- Every AI endpoint uses the consent policy engine.

Tests:
- Consent matrix tests.
- Endpoint allow/deny tests.
- Audit tests.

---

## 6. Workstream D — i18n and Localization

### D1. Canonical i18n Package and Provider

Mapped TODOs:
- TODO 17
- TODO 18

Implementation tasks:
1. Select or reuse canonical i18n library according to repo standards.
2. Add shared locale catalogs: `en`, `es`, `hi`, and `zh`.
3. Create `TutorPutorI18nProvider`.
4. Mount provider in web app, admin app, mobile app, and shared TutorPutor UI library.
5. Persist locale through profile setting, local storage for anonymous users, request header, and API context.
6. Add fallback rules: user locale, tenant locale, default locale, and English fallback.

Acceptance criteria:
- Locale is available in every app and API request.
- Reload preserves locale.
- Missing translation keys fail CI.

Tests:
- Provider tests.
- Locale persistence tests.
- API header tests.
- Missing key CI test.

### D2. Extract User-Facing Strings

Mapped TODO:
- TODO 19

Implementation tasks:
1. Add lint/AST rule for raw user-facing strings.
2. Extract strings from learner navigation, admin navigation, dashboard cards, assessment pages, content studio, command palette, forms, errors, empty states, and loading states.
3. Maintain translation keys by domain: `common`, `learner`, `admin`, `assessment`, `contentStudio`, `ai`, `privacy`, `security`, `offline`, and `errors`.

Acceptance criteria:
- UI is not English-hardcoded.
- Translation coverage is measured.

Tests:
- Raw string lint tests.
- Locale switch Playwright tests.
- Visual smoke tests per locale.

### D3. Locale-Aware Backend Contracts

Mapped TODO:
- TODO 20

Implementation tasks:
1. Add localized fields for content title/body, assessment prompt/options/explanations, simulation labels/units/help text, animation captions, AI feedback, credential text, and server-provided dashboard labels.
2. Add content fallback resolver.
3. Add missing translation telemetry.

Acceptance criteria:
- Content fetch respects locale.
- Fallback is deterministic and observable.

Tests:
- API locale tests.
- Missing translation tests.
- Generated content localization tests.

---

## 7. Workstream E — Assessment, CBM, Mastery, and Micro-Viva

### E1. Frontend Confidence Capture

Mapped TODO:
- TODO 21

Implementation tasks:
1. Add confidence selector for every answer: low, medium, high.
2. Block submit until confidence exists for each answered item.
3. Persist confidence per response.
4. Show confidence in review page.

Acceptance criteria:
- Learner cannot submit without confidence.
- Payload includes confidence.

Tests:
- Unit tests for form validation.
- Playwright tests for submit blocking.
- API payload tests.

### E2. Canonical CBM Scoring

Mapped TODO:
- TODO 22

Implementation tasks:
1. Create `AssessmentScoringService`.
2. Implement correct answer multipliers, wrong answer penalties, Brier score, calibration gain, and per-item score breakdown.
3. Remove scoring duplication.
4. Persist score evidence.

Acceptance criteria:
- All CBM scoring happens in backend canonical service.
- Frontend displays backend scores only.

Tests:
- Golden scoring matrix.
- Edge cases: omitted answer, no confidence, invalid confidence, high-confidence wrong, retake, partial credit, and AI-grading pending.

### E3. Assessment Results Route

Mapped TODO:
- TODO 23

Implementation tasks:
1. Add route `/assessments/:assessmentId/results`.
2. Show score, confidence calibration, item feedback, explanations, simulation evidence, remediation, and retry policy.
3. Add error states: pending grading, manual review required, expired attempt, missing attempt, and unauthorized.

Acceptance criteria:
- Submit navigation lands on valid route.
- Results are backend-derived.

Tests:
- Full assessment E2E.
- Pending review tests.
- Unauthorized tests.

### E4. Status Vocabulary Alignment

Mapped TODO:
- TODO 24

Implementation tasks:
1. Define canonical statuses for assessment lifecycle, attempt lifecycle, and grading lifecycle.
2. Update DB enums, API schemas, frontend filters, documentation, and tests.
3. Add migration if needed.

Acceptance criteria:
- No frontend-only status names.
- All filters map to canonical statuses.

Tests:
- Contract tests.
- UI filter tests.
- Migration tests.

### E5. Mastery from Evidence

Mapped TODO:
- TODO 25

Implementation tasks:
1. Create `LearningEvidenceService`.
2. Update mastery from claim IDs, evidence IDs, concept IDs, confidence, process traces, and misconception tags.
3. Remove module-ID string heuristics.
4. Add domain mapping from canonical module metadata.

Acceptance criteria:
- Mastery updates are evidence-based.
- Domain mapping is correct.

Tests:
- Mastery golden tests.
- Multi-domain tests.
- Simulation evidence tests.

### E6. AI Grading Failure Review Path

Mapped TODO:
- TODO 26

Implementation tasks:
1. Add grading result statuses: `graded`, `pending_ai`, `pending_human_review`, and `failed`.
2. On AI failure, create manual review task.
3. Do not award zero because of infrastructure failure.
4. Notify instructor/admin where appropriate.

Acceptance criteria:
- Learners are not penalized for AI outage.
- Manual review is auditable.

Tests:
- AI timeout tests.
- Malformed AI response tests.
- Manual review completion tests.

### E7. Micro-Viva Implementation

Mapped TODO:
- TODO 27

Implementation tasks:
1. Create viva selection engine: random 10%, anomaly flags, and instructor request.
2. Add scheduling: available slots, learner confirmation, and instructor queue.
3. Add rubric: conceptual accuracy, transfer/generalization, error diagnosis, and communication clarity.
4. Add recording metadata; do not store raw recording unless storage/privacy policy is complete.
5. Add remediation flow.

Acceptance criteria:
- Viva is a real workflow, not a document-only feature.
- Failure leads to targeted remediation and re-viva.

Tests:
- Random selection tests.
- Anomaly-trigger tests.
- Instructor scheduling E2E.
- Rubric scoring tests.

---

## 8. Workstream F — Telemetry, Analytics, and Dashboards

### F1. Versioned Telemetry Schema Registry

Mapped TODO:
- TODO 28

Implementation tasks:
1. Create schema registry for `sim.start`, `sim.control.change`, `sim.snapshot`, `sim.capture`, `assess.answer`, `assist.hint`, `ai.tutor.query`, `content.view`, and `module.progress`.
2. Validate every ingested event.
3. Reject unknown events, wrong schema versions, direct PII, and missing tenant/user/session/run/attempt context.
4. Store validation error telemetry.

Acceptance criteria:
- Telemetry is typed and replayable.
- PII is rejected.

Tests:
- Schema tests.
- Invalid event tests.
- PII rejection tests.
- Replay tests.

### F2. Real Content Generation Metrics

Mapped TODO:
- TODO 29

Implementation tasks:
1. Create `ContentGenerationMetricsService`.
2. Metrics: generated artifacts by status, validation failures by reason, review backlog, review SLA, auto-publish eligibility, AI latency, AI cost, queue depth, and DLQ count.
3. Replace random dashboard values.
4. Add degraded states.

Acceptance criteria:
- Admin metrics reconcile with backend data.
- No fake/random values remain.

Tests:
- Seeded dashboard tests.
- Empty/degraded/error state tests.

### F3. Instructor Learning Analytics

Mapped TODO:
- TODO 30

Implementation tasks:
1. Add instructor analytics API for claim mastery, misconception clusters, CBM calibration, Brier score, simulation process evidence, viva queue, and remediation recommendations.
2. Add instructor dashboard panels.
3. Add export support if required.

Acceptance criteria:
- Instructor sees actionable mastery evidence.
- Dashboard values reconcile with raw events.

Tests:
- Seeded classroom E2E.
- Metric reconciliation tests.

---

## 9. Workstream G — TutorPutor Web/Admin/Mobile Apps

### G1. Learner Critical Journey

Mapped TODO:
- TODO 31

Implementation tasks:
1. Seed tenant, learner, module, content blocks, simulation, assessment, and AI context.
2. E2E flow: login, onboarding, diagnostic, pathway, module, simulation, AI tutor hint, assessment, confidence submission, feedback, remediation, and dashboard update.

Acceptance criteria:
- One test proves the core learner product works.

Tests:
- Playwright critical journey.

### G2. Remove Placeholder Home

Mapped TODO:
- TODO 32

Implementation tasks:
1. Decide canonical landing behavior: authenticated learner to dashboard, unauthenticated user to marketing/landing page.
2. Remove unused placeholder or convert it to a real landing page.
3. Add route test.

Acceptance criteria:
- No learner lands on placeholder.

Tests:
- Redirect tests.
- Auth state route tests.

### G3. Admin Route Cleanup

Mapped TODO:
- TODO 33

Implementation tasks:
1. Remove duplicate `settings/marketplace`.
2. Audit legacy route redirects.
3. Keep only documented compatibility redirects, behind planned removal note if needed.

Acceptance criteria:
- One canonical route per admin page.

Tests:
- Route snapshot tests.
- Redirect tests.

### G4. Design-System Alignment

Mapped TODO:
- TODO 34

Implementation tasks:
1. Inventory non-canonical UI usage.
2. Replace with `@ghatana/ui`, `@ghatana/theme`, `@ghatana/tokens`, and `@ghatana/design-system`.
3. Standardize buttons, cards, forms, tables, dialogs, navigation, command palette, and charts.
4. Add visual regression.

Acceptance criteria:
- UI follows shared design-system tokens and components.
- No scattered one-off styles for common patterns.

Tests:
- Visual regression.
- Accessibility tests.
- Theme switch tests.

### G5. Split Unified Content Studio

Mapped TODO:
- TODO 6

Implementation tasks:
1. Extract `GenerationDashboard`, `ContentAuthoringForm`, `GenerationQueueMonitor`, `TemplateLibraryPanel`, `ValidationReviewQueue`, `InfrastructureHealthPanel`, and `ContentAnalyticsPanel`.
2. Add typed props and API hooks.
3. Remove shared random state.
4. Use composition in `UnifiedContentStudio`.

Acceptance criteria:
- Each panel is independently testable.
- Data source ownership is clear.

Tests:
- Component tests.
- API hook tests.
- Admin E2E.

---

## 10. Workstream H — Accessibility, Offline, and Mobile

### H1. Accessible Simulations

Mapped TODO:
- TODO 35

Implementation tasks:
1. Add accessibility contract to SimKit.
2. Every simulation must define keyboard operations, ARIA labels, screen-reader summary, non-visual state representation, high-contrast line/shape strategy, reduced-motion mode, and reset behavior.
3. Validate in CMS and generator.

Acceptance criteria:
- Simulation cannot publish without accessibility metadata.

Tests:
- axe tests.
- keyboard tests.
- reduced-motion tests.
- screen-reader snapshot tests where possible.

### H2. Offline Learning Sync

Mapped TODO:
- TODO 36

Implementation tasks:
1. Define offline queue schema.
2. Support queued progress updates, simulation telemetry, assessment attempts, and content completion.
3. Add conflict resolution: server wins for graded attempts, telemetry is append-only, latest learner progress is merged with audit.
4. Add unavailable AI tutor state.
5. Add sync status UI.

Acceptance criteria:
- Offline learning does not lose evidence.
- Reconnect sync is deterministic.

Tests:
- Offline E2E.
- Conflict tests.
- Sync retry tests.

### H3. Mobile Production Tests

Mapped TODO:
- TODO 37

Implementation tasks:
1. Remove `--passWithNoTests`.
2. Add mobile tests for login, navigation, API configuration, token storage, offline queue, sync, locale, and privacy settings.
3. Add mobile CI target.

Acceptance criteria:
- Mobile package cannot pass with zero tests.

Tests:
- React Native unit/integration tests.
- CI check for test count.

---

## 11. Workstream I — Backend, API Contracts, and Data Model

### I1. Replace String-Encoded JSON

Mapped TODO:
- TODO 38

Implementation tasks:
1. Inventory string JSON fields.
2. Migrate to Prisma `Json` fields.
3. Add Zod schemas.
4. Add migration scripts.
5. Add backward data migration if data exists.
6. Remove parse/stringify scattered logic.

Acceptance criteria:
- JSON fields are typed and validated.

Tests:
- Migration tests.
- Round-trip tests.
- Invalid payload tests.

### I2. API Route Ownership

Mapped TODO:
- TODO 39

Implementation tasks:
1. Create route ownership registry.
2. Compare OpenAPI paths, Fastify/routes, gateway routes, generated clients, and tests.
3. Fail CI on drift.

Acceptance criteria:
- Every route has one owner and tests.

Tests:
- Route ownership CI test.

### I3. Shared Typed API Client

Mapped TODO:
- TODO 40

Implementation tasks:
1. Generate or consolidate typed API client.
2. Replace hand-shaped frontend response assumptions.
3. Use shared error handling.
4. Use tenant and locale headers automatically.

Acceptance criteria:
- Frontend cannot call untyped APIs.

Tests:
- Type-level tests.
- Runtime contract tests.
- API integration tests.

---

## 12. Workstream J — Privacy, Security, Compliance, and Child Safety

### J1. Consent Policy Engine

Mapped TODO:
- TODO 41

Implementation tasks:
1. Define consent matrix.
2. Enforce at AI tutor, AI grading, simulation telemetry, learning analytics, personalization, and marketing.
3. Add UI surface for consent settings.
4. Add audit logs.

Acceptance criteria:
- Consent controls are granular and enforced server-side.

Tests:
- Consent matrix tests.
- Privacy settings E2E.
- Audit tests.

### J2. Social and Child Safety

Mapped TODO:
- TODO 42

Implementation tasks:
1. Implement role/age restrictions: no unapproved adult-minor direct messaging, verified teacher channels only, and parent/guardian visibility where required.
2. Add moderation: report, hide, review, approve/reject, and appeal.
3. Add audit logs and notifications.
4. Add AI moderation only as assistant signal, not sole authority.

Acceptance criteria:
- K-12 safety rules are enforced.

Tests:
- Minor/adult messaging tests.
- Moderation flow E2E.
- Audit-log tests.

---

## 13. Workstream K — DevOps, Observability, CI/CD, and Resilience

### K1. Product-Level Critical Journey Suite

Mapped TODO:
- TODO 43

Implementation tasks:
1. Add suites for learner, instructor, admin/content author, operator, privacy/security, and LTI/institution.
2. Use deterministic seed data.
3. Run in CI.

Acceptance criteria:
- Critical journeys block merge.

Tests:
- Playwright + backend integration suite.

### K2. CI Quality Gates

Mapped TODO:
- TODO 44

Implementation tasks:
1. Enforce typecheck, lint, unit tests, integration tests, API contract tests, route ownership tests, Playwright E2E, accessibility tests, tenant-scope validation, i18n missing-key validation, and mobile tests.
2. Fail on `passWithNoTests`, fake/random production data, contract drift, accessibility critical issues, missing route owners, and missing translation keys.

Acceptance criteria:
- CI catches production-readiness gaps.

Tests:
- CI negative tests where feasible.

### K3. Content Generation Observability

Mapped TODO:
- TODO 45

Implementation tasks:
1. Add metrics for job started/completed/failed, validation failure reason, AI latency, AI token/cost, DLQ count, queue depth, auto-publish blocked count, and human-review backlog.
2. Add logs with correlation IDs.
3. Add dashboards and alerts.
4. Add runbooks.

Acceptance criteria:
- Operators can detect and diagnose generation failures.

Tests:
- Metrics tests.
- Alert-rule tests.
- DLQ simulation tests.

---

## 14. Cross-Cutting Production Patterns

### 14.1 Error Handling

All services must use typed errors:

- `ValidationError`
- `TenantScopeError`
- `ConsentDeniedError`
- `FeatureDisabledError`
- `InsufficientEvidenceError`
- `ManualReviewRequiredError`
- `AiProviderUnavailableError`
- `TelemetrySchemaError`
- `RouteOwnershipError`
- `LocalizationMissingKeyError`

Rules:
- Do not return silent empty states for missing required evidence.
- Do not convert AI failures to learner failure.
- Do not swallow validation or tenant-scope errors.
- Do not leak internals to users.

### 14.2 Feature Flags

Incomplete features must be behind explicit flags:

| Feature | Flag |
|---|---|
| Autonomous content auto-publish | `TUTORPUTOR_AUTONOMOUS_CONTENT_AUTO_PUBLISH_ENABLED` |
| Micro-viva workflow | `TUTORPUTOR_MICRO_VIVA_ENABLED` |
| Social learning | `TUTORPUTOR_SOCIAL_LEARNING_ENABLED` |
| Mobile production sync | `TUTORPUTOR_MOBILE_SYNC_ENABLED` |
| Semantic evidence search | `TUTORPUTOR_SEMANTIC_EVIDENCE_SEARCH_ENABLED` |
| AI grading | `TUTORPUTOR_AI_GRADING_ENABLED` |
| Multilingual generated content | `TUTORPUTOR_GENERATED_CONTENT_I18N_ENABLED` |

Rules:
- Feature flag off must produce typed unavailable state.
- Feature flag on must require complete tests.
- Do not expose incomplete UI without a disabled/coming-soon state.

### 14.3 Observability

Every production path must emit:

- correlation ID
- tenant ID
- user role
- operation name
- duration
- success/failure
- typed error code
- important policy decision outcome

AI/content-generation paths must additionally emit:

- model ID
- model version
- prompt version
- token count or estimated cost
- latency
- retry count
- validation result
- review requirement
- redaction status

Telemetry/learning paths must additionally emit:

- schema version
- event type
- run/attempt/session ID
- validation status
- no-PII validation result

---

## 15. Testing Strategy

### 15.1 Required Test Types

| Type | Required For |
|---|---|
| Unit tests | Scoring, policies, schema validation, seed derivation, redaction, locale fallback |
| Integration tests | API routes, DB writes, content-generation pipeline, AI service wrappers |
| Contract tests | OpenAPI, typed clients, route ownership, telemetry schemas |
| Golden tests | CBM scoring, generated simulations, generated assessments, AI grading rubrics |
| E2E tests | Learner, admin, instructor, privacy, offline, LTI |
| Accessibility tests | Learner shell, admin shell, simulation player, assessment UI, content studio |
| Security tests | consent, tenant isolation, child-safety restrictions, audit logs |
| Performance tests | telemetry ingest, AI proxy latency, dashboard metrics, assessment submission |
| Migration tests | JSON field conversion, status vocabulary alignment |

### 15.2 Golden Datasets

Create canonical fixtures for:

1. CBM scoring matrix
2. Math graph simulation
3. Physics motion simulation
4. CS scheduling simulation
5. Business supply-demand simulation
6. Medicine vitals simulation
7. AI short-answer grading
8. Generated assessment bundle
9. Semantic evidence search
10. Cross-tenant isolation
11. i18n locale fallback
12. Offline sync conflict

### 15.3 No Test Theater Rules

A test is invalid if it only verifies:

- component renders without checking behavior
- function returns any value
- API returns 200 without checking payload
- AI returns text without checking grounding metadata
- telemetry event writes without schema validation
- dashboard renders without metric reconciliation
- route exists without role/tenant/security behavior

---

## 16. Recommended Implementation Order

### Sprint 1 — Safety and Contract Baseline

1. Disable unsafe generated-content auto-publish.
2. Remove random dashboard metrics.
3. Add tenant scope to knowledge-base routes.
4. Add AI audit redaction.
5. Add CI failure for mobile `passWithNoTests`.
6. Add route ownership registry scaffold.
7. Add feature flag policy scaffold.

### Sprint 2 — i18n, Consent, Telemetry, CBM Contracts

1. Add i18n provider and catalogs.
2. Add missing-key checks.
3. Add consent policy engine.
4. Add telemetry schema registry.
5. Add canonical CBM scoring service.
6. Add generated simulation deterministic seed derivation.
7. Add API route ownership CI check.

### Sprint 3 — AI Grounding and Evidence

1. Implement learning context assembler.
2. Fix AI domain/difficulty mapping.
3. Ground question generation in content evidence.
4. Implement semantic evidence search behind flag.
5. Make AI grading failures become manual review.
6. Add generated-content review screen skeleton.

### Sprint 4 — SimKit and Assessment

1. Implement domain SimKit templates.
2. Generate multi-item assessment bundles.
3. Add frontend confidence selection.
4. Add results route.
5. Align status vocabulary.
6. Add accessible simulation metadata and validation.

### Sprint 5 — App Journeys and Admin Refactor

1. Split `UnifiedContentStudio`.
2. Implement real content-generation metrics.
3. Add learner critical journey.
4. Add instructor analytics.
5. Remove placeholder/duplicate routes.
6. Apply design-system consistency pass.

### Sprint 6 — Offline, Mobile, Social Safety, Observability

1. Complete offline sync.
2. Add mobile tests.
3. Add social/child-safety enforcement.
4. Add content-generation dashboards/alerts.
5. Add micro-viva workflow.
6. Enforce full CI quality gates.

---

## 17. Definition of Done

A TODO is complete only when all conditions below are satisfied:

1. **Implementation**
   - Production code is complete.
   - No fake data, placeholders, or random production behavior.
   - Feature flag exists if capability is intentionally incomplete.

2. **Architecture**
   - Uses canonical shared abstractions.
   - No duplicated schema/client/service/component logic.
   - Proper separation of concerns.

3. **Correctness**
   - Edge cases are handled.
   - Determinism is enforced where required.
   - Tenant, role, consent, privacy, and locale are respected.

4. **Tests**
   - Meaningful unit tests exist.
   - Integration/contract tests exist where relevant.
   - E2E tests exist for user-facing journeys.
   - Accessibility tests exist for UI and simulations.
   - Golden tests exist for scoring/generation/simulation/AI behavior.

5. **Observability**
   - Structured logs.
   - Metrics.
   - Correlation IDs.
   - Typed errors.
   - Alerting for operationally critical paths.

6. **Security and Privacy**
   - Server-side authorization.
   - Consent enforcement.
   - PII redaction.
   - Audit logs.
   - Child-safety where relevant.

7. **i18n**
   - User-facing strings are localized.
   - Locale propagates to API/AI/content.
   - Fallback is deterministic.
   - Missing keys fail CI.

8. **Documentation**
   - Architecture/design docs updated only where canonical.
   - No stale TODO documents left behind.
   - Runbooks added for operational flows.

---

## 18. Completion Matrix

| TODO | Workstream | Phase |
|---|---|---|
| 1 | A1 | 1 |
| 2 | A2 | 4 |
| 3 | A2 | 2 / 4 |
| 4 | A3 | 4 |
| 5 | F2 | 1 |
| 6 | G5 | 5 |
| 7 | A4 | 3 |
| 8 | B1 | 2 |
| 9 | B2 | 1 |
| 10 | B3 | 3 |
| 11 | B4 | 3 |
| 12 | C1 | 3 |
| 13 | C1 | 3 |
| 14 | C2 | 1 |
| 15 | C3 | 3 |
| 16 | C4 | 3 |
| 17 | D1 | 2 |
| 18 | D1 | 2 |
| 19 | D2 | 2 / 5 |
| 20 | D3 | 2 |
| 21 | E1 | 4 |
| 22 | E2 | 2 / 4 |
| 23 | E3 | 4 |
| 24 | E4 | 4 |
| 25 | E5 | 3 |
| 26 | E6 | 3 |
| 27 | E7 | 5 / 6 |
| 28 | F1 | 2 |
| 29 | F2 | 5 |
| 30 | F3 | 5 |
| 31 | G1 | 5 |
| 32 | G2 | 5 |
| 33 | G3 | 5 |
| 34 | G4 | 5 |
| 35 | H1 | 4 |
| 36 | H2 | 5 / 6 |
| 37 | H3 | 1 / 6 |
| 38 | I1 | 2 |
| 39 | I2 | 2 |
| 40 | I3 | 2 |
| 41 | J1 | 2 / 6 |
| 42 | J2 | 6 |
| 43 | K1 | 6 |
| 44 | K2 | 1 / 6 |
| 45 | K3 | 6 |

---

## 19. Final Production Readiness Gate

Before marking the TutorPutor remediation complete, run and pass:

1. Full product typecheck.
2. Full product lint.
3. Unit tests.
4. Integration tests.
5. API contract tests.
6. Route ownership tests.
7. Tenant-scope tests.
8. Consent matrix tests.
9. i18n missing-key tests.
10. Accessibility tests.
11. Simulation golden tests.
12. CBM scoring golden tests.
13. AI grounding tests.
14. Generated-content review and validation tests.
15. Learner critical E2E.
16. Admin content-generation E2E.
17. Instructor analytics E2E.
18. Privacy export/delete E2E.
19. Offline sync E2E.
20. Mobile tests.
21. Observability and alert-rule tests.

No release is acceptable if any critical path is untested, fake, unlocalized, non-observable, privacy-unsafe, tenant-unsafe, or dependent on mocks/stubs/placeholders in production code.
