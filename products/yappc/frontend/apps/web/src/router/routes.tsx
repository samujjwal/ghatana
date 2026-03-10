/**
 * YAPPC Application Routes
 *
 * @description Routing configuration for all YAPPC phases using
 * React Router v7 with route guards, lazy loading, and nested layouts.
 *
 * @doc.type routing
 * @doc.purpose Application navigation
 * @doc.layer infrastructure
 */

import React, { Suspense, lazy } from 'react';
import {
  createBrowserRouter,
  RouterProvider,
  Navigate,
  Outlet,
  RouteObject,
} from 'react-router';
import { useAtomValue } from 'jotai';

import {
  userAtom,
  isAuthenticatedAtom,
  currentProjectAtom,
} from '../state/atoms';

// =============================================================================
// Lazy-loaded Page Components
// =============================================================================

// Auth Pages
const SSOCallbackPage = lazy(() => import('../pages/auth/SSOCallbackPage'));

// Landing
const LandingPage = lazy(() => import('../pages/LandingPage'));

// Dashboard
const DashboardPage = lazy(() => import('../pages/dashboard/DashboardPage'));
const ProjectsPage = lazy(() => import('../pages/dashboard/ProjectsPage'));
const UnifiedProjectDashboard = lazy(
  () => import('../pages/dashboard/UnifiedProjectDashboard')
);
const PhaseOverviewPage = lazy(
  () => import('../pages/dashboard/PhaseOverviewPage')
);
const SettingsPage = lazy(() => import('../pages/settings/SettingsPage'));
const ProfilePage = lazy(() => import('../pages/settings/ProfilePage'));

// Bootstrapping Phase (Phase 1)
const BootstrapSessionPage = lazy(
  () => import('../pages/bootstrapping/BootstrapSessionPage')
);
const TemplateSelectionPage = lazy(
  () => import('../pages/bootstrapping/TemplateSelectionPage')
);
const UploadDocsPage = lazy(
  () => import('../pages/bootstrapping/UploadDocsPage')
);
const BootstrapCompletePage = lazy(
  () => import('../pages/bootstrapping/BootstrapCompletePage')
);
const BootstrapCollaboratePage = lazy(
  () => import('../pages/bootstrapping/BootstrapCollaboratePage')
);
const ResumeSessionPage = lazy(
  () => import('../pages/bootstrapping/ResumeSessionPage')
);
const BootstrapPreviewPage = lazy(
  () => import('../pages/bootstrapping/BootstrapPreviewPage')
);

// Initialization Phase (Phase 2)
const SetupWizardPage = lazy(
  () => import('../pages/initialization/InitializationWizardPage')
);
const InfrastructureConfigPage = lazy(
  () => import('../pages/initialization/InfrastructureConfigPage')
);
const EnvironmentSetupPage = lazy(
  () => import('../pages/initialization/EnvironmentSetupPage')
);
const TeamInvitePage = lazy(
  () => import('../pages/initialization/TeamInvitePage')
);
const InitializationPresetsPage = lazy(
  () => import('../pages/initialization/InitializationPresetsPage')
);
const InitializationCompletePage = lazy(
  () => import('../pages/initialization/InitializationCompletePage')
);
const InitializationProgressPage = lazy(
  () => import('../pages/initialization/InitializationProgressPage')
);

// Development Phase (Phase 3)
const DevDashboardPage = lazy(
  () => import('../pages/development/DevDashboardPage')
);
const SprintBoardPage = lazy(
  () => import('../pages/development/SprintBoardPage')
);
const PullRequestDetailPage = lazy(
  () => import('../pages/development/PullRequestDetailPage')
);
const PullRequestsPage = lazy(
  () => import('../pages/development/PullRequestsPage')
);
const VelocityPage = lazy(
  () => import('../pages/development/VelocityChartsPage')
);
const CodeReviewPage = lazy(
  () => import('../pages/development/CodeReviewPage')
);
const CodeReviewDetailPage = lazy(
  () => import('../pages/development/CodeReviewDetailPage')
);
const SprintPlanningPage = lazy(
  () => import('../pages/development/SprintPlanningPage')
);
const BacklogPage = lazy(
  () => import('../pages/development/BacklogPage')
);
const StoryDetailPage = lazy(
  () => import('../pages/development/StoryDetailPage')
);
const EpicsPage = lazy(
  () => import('../pages/development/EpicsPage')
);
const FeatureFlagsPage = lazy(
  () => import('../pages/development/FeatureFlagsPage')
);
const DeploymentsPage = lazy(
  () => import('../pages/development/DeploymentsPage')
);

// Operations Phase (Phase 4)
const OpsDashboardPage = lazy(
  () => import('../pages/operations/OpsDashboardPage')
);
const WarRoomPage = lazy(() => import('../pages/operations/WarRoomPage'));
const DashboardEditorPage = lazy(
  () => import('../pages/operations/DashboardEditorPage')
);
const RunbookDetailPage = lazy(
  () => import('../pages/operations/RunbookDetailPage')
);
const OnCallPage = lazy(() => import('../pages/operations/OnCallPage'));
const ServiceMapPage = lazy(() => import('../pages/operations/ServiceMapPage'));
const IncidentsPage = lazy(
  () => import('../pages/operations/IncidentsPage')
);
const IncidentDetailPage = lazy(
  () => import('../pages/operations/IncidentDetailPage')
);
const AlertsPage = lazy(() => import('../pages/operations/AlertsPage'));
const DashboardsPage = lazy(
  () => import('../pages/operations/DashboardsPage')
);
const LogsPage = lazy(() => import('../pages/operations/LogsPage'));
const MetricsPage = lazy(() => import('../pages/operations/MetricsPage'));
const RunbooksPage = lazy(() => import('../pages/operations/RunbooksPage'));
const PostmortemsPage = lazy(
  () => import('../pages/operations/PostmortemsPage')
);

// Collaboration Phase (Phase 5)
const TeamHubPage = lazy(
  () => import('../pages/collaboration/TeamDashboardPage')
);
const CalendarPage = lazy(
  () => import('../pages/collaboration/CalendarPage')
);
const ArticlePage = lazy(() => import('../pages/collaboration/ArticlePage'));
const MessagesPage = lazy(
  () => import('../pages/collaboration/TeamChatPage')
);
const ChannelPage = lazy(() => import('../pages/collaboration/ChannelPage'));
const DirectMessagePage = lazy(
  () => import('../pages/collaboration/DirectMessagePage')
);
const ActivityFeedPage = lazy(
  () => import('../pages/collaboration/ActivityFeedPage')
);
const KnowledgeBasePage = lazy(
  () => import('../pages/collaboration/KnowledgeBasePage')
);
const ArticleEditPage = lazy(
  () => import('../pages/collaboration/ArticleEditPage')
);
const ArticleNewPage = lazy(
  () => import('../pages/collaboration/ArticleNewPage')
);
const StandupsPage = lazy(
  () => import('../pages/collaboration/StandupsPage')
);
const RetrosPage = lazy(() => import('../pages/collaboration/RetrosPage'));
const GoalsPage = lazy(() => import('../pages/collaboration/GoalsPage'));

// Security Phase (Phase 6)
const SecurityDashboardPage = lazy(
  () => import('../pages/security/SecurityDashboardPage')
);
const VulnerabilityDetailPage = lazy(
  () => import('../pages/security/VulnerabilityDetailPage')
);
const ScanResultsPage = lazy(() => import('../pages/security/ScanResultsPage'));
const PolicyDetailPage = lazy(
  () => import('../pages/security/PolicyDetailPage')
);
const ThreatModelPage = lazy(() => import('../pages/security/ThreatModelPage'));
const VulnerabilitiesPage = lazy(
  () => import('../pages/security/VulnerabilitiesPage')
);
const ScansPage = lazy(() => import('../pages/security/ScansPage'));
const CompliancePage = lazy(() => import('../pages/security/CompliancePage'));
const ComplianceDetailPage = lazy(
  () => import('../pages/security/ComplianceDetailPage')
);
const SecretsPage = lazy(() => import('../pages/security/SecretsPage'));
const PoliciesPage = lazy(() => import('../pages/security/PoliciesPage'));
const SecurityAlertsPage = lazy(
  () => import('../pages/security/SecurityAlertsPage')
);
const AuditPage = lazy(() => import('../pages/security/AuditPage'));

// Admin Pages
const TeamsPage = lazy(() => import('../pages/admin/TeamsPage'));
const BillingPage = lazy(() => import('../pages/admin/BillingPage'));

// Error Pages
const NotFoundPage = lazy(() => import('../pages/errors/NotFoundPage'));
const ErrorPage = lazy(() => import('../pages/errors/ErrorPage'));
const UnauthorizedPage = lazy(() => import('../pages/errors/UnauthorizedPage'));

// =============================================================================
// Loading Component
// =============================================================================

const PageLoader: React.FC = () => (
  <div className="flex items-center justify-center h-screen bg-zinc-950">
    <div className="flex flex-col items-center gap-4">
      <div className="w-12 h-12 border-4 border-violet-500/30 border-t-violet-500 rounded-full animate-spin" />
      <p className="text-sm text-zinc-400">Loading...</p>
    </div>
  </div>
);

// =============================================================================
// Route Guards
// =============================================================================

/**
 * Protect routes requiring authentication.
 */
const AuthGuard: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  if (!isAuthenticated) {
    return <Navigate to="/sso/callback" replace />;
  }

  return <>{children || <Outlet />}</>;
};

/**
 * Protect routes requiring a selected project.
 */
const ProjectGuard: React.FC<{ children?: React.ReactNode }> = ({
  children,
}) => {
  const currentProject = useAtomValue(currentProjectAtom);

  if (!currentProject) {
    return <Navigate to="/projects" replace />;
  }

  return <>{children || <Outlet />}</>;
};

/**
 * Protect admin routes.
 */
const AdminGuard: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
  const user = useAtomValue(userAtom);

  if (user?.role !== 'admin') {
    return <Navigate to="/unauthorized" replace />;
  }

  return <>{children || <Outlet />}</>;
};

/**
 * Redirect authenticated users away from auth pages.
 */
const GuestGuard: React.FC<{ children?: React.ReactNode }> = ({ children }) => {
  const isAuthenticated = useAtomValue(isAuthenticatedAtom);

  if (isAuthenticated) {
    return <Navigate to="/dashboard" replace />;
  }

  return <>{children || <Outlet />}</>;
};

// =============================================================================
// Layout Components
// =============================================================================

const AppLayout = lazy(() => import('../layouts/AppLayout'));
const AuthLayout = lazy(() => import('../layouts/AuthLayout'));
const PublicLayout = lazy(() => import('../layouts/PublicLayout'));
const ProjectLayout = lazy(() => import('../layouts/ProjectLayout'));

// =============================================================================
// Route Configuration
// =============================================================================

const routes: RouteObject[] = [
  // Public Routes
  {
    path: '/',
    element: (
      <Suspense fallback={<PageLoader />}>
        <PublicLayout />
      </Suspense>
    ),
    children: [{ index: true, element: <LandingPage /> }],
  },

  // Auth Routes (Guest only)
  {
    path: '/',
    element: (
      <GuestGuard>
        <Suspense fallback={<PageLoader />}>
          <AuthLayout />
        </Suspense>
      </GuestGuard>
    ),
    children: [{ path: 'sso/callback', element: <SSOCallbackPage /> }],
  },

  // Authenticated App Routes
  {
    path: '/',
    element: (
      <AuthGuard>
        <Suspense fallback={<PageLoader />}>
          <AppLayout />
        </Suspense>
      </AuthGuard>
    ),
    children: [
      { path: 'dashboard', element: <DashboardPage /> },
      { path: 'projects', element: <ProjectsPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: 'profile', element: <ProfilePage /> },
      { path: 'templates', element: <TemplateSelectionPage /> },
    ],
  },

  // Unified Project Dashboard
  {
    path: '/project/:projectId/unified',
    element: (
      <AuthGuard>
        <ProjectGuard>
          <Suspense fallback={<PageLoader />}>
            <UnifiedProjectDashboard />
          </Suspense>
        </ProjectGuard>
      </AuthGuard>
    ),
    children: [
      { index: true, element: <PhaseOverviewPage /> },
      { path: ':phase', element: <PhaseOverviewPage /> },
    ],
  },

  // Project-scoped Routes
  {
    path: '/project/:projectId',
    element: (
      <AuthGuard>
        <ProjectGuard>
          <Suspense fallback={<PageLoader />}>
            <ProjectLayout />
          </Suspense>
        </ProjectGuard>
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="development" replace /> },

      // Phase 1: Bootstrapping
      {
        path: 'bootstrap',
        children: [
          { index: true, element: <BootstrapSessionPage /> },
          { path: 'session/:sessionId', element: <BootstrapSessionPage /> },
          { path: 'upload', element: <UploadDocsPage /> },
          { path: 'collaborate', element: <BootstrapCollaboratePage /> },
          { path: 'resume', element: <ResumeSessionPage /> },
          { path: 'preview', element: <BootstrapPreviewPage /> },
          { path: 'complete', element: <BootstrapCompletePage /> },
        ],
      },

      // Phase 2: Initialization
      {
        path: 'setup',
        children: [
          { index: true, element: <SetupWizardPage /> },
          { path: 'infrastructure', element: <InfrastructureConfigPage /> },
          { path: 'environments', element: <EnvironmentSetupPage /> },
          { path: 'team', element: <TeamInvitePage /> },
          { path: 'progress', element: <InitializationProgressPage /> },
          { path: 'presets', element: <InitializationPresetsPage /> },
          { path: 'complete', element: <InitializationCompletePage /> },
        ],
      },

      // Phase 3: Development
      {
        path: 'development',
        children: [
          { index: true, element: <DevDashboardPage /> },
          { path: 'board', element: <SprintBoardPage /> },
          { path: 'board/:sprintId', element: <SprintBoardPage /> },
          { path: 'backlog', element: <BacklogPage /> },
          { path: 'stories/:storyId', element: <StoryDetailPage /> },
          { path: 'epics', element: <EpicsPage /> },
          { path: 'prs', element: <PullRequestsPage /> },
          { path: 'prs/:prId', element: <PullRequestDetailPage /> },
          { path: 'review/:prId', element: <CodeReviewPage /> },
          { path: 'velocity', element: <VelocityPage /> },
          { path: 'review-dashboard', element: <CodeReviewPage /> },
          { path: 'review-detail/:id', element: <CodeReviewDetailPage /> },
          { path: 'planning', element: <SprintPlanningPage /> },
          { path: 'flags', element: <FeatureFlagsPage /> },
          { path: 'deployments', element: <DeploymentsPage /> },
        ],
      },

      // Phase 4: Operations
      {
        path: 'operations',
        children: [
          { index: true, element: <OpsDashboardPage /> },
          { path: 'incidents', element: <IncidentsPage /> },
          { path: 'incidents/:incidentId', element: <IncidentDetailPage /> },
          {
            path: 'incidents/:incidentId/warroom',
            element: <WarRoomPage />,
          },
          { path: 'alerts', element: <AlertsPage /> },
          { path: 'dashboards', element: <DashboardsPage /> },
          {
            path: 'dashboards/:dashboardId',
            element: <DashboardEditorPage />,
          },
          { path: 'logs', element: <LogsPage /> },
          { path: 'metrics', element: <MetricsPage /> },
          { path: 'runbooks', element: <RunbooksPage /> },
          { path: 'runbooks/:runbookId', element: <RunbookDetailPage /> },
          { path: 'oncall', element: <OnCallPage /> },
          { path: 'services', element: <ServiceMapPage /> },
          { path: 'postmortems', element: <PostmortemsPage /> },
        ],
      },

      // Phase 5: Collaboration
      {
        path: 'team',
        children: [
          { index: true, element: <TeamHubPage /> },
          { path: 'calendar', element: <CalendarPage /> },
          { path: 'knowledge', element: <KnowledgeBasePage /> },
          { path: 'knowledge/new', element: <ArticleNewPage /> },
          { path: 'knowledge/:articleId', element: <ArticlePage /> },
          { path: 'knowledge/:articleId/edit', element: <ArticleEditPage /> },
          { path: 'standups', element: <StandupsPage /> },
          { path: 'retros', element: <RetrosPage /> },
          { path: 'goals', element: <GoalsPage /> },
          { path: 'messages', element: <MessagesPage /> },
          { path: 'messages/channel/:channelId', element: <ChannelPage /> },
          { path: 'messages/dm/:userId', element: <DirectMessagePage /> },
          { path: 'activity', element: <ActivityFeedPage /> },
        ],
      },

      // Phase 6: Security
      {
        path: 'security',
        children: [
          { index: true, element: <SecurityDashboardPage /> },
          { path: 'vulnerabilities', element: <VulnerabilitiesPage /> },
          {
            path: 'vulnerabilities/:vulnId',
            element: <VulnerabilityDetailPage />,
          },
          { path: 'scans', element: <ScansPage /> },
          { path: 'scans/:scanId', element: <ScanResultsPage /> },
          { path: 'compliance', element: <CompliancePage /> },
          {
            path: 'compliance/:frameworkId',
            element: <ComplianceDetailPage />,
          },
          { path: 'secrets', element: <SecretsPage /> },
          { path: 'policies', element: <PoliciesPage /> },
          { path: 'policies/:policyId', element: <PolicyDetailPage /> },
          { path: 'alerts', element: <SecurityAlertsPage /> },
          { path: 'audit', element: <AuditPage /> },
          { path: 'threat-model', element: <ThreatModelPage /> },
        ],
      },
    ],
  },

  // Admin Routes
  {
    path: '/admin',
    element: (
      <AuthGuard>
        <AdminGuard>
          <Suspense fallback={<PageLoader />}>
            <AppLayout />
          </Suspense>
        </AdminGuard>
      </AuthGuard>
    ),
    children: [
      { index: true, element: <Navigate to="teams" replace /> },
      { path: 'teams', element: <TeamsPage /> },
      { path: 'billing', element: <BillingPage /> },
    ],
  },

  // Error Routes
  { path: '/unauthorized', element: <UnauthorizedPage /> },
  { path: '/error', element: <ErrorPage /> },
  { path: '*', element: <NotFoundPage /> },
];

// =============================================================================
// Router Instance
// =============================================================================

export const router = createBrowserRouter(routes);

// =============================================================================
// Router Provider Component
// =============================================================================

export const AppRouter: React.FC = () => {
  return <RouterProvider router={router} />;
};

export default AppRouter;
