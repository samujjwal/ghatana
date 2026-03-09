# FLASHIT — Reverse-Engineered Product Specification

**Document Type:** Complete Product Specification (Reverse-Engineered from Source Code)  
**Version:** 1.0  
**Date:** 2026-03-04  
**Source of Truth:** Codebase at `products/flashit/`  
**Classification:** Internal — Architecture & Product Reference  

---

# 1 Executive Summary

## 1.1 What Flashit Is

Flashit is a **Moment Intelligence Platform** — a full-stack personal context capture system that helps users record, organise, and derive meaning from everyday thoughts, experiences, and media. At its core, every user interaction creates a **Moment**: the platform's fundamental primitive. Around this primitive, Flashit layers AI-powered classification, enrichment, summarization, reflection, pattern detection, and semantic search to transform raw captures into long-term personal intelligence.

## 1.2 Target Users

| Segment | Description |
|---------|-------------|
| **Individual Knowledge Workers** | Professionals capturing daily thoughts, meeting notes, ideas |
| **Reflective Journalers** | Users seeking self-awareness through emotional and behavioural tracking |
| **Creative Professionals** | Writers, designers, researchers capturing multimedia inspiration |
| **Teams (Pro/Teams tier)** | Collaborative groups sharing spheres, commenting, co-editing moments |
| **Students / Lifelong Learners** | Users who capture learning moments and review insights over time |

## 1.3 Primary Use Cases

1. **Multi-modal Moment Capture** — Text, voice (transcription), image, video with AI auto-classification into contextual Spheres
2. **AI-Powered Reflection** — Insight generation, pattern detection, thematic connections across moments
3. **Semantic & Hybrid Search** — Natural language queries against personal moment graph
4. **Mood & Emotional Intelligence** — Track emotional patterns, detect alerts, visualise mood over time
5. **Collaborative Knowledge Sharing** — Share Spheres, co-edit moments, comment, react, follow
6. **Memory Expansion** — AI-driven analysis: summarise, extract themes, identify patterns, find connections
7. **Analytics & Meaning Metrics** — "Return-to-Meaning" rate, language evolution, engagement scores, temporal arcs

## 1.4 Core Differentiator

Flashit treats **Moments as a first-class intelligence primitive** — not just notes or journal entries. Every moment is automatically enriched (tags, entities, sentiment, mood, embeddings), connected via typed links and embeddings, and continuously analysed by AI agents to surface patterns, insights, and recommendations that accumulate into personal knowledge.

---

# 2 System Architecture

## 2.1 System Context (C4 Level 1)

```
┌─────────────────────────────────┐
│          End Users              │
│  (Mobile / Web / API Keys)     │
└────────────┬────────────────────┘
             │ HTTPS
             ▼
┌─────────────────────────────────┐
│     Flashit Platform            │
│  ┌───────────────────────────┐  │
│  │  Fastify Gateway (Node)   │  │
│  │  REST API · JWT · Billing │  │
│  └─────────────┬─────────────┘  │
│                │ HTTP (internal) │
│  ┌─────────────▼─────────────┐  │
│  │Java Agent Service (ActiveJ)│ │
│  │ Classification · NLP ·     │ │
│  │ Embeddings · Reflection ·  │ │
│  │ Transcription              │ │
│  └────────────────────────────┘ │
│                                 │
│  Infrastructure:                │
│  PostgreSQL · Redis · MinIO/S3  │
│  Prometheus · Grafana           │
└─────────────────────────────────┘
             │
             ▼
┌─────────────────────────────────┐
│     External Services           │
│  OpenAI (GPT-4o, Whisper,       │
│  text-embedding-3-small)        │
│  Stripe (Billing)               │
│  SMTP (Email)                   │
│  VAPID (Push Notifications)     │
└─────────────────────────────────┘
```

## 2.2 Containers (C4 Level 2)

| Container | Technology | Port | Responsibility |
|-----------|-----------|------|----------------|
| **React Native Mobile App** | Expo + React Native | — | Offline-first capture, SQLite sync, biometric auth, media pipeline |
| **React Web App** | Vite + React | 3000 | Dashboard, timeline, search, CRDT collaborative editing |
| **Fastify Gateway** | Node.js 20 + Fastify + Prisma | 2900 | REST API, auth, CRUD, billing, route orchestration |
| **Java Agent Service** | Java 21 + ActiveJ | 8090 | AI processing: classification, embeddings, reflection, transcription, NLP |
| **Collaboration Server** | Socket.IO + Redis Adapter | (via Gateway) | Real-time presence, co-editing, live reactions |
| **Notification Worker** | BullMQ + Redis | (background) | Multi-channel notifications: in-app, email, push |
| **Reflection Worker** | BullMQ + Redis | (background) | Async AI reflection job processing |
| **Compliance Worker** | BullMQ + Redis | (background) | GDPR/CCPA audits, backup validation |
| **PostgreSQL** | PostgreSQL 15 | 5432 | Primary data store (Prisma ORM) |
| **Redis** | Redis 7 | 6383 | Cache, rate limiting, sessions, job queues, Socket.IO pub/sub |
| **MinIO / S3** | MinIO (dev) / S3 (prod) | 9002 | Media file storage (presigned URLs) |
| **Prometheus** | Prometheus | 9090 | Metrics collection |
| **Grafana** | Grafana | 3001 | Observability dashboards |

## 2.3 Components (C4 Level 3)

### Gateway Components

| Component | File(s) | Purpose |
|-----------|---------|---------|
| **Moment Routes** | `routes/moments.ts` (589 LOC) | CRUD, AI-assisted sphere classification on create |
| **Sphere Routes** | `routes/spheres.ts` | Sphere management with typed visibility |
| **Search Routes** | `routes/search.ts` (509 LOC) | Hybrid/semantic/text/similar search, embedding generation triggers |
| **Reflection Routes** | `routes/reflection.ts` (430 LOC) | Insights, patterns, connections, weekly/monthly summaries |
| **Analytics Routes** | `routes/analytics.ts` (983 LOC) | Dashboard, meaning metrics, temporal arcs, language evolution, reports |
| **Memory Expansion Routes** | `routes/memory-expansion.ts` (270 LOC) | AI expansion jobs: summarize, themes, patterns, connections |
| **Collaboration Routes** | `routes/collaboration.ts` (1043 LOC) | Share spheres, invitations, comments, reactions, follows |
| **Moment Links Routes** | `routes/moment-links.ts` (627 LOC) | 9 typed link types, graph traversal (depth 1–5) |
| **Auth Routes** | `routes/auth-enhanced.ts` | Register, login, refresh, 2FA, sessions |
| **Billing Routes** | `routes/billing.ts` | Usage tracking, tier limits, Stripe checkout |
| **Upload Routes** | `routes/upload.ts`, `routes/progressive-upload.ts` | Presigned URLs, multipart chunked upload |
| **Transcription Routes** | `routes/transcription.ts` | Audio/video transcription via Java Agent |
| **Notification Routes** | `routes/notifications.ts` | CRUD + read/dismiss, unread count |
| **Admin Routes** | `routes/admin.ts` | User management, content moderation, platform stats |
| **Privacy Routes** | `routes/privacy.ts` | GDPR data export, deletion requests |
| **Template Routes** | `routes/templates.ts` | Moment capture templates |
| **Adoption Routes** | `routes/adoption.ts` | Guided onboarding flows |
| **System Routes** | `routes/system.ts`, `routes/health.ts` | Health, readiness, circuit breaker reset, Prometheus metrics |

### AI Service Components (Gateway-Side)

| Component | File(s) | LOC | Purpose |
|-----------|---------|-----|---------|
| **NLP Service** | `services/ai/nlpService.ts` | ~400 | Auto-tagging, entity extraction, sentiment, mood via GPT-4o-mini |
| **Entity Extractor** | `services/ai/entityExtractor.ts` | 611 | Advanced NER with disambiguation, coreference, Wikidata linking |
| **Conversation Threader** | `services/ai/conversationThreader.ts` | 759 | Temporal + semantic moment threading |
| **Mood Analyzer** | `services/ai/moodAnalyzer.ts` | 1008 | Mood trends, patterns, alerts (15 emotion types) |
| **Summary Generator** | `services/ai/summaryGenerator.ts` | 807 | Daily/weekly/monthly/yearly AI summaries |
| **AI Cache** | `services/ai/ai-cache-service.ts` | 455 | Redis-backed, per-operation TTL, SHA-256 hashing |
| **AI Cost Tracker** | `services/ai/ai-cost-tracking.ts` | 639 | Per-model token pricing, user cost summaries |
| **LLM Service** | `services/llm/llm-service.ts` | ~300 | Multi-provider abstraction (OpenAI, Ollama, Anthropic) |
| **Enhanced Search** | `services/search/enhanced-search-service.ts` | 746 | Hybrid search: PostgreSQL FTS + vector similarity |
| **Faceted Search** | `services/search/facetedSearch.ts` | 821 | Aggregated facets, saved searches, search history |
| **Visual Search** | `services/search/visualSearch.ts` | 729 | Image analysis (objects, scenes, faces, text) |
| **Vector Embedding** | `services/embeddings/vector-service.ts` | 557 | BullMQ async embedding generation, text preprocessing |

### Java Agent Components

| Component | File | Purpose |
|-----------|------|---------|
| **ClassificationService** | `service/ClassificationService.java` (~140 LOC) | GPT-4o sphere classification with structured prompts |
| **EmbeddingService** | `service/EmbeddingService.java` (~150 LOC) | OpenAI text-embedding-3-small, in-memory cosine similarity |
| **ReflectionService** | `service/ReflectionService.java` (~145 LOC) | GPT-4o insight/pattern/connection generation |
| **TranscriptionService** | `service/TranscriptionService.java` (~170 LOC) | Whisper API, multipart upload, timestamped segments |
| **NLPService** | `service/NLPService.java` (~200 LOC) | Entity extraction, sentiment analysis, mood detection |
| **AgentHttpRouter** | `http/AgentHttpRouter.java` | 17 HTTP endpoints, generic `parseAndExecute` handler |
| **AgentConfig** | `config/AgentConfig.java` | Environment-based configuration |
| **26 DTO Records** | `dto/*.java` | Request/response contracts for all agent operations |

### Backend Module Components

| Module | File | LOC | Purpose |
|--------|------|-----|---------|
| **Reflection Intelligence** | `modules/intelligence/reflection-service.ts` | 853 | BullMQ async reflection with GPT-4, prompt templates, citation tracking |
| **Collaboration Server** | `modules/collaboration/collaboration-server.ts` | 856 | Socket.IO real-time: presence, co-editing, live events |
| **Notification Service** | `modules/notification/notification-service.ts` | 855 | Multi-channel (in-app, email, push, SMS), templates, quiet hours, batching |
| **Compliance Service** | `modules/compliance/compliance-service.ts` | 868 | GDPR/CCPA/ISO27001 auditing, backup validation, data governance |
| **Deletion Service** | `modules/compliance/deletion-service.ts` | — | Account/moment deletion scheduling |
| **Export Service** | `modules/compliance/export-service.ts` | — | Data export (JSON/CSV) |
| **Security Review** | `modules/compliance/security-review-service.ts` | — | Security posture reviews |

---

# 3 Domain Model

## 3.1 Core Entities

### 3.1.1 Moment

The **fundamental primitive** of the entire platform.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK, auto-gen | Unique moment identifier |
| `userId` | UUID | FK → User, NOT NULL | Owner of the moment |
| `sphereId` | UUID | FK → Sphere, NOT NULL | Container sphere (can be auto-classified) |
| `contentText` | Text | NOT NULL, min 1 char | Primary text content |
| `contentTranscript` | Text | nullable | AI-generated audio/video transcript |
| `contentType` | Enum | TEXT, VOICE, VIDEO, IMAGE, MIXED | Capture modality |
| `emotions` | String[] | — | User-tagged or AI-detected emotions |
| `tags` | String[] | — | User-tagged or AI-auto-tagged |
| `intent` | String(500) | nullable | User's stated intent |
| `sentimentScore` | Decimal(3,2) | -1.00 to 1.00 | AI-computed sentiment polarity |
| `importance` | Int | 1–5, nullable | User-assigned importance rating |
| `entities` | String[] | — | AI-extracted named entities |
| `capturedAt` | Timestamptz | NOT NULL | When the moment occurred |
| `ingestedAt` | Timestamptz | auto, NOT NULL | When the system received it |
| `updatedAt` | Timestamptz | auto | Last modification |
| `deletedAt` | Timestamptz | nullable | Soft-delete timestamp |
| `metadata` | JSONB | nullable | Extensible metadata envelope |
| `version` | Int | default 1 | Optimistic concurrency |

**Lifecycle:** Created → (AI Enriched: tags, entities, sentiment, embedding) → (Linked) → (Reflected upon) → Soft-Deleted

**Relationships:**
- Belongs to one User (owner)
- Belongs to one Sphere (container)
- Has many MomentMedia (attachments)
- Has many MediaReferences (S3 objects)
- Has many MomentEmbeddings (vector representations)
- Has many MomentLinks as source (outgoing typed links)
- Has many MomentLinks as target (incoming typed links)
- Has many AuditEvents
- Referenced by AIInsight, MemoryExpansion, Comment, Reaction

**Evidence:** `schema.prisma` lines 177–222, `routes/moments.ts`

---

### 3.1.2 Sphere

Contextual container for organising moments.

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK | — |
| `userId` | UUID | FK → User | Creator/owner |
| `name` | String(255) | NOT NULL | Display name |
| `description` | Text | nullable | Purpose description |
| `type` | String(50) | Personal, Work, Health, Learning, Social, Creative, Custom | Category |
| `visibility` | String(50) | Private, Shared, Public | Access scope |
| `createdAt` / `updatedAt` / `deletedAt` | Timestamptz | — | Lifecycle timestamps |

**Relationships:** Has many Moments, SphereAccess, SphereEmbeddings, AuditEvents

**Evidence:** `schema.prisma` lines 107–126

---

### 3.1.3 User

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | PK |
| `email` | String(255) | Unique, indexed |
| `passwordHash` | String(255) | bcrypt hash |
| `displayName` | String(255) | Optional display name |
| `role` | Enum | USER, OPERATOR, ADMIN, SUPER_ADMIN |
| `twoFactorEnabled` | Boolean | MFA status |
| `emailVerified` | Boolean | Email confirmation |
| `subscriptionTier` | String(50) | free, pro, teams |
| `stripeCustomerId` / `stripeSubscriptionId` | String | Stripe integration |
| `subscriptionStatus` / `subscriptionEndsAt` | — | Billing state |

**Relationships:** Spheres, Moments, SphereAccess, AuditEvents, RefreshTokens, Sessions, AIInsights, MemoryExpansions, AITokenUsage, Comments, Reactions, Follows, Invitations, Notifications, Templates, Reports, DataExportRequests, DeletionRequests, ApiKeys

**Evidence:** `schema.prisma` lines 49–110

---

### 3.1.4 MomentLink

Typed relationship between two moments.

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | PK |
| `sourceMomentId` | UUID | FK → Moment |
| `targetMomentId` | UUID | FK → Moment |
| `linkType` | String(50) | 9 types (see below) |
| `createdBy` | UUID | FK → User |
| `deletedAt` | Timestamptz | Soft-delete |

**Link Types:** `related`, `follows`, `precedes`, `references`, `causes`, `similar`, `contradicts`, `elaborates`, `summarizes`

**Evidence:** `schema.prisma` lines 243–262, `routes/moment-links.ts`

---

### 3.1.5 AIInsight

AI-generated insight persisted for later retrieval.

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | PK |
| `userId` | UUID | FK → User |
| `insightType` | String(100) | Category of insight |
| `title` | String(500) | Human-readable title |
| `content` | Text | Full insight content |
| `confidence` | Decimal(3,2) | AI confidence score |
| `relatedMoments` | String[] | Moment IDs cited |
| `metadata` | JSONB | Additional context |

**Evidence:** `schema.prisma` lines 563–580

---

### 3.1.6 MemoryExpansion

AI-driven expansion of a moment (summarise, themes, patterns, connections).

| Field | Type | Description |
|-------|------|-------------|
| `id` | UUID | PK |
| `userId` | UUID | FK → User |
| `momentId` | UUID | FK → Moment |
| `expansionType` | String(100) | summarize, extract_themes, identify_patterns, find_connections |
| `content` | Text | Expansion result |
| `metadata` | JSONB | Processing metadata |

**Evidence:** `schema.prisma` lines 582–597

---

### 3.1.7 MomentEmbedding / SphereEmbedding

Vector representations for semantic operations.

| Field | Type | Description |
|-------|------|-------------|
| `embeddingVector` | Bytes (pgvector) | High-dimensional vector |
| `embeddingModelId` | UUID | FK → EmbeddingModel |
| `contentType` | String(50) | What was embedded |
| `tokenCount` | Int | Input token count |

**Evidence:** `schema.prisma` lines 267–324

---

### 3.1.8 Supporting Entities

| Entity | Purpose | Evidence |
|--------|---------|---------|
| **SphereAccess** | Role-based sphere sharing (OWNER, EDITOR, VIEWER) | `schema.prisma` lines 128–148 |
| **MediaReference** | S3 object tracking (bucket, key, mimeType, uploadStatus) | `schema.prisma` lines 150–175 |
| **MomentMedia** | M2M join: Moment ↔ MediaReference with type and order | `schema.prisma` lines 224–241 |
| **EmbeddingModel** | Model registry (provider, dimensions, cost) | `schema.prisma` lines 264–285 |
| **AuditEvent** | 22 event types for full audit trail | `schema.prisma` lines 326–358 |
| **Comment** | Threaded comments on moments (parent-child tree) | `schema.prisma` lines 647–666 |
| **Reaction** | Emoji reactions on moments (unique per user+emoji) | `schema.prisma` lines 668–680 |
| **Follow** | User follow/unfollow with timestamp | `schema.prisma` lines 682–696 |
| **Invitation** | Sphere sharing invitations with token + expiry | `schema.prisma` lines 698–722 |
| **Notification** | Multi-channel notifications with read/dismiss state | `schema.prisma` lines 724–749 |
| **Template** | Reusable moment capture templates with categories | `schema.prisma` lines 751–770 |
| **Report** | Generated reports (PDF/CSV/JSON) with async processing | `schema.prisma` lines 772–792 |
| **DataExportRequest** | GDPR data export requests | `schema.prisma` lines 794–813 |
| **DeletionRequest** | GDPR data deletion with confirmation + grace period | `schema.prisma` lines 815–834 |
| **ApiKey** | Scoped API keys with rotation support | `schema.prisma` lines 836–853 |
| **AITokenUsage** | Per-operation token and cost tracking | `schema.prisma` lines 599–625 |
| **TranscriptionUsage** | Audio transcription usage tracking | `schema.prisma` lines 545–561 |
| **UserTierSettings** | Per-user rate limits and quotas | `schema.prisma` lines 487–510 |
| **RefreshToken / UserSession** | JWT refresh + session management | `schema.prisma` lines 360–438 |
| **TwoFactorAuth** | TOTP 2FA with backup codes | `schema.prisma` lines 400–417 |
| **PasswordResetToken / EmailVerificationToken** | Self-service account recovery | `schema.prisma` lines 440–485 |
| **SecurityAuditLog** | Security-specific event logging (severity, IP, device) | `schema.prisma` lines 512–543 |
| **PushSubscription** | Web Push endpoints per device | `schema.prisma` lines 527–545 |

---

# 4 Functional Requirements

## 4.1 Moment Capture

| ID | Description | Inputs | Processing | Outputs | Evidence |
|----|-------------|--------|-----------|---------|---------|
| FLASHIT-FR-001 | Create text moment | `content.text`, `sphereId?`, signals (emotions, tags, intent, importance, entities), metadata | Zod validation → AI sphere classification if no sphereId → access check → Prisma create → audit event → async embedding generation | Moment object with sphere info | `routes/moments.ts` |
| FLASHIT-FR-002 | Create voice moment | Audio file via presigned URL | Upload to S3 → transcription via Java Agent (Whisper) → text moment creation with transcript | Moment + transcript | `routes/upload.ts`, `routes/transcription.ts` |
| FLASHIT-FR-003 | Create image moment | Image file via presigned URL | Upload to S3 → optional visual analysis → moment creation | Moment + media reference | `routes/upload.ts`, `routes/progressive-upload.ts` |
| FLASHIT-FR-004 | Create video moment | Video file via progressive upload | Multipart chunked upload to S3 → transcription → moment creation | Moment + transcript + media | `routes/progressive-upload.ts` |
| FLASHIT-FR-005 | AI sphere auto-classification | Content + emotions + tags + intent + available spheres | Java Agent: GPT-4o structured prompt → confidence-scored sphere recommendation; falls back to local heuristic if Java unavailable | ClassificationResponse with sphereId, confidence, reasoning, alternatives | `services/java-agents/classification-service.ts` |
| FLASHIT-FR-006 | List moments (paginated) | sphereIds[], query, tags[], emotions[], startDate, endDate, limit, cursor | Prisma query with cursor pagination, multi-field filtering, latest-first ordering | Paginated moment list | `routes/moments.ts` |
| FLASHIT-FR-007 | Update moment | Moment ID + partial body | Ownership check → Prisma update → audit event | Updated moment | `routes/moments.ts` |
| FLASHIT-FR-008 | Soft-delete moment | Moment ID | Ownership check → set deletedAt → audit event | Success confirmation | `routes/moments.ts` |

**Error Cases:** Invalid sphere access (403), sphere deleted (403), missing write permission (403), classification failure (500 with message), Zod validation errors (400)

---

## 4.2 Moment Context Enrichment

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-010 | Auto-tagging | NLP Service analyses content → 3–7 confidence-scored tags | `services/ai/nlpService.ts` |
| FLASHIT-FR-011 | Named entity extraction | GPT-4o-mini NER: PERSON, ORG, LOCATION, DATE, EVENT, CONCEPT, EMOTION with confidence + offsets + coreference + Wikidata linking | `services/ai/entityExtractor.ts`, Java `NLPService.extractEntities()` |
| FLASHIT-FR-012 | Sentiment analysis | Label (positive/negative/neutral/mixed) + polarity scores (positive, negative, neutral) | Java `NLPService.analyzeSentiment()` |
| FLASHIT-FR-013 | Mood detection | Primary mood + secondary moods + intensity (0–1) | Java `NLPService.detectMood()`, `services/ai/moodAnalyzer.ts` |
| FLASHIT-FR-014 | Embedding generation | text-embedding-3-small → 1536-dimension vector → stored in `moment_embeddings` | Java `EmbeddingService`, `services/embeddings/vector-service.ts` |
| FLASHIT-FR-015 | Conversation threading | Temporal proximity + semantic similarity → group moments into threads | `services/ai/conversationThreader.ts` |

---

## 4.3 Search

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-020 | Hybrid search | 4 modes: `text` (PostgreSQL FTS via `ts_rank`), `semantic` (vector cosine similarity), `hybrid` (weighted 0.3 text / 0.7 semantic), `similar` (nearest-neighbor) | `services/search/enhanced-search-service.ts` |
| FLASHIT-FR-021 | Faceted search | Aggregated facets: emotions, tags, spheres, contentTypes, dateRange, importance, hasMedia, hasTranscript + saved searches + history | `services/search/facetedSearch.ts` |
| FLASHIT-FR-022 | Visual search | Image analysis (objects, scenes, colors, text, faces, style) → vector similarity match | `services/search/visualSearch.ts` |
| FLASHIT-FR-023 | Search suggestions | Autocomplete from tags, spheres, recent queries | `routes/search.ts` |
| FLASHIT-FR-024 | Similar moments | Vector similarity by moment ID | `routes/search.ts` |

**Boost Factors:** Recency (exponential decay), importance, emotional richness  
**Result Enhancement:** `<mark>` highlighting via `TextHighlighter`

---

## 4.4 AI Reflection

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-030 | Generate insights | Fetch sphere moments → Java Agent `ReflectionService` (GPT-4o, temp=0.7) → structured JSON: summary, insights[], themes[], actionItems[] → persist as `AIInsight` | `routes/reflection.ts`, Java `ReflectionService` |
| FLASHIT-FR-031 | Detect patterns | Minimum 5 moments required → pattern analysis: `PatternInfo{pattern, frequency, confidence, examples}` | `services/java-agents/reflection-client.ts` |
| FLASHIT-FR-032 | Find connections | Minimum 2 moments → cross-moment thematic connections: `ConnectionInfo{momentId, relationship, confidence}` | `services/java-agents/reflection-client.ts` |
| FLASHIT-FR-033 | Weekly summary | Last 7 days of moments in sphere → comprehensive AI reflection | `routes/reflection.ts` |
| FLASHIT-FR-034 | Monthly summary | Last 30 days of moments in sphere → comprehensive AI reflection | `routes/reflection.ts` |
| FLASHIT-FR-035 | Memory expansion | 4 types: summarize, extract_themes, identify_patterns, find_connections → BullMQ async job → result stored as `MemoryExpansion` | `routes/memory-expansion.ts` |

**Billing Gate:** All reflection operations are gated by subscription tier AI insight limits (Free: 5/mo, Pro: 100/mo, Teams: unlimited)

---

## 4.5 Moment Linking & Graph

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-040 | Create typed link | Source + target moment IDs + link type (9 types) → access check on both → Prisma create → audit | `routes/moment-links.ts` |
| FLASHIT-FR-041 | Graph traversal | Starting moment + direction (outgoing/incoming/both) + max depth (1–5) → BFS/DFS traversal of link graph | `routes/moment-links.ts` |
| FLASHIT-FR-042 | AI link suggestions | Surface `LinkSuggestionsCard` in UI based on semantic similarity | `components/Links/LinkSuggestionsCard.tsx` |

**Link Types:** `related` · `follows` · `precedes` · `references` · `causes` · `similar` · `contradicts` · `elaborates` · `summarizes`

---

## 4.6 Analytics & Meaning

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-050 | Analytics dashboard | Daily data, sphere activity, trends, top tags/categories, mood distribution, word cloud, hourly/weekday distribution | `routes/analytics.ts`, `services/analytics.ts` |
| FLASHIT-FR-051 | Return-to-Meaning metrics | Track how often users revisit and annotate past moments | `routes/analytics.ts` (meaning endpoints) |
| FLASHIT-FR-052 | Temporal arcs | Cross-time referencing analysis (how moments connect over time) | `routes/analytics.ts` |
| FLASHIT-FR-053 | Language evolution | Track vocabulary and expression changes over time with privacy controls | `routes/analytics.ts`, `components/LanguageEvolution/` |
| FLASHIT-FR-054 | Engagement scoring | 0–100 score based on activity frequency + streak calculation (current + longest) | `services/analytics.ts` |
| FLASHIT-FR-055 | Report generation | PDF/CSV/JSON reports with async processing | `routes/analytics.ts` |

---

## 4.7 Collaboration

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-060 | Share sphere | Email-based sharing → existing user gets SphereAccess, new user gets Invitation with token + expiry | `routes/collaboration.ts` |
| FLASHIT-FR-061 | Threaded comments | Create comments on moments with parent-child threading, mentions | `routes/collaboration.ts` |
| FLASHIT-FR-062 | Reactions | Toggle reactions (like, love, insightful, helpful, inspiring) on moments | `routes/collaboration.ts` |
| FLASHIT-FR-063 | Follow users | Follow/unfollow with activity feed | `routes/collaboration.ts` |
| FLASHIT-FR-064 | Real-time presence | Socket.IO: user status (active/idle/away), current location, cursor position | `modules/collaboration/collaboration-server.ts` |
| FLASHIT-FR-065 | Co-editing | CRDT-based conflict-free collaborative moment editing | `services/crdtEngine.ts`, `hooks/useCollaborativeEdit.ts` |
| FLASHIT-FR-066 | Notification preferences | Per-type enable/disable, digest frequency (never/daily/weekly/monthly), quiet hours (timezone-aware) | `routes/collaboration.ts` |

**Billing Gate:** Collaborator limits: Free: 0, Pro: 25, Teams: unlimited

---

## 4.8 Mood & Emotional Intelligence

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-070 | Mood tracking | 15 emotion types tracked per moment (joy, sadness, anger, fear, surprise, love, anxiety, calm, etc.) | `services/ai/moodAnalyzer.ts` |
| FLASHIT-FR-071 | Mood trends | MoodTrend: improving / stable / declining | `services/ai/moodAnalyzer.ts` |
| FLASHIT-FR-072 | Mood patterns | MoodPattern: recurring, trend, sudden_change, cycle | `services/ai/moodAnalyzer.ts` |
| FLASHIT-FR-073 | Mood alerts | `prolonged_negative`, `sudden_drop`, `high_anxiety`, `emotional_volatility` with configurable sensitivity | `services/ai/moodAnalyzer.ts` |

**Privacy:** Opt-in only, resource recommendations for concerning patterns

---

## 4.9 Transcription

| ID | Description | Processing | Evidence |
|----|-------------|-----------|---------|
| FLASHIT-FR-080 | Audio transcription | Java Agent → Whisper API → timestamped segments with per-segment confidence | Java `TranscriptionService` |
| FLASHIT-FR-081 | Batch transcription | Multiple moments queued; background processing | `services/java-agents/transcription-client.ts` |
| FLASHIT-FR-082 | Re-transcription | Retry with different params or after model upgrade | `services/java-agents/transcription-client.ts` |
| FLASHIT-FR-083 | Auto-discovery | Find moments needing transcription (has audio/video media, no transcript yet) | `services/java-agents/transcription-client.ts` |

---

## 4.10 Authentication & Security

| ID | Description | Evidence |
|----|-------------|---------|
| FLASHIT-FR-090 | JWT auth with refresh tokens | `routes/auth-enhanced.ts`, `model RefreshToken` |
| FLASHIT-FR-091 | TOTP 2FA with backup codes | `model TwoFactorAuth` |
| FLASHIT-FR-092 | RBAC (USER, OPERATOR, ADMIN, SUPER_ADMIN) | `enum UserRole` |
| FLASHIT-FR-093 | Session management (device fingerprint, IP tracking) | `model UserSession` |
| FLASHIT-FR-094 | API key management (scoped, rotatable) | `model ApiKey`, `routes/api-keys.ts` |
| FLASHIT-FR-095 | Account lockout (failed attempts → locked until) | User model fields |
| FLASHIT-FR-096 | Biometric auth (mobile) | `services/biometricAuth.ts` |

---

## 4.11 Billing & Subscriptions

| ID | Description | Evidence |
|----|-------------|---------|
| FLASHIT-FR-100 | 3-tier model: Free / Pro ($9.99/mo) / Teams ($24.99/mo) | README, `routes/billing.ts` |
| FLASHIT-FR-101 | Stripe integration (webhook-driven state changes) | `routes/billing.ts` |
| FLASHIT-FR-102 | Per-tier limits (moments, spheres, storage, transcription, AI insights, collaborators, memory expansions) | `model UserTierSettings` |
| FLASHIT-FR-103 | Usage tracking dashboard | `routes/billing.ts` |

---

## 4.12 Compliance & Privacy

| ID | Description | Evidence |
|----|-------------|---------|
| FLASHIT-FR-110 | GDPR data export (JSON/CSV) | `model DataExportRequest`, `modules/compliance/export-service.ts` |
| FLASHIT-FR-111 | GDPR data deletion (scheduled, confirmed, executed) | `model DeletionRequest`, `modules/compliance/deletion-service.ts` |
| FLASHIT-FR-112 | GDPR/CCPA/ISO27001 compliance auditing | `modules/compliance/compliance-service.ts` |
| FLASHIT-FR-113 | Audit trail (22 event types) | `model AuditEvent`, `enum AuditEventType` |
| FLASHIT-FR-114 | Security audit logging (severity, IP, success/fail) | `model SecurityAuditLog` |
| FLASHIT-FR-115 | 30-day audit log retention (free tier) | `scheduler.ts` |

---

# 5 Moment Intelligence Requirements

This section evaluates the **10-stage Moment Intelligence lifecycle** against the implementation.

## 5.1 Moment Capture ✅ IMPLEMENTED

**Status: Complete**

| Method | Implementation | Evidence |
|--------|---------------|---------|
| Manual text input | Web + Mobile capture screens | `CapturePage.tsx`, `CaptureScreen.tsx` |
| Voice capture | Voice recorder → Whisper transcription | `VoiceRecorder.tsx`, `VoiceRecorderScreen.tsx` |
| Image capture | Camera/gallery → presigned S3 upload | `ImageCapture.tsx`, `ImageCaptureScreen.tsx` |
| Video capture | Video recorder → progressive upload | `VideoRecorder.tsx`, `VideoRecorderScreen.tsx` |
| Template-based capture | Moment templates | `routes/templates.ts`, `TemplateLibrary.tsx` |
| Offline capture (mobile) | SQLite local DB → queue-based sync | `database/`, `syncService.ts`, `offlineQueue.ts` |

---

## 5.2 Moment Storage ✅ IMPLEMENTED

**Status: Complete**

| Aspect | Implementation |
|--------|---------------|
| Primary store | PostgreSQL 15 via Prisma ORM |
| Media store | MinIO/S3 with presigned URLs |
| Vector store | `moment_embeddings` table with pgvector-compatible bytes |
| Mobile offline | SQLite with full schema + sync queue |
| Caching | Redis 7 (sessions, rate limiting, AI cache) |

---

## 5.3 Moment Context Enrichment ✅ IMPLEMENTED

**Status: Complete**

| Enrichment | Implementation | Trigger |
|------------|---------------|---------|
| Auto-tags | NLP Service (GPT-4o-mini) → 3–7 tags per moment | On create or explicit |
| Named entities | EntityExtractor: 7 entity types with disambiguation, coreference, Wikidata linking | On create or explicit |
| Sentiment | Java Agent `NLPService.analyzeSentiment()` → polarity scores | On create or explicit |
| Mood | Java Agent `NLPService.detectMood()` + MoodAnalyzer patterns | On create |
| Embeddings | text-embedding-3-small → 1536d vector | Async via BullMQ on create |
| Conversation threads | ConversationThreader: temporal + semantic clustering | On demand |

---

## 5.4 Moment Summarization ✅ IMPLEMENTED

**Status: Complete (Multi-Tier)**

| Summary Type | Implementation | Evidence |
|-------------|---------------|---------|
| Daily summary | `SummaryGeneratorService` with parallel component generation | `services/ai/summaryGenerator.ts` |
| Weekly summary | `/api/reflection/weekly` endpoint → Java Agent | `routes/reflection.ts` |
| Monthly summary | `/api/reflection/monthly` endpoint → Java Agent | `routes/reflection.ts` |
| Yearly summary | `SummaryGeneratorService` (period: yearly) | `services/ai/summaryGenerator.ts` |
| Custom range | `SummaryGeneratorService` (period: custom) | `services/ai/summaryGenerator.ts` |
| Memory expansion (summarize) | Async BullMQ job → AI summarization of individual moment | `routes/memory-expansion.ts` |

**Components per Summary:** Overview narrative, EmotionAnalysis, TopicCluster[], Highlight[] (achievement/milestone/insight/memorable/growth), Recommendation[], word cloud, sphere breakdown, key moment selection

**Versioning:** ⚠️ **PARTIAL** — Summaries are generated on demand and stored as `AIInsight` records but do not have explicit version tracking. Re-generating a summary creates a new record rather than versioning an existing one.

---

## 5.5 Moment Analysis ✅ IMPLEMENTED

**Status: Complete**

| Analysis Type | Method | Evidence |
|--------------|--------|---------|
| Pattern detection | Java Agent `ReflectionService.detectPatterns()` (LLM-assisted) | `services/java-agents/reflection-client.ts` |
| Trend detection | MoodAnalyzer trend analysis (statistical + LLM) | `services/ai/moodAnalyzer.ts` |
| Behaviour analysis | MoodAnalyzer pattern engine (recurring, trend, sudden_change, cycle) | `services/ai/moodAnalyzer.ts` |
| Correlation analysis | Java Agent connection detection (LLM-assisted) | Java `ReflectionService` |
| Cluster analysis | Conversation threading (temporal + semantic clustering) | `services/ai/conversationThreader.ts` |
| Anomaly detection | Mood alerts: `prolonged_negative`, `sudden_drop`, `high_anxiety`, `emotional_volatility` | `services/ai/moodAnalyzer.ts` |
| Language evolution | Vocabulary and expression change tracking over time | `routes/analytics.ts` |
| Temporal arcs | Cross-time referencing analysis | `routes/analytics.ts` |

**Analysis Methods:**
- ✅ LLM-assisted (primary via GPT-4o / GPT-4o-mini)
- ✅ Statistical (mood trends, engagement scoring)
- ⚠️ Rule-based (basic heuristic fallback in classification)
- ❌ Traditional ML-based models (not implemented — all ML work delegated to LLM APIs)

---

## 5.6 Pattern Detection ✅ IMPLEMENTED

**Status: Complete**

Implemented via `ReflectionService.detectPatterns()`:
- Output: `PatternInfo { pattern, frequency, confidence, examples }`
- Minimum threshold: 5 moments required
- Detection is LLM-assisted (GPT-4o, temp=0.7)
- Mood-specific patterns via MoodAnalyzer (statistical + LLM hybrid)

---

## 5.7 Insight Generation ✅ IMPLEMENTED

**Status: Complete**

| Insight Type | Implementation |
|-------------|---------------|
| Behavioural insights | `ReflectionService.generateInsights()` → structured JSON with themes, action items |
| Mood insights | `MoodAnalyzer.getMoodInsights()` → trends, patterns, alerts |
| Analytical summaries | `SummaryGeneratorService` → emotion analysis, topic clusters, highlights, recommendations |
| Memory expansion insights | 4 expansion types: summarize, extract_themes, identify_patterns, find_connections |
| Connection insights | `ReflectionService.findConnections()` → cross-moment relationship detection |

**Persistence:** Insights stored as `AIInsight` records with type, title, content, confidence score, related moment IDs

---

## 5.8 Recommendation Generation ⚠️ PARTIALLY IMPLEMENTED

**Status: Partial — Present within summaries but no standalone recommendation engine**

| Aspect | Status | Evidence |
|--------|--------|---------|
| Recommendations within summaries | ✅ | `SummaryGeneratorService` generates `Recommendation[]` as part of summary output |
| Action items from reflection | ✅ | `ReflectionResponse.actionItems[]` |
| Mood-based resource recommendations | ✅ | `MoodAnalyzer` suggests resources for concerning patterns |
| Link suggestions | ✅ | `LinkSuggestionsCard.tsx` suggests connections between moments |
| **Standalone recommendation engine** | ❌ **MISSING** | No dedicated service that proactively pushes recommendations |
| **Habit suggestions** | ❌ **MISSING** | No temporal activity pattern analysis for habit formation |
| **Productivity recommendations** | ❌ **MISSING** | No workflow or productivity-focused analysis |
| **Recommendation ranking** | ❌ **MISSING** | No scoring/ranking system for recommendations |
| **Feedback loop** | ❌ **MISSING** | No mechanism to track whether recommendations were acted upon |

**→ Flagged as CRITICAL MISSING ARCHITECTURE COMPONENT (see §8)**

---

## 5.9 Knowledge Graph Formation ⚠️ PARTIALLY IMPLEMENTED

**Status: Partial — Graph primitives exist but no unified knowledge graph layer**

| Aspect | Status | Evidence |
|--------|--------|---------|
| Typed moment links (9 types) | ✅ | `model MomentLink`, `routes/moment-links.ts` |
| Graph traversal (depth 1–5) | ✅ | `routes/moment-links.ts` |
| Sphere → Moment hierarchy | ✅ | Prisma relations |
| User → Follow → User relationships | ✅ | `model Follow` |
| Named entity extraction | ✅ | `services/ai/entityExtractor.ts` (Wikidata IDs) |
| Conversation threads | ✅ | `services/ai/conversationThreader.ts` |
| **Cross-moment topic graph** | ❌ **MISSING** | No persistent topic/concept nodes that moments connect to |
| **Named entity graph** | ❌ **MISSING** | Entities are extracted but not linked into a queryable graph |
| **Knowledge graph visualisation** | ❌ **MISSING** | `TimelineArcView.tsx` exists but no full graph view |
| **Graph-based reasoning** | ❌ **MISSING** | No inference or path-finding over the knowledge graph |
| **Long-term intelligence accumulation** | ❌ **MISSING** | No system that continuously builds and refines the graph |

**→ Flagged as MISSING ARCHITECTURE COMPONENT — recommend a Knowledge Intelligence Layer (see §8)**

---

## 5.10 Long-Term Intelligence Accumulation ⚠️ PARTIALLY IMPLEMENTED

**Status: Partial — Individual intelligence operations exist but no accumulation system**

| Aspect | Status |
|--------|--------|
| Individual insights generated and persisted | ✅ |
| Memory expansions persisted | ✅ |
| Mood history tracked | ✅ |
| Language evolution tracked | ✅ |
| **Cross-session learning** | ❌ MISSING |
| **Personalized models** | ❌ MISSING |
| **Progressive insight refinement** | ❌ MISSING |
| **Intelligence quality scoring** | ❌ MISSING |

---

# 6 Non-Functional Requirements

## 6.1 Performance

| Aspect | Implementation | Evidence |
|--------|---------------|---------|
| API rate limiting | Per-tier limits: 10/100/1000 requests per min/hour/day (free tier) | `model UserTierSettings` |
| Circuit breakers | Java Agent calls wrapped in circuit breakers with graceful degradation | `services/java-agents/agent-client.ts`, `lib/circuit-breaker.ts` |
| Connection pooling | Prisma default pool | `lib/prisma.ts` |
| AI response caching | Redis-backed with per-operation TTLs (6h–30d) | `services/ai/ai-cache-service.ts` |
| Async processing | BullMQ job queues for embeddings, reflections, notifications, compliance | Various workers |
| Progressive upload | Multipart chunked upload for files >50MB | `routes/progressive-upload.ts` |
| Database indexing | 60+ indexes across 25+ models | `schema.prisma` |
| Performance monitoring | Prometheus metrics + Grafana dashboards | `plugins/prometheus.ts`, `monitoring/` |
| Bandwidth-aware sync | Mobile adaptive sync based on network quality | `services/adaptiveSync.ts` |
| Battery optimization | Mobile battery-aware sync scheduling | `services/batteryOptimizer.ts` |

## 6.2 Scalability

| Aspect | Implementation |
|--------|---------------|
| Horizontal: Gateway | Stateless Fastify instances behind load balancer |
| Horizontal: Java Agent | Stateless ActiveJ instances (in-memory embeddings are ephemeral) |
| Horizontal: Real-time | Redis adapter for Socket.IO (pub/sub across instances) |
| Vertical: Database | PostgreSQL read replicas planned (Phase 4 in completion plan) |
| Queue scaling | BullMQ workers independently scalable |
| Storage | S3/MinIO: object storage scales independently |
| Multi-tenancy | `TenantIsolationHttpFilter` on Java Agent |

## 6.3 Reliability

| Aspect | Implementation |
|--------|---------------|
| Circuit breakers | Java Agent HTTP calls: open after N failures, graceful degradation paths |
| Retry with backoff | BullMQ exponential backoff for failed jobs |
| Graceful degradation | Gateway continues without Java Agent (local heuristic classification, degraded AI) |
| Offline-first mobile | SQLite + sync queue with conflict resolution (last-write-wins) |
| Health checks | `/health`, `/health/detailed`, `/health/ready`, `/health/live` (K8s compatible) |
| Background job resilience | Redis-backed BullMQ with persistence |

## 6.4 Security

| Aspect | Implementation |
|--------|---------------|
| Authentication | JWT (access + refresh tokens) with device fingerprinting |
| MFA | TOTP 2FA with encrypted backup codes |
| Authorization | RBAC (4 roles) + sphere-level ACL (OWNER/EDITOR/VIEWER) |
| Rate limiting | Per-tier rate limits, account lockout after failed attempts |
| Audit trail | 22-type audit events + security-specific audit log with severity |
| Input validation | Zod schemas on all endpoints |
| Session management | Device tracking, IP logging, session revocation |
| API key security | SHA-256 hashed keys with scopes and expiration |
| CORS | Configured via Fastify CORS plugin |
| Encryption | Passwords: bcrypt; Transport: HTTPS; Storage: S3 server-side encryption |

## 6.5 Privacy

| Aspect | Implementation |
|--------|---------------|
| GDPR compliance | Data export + deletion requests with scheduled execution + confirmation |
| CCPA compliance | Compliance auditing and reporting |
| Soft deletes | All major entities support `deletedAt` |
| Data retention | Configurable audit log retention (30-day for free tier) |
| Mood privacy | Opt-in mood tracking with configurable sensitivity |
| Language privacy | Language evolution analysis with privacy settings |
| Backup validation | Automated backup integrity testing |

## 6.6 Observability

| Aspect | Implementation |
|--------|---------------|
| Metrics | Prometheus scraping Gateway (:2900/metrics) + Agent (:8090/metrics) |
| Dashboards | Pre-provisioned Grafana: request rates, latency percentiles, error rates, JVM heap, Redis, circuit breakers |
| Logging | SLF4J+Log4j2 (Java), structured JSON logger (Node) |
| Tracing | OpenTelemetry tracing plugin | 
| AI cost monitoring | Per-operation token tracking with cost summaries |
| Error tracking | Error handler middleware with structured error responses |
| Performance monitoring | Custom performance monitoring service |

---

# 7 Extensibility Model

## 7.1 Current Extension Points

| Extension Point | Mechanism | Evidence |
|-----------------|-----------|---------|
| New content types | `contentType` enum (TEXT, VOICE, VIDEO, IMAGE, MIXED) — add new types | `schema.prisma` Moment model |
| New link types | `linkType` String(50) — not enum-constrained, extensible | `model MomentLink` |
| New sphere types | `type` String(50) — not enum-constrained | `model Sphere` |
| New AI analysis operations | Memory expansion `expansionType` is String-typed | `routes/memory-expansion.ts` |
| New LLM providers | Multi-provider `LLMService` with provider abstraction (OpenAI, Ollama, Anthropic) | `services/llm/llm-service.ts` |
| New notification channels | `NotificationChannel` type supports in_app, email, push, SMS | `notification-service.ts` |
| New compliance regulations | Modular compliance report structure (GDPR, CCPA, ISO27001) | `compliance-service.ts` |
| New embedding models | `EmbeddingModel` registry with provider, dimensions, cost | `model EmbeddingModel` |
| JSONB metadata | Extensible `metadata` field on Moment, AIInsight, MemoryExpansion | `schema.prisma` |
| Template system | User-created moment capture templates with categories | `model Template` |

## 7.2 Plugin Architecture

**Status: ❌ No formal plugin system exists.**

The codebase uses service-oriented architecture with well-defined interfaces, but there is no plugin loader, hook system, or extension registry. New capabilities require direct code modification.

**Recommendation:** Introduce a plugin/extension registry for:
- Custom moment enrichment pipelines
- Custom analysis algorithms
- Custom recommendation models
- External integration connectors (calendar, email, CRM, etc.)

---

# 8 Missing Intelligence Components

## 8.1 Standalone Recommendation Engine

**Status:** ❌ **CRITICAL MISSING ARCHITECTURE COMPONENT**

**What Exists:** Recommendations are generated as sub-components within AI summaries (`SummaryGeneratorService.Recommendation[]`) and as action items in reflection outputs. Mood alerts suggest resources. Link suggestions propose connections.

**What Is Missing:**
1. **Proactive Recommendation Service** — A dedicated service that continuously monitors activity and pushes contextual recommendations without user request
2. **Recommendation Types Needed:**
   - Decision suggestions (based on recurring decision patterns)
   - Habit formation suggestions (based on temporal activity patterns)
   - Productivity recommendations (based on workflow analysis)
   - Relationship recommendations (based on social graph activity)
   - "Revisit This" suggestions (based on moment importance + time elapsed)
3. **Ranking and Scoring System** — Currently recommendations are unranked lists; need priority scoring, relevance ranking, and diversity balancing
4. **Feedback Loop** — No mechanism to track whether recommendations were viewed, acted upon, or dismissed; essential for learning and personalization
5. **Recommendation Shelving** — No way to snooze, dismiss, or archive recommendations

**Proposed Module Architecture:**
```
RecommendationEngine/
├── RecommendationService          # Core orchestrator
├── RecommendationTrigger          # Event-driven trigger (new moment, time-based, pattern-detected)
├── RecommendationScorer           # ML/heuristic scoring and ranking
├── RecommendationStore            # Prisma model for persisted recommendations
├── RecommendationFeedbackTracker  # Track view/accept/dismiss/act-on events
├── strategies/
│   ├── HabitRecommender           # Temporal pattern → habit suggestions
│   ├── RevisitRecommender         # Importance decay → revisit suggestions
│   ├── ConnectionRecommender      # Graph analysis → relationship suggestions
│   ├── ProductivityRecommender    # Workflow patterns → productivity tips
│   └── WellbeingRecommender       # Mood patterns → wellbeing resources
└── api/
    └── recommendation.routes.ts   # GET /api/recommendations, PATCH /:id/feedback
```

---

## 8.2 Knowledge Intelligence Layer (Knowledge Graph)

**Status:** ❌ **MISSING ARCHITECTURE COMPONENT**

**What Exists:**
- MomentLink with 9 typed relationships and graph traversal (depth 1–5)
- Named entity extraction with Wikidata IDs
- Conversation threading (implicit clustering)
- Sphere hierarchy
- User follow graph

**What Is Missing:**
1. **Topic/Concept Nodes** — Persistent topic entities that moments reference, enabling queries like "all moments about Project X"
2. **Entity Graph** — Named entities (people, places, organizations) are extracted per-moment but never linked into a persistent, queryable graph
3. **Relationship Inference** — No system to infer new relationships from existing graph structure (e.g., "You mention Sarah and Project X together frequently — are they related?")
4. **Graph Query API** — Only moment-to-moment traversal exists; no general-purpose graph query interface
5. **Knowledge Accumulation Engine** — No continuous process that watches for new moments and enriches the graph
6. **Graph Visualisation** — `TimelineArcView.tsx` shows time-based links; no full force-directed graph view

**Proposed Module Architecture:**
```
KnowledgeGraph/
├── KnowledgeGraphService           # Core graph builder
├── TopicNode model                 # Persistent topic/concept entities
├── EntityNode model                # Persistent named entity nodes
├── GraphRelationship model         # Entity-to-entity, entity-to-topic, moment-to-entity edges
├── GraphEnrichmentWorker           # Background worker: watch new moments, update graph
├── GraphInferenceEngine            # Infer new relationships from graph structure
├── GraphQueryService               # Cypher-like or custom query API for graph traversal
├── GraphVisualisationAPI           # API for rendering interactive graph views
└── api/
    ├── knowledge-graph.routes.ts   # CRUD for graph nodes + query endpoints
    └── graph-visualisation.routes.ts
```

**Technology Consideration:** Current PostgreSQL + Prisma may be sufficient for moderate graph sizes. For larger scale, consider a dedicated graph database (Neo4j, Amazon Neptune) or PostgreSQL recursive CTEs with materialized views.

---

## 8.3 Long-Term Intelligence Accumulation System

**Status:** ❌ **MISSING ARCHITECTURE COMPONENT**

**What Exists:** Individual insights, memory expansions, and mood history are persisted. Language evolution is tracked.

**What Is Missing:**
1. **Cross-Session Learning** — The system does not learn user preferences, writing style, or categorization patterns over time
2. **Personalized Models** — No user-specific model tuning or few-shot example curation
3. **Progressive Refinement** — Generated insights are one-shot; there is no mechanism to refine insights as new data arrives
4. **Intelligence Quality Scoring** — No way to measure whether generated insights are accurate or useful over time
5. **Intelligence Digest** — No periodic "state of your knowledge" report that synthesizes all accumulated intelligence

**Proposed Module:** `IntelligenceAccumulationService` that:
- Maintains a user-specific "knowledge profile" updated incrementally
- Tracks insight quality via user feedback (accept/reject/modify patterns from NLP feedback)
- Generates periodic intelligence digests
- Uses accumulated context to improve future AI prompts (few-shot personalization)

---

## 8.4 Summary Versioning

**Status:** ⚠️ **MINOR GAP**

Summaries are created as `AIInsight` records but have no explicit version chain. Regenerating a summary creates a new record with no link to the previous version.

**Recommendation:** Add a `previousInsightId` FK to `AIInsight` to create a version chain, or add a `version` integer with a composite unique constraint on `(userId, insightType, sphereId, period)`.

---

# 9 Traceability Matrix

| Requirement | Code Evidence | Tests | Notes |
|-------------|---------------|-------|-------|
| FLASHIT-FR-001 (Create moment) | `routes/moments.ts` POST handler | `routes/__tests__/`, `services/__tests__/` | Core path, well-tested |
| FLASHIT-FR-005 (AI classification) | `services/java-agents/classification-service.ts`, Java `ClassificationService.java` | Java agent tests (15 tests per README) | Local heuristic fallback for resilience |
| FLASHIT-FR-010–015 (Enrichment) | `services/ai/nlpService.ts`, `entityExtractor.ts`, `moodAnalyzer.ts`, Java `NLPService.java` | `services/__tests__/` | Multiple enrichment paths |
| FLASHIT-FR-020–024 (Search) | `services/search/enhanced-search-service.ts`, `facetedSearch.ts`, `visualSearch.ts` | `services/__tests__/` | 4 search modes |
| FLASHIT-FR-030–035 (Reflection) | `routes/reflection.ts`, Java `ReflectionService.java`, `services/ai/summaryGenerator.ts` | Route + service tests | Billing-gated |
| FLASHIT-FR-040–042 (Linking) | `routes/moment-links.ts` | `routes/__tests__/` | 9 link types, depth traversal |
| FLASHIT-FR-050–055 (Analytics) | `routes/analytics.ts`, `services/analytics.ts` | `services/__tests__/` | Complex analytics pipeline |
| FLASHIT-FR-060–066 (Collaboration) | `routes/collaboration.ts`, `modules/collaboration/collaboration-server.ts` | `routes/__tests__/`, `services/__tests__/` | Socket.IO + CRDT |
| FLASHIT-FR-070–073 (Mood) | `services/ai/moodAnalyzer.ts` | `services/__tests__/` | Privacy-first design |
| FLASHIT-FR-080–083 (Transcription) | Java `TranscriptionService.java`, `services/java-agents/transcription-client.ts` | Agent tests | Whisper-1 |
| FLASHIT-FR-090–096 (Auth) | `routes/auth-enhanced.ts`, multiple models | `routes/__tests__/`, `lib/__tests__/` | JWT + 2FA + biometric |
| FLASHIT-FR-100–103 (Billing) | `routes/billing.ts` | `routes/__tests__/` | Stripe webhooks |
| FLASHIT-FR-110–115 (Compliance) | `modules/compliance/`, `routes/privacy.ts` | — | Comprehensive GDPR/CCPA |

---

# 10 Gap & Risk Analysis

## 10.1 Architecture Risks

| Risk | Severity | Description | Mitigation |
|------|----------|-------------|-----------|
| **Dual AI execution path** | HIGH | Both Node.js Gateway and Java Agent perform NLP/sentiment/embedding operations, creating potential inconsistency | Complete Phase 2 migration: consolidate all AI into Java Agent; make Node.js services façades only |
| **In-memory embedding store (Java)** | HIGH | `EmbeddingService` uses `ConcurrentHashMap` — data lost on restart, not cluster-safe | Migrate to PostgreSQL pgvector or Redis vector storage |
| **No formal plugin system** | MEDIUM | All extensions require direct code modification | Design plugin/extension registry |
| **CRDT complexity** | MEDIUM | Custom CRDT engine for collaborative editing — complex to maintain and debug | Consider using established library (Yjs, Automerge) |
| **Mobile sync conflicts** | MEDIUM | Last-write-wins conflict resolution may lose data | Implement CRDT-based sync or 3-way merge |

## 10.2 AI Risks

| Risk | Severity | Description | Mitigation |
|------|----------|-------------|-----------|
| **Single LLM vendor dependency** | HIGH | Primary path depends on OpenAI (GPT-4o, Whisper, embeddings) | Multi-provider `LLMService` exists but not fully utilized; activate Ollama/Anthropic fallbacks |
| **Prompt injection** | MEDIUM | User-provided content is embedded directly into LLM prompts | Implement prompt sanitization and output validation |
| **AI cost escalation** | MEDIUM | Per-user AI consumption without hard spending caps | `AICostTrackingService` tracks costs; add per-user spending limits |
| **Hallucination in insights** | MEDIUM | ReflectionService may generate unfounded insights | Citation tracking exists (`citations[]`); add confidence threshold filtering |
| **No model evaluation framework** | LOW | No systematic way to evaluate AI output quality | Implement evaluation harness with golden datasets |

## 10.3 Privacy Risks

| Risk | Severity | Description | Mitigation |
|------|----------|-------------|-----------|
| **PII sent to LLM APIs** | HIGH | User moments (potentially containing sensitive content) sent to OpenAI | Implement PII detection/redaction before LLM calls; consider on-premise models |
| **Mood data sensitivity** | HIGH | Emotional patterns could reveal mental health status | Opt-in design ✅; add data minimization and retention limits |
| **Third-party data sharing** | MEDIUM | OpenAI, Stripe, SMTP providers receive user data | Document data flows; ensure DPAs are in place |
| **Backup security** | LOW | Backup validation exists but encryption-at-rest coverage unclear | Verify S3 SSE configuration; add backup encryption auditing |

## 10.4 Scalability Risks

| Risk | Severity | Description | Mitigation |
|------|----------|-------------|-----------|
| **PostgreSQL vector search** | HIGH | Cosine similarity queries may not scale with large embedding tables | Consider pgvector extension with HNSW indexes, or migrate to dedicated vector DB |
| **Single-database architecture** | MEDIUM | All services share one PostgreSQL instance | Plan for read replicas; consider CQRS for analytics queries |
| **Redis as single point of failure** | MEDIUM | Cache, queues, real-time, sessions all depend on Redis | Redis Sentinel or Cluster mode for HA |
| **No CDN** | LOW | Static assets and media served directly | Add CloudFront/CDN layer for media delivery |

---

# 11 Coverage Score

## 11.1 Moment Intelligence Lifecycle Coverage

| Stage | Score | Status |
|-------|-------|--------|
| 1. Moment Capture | 10/10 | ✅ Complete (5 modalities + offline + templates) |
| 2. Moment Storage | 10/10 | ✅ Complete (PostgreSQL + S3 + SQLite + Redis) |
| 3. Moment Context Enrichment | 9/10 | ✅ Complete (tags, entities, sentiment, mood, embeddings, threads) — minor: no location enrichment |
| 4. Moment Summarization | 8/10 | ✅ Complete (daily/weekly/monthly/yearly/custom) — minor: no summary versioning |
| 5. Moment Analysis | 9/10 | ✅ Complete (patterns, trends, behaviour, correlation, clustering, anomaly, language evolution) — minor: no traditional ML models |
| 6. Pattern Detection | 9/10 | ✅ Complete (LLM + statistical hybrid) |
| 7. Insight Generation | 9/10 | ✅ Complete (5 insight types, persisted with confidence scores) |
| 8. Recommendation Generation | 4/10 | ⚠️ Partial (embedded in summaries only; no standalone engine, ranking, or feedback loop) |
| 9. Knowledge Graph Formation | 4/10 | ⚠️ Partial (typed links + traversal exist; no topic/entity graph, inference, or visualisation) |
| 10. Long-term Intelligence Accumulation | 3/10 | ⚠️ Partial (individual pieces persisted; no cross-session learning or progressive refinement) |

## 11.2 Overall Platform Coverage

| Category | Score | Max | % |
|----------|-------|-----|---|
| Moment Intelligence Lifecycle | 75 | 100 | 75% |
| Core CRUD & UX | 48 | 50 | 96% |
| Authentication & Security | 18 | 20 | 90% |
| Collaboration | 17 | 20 | 85% |
| Compliance & Privacy | 9 | 10 | 90% |
| Observability | 9 | 10 | 90% |
| Extensibility | 4 | 10 | 40% |
| **TOTAL** | **180** | **220** | **82%** |

## 11.3 Completeness Summary

**Overall Product Completeness: 82%**

The platform has a **strong foundation** with comprehensive capture, storage, enrichment, search, analysis, and collaboration capabilities. The primary gaps are in the **intelligence layer's long-term accumulation** — specifically the missing standalone recommendation engine, unified knowledge graph, and cross-session learning system. These represent the final ~18% needed to fully realize the Moment Intelligence Platform vision.

**Priority Remediation:**
1. 🔴 **P0:** Standalone Recommendation Engine (§8.1)
2. 🔴 **P0:** Knowledge Intelligence Layer (§8.2)
3. 🟡 **P1:** Long-Term Intelligence Accumulation (§8.3)
4. 🟡 **P1:** Consolidate dual AI execution paths (§10.1)
5. 🟢 **P2:** Plugin/Extension system (§7.2)
6. 🟢 **P2:** Summary versioning (§8.4)
7. 🟢 **P2:** Migrate in-memory embedding store to persistent storage (§10.1)

---

# Appendix A: Technology Inventory

| Technology | Version | Usage |
|-----------|---------|-------|
| Node.js | 20+ | Gateway runtime |
| Java | 21 | Agent service runtime |
| Fastify | — | HTTP framework (Gateway) |
| ActiveJ | — | HTTP framework + DI + Eventloop (Agent) |
| Prisma | — | ORM (PostgreSQL) |
| React | — | Web UI framework |
| React Native | — | Mobile UI framework (Expo) |
| PostgreSQL | 15 | Primary data store |
| Redis | 7 | Cache, queues, sessions, pub/sub |
| MinIO / S3 | — | Object storage |
| OpenAI GPT-4o | — | Classification, reflection, NLP |
| OpenAI GPT-4o-mini | — | Auto-tagging, entity extraction, sentiment, mood |
| OpenAI Whisper-1 | — | Audio transcription |
| OpenAI text-embedding-3-small | — | 1536-dimension embeddings |
| Anthropic Claude 3 | — | Alternative LLM provider (configured, pricing tracked) |
| Ollama | — | Local LLM provider (configured) |
| Stripe | — | Subscription billing |
| Socket.IO | — | Real-time collaboration |
| BullMQ | — | Job queues |
| Jotai | — | Client-side atomic state |
| TanStack Query | — | Server state management |
| Tailwind CSS | — | Web styling |
| Zod | — | Runtime validation |
| Prometheus | — | Metrics collection |
| Grafana | — | Metrics visualization |
| Docker Compose | — | Local development orchestration |

# Appendix B: Subscription Tier Limits

| Feature | Free | Pro ($9.99/mo) | Teams ($24.99/mo) |
|---------|------|----------------|-------------------|
| Moments/month | 100 | 5,000 | Unlimited |
| Spheres | 3 | 50 | Unlimited |
| Storage | 1 GB | 50 GB | 500 GB |
| Transcription hours | 10 | 100 | Unlimited |
| AI Insights/month | 5 | 100 | Unlimited |
| Collaborators | 0 | 25 | Unlimited |
| Memory Expansions/month | 2 | 50 | Unlimited |

# Appendix C: API Endpoint Inventory

## Gateway (port 2900) — 65+ endpoints

### Auth (no prefix)
- POST /auth/register, /auth/login, /auth/refresh, /auth/2fa/setup
- GET /auth/sessions

### Moments (/api/moments)
- POST, GET, GET /:id, PUT /:id, DELETE /:id
- POST /:id/links, GET /:id/links

### Spheres (/api/spheres)
- POST, GET, PUT /:id, DELETE /:id

### Search (/api/search)
- POST /api/search, POST /api/search/similar, GET /api/search/suggestions
- POST /api/search/embeddings/generate

### Reflection (/api/reflection)
- POST /insights, /patterns, /connections
- GET /weekly, /monthly

### Analytics (/api/analytics)
- GET /dashboard, /meaning, /meaning/summary, /meaning/return-rate, /meaning/temporal-arcs, /meaning/language-evolution

### Memory Expansion (/api/memory-expansion)
- POST, GET, GET /:jobId, POST /batch

### Collaboration (/api/collaboration)
- POST /spheres/share, /invitations/accept, /comments, /reactions, /follow

### Notifications (/api/notifications)
- GET, GET /unread-count, PATCH /:id/read, PATCH /read-all, DELETE /:id

### Billing (/api/billing)
- GET /usage, /limits; POST /upgrade

### Admin (/api/admin)
- GET /users, /content/flagged, /stats; PATCH /users/:id/role, /users/:id/suspend

### Other
- Upload, Progressive Upload, Transcription, Privacy, Templates, Adoption, API Keys, System, Health

## Java Agent (port 8090) — 17 endpoints

- GET /health, /ready, /api/v1/agents, /api/v1/agents/:id/status
- POST /api/v1/agents/classification/classify, /classification/suggest-spheres
- POST /api/v1/agents/embedding/generate, /embedding/batch, /embedding/search
- POST /api/v1/agents/reflection/insights, /reflection/patterns, /reflection/connections
- POST /api/v1/agents/transcription/transcribe; GET /transcription/status/:jobId
- POST /api/v1/agents/nlp/extract-entities, /nlp/analyze-sentiment, /nlp/detect-mood

---

*Document generated by reverse engineering from source code. All claims are evidence-backed. Assumptions are marked with ⚠️. Missing components are flagged with ❌.*
