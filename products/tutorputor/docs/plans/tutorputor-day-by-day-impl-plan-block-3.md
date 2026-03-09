# TutorPutor – Day-by-Day Implementation Plan (Block 3: Days 21–30)
## Focus: Pathways Engine, Teacher Dashboards, Collaboration, VR/AR Sims, Marketplace Payments, LTI Integration

This phase builds on Block 1 (Vertical Slice MVP) and Block 2 (Assessments, CMS, Analytics).  
Block 3 focuses on advanced platform capabilities required for enterprise-scale learning systems.

---

# DAY 21 — Pathways Engine Contracts + Service Skeleton

**Goal:** Introduce a Learning Pathways engine that connects modules into personalized sequences.  

### Tasks
- Add contracts to `contracts/v1`:
  - `LearningPath`, `LearningPathNode`, `LearningPathRecommendation`
- Add `PathwaysService` interface:
  ```ts
  generatePathway(goal, constraints)
  getPathwayForUser(userId)
  advancePathway(userId, completedModuleId)
  ```
- Create service package `services/tutorputor-pathways`
- Expose `/api/v1/pathways/*` routes with stub data

### Acceptance Criteria
- Pathways routes respond with stubbed structured objects.
- Contracts stable and referenced by gateway + frontend.

---

# DAY 22 — Pathways: AI-Based Module Recommendation

**Goal:** Implement hybrid recommendation using AI + prerequisites + difficulty gradients.

### Tasks
- Integrate `ai-integration` in PathwaysService:
  - Use LLM to propose ordered module sets
  - Rank/prioritize modules using internal scoring
- Build deterministic test cases
- Add analytics signals: user mastery, past performance, learning time

### Acceptance Criteria
- `POST /pathways/generate` returns a coherent AI-suggested learning path.

---

# DAY 23 — Teacher Dashboard: Overview APIs

**Goal:** Build foundation for teacher experience.

### Tasks
- Add contracts:
  - `Classroom`, `TeacherDashboardSummary`, `RosterEntry`
- Add `TeacherService` interface:
  - `getTeacherDashboard(teacherId)`
  - `createClassroom`
  - `addStudentToClassroom`
- Stub implementations in `services/tutorputor-teacher`
- Add routes under `/api/v1/teacher/*`

### Acceptance Criteria
- Teacher dashboard endpoints return valid stubbed data.

---

# DAY 24 — Teacher Assignments + Classroom Progress

**Goal:** Give teachers tools to assign modules and view progress.

### Tasks
- Add APIs:
  - `assignModule(classroomId, moduleId)`
  - `getClassroomProgress(classroomId)`
- Plug into analytics service for:
  - mastery trends
  - struggling students
  - assignment performance
- FE: Render basic teacher dashboard view

### Acceptance Criteria
- Class-level progress appears in dashboard from stub/analytics.

---

# DAY 25 — Collaboration System: Q&A, Discussions, Help-Requests

**Goal:** Add community learning features.

### Tasks
- Add contracts:
  - `Thread`, `Post`, `HelpRequest`
- Add `CollaborationService`:
  - `postQuestion(moduleId, content)`
  - `reply(threadId, content)`
  - `listThreads(moduleId)`
- Add access rules (teacher moderation)

### Acceptance Criteria
- Basic discussion lifecycle works end-to-end with stub service.

---

# DAY 26 — VR/AR Simulation Integration

**Goal:** Add VR/AR modules and rendering placeholders.

### Tasks
- Add block type: `vr_simulation`
- Extend CMS to create VR blocks:
  - fields: `bundleUrl`, `requirements`, `metadata`
- Extend FE:
  - Render VR simulation placeholder iframe/div
- Add simulation launch token endpoint:
  `/api/v1/simulations/launch`

### Acceptance Criteria
- CMS and FE support VR blocks.
- Launch endpoint returns token.

---

# DAY 27 — Marketplace Payments Integration (Mocked)

**Goal:** Introduce payment flows for purchasing third‑party modules.

### Tasks
- Add `BillingService` contracts:
  - `createCheckoutSession(listingId)`
  - `verifyPayment(sessionId)`
  - `listPurchases(userId)`
- Create `services/tutorputor-billing`
- Mock PSP integration using Kill Bill API stubs

### Acceptance Criteria
- Purchasing from marketplace returns success/receipt.
- No real payment processing yet.

---

# DAY 28 — LTI Integration (Canvas, Blackboard, Google Classroom)

**Goal:** Allow LTI-based institutional integration.

### Tasks
- Add `LTIService`:
  - `validateLaunch`
  - `getDeepLinkingContent`
- Implement:
  - LTI 1.3 launch flow
  - JWK/JWT validation
- FE: LTI launch page handling

### Acceptance Criteria
- Validate mocked Canvas/Blackboard LTI launch.
- Return LTI deep-linking responses.

---

# DAY 29 — Advanced Analytics (Predictive Modeling + Dashboards)

**Goal:** Provide teachers/admins deeper insights.

### Tasks
- Add analytics aggregation for:
  - daily/weekly usage
  - module difficulty heatmap
  - risk indicators (“at risk of falling behind”)
- Add ML placeholder:
  - rule-based early-warning model
- FE: add charts to teacher dashboard

### Acceptance Criteria
- Analytics endpoints return chart-ready data.
- Pathways engine uses analytics signals.

---

# DAY 30 — System Hardening + Full E2E Scenario Tests (Block 3)

**Goal:** Validate the full expanded system.

### Tasks
- Add Playwright tests for scenarios:
  - student → pathway → module → assessment → tutor
  - teacher → class → assignment → progress review
  - author → CMS VR module → publish
  - student → purchase marketplace item (mock)
  - LTI user → deep link → module
- Add rate limiting + tracing
- Validate multi-tenant boundaries

### Acceptance Criteria
- All advanced flows pass.
- TutorPutor is enterprise-ready for:
  - student mode  
  - teacher mode  
  - author mode  
  - marketplace mode  
  - LTI mode  

---

# End of Block 3 Plan
Block 4 Preview: Offline mode → Native mobile apps → SSO → Institutional Admin → GDPR/compliance tooling.
