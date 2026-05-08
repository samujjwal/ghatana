# DMOS Implementation Plan

## Purpose

This document defines the self-contained implementation plan for DMOS. It sequences work from product foundation to production readiness while preventing feature sprawl, fake production paths, and incomplete end-to-end flows.

## Implementation Principles

1. Complete vertical slices before expanding breadth.
2. Finish real persistence and authorization before polishing UI.
3. Do not expose features that lack backend/data support.
4. Do not add connector breadth until Google Ads lifecycle is production-safe.
5. Use Kernel for generic platform capabilities only.
6. Add tests with every implementation step.
7. Prefer fix-forward simplification over compatibility with incomplete scaffolding.
8. Every task must have acceptance criteria and required tests.

## Phase 0: Documentation and Product Baseline

### Goals

- Establish self-contained docs `00–11`.
- Align product vision, architecture, features, data model, testing, operations, and implementation plan.
- Remove ambiguity about production-ready versus boundary capabilities.

### Tasks

| Task | Acceptance Criteria | Tests/Checks |
|---|---|---|
| Expand docs `00–11` | Each document is self-contained and linked from README or docs index | Documentation taxonomy check |
| Add feature status matrix | Stable, partial, boundary, blocked states are clear | Manual doc review |
| Align README with docs | README points to canonical docs | Link check |
| Remove outdated root docs or make them redirects | No conflicting architecture docs | Root-doc check |

## Phase 1: Production Foundation

### Goals

- Ensure runtime safety, identity, tenant isolation, persistence, and contract governance.
- Remove or isolate all production mocks, stubs, fake data, and placeholders.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Enforce production identity derivation | Roles/permissions derived server-side | Spoofed header API tests |
| Enforce tenant/workspace scope | All queries and actions scoped | Cross-tenant integration tests |
| Validate production bootstrap | Unsafe config fails startup | Production bootstrap tests |
| Prove PostgreSQL adapter parity | In-memory and PostgreSQL behavior match where applicable | Repository parity tests |
| Validate migrations | Empty DB migrates successfully | Flyway migration test |
| Add static scans | Production cannot import test utilities or stubs | CI static scan |
| Standardize error envelope | All APIs return consistent errors | API negative-path tests |

## Phase 2: Campaign Vertical Slice

### Goals

Deliver a complete campaign lifecycle from UI to API to service to domain to persistence to audit/telemetry.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Campaign list/detail/create | Real data only, scoped by tenant/workspace | UI, API, persistence tests |
| Campaign lifecycle transitions | Draft, review, launch, pause, complete, archive, rollback rules enforced | Domain and API tests |
| Campaign audit events | Every mutation emits audit event | Integration tests |
| Campaign UI states | Loading, empty, error, disabled, unauthorized states | UI tests |
| Campaign E2E | Browser journey create -> launch -> pause -> archive | Playwright E2E |

## Phase 3: Strategy and Budget Workflows

### Goals

Complete strategy generation/review and budget recommendation/approval.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Strategy request model | Objective, audience, constraints, geography, tone, assumptions captured | DTO/API tests |
| Strategy generation service | AI/deterministic output includes provenance | Service tests |
| Strategy approval | Sensitive strategy changes require approval | API/E2E tests |
| Budget recommendation | Recommendation includes rationale, assumptions, thresholds | Unit/service tests |
| Budget approval | High-risk or threshold-exceeding budget requires approval | E2E tests |
| UI review screens | Users can inspect, revise, approve, reject | UI tests |

## Phase 4: Content and Validation Workflows

### Goals

Complete content generation, validation, versioning, and approval.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Ad copy generation | Candidate content has source, prompt summary, model provenance | API/service tests |
| Landing page generation | Structured sections and CTA captured | API/UI tests |
| Email follow-up generation | Sequence, audience, offer, tone captured | API/UI tests |
| Content validation | Brand/policy/compliance findings returned | Unit/service tests |
| Version history | Selected/rejected content versions preserved | Persistence tests |
| Approval gate | High-risk public content requires approval | E2E tests |

## Phase 5: Google Ads Connector Productionization

### Goals

Make Google Ads execution safe, observable, and reversible where possible.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| OAuth/token management | Tokens stored securely and redacted | Security tests |
| Outbox execution | External calls are outbox-driven | Integration tests |
| Idempotency | Duplicate launch does not duplicate external campaign | Connector tests |
| Retry/DLQ | Transient failures retry; exhausted failures go to DLQ | Chaos/retry tests |
| Kill switch | Connector disabled blocks execution | API/UI tests |
| External state reconciliation | External ID and status stored | Integration tests |
| Rollback/compensation | Supported rollback documented and tested | Connector tests |

## Phase 6: AI Transparency and Governance

### Goals

Make AI assistance explainable, auditable, and safe.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| AI action log | Every AI operation persisted with provenance | Service/API tests |
| Redaction | Sensitive inputs/outputs redacted | Privacy tests |
| Model provenance | Provider/model/version/confidence/rationale captured | API/UI tests |
| Policy checks | AI outputs validated before approval/public use | Unit/service tests |
| Explainability UI | Users can inspect why recommendation exists | UI/E2E tests |

## Phase 7: Analytics and Reporting

### Goals

Deliver real reporting only when source data and formulas are correct.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Funnel analytics | Stage definitions, counts, conversion, drop-off from real data | Calculation tests |
| Attribution | Model assumptions explicit and reproducible | Unit tests |
| ROI/ROAS | Spend/revenue formulas documented and tested | Calculation/API tests |
| Data freshness | UI shows last updated/source confidence | UI tests |
| Export/reporting | Reports match displayed values | E2E/export tests |

## Phase 8: Agency, Market Research, Localization, Advanced Channels

### Goals

Expand only after core lifecycle is safe.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Agency operations | Client/workspace boundaries and reporting | Tenant/client tests |
| Market research | Research outputs have source/provenance/confidence | API/UI tests |
| Localization | Locale, language, region, policy constraints modeled | Unit/API tests |
| Advanced channels | New connector added only with same safety model as Google Ads | Connector certification tests |

## Phase 9: Production Readiness

### Goals

Complete release readiness and operational hardening.

### Tasks

| Task | Acceptance Criteria | Required Tests |
|---|---|---|
| Full E2E suite | Critical journeys pass in CI | Browser E2E |
| Security review | Auth, tenant, PII, token, CORS/CSRF risks addressed | Security tests |
| Performance baseline | Dashboard and APIs meet latency budget | Performance tests |
| Observability dashboard | Metrics/traces/logs available for core flows | Operational validation |
| Runbooks | Incidents, connector failures, DSAR, rollback documented | Manual review |
| Release checklist | Production release gate passes | CI gate |

## Priority Rules

- P0: release blockers, security/privacy, fake production paths, broken core flow.
- P1: critical functionality, testing gaps, connector safety, observability gaps.
- P2: important UX, reporting, performance, maintainability.
- P3: polish, optimization, long-tail expansion.

## Definition of Done

A task is done only when:

- Production code is implemented.
- Tests prove behavior.
- Documentation reflects behavior.
- No production mock/stub/fake path remains.
- Observability and audit requirements are met.
- Feature gates are correct.
- Acceptance criteria are satisfied.

## Recovered Roadmap and Epics

## Historical Phase Roadmap

### Phase 1: Foundation

Deliverables:

- Product shell: workspace, organization, users, roles.
- Brand profile: voice, colors, offers, claims.
- Intake: business profile and growth goal capture.
- Market research: competitor and keyword workflow.
- Plan generator: 30/60/90-day strategy.
- Asset generator: landing page, ad copy, email sequence.
- Approval workflow: review, approve, reject, edit.
- Basic analytics: campaign and lead dashboard.
- Consent foundation.

### Phase 2: Execution

Deliverables:

- Google Ads connector first.
- CMS/landing-page publishing.
- Email sending path.
- CRM-lite.
- Proposal engine.
- Contract drafts.
- Full audit log.
- Durable workflows.
- Preflight campaign safety.

### Phase 3: Autonomous Optimization

Deliverables:

- Experiment engine.
- Basic attribution.
- Next-best-action queue.
- Budget pacing.
- AI performance reviews.
- Learning/playbook versioning.

### Phase 4: Platformization

Deliverables:

- Multi-channel expansion.
- External CRM integrations.
- Email automation integrations.
- Agency mode.
- Advanced attribution.
- Industry playbooks.
- Enterprise SSO/data residency/custom policies.

### Phase 5: Ecosystem Expansion

Deliverables:

- Self-marketing scale.
- Advanced AI and custom model training.
- Predictive analytics.
- Voice/video AI.
- Community.
- Public API platform.
- Marketplace.

## Recovered Epics

| Epic | Outcome |
|---|---|
| E1: Customer workspace + brand profile | Store customer, offer, ICP, brand rules |
| E2: AI intake + free audit | Convert visitors into qualified prospects |
| E3: Strategy generator | Create 30-day marketing plan |
| E4: Proposal/SOW generator | Convert plan into commercial offer |
| E5: Landing page + email generator | Create first executable campaign assets |
| E6: Approval workflow | Human review for brand/legal/budget |
| E7: Lead capture + CRM-lite | Track leads from campaign |
| E8: Analytics dashboard | Show results and next actions |
| E9: Consent foundation | Consent proof, unsubscribe, suppression, privacy rights |
| E10: Google Ads integration | OAuth, campaign creation, performance sync |
| E11: Durable workflow execution | Commands, idempotency, retry, DLQ, rollback, kill switch |
| E12: Preflight campaign safety | Tracking, budget, creative, audience, compliance, rollback checks |
| E13: Self-marketing tenant isolation | DMOS markets itself without mixing customer data |

## Implementation Rule

No post-MVP feature may be promoted until the MVP loop proves:

```text
intake -> approved plan -> proposal/SOW -> approved assets -> safe launch/export -> lead captured -> report -> next action
```

with real data, real persistence, real authorization, audit, telemetry, and tests.
