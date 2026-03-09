/**
 * Viewport Culling System
 * 
 * Optimizes rendering by only processing visible elements within viewport
 * 
 * @doc.type module
 * @doc.purpose Performance optimization for infinite canvas
 * @doc.layer lib
 * @doc.pattern Utility
 */

import type { HierarchicalNode } from './HierarchyManager';

export interface Viewport {
    x: number;
    y: number;
    zoom: number;
    width: number;
    height: number;
}

export interface Bounds {
    x: number;
    y: number;
    width: number;
    height: number;
}

/**
 * Check if a node is within the viewport with margin for smooth transitions
 */
export function isNodeInViewport(
    node: HierarchicalNode,
    viewport: Viewport,
    margin: number = 200
): boolean {
    // Safety check for missing position
    if (!node.position || typeof node.position.x !== 'number' || typeof node.position.y !== 'number') {
        return false;
    }
    if (!node.size || typeof node.size.width !== 'number' || typeof node.size.height !== 'number') {
        return false;
    }

    // ReactFlow viewport: viewport.x and viewport.y are the pan offset
    // To convert screen to canvas: canvasX = (screenX - viewport.x) / viewport.zoom
    // To convert canvas to screen: screenX = canvasX * viewport.zoom + viewport.x

    // Calculate viewport bounds in canvas coordinates
    // Top-left of viewport in canvas coords
    const viewportLeft = (-viewport.x / viewport.zoom) - margin;
    const viewportTop = (-viewport.y / viewport.zoom) - margin;
    // Bottom-right of viewport in canvas coords
    const viewportRight = viewportLeft + (viewport.width / viewport.zoom) + (margin * 2);
    const viewportBottom = viewportTop + (viewport.height / viewport.zoom) + (margin * 2);

    // Calculate node bounds
    const nodeRight = node.position.x + node.size.width;
    const nodeBottom = node.position.y + node.size.height;

    // Check intersection using AABB (Axis-Aligned Bounding Box) collision
    const isVisible = !(
        node.position.x > viewportRight ||
        nodeRight < viewportLeft ||
        node.position.y > viewportBottom ||
        nodeBottom < viewportTop
    );

    return isVisible;
}

/**
 * Filter nodes to only those visible in viewport
 */
export function getVisibleNodes(
    nodes: HierarchicalNode[],
    viewport: Viewport,
    margin: number = 200
): HierarchicalNode[] {
    return nodes.filter(node => isNodeInViewport(node, viewport, margin));
}

/**
 * Calculate optimal rendering chunks for large canvases
 */
export function calculateRenderChunks(
    nodes: HierarchicalNode[],
    viewport: Viewport,
    chunkSize: number = 1000
): HierarchicalNode[][] {
    const visibleNodes = getVisibleNodes(nodes, viewport);
    const chunks: HierarchicalNode[][] = [];

    for (let i = 0; i < visibleNodes.length; i += chunkSize) {
        chunks.push(visibleNodes.slice(i, i + chunkSize));
    }

    return chunks;
}

/**
 * Spatial indexing using a simple grid-based quadtree alternative
 * For very large canvases (1000+ nodes), use this for O(1) spatial queries
 */
export class SpatialIndex {
    private gridSize: number;
    private grid: Map<string, HierarchicalNode[]>;

    constructor(gridSize: number = 500) {
        this.gridSize = gridSize;
        this.grid = new Map();
    }

    /**
     * Get grid cell key for a position
     */
    private getCellKey(x: number, y: number): string {
        const cellX = Math.floor(x / this.gridSize);
        const cellY = Math.floor(y / this.gridSize);
        return `${cellX},${cellY}`;
    }

    /**
     * Get all cell keys that a node occupies
     */
    private getNodeCells(node: HierarchicalNode): string[] {
        const cells: string[] = [];

        // Safety check for missing position
        if (!node.position || typeof node.position.x !== 'number' || typeof node.position.y !== 'number') {
            return cells;
        }
        if (!node.size || typeof node.size.width !== 'number' || typeof node.size.height !== 'number') {
            return cells;
        }

        const startX = Math.floor(node.position.x / this.gridSize);
        const startY = Math.floor(node.position.y / this.gridSize);
        const endX = Math.floor((node.position.x + node.size.width) / this.gridSize);
        const endY = Math.floor((node.position.y + node.size.height) / this.gridSize);

        for (let x = startX; x <= endX; x++) {
            for (let y = startY; y <= endY; y++) {
                cells.push(`${x},${y}`);
            }
        }

        return cells;
    }

    /**
     * Index nodes into spatial grid
     */
    public indexNodes(nodes: HierarchicalNode[]): void {
        this.grid.clear();

        for (const node of nodes) {
            const cells = this.getNodeCells(node);
            for (const cell of cells) {
                if (!this.grid.has(cell)) {
                    this.grid.set(cell, []);
                }
                this.grid.get(cell)!.push(node);
            }
        }
    }

    /**
     * Query nodes in viewport using spatial index
     */
    public queryViewport(viewport: Viewport, margin: number = 200): HierarchicalNode[] {
        const viewportLeft = -viewport.x / viewport.zoom - margin;
        const viewportTop = -viewport.y / viewport.zoom - margin;
        const viewportRight = viewportLeft + (viewport.width / viewport.zoom) + (margin * 2);
        const viewportBottom = viewportTop + (viewport.height / viewport.zoom) + (margin * 2);

        const startCellX = Math.floor(viewportLeft / this.gridSize);
        const startCellY = Math.floor(viewportTop / this.gridSize);
        const endCellX = Math.floor(viewportRight / this.gridSize);
        const endCellY = Math.floor(viewportBottom / this.gridSize);

        const nodesSet = new Set<HierarchicalNode>();

        for (let x = startCellX; x <= endCellX; x++) {
            for (let y = startCellY; y <= endCellY; y++) {
                const cell = this.grid.get(`${x},${y}`);
                if (cell) {
                    cell.forEach(node => nodesSet.add(node));
                }
            }
        }

        // Filter to exact viewport bounds
        return Array.from(nodesSet).filter(node =>
            isNodeInViewport(node, viewport, margin)
        );
    }

    /**
     * Query nodes at a specific point
     */
    public queryPoint(x: number, y: number): HierarchicalNode[] {
        const cell = this.grid.get(this.getCellKey(x, y));
        if (!cell) return [];

        return cell.filter(node =>
            x >= node.position.x &&
            x <= node.position.x + node.size.width &&
            y >= node.position.y &&
            y <= node.position.y + node.size.height
        );
    }
}

/**
 * Performance metrics for viewport culling
 */
export class ViewportMetrics {
    private totalNodes: number = 0;
    private visibleNodes: number = 0;
    private renderTime: number = 0;

    public update(total: number, visible: number, renderTime: number): void {
        this.totalNodes = total;
        this.visibleNodes = visible;
        this.renderTime = renderTime;
    }

    public getCullingRatio(): number {
        if (this.totalNodes === 0) return 0;
        return (1 - this.visibleNodes / this.totalNodes) * 100;
    }

    public getMetrics() {
        return {
            totalNodes: this.totalNodes,
            visibleNodes: this.visibleNodes,
            culledNodes: this.totalNodes - this.visibleNodes,
            cullingRatio: this.getCullingRatio(),
            renderTime: this.renderTime
        };
    }
}
