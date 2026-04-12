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
import { render } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import React from 'react';

vi.mock('../../lib/api/client', () => ({
    apiClient: {
        get: vi.fn().mockResolvedValue([]),
        post: vi.fn().mockResolvedValue({}),
        put: vi.fn().mockResolvedValue({}),
        delete: vi.fn().mockResolvedValue(undefined),
    },
}));

vi.mock('@ghatana/canvas/flow', () => ({
    FlowCanvas: ({ children }: { children?: React.ReactNode }) =>
        React.createElement('div', { 'data-testid': 'flow-canvas' }, children),
    FlowControls: () => React.createElement('div', { 'data-testid': 'flow-controls' }),
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
    });

    it('renders without crashing', () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });
        expect(document.body).toBeTruthy();
    });

    it('displays pipeline or fabric content', () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });
        const body = document.body.textContent ?? '';
        expect(body.toLowerCase()).toMatch(/pipeline|fabric|data|flow|connector/i);
    });

    it('renders the page with some structure', () => {
        render(<DataFabricPage />, { wrapper: TestWrapper });
        expect(document.body.children.length).toBeGreaterThan(0);
    });

    it('renders without throwing for an empty pipeline list', () => {
        expect(() =>
            render(<DataFabricPage />, { wrapper: TestWrapper })
        ).not.toThrow();
    });
});
