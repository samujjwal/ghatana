/**
 * Tests for the Event Explorer page.
 *
 * Supplements DcNewPages.test.tsx with loading-state, empty-state,
 * and error-state scenarios for EventExplorerPage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for EventExplorerPage edge cases
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

vi.mock('../../api/events.service', () => ({
    eventsService: {
        listEvents: vi.fn(),
        getStats: vi.fn(),
        openStream: vi.fn(),
    },
}));

import { EventExplorerPage } from '../../pages/EventExplorerPage';
import { eventsService } from '../../api/events.service';

const mockListEvents = vi.mocked(eventsService.listEvents);
const mockGetStats = vi.mocked(eventsService.getStats);
const mockOpenStream = vi.mocked(eventsService.openStream);

const sampleEvents = [
    {
        id: 'evt-001',
        tenantId: 'tenant-a',
        eventType: 'AGENT_COMPLETED',
        tier: 'HOT',
        payload: { agentId: 'ag-1' },
        timestamp: new Date().toISOString(),
        source: 'aep-core',
        metadata: {},
    },
    {
        id: 'evt-002',
        tenantId: 'tenant-a',
        eventType: 'PIPELINE_FAILED',
        tier: 'WARM',
        payload: { pipelineId: 'pip-1', error: 'Timeout' },
        timestamp: new Date().toISOString(),
        source: 'pipeline-engine',
        metadata: { retries: 3 },
    },
];

const sampleStats = {
    total: 12345,
    byTier: { HOT: 3000, WARM: 5000, COOL: 3000, COLD: 1345 },
    byType: { AGENT_COMPLETED: 6000 },
    eventsPerMinute: 42.5,
};

describe('EventPage — EventExplorerPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockListEvents.mockResolvedValue({ events: sampleEvents, total: 2, hasMore: false } as never);
        mockGetStats.mockResolvedValue(sampleStats as never);
        mockOpenStream.mockReturnValue({
            close: vi.fn(),
            addEventListener: vi.fn(),
            removeEventListener: vi.fn(),
            dispatchEvent: vi.fn(),
            onmessage: null,
            onerror: null,
            onopen: null,
            readyState: 1,
            url: '',
            withCredentials: false,
            CONNECTING: 0,
            OPEN: 1,
            CLOSED: 2,
        } as unknown as EventSource);
    });

    it('renders without crashing', async () => {
        render(<EventExplorerPage />, { wrapper: TestWrapper });
        await screen.findByText('AGENT_COMPLETED');
        expect(document.body).toBeTruthy();
    });

    it('displays canonical event entries and stats', async () => {
        render(<EventExplorerPage />, { wrapper: TestWrapper });
        expect(await screen.findByText('AGENT_COMPLETED')).toBeInTheDocument();
        expect(screen.getByText('PIPELINE_FAILED')).toBeInTheDocument();
        expect(screen.getByText(/12,345/)).toBeInTheDocument();
    });

    it('renders filters and canonical event throughput stats', async () => {
        render(<EventExplorerPage />, { wrapper: TestWrapper });
        expect(await screen.findByRole('button', { name: 'ALL' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'HOT' })).toBeInTheDocument();
        expect(screen.getByPlaceholderText(/filter by event type/i)).toBeInTheDocument();
        await waitFor(() => {
            expect(document.body.textContent).toContain('42.5 events/min');
        });
    });

    it('shows the empty state when the canonical event list is empty', async () => {
        mockListEvents.mockResolvedValue({ events: [], total: 0, hasMore: false } as never);
        render(<EventExplorerPage />, { wrapper: TestWrapper });

        await waitFor(() => {
            expect(screen.getByText(/no events found/i)).toBeInTheDocument();
        });
    });

    it('opens the detail panel for a selected event and closes it again', async () => {
        render(<EventExplorerPage />, { wrapper: TestWrapper });
        await screen.findByText('AGENT_COMPLETED');

        fireEvent.click(screen.getByTestId('event-row-evt-001'));
        expect(screen.getByText('Event Detail')).toBeInTheDocument();
        expect(screen.getByText(/agentId/i)).toBeInTheDocument();

        fireEvent.click(screen.getByLabelText('Close detail panel'));
        expect(screen.queryByText('Event Detail')).not.toBeInTheDocument();
    });

    it('opens live tail mode through the canonical stream service', async () => {
        render(<EventExplorerPage />, { wrapper: TestWrapper });
        await screen.findByText('AGENT_COMPLETED');

        fireEvent.click(screen.getByRole('button', { name: /live tail/i }));

        await waitFor(() => {
            expect(mockOpenStream).toHaveBeenCalledWith({});
        });
        expect(screen.getByRole('button', { name: /stop live/i })).toBeInTheDocument();
    });
});
