# DMOS UX Workflows

## Purpose

This document defines the self-contained user experience and workflow model for DMOS. It describes routes, personas, user journeys, page states, interaction rules, and approval behavior.

## UX Philosophy

DMOS should feel like a digital marketing command center: simple, actionable, and complete. The UI should surface the next useful decision, not expose internal modules. It should make AI, approvals, risk, and performance understandable without increasing cognitive load.

Core UX rules:

- Start from workspace dashboard.
- Show real data only.
- Make feature availability clear.
- Keep one primary action per workflow stage.
- Use consistent page patterns.
- Preserve transparency for AI and approvals.
- Never make a fake or disabled feature look functional.
- Provide actionable empty, error, loading, unauthorized, stale, and partial-data states.

## Primary Navigation Model

| Route | Purpose | Production Requirement |
|---|---|---|
| `/login` | Authentication entry point | Production auth; manual login dev-only |
| `/workspaces/:workspaceId/dashboard` | Command center | Real campaign, approval, risk, budget, AI, and workflow data |
| `/workspaces/:workspaceId/campaigns` | Campaign management | List, create, lifecycle actions |
| `/workspaces/:workspaceId/strategy` | Strategy generation/review | AI provenance, review, approval |
| `/workspaces/:workspaceId/budget` | Budget planning | Recommendation, approval, tracking |
| `/workspaces/:workspaceId/approvals` | Pending approvals | Filter by action, risk, requester, status |
| `/workspaces/:workspaceId/approvals/:requestId` | Approval detail | Snapshot, risk, approve/reject |
| `/workspaces/:workspaceId/ai-actions` | AI transparency | Searchable AI action log |
| `/workspaces/:workspaceId/funnel-analytics` | Funnel reporting | Feature-gated until real data exists |
| `/workspaces/:workspaceId/attribution` | Attribution reporting | Feature-gated until real data exists |
| `/workspaces/:workspaceId/roi-roas` | Financial performance | Feature-gated until real data exists |
| `/workspaces/:workspaceId/market-research` | Research and positioning | Feature-gated until backed by real services |
| `/workspaces/:workspaceId/agency` | Multi-client operations | Feature-gated until client model is complete |

## Dashboard Workflow

The dashboard should answer:

1. What is happening now?
2. What needs my attention?
3. What is at risk?
4. What changed because of AI or automation?
5. What should I do next?

Recommended dashboard hierarchy:

1. KPI strip: active campaigns, spend, ROI/ROAS, pending approvals, risk alerts.
2. Campaign performance or lifecycle summary.
3. Approval and task queue.
4. AI recommendations and AI action log summary.
5. Risk/compliance summary.
6. Connector health and data freshness.
7. Recent activity/audit trail.

No dashboard card may show static placeholder numbers in production.

## Campaign Workflow

### Create campaign

1. User opens campaigns page.
2. User clicks create campaign.
3. Form asks for objective, audience, budget range, channel intent, dates, landing page, and constraints.
4. System validates required fields.
5. User submits.
6. API creates draft campaign.
7. UI shows campaign detail with draft status and next steps.

### Review campaign

1. User opens campaign detail.
2. UI shows strategy, budget, content, risk, approvals, connector readiness, and performance.
3. User edits sections or requests AI assistance.
4. Changes are saved with audit and version metadata.

### Launch campaign

1. User clicks launch.
2. UI shows preflight checklist.
3. Budget, content, connector, approval, and policy states are checked.
4. If approval is required, launch creates approval request.
5. If approved and connector ready, launch creates connector execution command.
6. UI shows launch pending/running/succeeded/failed state.

### Pause, complete, archive, rollback

Lifecycle actions must show:

- Current state.
- Action impact.
- Whether connector compensation is available.
- Confirmation for destructive or external-impact actions.
- Success/failure state.
- Audit trail update.

## Strategy Workflow

1. User provides business objective, customer segment, offer, geography, constraints, budget, and tone.
2. AI/deterministic strategy service generates a structured strategy.
3. UI displays target audience, positioning, channels, messaging, campaign structure, risks, assumptions, and confidence.
4. User edits or requests revision.
5. Sensitive or high-budget strategies require approval.
6. Approved strategy becomes campaign planning baseline.

## Budget Workflow

1. User opens budget page.
2. System shows current campaign budget, recommended allocation, rationale, assumptions, and risks.
3. User adjusts budget.
4. Budget changes above configured thresholds require approval.
5. Approved budget updates campaign and connector execution constraints.
6. UI records final budget source: manual, AI recommendation, imported, or approved override.

## Content Workflow

1. User requests ad copy, landing-page content, email follow-up, or validation.
2. UI collects brief, audience, tone, offer, compliance constraints, and channel.
3. AI generates candidate content.
4. Content validation checks brand, policy, claims, privacy, and platform constraints.
5. User reviews versions and selects candidate.
6. Approval is required for public publishing, regulated claims, or high-risk outputs.
7. Final content is attached to campaign with version history.

## Approval Workflow

### Queue

Approval queue must show:

- Request title and type.
- Risk level.
- Requested by.
- Requested at.
- Target entity.
- Required permission.
- Current status.
- SLA/age indicator.
- Filter/sort controls.

### Detail

Approval detail must show:

- Immutable snapshot of requested action.
- Diff from previous state where relevant.
- Policy/compliance findings.
- AI provenance if AI generated or recommended the action.
- Business impact.
- Approve/reject controls.
- Notes field.
- Audit history.

### Decisions

- Approve resumes or enables downstream action.
- Reject blocks downstream action and records reason.
- Duplicate or stale decisions return conflict.
- Unauthorized decisions show 403 and do not mutate state.

## AI Action Log Workflow

AI action log must help users answer:

- What did AI do?
- What input did it use?
- What model/provider/version was used?
- What output was produced?
- What confidence/rationale was recorded?
- Who approved, edited, rejected, or executed it?
- Was any sensitive data redacted?
- Which campaign/content/budget item was affected?

## Feature-Unavailable UX

Feature-gated routes must show:

- Feature name.
- Why unavailable: disabled, missing permission, connector not configured, not implemented, plan tier, or production safety gate.
- Required capability or next action.
- Safe navigation back to dashboard.
- No fake data or partial illusion of functionality.

## Page State Requirements

Every page must implement:

- Loading state.
- Empty state.
- Error state.
- Unauthorized state.
- Feature disabled state.
- Stale data or partial data state where applicable.
- Retry behavior for safe requests.
- Pending state for mutating actions.
- Confirmation for destructive/external-impacting actions.

## UX Acceptance Criteria

A workflow is production-ready when:

- A user can complete it from dashboard without internal knowledge.
- Every visible action maps to a real backend capability or explicit unavailable state.
- All data is traceable to an API response.
- Role/capability restrictions are visible and enforced.
- AI output includes provenance and explanation.
- Sensitive actions require approval.
- Loading, empty, error, unauthorized, stale, and disabled states are tested.

## Recovered End-to-End UX Journeys

## Self-Marketing Journey

DMOS must be able to use its own engine to acquire customers in an isolated internal tenant.

```text
Visitor
  -> DMOS landing page
  -> free audit / lead magnet
  -> AI qualification
  -> business intake
  -> generated audit
  -> consultation/demo
  -> proposal/SOW draft
  -> contract approval/signature
  -> onboarding
```

User-visible states:

- Anonymous visitor.
- Qualified lead.
- Audit generated.
- Proposal pending.
- Contract pending.
- Customer onboarding.
- Active workspace.

## Free Audit Journey

1. Visitor provides website, geography, business type, offer, and goal.
2. System records consent and purpose.
3. AI audits website, competitors, keywords, and funnel basics.
4. User sees useful summary even before purchase.
5. System creates lead record and recommended next step.
6. Proposal/SOW can be generated from audit outcome.

Audit output should include:

- Website gaps.
- Competitor observations.
- Keyword/search opportunity.
- Offer/messaging issues.
- Tracking gaps.
- First 30-day plan.
- Risk/compliance warnings.
- Confidence/source notes.

## Proposal/SOW Journey

1. Approved strategy becomes proposal input.
2. Scope builder maps goals to deliverables, channels, timeline, assumptions, and approval gates.
3. Pricing recommender suggests package, retainer, performance fee, or hybrid model.
4. Proposal generator creates branded proposal.
5. Contract agent drafts SOW/MSA from approved templates.
6. Risk flags are surfaced.
7. Human approves before sending.
8. Signature status is tracked.
9. Renewal engine uses campaign outcomes to propose renewal/upsell.

UX must clearly state that generated contracts are drafts from approved templates and not legal advice.

## Campaign Safety Preflight UX

Launch flow must show a checklist before any external execution:

| Category | Checks |
|---|---|
| Tracking | Conversion events, UTM, click IDs, consent banner, destination URL |
| Budget | Daily/monthly cap, approved maximum, pacing |
| Creative | Brand validation, claim validation, forbidden claims, disclosures |
| Audience | Region, age restrictions, consent/audience source, exclusion lists |
| Compliance | Channel policy, industry policy, privacy policy, unsubscribe |
| Technical | Landing page availability, form works, CRM mapping |
| Rollback | Pause/unpublish/suppress instructions exist |
| Human approval | Required approvals captured |

Failed preflight blocks launch unless explicit policy allows approved override.

## Override and Delegation UX

Users must be able to:

- Pause automation.
- Override AI recommendations.
- Reject proposed commands.
- Approve one action.
- Delegate approval authority.
- Set delegation expiration.
- Resume automation.
- Revoke delegation.
- Require stricter approvals.

Every override or delegation requires a reason and appears in audit history.
