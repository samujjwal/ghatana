# YAPPC Production Readiness Audit Tracker

## Purpose

This tracker translates the production-readiness audit for commit `f302e89c8e7116e8821a7957b4a06a5d7dff81e` into executable implementation slices. It follows the repository rules in `.github/copilot-instructions.md`: reuse existing platform contracts first, keep boundaries explicit, avoid silent failures, test meaningful behavior, and make production degradation observable.

## Execution Strategy

1. Stabilize lifecycle truth first: phase packets, gates, policy, feature flags, evidence, and run status.
2. Make Kernel handoff deterministic: typed contract registry, required workspace/project/surface/profile input, and provider-backed visibility.
3. Remove frontend-only lifecycle truth: render backend-returned actions/readiness and externalize copy through platform i18n.
4. Close Data Cloud/AEP loops: tenant/workspace-scoped evidence, run status, learning events, and evolution traceability.
5. Prove readiness with focused unit, contract, integration, and CI tests.

## Tracking List

| ID | Priority | Audit Items Covered | Implementation Slice | Status | Acceptance Criteria |
| --- | --- | --- | --- | --- | --- |
| TODO-001 | P0 | BE-001, AEP-001, Run | Add `PlatformRunStatusService` backed by Data Cloud platform run records and include status in `PhasePacket`. | Implemented in code | Packet includes run ID, status, trace ID, and evidence IDs when a run exists. |
| TODO-002 | P0 | BE-002, Validate | Build gate validator context from project, workspace, tenant, flags, completed artifacts, evidence, governance, and health state. | Implemented in code | Gate validator receives non-empty tenant/workspace/artifact/evidence/policy context. |
| TODO-003 | P0 | BE-003, BE-005, Security/RBAC | Make available actions server-side decisions from capabilities, readiness, tenant tier, feature flags, and policy outcomes. | Implemented in code | Denied policy or missing capability disables actions with explicit reasons. |
| TODO-004 | P0 | KRN-001, KRN-002, SHR-004 | Add a Kernel ProductUnit contract registry for provider/profile/surface validation and use it from exporter/validator. | Implemented in code | Invalid providers, profiles, or surfaces fail validation through one registry. |
| TODO-005 | P0 | BE-008, KRN-003 | Require explicit Kernel CLI workspace ID, project ID, lifecycle profile, and surfaces. | Implemented in code | Missing workspace/project/surface/profile fails clearly; no random workspace/default surface is emitted. |
| TODO-006 | P0 | KRN-004, ARCH-003 | Split Kernel lifecycle ingestion into provider interface plus filesystem dev provider and Data Cloud production provider. | Implemented in code | Production-mode provider does not read `.kernel` directly. |
| TODO-007 | P0 | BE-007, SHR-005 | Remove static cached executor from Kernel ingest and inject a managed executor. | Implemented in code | Executor lifecycle is owned by caller/runtime and can be tested. |
| TODO-008 | P1 | BE-004, Validate | Replace binary readiness with weighted artifact, blocker, evidence, governance, and health scoring. | Implemented in code | Score changes explain missing requirements and never reports ready with critical blockers. |
| TODO-009 | P1 | BE-003, Feature flags | Source enabled phase flags from project/entitlement state instead of always returning empty. | Implemented in code | Packet includes effective flags from project state and tier defaults. |
| TODO-010 | P1 | DC-002, Evidence | Include workspace ID in platform evidence and governance queries. | Implemented in code | Evidence queries are tenant/project/workspace scoped. |
| TODO-011 | P1 | FE-002, SHR-001 | Move phase cockpit route copy into `@ghatana/i18n` resources. | Implemented in code | Component uses translation keys for route-owned cockpit copy. |
| TODO-012 | P1 | FE-003, FE-004, SHR-002 | Remove client-side action enablement inference and avoid synthesized transition prediction where backend data exists. | Implemented in code | UI disables actions from backend action contract and no longer upgrades access locally. |
| TODO-013 | P1 | BE-006, DC-001 | Apply fail-closed tenant handling across durable YAPPC repositories. | Implemented in code | Shared Data Cloud repository and AI suggestion paths reject missing/default tenant before durable access. |
| TODO-014 | P1 | AEP-002, Learn | Persist learning evidence from generation, gate, approval, and run failures. | Implemented in code | Learn phase saves tenant-scoped evidence with observation, insight, run, project, and provenance references. |
| TODO-015 | P1 | Evolve | Add evolution proposal, impact, approval, ProductUnitIntent update, and validate/generate/run loop. | Implemented in code | Evolve phase persists approval-pending proposals with insight, plan, intent, project, tenant, and provenance metadata. |
| TODO-016 | P2 | ARCH-001, KRN-005, Docs | Reconcile Kernel visibility docs with implementation states. | Implemented in docs | Docs use verified states instead of contradictory pending/complete language. |
| TODO-017 | P2 | Testing/CI | Ensure YAPPC-specific backend/frontend/contract/i18n tests run in CI. | Implemented in CI | CI quality job runs SLO, cost, domain invariant, and OpenAPI breaking-change gates and uploads evidence. |
| TODO-018 | P2 | Observability | Surface SLO, cost, and domain invariant evidence in Observe/Admin. | Implemented in UI | Admin observability route shows release-gate evidence cards linked to CI-generated artifacts. |

## Residual Long-Running Work

The follow-up implementation pass added route-level i18n hardening for Admin Observability, a typed live release-gate evidence loader with CI artifact fallback, and an executable YAPPC E2E Gradle module that proves Learn/Evolve tenant/project/run provenance through Kernel promotion. Additional hardening should continue as incremental evidence-backed work: replace static monitoring stack URLs with environment-backed configuration and promote release-gate evidence retrieval fully to the backend once the admin observability endpoint is deployed.
