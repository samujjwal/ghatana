/**
 * Alignment & Distribution Engine
 * 
 * @doc.type class
 * @doc.purpose Align and distribute nodes on canvas
 * @doc.layer core
 * @doc.pattern Service
 */

import type { HierarchicalNode } from './HierarchyManager';

export type AlignmentType =
    | 'left'
    | 'centerX'
    | 'right'
    | 'top'
    | 'centerY'
    | 'bottom';

export type DistributionAxis = 'horizontal' | 'vertical';

export class AlignmentEngine {
    constructor(
        private getNode: (id: string) => HierarchicalNode | undefined,
        private updateNode: (id: string, updates: Partial<HierarchicalNode>) => void,
        private onNodesChanged?: (nodeIds: string[]) => void
    ) { }

    /**
     * Align multiple nodes
     */
    align(nodeIds: string[], alignment: AlignmentType): void {
        const nodes = nodeIds
            .map(id => this.getNode(id))
            .filter((node): node is HierarchicalNode => node !== undefined);

        if (nodes.length < 2) return;

        switch (alignment) {
            case 'left':
                this.alignLeft(nodes);
                break;
            case 'centerX':
                this.alignCenterX(nodes);
                break;
            case 'right':
                this.alignRight(nodes);
                break;
            case 'top':
                this.alignTop(nodes);
                break;
            case 'centerY':
                this.alignCenterY(nodes);
                break;
            case 'bottom':
                this.alignBottom(nodes);
                break;
        }

        if (this.onNodesChanged) {
            this.onNodesChanged(nodeIds);
        }
    }

    /**
     * Distribute nodes evenly
     */
    distribute(nodeIds: string[], axis: DistributionAxis): void {
        const nodes = nodeIds
            .map(id => this.getNode(id))
            .filter((node): node is HierarchicalNode => node !== undefined);

        if (nodes.length < 3) return;  // Need at least 3 nodes

        // Sort by position
        nodes.sort((a, b) => {
            if (axis === 'horizontal') {
                return a.position.x - b.position.x;
            } else {
                return a.position.y - b.position.y;
            }
        });

        const first = nodes[0];
        const last = nodes[nodes.length - 1];

        if (axis === 'horizontal') {
            this.distributeHorizontal(nodes, first, last);
        } else {
            this.distributeVertical(nodes, first, last);
        }

        if (this.onNodesChanged) {
            this.onNodesChanged(nodeIds);
        }
    }

    /**
     * Distribute spacing equally
     */
    distributeSpacing(nodeIds: string[], axis: DistributionAxis, spacing: number): void {
        const nodes = nodeIds
            .map(id => this.getNode(id))
            .filter((node): node is HierarchicalNode => node !== undefined);

        if (nodes.length < 2) return;

        // Sort by position
        nodes.sort((a, b) => {
            if (axis === 'horizontal') {
                return a.position.x - b.position.x;
            } else {
                return a.position.y - b.position.y;
            }
        });

        if (axis === 'horizontal') {
            let currentX = nodes[0].position.x;
            for (const node of nodes) {
                this.updateNode(node.id, {
                    position: { x: currentX, y: node.position.y }
                });
                currentX += node.size.width + spacing;
            }
        } else {
            let currentY = nodes[0].position.y;
            for (const node of nodes) {
                this.updateNode(node.id, {
                    position: { x: node.position.x, y: currentY }
                });
                currentY += node.size.height + spacing;
            }
        }

        if (this.onNodesChanged) {
            this.onNodesChanged(nodeIds);
        }
    }

    /**
     * Tidy up - auto-organize nodes
     */
    tidyUp(nodeIds: string[]): void {
        // Simplified auto-organize
        // In production, would use force-directed layout algorithm
        const nodes = nodeIds
            .map(id => this.getNode(id))
            .filter((node): node is HierarchicalNode => node !== undefined);

        if (nodes.length === 0) return;

        // Sort nodes
        nodes.sort((a, b) => {
            const aCenter = a.position.y + a.size.height / 2;
            const bCenter = b.position.y + b.size.height / 2;
            return aCenter - bCenter;
        });

        // Arrange in grid
        const columns = Math.ceil(Math.sqrt(nodes.length));
        const spacing = 50;
        let row = 0;
        let col = 0;

        const startX = nodes[0].position.x;
        const startY = nodes[0].position.y;

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: {
                    x: startX + col * (200 + spacing),
                    y: startY + row * (150 + spacing)
                }
            });

            col++;
            if (col >= columns) {
                col = 0;
                row++;
            }
        }

        if (this.onNodesChanged) {
            this.onNodesChanged(nodeIds);
        }
    }

    /**
     * Match size (width, height, or both)
     */
    matchSize(nodeIds: string[], dimension: 'width' | 'height' | 'both'): void {
        const nodes = nodeIds
            .map(id => this.getNode(id))
            .filter((node): node is HierarchicalNode => node !== undefined);

        if (nodes.length < 2) return;

        const reference = nodes[0];

        for (let i = 1; i < nodes.length; i++) {
            const node = nodes[i];
            const updates: Partial<HierarchicalNode> = {};

            if (dimension === 'width' || dimension === 'both') {
                updates.size = {
                    ...node.size,
                    width: reference.size.width
                };
            }

            if (dimension === 'height' || dimension === 'both') {
                updates.size = {
                    ...(updates.size || node.size),
                    height: reference.size.height
                };
            }

            this.updateNode(node.id, updates);
        }

        if (this.onNodesChanged) {
            this.onNodesChanged(nodeIds);
        }
    }

    // Private alignment methods

    private alignLeft(nodes: HierarchicalNode[]): void {
        const minX = Math.min(...nodes.map(n => n.position.x));

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: minX, y: node.position.y }
            });
        }
    }

    private alignCenterX(nodes: HierarchicalNode[]): void {
        const avgX = nodes.reduce(
            (sum, n) => sum + n.position.x + n.size.width / 2,
            0
        ) / nodes.length;

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: avgX - node.size.width / 2, y: node.position.y }
            });
        }
    }

    private alignRight(nodes: HierarchicalNode[]): void {
        const maxRight = Math.max(...nodes.map(n => n.position.x + n.size.width));

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: maxRight - node.size.width, y: node.position.y }
            });
        }
    }

    private alignTop(nodes: HierarchicalNode[]): void {
        const minY = Math.min(...nodes.map(n => n.position.y));

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: node.position.x, y: minY }
            });
        }
    }

    private alignCenterY(nodes: HierarchicalNode[]): void {
        const avgY = nodes.reduce(
            (sum, n) => sum + n.position.y + n.size.height / 2,
            0
        ) / nodes.length;

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: node.position.x, y: avgY - node.size.height / 2 }
            });
        }
    }

    private alignBottom(nodes: HierarchicalNode[]): void {
        const maxBottom = Math.max(...nodes.map(n => n.position.y + n.size.height));

        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: node.position.x, y: maxBottom - node.size.height }
            });
        }
    }

    private distributeHorizontal(
        nodes: HierarchicalNode[],
        first: HierarchicalNode,
        last: HierarchicalNode
    ): void {
        const totalWidth = nodes.reduce((sum, n) => sum + n.size.width, 0);
        const availableSpace = (last.position.x + last.size.width) - first.position.x - totalWidth;
        const spacing = availableSpace / (nodes.length - 1);

        let currentX = first.position.x;
        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: currentX, y: node.position.y }
            });
            currentX += node.size.width + spacing;
        }
    }

    private distributeVertical(
        nodes: HierarchicalNode[],
        first: HierarchicalNode,
        last: HierarchicalNode
    ): void {
        const totalHeight = nodes.reduce((sum, n) => sum + n.size.height, 0);
        const availableSpace = (last.position.y + last.size.height) - first.position.y - totalHeight;
        const spacing = availableSpace / (nodes.length - 1);

        let currentY = first.position.y;
        for (const node of nodes) {
            this.updateNode(node.id, {
                position: { x: node.position.x, y: currentY }
            });
            currentY += node.size.height + spacing;
        }
    }
}

/**
 * Smart Guides System
 * Shows alignment guides while dragging
 */

export interface Guide {
    id: string;
    type: 'vertical' | 'horizontal';
    position: number;
    nodes: string[];  // Node IDs that match this guide
}

export class SmartGuidesSystem {
    private guides: Guide[] = [];
    private snapThreshold: number = 5;  // pixels

    constructor(
        private getAllNodes: () => HierarchicalNode[]
    ) { }

    /**
     * Get guides for current drag operation
     */
    getGuidesForNode(draggedNode: HierarchicalNode, excludeIds: string[] = []): Guide[] {
        const allNodes = this.getAllNodes().filter(n => !excludeIds.includes(n.id));
        const guides: Guide[] = [];

        // Left edge guides
        const leftEdges = allNodes.map(n => n.position.x);
        guides.push(...this.createGuidesFromPositions(leftEdges, 'vertical', allNodes, 'left'));

        // Right edge guides
        const rightEdges = allNodes.map(n => n.position.x + n.size.width);
        guides.push(...this.createGuidesFromPositions(rightEdges, 'vertical', allNodes, 'right'));

        // Center X guides
        const centerXs = allNodes.map(n => n.position.x + n.size.width / 2);
        guides.push(...this.createGuidesFromPositions(centerXs, 'vertical', allNodes, 'centerX'));

        // Top edge guides
        const topEdges = allNodes.map(n => n.position.y);
        guides.push(...this.createGuidesFromPositions(topEdges, 'horizontal', allNodes, 'top'));

        // Bottom edge guides
        const bottomEdges = allNodes.map(n => n.position.y + n.size.height);
        guides.push(...this.createGuidesFromPositions(bottomEdges, 'horizontal', allNodes, 'bottom'));

        // Center Y guides
        const centerYs = allNodes.map(n => n.position.y + n.size.height / 2);
        guides.push(...this.createGuidesFromPositions(centerYs, 'horizontal', allNodes, 'centerY'));

        return guides;
    }

    /**
     * Snap position to nearest guide
     */
    snapToGuides(
        position: { x: number; y: number },
        size: { width: number; height: number },
        guides: Guide[]
    ): { x: number; y: number; snappedGuides: Guide[] } {
        let newX = position.x;
        let newY = position.y;
        const snappedGuides: Guide[] = [];

        // Check vertical guides (X position)
        for (const guide of guides.filter(g => g.type === 'vertical')) {
            // Check left edge
            if (Math.abs(position.x - guide.position) < this.snapThreshold) {
                newX = guide.position;
                snappedGuides.push(guide);
            }
            // Check right edge
            else if (Math.abs(position.x + size.width - guide.position) < this.snapThreshold) {
                newX = guide.position - size.width;
                snappedGuides.push(guide);
            }
            // Check center
            else if (Math.abs(position.x + size.width / 2 - guide.position) < this.snapThreshold) {
                newX = guide.position - size.width / 2;
                snappedGuides.push(guide);
            }
        }

        // Check horizontal guides (Y position)
        for (const guide of guides.filter(g => g.type === 'horizontal')) {
            // Check top edge
            if (Math.abs(position.y - guide.position) < this.snapThreshold) {
                newY = guide.position;
                snappedGuides.push(guide);
            }
            // Check bottom edge
            else if (Math.abs(position.y + size.height - guide.position) < this.snapThreshold) {
                newY = guide.position - size.height;
                snappedGuides.push(guide);
            }
            // Check center
            else if (Math.abs(position.y + size.height / 2 - guide.position) < this.snapThreshold) {
                newY = guide.position - size.height / 2;
                snappedGuides.push(guide);
            }
        }

        return { x: newX, y: newY, snappedGuides };
    }

    private createGuidesFromPositions(
        positions: number[],
        type: 'vertical' | 'horizontal',
        nodes: HierarchicalNode[],
        alignment: string
    ): Guide[] {
        const guides: Guide[] = [];
        const uniquePositions = Array.from(new Set(positions));

        for (const position of uniquePositions) {
            guides.push({
                id: `${type}-${alignment}-${position}`,
                type,
                position,
                nodes: nodes
                    .filter(n => this.nodeMatchesGuide(n, position, type, alignment))
                    .map(n => n.id)
            });
        }

        return guides;
    }

    private nodeMatchesGuide(
        node: HierarchicalNode,
        position: number,
        type: 'vertical' | 'horizontal',
        alignment: string
    ): boolean {
        if (type === 'vertical') {
            switch (alignment) {
                case 'left':
                    return node.position.x === position;
                case 'right':
                    return node.position.x + node.size.width === position;
                case 'centerX':
                    return node.position.x + node.size.width / 2 === position;
            }
        } else {
            switch (alignment) {
                case 'top':
                    return node.position.y === position;
                case 'bottom':
                    return node.position.y + node.size.height === position;
                case 'centerY':
                    return node.position.y + node.size.height / 2 === position;
            }
        }
        return false;
    }
}
