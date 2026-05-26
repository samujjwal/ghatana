# Agent Usage Exceptions

Direct agent execution is release-blocked outside narrow adapter/factory internals. Agent event processing must go through `EventOperatorCapability`.

Approved exception surfaces:

| Surface | Scope | Owner | Review/Rationale | Revalidation |
| --- | --- | --- | --- | --- |
| `AgentCapabilityExecutionFactory` | Factory internals only | Data Cloud Action Plane | Builds governed capability execution trees. | Revalidate before every production-ready promotion. |
| `AgentEventOperatorCapabilityAdapter` | Adapter internals only | Data Cloud Action Plane | Bridges agent capabilities into the AEP `EventOperatorCapability` contract. | Revalidate before every production-ready promotion. |
| `GovernedAgentDispatcher` | Dispatcher internals only | Data Cloud Agent Runtime | Central policy, audit, and safety dispatch path. | Revalidate before every production-ready promotion. |
| test fixtures | test source only | Data Cloud Test Owners | Deterministic unit and contract tests. | Revalidate when test-only path patterns change. |

The release audit parses this table directly. Any new exception must include a specific file pattern, owner, review/rationale, and revalidation text before it is accepted by `scripts/audit-agent-usage.mjs`.
