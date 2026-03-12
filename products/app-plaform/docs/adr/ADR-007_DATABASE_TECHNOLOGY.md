# ADR-007: Database Technology Selection
## Project Siddhanta - Architectural Decision Record

**Status**: Accepted  
**Date**: 2026-03-08  
**Decision**: PostgreSQL as primary datastore with Redis caching and TimescaleDB for time-series  
**Impact**: High

---

# CONTEXT

## Problem Statement

Project Siddhanta requires a data persistence strategy that supports:

- Event sourcing with append-only immutable event stores
- ACID transactions for financial operations (ledger postings, settlements)
- High-throughput reads for trading path (100K+ TPS)
- Time-series data for market data and metrics
- Multi-tenant isolation at the database level
- 10-year data retention with archival capabilities
- Air-gapped deployment support

## Constraints

1. **Consistency**: Financial transactions require strong consistency (ACID)
2. **Performance**: Sub-millisecond reads on hot path; 100K+ TPS burst
3. **Compliance**: 10-year data retention; immutable audit trails
4. **Multi-Tenant**: Row-level security for tenant isolation
5. **Portability**: Must run on-premise and in multiple cloud providers
6. **Open Source**: Avoid proprietary database lock-in

---

# DECISION

## Architecture Choice

**PostgreSQL 15+ as the primary relational datastore, Redis 7+ as the caching layer, TimescaleDB for time-series data, and Kafka/NATS as the event streaming backbone.**

### **Technology Mapping**

| Data Category | Technology | Justification |
|---------------|-----------|---------------|
| **Event Store** | PostgreSQL + Kafka | Append-only tables with Kafka for event streaming |
| **Configuration** | PostgreSQL + Redis | PostgreSQL for persistence, Redis for hot-reload cache |
| **Audit Logs** | PostgreSQL | Immutable, hash-chained audit entries |
| **Ledger** | PostgreSQL | ACID transactions, double-entry bookkeeping |
| **Sessions** | Redis | Fast session lookups and TTL-based expiry |
| **Market Data** | TimescaleDB | Purpose-built time-series database (PostgreSQL extension) |
| **Object Storage** | S3 / Ceph | Model artifacts, report exports, plugin bundles |
| **Search** | OpenSearch | Full-text search for audit logs, reference data |

### **PostgreSQL Selection Rationale**

1. **ACID Compliance**: Full ACID transactions for financial operations
2. **Row-Level Security (RLS)**: Native multi-tenant isolation
3. **JSONB Support**: Flexible schema for event data and configuration
4. **Extension Ecosystem**: TimescaleDB, PostGIS, pg_partman
5. **Partitioning**: Native table partitioning for retention management
6. **Open Source**: No licensing costs; broad community support
7. **Cloud Portable**: Available on AWS RDS, Azure, GCP, and on-premise

### **Redis Selection Rationale**

1. **Sub-Millisecond Reads**: <0.1ms for cached configuration and sessions
2. **Pub/Sub**: Real-time notifications for config hot-reload
3. **TTL Support**: Automatic session and cache expiry
4. **Data Structures**: Sorted sets for leaderboards, HyperLogLog for cardinality
5. **Cluster Mode**: Horizontal scaling for high-throughput caching

### **Kafka/NATS Selection Rationale**

1. **Event Streaming**: At-least-once delivery for event sourcing
2. **Partitioning**: Ordered delivery per aggregate (partition key)
3. **Durability**: Persistent event log with configurable retention
4. **Consumer Groups**: Scalable event processing
5. **Schema Registry**: Event schema evolution management

---

# CONSEQUENCES

## Positive Consequences

### **Reliability**
- ACID transactions guarantee financial data integrity
- Immutable event store prevents data loss
- Multi-AZ replication for high availability

### **Performance**
- Redis caching delivers sub-millisecond reads on hot path
- PostgreSQL query optimization with indexes and partitions
- TimescaleDB provides native time-series performance

### **Compliance**
- RLS enforces tenant isolation at database level
- Hash-chained audit logs provide tamper detection
- Table partitioning enables 10-year retention with archival

### **Portability**
- All technologies are open source
- Available on all major cloud providers
- Support air-gapped (on-premise) deployment

## Negative Consequences

### **Operational Complexity**
- Multiple database technologies to manage and monitor
- Backup and recovery across PostgreSQL, Redis, Kafka
- Schema migration coordination across services
- **Mitigation**: Automated backup, infrastructure-as-code, schema migration pipelines

### **Consistency Challenges**
- Eventual consistency between event store and read projections
- Cache invalidation complexity with Redis
- **Mitigation**: Outbox pattern (K-17), cache versioning, event replay

---

# ALTERNATIVES CONSIDERED

## Option 1: MongoDB (Document Store)
- **Rejected**: Weaker ACID guarantees; no native RLS; less mature for financial operations
- **Risk**: Data consistency issues; multi-tenant isolation complexity

## Option 2: CockroachDB (Distributed SQL)
- **Rejected**: Higher operational complexity; less ecosystem maturity; license concerns
- **Risk**: Performance overhead for distributed consensus

## Option 3: AWS DynamoDB
- **Rejected**: Cloud lock-in; no air-gap support; limited query flexibility
- **Risk**: Vendor dependency; cost unpredictability

## Option 4: Oracle Database
- **Rejected**: Proprietary licensing; prohibitive cost; limited cloud portability
- **Risk**: Vendor lock-in; budget constraints

---

# IMPLEMENTATION NOTES

## Database Schema Strategy

- **Per-Service Schemas**: Each microservice owns its database schema
- **Event Store Schema**: Shared event store with per-aggregate tables
- **Migration Tool**: Flyway or Knex for schema migrations
- **Partitioning**: Monthly partitions for event tables, daily for audit logs

## Connection Management
- **Connection Pooling**: PgBouncer for PostgreSQL connection pooling
- **Read Replicas**: Async read replicas for reporting queries
- **Redis Sentinel**: Redis Sentinel for high-availability caching

## Backup & Recovery
- **PostgreSQL**: Continuous WAL archiving, daily full backups
- **Redis**: RDB snapshots + AOF persistence
- **Kafka**: Topic replication factor 3, tiered storage for archival

---

**Decision Makers**: Platform Architecture Team, Data Engineering Team  
**Reviewers**: DBA Team, Security Team  
**Approval Date**: 2026-03-08
