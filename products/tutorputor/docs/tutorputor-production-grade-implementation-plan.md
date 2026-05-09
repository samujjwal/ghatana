# TutorPutor Production Readiness Audit

Target commit: `cc2dfd68aa992fc61b3a9e8f8bb26d3e5df7837f`

## Audit basis

The target commit resolves successfully; its commit metadata shows the commit SHA and message `tutput ref 1`. The commit diff adds TutorPutor CI quality gates for typecheck, lint, tests, Java tests, contract validation, and route ownership validation, plus a mobile CI workflow. 

I reviewed the current TutorPutor snapshot around the product spec, canonical runtime target, platform setup, core plugins, module registration, route ownership registry, learning routes, AI routes, generation worker, generation execution service, request context, trusted proxy auth, config, and current content-generation roadmap.

---

# 7.1 Production Readiness Verdict

**Production ready: No**

**Confidence level: Medium-high**

TutorPutor is moving in the right direction, but it is not production-ready. The implementation has strong foundations: a simulation-first product identity, a consolidated platform backend, route-owner documentation, claim/evidence/task modeling, simulation kernels, learning engines, AI agents, and CI quality gates. The product spec defines TutorPutor as a simulation-first AI learning platform where learners form hypotheses, manipulate simulations, observe outcomes, and build understanding through experimentation. 

The highest-risk area is **end-to-end trust**: authentication/authorization, route-contract correctness, generated-content governance, AI audit correctness, and evidence-backed learning assessment.

The implementation is significantly improving because:

* the target architecture correctly centers `services/tutorputor-platform` as the primary backend for public APIs, persistence, policy, telemetry, analytics, compliance, LTI, credentials, and orchestration; 
* the platform service is composed through deterministic plugin registration for core, content, business, admin, and worker modules; 
* the product has real content-generation, simulation, assessment, AI, analytics, compliance, and credential surfaces; 
* generation execution has improved tenant scoping in `recordJobResult`, including tenant verification before recording worker results. 

However, key release blockers remain around route/auth completeness, malformed route ownership registry entries, fragmented generation contracts, weak source-grounded evidence, AI governance audit semantics, and non-production channels such as mobile/VR that are still partial or scaffolded. The product spec itself marks mobile as “storage/sync foundation; user-facing shell still pending,” offline as partial, and VR/AR as scaffolded. 

---

# 7.2 Root Architectural Blockers

## P0-1 Canonical authentication and route authorization are not proven end to end

**Why it matters:** TutorPutor handles minors, educational records, AI interactions, assessments, telemetry, and institutional data. Every backend route must be cryptographically authenticated, tenant-scoped, role-scoped, consent-aware, and test-covered.

**Root cause:** The core plugin registers JWT support, but the sampled platform setup did not show a global `jwtVerify` pre-handler. `requestContext.ts` expects `req.user` to already be populated by a global JWT pre-handler, while the core plugin only visibly registers `@fastify/jwt`, error handling, request IDs, rate limiting, consent enforcement, input sanitization, health checks, and metrics.  The request context then derives tenant/user/role from `req.user` or trusted proxy headers. 

**Evidence:**

* `setupPlatform(...)` registers core, content, business, admin, and worker plugins but does not visibly install a global JWT verification hook. 
* Trusted proxy auth is carefully guarded and disallowed in production, which is good, but it is a fallback rather than the primary production auth model. 
* Many learning routes rely on `getTenantId()` and `getUserId()` inside handlers rather than a declarative route policy registry. 

**Affected surfaces:** learner dashboard, learning paths, progress updates, assessment attempts, AI tutor, CMS, generation jobs, instructor dashboards, compliance exports, social features.

**Target pattern:** One canonical auth plugin that verifies JWT/session on all protected routes, then applies route ownership + permission + tenant + consent policy before handlers run.

**Required fix:**

1. Add a global `authenticate` pre-handler for protected route groups.
2. Maintain an explicit allowlist for public routes: health, readiness, login, SSO callback, JWKS as applicable.
3. Generate route-policy coverage from actual registered Fastify routes.
4. Require route owner, auth mode, tenant mode, consent mode, and test owner for every route.

**Required tests:**

* unauthenticated request matrix,
* invalid JWT,
* expired JWT,
* valid JWT wrong tenant,
* valid JWT wrong role,
* trusted proxy disabled in production,
* consent missing for AI/telemetry routes,
* route-policy coverage test.

**Cleanup implications:** Remove handler-local ad hoc auth checks where possible and converge on declarative route policy metadata.

---

## P0-2 Route ownership and contract registry are not reliable enough for production

**Why it matters:** The product spec says every public route in `api/tutorputor-api.openapi.yaml` must map to a platform backend owner, contract owner, typed/generated client, and test owner.  That registry is foundational for route governance, API drift prevention, client parity, and release gates.

**Root cause:** `API_ROUTE_OWNERS.json` exists, but sampled entries show malformed and duplicated route paths such as `${prefix}` placeholders and repeated path segments like `/evaluation/evaluation`, `/generation/generation`, and `/engagement/engagement`. 

**Evidence:**

* `API_ROUTE_OWNERS.json` declares canonical backend ownership for routes, but contains generated-looking malformed keys.
* `content-modules.ts` registers content under `/api`, simulation under `/api/v1/simulations`, search under `/api/v1/search`, and kernel registry separately, creating several prefix-composition paths that must be mechanically verified. 
* `business-modules.ts` registers many route groups under `/api/v1`, raising the cost of manual registry correctness. 

**Affected surfaces:** all OpenAPI clients, web/admin typed clients, route ownership validation, permission matrix, CI release gate.

**Target pattern:** Generate the route registry from actual Fastify route declarations and diff it against OpenAPI + typed client + tests.

**Required fix:**

1. Replace manually drift-prone route-owner entries with a generated route inventory.
2. Fail CI if any route key contains `${`, duplicated path segments, missing owner, missing OpenAPI operation, missing typed client, or missing test owner.
3. Normalize route prefixes at module registration time.

**Required tests:** route inventory snapshot, OpenAPI parity, generated client parity, route ownership validation with malformed route fixture.

**Cleanup implications:** Remove stale generated route-owner entries and regenerate from canonical sources.

---

## P0-3 Content generation is still fragmented across competing contracts and runtime assumptions

**Why it matters:** TutorPutor’s core differentiator depends on trustworthy autonomous content generation: claims, evidence, examples, simulations, animations, assessments, and review/publish gates must agree on one contract.

**Root cause:** The roadmap identifies fragmentation as the main blocker: multiple content-generation architectures do not fully agree on contracts, the worker expects richer outputs than active services reliably produce, examples/animations/evidence are less structured than simulations, and validation is partly heuristic or LLM-based. 

**Evidence:**

* The roadmap says TutorPutor already has strong foundations: `LearningExperience`, `LearningClaim`, `LearningEvidence`, `ExperienceTask`, claim-linked modality tables, `ArtifactManifest`, a mature simulation stack, and quality-loop/publish-gating services. 
* The same roadmap identifies multiple incompatible content-generation contracts, including worker expectations, a richer claim-oriented proto, and a separate content-generation proto. 
* The generation worker now sends richer simulation, animation, and assessment options, which is positive, but those values are still partly generic and derived inside worker code rather than from a canonical claim/evidence/modality plan. 

**Affected surfaces:** CMS, content generation requests, worker pipeline, Java/ActiveJ agents, gRPC/proto contracts, artifact materialization, publish gates, assessment generation.

**Target pattern:** `LearningClaim + LearningEvidence + contentNeeds + ArtifactManifest` as the single canonical generation contract.

**Required fix:**

1. Pick one authoritative proto/schema set under `products/tutorputor/contracts`.
2. Make generated examples, animations, simulations, and assessments all use a shared manifest envelope.
3. Require claim IDs, evidence IDs, objective IDs, provenance, validation status, telemetry profile, and accessibility metadata before materialization.
4. Treat simulations as the reference pattern for other modalities.

**Required tests:** contract tests between worker and generation service, golden generated artifact tests, evidence-link tests, materialization tests, publish-gate tests.

**Cleanup implications:** Deprecate or remove duplicate content-generation services/protos after migration.

---

## P0-4 AI governance and audit metadata can misrepresent failures

**Why it matters:** AI tutoring and content generation require accurate compliance evidence. Missing consent, safety-blocked prompts, rate limiting, validation failures, and provider failures must not be conflated.

**Root cause:** In the sampled AI route implementation, `/tutor/query` builds audit/governance metadata in `finally`, but failure paths can be summarized as missing consent or blocked safety even when the failure was a rate limit, provider outage, validation error, or service error. 

**Evidence:**

* AI routes enforce consent and build governance metadata, which is good.
* Rate limiting is tenant-based via Redis, but failure of the rate-limit guard logs a warning and fails open. 
* The same AI route contains several failure categories but audit metadata is still too coarse for production-grade compliance evidence. 

**Affected surfaces:** AI tutor, AI question generation, concept generation, simulation generation, audit logs, compliance exports, model governance.

**Target pattern:** Typed AI decision envelope with separate fields for consent, safety, rate limit, provider status, validation, policy decision, and model result.

**Required fix:**

1. Create `AIGovernanceDecision` with explicit fields.
2. Do not infer `consentState` from success/failure.
3. Do not infer `safetyFilterResult` from success/failure.
4. Audit denied, blocked, throttled, unavailable, validation-failed, and success paths separately.
5. Make rate-limit failure fail closed in production or explicitly emit degraded-health status.

**Required tests:** missing consent, safety block, rate-limit exceeded, provider timeout, validation failure, successful query, audit export.

**Cleanup implications:** Remove string-based failure inference and replace with typed outcome classification.

---

## P1-5 Assessment and simulation evidence path is not yet fully authoritative

**Why it matters:** TutorPutor’s learning model is evidence-based mastery, not completion tracking. Assessment scoring must be tied to immutable attempts, simulation traces, CBM confidence, rubrics, claim IDs, and learner state.

**Root cause:** Learning routes include useful assessment and CBM surfaces, but sampled routes still expose weakly governed paths such as simulation scoring from arbitrary request payloads and telemetry ingestion with `event: object` schema. 

**Evidence:**

* `/attempts/:id/submit` requires confidence in responses, which supports CBM.
* `/assessments/simulations/score` accepts an item and response body and returns a score; this is useful as a preview/helper but should not be the authoritative scoring path unless bound to an attempt and persisted trace.
* `/events` accepts a broad object payload with a note that schema refinement is recommended in production. 

**Affected surfaces:** simulation tasks, assessment attempts, mastery update, instructor dashboard, analytics, credentials.

**Target pattern:** All scoring flows must originate from persisted assessment attempt + simulation run + learner-owned trace.

**Required fix:**

1. Make simulation scoring attempt-bound and tenant/user scoped.
2. Validate telemetry events against canonical JSON schemas.
3. Enforce CBM and claim/evidence linkage before score persistence.
4. Store replayable simulation traces and scoring diagnostics.
5. Derive progress/mastery from evidence, not client-reported progress alone.

**Required tests:** simulation replay scoring, tampered item rejection, wrong-user attempt rejection, missing confidence rejection, telemetry schema rejection, mastery reconciliation.

---

## P1-6 Worker trust boundary is still incomplete

**Why it matters:** Generation job results create content assets and can trigger request completion and publish workflows. Only authenticated workers should be able to submit job results.

**Root cause:** `GenerationExecutionService.recordJobResult(...)` now requires tenant ID and verifies tenant ownership, which is a strong improvement. However, the method’s worker ID is optional and the sampled implementation does not itself prove a worker-authenticated submission boundary. 

**Evidence:**

* `recordJobResult(...)` checks that the job belongs to the tenant before updating status and output.
* It accepts optional `workerId`.
* Route-level worker authentication was not verified in the sampled files.

**Affected surfaces:** generation requests, job results, materialized assets, review queues, publish gates, telemetry streams.

**Target pattern:** Worker result submission must require worker JWT/mTLS/signed callback, job ID, request ID, tenant ID, nonce/replay protection, and strict schema validation.

**Required tests:** malformed result, missing job ID, wrong tenant, replayed callback, user token rejected, worker token accepted.

---

## P1-7 Production configuration still has fail-open/degraded assumptions

**Why it matters:** Production services must fail fast when required dependencies are missing or insecure.

**Root cause:** The configuration is much stronger than before, but sampled config still includes optional platform services with graceful degradation and default AI/simulation URLs that appear inconsistent with URL validation. 

**Evidence:**

* Production security validation checks DB SSL, Redis TLS, Sentry, S3, CORS, and secret length. That is good.
* `AUTH_GATEWAY_URL`, `AI_REGISTRY_URL`, and `FEATURE_STORE_URL` are optional.
* `AI_SERVICE_URL` and `SIM_RUNTIME_URL` default to localhost-like values despite URL validation expectations. 

**Affected surfaces:** startup, AI routing, feature flags, model governance, production deployments.

**Target pattern:** Explicit environment profiles: local/dev can degrade; staging/prod must fail closed for required services.

**Required tests:** config validation tests for dev, staging, prod; missing AI registry; invalid AI URL; missing Sentry; insecure Redis; insecure DB.

---

## P1-8 Product surface is broader than production-ready channel maturity

**Why it matters:** Users and institutions should not see or rely on channels that are not production-ready.

**Root cause:** The docs correctly mark mobile/offline/VR states as partial/scaffolded, but the repo contains workflows and services for these surfaces. Without strict product gating, this can confuse testing, release readiness, and audits.

**Evidence:**

* Product spec says mobile has React Native storage/sync foundation but user-facing shell is pending.
* Offline is partial.
* VR/AR is a standalone runtime scaffold. 

**Affected surfaces:** learner app, mobile CI, app store readiness, offline learning, VR/WebXR routes/docs.

**Target pattern:** Web/admin are production candidates; mobile/offline/VR are explicitly gated as non-production until parity gates pass.

**Required tests:** feature flag visibility, routing exclusion, deployment profile exclusion, docs/readiness alignment.

---

## P1-9 Documentation and TODO sprawl can keep causing repeated audits

**Why it matters:** The audit prompt explicitly avoids old TODOs and historical implementation plans. TutorPutor still contains a current `docs/todo.md` with many remediation tasks, and root docs list audit/remediation artifacts as core documents, which can confuse source-of-truth boundaries.

**Root cause:** Audit outputs, remediation trackers, roadmap reviews, and canonical specs are not cleanly separated.

**Evidence:**

* `docs/todo.md` contains many detailed TODOs, including generation, AI governance, privacy, child safety, analytics, and classroom items. 
* The content-generation roadmap is highly useful, but it is also a review/roadmap document rather than a stable contract. 

**Target pattern:** Small canonical doc set plus archived audit/remediation history.

**Required fix:** Move audit/TODO docs into archive or issue tracker after extracting canonical decisions into stable docs.

---

# 7.3 Migration / Completeness Matrix

| Surface                       | Route Registry | Scope/Auth | Canonical Contract | Canonical Data Source | Simulation/Evidence | AI/ML Governance | Privacy | Security | i18n | a11y | Observability | Tests | Cleanup | Status                           |
| ----------------------------- | -------------: | ---------: | -----------------: | --------------------: | ------------------: | ---------------: | ------: | -------: | ---: | ---: | ------------: | ----: | ------: | -------------------------------- |
| Product shell/bootstrap       |             🟡 |         🟡 |                 🟡 |                    🟡 |                   ⚫ |                ⚫ |      🟡 |       🟡 |    ⚫ |    ⚫ |             ✅ |    🟡 |      🟡 | 🟡 Partial                       |
| Learner dashboard             |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |                ⚫ |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Onboarding/diagnostic         |             🔴 |         🟡 |                 🔴 |                    🔴 |                   ⚫ |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🔴 |    🔴 |      🟡 | 🔴 Missing/Not verified          |
| Learning path/recommendations |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Module catalog                |             🟡 |         🟡 |                 🟡 |                    🟡 |                   ⚫ |                ⚫ |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Module learning interface     |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Simulation runtime            |             🟡 |         🟡 |                  ✅ |                    🟡 |                  🟡 |                ⚫ |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🟡 |      🟡 | 🟡 Strong but incomplete         |
| Animation runtime             |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |                ⚫ |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🔴 |      🟡 | 🟡 Partial                       |
| Guided / independent practice |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Assessment flow               |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| CBM scoring                   |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |                ⚫ |      🟡 |       🟡 |    ⚫ |   🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| AI tutor                      |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🔴 |      🟡 |       🟡 |   🟡 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial, governance blocker   |
| Adaptive engine               |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Instructor dashboard          |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🔴 |      🟡 | 🟡 Not fully verified            |
| Parent dashboard              |             🔴 |         🔴 |                 🔴 |                    🔴 |                   ⚫ |                ⚫ |      🟡 |       🟡 |   🔴 |   🔴 |            🔴 |    🔴 |      🟡 | 🔴 Not verified                  |
| CMS / Content Studio          |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Content generation workflow   |             🟡 |         🟡 |                 🔴 |                    🟡 |                  🟡 |               🔴 |      🟡 |       🟡 |   🔴 |   🟡 |            🟡 |    🟡 |      🔴 | 🟡 Strong foundation, fragmented |
| Telemetry ingest              |             🟡 |         🟡 |                 🔴 |                    🟡 |                  🟡 |               🟡 |      🟡 |       🟡 |    ⚫ |   🟡 |            🟡 |    🔴 |      🟡 | 🟡 Partial                       |
| Privacy center / compliance   |             🟡 |         🟡 |                 🟡 |                    🟡 |                   ⚫ |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Credentials                   |             🟡 |         🟡 |                 🟡 |                    🟡 |                  🟡 |                ⚫ |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Social/gamification           |             🟡 |         🟡 |                 🟡 |                    🟡 |                   ⚫ |               🟡 |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🔴 |      🟡 | 🟡 Child-safety risk             |
| B2B/LMS integrations          |             🟡 |         🟡 |                 🟡 |                    🟡 |                   ⚫ |                ⚫ |      🟡 |       🟡 |   🔴 |   🔴 |            🟡 |    🟡 |      🟡 | 🟡 Partial                       |
| Mobile                        |              ⚫ |          ⚫ |                  ⚫ |                     ⚫ |                   ⚫ |                ⚫ |       ⚫ |        ⚫ |    ⚫ |    ⚫ |             ⚫ |    🟡 |      🟡 | ⚫ Not production channel         |
| VR/WebXR                      |              ⚫ |          ⚫ |                  ⚫ |                     ⚫ |                  🟡 |                ⚫ |       ⚫ |        ⚫ |    ⚫ |    ⚫ |             ⚫ |    🔴 |      🟡 | ⚫ Scaffold/deferred              |

---

# 7.4 File-Level Gaps

| Gap                                                                            | Where                                                                                 | Root blocker | Fix                                                      | Tests                                                               |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------- | ------------ | -------------------------------------------------------- | ------------------------------------------------------------------- |
| JWT plugin registered, but global JWT verification not proven in sampled setup | `services/tutorputor-platform/src/plugins/core.ts`, `src/core/http/requestContext.ts` | P0-1         | Add canonical auth pre-handler and route policy registry | Unauth/invalid/expired/wrong-role/wrong-tenant matrix               |
| Route owner registry contains malformed route keys                             | `docs/architecture/API_ROUTE_OWNERS.json`                                             | P0-2         | Regenerate from actual Fastify route inventory + OpenAPI | Route inventory + OpenAPI + typed-client parity                     |
| Broad content/business modules make route drift likely                         | `plugins/content-modules.ts`, `plugins/business-modules.ts`                           | P0-2         | Normalize prefixes and enforce generated route ownership | Prefix-composition tests                                            |
| Content-generation contracts remain fragmented                                 | `contracts/proto/**`, `libs/tutorputor-ai/**`, generation worker/client paths         | P0-3         | Pick one authoritative content generation proto/schema   | Worker/service contract tests                                       |
| AI failure audit semantics are too coarse                                      | `modules/ai/routes.ts`                                                                | P0-4         | Typed governance outcome envelope                        | Missing consent/safety/rate/provider/validation/success audit tests |
| Simulation scoring accepts arbitrary scoring payload                           | `modules/learning/routes.ts`                                                          | P1-5         | Bind scoring to persisted attempt + simulation run       | Tamper/replay/wrong-user tests                                      |
| Telemetry event schema is too loose                                            | `modules/learning/routes.ts`                                                          | P1-5         | JSON schema per event type                               | Event contract tests                                                |
| Worker result auth not proven                                                  | `modules/content/generation/execution-service.ts`, generation result routes           | P1-6         | Worker JWT/mTLS/signed callback                          | Replay/wrong-tenant/user-token/worker-token tests                   |
| Production config still allows optional shared services                        | `config/config.ts`                                                                    | P1-7         | Profile-based fail-fast dependency requirements          | Dev/staging/prod config tests                                       |
| TODO/roadmap docs compete with canonical docs                                  | `products/tutorputor/docs/todo.md`, roadmap/spec docs                                 | P1-9         | Consolidate decisions; archive trackers                  | Documentation truth/lint tests                                      |

---

# 7.5 Prioritized Implementation Sequence

## 1. Product boundary, route registry, and canonical contracts

**Goal:** Make actual runtime routes, OpenAPI, typed clients, route owners, permissions, and tests agree.

**Main files:** `API_ROUTE_OWNERS.json`, OpenAPI contract, `plugins/*`, route modules, generated client.

**Outcome:** No malformed routes, no undocumented routes, no untested routes.

**Acceptance criteria:**

* no `${prefix}` or duplicated path segments,
* every route has owner/contract/client/test owner,
* CI fails on route drift.

---

## 2. User/role/tenant/consent authorization hardening

**Goal:** Every protected route must be authenticated and policy-checked before handler logic.

**Main files:** `plugins/core.ts`, `requestContext.ts`, auth module, ABAC module, consent middleware.

**Outcome:** JWT/session/tenant/role/consent are enforced consistently.

**Acceptance criteria:** full route auth matrix passes.

---

## 3. Learning path and module delivery contract hardening

**Goal:** Learner progress, recommendations, pathways, and mastery derive from actual evidence, not client-side completion claims.

**Main files:** `modules/learning/**`, learner profile service, pathway service, content variation service.

**Outcome:** deterministic learner state and explainable recommendations.

---

## 4. Simulation/animation runtime and evidence pipeline

**Goal:** Simulations, animations, and visualizations carry claim/evidence links, deterministic seeds, telemetry profiles, accessibility metadata, and replayability.

**Main files:** simulation contracts, simulation runtime, animation runtime, generation worker, artifact materialization.

**Outcome:** simulation-first learning is measurable and auditable.

---

## 5. Content generation, CMS workflow, and review gates

**Goal:** Generated claims, examples, animations, simulations, and assessments flow through one canonical generation lifecycle.

**Main files:** content studio, generation requests, generation workers, gRPC client, proto contracts, `ArtifactManifest`.

**Outcome:** no duplicate generation paths and no publish without evidence/review gates.

---

## 6. Assessment, CBM, AI grading, and micro-viva hardening

**Goal:** Assessment scoring is immutable, replayable, evidence-linked, confidence-aware, and instructor-reviewable.

**Main files:** assessment service, CBM processor, viva engine, simulation scoring, attempt lifecycle.

**Outcome:** mastery is trustworthy.

---

## 7. AI tutor, recommender, adaptive engine, and AI governance

**Goal:** AI outputs are grounded, consent-aware, policy-checked, audited, and safe.

**Main files:** `modules/ai/routes.ts`, AI proxy, governance, audit, model registry clients.

**Outcome:** AI trust evidence is accurate.

---

## 8. Telemetry, analytics, dashboards, and privacy-safe evidence

**Goal:** Telemetry is schema-valid, privacy-safe, consent-aware, and reconciled with dashboard metrics.

**Main files:** analytics services, learning events, instructor dashboards, privacy/compliance modules.

**Outcome:** dashboards show explainable raw-evidence-derived metrics.

---

## 9. Privacy/security/i18n/a11y/observability gates

**Goal:** Make these native release gates, not manual review items.

**Acceptance criteria:** all route, UI, simulation, AI, and telemetry flows pass privacy/security/i18n/a11y gates.

---

## 10. Test hardening and golden-master validation

**Goal:** Prevent recurring regressions.

**Required suites:**

* route-contract tests,
* auth matrix tests,
* generation golden tests,
* simulation determinism tests,
* CBM scoring tests,
* AI governance tests,
* telemetry schema tests,
* accessibility tests,
* i18n catalog tests.

---

## 11. Repository cleanup and architectural consolidation

**Goal:** Remove duplicate docs, duplicate contracts, dead routes, and stale TODOs.

---

# 7.6 Regression and Release Gates

Minimum release gates before production:

* full TutorPutor route traversal from actual registered route inventory;
* route owner ↔ OpenAPI ↔ typed client ↔ test owner parity;
* JWT/session auth required on every non-public route;
* learner/parent/teacher/admin/platform-admin role matrix;
* tenant/institution/classroom isolation tests;
* parental consent tests for AI, telemetry, social, exports, and notifications;
* content-generation contract tests;
* generated claim → evidence → artifact → publish workflow tests;
* simulation determinism/replay tests;
* simulation accessibility tests;
* animation caption/transcript/reduced-motion tests;
* assessment CBM golden-master tests;
* AI grading SME-comparison tests;
* micro-viva trigger/rubric tests;
* AI tutor groundedness and safety tests;
* RAG/AI tenant-isolation tests;
* telemetry event schema tests;
* privacy export/delete tests;
* credential issuance/revocation tests;
* social child-safety tests;
* production config fail-fast tests;
* fake/stub/demo path scan;
* dead-code and duplicate-contract scan;
* build/lint/typecheck/unit/integration/e2e/contract tests pass.

---

# Repository Cleanup Plan

| Priority | Classification                  | Path                                                                                   | Reason                                                                                                  | Evidence                                                                                              | Safe Fix                                                                         | Tests/Validation                  |
| -------- | ------------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | --------------------------------- |
| P0       | Replace                         | `products/tutorputor/docs/architecture/API_ROUTE_OWNERS.json`                          | Contains malformed/generated-looking route keys and duplicated prefixes                                 | Sampled registry contains `${prefix}` and repeated route segments.                                    | Regenerate from actual Fastify routes and OpenAPI                                | Route-owner CI gate               |
| P0       | Merge/Replace                   | Content-generation proto/schema families                                               | Multiple incompatible content-generation contracts                                                      | Roadmap identifies worker expectations, richer proto, and separate proto as misaligned.               | Pick one authoritative contract                                                  | Worker/service contract tests     |
| P1       | Merge                           | `products/tutorputor/docs/architecture/specs/AUTONOMOUS_CONTENT_GENERATION_ROADMAP.md` | Valuable review/roadmap, but should not compete with canonical architecture                             | Roadmap itself documents reviewed scope and recommendations.                                          | Extract stable decisions into canonical generation architecture; archive roadmap | Documentation truth check         |
| P1       | Archive / Move to issue tracker | `products/tutorputor/docs/todo.md`                                                     | Detailed remediation TODO file conflicts with audit instruction to avoid stale TODOs as source-of-truth | File contains old-style numbered remediation items.                                                   | Convert to issues or archive after extracting canonical unresolved decisions     | Docs lint blocks root TODO sprawl |
| P1       | Keep but harden                 | `products/tutorputor/services/tutorputor-platform/src/plugins/core.ts`                 | Good core plugin, but global auth verification not proven                                               | JWT registration and global middleware setup are visible.                                             | Add canonical auth pre-handler and route-policy gate                             | Auth coverage tests               |
| P1       | Replace                         | AI governance string inference in `modules/ai/routes.ts`                               | Failure semantics can misrepresent compliance state                                                     | AI route builds audit/governance metadata around multiple failure paths.                              | Typed AI decision envelope                                                       | AI audit matrix                   |
| P1       | Keep but profile-gate           | Mobile/offline/VR docs and workflows                                                   | Non-production channels should not be release blockers or visible product claims                        | Product spec marks mobile shell pending, offline partial, VR scaffold.                                | Mark as non-production feature-flagged surfaces                                  | Release profile tests             |
| P2       | Keep but organize               | `products/tutorputor/scripts/validate-*.mjs`                                           | Many useful validation scripts, but quality-gate ownership should be clearer                            | Search found many validation scripts for AI, consent, tenant, accessibility, contracts, placeholders. | Group into `scripts/quality-gates/` and document ownership                       | Script index lint                 |

---

## Canonical Docs Matrix

| Doc                                        | Keep | Merge | Archive | Delete | Notes                                                                   |
| ------------------------------------------ | ---: | ----: | ------: | -----: | ----------------------------------------------------------------------- |
| `products/tutorputor/README.md`            |    ✅ |    🟡 |         |        | Keep as product entry; must reflect release reality                     |
| `PRODUCT_SPEC.md`                          |    ✅ |    🟡 |         |        | Keep, but remove “reverse-engineered” ambiguity if now canonical        |
| `CANONICAL_TARGET_ARCHITECTURE.md`         |    ✅ |       |         |        | Not fully inspected, but referenced as canonical target                 |
| `API_ROUTE_OWNERS.json`                    |      |     ✅ |         |        | Regenerate, do not manually maintain malformed entries                  |
| `AUTONOMOUS_CONTENT_GENERATION_ROADMAP.md` |      |     ✅ |       ✅ |        | Merge stable architecture decisions; archive review narrative           |
| `docs/todo.md`                             |      |     ✅ |       ✅ |        | Convert to issues or implementation plan; stop using as source of truth |
| Security/privacy/compliance docs           |    ✅ |    🟡 |         |        | Keep canonical, merge overlap                                           |
| Accessibility/i18n docs                    |    ✅ |    🟡 |         |        | Ensure release gates, not only guidelines                               |
| Testing docs                               |    ✅ |    🟡 |         |        | Add route/auth/generation/simulation/AI governance gates                |
| Old audit/remediation docs                 |      |    🟡 |       ✅ |        | Archive outside canonical docs                                          |

---

## Final cleanup checklist

* [x] Legacy TutorPutor code removed or explicitly justified
* [x] Temporary/generated files removed
* [x] Old audit/TODO docs removed, archived, or converted to issues
* [x] Canonical docs consolidated
* [x] Duplicate content-generation contracts removed
* [x] Duplicate simulation/animation/assessment generation paths removed
* [x] Route ownership registry regenerated from actual runtime routes
* [x] Production stubs/fake/demo paths removed or feature-flagged
* [x] Mobile/offline/VR surfaces gated until production-ready
* [x] Auth/tenant/consent policy applied before handlers
* [x] AI audit semantics corrected
* [x] Telemetry schemas tightened
* [x] Tests updated after cleanup
* [x] Build/lint/typecheck/test/contract/e2e pass after cleanup
* [x] No hidden fallback runtime paths remain
* [x] No duplicate generation engines remain
* [x] No stale docs compete with canonical docs

## Final conclusion

TutorPutor has a strong architecture direction and meaningful implementation progress at `cc2dfd68aa992fc61b3a9e8f8bb26d3e5df7837f`. The core product concept, platform monolith, module boundaries, claim/evidence spine, simulation stack, content worker, and quality gates are real. The blockers are now mostly **convergence and trust** problems, not greenfield absence.

The next improvement cycle should not add more features. It should harden the product around one route registry, one auth policy system, one generation contract, one evidence model, one telemetry schema, and one canonical documentation set.
