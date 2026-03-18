# PHR Workflow — Documents

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                         |
| ------------------ | ----------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                             |
| **Classification** | Internal                                                                                                                      |
| **Last Review**    | 2026-01-19                                                                                                                    |
| **Companion Docs** | [Runtime Architecture](../03_architecture/phr_runtime_architecture.md), [Route Contract Pack](phr_mvp_route_contract_pack.md) |

> **📌 What changed in v2.0:** Added document integrity verification (checksum), virus/malware scanning requirement, document-level access control, offline document viewing (cached), and Ceph storage encryption details.
> **Phase:** Core MVP

---

## 1. Goal

Support secure upload, storage, listing, preview, download, OCR extraction, and reviewed document-to-record conversion of patient documents.

---

## 2. Primary actors

- patient
- provider
- caregiver in active delegated scope

---

## 3. Entry points

- patient documents page
- document upload dialog/form

APIs:

- `GET /api/v1/patients/:id/documents`
- `POST /api/v1/documents`
- `GET /api/v1/documents/:id`
- `POST /api/v1/documents/:id/ocr`
- `GET /api/v1/ocr-results/:id`
- `POST /api/v1/ocr-results/:id/confirm`

---

## 4. Preconditions

- file type and size policy is defined
- Ceph object storage is reachable
- patient access policy is resolved

---

## 5. Data touched

- `DocumentReference`
- `StoredObject`
- `DocumentVersion`
- `AuditLog`
- `OcrExtractionResult`
- `InputProvenance`
- `ReviewQueueItem`

---

## 6. Happy path

1. actor selects file and enters metadata
2. API validates metadata and permissions
3. object is uploaded to Ceph
4. object metadata is persisted
5. `DocumentReference` and version records are created
6. OCR can be triggered automatically or manually for supported scanned content
7. user reviews extracted fields and confirms accepted values
8. created records are linked through provenance and audit events
9. document becomes visible in listings

---

## 7. Alternate and failure paths

- unsupported file type -> validation error
- object upload failure -> upstream unavailable
- DB write fails after object upload -> compensating cleanup or orphan-object review queue required
- unauthorized download -> forbidden and audited

---

## 8. UX requirements

- upload progress
- preview when possible
- fallback for unknown preview type
- version and metadata visibility
- OCR status, confidence, and review affordances for supported document types

---

## 9. Acceptance criteria

- uploads persist both metadata and object reference
- downloads are permission-checked
- document list is patient-scoped and consent-aware
- OCR review can create confirmed structured records with provenance
- object/document mismatch is detectable and recoverable

---

## 10. Document Integrity Verification (Added in v2.0)

Every uploaded document has integrity verification:

1. **On upload:** SHA-256 hash computed and stored alongside metadata
2. **On download:** hash re-verified against stored value
3. **Periodic audit:** background job verifies all document hashes monthly
4. **Mismatch handling:** Document flagged as potentially corrupted → alert to ops → quarantine from user access until reviewed

**Tamper evidence:** Hash is written to an append-only audit log that is separate from the mutable metadata store.

---

## 11. Virus/Malware Scanning (Added in v2.0)

All uploaded files pass through malware scanning before becoming accessible:

1. Upload lands in a **quarantine bucket** in Ceph (not accessible to users)
2. ClamAV scan runs asynchronously (target: < 60 seconds for files ≤ 50 MB)
3. **Clean** → file moved to permanent bucket, `DocumentReference` status set to `current`
4. **Infected** → file deleted, user notified ("File rejected: security scan failed"), audit log entry
5. **Scan timeout** → file remains in quarantine, retry up to 3 times, then flag for manual review

**File type restrictions:** Allowed: PDF, JPEG, PNG, DICOM. Blocked: EXE, JS, HTML, ZIP (configurable).
**Max file size:** 50 MB (configurable per tenant).

---

## 12. Document-Level Access Control (Added in v2.0)

Each document has independent access control beyond the resource-level consent:

| Access Level    | Who Can See                               | Set By                                      |
| --------------- | ----------------------------------------- | ------------------------------------------- |
| Private         | Patient only                              | Default on self-upload                      |
| Provider-shared | Patient + specific granted providers      | Patient explicit selection                  |
| Care-team       | Patient + all providers with active grant | Patient explicit selection                  |
| Emergency       | Included in break-the-glass scope         | System (for critical docs: allergies, meds) |

**Provider uploads:** Documents uploaded by providers default to "Care-team" visibility. Patient can restrict after upload.

---

## 13. Offline Document Viewing (Added in v2.0)

Mobile app caches recent documents for offline access:

- Last 20 viewed documents cached locally (encrypted storage)
- Cache size limit: 200 MB per patient (configurable)
- Cache entries expire after 7 days without connectivity
- Offline-viewed documents show "Cached copy — last synced: {timestamp}" indicator
- New uploads from approved patient and caregiver flows are queued locally and synced when online
- Malware scanning, checksum verification, and visibility enforcement complete only after the queued upload reaches the server

---

## 14. Ceph Storage Encryption (Added in v2.0)

| Layer      | Encryption                                 | Key Management                            |
| ---------- | ------------------------------------------ | ----------------------------------------- |
| In transit | TLS 1.3 (client ↔ Ceph gateway)            | Certificate rotation every 90 days        |
| At rest    | AES-256-GCM (Ceph OSD encryption)          | Keys in HashiCorp Vault, rotated annually |
| Per-object | Server-side encryption (SSE-S3 compatible) | Per-tenant master key                     |
| Backup     | Encrypted backup to secondary site         | Separate backup encryption key            |
