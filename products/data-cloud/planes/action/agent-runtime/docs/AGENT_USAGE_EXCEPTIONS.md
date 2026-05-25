# Agent Usage Exceptions

Direct agent execution is release-blocked outside narrow adapter/factory internals. Agent event processing must go through `EventOperatorCapability`.

Approved exception surfaces:

| Surface | Scope | Reason |
| --- | --- | --- |
| `AgentCapabilityExecutionFactory` | Factory internals only | Builds governed capability execution trees. |
| `AgentEventOperatorCapabilityAdapter` | Adapter internals only | Bridges agent capabilities into the AEP `EventOperatorCapability` contract. |
| `GovernedAgentDispatcher` | Dispatcher internals only | Central policy, audit, and safety dispatch path. |
| test fixtures | test source only | Deterministic unit and contract tests. |

Any new exception requires a specific file pattern, owner review, and a removal or revalidation rationale before it can be added to `scripts/audit-agent-usage.mjs`.
