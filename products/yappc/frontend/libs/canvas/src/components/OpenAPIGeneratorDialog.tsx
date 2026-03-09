/**
 * OpenAPIGeneratorDialog Component
 * 
 * Dialog for generating OpenAPI 3.0 specifications from API nodes.
 * Provides YAML/JSON preview, customization options, and export functionality.
 * 
 * @doc.type component
 * @doc.purpose OpenAPI spec generation UI
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useState, useCallback, useMemo } from 'react';
import { Dialog, DialogTitle, DialogContent, DialogActions, Button, TextField, Select, MenuItem, FormControl, InputLabel, Box, Typography, Tabs, Tab, Switch, FormControlLabel, Alert, IconButton, Chip, Surface as Paper, Divider } from '@ghatana/ui';
import { X as CloseIcon, Download as DownloadIcon, Copy as CopyIcon, CheckCircle as CheckIcon, AlertCircle as ErrorIcon, Plug as APIIcon } from 'lucide-react';
import type { Node } from '@xyflow/react';
import { OpenAPIService, type OpenAPIGenerationOptions, type APINodeData } from '../services/OpenAPIService';

/**
 * OpenAPIGeneratorDialog props
 */
export interface OpenAPIGeneratorDialogProps {
    /** Whether dialog is open */
    open: boolean;
    /** Close callback */
    onClose: () => void;
    /** API nodes from canvas */
    apiNodes: Node<APINodeData>[];
}

/**
 * Format type for export
 */
type ExportFormat = 'yaml' | 'json';

/**
 * Tab panel component
 */
interface TabPanelProps {
    children?: React.ReactNode;
    index: number;
    value: number;
}

function TabPanel({ children, value, index }: TabPanelProps) {
    return (
        <div
            role="tabpanel"
            hidden={value !== index}
            id={`tabpanel-${index}`}
            aria-labelledby={`tab-${index}`}
        >
            {value === index && <Box className="py-4">{children}</Box>}
        </div>
    );
}

/**
 * OpenAPIGeneratorDialog component
 */
export function OpenAPIGeneratorDialog({ open, onClose, apiNodes }: OpenAPIGeneratorDialogProps) {
    // State
    const [title, setTitle] = useState('My API');
    const [version, setVersion] = useState('1.0.0');
    const [description, setDescription] = useState('API documentation generated from YAPPC canvas');
    const [serverUrl, setServerUrl] = useState('http://localhost:3000');
    const [format, setFormat] = useState<ExportFormat>('yaml');
    const [includeExamples, setIncludeExamples] = useState(true);
    const [includeSchemas, setIncludeSchemas] = useState(true);
    const [tabValue, setTabValue] = useState(0);
    const [copied, setCopied] = useState(false);

    /**
     * Generate OpenAPI spec
     */
    const spec = useMemo(() => {
        const options: OpenAPIGenerationOptions = {
            title,
            version,
            description,
            serverUrl,
            includeExamples,
            includeSchemas,
        };

        return OpenAPIService.generateSpec(apiNodes, options);
    }, [apiNodes, title, version, description, serverUrl, includeExamples, includeSchemas]);

    /**
     * Validate spec
     */
    const validation = useMemo(() => {
        return OpenAPIService.validate(spec);
    }, [spec]);

    /**
     * Generate output string
     */
    const output = useMemo(() => {
        return format === 'yaml'
            ? OpenAPIService.toYAML(spec)
            : OpenAPIService.toJSON(spec, true);
    }, [spec, format]);

    /**
     * Copy to clipboard
     */
    const handleCopy = useCallback(async () => {
        try {
            await navigator.clipboard.writeText(output);
            setCopied(true);
            setTimeout(() => setCopied(false), 2000);
        } catch (error) {
            console.error('Failed to copy:', error);
        }
    }, [output]);

    /**
     * Download file
     */
    const handleDownload = useCallback(() => {
        const blob = new Blob([output], { type: 'text/plain' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `openapi.${format}`;
        document.body.appendChild(a);
        a.click();
        document.body.removeChild(a);
        URL.revokeObjectURL(url);
    }, [output, format]);

    /**
     * Get endpoint count
     */
    const endpointCount = useMemo(() => {
        return Object.values(spec.paths).reduce(
            (total, methods) => total + Object.keys(methods).length,
            0
        );
    }, [spec]);

    /**
     * Get unique tags
     */
    const tags = useMemo(() => {
        const tagSet = new Set<string>();
        for (const methods of Object.values(spec.paths)) {
            for (const endpoint of Object.values(methods)) {
                if (Array.isArray((endpoint as unknown).tags)) {
                    (endpoint as unknown).tags.forEach((tag: string) => tagSet.add(tag));
                }
            }
        }
        return Array.from(tagSet);
    }, [spec]);

    return (
        <Dialog
            open={open}
            onClose={onClose}
            size="lg"
            fullWidth
            PaperProps={{
                sx: {
                    height: '90vh',
                    maxHeight: '90vh',
                },
            }}
        >
            <DialogTitle>
                <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Box display="flex" alignItems="center" gap={1}>
                        <APIIcon />
                        <Typography as="h6">Generate OpenAPI Specification</Typography>
                    </Box>
                    <IconButton size="sm" onClick={onClose}>
                        <CloseIcon />
                    </IconButton>
                </Box>
            </DialogTitle>

            <DialogContent dividers>
                <Tabs value={tabValue} onChange={(_, v) => setTabValue(v)} className="mb-4">
                    <Tab label="Configuration" />
                    <Tab label="Preview" />
                    <Tab label="Summary" />
                </Tabs>

                {/* Configuration Tab */}
                <TabPanel value={tabValue} index={0}>
                    <Box display="flex" flexDirection="column" gap={2}>
                        {/* API Info */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                API Information
                            </Typography>
                            <Box display="flex" flexDirection="column" gap={2} mt={1}>
                                <TextField
                                    label="API Title"
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    fullWidth
                                    required
                                />
                                <TextField
                                    label="Version"
                                    value={version}
                                    onChange={(e) => setVersion(e.target.value)}
                                    fullWidth
                                    required
                                    placeholder="1.0.0"
                                />
                                <TextField
                                    label="Description"
                                    value={description}
                                    onChange={(e) => setDescription(e.target.value)}
                                    fullWidth
                                    multiline
                                    rows={3}
                                />
                                <TextField
                                    label="Server URL"
                                    value={serverUrl}
                                    onChange={(e) => setServerUrl(e.target.value)}
                                    fullWidth
                                    placeholder="http://localhost:3000"
                                />
                            </Box>
                        </Paper>

                        {/* Export Options */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                Export Options
                            </Typography>
                            <Box display="flex" flexDirection="column" gap={2} mt={1}>
                                <FormControl fullWidth>
                                    <InputLabel>Format</InputLabel>
                                    <Select
                                        value={format}
                                        onChange={(e) => setFormat(e.target.value as ExportFormat)}
                                        label="Format"
                                    >
                                        <MenuItem value="yaml">YAML</MenuItem>
                                        <MenuItem value="json">JSON</MenuItem>
                                    </Select>
                                </FormControl>
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={includeExamples}
                                            onChange={(e) => setIncludeExamples(e.target.checked)}
                                        />
                                    }
                                    label="Include examples"
                                />
                                <FormControlLabel
                                    control={
                                        <Switch
                                            checked={includeSchemas}
                                            onChange={(e) => setIncludeSchemas(e.target.checked)}
                                        />
                                    }
                                    label="Include schema definitions"
                                />
                            </Box>
                        </Paper>

                        {/* Validation */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                Validation
                            </Typography>
                            {validation.valid ? (
                                <Alert severity="success" icon={<CheckIcon />} className="mt-2">
                                    OpenAPI specification is valid!
                                </Alert>
                            ) : (
                                <Alert severity="error" icon={<ErrorIcon />} className="mt-2">
                                    <Typography as="p" className="text-sm" gutterBottom>
                                        Validation errors:
                                    </Typography>
                                    <ul style={{ margin: 0, paddingLeft: 20 }}>
                                        {validation.errors.map((error, i) => (
                                            <li key={i}>
                                                <Typography as="span" className="text-xs text-gray-500">{error}</Typography>
                                            </li>
                                        ))}
                                    </ul>
                                </Alert>
                            )}
                        </Paper>
                    </Box>
                </TabPanel>

                {/* Preview Tab */}
                <TabPanel value={tabValue} index={1}>
                    <Box>
                        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
                            <Typography as="p" className="text-sm font-medium" fontWeight="bold">
                                OpenAPI {format.toUpperCase()} Output
                            </Typography>
                            <Box display="flex" gap={1}>
                                <Button
                                    size="sm"
                                    startIcon={copied ? <CheckIcon /> : <CopyIcon />}
                                    onClick={handleCopy}
                                    variant="outlined"
                                >
                                    {copied ? 'Copied!' : 'Copy'}
                                </Button>
                            </Box>
                        </Box>
                        <Paper
                            variant="outlined"
                            className="p-4 overflow-auto bg-gray-50 dark:bg-gray-800 max-h-[60vh]" >
                            <pre
                                style={{
                                    margin: 0,
                                    fontFamily: 'monospace',
                                    fontSize: '12px',
                                    whiteSpace: 'pre-wrap',
                                    wordBreak: 'break-word',
                                }}
                            >
                                {output}
                            </pre>
                        </Paper>
                    </Box>
                </TabPanel>

                {/* Summary Tab */}
                <TabPanel value={tabValue} index={2}>
                    <Box display="flex" flexDirection="column" gap={2}>
                        {/* Statistics */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                Statistics
                            </Typography>
                            <Box display="flex" gap={4} mt={2}>
                                <Box>
                                    <Typography as="h4" tone="primary">
                                        {apiNodes.length}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        API Nodes
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography as="h4" tone="primary">
                                        {endpointCount}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Endpoints
                                    </Typography>
                                </Box>
                                <Box>
                                    <Typography as="h4" tone="primary">
                                        {Object.keys(spec.paths).length}
                                    </Typography>
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        Unique Paths
                                    </Typography>
                                </Box>
                            </Box>
                        </Paper>

                        {/* Tags */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                Tags
                            </Typography>
                            <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
                                {tags.length > 0 ? (
                                    tags.map((tag) => (
                                        <Chip key={tag} label={tag} size="sm" />
                                    ))
                                ) : (
                                    <Typography as="span" className="text-xs text-gray-500" color="text.secondary">
                                        No tags defined
                                    </Typography>
                                )}
                            </Box>
                        </Paper>

                        {/* Endpoints List */}
                        <Paper variant="outlined" className="p-4">
                            <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                Endpoints
                            </Typography>
                            <Box display="flex" flexDirection="column" gap={1} mt={1}>
                                {Object.entries(spec.paths).map(([path, methods]) =>
                                    Object.keys(methods).map((method) => (
                                        <Box
                                            key={`${method}-${path}`}
                                            display="flex"
                                            alignItems="center"
                                            gap={1}
                                        >
                                            <Chip
                                                label={method.toUpperCase()}
                                                size="sm"
                                                color={
                                                    method === 'get'
                                                        ? 'info'
                                                        : method === 'post'
                                                            ? 'success'
                                                            : method === 'delete'
                                                                ? 'error'
                                                                : 'warning'
                                                }
                                            />
                                            <Typography as="p" className="text-sm" fontFamily="monospace">
                                                {path}
                                            </Typography>
                                        </Box>
                                    ))
                                )}
                            </Box>
                        </Paper>

                        {/* Security Schemes */}
                        {spec.components?.securitySchemes &&
                            Object.keys(spec.components.securitySchemes).length > 0 && (
                                <Paper variant="outlined" className="p-4">
                                    <Typography as="p" className="text-sm font-medium" gutterBottom fontWeight="bold">
                                        Security Schemes
                                    </Typography>
                                    <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
                                        {Object.keys(spec.components.securitySchemes).map((scheme) => (
                                            <Chip
                                                key={scheme}
                                                label={scheme}
                                                size="sm"
                                                tone="warning"
                                                icon={<CheckIcon />}
                                            />
                                        ))}
                                    </Box>
                                </Paper>
                            )}
                    </Box>
                </TabPanel>
            </DialogContent>

            <DialogActions>
                <Button onClick={onClose}>Cancel</Button>
                <Button
                    variant="solid"
                    startIcon={<DownloadIcon />}
                    onClick={handleDownload}
                    disabled={!validation.valid}
                >
                    Download {format.toUpperCase()}
                </Button>
            </DialogActions>
        </Dialog>
    );
}
