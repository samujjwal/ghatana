# AgentOperatorSpec

**Status:** Target specification  
**Owner:** AEP maintainers
**Schema:** `products/aep/contracts/schemas/agent-operator-spec.schema.json`
**Current code contract:** `products/data-cloud/planes/action/operator-contracts/src/main/java/com/ghatana/core/operator/agent/AgentOperator.java`
**Current replay records:** `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/replay`

`AgentOperatorSpec` defines how agents participate as event operators.

## Required Fields

- `operatorId`
- `operatorKind`
- `agentRef`
- `inputSchema`
- `outputSchema`
- `modelPolicy`
- `toolPolicy`
- `memoryPolicy`
- `retrievalPolicy`
- `replayPolicy`
- `uncertaintyPolicy`
- `guardrails`
- `humanReviewPolicy`
- `observabilityPolicy`

## Operator Kinds

```text
AGENT_PREDICATE
AGENT_ENRICH
AGENT_EXTRACT
AGENT_PATTERN_SYNTHESIS
AGENT_EXPLANATION
AGENT_REVIEW
AGENT_ACTION
AGENT_REFLECTION
```

## Side-Effect Classification

```text
PURE_INFERENCE       # no external side effect
READ_ONLY_TOOL_USE   # can query tools
PROPOSE_ACTION       # emits recommendation/action request
SIDE_EFFECTING       # calls external mutating tool
```

## Contract

An `AgentOperator` extends `EventOperator` and `UnifiedOperator` during migration, and must consume `EventContext`. It emits typed operator results or typed events using the same envelope, metrics, tracing, replay, lifecycle, uncertainty, governance, and security contracts as every other operator.

## Governance Requirements

- Side-effecting agent operators require approval policy.
- Replay metadata is mandatory.
- Tool calls emit audit events.
- Agent outputs are typed events or typed operator results.
- High-risk operators cannot self-approve production changes.
- `AGENT_ACTION` requires tool policy, idempotency key, audit event, and rollback or compensation metadata.

## Replay Requirements

Agent replay policy must state whether replay uses:

- recorded output,
- recorded prompt plus recorded retrieval,
- live model replay,
- blocked replay.

Live model replay is opt-in because model/provider behavior may change over time.

## Current Implementation Notes

The current co-located implementation includes replay records for:

- `AgentExecutionRecord`
- `AgentPromptSnapshot`
- `AgentRetrievalSnapshot`
- `AgentToolCallRecord`
- `AgentOutputRecord`
- `AgentReplayPolicy`

These records require model, prompt, retrieval, tool, and output metadata needed for deterministic replay and audit.
