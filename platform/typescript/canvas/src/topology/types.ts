/**
 * Topology Visualization Types
 *
 * Shared type definitions for topology/graph visualizations used across products:
 * - Data-Cloud: EventCloud stream topology
 * - AEP: Neural Map agent network
 *
 * @doc.type types
 * @doc.purpose Type definitions for topology visualization
 * @doc.layer shared
 */

import type { Node, Edge, NodeProps, EdgeProps, Position } from '@xyflow/react';

// ============================================
// VALIDATION AND CONFIG TYPES
// ============================================

/**
 * Validation error for topology nodes/edges.
 */
export interface ValidationError {
    field: string;
    message: string;
    severity: 'error' | 'warning';
}

/**
 * Node configuration schema.
 */
export interface NodeConfig {
    id: string;
    type: string;
    label: string;
    config?: Record<string, unknown>;
}

// ============================================
// BASE TOPOLOGY TYPES
// ============================================

/**
 * Status of a topology node/edge for visual indication.
 */
export type TopologyNodeStatus = 'healthy' | 'warning' | 'error' | 'inactive' | 'processing' | 'pending';

/**
 * Common metrics for topology nodes.
 */
export interface TopologyMetrics {
    /**
     * Throughput (e.g., events/sec, messages/sec)
     */
    throughput?: number;

    /**
     * Latency in milliseconds
     */
    latencyMs?: number;

    /**
     * Error rate (0-1)
     */
    errorRate?: number;

    /**
     * Queue depth (for buffered components)
     */
    queueDepth?: number;

    /**
     * CPU usage (0-100)
     */
    cpuPercent?: number;

    /**
     * Memory usage (0-100)
     */
    memoryPercent?: number;

    /**
     * Custom metrics
     */
    custom?: Record<string, number | string>;
}

/**
 * Base data structure for topology nodes.
 * Includes index signature for ReactFlow compatibility.
 */
export interface TopologyNodeData {
    /**
     * Display label
     */
    label: string;

    /**
     * Node type for styling/behavior
     */
    nodeType: string;

    /**
     * Current status
     */
    status: TopologyNodeStatus;

    /**
     * Description/tooltip text
     */
    description?: string;

    /**
     * Icon identifier
     */
    icon?: string;

    /**
     * Node-specific configuration
     */
    config?: Record<string, unknown>;

    /**
     * Real-time metrics
     */
    metrics?: TopologyMetrics;

    /**
     * Tenant ID for multi-tenant isolation
     */
    tenantId?: string;

    /**
     * Last update timestamp
     */
    lastUpdated?: number;

    /**
     * Index signature for ReactFlow compatibility
     */
    [key: string]: unknown;
}

/**
 * Base data structure for topology edges.
 * Includes index signature for ReactFlow compatibility.
 */
export interface TopologyEdgeData {
    /**
     * Edge label
     */
    label?: string;

    /**
     * Current throughput
     */
    throughput?: number;

    /**
     * Animation state
     */
    animated?: boolean;

    /**
     * Status-based styling
     */
    status?: TopologyNodeStatus;

    /**
     * Index signature for ReactFlow compatibility
     */
    [key: string]: unknown;

    /**
     * Edge type for styling
     */
    edgeType?: string;
}

/**
 * Typed node for topology visualizations.
 */
export type TopologyNode<T extends TopologyNodeData = TopologyNodeData> = Node<T>;

/**
 * Typed edge for topology visualizations.
 */
export type TopologyEdge<T extends TopologyEdgeData = TopologyEdgeData> = Edge<T>;

// ============================================
// EVENTCLOUD STREAM TOPOLOGY (Data-Cloud)
// ============================================

/**
 * EventCloud-specific node types.
 */
export type EventCloudNodeType =
    | 'source'
    | 'processor'
    | 'sink'
    | 'router'
    | 'aggregator'
    | 'filter'
    | 'transformer'
    | 'storage'
    | 'queue';

/**
 * EventCloud stream node data.
 */
export interface EventCloudNodeData extends TopologyNodeData {
    /**
     * EventCloud node type
     */
    nodeType: EventCloudNodeType;

    /**
     * Stream configuration
     */
    streamConfig?: {
        partitions?: number;
        replicationFactor?: number;
        retentionMs?: number;
    };

    /**
     * Consumer group info
     */
    consumerGroup?: string;

    /**
     * Connected topics
     */
    topics?: string[];
}

/**
 * EventCloud stream edge data.
 */
export interface EventCloudEdgeData extends TopologyEdgeData {
    /**
     * Topic name for the stream
     */
    topic?: string;

    /**
     * Partition key expression
     */
    partitionKey?: string;

    /**
     * Message format (avro, json, protobuf)
     */
    messageFormat?: 'avro' | 'json' | 'protobuf';
}

/**
 * EventCloud topology node.
 */
export type EventCloudNode = TopologyNode<EventCloudNodeData>;

/**
 * EventCloud topology edge.
 */
export type EventCloudEdge = TopologyEdge<EventCloudEdgeData>;

// ============================================
// NEURAL MAP (AEP - Agentic Event Processor)
// ============================================

/**
 * Neural Map node types for agent networks.
 */
export type NeuralMapNodeType =
    | 'agent'
    | 'tool'
    | 'memory'
    | 'prompt'
    | 'output'
    | 'decision'
    | 'loop'
    | 'parallel'
    | 'condition';

/**
 * Agent execution state.
 */
export type AgentExecutionState = 'idle' | 'thinking' | 'executing' | 'waiting' | 'completed' | 'failed';

/**
 * Neural Map agent node data.
 */
export interface NeuralMapNodeData extends TopologyNodeData {
    /**
     * Neural map node type
     */
    nodeType: NeuralMapNodeType;

    /**
     * Agent execution state
     */
    executionState?: AgentExecutionState;

    /**
     * Agent model (e.g., 'gpt-4', 'claude-3')
     */
    model?: string;

    /**
     * Tool definitions available to agent
     */
    tools?: string[];

    /**
     * Current prompt/instruction
     */
    prompt?: string;

    /**
     * Last output/response
     */
    lastOutput?: string;

    /**
     * Token usage
     */
    tokenUsage?: {
        input: number;
        output: number;
        total: number;
    };

    /**
     * Execution history count
     */
    executionCount?: number;
}

/**
 * Neural Map edge data.
 */
export interface NeuralMapEdgeData extends TopologyEdgeData {
    /**
     * Condition for edge traversal
     */
    condition?: string;

    /**
     * Priority for multiple edges
     */
    priority?: number;

    /**
     * Edge was traversed in last execution
     */
    traversed?: boolean;
}

/**
 * Neural Map topology node.
 */
export type NeuralMapNode = TopologyNode<NeuralMapNodeData>;

/**
 * Neural Map topology edge.
 */
export type NeuralMapEdge = TopologyEdge<NeuralMapEdgeData>;

// ============================================
// SHARED VISUALIZATION PROPS
// ============================================

/**
 * Configuration for topology visualization.
 */
export interface TopologyVisualizationConfig {
    /**
     * Enable real-time metrics updates
     */
    enableRealTimeMetrics?: boolean;

    /**
     * Metrics refresh interval in ms
     */
    metricsRefreshInterval?: number;

    /**
     * Enable edge animations
     */
    enableAnimations?: boolean;

    /**
     * Show node metrics inline
     */
    showInlineMetrics?: boolean;

    /**
     * Layout algorithm
     */
    layout?: 'dagre' | 'elk' | 'force' | 'manual';

    /**
     * Layout direction
     */
    layoutDirection?: 'TB' | 'BT' | 'LR' | 'RL';

    /**
     * Enable zoom controls
     */
    enableZoomControls?: boolean;

    /**
     * Enable fullscreen mode
     */
    enableFullscreen?: boolean;

    /**
     * Node spacing in pixels
     */
    nodeSpacing?: number;

    /**
     * Rank spacing in pixels (for dagre)
     */
    rankSpacing?: number;
}

/**
 * Props for topology visualization component.
 */
export interface TopologyVisualizationProps<
    TNode extends TopologyNodeData = TopologyNodeData,
    TEdge extends TopologyEdgeData = TopologyEdgeData
> {
    /**
     * Topology nodes
     */
    nodes: TopologyNode<TNode>[];

    /**
     * Topology edges
     */
    edges: TopologyEdge<TEdge>[];

    /**
     * Visualization configuration
     */
    config?: TopologyVisualizationConfig;

    /**
     * Callback when node is clicked
     */
    onNodeClick?: (node: TopologyNode<TNode>) => void;

    /**
     * Callback when edge is clicked
     */
    onEdgeClick?: (edge: TopologyEdge<TEdge>) => void;

    /**
     * Callback when nodes/edges change
     */
    onChange?: (nodes: TopologyNode<TNode>[], edges: TopologyEdge<TEdge>[]) => void;

    /**
     * Validation errors to display
     */
    validationErrors?: ValidationError[];

    /**
     * Loading state
     */
    isLoading?: boolean;

    /**
     * Error state
     */
    error?: Error | null;

    /**
     * Read-only mode
     */
    readOnly?: boolean;

    /**
     * Container className
     */
    className?: string;

    /**
     * Tenant ID for multi-tenant filtering
     */
    tenantId?: string;
}

/**
 * Custom node component props.
 * Uses Omit to properly override data with TopologyNodeData.
 */
export interface TopologyNodeComponentProps extends Omit<NodeProps, 'data'> {
    /**
     * Whether metrics should be displayed
     */
    showMetrics?: boolean;

    /**
     * Compact display mode
     */
    compact?: boolean;

    /**
     * Node data with TopologyNodeData structure
     */
    data: TopologyNodeData;
}

/**
 * Custom edge component props.
 * Uses Omit to properly override data with TopologyEdgeData.
 */
export interface TopologyEdgeComponentProps extends Omit<EdgeProps, 'data'> {
    /**
     * Whether to animate the edge
     */
    animate?: boolean;

    /**
     * Edge data with TopologyEdgeData structure
     */
    data?: TopologyEdgeData;
}

// ============================================
// LAYOUT TYPES
// ============================================

/**
 * Node position for layout calculations.
 */
export interface LayoutNodePosition {
    id: string;
    x: number;
    y: number;
    width: number;
    height: number;
}

/**
 * Layout result from layout algorithms.
 */
export interface LayoutResult {
    nodes: LayoutNodePosition[];
    edges: Array<{
        id: string;
        points: Array<{ x: number; y: number }>;
    }>;
    width: number;
    height: number;
}

/**
 * Layout options for dagre/elk layouts.
 */
export interface LayoutOptions {
    direction: 'TB' | 'BT' | 'LR' | 'RL';
    nodeSpacing: number;
    rankSpacing: number;
    edgeSpacing: number;
    centerNodes: boolean;
}
