/**
 * DMOS App — root router.
 *
 * @doc.type component
 * @doc.purpose Root routing shell for the DMOS console
 * @doc.layer frontend
 */
import React, { Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { LoginPage } from '@/pages/LoginPage';
import { DashboardPage } from '@/pages/DashboardPage';
import { ApprovalQueuePage } from '@/pages/ApprovalQueuePage';
import { ApprovalDetailPage } from '@/pages/ApprovalDetailPage';
import { AiActionLogPage } from '@/pages/AiActionLogPage';
import { CampaignsPage } from '@/pages/CampaignsPage';
import { StrategyPage } from '@/pages/StrategyPage';
import { BudgetPage } from '@/pages/BudgetPage';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 30_000, retry: 1 },
  },
});

export function App(): React.ReactElement {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Suspense fallback={<div data-testid="app-loading">Loading…</div>}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route
                path="/workspaces/:workspaceId/dashboard"
                element={<DashboardPage />}
              />
              <Route
                path="/workspaces/:workspaceId/approvals"
                element={<ApprovalQueuePage />}
              />
              <Route
                path="/workspaces/:workspaceId/approvals/:requestId"
                element={<ApprovalDetailPage />}
              />
              <Route
                path="/workspaces/:workspaceId/ai-actions"
                element={<AiActionLogPage />}
              />
              <Route
                path="/workspaces/:workspaceId/ai-actions/:actionId"
                element={<AiActionLogPage />}
              />
              <Route
                path="/workspaces/:workspaceId/campaigns"
                element={<CampaignsPage />}
              />
              <Route
                path="/workspaces/:workspaceId/strategy"
                element={<StrategyPage />}
              />
              <Route
                path="/workspaces/:workspaceId/budget"
                element={<BudgetPage />}
              />
              <Route path="/" element={<Navigate to="/login" replace />} />
            </Routes>
          </Suspense>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  );
}
