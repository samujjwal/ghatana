# ADR: Typed Phase Gate Context

**Status**: Accepted  
**Date**: 2026-05-27  
**Applies to**: `YAPPC-P0-014`, `YAPPC-P0-015`

---

## Context

Phase advancement depends on facts from several domains: required artifacts, completed artifacts, evidence availability, governance outcome, preview health, generation health, runtime health, and enabled feature flags. The earlier gate boundary reduced those facts to a string-keyed `Map<String, Boolean>` before validation. That made the contract fragile because new gate inputs could be added as ad hoc keys without an explicit owner, type, or testable surface.

The gate validator still needs condition verdicts because lifecycle stage specs are condition-key based, but those verdicts should be derived from a typed lifecycle context rather than being the only source of truth.

---

## Decision

YAPPC phase gate validation uses `PhaseGateValidator.PhaseGateContext` as the canonical service boundary for phase gate inputs.

The typed context carries:

- Required artifact IDs.
- Completed artifact IDs.
- Evidence availability.
- Governance allow/deny state.
- Preview, generation, and runtime health.
- Enabled lifecycle feature flags.
- Derived condition verdicts for stage-spec compatibility.

`PhasePacketServiceImpl` builds this context from canonical packet inputs and passes it to `PhaseGateValidator.validate(projectId, targetPhase, context)`. The legacy map overload remains only as an adapter for older callers and tests that have not yet moved to the typed boundary.

---

## Consequences

- Gate inputs have a single typed boundary before they are reduced to stage-spec condition keys.
- Tests can capture and assert the actual gate inputs instead of inferring behavior from string maps.
- Required artifact matching stays tied to canonical artifact IDs/types before condition verdicts are evaluated.
- Future gate dimensions must be added to `PhaseGateContext` first, with focused validator and phase packet tests.
- The map-based compatibility overload should not be used by new production code.

---

## Validation

- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/gate/PhaseGateValidator.java`
- `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/phase/PhasePacketServiceImpl.java`
- `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/phase/PhasePacketServiceImplTest.java`
- `products/yappc/docs/YAPPC_BACKLOG_PROGRESS.md`
