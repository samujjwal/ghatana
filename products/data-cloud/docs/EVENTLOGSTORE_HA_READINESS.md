# EventLogStore Multi-Node HA Readiness Assessment

**Task:** CROSS-P0-1: Complete Data Cloud EventLogStore production readiness (multi-node HA)
**Date:** 2026-04-20
**Status:** Assessment Complete

## Current State

### Infrastructure Configuration (Helm)

The Data Cloud Helm chart includes multi-node HA configuration:

- **Replica Count:** 2 (configurable per environment)
- **Kafka Configuration:**
  - Partitions: 4
  - Replication Factor: 3
  - Bootstrap Servers: Configured for cluster access
- **Pod Disruption Budget:** Enabled with minAvailable: 1
- **Horizontal Pod Autoscaler:** Enabled (minReplicas: 2, maxReplicas: 10)
- **Probes:** Liveness, readiness, and startup probes configured

### KafkaEventLogStore Implementation

**HA-Ready Features:**
- **Exactly-once semantics:** Transactional producers with `read_committed` isolation level
- **Configurable partitions:** Supports multiple partitions per topic (default: 4 from helm)
- **Configurable replication factor:** Supports Kafka replication (default: 3 from helm)
- **Idempotent producers:** `ENABLE_IDEMPOTENCE_CONFIG = true`
- **Acks configuration:** `ACKS_CONFIG = "all"` (wait for all replicas)
- **Retries:** `RETRIES_CONFIG = 3`
- **Consumer isolation:** `ISOLATION_LEVEL_CONFIG = "read_committed"`

**Transaction ID Generation:**
```java
props.put(ProducerConfig.TRANSACTIONAL_ID_CONFIG, "datacloud-eventlog-" + UUID.randomUUID());
```

Each instance generates a unique transactional ID, which is correct for multi-node deployments. This ensures each producer has its own transactional identity.

### Topic Creation

Topics are created with:
- Configurable partition count (from helm: 4)
- Configurable replication factor (from helm: 3)
- Per-tenant topic scheme: `datacloud.{tenantId}.events`

## Production Readiness Gaps

### 1. Transactional ID Management

**Current:** Random UUID per instance
**Concern:** In rolling updates or pod restarts, the transactional ID changes, which may cause:
- Zombie fencing to fail for old instances
- Potential duplicate messages during restart
- Transaction coordinator state cleanup delays

**Recommendation:** Consider using a stable transactional ID scheme (e.g., pod-name-based or external coordination) for production HA scenarios with frequent rolling updates.

### 2. Consumer Group Management

**Current:** Random UUID-based group IDs for read and tail operations
**Concern:** 
- No consumer group coordination across instances
- Rebalancing behavior not explicitly configured
- No static membership for stable partition assignment

**Recommendation:** For production HA, consider:
- Static membership for tail subscriptions
- Explicit session timeout configuration
- Consumer group coordination strategies

### 3. Partition Assignment Strategy

**Current:** Single partition (partition 0) used for all operations
**Concern:** 
- All reads/writes go to partition 0, negating multi-partition benefits
- No partition key strategy for load distribution
- Single partition creates hotspots

**Recommendation:** Implement partitioning strategy:
- Use tenant ID as partition key
- Hash tenant ID to partition count
- Enable parallel consumption across partitions

### 4. Monitoring and Observability

**Current:** Basic metrics (append count, read count, timers)
**Missing:**
- Consumer lag metrics per partition
- Producer transaction commit/abort metrics
- Broker connection health metrics
- Partition-level throughput metrics

**Recommendation:** Add comprehensive Kafka metrics for production HA monitoring.

### 5. Failover Testing

**Current:** No documented failover testing
**Missing:**
- Pod crash recovery validation
- Kafka broker failure handling
- Network partition tolerance
- Graceful shutdown verification

**Recommendation:** Add chaos engineering tests for multi-node HA scenarios.

## Recommended Actions

### Phase 1: Critical (P0)

1. **Implement partitioning strategy** - Use tenant ID as partition key to distribute load
2. **Add consumer lag monitoring** - Track lag per tenant/partition for operational visibility
3. **Document failover behavior** - Create runbook for pod/broker failures

### Phase 2: Important (P1)

4. **Review transactional ID strategy** - Evaluate stable transactional ID for rolling updates
5. **Add consumer group coordination** - Static membership for tail subscriptions
6. **Implement chaos testing** - Multi-node HA failure scenario tests

### Phase 3: Enhancement (P2)

7. **Optimize producer/consumer configs** - Tune for production throughput
8. **Add partition rebalancing hooks** - Custom rebalance listeners
9. **Implement dead letter queue** - For failed event processing

## Conclusion

**Current Status:** Partially HA-ready

The KafkaEventLogStore has good foundational HA features (exactly-once semantics, configurable replication, idempotent producers). However, critical gaps exist in:
- Partitioning strategy (currently single-partition)
- Consumer group management
- Observability
- Failover testing

**Verdict:** EventLogStore is **NOT production-ready for multi-node HA** without addressing the partitioning strategy and adding operational monitoring. The helm infrastructure is ready, but the application-level Kafka client configuration needs enhancement for true HA production deployment.

**Next Steps:** Implement Phase 1 critical actions before marking CROSS-P0-1 as complete.
