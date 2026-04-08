# Agent System Modernization Review

Date: 2026-04-07
Status: Reviewed against live repo state
Scope: `AGENT_SYSTEM_MODERNIZATION_BLUEPRINT_2026-04-06.md` and `AGENT_SYSTEM_MODERNIZATION_IMPLEMENTATION_PLAN_2026-04-06.md`

## Outcome

The modernization direction is sound, but the original documents were not fully executable as written. The main risks were stale truth sources, duplicate contract creation, incorrect Data Cloud persistence assumptions, and one incorrect Audio-Video module target.

The blueprint and implementation plan have been updated to align with the current repo shape.

## Validated corrections

1. Module authority now treats `settings.gradle.kts` as the sole module registry; the separate shared-library registry document is removed.
2. Phase 0 now targets the real stale cross-references instead of incorrectly treating `docs/agent-system/README.md` as the broken self-learning-spec link source.
3. The autonomy section now preserves `agent-core` as the shared runtime vocabulary while documenting Data Cloud autonomy as a product-local control model that requires explicit mapping.
4. `AgentType` compatibility work now extends the existing resolver/loader path instead of introducing parallel aliasing behavior.
5. Tool-governance work now reuses the existing `ActionClass` and `tool-runtime` approval seams rather than creating duplicate platform contracts.
6. Planning work now requires reconciliation with the existing `PlanningAgent` model before introducing a durable-workflow-facing graph type.
7. Data Cloud release and rollout persistence now stays behind the existing Data Cloud persistence boundary unless a separate architecture decision changes that boundary.
8. Audio-Video orchestration work now targets the existing `multimodal-service` intelligence module by default.

## Execution guardrails

1. Do not create a second contract when a canonical one already exists in `platform/java/agent-core` or `platform/java/tool-runtime`.
2. Do not bypass product-owned persistence boundaries for convenience. If Data Cloud storage changes are needed, make them in the Data Cloud-owned layer.
3. Do not collapse product-local control vocabularies into shared runtime enums without an explicit ADR and migration plan.
4. Do not introduce new planning type names into `com.ghatana.agent.planning` until the current `PlanningAgent` abstractions are reconciled.
5. Treat document cleanup tasks as complete only when links, module ownership, and build-truth references all agree.

## Remaining follow-through outside these two docs

The plan now points at the correct work, but these repo items still need follow-up execution:

- Update `docs/adr/ADR-001-typed-agent-framework.md` to match the current `AgentType` taxonomy.
- Decide whether `agent-memory` is live, relocated, or pending deletion, then reflect that consistently in docs and build metadata.