# YAPPC Agent Operations

This runbook covers YAPPC governed agent execution state, lifecycle transitions, failure handling, governance visibility, and retention. It complements the generic [agent execution contract](06-agent-execution-contract.md) with the current implementation evidence.

## Runtime Model

| Concern | Current source | Operator surface | Evidence |
| --- | --- | --- | --- |
| Dispatch execution | `AgentExecutorOperator` receives `agent.dispatch.validated` and emits `agent.result.produced`. | Lifecycle/AEP pipeline and downstream result aggregation. | `products/yappc/core/yappc-services/src/main/java/com/ghatana/yappc/services/lifecycle/operators/AgentExecutorOperator.java`; `products/yappc/core/yappc-services/src/test/java/com/ghatana/yappc/services/lifecycle/operators/AgentExecutorOperatorGovernedRuntimeTest.java` |
| Durable execution state | `AgentStateRepository` persists records in `agent-executions`. | Data Cloud execution history and running-execution queries. | `products/yappc/infrastructure/datacloud/src/main/java/com/ghatana/yappc/infrastructure/datacloud/repository/AgentStateRepository.java`; `products/yappc/infrastructure/datacloud/src/test/java/com/ghatana/yappc/infrastructure/datacloud/repository/YappcDataCloudRepositoryTenantEnforcementTest.java` |
| Agent run UI | `AgentRunViewer` shows run status, retry action, timestamps, errors, and AEP lineage. | Observe/Learn/admin surfaces that embed agent run history. | `products/yappc/frontend/web/src/components/agents/AgentRunViewer.tsx`; `products/yappc/frontend/web/src/components/agents/__tests__/AgentRunViewer.test.tsx` |
| Agent monitor UI | `AgentMonitor` shows orchestration progress, agent status, conflicts, retry, resolve, and stop actions. | Multi-agent orchestration monitoring. | `products/yappc/frontend/web/src/components/agents/AgentMonitor.tsx`; `products/yappc/frontend/web/src/components/agents/__tests__/AgentMonitor.test.tsx` |
| Governance health | `AgentGovernanceHealthPanel` renders learning level, governance state, learning evidence, policy blocks, and promotion queue. | Kernel health and Observe/Learn visibility. | `products/yappc/frontend/web/src/pages/kernel-health/AgentGovernanceHealthPanel.tsx`; `products/yappc/frontend/web/src/pages/kernel-health/__tests__/KernelHealthDashboardPage.test.tsx` |

## Execution State

The durable state collection is `agent-executions`. Each record is tenant-scoped and must be accessed through `TenantContext`; the repository rejects missing or `default-tenant` context.

| State | Set by | Meaning | Operator response |
| --- | --- | --- | --- |
| `PENDING` | `AgentStateRepository.create` | Dispatch accepted and execution record created. | Watch for transition to `RUNNING`; investigate stuck records before retrying. |
| `RUNNING` | `AgentStateRepository.markRunning` | Governed workflow step execution is in progress. | Use `findRunning` for live operational view. |
| `SUCCEEDED` | `AgentStateRepository.markSucceeded` | Agent emitted a successful `agent.result.produced` event. | Verify result payload, evidence, and downstream aggregation. |
| `FAILED` | `AgentStateRepository.markFailed` | Agent execution failed or emitted an error result. | Inspect `errorMessage`, AEP lineage, and correlation ID; retry only after cause is understood. |
| `CANCELLED` | `AgentStateRepository.markCancelled` | Execution was cancelled by operator or lifecycle control. | Confirm no downstream action is waiting on the cancelled run. |

Required record fields:

| Field | Purpose | Validation |
| --- | --- | --- |
| `executionId` | Stable UUID for state updates and UI links. | `AgentExecutorOperatorGovernedRuntimeTest` |
| `agentId`, `agentType` | Identifies the SDLC agent and phase-derived type. | `AgentStateRepository.java` |
| `projectId` | Optional project scope. | `AgentStateRepository.java` |
| `status` | Lifecycle state. | `AgentExecutorOperatorGovernedRuntimeTest` |
| `inputPayload` | Dispatch metadata, including tenant and correlation ID. | `AgentExecutorOperator.java` |
| `resultPayload` | Success payload for terminal successful runs. | `AgentStateRepository.java` |
| `errorMessage` | Failure reason for terminal failed runs. | `AgentExecutorOperatorGovernedRuntimeTest` |
| `startedAt`, `updatedAt`, `completedAt`, `durationMs` | Operational timestamps and timing fields. | `AgentStateRepository.java` |

## Lifecycle Flow

1. `AgentExecutorOperator.process` validates `agentId`, tenant, phase transition fields, and correlation ID from `agent.dispatch.validated`.
2. `createExecution` stores a `PENDING` record in `agent-executions` when an `AgentStateRepository` is injected.
3. `markRunning` moves the record to `RUNNING`.
4. `executeAgentCore` dispatches through `WorkflowStepOperatorAdapter` with policy, approval, idempotency key, and audit metadata.
5. `markTerminal` stores `SUCCEEDED` with result metadata when the governed agent succeeds, or `FAILED` with a specific error message when it fails.
6. The operator emits `agent.result.produced` with `status=success` or `status=error` so downstream aggregation can continue with partial results.

## Failure Handling

| Failure | How it appears | Required response | Evidence |
| --- | --- | --- | --- |
| Missing `agentId` | OperatorResult failed with `INVALID_PAYLOAD`. | Fix event producer; do not fabricate an agent ID downstream. | `AgentExecutorOperator.process` |
| Agent system not initialized | `agent.result.produced` with `_error=agent_system_not_initialized`. | Check lifecycle module startup and agent registry initialization before replay. | `AgentExecutorOperator.executeAgentCore` |
| Unknown agent | `agent.result.produced` with `_error=agent_not_found`. | Compare event `agentId` with registered SDLC agents and catalog entries. | `AgentExecutorOperator.executeAgentCore` |
| Governed execution error | Durable state becomes `FAILED`; result event has `status=error`. | Inspect `errorMessage`, correlation ID, policy/approval metadata, and AEP lineage. | `AgentExecutorOperatorGovernedRuntimeTest`, `AgentRunViewer.test.tsx` |
| Data Cloud tenant context missing | Repository throws `SecurityException`. | Fix request/dispatch tenant propagation; never use `default-tenant` as a fallback. | `YappcDataCloudRepositoryTenantEnforcementTest` |

Retry rules:

1. Retry only terminal `FAILED` runs or orchestration conflicts with an explicit operator action.
2. Preserve the original correlation ID in investigation notes and create a new idempotency key for replay unless the upstream contract requires deterministic replay.
3. If failure is `agent_not_found` or `agent_system_not_initialized`, fix registry/startup first; retries without a code or config change should remain blocked.
4. If a retry succeeds, keep the failed record for evidence rather than deleting it.

## Governance Visibility

Agent governance health is surfaced through phase packets and Kernel health UI. Operators should inspect:

| Signal | Meaning | Response |
| --- | --- | --- |
| `learningLevel` (`L0` to `L3`) | How mature the agent learning loop is. | Treat lower levels as requiring closer human review. |
| `governanceState` (`ready`, `requires_approval`, `requires_verification`, `obsolete`, `quarantined`) | Whether the agent can be trusted for autonomous or assisted execution. | Block unsafe use for `obsolete` or `quarantined`; require human approval or verification when indicated. |
| `learningEvidence` | Semantic facts, negative knowledge, and episodic captures. | Use evidence counts to diagnose learning and feedback gaps. |
| `policyBlocks` | Active policy blockers. | Resolve policy or capability issues before rerun. |
| `promotionQueue` | Pending governance promotion work. | Coordinate promotion review with agent owner. |

## Retention

`AgentStateRepository.deleteOlderThan(Instant before)` deletes tenant-scoped `agent-executions` records older than the supplied cutoff. Retention jobs must run under a real tenant context and should be paired with evidence export when records are needed for audits.

Retention rules:

| Rule | Reason | Validation |
| --- | --- | --- |
| Keep terminal failed runs long enough for incident review. | Failure records include error and correlation context. | `AgentRunViewer.test.tsx` |
| Do not delete active `RUNNING` records with a broad cutoff until they are reconciled. | Running records may represent in-flight work or stuck execution. | `AgentStateRepository.findRunning` |
| Run retention per tenant. | The repository enforces tenant context and rejects `default-tenant`. | `YappcDataCloudRepositoryTenantEnforcementTest` |
| Export audit-relevant evidence before purge. | Purged execution records are no longer queryable from Data Cloud. | `AgentStateRepository.deleteOlderThan` |

## Operator Checks

```powershell
./gradlew :products:yappc:core:yappc-services:test --tests "com.ghatana.yappc.services.lifecycle.operators.AgentExecutorOperatorGovernedRuntimeTest" --no-daemon
./gradlew :products:yappc:infrastructure:datacloud:test --tests "com.ghatana.yappc.infrastructure.datacloud.repository.YappcDataCloudRepositoryTenantEnforcementTest" --no-daemon
pnpm -C products/yappc/frontend/web exec vitest run src/components/agents/__tests__/AgentRunViewer.test.tsx src/components/agents/__tests__/AgentMonitor.test.tsx
```

## Change Rules

1. Add new agent states to `AgentStateRepository`, `AgentRunViewer`, tests, and this guide in the same change.
2. Preserve tenant fail-closed behavior for all state reads, writes, and retention deletes.
3. Keep raw prompts, generated content, and sensitive agent inputs out of logs and public UI.
4. Agent retry, stop, conflict resolution, and promotion actions must remain explicit operator actions or backend-governed transitions.
