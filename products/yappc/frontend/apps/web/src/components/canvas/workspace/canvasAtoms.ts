/**
 * Canvas State Management with Jotai
 *
 * Single authoritative source of truth for the canvas-first workspace.
 * All camera, selection, history, command-registry, and code-associations
 * state lives here. No parallel state systems allowed.
 *
 * Architecture decisions:
 *  - cameraAtom: replaces stale viewportAtom + local useState pattern
 *  - commandRegistryAtom: enables extensible CommandPalette + plugins
 *  - nodePositionsAtom: persists user-dragged positions separately from
 *    server artifact model, so server refetches never stomp positions
 *  - codeAssociationsAtom: batch-loaded at workspace level to eliminate N+1
 *
 * @doc.type module
 * @doc.purpose Jotai atoms for canvas state
 * @doc.layer product
 * @doc.pattern State Management
 */

import { atom } from 'jotai';
import { atomFamily } from 'jotai/utils';
import type React from 'react';
import { type Node, type Edge } from '@xyflow/react';
import type { ArtifactNodeData } from '../nodes/ArtifactNode';
import type { DependencyEdgeData } from '../edges';
import type { InspectorArtifact } from './InspectorPanel';
import type { ArtifactTemplate } from './ArtifactPalette';

// ============================================================================
// Types
// ============================================================================

/**
 * A command action entry for the command palette and plugin system.
 * Commands are registered at workspace level and rendered/searched by
 * CommandPalette. Plugins can push their own commands at mount.
 */
export interface CanvasCommandAction {
    /** Stable unique ID used to deduplicate registrations */
    id: string;
    /** Human-readable label shown in the command palette */
    label: string;
    /** Optional keyboard shortcut hint shown in palette */
    shortcut?: string;
    /** Optional grouping label */
    group?: string;
    /** Optional Lucide icon component */
    icon?: React.ReactNode;
    /** Callback executed when the command is triggered */
    onExecute: () => void;
}

/**
 * A persisted user-dragged position for a node.
 * Stored separately from the artifact model to survive server refetches.
 */
export interface NodePosition {
    x: number;
    y: number;
}

/**
 * A code link between a canvas artifact and a code artifact.
 */
export interface CodeLink {
    id: string;
    codeArtifactId: string;
    relationship: 'IMPLEMENTATION' | 'TEST' | 'DOCUMENTATION' | 'MOCK';
    file?: string;
    line?: number;
}

// ============================================================================
// Constants
// ============================================================================

/** Maximum undo/redo history entries to prevent memory leaks */
export const MAX_HISTORY_SIZE = 100;

/** Shared zone center positions for phase navigation */
export const PHASE_ZONE_CENTERS: Record<string, { x: number; y: number }> = {
    INTENT: { x: 400, y: 300 },
    SHAPE: { x: 1250, y: 300 },
    VALIDATE: { x: 2200, y: 300 },
    GENERATE: { x: 3100, y: 300 },
    RUN: { x: 3900, y: 300 },
    OBSERVE: { x: 4550, y: 300 },
    IMPROVE: { x: 5300, y: 300 },
};

/**
 * Canvas UI State Atoms
 */

/** Active persona for filtering (null = show all) */
export const activePersonaAtom = atom<string | null>(null);

/** AI modal visibility state */
export const isAIModalOpenAtom = atom(false);

/** Project switcher modal visibility */
export const isProjectSwitcherOpenAtom = atom(false);

/** Inspector panel visibility and selected artifact */
export const isInspectorOpenAtom = atom(false);
export const selectedArtifactAtom = atom<InspectorArtifact | null>(null);

/**
 * Canvas Interaction State Atoms
 */

/** Selected nodes in the canvas */
export const selectedNodesAtom = atom<string[]>([]);

/** Quick create menu position (null = hidden) */
export const quickCreateMenuPositionAtom = atom<{ x: number; y: number } | null>(null);

/** Currently dragged artifact template */
export const draggedTemplateAtom = atom<ArtifactTemplate | null>(null);

/**
 * ReactFlow v12 requires node/edge data types to satisfy Record<string, unknown>.
 * We use these branded aliases at the atom layer to satisfy the constraint
 * without polluting the component-level data interfaces with an index signature.
 */
export type ArtifactNodeDataR = ArtifactNodeData & Record<string, unknown>;
export type DependencyEdgeDataR = DependencyEdgeData & Record<string, unknown>;

/** Copied nodes for paste operation */
export const copiedNodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);

/**
 * ReactFlow Graph State Atoms
 * 
 * These atoms hold the actual graph data that ReactFlow uses.
 * They're separate from ReactFlow's internal zustand store to maintain
 * a single source of truth in Jotai.
 */

/** All nodes in the canvas */
export const nodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);

/** All edges between nodes */
export const edgesAtom = atom<Edge<DependencyEdgeDataR>[]>([]);

/**
 * When true, suppress automatic syncing of generated nodes from the
 * artifact source into `nodesAtom`. Used by dev/playground to avoid
 * immediate repopulation when the user clears the canvas.
 */
export const suppressGeneratedSyncAtom = atom(false);

/** Currently active task in the workspace */
export const activeTaskAtom = atom<string | null>(null);

/** Transient AI-suggested nodes (not yet persisted) */
export const ghostNodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);

/**
 * Camera State — single authoritative source of truth for the canvas viewport.
 *
 * Replaces the previous dual-state pattern (viewportAtom + local useState in
 * CanvasWorkspace). ReactFlow's `onMove` callback writes here; all consumers
 * subscribe to this atom. Never hold a copy in component state.
 *
 * `initialized` is set to `true` by the ReactFlow `onInit` callback so that
 * culling atoms know the camera has a real viewport (not the default 0,0,1).
 * Before initialization, `visibleNodeIdsAtom` returns ALL nodes, preventing
 * a blank canvas on cold load.
 */
export interface CameraState {
    x: number;
    y: number;
    zoom: number;
    /** True once ReactFlow's onInit has provided the real viewport. */
    initialized: boolean;
}

export const cameraAtom = atom<CameraState>({ x: 0, y: 0, zoom: 1, initialized: false });

/** Convenience derived atom: current zoom level */
export const cameraZoomAtom = atom((get) => get(cameraAtom).zoom);

/** @deprecated Use cameraAtom instead */
export const viewportAtom = cameraAtom;

// ============================================================================
// Viewport Culling — derived atoms for off-screen node filtering
// ============================================================================

/**
 * World-space bounds of the visible viewport, with a generous off-screen
 * margin so nodes that are partially off-screen are never culled prematurely.
 *
 * Derived purely from `cameraAtom`, so it updates on every pan/zoom.
 *
 * @doc.type atom
 * @doc.purpose Viewport bounds for culling
 * @doc.layer product
 * @doc.pattern DerivedAtom
 */
export const visibleViewportAtom = atom((get) => {
    const { x, y, zoom } = get(cameraAtom);
    const MARGIN = 400; // px in world-space — keeps nodes visible slightly off-screen
    const w = typeof window !== 'undefined' ? window.innerWidth : 1920;
    const h = typeof window !== 'undefined' ? window.innerHeight : 1080;
    return {
        x: -x / zoom - MARGIN,
        y: -y / zoom - MARGIN,
        width: w / zoom + MARGIN * 2,
        height: h / zoom + MARGIN * 2,
    };
});

/**
 * Set of node IDs that are currently within (or near) the visible viewport.
 *
 * Used to filter `styledNodes` in CanvasWorkspace so that large canvases
 * don't waste React reconciliation time on thousands of off-screen nodes.
 * ReactFlow's own `onlyRenderVisibleElements` handles DOM suppression;
 * this atom allows us to skip expensive JS-level style computations too.
 *
 * Nodes without `measured` dimensions are always included because they
 * haven't been laid out yet and must be rendered at least once.
 *
 * @doc.type atom
 * @doc.purpose Viewport-culled node ID set
 * @doc.layer product
 * @doc.pattern DerivedAtom
 */
export const visibleNodeIdsAtom = atom((get) => {
    const camera = get(cameraAtom);
    const nodes = get(nodesAtom);

    // Before ReactFlow's onInit fires, camera.initialized is false and the
    // viewport coordinates are 0,0 with zoom 1 — not the real viewport.
    // Return ALL node IDs so the canvas never renders blank on cold load.
    if (!camera.initialized) {
        return new Set(nodes.map((n) => n.id));
    }

    const vp = get(visibleViewportAtom);
    const ids = new Set<string>();
    for (const n of nodes) {
        const w = n.measured?.width ?? null;
        const h = n.measured?.height ?? null;
        // Include nodes not yet measured (first render — no dimensions yet)
        if (w === null || h === null) {
            ids.add(n.id);
            continue;
        }
        // AABB intersection check with off-screen margin baked into vp
        if (
            n.position.x + w >= vp.x &&
            n.position.x <= vp.x + vp.width &&
            n.position.y + h >= vp.y &&
            n.position.y <= vp.y + vp.height
        ) {
            ids.add(n.id);
        }
    }
    return ids;
});

/**
 * Canvas History State
 * 
 * Stores undo/redo history for canvas operations.
 */
export interface CanvasHistoryEntry {
    nodes: Node<ArtifactNodeDataR>[];
    edges: Edge<DependencyEdgeDataR>[];
    timestamp: number;
}

export const canvasHistoryAtom = atom<CanvasHistoryEntry[]>([]);

/**
 * Canvas Mode State
 * 
 * Controls which interaction mode is active (navigate, sketch, code, diagram).
 * Used for coordinating pointer events between ReactFlow, Konva, and Monaco.
 */
export type CanvasInteractionMode = 'navigate' | 'sketch' | 'code' | 'diagram';

export const canvasInteractionModeAtom = atom<CanvasInteractionMode>('navigate');

/**
 * Sketch Configuration Atoms
 * 
 * State for sketch/drawing tools when in sketch mode.
 */
export type SketchTool = 'pen' | 'rect' | 'ellipse' | 'eraser';

export const sketchToolAtom = atom<SketchTool>('pen');
export const sketchColorAtom = atom<string>('#000000');
export const sketchStrokeWidthAtom = atom<number>(2);

/**
 * Diagram Configuration Atoms
 * 
 * State for diagram editing when in diagram mode.
 */
export type DiagramType = 'mermaid' | 'excalidraw' | 'flowchart' | 'sequence' | 'class';

export const diagramTypeAtom = atom<DiagramType>('mermaid');
export const diagramContentAtom = atom<string>('graph TD\n  A[Start] --> B[End]');
export const diagramZoomAtom = atom<number>(1);
export const showDiagramEditorAtom = atom<boolean>(false);
export const historyIndexAtom = atom(0);

/**
 * Derived Atoms
 * 
 * These atoms compute values from other atoms.
 */

/** Can undo? (history index > 0) */
export const canUndoAtom = atom((get) => {
    return get(historyIndexAtom) > 0;
});

/** Can redo? (history index < history length - 1) */
export const canRedoAtom = atom((get) => {
    const history = get(canvasHistoryAtom);
    const index = get(historyIndexAtom);
    return index < history.length - 1;
});

/** Currently selected node */
export const selectedNodeAtom = atom((get) => {
    const selectedIds = get(selectedNodesAtom);
    return selectedIds.length > 0 ? selectedIds[0] : null;
});

/** Get node by ID */
export const getNodeByIdAtom = atom(
    (get) => (nodeId: string) => {
        const nodes = get(nodesAtom);
        return nodes.find(n => n.id === nodeId);
    }
);

/** Get edge by ID */
export const getEdgeByIdAtom = atom(
    (get) => (edgeId: string) => {
        const edges = get(edgesAtom);
        return edges.find(e => e.id === edgeId);
    }
);

/**
 * Action Atoms
 * 
 * Write-only atoms that trigger state updates.
 */

/** Update nodes */
export const updateNodesAtom = atom(
    null,
    (_get, set, newNodes: Node<ArtifactNodeDataR>[]) => {
        set(nodesAtom, newNodes);
    }
);

/** Update edges */
export const updateEdgesAtom = atom(
    null,
    (_get, set, newEdges: Edge<DependencyEdgeDataR>[]) => {
        set(edgesAtom, newEdges);
    }
);

/** Update camera — prefer writing cameraAtom directly via onMove callback */
export const updateViewportAtom = atom(
    null,
    (_get, set, viewport: { x: number; y: number; zoom: number }) => {
        set(cameraAtom, { ...viewport, initialized: true });
    }
);

/** Add node */
export const addNodeAtom = atom(
    null,
    (get, set, node: Node<ArtifactNodeDataR>) => {
        const nodes = get(nodesAtom);
        set(nodesAtom, [...nodes, node]);
    }
);

/** Remove node */
export const removeNodeAtom = atom(
    null,
    (get, set, nodeId: string) => {
        const nodes = get(nodesAtom);
        const edges = get(edgesAtom);
        set(nodesAtom, nodes.filter(n => n.id !== nodeId));
        set(edgesAtom, edges.filter(e => e.source !== nodeId && e.target !== nodeId));
    }
);

/** Add edge */
export const addEdgeAtom = atom(
    null,
    (get, set, edge: Edge<DependencyEdgeDataR>) => {
        const edges = get(edgesAtom);
        // Avoid duplicate edges
        if (!edges.some(e => e.id === edge.id)) {
            set(edgesAtom, [...edges, edge]);
        }
    }
);

/** Remove edge */
export const removeEdgeAtom = atom(
    null,
    (get, set, edgeId: string) => {
        const edges = get(edgesAtom);
        set(edgesAtom, edges.filter(e => e.id !== edgeId));
    }
);

/** Clear selection */
export const clearSelectionAtom = atom(
    null,
    (_get, set) => {
        set(selectedNodesAtom, []);
    }
);

/** Undo operation */
export const undoAtom = atom(
    null,
    (get, set) => {
        const index = get(historyIndexAtom);
        const history = get(canvasHistoryAtom);

        if (index > 0) {
            const newIndex = index - 1;
            const entry = history[newIndex];
            set(nodesAtom, entry.nodes);
            set(edgesAtom, entry.edges);
            set(historyIndexAtom, newIndex);
        }
    }
);

/** Redo operation */
export const redoAtom = atom(
    null,
    (get, set) => {
        const index = get(historyIndexAtom);
        const history = get(canvasHistoryAtom);

        if (index < history.length - 1) {
            const newIndex = index + 1;
            const entry = history[newIndex];
            set(nodesAtom, entry.nodes);
            set(edgesAtom, entry.edges);
            set(historyIndexAtom, newIndex);
        }
    }
);

/** Push history entry (bounded — caps at MAX_HISTORY_SIZE) */
export const pushHistoryAtom = atom(
    null,
    (get, set) => {
        const nodes = get(nodesAtom);
        const edges = get(edgesAtom);
        const history = get(canvasHistoryAtom);
        const index = get(historyIndexAtom);

        // Remove any history entries after current index (redo path is lost)
        const trimmed = history.slice(0, index + 1);

        // Add new entry
        trimmed.push({
            nodes,
            edges,
            timestamp: Date.now(),
        });

        // Enforce history cap to prevent memory leaks
        const newHistory = trimmed.slice(-MAX_HISTORY_SIZE);

        set(canvasHistoryAtom, newHistory);
        set(historyIndexAtom, newHistory.length - 1);
    }
);

/** Command palette visibility */
export const isCommandPaletteOpenAtom = atom(false);

/** Search panel visibility */
export const isSearchOpenAtom = atom(false);

/** Alignment guide data for snap overlays */
export const alignmentGuidesAtom = atom<{ vertical: number | null; horizontal: number | null }>({ vertical: null, horizontal: null });

/**
 * Whether the user prefers reduced motion (SSR-safe).
 * Dynamically updated by useCanvasAccessibility when the OS setting changes.
 */
export const prefersReducedMotionAtom = atom<boolean>(
    typeof window !== 'undefined'
        ? window.matchMedia('(prefers-reduced-motion: reduce)').matches
        : false
);

/**
 * Whether the system/app is currently in dark mode.
 *
 * Checked against two signals (in priority order):
 *  1. Tailwind's dark-mode class on `<html>` (class strategy, e.g. set by ThemeProvider)
 *  2. `prefers-color-scheme: dark` OS media query (media strategy fallback)
 *
 * Reactive: `useCanvasAccessibility` re-triggers this on both transitions.
 */
export const prefersDarkModeAtom = atom<boolean>(
    typeof window !== 'undefined'
        ? document.documentElement.classList.contains('dark') ||
          window.matchMedia('(prefers-color-scheme: dark)').matches
        : false
);

/** Canvas accessibility announcement (for aria-live region) */
export const canvasAnnouncementAtom = atom<string>('');

// ============================================================================
// Command Registry — extensible by plugins, read by CommandPalette
// ============================================================================

/**
 * Central registry of all available canvas commands.
 *
 * Uses `Map<string, CanvasCommandAction>` keyed by command `id` for O(1)
 * lookup and stable identity: writing a single entry creates a new Map but
 * leaves all other entries untouched. This ensures components that read
 * `sortedCommandsAtom` don't burst-re-render on every registration at startup.
 *
 * Do NOT subscribe to this atom directly in UI — use `sortedCommandsAtom`
 * which returns a stable sorted array for rendering.
 */
export const commandRegistryAtom = atom<Map<string, CanvasCommandAction>>(new Map());

/**
 * Derived read atom: sorted display list for the CommandPalette.
 *
 * Sort order: alphabetical within group, then by label. Consumers never need
 * to sort themselves. Stable reference changes only when the Map changes.
 */
export const sortedCommandsAtom = atom<CanvasCommandAction[]>((get) =>
    [...get(commandRegistryAtom).values()].sort((a, b) => {
        const ga = a.group ?? '';
        const gb = b.group ?? '';
        return ga !== gb ? ga.localeCompare(gb) : a.label.localeCompare(b.label);
    }),
);

/**
 * Write-only atom to register one or more commands without overwriting
 * commands registered by other parts of the app (plugins, etc.).
 * Caller's version wins on id collision.
 */
export const registerCommandsAtom = atom(
    null,
    (get, set, commands: CanvasCommandAction[]) => {
        const prev = get(commandRegistryAtom);
        const next = new Map(prev);
        for (const cmd of commands) next.set(cmd.id, cmd);
        set(commandRegistryAtom, next);
    }
);

/**
 * Write-only atom to unregister commands by id (used on plugin unmount).
 */
export const unregisterCommandsAtom = atom(
    null,
    (get, set, ids: string[]) => {
        const prev = get(commandRegistryAtom);
        const next = new Map(prev);
        for (const id of ids) next.delete(id);
        set(commandRegistryAtom, next);
    }
);

// ============================================================================
// Node Position Persistence — separate from the server artifact model
// ============================================================================

/**
 * User-dragged node positions, keyed by node ID.
 *
 * This solves the dual data-model problem: server refetches (via useArtifacts)
 * regenerate node objects from artifact data, which would stomp user-dragged
 * positions. By storing positions here separately and merging at render time,
 * server-state and layout-state are kept independent.
 *
 * Initialized from localStorage on mount; persisted on drag stop.
 */
export const nodePositionsAtom = atom<Record<string, NodePosition>>({});

/**
 * Upsert a single node position (called from onNodeDragStop).
 */
export const setNodePositionAtom = atom(
    null,
    (get, set, { id, x, y }: { id: string; x: number; y: number }) => {
        const prev = get(nodePositionsAtom);
        set(nodePositionsAtom, { ...prev, [id]: { x, y } });
    }
);

// ============================================================================
// Code Associations — batch-loaded at workspace level (eliminates N+1)
// ============================================================================

/**
 * Batch-loaded code associations for all artifacts in the workspace.
 *
 * Written once by useCodeAssociationsBatch (workspace-level hook).
 * Read by ArtifactNode components via atom selector — no per-node fetch.
 */
export const codeAssociationsAtom = atom<Map<string, CodeLink[]>>(new Map());

// ============================================================================
// Per-Node Selector AtomFamily — O(1) subscriptions for individual nodes
// ============================================================================

/**
 * Returns the `Node` object for a given node ID, or `undefined` if not found.
 *
 * Node components should subscribe to this rather than reading from the full
 * `nodesAtom` array, so they only re-render when THEIR node data changes.
 *
 * ```ts
 * // Inside ArtifactNode or any node component:
 * const node = useAtomValue(nodeByIdAtomFamily(id));
 * ```
 *
 * @doc.type atom
 * @doc.purpose Per-node stable selector
 * @doc.layer product
 * @doc.pattern AtomFamily
 */
export const nodeByIdAtomFamily = atomFamily((id: string) =>
    atom((get) => get(nodesAtom).find((n) => n.id === id))
);

/**
 * Returns `true` if the given node id is in `selectedNodesAtom`.
 *
 * Isolates selection changes from nodes that aren't involved, preventing
 * every node from re-rendering on every selection change.
 *
 * @doc.type atom
 * @doc.purpose Per-node selection selector
 * @doc.layer product
 * @doc.pattern AtomFamily
 */
export const isNodeSelectedAtomFamily = atomFamily((id: string) =>
    atom((get) => get(selectedNodesAtom).includes(id))
);

/**
 * Returns just the `{ x, y }` position of a node.
 *
 * Useful when a component only needs to react to position changes (e.g.,
 * a connector overlay or a spatial label), not data changes.
 *
 * @doc.type atom
 * @doc.purpose Per-node position selector
 * @doc.layer product
 * @doc.pattern AtomFamily
 */
export const nodePositionAtomFamily = atomFamily((id: string) =>
    atom((get) => {
        const n = get(nodesAtom).find((n) => n.id === id);
        return n ? n.position : null;
    })
);

// ============================================================================
// Core Canvas Types
//
// These types were previously re-exported from state/atoms/canvasAtom.ts
// (now deleted). They are defined here as the canonical source for the
// legacy canvas model. The unified canvas (unifiedCanvasAtom.ts) uses
// a different type model (HierarchicalNode-based).
// ============================================================================

export interface CanvasElement {
    id: string;
    type: string;
    position: { x: number; y: number };
    kind?: string;
    size?: { width: number; height: number };
    data?: Record<string, unknown>;
    style?: Record<string, unknown>;
    selected?: boolean;
}

export interface CanvasConnection {
    id: string;
    source: string;
    target: string;
    type?: string;
    label?: string;
    sourceHandle?: string;
    targetHandle?: string;
    animated?: boolean;
    data?: Record<string, unknown>;
    style?: Record<string, unknown>;
}

export interface CanvasState {
    elements: CanvasElement[];
    connections: CanvasConnection[];
    metadata?: Record<string, unknown>;
    selectedElements?: string[];
    viewport?: { x: number; y: number; zoom: number };
    viewportPosition?: { x: number; y: number };
    zoomLevel?: number;
    draggedElement?: unknown;
    isReadOnly?: boolean;
    layers?: unknown[];
    history?: unknown;
}
