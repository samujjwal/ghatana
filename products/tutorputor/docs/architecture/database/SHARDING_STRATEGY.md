# Database Sharding Strategy

**Last Updated:** 2026-04-17  
**Version:** 1.0  
**Status:** DEFERRED - Not required at current scale

---

## Overview

This document outlines the database sharding strategy for TutorPutor. Sharding is currently deferred as read replicas provide sufficient scaling. This strategy will be implemented when write throughput or data volume requires it.

---

## Current State

### Read Replicas
**Status:** IMPLEMENTED

- PostgreSQL read replicas configured
- Read/write split logic operational
- Replica health monitoring
- Automatic failover to primary

**Benefits:**
- Scales read operations
- Improves query performance
- Provides high availability

**Limitations:**
- Does not scale write operations
- Single primary database for all writes
- Data limited to single database capacity

---

## Sharding Evaluation

### When to Shard

Sharding should be implemented when:
1. Write throughput exceeds single database capacity (>10,000 writes/second)
2. Data volume exceeds single database capacity (>5 TB)
3. Geographic distribution requires local data centers
4. Tenant isolation requires separate databases

### Current Metrics
- Write throughput: <1,000 writes/second (well below threshold)
- Data volume: <100 GB (well below threshold)
- Geographic distribution: Single region
- Tenant isolation: Implemented at application level

**Conclusion:** Sharding not required at current scale.

---

## Sharding Strategy (Future Implementation)

### Sharding Approach

**Recommended Strategy:** Tenant-based sharding

**Rationale:**
- Natural sharding key (tenantId)
- Clear data ownership boundaries
- Easy to implement cross-tenant queries
- Aligns with multi-tenant architecture

---

### Sharding Key Selection

**Primary Key:** `tenantId`

**Secondary Keys:**
- `userId` (for user-scoped queries)
- `moduleId` (for content-scoped queries)

**Sharding Function:**
```typescript
function getShardId(tenantId: string, totalShards: number): number {
  const hash = crypto.createHash('sha256').update(tenantId).digest('hex');
  const shardId = parseInt(hash.substring(0, 8), 16) % totalShards;
  return shardId;
}
```

---

### Shard Routing

**Application-Level Routing:**
- Middleware intercepts database queries
- Extracts tenantId from context
- Routes to appropriate shard
- Handles cross-shard queries

**Database-Level Routing:**
- PostgreSQL FDW (Foreign Data Wrapper)
- Citus extension (PostgreSQL extension for sharding)
- ProxySQL for query routing

---

### Cross-Shard Queries

**Approach 1: Application-Level Aggregation**
- Query each shard independently
- Aggregate results in application
- Suitable for read-heavy operations

**Approach 2: Distributed Query**
- Use PostgreSQL FDW for cross-shard joins
- Suitable for complex queries
- Performance overhead

**Approach 3: Denormalization**
- Duplicate data across shards
- Query single shard for common operations
- Trade-off: Data consistency complexity

---

### Shard Rebalancing

**Trigger Conditions:**
- Uneven data distribution (>20% variance)
- Hot tenant requiring dedicated shard
- Adding new shards

**Rebalancing Process:**
1. Create new shards
2. Migrate data to new shards
3. Update routing configuration
4. Verify data integrity
5. Retire old shards

**Migration Strategy:**
- Online migration (zero downtime)
- Dual-write during migration
- Verification before cutover
- Rollback capability

---

## Performance Testing

### Test Scenarios

1. **Single-Shard Performance**
   - Baseline performance metrics
   - Query latency
   - Throughput

2. **Multi-Shard Performance**
   - Cross-shard query performance
   - Routing overhead
   - Aggregation performance

3. **Rebalancing Performance**
   - Migration time
   - Performance during migration
   - Impact on application

### Success Criteria

- Query latency <100ms (95th percentile)
- Throughput >10,000 writes/second per shard
- Cross-shard queries <500ms
- Rebalancing <4 hours

---

## Implementation Steps (When Needed)

1. **Phase 1: Preparation**
   - Choose sharding technology (Citus or custom)
   - Design shard schema
   - Implement sharding key selection

2. **Phase 2: Infrastructure**
   - Provision shard databases
   - Configure shard routing
   - Set up monitoring

3. **Phase 3: Migration**
   - Implement dual-write
   - Migrate data to shards
   - Verify data integrity

4. **Phase 4: Cutover**
   - Switch to shard routing
   - Monitor performance
   - Retire primary database

5. **Phase 5: Optimization**
   - Tune shard configuration
   - Optimize cross-shard queries
   - Implement rebalancing

---

## Monitoring

### Metrics

- Shard health status
- Query latency per shard
- Data distribution per shard
- Cross-shard query count
- Rebalancing progress

### Alerts

- Shard health failure
- Uneven data distribution
- High cross-shard query latency
- Rebalancing timeout

---

**Maintained By:** TutorPutor Engineering Team  
**Contact:** See team documentation for ownership
