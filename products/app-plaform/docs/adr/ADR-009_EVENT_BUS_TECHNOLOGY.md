# ADR-009: Event Bus Technology Selection
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: Apache Kafka as primary event bus with NATS for lightweight messaging  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta's event-driven architecture requires a messaging backbone that supports:

- Append-only event sourcing with immutable event store
- At-least-once delivery with idempotency enforcement
- Ordered delivery per aggregate (partition key)
- Schema evolution with backward compatibility
- Saga orchestration for distributed transactions
- Dead letter queue management and event replay
- 100K+ events per second throughput
- 10-year event retention with tiered storage

## Constraints

1. **Durability**: Zero event loss for financial transactions
2. **Ordering**: Strict ordering per aggregate ID
3. **Performance**: <2ms P99 publish latency on critical path
4. **Retention**: 10-year retention for regulatory compliance
5. **Portability**: Must run on-premise and in multiple clouds

---

# DECISION

## Architecture Choice

**Apache Kafka 3+ as the primary event streaming platform, supplemented by NATS for lightweight request-reply and pub/sub patterns. Confluent Schema Registry for event schema management.**

### **Technology Mapping**

| Use Case | Technology | Justification |
|----------|-----------|---------------|
| **Event Sourcing** | Kafka | Durable log, ordered per partition, configurable retention |
| **Domain Events** | Kafka | At-least-once delivery, consumer groups |
| **Saga Commands** | Kafka | Reliable command delivery with DLQ |
| **Lightweight Pub/Sub** | NATS | Ultra-low-latency for config reload signals, heartbeats |
| **Request-Reply** | NATS | Sub-millisecond for internal service coordination |
| **Schema Registry** | Confluent Schema Registry | Avro/JSON schema versioning with compatibility checks |

### **Kafka Configuration Strategy**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| **Replication Factor** | 3 | Tolerate 1 broker failure |
| **Min ISR** | 2 | Guarantee durability with acks=all |
| **Partition Key** | aggregate_id | Ordered delivery per domain aggregate |
| **Retention** | 90 days hot, 10 years tiered | Compliance + performance balance |
| **Compaction** | Enabled for projection topics | Latest state per key |
| **Compression** | LZ4 | Balance of speed and compression ratio |

### **Event Envelope Standard**
```json
{
  "event_id": "uuid-v7",
  "event_type": "OrderPlaced",
  "event_version": "1.0.0",
  "aggregate_id": "order_123",
  "aggregate_type": "Order",
  "sequence_number": 1,
  "causality_id": "uuid",
  "correlation_id": "uuid",
  "trace_id": "uuid",
  "tenant_id": "tenant_001",
  "data": { ... },
  "metadata": { ... },
  "timestamp_bs": "2081-11-17",
  "timestamp_gregorian": "2025-03-02T10:30:00Z"
}
```

---

# CONSEQUENCES

## Positive Consequences

- **Durability**: Kafka's replicated log guarantees zero event loss
- **Ordering**: Partition-key ordering satisfies event sourcing requirements
- **Scalability**: Kafka scales to millions of events/second
- **Ecosystem**: Rich connector ecosystem (Kafka Connect) for integrations
- **Schema Evolution**: Schema Registry prevents breaking changes

## Negative Consequences

- **Operational Complexity**: Kafka cluster management requires expertise
  - **Mitigation**: Managed Kafka (MSK/Confluent Cloud) or dedicated ops team
- **Resource Intensive**: Kafka brokers require significant memory and disk
  - **Mitigation**: Tiered storage offloads old data to object storage
- **Eventual Consistency**: Consumers may lag behind producers
  - **Mitigation**: Consumer lag monitoring, SLO-based alerting

---

# ALTERNATIVES CONSIDERED

## Option 1: RabbitMQ
- **Rejected**: Message-oriented (not log-oriented); no native event replay; weaker ordering
## Option 2: AWS EventBridge
- **Rejected**: Cloud lock-in; no air-gap support; limited retention
## Option 3: Apache Pulsar
- **Rejected**: Less mature ecosystem; smaller community; operational complexity
## Option 4: Redis Streams
- **Rejected**: Limited durability guarantees; not designed for 10-year retention

---

**Decision Makers**: Platform Architecture Team, Data Engineering Team  
**Approval Date**: 2026-03-08
