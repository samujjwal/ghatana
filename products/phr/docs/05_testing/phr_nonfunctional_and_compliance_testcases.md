# PHR Platform — Non-Functional and Compliance Test Cases

**Version:** 2.0  
**Date:** 2026-03-17  
**Updated:** 2026-01-19

| Field          | Value                                                                                                                                                                                                                                               |
| -------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Owner          | QA Lead                                                                                                                                                                                                                                             |
| Classification | C2 — Internal                                                                                                                                                                                                                                       |
| Review cadence | Per sprint                                                                                                                                                                                                                                          |
| Companion docs | [Runtime architecture](../03_architecture/phr_runtime_architecture.md), [Requirements traceability](../01_governance/phr_requirements_traceability_matrix.md), [Consolidated report](../02_strategy_and_requirements/phr-consolidated-report-v2.md) |

> 📌 **What changed in v2.0:** OWASP Top 10 test suite, DPIA validation, penetration test requirements, load/stress test specifications with targets, cross-tenant isolation verification, data retention compliance, Nepali ASR accuracy tests, breach notification process test.

This document defines quality, security, accessibility, operational, and compliance test cases that must accompany functional delivery.

---

## 1. Security and privacy

| Test ID   | Phase | Area                | Scenario                            | Expected result                                       |
| --------- | ----- | ------------------- | ----------------------------------- | ----------------------------------------------------- |
| `NFR-001` | MVP   | audit completeness  | sensitive record read/write actions | audit rows exist with actor, tenant, resource, action |
| `NFR-002` | MVP   | consent privacy     | revoked grant reuse attempt         | access denied and audited                             |
| `NFR-003` | MVP   | auth hardening      | repeated failed logins              | rate-limit or lockout policy enforced                 |
| `NFR-004` | MVP   | data sovereignty    | storage config inspection           | DB/object endpoints remain Nepal-hosted               |
| `NFR-005` | MVP   | processing locality | integration path review             | primary data processing remains in-country            |
| `NFR-006` | MVP   | encryption          | data at rest and in transit         | expected encryption controls are enabled              |

---

## 2. Accessibility and usability

| Test ID   | Phase | Area          | Scenario                                | Expected result                                   |
| --------- | ----- | ------------- | --------------------------------------- | ------------------------------------------------- |
| `NFR-007` | MVP   | accessibility | WCAG 2.2 AA baseline on shipped screens | automated and manual audits pass agreed threshold |
| `NFR-008` | MVP   | localization  | Nepali/English critical flows           | strings, layouts, and dates render correctly      |

---

## 3. Data quality and interoperability

| Test ID   | Phase       | Area                      | Scenario                             | Expected result                                    |
| --------- | ----------- | ------------------------- | ------------------------------------ | -------------------------------------------------- |
| `NFR-009` | MVP         | export integrity          | patient export generation            | export contains complete and correctly scoped data |
| `NFR-010` | MVP/Phase 2 | openIMIS interoperability | eligibility/claim mapping validation | request and response mappings are stable           |
| `NFR-011` | MVP         | FHIR gateway              | inbound payload validation           | invalid FHIR payloads fail predictably             |

---

## 4. Reliability and operations

| Test ID   | Phase   | Area                 | Scenario                                   | Expected result                               |
| --------- | ------- | -------------------- | ------------------------------------------ | --------------------------------------------- |
| `NFR-012` | MVP     | reminder reliability | reminder planning after appointment create | reminder plan is persisted and retriable      |
| `NFR-013` | MVP     | backup restore       | restore latest backup into test env        | restored system is usable and verified        |
| `NFR-014` | MVP     | observability        | induce dependency failure                  | alerting and logs point to failing dependency |
| `NFR-015` | MVP     | offline sync         | conflicting offline edits on approved queued-write surfaces | conflict rule is deterministic and visible    |

---

## 5. Performance

| Test ID   | Phase   | Area               | Scenario                                  | Expected result                                  |
| --------- | ------- | ------------------ | ----------------------------------------- | ------------------------------------------------ |
| `NFR-016` | MVP     | API latency        | common read endpoints under expected load | p90 remains within target                        |
| `NFR-017` | MVP     | timeline query     | timeline for record-heavy patient         | acceptable response time and pagination behavior |
| `NFR-018` | MVP     | document upload    | supported-size file upload                | upload completes within agreed SLA               |
| `NFR-019` | Phase 2 | telemedicine media | degraded network conditions               | room degrades gracefully without data loss       |
| `NFR-020` | MVP     | OCR/ASR processing | async job surge                           | queued work remains observable and recoverable   |

---

## 6. Compliance evidence artifacts

Every executed compliance testcase should produce durable evidence:

- report or screenshot
- run timestamp
- environment
- operator
- result
- linked remediation ticket if failed

---

## 7. OWASP Top 10 Security Test Suite (Added in v2.0)

| Test ID   | OWASP Risk                    | Scenario                                                | Expected result                                              |
| --------- | ----------------------------- | ------------------------------------------------------- | ------------------------------------------------------------ |
| `NFR-021` | A01 Broken Access Control     | Horizontal privilege escalation (patient A → patient B) | 403/404 and audited                                          |
| `NFR-022` | A01 Broken Access Control     | Vertical privilege escalation (patient → admin)         | 403 and audited                                              |
| `NFR-023` | A02 Cryptographic Failures    | Inspect DB for PII in plaintext                         | All C3/C4 data encrypted at rest                             |
| `NFR-024` | A02 Cryptographic Failures    | TLS configuration scan                                  | TLS 1.3 enforced, no weak ciphers                            |
| `NFR-025` | A03 Injection                 | SQL injection on all input fields                       | Parameterized queries prevent injection                      |
| `NFR-026` | A03 Injection                 | XSS payload in all text fields                          | Input sanitized or escaped on output                         |
| `NFR-027` | A04 Insecure Design           | Break-the-glass without justification                   | Emergency grant created but escalation ticket auto-generated |
| `NFR-028` | A05 Security Misconfiguration | Default credentials scan                                | No default passwords in any component                        |
| `NFR-029` | A05 Security Misconfiguration | Error response content audit                            | No stack traces, internal IDs, or DB names                   |
| `NFR-030` | A06 Vulnerable Components     | Dependency vulnerability scan (OWASP Dependency-Check)  | Zero critical/high CVEs                                      |
| `NFR-031` | A07 Auth Failures             | Session fixation attempt                                | Session ID regenerated on login                              |
| `NFR-032` | A07 Auth Failures             | Brute force login                                       | Account locked after 5 failed attempts                       |
| `NFR-033` | A08 Data Integrity            | JWT token tampering                                     | Tampered tokens rejected with 401                            |
| `NFR-034` | A09 Logging Failures          | Sensitive action without audit log                      | All sensitive actions produce audit entries                  |
| `NFR-035` | A10 SSRF                      | API parameter with internal URL                         | Request blocked, not forwarded                               |

---

## 8. DPIA Validation Tests (Added in v2.0)

| Test ID   | Phase | Area | Scenario                     | Expected result                                              |
| --------- | ----- | ---- | ---------------------------- | ------------------------------------------------------------ |
| `NFR-036` | MVP   | DPIA | Data inventory completeness  | All personal data flows documented                           |
| `NFR-037` | MVP   | DPIA | Consent mechanism validation | All data collection has associated consent record            |
| `NFR-038` | MVP   | DPIA | Data minimization check      | APIs return only necessary fields per role                   |
| `NFR-039` | MVP   | DPIA | Right to erasure             | Patient data deletion request results in complete data purge |
| `NFR-040` | MVP   | DPIA | Data portability             | Patient export contains all personal data in FHIR format     |

---

## 9. Penetration Test Requirements (Added in v2.0)

| Requirement      | Specification                                                  |
| ---------------- | -------------------------------------------------------------- |
| Frequency        | Before each major release (MVP, Phase 2)                       |
| Scope            | All public-facing APIs + authentication flows                  |
| Methodology      | OWASP Testing Guide v4.2                                       |
| Provider         | Independent third-party security firm                          |
| Remediation SLA  | Critical: 48h, High: 7 days, Medium: 30 days, Low: next sprint |
| Report retention | 7 years (compliance audit evidence)                            |

---

## 10. Load and Stress Test Specifications (Added in v2.0)

### 10.1 Load Test Scenarios

| Scenario               | Concurrent Users | Duration | RPS Target | Pass Criteria                        |
| ---------------------- | ---------------- | -------- | ---------- | ------------------------------------ |
| Normal load            | 100              | 30 min   | 200        | p95 < 500ms, 0 errors                |
| Peak load              | 500              | 15 min   | 1000       | p95 < 1s, < 0.1% errors              |
| Sustained load         | 200              | 4 hours  | 400        | p95 < 500ms, 0 memory leaks          |
| Timeline heavy patient | 50               | 10 min   | 100        | p95 < 1s for 10,000+ record patients |

### 10.2 Stress Test Scenarios

| Scenario              | Description                 | Pass Criteria                              |
| --------------------- | --------------------------- | ------------------------------------------ |
| Database failover     | Kill primary DB during load | Automatic failover < 30s, no data loss     |
| Cache failure         | Kill Valkey during load     | Graceful degradation, DB fallback          |
| Upstream timeout      | openIMIS 100% timeout       | Circuit breaker activates, cached fallback |
| Document upload surge | 100 concurrent 50MB uploads | Queue-based processing, no OOM             |

---

## 11. Cross-Tenant Isolation Verification (Added in v2.0)

| Test ID   | Phase | Area          | Scenario                                    | Expected result                          |
| --------- | ----- | ------------- | ------------------------------------------- | ---------------------------------------- |
| `NFR-041` | MVP   | multi-tenancy | Tenant A queries with Tenant B's patient ID | 404 (empty result from RLS)              |
| `NFR-042` | MVP   | multi-tenancy | Audit logs filtered by tenant               | No cross-tenant audit entries visible    |
| `NFR-043` | MVP   | multi-tenancy | Search results scoped by tenant             | Only tenant's patients in search results |

---

## 12. Data Retention Compliance Tests (Added in v2.0)

| Test ID   | Phase | Area      | Scenario                                     | Expected result                          |
| --------- | ----- | --------- | -------------------------------------------- | ---------------------------------------- |
| `NFR-044` | MVP   | retention | Patient data past 10-year retention          | Purge job soft-deletes data              |
| `NFR-045` | MVP   | retention | Audit logs at 7-year mark                    | Archived, not deleted                    |
| `NFR-046` | MVP   | retention | Insurance records at 7-year mark post-expiry | Purge completes with audit trail         |
| `NFR-047` | MVP   | retention | Legal hold prevents purge                    | Held records exempt from automated purge |

---

## 13. Nepali ASR Accuracy Tests (Added in v2.0)

| Test ID   | Phase | Area | Scenario                              | Expected result                      |
| --------- | ----- | ---- | ------------------------------------- | ------------------------------------ |
| `NFR-048` | MVP   | ASR  | Nepali medical terminology dictation  | ≥ 85% word accuracy for common terms |
| `NFR-049` | MVP   | ASR  | English medical terminology dictation | ≥ 90% word accuracy                  |
| `NFR-050` | MVP   | ASR  | Mixed Nepali-English dictation        | ≥ 80% word accuracy                  |
| `NFR-051` | MVP   | ASR  | Background noise (clinic environment) | ≥ 75% word accuracy                  |

---

## 14. Breach Notification Process Test (Added in v2.0)

| Test ID   | Phase | Area   | Scenario                       | Expected result                                                       |
| --------- | ----- | ------ | ------------------------------ | --------------------------------------------------------------------- |
| `NFR-052` | MVP   | breach | Simulated data breach detected | Notification to DPO within 1 hour                                     |
| `NFR-053` | MVP   | breach | Affected patients identified   | Data classification determines notification scope                     |
| `NFR-054` | MVP   | breach | Regulatory notification        | Report generated for Nepal Privacy Act 2075 authority within 72 hours |
