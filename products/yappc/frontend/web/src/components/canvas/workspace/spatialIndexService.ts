/**
 * Spatial Index Service
 * 
 * Off-thread RBush spatial indexing via Web Worker for collision detection
 * and alignment snapping. Includes timeout, error handling, and cleanup.
 * 
 * @doc.type module
 * @doc.purpose Off-thread spatial indexing for canvas
 * @doc.layer product
 * @doc.pattern Worker Bridge
 */

import type { Node } from '@xyflow/react';
import type { SpatialItem } from './spatial.worker';

const WORKER_TIMEOUT_MS = 5000;

let nextMsgId = 1;
const resolvers = new Map<number, { resolve: (val: unknown) => void; reject: (err: Error) => void; timer: ReturnType<typeof setTimeout> }>();
let worker: Worker | null = null;

if (typeof window !== 'undefined') {
    worker = new Worker(new URL('./spatial.worker.ts', import.meta.url), { type: 'module' });

    worker.onmessage = (event) => {
        const { type, msgId, payload } = event.data;
        if (!msgId) return;

        const p = resolvers.get(msgId);
        if (p) {
            clearTimeout(p.timer);
            resolvers.delete(msgId);
            if (type === 'ERROR') {
                p.reject(new Error(String(payload)));
            } else {
                p.resolve(payload);
            }
        }
    };

    worker.onerror = (event) => {
        // Reject all pending queries on worker crash
        for (const [id, { reject, timer }] of resolvers) {
            clearTimeout(timer);
            reject(new Error(`Spatial worker error: ${event.message}`));
        }
        resolvers.clear();
    };
}

const sendToWorker = <T>(type: string, payload: unknown): Promise<T> => {
    if (!worker) return Promise.resolve(null as T);
    return new Promise<T>((resolve, reject) => {
        const id = nextMsgId++;
        const timer = setTimeout(() => {
            resolvers.delete(id);
            reject(new Error(`Spatial worker timeout after ${WORKER_TIMEOUT_MS}ms`));
        }, WORKER_TIMEOUT_MS);

        resolvers.set(id, {
            resolve: resolve as (val: unknown) => void,
            reject,
            timer,
        });
        worker!.postMessage({ type, payload, msgId: id });
    });
};

function nodeToItem(n: Node): SpatialItem | null {
    // Skip nodes whose dimensions haven't been measured by the DOM yet.
    // Using fallback dimensions causes systematically wrong spatial queries
    // on the first render cycle before ResizeObserver fires.
    const w = n.measured?.width;
    const h = n.measured?.height;
    if (!w || !h) return null;

    return {
        id: n.id,
        minX: n.position.x,
        minY: n.position.y,
        maxX: n.position.x + w,
        maxY: n.position.y + h,
    };
}

/**
 * Spatial Index API bridging Jotai and WebWorker RBush instances.
 *
 * @doc.type service
 * @doc.purpose Spatial query bridge for canvas nodes
 * @doc.layer product
 * @doc.pattern Proxy
 */
export const spatialIndexAPI = {
    buildIndex: async (nodes: Node[]): Promise<void> => {
        const items = nodes.map(nodeToItem).filter((x): x is SpatialItem => x !== null);
        await sendToWorker<void>('BUILD', items);
    },

    findCollisions: async (node: Node, searchRadius: number = 0): Promise<SpatialItem[]> => {
        const item = nodeToItem(node);
        if (!item) return [];
        return sendToWorker<SpatialItem[]>('SEARCH', { item, radius: searchRadius });
    },

    /** Insert a single node without full rebuild */
    insertNode: async (node: Node): Promise<void> => {
        const item = nodeToItem(node);
        if (!item) return; // unmeasured — skip until ResizeObserver fires
        await sendToWorker<void>('INSERT', item);
    },

    /** Remove a single node without full rebuild */
    removeNode: async (nodeId: string): Promise<void> => {
        await sendToWorker<void>('REMOVE', nodeId);
    },

    /**
     * Incrementally sync the spatial index with the minimum necessary operations.
     * Replaces the full-rebuild `buildIndex` call during normal canvas updates.
     * Safe to call on every `nodes` atom change — only sends the diff.
     */
    syncNodes: async (
        added: Node[],
        removed: string[],
        moved: Node[],
    ): Promise<void> => {
        const ops: Promise<void>[] = [
            ...added.map((n) => spatialIndexAPI.insertNode(n)),
            ...removed.map((id) => spatialIndexAPI.removeNode(id)),
            // For moved nodes: remove the old bounding box first, then insert new
            ...moved.flatMap((n) => [
                spatialIndexAPI.removeNode(n.id),
                spatialIndexAPI.insertNode(n),
            ]),
        ];
        await Promise.all(ops);
    },

    /** Clean up worker resources */
    destroy: () => {
        for (const [, { reject, timer }] of resolvers) {
            clearTimeout(timer);
            reject(new Error('Spatial index service destroyed'));
        }
        resolvers.clear();
        worker?.terminate();
        worker = null;
    },
};