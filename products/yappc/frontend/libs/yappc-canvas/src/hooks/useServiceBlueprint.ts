/**
 * useServiceBlueprint Hook
 * 
 * State management for service blueprint mapping
 * 
 * @doc.type hook
 * @doc.purpose Service blueprint state management
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useCallback, useMemo } from 'react';

/**
 * Lane types for service blueprint
 */
export type LaneType = 'customer' | 'frontstage' | 'backstage' | 'support';

/**
 * Touchpoint for customer interactions
 */
export interface Touchpoint {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Touchpoint name
     */
    name: string;

    /**
     * Channel (optional)
     */
    channel?: string;

    /**
     * Metrics (optional)
     */
    metrics?: string;
}

/**
 * Process node in blueprint
 */
export interface ProcessNode {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Node name
     */
    name: string;

    /**
     * Description (optional)
     */
    description?: string;

    /**
     * Duration (optional)
     */
    duration?: string;

    /**
     * Touchpoints associated with this node
     */
    touchpoints?: Touchpoint[];
}

/**
 * Connection between nodes
 */
export interface NodeConnection {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Source node ID
     */
    from: string;

    /**
     * Target node ID
     */
    to: string;

    /**
     * Connection type
     */
    type: 'flow' | 'support';
}

/**
 * Blueprint lane (swim lane)
 */
export interface BlueprintLane {
    /**
     * Unique identifier
     */
    id: string;

    /**
     * Lane type
     */
    type: LaneType;

    /**
     * Process nodes in this lane
     */
    nodes: ProcessNode[];
}

/**
 * Options for useServiceBlueprint hook
 */
export interface UseServiceBlueprintOptions {
    /**
     * Initial blueprint name
     */
    initialBlueprintName?: string;

    /**
     * Initial service description
     */
    initialServiceDescription?: string;
}

/**
 * Result of useServiceBlueprint hook
 */
export interface UseServiceBlueprintResult {
    /**
     * Blueprint lanes
     */
    lanes: BlueprintLane[];

    /**
     * Node connections
     */
    connections: NodeConnection[];

    /**
     * Blueprint name
     */
    blueprintName: string;

    /**
     * Service description
     */
    serviceDescription: string;

    /**
     * Set blueprint name
     */
    setBlueprintName: (name: string) => void;

    /**
     * Set service description
     */
    setServiceDescription: (description: string) => void;

    /**
     * Add a node to a lane
     */
    addNode: (laneType: LaneType, node: Omit<ProcessNode, 'id' | 'touchpoints'>) => string;

    /**
     * Update a node
     */
    updateNode: (nodeId: string, updates: Partial<Omit<ProcessNode, 'id'>>) => void;

    /**
     * Delete a node
     */
    deleteNode: (nodeId: string) => void;

    /**
     * Get node by ID
     */
    getNode: (nodeId: string) => ProcessNode | undefined;

    /**
     * Add connection between nodes
     */
    addConnection: (connection: Omit<NodeConnection, 'id'>) => void;

    /**
     * Delete connection
     */
    deleteConnection: (connectionId: string) => void;

    /**
     * Add touchpoint to node
     */
    addTouchpoint: (nodeId: string, touchpoint: Omit<Touchpoint, 'id'>) => void;

    /**
     * Delete touchpoint
     */
    deleteTouchpoint: (nodeId: string, touchpointId: string) => void;

    /**
     * Validate blueprint completeness
     */
    validateBlueprint: () => string[];

    /**
     * Generate flow analysis
     */
    analyzeFlow: () => {
        orphanedNodes: ProcessNode[];
        missingTouchpoints: ProcessNode[];
        crossLaneConnections: NodeConnection[];
    };

    /**
     * Export blueprint as JSON
     */
    exportBlueprint: () => string;

    /**
     * Get total node count
     */
    getNodeCount: () => number;

    /**
     * Get total connection count
     */
    getConnectionCount: () => number;

    /**
     * Get total touchpoint count
     */
    getTouchpointCount: () => number;
}

// Default lanes in order
const DEFAULT_LANE_TYPES: LaneType[] = ['customer', 'frontstage', 'backstage', 'support'];

/**
 * Service blueprint mapping hook
 */
export const useServiceBlueprint = (options: UseServiceBlueprintOptions = {}): UseServiceBlueprintResult => {
    const { initialBlueprintName = 'Service Blueprint', initialServiceDescription = '' } = options;

    // Initialize lanes
    const initialLanes: BlueprintLane[] = DEFAULT_LANE_TYPES.map((type, index) => ({
        id: `lane-${type}`,
        type,
        nodes: [],
    }));

    // State
    const [lanes, setLanes] = useState<BlueprintLane[]>(initialLanes);
    const [connections, setConnections] = useState<NodeConnection[]>([]);
    const [blueprintName, setBlueprintName] = useState(initialBlueprintName);
    const [serviceDescription, setServiceDescription] = useState(initialServiceDescription);

    // Generate unique ID
    const generateId = useCallback((prefix: string) => {
        return `${prefix}-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }, []);

    // Add node
    const addNode = useCallback((laneType: LaneType, node: Omit<ProcessNode, 'id' | 'touchpoints'>): string => {
        const id = generateId('node');
        const newNode: ProcessNode = {
            ...node,
            id,
            touchpoints: [],
        };

        setLanes(prev =>
            prev.map(lane =>
                lane.type === laneType
                    ? { ...lane, nodes: [...lane.nodes, newNode] }
                    : lane
            )
        );

        return id;
    }, [generateId]);

    // Update node
    const updateNode = useCallback((nodeId: string, updates: Partial<Omit<ProcessNode, 'id'>>) => {
        setLanes(prev =>
            prev.map(lane => ({
                ...lane,
                nodes: lane.nodes.map(node =>
                    node.id === nodeId ? { ...node, ...updates } : node
                ),
            }))
        );
    }, []);

    // Delete node
    const deleteNode = useCallback((nodeId: string) => {
        setLanes(prev =>
            prev.map(lane => ({
                ...lane,
                nodes: lane.nodes.filter(node => node.id !== nodeId),
            }))
        );

        // Also delete connections involving this node
        setConnections(prev =>
            prev.filter(conn => conn.from !== nodeId && conn.to !== nodeId)
        );
    }, []);

    // Get node
    const getNode = useCallback((nodeId: string): ProcessNode | undefined => {
        for (const lane of lanes) {
            const node = lane.nodes.find(n => n.id === nodeId);
            if (node) return node;
        }
        return undefined;
    }, [lanes]);

    // Add connection
    const addConnection = useCallback((connection: Omit<NodeConnection, 'id'>) => {
        const id = generateId('conn');
        const newConnection: NodeConnection = {
            ...connection,
            id,
        };
        setConnections(prev => [...prev, newConnection]);
    }, [generateId]);

    // Delete connection
    const deleteConnection = useCallback((connectionId: string) => {
        setConnections(prev => prev.filter(conn => conn.id !== connectionId));
    }, []);

    // Add touchpoint
    const addTouchpoint = useCallback((nodeId: string, touchpoint: Omit<Touchpoint, 'id'>) => {
        const id = generateId('touchpoint');
        const newTouchpoint: Touchpoint = {
            ...touchpoint,
            id,
        };

        setLanes(prev =>
            prev.map(lane => ({
                ...lane,
                nodes: lane.nodes.map(node =>
                    node.id === nodeId
                        ? { ...node, touchpoints: [...(node.touchpoints || []), newTouchpoint] }
                        : node
                ),
            }))
        );
    }, [generateId]);

    // Delete touchpoint
    const deleteTouchpoint = useCallback((nodeId: string, touchpointId: string) => {
        setLanes(prev =>
            prev.map(lane => ({
                ...lane,
                nodes: lane.nodes.map(node =>
                    node.id === nodeId
                        ? {
                            ...node,
                            touchpoints: (node.touchpoints || []).filter(tp => tp.id !== touchpointId),
                        }
                        : node
                ),
            }))
        );
    }, []);

    // Validate blueprint
    const validateBlueprint = useCallback((): string[] => {
        const issues: string[] = [];

        // Check for empty lanes
        const emptyLanes = lanes.filter(lane => lane.nodes.length === 0);
        if (emptyLanes.length > 0) {
            issues.push(`Empty lanes: ${emptyLanes.map(l => l.type).join(', ')}`);
        }

        // Check for nodes without touchpoints in customer/frontstage
        lanes.forEach(lane => {
            if (lane.type === 'customer' || lane.type === 'frontstage') {
                const noTouchpoints = lane.nodes.filter(n => !n.touchpoints || n.touchpoints.length === 0);
                if (noTouchpoints.length > 0) {
                    issues.push(`${lane.type} nodes without touchpoints: ${noTouchpoints.map(n => n.name).join(', ')}`);
                }
            }
        });

        // Check for orphaned nodes (no connections)
        const allNodes = lanes.flatMap(lane => lane.nodes);
        const connectedNodeIds = new Set([
            ...connections.map(c => c.from),
            ...connections.map(c => c.to),
        ]);
        const orphaned = allNodes.filter(n => !connectedNodeIds.has(n.id));
        if (orphaned.length > 0) {
            issues.push(`Orphaned nodes (no connections): ${orphaned.map(n => n.name).join(', ')}`);
        }

        return issues;
    }, [lanes, connections]);

    // Analyze flow
    const analyzeFlow = useCallback(() => {
        const allNodes = lanes.flatMap(lane => lane.nodes);
        const connectedNodeIds = new Set([
            ...connections.map(c => c.from),
            ...connections.map(c => c.to),
        ]);

        const orphanedNodes = allNodes.filter(n => !connectedNodeIds.has(n.id));

        const missingTouchpoints = lanes
            .filter(lane => lane.type === 'customer' || lane.type === 'frontstage')
            .flatMap(lane => lane.nodes)
            .filter(n => !n.touchpoints || n.touchpoints.length === 0);

        // Find connections that cross multiple lanes
        const crossLaneConnections = connections.filter(conn => {
            const fromNode = getNode(conn.from);
            const toNode = getNode(conn.to);
            if (!fromNode || !toNode) return false;

            const fromLane = lanes.find(l => l.nodes.some(n => n.id === fromNode.id));
            const toLane = lanes.find(l => l.nodes.some(n => n.id === toNode.id));

            return fromLane?.type !== toLane?.type;
        });

        return {
            orphanedNodes,
            missingTouchpoints,
            crossLaneConnections,
        };
    }, [lanes, connections, getNode]);

    // Export blueprint
    const exportBlueprint = useCallback((): string => {
        const blueprint = {
            name: blueprintName,
            description: serviceDescription,
            lanes: lanes.map(lane => ({
                type: lane.type,
                nodes: lane.nodes.map(node => ({
                    name: node.name,
                    description: node.description,
                    duration: node.duration,
                    touchpoints: node.touchpoints,
                })),
            })),
            connections: connections.map(conn => ({
                from: getNode(conn.from)?.name || 'Unknown',
                to: getNode(conn.to)?.name || 'Unknown',
                type: conn.type,
            })),
            metadata: {
                exportedAt: new Date().toISOString(),
                nodeCount: getNodeCount(),
                connectionCount: getConnectionCount(),
                touchpointCount: getTouchpointCount(),
            },
            validation: validateBlueprint(),
        };
        return JSON.stringify(blueprint, null, 2);
    }, [blueprintName, serviceDescription, lanes, connections, getNode]);

    // Get node count
    const getNodeCount = useCallback((): number => {
        return lanes.reduce((count, lane) => count + lane.nodes.length, 0);
    }, [lanes]);

    // Get connection count
    const getConnectionCount = useCallback((): number => {
        return connections.length;
    }, [connections]);

    // Get touchpoint count
    const getTouchpointCount = useCallback((): number => {
        return lanes.reduce((count, lane) =>
            count + lane.nodes.reduce((nodeCount, node) =>
                nodeCount + (node.touchpoints?.length || 0), 0
            ), 0
        );
    }, [lanes]);

    return {
        lanes,
        connections,
        blueprintName,
        serviceDescription,
        setBlueprintName,
        setServiceDescription,
        addNode,
        updateNode,
        deleteNode,
        getNode,
        addConnection,
        deleteConnection,
        addTouchpoint,
        deleteTouchpoint,
        validateBlueprint,
        analyzeFlow,
        exportBlueprint,
        getNodeCount,
        getConnectionCount,
        getTouchpointCount,
    };
};
