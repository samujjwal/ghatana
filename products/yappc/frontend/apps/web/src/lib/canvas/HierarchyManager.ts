/**
 * HierarchyManager - Hierarchical Node System
 * 
 * @doc.type class
 * @doc.purpose Manages infinite parent-child node relationships
 * @doc.layer core
 * @doc.pattern Manager
 */

export interface Padding {
    top: number;
    right: number;
    bottom: number;
    left: number;
}

export interface HierarchicalNode {
    id: string;
    type: string;
    position: { x: number; y: number };
    size: { width: number; height: number };
    data: Record<string, unknown>;

    // Hierarchy
    parentId?: string;              // null = root level
    children: string[];             // Child node IDs
    childrenVisible: boolean;       // Collapsed/expanded
    depth: number;                  // 0 = root, 1 = child, etc.
    path: string[];                 // [rootId, parentId, thisId]

    // Container properties
    isContainer: boolean;           // Can contain children
    containerType?: 'group' | 'frame' | 'artifact';
    autoResize: boolean;            // Resize to fit children
    padding: Padding;               // Space around children

    // Child layout
    childLayout: 'free' | 'vertical' | 'horizontal' | 'grid';
    childSpacing: number;
    childAlignment: 'start' | 'center' | 'end';

    // Zoom visibility
    minZoom?: number;               // Hide below this
    maxZoom?: number;               // Hide above this
    childrenMinZoom?: number;       // Children visibility threshold
}

export interface BreadcrumbItem {
    id: string;
    label: string;
    onClick: () => void;
}

type HierarchyEventType = 'hierarchy:changed' | 'node:added' | 'node:removed' | 'node:moved';

export class HierarchyManager {
    private nodes: Map<string, HierarchicalNode> = new Map();
    private listeners: Map<HierarchyEventType, Array<(data: unknown) => void>> = new Map();

    constructor(
        private onNodeUpdate?: (nodeId: string, node: HierarchicalNode) => void,
        private onNodesUpdate?: (nodes: HierarchicalNode[]) => void
    ) { }

    /**
     * Initialize with nodes
     */
    setNodes(nodes: HierarchicalNode[]): void {
        this.nodes.clear();
        for (const node of nodes) {
            this.nodes.set(node.id, node);
        }
    }

    /**
     * Get node by ID
     */
    getNode(nodeId: string): HierarchicalNode | undefined {
        return this.nodes.get(nodeId);
    }

    /**
     * Get all nodes
     */
    getAllNodes(): HierarchicalNode[] {
        return Array.from(this.nodes.values());
    }

    /**
     * Get root nodes (no parent)
     */
    getRootNodes(): HierarchicalNode[] {
        return this.getAllNodes().filter(node => !node.parentId);
    }

    /**
     * Get children of a node
     */
    getChildren(nodeId: string): HierarchicalNode[] {
        const node = this.getNode(nodeId);
        if (!node) return [];

        return node.children
            .map(childId => this.getNode(childId))
            .filter((child): child is HierarchicalNode => child !== undefined);
    }

    /**
     * Get parent of a node
     */
    getParent(nodeId: string): HierarchicalNode | undefined {
        const node = this.getNode(nodeId);
        if (!node || !node.parentId) return undefined;

        return this.getNode(node.parentId);
    }

    /**
     * Get all ancestors (parent, grandparent, etc.)
     */
    getAncestors(nodeId: string): HierarchicalNode[] {
        const ancestors: HierarchicalNode[] = [];
        let current = this.getNode(nodeId);

        while (current?.parentId) {
            const parent = this.getNode(current.parentId);
            if (!parent) break;
            ancestors.push(parent);
            current = parent;
        }

        return ancestors;
    }

    /**
     * Get all descendants (children, grandchildren, etc.)
     */
    getDescendants(nodeId: string): HierarchicalNode[] {
        const node = this.getNode(nodeId);
        if (!node) return [];

        const descendants: HierarchicalNode[] = [];

        const collect = (n: HierarchicalNode) => {
            for (const childId of n.children) {
                const child = this.getNode(childId);
                if (child) {
                    descendants.push(child);
                    collect(child);  // Recursive
                }
            }
        };

        collect(node);
        return descendants;
    }

    /**
     * Add child to parent
     */
    addChild(parentId: string, childNode: Partial<HierarchicalNode>): HierarchicalNode {
        const parent = this.getNode(parentId);
        if (!parent) {
            throw new Error(`Parent node ${parentId} not found`);
        }

        if (!parent.isContainer) {
            throw new Error(`Node ${parentId} is not a container`);
        }

        // Create full node with defaults
        const newNode: HierarchicalNode = {
            id: childNode.id || this.generateId(),
            type: childNode.type || 'default',
            position: childNode.position || { x: 0, y: 0 },
            size: childNode.size || { width: 100, height: 100 },
            data: childNode.data || {},
            parentId,
            children: [],
            childrenVisible: true,
            depth: parent.depth + 1,
            path: [...parent.path, childNode.id || this.generateId()],
            isContainer: childNode.isContainer ?? false,
            autoResize: childNode.autoResize ?? false,
            padding: childNode.padding || { top: 10, right: 10, bottom: 10, left: 10 },
            childLayout: childNode.childLayout || 'free',
            childSpacing: childNode.childSpacing ?? 10,
            childAlignment: childNode.childAlignment || 'start',
        };

        // Add to nodes map
        this.nodes.set(newNode.id, newNode);

        // Add to parent's children
        parent.children.push(newNode.id);
        this.nodes.set(parent.id, parent);

        // Position relative to parent
        newNode.position = this.calculateChildPosition(parent, newNode);

        // Auto-resize parent if enabled
        if (parent.autoResize) {
            this.resizeToFitChildren(parentId);
        }

        // Layout children if not free layout
        if (parent.childLayout !== 'free') {
            this.layoutChildren(parentId);
        }

        // Notify listeners
        this.emit('node:added', { node: newNode, parent });
        this.emit('hierarchy:changed', { type: 'add', nodeId: newNode.id });

        // Notify update callback
        if (this.onNodeUpdate) {
            this.onNodeUpdate(newNode.id, newNode);
            this.onNodeUpdate(parent.id, parent);
        }

        return newNode;
    }

    /**
     * Remove node (and its descendants)
     */
    removeNode(nodeId: string): void {
        const node = this.getNode(nodeId);
        if (!node) return;

        // Recursively remove descendants first
        const descendants = this.getDescendants(nodeId);
        for (const descendant of descendants) {
            this.nodes.delete(descendant.id);
        }

        // Remove from parent's children
        if (node.parentId) {
            const parent = this.getNode(node.parentId);
            if (parent) {
                parent.children = parent.children.filter(id => id !== nodeId);

                // Auto-resize parent if enabled
                if (parent.autoResize) {
                    this.resizeToFitChildren(node.parentId);
                }

                this.nodes.set(parent.id, parent);

                if (this.onNodeUpdate) {
                    this.onNodeUpdate(parent.id, parent);
                }
            }
        }

        // Remove node
        this.nodes.delete(nodeId);

        // Notify listeners
        this.emit('node:removed', { nodeId, descendants });
        this.emit('hierarchy:changed', { type: 'remove', nodeId });
    }

    /**
     * Get global position of a node (recursive)
     */
    private getGlobalPosition(nodeId: string): { x: number; y: number } {
        let x = 0;
        let y = 0;
        let current = this.getNode(nodeId);

        while (current) {
            x += current.position.x;
            y += current.position.y;

            if (current.parentId) {
                const parent = this.getNode(current.parentId);
                if (parent) {
                    x += parent.padding.left;
                    y += parent.padding.top;
                    current = parent;
                } else {
                    break;
                }
            } else {
                break;
            }
        }
        return { x, y };
    }

    /**
     * Move node to different parent (reparent)
     */
    reparent(nodeId: string, newParentId: string): void {
        const node = this.getNode(nodeId);
        const newParent = this.getNode(newParentId);

        if (!node || !newParent) {
            throw new Error('Node or new parent not found');
        }

        if (!newParent.isContainer) {
            throw new Error(`Node ${newParentId} is not a container`);
        }

        // Check for circular reference
        if (this.wouldCreateCircularReference(nodeId, newParentId)) {
            throw new Error('Cannot reparent: would create circular reference');
        }

        // Calculate global position ensuring we maintain visual position
        const globalPos = this.getGlobalPosition(nodeId);
        const parentGlobal = this.getGlobalPosition(newParentId);

        // Remove from old parent
        if (node.parentId) {
            const oldParent = this.getNode(node.parentId);
            if (oldParent) {
                oldParent.children = oldParent.children.filter(id => id !== nodeId);
                this.nodes.set(oldParent.id, oldParent);

                if (this.onNodeUpdate) {
                    this.onNodeUpdate(oldParent.id, oldParent);
                }
            }
        }

        // Update node
        node.parentId = newParentId;
        node.depth = newParent.depth + 1;
        node.path = [...newParent.path, node.id];

        // Update position to be relative to new parent (preserving global position)
        node.position = {
            x: globalPos.x - parentGlobal.x - newParent.padding.left,
            y: globalPos.y - parentGlobal.y - newParent.padding.top
        };

        // Update all descendants' depth and path
        this.updateDescendantsHierarchy(node);

        // Add to new parent
        newParent.children.push(nodeId);
        this.nodes.set(newParent.id, newParent);
        this.nodes.set(node.id, node);

        // Auto-resize if enabled
        if (newParent.autoResize) {
            this.resizeToFitChildren(newParentId);
        }

        // Layout children if not free
        if (newParent.childLayout !== 'free') {
            this.layoutChildren(newParentId);
        }

        // Notify listeners
        this.emit('node:moved', { nodeId, oldParentId: node.parentId, newParentId });
        this.emit('hierarchy:changed', { type: 'move', nodeId });

        if (this.onNodeUpdate) {
            this.onNodeUpdate(nodeId, node);
            this.onNodeUpdate(newParentId, newParent);
        }
    }

    /**
     * Toggle children visibility (collapse/expand)
     */
    toggleChildrenVisibility(nodeId: string): void {
        const node = this.getNode(nodeId);
        if (!node || node.children.length === 0) return;

        node.childrenVisible = !node.childrenVisible;
        this.nodes.set(node.id, node);

        if (this.onNodeUpdate) {
            this.onNodeUpdate(node.id, node);
        }

        this.emit('hierarchy:changed', { type: 'toggle', nodeId });
    }

    /**
     * Get breadcrumb trail for a node
     */
    getBreadcrumb(nodeId: string): BreadcrumbItem[] {
        const node = this.getNode(nodeId);
        if (!node) return [];

        return node.path.map(id => {
            const pathNode = this.getNode(id);
            return {
                id,
                label: pathNode?.data.label || 'Untitled',
                onClick: () => {
                    // Callback would zoom to this node
                }
            };
        });
    }

    /**
     * Layout children based on container's layout setting
     */
    layoutChildren(containerId: string): void {
        const container = this.getNode(containerId);
        if (!container) return;

        const children = this.getChildren(containerId);
        if (children.length === 0) return;

        switch (container.childLayout) {
            case 'vertical':
                this.layoutVertical(container, children);
                break;
            case 'horizontal':
                this.layoutHorizontal(container, children);
                break;
            case 'grid':
                this.layoutGrid(container, children);
                break;
            case 'free':
            default:
                // No automatic layout, preserve positions
                break;
        }

        // Update all children
        for (const child of children) {
            this.nodes.set(child.id, child);
            if (this.onNodeUpdate) {
                this.onNodeUpdate(child.id, child);
            }
        }
    }

    /**
     * Resize container to fit children
     */
    resizeToFitChildren(containerId: string): void {
        const container = this.getNode(containerId);
        if (!container) return;

        const children = this.getChildren(containerId);
        if (children.length === 0) return;

        // Calculate bounding box of all children
        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const child of children) {
            minX = Math.min(minX, child.position.x);
            minY = Math.min(minY, child.position.y);
            maxX = Math.max(maxX, child.position.x + child.size.width);
            maxY = Math.max(maxY, child.position.y + child.size.height);
        }

        // Add padding
        container.size.width = maxX - minX + container.padding.left + container.padding.right;
        container.size.height = maxY - minY + container.padding.top + container.padding.bottom;

        this.nodes.set(container.id, container);

        if (this.onNodeUpdate) {
            this.onNodeUpdate(container.id, container);
        }
    }

    /**
     * Add event listener
     */
    on(event: HierarchyEventType, callback: (data: unknown) => void): () => void {
        if (!this.listeners.has(event)) {
            this.listeners.set(event, []);
        }

        this.listeners.get(event)!.push(callback);

        // Return unsubscribe function
        return () => {
            const callbacks = this.listeners.get(event);
            if (callbacks) {
                const index = callbacks.indexOf(callback);
                if (index > -1) {
                    callbacks.splice(index, 1);
                }
            }
        };
    }

    // Private helper methods

    private emit(event: HierarchyEventType, data: unknown): void {
        const callbacks = this.listeners.get(event);
        if (callbacks) {
            for (const callback of callbacks) {
                callback(data);
            }
        }
    }

    private generateId(): string {
        return `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`;
    }

    private calculateChildPosition(
        parent: HierarchicalNode,
        child: HierarchicalNode
    ): { x: number; y: number } {
        // Default position: offset from parent with padding
        return {
            x: parent.position.x + parent.padding.left,
            y: parent.position.y + parent.padding.top
        };
    }

    private wouldCreateCircularReference(nodeId: string, newParentId: string): boolean {
        // Check if newParent is a descendant of node
        const descendants = this.getDescendants(nodeId);
        return descendants.some(d => d.id === newParentId);
    }

    private updateDescendantsHierarchy(node: HierarchicalNode): void {
        const descendants = this.getDescendants(node.id);

        for (const descendant of descendants) {
            const parent = this.getNode(descendant.parentId!);
            if (parent) {
                descendant.depth = parent.depth + 1;
                descendant.path = [...parent.path, descendant.id];
                this.nodes.set(descendant.id, descendant);
            }
        }
    }

    private layoutVertical(container: HierarchicalNode, children: HierarchicalNode[]): void {
        let currentY = container.position.y + container.padding.top;

        for (const child of children) {
            child.position.x = container.position.x + container.padding.left;
            child.position.y = currentY;

            currentY += child.size.height + container.childSpacing;
        }
    }

    private layoutHorizontal(container: HierarchicalNode, children: HierarchicalNode[]): void {
        let currentX = container.position.x + container.padding.left;

        for (const child of children) {
            child.position.x = currentX;
            child.position.y = container.position.y + container.padding.top;

            currentX += child.size.width + container.childSpacing;
        }
    }

    private layoutGrid(container: HierarchicalNode, children: HierarchicalNode[]): void {
        const columns = (container.data.gridColumns as number) || 3;
        const cellWidth =
            (container.size.width - container.padding.left - container.padding.right -
                (columns - 1) * container.childSpacing) / columns;

        let row = 0;
        let col = 0;

        for (const child of children) {
            child.position.x = container.position.x + container.padding.left +
                col * (cellWidth + container.childSpacing);
            child.position.y = container.position.y + container.padding.top +
                row * (child.size.height + container.childSpacing);

            col++;
            if (col >= columns) {
                col = 0;
                row++;
            }
        }
    }
}
