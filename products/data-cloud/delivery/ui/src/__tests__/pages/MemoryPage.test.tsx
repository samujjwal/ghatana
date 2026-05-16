/**
 * Tests for the MemoryPlaneViewerPage.
 *
 * Supplements DcNewPages.test.tsx with specific memory plane
 * display and interaction scenarios.
 *
 * @doc.type test
 * @doc.purpose RTL tests for MemoryPlaneViewerPage
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

vi.mock('../../api/memory.service', () => ({
    memoryService: {
        listMemoryItems: vi.fn(),
        deleteMemoryItem: vi.fn(),
        getConsolidationStatus: vi.fn(),
    },
}));

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
    },
}));

import { MemoryPlaneViewerPage } from '../../pages/MemoryPlaneViewerPage';
import { memoryService } from '../../api/memory.service';

const mockListMemoryItems = vi.mocked(memoryService.listMemoryItems);
const mockDeleteMemoryItem = vi.mocked(memoryService.deleteMemoryItem);
const mockGetConsolidationStatus = vi.mocked(memoryService.getConsolidationStatus);

const sampleItems = [
  {
    id: 'mem-001',
    tenantId: TEST_TENANT_ID,
    agentId: 'agent-123',
    type: 'EPISODIC' as const,
    content: 'User asked about refund policy',
    tags: ['refund', 'policy'],
    salience: 0.85,
    createdAt: '2026-04-14T12:00:00Z',
    metadata: { source: 'conversation', region: 'us-east' },
  },
  {
    id: 'mem-002',
    tenantId: TEST_TENANT_ID,
    agentId: 'agent-456',
    type: 'EPISODIC' as const,
    content: 'Failed to process order 12345',
    tags: ['order', 'failure'],
    salience: 0.62,
    createdAt: '2026-04-14T12:10:00Z',
    metadata: {},
  },
];

describe('MemoryPage — MemoryPlaneViewerPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockListMemoryItems.mockResolvedValue(sampleItems as never);
        mockDeleteMemoryItem.mockResolvedValue(undefined as never);
        mockGetConsolidationStatus.mockResolvedValue({
            lastRun: '2026-04-14T12:00:00Z',
            episodesProcessed: 24,
            policiesExtracted: 3,
        });
    });

    it('renders consolidation summary and canonical memory items', async () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        expect(await screen.findByText('User asked about refund policy')).toBeInTheDocument();
        expect(screen.getByText('Failed to process order 12345')).toBeInTheDocument();
        expect(screen.getByText(/Episodes processed:/i)).toBeInTheDocument();
        expect(screen.getByText('24')).toBeInTheDocument();
        expect(screen.getByText('3')).toBeInTheDocument();
    });

    it('passes active type and agent filter through the canonical memory service query', async () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        await screen.findByText('User asked about refund policy');
        expect(mockListMemoryItems).toHaveBeenCalledWith({
            type: 'EPISODIC',
            agentId: undefined,
            query: undefined,
        });

        fireEvent.change(screen.getByLabelText('Filter by agent'), { target: { value: 'agent-123' } });

        await waitFor(() => {
            expect(mockListMemoryItems).toHaveBeenLastCalledWith({
                type: 'EPISODIC',
                agentId: 'agent-123',
                query: undefined,
            });
        });
    });

    it('filters client-side search results and shows an honest empty state when nothing matches', async () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        await screen.findByText('User asked about refund policy');
        fireEvent.change(screen.getByLabelText('Search memory items'), { target: { value: 'zzznomatch' } });

        expect(screen.getByText(/No episodic memory items found/i)).toBeInTheDocument();
        expect(screen.getByText(/Try clearing the search filter/i)).toBeInTheDocument();
        expect(screen.getByText(/0 items/i)).toBeInTheDocument();
    });

    it('toggles metadata visibility for a memory card', async () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        await screen.findByText('User asked about refund policy');
        fireEvent.click(screen.getAllByRole('button', { name: /show metadata/i })[0]);

        expect(screen.getByText(/"source": "conversation"/i)).toBeInTheDocument();
        expect(screen.getByText(/"region": "us-east"/i)).toBeInTheDocument();
    });

    it('deletes memory items through the canonical launcher boundary', async () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        await screen.findByText('User asked about refund policy');
        fireEvent.click(screen.getAllByLabelText('Delete memory item')[0]);

        await waitFor(() => {
            expect(mockDeleteMemoryItem).toHaveBeenCalledWith('agent-123', 'mem-001');
        });
    });
});
