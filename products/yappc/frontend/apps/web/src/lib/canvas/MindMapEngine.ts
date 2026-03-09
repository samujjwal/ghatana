/**
 * MindMapEngine - Automatic Layout for Mind Maps
 * 
 * Implements tree-based and radial layouts for mind map nodes
 * 
 * @doc.type class
 * @doc.purpose Auto-layout algorithm for mind maps
 * @doc.layer lib
 * @doc.pattern Engine
 */

import { logger } from '../../utils/Logger';

export interface MindMapNode {
    id: string;
    position: { x: number; y: number };
    size: { width: number; height: number };
    data: {
        text: string;
        level: number;
        isRoot?: boolean;
    };
    parentId?: string;
    children: string[];
}

export interface LayoutConfig {
    type: 'tree' | 'radial' | 'fishbone';
    direction: 'horizontal' | 'vertical';
    levelSpacing: number;
    siblingSpacing: number;
    rootPosition?: { x: number; y: number };
}

export class MindMapEngine {
    private config: LayoutConfig;

    constructor(config: Partial<LayoutConfig> = {}) {
        this.config = {
            type: config.type || 'tree',
            direction: config.direction || 'horizontal',
            levelSpacing: config.levelSpacing || 200,
            siblingSpacing: config.siblingSpacing || 100,
            rootPosition: config.rootPosition || { x: 400, y: 300 }
        };
    }

    /**
     * Calculate layout for all nodes
     */
    public layoutNodes(nodes: MindMapNode[]): MindMapNode[] {
        // Find root node
        const rootNode = nodes.find(n => n.data.isRoot || n.data.level === 0);
        if (!rootNode) {
            logger.warn('No root node found', 'mind-map-engine');
            return nodes;
        }

        // Build tree structure
        const tree = this.buildTree(nodes, rootNode.id);

        // Apply layout algorithm
        switch (this.config.type) {
            case 'radial':
                return this.applyRadialLayout(nodes, tree);
            case 'fishbone':
                return this.applyFishboneLayout(nodes, tree);
            case 'tree':
            default:
                return this.applyTreeLayout(nodes, tree);
        }
    }

    /**
     * Build tree structure from flat node list
     */
    private buildTree(nodes: MindMapNode[], rootId: string): TreeNode {
        const nodeMap = new Map(nodes.map(n => [n.id, n]));

        const buildSubtree = (nodeId: string, level: number): TreeNode => {
            const node = nodeMap.get(nodeId);
            if (!node) throw new Error(`Node ${nodeId} not found`);

            const children = node.children
                .map(childId => buildSubtree(childId, level + 1))
                .filter(Boolean);

            return {
                id: nodeId,
                level,
                children,
                width: node.size.width,
                height: node.size.height,
                x: 0,
                y: 0
            };
        };

        return buildSubtree(rootId, 0);
    }

    /**
     * Apply tree layout (horizontal or vertical)
     */
    private applyTreeLayout(nodes: MindMapNode[], tree: TreeNode): MindMapNode[] {
        const { direction, levelSpacing, siblingSpacing, rootPosition } = this.config;

        // Calculate subtree heights/widths
        this.calculateSubtreeDimensions(tree, direction);

        // Position nodes
        if (direction === 'horizontal') {
            this.layoutTreeHorizontal(tree, rootPosition!.x, rootPosition!.y, levelSpacing, siblingSpacing);
        } else {
            this.layoutTreeVertical(tree, rootPosition!.x, rootPosition!.y, levelSpacing, siblingSpacing);
        }

        // Update node positions
        return this.applyPositions(nodes, tree);
    }

    /**
     * Calculate dimensions of each subtree
     */
    private calculateSubtreeDimensions(node: TreeNode, direction: 'horizontal' | 'vertical'): number {
        if (node.children.length === 0) {
            node.subtreeSize = direction === 'horizontal' ? node.height : node.width;
            return node.subtreeSize;
        }

        let totalSize = 0;
        for (const child of node.children) {
            totalSize += this.calculateSubtreeDimensions(child, direction);
        }

        // Add spacing between siblings
        const spacing = this.config.siblingSpacing * (node.children.length - 1);
        node.subtreeSize = totalSize + spacing;

        return node.subtreeSize;
    }

    /**
     * Layout tree horizontally (left to right)
     */
    private layoutTreeHorizontal(
        node: TreeNode,
        x: number,
        y: number,
        levelSpacing: number,
        siblingSpacing: number
    ): void {
        node.x = x;
        node.y = y;

        if (node.children.length === 0) return;

        // Calculate starting Y for children
        const startY = y - (node.subtreeSize! / 2);
        let currentY = startY;

        for (const child of node.children) {
            const childCenterY = currentY + (child.subtreeSize! / 2);
            this.layoutTreeHorizontal(
                child,
                x + levelSpacing,
                childCenterY,
                levelSpacing,
                siblingSpacing
            );
            currentY += child.subtreeSize! + siblingSpacing;
        }
    }

    /**
     * Layout tree vertically (top to bottom)
     */
    private layoutTreeVertical(
        node: TreeNode,
        x: number,
        y: number,
        levelSpacing: number,
        siblingSpacing: number
    ): void {
        node.x = x;
        node.y = y;

        if (node.children.length === 0) return;

        // Calculate starting X for children
        const startX = x - (node.subtreeSize! / 2);
        let currentX = startX;

        for (const child of node.children) {
            const childCenterX = currentX + (child.subtreeSize! / 2);
            this.layoutTreeVertical(
                child,
                childCenterX,
                y + levelSpacing,
                levelSpacing,
                siblingSpacing
            );
            currentX += child.subtreeSize! + siblingSpacing;
        }
    }

    /**
     * Apply radial layout (circular around center)
     */
    private applyRadialLayout(nodes: MindMapNode[], tree: TreeNode): MindMapNode[] {
        const { rootPosition } = this.config;

        tree.x = rootPosition!.x;
        tree.y = rootPosition!.y;

        // Layout each level in concentric circles
        this.layoutRadialLevel(tree, 0, 0, Math.PI * 2);

        return this.applyPositions(nodes, tree);
    }

    /**
     * Layout nodes in radial/circular pattern
     */
    private layoutRadialLevel(
        node: TreeNode,
        level: number,
        startAngle: number,
        angleRange: number
    ): void {
        if (node.children.length === 0) return;

        const radius = (level + 1) * this.config.levelSpacing;
        const angleStep = angleRange / node.children.length;

        node.children.forEach((child, index) => {
            const angle = startAngle + (index + 0.5) * angleStep;
            child.x = node.x + Math.cos(angle) * radius;
            child.y = node.y + Math.sin(angle) * radius;

            // Recursively layout child's subtree
            this.layoutRadialLevel(
                child,
                level + 1,
                angle - angleStep / 2,
                angleStep
            );
        });
    }

    /**
     * Apply fishbone layout (Ishikawa diagram style)
     */
    private applyFishboneLayout(nodes: MindMapNode[], tree: TreeNode): MindMapNode[] {
        const { rootPosition, levelSpacing, siblingSpacing } = this.config;

        tree.x = rootPosition!.x;
        tree.y = rootPosition!.y;

        // Main spine is horizontal
        const childCount = tree.children.length;
        const topChildren = tree.children.slice(0, Math.ceil(childCount / 2));
        const bottomChildren = tree.children.slice(Math.ceil(childCount / 2));

        // Layout top branches (angled up)
        topChildren.forEach((child, index) => {
            const angle = -Math.PI / 4; // 45 degrees up
            const distance = (index + 1) * levelSpacing;
            child.x = tree.x + Math.cos(angle) * distance;
            child.y = tree.y + Math.sin(angle) * distance;
        });

        // Layout bottom branches (angled down)
        bottomChildren.forEach((child, index) => {
            const angle = Math.PI / 4; // 45 degrees down
            const distance = (index + 1) * levelSpacing;
            child.x = tree.x + Math.cos(angle) * distance;
            child.y = tree.y + Math.sin(angle) * distance;
        });

        return this.applyPositions(nodes, tree);
    }

    /**
     * Apply calculated positions back to nodes
     */
    private applyPositions(nodes: MindMapNode[], tree: TreeNode): MindMapNode[] {
        const positionMap = new Map<string, { x: number; y: number }>();

        const collectPositions = (node: TreeNode) => {
            positionMap.set(node.id, { x: node.x, y: node.y });
            node.children.forEach(collectPositions);
        };

        collectPositions(tree);

        return nodes.map(node => {
            const pos = positionMap.get(node.id);
            if (pos) {
                return {
                    ...node,
                    position: { x: pos.x - node.size.width / 2, y: pos.y - node.size.height / 2 }
                };
            }
            return node;
        });
    }

    /**
     * Update configuration
     */
    public updateConfig(config: Partial<LayoutConfig>): void {
        this.config = { ...this.config, ...config };
    }
}

interface TreeNode {
    id: string;
    level: number;
    children: TreeNode[];
    width: number;
    height: number;
    x: number;
    y: number;
    subtreeSize?: number;
}
