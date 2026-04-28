# ADR-024: Data Cloud AI Action Record — Required Evidence Model for Every Suggestion

**Status:** Accepted  
**Date:** 2026-04-28  
**Decision Makers:** Data Cloud Platform Team  
**Phase:** 2 — Automation and AI-Native Differentiation  

## Context

Data Cloud generates AI-driven suggestions across multiple surfaces (entity suggestions, analytics automation, pipeline drafts, workflow advisories, quality checks). Without a unified action record, these suggestions are opaque: we cannot audit what was proposed, by which model, with what confidence, and what the operator ultimately decided. This opacity prevents trust, learning, and regulatory compliance.

## Decision

Every AI-generated suggestion or automated action **must** be recorded as an immutable `AiActionRecord` with a required evidence envelope.

### AiActionRecord Schema

| Field | Type | Required | Description |
|---|---|---|---|
| `actionId` | UUID | Yes | Unique identifier for this action |
| `tenantId` | String | Yes | Scoped to the requesting tenant |
| `domain` | String | Yes | Capability domain: `query`, `pipeline`, `entity`, `alert`, `quality`, `autonomy`, `governance` |
| `intent` | String | Yes | Human-readable description of the intent |
| `inputs` | Object | Yes | The input that triggered the suggestion (query text, pipeline config, schema) |
| `model` | Object | Yes | `{provider, name, version}` of the model used |
| `confidence` | Object | Yes | `{score: 0.0-1.0, band: "low"|"medium"|"high", reason: "heuristic"|"llm"|"ensemble"}` |
| `risk` | Object | Yes | `{level: "low"|"medium"|"high", reasons: [...]}` |
| `decision` | Object | Yes | `{mode: "suggest"|"confirm"|"notify"|"autonomous", recommendedAction, alternatives: [...]}` |
| `provenance` | Object | Yes | `{handlerClass, handlerMethod, requestId, timestamp, latencyMs}` |
| `outcome` | Object | No | `{applied: bool, appliedBy, appliedAt, feedback, correction}` — backfilled when operator acts |

### Evidence Model

Every record must carry at least one of these evidence types:

1. **Heuristic Trace**: The exact rules, thresholds, and matching conditions that produced the suggestion.
2. **LLM Prompt & Response**: The prompt sent to the model, the raw response, and any post-processing applied.
3. **Ensemble Vote**: When multiple models or heuristics disagree, the vote distribution and tie-breaking logic.

### Storage and Audit

- **Persistence**: All records are written to `dc_ai_actions` collection via `DataCloudClient.save()`.
- **Event Log**: Each record is also emitted as an `ai.action` event to the tenant event log via `DataCloudClient.appendEvent()` for downstream audit and compliance reporting.
- **Retention**: `dc_ai_actions` retention is 90 days by default, configurable per tenant via `dc_memory_retention_policies`.

### Enforcement

- `AiAssistHandler.recordAiAction()` is the central helper. All AI endpoint handlers (entity-suggest, analytics-suggest, analytics-automate, pipeline-draft, pipeline-refine, pipeline-hint, brain-explain, ai-suggestions, ai-workflow-advisory, ai-quality-advisory, ai-fabric-advisory) invoke it in both LLM-backed and heuristic fallback paths.
- Missing `recordAiAction()` calls in a handler are caught by CI static analysis (grep for `recordAiAction` in every handler class).

## Consequences

- **Positive**: Complete audit trail of every AI decision; supports compliance (SOC2, GDPR Article 5(2) accountability).
- **Positive**: Learning loop can correlate action records with feedback to improve model selection and confidence calibration.
- **Negative**: Every AI endpoint incurs an extra database write; latency increases by ~5-15ms.
- **Negative**: Storage overhead of `dc_ai_actions` can be significant for high-throughput tenants; retention policies must be actively managed.

## Related

- `AiAssistHandler.recordAiAction()` — central helper method
- `docs/adr/ADR-021-data-cloud-autonomy.md` — autonomy levels determine `decision.mode`
- `docs/adr/ADR-023-data-cloud-sovereign-profile.md` — model consent gate before external LLM calls
