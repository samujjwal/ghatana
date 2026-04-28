# ADR-021: Data Cloud Autonomy Levels, Human Takeover, and Rollback

**Status:** Accepted  
**Date:** 2026-04-28  
**Decision Makers:** Data Cloud Platform Team  
**Phase:** 2 — Core Intelligence & Trust  

## Context

Data Cloud provides AI-assisted and autonomous operations across pipeline execution, alert remediation, schema inference, and governance policy generation. Without explicit trust boundaries, fully autonomous actions could introduce regressions, security policy violations, or compliance breaches. The platform must allow operators to define per-domain autonomy levels, intervene in real time, and undo (roll back) actions where safe.

## Decision

Introduce a **four-tier autonomy model** with per-domain configuration, human takeover endpoints, and compensating rollback:

### 1. Autonomy Levels

| Level | Meaning | Examples |
|---|---|---|
| **SUGGEST** | AI proposes; operator must confirm | High-risk schema changes, policy recommendations |
| **CONFIRM** | AI proposes; operator must confirm within timeout | Remediation actions, alert triage |
| **NOTIFY** | AI acts autonomously; notifies operator | Low-risk pipeline retries, data quality fixes |
| **AUTONOMOUS** | AI acts and reports in batch | Alert acknowledgment, metric aggregation |

Each domain (`query`, `pipeline`, `governance`, `operations`, `alerting`) stores its own `autonomyLevel`, `confidenceThreshold`, and `requiresApproval` flag.

### 2. Human Takeover API

- `POST /api/v1/autonomy/pause` — Freeze all autonomous actions for a domain or tenant
- `POST /api/v1/autonomy/resume` — Resume autonomous actions
- `POST /api/v1/autonomy/review` — Queue recent autonomous actions for human review
- `GET /api/v1/autonomy/plan/:actionType` — Expose plan, inputs, confidence, risk, and cost before execution

Takeover events are written to the audit event log with `actor`, `timestamp`, `reason`, and `domain`.

### 3. Rollback & Compensation

Where an action is reversible, the platform records a **compensating action** at the time of execution:

- Before mutating a database step, record the undo SQL or pre-image.
- Before deleting a file, record a restore path or backup reference.
- Before invoking an external API, store the `undoEndpoint` or `compensateUrl`.

Rollback is triggered by:

- Operator-initiated `POST /api/v1/executions/:id/rollback`
- Policy-initiated when confidence drops below threshold after feedback
- Automatic when a post-action validation (data contract, SLA) fails

### 4. Audit Requirements

Every autonomy decision, execution, pause/resume, and rollback writes an event to `dc_autonomy_log` (collection) and emits a structured event (`autonomy.decision`, `autonomy.rollback`, `autonomy.takeover`) to the event log. Events include:

- `tenantId`, `domain`, `actionType`, `confidence`, `riskScore`, `costEstimate`
- `operatorId` (if human), `autonomyLevel` at time of action
- `rollbackId` and `compensationResult` if rolled back

## Rationale

- **Four tiers** give operators graduated trust without all-or-nothing autonomy.
- **Per-domain** configuration allows sensitive domains (governance, security) to remain SUGGEST while operations can be AUTONOMOUS.
- **Human takeover** preserves emergency control without requiring full shutdown.
- **Compensating actions** enable safe undo rather than complex saga orchestration at this scale.
- **Audit logging** satisfies compliance requirements (SOX, HIPAA traceability) and feeds the learning loop with verified outcomes.

## Consequences

- Every AI action path must check autonomy level before execution.
- Rollback requires pre-image storage; this adds I/O and storage cost.
- Operator review queues require UI attention; may create backlog if NOTIFY-level actions are too noisy.
- Audit log volume increases with every autonomous action; retention policy must align with compliance requirements.

## Alternatives Considered

1. **Binary autonomy (all on or all off)** — rejected; too coarse for a platform with mixed-risk domains.
2. **External approval workflow (e.g., Jira, ServiceNow)** — deferred; adds external dependency, can integrate later via webhook.
3. **Event sourcing for rollback** — rejected; compensating actions are simpler and sufficient for current step granularity.

## Related

- `AutonomyHandler.java` — HTTP endpoints for pause, resume, review, plan, and policy updates.
- `WorkflowExecutionHandler.handleRollbackExecution()` — per-step rollback with compensating actions.
- `AiAssistHandler.handleAiSuggestionFeedback()` — feedback loop that adjusts confidence thresholds.
