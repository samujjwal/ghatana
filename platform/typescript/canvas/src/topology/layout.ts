/**
 * Topology Layout Utilities
 *
 * Provides automatic layout algorithms for topology visualizations.
 * Supports dagre (hierarchical), elk (enterprise), and force-directed layouts.
 *
 * @doc.type utility
 * @doc.purpose Automatic layout for topology graphs
 * @doc.layer shared
 * @doc.pattern Utility
 */

import type { Node, Edge } from '@xyflow/react';
import type { LayoutOptions, LayoutResult, LayoutNodePosition } from './types';

// ============================================
// DEFAULT OPTIONS
// ============================================

const DEFAULT_LAYOUT_OPTIONS: LayoutOptions = {
    direction: 'LR',
    nodeSpacing: 100,
    rankSpacing: 200,
    edgeSpacing: 50,
    centerNodes: true,
};

// ============================================
// DAGRE LAYOUT (Hierarchical)
// ============================================

/**
 * Calculate hierarchical layout using a simple dagre-like algorithm.
 *
 * This is a simplified implementation that doesn't require the full dagre library.
 * For production use with complex graphs, consider using dagre-d3 or elkjs.
 *
 * @param nodes - Nodes to layout
 * @param edges - Edges defining connections
 * @param options - Layout options
 * @returns Layout result with positioned nodes
 */
export function calculateDagreLayout<N extends Node, E extends Edge>(
    nodes: N[],
    edges: E[],
    options: Partial<LayoutOptions> = {}
): { nodes: N[]; edges: E[] } {
    const opts = { ...DEFAULT_LAYOUT_OPTIONS, ...options };
    const { direction, nodeSpacing, rankSpacing } = opts;

    // Build adjacency lists
    const outgoing = new Map<string, string[]>();
    const incoming = new Map<string, string[]>();

    nodes.forEach((node) => {
        outgoing.set(node.id, []);
        incoming.set(node.id, []);
    });

    edges.forEach((edge) => {
        outgoing.get(edge.source)?.push(edge.target);
        incoming.get(edge.target)?.push(edge.source);
    });

    // Find root nodes (no incoming edges)
    const rootNodes = nodes.filter((node) => incoming.get(node.id)?.length === 0);

    // If no roots found, use the first node
    const startNodes = rootNodes.length > 0 ? rootNodes : nodes.slice(0, 1);

    // Calculate ranks using BFS
    const ranks = new Map<string, number>();
    const queue: Array<{ id: string; rank: number }> = [];

    startNodes.forEach((node) => {
        ranks.set(node.id, 0);
        queue.push({ id: node.id, rank: 0 });
    });

    while (queue.length > 0) {
        const { id, rank } = queue.shift()!;
        const children = outgoing.get(id) ?? [];

        children.forEach((childId) => {
            const existingRank = ranks.get(childId);
            const newRank = rank + 1;

            if (existingRank === undefined || newRank > existingRank) {
                ranks.set(childId, newRank);
                queue.push({ id: childId, rank: newRank });
            }
        });
    }

    // Handle nodes not reachable from roots
    nodes.forEach((node) => {
        if (!ranks.has(node.id)) {
            ranks.set(node.id, 0);
        }
    });

    // Group nodes by rank
    const rankGroups = new Map<number, string[]>();
    nodes.forEach((node) => {
        const rank = ranks.get(node.id) ?? 0;
        if (!rankGroups.has(rank)) {
            rankGroups.set(rank, []);
        }
        rankGroups.get(rank)!.push(node.id);
    });

    // Calculate positions
    const isHorizontal = direction === 'LR' || direction === 'RL';
    const isReversed = direction === 'RL' || direction === 'BT';

    // Default node dimensions
    const nodeWidth = 180;
    const nodeHeight = 80;

    // Calculate positions for each node
    const positions = new Map<string, { x: number; y: number }>();

    rankGroups.forEach((nodeIds, rank) => {
        const rankPosition = isReversed ? -rank * rankSpacing : rank * rankSpacing;

        nodeIds.forEach((nodeId, index) => {
            const offset = (index - (nodeIds.length - 1) / 2) * (isHorizontal ? nodeHeight + nodeSpacing : nodeWidth + nodeSpacing);

            if (isHorizontal) {
                positions.set(nodeId, {
                    x: rankPosition,
                    y: offset,
                });
            } else {
                positions.set(nodeId, {
                    x: offset,
                    y: rankPosition,
                });
            }
        });
    });

    // Apply positions to nodes
    const positionedNodes = nodes.map((node) => {
        const pos = positions.get(node.id) ?? { x: 0, y: 0 };
        return {
            ...node,
            position: {
                x: pos.x,
                y: pos.y,
            },
        };
    });

    return {
        nodes: positionedNodes,
        edges,
    };
}

// ============================================
// FORCE-DIRECTED LAYOUT
// ============================================

/**
 * Simple force-directed layout simulation.
 *
 * Uses basic spring-embedder physics:
 * - Nodes repel each other
 * - Edges act as springs
 *
 * @param nodes - Nodes to layout
 * @param edges - Edges defining connections
 * @param options - Layout options
 * @param iterations - Number of simulation iterations (default: 100)
 * @returns Layout result with positioned nodes
 */
export function calculateForceLayout<N extends Node, E extends Edge>(
    nodes: N[],
    edges: E[],
    options: Partial<LayoutOptions> = {},
    iterations = 100
): { nodes: N[]; edges: E[] } {
    const opts = { ...DEFAULT_LAYOUT_OPTIONS, ...options };
    const { nodeSpacing } = opts;

    if (nodes.length === 0) {
        return { nodes, edges };
    }

    // Initialize positions in a circle
    const centerX = 0;
    const centerY = 0;
    const radius = nodeSpacing * Math.sqrt(nodes.length);

    const positions = new Map<string, { x: number; y: number; vx: number; vy: number }>();

    nodes.forEach((node, index) => {
        const angle = (2 * Math.PI * index) / nodes.length;
        positions.set(node.id, {
            x: node.position?.x ?? centerX + radius * Math.cos(angle),
            y: node.position?.y ?? centerY + radius * Math.sin(angle),
            vx: 0,
            vy: 0,
        });
    });

    // Create edge lookup
    const edgeSet = new Set(edges.map((e) => `${e.source}-${e.target}`));
    const isConnected = (a: string, b: string) =>
        edgeSet.has(`${a}-${b}`) || edgeSet.has(`${b}-${a}`);

    // Simulation parameters
    const repulsionStrength = nodeSpacing * nodeSpacing * 100;
    const springStrength = 0.1;
    const damping = 0.9;
    const minDistance = 50;

    // Run simulation
    for (let iter = 0; iter < iterations; iter++) {
        const temperature = 1 - iter / iterations;

        // Calculate forces
        nodes.forEach((nodeA) => {
            const posA = positions.get(nodeA.id)!;
            let fx = 0;
            let fy = 0;

            // Repulsion from other nodes
            nodes.forEach((nodeB) => {
                if (nodeA.id === nodeB.id) return;

                const posB = positions.get(nodeB.id)!;
                const dx = posA.x - posB.x;
                const dy = posA.y - posB.y;
                const distance = Math.max(Math.sqrt(dx * dx + dy * dy), minDistance);

                const force = repulsionStrength / (distance * distance);
                fx += (dx / distance) * force;
                fy += (dy / distance) * force;
            });

            // Spring attraction for connected nodes
            nodes.forEach((nodeB) => {
                if (nodeA.id === nodeB.id) return;
                if (!isConnected(nodeA.id, nodeB.id)) return;

                const posB = positions.get(nodeB.id)!;
                const dx = posB.x - posA.x;
                const dy = posB.y - posA.y;
                const distance = Math.sqrt(dx * dx + dy * dy);

                const force = springStrength * (distance - nodeSpacing);
                fx += (dx / distance) * force;
                fy += (dy / distance) * force;
            });

            // Update velocity with temperature cooling
            posA.vx = (posA.vx + fx) * damping * temperature;
            posA.vy = (posA.vy + fy) * damping * temperature;
        });

        // Apply velocities
        nodes.forEach((node) => {
            const pos = positions.get(node.id)!;
            pos.x += pos.vx;
            pos.y += pos.vy;
        });
    }

    // Apply positions to nodes
    const positionedNodes = nodes.map((node) => {
        const pos = positions.get(node.id)!;
        return {
            ...node,
            position: {
                x: pos.x,
                y: pos.y,
            },
        };
    });

    return {
        nodes: positionedNodes,
        edges,
    };
}

// ============================================
// AUTO LAYOUT HOOK
// ============================================

/**
 * Auto-layout nodes and edges based on algorithm type.
 *
 * @param nodes - Nodes to layout
 * @param edges - Edges defining connections
 * @param algorithm - Layout algorithm to use
 * @param options - Layout options
 * @returns Positioned nodes and edges
 */
export function autoLayout<N extends Node, E extends Edge>(
    nodes: N[],
    edges: E[],
    algorithm: 'dagre' | 'elk' | 'force' | 'manual' = 'dagre',
    options: Partial<LayoutOptions> = {}
): { nodes: N[]; edges: E[] } {
    switch (algorithm) {
        case 'force':
            return calculateForceLayout(nodes, edges, options);
        case 'manual':
            return { nodes, edges };
        case 'elk':
        // ELK layout not implemented yet, fall through to dagre
        // TODO: Implement ELK layout when elkjs is added
        case 'dagre':
        default:
            return calculateDagreLayout(nodes, edges, options);
    }
}

// ============================================
// LAYOUT HELPERS
// ============================================

/**
 * Center nodes in the viewport.
 *
 * @param nodes - Nodes to center
 * @returns Centered nodes
 */
export function centerNodes<N extends Node>(nodes: N[]): N[] {
    if (nodes.length === 0) return nodes;

    // Find bounds
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;

    nodes.forEach((node) => {
        minX = Math.min(minX, node.position?.x ?? 0);
        maxX = Math.max(maxX, node.position?.x ?? 0);
        minY = Math.min(minY, node.position?.y ?? 0);
        maxY = Math.max(maxY, node.position?.y ?? 0);
    });

    // Calculate offset to center
    const centerX = (minX + maxX) / 2;
    const centerY = (minY + maxY) / 2;

    return nodes.map((node) => ({
        ...node,
        position: {
            x: (node.position?.x ?? 0) - centerX,
            y: (node.position?.y ?? 0) - centerY,
        },
    }));
}

/**
 * Fit nodes to a target size.
 *
 * @param nodes - Nodes to fit
 * @param width - Target width
 * @param height - Target height
 * @param padding - Padding from edges
 * @returns Scaled and centered nodes
 */
export function fitNodesToSize<N extends Node>(
    nodes: N[],
    width: number,
    height: number,
    padding = 50
): N[] {
    if (nodes.length === 0) return nodes;

    // Find bounds
    let minX = Infinity;
    let maxX = -Infinity;
    let minY = Infinity;
    let maxY = -Infinity;

    nodes.forEach((node) => {
        minX = Math.min(minX, node.position?.x ?? 0);
        maxX = Math.max(maxX, node.position?.x ?? 0);
        minY = Math.min(minY, node.position?.y ?? 0);
        maxY = Math.max(maxY, node.position?.y ?? 0);
    });

    const graphWidth = maxX - minX || 1;
    const graphHeight = maxY - minY || 1;

    const targetWidth = width - padding * 2;
    const targetHeight = height - padding * 2;

    const scale = Math.min(targetWidth / graphWidth, targetHeight / graphHeight, 1);

    return nodes.map((node) => ({
        ...node,
        position: {
            x: ((node.position?.x ?? 0) - minX) * scale + padding,
            y: ((node.position?.y ?? 0) - minY) * scale + padding,
        },
    }));
}
