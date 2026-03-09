import { LayoutDashboard as DashboardIcon, Gauge as SpeedIcon, Cpu as MemoryIcon, AlertCircle as ErrorIcon, AlertTriangle as WarningIcon, Info as InfoIcon, Activity as TimelineIcon, Bug as BugReportIcon, RefreshCw as RefreshIcon, Download as DownloadIcon, XCircle as ClearIcon, Play as PlayIcon, Stop as StopIcon, ChevronDown as ExpandMoreIcon, Eye as VisibilityIcon, Code as CodeIcon, CheckCircle as CheckCircleIcon } from 'lucide-react';
import {
  Card,
  CardContent,
  CardHeader,
  Typography,
  Box,
  Grid,
  ListItem,
  ListItemText,
  ListItemIcon,
  Chip,
  LinearProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Select,
  FormControl,
  InputLabel,
  Switch,
  FormControlLabel,
  Alert,
  IconButton,
  Tooltip,
  Badge,
  InteractiveList as List,
  Surface as Paper,
} from '@ghatana/ui';
import { TextField, MenuItem } from '@ghatana/ui';
import { Accordion, AccordionSummary, AccordionDetails } from '@ghatana/yappc-ui';
import React, { useState, useCallback, useMemo, useEffect } from 'react';

import {
  useAnalytics,
  useDebug,
  PerformanceMetric,
  UserAction,
  ErrorEvent
} from './hooks';

import type {
  SystemHealth,
  DebugLog} from './hooks';
import type { CanvasData } from '../schemas/canvas-schemas';

// Performance dashboard component
/**
 *
 */
export interface PerformanceDashboardProps {
  canvasId?: string;
  userId?: string;
  autoRefresh?: boolean;
  refreshInterval?: number;
}

export const PerformanceDashboard: React.FC<PerformanceDashboardProps> = ({
  canvasId,
  userId,
  autoRefresh = true,
  refreshInterval = 5000,
}) => {
  const {
    getSystemHealth,
    getMetrics,
    isRecording,
    metricsCount,
    lastFlush,
    startRecording,
    stopRecording,
    flushMetrics,
    clearMetrics,
  } = useAnalytics({
    canvasId,
    userId,
    enableRealTime: true,
    metricsInterval: refreshInterval,
  });

  const [systemHealth, setSystemHealth] = useState<SystemHealth | null>(null);
  const [selectedCategory, setSelectedCategory] = useState<string>('all');
  const [timeRange, setTimeRange] = useState<'1m' | '5m' | '15m' | '1h'>('5m');

  // Update system health periodically
  useEffect(() => {
    const updateHealth = () => {
      setSystemHealth(getSystemHealth());
    };

    updateHealth(); // Initial load

    if (autoRefresh) {
      const interval = setInterval(updateHealth, refreshInterval);
      return () => clearInterval(interval);
    }
    return undefined;
  }, [getSystemHealth, autoRefresh, refreshInterval]);

  const metrics = useMemo(() => {
    const endTime = new Date();
    const startTime = new Date();

    switch (timeRange) {
      case '1m':
        startTime.setMinutes(endTime.getMinutes() - 1);
        break;
      case '5m':
        startTime.setMinutes(endTime.getMinutes() - 5);
        break;
      case '15m':
        startTime.setMinutes(endTime.getMinutes() - 15);
        break;
      case '1h':
        startTime.setHours(endTime.getHours() - 1);
        break;
    }

    return getMetrics(
      selectedCategory === 'all' ? undefined : selectedCategory,
      {
        start: startTime.toISOString(),
        end: endTime.toISOString(),
      }
    );
  }, [getMetrics, selectedCategory, timeRange]);

  const getHealthColor = (status: SystemHealth['status']): 'success' | 'warning' | 'error' | 'primary' => {
    switch (status) {
      case 'healthy': return 'success';
      case 'degraded': return 'warning';
      case 'critical': return 'error';
      default: return 'primary';
    }
  };

  const formatMetricValue = (value: number, unit: string) => {
    if (unit === 'ms') {
      return value < 1000 ? `${value.toFixed(1)}ms` : `${(value / 1000).toFixed(2)}s`;
    }
    if (unit === 'bytes' || unit === 'MB') {
      return value < 1024 ? `${value.toFixed(1)}B` : `${(value / 1024).toFixed(1)}KB`;
    }
    return `${value} ${unit}`;
  };

  return (
    <Box className="flex flex-col gap-6">
      <Box>
        <Typography variant="h5" component="h2" className="mb-4">
          Performance Dashboard
        </Typography>

        <Box className="flex flex-wrap gap-2">
          <FormControl size="small" className="min-w-[140px]">
            <InputLabel id="performance-time-range-label">Time Range</InputLabel>
            <Select
              labelId="performance-time-range-label"
              value={timeRange}
              label="Time Range"
              onChange={(event) =>
                setTimeRange(event.target.value as typeof timeRange)
              }
            >
              <MenuItem value="1m">Last 1m</MenuItem>
              <MenuItem value="5m">Last 5m</MenuItem>
              <MenuItem value="15m">Last 15m</MenuItem>
              <MenuItem value="1h">Last 1h</MenuItem>
            </Select>
          </FormControl>

          <FormControl size="small" className="min-w-[160px]">
            <InputLabel id="performance-category-label">Category</InputLabel>
            <Select
              labelId="performance-category-label"
              value={selectedCategory}
              label="Category"
              onChange={(event) => setSelectedCategory(event.target.value)}
            >
              <MenuItem value="all">All Categories</MenuItem>
              <MenuItem value="render">Render</MenuItem>
              <MenuItem value="interaction">Interaction</MenuItem>
              <MenuItem value="network">Network</MenuItem>
              <MenuItem value="memory">Memory</MenuItem>
              <MenuItem value="canvas">Canvas</MenuItem>
            </Select>
          </FormControl>

          <Tooltip title={isRecording ? 'Stop Recording' : 'Start Recording'}>
            <IconButton
              color={isRecording ? 'error' : 'success'}
              onClick={isRecording ? stopRecording : startRecording}
              aria-label={isRecording ? 'Stop recording' : 'Start recording'}
            >
              {isRecording ? <StopIcon /> : <PlayIcon />}
            </IconButton>
          </Tooltip>

          <Tooltip title="Flush Metrics">
            <IconButton onClick={flushMetrics} aria-label="Flush metrics">
              <RefreshIcon />
            </IconButton>
          </Tooltip>

          <Tooltip title="Clear Metrics">
            <IconButton onClick={clearMetrics} aria-label="Clear metrics">
              <ClearIcon />
            </IconButton>
          </Tooltip>
        </Box>
      </Box>

      {/* System Health Overview */}
      {systemHealth && (
        <Grid container spacing={3} className="mb-6">
          <Grid item xs={12} md={3}>
            <Card>
              <CardContent>
                <Box className="flex items-center gap-4">
                  <SpeedIcon color={getHealthColor(systemHealth.status)} />
                  <Box>
                    <Typography variant="h6">System Health</Typography>
                    <Chip
                      label={systemHealth.status.toUpperCase()}
                      color={getHealthColor(systemHealth.status)}
                      size="small"
                    />
                  </Box>
                </Box>
                <Box className="mt-4">
                  <Typography variant="body2" color="text.secondary">
                    Score: {systemHealth.score}/100
                  </Typography>
                  <LinearProgress
                    variant="determinate"
                    value={systemHealth.score}
                    color={getHealthColor(systemHealth.status)}
                    className="mt-2"
                  />
                </Box>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} md={9}>
            <Card>
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  Key Metrics
                </Typography>
                <Grid container spacing={2}>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="h4" color="primary">
                        {systemHealth.metrics.renderTime.toFixed(1)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Avg Render Time (ms)
                      </Typography>
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="h4" color="secondary">
                        {systemHealth.metrics.memoryUsage.toFixed(1)}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Memory Usage (MB)
                      </Typography>
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="h4" color="info.main">
                        {systemHealth.metrics.canvasNodes}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Canvas Nodes
                      </Typography>
                    </Box>
                  </Grid>
                  <Grid item xs={6} sm={3}>
                    <Box textAlign="center">
                      <Typography variant="h4" color="warning.main">
                        {systemHealth.metrics.errorRate}
                      </Typography>
                      <Typography variant="body2" color="text.secondary">
                        Error Rate (/min)
                      </Typography>
                    </Box>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>
          </Grid>
        </Grid>
      )}

      {/* Recording Status */}
      <Box className="mb-6">
        <Alert
          severity={isRecording ? 'info' : 'warning'}
          action={
            <Button
              color="inherit"
              size="small"
              onClick={isRecording ? stopRecording : startRecording}
            >
              {isRecording ? 'Stop' : 'Start'}
            </Button>
          }
        >
          {isRecording
            ? `Recording metrics • ${metricsCount} events collected • Last flush: ${lastFlush || 'Never'}`
            : 'Metrics recording is paused'
          }
        </Alert>
      </Box>

      {/* Metrics Table */}
      <Card>
        <CardHeader title="Recent Metrics" />
        <CardContent>
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Timestamp</TableCell>
                  <TableCell>Metric</TableCell>
                  <TableCell>Value</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Metadata</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {metrics.slice(0, 50).map((metric) => (
                  <TableRow key={metric.id}>
                    <TableCell>
                      {new Date(metric.timestamp).toLocaleTimeString()}
                    </TableCell>
                    <TableCell>{metric.name}</TableCell>
                    <TableCell>
                      <Typography variant="body2" fontFamily="monospace">
                        {formatMetricValue(metric.value, metric.unit)}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Chip label={metric.category} size="small" />
                    </TableCell>
                    <TableCell>
                      {metric.metadata && (
                        <Typography variant="caption" color="text.secondary">
                          {Object.keys(metric.metadata).slice(0, 2).join(', ')}
                          {Object.keys(metric.metadata).length > 2 && '...'}
                        </Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          {metrics.length === 0 && (
            <Box className="text-center py-8">
              <Typography color="text.secondary">
                No metrics available for the selected time range
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>
    </Box>
  );
};

// Debug console component
/**
 *
 */
export interface DebugConsoleProps {
  canvasData?: CanvasData;
  onCanvasInspect?: (inspection: Record<string, unknown>) => void;
  onCanvasValidate?: (validation: { isValid: boolean; issues: string[] }) => void;
}

export const DebugConsole: React.FC<DebugConsoleProps> = ({
  canvasData,
  onCanvasInspect,
  onCanvasValidate,
}) => {
  const {
    debug,
    info,
    warn,
    error,
    logs,
    isEnabled,
    enable,
    disable,
    clearLogs,
    exportLogs,
    inspectCanvas,
    validateCanvas,
  } = useDebug({
    enabled: true,
    logLevel: 'debug',
    persistLogs: true,
    maxLogEntries: 500,
  });

  const [showExportDialog, setShowExportDialog] = useState(false);
  const [selectedLogLevel, setSelectedLogLevel] = useState<string>('all');

  const filteredLogs = useMemo(() => {
    if (selectedLogLevel === 'all') return logs;
    return logs.filter(log => log.level === selectedLogLevel);
  }, [logs, selectedLogLevel]);

  const handleInspectCanvas = useCallback(() => {
    if (!canvasData) {
      warn('No canvas data available for inspection');
      return;
    }

    const inspection = inspectCanvas(canvasData);
    onCanvasInspect?.(inspection);
    info('Canvas inspection completed', { nodeCount: inspection.metadata.nodeCount });
  }, [canvasData, inspectCanvas, onCanvasInspect, warn, info]);

  const handleValidateCanvas = useCallback(() => {
    if (!canvasData) {
      warn('No canvas data available for validation');
      return;
    }

    const validation = validateCanvas(canvasData);
    onCanvasValidate?.(validation);

    if (validation.isValid) {
      info('Canvas validation passed');
    } else {
      error(`Canvas validation failed with ${validation.issues.length} issues`);
    }
  }, [canvasData, validateCanvas, onCanvasValidate, warn, info, error]);

  const getLogIcon = (level: DebugLog['level']) => {
    switch (level) {
      case 'error': return <ErrorIcon color="error" />;
      case 'warn': return <WarningIcon color="warning" />;
      case 'info': return <InfoIcon color="info" />;
      case 'debug': return <BugReportIcon color="action" />;
      default: return <InfoIcon />;
    }
  };

  const getLogColor = (level: DebugLog['level']) => {
    switch (level) {
      case 'error': return 'error.main';
      case 'warn': return 'warning.main';
      case 'info': return 'info.main';
      case 'debug': return 'text.secondary';
      default: return 'text.primary';
    }
  };

  return (
    <Box className="p-4">
      {/* Header */}
      <Box className="flex justify-between items-center mb-6">
        <Typography variant="h4" className="flex items-center gap-2">
          <BugReportIcon />
          Debug Console
          <Badge badgeContent={logs.length} color="primary" />
        </Typography>

        <Box className="flex gap-4 items-center">
          <FormControlLabel
            control={<Switch checked={isEnabled} onChange={isEnabled ? disable : enable} />}
            label="Debug Enabled"
          />

          <FormControl size="small" className="min-w-[120px]">
            <InputLabel>Log Level</InputLabel>
            <Select
              value={selectedLogLevel}
              label="Log Level"
              onChange={(e) => setSelectedLogLevel(e.target.value)}
            >
              <MenuItem value="all">All Levels</MenuItem>
              <MenuItem value="error">Error</MenuItem>
              <MenuItem value="warn">Warning</MenuItem>
              <MenuItem value="info">Info</MenuItem>
              <MenuItem value="debug">Debug</MenuItem>
            </Select>
          </FormControl>

          <Button onClick={clearLogs} startIcon={<ClearIcon />}>
            Clear
          </Button>

          <Button onClick={() => setShowExportDialog(true)} startIcon={<DownloadIcon />}>
            Export
          </Button>
        </Box>
      </Box>

      {/* Canvas Tools */}
      {canvasData && (
        <Card className="mb-6">
          <CardHeader title="Canvas Debug Tools" />
          <CardContent>
            <Box className="flex gap-4 flex-wrap">
              <Button
                variant="outlined"
                startIcon={<VisibilityIcon />}
                onClick={handleInspectCanvas}
              >
                Inspect Canvas
              </Button>

              <Button
                variant="outlined"
                startIcon={<CheckCircleIcon />}
                onClick={handleValidateCanvas}
              >
                Validate Canvas
              </Button>

              <Button
                variant="outlined"
                startIcon={<CodeIcon />}
                onClick={() => debug('Canvas state snapshot', {
                  nodeCount: canvasData.nodes.length,
                  edgeCount: canvasData.edges.length,
                  viewport: canvasData.viewport,
                })}
              >
                Log State
              </Button>
            </Box>
          </CardContent>
        </Card>
      )}

      {/* Debug Logs */}
      <Card>
        <CardHeader title={`Debug Logs (${filteredLogs.length})`} />
        <CardContent>
          <List className="overflow-auto max-h-[600px]">
            {filteredLogs.map((log) => (
              <ListItem key={log.id} divider>
                <ListItemIcon>
                  {getLogIcon(log.level)}
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Box className="flex items-center gap-2">
                      <Typography
                        variant="body2"
                        style={{ color: getLogColor(log.level), fontWeight: 500 }}
                      >
                        {log.message}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {new Date(log.timestamp).toLocaleTimeString()}
                      </Typography>
                    </Box>
                  }
                  secondary={
                    <Box className="mt-2">
                      {log.context && (
                        <Accordion>
                          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography variant="caption">
                              Context ({Object.keys(log.context).length} properties)
                            </Typography>
                          </AccordionSummary>
                          <AccordionDetails>
                            <pre style={{ fontSize: '12px', overflow: 'auto' }}>
                              {JSON.stringify(log.context, null, 2)}
                            </pre>
                          </AccordionDetails>
                        </Accordion>
                      )}
                      {log.stack && (
                        <Accordion>
                          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography variant="caption">Stack Trace</Typography>
                          </AccordionSummary>
                          <AccordionDetails>
                            <pre style={{ fontSize: '10px', overflow: 'auto' }}>
                              {log.stack}
                            </pre>
                          </AccordionDetails>
                        </Accordion>
                      )}
                    </Box>
                  }
                />
              </ListItem>
            ))}
          </List>

          {filteredLogs.length === 0 && (
            <Box className="text-center py-8">
              <Typography color="text.secondary">
                No logs available
                {selectedLogLevel !== 'all' && ` for level: ${selectedLogLevel}`}
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      {/* Export Dialog */}
      <Dialog open={showExportDialog} onClose={() => setShowExportDialog(false)} maxWidth="md" fullWidth>
        <DialogTitle>Export Debug Logs</DialogTitle>
        <DialogContent>
          <TextField
            fullWidth
            multiline
            rows={20}
            value={exportLogs()}
            variant="outlined"
            InputProps={{
              readOnly: true,
              style: { fontFamily: 'monospace', fontSize: '12px' },
            }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setShowExportDialog(false)}>Close</Button>
          <Button
            variant="contained"
            onClick={() => {
              const blob = new Blob([exportLogs()], { type: 'application/json' });
              const url = URL.createObjectURL(blob);
              const a = document.createElement('a');
              a.href = url;
              a.download = `debug-logs-${new Date().toISOString().slice(0, 19)}.json`;
              a.click();
              URL.revokeObjectURL(url);
            }}
          >
            Download
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
