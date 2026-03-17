# PHR Platform — Telemedicine Contract and Workflow Pack

**Version:** 2.0  
**Date:** 2026-01-19

| Field              | Value                                                                                                                                                   |
| ------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Document Owner** | PHR Platform Lead                                                                                                                                       |
| **Classification** | Internal — Restricted                                                                                                                                   |
| **Last Review**    | 2026-01-19                                                                                                                                              |
| **Companion Docs** | [Phase 2 Release Definition](../01_governance/phr_phase2_release_definition.md), [Runtime Architecture](../03_architecture/phr_runtime_architecture.md) |

> **📌 What changed in v2.0:** Added audio-only fallback for 2G/EDGE networks, FCHV-mediated telemedicine flow, store-and-forward imaging, E2E encryption requirements, recording consent model, and network quality adaptive bitrate specifications (inspired by India eSanjeevani).
> **Phase:** 2

This document formalizes the Phase 2 telemedicine design boundary that was intentionally excluded from the Core MVP route pack.

---

## 1. Scope

This pack covers:

- consultation room creation and join flow
- audio/video session state
- recording consent
- transcript linkage
- encounter linkage
- telemedicine-specific test and design considerations

It does not cover:

- custom ASR model training
- advanced AI summarization
- group consultations
- marketplace/provider ratings

---

## 2. Primary actors

- patient
- provider
- provider support staff
- admin/compliance for audit access

---

## 3. App-owned tables

- `TelemedicineSession`
- `TelemedicineParticipant`
- `TranscriptAsset`
- `RecordingAsset`

---

## 4. Proposed routes

| Method | Route                                                 | Purpose                                                     |
| ------ | ----------------------------------------------------- | ----------------------------------------------------------- |
| `POST` | `/api/v1/telemedicine/sessions`                       | create consultation room from appointment/encounter context |
| `GET`  | `/api/v1/telemedicine/sessions/:id`                   | get session metadata and joinability                        |
| `POST` | `/api/v1/telemedicine/sessions/:id/join`              | issue join token/connection details                         |
| `POST` | `/api/v1/telemedicine/sessions/:id/leave`             | mark participant left                                       |
| `POST` | `/api/v1/telemedicine/sessions/:id/end`               | end consultation                                            |
| `POST` | `/api/v1/telemedicine/sessions/:id/recording-consent` | capture recording consent state                             |
| `GET`  | `/api/v1/telemedicine/sessions/:id/transcript`        | fetch transcript metadata                                   |
| `POST` | `/api/v1/telemedicine/sessions/:id/link-encounter`    | attach session outcome to encounter                         |

---

## 5. Session state model

### 5.1 `TelemedicineSession.status`

- `scheduled`
- `ready`
- `active`
- `ended`
- `cancelled`
- `failed`

### 5.2 `TelemedicineParticipant.status`

- `invited`
- `joined`
- `left`
- `dropped`

### 5.3 Consent state

- `pending`
- `granted`
- `declined`
- `not_required`

---

## 6. Workflow

### 6.1 Happy path

1. appointment exists
2. provider or system creates telemedicine session
3. patient and provider request join tokens
4. media session becomes active
5. optional recording consent captured
6. session ends
7. transcript/recording metadata linked if retained
8. encounter outcome is updated
9. audit trail is written

### 6.2 Failure paths

- patient no-show
- provider no-show
- consent declined for recording
- media connection failed
- session dropped and resumed
- transcript unavailable or delayed

---

## 7. Required metadata

### 7.1 Session

- `appointmentId`
- `patientId`
- `providerId`
- `encounterId?`
- `scheduledStart`
- `actualStart?`
- `actualEnd?`
- `status`
- `recordingConsentState`
- `createdByActorId`

### 7.2 Transcript asset

- `sessionId`
- `storageRef`
- `language`
- `status`
- `retentionUntil?`

### 7.3 Recording asset

- `sessionId`
- `storageRef`
- `encryptionState`
- `retentionUntil`
- `accessPolicy`

---

## 8. Policy rules

- patient may only join their own session
- provider may only join sessions within their assignment/scope
- support staff may create sessions only where allowed by policy
- transcript and recording access must be separately authorized from room access
- recording requires explicit consent capture and audit

---

## 9. Integration needs

- WebRTC/Jitsi or equivalent media layer
- token minting service for room join
- transcript service or metadata bridge if captions/transcripts are enabled
- encrypted storage for retained recordings

---

## 10. Test focus areas

- room create/join/leave/end
- duplicate join attempts
- participant reconnect
- recording consent granted/declined
- transcript visibility rules
- encounter linkage after session end
- audit completeness for all session transitions

---

## 11. Final recommendation

Do not let telemedicine emerge implicitly from appointment or voice-input work.
Implement it as a dedicated Phase 2 service/workflow pack with its own routes, tables, and policy model.

---

## 12. Audio-Only 2G Fallback (Added in v2.0)

**Context:** 40% of Nepal's rural population relies on 2G networks. Video telemedicine is not viable.

**Fallback strategy:**

1. Client detects effective bandwidth on session join
2. If bandwidth < 100 kbps → auto-switch to audio-only mode
3. If bandwidth < 30 kbps → offer SIP/PSTN fallback (phone call bridge)
4. UI shows clear indicator: "Audio-only mode — low connectivity detected"
5. Provider can manually switch between video/audio/phone modes

**Adaptive bitrate (inspired by India eSanjeevani):**

| Bandwidth    | Mode          | Codec      | Quality            |
| ------------ | ------------- | ---------- | ------------------ |
| > 500 kbps   | Video + Audio | VP8 + Opus | 360p+              |
| 100–500 kbps | Video + Audio | VP8 + Opus | 180p (low quality) |
| 30–100 kbps  | Audio only    | Opus       | Mono 16kHz         |
| < 30 kbps    | Phone bridge  | PSTN       | Standard telephony |

---

## 13. FCHV-Mediated Telemedicine Flow (Added in v2.0)

**Scenario:** FCHV visits patient at home, connects patient to remote specialist via FCHV tablet.

1. FCHV opens app → selects patient → requests telemedicine session
2. System checks specialist availability and patient consent
3. FCHV device serves as the patient's video/audio endpoint
4. Specialist joins from clinic or remote location
5. FCHV translates (Nepali dialect ↔ standard Nepali) if needed
6. Specialist creates clinical notes during session
7. Session outcome linked to patient's encounter record
8. FCHV confirms patient understanding of follow-up instructions

**Access control:** FCHV has session-scoped access only. Cannot view historical records. Session access expires when call ends.

---

## 14. Store-and-Forward Telemedicine (Added in v2.0)

For non-urgent consultations where real-time video is not feasible:

1. Patient or FCHV captures: photos (skin condition, wound), voice description, vital signs
2. Data packaged as an asynchronous consultation request
3. Stored locally if offline, synced when connectivity available
4. Specialist reviews at next available slot (SLA: within 24 hours)
5. Specialist responds with text/voice recommendation
6. Patient notified when response is available
7. Follow-up appointment scheduled if needed

**Use cases:** Dermatology, wound care, medication review, chronic disease check-in.

---

## 15. End-to-End Encryption for Telemedicine (Added in v2.0)

| Layer                     | Encryption                   | Notes                             |
| ------------------------- | ---------------------------- | --------------------------------- |
| Signaling (session setup) | TLS 1.3                      | Jitsi OWASP-compliant             |
| Media (audio/video)       | SRTP + OWASP                 | Jitsi default, E2EE optional      |
| Recordings                | AES-256-GCM at rest          | Stored in Ceph, per-patient key   |
| Transcripts               | AES-256 at rest              | Stored separately from recordings |
| Store-and-forward         | AES-256 in transit + at rest | Encrypted before local storage    |

**Recording consent:** Recording NEVER starts without explicit patient consent. Consent state is persisted and audited. Patient can withdraw consent mid-session → recording stops immediately, partial recording retained with consent withdrawal timestamp.

---

## 16. Session Reconnection and Resilience (Added in v2.0)

**Network interruption handling:**

1. If participant drops → 30-second auto-reconnect window
2. Other participant sees "Reconnecting..." indicator
3. If reconnection succeeds within 30s → session continues seamlessly
4. If reconnection fails → session status changes to `dropped`
5. Either participant can re-join via session link within 15 minutes
6. After 15 minutes → session auto-ends with `failed` status
7. If session was being recorded → recording stops at drop, resumes on reconnect as new segment

**Global precedent:** India eSanjeevani handles 1.5M+ sessions monthly with similar reconnection strategy.
