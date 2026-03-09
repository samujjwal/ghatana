/**
 * Tests for useServiceHealth hook
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useServiceHealth } from '../useServiceHealth';
import type { Node, Edge } from '@xyflow/react';

// Mock React Flow
const mockSetNodes = vi.fn();
const mockGetNodes = vi.fn();
const mockGetEdges = vi.fn();

vi.mock('@xyflow/react', () => ({
    useReactFlow: () => ({
        setNodes: mockSetNodes,
        getNodes: mockGetNodes,
        getEdges: mockGetEdges,
    }),
}));

describe('useServiceHealth', () => {
    beforeEach(() => {
        vi.clearAllMocks();
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    describe('Initialization', () => {
        it('should initialize with empty health data', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() => useServiceHealth());

            expect(result.current.healthData.size).toBe(0);
            expect(result.current.overallHealth).toBe('unknown');
            expect(result.current.alerts).toEqual([]);
            expect(result.current.isRefreshing).toBe(false);
        });

        it('should initialize with custom options', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({
                    autoRefresh: false,
                    enableAlerts: false,
                    refreshInterval: 60000,
                })
            );

            expect(result.current.healthData.size).toBe(0);
        });
    });

    describe('Metrics Refresh', () => {
        it('should refresh metrics for all service nodes', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Auth Service' },
                },
                {
                    id: 'service-2',
                    type: 'api',
                    position: { x: 100, y: 0 },
                    data: { label: 'User API' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(2);
            });

            expect(result.current.healthData.has('service-1')).toBe(true);
            expect(result.current.healthData.has('service-2')).toBe(true);
        });

        it('should refresh metrics for a single node', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Auth Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics('service-1');
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(1);
            });
        });

        it('should set isRefreshing during refresh', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            const refreshPromise = act(async () => {
                await result.current.refreshMetrics();
            });

            // Check that isRefreshing is true during refresh
            expect(result.current.isRefreshing).toBe(true);

            await refreshPromise;

            await waitFor(() => {
                expect(result.current.isRefreshing).toBe(false);
            });
        });

        it('should update lastRefresh timestamp', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            expect(result.current.lastRefresh).toBeNull();

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.lastRefresh).not.toBeNull();
            });
        });
    });

    describe('Health Status Calculation', () => {
        it('should calculate overall health as healthy', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            // Mock low metric values (healthy)
            vi.spyOn(Math, 'random').mockReturnValue(0.1);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.overallHealth).toBe('healthy');
            });

            vi.restoreAllMocks();
        });

        it('should calculate overall health as degraded', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: false })
            );

            // Mock medium metric values (degraded)
            vi.spyOn(Math, 'random').mockReturnValue(0.7);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.overallHealth).toBe('degraded');
            });

            vi.restoreAllMocks();
        });

        it('should calculate overall health as critical', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: false })
            );

            // Mock high metric values (critical)
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.overallHealth).toBe('critical');
            });

            vi.restoreAllMocks();
        });
    });

    describe('Node Health Retrieval', () => {
        it('should get health data for specific node', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Auth Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                const health = result.current.getNodeHealth('service-1');
                expect(health).not.toBeNull();
                expect(health?.serviceName).toBe('Auth Service');
            });
        });

        it('should return null for non-existent node', async () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            const health = result.current.getNodeHealth('non-existent');
            expect(health).toBeNull();
        });
    });

    describe('Alert Management', () => {
        it('should generate alerts for critical metrics', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: true })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.alerts.length).toBeGreaterThan(0);
            });

            vi.restoreAllMocks();
        });

        it('should acknowledge alerts', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: true })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.alerts.length).toBeGreaterThan(0);
            });

            const firstAlert = result.current.alerts[0];
            expect(firstAlert.acknowledged).toBe(false);

            act(() => {
                result.current.acknowledgeAlert(firstAlert.id);
            });

            const acknowledgedAlert = result.current.alerts.find(a => a.id === firstAlert.id);
            expect(acknowledgedAlert?.acknowledged).toBe(true);

            vi.restoreAllMocks();
        });

        it('should clear alerts', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: true })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.alerts.length).toBeGreaterThan(0);
            });

            const initialCount = result.current.alerts.length;
            const firstAlert = result.current.alerts[0];

            act(() => {
                result.current.clearAlert(firstAlert.id);
            });

            expect(result.current.alerts.length).toBe(initialCount - 1);

            vi.restoreAllMocks();
        });

        it('should count unacknowledged alerts', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: true })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.unacknowledgedCount).toBeGreaterThan(0);
            });

            const initialCount = result.current.unacknowledgedCount;
            const firstAlert = result.current.alerts[0];

            act(() => {
                result.current.acknowledgeAlert(firstAlert.id);
            });

            expect(result.current.unacknowledgedCount).toBe(initialCount - 1);

            vi.restoreAllMocks();
        });
    });

    describe('SLO Management', () => {
        it('should add SLO', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            act(() => {
                result.current.addSLO({
                    name: 'API Latency',
                    target: 99.9,
                    metricType: 'latency',
                    timeWindow: '24h',
                });
            });

            expect(result.current.slos.length).toBe(1);
            expect(result.current.slos[0].name).toBe('API Latency');
            expect(result.current.slos[0].target).toBe(99.9);
        });

        it('should remove SLO', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            act(() => {
                result.current.addSLO({
                    name: 'API Latency',
                    target: 99.9,
                    metricType: 'latency',
                    timeWindow: '24h',
                });
            });

            const sloId = result.current.slos[0].id;

            act(() => {
                result.current.removeSLO(sloId);
            });

            expect(result.current.slos.length).toBe(0);
        });
    });

    describe('Circuit Breaker Management', () => {
        it('should enable circuit breaker', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            act(() => {
                result.current.enableCircuitBreaker('service-1', {
                    threshold: 5,
                    timeout: 30,
                    fallbackStrategy: 'cache',
                });
            });

            expect(result.current.circuitBreakers.has('service-1')).toBe(true);
            expect(result.current.circuitBreakers.get('service-1')?.enabled).toBe(true);
            expect(result.current.circuitBreakers.get('service-1')?.threshold).toBe(5);
        });

        it('should disable circuit breaker', () => {
            mockGetNodes.mockReturnValue([]);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            act(() => {
                result.current.enableCircuitBreaker('service-1', {
                    threshold: 5,
                    timeout: 30,
                    fallbackStrategy: 'cache',
                });
            });

            expect(result.current.circuitBreakers.has('service-1')).toBe(true);

            act(() => {
                result.current.disableCircuitBreaker('service-1');
            });

            expect(result.current.circuitBreakers.has('service-1')).toBe(false);
        });
    });

    describe('Visualization', () => {
        it('should color nodes by health status', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(1);
            });

            act(() => {
                result.current.colorNodesByHealth();
            });

            expect(mockSetNodes).toHaveBeenCalled();
        });

        it('should highlight unhealthy path', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service 1' },
                },
                {
                    id: 'service-2',
                    type: 'service',
                    position: { x: 100, y: 0 },
                    data: { label: 'Service 2' },
                },
            ];

            const mockEdges: Edge[] = [
                { id: 'e1', source: 'service-1', target: 'service-2' },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue(mockEdges);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: false })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(2);
            });

            act(() => {
                result.current.highlightUnhealthyPath('service-1');
            });

            expect(mockSetNodes).toHaveBeenCalled();

            vi.restoreAllMocks();
        });
    });

    describe('Incident Management', () => {
        it('should create incident report', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Auth Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(1);
            });

            let report = '';
            await act(async () => {
                report = await result.current.createIncidentReport('service-1');
            });

            expect(report).toContain('Incident Report');
            expect(report).toContain('Auth Service');
            expect(report).toContain('Metrics at Incident');
        });

        it('should get incident timeline', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'Service' },
                },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue([]);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false, enableAlerts: false })
            );

            // Mock high metric values
            vi.spyOn(Math, 'random').mockReturnValue(0.95);

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                expect(result.current.healthData.size).toBe(1);
            });

            const timeline = result.current.getIncidentTimeline('service-1');
            expect(timeline.length).toBeGreaterThan(0);
            expect(timeline[0]).toHaveProperty('timestamp');
            expect(timeline[0]).toHaveProperty('event');

            vi.restoreAllMocks();
        });
    });

    describe('Dependencies', () => {
        it('should track service dependencies', async () => {
            const mockNodes: Node[] = [
                {
                    id: 'service-1',
                    type: 'service',
                    position: { x: 0, y: 0 },
                    data: { label: 'API' },
                },
                {
                    id: 'service-2',
                    type: 'database',
                    position: { x: 100, y: 0 },
                    data: { label: 'Database' },
                },
            ];

            const mockEdges: Edge[] = [
                { id: 'e1', source: 'service-1', target: 'service-2' },
            ];

            mockGetNodes.mockReturnValue(mockNodes);
            mockGetEdges.mockReturnValue(mockEdges);

            const { result } = renderHook(() =>
                useServiceHealth({ autoRefresh: false })
            );

            await act(async () => {
                await result.current.refreshMetrics();
            });

            await waitFor(() => {
                const health = result.current.getNodeHealth('service-1');
                expect(health?.dependencies).toContain('service-2');
            });
        });
    });
});
