/**
 * ObserveStep - UI for the Observe step of the workflow
 *
 * @doc.type component
 * @doc.purpose Observe step workspace
 * @doc.layer product
 * @doc.pattern Form Component
 */

import React from 'react';
import { Box, Card, CardContent, Typography, TextField, IconButton, Button, InteractiveList as List, ListItem, ListItemIcon, ListItemText, Divider, Chip, Alert, FormControl, InputLabel, Select, MenuItem, LinearProgress } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, TrendingUp as UpIcon, TrendingDown as DownIcon, AlertTriangle as AnomalyIcon, Timer as TimerIcon, CheckCircle as StableIcon } from 'lucide-react';
import { useAtomValue, useSetAtom } from 'jotai';

import {
    currentWorkflowAtom,
    draftStepDataAtom,
    updateDraftStepDataAtom,
} from '../../../stores/workflow.store';

import type { ObserveStepData, Anomaly } from '@ghatana/yappc-types';

// ============================================================================
// CONSTANTS
// ============================================================================

const ANOMALY_TYPES: { value: Anomaly['type']; label: string }[] = [
    { value: 'REGRESSION', label: 'Regression' },
    { value: 'SPIKE', label: 'Spike' },
    { value: 'DROP', label: 'Drop' },
    { value: 'ERROR_RATE', label: 'Error Rate' },
    { value: 'LATENCY', label: 'Latency' },
];

const SEVERITY_LEVELS: { value: Anomaly['severity']; label: string; color: 'success' | 'info' | 'warning' | 'error' }[] = [
    { value: 'LOW', label: 'Low', color: 'success' },
    { value: 'MEDIUM', label: 'Medium', color: 'info' },
    { value: 'HIGH', label: 'High', color: 'warning' },
    { value: 'CRITICAL', label: 'Critical', color: 'error' },
];

// ============================================================================
// COMPONENT
// ============================================================================

export function ObserveStep() {
    const workflow = useAtomValue(currentWorkflowAtom);
    const draftData = useAtomValue(draftStepDataAtom) as ObserveStepData | null;
    const updateDraft = useSetAtom(updateDraftStepDataAtom);

    const [newAnomalyType, setNewAnomalyType] = React.useState<Anomaly['type']>('REGRESSION');
    const [newAnomalySeverity, setNewAnomalySeverity] = React.useState<Anomaly['severity']>('MEDIUM');
    const [newAnomalyMetric, setNewAnomalyMetric] = React.useState('');
    const [newAnomalyDesc, setNewAnomalyDesc] = React.useState('');
    const [newMetricName, setNewMetricName] = React.useState('');
    const [newMetricBefore, setNewMetricBefore] = React.useState('');
    const [newMetricAfter, setNewMetricAfter] = React.useState('');

    // Get current data (draft or saved)
    const baseData = draftData ?? workflow?.steps.observe.data;
    const currentData: ObserveStepData = {
        metricsDelta: baseData?.metricsDelta ?? { before: {}, after: {}, percentChange: {} },
        anomalies: baseData?.anomalies ?? [],
        observationWindow: baseData?.observationWindow ?? {
            startedAt: new Date().toISOString(),
            durationHours: 24,
            status: 'ACTIVE',
        },
    };

    const handleChange = (field: keyof ObserveStepData, value: unknown) => {
        updateDraft({ ...currentData, [field]: value });
    };

    // Calculate observation time remaining
    const startTime = new Date(currentData.observationWindow?.startedAt || new Date().toISOString()).getTime();
    const durationMs = (currentData.observationWindow?.durationHours ?? 24) * 60 * 60 * 1000;
    const endTime = startTime + durationMs;
    const now = Date.now();
    const elapsed = Math.max(0, now - startTime);
    const progress = Math.min(100, (elapsed / durationMs) * 100);
    const hoursRemaining = Math.max(0, (endTime - now) / (60 * 60 * 1000));

    // Metrics
    const handleAddMetric = () => {
        if (newMetricName.trim() && newMetricBefore && newMetricAfter) {
            const before = parseFloat(newMetricBefore);
            const after = parseFloat(newMetricAfter);
            const percentChange = before !== 0 ? ((after - before) / before) * 100 : 0;

            handleChange('metricsDelta', {
                before: { ...currentData.metricsDelta.before, [newMetricName]: before },
                after: { ...currentData.metricsDelta.after, [newMetricName]: after },
                percentChange: { ...currentData.metricsDelta.percentChange, [newMetricName]: percentChange },
            });
            setNewMetricName('');
            setNewMetricBefore('');
            setNewMetricAfter('');
        }
    };

    const handleRemoveMetric = (name: string) => {
        const { [name]: _b, ...beforeRest } = currentData.metricsDelta.before;
        const { [name]: _a, ...afterRest } = currentData.metricsDelta.after;
        const { [name]: _p, ...percentRest } = currentData.metricsDelta.percentChange;

        handleChange('metricsDelta', {
            before: beforeRest,
            after: afterRest,
            percentChange: percentRest,
        });
    };

    // Anomalies
    const handleAddAnomaly = () => {
        if (newAnomalyMetric.trim() && newAnomalyDesc.trim()) {
            const anomaly: Anomaly = {
                id: `anomaly-${Date.now()}`,
                type: newAnomalyType,
                severity: newAnomalySeverity,
                metric: newAnomalyMetric.trim(),
                description: newAnomalyDesc.trim(),
                detectedAt: new Date().toISOString(),
                resolved: false,
            };
            handleChange('anomalies', [...currentData.anomalies, anomaly]);
            setNewAnomalyMetric('');
            setNewAnomalyDesc('');
        }
    };

    const handleToggleAnomalyResolved = (id: string) => {
        handleChange(
            'anomalies',
            currentData.anomalies.map((a) => (a.id === id ? { ...a, resolved: !a.resolved } : a))
        );
    };

    const handleRemoveAnomaly = (id: string) => {
        handleChange(
            'anomalies',
            currentData.anomalies.filter((a) => a.id !== id)
        );
    };

    const metricNames = Object.keys(currentData.metricsDelta.before);
    const unresolvedAnomalies = currentData.anomalies.filter((a) => !a.resolved);

    return (
        <Box className="max-w-[800px] mx-auto">

            {/* Observation Window */}
            <Card className="mb-6">
                <CardContent>
                    <Box className="flex items-center gap-2 mb-4">
                        <TimerIcon tone="primary" />
                        <Typography as="p" className="text-lg font-medium" fontWeight={600}>
                            Observation Window
                        </Typography>
                        <Chip
                            label={currentData.observationWindow.status}
                            size="sm"
                            color={currentData.observationWindow.status === 'COMPLETED' ? 'success' : 'primary'}
                        />
                    </Box>

                    <Box className="mb-2">
                        <Box className="flex justify-between mb-1">
                            <Typography as="p" className="text-sm" color="text.secondary">
                                {progress.toFixed(0)}% complete
                            </Typography>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                {hoursRemaining.toFixed(1)}h remaining
                            </Typography>
                        </Box>
                        <LinearProgress variant="determinate" value={progress} className="h-[8px] rounded-2xl" />
                    </Box>

                    <Box className="flex gap-2 mt-4">
                        <Button
                            size="sm"
                            variant="outlined"
                            onClick={() =>
                                handleChange('observationWindow', {
                                    ...currentData.observationWindow,
                                    durationHours: currentData.observationWindow.durationHours + 24,
                                    status: 'EXTENDED',
                                })
                            }
                        >
                            Extend +24h
                        </Button>
                        <Button
                            size="sm"
                            variant="solid"
                            tone="success"
                            startIcon={<StableIcon />}
                            onClick={() =>
                                handleChange('observationWindow', {
                                    ...currentData.observationWindow,
                                    endedAt: new Date().toISOString(),
                                    status: 'COMPLETED',
                                })
                            }
                        >
                            Confirm Stability
                        </Button>
                    </Box>
                </CardContent>
            </Card>

            {/* Metrics Comparison */}
            <Card className="mb-6">
                <CardContent>
                    <Typography as="p" className="text-lg font-medium" gutterBottom fontWeight={600}>
                        Metrics Comparison
                    </Typography>

                    <Box className="flex gap-2 mb-4 flex-wrap">
                        <TextField
                            size="sm"
                            placeholder="Metric name"
                            value={newMetricName}
                            onChange={(e) => setNewMetricName(e.target.value)}
                            className="min-w-[150px]"
                        />
                        <TextField
                            size="sm"
                            placeholder="Before"
                            type="number"
                            value={newMetricBefore}
                            onChange={(e) => setNewMetricBefore(e.target.value)}
                            className="w-[100px]"
                        />
                        <TextField
                            size="sm"
                            placeholder="After"
                            type="number"
                            value={newMetricAfter}
                            onChange={(e) => setNewMetricAfter(e.target.value)}
                            className="w-[100px]"
                        />
                        <Button
                            variant="outlined"
                            onClick={handleAddMetric}
                            disabled={!newMetricName.trim() || !newMetricBefore || !newMetricAfter}
                            startIcon={<AddIcon />}
                        >
                            Add
                        </Button>
                    </Box>

                    {metricNames.length === 0 ? (
                        <Alert severity="info">No metrics tracked yet. Add before/after metrics to compare.</Alert>
                    ) : (
                        <List>
                            {metricNames.map((name, index) => {
                                const before = currentData.metricsDelta.before[name];
                                const after = currentData.metricsDelta.after[name];
                                const change = currentData.metricsDelta.percentChange[name];
                                const isUp = change > 0;

                                return (
                                    <React.Fragment key={name}>
                                        <ListItem className="px-0">
                                            <ListItemText
                                                primary={name}
                                                secondary={`${before} → ${after}`}
                                            />
                                            <Chip
                                                icon={isUp ? <UpIcon /> : <DownIcon />}
                                                label={`${change > 0 ? '+' : ''}${change.toFixed(1)}%`}
                                                size="sm"
                                                color={Math.abs(change) > 20 ? 'warning' : 'default'}
                                                className="mr-2"
                                            />
                                            <IconButton size="sm" onClick={() => handleRemoveMetric(name)}>
                                                <DeleteIcon size={16} />
                                            </IconButton>
                                        </ListItem>
                                        {index < metricNames.length - 1 && <Divider />}
                                    </React.Fragment>
                                );
                            })}
                        </List>
                    )}
                </CardContent>
            </Card>

            {/* Anomalies */}
            <Card>
                <CardContent>
                    <Box className="flex items-center gap-2 mb-4">
                        <AnomalyIcon color={unresolvedAnomalies.length > 0 ? 'warning' : 'success'} />
                        <Typography as="p" className="text-lg font-medium" fontWeight={600}>
                            Anomalies ({unresolvedAnomalies.length} unresolved)
                        </Typography>
                    </Box>

                    <Box className="flex gap-2 mb-4 flex-wrap">
                        <FormControl size="sm" className="min-w-[120px]">
                            <InputLabel>Type</InputLabel>
                            <Select
                                value={newAnomalyType}
                                label="Type"
                                onChange={(e) => setNewAnomalyType(e.target.value as Anomaly['type'])}
                            >
                                {ANOMALY_TYPES.map((t) => (
                                    <MenuItem key={t.value} value={t.value}>{t.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <FormControl size="sm" className="min-w-[100px]">
                            <InputLabel>Severity</InputLabel>
                            <Select
                                value={newAnomalySeverity}
                                label="Severity"
                                onChange={(e) => setNewAnomalySeverity(e.target.value as Anomaly['severity'])}
                            >
                                {SEVERITY_LEVELS.map((s) => (
                                    <MenuItem key={s.value} value={s.value}>{s.label}</MenuItem>
                                ))}
                            </Select>
                        </FormControl>
                        <TextField
                            size="sm"
                            placeholder="Metric"
                            value={newAnomalyMetric}
                            onChange={(e) => setNewAnomalyMetric(e.target.value)}
                            className="min-w-[120px]"
                        />
                        <TextField
                            size="sm"
                            placeholder="Description"
                            value={newAnomalyDesc}
                            onChange={(e) => setNewAnomalyDesc(e.target.value)}
                            className="grow min-w-[200px]"
                        />
                        <Button
                            variant="solid"
                            tone="warning"
                            onClick={handleAddAnomaly}
                            disabled={!newAnomalyMetric.trim() || !newAnomalyDesc.trim()}
                            startIcon={<AddIcon />}
                        >
                            Report
                        </Button>
                    </Box>

                    {currentData.anomalies.length > 0 && (
                        <List>
                            {currentData.anomalies.map((anomaly, index) => {
                                const severity = SEVERITY_LEVELS.find((s) => s.value === anomaly.severity);

                                return (
                                    <React.Fragment key={anomaly.id}>
                                        <ListItem className="px-0" style={{ opacity: anomaly.resolved ? 0.5 : 1 }}>
                                            <ListItemIcon className="min-w-[40px]">
                                                <AnomalyIcon color={anomaly.resolved ? 'disabled' : severity?.color} />
                                            </ListItemIcon>
                                            <ListItemText
                                                primary={
                                                    <Box className="flex items-center gap-2">
                                                        <Typography
                                                            as="p" className="text-sm"
                                                            style={{ textDecoration: anomaly.resolved ? 'line-through' : 'none' }}
                                                        >
                                                            {anomaly.description}
                                                        </Typography>
                                                        <Chip label={anomaly.type} size="sm" variant="outlined" className="h-[18px] text-[10px]" />
                                                    </Box>
                                                }
                                                secondary={`Metric: ${anomaly.metric}`}
                                            />
                                            <Chip
                                                label={anomaly.resolved ? 'Resolved' : severity?.label}
                                                size="sm"
                                                color={anomaly.resolved ? 'success' : severity?.color}
                                                className="mr-2"
                                            />
                                            <Button
                                                size="sm"
                                                onClick={() => handleToggleAnomalyResolved(anomaly.id)}
                                            >
                                                {anomaly.resolved ? 'Reopen' : 'Resolve'}
                                            </Button>
                                            <IconButton size="sm" onClick={() => handleRemoveAnomaly(anomaly.id)}>
                                                <DeleteIcon size={16} />
                                            </IconButton>
                                        </ListItem>
                                        {index < currentData.anomalies.length - 1 && <Divider />}
                                    </React.Fragment>
                                );
                            })}
                        </List>
                    )}
                </CardContent>
            </Card>
        </Box>
    );
}

export default ObserveStep;
