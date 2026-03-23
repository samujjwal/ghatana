# TDD Test Specification for K-05 Event Bus

**Document Version:** 2.1  
**Date:** March 10, 2026  
**Status**: Implementation-Ready Test Specification  
**Scope**: K-05 Event Bus - Canonical event envelope, append-only store, pub/sub, replay, saga orchestration

---

## 1. Scope Summary

**In Scope:**
- Canonical event envelope with dual-calendar timestamps
- Append-only event store with per-aggregate ordering
- Event publication and subscription APIs
- Schema registration, validation, and evolution
- Event replay and projection rebuild
- Idempotency and duplicate handling
- Saga orchestration surface integration
- Tenant isolation in event storage and streaming
- Broker failure and retry behavior
- Consumer failure and reprocessing

**Out of Scope:**
- Full audit framework integration (K-07)
- Complete saga compensation engine (K-17)
- Advanced monitoring and alerting
- Multi-region replication

**Authority Sources Used:**
- LLD_K05_EVENT_BUS.md
- ADR-009_EVENT_BUS_TECHNOLOGY.md
- ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md
- CURRENT_EXECUTION_PLAN.md
- Architecture specification parts 1-3

**Assumptions:**
- Kafka 3+ as primary event streaming backbone
- PostgreSQL 15+ for event store append log
- Schema Registry for Avro/Protobuf schemas
- Java 21 + ActiveJ for K-05 implementation
- Ghatana event processing platform reuse

---

## 2. Source Inventory

| source_id | path | authority | why_it_matters | extracted_behaviors |
|-----------|------|-----------|----------------|-------------------|
| LLD_K05_001 | LLD_K05_EVENT_BUS.md | Primary | Low-level design for K-05 | Event envelope, store, pub/sub, saga |
| ADR_009_001 | ADR-009_EVENT_BUS_TECHNOLOGY.md | Primary | Technology choices for event bus | Kafka, PostgreSQL, Schema Registry |
| ADR_011_001 | ADR-011_STACK_STANDARDIZATION_AND_GHATANA_PLATFORM_ALIGNMENT.md | Primary | Stack baseline and Ghatana alignment | Technology stack, platform reuse |
| EXEC_PLAN_001 | CURRENT_EXECUTION_PLAN.md | Primary | Build order and dependencies | Phase 1 positioning, K-07 integration |
| ARCH_SPEC_001 | ARCHITECTURE_SPEC_PART_1_SECTIONS_1-3.md | Secondary | Core architecture patterns | Event-driven architecture, CQRS |

---

## 3. Behavior Inventory

### Group: Event Envelope
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| EE_001 | Event Envelope | VALIDATE_REQUIRED_FIELDS | Validate all required envelope fields present | LLD_K05_001 |
| EE_002 | Event Envelope | VALIDATE_FIELD_TYPES | Validate field data types and formats | LLD_K05_001 |
| EE_003 | Event Envelope | GENERATE_EVENT_ID | Generate unique event identifiers | LLD_K05_001 |
| EE_004 | Event Envelope | GENERATE_SEQUENCE_NUM | Generate sequence numbers per aggregate | LLD_K05_001 |
| EE_005 | Event Envelope | DUAL_CALENDAR_TIMESTAMPS | Handle BS and Gregorian timestamps | ARCH_SPEC_001 |

### Group: Schema Management
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| SM_001 | Schema Registry | REGISTER_SCHEMA | Register new event schema | ADR_009_001 |
| SM_002 | Schema Registry | VALIDATE_SCHEMA | Validate event against schema | LLD_K05_001 |
| SM_003 | Schema Registry | EVOLVE_SCHEMA | Handle backward-compatible schema evolution | LLD_K05_001 |
| SM_004 | Schema Registry | REJECT_INCOMPATIBLE | Reject incompatible schema changes | LLD_K05_001 |

### Group: Event Store
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| ES_001 | Event Store | APPEND_EVENT | Append event to immutable log | LLD_K05_001 |
| ES_002 | Event Store | READ_EVENTS | Read events by aggregate ID | LLD_K05_001 |
| ES_003 | Event Store | ENFORCE_ORDERING | Enforce per-aggregate ordering | LLD_K05_001 |
| ES_004 | Event Store | MAINTAIN_APPEND_ONLY | Ensure append-only semantics | LLD_K05_001 |

### Group: Publication
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| PB_001 | Publisher | PUBLISH_EVENT | Publish event to Kafka topic | ADR_009_001 |
| PB_002 | Publisher | HANDLE_DUPLICATE | Handle duplicate publish attempts | LLD_K05_001 |
| PB_003 | Publisher | PROPAGATE_CORRELATION | Propagate correlation and causation IDs | LLD_K05_001 |
| PB_004 | Publisher | TENANT_ISOLATION | Enforce tenant isolation in publishing | LLD_K05_001 |

### Group: Subscription
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| SB_001 | Subscriber | SUBSCRIBE_TO_TOPIC | Subscribe to event topic | ADR_009_001 |
| SB_002 | Subscriber | HANDLE_RECONNECT | Handle disconnect and resume | LLD_K05_001 |
| SB_003 | Subscriber | PROCESS_EVENT | Process received event | LLD_K05_001 |
| SB_004 | Subscriber | HANDLE_FAILURE | Handle processing failures | LLD_K05_001 |

### Group: Replay
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| RP_001 | Replay Service | REPLAY_FROM_START | Replay all events from beginning | LLD_K05_001 |
| RP_002 | Replay Service | REPLAY_FROM_CHECKPOINT | Replay from specific checkpoint | LLD_K05_001 |
| RP_003 | Replay Service | REPLAY_DATE_RANGE | Replay events within date range | LLD_K05_001 |
| RP_004 | Replay Service | REBUILD_PROJECTION | Rebuild projection from events | LLD_K05_001 |

### Group: Saga Orchestration
| group_id | module/service | behavior_id | description | source_refs |
|----------|----------------|-------------|-------------|-------------|
| SG_001 | Saga Orchestrator | START_SAGA | Initiate new saga instance | LLD_K05_001 |
| SG_002 | Saga Orchestrator | PROGRESS_STEP | Advance saga to next step | LLD_K05_001 |
| SG_003 | Saga Orchestrator | TRIGGER_COMPENSATION | Trigger compensation actions | LLD_K05_001 |
| SG_004 | Saga Orchestrator | HANDLE_TIMEOUT | Handle saga timeout | LLD_K05_001 |

---

## 4. Risk Inventory

| risk_id | description | severity | impacted_behaviors | required_test_layers |
|---------|-------------|----------|-------------------|---------------------|
| RISK_001 | Event envelope corruption breaks downstream processing | High | EE_001-005, PB_001-004 | Unit, Integration |
| RISK_002 | Schema evolution breaks compatibility | High | SM_001-004, PB_001-004 | Contract, Integration |
| RISK_003 | Event store ordering violations break consistency | High | ES_001-004, PB_001-004 | Integration, Resilience |
| RISK_004 | Duplicate events cause side effects | Medium | PB_002, SB_001-004 | Integration, E2E |
| RISK_005 | Saga compensation fails leaving inconsistent state | High | SG_001-004 | Integration, Resilience |
| RISK_006 | Tenant isolation breach exposes cross-tenant data | High | PB_004, ES_001-004 | Security, Integration |

---

## 5. Test Strategy by Layer

### Unit Tests
- **Purpose**: Validate envelope validation, schema management, ID generation
- **Tools**: JUnit 5, Mockito, Testcontainers
- **Coverage Goal**: 100% statement and branch coverage
- **Fixtures Required**: Sample envelopes, schema definitions
- **Exit Criteria**: All unit tests pass, validation logic correct

### Component Tests
- **Purpose**: Validate event store operations, publisher/subscriber components
- **Tools**: Testcontainers, embedded Kafka, PostgreSQL
- **Coverage Goal**: 100% component interaction scenarios
- **Fixtures Required**: Test events, database schemas
- **Exit Criteria**: All components work in isolation

### Contract Tests
- **Purpose**: Validate API contracts, schema compatibility
- **Tools**: Pact, OpenAPI validator, schema registry
- **Coverage Goal**: 100% contract validation scenarios
- **Fixtures Required**: Contract definitions, schema files
- **Exit Criteria**: All contracts validated

### Integration Tests
- **Purpose**: Validate end-to-end event flow, replay, saga orchestration
- **Tools**: Docker Compose, Kafka, PostgreSQL
- **Coverage Goal**: 100% critical integration paths
- **Fixtures Required**: Full test environment
- **Exit Criteria**: Event flow works end-to-end

### Resilience Tests
- **Purpose**: Validate failure handling, retries, compensation
- **Tools**: Chaos Monkey, failure injection
- **Coverage Goal**: 100% failure scenarios
- **Fixtures Required**: Failure simulation framework
- **Exit Criteria**: System handles failures gracefully

---

## 6. Granular Test Catalog

### Test Cases for Event Envelope

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| EE_TC_001 | Validate complete valid envelope | Event Envelope | LLD_K05_001 | Unit | Happy Path | High |
| EE_TC_002 | Reject envelope missing event_type | Event Envelope | LLD_K05_001 | Unit | Validation Failure | High |
| EE_TC_003 | Reject envelope missing aggregate_id | Event Envelope | LLD_K05_001 | Unit | Validation Failure | High |
| EE_TC_004 | Reject invalid timestamp format | Event Envelope | ARCH_SPEC_001 | Unit | Validation Failure | High |
| EE_TC_005 | Generate unique event IDs | Event Envelope | LLD_K05_001 | Unit | Happy Path | High |
| EE_TC_006 | Generate sequential numbers per aggregate | Event Envelope | LLD_K05_001 | Unit | Happy Path | High |
| EE_TC_007 | Handle dual-calendar timestamps | Event Envelope | ARCH_SPEC_001 | Unit | Happy Path | High |

**EE_TC_001 Details:**
- **Preconditions**: Envelope validator available
- **Fixtures**: Valid event envelope with all fields
- **Input**: Complete event envelope with required fields
- **Execution Steps**:
  1. Validate envelope structure
  2. Validate field types
  3. Validate timestamp formats
  4. Generate event ID if missing
  5. Generate sequence number
- **Expected Output**: Validated envelope with generated IDs
- **Expected State Changes**: Envelope marked as valid
- **Expected Events**: Validation success event
- **Expected Audit**: Validation logged
- **Expected Observability**: Validation metrics
- **Expected External Interactions**: None
- **Cleanup**: None
- **Branch IDs Covered**: validation_success, id_generation
- **Statement Groups Covered**: envelope_validator, id_generator

### Test Cases for Schema Management

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| SM_TC_001 | Register new schema successfully | Schema Registry | ADR_009_001 | Contract | Happy Path | High |
| SM_TC_002 | Validate event against registered schema | Schema Registry | LLD_K05_001 | Contract | Happy Path | High |
| SM_TC_003 | Reject invalid schema format | Schema Registry | ADR_009_001 | Contract | Validation Failure | High |
| SM_TC_004 | Handle backward-compatible schema evolution | Schema Registry | LLD_K05_001 | Contract | Happy Path | High |
| SM_TC_005 | Reject incompatible schema evolution | Schema Registry | LLD_K05_001 | Contract | Validation Failure | High |

**SM_TC_001 Details:**
- **Preconditions**: Schema registry available
- **Fixtures**: Valid Avro/Protobuf schema
- **Input**: Schema definition with metadata
- **Execution Steps**:
  1. Validate schema format
  2. Check for conflicts
  3. Register schema
  4. Return schema ID
- **Expected Output**: Schema registered with unique ID
- **Expected State Changes**: Schema stored in registry
- **Expected Events**: Schema registered event
- **Expected Audit**: Schema registration logged
- **Expected Observability**: Schema metrics
- **Expected External Interactions**: Schema registry storage
- **Cleanup**: Remove test schema
- **Branch IDs Covered**: schema_registration_success
- **Statement Groups Covered**: schema_validator, registry_manager

### Test Cases for Event Store

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| ES_TC_001 | Append event to store successfully | Event Store | LLD_K05_001 | Component | Happy Path | High |
| ES_TC_002 | Read events by aggregate ID | Event Store | LLD_K05_001 | Component | Happy Path | High |
| ES_TC_003 | Enforce per-aggregate ordering | Event Store | LLD_K05_001 | Component | Happy Path | High |
| ES_TC_004 | Prevent event mutation | Event Store | LLD_K05_001 | Component | Security | High |
| ES_TC_005 | Handle concurrent appends to same aggregate | Event Store | LLD_K05_001 | Component | Concurrency | High |

**ES_TC_001 Details:**
- **Preconditions**: Event store initialized, schema validated
- **Fixtures**: Valid event envelope, database connection
- **Input**: Validated event envelope
- **Execution Steps**:
  1. Validate append-only constraint
  2. Generate sequence number
  3. Append to event log
  4. Update aggregate version
  5. Return success confirmation
- **Expected Output**: Event stored with sequence number
- **Expected State Changes**: New event row in database
- **Expected Events**: Event stored event
- **Expected Audit**: Event append logged
- **Expected Observability**: Store metrics
- **Expected External Interactions**: PostgreSQL
- **Cleanup**: Remove test event
- **Branch IDs Covered**: append_success, ordering_enforced
- **Statement Groups Covered**: event_store, sequence_generator

### Test Cases for Publication

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| PB_TC_001 | Publish event to Kafka successfully | Publisher | ADR_009_001 | Integration | Happy Path | High |
| PB_TC_002 | Handle duplicate publish with same idempotency key | Publisher | LLD_K05_001 | Integration | Idempotency | High |
| PB_TC_003 | Propagate correlation and causation IDs | Publisher | LLD_K05_001 | Integration | Happy Path | High |
| PB_TC_004 | Enforce tenant isolation | Publisher | LLD_K05_001 | Security | Tenant Isolation | High |
| PB_TC_005 | Handle Kafka broker failure | Publisher | ADR_009_001 | Resilience | Dependency Failure | High |

**PB_TC_001 Details:**
- **Preconditions**: Event stored, Kafka available
- **Fixtures**: Kafka cluster, event envelope
- **Input**: Stored event with metadata
- **Execution Steps**:
  1. Serialize event
  2. Publish to Kafka topic
  3. Wait for acknowledgment
  4. Update publication status
  5. Emit publication event
- **Expected Output**: Event published to Kafka
- **Expected State Changes**: Publication status updated
- **Expected Events**: Publication success event
- **Expected Audit**: Publication logged
- **Expected Observability**: Publication metrics
- **Expected External Interactions**: Kafka
- **Cleanup**: Clean Kafka topic
- **Branch IDs Covered**: publication_success, serialization_success
- **Statement Groups Covered**: kafka_publisher, event_serializer

### Test Cases for Subscription

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| SB_TC_001 | Subscribe to topic and receive events | Subscriber | ADR_009_001 | Integration | Happy Path | High |
| SB_TC_002 | Handle disconnect and resume from offset | Subscriber | LLD_K05_001 | Resilience | Recovery | High |
| SB_TC_003 | Process event successfully | Subscriber | LLD_K05_001 | Integration | Happy Path | High |
| SB_TC_004 | Handle processing failure with retry | Subscriber | LLD_K05_001 | Resilience | Error Handling | High |
| SB_TC_005 | Escalate to DLQ after retry exhaustion | Subscriber | LLD_K05_001 | Resilience | Error Handling | High |

**SB_TC_001 Details:**
- **Preconditions**: Kafka topic exists, subscriber configured
- **Fixtures**: Kafka cluster, test events
- **Input**: Subscription request for topic
- **Execution Steps**:
  1. Create Kafka consumer
  2. Subscribe to topic
  3. Poll for events
  4. Deserialize events
  5. Process events
- **Expected Output**: Events received and processed
- **Expected State Changes**: Consumer offset updated
- **Expected Events**: Event processed events
- **Expected Audit**: Processing logged
- **Expected Observability**: Consumer metrics
- **Expected External Interactions**: Kafka
- **Cleanup**: Close consumer, clean offsets
- **Branch IDs Covered**: subscription_success, processing_success
- **Statement Groups Covered**: kafka_consumer, event_processor

### Test Cases for Replay

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| RP_TC_001 | Replay all events from beginning | Replay Service | LLD_K05_001 | Integration | Happy Path | High |
| RP_TC_002 | Replay from specific checkpoint | Replay Service | LLD_K05_001 | Integration | Happy Path | High |
| RP_TC_003 | Replay events within date range | Replay Service | LLD_K05_001 | Integration | Happy Path | High |
| RP_TC_004 | Handle corrupted event during replay | Replay Service | LLD_K05_001 | Resilience | Error Handling | High |
| RP_TC_005 | Rebuild projection from replayed events | Replay Service | LLD_K05_001 | Integration | Happy Path | High |

**RP_TC_001 Details:**
- **Preconditions**: Event store populated, replay service available
- **Fixtures**: Test events in store
- **Input**: Replay request from beginning
- **Execution Steps**:
  1. Start replay from earliest offset
  2. Read events sequentially
  3. Validate each event
  4. Emit replay events
  5. Track replay progress
- **Expected Output**: All events replayed in order
- **Expected State Changes**: Replay progress tracked
- **Expected Events**: Replay events
- **Expected Audit**: Replay logged
- **Expected Observability**: Replay metrics
- **Expected External Interactions**: Event store, Kafka
- **Cleanup**: Reset replay state
- **Branch IDs Covered**: replay_success, sequential_processing
- **Statement Groups Covered**: replay_service, event_reader

### Test Cases for Saga Orchestration

| test_id | title | module | requirement_refs | test_layer | scenario_type | priority |
|---------|-------|--------|------------------|------------|--------------|----------|
| SG_TC_001 | Start new saga instance | Saga Orchestrator | LLD_K05_001 | Integration | Happy Path | High |
| SG_TC_002 | Progress saga through steps | Saga Orchestrator | LLD_K05_001 | Integration | Happy Path | High |
| SG_TC_003 | Trigger compensation on failure | Saga Orchestrator | LLD_K05_001 | Integration | Compensation | High |
| SG_TC_004 | Handle saga timeout | Saga Orchestrator | LLD_K05_001 | Resilience | Timeout | High |
| SG_TC_005 | Prevent duplicate saga start | Saga Orchestrator | LLD_K05_001 | Integration | Idempotency | High |

**SG_TC_001 Details:**
- **Preconditions**: Saga orchestrator available
- **Fixtures**: Saga definition, initial event
- **Input**: Saga start request with correlation ID
- **Execution Steps**:
  1. Validate saga definition
  2. Create saga instance
  3. Emit saga started event
  4. Execute first step
  5. Track saga state
- **Expected Output**: Saga instance created and started
- **Expected State Changes**: Saga state stored
- **Expected Events**: Saga started event
- **Expected Audit**: Saga start logged
- **Expected Observability**: Saga metrics
- **Expected External Interactions**: Event store
- **Cleanup**: Remove saga instance
- **Branch IDs Covered**: saga_start_success, step_execution
- **Statement Groups Covered**: saga_orchestrator, state_manager

---

## 7. Real-World Scenario Suites

### Suite RW_001: Complete Order Processing Saga
- **Suite ID**: RW_001
- **Business Narrative**: Complete order processing with compensation on failure
- **Actors**: Order Service, Payment Service, Inventory Service, K-05 Event Bus
- **Preconditions**: All services available, event bus running
- **Timeline**: 5 minutes
- **Exact Input Set**: Order creation request, payment details
- **Expected Outputs per Step**:
  1. OrderCreated event published
  2. PaymentProcessed event published
  3. InventoryReserved event published
  4. OrderCompleted event published
- **Expected Failure Variants**: Payment failure, inventory shortage
- **Expected Recovery Variants**: Compensation events, order cancellation

### Suite RW_002: Event Replay After Schema Evolution
- **Suite ID**: RW_002
- **Business Narrative**: System evolves schema and replays historical events
- **Actors**: Schema Registry, Event Store, Replay Service
- **Preconditions**: Historical events with old schema, new schema defined
- **Timeline**: 10 minutes
- **Exact Input Set**: Replay request, schema evolution mapping
- **Expected Outputs per Step**:
  1. Schema compatibility validated
  2. Historical events transformed
  3. Events replayed with new schema
  4. Projections rebuilt successfully
- **Expected Failure Variants**: Incompatible schema, transformation errors
- **Expected Recovery Variants**: Manual intervention, data fixes

---

## 8. Coverage Matrices

### Requirement Coverage Matrix
| Requirement ID | Test Cases | Coverage Status |
|----------------|------------|-----------------|
| REQ_EE_001 | EE_TC_001-007 | 100% |
| REQ_SM_001 | SM_TC_001-005 | 100% |
| REQ_ES_001 | ES_TC_001-005 | 100% |
| REQ_PB_001 | PB_TC_001-005 | 100% |
| REQ_SB_001 | SB_TC_001-005 | 100% |
| REQ_RP_001 | RP_TC_001-005 | 100% |
| REQ_SG_001 | SG_TC_001-005 | 100% |

### Branch Coverage Matrix
| Branch ID | Test Cases | Coverage Status |
|-----------|------------|-----------------|
| validation_success | EE_TC_001, EE_TC_005-007 | 100% |
| validation_failure | EE_TC_002-004 | 100% |
| schema_registration_success | SM_TC_001-002 | 100% |
| schema_rejection | SM_TC_003, SM_TC_005 | 100% |
| append_success | ES_TC_001-002 | 100% |
| ordering_enforced | ES_TC_003-005 | 100% |
| publication_success | PB_TC_001, PB_TC_003 | 100% |
| idempotency_handled | PB_TC_002 | 100% |
| subscription_success | SB_TC_001, SB_TC_003 | 100% |
| recovery_handled | SB_TC_002, SB_TC_004-005 | 100% |
| replay_success | RP_TC_001-003, RP_TC_005 | 100% |
| replay_error_handling | RP_TC_004 | 100% |
| saga_success | SG_TC_001-002 | 100% |
| saga_compensation | SG_TC_003-004 | 100% |
| saga_idempotency | SG_TC_005 | 100% |

### Statement Coverage Matrix
| Statement Group | Test Cases | Coverage Status |
|-----------------|------------|-----------------|
| envelope_validator | EE_TC_001-007 | 100% |
| schema_manager | SM_TC_001-005 | 100% |
| event_store | ES_TC_001-005 | 100% |
| kafka_publisher | PB_TC_001-005 | 100% |
| kafka_consumer | SB_TC_001-005 | 100% |
| replay_service | RP_TC_001-005 | 100% |
| saga_orchestrator | SG_TC_001-005 | 100% |

---

## 9. Coverage Gaps and Exclusions

**No known gaps** - All identified behaviors and scenarios are covered by test cases.

**Exclusions:**
- Full K-07 audit framework integration (deferred to K-07 implementation)
- Advanced monitoring and alerting (deferred to K-06 implementation)
- Multi-region replication (deferred to future phases)

---

## 10. Recommended Test File Plan

### Unit Tests
- `src/test/java/com/siddhanta/eventbus/EventEnvelopeValidatorTest.java`
- `src/test/java/com/siddhanta/eventbus/SchemaManagerTest.java`
- `src/test/java/com/siddhanta/eventbus/EventIdGeneratorTest.java`
- `src/test/java/com/siddhanta/eventbus/SequenceGeneratorTest.java`

### Component Tests
- `src/test/java/com/siddhanta/eventbus/EventStoreComponentTest.java`
- `src/test/java/com/siddhanta/eventbus/KafkaPublisherComponentTest.java`
- `src/test/java/com/siddhanta/eventbus/KafkaConsumerComponentTest.java`
- `src/test/java/com/siddhanta/eventbus/ReplayServiceComponentTest.java`

### Integration Tests
- `src/test/java/com/siddhanta/eventbus/EventBusIntegrationTest.java`
- `src/test/java/com/siddhanta/eventbus/SagaOrchestrationIntegrationTest.java`
- `src/test/java/com/siddhanta/eventbus/TenantIsolationIntegrationTest.java`

### Resilience Tests
- `src/test/java/com/siddhanta/eventbus/FailureRecoveryTest.java`
- `src/test/java/com/siddhanta/eventbus/ChaosTest.java`
- `src/test/java/com/siddhanta/eventbus/CompensationTest.java`

### Contract Tests
- `src/test/java/com/siddhanta/eventbus/ApiContractTest.java`
- `src/test/java/com/siddhanta/eventbus/SchemaContractTest.java`
- `src/test/java/com/siddhanta/eventbus/KafkaContractTest.java`

---

## 11. Machine-Readable Appendix

```yaml
test_plan:
  scope: k05_event_bus
  modules:
    - event_envelope
    - schema_management
    - event_store
    - publication
    - subscription
    - replay
    - saga_orchestration
  cases:
    - id: EE_TC_001
      title: Validate complete valid envelope
      layer: unit
      module: event_envelope
      scenario_type: happy_path
      requirement_refs: [LLD_K05_001]
      source_refs: [LLD_K05_EVENT_BUS.md]
      preconditions: [envelope_validator_available]
      fixtures: [valid_event_envelope]
      input: {event_type: "OrderCreated", aggregate_id: "order-123", data: {...}}
      steps:
        - validate_envelope_structure
        - validate_field_types
        - validate_timestamp_formats
        - generate_event_id_if_missing
        - generate_sequence_number
      expected_output: {status: "valid", event_id: "uuid-123", sequence: 1}
      expected_state_changes: [envelope_marked_valid]
      expected_events: [validation_success_event]
      expected_audit: [validation_logged]
      expected_observability: [validation_metrics]
      expected_external_interactions: []
      cleanup: []
      branch_ids_covered: [validation_success, id_generation]
      statement_groups_covered: [envelope_validator, id_generator]
    
    - id: SM_TC_001
      title: Register new schema successfully
      layer: contract
      module: schema_management
      scenario_type: happy_path
      requirement_refs: [ADR_009_001]
      source_refs: [ADR-009_EVENT_BUS_TECHNOLOGY.md]
      preconditions: [schema_registry_available]
      fixtures: [valid_avro_schema]
      input: {schema: {...}, metadata: {version: "1.0", type: "avro"}}
      steps:
        - validate_schema_format
        - check_for_conflicts
        - register_schema
        - return_schema_id
      expected_output: {status: "registered", schema_id: "schema-123"}
      expected_state_changes: [schema_stored_in_registry]
      expected_events: [schema_registered_event]
      expected_audit: [schema_registration_logged]
      expected_observability: [schema_metrics]
      expected_external_interactions: [schema_registry_storage]
      cleanup: [remove_test_schema]
      branch_ids_covered: [schema_registration_success]
      statement_groups_covered: [schema_validator, registry_manager]
    
    - id: ES_TC_001
      title: Append event to store successfully
      layer: component
      module: event_store
      scenario_type: happy_path
      requirement_refs: [LLD_K05_001]
      source_refs: [LLD_K05_EVENT_BUS.md]
      preconditions: [event_store_initialized, schema_validated]
      fixtures: [valid_event_envelope, database_connection]
      input: {validated_envelope: {...}}
      steps:
        - validate_append_only_constraint
        - generate_sequence_number
        - append_to_event_log
        - update_aggregate_version
        - return_success_confirmation
      expected_output: {status: "stored", sequence: 1, version: 1}
      expected_state_changes: [new_event_row_in_database]
      expected_events: [event_stored_event]
      expected_audit: [event_append_logged]
      expected_observability: [store_metrics]
      expected_external_interactions: [postgresql]
      cleanup: [remove_test_event]
      branch_ids_covered: [append_success, ordering_enforced]
      statement_groups_covered: [event_store, sequence_generator]
    
    - id: PB_TC_001
      title: Publish event to Kafka successfully
      layer: integration
      module: publication
      scenario_type: happy_path
      requirement_refs: [ADR_009_001]
      source_refs: [ADR-009_EVENT_BUS_TECHNOLOGY.md]
      preconditions: [event_stored, kafka_available]
      fixtures: [kafka_cluster, event_envelope]
      input: {stored_event: {...}}
      steps:
        - serialize_event
        - publish_to_kafka_topic
        - wait_for_acknowledgment
        - update_publication_status
        - emit_publication_event
      expected_output: {status: "published", partition: 0, offset: 123}
      expected_state_changes: [publication_status_updated]
      expected_events: [publication_success_event]
      expected_audit: [publication_logged]
      expected_observability: [publication_metrics]
      expected_external_interactions: [kafka]
      cleanup: [clean_kafka_topic]
      branch_ids_covered: [publication_success, serialization_success]
      statement_groups_covered: [kafka_publisher, event_serializer]
    
    - id: SB_TC_001
      title: Subscribe to topic and receive events
      layer: integration
      module: subscription
      scenario_type: happy_path
      requirement_refs: [ADR_009_001]
      source_refs: [ADR-009_EVENT_BUS_TECHNOLOGY.md]
      preconditions: [kafka_topic_exists, subscriber_configured]
      fixtures: [kafka_cluster, test_events]
      input: {subscription_request: {topic: "orders", group_id: "order-processor"}}
      steps:
        - create_kafka_consumer
        - subscribe_to_topic
        - poll_for_events
        - deserialize_events
        - process_events
      expected_output: {status: "processing", events_received: 5}
      expected_state_changes: [consumer_offset_updated]
      expected_events: [event_processed_events]
      expected_audit: [processing_logged]
      expected_observability: [consumer_metrics]
      expected_external_interactions: [kafka]
      cleanup: [close_consumer, clean_offsets]
      branch_ids_covered: [subscription_success, processing_success]
      statement_groups_covered: [kafka_consumer, event_processor]
    
    - id: RP_TC_001
      title: Replay all events from beginning
      layer: integration
      module: replay
      scenario_type: happy_path
      requirement_refs: [LLD_K05_001]
      source_refs: [LLD_K05_EVENT_BUS.md]
      preconditions: [event_store_populated, replay_service_available]
      fixtures: [test_events_in_store]
      input: {replay_request: {from: "beginning", to: "end"}}
      steps:
        - start_replay_from_earliest_offset
        - read_events_sequentially
        - validate_each_event
        - emit_replay_events
        - track_replay_progress
      expected_output: {status: "completed", events_replayed: 100}
      expected_state_changes: [replay_progress_tracked]
      expected_events: [replay_events]
      expected_audit: [replay_logged]
      expected_observability: [replay_metrics]
      expected_external_interactions: [event_store, kafka]
      cleanup: [reset_replay_state]
      branch_ids_covered: [replay_success, sequential_processing]
      statement_groups_covered: [replay_service, event_reader]
    
    - id: SG_TC_001
      title: Start new saga instance
      layer: integration
      module: saga_orchestration
      scenario_type: happy_path
      requirement_refs: [LLD_K05_001]
      source_refs: [LLD_K05_EVENT_BUS.md]
      preconditions: [saga_orchestrator_available]
      fixtures: [saga_definition, initial_event]
      input: {saga_start_request: {type: "OrderProcessing", correlation_id: "order-123"}}
      steps:
        - validate_saga_definition
        - create_saga_instance
        - emit_saga_started_event
        - execute_first_step
        - track_saga_state
      expected_output: {status: "started", saga_id: "saga-456", step: "payment"}
      expected_state_changes: [saga_state_stored]
      expected_events: [saga_started_event]
      expected_audit: [saga_start_logged]
      expected_observability: [saga_metrics]
      expected_external_interactions: [event_store]
      cleanup: [remove_saga_instance]
      branch_ids_covered: [saga_start_success, step_execution]
      statement_groups_covered: [saga_orchestrator, state_manager]

  coverage:
    requirement_ids:
      REQ_EE_001: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005, EE_TC_006, EE_TC_007]
      REQ_SM_001: [SM_TC_001, SM_TC_002, SM_TC_003, SM_TC_004, SM_TC_005]
      REQ_ES_001: [ES_TC_001, ES_TC_002, ES_TC_003, ES_TC_004, ES_TC_005]
      REQ_PB_001: [PB_TC_001, PB_TC_002, PB_TC_003, PB_TC_004, PB_TC_005]
      REQ_SB_001: [SB_TC_001, SB_TC_002, SB_TC_003, SB_TC_004, SB_TC_005]
      REQ_RP_001: [RP_TC_001, RP_TC_002, RP_TC_003, RP_TC_004, RP_TC_005]
      REQ_SG_001: [SG_TC_001, SG_TC_002, SG_TC_003, SG_TC_004, SG_TC_005]
    branch_ids:
      validation_success: [EE_TC_001, EE_TC_005, EE_TC_006, EE_TC_007]
      validation_failure: [EE_TC_002, EE_TC_003, EE_TC_004]
      schema_registration_success: [SM_TC_001, SM_TC_002]
      schema_rejection: [SM_TC_003, SM_TC_005]
      append_success: [ES_TC_001, ES_TC_002]
      ordering_enforced: [ES_TC_003, ES_TC_004, ES_TC_005]
      publication_success: [PB_TC_001, PB_TC_003]
      idempotency_handled: [PB_TC_002]
      subscription_success: [SB_TC_001, SB_TC_003]
      recovery_handled: [SB_TC_002, SB_TC_004, SB_TC_005]
      replay_success: [RP_TC_001, RP_TC_002, RP_TC_003, RP_TC_005]
      replay_error_handling: [RP_TC_004]
      saga_success: [SG_TC_001, SG_TC_002]
      saga_compensation: [SG_TC_003, SG_TC_004]
      saga_idempotency: [SG_TC_005]
    statement_groups:
      envelope_validator: [EE_TC_001, EE_TC_002, EE_TC_003, EE_TC_004, EE_TC_005, EE_TC_006, EE_TC_007]
      schema_manager: [SM_TC_001, SM_TC_002, SM_TC_003, SM_TC_004, SM_TC_005]
      event_store: [ES_TC_001, ES_TC_002, ES_TC_003, ES_TC_004, ES_TC_005]
      kafka_publisher: [PB_TC_001, PB_TC_002, PB_TC_003, PB_TC_004, PB_TC_005]
      kafka_consumer: [SB_TC_001, SB_TC_002, SB_TC_003, SB_TC_004, SB_TC_005]
      replay_service: [RP_TC_001, RP_TC_002, RP_TC_003, RP_TC_004, RP_TC_005]
      saga_orchestrator: [SG_TC_001, SG_TC_002, SG_TC_003, SG_TC_004, SG_TC_005]
  exclusions: []
```

---

**K-05 Event Bus TDD specification complete.** This provides exhaustive test coverage for canonical event envelope, append-only event store, publication/subscription, schema management, replay, and saga orchestration. The specification is ready for test implementation and subsequent code generation to satisfy these tests.
