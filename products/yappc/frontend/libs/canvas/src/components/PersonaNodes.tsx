/**
 * Persona Journey Node Types
 *
 * Custom node types for different personas following YAPPC_USER_JOURNEYS.md.
 * These nodes enable persona-specific workflows on the canvas.
 *
 * @doc.type module
 * @doc.purpose Persona-specific canvas node types
 * @doc.layer product
 * @doc.pattern Component
 *
 * Personas Supported:
 * - Product Manager: AI Prompt, Requirement, User Flow, Stakeholder
 * - Architect: Database, Service, API Endpoint, Infrastructure
 * - Developer: Code, Function, Test, IDE Integration
 * - QA: Test Suite, Coverage, Validation
 * - UX Designer: UI Screen, Wireframe, Prototype Link
 */

import React, { useState, useCallback, useEffect } from 'react';
import { Handle, Position } from '@xyflow/react';
import type { NodeProps } from '@xyflow/react';
import {
  Box,
  Typography,
  IconButton,
  Chip,
  Button,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Tooltip,
  Alert,
  Surface as Paper,
  Spinner as CircularProgress,
} from '@ghatana/ui';
import { TextField } from '@ghatana/ui';
import { useMuiTheme as useTheme } from '@ghatana/yappc-ui';
import { useAIBrainstorming } from '../hooks/useAIBrainstorming';
import { Bot as AIIcon, FileText as RequirementIcon, GitBranch as FlowIcon, Users as StakeholderIcon, HardDrive as DatabaseIcon, Plug as ApiIcon, Cloud as CloudIcon, Code as CodeIcon, Functions as FunctionIcon, Bug as TestIcon, Smartphone as UIIcon, Paintbrush as DesignIcon, Play as RunIcon, Check as CheckIcon, AlertCircle as ErrorIcon, Clock as PendingIcon } from 'lucide-react';

// Import Property Panels
import {
    SchemaDesignerPanel,
    DeploymentConfigPanel,
    APIDesignerPanel,
    type SchemaField,
    type DeploymentConfig,
    type APIEndpointConfig,
} from './PropertyPanels';

// Import and re-export base types from node-types to avoid circular dependencies
export type {
    PersonaNodeData,
    PersonaNodeType,
    ServiceNodeData,
    APIEndpointNodeData,
    DatabaseNodeData,
} from '../integration/node-types';

import type { PersonaNodeData, PersonaNodeType } from '../integration/node-types';

// ============================================================================
// TYPE DEFINITIONS (Node-specific)
// ============================================================================

// ============================================================================
// AI PROMPT NODE (Product Manager)
// ============================================================================

export interface AIPromptNodeData extends PersonaNodeData {
    type: 'aiPrompt';
    prompt: string;
    response?: string;
    suggestions?: string[];
    isGenerating?: boolean;
}

export function AIPromptNode({ data, id, selected }: NodeProps<AIPromptNodeData>) {
    const theme = useTheme();
    const [prompt, setPrompt] = useState(data.prompt || '');

    // Use AI brainstorming hook
    const {
        response: aiResponse,
        loading,
        error,
        generateIdeas,
    } = useAIBrainstorming({
        autoSpawn: true,
        suggestionCount: 5,
        onNodesSpawned: (nodes, edges) => {
            console.log(`Spawned ${nodes.length} nodes and ${edges.length} edges`);
        },
    });

    const handleGenerate = useCallback(async () => {
        await generateIdeas(prompt, id);
    }, [prompt, id, generateIdeas]);

    // Update local prompt when data changes
    useEffect(() => {
        if (data.prompt && data.prompt !== prompt) {
            setPrompt(data.prompt);
        }
    }, [data.prompt]);

    const borderColor = selected ? theme.palette.primary.main : theme.palette.divider;

    return (
        <Paper
            elevation={selected ? 8 : 2}
            className="min-w-[280px] max-w-[400px]"
            style={{
                border: `2px solid ${borderColor}`,
                backgroundColor: theme.palette.secondary.main,
                color: theme.palette.secondary.contrastText,
            }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="px-4 py-2 flex items-center gap-2" >
                <AIIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    AI Brainstorm
                </Typography>
                {data.aiGenerated && (
                    <Chip label="AI" size="small" color="info" className="ml-auto" />
                )}
            </Box>

            {/* Content */}
            <Box className="p-4">
                <TextField
                    fullWidth
                    multiline
                    rows={3}
                    placeholder="Describe your idea or requirement..."
                    value={prompt}
                    onChange={(e) => setPrompt(e.target.value)}
                    variant="outlined"
                    size="small"
                    className="mb-4"
                />

                <Button
                    fullWidth
                    variant="contained"
                    color="secondary"
                    onClick={handleGenerate}
                    disabled={loading || !prompt.trim()}
                    startIcon={loading ? <CircularProgress size={16} /> : <AIIcon />}
                >
                    {loading ? 'Generating...' : 'Generate Ideas'}
                </Button>

                {error && (
                    <Alert severity="error" className="mt-4">
                        {error.message}
                    </Alert>
                )}

                {aiResponse && (
                    <Box className="mt-4">
                        <Box className="p-3 rounded bg-gray-100 dark:bg-gray-800">
                            <Typography variant="body2" color="text.secondary">
                                {aiResponse.response}
                            </Typography>
                        </Box>
                        {aiResponse.suggestions && aiResponse.suggestions.length > 0 && (
                            <Box className="mt-2">
                                <Typography variant="caption" color="text.secondary">
                                    Generated {aiResponse.suggestions.length} node(s)
                                </Typography>
                                {aiResponse.tokenUsage && (
                                    <Typography variant="caption" color="text.secondary" className="ml-2">
                                        • {aiResponse.tokenUsage.totalTokens} tokens
                                    </Typography>
                                )}
                            </Box>
                        )}
                    </Box>
                )}
            </Box>

            <Handle type="source" position={Position.Right} />
        </Paper>
    );
}

// ============================================================================
// DATABASE NODE (Architect)
// ============================================================================

export interface DatabaseNodeData extends PersonaNodeData {
    type: 'database';
    engine: 'postgres' | 'mysql' | 'mongodb' | 'redis' | 'dynamodb';
    schema?: {
        tableName?: string;
        fields?: SchemaField[];
        tables?: Array<{
            name: string;
            columns: Array<{ name: string; type: string; nullable?: boolean }>;
        }>;
    };
}

export function DatabaseNode({ data, id, selected }: NodeProps<DatabaseNodeData>) {
    const theme = useTheme();
    const [showSchema, setShowSchema] = useState(false);
    const [schema, setSchema] = useState(data.schema);
    const borderColor = selected ? theme.palette.success.main : theme.palette.divider;

    const engineIcons: Record<string, string> = {
        postgres: '🐘',
        mysql: '🐬',
        mongodb: '🍃',
        redis: '🔴',
        dynamodb: '⚡',
    };

    const handleSaveSchema = useCallback((tableName: string, fields: SchemaField[]) => {
        const newSchema = { tableName, fields };
        setSchema(newSchema);
        // NOTE: Update node data in React Flow store
        // This would typically be done via a context or prop callback
    }, []);

    const handleDoubleClick = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setShowSchema(true);
    }, []);

    return (
        <Paper
            elevation={selected ? 8 : 2}
            onDoubleClick={handleDoubleClick}
            className="min-w-[200px]"
            style={{
                border: `2px solid ${borderColor}`,
                backgroundColor: theme.palette.success.main,
                color: theme.palette.success.contrastText,
            }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="px-4 py-2 flex items-center gap-2" >
                <DatabaseIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    {data.label || 'Database'}
                </Typography>
                <Typography variant="caption" className="ml-auto">
                    {engineIcons[data.engine] || '💾'} {data.engine?.toUpperCase()}
                </Typography>
            </Box>

            {/* Content */}
            <Box className="p-4">
                {schema?.tableName && (
                    <Chip
                        label={schema.tableName}
                        size="small"
                        variant="outlined"
                        color="success"
                        className="mr-1 mb-1"
                    />
                )}
                {schema?.fields && (
                    <Typography variant="caption" color="text.secondary">
                        {schema.fields.length} fields
                    </Typography>
                )}
                {!schema?.tableName && (
                    <Typography variant="body2" color="text.secondary" className="italic">
                        Double-click to define schema
                    </Typography>
                )}
            </Box>

            <Handle type="source" position={Position.Right} />

            {/* Schema Designer Panel */}
            <SchemaDesignerPanel
                open={showSchema}
                onClose={() => setShowSchema(false)}
                initialFields={schema?.fields}
                tableName={schema?.tableName}
                onSave={handleSaveSchema}
            />
        </Paper>
    );
}

// ============================================================================
// SERVICE NODE (Architect / Developer)
// Note: ServiceNodeData type is imported from node-types.ts
// ============================================================================

// Extend the base ServiceNodeData with additional UI-specific fields
export interface UIServiceNodeData extends PersonaNodeData {
    type: 'service';
    technology?: 'nodejs' | 'java' | 'python' | 'go' | 'rust';
    config?: DeploymentConfig;
    endpoints?: string[];
}

export function ServiceNode({ data, id, selected }: NodeProps<UIServiceNodeData>) {
    const theme = useTheme();
    const [showConfig, setShowConfig] = useState(false);
    const [config, setConfig] = useState<DeploymentConfig | undefined>(data.config);
    const borderColor = selected ? theme.palette.info.main : theme.palette.divider;

    const techIcons: Record<string, string> = {
        nodejs: '🟢',
        java: '☕',
        python: '🐍',
        go: '🐹',
        rust: '🦀',
    };

    const statusIcon = {
        draft: <PendingIcon color="action" />,
        ready: <CheckIcon color="success" />,
        'in-progress': <CircularProgress size={16} />,
        completed: <CheckIcon color="success" />,
        error: <ErrorIcon color="error" />,
    };

    const handleSaveConfig = useCallback((newConfig: DeploymentConfig) => {
        setConfig(newConfig);
        // NOTE: Update node data in React Flow store
    }, []);

    const handleDoubleClick = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setShowConfig(true);
    }, []);

    return (
        <Paper
            elevation={selected ? 8 : 2}
            onDoubleClick={handleDoubleClick}
            className="min-w-[220px]"
            style={{
                border: `2px solid ${borderColor}`,
                backgroundColor: theme.palette.info.main,
                color: theme.palette.info.contrastText,
            }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="px-4 py-2 flex items-center gap-2" >
                <CodeIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    {data.label || 'Service'}
                </Typography>
                {data.technology && (
                    <Typography variant="caption" className="ml-auto">
                        {techIcons[data.technology]} {data.technology}
                    </Typography>
                )}
            </Box>

            {/* Content */}
            <Box className="p-4">
                <Box className="flex items-center gap-2 mb-2">
                    {statusIcon[data.status || 'draft']}
                    <Typography variant="body2" color="text.secondary">
                        {data.status || 'Draft'}
                    </Typography>
                </Box>

                {config && (
                    <Box className="text-xs text-gray-500 dark:text-gray-400">
                        <div>Replicas: {config.replicas}</div>
                        <div>CPU: {config.cpu}</div>
                        <div>Memory: {config.memory}</div>
                    </Box>
                )}
                {!config && (
                    <Typography variant="body2" color="text.secondary" className="italic">
                        Double-click to configure
                    </Typography>
                )}

                {data.endpoints && data.endpoints.length > 0 && (
                    <Box className="mt-2">
                        {data.endpoints.slice(0, 3).map((ep, idx) => (
                            <Chip
                                key={idx}
                                label={ep}
                                size="small"
                                variant="outlined"
                                className="mr-1 mb-1 text-[0.65rem]"
                            />
                        ))}
                    </Box>
                )}
            </Box>

            <Handle type="source" position={Position.Right} />

            {/* Deployment Config Panel */}
            <DeploymentConfigPanel
                open={showConfig}
                onClose={() => setShowConfig(false)}
                initialConfig={config}
                serviceName={data.label || 'Service'}
                onSave={handleSaveConfig}
            />
        </Paper>
    );
}

// ============================================================================
// API ENDPOINT NODE (Developer)
// ============================================================================

export interface APIEndpointNodeData extends PersonaNodeData {
    type: 'apiEndpoint';
    method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';
    path: string;
    requestSchema?: Record<string, unknown>;
    responseSchema?: Record<string, unknown>;
    apiConfig?: APIEndpointConfig;
}

export function APIEndpointNode({ data, id, selected }: NodeProps<APIEndpointNodeData>) {
    const theme = useTheme();
    const [showDesigner, setShowDesigner] = useState(false);
    const [apiConfig, setApiConfig] = useState<APIEndpointConfig | undefined>(
        data.apiConfig || { method: data.method, path: data.path, authentication: 'jwt' }
    );
    const borderColor = selected ? theme.palette.warning.main : theme.palette.divider;

    const methodColors: Record<string, string> = {
        GET: '#61affe',
        POST: '#49cc90',
        PUT: '#fca130',
        DELETE: '#f93e3e',
        PATCH: '#50e3c2',
    };

    const handleSaveAPI = useCallback((newConfig: APIEndpointConfig) => {
        setApiConfig(newConfig);
        // NOTE: Update node data in React Flow store
    }, []);

    const handleDoubleClick = useCallback((e: React.MouseEvent) => {
        e.stopPropagation();
        setShowDesigner(true);
    }, []);

    return (
        <Paper
            elevation={selected ? 8 : 2}
            onDoubleClick={handleDoubleClick}
            className="min-w-[200px]" style={{ border: `2px solid ${borderColor}` }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="text-[#fff] px-4 py-2 flex items-center gap-2" style={{ backgroundColor: methodColors[apiConfig?.method || data.method] || theme.palette.grey[500] }}
            >
                <ApiIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    {apiConfig?.method || data.method}
                </Typography>
            </Box>

            {/* Content */}
            <Box className="p-4">
                <Typography
                    variant="body2"
                    className="p-2 rounded overflow-hidden text-ellipsis font-mono bg-gray-100 dark:bg-gray-800"
                >
                    {apiConfig?.path || data.path || '/api/endpoint'}
                </Typography>

                {apiConfig?.authentication && (
                    <Chip
                        label={apiConfig.authentication}
                        size="small"
                        variant="outlined"
                        className="mt-2 text-[0.65rem]"
                    />
                )}

                {!apiConfig?.description && (
                    <Typography variant="caption" color="text.secondary" className="block mt-2 italic">
                        Double-click to configure
                    </Typography>
                )}
            </Box>

            <Handle type="source" position={Position.Right} />

            {/* API Designer Panel */}
            <APIDesignerPanel
                open={showDesigner}
                onClose={() => setShowDesigner(false)}
                initialConfig={apiConfig}
                onSave={handleSaveAPI}
            />
        </Paper>
    );
}

// ============================================================================
// UI SCREEN NODE (UX Designer)
// ============================================================================

export interface UIScreenNodeData extends PersonaNodeData {
    type: 'uiScreen';
    screenType: 'view' | 'edit' | 'list' | 'detail' | 'modal';
    components?: string[];
    prototypeLink?: string;
}

export function UIScreenNode({ data, id, selected }: NodeProps<UIScreenNodeData>) {
    const theme = useTheme();
    const borderColor = selected ? theme.palette.secondary.main : theme.palette.divider;

    return (
        <Paper
            elevation={selected ? 8 : 2}
            className="min-w-[180px]"
            style={{
                border: `2px solid ${borderColor}`,
                backgroundColor: theme.palette.secondary.light,
                color: theme.palette.secondary.contrastText,
            }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="px-4 py-2 flex items-center gap-2" >
                <UIIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    {data.label || 'UI Screen'}
                </Typography>
            </Box>

            {/* Mini Preview */}
            <Box
                className="p-4 flex flex-col gap-1 bg-gray-100 dark:bg-gray-800 min-h-[80px]"
            >
                {/* Wireframe placeholder */}
                <Box className="h-[8px] rounded-[4px] w-[60%] bg-gray-300" />
                <Box className="h-[24px] bg-gray-200 dark:bg-gray-700 rounded-[4px]" />
                <Box className="h-[16px] rounded-[4px] w-[80%] bg-gray-300" />
            </Box>

            {/* Actions */}
            <Box className="p-2 flex justify-center">
                <Chip
                    label={data.screenType || 'view'}
                    size="small"
                    color="secondary"
                    variant="outlined"
                />
            </Box>

            <Handle type="source" position={Position.Right} />
        </Paper>
    );
}

// ============================================================================
// TEST SUITE NODE (QA Engineer)
// ============================================================================

export interface TestSuiteNodeData extends PersonaNodeData {
    type: 'testSuite';
    testType: 'unit' | 'integration' | 'e2e' | 'performance';
    testCount?: number;
    passRate?: number;
    lastRun?: string;
}

export function TestSuiteNode({ data, id, selected }: NodeProps<TestSuiteNodeData>) {
    const theme = useTheme();
    const borderColor = selected ? theme.palette.error.main : theme.palette.divider;
    const passRate = data.passRate || 0;

    return (
        <Paper
            elevation={selected ? 8 : 2}
            className="min-w-[200px]" style={{ border: `2px solid ${borderColor}` }}
        >
            <Handle type="target" position={Position.Left} />

            {/* Header */}
            <Box
                className="text-[#fff] px-4 py-2 flex items-center gap-2" style={{ backgroundColor: passRate >= 95 ? theme.palette.success.main : passRate >= 80 ? theme.palette.warning.main : theme.palette.error.main }}
            >
                <TestIcon size={16} />
                <Typography variant="subtitle2" fontWeight="bold">
                    {data.label || 'Test Suite'}
                </Typography>
            </Box>

            {/* Content */}
            <Box className="p-4">
                <Box className="flex justify-between mb-2">
                    <Typography variant="body2" color="text.secondary">
                        Tests: {data.testCount || 0}
                    </Typography>
                    <Typography
                        variant="body2"
                        fontWeight="bold"
                        color={passRate >= 95 ? 'success.main' : passRate >= 80 ? 'warning.main' : 'error.main'}
                    >
                        {passRate}%
                    </Typography>
                </Box>

                <Chip
                    label={data.testType || 'unit'}
                    size="small"
                    variant="outlined"
                    className="mr-1"
                />

                <Button
                    size="small"
                    variant="contained"
                    startIcon={<RunIcon />}
                    className="mt-2 w-full"
                >
                    Run Tests
                </Button>
            </Box>

            <Handle type="source" position={Position.Right} />
        </Paper>
    );
}

// ============================================================================
// EXPORTS
// ============================================================================

export const personaNodeTypes = {
    aiPrompt: AIPromptNode,
    database: DatabaseNode,
    service: ServiceNode,
    apiEndpoint: APIEndpointNode,
    uiScreen: UIScreenNode,
    testSuite: TestSuiteNode,
};

export type PersonaNodeTypes = typeof personaNodeTypes;
