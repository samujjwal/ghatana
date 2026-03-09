/**
 * Property Panels for Persona Nodes
 *
 * Reusable property panel components for editing node-specific configurations.
 * Implements YAPPC_USER_JOURNEYS.md requirements:
 * - Journey 2.1 (Architect): Schema Designer for DatabaseNode
 * - Journey 2.1 (Architect): Deployment Config for ServiceNode
 * - Journey 6.1 (Backend): API Designer for APIEndpointNode
 *
 * @doc.type module
 * @doc.purpose Property editors for persona nodes
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Box, Typography, Select, MenuItem, FormControl, InputLabel, IconButton, Chip, Divider, Stack, Switch, FormControlLabel, Accordion, AccordionSummary, AccordionDetails, FormHelperText } from '@ghatana/ui';
import { Plus as AddIcon, Trash2 as DeleteIcon, ChevronDown as ExpandMoreIcon } from 'lucide-react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

export interface SchemaField {
    name: string;
    type: 'string' | 'number' | 'boolean' | 'date' | 'uuid' | 'text' | 'json';
    nullable?: boolean;
    unique?: boolean;
    default?: string;
    length?: number;
    description?: string;
}

export interface DeploymentConfig {
    replicas: number;
    memory: string;
    cpu: string;
    port: number;
    environment?: Record<string, string>;
    healthCheck?: {
        path: string;
        interval: number;
    };
    autoScaling?: {
        enabled: boolean;
        minReplicas?: number;
        maxReplicas?: number;
        targetCPU?: number;
    };
}

export interface APIEndpointConfig {
    method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    path: string;
    description?: string;
    requestBody?: {
        type: string;
        schema: Record<string, unknown>;
    };
    responseBody?: {
        type: string;
        schema: Record<string, unknown>;
    };
    authentication?: 'none' | 'jwt' | 'apiKey' | 'oauth2';
    rateLimit?: {
        enabled: boolean;
        requests: number;
        window: string;
    };
}

// ============================================================================
// SCHEMA DESIGNER PANEL (Database Node)
// ============================================================================

export interface SchemaDesignerPanelProps {
    open: boolean;
    onClose: () => void;
    initialFields?: SchemaField[];
    tableName?: string;
    onSave: (tableName: string, fields: SchemaField[]) => void;
}

export function SchemaDesignerPanel({
    open,
    onClose,
    initialFields = [],
    tableName: initialTableName = '',
    onSave,
}: SchemaDesignerPanelProps) {
    const [tableName, setTableName] = useState(initialTableName);
    const [fields, setFields] = useState<SchemaField[]>(initialFields.length > 0 ? initialFields : [
        { name: 'id', type: 'uuid', nullable: false, unique: true },
    ]);

    const handleAddField = useCallback(() => {
        setFields([...fields, { name: '', type: 'string' }]);
    }, [fields]);

    const handleRemoveField = useCallback((index: number) => {
        setFields(fields.filter((_, i) => i !== index));
    }, [fields]);

    const handleFieldChange = useCallback((index: number, updates: Partial<SchemaField>) => {
        setFields(fields.map((field, i) => i === index ? { ...field, ...updates } : field));
    }, [fields]);

    const handleSave = useCallback(() => {
        if (!tableName.trim()) {
            alert('Please enter a table name');
            return;
        }
        if (fields.some(f => !f.name.trim())) {
            alert('All fields must have a name');
            return;
        }
        onSave(tableName, fields);
        onClose();
    }, [tableName, fields, onSave, onClose]);

    return (
        <Dialog open={open} onClose={onClose} size="md" fullWidth>
            <DialogTitle>Schema Designer</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={3}>
                    {/* Table Name */}
                    <TextField
                        label="Table Name"
                        value={tableName}
                        onChange={(e) => setTableName(e.target.value)}
                        fullWidth
                        placeholder="e.g., users, orders, products"
                        helperText="Database table name (lowercase, snake_case recommended)"
                    />

                    <Divider />

                    {/* Fields */}
                    <Box>
                        <Box className="flex justify-between items-center mb-4">
                            <Typography as="h6">Fields</Typography>
                            <Button
                                startIcon={<AddIcon />}
                                onClick={handleAddField}
                                variant="outlined"
                                size="sm"
                            >
                                Add Field
                            </Button>
                        </Box>

                        <Stack spacing={2}>
                            {fields.map((field, index) => (
                                <Box
                                    key={index}
                                    className="p-4 rounded border border-solid border-gray-200 dark:border-gray-700"
                                >
                                    <Stack spacing={2}>
                                        {/* Field Name and Type */}
                                        <Box className="flex gap-2 items-start">
                                            <TextField
                                                label="Field Name"
                                                value={field.name}
                                                onChange={(e) => handleFieldChange(index, { name: e.target.value })}
                                                size="sm"
                                                fullWidth
                                                placeholder="e.g., user_id, email"
                                            />
                                            <FormControl size="sm" className="min-w-[120px]">
                                                <InputLabel>Type</InputLabel>
                                                <Select
                                                    value={field.type}
                                                    onChange={(e) => handleFieldChange(index, { type: e.target.value as SchemaField['type'] })}
                                                    label="Type"
                                                >
                                                    <MenuItem value="string">String</MenuItem>
                                                    <MenuItem value="number">Number</MenuItem>
                                                    <MenuItem value="boolean">Boolean</MenuItem>
                                                    <MenuItem value="date">Date</MenuItem>
                                                    <MenuItem value="uuid">UUID</MenuItem>
                                                    <MenuItem value="text">Text</MenuItem>
                                                    <MenuItem value="json">JSON</MenuItem>
                                                </Select>
                                            </FormControl>
                                            <IconButton
                                                onClick={() => handleRemoveField(index)}
                                                tone="danger"
                                                size="sm"
                                                disabled={index === 0} // Don't allow removing the ID field
                                            >
                                                <DeleteIcon />
                                            </IconButton>
                                        </Box>

                                        {/* Field Options */}
                                        <Box className="flex gap-4 flex-wrap">
                                            <FormControlLabel
                                                control={
                                                    <Switch
                                                        checked={field.nullable || false}
                                                        onChange={(e) => handleFieldChange(index, { nullable: e.target.checked })}
                                                        size="sm"
                                                    />
                                                }
                                                label="Nullable"
                                            />
                                            <FormControlLabel
                                                control={
                                                    <Switch
                                                        checked={field.unique || false}
                                                        onChange={(e) => handleFieldChange(index, { unique: e.target.checked })}
                                                        size="sm"
                                                    />
                                                }
                                                label="Unique"
                                            />
                                        </Box>

                                        {/* Description */}
                                        <TextField
                                            label="Description (optional)"
                                            value={field.description || ''}
                                            onChange={(e) => handleFieldChange(index, { description: e.target.value })}
                                            size="sm"
                                            fullWidth
                                            multiline
                                            rows={1}
                                        />
                                    </Stack>
                                </Box>
                            ))}
                        </Stack>
                    </Box>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button onClick={handleSave} variant="solid" tone="primary">
                    Save Schema
                </Button>
            </DialogActions>
        </Dialog>
    );
}

// ============================================================================
// DEPLOYMENT CONFIG PANEL (Service Node)
// ============================================================================

export interface DeploymentConfigPanelProps {
    open: boolean;
    onClose: () => void;
    initialConfig?: DeploymentConfig;
    serviceName?: string;
    onSave: (config: DeploymentConfig) => void;
}

export function DeploymentConfigPanel({
    open,
    onClose,
    initialConfig,
    serviceName = 'Service',
    onSave,
}: DeploymentConfigPanelProps) {
    const [config, setConfig] = useState<DeploymentConfig>(initialConfig || {
        replicas: 2,
        memory: '512Mi',
        cpu: '500m',
        port: 8080,
        environment: {},
        healthCheck: {
            path: '/health',
            interval: 30,
        },
        autoScaling: {
            enabled: false,
            minReplicas: 2,
            maxReplicas: 10,
            targetCPU: 70,
        },
    });

    const [envKey, setEnvKey] = useState('');
    const [envValue, setEnvValue] = useState('');

    const handleUpdate = useCallback((updates: Partial<DeploymentConfig>) => {
        setConfig({ ...config, ...updates });
    }, [config]);

    const handleAddEnvVar = useCallback(() => {
        if (!envKey.trim()) return;
        handleUpdate({
            environment: { ...config.environment, [envKey]: envValue },
        });
        setEnvKey('');
        setEnvValue('');
    }, [envKey, envValue, config.environment, handleUpdate]);

    const handleRemoveEnvVar = useCallback((key: string) => {
        const newEnv = { ...config.environment };
        delete newEnv[key];
        handleUpdate({ environment: newEnv });
    }, [config.environment, handleUpdate]);

    const handleSave = useCallback(() => {
        onSave(config);
        onClose();
    }, [config, onSave, onClose]);

    return (
        <Dialog open={open} onClose={onClose} size="md" fullWidth>
            <DialogTitle>Deployment Configuration - {serviceName}</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={3}>
                    {/* Basic Resources */}
                    <Accordion defaultExpanded>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Resources</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Stack spacing={2}>
                                <TextField
                                    label="Replicas"
                                    type="number"
                                    value={config.replicas}
                                    onChange={(e) => handleUpdate({ replicas: parseInt(e.target.value) || 1 })}
                                    fullWidth
                                    helperText="Number of pod replicas"
                                />
                                <TextField
                                    label="Memory Limit"
                                    value={config.memory}
                                    onChange={(e) => handleUpdate({ memory: e.target.value })}
                                    fullWidth
                                    placeholder="e.g., 512Mi, 1Gi"
                                    helperText="Kubernetes memory format"
                                />
                                <TextField
                                    label="CPU Limit"
                                    value={config.cpu}
                                    onChange={(e) => handleUpdate({ cpu: e.target.value })}
                                    fullWidth
                                    placeholder="e.g., 500m, 1"
                                    helperText="Kubernetes CPU format (millicores)"
                                />
                                <TextField
                                    label="Port"
                                    type="number"
                                    value={config.port}
                                    onChange={(e) => handleUpdate({ port: parseInt(e.target.value) || 8080 })}
                                    fullWidth
                                    helperText="Container port"
                                />
                            </Stack>
                        </AccordionDetails>
                    </Accordion>

                    {/* Health Check */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Health Check</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Stack spacing={2}>
                                <TextField
                                    label="Health Check Path"
                                    value={config.healthCheck?.path || ''}
                                    onChange={(e) => handleUpdate({
                                        healthCheck: { ...config.healthCheck, path: e.target.value } as unknown,
                                    })}
                                    fullWidth
                                    placeholder="/health or /actuator/health"
                                />
                                <TextField
                                    label="Interval (seconds)"
                                    type="number"
                                    value={config.healthCheck?.interval || 30}
                                    onChange={(e) => handleUpdate({
                                        healthCheck: { ...config.healthCheck, interval: parseInt(e.target.value) || 30 } as unknown,
                                    })}
                                    fullWidth
                                />
                            </Stack>
                        </AccordionDetails>
                    </Accordion>

                    {/* Auto Scaling */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Auto Scaling</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Stack spacing={2}>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={config.autoScaling?.enabled || false}
                                            onChange={(e) => handleUpdate({
                                                autoScaling: { ...config.autoScaling, enabled: e.target.checked } as unknown,
                                            })}
                                        />
                                    }
                                    label="Enable Horizontal Pod Autoscaler"
                                />
                                {config.autoScaling?.enabled && (
                                    <>
                                        <TextField
                                            label="Min Replicas"
                                            type="number"
                                            value={config.autoScaling.minReplicas || 2}
                                            onChange={(e) => handleUpdate({
                                                autoScaling: { ...config.autoScaling, minReplicas: parseInt(e.target.value) || 2 } as unknown,
                                            })}
                                            fullWidth
                                        />
                                        <TextField
                                            label="Max Replicas"
                                            type="number"
                                            value={config.autoScaling.maxReplicas || 10}
                                            onChange={(e) => handleUpdate({
                                                autoScaling: { ...config.autoScaling, maxReplicas: parseInt(e.target.value) || 10 } as unknown,
                                            })}
                                            fullWidth
                                        />
                                        <TextField
                                            label="Target CPU Utilization (%)"
                                            type="number"
                                            value={config.autoScaling.targetCPU || 70}
                                            onChange={(e) => handleUpdate({
                                                autoScaling: { ...config.autoScaling, targetCPU: parseInt(e.target.value) || 70 } as unknown,
                                            })}
                                            fullWidth
                                        />
                                    </>
                                )}
                            </Stack>
                        </AccordionDetails>
                    </Accordion>

                    {/* Environment Variables */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Environment Variables</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Stack spacing={2}>
                                {/* Add new env var */}
                                <Box className="flex gap-2">
                                    <TextField
                                        label="Key"
                                        value={envKey}
                                        onChange={(e) => setEnvKey(e.target.value)}
                                        size="sm"
                                        placeholder="DATABASE_URL"
                                    />
                                    <TextField
                                        label="Value"
                                        value={envValue}
                                        onChange={(e) => setEnvValue(e.target.value)}
                                        size="sm"
                                        fullWidth
                                        placeholder="postgres://..."
                                    />
                                    <Button
                                        onClick={handleAddEnvVar}
                                        variant="outlined"
                                        size="sm"
                                    >
                                        Add
                                    </Button>
                                </Box>

                                {/* Existing env vars */}
                                {Object.entries(config.environment || {}).length > 0 && (
                                    <Stack spacing={1}>
                                        {Object.entries(config.environment || {}).map(([key, value]) => (
                                            <Chip
                                                key={key}
                                                label={`${key}=${value}`}
                                                onDelete={() => handleRemoveEnvVar(key)}
                                                size="sm"
                                            />
                                        ))}
                                    </Stack>
                                )}
                            </Stack>
                        </AccordionDetails>
                    </Accordion>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button onClick={handleSave} variant="solid" tone="primary">
                    Save Configuration
                </Button>
            </DialogActions>
        </Dialog>
    );
}

// ============================================================================
// API DESIGNER PANEL (API Endpoint Node)
// ============================================================================

export interface APIDesignerPanelProps {
    open: boolean;
    onClose: () => void;
    initialConfig?: APIEndpointConfig;
    onSave: (config: APIEndpointConfig) => void;
}

export function APIDesignerPanel({
    open,
    onClose,
    initialConfig,
    onSave,
}: APIDesignerPanelProps) {
    const [config, setConfig] = useState<APIEndpointConfig>(initialConfig || {
        method: 'GET',
        path: '/api/resource',
        authentication: 'jwt',
        rateLimit: {
            enabled: false,
            requests: 100,
            window: '1m',
        },
    });

    const handleUpdate = useCallback((updates: Partial<APIEndpointConfig>) => {
        setConfig({ ...config, ...updates });
    }, [config]);

    const handleSave = useCallback(() => {
        if (!config.path.trim()) {
            alert('Please enter an API path');
            return;
        }
        onSave(config);
        onClose();
    }, [config, onSave, onClose]);

    return (
        <Dialog open={open} onClose={onClose} size="md" fullWidth>
            <DialogTitle>API Endpoint Designer</DialogTitle>
            <DialogContent dividers>
                <Stack spacing={3}>
                    {/* Basic Info */}
                    <Box className="flex gap-4">
                        <FormControl className="min-w-[120px]">
                            <InputLabel>Method</InputLabel>
                            <Select
                                value={config.method}
                                onChange={(e) => handleUpdate({ method: e.target.value as APIEndpointConfig['method'] })}
                                label="Method"
                            >
                                <MenuItem value="GET">GET</MenuItem>
                                <MenuItem value="POST">POST</MenuItem>
                                <MenuItem value="PUT">PUT</MenuItem>
                                <MenuItem value="DELETE">DELETE</MenuItem>
                                <MenuItem value="PATCH">PATCH</MenuItem>
                            </Select>
                        </FormControl>
                        <TextField
                            label="Path"
                            value={config.path}
                            onChange={(e) => handleUpdate({ path: e.target.value })}
                            fullWidth
                            placeholder="/api/users/:id"
                            helperText="Use :param for path parameters"
                        />
                    </Box>

                    <TextField
                        label="Description"
                        value={config.description || ''}
                        onChange={(e) => handleUpdate({ description: e.target.value })}
                        fullWidth
                        multiline
                        rows={2}
                        placeholder="What does this endpoint do?"
                    />

                    <Divider />

                    {/* Authentication */}
                    <FormControl fullWidth>
                        <InputLabel>Authentication</InputLabel>
                        <Select
                            value={config.authentication}
                            onChange={(e) => handleUpdate({ authentication: e.target.value as APIEndpointConfig['authentication'] })}
                            label="Authentication"
                        >
                            <MenuItem value="none">None (Public)</MenuItem>
                            <MenuItem value="jwt">JWT Bearer Token</MenuItem>
                            <MenuItem value="apiKey">API Key</MenuItem>
                            <MenuItem value="oauth2">OAuth 2.0</MenuItem>
                        </Select>
                        <FormHelperText>
                            Authentication method required to access this endpoint
                        </FormHelperText>
                    </FormControl>

                    {/* Rate Limiting */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Rate Limiting</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Stack spacing={2}>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={config.rateLimit?.enabled || false}
                                            onChange={(e) => handleUpdate({
                                                rateLimit: { ...config.rateLimit, enabled: e.target.checked } as unknown,
                                            })}
                                        />
                                    }
                                    label="Enable Rate Limiting"
                                />
                                {config.rateLimit?.enabled && (
                                    <>
                                        <TextField
                                            label="Max Requests"
                                            type="number"
                                            value={config.rateLimit.requests}
                                            onChange={(e) => handleUpdate({
                                                rateLimit: { ...config.rateLimit, requests: parseInt(e.target.value) || 100 } as unknown,
                                            })}
                                            fullWidth
                                            helperText="Maximum requests per window"
                                        />
                                        <TextField
                                            label="Time Window"
                                            value={config.rateLimit.window}
                                            onChange={(e) => handleUpdate({
                                                rateLimit: { ...config.rateLimit, window: e.target.value } as unknown,
                                            })}
                                            fullWidth
                                            placeholder="1m, 1h, 1d"
                                            helperText="Time window (e.g., 1m = 1 minute, 1h = 1 hour)"
                                        />
                                    </>
                                )}
                            </Stack>
                        </AccordionDetails>
                    </Accordion>

                    {/* Request/Response (Simplified) */}
                    <Accordion>
                        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                            <Typography as="p" className="text-lg font-medium">Request/Response Schema</Typography>
                        </AccordionSummary>
                        <AccordionDetails>
                            <Typography as="p" className="text-sm" color="text.secondary">
                                OpenAPI schema generation will be available in the next version.
                                For now, use the "Generate OpenAPI Spec" context menu action.
                            </Typography>
                        </AccordionDetails>
                    </Accordion>
                </Stack>
            </DialogContent>
            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button onClick={handleSave} variant="solid" tone="primary">
                    Save Endpoint
                </Button>
            </DialogActions>
        </Dialog>
    );
}
