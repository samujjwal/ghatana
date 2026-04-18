import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

const { mockWorkflowsApi, mockAi, mockCapabilities } = vi.hoisted(() => ({
  mockWorkflowsApi: {
    list: vi.fn(),
  },
  mockAi: {
    getPipelineOptimisationHints: vi.fn(),
    aiQueryKeys: {
      pipelineHints: (pipelineId: string) => ['pipeline-hints', pipelineId],
    },
  },
  mockCapabilities: {
    useCapabilityRegistry: vi.fn(),
  },
}));

vi.mock('../../lib/api/workflows', () => ({
  workflowsApi: mockWorkflowsApi,
}));

vi.mock('../../lib/api/ai', () => ({
  getPipelineOptimisationHints: mockAi.getPipelineOptimisationHints,
  aiQueryKeys: mockAi.aiQueryKeys,
}));

vi.mock('../../api/capabilities.service', () => ({
  useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

import { WorkflowsPage } from '../../pages/WorkflowsPage';

describe('WorkflowsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockWorkflowsApi.list.mockResolvedValue({
      items: [
        {
          id: 'wf-1',
          name: 'Nightly Order Sync',
          description: 'Copies order updates into analytics storage.',
          status: 'active',
          nodes: [],
          edges: [],
          tags: ['nightly'],
          createdAt: '2026-04-01T00:00:00Z',
          updatedAt: '2026-04-17T00:00:00Z',
          createdBy: 'tester',
          lastExecutedAt: '2026-04-17T01:00:00Z',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });
    mockAi.getPipelineOptimisationHints.mockResolvedValue({ data: { hints: [] } });
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-workflows',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'active',
            summary: 'ACTIVE',
            detail: undefined,
            rawValue: 'ACTIVE',
          },
        ],
      },
    });
  });

  it('shows an explicit unavailable state for AI hints when ai assist is not configured', async () => {
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-workflows',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'unavailable',
            summary: 'NOT_CONFIGURED',
            detail: 'AI assist is not configured in this environment.',
            rawValue: 'NOT_CONFIGURED',
          },
        ],
      },
    });

    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Nightly Order Sync')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Nightly Order Sync'));

    expect(await screen.findByText(/AI optimisation hints unavailable/i)).toBeInTheDocument();
    expect(mockAi.getPipelineOptimisationHints).not.toHaveBeenCalled();
  });

  it('shows a degraded warning for AI hints when ai assist is degraded', async () => {
    mockCapabilities.useCapabilityRegistry.mockReturnValue({
      data: {
        generatedAt: '2026-04-17T12:00:00Z',
        requestId: 'req-workflows',
        tenantId: 'tenant-alpha',
        capabilities: [
          {
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'degraded',
            summary: 'DEGRADED',
            detail: 'LLM backing service is temporarily unavailable.',
            rawValue: 'DEGRADED',
          },
        ],
      },
    });

    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Nightly Order Sync')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Nightly Order Sync'));

    expect(await screen.findByText(/AI optimisation hints degraded/i)).toBeInTheDocument();
    expect(screen.getByText(/LLM backing service is temporarily unavailable./i)).toBeInTheDocument();
  });
});