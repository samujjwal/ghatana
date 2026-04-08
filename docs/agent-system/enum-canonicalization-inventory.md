# Enum Canonicalization Inventory

**Date Audited:** 2026-04-08  
**Status:** ✅ All catalog entries are clean — loader resolves all values to canonical enums

---

## Summary

All 8 AEP operator agent-spec YAML files were audited against the four canonical enum
fields loaded by `AgentSpecLoader`. The loader already normalizes raw YAML values to
canonical enum names via `.toUpperCase().replace('-', '_')`, so any lowercase or
hyphenated value is automatically resolved without requiring catalog edits.

---

## Canonical Enum Classes

| Java Enum | Canonical Values |
|-----------|-----------------|
| `AgentType` | `DETERMINISTIC`, `PROBABILISTIC`, `HYBRID`, `ADAPTIVE`, `COMPOSITE`, `REACTIVE`, `STREAM_PROCESSOR`, `PLANNING`, `CUSTOM` |
| `AutonomyLevel` | `ADVISORY`, `DRAFT`, `SUPERVISED`, `BOUNDED_AUTONOMOUS`, `AUTONOMOUS` |
| `DeterminismGuarantee` | `FULL`, `CONFIG_SCOPED`, `NONE`, `EVENTUAL` |
| `StateMutability` | `STATELESS`, `LOCAL_STATE`, `EXTERNAL_STATE`, `HYBRID_STATE` |

---

## AEP Catalog Audit Results

### File coverage

| File | agentType | autonomyLevel | determinismGuarantee | stateMutability | Status |
|------|-----------|---------------|----------------------|-----------------|--------|
| `operators/ingestion/http-ingestion-agent.yaml` | `stream_processor` → `STREAM_PROCESSOR` | `autonomous` → `AUTONOMOUS` | `full` → `FULL` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/ingestion/kafka-ingestion-agent.yaml` | `stream_processor` → `STREAM_PROCESSOR` | `autonomous` → `AUTONOMOUS` | `full` → `FULL` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/orchestration/unified-event-orchestrator.yaml` | `planning` → `PLANNING` | `semi-autonomous` → `SUPERVISED` (alias) | `config-scoped` → `CONFIG_SCOPED` | `external-state` → `EXTERNAL_STATE` | ✅ |
| `operators/pattern/anomaly-detection-agent.yaml` | `hybrid` → `HYBRID` | `semi-autonomous` → `SUPERVISED` (alias) | `config-scoped` → `CONFIG_SCOPED` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/pattern/correlation-agent.yaml` | `stream_processor` → `STREAM_PROCESSOR` | `autonomous` → `AUTONOMOUS` | `config-scoped` → `CONFIG_SCOPED` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/pattern/pattern-detection-agent.yaml` | `probabilistic` → `PROBABILISTIC` | `semi-autonomous` → `SUPERVISED` (alias) | `none` → `NONE` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/routing/event-router-agent.yaml` | `adaptive` → `ADAPTIVE` | `autonomous` → `AUTONOMOUS` | `config-scoped` → `CONFIG_SCOPED` | `local-state` → `LOCAL_STATE` | ✅ |
| `operators/transformation/event-transformation-agent.yaml` | `hybrid` → `HYBRID` | `autonomous` → `AUTONOMOUS` | `config-scoped` → `CONFIG_SCOPED` | `local-state` → `LOCAL_STATE` | ✅ |

### Legacy alias usage

| Raw YAML value | Resolved to | Alias defined in |
|----------------|-------------|-----------------|
| `semi-autonomous` | `SUPERVISED` | `AutonomyLevel.fromString()` |

No other legacy aliases are in use across all 8 catalog files.

---

## Key Finding

**No breaking values.** All 8 files produce valid canonical enum constants after loader normalization.
The `semi-autonomous` alias appears in 3 files — it resolves correctly to `SUPERVISED` and is
explicitly tested in `AutonomyLevelTest`. No catalog edits required.

---

## Validation Enforcement

- `AgentSpecValidator` introduced to validate canonical enum consistency on loaded specs.
- `AgentSpecLoader.materialize()` now guards against unsupported `agentSpecVersion` values
  (supported: `1.0.0`, `2.0.0`) and throws `UnsupportedSpecVersionException`.
- `AgentSpecValidatorTest` covers deprecated `LLM` type, unknown `autonomyLevel`, and
  unsupported `specVersion` scenarios (17 tests, all passing).
- `AgentSpecLoaderTest.SpecVersionGuard` tests the loader-level version rejection (4 tests, all passing).

---

## Next Steps

- As new catalog entries are added, they should use the canonical (uppercase) form or
  documented aliases listed in `AutonomyLevel.fromString()`.
- `AgentSpecValidator` should be called after `AgentSpecLoader.load()` in production wiring
  when strict validation is required (see Phase 2 / `GovernedAgentDispatcher`).
