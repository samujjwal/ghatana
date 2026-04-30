/**
 * AEP Application Router
 *
 * Provides top-level routing for all AEP UI pages using React Router v7.
 * Navigation is organised around operator outcomes (Operate / Build / Learn / Govern / Catalog)
 * rather than individual capability pages.
 *
 * Canonical routes:
 *   /operate              → MonitoringDashboardPage (runs, alerts, failures)
 *   /operate/costs        → CostDashboardPage (tenant spend and budget visibility)
 *   /operate/reviews      → HitlReviewPage (human review queue)
 *   /operate/runs/:runId  → RunDetailPage (unified run detail + lineage + decisions)
 *   /operate/operations   → OperationCenterPage (active and historical operations)
 *   /build/pipelines      → PipelineListPage
 *   /build/pipelines/new  → PipelineBuilderPage
 *   /build/patterns       → PatternStudioPage
 *   /learn/episodes       → LearningPage (episodes, reflection)
 *   /learn/memory         → MemoryExplorerPage
 *   /govern               → GovernancePage (policies, compliance, audit)
 *   /govern/privacy       → PrivacyRequestPage (GDPR/CCPA fulfilment workbench)
 *   /catalog/agents       → AgentRegistryPage
 *   /catalog/agents/:id   → AgentDetailPage
 *   /catalog/marketplace  → AgentMarketplacePage
 *   /catalog/workflows    → WorkflowCatalogPage
 *
 * Backward-compat redirects (old noun-based routes → canonical paths):
 *   /                     → /operate
 *   /pipelines/list       → /build/pipelines
 *   /pipelines            → /build/pipelines/new
 *   /agents               → /catalog/agents
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React, { lazy, Suspense } from 'react';
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
  Outlet,
  useParams,
} from 'react-router';
import { BarChart3, FileText, Database, Shield, Settings } from 'lucide-react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProtectedRoute } from '@/components/security/ProtectedRoute';
import { NavBar } from '@/components/shared/NavBar';
import { Breadcrumbs } from '@/components/core/Breadcrumbs';
import { FuzzyFinder } from '@/components/core/FuzzyFinder';
import { AuthProvider } from '@/context/AuthContext';
import { useLocation, useNavigate } from 'react-router';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { RuntimeTruthBanner } from '@/components/shared/RuntimeTruthBanner';
import {
  getAgentRegistryUrl,
  getCostDashboardUrl,
  getGovernanceUrl,
  getLearningEpisodesUrl,
  getMarketplaceUrl,
  getMemoryExplorerUrl,
  getNewPipelineUrl,
  getOperateUrl,
  getPatternStudioUrl,
  getPipelineListUrl,
  getPrivacyRequestsUrl,
  getReviewQueueUrl,
  getWorkflowCatalogUrl,
} from '@/lib/routes';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

// P2-14: AEP-specific finder items for command palette — only canonical routes
const getAEPFinderItems = (navigate: (path: string) => void) => [
  {
    id: 'monitoring',
    label: 'Monitoring Dashboard',
    icon: <BarChart3 className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getOperateUrl()),
  },
  {
    id: 'costs',
    label: 'Cost Dashboard',
    icon: <BarChart3 className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getCostDashboardUrl()),
  },
  {
    id: 'reviews',
    label: 'HITL Reviews',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getReviewQueueUrl()),
  },
  {
    id: 'pipelines',
    label: 'Pipelines',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getPipelineListUrl()),
  },
  {
    id: 'new-pipeline',
    label: 'New Pipeline',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getNewPipelineUrl()),
  },
  {
    id: 'patterns',
    label: 'Pattern Studio',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getPatternStudioUrl()),
  },
  {
    id: 'episodes',
    label: 'Learning Episodes',
    icon: <Database className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getLearningEpisodesUrl()),
  },
  {
    id: 'memory',
    label: 'Memory Explorer',
    icon: <Database className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getMemoryExplorerUrl()),
  },
  {
    id: 'governance',
    label: 'Governance',
    icon: <Shield className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getGovernanceUrl()),
  },
  {
    id: 'privacy',
    label: 'Privacy Requests',
    icon: <Shield className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getPrivacyRequestsUrl()),
  },
  {
    id: 'agents',
    label: 'Agent Registry',
    icon: <Settings className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getAgentRegistryUrl()),
  },
  {
    id: 'marketplace',
    label: 'Agent Marketplace',
    icon: <Settings className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getMarketplaceUrl()),
  },
  {
    id: 'workflows',
    label: 'Workflow Catalog',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate(getWorkflowCatalogUrl()),
  },
];

// Lazy-loaded pages — canonical routes
const MonitoringDashboardPage = lazy(() =>
  import('@/pages/MonitoringDashboardPage').then((m) => ({ default: m.MonitoringDashboardPage })),
);
const HitlReviewPage = lazy(() =>
  import('@/pages/HitlReviewPage').then((m) => ({ default: m.HitlReviewPage })),
);
const CostDashboardPage = lazy(() =>
  import('@/pages/CostDashboardPage').then((m) => ({ default: m.CostDashboardPage })),
);
const RunDetailPage = lazy(() =>
  import('@/pages/RunDetailPage').then((m) => ({ default: m.RunDetailPage })),
);
const PipelineListPage = lazy(() =>
  import('@/pages/PipelineListPage').then((m) => ({ default: m.PipelineListPage })),
);
const PipelineBuilderPage = lazy(() =>
  import('@/pages/PipelineBuilderPage').then((m) => ({ default: m.PipelineBuilderPage })),
);
const PatternStudioPage = lazy(() =>
  import('@/pages/PatternStudioPage').then((m) => ({ default: m.PatternStudioPage })),
);
// NOTE: LearningPage consolidated into PatternStudioPage (TASK-S1)
// /learn/episodes now renders PatternStudioPage with ?tab=learning
const MemoryExplorerPage = lazy(() =>
  import('@/pages/MemoryExplorerPage').then((m) => ({ default: m.MemoryExplorerPage })),
);
const GovernancePage = lazy(() =>
  import('@/pages/GovernancePage').then((m) => ({ default: m.GovernancePage })),
);
const PrivacyRequestPage = lazy(() =>
  import('@/pages/PrivacyRequestPage').then((m) => ({ default: m.PrivacyRequestPage })),
);
const AgentRegistryPage = lazy(() =>
  import('@/pages/AgentRegistryPage').then((m) => ({ default: m.AgentRegistryPage })),
);
// NOTE: AgentDetailPage consolidated into AgentRegistryPage inline drawer (TASK-S2)
// /catalog/agents/:agentId now redirects to /catalog/agents
const WorkflowCatalogPage = lazy(() =>
  import('@/pages/WorkflowCatalogPage').then((m) => ({ default: m.WorkflowCatalogPage })),
);
const AgentMarketplacePage = lazy(() =>
  import('@/pages/AgentMarketplacePage').then((m) => ({ default: m.AgentMarketplacePage })),
);
const LoginPage = lazy(() =>
  import('@/pages/LoginPage').then((m) => ({ default: m.LoginPage })),
);
const SsoCallbackPage = lazy(() =>
  import('@/pages/SsoCallbackPage').then((m) => ({ default: m.SsoCallbackPage })),
);
const SessionExpiryPage = lazy(() =>
  import('@/pages/SessionExpiryPage').then((m) => ({ default: m.SessionExpiryPage })),
);
const OperationCenterPage = lazy(() =>
  import('@/pages/OperationCenterPage').then((m) => ({ default: m.OperationCenterPage })),
);

// ─── Redirect helpers ──────────────────────────────────────────────────

/** Redirect /agents/:id → /catalog/agents/:id while preserving the dynamic segment. */
function AgentDetailRedirect() {
  const { agentId } = useParams<{ agentId: string }>();
  return <Navigate to={`/catalog/agents/${agentId ?? ''}`} replace />;
}

// ─── Layout ──────────────────────────────────────────────────────────

function PageShell({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const navigate = useNavigate();
  const [mobileNavOpen, setMobileNavOpen] = React.useState(false);

  // P2-14: Generate breadcrumbs from current route (only when feature flag enabled)
  const breadcrumbItems = generateBreadcrumbs(location.pathname);

  // P2-14: Get finder items with navigation actions (only when feature flag enabled)
  const finderItems = getAEPFinderItems(navigate);

  const showBreadcrumbs = isFeatureEnabled('BREADCRUMBS');
  const showCommandPalette = isFeatureEnabled('COMMAND_PALETTE');

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-950">
      {/* Desktop sidebar — always visible on md+ */}
      <div className="hidden md:block">
        <NavBar />
      </div>

      {/* Mobile nav overlay */}
      {mobileNavOpen && (
        <div className="fixed inset-0 z-50 flex md:hidden">
          <div
            className="flex-1 bg-black/50"
            onClick={() => setMobileNavOpen(false)}
            aria-hidden="true"
          />
          <div className="w-52 bg-white dark:bg-gray-950 shadow-xl">
            <NavBar />
          </div>
        </div>
      )}

      <main className="flex-1 overflow-auto min-w-0">
        {/* Mobile header with hamburger */}
        <div className="md:hidden flex items-center px-4 py-2 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-950">
          <button
            type="button"
            onClick={() => setMobileNavOpen(true)}
            className="p-2 rounded-md text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800"
            aria-label="Open navigation menu"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <span className="ml-2 text-sm font-semibold text-gray-900 dark:text-gray-100">AEP</span>
        </div>

        {/* F-040: Runtime-truth banner — server-driven capability status */}
        <RuntimeTruthBanner />

        {(showBreadcrumbs || showCommandPalette) && (
          <div className="px-4 md:px-6 py-3 md:py-4 flex items-center justify-between">
            {showBreadcrumbs && <Breadcrumbs items={breadcrumbItems} />}
            {showCommandPalette && <FuzzyFinder items={finderItems} enableShortcut={true} />}
          </div>
        )}
        <Suspense
          fallback={
            <div className="flex h-full items-center justify-center text-gray-400">
              Loading…
            </div>
          }
        >
          {children}
        </Suspense>
      </main>
    </div>
  );
}

/**
 * P2-14: Generate breadcrumb items from current pathname
 */
function generateBreadcrumbs(pathname: string) {
  const parts = pathname.split('/').filter(Boolean);
  const items: { label: string; href?: string }[] = [];

  // Build breadcrumb trail
  let currentPath = '';
  for (let i = 0; i < parts.length; i++) {
    currentPath += '/' + parts[i];
    const label = formatBreadcrumbLabel(parts[i]);

    // Don't make the last item clickable
    if (i === parts.length - 1) {
      items.push({ label });
    } else {
      items.push({ label, href: currentPath });
    }
  }

  return items;
}

/**
 * Format breadcrumb labels for better readability
 */
function formatBreadcrumbLabel(segment: string): string {
  const labelMap: Record<string, string> = {
    'operate': 'Operate',
    'build': 'Build',
    'learn': 'Learn',
    'govern': 'Governance',
    'catalog': 'Catalog',
    'pipelines': 'Pipelines',
    'patterns': 'Patterns',
    'episodes': 'Episodes',
    'memory': 'Memory',
    'privacy': 'Privacy',
    'agents': 'Agents',
    'marketplace': 'Marketplace',
    'workflows': 'Workflows',
    'reviews': 'Reviews',
    'costs': 'Costs',
    'runs': 'Runs',
  };

  // Check if it's a dynamic segment (like :runId)
  if (segment.startsWith(':')) {
    return segment.replace(':', '').replace(/([A-Z])/g, ' $1').trim();
  }

  return labelMap[segment] || segment.charAt(0).toUpperCase() + segment.slice(1);
}

function ProtectedShell() {
  return (
    <ProtectedRoute>
      <PageShell>
        <Outlet />
      </PageShell>
    </ProtectedRoute>
  );
}

// ─── App ─────────────────────────────────────────────────────────────

/**
 * Root application component with outcome-oriented React Router routing.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Suspense
            fallback={
              <div className="flex min-h-screen items-center justify-center bg-gray-50 text-gray-400 dark:bg-gray-950">
                Loading…
              </div>
            }
          >
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/auth/callback" element={<SsoCallbackPage />} />
              <Route path="/session-expired" element={<SessionExpiryPage />} />

              <Route element={<ProtectedShell />}>
                {/* ── Canonical routes ─────────────────────────── */}
                <Route path="/operate" element={<MonitoringDashboardPage />} />
                <Route path="/operate/costs" element={<CostDashboardPage />} />
                <Route path="/operate/reviews" element={<HitlReviewPage />} />
                <Route path="/operate/runs/:runId" element={<RunDetailPage />} />
                <Route path="/operate/operations" element={<OperationCenterPage />} />

                <Route path="/build/pipelines" element={<PipelineListPage />} />
                <Route path="/build/pipelines/new" element={<PipelineBuilderPage />} />
                <Route path="/build/pipelines/:pipelineId/edit" element={<PipelineBuilderPage />} />
                <Route path="/build/patterns" element={<PatternStudioPage />} />

                <Route path="/learn/episodes" element={<Navigate to="/build/patterns?tab=learning" replace />} />
                <Route path="/learn/memory" element={<MemoryExplorerPage />} />

                <Route path="/govern" element={<GovernancePage />} />
                <Route path="/govern/privacy" element={<PrivacyRequestPage />} />

                <Route path="/catalog/agents" element={<AgentRegistryPage />} />
                <Route path="/catalog/agents/:agentId" element={<AgentRegistryPage />} />
                <Route path="/catalog/marketplace" element={<AgentMarketplacePage />} />
                <Route path="/catalog/workflows" element={<WorkflowCatalogPage />} />

                {/* ── Backward-compat redirects ─────────────────── */}
                <Route index element={<Navigate to="/operate" replace />} />
                <Route path="/pipelines/list" element={<Navigate to="/build/pipelines" replace />} />
                <Route path="/pipelines" element={<Navigate to="/build/pipelines/new" replace />} />
                <Route path="/agents" element={<Navigate to="/catalog/agents" replace />} />
                <Route path="/agents/:agentId" element={<AgentDetailRedirect />} />
                <Route path="/monitoring" element={<Navigate to="/operate" replace />} />
                <Route path="/patterns" element={<Navigate to="/build/patterns" replace />} />
                <Route path="/hitl" element={<Navigate to="/operate/reviews" replace />} />
                <Route path="/learning" element={<Navigate to="/learn/episodes" replace />} />
                <Route path="/workflows" element={<Navigate to="/catalog/workflows" replace />} />
                <Route path="/memory" element={<Navigate to="/learn/memory" replace />} />
                <Route
                  path="*"
                  element={
                    <div className="flex flex-col items-center justify-center h-full text-gray-500">
                      <h1 className="text-2xl font-semibold mb-2">Page not found</h1>
                      <p className="text-sm mb-4">The requested page does not exist.</p>
                      <a href="/operate" className="text-indigo-600 hover:underline text-sm">
                        Go to Monitoring Dashboard
                      </a>
                    </div>
                  }
                />
              </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
