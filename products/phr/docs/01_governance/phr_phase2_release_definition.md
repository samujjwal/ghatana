# PHR Platform — Phase 2 Release Definition

**Version:** 2.0  
**Date:** 2026-03-17  
**Last reviewed:** 2026-03-17  
**Next review due:** 2026-05-17  
**Document owner:** PHR Product Lead  
**Approval status:** Active  
**Classification:** Internal — Restricted

This document defines the formal delivery boundary for the committed **Phase 2** extension set that follows Core MVP.

> **Global context:** Phase 2 capabilities are informed by telemedicine deployments in India (eSanjeevani — 150M+ consultations), Rwanda's community health worker tablet programs, the UK NHS App's family access delegation model, and Estonia's e-Prescription system. Offline-first design draws from OpenMRS deployments in Sub-Saharan Africa and India's ABDM offline voucher model.

---

## 1. Release intent

Phase 2 expands Core MVP into an operationally richer patient and provider experience with:

- telemedicine consultation workflows (video + audio, adaptive bitrate for Nepal's variable bandwidth)
- claims and claim tracking (openIMIS FHIR Claim format, end-to-end submission lifecycle)
- family health hub analytics and multi-profile household administration layered on top of the MVP caregiver baseline
- advanced offline automation, richer merge tooling, and long-duration disconnected operation layered on top of the MVP sync baseline
- payment refunds, subscriptions, and reconciliation automation layered on top of MVP bill-pay flows
- richer reminder and notification handling (SMS + push + voice reminders for low-literacy users)
- claims reimbursement and denial-management workflows
- advanced referral coordination and specialist feedback loops
- advanced imaging comparison and diagnostics workflows

Phase 2 assumes Core MVP platform, identity, consent, clinical, document, and scheduling foundations already exist.

> **Design principle — "Last-Mile Healthcare":** Phase 2 features are designed to work in Nepal's most challenging environments: rural health posts with intermittent connectivity, busy urban OPDs with 200+ daily patient visits, and caregiving scenarios where elderly patients depend on family members for digital access. Every feature must degrade gracefully when network, literacy, or device constraints apply.

---

## 2. In-scope business capabilities

### 2.1 Telemedicine

- video consultation room (Jitsi-based, self-hosted, E2EE capable)
- audio-only fallback (automatic degradation when bandwidth < 500kbps)
- adaptive bitrate streaming (500kbps–2Mbps based on network quality)
- call metadata (duration, quality metrics, participant join/leave events)
- recording consent (explicit bilateral consent capture, audited)
- transcript linkage (Nepali and English ASR with medical terminology)
- consultation summary linkage to encounters
- session reconnect handling (auto-rejoin within 2-minute window after network drop)
- waiting room with queue position indicator

### 2.2 Insurance extension

- claim creation
- claim submission attempts
- claim status retrieval
- attachment linkage to claims

### 2.3 Family and caregiver expansion

- family account and household administration model
- shared family health hub and family-wide analytics
- family plan pricing and subscription packaging
- cross-timezone NRN caregiver notification model
- pediatric and elderly-care dashboards beyond the MVP dependent summary

> **Nepal cultural context:** In Nepal's joint-family system, healthcare decisions are often made collectively. The caregiver model must support multiple caregivers per patient (e.g., spouse + adult child), with each having independently scoped and revocable access grants. This aligns with the UK NHS App's proxy access model but adapted for Nepal's family dynamics.

### 2.4 Experience and resilience expansion

- offline support extended to longer disconnected windows, larger queue volumes, and richer retry orchestration
- advanced sync diagnostics, operator tooling, and replay/recovery support
- richer reminder and notification handling (SMS, push, voice call reminders)
- data freshness indicators ("Last synced X minutes ago" visible to user)
- background sync with exponential backoff on repeated failures
- encrypted local storage (SQLite with AES-256 encryption on mobile)

Core MVP already includes delegated caregiver access, generalized offline queueing and conflict handling for approved flows, payment initiation and receipts, referral creation/tracking, and imaging-viewer baseline. Phase 2 expands those foundations rather than introducing them for the first time.

### 2.5 Payment and financial expansion

- claim-linked reimbursement management
- refund workflows and settlement exceptions
- subscription management (upgrade/downgrade/cancel)
- automated reconciliation and finance operations views

### 2.6 Referral and imaging expansion

- specialist feedback loops and referral outcome exchange
- referral work-queue orchestration across facilities
- comparative imaging review and advanced diagnostics collaboration

---

## 3. Phase 2 activation inventory

### 3.1 Modules

- `TelemedicineModule`
- `BillingModule`
- `ClaimsModule` expansion within `BillingModule`
- `NotificationModule` expansion

### 3.2 Resources and app tables

- `Claim`
- `ClaimResponse`
- `RelatedPerson`
- `Schedule`
- `Slot`
- `TelemedicineSession`
- `TelemedicineParticipant`
- `TranscriptAsset`
- `RecordingAsset`
- `ClaimSubmissionAttempt`
- `SubscriptionPlan`
- `RefundRequest`

### 3.3 UI surfaces

- patient claims
- family health hub
- claims and reimbursement workbench
- telemedicine consultation room

---

## 4. Dependencies

### 4.1 Core MVP foundations required

- stable encounter and appointment context from Core MVP
- stable document storage and access control
- consent engine operational and tested
- audit logging verified and immutable

### 4.2 New infrastructure

- Jitsi Meet deployment (self-hosted, Nepal data center, WebRTC + SRTP)
- TURN/STUN server infrastructure for NAT traversal (coturn, self-hosted)
- streaming media capacity planning (concurrent sessions: 100 target, 500 peak)
- recording storage allocation in Ceph (encrypted, retention-policy managed)

### 4.3 External integrations

- openIMIS claim submission sandbox validated and production credentials obtained
- Khalti/eSewa payment gateway sandbox integration tested
- explicit retention and consent policies for recordings and transcripts (legal review completed)

### 4.4 Organizational

- clinical advisory board has reviewed telemedicine workflow and consent language
- legal review of recording retention (Nepal IT Bill implications assessed)
- provider training materials for telemedicine workflow developed

---

## 5. Exit criteria

### 5.1 Functional

- telemedicine session can be established, joined, ended, and audited end-to-end
- audio-only fallback activates automatically when bandwidth drops below threshold
- session reconnect works within 2-minute window after network interruption
- claims can be created, submitted to openIMIS, and status retrieved reliably
- duplicate claim submission is prevented (idempotency key enforcement)
- caregiver family-hub features remain scoped by active grants and subscription entitlements
- offline recovery tooling can replay queued operations after multi-day disconnection
- claim reimbursement and denial workflows complete end-to-end
- refund and subscription-management flows complete end-to-end
- advanced referral and specialist feedback loop completes across facilities

### 5.2 Quality

- media/network failure paths are tested (packet loss up to 5%, jitter up to 50ms, bandwidth 500kbps)
- duplicate submission protections exist for claims and telemedicine joins
- accessibility covers captions, transcripts, large text, and high contrast
- telemedicine works on 3G connection with adaptive quality
- offline recovery tested with 7-day disconnected windows and replay of large queue volumes
- household and caregiver workflows tested with multiple concurrent caregivers per patient

### 5.3 Security

- telemedicine sessions use end-to-end encryption (SRTP for media, TLS 1.3 for signaling)
- recording encryption key management documented and implemented
- recording retention policy enforced automatically (configurable per-tenant)
- claim submission data encrypted in transit and at rest
- offline cached data encrypted at rest on device (AES-256)
- payment PCI DSS compliance verified for payment flow
- session tokens for telemedicine are HMAC-signed, single-use, with configurable expiry

### 5.4 Compliance

- recording consent language reviewed by legal counsel and available in Nepali and English
- recording retention rules configured (default 90 days, configurable per policy)
- transcript and recording access is audited with patient visibility
- claims retain required submission history and attachments for 7 years per HIB policy
- offline data handling complies with Privacy Act 2075 (data minimization for cached records)
- telemedicine consent captures explicit bilateral agreement before recording starts

---

## 6. Risk register

| Risk                                           | Likelihood | Impact   | Mitigation                                                                                            |
| ---------------------------------------------- | ---------- | -------- | ----------------------------------------------------------------------------------------------------- |
| Telemedicine quality poor on 3G in rural Nepal | High       | High     | Adaptive bitrate; audio-only fallback; pre-call bandwidth check with user warning                     |
| Claim rejection rate high from openIMIS        | Medium     | High     | Pre-submission validation against openIMIS schema; clear rejection reason display; re-submission flow |
| Offline sync conflicts cause data loss         | Medium     | Critical | Immutable event log for offline changes; conflict resolution UI; automatic backup before sync         |
| Recording storage costs exceed budget          | Medium     | Medium   | Retention policy with automatic purging; compressed recording format; tiered storage                  |
| Caregiver access misuse                        | Low        | High     | Time-bounded grants; revocation alerts to patient; audit trail visible to patient                     |
| Nepal IT Bill changes recording consent rules  | Low        | High     | Modular consent engine; legal advisory board; configurable retention policies                         |
| Payment gateway downtime                       | Medium     | Medium   | Graceful degradation; retry with exponential backoff; multiple gateway support                        |

---

## 7. Success measures

| Measure                                        | Target                        | Method                          |
| ---------------------------------------------- | ----------------------------- | ------------------------------- |
| Telemedicine session completion rate           | > 85%                         | Session state analytics         |
| Claim submission success rate (first attempt)  | > 90%                         | openIMIS integration monitoring |
| Caregiver activation rate                      | > 30% of eligible patients    | Family module analytics         |
| Offline data entry sync success rate           | > 99%                         | Sync event tracking             |
| Median telemedicine session duration           | 8–15 minutes                  | Session metadata analysis       |
| Provider adoption of telemedicine              | > 50% of registered providers | Provider activity tracking      |
| Claim processing time (submission to response) | < 72 hours                    | Claim lifecycle tracking        |

---

## 8. Non-goals

- advanced AI diagnostic recommendations
- custom medical ASR model training (deferred to Phase 3)
- wearable and IoT device integration
- public health research data features
- rich analytics warehouse
- group telemedicine consultations (1-on-1 only in Phase 2)
- cross-border telemedicine (Nepal-only in Phase 2)
- automated claim adjudication (human-reviewed only)

---

## 9. Global best practices alignment

| System               | Country        | Lesson Applied                                                                                  |
| -------------------- | -------------- | ----------------------------------------------------------------------------------------------- |
| **eSanjeevani**      | India          | Adaptive bitrate telemedicine; audio fallback; waiting room UX; 150M+ consultations proven      |
| **NHS Proxy Access** | UK             | Caregiver delegation model; time-bounded, auditable, revocable proxy access for family members  |
| **OpenMRS Offline**  | Rwanda/Kenya   | Offline-first sync architecture for resource-constrained settings; conflict resolution patterns |
| **openIMIS Claims**  | Tanzania/Nepal | FHIR Claim resource mapping; idempotent submission; attachment handling                         |
| **e-Prescription**   | Estonia        | End-to-end digital prescription with pharmacy integration; claim linkage                        |

---

## 10. Final decision

Phase 2 should be treated as the **consultation, claims, delegation, and resilience expansion release** on top of the Core MVP record platform.

Any work outside that boundary must be tracked as Phase 3 unless this document is revised through the change control process defined in the [Core MVP Release Definition](phr_core_mvp_release_definition.md).
