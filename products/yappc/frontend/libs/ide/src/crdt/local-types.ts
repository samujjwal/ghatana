/**
 * Local lightweight CRDT types for the IDE package.
 * These are intentionally minimal to decouple the IDE package's type-check
 * from the full `@ghatana/yappc-crdt-core` build while preserving the shapes used in
 * unit tests and simple integrations.
 */

export interface VectorClock {
    id: string;
    values: Map<string, number>;
    timestamp: number;
}

export interface CRDTOperation {
    id: string;
    replicaId: string;
    type: string;
    targetId: string;
    vectorClock?: VectorClock;
    data?: unknown;
    timestamp: number;
    parents?: string[];
}

// Minimal Canvas node shape used by the bridge
export interface CanvasNodeLite {
    id: string;
    type: string;
    position?: { x: number; y: number };
    size?: { width: number; height: number };
    data?: unknown;
    connections?: unknown[];
}