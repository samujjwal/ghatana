/**
 * Mindmap Element - Hierarchical mind map visualization
 * 
 * @doc.type class
 * @doc.purpose Container element for mind map nodes with automatic layout
 * @doc.layer elements
 * @doc.pattern Element
 * 
 * Implements AFFiNE-style mind maps with:
 * - Hierarchical node structure
 * - Automatic layout (horizontal/vertical)
 * - Multiple visual styles
 * - Expandable/collapsible nodes
 */

import { BaseElementProps, CanvasElementType, Point } from "../types/index.js";
import { CanvasElement } from "./base.js";
import { Bound } from "../utils/bounds.js";
import { themeManager } from "../theme/index.js";
import { nanoid } from "nanoid";

/**
 * Mind map node definition
 */
export interface MindmapNode {
    /** Node ID */
    id: string;
    /** Node text content */
    text: string;
    /** Child nodes */
    children: MindmapNode[];
    /** Whether node is collapsed */
    collapsed?: boolean;
    /** Node position (calculated by layout) */
    xywh?: string;
    /** Custom node style */
    style?: MindmapNodeStyle;
}

/**
 * Mind map node style
 */
export interface MindmapNodeStyle {
    fillColor?: string;
    strokeColor?: string;
    textColor?: string;
    fontSize?: number;
    fontWeight?: string;
    radius?: number;
    padding?: [number, number];
}

/**
 * Mind map layout type
 */
export enum MindmapLayoutType {
    /** Nodes spread to the right */
    Right = 0,
    /** Nodes spread to the left */
    Left = 1,
    /** Nodes spread both directions */
    Balanced = 2,
}

/**
 * Mind map style preset
 */
export enum MindmapStyle {
    One = 1,
    Two = 2,
    Three = 3,
    Four = 4,
}

/**
 * Mind map element props
 */
export interface MindmapProps extends BaseElementProps {
    /** Root node and tree structure */
    tree?: MindmapNode;
    /** Layout direction */
    layoutType?: MindmapLayoutType;
    /** Visual style preset */
    style?: MindmapStyle;
}

/**
 * Style definitions for mind map presets
 */
const MINDMAP_STYLES: Record<MindmapStyle, {
    root: MindmapNodeStyle;
    node: MindmapNodeStyle;
    connector: { color: string; width: number };
}> = {
    [MindmapStyle.One]: {
        root: {
            fillColor: '#53b2ef',
            strokeColor: '#53b2ef',
            textColor: '#ffffff',
            fontSize: 18,
            fontWeight: '600',
            radius: 8,
            padding: [12, 24],
        },
        node: {
            fillColor: '#ffffff',
            strokeColor: '#d0d5dd',
            textColor: '#1f2937',
            fontSize: 14,
            fontWeight: '400',
            radius: 6,
            padding: [8, 16],
        },
        connector: { color: '#53b2ef', width: 2 },
    },
    [MindmapStyle.Two]: {
        root: {
            fillColor: '#7c3aed',
            strokeColor: '#7c3aed',
            textColor: '#ffffff',
            fontSize: 18,
            fontWeight: '600',
            radius: 24,
            padding: [12, 24],
        },
        node: {
            fillColor: '#f3f4f6',
            strokeColor: 'transparent',
            textColor: '#374151',
            fontSize: 14,
            fontWeight: '400',
            radius: 16,
            padding: [8, 16],
        },
        connector: { color: '#7c3aed', width: 2 },
    },
    [MindmapStyle.Three]: {
        root: {
            fillColor: '#059669',
            strokeColor: '#059669',
            textColor: '#ffffff',
            fontSize: 18,
            fontWeight: '600',
            radius: 0,
            padding: [12, 24],
        },
        node: {
            fillColor: 'transparent',
            strokeColor: 'transparent',
            textColor: '#1f2937',
            fontSize: 14,
            fontWeight: '400',
            radius: 0,
            padding: [4, 8],
        },
        connector: { color: '#059669', width: 2 },
    },
    [MindmapStyle.Four]: {
        root: {
            fillColor: '#f59e0b',
            strokeColor: '#f59e0b',
            textColor: '#ffffff',
            fontSize: 18,
            fontWeight: '600',
            radius: 4,
            padding: [12, 24],
        },
        node: {
            fillColor: '#fef3c7',
            strokeColor: '#f59e0b',
            textColor: '#92400e',
            fontSize: 14,
            fontWeight: '400',
            radius: 4,
            padding: [8, 16],
        },
        connector: { color: '#f59e0b', width: 2 },
    },
};

/**
 * Mindmap Element
 * 
 * Renders a hierarchical mind map with automatic layout.
 * Supports multiple visual styles and layout directions.
 */
export class MindmapElement extends CanvasElement {
    public tree: MindmapNode;
    public layoutType: MindmapLayoutType;
    public stylePreset: MindmapStyle;

    private nodePositions = new Map<string, { x: number; y: number; w: number; h: number }>();
    private needsLayout = true;

    // Layout constants
    private static readonly HORIZONTAL_GAP = 60;
    private static readonly VERTICAL_GAP = 20;
    private static readonly MIN_NODE_WIDTH = 80;
    private static readonly MAX_NODE_WIDTH = 200;

    constructor(props: MindmapProps) {
        super(props);
        this.tree = props.tree || this.createDefaultTree();
        this.layoutType = props.layoutType ?? MindmapLayoutType.Right;
        this.stylePreset = props.style ?? MindmapStyle.One;
    }

    private createDefaultTree(): MindmapNode {
        return {
            id: nanoid(),
            text: 'Central Topic',
            children: [
                { id: nanoid(), text: 'Branch 1', children: [] },
                { id: nanoid(), text: 'Branch 2', children: [] },
                { id: nanoid(), text: 'Branch 3', children: [] },
            ],
        };
    }

    get type(): CanvasElementType | 'mindmap' {
        return 'mindmap' as unknown as CanvasElementType;
    }

    render(ctx: CanvasRenderingContext2D, zoom: number = 1): void {
        ctx.save();
        this.applyTransform(ctx);

        const bound = this.getBounds();

        // Perform layout if needed
        if (this.needsLayout) {
            this.performLayout(ctx);
            this.needsLayout = false;
        }

        const style = MINDMAP_STYLES[this.stylePreset];

        // Draw connectors first (behind nodes)
        this.drawConnectors(ctx, this.tree, null, style.connector, zoom);

        // Draw nodes
        this.drawNode(ctx, this.tree, true, zoom);

        ctx.restore();
    }

    private performLayout(ctx: CanvasRenderingContext2D): void {
        this.nodePositions.clear();

        const bound = this.getBounds();
        const rootX = bound.x + bound.w / 2;
        const rootY = bound.y + bound.h / 2;

        // Calculate root node size
        const rootSize = this.calculateNodeSize(ctx, this.tree, true);

        // Position root at center
        this.nodePositions.set(this.tree.id, {
            x: rootX - rootSize.w / 2,
            y: rootY - rootSize.h / 2,
            w: rootSize.w,
            h: rootSize.h,
        });

        // Layout children
        if (this.tree.children.length > 0 && !this.tree.collapsed) {
            this.layoutChildren(ctx, this.tree, rootX, rootY, rootSize);
        }
    }

    private layoutChildren(
        ctx: CanvasRenderingContext2D,
        parent: MindmapNode,
        parentX: number,
        parentY: number,
        parentSize: { w: number; h: number }
    ): void {
        const children = parent.children.filter(c => !c.collapsed);
        if (children.length === 0) return;

        const isRoot = parent === this.tree;
        const style = MINDMAP_STYLES[this.stylePreset];

        // Calculate total height of children
        let totalHeight = 0;
        const childSizes: { w: number; h: number }[] = [];

        for (const child of children) {
            const size = this.calculateSubtreeSize(ctx, child);
            childSizes.push(size);
            totalHeight += size.h;
        }
        totalHeight += (children.length - 1) * MindmapElement.VERTICAL_GAP;

        // Position children
        let currentY = parentY - totalHeight / 2;
        const direction = this.layoutType === MindmapLayoutType.Left ? -1 : 1;
        const childX = parentX + direction * (parentSize.w / 2 + MindmapElement.HORIZONTAL_GAP);

        for (let i = 0; i < children.length; i++) {
            const child = children[i];
            const childSize = this.calculateNodeSize(ctx, child, false);
            const subtreeSize = childSizes[i];

            const nodeY = currentY + subtreeSize.h / 2 - childSize.h / 2;
            const nodeX = direction > 0 ? childX : childX - childSize.w;

            this.nodePositions.set(child.id, {
                x: nodeX,
                y: nodeY,
                w: childSize.w,
                h: childSize.h,
            });

            // Recursively layout grandchildren
            if (child.children.length > 0 && !child.collapsed) {
                this.layoutChildren(
                    ctx,
                    child,
                    nodeX + childSize.w / 2,
                    nodeY + childSize.h / 2,
                    childSize
                );
            }

            currentY += subtreeSize.h + MindmapElement.VERTICAL_GAP;
        }
    }

    private calculateNodeSize(
        ctx: CanvasRenderingContext2D,
        node: MindmapNode,
        isRoot: boolean
    ): { w: number; h: number } {
        const style = MINDMAP_STYLES[this.stylePreset];
        const nodeStyle = isRoot ? style.root : style.node;
        const padding = nodeStyle.padding || [8, 16];

        ctx.font = `${nodeStyle.fontWeight} ${nodeStyle.fontSize}px ${themeManager.getTheme().typography.fontFamily}`;
        const textWidth = ctx.measureText(node.text).width;

        const w = Math.max(
            MindmapElement.MIN_NODE_WIDTH,
            Math.min(textWidth + padding[1] * 2, MindmapElement.MAX_NODE_WIDTH)
        );
        const h = (nodeStyle.fontSize || 14) + padding[0] * 2;

        return { w, h };
    }

    private calculateSubtreeSize(ctx: CanvasRenderingContext2D, node: MindmapNode): { w: number; h: number } {
        const nodeSize = this.calculateNodeSize(ctx, node, false);

        if (node.collapsed || node.children.length === 0) {
            return nodeSize;
        }

        let maxChildWidth = 0;
        let totalChildHeight = 0;

        for (const child of node.children) {
            const childSize = this.calculateSubtreeSize(ctx, child);
            maxChildWidth = Math.max(maxChildWidth, childSize.w);
            totalChildHeight += childSize.h;
        }
        totalChildHeight += (node.children.length - 1) * MindmapElement.VERTICAL_GAP;

        return {
            w: nodeSize.w + MindmapElement.HORIZONTAL_GAP + maxChildWidth,
            h: Math.max(nodeSize.h, totalChildHeight),
        };
    }

    private drawNode(
        ctx: CanvasRenderingContext2D,
        node: MindmapNode,
        isRoot: boolean,
        zoom: number
    ): void {
        const pos = this.nodePositions.get(node.id);
        if (!pos) return;

        const style = MINDMAP_STYLES[this.stylePreset];
        const nodeStyle = node.style || (isRoot ? style.root : style.node);
        const theme = themeManager.getTheme();

        // Draw node background
        ctx.fillStyle = nodeStyle.fillColor || '#ffffff';
        ctx.strokeStyle = nodeStyle.strokeColor || '#d0d5dd';
        ctx.lineWidth = 1 / zoom;

        const radius = nodeStyle.radius || 0;
        if (radius > 0) {
            this.drawRoundedRect(ctx, pos.x, pos.y, pos.w, pos.h, radius);
            ctx.fill();
            if (nodeStyle.strokeColor && nodeStyle.strokeColor !== 'transparent') {
                ctx.stroke();
            }
        } else {
            ctx.fillRect(pos.x, pos.y, pos.w, pos.h);
            if (nodeStyle.strokeColor && nodeStyle.strokeColor !== 'transparent') {
                ctx.strokeRect(pos.x, pos.y, pos.w, pos.h);
            }
        }

        // Draw text
        ctx.fillStyle = nodeStyle.textColor || '#1f2937';
        ctx.font = `${nodeStyle.fontWeight} ${nodeStyle.fontSize}px ${theme.typography.fontFamily}`;
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(node.text, pos.x + pos.w / 2, pos.y + pos.h / 2);

        // Draw collapse indicator if has children
        if (node.children.length > 0 && zoom > 0.5) {
            this.drawCollapseIndicator(ctx, node, pos, zoom);
        }

        // Draw children
        if (!node.collapsed) {
            for (const child of node.children) {
                this.drawNode(ctx, child, false, zoom);
            }
        }
    }

    private drawCollapseIndicator(
        ctx: CanvasRenderingContext2D,
        node: MindmapNode,
        pos: { x: number; y: number; w: number; h: number },
        zoom: number
    ): void {
        const direction = this.layoutType === MindmapLayoutType.Left ? -1 : 1;
        const indicatorX = direction > 0 ? pos.x + pos.w + 4 : pos.x - 16;
        const indicatorY = pos.y + pos.h / 2;
        const size = 12;

        ctx.fillStyle = '#ffffff';
        ctx.strokeStyle = '#9ca3af';
        ctx.lineWidth = 1 / zoom;

        ctx.beginPath();
        ctx.arc(indicatorX + size / 2, indicatorY, size / 2, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();

        // Draw +/- symbol
        ctx.strokeStyle = '#6b7280';
        ctx.lineWidth = 1.5 / zoom;
        ctx.beginPath();
        ctx.moveTo(indicatorX + 3, indicatorY);
        ctx.lineTo(indicatorX + size - 3, indicatorY);
        if (node.collapsed) {
            ctx.moveTo(indicatorX + size / 2, indicatorY - 3);
            ctx.lineTo(indicatorX + size / 2, indicatorY + 3);
        }
        ctx.stroke();
    }

    private drawConnectors(
        ctx: CanvasRenderingContext2D,
        node: MindmapNode,
        parentPos: { x: number; y: number; w: number; h: number } | null,
        connectorStyle: { color: string; width: number },
        zoom: number
    ): void {
        const pos = this.nodePositions.get(node.id);
        if (!pos) return;

        // Draw connector to parent
        if (parentPos) {
            ctx.strokeStyle = connectorStyle.color;
            ctx.lineWidth = connectorStyle.width / zoom;
            ctx.lineCap = 'round';

            const direction = this.layoutType === MindmapLayoutType.Left ? -1 : 1;
            const startX = direction > 0 ? parentPos.x + parentPos.w : parentPos.x;
            const startY = parentPos.y + parentPos.h / 2;
            const endX = direction > 0 ? pos.x : pos.x + pos.w;
            const endY = pos.y + pos.h / 2;

            // Draw curved connector
            ctx.beginPath();
            ctx.moveTo(startX, startY);

            const controlX = (startX + endX) / 2;
            ctx.bezierCurveTo(controlX, startY, controlX, endY, endX, endY);
            ctx.stroke();
        }

        // Draw connectors for children
        if (!node.collapsed) {
            for (const child of node.children) {
                this.drawConnectors(ctx, child, pos, connectorStyle, zoom);
            }
        }
    }

    private drawRoundedRect(
        ctx: CanvasRenderingContext2D,
        x: number,
        y: number,
        w: number,
        h: number,
        r: number
    ): void {
        ctx.beginPath();
        ctx.moveTo(x + r, y);
        ctx.lineTo(x + w - r, y);
        ctx.quadraticCurveTo(x + w, y, x + w, y + r);
        ctx.lineTo(x + w, y + h - r);
        ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
        ctx.lineTo(x + r, y + h);
        ctx.quadraticCurveTo(x, y + h, x, y + h - r);
        ctx.lineTo(x, y + r);
        ctx.quadraticCurveTo(x, y, x + r, y);
        ctx.closePath();
    }

    includesPoint(x: number, y: number): boolean {
        // Check if point is within any node
        for (const pos of this.nodePositions.values()) {
            if (x >= pos.x && x <= pos.x + pos.w && y >= pos.y && y <= pos.y + pos.h) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get node at point
     */
    getNodeAtPoint(x: number, y: number): MindmapNode | null {
        for (const [nodeId, pos] of this.nodePositions.entries()) {
            if (x >= pos.x && x <= pos.x + pos.w && y >= pos.y && y <= pos.y + pos.h) {
                return this.findNodeById(this.tree, nodeId);
            }
        }
        return null;
    }

    private findNodeById(node: MindmapNode, id: string): MindmapNode | null {
        if (node.id === id) return node;
        for (const child of node.children) {
            const found = this.findNodeById(child, id);
            if (found) return found;
        }
        return null;
    }

    /**
     * Add child node
     */
    addNode(parentId: string, text: string = 'New Node'): MindmapNode | null {
        const parent = this.findNodeById(this.tree, parentId);
        if (!parent) return null;

        const newNode: MindmapNode = {
            id: nanoid(),
            text,
            children: [],
        };
        parent.children.push(newNode);
        this.needsLayout = true;
        return newNode;
    }

    /**
     * Remove node
     */
    removeNode(nodeId: string): boolean {
        if (nodeId === this.tree.id) return false; // Can't remove root

        const removed = this.removeNodeFromParent(this.tree, nodeId);
        if (removed) {
            this.needsLayout = true;
        }
        return removed;
    }

    private removeNodeFromParent(parent: MindmapNode, nodeId: string): boolean {
        const index = parent.children.findIndex(c => c.id === nodeId);
        if (index !== -1) {
            parent.children.splice(index, 1);
            return true;
        }
        for (const child of parent.children) {
            if (this.removeNodeFromParent(child, nodeId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Toggle node collapse
     */
    toggleCollapse(nodeId: string): void {
        const node = this.findNodeById(this.tree, nodeId);
        if (node) {
            node.collapsed = !node.collapsed;
            this.needsLayout = true;
        }
    }

    /**
     * Update node text
     */
    updateNodeText(nodeId: string, text: string): void {
        const node = this.findNodeById(this.tree, nodeId);
        if (node) {
            node.text = text;
            this.needsLayout = true;
        }
    }

    /**
     * Force layout recalculation
     */
    invalidateLayout(): void {
        this.needsLayout = true;
    }
}
