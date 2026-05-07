import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryExplorerPage } from '@/pages/MemoryExplorerPage';
import { createAepTestWrapper } from '@/__tests__/test-utils/wrapper';

vi.mock('@/hooks/useAgents', () => ({
  useAgents: vi.fn(),
}));

vi.mock('@/hooks/useAgentMemory', () => ({
  useAllEpisodes: vi.fn(),
  useAgentEpisodes: vi.fn(),
  useAgentFacts: vi.fn(),
  usePolicies: vi.fn(),
}));

import { useAgents } from '@/hooks/useAgents';
import { useAllEpisodes, useAgentEpisodes, useAgentFacts, usePolicies } from '@/hooks/useAgentMemory';

describe('MemoryExplorerPage', () => {
  beforeEach(() => {
    vi.mocked(useAgents).mockReturnValue({
      data: [{ id: 'agent-001', name: 'Validator', tenantId: 'default' }],
    } as ReturnType<typeof useAgents>);

    vi.mocked(useAllEpisodes).mockReturnValue({
      data: [
        {
          id: 'ep-001',
          tenantId: 'default',
          agentId: 'agent-001',
          pipelineId: 'pipe-001',
          outcome: 'SUCCESS',
          latencyMs: 25,
          timestamp: new Date().toISOString(),
        },
      ],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAllEpisodes>);

    vi.mocked(useAgentEpisodes).mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAgentEpisodes>);

    vi.mocked(useAgentFacts).mockReturnValue({
      data: [
        {
          id: 'fact-001',
          tenantId: 'default',
          agentId: 'agent-001',
          type: 'SEMANTIC',
          subject: 'Invoice',
          predicate: 'requires_approval',
          object: 'true',
          confidence: 0.92,
          validityStatus: 'ACTIVE',
          createdAt: new Date().toISOString(),
        },
      ],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof useAgentFacts>);

    vi.mocked(usePolicies).mockReturnValue({
      data: [
        {
          id: 'policy-001',
          tenantId: 'default',
          skillId: 'email-routing',
          name: 'Escalate invoices',
          description: 'Escalate large invoices',
          status: 'PENDING_REVIEW',
          confidenceScore: 0.64,
          episodeCount: 12,
          version: 2,
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
      ],
      isLoading: false,
      isError: false,
      refetch: vi.fn(),
    } as unknown as ReturnType<typeof usePolicies>);
  });

  it('shows a citation-backed summary for tenant-level episodes', () => {
    render(<MemoryExplorerPage />, { wrapper: createAepTestWrapper() });

    expect(screen.getByText('Assist summary')).toBeInTheDocument();
    expect(screen.getByText(/episode records are visible/i)).toBeInTheDocument();
    expect(screen.getByText(/ep-001 · success/i)).toBeInTheDocument();
  });

  it('filters policies by confidence tier', async () => {
    const user = userEvent.setup();
    render(<MemoryExplorerPage />, { wrapper: createAepTestWrapper() });

    await user.click(screen.getByRole('button', { name: 'Policies' }));
    await user.selectOptions(screen.getByLabelText('Confidence'), 'HIGH');

    expect(screen.getByText(/no visible policies/i)).toBeInTheDocument();
  });

  it('requires an agent selection before showing semantic facts', async () => {
    const user = userEvent.setup();
    render(<MemoryExplorerPage />, { wrapper: createAepTestWrapper() });

    await user.click(screen.getByRole('button', { name: 'Facts' }));

    expect(screen.getByText(/select an agent to inspect facts/i)).toBeInTheDocument();
  });
});
