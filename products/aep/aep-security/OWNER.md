# Owner: AEP Security

**Parent:** AEP (Agentic Event Processor)  
**Team:** Platform Engineering — AEP Team  
**Slack:** #platform-aep-security  
**On-call:** AEP on-call rotation

## Responsibility

Security controls and governance for AEP. Provides authentication, authorization, and security policy enforcement.

- Tenant-scoped access control
- Pipeline execution authorization
- Security policy enforcement
- MFA step-up handling
- Audit logging for security events

## Key Components

| Component | Purpose |
|-----------|---------|
| `MfaStepUpGate` | MFA step-up authentication gate |
| `SecurityPolicyEnforcer` | Policy enforcement at execution |
| `TenantAccessControl` | Tenant-scoped authorization |

## TODO Debt

Per AEP-A6 audit, the following placeholders need GH issue tracking:
- `MfaStepUpGate` implementation completion

## Dependencies

- `platform:java:security`
- `platform:java:identity`
- `platform:java:governance`

## Audit Status

- Last audited: 2026-04-29
- Placeholder/TODO debt: Tracked under AEP-A6
