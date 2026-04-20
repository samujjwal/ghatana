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
 *   /build/pipelines      → PipelineListPage
 *   /build/pipelines/new  → PipelineBuilderPage
 *   /build/patterns       → PatternStudioPage
 *   /learn/episodes       → LearningPage (episodes, reflection)
 *   /learn/memory         → MemoryExplorerPage
 *   /govern               → GovernancePage (policies, compliance, audit)
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
 *   /agents/:id           → /catalog/agents/:id
 *   /monitoring           → /operate
 *   /patterns             → /build/patterns
 *   /hitl                 → /operate/reviews
 *   /learning             → /learn/episodes
 *   /workflows            → /catalog/workflows
 *   /memory               → /learn/memory
 *
 * @doc.type router
 * @doc.purpose AEP operator-cockpit routing — outcome-first navigation
 * @doc.layer frontend
 */
import React, { Suspense, lazy } from 'react';
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
import { FuzzyFinder, DEFAULT_FINDER_ITEMS } from '@/components/core/FuzzyFinder';
import { AuthProvider } from '@/context/AuthContext';
import { useLocation, useNavigate } from 'react-router';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

// P2-14: AEP-specific finder items for command palette
const getAEPFinderItems = (navigate: (path: string) => void) => [
  {
    id: 'monitoring',
    label: 'Monitoring Dashboard',
    icon: <BarChart3 className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/operate'),
  },
  {
    id: 'costs',
    label: 'Cost Dashboard',
    icon: <BarChart3 className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/operate/costs'),
  },
  {
    id: 'reviews',
    label: 'HITL Reviews',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/operate/reviews'),
  },
  {
    id: 'pipelines',
    label: 'Pipelines',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/build/pipelines'),
  },
  {
    id: 'new-pipeline',
    label: 'New Pipeline',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/build/pipelines/new'),
  },
  {
    id: 'patterns',
    label: 'Pattern Studio',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/build/patterns'),
  },
  {
    id: 'episodes',
    label: 'Learning Episodes',
    icon: <Database className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/learn/episodes'),
  },
  {
    id: 'memory',
    label: 'Memory Explorer',
    icon: <Database className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/learn/memory'),
  },
  {
    id: 'governance',
    label: 'Governance',
    icon: <Shield className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/govern'),
  },
  {
    id: 'agents',
    label: 'Agent Registry',
    icon: <Settings className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/catalog/agents'),
  },
  {
    id: 'marketplace',
    label: 'Agent Marketplace',
    icon: <Settings className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/catalog/marketplace'),
  },
  {
    id: 'workflows',
    label: 'Workflow Catalog',
    icon: <FileText className="h-4 w-4" />,
    category: 'Pages',
    action: () => navigate('/catalog/workflows'),
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
const LearningPage = lazy(() =>
  import('@/pages/LearningPage').then((m) => ({ default: m.LearningPage })),
);
const MemoryExplorerPage = lazy(() =>
  import('@/pages/MemoryExplorerPage').then((m) => ({ default: m.MemoryExplorerPage })),
);
const GovernancePage = lazy(() =>
  import('@/pages/GovernancePage').then((m) => ({ default: m.GovernancePage })),
);
const AgentRegistryPage = lazy(() =>
  import('@/pages/AgentRegistryPage').then((m) => ({ default: m.AgentRegistryPage })),
);
const AgentDetailPage = lazy(() =>
  import('@/pages/AgentDetailPage').then((m) => ({ default: m.AgentDetailPage })),
);
const WorkflowCatalogPage = lazy(() =>
  import('@/pages/WorkflowCatalogPage').then((m) => ({ default: m.WorkflowCatalogPage })),
);
const AgentMarketplacePage = lazy(() =>
  import('@/pages/AgentMarketplacePage').then((m) => ({ default: m.AgentMarketplacePage })),
);
const LoginPage = lazy(() =>
  import('@/pages/LoginPage').then((m) => ({ default: m.LoginPage })),
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
  
  // P2-14: Generate breadcrumbs from current route
  const breadcrumbItems = generateBreadcrumbs(location.pathname);
  
  // P2-14: Get finder items with navigation actions
  const finderItems = getAEPFinderItems(navigate);

  return (
    <div className="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-950">
      <NavBar />
      <main className="flex-1 overflow-auto">
        <div className="px-6 py-4 flex items-center justify-between">
          <Breadcrumbs items={breadcrumbItems} />
          <FuzzyFinder items={finderItems} enableShortcut={true} />
        </div>
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

              <Route element={<ProtectedShell />}>
                {/* ── Canonical routes ─────────────────────────── */}
                <Route path="/operate" element={<MonitoringDashboardPage />} />
                <Route path="/operate/costs" element={<CostDashboardPage />} />
                <Route path="/operate/reviews" element={<HitlReviewPage />} />
                <Route path="/operate/runs/:runId" element={<RunDetailPage />} />

                <Route path="/build/pipelines" element={<PipelineListPage />} />
                <Route path="/build/pipelines/new" element={<PipelineBuilderPage />} />
                <Route path="/build/patterns" element={<PatternStudioPage />} />

                <Route path="/learn/episodes" element={<LearningPage />} />
                <Route path="/learn/memory" element={<MemoryExplorerPage />} />

                <Route path="/govern" element={<GovernancePage />} />

                <Route path="/catalog/agents" element={<AgentRegistryPage />} />
                <Route path="/catalog/agents/:agentId" element={<AgentDetailPage />} />
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
                <Route path="*" element={<Navigate to="/operate" replace />} />
              </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
