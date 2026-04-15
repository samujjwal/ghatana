/**
 * Tests for the Pipeline / DataFabric page.
 *
 * Supplements DcNewPages.test.tsx with pipeline-specific scenarios for DataFabricPage.
 *
 * @doc.type test
 * @doc.purpose RTL tests for DataFabricPage pipeline scenarios
 * @doc.layer frontend
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import React from 'react';

const { mockApiClient } = vi.hoisted(() => ({
    mockApiClient: {
        get: vi.fn(),
        post: vi.fn(),
        put: vi.fn(),
        delete: vi.fn(),
    },
}));

vi.mock('../../lib/api/client', () => ({
    apiClient: mockApiClient,
}));

vi.mock('@ghatana/canvas/flow', () => ({
    FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
        React.createElement('div', { 'data-testid': 'flow-canvas' }, children),
    FlowControls: () => React.createElement('div', { 'data-testid': 'flow-controls' }),
    MarkerType: { ArrowClosed: 'arrowclosed' },
    useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    addEdge: vi.fn((conn: unknown, eds: unknown) => eds),
    Background: () => React.createElement('div', { 'data-testid': 'flow-background' }),
    Controls: () => React.createElement('div', { 'data-testid': 'flow-controls' }),
}));

vi.mock('reactflow', async () => ({
    ...(await vi.importActual('reactflow').catch(() => ({}))),
    ReactFlow: ({ children }: { children?: React.ReactNode }) =>
        React.createElement('div', { 'data-testid': 'react-flow' }, children),
    useNodesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    useEdgesState: (initial: unknown[]) => [initial, vi.fn(), vi.fn()],
    addEdge: vi.fn((conn: unknown, eds: unknown) => eds),
    Background: () => React.createElement('div'),
    Controls: () => React.createElement('div'),
}));

import { DataFabricPage } from '../../pages/DataFabricPage';
import { FABRIC_METRICS_BOUNDARY_MESSAGE } from '../../pages/DataFabricPage';

describe('PipelinePage — DataFabricPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockApiClient.get.mockResolvedValue([]);
        mockApiClient.post.mockResolvedValue({
            collection: 'orders',
            targetTier: 'WARM',
            status: 'SCHEDULED',
            eventsMigrated: 1250,
        });
        mockApiClient.put.mockResolvedValue({});
        mockApiClient.delete.mockResolvedValue(undefined);
    });

    it('renders the data-fabric shell with preview messaging and migration controls', () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });
        expect(screen.getByRole('heading', { name: /Data Fabric/i })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: /Migrate Tier/i })).toBeInTheDocument();
        expect(screen.getByText(FABRIC_METRICS_BOUNDARY_MESSAGE)).toBeInTheDocument();
    });

    it('surfaces the preview boundary and static topology labels', () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });

        expect(screen.getByText(FABRIC_METRICS_BOUNDARY_MESSAGE)).toBeInTheDocument();
        expect(screen.getByText(/HOT \(Redis\)/i)).toBeInTheDocument();
        expect(screen.getByText(/WARM \(PostgreSQL\)/i)).toBeInTheDocument();
        expect(screen.getByText(/COOL \(Iceberg\)/i)).toBeInTheDocument();
        expect(screen.getByText(/COLD \(S3\/Archive\)/i)).toBeInTheDocument();
        expect(screen.getByText(/Total throughput:/i)).toBeInTheDocument();
        expect(screen.getByText(/Total storage:/i)).toBeInTheDocument();
    });

    it('submits a manual migration request through the canonical launcher route', async () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });

        fireEvent.click(screen.getByRole('button', { name: /migrate tier/i }));
        fireEvent.change(screen.getByPlaceholderText(/collection \/ stream name/i), {
            target: { value: 'orders' },
        });
        fireEvent.change(screen.getByRole('combobox'), {
            target: { value: 'WARM' },
        });
        fireEvent.click(screen.getByRole('button', { name: /start migration/i }));

        await waitFor(() => {
            expect(mockApiClient.post).toHaveBeenCalledWith(
                '/collections/orders/migrate',
                {},
                { params: { targetTier: 'WARM' } },
            );
        });

        await waitFor(() => {
            expect(screen.queryByPlaceholderText(/collection \/ stream name/i)).not.toBeInTheDocument();
        });
    });
});
