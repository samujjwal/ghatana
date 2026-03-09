# TutorPutor – Comprehensive Analysis & Enhancement Plan

> **Document Version:** 1.0.0  
> **Created:** 2025-12-03  
> **Author:** AI Analysis Agent  
> **Scope:** Full product analysis, feature inventory, and enhancement roadmap

---

## Executive Summary

TutorPutor is an **AI-powered tutoring and learning management platform** within the Ghatana ecosystem. It provides guided learning experiences through modules, assessments, and AI-driven tutoring assistance. This document analyzes the current implementation, identifies gaps, and proposes a comprehensive enhancement plan following the **reuse-first policy** and architectural guidelines from `copilot-instructions.md`.

---

## Part 1: Current State Analysis

### 1.1 Architecture Overview

```
products/tutorputor/
├── ai-service/              # Java-based AI entity collection (future)
│   └── entity-collection/
├── apps/
│   ├── api-gateway/         # Fastify-based API gateway
│   └── tutorputor-web/      # React SPA frontend
├── contracts/               # TypeScript types and service interfaces
│   └── v1/
├── docs/                    # Product documentation
└── services/                # Node.js microservices
    ├── tutorputor-ai-proxy/ # OpenAI-powered AI tutor
    ├── tutorputor-analytics/# Learning event tracking
    ├── tutorputor-assessment/# Quiz & assessment engine
    ├── tutorputor-cms/      # Content management (skeleton)
    ├── tutorputor-content/  # Content delivery (skeleton)
    ├── tutorputor-db/       # Prisma + SQLite database
    ├── tutorputor-learning/ # Enrollment & progress tracking
    └── tutorputor-marketplace/# Module marketplace (skeleton)
```

### 1.2 Technology Stack

| Layer            | Technology                                        | Status                    |
| ---------------- | ------------------------------------------------- | ------------------------- |
| Frontend         | React 18+, TanStack Query, React Router, Tailwind | ✅ Implemented            |
| API Gateway      | Fastify                                           | ✅ Implemented            |
| Services         | Node.js/TypeScript                                | ✅ Partially Implemented  |
| Database         | Prisma + SQLite                                   | ✅ Implemented            |
| AI Integration   | OpenAI API                                        | ✅ Basic Implementation   |
| State Management | TanStack Query                                    | ✅ Used for server state  |
| UI Components    | @ghatana/ui                                       | ✅ Imported via re-export |

### 1.3 Current Features Inventory

#### Implemented Features ✅

| Feature               | Location                                 | Description                                |
| --------------------- | ---------------------------------------- | ------------------------------------------ |
| **User Dashboard**    | `tutorputor-web/pages/DashboardPage.tsx` | Shows enrollments, recommended modules     |
| **Module Viewer**     | `tutorputor-web/pages/ModulePage.tsx`    | Module details, objectives, content blocks |
| **Enrollment System** | `tutorputor-learning/service.ts`         | Enroll, track progress, time spent         |
| **Progress Tracking** | `tutorputor-learning/service.ts`         | Update progress %, time tracking           |
| **Content Blocks**    | `contracts/v1/types.ts`                  | Text, rich_text, video, exercise, etc.     |
| **Assessment Engine** | `tutorputor-assessment/service.ts`       | Create, list, attempt, submit assessments  |
| **AI Tutor Query**    | `tutorputor-ai-proxy/service.ts`         | OpenAI-powered Q&A with citations          |
| **Analytics Events**  | `tutorputor-analytics/service.ts`        | Record learning events, get summaries      |
| **Marketplace**       | `contracts/v1/services.ts`               | Listing, pricing, visibility (skeleton)    |
| **CMS**               | `contracts/v1/services.ts`               | Create/update/publish modules (skeleton)   |

#### Skeleton/Incomplete Features ⚠️

| Feature             | Status        | Notes                                             |
| ------------------- | ------------- | ------------------------------------------------- |
| CMS Service         | Contract only | No implementation in `tutorputor-cms/src`         |
| Content Service     | Contract only | No implementation in `tutorputor-content/src`     |
| Marketplace Service | Contract only | No implementation in `tutorputor-marketplace/src` |
| Assessment UI       | Not started   | Backend ready, no frontend pages                  |
| Analytics Dashboard | Not started   | Backend ready, no frontend pages                  |
| AI Tutor Chat UI    | Not started   | Backend ready, no chat interface                  |
| User Authentication | Missing       | Uses stub tenant/user IDs                         |

---

## Part 2: Page & Route Structure

### 2.1 Current Routes

| Route            | Page Component  | Purpose                       |
| ---------------- | --------------- | ----------------------------- |
| `/`              | `DashboardPage` | Learner home with enrollments |
| `/dashboard`     | `DashboardPage` | Alias for home                |
| `/modules/:slug` | `ModulePage`    | Module detail and progress    |

### 2.2 Missing Pages (Required)

| Route                              | Page                   | Priority |
| ---------------------------------- | ---------------------- | -------- |
| `/assessments`                     | AssessmentListPage     | High     |
| `/assessments/:id`                 | AssessmentTakePage     | High     |
| `/assessments/:id/results`         | AssessmentResultsPage  | High     |
| `/ai-tutor`                        | AITutorChatPage        | High     |
| `/analytics`                       | AnalyticsDashboardPage | Medium   |
| `/profile`                         | UserProfilePage        | Medium   |
| `/settings`                        | SettingsPage           | Low      |
| `/cms`                             | CMSPage (for creators) | Medium   |
| `/marketplace`                     | MarketplacePage        | Low      |
| `/modules/:slug/lessons/:lessonId` | LessonPage             | High     |

---

## Part 3: Reusable Library Analysis (Reuse-First Policy)

### 3.1 Available Libraries in `libs/`

#### TypeScript Libraries (`libs/typescript/`)

| Library                        | Purpose                                   | TutorPutor Usage                       |
| ------------------------------ | ----------------------------------------- | -------------------------------------- |
| `@ghatana/ui`                  | UI components (Button, Card, Modal, etc.) | ✅ Used via re-export                  |
| `@ghatana/charts`              | Data visualization                        | ⚠️ Not used (should use for analytics) |
| `@ghatana/api`                 | HTTP client with middleware               | ❌ Not used (uses custom axios client) |
| `@ghatana/state`               | State management helpers                  | ❌ Not used (only TanStack Query)      |
| `@ghatana/realtime`            | WebSocket/SSE helpers                     | ❌ Not used (needed for live features) |
| `@ghatana/theme`               | Theming system                            | ⚠️ Partially (imports via ui)          |
| `@ghatana/tokens`              | Design tokens                             | ⚠️ Partially (imports via ui)          |
| `@ghatana/test-utils`          | Testing utilities                         | ⚠️ Should adopt                        |
| `@ghatana/design-system`       | Unified design facade                     | ⚠️ Should migrate to                   |
| `@ghatana/accessibility-audit` | A11y auditing                             | ❌ Not used                            |

#### Java Libraries (`libs/java/`)

| Library                  | Purpose               | TutorPutor Usage            |
| ------------------------ | --------------------- | --------------------------- |
| `ai-integration`         | AI/LLM integration    | ❌ Uses Node OpenAI instead |
| `observability`          | Metrics/tracing       | ❌ Not used                 |
| `auth` / `auth-platform` | Authentication        | ❌ Not used                 |
| `http-server`            | HTTP framework        | N/A (Node backend)          |
| `database`               | Database abstractions | N/A (Uses Prisma)           |
| `event-cloud`            | Event processing      | ❌ Could use for analytics  |

### 3.2 Reuse Violations & Fixes

| Current Implementation               | Should Use                        | Priority |
| ------------------------------------ | --------------------------------- | -------- |
| Custom `TutorPutorApiClient` (axios) | `@ghatana/api`                    | High     |
| No analytics charts                  | `@ghatana/charts`                 | High     |
| No WebSocket for live tutor          | `@ghatana/realtime`               | Medium   |
| Direct Tailwind classes              | `@ghatana/design-system` patterns | Medium   |
| No A11y auditing                     | `@ghatana/accessibility-audit`    | Medium   |
| Custom test setup                    | `@ghatana/test-utils`             | Low      |

---

## Part 4: Data Model Analysis

### 4.1 Current Prisma Schema Entities

| Entity                    | Purpose                           | Completeness |
| ------------------------- | --------------------------------- | ------------ |
| `Module`                  | Learning module metadata          | ✅ Complete  |
| `ModuleTag`               | Tags for modules                  | ✅ Complete  |
| `ModuleLearningObjective` | Bloom's taxonomy objectives       | ✅ Complete  |
| `ModuleContentBlock`      | Content items (text, video, etc.) | ✅ Complete  |
| `ModulePrerequisite`      | Module dependencies               | ✅ Complete  |
| `ModuleRevision`          | Version history                   | ✅ Complete  |
| `Enrollment`              | User-module relationship          | ✅ Complete  |
| `Assessment`              | Quiz/test definitions             | ✅ Complete  |
| `AssessmentObjective`     | Assessment-objective links        | ✅ Complete  |
| `AssessmentItem`          | Questions/items                   | ✅ Complete  |
| `AssessmentAttempt`       | User attempts                     | ✅ Complete  |
| `AssessmentDraft`         | AI-generated drafts               | ✅ Complete  |
| `LearningEvent`           | Analytics events                  | ✅ Complete  |
| `MarketplaceListing`      | Module listings                   | ✅ Complete  |

### 4.2 Missing Data Models

| Entity              | Purpose                           | Priority |
| ------------------- | --------------------------------- | -------- |
| `User`              | User profiles (currently stubbed) | Critical |
| `Tenant`            | Multi-tenant support              | Critical |
| `Lesson`            | Individual lessons within modules | High     |
| `TutorConversation` | AI chat history                   | High     |
| `TutorMessage`      | Individual chat messages          | High     |
| `LearningPath`      | Grouped modules/tracks            | Medium   |
| `Certificate`       | Completion certificates           | Low      |
| `Badge`             | Gamification achievements         | Low      |

---

## Part 5: Service Contracts Analysis

### 5.1 Defined Service Interfaces

| Service              | Methods                                                                                        | Implementation Status |
| -------------------- | ---------------------------------------------------------------------------------------------- | --------------------- |
| `ContentService`     | `getModuleBySlug`, `listModules`                                                               | ❌ Not implemented    |
| `LearningService`    | `getDashboard`, `enrollInModule`, `updateProgress`                                             | ✅ Implemented        |
| `AIProxyService`     | `handleTutorQuery`                                                                             | ✅ Implemented        |
| `AssessmentService`  | `listAssessments`, `getAssessment`, `generateAssessmentItems`, `startAttempt`, `submitAttempt` | ✅ Implemented        |
| `CMSService`         | `listModules`, `createModuleDraft`, `updateModuleDraft`, `publishModule`                       | ❌ Not implemented    |
| `AnalyticsService`   | `recordEvent`, `getSummary`                                                                    | ✅ Implemented        |
| `MarketplaceService` | `listListings`, `createListing`, `updateListing`                                               | ❌ Not implemented    |

---

## Part 6: Enhancement Plan

### Phase 1: Foundation & Compliance (Sprint 1-2)

**Goal:** Achieve reuse-first compliance and establish missing foundations.

#### 1.1 API Client Migration

**Priority:** Critical  
**Effort:** Medium

```typescript
// REPLACE: products/tutorputor/apps/tutorputor-web/src/api/tutorputorClient.ts
// WITH: @ghatana/api integration

import { createApiClient } from "@ghatana/api";

export const apiClient = createApiClient({
  baseURL: "/api",
  middleware: ["auth", "tenant", "correlation", "retry"],
});
```

#### 1.2 Design System Migration

**Priority:** High  
**Effort:** Medium

```typescript
// REPLACE: src/components/ui/index.ts
// Current: export * from '@ghatana/ui';
// Should be: Use @ghatana/design-system for unified approach

import { DesignSystemProvider } from "@ghatana/design-system";
```

#### 1.3 Authentication Integration

**Priority:** Critical  
**Effort:** High

- Integrate with `libs/java/auth-platform` or platform auth service
- Replace stubbed tenant/user IDs with real auth flow
- Add protected routes

### Phase 2: Core Feature Completion (Sprint 3-5)

**Goal:** Complete assessment UI, AI tutor chat, and analytics.

#### 2.1 Assessment Pages

| Page                  | Components                               | Routes                     |
| --------------------- | ---------------------------------------- | -------------------------- |
| AssessmentListPage    | AssessmentCard, FilterBar                | `/assessments`             |
| AssessmentTakePage    | QuestionRenderer, Timer, ProgressBar     | `/assessments/:id/take`    |
| AssessmentResultsPage | ScoreCard, FeedbackList, Recommendations | `/assessments/:id/results` |

**Reusable Components from @ghatana/ui:**

- `Card`, `Progress`, `Badge`, `Button`
- `Modal` for confirmation dialogs
- `Stepper` for multi-question navigation
- `Form` for answer submission

#### 2.2 AI Tutor Chat Interface

| Feature             | Implementation                     |
| ------------------- | ---------------------------------- |
| Chat UI             | Use `@ghatana/ui` Card, TextField  |
| Message History     | New `TutorConversation` model      |
| Real-time Streaming | `@ghatana/realtime` WebSocket      |
| Citations Display   | Custom component with module links |
| Suggested Questions | AI-generated follow-ups            |

**Route:** `/ai-tutor` and `/modules/:slug/tutor`

#### 2.3 Analytics Dashboard

| Widget                 | Library               | Purpose                       |
| ---------------------- | --------------------- | ----------------------------- |
| EventsOverTimeChart    | `@ghatana/charts`     | Line chart of learning events |
| ModuleCompletionsBar   | `@ghatana/charts`     | Bar chart by module           |
| LearnerActivityHeatmap | `@ghatana/charts`     | Activity patterns             |
| KPICards               | `@ghatana/ui` KpiCard | Key metrics                   |

**Route:** `/analytics`

### Phase 3: Content & CMS (Sprint 6-8)

**Goal:** Complete content management and delivery.

#### 3.1 CMS Service Implementation

```typescript
// products/tutorputor/services/tutorputor-cms/src/service.ts
export function createCMSService(prisma: TutorPrismaClient): CMSService {
  return {
    async listModules({ tenantId, status, cursor, limit }) {
      /* ... */
    },
    async createModuleDraft({ tenantId, authorId, input }) {
      /* ... */
    },
    async updateModuleDraft({ tenantId, moduleId, userId, patch }) {
      /* ... */
    },
    async publishModule({ tenantId, moduleId, userId }) {
      /* ... */
    },
  };
}
```

#### 3.2 Content Renderer

| Block Type                  | Renderer Component | Features           |
| --------------------------- | ------------------ | ------------------ |
| `text`                      | TextBlockRenderer  | Markdown support   |
| `rich_text`                 | RichTextRenderer   | WYSIWYG content    |
| `video`                     | VideoPlayer        | Playback, tracking |
| `interactive_visualization` | InteractiveViz     | D3/Chart.js        |
| `simulation`                | SimulationRunner   | Iframe or canvas   |
| `exercise`                  | ExerciseRenderer   | Input, validation  |
| `assessment_item_ref`       | InlineQuiz         | Embedded questions |
| `ai_tutor_prompt`           | TutorPrompt        | Trigger AI chat    |

#### 3.3 Lesson-Level Navigation

- Add `Lesson` model to Prisma schema
- Create `LessonPage` component
- Implement lesson sequencing and navigation

### Phase 4: Marketplace & Advanced (Sprint 9-12)

**Goal:** Enable content marketplace and advanced features.

#### 4.1 Marketplace Service

```typescript
// products/tutorputor/services/tutorputor-marketplace/src/service.ts
export function createMarketplaceService(
  prisma: TutorPrismaClient
): MarketplaceService {
  // Implement listing, pricing, purchase flow
}
```

#### 4.2 Advanced Features

| Feature                   | Priority | Sprint |
| ------------------------- | -------- | ------ |
| Learning Paths            | Medium   | 9      |
| Gamification (Badges)     | Low      | 10     |
| Certificates              | Low      | 10     |
| Offline Support           | Low      | 11     |
| Mobile App (React Native) | Medium   | 11-12  |

---

## Part 7: Component Mapping to @ghatana/ui

### 7.1 Existing Components to Reuse

| TutorPutor Need  | @ghatana/ui Component            | Usage Location             |
| ---------------- | -------------------------------- | -------------------------- |
| Navigation bar   | `AppBar`                         | AppLayout                  |
| Module cards     | `Card`                           | DashboardPage, ModulePage  |
| Progress bars    | `Progress`                       | EnrollmentCard, ModulePage |
| Domain badges    | `Badge`, `Chip`                  | ModuleCard                 |
| Loading states   | `Spinner`, `Skeleton`            | All pages                  |
| Forms            | `Form`, `FormField`, `TextField` | Assessment, Profile        |
| Dialogs          | `Modal`, `ConfirmDialog`         | Enrollment, Submit         |
| Lists            | `InteractiveList`, `TreeView`    | Objectives, Content        |
| Tabs             | `Tabs`                           | ModulePage sections        |
| Tooltips         | `Tooltip`                        | Help text                  |
| Data grid        | `DataGrid`                       | CMS, Analytics             |
| Dashboard layout | `DashboardLayout`                | Analytics                  |
| Stepper          | `Stepper`                        | Assessment flow            |
| Timeline         | `Timeline`                       | Learning history           |
| Breadcrumb       | `Breadcrumb`                     | Module > Lesson            |

### 7.2 New Components Needed (Product-Specific)

| Component               | Purpose                     | Location        |
| ----------------------- | --------------------------- | --------------- |
| `TutorChatMessage`      | AI chat bubble              | AI Tutor page   |
| `QuestionRenderer`      | Assessment question display | Assessment page |
| `VideoPlayer`           | Video content with tracking | Lesson page     |
| `CodeEditor`            | Code exercise input         | Lesson page     |
| `LearningObjectiveList` | Objectives with checkmarks  | Module page     |
| `ModulePrerequisites`   | Prerequisite graph          | Module page     |

---

## Part 8: API Routes Enhancement

### 8.1 Current API Routes

| Method | Route                                     | Handler          |
| ------ | ----------------------------------------- | ---------------- |
| GET    | `/api/v1/learning/dashboard`              | `learning.ts`    |
| POST   | `/api/v1/enrollments`                     | `learning.ts`    |
| PATCH  | `/api/v1/enrollments/:id/progress`        | `learning.ts`    |
| GET    | `/api/v1/modules`                         | `modules.ts`     |
| GET    | `/api/v1/modules/:slug`                   | `modules.ts`     |
| GET    | `/api/v1/assessments`                     | `assessments.ts` |
| GET    | `/api/v1/assessments/:id`                 | `assessments.ts` |
| POST   | `/api/v1/assessments/generate`            | `assessments.ts` |
| POST   | `/api/v1/assessments/:id/attempts`        | `assessments.ts` |
| POST   | `/api/v1/assessments/attempts/:id/submit` | `assessments.ts` |
| POST   | `/api/v1/ai/tutor/query`                  | `ai.ts`          |
| POST   | `/api/v1/analytics/events`                | `analytics.ts`   |
| GET    | `/api/v1/analytics/summary`               | `analytics.ts`   |

### 8.2 Missing API Routes

| Method | Route                                         | Purpose              | Priority |
| ------ | --------------------------------------------- | -------------------- | -------- |
| GET    | `/api/v1/users/me`                            | Current user profile | Critical |
| PATCH  | `/api/v1/users/me`                            | Update profile       | Medium   |
| GET    | `/api/v1/modules/:slug/lessons`               | List lessons         | High     |
| GET    | `/api/v1/modules/:slug/lessons/:id`           | Lesson detail        | High     |
| POST   | `/api/v1/ai/tutor/conversations`              | Start chat           | High     |
| GET    | `/api/v1/ai/tutor/conversations/:id`          | Chat history         | High     |
| POST   | `/api/v1/ai/tutor/conversations/:id/messages` | Send message         | High     |
| GET    | `/api/v1/cms/modules`                         | CMS module list      | Medium   |
| POST   | `/api/v1/cms/modules`                         | Create module        | Medium   |
| PUT    | `/api/v1/cms/modules/:id`                     | Update module        | Medium   |
| POST   | `/api/v1/cms/modules/:id/publish`             | Publish module       | Medium   |
| GET    | `/api/v1/marketplace/listings`                | Browse listings      | Low      |
| POST   | `/api/v1/marketplace/listings`                | Create listing       | Low      |
| GET    | `/api/v1/learning-paths`                      | Learning paths       | Medium   |

---

## Part 9: Testing Strategy

### 9.1 Current Test Coverage

| Area               | Files                    | Coverage   |
| ------------------ | ------------------------ | ---------- |
| API Gateway        | `server.test.ts`         | Basic      |
| Dashboard Page     | `DashboardPage.test.tsx` | Basic      |
| Assessment Service | `service.test.ts`        | Moderate   |
| Analytics Service  | `service.test.ts`        | Basic      |
| Contracts          | `contracts.type.test.ts` | Types only |

### 9.2 Required Test Additions

| Test Type   | Target         | Using                          |
| ----------- | -------------- | ------------------------------ |
| Unit        | All services   | `@ghatana/test-utils`, Vitest  |
| Integration | API routes     | Fastify inject                 |
| Component   | All pages      | React Testing Library          |
| E2E         | Critical flows | Playwright                     |
| A11y        | All pages      | `@ghatana/accessibility-audit` |

---

## Part 10: Implementation Priority Matrix

### Critical (Sprint 1-2)

1. ✅ Migrate to `@ghatana/api` client
2. ✅ Implement authentication integration
3. ✅ Add User/Tenant models
4. ✅ Complete ContentService implementation

### High (Sprint 3-5)

1. Assessment UI pages
2. AI Tutor chat interface
3. Lesson-level navigation
4. Analytics dashboard with `@ghatana/charts`
5. Real-time features with `@ghatana/realtime`

### Medium (Sprint 6-8)

1. CMS service & UI
2. Learning paths feature
3. Content block renderers (video, code, etc.)
4. Mobile-responsive improvements

### Low (Sprint 9-12)

1. Marketplace implementation
2. Gamification (badges, certificates)
3. Offline support
4. React Native mobile app

---

## Part 11: Technical Debt & Cleanup

### 11.1 Identified Debt

| Issue                | Location               | Fix                                    |
| -------------------- | ---------------------- | -------------------------------------- |
| Stub tenant/user IDs | `tutorputorClient.ts`  | Integrate auth                         |
| Custom axios client  | `tutorputorClient.ts`  | Use `@ghatana/api`                     |
| Inline Tailwind      | All components         | Use design system patterns             |
| SQLite in production | `prisma/schema.prisma` | Migrate to PostgreSQL                  |
| No error boundaries  | `App.tsx`              | Add `ErrorBoundary` from `@ghatana/ui` |
| No loading skeletons | All pages              | Add `Skeleton` components              |
| Hardcoded strings    | All files              | Add i18n support                       |

### 11.2 Code Quality Improvements

1. Add ESLint strict mode
2. Enable TypeScript strict null checks
3. Add Prettier formatting
4. Implement conventional commits
5. Add pre-commit hooks

---

## Part 12: Documentation Requirements

### 12.1 Missing Documentation

| Document                      | Purpose          | Priority |
| ----------------------------- | ---------------- | -------- |
| API Reference                 | OpenAPI spec     | High     |
| User Manual                   | End-user guide   | Medium   |
| Developer Guide               | Onboarding       | High     |
| Architecture Decision Records | Design decisions | Medium   |
| Component Storybook           | UI documentation | Medium   |

### 12.2 Documentation Updates Needed

- Update `DESIGN_ARCHITECTURE.md` with actual implementation details
- Add service-level architecture diagrams
- Document data flow for key features
- Add deployment and operations guides

---

## Conclusion

TutorPutor has a solid foundation with well-defined contracts and a clean separation of concerns. The main gaps are:

1. **Reuse violations** - Not leveraging `@ghatana/api`, `@ghatana/realtime`, `@ghatana/charts`
2. **Missing UI** - Assessment, AI tutor, analytics pages not implemented
3. **Skeleton services** - CMS, Content, Marketplace need implementation
4. **Authentication** - Currently stubbed, needs platform integration
5. **Real-time features** - No WebSocket support for live tutoring

Following this enhancement plan with the priority matrix will bring TutorPutor to production readiness while maintaining strict adherence to the reuse-first policy and architectural guidelines.

---

## Appendix: Quick Reference

### Key Files

- Contracts: `contracts/v1/types.ts`, `contracts/v1/services.ts`
- Database: `services/tutorputor-db/prisma/schema.prisma`
- Frontend Entry: `apps/tutorputor-web/src/main.tsx`
- API Gateway: `apps/api-gateway/src/server.ts`

### Shared Libraries to Use

- `@ghatana/ui` - UI components
- `@ghatana/api` - HTTP client
- `@ghatana/charts` - Data visualization
- `@ghatana/realtime` - WebSocket/SSE
- `@ghatana/design-system` - Unified design
- `@ghatana/state` - State management
- `@ghatana/test-utils` - Testing utilities

### Commands

```bash
# Build
pnpm --filter @tutorputor/web build

# Test
pnpm --filter @tutorputor/... test

# Dev
pnpm --filter @tutorputor/web dev
pnpm --filter @tutorputor/api-gateway dev

# Database
pnpm --filter @tutorputor/db prisma:generate
pnpm --filter @tutorputor/db prisma:migrate
```
