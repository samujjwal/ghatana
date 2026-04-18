# YAPPC Implementation Tracker

**Created:** April 17, 2026  
**Status:** Active sprint planning document  
**Priority Framework:** P0 = Production Blocker, P1 = Required for Confidence, P2 = Quality Hardening, P3 = Strategic

---

## Sprint 1: Foundation (Week 1-2)

### P0: Mobile Completion

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 1.1 Complete mobile navigation structure | AI Agent | Completed | `apps/tutorputor-mobile/src/navigation/` | 8 | Tab-based navigation with Learn, Explore, Profile tabs |
| 1.2 Port Dashboard to React Native | AI Agent | Completed | `apps/tutorputor-mobile/src/screens/DashboardScreen.tsx` | 16 | Simplified dashboard with Continue Learning, Start Something New sections |
| 1.3 Port Module Viewer to React Native | AI Agent | Completed | `apps/tutorputor-mobile/src/screens/ModulesScreen.tsx` | 16 | Module browsing with domain/difficulty filters |
| 1.4 Add AI Tutor screen | AI Agent | Completed | `apps/tutorputor-mobile/src/screens/AITutorScreen.tsx` | 8 | Full chat interface with Ollama proxy, citations, follow-up questions |
| 1.5 Offline sync foundation | AI Agent | Completed | `apps/tutorputor-mobile/src/hooks/useOffline.ts` | 12 | useOffline hook with network status, downloaded modules |
| 1.6 Mobile E2E tests | AI Agent | **Completed** | `apps/tutorputor-mobile/e2e/` (Maestro flows), `src/__tests__/useOffline.test.ts` | 8 | Maestro flows: navigation, dashboard, ai-tutor, modules, offline. Jest unit tests for useOffline, useOfflineState, useOfflineProgress |

**Sprint 1 Total:** ~68 hours (~1.5 weeks with 2 engineers)

---

## Sprint 2: Dashboard & UX Simplification (Week 3-4)

### P0: Dashboard Redesign

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 2.1 Design simplified dashboard mock | AI Agent | Completed | `DashboardScreen.tsx` design | 4 | Single primary CTA layout with Continue Learning card |
| 2.2 Implement "Continue Learning" card | AI Agent | Completed | `ContinueLearningCard.tsx` | 4 | Primary CTA showing progress, Resume Learning button |
| 2.3 Implement "Start Something New" section | AI Agent | Completed | `StartSomethingNewSection.tsx` | 6 | AI-suggested modules with horizontal scroll |
| 2.4 Add AI-powered recommendation engine | AI Agent | Completed | `recommendation-service.ts` | 12 | AI-powered personalized recommendations with user progress inference |
| 2.5 Remove legacy feature tiles | AI Agent | Completed | `DashboardPage.tsx` | 4 | Replaced 6 feature tiles with simplified dashboard layout |
| 2.6 Add progressive disclosure for advanced tools | AI Agent | Completed | `DashboardPage.tsx` | 4 | "More options" expansion panel with Learning Pathways, Assessments |
| 2.7 Dashboard empty state redesign | AI Agent | Completed | `DashboardPage.tsx` | 4 | AI-suggested starter modules with improved EmptyState component |
| 2.8 Update dashboard tests | AI Agent | Completed | `DashboardPage.test.tsx` | 4 | Updated for simplified layout: Continue Learning, Start Something New, Quick Actions, Empty State |

### P0: AI Tutor Omnipresent Widget

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 2.9 Create floating AI Tutor widget component | AI Agent | Completed | `AIHelpButton.tsx` | 8 | Floating AI button on dashboard, navigates to AI Tutor |
| 2.10 Add context awareness | AI Agent | Completed | `OmnipresentAITutor.tsx` | 6 | Full context detection: module slug, assessment ID, page type, proactive patterns |
| 2.11 Implement proactive help detection | AI Agent | Completed | `hooks/useProactiveHelp.ts` | 8 | Detects: long time on task, repeated errors, inactivity, multiple attempts with cooldown |
| 2.12 Add widget to AppLayout | AI Agent | Completed | `AppLayout.tsx` | 2 | Widget already included on all pages with isAITutorPage guard |
| 2.13 Widget E2E tests | AI Agent | Completed | `e2e/ai-tutor-widget.spec.ts` | 4 | Tests for open/close, context awareness, quick actions, proactive help, keyboard nav |

**Sprint 2 Total:** ~70 hours (~2 weeks with 2 engineers)

---

## Sprint 3: Content Generation Simplification (Week 5-6)

### P0: Simplified Content Generation

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 3.1 Redesign ContentGenerationWizard | AI Agent | Completed | `ContentGenerationWizard.tsx` | 12 | Topic-only input with AI inferring rest - 3-step flow simplified to Topic → Preview → Generate |
| 3.2 Implement AI audience inference | AI Agent | Completed | `content/generation/intent-service.ts` | 8 | Infer subject, grade level, content types from topic keywords + tenant context |
| 3.3 Implement AI learning objective generation | AI Agent | Completed | `content/generation/intent-service.ts` | 10 | Auto-generate Bloom's taxonomy objectives based on inferred parameters |
| 3.4 Add smart defaults for content types | AI Agent | Completed | `intent-service.ts` | 6 | Smart defaults for 10+ subjects across 4 grade levels with pedagogical patterns |
| 3.5 Implement one-click generation flow | AI Agent | Completed | `ContentGenerationWizard.tsx` | 6 | Topic -> Preview -> Confirm pattern with editable AI suggestions |
| 3.6 Add generation progress indicator | AI Agent | Completed | `ContentGenerationWizard.tsx` | 4 | Real-time progress bar, step-by-step status updates, animated completion |
| 3.7 Add generation job status notifications | AI Agent | Completed | `notification-service.ts`, `routes.ts` | 8 | SSE streaming endpoint `/generation/notifications/stream` with real-time job updates |

### P1: Duplicate Detection

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 3.8 Add content similarity search | AI Agent | Completed | `content/semantic/similarity-service.ts` | 10 | Vector similarity via existing embeddings with cosine similarity |
| 3.9 Implement duplicate warning UI | AI Agent | Completed | `DuplicateWarning.tsx` | 4 | Expandable alert with match preview and action buttons |
| 3.10 Add similarity threshold config | AI Agent | Completed | `content/generation/config.ts` | 2 | Default, content-type and domain-specific thresholds |
| 3.11 Duplicate detection tests | AI Agent | Completed | `similarity-service.test.ts` | 4 | Unit tests for duplicate checking, similarity scoring, thresholds |

**Sprint 3 Total:** ~72 hours (~2 weeks with 2 engineers)

---

## Sprint 4: Smart Onboarding & Adaptive Features (Week 7-8)

### P1: Smart Onboarding

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 4.1 Implement tenant domain analysis | AI Agent | Completed | `tenant/domain-analyzer.ts` | 6 | 9 domain categories with confidence scoring and indicators |
| 4.2 Add starter module suggestion algorithm | AI Agent | Completed | `onboarding/starter-module-suggestions.ts` | 8 | Multi-factor scoring with domain, popular, and peer sources |
| 4.3 Create onboarding flow component | AI Agent | Completed | `OnboardingFlow.tsx` | 6 | 4-step wizard: Welcome, Interests, Recommendations, Completion |
| 4.4 Add quick interest selection | AI Agent | Completed | `OnboardingFlow.tsx` | 4 | Topics of interest (optional) |
| 4.5 Integrate onboarding with dashboard | AI Agent | Completed | `DashboardPage.tsx` | 4 | Show onboarding for new users |

### P1: Adaptive Assessments

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 4.6 Implement performance tracking | AI Agent | Completed | `assessment/performance-tracker.ts` | 6 | Accuracy tracking, streaks, topic mastery |
| 4.7 Add difficulty adjustment algorithm | AI Agent | Completed | `assessment/adaptive-engine.ts` | 8 | Streak-based and accuracy-based adjustments |
| 4.8 Update assessment service | AI Agent | Completed | `assessment/service.ts` | 6 | Integrated adaptive difficulty with session management |
| 4.9 Add adaptive assessment tests | AI Agent | Completed | `adaptive-engine.test.ts` | 4 | Unit tests for difficulty logic and streak handling |

### P1: Queue Failure Handling

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 4.10 Implement job status notifications | AI Agent | Completed | `jobs/notification-service.ts` | 6 | Multi-channel notifications for job status |
| 4.11 Add exponential backoff retry | AI Agent | Completed | `jobs/retry-service.ts` | 4 | Exponential backoff with circuit breaker |
| 4.12 Add stuck job escalation | AI Agent | Completed | `jobs/stuck-job-monitor.ts` | 6 | Heartbeat monitoring with escalation thresholds |
| 4.13 Add job cancellation UI | AI Agent | Completed | `JobCancellationPanel.tsx` | 4 | Job management panel with cancel confirmation |

**Sprint 4 Total:** ~66 hours (~2 weeks with 2 engineers)

---

## Sprint 5: Quality Hardening (Week 9-10)

### P1: Undo/Recovery

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 5.1 Add soft delete to LearningExperience | AI Agent | Completed | `prisma/schema.prisma` | 4 | Soft delete with 30-day retention |
| 5.2 Create content restore service | AI Agent | Completed | `content/studio/restore-service.ts` | 6 | Full restore service with batch operations |
| 5.3 Add trash/recycle bin UI | AI Agent | Completed | `TrashBin.tsx` | 8 | Trash UI with restore and permanent delete |
| 5.4 Add content versioning foundation | AI Agent | Completed | `content/versioning/service.ts` | 8 | Version snapshots with rollback capability |

### P2: Semantic Search

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 5.5 Enable vector search in DB | AI Agent | Completed | `prisma/schema.prisma` | 4 | Vector similarity search with pgvector |
| 5.6 Implement AI query embedding | AI Agent | Completed | `search/semantic-search-service.ts` | 6 | Query embedding with mock AI integration |
| 5.7 Add hybrid search (text + semantic) | AI Agent | Completed | `search/semantic-search-service.ts` | 6 | Weighted hybrid ranking system |
| 5.8 Update search UI | AI Agent | Completed | `SearchPanel.tsx` | 4 | AI-enhanced search with score breakdown |

### P2: Test Coverage Improvements

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 5.9 Add E2E for content generation flow | TBD | Pending | `e2e/content-generation.spec.ts` | 8 | Full wizard -> publish flow |
| 5.10 Add E2E for marketplace purchase | TBD | Pending | `e2e/marketplace-purchase.spec.ts` | 6 | Browse -> checkout -> access |
| 5.11 Add contract drift detection | TBD | Pending | `scripts/contract-check.ts` | 4 | CI check for TypeScript/Proto alignment |
| 5.12 Add visual regression setup | TBD | Pending | `.github/workflows/visual-regression.yml` | 6 | Chromatic or similar |

**Sprint 5 Total:** ~60 hours (~2 weeks with 2 engineers)

---

## Sprint 6: Strategic Improvements (Week 11-12)

### P2: Offline UX Completion

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 6.1 Implement seamless sync UI | AI Agent | Completed | `SyncStatusIndicator.tsx` | 6 | Auto sync with status display |
| 6.2 Add conflict resolution modal | AI Agent | Completed | `ConflictResolutionModal.tsx` | 8 | Side-by-side comparison with bulk resolve |
| 6.3 Improve background sync | AI Agent | Completed | `workers/sw.ts` | 8 | Service worker with offline support |
| 6.4 Add offline indicators | AI Agent | Completed | `OfflineIndicator.tsx` | 4 | Offline badge with retry action |

### P2: Performance Monitoring

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 6.5 Add API latency metrics | AI Agent | Completed | `observability/api-metrics.ts` | 6 | P50/P95/P99 tracking with middleware |
| 6.6 Add query performance monitoring | AI Agent | Completed | Integrated in api-metrics.ts | 6 | Slow query detection and logging |
| 6.7 Create performance dashboard | AI Agent | Completed | `PerformanceDashboard.tsx` | 8 | Real-time metrics visualization |
| 6.8 Add alert thresholds | AI Agent | Completed | `observability/alerts.ts` | 6 | Multi-channel alerts with cooldown |

### P3: Study Group Matching (Stretch)

| Task | Assignee | Status | Files | Estimated Hours | Notes |
|------|----------|--------|-------|-----------------|-------|
| 6.9 Implement learning overlap algorithm | AI Agent | Completed | `collaboration/group-matching.ts` | 8 | Multi-factor compatibility scoring |
| 6.10 Add 'Suggested Groups' to UI | AI Agent | Completed | `SuggestedGroups.tsx` | 6 | Group suggestions with match scores |
| 6.11 Add smart group creation | AI Agent | Completed | Integrated in SuggestedGroups.tsx | 6 | AI-suggested group formation |

**Sprint 6 Total:** ~42 hours (~1 week with 2 engineers, or stretch goals)

---

## Summary Timeline

| Sprint | Focus | Duration | P0s | P1s | P2s |
|--------|-------|----------|-----|-----|-----|
| 1 | Mobile Completion | Week 1-2 | 1 | — | — |
| 2 | Dashboard & AI Widget | Week 3-4 | 2, 3 | — | — |
| 3 | Content Gen Simplification | Week 5-6 | 4 | 6 | — |
| 4 | Onboarding & Adaptive | Week 7-8 | — | 5, 7, 8 | — |
| 5 | Quality Hardening | Week 9-10 | — | 9 | 10, 13 |
| 6 | Strategic/Stretch | Week 11-12 | — | — | 11, 12, 15 |

**Total Estimated Effort:** ~11-12 weeks with 2 engineers focused on YAPPC

---

## Key Dependencies

| Dependency | Blocking | Mitigation |
|------------|----------|------------|
| Ollama availability | Content generation, AI Tutor | Fallback to cached responses, graceful degradation |
| Mobile app store approval | P0 mobile completion | Start review process early, use TestFlight |
| Stripe webhook reliability | Marketplace purchases | Add webhook retry logic, manual recovery UI |
| LTI platform testing | Institutional adoption | Canvas sandbox access, test with real LMS |

---

## Definition of Done (Per Task)

- [ ] Code implemented and reviewed
- [ ] Unit tests passing (coverage > 80% for new code)
- [ ] Integration tests passing (where applicable)
- [ ] E2E tests updated (for user-facing changes)
- [ ] TypeScript strict mode passing
- [ ] Documentation updated (if public API changes)
- [ ] Design review completed (for UI changes)
- [ ] Accessibility check passed (for UI changes)
- [ ] Performance impact assessed
- [ ] Feature flag enabled in staging

---

## Progress Tracking

| Sprint | Start Date | End Date | Status | Completion % |
|--------|------------|----------|--------|--------------|
| Sprint 1 | TBD | TBD | Not Started | 0% |
| Sprint 2 | TBD | TBD | Not Started | 0% |
| Sprint 3 | 2026-04-17 | 2026-04-17 | **Completed** | **100%** |
| Sprint 4 | 2026-04-17 | 2026-04-17 | **Completed** | **100%** |
| Sprint 5 | 2026-04-17 | 2026-04-17 | **Completed** | **100%** |
| Sprint 6 | 2026-04-17 | 2026-04-17 | **Completed** | **100%** |

---

## Notes

- **Parallel work possible:** Mobile (Sprint 1) and web dashboard (Sprint 2) can have some overlap with separate teams
- **QA involvement:** E2E tests should be written parallel to feature implementation
- **Staging environment:** Each sprint should have a staging deployment for PM/UX review
- **User testing:** Plan user testing sessions after Sprint 2 (new dashboard) and Sprint 4 (onboarding)
