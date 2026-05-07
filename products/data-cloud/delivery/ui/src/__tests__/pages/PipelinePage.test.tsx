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
import { dataFabricMetricsBoundary } from '@/components/common/unsupportedSurfaceRegistry';

const SAMPLE_FABRIC_METRICS = {
    tiers: [
        {
            tier: 'HOT' as const,
            label: 'Redis Cluster',
            throughputEps: 42_000,
            latencyP99Ms: 0.8,
            errorRate: 0.001,
            queueDepth: 0,
            status: 'healthy' as const,
            instanceCount: 3,
        },
        {
            tier: 'WARM' as const,
            label: 'PostgreSQL',
            throughputEps: 8_000,
            latencyP99Ms: 6.5,
            errorRate: 0,
            queueDepth: 120,
            status: 'healthy' as const,
            instanceCount: 2,
            storageGb: 42.7,
        },
        {
            tier: 'COOL' as const,
            label: 'Apache Iceberg',
            throughputEps: 200,
            latencyP99Ms: 85,
            errorRate: 0,
            queueDepth: 0,
            status: 'healthy' as const,
            instanceCount: 1,
            storageGb: 850.3,
        },
        {
            tier: 'COLD' as const,
            label: 'S3 Archive',
            throughputEps: 5,
            latencyP99Ms: 2_000,
            errorRate: 0,
            queueDepth: 0,
            status: 'healthy' as const,
            instanceCount: 1,
            storageGb: 10_200,
        },
    ],
    totalEventsPerSec: 50_205,
    totalStorageGb: 11_093,
    lastUpdated: new Date().toISOString(),
};

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

describe('PipelinePage — DataFabricPage', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        mockApiClient.get.mockResolvedValue(SAMPLE_FABRIC_METRICS);
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
        expect(screen.getByText(dataFabricMetricsBoundary.summary)).toBeInTheDocument();
    });

    it('surfaces the preview boundary and static topology labels', async () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });

        expect(screen.getByText(dataFabricMetricsBoundary.summary)).toBeInTheDocument();
        expect(screen.getByText(/HOT \(Redis\)/i)).toBeInTheDocument();
        expect(screen.getByText(/WARM \(PostgreSQL\)/i)).toBeInTheDocument();
        expect(screen.getByText(/COOL \(Iceberg\)/i)).toBeInTheDocument();
        expect(screen.getByText(/COLD \(S3\/Archive\)/i)).toBeInTheDocument();

        expect(await screen.findByText(/50205\.0/i)).toBeInTheDocument();
        expect(screen.getByText(/events\/sec/i)).toBeInTheDocument();
        expect(screen.getByText(/11093\.0/i)).toBeInTheDocument();
        expect(screen.getByText(/GB/i)).toBeInTheDocument();
    });

    it('keeps governed migration execution disabled until required inputs are provided', async () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });

        const openPanelButton = screen.getByRole('button', { name: /migrate tier/i });
        expect(openPanelButton).toBeEnabled();

        fireEvent.click(openPanelButton);

        const startMigrationButton = await screen.findByRole('button', { name: /start migration/i });

        expect(screen.getByPlaceholderText(/collection \/ stream name/i)).toBeInTheDocument();
        expect(screen.getByPlaceholderText(/reason for migration/i)).toBeInTheDocument();
        expect(startMigrationButton).toBeDisabled();
        expect(mockApiClient.post).not.toHaveBeenCalled();
    });
});
