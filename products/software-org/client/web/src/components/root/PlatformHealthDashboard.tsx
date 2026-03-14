import { useState, useMemo } from 'react';
import {
    Box,
    Card,
    Stack,
    Grid,
    Typography,
    Chip,
    IconButton,
    Button,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Tabs,
    Tab,
    LinearProgress,
    Alert,
    Table,
    TableBody,
    TableCell,
    TableContainer,
    TableHead,
    TableRow,
    Paper,
} from '@ghatana/design-system';
import {
    Close as CloseIcon,
    TrendingUp as TrendingUpIcon,
    TrendingDown as TrendingDownIcon,
    Warning as WarningIcon,
    Error as ErrorIcon,
    CheckCircle as CheckCircleIcon,
    Speed as SpeedIcon,
    Storage as StorageIcon,
    Memory as MemoryIcon,
    CloudQueue as CloudIcon,
} from '@ghatana/design-system/icons';

/**
 * System health metric data point
 */
export interface HealthMetric {
    id: string;
    name: string;
    value: number;
    unit: string;
    status: 'healthy' | 'warning' | 'critical';
    threshold: {
        warning: number;
        critical: number;
    };
    trend: 'up' | 'down' | 'stable';
    lastUpdated: string;
}

/**
 * Alert/incident information
 */
export interface SystemAlert {
    id: string;
    severity: 'info' | 'warning' | 'error' | 'critical';
    title: string;
    description: string;
    affectedServices: string[];
    affectedTenants: number;
    triggeredAt: string;
    acknowledgedAt?: string;
    resolvedAt?: string;
    assignedTo?: string;
}

/**
 * Service status information
 */
export interface ServiceStatus {
    id: string;
    name: string;
    status: 'operational' | 'degraded' | 'outage';
    uptime: number; // percentage
    responseTime: number; // ms
    errorRate: number; // percentage
    lastIncident?: string;
}

/**
 * Platform-wide system metrics
 */
export interface SystemMetrics {
    cpu: {
        average: number;
        peak: number;
        trend: 'up' | 'down' | 'stable';
    };
    memory: {
        used: number; // GB
        total: number; // GB
        percentage: number;
    };
    storage: {
        used: number; // TB
        total: number; // TB
        percentage: number;
    };
    network: {
        inbound: number; // Mbps
        outbound: number; // Mbps
    };
    database: {
        connections: number;
        maxConnections: number;
        qps: number; // queries per second
        avgLatency: number; // ms
    };
}

export interface PlatformHealthDashboardProps {
    healthMetrics?: HealthMetric[];
    systemAlerts?: SystemAlert[];
    serviceStatuses?: ServiceStatus[];
    systemMetrics?: SystemMetrics;
    onAcknowledgeAlert?: (alertId: string) => void;
    onResolveAlert?: (alertId: string) => void;
    onInvestigateAlert?: (alertId: string) => void;
}

/**
 * Platform Health Monitoring Dashboard
 *
 * Provides platform administrators with:
 * - Real-time system health metrics
 * - Alert management and investigation
 * - Service status monitoring
 * - System resource utilization
 * - Drill-down capabilities for detailed analysis
 */
export function PlatformHealthDashboard({
    healthMetrics: propMetrics,
    systemAlerts: propAlerts,
    serviceStatuses: propStatuses,
    systemMetrics: propSystemMetrics,
    onAcknowledgeAlert,
    onResolveAlert,
    onInvestigateAlert,
}: PlatformHealthDashboardProps) {
    // Mock data for development
    const mockMetrics: HealthMetric[] = [
        {
            id: 'metric-1',
            name: 'API Response Time',
            value: 145,
            unit: 'ms',
            status: 'healthy',
            threshold: { warning: 200, critical: 500 },
            trend: 'stable',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-2',
            name: 'Database Query Latency',
            value: 38,
            unit: 'ms',
            status: 'healthy',
            threshold: { warning: 50, critical: 100 },
            trend: 'down',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-3',
            name: 'Error Rate',
            value: 0.8,
            unit: '%',
            status: 'healthy',
            threshold: { warning: 1, critical: 5 },
            trend: 'stable',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-4',
            name: 'CPU Utilization',
            value: 72,
            unit: '%',
            status: 'warning',
            threshold: { warning: 70, critical: 90 },
            trend: 'up',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-5',
            name: 'Memory Usage',
            value: 85,
            unit: '%',
            status: 'warning',
            threshold: { warning: 80, critical: 95 },
            trend: 'up',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
        {
            id: 'metric-6',
            name: 'Storage Capacity',
            value: 92,
            unit: '%',
            status: 'critical',
            threshold: { warning: 80, critical: 90 },
            trend: 'up',
            lastUpdated: '2025-12-11T12:00:00Z',
        },
    ];

    const mockAlerts: SystemAlert[] = [
        {
            id: 'alert-1',
            severity: 'critical',
            title: 'Storage capacity approaching limit',
            description: 'Platform storage is at 92% capacity. Immediate action required to prevent service disruption.',
            affectedServices: ['Database', 'File Storage', 'Backup Service'],
            affectedTenants: 45,
            triggeredAt: '2025-12-11T11:30:00Z',
        },
        {
            id: 'alert-2',
            severity: 'warning',
            title: 'High CPU utilization detected',
            description: 'CPU usage has exceeded 70% for the past 30 minutes.',
            affectedServices: ['API Gateway', 'Compute Cluster'],
            affectedTenants: 0,
            triggeredAt: '2025-12-11T11:15:00Z',
            acknowledgedAt: '2025-12-11T11:20:00Z',
            assignedTo: 'DevOps Team',
        },
        {
            id: 'alert-3',
            severity: 'warning',
            title: 'Memory usage above threshold',
            description: 'Memory usage is at 85%, approaching the critical threshold.',
            affectedServices: ['Application Servers'],
            affectedTenants: 0,
            triggeredAt: '2025-12-11T10:45:00Z',
        },
        {
            id: 'alert-4',
            severity: 'info',
            title: 'Scheduled maintenance completed',
            description: 'Database maintenance window completed successfully.',
            affectedServices: ['Database'],
            affectedTenants: 150,
            triggeredAt: '2025-12-11T06:00:00Z',
            resolvedAt: '2025-12-11T07:30:00Z',
        },
    ];

    const mockServices: ServiceStatus[] = [
        {
            id: 'service-1',
            name: 'API Gateway',
            status: 'operational',
            uptime: 99.98,
            responseTime: 145,
            errorRate: 0.8,
        },
        {
            id: 'service-2',
            name: 'Database Cluster',
            status: 'operational',
            uptime: 99.99,
            responseTime: 38,
            errorRate: 0.1,
        },
        {
            id: 'service-3',
            name: 'File Storage',
            status: 'degraded',
            uptime: 98.5,
            responseTime: 320,
            errorRate: 2.1,
            lastIncident: '2025-12-11T11:30:00Z',
        },
        {
            id: 'service-4',
            name: 'Authentication Service',
            status: 'operational',
            uptime: 99.95,
            responseTime: 95,
            errorRate: 0.3,
        },
        {
            id: 'service-5',
            name: 'Background Jobs',
            status: 'operational',
            uptime: 99.92,
            responseTime: 0,
            errorRate: 1.2,
        },
    ];

    const mockSystemMetrics: SystemMetrics = {
        cpu: {
            average: 72,
            peak: 89,
            trend: 'up',
        },
        memory: {
            used: 175,
            total: 256,
            percentage: 68,
        },
        storage: {
            used: 8.5,
            total: 10,
            percentage: 85,
        },
        network: {
            inbound: 1250,
            outbound: 850,
        },
        database: {
            connections: 450,
            maxConnections: 1000,
            qps: 2500,
            avgLatency: 38,
        },
    };

    const healthMetrics = propMetrics || mockMetrics;
    const systemAlerts = propAlerts || mockAlerts;
    const serviceStatuses = propStatuses || mockServices;
    const systemMetrics = propSystemMetrics || mockSystemMetrics;

    const [selectedAlert, setSelectedAlert] = useState<SystemAlert | null>(null);
    const [alertTab, setAlertTab] = useState(0);
    const [filterSeverity, setFilterSeverity] = useState<string>('all');

    // Filter alerts
    const filteredAlerts = useMemo(() => {
        if (filterSeverity === 'all') return systemAlerts;
        return systemAlerts.filter((alert) => alert.severity === filterSeverity);
    }, [systemAlerts, filterSeverity]);

    // Count alerts by severity
    const alertCounts = useMemo(() => {
        return {
            critical: systemAlerts.filter((a) => a.severity === 'critical' && !a.resolvedAt).length,
            warning: systemAlerts.filter((a) => a.severity === 'warning' && !a.resolvedAt).length,
            info: systemAlerts.filter((a) => a.severity === 'info' && !a.resolvedAt).length,
            total: systemAlerts.filter((a) => !a.resolvedAt).length,
        };
    }, [systemAlerts]);

    const handleAlertClick = (alert: SystemAlert) => {
        setSelectedAlert(alert);
        onInvestigateAlert?.(alert.id);
    };

    const handleCloseAlert = () => {
        setSelectedAlert(null);
        setAlertTab(0);
    };

    const handleAcknowledge = () => {
        if (selectedAlert && onAcknowledgeAlert) {
            onAcknowledgeAlert(selectedAlert.id);
            handleCloseAlert();
        }
    };

    const handleResolve = () => {
        if (selectedAlert && onResolveAlert) {
            onResolveAlert(selectedAlert.id);
            handleCloseAlert();
        }
    };

    const getStatusColor = (status: string) => {
        switch (status) {
            case 'healthy':
            case 'operational':
                return 'success';
            case 'warning':
            case 'degraded':
                return 'warning';
            case 'critical':
            case 'outage':
                return 'error';
            default:
                return 'default';
        }
    };

    const getSeverityColor = (severity: string) => {
        switch (severity) {
            case 'critical':
                return 'error';
            case 'warning':
                return 'warning';
            case 'error':
                return 'error';
            case 'info':
                return 'info';
            default:
                return 'default';
        }
    };

    const formatDateTime = (dateString: string) => {
        const date = new Date(dateString);
        const now = new Date();
        const diff = now.getTime() - date.getTime();
        const minutes = Math.floor(diff / 60000);
        const hours = Math.floor(minutes / 60);

        if (minutes < 60) return `${minutes}m ago`;
        if (hours < 24) return `${hours}h ago`;
        return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    };

    return (
        <Box sx={{ p: 3 }}>
            {/* Header */}
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 3 }}>
                <Box>
                    <Typography variant="h4" sx={{ fontWeight: 600 }}>
                        Platform Health
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        System-wide monitoring and alerting
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    <Chip
                        icon={<CheckCircleIcon />}
                        label={`${serviceStatuses.filter((s) => s.status === 'operational').length} Operational`}
                        color="success"
                    />
                    <Chip
                        icon={<WarningIcon />}
                        label={`${alertCounts.total} Active Alerts`}
                        color={alertCounts.critical > 0 ? 'error' : alertCounts.warning > 0 ? 'warning' : 'default'}
                    />
                </Stack>
            </Stack>

            {/* System Overview */}
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: systemMetrics.cpu.average > 70 ? 'warning.main' : 'success.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <SpeedIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    CPU Usage
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {systemMetrics.cpu.average}%
                                </Typography>
                                <Stack direction="row" alignItems="center" spacing={0.5}>
                                    {systemMetrics.cpu.trend === 'up' ? (
                                        <TrendingUpIcon fontSize="small" color="error" />
                                    ) : (
                                        <TrendingDownIcon fontSize="small" color="success" />
                                    )}
                                    <Typography variant="caption" color="text.secondary">
                                        Peak: {systemMetrics.cpu.peak}%
                                    </Typography>
                                </Stack>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: systemMetrics.memory.percentage > 80 ? 'warning.main' : 'info.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <MemoryIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Memory
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {systemMetrics.memory.used} GB
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    of {systemMetrics.memory.total} GB ({systemMetrics.memory.percentage}%)
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: systemMetrics.storage.percentage > 90 ? 'error.main' : systemMetrics.storage.percentage > 80 ? 'warning.main' : 'secondary.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <StorageIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Storage
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {systemMetrics.storage.used.toFixed(1)} TB
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    of {systemMetrics.storage.total} TB ({systemMetrics.storage.percentage}%)
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>

                <Grid item xs={12} sm={6} md={3}>
                    <Card sx={{ p: 2 }}>
                        <Stack direction="row" alignItems="center" spacing={2}>
                            <Box
                                sx={{
                                    width: 48,
                                    height: 48,
                                    borderRadius: 2,
                                    bgcolor: 'primary.main',
                                    display: 'flex',
                                    alignItems: 'center',
                                    justifyContent: 'center',
                                    color: 'white',
                                }}
                            >
                                <CloudIcon />
                            </Box>
                            <Box sx={{ flex: 1 }}>
                                <Typography variant="body2" color="text.secondary">
                                    Network
                                </Typography>
                                <Typography variant="h5" sx={{ fontWeight: 600 }}>
                                    {systemMetrics.network.inbound} Mbps
                                </Typography>
                                <Typography variant="caption" color="text.secondary">
                                    ↓ In / ↑ Out {systemMetrics.network.outbound} Mbps
                                </Typography>
                            </Box>
                        </Stack>
                    </Card>
                </Grid>
            </Grid>

            {/* Health Metrics */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">Health Metrics</Typography>
                </Box>
                <Grid container spacing={2} sx={{ p: 2 }}>
                    {healthMetrics.map((metric) => (
                        <Grid item xs={12} sm={6} md={4} key={metric.id}>
                            <Card variant="outlined">
                                <Box sx={{ p: 2 }}>
                                    <Stack direction="row" justifyContent="space-between" alignItems="flex-start" sx={{ mb: 1 }}>
                                        <Typography variant="subtitle2">{metric.name}</Typography>
                                        <Chip
                                            size="small"
                                            label={metric.status.toUpperCase()}
                                            color={getStatusColor(metric.status) as any}
                                        />
                                    </Stack>
                                    <Typography variant="h4" sx={{ fontWeight: 600, mb: 1 }}>
                                        {metric.value} {metric.unit}
                                    </Typography>
                                    <LinearProgress
                                        variant="determinate"
                                        value={Math.min((metric.value / metric.threshold.critical) * 100, 100)}
                                        color={getStatusColor(metric.status) as any}
                                        sx={{ mb: 1 }}
                                    />
                                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                                        <Typography variant="caption" color="text.secondary">
                                            Warning: {metric.threshold.warning} {metric.unit}
                                        </Typography>
                                        <Stack direction="row" alignItems="center" spacing={0.5}>
                                            {metric.trend === 'up' ? (
                                                <TrendingUpIcon fontSize="small" color={metric.status === 'critical' ? 'error' : 'inherit'} />
                                            ) : metric.trend === 'down' ? (
                                                <TrendingDownIcon fontSize="small" color="success" />
                                            ) : null}
                                            <Typography variant="caption" color="text.secondary">
                                                {metric.trend}
                                            </Typography>
                                        </Stack>
                                    </Stack>
                                </Box>
                            </Card>
                        </Grid>
                    ))}
                </Grid>
            </Card>

            {/* Service Status */}
            <Card sx={{ mb: 3 }}>
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                    <Typography variant="h6">Service Status</Typography>
                </Box>
                <TableContainer>
                    <Table>
                        <TableHead>
                            <TableRow>
                                <TableCell>Service</TableCell>
                                <TableCell>Status</TableCell>
                                <TableCell align="right">Uptime</TableCell>
                                <TableCell align="right">Response Time</TableCell>
                                <TableCell align="right">Error Rate</TableCell>
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {serviceStatuses.map((service) => (
                                <TableRow key={service.id}>
                                    <TableCell>
                                        <Typography variant="body2" sx={{ fontWeight: 500 }}>
                                            {service.name}
                                        </Typography>
                                    </TableCell>
                                    <TableCell>
                                        <Chip
                                            size="small"
                                            label={service.status.toUpperCase()}
                                            color={getStatusColor(service.status) as any}
                                            icon={
                                                service.status === 'operational' ? (
                                                    <CheckCircleIcon />
                                                ) : service.status === 'degraded' ? (
                                                    <WarningIcon />
                                                ) : (
                                                    <ErrorIcon />
                                                )
                                            }
                                        />
                                    </TableCell>
                                    <TableCell align="right">
                                        <Typography variant="body2">{service.uptime.toFixed(2)}%</Typography>
                                    </TableCell>
                                    <TableCell align="right">
                                        <Typography variant="body2">
                                            {service.responseTime > 0 ? `${service.responseTime} ms` : 'N/A'}
                                        </Typography>
                                    </TableCell>
                                    <TableCell align="right">
                                        <Typography
                                            variant="body2"
                                            color={service.errorRate > 5 ? 'error' : service.errorRate > 2 ? 'warning' : 'inherit'}
                                        >
                                            {service.errorRate.toFixed(1)}%
                                        </Typography>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </TableContainer>
            </Card>

            {/* Active Alerts */}
            <Card>
                <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
                    <Stack direction="row" justifyContent="space-between" alignItems="center">
                        <Typography variant="h6">Active Alerts</Typography>
                        <Stack direction="row" spacing={1}>
                            <Button
                                size="small"
                                variant={filterSeverity === 'all' ? 'contained' : 'outlined'}
                                onClick={() => setFilterSeverity('all')}
                            >
                                All ({systemAlerts.length})
                            </Button>
                            <Button
                                size="small"
                                variant={filterSeverity === 'critical' ? 'contained' : 'outlined'}
                                color="error"
                                onClick={() => setFilterSeverity('critical')}
                            >
                                Critical ({alertCounts.critical})
                            </Button>
                            <Button
                                size="small"
                                variant={filterSeverity === 'warning' ? 'contained' : 'outlined'}
                                color="warning"
                                onClick={() => setFilterSeverity('warning')}
                            >
                                Warning ({alertCounts.warning})
                            </Button>
                        </Stack>
                    </Stack>
                </Box>
                <Stack divider={<Box sx={{ borderBottom: 1, borderColor: 'divider' }} />}>
                    {filteredAlerts.map((alert) => (
                        <Box
                            key={alert.id}
                            sx={{
                                p: 2,
                                cursor: 'pointer',
                                '&:hover': { bgcolor: 'action.hover' },
                                opacity: alert.resolvedAt ? 0.6 : 1,
                            }}
                            onClick={() => handleAlertClick(alert)}
                        >
                            <Stack direction="row" spacing={2} alignItems="flex-start">
                                <Box sx={{ mt: 0.5 }}>
                                    {alert.severity === 'critical' || alert.severity === 'error' ? (
                                        <ErrorIcon color="error" />
                                    ) : alert.severity === 'warning' ? (
                                        <WarningIcon color="warning" />
                                    ) : (
                                        <CheckCircleIcon color="info" />
                                    )}
                                </Box>
                                <Box sx={{ flex: 1 }}>
                                    <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                                        <Chip
                                            size="small"
                                            label={alert.severity.toUpperCase()}
                                            color={getSeverityColor(alert.severity) as any}
                                        />
                                        {alert.acknowledgedAt && !alert.resolvedAt && (
                                            <Chip size="small" label="ACKNOWLEDGED" color="info" variant="outlined" />
                                        )}
                                        {alert.resolvedAt && <Chip size="small" label="RESOLVED" color="success" />}
                                    </Stack>
                                    <Typography variant="subtitle1" sx={{ fontWeight: 600, mb: 0.5 }}>
                                        {alert.title}
                                    </Typography>
                                    <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
                                        {alert.description}
                                    </Typography>
                                    <Stack direction="row" spacing={2}>
                                        <Typography variant="caption" color="text.secondary">
                                            Affected: {alert.affectedServices.join(', ')}
                                        </Typography>
                                        {alert.affectedTenants > 0 && (
                                            <Typography variant="caption" color="text.secondary">
                                                {alert.affectedTenants} tenants impacted
                                            </Typography>
                                        )}
                                        <Typography variant="caption" color="text.secondary">
                                            {formatDateTime(alert.triggeredAt)}
                                        </Typography>
                                    </Stack>
                                </Box>
                            </Stack>
                        </Box>
                    ))}
                </Stack>

                {filteredAlerts.length === 0 && (
                    <Box sx={{ p: 4, textAlign: 'center' }}>
                        <Typography variant="body1" color="text.secondary">
                            No alerts match your filter
                        </Typography>
                    </Box>
                )}
            </Card>

            {/* Alert Detail Dialog */}
            <Dialog open={!!selectedAlert} onClose={handleCloseAlert} maxWidth="md" fullWidth>
                {selectedAlert && (
                    <>
                        <DialogTitle>
                            <Stack direction="row" justifyContent="space-between" alignItems="center">
                                <Box>
                                    <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                                        <Chip
                                            size="small"
                                            label={selectedAlert.severity.toUpperCase()}
                                            color={getSeverityColor(selectedAlert.severity) as any}
                                        />
                                        {selectedAlert.acknowledgedAt && !selectedAlert.resolvedAt && (
                                            <Chip size="small" label="ACKNOWLEDGED" color="info" variant="outlined" />
                                        )}
                                        {selectedAlert.resolvedAt && <Chip size="small" label="RESOLVED" color="success" />}
                                    </Stack>
                                    <Typography variant="h6">{selectedAlert.title}</Typography>
                                </Box>
                                <IconButton onClick={handleCloseAlert}>
                                    <CloseIcon />
                                </IconButton>
                            </Stack>
                        </DialogTitle>

                        <Tabs value={alertTab} onChange={(_, v) => setAlertTab(v)} sx={{ px: 3 }}>
                            <Tab label="Details" />
                            <Tab label="Impact" />
                            <Tab label="Timeline" />
                        </Tabs>

                        <DialogContent>
                            {/* Details Tab */}
                            {alertTab === 0 && (
                                <Stack spacing={3}>
                                    <Box>
                                        <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                            Description
                                        </Typography>
                                        <Typography variant="body2" color="text.secondary">
                                            {selectedAlert.description}
                                        </Typography>
                                    </Box>

                                    <Box>
                                        <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                            Affected Services
                                        </Typography>
                                        <Stack direction="row" spacing={1}>
                                            {selectedAlert.affectedServices.map((service) => (
                                                <Chip key={service} label={service} size="small" />
                                            ))}
                                        </Stack>
                                    </Box>

                                    {selectedAlert.assignedTo && (
                                        <Box>
                                            <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                                Assigned To
                                            </Typography>
                                            <Typography variant="body2">{selectedAlert.assignedTo}</Typography>
                                        </Box>
                                    )}
                                </Stack>
                            )}

                            {/* Impact Tab */}
                            {alertTab === 1 && (
                                <Stack spacing={3}>
                                    <Alert severity={getSeverityColor(selectedAlert.severity) as any}>
                                        <Typography variant="subtitle2">Impact Summary</Typography>
                                        <Typography variant="body2">
                                            This alert affects {selectedAlert.affectedServices.length} service(s) and{' '}
                                            {selectedAlert.affectedTenants} tenant(s).
                                        </Typography>
                                    </Alert>

                                    <Card variant="outlined">
                                        <Box sx={{ p: 2 }}>
                                            <Typography variant="subtitle2" sx={{ mb: 1 }}>
                                                Affected Tenants
                                            </Typography>
                                            <Typography variant="h4" sx={{ fontWeight: 600 }}>
                                                {selectedAlert.affectedTenants}
                                            </Typography>
                                            <Typography variant="caption" color="text.secondary">
                                                tenants experiencing issues
                                            </Typography>
                                        </Box>
                                    </Card>
                                </Stack>
                            )}

                            {/* Timeline Tab */}
                            {alertTab === 2 && (
                                <Stack spacing={2}>
                                    <Box>
                                        <Typography variant="caption" color="text.secondary">
                                            Triggered
                                        </Typography>
                                        <Typography variant="body2">
                                            {new Date(selectedAlert.triggeredAt).toLocaleString()}
                                        </Typography>
                                    </Box>
                                    {selectedAlert.acknowledgedAt && (
                                        <Box>
                                            <Typography variant="caption" color="text.secondary">
                                                Acknowledged
                                            </Typography>
                                            <Typography variant="body2">
                                                {new Date(selectedAlert.acknowledgedAt).toLocaleString()}
                                            </Typography>
                                        </Box>
                                    )}
                                    {selectedAlert.resolvedAt && (
                                        <Box>
                                            <Typography variant="caption" color="text.secondary">
                                                Resolved
                                            </Typography>
                                            <Typography variant="body2">
                                                {new Date(selectedAlert.resolvedAt).toLocaleString()}
                                            </Typography>
                                        </Box>
                                    )}
                                </Stack>
                            )}
                        </DialogContent>

                        <DialogActions>
                            <Button onClick={handleCloseAlert}>Close</Button>
                            {!selectedAlert.acknowledgedAt && !selectedAlert.resolvedAt && (
                                <Button onClick={handleAcknowledge} variant="outlined">
                                    Acknowledge
                                </Button>
                            )}
                            {!selectedAlert.resolvedAt && (
                                <Button onClick={handleResolve} variant="contained" color="success">
                                    Mark Resolved
                                </Button>
                            )}
                        </DialogActions>
                    </>
                )}
            </Dialog>
        </Box>
    );
}
