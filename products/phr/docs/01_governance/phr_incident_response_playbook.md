# PHR Platform — Incident Response Playbook

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-06-17  
**Document owner:** Security Lead  
**Approval status:** Release-gating operational playbook  
**Classification:** Internal — Restricted

This playbook covers detection, containment, eradication, recovery, and lessons learned for security and availability incidents affecting PHR.

---

## 1. Severity model

| Severity | Definition | Initial response target |
| --- | --- | --- |
| `SEV-1` | confirmed patient-data breach, cross-tenant exposure, or prolonged outage | 15 minutes |
| `SEV-2` | significant degradation, partial security compromise, critical dependency failure | 30 minutes |
| `SEV-3` | contained functional or security issue with limited blast radius | 4 hours |

---

## 2. Response phases

### 2.1 Detection

- alert received from monitoring, DAST, user report, or audit anomaly
- incident commander assigned
- incident ticket and timeline started immediately

### 2.2 Containment

- isolate affected tenant, service, or integration path
- revoke or rotate compromised secrets if applicable
- disable affected feature flags or routes if needed
- preserve logs, traces, and forensic evidence

### 2.3 Eradication

- remove malicious artifact, bad deployment, or compromised credentials
- patch vulnerable code or infrastructure configuration
- verify that tenant and consent controls remain intact

### 2.4 Recovery

- restore service using approved rollback or recovery procedure
- validate health checks, audit flow, and tenant isolation
- communicate restoration status to stakeholders

### 2.5 Lessons learned

- complete post-incident review within 5 business days
- record root cause, contributing factors, and follow-up actions
- update runbooks, alerts, tests, and controls accordingly

---

## 3. Required roles

| Role | Responsibility |
| --- | --- |
| Incident commander | coordinate response and decisions |
| Security lead | determine breach scope and security actions |
| DevOps lead | infrastructure containment and recovery |
| Backend lead | application remediation and validation |
| Compliance lead | notification and legal obligations |
| Communications owner | internal and external communication approval |

---

## 4. Notification rules

- notify internal responders immediately for `SEV-1` and `SEV-2`
- notify compliance lead within 1 hour for suspected breach
- trigger breach assessment workflow if patient data may have been exposed
- preserve evidence for the 72-hour regulatory notification path if needed

---

## 5. Evidence checklist

- incident timeline
- affected services and tenants
- compromised data classes
- logs, traces, screenshots, and scan results
- mitigation and validation steps
- communication record

This playbook is exercised through the breach notification and disaster recovery drills referenced in the QA and operations documents.