# DMOS Product Requirements

## Purpose

This document defines the self-contained product requirements for DMOS. It translates the vision into functional and non-functional requirements that implementation, testing, and release decisions must follow.

## Requirement Status Terms

| Status | Meaning |
|---|---|
| Required | Must exist for production readiness |
| Boundary | Planned capability that must remain feature-gated until complete |
| Optional | Useful but not required for first production pass |
| Blocked | Cannot ship until dependency or safety requirement is met |

## Functional Requirements

## FR-1 Workspace and Tenancy

| ID | Requirement | Priority |
|---|---|---|
| FR-1.1 | Every operation must be scoped to tenant and workspace | Required |
| FR-1.2 | Workspace dashboard must summarize campaigns, approvals, risk, AI actions, and connector status | Required |
| FR-1.3 | UI route availability must come from backend capabilities | Required |
| FR-1.4 | Client/account scoping must be supported for agency operations | Boundary |
| FR-1.5 | Cross-tenant and cross-workspace data leakage must be impossible through API or UI | Required |

## FR-2 Campaign Management

| ID | Requirement | Priority |
|---|---|---|
| FR-2.1 | User can create a campaign draft with objective, audience, dates, budget, and channels | Required |
| FR-2.2 | User can list and view campaigns scoped to workspace | Required |
| FR-2.3 | Campaign lifecycle supports draft, review, approved, launched, paused, completed, archived, failed, rollback states as applicable | Required |
| FR-2.4 | Invalid lifecycle transitions must be rejected | Required |
| FR-2.5 | Mutating actions must be audited | Required |
| FR-2.6 | Launch must validate strategy, budget, content, approval, connector, and policy readiness | Required |
| FR-2.7 | Rollback/compensation must be explicit and limited to supported operations | Required |

## FR-3 Strategy

| ID | Requirement | Priority |
|---|---|---|
| FR-3.1 | User can request strategy generation from objective, audience, offer, constraints, and region | Required |
| FR-3.2 | Strategy output includes segments, positioning, channel plan, messaging, assumptions, risks, and success metrics | Required |
| FR-3.3 | Strategy output includes AI/model provenance where AI is used | Required |
| FR-3.4 | User can edit or request revision | Required |
| FR-3.5 | Sensitive or high-impact strategies require approval | Required |

## FR-4 Budget

| ID | Requirement | Priority |
|---|---|---|
| FR-4.1 | User can view budget plan for campaign/workspace | Required |
| FR-4.2 | System can recommend budget allocation with rationale and assumptions | Required |
| FR-4.3 | Budget changes above configured thresholds require approval | Required |
| FR-4.4 | Budget history and final approved source must be visible | Required |
| FR-4.5 | Spend data must be reconciled with connector/performance data where available | Boundary |

## FR-5 Content Generation and Validation

| ID | Requirement | Priority |
|---|---|---|
| FR-5.1 | User can generate ad copy | Required |
| FR-5.2 | User can generate landing-page content | Required |
| FR-5.3 | User can generate email follow-up content | Required |
| FR-5.4 | Content validation checks brand, platform, claims, privacy, and compliance constraints | Required |
| FR-5.5 | Content versions and selected candidates are persisted | Required |
| FR-5.6 | Public/high-risk content requires approval | Required |

## FR-6 Approvals

| ID | Requirement | Priority |
|---|---|---|
| FR-6.1 | Sensitive actions create approval requests | Required |
| FR-6.2 | Approval queue is permission-aware | Required |
| FR-6.3 | Approval detail shows immutable snapshot, risk, rationale, and AI provenance where relevant | Required |
| FR-6.4 | Approve/reject decisions are immutable and audited | Required |
| FR-6.5 | Duplicate or stale decisions return conflict | Required |

## FR-7 AI Action Transparency

| ID | Requirement | Priority |
|---|---|---|
| FR-7.1 | Every AI-assisted operation creates an AI action record | Required |
| FR-7.2 | Record includes provider/model/version, prompt/input summary, output summary, rationale, confidence, risk, status | Required |
| FR-7.3 | Sensitive inputs and outputs are redacted | Required |
| FR-7.4 | UI exposes searchable AI action log | Required |
| FR-7.5 | User feedback on AI output can be captured | Optional |

## FR-8 Connectors

| ID | Requirement | Priority |
|---|---|---|
| FR-8.1 | Google Ads connector supports OAuth/status/preflight | Required |
| FR-8.2 | Campaign launch uses outbox-backed execution | Required |
| FR-8.3 | Connector execution is idempotent | Required |
| FR-8.4 | Retry, DLQ, kill switch, and failure surfacing are implemented | Required |
| FR-8.5 | Performance retrieval maps external metrics into DMOS reporting model | Required |
| FR-8.6 | Additional connectors follow the same certification model | Boundary |

## FR-9 Analytics and Reporting

| ID | Requirement | Priority |
|---|---|---|
| FR-9.1 | Dashboard reports campaign status and key performance indicators from real data | Required |
| FR-9.2 | ROI/ROAS calculations use documented formulas | Required |
| FR-9.3 | Funnel analytics show stage conversion and drop-off | Boundary |
| FR-9.4 | Attribution supports explicit model assumptions | Boundary |
| FR-9.5 | Reports show source and freshness metadata | Required |
| FR-9.6 | Exports match UI values | Required |

## FR-10 Market Research and Self-Marketing

| ID | Requirement | Priority |
|---|---|---|
| FR-10.1 | User can generate market/persona/competitor research with provenance | Boundary |
| FR-10.2 | DMOS can manage its own self-marketing funnel | Boundary |
| FR-10.3 | Research outputs have confidence and source metadata | Boundary |
| FR-10.4 | Research cannot be treated as factual without provenance | Required |

## Non-Functional Requirements

| Category | Requirement |
|---|---|
| Security | Server-side auth, capability checks, tenant isolation, safe errors |
| Privacy | PII redaction, retention, DSAR support, token protection |
| Reliability | Idempotency, retries, DLQ, safe startup, rollback path |
| Observability | Structured logs, metrics, traces, audit, correlation IDs |
| Performance | Dashboard and list APIs must meet defined latency budgets under realistic data |
| Accessibility | Critical workflows keyboard accessible and screen-reader friendly |
| Maintainability | Clear module boundaries, no duplicated business logic |
| Testability | Every critical flow testable across unit/integration/API/UI/E2E tiers |
| Compliance | Policy packs, approval matrix, immutable audit decisions |
| Internationalization | Locale/currency/date/channel compliance prepared for future expansion |

## Acceptance Criteria

A requirement is complete only when:

- It has production implementation.
- It is documented in relevant API/design/data docs.
- It has meaningful tests.
- UI and backend behavior match.
- It has no production stub or fake path.
- It emits required audit/telemetry.
- It fails safely when unavailable.

## Recovered Product Requirements

## FR-11 Self-Marketing

| ID | Requirement | Priority |
|---|---|---|
| FR-11.1 | DMOS can run its own marketing funnel in a separate internal tenant | Required |
| FR-11.2 | Self-marketing data must never mix with customer tenants | Required |
| FR-11.3 | Self-marketing campaigns use the same approval, audit, and analytics flows as customer campaigns | Required |
| FR-11.4 | Case studies generated from self-marketing data must be privacy-safe | Required |

## FR-12 Intake and Free Audit

| ID | Requirement | Priority |
|---|---|---|
| FR-12.1 | User can submit website, business profile, geography, offer, budget, and goal | Required |
| FR-12.2 | System records consent and purpose before using contact/marketing data | Required |
| FR-12.3 | System generates free audit with website, competitor, keyword, funnel, and tracking observations | Required |
| FR-12.4 | Audit output can become strategy/proposal input | Required |

## FR-13 Proposal and Contract

| ID | Requirement | Priority |
|---|---|---|
| FR-13.1 | System can convert approved strategy into proposal draft | Required |
| FR-13.2 | System can recommend pricing/package assumptions | Boundary |
| FR-13.3 | System can generate SOW/MSA-style draft from approved templates only | Required |
| FR-13.4 | Contract/proposal drafts require human approval before send/signature | Required |
| FR-13.5 | System flags unsupported guarantees, legal-sensitive claims, privacy risk, and missing approvals | Required |
| FR-13.6 | System clearly marks contract output as draft/not legal advice | Required |

## FR-14 CRM-Lite and Sales Loop

| ID | Requirement | Priority |
|---|---|---|
| FR-14.1 | System captures leads from landing pages/forms | Required |
| FR-14.2 | Lead references PII-safe contact model, not raw contact blobs | Required |
| FR-14.3 | Lead status workflow supports captured, qualified, contacted, opportunity, won, lost | Required |
| FR-14.4 | Opportunity can link to proposal and contract | Boundary |
| FR-14.5 | Renewal/upsell recommendation can be generated from outcomes | Boundary |

## FR-15 Durable Execution

| ID | Requirement | Priority |
|---|---|---|
| FR-15.1 | Sensitive/external actions are represented as durable commands | Required |
| FR-15.2 | Commands support idempotency, status, retries, and correlation | Required |
| FR-15.3 | Failed commands enter retry or DLQ according to policy | Required |
| FR-15.4 | Rollback/compensation is defined for supported actions | Required |
| FR-15.5 | Kill switches can block execution at campaign/workspace/tenant/global level | Required |

## FR-16 Consent-First Measurement

| ID | Requirement | Priority |
|---|---|---|
| FR-16.1 | Consent categories include necessary, analytics, marketing, and channel-specific consent where applicable | Required |
| FR-16.2 | Consent proof stores source, timestamp, text shown, policy version, region, method | Required |
| FR-16.3 | Purpose limitation is enforced before audience export, email, SMS, CRM export, or external sync | Required |
| FR-16.4 | Enhanced conversion or external matching uses hashed/allowed first-party data only where consent permits | Boundary |
| FR-16.5 | Suppression lists are enforced before contact or audience sync | Required |
