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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ProtectedRoute } from '@/components/security/ProtectedRoute';
import { NavBar } from '@/components/shared/NavBar';
import { AuthProvider } from '@/context/AuthContext';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

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
  return (
    <div className="flex h-screen overflow-hidden bg-gray-50 dark:bg-gray-950">
      <NavBar />
      <main className="flex-1 overflow-auto">
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
