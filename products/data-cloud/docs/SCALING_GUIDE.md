# Data-Cloud Stateful Scaling Guide

> **Audience**: Platform engineers and SREs operating the Ghatana Data-Cloud stack.  
> **Scope**: Kafka (MSK) partition sizing and ClickHouse shard topology — the two stateful tiers whose throughput must be explicitly pre-planned before EKS workloads scale.

---

## Table of Contents

1. [Why Stateful Scaling Requires Planning](#why-stateful-scaling-requires-planning)
2. [Kafka (MSK) Partition Sizing](#kafka-msk-partition-sizing)
   - [Partition Count Formula](#partition-count-formula)
   - [Per-Topic Recommendations](#per-topic-recommendations)
   - [Partition-Rebalance Runbook](#partition-rebalance-runbook)
3. [ClickHouse Shard Topology](#clickhouse-shard-topology)
   - [Shard-Replica Matrix](#shard-replica-matrix)
   - [Adding a Shard Online (Zero-Downtime)](#adding-a-shard-online-zero-downtime)
   - [remote_servers.xml Config Pattern](#remote_serversxml-config-pattern)
4. [HPA ↔ Stateful Tier Alignment](#hpa--stateful-tier-alignment)
5. [Prometheus Alerts for Scaling Headroom](#prometheus-alerts-for-scaling-headroom)
6. [Quarterly Capacity Review Checklist](#quarterly-capacity-review-checklist)

---

## Why Stateful Scaling Requires Planning

EKS HPA auto-scales stateless workloads within seconds. Kafka and ClickHouse are
**stateful** — their parallelism is bounded at creation time:

| Resource | Parallelism unit | Can be increased online? |
|---|---|---|
| Kafka topic | Partition | ✅ Yes (partition expansion only; cannot shrink) |
| ClickHouse | Shard | ✅ Yes (add shard + run `SYSTEM RELOAD CONFIG`) |
| EKS pod (stateless) | Replica | ✅ Yes (instantaneous) |

The rule is: **provision stateful tiers ahead of the peak replica count**, not
reactively. Over-provisioning partitions is cheap; rebalancing under load is
expensive.

---

## Kafka (MSK) Partition Sizing

### Partition Count Formula

```
partitions_per_topic = max(
    ceil(peak_throughput_mb_per_sec / target_partition_throughput_mb_per_sec),
    max_consumer_replicas * consumer_parallelism_factor
)
```

Default constants used by this platform:

| Constant | Value | Rationale |
|---|---|---|
| `target_partition_throughput_mb_per_sec` | **10 MB/s** | Conservative ceiling for `kafka.m5.large` brokers |
| `consumer_parallelism_factor` | **2** | Each consumer replica handles 2 partitions in steady state; spike headroom |
| `min_partitions_per_topic` | **6** | Never fewer — allows 3× shard growth without re-partitioning |

**Example — `datacloud.events` topic:**

```
peak_throughput    = 120 MB/s
by_throughput      = ceil(120 / 10) = 12
max_consumer_pods  = 20  (HPA maxReplicas)
by_consumers       = 20 * 2 = 40
partitions         = max(12, 40) = 40  → round up to nearest multiple of broker_count (3): 42
```

### Per-Topic Recommendations

| Topic | Current partitions | Recommended (for 20 HPA max-replicas) | Notes |
|---|---|---|---|
| `datacloud.events` | 12 | 42 | High-volume ingest pipeline |
| `datacloud.commands` | 6 | 12 | Low-volume, latency-sensitive |
| `datacloud.dlq` | 6 | 6 | DLQ — throughput bounded by error rate |
| `datacloud.notifications` | 6 | 12 | Push fanout — keep low per-partition lag |
| `datacloud.audit` | 3 | 6 | Compliance log — moderate volume |

> **Rule of thumb**: Set `max_partitions = max_consumer_replicas × 2`.  
> Pre-create at that count so HPA can scale without Kafka config changes.

### Partition-Rebalance Runbook

```bash
# 1. Check current partition counts
kafka-topics.sh \
  --bootstrap-server "$MSK_BOOTSTRAP" \
  --describe \
  --topic datacloud.events | grep PartitionCount

# 2. Increase partition count (cannot be reduced — make the decision carefully)
kafka-topics.sh \
  --bootstrap-server "$MSK_BOOTSTRAP" \
  --alter \
  --topic datacloud.events \
  --partitions 42

# 3. Preferred replica election — ensure leadership is balanced after the change
kafka-leader-election.sh \
  --bootstrap-server "$MSK_BOOTSTRAP" \
  --election-type PREFERRED \
  --all-topic-partitions

# 4. Monitor consumer lag after rebalance (wait for 0 lag in all partitions)
kafka-consumer-groups.sh \
  --bootstrap-server "$MSK_BOOTSTRAP" \
  --describe \
  --group data-cloud-event-consumer

# 5. Update Kafka topic config in Terraform to match (prevents drift):
#    In modules/msk/main.tf, add/update aws_msk_topic or aws_kafka_topic resource.
```

**Important constraints:**
- Partition count can **only increase**, never decrease.
- Repartitioning causes consumer group rebalance (brief pause in consumption).
- Always rebalance during low-traffic windows (weekday 02:00–04:00 UTC).
- Coordinate with the on-call to monitor consumer lag during the rebalance.

---

## ClickHouse Shard Topology

ClickHouse horizontal scaling is achieved by adding **shards** to the
`Distributed` table engine. Each shard can optionally have multiple **replicas**
for HA.

### Shard-Replica Matrix

| EKS peak replicas | ClickHouse nodes | Shards | Replicas/shard | Config key |
|---|---|---|---|---|
| 1–5 | 1 | 1 | 1 | `single` |
| 6–15 | 2 | 2 | 1 | `two-shard` |
| 16–40 | 4 | 2 | 2 | `two-shard-ha` |
| 41–100 | 6 | 3 | 2 | `three-shard-ha` |
| 100+ | 8 | 4 | 2 | `four-shard-ha` |

> For the Data-Cloud platform, target the `two-shard-ha` tier (4 nodes) for
> production and `single` for staging/DR cold-standby.

### Adding a Shard Online (Zero-Downtime)

```bash
# ── Step 1: Provision new ClickHouse node ──────────────────────────────────
# Increase var.clickhouse_node_count in terraform.tfvars:
#   clickhouse_node_count = 4   # was 2
terraform -chdir=products/data-cloud/terraform apply \
  -var-file=environments/production/terraform.tfvars \
  -target=module.clickhouse

# ── Step 2: Update remote_servers.xml on ALL existing nodes ───────────────
# Terraform will push the new SSM Parameter automatically.
# Use the following Ansible playbook (or the CloudInit upgrade path) to apply:
ansible-playbook \
  -i inventory/production.ini \
  playbooks/clickhouse-reload-config.yml \
  -e "ssm_param=/${NAME_PREFIX}/clickhouse/remote-servers-config"

# The playbook calls: SYSTEM RELOAD CONFIG; on each node to pick up the
# new shard definition without restart.

# ── Step 3: Redistribute data ─────────────────────────────────────────────
# After nodes reload config, existing data is NOT automatically redistributed.
# For tables using ReplicatedMergeTree + Distributed:
clickhouse-client --query "
  INSERT INTO distributed_events
  SELECT * FROM shard_events WHERE _shard_num < numShards();
"
# For large tables, use the clickhouse-copier utility instead:
clickhouse-copier \
  --config-file copier-config.xml \
  --task-path /clickhouse/copier/tasks/rebalance-events \
  --base-dir /var/lib/clickhouse-copier

# ── Step 4: Verify ────────────────────────────────────────────────────────
clickhouse-client --query "
  SELECT shard_num, count() AS rows
  FROM cluster('data_cloud_global', default, shard_events)
  GROUP BY shard_num
  ORDER BY shard_num;
"
```

### remote_servers.xml Config Pattern

The `cross-region-replication` Terraform module stores the ClickHouse cluster
definition in SSM Parameter Store under:

```
/${name_prefix}/clickhouse/remote-servers-config
```

The XML structure defines three cluster names that the application uses:

```xml
<remote_servers>
  <!-- Primary region shards — high-performance read/write -->
  <data_cloud_primary>
    <shard>
      <weight>1</weight>
      <internal_replication>true</internal_replication>
      <replica><host>10.0.10.11</host><port>9000</port></replica>
      <replica><host>10.0.20.11</host><port>9000</port></replica>
    </shard>
    <shard>
      <weight>1</weight>
      <internal_replication>true</internal_replication>
      <replica><host>10.0.10.12</host><port>9000</port></replica>
      <replica><host>10.0.20.12</host><port>9000</port></replica>
    </shard>
  </data_cloud_primary>

  <!-- DR region shards — read-only during normal operations -->
  <data_cloud_dr>
    <shard>
      <weight>1</weight>
      <internal_replication>true</internal_replication>
      <replica><host>10.1.10.11</host><port>9000</port></replica>
    </shard>
  </data_cloud_dr>

  <!-- Global cluster — spans both regions for federated queries -->
  <data_cloud_global>
    <!-- Primary shards -->
    <shard><replica><host>10.0.10.11</host><port>9000</port></replica></shard>
    <shard><replica><host>10.0.10.12</host><port>9000</port></replica></shard>
    <!-- DR shards -->
    <shard><replica><host>10.1.10.11</host><port>9000</port></replica></shard>
  </data_cloud_global>
</remote_servers>
```

**Application cluster selection:**

| Query type | Cluster name | Driver setting |
|---|---|---|
| Real-time ingest | `data_cloud_primary` | `SETTINGS prefer_localhost_replica = 1` |
| Analytics reads | `data_cloud_global` | `SETTINGS max_parallel_replicas = 4` |
| DR validation | `data_cloud_dr` | Read-only; run after failover promotion |

---

## HPA ↔ Stateful Tier Alignment

Keep the following table updated as HPA limits change. Commit changes to
`products/data-cloud/k8s/hpa/` before applying Kafka partition or ClickHouse
shard changes.

```
┌─────────────────────────────┬──────────────┬──────────────┬───────────────────┐
│ Service                     │ HPA min/max  │ Kafka topic  │ Partitions needed │
├─────────────────────────────┼──────────────┼──────────────┼───────────────────┤
│ event-ingest                │ 2 / 20       │ datacloud.*  │ 40 (= 20 × 2)     │
│ query-engine                │ 2 / 10       │ —            │ —                 │
│ stream-processor            │ 2 / 15       │ datacloud.*  │ 30 (= 15 × 2)     │
│ notification-fanout         │ 2 / 8        │ datacloud.notifications │ 16     │
└─────────────────────────────┴──────────────┴──────────────┴───────────────────┘
```

> **Invariant**: `Kafka partitions ≥ HPA maxReplicas × 2` for all consumer services.

---

## Prometheus Alerts for Scaling Headroom

Add these alert rules to `monitoring/prometheus/rules/data-cloud-scaling.yml`:

```yaml
groups:
  - name: data-cloud-stateful-scaling
    rules:
      # Alert when a consumer group's partition headroom drops below 20%
      - alert: KafkaPartitionHeadroomLow
        expr: |
          (
            kafka_topic_partitions{topic=~"datacloud\\..*"}
            /
            on(topic) group_left()
            label_replace(
              max by (topic) (kube_horizontalpodautoscaler_spec_max_replicas{namespace="data-cloud"}) * 2,
              "topic", "datacloud.$1", "horizontalpodautoscaler", "(.+)"
            )
          ) < 1.2
        for: 10m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "Kafka partition headroom < 20% for topic {{ $labels.topic }}"
          description: |
            Topic {{ $labels.topic }} has {{ $value | humanize }}× the minimum
            required partitions. Increase partitions before the next HPA scale-up.
          runbook_url: "https://wiki.ghatana.io/runbooks/kafka-partition-expansion"

      # Alert when ClickHouse node count doesn't match the target shard matrix
      - alert: ClickHouseShardCountMismatch
        expr: |
          clickhouse_clusters_active_nodes{cluster="data_cloud_primary"}
          < on() group_left()
          clickhouse_clusters_active_nodes{cluster="data_cloud_primary"} * 0 + 4
        for: 15m
        labels:
          severity: warning
          team: platform
        annotations:
          summary: "ClickHouse primary cluster has fewer than 4 active nodes"
          description: |
            Expected 4 nodes (2 shards × 2 replicas) but only
            {{ $value }} are reporting. Check node health and Terraform state.

      # Alert when cross-region replication lag exceeds RTO budget
      - alert: KafkaCrossRegionReplicationLagHigh
        expr: |
          kafka_consumergroup_lag{
            consumergroup="data-cloud-msk-replicator",
            topic=~"datacloud\\..*"
          } > 100000
        for: 5m
        labels:
          severity: critical
          team: platform
        annotations:
          summary: "MSK replicator lag > 100k messages on topic {{ $labels.topic }}"
          description: |
            The DR region is falling behind. If lag exceeds the RPO budget
            (5 minutes), page the on-call SRE.
          runbook_url: "https://wiki.ghatana.io/runbooks/msk-replication-lag"
```

---

## Quarterly Capacity Review Checklist

Run every quarter (Jan, Apr, Jul, Oct) before planning the next EKS node group
limit changes:

```
[ ] Review HPA maxReplicas for all data-cloud services (k8s/hpa/)
[ ] Verify Kafka partition counts satisfy: partitions ≥ maxReplicas × 2
[ ] Confirm ClickHouse shard/replica matrix matches target tier (see table above)
[ ] Check MSK broker storage utilisation — alert threshold is 70%
[ ] Validate cross-region MSK replicator lag < 10k messages in steady state
[ ] Update clickhouse_ami_id in environments/dr/terraform.tfvars (quarterly AMI refresh)
[ ] Review CloudWatch scaling alarms — adjust thresholds if traffic patterns changed
[ ] Update this document with new HPA limits and partition counts
```
