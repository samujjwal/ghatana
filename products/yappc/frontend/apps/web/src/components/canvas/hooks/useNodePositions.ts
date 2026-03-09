/**
 * Node Position Persistence Hook
 *
 * Solves the dual data-model problem: server artifact refetches regenerate
 * node objects, which would stomp user-dragged positions. This hook:
 *
 *   1. Loads saved positions from localStorage on mount
 *   2. Merges saved positions into generated nodes at render time
 *   3. Persists updated positions to localStorage on drag stop
 *
 * Integration in CanvasWorkspace:
 *   const { mergePositions } = useNodePositions(projectId);
 *   const stableNodes = useMemo(() => mergePositions(generatedNodes), [generatedNodes, mergePositions]);
 *
 * @doc.type hook
 * @doc.purpose Persists user-dragged node positions across server refetches
 * @doc.layer product
 * @doc.pattern Repository, Position Persistence
 */

import { useCallback, useEffect } from 'react';
import { useAtom } from 'jotai';
import { type Node } from '@xyflow/react';
import { nodePositionsAtom, type NodePosition } from '../workspace';
import type { ArtifactNodeData } from '../nodes/ArtifactNode';

// ============================================================================
// Storage schema — versioned to enable safe migrations
// ============================================================================

/** Current storage format version. Increment when the shape changes. */
const STORE_VERSION = 1 as const;

/**
 * Versioned envelope stored in localStorage.
 *
 * Adding `_v` allows future code to detect and migrate legacy data:
 *   - v1 (current): `{ _v: 1, positions: Record<nodeId, {x,y}> }`
 *   - legacy (pre-v1): plain `Record<nodeId, {x,y}>` — migrated transparently
 *
 * @doc.type interface
 * @doc.purpose Versioned localStorage envelope for position persistence
 * @doc.layer product
 * @doc.pattern SchemaVersioning
 */
interface NodePositionStore {
    _v: typeof STORE_VERSION;
    positions: Record<string, NodePosition>;
}

/** Key written to localStorage. The 'v2' suffix prevents collisions with the
 *  old unversioned format (which used 'v1' in the key itself). */
const STORAGE_KEY_PREFIX = 'canvas-positions-store-v2-';
/** Legacy key prefix — used only for one-time migration on first load. */
const LEGACY_KEY_PREFIX = 'canvas-positions-v1-';

/**
 * Upgrade legacy (pre-versioning) stored data to the current format.
 * Returns `null` if no legacy data exists for this project.
 */
function migrateLegacyStore(
    legacyKey: string,
): NodePositionStore | null {
    try {
        const raw = localStorage.getItem(legacyKey);
        if (!raw) return null;
        const legacy = JSON.parse(raw) as Record<string, NodePosition>;
        // Basic shape check before trusting the data
        if (typeof legacy !== 'object' || Array.isArray(legacy)) return null;
        return { _v: STORE_VERSION, positions: legacy };
    } catch {
        return null;
    }
}

/** Deserialize and validate a position store from localStorage. */
function loadStore(key: string): Record<string, NodePosition> {
    try {
        const raw = localStorage.getItem(key);
        if (!raw) return {};
        const parsed = JSON.parse(raw) as NodePositionStore;
        // Must have _v field matching current version
        if (!parsed || parsed._v !== STORE_VERSION) return {};
        return parsed.positions ?? {};
    } catch {
        return {};
    }
}

/** Serialize and write a position store to localStorage. */
function saveStore(key: string, positions: Record<string, NodePosition>): void {
    try {
        const store: NodePositionStore = { _v: STORE_VERSION, positions };
        localStorage.setItem(key, JSON.stringify(store));
    } catch {
        // Storage quota exceeded — positions survive in memory for this session
    }
}

export function useNodePositions(projectId: string) {
    const [positions, setPositions] = useAtom(nodePositionsAtom);
    const storageKey = `${STORAGE_KEY_PREFIX}${projectId}`;
    const legacyKey = `${LEGACY_KEY_PREFIX}${projectId}`;

    // Load from localStorage on mount — with one-time legacy migration
    useEffect(() => {
        let loaded = loadStore(storageKey);

        if (Object.keys(loaded).length === 0) {
            // No versioned store found — attempt one-time migration from legacy format
            const migrated = migrateLegacyStore(legacyKey);
            if (migrated) {
                loaded = migrated.positions;
                // Persist in new versioned format and remove old key
                saveStore(storageKey, loaded);
                try { localStorage.removeItem(legacyKey); } catch { /* ignore */ }
            }
        }

        if (Object.keys(loaded).length > 0) {
            setPositions((prev) => ({ ...loaded, ...prev })); // in-memory wins over disk
        }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [projectId]);

    /**
     * Persist a position update for a single node.
     * Called from onNodeDragStop.
     */
    const persistPosition = useCallback(
        (nodeId: string, x: number, y: number) => {
            setPositions((prev) => {
                const updated = { ...prev, [nodeId]: { x, y } };
                saveStore(storageKey, updated);
                return updated;
            });
        },
        [setPositions, storageKey],
    );

    /**
     * Persist positions for multiple nodes in a single atomic write.
     * Use this instead of calling persistPosition() N times after a multi-node
     * drag — saves N localStorage writes per drag-stop down to exactly one.
     */
    const persistPositions = useCallback(
        (updates: Record<string, NodePosition>) => {
            if (Object.keys(updates).length === 0) return;
            setPositions((prev) => {
                const updated = { ...prev, ...updates };
                saveStore(storageKey, updated);
                return updated;
            });
        },
        [setPositions, storageKey],
    );

    /**
     * Merge saved positions into server-generated node objects.
     * If a node has a saved position it overrides the server-computed layout.
     * Nodes without a saved position keep their generated position.
     */
    const mergePositions = useCallback(
        (nodes: Node<ArtifactNodeData>[]): Node<ArtifactNodeData>[] =>
            nodes.map((node) => {
                const saved = positions[node.id];
                if (saved) {
                    return { ...node, position: saved };
                }
                return node;
            }),
        [positions],
    );

    /**
     * Clear all saved positions for this project (e.g. "Reset Layout").
     */
    const clearPositions = useCallback(() => {
        setPositions({});
        try {
            localStorage.removeItem(storageKey);
            localStorage.removeItem(legacyKey); // also clear legacy key if still present
        } catch { /* ignore */ }
    }, [setPositions, storageKey, legacyKey]);

    return { positions, persistPosition, persistPositions, mergePositions, clearPositions };
}
