/**
 * Operations Center Component
 *
 * Root-level operations center component with infrastructure monitoring, deployment tracking,
 * incident response, and service health.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    KpiCard,
    Box,
    Chip,
    LinearProgress,
    Tabs,
    Tab,
    Alert,
    Button,
    Typography,
    Stack,
    Table,
    TableHead,
    TableBody,
    TableRow,
    TableCell,
} from '@ghatana/ui';

/**
 * Infrastructure metrics
 */
export interface InfrastructureMetrics {
    totalServices: number;
    healthyServices: number;
    degradedServices: number;
    downServices: number;
    averageUptime: number; // Percentage
    activeIncidents: number;
    recentDeployments: number;
    cpuUsage: number; // Percentage
    memoryUsage: number; // Percentage
}

/**
 * Service status
 */
export interface ServiceStatus {
    id: string;
    name: string;
    type: 'api' | 'database' | 'queue' | 'storage' | 'compute';
    status: 'healthy' | 'degraded' | 'down';
    uptime: number; // Percentage
    responseTime: number; // ms
    requestsPerSecond: number;
    errorRate: number; // Percentage
    lastDeployment: string;
}

/**
 * Deployment record
 */
export interface DeploymentRecord {
    id: string;
    service: string;
    version: string;
    environment: 'production' | 'staging' | 'development';
    status: 'success' | 'failed' | 'in-progress' | 'rolled-back';
    deployedBy: string;
    startTime: string;
    endTime?: string;
    duration?: number; // minutes
}

/**
 * Incident record
 */
export interface IncidentRecord {
    id: string;
    title: string;
    severity: 'critical' | 'high' | 'medium' | 'low';
    status: 'active' | 'investigating' | 'resolved';
    affectedServices: string[];
    reportedAt: string;
    resolvedAt?: string;
    assignedTo: string;
    description: string;
}

/**
 * Resource utilization
 */
export interface ResourceUtilization {
    resourceType: 'cpu' | 'memory' | 'disk' | 'network';
    current: number; // Percentage or MB
    limit: number;
    unit: string;
    trend: 'up' | 'down' | 'stable';
}

/**
 * Operations Center Props
 */
export interface OperationsCenterProps {
    /** Infrastructure metrics */
    infraMetrics: InfrastructureMetrics;
    /** Service statuses */
    services: ServiceStatus[];
    /** Deployment records */
    deployments: DeploymentRecord[];
    /** Incident records */
    incidents: IncidentRecord[];
    /** Resource utilization */
    resources: ResourceUtilization[];
    /** Callback when service is clicked */
    onServiceClick?: (serviceId: string) => void;
    /** Callback when deployment is clicked */
    onDeploymentClick?: (deploymentId: string) => void;
    /** Callback when incident is clicked */
    onIncidentClick?: (incidentId: string) => void;
    /** Callback when create incident is clicked */
    onCreateIncident?: () => void;
    /** Callback when trigger deployment is clicked */
    onTriggerDeployment?: () => void;
}

/**
 * Operations Center Component
 *
 * Provides Root-level operations monitoring with:
 * - Infrastructure health overview (services, uptime, incidents, deployments)
 * - Service status monitoring
 * - Deployment tracking
 * - Incident management
 * - Resource utilization monitoring
 * - Tab-based navigation (Services, Deployments, Incidents, Resources)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (infrastructure metrics)
 * - Grid (responsive layouts)
 * - Card (service cards, incident cards)
 * - Table (deployments, incidents)
 * - Chip (status, severity, environment indicators)
 * - LinearProgress (uptime, resource usage)
 * - Tabs (navigation)
 * - Alert (critical incidents)
 *
 * @example
 * ```tsx
 * <OperationsCenter
 *   infraMetrics={metrics}
 *   services={serviceList}
 *   deployments={deploymentList}
 *   incidents={incidentList}
 *   resources={resourceList}
 *   onServiceClick={(id) => navigate(`/ops/services/${id}`)}
 * />
 * ```
 */
export const OperationsCenter: React.FC<OperationsCenterProps> = ({
    infraMetrics,
    services,
    deployments,
    incidents,
    resources,
    onServiceClick,
    onDeploymentClick,
    onIncidentClick,
    onCreateIncident,
    onTriggerDeployment,
}) => {
    const [selectedTab, setSelectedTab] = useState<'services' | 'deployments' | 'incidents' | 'resources'>('services');
    const [serviceFilter, setServiceFilter] = useState<'all' | 'healthy' | 'degraded' | 'down'>('all');

    // Get status color
    const getStatusColor = (status: string): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'healthy':
            case 'success':
            case 'resolved':
                return 'success';
            case 'degraded':
            case 'in-progress':
            case 'investigating':
                return 'warning';
            case 'down':
            case 'failed':
            case 'active':
            case 'rolled-back':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get severity color
    const getSeverityColor = (severity: 'critical' | 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (severity) {
            case 'critical':
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Get environment color
    const getEnvironmentColor = (env: 'production' | 'staging' | 'development'): 'error' | 'warning' | 'default' => {
        switch (env) {
            case 'production':
                return 'error';
            case 'staging':
                return 'warning';
            case 'development':
                return 'default';
        }
    };

    // Get type color
    const getTypeColor = (type: string): 'default' | 'warning' | 'error' => {
        switch (type) {
            case 'api':
                return 'error';
            case 'database':
                return 'warning';
            case 'queue':
                return 'default';
            case 'storage':
                return 'warning';
            case 'compute':
                return 'error';
            default:
                return 'default';
        }
    };

    // Get trend icon
    const getTrendIcon = (trend: 'up' | 'down' | 'stable'): string => {
        switch (trend) {
            case 'up':
                return '↑';
            case 'down':
                return '↓';
            case 'stable':
                return '→';
        }
    };

    // Format date
    const formatDate = (dateString: string): string => {
        const date = new Date(dateString);
        return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
    };

    // Filter services
    const filteredServices = serviceFilter === 'all' ? services : services.filter((s) => s.status === serviceFilter);

    // Count critical incidents
    const criticalIncidents = incidents.filter((i) => i.severity === 'critical' && i.status === 'active').length;

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Operations Center
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        Infrastructure monitoring and incident response
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onTriggerDeployment && selectedTab === 'deployments' && (
                        <Button variant="primary" size="md" onClick={onTriggerDeployment}>
                            New Deployment
                        </Button>
                    )}
                    {onCreateIncident && selectedTab === 'incidents' && (
                        <Button variant="primary" size="md" onClick={onCreateIncident}>
                            Report Incident
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Critical Incidents Alert */}
            {criticalIncidents > 0 && (
                <Alert severity="error">
                    {criticalIncidents} critical incident{criticalIncidents > 1 ? 's' : ''} require immediate attention
                </Alert>
            )}

            {/* Infrastructure Metrics */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Service Health"
                    value={`${infraMetrics.healthyServices}/${infraMetrics.totalServices}`}
                    description={`${Math.round((infraMetrics.healthyServices / infraMetrics.totalServices) * 100)}% healthy`}
                    status={infraMetrics.downServices === 0 ? 'healthy' : infraMetrics.downServices > 2 ? 'error' : 'warning'}
                />

                <KpiCard
                    label="Average Uptime"
                    value={`${infraMetrics.averageUptime}%`}
                    description="Last 30 days"
                    status={infraMetrics.averageUptime >= 99.9 ? 'healthy' : infraMetrics.averageUptime >= 99 ? 'warning' : 'error'}
                />

                <KpiCard
                    label="Active Incidents"
                    value={infraMetrics.activeIncidents}
                    description={`${criticalIncidents} critical`}
                    status={infraMetrics.activeIncidents === 0 ? 'healthy' : criticalIncidents > 0 ? 'error' : 'warning'}
                />

                <KpiCard label="Recent Deployments" value={infraMetrics.recentDeployments} description="Last 24 hours" status="healthy" />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`Services (${services.length})`} value="services" />
                    <Tab label={`Deployments (${deployments.length})`} value="deployments" />
                    <Tab label={`Incidents (${incidents.length})`} value="incidents" />
                    <Tab label={`Resources (${resources.length})`} value="resources" />
                </Tabs>

                {/* Services Tab */}
                {selectedTab === 'services' && (
                    <Box className="p-4">
                        {/* Service Filter */}
                        <Stack direction="row" spacing={2} className="mb-4">
                            <Chip label={`All (${services.length})`} color={serviceFilter === 'all' ? 'error' : 'default'} onClick={() => setServiceFilter('all')} />
                            <Chip
                                label={`Healthy (${services.filter((s) => s.status === 'healthy').length})`}
                                color={serviceFilter === 'healthy' ? 'success' : 'default'}
                                onClick={() => setServiceFilter('healthy')}
                            />
                            <Chip
                                label={`Degraded (${services.filter((s) => s.status === 'degraded').length})`}
                                color={serviceFilter === 'degraded' ? 'warning' : 'default'}
                                onClick={() => setServiceFilter('degraded')}
                            />
                            <Chip
                                label={`Down (${services.filter((s) => s.status === 'down').length})`}
                                color={serviceFilter === 'down' ? 'error' : 'default'}
                                onClick={() => setServiceFilter('down')}
                            />
                        </Stack>

                        {/* Service Grid */}
                        <Grid columns={2} gap={4}>
                            {filteredServices.map((service) => (
                                <Card key={service.id} className="cursor-pointer hover:shadow-md transition-shadow" onClick={() => onServiceClick?.(service.id)}>
                                    <Box className="p-4">
                                        {/* Service Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box>
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {service.name}
                                                    </Typography>
                                                    <Chip label={service.type} color={getTypeColor(service.type)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Last deployed: {formatDate(service.lastDeployment)}
                                                </Typography>
                                            </Box>
                                            <Chip label={service.status} color={getStatusColor(service.status)} size="small" />
                                        </Box>

                                        {/* Uptime */}
                                        <Box className="mb-3">
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Uptime
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {service.uptime}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={service.uptime}
                                                color={service.uptime >= 99.5 ? 'success' : service.uptime >= 99 ? 'warning' : 'error'}
                                            />
                                        </Box>

                                        {/* Service Metrics */}
                                        <Grid columns={3} gap={2}>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Response Time
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {service.responseTime}ms
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Requests/sec
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {service.requestsPerSecond}
                                                </Typography>
                                            </Box>
                                            <Box>
                                                <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                    Error Rate
                                                </Typography>
                                                <Typography variant="body2" className={`font-medium ${service.errorRate > 1 ? 'text-red-600' : 'text-slate-900 dark:text-neutral-100'}`}>
                                                    {service.errorRate}%
                                                </Typography>
                                            </Box>
                                        </Grid>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}

                {/* Deployments Tab */}
                {selectedTab === 'deployments' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Recent Deployments
                        </Typography>

                        <Table>
                            <TableHead>
                                <TableRow>
                                    <TableCell>Service</TableCell>
                                    <TableCell>Version</TableCell>
                                    <TableCell>Environment</TableCell>
                                    <TableCell>Status</TableCell>
                                    <TableCell>Deployed By</TableCell>
                                    <TableCell>Start Time</TableCell>
                                    <TableCell>Duration</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {deployments.map((deployment) => (
                                    <TableRow
                                        key={deployment.id}
                                        className="cursor-pointer hover:bg-slate-50 dark:hover:bg-neutral-800"
                                        onClick={() => onDeploymentClick?.(deployment.id)}
                                    >
                                        <TableCell>
                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                {deployment.service}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {deployment.version}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={deployment.environment} color={getEnvironmentColor(deployment.environment)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Chip label={deployment.status} color={getStatusColor(deployment.status)} size="small" />
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {deployment.deployedBy}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {formatDate(deployment.startTime)}
                                            </Typography>
                                        </TableCell>
                                        <TableCell>
                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                {deployment.duration ? `${deployment.duration} min` : '-'}
                                            </Typography>
                                        </TableCell>
                                    </TableRow>
                                ))}
                            </TableBody>
                        </Table>
                    </Box>
                )}

                {/* Incidents Tab */}
                {selectedTab === 'incidents' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Incident Records
                        </Typography>

                        <Stack spacing={3}>
                            {incidents.map((incident) => (
                                <Card
                                    key={incident.id}
                                    className="cursor-pointer hover:shadow-md transition-shadow"
                                    onClick={() => onIncidentClick?.(incident.id)}
                                >
                                    <Box className="p-4">
                                        {/* Incident Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Box className="flex-1">
                                                <Box className="flex items-center gap-2 mb-1">
                                                    <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                        {incident.title}
                                                    </Typography>
                                                    <Chip label={incident.severity} color={getSeverityColor(incident.severity)} size="small" />
                                                    <Chip label={incident.status} color={getStatusColor(incident.status)} size="small" />
                                                </Box>
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    {incident.description}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Incident Details */}
                                        <Box className="mt-3 pt-3 border-t border-slate-200 dark:border-neutral-700">
                                            <Grid columns={4} gap={3}>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Affected Services
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {incident.affectedServices.length} service{incident.affectedServices.length !== 1 ? 's' : ''}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Reported At
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {formatDate(incident.reportedAt)}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Assigned To
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {incident.assignedTo}
                                                    </Typography>
                                                </Box>
                                                <Box>
                                                    <Typography variant="caption" className="text-slate-500 dark:text-neutral-400">
                                                        Duration
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {incident.resolvedAt
                                                            ? `${Math.round((new Date(incident.resolvedAt).getTime() - new Date(incident.reportedAt).getTime()) / 60000)} min`
                                                            : 'Ongoing'}
                                                    </Typography>
                                                </Box>
                                            </Grid>
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Stack>
                    </Box>
                )}

                {/* Resources Tab */}
                {selectedTab === 'resources' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Resource Utilization
                        </Typography>

                        <Grid columns={2} gap={4}>
                            {resources.map((resource) => (
                                <Card key={resource.resourceType}>
                                    <Box className="p-4">
                                        {/* Resource Header */}
                                        <Box className="flex items-start justify-between mb-3">
                                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 capitalize">
                                                {resource.resourceType}
                                            </Typography>
                                            <Chip
                                                label={`${getTrendIcon(resource.trend)} ${resource.trend}`}
                                                color={resource.trend === 'up' ? 'warning' : resource.trend === 'down' ? 'success' : 'default'}
                                                size="small"
                                            />
                                        </Box>

                                        {/* Current Usage */}
                                        <Box className="mb-3">
                                            <Box className="flex items-baseline gap-2 mb-1">
                                                <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                                                    {resource.current} {resource.unit}
                                                </Typography>
                                                <Typography variant="body2" className="text-slate-500 dark:text-neutral-400">
                                                    / {resource.limit} {resource.unit}
                                                </Typography>
                                            </Box>
                                        </Box>

                                        {/* Usage Progress */}
                                        <Box>
                                            <Box className="flex items-center justify-between mb-1">
                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                    Utilization
                                                </Typography>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                    {Math.round((resource.current / resource.limit) * 100)}%
                                                </Typography>
                                            </Box>
                                            <LinearProgress
                                                variant="determinate"
                                                value={Math.min((resource.current / resource.limit) * 100, 100)}
                                                color={
                                                    (resource.current / resource.limit) * 100 < 70
                                                        ? 'success'
                                                        : (resource.current / resource.limit) * 100 < 85
                                                            ? 'warning'
                                                            : 'error'
                                                }
                                            />
                                        </Box>
                                    </Box>
                                </Card>
                            ))}
                        </Grid>
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockOperationsCenterData = {
    infraMetrics: {
        totalServices: 24,
        healthyServices: 22,
        degradedServices: 1,
        downServices: 1,
        averageUptime: 99.85,
        activeIncidents: 2,
        recentDeployments: 8,
        cpuUsage: 65,
        memoryUsage: 72,
    } as InfrastructureMetrics,

    services: [
        {
            id: 'service-1',
            name: 'API Gateway',
            type: 'api',
            status: 'healthy',
            uptime: 99.95,
            responseTime: 85,
            requestsPerSecond: 1250,
            errorRate: 0.02,
            lastDeployment: '2025-12-10T14:30:00Z',
        },
        {
            id: 'service-2',
            name: 'User Database',
            type: 'database',
            status: 'healthy',
            uptime: 99.99,
            responseTime: 12,
            requestsPerSecond: 850,
            errorRate: 0.01,
            lastDeployment: '2025-12-08T10:00:00Z',
        },
        {
            id: 'service-3',
            name: 'Event Queue',
            type: 'queue',
            status: 'degraded',
            uptime: 98.5,
            responseTime: 350,
            requestsPerSecond: 420,
            errorRate: 1.2,
            lastDeployment: '2025-12-09T16:15:00Z',
        },
    ] as ServiceStatus[],

    deployments: [
        {
            id: 'deploy-1',
            service: 'API Gateway',
            version: 'v2.5.0',
            environment: 'production',
            status: 'success',
            deployedBy: 'ops-team',
            startTime: '2025-12-10T14:30:00Z',
            endTime: '2025-12-10T14:45:00Z',
            duration: 15,
        },
        {
            id: 'deploy-2',
            service: 'Auth Service',
            version: 'v1.8.2',
            environment: 'staging',
            status: 'in-progress',
            deployedBy: 'dev-team',
            startTime: '2025-12-11T09:00:00Z',
        },
        {
            id: 'deploy-3',
            service: 'Payment Service',
            version: 'v3.1.0',
            environment: 'production',
            status: 'failed',
            deployedBy: 'ops-team',
            startTime: '2025-12-09T20:00:00Z',
            endTime: '2025-12-09T20:10:00Z',
            duration: 10,
        },
    ] as DeploymentRecord[],

    incidents: [
        {
            id: 'incident-1',
            title: 'API Gateway High Latency',
            severity: 'high',
            status: 'investigating',
            affectedServices: ['API Gateway', 'Auth Service'],
            reportedAt: '2025-12-11T08:30:00Z',
            assignedTo: 'ops-team',
            description: 'Increased response times affecting user authentication',
        },
        {
            id: 'incident-2',
            title: 'Event Queue Processing Delays',
            severity: 'medium',
            status: 'active',
            affectedServices: ['Event Queue'],
            reportedAt: '2025-12-11T06:00:00Z',
            assignedTo: 'backend-team',
            description: 'Message processing backlog increasing',
        },
    ] as IncidentRecord[],

    resources: [
        { resourceType: 'cpu', current: 65, limit: 100, unit: '%', trend: 'stable' },
        { resourceType: 'memory', current: 72, limit: 100, unit: '%', trend: 'up' },
        { resourceType: 'disk', current: 450, limit: 1000, unit: 'GB', trend: 'up' },
        { resourceType: 'network', current: 2.5, limit: 10, unit: 'Gbps', trend: 'stable' },
    ] as ResourceUtilization[],
};
