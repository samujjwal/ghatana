/**
 * useFullStackMode Tests
 * 
 * Tests for full-stack split-screen mode hook
 * 
 * @doc.type test
 * @doc.purpose useFullStackMode hook tests
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useFullStackMode } from '../useFullStackMode';
import * as ReactFlow from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';

// Mock React Flow hooks
vi.mock('@xyflow/react', () => ({
    useNodes: vi.fn(),
    useEdges: vi.fn(),
    useReactFlow: vi.fn(),
}));

describe('useFullStackMode', () => {
    let mockNodes: Node[];
    let mockEdges: Edge[];
    let mockSetNodes: ReturnType<typeof vi.fn>;
    let mockSetEdges: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        mockNodes = [];
        mockEdges = [];
        mockSetNodes = vi.fn();
        mockSetEdges = vi.fn();

        vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
        vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);
        vi.mocked(ReactFlow.useReactFlow).mockReturnValue({
            setNodes: mockSetNodes,
            setEdges: mockSetEdges,
            setViewport: vi.fn(),
            getViewport: vi.fn(() => ({ x: 0, y: 0, zoom: 1 })),
        } as unknown);
    });

    describe('Initialization', () => {
        it('should initialize with default single mode', () => {
            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.mode).toBe('single');
            expect(result.current.isSplit).toBe(false);
        });

        it('should initialize with custom mode', () => {
            const { result } = renderHook(() =>
                useFullStackMode({ initialMode: 'split-vertical' })
            );

            expect(result.current.mode).toBe('split-vertical');
            expect(result.current.isSplit).toBe(true);
        });

        it('should initialize active side as frontend', () => {
            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.activeSide).toBe('frontend');
        });
    });

    describe('Mode Management', () => {
        it('should toggle between single and split modes', () => {
            const { result } = renderHook(() => useFullStackMode());

            act(() => {
                result.current.toggle();
            });

            expect(result.current.mode).toBe('split-vertical');
            expect(result.current.isSplit).toBe(true);

            act(() => {
                result.current.toggle();
            });

            expect(result.current.mode).toBe('single');
            expect(result.current.isSplit).toBe(false);
        });

        it('should set specific mode', () => {
            const { result } = renderHook(() => useFullStackMode());

            act(() => {
                result.current.setMode('split-horizontal');
            });

            expect(result.current.mode).toBe('split-horizontal');
        });

        it('should set active side', () => {
            const { result } = renderHook(() => useFullStackMode());

            act(() => {
                result.current.setActiveSide('backend');
            });

            expect(result.current.activeSide).toBe('backend');
        });
    });

    describe('Node Partitioning', () => {
        beforeEach(() => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'ui',
                    position: { x: 100, y: 100 },
                    data: { label: 'Login Page' },
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 1000, y: 100 },
                    data: { label: 'Auth API' },
                },
                {
                    id: 'component-1',
                    type: 'component',
                    position: { x: 200, y: 200 },
                    data: { label: 'Button' },
                },
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 1100, y: 200 },
                    data: { label: 'User Service' },
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
        });

        it('should partition nodes by type', () => {
            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.frontendPartition.nodes).toHaveLength(2);
            expect(result.current.backendPartition.nodes).toHaveLength(2);

            expect(result.current.frontendPartition.nodes[0].id).toBe('ui-1');
            expect(result.current.frontendPartition.nodes[1].id).toBe('component-1');

            expect(result.current.backendPartition.nodes[0].id).toBe('api-1');
            expect(result.current.backendPartition.nodes[1].id).toBe('service-1');
        });

        it('should calculate partition bounds', () => {
            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.frontendPartition.bounds).toBeDefined();
            expect(result.current.frontendPartition.bounds.x).toBe(100);
            expect(result.current.frontendPartition.bounds.y).toBe(100);

            expect(result.current.backendPartition.bounds).toBeDefined();
            expect(result.current.backendPartition.bounds.x).toBe(1000);
        });

        it('should handle empty partitions', () => {
            mockNodes = [];
            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);

            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.frontendPartition.nodes).toHaveLength(0);
            expect(result.current.backendPartition.nodes).toHaveLength(0);
            expect(result.current.frontendPartition.bounds.width).toBe(0);
        });
    });

    describe('Data Flow Edges', () => {
        beforeEach(() => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'ui',
                    position: { x: 100, y: 100 },
                    data: {},
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 1000, y: 100 },
                    data: {},
                },
            ];

            mockEdges = [
                {
                    id: 'edge-1',
                    source: 'ui-1',
                    target: 'api-1',
                    type: 'default',
                },
                {
                    id: 'edge-2',
                    source: 'api-1',
                    target: 'ui-1',
                    type: 'default',
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);
        });

        it('should identify cross-canvas data flow edges', () => {
            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.dataFlowEdges).toHaveLength(2);
        });

        it('should set flow direction for data flow edges', () => {
            const { result } = renderHook(() => useFullStackMode());

            const frontendToBackend = result.current.dataFlowEdges.find(
                (e) => e.source === 'ui-1' && e.target === 'api-1'
            );
            expect(frontendToBackend?.data?.flowDirection).toBe('frontend-to-backend');

            const backendToFrontend = result.current.dataFlowEdges.find(
                (e) => e.source === 'api-1' && e.target === 'ui-1'
            );
            expect(backendToFrontend?.data?.flowDirection).toBe('backend-to-frontend');
        });

        it('should not include same-canvas edges', () => {
            mockEdges.push({
                id: 'edge-3',
                source: 'ui-1',
                target: 'ui-1',
                type: 'default',
            });

            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);

            const { result } = renderHook(() => useFullStackMode());

            expect(result.current.dataFlowEdges).toHaveLength(2);
        });
    });

    describe('Validation', () => {
        it('should validate successfully with proper connections', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'ui',
                    position: { x: 0, y: 0 },
                    data: { isApiCall: true, label: 'API Call' },
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: { label: 'Endpoint' },
                },
            ];

            mockEdges = [
                {
                    id: 'edge-1',
                    source: 'ui-1',
                    target: 'api-1',
                    type: 'default',
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);

            const { result } = renderHook(() => useFullStackMode({ autoValidate: true }));

            expect(result.current.validation.valid).toBe(true);
            expect(result.current.validation.errors).toHaveLength(0);
        });

        it('should detect missing backend endpoints', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'apiCall',
                    position: { x: 0, y: 0 },
                    data: { label: 'API Call' },
                },
            ];

            mockEdges = [];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);

            const { result } = renderHook(() => useFullStackMode({ autoValidate: true }));

            expect(result.current.validation.valid).toBe(false);
            expect(result.current.validation.errors).toHaveLength(1);
            expect(result.current.validation.errors[0].type).toBe('missing-backend');
        });

        it('should warn about unused backend APIs', () => {
            mockNodes = [
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: { label: 'Unused API' },
                },
            ];

            mockEdges = [];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);

            const { result } = renderHook(() => useFullStackMode({ autoValidate: true }));

            expect(result.current.validation.warnings).toHaveLength(1);
            expect(result.current.validation.warnings[0].message).toContain('no frontend consumer');
        });

        it('should detect type mismatches', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'ui',
                    position: { x: 0, y: 0 },
                    data: { responseType: 'string' },
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: { requestType: 'number' },
                },
            ];

            mockEdges = [
                {
                    id: 'edge-1',
                    source: 'ui-1',
                    target: 'api-1',
                    type: 'default',
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
            vi.mocked(ReactFlow.useEdges).mockReturnValue(mockEdges);

            const { result } = renderHook(() => useFullStackMode({ autoValidate: true }));

            expect(result.current.validation.errors.some((e) => e.type === 'type-mismatch')).toBe(true);
        });
    });

    describe('Node Operations', () => {
        beforeEach(() => {
            mockNodes = [
                {
                    id: 'node-1',
                    type: 'ui',
                    position: { x: 100, y: 100 },
                    data: {},
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);
        });

        it('should get node side', () => {
            const { result } = renderHook(() => useFullStackMode());

            const side = result.current.getNodeSide('node-1');
            expect(side).toBe('frontend');
        });

        it('should return null for non-existent node', () => {
            const { result } = renderHook(() => useFullStackMode());

            const side = result.current.getNodeSide('non-existent');
            expect(side).toBeNull();
        });

        it('should move node to backend canvas', () => {
            const { result } = renderHook(() => useFullStackMode());

            act(() => {
                result.current.moveNodeToCanvas('node-1', 'backend');
            });

            expect(mockSetNodes).toHaveBeenCalled();
        });

        it('should create data flow edge', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'ui',
                    position: { x: 0, y: 0 },
                    data: {},
                },
                {
                    id: 'api-1',
                    type: 'api',
                    position: { x: 0, y: 0 },
                    data: {},
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);

            const { result } = renderHook(() => useFullStackMode());

            act(() => {
                result.current.createDataFlow('ui-1', 'api-1', 'User');
            });

            expect(mockSetEdges).toHaveBeenCalled();
            const callArg = mockSetEdges.mock.calls[0][0];
            expect(callArg).toHaveLength(1);
            expect(callArg[0].data.dataType).toBe('User');
        });
    });

    describe('Auto-validation', () => {
        it('should auto-validate when enabled', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'apiCall',
                    position: { x: 0, y: 0 },
                    data: {},
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);

            const { result } = renderHook(() =>
                useFullStackMode({ autoValidate: true, initialMode: 'split-vertical' })
            );

            expect(result.current.validation).toBeDefined();
            expect(result.current.validation.valid).toBe(false);
        });

        it('should not auto-validate when disabled', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'apiCall',
                    position: { x: 0, y: 0 },
                    data: {},
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);

            const { result } = renderHook(() =>
                useFullStackMode({ autoValidate: false, initialMode: 'split-vertical' })
            );

            expect(result.current.validation.valid).toBe(true);
        });

        it('should not validate in single mode', () => {
            mockNodes = [
                {
                    id: 'ui-1',
                    type: 'apiCall',
                    position: { x: 0, y: 0 },
                    data: {},
                },
            ];

            vi.mocked(ReactFlow.useNodes).mockReturnValue(mockNodes);

            const { result } = renderHook(() =>
                useFullStackMode({ autoValidate: true, initialMode: 'single' })
            );

            expect(result.current.validation.valid).toBe(true);
        });
    });
});
