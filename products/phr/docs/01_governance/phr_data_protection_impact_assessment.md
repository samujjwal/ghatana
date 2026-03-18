# PHR Platform — Data Protection Impact Assessment Template

**Version:** 1.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-06-17  
**Document owner:** Compliance Lead  
**Approval status:** Release-gating template  
**Classification:** Internal — Restricted

This DPIA template is the release-gating artifact for Privacy Act 2075 readiness and must be completed before public launch.

---

## 1. Assessment header

| Field | Value |
| --- | --- |
| Initiative | PHR Core MVP |
| Assessment owner | Compliance Lead |
| Technical approver | Architecture Lead |
| Business approver | Product Lead |
| Assessment date | `TBD` |
| Review frequency | before MVP launch, then annually or on major scope change |

---

## 2. Processing overview

Document the following:

- purpose of processing
- categories of data subjects
- categories of personal and health data processed
- systems, services, and vendors involved
- Nepal data residency statement

Required minimum statement for PHR MVP:

- personal data includes demographic, contact, and access-control information
- sensitive data includes clinical history, medications, insurance eligibility results, and uploaded medical documents
- processing occurs for self-service records, care delivery, eligibility verification, and compliance logging

---

## 3. Data flow inventory

List each major flow:

| Flow | Input | Processing | Output | Retention | Legal basis |
| --- | --- | --- | --- | --- | --- |
| patient registration | demographic and contact data | account and profile creation | patient record root | policy-defined subject to legal minimums | consent and care delivery |
| clinical update | encounter, observation, medication data | provider-authorized mutation | longitudinal record | 7 years minimum | care delivery |
| document upload and OCR | document binary and metadata | storage, OCR, review | document reference and derived data | 7 years minimum | consent and care delivery |
| insurance eligibility | patient and coverage identifiers | external eligibility request | eligibility result log | 7 years per policy | care delivery and payer operations |
| audit logging | actor, tenant, action metadata | immutable logging | compliance evidence | policy-driven archival baseline subject to legal minimums | legal obligation |

---

## 4. Necessity and proportionality checklist

Answer for each processing activity:

- is the data set limited to what the workflow needs
- are there less intrusive alternatives
- is the retention period justified
- is the retention or deletion policy configurable without violating legal minimums or legal hold
- are data subject rights supported
- are consent and revocation flows explicit where required

---

## 5. Risk assessment matrix

| Risk | Likelihood | Impact | Current controls | Residual risk | Owner |
| --- | --- | --- | --- | --- | --- |
| unauthorized cross-tenant access | medium | critical | tenancy enforcement, RLS, audit | low | Architecture Lead |
| overbroad provider access | medium | critical | ConsentService, grants, break-the-glass controls | medium | Consent Lead |
| secrets compromise | medium | critical | vault-managed secrets, rotation, monitoring | medium | DevOps Lead |
| malware in uploaded documents | medium | high | quarantine, anti-malware scan, checksum | medium | Document Lead |
| delayed breach notification | low | critical | incident playbook, compliance drills | low | Compliance Lead |

---

## 6. Required controls confirmation

Mark each as complete before release:

- data classification matrix approved
- consent runtime controls implemented
- tenant isolation verified
- encryption at rest and in transit validated
- audit retention policy configured
- breach notification process rehearsed
- export and deletion rights documented
- DAST and SAST reports attached

---

## 7. Evidence attachments

Attach or reference:

- architecture and data flow diagrams
- security scan results
- pen-test report or schedule
- retention policy notes
- incident response exercise result
- compliance sign-off record

The DPIA is complete only when risks, controls, residual risks, and approvals are all explicitly recorded.