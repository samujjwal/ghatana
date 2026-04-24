# Audio-Video Media Privacy and Retention Policy

**Status:** Authoritative  
**Last Updated:** 2026-04-24  
**Owner:** Audio-Video Team  
**Slack:** #av-platform  
**Compliance Scope:** GDPR, CCPA, SOC2 Type II  
**Review Cadence:** Quarterly + on any platform change

---

## 1. Purpose

This document defines the privacy protections, data retention schedule, redaction
requirements, and consent requirements for all media data (audio, video, image,
transcripts, embeddings) processed by the Audio-Video platform.

All engineers working on the Audio-Video platform must follow this policy.
Violations must be reported to `#av-platform` and `#platform-security` immediately.

---

## 2. Media Data Classification

| Data Type | Classification | Contains PII? | Retention Default | Notes |
|-----------|--------------|:---:|---|---|
| Raw audio stream (in-flight) | T1 Confidential | Possible | Not persisted | STT pipeline only; discarded after transcription |
| Raw video frames (in-flight) | T1 Confidential | Possible | Not persisted | Vision pipeline only; discarded after analysis |
| Transcript text | T1 Confidential | Yes | 30 days | May contain names, addresses, medical info |
| Speaker embeddings / voice prints | T0 Critical | Yes (biometric) | Never beyond consent window | Biometric under GDPR Art. 9 |
| Vision analysis results (objects, faces) | T1 Confidential | Yes (faces) | 30 days | Face data requires explicit consent |
| Session metadata (duration, device, language) | T2 Internal | Indirect | 90 days | Used for billing and analytics |
| Anonymised transcripts (PII redacted) | T3 Internal | No | 365 days | Post-redaction analytics |
| Audit events (start/stop/consent/revocation) | T2 Internal | Indirect | 365 days (compliance) | SOC2 audit trail |

---

## 3. Consent Requirements

### 3.1 Mandatory Consent

All of the following **require explicit, informed, revocable consent** before
any media processing begins:

- Voice/audio capture and STT transcription
- Video capture and vision analysis
- Speaker identification / biometric embedding generation
- Face detection or recognition

### 3.2 Consent Enforcement

- Consent is stored in `ConsentStore` and checked by `ConsentGuard` at session start.
- A session that attempts to start without a valid `AUDIO_CAPTURE` or `VIDEO_CAPTURE`
  consent record is rejected with `403 Forbidden`.
- Consent records carry: `tenantId`, `userId`, `consentType`, `grantedAtMs`,
  `revokedAtMs` (null if active), `dataTypes[]`, `processingPurposes[]`.
- Consent is **not** inherited across sessions or tenants.

### 3.3 Consent Revocation

- Revocation takes effect **immediately**: in-progress sessions are terminated.
- All media-derived data (transcripts, embeddings, analysis results) for the
  revoked consent are scheduled for deletion within **72 hours**.
- A `CONSENT_REVOKED` audit event is emitted synchronously before session termination.
- Tests: `ConsentRevocationTest` in `products/audio-video/libs/java/common`

---

## 4. Retention Schedule

| Data Type | Retention Window | Enforcement Mechanism |
|-----------|----------------|----------------------|
| Raw audio/video (in-flight) | 0 — never written to disk | `StreamingSession` lifecycle |
| Transcript text | 30 days from session end | `MediaRetentionService.purgeExpiredTranscripts()` |
| Speaker embeddings | Until consent revoked, max 30 days | `MediaRetentionService.purgeRevokedEmbeddings()` |
| Vision analysis (faces) | 30 days from session end | `MediaRetentionService.purgeExpiredAnalysis()` |
| Session metadata | 90 days from session end | `MediaRetentionService.purgeExpiredMetadata()` |
| Anonymised transcripts | 365 days | `MediaRetentionService.purgeExpiredAnonymised()` |
| Audit events | 365 days | `AuditRetentionJob` (platform-level) |

### 4.1 Retention Enforcement

- `MediaRetentionService` runs a scheduled purge job every 24 hours.
- Purge operations are idempotent and logged with `tenantId`, `sessionId`, `dataType`,
  `purgedCount`, `correlationId`.
- Purge failures are retried up to 3 times with exponential backoff before alerting.
- Retention tests: `MediaRetentionPolicyTest` asserts that no records survive past
  their expiry window.

---

## 5. PII Redaction Requirements

### 5.1 Transcript Redaction

Transcript text **must be redacted** before storage in the anonymised tier.
Redaction must cover:

- Full names (via NLP NER)
- Email addresses (regex)
- Phone numbers (regex, international formats)
- Physical addresses
- Financial information (card numbers, IBANs)
- Health/medical information (NER)
- Government IDs (regex)

Redaction is performed by `TranscriptRedactionService` using the platform's
PII redaction pipeline. Redacted tokens are replaced with `[REDACTED_<TYPE>]`
placeholder strings. Original unredacted transcripts are stored in the T1 tier
and subject to the 30-day retention window.

### 5.2 Vision / Face Redaction

Face detection results are never stored in plain form. Face bounding boxes are
anonymised by replacing with a salted, per-tenant pseudonym (`FACE_<hash>`).
Face embeddings (biometric) are stored only in the T0 tier with AES-256 encryption
at rest, scoped to the consenting user.

### 5.3 Metadata Stripping

Audio metadata (ID3 tags, device identifiers, GPS coordinates) is stripped from
all audio buffers before processing. The stripping is applied by `AudioInputSanitiser`
at the session ingestion boundary.

---

## 6. Data Minimisation

- Raw audio and video are **never persisted to disk**. They exist only in the
  in-process stream pipeline buffer (`AudioChunkBuffer`, `VideoFrameBuffer`).
- Transcripts are generated from audio and the audio buffer is discarded
  immediately after the STT result is produced.
- Embeddings are generated only when explicitly requested by a consented feature
  (e.g., speaker ID). Embedding generation must be gated by the `SPEAKER_ID`
  consent type.

---

## 7. Cross-Border Data Transfer

- Audio/video media data must not be transmitted to external STT, TTS, or vision
  model endpoints without verifying that the endpoint is within the consented
  data residency region.
- The `AiInferenceClient` checks the configured `dataResidencyRegion` against
  the session's `consentRecord.allowedRegions[]` before dispatching.
- Transfer to disallowed regions results in `403 REGION_NOT_PERMITTED` and a
  `COMPLIANCE_REGION_VIOLATION` audit event.

---

## 8. Security at Rest and in Transit

| Layer | Requirement |
|-------|------------|
| Transport | TLS 1.3 for all gRPC streams and HTTP connections |
| Transcript storage | AES-256 encryption at rest, per-tenant key |
| Biometric embeddings | AES-256 at rest + additional HMAC integrity check |
| Audit events | Write-once append log; no update/delete permitted |
| Session tokens | Scoped, short-lived (1 hour), bound to `tenantId` + `userId` |

---

## 9. Incident Response

If a privacy violation is detected (e.g., transcript data leaked cross-tenant,
biometric data retained beyond window):

1. Immediately alert `#av-platform` and `#platform-security`.
2. Emit a `PRIVACY_INCIDENT` audit event with `severity: CRITICAL`.
3. Suspend the affected session processing pipeline.
4. Initiate a privacy impact assessment within 24 hours.
5. Notify affected users within 72 hours per GDPR Art. 33/34 if the breach
   involves personal data.

---

## 10. Compliance Verification

| Check | Frequency | Method |
|-------|----------|--------|
| Consent gate enforced | Every PR | `ConsentGuard` unit tests |
| Retention window respected | Every PR | `MediaRetentionPolicyTest` |
| PII not stored in plaintext | Every PR | `PiiLeakagePreventionTest` |
| Cross-tenant isolation | Every PR | `TenantIsolationTest` |
| Biometric expiry enforced | Weekly CI | `BiometricRetentionComplianceTest` |
| Audit trail completeness | Quarterly | Manual SOC2 audit review |

---

## 11. Related Documents

- `docs/SECRETS_CLASSIFICATION.md` — secrets and key management
- `docs/architecture/PROPAGATION_CONTRACTS.md` — tenant/audit propagation
- `products/audio-video/libs/java/common/src/test/java/com/ghatana/media/` — compliance tests
- GDPR Article 9 (biometric data), Article 17 (right to erasure), Article 33/34 (breach notification)
