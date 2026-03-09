# TutorPutor – Day-by-Day Implementation Plan (Block 4: Days 31–50)

## Focus: Offline Mode, Mobile/Tauri, SSO & Enterprise Admin, Social Learning, Advanced VR, Payments, LTI, Compliance

> **Version:** 2.1.0  
> **Last Updated:** 2025-12-04  
> **Compliance:** `copilot-instructions.md` v2.3.0 (Reuse-First, No Duplicates, Hybrid Backend)

> Assumption: All components marked as "implemented" or "gap resolved" in the **Consolidated Implementation Plan** are already available and stable. Block 4 builds _on top_ of that, without re-solving earlier gaps.

This block combines:

- **Enterprise / infrastructure readiness (Option A)**
- **Learning experience innovation (Option B)**

into a single **20‑day block** focused on **hardening + differentiation**.

---

## 🚨 NON-NEGOTIABLE RULES (from copilot-instructions.md)

1. **Reuse First**: ALWAYS check `libs/java/*` and `libs/typescript/*` before writing new code
2. **Type Safety**: No `any`. Strict null checks. 100% type coverage
3. **Linting**: Zero warnings allowed
4. **Documentation**: Every public class/method MUST have JavaDoc and `@doc.*` tags
5. **Testing**: All async Java tests MUST use `EventloopTestBase`
6. **Architecture**: Follow Hybrid Backend model (Java/ActiveJ for Domain, Node/Fastify for User API)

---

## Required Shared Libraries (REUSE MANDATORY)

| Category          | Library               | Package                    | Use For                                                      |
| ----------------- | --------------------- | -------------------------- | ------------------------------------------------------------ |
| **HTTP**          | `@ghatana/api`        | `libs/typescript/api`      | API client, HTTP calls                                       |
| **Realtime**      | `@ghatana/realtime`   | `libs/typescript/realtime` | WebSocket, presence, live updates                            |
| **State**         | `@ghatana/state`      | `libs/typescript/state`    | Local state management, persistence                          |
| **UI**            | `@ghatana/ui`         | `libs/typescript/ui`       | All UI components (Banner, Badge, Avatar, etc.)              |
| **Charts**        | `@ghatana/charts`     | `libs/typescript/charts`   | Analytics visualization                                      |
| **Auth (Java)**   | `libs:auth`           | `libs/java/auth`           | **OIDC verification (OidcTokenVerifier exists!)**, JWT, RBAC |
| **Observability** | `libs:observability`  | `libs/java/observability`  | Metrics, tracing, logging, health                            |
| **AI**            | `libs:ai-integration` | `libs/java/ai-integration` | LLM/RAG workflows                                            |

---

We keep using:

- **Fastify** for all HTTP services (Node.js User API layer)
- **Java/ActiveJ** for domain logic (Auth, AI, Event Processing)
- **Ghatana shared libs** (`@ghatana/ui`, `@ghatana/api`, `@ghatana/realtime`, etc.)
- **libs:ai-integration** for all LLM/RAG work
- Existing **contracts/v1** pattern for types + service interfaces

---

## High-Level Areas Covered in Block 4

1. **Offline mode + local-first caching (web + Tauri)** – via `@ghatana/state` extension
2. **Mobile / desktop packaging & performance tuning**
3. **SSO (OAuth2/OIDC/SAML) & Enterprise admin console** – via `libs:auth` (OidcTokenVerifier)
4. **Data privacy: data export, deletion, retention (GDPR/FERPA/CCPA)**
5. **Social & collaborative learning features** – via `@ghatana/realtime`
6. **Advanced VR/AR simulation pipeline** (content lifecycle, telemetry, fallbacks)
7. **Marketplace payments: PSP-agnostic integration**
8. **LTI integration hardening + institutional reporting**

Days 31–40 lean infrastructure/enterprise;  
Days 41–50 lean experience/engagement.

---

## Repo / Package Context (Aligned with Actual Structure)

```text
products/tutorputor/
├── apps/
│   ├── api-gateway/           # Fastify gateway (Node.js)
│   ├── tutorputor-web/        # React SPA
│   └── tutorputor-admin/      # Institutional admin console [NEW - Block 4]
├── contracts/
│   └── v1/                    # types.ts, services.ts
├── docs/
│   └── ...                    # architecture, plans, runbooks
├── services/                  # Node.js/Fastify services
│   ├── tutorputor-learning/
│   ├── tutorputor-content/
│   ├── tutorputor-assessment/
│   ├── tutorputor-ai-proxy/
│   ├── tutorputor-cms/
│   ├── tutorputor-analytics/
│   ├── tutorputor-pathways/
│   ├── tutorputor-teacher/
│   ├── tutorputor-collaboration/
│   ├── tutorputor-billing/
│   ├── tutorputor-lti/
│   ├── tutorputor-gamification/  # ✅ ALREADY EXISTS - REUSE
│   ├── tutorputor-search/
│   ├── tutorputor-simulation/
│   └── tutorputor-compliance/    # [NEW - Block 4]
└── ai-service/                   # Java/ActiveJ AI domain service

libs/
├── typescript/
│   ├── api/                   # @ghatana/api - HTTP client (MUST USE)
│   ├── realtime/              # @ghatana/realtime - WebSocket (MUST USE for presence)
│   ├── state/                 # @ghatana/state - State management (extend for offline)
│   ├── ui/                    # @ghatana/ui - Components (MUST USE)
│   └── charts/                # @ghatana/charts - Visualization (MUST USE)
└── java/
    ├── auth/                  # ✅ HAS OidcTokenVerifier, OidcConfiguration - REUSE
    ├── observability/         # Metrics, tracing, health - REUSE
    └── ai-integration/        # LLM integration - REUSE
```

---

## DAYS 31–35 – Offline Mode, Mobile/Tauri, Performance

> **⚠️ REUSE MANDATE**: Extend `@ghatana/state` for offline persistence.
> **DO NOT** create a separate `libs/tutorputor-offline/` package.

### **Day 31 – Offline Mode Design & Contracts**

**Goal:** Formalize offline behavior, caching rules, and sync semantics for TutorPutor.

**Focus Areas:**

- `products/tutorputor/contracts/v1` (offline metadata types)
- `libs/typescript/state` (extend with offline adapters)

**Tasks:**

- [ ] **REUSE CHECK**: Review `@ghatana/state` for existing persistence APIs
- [ ] Define offline-related types in `contracts/v1/types.ts`:

  ```typescript
  export type SyncStatus = "synced" | "pending" | "error" | "offline";

  export interface OfflineState {
    isOnline: boolean;
    lastSyncAt: string | null;
    pendingMutationsCount: number;
  }

  export interface PendingMutation {
    id: string;
    type: "progress" | "assessment" | "ai-query";
    payload: unknown;
    createdAt: string;
    retryCount: number;
  }
  ```

- [ ] **EXTEND `@ghatana/state`** (NOT create new package):
  - Add `IndexedDBAdapter` to `libs/typescript/state/src/adapters/`
  - Add `OfflineStore` interface to `libs/typescript/state/src/types.ts`
  - This enables cross-product reuse (Guardian, Data-Cloud, etc.)
- [ ] Document **offline behavior spec** in `products/tutorputor/docs/offline-mode-spec.md`:
  - What works offline (reading cached modules, viewing progress)
  - What is queued (progress updates, assessment attempts, AI questions)
  - Conflict resolution strategy (last-write-wins with server authority)

**Acceptance Criteria:**

- Offline spec document is complete and agreed upon.
- Contracts/types compile and are reusable by web and desktop apps.
- `@ghatana/state` extended with IndexedDB adapter.

### **Day 32 – Web App Offline Support (PWA + Cache Layer)**

**Goal:** Implement offline mode for the web app using PWA + local caching.

**Focus Areas:**

- `products/tutorputor/apps/tutorputor-web`
- `libs/typescript/state` (use extended OfflineStore)

**Tasks:**

- [ ] Add PWA support:
  - Service worker registration
  - App manifest (icons, name, offline start URL)
- [ ] **REUSE**: Use `@ghatana/state` OfflineStore with IndexedDB adapter:
  - Cache last N modules and content blocks
  - Cache user's recent enrollments and progress snapshots
- [ ] Create TutorPutor-specific hooks (in `apps/tutorputor-web/src/hooks/`):
  - `useOfflineState()` – online/offline detection
  - `useCachedModule(slug)` – wraps `@ghatana/state` OfflineStore
- [ ] **REUSE `@ghatana/ui`** for offline UI indicators:
  - Use `Banner` component for "Offline mode" banner
  - Use `Badge` component for stale data indicators
  - Use `Avatar` with `status='offline'` for user state

**Acceptance Criteria:**

- User can:
  - Load a cached module while offline
  - See local progress (even if not yet synced)
- STUB sync: queue mutations without sending yet.

### **Day 33 – Sync Engine for Progress & Events**

**Goal:** Implement real sync for queued mutations once connectivity resumes.

**Focus Areas:**

- `libs/typescript/state` (add SyncEngine to shared lib)
- `products/tutorputor/apps/tutorputor-web`

**Tasks:**

- [ ] **EXTEND `@ghatana/state`** with `SyncEngine`:
  - Location: `libs/typescript/state/src/sync/SyncEngine.ts`
  - Maintain queue of `PendingMutation` records
  - Retry logic with exponential backoff
  - Idempotency key generation
- [ ] **REUSE `@ghatana/api`** for HTTP calls:
  - Use `ApiClient` from `@ghatana/api` for sync requests
  - DO NOT use axios directly
- [ ] Create hook in tutorputor-web: `useSyncEngine()`
  - Dispatch queued updates when `navigator.onLine === true`
- [ ] **REUSE `libs:observability`** for telemetry:
  - Emit sync success/fail metrics

**Acceptance Criteria:**

- Progress updates made offline are reliably synced once back online.
- No duplicate or regressive progress on server.
- Sync is transparent to the user aside from subtle status indicator.

### **Day 34 – Tauri/Desktop Shell Integration**

**Goal:** Ensure desktop (Tauri) app uses exactly the same offline layer and sync engine.

**Focus Areas:**

- `products/tutorputor/apps/tutorputor-desktop` (create if not exists)
- Shared code from `tutorputor-web`

**Tasks:**

- [ ] Set up Tauri shell to host the web app:
  - Embed `tutorputor-web` build
  - Configure Tauri for offline-first behavior
- [ ] Validate persistence in Tauri:
  - IndexedDB works in Tauri's WebView
  - Same `@ghatana/state` OfflineStore works
- [ ] Add desktop-specific enhancements:
  - OS-level notifications (study reminders) using Tauri APIs
- [ ] **ENSURE NO CODE DUPLICATION**:
  - Desktop and web share same hooks/components

**Acceptance Criteria:**

- TutorPutor desktop runs full vertical slice with offline mode.
- Offline/online behavior is consistent between web and desktop.

### **Day 35 – Performance Tuning & Bundle Budgets**

**Goal:** Optimize performance for offline-enabled apps.

**Focus Areas:**

- `products/tutorputor/apps/tutorputor-web`
- Build pipeline (Vite)

**Tasks:**

- [ ] Set bundle budgets & analyze:
  - JS bundle size per route
  - Initial load time, time to interactive
- [ ] Implement code-splitting for heavy features:
  - Lazy-load: Pathways, Analytics, Teacher dashboards
- [ ] Optimize offline caching:
  - LRU cache eviction (max 50 modules)
  - TTL for stale data (24 hours)
- [ ] Add Lighthouse/Playwright performance CI checks.

**Acceptance Criteria:**

- Initial bundle < 200KB gzipped
- Time to interactive < 3s on 3G
- CI fails if budgets are exceeded.

---

## DAYS 36–40 – SSO, Enterprise Admin, Compliance & Data Governance

> **⚠️ REUSE MANDATE**: Use `libs/java/auth/oidc/OidcTokenVerifier.java` for OIDC.
> The OIDC infrastructure is ALREADY IMPLEMENTED in `libs:auth`.

### **Day 36 – SSO & Identity Federation Contracts**

**Goal:** Prepare for enterprise SSO integrations while reusing existing IAM.

**Focus Areas:**

- `products/tutorputor/contracts/v1` (auth/SSO types)
- `libs/java/auth` (**ALREADY HAS OIDC SUPPORT**)

**Tasks:**

- [ ] **REUSE CHECK**: Review `libs/java/auth/oidc/`:
  - `OidcConfiguration.java` – OIDC provider config
  - `OidcTokenVerifier.java` – JWT verification for OIDC
- [ ] Add TutorPutor-specific SSO types in `contracts/v1/types.ts`:

  ```typescript
  export interface IdentityProviderConfig {
    id: string;
    type: "oidc" | "saml";
    displayName: string;
    discoveryEndpoint: string; // For OIDC
    clientId: string;
    enabled: boolean;
  }

  export interface SsoUserLink {
    userId: UserId;
    providerId: string;
    externalId: string;
    linkedAt: string;
  }
  ```

- [ ] Write `docs/tutorputor/sso-architecture.md`:
  - Sequence diagrams: LMS/IdP → TutorPutor Gateway → `libs:auth` OidcTokenVerifier

**Acceptance Criteria:**

- SSO contracts agreed upon and stable.
- Architecture doc shows reuse of `libs:auth`.

### **Day 37 – OIDC SSO Implementation (Google, Microsoft)**

**Goal:** Implement OIDC flows for common providers via existing `libs:auth`.

**Focus Areas:**

- `libs/java/auth` (**USE EXISTING OidcTokenVerifier**)
- `products/tutorputor/apps/api-gateway` (SSO callback endpoints)
- `products/tutorputor/apps/tutorputor-web` (login flows)

**Tasks:**

- [ ] **REUSE `libs:auth`**: Configure OIDC providers:

  ```java
  OidcConfiguration googleConfig = OidcConfiguration.builder()
      .discoveryEndpoint("https://accounts.google.com/.well-known/openid-configuration")
      .clientId(env.get("GOOGLE_CLIENT_ID"))
      .expectedAudience("tutorputor-api")
      .build();

  OidcTokenVerifier verifier = new OidcTokenVerifier(googleConfig);
  ```

- [ ] Add callback endpoints in api-gateway:
  - `GET /auth/callback/google`
  - `GET /auth/callback/microsoft`
- [ ] **REUSE `@ghatana/ui`** for frontend:
  - Use `Button` component for "Sign in with Google/Microsoft"
  - Use existing form components for login flow
- [ ] Map external identity → TutorPutor user + tenant membership.

**Acceptance Criteria:**

- User can sign in with Google/Microsoft and land in dashboard.
- SSO user mapping stored in database.

### **Day 38 – Institutional Admin Console (Tenant Admin App)**

**Goal:** Provide a dedicated admin console for enterprise tenants.

**Focus Areas:**

- `products/tutorputor/apps/tutorputor-admin` (NEW app)
- Existing services: analytics, learning, pathways

**Tasks:**

- [ ] Create `tutorputor-admin` React app:
  - **REUSE `@ghatana/ui`** for all components
  - **REUSE `@ghatana/charts`** for metrics visualization
- [ ] Add `InstitutionAdminService` interface to `contracts/v1/services.ts`:
  ```typescript
  export interface InstitutionAdminService {
    getTenantSummary(tenantId: TenantId): Promise<TenantSummary>;
    listTenantUsers(
      tenantId: TenantId,
      pagination: PaginationArgs
    ): Promise<PaginatedResult<UserSummary>>;
    getTenantUsage(
      tenantId: TenantId,
      dateRange: DateRange
    ): Promise<UsageMetrics>;
  }
  ```
- [ ] Build admin UI:
  - Tenant-level metrics (user counts, DAU, module usage)
  - Bulk operations (import users, assign pathways)
- [ ] **REUSE `libs:auth`** for role enforcement:
  - Only `admin` role can access this app

**Acceptance Criteria:**

- Tenant admin can log in and see high-level stats & user list.
- No cross-tenant data leaks.

### **Day 39 – Data Export, Deletion & Retention Policies (Compliance)**

**Goal:** Implement data privacy features for users and institutions.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-compliance` (NEW service)
- `products/tutorputor/apps/api-gateway`

**Tasks:**

- [ ] Define `ComplianceService` interface in `contracts/v1/services.ts`:
  ```typescript
  export interface ComplianceService {
    requestUserExport(
      userId: UserId
    ): Promise<{ requestId: string; estimatedCompletionAt: string }>;
    requestUserDeletion(
      userId: UserId
    ): Promise<{ requestId: string; scheduledDeletionAt: string }>;
    getExportStatus(requestId: string): Promise<ExportStatus>;
    downloadExport(
      requestId: string
    ): Promise<{ downloadUrl: string; expiresAt: string }>;
  }
  ```
- [ ] Implement queue-based jobs:
  - Aggregate data from all TutorPutor services
  - Produce export packages (JSON/CSV bundles)
  - Schedule soft-delete → hard-delete after retention period
- [ ] Add self-service Privacy Center in tutorputor-web:
  - Route: `/privacy/my-data`
  - **REUSE `@ghatana/ui`** components

**Acceptance Criteria:**

- User requests export → export produced and downloadable.
- User requests deletion → account marked for deletion.

### **Day 40 – Compliance Automation & Audit Logging**

**Goal:** Add observability and audit trails for all compliance operations.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-compliance`
- `libs/java/observability` (**REUSE for logging**)

**Tasks:**

- [ ] Add `AuditEvent` type to `contracts/v1/types.ts`:
  ```typescript
  export interface AuditEvent {
    id: string;
    actorId: UserId;
    tenantId: TenantId;
    action:
      | "data_export"
      | "data_deletion"
      | "sso_config_change"
      | "role_change";
    targetId: string;
    timestamp: string;
    metadata: Record<string, unknown>;
  }
  ```
- [ ] **REUSE `libs:observability`**:
  - Use OpenTelemetry for structured logging
  - Emit audit events via tracing spans
- [ ] Add admin UI page for audit history:
  - Filter by date, action, actor
  - **REUSE `@ghatana/ui` DataGrid** for table

**Acceptance Criteria:**

- Every compliance operation is audit-logged.
- Admins can review audit events for their tenant.

---

## DAYS 41–45 – Social Learning & Collaboration Enhancements

> **⚠️ REUSE MANDATES**:
>
> - Use `@ghatana/realtime` for presence (WebSocket layer exists!)
> - Use existing `tutorputor-gamification` service for streaks/badges

### **Day 41 – Social Graph & Engagement Contracts**

**Goal:** Formalize social structures and engagement metrics.

**Focus Areas:**

- `products/tutorputor/contracts/v1`
- `products/tutorputor/services/tutorputor-gamification` (**ALREADY EXISTS**)

**Tasks:**

- [ ] **REUSE CHECK**: Review `services/tutorputor-gamification/`:
  - Already has badge/streak infrastructure
- [ ] Add types to `contracts/v1/types.ts`:

  ```typescript
  export interface StudyGroup {
    id: StudyGroupId;
    tenantId: TenantId;
    classroomId: ClassroomId;
    name: string;
    memberIds: UserId[];
    createdAt: string;
  }

  export interface LeaderboardEntry {
    userId: UserId;
    displayName: string;
    score: number;
    rank: number;
    streak: number;
  }
  ```

- [ ] Extend `CollaborationService` in `contracts/v1/services.ts`:
  - `createStudyGroup`, `joinStudyGroup`, `listStudyGroups`

**Acceptance Criteria:**

- Contracts for social features stable.
- Gamification service confirmed as reusable.

### **Day 42 – Discussion Threads 2.0 (Reactions, Mentions, Tagging)**

**Goal:** Enhance collaboration threads for richer interaction.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-collaboration`
- `products/tutorputor/apps/tutorputor-web`

**Tasks:**

- [ ] Add types for reactions/mentions:

  ```typescript
  export type ReactionType = "👍" | "✅" | "❓" | "💡";

  export interface Reaction {
    postId: PostId;
    userId: UserId;
    type: ReactionType;
    createdAt: string;
  }

  export interface Mention {
    userId: UserId;
    displayName: string;
  }
  ```

- [ ] Implement reactions on posts
- [ ] Add `@mention` support within tenants
- [ ] Tag threads by type (`question`, `explanation`, `resource`)
- [ ] **REUSE `@ghatana/ui`** for thread UI:
  - Use existing Button, Avatar, Badge components

**Acceptance Criteria:**

- Students can react, mention peers, and filter by tags.
- All operations scoped to tenant & classroom.

### **Day 43 – Live Presence & Lightweight Co-Viewing**

**Goal:** Provide simple real-time awareness in modules.

**Focus Areas:**

- `libs/typescript/realtime` (**MUST REUSE**)
- `products/tutorputor/apps/tutorputor-web`

**Tasks:**

- [ ] **REUSE `@ghatana/realtime`**:

  ```typescript
  import { useWebSocket, WebSocketConfig } from "@ghatana/realtime";

  const config: WebSocketConfig = {
    url: "/ws/presence",
    reconnect: true,
  };

  const { send, lastMessage, connectionState } = useWebSocket(config);
  ```

- [ ] Implement presence channel per module slug:
  - Channel: `presence:module:{slug}`
  - Events: `user_joined`, `user_left`
- [ ] **REUSE `@ghatana/ui`**:
  - Use `Avatar` with `status` prop for presence indicators
  - Show "3 others learning now" badge

**Acceptance Criteria:**

- Presence updates visible in near real time.
- No PII beyond displayName/avatar inside tenant.

### **Day 44 – Study Groups & Group Assignments**

**Goal:** Formalize study groups and group-based assignments.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-teacher`
- `products/tutorputor/services/tutorputor-collaboration`

**Tasks:**

- [ ] Teacher UI:
  - Create study groups from classroom roster
  - Assign module(s) to study group
- [ ] Student UI:
  - Group home page: members, progress, shared discussions
- [ ] Analytics:
  - Group progress as aggregate
  - **REUSE `@ghatana/charts`** for visualization

**Acceptance Criteria:**

- Teacher can create groups and assign work.
- Group members see each other's relative progress.

### **Day 45 – Gamification: Streaks, Badges & Leaderboards**

**Goal:** Add light gamification to support motivation.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-gamification` (**ALREADY EXISTS**)
- `products/tutorputor/apps/tutorputor-web`

**Tasks:**

- [ ] **REUSE `tutorputor-gamification`** service:
  - Extend existing streak computation
  - Add new badge definitions
- [ ] Define badges:
  - "First Module Complete", "Streak 7 days", "Helper (5 accepted answers)"
- [ ] Leaderboards per classroom or study group:
  - Scoped by tenant/classroom
- [ ] **REUSE `@ghatana/ui`** for UI:
  - Use Badge component for achievement badges
  - Use DataGrid for leaderboards

**Acceptance Criteria:**

- Streaks and badges computed reliably.
- Leaderboards respect tenant & classroom boundaries.

---

## DAYS 46–50 – Advanced VR, Payments Hardening, LTI & E2E Signoff

### **Day 46 – VR/AR Content Lifecycle & Telemetry**

**Goal:** Upgrade VR/AR pipeline from basic to production-ready.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-cms`
- `products/tutorputor/services/tutorputor-analytics`
- `products/tutorputor/services/tutorputor-simulation`

**Tasks:**

- [ ] Add VR content lifecycle states to CMS:
  - `draft`, `qa_ready`, `certified`, `deprecated`
- [ ] Collect telemetry via `libs:observability`:
  - Time spent in sim
  - Completion of key scenario milestones
- [ ] UI:
  - Visual label for "VR Certified" modules
  - Fallback path for non-VR devices (2D version)
  - **REUSE `@ghatana/ui` Badge** for certification label

**Acceptance Criteria:**

- VR sims produce telemetry events.
- Non-VR users get a graceful 2D fallback.

### **Day 47 – Payments Integration: PSP-Agnostic Implementation**

**Goal:** Connect marketplace purchases to real (or sandbox) payment rails.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-billing`
- `products/tutorputor/apps/tutorputor-web` (checkout flow)

**Tasks:**

- [ ] Implement PSP-agnostic payment interface:
  ```typescript
  export interface PaymentProvider {
    createCheckoutSession(args: CheckoutArgs): Promise<CheckoutSession>;
    verifyPayment(sessionId: string): Promise<PaymentStatus>;
    handleWebhook(payload: unknown): Promise<WebhookResult>;
  }
  ```
- [ ] Implement adapters (choose one for MVP):
  - Stripe adapter (recommended for sandbox)
  - OR generic PSP adapter
- [ ] Webhook handling for payment success/failure
- [ ] Creator earnings view in admin/creator mode
- [ ] **REUSE `@ghatana/ui`** for checkout flow

**Acceptance Criteria:**

- Test card purchases in sandbox succeed & unlock modules.
- Creator earnings visible and auditable.

### **Day 48 – LTI Integration Hardening & LMS Reporting**

**Goal:** Make LTI robust enough for real institutional pilots.

**Focus Areas:**

- `products/tutorputor/services/tutorputor-lti`
- `products/tutorputor/apps/tutorputor-admin`

**Tasks:**

- [ ] Improve error handling & logging:
  - **REUSE `libs:observability`** for structured logs
- [ ] Support grade passback to LMS (basic score sync)
- [ ] Add LTI configuration screens in admin console:
  - Keys, endpoints, allowed tools
  - **REUSE `@ghatana/ui` Form** components
- [ ] LMS-specific tweaks for Canvas/Blackboard/Google Classroom quirks

**Acceptance Criteria:**

- LTI launch + basic score return works with real/sandbox LMS.
- Misconfigurations produce helpful error messages.

### **Day 49 – Global E2E Scenarios (Enterprise & Social)**

**Goal:** Validate full platform behavior with complex scenarios.

**Focus Areas:**

- All apps & services
- E2E test suites (Playwright, API tests)

**Tasks:**

- [ ] E2E Scenario Set:
  1. Student signs in via SSO → uses offline mode → syncs → completes pathway
  2. Teacher creates classroom → assigns modules & VR sim → monitors analytics
  3. Student joins study group → participates in discussions → earns badges
  4. Author publishes VR module → gets purchased via marketplace → telemetry appears
  5. Tenant admin configures LTI → LMS launches module → grades are synced
- [ ] Add targeted load tests:
  - AI tutor query bursts
  - High concurrent presence in one module

**Acceptance Criteria:**

- All E2E scenarios pass.
- System stable under target load parameters.

### **Day 50 – Release Readiness Review & Tech Debt Sweep**

**Goal:** Consolidate Block 4, resolve critical tech debt, prepare for pilots.

**Focus Areas:**

- All TutorPutor components
- Documentation & observability

**Tasks:**

- [ ] **COMPLIANCE CHECK** against copilot-instructions.md:
  - [ ] All new code reuses `libs/*` where applicable
  - [ ] No duplicate implementations
  - [ ] All Java async tests use `EventloopTestBase`
  - [ ] All public classes have `@doc.*` tags
  - [ ] Zero lint warnings
- [ ] Fix high-priority tech debt items:
  - TODOs in critical path services
  - Missing tests for billing, lti, compliance
- [ ] Update documentation:
  - `CONSOLIDATED_IMPLEMENTATION_PLAN.md` with Block 4 status
  - `RUNBOOKS` for operations (SSO, payments, LTI, VR issues)
- [ ] Formal "Go/No-Go" checklist for institutional pilot

**Acceptance Criteria:**

- Block 4 work is fully integrated, documented, and passing CI.
- TutorPutor is ready for **real-world pilots** with:
  - SSO (via `libs:auth`)
  - LTI
  - Offline mode (via `@ghatana/state`)
  - Social learning (via `@ghatana/realtime`)
  - Marketplace + VR/AR experiences

---

## Summary of Reuse-First Corrections

| Original Plan                     | Corrected Approach                             |
| --------------------------------- | ---------------------------------------------- |
| Create `libs/tutorputor-offline/` | Extend `@ghatana/state` with IndexedDB adapter |
| Custom SSO implementation         | Reuse `libs/java/auth/oidc/OidcTokenVerifier`  |
| Custom WebSocket for presence     | Reuse `@ghatana/realtime` WebSocket hooks      |
| New gamification service          | Reuse existing `tutorputor-gamification`       |
| Kill Bill specific integration    | PSP-agnostic `PaymentProvider` interface       |
| Custom UI components              | ALWAYS use `@ghatana/ui` components            |
| Custom charts                     | ALWAYS use `@ghatana/charts`                   |

---

**End of Block 4 (Days 31–50) Implementation Plan v2.1.0**
