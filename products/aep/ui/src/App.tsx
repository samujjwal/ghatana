/**
 * AEP Application Router
 *
 * Provides top-level routing for all AEP UI pages using React Router v7.
 * Uses lazy imports for code-splitting so PipelineBuilderPage (largest page)
 * doesn't block the initial shell render.
 *
 * Routes:
 *   /              → redirect to /pipelines/list
 *   /pipelines/list → PipelineListPage
 *   /pipelines     → PipelineBuilderPage
 *   /agents        → AgentRegistryPage
 *   /agents/:id    → AgentDetailPage
 *   /monitoring    → MonitoringDashboardPage
 *   /patterns      → PatternStudioPage
 *   /hitl          → HitlReviewPage
 *   /learning      → LearningPage
 *   /workflows     → WorkflowCatalogPage
 *   /memory        → MemoryExplorerPage
 *
 * @doc.type router
 * @doc.purpose AEP application routing
 * @doc.layer frontend
 */
import React, { Suspense, lazy } from 'react';
import {
  BrowserRouter,
  Routes,
  Route,
  Navigate,
} from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NavBar } from '@/components/shared/NavBar';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, staleTime: 30_000 },
  },
});

// Lazy-loaded pages
const PipelineListPage = lazy(() =>
  import('@/pages/PipelineListPage').then((m) => ({ default: m.PipelineListPage })),
);
const PipelineBuilderPage = lazy(() =>
  import('@/pages/PipelineBuilderPage').then((m) => ({ default: m.PipelineBuilderPage })),
);
const AgentRegistryPage = lazy(() =>
  import('@/pages/AgentRegistryPage').then((m) => ({ default: m.AgentRegistryPage })),
);
const MonitoringDashboardPage = lazy(() =>
  import('@/pages/MonitoringDashboardPage').then((m) => ({ default: m.MonitoringDashboardPage })),
);
const PatternStudioPage = lazy(() =>
  import('@/pages/PatternStudioPage').then((m) => ({ default: m.PatternStudioPage })),
);
const HitlReviewPage = lazy(() =>
  import('@/pages/HitlReviewPage').then((m) => ({ default: m.HitlReviewPage })),
);
const LearningPage = lazy(() =>
  import('@/pages/LearningPage').then((m) => ({ default: m.LearningPage })),
);
const AgentDetailPage = lazy(() =>
  import('@/pages/AgentDetailPage').then((m) => ({ default: m.AgentDetailPage })),
);const WorkflowCatalogPage = lazy(() =>
  import('@/pages/WorkflowCatalogPage').then((m) => ({ default: m.WorkflowCatalogPage }))
);
const MemoryExplorerPage = lazy(() =>
  import('@/pages/MemoryExplorerPage').then((m) => ({ default: m.MemoryExplorerPage }))
);
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

// ─── App ─────────────────────────────────────────────────────────────

/**
 * Root application component with full React Router routing.
 */
export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <PageShell>
          <Routes>
            <Route index element={<Navigate to="/pipelines/list" replace />} />
            <Route path="/pipelines/list" element={<PipelineListPage />} />
            <Route path="/pipelines" element={<PipelineBuilderPage />} />
            <Route path="/agents" element={<AgentRegistryPage />} />
            <Route path="/agents/:agentId" element={<AgentDetailPage />} />
            <Route path="/monitoring" element={<MonitoringDashboardPage />} />
            <Route path="/patterns" element={<PatternStudioPage />} />
            <Route path="/hitl" element={<HitlReviewPage />} />
            <Route path="/learning" element={<LearningPage />} />
            <Route path="/workflows" element={<WorkflowCatalogPage />} />
            <Route path="/memory" element={<MemoryExplorerPage />} />
            <Route path="*" element={<Navigate to="/pipelines/list" replace />} />
          </Routes>
        </PageShell>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
