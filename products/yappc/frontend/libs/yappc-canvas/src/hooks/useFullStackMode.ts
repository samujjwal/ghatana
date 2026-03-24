/**
 * useFullStackMode Hook
 * 
 * React hook for managing full-stack development mode with split-screen canvas view.
 * Enables simultaneous frontend and backend development with data flow tracking.
 * 
 * @doc.type hook
 * @doc.purpose Full-stack split-screen mode state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';
import { useNodes, useEdges, useReactFlow } from '@xyflow/react';
import type { Node, Edge } from '@xyflow/react';

/**
 * Canvas side type
 */
export type CanvasSide = 'frontend' | 'backend';

/**
 * Layout mode
 */
export type LayoutMode = 'single' | 'split-horizontal' | 'split-vertical';

/**
 * Data flow edge type
 */
export type DataFlowEdgeData = {
    dataType?: string;
    validated?: boolean;
    validationErrors?: string[];
    flowDirection?: 'frontend-to-backend' | 'backend-to-frontend';
};

export type DataFlowEdge = Edge<DataFlowEdgeData>;

/**
 * Canvas partition
 */
export interface CanvasPartition {
    side: CanvasSide;
    nodes: Node[];
    edges: Edge[];
    bounds: {
        x: number;
        y: number;
        width: number;
        height: number;
    };
}

/**
 * Validation result
 */
export interface CrossCanvasValidation {
    valid: boolean;
    errors: Array<{
        type: 'missing-backend' | 'missing-frontend' | 'type-mismatch' | 'circular-dependency';
        message: string;
        nodeId?: string;
        edgeId?: string;
    }>;
    warnings: Array<{
        message: string;
        nodeId?: string;
    }>;
}

/**
 * useFullStackMode options
 */
export interface UseFullStackModeOptions {
    /** Initial layout mode */
    initialMode?: LayoutMode;
    /** Auto-validate on changes */
    autoValidate?: boolean;
    /** Sync viewport between canvases */
    syncViewport?: boolean;
}

/**
 * useFullStackMode result
 */
export interface UseFullStackModeResult {
    /** Current layout mode */
    mode: LayoutMode;
    /** Set layout mode */
    setMode: (mode: LayoutMode) => void;
    /** Toggle between single and split modes */
    toggle: () => void;
    /** Whether in split mode */
    isSplit: boolean;
    /** Frontend partition */
    frontendPartition: CanvasPartition;
    /** Backend partition */
    backendPartition: CanvasPartition;
    /** Data flow edges between canvases */
    dataFlowEdges: DataFlowEdge[];
    /** Cross-canvas validation result */
    validation: CrossCanvasValidation;
    /** Validate cross-canvas connections */
    validate: () => CrossCanvasValidation;
    /** Move node to other canvas */
    moveNodeToCanvas: (nodeId: string, targetSide: CanvasSide) => void;
    /** Create data flow edge */
    createDataFlow: (sourceId: string, targetId: string, dataType: string) => void;
    /** Get node's canvas side */
    getNodeSide: (nodeId: string) => CanvasSide | null;
    /** Active canvas side */
    activeSide: CanvasSide;
    /** Set active canvas side */
    setActiveSide: (side: CanvasSide) => void;
}

/**
 * Determine node's canvas side based on type or position
 */
function determineNodeSide(node: Node, mode: LayoutMode, viewportWidth: number): CanvasSide {
    // Check node type
    const frontendTypes = ['ui', 'component', 'screen', 'page', 'frontend'];
    const backendTypes = ['api', 'service', 'database', 'backend', 'server'];

    if (node.type && frontendTypes.includes(node.type)) {
        return 'frontend';
    }
    if (node.type && backendTypes.includes(node.type)) {
        return 'backend';
    }

    // Fallback to position in split mode
    if (mode === 'split-vertical') {
        return node.position.x < viewportWidth / 2 ? 'frontend' : 'backend';
    }
    if (mode === 'split-horizontal') {
        return node.position.y < 500 ? 'frontend' : 'backend';
    }

    // Default to frontend
    return 'frontend';
}

/**
 * useFullStackMode hook
 */
export function useFullStackMode(
    options: UseFullStackModeOptions = {}
): UseFullStackModeResult {
    const {
        initialMode = 'single',
        autoValidate = true,
        syncViewport = false,
    } = options;

    const [mode, setMode] = useState<LayoutMode>(initialMode);
    const [activeSide, setActiveSide] = useState<CanvasSide>('frontend');

    const nodes = useNodes();
    const edges = useEdges();
    const { setViewport, getViewport, setNodes: updateNodes, setEdges: updateEdges } = useReactFlow();

    const isSplit = mode !== 'single';

    /**
     * Get viewport dimensions
     */
    const viewportWidth = useMemo(() => {
        if (typeof window !== 'undefined') {
            return window.innerWidth;
        }
        return 1920; // Default width
    }, []);

    /**
     * Partition nodes by canvas side
     */
    const { frontendPartition, backendPartition } = useMemo(() => {
        const frontendNodes: Node[] = [];
        const backendNodes: Node[] = [];

        nodes.forEach((node) => {
            const side = determineNodeSide(node, mode, viewportWidth);
            if (side === 'frontend') {
                frontendNodes.push(node);
            } else {
                backendNodes.push(node);
            }
        });

        // Calculate bounds
        const calculateBounds = (nodeList: Node[]) => {
            if (nodeList.length === 0) {
                return { x: 0, y: 0, width: 0, height: 0 };
            }

            const xs = nodeList.map((n) => n.position.x);
            const ys = nodeList.map((n) => n.position.y);
            const minX = Math.min(...xs);
            const minY = Math.min(...ys);
            const maxX = Math.max(...xs.map((x, i) => x + (nodeList[i].width || 200)));
            const maxY = Math.max(...ys.map((y, i) => y + (nodeList[i].height || 100)));

            return {
                x: minX,
                y: minY,
                width: maxX - minX,
                height: maxY - minY,
            };
        };

        const frontendEdges = edges.filter((edge) =>
            frontendNodes.some((n) => n.id === edge.source || n.id === edge.target)
        );

        const backendEdges = edges.filter((edge) =>
            backendNodes.some((n) => n.id === edge.source || n.id === edge.target)
        );

        return {
            frontendPartition: {
                side: 'frontend' as CanvasSide,
                nodes: frontendNodes,
                edges: frontendEdges,
                bounds: calculateBounds(frontendNodes),
            },
            backendPartition: {
                side: 'backend' as CanvasSide,
                nodes: backendNodes,
                edges: backendEdges,
                bounds: calculateBounds(backendNodes),
            },
        };
    }, [nodes, edges, mode, viewportWidth]);

    /**
     * Get data flow edges (edges connecting frontend and backend)
     */
    const dataFlowEdges = useMemo(() => {
        const frontendNodeIds = new Set(frontendPartition.nodes.map((n) => n.id));
        const backendNodeIds = new Set(backendPartition.nodes.map((n) => n.id));

        return edges
            .filter((edge) => {
                const sourceIsFrontend = frontendNodeIds.has(edge.source);
                const targetIsFrontend = frontendNodeIds.has(edge.target);
                return sourceIsFrontend !== targetIsFrontend; // Cross-canvas edge
            })
            .map((edge) => {
                const sourceIsFrontend = frontendNodeIds.has(edge.source);
                const prevData = (edge.data ?? {}) as Partial<DataFlowEdgeData>;
                return {
                    ...edge,
                    data: {
                        ...prevData,
                        flowDirection: sourceIsFrontend
                            ? ('frontend-to-backend' as const)
                            : ('backend-to-frontend' as const),
                    },
                } as DataFlowEdge;
            });
    }, [edges, frontendPartition.nodes, backendPartition.nodes]);

    /**
     * Validate cross-canvas connections
     */
    const validate = useCallback((): CrossCanvasValidation => {
        const errors: CrossCanvasValidation['errors'] = [];
        const warnings: CrossCanvasValidation['warnings'] = [];

        // Check for missing backend APIs
        const frontendApiCalls = frontendPartition.nodes.filter(
            (n) => n.type === 'apiCall' || !!(n.data as Record<string, unknown> | undefined)?.isApiCall
        );

        frontendApiCalls.forEach((node) => {
            const hasBackendEndpoint = dataFlowEdges.some(
                (edge) =>
                    edge.source === node.id &&
                    backendPartition.nodes.some((bn) => bn.id === edge.target && bn.type === 'api')
            );

            if (!hasBackendEndpoint) {
                errors.push({
                    type: 'missing-backend',
                    message: `Frontend API call "${node.data?.label || node.id}" has no backend endpoint`,
                    nodeId: node.id,
                });
            }
        });

        // Check for missing frontend consumers
        const backendApis = backendPartition.nodes.filter((n) => n.type === 'api');

        backendApis.forEach((node) => {
            const hasFrontendConsumer = dataFlowEdges.some(
                (edge) => edge.target === node.id && frontendPartition.nodes.some((fn) => fn.id === edge.source)
            );

            if (!hasFrontendConsumer) {
                warnings.push({
                    message: `Backend API "${node.data?.label || node.id}" has no frontend consumer`,
                    nodeId: node.id,
                });
            }
        });

        // Check for type mismatches
        dataFlowEdges.forEach((edge) => {
            const sourceNode = nodes.find((n) => n.id === edge.source);
            const targetNode = nodes.find((n) => n.id === edge.target);

            if (sourceNode && targetNode) {
                const sourceData = (sourceNode.data ?? {}) as Record<string, unknown>;
                const targetData = (targetNode.data ?? {}) as Record<string, unknown>;

                const sourceType = sourceData.dataType || sourceData.responseType;
                const targetType = targetData.dataType || targetData.requestType;

                if (sourceType && targetType && sourceType !== targetType) {
                    errors.push({
                        type: 'type-mismatch',
                        message: `Type mismatch: ${sourceType} → ${targetType}`,
                        edgeId: edge.id,
                    });
                }
            }
        });

        // Check for circular dependencies
        const visited = new Set<string>();
        const recursionStack = new Set<string>();

        const hasCycle = (nodeId: string): boolean => {
            visited.add(nodeId);
            recursionStack.add(nodeId);

            const outgoingEdges = dataFlowEdges.filter((e) => e.source === nodeId);

            for (const edge of outgoingEdges) {
                if (!visited.has(edge.target)) {
                    if (hasCycle(edge.target)) {
                        return true;
                    }
                } else if (recursionStack.has(edge.target)) {
                    errors.push({
                        type: 'circular-dependency',
                        message: `Circular dependency detected involving ${nodeId}`,
                        nodeId,
                    });
                    return true;
                }
            }

            recursionStack.delete(nodeId);
            return false;
        };

        nodes.forEach((node) => {
            if (!visited.has(node.id)) {
                hasCycle(node.id);
            }
        });

        return {
            valid: errors.length === 0,
            errors,
            warnings,
        };
    }, [nodes, dataFlowEdges, frontendPartition, backendPartition]);

    /**
     * Auto-validate on changes
     */
    const validation = useMemo(() => {
        if (autoValidate && isSplit) {
            return validate();
        }
        return { valid: true, errors: [], warnings: [] };
    }, [autoValidate, isSplit, validate]);

    /**
     * Toggle mode
     */
    const toggle = useCallback(() => {
        setMode((prev) => (prev === 'single' ? 'split-vertical' : 'single'));
    }, []);

    /**
     * Move node to other canvas
     */
    const moveNodeToCanvas = useCallback(
        (nodeId: string, targetSide: CanvasSide) => {
            const node = nodes.find((n) => n.id === nodeId);
            if (!node) return;

            const updatedNode = {
                ...node,
                position: {
                    ...node.position,
                    x: targetSide === 'backend' ? viewportWidth / 2 + 100 : 100,
                },
            };

            updateNodes(nodes.map((n) => (n.id === nodeId ? updatedNode : n)));
        },
        [nodes, updateNodes, viewportWidth]
    );

    /**
     * Create data flow edge
     */
    const createDataFlow = useCallback(
        (sourceId: string, targetId: string, dataType: string) => {
            const newEdge: DataFlowEdge = {
                id: `dataflow-${sourceId}-${targetId}`,
                source: sourceId,
                target: targetId,
                type: 'smoothstep',
                animated: true,
                style: { stroke: '#ff6b6b', strokeWidth: 2 },
                data: {
                    dataType,
                    validated: false,
                    flowDirection:
                        frontendPartition.nodes.some((n) => n.id === sourceId)
                            ? 'frontend-to-backend'
                            : 'backend-to-frontend',
                },
            };

            updateEdges([...edges, newEdge]);
        },
        [edges, updateEdges, frontendPartition.nodes]
    );

    /**
     * Get node's canvas side
     */
    const getNodeSide = useCallback(
        (nodeId: string): CanvasSide | null => {
            const node = nodes.find((n) => n.id === nodeId);
            if (!node) return null;
            return determineNodeSide(node, mode, viewportWidth);
        },
        [nodes, mode, viewportWidth]
    );

    return {
        mode,
        setMode,
        toggle,
        isSplit,
        frontendPartition,
        backendPartition,
        dataFlowEdges,
        validation,
        validate,
        moveNodeToCanvas,
        createDataFlow,
        getNodeSide,
        activeSide,
        setActiveSide,
    };
}
