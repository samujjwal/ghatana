import React from 'react';
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from '@/context/AuthContext';
import { AiActionLogPage } from '@/pages/AiActionLogPage';

vi.mock('@/hooks/useAiActionLog', () => ({
  useAiActionLog: () => ({
    entries: [
      {
        actionId: 'act-1',
        workspaceId: 'ws-1',
        correlationId: 'corr-1',
        actionType: 'DRAFT_GENERATED',
        status: 'PROPOSED',
        actor: 'agent',
        initiatedByAi: true,
        confidence: 0.8,
        evidenceLinks: [],
        policyChecks: [],
        summary: 'Generated draft',
        details: 'Used strategy',
        relatedEntityId: 'content-1',
        occurredAt: '2026-01-01T00:00:00Z',
      },
    ],
    isLoading: false,
    isError: false,
    error: null,
  }),
  useAiActionDetail: () => ({
    entry: null,
    isLoading: false,
    isError: false,
    error: null,
  }),
}));

function renderPage(path: string, token: string | null = 'test-token'): void {
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <AuthProvider
        initialToken={token}
        initialWorkspaceId="ws-1"
        initialTenantId="tenant-1"
        initialRoles={[]}
      >
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/login" element={<div data-testid="login-page" />} />
            <Route path="/workspaces/:workspaceId/ai-actions" element={<AiActionLogPage />} />
            <Route path="/workspaces/:workspaceId/ai-actions/:actionId" element={<AiActionLogPage />} />
          </Routes>
        </MemoryRouter>
      </AuthProvider>
    </QueryClientProvider>,
  );
}

describe('AiActionLogPage', () => {
  it('redirects unauthenticated user to login', () => {
    renderPage('/workspaces/ws-1/ai-actions', null);
    expect(screen.getByTestId('login-page')).toBeInTheDocument();
  });

  it('renders timeline entries', async () => {
    renderPage('/workspaces/ws-1/ai-actions');
    expect(await screen.findByTestId('ai-action-log-page')).toBeInTheDocument();
    expect(screen.getByTestId('ai-action-log-item-act-1')).toBeInTheDocument();
  });
});
