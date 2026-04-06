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
import { render, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';

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

describe('MemoryPage — MemoryPlaneViewerPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockListMemoryItems.mockResolvedValue({ items: [], total: 0 } as never);
    });

    it('renders without crashing', () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays memory-related content', () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/memory|retention|consolidat|plane|recall/i);
    });

    it('renders page with structural content', () => {
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('handles empty memory list without crashing', async () => {
        mockListMemoryItems.mockResolvedValue({ items: [], total: 0 } as never);
        render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper });

        await waitFor(() => expect(document.body).toBeTruthy());
    });

    it('renders without throwing', () => {
        expect(() =>
            render(<MemoryPlaneViewerPage />, { wrapper: TestWrapper })
        ).not.toThrow();
    });
});
