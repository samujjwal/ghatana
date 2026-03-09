/**
 * Visualization Integration for Data-Cloud
 *
 * Bridges @ghatana/ui-extensions visualization components with Data-Cloud
 * metrics and telemetry APIs. Provides typed hooks for dashboards.
 *
 * @doc.type module
 * @doc.purpose Visualization integration for Data-Cloud
 * @doc.layer frontend
 * @doc.pattern Integration
 */

import * as React from 'react';
import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';

// ============================================
// BASE TYPES (Local definitions to avoid import issues)
// ============================================

/** Stream event */
export interface StreamEvent<T = unknown> {
    id: string;
    type: string;
    timestamp: string;
    data: T;
    metadata?: Record<string, unknown>;
}

/** Metric data point */
export interface MetricDataPoint {
    timestamp: string | Date;
    value: number;
    metadata?: Record<string, unknown>;
}

/** Metric series */
export interface MetricSeries {
    id: string;
    name: string;
    description?: string;
    unit: string;
    dataPoints: MetricDataPoint[];
    labels?: Record<string, string>;
}

/** Metric configuration */
export interface MetricConfig {
    id: string;
    name: string;
    unit: string;
    aggregation?: 'sum' | 'avg' | 'min' | 'max' | 'count' | 'p50' | 'p95' | 'p99';
    refreshInterval?: number;
    thresholds?: {
        warning?: number;
        critical?: number;
    };
}

// ============================================
// DATA-CLOUD SPECIFIC TYPES
// ============================================

/**
 * Data-Cloud metric definition with entity awareness
 */
export interface DataCloudMetric extends MetricSeries {
    /** Entity type this metric applies to */
    entityType?: string;
    /** Entity ID if scoped to specific entity */
    entityId?: string;
    /** Brain subsystem if from Brain */
    brainSubsystem?: 'spotlight' | 'autonomy' | 'learning' | 'governance' | 'optimization';
    /** Source system */
    source: 'eventcloud' | 'brain' | 'entity' | 'pipeline' | 'connector';
    /** Aggregation type */
    aggregation: 'sum' | 'avg' | 'min' | 'max' | 'count' | 'p50' | 'p95' | 'p99';
    /** Time granularity */
    granularity: '1m' | '5m' | '15m' | '1h' | '6h' | '1d';
    /** Optional chart color */
    color?: string;
    /** Optional alias used by existing charts */
    data?: MetricDataPoint[];
}

/**
 * Time range for metrics queries
 */
export interface TimeRange {
    start: Date;
    end: Date;
    /** Preset time range */
    preset?: 'last5m' | 'last15m' | 'last1h' | 'last6h' | 'last24h' | 'last7d' | 'last30d';
}

/**
 * Dashboard configuration
 */
export interface DashboardConfig {
    id: string;
    name: string;
    description?: string;
    /** Dashboard layout */
    layout: 'grid' | 'flow' | 'columns';
    /** Panels in the dashboard */
    panels: DashboardPanel[];
    /** Shared filters */
    filters?: {
        timeRange?: TimeRange;
        entityTypes?: string[];
        tenants?: string[];
    };
    /** Refresh interval in seconds */
    refreshInterval?: number;
    /** Created timestamp */
    createdAt: string;
    /** Updated timestamp */
    updatedAt: string;
}

/**
 * Dashboard panel configuration
 */
export interface DashboardPanel {
    id: string;
    title: string;
    /** Panel type */
    type: 'metric-chart' | 'event-stream' | 'stat-card' | 'table' | 'topology' | 'heatmap';
    /** Grid position */
    position: { x: number; y: number; w: number; h: number };
    /** Panel-specific configuration */
    config: {
        /** Metrics to display */
        metrics?: string[];
        /** Metric configuration */
        metricConfig?: MetricConfig;
        /** Event filters */
        eventFilters?: {
            types?: string[];
            severities?: string[];
        };
        /** Custom query */
        query?: string;
    };
}

/**
 * System health overview
 */
export interface SystemHealth {
    overall: 'healthy' | 'degraded' | 'unhealthy';
    components: {
        name: string;
        status: 'healthy' | 'degraded' | 'unhealthy';
        message?: string;
        lastCheck: string;
    }[];
    metrics: {
        uptime: number;
        requestRate: number;
        errorRate: number;
        latencyP50: number;
        latencyP99: number;
    };
}

// ============================================
// API FUNCTIONS
// ============================================

async function fetchMetrics(params: {
    metricIds: string[];
    timeRange: TimeRange;
    entityType?: string;
    entityId?: string;
}): Promise<DataCloudMetric[]> {
    const searchParams = new URLSearchParams();
    params.metricIds.forEach((id) => searchParams.append('metrics', id));
    searchParams.set('start', params.timeRange.start.toISOString());
    searchParams.set('end', params.timeRange.end.toISOString());
    if (params.entityType) searchParams.set('entityType', params.entityType);
    if (params.entityId) searchParams.set('entityId', params.entityId);

    const response = await fetch(`/api/metrics?${searchParams.toString()}`);
    if (!response.ok) throw new Error('Failed to fetch metrics');
    return response.json();
}

async function fetchAvailableMetrics(): Promise<
    { id: string; name: string; description: string; source: string; unit: string }[]
> {
    const response = await fetch('/api/metrics/available');
    if (!response.ok) throw new Error('Failed to fetch available metrics');
    return response.json();
}

async function fetchDashboards(): Promise<DashboardConfig[]> {
    const response = await fetch('/api/dashboards');
    if (!response.ok) throw new Error('Failed to fetch dashboards');
    return response.json();
}

async function fetchDashboard(id: string): Promise<DashboardConfig> {
    const response = await fetch(`/api/dashboards/${id}`);
    if (!response.ok) throw new Error('Failed to fetch dashboard');
    return response.json();
}

async function saveDashboard(dashboard: Omit<DashboardConfig, 'createdAt' | 'updatedAt'>): Promise<DashboardConfig> {
    const response = await fetch(`/api/dashboards/${dashboard.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(dashboard),
    });
    if (!response.ok) throw new Error('Failed to save dashboard');
    return response.json();
}

async function deleteDashboard(id: string): Promise<void> {
    const response = await fetch(`/api/dashboards/${id}`, { method: 'DELETE' });
    if (!response.ok) throw new Error('Failed to delete dashboard');
}

async function fetchSystemHealth(): Promise<SystemHealth> {
    const response = await fetch('/api/health/detailed');
    if (!response.ok) throw new Error('Failed to fetch system health');
    return response.json();
}

async function fetchRecentEvents(params: {
    limit?: number;
    types?: string[];
    severities?: string[];
}): Promise<StreamEvent[]> {
    const searchParams = new URLSearchParams();
    if (params.limit) searchParams.set('limit', params.limit.toString());
    params.types?.forEach((t) => searchParams.append('types', t));
    params.severities?.forEach((s) => searchParams.append('severities', s));

    const response = await fetch(`/api/events/recent?${searchParams.toString()}`);
    if (!response.ok) throw new Error('Failed to fetch recent events');
    return response.json();
}

// ============================================
// HOOKS
// ============================================

/**
 * Time range preset helper
 */
function resolveTimeRange(preset?: TimeRange['preset']): TimeRange {
    const now = new Date();
    const presets: Record<NonNullable<TimeRange['preset']>, number> = {
        last5m: 5 * 60 * 1000,
        last15m: 15 * 60 * 1000,
        last1h: 60 * 60 * 1000,
        last6h: 6 * 60 * 60 * 1000,
        last24h: 24 * 60 * 60 * 1000,
        last7d: 7 * 24 * 60 * 60 * 1000,
        last30d: 30 * 24 * 60 * 60 * 1000,
    };

    const duration = presets[preset ?? 'last1h'];
    return {
        start: new Date(now.getTime() - duration),
        end: now,
        preset,
    };
}

/**
 * Hook for fetching Data-Cloud metrics
 *
 * @example
 * ```tsx
 * const { metrics, isLoading } = useDataCloudMetrics({
 *   metricIds: ['events.processed', 'brain.decisions', 'entities.created'],
 *   timeRange: { preset: 'last1h' },
 * });
 *
 * <LiveMetricsDashboard
 *   metrics={metrics}
 *   isLoading={isLoading}
 * />
 * ```
 */
export function useDataCloudMetrics(options: {
    metricIds: string[];
    timeRange?: TimeRange | { preset: TimeRange['preset'] };
    entityType?: string;
    entityId?: string;
    refreshInterval?: number;
    enabled?: boolean;
}) {
    const timeRange = useMemo(() => {
        if (!options.timeRange) return resolveTimeRange('last1h');
        if ('preset' in options.timeRange && !('start' in options.timeRange)) {
            return resolveTimeRange(options.timeRange.preset);
        }
        return options.timeRange as TimeRange;
    }, [options.timeRange]);

    const { data: metrics = [], isLoading, error, refetch } = useQuery({
        queryKey: ['metrics', options.metricIds, timeRange, options.entityType, options.entityId],
        queryFn: () =>
            fetchMetrics({
                metricIds: options.metricIds,
                timeRange,
                entityType: options.entityType,
                entityId: options.entityId,
            }),
        staleTime: 30000,
        refetchInterval: options.refreshInterval ? options.refreshInterval * 1000 : undefined,
        enabled: options.enabled !== false,
    });

    // Convert to chart-friendly format
    const chartData = useMemo(
        () =>
            metrics.map((m) => ({
                id: m.id,
                name: m.name,
                unit: m.unit,
                color: m.color,
                data: m.data ?? m.dataPoints,
            })),
        [metrics]
    );

    // Calculate summaries
    const summaries = useMemo(
        () =>
            metrics.reduce(
                (acc, m) => {
                    const points = m.data ?? m.dataPoints;
                    const values = points.map((d: MetricDataPoint) => d.value);
                    if (values.length === 0) return acc;

                    acc[m.id] = {
                        current: values[values.length - 1],
                        min: Math.min(...values),
                        max: Math.max(...values),
                        avg: values.reduce((a: number, b: number) => a + b, 0) / values.length,
                    };
                    return acc;
                },
                {} as Record<string, { current: number; min: number; max: number; avg: number }>
            ),
        [metrics]
    );

    return {
        metrics,
        chartData,
        summaries,
        isLoading,
        error,
        refetch,
        timeRange,
    };
}

/**
 * Hook for available metrics catalog
 *
 * @example
 * ```tsx
 * const { metrics, bySource } = useAvailableMetrics();
 *
 * <MetricSelector
 *   options={metrics}
 *   grouped={bySource}
 * />
 * ```
 */
export function useAvailableMetrics() {
    const { data: metrics = [], isLoading, error } = useQuery({
        queryKey: ['metrics', 'available'],
        queryFn: fetchAvailableMetrics,
        staleTime: 5 * 60 * 1000, // 5 minutes
    });

    const bySource = useMemo(
        () =>
            metrics.reduce(
                (acc, m) => {
                    if (!acc[m.source]) acc[m.source] = [];
                    acc[m.source].push(m);
                    return acc;
                },
                {} as Record<string, typeof metrics>
            ),
        [metrics]
    );

    return {
        metrics,
        bySource,
        isLoading,
        error,
    };
}

/**
 * Hook for dashboard management
 *
 * @example
 * ```tsx
 * const { dashboards, save, remove } = useDashboards();
 *
 * <DashboardList
 *   dashboards={dashboards}
 *   onSave={save}
 *   onDelete={remove}
 * />
 * ```
 */
export function useDashboards() {
    const queryClient = useQueryClient();

    const { data: dashboards = [], isLoading, error } = useQuery({
        queryKey: ['dashboards'],
        queryFn: fetchDashboards,
        staleTime: 60000,
    });

    const saveMutation = useMutation({
        mutationFn: saveDashboard,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['dashboards'] });
        },
    });

    const deleteMutation = useMutation({
        mutationFn: deleteDashboard,
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['dashboards'] });
        },
    });

    return {
        dashboards,
        isLoading,
        error,
        save: saveMutation.mutateAsync,
        isSaving: saveMutation.isPending,
        remove: deleteMutation.mutateAsync,
        isDeleting: deleteMutation.isPending,
    };
}

/**
 * Hook for a single dashboard
 *
 * @example
 * ```tsx
 * const { dashboard, updatePanel } = useDashboard('main-dashboard');
 *
 * <DashboardEditor
 *   dashboard={dashboard}
 *   onPanelChange={updatePanel}
 * />
 * ```
 */
export function useDashboard(id: string) {
    const queryClient = useQueryClient();

    const { data: dashboard, isLoading, error } = useQuery({
        queryKey: ['dashboards', id],
        queryFn: () => fetchDashboard(id),
        staleTime: 60000,
    });

    const updateMutation = useMutation({
        mutationFn: (updates: Partial<DashboardConfig>) =>
            saveDashboard({ ...dashboard!, ...updates }),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['dashboards', id] });
            queryClient.invalidateQueries({ queryKey: ['dashboards'] });
        },
    });

    const updatePanel = (panelId: string, updates: Partial<DashboardPanel>) => {
        if (!dashboard) return;

        const updatedPanels = dashboard.panels.map((p) =>
            p.id === panelId ? { ...p, ...updates } : p
        );

        return updateMutation.mutateAsync({ panels: updatedPanels });
    };

    const addPanel = (panel: DashboardPanel) => {
        if (!dashboard) return;
        return updateMutation.mutateAsync({ panels: [...dashboard.panels, panel] });
    };

    const removePanel = (panelId: string) => {
        if (!dashboard) return;
        return updateMutation.mutateAsync({
            panels: dashboard.panels.filter((p) => p.id !== panelId),
        });
    };

    return {
        dashboard,
        isLoading,
        error,
        update: updateMutation.mutateAsync,
        isUpdating: updateMutation.isPending,
        updatePanel,
        addPanel,
        removePanel,
    };
}

/**
 * Hook for system health overview
 *
 * @example
 * ```tsx
 * const { health, isHealthy } = useSystemHealth();
 *
 * <SystemHealthBanner
 *   status={health.overall}
 *   components={health.components}
 * />
 * ```
 */
export function useSystemHealth(options?: { refreshInterval?: number }) {
    const { data: health, isLoading, error, refetch } = useQuery({
        queryKey: ['health', 'detailed'],
        queryFn: fetchSystemHealth,
        staleTime: 10000,
        refetchInterval: options?.refreshInterval ? options.refreshInterval * 1000 : 30000,
    });

    return {
        health,
        isLoading,
        error,
        refetch,
        isHealthy: health?.overall === 'healthy',
        isDegraded: health?.overall === 'degraded',
        isUnhealthy: health?.overall === 'unhealthy',
    };
}

/**
 * Hook for recent events (non-realtime)
 *
 * @example
 * ```tsx
 * const { events } = useRecentEvents({ limit: 100 });
 *
 * <EventStreamVisualization
 *   events={events}
 *   realtime={false}
 * />
 * ```
 */
export function useRecentEvents(options?: {
    limit?: number;
    types?: string[];
    severities?: string[];
    refreshInterval?: number;
}) {
    const { data: events = [], isLoading, error, refetch } = useQuery({
        queryKey: ['events', 'recent', options?.types, options?.severities, options?.limit],
        queryFn: () =>
            fetchRecentEvents({
                limit: options?.limit ?? 100,
                types: options?.types,
                severities: options?.severities,
            }),
        staleTime: 10000,
        refetchInterval: options?.refreshInterval ? options.refreshInterval * 1000 : undefined,
    });

    return {
        events,
        isLoading,
        error,
        refetch,
    };
}

// ============================================
// CONTEXT & PROVIDER
// ============================================

interface DataCloudVisualizationContextValue {
    defaultTimeRange: TimeRange;
    setDefaultTimeRange: (range: TimeRange | { preset: TimeRange['preset'] }) => void;
    systemHealth: SystemHealth | undefined;
    isHealthLoading: boolean;
}

const DataCloudVisualizationContext = createContext<DataCloudVisualizationContextValue | null>(null);

/**
 * Provider for Data-Cloud visualization context
 *
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <DataCloudVisualizationProvider defaultPreset="last1h">
 *       <Dashboard />
 *     </DataCloudVisualizationProvider>
 *   );
 * }
 * ```
 */
export function DataCloudVisualizationProvider({
    children,
    defaultPreset = 'last1h',
}: {
    children: ReactNode;
    defaultPreset?: TimeRange['preset'];
}) {
    const [defaultTimeRange, setDefaultTimeRangeState] = React.useState<TimeRange>(
        () => resolveTimeRange(defaultPreset)
    );

    const { health, isLoading: isHealthLoading } = useSystemHealth();

    const setDefaultTimeRange = React.useCallback(
        (range: TimeRange | { preset: TimeRange['preset'] }) => {
            if ('preset' in range && !('start' in range)) {
                setDefaultTimeRangeState(resolveTimeRange(range.preset));
            } else {
                setDefaultTimeRangeState(range as TimeRange);
            }
        },
        []
    );

    const value: DataCloudVisualizationContextValue = useMemo(
        () => ({
            defaultTimeRange,
            setDefaultTimeRange,
            systemHealth: health,
            isHealthLoading,
        }),
        [defaultTimeRange, setDefaultTimeRange, health, isHealthLoading]
    );

    return (
        <DataCloudVisualizationContext.Provider value={value}>
            {children}
        </DataCloudVisualizationContext.Provider>
    );
}

/**
 * Hook to access Data-Cloud visualization context
 */
export function useDataCloudVisualizationContext() {
    const context = useContext(DataCloudVisualizationContext);
    if (!context) {
        throw new Error('useDataCloudVisualizationContext must be used within DataCloudVisualizationProvider');
    }
    return context;
}
