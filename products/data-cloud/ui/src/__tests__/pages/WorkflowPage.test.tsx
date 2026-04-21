/**
 * Tests for the Workflow pages.
 *
 * Supplements RemainingPages.test.tsx with additional interaction
 * scenarios for WorkflowsPage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for WorkflowsPage interactions and states
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

const { mockApiClient } = vi.hoisted(() => ({
    mockApiClient: {
        get: vi.fn(),
        post: vi.fn(),
        delete: vi.fn(),
    },
}));

const { mockAi, mockCapabilities } = vi.hoisted(() => ({
    mockAi: {
        getPipelineOptimisationHints: vi.fn(),
        aiQueryKeys: {
            pipelineHints: (pipelineId: string) => ['ai', 'pipeline-hints', pipelineId],
        },
    },
    mockCapabilities: {
        useCapabilityRegistry: vi.fn(),
        getCapabilitySignal: vi.fn(),
    },
}));

vi.mock('../../lib/api/client', () => ({
    apiClient: mockApiClient,
}));

vi.mock('../../lib/api/ai', () => ({
    getPipelineOptimisationHints: mockAi.getPipelineOptimisationHints,
    aiQueryKeys: mockAi.aiQueryKeys,
}));

vi.mock('../../api/capabilities.service', () => ({
    useCapabilityRegistry: mockCapabilities.useCapabilityRegistry,
    getCapabilitySignal: mockCapabilities.getCapabilitySignal,
}));

import { WorkflowsPage } from '../../pages/WorkflowsPage';

describe('WorkflowPage — WorkflowsPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockApiClient.get.mockResolvedValue({
            tenantId: TEST_TENANT_ID,
            pipelines: [
                {
                    id: 'wf-contract-1',
                    tenantId: TEST_TENANT_ID,
                    name: 'Contract Pipeline',
                    description: 'Canonical launcher-backed workflow payload',
                    status: 'active',
                    nodes: [],
                    edges: [],
                    schedule: '0 0 * * *',
                    tags: ['contract', 'daily', 'sync'],
                    createdAt: '2026-04-10T00:00:00Z',
                    updatedAt: '2026-04-14T00:00:00Z',
                    createdBy: 'contract-runner',
                    lastExecutedAt: '2026-04-14T10:30:00Z',
                },
            ],
            count: 1,
            timestamp: '2026-04-14T10:35:00Z',
        });
        mockAi.getPipelineOptimisationHints.mockResolvedValue({
            data: {
                pipelineId: 'wf-contract-1',
                hints: [
                    {
                        type: 'performance',
                        title: 'Reduce repeated enrichment lookups',
                        description: 'Cache repeated upstream calls to reduce end-to-end latency.',
                        confidence: 0.92,
                        impact: 'high',
                        fallback: false,
                    },
                ],
                generatedAt: '2026-04-14T10:35:00Z',
            },
        });
        mockApiClient.delete.mockResolvedValue(undefined);
        mockCapabilities.useCapabilityRegistry.mockReturnValue({
            data: {
                capabilities: [{ key: 'ai.assist', status: 'active', label: 'AI Assist', summary: 'ACTIVE', rawValue: true }],
            },
        });
        mockCapabilities.getCapabilitySignal.mockReturnValue({
            key: 'ai.assist',
            label: 'AI Assist',
            status: 'active',
            summary: 'ACTIVE',
            rawValue: true,
        });
    });

    it('renders the workflows shell with canonical search and filter controls', () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });
        expect(screen.getByRole('heading', { name: 'Workflows' })).toBeInTheDocument();
        expect(screen.getByPlaceholderText(/Search workflows by outcome, schedule, or owner/i)).toBeInTheDocument();
        expect(screen.getByText('All Workflows')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: /New Pipeline/i })).toBeInTheDocument();
    });

    it('renders canonical pipeline payload details from the launcher route', async () => {
        render(<WorkflowsPage />, { wrapper: TestWrapper });

        expect(await screen.findByText('Contract Pipeline')).toBeInTheDocument();
        expect(screen.getByText('Canonical launcher-backed workflow payload')).toBeInTheDocument();
        expect(screen.getByText('contract')).toBeInTheDocument();
        expect(screen.getByText('daily')).toBeInTheDocument();
        expect(screen.getByText('Total')).toBeInTheDocument();
        expect(screen.getAllByText('Active').length).toBeGreaterThan(0);

        await waitFor(() => {
            expect(mockApiClient.get).toHaveBeenCalledWith('/pipelines', {
                params: { limit: 50 },
            });
        });
    });

    it('opens workflow details and shows canonical AI optimisation hints', async () => {
        const user = userEvent.setup();
        render(<WorkflowsPage />, { wrapper: TestWrapper });

        const workflowName = await screen.findByText('Contract Pipeline');
        fireEvent.click(workflowName.closest('[data-testid="workflow-item"]') ?? workflowName);
        await user.click(await screen.findByRole('button', { name: /show pipeline details/i }));

        expect(await screen.findByText('Inline AI Recommendations')).toBeInTheDocument();
        expect(screen.getByText('Reduce repeated enrichment lookups')).toBeInTheDocument();
        expect(screen.getByText(/cache repeated upstream calls/i)).toBeInTheDocument();

        await waitFor(() => {
            expect(mockAi.getPipelineOptimisationHints).toHaveBeenCalledWith('wf-contract-1');
        });
    });
});
