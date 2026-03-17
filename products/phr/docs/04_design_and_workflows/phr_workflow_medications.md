# PHR Workflow — Medication Management

**Version:** 2.0  
**Date:** 2026-01-19
**Phase:** Core MVP

| Field              | Value                                                                                                                   |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                       |
| **Classification** | Internal                                                                                                                |
| **Last Review**    | 2026-01-19                                                                                                              |
| **Companion Docs** | [Activation Plan](../03_architecture/phr_mvp_activation_plan.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Added drug interaction checking requirements (Phase 2), emergency QR medication data flow, OCR prescription capture confidence thresholds, and offline medication reminder behavior.

---

## 1. Goal

Support safe viewing and entry of active and historical medication requests.

---

## 2. Primary actors

- patient
- provider
- caregiver in later delegated read flows

---

## 3. Entry points

- patient medications page
- provider medication request entry page

APIs:

- `GET /api/v1/patients/:id/medication-requests`
- `GET /api/v1/medications/:id`
- `POST /api/v1/medication-requests`

---

## 4. Preconditions

- patient context exists
- provider has authorized patient access for writes
- medication catalog entry is resolvable or creatable under policy

---

## 5. Data touched

- `Medication`
- `MedicationRequest`
- `AuditLog`
- later `ReminderPlan`

---

## 6. Happy path

### 6.1 Patient read flow

1. patient opens medications page
2. active and historical medication requests are fetched
3. grouped display is rendered

### 6.2 Provider create flow

1. provider opens medication request form in patient context
2. selects or enters medication details
3. validation runs on dosage/instructions/status
4. medication request is created
5. audit record is written
6. patient medication views refresh

---

## 7. Failure paths

- invalid dosage semantics -> validation error
- provider without access -> forbidden
- duplicate active medication request according to policy -> conflict or warning path

---

## 8. Notifications

Core MVP:

- optional patient notification for new medication request

Phase 2:

- reminder plan creation
- adherence tracking

---

## 9. Acceptance criteria

- active/history views are distinct
- dosage and status are visible and understandable
- provider-created requests are visible in patient timeline and medication list
- medication writes are audited

---

## 10. Drug Interaction Check — Phase 2 (Added in v2.0)

When a provider creates a new medication request:

1. System checks the new medication against patient's active medication list
2. Interaction check uses a local drug interaction database (WHO Essential Medicines List + Nepal National Formulary)
3. Severity levels: **Critical** (block + require override), **Major** (warn + require acknowledgment), **Minor** (inform only)
4. Provider must acknowledge interaction warning before proceeding
5. Interaction check result is stored in audit log

**Data source:** OpenFDA drug interaction API (when available) with local cache fallback. Nepal-specific formulations mapped manually.

---

## 11. Emergency QR Medication Payload (Added in v2.0)

Active medications are included in the patient's Emergency QR card:

| Field                     | Included | Format                                 |
| ------------------------- | -------- | -------------------------------------- |
| Medication name (generic) | Yes      | Plain text                             |
| Dosage                    | Yes      | Structured (amount + unit + frequency) |
| Route                     | Yes      | Code (oral, injection, topical)        |
| Start date                | Yes      | ISO 8601 date                          |
| Prescribing provider      | No       | Privacy excluded                       |
| Diagnosis/reason          | No       | Privacy excluded                       |

**Auto-refresh:** QR payload regenerates whenever active medication list changes.

---

## 12. OCR Prescription Import (Added in v2.0)

Patients can photograph paper prescriptions for digitization:

1. Patient takes photo of prescription via mobile app
2. Image uploaded to Ceph (encrypted)
3. Tesseract OCR processes image → extracts medication names, dosage, frequency
4. System attempts to match extracted text against medication database
5. Results presented to patient with **confidence scores**:
   - ≥ 90% → auto-populated, patient confirms
   - 70–89% → suggested with alternatives, patient selects
   - < 70% → flagged as "needs manual entry" with OCR text shown
6. Patient confirms or corrects extracted data
7. Confirmed data creates medication records with `source: ocr-import` provenance

**Language support:** Nepali and English prescription text. Devanagari OCR model required.

---

## 13. Offline Medication Reminders (Added in v2.0)

Medication reminders work offline on mobile:

- Reminder schedule synced to device when online
- Local notifications fire on schedule regardless of connectivity
- Adherence responses (taken / skipped / delayed) stored locally
- Synced to server when connectivity returns
- Provider dashboard shows adherence patterns (Phase 2)
