/**
 * Drop Target Resolver
 *
 * Determines valid drop targets based on constraints and artifact contracts.
 * Handles hit testing and constraint validation.
 *
 * @doc.type service
 * @doc.purpose Drop target resolution and validation
 * @doc.layer core
 * @doc.pattern Strategy
 */

import type {
    ArtifactContract,
    ArtifactKind,
    UniqueId,
    UniversalNode,
} from '../model/contracts';
import type { DragSource, DropTarget } from './DragDropManager';

// ============================================================================
// Hit Test Types
// ============================================================================

/**
 * Hit test result
 */
export interface HitTestResult {
    /** Node that was hit */
    nodeId: UniqueId | null;
    /** Node data */
    node: UniversalNode | null;
    /** Which part of the node was hit */
    zone: HitZone;
    /** Distance from center (for ranking) */
    distance: number;
    /** Whether node accepts drops */
    droppable: boolean;
}

/**
 * Hit zones on a node
 */
export type HitZone =
    | 'center'
    | 'top'
    | 'right'
    | 'bottom'
    | 'left'
    | 'top-left'
    | 'top-right'
    | 'bottom-left'
    | 'bottom-right'
    | 'edge';

/**
 * Point in canvas coordinates
 */
export interface CanvasPoint {
    x: number;
    y: number;
}

// ============================================================================
// Constraint Validation
// ============================================================================

/**
 * Constraint validation result
 */
export interface ConstraintResult {
    allowed: boolean;
    reason?: string;
    warnings?: string[];
}

/**
 * Constraint validator function
 */
export type ConstraintValidator = (
    source: DragSource,
    target: { nodeId?: UniqueId; node?: UniversalNode; contract?: ArtifactContract },
    context: DropResolverContext
) => ConstraintResult;

/**
 * Context for drop resolution
 */
export interface DropResolverContext {
    /** All nodes in the document */
    nodes: Map<UniqueId, UniversalNode>;
    /** Contract lookup */
    getContract: (kind: ArtifactKind) => ArtifactContract | undefined;
    /** Current view ID */
    viewId: string;
    /** Viewport transform */
    viewport: {
        x: number;
        y: number;
        zoom: number;
    };
}

// ============================================================================
// Drop Target Resolver Implementation
// ============================================================================

/**
 * Configuration for drop resolver
 */
export interface DropResolverConfig {
    /** Edge tolerance for hit testing (px) */
    edgeTolerance: number;
    /** Zone size for corners (px) */
    cornerSize: number;
    /** Enable nested drops */
    allowNestedDrops: boolean;
    /** Enable sibling reordering */
    allowReorder: boolean;
    /** Max depth for nested drops */
    maxNestingDepth: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: DropResolverConfig = {
    edgeTolerance: 10,
    cornerSize: 20,
    allowNestedDrops: true,
    allowReorder: true,
    maxNestingDepth: 10,
};

/**
 * Drop Target Resolver
 *
 * Resolves drop targets based on position, constraints, and contracts.
 */
export class DropTargetResolver {
    private config: DropResolverConfig;
    private customValidators: Map<string, ConstraintValidator> = new Map();

    constructor(config: Partial<DropResolverConfig> = {}) {
        this.config = { ...DEFAULT_CONFIG, ...config };
    }

    // ============================================================================
    // Public API
    // ============================================================================

    /**
     * Resolve drop target for a given position
     */
    resolve(
        source: DragSource,
        canvasPoint: CanvasPoint,
        context: DropResolverContext
    ): DropTarget {
        // Hit test to find node under cursor
        const hitResult = this.hitTest(canvasPoint, context);

        // If no node hit, resolve to canvas drop
        if (!hitResult.nodeId || !hitResult.node) {
            return this.resolveCanvasDrop(source, canvasPoint, context);
        }

        // Validate constraints for node drop
        const validation = this.validateDrop(source, hitResult, context);

        return {
            type: 'node',
            nodeId: hitResult.nodeId,
            viewId: context.viewId,
            position: canvasPoint,
            allowed: validation.allowed,
            reason: validation.reason,
            index: this.calculateInsertIndex(hitResult, canvasPoint, context),
        };
    }

    /**
     * Resolve drop target for canvas (no specific node)
     */
    resolveCanvasDrop(
        source: DragSource,
        canvasPoint: CanvasPoint,
        context: DropResolverContext
    ): DropTarget {
        const validation = this.validateCanvasDrop(source, context);

        return {
            type: 'canvas',
            viewId: context.viewId,
            position: canvasPoint,
            allowed: validation.allowed,
            reason: validation.reason,
        };
    }

    /**
     * Perform hit test at canvas coordinates
     */
    hitTest(point: CanvasPoint, context: DropResolverContext): HitTestResult {
        let bestHit: HitTestResult = {
            nodeId: null,
            node: null,
            zone: 'center',
            distance: Infinity,
            droppable: false,
        };

        // Test all nodes (in reverse z-order for top-most first)
        const sortedNodes = Array.from(context.nodes.values()).sort(
            (a, b) => (b.transform.zIndex || 0) - (a.transform.zIndex || 0)
        );

        for (const node of sortedNodes) {
            if (!node.visible) continue;

            const hit = this.testNode(point, node, context);
            if (hit && hit.distance < bestHit.distance) {
                bestHit = hit;
            }
        }

        return bestHit;
    }

    /**
     * Add a custom constraint validator
     */
    addValidator(id: string, validator: ConstraintValidator): void {
        this.customValidators.set(id, validator);
    }

    /**
     * Remove a custom constraint validator
     */
    removeValidator(id: string): void {
        this.customValidators.delete(id);
    }

    /**
     * Update configuration
     */
    updateConfig(config: Partial<DropResolverConfig>): void {
        this.config = { ...this.config, ...config };
    }

    // ============================================================================
    // Hit Testing
    // ============================================================================

    /**
     * Test if point is inside a node
     */
    private testNode(
        point: CanvasPoint,
        node: UniversalNode,
        context: DropResolverContext
    ): HitTestResult | null {
        const { x, y, width, height } = node.transform;

        // Check if point is inside bounds
        if (point.x < x || point.x > x + width || point.y < y || point.y > y + height) {
            return null;
        }

        // Calculate zone and distance
        const zone = this.calculateZone(point, node.transform);
        const centerX = x + width / 2;
        const centerY = y + height / 2;
        const distance = Math.sqrt((point.x - centerX) ** 2 + (point.y - centerY) ** 2);

        // Check if node is droppable
        const contract = context.getContract(node.kind);
        const droppable = contract?.capabilities.droppable ?? false;

        return {
            nodeId: node.id,
            node,
            zone,
            distance,
            droppable,
        };
    }

    /**
     * Calculate which zone of a node was hit
     */
    private calculateZone(
        point: CanvasPoint,
        transform: UniversalNode['transform']
    ): HitZone {
        const { x, y, width, height } = transform;
        const { cornerSize, edgeTolerance } = this.config;

        const relX = point.x - x;
        const relY = point.y - y;

        // Check corners first
        if (relX < cornerSize && relY < cornerSize) return 'top-left';
        if (relX > width - cornerSize && relY < cornerSize) return 'top-right';
        if (relX < cornerSize && relY > height - cornerSize) return 'bottom-left';
        if (relX > width - cornerSize && relY > height - cornerSize) return 'bottom-right';

        // Check edges
        if (relY < edgeTolerance) return 'top';
        if (relY > height - edgeTolerance) return 'bottom';
        if (relX < edgeTolerance) return 'left';
        if (relX > width - edgeTolerance) return 'right';

        // Center
        return 'center';
    }

    // ============================================================================
    // Constraint Validation
    // ============================================================================

    /**
     * Validate drop on a node
     */
    private validateDrop(
        source: DragSource,
        hit: HitTestResult,
        context: DropResolverContext
    ): ConstraintResult {
        if (!hit.node || !hit.nodeId) {
            return { allowed: false, reason: 'No target node' };
        }

        const contract = context.getContract(hit.node.kind);

        // Check if node accepts drops
        if (!contract?.capabilities.droppable) {
            return { allowed: false, reason: 'Target does not accept drops' };
        }

        // Check acceptsChildren constraint
        const acceptsChildren = contract.constraints.acceptsChildren;
        if (acceptsChildren === false) {
            return { allowed: false, reason: 'Target cannot have children' };
        }

        // Check kind restrictions
        if (Array.isArray(acceptsChildren)) {
            const sourceKind = this.getSourceKind(source);
            if (sourceKind && !acceptsChildren.includes(sourceKind)) {
                return {
                    allowed: false,
                    reason: `Target only accepts: ${acceptsChildren.join(', ')}`,
                };
            }
        }

        // Check max children
        const maxChildren = contract.constraints.maxChildren;
        if (maxChildren !== undefined && hit.node.children.length >= maxChildren) {
            return {
                allowed: false,
                reason: `Target has maximum children (${maxChildren})`,
            };
        }

        // Check nesting depth
        const depth = this.calculateNestingDepth(hit.nodeId, context);
        if (depth >= this.config.maxNestingDepth) {
            return { allowed: false, reason: 'Maximum nesting depth reached' };
        }

        // Check for circular reference (if moving existing nodes)
        if (source.type === 'canvas') {
            for (const nodeId of source.nodeIds) {
                if (this.wouldCreateCircular(nodeId, hit.nodeId, context)) {
                    return { allowed: false, reason: 'Would create circular reference' };
                }
            }
        }

        // Run custom validators
        for (const [, validator] of this.customValidators) {
            const result = validator(source, { nodeId: hit.nodeId, node: hit.node, contract }, context);
            if (!result.allowed) {
                return result;
            }
        }

        return { allowed: true };
    }

    /**
     * Validate drop on canvas (root level)
     */
    private validateCanvasDrop(
        source: DragSource,
        context: DropResolverContext
    ): ConstraintResult {
        const sourceKind = this.getSourceKind(source);

        if (!sourceKind) {
            return { allowed: true }; // Allow external drops
        }

        // Check if source requires a parent
        const contract = context.getContract(sourceKind);
        if (contract?.constraints.requiresParent) {
            return {
                allowed: false,
                reason: `${contract.identity.name} requires a parent of type: ${contract.constraints.requiresParent.join(', ')}`,
            };
        }

        return { allowed: true };
    }

    /**
     * Get the kind from a drag source
     */
    private getSourceKind(source: DragSource): ArtifactKind | null {
        switch (source.type) {
            case 'palette':
                return source.contract.identity.kind;
            case 'canvas':
                return source.nodes[0]?.kind ?? null;
            case 'tree':
                return source.node.kind;
            default:
                return null;
        }
    }

    /**
     * Calculate nesting depth of a node
     */
    private calculateNestingDepth(nodeId: UniqueId, context: DropResolverContext): number {
        let depth = 0;
        let currentId: UniqueId | null = nodeId;

        while (currentId) {
            const node = context.nodes.get(currentId);
            if (!node) break;

            currentId = node.parentId;
            depth++;

            if (depth > this.config.maxNestingDepth + 1) {
                break; // Safety limit
            }
        }

        return depth;
    }

    /**
     * Check if reparenting would create a circular reference
     */
    private wouldCreateCircular(
        sourceId: UniqueId,
        targetId: UniqueId,
        context: DropResolverContext
    ): boolean {
        if (sourceId === targetId) return true;

        // Check if target is a descendant of source
        const descendants = this.getDescendants(sourceId, context);
        return descendants.includes(targetId);
    }

    /**
     * Get all descendant IDs of a node
     */
    private getDescendants(nodeId: UniqueId, context: DropResolverContext): UniqueId[] {
        const result: UniqueId[] = [];
        const queue = [nodeId];

        while (queue.length > 0) {
            const current = queue.shift()!;
            const node = context.nodes.get(current);

            if (node) {
                for (const childId of node.children) {
                    result.push(childId);
                    queue.push(childId);
                }
            }
        }

        return result;
    }

    /**
     * Calculate insert index based on position
     */
    private calculateInsertIndex(
        hit: HitTestResult,
        point: CanvasPoint,
        context: DropResolverContext
    ): number | undefined {
        if (!hit.node || !this.config.allowReorder) return undefined;

        const children = hit.node.children;
        if (children.length === 0) return 0;

        // Simple heuristic: sort by y position and find insert point
        const childNodes = children
            .map((id) => context.nodes.get(id))
            .filter((n): n is UniversalNode => n !== undefined)
            .sort((a, b) => a.transform.y - b.transform.y);

        for (let i = 0; i < childNodes.length; i++) {
            const childCenter = childNodes[i].transform.y + childNodes[i].transform.height / 2;
            if (point.y < childCenter) {
                return i;
            }
        }

        return children.length;
    }
}

// ============================================================================
// Convenience Functions
// ============================================================================

/**
 * Create a drop target resolver with default config
 */
export function createDropTargetResolver(
    config?: Partial<DropResolverConfig>
): DropTargetResolver {
    return new DropTargetResolver(config);
}
