# Phase 3: Operations Plane Hardening Plan

## Current State

The Operations Plane has:

- ✅ `PluginConfigCompiler` for plugin configuration
- ✅ `PluginHealthCheckFactory` for health checks
- ✅ `ConfigDrivenHealthCheck` for configuration-driven health
- ✅ Health check infrastructure

## Target State

- **Runtime Truth**: Runtime truth provider for system state
- **Health Checks**: Comprehensive health checks for all components
- **Backup/Restore**: Backup and restore procedures with tests
- **Runtime Truth Drift**: Detection and prevention of runtime truth drift

## Implementation Tasks

### 1. Runtime Truth Provider

**Status**: Partial (kernel-bridge provides runtime truth)

**Current State**:

- `DataCloudRuntimeTruthProvider` exists in kernel-bridge
- Provides lifecycle events, artifact refs, health snapshots, provenance refs, tenant scoping

**Required**:

- Central runtime truth service in Operations Plane
- Runtime truth aggregation from all planes
- Runtime truth consistency checks
- Runtime truth drift detection

**Implementation**:

- Create `RuntimeTruthService` in operations/config
- Aggregate runtime truth from:
  - Data Plane (entity storage state)
  - Event Plane (event log state)
  - Governance Plane (policy state)
  - Action Plane (agent state)
- Add runtime truth consistency tests
- Add runtime truth drift detection tests

### 2. Health Checks

**Status**: Partial (PluginHealthCheckFactory exists)

**Current State**:

- `PluginHealthCheckFactory` provides plugin health checks
- `ConfigDrivenHealthCheck` provides configuration-driven health

**Required**:

- Health checks for all planes:
  - Data Plane health (storage connectivity, query performance)
  - Event Plane health (event log append/read latency)
  - Governance Plane health (policy evaluation performance)
  - Action Plane health (agent runtime health)
- Health check aggregation
- Health check alerting
- Health check SLA monitoring

**Implementation**:

- Extend `PluginHealthCheckFactory` with plane-specific checks
- Add `PlaneHealthAggregator` service
- Add health check threshold tests
- Add health check failure detection tests
- Add health check SLA tests

### 3. Backup/Restore

**Status**: Partial (scripts exist but need hardening)

**Current State**:

- Backup/restore scripts exist in products/data-cloud/scripts/
- `run-backup-drill.sh` provides backup drill validation

**Required**:

- Backup procedures for:
  - Data Plane (entity storage backup)
  - Event Plane (event log backup)
  - Governance Plane (policy backup)
  - Configuration backup
- Restore procedures with validation
- Backup consistency checks
- Restore rollback procedures
- Backup/restore performance tests

**Implementation**:

- Create `BackupService` in operations/config
- Create `RestoreService` in operations/config
- Add backup consistency tests
- Add restore validation tests
- Add backup/restore performance tests
- Integrate with existing backup scripts

### 4. Runtime Truth Drift Detection

**Status**: Pending

**Required**:

- Runtime truth baseline capture
- Runtime truth drift detection
- Runtime truth reconciliation
- Runtime truth alerting
- Runtime truth correction procedures

**Implementation**:

- Create `RuntimeTruthDriftDetector` in operations/config
- Add drift detection tests
- Add drift reconciliation tests
- Add drift alerting tests
- Integrate with runtime truth service

## Exit Criteria

- [ ] Runtime truth provider aggregates state from all planes
- [ ] Health checks exist for all planes with aggregation
- [ ] Backup/restore procedures are tested and validated
- [ ] Runtime truth drift detection is implemented

## Dependencies

- Phase 3: Data Plane hardening (completed)
- Phase 3: Event Plane hardening (completed)
- Phase 3: Governance Plane hardening (completed)

## Related Files

- `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/health/PluginHealthCheckFactory.java`
- `products/data-cloud/planes/operations/config/src/main/java/com/ghatana/datacloud/config/health/ConfigDrivenHealthCheck.java`
- `products/data-cloud/extensions/kernel-bridge/src/main/java/com/ghatana/datacloud/kernel/DataCloudRuntimeTruthProvider.java`
- `products/data-cloud/scripts/run-backup-drill.sh`

## Notes

The Operations Plane has a solid foundation with health check infrastructure. The main gaps are:

1. Central runtime truth aggregation service
2. Plane-specific health checks
3. Backup/restore service implementation
4. Runtime truth drift detection

These should be implemented incrementally with each addition having corresponding tests.
