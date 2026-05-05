# ADR-026: Workflow Terminology and Ownership Boundary

## Status

Accepted

## Context

The current Data Cloud and AEP surfaces both use the words "workflow" and "pipeline" in overlapping ways.
This creates operator and developer confusion about which product owns execution responsibilities.

The product boundary is already intended to be asymmetric:

- Data Cloud owns data-local plugin execution and execution persistence tied to data workloads.
- AEP owns agentic orchestration and runtime control-plane behavior.

We need one canonical terminology decision that is enforced in docs, UI copy, and route/contract tests.

## Decision

Use the following canonical wording:

1. Data Cloud terminology
- "workflow" in Data Cloud means data-local plugin execution.
- Data Cloud pipeline/workflow routes remain under `/api/v1/pipelines/*` for compatibility.
- Data Cloud docs and UI copy must describe these flows as plugin runtime execution, not agentic orchestration.

2. AEP terminology
- AEP is the agentic orchestration and runtime control plane.
- AEP pipeline and workflow-catalog surfaces are orchestration concepts.
- AEP docs and UI copy must call out orchestration/runtime ownership explicitly.

3. Contract guardrails
- Route names stay stable unless a dedicated migration ADR is accepted.
- OpenAPI and route-registry tests must assert the terminology boundary so drift is caught in CI.

## Rationale

- Preserves API compatibility while clarifying ownership.
- Reduces operational confusion during incidents and escalations.
- Gives engineering a testable source of truth for wording and route intent.

## Consequences

Positive:

- Data Cloud and AEP docs communicate a clear product boundary.
- UI guidance aligns with runtime ownership.
- CI catches terminology regressions before release.

Trade-offs:

- Existing internal symbols and legacy route aliases are not renamed in this decision.
- Some historical wording will remain in lower-priority docs until separately remediated.

## Alternatives Considered

1. Rename all Data Cloud "workflow" symbols to "pipeline" in one sweep.
- Rejected due to high churn and avoidable regression risk.

2. Move AEP workflow catalog routes immediately from `/catalog/workflows` to `/catalog/orchestrations`.
- Rejected because it introduces route migration complexity unrelated to this hardening item.

3. Keep terms loosely defined and rely on tribal knowledge.
- Rejected because it is not testable and repeatedly causes confusion.
