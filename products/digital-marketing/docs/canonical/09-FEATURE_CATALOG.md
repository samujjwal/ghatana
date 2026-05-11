# DMOS Feature Catalog

## Purpose

This document is the self-contained inventory of DMOS features, their intended outcomes, implementation status, dependencies, and required validation.

## Status Legend

| Status | Meaning |
|---|---|
| Ready | Implemented, tested, operationalized, and passing release gates |
| Partial | Implemented but missing critical completeness, tests, or production hardening |
| Boundary | Product scope defined, but must remain feature-gated until implemented with real data/services |
| Dev/Test only | Useful for local/test workflows but blocked from production |
| Not verified | Implementation may exist, but current production proof has not been run |

## Feature Inventory

| Area | Feature | Status | Primary Dependencies | Required Proof |
|---|---|---|---|---|
| Workspace | Login and workspace entry | Partial | Production identity provider | Prod auth tests, dev login gated |
| Workspace | Dashboard command center | Partial | Campaigns, approvals, AI actions, connectors | Real data UI/API/E2E |
| Workspace | Capability-driven routes | Partial | Workspace capabilities API | Backend and UI flag tests |
| Campaigns | Campaign list | Partial | Persistence, tenant filters | API/UI/persistence tests |
| Campaigns | Campaign create | Partial | Validation, persistence, audit | E2E create flow |
| Campaigns | Campaign detail | Partial | Aggregated campaign data | UI/API consistency tests |
| Campaigns | Launch/pause/complete/archive | Partial | Lifecycle rules, connector, approvals | Domain/API/E2E tests |
| Campaigns | Rollback | Partial | Connector compensation | Connector rollback tests |
| Strategy | Strategy generation | Partial | AI provider/rule engine, provenance | Service/API/UI tests |
| Strategy | Strategy review/revision | Partial | Versioning, UI editor | UI/E2E tests |
| Strategy | Strategy approval | Partial | Approval plugin | Approval E2E |
| Budget | Budget recommendation | Partial | Recommendation service, assumptions | Unit/service/API tests |
| Budget | Budget approval | Partial | Approval thresholds | E2E approval |
| Budget | Spend tracking | Boundary | Connector performance data | Data freshness tests |
| Content | Ad copy generation | Partial | AI provider, validation | API/UI/provenance tests |
| Content | Landing-page generation | Partial | AI provider, content model | API/UI tests |
| Content | Email follow-up generation | Partial | AI provider, sequence model | API/UI tests |
| Content | Content validation | Partial | Policy packs | Validation tests |
| Approvals | Approval queue | Partial | Approval service, permission model | Role matrix tests |
| Approvals | Approval detail | Partial | Snapshot and risk metadata | UI/API tests |
| Approvals | Approve/reject | Partial | Immutable decision model | Conflict/audit tests |
| AI | AI action log | Partial | Persistence, redaction | Redaction/UI/API tests |
| AI | Model provenance | Partial | Provider metadata | API/UI tests |
| AI | Next-best action | Partial | Analytics and campaign state | Recommendation tests |
| Connector | Google Ads OAuth/status | Partial | OAuth config, token handling | Security/connector tests |
| Connector | Google Ads campaign execution | Partial | Outbox, idempotency, external API | Connector E2E/sandbox |
| Connector | Retry/DLQ | Partial | Queue/outbox infrastructure | Chaos tests |
| Connector | Kill switch | Partial | Feature flag/capability service | API/UI tests |
| Reporting | ROI/ROAS | Boundary | Spend/revenue/performance data | Formula and UI tests |
| Reporting | Funnel analytics | Boundary | Event/funnel data model | Calculation tests |
| Reporting | Attribution | Boundary | Touchpoint model | Model tests |
| Market | Market research | Boundary | Research providers, provenance | Source/provenance tests |
| Agency | Multi-client workspace | Boundary | Client/account data model | Tenant/client tests |
| Localization | Multi-language campaigns | Boundary | Locale model, compliance rules | i18n/policy tests |
| Operations | Observability | Partial | OTel/logging/metrics | Trace/metric validation |
| Operations | Retention/DSAR | Partial | PII model, retention jobs | Privacy tests |

## Feature Lifecycle

Every feature moves through:

1. **Defined:** Outcome, persona, requirements, and acceptance criteria documented.
2. **Designed:** UI, API, service, data, security, and observability design completed.
3. **Implemented:** Production code exists without fake paths.
4. **Tested:** Required test tiers pass.
5. **Operationalized:** Monitoring, alerts, runbooks, and rollback behavior exist.
6. **Released:** Feature flag enabled for intended tenants.
7. **Measured:** Usage, quality, and outcome metrics reviewed.

## Ready Feature Requirements

A feature can be marked `Ready` only when:

- It is backed by real backend services.
- It has durable production persistence if stateful.
- It has API contract coverage.
- It has UI coverage for all states.
- It enforces backend authorization and capability checks.
- It emits audit and telemetry where needed.
- It has meaningful tests.
- It has operational ownership.

## Boundary Feature Rules

Boundary features may appear in docs and route manifests, but production UI must not imply they are functional unless complete. Boundary features must:

- Be feature-gated.
- Render unavailable state when disabled.
- Avoid fake metrics, fake charts, or fake success responses.
- Have clear dependency list.
- Have a promotion plan to partial/stable.

## Feature Dependencies

### Campaigns depend on

- Workspace context.
- Tenant isolation.
- Persistence.
- Authorization.
- Audit.
- Telemetry.
- Strategy/budget/content readiness for launch.
- Connector status for external execution.

### Strategy depends on

- Input brief.
- AI/model provider or deterministic engine.
- Provenance.
- Approval workflow.
- Versioning.

### Budget depends on

- Campaign objective.
- Spend constraints.
- Recommendation assumptions.
- Approval thresholds.
- Reporting/performance data when available.

### Connectors depend on

- OAuth/token management.
- Outbox.
- Idempotency.
- Retry/DLQ.
- Kill switch.
- Audit and telemetry.
- External provider contract tests.

### Reporting depends on

- Real source data.
- Formula definitions.
- Freshness metadata.
- Tenant/workspace filters.
- Export parity.

## Required Tests by Feature Category

| Category | Required Tests |
|---|---|
| Campaigns | Domain lifecycle, API validation, persistence, UI states, E2E |
| Strategy | Input validation, AI provenance, approval, UI revision |
| Budget | Formula/assumption tests, approval threshold tests |
| Content | Generation, validation, versioning, redaction |
| Approvals | Role matrix, conflict, immutable audit |
| AI | Provenance, redaction, linked entity, UI search |
| Connectors | OAuth, idempotency, retry, DLQ, kill switch, sandbox |
| Reports | Calculation, source freshness, export parity |
| Agency | Tenant/client isolation |
| Operations | Bootstrap, telemetry, rate limits, retention |

## Feature Catalog Maintenance

This catalog must be updated when:

- A route is added or removed.
- A capability is added or renamed.
- A feature moves status.
- A feature becomes production-ready.
- A production stub is discovered.
- A new connector or report is added.
- Tests or operational requirements change.

## Recovered Feature Families

| Area | Feature | Status | Notes |
|---|---|---|---|
| Self-marketing | Internal DMOS growth funnel | Boundary | Must run in isolated internal tenant |
| Self-marketing | Free audit funnel | Boundary | Converts visitors into qualified prospects |
| Intake | Business profile and growth goal capture | Boundary | Feeds audit/strategy/proposal |
| Audit | Website and competitor scan | Boundary | Must include source/confidence |
| Sales | Proposal generator | Boundary | Template + strategy driven |
| Sales | SOW/MSA draft generator | Boundary | Not legal advice; human-approved |
| CRM-lite | Lead capture | Boundary | PII-safe contact model required |
| CRM-lite | Lead status pipeline | Boundary | Captured -> qualified -> opportunity -> won/lost |
| Execution | Durable workflow engine | Partial | Required before broad external execution |
| Execution | Command records | Partial | Idempotent and auditable |
| Execution | Rollback/compensation plans | Partial | Required for external-impact actions |
| Execution | Kill switches | Partial | Campaign/workspace/tenant/global |
| Governance | Consent proof | Boundary | Required for marketing/contact data |
| Governance | Suppression lists | Boundary | Required for email/SMS/audience sync |
| Governance | Claim evidence | Boundary | Required for claim-sensitive content |
| Learning | Playbook versioning | Boundary | Experiment results promote into playbooks |
| Experimentation | A/B tests | Boundary | Hypothesis, sample size, winner criteria |
| Attribution | Touchpoint model | Boundary | Last-click MVP; multi-touch later |
| Agency | White-label reports | Boundary | Requires multi-client model |
| Enterprise | SSO/data residency/custom policies | Boundary | Post-MVP |

## Feature Promotion Checklist

Before any boundary feature becomes `Ready`:

- Product requirement exists.
- Design is complete.
- API contract exists.
- Data model exists.
- UI states exist.
- Backend authorization exists.
- Production adapter exists or unavailable state is explicit.
- Tests cover happy path and failure paths.
- Audit/telemetry exists.
- Operations/runbook exists.
