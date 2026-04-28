/**
 * RunDetailPage provenance coverage.
 *
 * @doc.type test
 * @doc.purpose Ensure run detail exposes lineage provenance fields instead of only raw events
 * @doc.layer frontend
 */
import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { Provider, createStore } from 'jotai';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@ghatana/theme';
import { tenantIdAtom } from '@/stores/tenant.store';
import { RunDetailPage } from '@/pages/RunDetailPage';
import * as aepApi from '@/api/aep.api';

vi.mock('@/api/aep.api');
vi.mock('@/lib/feature-flags', () => ({
  isFeatureEnabled: () => true,
  featureFlags: {
    EVENT_LINEAGE: true,
    AGENT_DECISIONS: true,
    POLICY_REFERENCES: true,
  },
}));

function renderRunDetailPage() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, staleTime: 0 },
      mutations: { retry: false },
    },
  });
  const store = createStore();
  store.set(tenantIdAtom, 'tenant-acme');

  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <MemoryRouter initialEntries={['/operate/runs/run-123']}>
            <Routes>
              <Route path="/operate/runs/:runId" element={<RunDetailPage />} />
            </Routes>
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('RunDetailPage', () => {
  beforeEach(() => {
    vi.mocked(aepApi.cancelRun).mockResolvedValue(undefined);
    vi.mocked(aepApi.getRunDetail).mockResolvedValue({
      id: 'run-123',
      pipelineId: 'pipeline-7',
      pipelineName: 'Fraud Triage',
      status: 'SUCCEEDED',
      startedAt: '2026-04-27T10:00:00.000Z',
      finishedAt: '2026-04-27T10:01:00.000Z',
      eventsProcessed: 4,
      errorsCount: 0,
      lineage: [
        {
          eventType: 'run.completed',
          timestamp: '2026-04-27T10:01:00.000Z',
          pipelineId: 'pipeline-7',
          stepType: 'run.completed',
          status: 'SUCCEEDED',
          details: {
            pipelineVersion: 'v7.3.1',
            evaluationGate: 'composite',
            agentVersions: [
              { agentId: 'reasoner', version: '2.4.0' },
              { agentId: 'policy-agent', version: '1.9.1' },
            ],
            policyBundle: ['default-allow@12', 'pii-enforcement@4'],
            complianceBundle: {
              piiEnforcement: 'BLOCK',
              killSwitchEnabled: true,
            },
          },
        },
      ],
      decisions: [],
      policies: [
        {
          policyId: 'default-allow',
          skillId: 'policy-agent',
          version: '12',
          promotedAt: '2026-04-27T10:00:30.000Z',
          stepType: 'policy.promoted',
        },
      ],
      provenance: {
        pipelineVersion: 'v7.3.1',
        agentVersions: [
          { agentId: 'reasoner', version: '2.4.0' },
          { agentId: 'policy-agent', version: '1.9.1' },
        ],
        policyBundle: ['default-allow@12', 'pii-enforcement@4'],
        evaluationGate: 'composite',
        complianceBundle: {
          piiEnforcement: 'BLOCK',
          killSwitchEnabled: true,
        },
      },
    });
  });

  it('renders the lineage provenance block with execution metadata', async () => {
    renderRunDetailPage();

    await waitFor(() => expect(screen.getByText('Run lineage summary')).toBeInTheDocument());
    expect(screen.getByText('v7.3.1')).toBeInTheDocument();
    expect(screen.getByText('composite')).toBeInTheDocument();
    expect(screen.getByText('reasoner v2.4.0')).toBeInTheDocument();
    expect(screen.getByText('policy-agent v1.9.1')).toBeInTheDocument();
    expect(screen.getByText('default-allow@12')).toBeInTheDocument();
    expect(screen.getByText('pii-enforcement@4')).toBeInTheDocument();
    expect(screen.getByText('BLOCK')).toBeInTheDocument();
    expect(screen.getByText('Enabled')).toBeInTheDocument();
  });
});
