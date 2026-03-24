/**
 * Performance Analysis Hook Tests
 * 
 * @doc.type test
 * @doc.purpose Comprehensive testing for performance profiling hook
 * @doc.layer product
 * @doc.pattern React Hook Testing
 */

import { renderHook, act } from '@testing-library/react';
import {
    usePerformanceAnalysis,
    MetricType,
    TimeRange,
    OptimizationPriority,
} from './usePerformanceAnalysis';

describe('usePerformanceAnalysis', () => {
    // ===========================
    // Initialization Tests (3)
    // ===========================

    describe('Initialization', () => {
        it('should initialize with default values', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            expect(result.current.system).toBe('Performance Analysis');
            expect(result.current.selectedService).toBeNull();
            expect(result.current.selectedTimeRange).toBe('1h');
            expect(result.current.getServices()).toEqual([]);
            expect(result.current.getMetrics()).toEqual([]);
        });

        it('should allow setting system name', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            act(() => {
                result.current.setSystem('Production System');
            });

            expect(result.current.system).toBe('Production System');
        });

        it('should allow setting selected service and time range', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            act(() => {
                const serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });
                result.current.setSelectedService(serviceId);
                result.current.setSelectedTimeRange('24h');
            });

            expect(result.current.selectedService).toBeTruthy();
            expect(result.current.selectedTimeRange).toBe('24h');
        });
    });

    // ===========================
    // Service Management Tests (4)
    // ===========================

    describe('Service Management', () => {
        it('should add a new service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                    dependencies: [],
                });
            });

            const services = result.current.getServices();
            expect(services).toHaveLength(1);
            expect(services[0].name).toBe('Database Service');
            expect(services[0].type).toBe('database');
            expect(serviceId).toBeTruthy();
        });

        it('should update a service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });
                result.current.updateService(serviceId, {
                    name: 'Updated API Service',
                    endpoint: 'https://api-v2.example.com',
                });
            });

            const services = result.current.getServices();
            expect(services[0].name).toBe('Updated API Service');
            expect(services[0].endpoint).toBe('https://api-v2.example.com');
        });

        it('should delete a service and cascade cleanup', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Cache Service',
                    type: 'cache',
                    endpoint: 'redis://cache.example.com:6379',
                });
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 50,
                });
            });

            expect(result.current.getServices()).toHaveLength(1);
            expect(result.current.getMetrics()).toHaveLength(1);

            act(() => {
                result.current.deleteService(serviceId);
            });

            expect(result.current.getServices()).toHaveLength(0);
            expect(result.current.getMetrics()).toHaveLength(0);
        });

        it('should initialize default SLO on service creation', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Test Service',
                    type: 'api',
                    endpoint: 'https://test.example.com',
                });
            });

            const slo = result.current.getSLO(serviceId);
            expect(slo.p50Target).toBe(100);
            expect(slo.p95Target).toBe(500);
            expect(slo.p99Target).toBe(1000);
            expect(slo.errorRateTarget).toBe(1.0);
        });
    });

    // ===========================
    // Metric Collection Tests (5)
    // ===========================

    describe('Metric Collection', () => {
        it('should add a metric', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            let metricId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });
                metricId = result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 250,
                });
            });

            const metrics = result.current.getMetrics();
            expect(metrics).toHaveLength(1);
            expect(metrics[0].type).toBe('latency_p95');
            expect(metrics[0].value).toBe(250);
            expect(metricId).toBeTruthy();
        });

        it('should get service metrics filtered by time range', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add metrics with timestamps
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 200,
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p50',
                    value: 100,
                });
            });

            const metrics = result.current.getServiceMetrics(serviceId, '1h');
            expect(metrics.length).toBeGreaterThanOrEqual(2);
        });

        it('should get latest metrics per type', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add multiple metrics of same type
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 200,
                });

                // Wait a bit to ensure different timestamps
                setTimeout(() => {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p95',
                        value: 250,
                    });
                }, 10);

                result.current.addMetric({
                    serviceId,
                    type: 'error_rate',
                    value: 1.5,
                });
            });

            const latestMetrics = result.current.getLatestMetrics(serviceId);
            expect(latestMetrics.length).toBeLessThanOrEqual(2);

            const p95Metric = latestMetrics.find((m) => m.type === 'latency_p95');
            expect(p95Metric).toBeTruthy();
        });

        it('should filter metrics by service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let service1Id: string;
            let service2Id: string;
            act(() => {
                service1Id = result.current.addService({
                    name: 'Service 1',
                    type: 'api',
                    endpoint: 'https://api1.example.com',
                });

                service2Id = result.current.addService({
                    name: 'Service 2',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                result.current.addMetric({
                    serviceId: service1Id,
                    type: 'latency_p95',
                    value: 200,
                });

                result.current.addMetric({
                    serviceId: service2Id,
                    type: 'latency_p95',
                    value: 500,
                });
            });

            const service1Metrics = result.current.getServiceMetrics(service1Id, '1h');
            const service2Metrics = result.current.getServiceMetrics(service2Id, '1h');

            expect(service1Metrics).toHaveLength(1);
            expect(service2Metrics).toHaveLength(1);
            expect(service1Metrics[0].value).toBe(200);
            expect(service2Metrics[0].value).toBe(500);
        });

        it('should get all metrics', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({ serviceId, type: 'latency_p50', value: 100 });
                result.current.addMetric({ serviceId, type: 'latency_p95', value: 250 });
                result.current.addMetric({ serviceId, type: 'error_rate', value: 0.5 });
            });

            const allMetrics = result.current.getMetrics();
            expect(allMetrics).toHaveLength(3);
        });
    });

    // ===========================
    // SLO Management Tests (4)
    // ===========================

    describe('SLO Management', () => {
        it('should get SLO for a service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });
            });

            const slo = result.current.getSLO(serviceId);
            expect(slo.serviceId).toBe(serviceId);
            expect(slo.p50Target).toBe(100);
            expect(slo.p95Target).toBe(500);
            expect(slo.p99Target).toBe(1000);
        });

        it('should update SLO', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.updateSLO(serviceId, {
                    p95Target: 300,
                    errorRateTarget: 0.5,
                });
            });

            const slo = result.current.getSLO(serviceId);
            expect(slo.p95Target).toBe(300);
            expect(slo.errorRateTarget).toBe(0.5);
            expect(slo.p50Target).toBe(100); // Unchanged
        });

        it('should detect SLO violation - high latency', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 600, // Exceeds default SLO of 500ms
                });
            });

            expect(result.current.isSLOViolated(serviceId)).toBe(true);
        });

        it('should detect SLO violation - high error rate', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'error_rate',
                    value: 2.5, // Exceeds default SLO of 1.0%
                });
            });

            expect(result.current.isSLOViolated(serviceId)).toBe(true);
        });

        it('should calculate SLO compliance percentage', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add mix of compliant and non-compliant metrics
                result.current.addMetric({ serviceId, type: 'latency_p95', value: 400 }); // Compliant
                result.current.addMetric({ serviceId, type: 'latency_p95', value: 600 }); // Non-compliant
                result.current.addMetric({ serviceId, type: 'latency_p95', value: 300 }); // Compliant
                result.current.addMetric({ serviceId, type: 'latency_p95', value: 450 }); // Compliant
            });

            const compliance = result.current.getSLOCompliance(serviceId, '1h');
            expect(compliance).toBe(75); // 3 out of 4 compliant
        });
    });

    // ===========================
    // Latency Analysis Tests (5)
    // ===========================

    describe('Latency Analysis', () => {
        it('should calculate P50 percentile', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add metrics with known values
                [100, 200, 300, 400, 500].forEach((value) => {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p50',
                        value,
                    });
                });
            });

            const p50 = result.current.calculatePercentile(serviceId, '1h', 50);
            expect(p50).toBeGreaterThanOrEqual(250);
            expect(p50).toBeLessThanOrEqual(350);
        });

        it('should calculate P95 percentile', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add 20 metrics
                for (let i = 1; i <= 20; i++) {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p95',
                        value: i * 50,
                    });
                }
            });

            const p95 = result.current.calculatePercentile(serviceId, '1h', 95);
            expect(p95).toBeGreaterThanOrEqual(900); // 95th percentile should be near high end
        });

        it('should calculate P99 percentile', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add 100 metrics
                for (let i = 1; i <= 100; i++) {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p99',
                        value: i * 10,
                    });
                }
            });

            const p99 = result.current.calculatePercentile(serviceId, '1h', 99);
            expect(p99).toBeGreaterThanOrEqual(980); // 99th percentile should be very high
        });

        it('should get latency distribution', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add metrics
                for (let i = 1; i <= 50; i++) {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p50',
                        value: i * 20,
                    });
                }
            });

            const distribution = result.current.getLatencyDistribution(serviceId, '1h');
            expect(distribution).toHaveProperty('p50');
            expect(distribution).toHaveProperty('p75');
            expect(distribution).toHaveProperty('p90');
            expect(distribution).toHaveProperty('p95');
            expect(distribution).toHaveProperty('p99');
            expect(distribution.p95).toBeGreaterThan(distribution.p50);
        });

        it('should calculate average latency', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add metrics: 100, 200, 300, 400, 500 (avg = 300)
                [100, 200, 300, 400, 500].forEach((value) => {
                    result.current.addMetric({
                        serviceId,
                        type: 'latency_p50',
                        value,
                    });
                });
            });

            const avgLatency = result.current.getAverageLatency(serviceId, '1h');
            expect(avgLatency).toBe(300);
        });
    });

    // ===========================
    // Bottleneck Detection Tests (5)
    // ===========================

    describe('Bottleneck Detection', () => {
        it('should detect high latency bottleneck', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                // Add metric exceeding SLO by 50%
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 800, // Default SLO is 500ms, so this is 60% over
                });
            });

            const bottlenecks = result.current.detectBottlenecks();
            expect(bottlenecks.length).toBeGreaterThan(0);

            const bottleneck = bottlenecks.find((b) => b.serviceId === serviceId);
            expect(bottleneck).toBeTruthy();
            expect(bottleneck?.severity).toBe('high');
            expect(bottleneck?.reason).toContain('P95 latency');
        });

        it('should detect critical latency bottleneck', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add metric exceeding SLO by 100%
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 1200, // Default SLO is 500ms, so this is 140% over
                });
            });

            const bottlenecks = result.current.detectBottlenecks();
            const bottleneck = bottlenecks.find((b) => b.serviceId === serviceId);

            expect(bottleneck).toBeTruthy();
            expect(bottleneck?.severity).toBe('critical');
        });

        it('should detect high error rate bottleneck', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                // Add error rate exceeding SLO by 100%
                result.current.addMetric({
                    serviceId,
                    type: 'error_rate',
                    value: 3.0, // Default SLO is 1.0%, so this is 200% of target
                });
            });

            const bottlenecks = result.current.detectBottlenecks();
            const bottleneck = bottlenecks.find((b) => b.serviceId === serviceId);

            expect(bottleneck).toBeTruthy();
            expect(bottleneck?.reason).toContain('Error rate');
        });

        it('should get bottlenecks by service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let service1Id: string;
            let service2Id: string;
            act(() => {
                service1Id = result.current.addService({
                    name: 'Service 1',
                    type: 'api',
                    endpoint: 'https://api1.example.com',
                });

                service2Id = result.current.addService({
                    name: 'Service 2',
                    type: 'api',
                    endpoint: 'https://api2.example.com',
                });

                // Add high latency for service 1
                result.current.addMetric({
                    serviceId: service1Id,
                    type: 'latency_p95',
                    value: 800,
                });

                // Add normal latency for service 2
                result.current.addMetric({
                    serviceId: service2Id,
                    type: 'latency_p95',
                    value: 200,
                });
            });

            result.current.detectBottlenecks();
            const service1Bottlenecks = result.current.getBottlenecksByService(service1Id);
            const service2Bottlenecks = result.current.getBottlenecksByService(service2Id);

            expect(service1Bottlenecks.length).toBeGreaterThan(0);
            expect(service2Bottlenecks.length).toBe(0);
        });

        it('should get critical path with dependencies', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let service1Id: string;
            let service2Id: string;
            let service3Id: string;
            act(() => {
                service1Id = result.current.addService({
                    name: 'Database',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                    dependencies: [],
                });

                service2Id = result.current.addService({
                    name: 'API',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                    dependencies: [service1Id],
                });

                service3Id = result.current.addService({
                    name: 'Cache',
                    type: 'cache',
                    endpoint: 'redis://cache.example.com:6379',
                    dependencies: [service2Id],
                });
            });

            const criticalPath = result.current.getCriticalPath();
            expect(criticalPath.length).toBeGreaterThan(0);
        });
    });

    // ===========================
    // Query Analysis Tests (4)
    // ===========================

    describe('Query Analysis', () => {
        it('should add a query', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            let queryId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                queryId = result.current.addQuery({
                    serviceId,
                    query: 'SELECT * FROM users WHERE id = ?',
                    executionTime: 150,
                    rowCount: 1,
                });
            });

            expect(queryId).toBeTruthy();
        });

        it('should get slow queries above threshold', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                // Add queries with different execution times
                result.current.addQuery({
                    serviceId,
                    query: 'SELECT * FROM users',
                    executionTime: 50,
                });

                result.current.addQuery({
                    serviceId,
                    query: 'SELECT * FROM orders',
                    executionTime: 150,
                });

                result.current.addQuery({
                    serviceId,
                    query: 'SELECT * FROM products',
                    executionTime: 250,
                });
            });

            const slowQueries = result.current.getSlowQueries(100);
            expect(slowQueries).toHaveLength(2); // 150ms and 250ms queries
            expect(slowQueries.every((q) => q.executionTime >= 100)).toBe(true);
        });

        it('should get query statistics', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            let queryId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                // Add same query multiple times with different execution times
                const query = 'SELECT * FROM users WHERE active = true';
                queryId = result.current.addQuery({
                    serviceId,
                    query,
                    executionTime: 100,
                });

                result.current.addQuery({
                    serviceId,
                    query,
                    executionTime: 150,
                });

                result.current.addQuery({
                    serviceId,
                    query,
                    executionTime: 200,
                });
            });

            const stats = result.current.getQueryStatistics(queryId);
            expect(stats.executionCount).toBe(3);
            expect(stats.avgExecutionTime).toBe(150); // (100 + 150 + 200) / 3
            expect(stats.p95ExecutionTime).toBeGreaterThanOrEqual(190);
        });

        it('should filter slow queries by service', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let service1Id: string;
            let service2Id: string;
            act(() => {
                service1Id = result.current.addService({
                    name: 'Database 1',
                    type: 'database',
                    endpoint: 'postgresql://db1.example.com:5432',
                });

                service2Id = result.current.addService({
                    name: 'Database 2',
                    type: 'database',
                    endpoint: 'postgresql://db2.example.com:5432',
                });

                result.current.addQuery({
                    serviceId: service1Id,
                    query: 'SELECT * FROM users',
                    executionTime: 150,
                });

                result.current.addQuery({
                    serviceId: service2Id,
                    query: 'SELECT * FROM orders',
                    executionTime: 200,
                });
            });

            const allSlowQueries = result.current.getSlowQueries(100);
            expect(allSlowQueries).toHaveLength(2);

            const service1Queries = allSlowQueries.filter((q) => q.serviceId === service1Id);
            const service2Queries = allSlowQueries.filter((q) => q.serviceId === service2Id);

            expect(service1Queries).toHaveLength(1);
            expect(service2Queries).toHaveLength(1);
        });
    });

    // ===========================
    // Optimization Tests (3)
    // ===========================

    describe('Optimization', () => {
        it('should generate optimization recommendations for high latency database', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 800, // Exceeds default SLO
                });
            });

            const recommendations = result.current.generateOptimizationRecommendations();
            expect(recommendations.length).toBeGreaterThan(0);

            const dbRecommendation = recommendations.find((r) => r.serviceId === serviceId);
            expect(dbRecommendation).toBeTruthy();
            expect(dbRecommendation?.recommendation).toContain('index');
            expect(dbRecommendation?.priority).toMatch(/high|critical/);
        });

        it('should generate optimization recommendations for high latency API', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 700, // Exceeds default SLO
                });
            });

            const recommendations = result.current.generateOptimizationRecommendations();
            const apiRecommendation = recommendations.find((r) => r.serviceId === serviceId);

            expect(apiRecommendation).toBeTruthy();
            expect(apiRecommendation?.recommendation).toContain('caching');
        });

        it('should generate recommendations for high error rate', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'error_rate',
                    value: 5.0, // Exceeds default SLO of 1.0%
                });
            });

            const recommendations = result.current.generateOptimizationRecommendations();
            const errorRecommendation = recommendations.find((r) => r.serviceId === serviceId);

            expect(errorRecommendation).toBeTruthy();
            expect(errorRecommendation?.recommendation).toContain('circuit breaker');
            expect(errorRecommendation?.priority).toBe('critical');
        });

        it('should filter recommendations by priority', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            act(() => {
                const service1Id = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                const service2Id = result.current.addService({
                    name: 'Database Service',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                });

                // High latency for critical priority
                result.current.addMetric({
                    serviceId: service1Id,
                    type: 'latency_p95',
                    value: 1200,
                });

                // Moderate latency for medium priority
                result.current.addMetric({
                    serviceId: service2Id,
                    type: 'latency_p95',
                    value: 650,
                });
            });

            const criticalRecommendations = result.current.getRecommendationsByPriority('critical');
            const highRecommendations = result.current.getRecommendationsByPriority('high');

            expect(criticalRecommendations.length + highRecommendations.length).toBeGreaterThan(0);
        });
    });

    // ===========================
    // Comparison Tests (3)
    // ===========================

    describe('Comparison', () => {
        it('should create a snapshot', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let snapshotId: string;
            act(() => {
                const serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 300,
                });

                snapshotId = result.current.createSnapshot();
            });

            expect(snapshotId).toBeTruthy();
        });

        it('should compare two snapshots', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let snapshot1Id: string;
            let snapshot2Id: string;
            act(() => {
                const serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 300,
                });

                snapshot1Id = result.current.createSnapshot();

                // Add more metrics for second snapshot
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 250, // Improvement
                });

                snapshot2Id = result.current.createSnapshot();
            });

            const comparison = result.current.compareSnapshots(snapshot1Id, snapshot2Id);
            expect(comparison.services).toBeDefined();
            expect(comparison.latencyChanges).toBeDefined();
        });

        it('should calculate latency changes in snapshot comparison', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let snapshot1Id: string;
            let snapshot2Id: string;
            let serviceId: string;
            act(() => {
                serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 400,
                });

                snapshot1Id = result.current.createSnapshot();

                // Add improved metrics
                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 300, // 25% improvement
                });

                snapshot2Id = result.current.createSnapshot();
            });

            const comparison = result.current.compareSnapshots(snapshot1Id, snapshot2Id);
            const latencyChange = comparison.latencyChanges[serviceId];

            if (latencyChange) {
                expect(latencyChange.before).toBe(400);
                expect(latencyChange.after).toBe(300);
                expect(latencyChange.change).toBe(-25); // 25% improvement
            }
        });
    });

    // ===========================
    // Export Tests (2)
    // ===========================

    describe('Export', () => {
        it('should export report in JSON format', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            act(() => {
                const serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 300,
                });
            });

            const jsonReport = result.current.exportReport('json');
            expect(jsonReport).toBeTruthy();

            const parsed = JSON.parse(jsonReport);
            expect(parsed.system).toBeDefined();
            expect(parsed.services).toBeDefined();
            expect(parsed.metrics).toBeDefined();
            expect(parsed.timestamp).toBeDefined();
        });

        it('should export report in CSV format', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            act(() => {
                const serviceId = result.current.addService({
                    name: 'API Service',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p50',
                    value: 100,
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p95',
                    value: 300,
                });

                result.current.addMetric({
                    serviceId,
                    type: 'latency_p99',
                    value: 500,
                });
            });

            const csvReport = result.current.exportReport('csv');
            expect(csvReport).toBeTruthy();
            expect(csvReport).toContain('Service,Type,P50,P95,P99');
            expect(csvReport).toContain('API Service');
        });
    });

    // ===========================
    // Complex Performance Scenario (1)
    // ===========================

    describe('Complex Performance Scenario', () => {
        it('should handle complex multi-service performance analysis', () => {
            const { result } = renderHook(() => usePerformanceAnalysis());

            let databaseId: string;
            let apiId: string;
            let cacheId: string;
            act(() => {
                // Set up multi-tier architecture
                databaseId = result.current.addService({
                    name: 'PostgreSQL Database',
                    type: 'database',
                    endpoint: 'postgresql://db.example.com:5432',
                    dependencies: [],
                });

                cacheId = result.current.addService({
                    name: 'Redis Cache',
                    type: 'cache',
                    endpoint: 'redis://cache.example.com:6379',
                    dependencies: [databaseId],
                });

                apiId = result.current.addService({
                    name: 'REST API',
                    type: 'api',
                    endpoint: 'https://api.example.com',
                    dependencies: [cacheId, databaseId],
                });

                // Add performance metrics
                // Database: High latency, needs optimization
                result.current.addMetric({ serviceId: databaseId, type: 'latency_p50', value: 150 });
                result.current.addMetric({ serviceId: databaseId, type: 'latency_p95', value: 800 });
                result.current.addMetric({ serviceId: databaseId, type: 'latency_p99', value: 1200 });
                result.current.addMetric({ serviceId: databaseId, type: 'error_rate', value: 0.5 });
                result.current.addMetric({ serviceId: databaseId, type: 'throughput', value: 500 });

                // Cache: Good performance
                result.current.addMetric({ serviceId: cacheId, type: 'latency_p50', value: 5 });
                result.current.addMetric({ serviceId: cacheId, type: 'latency_p95', value: 20 });
                result.current.addMetric({ serviceId: cacheId, type: 'latency_p99', value: 50 });
                result.current.addMetric({ serviceId: cacheId, type: 'error_rate', value: 0.1 });

                // API: Moderate latency
                result.current.addMetric({ serviceId: apiId, type: 'latency_p50', value: 100 });
                result.current.addMetric({ serviceId: apiId, type: 'latency_p95', value: 400 });
                result.current.addMetric({ serviceId: apiId, type: 'latency_p99', value: 800 });
                result.current.addMetric({ serviceId: apiId, type: 'error_rate', value: 1.2 });
                result.current.addMetric({ serviceId: apiId, type: 'throughput', value: 1000 });

                // Add slow queries
                result.current.addQuery({
                    serviceId: databaseId,
                    query: 'SELECT * FROM users WHERE created_at > ?',
                    executionTime: 850,
                    rowCount: 10000,
                });

                result.current.addQuery({
                    serviceId: databaseId,
                    query: 'SELECT COUNT(*) FROM orders GROUP BY user_id',
                    executionTime: 1200,
                    rowCount: 5000,
                });
            });

            // Validate services
            const services = result.current.getServices();
            expect(services).toHaveLength(3);

            // Validate metrics
            const allMetrics = result.current.getMetrics();
            expect(allMetrics.length).toBeGreaterThan(10);

            // Validate SLO violations
            expect(result.current.isSLOViolated(databaseId)).toBe(true); // High P95
            expect(result.current.isSLOViolated(cacheId)).toBe(false); // Good performance
            expect(result.current.isSLOViolated(apiId)).toBe(true); // High error rate

            // Validate bottleneck detection
            const bottlenecks = result.current.detectBottlenecks();
            expect(bottlenecks.length).toBeGreaterThan(0);

            const databaseBottlenecks = result.current.getBottlenecksByService(databaseId);
            expect(databaseBottlenecks.length).toBeGreaterThan(0);
            expect(databaseBottlenecks.some((b) => b.severity === 'high' || b.severity === 'critical')).toBe(true);

            // Validate slow query detection
            const slowQueries = result.current.getSlowQueries(100);
            expect(slowQueries).toHaveLength(2);
            expect(slowQueries.every((q) => q.serviceId === databaseId)).toBe(true);

            // Validate optimization recommendations
            const recommendations = result.current.generateOptimizationRecommendations();
            expect(recommendations.length).toBeGreaterThan(0);

            const databaseRecommendations = recommendations.filter((r) => r.serviceId === databaseId);
            expect(databaseRecommendations.length).toBeGreaterThan(0);
            expect(databaseRecommendations.some((r) => r.recommendation.includes('index'))).toBe(true);

            const apiRecommendations = recommendations.filter((r) => r.serviceId === apiId);
            expect(apiRecommendations.length).toBeGreaterThan(0);

            // Validate critical path
            const criticalPath = result.current.getCriticalPath();
            expect(criticalPath.length).toBeGreaterThan(0);

            // Validate latency calculations
            const dbP95 = result.current.calculatePercentile(databaseId, '1h', 95);
            expect(dbP95).toBeGreaterThan(700);

            const dbDistribution = result.current.getLatencyDistribution(databaseId, '1h');
            expect(dbDistribution.p95).toBeGreaterThan(dbDistribution.p50);

            // Validate SLO compliance
            const dbCompliance = result.current.getSLOCompliance(databaseId, '1h');
            expect(dbCompliance).toBeLessThan(100);

            // Create snapshot and validate
            const snapshotId = result.current.createSnapshot();
            expect(snapshotId).toBeTruthy();

            // Improve database performance
            act(() => {
                result.current.addMetric({ serviceId: databaseId, type: 'latency_p95', value: 400 }); // Improved
            });

            const snapshot2Id = result.current.createSnapshot();
            const comparison = result.current.compareSnapshots(snapshotId, snapshot2Id);
            expect(comparison.latencyChanges[databaseId]).toBeDefined();

            // Validate export
            const jsonReport = result.current.exportReport('json');
            const parsed = JSON.parse(jsonReport);
            expect(parsed.services).toHaveLength(3);

            const csvReport = result.current.exportReport('csv');
            expect(csvReport).toContain('PostgreSQL Database');
            expect(csvReport).toContain('Redis Cache');
            expect(csvReport).toContain('REST API');
        });
    });
});
