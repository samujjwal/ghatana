/**
 * useDataPipeline Hook
 * 
 * React hook for managing ETL/ELT pipeline state.
 * Supports pipeline construction, schema mapping, and Airflow DAG generation.
 * 
 * Features:
 * - Pipeline node management (source, transform, sink)
 * - Connection management with validation
 * - Schema mapping and lineage tracking
 * - Airflow DAG code generation
 * - Schedule configuration
 * - Pipeline validation
 * 
 * @doc.type hook
 * @doc.purpose Data pipeline state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';
import type { Node } from '@xyflow/react';

// ============================================================================
// TYPE DEFINITIONS
// ============================================================================

/**
 * Pipeline node types
 */
export type PipelineNodeType = 'source' | 'transform' | 'sink';

/**
 * Data source types
 */
export type DataSourceType = 'postgres' | 'mysql' | 'mongodb' | 's3' | 'api' | 'kafka';

/**
 * Transformation types
 */
export type TransformationType = 'filter' | 'aggregate' | 'join' | 'map' | 'pivot' | 'deduplicate';

/**
 * Sink types
 */
export type SinkType = 'bigquery' | 'redshift' | 'snowflake' | 's3' | 'postgres' | 'kafka';

/**
 * Column schema
 */
export interface ColumnSchema {
    name: string;
    type: 'string' | 'number' | 'boolean' | 'date' | 'timestamp' | 'json' | 'array';
    nullable: boolean;
    description?: string;
}

/**
 * Schema mapping
 */
export interface SchemaMapping {
    sourceColumn: string;
    targetColumn: string;
    transformation?: string;
}

/**
 * Pipeline node
 */
export interface PipelineNode {
    id: string;
    type: PipelineNodeType;
    name: string;
    config: {
        sourceType?: DataSourceType;
        transformationType?: TransformationType;
        sinkType?: SinkType;
        tableName?: string;
        query?: string;
        schema?: ColumnSchema[];
        mappings?: SchemaMapping[];
        [key: string]: unknown;
    };
}

export type PipelineNodeConfig = PipelineNode['config'];

/**
 * Pipeline connection
 */
export interface PipelineConnection {
    id: string;
    from: string;
    to: string;
}

/**
 * Validation error
 */
export interface ValidationError {
    nodeId: string;
    message: string;
    severity: 'error' | 'warning';
}

/**
 * Hook options
 */
export interface UseDataPipelineOptions {
    /** Initial pipeline name */
    initialName?: string;
    /** Initial schedule */
    initialSchedule?: string;
    /** Node context */
    node?: Node;
}

/**
 * Hook return type
 */
export interface UseDataPipelineResult {
    // State
    nodes: PipelineNode[];
    connections: PipelineConnection[];
    selectedNode: string | null;
    pipelineName: string;
    schedule: string;
    validationErrors: ValidationError[];

    // Actions
    addNode: (type: PipelineNodeType) => string;
    updateNode: (id: string, updates: Partial<PipelineNode>) => void;
    updateNodeConfig: (id: string, config: Partial<PipelineNode['config']>) => void;
    deleteNode: (id: string) => void;
    selectNode: (id: string | null) => void;
    setPipelineName: (name: string) => void;
    setSchedule: (schedule: string) => void;

    // Connections
    addConnection: (from: string, to: string) => void;
    deleteConnection: (id: string) => void;
    canConnect: (from: string, to: string) => boolean;

    // Schema Management
    setNodeSchema: (nodeId: string, schema: ColumnSchema[]) => void;
    addSchemaMapping: (nodeId: string, mapping: SchemaMapping) => void;
    removeSchemaMapping: (nodeId: string, index: number) => void;

    // Code Generation
    generateAirflowDAG: () => string;
    generateDBTModel: () => string;
    exportPipeline: () => string;

    // Validation
    validatePipeline: () => ValidationError[];
    isValid: boolean;

    // Utilities
    getNode: (id: string) => PipelineNode | undefined;
    getNodesByType: (type: PipelineNodeType) => PipelineNode[];
    getUpstreamNodes: (nodeId: string) => PipelineNode[];
    getDownstreamNodes: (nodeId: string) => PipelineNode[];
}

// ============================================================================
// HOOK
// ============================================================================

/**
 * Hook for managing data pipeline state
 */
export function useDataPipeline(options: UseDataPipelineOptions = {}): UseDataPipelineResult {
    const {
        initialName = 'data_pipeline',
        initialSchedule = '0 2 * * *',
        node,
    } = options;

    // State
    const [nodes, setNodes] = useState<PipelineNode[]>([]);
    const [connections, setConnections] = useState<PipelineConnection[]>([]);
    const [selectedNode, setSelectedNode] = useState<string | null>(null);
    const [pipelineName, setPipelineName] = useState(initialName);
    const [schedule, setSchedule] = useState(initialSchedule);

    /**
     * Add pipeline node
     */
    const addNode = useCallback((type: PipelineNodeType): string => {
        const id = `${type}-${Date.now()}`;
        const newNode: PipelineNode = {
            id,
            type,
            name: `${type.charAt(0).toUpperCase() + type.slice(1)} ${nodes.filter((n) => n.type === type).length + 1}`,
            config: {},
        };

        setNodes((prev) => [...prev, newNode]);
        setSelectedNode(id);
        return id;
    }, [nodes]);

    /**
     * Update node
     */
    const updateNode = useCallback((id: string, updates: Partial<PipelineNode>) => {
        setNodes((prev) =>
            prev.map((n) => (n.id === id ? { ...n, ...updates } : n))
        );
    }, []);

    /**
     * Update node configuration
     */
    const updateNodeConfig = useCallback((id: string, config: Partial<PipelineNode['config']>) => {
        setNodes((prev) =>
            prev.map((n) => (n.id === id ? { ...n, config: { ...n.config, ...config } } : n))
        );
    }, []);

    /**
     * Delete node
     */
    const deleteNode = useCallback((id: string) => {
        setNodes((prev) => prev.filter((n) => n.id !== id));
        setConnections((prev) => prev.filter((c) => c.from !== id && c.to !== id));
        if (selectedNode === id) {
            setSelectedNode(null);
        }
    }, [selectedNode]);

    /**
     * Select node
     */
    const selectNode = useCallback((id: string | null) => {
        setSelectedNode(id);
    }, []);

    /**
     * Check if two nodes can be connected
     */
    const canConnect = useCallback((from: string, to: string): boolean => {
        // Check if connection already exists
        if (connections.some((c) => c.from === from && c.to === to)) {
            return false;
        }

        // Check if nodes exist
        const fromNode = nodes.find((n) => n.id === from);
        const toNode = nodes.find((n) => n.id === to);
        if (!fromNode || !toNode) {
            return false;
        }

        // Validate connection logic (source → transform → sink)
        if (fromNode.type === 'source' && toNode.type === 'sink') {
            // Direct source to sink not recommended but allowed
            return true;
        }
        if (fromNode.type === 'source' && toNode.type === 'transform') {
            return true;
        }
        if (fromNode.type === 'transform' && toNode.type === 'transform') {
            return true;
        }
        if (fromNode.type === 'transform' && toNode.type === 'sink') {
            return true;
        }

        return false;
    }, [nodes, connections]);

    /**
     * Add connection
     */
    const addConnection = useCallback((from: string, to: string) => {
        if (!canConnect(from, to)) {
            return;
        }

        const newConnection: PipelineConnection = {
            id: `${from}-${to}`,
            from,
            to,
        };

        setConnections((prev) => [...prev, newConnection]);
    }, [canConnect]);

    /**
     * Delete connection
     */
    const deleteConnection = useCallback((id: string) => {
        setConnections((prev) => prev.filter((c) => c.id !== id));
    }, []);

    /**
     * Set node schema
     */
    const setNodeSchema = useCallback((nodeId: string, schema: ColumnSchema[]) => {
        updateNodeConfig(nodeId, { schema });
    }, [updateNodeConfig]);

    /**
     * Add schema mapping
     */
    const addSchemaMapping = useCallback((nodeId: string, mapping: SchemaMapping) => {
        setNodes((prev) =>
            prev.map((n) => {
                if (n.id === nodeId) {
                    const mappings = n.config.mappings || [];
                    return {
                        ...n,
                        config: {
                            ...n.config,
                            mappings: [...mappings, mapping],
                        },
                    };
                }
                return n;
            })
        );
    }, []);

    /**
     * Remove schema mapping
     */
    const removeSchemaMapping = useCallback((nodeId: string, index: number) => {
        setNodes((prev) =>
            prev.map((n) => {
                if (n.id === nodeId) {
                    const mappings = n.config.mappings || [];
                    return {
                        ...n,
                        config: {
                            ...n.config,
                            mappings: mappings.filter((_, i) => i !== index),
                        },
                    };
                }
                return n;
            })
        );
    }, []);

    /**
     * Generate Airflow DAG
     */
    const generateAirflowDAG = useCallback((): string => {
        const imports = [
            "from airflow import DAG",
            "from airflow.operators.python import PythonOperator",
            "from datetime import datetime, timedelta",
            "import logging",
        ];

        const tasks: string[] = [];
        const taskDefs: string[] = [];

        nodes.forEach((pNode) => {
            const taskId = pNode.id.replace(/-/g, '_');

            taskDefs.push(`
def ${pNode.type}_${taskId}(**context):
    """${pNode.name}"""
    logger = logging.getLogger(__name__)
    logger.info("Processing ${pNode.name}")
    # TODO: Implement ${pNode.type} logic
    return {"status": "success"}

${taskId}_task = PythonOperator(
    task_id='${taskId}',
    python_callable=${pNode.type}_${taskId},
    dag=dag,
)`);
            tasks.push(`${taskId}_task`);
        });

        const deps = connections.map((c) => {
            const from = c.from.replace(/-/g, '_');
            const to = c.to.replace(/-/g, '_');
            return `${from}_task >> ${to}_task`;
        });

        return `${imports.join('\n')}

default_args = {
    'owner': 'data-engineering',
    'start_date': datetime(2025, 1, 1),
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

dag = DAG(
    '${pipelineName}',
    default_args=default_args,
    schedule_interval='${schedule}',
    catchup=False,
)

${taskDefs.join('\n')}

# Dependencies
${deps.join('\n')}`;
    }, [nodes, connections, pipelineName, schedule]);

    /**
     * Generate DBT model
     */
    const generateDBTModel = useCallback((): string => {
        const sourceNodes = nodes.filter((n) => n.type === 'source');
        const transformNodes = nodes.filter((n) => n.type === 'transform');

        return `-- DBT Model: ${pipelineName}
-- Generated from YAPPC

{{ config(materialized='table') }}

with source_data as (
    select *
    from {{ source('${sourceNodes[0]?.config.tableName || 'source'}', '${sourceNodes[0]?.config.tableName || 'table'}') }}
),

transformed as (
    select
        ${transformNodes.map((t) => `-- ${t.name}`).join(',\n        ')}
    from source_data
)

select * from transformed`;
    }, [nodes, pipelineName]);

    /**
     * Export pipeline as JSON
     */
    const exportPipeline = useCallback((): string => {
        return JSON.stringify({
            name: pipelineName,
            schedule,
            nodes,
            connections,
        }, null, 2);
    }, [pipelineName, schedule, nodes, connections]);

    /**
     * Validate pipeline
     */
    const validatePipeline = useCallback((): ValidationError[] => {
        const errors: ValidationError[] = [];

        // Check for sources
        const sources = nodes.filter((n) => n.type === 'source');
        if (sources.length === 0) {
            errors.push({
                nodeId: '',
                message: 'Pipeline must have at least one source',
                severity: 'error',
            });
        }

        // Check for sinks
        const sinks = nodes.filter((n) => n.type === 'sink');
        if (sinks.length === 0) {
            errors.push({
                nodeId: '',
                message: 'Pipeline must have at least one sink',
                severity: 'error',
            });
        }

        // Check node configuration
        nodes.forEach((pNode) => {
            if (pNode.type === 'source' && !pNode.config.sourceType) {
                errors.push({
                    nodeId: pNode.id,
                    message: `${pNode.name}: Source type not configured`,
                    severity: 'error',
                });
            }
            if (pNode.type === 'transform' && !pNode.config.transformationType) {
                errors.push({
                    nodeId: pNode.id,
                    message: `${pNode.name}: Transformation type not configured`,
                    severity: 'warning',
                });
            }
            if (pNode.type === 'sink' && !pNode.config.sinkType) {
                errors.push({
                    nodeId: pNode.id,
                    message: `${pNode.name}: Sink type not configured`,
                    severity: 'error',
                });
            }
        });

        // Check for disconnected nodes
        nodes.forEach((pNode) => {
            const hasIncoming = connections.some((c) => c.to === pNode.id);
            const hasOutgoing = connections.some((c) => c.from === pNode.id);

            if (pNode.type === 'source' && !hasOutgoing) {
                errors.push({
                    nodeId: pNode.id,
                    message: `${pNode.name}: Source not connected to any transformation or sink`,
                    severity: 'warning',
                });
            }
            if (pNode.type === 'sink' && !hasIncoming) {
                errors.push({
                    nodeId: pNode.id,
                    message: `${pNode.name}: Sink not receiving data from any source`,
                    severity: 'error',
                });
            }
        });

        return errors;
    }, [nodes, connections]);

    /**
     * Check if pipeline is valid
     */
    const isValid = useMemo(() => {
        const errors = validatePipeline();
        return errors.filter((e) => e.severity === 'error').length === 0;
    }, [validatePipeline]);

    /**
     * Get validation errors
     */
    const validationErrors = useMemo(() => validatePipeline(), [validatePipeline]);

    /**
     * Get node by ID
     */
    const getNode = useCallback((id: string): PipelineNode | undefined => {
        return nodes.find((n) => n.id === id);
    }, [nodes]);

    /**
     * Get nodes by type
     */
    const getNodesByType = useCallback((type: PipelineNodeType): PipelineNode[] => {
        return nodes.filter((n) => n.type === type);
    }, [nodes]);

    /**
     * Get upstream nodes
     */
    const getUpstreamNodes = useCallback((nodeId: string): PipelineNode[] => {
        const upstreamIds = connections
            .filter((c) => c.to === nodeId)
            .map((c) => c.from);
        return nodes.filter((n) => upstreamIds.includes(n.id));
    }, [nodes, connections]);

    /**
     * Get downstream nodes
     */
    const getDownstreamNodes = useCallback((nodeId: string): PipelineNode[] => {
        const downstreamIds = connections
            .filter((c) => c.from === nodeId)
            .map((c) => c.to);
        return nodes.filter((n) => downstreamIds.includes(n.id));
    }, [nodes, connections]);

    return {
        // State
        nodes,
        connections,
        selectedNode,
        pipelineName,
        schedule,
        validationErrors,

        // Actions
        addNode,
        updateNode,
        updateNodeConfig,
        deleteNode,
        selectNode,
        setPipelineName,
        setSchedule,

        // Connections
        addConnection,
        deleteConnection,
        canConnect,

        // Schema Management
        setNodeSchema,
        addSchemaMapping,
        removeSchemaMapping,

        // Code Generation
        generateAirflowDAG,
        generateDBTModel,
        exportPipeline,

        // Validation
        validatePipeline,
        isValid,

        // Utilities
        getNode,
        getNodesByType,
        getUpstreamNodes,
        getDownstreamNodes,
    };
}
