/**
 * Performance Analysis Hook
 * 
 * @doc.type hook
 * @doc.purpose State management for performance profiling, latency tracking, and optimization analysis
 * @doc.layer product
 * @doc.pattern Custom React Hook
 * 
 * Provides comprehensive performance analysis including:
 * - Service and metric management
 * - Latency percentile calculations (P50, P95, P99)
 * - SLO tracking and compliance monitoring
 * - Bottleneck detection algorithms
 * - Query performance analysis
 * - AI-powered optimization recommendations
 * - Snapshot comparison for before/after analysis
 * - Performance report export
 * 
 * @example
 * ```tsx
 * const {
 *   services,
 *   addService,
 *   calculatePercentile,
 *   detectBottlenecks,
 *   generateOptimizationRecommendations
 * } = usePerformanceAnalysis();
 * ```
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Metric type for performance tracking
 */
export type MetricType =
    | 'latency_p50'
    | 'latency_p95'
    | 'latency_p99'
    | 'error_rate'
    | 'throughput'
    | 'cpu_usage'
    | 'memory_usage';

/**
 * Time range for metric queries
 */
export type TimeRange = '1h' | '6h' | '24h' | '7d' | '30d';

/**
 * Optimization priority level
 */
export type OptimizationPriority = 'low' | 'medium' | 'high' | 'critical';

/**
 * Service in the system
 */
export interface Service {
    /**
     * Unique service identifier
     */
    id: string;

    /**
     * Service name
     */
    name: string;

    /**
     * Service type
     */
    type: 'api' | 'database' | 'cache' | 'queue' | 'external';

    /**
     * Service endpoint URL
     */
    endpoint: string;

    /**
     * Dependencies (service IDs this service calls)
     */
    dependencies?: string[];
}

/**
 * Performance metric data point
 */
export interface PerformanceMetric {
    /**
     * Unique metric identifier
     */
    id: string;

    /**
     * Service this metric belongs to
     */
    serviceId: string;

    /**
     * Type of metric
     */
    type: MetricType;

    /**
     * Metric value
     */
    value: number;

    /**
     * Timestamp when metric was collected
     */
    timestamp: Date;
}

/**
 * Service Level Objective (SLO) definition
 */
export interface SLO {
    /**
     * Service this SLO applies to
     */
    serviceId: string;

    /**
     * P50 latency target (ms)
     */
    p50Target: number;

    /**
     * P95 latency target (ms)
     */
    p95Target: number;

    /**
     * P99 latency target (ms)
     */
    p99Target: number;

    /**
     * Error rate target (%)
     */
    errorRateTarget: number;
}

/**
 * Detected performance bottleneck
 */
export interface Bottleneck {
    /**
     * Unique bottleneck identifier
     */
    id: string;

    /**
     * Service with bottleneck
     */
    serviceId: string;

    /**
     * Reason for bottleneck
     */
    reason: string;

    /**
     * Severity level
     */
    severity: OptimizationPriority;

    /**
     * Impact description
     */
    impact: string;

    /**
     * When detected
     */
    detectedAt: Date;
}

/**
 * Query performance record
 */
export interface QueryPerformance {
    /**
     * Unique query identifier
     */
    id: string;

    /**
     * Service executing this query
     */
    serviceId: string;

    /**
     * SQL query text
     */
    query: string;

    /**
     * Execution time (ms)
     */
    executionTime: number;

    /**
     * Timestamp
     */
    timestamp: Date;

    /**
     * Number of rows returned
     */
    rowCount?: number;
}

/**
 * Optimization recommendation
 */
export interface OptimizationRecommendation {
    /**
     * Unique recommendation identifier
     */
    id: string;

    /**
     * Service to optimize
     */
    serviceId: string;

    /**
     * Recommendation text
     */
    recommendation: string;

    /**
     * Priority level
     */
    priority: OptimizationPriority;

    /**
     * Estimated improvement
     */
    estimatedImprovement: string;

    /**
     * Implementation effort
     */
    implementationEffort: string;
}

/**
 * Performance snapshot for comparison
 */
export interface PerformanceSnapshot {
    /**
     * Unique snapshot identifier
     */
    id: string;

    /**
     * When snapshot was taken
     */
    timestamp: Date;

    /**
     * Services at snapshot time
     */
    services: Service[];

    /**
     * Metrics at snapshot time
     */
    metrics: PerformanceMetric[];
}

/**
 * Return type of usePerformanceAnalysis hook
 */
export interface UsePerformanceAnalysisReturn {
    // State
    system: string;
    setSystem: (system: string) => void;
    selectedService: string | null;
    setSelectedService: (serviceId: string | null) => void;
    selectedTimeRange: TimeRange;
    setSelectedTimeRange: (range: TimeRange) => void;

    // Service Management
    getServices: () => Service[];
    addService: (service: Omit<Service, 'id'>) => string;
    updateService: (id: string, updates: Partial<Service>) => void;
    deleteService: (id: string) => void;

    // Metric Collection
    addMetric: (metric: Omit<PerformanceMetric, 'id' | 'timestamp'>) => string;
    getMetrics: () => PerformanceMetric[];
    getServiceMetrics: (serviceId: string, timeRange: TimeRange) => PerformanceMetric[];
    getLatestMetrics: (serviceId: string) => PerformanceMetric[];

    // SLO Management
    getSLO: (serviceId: string) => SLO;
    updateSLO: (serviceId: string, slo: Partial<SLO>) => void;
    isSLOViolated: (serviceId: string) => boolean;
    getSLOCompliance: (serviceId: string, timeRange: TimeRange) => number;

    // Latency Analysis
    calculatePercentile: (serviceId: string, timeRange: TimeRange, percentile: number) => number;
    getLatencyDistribution: (serviceId: string, timeRange: TimeRange) => Record<string, number>;
    getAverageLatency: (serviceId: string, timeRange: TimeRange) => number;

    // Bottleneck Detection
    detectBottlenecks: () => Bottleneck[];
    getBottlenecksByService: (serviceId: string) => Bottleneck[];
    getCriticalPath: () => string[];

    // Query Analysis
    addQuery: (query: Omit<QueryPerformance, 'id' | 'timestamp'>) => string;
    getSlowQueries: (thresholdMs: number) => QueryPerformance[];
    getQueryStatistics: (queryId: string) => {
        avgExecutionTime: number;
        p95ExecutionTime: number;
        executionCount: number;
    };

    // Optimization
    generateOptimizationRecommendations: () => OptimizationRecommendation[];
    getRecommendationsByPriority: (priority: OptimizationPriority) => OptimizationRecommendation[];

    // Comparison
    createSnapshot: () => string;
    compareSnapshots: (snapshot1Id: string, snapshot2Id: string) => {
        services: { added: Service[]; removed: Service[]; modified: Service[] };
        latencyChanges: Record<string, { before: number; after: number; change: number }>;
    };

    // Export
    exportReport: (format: 'json' | 'csv') => string;
}

/**
 * Performance Analysis Hook
 * 
 * Provides state management and operations for performance profiling,
 * including metric collection, SLO tracking, bottleneck detection,
 * and optimization recommendations.
 * 
 * @returns Performance analysis state and operations
 */
export const usePerformanceAnalysis = (): UsePerformanceAnalysisReturn => {
    // State
    const [system, setSystem] = useState<string>('Performance Analysis');
    const [selectedService, setSelectedService] = useState<string | null>(null);
    const [selectedTimeRange, setSelectedTimeRange] = useState<TimeRange>('1h');
    const [services, setServices] = useState<Service[]>([]);
    const [metrics, setMetrics] = useState<PerformanceMetric[]>([]);
    const [slos, setSLOs] = useState<Map<string, SLO>>(new Map());
    const [bottlenecks, setBottlenecks] = useState<Bottleneck[]>([]);
    const [queries, setQueries] = useState<QueryPerformance[]>([]);
    const [snapshots, setSnapshots] = useState<PerformanceSnapshot[]>([]);

    // Helper: Convert time range to milliseconds
    const getTimeRangeMs = useCallback((range: TimeRange): number => {
        switch (range) {
            case '1h': return 60 * 60 * 1000;
            case '6h': return 6 * 60 * 60 * 1000;
            case '24h': return 24 * 60 * 60 * 1000;
            case '7d': return 7 * 24 * 60 * 60 * 1000;
            case '30d': return 30 * 24 * 60 * 60 * 1000;
            default: return 60 * 60 * 1000;
        }
    }, []);

    // Service Management
    const getServices = useCallback((): Service[] => {
        return services;
    }, [services]);

    const addService = useCallback((service: Omit<Service, 'id'>): string => {
        const id = `service-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newService: Service = {
            id,
            ...service,
        };
        setServices((prev) => [...prev, newService]);

        // Initialize default SLO
        setSLOs((prev) => {
            const newMap = new Map(prev);
            newMap.set(id, {
                serviceId: id,
                p50Target: 100,
                p95Target: 500,
                p99Target: 1000,
                errorRateTarget: 1.0,
            });
            return newMap;
        });

        return id;
    }, []);

    const updateService = useCallback((id: string, updates: Partial<Service>): void => {
        setServices((prev) =>
            prev.map((service) => (service.id === id ? { ...service, ...updates } : service))
        );
    }, []);

    const deleteService = useCallback((id: string): void => {
        setServices((prev) => prev.filter((service) => service.id !== id));
        setMetrics((prev) => prev.filter((metric) => metric.serviceId !== id));
        setSLOs((prev) => {
            const newMap = new Map(prev);
            newMap.delete(id);
            return newMap;
        });
        setBottlenecks((prev) => prev.filter((b) => b.serviceId !== id));
        setQueries((prev) => prev.filter((q) => q.serviceId !== id));
    }, []);

    // Metric Collection
    const addMetric = useCallback((metric: Omit<PerformanceMetric, 'id' | 'timestamp'>): string => {
        const id = `metric-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newMetric: PerformanceMetric = {
            id,
            ...metric,
            timestamp: new Date(),
        };
        setMetrics((prev) => [...prev, newMetric]);
        return id;
    }, []);

    const getMetrics = useCallback((): PerformanceMetric[] => {
        return metrics;
    }, [metrics]);

    const getServiceMetrics = useCallback(
        (serviceId: string, timeRange: TimeRange): PerformanceMetric[] => {
            const rangeMs = getTimeRangeMs(timeRange);
            const cutoff = Date.now() - rangeMs;
            return metrics.filter(
                (m) => m.serviceId === serviceId && m.timestamp.getTime() >= cutoff
            );
        },
        [metrics, getTimeRangeMs]
    );

    const getLatestMetrics = useCallback(
        (serviceId: string): PerformanceMetric[] => {
            const serviceMetrics = metrics.filter((m) => m.serviceId === serviceId);
            const latestByType = new Map<MetricType, PerformanceMetric>();

            serviceMetrics.forEach((metric) => {
                const existing = latestByType.get(metric.type);
                if (!existing || metric.timestamp > existing.timestamp) {
                    latestByType.set(metric.type, metric);
                }
            });

            return Array.from(latestByType.values());
        },
        [metrics]
    );

    // SLO Management
    const getSLO = useCallback(
        (serviceId: string): SLO => {
            return (
                slos.get(serviceId) || {
                    serviceId,
                    p50Target: 100,
                    p95Target: 500,
                    p99Target: 1000,
                    errorRateTarget: 1.0,
                }
            );
        },
        [slos]
    );

    const updateSLO = useCallback((serviceId: string, slo: Partial<SLO>): void => {
        setSLOs((prev) => {
            const newMap = new Map(prev);
            const existing = newMap.get(serviceId) || {
                serviceId,
                p50Target: 100,
                p95Target: 500,
                p99Target: 1000,
                errorRateTarget: 1.0,
            };
            newMap.set(serviceId, { ...existing, ...slo });
            return newMap;
        });
    }, []);

    const isSLOViolated = useCallback(
        (serviceId: string): boolean => {
            const slo = getSLO(serviceId);
            const latestMetrics = getLatestMetrics(serviceId);

            const p95Metric = latestMetrics.find((m) => m.type === 'latency_p95');
            const errorRateMetric = latestMetrics.find((m) => m.type === 'error_rate');

            return (
                (p95Metric ? p95Metric.value > slo.p95Target : false) ||
                (errorRateMetric ? errorRateMetric.value > slo.errorRateTarget : false)
            );
        },
        [getSLO, getLatestMetrics]
    );

    const getSLOCompliance = useCallback(
        (serviceId: string, timeRange: TimeRange): number => {
            const slo = getSLO(serviceId);
            const rangeMetrics = getServiceMetrics(serviceId, timeRange);
            const p95Metrics = rangeMetrics.filter((m) => m.type === 'latency_p95');

            if (p95Metrics.length === 0) return 100;

            const compliantMetrics = p95Metrics.filter((m) => m.value <= slo.p95Target).length;
            return (compliantMetrics / p95Metrics.length) * 100;
        },
        [getSLO, getServiceMetrics]
    );

    // Latency Analysis
    const calculatePercentile = useCallback(
        (serviceId: string, timeRange: TimeRange, percentile: number): number => {
            const rangeMetrics = getServiceMetrics(serviceId, timeRange);
            const latencyMetrics = rangeMetrics.filter((m) => m.type.startsWith('latency_'));

            if (latencyMetrics.length === 0) return 0;

            const values = latencyMetrics.map((m) => m.value).sort((a, b) => a - b);
            const index = Math.ceil((percentile / 100) * values.length) - 1;
            return values[Math.max(0, index)];
        },
        [getServiceMetrics]
    );

    const getLatencyDistribution = useCallback(
        (serviceId: string, timeRange: TimeRange): Record<string, number> => {
            return {
                p50: calculatePercentile(serviceId, timeRange, 50),
                p75: calculatePercentile(serviceId, timeRange, 75),
                p90: calculatePercentile(serviceId, timeRange, 90),
                p95: calculatePercentile(serviceId, timeRange, 95),
                p99: calculatePercentile(serviceId, timeRange, 99),
            };
        },
        [calculatePercentile]
    );

    const getAverageLatency = useCallback(
        (serviceId: string, timeRange: TimeRange): number => {
            const rangeMetrics = getServiceMetrics(serviceId, timeRange);
            const latencyMetrics = rangeMetrics.filter((m) => m.type.startsWith('latency_'));

            if (latencyMetrics.length === 0) return 0;

            const sum = latencyMetrics.reduce((acc, m) => acc + m.value, 0);
            return sum / latencyMetrics.length;
        },
        [getServiceMetrics]
    );

    // Bottleneck Detection
    const detectBottlenecks = useCallback((): Bottleneck[] => {
        const detected: Bottleneck[] = [];

        services.forEach((service) => {
            const slo = getSLO(service.id);
            const latestMetrics = getLatestMetrics(service.id);
            const p95Metric = latestMetrics.find((m) => m.type === 'latency_p95');
            const errorRateMetric = latestMetrics.find((m) => m.type === 'error_rate');

            // High latency bottleneck
            if (p95Metric && p95Metric.value > slo.p95Target * 1.5) {
                detected.push({
                    id: `bottleneck-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                    serviceId: service.id,
                    reason: `P95 latency (${p95Metric.value.toFixed(0)}ms) exceeds SLO target (${slo.p95Target}ms) by 50%`,
                    severity: p95Metric.value > slo.p95Target * 2 ? 'critical' : 'high',
                    impact: 'Users experiencing significant delays',
                    detectedAt: new Date(),
                });
            }

            // High error rate bottleneck
            if (errorRateMetric && errorRateMetric.value > slo.errorRateTarget * 2) {
                detected.push({
                    id: `bottleneck-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                    serviceId: service.id,
                    reason: `Error rate (${errorRateMetric.value.toFixed(2)}%) exceeds SLO target (${slo.errorRateTarget}%)`,
                    severity: errorRateMetric.value > slo.errorRateTarget * 5 ? 'critical' : 'high',
                    impact: 'Service reliability compromised',
                    detectedAt: new Date(),
                });
            }
        });

        setBottlenecks(detected);
        return detected;
    }, [services, getSLO, getLatestMetrics]);

    const getBottlenecksByService = useCallback(
        (serviceId: string): Bottleneck[] => {
            return bottlenecks.filter((b) => b.serviceId === serviceId);
        },
        [bottlenecks]
    );

    const getCriticalPath = useCallback((): string[] => {
        // Build dependency graph and find longest path
        const visited = new Set<string>();
        let longestPath: string[] = [];

        const dfs = (serviceId: string, path: string[]): void => {
            if (visited.has(serviceId)) return;

            visited.add(serviceId);
            path.push(serviceId);

            if (path.length > longestPath.length) {
                longestPath = [...path];
            }

            const service = services.find((s) => s.id === serviceId);
            if (service?.dependencies) {
                service.dependencies.forEach((depId) => {
                    dfs(depId, path);
                });
            }

            path.pop();
            visited.delete(serviceId);
        };

        // Start DFS from services with no incoming dependencies
        services.forEach((service) => {
            const hasIncomingDeps = services.some(
                (s) => s.dependencies && s.dependencies.includes(service.id)
            );
            if (!hasIncomingDeps) {
                dfs(service.id, []);
            }
        });

        return longestPath;
    }, [services]);

    // Query Analysis
    const addQuery = useCallback((query: Omit<QueryPerformance, 'id' | 'timestamp'>): string => {
        const id = `query-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const newQuery: QueryPerformance = {
            id,
            ...query,
            timestamp: new Date(),
        };
        setQueries((prev) => [...prev, newQuery]);
        return id;
    }, []);

    const getSlowQueries = useCallback(
        (thresholdMs: number): QueryPerformance[] => {
            return queries.filter((q) => q.executionTime >= thresholdMs);
        },
        [queries]
    );

    const getQueryStatistics = useCallback(
        (queryId: string) => {
            const query = queries.find((q) => q.id === queryId);
            if (!query) {
                return {
                    avgExecutionTime: 0,
                    p95ExecutionTime: 0,
                    executionCount: 0,
                };
            }

            // Find similar queries (same query text)
            const similarQueries = queries.filter((q) => q.query === query.query);
            const executionTimes = similarQueries.map((q) => q.executionTime).sort((a, b) => a - b);

            const avgExecutionTime =
                executionTimes.reduce((sum, t) => sum + t, 0) / executionTimes.length;
            const p95Index = Math.ceil(0.95 * executionTimes.length) - 1;
            const p95ExecutionTime = executionTimes[Math.max(0, p95Index)];

            return {
                avgExecutionTime,
                p95ExecutionTime,
                executionCount: similarQueries.length,
            };
        },
        [queries]
    );

    // Optimization
    const generateOptimizationRecommendations = useCallback((): OptimizationRecommendation[] => {
        const recommendations: OptimizationRecommendation[] = [];

        services.forEach((service) => {
            const slo = getSLO(service.id);
            const latestMetrics = getLatestMetrics(service.id);
            const p95Metric = latestMetrics.find((m) => m.type === 'latency_p95');
            const errorRateMetric = latestMetrics.find((m) => m.type === 'error_rate');

            // High latency recommendations
            if (p95Metric && p95Metric.value > slo.p95Target * 1.2) {
                if (service.type === 'database') {
                    recommendations.push({
                        id: `rec-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                        serviceId: service.id,
                        recommendation: 'Add database indexes for frequently queried columns',
                        priority: p95Metric.value > slo.p95Target * 2 ? 'critical' : 'high',
                        estimatedImprovement: '30-50% latency reduction',
                        implementationEffort: 'Low (1-2 hours)',
                    });
                } else if (service.type === 'api') {
                    recommendations.push({
                        id: `rec-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                        serviceId: service.id,
                        recommendation: 'Implement response caching for frequently accessed endpoints',
                        priority: p95Metric.value > slo.p95Target * 2 ? 'high' : 'medium',
                        estimatedImprovement: '40-60% latency reduction',
                        implementationEffort: 'Medium (3-5 hours)',
                    });
                }
            }

            // High error rate recommendations
            if (errorRateMetric && errorRateMetric.value > slo.errorRateTarget * 1.5) {
                recommendations.push({
                    id: `rec-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                    serviceId: service.id,
                    recommendation: 'Implement circuit breaker pattern for external service calls',
                    priority: errorRateMetric.value > slo.errorRateTarget * 3 ? 'critical' : 'high',
                    estimatedImprovement: '50-70% error reduction',
                    implementationEffort: 'Medium (4-6 hours)',
                });
            }

            // Cache recommendations
            if (service.type === 'api' || service.type === 'database') {
                const throughput = latestMetrics.find((m) => m.type === 'throughput')?.value || 0;
                if (throughput > 100) {
                    recommendations.push({
                        id: `rec-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
                        serviceId: service.id,
                        recommendation: 'Add Redis caching layer to reduce database load',
                        priority: 'medium',
                        estimatedImprovement: '25-40% latency reduction',
                        implementationEffort: 'High (1-2 days)',
                    });
                }
            }
        });

        return recommendations;
    }, [services, getSLO, getLatestMetrics]);

    const getRecommendationsByPriority = useCallback(
        (priority: OptimizationPriority): OptimizationRecommendation[] => {
            const allRecommendations = generateOptimizationRecommendations();
            return allRecommendations.filter((r) => r.priority === priority);
        },
        [generateOptimizationRecommendations]
    );

    // Comparison
    const createSnapshot = useCallback((): string => {
        const id = `snapshot-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
        const snapshot: PerformanceSnapshot = {
            id,
            timestamp: new Date(),
            services: [...services],
            metrics: [...metrics],
        };
        setSnapshots((prev) => [...prev, snapshot]);
        return id;
    }, [services, metrics]);

    const compareSnapshots = useCallback(
        (snapshot1Id: string, snapshot2Id: string) => {
            const snapshot1 = snapshots.find((s) => s.id === snapshot1Id);
            const snapshot2 = snapshots.find((s) => s.id === snapshot2Id);

            if (!snapshot1 || !snapshot2) {
                return {
                    services: { added: [], removed: [], modified: [] },
                    latencyChanges: {},
                };
            }

            // Compare services
            const snapshot1ServiceIds = new Set(snapshot1.services.map((s) => s.id));
            const snapshot2ServiceIds = new Set(snapshot2.services.map((s) => s.id));

            const added = snapshot2.services.filter((s) => !snapshot1ServiceIds.has(s.id));
            const removed = snapshot1.services.filter((s) => !snapshot2ServiceIds.has(s.id));
            const modified = snapshot2.services.filter((s) => snapshot1ServiceIds.has(s.id));

            // Compare latency
            const latencyChanges: Record<string, { before: number; after: number; change: number }> = {};

            modified.forEach((service) => {
                const before = snapshot1.metrics
                    .filter((m) => m.serviceId === service.id && m.type === 'latency_p95')
                    .map((m) => m.value);
                const after = snapshot2.metrics
                    .filter((m) => m.serviceId === service.id && m.type === 'latency_p95')
                    .map((m) => m.value);

                if (before.length > 0 && after.length > 0) {
                    const avgBefore = before.reduce((sum, v) => sum + v, 0) / before.length;
                    const avgAfter = after.reduce((sum, v) => sum + v, 0) / after.length;
                    const change = ((avgAfter - avgBefore) / avgBefore) * 100;

                    latencyChanges[service.id] = {
                        before: avgBefore,
                        after: avgAfter,
                        change,
                    };
                }
            });

            return {
                services: { added, removed, modified },
                latencyChanges,
            };
        },
        [snapshots]
    );

    // Export
    const exportReport = useCallback(
        (format: 'json' | 'csv'): string => {
            if (format === 'json') {
                return JSON.stringify(
                    {
                        system,
                        services,
                        metrics: metrics.slice(-100), // Last 100 metrics
                        bottlenecks,
                        recommendations: generateOptimizationRecommendations(),
                        timestamp: new Date().toISOString(),
                    },
                    null,
                    2
                );
            }

            // CSV format
            const headers = ['Service', 'Type', 'P50', 'P95', 'P99', 'Error Rate', 'SLO Violated'];
            const rows = services.map((service) => {
                const latestMetrics = getLatestMetrics(service.id);
                const p50 = latestMetrics.find((m) => m.type === 'latency_p50')?.value || 0;
                const p95 = latestMetrics.find((m) => m.type === 'latency_p95')?.value || 0;
                const p99 = latestMetrics.find((m) => m.type === 'latency_p99')?.value || 0;
                const errorRate = latestMetrics.find((m) => m.type === 'error_rate')?.value || 0;
                const violated = isSLOViolated(service.id);

                return [
                    service.name,
                    service.type,
                    p50.toFixed(0),
                    p95.toFixed(0),
                    p99.toFixed(0),
                    errorRate.toFixed(2),
                    violated ? 'Yes' : 'No',
                ].join(',');
            });

            return [headers.join(','), ...rows].join('\n');
        },
        [system, services, metrics, bottlenecks, generateOptimizationRecommendations, getLatestMetrics, isSLOViolated]
    );

    return {
        // State
        system,
        setSystem,
        selectedService,
        setSelectedService,
        selectedTimeRange,
        setSelectedTimeRange,

        // Service Management
        getServices,
        addService,
        updateService,
        deleteService,

        // Metric Collection
        addMetric,
        getMetrics,
        getServiceMetrics,
        getLatestMetrics,

        // SLO Management
        getSLO,
        updateSLO,
        isSLOViolated,
        getSLOCompliance,

        // Latency Analysis
        calculatePercentile,
        getLatencyDistribution,
        getAverageLatency,

        // Bottleneck Detection
        detectBottlenecks,
        getBottlenecksByService,
        getCriticalPath,

        // Query Analysis
        addQuery,
        getSlowQueries,
        getQueryStatistics,

        // Optimization
        generateOptimizationRecommendations,
        getRecommendationsByPriority,

        // Comparison
        createSnapshot,
        compareSnapshots,

        // Export
        exportReport,
    };
};

export default usePerformanceAnalysis;
