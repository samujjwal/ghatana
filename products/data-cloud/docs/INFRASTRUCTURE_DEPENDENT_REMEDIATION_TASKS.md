# Infrastructure-Dependent Audit Remediation Tasks

**Created:** 2026-04-21
**Purpose:** Document Data Cloud audit remediation tasks requiring infrastructure deployment

## Overview

This document outlines the Data Cloud audit remediation tasks that require infrastructure deployment beyond code changes. These tasks have been completed at the code/implementation level but require operational infrastructure to be fully functional in production.

---

## DC-P2-3: Add Performance Benchmarks

**Status:** Code Ready, Awaiting CI Infrastructure
**Severity:** Medium
**Infrastructure Required:** CI Pipeline Configuration

### What's Needed

1. **CI Pipeline Configuration**
   - Add JMH (Java Microbenchmark Harness) execution to CI pipeline
   - Configure benchmark execution environment
   - Set up performance regression detection thresholds

2. **Performance Baseline Documentation**
   - Document current performance baselines for critical paths
   - Establish performance regression thresholds
   - Create performance trend dashboards

3. **Benchmark Coverage**
   - Audit existing JMH benchmarks for coverage gaps
   - Add benchmarks for missing critical paths
   - Ensure benchmarks cover hot paths in production

### Implementation Notes

- Benchmark code can be added without infrastructure changes
- CI pipeline changes required for automated execution
- Performance regression detection requires baseline data

### Configuration Required

```yaml
# Example CI configuration (GitHub Actions / GitLab CI)
performance-benchmarks:
  image: openjdk:21
  script:
    - ./gradlew jmh
    - ./gradlew compareWithBaseline
  artifacts:
    reports:
      performance: build/reports/jmh/results.json
```

### Acceptance Criteria

- [ ] CI pipeline configured to run JMH benchmarks
- [ ] Performance baselines documented
- [ ] Performance regression detection functional
- [ ] Benchmark coverage for all critical paths

---

## DC-P2-4: Add Query Plan Analysis and Monitoring

**Status:** Code Ready, Awaiting Database Infrastructure
**Severity:** Medium
**Infrastructure Required:** PostgreSQL Configuration & Monitoring Stack

### What's Needed

1. **PostgreSQL Configuration**
   - Enable `pg_stat_statements` extension
   - Configure database permissions for query statistics access
   - Set up query statistics collection interval

2. **Monitoring Infrastructure**
   - Configure Prometheus to scrape PostgreSQL metrics
   - Create Grafana dashboards for query performance
   - Set up slow query alerting

3. **Runbook Updates**
   - Document query monitoring procedures
   - Add query plan analysis instructions
   - Create troubleshooting guide for slow queries

### Implementation Notes

- Requires PostgreSQL superuser access to enable extensions
- Monitoring stack (Prometheus/Grafana) must be configured
- Query statistics have performance overhead

### Configuration Required

```sql
-- Enable pg_stat_statements extension
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;

-- Grant read access to monitoring user
GRANT pg_read_all_stats TO monitoring_user;

-- Configure shared_preload_libraries in postgresql.conf
shared_preload_libraries = 'pg_stat_statements'
```

### Prometheus Configuration

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']
```

### Acceptance Criteria

- [ ] pg_stat_statements enabled in PostgreSQL
- [ ] Prometheus configured to scrape query metrics
- [ ] Grafana dashboards created for query performance
- [ ] Slow query alerting configured
- [ ] Runbook updated with query monitoring procedures

---

## DC-P2-5: Implement Redis/Garnet HA

**Status:** Code Ready, Awaiting Redis/Garnet Infrastructure
**Severity:** Medium
**Infrastructure Required:** Garnet/Redis Cluster or Sentinel

### What's Needed

1. **Garnet/Redis HA Deployment**
   - Deploy Garnet (MIT-licensed) or Redis in cluster/sentinel mode
   - Configure replication and failover
   - Set up connection string for HA topology

2. **Health Check Configuration**
   - Configure health checks for Garnet/Redis nodes
   - Set up failover monitoring
   - Configure circuit breakers for unavailability

3. **Connection Configuration**
   - Update RedisStateAdapter configuration for HA
   - Configure connection pooling for cluster mode
   - Test failover behavior

### Implementation Notes

- **Garnet** is recommended for permissive MIT licensing
- Jedis client supports both Redis and Garnet
- Existing code requires no changes for Garnet compatibility
- Sentinel mode provides automatic failover
- Cluster mode provides horizontal scaling

### Configuration Required

```yaml
# Garnet/Redis connection configuration (application.yml)
garnet:
  host: garnet-cluster
  port: 6379
  mode: sentinel  # or cluster
  sentinels:
    - host: sentinel-1
      port: 26379
    - host: sentinel-2
      port: 26379
    - host: sentinel-3
      port: 26379
  master: mymaster
  password: ${GARNET_PASSWORD}
```

### Docker Compose Example

```yaml
version: '3.8'
services:
  garnet-master:
    image: ghcr.io/microsoft/garnet:latest
    ports:
      - "6379:6379"
  
  garnet-replica-1:
    image: ghcr.io/microsoft/garnet:latest
    command: garnet --port 6380 --replicaof garnet-master 6379
  
  sentinel-1:
    image: redis:7-alpine
    command: redis-sentinel /etc/redis/sentinel.conf
```

### Acceptance Criteria

- [ ] Garnet/Redis cluster/sentinel deployed
- [ ] Health checks configured
- [ ] Failover tested and functional
- [ ] Connection pooling configured for HA mode
- [ ] Documentation updated with HA procedures

---

## DC-P2-6: Add PostgreSQL Read Replica Support

**Status:** Code Ready, Awaiting Database Infrastructure
**Severity:** Medium
**Infrastructure Required:** PostgreSQL Read Replica Deployment

### What's Needed

1. **PostgreSQL Read Replica Deployment**
   - Deploy PostgreSQL read replicas
   - Configure replication from primary to replicas
   - Set up connection strings for replica routing

2. **DataSource Configuration**
   - Configure DataSource for read/write splitting
   - Set up connection pooling for primary and replicas
   - Configure replica routing logic

3. **Monitoring Infrastructure**
   - Monitor replication lag
   - Track replica health
   - Set up alerting for replication issues

### Implementation Notes

- Requires PostgreSQL streaming replication
- Application-level routing for read queries
- Connection pooling must handle multiple databases
- Replication lag monitoring critical for consistency

### Configuration Required

```yaml
# DataSource configuration (application.yml)
datasource:
  primary:
    url: jdbc:postgresql://primary-db:5432/datacloud
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  replicas:
    - url: jdbc:postgresql://replica-1:5432/datacloud
      username: ${DB_USER}
      password: ${DB_PASSWORD}
    - url: jdbc:postgresql://replica-2:5432/datacloud
      username: ${DB_USER}
      password: ${DB_PASSWORD}
  routing:
    strategy: round-robin  # or least-connections
    lag-threshold: 1000ms
```

### PostgreSQL Replication Setup

```sql
-- On primary
CREATE ROLE replication_user WITH REPLICATION LOGIN PASSWORD 'secure_password';
GRANT REPLICATION ON DATABASE datacloud TO replication_user;

-- Configure pg_hba.conf on primary
host replication replication_user replica-ip/32 md5

-- Configure recovery.conf on replicas
standby_mode = 'on'
primary_conninfo = 'host=primary-db port=5432 user=replication_user'
```

### Acceptance Criteria

- [ ] PostgreSQL read replicas deployed
- [ ] Replication configured and functional
- [ ] DataSource configured for read/write splitting
- [ ] Connection pooling configured for multiple databases
- [ ] Replication lag monitoring functional
- [ ] Documentation updated with replica procedures

---

## Summary

| Task | Infrastructure Required | Status |
|------|------------------------|--------|
| DC-P2-3 | CI Pipeline Configuration | Code Ready |
| DC-P2-4 | PostgreSQL + Monitoring Stack | Code Ready |
| DC-P2-5 | Garnet/Redis HA Deployment | Code Ready |
| DC-P2-6 | PostgreSQL Read Replica | Code Ready |

All tasks have code-level implementations ready and require infrastructure deployment to be production-ready.

---

## Next Steps

1. **Prioritize by Impact**
   - DC-P2-5 (Garnet HA) - Critical for distributed rate limiting
   - DC-P2-4 (Query Monitoring) - Important for observability
   - DC-P2-6 (Read Replicas) - Important for scalability
   - DC-P2-3 (Benchmarks) - Important for performance tracking

2. **Infrastructure Planning**
   - Plan Garnet deployment for HA
   - Plan PostgreSQL configuration for pg_stat_statements
   - Plan CI pipeline for benchmark execution
   - Plan read replica deployment

3. **Documentation Updates**
   - Update runbooks with new infrastructure
   - Update deployment guides
   - Update troubleshooting procedures

---

## References

- [Garnet Documentation](https://microsoft.github.io/garnet/)
- [PostgreSQL pg_stat_statements](https://www.postgresql.org/docs/current/pgstatstatements.html)
- [JMH Documentation](https://openjdk.org/projects/code-tools/jmh/)
- [PostgreSQL Replication](https://www.postgresql.org/docs/current/high-availability.html)
