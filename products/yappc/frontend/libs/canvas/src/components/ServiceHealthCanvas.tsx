/**
 * @doc.type component
 * @doc.purpose Service health monitoring canvas for Journey 13.1 (SRE - Real-Time Incident Response)
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useCallback, useMemo } from 'react';
import { Surface as Paper, Box, Typography, IconButton, Badge, Chip, Alert as MuiAlert, AlertTitle, Button, Collapse, InteractiveList as List, ListItem, ListItemText, ListItemIcon, Divider, Tooltip, Spinner as CircularProgress, Dialog, DialogTitle, DialogContent, DialogActions, TextField, MenuItem, Switch, FormControlLabel } from '@ghatana/ui';
import { RefreshCw as RefreshIcon, Bell as NotificationsIcon, Bell as NotificationsActiveIcon, CheckCircle as HealthyIcon, AlertTriangle as WarningIcon, AlertCircle as CriticalIcon, HelpCircle as UnknownIcon, Activity as TimelineIcon, FileText as ReportIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon, Settings as SettingsIcon, Power as CircuitBreakerIcon } from 'lucide-react';
import {
    useServiceHealth,
    type UseServiceHealthOptions,
    type HealthStatus,
    type Alert,
    type Metric,
    type MetricType,
    type SLO,
} from '../hooks/useServiceHealth';

const toMuiSeverity = (severity: Alert['severity']): 'info' | 'warning' | 'error' => {
    if (severity === 'critical') return 'error';
    return severity;
};

/**
 * Component props
 */
export interface ServiceHealthCanvasProps {
    /**
     * Position of the health panel
     * @default 'top-right'
     */
    position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';

    /**
     * Show overall health status
     * @default true
     */
    showOverallHealth?: boolean;

    /**
     * Show alerts panel
     * @default true
     */
    showAlerts?: boolean;

    /**
     * Show metrics panel
     * @default true
     */
    showMetrics?: boolean;

    /**
     * Show SLO dashboard
     * @default true
     */
    showSLOs?: boolean;

    /**
     * Compact mode (minimal UI)
     * @default false
     */
    compact?: boolean;

    /**
     * Auto-color nodes by health
     * @default true
     */
    autoColorNodes?: boolean;

    /**
     * Service health hook options
     */
    healthOptions?: UseServiceHealthOptions;
}

/**
 * Health status icon component
 */
const HealthStatusIcon: React.FC<{ status: HealthStatus; size?: 'small' | 'medium' | 'large' }> = ({
    status,
    size = 'medium',
}) => {
    const iconProps = { fontSize: size };

    switch (status) {
        case 'healthy':
            return <HealthyIcon {...iconProps} className="text-[#4caf50]" />;
        case 'degraded':
            return <WarningIcon {...iconProps} className="text-[#ff9800]" />;
        case 'critical':
            return <CriticalIcon {...iconProps} className="text-[#f44336]" />;
        case 'unknown':
        default:
            return <UnknownIcon {...iconProps} className="text-[#9e9e9e]" />;
    }
};

/**
 * Metric display component
 */
const MetricDisplay: React.FC<{ metric: Metric }> = ({ metric }) => {
    const isWarning = metric.value >= metric.threshold.warning;
    const isCritical = metric.value >= metric.threshold.critical;

    return (
        <Box className="mb-2">
            <Box className="flex justify-between items-center">
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    {metric.type.replace('_', ' ').toUpperCase()}
                </Typography>
                <Chip
                    label={`${metric.value.toFixed(1)}${metric.unit}`}
                    size="sm"
                    color={isCritical ? 'error' : isWarning ? 'warning' : 'success'}
                />
            </Box>
            <Box
                className="w-full rounded-lg mt-1 overflow-hidden h-[4px] bg-[#e0e0e0]"
            >
                <Box
                    style={{
                        width: `${Math.min(100, (metric.value / metric.threshold.critical) * 100)}%`,
                        height: '100%',
                        backgroundColor: isCritical ? '#f44336' : isWarning ? '#ff9800' : '#4caf50',
                        transition: 'width 0.3s ease',
                    }}
                />
            </Box>
        </Box>
    );
};

/**
 * SLO Display Component
 */
const SLODisplay: React.FC<{ slo: SLO }> = ({ slo }) => {
    const percentage = slo.current;
    const isBreached = slo.breached;

    return (
        <Box className="mb-4">
            <Box className="flex justify-between items-center mb-1">
                <Typography as="p" className="text-sm">{slo.name}</Typography>
                <Typography
                    as="p" className="text-sm font-bold" style={{ color: isBreached ? '#f44336' : '#4caf50' }}
                >
                    {percentage.toFixed(2)}%
                </Typography>
            </Box>
            <Box className="flex gap-2 items-center">
                <Box
                    className="flex-1 overflow-hidden h-[8px] bg-[#e0e0e0] rounded-2xl"
                >
                    <Box
                        style={{
                            width: `${percentage}%`,
                            height: '100%',
                            backgroundColor: isBreached ? '#f44336' : '#4caf50',
                            transition: 'width 0.3s ease',
                        }}
                    />
                </Box>
                <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                    Target: {slo.target}%
                </Typography>
            </Box>
            <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                {slo.timeWindow} window
            </Typography>
        </Box>
    );
};

/**
 * Service Health Canvas Component
 * 
 * Displays real-time service health metrics, alerts, SLOs, and circuit breaker controls.
 * Integrates with Prometheus for live metrics and provides incident management tools.
 * 
 * @example
 * ```tsx
 * <ServiceHealthCanvas
 *   position="top-right"
 *   showOverallHealth
 *   showAlerts
 *   autoColorNodes
 * />
 * ```
 */
export const ServiceHealthCanvas: React.FC<ServiceHealthCanvasProps> = ({
    position = 'top-right',
    showOverallHealth = true,
    showAlerts = true,
    showMetrics = true,
    showSLOs = true,
    compact = false,
    autoColorNodes = true,
    healthOptions = {},
}) => {
    const {
        healthData,
        overallHealth,
        refreshMetrics,
        isRefreshing,
        lastRefresh,
        alerts,
        acknowledgeAlert,
        clearAlert,
        unacknowledgedCount,
        slos,
        addSLO,
        removeSLO,
        circuitBreakers,
        enableCircuitBreaker,
        disableCircuitBreaker,
        colorNodesByHealth,
        createIncidentReport,
    } = useServiceHealth(healthOptions);

    const [alertsPanelOpen, setAlertsPanelOpen] = React.useState(false);
    const [metricsPanelOpen, setMetricsPanelOpen] = React.useState(true);
    const [slosPanelOpen, setSlosPanelOpen] = React.useState(false);
    const [circuitBreakerDialogOpen, setCircuitBreakerDialogOpen] = React.useState(false);
    const [incidentReportDialogOpen, setIncidentReportDialogOpen] = React.useState(false);
    const [selectedNodeId, setSelectedNodeId] = React.useState<string | null>(null);
    const [incidentReport, setIncidentReport] = React.useState<string>('');

    // Auto-color nodes on health data change
    React.useEffect(() => {
        if (autoColorNodes && healthData.size > 0) {
            colorNodesByHealth();
        }
    }, [autoColorNodes, healthData, colorNodesByHealth]);

    // Position styles
    const positionStyles = useMemo(() => {
        const styles: React.CSSProperties = {
            position: 'fixed',
            zIndex: 1000,
        };

        switch (position) {
            case 'top-left':
                return { ...styles, top: 16, left: 16 };
            case 'top-right':
                return { ...styles, top: 16, right: 16 };
            case 'bottom-left':
                return { ...styles, bottom: 16, left: 16 };
            case 'bottom-right':
                return { ...styles, bottom: 16, right: 16 };
        }
    }, [position]);

    // Handle refresh
    const handleRefresh = useCallback(() => {
        refreshMetrics();
    }, [refreshMetrics]);

    // Handle alert click
    const handleAlertClick = useCallback((alert: Alert) => {
        acknowledgeAlert(alert.id);
    }, [acknowledgeAlert]);

    // Handle create incident report
    const handleCreateIncidentReport = useCallback(async (nodeId: string) => {
        const report = await createIncidentReport(nodeId);
        setIncidentReport(report);
        setIncidentReportDialogOpen(true);
    }, [createIncidentReport]);

    // Compact mode
    if (compact) {
        return (
            <Paper style={{ ...positionStyles, padding: 8 }}>
                <Box className="flex gap-2 items-center">
                    <HealthStatusIcon status={overallHealth} size="md" />
                    <Badge badgeContent={unacknowledgedCount} tone="danger">
                        <IconButton size="sm" onClick={() => setAlertsPanelOpen(!alertsPanelOpen)}>
                            {unacknowledgedCount > 0 ? <NotificationsActiveIcon /> : <NotificationsIcon />}
                        </IconButton>
                    </Badge>
                    <IconButton size="sm" onClick={handleRefresh} disabled={isRefreshing}>
                        <RefreshIcon />
                    </IconButton>
                </Box>
            </Paper>
        );
    }

    // Full mode
    return (
        <>
            <Paper style={{ ...positionStyles, width: 400, maxHeight: '80vh', overflow: 'auto' }}>
                {/* Header */}
                <Box className="p-4 border-gray-200 dark:border-gray-700 border-b" >
                    <Box className="flex justify-between items-center">
                        <Box className="flex gap-2 items-center">
                            <Typography as="h6">Service Health</Typography>
                            {showOverallHealth && <HealthStatusIcon status={overallHealth} size="md" />}
                        </Box>
                        <Box className="flex gap-1">
                            <Tooltip title="Refresh metrics">
                                <IconButton size="sm" onClick={handleRefresh} disabled={isRefreshing}>
                                    {isRefreshing ? <CircularProgress size={20} /> : <RefreshIcon />}
                                </IconButton>
                            </Tooltip>
                            <Badge badgeContent={unacknowledgedCount} tone="danger">
                                <IconButton
                                    size="sm"
                                    onClick={() => setAlertsPanelOpen(!alertsPanelOpen)}
                                >
                                    {unacknowledgedCount > 0 ? <NotificationsActiveIcon /> : <NotificationsIcon />}
                                </IconButton>
                            </Badge>
                        </Box>
                    </Box>
                    {lastRefresh && (
                        <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                            Last updated: {new Date(lastRefresh).toLocaleTimeString()}
                        </Typography>
                    )}
                </Box>

                {/* Alerts Panel */}
                {showAlerts && (
                    <Box className="border-gray-200 dark:border-gray-700 border-b" >
                        <Box
                            className="p-2 flex justify-between items-center cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800"
                            onClick={() => setAlertsPanelOpen(!alertsPanelOpen)}
                        >
                            <Typography as="p" className="text-sm font-medium">
                                Alerts ({alerts.length})
                            </Typography>
                            {alertsPanelOpen ? <CollapseIcon /> : <ExpandIcon />}
                        </Box>
                        <Collapse in={alertsPanelOpen}>
                            <Box className="p-4 overflow-auto max-h-[200px]">
                                {alerts.length === 0 ? (
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        No active alerts
                                    </Typography>
                                ) : (
                                    alerts.map(alert => (
                                        <MuiAlert
                                            key={alert.id}
                                            severity={toMuiSeverity(alert.severity)}
                                            onClose={() => clearAlert(alert.id)}
                                            className="mb-2" style={{ opacity: alert.acknowledged ? 0.6 : 1 }}
                                            action={
                                                !alert.acknowledged ? (
                                                    <Button size="sm" onClick={() => handleAlertClick(alert)}>
                                                        ACK
                                                    </Button>
                                                ) : undefined
                                            }
                                        >
                                            <AlertTitle>
                                                {alert.severity.toUpperCase()} - {new Date(alert.timestamp).toLocaleTimeString()}
                                            </AlertTitle>
                                            {alert.message}
                                        </MuiAlert>
                                    ))
                                )}
                            </Box>
                        </Collapse>
                    </Box>
                )}

                {/* Metrics Panel */}
                {showMetrics && (
                    <Box className="border-gray-200 dark:border-gray-700 border-b" >
                        <Box
                            className="p-2 flex justify-between items-center cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800"
                            onClick={() => setMetricsPanelOpen(!metricsPanelOpen)}
                        >
                            <Typography as="p" className="text-sm font-medium">
                                Metrics ({healthData.size} services)
                            </Typography>
                            {metricsPanelOpen ? <CollapseIcon /> : <ExpandIcon />}
                        </Box>
                        <Collapse in={metricsPanelOpen}>
                            <Box className="p-4 overflow-auto max-h-[300px]">
                                {Array.from(healthData.values()).map(health => (
                                    <Box key={health.nodeId} className="mb-4 pb-4 border-gray-200 dark:border-gray-700 border-b" >
                                        <Box className="flex justify-between items-center mb-2">
                                            <Box className="flex gap-2 items-center">
                                                <HealthStatusIcon status={health.status} size="sm" />
                                                <Typography as="p" className="text-sm" fontWeight="bold">
                                                    {health.serviceName}
                                                </Typography>
                                            </Box>
                                            <Box className="flex gap-1">
                                                <Tooltip title="Circuit Breaker">
                                                    <IconButton
                                                        size="sm"
                                                        color={circuitBreakers.has(health.nodeId) ? 'error' : 'default'}
                                                        onClick={() => {
                                                            setSelectedNodeId(health.nodeId);
                                                            setCircuitBreakerDialogOpen(true);
                                                        }}
                                                    >
                                                        <CircuitBreakerIcon size={16} />
                                                    </IconButton>
                                                </Tooltip>
                                                <Tooltip title="Incident Report">
                                                    <IconButton
                                                        size="sm"
                                                        onClick={() => handleCreateIncidentReport(health.nodeId)}
                                                    >
                                                        <ReportIcon size={16} />
                                                    </IconButton>
                                                </Tooltip>
                                            </Box>
                                        </Box>
                                        <Typography as="span" className="text-xs text-gray-500 mb-2 block" color="text.secondary">
                                            Uptime: {Math.floor(health.uptime / 3600)}h {Math.floor((health.uptime % 3600) / 60)}m
                                        </Typography>
                                        {health.metrics.map(metric => (
                                            <MetricDisplay key={metric.type} metric={metric} />
                                        ))}
                                    </Box>
                                ))}
                            </Box>
                        </Collapse>
                    </Box>
                )}

                {/* SLOs Panel */}
                {showSLOs && (
                    <Box>
                        <Box
                            className="p-2 flex justify-between items-center cursor-pointer hover:bg-gray-100 hover:dark:bg-gray-800"
                            onClick={() => setSlosPanelOpen(!slosPanelOpen)}
                        >
                            <Typography as="p" className="text-sm font-medium">
                                SLOs ({slos.length})
                            </Typography>
                            {slosPanelOpen ? <CollapseIcon /> : <ExpandIcon />}
                        </Box>
                        <Collapse in={slosPanelOpen}>
                            <Box className="p-4">
                                {slos.length === 0 ? (
                                    <Typography as="p" className="text-sm" color="text.secondary">
                                        No SLOs defined
                                    </Typography>
                                ) : (
                                    slos.map(slo => <SLODisplay key={slo.id} slo={slo} />)
                                )}
                            </Box>
                        </Collapse>
                    </Box>
                )}
            </Paper>

            {/* Circuit Breaker Dialog */}
            <Dialog
                open={circuitBreakerDialogOpen}
                onClose={() => setCircuitBreakerDialogOpen(false)}
                size="sm"
                fullWidth
            >
                <DialogTitle>Circuit Breaker Configuration</DialogTitle>
                <DialogContent>
                    {selectedNodeId && (
                        <Box className="pt-4">
                            <FormControlLabel
                                control={
                                    <Switch
                                        checked={circuitBreakers.has(selectedNodeId)}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                enableCircuitBreaker(selectedNodeId, {
                                                    threshold: 5,
                                                    timeout: 30,
                                                    fallbackStrategy: 'cache',
                                                });
                                            } else {
                                                disableCircuitBreaker(selectedNodeId);
                                            }
                                        }}
                                    />
                                }
                                label="Enable Circuit Breaker"
                            />
                            {circuitBreakers.has(selectedNodeId) && (
                                <Box className="mt-4">
                                    <TextField
                                        fullWidth
                                        label="Failure Threshold"
                                        type="number"
                                        defaultValue={circuitBreakers.get(selectedNodeId)?.threshold}
                                        className="mb-4"
                                    />
                                    <TextField
                                        fullWidth
                                        label="Timeout (seconds)"
                                        type="number"
                                        defaultValue={circuitBreakers.get(selectedNodeId)?.timeout}
                                        className="mb-4"
                                    />
                                    <TextField
                                        fullWidth
                                        select
                                        label="Fallback Strategy"
                                        defaultValue={circuitBreakers.get(selectedNodeId)?.fallbackStrategy}
                                    >
                                        <MenuItem value="cache">Cached Data</MenuItem>
                                        <MenuItem value="default">Default Response</MenuItem>
                                        <MenuItem value="fail-fast">Fail Fast</MenuItem>
                                    </TextField>
                                </Box>
                            )}
                        </Box>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setCircuitBreakerDialogOpen(false)}>Close</Button>
                    <Button variant="solid" onClick={() => setCircuitBreakerDialogOpen(false)}>
                        Apply
                    </Button>
                </DialogActions>
            </Dialog>

            {/* Incident Report Dialog */}
            <Dialog
                open={incidentReportDialogOpen}
                onClose={() => setIncidentReportDialogOpen(false)}
                size="md"
                fullWidth
            >
                <DialogTitle>Incident Report</DialogTitle>
                <DialogContent>
                    <TextField
                        fullWidth
                        multiline
                        rows={20}
                        value={incidentReport}
                        InputProps={{ readOnly: true }}
                        className="mt-4 font-mono"
                    />
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setIncidentReportDialogOpen(false)}>Close</Button>
                    <Button
                        variant="solid"
                        onClick={() => {
                            navigator.clipboard.writeText(incidentReport);
                        }}
                    >
                        Copy to Clipboard
                    </Button>
                </DialogActions>
            </Dialog>
        </>
    );
};
