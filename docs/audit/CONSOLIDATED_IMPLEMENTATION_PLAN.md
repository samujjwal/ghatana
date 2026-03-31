# Ghatana Platform: Consolidated Implementation Plan

**Version:** 1.0  
**Date:** March 30, 2026  
**Scope:** All products and platform components — synthesized from V3 Ultra-Strict Audit  
**Owner:** Engineering Leadership

---

## 0. How to Read This Document

This plan consolidates every finding, gap, and recommendation from the full suite of per-product and platform audit reports into a single, prioritized, phase-gated execution roadmap. Each task carries an explicit **priority**, **affected component**, **tech convention alignment**, and **acceptance criteria** so that any engineer can pick up a task and execute it without ambiguity.

**Priority levels:**
- **P0 – Blocker:** Prevents production deployment or poses an active security/compliance risk.
- **P1 – Critical:** Materially degrades correctness, user experience, or maintainability; must be done before the next milestone.
- **P2 – Important:** Needed for production excellence; blocks the 9+/10 target.
- **P3 – Enhancement:** Quality-of-life or future-proofing; scheduled for backlog sprints.

**Convention alignment notation** refers directly to [copilot-instructions.md](../../.github/copilot-instructions.md).

---

## 1. Production Readiness Summary

| Product / Component | Score | Gate | Primary Gap |
|---------------------|-------|------|-------------|
| Shared Services | 8.5/10 | ✅ GO | Minor: drift detection, tracing |
| Platform Libraries | 8.5/10 | ✅ GO | Minor: doc coverage, AFFiNE eval |
| TutorPutor | 7.75/10 | ✅ GO | `any` types, LTI, pagination |
| YAPPC | 7.5/10 | ✅ GO | E2E tests, AFFiNE eval |
| Data Cloud | 7.0/10 | 🟡 CONDITIONAL GO | State, Monaco, AI UI |
| AEP | 6.5/10 | 🟡 CONDITIONAL GO | Contract dup, SSE mismatch |
| Flashit | 5.5/10 | 🔴 NO-GO | Security blockers, billing |
| Audio-Video | TBD | 🔍 AUDIT NEEDED | Full code review required |
| DCMAAR | TBD | 🔍 AUDIT NEEDED | 2,898 items not yet reviewed |
| Software Org | TBD | 🔍 AUDIT NEEDED | 1,653 items not yet reviewed |
| Aura | N/A | 🏗️ NOT STARTED | 6-month implementation |
| Finance | TBD | ⚠️ AUDIT NEEDED | Financial + compliance logic |
| PHR | TBD | ⚠️ AUDIT NEEDED | HIPAA compliance mandatory |
| Virtual Org | TBD | 🔍 AUDIT NEEDED | 411 items not yet reviewed |
| Security Gateway | TBD | ⚠️ CRITICAL AUDIT | Foundational infra — blocks all |

---

## 2. Phase 0 — Security and Compliance Blockers (Week 1–2)

> These items must be resolved before any other release gating. They carry legal, financial, or cross-cutting security risk.

### 2.1 Flashit — P0 Security Fixes

**Why first:** Active production deployment with security vulnerabilities is unacceptable per Rule 5 of copilot-instructions.md.

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| F-01 | Replace stub email service with real transactional email integration (Postmark/SES). Wire through `shared-services` pattern — no ad-hoc SMTP logic in product code. | Shared-service boundary; no platform leakage | Email sent, delivery confirmation logged, test covers failure path |
| F-02 | Remove all hardcoded user IDs. Replace with `TenantContext`-scoped identity resolved from JWT claims. Applies to all 15 service sub-modules. | Rule 5: no unsafe defaults; validate at boundary | Zero hardcoded UUIDs in source; integration test validates tenant isolation |
| F-03 | Complete Stripe billing integration end-to-end: webhook receipt → idempotent processing → subscription state machine → feature gate enforcement. Use `platform:java:http` for webhook endpoint. | ActiveJ Promise model; no blocking; idempotent handler | Subscription created, updated, and cancelled flows covered by integration tests |
| F-04 | Add TOTP-based 2FA to auth flow. Reuse `shared-services/auth-gateway` 2FA capability — do not re-implement. | Reuse before creating (Rule 1) | 2FA enroll + verify flows pass; brute-force protection verified |
| F-05 | Consolidate 15 Flashit micro-services into a coherent domain cluster: Capture, Processing, Organization, Search, Billing. Keep inter-domain boundaries via explicit service interfaces, not shared database tables. | Keep boundaries explicit (Rule 3) | Service count ≤ 6; no cross-domain direct DB access; contract tests for inter-service calls |

### 2.2 Security Gateway — P0 Full Audit

**Why first:** Security Gateway is load-bearing infrastructure. A gap here affects every product.

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| SG-01 | Conduct full source-code audit: JWT validation logic, token rotation, mTLS setup, OAuth2/OIDC integration. Document each finding in `docs/audit/platform/SECURITY_GATEWAY_FULL_AUDIT.md`. | Zero-warning mindset | Audit report complete; each finding has P0/P1/P2 label |
| SG-02 | Validate WAF rules against OWASP Top 10. Confirm SQL injection, XSS, path traversal protections are active and tested. | OWASP Top 10 per security requirements | Penetration test results show 0 OWASP Top 10 vector open |
| SG-03 | Validate rate-limiting configuration per tenant. Token bucket parameters must be documented and env-configurable (no hardcoded values). | Rule 5: no unsafe defaults | Rate limit enforced in integration test; config via environment |
| SG-04 | Audit DDoS and IP-blocking policies. Confirm these are tested under simulated load. | Observability: structured logs for threat events | Load test evidence in `docs/audit/` |
| SG-05 | Confirm all security events (auth failures, policy violations, rate limit trips) emit structured logs with `correlationId`. | Rule 10: observability as part of the feature | Logs appear in Loki under `security.*` labels with correlation IDs |

### 2.3 Finance — P0 Compliance Audit

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| FIN-01 | Audit double-entry accounting logic. Verify no transactions produce unbalanced ledger state. | No silent failures (Rule 4) | Property-based test: every transaction leaves ledger balanced |
| FIN-02 | Confirm immutable audit trail: all financial mutations logged with actor, timestamp, before/after state. | Observability (Rule 10) | Audit log survives service restart; passes compliance review |
| FIN-03 | Validate data retention and encryption-at-rest policies against regulatory requirements (SOX, local equivalents). | Rule 5 | Encryption verified; retention policy documented and enforced |

### 2.4 PHR — P0 HIPAA Compliance

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| PHR-01 | Audit PHI (Protected Health Information) encryption: field-level encryption at rest + TLS in transit. Verify key management. | Rule 5: no unsafe defaults | Encryption integration test; key rotation documented |
| PHR-02 | Verify all PHI access is RBAC-gated and every access event is logged in immutable audit log. | Rule 4: no silent failures | Zero unlogged PHI reads in test harness |
| PHR-03 | Validate HL7 FHIR contract correctness. Add contract tests for each integration point. | Contract tests for API boundaries | FHIR schema validation passing in CI |
| PHR-04 | Define and test breach notification workflow. | Rule 4 | Notification workflow has unit + integration tests |

---

## 3. Phase 1 — Conditional GO Unblocking (Weeks 3–6)

> These tasks move AEP and Data Cloud from CONDITIONAL GO to GO and must be complete before their respective general availability milestones.

### 3.1 AEP — From 6.5/10 to 8.5/10

#### 3.1.1 Contract and API Topology (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| AEP-01 | Deduplicate OpenAPI spec: single source in `platform/contracts/aep/openapi.yaml`. Delete the copy in `launcher/resources/`. Update CI to validate spec from contracts only. | Single source of truth; contracts stay in `platform/contracts` | `grep -r openapi.yaml products/aep/launcher` returns empty; broken build if contracts drift |
| AEP-02 | Replace handwritten TypeScript API clients with generated code from the canonical OpenAPI spec. Add generation step to Turborepo pipeline. | Prefer existing dependencies; no duplicate implementations | Generated client in `@ghatana/api`; zero handwritten fetch calls for AEP APIs |
| AEP-03 | Resolve API topology: confirm gateway vs. launcher routing in a single `TOPOLOGY.md` doc. Enforce via ArchUnit test that UI never calls launcher directly (must go via BFF or gateway). | Keep boundaries explicit | ArchUnit boundary test passes in CI |

#### 3.1.2 SSE and Real-Time Correctness (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| AEP-04 | Fix SSE path mismatch: align UI expectation (`/events/stream`) with BFF implementation. Verify port alignment (UI:3000, BFF:3002, Launcher:8090). Add integration test that drives the full SSE path end-to-end. | No silent failures | Integration test: pipeline status update delivered to UI via SSE within 2s |
| AEP-05 | Deduplicate SSE cache-update logic shared between `usePipelineRuns` and `useHitlQueue`. Extract shared `useSSESubscription` hook into `@ghatana/realtime`. | Reuse before creating | Single hook; both consumers import from `@ghatana/realtime` |
| AEP-06 | Fix HITL event naming: align `hitl.new` (UI) → `hitl_request_created` (backend). Document canonical event schema in `platform/contracts`. | Explicit contracts | Contract test covering event name; schema in `platform/contracts/aep/events/` |

#### 3.1.3 Feature Completeness (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| AEP-07 | Implement pipeline versioning: draft → named version → rollback. Persist version snapshots in PostgreSQL. Expose version list in UI pipeline detail. | ActiveJ Promise; no blocking I/O | Version CRUD APIs + UI controls; undo-to-version integration test |
| AEP-08 | Implement HITL auto-escalation: configurable timeout + escalation policy. Fire `hitl_escalated` event to SSE stream. | Event-driven; explicit contracts | Escalation fires after timeout in integration test; email/notification surfaced |
| AEP-09 | Reduce `platform/` monolith: identify and extract product-agnostic utilities that belong in `platform/java/*` vs. product-specific code that belongs in `products/aep/`. Target: ≤ 400 files in `platform/` AEP-owned area. | No platform dumping ground | Module boundary map committed; ArchUnit test enforces it |

### 3.2 Data Cloud — From 7.0/10 to 9.0/10

#### 3.2.1 State Management (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| DC-01 | Remove Zustand from Data Cloud UI. Migrate all shared state to Jotai atoms following the `StateManager` pattern used across the platform. | Rule 2: follow existing platform state pattern (Jotai) | Zero Zustand imports in Data Cloud; `grep -r zustand products/data-cloud/` is empty |
| DC-02 | Replace all manual `fetch` + `useEffect` patterns with TanStack Query (`useQuery`/`useMutation`). Applies to collection CRUD, pipeline status, and governance APIs. | Server state → TanStack Query (Section 6 of copilot-instructions.md) | Zero bare `useEffect` data-fetching in Data Cloud UI; loading/error states handled uniformly |
| DC-03 | Replace mock/stub data with real API calls. Add `msw` handlers for tests — keep mocks in tests only, never in production paths. | No silent failures; validate at boundaries | Production build contains zero `MOCK_` prefixed constants |

#### 3.2.2 UI and Component Quality (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| DC-04 | Replace local `BaseCard`, `StatusBadge`, and `LoadingState` components with canonical equivalents from `@ghatana/ui`. Delete local copies. | Reuse before creating | Zero `local/components/BaseCard` or equivalent local re-implementations |
| DC-05 | Migrate all hardcoded hex colors to `@ghatana/theme` CSS variables. No hardcoded colors in any `.tsx` or `.css` file. | Design tokens (Section 6) | `grep -rn '#[0-9a-fA-F]{6}' products/data-cloud/` returns zero results in component files |
| DC-06 | Add `@doc.*` JSDoc tags to all public TypeScript classes/functions missing them. Bring coverage to ≥ 90% in public-API surface. | Rule 8: public APIs require documentation | Doc coverage check in CI passes at 90% threshold |

#### 3.2.3 Missing Features (P1)

| # | Task | Convention | Acceptance Criteria |
|---|------|-----------|---------------------|
| DC-07 | Integrate Monaco SQL editor using `@yappc/code-editor` (already exists — reuse, do not create new). Wire to query execution API with TanStack Query mutation. | Reuse `@yappc/code-editor` | SQL editor functional; syntax highlighting for ANSI SQL; query executes and results render |
| DC-08 | Build AI Assistant UI: chat interface backed by `shared-services/ai-inference`. State managed with Jotai; streaming responses via SSE using `@ghatana/realtime`. | Reuse shared AI services; `@ghatana/realtime` for streaming | Chat sends message, streams response, renders markdown; error state handled |
| DC-09 | Complete governance UI: data lineage visualization (`@yappc/canvas`), policy management screens, audit log viewer. | Reuse `@yappc/canvas` for lineage | All three governance screens render with real API data |
| DC-10 | Add in-app notification system for pipeline completion and governance alerts. Use existing `@ghatana/realtime` WebSocket/SSE infrastructure. | Reuse `@ghatana/realtime` | Notification renders within 1s of backend event; persists across navigation |

---

## 4. Phase 2 — Go Products: Excellence Work (Weeks 5–10)

> These tasks apply to products already at GO status. They close the gap to 9+/10, reduce technical debt, and unblock longer-term roadmap features.

### 4.1 YAPPC — From 7.5/10 to 9.0/10

| # | Task | Priority | Convention | Acceptance Criteria |
|---|------|----------|-----------|---------------------|
| Y-01 | Complete E2E test suite (Playwright). Cover critical paths: Canvas → Save → Preview → Deploy; Architecture → Validate → Generate; DevSecOps scan → Report. | P1 | Playwright; Section 16 test placement | 3 critical user journeys covered; CI blocks on failure |
| Y-02 | Evaluate AFFiNE/BlockSuite for canvas block model. Document decision as an ADR in `docs/adr/`. If adopted, integrate via `@yappc/canvas` — no direct AFFiNE imports in app code. | P2 | ADR process; boundary via `@yappc/canvas` | ADR committed; if adopted, `@yappc/canvas` wraps BlockSuite |
| Y-03 | Bundle size monitoring: add Turborepo build step that fails if any entry chunk exceeds 500 kB uncompressed. Report per-chunk sizes in CI output. | P2 | Zero-warning mindset | CI bundle report present; threshold enforced |
| Y-04 | Verify all 22 Java test files extend `EventloopTestBase` and use `runPromise`. Add CI check that fails on any `.java` test that does not extend the base. | P1 | Rule from Section 4: ActiveJ async tests | CI enforces base class; zero violations |

### 4.2 TutorPutor — From 7.75/10 to 10/10 Excellence

| # | Task | Priority | Convention | Acceptance Criteria |
|---|------|----------|-----------|---------------------|
| T-01 | Eliminate 1,177 `any` TypeScript type usages. Use `unknown` at boundaries; introduce Zod schemas for all external API responses. Stricter: zero `any` in domain logic. | P1 | Strict TypeScript; `unknown` over `any` (Section 5) | `tsc --strict` passes; `any` count ≤ 20 (boundary adapters only) |
| T-02 | Complete LTI 1.3 signature validation. Use `platform:java:security` JWT utilities — do not re-implement RS256 validation. Add integration test with a mock LTI Tool Consumer. | P0 | Reuse `platform:java:security` | LTI launch validates signature; invalid token returns 401; test covers replay attack |
| T-03 | Consolidate pagination helpers from 3 service duplicates into a single `platform/java/core` utility (`PaginationUtils`). Replace all usages. | P2 | Naming: suffix `Utils`; place in `platform:java:core:util` | Zero duplicate pagination impls; all consumers import from platform |
| T-04 | Add Bayesian Knowledge Tracing module for mastery estimation. Implement as `PROBABILISTIC` agent extending `AbstractTypedAgent<LearnerInteraction, MasteryEstimate>`. | P3 | Agent type taxonomy (Section 18) | Unit tests with known BKT params verify expected mastery; `@doc.*` tags present |
| T-05 | Add engagement monitoring (emotional state detection). Follow `ADAPTIVE` agent pattern from Section 18. Implement as background observability signal, not blocking UI. | P3 | `ADAPTIVE` agent; observability signal | Async; non-blocking; emits metric; covered by unit test |

### 4.3 Shared Services — From 8.5/10 to 9.5/10

| # | Task | Priority | Convention | Acceptance Criteria |
|---|------|----------|-----------|---------------------|
| SS-01 | Implement advanced feature drift detection for Feature Store. Add statistical drift metric (PSI or KS test) per feature, emitting `feature.drift_detected` event. | P2 | Observability; event-driven | Drift metric emitted; alert fires when threshold exceeded; covered by unit test |
| SS-02 | Complete cross-service distributed tracing. Ensure `correlationId` propagated through AI Inference → Auth Gateway → Feature Store chain. Verify in Jaeger. | P1 | Rule 10: observability; correlation IDs | Full trace visible in Jaeger for AI inference call; no broken spans |
| SS-03 | Istio service mesh optimization: enable mTLS in STRICT mode between shared services. Document policy as code in `monitoring/` Helm values. | P2 | Infrastructure as code; `monitoring/` convention | `kubectl get peerauthentication` shows STRICT; mTLS verified by test traffic |

### 4.4 Platform Libraries — Maintenance

| # | Task | Priority | Convention | Acceptance Criteria |
|---|------|----------|-----------|---------------------|
| PL-01 | Raise `@doc.*` JavaDoc tag coverage to 100% for all public Java platform classes in `platform/java/*`. Add CI check that fails on missing tags. | P1 | Rule 8 | CI Javadoc check passes; zero missing `@doc.*` tags |
| PL-02 | Raise TypeScript public-API doc coverage (`@ghatana/ui`, `@yappc/canvas`, `@ghatana/realtime`) to ≥ 95%. Use TypeDoc in CI. | P2 | Rule 8 | TypeDoc report shows ≥ 95% documented symbols |
| PL-03 | Evaluate AFFiNE/BlockSuite for `@yappc/canvas`. Decision required within 4 weeks. Publish ADR. If not adopted, close the open loop in YAPPC audit. | P2 | ADR process; reuse existing infra | ADR signed off by engineering lead |
| PL-04 | Remove remaining `any` types from `@ghatana/ui` and `@yappc/canvas`. Replace with `unknown` + type guards at boundaries. | P1 | Strict TypeScript (Section 5) | `tsc --strict` on platform/typescript passes with zero `any` in public APIs |

---

## 5. Phase 3 — Audit-Required Products (Weeks 6–10, Parallel Track)

> These products need full audits before any implementation plan can be finalized. Run the audit track in parallel with Phase 1–2 implementation.

### 5.1 Audio-Video — Full Audit

| # | Task | Owner Area | Deliverable |
|---|------|-----------|-------------|
| AV-01 | Map full tech stack: Rust crates, Kotlin modules, frontend apps. Document in `docs/audit/products/AUDIO_VIDEO_FULL_AUDIT.md`. | DevEx | Confirmed stack map |
| AV-02 | Audit transcoding pipeline: correctness, resource cleanup, error propagation, format support matrix. | Platform Eng | Findings with P0/P1/P2 labels |
| AV-03 | Audit streaming: RTMP/WebRTC session lifecycle, reconnection, backpressure handling. | Backend | Streaming correctness findings |
| AV-04 | Audit storage tiers: multi-tier write path correctness; data loss scenarios under failure. | Backend | Storage fault-tolerance findings |
| AV-05 | Rust-specific: `unsafe` blocks documented and justified; `clippy` clean with no `allow` suppressions without comment. | Rust | `cargo clippy` output clean |
| AV-06 | Define implementation plan tasks (following Phase 0–2 pattern) and add to this document as an addendum. | Eng Lead | AV implementation tasks committed |

### 5.2 DCMAAR — Full Audit

| # | Task | Owner Area | Deliverable |
|---|------|-----------|-------------|
| DC-M-01 | Audit rights management logic: license expiry calculations, territory restrictions, usage accounting. | Domain Eng | Logic correctness findings |
| DC-M-02 | Audit content ingestion pipeline: metadata extraction, approval workflow state machine, publishing automation. | Backend | Pipeline findings |
| DC-M-03 | Map integration contracts with distribution channels, payment systems, analytics platforms. Add contract tests. | Platform | Contract test coverage |
| DC-M-04 | Full source audit of 2,898-item codebase. Prioritize security, data integrity, and licensing logic first. | Engineering | Audit report with prioritized tasks |

### 5.3 Software Org — Full Audit

| # | Task | Owner Area | Deliverable |
|---|------|-----------|-------------|
| SO-01 | Map workflow engine implementation. Verify state transitions are explicit and tested. | Backend | State machine correctness findings |
| SO-02 | Audit YAPPC ↔ Software Org ↔ AEP integration contracts. Ensure no direct cross-product DB access. | Platform | Boundary audit report |
| SO-03 | Confirm analytics pipeline (metrics, velocity, quality) feeds from real data, not sampling. | Data | Data pipeline correctness |

### 5.4 Virtual Org — Full Audit

| # | Task | Owner Area | Deliverable |
|---|------|-----------|-------------|
| VO-01 | Audit hierarchical org model: parent–child correctness, circular reference guards, bulk re-org operations. | Backend | Logic findings |
| VO-02 | Confirm Software Org ↔ Virtual Org sync is event-driven (not scheduled polling). | Platform | Integration pattern findings |

---

## 6. Phase 4 — New Product Kickoff: Aura (Month 2–8)

Aura has excellent design specifications (9/10 design score) but zero implementation. This phase bootstraps it correctly against Ghatana conventions.

| # | Task | Priority | Convention | Acceptance Criteria |
|---|------|----------|-----------|---------------------|
| AU-01 | Create Gradle multi-module structure under `products/aura/` following the same cluster topology as YAPPC (5 domain clusters). | P0 (kickoff gate) | Section 21 product conventions | `./gradlew :products:aura:build` succeeds with empty modules |
| AU-02 | Implement `PersonalIntelligenceAgent` as `ADAPTIVE` agent (`AbstractTypedAgent<UserInteraction, PreferenceUpdate>`). Register in AEP Central Registry. | P0 | Agent taxonomy Section 18; AEP registry | Agent executes in test; `@doc.*` tags present |
| AU-03 | Create TypeScript workspace under `products/aura/web/` with `pnpm`, strict TypeScript, Jotai, TanStack Query. Mirror pattern from Data Cloud web app. | P0 | Section 5–6 TS standards | `pnpm -F @aura/web build` succeeds |
| AU-04 | Implement knowledge graph data model (Neo4j). Define entity types and relationship schemas in `platform/contracts/aura/`. | P1 | Contracts in `platform/contracts/` | Contract schema validated; Neo4j schema migration script present |
| AU-05 | Implement recommendation engine as `COMPOSITE` agent: deterministic fast-path + probabilistic LLM fallback. | P1 | Agent type taxonomy | Hybrid agent covered by unit + integration tests |
| AU-06 | Style/Shade Ontology: model as typed Kotlin/Java domain objects. No stringly-typed shade values anywhere in service code. | P1 | Strict typing; immutable records | Zero `String shade` in domain layer; `ShadeOntology` record used |

---

## 7. Cross-Cutting Implementation Standards

These apply to every task above, across every product. They are derived directly from `copilot-instructions.md` and must be enforced in code review.

### 7.1 Backend (Java / ActiveJ)

- **No blocking on event loop.** All I/O wrapped in `Promise.ofBlocking(...)` or native async APIs.
- **Constructor injection only.** Field injection fails lint check.
- **Async tests extend `EventloopTestBase`.** Use `runPromise(() -> ...)`. No `.getResult()` calls.
- **All public classes have `@doc.*` tags** (`@doc.type`, `@doc.purpose`, `@doc.layer`, `@doc.pattern`).
- **No hardcoded credentials or configuration.** All secrets from environment / config service.

### 7.2 Frontend (TypeScript / React)

- **Zero `any` in domain logic.** Use `unknown` + Zod at external boundaries.
- **Server state via TanStack Query exclusively.** No bare `useEffect` data fetching.
- **App state via Jotai.** No Zustand, Redux, or Context for app-level state.
- **Components from `@ghatana/ui` first.** Local components only for product-specific widgets with no equivalent in the design system.
- **No hardcoded hex colors.** All colors from `@ghatana/theme` CSS variables.
- **Test files in `__tests__/` co-located with source.** Naming: `Component.test.tsx`.

### 7.3 Contracts and Boundaries

- **OpenAPI specs live in `platform/contracts/`** — one canonical file per product.
- **Event schemas versioned in `platform/contracts/{product}/events/`.**
- **Consumers are idempotent** for all event-driven integrations.
- **Cross-product calls go through declared service interfaces,** never direct DB access.
- **ArchUnit boundary tests** in every Java module that has cross-module dependencies.

### 7.4 Observability

- **Every error is logged with `correlationId`** and appropriate structured fields.
- **Metrics endpoint** (`/metrics`, Prometheus format) required for every deployable service.
- **Distributed traces** must not have broken spans for any critical path.
- **Health semantics correct** — `/health/ready` reflects actual dependency readiness, not just process alive.

### 7.5 Security

- **Input validated at every service boundary** using Jakarta Validation or Zod.
- **Tenant isolation enforced in repository layer** via `TenantContext`.
- **No OWASP Top 10 vectors left open** in any production surface.
- **Rate limiting** on all public API endpoints.

---

## 8. CI/CD Enforcement Checklist

Add these automated checks to the relevant CI pipelines. None of these should require human code-review to enforce.

| Check | Scope | Fails On |
|-------|-------|----------|
| `@doc.*` Javadoc coverage | All `platform/java/*` | Any public class missing `@doc.type/purpose/layer/pattern` |
| `tsc --strict` | All TypeScript workspaces | Any `any` in non-boundary code |
| ArchUnit boundary tests | All Java multi-module products | Cross-layer dependency violation |
| EventloopTestBase enforcement | All `*Test.java` in ActiveJ modules | Test not extending base |
| Bundle size check | All TS web apps | Entry chunk > 500 kB |
| OpenAPI contract drift | All products with OpenAPI specs | Generated client diverges from spec |
| Hardcoded credential scan | All source | `password =`, API key literals in code |
| OWASP dependency check | All `build.gradle.kts` | CVE above CVSS 7.0 without suppression |

---

## 9. Milestone Timeline

| Milestone | Target Week | Exit Criteria |
|-----------|-------------|---------------|
| M0 — Security/Compliance Unblocked | Week 2 | Flashit P0s fixed; Security Gateway audit complete; Finance + PHR P0 findings documented |
| M1 — AEP GO | Week 5 | All AEP-01 → AEP-09 tasks done; CI green; score ≥ 8.0/10 |
| M2 — Data Cloud GO | Week 6 | All DC-01 → DC-10 tasks done; state management clean; Monaco + AI UI live |
| M3 — Excellence Wave 1 | Week 8 | YAPPC: E2E tests done; TutorPutor: 0 `any` + LTI fixed; Shared Services: tracing complete |
| M4 — Audit-Required Products Resolved | Week 10 | Audio-Video, DCMAAR, Software Org, Virtual Org full audits filed; implementation tasks created |
| M5 — Aura Kickoff | Week 8 | AU-01 → AU-03 complete; first agentic flow running in dev environment |
| M6 — Platform Excellence | Week 12 | All CI/CD checks green; `@doc.*` 100% on platform Java; zero `any` on platform TypeScript |

---

## 10. Task Reference Index

Quick lookup by product:

| Product | Task IDs |
|---------|----------|
| AEP | AEP-01 to AEP-09 |
| Audio-Video | AV-01 to AV-06 |
| Aura | AU-01 to AU-06 |
| Data Cloud | DC-01 to DC-10 |
| DCMAAR | DC-M-01 to DC-M-04 |
| Finance | FIN-01 to FIN-03 |
| Flashit | F-01 to F-05 |
| PHR | PHR-01 to PHR-04 |
| Platform Libraries | PL-01 to PL-04 |
| Security Gateway | SG-01 to SG-05 |
| Shared Services | SS-01 to SS-03 |
| Software Org | SO-01 to SO-03 |
| TutorPutor | T-01 to T-05 |
| Virtual Org | VO-01 to VO-02 |
| YAPPC | Y-01 to Y-04 |

---

**Document Version:** 1.0  
**Last Updated:** March 30, 2026  
**Source Audits:** AEP, Data Cloud, YAPPC, TutorPutor, Flashit, Audio-Video, DCMAAR, Software Org, Virtual Org, Aura, Finance, PHR, Security Gateway, Shared Services, Platform Libraries — all V3 Ultra-Strict Audits
