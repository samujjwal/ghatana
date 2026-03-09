/**
 * Platform Health Component
 *
 * Displays real-time health metrics of the platform.
 *
 * @package @ghatana/software-org-web
 */

import React, { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
    Box,
    Card,
    Grid,
    KpiCard,
    Typography,
    Chip,
    Progress,
} from '@ghatana/ui';
import { io, Socket } from 'socket.io-client';

interface HealthData {
    status: string;
    uptime: number;
    metrics: {
        totalTenants: number;
        totalUsers: number;
        activeWorkspaces: number;
        apiLatency: number;
        errorRate: number;
    };
    services: {
        name: string;
        status: 'operational' | 'degraded' | 'down';
        message?: string;
    }[];
}

interface AggregatedAlertsData {
    summary: {
        totalAlerts: number;
        criticalCount: number;
        highCount: number;
        activeCount: number;
    };
    byTenant: Array<{
        tenantId: string;
        tenantKey: string;
        tenantName: string;
        totalAlerts: number;
        criticalCount: number;
        activeCount: number;
    }>;
    recentAlerts: Array<{
        id: string;
        tenantKey: string;
        severity: string;
        status: string;
        title: string;
        message: string;
        createdAt: string;
    }>;
}

export const PlatformHealth: React.FC = () => {
    const [liveAlertCount, setLiveAlertCount] = useState(0);
    const [socket, setSocket] = useState<Socket | null>(null);

    const { data: health, isLoading } = useQuery<HealthData>({
        queryKey: ['/api/v1/root/health'],
        queryFn: async () => {
            const res = await fetch('/api/v1/root/health');
            if (!res.ok) throw new Error('Failed to fetch health');
            return res.json();
        },
    });

    const { data: aggregatedAlerts } = useQuery<AggregatedAlertsData>({
        queryKey: ['/api/v1/root/alerts/aggregated'],
        queryFn: async () => {
            const res = await fetch('/api/v1/root/alerts/aggregated?limit=50');
            if (!res.ok) throw new Error('Failed to fetch aggregated alerts');
            return res.json();
        },
        refetchInterval: 30000, // Refresh every 30s
    });

    // WebSocket connection for real-time alerts (cross-tenant view)
    useEffect(() => {
        const newSocket = io('/observe/alerts', {
            transports: ['websocket', 'polling'],
        });

        newSocket.on('connect', () => {
            console.log('[Root] Connected to alerts stream');
            // Subscribe to all tenants (root view)
            newSocket.emit('subscribe', { tenantId: 'all', filters: {} });
        });

        newSocket.on('alerts:new', (data: { alert: { severity: string } }) => {
            setLiveAlertCount((prev) => prev + 1);
            if (data.alert.severity === 'critical' || data.alert.severity === 'high') {
                // Could trigger toast notification here
                console.log('[Root] New high-severity alert:', data.alert);
            }
        });

        newSocket.on('disconnect', () => {
            console.log('[Root] Disconnected from alerts stream');
        });

        setSocket(newSocket);

        return () => {
            newSocket.close();
        };
    }, []);

    if (isLoading) return <Progress variant="linear" value={0} indeterminate />;

    if (!health) return null;

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'operational': return 'success';
            case 'degraded': return 'warning';
            case 'down': return 'error';
            default: return 'default';
        }
    };

    return (
        <Box>
            {/* KPI Grid */}
            <Grid container spacing={3} className="mb-6">
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="System Status"
                        value={health.status.toUpperCase()}
                        trend="neutral"
                        color={getStatusColor(health.status === 'healthy' ? 'operational' : 'degraded')}
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Uptime (30d)"
                        value={`${health.uptime}%`}
                        trend="up"
                        trendValue="0.01%"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="API Latency"
                        value={`${health.metrics.apiLatency}ms`}
                        trend="down"
                        trendValue="5ms"
                    />
                </Grid>
                <Grid item xs={12} sm={6} md={3}>
                    <KpiCard
                        title="Error Rate"
                        value={`${health.metrics.errorRate}%`}
                        trend="neutral"
                    />
                </Grid>
            </Grid>

            {/* Service Status */}
            <Card className="p-6">
                <Typography variant="h6" className="mb-4 font-bold text-slate-900 dark:text-neutral-100">
                    Service Status
                </Typography>
                <Grid container spacing={2}>
                    {health.services.map((service) => (
                        <Grid item xs={12} md={6} key={service.name}>
                            <Box className="flex items-center justify-between p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-700">
                                <Box>
                                    <Typography variant="subtitle2" className="font-medium text-slate-900 dark:text-neutral-100">
                                        {service.name}
                                    </Typography>
                                    {service.message && (
                                        <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                            {service.message}
                                        </Typography>
                                    )}
                                </Box>
                                <Chip
                                    label={service.status.toUpperCase()}
                                    color={getStatusColor(service.status)}
                                    size="small"
                                />
                            </Box>
                        </Grid>
                    ))}
                </Grid>
            </Card>

            {/* Cross-Tenant Alerts Aggregation */}
            {aggregatedAlerts && (
                <Card className="p-6 mt-6">
                    <Box className="flex items-center justify-between mb-4">
                        <Typography variant="h6" className="font-bold text-slate-900 dark:text-neutral-100">
                            Cross-Tenant Alerts
                        </Typography>
                        {socket?.connected && (
                            <Chip label="Live" color="success" size="small" />
                        )}
                    </Box>

                    {/* Alerts Summary */}
                    <Grid container spacing={2} className="mb-4">
                        <Grid item xs={6} md={3}>
                            <Box className="p-3 bg-slate-50 dark:bg-neutral-800 rounded">
                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                    Total Alerts
                                </Typography>
                                <Typography variant="h5" className="font-bold text-slate-900 dark:text-neutral-100">
                                    {aggregatedAlerts.summary.totalAlerts + liveAlertCount}
                                </Typography>
                            </Box>
                        </Grid>
                        <Grid item xs={6} md={3}>
                            <Box className="p-3 bg-red-50 dark:bg-red-900/20 rounded">
                                <Typography variant="caption" className="text-red-600 dark:text-red-400">
                                    Critical
                                </Typography>
                                <Typography variant="h5" className="font-bold text-red-700 dark:text-red-300">
                                    {aggregatedAlerts.summary.criticalCount}
                                </Typography>
                            </Box>
                        </Grid>
                        <Grid item xs={6} md={3}>
                            <Box className="p-3 bg-orange-50 dark:bg-orange-900/20 rounded">
                                <Typography variant="caption" className="text-orange-600 dark:text-orange-400">
                                    High
                                </Typography>
                                <Typography variant="h5" className="font-bold text-orange-700 dark:text-orange-300">
                                    {aggregatedAlerts.summary.highCount}
                                </Typography>
                            </Box>
                        </Grid>
                        <Grid item xs={6} md={3}>
                            <Box className="p-3 bg-blue-50 dark:bg-blue-900/20 rounded">
                                <Typography variant="caption" className="text-blue-600 dark:text-blue-400">
                                    Active
                                </Typography>
                                <Typography variant="h5" className="font-bold text-blue-700 dark:text-blue-300">
                                    {aggregatedAlerts.summary.activeCount}
                                </Typography>
                            </Box>
                        </Grid>
                    </Grid>

                    {/* Recent Alerts */}
                    <Typography variant="subtitle2" className="mb-2 font-semibold text-slate-900 dark:text-neutral-100">
                        Recent Alerts
                    </Typography>
                    <Box className="space-y-2">
                        {aggregatedAlerts.recentAlerts.map((alert) => (
                            <Box
                                key={alert.id}
                                className="flex items-start gap-3 p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-700"
                            >
                                <Chip
                                    label={alert.severity.toUpperCase()}
                                    color={getStatusColor(alert.severity === 'critical' ? 'down' : alert.severity === 'high' ? 'degraded' : 'operational')}
                                    size="small"
                                />
                                <Box className="flex-1">
                                    <Typography variant="subtitle2" className="font-medium text-slate-900 dark:text-neutral-100">
                                        {alert.title}
                                    </Typography>
                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                        {alert.tenantKey} • {new Date(alert.createdAt).toLocaleString()}
                                    </Typography>
                                </Box>
                                <Chip
                                    label={alert.status.toUpperCase()}
                                    size="small"
                                    variant="outlined"
                                />
                            </Box>
                        ))}
                    </Box>
                </Card>
            )}
        </Box>
    );
};
