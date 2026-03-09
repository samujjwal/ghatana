/**
 * System Dashboard Canvas Content
 * 
 * System health dashboard for Observe × System level.
 * Monitor system-wide metrics and health.
 * 
 * @doc.type component
 * @doc.purpose System health monitoring dashboard
 * @doc.layer product
 * @doc.pattern Component
 */

import { useState, useMemo } from 'react';
import { BaseCanvasContent } from '../BaseCanvasContent';
import { Box, Typography, Surface as Paper } from '@ghatana/ui';

interface SystemMetric {
    id: string;
    name: string;
    value: number;
    unit: string;
    status: 'healthy' | 'warning' | 'critical';
    trend: 'up' | 'down' | 'stable';
    threshold: number;
}

interface ServiceStatus {
    id: string;
    name: string;
    status: 'healthy' | 'degraded' | 'down';
    uptime: number;
    latency: number;
    errorRate: number;
}

const MOCK_METRICS: SystemMetric[] = [
    { id: '1', name: 'CPU Usage', value: 45, unit: '%', status: 'healthy', trend: 'stable', threshold: 80 },
    { id: '2', name: 'Memory Usage', value: 68, unit: '%', status: 'warning', trend: 'up', threshold: 75 },
    { id: '3', name: 'Disk Usage', value: 52, unit: '%', status: 'healthy', trend: 'up', threshold: 85 },
    { id: '4', name: 'Network I/O', value: 245, unit: 'MB/s', status: 'healthy', trend: 'stable', threshold: 500 },
    { id: '5', name: 'Request Rate', value: 1250, unit: 'req/s', status: 'healthy', trend: 'up', threshold: 2000 },
    { id: '6', name: 'Error Rate', value: 2.4, unit: '%', status: 'critical', trend: 'up', threshold: 1 },
];

const MOCK_SERVICES: ServiceStatus[] = [
    { id: '1', name: 'API Gateway', status: 'healthy', uptime: 99.98, latency: 45, errorRate: 0.2 },
    { id: '2', name: 'Auth Service', status: 'healthy', uptime: 99.95, latency: 32, errorRate: 0.5 },
    { id: '3', name: 'Database', status: 'healthy', uptime: 99.99, latency: 12, errorRate: 0.1 },
    { id: '4', name: 'Cache', status: 'degraded', uptime: 98.5, latency: 8, errorRate: 1.2 },
    { id: '5', name: 'Message Queue', status: 'healthy', uptime: 99.92, latency: 5, errorRate: 0.3 },
];

const getStatusColor = (status: 'healthy' | 'warning' | 'critical' | 'degraded' | 'down') => {
    switch (status) {
        case 'healthy':
            return '#10B981';
        case 'warning':
        case 'degraded':
            return '#F59E0B';
        case 'critical':
        case 'down':
            return '#EF4444';
    }
};

const getTrendIcon = (trend: 'up' | 'down' | 'stable') => {
    switch (trend) {
        case 'up':
            return '📈';
        case 'down':
            return '📉';
        case 'stable':
            return '➡️';
    }
};

export const SystemDashboardCanvas = () => {
    const [metrics] = useState<SystemMetric[]>(MOCK_METRICS);
    const [services] = useState<ServiceStatus[]>(MOCK_SERVICES);

    const stats = useMemo(() => {
        return {
            healthyMetrics: metrics.filter(m => m.status === 'healthy').length,
            warningMetrics: metrics.filter(m => m.status === 'warning').length,
            criticalMetrics: metrics.filter(m => m.status === 'critical').length,
            healthyServices: services.filter(s => s.status === 'healthy').length,
            degradedServices: services.filter(s => s.status === 'degraded').length,
            downServices: services.filter(s => s.status === 'down').length,
        };
    }, [metrics, services]);

    const hasContent = metrics.length > 0 || services.length > 0;

    return (
        <BaseCanvasContent hasContent={hasContent}>
            <Box className="h-full flex flex-col bg-[#fafafa] p-4">
                <Typography variant="h6" className="font-semibold mb-4">
                    System Health Dashboard
                </Typography>

                <Box className="grid gap-4 mb-4" >
                    {metrics.map(metric => (
                        <Paper key={metric.id} elevation={2} className="p-4">
                            <Box className="flex justify-between mb-2" style={{ alignItems: 'start', gridTemplateColumns: 'repeat(3 }} >
                                <Typography variant="caption" color="text.secondary" className="font-semibold">
                                    {metric.name}
                                </Typography>
                                <Typography fontSize="1rem">{getTrendIcon(metric.trend)}</Typography>
                            </Box>
                            <Typography variant="h5" className="font-bold mb-1">
                                {metric.value}
                                <Typography component="span" variant="body2" color="text.secondary">
                                    {' '}
                                    {metric.unit}
                                </Typography>
                            </Typography>
                            <Box
                                className="w-full rounded overflow-hidden h-[8px] bg-[#E5E7EB] mb-1"
                            >
                                <Box
                                    style={{ width: `${Math.min((metric.value / metric.threshold) * 100, backgroundColor: getStatusColor(service.status) }}
                                />
                            </Box>
                            <Typography variant="caption" color="text.secondary">
                                Threshold: {metric.threshold} {metric.unit}
                            </Typography>
                        </Paper>
                    ))}
                </Box>

                <Typography variant="subtitle2" className="font-semibold mb-2">
                    Service Status
                </Typography>
                <Box className="overflow-y-auto">
                    {services.map(service => (
                        <Paper key={service.id} elevation={2} className="p-3 mb-2">
                            <Box className="flex items-center gap-4">
                                <Box
                                    className="rounded-full shrink-0 w-[12px] h-[12px]" />
                                <Box className="flex-1">
                                    <Typography variant="body2" className="font-semibold mb-[2.4px]">
                                        {service.name}
                                    </Typography>
                                    <Box className="flex gap-4">
                                        <Typography variant="caption" color="text.secondary">
                                            Uptime: {service.uptime}%
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Latency: {service.latency}ms
                                        </Typography>
                                        <Typography variant="caption" color="text.secondary">
                                            Errors: {service.errorRate}%
                                        </Typography>
                                    </Box>
                                </Box>
                            </Box>
                        </Paper>
                    ))}
                </Box>

                <Box
                    className="absolute rounded top-[16px] right-[16px] bg-white p-4 shadow"
                >
                    <Typography variant="subtitle2" className="font-semibold mb-2">
                        System Summary
                    </Typography>
                    <Typography variant="caption" display="block">
                        Metrics: {metrics.length}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('healthy') }}>
                        Healthy: {stats.healthyMetrics}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('warning') }}>
                        Warning: {stats.warningMetrics}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('critical') }}>
                        Critical: {stats.criticalMetrics}
                    </Typography>
                    <Typography variant="caption" display="block" className="mt-2">
                        Services: {services.length}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('healthy') }}>
                        Healthy: {stats.healthyServices}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('degraded') }}>
                        Degraded: {stats.degradedServices}
                    </Typography>
                    <Typography variant="caption" display="block" style={{ color: getStatusColor('down') }}>
                        Down: {stats.downServices}
                    </Typography>
                </Box>
            </Box>
        </BaseCanvasContent>
    );
};

export default SystemDashboardCanvas;
