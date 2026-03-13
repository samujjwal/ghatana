# Specialized TDD Prompt: K-05 Event Bus

Use this prompt together with [tdd_test_spec_generation_prompt_v1.md](tdd_test_spec_generation_prompt_v1.md).

Execution-plan position:
- Phase 1
- Build order position: 1

Primary sources:
- [../CURRENT_EXECUTION_PLAN.md](../CURRENT_EXECUTION_PLAN.md)
- [../LLD_K05_EVENT_BUS.md](../LLD_K05_EVENT_BUS.md)
- [../ADR-009_EVENT_BUS_TECHNOLOGY.md](../ADR-009_EVENT_BUS_TECHNOLOGY.md)
- [../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md](../ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md)

---

## Prompt Addendum

```text
Apply the base Siddhanta TDD prompt, then apply these K-05-specific overrides.

## Scope

Target scope:
- canonical event envelope
- append-only event store
- event publication and subscription
- schema validation
- replay
- idempotency
- ordering guarantees per aggregate
- saga orchestration surface

Default source paths:
- LLD_K05_EVENT_BUS.md
- ADR-009_EVENT_BUS_TECHNOLOGY.md
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- CURRENT_EXECUTION_PLAN.md

## Goal

Produce a test specification that can drive implementation of K-05 from zero while proving immutable event storage, per-aggregate ordering, schema compatibility, replay correctness, delivery guarantees, and safe saga orchestration boundaries.

## Mandatory Behavior Areas

You must explicitly decompose and test:
- event envelope field validation
- required metadata fields
- dual-calendar timestamp requirements
- event ID generation
- sequence number generation
- aggregate ordering guarantees
- append-only persistence
- schema registration and lookup behavior
- schema version validation
- backward-compatible schema evolution
- publish API success and failure modes
- subscription behavior
- replay APIs
- projection rebuild behavior
- duplicate publish/idempotency handling
- broker failure and retry behavior
- consumer failure and reprocessing behavior
- correlation_id and causation_id propagation
- tenant isolation in event storage and streaming
- saga start, step progression, compensation trigger, timeout, and terminal states
- K-05/K-07 integration boundary assumptions

## Mandatory Scenario Families

You must enumerate, at minimum:
- valid publish with full envelope
- publish missing required field
- publish invalid payload shape
- publish unknown schema
- publish incompatible schema version
- duplicate publish same idempotency key
- concurrent publish same aggregate
- concurrent publish different aggregates
- out-of-order event attempt
- replay from beginning
- replay from checkpoint
- replay bounded date range
- replay with corrupted event record
- replay after schema evolution
- subscriber starts from latest
- subscriber starts from earliest
- subscriber disconnect and resume
- consumer processing failure then retry success
- consumer processing failure then DLQ/escalation handoff
- saga success path
- saga compensating path
- saga timeout
- saga duplicate start
- tenant A cannot read tenant B events
- audit hook absent during bootstrap phase
- audit hook enabled after K-07 availability

## Expected Outputs To Force

For every test case, specify exact expected:
- HTTP/gRPC responses
- persisted event rows
- sequence numbers
- topic/partition assignment assumptions if relevant
- replay streams
- emitted internal events
- no-mutation guarantees on stored events
- audit side effects or explicit absence during bootstrap mode
- metrics such as publish latency, replay counts, consumer lag indicators
- failure/error codes

## Coverage Additions

In addition to the base matrices, include:
- envelope field coverage matrix
- schema-evolution coverage matrix
- ordering coverage matrix
- replay coverage matrix
- idempotency coverage matrix
- saga-state coverage matrix

## TDD Sequencing

Order the tests first as:
1. pure envelope and schema validator unit tests
2. event store append/read tests
3. publish-path contract tests
4. ordering/idempotency tests
5. replay tests
6. subscriber integration tests
7. saga orchestration tests
8. performance and failure-path tests

Respect the execution plan note:
- ship K-05 core bus/event store first
- do not require K-07 callback enforcement in the initial green path
- add K-05 to K-07 hooks only after K-07 is available
```
