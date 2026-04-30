# Regulated Incident Runbooks

## Scope

This directory contains operational runbooks for incident response in regulated domains: PHR (Personal Health Records), Finance, and cross-product systems. Runbooks are executable procedures that ensure compliance with Nepal privacy/healthcare regulations (Directive 2081, Privacy Act 2075), HIPAA, SOX (Sarbanes-Oxley), and PCI-DSS standards where applicable.

## Runbook Index

### PHR (Healthcare) Incidents

- **[phr-consent-breach-incident.md](phr-consent-breach-incident.md)** — Response procedure when a patient consent decision is violated or a provider accesses data without valid consent.
- **[phr-emergency-access-abuse-incident.md](phr-emergency-access-abuse-incident.md)** — Response procedure when break-the-glass emergency access is invoked outside established guidelines.
- **[phr-phi-redaction-failure-incident.md](phr-phi-redaction-failure-incident.md)** — Response procedure when PHI (Protected Health Information) appears in logs, traces, or metrics contrary to redaction policies.

### Finance (Trading & Ledger) Incidents

- **[finance-ledger-imbalance-incident.md](finance-ledger-imbalance-incident.md)** — Response procedure when double-entry ledger imbalance is detected.
- **[finance-fraud-false-positive-spike-incident.md](finance-fraud-false-positive-spike-incident.md)** — Response procedure when fraud detection false-positive rate exceeds thresholds.
- **[finance-compliance-rule-breach-incident.md](finance-compliance-rule-breach-incident.md)** — Response procedure when a compliance rule (SOX, risk limit, sanctions) is violated.

### Cross-Product Incidents

- **[audit-trail-tampering-incident.md](audit-trail-tampering-incident.md)** — Response procedure when audit trail integrity is compromised.
- **[plugin-failure-incident.md](plugin-failure-incident.md)** — Response procedure when a critical plugin (audit, consent, compliance, billing) fails.
- **[tenant-isolation-breach-incident.md](tenant-isolation-breach-incident.md)** — Response procedure when tenant data isolation is violated.

## Response Phases

Every runbook follows a standard incident response structure:

1. **Detection** — How the incident is typically detected
2. **Immediate Actions** — Containment and immediate mitigation steps
3. **Investigation** — Diagnostic and forensic steps
4. **Remediation** — Fix and recovery procedures
5. **Verification** — Proof that the incident is resolved
6. **Escalation** — When to involve management, legal, or regulatory bodies
7. **Communication** — Notification requirements to patients, providers, auditors
8. **Post-Incident** — Documentation and lessons-learned process

## Compliance References

- **Nepal**: Directive 2081 (Healthcare), Privacy Act 2075
- **USA**: HIPAA (healthcare), SOX (finance), PCI-DSS (payments)
- **EU**: GDPR (where applicable)
- **Internal**: Ghatana Compliance Policy, Audit Governance Framework

## Testing

All runbooks are tested annually or whenever material system changes occur. Test events are logged in the audit trail with event type `test.incident-runbook-execution`.

---

Last updated: 2026-04-29
