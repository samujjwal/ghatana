# Phase 5: E2E Test Execution Roadmap

**Target**: 100+ E2E tests across 5 parallel tracks  
**Timeline**: Weeks 2-6 (Apr 15 - May 24)  
**Velocity**: 18-22 tests per week  
**Status**: READY FOR KICKOFF (Apr 15)

---

## Overview: 5 Parallel Tracks

```
Track 1: Agent Execution Patterns        (25+ tests)
Track 2: Workflow Orchestration         (25+ tests)
Track 3: Data Processing Pipelines      (25+ tests)
Track 4: Cross-Module Integration       (15+ tests)
Track 5: Error Handling & Edge Cases    (10+ tests)
────────────────────────────────────────────────
TOTAL                                    100+ tests
```

All tracks start **Monday April 15** and run in parallel.

---

## Track 1: Agent Execution Patterns (25+ tests)

**Focus**: TypedAgent<I,O> contract and execution flows  
**Base Class**: EventloopTestBase (ActiveJ async)

### Test Categories

#### 1A. Deterministic Agents (6 tests)
```
- Execute deterministic agent → immediate result
- Execute with complex input object → structured output
- Execute same input twice → idempotent result
- Handle deterministic agent failure → error state
- Track execution metrics (latency)
- Batch execute 5 deterministic agents → all succeed
```

**Test Pattern**:
```java
@Test
@DisplayName("Should execute deterministic agent with simple input")
void shouldExecuteDeterministicAgent() {
    runPromise(() -> {
        TypedAgent<String, String> agent = new EchoAgent();
        return agent.execute(new AgentContext(), "test input")
            .thenApply(result -> {
                assertThat(result).isEqualTo("test input");
                return result;
            });
    });
}
```

#### 1B. Probabilistic Agents (6 tests)
```
- Execute probabilistic agent → score in [0.0, 1.0]
- Multiple executions → varying scores (stochastic)
- Confidence accumulation → trending confidence over runs
- Handle probabilistic timeout → return partial confidence
- Compare agents by confidence score
- Validate confidence distribution (histogram)
```

#### 1C. Composite Agents (4 tests)
```
- Execute composite with 3 sub-agents → aggregated score
- Sub-agent failure → composite handles gracefully
- Nested composite (composite → composite) → works
- Composite parallel execution → all sub-agents run
```

#### 1D. Agent Registry (4 tests)
```
- Register and lookup agent by ID
- Update agent definition → immediately active
- List all agents → matches registered
- Deregister agent → not found on lookup
```

#### 1E. Timeout & Lifecycle (5 tests)
```
- Agent initialization timeout → fails gracefully
- Agent execution timeout → returns timeout status
- Agent shutdown → releases resources
- Agent restart → clean state
- Concurrent shutdown → no race condition
```

**Total Track 1**: 25 tests, ~32 hours programming (1.3 tests/hour)

---

## Track 2: Workflow Orchestration (25+ tests)

**Focus**: Workflow execution engine, DAG topology, transitions

### Test Categories

#### 2A. Sequential Workflows (6 tests)
```
- Linear 3-step workflow → completes all steps
- Step output → input to next step (data flow)
- Step failure → stops workflow
- Resume from checkpoint → skips completed steps
- Timing: step 1 (100ms) → step 2 (50ms) → total ~150ms
- Large payload flow → 10MB data through workflow
```

#### 2B. Conditional Workflows (6 tests)
```
- If condition true → take path A
- If condition false → take path B
- Nested if/else → complex routing
- Condition evaluation error → default path
- Multiple conditions (AND/OR) → correct path
- Dynamic condition based on step output → routes correctly
```

#### 2C. Retry & Error Recovery (5 tests)
```
- Step fails once → retry succeeds (flaky)
- Retry exhaustion → workflow fails
- Backoff strategy (exponential) → correct delays
- Circuit breaker: N failures → open circuit
- Graceful degradation: fallback step runs
```

#### 2D. State & Checkpointing (4 tests)
```
- Workflow state persisted → resume from crash
- Checkpoint every step → minimal replay
- Partial state recovery → no duplicate processing
- Long workflow (20 steps) → all state consistent
```

#### 2E. Metrics & Observability (4 tests)
```
- Step execution latency tracked
- Workflow throughput measured (workflows/sec)
- Error rate per step
- Complete trace captured (correlation ID)
```

**Total Track 2**: 25 tests, ~32 hours programming

---

## Track 3: Data Processing Pipelines (25+ tests)

**Focus**: Event sources, sinks, transformations, backpressure

### Test Categories

#### 3A. Read Operations (6 tests)
```
- Read from cache → immediate (hot)
- Read from cache → miss → fallback to source
- Read with TTL → expired data triggers refresh
- Batch read (1000 items) → all returned ordered
- Read during write → consistent snapshot
- Read timeout → returns partial results
```

#### 3B. Write Operations (6 tests)
```
- Write to cache → immediately readable
- Write batch (1000 items) → all persisted
- Write with validation → invalid items rejected
- Write with deduplication → no duplicates
- Write during read → isolation respected
- Write timeout → retry with backoff
```

#### 3C. Pipeline Transformations (6 tests)
```
- Map transformation (uppercase) → correct output
- Filter transformation (>100) → only matches pass
- FlatMap (expand 1→3) → correct cardinality
- Chained transformations → all applied
- Transformation error → stops pipeline
- Parallel transformation with 4 threads → all transform
```

#### 3D. Event Sink Operations (4 tests)
```
- Sink to Kafka → event delivered
- Sink batching (100 items) → one batch published
- Sink backpressure → slows when full
- Sink retry on failure → eventually succeeds
```

#### 3E. Error Recovery (3 tests)
```
- Dead letter queue → bad events collected
- Poison pill detection → stops processing
- Quarantine & analyze → re-process known issue
```

**Total Track 3**: 25 tests, ~32 hours programming

---

## Track 4: Cross-Module Integration (15+ tests)

**Focus**: Agent → Workflow → Data interactions

### Test Categories

#### 4A. Agent to Workflow (4 tests)
```
- Agent inside workflow step → correct input/output
- Workflow produces agent input → agent consumes
- Agent failure stops workflow → correct
- Agent async execution → workflow waits
```

#### 4B. Workflow to Data (4 tests)
```
- Workflow reads data → uses in decision
- Workflow writes data → persisted for agents
- Data transformation in workflow → correct
- Workflow coordinate via shared data → consistent
```

#### 4C. Agent to Data (4 tests)
```
- Agent read → uses live data
- Agent write → data queryable after
- Agent cache → reuses recent data
- Agent data migration → old data upgraded
```

#### 4D. Full E2E: Agent → Workflow → Data (3 tests)
```
- Create workflow of 3 agents → all execute
- Agent 1 writes → Agent 2 reads → Agent 3 processes
- Failure in middle → all clean up, no partial state
```

**Total Track 4**: 15 tests, ~20 hours programming

---

## Track 5: Error Handling & Edge Cases (10+ tests)

**Focus**: Resilience, timeouts, resource limits

### Test Categories

#### 5A. Timeout Scenarios (4 tests)
```
- Agent timeout (configured: 1s) → returns timeout status
- Workflow timeout (configured: 5s) → stops at timeout
- Data read timeout → fallback mechanism
- Multiple concurrent timeouts → all handle independently
```

#### 5B. Resource Exhaustion (3 tests)
```
- Memory pressure → graceful degradation
- Thread pool exhausted → queues, eventually processes
- Connection pool full → waits, times out if configured
```

#### 5C. Failure Propagation (3 tests)
```
- Agent fails → workflow captures error
- Workflow fails → triggers cleanup
- Cascade failure (A fails → B fails) → all logged
```

**Total Track 5**: 10 tests, ~13 hours programming

---

## Implementation Plan: Weeks 2-6

### Week 2 (Apr 15-19): Kickoff
- Start Track 1 (18-20 deterministic + probabilistic agent tests)
- Start Track 5 (4-5 timeout tests)
- Infrastructure: EventloopTestBase patterns established
- **Deliverables**: 22 tests, template examples, test double library

### Week 3 (Apr 22-26): Acceleration
- Complete Track 1 (5-7 remaining tests)
- Start Track 2 (16-18 workflow tests)
- Start Track 3 (6-8 data tests)
- **Deliverables**: 22 tests (cumulative 44)

### Week 4 (Apr 29-May 3): Parallel Execution
- Complete Track 2 (7-9 remaining tests)
- Continue Track 3 (12-14 more tests)
- Start Track 4 (4-6 integration tests)
- **Deliverables**: 22 tests (cumulative 66)

### Week 5 (May 6-10): Data & Integration
- Complete Track 3 (11-13 remaining tests)
- Continue Track 4 (9-11 remaining)
- Start error case expansion
- **Deliverables**: 20 tests (cumulative 86)

### Week 6 (May 13-17): Completion & Polish
- Complete Track 4 (4-6 remaining)
- Complete Track 5 edge cases (5-7 remaining)
- Cross-module integration validation
- **Deliverables**: 14+ tests (cumulative 100+), all passing

### Week 7 (May 20-24): Validation
- Run full test suite: 100+ tests
- Performance baseline (avg 200ms per test = 20 minute suite)
- Coverage analysis
- Final governance test pass

---

## Test Infrastructure Requirements

### Base Classes (Already exist)
- `EventloopTestBase` - ActiveJ async test execution
- `PlatformIntegrationTestBase` - Multi-component setup
- `TestClock` - Deterministic time control

### Test Doubles Needed (Create Week 1-2)
```java
// Agent test doubles
TestDeterministicAgent(String id, Function<Input, Output> transform)
TestProbabilisticAgent(String id, double confidence)
TestCompositeAgent(Agent... subAgents)

// Workflow test doubles
TestWorkflow(String id, WorkflowDefinition def)
TestWorkflowStep(String stepId, Function<Input, Output> logic)

// Data test doubles
TestDataSource(Collection<Record> data)
TestDataSink(Collection<Record> collected)
TestEventPublisher(List<Event> events)
```

### Container Fixtures (Already available)
- PostgreSQL 14 (via TestContainers)
- Redis 7 (via TestContainers)
- Kafka 7 (via TestContainers)

---

## Success Criteria (Week 6+)

- ✅ 100+ tests passing (zero failures)
- ✅ Average execution time < 250ms per test (total suite < 30 min)
- ✅ Code coverage > 85% for tested modules (Agent, Workflow, Data)
- ✅ All tests reproducible locally and in CI
- ✅ Clean compilation (zero warnings)
- ✅ Test doubles + integration examples documented

---

## Resource Allocation

| Track | Engineer | Week 2-6 Hours | Weekly Velocity |
|-------|----------|----------------|-----------------|
| 1: Agents | Engineer A | 32h | 6.4h/week |
| 2: Workflows | Engineer B | 32h | 6.4h/week |
| 3: Data | Engineer C | 32h | 6.4h/week |
| 4: Integration | Engineers A+B+C | 20h | 4h/week |
| 5: Error Cases | Engineers A+B+C | 13h | 2.6h/week |

**Total**: 3 engineers, ~129 hours across 5 weeks = committed path to 100+ tests

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Test doubles complex | Create library early (Week 1) |
| EventLoop unfamiliar | Training + examples Week 1 |
| Timeout flakiness | Use TestClock + deterministic time |
| Resource limits | Run in ResourceLimited container |
| Large test suite slow | Parallel execution (gradle -P parallel) |

---

## Weekly Checkpoints

- **Week 2 Thu (Apr 18)**: 22 tests, all passing, template examples
- **Week 3 Thu (Apr 25)**: 44 tests running, pattern proven
- **Week 4 Thu (May 2)**: 66 tests, cross-module working
- **Week 5 Thu (May 9)**: 86 tests, edge cases covered
- **Week 6 Thu (May 16)**: 100+ tests, ready for validation

---

## Deliverables by Week

| Week | Tests Delivered | Key Milestones |
|------|-----------------|-----------------|
| W2 | 22 | Agent fixtures, Workflow basics |
| W3 | 22 | Workflow complete, Data started |
| W4 | 22 | Data pipelines, Integration begun |
| W5 | 20 | Cross-module flows working |
| W6 | 14+ | Error handling, full suite ready |
| **TOTAL** | **100+** | **Production-ready test coverage** |

---

## Execution Commands (Week 2+)

```bash
# Run all E2E tests
./gradlew platform:java:*:test -x test --categories E2E

# Run specific track
./gradlew platform:java:agent-core:test -x test --categories E2E_AGENTS

# Run with parallel execution
./gradlew test -P parallel --max-workers=4 -x test --categories E2E

# Generate test report
./gradlew testReport --categories E2E
# View: build/reports/tests/e2eReport/index.html
```

---

## Sign-Off: Ready for Monday April 15 Kickoff

- ✅ Roadmap documented (this file)
- ✅ Track assignments defined
- ✅ Test doubles identified
- ✅ Success criteria clear
- ✅ Resource commitment established
- ✅ Risk mitigations in place

**Next**: Create test double library Week 1, start Track 1 & 5 Week 2.

**Status**: 🚀 **READY FOR EXECUTION**
