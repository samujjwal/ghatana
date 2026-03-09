/**
 * Performance Analysis Component
 * 
 * @doc.type component
 * @doc.purpose Performance profiling and optimization analysis with latency tracking and SLO validation
 * @doc.layer product
 * @doc.pattern Presentation Component
 * 
 * Features:
 * - Latency metrics overlay (P50, P95, P99 per service)
 * - Query profiler (SQL execution plans, slow query detection)
 * - SLO validation (target vs actual latency comparison)
 * - Bottleneck detection (automatic identification of slow components)
 * - Optimization recommendations (AI-powered suggestions)
 * - Performance timeline (historical trends)
 * - Comparison view (before/after optimization)
 * - Heat map visualization (color-coded latency)
 * - Export capabilities (CSV, JSON reports)
 * 
 * @example
 * ```tsx
 * <PerformanceAnalysis systemName="E-commerce Platform" />
 * ```
 */

import React, { useState } from 'react';
import {
  Button,
  Card,
  CardContent,
  CardHeader,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Typography,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { usePerformanceAnalysis } from '../hooks/usePerformanceAnalysis';
import type {
    MetricType,
    TimeRange,
    OptimizationPriority,
} from '../hooks/usePerformanceAnalysis';

interface PerformanceAnalysisProps {
    /**
     * System name displayed in the header
     */
    systemName?: string;
}

/**
 * Performance Analysis Component
 * 
 * Comprehensive performance profiling tool providing real-time latency monitoring,
 * SLO validation, bottleneck detection, and AI-powered optimization recommendations
 * for distributed systems and microservices.
 * 
 * @param props - Component props
 * @returns Performance Analysis component
 */
export const PerformanceAnalysis: React.FC<PerformanceAnalysisProps> = ({
    systemName = 'Performance Analysis',
}) => {
    const {
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
    } = usePerformanceAnalysis();

    // Local UI state
    const [showAddService, setShowAddService] = useState(false);
    const [showServiceDetails, setShowServiceDetails] = useState(false);
    const [showBottleneckAnalysis, setShowBottleneckAnalysis] = useState(false);
    const [showOptimizations, setShowOptimizations] = useState(false);
    const [showComparison, setShowComparison] = useState(false);
    const [showQueryAnalysis, setShowQueryAnalysis] = useState(false);
    const [newServiceName, setNewServiceName] = useState('');
    const [newServiceType, setNewServiceType] = useState('api');
    const [comparisonSnapshot1, setComparisonSnapshot1] = useState('');
    const [comparisonSnapshot2, setComparisonSnapshot2] = useState('');

    // Get data
    const services = getServices();
    const bottlenecks = detectBottlenecks();
    const criticalPath = getCriticalPath();
    const recommendations = generateOptimizationRecommendations();
    const slowQueries = getSlowQueries(100); // Top 100ms threshold
    const serviceDetails = selectedService
        ? services.find(s => s.id === selectedService)
        : null;
    const serviceMetrics = selectedService
        ? getServiceMetrics(selectedService, selectedTimeRange)
        : [];

    // Latency color mapping
    const getLatencyColor = (latency: number, sloTarget: number): string => {
        const ratio = latency / sloTarget;
        if (ratio >= 1.5) return 'bg-red-100 text-red-800 border-red-300';
        if (ratio >= 1.2) return 'bg-orange-100 text-orange-800 border-orange-300';
        if (ratio >= 1.0) return 'bg-yellow-100 text-yellow-800 border-yellow-300';
        return 'bg-green-100 text-green-800 border-green-300';
    };

    // Priority color mapping
    const getPriorityColor = (priority: OptimizationPriority): string => {
        switch (priority) {
            case 'critical':
                return 'text-red-600';
            case 'high':
                return 'text-orange-600';
            case 'medium':
                return 'text-yellow-600';
            case 'low':
                return 'text-green-600';
            default:
                return 'text-gray-600';
        }
    };

    // Handle add service
    const handleAddService = () => {
        if (newServiceName.trim()) {
            addService({
                name: newServiceName,
                type: newServiceType as 'api' | 'database' | 'cache' | 'queue' | 'external',
                endpoint: `https://${newServiceName.toLowerCase().replace(/\s+/g, '-')}.example.com`,
            });
            setNewServiceName('');
            setNewServiceType('api');
            setShowAddService(false);
        }
    };

    // Handle service click
    const handleServiceClick = (serviceId: string) => {
        setSelectedService(serviceId);
        setShowServiceDetails(true);
    };

    // Handle create snapshot
    const handleCreateSnapshot = () => {
        const snapshotId = createSnapshot();
        alert(`Snapshot created: ${snapshotId}`);
    };

    return (
        <div className="w-full h-full p-6 space-y-6 bg-gray-50 overflow-auto">
            {/* Header */}
            <div className="flex items-center justify-between">
                <div>
                    <Typography variant="h4" className="font-bold text-gray-900">
                        Performance Analysis
                    </Typography>
                    <TextField
                        value={system}
                        onChange={(e) => setSystem(e.target.value)}
                        placeholder="System name"
                        className="mt-2 text-lg"
                    />
                </div>
                <div className="flex gap-2">
                    <Button
                        onClick={() => setShowAddService(true)}
                        className="bg-blue-600 text-white hover:bg-blue-700"
                    >
                        Add Service
                    </Button>
                    <Button
                        onClick={() => setShowBottleneckAnalysis(true)}
                        className="bg-red-600 text-white hover:bg-red-700"
                    >
                        Bottlenecks ({bottlenecks.length})
                    </Button>
                    <Button
                        onClick={() => setShowOptimizations(true)}
                        className="bg-green-600 text-white hover:bg-green-700"
                    >
                        Optimizations
                    </Button>
                    <Button
                        onClick={() => setShowQueryAnalysis(true)}
                        className="bg-purple-600 text-white hover:bg-purple-700"
                    >
                        Queries
                    </Button>
                    <Button
                        onClick={handleCreateSnapshot}
                        className="bg-orange-600 text-white hover:bg-orange-700"
                    >
                        Snapshot
                    </Button>
                    <Button
                        onClick={() => setShowComparison(true)}
                        className="bg-indigo-600 text-white hover:bg-indigo-700"
                    >
                        Compare
                    </Button>
                </div>
            </div>

            {/* Performance Overview */}
            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
                <Card className="border-l-4 border-blue-500 bg-blue-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Total Services
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-blue-700">
                            {services.length}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className="border-l-4 border-red-500 bg-red-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Bottlenecks
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-red-700">
                            {bottlenecks.length}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className="border-l-4 border-purple-500 bg-purple-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Slow Queries
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-purple-700">
                            {slowQueries.length}
                        </Typography>
                    </CardContent>
                </Card>

                <Card className="border-l-4 border-green-500 bg-green-50">
                    <CardContent className="p-4">
                        <Typography variant="caption" className="text-gray-600 uppercase tracking-wide">
                            Recommendations
                        </Typography>
                        <Typography variant="h3" className="font-bold mt-2 text-green-700">
                            {recommendations.length}
                        </Typography>
                    </CardContent>
                </Card>
            </div>

            {/* Time Range Filter */}
            <div className="flex items-center gap-2">
                <Typography variant="body2" className="font-semibold text-gray-700">
                    Time Range:
                </Typography>
                {(['1h', '6h', '24h', '7d', '30d'] as TimeRange[]).map((range) => (
                    <Button
                        key={range}
                        onClick={() => setSelectedTimeRange(range)}
                        className={
                            selectedTimeRange === range
                                ? 'bg-blue-600 text-white'
                                : 'bg-gray-200 text-gray-700 hover:bg-gray-300'
                        }
                    >
                        {range}
                    </Button>
                ))}
            </div>

            {/* Service Performance Grid */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                {services.map((service) => {
                    const metrics = getLatestMetrics(service.id);
                    const slo = getSLO(service.id);
                    const isViolated = isSLOViolated(service.id);
                    const compliance = getSLOCompliance(service.id, selectedTimeRange);
                    const p50 = metrics.find((m) => m.type === 'latency_p50')?.value || 0;
                    const p95 = metrics.find((m) => m.type === 'latency_p95')?.value || 0;
                    const p99 = metrics.find((m) => m.type === 'latency_p99')?.value || 0;
                    const errorRate = metrics.find((m) => m.type === 'error_rate')?.value || 0;
                    const throughput = metrics.find((m) => m.type === 'throughput')?.value || 0;

                    return (
                        <Card
                            key={service.id}
                            onClick={() => handleServiceClick(service.id)}
                            className={`cursor-pointer hover:shadow-lg transition-shadow border-2 ${isViolated ? 'border-red-500 bg-red-50' : 'border-gray-200 bg-white'
                                }`}
                        >
                            <CardHeader className="border-b bg-gray-50">
                                <div className="flex items-center justify-between">
                                    <div>
                                        <Typography variant="h6" className="font-bold">
                                            {service.name}
                                        </Typography>
                                        <Typography variant="caption" className="text-gray-600">
                                            {service.type.toUpperCase()}
                                        </Typography>
                                    </div>
                                    {isViolated && (
                                        <span className="px-2 py-1 bg-red-100 text-red-800 rounded text-xs font-semibold">
                                            SLO VIOLATED
                                        </span>
                                    )}
                                </div>
                            </CardHeader>
                            <CardContent className="p-4 space-y-3">
                                {/* Latency Percentiles */}
                                <div>
                                    <Typography variant="caption" className="text-gray-600 uppercase">
                                        Latency (ms)
                                    </Typography>
                                    <div className="grid grid-cols-3 gap-2 mt-1">
                                        <div className={`p-2 rounded border text-center ${getLatencyColor(p50, slo.p50Target)}`}>
                                            <div className="text-xs font-semibold">P50</div>
                                            <div className="text-sm font-bold">{p50.toFixed(0)}</div>
                                        </div>
                                        <div className={`p-2 rounded border text-center ${getLatencyColor(p95, slo.p95Target)}`}>
                                            <div className="text-xs font-semibold">P95</div>
                                            <div className="text-sm font-bold">{p95.toFixed(0)}</div>
                                        </div>
                                        <div className={`p-2 rounded border text-center ${getLatencyColor(p99, slo.p99Target)}`}>
                                            <div className="text-xs font-semibold">P99</div>
                                            <div className="text-sm font-bold">{p99.toFixed(0)}</div>
                                        </div>
                                    </div>
                                </div>

                                {/* SLO Compliance */}
                                <div>
                                    <div className="flex items-center justify-between mb-1">
                                        <Typography variant="caption" className="text-gray-600 uppercase">
                                            SLO Compliance
                                        </Typography>
                                        <Typography variant="caption" className={compliance >= 99 ? 'text-green-600' : 'text-red-600'}>
                                            {compliance.toFixed(1)}%
                                        </Typography>
                                    </div>
                                    <div className="w-full bg-gray-200 rounded-full h-2">
                                        <div
                                            className={`h-2 rounded-full ${compliance >= 99 ? 'bg-green-600' : 'bg-red-600'}`}
                                            style={{ width: `${compliance}%` }}
                                        />
                                    </div>
                                </div>

                                {/* Additional Metrics */}
                                <div className="grid grid-cols-2 gap-2 text-xs">
                                    <div>
                                        <span className="text-gray-600">Error Rate:</span>
                                        <span className={`ml-1 font-semibold ${errorRate > 1 ? 'text-red-600' : 'text-green-600'}`}>
                                            {errorRate.toFixed(2)}%
                                        </span>
                                    </div>
                                    <div>
                                        <span className="text-gray-600">Throughput:</span>
                                        <span className="ml-1 font-semibold text-blue-600">
                                            {throughput.toFixed(0)} req/s
                                        </span>
                                    </div>
                                </div>
                            </CardContent>
                        </Card>
                    );
                })}
            </div>

            {/* Critical Path Visualization */}
            {criticalPath.length > 0 && (
                <Card>
                    <CardHeader>
                        <Typography variant="h6" className="font-semibold">
                            Critical Path (Slowest Request Flow)
                        </Typography>
                    </CardHeader>
                    <CardContent>
                        <div className="flex items-center gap-3 overflow-x-auto pb-2">
                            {criticalPath.map((serviceId, index) => {
                                const service = services.find((s) => s.id === serviceId);
                                const metrics = getLatestMetrics(serviceId);
                                const latency = metrics.find((m) => m.type === 'latency_p95')?.value || 0;

                                return (
                                    <React.Fragment key={serviceId}>
                                        <div className="flex-shrink-0 p-3 bg-white rounded border-2 border-orange-300 min-w-[150px]">
                                            <Typography variant="body2" className="font-semibold">
                                                {service?.name || 'Unknown'}
                                            </Typography>
                                            <Typography variant="caption" className="text-gray-600">
                                                {service?.type}
                                            </Typography>
                                            <div className="mt-2 text-orange-600 font-bold">
                                                {latency.toFixed(0)}ms (P95)
                                            </div>
                                        </div>
                                        {index < criticalPath.length - 1 && (
                                            <div className="flex-shrink-0 text-gray-400 text-2xl">→</div>
                                        )}
                                    </React.Fragment>
                                );
                            })}
                        </div>
                        <div className="mt-3 p-3 bg-orange-50 rounded border border-orange-200">
                            <Typography variant="caption" className="text-orange-800">
                                <strong>Total Critical Path Latency:</strong>{' '}
                                {criticalPath.reduce((sum, serviceId) => {
                                    const metrics = getLatestMetrics(serviceId);
                                    return sum + (metrics.find((m) => m.type === 'latency_p95')?.value || 0);
                                }, 0).toFixed(0)}ms
                            </Typography>
                        </div>
                    </CardContent>
                </Card>
            )}

            {/* Add Service Dialog */}
            <Dialog open={showAddService} onClose={() => setShowAddService(false)}>
                <DialogTitle>Add New Service</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <TextField
                            label="Service Name"
                            value={newServiceName}
                            onChange={(e) => setNewServiceName(e.target.value)}
                            placeholder="e.g., User API"
                            fullWidth
                        />
                        <div>
                            <Typography variant="caption" className="text-gray-600 mb-1 block">
                                Service Type
                            </Typography>
                            <select
                                value={newServiceType}
                                onChange={(e) => setNewServiceType(e.target.value)}
                                className="w-full px-3 py-2 border border-gray-300 rounded"
                            >
                                <option value="api">API</option>
                                <option value="database">Database</option>
                                <option value="cache">Cache</option>
                                <option value="queue">Queue</option>
                                <option value="external">External</option>
                            </select>
                        </div>
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddService(false)}>Cancel</Button>
                    <Button onClick={handleAddService} className="bg-blue-600 text-white hover:bg-blue-700">
                        Add Service
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Service Details Dialog */}
            <Dialog open={showServiceDetails} onClose={() => setShowServiceDetails(false)} maxWidth="lg">
                <DialogTitle>Service Performance Details</DialogTitle>
                <DialogContent>
                    {serviceDetails && (
                        <div className="space-y-4">
                            <div>
                                <Typography variant="h6" className="font-semibold">
                                    {serviceDetails.name}
                                </Typography>
                                <Typography variant="body2" className="text-gray-600">
                                    {serviceDetails.endpoint}
                                </Typography>
                            </div>

                            {/* Latency Distribution */}
                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Latency Distribution ({selectedTimeRange})
                                </Typography>
                                <div className="grid grid-cols-5 gap-2">
                                    {Object.entries(getLatencyDistribution(serviceDetails.id, selectedTimeRange)).map(([percentile, value]) => (
                                        <div key={percentile} className="p-2 bg-gray-50 rounded border text-center">
                                            <div className="text-xs text-gray-600">{percentile}</div>
                                            <div className="text-sm font-bold">{value.toFixed(0)}ms</div>
                                        </div>
                                    ))}
                                </div>
                            </div>

                            {/* SLO Targets */}
                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    SLO Targets vs Actual
                                </Typography>
                                <div className="space-y-2">
                                    {(['p50', 'p95', 'p99'] as const).map((percentile) => {
                                        const slo = getSLO(serviceDetails.id);
                                        const target = slo[`${percentile}Target`];
                                        const actual = calculatePercentile(serviceDetails.id, selectedTimeRange, percentile === 'p50' ? 50 : percentile === 'p95' ? 95 : 99);
                                        const ratio = actual / target;

                                        return (
                                            <div key={percentile} className="flex items-center justify-between p-2 bg-gray-50 rounded">
                                                <span className="font-semibold uppercase">{percentile}</span>
                                                <div className="flex items-center gap-4">
                                                    <span className="text-sm">Target: {target}ms</span>
                                                    <span className={`text-sm font-bold ${ratio > 1 ? 'text-red-600' : 'text-green-600'}`}>
                                                        Actual: {actual.toFixed(0)}ms
                                                    </span>
                                                    <span className={`text-xs ${ratio > 1 ? 'text-red-600' : 'text-green-600'}`}>
                                                        {ratio > 1 ? `+${((ratio - 1) * 100).toFixed(0)}%` : `${((1 - ratio) * 100).toFixed(0)}% faster`}
                                                    </span>
                                                </div>
                                            </div>
                                        );
                                    })}
                                </div>
                            </div>

                            {/* Metric Timeline */}
                            <div>
                                <Typography variant="body2" className="font-semibold mb-2">
                                    Recent Metrics ({serviceMetrics.length} data points)
                                </Typography>
                                {serviceMetrics.length === 0 ? (
                                    <Typography variant="caption" className="text-gray-500">
                                        No metrics available for this time range
                                    </Typography>
                                ) : (
                                    <div className="max-h-40 overflow-y-auto">
                                        <div className="grid grid-cols-2 gap-2 text-xs">
                                            {serviceMetrics.slice(0, 10).map((metric, index) => (
                                                <div key={index} className="p-2 bg-gray-50 rounded">
                                                    <div className="font-semibold">{metric.type}</div>
                                                    <div className="text-gray-600">
                                                        {metric.value.toFixed(2)} • {new Date(metric.timestamp).toLocaleTimeString()}
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowServiceDetails(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Bottleneck Analysis Dialog */}
            <Dialog open={showBottleneckAnalysis} onClose={() => setShowBottleneckAnalysis(false)} maxWidth="lg">
                <DialogTitle>Bottleneck Analysis</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        {bottlenecks.length === 0 ? (
                            <div className="text-center py-8">
                                <Typography variant="body1" className="text-green-600 font-semibold">
                                    ✓ No bottlenecks detected
                                </Typography>
                                <Typography variant="caption" className="text-gray-600">
                                    All services are performing within acceptable limits
                                </Typography>
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {bottlenecks.map((bottleneck) => {
                                    const service = services.find((s) => s.id === bottleneck.serviceId);
                                    return (
                                        <div key={bottleneck.id} className="p-4 bg-red-50 rounded border-2 border-red-200">
                                            <div className="flex items-start justify-between">
                                                <div className="flex-1">
                                                    <Typography variant="body2" className="font-semibold text-red-900">
                                                        {service?.name || 'Unknown Service'}
                                                    </Typography>
                                                    <Typography variant="caption" className="text-red-700">
                                                        {bottleneck.reason}
                                                    </Typography>
                                                    <div className="mt-2 grid grid-cols-2 gap-2 text-xs">
                                                        <div>
                                                            <span className="text-gray-600">Severity:</span>
                                                            <span className={`ml-1 font-bold ${getPriorityColor(bottleneck.severity)}`}>
                                                                {bottleneck.severity.toUpperCase()}
                                                            </span>
                                                        </div>
                                                        <div>
                                                            <span className="text-gray-600">Impact:</span>
                                                            <span className="ml-1 font-semibold">{bottleneck.impact}</span>
                                                        </div>
                                                    </div>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowBottleneckAnalysis(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Optimization Recommendations Dialog */}
            <Dialog open={showOptimizations} onClose={() => setShowOptimizations(false)} maxWidth="lg">
                <DialogTitle>Optimization Recommendations</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        {recommendations.length === 0 ? (
                            <Typography variant="body2" className="text-gray-500 italic">
                                No optimization recommendations at this time
                            </Typography>
                        ) : (
                            <div className="space-y-3">
                                {recommendations.map((rec) => {
                                    const service = services.find((s) => s.id === rec.serviceId);
                                    return (
                                        <div key={rec.id} className="p-4 bg-white rounded border-2 border-gray-200">
                                            <div className="flex items-start justify-between mb-2">
                                                <Typography variant="body2" className="font-semibold">
                                                    {service?.name || 'Unknown Service'}
                                                </Typography>
                                                <span className={`text-xs font-bold uppercase ${getPriorityColor(rec.priority)}`}>
                                                    {rec.priority}
                                                </span>
                                            </div>
                                            <Typography variant="body2" className="text-gray-700 mb-2">
                                                {rec.recommendation}
                                            </Typography>
                                            <div className="grid grid-cols-2 gap-2 text-xs bg-gray-50 p-2 rounded">
                                                <div>
                                                    <span className="text-gray-600">Impact:</span>
                                                    <span className="ml-1 font-semibold text-green-600">{rec.estimatedImprovement}</span>
                                                </div>
                                                <div>
                                                    <span className="text-gray-600">Effort:</span>
                                                    <span className="ml-1 font-semibold">{rec.implementationEffort}</span>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowOptimizations(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Query Analysis Dialog */}
            <Dialog open={showQueryAnalysis} onClose={() => setShowQueryAnalysis(false)} maxWidth="lg">
                <DialogTitle>Slow Query Analysis</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        {slowQueries.length === 0 ? (
                            <Typography variant="body2" className="text-green-600">
                                ✓ No slow queries detected
                            </Typography>
                        ) : (
                            <div className="space-y-3">
                                {slowQueries.map((query) => {
                                    const service = services.find((s) => s.id === query.serviceId);
                                    const stats = getQueryStatistics(query.id);
                                    return (
                                        <div key={query.id} className="p-3 bg-purple-50 rounded border border-purple-200">
                                            <div className="flex items-start justify-between mb-2">
                                                <Typography variant="caption" className="font-semibold">
                                                    {service?.name || 'Unknown Service'}
                                                </Typography>
                                                <Typography variant="caption" className="font-bold text-purple-700">
                                                    {query.executionTime.toFixed(0)}ms
                                                </Typography>
                                            </div>
                                            <div className="bg-gray-900 text-gray-100 p-2 rounded text-xs font-mono mb-2 overflow-x-auto">
                                                {query.query}
                                            </div>
                                            <div className="grid grid-cols-3 gap-2 text-xs">
                                                <div>
                                                    <span className="text-gray-600">Avg:</span>
                                                    <span className="ml-1 font-semibold">{stats.avgExecutionTime.toFixed(0)}ms</span>
                                                </div>
                                                <div>
                                                    <span className="text-gray-600">P95:</span>
                                                    <span className="ml-1 font-semibold">{stats.p95ExecutionTime.toFixed(0)}ms</span>
                                                </div>
                                                <div>
                                                    <span className="text-gray-600">Count:</span>
                                                    <span className="ml-1 font-semibold">{stats.executionCount}</span>
                                                </div>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowQueryAnalysis(false)}>Close</Button>
                </DialogActions>
            </Dialog>

            {/* Comparison Dialog */}
            <Dialog open={showComparison} onClose={() => setShowComparison(false)} maxWidth="lg">
                <DialogTitle>Performance Comparison</DialogTitle>
                <DialogContent>
                    <div className="space-y-4">
                        <Typography variant="caption" className="text-gray-600">
                            Compare performance metrics between two snapshots
                        </Typography>
                        <div className="grid grid-cols-2 gap-4">
                            <TextField
                                label="Snapshot 1 ID"
                                value={comparisonSnapshot1}
                                onChange={(e) => setComparisonSnapshot1(e.target.value)}
                                placeholder="snapshot-xxx"
                                fullWidth
                            />
                            <TextField
                                label="Snapshot 2 ID"
                                value={comparisonSnapshot2}
                                onChange={(e) => setComparisonSnapshot2(e.target.value)}
                                placeholder="snapshot-yyy"
                                fullWidth
                            />
                        </div>
                        {comparisonSnapshot1 && comparisonSnapshot2 && (
                            <div className="mt-4">
                                <Button
                                    onClick={() => {
                                        const comparison = compareSnapshots(comparisonSnapshot1, comparisonSnapshot2);
                                        console.log('Comparison result:', comparison);
                                    }}
                                    className="bg-indigo-600 text-white hover:bg-indigo-700"
                                >
                                    Compare Snapshots
                                </Button>
                            </div>
                        )}
                    </div>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowComparison(false)}>Close</Button>
                </DialogActions>
            </Dialog>
        </div>
    );
};

export default PerformanceAnalysis;
