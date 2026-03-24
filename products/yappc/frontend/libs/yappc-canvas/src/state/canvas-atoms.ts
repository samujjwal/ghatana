import { atom } from 'jotai';

import type { PortalElement, DrillDownContext } from '../types/portal-types';
import type { Node, Edge, Viewport } from '@xyflow/react';

/**
 *
 */
export interface CanvasSnapshot {
  nodes: Node[];
  edges: Edge[];
  viewport: Viewport;
  timestamp: number;
  label?: string;
}

/**
 *
 */
export interface CanvasHistory {
  [canvasId: string]: {
    nodes: Node[];
    edges: Edge[];
    viewport: Viewport;
  };
}

/**
 *
 */
export interface CanvasState {
  // Current canvas data
  nodes: Node[];
  edges: Edge[];
  viewport: Viewport;

  // Multi-canvas support
  canvasHistory: CanvasHistory;
  portals: { [portalId: string]: PortalElement };

  // Versioning and snapshots
  snapshots: CanvasSnapshot[];
  currentSnapshotIndex: number;

  // Undo/Redo support
  history: CanvasSnapshot[];
  historyIndex: number;
  maxHistorySize: number;

  // Metadata
  lastModified: number;
  canvasId: string;
  name?: string;
  description?: string;
}

// Initial canvas state
const initialCanvasState: CanvasState = {
  nodes: [],
  edges: [],
  viewport: { x: 0, y: 0, zoom: 1 },
  canvasHistory: {},
  portals: {},
  snapshots: [],
  currentSnapshotIndex: -1,
  history: [],
  historyIndex: -1,
  maxHistorySize: 50,
  lastModified: Date.now(),
  canvasId: 'root',
};

// Main canvas state atom
export const canvasStateAtom = atom<CanvasState>(initialCanvasState);

// Derived atoms for specific aspects
export const nodesAtom = atom(
  (get) => get(canvasStateAtom).nodes,
  (get, set, newNodes: Node[]) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      nodes: newNodes,
      lastModified: Date.now()
    });
  }
);

export const edgesAtom = atom(
  (get) => get(canvasStateAtom).edges,
  (get, set, newEdges: Edge[]) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      edges: newEdges,
      lastModified: Date.now()
    });
  }
);

export const cameraAtom = atom(
  (get) => get(canvasStateAtom).viewport,
  (get, set, newViewport: Viewport) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      viewport: newViewport,
      lastModified: Date.now()
    });
  }
);

export const portalsAtom = atom(
  (get) => get(canvasStateAtom).portals,
  (get, set, newPortals: { [portalId: string]: PortalElement }) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      portals: newPortals,
      lastModified: Date.now()
    });
  }
);

export const canvasHistoryAtom = atom(
  (get) => get(canvasStateAtom).canvasHistory,
  (get, set, newHistory: CanvasHistory) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      canvasHistory: newHistory,
      lastModified: Date.now()
    });
  }
);

// Snapshot management atoms
export const snapshotsAtom = atom(
  (get) => get(canvasStateAtom).snapshots,
  (get, set, newSnapshots: CanvasSnapshot[]) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      snapshots: newSnapshots,
      lastModified: Date.now()
    });
  }
);

export const currentSnapshotIndexAtom = atom(
  (get) => get(canvasStateAtom).currentSnapshotIndex,
  (get, set, newIndex: number) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      currentSnapshotIndex: newIndex,
      lastModified: Date.now()
    });
  }
);

// History management atoms for undo/redo
export const historyAtom = atom(
  (get) => get(canvasStateAtom).history,
  (get, set, newHistory: CanvasSnapshot[]) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      history: newHistory,
      lastModified: Date.now()
    });
  }
);

export const historyIndexAtom = atom(
  (get) => get(canvasStateAtom).historyIndex,
  (get, set, newIndex: number) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      historyIndex: newIndex,
      lastModified: Date.now()
    });
  }
);

// Canvas metadata atoms
export const canvasIdAtom = atom(
  (get) => get(canvasStateAtom).canvasId,
  (get, set, newId: string) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      canvasId: newId,
      lastModified: Date.now()
    });
  }
);

export const canvasNameAtom = atom(
  (get) => get(canvasStateAtom).name,
  (get, set, newName: string | undefined) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      name: newName,
      lastModified: Date.now()
    });
  }
);

export const canvasDescriptionAtom = atom(
  (get) => get(canvasStateAtom).description,
  (get, set, newDescription: string | undefined) => {
    set(canvasStateAtom, {
      ...get(canvasStateAtom),
      description: newDescription,
      lastModified: Date.now()
    });
  }
);

// Utility atoms for computed values
export const isCanvasEmptyAtom = atom((get) => {
  const state = get(canvasStateAtom);
  return state.nodes.length === 0 && state.edges.length === 0;
});

export const canCanvasUndoAtom = atom((get) => {
  const state = get(canvasStateAtom);
  return state.historyIndex > 0;
});

export const canCanvasRedoAtom = atom((get) => {
  const state = get(canvasStateAtom);
  return state.historyIndex < state.history.length - 1;
});

export const canvasModifiedAtom = atom((get) => {
  const state = get(canvasStateAtom);
  return state.lastModified;
});

// Reset atoms for testing and initialization
export const resetCanvasStateAtom = atom(null, (get, set) => {
  set(canvasStateAtom, initialCanvasState);
});

export default canvasStateAtom;