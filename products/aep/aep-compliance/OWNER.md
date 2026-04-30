# Owner: AEP Compliance

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep  
**On-call:** AEP on-call rotation

## Responsibility

Compliance automation for AEP. Handles data retention, GDPR obligations, and audit requirements.

- Data retention policy execution
- GDPR right-to-erasure processing
- Compliance audit logging
- Policy violation detection
- Retention failure retry handling

## Key Components

| Component | Purpose |
|-----------|---------|
| `RetentionPolicyExecutor` | Retention policy enforcement |
| `GdprErasureProcessor` | GDPR erasure request handling |
| `ComplianceAuditor` | Audit event generation |

## Test Coverage Gaps

Per AEP-A11 audit, needs:
- Deeper retention execution tests
- Failure retry scenario coverage
- Operational telemetry assertions

## Dependencies

- `platform:java:data-governance`
- `platform:java:audit`
- `products:aep:aep-operator-contracts`

## Audit Status

- Last audited: 2026-04-29
- Source files: 10 | Test coverage: Thin (AEP-A11)
