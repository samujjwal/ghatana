# YAPPC Canonical Models

> **Status**: Existing and Executable

> This document defines the canonical models for YAPPC as implemented and operational.

## Purpose Models

Status: active contributor reference

Use this document when adding or changing a phase, component, import path, generator, preview surface, or governance event. It complements the canonical terminology glossary in `products/yappc/docs/guides/terminology-glossary.md`.

**IMPORTANT**: YAPPC integrates with the merged Data Cloud+AEP platform product through typed contracts. AEP and Data Cloud are no longer treated as separate external products.

## Product Model

YAPPC separates the stored work container from the software being delivered.

| Term | Meaning | Primary surfaces |
| --- | --- | --- |
| Project | Workspace-scoped delivery container that moves through the mounted lifecycle. | `/api/projects/*`, dashboard cards, `/p/:projectId/:phase` routes |
| Product | Real-world customer or business outcome being shaped by the project. | Intent notes, Shape decisions, roadmap and retrospective copy |
| App | Runnable software produced, generated, deployed, or observed from a project. | Generate outputs, Run deployments, Observe preview/runtime signals |

Rules:

- Use Project for persisted resources, access control, dashboard actions, phase routing, and API identifiers.
- Use Product for the business outcome, not as a synonym for a project row.
- Use App only for generated or deployed runtime software.
- Keep workspace, tenant, and actor scope explicit in backend calls and audit records.

## Lifecycle Model

The mounted lifecycle is:

1. Intent
2. Shape
3. Validate
4. Generate
5. Run
6. Observe
7. Learn
8. Evolve

Each phase route is mounted at `/p/:projectId/:phase`, where `:phase` is one of `intent`, `shape`, `validate`, `generate`, `run`, `observe`, `learn`, or `evolve`.

Lifecycle packet inputs:

- Persisted project snapshot from `/api/projects/{projectId}`.
- Activity feed from `/api/projects/{projectId}/activity`.
- Readiness preview from `/api/v1/lifecycle/next`.
- Backend dashboard classification from `/api/projects/dashboard-actions`.
- Phase actions through typed `yappcApi` methods, never ad hoc route-local fetches.

When adding a phase:

1. Extend the canonical phase adapter in `frontend/web/src/services/phase/CanonicalPhaseService.ts`.
2. Add phase copy and supporting workspace labels in `frontend/web/src/routes/app/project/_phaseCockpit.tsx`.
3. Update OpenAPI lifecycle schemas and route summaries.
4. Add route tests in `frontend/web/src/routes/app/project/__tests__/phase-cockpit-routes.test.tsx`.
5. Update dashboard action routing if the phase can appear in backend classifications.

## Artifact Model

An Artifact is a governed lifecycle output that can be reviewed, persisted, traced, or generated.

Artifact families:

- Lifecycle artifacts: requirements, evidence, risk, approvals, and run results.
- Generated artifacts: code, config, CI, tests, docs, schemas, and diffs.
- Imported artifacts: governed source imports, decompiled components, and residual islands.
- Page artifacts: Page documents that wrap Builder documents plus governance metadata.

Core API surfaces:

- `/api/artifacts` and `/api/artifacts/{artifactId}` for lifecycle artifacts.
- `/api/v1/yappc/generate` and `/api/v1/yappc/generate/diff` for generated artifacts and provenance-bearing diffs.
- `/api/v1/yappc/artifact/import-source` for governed source imports.
- `/api/artifacts/{artifactId}/code-associations` for traceability between artifacts and code.

When adding an artifact type:

1. Define the wire contract in OpenAPI before adding UI assumptions.
2. Preserve tenant, workspace, project, artifact, actor, and phase scope.
3. Add provenance or governance trace fields when the artifact can affect generated output.
4. Add contract tests that fail if the frontend client uses a route missing from OpenAPI.

## Builder Model

The page builder has three nested concepts:

| Term | Meaning |
| --- | --- |
| Canvas node | Spatial graph node in the project canvas. |
| Page document | Persisted page artifact envelope with metadata, sync state, and operation history. |
| Builder document | Registry-backed ui-builder model inside a page document. |

Builder flow:

1. A canvas node may embed a page document.
2. The page document stores governance metadata, preview trust, operation history, and sync status.
3. The builder document stores component instances, slots, props, and registry contract versions.
4. Registry compatibility and migrations run before command-created nodes become builder document nodes.
5. Page document persistence records operation logs for save, conflict, import, reload, overwrite, and governance decisions.

When adding a component:

1. Add or update the design-system registry contract.
2. Add compatibility or migration rules if legacy aliases must be accepted.
3. Ensure preview trust and data classification metadata are explicit.
4. Add adapter coverage around real registry validation and serialization.
5. Add PageDesigner coverage for palette/search/category behavior if the component is user-insertable.

## Preview Trust Model

Preview trust is a policy boundary, not a visual preference.

Trust levels:

- `trusted-local`: safe to render locally without remote execution.
- `trusted-controlled`: allowed in controlled preview runtime with known dependencies.
- `semi-trusted`: requires policy guidance, isolation, or review before direct preview.
- `untrusted`: must not execute directly; show residual or unavailable state.

Preview rules:

- Components and imported artifacts declare preview trust metadata.
- Privacy-sensitive, credential, regulated, or restricted data requires explicit acknowledgement before preview or telemetry.
- Preview runtime errors, console signals, policy blocks, reload latency, and user actions surface in Observe rather than hiding inside advanced panels.
- Source import requires the artifact compiler runtime health check before decompile.

When adding an import path:

1. Validate untrusted input before mutating canvas or page documents.
2. Use governed import APIs for source imports; do not add local fallback command syntax.
3. Attach residual island metadata when a component cannot be mapped to a reviewed registry contract.
4. Block preview or import when the runtime health contract reports unavailable dependencies.

## Governance Trace

Governance trace is the auditable chain connecting decisions, generated output, UI edits, and backend actions.

Required trace dimensions:

- Tenant, workspace, project, artifact, and actor identifiers.
- Mounted phase and lifecycle packet context.
- Operation name, outcome, and timestamp.
- Changed node ids or generated file paths where applicable.
- Review, approval, rollback, or overwrite reason when the action can affect delivery.
- Data classification and preview trust when telemetry, preview, import, or generation is involved.

Primary trace surfaces:

- `/api/audit/events` for product audit events.
- Page document operation logs for builder edits and persistence decisions.
- Generate provenance on generated files and diff regions.
- Dashboard safe-action execution audit through `/api/projects/{projectId}/dashboard-actions/execute`.
- Platform execution trace references for Data Cloud+AEP integration.

## Platform Integration Boundary

YAPPC consumes Data Cloud+AEP capabilities through typed contracts only. YAPPC must not import internal platform runtime, memory, retrieval, analytics, or telemetry modules.

Platform contract categories:

- **Agent/intelligence execution**: Submit lifecycle tasks, inspect orchestration status, display execution progress, show trace references, cancel long-running work.
- **Evidence/retrieval**: Retrieve relevant project evidence, show evidence in Validate/Generate/Review, index approved artifacts, support traceability.
- **Memory**: Retrieve project-scoped memory summaries, write approved lifecycle knowledge.
- **Telemetry/analytics**: Emit lifecycle events, display health summaries, show model quality or confidence summaries, support feedback loops.
- **Policy/guardrails**: Evaluate high-impact operations, show block/review/approve decisions, attach policy decisions to audit records.

Required YAPPC → Data Cloud+AEP request context:

- tenantId, workspaceId, projectId, actorId
- phase, operation, dataClassification
- requestedAt, correlationId
- artifactId, canvasNodeId, generationRunId where applicable

Required Data Cloud+AEP → YAPPC response metadata:

- status, confidence, confidenceReason
- traceId, evidenceIds, policyDecisionId
- degraded, degradedReason
- createdAt, completedAt
- runId, memoryRecordIds, searchResultIds where applicable

Forbidden dependencies:

- YAPPC cannot import internal Data Cloud+AEP runtime modules (e.g., agent-registry, execution-engine, memory, search internals).
- YAPPC cannot import internal Data Cloud+AEP data modules (e.g., events, embeddings, analytics internals).
- YAPPC must use typed/generated platform clients for all platform communication.

When adding a generator:

1. Start from an explicit project, actor, phase, and source artifact context.
2. Return generated artifacts with provenance, not anonymous file blobs.
3. Route review decisions through apply, reject, or rollback endpoints.
4. Preserve provenance when a user edits generated output before review.
5. Add tests that prove review-required and rollback metadata are visible to the operator.

## Contributor Recipes

Add a phase:

1. Update lifecycle adapter, route copy, API schema, dashboard routing, and phase tests.
2. Confirm old lifecycle aliases still map through the compatibility adapter.

Add a component:

1. Update registry contract, migration compatibility, preview trust metadata, palette behavior, and builder adapter tests.

Add an import path:

1. Add validation, governed API orchestration, compiler/runtime health handling, residual island fallback, and import tests.

Add a generator:

1. Add OpenAPI schema, typed client method, provenance-bearing response model, review decision flow, and generated artifact tests.
2. Link generation to Data Cloud+AEP run/trace/evidence where applicable.

Add platform integration:

1. Define platform DTOs for the contract category (execution, evidence, memory, telemetry, policy).
2. Add required request context (tenant/workspace/project/actor/phase/operation).
3. Add required response metadata (status/confidence/traceId/evidenceIds/policyDecisionId/degraded).
4. Use typed/generated platform client; never import internal platform modules.
5. Add contract tests for platform DTOs.
6. Handle unavailable/degraded platform states explicitly.

## Verification Expectations

Every change to these models should include:

- A focused unit, component, or contract test for the touched boundary.
- A narrowed typecheck grep for touched TypeScript surfaces.
- OpenAPI contract coverage when adding or changing an HTTP route.
- Tracker updates in `products/yappc/docs/audits/IMPLEMENTATION_TRACKER.md`.
