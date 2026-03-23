/**
 * React Hook for Topology Visualization
 *
 * Provides state management and utilities for topology graphs including:
 * - Node/edge state management
 * - Automatic layout
 * - Selection handling
 * - Real-time updates integration
 * - Undo/redo support
 *
 * @doc.type hook
 * @doc.purpose React state management for topology graphs
 * @doc.layer shared
 * @doc.pattern ReactHook
 */

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNodesState, useEdgesState, type Connection, addEdge } from '@xyflow/react';
import type {
    TopologyNode,
    TopologyEdge,
    TopologyNodeData,
    TopologyEdgeData,
    TopologyVisualizationConfig,
    LayoutOptions,
} from './types';
import { autoLayout, centerNodes } from './layout';
import { useHistory } from './builder/useHistory';

// ============================================
// TYPES
// ============================================

export interface UseTopologyOptions<
    TNode extends TopologyNodeData = TopologyNodeData,
    TEdge extends TopologyEdgeData = TopologyEdgeData
> {
    /**
     * Initial nodes
     */
    initialNodes?: TopologyNode<TNode>[];

    /**
     * Initial edges
     */
    initialEdges?: TopologyEdge<TEdge>[];

    /**
     * Visualization configuration
     */
    config?: TopologyVisualizationConfig;

    /**
     * Callback when topology changes
     */
    onChange?: (nodes: TopologyNode<TNode>[], edges: TopologyEdge<TEdge>[]) => void;

    /**
     * Callback when node is selected
     */
    onNodeSelect?: (node: TopologyNode<TNode> | null) => void;

    /**
     * Callback when edge is selected
     */
    onEdgeSelect?: (edge: TopologyEdge<TEdge> | null) => void;

    /**
     * Enable history (undo/redo)
     */
    enableHistory?: boolean;

    /**
     * Read-only mode
     */
    readOnly?: boolean;
}

export interface UseTopologyReturn<
    TNode extends TopologyNodeData = TopologyNodeData,
    TEdge extends TopologyEdgeData = TopologyEdgeData
> {
    /**
     * Current nodes
     */
    nodes: TopologyNode<TNode>[];

    /**
     * Current edges
     */
    edges: TopologyEdge<TEdge>[];

    /**
     * Set nodes state
     */
    setNodes: React.Dispatch<React.SetStateAction<TopologyNode<TNode>[]>>;

    /**
     * Set edges state
     */
    setEdges: React.Dispatch<React.SetStateAction<TopologyEdge<TEdge>[]>>;

    /**
     * ReactFlow onNodesChange handler
     */
    onNodesChange: (changes: unknown[]) => void;

    /**
     * ReactFlow onEdgesChange handler
     */
    onEdgesChange: (changes: unknown[]) => void;

    /**
     * ReactFlow onConnect handler
     */
    onConnect: (connection: Connection) => void;

    /**
     * Currently selected node
     */
    selectedNode: TopologyNode<TNode> | null;

    /**
     * Currently selected edge
     */
    selectedEdge: TopologyEdge<TEdge> | null;

    /**
     * Select a node by ID
     */
    selectNode: (nodeId: string | null) => void;

    /**
     * Select an edge by ID
     */
    selectEdge: (edgeId: string | null) => void;

    /**
     * Update a node's data
     */
    updateNodeData: (nodeId: string, data: Partial<TNode>) => void;

    /**
     * Update an edge's data
     */
    updateEdgeData: (edgeId: string, data: Partial<TEdge>) => void;

    /**
     * Add a new node
     */
    addNode: (node: TopologyNode<TNode>) => void;

    /**
     * Remove a node
     */
    removeNode: (nodeId: string) => void;

    /**
     * Remove an edge
     */
    removeEdge: (edgeId: string) => void;

    /**
     * Apply automatic layout
     */
    applyLayout: (algorithm?: 'dagre' | 'force' | 'manual', options?: Partial<LayoutOptions>) => void;

    /**
     * Center the graph in viewport
     */
    centerGraph: () => void;

    /**
     * Undo last change (if history enabled)
     */
    undo: () => void;

    /**
     * Redo last undone change (if history enabled)
     */
    redo: () => void;

    /**
     * Can undo
     */
    canUndo: boolean;

    /**
     * Can redo
     */
    canRedo: boolean;

    /**
     * Reset to initial state
     */
    reset: () => void;
}

// ============================================
// HOOK IMPLEMENTATION
// ============================================

/**
 * React hook for topology visualization state management.
 *
 * @example
 * ```tsx
 * function PipelineTopology() {
 *   const {
 *     nodes,
 *     edges,
 *     onNodesChange,
 *     onEdgesChange,
 *     onConnect,
 *     selectedNode,
 *     applyLayout,
 *   } = useTopology({
 *     initialNodes: pipelineNodes,
 *     initialEdges: pipelineEdges,
 *     config: { layout: 'dagre', layoutDirection: 'LR' },
 *   });
 *
 *   return (
 *     <ReactFlow
 *       nodes={nodes}
 *       edges={edges}
 *       onNodesChange={onNodesChange}
 *       onEdgesChange={onEdgesChange}
 *       onConnect={onConnect}
 *       nodeTypes={topologyNodeTypes}
 *       edgeTypes={topologyEdgeTypes}
 *     />
 *   );
 * }
 * ```
 */
export function useTopology<
    TNode extends TopologyNodeData = TopologyNodeData,
    TEdge extends TopologyEdgeData = TopologyEdgeData
>(options: UseTopologyOptions<TNode, TEdge> = {}): UseTopologyReturn<TNode, TEdge> {
    const {
        initialNodes = [],
        initialEdges = [],
        config = {},
        onChange,
        onNodeSelect,
        onEdgeSelect,
        enableHistory = true,
        readOnly = false,
    } = options;

    // Use ReactFlow's state hooks
    const [nodes, setNodes, onNodesChange] = useNodesState(initialNodes as TopologyNode<TNode>[]);
    const [edges, setEdges, onEdgesChange] = useEdgesState(initialEdges as TopologyEdge<TEdge>[]);

    // Selection state
    const [selectedNode, setSelectedNode] = useState<TopologyNode<TNode> | null>(null);
    const [selectedEdge, setSelectedEdge] = useState<TopologyEdge<TEdge> | null>(null);

    // History for undo/redo
    const {
        pushHistory,
        undo: historyUndo,
        redo: historyRedo,
        canUndo,
        canRedo,
        clearHistory,
    } = useHistory();

    // Track if change came from history
    const isHistoryChange = useRef(false);

    // Apply layout on initial load
    useEffect(() => {
        if (config.layout && config.layout !== 'manual' && initialNodes.length > 0) {
            const layoutOptions: Partial<LayoutOptions> = {
                direction: config.layoutDirection ?? 'LR',
                nodeSpacing: config.nodeSpacing ?? 100,
                rankSpacing: config.rankSpacing ?? 200,
            };

            const { nodes: layoutedNodes } = autoLayout(
                initialNodes as TopologyNode<TNode>[],
                initialEdges as TopologyEdge<TEdge>[],
                config.layout,
                layoutOptions
            );

            setNodes(layoutedNodes);
        }
        // Only run on initial mount
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    // Call onChange when nodes/edges change
    useEffect(() => {
        if (!isHistoryChange.current) {
            onChange?.(nodes, edges);

            // Save to history if enabled
            if (enableHistory) {
                pushHistory(nodes, edges);
            }
        }
        isHistoryChange.current = false;
    }, [nodes, edges, onChange, enableHistory, pushHistory]);

    // Handle connection
    const onConnect = useCallback(
        (connection: Connection) => {
            if (readOnly) return;

            setEdges((eds) =>
                addEdge(
                    {
                        ...connection,
                        type: 'topology',
                        data: { status: 'inactive', animated: false } as TEdge,
                    },
                    eds
                )
            );
        },
        [readOnly, setEdges]
    );

    // Select node by ID
    const selectNode = useCallback(
        (nodeId: string | null) => {
            const node = nodeId ? nodes.find((n) => n.id === nodeId) ?? null : null;
            setSelectedNode(node);
            setSelectedEdge(null);
            onNodeSelect?.(node);
        },
        [nodes, onNodeSelect]
    );

    // Select edge by ID
    const selectEdge = useCallback(
        (edgeId: string | null) => {
            const edge = edgeId ? edges.find((e) => e.id === edgeId) ?? null : null;
            setSelectedEdge(edge);
            setSelectedNode(null);
            onEdgeSelect?.(edge);
        },
        [edges, onEdgeSelect]
    );

    // Update node data
    const updateNodeData = useCallback(
        (nodeId: string, data: Partial<TNode>) => {
            if (readOnly) return;

            setNodes((nds) =>
                nds.map((node) =>
                    node.id === nodeId
                        ? { ...node, data: { ...node.data, ...data } as TNode }
                        : node
                )
            );
        },
        [readOnly, setNodes]
    );

    // Update edge data
    const updateEdgeData = useCallback(
        (edgeId: string, data: Partial<TEdge>) => {
            if (readOnly) return;

            setEdges((eds) =>
                eds.map((edge) =>
                    edge.id === edgeId
                        ? { ...edge, data: { ...edge.data, ...data } as TEdge }
                        : edge
                )
            );
        },
        [readOnly, setEdges]
    );

    // Add a new node
    const addNode = useCallback(
        (node: TopologyNode<TNode>) => {
            if (readOnly) return;
            setNodes((nds) => [...nds, node]);
        },
        [readOnly, setNodes]
    );

    // Remove a node
    const removeNode = useCallback(
        (nodeId: string) => {
            if (readOnly) return;

            setNodes((nds) => nds.filter((node) => node.id !== nodeId));
            setEdges((eds) => eds.filter((edge) => edge.source !== nodeId && edge.target !== nodeId));

            if (selectedNode?.id === nodeId) {
                setSelectedNode(null);
            }
        },
        [readOnly, selectedNode, setNodes, setEdges]
    );

    // Remove an edge
    const removeEdge = useCallback(
        (edgeId: string) => {
            if (readOnly) return;

            setEdges((eds) => eds.filter((edge) => edge.id !== edgeId));

            if (selectedEdge?.id === edgeId) {
                setSelectedEdge(null);
            }
        },
        [readOnly, selectedEdge, setEdges]
    );

    // Apply layout
    const applyLayout = useCallback(
        (algorithm: 'dagre' | 'force' | 'manual' = 'dagre', layoutOptions: Partial<LayoutOptions> = {}) => {
            const opts: Partial<LayoutOptions> = {
                direction: config.layoutDirection ?? 'LR',
                nodeSpacing: config.nodeSpacing ?? 100,
                rankSpacing: config.rankSpacing ?? 200,
                ...layoutOptions,
            };

            const { nodes: layoutedNodes, edges: layoutedEdges } = autoLayout(nodes, edges, algorithm, opts);

            setNodes(layoutedNodes);
            setEdges(layoutedEdges);
        },
        [nodes, edges, config, setNodes, setEdges]
    );

    // Center graph
    const centerGraph = useCallback(() => {
        const centeredNodes = centerNodes(nodes);
        setNodes(centeredNodes);
    }, [nodes, setNodes]);

    // Undo
    const undo = useCallback(() => {
        if (!enableHistory || !canUndo) return;

        isHistoryChange.current = true;
        const previousState = historyUndo();
        if (previousState) {
            setNodes(previousState.nodes as TopologyNode<TNode>[]);
            setEdges(previousState.edges as TopologyEdge<TEdge>[]);
        }
    }, [enableHistory, canUndo, historyUndo, setNodes, setEdges]);

    // Redo
    const redo = useCallback(() => {
        if (!enableHistory || !canRedo) return;

        isHistoryChange.current = true;
        const nextState = historyRedo();
        if (nextState) {
            setNodes(nextState.nodes as TopologyNode<TNode>[]);
            setEdges(nextState.edges as TopologyEdge<TEdge>[]);
        }
    }, [enableHistory, canRedo, historyRedo, setNodes, setEdges]);

    // Reset to initial state
    const reset = useCallback(() => {
        setNodes(initialNodes as TopologyNode<TNode>[]);
        setEdges(initialEdges as TopologyEdge<TEdge>[]);
        setSelectedNode(null);
        setSelectedEdge(null);
        clearHistory();
    }, [initialNodes, initialEdges, setNodes, setEdges, clearHistory]);

    return {
        nodes,
        edges,
        setNodes,
        setEdges,
        onNodesChange: onNodesChange as (changes: unknown[]) => void,
        onEdgesChange: onEdgesChange as (changes: unknown[]) => void,
        onConnect,
        selectedNode,
        selectedEdge,
        selectNode,
        selectEdge,
        updateNodeData,
        updateEdgeData,
        addNode,
        removeNode,
        removeEdge,
        applyLayout,
        centerGraph,
        undo,
        redo,
        canUndo,
        canRedo,
        reset,
    };
}
