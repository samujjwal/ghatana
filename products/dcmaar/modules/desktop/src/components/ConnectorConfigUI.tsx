/**
 * @fileoverview Connector Configuration UI Component
 *
 * Provides UI for selecting and configuring connectors (sources and sinks).
 * Supports:
 * - Preset selection (Agent, Extension, Both, Multi-Agent, etc.)
 * - Custom connector configuration
 * - Visual status of active connectors
 * - Add/remove connectors at runtime
 */

import React, { useState, useEffect } from 'react';
import {
    Box,
    Card,
    CardContent,
    Typography,
    Button,
    Grid,
    Chip,
    IconButton,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    TextField,
    Select,
    MenuItem,
    FormControl,
    InputLabel,
    Switch,
    FormControlLabel,
    Alert,
    Divider,
    List,
    ListItem,
    ListItemText,
    ListItemSecondaryAction,
    Paper,
    Tooltip,
} from '@mui/material';
import {
    Add as AddIcon,
    Delete as DeleteIcon,
    Edit as EditIcon,
    CheckCircle as CheckCircleIcon,
    Error as ErrorIcon,
    PlayArrow as PlayIcon,
    Stop as StopIcon,
    Settings as SettingsIcon,
} from '@mui/icons-material';
import type {
    ConnectorConfig,
    DesktopConnectorConfig,
    PresetType,
    ConnectorState,
    ConnectorPreset,
} from '../libs/connectors';
import {
    getRecommendedPresets,
    createConfigFromPreset,
    validateConnectorConfig,
} from '../libs/connectors';

interface ConnectorConfigUIProps {
    /** Current connector configuration */
    config?: DesktopConnectorConfig;
    /** Current connector state */
    state?: ConnectorState;
    /** Callback when configuration changes */
    onChange?: (config: DesktopConnectorConfig) => void;
    /** Callback to apply configuration */
    onApply?: (config: DesktopConnectorConfig) => Promise<void>;
    /** Callback to start/stop connectors */
    onToggleConnector?: (connectorId: string, type: 'source' | 'sink', start: boolean) => Promise<void>;
    /** Workspace ID */
    workspaceId?: string;
}

export const ConnectorConfigUI: React.FC<ConnectorConfigUIProps> = ({
    config,
    state,
    onChange,
    onApply,
    onToggleConnector,
    workspaceId = 'default',
}) => {
    const [selectedPreset, setSelectedPreset] = useState<PresetType | 'custom'>('custom');
    const [currentConfig, setCurrentConfig] = useState<DesktopConnectorConfig>(
        config || {
            workspaceId,
            sources: [],
            sinks: [],
            autoStart: true,
            logging: { level: 'info', enabled: true },
            healthCheckInterval: 30000,
        },
    );
    const [showAddDialog, setShowAddDialog] = useState(false);
    const [editingConnector, setEditingConnector] = useState<{
        connector: ConnectorConfig;
        type: 'source' | 'sink';
        index: number;
    } | null>(null);
    const [validationErrors, setValidationErrors] = useState<string[]>([]);
    const [applying, setApplying] = useState(false);

    useEffect(() => {
        if (config) {
            setCurrentConfig(config);
        }
    }, [config]);

    const handlePresetChange = (presetId: PresetType | 'custom') => {
        setSelectedPreset(presetId);
        if (presetId !== 'custom') {
            const newConfig = createConfigFromPreset(presetId, workspaceId);
            setCurrentConfig(newConfig);
            onChange?.(newConfig);
        }
    };

    const handleAddConnector = (type: 'source' | 'sink') => {
        setEditingConnector({
            connector: {
                id: `${type}-${Date.now()}`,
                name: `New ${type}`,
                type: 'agent',
                enabled: true,
                options: {},
            },
            type,
            index: -1,
        });
        setShowAddDialog(true);
    };

    const handleEditConnector = (
        connector: ConnectorConfig,
        type: 'source' | 'sink',
        index: number,
    ) => {
        setEditingConnector({ connector: { ...connector }, type, index });
        setShowAddDialog(true);
    };

    const handleSaveConnector = () => {
        if (!editingConnector) return;

        const newConfig = { ...currentConfig };
        const list = editingConnector.type === 'source' ? newConfig.sources : newConfig.sinks;

        if (editingConnector.index >= 0) {
            list[editingConnector.index] = editingConnector.connector;
        } else {
            list.push(editingConnector.connector);
        }

        setCurrentConfig(newConfig);
        onChange?.(newConfig);
        setShowAddDialog(false);
        setEditingConnector(null);
        setSelectedPreset('custom');
    };

    const handleDeleteConnector = (type: 'source' | 'sink', index: number) => {
        const newConfig = { ...currentConfig };
        const list = type === 'source' ? newConfig.sources : newConfig.sinks;
        list.splice(index, 1);

        setCurrentConfig(newConfig);
        onChange?.(newConfig);
        setSelectedPreset('custom');
    };

    const handleApply = async () => {
        const validation = validateConnectorConfig(currentConfig);
        if (!validation.valid) {
            setValidationErrors(validation.errors);
            return;
        }

        setValidationErrors([]);
        setApplying(true);

        try {
            await onApply?.(currentConfig);
        } catch (error) {
            setValidationErrors([(error as Error).message]);
        } finally {
            setApplying(false);
        }
    };

    const renderPresetCards = () => {
        const presets = getRecommendedPresets();

        return (
            <Grid container spacing={2} sx={{ mb: 3 }}>
                {presets.map((preset: ConnectorPreset) => (
                    <Grid size={{ xs: 12, sm: 6, md: 4 }} key={preset.id}>
                        <Card
                            variant={selectedPreset === preset.id ? 'elevation' : 'outlined'}
                            sx={{
                                cursor: 'pointer',
                                border:
                                    selectedPreset === preset.id ? '2px solid' : undefined,
                                borderColor: 'primary.main',
                                transition: 'all 0.2s',
                                '&:hover': {
                                    boxShadow: 3,
                                },
                            }}
                            onClick={() => handlePresetChange(preset.id)}
                        >
                            <CardContent>
                                <Box display="flex" justifyContent="space-between" alignItems="start" mb={1}>
                                    <Typography variant="h6" component="div">
                                        {preset.name}
                                    </Typography>
                                    {preset.recommended && (
                                        <Chip label="Recommended" size="small" color="primary" />
                                    )}
                                </Box>
                                <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
                                    {preset.description}
                                </Typography>
                                <Box display="flex" flexWrap="wrap" gap={0.5}>
                                    {preset.tags.map((tag: string) => (
                                        <Chip key={tag} label={tag} size="small" variant="outlined" />
                                    ))}
                                </Box>
                            </CardContent>
                        </Card>
                    </Grid>
                ))}
                <Grid size={{ xs: 12, sm: 6, md: 4 }}>
                    <Card
                        variant={selectedPreset === 'custom' ? 'elevation' : 'outlined'}
                        sx={{
                            cursor: 'pointer',
                            border: selectedPreset === 'custom' ? '2px solid' : undefined,
                            borderColor: 'primary.main',
                            transition: 'all 0.2s',
                            '&:hover': {
                                boxShadow: 3,
                            },
                        }}
                        onClick={() => handlePresetChange('custom')}
                    >
                        <CardContent>
                            <Box display="flex" alignItems="center" mb={1}>
                                <SettingsIcon sx={{ mr: 1 }} />
                                <Typography variant="h6">Custom</Typography>
                            </Box>
                            <Typography variant="body2" color="text.secondary">
                                Configure sources and sinks manually for specific requirements.
                            </Typography>
                        </CardContent>
                    </Card>
                </Grid>
            </Grid>
        );
    };

    const renderConnectorList = (
        connectors: ConnectorConfig[],
        type: 'source' | 'sink',
    ) => (
        <Paper variant="outlined" sx={{ p: 2 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                <Typography variant="h6">
                    {type === 'source' ? 'Sources' : 'Sinks'} ({connectors.length})
                </Typography>
                <Button
                    startIcon={<AddIcon />}
                    variant="outlined"
                    size="small"
                    onClick={() => handleAddConnector(type)}
                >
                    Add {type}
                </Button>
            </Box>

            {connectors.length === 0 ? (
                <Alert severity="info">
                    No {type}s configured. Add at least one {type} to get started.
                </Alert>
            ) : (
                <List>
                    {connectors.map((connector, index) => (
                        <React.Fragment key={connector.id}>
                            {index > 0 && <Divider />}
                            <ListItem>
                                <Box display="flex" alignItems="center" mr={2}>
                                    {connector.enabled ? (
                                        <CheckCircleIcon color="success" />
                                    ) : (
                                        <ErrorIcon color="disabled" />
                                    )}
                                </Box>
                                <ListItemText
                                    primary={
                                        <Box display="flex" alignItems="center" gap={1}>
                                            <Typography variant="subtitle1">{connector.name}</Typography>
                                            <Chip label={connector.type} size="small" />
                                            {connector.tags?.map((tag: string) => (
                                                <Chip key={tag} label={tag} size="small" variant="outlined" />
                                            ))}
                                        </Box>
                                    }
                                    secondary={`ID: ${connector.id} | Priority: ${connector.priority || 'default'}`}
                                />
                                <ListItemSecondaryAction>
                                    <Tooltip title={connector.enabled ? 'Stop' : 'Start'}>
                                        <IconButton
                                            edge="end"
                                            onClick={() =>
                                                onToggleConnector?.(connector.id, type, !connector.enabled)
                                            }
                                        >
                                            {connector.enabled ? <StopIcon /> : <PlayIcon />}
                                        </IconButton>
                                    </Tooltip>
                                    <Tooltip title="Edit">
                                        <IconButton
                                            edge="end"
                                            onClick={() => handleEditConnector(connector, type, index)}
                                        >
                                            <EditIcon />
                                        </IconButton>
                                    </Tooltip>
                                    <Tooltip title="Delete">
                                        <IconButton
                                            edge="end"
                                            onClick={() => handleDeleteConnector(type, index)}
                                        >
                                            <DeleteIcon />
                                        </IconButton>
                                    </Tooltip>
                                </ListItemSecondaryAction>
                            </ListItem>
                        </React.Fragment>
                    ))}
                </List>
            )}
        </Paper>
    );

    const renderEditDialog = () => {
        if (!editingConnector) return null;

        const { connector } = editingConnector;

        return (
            <Dialog open={showAddDialog} onClose={() => setShowAddDialog(false)} maxWidth="sm" fullWidth>
                <DialogTitle>
                    {editingConnector.index >= 0 ? 'Edit' : 'Add'} {editingConnector.type}
                </DialogTitle>
                <DialogContent>
                    <Box sx={{ pt: 1 }}>
                        <TextField
                            fullWidth
                            label="Name"
                            value={connector.name}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                setEditingConnector({
                                    ...editingConnector,
                                    connector: { ...connector, name: e.target.value },
                                })
                            }
                            margin="normal"
                        />

                        <FormControl fullWidth margin="normal">
                            <InputLabel>Type</InputLabel>
                            <Select
                                value={connector.type}
                                label="Type"
                                onChange={(e: any) =>
                                    setEditingConnector({
                                        ...editingConnector,
                                        connector: {
                                            ...connector,
                                            type: e.target.value as ConnectorConfig['type'],
                                        },
                                    })
                                }
                            >
                                <MenuItem value="agent">Agent</MenuItem>
                                <MenuItem value="extension">Extension</MenuItem>
                                <MenuItem value="http">HTTP</MenuItem>
                                <MenuItem value="grpc">gRPC</MenuItem>
                                <MenuItem value="bridge">Bridge</MenuItem>
                                <MenuItem value="file">File</MenuItem>
                                <MenuItem value="mock">Mock</MenuItem>
                            </Select>
                        </FormControl>

                        <TextField
                            fullWidth
                            label="ID"
                            value={connector.id}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                setEditingConnector({
                                    ...editingConnector,
                                    connector: { ...connector, id: e.target.value },
                                })
                            }
                            margin="normal"
                            disabled={editingConnector.index >= 0}
                        />

                        <TextField
                            fullWidth
                            label="Priority"
                            type="number"
                            value={connector.priority || 1}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                setEditingConnector({
                                    ...editingConnector,
                                    connector: { ...connector, priority: parseInt(e.target.value) },
                                })
                            }
                            margin="normal"
                        />

                        <FormControlLabel
                            control={
                                <Switch
                                    checked={connector.enabled}
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                        setEditingConnector({
                                            ...editingConnector,
                                            connector: { ...connector, enabled: e.target.checked },
                                        })
                                    }
                                />
                            }
                            label="Enabled"
                            sx={{ mt: 2 }}
                        />

                        <TextField
                            fullWidth
                            label="Options (JSON)"
                            value={JSON.stringify(connector.options, null, 2)}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                                try {
                                    const options = JSON.parse(e.target.value);
                                    setEditingConnector({
                                        ...editingConnector,
                                        connector: { ...connector, options },
                                    });
                                } catch {
                                    // Invalid JSON, ignore
                                }
                            }}
                            margin="normal"
                            multiline
                            rows={4}
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setShowAddDialog(false)}>Cancel</Button>
                    <Button onClick={handleSaveConnector} variant="contained">
                        Save
                    </Button>
                </DialogActions>
            </Dialog>
        );
    };

    const renderStatusBar = () => {
        if (!state) return null;

        return (
            <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
                <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <Typography variant="caption" color="text.secondary">
                            Status
                        </Typography>
                        <Box display="flex" alignItems="center" gap={1}>
                            {state.healthy ? (
                                <>
                                    <CheckCircleIcon color="success" />
                                    <Typography>Healthy</Typography>
                                </>
                            ) : (
                                <>
                                    <ErrorIcon color="error" />
                                    <Typography>Unhealthy</Typography>
                                </>
                            )}
                        </Box>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <Typography variant="caption" color="text.secondary">
                            Active Sources
                        </Typography>
                        <Typography variant="h6">
                            {state.activeSourcesCount} / {state.totalSourcesCount}
                        </Typography>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <Typography variant="caption" color="text.secondary">
                            Active Sinks
                        </Typography>
                        <Typography variant="h6">
                            {state.activeSinksCount} / {state.totalSinksCount}
                        </Typography>
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6, md: 3 }}>
                        <Typography variant="caption" color="text.secondary">
                            Last Health Check
                        </Typography>
                        <Typography variant="body2">
                            {state.lastHealthCheck
                                ? new Date(state.lastHealthCheck).toLocaleTimeString()
                                : 'N/A'}
                        </Typography>
                    </Grid>
                </Grid>

                {state.errors.length > 0 && (
                    <Alert severity="error" sx={{ mt: 2 }}>
                        <Typography variant="subtitle2">Errors:</Typography>
                        <ul style={{ margin: '8px 0 0 0', paddingLeft: '20px' }}>
                            {state.errors.map((error: string, i: number) => (
                                <li key={i}>{error}</li>
                            ))}
                        </ul>
                    </Alert>
                )}
            </Paper>
        );
    };

    return (
        <Box>
            <Typography variant="h4" gutterBottom>
                Connector Configuration
            </Typography>

            <Typography variant="body1" color="text.secondary" sx={{ mb: 3 }}>
                Configure data sources and command sinks for the desktop application. Choose a preset or
                create a custom configuration.
            </Typography>

            {renderStatusBar()}

            <Typography variant="h5" gutterBottom>
                Choose a Preset
            </Typography>
            {renderPresetCards()}

            <Divider sx={{ my: 4 }} />

            <Typography variant="h5" gutterBottom>
                Current Configuration
            </Typography>

            {validationErrors.length > 0 && (
                <Alert severity="error" sx={{ mb: 2 }}>
                    <Typography variant="subtitle2">Configuration Errors:</Typography>
                    <ul style={{ margin: '8px 0 0 0', paddingLeft: '20px' }}>
                        {validationErrors.map((error, i) => (
                            <li key={i}>{error}</li>
                        ))}
                    </ul>
                </Alert>
            )}

            <Grid container spacing={3}>
                <Grid size={{ xs: 12, md: 6 }}>
                    {renderConnectorList(currentConfig.sources, 'source')}
                </Grid>
                <Grid size={{ xs: 12, md: 6 }}>
                    {renderConnectorList(currentConfig.sinks, 'sink')}
                </Grid>
            </Grid>

            <Box display="flex" justifyContent="flex-end" gap={2} mt={3}>
                <FormControlLabel
                    control={
                        <Switch
                            checked={currentConfig.autoStart}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => {
                                const newConfig = { ...currentConfig, autoStart: e.target.checked };
                                setCurrentConfig(newConfig);
                                onChange?.(newConfig);
                            }}
                        />
                    }
                    label="Auto-start connectors"
                />
                <Button
                    variant="contained"
                    size="large"
                    onClick={handleApply}
                    disabled={applying || validationErrors.length > 0}
                >
                    {applying ? 'Applying...' : 'Apply Configuration'}
                </Button>
            </Box>

            {renderEditDialog()}
        </Box>
    );
};

export default ConnectorConfigUI;
