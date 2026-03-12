# LOW-LEVEL DESIGN: K-05 EVENT BUS, EVENT STORE & WORKFLOW ORCHESTRATION

**Module**: K-05 Event Bus, Event Store & Workflow Orchestration  
**Layer**: Kernel  
**Version**: 1.0.0  
**Status**: Implementation-Ready  
**Owner**: Platform Core Team

---

## 1. MODULE OVERVIEW

### 1.1 Purpose & Responsibilities

The Event Bus provides **append-only event storage, reliable delivery, and saga-based workflow orchestration** for all asynchronous communication across Project Siddhanta.

**Core Responsibilities**:
- Append-only event store with immutable history
- Schema validation for all events
- At-least-once delivery guarantees
- Idempotency enforcement
- Saga orchestration for distributed transactions
- Event replay for projections and debugging
- Dual-calendar timestamping (BS + Gregorian)
- Projection rebuild from event history
- Schema evolution with backward compatibility

**Invariants**:
1. Events MUST be immutable once written
2. Event ordering MUST be preserved per aggregate
3. All events MUST have dual-calendar timestamps
4. Delivery MUST be at-least-once (consumers handle idempotency)
5. Schema changes MUST be backward compatible

### 1.2 Explicit Non-Goals

- ❌ Real-time streaming analytics (use dedicated analytics engine)
- ❌ Message transformation/enrichment (consumers responsible)
- ❌ Event filtering at bus level (consumers filter)

### 1.3 Dependencies

| Dependency | Purpose | Readiness Gate |
|------------|---------|----------------|
| K-02 Config Engine | Event schema registry | K-02 stable |
| K-07 Audit Framework | Event publication audit | K-07 stable |
| PostgreSQL | Event store persistence | DB available |
| Kafka/NATS | Event streaming | Broker available |

---

## 2. PUBLIC APIS & CONTRACTS

### 2.1 REST API Endpoints

```yaml
POST /api/v1/events/publish
Authorization: Bearer {service_token}
Content-Type: application/json

Request:
{
  "event_type": "OrderPlaced",
  "event_version": "1.0.0",
  "aggregate_id": "order_123",
  "aggregate_type": "Order",
  "data": {
    "order_id": "order_123",
    "instrument_id": "NABIL",
    "quantity": 100,
    "price": 1250.50
  },
  "metadata": {
    "tenant_id": "tenant_np_1",
    "user_id": "user_456",
    "trace_id": "abc-123"
  },
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}

Response 201:
{
  "event_id": "evt_7a8b9c0d",
  "sequence_number": 1523,
  "partition": 3,
  "timestamp": "2025-03-02T10:30:00.123Z"
}

Response 400:
{
  "error": "SCHEMA_VALIDATION_FAILED",
  "message": "Event data does not match schema OrderPlaced v1.0.0",
  "code": "EVENT_E001",
  "validation_errors": [
    "Missing required field: order_id"
  ]
}
```

```yaml
GET /api/v1/events/stream/{aggregate_type}/{aggregate_id}
Authorization: Bearer {service_token}

Response 200:
{
  "aggregate_id": "order_123",
  "aggregate_type": "Order",
  "events": [
    {
      "event_id": "evt_1",
      "event_type": "OrderPlaced",
      "event_version": "1.0.0",
      "sequence_number": 1,
      "data": {...},
      "timestamp": "2025-03-02T10:30:00Z"
    },
    {
      "event_id": "evt_2",
      "event_type": "OrderExecuted",
      "event_version": "1.0.0",
      "sequence_number": 2,
      "data": {...},
      "timestamp": "2025-03-02T10:31:00Z"
    }
  ]
}
```

```yaml
POST /api/v1/sagas/start
Authorization: Bearer {service_token}

Request:
{
  "saga_type": "OrderPlacementSaga",
  "saga_id": "saga_123",
  "initial_data": {
    "order_id": "order_123",
    "quantity": 100
  },
  "compensation_timeout_seconds": 300
}

Response 202:
{
  "saga_id": "saga_123",
  "status": "STARTED",
  "current_step": "validate_order"
}
```

### 2.2 gRPC Service Definition

```protobuf
syntax = "proto3";

package siddhanta.events.v1;

service EventBusService {
  rpc PublishEvent(PublishEventRequest) returns (PublishEventResponse);
  rpc SubscribeEvents(SubscribeEventsRequest) returns (stream Event);
  rpc GetEventStream(GetEventStreamRequest) returns (GetEventStreamResponse);
  rpc ReplayEvents(ReplayEventsRequest) returns (stream Event);
}

message PublishEventRequest {
  string event_type = 1;
  string event_version = 2;
  string aggregate_id = 3;
  string aggregate_type = 4;
  google.protobuf.Struct data = 5;
  EventMetadata metadata = 6;
  string timestamp_bs = 7;
  google.protobuf.Timestamp timestamp_gregorian = 8;
}

message PublishEventResponse {
  string event_id = 1;
  int64 sequence_number = 2;
  int32 partition = 3;
  google.protobuf.Timestamp timestamp = 4;
}

message Event {
  string event_id = 1;
  string event_type = 2;
  string event_version = 3;
  string aggregate_id = 4;
  string aggregate_type = 5;
  int64 sequence_number = 6;
  google.protobuf.Struct data = 7;
  EventMetadata metadata = 8;
  string timestamp_bs = 9;
  google.protobuf.Timestamp timestamp_gregorian = 10;
}

message EventMetadata {
  string tenant_id = 1;
  string user_id = 2;
  string trace_id = 3;
  string causation_id = 4;
  string correlation_id = 5;
}
```

### 2.3 SDK Method Signatures

```typescript
interface EventBusClient {
  /**
   * Publish event to event store
   * @throws SchemaValidationError, PublishError
   */
  publish<T>(event: EventEnvelope<T>): Promise<PublishResult>;

  /**
   * Subscribe to event types
   * Returns async iterator of events
   */
  subscribe(
    eventTypes: string[],
    options?: SubscribeOptions
  ): AsyncIterator<Event>;

  /**
   * Get event stream for aggregate
   */
  getEventStream(
    aggregateType: string,
    aggregateId: string
  ): Promise<Event[]>;

  /**
   * Replay events from timestamp
   */
  replay(
    fromTimestamp: Date,
    toTimestamp?: Date,
    eventTypes?: string[]
  ): AsyncIterator<Event>;
}

interface SagaOrchestrator {
  /**
   * Start saga execution
   */
  startSaga<T>(
    sagaType: string,
    sagaId: string,
    initialData: T
  ): Promise<SagaExecution>;

  /**
   * Get saga status
   */
  getSagaStatus(sagaId: string): Promise<SagaStatus>;

  /**
   * Compensate failed saga
   */
  compensate(sagaId: string): Promise<void>;
}

interface EventEnvelope<T> {
  eventType: string;
  eventVersion: string;
  aggregateId: string;
  aggregateType: string;
  data: T;
  metadata: EventMetadata;
  timestampBs: string;
  timestampGregorian: Date;
}

interface PublishResult {
  eventId: string;
  sequenceNumber: number;
  partition: number;
  timestamp: Date;
}
```

### 2.4 Error Model

| Error Code | HTTP Status | Retryable | Description |
|------------|-------------|-----------|-------------|
| EVENT_E001 | 400 | No | Schema validation failed |
| EVENT_E002 | 409 | No | Duplicate event ID |
| EVENT_E003 | 500 | Yes | Event store unavailable |
| EVENT_E004 | 400 | No | Invalid event version |
| EVENT_E005 | 500 | Yes | Kafka broker unavailable |
| EVENT_E006 | 404 | No | Aggregate not found |
| SAGA_E001 | 409 | No | Saga already started |
| SAGA_E002 | 500 | Yes | Saga step timeout |
| SAGA_E003 | 500 | No | Compensation failed |

---

## 3. DATA MODEL

### 3.1 Event Schemas

#### OrderPlacedEvent v1.0.0

```json
{
  "event_id": "evt_7a8b9c0d",
  "event_type": "OrderPlaced",
  "event_version": "1.0.0",
  "aggregate_id": "order_123",
  "aggregate_type": "Order",
  "sequence_number": 1,
  "data": {
    "order_id": "order_123",
    "instrument_id": "NABIL",
    "quantity": 100,
    "price": 1250.50,
    "order_type": "LIMIT",
    "side": "BUY"
  },
  "metadata": {
    "tenant_id": "tenant_np_1",
    "user_id": "user_456",
    "trace_id": "abc-123",
    "causation_id": "cmd_789",
    "correlation_id": "corr_123"
  },
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

### 3.2 Storage Tables

#### events

```sql
CREATE TABLE events (
  event_id VARCHAR(255) PRIMARY KEY,
  event_type VARCHAR(255) NOT NULL,
  event_version VARCHAR(50) NOT NULL,
  aggregate_id VARCHAR(255) NOT NULL,
  aggregate_type VARCHAR(100) NOT NULL,
  sequence_number BIGSERIAL NOT NULL,
  data JSONB NOT NULL,
  metadata JSONB NOT NULL,
  timestamp_bs VARCHAR(10) NOT NULL,
  timestamp_gregorian TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE (aggregate_id, sequence_number)
);

CREATE INDEX idx_events_aggregate ON events(aggregate_type, aggregate_id, sequence_number);
CREATE INDEX idx_events_type ON events(event_type, timestamp_gregorian);
CREATE INDEX idx_events_timestamp ON events(timestamp_gregorian);
```

#### saga_executions

```sql
CREATE TABLE saga_executions (
  saga_id VARCHAR(255) PRIMARY KEY,
  saga_type VARCHAR(255) NOT NULL,
  status VARCHAR(50) NOT NULL CHECK (status IN ('STARTED', 'IN_PROGRESS', 'COMPLETED', 'COMPENSATING', 'COMPENSATED', 'FAILED')),
  current_step VARCHAR(255),
  initial_data JSONB NOT NULL,
  step_results JSONB,
  started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  completed_at TIMESTAMPTZ,
  compensation_timeout_seconds INT NOT NULL DEFAULT 300
);

CREATE INDEX idx_saga_status ON saga_executions(status, started_at);
```

#### saga_steps

```sql
CREATE TABLE saga_steps (
  step_id UUID PRIMARY KEY,
  saga_id VARCHAR(255) NOT NULL REFERENCES saga_executions(saga_id),
  step_name VARCHAR(255) NOT NULL,
  step_order INT NOT NULL,
  status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'COMPENSATED')),
  input_data JSONB,
  output_data JSONB,
  error_message TEXT,
  started_at TIMESTAMPTZ,
  completed_at TIMESTAMPTZ,
  UNIQUE (saga_id, step_order)
);

CREATE INDEX idx_saga_steps_saga ON saga_steps(saga_id, step_order);
```

---

## 4. CONTROL FLOW

### 4.1 Event Publication Flow

```
Service → EventBusClient.publish(event)
  ↓
EventBusClient → Validate event schema against registry (K-02)
  ↓ [Schema valid]
EventBusClient → Generate event_id (UUID)
  ↓
EventBusClient → Assign sequence_number (per aggregate)
  ↓
EventBusClient → Insert into events table (PostgreSQL)
  ↓ [Transaction committed]
EventBusClient → Publish to Kafka topic (event_type)
  ↓
Kafka → Partition by aggregate_id
  ↓
EventBusClient → Return PublishResult
  ↓
Subscribers → Receive event from Kafka
  ↓
Subscribers → Process event (idempotent)
  ↓
Subscribers → Ack message to Kafka
```

### 4.2 Saga Orchestration Flow

```
Service → SagaOrchestrator.startSaga(sagaType, sagaId, data)
  ↓
SagaOrchestrator → Create saga_execution (status=STARTED)
  ↓
SagaOrchestrator → Load saga definition (steps)
  ↓
SagaOrchestrator → Execute step 1
  ↓
Step Handler → Perform action (e.g., reserve inventory)
  ↓ [Success]
Step Handler → Return result
  ↓
SagaOrchestrator → Save step result
  ↓
SagaOrchestrator → Execute step 2
  ↓
Step Handler → Perform action (e.g., charge payment)
  ↓ [Failure]
Step Handler → Throw error
  ↓
SagaOrchestrator → Update saga status=COMPENSATING
  ↓
SagaOrchestrator → Execute compensation for step 1
  ↓
Compensation Handler → Undo step 1 (e.g., release inventory)
  ↓
SagaOrchestrator → Update saga status=COMPENSATED
```

### 4.3 Event Replay Flow

```
Admin → EventBusClient.replay(fromTimestamp, toTimestamp)
  ↓
EventBusClient → Query events table with timestamp range
  ↓
EventBusClient → Stream events in sequence order
  ↓
Projection Builder → Receive event
  ↓
Projection Builder → Apply event to projection
  ↓
Projection Builder → Update projection state
  ↓
Projection Builder → Ack event
  ↓
[Repeat for all events]
  ↓
Projection Builder → Projection rebuilt
```

---

## 5. ALGORITHMS & POLICIES

### 5.1 Idempotency Enforcement

```python
import hashlib

def ensure_idempotency(event: Event, consumer_id: str) -> bool:
    """
    Check if event already processed by consumer.
    Uses event_id + consumer_id as idempotency key.
    """
    idempotency_key = f"{event.event_id}:{consumer_id}"
    
    # Check if already processed
    existing = db.query(
        "SELECT 1 FROM processed_events WHERE idempotency_key = %s",
        (idempotency_key,)
    )
    
    if existing:
        logger.info(f"Event {event.event_id} already processed by {consumer_id}")
        return False  # Skip processing
    
    # Mark as processed (within same transaction as business logic)
    db.execute(
        "INSERT INTO processed_events (idempotency_key, event_id, consumer_id, processed_at) VALUES (%s, %s, %s, NOW())",
        (idempotency_key, event.event_id, consumer_id)
    )
    
    return True  # Process event
```

### 5.2 Saga Compensation Algorithm

```python
class SagaOrchestrator:
    """
    Execute saga with automatic compensation on failure.
    """
    
    async def execute_saga(self, saga_def: SagaDefinition, saga_id: str, data: dict):
        """
        Execute saga steps in order.
        Compensate in reverse order on failure.
        """
        completed_steps = []
        
        try:
            for step in saga_def.steps:
                # Execute step
                result = await self.execute_step(saga_id, step, data)
                
                # Save result
                completed_steps.append((step, result))
                data.update(result)
                
                # Update saga state
                self.update_saga_step(saga_id, step.name, 'COMPLETED', result)
            
            # All steps completed
            self.update_saga_status(saga_id, 'COMPLETED')
            return data
        
        except Exception as e:
            logger.error(f"Saga {saga_id} failed at step {step.name}: {e}")
            
            # Compensate in reverse order
            self.update_saga_status(saga_id, 'COMPENSATING')
            
            for step, result in reversed(completed_steps):
                try:
                    await self.compensate_step(saga_id, step, result)
                    self.update_saga_step(saga_id, step.name, 'COMPENSATED')
                except Exception as comp_error:
                    logger.error(f"Compensation failed for step {step.name}: {comp_error}")
                    self.update_saga_status(saga_id, 'FAILED')
                    raise
            
            self.update_saga_status(saga_id, 'COMPENSATED')
            raise
```

### 5.3 Event Partitioning Strategy

```python
def calculate_partition(aggregate_id: str, num_partitions: int) -> int:
    """
    Partition events by aggregate_id to preserve ordering.
    """
    # Hash aggregate_id
    hash_value = int(hashlib.md5(aggregate_id.encode()).hexdigest(), 16)
    
    # Modulo to get partition
    partition = hash_value % num_partitions
    
    return partition
```

### 5.4 Schema Evolution Policy

```python
class SchemaEvolution:
    """
    Enforce backward compatibility for event schemas.
    """
    
    @staticmethod
    def validate_schema_change(old_schema: dict, new_schema: dict) -> bool:
        """
        Validate that new schema is backward compatible.
        
        Allowed changes:
        - Add optional fields
        - Add new enum values
        
        Prohibited changes:
        - Remove fields
        - Change field types
        - Make optional field required
        """
        old_fields = set(old_schema.get('required', []))
        new_fields = set(new_schema.get('required', []))
        
        # Check no required fields removed
        if old_fields - new_fields:
            raise SchemaIncompatibleError(
                f"Cannot remove required fields: {old_fields - new_fields}"
            )
        
        # Check field types unchanged
        for field, old_type in old_schema.get('properties', {}).items():
            if field in new_schema.get('properties', {}):
                new_type = new_schema['properties'][field]
                if old_type.get('type') != new_type.get('type'):
                    raise SchemaIncompatibleError(
                        f"Cannot change type of field {field}"
                    )
        
        return True
```

---

## 6. NFR BUDGETS

### 6.1 Latency Budgets

| Operation | P50 | P95 | P99 | Timeout | Notes |
|-----------|-----|-----|-----|---------|-------|
| publish() - critical path (sync) | 0.5ms | 1ms | 2ms | 100ms | OMS, Ledger — matches EPIC-K-05 NFR |
| publish() - standard (sync) | 2ms | 5ms | 10ms | 1000ms | Non-critical producers |
| publish() - async ack (Kafka ACK=all) | 5ms | 15ms | 50ms | 5000ms | Full replication confirmation |
| getEventStream() | 2ms | 10ms | 30ms | 5000ms | Stream subscription |
| replay() - per event | 0.5ms | 2ms | 5ms | N/A | Projection rebuild |

> **Note**: Critical-path publish latency (2ms P99) is essential for the D-01 OMS latency budget of 12ms end-to-end. The bus achieves this via batching, direct Kafka producer with `acks=1` for critical topics, and in-memory schema validation cache.

### 6.2 Throughput Targets

| Operation | Target TPS | Peak TPS |
|-----------|------------|----------|
| publish() | 100,000 | 200,000 |
| consume() | 200,000 | 400,000 |

### 6.3 Durability Guarantees

- **Event Store**: Synchronous replication to 3 PostgreSQL replicas
- **Kafka**: min.insync.replicas = 2, replication.factor = 3
- **RPO**: 0 (no data loss)
- **RTO**: < 5 minutes (automatic failover)

---

## 7. SECURITY DESIGN

### 7.1 Event Encryption at Rest

```python
from cryptography.fernet import Fernet

class EventEncryption:
    """
    Encrypt sensitive event data at rest.
    """
    
    def __init__(self, encryption_key: bytes):
        self.cipher = Fernet(encryption_key)
    
    def encrypt_event_data(self, data: dict, sensitive_fields: list[str]) -> dict:
        """
        Encrypt sensitive fields in event data.
        """
        encrypted_data = data.copy()
        
        for field in sensitive_fields:
            if field in encrypted_data:
                plaintext = str(encrypted_data[field]).encode()
                ciphertext = self.cipher.encrypt(plaintext)
                encrypted_data[field] = {
                    '_encrypted': True,
                    '_value': ciphertext.decode()
                }
        
        return encrypted_data
    
    def decrypt_event_data(self, data: dict) -> dict:
        """
        Decrypt encrypted fields in event data.
        """
        decrypted_data = data.copy()
        
        for field, value in decrypted_data.items():
            if isinstance(value, dict) and value.get('_encrypted'):
                ciphertext = value['_value'].encode()
                plaintext = self.cipher.decrypt(ciphertext)
                decrypted_data[field] = plaintext.decode()
        
        return decrypted_data
```

### 7.2 Tenant Isolation

```sql
-- Row-level security for tenant isolation
CREATE POLICY tenant_isolation ON events
  USING (metadata->>'tenant_id' = current_setting('app.tenant_id'));

ALTER TABLE events ENABLE ROW LEVEL SECURITY;
```

### 7.3 Event Signing

```python
import hmac
import hashlib

def sign_event(event: Event, secret_key: bytes) -> str:
    """
    Sign event with HMAC-SHA256.
    """
    message = f"{event.event_id}:{event.aggregate_id}:{event.data}".encode()
    signature = hmac.new(secret_key, message, hashlib.sha256).hexdigest()
    return signature

def verify_event_signature(event: Event, signature: str, secret_key: bytes) -> bool:
    """
    Verify event signature.
    """
    expected_signature = sign_event(event, secret_key)
    return hmac.compare_digest(signature, expected_signature)
```

---

## 8. OBSERVABILITY & AUDIT

### 8.1 Metrics

```yaml
metrics:
  - name: events_published_total
    type: counter
    labels: [event_type, aggregate_type, tenant_id]
  
  - name: events_publish_latency_seconds
    type: histogram
    labels: [event_type]
    buckets: [0.001, 0.005, 0.01, 0.05, 0.1, 0.5, 1.0]
  
  - name: events_consumed_total
    type: counter
    labels: [event_type, consumer_group]
  
  - name: events_consumer_lag
    type: gauge
    labels: [consumer_group, partition]
  
  - name: saga_executions_total
    type: counter
    labels: [saga_type, status]
  
  - name: saga_step_duration_seconds
    type: histogram
    labels: [saga_type, step_name]
```

### 8.2 Structured Logs

```json
{
  "timestamp": "2025-03-02T10:30:00.123Z",
  "level": "INFO",
  "service": "event-bus",
  "trace_id": "abc-123",
  "action": "EVENT_PUBLISHED",
  "event_id": "evt_7a8b9c0d",
  "event_type": "OrderPlaced",
  "aggregate_id": "order_123",
  "sequence_number": 1,
  "partition": 3
}
```

### 8.3 Distributed Tracing

```typescript
// OpenTelemetry tracing
async function publishEvent(event: Event): Promise<PublishResult> {
  const span = tracer.startSpan('event.publish', {
    attributes: {
      'event.type': event.eventType,
      'event.aggregate_id': event.aggregateId,
      'event.tenant_id': event.metadata.tenantId
    }
  });

  try {
    // Validate schema
    const validateSpan = tracer.startSpan('event.validate_schema', { parent: span });
    await validateSchema(event);
    validateSpan.end();

    // Write to store
    const writeSpan = tracer.startSpan('event.write_store', { parent: span });
    const result = await writeToStore(event);
    writeSpan.end();

    // Publish to Kafka
    const kafkaSpan = tracer.startSpan('event.publish_kafka', { parent: span });
    await publishToKafka(event);
    kafkaSpan.end();

    span.setStatus({ code: SpanStatusCode.OK });
    return result;
  } catch (error) {
    span.recordException(error);
    span.setStatus({ code: SpanStatusCode.ERROR });
    throw error;
  } finally {
    span.end();
  }
}
```

---

## 9. EXTENSIBILITY & EVOLUTION

### 9.1 Custom Event Handlers

```typescript
interface EventHandler<T = any> {
  eventType: string;
  handle(event: Event<T>): Promise<void>;
}

class OrderPlacedHandler implements EventHandler<OrderPlacedData> {
  eventType = 'OrderPlaced';

  async handle(event: Event<OrderPlacedData>): Promise<void> {
    // Update read model
    await this.updateOrderReadModel(event.data);
    
    // Trigger downstream actions
    await this.notifyRiskManagement(event.data);
  }

  private async updateOrderReadModel(data: OrderPlacedData): Promise<void> {
    // Implementation
  }

  private async notifyRiskManagement(data: OrderPlacedData): Promise<void> {
    // Implementation
  }
}

// Register handler
eventBus.registerHandler(new OrderPlacedHandler());
```

### 9.2 Saga Definition DSL

```typescript
const orderPlacementSaga = defineSaga({
  name: 'OrderPlacementSaga',
  steps: [
    {
      name: 'validate_order',
      action: async (data) => {
        const result = await orderService.validate(data.orderId);
        return { validationResult: result };
      },
      compensation: async (data) => {
        // No compensation needed for validation
      }
    },
    {
      name: 'reserve_inventory',
      action: async (data) => {
        const reservation = await inventoryService.reserve(
          data.instrumentId,
          data.quantity
        );
        return { reservationId: reservation.id };
      },
      compensation: async (data) => {
        await inventoryService.release(data.reservationId);
      }
    },
    {
      name: 'charge_payment',
      action: async (data) => {
        const payment = await paymentService.charge(
          data.userId,
          data.amount
        );
        return { paymentId: payment.id };
      },
      compensation: async (data) => {
        await paymentService.refund(data.paymentId);
      }
    },
    {
      name: 'confirm_order',
      action: async (data) => {
        await orderService.confirm(data.orderId);
        return { confirmed: true };
      },
      compensation: async (data) => {
        await orderService.cancel(data.orderId);
      }
    }
  ]
});
```

### 9.3 Projection Rebuild

```typescript
class ProjectionRebuilder {
  async rebuild(
    projectionName: string,
    fromTimestamp?: Date
  ): Promise<void> {
    // Clear existing projection
    await this.clearProjection(projectionName);

    // Replay events
    const events = eventBus.replay(fromTimestamp || new Date(0));

    for await (const event of events) {
      // Apply event to projection
      await this.applyEventToProjection(projectionName, event);
    }

    logger.info(`Projection ${projectionName} rebuilt`);
  }

  private async applyEventToProjection(
    projectionName: string,
    event: Event
  ): Promise<void> {
    const handler = this.getProjectionHandler(projectionName, event.eventType);
    if (handler) {
      await handler(event);
    }
  }
}
```

---

## 10. TEST PLAN

### 10.1 Unit Tests

```typescript
describe('EventBus', () => {
  it('should publish event with valid schema', async () => {
    const event: EventEnvelope<OrderPlacedData> = {
      eventType: 'OrderPlaced',
      eventVersion: '1.0.0',
      aggregateId: 'order_123',
      aggregateType: 'Order',
      data: {
        orderId: 'order_123',
        instrumentId: 'NABIL',
        quantity: 100,
        price: 1250.50
      },
      metadata: {
        tenantId: 'tenant_np_1',
        userId: 'user_456',
        traceId: 'abc-123'
      },
      timestampBs: '2081-11-17',
      timestampGregorian: new Date()
    };

    const result = await eventBus.publish(event);

    expect(result.eventId).toBeDefined();
    expect(result.sequenceNumber).toBeGreaterThan(0);
  });

  it('should reject event with invalid schema', async () => {
    const invalidEvent = {
      eventType: 'OrderPlaced',
      eventVersion: '1.0.0',
      aggregateId: 'order_123',
      data: {
        // Missing required field: orderId
        quantity: 100
      }
    };

    await expect(eventBus.publish(invalidEvent as any))
      .rejects.toThrow(SchemaValidationError);
  });
});
```

### 10.2 Integration Tests

```typescript
describe('Saga Orchestration', () => {
  it('should complete saga successfully', async () => {
    const sagaId = 'saga_test_1';
    const initialData = {
      orderId: 'order_123',
      quantity: 100,
      amount: 125050.00
    };

    const execution = await sagaOrchestrator.startSaga(
      'OrderPlacementSaga',
      sagaId,
      initialData
    );

    // Wait for completion
    await waitForSagaCompletion(sagaId, 10000);

    const status = await sagaOrchestrator.getSagaStatus(sagaId);
    expect(status.status).toBe('COMPLETED');
  });

  it('should compensate failed saga', async () => {
    // Mock payment service to fail
    paymentService.charge = jest.fn().mockRejectedValue(
      new Error('Insufficient funds')
    );

    const sagaId = 'saga_test_2';
    const initialData = {
      orderId: 'order_456',
      quantity: 100,
      amount: 125050.00
    };

    await expect(
      sagaOrchestrator.startSaga('OrderPlacementSaga', sagaId, initialData)
    ).rejects.toThrow();

    const status = await sagaOrchestrator.getSagaStatus(sagaId);
    expect(status.status).toBe('COMPENSATED');

    // Verify inventory released
    const inventory = await inventoryService.getReservation(
      initialData.orderId
    );
    expect(inventory).toBeNull();
  });
});
```

### 10.3 Replay Tests

```typescript
describe('Event Replay', () => {
  it('should replay events in order', async () => {
    // Publish test events
    const events = [
      { eventType: 'OrderPlaced', aggregateId: 'order_1', data: {...} },
      { eventType: 'OrderExecuted', aggregateId: 'order_1', data: {...} },
      { eventType: 'OrderSettled', aggregateId: 'order_1', data: {...} }
    ];

    for (const event of events) {
      await eventBus.publish(event);
    }

    // Replay
    const replayed: Event[] = [];
    const replayStream = eventBus.replay(new Date(Date.now() - 60000));

    for await (const event of replayStream) {
      if (event.aggregateId === 'order_1') {
        replayed.push(event);
      }
    }

    expect(replayed).toHaveLength(3);
    expect(replayed[0].eventType).toBe('OrderPlaced');
    expect(replayed[1].eventType).toBe('OrderExecuted');
    expect(replayed[2].eventType).toBe('OrderSettled');
  });
});
```

### 10.4 Chaos Tests

```typescript
describe('Event Bus Resilience', () => {
  it('should handle Kafka broker failure', async () => {
    // Simulate Kafka unavailable
    await stopKafkaBroker();

    const event = createTestEvent();

    // Should still write to event store
    const result = await eventBus.publish(event);
    expect(result.eventId).toBeDefined();

    // Verify event in store
    const stored = await getEventFromStore(result.eventId);
    expect(stored).toBeDefined();

    // Restart Kafka
    await startKafkaBroker();

    // Event should be published to Kafka eventually
    await waitForKafkaMessage(event.eventType, 5000);
  });

  it('should enforce idempotency on duplicate delivery', async () => {
    const event = createTestEvent();
    await eventBus.publish(event);

    let processCount = 0;
    const handler = async (e: Event) => {
      if (await ensureIdempotency(e, 'test_consumer')) {
        processCount++;
      }
    };

    // Process same event twice
    await handler(event);
    await handler(event);

    expect(processCount).toBe(1);
  });
});
```

---

## 11. VALIDATION QUESTIONS & ASSUMPTIONS

### Assumptions

1. **[ASSUMPTION]** Event ordering only required per aggregate
   - **Validation**: Are there cross-aggregate ordering requirements?
   - **Impact**: May need global ordering mechanism

2. **[ASSUMPTION]** Saga compensation always succeeds
   - **Validation**: What if compensation fails?
   - **Impact**: May need manual intervention workflow

3. **[ASSUMPTION]** Event replay is infrequent (admin operation)
   - **Validation**: Is continuous replay needed for projections?
   - **Impact**: May need dedicated replay infrastructure

4. **[ASSUMPTION]** Kafka is primary event broker
   - **Validation**: Are there air-gapped deployments without Kafka?
   - **Impact**: May need pluggable broker abstraction

---

**END OF LLD: K-05 EVENT BUS, EVENT STORE & WORKFLOW ORCHESTRATION**
