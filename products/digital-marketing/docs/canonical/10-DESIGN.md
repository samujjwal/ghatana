# DMOS Design

## Purpose

This document defines the self-contained design for DMOS across product experience, UI layout, service interactions, AI behavior, approvals, connectors, reporting, and governance.

## Design Objectives

DMOS design must deliver:

- Simple but powerful workflows.
- Low cognitive load.
- Transparent AI assistance.
- Real-time understanding of what needs action.
- Clear distinction between ready, pending, blocked, failed, and unavailable states.
- Consistent UI and API behavior.
- Production-grade safety for external execution.

## Product Surface Design

The product is organized around a workspace. The workspace dashboard is the starting point for every user. From there, users navigate into campaigns, strategy, budget, approvals, AI actions, reports, connectors, and operations depending on permissions and capabilities.

### Workspace dashboard sections

1. **KPI strip:** campaign count, active spend, ROI/ROAS, pending approvals, risk alerts.
2. **Campaign health:** lifecycle status, performance trend, launch readiness.
3. **Decision queue:** approvals, blocked actions, required reviews.
4. **AI recommendations:** next-best actions with confidence and rationale.
5. **Risk/compliance:** policy violations, high-risk content, connector issues.
6. **Connector health:** OAuth status, last sync, failures, kill switch.
7. **Activity/audit:** recent actions and AI logs.

## UI Component Design

### Core components

| Component | Purpose |
|---|---|
| CommandCard | Compact dashboard decision or status card |
| KpiStrip | First-level data summary |
| ReadinessChecklist | Launch and connector readiness |
| ApprovalQueueTable | Reviewer task list |
| ApprovalSnapshotPanel | Immutable approval request detail |
| AiProvenancePanel | Model/provider/rationale/confidence display |
| RiskBadge | Visual risk classification |
| FeatureUnavailablePanel | Honest disabled/incomplete state |
| ConnectorHealthPanel | Connector status, token state, last sync |
| DataFreshnessBadge | Source and last updated indicator |
| AuditTimeline | User/system/AI action history |

### State design

Every major component must support:

- Loading.
- Empty.
- Error.
- Unauthorized.
- Feature disabled.
- Partial data.
- Stale data.
- Pending mutation.
- Success.
- Conflict.

## Form Design

Forms should be short, guided, and progressive. Campaign, strategy, budget, and content forms should ask only what is required at the current step.

### Form rules

- Required fields are explicit.
- Validation messages explain how to fix.
- AI suggestions are editable.
- Risky changes show impact before submit.
- External-impact actions require confirmation.
- Duplicate submissions are prevented.
- Failed submissions preserve user input.
- Successful submissions show next step.

## Approval Design

Approval is not a generic modal. It is a decision workflow.

Approval detail must include:

- Requested action.
- Target object.
- Requested by.
- Requested at.
- Current status.
- Immutable snapshot.
- Diff from previous state when available.
- AI provenance when applicable.
- Risk and policy findings.
- Business impact.
- Approve/reject actions.
- Notes.
- Audit timeline.

## AI Design

AI features must be designed as assistance, not opaque automation.

### AI output display

Every AI-generated or AI-recommended output should show:

- What was generated.
- Why it was generated.
- Model/provider/version.
- Confidence or uncertainty indicator.
- Input summary.
- Risk/policy findings.
- Whether a human edited it.
- Whether it was approved, rejected, or executed.

### AI safety rules

- No silent public publishing.
- No silent budget movement.
- No hidden connector execution.
- No unredacted sensitive prompt logging.
- No AI output treated as factual research without provenance.
- No AI recommendation without user-visible rationale.

## Service Design

Application services should be use-case oriented.

Example service responsibilities:

| Service | Responsibility |
|---|---|
| CampaignService | Campaign lifecycle and launch readiness |
| StrategyService | Strategy generation, revision, approval integration |
| BudgetService | Budget recommendation, threshold checks, approvals |
| ContentGenerationService | Content generation and versioning |
| ContentValidationService | Brand/policy/compliance validation |
| ApprovalService | Approval request, queue, detail, decision |
| AiActionLogService | AI action persistence and retrieval |
| ConnectorExecutionService | Outbox, retries, kill switch, external execution |
| ReportingService | KPI, ROI/ROAS, funnel, attribution calculations |
| WorkspaceService | Capabilities, settings, workspace metadata |

Services should accept `DmOperationContext` or equivalent and must not infer tenant/workspace from request-global state.

## Domain Design

Domain models should enforce invariants:

- Campaign lifecycle transitions.
- Required launch readiness checks.
- Budget threshold rules.
- Approval decision immutability.
- AI action provenance presence.
- Connector execution status transitions.
- Tenant/workspace identity consistency.
- Content version immutability once approved/published.

## Connector Design

Connectors must be treated as external-impact workflows.

Required design elements:

- Connector account model.
- OAuth/token lifecycle.
- Preflight validation.
- Outbox command.
- Idempotency key.
- Retry policy.
- DLQ.
- Kill switch.
- External ID mapping.
- Error categorization.
- Reconciliation.
- Compensation/rollback where supported.
- Audit and telemetry.

## Reporting Design

Reports must show:

- Formula definition.
- Source data.
- Freshness timestamp.
- Scope filters.
- Empty/partial state.
- Export parity.
- Confidence or limitation notes where applicable.

### Core formulas

- ROI = `(revenue - spend) / spend`.
- ROAS = `revenue / ad_spend`.
- Conversion rate = `conversions / visits_or_clicks`.
- Cost per acquisition = `spend / acquisitions`.
- Funnel drop-off = `1 - next_stage_count / current_stage_count`.

Formulas must define denominator-zero behavior and currency/date handling.

## Governance Design

Governance is embedded, not an afterthought.

### Required controls

- Server-derived identity.
- Tenant/workspace scoping.
- Capability checks.
- Feature flags.
- Approval policies.
- Compliance rule packs.
- Audit events.
- PII redaction.
- Retention/DSAR.
- Connector kill switches.

## Design Acceptance Criteria

A design is ready for implementation when:

- User outcome is clear.
- UI states are defined.
- API contract is known.
- Service ownership is known.
- Domain invariants are listed.
- Data model changes are identified.
- Authorization and capability rules are defined.
- Audit/telemetry requirements are defined.
- Tests are listed.
- Failure modes are designed.

## Recovered Design Additions

## Growth Execution Design

DMOS design should be organized around the user asking for outcomes, not building low-level campaigns.

Primary interaction:

```text
"Here is my business, offer, target market, constraints, budget, and goal."
```

DMOS response:

```text
"Here is the recommended strategy, proposal, assets, launch readiness, expected metrics, risks, approvals needed, and first action."
```

## Preflight Design

The launch button must not be a simple submit button. It is a preflight workflow.

Preflight output design:

| State | Meaning | UX |
|---|---|---|
| Ready | All checks pass | Enable launch/export |
| Needs approval | Policy requires human decision | Route to approval |
| Needs fix | User/config issue blocks launch | Show fix actions |
| Connector blocked | OAuth/health/kill switch issue | Show connector remediation |
| Compliance blocked | Claim/privacy/policy failure | Show evidence and remediation |
| Unsupported | Feature/action unavailable | Show explicit unavailable state |

## Contract/SOW Design

Contract and proposal design must include:

- Template source.
- Clause version.
- Strategy source.
- Pricing assumptions.
- Scope and deliverables.
- Timeline and milestones.
- Approval gates.
- Risk flags.
- Human approver.
- Audit timeline.
- Disclaimer: draft only, not legal advice.

## Learning Design

Learning is a first-class design surface:

- Every experiment records hypothesis.
- Every result records confidence.
- Every recommendation records expected impact.
- Every human override records reason.
- Successful patterns can be promoted to playbook versions.
- Playbook promotion requires approval.
- Failed recommendations feed model/agent evaluation.
