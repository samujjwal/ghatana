/**
 * Predictive Layout
 *
 * AI-assisted layout suggestions and auto-arrangement.
 * Provides smart positioning, alignment, and grouping recommendations.
 *
 * @doc.type service
 * @doc.purpose AI layout suggestions
 * @doc.layer ai
 * @doc.pattern Strategy Pattern
 */

import type { UniversalNode, ArtifactContract, NodeTransform } from '../model/contracts';
import { getArtifactRegistry } from '../registry/ArtifactRegistry';

// ============================================================================
// Predictive Layout Types
// ============================================================================

/**
 * Layout suggestion type
 */
export type SuggestionType =
    | 'position'
    | 'alignment'
    | 'distribution'
    | 'grouping'
    | 'sizing'
    | 'spacing'
    | 'nesting';

/**
 * Layout suggestion confidence
 */
export type SuggestionConfidence = 'low' | 'medium' | 'high';

/**
 * Layout suggestion
 */
export interface LayoutSuggestion {
    /** Unique identifier */
    id: string;
    /** Type of suggestion */
    type: SuggestionType;
    /** Human-readable description */
    description: string;
    /** Confidence level */
    confidence: SuggestionConfidence;
    /** Score (0-1) */
    score: number;
    /** Node IDs affected */
    nodeIds: string[];
    /** Suggested transforms (nodeId -> new transform) */
    transforms?: Record<string, Partial<NodeTransform>>;
    /** Preview data for visualization */
    preview?: LayoutPreview;
    /** Action to apply the suggestion */
    apply?: () => void;
}

/**
 * Layout preview for visualization
 */
export interface LayoutPreview {
    /** Guide lines to show */
    guides: LayoutGuide[];
    /** Highlight areas */
    highlights: LayoutHighlight[];
    /** Ghost positions for nodes */
    ghosts: Record<string, NodeTransform>;
}

/**
 * Guide line for alignment visualization
 */
export interface LayoutGuide {
    /** Guide type */
    type: 'horizontal' | 'vertical';
    /** Position (x for vertical, y for horizontal) */
    position: number;
    /** Start point */
    start: number;
    /** End point */
    end: number;
    /** Color */
    color?: string;
    /** Dashed */
    dashed?: boolean;
}

/**
 * Highlight area
 */
export interface LayoutHighlight {
    /** Bounds */
    x: number;
    y: number;
    width: number;
    height: number;
    /** Color */
    color?: string;
    /** Opacity */
    opacity?: number;
}

/**
 * Layout analysis result
 */
export interface LayoutAnalysis {
    /** Detected patterns */
    patterns: LayoutPattern[];
    /** Alignment issues */
    issues: LayoutIssue[];
    /** Overall layout score */
    score: number;
    /** Recommendations */
    suggestions: LayoutSuggestion[];
}

/**
 * Detected layout pattern
 */
export interface LayoutPattern {
    /** Pattern type */
    type: 'grid' | 'flex-row' | 'flex-column' | 'stack' | 'free-form';
    /** Nodes in pattern */
    nodeIds: string[];
    /** Pattern bounds */
    bounds: { x: number; y: number; width: number; height: number };
    /** Pattern parameters */
    params?: Record<string, unknown>;
}

/**
 * Layout issue
 */
export interface LayoutIssue {
    /** Issue type */
    type: 'misalignment' | 'overlap' | 'inconsistent-spacing' | 'orphaned';
    /** Severity */
    severity: 'low' | 'medium' | 'high';
    /** Affected node IDs */
    nodeIds: string[];
    /** Description */
    description: string;
}

/**
 * Snap target for drag operations
 */
export interface SnapTarget {
    /** Target type */
    type: 'node' | 'grid' | 'guide' | 'center';
    /** Axis */
    axis: 'x' | 'y' | 'both';
    /** Snap position */
    position: { x?: number; y?: number };
    /** Distance to snap (for prioritization) */
    distance: number;
    /** Reference node ID (if type is 'node') */
    referenceNodeId?: string;
}

// ============================================================================
// Predictive Layout Class
// ============================================================================

/**
 * Predictive Layout - AI-assisted layout service
 */
export class PredictiveLayout {
    private static instance: PredictiveLayout | null = null;

    /** Grid snap size */
    private gridSize: number = 8;

    /** Snap threshold in pixels */
    private snapThreshold: number = 10;

    /** Enable smart snapping */
    private smartSnapEnabled: boolean = true;

    private constructor() { }

    /**
     * Get singleton instance
     */
    public static getInstance(): PredictiveLayout {
        if (!PredictiveLayout.instance) {
            PredictiveLayout.instance = new PredictiveLayout();
        }
        return PredictiveLayout.instance;
    }

    // ============================================================================
    // Configuration
    // ============================================================================

    /**
     * Set grid size
     */
    public setGridSize(size: number): void {
        this.gridSize = size;
    }

    /**
     * Set snap threshold
     */
    public setSnapThreshold(threshold: number): void {
        this.snapThreshold = threshold;
    }

    /**
     * Enable/disable smart snapping
     */
    public setSmartSnapEnabled(enabled: boolean): void {
        this.smartSnapEnabled = enabled;
    }

    // ============================================================================
    // Layout Analysis
    // ============================================================================

    /**
     * Analyze layout of nodes
     */
    public analyzeLayout(nodes: UniversalNode[]): LayoutAnalysis {
        const patterns = this.detectPatterns(nodes);
        const issues = this.detectIssues(nodes);
        const suggestions = this.generateSuggestions(nodes, patterns, issues);
        const score = this.calculateLayoutScore(nodes, issues);

        return {
            patterns,
            issues,
            score,
            suggestions,
        };
    }

    /**
     * Detect layout patterns
     */
    private detectPatterns(nodes: UniversalNode[]): LayoutPattern[] {
        const patterns: LayoutPattern[] = [];

        // Detect horizontal alignment (row)
        const horizontalGroups = this.findAlignedGroups(nodes, 'horizontal');
        horizontalGroups.forEach((group) => {
            if (group.length >= 2) {
                patterns.push({
                    type: 'flex-row',
                    nodeIds: group.map((n) => n.id),
                    bounds: this.calculateGroupBounds(group),
                });
            }
        });

        // Detect vertical alignment (column)
        const verticalGroups = this.findAlignedGroups(nodes, 'vertical');
        verticalGroups.forEach((group) => {
            if (group.length >= 2) {
                patterns.push({
                    type: 'flex-column',
                    nodeIds: group.map((n) => n.id),
                    bounds: this.calculateGroupBounds(group),
                });
            }
        });

        // Detect grid pattern
        const gridPattern = this.detectGridPattern(nodes);
        if (gridPattern) {
            patterns.push(gridPattern);
        }

        return patterns;
    }

    /**
     * Find nodes that are aligned on an axis
     */
    private findAlignedGroups(
        nodes: UniversalNode[],
        direction: 'horizontal' | 'vertical'
    ): UniversalNode[][] {
        const groups: UniversalNode[][] = [];
        const used = new Set<string>();
        const tolerance = 10;

        for (const node of nodes) {
            if (used.has(node.id)) continue;

            const group: UniversalNode[] = [node];
            used.add(node.id);

            const referenceY = node.transform.y + node.transform.height / 2;
            const referenceX = node.transform.x + node.transform.width / 2;

            for (const other of nodes) {
                if (used.has(other.id)) continue;

                if (direction === 'horizontal') {
                    const otherY = other.transform.y + other.transform.height / 2;
                    if (Math.abs(otherY - referenceY) <= tolerance) {
                        group.push(other);
                        used.add(other.id);
                    }
                } else {
                    const otherX = other.transform.x + other.transform.width / 2;
                    if (Math.abs(otherX - referenceX) <= tolerance) {
                        group.push(other);
                        used.add(other.id);
                    }
                }
            }

            if (group.length >= 2) {
                // Sort by position
                if (direction === 'horizontal') {
                    group.sort((a, b) => a.transform.x - b.transform.x);
                } else {
                    group.sort((a, b) => a.transform.y - b.transform.y);
                }
                groups.push(group);
            }
        }

        return groups;
    }

    /**
     * Detect grid pattern in nodes
     */
    private detectGridPattern(nodes: UniversalNode[]): LayoutPattern | null {
        if (nodes.length < 4) return null;

        // Find common spacing patterns
        const xPositions = [...new Set(nodes.map((n) => n.transform.x))].sort((a, b) => a - b);
        const yPositions = [...new Set(nodes.map((n) => n.transform.y))].sort((a, b) => a - b);

        // Check for regular spacing
        const xSpacings = xPositions.slice(1).map((x, i) => x - xPositions[i]);
        const ySpacings = yPositions.slice(1).map((y, i) => y - yPositions[i]);

        const avgXSpacing = xSpacings.reduce((a, b) => a + b, 0) / xSpacings.length;
        const avgYSpacing = ySpacings.reduce((a, b) => a + b, 0) / ySpacings.length;

        const xVariance = xSpacings.reduce((a, b) => a + Math.abs(b - avgXSpacing), 0) / xSpacings.length;
        const yVariance = ySpacings.reduce((a, b) => a + Math.abs(b - avgYSpacing), 0) / ySpacings.length;

        // If spacing is relatively consistent, it's a grid
        if (xVariance < 20 && yVariance < 20 && xPositions.length >= 2 && yPositions.length >= 2) {
            return {
                type: 'grid',
                nodeIds: nodes.map((n) => n.id),
                bounds: this.calculateGroupBounds(nodes),
                params: {
                    columns: xPositions.length,
                    rows: yPositions.length,
                    columnGap: avgXSpacing,
                    rowGap: avgYSpacing,
                },
            };
        }

        return null;
    }

    /**
     * Detect layout issues
     */
    private detectIssues(nodes: UniversalNode[]): LayoutIssue[] {
        const issues: LayoutIssue[] = [];

        // Detect overlaps
        for (let i = 0; i < nodes.length; i++) {
            for (let j = i + 1; j < nodes.length; j++) {
                if (this.nodesOverlap(nodes[i], nodes[j])) {
                    issues.push({
                        type: 'overlap',
                        severity: 'medium',
                        nodeIds: [nodes[i].id, nodes[j].id],
                        description: `${nodes[i].name} overlaps with ${nodes[j].name}`,
                    });
                }
            }
        }

        // Detect misalignments (near-miss alignments)
        const tolerance = 5;
        const alignmentTolerance = 15;

        for (let i = 0; i < nodes.length; i++) {
            for (let j = i + 1; j < nodes.length; j++) {
                const aCenter = nodes[i].transform.y + nodes[i].transform.height / 2;
                const bCenter = nodes[j].transform.y + nodes[j].transform.height / 2;
                const diff = Math.abs(aCenter - bCenter);

                if (diff > tolerance && diff < alignmentTolerance) {
                    issues.push({
                        type: 'misalignment',
                        severity: 'low',
                        nodeIds: [nodes[i].id, nodes[j].id],
                        description: `${nodes[i].name} is slightly misaligned with ${nodes[j].name}`,
                    });
                }
            }
        }

        // Detect inconsistent spacing
        const spacings = this.calculateSpacings(nodes);
        if (spacings.length >= 2) {
            const avgSpacing = spacings.reduce((a, b) => a + b, 0) / spacings.length;
            const variance = spacings.reduce((a, b) => a + Math.abs(b - avgSpacing), 0) / spacings.length;

            if (variance > 10) {
                issues.push({
                    type: 'inconsistent-spacing',
                    severity: 'low',
                    nodeIds: nodes.map((n) => n.id),
                    description: 'Spacing between elements is inconsistent',
                });
            }
        }

        return issues;
    }

    /**
     * Calculate spacing between adjacent nodes
     */
    private calculateSpacings(nodes: UniversalNode[]): number[] {
        const spacings: number[] = [];
        const sorted = [...nodes].sort((a, b) => a.transform.x - b.transform.x);

        for (let i = 0; i < sorted.length - 1; i++) {
            const spacing =
                sorted[i + 1].transform.x - (sorted[i].transform.x + sorted[i].transform.width);
            if (spacing > 0) {
                spacings.push(spacing);
            }
        }

        return spacings;
    }

    /**
     * Check if two nodes overlap
     */
    private nodesOverlap(a: UniversalNode, b: UniversalNode): boolean {
        const aLeft = a.transform.x;
        const aRight = a.transform.x + a.transform.width;
        const aTop = a.transform.y;
        const aBottom = a.transform.y + a.transform.height;

        const bLeft = b.transform.x;
        const bRight = b.transform.x + b.transform.width;
        const bTop = b.transform.y;
        const bBottom = b.transform.y + b.transform.height;

        return !(aRight <= bLeft || bRight <= aLeft || aBottom <= bTop || bBottom <= aTop);
    }

    /**
     * Calculate group bounds
     */
    private calculateGroupBounds(nodes: UniversalNode[]): {
        x: number;
        y: number;
        width: number;
        height: number;
    } {
        if (nodes.length === 0) {
            return { x: 0, y: 0, width: 0, height: 0 };
        }

        let minX = Infinity;
        let minY = Infinity;
        let maxX = -Infinity;
        let maxY = -Infinity;

        for (const node of nodes) {
            minX = Math.min(minX, node.transform.x);
            minY = Math.min(minY, node.transform.y);
            maxX = Math.max(maxX, node.transform.x + node.transform.width);
            maxY = Math.max(maxY, node.transform.y + node.transform.height);
        }

        return {
            x: minX,
            y: minY,
            width: maxX - minX,
            height: maxY - minY,
        };
    }

    /**
     * Calculate overall layout score
     */
    private calculateLayoutScore(nodes: UniversalNode[], issues: LayoutIssue[]): number {
        if (nodes.length === 0) return 1;

        let score = 1;

        // Deduct for issues
        for (const issue of issues) {
            switch (issue.severity) {
                case 'high':
                    score -= 0.2;
                    break;
                case 'medium':
                    score -= 0.1;
                    break;
                case 'low':
                    score -= 0.05;
                    break;
            }
        }

        return Math.max(0, score);
    }

    // ============================================================================
    // Suggestions
    // ============================================================================

    /**
     * Generate layout suggestions
     */
    private generateSuggestions(
        nodes: UniversalNode[],
        patterns: LayoutPattern[],
        issues: LayoutIssue[]
    ): LayoutSuggestion[] {
        const suggestions: LayoutSuggestion[] = [];
        let idCounter = 0;

        // Generate alignment suggestions for misalignments
        for (const issue of issues) {
            if (issue.type === 'misalignment') {
                const alignmentSuggestion = this.suggestAlignment(
                    nodes.filter((n) => issue.nodeIds.includes(n.id)),
                    `suggestion-${idCounter++}`
                );
                if (alignmentSuggestion) {
                    suggestions.push(alignmentSuggestion);
                }
            }
        }

        // Generate spacing suggestions
        const spacingSuggestion = this.suggestEvenSpacing(nodes, `suggestion-${idCounter++}`);
        if (spacingSuggestion) {
            suggestions.push(spacingSuggestion);
        }

        // Generate distribution suggestions
        if (nodes.length >= 3) {
            const distributeSuggestion = this.suggestDistribution(nodes, `suggestion-${idCounter++}`);
            if (distributeSuggestion) {
                suggestions.push(distributeSuggestion);
            }
        }

        // Sort by score
        suggestions.sort((a, b) => b.score - a.score);

        return suggestions;
    }

    /**
     * Suggest alignment for nodes
     */
    private suggestAlignment(
        nodes: UniversalNode[],
        id: string
    ): LayoutSuggestion | null {
        if (nodes.length < 2) return null;

        // Calculate average center Y
        const avgCenterY =
            nodes.reduce((sum, n) => sum + n.transform.y + n.transform.height / 2, 0) / nodes.length;

        const transforms: Record<string, Partial<NodeTransform>> = {};
        const guides: LayoutGuide[] = [];

        let totalAdjustment = 0;

        for (const node of nodes) {
            const currentCenterY = node.transform.y + node.transform.height / 2;
            const adjustment = avgCenterY - currentCenterY;
            totalAdjustment += Math.abs(adjustment);

            if (Math.abs(adjustment) > 1) {
                transforms[node.id] = { y: node.transform.y + adjustment };
            }
        }

        if (Object.keys(transforms).length === 0) return null;

        // Create guide line
        const minX = Math.min(...nodes.map((n) => n.transform.x));
        const maxX = Math.max(...nodes.map((n) => n.transform.x + n.transform.width));

        guides.push({
            type: 'horizontal',
            position: avgCenterY,
            start: minX - 10,
            end: maxX + 10,
            color: '#6366f1',
            dashed: true,
        });

        return {
            id,
            type: 'alignment',
            description: `Align ${nodes.length} elements horizontally`,
            confidence: totalAdjustment < 20 ? 'high' : totalAdjustment < 50 ? 'medium' : 'low',
            score: Math.max(0.3, 1 - totalAdjustment / 100),
            nodeIds: nodes.map((n) => n.id),
            transforms,
            preview: {
                guides,
                highlights: [],
                ghosts: Object.fromEntries(
                    nodes.map((n) => [
                        n.id,
                        { ...n.transform, ...(transforms[n.id] ?? {}) },
                    ])
                ),
            },
        };
    }

    /**
     * Suggest even spacing between nodes
     */
    private suggestEvenSpacing(
        nodes: UniversalNode[],
        id: string
    ): LayoutSuggestion | null {
        if (nodes.length < 3) return null;

        const sorted = [...nodes].sort((a, b) => a.transform.x - b.transform.x);

        // Calculate total available space
        const totalWidth = sorted.reduce((sum, n) => sum + n.transform.width, 0);
        const bounds = this.calculateGroupBounds(sorted);
        const availableSpace = bounds.width - totalWidth;
        const idealGap = availableSpace / (sorted.length - 1);

        if (idealGap < 0) return null; // Overlapping, different fix needed

        const transforms: Record<string, Partial<NodeTransform>> = {};
        let currentX = sorted[0].transform.x;

        for (let i = 0; i < sorted.length; i++) {
            const node = sorted[i];
            if (i > 0) {
                currentX += idealGap;
            }

            const adjustment = currentX - node.transform.x;
            if (Math.abs(adjustment) > 1) {
                transforms[node.id] = { x: currentX };
            }

            currentX += node.transform.width;
        }

        if (Object.keys(transforms).length === 0) return null;

        return {
            id,
            type: 'spacing',
            description: `Distribute ${nodes.length} elements with even spacing`,
            confidence: 'high',
            score: 0.8,
            nodeIds: nodes.map((n) => n.id),
            transforms,
        };
    }

    /**
     * Suggest distribution (centering within bounds)
     */
    private suggestDistribution(
        nodes: UniversalNode[],
        id: string
    ): LayoutSuggestion | null {
        if (nodes.length < 2) return null;

        // Find the bounding box center
        const bounds = this.calculateGroupBounds(nodes);
        const centerX = bounds.x + bounds.width / 2;
        const centerY = bounds.y + bounds.height / 2;

        // Calculate group content center
        const contentCenterX =
            nodes.reduce((sum, n) => sum + n.transform.x + n.transform.width / 2, 0) / nodes.length;
        const contentCenterY =
            nodes.reduce((sum, n) => sum + n.transform.y + n.transform.height / 2, 0) / nodes.length;

        const offsetX = centerX - contentCenterX;
        const offsetY = centerY - contentCenterY;

        if (Math.abs(offsetX) < 5 && Math.abs(offsetY) < 5) return null;

        const transforms: Record<string, Partial<NodeTransform>> = {};
        for (const node of nodes) {
            transforms[node.id] = {
                x: node.transform.x + offsetX,
                y: node.transform.y + offsetY,
            };
        }

        return {
            id,
            type: 'distribution',
            description: 'Center elements within their bounds',
            confidence: 'medium',
            score: 0.6,
            nodeIds: nodes.map((n) => n.id),
            transforms,
        };
    }

    // ============================================================================
    // Smart Snapping
    // ============================================================================

    /**
     * Get snap targets for a dragging node
     */
    public getSnapTargets(
        draggingNode: UniversalNode,
        otherNodes: UniversalNode[],
        currentPosition: { x: number; y: number }
    ): SnapTarget[] {
        const targets: SnapTarget[] = [];

        if (!this.smartSnapEnabled) {
            // Only grid snap
            return this.getGridSnapTargets(currentPosition);
        }

        const dragBounds = {
            x: currentPosition.x,
            y: currentPosition.y,
            width: draggingNode.transform.width,
            height: draggingNode.transform.height,
            centerX: currentPosition.x + draggingNode.transform.width / 2,
            centerY: currentPosition.y + draggingNode.transform.height / 2,
            right: currentPosition.x + draggingNode.transform.width,
            bottom: currentPosition.y + draggingNode.transform.height,
        };

        // Check alignment with other nodes
        for (const node of otherNodes) {
            if (node.id === draggingNode.id) continue;

            const nodeBounds = {
                x: node.transform.x,
                y: node.transform.y,
                centerX: node.transform.x + node.transform.width / 2,
                centerY: node.transform.y + node.transform.height / 2,
                right: node.transform.x + node.transform.width,
                bottom: node.transform.y + node.transform.height,
            };

            // Left edge alignment
            const leftDist = Math.abs(dragBounds.x - nodeBounds.x);
            if (leftDist < this.snapThreshold) {
                targets.push({
                    type: 'node',
                    axis: 'x',
                    position: { x: nodeBounds.x },
                    distance: leftDist,
                    referenceNodeId: node.id,
                });
            }

            // Right edge alignment
            const rightDist = Math.abs(dragBounds.right - nodeBounds.right);
            if (rightDist < this.snapThreshold) {
                targets.push({
                    type: 'node',
                    axis: 'x',
                    position: { x: nodeBounds.right - draggingNode.transform.width },
                    distance: rightDist,
                    referenceNodeId: node.id,
                });
            }

            // Center X alignment
            const centerXDist = Math.abs(dragBounds.centerX - nodeBounds.centerX);
            if (centerXDist < this.snapThreshold) {
                targets.push({
                    type: 'center',
                    axis: 'x',
                    position: { x: nodeBounds.centerX - draggingNode.transform.width / 2 },
                    distance: centerXDist,
                    referenceNodeId: node.id,
                });
            }

            // Top edge alignment
            const topDist = Math.abs(dragBounds.y - nodeBounds.y);
            if (topDist < this.snapThreshold) {
                targets.push({
                    type: 'node',
                    axis: 'y',
                    position: { y: nodeBounds.y },
                    distance: topDist,
                    referenceNodeId: node.id,
                });
            }

            // Bottom edge alignment
            const bottomDist = Math.abs(dragBounds.bottom - nodeBounds.bottom);
            if (bottomDist < this.snapThreshold) {
                targets.push({
                    type: 'node',
                    axis: 'y',
                    position: { y: nodeBounds.bottom - draggingNode.transform.height },
                    distance: bottomDist,
                    referenceNodeId: node.id,
                });
            }

            // Center Y alignment
            const centerYDist = Math.abs(dragBounds.centerY - nodeBounds.centerY);
            if (centerYDist < this.snapThreshold) {
                targets.push({
                    type: 'center',
                    axis: 'y',
                    position: { y: nodeBounds.centerY - draggingNode.transform.height / 2 },
                    distance: centerYDist,
                    referenceNodeId: node.id,
                });
            }
        }

        // Add grid snap targets
        targets.push(...this.getGridSnapTargets(currentPosition));

        // Sort by distance
        targets.sort((a, b) => a.distance - b.distance);

        return targets;
    }

    /**
     * Get grid snap targets
     */
    private getGridSnapTargets(position: { x: number; y: number }): SnapTarget[] {
        const snappedX = Math.round(position.x / this.gridSize) * this.gridSize;
        const snappedY = Math.round(position.y / this.gridSize) * this.gridSize;

        const targets: SnapTarget[] = [];

        const xDist = Math.abs(position.x - snappedX);
        if (xDist < this.snapThreshold) {
            targets.push({
                type: 'grid',
                axis: 'x',
                position: { x: snappedX },
                distance: xDist,
            });
        }

        const yDist = Math.abs(position.y - snappedY);
        if (yDist < this.snapThreshold) {
            targets.push({
                type: 'grid',
                axis: 'y',
                position: { y: snappedY },
                distance: yDist,
            });
        }

        return targets;
    }

    /**
     * Apply snap to position
     */
    public applySnap(
        position: { x: number; y: number },
        targets: SnapTarget[]
    ): { x: number; y: number; snappedTo: SnapTarget[] } {
        let { x, y } = position;
        const snappedTo: SnapTarget[] = [];

        // Find best X snap
        const xTargets = targets.filter((t) => t.axis === 'x' || t.axis === 'both');
        if (xTargets.length > 0) {
            const bestX = xTargets[0];
            if (bestX.position.x !== undefined) {
                x = bestX.position.x;
                snappedTo.push(bestX);
            }
        }

        // Find best Y snap
        const yTargets = targets.filter((t) => t.axis === 'y' || t.axis === 'both');
        if (yTargets.length > 0) {
            const bestY = yTargets[0];
            if (bestY.position.y !== undefined) {
                y = bestY.position.y;
                snappedTo.push(bestY);
            }
        }

        return { x, y, snappedTo };
    }

    // ============================================================================
    // Smart Positioning
    // ============================================================================

    /**
     * Suggest position for a new node
     */
    public suggestNewNodePosition(
        newNodeSize: { width: number; height: number },
        existingNodes: UniversalNode[],
        viewportBounds: { x: number; y: number; width: number; height: number }
    ): { x: number; y: number } {
        if (existingNodes.length === 0) {
            // Center in viewport
            return {
                x: viewportBounds.x + viewportBounds.width / 2 - newNodeSize.width / 2,
                y: viewportBounds.y + viewportBounds.height / 2 - newNodeSize.height / 2,
            };
        }

        // Find the last added node (assuming it's the most recent focus)
        const lastNode = existingNodes[existingNodes.length - 1];

        // Try to place to the right
        let suggestedX = lastNode.transform.x + lastNode.transform.width + 20;
        let suggestedY = lastNode.transform.y;

        // Check if it fits in viewport
        if (suggestedX + newNodeSize.width > viewportBounds.x + viewportBounds.width) {
            // Try below instead
            suggestedX = lastNode.transform.x;
            suggestedY = lastNode.transform.y + lastNode.transform.height + 20;
        }

        // Snap to grid
        suggestedX = Math.round(suggestedX / this.gridSize) * this.gridSize;
        suggestedY = Math.round(suggestedY / this.gridSize) * this.gridSize;

        // Ensure no overlap
        let attempts = 0;
        while (
            attempts < 10 &&
            existingNodes.some((n) =>
                this.nodesOverlap(n, {
                    ...n,
                    transform: {
                        x: suggestedX,
                        y: suggestedY,
                        width: newNodeSize.width,
                        height: newNodeSize.height,
                    },
                } as UniversalNode)
            )
        ) {
            suggestedY += this.gridSize;
            attempts++;
        }

        return { x: suggestedX, y: suggestedY };
    }
}

/**
 * Get singleton instance
 */
export function getPredictiveLayout(): PredictiveLayout {
    return PredictiveLayout.getInstance();
}

export default PredictiveLayout;
