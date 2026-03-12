# LOW-LEVEL DESIGN: K-17 DISTRIBUTED TRANSACTION COORDINATOR

**Module**: K-17 Distributed Transaction Coordinator (DTC)  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-17 provides **transactional outbox, version vectors for cross-stream causal ordering, and compensation orchestration** to guarantee data consistency across distributed services.

**Core Responsibilities**:
- Transactional outbox pattern for guaranteed event publishing
- Version vectors for cross-aggregate causal ordering
- Saga orchestration with step-level compensation
- Idempotent command processing via deduplication keys
- Timeout management with automatic compensation triggers
- Dead-letter escalation for failed compensations (→ K-19)
- Dual-calendar timestamps on all coordination metadata

**Invariants**:
1. Outbox events MUST be published in order, at-least-once
2. Saga compensation MUST be triggered within configured timeout
3. Compensated sagas MUST leave the system in a consistent state
4. All saga state transitions MUST be persisted before acknowledgement
5. Idempotency keys MUST be retained for at least 7 days
6. Failed compensations MUST be escalated to K-19 DLQ

### 1.2 Explicit Non-Goals

- ❌ Two-phase commit (2PC) — uses saga pattern instead
- ❌ Long-running transaction locking — uses optimistic concurrency
- ❌ Message broker management — delegates to K-05 Event Bus

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-05 Event Bus | Event delivery and saga event publishing | K-05 stable |
| K-06 Observability | Saga metrics and tracing | K-06 stable |
| K-07 Audit Framework | Saga lifecycle audit | K-07 stable |
| K-15 Dual-Calendar | BS timestamps | K-15 stable |
| K-19 DLQ Management | Dead-letter for failed compensations | K-19 stable |
| PostgreSQL | Outbox table, saga state, idempotency store | DB available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/dtc/sagas
Authorization: Bearer {service_token}

Request:
{
  "saga_type": "TradeSettlementSaga",
  "saga_id": "saga_trd_001",
  "tenant_id": "tenant_np_1",
  "initial_data": {
    "trade_id": "TRD-001",
    "buyer_account": "acc_buyer_1",
    "seller_account": "acc_seller_1",
    "amount": 125050.00,
    "currency": "NPR"
  },
  "timeout_seconds": 300,
  "mode": "ORCHESTRATED"
}

Response 202:
{
  "saga_id": "saga_trd_001",
  "status": "STARTED",
  "current_step": "validate_funds",
  "started_at": "2025-03-02T10:30:00Z",
  "started_at_bs": "2081-11-18 10:30:00"
}
```

```yaml
GET /api/v1/dtc/sagas/{saga_id}
Authorization: Bearer {service_token}

Response 200:
{
  "saga_id": "saga_trd_001",
  "saga_type": "TradeSettlementSaga",
  "status": "COMPLETED",
  "steps": [
    { "name": "validate_funds", "status": "COMPLETED", "duration_ms": 12 },
    { "name": "debit_buyer", "status": "COMPLETED", "duration_ms": 8 },
    { "name": "credit_seller", "status": "COMPLETED", "duration_ms": 7 },
    { "name": "update_positions", "status": "COMPLETED", "duration_ms": 15 }
  ],
  "started_at": "2025-03-02T10:30:00Z",
  "completed_at": "2025-03-02T10:30:00.042Z",
  "total_duration_ms": 42
}
```

```yaml
POST /api/v1/dtc/sagas/{saga_id}/compensate
Authorization: Bearer {admin_token}

Response 202:
{
  "saga_id": "saga_trd_001",
  "status": "COMPENSATING",
  "message": "Manual compensation initiated"
}
```

```yaml
POST /api/v1/dtc/outbox/flush
Authorization: Bearer {service_token}

Response 200:
{
  "flushed_count": 42,
  "remaining": 0,
  "oldest_pending_age_ms": 0
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.dtc.v1;

service DTCService {
  rpc StartSaga(StartSagaRequest) returns (StartSagaResponse);
  rpc GetSagaStatus(GetSagaStatusRequest) returns (SagaStatusResponse);
  rpc CompensateSaga(CompensateSagaRequest) returns (CompensateResponse);
  rpc ExecuteIdempotent(IdempotentCommandRequest) returns (IdempotentCommandResponse);
}

message StartSagaRequest {
  string saga_type = 1;
  string saga_id = 2;
  string tenant_id = 3;
  google.protobuf.Struct initial_data = 4;
  int32 timeout_seconds = 5;
  SagaMode mode = 6;
}

enum SagaMode {
  ORCHESTRATED = 0;    // Central coordinator drives steps
  CHOREOGRAPHED = 1;   // Event-driven, each service reacts
  SYNCHRONOUS = 2;     // All steps in one transaction (T+0 settlement)
}
```

### 2.3 SDK Method Signatures

```typescript
interface DTCClient {
  /** Start a saga */
  startSaga<T>(sagaType: string, sagaId: string, data: T, options?: SagaOptions): Promise<SagaExecution>;

  /** Get saga status */
  getSagaStatus(sagaId: string): Promise<SagaStatus>;

  /** Manually trigger compensation */
  compensate(sagaId: string): Promise<void>;

  /** Execute command with idempotency */
  executeIdempotent<T, R>(key: string, command: () => Promise<R>): Promise<R>;

  /** Publish via outbox (guaranteed delivery) */
  publishViaOutbox(event: EventEnvelope): Promise<void>;
}

interface SagaOptions {
  timeoutSeconds: number;
  mode: 'ORCHESTRATED' | 'CHOREOGRAPHED' | 'SYNCHRONOUS';
  retryPolicy?: RetryPolicy;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| DTC_E001 | 409 | No | Saga already exists with this ID |
| DTC_E002 | 400 | No | Unknown saga type |
| DTC_E003 | 500 | Yes | Saga step execution failed |
| DTC_E004 | 500 | No | Compensation failed (escalated to DLQ) |
| DTC_E005 | 408 | No | Saga timed out |
| DTC_E006 | 409 | No | Duplicate idempotency key |
| DTC_E007 | 500 | Yes | Outbox flush failed |

---

## 3. DATA MODEL

### 3.1 Outbox Table

```sql
CREATE TABLE outbox (
  outbox_id BIGSERIAL PRIMARY KEY,
  tenant_id UUID NOT NULL,
  event_type VARCHAR(255) NOT NULL,
  event_payload JSONB NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
  retry_count INT NOT NULL DEFAULT 0,
  max_retries INT NOT NULL DEFAULT 5,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL,
  published_at TIMESTAMPTZ,
  next_retry_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_pending ON outbox(status, created_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_retry ON outbox(next_retry_at) WHERE status = 'FAILED' AND retry_count < max_retries;
```

### 3.2 Saga Tables

```sql
CREATE TABLE saga_definitions (
  saga_type VARCHAR(100) PRIMARY KEY,
  steps JSONB NOT NULL,       -- ordered step definitions with compensation
  timeout_seconds INT NOT NULL DEFAULT 300,
  retry_policy JSONB NOT NULL DEFAULT '{"max_retries": 3, "backoff_ms": 1000}',
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at_bs VARCHAR(30) NOT NULL
);

CREATE TABLE saga_instances (
  saga_id VARCHAR(255) PRIMARY KEY,
  saga_type VARCHAR(100) NOT NULL REFERENCES saga_definitions(saga_type),
  tenant_id UUID NOT NULL,
  status VARCHAR(50) NOT NULL CHECK (status IN (
    'STARTED', 'IN_PROGRESS', 'COMPLETED', 'COMPENSATING', 'COMPENSATED', 'FAILED', 'TIMED_OUT'
  )),
  current_step INT NOT NULL DEFAULT 0,
  initial_data JSONB NOT NULL,
  step_results JSONB NOT NULL DEFAULT '[]',
  error_details JSONB,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at_bs VARCHAR(30) NOT NULL,
  completed_at TIMESTAMPTZ,
  completed_at_bs VARCHAR(30),
  timeout_at TIMESTAMPTZ NOT NULL,
  version INT NOT NULL DEFAULT 0    -- optimistic locking
);

CREATE INDEX idx_saga_status ON saga_instances(status, timeout_at);
CREATE INDEX idx_saga_tenant ON saga_instances(tenant_id, started_at);
```

### 3.3 Idempotency Store

```sql
CREATE TABLE idempotency_keys (
  idempotency_key VARCHAR(255) PRIMARY KEY,
  tenant_id UUID NOT NULL,
  result JSONB,
  status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'FAILED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at TIMESTAMPTZ NOT NULL  -- TTL for cleanup
);

CREATE INDEX idx_idempotency_expiry ON idempotency_keys(expires_at);
```

### 3.4 Version Vector Store

```sql
CREATE TABLE version_vectors (
  aggregate_id VARCHAR(255) NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  stream_id VARCHAR(255) NOT NULL,
  vector_clock JSONB NOT NULL,     -- {"stream_a": 42, "stream_b": 17}
  last_updated TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (aggregate_id, aggregate_type)
);
```

---

## 4. CONTROL FLOW

### 4.1 Transactional Outbox Flow

```
Service → BusinessLogic.execute()
  → BEGIN DB TRANSACTION
    → Mutate domain data (INSERT/UPDATE in domain table)
    → INSERT INTO outbox (event_type, event_payload, status='PENDING')
  → COMMIT TRANSACTION

OutboxPoller (every 100ms):
  → SELECT FROM outbox WHERE status='PENDING' ORDER BY created_at LIMIT 100 FOR UPDATE SKIP LOCKED
  → FOR each outbox_record:
      → K-05 EventBus.publish(event)
      → IF success: UPDATE outbox SET status='PUBLISHED', published_at=NOW()
      → IF failure: UPDATE outbox SET retry_count += 1, next_retry_at = NOW() + backoff
        → IF retry_count >= max_retries: UPDATE status='FAILED', escalate to K-19
```

### 4.2 Saga Orchestration Flow

```
Client → DTCService.startSaga(TradeSettlementSaga, data)
  → SagaOrchestrator.initialize(sagaType, sagaId, data)
    → Load saga_definition (steps + compensations)
    → INSERT saga_instance(status='STARTED')
    → FOR step_index in [0..steps.length]:
        → UPDATE saga_instance(current_step=step_index, status='IN_PROGRESS')
        → Execute step[step_index].action(data, previous_results)
          → IF success:
              → Record step result in step_results
              → CONTINUE to next step
          → IF failure:
              → UPDATE saga_instance(status='COMPENSATING')
              → Trigger compensation (reverse order)
              → FOR comp_index in [step_index-1..0]:
                  → Execute step[comp_index].compensate(data, step_results)
              → UPDATE saga_instance(status='COMPENSATED')
              → IF compensation fails: status='FAILED', escalate to K-19 DLQ
    → UPDATE saga_instance(status='COMPLETED')
    → EventBus.publish(SagaCompletedEvent)
```

### 4.3 Saga Timeout Watchdog

```
CronJob (every 10s):
  → SELECT FROM saga_instances WHERE status IN ('STARTED', 'IN_PROGRESS') AND timeout_at < NOW()
  → FOR each timed_out_saga:
      → UPDATE status='TIMED_OUT'
      → Trigger compensation flow
      → EventBus.publish(SagaTimedOutEvent)
      → AlertService.fire(P2, "Saga timed out")
```

### 4.4 Synchronous Mode (T+0 Settlement)

```
For SYNCHRONOUS mode sagas:
  → All steps execute within a SINGLE database transaction
  → No compensation needed — DB rollback handles failure
  → Sub-second execution target
  → Used for T+0 atomic settlement (debit + credit + position in one TX)
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Outbox Polling with Backoff

```python
def poll_outbox():
    """Poll-based outbox with exponential backoff on failures."""
    records = outbox_repo.get_pending(limit=100)
    
    for record in records:
        try:
            event_bus.publish(record.to_event())
            outbox_repo.mark_published(record.outbox_id)
        except PublishError:
            backoff_ms = min(1000 * (2 ** record.retry_count), 60_000)  # max 60s
            record.retry_count += 1
            record.next_retry_at = now() + timedelta(milliseconds=backoff_ms)
            
            if record.retry_count >= record.max_retries:
                record.status = 'FAILED'
                dlq_service.enqueue(record)  # K-19
                
            outbox_repo.update(record)
```

### 5.2 Version Vector Merge

```python
def merge_vectors(local: dict, remote: dict) -> dict:
    """Merge two version vectors, taking max per stream."""
    merged = dict(local)
    for stream_id, clock in remote.items():
        merged[stream_id] = max(merged.get(stream_id, 0), clock)
    return merged

def is_causally_after(v1: dict, v2: dict) -> bool:
    """True if v1 happened after v2 (v1 dominates v2)."""
    return all(v1.get(k, 0) >= v for k, v in v2.items()) and v1 != v2
```

### 5.3 Saga Definition DSL

```json
{
  "saga_type": "TradeSettlementSaga",
  "steps": [
    {
      "name": "validate_funds",
      "action": { "service": "D-01-OMS", "method": "validateFunds", "timeout_ms": 5000 },
      "compensation": { "service": "D-01-OMS", "method": "releaseHold" }
    },
    {
      "name": "debit_buyer",
      "action": { "service": "K-16-Ledger", "method": "postDebit", "timeout_ms": 5000 },
      "compensation": { "service": "K-16-Ledger", "method": "reverseDebit" }
    },
    {
      "name": "credit_seller",
      "action": { "service": "K-16-Ledger", "method": "postCredit", "timeout_ms": 5000 },
      "compensation": { "service": "K-16-Ledger", "method": "reverseCredit" }
    },
    {
      "name": "update_positions",
      "action": { "service": "D-03-PMS", "method": "transferPosition", "timeout_ms": 10000 },
      "compensation": { "service": "D-03-PMS", "method": "reverseTransfer" }
    }
  ],
  "timeout_seconds": 300,
  "retry_policy": { "max_retries": 3, "backoff_ms": 1000, "jitter": true }
}
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| Outbox write (in DB TX) | 1ms | 3ms | 5ms | 10ms |
| Outbox publish (to K-05) | 2ms | 5ms | 10ms | 30ms |
| Saga start | 5ms | 15ms | 30ms | 100ms |
| Saga step execution | Varies by step service | | | |
| Synchronous saga (T+0) | 20ms | 50ms | 100ms | 500ms |
| Idempotency check | 1ms | 3ms | 5ms | 10ms |

### 6.2 Throughput Targets

| Metric | Target |
|--------|--------|
| Outbox throughput | 50K events/sec |
| Saga starts | 5K/sec |
| Idempotency checks | 100K/sec |
| Concurrent active sagas | 100K |

---

## 7. SECURITY DESIGN

### 7.1 Saga Access Control

- Saga creation: `dtc:saga:start` permission
- Saga status query: `dtc:saga:view` permission
- Manual compensation: `dtc:saga:compensate` permission (admin only)
- Outbox flush: `dtc:outbox:admin` permission

### 7.2 Data Protection

- Saga initial_data may contain PII — encrypted at rest (AES-256)
- Outbox event_payload encrypted at rest
- Idempotency keys are opaque (no PII in key value)

### 7.3 Tenant Isolation

- RLS on saga_instances and outbox tables
- Cross-tenant saga steps are prevented at SDK level

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_dtc_outbox_pending_count{tenant}                    gauge
siddhanta_dtc_outbox_publish_duration_ms                      histogram
siddhanta_dtc_outbox_publish_total{result}                    counter
siddhanta_dtc_outbox_age_ms                                   histogram
siddhanta_dtc_saga_started_total{saga_type, tenant}           counter
siddhanta_dtc_saga_completed_total{saga_type, result}         counter
siddhanta_dtc_saga_duration_ms{saga_type}                     histogram
siddhanta_dtc_saga_compensation_total{saga_type}              counter
siddhanta_dtc_saga_active{saga_type}                          gauge
siddhanta_dtc_idempotency_hit_total                           counter
siddhanta_dtc_idempotency_miss_total                          counter
```

### 8.2 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| Outbox lag > 5s | Oldest pending outbox record > 5s old | P2 |
| Outbox failed records | >10 failed records in 5min | P1 |
| Saga timeout rate | >5% sagas timing out in 15min | P1 |
| Compensation failure | Any compensation fails | P1 |
| Active saga count high | >80% of max concurrent sagas | P2 |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Saga Definitions

New saga types are registered via `saga_definitions` table or T1 config pack:
```json
{
  "content_pack_type": "T1",
  "name": "nepal-ipo-allotment-saga",
  "saga_definitions": [
    {
      "saga_type": "IPOAllotmentSaga",
      "steps": [...]
    }
  ]
}
```

### 9.2 Cross-Chain Atomic Swaps (Digital Assets)

For DvP (Delivery vs. Payment) on tokenized securities:
- Saga step 1: Lock tokens on-chain (escrow smart contract)
- Saga step 2: Debit buyer fiat via K-16
- Saga step 3: Transfer tokens from escrow to buyer
- Compensation: Unlock escrow, reverse fiat debit

### 9.3 Pluggable Conflict Resolution

```typescript
interface ConflictResolverPlugin {
  resolve(conflict: VersionConflict): Promise<Resolution>;
}
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-DTC-001 | Outbox record created in TX | Record persists even if app crashes after TX |
| UT-DTC-002 | Idempotency dedup | Second call returns cached result |
| UT-DTC-003 | Version vector merge | Max-per-stream semantics |
| UT-DTC-004 | Saga state transitions | Valid transitions only |
| UT-DTC-005 | Backoff calculation | Exponential with jitter |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-DTC-001 | Outbox → K-05 publish | Event appears in K-05 |
| IT-DTC-002 | Full saga happy path | All steps complete, COMPLETED status |
| IT-DTC-003 | Saga step failure + compensation | Compensation runs in reverse, COMPENSATED |
| IT-DTC-004 | Saga timeout | Status transitions to TIMED_OUT, compensation triggered |
| IT-DTC-005 | Synchronous saga (T+0) | All steps in single TX, rollback on failure |
| IT-DTC-006 | Failed compensation → K-19 DLQ | Record escalated to dead-letter queue |

### 10.3 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-DTC-001 | DB crash during saga step | Saga resumes from last completed step on restart |
| CT-DTC-002 | K-05 unavailable during outbox flush | Records stay PENDING, retried on recovery |
| CT-DTC-003 | Step service crash mid-saga | Timeout triggers, compensation executes |
| CT-DTC-004 | Concurrent saga on same aggregate | Version vector detects conflict, one saga retries |

---

**END OF K-17 DISTRIBUTED TRANSACTION COORDINATOR LLD**
