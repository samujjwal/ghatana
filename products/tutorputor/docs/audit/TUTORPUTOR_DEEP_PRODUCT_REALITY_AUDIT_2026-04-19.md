# Tutorputor Deep Product Reality Audit (2026-04-19)

## Scope
- Products audited: Tutorputor, tutorputor-content-generation
- Additional surfaces audited: learner web app, admin app, mobile app, API gateway, platform service, shared Tutorputor libraries, selected platform libraries, content-worker and gRPC integration paths, deployment and E2E assets
- Audit method: source-code and artifact-based reconstruction plus end-to-end flow tracing from routes, services, scripts, schemas, and tests
- Evidence sources include: [products/tutorputor/README.md](products/tutorputor/README.md), [products/tutorputor/docs/architecture/CURRENT_STATE.md](products/tutorputor/docs/architecture/CURRENT_STATE.md), [products/tutorputor/services/tutorputor-platform/src/setup.ts](products/tutorputor/services/tutorputor-platform/src/setup.ts), [products/tutorputor/apps/tutorputor-web/src/router/routes.tsx](products/tutorputor/apps/tutorputor-web/src/router/routes.tsx), [products/tutorputor/apps/tutorputor-admin/src/services/contentStudioApi.ts](products/tutorputor/apps/tutorputor-admin/src/services/contentStudioApi.ts), [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts), [products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/ContentGenerationLauncher.java](products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/ContentGenerationLauncher.java), [products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/contentstudio/grpc/ContentGenerationServiceImpl.java](products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/contentstudio/grpc/ContentGenerationServiceImpl.java), [products/tutorputor/tests/e2e/ContentStudio.spec.ts](products/tutorputor/tests/e2e/ContentStudio.spec.ts), [products/tutorputor/tests/e2e/test-results.json](products/tutorputor/tests/e2e/test-results.json)

## A. Executive Verdict
- Overall maturity: Partial production maturity with strong module breadth but meaningful trust gaps in auth boundary hardening, consent enforcement integration, and real AI/content runtime operability.
- End-to-end readiness: Partial. Authoring happy-path is evidenced; identity, consent, queue/worker and login flows have notable breakpoints.
- Production readiness: Partial/conditional. Platform has health/metrics/tracing and modular boundaries, but critical paths can fail open or silently degrade.
- AI/ML-first maturity: Medium. AI endpoints exist broadly, but several paths use fallback defaults, synthetic generation, or non-operational service bootstraps.
- Automation maturity: Medium. Content pipelines are queue-oriented but local/dev defaults disable queue and worker by default.
- UX simplicity rating: 6/10 for learner and admin navigation; 4/10 for identity/login and cross-app consistency.
- Cognitive load assessment: Medium. Core route map is reasonably clear; duplication and inconsistent auth/state handling increase hidden cognitive load.
- Governance/privacy/security/visibility maturity: Medium-low. Strong pieces exist, but key controls are either bypass-prone or not wired at runtime.
- Content generation maturity: Medium-low. Pipeline scaffolding is substantial; production confidence depends on external services and currently mixed implementation quality.
- Content validation maturity: Medium. Validation gates and scoring are present, but some validation dimensions are rule-based heuristics and not all policy middleware is active.
- Pedagogical trust maturity: Medium-low. Pedagogical structures are explicit, but grounding/provenance and independent validation are not consistently enforced across all generation surfaces.
- Operability/resilience maturity: Medium. Good observability primitives and tests exist; startup defaults and optionalized critical dependencies reduce trust in real outcomes.
- Top blockers:
- Trusted header auth bypass path in platform request guard: [products/tutorputor/services/tutorputor-platform/src/setup.ts](products/tutorputor/services/tutorputor-platform/src/setup.ts)
- Consent middleware is implemented but not wired into runtime: [products/tutorputor/services/tutorputor-platform/src/core/middleware/consent-enforcement.ts](products/tutorputor/services/tutorputor-platform/src/core/middleware/consent-enforcement.ts)
- Learner login integration mismatch on route paths: [products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx](products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx), [products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts](products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts)
- Auth module lacks refresh/logout endpoints used by clients: [products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx](products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx), [products/tutorputor/apps/tutorputor-admin/src/hooks/useAuth.ts](products/tutorputor/apps/tutorputor-admin/src/hooks/useAuth.ts)
- Content generation service startup and worker integration are conditional and often disabled in default dev topology: [products/tutorputor/bin/ttr-dev](products/tutorputor/bin/ttr-dev), [products/tutorputor/docker-compose.yml](products/tutorputor/docker-compose.yml)

## B. Reconstructed Product Model
- Target personas/users:
- Learners using web routes and AI tutor panel: [products/tutorputor/apps/tutorputor-web/src/router/routes.tsx](products/tutorputor/apps/tutorputor-web/src/router/routes.tsx)
- Educators/content creators in admin authoring workflows: [products/tutorputor/apps/tutorputor-admin/src/pages/AuthoringPage.tsx](products/tutorputor/apps/tutorputor-admin/src/pages/AuthoringPage.tsx)
- Operators/platform engineers via gateway/platform and observability endpoints: [products/tutorputor/services/tutorputor-platform/src/core/observability/metrics.ts](products/tutorputor/services/tutorputor-platform/src/core/observability/metrics.ts)
- Jobs to be done:
- Learn through pathways/modules/assessments/simulations
- Author and publish validated learning experiences
- Automate generation of claims/examples/simulations/animations and evaluate quality
- Run tenant-aware, policy-aware, observable platform operations
- Expected outcomes:
- Outcome-first learning UX with minimal user friction
- AI-assisted generation and tutoring with safe fallback
- Governance and compliance by default
- Expected learning outcomes:
- Claim-evidence-task alignment with publish gating and pedagogical checks: [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts)
- Primary workflows:
- Learner browse and study flow
- Admin content authoring, validate, publish flow
- AI tutor query flow
- Secondary workflows:
- Marketplace and billing
- LTI integration
- Notifications and device token lifecycle
- Mobile offline/sync foundation
- Cross-product workflows if applicable:
- Platform service to Java gRPC content generation worker path
- Gateway to platform module execution path
- Expected AI/ML role:
- Tutor answers, generation of content artifacts, content-needs analysis, and validation support
- Expected user involvement level:
- Users should guide intent and review only high-risk/low-confidence decisions
- Justified human review/governance points:
- Publication gate after validation
- Admin role-gated generation operations
- Justified SME/instructional review points:
- Any low confidence, high-risk, or conflicting evidence outputs should route to review
- Operational expectations:
- Reliable startup, queue/worker readiness, traceability, and incident diagnosability

## C. End-to-End Workflow Audit Matrix
| Workflow | Intended outcome | Actual behavior | User burden | AI role today | AI role needed | Key gaps | Severity | Exact evidence |
|---|---|---|---|---|---|---|---|---|
| Learner login and session bootstrap | Tenant-aware SSO login and authenticated session | Login UI fetches /auth/providers, while auth module exposes /api/v1/auth/sso/providers; refresh/logout client expectations exceed backend routes | High when real auth is enabled | Minimal | Should fully automate token/session lifecycle | Route contract mismatch, missing refresh/logout routes, unused AuthProvider in app root | P0 | [products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx](products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx), [products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx](products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx), [products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts](products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts), [products/tutorputor/apps/tutorputor-web/src/App.tsx](products/tutorputor/apps/tutorputor-web/src/App.tsx) |
| Learner AI tutor assist | Context-aware tutoring with safe tenant/user context | Uses hardcoded tenant fallback and can operate without strict identity context | Medium | Query + answer generation | Should use authenticated tenant/user context and confidence-based escalation | Hardcoded headers and default identity pathways | P1 | [products/tutorputor/apps/tutorputor-web/src/components/OmnipresentAITutor.tsx](products/tutorputor/apps/tutorputor-web/src/components/OmnipresentAITutor.tsx), [products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts](products/tutorputor/services/tutorputor-platform/src/modules/ai/routes.ts) |
| Admin authoring create-validate-publish | End-to-end authoring with gated publication | Strong happy-path exists and one real E2E test passes; role gating is applied | Medium | Claims and artifact generation support | Should include full confidence routing and deterministic provenance controls | E2E depth is narrow and uses trusted headers instead of full auth | P1 | [products/tutorputor/tests/e2e/ContentStudio.spec.ts](products/tutorputor/tests/e2e/ContentStudio.spec.ts), [products/tutorputor/tests/e2e/test-results.json](products/tutorputor/tests/e2e/test-results.json), [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/routes.ts), [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts) |
| Global API auth enforcement | Require valid bearer token for protected APIs | Guard allows trusted proxy headers in absence of token; this is safe only with strict network trust boundary | Low in UX, high in risk | None | Should enforce cryptographic identity for all externally reachable paths | Fail-open impersonation surface if header trust is not isolated | P0 | [products/tutorputor/services/tutorputor-platform/src/setup.ts](products/tutorputor/services/tutorputor-platform/src/setup.ts), [products/tutorputor/services/tutorputor-platform/src/core/http/requestContext.ts](products/tutorputor/services/tutorputor-platform/src/core/http/requestContext.ts) |
| Consent-aware processing | Block AI/analytics operations without consent | Consent middleware exists with tests, but no runtime registration found | Invisible burden; hidden compliance risk | N/A | Should be automatic and enforced | Control exists only as code artifact, not active guard | P0 | [products/tutorputor/services/tutorputor-platform/src/core/middleware/consent-enforcement.ts](products/tutorputor/services/tutorputor-platform/src/core/middleware/consent-enforcement.ts), [products/tutorputor/services/tutorputor-platform/src/core/middleware/__tests__/consent-enforcement.test.ts](products/tutorputor/services/tutorputor-platform/src/core/middleware/__tests__/consent-enforcement.test.ts) |
| Queue-based content generation runtime | Async generation through worker and gRPC services | Queue and worker are disabled in default local stack; worker init can fail and continue | Medium for operators | AI generation intended | Should be active by default in production-like validation profile | Operational mismatch between documented architecture and default topology | P1 | [products/tutorputor/bin/ttr-dev](products/tutorputor/bin/ttr-dev), [products/tutorputor/docker-compose.yml](products/tutorputor/docker-compose.yml), [products/tutorputor/services/tutorputor-platform/src/modules/content/queue/content-generation-queue.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/queue/content-generation-queue.ts), [products/tutorputor/services/tutorputor-platform/src/startup/content-worker-init.ts](products/tutorputor/services/tutorputor-platform/src/startup/content-worker-init.ts) |
| Mobile app runtime | Offline-capable learner app | Strong foundation, but browser localStorage is used in RN app initialization and dashboard hook | Medium | Low | Should use secure native token storage and complete auth integration | Runtime portability risk and authentication incompleteness | P1 | [products/tutorputor/apps/tutorputor-mobile/src/App.tsx](products/tutorputor/apps/tutorputor-mobile/src/App.tsx), [products/tutorputor/apps/tutorputor-mobile/src/hooks/useDashboard.ts](products/tutorputor/apps/tutorputor-mobile/src/hooks/useDashboard.ts) |

## D. Content Generation and Validation Audit Matrix
| Artifact or pipeline | Intended objective | Generation method today | Validation method today | Correctness and pedagogy risk | Reproducibility and versioning | Monitoring state | Severity | Exact evidence |
|---|---|---|---|---|---|---|---|---|
| Learning claims | Generate objective-aligned claims | gRPC generation via LLM + parsing | Validation scorecard and claim/task/artifact checks | Medium: parsing and confidence are present, but fallback/default behavior appears in adjacent processors | Versioned experience model and events exist | Telemetry events and validation records exist | P1 | [products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/contentstudio/grpc/ContentGenerationServiceImpl.java](products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/contentstudio/grpc/ContentGenerationServiceImpl.java), [products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts](products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ClaimGenerationProcessor.ts), [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts) |
| Examples, simulations, animations | Build multimodal artifacts per claim | Queue + worker processors invoking gRPC client | Validation records and publish gating by artifact coverage | Medium-high when queue/worker unavailable; generation may be synthetic or defaults in some Java services | Asset materialization and revision structures exist | Worker telemetry exists | P1 | [products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts](products/tutorputor/services/tutorputor-platform/src/workers/content/processors/GenerationRequestJobProcessor.ts), [products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts](products/tutorputor/services/tutorputor-platform/src/workers/content/grpc/RealContentGenerationClient.ts), [products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java](products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/PlatformContentGenerator.java) |
| Content quality validation | Prevent low-quality publish | Rule/pillar score checks and issue lists | Publish gate uses canPublish and minimum score | Medium: strong structural gating, but safety and some dimensions are heuristic defaults | Validation history persisted | Validation events and records are observable | P1 | [products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts](products/tutorputor/services/tutorputor-platform/src/modules/content/studio/service.ts), [products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ContentValidationProcessor.ts](products/tutorputor/services/tutorputor-platform/src/workers/content/processors/ContentValidationProcessor.ts), [products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/agent/ContentQualityValidator.java](products/tutorputor/libs/tutorputor-ai/src/agents/main/java/com/ghatana/tutorputor/agent/ContentQualityValidator.java) |
| Production content-generation service boot | Start runnable service for generation | Launcher currently logs startup only | Not applicable | High operational risk if this service is assumed to be full runtime | No runtime lifecycle in launcher itself | Minimal from launcher | P0 | [products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/ContentGenerationLauncher.java](products/tutorputor/services/tutorputor-content-generation/src/main/java/com/ghatana/tutorputor/contentgeneration/ContentGenerationLauncher.java), [products/tutorputor/services/tutorputor-content-generation/build.gradle.kts](products/tutorputor/services/tutorputor-content-generation/build.gradle.kts) |

## E. UX and Cognitive Load Review
- Information architecture:
- Learner route map is coherent and canonicalized with redirects: [products/tutorputor/apps/tutorputor-web/src/router/routes.tsx](products/tutorputor/apps/tutorputor-web/src/router/routes.tsx)
- Auth integration introduces hidden complexity due to mismatched login route contracts.
- Navigation:
- Learner pages are organized by outcome-oriented route groups.
- Admin and learner auth behaviors are inconsistent, increasing cross-app cognitive friction.
- Workflow simplicity:
- Authoring flow is feature-rich but still requires manual claim/task/link operations for publish readiness.
- Form/input minimization:
- Good automation hooks exist, but generation and validation still require manual repair in many scenarios.
- Dashboard/report simplification:
- Multiple analytics and dashboard surfaces exist; not all are clearly separated by persona.
- Review/approval burden:
- Publication gating is explicit and useful, but can be burdensome when generation runtime is unavailable.
- Error/loading/empty/recovery:
- Exists in many components, but fallback logic can hide operational failures.
- Onboarding/discoverability:
- Login flow is currently highest friction due to endpoint mismatch and mixed auth assumptions.
- Progressive disclosure:
- Moderate quality. Some advanced controls are still exposed early in authoring contexts.
- Accessibility/usability barriers:
- Accessibility scoring appears in validation; end-user app-level accessibility evidence is less visible.
- Implicit AI assistance:
- Omnipresent tutor is present, but context identity assumptions can degrade trust.

## F. AI/ML Pervasive Automation Review
- Where AI is first-class:
- Tutor query routes and generation routes in platform AI module
- Content generation gRPC service implementations and worker processors
- Where AI is too shallow:
- Fallbacks and synthetic outputs in some generator implementations reduce production truthfulness.
- Where automation should increase:
- Automatic claim-to-artifact completion before author sees publish blockers.
- Explicitly model confidence thresholds that route to review.
- Where AI is too exposed:
- User-visible interactions still rely on manual context and direct question handling without deep workflow orchestration.
- Trust/fallback/escalation weaknesses:
- Worker and queue startup are optionalized and can silently disable generation capabilities.
- Policy/privacy/security around AI gaps:
- Consent enforcement is not active in runtime path.
- Opportunity to reduce user burden:
- Auto-repair of missing claim links/tasks/artifacts and guided remediation suggestions can be made automatic.

## G. Content Truth, Evidence, and Assessment Review
- Claim/evidence/task alignment:
- Explicit checks exist and publish gate enforces baseline linkage.
- Simulation correctness gaps:
- Simulation generation depends on queue/gRPC availability; default topologies often disable that path.
- Animation/visual fidelity gaps:
- Coverage checks exist, but independent visual QA evidence is limited.
- Example/explanation correctness gaps:
- Rule-based quality checks exist, but deep factual adjudication appears partial.
- Rubric/grading validity:
- Assessment and quality modules exist; full calibration evidence across realistic workloads is limited.
- Source/provenance gaps:
- Provenance records exist for publish, but not all generated outputs show robust independent source grounding.
- Seed/reproducibility gaps:
- Some generation code uses random IDs and synthetic defaults; deterministic replay is not clearly enforced end-to-end.
- Regression/evaluation gaps:
- There are many unit tests, but production-likeness varies, and E2E coverage is narrow.
- Anti-hallucination gaps:
- Validator and knowledge services exist, but runtime guarantees depend on integration paths not always active.

## H. API, Backend, DB, Integration Review
- API contract issues:
- Login provider path mismatch between frontend and backend.
- Missing refresh/logout routes expected by clients.
- Gateway and platform route topology can be confusing for auth and proxy assumptions.
- Orchestration issues:
- Queue and worker default disabled in common startup paths.
- Backend/domain logic issues:
- Trusted header bypass and runtime fail-open behavior for critical AI/worker paths.
- Database/query/transaction issues:
- Strong Prisma model coverage and event/provenance support in content studio.
- Event/job/worker issues:
- Job enqueue can noop when content queue disabled; generation capabilities appear present but may not execute.
- Integration contract issues:
- SSO and auth contract inconsistencies across apps and platform endpoints.
- Reuse/duplication issues:
- Significant UI duplication between app-local components and shared UI package.
- Scalability/cost/efficiency concerns:
- Some observability and cost telemetry are present; worker optionalization obscures realistic throughput/cost testing.

## I. Governance, Privacy, Security, Visibility Review
- Governance gaps:
- Consent policy not runtime-enforced.
- Privacy gaps:
- Consent route requirement model exists but inactive; risk for AI/analytics processing flows.
- Security gaps:
- Trusted proxy header auth bypass can be abused without strict perimeter controls.
- Auditability gaps:
- Good event logging in content workflow; auth/session lifecycle gaps reduce full traceability.
- Observability gaps:
- Strong metrics and health endpoints; runtime tracing exists but full dependency-health coupling could be tighter.
- Approval/control gaps:
- Publish gating works well; policy controls around AI/consent need stronger hard enforcement.
- Trust boundary issues:
- Header trust fallback is the largest boundary concern.
- Tenant/workspace isolation concerns:
- Many routes enforce tenant checks; bypass path and dev bypass patterns remain risks.

## J. Production Operability Review
- Deployment/config issues:
- Default app-stack disables content worker and queue functionality.
- Migration/rollback risk:
- No critical migration blocker identified from sampled files, but startup mode differences can mask failures.
- Resilience/failure handling issues:
- Worker init can fail and continue unless strict flag set.
- Backup/restore/disaster recovery gaps:
- No strong direct evidence of DR runbooks in sampled Tutorputor-specific files.
- Monitoring/alerting gaps:
- Metrics/health/tracing exist; actionable SLO and alert policy evidence is partial.
- Supportability/incident diagnosis:
- Correlation IDs and structured metrics exist, which is a strength.
- Operational burden risks:
- Conditional service startup and profile-dependent behavior increase operator ambiguity.
- Environment parity/config hygiene:
- Local and production-like execution differ significantly in queue/worker posture.

## K. Testing and Evidence Gaps
- Missing workflow tests:
- No broad E2E suite proving learner login + SSO + refresh/logout lifecycle.
- Missing cross-product tests:
- Limited direct evidence of robust platform-to-content-generation-runtime integration tests in realistic deployment mode.
- Missing production-behavior tests:
- Need tests where content worker is required and startup fails hard.
- Missing privacy/security/governance tests:
- Need integration tests proving consent middleware is attached and enforced at runtime.
- Missing failure/recovery tests:
- Need full-path queue, gRPC outage, retry, and compensation behavior tests.
- Missing content-validation tests:
- Need independent golden set tests for content correctness beyond structural checks.
- Missing simulation/animation correctness tests:
- Need pedagogical and scientific correctness tests on generated artifacts.
- Missing grading/rubric validation tests:
- Need calibration and drift regression tests.
- Weak or misleading tests:
- E2E currently demonstrates a narrow admin happy path and uses trusted header auth shortcut.
- Highest-risk unproven claims:
- Truly production-grade AI-pervasive automated content lifecycle under strict governance controls.

## L. Prioritized Remediation Plan
### P0: Must fix immediately
- Issue: Header-based auth bypass for guarded routes.
- Why it matters: Enables identity spoofing if perimeter assumptions break.
- Affected outcomes: Security, tenant isolation, governance trust.
- Root cause: Trusted proxy fallback in global auth guard and request context utilities.
- Exact fix: Remove external fallback by default; gate trusted-header mode behind explicit internal-only config and source IP/network checks.
- Validation: Add integration tests for token-required behavior across guarded endpoints.

- Issue: Consent enforcement not active in runtime.
- Why it matters: AI/analytics processing may occur without required consent.
- Affected outcomes: Privacy compliance and legal trust.
- Root cause: Middleware implemented but not registered.
- Exact fix: Register consent pre-handler for relevant route groups and align identity field usage with JWT claim shape.
- Validation: End-to-end consent required tests for AI and analytics endpoints.

- Issue: Content generation launcher not a full runnable service lifecycle.
- Why it matters: Operational readiness overstates runtime capability.
- Affected outcomes: Content automation reliability.
- Root cause: Minimal launcher implementation.
- Exact fix: Implement full bootstrap with server start, health, config validation, and graceful shutdown.
- Validation: Startup, health, and generation smoke tests in CI.

### P1: Required for production trust
- Issue: Frontend/backend auth contract mismatch for login/refresh/logout.
- Exact fix: Align routes and token lifecycle APIs, update clients, and add contract tests.
- Evidence: [products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx](products/tutorputor/apps/tutorputor-web/src/components/auth/SsoLogin.tsx), [products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx](products/tutorputor/apps/tutorputor-web/src/contexts/AuthContext.tsx), [products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts](products/tutorputor/services/tutorputor-platform/src/modules/auth/index.ts)

- Issue: Queue/worker disabled by default in main dev topology.
- Exact fix: Provide production-like profile that enables queue and required worker; make this the default for acceptance testing.

- Issue: Mobile runtime uses browser localStorage in app init and hooks.
- Exact fix: Replace with secure native storage abstraction for auth/session.

### P2: Simplification and automation hardening
- Issue: High UI duplication across app-local components and shared UI package.
- Exact fix: Consolidate shared primitives into Tutorputor UI package and enforce usage boundaries.

- Issue: Manual burden in claim-task-artifact completion.
- Exact fix: Add auto-remediation assistant that proposes and applies missing linkage/content generation steps.

### P3: Strategic improvements
- Issue: Expand pedagogical correctness and provenance guarantees.
- Exact fix: Build independent evaluator stack, golden datasets, SME-in-the-loop escalations, and seeded replay harness.

## M. Simplicity and Automation Blueprint
- Workflows to simplify:
- Merge learner auth flows into one canonical token/session path.
- Auto-populate content needs and artifact plans per claim, with one-click apply.
- Screens/routes to merge/remove:
- Reduce overlapping analytics/dashboard entry points per persona.
- Inputs to infer/auto-populate:
- Tenant/user context from authenticated session only.
- Claim metadata defaults from domain/grade profile.
- Decisions to automate:
- Auto-queue missing artifacts for claims that fail publish gate.
- Decisions to recommend instead of asking:
- Suggested remediation plan ranking based on validation severity.
- Unnecessary review points to remove:
- Repeated manual trigger steps once claim baseline data is present.
- Review/governance points to retain:
- Publish approval when confidence below threshold, policy flags present, or sensitive domain.
- AI interventions to add:
- Auto-link orphan tasks and propose corrections before blocking publish.
- AI exposure to reduce:
- Avoid user-facing low-level generation controls in primary learner flows.
- Visibility/audit controls to strengthen:
- Mandatory policy decision log with consent and model-version evidence.

## N. Content System Hardening Blueprint
- Objective to claim to evidence to task to feedback modeling:
- Enforce strict schema progression and failure reasons at each edge.
- Simulation architecture hardening:
- Deterministic simulation validation suite by domain with physical/model sanity checks.
- Animation and visual QA hardening:
- Frame-level semantic checks against claim statements and known misconceptions.
- Example/explanation generation hardening:
- Independent verifier model and rule engine separate from generator.
- Provenance and grounding controls:
- Attach source IDs and confidence per generated assertion, mandatory for publish.
- Seeded reproducibility and versioning:
- Require deterministic seed on generation jobs and retain replay manifests.
- SME/instructional review gates:
- Trigger gate for low confidence, high-risk domain, or contradiction signals.
- Evaluation harnesses and golden datasets:
- Curate domain-specific benchmark corpora for correctness and pedagogy.
- Regression suite for generated artifacts:
- Add snapshot + semantic regression checks for claims/examples/simulations/animations.
- Telemetry signals:
- Track post-release learner confusion signals, correction frequency, and artifact rollback rates.
- Remediation and rollback strategy:
- One-click rollback to previous approved artifact version with dependency impact report.

## O. Product System Architecture Corrections
- Product boundary corrections:
- Clarify runtime ownership between platform service, tutorputor-content-generation service, and tutorputor-ai library.
- Shared capability consolidation:
- Consolidate authentication client logic and route contracts across web/admin/mobile.
- Dependency direction fixes:
- Ensure gateway remains thin and avoid direct behavior drift from platform setup assumptions.
- Ownership clarification:
- Define single owner for AI content runtime boot path and enforce operational SLO.
- Integration simplification:
- Publish one canonical API contract document generated from actual route registrations.
- Contract normalization:
- Align /auth, /api/v1/auth, and app-side route assumptions.
- Data boundary corrections:
- Enforce consent and tenant boundaries through mandatory middleware chain.

## P. Final Truth Statement
- What truly works end to end today:
- Core platform module registration, health/metrics surfaces, and a narrow admin authoring happy path with validate/publish evidence.
- What works only partially:
- AI-pervasive content generation and policy-first controls, due to optional runtime dependencies and disabled-default queue/worker topology.
- What is misleading or overstated:
- Production trust claims for fully integrated generation runtime where launcher/runtime wiring remains partial.
- What creates user burden:
- Broken or inconsistent auth/session flows and manual remediation burden in content authoring.
- What blocks dead-simple UX:
- Contract mismatches and duplicated auth/UI logic across apps.
- What blocks pervasive automation:
- Queue/worker optionalization and incomplete runtime boot contracts.
- What blocks trustworthy content generation:
- Inconsistent grounding/provenance guarantees and synthetic fallback behavior in some generation components.
- What blocks trustworthy content validation:
- Limited independent validation depth and inactive consent-policy enforcement.
- What blocks production trust:
- Trusted header bypass path and inactive policy middleware in production request chain.
- What blocks governance/privacy/security/visibility maturity:
- Consent enforcement not wired and fail-open trust boundaries.
- What blocks operability and resilience:
- Environment-profile drift and conditional startup behavior for critical AI content paths.
- What must change to reach target state:
- Enforce strict authenticated identity boundaries, activate consent middleware, align auth contracts across clients, make content worker and generation runtime first-class operational dependencies, and harden content correctness/provenance with independent evaluators and deterministic replay.

## Appendix: Additional Notable Evidence
- Shared UI package remains thin relative to app-level UI duplication: [products/tutorputor/libs/tutorputor-ui/src/components/index.ts](products/tutorputor/libs/tutorputor-ui/src/components/index.ts)
- API gateway delegates directly to platform setup, reducing separation but increasing coupling risk: [products/tutorputor/apps/api-gateway/src/createServer.ts](products/tutorputor/apps/api-gateway/src/createServer.ts)
- Existing audit doc reference in current-state file appears stale/missing at referenced location: [products/tutorputor/docs/architecture/CURRENT_STATE.md](products/tutorputor/docs/architecture/CURRENT_STATE.md)
