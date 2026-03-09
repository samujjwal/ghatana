/**
 * Canonical IDE CRDT types for YAPPC
 *
 * Purpose: Provide a single source of truth for IDE-related CRDT types
 * (IDE CRDT state, file/folder shapes, and lightweight canvas node types)
 */

import * as Y from 'yjs';

// Minimal local CRDT types (kept intentionally lightweight to avoid depending on the full crdt-core during iterative work)
export interface VectorClock {
    id: string;
    values: Map<string, number>;
    timestamp: number;
}

export interface CRDTOperation {
    id: string;
    replicaId: string;
    type: string; // allows transport of both core types and product-specific operations (e.g., 'ide:createFile')
    targetId: string;
    vectorClock: VectorClock;
    data: unknown;
    timestamp: number;
    parents: string[];
}

/**
 * Lightweight Canvas node type used by the IDE bridge (minimal shape)
 */
export interface CanvasNodeLite {
    id: string;
    type: string;
    code?: string; // generated code or snippet
    metadata?: Record<string, unknown>;
    // Common additional fields used by bridge
    position?: { x: number; y: number };
    data?: unknown;
    size?: { width: number; height: number };
    connections?: string[];
}

export interface IDEFileCRDT {
    id: string;
    path: string;
    // collaborative content: Y.Text in real deployments, but allow plain string for test-friendly usage
    content: Y.Text | string | unknown;
    language: string;
    metadata: {
        createdAt: number;
        modifiedAt: number;
        size: number;
        createdBy: string;
        modifiedBy: string;
    };
}

export interface IDEFolderCRDT {
    id: string;
    path: string;
    children: string[];
    metadata: {
        createdAt: number;
        createdBy: string;
    };
}

export interface IDEPresenceCRDT {
    userId: string;
    userName: string;
    userColor: string;
    activeFileId: string | null;
    cursorPosition: { line: number; column: number } | null;
    selection: {
        start: { line: number; column: number };
        end: { line: number; column: number };
    } | null;
    lastActivity: number;
    isOnline: boolean;
}

export interface IDECRDTState {
    canvas: Record<string, unknown> | Y.Map<unknown>;
    files: Record<string, IDEFileCRDT> | Y.Map<unknown>;
    folders: Record<string, IDEFolderCRDT> | Y.Map<unknown>;
    rootFolderId: string | null;
    editorState: Record<string, unknown> | Y.Map<unknown>;
    presence: Record<string, IDEPresenceCRDT> | Y.Map<unknown>;
    settings: Record<string, unknown> | Y.Map<unknown>;
}

export interface IDECRDTStateWrapper {
    id: string;
    replicaId: string;
    state: IDECRDTState;
}

// Helper: convert a plain-record file map to a Y.Map version (for runtime bridging)
export function recordToYMap<T>(record: Record<string, T>, ymap?: Y.Map<unknown>): Y.Map<unknown> {
    const map = ymap || new Y.Map();
    for (const key of Object.keys(record)) {
        map.set(key, record[key] as unknown);
    }
    return map as Y.Map<unknown>;
}

export function yMapToRecord<T>(ymap: Y.Map<unknown>): Record<string, T> {
    const out: Record<string, T> = {};
    ymap.forEach((value: unknown, key: string) => {
        out[key] = value as T;
    });
    return out;
}
