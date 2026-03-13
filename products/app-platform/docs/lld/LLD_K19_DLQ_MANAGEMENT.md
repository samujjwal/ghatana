# LOW-LEVEL DESIGN: K-19 DLQ MANAGEMENT & EVENT REPLAY

**Module**: K-19 DLQ Management & Event Replay  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

K-19 provides a **dead-letter queue management framework** for events that cannot be processed successfully, including poison pill detection, root-cause analysis (RCA) enforcement, safe replay with idempotency verification, and an operational dashboard for monitoring and resolution.

**Core Responsibilities**:
- Dead-letter queue ingestion from K-05, K-17, and domain services
- Poison pill detection (events that always fail)
- RCA requirement enforcement — replay blocked until RCA is filed
- Safe replay engine with idempotency key pre-check
- Replay audit trail — every replay attempt is recorded
- Threshold-based alerts and escalation policies
- Dual-calendar timestamps on all DLQ records

**Invariants**:
1. Dead-lettered events MUST NOT be discarded — minimum 10-year retention
2. Replay MUST NOT proceed without an RCA reference
3. Replay MUST verify idempotency before re-publishing
4. Poison pill events MUST be quarantined after N consecutive failures
5. All DLQ operations MUST emit K-07 audit events
6. DLQ events MUST preserve original K-05 event envelope intact

### 1.2 Explicit Non-Goals

- ❌ Event transformation or enrichment — DLQ preserves original events
- ❌ Automatic replay without human review (except pre-approved auto-retry rules)
- ❌ Message broker administration — delegates to K-05

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-05 Event Bus | Source of dead-lettered events, replay target | K-05 stable |
| K-06 Observability | DLQ metrics and alerting | K-06 stable |
| K-07 Audit Framework | Replay audit trail | K-07 stable |
| K-15 Dual-Calendar | BS timestamps | K-15 stable |
| PostgreSQL | DLQ persistence and replay state | DB available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
GET /api/v1/dlq/events?status=PENDING&limit=50&offset=0
Authorization: Bearer {admin_token}

Response 200:
{
  "events": [
    {
      "dlq_id": "dlq_evt_001",
      "original_event_id": "evt_abc123",
      "event_type": "siddhanta.oms.order.placed",
      "tenant_id": "tenant_np_1",
      "failure_reason": "SchemaValidationError: missing field 'instrument_id'",
      "failure_count": 3,
      "status": "PENDING",
      "is_poison_pill": false,
      "dead_lettered_at": "2025-03-02T10:30:00Z",
      "dead_lettered_at_bs": "2081-11-18 10:30:00",
      "original_event": { "..." }
    }
  ],
  "total_count": 142,
  "pagination": { "limit": 50, "offset": 0, "has_more": true }
}
```

```yaml
GET /api/v1/dlq/events/{dlq_id}
Authorization: Bearer {admin_token}

Response 200:
{
  "dlq_id": "dlq_evt_001",
  "original_event": { "...full K-05 event envelope..." },
  "failure_history": [
    { "attempt": 1, "error": "SchemaValidationError", "at": "2025-03-02T10:29:55Z" },
    { "attempt": 2, "error": "SchemaValidationError", "at": "2025-03-02T10:29:58Z" },
    { "attempt": 3, "error": "SchemaValidationError", "at": "2025-03-02T10:30:00Z" }
  ],
  "rca": null,
  "replay_history": []
}
```

```yaml
POST /api/v1/dlq/events/{dlq_id}/rca
Authorization: Bearer {admin_token}

Request:
{
  "rca_reference": "INC-2025-0342",
  "root_cause": "Schema v2 deployment incomplete — consumer still on v1",
  "remediation": "Deployed consumer v2.1 with backward-compatible schema handling",
  "analyst": "eng_ops_01"
}

Response 200:
{
  "dlq_id": "dlq_evt_001",
  "rca_status": "FILED",
  "replay_eligible": true
}
```

```yaml
POST /api/v1/dlq/events/{dlq_id}/replay
Authorization: Bearer {admin_token}

Request:
{
  "replay_mode": "SINGLE",
  "dry_run": false,
  "force_idempotency_bypass": false
}

Response 202:
{
  "replay_id": "rpl_001",
  "dlq_id": "dlq_evt_001",
  "status": "REPLAYING",
  "message": "Event submitted for replay via K-05"
}
```

```yaml
POST /api/v1/dlq/bulk-replay
Authorization: Bearer {admin_token}

Request:
{
  "filter": {
    "event_type": "siddhanta.oms.order.placed",
    "status": "RCA_FILED",
    "dead_lettered_after": "2025-03-01T00:00:00Z"
  },
  "dry_run": true,
  "max_events": 100
}

Response 202:
{
  "bulk_replay_id": "brpl_001",
  "matched_events": 87,
  "status": "DRY_RUN_COMPLETE",
  "idempotency_conflicts": 3,
  "safe_to_replay": 84
}
```

```yaml
GET /api/v1/dlq/dashboard/summary
Authorization: Bearer {admin_token}

Response 200:
{
  "total_pending": 142,
  "total_quarantined": 7,
  "total_replayed": 1205,
  "by_event_type": [
    { "event_type": "siddhanta.oms.order.placed", "count": 85 },
    { "event_type": "siddhanta.settlement.trade.settled", "count": 57 }
  ],
  "by_tenant": [
    { "tenant_id": "tenant_np_1", "count": 120 },
    { "tenant_id": "tenant_np_2", "count": 22 }
  ],
  "oldest_pending_age_hours": 4.2,
  "poison_pill_count": 3
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.dlq.v1;

service DLQService {
  rpc EnqueueDeadLetter(EnqueueRequest) returns (EnqueueResponse);
  rpc GetDeadLetter(GetDeadLetterRequest) returns (DeadLetterEvent);
  rpc FileRCA(FileRCARequest) returns (RCAResponse);
  rpc ReplayEvent(ReplayRequest) returns (ReplayResponse);
  rpc BulkReplay(BulkReplayRequest) returns (BulkReplayResponse);
  rpc GetDashboardSummary(Empty) returns (DashboardSummary);
}
```

### 2.3 SDK Method Signatures

```typescript
interface DLQClient {
  /** Enqueue a failed event into the DLQ */
  enqueue(event: EventEnvelope, failureReason: string): Promise<string>;

  /** List DLQ events with filtering */
  list(filter: DLQFilter): Promise<PaginatedResult<DLQEvent>>;

  /** File root-cause analysis */
  fileRCA(dlqId: string, rca: RCADetails): Promise<void>;

  /** Replay a single event */
  replay(dlqId: string, options?: ReplayOptions): Promise<ReplayResult>;

  /** Bulk replay with dry-run support */
  bulkReplay(filter: DLQFilter, dryRun: boolean): Promise<BulkReplayResult>;

  /** Get dashboard summary */
  getDashboardSummary(): Promise<DashboardSummary>;
}

interface ReplayOptions {
  dryRun: boolean;
  forceIdempotencyBypass: boolean;  // requires elevated permission
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| DLQ_E001 | 400 | No | Missing RCA — replay blocked |
| DLQ_E002 | 409 | No | Idempotency conflict — event already processed |
| DLQ_E003 | 409 | No | Event is quarantined (poison pill) |
| DLQ_E004 | 400 | No | Invalid bulk replay filter |
| DLQ_E005 | 500 | Yes | Replay publish to K-05 failed |
| DLQ_E006 | 403 | No | Idempotency bypass requires admin role |

---

## 3. DATA MODEL

### 3.1 DLQ Event Store

```sql
CREATE TABLE dlq_events (
  dlq_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  original_event_id VARCHAR(255) NOT NULL,
  event_type VARCHAR(255) NOT NULL,
  event_version VARCHAR(20) NOT NULL,
  aggregate_id VARCHAR(255),
  aggregate_type VARCHAR(100),
  original_event JSONB NOT NULL,     -- full K-05 envelope preserved
  failure_reason TEXT NOT NULL,
  failure_count INT NOT NULL DEFAULT 1,
  status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (status IN (
    'PENDING', 'RCA_FILED', 'REPLAYING', 'REPLAYED', 'QUARANTINED', 'DISCARDED'
  )),
  is_poison_pill BOOLEAN NOT NULL DEFAULT FALSE,
  rca_reference VARCHAR(100),
  rca_details JSONB,
  dead_lettered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  dead_lettered_at_bs VARCHAR(30) NOT NULL,
  last_failure_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  resolved_at TIMESTAMPTZ,
  resolved_at_bs VARCHAR(30)
);

CREATE INDEX idx_dlq_status ON dlq_events(status, dead_lettered_at);
CREATE INDEX idx_dlq_event_type ON dlq_events(event_type, status);
CREATE INDEX idx_dlq_tenant ON dlq_events(tenant_id, status);
CREATE INDEX idx_dlq_original_event ON dlq_events(original_event_id);

ALTER TABLE dlq_events ENABLE ROW LEVEL SECURITY;
CREATE POLICY dlq_tenant_isolation ON dlq_events
  USING (tenant_id = current_setting('app.current_tenant')::UUID);
```

### 3.2 Failure History

```sql
CREATE TABLE dlq_failure_history (
  failure_id BIGSERIAL PRIMARY KEY,
  dlq_id UUID NOT NULL REFERENCES dlq_events(dlq_id),
  attempt_number INT NOT NULL,
  error_class VARCHAR(255) NOT NULL,
  error_message TEXT NOT NULL,
  stack_trace TEXT,
  consumer_service VARCHAR(100) NOT NULL,
  consumer_version VARCHAR(50),
  failed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  failed_at_bs VARCHAR(30) NOT NULL
);

CREATE INDEX idx_failure_dlq ON dlq_failure_history(dlq_id, attempt_number);
```

### 3.3 Replay Audit

```sql
CREATE TABLE dlq_replay_history (
  replay_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  dlq_id UUID NOT NULL REFERENCES dlq_events(dlq_id),
  bulk_replay_id UUID,          -- NULL for single replays
  replay_mode VARCHAR(20) NOT NULL CHECK (replay_mode IN ('SINGLE', 'BULK')),
  initiated_by VARCHAR(100) NOT NULL,
  rca_reference VARCHAR(100) NOT NULL,
  idempotency_check_result VARCHAR(20) NOT NULL,    -- PASS, CONFLICT, BYPASSED
  dry_run BOOLEAN NOT NULL DEFAULT FALSE,
  result VARCHAR(20) NOT NULL CHECK (result IN ('SUCCESS', 'FAILED', 'DRY_RUN')),
  error_details TEXT,
  replayed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  replayed_at_bs VARCHAR(30) NOT NULL
);

CREATE INDEX idx_replay_dlq ON dlq_replay_history(dlq_id);
CREATE INDEX idx_replay_bulk ON dlq_replay_history(bulk_replay_id);
```

---

## 4. CONTROL FLOW

### 4.1 Dead-Letter Ingestion Flow

```
Consumer fails to process event (after exhausting K-05 retries)
  → K-05 routes event to DLQ topic
  → K-19 DLQ Ingestion Worker:
      → Check if original_event_id already in dlq_events
        → IF exists: INCREMENT failure_count, append to failure_history
        → IF new: INSERT into dlq_events(status='PENDING')
      → Poison pill detection:
        → IF failure_count >= poison_pill_threshold (default 5):
            → UPDATE status='QUARANTINED', is_poison_pill=TRUE
            → Emit alert (P1)
      → Emit K-05 event: siddhanta.dlq.event.dead_lettered
      → Emit K-07 audit: DLQ_EVENT_RECEIVED
```

### 4.2 RCA + Replay Flow

```
Operator reviews DLQ dashboard
  → GET /api/v1/dlq/events (filtered by event_type, tenant)
  → Investigates root cause
  → POST /api/v1/dlq/events/{dlq_id}/rca
    → UPDATE dlq_events SET status='RCA_FILED', rca_reference, rca_details
    → Emit K-07 audit: DLQ_RCA_FILED

Operator triggers replay:
  → POST /api/v1/dlq/events/{dlq_id}/replay
    → Validate: status must be 'RCA_FILED'
    → Idempotency check:
      → Query K-17 idempotency_keys for original event_id
        → IF key exists AND status='COMPLETED': return DLQ_E002 (conflict)
        → IF key exists AND status='FAILED': proceed (safe to retry)
        → IF key not found: proceed
    → Re-publish original_event to K-05 (original topic)
    → UPDATE dlq_events SET status='REPLAYING'
    → INSERT into dlq_replay_history
    → IF consumer processes successfully:
        → UPDATE dlq_events SET status='REPLAYED', resolved_at=NOW()
    → IF consumer fails again:
        → Re-enter DLQ flow (failure_count increments)
    → Emit K-07 audit: DLQ_EVENT_REPLAYED
```

### 4.3 Bulk Replay Flow

```
Operator identifies class of related DLQ events (same root cause)
  → POST /api/v1/dlq/bulk-replay (dry_run=true)
    → Match events by filter criteria
    → For each matched event:
      → Check RCA present (skip if not)
      → Check idempotency (flag conflicts)
    → Return summary: total, safe_to_replay, conflicts
  → Review dry-run results
  → POST /api/v1/dlq/bulk-replay (dry_run=false)
    → Replay safe events in batches (100 events/batch, 10ms delay between)
    → Track progress in bulk_replay_progress table
    → Emit K-05 event: siddhanta.dlq.bulk_replay.completed
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Poison Pill Detection

```python
POISON_PILL_THRESHOLD = 5          # consecutive failures
POISON_PILL_TIME_WINDOW = 3600     # 1 hour

def check_poison_pill(dlq_event: DLQEvent) -> bool:
    """Detect if event is a poison pill (always fails)."""
    if dlq_event.failure_count >= POISON_PILL_THRESHOLD:
        # Check if all failures are the same error class
        recent_failures = get_recent_failures(
            dlq_event.dlq_id, 
            since=now() - timedelta(seconds=POISON_PILL_TIME_WINDOW)
        )
        error_classes = set(f.error_class for f in recent_failures)
        if len(error_classes) == 1:
            # Same error every time — poison pill
            return True
        if dlq_event.failure_count >= POISON_PILL_THRESHOLD * 2:
            # Too many failures regardless of variety
            return True
    return False
```

### 5.2 Auto-Retry Rules

```json
{
  "auto_retry_rules": [
    {
      "name": "transient-network-retry",
      "match": {
        "error_class_regex": "^(TimeoutException|ConnectionRefused|DNSLookupFailed)$",
        "max_failure_count": 3
      },
      "action": {
        "auto_replay": true,
        "delay_seconds": 300,
        "rca_auto_tag": "AUTO:TRANSIENT_NETWORK",
        "max_auto_retries": 3
      }
    }
  ]
}
```

### 5.3 Replay Rate Limiter

```python
REPLAY_RATE_LIMIT = 100     # events per second
REPLAY_BATCH_SIZE = 100     # events per batch
REPLAY_BATCH_DELAY_MS = 10  # delay between batches

def bulk_replay(events: list[DLQEvent]):
    """Rate-limited bulk replay to avoid overwhelming consumers."""
    for batch in chunk(events, REPLAY_BATCH_SIZE):
        for event in batch:
            event_bus.publish(event.original_event)
            time.sleep(1.0 / REPLAY_RATE_LIMIT)
        time.sleep(REPLAY_BATCH_DELAY_MS / 1000)
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Max |
|-----------|-----|-----|-----|-----|
| DLQ ingestion | 5ms | 15ms | 30ms | 100ms |
| Single replay | 10ms | 30ms | 50ms | 200ms |
| RCA filing | 5ms | 10ms | 20ms | 50ms |
| Dashboard summary | 50ms | 200ms | 500ms | 2000ms |
| Bulk replay (100 events) | 2s | 5s | 10s | 30s |

### 6.2 Storage & Retention

| Metric | Target |
|--------|--------|
| DLQ event retention | 10 years (regulatory) |
| Replay history retention | 10 years |
| Failure history retention | 10 years |
| Max DLQ events (per tenant) | Unlimited |
| Storage per DLQ event | ~2KB avg (event + metadata) |

---

## 7. SECURITY DESIGN

### 7.1 Access Control

| Operation | Required Permission |
|-----------|-------------------|
| View DLQ events | `dlq:events:view` |
| File RCA | `dlq:rca:write` |
| Single replay | `dlq:replay:execute` |
| Bulk replay | `dlq:replay:bulk` |
| Idempotency bypass | `dlq:replay:bypass_idempotency` (admin only) |
| Discard event | `dlq:events:discard` (super-admin, maker-checker) |

### 7.2 Data Protection

- Original events may contain PII — encrypted at rest
- RCA details may contain sensitive operational info — access logged
- Replay audit trail is immutable (append-only)

### 7.3 Tenant Isolation

- RLS on dlq_events ensures tenant cannot see other tenants' dead letters
- Bulk replay scoped to tenant_id filter

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```
siddhanta_dlq_events_total{tenant, event_type, status}           counter
siddhanta_dlq_events_pending{tenant}                              gauge
siddhanta_dlq_events_quarantined{tenant}                          gauge
siddhanta_dlq_ingestion_duration_ms                               histogram
siddhanta_dlq_replay_total{result}                                counter
siddhanta_dlq_replay_duration_ms                                  histogram
siddhanta_dlq_bulk_replay_total{result}                           counter
siddhanta_dlq_oldest_pending_age_seconds{tenant}                  gauge
siddhanta_dlq_poison_pill_total{event_type}                       counter
siddhanta_dlq_rca_filed_total{tenant}                             counter
```

### 8.2 Alerts

| Alert | Condition | Severity |
|-------|-----------|----------|
| DLQ pending > threshold | >100 pending events per tenant | P2 |
| DLQ age > 24h | Oldest pending event > 24 hours | P1 |
| Poison pill detected | Any event quarantined | P1 |
| Replay failure | Replay attempt fails | P2 |
| Bulk replay failure | >10% events fail in bulk replay | P1 |
| DLQ ingestion spike | >50 events/minute for 5 minutes | P2 |

### 8.3 K-07 Audit Events

| Event | Trigger |
|-------|---------|
| DLQ_EVENT_RECEIVED | Event enters DLQ |
| DLQ_RCA_FILED | RCA filed for event |
| DLQ_EVENT_REPLAYED | Event replayed via K-05 |
| DLQ_EVENT_QUARANTINED | Poison pill quarantined |
| DLQ_BULK_REPLAY_STARTED | Bulk replay initiated |
| DLQ_BULK_REPLAY_COMPLETED | Bulk replay finished |
| DLQ_EVENT_DISCARDED | Event manually discarded (rare, audited) |

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Auto-Retry Rules (T1)

```json
{
  "content_pack_type": "T1",
  "jurisdiction": "NP",
  "name": "nepal-dlq-auto-retry-rules",
  "auto_retry_rules": [
    {
      "name": "nepse-gateway-timeout",
      "match": { "error_class_regex": "^NepseGatewayTimeout$" },
      "action": { "auto_replay": true, "delay_seconds": 600, "rca_auto_tag": "AUTO:NEPSE_TIMEOUT" }
    }
  ]
}
```

### 9.2 Custom DLQ Handlers (T3)

```typescript
interface DLQHandler {
  /** Custom pre-replay hook — e.g., event transformation */
  beforeReplay(event: DLQEvent): Promise<EventEnvelope>;

  /** Custom post-replay hook — e.g., reconciliation check */
  afterReplay(event: DLQEvent, result: ReplayResult): Promise<void>;
}
```

### 9.3 Future: AI-Assisted RCA

- Pattern matching across DLQ events to suggest root causes
- Similar incident detection from historical RCA database
- Automated remediation suggestions

---

## 10. TEST PLAN

### 10.1 Unit Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| UT-DLQ-001 | Poison pill detection | Event quarantined after N failures |
| UT-DLQ-002 | RCA requirement | Replay blocked without RCA |
| UT-DLQ-003 | Idempotency check | Conflict detected for already-processed events |
| UT-DLQ-004 | Auto-retry rule matching | Transient errors auto-retried |
| UT-DLQ-005 | Replay rate limiter | Events published at configured rate |
| UT-DLQ-006 | DLQ filter queries | Correct events returned by filter |

### 10.2 Integration Tests

| Test | Description | Assertion |
|------|-------------|-----------|
| IT-DLQ-001 | K-05 failure → DLQ ingestion | Failed event appears in DLQ |
| IT-DLQ-002 | RCA + replay → K-05 re-publish | Event re-published and processed |
| IT-DLQ-003 | Bulk replay dry-run → conflict detection | Conflicts identified correctly |
| IT-DLQ-004 | Bulk replay execution → rate limited | 100 events replayed within expected time |
| IT-DLQ-005 | K-07 audit trail | All DLQ operations audited |
| IT-DLQ-006 | Tenant isolation | Tenant A cannot see Tenant B's DLQ events |

### 10.3 Chaos Tests

| Test | Description | Expected Behavior |
|------|-------------|-------------------|
| CT-DLQ-001 | DLQ DB unavailable during ingestion | Events buffered in K-05, retried |
| CT-DLQ-002 | Replay target service crashes | Replay recorded as failed, event remains in DLQ |
| CT-DLQ-003 | Bulk replay mid-failure | Progress saved, resumable |
| CT-DLQ-004 | Poison pill flood (100 poison events) | All quarantined, alerts fired, DLQ functional |

---

**END OF K-19 DLQ MANAGEMENT & EVENT REPLAY LLD**
