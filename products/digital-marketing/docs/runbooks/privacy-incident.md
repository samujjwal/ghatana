# Privacy Incident Runbook

## Trigger

- Suspected PII exposure, raw contact data in logs, missing consent enforcement, unauthorized export, or cross-tenant data access.
- AI prompt/output includes unredacted PII without valid purpose and consent.

## Immediate Containment

1. Stop affected processing paths with the smallest safe kill switch scope.
2. Preserve logs, audit events, request IDs, consent snapshots, and affected entity IDs.
3. Revoke connector/API credentials if exfiltration is possible.
4. Notify security, privacy owner, and incident lead.

## Investigation

1. Identify affected tenants, workspaces, contacts, prompts, exports, and connectors.
2. Confirm whether identifiers were hashed, encrypted, redacted, or raw.
3. Check consent, suppression, purpose, and authorization snapshots.
4. Determine whether legal hold, customer notification, or regulatory timelines apply.

## Recovery

1. Patch the failing consent/redaction/authorization path.
2. Remove or redact unsafe logs and AI artifacts according to retention policy.
3. Rotate keys or credentials if exposure risk exists.
4. Add regression tests for the failed path before restoring execution.

## Verification

- Logs, traces, AI artifacts, exports, and connector commands contain no unredacted PII.
- Consent and suppression checks pass before every contact-data action.
- Audit record captures actor, scope, policy, consent snapshot, and redaction status.
- Incident postmortem records affected scope and corrective controls.
