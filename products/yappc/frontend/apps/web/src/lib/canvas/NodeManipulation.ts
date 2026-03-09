/**
 * Node Manipulation Systems
 * 
 * Implements resize, layer, group, and connection management
 * 
 * @doc.type module
 * @doc.purpose Advanced node manipulation for unified canvas
 * @doc.layer core
 * @doc.pattern Manager
 */

import type { HierarchicalNode } from './HierarchyManager';

// ============================================================================
// RESIZE SYSTEM
// ============================================================================

export interface Point {
    x: number;
    y: number;
}

export interface KeyModifiers {
    shift: boolean;
    alt: boolean;
    ctrl: boolean;
    meta: boolean;
}

export type ResizeHandlePosition = 'tl' | 'tr' | 'bl' | 'br' | 't' | 'r' | 'b' | 'l';

export interface ResizeHandle {
    id: string;
    type: 'corner' | 'edge';
    position: ResizeHandlePosition;
    cursor: string;
}

export interface Bounds {
    x: number;
    y: number;
    width: number;
    height: number;
}

export class NodeResizeManager {
    private activeNode: HierarchicalNode | null = null;
    private activeHandle: ResizeHandle | null = null;
    private startBounds: Bounds | null = null;
    private startMousePos: Point | null = null;

    constructor(
        private getNode: (id: string) => HierarchicalNode | undefined,
        private updateNode: (id: string, updates: Partial<HierarchicalNode>) => void,
        private layoutChildren?: (nodeId: string) => void
    ) { }

    /**
     * Get resize handles for a node
     */
    getHandles(nodeId: string): ResizeHandle[] {
        return [
            // Corners
            { id: `${nodeId}-tl`, type: 'corner', position: 'tl', cursor: 'nwse-resize' },
            { id: `${nodeId}-tr`, type: 'corner', position: 'tr', cursor: 'nesw-resize' },
            { id: `${nodeId}-bl`, type: 'corner', position: 'bl', cursor: 'nesw-resize' },
            { id: `${nodeId}-br`, type: 'corner', position: 'br', cursor: 'nwse-resize' },

            // Edges
            { id: `${nodeId}-t`, type: 'edge', position: 't', cursor: 'ns-resize' },
            { id: `${nodeId}-r`, type: 'edge', position: 'r', cursor: 'ew-resize' },
            { id: `${nodeId}-b`, type: 'edge', position: 'b', cursor: 'ns-resize' },
            { id: `${nodeId}-l`, type: 'edge', position: 'l', cursor: 'ew-resize' },
        ];
    }

    /**
     * Start resize operation
     */
    startResize(nodeId: string, handlePosition: ResizeHandlePosition, mousePos: Point): void {
        const node = this.getNode(nodeId);
        if (!node) return;

        this.activeNode = node;
        this.activeHandle = this.getHandles(nodeId).find(h => h.position === handlePosition) || null;
        this.startBounds = {
            x: node.position.x,
            y: node.position.y,
            width: node.size.width,
            height: node.size.height
        };
        this.startMousePos = mousePos;
    }

    /**
     * Update resize during drag
     */
    updateResize(currentMousePos: Point, modifiers: KeyModifiers): void {
        if (!this.activeNode || !this.activeHandle || !this.startBounds || !this.startMousePos) {
            return;
        }

        const delta: Point = {
            x: currentMousePos.x - this.startMousePos.x,
            y: currentMousePos.y - this.startMousePos.y
        };

        const lockAspect = modifiers.shift;
        const fromCenter = modifiers.alt;

        const newBounds = this.calculateNewBounds(
            this.startBounds,
            delta,
            this.activeHandle.position,
            lockAspect,
            fromCenter
        );

        // Apply minimum size constraints
        const minWidth = (this.activeNode.data.minWidth as number) || 50;
        const minHeight = (this.activeNode.data.minHeight as number) || 50;

        if (newBounds.width < minWidth) {
            newBounds.width = minWidth;
        }
        if (newBounds.height < minHeight) {
            newBounds.height = minHeight;
        }

        // Update node
        this.updateNode(this.activeNode.id, {
            position: { x: newBounds.x, y: newBounds.y },
            size: { width: newBounds.width, height: newBounds.height }
        });

        // Update children layout if container
        if (this.activeNode.isContainer && this.activeNode.childLayout !== 'free' && this.layoutChildren) {
            this.layoutChildren(this.activeNode.id);
        }
    }

    /**
     * End resize operation
     */
    endResize(): void {
        this.activeNode = null;
        this.activeHandle = null;
        this.startBounds = null;
        this.startMousePos = null;
    }

    private calculateNewBounds(
        startBounds: Bounds,
        delta: Point,
        position: ResizeHandlePosition,
        lockAspect: boolean,
        fromCenter: boolean
    ): Bounds {
        const newBounds = { ...startBounds };

        // Calculate aspect ratio if needed
        const aspectRatio = lockAspect ? startBounds.width / startBounds.height : 0;

        switch (position) {
            case 'tl': // Top-left corner
                if (lockAspect) {
                    // Maintain aspect ratio
                    const scale = Math.min(
                        (startBounds.width - delta.x) / startBounds.width,
                        (startBounds.height - delta.y) / startBounds.height
                    );
                    newBounds.width = startBounds.width * scale;
                    newBounds.height = startBounds.height * scale;
                    newBounds.x = startBounds.x + startBounds.width - newBounds.width;
                    newBounds.y = startBounds.y + startBounds.height - newBounds.height;
                } else {
                    newBounds.x = startBounds.x + delta.x;
                    newBounds.y = startBounds.y + delta.y;
                    newBounds.width = startBounds.width - delta.x;
                    newBounds.height = startBounds.height - delta.y;
                }
                break;

            case 'tr': // Top-right corner
                if (lockAspect) {
                    const scale = Math.min(
                        (startBounds.width + delta.x) / startBounds.width,
                        (startBounds.height - delta.y) / startBounds.height
                    );
                    newBounds.width = startBounds.width * scale;
                    newBounds.height = startBounds.height * scale;
                    newBounds.y = startBounds.y + startBounds.height - newBounds.height;
                } else {
                    newBounds.y = startBounds.y + delta.y;
                    newBounds.width = startBounds.width + delta.x;
                    newBounds.height = startBounds.height - delta.y;
                }
                break;

            case 'bl': // Bottom-left corner
                if (lockAspect) {
                    const scale = Math.min(
                        (startBounds.width - delta.x) / startBounds.width,
                        (startBounds.height + delta.y) / startBounds.height
                    );
                    newBounds.width = startBounds.width * scale;
                    newBounds.height = startBounds.height * scale;
                    newBounds.x = startBounds.x + startBounds.width - newBounds.width;
                } else {
                    newBounds.x = startBounds.x + delta.x;
                    newBounds.width = startBounds.width - delta.x;
                    newBounds.height = startBounds.height + delta.y;
                }
                break;

            case 'br': // Bottom-right corner
                if (lockAspect) {
                    const scale = Math.max(
                        (startBounds.width + delta.x) / startBounds.width,
                        (startBounds.height + delta.y) / startBounds.height
                    );
                    newBounds.width = startBounds.width * scale;
                    newBounds.height = startBounds.height * scale;
                } else {
                    newBounds.width = startBounds.width + delta.x;
                    newBounds.height = startBounds.height + delta.y;
                }
                break;

            case 't': // Top edge
                newBounds.y = startBounds.y + delta.y;
                newBounds.height = startBounds.height - delta.y;
                break;

            case 'r': // Right edge
                newBounds.width = startBounds.width + delta.x;
                break;

            case 'b': // Bottom edge
                newBounds.height = startBounds.height + delta.y;
                break;

            case 'l': // Left edge
                newBounds.x = startBounds.x + delta.x;
                newBounds.width = startBounds.width - delta.x;
                break;
        }

        // Apply fromCenter transformation
        if (fromCenter) {
            const widthDiff = newBounds.width - startBounds.width;
            const heightDiff = newBounds.height - startBounds.height;
            newBounds.x -= widthDiff / 2;
            newBounds.y -= heightDiff / 2;
        }

        return newBounds;
    }
}

// ============================================================================
// LAYER SYSTEM
// ============================================================================

export interface Layer {
    id: string;
    name: string;
    visible: boolean;
    locked: boolean;
    opacity: number;
    zIndexRange: [number, number];  // e.g., [0, 999] for layer 1
}

export class LayerManager {
    private layers: Map<string, Layer> = new Map();
    private nodeLayerMap: Map<string, string> = new Map();  // nodeId -> layerId

    constructor(
        private updateNode: (nodeId: string, updates: Partial<HierarchicalNode>) => void
    ) {
        // Create default layers
        this.createLayer('background', 'Background', [0, 999]);
        this.createLayer('content', 'Content', [1000, 1999]);
        this.createLayer('ui', 'UI', [2000, 2999]);
    }

    /**
     * Create a new layer
     */
    createLayer(id: string, name: string, zIndexRange: [number, number]): Layer {
        const layer: Layer = {
            id,
            name,
            visible: true,
            locked: false,
            opacity: 1.0,
            zIndexRange
        };

        this.layers.set(id, layer);
        return layer;
    }

    /**
     * Get all layers
     */
    getLayers(): Layer[] {
        return Array.from(this.layers.values());
    }

    /**
     * Get layer by ID
     */
    getLayer(layerId: string): Layer | undefined {
        return this.layers.get(layerId);
    }

    /**
     * Assign node to layer
     */
    setNodeLayer(nodeId: string, layerId: string): void {
        const layer = this.layers.get(layerId);
        if (!layer) {
            throw new Error(`Layer ${layerId} not found`);
        }

        this.nodeLayerMap.set(nodeId, layerId);

        // Update node's z-index to be within layer range
        const zIndex = layer.zIndexRange[0];
        this.updateNode(nodeId, {
            data: { layerId, zIndex }
        });
    }

    /**
     * Get node's layer
     */
    getNodeLayer(nodeId: string): Layer | undefined {
        const layerId = this.nodeLayerMap.get(nodeId);
        return layerId ? this.layers.get(layerId) : undefined;
    }

    /**
     * Bring node forward within its layer
     */
    bringForward(nodeId: string): void {
        const layerId = this.nodeLayerMap.get(nodeId);
        if (!layerId) return;

        const layer = this.layers.get(layerId);
        if (!layer) return;

        // Increment z-index within layer range
        this.updateNode(nodeId, {
            data: { zIndex: Math.min((this.getNodeZIndex(nodeId) || 0) + 1, layer.zIndexRange[1]) }
        });
    }

    /**
     * Send node backward within its layer
     */
    sendBackward(nodeId: string): void {
        const layerId = this.nodeLayerMap.get(nodeId);
        if (!layerId) return;

        const layer = this.layers.get(layerId);
        if (!layer) return;

        // Decrement z-index within layer range
        this.updateNode(nodeId, {
            data: { zIndex: Math.max((this.getNodeZIndex(nodeId) || 0) - 1, layer.zIndexRange[0]) }
        });
    }

    /**
     * Bring to front of layer
     */
    bringToFront(nodeId: string): void {
        const layerId = this.nodeLayerMap.get(nodeId);
        if (!layerId) return;

        const layer = this.layers.get(layerId);
        if (!layer) return;

        this.updateNode(nodeId, {
            data: { zIndex: layer.zIndexRange[1] }
        });
    }

    /**
     * Send to back of layer
     */
    sendToBack(nodeId: string): void {
        const layerId = this.nodeLayerMap.get(nodeId);
        if (!layerId) return;

        const layer = this.layers.get(layerId);
        if (!layer) return;

        this.updateNode(nodeId, {
            data: { zIndex: layer.zIndexRange[0] }
        });
    }

    /**
     * Toggle layer visibility
     */
    toggleLayerVisibility(layerId: string): void {
        const layer = this.layers.get(layerId);
        if (!layer) return;

        layer.visible = !layer.visible;
        this.layers.set(layerId, layer);
    }

    /**
     * Toggle layer lock
     */
    toggleLayerLock(layerId: string): void {
        const layer = this.layers.get(layerId);
        if (!layer) return;

        layer.locked = !layer.locked;
        this.layers.set(layerId, layer);
    }

    private getNodeZIndex(nodeId: string): number | undefined {
        // Would need to get from node data
        return 0;
    }
}

// ============================================================================
// GROUP SYSTEM
// ============================================================================

export interface Group {
    id: string;
    name: string;
    nodeIds: string[];
    locked: boolean;
    collapsed: boolean;
}

export class GroupManager {
    private groups: Map<string, Group> = new Map();

    constructor(
        private getNode: (id: string) => HierarchicalNode | undefined,
        private updateNode: (id: string, updates: Partial<HierarchicalNode>) => void,
        private addChild: (parentId: string, child: Partial<HierarchicalNode>) => HierarchicalNode
    ) { }

    /**
     * Create group from selected nodes
     */
    createGroup(nodeIds: string[], name?: string): Group {
        const groupId = `group_${Date.now()}`;

        const group: Group = {
            id: groupId,
            name: name || `Group ${this.groups.size + 1}`,
            nodeIds: [...nodeIds],
            locked: false,
            collapsed: false
        };

        this.groups.set(groupId, group);

        // Calculate bounding box of all nodes
        const bounds = this.calculateGroupBounds(nodeIds);

        // Create container node for group
        const groupNode = this.addChild('root', {
            id: groupId,
            type: 'group',
            position: { x: bounds.x, y: bounds.y },
            size: { width: bounds.width, height: bounds.height },
            data: { label: group.name, groupId },
            isContainer: true,
            children: nodeIds,
            childLayout: 'free'
        });

        // Update all nodes to be children of group
        for (const nodeId of nodeIds) {
            this.updateNode(nodeId, {
                parentId: groupId,
                data: { groupId }
            });
        }

        return group;
    }

    /**
     * Ungroup - remove group and restore children to canvas
     */
    ungroup(groupId: string): void {
        const group = this.groups.get(groupId);
        if (!group) return;

        // Restore all nodes to root
        for (const nodeId of group.nodeIds) {
            this.updateNode(nodeId, {
                parentId: undefined,
                data: { groupId: undefined }
            });
        }

        this.groups.delete(groupId);
    }

    /**
     * Add node to group
     */
    addToGroup(nodeId: string, groupId: string): void {
        const group = this.groups.get(groupId);
        if (!group) return;

        if (!group.nodeIds.includes(nodeId)) {
            group.nodeIds.push(nodeId);
            this.groups.set(groupId, group);

            this.updateNode(nodeId, {
                parentId: groupId,
                data: { groupId }
            });
        }
    }

    /**
     * Remove node from group
     */
    removeFromGroup(nodeId: string): void {
        for (const [groupId, group] of this.groups.entries()) {
            if (group.nodeIds.includes(nodeId)) {
                group.nodeIds = group.nodeIds.filter(id => id !== nodeId);
                this.groups.set(groupId, group);

                this.updateNode(nodeId, {
                    parentId: undefined,
                    data: { groupId: undefined }
                });
                break;
            }
        }
    }

    /**
     * Toggle group collapsed state
     */
    toggleCollapse(groupId: string): void {
        const group = this.groups.get(groupId);
        if (!group) return;

        group.collapsed = !group.collapsed;
        this.groups.set(groupId, group);

        // Show/hide children
        for (const nodeId of group.nodeIds) {
            this.updateNode(nodeId, {
                data: { visible: !group.collapsed }
            });
        }
    }

    private calculateGroupBounds(nodeIds: string[]): Bounds {
        const nodes = nodeIds.map(id => this.getNode(id)).filter(Boolean) as HierarchicalNode[];

        if (nodes.length === 0) {
            return { x: 0, y: 0, width: 0, height: 0 };
        }

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const node of nodes) {
            minX = Math.min(minX, node.position.x);
            minY = Math.min(minY, node.position.y);
            maxX = Math.max(maxX, node.position.x + node.size.width);
            maxY = Math.max(maxY, node.position.y + node.size.height);
        }

        return {
            x: minX - 20,  // Add padding
            y: minY - 20,
            width: maxX - minX + 40,
            height: maxY - minY + 40
        };
    }
}

// ============================================================================
// CONNECTION SYSTEM
// ============================================================================

export type ConnectionType =
    | 'flow'        // Data flow
    | 'dependency'  // Task dependency
    | 'reference'   // Reference link
    | 'inheritance' // Class inheritance
    | 'composition' // Component composition
    | 'association' // General association
    | 'aggregation';// Aggregation relationship

export type ConnectionStyle = 'straight' | 'bezier' | 'step' | 'smart';

export interface Connection {
    id: string;
    source: string;
    target: string;
    sourceHandle?: string;
    targetHandle?: string;
    type: ConnectionType;
    label?: string;
    style: ConnectionStyle;
    animated?: boolean;
    bidirectional?: boolean;
    data?: Record<string, unknown>;
}

export class ConnectionManager {
    private connections: Map<string, Connection> = new Map();

    constructor() { }

    /**
     * Create connection between nodes
     */
    createConnection(
        source: string,
        target: string,
        type: ConnectionType = 'flow',
        options?: Partial<Connection>
    ): Connection {
        const connectionId = `conn_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;

        const connection: Connection = {
            id: connectionId,
            source,
            target,
            type,
            style: 'smart',
            ...options
        };

        this.connections.set(connectionId, connection);
        return connection;
    }

    /**
     * Get all connections
     */
    getConnections(): Connection[] {
        return Array.from(this.connections.values());
    }

    /**
     * Get connections for a node
     */
    getNodeConnections(nodeId: string): Connection[] {
        return Array.from(this.connections.values()).filter(
            conn => conn.source === nodeId || conn.target === nodeId
        );
    }

    /**
     * Remove connection
     */
    removeConnection(connectionId: string): void {
        this.connections.delete(connectionId);
    }

    /**
     * Update connection
     */
    updateConnection(connectionId: string, updates: Partial<Connection>): void {
        const connection = this.connections.get(connectionId);
        if (!connection) return;

        Object.assign(connection, updates);
        this.connections.set(connectionId, connection);
    }

    /**
     * Calculate connection path
     */
    calculatePath(connection: Connection, sourcePos: Point, targetPos: Point): string {
        switch (connection.style) {
            case 'straight':
                return this.straightPath(sourcePos, targetPos);
            case 'bezier':
                return this.bezierPath(sourcePos, targetPos);
            case 'step':
                return this.stepPath(sourcePos, targetPos);
            case 'smart':
            default:
                return this.smartPath(sourcePos, targetPos);
        }
    }

    private straightPath(source: Point, target: Point): string {
        return `M ${source.x} ${source.y} L ${target.x} ${target.y}`;
    }

    private bezierPath(source: Point, target: Point): string {
        const dx = target.x - source.x;
        const dy = target.y - source.y;

        const controlPoint1 = {
            x: source.x + dx * 0.5,
            y: source.y
        };

        const controlPoint2 = {
            x: source.x + dx * 0.5,
            y: target.y
        };

        return `M ${source.x} ${source.y} C ${controlPoint1.x} ${controlPoint1.y}, ${controlPoint2.x} ${controlPoint2.y}, ${target.x} ${target.y}`;
    }

    private stepPath(source: Point, target: Point): string {
        const midX = (source.x + target.x) / 2;
        return `M ${source.x} ${source.y} L ${midX} ${source.y} L ${midX} ${target.y} L ${target.x} ${target.y}`;
    }

    private smartPath(source: Point, target: Point): string {
        // Use step for now, would implement A* pathfinding for obstacle avoidance
        return this.stepPath(source, target);
    }
}
