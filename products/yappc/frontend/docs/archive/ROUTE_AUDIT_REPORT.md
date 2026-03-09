# YAPPC Route Audit Report
**Date:** 2026-02-03  
**Status:** CRITICAL - Routes ↔ Pages Mismatch Identified  
**Priority:** P0 - Production Blocker

---

## Executive Summary

Comprehensive audit of `apps/web/src/router/routes.tsx` against actual page files reveals **multiple critical mismatches** that will cause runtime navigation failures.

**Findings:**
- ✅ **Auth Pages:** All 5 routes correctly mapped
- ✅ **Admin Pages:** All 6 routes correctly mapped
- ⚠️ **Bootstrapping:** 2 stub pages, 2 missing pages
- ⚠️ **Initialization:** 4 stub pages exist
- ⚠️ **Development:** 3 stub pages exist
- ⚠️ **Operations:** 8 stub pages exist
- ⚠️ **Collaboration:** 3 stub pages exist
- ⚠️ **Security:** 6 stub pages exist

---

## Detailed Analysis by Phase

### ✅ Auth Pages (5/5 Complete)
All auth routes correctly map to existing pages:
- LoginPage ✅
- RegisterPage ✅
- ForgotPasswordPage ✅
- ResetPasswordPage ✅
- SSOCallbackPage ✅

### ✅ Admin Pages (6/6 Complete)
All admin routes correctly map to existing pages:
- AdminDashboardPage ✅
- UsersPage ✅
- TeamsPage ✅
- BillingPage ✅
- AuditPage ✅
- IntegrationsPage ✅

---

### ⚠️ Bootstrapping Phase (10/12 Complete)

**Existing Pages (12 files):**
1. BootstrapCollaboratePage.tsx (26KB) - NOT ROUTED
2. BootstrapCompletePage.tsx (15KB) ✅ ROUTED
3. BootstrapExportPage.tsx (24KB) ✅ ROUTED
4. BootstrapReviewPage.tsx (27KB) - NOT ROUTED
5. BootstrapSessionPage.tsx (14KB) ✅ ROUTED
6. ImportFromURLPage.tsx (23KB) ✅ ROUTED
7. ProjectPreviewPage.tsx (219 bytes) ✅ ROUTED (STUB)
8. ResumeSessionPage.tsx (17KB) - NOT ROUTED
9. StartProjectPage.tsx (15KB) ✅ ROUTED
10. TemplateGalleryPage.tsx (222 bytes) ✅ ROUTED (STUB)
11. TemplateSelectionPage.tsx (20KB) - NOT ROUTED (router imports as TemplateGalleryPage)
12. UploadDocsPage.tsx (19KB) ✅ ROUTED

**Issues:**
1. **Router imports TemplateSelectionPage as TemplateGalleryPage** (line 58-60)
   - Both files exist but router uses wrong name
   - TemplateGalleryPage is 222 bytes (stub)
   - TemplateSelectionPage is 20KB (real implementation)
   
2. **Missing Routes:**
   - BootstrapCollaboratePage (26KB) - Collaboration step
   - BootstrapReviewPage (27KB) - Review step
   - ResumeSessionPage (17KB) - Resume functionality

**Recommendation:**
- Use TemplateSelectionPage (real implementation) instead of TemplateGalleryPage
- Add routes for Collaborate, Review, Resume pages

---

### ⚠️ Initialization Phase (7/11 Complete)

**Existing Pages (11 files):**
1. EnvironmentSetupPage.tsx (225 bytes) ✅ ROUTED (STUB)
2. InfrastructureConfigPage.tsx (237 bytes) ✅ ROUTED (STUB)
3. InitializationCompletePage.tsx (25KB) - NOT ROUTED
4. InitializationPresetsPage.tsx (19KB) ✅ ROUTED
5. InitializationProgressPage.tsx (17KB) - NOT ROUTED
6. InitializationRollbackPage.tsx (21KB) ✅ ROUTED
7. InitializationWizardPage.tsx (31KB) ✅ ROUTED (as SetupWizardPage)
8. SetupProgressPage.tsx (216 bytes) ✅ ROUTED (STUB)
9. SetupWizardPage.tsx (210 bytes) - STUB (router imports InitializationWizardPage)
10. TeamInvitePage.tsx (207 bytes) ✅ ROUTED (STUB)

**Issues:**
1. **Router imports InitializationWizardPage as SetupWizardPage** (line 78-80)
   - SetupWizardPage is 210 bytes (stub)
   - InitializationWizardPage is 31KB (real implementation)

2. **Missing Routes:**
   - InitializationCompletePage (25KB) - Completion screen
   - InitializationProgressPage (17KB) - Progress tracking

**Recommendation:**
- Router already correctly imports InitializationWizardPage
- Add routes for Complete and Progress pages
- Stubs exist for Infrastructure, Environment, Team, SetupProgress (can be enhanced or kept as wizard steps)

---

### ⚠️ Development Phase (16/19 Complete)

**Existing Pages (19 files):**
1. BacklogPage.tsx (34KB) ✅ ROUTED
2. CodeReviewDashboardPage.tsx (21KB) ✅ ROUTED (as CodeReviewPage)
3. CodeReviewDetailPage.tsx (58KB) ✅ ROUTED
4. CodeReviewPage.tsx (207 bytes) - STUB
5. DeploymentDetailPage.tsx (32KB) - NOT ROUTED
6. DeploymentsPage.tsx (25KB) ✅ ROUTED
7. DevDashboardPage.tsx (15KB) ✅ ROUTED
8. EpicsPage.tsx (192 bytes) ✅ ROUTED (STUB)
9. FeatureFlagsPage.tsx (19KB) ✅ ROUTED
10. PullRequestDetailPage.tsx (228 bytes) ✅ ROUTED (STUB)
11. PullRequestsPage.tsx (213 bytes) ✅ ROUTED (STUB)
12. SprintBoardPage.tsx (17KB) ✅ ROUTED
13. SprintListPage.tsx (28KB) - NOT ROUTED
14. SprintPlanningPage.tsx (33KB) ✅ ROUTED
15. SprintRetroPage.tsx (27KB) ✅ ROUTED
16. StoryDetailPage.tsx (46KB) ✅ ROUTED
17. VelocityChartsPage.tsx (24KB) ✅ ROUTED (as VelocityPage)
18. VelocityPage.tsx (201 bytes) - STUB

**Issues:**
1. **Router imports CodeReviewDashboardPage as CodeReviewPage** (line 127-129)
   - CodeReviewPage is 207 bytes (stub)
   - CodeReviewDashboardPage is 21KB (real implementation)

2. **Router imports VelocityChartsPage as VelocityPage** (line 124-126)
   - VelocityPage is 201 bytes (stub)
   - VelocityChartsPage is 24KB (real implementation)

3. **Missing Routes:**
   - DeploymentDetailPage (32KB) - Individual deployment view
   - SprintListPage (28KB) - Sprint list view

**Recommendation:**
- Router already correctly imports real implementations
- Add routes for DeploymentDetail and SprintList
- Stubs exist for Epics, PRs (can be enhanced)

---

### ⚠️ Operations Phase (11/19 Complete)

**Existing Pages (19 files):**
1. AlertsPage.tsx (26KB) ✅ ROUTED
2. DashboardEditorPage.tsx (222 bytes) ✅ ROUTED (STUB)
3. DashboardsPage.tsx (207 bytes) ✅ ROUTED (STUB)
4. IncidentDetailPage.tsx (28KB) ✅ ROUTED
5. IncidentListPage.tsx (28KB) - NOT ROUTED (router uses IncidentsPage)
6. IncidentsPage.tsx (204 bytes) ✅ ROUTED (STUB)
7. LogExplorerPage.tsx (21KB) ✅ ROUTED
8. MetricsPage.tsx (22KB) ✅ ROUTED
9. OnCallPage.tsx (195 bytes) ✅ ROUTED (STUB)
10. OperationsDashboardPage.tsx (21KB) - NOT ROUTED
11. OpsDashboardPage.tsx (16KB) ✅ ROUTED
12. PostmortemsPage.tsx (210 bytes) ✅ ROUTED (STUB)
13. RunbookDetailPage.tsx (216 bytes) ✅ ROUTED (STUB)
14. RunbooksPage.tsx (201 bytes) ✅ ROUTED (STUB)
15. ServiceLogsPage.tsx (20KB) ✅ ROUTED
16. ServiceMapPage.tsx (207 bytes) ✅ ROUTED (STUB)
17. TracesPage.tsx (28KB) - NOT ROUTED
18. WarRoomPage.tsx (198 bytes) ✅ ROUTED (STUB)

**Issues:**
1. **Router imports IncidentsPage (stub) instead of IncidentListPage (real)**
   - IncidentsPage is 204 bytes (stub)
   - IncidentListPage is 28KB (real implementation)

2. **Two dashboard implementations:**
   - OpsDashboardPage (16KB) - Currently routed
   - OperationsDashboardPage (21KB) - Not routed

3. **Missing Routes:**
   - TracesPage (28KB) - Distributed tracing view

**Recommendation:**
- Use IncidentListPage instead of IncidentsPage
- Decide between OpsDashboardPage vs OperationsDashboardPage (or route both)
- Add route for TracesPage
- Many stubs exist (Dashboards, DashboardEditor, OnCall, Postmortems, Runbooks, ServiceMap, WarRoom)

---

### ⚠️ Collaboration Phase (16/19 Complete)

**Existing Pages (19 files):**
1. ActivityFeedPage.tsx (213 bytes) ✅ ROUTED (STUB)
2. ArticleEditorPage.tsx (216 bytes) ✅ ROUTED (STUB)
3. ArticlePage.tsx (198 bytes) ✅ ROUTED (STUB)
4. CalendarPage.tsx (201 bytes) - STUB (router imports TeamCalendarPage)
5. ChannelPage.tsx (198 bytes) ✅ ROUTED (STUB)
6. DirectMessagePage.tsx (216 bytes) ✅ ROUTED (STUB)
7. GoalsPage.tsx (192 bytes) ✅ ROUTED (STUB)
8. IntegrationsPage.tsx (25KB) ✅ ROUTED (as TeamIntegrationsPage)
9. KnowledgeBasePage.tsx (19KB) ✅ ROUTED
10. MessagesPage.tsx (201 bytes) - STUB (router imports TeamChatPage)
11. NotificationsPage.tsx (24KB) ✅ ROUTED
12. RetrosPage.tsx (195 bytes) ✅ ROUTED (STUB)
13. StandupsPage.tsx (201 bytes) ✅ ROUTED (STUB)
14. TeamCalendarPage.tsx (23KB) ✅ ROUTED (as CalendarPage)
15. TeamChatPage.tsx (21KB) ✅ ROUTED (as MessagesPage)
16. TeamDashboardPage.tsx (23KB) ✅ ROUTED (as TeamHubPage)
17. TeamHubPage.tsx (198 bytes) - STUB
18. TeamSettingsPage.tsx (32KB) ✅ ROUTED

**Issues:**
1. **Router correctly imports real implementations:**
   - TeamDashboardPage as TeamHubPage ✅
   - TeamCalendarPage as CalendarPage ✅
   - TeamChatPage as MessagesPage ✅
   - IntegrationsPage as TeamIntegrationsPage ✅

2. **Many stub pages exist** (can be enhanced):
   - ActivityFeedPage, ArticleEditorPage, ArticlePage
   - ChannelPage, DirectMessagePage, GoalsPage
   - RetrosPage, StandupsPage

**Recommendation:**
- Router mappings are correct
- Enhance stub pages as needed
- All critical pages are routed

---

### ⚠️ Security Phase (9/15 Complete)

**Existing Pages (15 files):**
1. AccessControlPage.tsx (23KB) ✅ ROUTED
2. AuditLogsPage.tsx (24KB) ✅ ROUTED
3. ComplianceFrameworkPage.tsx (234 bytes) ✅ ROUTED (STUB)
4. CompliancePage.tsx (20KB) ✅ ROUTED
5. PoliciesPage.tsx (201 bytes) ✅ ROUTED (STUB)
6. PolicyDetailPage.tsx (213 bytes) ✅ ROUTED (STUB)
7. ScanResultsPage.tsx (210 bytes) ✅ ROUTED (STUB)
8. SecretsPage.tsx (198 bytes) ✅ ROUTED (STUB)
9. SecurityAlertsPage.tsx (219 bytes) ✅ ROUTED (STUB)
10. SecurityDashboardPage.tsx (19KB) ✅ ROUTED
11. SecurityScansPage.tsx (216 bytes) ✅ ROUTED (STUB)
12. ThreatModelPage.tsx (210 bytes) ✅ ROUTED (STUB)
13. VulnerabilitiesPage.tsx (21KB) ✅ ROUTED
14. VulnerabilityDetailPage.tsx (234 bytes) ✅ ROUTED (STUB)

**Issues:**
- All routes correctly mapped
- Many stub pages exist (can be enhanced):
  - ComplianceFrameworkPage, PoliciesPage, PolicyDetailPage
  - ScanResultsPage, SecretsPage, SecurityAlertsPage
  - SecurityScansPage, ThreatModelPage, VulnerabilityDetailPage

**Recommendation:**
- All routes working correctly
- Enhance stub pages as needed

---

## Summary of Issues

### Critical (Must Fix Immediately)

1. **Bootstrapping:**
   - ✅ Router correctly imports TemplateSelectionPage (mapped as TemplateGalleryPage)
   - ❌ Missing routes: BootstrapCollaboratePage, BootstrapReviewPage, ResumeSessionPage

2. **Initialization:**
   - ✅ Router correctly imports InitializationWizardPage (mapped as SetupWizardPage)
   - ❌ Missing routes: InitializationCompletePage, InitializationProgressPage

3. **Development:**
   - ✅ Router correctly imports CodeReviewDashboardPage (mapped as CodeReviewPage)
   - ✅ Router correctly imports VelocityChartsPage (mapped as VelocityPage)
   - ❌ Missing routes: DeploymentDetailPage, SprintListPage

4. **Operations:**
   - ❌ Router imports IncidentsPage (stub) instead of IncidentListPage (real)
   - ❌ Missing route: TracesPage
   - ⚠️ Two dashboard implementations (OpsDashboardPage vs OperationsDashboardPage)

### Stub Pages (Can Enhance Later)

**Total Stub Pages: 26**

- Bootstrapping: 2 stubs (ProjectPreviewPage, TemplateGalleryPage)
- Initialization: 4 stubs (EnvironmentSetupPage, InfrastructureConfigPage, SetupProgressPage, TeamInvitePage)
- Development: 3 stubs (EpicsPage, PullRequestsPage, PullRequestDetailPage)
- Operations: 8 stubs (DashboardEditorPage, DashboardsPage, OnCallPage, PostmortemsPage, RunbookDetailPage, RunbooksPage, ServiceMapPage, WarRoomPage)
- Collaboration: 3 stubs (ActivityFeedPage, ArticleEditorPage, ArticlePage, ChannelPage, DirectMessagePage, GoalsPage, RetrosPage, StandupsPage)
- Security: 6 stubs (ComplianceFrameworkPage, PoliciesPage, PolicyDetailPage, ScanResultsPage, SecretsPage, SecurityAlertsPage, SecurityScansPage, ThreatModelPage, VulnerabilityDetailPage)

---

## Action Plan

### Phase 1: Fix Critical Route Mismatches (Day 1)

1. **Fix Operations IncidentsPage import**
   - Change from `IncidentsPage` to `IncidentListPage`

2. **Add Missing Routes:**
   - Bootstrapping: `/collaborate`, `/review`, `/resume`
   - Initialization: `/complete`, `/progress` (real pages, not stubs)
   - Development: `/deployments/:deploymentId`, `/sprints`
   - Operations: `/traces`

### Phase 2: Add CI Route Validation (Day 2)

Create script to validate:
1. Every lazy import in routes.tsx exists on disk
2. Every page file has at least one route (or is documented as unreachable)
3. No stub pages are routed when real implementations exist

### Phase 3: Enhance Stub Pages (Weeks 1-9)

Systematically replace stub pages with real implementations according to phase priorities.

---

## Files Requiring Updates

1. `apps/web/src/router/routes.tsx` - Fix imports and add missing routes
2. Create `scripts/validate-routes.ts` - CI validation script
3. Update `.github/workflows/ci.yml` - Add route validation step

---

**Status:** Ready for implementation  
**Next Step:** Fix critical route mismatches in routes.tsx
