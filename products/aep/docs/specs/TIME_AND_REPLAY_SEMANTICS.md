# Time and Replay Semantics

**Status:** Target specification  
**Owner:** AEP maintainers
**Current code contracts:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/aep/model/EventTimeContext.java`, `ReplayContext.java`, `TimePolicyEvaluator.java`

## Time Concepts

- `eventTime`: when the event happened at the source.
- `processingTime`: when AEP processed the event.
- `detectionTime`: when AEP detected or emitted a match.
- `intervalTime`: start and end time for interval events.
- `watermark`: progress marker for event-time completeness.
- `allowedLateness`: how long late events may still affect a pattern.
- `partialMatchState`: stored in-flight state for patterns that have begun but not completed.

## Pattern Requirements

Every pattern specifies:

- time mode,
- allowed lateness,
- late-event behavior,
- partial-match expiry,
- replay policy,
- watermark behavior when using event time.

## Late Event Handling

Allowed behaviors:

```text
incorporate
compensate
degrade_confidence
emit_correction
reject_to_dlq
```

## Replay Determinism

Replay must be deterministic for standard operators when the input event log, PatternSpec, operator versions, and configuration are fixed.

Agent operators require explicit replay policy because models, prompts, retrieval stores, and tools can change.

## Agent Replay Policy

Supported modes:

```text
recorded_output
recorded_prompt_and_retrieval
live_model_opt_in
blocked
```

Replay can run in recorded-output mode for agent calls. Live model replay is opt-in and must record model, prompt, retrieval, tool, and guardrail versions.
