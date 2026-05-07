# Multi-Region Routing Runbook

## Overview

DMOS uses product-specific multi-region routing via `DmosWorkspaceRegionRouter` to route workspace API calls to regional endpoints. This runbook covers operational procedures for managing regional deployments.

## Architecture

### Components

- **DmosWorkspaceRegionRouter**: Product-owned router that resolves workspace API calls to regional endpoints
- **Region Registration**: Regional endpoints registered with HTTPS scheme validation (P1-008)
- **Failover Strategy**: Deterministic failover to primary region on failures
- **Tenant Isolation**: All data scoped to tenant with workspace-level isolation

### Region Configuration

Regions are configured via `DmosWorkspaceRegionRouter.Builder`:

```java
DmosWorkspaceRegionRouter router = DmosWorkspaceRegionRouter.builder()
    .primaryRegion("us-east-1")
    .registerRegion("us-east-1", "https://api.us-east-1.dmos.example.com")
    .registerRegion("eu-west-1", "https://api.eu-west-1.dmos.example.com")
    .registerRegion("ap-southeast-1", "https://api.ap-southeast-1.dmos.example.com")
    .build();
```

### Validation Rules

- All regional endpoints **must use HTTPS scheme** (enforced at construction)
- Primary region **must be registered** (enforced at construction)
- Region keys must be non-null and non-empty

## Operational Procedures

### Adding a New Region

1. Provision regional infrastructure (API server, PostgreSQL)
2. Configure HTTPS endpoint with valid TLS certificate
3. Register region in `DmosWorkspaceRegionRouter` configuration
4. Update data residency matrix (see below)
5. Run smoke tests to verify connectivity

### Removing a Region

1. Migrate workspaces to other regions
2. Deregister region from router configuration
3. Decommission regional infrastructure
4. Update data residency matrix

### Failover Drill

**Objective**: Verify failover to primary region works correctly

**Procedure**:
1. Simulate regional outage (stop API server or block network)
2. Trigger API calls to affected region
3. Verify requests failover to primary region
4. Verify no data loss or corruption
5. Restore region and verify normal operation

**Expected Behavior**:
- Router falls back to primary region on regional failures
- Requests return with appropriate latency increase
- No duplicate writes or inconsistent state

### Data Residency Matrix

| Region | Tenant IDs | Data Types | Retention |
|--------|-----------|------------|-----------|
| us-east-1 | US-based tenants | Campaigns, strategies, budgets | 7 years |
| eu-west-1 | EU-based tenants | Campaigns, strategies, budgets | 7 years (GDPR compliant) |
| ap-southeast-1 | APAC-based tenants | Campaigns, strategies, budgets | 7 years |

**Notes**:
- Data residency is enforced at tenant level
- Cross-region access is blocked by authorization (P1-014 E2E tests verify this)
- Data export/DSAR operations respect residency constraints

## Monitoring

### Key Metrics

- Regional endpoint health (availability, latency)
- Failover rate (number of fallbacks to primary region)
- Cross-region request count (should be minimal)
- Data residency compliance

### Operational Dashboards

**Required Dashboards**:
1. **Regional Health Dashboard**: Shows availability and latency per region
2. **Failover Dashboard**: Shows failover events and recovery times
3. **Data Residency Dashboard**: Shows tenant-to-region mapping and compliance status

**Implementation Status**: Pending - dashboards to be implemented as part of observability infrastructure

## Troubleshooting

### Regional Endpoint Unreachable

**Symptoms**: High latency, 5xx errors to specific region

**Steps**:
1. Check regional endpoint health status
2. Verify network connectivity between regions
3. Check TLS certificate validity
4. Review router logs for failover events

### Failover Not Triggering

**Symptoms**: Requests failing instead of falling back to primary

**Steps**:
1. Verify primary region is registered
2. Check router configuration
3. Review failover logic in `DmosWorkspaceRegionRouter`
4. Verify health check status

### Data Residency Violation

**Symptoms**: Tenant data stored in wrong region

**Steps**:
1. Review tenant-to-region mapping
2. Check router region resolution logic
3. Audit data location in PostgreSQL
4. Correct residency violations

## Security Considerations

- All regional endpoints must use HTTPS (enforced at router construction)
- Tenant isolation enforced via `DmOperationContext` and authorization
- Cross-region access blocked by security policies
- Region hints logged for audit purposes (P2-007: redaction pending with P1-013)

## References

- P1-007: Region routing boundary (justified as product-specific)
- P1-008: Region router hardening (HTTPS validation)
- P2-007: Logging redaction (pending PII hardening)
