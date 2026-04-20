# Data Cloud Product Reality Audit

Date: 2026-04-19

Scope audited:

- Product: `products/data-cloud`
- Relevant shared UI/UX libraries: `platform/typescript/design-system`, `platform/typescript/data-grid`, `platform/typescript/canvas`, `platform/typescript/theme`, `platform/typescript/wizard`, `platform/typescript/foundation/platform-utils`
- Relevant backend/platform libraries: `platform:java:http`, `platform:java:security`, `platform:java:observability`, `platform:java:audit`, `platform:java:ai-integration`, `platform:java:workflow`
- Related product/library dependency inspected: `products/audio-video` via `@audio-video/ui`

Validation executed during audit:

- `./gradlew :products:data-cloud:launcher:test --tests '*DataCloudHttpServerGovernanceTest' --tests '*DataCloudHttpServerAlertsTest'` -> passed
- `corepack pnpm --dir products/data-cloud/ui exec vitest run src/__tests__/pages/AlertsPage.test.tsx src/__tests__/pages/TrustCenter.test.tsx src/__tests__/e2e/CriticalPathJourney.test.tsx` -> failed before executing tests due unresolved `@ghatana/data-grid` import through `@ghatana/design-system`
- Editor diagnostics on `products/data-cloud/ui` -> no active TypeScript/editor errors reported

Method summary:

- Reconstructed intended product behavior from product docs, route truth docs, runbook, OpenAPI, build wiring, runtime handlers, UI routes/pages, and tests.
- Sampled real backend implementation paths in the launcher HTTP server, security filter, alert/governance handlers, and integration tests.
- Sampled major UI journeys and the shared platform libraries they depend on.
- Treated tests as evidence, not as proof by default.

Assumptions:

- This audit is evidence-based from repository state, not from a deployed environment.
- “Production-ready” requires truthful end-to-end evidence for runtime behavior, failure handling, and UI testability, not only route presence.
- AEP remains the owning control plane for agent execution and broader orchestration as stated in the product docs.

## A. Executive Verdict

Overall maturity: `partial / late-beta backend, pre-production frontend system`

- End-to-end readiness: `partial`
- Production readiness: `partial for core backend CRUD/event/governance primitives, not credible for the full advertised product surface`
- AI/ML-first maturity: `partial`
- Automation maturity: `partial`
- UX simplicity rating: `6/10 for primary-user navigation, 3/10 for total product surface honesty and completion`
- Cognitive load assessment: `improved shell disclosure, but too many partial/read-only/preview surfaces remain exposed`
- Governance/privacy/security/visibility maturity: `mixed; strong intent and some real controls, but weak API semantics and uneven product truth`
- Operability/resilience maturity: `mixed; runbook and durable profiles exist, but test evidence is overstated and deployment truth is incomplete`

Top blockers:

1. Shared UI library packaging is broken for representative Data Cloud UI test execution. `@ghatana/design-system` re-exports `@ghatana/data-grid`, but the design-system package does not declare it while `@ghatana/data-grid` itself depends on `@ghatana/design-system`, creating a cycle and causing Vite/Vitest resolution failure.
2. Large parts of the evidence base are misleading. Several “integration” or “e2e” tests are trivial or fully in-memory doubles, so they do not prove production behavior.
3. API error semantics are not production-safe enough. Governance and other HTTP tests normalize invalid requests to HTTP `200` with embedded error envelopes, which weakens clients, observability, retries, and operator trust.
4. Product truth is fragmented. The docs, boundary registry, and UI behavior disagree on whether some surfaces are live, preview, read-only, or absent, especially alerts and operator/admin surfaces.
5. AI is present across many surfaces but is not yet deeply outcome-automating. Much of it is assistive, heuristic, or fallback-oriented rather than pervasive, low-burden automation.

Bottom line:

- Data Cloud has real product substance in the launcher, entity/event/governance/memory/context routes, and operational documentation.
- It is not yet trustworthy as a fully working, dead-simple, production-grade, AI/ML-pervasive product system across the full advertised UI and workflow portfolio.

## B. Reconstructed Product Model

Target personas / users:

- Analysts running ad hoc analytics and collection exploration
- Operators handling runtime health, trust, alerts, and incident response
- Admins configuring platform/runtime boundaries
- Product teams and adjacent products using Data Cloud as storage, event, memory, context, and registry substrate
- AEP and other products consuming Data Cloud contracts or runtime state

Jobs to be done:

- Create, query, validate, export, and semantically search tenant-scoped entities
- Append/query events and use them as an event-backed persistence surface
- Define and execute pipelines, observe execution state, and persist checkpoints/logs
- Run analytics queries with AI assistance and optional federated execution
- Apply governance actions: classify retention, preview purge, redact data, inspect compliance posture and audit activity
- Persist agent memory and context for AI-assisted workflows
- Publish discoverable data products for reuse

Expected outcomes:

- Data infrastructure should feel like one calm product, not a set of partially connected consoles
- Users should mostly work from a narrow primary shell: home, data, pipelines, query
- Operators should get truthful diagnostics, trust actions, and incident signals
- Adjacent products should rely on stable contracts and clear ownership boundaries
- AI should reduce input burden, review burden, and navigation burden

Primary workflows:

- Collection/entity lifecycle
- Event ingest and exploration
- Pipeline creation/execution/monitoring
- Analytics querying and NLQ guidance
- Governance trust actions

Secondary workflows:

- Data products publish/subscribe
- Context and RAG usage
- Memory plane inspection
- Plugin and agent catalog review
- Alert triage

Cross-product workflows:

- AEP using Data Cloud as persistence/event/cloud substrate rather than surrendering orchestration ownership
- YAPPC and other products consuming `:products:data-cloud:spi`
- Audio-video dependency used in UI only for speech synthesis hooks on selected pages

Expected AI/ML role:

- Intent routing on the home page
- Workflow draft generation
- Analytics suggestion and explain-before-run guidance
- Similarity/RAG/context retrieval
- Alert grouping and suggested resolution
- Governance recommendations and trust posture summarization

Expected user involvement level:

- User should provide domain intent, dataset selection, and high-level review
- User should not need to manually navigate many fragmented operator or preview surfaces
- Review should be required only for destructive or low-confidence actions

Justified human review / governance points:

- Purge confirmation
- Low-confidence workflow drafts
- Expensive analytics plans
- Destructive alert auto-resolution or action application
- Privacy-sensitive redaction and compliance actions

Operational expectations:

- Standalone launcher with health, metrics, auth, tenant isolation, audit, and durable storage profiles
- Credible local mode and sovereign durable mode
- Clear capability truth for optional services such as Trino or AI providers

## C. End-to-End Workflow Audit Matrix

| Workflow | Intended outcome | Actual current behavior | User burden | AI today | AI that should exist | UI/API/backend/DB/async assessment | Governance/security/visibility | Production operability | Test evidence | Key gaps | Severity | Exact files/services/components involved |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Collection and entity lifecycle | Create, inspect, validate, export, and search tenant data | Backend routes are real and launcher-backed. UI routes `/data` are canonical. Semantic search and validation exist. | Moderate. Primary flow is simpler than before, but lineage/quality remain previews or merged subviews. | Similarity and suggestion helpers exist. | Automatic schema inference, duplicate detection, entity prefill, conflict resolution. | Strongest end-to-end area in the product. Entity CRUD, export, validation, semantic search, and CDC routes are concretely wired. | Tenant scoping exists in backend auth/filter chain. | Reasonably credible on backend; less so on full browser path. | Backend tests exist, but many are still mock-heavy. UI “journey” tests are render-only. | UI proof is weak; browser-grade truth is not established. | Medium | `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/DataCloudRouterBuilder.java`, `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EntityCrudHandler.java`, `products/data-cloud/ui/src/pages/DataExplorer.tsx` |
| Event append and exploration | Append/query events, inspect streams, power downstream workflows | Backend event routes are real; operator `/events` route is live. | Low to moderate for operators. | Limited AI on top of event data. | Correlation, anomaly clustering, replay safety recommendations. | Event append/query/stream routes are wired in launcher. | Tenant isolation and audit intent exist. | Credible as backend primitive. | Backend tests exist. | UI truth still mostly mock-backed; cross-product replay evidence is thin. | Medium | `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/EventHandler.java`, `products/data-cloud/ui/src/pages/EventExplorerPage.tsx` |
| Pipelines and workflow execution | Capture intent, define pipelines, execute them, inspect durable execution | Metadata CRUD and execution routes are real. Execution remains single-process plugin execution, not distributed orchestration. | Moderate. Users still step from intent capture into manual editing. | Workflow draft generation with confidence/fallback metadata. | Safer end-to-end execution planning, node autofix, input inference, auto-validation, rollback guidance. | Real backend routes plus real plugin manager. But orchestration scope is narrower than some UI copy implies. | Review/fallback messaging is honest in parts. | Good enough for local/runtime metadata, not enough for “full orchestration plane” claims. | Evidence overstated: `EndToEndWorkflowTest` uses in-memory client and mocks for storage surfaces. UI “e2e” is hook/render-level only. | Distributed execution and durable multi-worker claims are not proven. | High | `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/WorkflowExecutionHandler.java`, `products/data-cloud/ui/src/pages/SmartWorkflowBuilder.tsx`, `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java` |
| SQL workspace | Let users ask questions and run analytics with low-friction guardrails | Query, explain, and suggestion routes exist. UI adds client-side scope inference and execution recommendations. | Moderate to high. User still needs SQL awareness and confidence handling is shallow. | Suggest, explain, scope inference, execution recommendation. | Dataset auto-selection, rewrite/optimization, policy-aware redaction, stronger low-confidence clarification. | Backend query/explain routes exist. UI logic is partly heuristic and client-side regex-driven. | Some guardrails exist, but not governance-first enough for expensive/shared data use. | Partial. Depends on optional capabilities such as Trino. | UI tests are mock-based; backend query route tests exist. | Analytics assistance is more advisory than automation-first. | High | `products/data-cloud/ui/src/pages/SqlWorkspacePage.tsx`, `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/AnalyticsHandler.java` |
| Trust center / governance | Give operators truthful compliance, redaction, retention, and purge workflows | Retention classification, purge preview, redaction, compliance summary, and audit views are wired. Access review remains explicitly read-only. | Moderate. Still operator-heavy and not fully lifecycle-complete. | Recommendations and derived posture cards. | Policy gap detection, remediation proposals, automatic safe defaults, approval routing. | The action-backed subset is real. The broader governance lifecycle is not. | Security intent is strong, but API semantics are weak because invalid inputs can still be HTTP `200` with error payloads. | Operable in limited scope, but not yet a full trust/governance plane. | Focused launcher governance tests passed in this audit, but those tests also codify `200` on invalid requests. | Error semantics and lifecycle incompleteness block production trust. | High | `products/data-cloud/ui/src/pages/TrustCenter.tsx`, `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerGovernanceTest.java`, `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/DataLifecycleHandler.java` |
| Alerts triage | List, group, suggest, acknowledge, resolve, and stream incidents | Backend alert routes are real and focused tests pass. UI also has unsupported-boundary messaging for absent deployments. Docs and boundary registry disagree on truth. | Moderate for operators; discovery is hidden, which is correct, but product truth is inconsistent. | Grouping and resolution suggestion exist. | Correlated cause tracing, auto-remediation with policy gates, richer incident timelines. | Backend is more real than some UI boundary metadata suggests. | Visibility exists, but trust suffers because product truth is contradictory. | Partial but real on backend. | Focused backend alerts tests passed. Representative UI tests failed before execution because of shared design-system packaging issue. | Docs/UI/runtime truth drift. | High | `products/data-cloud/launcher/src/test/java/com/ghatana/datacloud/launcher/http/DataCloudHttpServerAlertsTest.java`, `products/data-cloud/ui/src/pages/AlertsPage.tsx`, `products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts`, `products/data-cloud/ROUTE_TRUTH_MATRIX_2026-04-17.md` |
| Agent catalog and plugin surfaces | Show safe discovery of agent/plugin capabilities without leaking control-plane complexity | Agent catalog is explicitly read-only. Plugin inventory is partial, with dependency graph and hot-swap/install boundaries. | High for operators looking for a complete control plane; they hit many boundaries. | Little beyond summaries. | Recommend owning control plane route, hide or merge incomplete areas, automatic dependency/health summarization. | Boundaries are explicit, which is good, but too much non-actionable surface remains. | Ownership with AEP is explicit. | Safe but incomplete. | Tests mostly confirm boundary messaging, not meaningful business outcomes. | Product exposes too many “not here” pages. | Medium | `products/data-cloud/ui/src/pages/AgentPluginManagerPage.tsx`, `products/data-cloud/ui/src/pages/PluginsPage.tsx`, `products/data-cloud/ui/src/pages/PluginDetailsPage.tsx` |
| Settings/admin | Offer actual admin controls | Entire page is a boundary shell with no writable backed features. | High. This is a navigable dead end. | None. | Either remove from product shell or back with real admin APIs. | Not a real workflow. | Not a security issue by itself, but increases false affordance. | Not production-meaningful as a user-facing feature. | Boundary-only tests. | Should not count as implemented product surface. | Medium | `products/data-cloud/ui/src/pages/SettingsPage.tsx`, `products/data-cloud/ui/src/components/common/unsupportedSurfaceRegistry.ts` |
| Data fabric | Show live topology and metrics | Explicit preview-only topology with hard-coded preview metrics. | Moderate if operators expect live telemetry. | None beyond visualization framing. | Live metrics, anomaly overlays, tenant-aware drilldowns, capacity recommendations. | Preview only. | Honest boundary exists. | Not production-real today. | Tests verify preview shell only. | Must not be marketed as live capability. | Medium | `products/data-cloud/ui/src/pages/DataFabricPage.tsx` |
| Context, memory, RAG | Store and retrieve agent context/memory and grounded retrieval | Backend contracts exist and docs describe live use. | Moderate because operator-facing pages remain niche. | RAG and memory retrieval are core AI enablers. | Better automatic context curation, policy-aware grounding, aging and confidence controls. | Backend looks real; UI evidence is light. | Security/privacy sensitivity is high and needs stronger evidence than present tests provide. | Potentially strong backend primitive. | Tests exist but not enough full-journey proof. | Needs stronger privacy and lifecycle evidence. | Medium | `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/MemoryPlaneHandler.java`, `products/data-cloud/launcher/src/main/java/com/ghatana/datacloud/launcher/http/handlers/ContextLayerHandler.java`, `products/data-cloud/ui/src/pages/MemoryPlaneViewerPage.tsx` |

## D. UX and Cognitive Load Review

Information architecture:

- The primary shell is materially better than the old sprawl. `Home`, `Data`, `Pipelines`, and `Query` are a sensible outcome-first set.
- That said, the repo still carries many pages that are partial, read-only, or preview-only. This reduces trust even if discovery is narrowed.

Navigation:

- Shell-role disclosure is a good simplification pattern.
- It is explicitly not authorization. The role mode is session-storage state and header disclosure, not security control.
- Direct-link operator/admin surfaces remain reachable, which is fine only if backend auth is complete and truthful UX copy remains explicit.

Workflow simplicity:

- `IntelligentHub` improves first-step simplicity, but many downstream workflows still require the user to understand product seams.
- Smart workflow creation is still a two-step pattern: describe intent, then continue in manual editing when automation is limited.

Form/input minimization:

- Governance pages now prefill some action recommendations, which is good.
- SQL and workflow flows still rely heavily on user specification rather than system inference.

Dashboard/report simplification:

- `Insights` is correctly framed as operator-facing rather than pretending to be a general BI suite.
- `DataFabricPage` remains preview-only and should not be exposed as a production diagnostic center.

Review/approval burden:

- Review is present in the right general places: purge, low-confidence AI draft, explain-before-run.
- Approval and access review lifecycles are incomplete or read-only, so governance burden is not reduced end to end.

Error/loading/empty/recovery states:

- Many pages have explicit degraded/unavailable boundary copy.
- That honesty is better than faking functionality, but the volume of such states indicates the product is still too incomplete for a “dead simple” claim.

Onboarding/discoverability:

- The role-aware shell helps.
- The product still needs too much institutional knowledge around what is actually live, partial, or owned elsewhere.

Progressive disclosure:

- One of the stronger aspects of the UI.
- However, progressive disclosure is sometimes compensating for unfinished surfaces rather than just reducing cognitive load.

Accessibility/usability barriers:

- Shared platform libraries include accessibility-focused packages and utilities.
- Practical product validation is blocked because representative UI suites fail before running.

Implicit AI/ML assistance:

- Present in wording and helper flows, but still too explicit and advisory in many places.

## E. AI/ML Pervasive Automation Review

Where AI/ML is correctly first-class:

- Semantic similarity and RAG are real platform capabilities.
- Alerts use grouping and resolution suggestions.
- SQL workspace includes NLQ suggestion and explain-before-run guidance.
- Workflow builder includes draft generation, provenance, and confidence/fallback handling.

Where AI is too shallow:

- Home-page intent routing is largely client-side classification logic, not a deeper system of context-aware automation.
- SQL assistance still leaves most of the burden on the user to refine dataset scope and query correctness.
- Workflow drafting helps with initial structure but does not yet eliminate significant downstream manual editing.

Where AI should automate more:

- Collection schema inference and quality remediation
- Duplicate/conflict detection on entity creation
- Policy-aware analytics rewriting and redaction
- Cross-surface next-best-action recommendations based on operator context
- Smarter trust/governance remediation sequencing

Where AI is too exposed:

- Many surfaces visibly discuss fallback, confidence, unsupported AI, and degraded modes. That honesty is good, but it means the product still exposes too much of the AI plumbing.

Where trust/fallback/escalation is weak:

- UI tests for AI-heavy pages are mostly mocked.
- Operator trust depends on AI truth panels and fallback counters, but not enough on proven outcome quality or evaluation pipelines.

Where policy/privacy/security around AI/ML is insufficient:

- Context/memory/RAG flows are security- and privacy-sensitive but do not yet have strong end-to-end evidence for redaction, retention, and approval gating across AI use.

Where AI should reduce user burden but currently does not:

- Analytics intent disambiguation
- Workflow refinement after draft generation
- Governance remediation playbooks
- Operator incident response sequencing

Verdict:

- Data Cloud is AI-aware and AI-assisted.
- It is not yet AI/ML-pervasive in the sense of deeply eliminating user burden across the primary product system.

## F. API / Backend / DB / Integration Review

API contract issues:

- The OpenAPI presents a broad, production-like contract surface, but the UI and route-truth docs explicitly narrow what is truly live.
- Invalid governance requests are tested as HTTP `200` with embedded error blocks, which is not a production-grade contract for clients or observability.

Orchestration issues:

- Workflow execution is real but constrained to a single-process plugin runtime. It should not be overinterpreted as a broader durable orchestrator.

Backend/domain issues:

- Backend composition is credible and reuse-heavy. The launcher correctly composes platform HTTP, security, observability, audit, and AI integration libraries.
- Product truth suffers more from evidence quality and API semantics than from obvious architectural chaos.

Database/query/transaction issues:

- Durable profiles are documented and there is evidence for sovereign/postgres/kafka-backed modes.
- However, much of the integration proof still uses in-memory doubles instead of proving persistence semantics across real boundaries.

Event/job/worker issues:

- Event routes are real.
- Workflow and alert side effects are present, but asynchronous and recovery behavior are not convincingly proven by the so-called integration suite.

Integration contract issues:

- Cross-product ownership is explicit for AEP agent control plane.
- The UI inherits a platform integration failure from the shared design system / data-grid packaging relationship.
- The UI also depends on `@audio-video/ui` for speech hooks in pages where that capability is not central to Data Cloud’s primary value.

Consistency/integrity issues:

- Alerts are a concrete inconsistency: backend routes and tests say “live”, while the unsupported-surface registry says “not in deployment”.
- This is a trust problem, not just a doc nit.

Reuse/duplication issues:

- Reuse of platform Java libraries is a strength.
- Reuse on the TypeScript side is directionally correct, but package wiring quality is not stable enough.
- `products/data-cloud/ui/package.json` duplicates `@ghatana/wizard`, which is minor but signals hygiene drift.

Scalability/cost/efficiency concerns:

- Platform-launcher coverage thresholds remain very low: instruction `0.20`, branch `0.10`.
- Performance and load tests exist, but some broader evidence remains synthetic or not tied to product outcomes.

## G. Governance / Privacy / Security / Visibility Review

Governance gaps:

- Trust Center is action-backed only for a subset of the promised lifecycle.
- Access review remains read-only.
- Policy CRUD and broader governance workflows are incomplete.

Privacy gaps:

- Privacy-sensitive flows exist, but the evidence for end-to-end redaction correctness, retention deletion, and AI-context privacy boundaries is not strong enough.

Security gaps:

- Backend security filter is solid in shape: API key/JWT auth, tenant isolation, policy checks, audit hooks.
- Frontend shell roles are stored client-side and explicitly do not provide authorization.
- Token handling still relies on browser-managed storage with a code comment pointing to future migration to httpOnly cookies.

Auditability gaps:

- Audit intent is present in backend security and governance surfaces.
- End-to-end evidence of audit fidelity under failure paths is weaker than the product messaging suggests.

Observability gaps:

- Health, metrics, and capability truth routes exist.
- `Insights` and AI truth concepts exist.
- Some surfaces such as fabric metrics remain preview-only, and broader operator truth remains fragmented.

Approval/control gaps:

- Governance approvals and access review are incomplete.
- Agent control plane mutation is owned elsewhere, which is appropriate, but the UI still exposes non-actionable surfaces.

Trust boundary issues:

- AEP ownership boundary is explicit and mostly well handled.
- The UI/design-system dependency break means a platform boundary issue can prevent Data Cloud product validation entirely.

Tenant/workspace isolation concerns:

- Backend intent is strong.
- `MultiTenantIsolationTest` is not strong evidence because it uses a custom in-memory store unrelated to the real persistence stack.

## H. Production Operability Review

Deployment/config issues:

- Runbook quality is good. Profiles and environment variables are documented clearly.
- Kubernetes and Helm are documentation-backed, not proven by this repository state.

Migration/rollback risk:

- There is explicit profile and durable storage guidance.
- The repo still lacks strong evidence that migrations, rollback, and state compatibility have been exercised through meaningful product flows.

Resilience/failure handling issues:

- The strongest negative evidence in the repo is the weakness of resilience tests. `FailureRecoveryTest` is effectively placeholder-level and does not test Data Cloud runtime behavior.

Backup/restore/disaster recovery gaps:

- Sovereign restore and postgres recovery guidance exists in the runbook.
- Evidence of automated or integration-tested restore paths is thin.

Monitoring/alerting gaps:

- Alert routes exist.
- Operator trust still suffers from inconsistencies between docs/UI boundaries/runtime truth.

Supportability/incident diagnosis gaps:

- There is meaningful runbook and capability-truth investment.
- The product still asks operators to understand too many partial surfaces and ownership seams.

Operational burden risks:

- The product is not yet “quiet automation first”; it still requires too much operator interpretation.

Environment parity/config hygiene issues:

- `pnpm` was not on PATH in the audited shell environment, though `corepack` worked.
- The more important parity issue is package/runtime wiring: UI tests fail on workspace resolution despite the package existing in the monorepo.

## I. Testing and Evidence Gaps

Missing workflow tests:

- Browser-level or realistic API-backed validation of primary-user paths: home -> data -> pipelines -> query -> trust
- Durable, real-storage workflow execution tests
- Policy-aware analytics and trust workflows under failure modes

Missing cross-product tests:

- Strong end-to-end AEP <-> Data Cloud operational journeys
- Strong verification that audio-video UI dependency does not destabilize Data Cloud pages

Missing production-behavior tests:

- Restore/recovery validation using real durable stores
- Backpressure, timeout, and partial outage behavior for optional AI/Trino services
- Authz and tenant-boundary enforcement against real request paths

Missing privacy/security/governance tests:

- End-to-end proof that invalid governance calls return safe, actionable HTTP semantics
- Memory/context/RAG privacy lifecycle tests across retention/redaction/deletion

Missing failure/recovery tests:

- `FailureRecoveryTest` is not an actual recovery test. It asserts booleans and object construction only.

Weak or misleading tests:

- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/FailureRecoveryTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/MultiTenantIsolationTest.java`
- `products/data-cloud/integration-tests/src/test/java/com/ghatana/datacloud/integration/EndToEndWorkflowTest.java`
- `products/data-cloud/ui/src/__tests__/e2e/CriticalPathJourney.test.tsx`
- `products/data-cloud/ui/tests/e2e/workflow.e2e.test.ts`

Highest-risk unproven product claims:

- “Working end to end” across the full UI portfolio
- “Production-grade” trust/governance semantics
- “AI/ML-pervasive” automation-first UX
- “E2E critical path” UI proof

## J. Prioritized Remediation Plan

### P0: must fix immediately

1. Fix shared TypeScript package wiring for `@ghatana/design-system` and `@ghatana/data-grid`.
   - Why it matters: representative Data Cloud UI test suites do not start.
   - Affected outcomes: all UI product validation, release confidence, platform reuse credibility.
   - Affected layers: platform TypeScript libraries, Data Cloud UI.
   - Root cause: design-system re-exports `@ghatana/data-grid` without declaring it; `@ghatana/data-grid` depends on design-system, creating a problematic cycle.
   - Exact fix: remove the cycle by making `@ghatana/data-grid` depend only on lower-level packages, or move the re-export behind a proper dependency declaration and build boundary.
   - AI implication: blocks validation of AI-heavy pages.
   - Governance/privacy/security implication: operator/governance pages cannot be credibly tested.
   - Production operability implication: broken release confidence for UI.
   - Required validation/tests: rerun the three failed suites plus broader Data Cloud UI smoke tests.

2. Stop returning HTTP `200` for invalid request scenarios that are actually client errors.
   - Why it matters: breaks client correctness, alerting, dashboards, retries, and operator trust.
   - Affected outcomes: governance, AI assist, voice, and other envelope-based routes.
   - Affected layers: launcher handlers, HTTP contract, SDKs, UI error handling.
   - Root cause: envelope-first error pattern normalized into success status codes.
   - Exact fix: use proper `4xx`/`5xx` statuses while preserving structured error bodies.
   - AI implication: low-confidence or invalid AI requests become easier to manage safely.
   - Governance/privacy/security implication: destructive and compliance flows become auditable and monitorable.
   - Production operability implication: real errors become visible to SLOs and incident tooling.
   - Required validation/tests: update OpenAPI, HTTP contract tests, UI service error handling tests.

3. Reclassify misleading tests and replace placeholders with real boundary tests.
   - Why it matters: current evidence overstates production readiness.
   - Affected outcomes: release trust, architecture decisions, auditability.
   - Affected layers: integration-tests, UI test suite.
   - Root cause: convenience tests labeled as end-to-end or integration.
   - Exact fix: rename synthetic tests or rebuild them against launcher plus real durable providers where applicable.
   - AI implication: AI quality and fallback claims become measurable.
   - Governance/privacy/security implication: governance promises become demonstrable.
   - Production operability implication: reduces false confidence.
   - Required validation/tests: real launcher-backed integration suite, browser-level smoke pack.

### P1: required for production trust

1. Reconcile product truth across docs, route matrix, and UI boundaries.
2. Remove or demote boundary-only pages from product framing until APIs exist.
3. Raise coverage thresholds materially above `0.20` instruction / `0.10` branch for `platform-launcher`.
4. Prove durable workflow execution, recovery, and tenant isolation against real providers.
5. Strengthen auth/session posture on the frontend and stop treating shell role as a quasi-product mode without clearer wording.

### P2: simplification and automation hardening

1. Collapse more operator-only surfaces into richer `Insights` and `Trust` rather than separate partial pages.
2. Improve analytics automation: intent clarification, query rewrite, safe scope inference, and policy-aware recommendations.
3. Improve workflow draft automation: post-draft refinement, validation, and suggested fixes.
4. Reduce the `@audio-video/ui` dependency footprint unless it materially improves a core Data Cloud outcome.

### P3: strategic improvements

1. Make Data Cloud’s AI truth/evaluation pipeline measurable and regression-tested.
2. Clarify long-term ownership between Data Cloud, AEP, and shared platform control planes.
3. Turn preview/operator tooling into a coherent operations console with fewer boundary dead ends.

## K. Simplicity and Automation Blueprint

Workflows to simplify:

- Merge operator diagnostics into `Insights` and trust remediation into `Trust` instead of keeping multiple partial pages.
- Keep only `Home`, `Data`, `Pipelines`, `Query`, `Trust`, and `Insights` as first-class product surfaces.

Screens/routes to merge or remove:

- Remove `Settings` from product framing until backed by real APIs.
- Keep `DataFabricPage` behind stronger preview labeling or internal-only operator tooling.
- Keep agent/plugin surfaces read-only but move deeper controls out of Data Cloud shell unless they become actionable.

Inputs to infer / auto-populate:

- Collection choice from recent activity and tenant context
- Retention/action templates from data sensitivity classification
- Workflow source/destination defaults from collection metadata
- Analytics time windows and scope from recent work context

Decisions to automate:

- Recommended query path selection
- Schema and duplicate warnings during entity creation
- Suggested retention tier and redaction field list
- Suggested workflow edge/node validation fixes

Decisions to recommend instead of asking:

- Federated versus direct analytics execution
- Governance remediation sequencing
- Incident grouping and probable next action

Unnecessary review points to remove:

- Manual transitions into separate boundary pages that exist only to say “not available here”

Review/governance points to retain:

- Purge confirmation
- Low-confidence AI-generated workflow drafts
- Expensive analytics explain-plan review
- Privacy-sensitive redactions

AI/ML interventions to add:

- Real query clarification loops
- Entity deduplication/conflict explanations
- Trust Center remediation plans
- Operator incident summaries grounded in alerts/events/context

AI/ML exposure to reduce:

- Reduce visible fallback plumbing in normal paths; keep it in operator diagnostics, not primary-user UX.

Visibility/audit controls to strengthen:

- Proper status codes
- Stronger correlation between action, audit event, and UI-visible result
- Product-wide capability truth that is consistent with route docs and UI boundaries

Operational signals needed to trust the product in production:

- Per-surface health and availability state
- Real error-rate and latency metrics by route family
- AI quality and fallback metrics tied to user outcomes
- Durable storage health and recovery status

## L. Product System Architecture Corrections

Product boundary corrections:

- Keep AEP ownership of execution/control plane explicit and avoid over-signaling those capabilities in Data Cloud UI.
- Data Cloud should present agent and plugin information only where it is actionable inside Data Cloud ownership.

Shared capability consolidation opportunities:

- Consolidate grid/table dependency strategy across `design-system` and `data-grid`.
- Keep canvas/theming utilities reusable, but ensure their packaging is stable enough for downstream products.

Dependency direction fixes:

- Eliminate the `design-system` <-> `data-grid` cycle.
- Re-evaluate `@audio-video/ui` dependency for low-value UI affordances.

Ownership clarification:

- Distinguish “launcher-backed and live”, “launcher-backed but optional”, “read-only external ownership”, and “preview only” in one canonical source that drives docs and UI.

Integration simplification:

- Generate route truth from runtime capability metadata or contract annotations instead of maintaining separate drifting artifacts.

Contract normalization:

- Normalize error handling to standard HTTP semantics with structured JSON bodies.

Data boundary corrections:

- Strengthen tests using actual entity/event providers instead of custom local test stores.

Reuse-first consolidation recommendations:

- Platform Java reuse is broadly correct and should continue.
- Platform TypeScript reuse needs stricter package governance and dependency checks before products depend on new re-exports.

## M. Final Truth Statement

What truly works end to end today:

- Core backend entity/event/governance/alert routes in the launcher are real enough to exercise directly.
- The product has meaningful runbook, durable profile, capability registry, and security-filter infrastructure.
- The simplified primary shell is directionally correct.

What works only partially:

- SQL workspace as an automation-first experience
- Workflow builder as a low-burden AI-first orchestration workflow
- Trust Center as a full governance plane
- Operator console as a single coherent operational experience

What is misleading or overstated:

- Several tests labeled “integration”, “end-to-end”, or “critical path” do not prove the claims they imply.
- The broader UI surface suggests more product completeness than the real action-backed subset supports.

What creates user burden:

- Too many partial/read-only/preview routes
- Heuristic AI assistance that still leaves substantial manual refinement work
- Cross-product ownership seams exposed in the UI

What blocks dead-simple UX:

- Boundary-heavy operator/admin pages
- Inconsistent product truth across docs and UI
- Lack of stronger workflow inference and automation

What blocks pervasive automation:

- AI is mostly assistive instead of deeply operational
- Strong fallback awareness, but weak end-to-end autonomous completion quality

What blocks production trust:

- Broken UI test execution via shared library packaging
- Weak evidence quality in the integration/e2e suite
- API error semantics that hide failures behind HTTP `200`

What blocks governance/privacy/security/visibility maturity:

- Incomplete governance lifecycle
- Weaker than necessary privacy lifecycle proof for memory/context/RAG flows
- Fragmented runtime truth across docs, pages, and boundary registries

What blocks operability and resilience:

- Placeholder failure/recovery tests
- Limited proof of real durable recovery and multi-provider behavior through end-to-end product journeys

What must change for Data Cloud to become truly production-grade, automation-first, AI/ML-pervasive, minimally burdensome, and end-to-end trustworthy:

- Fix shared UI package wiring immediately.
- Replace synthetic evidence with real launcher-plus-provider-plus-browser validation.
- Normalize HTTP error semantics.
- Prune or merge non-actionable surfaces.
- Deepen AI from advisory helpers into safer automatic intent resolution, remediation, and workflow completion.
- Make one canonical runtime-truth source drive docs, shell disclosure, and operator messaging.
