# Phase 7: GovernedAgentDispatcher Decomposition Plan

## Current State

`GovernedAgentDispatcher` is a large class (1143 lines) with multiple responsibilities:

### Current Responsibilities

From the class documentation and implementation:

- **Release guard**: Rejects dispatch if AgentRelease is not response-serving
- **Grant validation**: Verifies execution grant is valid before dispatch
- **Invariant monitoring**: Evaluates pre-dispatch invariants
- **Mastery checks**: Validates mastery state compatibility
- **Version context checks**: Validates version compatibility
- **Task classification**: Classifies task risk and novelty
- **Mode selection**: Selects appropriate execution mode
- **Trace recording**: Appends evidence to trace ledger
- **OTel tracing**: Emits structured lifecycle spans

### Current Dependencies

- `AgentDispatcher` (delegate)
- `InvariantMonitor`
- `AgentTraceLedger`
- `AgentReleaseRepository`
- `AgentRunTracer`
- `AgentCapabilityManifest`
- `MasteryRegistry`
- `VersionContextResolver`
- `TaskClassifier`
- `MasteryAwareModeSelector`
- `MemoryRetriever`

## Target State

According to the implementation tracker, decompose into:

```text
AgentReleaseGate
AgentGrantGate
AgentInvariantGate
AgentMasteryGate
AgentVersionGate
AgentModeSelectionGate
AgentMemoryRetrievalStage
AgentTraceStage
AgentDispatchPipeline
```

## Implementation Tasks

### 1. Create AgentReleaseGate

**Status**: Pending

**Responsibility**: Rejects dispatch if AgentRelease is not response-serving

**Implementation**:

- Extract release check logic from GovernedAgentDispatcher
- Create `AgentReleaseGate` class
- Add release gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- `AgentReleaseRepository`
- `AgentInstanceConfig`

### 2. Create AgentGrantGate

**Status**: Pending

**Responsibility**: Verifies execution grant is valid before dispatch

**Implementation**:

- Extract grant validation logic from GovernedAgentDispatcher
- Create `AgentGrantGate` class
- Add grant gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- Execution grant validation logic

### 3. Create AgentInvariantGate

**Status**: Pending

**Responsibility**: Evaluates pre-dispatch invariants

**Implementation**:

- Extract invariant monitoring logic from GovernedAgentDispatcher
- Create `AgentInvariantGate` class
- Add invariant gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- `InvariantMonitor`

### 4. Create AgentMasteryGate

**Status**: Pending

**Responsibility**: Validates mastery state compatibility

**Implementation**:

- Extract mastery check logic from GovernedAgentDispatcher
- Create `AgentMasteryGate` class
- Add mastery gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- `MasteryRegistry`
- `MasteryQuery`

### 5. Create AgentVersionGate

**Status**: Pending

**Responsibility**: Validates version compatibility

**Implementation**:

- Extract version context check logic from GovernedAgentDispatcher
- Create `AgentVersionGate` class
- Add version gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- `VersionContextResolver`
- `VersionContext`
- `DependencyFingerprint`
- `EnvironmentSnapshot`
- `RuntimeFingerprint`

### 6. Create AgentModeSelectionGate

**Status**: Pending

**Responsibility**: Selects appropriate execution mode

**Implementation**:

- Extract mode selection logic from GovernedAgentDispatcher
- Create `AgentModeSelectionGate` class
- Add mode selection gate tests
- Integrate with dispatch pipeline

**Dependencies**:

- `TaskClassifier`
- `MasteryAwareModeSelector`
- `InteractionMode`

### 7. Create AgentMemoryRetrievalStage

**Status**: Pending

**Responsibility**: Retrieves memory for agent execution

**Implementation**:

- Extract memory retrieval logic from GovernedAgentDispatcher
- Create `AgentMemoryRetrievalStage` class
- Add memory retrieval stage tests
- Integrate with dispatch pipeline

**Dependencies**:

- `MemoryRetriever`

### 8. Create AgentTraceStage

**Status**: Pending

**Responsibility**: Records trace events and OTel spans

**Implementation**:

- Extract trace recording logic from GovernedAgentDispatcher
- Create `AgentTraceStage` class
- Add trace stage tests
- Integrate with dispatch pipeline

**Dependencies**:

- `AgentTraceLedger`
- `AgentRunTracer`
- `TraceEventBuilder`
- `HashChainedTraceAppender`

### 9. Create AgentDispatchPipeline

**Status**: Pending

**Responsibility**: Orchestrates all gates and stages in sequence

**Implementation**:

- Create `AgentDispatchPipeline` class
- Compose all gates and stages
- Add pipeline tests
- Replace GovernedAgentDispatcher with pipeline

**Dependencies**:

- All gates and stages
- `AgentDispatcher` (delegate)

### 10. Update GovernedAgentDispatcher

**Status**: Pending

**Responsibility**: Update to use new pipeline

**Implementation**:

- Update GovernedAgentDispatcher to delegate to AgentDispatchPipeline
- Maintain backward compatibility during transition
- Add deprecation notice
- Update all call sites
- Remove old implementation after transition

## Exit Criteria

- [ ] All gates are extracted and tested
- [ ] All stages are extracted and tested
- [ ] AgentDispatchPipeline composes gates and stages
- [ ] GovernedAgentDispatcher uses new pipeline
- [ ] All tests pass
- [ ] Backward compatibility is maintained during transition

## Dependencies

- Phase 6: Operator runtime migration (completed)
- Phase 8: Production release gates (pending)

## Related Files

- `products/data-cloud/planes/action/agent-runtime/src/main/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcher.java`
- `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherTest.java`
- `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedDispatcherArchTest.java`
- `products/data-cloud/planes/action/agent-runtime/src/test/java/com/ghatana/agent/runtime/safety/GovernedAgentDispatcherMasteryTest.java`

## Notes

GovernedAgentDispatcher is a well-structured but large class. The decomposition should:

1. Extract each responsibility into a separate class
2. Maintain the same behavior during transition
3. Add comprehensive tests for each component
4. Use composition to rebuild the pipeline
5. Ensure backward compatibility during the transition

This is a significant refactoring that should be done incrementally with each extraction having corresponding tests.
