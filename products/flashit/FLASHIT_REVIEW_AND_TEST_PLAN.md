# FlashIt — Comprehensive Review, Flow Report & Test Plan

**Version:** 2.0  
**Date:** 2026-02-22  
**Status:** Deep Review Complete  
**Scope:** Plan review, gap analysis, end-to-end flows, and full test plan

---

## TABLE OF CONTENTS

1. [Completion Plan Review & Gap Analysis](#1-completion-plan-review--gap-analysis)
2. [End-to-End Flow Reports](#2-end-to-end-flow-reports)
   - [2.1 User Flows](#21-user-flows)
   - [2.2 Admin Flows](#22-admin-flows)
   - [2.3 Operator Flows](#23-operator-flows)
3. [Comprehensive Test Plan](#3-comprehensive-test-plan)

---

## 1. COMPLETION PLAN REVIEW & GAP ANALYSIS

### 1.1 Plan Accuracy Assessment

The [FLASHIT_COMPLETION_PLAN.md](FLASHIT_COMPLETION_PLAN.md) correctly identifies the top-level problem (missing Java Agent) but **misses 20+ additional gaps** found during deep code review. Below is a detailed comparison.

### 1.2 Issues Correctly Identified in the Plan ✅

| # | Issue | Plan Phase | Assessment |
|:--|:------|:-----------|:-----------|
| 1 | Missing Java Agent Service | Phase 1 | ✅ Correct — `backend/agent/` has zero source files |
| 2 | Architecture Violation (AI in Node.js) | Phase 2 | ✅ Correct — `backend/modules/intelligence/reflection-service.ts` duplicates Java Agent logic in Node.js with direct OpenAI calls |
| 3 | Mixed AWS SDK versions | Phase 2 | ⚠️ Partially Fixed — `IMPLEMENTATION_UPDATE.md` says v2 was migrated to v3, but should be verified |
| 4 | Missing README/documentation | Phase 6 | ✅ Correct |
| 5 | Error handling improvements | Phase 3 | ✅ Correct |
| 6 | Performance/caching | Phase 4 | ✅ Correct |
| 7 | Security hardening | Phase 5 | ✅ Correct |

### 1.3 Critical Gaps MISSING from the Plan 🔴

These issues exist in the codebase but are **not addressed** anywhere in the completion plan:

#### A. Architecture & Integration Gaps

| # | Gap | Severity | Details |
|:--|:----|:---------|:--------|
| A1 | **Templates route not registered** | MEDIUM | `routes/templates.ts` (406 lines) is dead code — never imported/registered in `server.ts`. Full template CRUD + rendering exists but is unreachable. |
| A2 | **Backend modules not integrated** | HIGH | `backend/modules/` contains 4 standalone services (collaboration WebSocket, compliance, intelligence, notification) using BullMQ/Socket.IO. These are **completely disconnected** from the Fastify gateway. No startup scripts, no service orchestration, no inter-process communication configured. |
| A3 | **Notification module has no routes** | HIGH | The notification service (`backend/modules/notification/`) handles multi-channel notifications (in-app, email, push, SMS) but there are **no API routes** for listing, dismissing, or managing in-app notifications. The `PushSubscription` model exists but no `Notification` model in the schema. |
| A4 | **No scheduled/cron jobs** | HIGH | 6+ services reference required cron jobs that don't exist: expired refresh token cleanup, stale rate limit cleanup, expired session cleanup, password reset token cleanup, audit log retention enforcement, scheduled data deletion processing. |
| A5 | **Prometheus metrics endpoint missing** | MEDIUM | `monitoring/prometheus.yml` scrapes `flashit-api:8000/metrics` but **no `/metrics` endpoint exists** in `server.ts`. No `prom-client` library is installed. The monitoring middleware stores metrics in-memory only. |

#### B. RBAC & Security Gaps

| # | Gap | Severity | Details |
|:--|:----|:---------|:--------|
| B1 | **No system-level RBAC** | CRITICAL | The `User` model has **no `role` field**. There is no concept of platform admin, operator, or support staff. Two admin endpoints exist but are unprotected: `GET /api/analytics/admin/queue-status` (has auth but no role check), `POST /api/health/circuits/:name/reset` (completely open). |
| B2 | **Inconsistent role naming** | MEDIUM | DB schema uses uppercase (`OWNER`, `EDITOR`, `ADMIN`) for sphere access. Collaboration module uses lowercase (`viewer`, `commenter`, `editor`, `admin`). No shared enum. |
| B3 | **SSO/SAML advertised but not implemented** | HIGH | Teams tier ($29/user/mo) advertises SSO/SAML integration — zero implementation exists. |
| B4 | **API key system advertised but not implemented** | HIGH | Teams tier advertises "Full API access" — no API key generation, management, or authentication exists. |

#### C. Billing & Limit Enforcement Gaps

| # | Gap | Severity | Details |
|:--|:----|:---------|:--------|
| C1 | **Collaborator limit not enforced** | HIGH | `TIER_LIMITS.free.collaborators = 0` but `collaboration.ts` routes never call a collaborator limit check. Free users can share spheres without restriction. |
| C2 | **Transcription hour limit not enforced** | HIGH | `TranscriptionUsage` model tracks hours, `TIER_LIMITS` defines 10/50/200 hour limits, but no `checkTranscriptionLimit()` function exists. Only computed in `getUsageSummary()`, never enforced before transcription. |
| C3 | **AI insight usage not incremented** | HIGH | `POST /api/reflection/insights` calls `checkAIInsightLimit()` to verify limit, but after generating insights, **never writes an `AIInsight` record**. Comment in code: `// TODO: Increment AI insight usage count`. Limit check always passes since count stays at 0. |
| C4 | **Audit log retention not enforced** | MEDIUM | Tier limits define 30/365/1095-day retention, but no cleanup job or retention policy is implemented. Audit logs grow indefinitely. |
| C5 | **Billing success/cancel URLs broken** | MEDIUM | Stripe checkout creates URLs like `/settings/billing?success=true` and `/settings/billing?canceled=true`. **No such page exists** in either web or mobile client. |

#### D. Database Schema Gaps

| # | Gap | Severity | Details |
|:--|:----|:---------|:--------|
| D1 | **Missing `Comment` model** | MEDIUM | Collaboration routes handle comments but no Prisma model exists — likely raw SQL or in-memory. |
| D2 | **Missing `Reaction` model** | MEDIUM | Reaction endpoints exist but no model backing. |
| D3 | **Missing `Follow` model** | MEDIUM | Follow user feature has routes but no persistence model. |
| D4 | **Missing `Invitation` model** | MEDIUM | Sphere invitation acceptance flow has no model. |
| D5 | **Missing `Notification` model** | HIGH | In-app notifications have no persistence — users can't retrieve notification history. |
| D6 | **Missing `Template` model** | LOW | Template engine uses in-memory data only — templates don't persist across restarts. |
| D7 | **Missing `Report` model** | LOW | Async report generation exists but job tracking is in-memory. |
| D8 | **Missing `DataExportRequest` / `DeletionRequest` models** | MEDIUM | GDPR export/deletion flows have endpoints but no proper job tracking tables. |

#### E. Frontend Gaps

| # | Gap | Severity | Details |
|:--|:----|:---------|:--------|
| E1 | **Web client missing 7+ page routes** | HIGH | Backend supports search, analytics, billing, privacy, collaboration, memory-expansion, reflection, settings — **no web pages exist for any of these**. Only 6 pages: Login, Register, Dashboard, Capture, Moments, Spheres. |
| E2 | **Mobile client missing 7+ screens** | HIGH | Same gap as web — no screens for Search, Analytics, Billing, Privacy, Collaboration, Memory Expansion, Reflection. |
| E3 | **`LanguageInsightsPage` not routed (web)** | MEDIUM | Page component exists but no route in `App.tsx` points to it. |
| E4 | **`NotificationSettingsScreen` not navigable (mobile)** | MEDIUM | Screen exists but not wired into navigation. |
| E5 | **Web search uses deprecated endpoint** | MEDIUM | Web `useSearchMoments` hook may still use simple text search instead of hybrid AI search API. |

#### F. Features in Code but NOT in Plan

| # | Feature | Status | Notes |
|:--|:--------|:-------|:------|
| F1 | **Adoption loops system** | Registered, working | Onboarding checklist, engagement nudges, feature suggestions — not mentioned in plan. |
| F2 | **Progressive/chunked uploads** | Registered, working | Multipart upload with progress — not mentioned in plan. |
| F3 | **Moment Links / Temporal Arcs** | Registered, working | 9 link types between moments, graph visualization — not mentioned in plan. |
| F4 | **Memory Expansion** | Registered, partially working | Summarize, themes, patterns, connections — depends on Java Agent. |
| F5 | **Visual Search** | Code exists | Image-based search via OpenAI Vision. |
| F6 | **Activity Feed** | Service exists | Activity feed generation — route exists in collaboration. |
| F7 | **CRDT collaborative editing (web)** | Code exists | Real-time text editing engine. |
| F8 | **Language Evolution / Insights** | Routes exist | Language pattern analysis over time. |
| F9 | **Report generation (PDF/CSV)** | Routes exist | Async report generation with job tracking. |
| F10 | **Compliance module** | Standalone service | GDPR/CCPA/ISO27001 checking — not integrated or mentioned. |

### 1.4 Plan Phase Corrections

#### Phase 1 — Java Agent (Correct but Incomplete)

**Additional tasks needed:**
- Implement ALL 17 expected endpoints (plan only lists 4 services; missing NLP, classification)
- Implement `/health` and `/ready` endpoints for service discovery
- Wire `classification/classify` and `classification/suggest-spheres` endpoints
- Wire `embedding/generate`, `embedding/batch`, `embedding/search` endpoints
- Wire `nlp/extract-entities`, `nlp/analyze-sentiment`, `nlp/detect-mood` endpoints
- Add graceful degradation in Gateway when Agent is unavailable (currently returns 502)

#### Phase 2 — Architecture Cleanup (Missing Items)

**Additional tasks needed:**
- Register `templates.ts` route in `server.ts` or remove dead code
- Remove duplicate legacy `auth.ts` file
- Integrate or formalize standalone backend modules (collaboration WS, compliance, notification)
- Add `Notification`, `Comment`, `Reaction`, `Follow`, `Invitation` Prisma models + migrations
- Implement all scheduled cleanup jobs (6+ cron tasks)
- Normalize role naming (uppercase/lowercase inconsistency)
- Fix AI insight usage tracking (write to `AIInsight` table after generation)
- Implement `checkTranscriptionLimit()` and enforce before transcription
- Enforce collaborator limits in collaboration routes

#### Phase 3 — Enhanced UX (Missing Items)

**Additional tasks needed:**
- Add missing web pages: Search, Analytics, Billing, Privacy, Collaboration, Memory Expansion, Reflection, Settings
- Add missing mobile screens: Search, Analytics, Billing, Privacy, Collaboration, Memory Expansion, Reflection
- Wire `LanguageInsightsPage` into web routing
- Wire `NotificationSettingsScreen` into mobile navigation
- Fix web search to use hybrid AI endpoint
- Add `/settings/billing` page (Stripe redirect target)

#### Phase 5 — Security Hardening (Missing Items)

**Additional tasks needed:**
- Add system-level `role` field to `User` model (ADMIN, OPERATOR, USER)
- Create admin middleware for role-based route protection
- Protect existing admin endpoints (analytics queue status, circuit breaker reset)
- Build admin panel routes (user management, content moderation, billing admin, system config)
- Implement SSO/SAML for Teams tier (or remove from pricing)
- Implement API key generation/management for Teams tier (or remove from pricing)

#### New Phase Needed — Monitoring & Observability

The plan mentions monitoring in Phase 4 but doesn't address:
- Install `prom-client` and expose `/metrics` endpoint
- Add Java Agent to Prometheus scrape config
- Create Grafana dashboards (currently scaffolded but empty)
- Set up alerting rules
- Implement distributed tracing (OpenTelemetry)
- Replace in-memory metrics aggregator with proper Prometheus counters/histograms

### 1.5 Recommended Updated Phase Structure

| Phase | Duration | Priority | Additions vs. Original Plan |
|:------|:---------|:---------|:----------------------------|
| **Phase 1**: Java Agent Implementation | Weeks 1-3 | CRITICAL | Add all 17 endpoints + graceful degradation |
| **Phase 2**: Architecture & Schema Cleanup | Weeks 3-5 | HIGH | Add schema models, cron jobs, route registration, module integration, limit enforcement. **Extended to 2 weeks** (was 1). |
| **Phase 3**: RBAC & Admin System | Week 5-6 | HIGH | **NEW PHASE** — system roles, admin middleware, admin panel, protected endpoints |
| **Phase 4**: Frontend Completion | Weeks 6-8 | HIGH | Add all missing pages/screens, fix routing, wire existing but unrouted features |
| **Phase 5**: Monitoring & Observability | Week 8-9 | MEDIUM | Prometheus, Grafana, alerting, distributed tracing |
| **Phase 6**: Performance & Caching | Weeks 9-10 | MEDIUM | Same as original Phase 4 |
| **Phase 7**: Security Hardening | Weeks 10-11 | MEDIUM | Same as original Phase 5 + SSO/SAML + API keys |
| **Phase 8**: Documentation & Deployment | Weeks 11-12 | LOW | Same as original Phase 6 |

**Revised estimate: 10-12 weeks** (original was 8-10).

---

## 2. END-TO-END FLOW REPORTS

### 2.1 User Flows

#### Flow U1: Registration & Onboarding

```
┌─────────────────────────────────────────────────────────────────────┐
│ REGISTRATION & ONBOARDING FLOW                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Client: Mobile/Web]                                               │
│       │                                                             │
│       ├── 1. POST /auth/register                                    │
│       │    Body: { email, password, name }                          │
│       │    ├── Validate: email format, password strength             │
│       │    ├── Check: email uniqueness                               │
│       │    ├── Hash: bcrypt(password, 12 rounds)                     │
│       │    ├── Create: User record (tier: FREE)                      │
│       │    ├── Create: Default "Personal" sphere (type: PERSONAL)    │
│       │    ├── Create: SphereAccess (role: OWNER)                    │
│       │    ├── Create: UserTierSettings (free defaults)              │
│       │    ├── Emit: AuditEvent (USER_REGISTERED)                    │
│       │    └── Return: { user, accessToken, refreshToken }           │
│       │                                                             │
│       ├── 2. GET /api/adoption/onboarding                           │
│       │    ├── Returns: checklist items, completion %                │
│       │    └── Items: create_first_moment, try_voice_capture,        │
│       │             explore_spheres, setup_notifications, etc.       │
│       │                                                             │
│       ├── 3. GET /api/adoption/suggestions                          │
│       │    └── Returns: personalized feature suggestions             │
│       │                                                             │
│       └── 4. POST /api/adoption/suggestions/interactions            │
│            Body: { suggestionId, action: "dismissed"|"accepted" }    │
│                                                                     │
│  [State: User is authenticated with JWT, free tier, 1 sphere]       │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No email verification flow (user activates immediately)           │
│ • No welcome email sent (email service exists but not triggered)    │
│ • Onboarding screen not wired in mobile navigation                  │
│ • No admin notification of new user registration                    │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U2: Authentication (Login, 2FA, Session Management)

```
┌─────────────────────────────────────────────────────────────────────┐
│ AUTHENTICATION FLOW                                                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Login - Standard]                                                 │
│       │                                                             │
│       ├── 1. POST /auth/login                                       │
│       │    Body: { email, password }                                │
│       │    ├── Check: account lockout (5 fails → 30min lock)        │
│       │    ├── Verify: bcrypt compare                                │
│       │    ├── If fail: increment failedLoginAttempts                │
│       │    ├── If 2FA enabled: return { requires2FA: true, tempToken}│
│       │    ├── If success: clearFailedAttempts                       │
│       │    ├── Create: UserSession record (device info, IP, UA)      │
│       │    ├── Create: RefreshToken record                           │
│       │    ├── Emit: AuditEvent (USER_LOGGED_IN)                     │
│       │    └── Return: { user, accessToken (15min), refreshToken }   │
│       │                                                             │
│  [Login - With 2FA]                                                 │
│       ├── 2. POST /auth/2fa/verify                                  │
│       │    Body: { tempToken, totpCode }                            │
│       │    ├── Verify: TOTP code against secret                      │
│       │    └── Return: { accessToken, refreshToken }                 │
│       │                                                             │
│  [Token Refresh]                                                    │
│       ├── 3. POST /auth/refresh                                     │
│       │    Body: { refreshToken }                                   │
│       │    ├── Verify: token hash exists and not revoked             │
│       │    ├── Rotate: new refreshToken, revoke old                  │
│       │    └── Return: { accessToken, refreshToken }                 │
│       │                                                             │
│  [Session Management]                                               │
│       ├── 4. GET /auth/sessions                                     │
│       │    └── Return: active sessions (device, IP, location)        │
│       │                                                             │
│       └── 5. DELETE /auth/sessions/:id                              │
│            ├── Revoke: associated refresh tokens                     │
│            └── Mark session inactive                                 │
│                                                                     │
│  [2FA Setup]                                                        │
│       ├── POST /auth/2fa/setup → { secret, qrCodeUrl }             │
│       └── POST /auth/2fa/enable → verify TOTP, enable 2FA           │
│                                                                     │
│  [Password Reset]                                                   │
│       ├── POST /auth/password-reset → send reset email              │
│       └── POST /auth/password-reset/confirm → verify token, update  │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Expired session cleanup: referenced as "daily cron" but NOT       │
│   implemented. Sessions accumulate indefinitely.                    │
│ • Expired refresh token cleanup: same — no cron job.                │
│ • Expired password reset token cleanup: same — no cron job.         │
│ • Rate limit stale entry cleanup: same — no cron job.               │
│ • No "logout all devices" endpoint.                                 │
│ • Biometric auth service exists on mobile but no backend support.   │
│ • No OAuth2 social login (Google, Apple, Facebook).                 │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U3: Moment Capture (Text)

```
┌─────────────────────────────────────────────────────────────────────┐
│ TEXT MOMENT CAPTURE FLOW                                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Client: UnifiedCaptureScreen / CapturePage]                       │
│       │                                                             │
│       ├── 1. POST /api/moments                                      │
│       │    Headers: Authorization: Bearer <token>                   │
│       │    Body: {                                                  │
│       │      contentText: "Today I realized...",                    │
│       │      contentType: "TEXT",                                   │
│       │      sphereId: "sphere-uuid",                               │
│       │      tags: ["reflection", "growth"],                        │
│       │      metadata: { location: {...}, weather: "sunny" }        │
│       │    }                                                        │
│       │    ├── Auth: verify JWT, extract userId                      │
│       │    ├── Limit: checkMomentLimit(userId) → free: 100/mo       │
│       │    ├── Verify: sphere access (OWNER or EDITOR)               │
│       │    ├── AI: classifySphere(text) → auto-assign if no sphere   │
│       │    ├── Create: Moment record in Prisma                       │
│       │    ├── Emit: AuditEvent (MOMENT_CREATED)                     │
│       │    └── Return: { moment } with id, timestamps                │
│       │                                                             │
│  [Background: AI Processing - SHOULD happen but partially broken]   │
│       │                                                             │
│       ├── 2. (Implicit) Embedding generation                        │
│       │    ├── Java Agent: POST /api/v1/agents/embedding/generate   │
│       │    ├── Store: MomentEmbedding record                         │
│       │    └── STATUS: ❌ Java Agent not implemented                 │
│       │                                                             │
│       ├── 3. (Implicit) NLP Processing                              │
│       │    ├── Java Agent: POST /api/v1/agents/nlp/extract-entities │
│       │    ├── Java Agent: POST /api/v1/agents/nlp/analyze-sentiment│
│       │    ├── Update: moment.entities, moment.sentimentScore        │
│       │    └── STATUS: ❌ Java Agent not implemented                 │
│       │                                                             │
│  [Offline Path - Mobile Only]                                       │
│       └── If offline:                                               │
│            ├── Store in SQLite (local DB)                            │
│            ├── Queue in offlineQueue                                 │
│            └── Sync when connectivity restored (adaptiveSync)        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Auto-embedding/NLP after moment creation depends on Java Agent    │
│ • No background processing trigger for text moments in Gateway      │
│ • Sphere classification calls Java Agent (broken)                   │
│ • Tags are sent to moment but never auto-suggested from content     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U4: Moment Capture (Voice/Audio)

```
┌─────────────────────────────────────────────────────────────────────┐
│ VOICE MOMENT CAPTURE FLOW                                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Client: VoiceRecorderScreen]                                      │
│       │                                                             │
│       ├── 1. Record audio (Expo AV / Web MediaRecorder)             │
│       │    ├── Waveform visualization                                │
│       │    ├── Audio level monitoring                                 │
│       │    └── Compress if needed (mediaCompressionService)           │
│       │                                                             │
│       ├── 2. POST /api/moments                                      │
│       │    Body: { contentText: "", contentType: "VOICE", sphereId } │
│       │    └── Return: { moment: { id } }                            │
│       │                                                             │
│       ├── 3. POST /api/upload/presigned-url                         │
│       │    Body: { momentId, fileName, mimeType, fileSize }         │
│       │    ├── Verify: sphere access                                 │
│       │    ├── Limit: checkStorageLimit(userId)                      │
│       │    ├── Generate: S3 presigned PUT URL (15min expiry)         │
│       │    ├── Create: MediaReference (status: PENDING)              │
│       │    ├── Create: MomentMedia link                              │
│       │    └── Return: { uploadUrl, mediaReferenceId }               │
│       │                                                             │
│       ├── 4. PUT <S3 presigned URL>                                 │
│       │    ├── Direct upload to S3/MinIO                              │
│       │    ├── Progress tracked by uploadProgressService              │
│       │    └── bandwidthService adapts chunk size                     │
│       │                                                             │
│       ├── 5. POST /api/upload/complete                              │
│       │    Body: { mediaReferenceId, momentId }                     │
│       │    ├── Update: MediaReference (status: COMPLETED)            │
│       │    ├── Trigger: enqueueTranscription(momentId, s3Key, ...)  │
│       │    ├── Emit: AuditEvent (MEDIA_UPLOADED)                     │
│       │    └── Return: { success: true }                             │
│       │                                                             │
│  [Background: Transcription Pipeline]                               │
│       │                                                             │
│       ├── 6a. [Node.js Path - Current]                              │
│       │    ├── WhisperService: download from S3, call OpenAI Whisper │
│       │    ├── Update: moment.contentTranscript                      │
│       │    ├── Track: TranscriptionUsage record                      │
│       │    └── STATUS: ⚠️ Working but should be in Java Agent       │
│       │                                                             │
│       ├── 6b. [Java Agent Path - Target]                            │
│       │    ├── POST /api/v1/agents/transcription/transcribe         │
│       │    └── STATUS: ❌ Not implemented                            │
│       │                                                             │
│  [Progressive Upload Path - Large Files]                            │
│       └── Alternative for files > threshold:                        │
│            ├── POST /api/progressive/init → { uploadId }            │
│            ├── POST /api/progressive/chunk (loop) → { partNumber }  │
│            ├── POST /api/progressive/complete → assemble             │
│            └── GET /api/progressive/status/:id → progress            │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Transcription hour limit NOT checked before enqueuing             │
│ • No transcription completion notification to user                  │
│ • No retry mechanism if transcription fails                         │
│ • Progressive upload exists but mobile client may not use it        │
│ • backgroundUploadService (mobile) may not handle app kill well     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U5: Moment Capture (Image)

```
┌─────────────────────────────────────────────────────────────────────┐
│ IMAGE MOMENT CAPTURE FLOW                                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Client: ImageCaptureScreen / ImageCapture component]              │
│       │                                                             │
│       ├── 1. Capture image (camera or gallery)                      │
│       │    ├── Image editing (crop, rotate, annotate)                │
│       │    ├── Compress (imageOptimization service)                   │
│       │    └── Generate thumbnail                                    │
│       │                                                             │
│       ├── 2. POST /api/moments                                      │
│       │    Body: { contentType: "IMAGE", sphereId }                 │
│       │                                                             │
│       ├── 3. POST /api/upload/presigned-url                         │
│       │                                                             │
│       ├── 4. PUT <S3 URL> (upload image)                            │
│       │                                                             │
│       ├── 5. POST /api/upload/complete                              │
│       │    (NO transcription triggered for images)                  │
│       │                                                             │
│  [Background: AI Processing - NOT IMPLEMENTED]                      │
│       ├── 6. Visual analysis (OpenAI Vision) → extract labels        │
│       ├── 7. OCR text extraction → store as contentTranscript        │
│       └── 8. Embedding generation                                    │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No automatic AI processing on image moments                       │
│ • Visual search service exists but not triggered on upload          │
│ • No OCR for text in images                                         │
│ • No thumbnail generation on server-side                            │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U6: Moment Capture (Video)

```
┌─────────────────────────────────────────────────────────────────────┐
│ VIDEO MOMENT CAPTURE FLOW                                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Client: VideoRecorderScreen]                                      │
│       │                                                             │
│       ├── 1. Record video (camera) or select from gallery           │
│       │    ├── Video preview with controls                           │
│       │    ├── Compress (mediaCompressionService)                    │
│       │    └── Extract audio track (if needed for transcription)     │
│       │                                                             │
│       ├── 2-5. Same upload flow as Voice (moment → presign → S3 →   │
│       │        complete → trigger transcription)                     │
│       │                                                             │
│  [Background: Processing]                                           │
│       ├── Transcription of audio track (same as voice flow)         │
│       ├── Video thumbnail extraction → NOT IMPLEMENTED              │
│       └── Scene detection / visual analysis → NOT IMPLEMENTED        │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No server-side video thumbnail extraction                         │
│ • No video-specific metadata extraction (duration, resolution)      │
│ • Large video uploads may exceed 50MB multipart limit               │
│ • Progressive upload may be needed but not auto-selected            │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U7: Search & Discovery

```
┌─────────────────────────────────────────────────────────────────────┐
│ SEARCH & DISCOVERY FLOW                                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Search Types Available]                                           │
│       │                                                             │
│       ├── 1. POST /api/search                                       │
│       │    Body: { query, type, sphereId?, filters? }               │
│       │    Types:                                                   │
│       │    ├── "text" → PostgreSQL full-text search                  │
│       │    ├── "semantic" → embedding similarity (Java Agent)        │
│       │    ├── "hybrid" → combined text + semantic ranking           │
│       │    └── "similar" → find similar moments by ID                │
│       │                                                             │
│       ├── 2. GET /api/search/suggestions                            │
│       │    └── Return: search suggestions based on history           │
│       │                                                             │
│       ├── 3. POST /api/search/similar                               │
│       │    Body: { momentId }                                       │
│       │    └── Find semantically similar moments                     │
│       │                                                             │
│       ├── 4. POST /api/search/generate-embedding                    │
│       │    └── Generate embedding for a query string                 │
│       │                                                             │
│       └── 5. POST /api/search/generate-reflection                   │
│            └── Generate AI reflection on search results              │
│                                                                     │
│  [Mobile Search Path]                                               │
│       ├── MomentsScreen: hybrid search integrated                    │
│       └── Filters: sphere, date range, content type, tags            │
│                                                                     │
│  [Web Search Path]                                                  │
│       ├── MomentsPage: basic search only                             │
│       ├── SearchFilters component exists but may not be wired        │
│       └── SavedSearches component exists                             │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Semantic search depends on Java Agent embeddings → broken          │
│ • No dedicated Search page on web or mobile                          │
│ • Web client may use deprecated simple search                        │
│ • Visual search (image-based) exists as service but no route        │
│ • Faceted search service exists but not wired to main route          │
│ • No search analytics/tracking                                       │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U8: Sphere Management & Collaboration

```
┌─────────────────────────────────────────────────────────────────────┐
│ SPHERE MANAGEMENT & COLLABORATION FLOW                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Sphere CRUD]                                                      │
│       ├── POST /api/spheres → create new sphere                     │
│       │    ├── Limit: checkSphereLimit(userId) → free: 3 spheres    │
│       │    ├── Types: PERSONAL, SHARED, WORK, FAMILY, PROJECT, CUSTOM│
│       │    ├── Visibility: PRIVATE, INVITE_ONLY, LINK_SHARED, PUBLIC │
│       │    └── Auto-create SphereAccess (OWNER)                      │
│       │                                                             │
│       ├── GET /api/spheres → list user's spheres (owned + shared)   │
│       ├── GET /api/spheres/:id → sphere details with access info     │
│       ├── PUT /api/spheres/:id → update (OWNER only)                │
│       └── DELETE /api/spheres/:id → soft delete (OWNER only)        │
│                                                                     │
│  [Collaboration]                                                    │
│       ├── POST /api/collaboration/spheres/share                     │
│       │    Body: { sphereId, email, role: VIEWER|EDITOR|ADMIN }     │
│       │    ├── Verify: caller is OWNER or ADMIN of sphere            │
│       │    ├── ❌ Missing: collaborator limit check (free: 0)       │
│       │    └── Create: SphereAccess record                           │
│       │                                                             │
│       ├── POST /api/collaboration/invitations/accept                │
│       │    └── Accept pending invitation (❌ no Invitation model)    │
│       │                                                             │
│       ├── POST /api/collaboration/comments                          │
│       │    └── Add comment to moment (❌ no Comment model)           │
│       │                                                             │
│       ├── POST /api/collaboration/reactions                         │
│       │    └── React to moment (❌ no Reaction model)                │
│       │                                                             │
│       └── POST /api/collaboration/follow                            │
│            └── Follow a user (❌ no Follow model)                    │
│                                                                     │
│  [Real-time Collaboration - Standalone Module]                      │
│       ├── WebSocket (Socket.IO) server on separate port              │
│       ├── Events: presence, cursor, typing, edit operations          │
│       └── ❌ NOT integrated with Fastify gateway                    │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Collaborator limit not enforced (free users can share)             │
│ • 4 collaboration features lack Prisma models                        │
│ • WebSocket collaboration server is standalone/disconnected          │
│ • No invitation email notification                                   │
│ • No UI for collaboration on either platform                         │
│ • CRDT engine (web) exists but not connected to WS server            │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U9: AI Reflection & Insights

```
┌─────────────────────────────────────────────────────────────────────┐
│ AI REFLECTION & INSIGHTS FLOW                                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [User-Triggered]                                                   │
│       ├── POST /api/reflection/insights                             │
│       │    Body: { sphereId, timeframeDays? }                       │
│       │    ├── Auth + billing limit check (free: 5/mo)               │
│       │    ├── Verify sphere access                                  │
│       │    ├── Fetch up to 50 recent moments                         │
│       │    ├── Call: Java Agent /api/v1/agents/reflection/insights   │
│       │    ├── ❌ Missing: write AIInsight record                   │
│       │    ├── ❌ Missing: increment usage counter                  │
│       │    └── Return: { insights } or 502 if agent unavailable      │
│       │                                                             │
│  [Available but No Routes]                                          │
│       ├── detectPatterns() → Java Agent /reflection/patterns         │
│       ├── findConnections() → Java Agent /reflection/connections     │
│       ├── getWeeklyReflection() → composed reflection                │
│       └── getMonthlyReflection() → composed reflection               │
│                                                                     │
│  [Intelligence Module - Parallel Implementation]                    │
│       └── reflection-service.ts (standalone BullMQ worker)           │
│            ├── Uses OpenAI directly (NOT via Java Agent)              │
│            ├── Has its own prompt templates                           │
│            ├── Implements pattern detection, connection finding       │
│            └── ❌ ARCHITECTURE VIOLATION: duplicates Java Agent work │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Java Agent not implemented → reflection always returns 502         │
│ • Only 1 of 5+ reflection operations exposed as a route             │
│ • Intelligence module duplicates logic in Node.js                    │
│ • No weekly/monthly automated reflection summaries                   │
│ • No push notification for generated insights                        │
│ • Insight counter never incremented → limit check always passes      │
│ • No UI for reflection on either platform                            │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U10: Memory Expansion

```
┌─────────────────────────────────────────────────────────────────────┐
│ MEMORY EXPANSION FLOW                                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [User-Triggered]                                                   │
│       ├── POST /api/memory-expansion                                │
│       │    Body: { momentId, expansionType }                        │
│       │    Types: "summarize", "themes", "patterns", "connections"  │
│       │    ├── Auth + check moment ownership                         │
│       │    ├── Limit: checkMemoryExpansionLimit (free: 2/mo)         │
│       │    ├── Submit as async job → return jobId                    │
│       │    └── Background: call Java Agent or AI service              │
│       │                                                             │
│       ├── GET /api/memory-expansion → list user's expansions        │
│       ├── GET /api/memory-expansion/:jobId → job status              │
│       ├── GET /api/memory-expansion/result/:id → expansion result   │
│       └── POST /api/memory-expansion/batch → multi-moment expansion  │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • Depends on Java Agent / AI service → likely broken                 │
│ • Job processing infrastructure (BullMQ?) not visible                │
│ • No UI for memory expansion on either platform                      │
│ • Web has MemoryExpansionDialog but no page/route to trigger it      │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U11: Billing & Subscription

```
┌─────────────────────────────────────────────────────────────────────┐
│ BILLING & SUBSCRIPTION FLOW                                         │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Subscription Lifecycle]                                           │
│       ├── GET /api/billing/usage → current usage summary             │
│       │    Returns: { moments, storage, transcription, insights,     │
│       │              expansions, spheres, collaborators }            │
│       │                                                             │
│       ├── GET /api/billing/limits → tier limits for user             │
│       ├── GET /api/billing/subscription → current plan details       │
│       │                                                             │
│       ├── POST /api/billing/upgrade                                 │
│       │    Body: { tier: "PRO"|"TEAMS" }                            │
│       │    ├── Create: Stripe checkout session                       │
│       │    ├── Success URL: /settings/billing?success=true           │
│       │    ├── Cancel URL: /settings/billing?canceled=true           │
│       │    └── Return: { checkoutUrl }                               │
│       │                                                             │
│       ├── POST /api/billing/downgrade                               │
│       │    └── Schedule downgrade at period end                      │
│       │                                                             │
│       └── POST /api/billing/webhook                                 │
│            ├── Verify: Stripe signature                               │
│            ├── Handle: checkout.session.completed → update tier       │
│            ├── Handle: subscription.updated → sync status             │
│            ├── Handle: subscription.deleted → downgrade to FREE       │
│            ├── Handle: invoice.payment_succeeded → log                │
│            └── Handle: invoice.payment_failed → notify user           │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No /settings/billing page on web or mobile                         │
│ • No billing settings screen in mobile navigation                    │
│ • No payment failed notification to user (notification service       │
│   exists but not integrated)                                         │
│ • No cancellation flow UI                                            │
│ • No invoice history endpoint                                        │
│ • No proration handling for mid-cycle changes                        │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U12: Privacy & Data Management (GDPR)

```
┌─────────────────────────────────────────────────────────────────────┐
│ PRIVACY & DATA MANAGEMENT FLOW                                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Data Export]                                                      │
│       ├── POST /api/privacy/export → request data export             │
│       │    ├── Auth required                                         │
│       │    ├── Format: JSON, CSV, PDF, ZIP                           │
│       │    ├── Async job (returns export ID)                         │
│       │    └── Includes: moments, spheres, media, settings           │
│       │                                                             │
│       ├── GET /api/privacy/export/:id/status → export job status     │
│       └── GET /api/privacy/export/:id/download → download export     │
│                                                                     │
│  [Data Deletion]                                                    │
│       ├── POST /api/privacy/deletion → request account deletion      │
│       │    ├── Requires confirmation                                 │
│       │    ├── Grace period before permanent deletion                 │
│       │    └── Async processing                                      │
│       └── POST /api/privacy/deletion/verify → confirm deletion       │
│                                                                     │
│  [Privacy Settings]                                                 │
│       ├── GET /api/privacy/settings → current privacy preferences    │
│       ├── PUT /api/privacy/settings → update preferences             │
│       └── POST /api/privacy/consent → record consent decisions       │
│                                                                     │
│  [Compliance Module - Standalone]                                   │
│       └── GDPR/CCPA/ISO27001 validation, backup scheduling,         │
│           security reviews (standalone BullMQ service)               │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No DataExportRequest or DeletionRequest Prisma model               │
│ • Export/deletion jobs likely stored in memory → lost on restart     │
│ • Compliance module not integrated with gateway                      │
│ • No UI for privacy management on either platform                    │
│ • Audit log retention not enforced                                   │
│ • No automated compliance reporting                                  │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U13: Analytics & Dashboard

```
┌─────────────────────────────────────────────────────────────────────┐
│ ANALYTICS & DASHBOARD FLOW                                          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Dashboard Data]                                                   │
│       ├── GET /api/analytics/dashboard                              │
│       │    └── Summary: recent moments, sphere activity, trends      │
│       │                                                             │
│       ├── GET /api/analytics/meaning                                │
│       │    └── Meaning map: emotional journeys, growth areas         │
│       │                                                             │
│       ├── GET /api/analytics/meaning/summary                        │
│       │    └── Aggregated meaning-making metrics                     │
│       │                                                             │
│       └── GET /api/analytics/insights                               │
│            └── AI-generated insights about user patterns             │
│                                                                     │
│  [Tracking]                                                         │
│       └── POST /api/analytics/track                                 │
│            Body: { event, properties }                              │
│            └── Record user interaction event                         │
│                                                                     │
│  [Reports]                                                          │
│       ├── POST /api/reports/generate → async report generation       │
│       │    Types: usage_summary, ai_insights, growth_report          │
│       └── GET /api/reports/:jobId → report status & download         │
│                                                                     │
│  [Language Evolution - Unique Feature]                               │
│       └── Language pattern analysis over time:                       │
│            ├── Vocabulary growth tracking                             │
│            ├── Emotional language evolution                           │
│            ├── Writing pattern changes                                │
│            └── UI: LanguageInsightsScreen (mobile, exists but        │
│                     routed) / LanguageInsightsPage (web, unrouted)   │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No dedicated Analytics page on web                                 │
│ • DashboardPage (web) and DashboardScreen (mobile) exist but        │
│   may not call all analytics endpoints                               │
│ • Report generation may use in-memory job tracking                   │
│ • LanguageInsightsPage not routed on web                             │
│ • No analytics data export or scheduled reports                      │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow U14: Moment Links & Temporal Arcs

```
┌─────────────────────────────────────────────────────────────────────┐
│ MOMENT LINKS & TEMPORAL ARCS FLOW                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  [Link Management]                                                  │
│       ├── POST /api/moments/:id/links                               │
│       │    Body: { targetMomentId, linkType }                       │
│       │    Link Types: related, follows, causes, caused_by,         │
│       │                similar, contradicts, expands, summarizes,    │
│       │                references                                    │
│       │                                                             │
│       ├── GET /api/moments/:id/links → list all links for moment    │
│       ├── DELETE /api/moments/:id/links/:linkId → remove link       │
│       └── GET /api/moments/:id/graph → connection graph             │
│            └── Returns nodes & edges for visualization               │
│                                                                     │
│  [UI Components]                                                    │
│       ├── Web: MomentLinkDialog, LinkedMomentsPanel,                │
│       │        TimelineArcView, LinkSuggestionsCard                  │
│       └── Mobile: NOT IMPLEMENTED                                    │
│                                                                     │
├─────────────────────────────────────────────────────────────────────┤
│ GAPS:                                                               │
│ • No mobile UI for moment links                                      │
│ • Link suggestions require Java Agent (semantic similarity)          │
│ • No automatic link detection between moments                        │
│ • Graph visualization may not be fully implemented                   │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 2.2 Admin Flows

> **CRITICAL: No admin system exists.** The following documents what SHOULD exist based on the feature set.

#### Flow A1: Admin Authentication & Access (NOT IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN AUTHENTICATION FLOW — ❌ NOT IMPLEMENTED                      │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED IMPLEMENTATION:                                            │
│                                                                     │
│  1. Add `role` field to User model                                  │
│     enum UserRole { USER, OPERATOR, ADMIN, SUPER_ADMIN }            │
│                                                                     │
│  2. Add `requireRole(role)` middleware                               │
│     ├── Check JWT → extract userId → lookup User.role                │
│     ├── Compare against required role                                │
│     └── Return 403 if insufficient                                   │
│                                                                     │
│  3. Admin login flow                                                │
│     ├── Same auth flow but with role verification                    │
│     ├── Mandatory 2FA for admin accounts                             │
│     └── Separate admin session tracking                              │
│                                                                     │
│  4. Admin audit trail                                               │
│     ├── Every admin action logged to AuditEvent                      │
│     └── Separate SecurityAuditLog for privilege changes              │
│                                                                     │
│ CURRENT STATE:                                                      │
│  • GET /api/analytics/admin/queue-status → has auth but             │
│    comment says "add admin role check" — not implemented             │
│  • POST /api/health/circuits/:name/reset → completely unprotected   │
│                                                                     │
│ RISK: Anyone with a valid JWT can reset circuit breakers             │
│       and view internal queue status.                                │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow A2: User Management (NOT IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN: USER MANAGEMENT FLOW — ❌ NOT IMPLEMENTED                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED ENDPOINTS:                                                 │
│                                                                     │
│  ├── GET    /api/admin/users                                        │
│  │    └── List all users with pagination, filters (tier, status)     │
│  │                                                                   │
│  ├── GET    /api/admin/users/:id                                    │
│  │    └── User detail (profile, billing, usage, sessions)            │
│  │                                                                   │
│  ├── PUT    /api/admin/users/:id                                    │
│  │    └── Update user (role, tier, status, notes)                    │
│  │                                                                   │
│  ├── POST   /api/admin/users/:id/suspend                            │
│  │    └── Suspend account (with reason, duration)                    │
│  │                                                                   │
│  ├── POST   /api/admin/users/:id/unsuspend                          │
│  │    └── Reactivate suspended account                               │
│  │                                                                   │
│  ├── DELETE  /api/admin/users/:id                                   │
│  │    └── Permanent deletion (admin override of GDPR flow)           │
│  │                                                                   │
│  ├── GET    /api/admin/users/:id/audit-log                          │
│  │    └── User's audit trail                                         │
│  │                                                                   │
│  └── POST   /api/admin/users/:id/impersonate                        │
│       └── Impersonate user for support (with full audit trail)       │
│                                                                     │
│ CURRENT STATE: Zero admin user management endpoints exist.           │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow A3: Content Moderation (NOT IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN: CONTENT MODERATION FLOW — ❌ NOT IMPLEMENTED                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED FOR SHARED/PUBLIC SPHERES:                                 │
│                                                                     │
│  ├── GET  /api/admin/moderation/queue                               │
│  │    └── Flagged/reported content waiting for review                │
│  │                                                                   │
│  ├── POST /api/admin/moderation/review                              │
│  │    └── Approve, reject, or escalate flagged content               │
│  │                                                                   │
│  ├── POST /api/admin/moderation/bulk                                │
│  │    └── Bulk actions on multiple items                             │
│  │                                                                   │
│  └── GET  /api/admin/moderation/stats                               │
│       └── Moderation queue metrics                                   │
│                                                                     │
│ ALSO NEEDED:                                                        │
│  • User-facing: POST /api/moments/:id/report (report content)       │
│  • User-facing: POST /api/users/:id/report (report user)            │
│  • Automated content scanning on upload                              │
│                                                                     │
│ CURRENT STATE: Zero moderation features exist. Public spheres        │
│ have no content oversight.                                           │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow A4: Billing Administration (NOT IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN: BILLING ADMINISTRATION FLOW — ❌ NOT IMPLEMENTED             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED ENDPOINTS:                                                 │
│                                                                     │
│  ├── GET    /api/admin/billing/overview                             │
│  │    └── Platform revenue, MRR, churn, subscriber counts           │
│  │                                                                   │
│  ├── GET    /api/admin/billing/subscriptions                        │
│  │    └── All active subscriptions with filters                      │
│  │                                                                   │
│  ├── POST   /api/admin/billing/users/:id/override-tier              │
│  │    └── Manually set user tier (comps, partners)                   │
│  │                                                                   │
│  ├── POST   /api/admin/billing/users/:id/credit                    │
│  │    └── Apply credits to account                                   │
│  │                                                                   │
│  └── GET    /api/admin/billing/invoices                             │
│       └── Invoice history across all users                           │
│                                                                     │
│ CURRENT STATE: Zero billing admin endpoints. Only user-facing        │
│ billing routes exist.                                                │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow A5: Platform Analytics (PARTIALLY IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN: PLATFORM ANALYTICS FLOW — ⚠️ PARTIALLY IMPLEMENTED          │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ EXISTING (but unprotected):                                         │
│  ├── GET /api/analytics/admin/queue-status → analytics queue state  │
│  └── GET /api/health/detailed → full system health                  │
│                                                                     │
│ REQUIRED:                                                           │
│  ├── GET  /api/admin/analytics/platform                             │
│  │    └── DAU/WAU/MAU, signups, retention, feature adoption          │
│  │                                                                   │
│  ├── GET  /api/admin/analytics/ai-usage                             │
│  │    └── Token consumption, costs, model usage, error rates         │
│  │                                                                   │
│  ├── GET  /api/admin/analytics/storage                              │
│  │    └── Total storage usage, per-user breakdown, growth trends     │
│  │                                                                   │
│  └── GET  /api/admin/analytics/errors                               │
│       └── Error rates, top errors, affected users                    │
│                                                                     │
│ CURRENT STATE: Admin queue status exists but is not role-protected.  │
│ No platform-wide analytics dashboard.                                │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow A6: System Configuration (NOT IMPLEMENTED)

```
┌─────────────────────────────────────────────────────────────────────┐
│ ADMIN: SYSTEM CONFIGURATION FLOW — ❌ NOT IMPLEMENTED               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED:                                                           │
│  ├── GET/PUT  /api/admin/config/feature-flags                       │
│  │    └── Enable/disable features per tier, globally, or per user    │
│  │                                                                   │
│  ├── GET/PUT  /api/admin/config/rate-limits                         │
│  │    └── Adjust rate limits dynamically                             │
│  │                                                                   │
│  ├── GET/PUT  /api/admin/config/ai-models                           │
│  │    └── Configure AI model versions, costs, fallbacks              │
│  │                                                                   │
│  ├── GET/PUT  /api/admin/config/tier-settings                       │
│  │    └── Adjust tier limits without code deployment                 │
│  │                                                                   │
│  └── POST     /api/admin/config/maintenance-mode                    │
│       └── Enable/disable maintenance mode                            │
│                                                                     │
│ CURRENT STATE: All configuration is env vars or hardcoded.           │
│ UserTierSettings exists in DB but no admin route to manage it.       │
└─────────────────────────────────────────────────────────────────────┘
```

---

### 2.3 Operator Flows

> "Operator" = DevOps, SRE, or support engineer with system access but not full admin.

#### Flow O1: Health Monitoring & Observability

```
┌─────────────────────────────────────────────────────────────────────┐
│ OPERATOR: HEALTH MONITORING FLOW — ⚠️ PARTIALLY IMPLEMENTED        │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ EXISTING:                                                           │
│  ├── GET /api/health                → basic health check             │
│  ├── GET /api/health/detailed       → extended health (DB, Redis,   │
│  │                                     S3, Java Agent, AI)           │
│  ├── GET /api/health/ready          → readiness probe (for K8s)     │
│  ├── GET /api/health/live           → liveness probe (for K8s)      │
│  ├── GET /api/health/circuits       → circuit breaker states         │
│  ├── POST /api/health/circuits/:name/reset → reset circuit          │
│  │    └── ❌ Unprotected — no auth                                  │
│  ├── GET /api/health/performance    → performance metrics            │
│  ├── GET /api/health/resources      → memory, CPU, event loop       │
│  └── GET /api/health/database       → DB connection pool metrics     │
│                                                                     │
│  ├── GET /api/system/circuit-breakers → alias for circuit states     │
│  └── GET /api/system/health/detailed  → alias for detailed health    │
│                                                                     │
│ GAPS IN MONITORING:                                                  │
│  • /metrics endpoint missing (Prometheus can't scrape)               │
│  • No prom-client library installed                                  │
│  • Monitoring middleware stores metrics in-memory (max 10K records)   │
│  • No distributed tracing (OpenTelemetry)                            │
│  • No log aggregation endpoint                                       │
│  • No alerting configuration                                         │
│  • Grafana dashboards likely empty scaffolds                         │
│                                                                     │
│ DOCKER STACK (docker-compose.local.yml):                            │
│  • PostgreSQL (dev: 5432, test: 5433)                                │
│  • Redis (6383)                                                      │
│  • MinIO (9002/9003)                                                 │
│  • MailHog (1025/8025)                                               │
│  • Prometheus (9090) — configured but can't scrape /metrics          │
│  • Grafana (3001) — scaffolded, likely empty dashboards              │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow O2: Service Management

```
┌─────────────────────────────────────────────────────────────────────┐
│ OPERATOR: SERVICE MANAGEMENT FLOW                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ SERVICES TO MANAGE:                                                 │
│  1. Fastify Gateway (port 2900)         — main API service           │
│  2. Java Agent (port 8090)              — ❌ NOT IMPLEMENTED        │
│  3. Collaboration WS Server (port ?)    — standalone, no startup     │
│  4. Intelligence Worker (BullMQ)        — standalone, no startup     │
│  5. Notification Worker (BullMQ)        — standalone, no startup     │
│  6. Compliance Worker (BullMQ)          — standalone, no startup     │
│  7. PostgreSQL (5432)                   — docker-compose             │
│  8. Redis (6383)                        — docker-compose             │
│  9. MinIO / S3 (9002)                   — docker-compose             │
│                                                                     │
│ GAPS:                                                               │
│  • No unified service orchestration                                  │
│  • Backend modules have no Dockerfile or start scripts               │
│  • No Kubernetes manifests for any service                           │
│  • No service discovery mechanism between services                   │
│  • No centralized config management (Vault, Consul, etc.)            │
│  • Docker Compose only covers infrastructure, not app services       │
│  • No graceful shutdown coordination between services                │
│  • No rolling deployment strategy                                    │
│  • No canary/blue-green deployment support                           │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow O3: Database Operations

```
┌─────────────────────────────────────────────────────────────────────┐
│ OPERATOR: DATABASE OPERATIONS FLOW                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ EXISTING:                                                           │
│  ├── Prisma migrations (4 migration files)                           │
│  ├── GET /api/health/database → connection pool status               │
│  ├── seed-simple.sql → basic seed data                               │
│  └── Demo data scripts in package.json (small/medium/large)          │
│                                                                     │
│ GAPS:                                                               │
│  • No backup automation                                              │
│  • No point-in-time recovery setup                                   │
│  • No read replica configuration                                     │
│  • No connection pool metrics exposed to Prometheus                  │
│  • No query performance dashboard                                    │
│  • No migration rollback strategy documented                         │
│  • No data sanitization for non-prod environments                    │
│  • Schema gap: 8+ missing models                                     │
└─────────────────────────────────────────────────────────────────────┘
```

#### Flow O4: Incident Response

```
┌─────────────────────────────────────────────────────────────────────┐
│ OPERATOR: INCIDENT RESPONSE FLOW — ❌ NOT IMPLEMENTED               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│ REQUIRED:                                                           │
│  ├── Circuit breaker dashboard with manual reset                     │
│  │    └── Exists (/api/health/circuits) but unprotected              │
│  │                                                                   │
│  ├── Maintenance mode toggle                                         │
│  │    └── Not implemented                                            │
│  │                                                                   │
│  ├── Alerting rules (Prometheus → PagerDuty/Slack)                  │
│  │    └── Not implemented                                            │
│  │                                                                   │
│  ├── Error rate monitoring with automatic circuit breaking           │
│  │    └── Circuit breaker exists but metrics not exposed              │
│  │                                                                   │
│  ├── Runbook / troubleshooting guides                                │
│  │    └── Not created                                                │
│  │                                                                   │
│  └── Log search & correlation                                        │
│       └── Logger supports correlation IDs but no aggregation          │
│                                                                     │
│ CURRENT STATE: Operators can hit health endpoints manually.          │
│ No automated alerting, no runbooks, no incident management.          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. COMPREHENSIVE TEST PLAN

### 3.1 Testing Strategy Overview

| Layer | Framework | Coverage Target | Current State |
|:------|:----------|:----------------|:--------------|
| **Java Agent Unit** | JUnit 5 + EventloopTestBase | 85% | ❌ 0% (no code) |
| **Gateway Unit** | Vitest | 85% | ~40% (16 test files) |
| **Gateway Integration** | Vitest + Supertest | 80% | ~15% (1 integration test file) |
| **Mobile Unit** | Jest + RTL | 80% | ~35% (24 test files) |
| **Mobile E2E** | Detox | Key flows | ~60% (10 E2E scenarios) |
| **Web Unit** | Jest + RTL | 80% | ~25% (6 test files) |
| **Web E2E** | Playwright | Key flows | ~60% (10 E2E scenarios) |
| **Shared Lib** | Vitest | 90% | ~20% (2 test files) |
| **Performance** | k6 / Artillery | All critical paths | ~10% (1 perf test file) |
| **Security** | OWASP ZAP / manual | OWASP Top 10 | ❌ 0% |

### 3.2 Test Case Catalog

---

#### Module: AUTH — Authentication & Authorization

##### TC-AUTH-001: User Registration

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-001-P1 | Register with valid email/password | Positive | POST /auth/register { email, password, name } | 201, returns user + tokens | P0 |
| AUTH-001-P2 | Register creates default sphere | Positive | Register → GET /api/spheres | Default "Personal" sphere exists | P0 |
| AUTH-001-P3 | Register creates tier settings | Positive | Register → check DB | UserTierSettings created with FREE defaults | P1 |
| AUTH-001-P4 | Register emits audit event | Positive | Register → check AuditEvent table | USER_REGISTERED event logged | P1 |
| AUTH-001-N1 | Register with existing email | Negative | POST /auth/register with used email | 409 Conflict | P0 |
| AUTH-001-N2 | Register with weak password | Negative | POST /auth/register { password: "123" } | 400, password strength error | P0 |
| AUTH-001-N3 | Register with invalid email | Negative | POST /auth/register { email: "notanemail" } | 400, validation error | P0 |
| AUTH-001-N4 | Register with missing fields | Negative | POST /auth/register { } | 400, missing required fields | P1 |
| AUTH-001-N5 | Register with SQL injection in email | Negative | POST /auth/register { email: "'; DROP TABLE--" } | 400, sanitized/rejected | P0 |
| AUTH-001-N6 | Register with XSS in name | Negative | POST /auth/register { name: "<script>alert(1)</script>" } | 201 but name sanitized | P1 |
| AUTH-001-E1 | Register when DB unavailable | Edge | Bring DB down → register | 500 with friendly error | P1 |
| AUTH-001-E2 | Concurrent registration same email | Edge | 2 parallel POSTs same email | One succeeds, one 409 | P1 |
| AUTH-001-E3 | Register with max-length fields | Edge | 255 char email, 1000 char name | Handled (accept or reject gracefully) | P2 |
| AUTH-001-E4 | Register with unicode chars | Edge | Email with unicode, name with emojis | Handled correctly | P2 |

##### TC-AUTH-002: User Login

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-002-P1 | Login with valid credentials | Positive | POST /auth/login { email, password } | 200, user + tokens | P0 |
| AUTH-002-P2 | Login creates session | Positive | Login → GET /auth/sessions | Session with device info | P0 |
| AUTH-002-P3 | Login resets failed attempts | Positive | Fail 2x → succeed → check failedAttempts | Count reset to 0 | P1 |
| AUTH-002-N1 | Login with wrong password | Negative | POST /auth/login wrong password | 401, failedAttempts++ | P0 |
| AUTH-002-N2 | Login with nonexistent email | Negative | POST /auth/login unknown email | 401 (same response as wrong pw) | P0 |
| AUTH-002-N3 | Login with empty body | Negative | POST /auth/login {} | 400 | P1 |
| AUTH-002-E1 | Account lockout after 5 failures | Edge | 5 wrong passwords | 423 Locked, 30min lockout | P0 |
| AUTH-002-E2 | Login after lockout expires | Edge | Lock → wait 30min → login | Success | P1 |
| AUTH-002-E3 | Login from multiple devices | Edge | Login on device A → Login on device B | Both sessions active | P1 |
| AUTH-002-E4 | Login with 2FA enabled | Edge | Login → 200 { requires2FA, tempToken } | Requires 2FA verification | P0 |

##### TC-AUTH-003: 2FA Setup & Verification

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-003-P1 | Setup 2FA | Positive | POST /auth/2fa/setup | { secret, qrCodeUrl } | P0 |
| AUTH-003-P2 | Enable 2FA with valid TOTP | Positive | Setup → POST /auth/2fa/enable { code } | 2FA enabled | P0 |
| AUTH-003-P3 | Verify 2FA on login | Positive | Login (2FA) → POST /auth/2fa/verify { tempToken, code } | Full tokens returned | P0 |
| AUTH-003-N1 | Enable 2FA with wrong TOTP | Negative | POST /auth/2fa/enable { wrongCode } | 400 | P0 |
| AUTH-003-N2 | Verify with expired tempToken | Negative | Wait > expiry → verify | 401 | P1 |
| AUTH-003-E1 | Use backup code | Edge | Verify with backup code instead of TOTP | Success, backup code consumed | P1 |
| AUTH-003-E2 | All backup codes used | Edge | Use all backup codes | Last one works, then none left | P2 |

##### TC-AUTH-004: Token Refresh

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-004-P1 | Refresh with valid token | Positive | POST /auth/refresh { refreshToken } | New access + refresh tokens | P0 |
| AUTH-004-P2 | Old refresh token invalidated | Positive | Refresh → use old token | 401 | P0 |
| AUTH-004-N1 | Refresh with expired token | Negative | Wait > expiry → refresh | 401 | P0 |
| AUTH-004-N2 | Refresh with revoked token | Negative | Revoke → refresh | 401 | P0 |
| AUTH-004-E1 | Concurrent refresh same token | Edge | 2 parallel refresh calls | One succeeds, one fails | P1 |

##### TC-AUTH-005: Session Management

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-005-P1 | List sessions | Positive | GET /auth/sessions | Array of active sessions | P0 |
| AUTH-005-P2 | Revoke specific session | Positive | DELETE /auth/sessions/:id | Session deactivated | P0 |
| AUTH-005-N1 | Revoke other user's session | Negative | DELETE with wrong user's session ID | 403 or 404 | P0 |
| AUTH-005-E1 | Revoke own current session | Edge | DELETE own session → make API call | Token should be invalid | P1 |

##### TC-AUTH-006: Password Reset

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| AUTH-006-P1 | Request reset email | Positive | POST /auth/password-reset { email } | 200, email sent | P0 |
| AUTH-006-P2 | Confirm reset with valid token | Positive | POST /auth/password-reset/confirm { token, newPassword } | Password updated | P0 |
| AUTH-006-N1 | Reset for nonexistent email | Negative | POST /auth/password-reset { unknownEmail } | 200 (no leak) | P0 |
| AUTH-006-N2 | Confirm with expired token | Negative | Wait > expiry → confirm | 400 | P0 |
| AUTH-006-N3 | Confirm with used token | Negative | Use token twice | Second use rejected | P0 |
| AUTH-006-E1 | Multiple reset requests | Edge | Request reset 3x | Only latest token valid | P1 |

---

#### Module: MOMENTS — Moment CRUD & Lifecycle

##### TC-MOM-001: Create Moment (Text)

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOM-001-P1 | Create text moment | Positive | POST /api/moments { contentText, contentType: TEXT, sphereId } | 201, moment with ID | P0 |
| MOM-001-P2 | Create with tags | Positive | POST /api/moments { ...fields, tags: ["tag1"] } | Tags stored | P1 |
| MOM-001-P3 | Create with metadata | Positive | POST /api/moments { ...fields, metadata: { location, weather } } | Metadata stored as JSONB | P1 |
| MOM-001-P4 | Auto-classify sphere | Positive | POST /api/moments without sphereId | Sphere auto-assigned (if Java Agent works) | P1 |
| MOM-001-N1 | Create without auth | Negative | POST /api/moments (no token) | 401 | P0 |
| MOM-001-N2 | Create in non-owned sphere | Negative | POST /api/moments { sphereId: otherUser's } | 403 | P0 |
| MOM-001-N3 | Create exceeding monthly limit | Negative | Create 101st moment on free tier | 429 limit exceeded | P0 |
| MOM-001-N4 | Create with empty content | Negative | POST /api/moments { contentText: "" } | 400 | P1 |
| MOM-001-N5 | Create with invalid sphereId | Negative | POST /api/moments { sphereId: "nonexistent" } | 404 | P1 |
| MOM-001-E1 | Create with very long text | Edge | 100KB text content | Handled (accept or graceful error) | P2 |
| MOM-001-E2 | Create with special characters | Edge | Emojis, RTL text, zero-width chars | Stored correctly | P2 |
| MOM-001-E3 | Concurrent creates hitting limit | Edge | Parallel creates at boundary | At most limit, no over-count | P2 |

##### TC-MOM-002: Read Moments

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOM-002-P1 | List user's moments | Positive | GET /api/moments | Paginated moments array | P0 |
| MOM-002-P2 | Filter by sphere | Positive | GET /api/moments?sphereId=X | Only moments in sphere X | P0 |
| MOM-002-P3 | Filter by contentType | Positive | GET /api/moments?contentType=VOICE | Only voice moments | P1 |
| MOM-002-P4 | Filter by date range | Positive | GET /api/moments?from=X&to=Y | Moments in range | P1 |
| MOM-002-P5 | Get single moment by ID | Positive | GET /api/moments/:id | Full moment with media refs | P0 |
| MOM-002-N1 | Get moment without auth | Negative | GET /api/moments/:id (no token) | 401 | P0 |
| MOM-002-N2 | Get other user's moment | Negative | GET /api/moments/:otherUserId'sMoment | 403 or 404 | P0 |
| MOM-002-E1 | Get nonexistent moment | Edge | GET /api/moments/invalid-uuid | 404 | P1 |
| MOM-002-E2 | List with 10,000+ moments | Edge | User has many moments → paginate | Efficient pagination | P2 |
| MOM-002-E3 | Access moment in shared sphere | Edge | User B reads moment in sphere shared by User A | Allowed based on access level | P1 |

##### TC-MOM-003: Update Moment

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOM-003-P1 | Update content text | Positive | PUT /api/moments/:id { contentText: "new" } | Updated, version incremented | P0 |
| MOM-003-P2 | Update tags | Positive | PUT /api/moments/:id { tags: ["new"] } | Tags updated | P1 |
| MOM-003-N1 | Update other user's moment | Negative | PUT other user's moment | 403 | P0 |
| MOM-003-N2 | Update with invalid data | Negative | PUT /api/moments/:id { contentType: "INVALID" } | 400 | P1 |
| MOM-003-E1 | Concurrent updates | Edge | 2 parallel PUTs on same moment | Last write wins or conflict detected | P2 |

##### TC-MOM-004: Delete Moment

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOM-004-P1 | Delete own moment | Positive | DELETE /api/moments/:id | Soft-deleted | P0 |
| MOM-004-P2 | Audit event emitted | Positive | Delete → check audit log | MOMENT_DELETED event | P1 |
| MOM-004-N1 | Delete other user's moment | Negative | DELETE other user's moment | 403 | P0 |
| MOM-004-E1 | Delete moment with media | Edge | Delete moment that has S3 files | Media cleaned up or orphaned? | P1 |
| MOM-004-E2 | Delete moment with links | Edge | Delete moment with moment links | Links removed/orphaned handled | P2 |

---

#### Module: UPLOAD — File Upload & Media

##### TC-UPL-001: Presigned URL Generation

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| UPL-001-P1 | Get presigned URL for audio | Positive | POST /api/upload/presigned-url { momentId, fileName, mimeType: "audio/mp4" } | { uploadUrl, mediaReferenceId } | P0 |
| UPL-001-P2 | Get presigned URL for image | Positive | Same with mimeType: "image/jpeg" | Success | P0 |
| UPL-001-P3 | Get presigned URL for video | Positive | Same with mimeType: "video/mp4" | Success | P0 |
| UPL-001-N1 | Request without auth | Negative | No token | 401 | P0 |
| UPL-001-N2 | Request for other user's moment | Negative | MomentId not owned by user | 403 | P0 |
| UPL-001-N3 | Exceed storage limit | Negative | Free user at 1GB → upload 100MB file | 429 storage limit | P0 |
| UPL-001-N4 | Invalid mime type | Negative | mimeType: "application/exe" | 400 | P1 |
| UPL-001-E1 | Very large file size declaration | Edge | fileSize: 10GB | Rejected or flagged | P1 |

##### TC-UPL-002: Upload Completion

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| UPL-002-P1 | Complete audio upload | Positive | POST /api/upload/complete { mediaReferenceId, momentId } | Transcription enqueued | P0 |
| UPL-002-P2 | Complete image upload | Positive | Complete + no transcription triggered | Success, no transcription | P0 |
| UPL-002-N1 | Complete with invalid mediaReferenceId | Negative | Nonexistent ID | 404 | P0 |
| UPL-002-N2 | Complete already completed upload | Negative | Call complete twice | Idempotent or error | P1 |
| UPL-002-E1 | Complete but S3 file missing | Edge | Complete without actual S3 upload | Handled gracefully | P1 |

##### TC-UPL-003: Progressive Upload

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| UPL-003-P1 | Full progressive upload flow | Positive | Init → upload chunks → complete | File assembled correctly | P0 |
| UPL-003-P2 | Check upload status | Positive | GET /api/progressive/status/:id | Progress percentage | P1 |
| UPL-003-N1 | Upload chunk out of order | Negative | Skip chunk 2, upload chunk 3 | Handled (queue or error) | P1 |
| UPL-003-N2 | Complete with missing chunks | Negative | Complete before all chunks sent | Error | P1 |
| UPL-003-E1 | Upload interrupted mid-chunk | Edge | Connection drops during chunk | Resumable | P1 |
| UPL-003-E2 | Very large file (5GB) | Edge | Upload 5GB in chunks | Handled within platform limits | P2 |

---

#### Module: TRANSCRIPTION — Audio/Video Transcription

##### TC-TRN-001: Transcription Flow

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| TRN-001-P1 | Transcribe audio moment | Positive | Upload audio → complete → check transcript | moment.contentTranscript populated | P0 |
| TRN-001-P2 | Batch transcription | Positive | POST /api/transcription/batch { momentIds } | All transcribed | P1 |
| TRN-001-P3 | Get transcript by momentId | Positive | GET /api/transcription/:momentId | Transcript content | P0 |
| TRN-001-N1 | Transcribe non-audio moment | Negative | POST /api/transcription/transcribe { textMomentId } | Error or no-op | P1 |
| TRN-001-N2 | Transcription hour limit exceeded | Negative | Free user exceeds 10hrs | Should reject (❌ NOT ENFORCED) | P0 |
| TRN-001-E1 | Transcribe 1-second audio | Edge | Very short audio | Valid transcript returned | P2 |
| TRN-001-E2 | Transcribe 3-hour audio | Edge | Very long audio | Processed (may time out?) | P2 |
| TRN-001-E3 | Transcribe corrupted audio | Edge | Upload invalid audio binary | Graceful failure | P1 |
| TRN-001-E4 | Multiple languages in same audio | Edge | Mixed English/Spanish audio | Best-effort transcription | P2 |

---

#### Module: SEARCH — Search & Discovery

##### TC-SRC-001: Search Operations

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| SRC-001-P1 | Text search | Positive | POST /api/search { query: "reflection", type: "text" } | Matching moments | P0 |
| SRC-001-P2 | Semantic search | Positive | POST /api/search { query: "feeling happy", type: "semantic" } | Semantically similar moments | P0 |
| SRC-001-P3 | Hybrid search | Positive | POST /api/search { query: "...", type: "hybrid" } | Combined ranked results | P0 |
| SRC-001-P4 | Search with sphere filter | Positive | POST /api/search { query, sphereId } | Filtered to sphere | P1 |
| SRC-001-P5 | Get search suggestions | Positive | GET /api/search/suggestions | Suggestion list | P1 |
| SRC-001-P6 | Find similar moments | Positive | POST /api/search/similar { momentId } | Similar moments ranked | P1 |
| SRC-001-N1 | Search without auth | Negative | POST /api/search (no token) | 401 | P0 |
| SRC-001-N2 | Empty query | Negative | POST /api/search { query: "" } | 400 or empty results | P1 |
| SRC-001-N3 | Semantic search without embeddings | Negative | Search when Java Agent down (no embeddings) | Fallback to text search or error | P0 |
| SRC-001-E1 | Search with SQL injection | Edge | POST /api/search { query: "'; DROP TABLE--" } | Safe, no injection | P0 |
| SRC-001-E2 | Search returns 1000+ results | Edge | Broad query | Paginated correctly | P1 |
| SRC-001-E3 | Search across shared spheres | Edge | User searches → results from shared sphere | Access-scoped results | P1 |

---

#### Module: SPHERES — Sphere Management

##### TC-SPH-001: Sphere CRUD

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| SPH-001-P1 | Create sphere | Positive | POST /api/spheres { name, type, visibility } | 201, sphere created | P0 |
| SPH-001-P2 | List spheres | Positive | GET /api/spheres | Owned + shared spheres | P0 |
| SPH-001-P3 | Update sphere | Positive | PUT /api/spheres/:id { name: "new" } | Updated | P0 |
| SPH-001-P4 | Delete sphere | Positive | DELETE /api/spheres/:id | Soft-deleted | P0 |
| SPH-001-N1 | Exceed sphere limit | Negative | Free user create 4th sphere | 429 limit exceeded | P0 |
| SPH-001-N2 | Update non-owned sphere | Negative | PUT sphere owned by another | 403 | P0 |
| SPH-001-N3 | Delete non-owned sphere | Negative | DELETE sphere owned by another | 403 | P0 |
| SPH-001-E1 | Delete sphere with moments | Edge | Delete sphere containing 100 moments | Moments handled (orphaned or moved) | P1 |
| SPH-001-E2 | Delete shared sphere | Edge | Owner deletes sphere shared with others | All access revoked | P1 |
| SPH-001-E3 | Delete default Personal sphere | Edge | Delete auto-created sphere | Prevented or handled | P2 |

---

#### Module: COLLAB — Collaboration

##### TC-COL-001: Sphere Sharing

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| COL-001-P1 | Share sphere with VIEWER role | Positive | POST /api/collaboration/spheres/share { sphereId, email, role: VIEWER } | Access granted | P0 |
| COL-001-P2 | Share sphere with EDITOR role | Positive | Same with EDITOR | Access granted with edit perms | P0 |
| COL-001-N1 | Share by non-owner | Negative | EDITOR tries to share | 403 | P0 |
| COL-001-N2 | Share with nonexistent user | Negative | Share with unknown email | Error or invite created | P1 |
| COL-001-N3 | Free user sharing (limit: 0) | Negative | Free user shares sphere | Should reject (❌ NOT ENFORCED) | P0 |
| COL-001-E1 | Share sphere already shared with user | Edge | Share twice with same user | Idempotent update or error | P1 |
| COL-001-E2 | Share with self | Edge | Owner shares with own email | Rejected or no-op | P2 |

##### TC-COL-002: Comments & Reactions

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| COL-002-P1 | Add comment to moment | Positive | POST /api/collaboration/comments { momentId, text } | Comment created (❌ no model) | P0 |
| COL-002-P2 | React to moment | Positive | POST /api/collaboration/reactions { momentId, type } | Reaction recorded (❌ no model) | P1 |
| COL-002-N1 | Comment on non-accessible moment | Negative | Comment on moment in non-shared sphere | 403 | P0 |
| COL-002-E1 | Very long comment | Edge | 10,000 char comment | Truncated or rejected | P2 |

---

#### Module: ANALYTICS — Analytics & Reporting

##### TC-ANL-001: Analytics Endpoints

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| ANL-001-P1 | Get dashboard data | Positive | GET /api/analytics/dashboard | Summary stats | P0 |
| ANL-001-P2 | Get meaning analytics | Positive | GET /api/analytics/meaning | Emotional journey data | P1 |
| ANL-001-P3 | Track user event | Positive | POST /api/analytics/track { event, properties } | Event recorded | P1 |
| ANL-001-P4 | Generate report | Positive | POST /api/reports/generate { type: "usage_summary" } | { jobId } | P1 |
| ANL-001-N1 | Access without auth | Negative | GET /api/analytics/dashboard (no token) | 401 | P0 |
| ANL-001-E1 | Dashboard with zero data | Edge | New user → dashboard | Empty/default state | P1 |
| ANL-001-E2 | Report for large date range | Edge | 1-year report | Completes within timeout | P2 |

---

#### Module: BILLING — Subscription & Payments

##### TC-BIL-001: Billing Operations

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| BIL-001-P1 | Get usage summary | Positive | GET /api/billing/usage | Current usage vs limits | P0 |
| BIL-001-P2 | Get tier limits | Positive | GET /api/billing/limits | Tier limit details | P0 |
| BIL-001-P3 | Upgrade to Pro | Positive | POST /api/billing/upgrade { tier: "PRO" } | Stripe checkout URL | P0 |
| BIL-001-P4 | Stripe webhook: checkout complete | Positive | POST /api/billing/webhook (checkout.session.completed) | User tier updated to PRO | P0 |
| BIL-001-P5 | Stripe webhook: subscription deleted | Positive | POST /api/billing/webhook (subscription.deleted) | User downgraded to FREE | P0 |
| BIL-001-N1 | Upgrade without auth | Negative | POST /api/billing/upgrade (no token) | 401 | P0 |
| BIL-001-N2 | Webhook with invalid signature | Negative | POST /api/billing/webhook (bad sig) | 400 | P0 |
| BIL-001-N3 | Downgrade already on free | Negative | POST /api/billing/downgrade on free tier | Error or no-op | P1 |
| BIL-001-E1 | Webhook arrives before checkout | Edge | Race condition | Handled idempotently | P1 |
| BIL-001-E2 | Payment fails repeatedly | Edge | invoice.payment_failed multiple times | User notified, eventually downgraded | P1 |

---

#### Module: PRIVACY — GDPR Compliance

##### TC-PRV-001: Data Export

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| PRV-001-P1 | Request data export | Positive | POST /api/privacy/export { format: "JSON" } | { exportId } | P0 |
| PRV-001-P2 | Check export status | Positive | GET /api/privacy/export/:id/status | { status: "completed" } | P0 |
| PRV-001-P3 | Download export | Positive | GET /api/privacy/export/:id/download | File download | P0 |
| PRV-001-N1 | Download other user's export | Negative | GET other user's export ID | 403 | P0 |
| PRV-001-E1 | Export with 10,000 moments | Edge | Large data set | Completes within reasonable time | P1 |
| PRV-001-E2 | Export with media files | Edge | Include S3 media | ZIP or links provided | P1 |

##### TC-PRV-002: Account Deletion

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| PRV-002-P1 | Request deletion | Positive | POST /api/privacy/deletion | Deletion request created | P0 |
| PRV-002-P2 | Verify deletion | Positive | POST /api/privacy/deletion/verify | Account marked for deletion | P0 |
| PRV-002-N1 | Delete without verification | Negative | Skip verify → check account | Account still active | P0 |
| PRV-002-E1 | Delete with active subscription | Edge | Delete Stripe customer mid-billing | Subscription cancelled | P1 |
| PRV-002-E2 | Delete owner of shared sphere | Edge | Owner deletes → shared users | Access revoked, spheres cleaned | P1 |

---

#### Module: REFLECTION — AI Insights

##### TC-REF-001: AI Reflection

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| REF-001-P1 | Generate insights | Positive | POST /api/reflection/insights { sphereId } | Insights array | P0 |
| REF-001-N1 | Exceed insight limit | Negative | Free user > 5 insights/mo | 429 (❌ counter not incremented) | P0 |
| REF-001-N2 | Non-owned sphere insights | Negative | Generate for non-accessible sphere | 403 | P0 |
| REF-001-N3 | Java Agent unavailable | Negative | Agent down → generate insights | 502 (no fallback) | P0 |
| REF-001-E1 | Sphere with zero moments | Edge | Generate insights for empty sphere | Empty insights or meaningful message | P1 |
| REF-001-E2 | Sphere with 1 moment | Edge | Generate insights for single moment | Minimal insights | P2 |

---

#### Module: MEMORY — Memory Expansion

##### TC-MEM-001: Memory Expansion

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MEM-001-P1 | Request summarize expansion | Positive | POST /api/memory-expansion { momentId, type: "summarize" } | { jobId } | P0 |
| MEM-001-P2 | List expansions | Positive | GET /api/memory-expansion | User's expansions | P1 |
| MEM-001-P3 | Get expansion result | Positive | GET /api/memory-expansion/result/:id | Expansion content | P1 |
| MEM-001-N1 | Exceed expansion limit | Negative | Free user > 2 expansions/mo | 429 | P0 |
| MEM-001-E1 | Expand very short moment | Edge | 1-word moment → summarize | Meaningful response | P2 |

---

#### Module: LINKS — Moment Links

##### TC-LNK-001: Moment Links

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| LNK-001-P1 | Create link between moments | Positive | POST /api/moments/:id/links { targetMomentId, linkType: "related" } | Link created | P0 |
| LNK-001-P2 | List moment links | Positive | GET /api/moments/:id/links | Array of links | P1 |
| LNK-001-P3 | Get connection graph | Positive | GET /api/moments/:id/graph | { nodes, edges } | P1 |
| LNK-001-P4 | Delete link | Positive | DELETE /api/moments/:id/links/:linkId | Link removed | P1 |
| LNK-001-N1 | Link to non-owned moment | Negative | Link to other user's moment | 403 | P0 |
| LNK-001-E1 | Self-link | Edge | Link moment to itself | Rejected | P2 |
| LNK-001-E2 | Duplicate link | Edge | Same link twice | Idempotent or error | P2 |
| LNK-001-E3 | Circular link chain | Edge | A→B→C→A | Allowed (graph supports cycles) | P2 |

---

#### Module: ADOPTION — Onboarding & Engagement

##### TC-ADP-001: Adoption System

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| ADP-001-P1 | Get suggestions | Positive | GET /api/adoption/suggestions | Feature suggestions | P1 |
| ADP-001-P2 | Get onboarding checklist | Positive | GET /api/adoption/onboarding | Checklist with completion % | P1 |
| ADP-001-P3 | Track interaction | Positive | POST /api/adoption/suggestions/interactions { action } | Recorded | P1 |
| ADP-001-E1 | Completed all onboarding | Edge | All checklist items done | 100% complete, no more suggestions | P2 |

---

#### Module: HEALTH — System Health

##### TC-HLT-001: Health Checks

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| HLT-001-P1 | Basic health check | Positive | GET /api/health | { status: "ok" } | P0 |
| HLT-001-P2 | Detailed health check | Positive | GET /api/health/detailed | All dependency statuses | P0 |
| HLT-001-P3 | Readiness probe | Positive | GET /api/health/ready | { ready: true/false } | P0 |
| HLT-001-P4 | Liveness probe | Positive | GET /api/health/live | { alive: true } | P0 |
| HLT-001-P5 | Circuit breaker status | Positive | GET /api/health/circuits | All circuit states | P1 |
| HLT-001-E1 | Health when DB down | Edge | Kill PostgreSQL → check health | Degraded but alive | P0 |
| HLT-001-E2 | Health when Redis down | Edge | Kill Redis → check health | Degraded but alive | P1 |
| HLT-001-E3 | Health when S3 down | Edge | Kill MinIO → check health | Degraded but alive | P1 |

---

#### Module: SECURITY — Rate Limiting & Protection

##### TC-SEC-001: Rate Limiting

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| SEC-001-P1 | Within rate limit | Positive | Normal request frequency | 200 responses | P0 |
| SEC-001-N1 | Exceed rate limit | Negative | Burst 100 requests/second | 429 after threshold | P0 |
| SEC-001-N2 | Exceed tier-specific limit | Negative | Free user exceeds per-minute limit | 429 | P0 |
| SEC-001-E1 | Rate limit reset | Edge | Hit limit → wait → retry | Allowed after window reset | P1 |
| SEC-001-E2 | Distributed rate limiting | Edge | Same user from 2 IPs | Combined rate applied | P2 |

##### TC-SEC-002: Input Validation & Injection Prevention

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| SEC-002-N1 | SQL injection in search query | Negative | POST /api/search { query: "' OR 1=1--" } | Sanitized, no injection | P0 |
| SEC-002-N2 | XSS in moment content | Negative | Create moment with `<script>alert(1)</script>` | Content stored safely | P0 |
| SEC-002-N3 | Path traversal in upload | Negative | fileName: "../../etc/passwd" | Rejected | P0 |
| SEC-002-N4 | JWT tampering | Negative | Modified JWT payload | 401 | P0 |
| SEC-002-N5 | CSRF attack attempt | Negative | Cross-origin request without CORS header | Blocked by CORS | P1 |
| SEC-002-N6 | Oversized request body | Negative | 100MB JSON body | 413 Payload Too Large | P1 |

---

#### Module: MOBILE — Mobile-Specific Tests

##### TC-MOB-001: Offline Functionality

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOB-001-P1 | Create moment offline | Positive | Airplane mode → create text moment | Stored in SQLite | P0 |
| MOB-001-P2 | Sync when online | Positive | Go offline → create moments → go online | Auto-synced to server | P0 |
| MOB-001-P3 | Read cached moments | Positive | Load moments → go offline → browse | Cached data available | P0 |
| MOB-001-E1 | Conflict on sync | Edge | Edit moment on web + mobile offline → sync | Conflict resolved | P1 |
| MOB-001-E2 | Large sync queue | Edge | 100 offline moments → sync | All synced reliably | P1 |
| MOB-001-E3 | Interrupted sync | Edge | Start sync → lose connection | Resume on reconnect | P1 |

##### TC-MOB-002: Media Capture

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOB-002-P1 | Record voice note | Positive | Navigate to VoiceRecorder → record → save | Audio uploaded + transcribed | P0 |
| MOB-002-P2 | Capture image | Positive | Navigate to ImageCapture → take photo → save | Image uploaded | P0 |
| MOB-002-P3 | Record video | Positive | Navigate to VideoRecorder → record → save | Video uploaded + transcribed | P0 |
| MOB-002-E1 | Permission denied (microphone) | Edge | Deny mic permission → record | Graceful error message | P0 |
| MOB-002-E2 | Permission denied (camera) | Edge | Deny camera perm → capture | Graceful error message | P0 |
| MOB-002-E3 | Low storage on device | Edge | Device nearly full → record | Warning shown | P1 |
| MOB-002-E4 | Background upload after app kill | Edge | Start upload → kill app → reopen | Upload resumes | P1 |
| MOB-002-E5 | Low battery during upload | Edge | Battery dies mid-upload | Resume on charge | P2 |

##### TC-MOB-003: Navigation & UX

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| MOB-003-P1 | Tab navigation | Positive | Tap each tab | All 5 tabs navigate correctly | P0 |
| MOB-003-P2 | Deep linking | Positive | Open moment via deep link | Navigate to moment detail | P1 |
| MOB-003-E1 | Back navigation from capture | Edge | Capture → back button mid-recording | Confirm discard dialog | P0 |
| MOB-003-E2 | Rotate device during capture | Edge | Rotate mid-recording | Layout adjusts, recording continues | P1 |

---

#### Module: WEB — Web-Specific Tests

##### TC-WEB-001: Web Functionality

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| WEB-001-P1 | Login flow | Positive | Navigate /login → enter creds → submit | Redirect to dashboard | P0 |
| WEB-001-P2 | Dashboard load | Positive | Navigate / (authenticated) | Dashboard with data | P0 |
| WEB-001-P3 | Capture text moment | Positive | Navigate /capture → type → save | Moment created | P0 |
| WEB-001-P4 | Browse moments | Positive | Navigate /moments → scroll | Paginated moments list | P0 |
| WEB-001-P5 | Manage spheres | Positive | Navigate /spheres → create/edit | Sphere CRUD works | P0 |
| WEB-001-N1 | Access private route unauthenticated | Negative | Navigate / without login | Redirect to /login | P0 |
| WEB-001-E1 | Browser back/forward | Edge | Navigate through pages → back → forward | Correct routing | P1 |
| WEB-001-E2 | Page refresh preserves state | Edge | Login → refresh page | Still authenticated | P0 |
| WEB-001-E3 | Token expiry during session | Edge | Wait 15min+ → make request | Auto-refresh or re-login | P0 |

---

#### Module: PERF — Performance Tests

##### TC-PERF-001: API Performance

| ID | Case | Type | Steps | Expected | Priority |
|:---|:-----|:-----|:------|:---------|:---------|
| PERF-001-1 | Moment creation latency | Performance | k6: 100 concurrent users creating moments | p95 < 200ms | P0 |
| PERF-001-2 | Search latency | Performance | k6: 50 concurrent searches | p95 < 500ms | P0 |
| PERF-001-3 | Upload throughput | Performance | k6: 20 concurrent 10MB uploads | All complete < 30s | P1 |
| PERF-001-4 | Dashboard API latency | Performance | k6: 100 concurrent dashboard loads | p95 < 300ms | P1 |
| PERF-001-5 | Soak test (4hr) | Performance | Steady 50 RPS for 4 hours | No memory leaks, p95 stable | P1 |
| PERF-001-6 | Spike test | Performance | 0 → 500 RPS in 10s | Graceful degradation, no crashes | P2 |

---

### 3.3 Test Coverage Summary

| Category | Total Cases | P0 | P1 | P2 | Exists | New |
|:---------|:------------|:---|:---|:---|:-------|:----|
| Auth | 34 | 18 | 12 | 4 | ~6 | ~28 |
| Moments | 16 | 6 | 6 | 4 | ~4 | ~12 |
| Upload | 11 | 5 | 4 | 2 | ~3 | ~8 |
| Transcription | 8 | 3 | 2 | 3 | ~3 | ~5 |
| Search | 12 | 5 | 4 | 3 | ~3 | ~9 |
| Spheres | 9 | 5 | 2 | 2 | ~2 | ~7 |
| Collaboration | 8 | 4 | 2 | 2 | 0 | 8 |
| Analytics | 6 | 2 | 3 | 1 | 0 | 6 |
| Billing | 10 | 5 | 3 | 2 | ~2 | ~8 |
| Privacy | 8 | 4 | 2 | 2 | ~2 | ~6 |
| Reflection | 6 | 3 | 1 | 2 | 0 | 6 |
| Memory Expansion | 5 | 2 | 1 | 2 | 0 | 5 |
| Moment Links | 7 | 2 | 1 | 4 | 0 | 7 |
| Adoption | 4 | 0 | 2 | 2 | 0 | 4 |
| Health | 8 | 4 | 2 | 2 | 0 | 8 |
| Security | 8 | 5 | 2 | 1 | 0 | 8 |
| Mobile | 12 | 5 | 5 | 2 | ~8 | ~4 |
| Web | 8 | 5 | 2 | 1 | ~4 | ~4 |
| Performance | 6 | 2 | 3 | 1 | ~1 | ~5 |
| **TOTAL** | **186** | **95** | **59** | **42** | **~38** | **~148** |

### 3.4 Test Execution Strategy

#### Phase 1 (with Java Agent development):
- Java Agent unit tests (JUnit 5 + EventloopTestBase)
- Gateway ↔ Java Agent integration tests
- All P0 test cases for existing routes

#### Phase 2 (Architecture cleanup):
- Schema migration tests (new models)
- Limit enforcement tests (transcription, collaborator, AI insight)
- Cron job tests

#### Phase 3 (RBAC):
- Admin role-based access tests
- Admin endpoint protection tests

#### Phase 4 (Frontend):
- New page/screen component tests
- Updated E2E flows for new pages
- Navigation tests

#### Phase 5+ (Monitoring, Performance, Security):
- Performance benchmark suite (k6)
- Security test suite (OWASP ZAP)
- Monitoring integration tests

### 3.5 Test Infrastructure Requirements

| Requirement | Current State | Needed |
|:------------|:-------------|:-------|
| Test database (PostgreSQL) | ✅ docker-compose port 5433 | Good |
| Test Redis | ✅ docker-compose port 6383 | Good |
| Test S3 (MinIO) | ✅ docker-compose port 9002 | Good |
| Test email (MailHog) | ✅ docker-compose port 1025 | Good |
| Java Agent mock server | ❌ None | Need mock for Gateway tests before Agent is ready |
| Stripe mock | ❌ None | Need stripe-mock or test mode keys |
| OpenAI mock | ❌ None | Need mock for AI service tests |
| k6 / Artillery | ❌ Not installed | Need for performance tests |
| OWASP ZAP | ❌ Not installed | Need for security tests |
| Detox (mobile E2E) | ⚠️ Config exists | Verify setup |
| Playwright (web E2E) | ⚠️ Config exists | Verify setup |

---

## APPENDIX A: Complete API Route Inventory

| Method | Path | Auth | Module | Status |
|:-------|:-----|:-----|:-------|:-------|
| POST | /auth/register | No | Auth | ✅ |
| POST | /auth/login | No | Auth | ✅ |
| POST | /auth/refresh | No | Auth | ✅ |
| POST | /auth/password-reset | No | Auth | ✅ |
| POST | /auth/password-reset/confirm | No | Auth | ✅ |
| POST | /auth/2fa/setup | Yes | Auth | ✅ |
| POST | /auth/2fa/enable | Yes | Auth | ✅ |
| POST | /auth/2fa/verify | Partial | Auth | ✅ |
| GET | /auth/sessions | Yes | Auth | ✅ |
| DELETE | /auth/sessions/:id | Yes | Auth | ✅ |
| POST | /api/moments | Yes | Moments | ✅ |
| GET | /api/moments | Yes | Moments | ✅ |
| GET | /api/moments/:id | Yes | Moments | ✅ |
| PUT | /api/moments/:id | Yes | Moments | ✅ |
| DELETE | /api/moments/:id | Yes | Moments | ✅ |
| POST | /api/moments/classify-sphere | Yes | Moments | ⚠️ Needs Java Agent |
| POST | /api/moments/:id/links | Yes | Links | ✅ |
| GET | /api/moments/:id/links | Yes | Links | ✅ |
| DELETE | /api/moments/:id/links/:linkId | Yes | Links | ✅ |
| GET | /api/moments/:id/graph | Yes | Links | ✅ |
| POST | /api/spheres | Yes | Spheres | ✅ |
| GET | /api/spheres | Yes | Spheres | ✅ |
| GET | /api/spheres/:id | Yes | Spheres | ✅ |
| PUT | /api/spheres/:id | Yes | Spheres | ✅ |
| DELETE | /api/spheres/:id | Yes | Spheres | ✅ |
| POST | /api/upload/presigned-url | Yes | Upload | ✅ |
| POST | /api/upload/complete | Yes | Upload | ✅ |
| POST | /api/progressive/init | Yes | Upload | ✅ |
| POST | /api/progressive/chunk | Yes | Upload | ✅ |
| POST | /api/progressive/complete | Yes | Upload | ✅ |
| GET | /api/progressive/status/:id | Yes | Upload | ✅ |
| POST | /api/transcription/transcribe | Yes | Transcription | ⚠️ Node.js fallback |
| POST | /api/transcription/batch | Yes | Transcription | ⚠️ |
| GET | /api/transcription/:momentId | Yes | Transcription | ✅ |
| POST | /api/search | Yes | Search | ⚠️ Semantic needs Agent |
| GET | /api/search/suggestions | Yes | Search | ✅ |
| POST | /api/search/similar | Yes | Search | ⚠️ Needs embeddings |
| POST | /api/search/generate-embedding | Yes | Search | ⚠️ Needs Agent |
| POST | /api/search/generate-reflection | Yes | Search | ⚠️ Needs Agent |
| GET | /api/analytics/dashboard | Yes | Analytics | ✅ |
| GET | /api/analytics/meaning | Yes | Analytics | ✅ |
| GET | /api/analytics/meaning/summary | Yes | Analytics | ✅ |
| GET | /api/analytics/insights | Yes | Analytics | ⚠️ Needs Agent |
| POST | /api/analytics/track | Yes | Analytics | ✅ |
| POST | /api/reports/generate | Yes | Analytics | ✅ |
| GET | /api/reports/:jobId | Yes | Analytics | ✅ |
| GET | /api/analytics/admin/queue-status | Yes* | Analytics | ⚠️ No admin check |
| POST | /api/collaboration/spheres/share | Yes | Collab | ⚠️ No limit check |
| POST | /api/collaboration/invitations/accept | Yes | Collab | ⚠️ No model |
| POST | /api/collaboration/comments | Yes | Collab | ⚠️ No model |
| POST | /api/collaboration/reactions | Yes | Collab | ⚠️ No model |
| POST | /api/collaboration/follow | Yes | Collab | ⚠️ No model |
| POST | /api/privacy/export | Yes | Privacy | ✅ |
| GET | /api/privacy/export/:id/status | Yes | Privacy | ✅ |
| GET | /api/privacy/export/:id/download | Yes | Privacy | ✅ |
| POST | /api/privacy/deletion | Yes | Privacy | ✅ |
| POST | /api/privacy/deletion/verify | Yes | Privacy | ✅ |
| GET | /api/privacy/settings | Yes | Privacy | ✅ |
| PUT | /api/privacy/settings | Yes | Privacy | ✅ |
| POST | /api/privacy/consent | Yes | Privacy | ✅ |
| POST | /api/memory-expansion | Yes | Memory | ⚠️ Needs Agent |
| GET | /api/memory-expansion | Yes | Memory | ✅ |
| GET | /api/memory-expansion/:jobId | Yes | Memory | ✅ |
| GET | /api/memory-expansion/result/:id | Yes | Memory | ✅ |
| POST | /api/memory-expansion/batch | Yes | Memory | ⚠️ Needs Agent |
| GET | /api/billing/usage | Yes | Billing | ✅ |
| GET | /api/billing/limits | Yes | Billing | ✅ |
| GET | /api/billing/subscription | Yes | Billing | ✅ |
| POST | /api/billing/upgrade | Yes | Billing | ✅ |
| POST | /api/billing/downgrade | Yes | Billing | ✅ |
| POST | /api/billing/webhook | No (Stripe sig) | Billing | ✅ |
| POST | /api/reflection/insights | Yes | Reflection | ⚠️ Needs Agent |
| GET | /api/adoption/suggestions | Yes | Adoption | ✅ |
| GET | /api/adoption/onboarding | Yes | Adoption | ✅ |
| POST | /api/adoption/suggestions/interactions | Yes | Adoption | ✅ |
| GET | /api/adoption/engagement | Yes | Adoption | ✅ |
| GET | /api/adoption/reminder-settings | Yes | Adoption | ✅ |
| GET | /api/health | No | Health | ✅ |
| GET | /api/health/detailed | No | Health | ✅ |
| GET | /api/health/ready | No | Health | ✅ |
| GET | /api/health/live | No | Health | ✅ |
| GET | /api/health/circuits | No | Health | ✅ |
| POST | /api/health/circuits/:name/reset | No* | Health | ⚠️ Unprotected |
| GET | /api/health/performance | No | Health | ✅ |
| GET | /api/health/resources | No | Health | ✅ |
| GET | /api/health/database | No | Health | ✅ |
| GET | /api/system/circuit-breakers | No | System | ✅ |
| GET | /api/system/health/detailed | No | System | ✅ |

**Total: 82 endpoints** (17 registered routes, 1 unregistered templates route)

---

## APPENDIX B: Existing Test File Inventory

### Backend Gateway (16 files)
1. `src/__tests__/api.integration.test.ts`
2. `src/__tests__/performance.test.ts`
3. `src/__tests__/privacy.test.ts`
4. `src/__tests__/search.test.ts`
5. `src/__tests__/transcription.test.ts`
6. `src/__tests__/upload.test.ts`
7. `src/__tests__/routes/auth.test.ts`
8. `src/__tests__/services/billing/stripe-service.test.ts`
9. `src/__tests__/lib/circuit-breaker.test.ts`
10. `src/routes/__tests__/auth.test.ts` ⚠️ (duplicate location)
11. `src/routes/__tests__/billing.test.ts`
12. `src/routes/__tests__/moments.test.ts`
13. `src/routes/__tests__/spheres.test.ts`
14. `src/services/__tests__/ai-services.test.ts`
15. `src/services/java-agents/__tests__/classification-service.test.ts`

### Mobile Client (24 files)
16. `__tests__/database/momentRepository.test.ts`
17. `__tests__/integration/uploadFlow.test.ts`
18. `__tests__/services/bandwidthService.test.ts`
19. `__tests__/services/errorHandlerService.test.ts`
20. `__tests__/services/mediaCompressionService.test.ts`
21. `__tests__/services/uploadProgressService.test.ts`
22. `src/__tests__/components/UploadProgressIndicator.test.tsx`
23. `src/__tests__/integration/offlineSync.integration.test.ts`
24. `src/__tests__/integration/offlineSync.integration.test.tsx`
25. `src/__tests__/screens/ImageCaptureScreen.test.tsx`
26. `src/__tests__/screens/UnifiedCaptureScreen.test.tsx`
27. `src/__tests__/screens/VideoRecorderScreen.test.tsx`
28. `src/__tests__/screens/VoiceRecorderScreen.test.tsx`
29. `src/__tests__/services/networkMonitor.test.ts`
30. `src/__tests__/services/offlineQueue.test.ts`
31. `src/__tests__/services/offlineSync.test.ts`
32. `src/__tests__/services/uploadManager.test.ts`
33. `src/__tests__/utils/imageOptimization.test.ts`
34. `src/screens/__tests__/DashboardScreen.test.tsx`
35. `src/screens/__tests__/MomentsScreen.test.tsx`
36-45. E2E: `e2e/01-voice-recording` through `e2e/10-analytics-insights`

### Web Client (16 files)
46. `src/hooks/__tests__/useVoiceCapture.test.ts`
47. `src/hooks/__tests__/useVideoCapture.test.ts`
48. `src/hooks/__tests__/useImageCapture.test.ts`
49. `src/hooks/__tests__/useRealtime.test.ts`
50. `src/pages/__tests__/DashboardPage.test.tsx`
51. `src/pages/__tests__/MomentsPage.test.tsx`
52-61. E2E: `e2e/01-authentication` through `e2e/10-performance`

### Shared Library (2 files)
62. `src/__tests__/validation.test.ts`
63. `src/__tests__/api-client.test.ts`

**Total: 63 test files** (0 Java tests)

---

*End of FlashIt Comprehensive Review, Flow Report & Test Plan*
