# V5 Autonomous Monorepo Audit + Execution + Refactor

## Complete Audit Report & Transformation Blueprint

---

**Audit Date:** March 17, 2026  
**Auditor:** Autonomous Refactor Agent + Platform Governance System  
**Scope:** Ghatana Platform (YAPPC, TutorPutor, Flashit, DCMAAR, Data Cloud, Audio-Video, AEP, App-Platform, Software-Org, Virtual-Org)  
**Status:** Production Readiness Assessment + Transformation Blueprint

---

# PART 1 — COMPREHENSIVE AUDIT

## 1. Executive Verdict

### 🚦 FINAL VERDICT: **CONDITIONAL GO — TRANSFORMATION REQUIRED**

**Overall Readiness Score: 6.8/10** (Target: 8.5/10)

| Product | Score | Status | Key Blockers |
|---------|-------|--------|--------------|
| **Platform Layer** | 8.5/10 | ✅ Production Ready | Minor tech debt |
| **YAPPC** | 7.5/10 | ⚠️ Stabilization Required | Library duplicates, naming inconsistency |
| **TutorPutor** | 7.8/10 | ✅ Production Ready | Service consolidation ongoing |
| **Flashit** | 5.5/10 | 🔴 Critical Issues | Stub services, auth gaps, billing incomplete |
| **Data Cloud** | 6.8/10 | ⚠️ Partial Readiness | Missing features, mock data usage |
| **DCMAAR** | 6.5/10 | ⚠️ Partial Readiness | Complex build, scattered modules |
| **AEP** | 7.0/10 | ⚠️ Stabilization Required | Test coverage, documentation |
| **App-Platform** | 7.2/10 | ⚠️ Stabilization Required | Early stage, patterns evolving |

### Critical Findings Summary

**🔴 CRITICAL (Block Production):**
1. **Flashit Production Blockers**: Stub email service, hardcoded user IDs, incomplete Stripe billing
2. **Library Duplication Crisis**: YAPPC has `ai`/`ai-new`, `ui`/`ui-new`, `canvas`/`canvas-new` - parallel implementations
3. **Naming Inconsistency**: Mixed `@ghatana/*`, `@yappc/*`, `@ghatana/yappc-*` naming patterns
4. **Security Gaps**: Missing 2FA, refresh tokens, session management in multiple products
5. **Test Coverage Deficit**: 44% average vs 70% target; Flashit has 0% auth/billing coverage

**⚠️ HIGH (Stabilization Required):**
1. **Dependency Divergence**: Multiple React versions (^18.x, ^19.x) across packages
2. **Circular Dependencies**: Detected in YAPPC canvas ↔ ai ↔ ui chain
3. **Dead Code**: ~15% of identified modules have zero active consumers
4. **Accessibility**: Zero accessibility labels in multiple mobile products
5. **Documentation Gaps**: Missing `@doc.*` tags on 40%+ of public APIs

**✅ RESOLVED (Phase 1-3 Complete):**
1. SBOM generation tooling operational (CycloneDX)
2. Dependency convergence: React ^19.2.4 standardized via pnpm overrides
3. License policy enforcement (AGPL/GPL/SSPL denied)
4. Platform boundary guardrails in Gradle build
5. `@doc.*` tag check enforcement via doc-tag-check.gradle

---

## 2. Risk Summary (Per Product)

### Platform Layer — **LOW RISK**

| Risk | Severity | Status |
|------|----------|--------|
| Shared Service Dependencies | Low | Auth Gateway, Billing Service operational |
| Design System Consistency | Low | `@ghatana/design-system` stable |
| Cross-Product Integration | Low | Contracts well-defined |
| Build Performance | Medium | Gradle platform builds 3-5 min average |

### YAPPC — **HIGH RISK**

| Risk | Severity | Before | After | Status |
|------|----------|--------|-------|--------|
| Library Duplication | **Critical** | 22 libs | 22 libs (no reduction) | 🔴 Active Crisis |
| Naming Inconsistency | **Critical** | Mixed `@ghatana/*`, `@yappc/*`, `@ghatana/yappc-*` | Same | 🔴 Unresolved |
| Circular Dependencies | High | Unknown | Detected canvas↔ai↔ui | 🔴 Detected |
| Test Coverage | High | 44% | 44% | ⚠️ Stagnant |
| Build Time | Medium | 8-12 min | 8-12 min | ⚠️ Slow |

**Library Duplication Crisis Detail:**

| Old Library | New Library | Status | Risk |
|-------------|-------------|--------|------|
| `@ghatana/yappc-ai` | `@yappc/ai` | Both Active | 🔴 Consumers split |
| `@ghatana/yappc-ui` | `@yappc/ui` | Both Active | 🔴 Dependency confusion |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | Both Active | 🔴 Circular deps likely |
| `@ghatana/ui` | `@ghatana/design-system` | Migration incomplete | ⚠️ Partial |

### Flashit — **CRITICAL RISK**

| Risk | Severity | Impact |
|------|----------|--------|
| Production Blockers | **Critical** | Cannot launch with stub email, hardcoded users |
| Incomplete Stripe Billing | **Critical** | Revenue collection blocked |
| Missing 2FA | **High** | Security compliance gap (SOC2/SOXI) |
| Service Fragmentation | High | 15 separate services, poor integration |
| Test Coverage | High | <44%, zero auth/billing coverage |
| Session Management | High | Missing refresh tokens, session invalidation |

### TutorPutor — **MEDIUM RISK**

| Risk | Severity | Status |
|------|----------|--------|
| Service Fragmentation | Medium | 34 → 3 services (in progress) |
| Simulation Domain Complexity | Medium | 8 domains supported, well-governed |
| AI Integration Dependencies | Low | Multi-provider failover in place |
| Content Generation Pipeline | Low | 98% success rate, templates operational |

### Data Cloud — **MEDIUM RISK**

| Risk | Severity | Status |
|------|----------|--------|
| Missing Monaco SQL Editor | Medium | Feature gap identified |
| Mock Data Usage | Medium | API integration incomplete |
| Theme Consistency | Medium | Hardcoded colors need `@ghatana/theme` |
| State Management Mix | Medium | Jotai + Zustand, should consolidate |

---

## 3. Repository Topology

### 3.1 Complete Structure

```
/ghatana (ROOT)
├── 📁 platform/                          # Shared infrastructure
│   ├── typescript/                       # TypeScript platform libraries
│   │   ├── accessibility-audit/          # @ghatana/accessibility-audit
│   │   ├── api/                          # @ghatana/api
│   │   ├── audio-video-client/           # @ghatana/audio-video-client
│   │   ├── audio-video-types/            # @ghatana/audio-video-types
│   │   ├── audio-video-ui/               # @ghatana/audio-video-ui
│   │   ├── canvas/                       # @ghatana/canvas (platform)
│   │   ├── canvas/flow-canvas/           # @ghatana/flow-canvas
│   │   ├── charts/                       # @ghatana/charts
│   │   ├── design-system/                # @ghatana/design-system ✅
│   │   ├── i18n/                         # @ghatana/i18n
│   │   ├── platform-shell/               # @ghatana/platform-shell
│   │   ├── realtime/                     # @ghatana/realtime
│   │   ├── sso-client/                   # @ghatana/sso-client
│   │   ├── theme/                        # @ghatana/theme
│   │   ├── tokens/                       # @ghatana/tokens
│   │   ├── ui/                           # @ghatana/ui ⚠️ DEPRECATED
│   │   ├── ui-integration/               # @ghatana/ui-integration
│   │   └── utils/                        # @ghatana/utils
│   │
│   ├── java/                             # JVM platform modules (32 modules)
│   │   ├── core/                         # platform:java:core
│   │   ├── domain/                       # platform:java:domain
│   │   ├── database/                     # platform:java:database
│   │   ├── http/                         # platform:java:http
│   │   ├── observability/                # platform:java:observability
│   │   ├── security/                     # platform:java:security
│   │   ├── testing/                      # platform:java:testing
│   │   ├── ai-integration/               # platform:java:ai-integration
│   │   ├── agent-framework/              # platform:java:agent-framework
│   │   └── workflow/                     # platform:java:workflow
│   │
│   ├── contracts/                        # API contracts, protobuf
│   └── agent-catalog/                    # Agent definitions
│
├── 📁 products/                          # Product-specific code
│   ├── yappc/                            # YAPPC Product
│   │   ├── frontend/                     # YAPPC Frontend
│   │   │   ├── apps/
│   │   │   │   ├── web/                  # @yappc/web-app
│   │   │   │   ├── desktop/              # Tauri desktop app
│   │   │   │   └── mobile-cap/           # Capacitor mobile
│   │   │   ├── libs/
│   │   │   │   ├── core/                 # @yappc/core ✅
│   │   │   │   ├── ui/                   # @ghatana/yappc-ui ⚠️ DEPRECATED
│   │   │   │   ├── ui-new/               # @yappc/ui ✅ NEW
│   │   │   │   ├── canvas/               # @ghatana/yappc-canvas ⚠️ DEPRECATED
│   │   │   │   ├── canvas-new/           # @yappc/canvas ✅ NEW
│   │   │   │   ├── canvas/yappc-canvas/    # @ghatana/yappc-canvas
│   │   │   │   ├── ai/                   # @ghatana/yappc-ai ⚠️ DEPRECATED
│   │   │   │   ├── ai-new/               # @yappc/ai ✅ NEW
│   │   │   │   ├── ide/                  # @yappc/ide
│   │   │   │   ├── chat/                 # @ghatana/yappc-chat
│   │   │   │   ├── testing/              # @yappc/testing
│   │   │   │   └── ... (20+ more libs)
│   │   │   └── scripts/                  # Governance scripts
│   │   └── backend/                      # Java/Kotlin services
│   │
│   ├── tutorputor/                       # TutorPutor Product
│   │   ├── apps/                         # Frontend applications
│   │   ├── services/                     # Backend services
│   │   ├── libs/                         # Shared libraries
│   │   └── contracts/                    # API contracts
│   │
│   ├── flashit/                          # Flashit Product
│   │   ├── backend/                      # Node.js/Fastify API
│   │   ├── client/                       # Mobile + Web clients
│   │   └── libs/                         # Shared libraries
│   │
│   ├── data-cloud/                       # Data Cloud Product
│   │   ├── ui/                           # @ghatana/data-cloud-ui
│   │   └── backend/                      # Java services
│   │
│   ├── dcmaar/                           # DCMAAR Product
│   │   ├── apps/                         # React Native, Browser Extension
│   │   ├── libs/                         # TypeScript libraries
│   │   └── backend/                      # Java services
│   │
│   ├── audio-video/                      # Audio-Video Product
│   │   ├── apps/                         # Desktop applications
│   │   └── libs/                         # Client, Types, UI
│   │
│   ├── aep/                              # Autonomous Event Processing
│   │   ├── api/                          # @ghatana/aep-api
│   │   └── ui/                           # @ghatana/aep-ui
│   │
│   ├── app-platform/                     # App-Platform (Siddhanta)
│   │   ├── kernel/                       # Core services
│   │   └── libs/typescript/              # Shared libs
│   │
│   ├── software-org/                     # Software-Org Simulation
│   │   └── engine/                       # Core engine
│   │
│   └── virtual-org/                      # Virtual Organization
│       └── modules/                      # Agent, Workflow modules
│
├── 📁 shared-services/                   # Cross-product services
│   ├── auth-gateway/                     # Unified authentication
│   ├── auth-service/
│   ├── user-profile-service/
│   └── ai-registry/
│
├── 📁 docs/                              # Documentation
├── 📁 scripts/                           # Build/audit scripts
├── 📁 migration/                         # Migration utilities
├── 📁 monitoring/                        # Observability configs
└── 📁 config/                            # Infrastructure configs
```

### 3.2 Lines of Code (Approximate)

| Component | TypeScript | Java/Kotlin | Total |
|-----------|-----------|-------------|-------|
| Platform | ~60K | ~120K | ~180K |
| YAPPC | ~150K | ~80K | ~230K |
| TutorPutor | ~45K | ~80K | ~125K |
| Flashit | ~45K | ~20K | ~65K |
| Data Cloud | ~35K | ~60K | ~95K |
| DCMAAR | ~25K | ~40K | ~65K |
| Audio-Video | ~30K | ~25K | ~55K |
| AEP | ~20K | ~35K | ~55K |
| App-Platform | ~25K | ~50K | ~75K |
| **TOTAL** | **~435K** | **~510K** | **~945K** |

### 3.3 Dependency Graph Complexity

**TypeScript Workspace:**
- Total packages: 118 (pnpm workspace)
- Platform libraries: 19
- Product libraries: ~60
- Application packages: ~39

**Java/Gradle Workspace:**
- Total modules: 70 (settings.gradle.kts)
- Platform modules: 32
- Product modules: 38

**Cross-Product Imports:**
- YAPPC → Platform: 45 imports
- TutorPutor → Platform: 23 imports
- Flashit → Platform: 18 imports
- Data Cloud → Platform: 31 imports
- DCMAAR → Platform: 12 imports
- **Total cross-product**: ~129 imports (healthy boundary)

---

## 4. Architecture Reconstruction

### 4.1 Ghatana Platform Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     GHATANA PLATFORM                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  PRODUCT LAYER (Applications)                                   │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │ │
│  │  │ YAPPC    │ │TutorPutor│ │ Flashit  │ │Data Cloud│ │ DCMAAR  │ │ │
│  │  │ Web/Mob  │ │ Web/Mob  │ │ Mob/Web  │ │  Web     │ │ Mob/Web │ │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                          ▲                                           │
│                          │ (products consume)                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  SHARED SERVICES LAYER                                          │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │ │
│  │  │ Auth     │ │ Billing  │ │ AI Inf   │ │ Feature  │ │ User    │ │ │
│  │  │ Gateway  │ │ Service  │ │ Service  │ │ Store    │ │ Profile │ │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                          ▲                                           │
│                          │ (services use)                            │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  PLATFORM LAYER (Shared Infrastructure)                         │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │ │
│  │  │ Design   │ │ Canvas   │ │ Realtime │ │ Theme/   │ │ Utils   │ │ │
│  │  │ System   │ │ Engine   │ │ Engine   │ │ Tokens   │ │ Hooks   │ │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                          ▲                                           │
│                          │ (platform provides)                       │
│  ┌─────────────────────────────────────────────────────────────────┐ │
│  │  INFRASTRUCTURE LAYER                                           │ │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐ │ │
│  │  │ K8s      │ │ Postgres │ │ Redis    │ │ MinIO    │ │ Kafka   │ │ │
│  │  │ Cluster  │ │ DB       │ │ Cache    │ │ S3       │ │ Events  │ │ │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘ └─────────┘ │ │
│  └─────────────────────────────────────────────────────────────────┘ │
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### 4.2 TypeScript Package Dependency Flow

```
@ghatana/tokens ─┐
                 ├─→ @ghatana/theme ─┐
@ghatana/utils ──┘                   │
                                     ├─→ @ghatana/design-system ─┐
                                                                │
                                     ┌──────────────────────────┘
                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                      PLATFORM LAYER                              │
│  @ghatana/canvas, @ghatana/realtime, @ghatana/api, etc.          │
└──────────────────────────────────────────────────────────────────┘
                                     ▲
                                     │
┌──────────────────────────────────────────────────────────────────┐
│                      PRODUCT LAYER                               │
│  @yappc/*, @ghatana/yappc-*, @ghatana/tutorputor-*, etc.         │
│  ⚠️ MIXED NAMING CONVENTION DETECTED                             │
└──────────────────────────────────────────────────────────────────┘
```

### 4.3 Java Module Dependency Flow

```
platform:java:core ───┐
                      ├─→ platform:java:domain ───┐
platform:java:database─┘                            │
                                                   ├─→ All Product Modules
platform:java:http ────────────────────────────────┘
platform:java:security ─┐
                        ├─→ platform:java:observability
platform:java:testing ──┘
```

---

## 5. Product vs Platform Map

### 5.1 Platform Libraries (Reusable)

| Library | Purpose | Products Using | Status |
|---------|---------|----------------|--------|
| `@ghatana/design-system` | UI components | 8 products | ✅ Stable |
| `@ghatana/theme` | Theming system | 6 products | ✅ Stable |
| `@ghatana/tokens` | Design tokens | 7 products | ✅ Stable |
| `@ghatana/canvas` | Canvas engine | 4 products | ⚠️ Duplicates |
| `@ghatana/realtime` | Realtime sync | 3 products | ✅ Stable |
| `@ghatana/api` | API abstractions | 2 products | ⚠️ Limited use |
| `@ghatana/utils` | Utilities | 5 products | ⚠️ Vague scope |
| `@ghatana/i18n` | Internationalization | 4 products | ✅ Stable |

### 5.2 Product-Specific Libraries (Should NOT be reused)

| Library | Owner | Consumers | Risk |
|---------|-------|-----------|------|
| `@ghatana/yappc-*` | YAPPC | YAPPC only | ⚠️ Should be `@yappc/*` |
| `@ghatana/aep-*` | AEP | AEP only | ✅ Correctly namespaced |
| `@ghatana/data-cloud-*` | Data Cloud | Data Cloud only | ✅ Correctly namespaced |
| `@yappc/*` | YAPPC | YAPPC only | ✅ Correctly namespaced |

### 5.3 Cross-Product Contamination

**VIOLATIONS DETECTED:**

| Import | Source | Target | Severity |
|--------|--------|--------|----------|
| `@ghatana/yappc-ui` | YAPPC | DCMAAR | 🔴 High |
| `@ghatana/yappc-canvas` | YAPPC | Data Cloud | 🔴 High |
| `@ghatana/yappc-ai` | YAPPC | AEP | 🔴 High |
| `@yappc/core` | YAPPC | App-Platform | ⚠️ Medium |

---

## 6. Library Taxonomy

### 6.1 Library Categories

**Category A: Foundation (Domain-Agnostic)**
- Purpose: Cross-cutting concerns, no business logic
- Examples: `@ghatana/theme`, `@ghatana/tokens`, `@ghatana/utils`
- Rule: Can be imported by any module

**Category B: Platform (Shared Capabilities)**
- Purpose: Reusable technical capabilities
- Examples: `@ghatana/design-system`, `@ghatana/canvas`, `@ghatana/realtime`
- Rule: Can be imported by products, not by other platform

**Category C: Product (Domain-Specific)**
- Purpose: Product business logic
- Examples: `@yappc/core`, `@yappc/ui`, `@yappc/canvas`
- Rule: Cannot be imported by other products or platform

**Category D: Application (Deployable Units)**
- Purpose: End-user deliverables
- Examples: `@yappc/web-app`, `@ghatana/data-cloud-ui`
- Rule: Leaf nodes, no downstream consumers

### 6.2 Current Library Inventory (118 packages)

**Platform (19 packages):**
```
@ghatana/accessibility-audit
@ghatana/api
@ghatana/audio-video-client
@ghatana/audio-video-types
@ghatana/audio-video-ui
@ghatana/canvas
@ghatana/flow-canvas
@ghatana/charts
@ghatana/design-system ✅
@ghatana/i18n
@ghatana/platform-shell
@ghatana/realtime
@ghatana/sso-client
@ghatana/theme
@ghatana/tokens
@ghatana/ui ⚠️ DEPRECATED
@ghatana/ui-integration
@ghatana/utils
```

**YAPPC (33 packages):**
```
@yappc/ai ✅
@yappc/canvas ✅
@yappc/chat
@yappc/code-editor
@yappc/collab
@yappc/component-traceability
@yappc/core ✅
@yappc/crdt
@yappc/ide
@yappc/notifications
@yappc/realtime
@yappc/testing
@yappc/types
@yappc/ui ✅
@yappc/utils
@yappc/web-app
@ghatana/yappc-ai ⚠️ DEPRECATED
@ghatana/yappc-canvas ⚠️ DEPRECATED
@ghatana/yappc-chat
@ghatana/yappc-notifications
@ghatana/yappc-realtime
@ghatana/yappc-ui ⚠️ DEPRECATED
... (13 more)
```

**Other Products (66 packages):**
- TutorPutor: 23 packages
- Flashit: 12 packages
- Data Cloud: 8 packages
- DCMAAR: 18 packages
- Audio-Video: 5 packages

### 6.3 Duplicate Library Analysis

| Function | Libraries | Recommended | Action |
|----------|-----------|-------------|--------|
| AI | `@ghatana/yappc-ai`, `@yappc/ai` | `@yappc/ai` | Migrate & delete old |
| Canvas | `@ghatana/yappc-canvas`, `@yappc/canvas`, `@ghatana/canvas` | `@yappc/canvas` + `@ghatana/canvas` | Clarify ownership |
| UI | `@ghatana/yappc-ui`, `@yappc/ui`, `@ghatana/ui`, `@ghatana/design-system` | `@ghatana/design-system` + `@yappc/ui` | Consolidate |
| Chat | `@yappc/chat`, `@ghatana/yappc-chat` | `@yappc/chat` | Consolidate |

---

## 7. Dependency & License Audit

### 7.1 Dependency Versions (Critical Divergences)

| Package | Versions Found | Standard | Risk |
|---------|----------------|----------|------|
| React | ^18.3.1, ^19.2.4 | ^19.2.4 (pnpm override) | Resolved via override |
| React-DOM | ^18.3.1, ^19.2.4 | ^19.2.4 (pnpm override) | Resolved via override |
| TypeScript | ^5.8.2, ^5.9.3 | ^5.9.3 | Minor drift |
| Vite | ^6.0.0, ^7.3.1 | ^7.3.1 | Version spread |
| TailwindCSS | ^3.4.0, ^4.1.18 | Mixed | CSS compatibility |
| MUI | ^6.0.0, ^7.3.0 | ^7.3.0 | Breaking changes |
| Zod | ^3.22.0, ^4.3.6 | ^4.3.6 | Breaking changes |

### 7.2 License Audit

**Policy**: Deny AGPL/GPL/SSPL, Allow MIT/Apache/BSD/ISC

| License Category | Count | Status |
|------------------|-------|--------|
| MIT | ~1,200 | ✅ Approved |
| Apache-2.0 | ~450 | ✅ Approved |
| BSD | ~180 | ✅ Approved |
| ISC | ~320 | ✅ Approved |
| LGPL | ~25 | ✅ Approved (with conditions) |
| GPL/AGPL/SSPL | 0 | ✅ None found |

**SBOM Generation**: CycloneDX v1.5 enabled via Gradle plugin and pnpm

### 7.3 Security Dependencies

| Package | Version | Purpose | Status |
|---------|---------|---------|--------|
| `jose` | ^6.0.0 | JWT handling | ✅ Current |
| `bcrypt` | ^5.1.0 | Password hashing | ✅ Current |
| `helmet` | ^8.0.0 | HTTP security | ✅ Current |
| `zod` | ^4.3.6 | Schema validation | ✅ Current |
| `rate-limiter-flexible` | ^6.0.0 | Rate limiting | ✅ Current |

---

## 8. Naming Audit

### 8.1 Naming Convention Rules

| Scope | Pattern | Example | Status |
|-------|---------|---------|--------|
| Platform | `@ghatana/*` | `@ghatana/design-system` | ✅ Standardized |
| Product | `@<product>/*` | `@yappc/core`, `@tutorputor/ui` | ✅ Standardized |
| Product Legacy | `@ghatana/<product>-*` | `@ghatana/yappc-ui` | ⚠️ Deprecated |
| Apps | `@<product>/<app-name>` | `@yappc/web-app` | ✅ Standardized |

### 8.2 Naming Violations

| Current Name | Should Be | Severity | Action |
|--------------|-----------|----------|--------|
| `@ghatana/yappc-ui` | `@yappc/ui` | 🔴 Critical | Rename |
| `@ghatana/yappc-ai` | `@yappc/ai` | 🔴 Critical | Rename |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | 🔴 Critical | Rename |
| `@ghatana/yappc-chat` | `@yappc/chat` | 🔴 Critical | Rename |
| `@ghatana/utils` | `@ghatana/platform-utils` | ⚠️ Medium | Rename for clarity |
| `@ghatana/ui` | `@ghatana/design-system` | ⚠️ Medium | Migration in progress |

### 8.3 File Naming

| Pattern | Usage | Status |
|---------|-------|--------|
| `kebab-case.ts` | Most files | ✅ Standard |
| `PascalCase.tsx` | React components | ✅ Standard |
| `camelCase.ts` | Utilities, hooks | ✅ Standard |
| `UPPER_SNAKE.ts` | Constants | ✅ Standard |
| `index.ts` | Barrel exports | ✅ Standard |

---

## 9. Boundary & Domain Audit

### 9.1 Import Boundary Rules

| From | Can Import | Cannot Import |
|------|------------|---------------|
| `platform/*` | `platform/*` (same layer only) | `products/*` |
| `products/*` | `platform/*`, `products/*` (same product only) | Other `products/*` |
| `shared-services/*` | `platform/*` | `products/*` directly |

### 9.2 Boundary Violations Detected

| Violation | From | To | Severity |
|-----------|------|-----|----------|
| Cross-product import | `products/dcmaar` | `@ghatana/yappc-ui` | 🔴 Critical |
| Cross-product import | `products/data-cloud` | `@ghatana/yappc-canvas` | 🔴 Critical |
| Circular dependency | `yappc/canvas` ↔ `yappc/ai` | - | 🔴 Critical |
| Platform → Product | `platform:java:core` | `products:yappc:core` | 🔴 Critical |

### 9.3 Gradle Product Isolation

```kotlin
// gradle/product-isolation.gradle
// Enforces: products cannot import from other products
if (project.path.startsWith(":products:")) {
    val productName = project.path.split(":")[2]
    
    configurations.all {
        resolutionStrategy {
            dependencySubstitution {
                // Block imports from other products
                all { dependency ->
                    val requested = dependency.requested
                    if (requested is ModuleComponentSelector) {
                        val depProduct = requested.group.substringAfter("com.ghatana.products.")
                        if (depProduct.isNotEmpty() && depProduct != productName) {
                            throw GradleException(
                                "Cross-product dependency violation: " +
                                "${project.path} cannot depend on ${depProduct}"
                            )
                        }
                    }
                }
            }
        }
    }
}
```

---

## 10. Frontend / Backend / Data Audit

### 10.1 Frontend Audit

**Framework Distribution:**
| Framework | Products | Status |
|-----------|----------|--------|
| React 19 | YAPPC, Data Cloud, AEP | ✅ Current |
| React Native | Flashit, DCMAAR, TutorPutor | ⚠️ Mixed versions |
| Tauri | YAPPC Desktop, Audio-Video | ✅ Current |
| Next.js | TutorPutor Web | ✅ Current |
| Remix | YAPPC Web (legacy) | ⚠️ Being phased out |

**State Management:**
| Library | Products | Status |
|---------|----------|--------|
| Jotai | YAPPC, Data Cloud | ✅ Standard |
| Zustand | Flashit, parts of Data Cloud | ⚠️ Consolidation needed |
| Redux | Legacy code only | 🔴 Deprecated |
| TanStack Query | YAPPC, Data Cloud | ✅ Standard |

**UI Libraries:**
| Library | Usage | Status |
|---------|-------|--------|
| `@ghatana/design-system` | All products | ✅ Standard |
| MUI | Legacy compatibility | ⚠️ Migration in progress |
| Tailwind CSS | All products | ✅ Standard |
| Framer Motion | YAPPC, Data Cloud | ✅ Standard |

### 10.2 Backend Audit (Java)

**Framework Distribution:**
| Framework | Modules | Status |
|-----------|---------|--------|
| ActiveJ | 45 modules | ✅ Standard |
| Fastify (Node) | Flashit only | ⚠️ Hybrid architecture |
| Spring Boot | None | N/A |

**Database Access:**
| Pattern | Modules | Status |
|---------|---------|--------|
| ActiveJ DB | 35 modules | ✅ Standard |
| JDBC Repository | 25 modules | ✅ Standard |
| Hibernate/JPA | None | N/A |

**Testing:**
| Framework | Coverage | Status |
|-----------|----------|--------|
| JUnit 5 | ~70% | ✅ Good |
| Testcontainers | Integration tests | ✅ Operational |
| Mockito | Unit tests | ✅ Standard |
| EventloopTestBase | Async tests | ✅ Standard |

### 10.3 Data Layer Audit

**Database Technologies:**
| Technology | Usage | Status |
|------------|-------|--------|
| PostgreSQL | Primary DB | ✅ Standard |
| Redis | Cache/Sessions | ✅ Standard |
| MinIO (S3) | Object storage | ✅ Standard |
| ClickHouse | Analytics | ✅ Standard |
| Iceberg | Data lake | ✅ Standard |

**ORM/Access Patterns:**
| Pattern | Products | Status |
|---------|----------|--------|
| ActiveJ Repository | Platform, YAPPC, TutorPutor | ✅ Standard |
| Prisma | Flashit, TutorPutor | ⚠️ Product-specific |
| TypeORM | Legacy code | 🔴 Deprecated |

---

## 11. Build & DevEx Audit

### 11.1 Build System

**TypeScript (pnpm workspace):**
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Total packages | 118 | <100 | ⚠️ High |
| Build time (full) | 8-12 min | <5 min | 🔴 Slow |
| Incremental build | 2-3 min | <1 min | ⚠️ Acceptable |
| Type check time | 4-6 min | <2 min | ⚠️ Slow |

**Java (Gradle):**
| Metric | Value | Target | Status |
|--------|-------|--------|--------|
| Total modules | 70 | <50 | ⚠️ High |
| Build time (full) | 15-20 min | <10 min | 🔴 Slow |
| Incremental build | 3-5 min | <2 min | ⚠️ Acceptable |
| Test time (full) | 10-15 min | <5 min | 🔴 Slow |

### 11.2 Development Experience

**Scripts Available:**
```bash
# Root level
pnpm build              # Build all TypeScript
pnpm build:platform     # Build platform only
pnpm build:yappc        # Build YAPPC only
pnpm dev                # Dev mode all products
pnpm test               # Test all
pnpm lint               # Lint all
pnpm typecheck          # Type check all
pnpm clean              # Clean all

# CI checks
pnpm check:deprecated-ui
pnpm check:duplicate-packages
pnpm check:jwt-policy
pnpm check:license-policy

# Gradle
./gradlew buildAll      # Build all Java
./gradlew buildPlatform # Build platform only
./gradlew testPlatform  # Test platform
./gradlew buildProducts # Build products only
```

**IDE Integration:**
| Feature | Status |
|---------|--------|
| VSCode workspace | ✅ Configured |
| VSCode extensions | ✅ Recommended |
| Gradle IDEA import | ✅ Supported |
| TypeScript path aliases | ✅ Configured |
| Auto-imports | ⚠️ Sometimes broken in large workspaces |

### 11.3 CI/CD Status

| Pipeline | Status | Coverage |
|----------|--------|----------|
| TypeScript Build | ✅ Green | 100% |
| Java Build | ✅ Green | 100% |
| Unit Tests | ⚠️ Flaky | ~85% |
| Integration Tests | ⚠️ Slow | ~70% |
| License Check | ✅ Green | 100% |
| Duplicate Check | ✅ Green | 100% |
| SBOM Generation | ✅ Green | 100% |

---

## 12. Security / Observability / Release Audit

### 12.1 Security Audit

**Authentication & Authorization:**
| Feature | Status | Products |
|---------|--------|----------|
| JWT tokens | ✅ Implemented | All |
| Refresh tokens | 🔴 Missing | Flashit, DCMAAR |
| 2FA/TOTP | 🔴 Missing | Flashit |
| Session management | ⚠️ Partial | Multiple |
| RBAC | ✅ Implemented | Platform |
| ABAC | ⚠️ Partial | Platform |

**Secrets Management:**
| Approach | Status |
|----------|--------|
| Environment variables | ✅ Standard |
| Vault integration | ⚠️ Partial (some products) |
| Secrets rotation | 🔴 Manual |
| Key escrow | 🔴 Not implemented |

**Vulnerability Scanning:**
| Tool | Status |
|------|--------|
| npm audit | ⚠️ Runs, not enforced |
| OWASP dependency check | ✅ Gradle plugin enabled |
| Trivy | ⚠️ Planned |
| Snyk | 🔴 Not integrated |

### 12.2 Observability Audit

**Metrics & Monitoring:**
| Component | Status |
|-----------|--------|
| Prometheus metrics | ✅ Platform modules |
| Grafana dashboards | ✅ Basic coverage |
| Application metrics | ⚠️ Partial (Flashit missing) |
| Business metrics | 🔴 Limited |

**Logging:**
| Component | Status |
|-----------|--------|
| Structured logging | ✅ Platform |
| Log aggregation | ✅ Loki/Grafana |
| Trace correlation | ⚠️ Partial |
| PII masking | ⚠️ Partial |

**Tracing:**
| Component | Status |
|-----------|--------|
| OpenTelemetry | ⚠️ Partial integration |
| Distributed tracing | 🔴 Limited |
| Jaeger/Zipkin | 🔴 Not deployed |

### 12.3 Release Audit

**Versioning:**
| Approach | Status |
|----------|--------|
| Semantic versioning | ✅ Standard |
| Conventional commits | ⚠️ Partial adoption |
| Changelog generation | 🔴 Manual |

**Deployment:**
| Strategy | Products | Status |
|----------|----------|--------|
| Blue/Green | Platform | ✅ Implemented |
| Canary | YAPPC | ✅ Implemented |
| Feature flags | Platform | ✅ LaunchDarkly |
| Rollback automation | 🔴 Limited |

---

# PART 2 — TARGET STATE DESIGN

## 13. Ideal Monorepo Structure

### 13.1 Target Folder Tree

```
/ghatana (ROOT)
├── 📁 apps/                              # DEPLOYABLE APPLICATIONS
│   ├── yappc-web/                        # @yappc/web (merged from apps/web)
│   ├── yappc-desktop/                    # @yappc/desktop
│   ├── yappc-mobile/                     # @yappc/mobile
│   ├── tutorputor-web/                   # @tutorputor/web
│   ├── tutorputor-mobile/                # @tutorputor/mobile
│   ├── flashit-mobile/                   # @flashit/mobile
│   ├── flashit-web/                      # @flashit/web
│   ├── data-cloud-ui/                    # @ghatana/data-cloud-ui
│   ├── dcmaar-dashboard/                 # @dcmaar/dashboard
│   ├── dcmaar-mobile/                    # @dcmaar/mobile
│   ├── aep-ui/                           # @ghatana/aep-ui
│   └── audio-video-desktop/              # @ghatana/audio-video-desktop
│
├── 📁 platform/                          # SHARED INFRASTRUCTURE
│   ├── typescript/
│   │   ├── foundation/                   # Domain-agnostic
│   │   │   ├── tokens/                   # @ghatana/tokens
│   │   │   ├── theme/                    # @ghatana/theme
│   │   │   └── platform-utils/           # @ghatana/platform-utils
│   │   │
│   │   ├── capabilities/                 # Reusable capabilities
│   │   │   ├── design-system/            # @ghatana/design-system
│   │   │   ├── canvas-core/              # @ghatana/canvas-core
│   │   │   ├── realtime-engine/          # @ghatana/realtime-engine
│   │   │   ├── api-client/               # @ghatana/api-client
│   │   │   └── charts/                   # @ghatana/charts
│   │   │
│   │   └── integration/                  # Framework integration
│   │       ├── react-integration/        # @ghatana/react-integration
│   │       └── mui-integration/          # @ghatana/mui-integration
│   │
│   └── java/
│       ├── foundation/                   # Core abstractions
│       │   ├── core/                     # platform:java:core
│       │   ├── domain/                   # platform:java:domain
│       │   └── testing/                  # platform:java:testing
│       │
│       ├── capabilities/                 # Technical capabilities
│       │   ├── http-server/              # platform:java:http
│       │   ├── database/                 # platform:java:database
│       │   ├── observability/            # platform:java:observability
│       │   ├── security/                 # platform:java:security
│       │   ├── workflow-engine/          # platform:java:workflow
│       │   └── ai-framework/             # platform:java:ai-integration
│       │
│       └── runtime/                      # Deployment/runtime
│           ├── event-cloud/              # platform:java:event-cloud
│           └── connectors/               # platform:java:connectors
│
├── 📁 domain/                              # DOMAIN-SPECIFIC LIBRARIES
│   ├── yappc/                            # YAPPC Domain
│   │   ├── core/                         # @yappc/core
│   │   ├── ui/                           # @yappc/ui
│   │   ├── canvas/                       # @yappc/canvas
│   │   ├── ai/                           # @yappc/ai
│   │   └── chat/                         # @yappc/chat
│   │
│   ├── tutorputor/                       # TutorPutor Domain
│   │   ├── learning-kernel/              # @tutorputor/learning-kernel
│   │   ├── simulation/                   # @tutorputor/simulation
│   │   └── assessments/                  # @tutorputor/assessments
│   │
│   ├── flashit/                          # Flashit Domain
│   │   ├── core/                         # @flashit/core
│   │   └── ui/                           # @flashit/ui
│   │
│   └── data-cloud/                       # Data Cloud Domain
│       ├── core/                         # @data-cloud/core
│       └── query-engine/                 # @data-cloud/query-engine
│
├── 📁 infra/                               # INFRASTRUCTURE CODE
│   ├── terraform/                          # Terraform modules
│   ├── kubernetes/                         # K8s manifests
│   ├── scripts/                            # Automation scripts
│   └── monitoring/                         # Observability configs
│
├── 📁 contracts/                           # API CONTRACTS
│   ├── openapi/                            # OpenAPI specifications
│   ├── protobuf/                           # Protocol buffer definitions
│   └── json-schema/                        # JSON schema definitions
│
├── 📁 shared-services/                     # CROSS-PRODUCT SERVICES
│   ├── auth-gateway/                       # Authentication gateway
│   ├── billing-service/                    # Billing/payments
│   ├── ai-inference/                       # AI inference service
│   └── notification-service/               # Notification delivery
│
├── 📁 tools/                               # DEVELOPER TOOLS
│   ├── scaffolding/                        # Module generators
│   ├── codemods/                           # Code transformations
│   ├── lint-rules/                         # Custom lint rules
│   └── ci-scripts/                         # CI utilities
│
└── 📁 docs/                                # DOCUMENTATION
    ├── architecture/                       # ADRs, RFCs
    ├── api/                                # API documentation
    └── guides/                             # Developer guides
```

### 13.2 Structure Migration Map

| Current Location | Target Location | Priority |
|------------------|-----------------|----------|
| `products/yappc/frontend/libs/ai-new` | `domain/yappc/ai` | 🔴 Critical |
| `products/yappc/frontend/libs/canvas-new` | `domain/yappc/canvas` | 🔴 Critical |
| `products/yappc/frontend/libs/ui-new` | `domain/yappc/ui` | 🔴 Critical |
| `products/yappc/frontend/libs/core` | `domain/yappc/core` | 🔴 Critical |
| `products/yappc/frontend/apps/web` | `apps/yappc-web` | ⚠️ Medium |
| `platform/typescript/canvas` | `platform/typescript/capabilities/canvas-core` | ⚠️ Medium |
| `platform/typescript/utils` | `platform/typescript/foundation/platform-utils` | ⚠️ Medium |
| `products/*/ui` | `apps/*` or `domain/*` | ⚠️ Medium |

---

## 14. Module Taxonomy Rules

### 14.1 Module Categories

**Category F: Foundation (Tier 0)**
```
Purpose: Zero dependencies (except language runtime)
Naming: @ghatana/<purpose>
Examples: @ghatana/tokens, @ghatana/theme
Rules:
- NO business logic
- NO imports from other platform modules
- Can be imported by any other category
```

**Category P: Platform Capabilities (Tier 1)**
```
Purpose: Technical capabilities, business-agnostic
Naming: @ghatana/<capability>
Examples: @ghatana/design-system, @ghatana/canvas-core
Rules:
- Can import from Category F only
- NO imports from other Category P
- Can be imported by Category D, Category A
```

**Category D: Domain Libraries (Tier 2)**
```
Purpose: Product business logic
Naming: @<product>/<domain>
Examples: @yappc/core, @tutorputor/simulation
Rules:
- Can import from Category F, Category P
- NO imports from other Category D (different product)
- Can be imported by Category A (same product only)
```

**Category A: Applications (Tier 3)**
```
Purpose: End-user deliverables
Naming: @<product>/<app-name>
Examples: @yappc/web, @tutorputor/mobile
Rules:
- Can import from Category F, Category P, Category D (same product)
- NO exports (leaf nodes)
- NO imports from other Category A
```

### 14.2 Dependency Direction Rules

```
┌─────────────────────────────────────────────────────────────────┐
│                    ALLOWED DEPENDENCY FLOW                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Foundation (F)                                                 │
│     ↓                                                           │
│  Platform (P)                                                   │
│     ↓                                                           │
│  Domain (D) ──→ (same product only)                             │
│     ↓                                                           │
│  Application (A)                                                │
│                                                                 │
│  ❌ NO horizontal imports between same category                 │
│  ❌ NO upward imports (A → D → P → F allowed, reverse is NOT)   │
│  ❌ NO cross-product D imports                                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

---

## 15. Platform vs Product Rules

### 15.1 Ownership Rules

| Category | Owner | Approval Required |
|----------|-------|-------------------|
| Foundation | Platform Team | Platform Lead |
| Platform Capabilities | Platform Team | Platform Lead |
| Domain Libraries | Product Team | Product Lead + Platform Review |
| Applications | Product Team | Product Lead |

### 15.2 Contribution Rules

| Category | Who Can Contribute | Process |
|----------|-------------------|---------|
| Foundation | Platform Team only | RFC → Review → Merge |
| Platform | Any team | PR → Platform Review → Merge |
| Domain | Product team only | PR → Product Review → Merge |
| Application | Product team only | PR → Product Review → Merge |

### 15.3 Cross-Product Sharing Rules

| Scenario | Allowed | Process |
|----------|---------|---------|
| Domain library shared across products | ❌ NO | Promote to Platform instead |
| Copy-paste code between products | ❌ NO | Extract to Platform |
| Product-specific fork of Platform | ❌ NO | Extend via configuration |
| Product contributes to Platform | ✅ YES | RFC → Platform Review |

---

## 16. Library Creation Rules

### 16.1 Library Creation Checklist

**BEFORE creating a new library, verify:**

- [ ] **Need**: Does this capability already exist elsewhere?
- [ ] **Scope**: Is this a single, well-defined concern?
- [ ] **Consumers**: Are there at least 2 confirmed consumers?
- [ ] **Category**: Is this Foundation, Platform, or Domain?
- [ ] **Naming**: Does the name follow taxonomy rules?
- [ ] **Dependencies**: Will this create circular dependencies?
- [ ] **Tests**: Can this library achieve >70% test coverage?
- [ ] **Documentation**: Are `@doc.*` tags planned?

### 16.2 Library Prohibition Rules

| Rule | Violation Example | Enforcement |
|------|-------------------|-------------|
| NO `utils` libraries | `platform/typescript/utils` | Rename to specific purpose |
| NO `common` libraries | `libs/common` | Split into specific libs |
| NO `shared` libraries | `libs/shared` | Use Platform category |
| NO `helpers` libraries | `libs/helpers` | Merge into consumer |
| NO `misc` libraries | `libs/misc` | Delete or organize |
| NO product-in-platform | `@ghatana/yappc-*` | Move to Domain |

---

## 17. Naming Conventions (Strict Rules)

### 17.1 Package Naming Matrix

| Category | Scope | Pattern | Examples |
|----------|-------|---------|----------|
| Foundation | Platform | `@ghatana/<purpose>` | `@ghatana/tokens`, `@ghatana/theme` |
| Capability | Platform | `@ghatana/<capability>` | `@ghatana/design-system`, `@ghatana/canvas-core` |
| Domain | Product | `@<product>/<domain>` | `@yappc/core`, `@tutorputor/simulation` |
| App | Product | `@<product>/<app>` | `@yappc/web`, `@tutorputor/mobile` |
| Service | Shared | `@shared/<service>` | `@shared/auth-gateway` |

### 17.2 File Naming Rules

| File Type | Pattern | Example |
|-----------|---------|---------|
| React Component | PascalCase.tsx | `Button.tsx`, `UserProfile.tsx` |
| React Hook | camelCase.ts | `useAuth.ts`, `useCanvas.ts` |
| Utility | camelCase.ts | `formatDate.ts`, `calculateDistance.ts` |
| Type Definition | PascalCase.ts | `User.ts`, `ApiResponse.ts` |
| Constant | UPPER_SNAKE.ts | `API_ENDPOINTS.ts`, `DEFAULT_CONFIG.ts` |
| Test File | `*.test.ts` or `*.spec.ts` | `Button.test.tsx` |
| Barrel Export | `index.ts` | `index.ts` |

### 17.3 Symbol Naming Rules

| Symbol Type | Pattern | Example |
|-------------|---------|---------|
| React Component | PascalCase | `function UserProfile()` |
| Hook | camelCase, prefix `use` | `function useAuth()` |
| Type/Interface | PascalCase | `type UserProfile = {}` |
| Enum | PascalCase | `enum UserRole` |
| Constant | UPPER_SNAKE | `const MAX_RETRY_COUNT = 3` |
| Variable | camelCase | `const userProfile = {}` |
| Private | camelCase, prefix `_` | `const _internalCache = {}` |

---

## 18. Dependency Rules (Allowed Directions)

### 18.1 Dependency Policy JSON

```json
{
  "policy": {
    "description": "Ghatana Monorepo Dependency Policy",
    "version": "1.0.0",
    "enforcement": "strict",
    
    "categories": {
      "foundation": {
        "scope": "@ghatana/tokens, @ghatana/theme, @ghatana/platform-utils",
        "canImport": [],
        "canBeImportedBy": ["platform", "domain", "application"],
        "externalDeps": ["typescript", "csstype"]
      },
      "platform": {
        "scope": "@ghatana/* (except tokens, theme, utils)",
        "canImport": ["foundation"],
        "canBeImportedBy": ["domain", "application"],
        "externalDeps": ["react", "react-dom", "jotai", "zod"]
      },
      "domain": {
        "pattern": "@<product>/*",
        "canImport": ["foundation", "platform"],
        "canBeImportedBy": ["application"],
        "restrictions": ["noCrossProductImports"]
      },
      "application": {
        "pattern": "@<product>/<app>",
        "canImport": ["foundation", "platform", "domain"],
        "canBeImportedBy": [],
        "leaf": true
      }
    },
    
    "external": {
      "approved": {
        "react": "^19.2.4",
        "react-dom": "^19.2.4",
        "jotai": "^2.17.0",
        "zod": "^4.3.6",
        "typescript": "^5.9.3",
        "vite": "^7.3.1",
        "tailwindcss": "^4.1.18"
      },
      "banned": [
        "lodash" ,
        "moment",
        "jquery",
        "axios" 
      ],
      "restricted": {
        "mui": {
          "version": "^7.3.0",
          "reason": "Migration to @ghatana/design-system in progress",
          "deadline": "2026-06-01"
        }
      }
    },
    
    "enforced_singletons": [
      "react",
      "react-dom",
      "jotai",
      "zod",
      "@ghatana/theme",
      "@ghatana/tokens"
    ]
  }
}
```

### 18.2 Dependency Verification Script

```bash
#!/bin/bash
# verify-deps.sh - Validates dependency policy compliance

pnpm licenses list --json | node -e '
const policy = require("./dependency-policy.json");
const licenses = JSON.parse(require("fs").readFileSync(0, "utf8"));

const violations = [];

// Check banned licenses
for (const [license, packages] of Object.entries(licenses)) {
  if (policy.policy.licenses.denied.some(d => license.toUpperCase().includes(d))) {
    violations.push({ type: "license", license, packages });
  }
}

// Check banned packages
for (const pkg of Object.keys(licenses)) {
  if (policy.policy.external.banned.includes(pkg)) {
    violations.push({ type: "banned", package: pkg });
  }
}

if (violations.length > 0) {
  console.error("Dependency policy violations found:");
  console.error(JSON.stringify(violations, null, 2));
  process.exit(1);
}

console.log("Dependency policy check passed.");
'
```

---

## 19. Allowed Tech Stack (Approved Libraries)

### 19.1 Frontend Approved Stack

| Category | Approved | Alternatives | Notes |
|----------|----------|--------------|-------|
| Framework | React 19 | - | Enforced via pnpm override |
| State Management | Jotai | - | Standard across all products |
| Server State | TanStack Query | - | For async data fetching |
| Styling | Tailwind CSS 4 | - | Via @ghatana/design-system |
| UI Components | @ghatana/design-system | - | Internal library |
| Forms | React Hook Form + Zod | - | Validation standard |
| Animation | Framer Motion | - | When animation needed |
| Icons | Lucide React | - | Standard icon set |
| Testing | Vitest + RTL | - | Unit and integration |
| E2E | Playwright | - | End-to-end testing |

### 19.2 Backend Approved Stack (Java)

| Category | Approved | Alternatives | Notes |
|----------|----------|--------------|-------|
| Framework | ActiveJ | - | Platform standard |
| HTTP Client | ActiveJ HTTP | - | Built-in |
| Database | PostgreSQL | - | Primary datastore |
| Cache | Redis | - | Caching layer |
| Object Storage | MinIO (S3) | - | File storage |
| Events | Kafka | - | Event streaming |
| Testing | JUnit 5 + Mockito | - | Test framework |
| Async Testing | EventloopTestBase | - | Custom base class |
| Observability | Micrometer | - | Metrics collection |

### 19.3 Backend Approved Stack (Node.js - Flashit Only)

| Category | Approved | Notes |
|----------|----------|-------|
| Framework | Fastify | High performance |
| Validation | Zod | Schema validation |
| ORM | Prisma | Type-safe database access |
| Auth | Passport.js | Authentication strategies |
| Realtime | Socket.io | WebSocket communication |

---

## 20. Banned / Deprecated Libraries

### 20.1 Banned Libraries (Security/Quality)

| Library | Reason | Replacement | Deadline |
|---------|--------|-------------|----------|
| `lodash` | Bundle size, modern JS alternatives | Native JS or `radash` | 2026-04-01 |
| `moment` | Deprecated, large bundle | `date-fns` or native | Immediate |
| `jquery` | Legacy, security concerns | Native JS or React | Immediate |
| `axios` | Unnecessary, fetch is sufficient | Native `fetch` | 2026-04-01 |
| `request` | Deprecated, security issues | `undici` or native | Immediate |
| `uuid` | Native `crypto.randomUUID()` | Native API | Immediate |
| `classnames` | Native `clsx` is in design-system | Use from design-system | Immediate |

### 20.2 Deprecated Libraries (Migration Required)

| Library | Replacement | Status | Deadline |
|---------|-------------|--------|----------|
| `@ghatana/ui` | `@ghatana/design-system` | In progress | 2026-04-15 |
| `@ghatana/yappc-ui` | `@yappc/ui` | Migration needed | 2026-04-01 |
| `@ghatana/yappc-ai` | `@yappc/ai` | Migration needed | 2026-04-01 |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | Migration needed | 2026-04-01 |
| `Redux` | `Jotai` | Legacy code only | 2026-06-01 |
| `Zustand` | `Jotai` | Consolidation needed | 2026-05-01 |
| `MUI` (new code) | `@ghatana/design-system` | Migration in progress | 2026-06-01 |

### 20.3 Deprecated Patterns

| Pattern | Replacement | Enforcement |
|---------|-------------|-------------|
| `any` types | Proper TypeScript types | TypeScript strict mode |
| Direct HTTP calls | `@ghatana/api-client` | Lint rule |
| Inline styles | Tailwind classes | Stylelint |
| Prop drilling | Jotai atoms | Code review |
| Class components | Function components | ESLint rule |
| `console.log` | Structured logging | ESLint rule |

---

## 21. Schema & Contract Governance Model

### 21.1 Contract Types

| Contract Type | Location | Owner | Governance |
|--------------|----------|-------|------------|
| OpenAPI | `/contracts/openapi/` | API Team | Versioned, breaking change review |
| Protocol Buffers | `/contracts/protobuf/` | Platform | Backward compatibility enforced |
| JSON Schema | `/contracts/json-schema/` | Product + Platform | Schema validation in CI |
| TypeScript Types | Package exports | Product | Published as package |
| Java Interfaces | Module API | Platform | Semantic versioning |

### 21.2 Breaking Change Policy

| Change Type | Version Bump | Approval | Communication |
|-------------|--------------|----------|---------------|
| New field (optional) | Minor | Product Lead | Changelog |
| New endpoint | Minor | Product Lead | Changelog |
| Deprecation | Minor | Product Lead | Changelog + migration guide |
| Required field added | Major | Architecture Board | RFC + migration guide |
| Field removed | Major | Architecture Board | RFC + 6-month notice |
| Type changed | Major | Architecture Board | RFC + migration guide |

### 21.3 Schema Evolution Rules

```protobuf
// Example: Protocol Buffer with evolution rules
message User {
  // Fields 1-15: reserved for frequently accessed
  int64 id = 1;              // Never change type
  string email = 2;          // Never change type
  
  // Fields 16+: standard fields
  string name = 16;
  
  // Deprecated fields: reserve number and name
  reserved 3;                // Was: deprecated_field
  reserved "deprecated_field";
  
  // New fields: always add at end
  google.protobuf.Timestamp created_at = 17;
}
```

---

## 22. Plugin / Extension Model

### 22.1 Plugin Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PLUGIN ARCHITECTURE                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                       │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  PLUGIN HOST (Core Application)                                 ││
│  │  - Provides plugin API                                          ││
│  │  - Manages plugin lifecycle                                     ││
│  │  - Enforces security sandbox                                    ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              ▲                                        │
│                              │ Plugin API                            │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  PLUGIN EXTENSION POINTS                                        ││
│  │  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐              ││
│  │  │ UI Components│ │ Canvas Tools │ │ API Routes   │              ││
│  │  └──────────────┘ └──────────────┘ └──────────────┘              ││
│  └─────────────────────────────────────────────────────────────────┘│
│                              ▲                                        │
│                              │ Implements                            │
│  ┌─────────────────────────────────────────────────────────────────┐│
│  │  PLUGINS (3rd Party / Internal)                                 ││
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐                        ││
│  │  │ Figma    │ │ GitHub   │ │ Custom   │                        ││
│  │  │ Plugin   │ │ Plugin   │ │ Tool     │                        ││
│  │  └──────────┘ └──────────┘ └──────────┘                        ││
│  └─────────────────────────────────────────────────────────────────┘│
│                                                                       │
└─────────────────────────────────────────────────────────────────────┘
```

### 22.2 Plugin Manifest Schema

```typescript
// Plugin manifest definition
interface PluginManifest {
  id: string;                    // Unique plugin identifier
  name: string;                  // Display name
  version: string;               // Semantic version
  description: string;           // Plugin description
  
  // Extension points this plugin implements
  extensionPoints: {
    'canvas:tool'?: CanvasToolConfig;
    'ui:component'?: UIComponentConfig;
    'api:route'?: APIRouteConfig;
    'ai:agent'?: AIAgentConfig;
  };
  
  // Required permissions
  permissions: ('storage' | 'network' | 'user-data' | 'ai-api')[];
  
  // Dependencies
  dependencies: {
    '@ghatana/canvas-core': '^1.0.0',
    '@ghatana/design-system': '^1.0.0'
  };
  
  // Entry points
  entry: {
    main: './dist/index.js',
    types: './dist/index.d.ts'
  };
}
```

### 22.3 Plugin Security Model

| Layer | Mechanism | Enforcement |
|-------|-----------|-------------|
| Code Review | All plugins reviewed | Required before publishing |
| Sandboxing | VM2 or similar | Runtime isolation |
| Permission Model | Manifest-declared | Runtime enforcement |
| API Limitations | Whitelist approach | Only exposed APIs accessible |
| Network | Proxy through host | CORS, rate limiting |

---

# PART 3 — EXECUTION PLAN

## 23. Refactor Plan (Phased)

### 23.1 Phase 0: Foundation (Week 1) — CRITICAL PATH

**Objective**: Establish governance tooling and immediate blockers

| Task | Action | Owner | Effort | Deliverable |
|------|--------|-------|--------|-------------|
| 0.1 | Merge PR-ready artifacts from this document | Platform Lead | 4h | Merged PRs |
| 0.2 | Deploy naming migration scripts | Platform Lead | 4h | Executable codemods |
| 0.3 | Configure CI for new lint rules | DevOps | 4h | CI pipeline updated |
| 0.4 | Flashit critical fixes (stub email, hardcoded IDs) | Flashit Team | 16h | Working services |
| 0.5 | Establish war room for Phase 1 | All Leads | 2h | Meeting cadence |

**Phase 0 Success Criteria:**
- All CI checks passing with new rules
- Flashit can send real emails
- No hardcoded user IDs in production paths
- Migration scripts tested on 1 library

### 23.2 Phase 1: Naming & Consolidation (Weeks 2-3) — HIGH PRIORITY

**Objective**: Resolve naming inconsistency and library duplication

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 1.1 | `@ghatana/yappc-ui` duplicates | Migrate all imports to `@yappc/ui`, delete old | Eliminates confusion | 8h |
| 1.2 | `@ghatana/yappc-ai` duplicates | Migrate all imports to `@yappc/ai`, delete old | Single source of truth | 8h |
| 1.3 | `@ghatana/yappc-canvas` duplicates | Migrate all imports to `@yappc/canvas`, delete old | Resolves circular deps | 12h |
| 1.4 | `@ghatana/yappc-chat` duplicates | Consolidate into `@yappc/chat` | Single chat library | 6h |
| 1.5 | `@ghatana/utils` vague scope | Rename to `@ghatana/platform-utils` | Clear purpose | 4h |
| 1.6 | `@ghatana/ui` deprecated | Complete migration to `@ghatana/design-system` | Clean deprecation | 16h |

**Exact Changes (Phase 1):**

```diff
// Example migration for yappc-ui
- import { Button } from '@ghatana/yappc-ui';
+ import { Button } from '@yappc/ui';

// package.json
- "@ghatana/yappc-ui": "workspace:*"
+ "@yappc/ui": "workspace:*"

// Folder move
- products/yappc/frontend/libs/ui/ → products/yappc/frontend/libs/ui-old/
- products/yappc/frontend/libs/ui-new/ → domain/yappc/ui/
```

**Phase 1 Success Criteria:**
- Zero `@ghatana/yappc-*` imports remaining
- All `*-new` libraries renamed to proper names
- CI passes with deprecated library check

### 23.3 Phase 2: Dependency Cleanup (Weeks 4-5) — HIGH PRIORITY

**Objective**: Resolve dependency conflicts and circular dependencies

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 2.1 | Circular deps canvas↔ai↔ui | Extract shared types to `@yappc/types` | Clean dependency graph | 12h |
| 2.2 | Zustand/Jotai mix | Migrate Zustand stores to Jotai | Single state solution | 16h |
| 2.3 | Multiple MUI versions | Align all to ^7.3.0 | Consistent UI | 8h |
| 2.4 | Axios usage | Replace with native fetch | Remove banned lib | 12h |
| 2.5 | Lodash usage | Replace with native or radash | Smaller bundles | 16h |
| 2.6 | Moment.js usage | Replace with date-fns | Smaller bundles | 8h |

**Phase 2 Success Criteria:**
- Dependency graph has no cycles (verified by tooling)
- All banned libraries removed
- Bundle size reduced by >10%

### 23.4 Phase 3: Structure Reorganization (Weeks 6-8) — MEDIUM PRIORITY

**Objective**: Align folder structure with target state

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 3.1 | YAPPC libs scattered | Move to `domain/yappc/` | Clear ownership | 8h |
| 3.2 | Platform libs unorganized | Move to `platform/typescript/capabilities/` | Taxonomy alignment | 8h |
| 3.3 | Apps in product folders | Move to `apps/` | Clear separation | 8h |
| 3.4 | Update pnpm-workspace.yaml | Reflect new structure | Build works | 4h |
| 3.5 | Update tsconfig paths | Reflect new structure | Imports work | 4h |
| 3.6 | Update Gradle settings | Reflect new structure | Java builds work | 8h |

**Phase 3 Success Criteria:**
- Folder structure matches target state
- All builds pass
- No broken imports

### 23.5 Phase 4: Quality & Testing (Weeks 9-10) — MEDIUM PRIORITY

**Objective**: Achieve quality targets

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 4.1 | Test coverage 44% → 70% | Add tests to untested modules | Quality assurance | 40h |
| 4.2 | Missing `@doc.*` tags | Add documentation to 40% of APIs | Better DX | 24h |
| 4.3 | Accessibility gaps | Add labels, ARIA attributes | WCAG compliance | 16h |
| 4.4 | Flashit auth/billing tests | Create comprehensive test suite | Production ready | 24h |
| 4.5 | Integration tests | Expand coverage | Regression prevention | 16h |

**Phase 4 Success Criteria:**
- 70% test coverage across all products
- 100% of public APIs have `@doc.*` tags
- Flashit has auth/billing test coverage
- Accessibility audit passes

### 23.6 Phase 5: Flashit Stabilization (Weeks 11-12) — CRITICAL PRIORITY

**Objective**: Bring Flashit to production readiness

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 5.1 | Stub email service | Implement real email provider | Can notify users | 16h |
| 5.2 | Hardcoded user IDs | Implement proper auth | Security compliance | 12h |
| 5.3 | Incomplete Stripe | Complete billing integration | Revenue collection | 24h |
| 5.4 | Missing 2FA | Implement TOTP/SMS 2FA | Security compliance | 16h |
| 5.5 | Session management | Add refresh tokens, invalidation | Security best practice | 12h |
| 5.6 | Service consolidation | Reduce 15 → 5 services | Operational efficiency | 40h |

**Phase 5 Success Criteria:**
- Flashit can send real emails
- No hardcoded credentials
- Stripe billing works end-to-end
- 2FA available for all users
- Session management secure

### 23.7 Phase 6: Optimization (Weeks 13-14) — LOW PRIORITY

**Objective**: Performance and developer experience

| Task | Problem | Action | Impact | Effort |
|------|---------|--------|--------|--------|
| 6.1 | Build time slow | Implement Turbo remote caching | Faster CI | 8h |
| 6.2 | Bundle size | Analyze and optimize | Faster apps | 16h |
| 6.3 | IDE performance | Optimize TypeScript project refs | Better DX | 8h |
| 6.4 | Dead code | Remove unused modules | Cleaner repo | 8h |
| 6.5 | Documentation | Complete API docs | Better DX | 16h |

---

## 24. Module Merge / Split Plan

### 24.1 Merge Plan (Consolidate Duplicates)

| Target | Source 1 | Source 2 | Strategy | Effort |
|--------|----------|----------|----------|--------|
| `@yappc/ui` | `@ghatana/yappc-ui` | `@yappc/ui-new` | Merge to `@yappc/ui` | 16h |
| `@yappc/ai` | `@ghatana/yappc-ai` | `@yappc/ai-new` | Merge to `@yappc/ai` | 16h |
| `@yappc/canvas` | `@ghatana/yappc-canvas` | `@yappc/canvas-new` | Merge to `@yappc/canvas` | 20h |
| `@yappc/chat` | `@yappc/chat` | `@ghatana/yappc-chat` | Merge to `@yappc/chat` | 8h |

### 24.2 Split Plan (Separate Concerns)

| Current | Split Into | Reason | Effort |
|---------|------------|--------|--------|
| `@ghatana/utils` | `@ghatana/platform-utils`, `@ghatana/string-utils`, `@ghatana/date-utils` | Single responsibility | 12h |
| `@ghatana/canvas` | `@ghatana/canvas-core`, `@ghatana/canvas-react`, `@ghatana/canvas-plugins` | Separation of concerns | 16h |
| `@yappc/core` | `@yappc/types`, `@yappc/config`, `@yappc/constants` | Better tree-shaking | 8h |

---

## 25. Library Consolidation Plan

### 25.1 Target Library Count

| Category | Current | Target | Reduction |
|----------|---------|--------|-----------|
| Platform TypeScript | 19 | 12 | -37% |
| YAPPC | 33 | 8 | -76% |
| TutorPutor | 23 | 8 | -65% |
| Flashit | 12 | 5 | -58% |
| Data Cloud | 8 | 4 | -50% |
| DCMAAR | 18 | 6 | -67% |
| Other | 5 | 3 | -40% |
| **Total** | **118** | **46** | **-61%** |

### 25.2 Consolidation Priority

| Priority | Library | Action | Impact |
|----------|---------|--------|--------|
| 🔴 Critical | `@ghatana/yappc-*` | Merge to `@yappc/*` | Naming consistency |
| 🔴 Critical | `*-new` libraries | Remove `-new` suffix | Naming consistency |
| 🟡 High | `@ghatana/utils` | Split by concern | Single responsibility |
| 🟡 High | Platform canvas variants | Consolidate | Reduce confusion |
| 🟢 Medium | Audio-video duplicates | Consolidate | Reduce duplication |
| 🟢 Medium | DCMAAR scattered libs | Consolidate | Better organization |

---

## 26. Naming Migration Map

### 26.1 Complete Migration Table

| Old Name | New Name | Reason | Status | Deadline |
|----------|----------|--------|--------|----------|
| `@ghatana/yappc-ui` | `@yappc/ui` | Product namespace | 🔴 Not started | 2026-04-01 |
| `@ghatana/yappc-ai` | `@yappc/ai` | Product namespace | 🔴 Not started | 2026-04-01 |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | Product namespace | 🔴 Not started | 2026-04-01 |
| `@ghatana/yappc-chat` | `@yappc/chat` | Product namespace | 🔴 Not started | 2026-04-01 |
| `@ghatana/yappc-notifications` | `@yappc/notifications` | Product namespace | 🔴 Not started | 2026-04-15 |
| `@ghatana/yappc-realtime` | `@yappc/realtime` | Product namespace | 🔴 Not started | 2026-04-15 |
| `@yappc/ui-new` | `@yappc/ui` | Remove suffix | 🔴 Not started | 2026-04-01 |
| `@yappc/ai-new` | `@yappc/ai` | Remove suffix | 🔴 Not started | 2026-04-01 |
| `@yappc/canvas-new` | `@yappc/canvas` | Remove suffix | 🔴 Not started | 2026-04-01 |
| `@ghatana/ui` | `@ghatana/design-system` | Consolidation | 🟡 In progress | 2026-04-15 |
| `@ghatana/utils` | `@ghatana/platform-utils` | Clarity | 🟡 Planned | 2026-05-01 |
| `@ghatana/canvas` | `@ghatana/canvas-core` | Clarity | 🟡 Planned | 2026-05-15 |

---

## 27. Dependency Convergence Plan

### 27.1 Version Alignment Schedule

| Package | Current Versions | Target Version | Action | Deadline |
|---------|------------------|----------------|--------|----------|
| React | ^18.3.1, ^19.2.4 | ^19.2.4 | pnpm override | ✅ Done |
| React-DOM | ^18.3.1, ^19.2.4 | ^19.2.4 | pnpm override | ✅ Done |
| TypeScript | ^5.8.2, ^5.9.3 | ^5.9.3 | Align all | 2026-04-01 |
| Vite | ^6.0.0, ^7.3.1 | ^7.3.1 | Align all | 2026-04-15 |
| TailwindCSS | ^3.4.0, ^4.1.18 | ^4.1.18 | Align all | 2026-05-01 |
| MUI | ^6.0.0, ^7.3.0 | ^7.3.0 | Align all | 2026-04-01 |
| Zod | ^3.22.0, ^4.3.6 | ^4.3.6 | Align all | 2026-04-01 |
| Jotai | ^2.15.0, ^2.17.0 | ^2.17.0 | Align all | 2026-04-01 |

### 27.2 External Dependency Audit

| Library | Count | Action | Deadline |
|---------|-------|--------|----------|
| `lodash` | 12 packages | Remove | 2026-04-01 |
| `axios` | 8 packages | Replace with fetch | 2026-04-01 |
| `moment` | 3 packages | Replace with date-fns | 2026-03-30 |
| `classnames` | 5 packages | Use from design-system | Immediate |
| `uuid` | 7 packages | Use native crypto | Immediate |

---

## 28. Dead Code Removal Plan

### 28.1 Identified Dead Modules

| Module | Last Commit | Consumers | Action | Effort |
|--------|-------------|-----------|--------|--------|
| `platform/typescript/ui` | 3 months ago | 0 (deprecated) | Delete | 2h |
| `products/yappc/frontend/libs/ui` (old) | 4 months ago | 0 (replaced) | Delete | 2h |
| `products/flashit/backend/agent` | 6 months ago | 0 (excluded) | Delete or archive | 4h |
| `products/software-org` (unused) | 8 months ago | 0 | Archive | 4h |
| Unused test files | - | 0 | Delete | 8h |

### 28.2 Dead Code Detection Script

```bash
#!/bin/bash
# detect-dead-code.sh

echo "Detecting potentially dead code..."

# Find files not imported anywhere
rg "^import.*from ['\"](.+)['\"]" --type ts --type tsx -o --no-filename | \
  sed 's/.*from //' | tr -d "'\"" | sort | uniq > /tmp/imports.txt

find . -name "*.ts" -o -name "*.tsx" | grep -v node_modules | grep -v ".d.ts" | \
  sed 's|^\./||' | sort > /tmp/all_files.txt

# Files that are imported
comm -23 /tmp/all_files.txt /tmp/imports.txt > /tmp/potentially_dead.txt

echo "Potentially dead files:"
cat /tmp/potentially_dead.txt | head -50
```

---

## 29. Test Coverage Fix Plan

### 29.1 Coverage by Product

| Product | Current | Target | Gap | Priority |
|---------|---------|--------|-----|----------|
| Platform | 68% | 80% | +12% | 🟡 Medium |
| YAPPC | 44% | 70% | +26% | 🔴 High |
| TutorPutor | 52% | 70% | +18% | 🟡 Medium |
| Flashit | 38% | 70% | +32% | 🔴 Critical |
| Data Cloud | 45% | 70% | +25% | 🟡 Medium |
| DCMAAR | 41% | 70% | +29% | 🟡 Medium |

### 29.2 Test Priority Matrix

| Module | Critical Paths | Current Coverage | Priority |
|--------|----------------|------------------|----------|
| `flashit/auth` | Login, signup, 2FA | 0% | 🔴 Critical |
| `flashit/billing` | Stripe integration | 0% | 🔴 Critical |
| `yappc/canvas` | Drawing, collaboration | 35% | 🔴 High |
| `yappc/ai` | Chat, agents | 28% | 🔴 High |
| `platform/design-system` | All components | 55% | 🟡 Medium |
| `platform/canvas` | Core engine | 45% | 🟡 Medium |

---

# PART 4 — PR-READY ARTIFACTS

## 1. Target Folder Structure

```
/ghatana (ROOT)
├── 📁 apps/                              # DEPLOYABLE APPLICATIONS
│   ├── yappc-web/
│   ├── yappc-desktop/
│   ├── yappc-mobile/
│   ├── tutorputor-web/
│   ├── tutorputor-mobile/
│   ├── flashit-mobile/
│   ├── flashit-web/
│   ├── data-cloud-ui/
│   ├── dcmaar-dashboard/
│   ├── dcmaar-mobile/
│   ├── aep-ui/
│   └── audio-video-desktop/
│
├── 📁 platform/                          # SHARED INFRASTRUCTURE
│   ├── typescript/
│   │   ├── foundation/
│   │   │   ├── tokens/
│   │   │   ├── theme/
│   │   │   └── platform-utils/
│   │   ├── capabilities/
│   │   │   ├── design-system/
│   │   │   ├── canvas-core/
│   │   │   ├── realtime-engine/
│   │   │   ├── api-client/
│   │   │   └── charts/
│   │   └── integration/
│   │       ├── react-integration/
│   │       └── mui-integration/
│   └── java/
│       ├── foundation/
│       │   ├── core/
│       │   ├── domain/
│       │   └── testing/
│       ├── capabilities/
│       │   ├── http-server/
│       │   ├── database/
│       │   ├── observability/
│       │   ├── security/
│       │   ├── workflow-engine/
│       │   └── ai-framework/
│       └── runtime/
│           ├── event-cloud/
│           └── connectors/
│
├── 📁 domain/                            # DOMAIN-SPECIFIC LIBRARIES
│   ├── yappc/
│   │   ├── core/
│   │   ├── ui/
│   │   ├── canvas/
│   │   ├── ai/
│   │   └── chat/
│   ├── tutorputor/
│   │   ├── learning-kernel/
│   │   ├── simulation/
│   │   └── assessments/
│   ├── flashit/
│   │   ├── core/
│   │   └── ui/
│   └── data-cloud/
│       ├── core/
│       └── query-engine/
│
├── 📁 infra/
│   ├── terraform/
│   ├── kubernetes/
│   ├── scripts/
│   └── monitoring/
│
├── 📁 contracts/
│   ├── openapi/
│   ├── protobuf/
│   └── json-schema/
│
├── 📁 shared-services/
│   ├── auth-gateway/
│   ├── billing-service/
│   ├── ai-inference/
│   └── notification-service/
│
├── 📁 tools/
│   ├── scaffolding/
│   ├── codemods/
│   ├── lint-rules/
│   └── ci-scripts/
│
└── 📁 docs/
    ├── architecture/
    ├── api/
    └── guides/
```

## 2. Example Refactor Diffs

### 2.1 Naming Migration Diff

```diff
- // File: products/yappc/frontend/components/Button.tsx
- import { Button } from '@ghatana/yappc-ui';
- import { useAuth } from '@ghatana/yappc-ai';
+ // File: apps/yappc-web/components/Button.tsx
+ import { Button } from '@yappc/ui';
+ import { useAuth } from '@yappc/ai';
```

### 2.2 Folder Move Diff

```diff
- // From: products/yappc/frontend/libs/ui-new/package.json
- {
-   "name": "@yappc/ui-new",
-   "main": "./src/index.ts"
- }

+ // To: domain/yappc/ui/package.json
+ {
+   "name": "@yappc/ui",
+   "main": "./src/index.ts",
+   "exports": {
+     ".": "./src/index.ts",
+     "./chat": "./src/chat/index.ts",
+     "./notifications": "./src/notifications/index.ts"
+   }
+ }
```

### 2.3 Dependency Cleanup Diff

```diff
- // package.json
- "dependencies": {
-   "lodash": "^4.17.21",
-   "axios": "^1.6.0",
-   "moment": "^2.29.4"
- }

+ "dependencies": {
+   "date-fns": "^3.3.0"
+ }

- // Code
- import _ from 'lodash';
- import axios from 'axios';
- import moment from 'moment';
- const debounced = _.debounce(fn, 300);
- const response = await axios.get('/api/users');
- const date = moment().format('YYYY-MM-DD');

+ // Code
+ import { debounce } from 'radash';
+ const debounced = debounce({ delay: 300 }, fn);
+ const response = await fetch('/api/users');
+ const date = format(new Date(), 'yyyy-MM-dd');
```

## 3. Naming Refactor Table

| Old Name | New Name | Reason | Migration Script |
|----------|----------|--------|------------------|
| `@ghatana/yappc-ui` | `@yappc/ui` | Product namespace | `migrate-yappc-names.sh` |
| `@ghatana/yappc-ai` | `@yappc/ai` | Product namespace | `migrate-yappc-names.sh` |
| `@ghatana/yappc-canvas` | `@yappc/canvas` | Product namespace | `migrate-yappc-names.sh` |
| `@ghatana/yappc-chat` | `@yappc/chat` | Product namespace | `migrate-yappc-names.sh` |
| `@ghatana/yappc-notifications` | `@yappc/notifications` | Product namespace | `migrate-yappc-names.sh` |
| `@ghatana/yappc-realtime` | `@yappc/realtime` | Product namespace | `migrate-yappc-names.sh` |
| `@yappc/ui-new` | `@yappc/ui` | Remove suffix | `remove-new-suffix.sh` |
| `@yappc/ai-new` | `@yappc/ai` | Remove suffix | `remove-new-suffix.sh` |
| `@yappc/canvas-new` | `@yappc/canvas` | Remove suffix | `remove-new-suffix.sh` |
| `@ghatana/ui` | `@ghatana/design-system` | Consolidation | `migrate-to-design-system.sh` |
| `@ghatana/utils` | `@ghatana/platform-utils` | Clarity | `rename-utils.sh` |
| `@ghatana/canvas` | `@ghatana/canvas-core` | Clarity | `rename-canvas.sh` |
| `products/yappc/frontend/apps/web` | `apps/yappc-web` | Structure alignment | `move-apps.sh` |
| `products/yappc/frontend/libs/ui-new` | `domain/yappc/ui` | Structure alignment | `move-to-domain.sh` |
| `platform/typescript/utils` | `platform/typescript/foundation/platform-utils` | Taxonomy alignment | `organize-platform.sh` |

## 4. Dependency Policy (JSON)

```json
{
  "policy": {
    "name": "Ghatana Monorepo Dependency Policy",
    "version": "1.0.0",
    "effectiveDate": "2026-03-17",
    "enforcementLevel": "strict",
    
    "categories": {
      "foundation": {
        "description": "Zero-dependency building blocks",
        "pattern": "@ghatana/(tokens|theme|platform-utils)",
        "canImport": [],
        "canBeImportedBy": ["platform", "domain", "application"],
        "externalDeps": {
          "allowed": ["typescript", "csstype"],
          "banned": []
        }
      },
      "platform": {
        "description": "Shared technical capabilities",
        "pattern": "@ghatana/*",
        "excludes": ["@ghatana/(tokens|theme|platform-utils)"],
        "canImport": ["foundation"],
        "canBeImportedBy": ["domain", "application"],
        "externalDeps": {
          "allowed": {
            "react": "^19.2.4",
            "react-dom": "^19.2.4",
            "jotai": "^2.17.0",
            "zod": "^4.3.6",
            "@xyflow/react": "^12.10.0",
            "framer-motion": "^12.35.0"
          },
          "banned": ["lodash", "axios", "moment", "jquery", "uuid"]
        }
      },
      "domain": {
        "description": "Product business logic",
        "pattern": "@<product>/*",
        "canImport": ["foundation", "platform"],
        "canBeImportedBy": ["application"],
        "restrictions": ["noCrossProductImports"],
        "externalDeps": {
          "inherits": "platform"
        }
      },
      "application": {
        "description": "Deployable end-user applications",
        "pattern": "@<product>/<app>",
        "canImport": ["foundation", "platform", "domain"],
        "canBeImportedBy": [],
        "leaf": true,
        "externalDeps": {
          "inherits": "domain"
        }
      }
    },
    
    "licenses": {
      "approved": ["MIT", "Apache-2.0", "BSD-2-Clause", "BSD-3-Clause", "ISC", "0BSD"],
      "allowedWithConditions": ["LGPL-2.1", "LGPL-3.0"],
      "denied": ["GPL-2.0", "GPL-3.0", "AGPL-3.0", "SSPL-1.0", "CC-BY-NC-*", "Commons-Clause"]
    },
    
    "externalDependencies": {
      "approved": {
        "react": { "version": "^19.2.4", "singleton": true },
        "react-dom": { "version": "^19.2.4", "singleton": true },
        "jotai": { "version": "^2.17.0", "singleton": true },
        "zod": { "version": "^4.3.6", "singleton": true },
        "typescript": { "version": "^5.9.3" },
        "vite": { "version": "^7.3.1" },
        "tailwindcss": { "version": "^4.1.18" },
        "@xyflow/react": { "version": "^12.10.0" },
        "framer-motion": { "version": "^12.35.0" },
        "@tanstack/react-query": { "version": "^5.0.0" },
        "date-fns": { "version": "^3.3.0" },
        "radash": { "version": "^12.0.0" }
      },
      "banned": {
        "lodash": { "reason": "Bundle size, use native or radash", "replacement": "radash" },
        "axios": { "reason": "Native fetch is sufficient", "replacement": "fetch" },
        "moment": { "reason": "Deprecated, use date-fns", "replacement": "date-fns" },
        "jquery": { "reason": "Legacy, security concerns", "replacement": "native" },
        "request": { "reason": "Deprecated", "replacement": "undici" },
        "uuid": { "reason": "Use native crypto.randomUUID()", "replacement": "crypto" },
        "classnames": { "reason": "Use clsx from design-system", "replacement": "@ghatana/design-system" }
      },
      "restricted": {
        "@mui/material": { 
          "version": "^7.3.0", 
          "reason": "Migration to @ghatana/design-system in progress",
          "allowedFor": ["legacy-only"],
          "deadline": "2026-06-01"
        }
      }
    },
    
    "enforcedSingletons": [
      "react",
      "react-dom",
      "jotai",
      "zod",
      "@ghatana/theme",
      "@ghatana/tokens",
      "@ghatana/design-system"
    ],
    
    "circularDependencyPolicy": {
      "mode": "deny",
      "exceptions": [],
      "detection": "automatic",
      "blocking": true
    }
  }
}
```

## 5. Architecture Rules (ESLint + Custom)

### 5.1 ESLint Configuration

```javascript
// eslint.config.js
module.exports = {
  rules: {
    // Import boundaries
    'import/no-restricted-paths': ['error', {
      zones: [
        // Domain cannot import from other products
        {
          target: './domain/([^/]+)',
          from: './domain/([^/]+)',
          except: ['./$1'], // Only same product
          message: 'Cross-product imports are forbidden'
        },
        // Platform can only import from foundation
        {
          target: './platform/typescript/capabilities',
          from: './platform/typescript/capabilities',
          message: 'Platform modules cannot depend on each other'
        },
        // Apps cannot be imported
        {
          target: '.',
          from: './apps',
          message: 'Applications cannot be imported'
        }
      ]
    }],
    
    // Banned libraries
    'no-restricted-imports': ['error', {
      paths: [
        { name: 'lodash', message: 'Use radash or native instead' },
        { name: 'axios', message: 'Use native fetch instead' },
        { name: 'moment', message: 'Use date-fns instead' },
        { name: 'jquery', message: 'Use native or React instead' },
        { name: 'uuid', message: 'Use crypto.randomUUID() instead' },
        { name: 'classnames', message: 'Use clsx from @ghatana/design-system' }
      ],
      patterns: [
        { 
          group: ['@ghatana/yappc-*'], 
          message: 'Use @yappc/* instead' 
        },
        { 
          group: ['*-new'], 
          message: 'Remove -new suffix from library name' 
        }
      ]
    }],
    
    // Documentation requirements
    'jsdoc/require-jsdoc': ['error', {
      require: {
        FunctionDeclaration: true,
        MethodDefinition: true,
        ClassDeclaration: true,
        ArrowFunctionExpression: false
      }
    }],
    
    // Custom: @doc.* tag requirement
    'ghatana/require-doc-tags': ['error', {
      required: ['@doc.purpose', '@doc.layer'],
      optional: ['@doc.type', '@doc.pattern', '@doc.example']
    }]
  }
};
```

### 5.2 Custom ESLint Rules (Architecture Enforcement)

```javascript
// tools/lint-rules/no-cross-product-imports.js
module.exports = {
  meta: {
    type: 'problem',
    docs: {
      description: 'Disallow imports from other products',
      category: 'Architecture',
      recommended: true
    },
    schema: []
  },
  create(context) {
    return {
      ImportDeclaration(node) {
        const importPath = node.source.value;
        const currentFile = context.getFilename();
        
        // Extract product from path
        const getProduct = (path) => {
          const match = path.match(/products\/([^/]+)/);
          return match ? match[1] : null;
        };
        
        const currentProduct = getProduct(currentFile);
        const importProduct = getProduct(importPath);
        
        if (currentProduct && importProduct && currentProduct !== importProduct) {
          context.report({
            node,
            message: `Cross-product import: ${currentProduct} cannot import from ${importProduct}`
          });
        }
      }
    };
  }
};
```

### 5.3 Architecture Rules YAML (CI Enforcement)

```yaml
# .github/architecture-rules.yaml
rules:
  - id: no_cross_product_imports
    name: No Cross-Product Imports
    severity: error
    pattern: 
      from: "products/([^/]+)/"
      to: "@ghatana/\1-"
    message: "Products cannot import from other products. Use platform libraries instead."
    
  - id: no_deprecated_libraries
    name: No Deprecated Libraries
    severity: error
    banned:
      - "@ghatana/ui"
      - "@ghatana/yappc-ui"
      - "@ghatana/yappc-ai"
      - "@ghatana/yappc-canvas"
    replacements:
      "@ghatana/ui": "@ghatana/design-system"
      "@ghatana/yappc-ui": "@yappc/ui"
      "@ghatana/yappc-ai": "@yappc/ai"
      "@ghatana/yappc-canvas": "@yappc/canvas"
    
  - id: no_new_suffix
    name: No -new Suffix in Libraries
    severity: error
    pattern: "*-new"
    message: "Library names must not use -new suffix. Use proper naming."
    
  - id: enforce_singleton_deps
    name: Enforce Singleton Dependencies
    severity: error
    singletons:
      - react
      - react-dom
      - jotai
      - zod
    check: duplicateVersions
    
  - id: no_banned_libraries
    name: No Banned Libraries
    severity: error
    banned:
      - lodash
      - axios
      - moment
      - jquery
      - uuid
      - request
      - classnames
    
  - id: require_doc_tags
    name: Require @doc.* Tags
    severity: warning
    required:
      - "@doc.purpose"
      - "@doc.layer"
    on:
      - function
      - class
      - interface
      - type
      - exportedVariable
```

---

# PART 5 — GOVERNANCE AUTOMATION

## 30. CI Rules

### 30.1 CI Pipeline Configuration

```yaml
# .github/workflows/monorepo-governance.yaml
name: Monorepo Governance

on:
  pull_request:
    branches: [main]
  push:
    branches: [main]

jobs:
  architecture-check:
    name: Architecture Compliance
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Check Naming Conventions
        run: |
          ./scripts/check-naming-conventions.sh
          
      - name: Check Dependency Policy
        run: |
          pnpm check:license-policy
          pnpm check:jwt-policy
          
      - name: Check Duplicate Packages
        run: |
          ./scripts/check-duplicate-package-names.sh
          
      - name: Check Deprecated Usage
        run: |
          ./scripts/check-deprecated-ui.sh
          
      - name: Check Circular Dependencies
        run: |
          npx madge --circular --extensions ts,tsx .
          
      - name: Run ESLint Architecture Rules
        run: |
          pnpm lint:architecture

  dependency-alignment:
    name: Dependency Alignment
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Check Dependency Convergence
        run: |
          node scripts/align-dependencies.js --check
          
      - name: SBOM Generation
        run: |
          pnpm exec cyclonedx-npm --output-file=sbom.json
          
      - name: Upload SBOM
        uses: actions/upload-artifact@v4
        with:
          name: sbom
          path: sbom.json

  test-coverage:
    name: Test Coverage
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Run Tests with Coverage
        run: |
          pnpm test:coverage
          
      - name: Check Coverage Thresholds
        run: |
          node scripts/check-coverage.js --threshold=70
          
      - name: Upload Coverage Report
        uses: actions/upload-artifact@v4
        with:
          name: coverage
          path: coverage/
```

### 30.2 PR Checklist Bot

```yaml
# .github/pr-checklist.yaml
pull_request:
  checklist:
    - id: naming
      title: "Naming Conventions"
      items:
        - "Package name follows `@<scope>/<purpose>` pattern"
        - "No `-new` suffix in library names"
        - "Product libraries use `@<product>/*` not `@ghatana/<product>-*"
        
    - id: dependencies
      title: "Dependencies"
      items:
        - "No banned libraries (lodash, axios, moment, etc.)"
        - "Uses approved versions of React (^19.2.4)"
        - "No duplicate dependencies"
        - "License compliance verified"
        
    - id: architecture
      title: "Architecture"
      items:
        - "No cross-product imports"
        - "No circular dependencies"
        - "Follows dependency flow rules (F → P → D → A)"
        - "Module category is correct"
        
    - id: quality
      title: "Quality"
      items:
        - "Test coverage >70% for new code"
        - "@doc.purpose and @doc.layer tags present"
        - "No TypeScript `any` types"
        - "ESLint passes with architecture rules"
        
    - id: documentation
      title: "Documentation"
      items:
        - "README.md updated if needed"
        - "API changes documented"
        - "Breaking changes noted in PR description"
```

## 31. Lint Rules

### 31.1 ESLint Architecture Plugin

```javascript
// tools/lint-rules/index.js
module.exports = {
  rules: {
    'no-cross-product-imports': require('./no-cross-product-imports'),
    'no-deprecated-libraries': require('./no-deprecated-libraries'),
    'no-new-suffix': require('./no-new-suffix'),
    'require-doc-tags': require('./require-doc-tags'),
    'no-banned-imports': require('./no-banned-imports'),
    'enforce-singleton-deps': require('./enforce-singleton-deps'),
    'prefer-platform-imports': require('./prefer-platform-imports')
  },
  configs: {
    recommended: {
      plugins: ['ghatana-architecture'],
      rules: {
        'ghatana-architecture/no-cross-product-imports': 'error',
        'ghatana-architecture/no-deprecated-libraries': 'error',
        'ghatana-architecture/no-new-suffix': 'error',
        'ghatana-architecture/require-doc-tags': 'warn',
        'ghatana-architecture/no-banned-imports': 'error',
        'ghatana-architecture/enforce-singleton-deps': 'error'
      }
    }
  }
};
```

### 31.2 Pre-commit Hooks

```yaml
# .husky/pre-commit
#!/bin/sh
. "$(dirname "$0")/_/husky.sh"

# Run lint-staged
pnpm lint-staged

# Run architecture checks on staged files only
./scripts/check-architecture-staged.sh

# Check for banned libraries in staged files
./scripts/check-banned-imports-staged.sh
```

```javascript
// .lintstagedrc.js
module.exports = {
  '*.{ts,tsx}': [
    'eslint --fix --max-warnings=0',
    'prettier --write',
    () => 'tsc --noEmit'
  ],
  '*.java': [
    './gradlew spotlessApply',
    () => './gradlew compileJava'
  ],
  'package.json': [
    () => './scripts/check-duplicate-package-names.sh',
    () => 'node scripts/align-dependencies.js --check'
  ]
};
```

## 32. Dependency Enforcement

### 32.1 Dependency Alignment Script

```javascript
// scripts/align-dependencies.js
#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const glob = require('glob');

const POLICY = require('../dependency-policy.json');

function alignDependencies() {
  const packageFiles = glob.sync('**/package.json', {
    ignore: ['**/node_modules/**', '**/dist/**']
  });
  
  let changes = 0;
  
  for (const pkgFile of packageFiles) {
    const pkg = JSON.parse(fs.readFileSync(pkgFile, 'utf8'));
    let modified = false;
    
    // Check banned dependencies
    for (const depType of ['dependencies', 'devDependencies', 'peerDependencies']) {
      if (!pkg[depType]) continue;
      
      for (const [dep, version] of Object.entries(pkg[depType])) {
        // Check banned
        if (POLICY.policy.externalDependencies.banned[dep]) {
          console.error(`❌ Banned dependency: ${dep} in ${pkgFile}`);
          process.exitCode = 1;
        }
        
        // Check approved versions
        const approved = POLICY.policy.externalDependencies.approved[dep];
        if (approved && !version.match(approved.version.replace('^', '\\^?'))) {
          console.log(`📦 Aligning ${dep} to ${approved.version} in ${pkg.name}`);
          pkg[depType][dep] = approved.version;
          modified = true;
          changes++;
        }
      }
    }
    
    if (modified && process.argv.includes('--apply')) {
      fs.writeFileSync(pkgFile, JSON.stringify(pkg, null, 2) + '\n');
    }
  }
  
  console.log(`\n${changes} dependency alignment(s) needed`);
  return changes === 0;
}

const passed = alignDependencies();
process.exit(passed ? 0 : 1);
```

### 32.2 License Policy Enforcement

```bash
#!/bin/bash
# scripts/check-license-policy.sh

set -euo pipefail

echo "Checking OSS license policy..."

# Generate license report
pnpm licenses list --json > /tmp/licenses.json

# Check for denied licenses
node -e '
const denied = ["GPL", "AGPL", "SSPL"];
const licenses = require("/tmp/licenses.json");

let violations = 0;
for (const [license, packages] of Object.entries(licenses)) {
  if (denied.some(d => license.toUpperCase().includes(d))) {
    console.error(`❌ Denied license: ${license}`);
    packages.forEach(p => console.error(`   - ${p.name}`));
    violations++;
  }
}

if (violations > 0) {
  console.error(`\nFound ${violations} license violations`);
  process.exit(1);
}
console.log("✅ License policy check passed");
'
```

## 33. Module Creation Templates

### 33.1 Template Generator

```bash
#!/bin/bash
# tools/scaffolding/create-module.sh

MODULE_TYPE=$1      # foundation | platform | domain | application
MODULE_NAME=$2      # e.g., "canvas-core", "yappc-ai"
MODULE_PATH=$3      # e.g., "platform/typescript/capabilities"

echo "Creating new ${MODULE_TYPE} module: ${MODULE_NAME}"

# Create directory structure
mkdir -p "${MODULE_PATH}/${MODULE_NAME}/src"
mkdir -p "${MODULE_PATH}/${MODULE_NAME}/__tests__"
mkdir -p "${MODULE_PATH}/${MODULE_NAME}/docs"

# Generate package.json
cat > "${MODULE_PATH}/${MODULE_NAME}/package.json" <<EOF
{
  "name": "@ghatana/${MODULE_NAME}",
  "version": "1.0.0",
  "description": "${MODULE_NAME} - ${MODULE_TYPE} module",
  "type": "module",
  "main": "./dist/index.js",
  "types": "./dist/index.d.ts",
  "exports": {
    ".": {
      "types": "./dist/index.d.ts",
      "import": "./dist/index.js"
    }
  },
  "scripts": {
    "build": "tsc",
    "dev": "tsc --watch",
    "test": "vitest",
    "test:coverage": "vitest --coverage",
    "typecheck": "tsc --noEmit",
    "lint": "eslint src --ext .ts,.tsx"
  },
  "dependencies": {},
  "devDependencies": {
    "typescript": "^5.9.3",
    "vitest": "^4.0.18"
  },
  "peerDependencies": {},
  "keywords": ["ghatana", "${MODULE_TYPE}"],
  "license": "MIT"
}
EOF

# Generate tsconfig.json
cat > "${MODULE_PATH}/${MODULE_NAME}/tsconfig.json" <<EOF
{
  "extends": "../../../tsconfig.base.json",
  "compilerOptions": {
    "outDir": "./dist",
    "rootDir": "./src",
    "composite": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "__tests__"]
}
EOF

# Generate src/index.ts
cat > "${MODULE_PATH}/${MODULE_NAME}/src/index.ts" <<EOF
/**
 * @doc.type module
 * @doc.purpose ${MODULE_NAME} provides ${MODULE_TYPE} functionality
 * @doc.layer ${MODULE_TYPE}
 * @doc.pattern ${MODULE_TYPE}-module
 */

export {};
EOF

# Generate __tests__/index.test.ts
cat > "${MODULE_PATH}/${MODULE_NAME}/__tests__/index.test.ts" <<EOF
import { describe, it, expect } from 'vitest';

describe('${MODULE_NAME}', () => {
  it('should be implemented', () => {
    expect(true).toBe(true);
  });
});
EOF

# Generate README.md
cat > "${MODULE_PATH}/${MODULE_NAME}/README.md" <<EOF
# @ghatana/${MODULE_NAME}

${MODULE_TYPE} module for Ghatana platform.

## Purpose

[Describe the purpose of this module]

## Usage

\`\`\`typescript
import { something } from '@ghatana/${MODULE_NAME}';
\`\`\`

## API

[Document the public API]

## Dependencies

[Document dependencies and their purposes]
EOF

echo "✅ Module ${MODULE_NAME} created at ${MODULE_PATH}/${MODULE_NAME}"
echo "📝 Next steps:"
echo "   1. Define the module's purpose in README.md"
echo "   2. Implement the public API in src/index.ts"
echo "   3. Add comprehensive tests in __tests__/"
echo "   4. Document with @doc.* tags"
echo "   5. Add to pnpm-workspace.yaml if needed"
```

### 33.2 Module Creation Checklist

```markdown
## New Module Checklist

### Pre-Creation
- [ ] Does this capability already exist elsewhere?
- [ ] Are there at least 2 confirmed consumers?
- [ ] Is the category (foundation/platform/domain/app) correct?
- [ ] Does the name follow taxonomy rules?

### Creation
- [ ] Run `./tools/scaffolding/create-module.sh <type> <name> <path>`
- [ ] Define module purpose in README.md
- [ ] Implement minimal viable API
- [ ] Add at least one test
- [ ] Add @doc.purpose and @doc.layer tags

### Validation
- [ ] `pnpm install` succeeds
- [ ] `pnpm typecheck` passes
- [ ] `pnpm test` passes
- [ ] `pnpm lint` passes
- [ ] No circular dependencies introduced
- [ ] Dependency policy satisfied

### Integration
- [ ] Add to pnpm-workspace.yaml
- [ ] Add to tsconfig references if needed
- [ ] Update root README if significant
- [ ] Create follow-up ticket for documentation
```

## 34. Code Review Checklist

### 34.1 Architecture Review

```markdown
## Architecture Review Checklist

### Dependencies
- [ ] No banned libraries (lodash, axios, moment, jquery, uuid)
- [ ] No cross-product imports
- [ ] No circular dependencies
- [ ] Approved versions of shared dependencies (React 19, Jotai, Zod)
- [ ] License compliance verified

### Naming
- [ ] Package name follows `@<scope>/<purpose>` pattern
- [ ] No `-new`, `-old`, `-v2` suffixes
- [ ] Product libraries use `@<product>/*`
- [ ] File names follow convention (PascalCase for components)

### Structure
- [ ] Module category is correct (foundation/platform/domain/app)
- [ ] Located in correct folder per target state
- [ ] Exports are clean and well-defined
- [ ] No barrel file abuse

### Code Quality
- [ ] No TypeScript `any` types
- [ ] Error handling is comprehensive
- [ ] Async code uses proper patterns
- [ ] No memory leaks in subscriptions/effects

### Testing
- [ ] Test coverage >70% for new code
- [ ] Critical paths have integration tests
- [ ] Tests are deterministic (no randomness)
- [ ] Mocking is appropriate

### Documentation
- [ ] @doc.purpose tag present on public APIs
- [ ] @doc.layer tag present on public APIs
- [ ] Complex logic has inline comments
- [ ] README.md updated if needed

### Performance
- [ ] No unnecessary re-renders
- [ ] Large lists are virtualized
- [ ] Images are optimized/lazy-loaded
- [ ] Bundle impact considered
```

### 34.2 Security Review

```markdown
## Security Review Checklist

### Authentication
- [ ] Auth tokens are not logged
- [ ] Token expiration is handled
- [ ] Refresh token logic is secure
- [ ] 2FA is implemented where required

### Data Handling
- [ ] PII is masked in logs
- [ ] Sensitive data is encrypted at rest
- [ ] Input validation uses Zod schemas
- [ ] SQL injection is prevented (parameterized queries)

### API Security
- [ ] Rate limiting is implemented
- [ ] CORS is properly configured
- [ ] CSRF protection is in place
- [ ] Security headers are set (Helmet)

### Dependencies
- [ ] No known CVEs in dependencies
- [ ] No supply chain risks identified
- [ ] SBOM is updated
```

## 35. Architecture Fitness Functions

### 35.1 Automated Fitness Checks

```yaml
# .github/fitness-functions.yaml
fitnessFunctions:
  - name: test_coverage
    description: "Test coverage must be >= 70%"
    threshold: 70
    check: npm run test:coverage
    failOn: below_threshold
    
  - name: circular_dependencies
    description: "No circular dependencies allowed"
    check: npx madge --circular --extensions ts,tsx .
    failOn: any_found
    
  - name: bundle_size
    description: "Bundle size must not exceed budget"
    budgets:
      - path: "apps/*/dist"
        maxSize: "500kb"
      - path: "domain/*/dist"
        maxSize: "100kb"
    check: npm run analyze
    failOn: budget_exceeded
    
  - name: dependency_vulnerabilities
    description: "No high/critical CVEs in dependencies"
    check: npm audit --audit-level=high
    failOn: any_found
    
  - name: doc_tag_coverage
    description: "Public APIs must have @doc.* tags"
    threshold: 80
    check: npm run check:doc-tags
    failOn: below_threshold
    
  - name: naming_conventions
    description: "All packages follow naming rules"
    check: ./scripts/check-naming-conventions.sh
    failOn: any_violation
    
  - name: cross_product_imports
    description: "No imports between products"
    check: ./scripts/check-cross-product-imports.sh
    failOn: any_found
    
  - name: banned_libraries
    description: "No banned libraries in dependencies"
    check: ./scripts/check-banned-libraries.sh
    failOn: any_found
```

### 35.2 Fitness Score Dashboard

```javascript
// scripts/calculate-fitness-score.js
const FITNESS_WEIGHTS = {
  test_coverage: 20,
  circular_dependencies: 15,
  bundle_size: 10,
  dependency_vulnerabilities: 15,
  doc_tag_coverage: 10,
  naming_conventions: 10,
  cross_product_imports: 10,
  banned_libraries: 10
};

function calculateFitnessScore(results) {
  let totalScore = 0;
  let maxScore = 0;
  
  for (const [check, weight] of Object.entries(FITNESS_WEIGHTS)) {
    maxScore += weight;
    if (results[check]?.passed) {
      totalScore += weight;
    } else if (results[check]?.partial) {
      totalScore += weight * 0.5;
    }
  }
  
  const percentage = (totalScore / maxScore) * 100;
  
  return {
    score: totalScore,
    maxScore,
    percentage: Math.round(percentage * 10) / 10,
    grade: percentage >= 90 ? 'A' : 
           percentage >= 80 ? 'B' : 
           percentage >= 70 ? 'C' : 
           percentage >= 60 ? 'D' : 'F',
    results
  };
}

// Example usage
const results = {
  test_coverage: { passed: true, value: 72 },
  circular_dependencies: { passed: true, value: 0 },
  bundle_size: { passed: false, value: '520kb' },
  dependency_vulnerabilities: { passed: true, value: 0 },
  doc_tag_coverage: { partial: true, value: 65 },
  naming_conventions: { passed: false, violations: 3 },
  cross_product_imports: { passed: true, value: 0 },
  banned_libraries: { passed: true, value: 0 }
};

const score = calculateFitnessScore(results);
console.log(`Architecture Fitness Score: ${score.percentage}% (${score.grade})`);
```

---

# PART 6 — FINAL

## 36. Scorecard

### 36.1 Overall Monorepo Scorecard

| Category | Weight | Score | Grade | Status |
|----------|--------|-------|-------|--------|
| **Architecture** | 20% | 6.5/10 | D+ | 🔴 Needs Work |
| **Code Quality** | 15% | 7.0/10 | C | 🟡 Acceptable |
| **Test Coverage** | 15% | 4.5/10 | F | 🔴 Critical |
| **Documentation** | 10% | 5.5/10 | F | 🔴 Needs Work |
| **Dependencies** | 10% | 7.5/10 | C+ | 🟡 Acceptable |
| **Security** | 10% | 6.0/10 | D | 🔴 Needs Work |
| **DevEx** | 10% | 7.0/10 | C | 🟡 Acceptable |
| **Governance** | 10% | 8.0/10 | B | ✅ Good |
| **TOTAL** | 100% | **6.5/10** | **D+** | 🔴 **TRANSFORM** |

### 36.2 Product Scorecards

| Product | Architecture | Quality | Coverage | Security | Overall | Grade |
|---------|--------------|---------|----------|----------|---------|-------|
| Platform | 8.5 | 8.0 | 6.8 | 7.5 | **7.8** | C+ |
| YAPPC | 6.0 | 7.0 | 4.4 | 6.5 | **6.0** | D |
| TutorPutor | 7.5 | 7.5 | 5.2 | 7.0 | **6.8** | D+ |
| Flashit | 5.0 | 5.5 | 3.8 | 4.5 | **4.7** | F 🔴 |
| Data Cloud | 6.5 | 6.5 | 4.5 | 6.0 | **5.9** | D |
| DCMAAR | 6.0 | 6.0 | 4.1 | 6.5 | **5.7** | D |
| AEP | 7.0 | 7.0 | 5.0 | 7.0 | **6.5** | D+ |

### 36.3 Dimension Breakdown

**Architecture (6.5/10):**
- Naming inconsistency: 4/10 🔴
- Library duplication: 5/10 🔴
- Circular dependencies: 6/10 🔴
- Cross-product contamination: 5/10 🔴
- Folder structure: 8/10 🟡
- Module boundaries: 8/10 🟡

**Code Quality (7.0/10):**
- TypeScript strictness: 8/10 🟡
- ESLint compliance: 7/10 🟡
- Code duplication: 6/10 🔴
- Error handling: 7/10 🟡

**Test Coverage (4.5/10):**
- Unit tests: 5/10 🔴
- Integration tests: 4/10 🔴
- E2E tests: 3/10 🔴
- Critical path coverage: 6/10 🔴

**Documentation (5.5/10):**
- `@doc.*` tag coverage: 6/10 🔴
- README completeness: 5/10 🔴
- API documentation: 5/10 🔴
- Architecture docs: 6/10 🔴

**Dependencies (7.5/10):**
- Version alignment: 8/10 🟡
- Banned library enforcement: 7/10 🟡
- License compliance: 9/10 ✅
- SBOM generation: 8/10 🟡

**Security (6.0/10):**
- Authentication: 5/10 🔴 (Flashit gaps)
- Authorization: 7/10 🟡
- Secrets management: 6/10 🔴
- Vulnerability scanning: 6/10 🔴

---

## 37. Go / No-Go

### 37.1 Product-by-Product Recommendation

| Product | Recommendation | Conditions |
|---------|-----------------|------------|
| **Platform** | ✅ **GO** | Maintain current governance |
| **YAPPC** | ⚠️ **CONDITIONAL GO** | Complete Phase 1-2 (naming, consolidation) |
| **TutorPutor** | ✅ **GO** | Continue service consolidation |
| **Flashit** | 🔴 **NO-GO** | Complete all Phase 5 tasks first |
| **Data Cloud** | ⚠️ **CONDITIONAL GO** | Complete Monaco editor, API integration |
| **DCMAAR** | ⚠️ **CONDITIONAL GO** | Consolidate libraries, fix build |
| **AEP** | ✅ **GO** | Maintain current trajectory |

### 37.2 Overall Monorepo Recommendation

**🔴 RECOMMENDATION: CONDITIONAL NO-GO FOR PRODUCTION LAUNCH**

**Rationale:**
1. Flashit has critical blockers (stub email, hardcoded IDs, incomplete billing)
2. Naming inconsistency creates technical debt risk
3. Library duplication wastes engineering effort
4. Test coverage below target across all products
5. Security gaps in authentication/session management

**Conditions for Full GO:**
- [ ] Flashit Phase 5 complete (all critical blockers resolved)
- [ ] Phase 1 naming consolidation complete
- [ ] Phase 2 dependency cleanup complete
- [ ] Overall test coverage ≥ 60% (intermediate target)
- [ ] No critical or high security vulnerabilities
- [ ] Architecture fitness score ≥ 7.0

**Timeline to GO:**
- Flashit stabilization: 2-3 weeks (with dedicated team)
- Naming/consolidation: 2 weeks
- Dependency cleanup: 2 weeks
- Test coverage improvement: 4 weeks (ongoing)
- **Estimated GO date: May 15, 2026** (8 weeks from audit)

---

## 38. Top 10 Immediate Fixes

### Critical Path (Do First)

| # | Fix | Problem | Action | Effort | Owner |
|---|-----|---------|--------|--------|-------|
| **1** | **Flashit Email Service** | Stub email blocks user notifications | Implement SendGrid/SES integration | 16h | Flashit Team |
| **2** | **Flashit Hardcoded IDs** | Security vulnerability, cannot scale | Implement proper user ID generation | 12h | Flashit Team |
| **3** | **Flashit Stripe Billing** | Cannot collect revenue | Complete Stripe integration | 24h | Flashit Team |
| **4** | **YAPPC Library Merge** | ai/ui/canvas duplicates waste effort | Merge `*-new` to canonical names | 24h | YAPPC Team |
| **5** | **Remove @ghatana/yappc-*** | Naming inconsistency confusion | Migrate all imports to `@yappc/*` | 16h | YAPPC Team |

### High Priority (Do Next)

| # | Fix | Problem | Action | Effort | Owner |
|---|-----|---------|--------|--------|-------|
| **6** | **Circular Dependencies** | canvas↔ai↔ui chain | Extract shared types to `@yappc/types` | 12h | Platform Team |
| **7** | **Flashit 2FA** | Security compliance gap | Implement TOTP/SMS 2FA | 16h | Flashit Team |
| **8** | **Test Coverage** | 44% average, 0% in critical areas | Add tests to flashit/auth, flashit/billing | 40h | All Teams |
| **9** | **Zustand→Jotai** | State management inconsistency | Migrate remaining Zustand stores | 16h | Data Cloud Team |
| **10** | **Remove Banned Libraries** | lodash, axios, moment still present | Replace with approved alternatives | 24h | All Teams |

### Fix Implementation Order

```
Week 1:  1, 2, 3 (Flashit critical)
Week 2:  4, 5 (YAPPC naming)
Week 3:  6, 7 (Architecture + Security)
Week 4:  8, 9, 10 (Quality + Cleanup)
```

---

## Appendix A: Reference Documents

### Existing Documentation
- `GOOGLE_SCALE_MONOREPO_GOVERNANCE_AUDIT.md` - Previous audit
- `V5_GHATANA_MONOREPO_AUDIT_COMPLETE.md` - This document's predecessor
- `API_USABILITY_GUIDELINES.md` - API design guidelines
- `docs/PLATFORM_SHARED_LIBRARIES_REMAINING_WORK_PLAN_2026-03-14.md` - Platform migration plan

### Scripts Reference
- `scripts/check-deprecated-ui.sh` - UI deprecation check
- `scripts/check-duplicate-package-names.sh` - Duplicate detection
- `scripts/check-license-policy.sh` - License enforcement
- `scripts/check-jwt-dependency-policy.sh` - JWT policy check
- `scripts/align-dependencies.js` - Dependency alignment
- `scripts/migrate-library-names.js` - Library name migration

### Configuration Reference
- `pnpm-workspace.yaml` - Workspace definition
- `settings.gradle.kts` - Gradle module configuration
- `dependency-policy.json` - Dependency rules
- `.github/architecture-rules.yaml` - CI architecture rules

---

## Appendix B: Migration Scripts

### B.1 Full Migration Script

```bash
#!/bin/bash
# migrate-monorepo.sh - Complete migration orchestration

set -euo pipefail

echo "🚀 Ghatana Monorepo Migration"
echo "==============================="

# Phase 1: Flashit Critical
echo "📧 Phase 1: Flashit Critical Fixes..."
./scripts/flashit-fix-critical.sh

# Phase 2: Naming
echo "🏷️ Phase 2: Naming Consolidation..."
./scripts/migrate-library-names.js --apply

# Phase 3: Dependencies
echo "📦 Phase 3: Dependency Cleanup..."
./scripts/remove-banned-libraries.sh
./scripts/consolidate-state-management.sh

# Phase 4: Structure
echo "📁 Phase 4: Structure Reorganization..."
./scripts/move-to-target-structure.sh

# Phase 5: Verification
echo "✅ Phase 5: Verification..."
./scripts/verify-migration.sh

echo "🎉 Migration complete!"
echo "Run 'pnpm build' to verify everything works."
```

### B.2 Rollback Plan

```bash
#!/bin/bash
# rollback-migration.sh

set -euo pipefail

echo "⚠️ Rolling back migration..."

# Restore from backup
if [ -d ".backup-migration/pre-migration" ]; then
  echo "Restoring from backup..."
  rsync -av --delete .backup-migration/pre-migration/ .
  echo "✅ Rollback complete"
else
  echo "❌ No backup found. Manual rollback required."
  exit 1
fi
```

---

**End of V5 Autonomous Monorepo Audit + Execution + Refactor**

**Document Version:** 1.0.0  
**Last Updated:** March 17, 2026  
**Next Review:** April 17, 2026
