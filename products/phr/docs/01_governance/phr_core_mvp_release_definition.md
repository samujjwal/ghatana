# PHR Platform — Core MVP Release Definition

**Version:** 2.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-04-17  
**Document owner:** PHR Product Lead  
**Approval status:** Active  
**Classification:** Internal — Restricted

This document defines the formal delivery boundary for the **Core MVP** release of the Ghatana PHR platform for Nepal.

> **Global context:** This release definition is informed by proven PHR/EHR deployment patterns from India's Ayushman Bharat Digital Mission (ABDM/ABHA), Estonia's X-Road health exchange, Australia's My Health Record, the UK's NHS App, and Rwanda's OpenMRS national rollout. Where applicable, lessons from these deployments are embedded directly into requirements, exit criteria, and risk controls.

It is the release-governance companion to:

- [03_architecture/phr_mvp_activation_plan.md](../03_architecture/phr_mvp_activation_plan.md)
- [02_strategy_and_requirements/phr-e2e-requirements.md](../02_strategy_and_requirements/phr-e2e-requirements.md)
- [04_design_and_workflows/phr_mvp_route_contract_pack.md](../04_design_and_workflows/phr_mvp_route_contract_pack.md)
- [04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md](../04_design_and_workflows/phr_screen_by_screen_mvp_implementation_matrix.md)

---

## 1. Release intent

Core MVP delivers the first production-grade, patient-owned health record baseline for Nepal with:

- patient identity and profile (inspired by India's ABHA health ID — federated, portable, patient-owned)
- provider-readable longitudinal records
- appointments
- document storage and retrieval
- OCR-assisted document digitization and review (critical for Nepal's paper-heavy health ecosystem)
- voice-assisted patient and provider data entry (Nepali ASR — accessibility differentiator)
- patient-controlled sharing (consent-first model aligned with Estonia's X-Road data exchange principles)
- insurance coverage and eligibility baseline (openIMIS/HIB integration)
- mobile, web, and desktop application delivery
- auditability, security, and Nepal-only data handling (data sovereignty per Directive 2081)
- emergency medical summary with QR code (inspired by Australia's My Health Record emergency access)
- bilingual interface (Nepali and English) with health literacy–optimized content

Core MVP does **not** include telemedicine call-room behavior, claims, caregiver portals, or offline sync as user-facing shipped features.

> **Design principle — "Paper-to-Digital Bridge":** Nepal's healthcare system remains predominantly paper-based. Core MVP explicitly prioritizes OCR and voice as primary data entry channels alongside manual forms, recognizing that most patient data will originate from physical documents and verbal interactions. This mirrors India's ABDM strategy of enabling digital health records from paper prescriptions via DigiLocker integration.

---

## 2. In-scope business capabilities

### 2.1 Patient identity and profile

- sign in and session bootstrap (Keycloak OIDC with MFA for sensitive operations)
- patient registration (with national ID/citizenship number verification where available)
- patient profile view/edit
- emergency contact capture
- emergency medical summary with QR code for first responders

### 2.2 Clinical record baseline

- medical timeline
- conditions list
- observations and trends
- medications list
- provider patient summary
- provider encounter detail and update shell
- provider observation entry
- provider medication request entry

### 2.3 Scheduling

- appointment list
- appointment booking
- provider calendar baseline

### 2.4 Documents

- document upload metadata + file storage
- multi-modal upload via file picker and camera capture where supported
- document listing
- document preview/download path
- OCR extraction
- OCR review and confirm

### 2.5 Assisted data input

- patient voice input
- provider voice dictation
- provenance tracking from source input to created records

### 2.6 Consent and governance

- list access grants
- create access grant
- revoke access grant
- audit viewer

### 2.7 Insurance baseline

- coverage summary
- eligibility check

### 2.8 Delivery channels

- mobile apps
- web app
- desktop app shell using the same routed application surface where feasible

---

## 3. Explicitly out of scope for Core MVP

- telemedicine video/audio consultation room
- telemedicine recording consent and transcript linkage
- claim submission and claim status
- caregiver/dependent portal
- offline sync
- payment flows
- referrals
- imaging viewer

These belong to the committed Phase 2 extension set unless reprioritized by formal change control.

---

## 4. Release inventory

### 4.1 Active modules

- `IdentityModule`
- `OrganizationModule`
- `PractitionerModule`
- `PatientModule`
- `ConsentModule`
- `AuditModule`
- `EncounterModule`
- `ObservationModule`
- `ConditionModule`
- `MedicationModule`
- `AppointmentModule`
- `DocumentModule`
- `DataInputModule`
- `InsuranceModule`
- `AdminModule`

### 4.2 Active resources and tables

- `Organization`
- `Practitioner`
- `Patient`
- `ConsentGrant`
- `AuditLog`
- `Encounter`
- `Observation`
- `Condition`
- `Medication`
- `MedicationRequest`
- `Appointment`
- `DocumentReference`
- `OcrExtractionResult`
- `AudioTranscription`
- `InputProvenance`
- `ReviewQueueItem`
- `Coverage`

### 4.3 Active UI surfaces

- sign in
- patient registration
- patient dashboard
- patient profile
- patient timeline
- patient conditions
- patient observations
- patient medications
- patient appointments
- patient appointment booking
- patient documents
- patient document upload
- patient OCR review
- patient voice input
- patient insurance
- patient access grants
- provider dashboard
- provider patient search
- provider patient summary
- provider encounter detail
- provider observation entry
- provider medication request entry
- provider voice dictation
- provider calendar
- admin dashboard
- admin audit viewer

### 4.4 Active APIs

- auth/session contracts
- patient contracts
- encounter/timeline contracts
- observation contracts
- condition contracts
- medication contracts
- appointment contracts
- document contracts
- OCR and transcription contracts
- coverage and eligibility contracts
- consent/sharing contracts
- audit contracts

---

## 5. Release dependencies

### 5.1 Platform dependencies

- Keycloak or equivalent identity provider integration (OIDC/OAuth2, MFA support)
- PostgreSQL (with AES-256 encryption at rest, row-level security for multi-tenancy)
- Ceph/RADOS Gateway object storage (on-premise, S3-compatible, LGPL licensed)
- OCR service integration (Tesseract or equivalent, on-premise)
- ASR service integration (Vosk on-premise streaming + Whisper batch, Apache 2.0/MIT)
- SMS provider integration (Sparrow SMS or equivalent Nepal-based provider)
- email delivery for recovery and notifications (Postfix on-premise)
- monitoring, logging, tracing, and alerting stack (Prometheus + Grafana + OpenSearch + Fluent Bit)
- ntfy (self-hosted push notifications) + APNS for iOS

### 5.2 Data and compliance dependencies

- Nepal-only data hosting (Nepal-IX or equivalent certified local data center)
- geographic redundancy within Nepal for backup (secondary data center in different city)
- security hardening and audit logging (immutable, tamper-evident audit trail)
- privacy policy and consent language in Nepali and English
- HIB/openIMIS access for eligibility baseline (sandbox credentials by development start)
- Data Protection Impact Assessment (DPIA) completed per Privacy Act 2075
- third-party security audit firm engagement planned (Vairav Technology, InfoDevelopers, or equivalent)

### 5.3 Organizational dependencies

- on-call rotation and incident response team formed
- clinical advisory board consulted on workflow accuracy
- pilot hospital partnership confirmed (minimum 2 facilities for beta testing)
- SIL-Nepal preliminary FHIR profile submission planned

---

## 6. Exit criteria

Core MVP is release-ready only when all of the following are true.

### 6.1 Functional

- all in-scope screens are implemented and tested across mobile, web, and desktop
- all in-scope APIs are versioned under `/api/v1` with OpenAPI documentation generated
- all critical write paths produce audit logs (immutable, tamper-evident)
- consent rules are enforced on patient record reads (verified by automated consent test suite)
- document upload and retrieval work with Ceph-backed storage (checksum verification on download)
- OCR extraction and human review complete successfully for supported document types (lab reports, prescriptions, insurance cards)
- patient and provider voice entry flows create reviewed clinical data with provenance
- eligibility checks return stable success/failure semantics with logged response
- emergency medical summary is accessible via QR code without full authentication
- bilingual UI (Nepali and English) is functional for all shipped surfaces

### 6.2 Quality

- unit, integration, and UI e2e packs pass for Core MVP scope
- unit test coverage ≥ 80% for business logic modules
- validation and permission failures are consistently shaped per error model spec
- no unresolved P0 or P1 security issues remain
- accessibility baseline passes WCAG 2.2 AA checks for shipped surfaces (automated + manual audit)
- performance: API p90 response time < 500ms; page load p90 < 2s on 3G connection
- localization: all user-facing strings verified in both Nepali and English

### 6.3 Security (mandatory before public release)

- OWASP Top 10 vulnerability scan completed with zero critical/high findings
- static application security testing (SAST) integrated into CI pipeline
- dependency vulnerability scanning (OWASP Dependency-Check or equivalent) with no known critical CVEs
- penetration test completed by approved third-party firm (internal pre-audit minimum)
- all secrets managed via vault or environment-injected configuration (no hardcoded credentials)
- TLS 1.3 enforced on all external and inter-service communication
- rate limiting configured on all public-facing endpoints
- session management hardening: token expiry, refresh rotation, concurrent session limits

### 6.4 Operational

- dashboards and alerting exist for API latency/errors, DB health, object storage, and auth failures
- backup/restore runbook is validated (tested restore from backup in non-production)
- on-call and incident runbooks exist with escalation paths
- release rollback procedure is documented and tested
- disaster recovery plan documented with RPO < 24 hours, RTO < 4 hours
- monitoring covers Nepal data residency compliance (alert if any data egress detected)

### 6.5 Compliance

- Data Protection Impact Assessment (DPIA) completed and filed
- privacy policy published in Nepali and English
- consent management system operational with granular consent capture
- audit log retention policy configured (minimum 1 year, recommended 3 years)
- data classification applied to all tables (PII, PHI, operational, public)
- SIL-Nepal preliminary FHIR profile submission completed (full certification targeted for Phase 2)

---

## 7. Change control

The following changes require explicit approval before entering Core MVP:

- adding new Phase 2 user-facing flows
- adding a new app-specific table with cross-module ownership impact
- introducing telemedicine session behavior
- introducing claims behavior
- changing patient consent semantics
- changing storage or hosting constraints affecting Nepal data residency
- modifying the FHIR resource activation list
- changing authentication or authorization model
- adding third-party service dependencies

**Change request process:** Submit a change request document referencing this release definition, the impacted requirement IDs, estimated effort, and risk assessment. Approval requires sign-off from the PHR Product Lead and Technical Lead.

---

## 8. Risk register

| Risk                                                | Likelihood | Impact   | Mitigation                                                                                                |
| --------------------------------------------------- | ---------- | -------- | --------------------------------------------------------------------------------------------------------- |
| OCR accuracy below 80% for Nepali medical documents | Medium     | High     | Ship with manual entry as primary path; OCR as accelerator; iterative model improvement                   |
| ASR accuracy below 85% for Nepali medical terms     | Medium     | High     | Use Vosk with custom medical vocabulary; fallback to manual entry; capture corrections for model training |
| Nepal-IX data center downtime exceeds SLA           | Low        | Critical | Geographic redundancy with secondary Nepal DC; automated failover; tested DR plan                         |
| SIL-Nepal FHIR certification delayed                | Medium     | Medium   | Build FHIR-compliant from day one; submit profiles early; decouple certification from release             |
| openIMIS API changes or unavailability              | Medium     | Medium   | Abstract integration behind adapter layer; maintain fallback eligibility check flow                       |
| Pilot hospital onboarding slower than planned       | Medium     | Medium   | Engage minimum 3 hospitals early; prioritize hospitals already on openIMIS                                |
| Regulatory changes (IT Bill, Directive amendments)  | Low        | High     | Maintain regulatory advisory relationship with MoHP; modular compliance design                            |
| Security audit finds critical vulnerabilities       | Medium     | High     | Continuous SAST/DAST in CI; internal pen-test before external audit; security champions in each team      |
| Low initial user adoption                           | Medium     | Medium   | FCHV network distribution; hospital front-desk QR codes; telecom bundle partnerships                      |

---

## 9. Success measures

| Measure                                       | Target                                   | Method                                            |
| --------------------------------------------- | ---------------------------------------- | ------------------------------------------------- |
| Patient registration and first-login journey  | 95% success rate                         | Analytics funnel tracking                         |
| Provider read access under consent rules      | Zero unauthorized access incidents       | Audit log analysis + automated consent test suite |
| Appointment booking and reminder delivery     | 90% reminder delivery rate               | SMS/push delivery tracking                        |
| Document upload and retrieval                 | < 5s for 10MB document on 3G             | Performance monitoring                            |
| Eligibility check for covered patients        | 95% successful response rate             | openIMIS integration monitoring                   |
| Audit completeness on sensitive record access | 100% coverage                            | Automated audit gap detection                     |
| Accessibility compliance                      | WCAG 2.2 AA pass on all shipped surfaces | Automated + manual accessibility audit            |
| Security posture                              | Zero P0/P1 vulnerabilities at release    | SAST/DAST + pen-test report                       |
| Data residency compliance                     | 100% in-Nepal storage verified           | Infrastructure audit + monitoring alerts          |
| User satisfaction (pilot)                     | NPS > 40                                 | Post-pilot survey                                 |

---

## 10. Global best practices alignment

This release incorporates lessons from nationally deployed PHR/EHR systems:

| System                   | Country      | Lesson Applied                                                                                                                  |
| ------------------------ | ------------ | ------------------------------------------------------------------------------------------------------------------------------- |
| **ABDM / ABHA**          | India        | Federated health ID model; patient-owned record with consent-based sharing; paper-to-digital bridge via DigiLocker pattern      |
| **X-Road**               | Estonia      | Consent-first data exchange; immutable audit trail visible to patients; data never copied — queried in place (future direction) |
| **My Health Record**     | Australia    | Emergency access QR code; opt-out model considerations; granular access logging visible to patients                             |
| **NHS App**              | UK           | Bilingual interface priority; integration with national insurance system; accessibility-first design                            |
| **OpenMRS**              | Rwanda/Kenya | Open-source, on-premise deployment in resource-constrained settings; community health worker integration                        |
| **WHO SMART Guidelines** | Global       | Digital adaptation of clinical guidelines; interoperability through FHIR; equity-centered design                                |

---

## 11. Nepal market innovations

Core MVP includes or enables the following Nepal-specific innovations:

1. **FCHV Digital Bridge:** Female Community Health Volunteer (FCHV) network of 55,000+ can register patients during home visits using simplified mobile flows, bridging the digital divide for rural populations.
2. **NRN Health Corridor (future):** Non-Resident Nepali community can manage healthcare for elderly parents in Nepal — a use case unique to Nepal's diaspora demographics.
3. **Nepali Medical ASR:** First-to-market voice input in Nepali for medical contexts, enabling providers in busy outpatient departments to dictate rather than type.
4. **Paper-to-Digital OCR Pipeline:** Designed for Nepal's paper-heavy healthcare system where most records exist as handwritten or printed physical documents.
5. **openIMIS-Native Insurance Integration:** Direct integration with Nepal's Social Health Insurance via openIMIS, covering 9 million enrolled individuals.

---

## 12. Expansion readiness

Core MVP is architected for regional expansion beyond Nepal:

- FHIR R4 compliance enables interoperability with any FHIR-conformant system globally
- multi-tenant architecture supports separate deployments per country/region
- modular consent engine can be adapted to different regulatory frameworks (GDPR, HIPAA, PDPA)
- language and localization framework supports addition of Hindi, Bengali, Maithili, and other South Asian languages
- insurance integration is abstracted behind adapter layer (openIMIS today; adaptable to other payer systems)

Target expansion markets: Eastern India (Bihar, UP, West Bengal), Bangladesh, Bhutan, Sri Lanka.

---

## 13. Final decision

Core MVP should be treated as a **records, scheduling, sharing, and insurance-baseline release**.
Any work outside that boundary must be tracked as Phase 2 unless this document is revised through the change control process defined in Section 7.
