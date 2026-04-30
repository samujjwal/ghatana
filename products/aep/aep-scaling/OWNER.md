# Owner: AEP Scaling

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Autoscaling and resource management for AEP pipelines and agents.

- Scaling policy management and evaluation
- Autoscaling triggers and thresholds
- Resource quota enforcement
- Policy conflict detection and resolution
- Scaling failure recovery

## Key Components

| Component | Purpose |
|-----------|---------|
| `DefaultScalingPolicyManager` | Policy CRUD and evaluation |
| `AutoscalingEngine` | Scaling decision engine |
| `ResourceQuotaEnforcer` | Tenant resource limits |

## Critical Tests

| Test | Purpose | Status |
|------|---------|--------|
| `DefaultScalingPolicyManagerTest` | Policy management validation | **PASS** (8 tests) |

## Test Coverage Gaps

Per AEP-A9 audit, needs expansion:
- Autoscaling failure/recovery paths
- Policy conflict resolution scenarios
- Multi-tenant scaling isolation

## Dependencies

- `platform:java:core`
- `platform:java:observability`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- DefaultScalingPolicyManagerTest: PASS (8 tests)
- Source files: 18 | Test files: 3 (thin coverage)
