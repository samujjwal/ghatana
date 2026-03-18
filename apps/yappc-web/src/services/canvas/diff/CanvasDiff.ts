/**
 * Canvas Diff Utility
 * 
 * Computes differences between two canvas states.
 * Used for version comparison and conflict visualization.
 * 
 * @doc.type service
 * @doc.purpose Canvas state diffing algorithm
 * @doc.layer product
 * @doc.pattern Algorithm
 */

import type { CanvasState, CanvasElement, CanvasConnection } from '../../../components/canvas/workspace/canvasAtoms';
import type { CanvasSnapshot } from '../CanvasPersistence';

export interface DiffResult {
    added: {
        nodes: CanvasElement[];
        edges: CanvasConnection[];
    };
    removed: {
        nodes: CanvasElement[];
        edges: CanvasConnection[];
    };
    modified: {
        nodes: ModifiedNode[];
        edges: ModifiedEdge[];
    };
    unchanged: {
        nodes: CanvasElement[];
        edges: CanvasConnection[];
    };
}

export interface ModifiedNode {
    id: string;
    before: CanvasElement;
    after: CanvasElement;
    changes: Change[];
}

export interface ModifiedEdge {
    id: string;
    before: CanvasConnection;
    after: CanvasConnection;
    changes: Change[];
}

export interface Change {
    field: string;
    before: unknown;
    after: unknown;
    type: 'position' | 'data' | 'style' | 'other';
}

/**
 * Compute diff between two canvas states
 */
export function diffCanvasStates(before: CanvasState, after: CanvasState): DiffResult {
    const result: DiffResult = {
        added: { nodes: [], edges: [] },
        removed: { nodes: [], edges: [] },
        modified: { nodes: [], edges: [] },
        unchanged: { nodes: [], edges: [] },
    };

    // Diff nodes
    const beforeNodes = new Map(before.elements.map(n => [n.id, n]));
    const afterNodes = new Map(after.elements.map(n => [n.id, n]));

    // Find added nodes
    for (const [id, node] of afterNodes) {
        if (!beforeNodes.has(id)) {
            result.added.nodes.push(node);
        }
    }

    // Find removed and modified nodes
    for (const [id, beforeNode] of beforeNodes) {
        const afterNode = afterNodes.get(id);

        if (!afterNode) {
            result.removed.nodes.push(beforeNode);
        } else {
            const changes = detectNodeChanges(beforeNode, afterNode);
            if (changes.length > 0) {
                result.modified.nodes.push({
                    id,
                    before: beforeNode,
                    after: afterNode,
                    changes,
                });
            } else {
                result.unchanged.nodes.push(afterNode);
            }
        }
    }

    // Diff edges
    const beforeEdges = new Map(before.connections.map(e => [e.id, e]));
    const afterEdges = new Map(after.connections.map(e => [e.id, e]));

    // Find added edges
    for (const [id, edge] of afterEdges) {
        if (!beforeEdges.has(id)) {
            result.added.edges.push(edge);
        }
    }

    // Find removed and modified edges
    for (const [id, beforeEdge] of beforeEdges) {
        const afterEdge = afterEdges.get(id);

        if (!afterEdge) {
            result.removed.edges.push(beforeEdge);
        } else {
            const changes = detectEdgeChanges(beforeEdge, afterEdge);
            if (changes.length > 0) {
                result.modified.edges.push({
                    id,
                    before: beforeEdge,
                    after: afterEdge,
                    changes,
                });
            } else {
                result.unchanged.edges.push(afterEdge);
            }
        }
    }

    return result;
}

/**
 * Detect changes in a node
 */
function detectNodeChanges(before: CanvasElement, after: CanvasElement): Change[] {
    const changes: Change[] = [];

    // Check position
    if (before.position.x !== after.position.x || before.position.y !== after.position.y) {
        changes.push({
            field: 'position',
            before: before.position,
            after: after.position,
            type: 'position',
        });
    }

    // Check type
    if (before.type !== after.type) {
        changes.push({
            field: 'type',
            before: before.type,
            after: after.type,
            type: 'other',
        });
    }

    // Check data
    const dataDiff = detectObjectChanges('data', before.data, after.data);
    changes.push(...dataDiff);

    return changes;
}

/**
 * Detect changes in an edge
 */
function detectEdgeChanges(before: CanvasConnection, after: CanvasConnection): Change[] {
    const changes: Change[] = [];

    // Check source/target
    if (before.source !== after.source) {
        changes.push({
            field: 'source',
            before: before.source,
            after: after.source,
            type: 'other',
        });
    }

    if (before.target !== after.target) {
        changes.push({
            field: 'target',
            before: before.target,
            after: after.target,
            type: 'other',
        });
    }

    // Check type
    if (before.type !== after.type) {
        changes.push({
            field: 'type',
            before: before.type,
            after: after.type,
            type: 'other',
        });
    }

    return changes;
}

/**
 * Detect changes in nested objects
 */
function detectObjectChanges(prefix: string, before: unknown, after: unknown): Change[] {
    const changes: Change[] = [];

    if (!before && !after) return changes;
    if (!before || !after) {
        changes.push({
            field: prefix,
            before,
            after,
            type: 'data',
        });
        return changes;
    }

    const allKeys = new Set([...Object.keys(before), ...Object.keys(after)]);

    for (const key of allKeys) {
        const beforeVal = before[key];
        const afterVal = after[key];

        if (JSON.stringify(beforeVal) !== JSON.stringify(afterVal)) {
            changes.push({
                field: `${prefix}.${key}`,
                before: beforeVal,
                after: afterVal,
                type: 'data',
            });
        }
    }

    return changes;
}

/**
 * Compute diff between two snapshots
 */
export function diffSnapshots(before: CanvasSnapshot, after: CanvasSnapshot): DiffResult {
    return diffCanvasStates(before.data, after.data);
}

/**
 * Get summary statistics of a diff
 */
export function getDiffStats(diff: DiffResult) {
    return {
        nodesAdded: diff.added.nodes.length,
        nodesRemoved: diff.removed.nodes.length,
        nodesModified: diff.modified.nodes.length,
        nodesUnchanged: diff.unchanged.nodes.length,
        edgesAdded: diff.added.edges.length,
        edgesRemoved: diff.removed.edges.length,
        edgesModified: diff.modified.edges.length,
        edgesUnchanged: diff.unchanged.edges.length,
        totalChanges:
            diff.added.nodes.length +
            diff.removed.nodes.length +
            diff.modified.nodes.length +
            diff.added.edges.length +
            diff.removed.edges.length +
            diff.modified.edges.length,
    };
}
