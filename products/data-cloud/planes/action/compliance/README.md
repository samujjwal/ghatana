# aep-compliance

## Purpose

`products/data-cloud/planes/action/compliance` enforces data-retention and regulatory compliance policies over AEP event data. It owns:

- `ComplianceService` — evaluates whether stored events satisfy applicable retention and deletion requirements
- `DataRetentionAutomationService` — scheduled enforcement of retention policies (purge, archive)
- `InMemoryRetentionPolicyEnforcer` — fast in-process enforcer for unit and integration tests

## Boundaries

- **Uses:** `platform:java:observability` for policy-execution audit logs; `aep-engine` for event identity types
- **Does not own:** analytics or pattern detection — that is `aep-analytics`; identity resolution — that is `aep-identity`
- **Fail-closed posture:** if a retention policy cannot be evaluated, the event is treated as non-compliant

## Key classes

| Class | Role |
|---|---|
| `ComplianceService` | Per-event compliance check; returns a typed result |
| `DataRetentionAutomationService` | Scheduled background job for bulk retention enforcement |
| `InMemoryRetentionPolicyEnforcer` | Deterministic in-process enforcer for test environments |

## Verification

```bash
./gradlew :products:data-cloud:planes:action:compliance:test
```
