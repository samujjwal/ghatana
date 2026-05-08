import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import {
  WORKFLOW_HINTS_DEGRADED_DETAIL,
  WORKFLOW_HINTS_DEGRADED_TITLE,
  WORKFLOW_HINTS_UNAVAILABLE_DETAIL,
  WORKFLOW_HINTS_UNAVAILABLE_TITLE,
} from '@/lib/runtime-boundaries';

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

vi.mock('../../api/surfaces.service', () => ({
  useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
  getCapabilitySignal: (capabilities: Array<{ key: string }> | undefined, aliases: string[]) =>
    capabilities?.find((capability) => aliases.includes(capability.key)),
}));

import { WorkflowsPage } from '../../pages/WorkflowsPage';

describe('WorkflowsPage', () => {
  beforeEach(() => {
    // Guard against timer mode leaking from other test files in the same worker.
    vi.useRealTimers();
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
            detail: WORKFLOW_HINTS_UNAVAILABLE_DETAIL,
            rawValue: 'NOT_CONFIGURED',
          },
        ],
      },
    });

    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Nightly Order Sync')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Nightly Order Sync'));
    fireEvent.click(screen.getByText(/Show pipeline details/i));

    expect(await screen.findByText(WORKFLOW_HINTS_UNAVAILABLE_TITLE)).toBeInTheDocument();
    expect(mockAi.getPipelineOptimisationHints).not.toHaveBeenCalled();
  }, 15000);

  it('renders the calmer outcome-first pipeline list with next-action messaging', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Nightly Order Sync')).toBeInTheDocument();
    expect(screen.getByText(/Keep the list about outcomes, not pipeline internals/i)).toBeInTheDocument();
    expect(screen.getByText(/Check the latest outcome/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Review pipeline/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Advanced editor/i })).toBeInTheDocument();
  }, 15000);

  it('keeps advanced pipeline details behind progressive disclosure in the review modal', async () => {
    render(<WorkflowsPage />, { wrapper: TestWrapper });

    fireEvent.click(await screen.findByText('Nightly Order Sync'));

    expect(screen.getByText(/Show pipeline details/i)).toBeInTheDocument();
    expect(screen.queryByText(/Inline AI Recommendations/i)).not.toBeInTheDocument();

    fireEvent.click(screen.getByText(/Show pipeline details/i));

    expect(screen.getAllByText(/Flow size/i).length).toBeGreaterThan(1);
    expect(screen.getAllByText(/Owner/i).length).toBeGreaterThan(1);
  }, 15000);

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
            detail: WORKFLOW_HINTS_DEGRADED_DETAIL,
            rawValue: 'DEGRADED',
          },
        ],
      },
    });

    render(<WorkflowsPage />, { wrapper: TestWrapper });

    expect(await screen.findByText('Nightly Order Sync')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Nightly Order Sync'));
    fireEvent.click(screen.getByText(/Show pipeline details/i));

    expect(await screen.findByText(WORKFLOW_HINTS_DEGRADED_TITLE)).toBeInTheDocument();
    expect(screen.getByText(WORKFLOW_HINTS_DEGRADED_DETAIL)).toBeInTheDocument();
  }, 15000);

    it('paginates the workflow list when there are more than 20 items', async () => {
      // Create 25 workflows — enough to spill into a second page with PAGE_SIZE=20
      const manyWorkflows = Array.from({ length: 25 }, (_, i) => ({
        id: `wf-page-${i + 1}`,
        name: `Pipeline ${i + 1}`,
        description: `Auto-generated pipeline ${i + 1} for pagination test.`,
        status: 'active',
        nodes: [],
        edges: [],
        tags: [],
        createdAt: '2026-04-01T00:00:00Z',
        updatedAt: '2026-04-17T00:00:00Z',
        createdBy: 'tester',
        lastExecutedAt: null,
      }));

      mockWorkflowsApi.list.mockResolvedValue({
        items: manyWorkflows,
        total: 25,
        page: 1,
        pageSize: 50,
        hasMore: false,
      });

      render(<WorkflowsPage />, { wrapper: TestWrapper });

      // Page 1: first 20 items visible
      expect(await screen.findByText('Pipeline 1')).toBeInTheDocument();
      expect(screen.getByText('Pipeline 20')).toBeInTheDocument();
      expect(screen.queryByText('Pipeline 21')).not.toBeInTheDocument();

      // Pagination controls present
      const nextButton = screen.getByRole('button', { name: /^next$/i });
      const prevButton = screen.getByRole('button', { name: /^previous$/i });
      expect(prevButton).toBeDisabled();
      expect(nextButton).not.toBeDisabled();

      // Navigate to page 2
      fireEvent.click(nextButton);

      // Page 2: items 21-25 visible, first 20 gone
      expect(await screen.findByText('Pipeline 21')).toBeInTheDocument();
      expect(screen.getByText('Pipeline 25')).toBeInTheDocument();
      expect(screen.queryByText('Pipeline 1')).not.toBeInTheDocument();

      // Previous button re-enabled; next disabled on final page
      expect(screen.getByRole('button', { name: /^previous$/i })).not.toBeDisabled();
      expect(screen.getByRole('button', { name: /^next$/i })).toBeDisabled();

      // Navigate back to page 1
      fireEvent.click(screen.getByRole('button', { name: /^previous$/i }));

      expect(await screen.findByText('Pipeline 1')).toBeInTheDocument();
      expect(screen.queryByText('Pipeline 21')).not.toBeInTheDocument();
    }, 15000);
});