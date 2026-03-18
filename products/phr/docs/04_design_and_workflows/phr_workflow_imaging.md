# PHR Workflow — Imaging Viewer and Study Access

**Version:** 2.0  
**Date:** 2026-03-17
**Phase:** Core MVP

| Field              | Value                                                                                                                                 |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                     |
| **Classification** | Internal — Restricted                                                                                                                 |
| **Last Review**    | 2026-03-17                                                                                                                            |
| **Companion Docs** | [Route Contract Pack](phr_mvp_route_contract_pack.md), [Documents workflow](phr_workflow_documents.md), [Runtime Architecture](../03_architecture/phr_runtime_architecture.md) |

> **📌 What changed in v2.0:** Added imaging study metadata retrieval, embedded viewer launch, radiology report display, secure download handling, and caregiver/provider access rules for MVP.

---

## 1. Goal

Allow patients, providers, and authorized caregivers to open imaging studies, read the associated radiology report, and securely download study artifacts without introducing advanced workstation behavior that remains Phase 2.

---

## 2. Primary actors

- patient
- provider
- caregiver with imaging scope

---

## 3. Entry points

- patient imaging viewer page
- provider patient summary imaging links
- caregiver dependent summary imaging card

APIs:

- `GET /api/v1/imaging-studies/:id`
- `GET /api/v1/imaging-studies/:id/download`

---

## 4. Preconditions

- imaging study exists and is linked to the patient
- associated report metadata is available when present
- actor has patient relationship or delegated imaging scope
- signed viewer and download URL generation is configured

---

## 5. Data touched

- `ImagingStudy`
- `DiagnosticReport`
- `StoredObject`
- `AuditLog`

---

## 6. Happy path

### 6.1 Open imaging study

1. actor opens imaging surface from patient or dependent context
2. policy layer validates study visibility
3. platform returns study metadata, series summary, report conclusion, and a signed viewer URL when embedded viewing is supported
4. UI renders study header, modality, performed date, and report panel
5. audit record is written for imaging-study access

### 6.2 Download study artifacts

1. actor selects download from the imaging page
2. platform validates access and issues a short-lived signed download URL
3. download response includes expiry time and content type
4. audit record is written for the download action

---

## 7. Alternate and failure paths

- study exists but viewer session cannot be generated -> metadata and report still render with explicit viewer-unavailable state
- no radiology report present yet -> imaging metadata loads with report pending state
- caregiver without imaging scope -> forbidden and audited
- signed URL expires before use -> client requests a fresh URL through the API
- object storage unavailable -> viewer and download actions fail with upstream unavailable semantics, without leaking storage internals

---

## 8. UX requirements

- report text must be readable without forcing DICOM viewer launch
- download controls must show expiry expectations clearly
- patient and caregiver surfaces should explain modality and study date in plain language where possible
- loading skeletons must separate metadata load from viewer-session load

---

## 9. Acceptance criteria

- study metadata, report summary, and viewer-launch path are available for authorized actors
- secure download links are time-limited and actor-scoped
- caregiver access is controlled by delegated imaging scope
- all imaging reads and downloads generate audit evidence
- report-only fallback works when viewer embedding is temporarily unavailable

---

## 10. Offline and caching behavior

- imaging study metadata and report summary may be cached for patient or caregiver read surfaces when policy allows
- full DICOM viewer sessions and artifact downloads remain online-only in MVP
- cached imaging screens must show freshness indicators and never imply that interactive viewer features are available offline

---

## 11. Security and compliance notes

- signed viewer and download URLs must be short-lived and not reusable across actors
- diagnostic reports inherit the same consent and tenant checks as the imaging study itself
- download actions are sensitive-data events and must be retained in audit logs
- comparative imaging, multi-study overlays, and advanced workstation tooling remain Phase 2