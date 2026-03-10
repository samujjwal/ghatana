/**
 * Canvas State — Compatibility Facade
 *
 * This file is a THIN FACADE over the canonical state in
 * `@ghatana/yappc-canvas` (libs/canvas/src/state/atoms.ts).
 *
 * All shared atoms (command registry, sketch tools, accessibility,
 * interaction mode, alignment, lifecycle, etc.) are re-exported from the
 * library. ReactFlow-specific atoms (nodes, edges, camera with
 * `initialized`, viewport culling, nodePositionsAtom, codeAssociationsAtom)
 * are defined here because they depend on `@xyflow/react` types which
 * the platform-agnostic library must NOT import.
 *
 * **Migration path**: components should gradually move to importing from
 * `@ghatana/yappc-canvas/state` directly. This file exists solely to keep
 * existing 60+ importers working without a big-bang refactor.
 *
 * @doc.type module
 * @doc.purpose Compatibility facade re-exporting canonical canvas atoms
 * @doc.layer product
 * @doc.pattern Adapter
 * @deprecated Import from `@ghatana/yappc-canvas/state` instead.
 */

import { atom } from 'jotai';
import { atomFamily } from 'jotai/utils';
import { type Node, type Edge } from '@xyflow/react';
import type { ArtifactNodeData } from '../nodes/ArtifactNode';
import type { DependencyEdgeData } from '../edges';
import type { InspectorArtifact } from './InspectorPanel';
import type { ArtifactTemplate } from './ArtifactPalette';

// ============================================================================
// Re-export EVERYTHING from the canonical library state
// ============================================================================
export {
  // Platform atoms
  hybridCanvasStateAtom,
  renderingModeAtom,
  activeLayerAtom,
  // Document & CRUD atoms
  canvasDocumentAtom,
  canvasSelectionAtom,
  canvasViewportAtom,
  canvasHistoryAtom,
  canvasUIStateAtom,
  canvasPerformanceAtom,
  canvasCollaborationAtom,
  canvasElementsArrayAtom,
  selectedElementsAtom,
  canvasCapabilitiesAtom,
  hasUnsavedChangesAtom,
  updateDocumentAtom,
  updateElementAtom,
  addElementAtom,
  removeElementAtom,
  updateSelectionAtom,
  addHistoryEntryAtom,
  resetUIStateAtom,
  batchUpdateElementsAtom,
  panViewportAtom,
  zoomViewportAtom,
  clearHistoryAtom,
  updateUIStateAtom,
  updatePerformanceAtom,
  // Interaction mode & sketch/diagram
  canvasInteractionModeAtom,
  sketchToolAtom,
  sketchColorAtom,
  sketchStrokeWidthAtom,
  diagramTypeAtom,
  diagramContentAtom,
  diagramZoomAtom,
  showDiagramEditorAtom,
  // Command registry
  commandRegistryAtom,
  sortedCommandsAtom,
  registerCommandsAtom,
  unregisterCommandsAtom,
  // Workspace UI
  activePersonaAtom,
  isAIModalOpenAtom,
  isProjectSwitcherOpenAtom,
  isInspectorOpenAtom,
  isCommandPaletteOpenAtom,
  isSearchOpenAtom,
  // Accessibility
  prefersReducedMotionAtom,
  prefersDarkModeAtom,
  canvasAnnouncementAtom,
  // Alignment
  alignmentGuidesAtom,
  // Constants
  PHASE_ZONE_CENTERS,
  MAX_HISTORY_SIZE,
  // Lifecycle & tasks
  lifecyclePhaseAtom,
  phaseProgressAtom,
  canvasTasksAtom,
  tasksByPhaseAtom,
  blockedTasksAtom,
  nextBestTaskAtom,
  // AI
  aiSuggestionsAtom,
  validationIssuesAtom,
  validationScoreAtom,
  // Collaboration
  collaboratorsAtom,
  onlineCollaboratorsAtom,
  // Project
  canvasProjectMetadataAtom,
  // Bounding box
  boundingBoxAtom,
} from '@ghatana/yappc-canvas/state';

// Re-export types from library
export type {
  CanvasInteractionMode,
  SketchTool,
  DiagramType,
  CanvasCommandAction,
  LifecyclePhase,
  PhaseProgress,
  TaskStatus,
  TaskPriority,
  CanvasTask,
  AISuggestion,
  ValidationIssue,
  Collaborator,
  CanvasProjectMetadata,
} from '@ghatana/yappc-canvas/state';

/**
 * Backward-compat alias for `hybridCanvasStateAtom`.
 * 5 files import `canvasAtom` from this module (pre-existing broken import
 * that never existed in the original file). This alias keeps them working.
 * @deprecated Import `hybridCanvasStateAtom` from `@ghatana/yappc-canvas/state`.
 */
export { hybridCanvasStateAtom as canvasAtom } from '@ghatana/yappc-canvas/state';

// ============================================================================
// ReactFlow-specific types (cannot live in platform-agnostic library)
// ============================================================================

export interface NodePosition {
  x: number;
  y: number;
}

export interface CodeLink {
  id: string;
  codeArtifactId: string;
  relationship: 'IMPLEMENTATION' | 'TEST' | 'DOCUMENTATION' | 'MOCK';
  file?: string;
  line?: number;
}

export interface CameraState {
  x: number;
  y: number;
  zoom: number;
  initialized: boolean;
}

export type ArtifactNodeDataR = ArtifactNodeData & Record<string, unknown>;
export type DependencyEdgeDataR = DependencyEdgeData & Record<string, unknown>;

// ============================================================================
// ReactFlow-specific atoms (depend on @xyflow/react types)
// ============================================================================

export const selectedNodesAtom = atom<string[]>([]);
export const quickCreateMenuPositionAtom = atom<{ x: number; y: number } | null>(null);
export const draggedTemplateAtom = atom<ArtifactTemplate | null>(null);
export const selectedArtifactAtom = atom<InspectorArtifact | null>(null);
export const copiedNodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);
export const nodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);
export const edgesAtom = atom<Edge<DependencyEdgeDataR>[]>([]);
export const suppressGeneratedSyncAtom = atom(false);
export const activeTaskAtom = atom<string | null>(null);
export const ghostNodesAtom = atom<Node<ArtifactNodeDataR>[]>([]);

// Camera — with ReactFlow-specific `initialized` flag
export const cameraAtom = atom<CameraState>({ x: 0, y: 0, zoom: 1, initialized: false });
export const cameraZoomAtom = atom((get) => get(cameraAtom).zoom);
/** @deprecated Use cameraAtom */
export const viewportAtom = cameraAtom;

// Viewport culling
export const visibleViewportAtom = atom((get) => {
  const { x, y, zoom } = get(cameraAtom);
  const MARGIN = 400;
  const w = typeof globalThis.window !== 'undefined' ? globalThis.window.innerWidth : 1920;
  const h = typeof globalThis.window !== 'undefined' ? globalThis.window.innerHeight : 1080;
  return {
    x: -x / zoom - MARGIN,
    y: -y / zoom - MARGIN,
    width: w / zoom + MARGIN * 2,
    height: h / zoom + MARGIN * 2,
  };
});

export const visibleNodeIdsAtom = atom((get) => {
  const camera = get(cameraAtom);
  const nodes = get(nodesAtom);
  if (!camera.initialized) return new Set(nodes.map((n) => n.id));
  const vp = get(visibleViewportAtom);
  const ids = new Set<string>();
  for (const n of nodes) {
    const w = n.measured?.width ?? null;
    const h = n.measured?.height ?? null;
    if (w === null || h === null) { ids.add(n.id); continue; }
    if (
      n.position.x + w >= vp.x && n.position.x <= vp.x + vp.width &&
      n.position.y + h >= vp.y && n.position.y <= vp.y + vp.height
    ) ids.add(n.id);
  }
  return ids;
});

// ReactFlow history (stores ReactFlow Node/Edge objects)
export interface CanvasHistoryEntry {
  nodes: Node<ArtifactNodeDataR>[];
  edges: Edge<DependencyEdgeDataR>[];
  timestamp: number;
}

export const canvasHistoryLegacyAtom = atom<CanvasHistoryEntry[]>([]);
export const historyIndexAtom = atom(0);

// Derived atoms
export const canUndoAtom = atom((get) => get(historyIndexAtom) > 0);
export const canRedoAtom = atom((get) => {
  const history = get(canvasHistoryLegacyAtom);
  return get(historyIndexAtom) < history.length - 1;
});

export const selectedNodeAtom = atom((get) => {
  const ids = get(selectedNodesAtom);
  return ids.length > 0 ? ids[0] : null;
});

export const getNodeByIdAtom = atom(
  (get) => (nodeId: string) => get(nodesAtom).find((n) => n.id === nodeId)
);

export const getEdgeByIdAtom = atom(
  (get) => (edgeId: string) => get(edgesAtom).find((e) => e.id === edgeId)
);

// Write atoms
export const updateNodesAtom = atom(null, (_get, set, nodes: Node<ArtifactNodeDataR>[]) => set(nodesAtom, nodes));
export const updateEdgesAtom = atom(null, (_get, set, edges: Edge<DependencyEdgeDataR>[]) => set(edgesAtom, edges));
export const updateViewportAtom = atom(null, (_get, set, vp: { x: number; y: number; zoom: number }) =>
  set(cameraAtom, { ...vp, initialized: true })
);
export const addNodeAtom = atom(null, (get, set, node: Node<ArtifactNodeDataR>) =>
  set(nodesAtom, [...get(nodesAtom), node])
);
export const removeNodeAtom = atom(null, (get, set, nodeId: string) => {
  set(nodesAtom, get(nodesAtom).filter((n) => n.id !== nodeId));
  set(edgesAtom, get(edgesAtom).filter((e) => e.source !== nodeId && e.target !== nodeId));
});
export const addEdgeAtom = atom(null, (get, set, edge: Edge<DependencyEdgeDataR>) => {
  const edges = get(edgesAtom);
  if (!edges.some((e) => e.id === edge.id)) set(edgesAtom, [...edges, edge]);
});
export const removeEdgeAtom = atom(null, (get, set, edgeId: string) =>
  set(edgesAtom, get(edgesAtom).filter((e) => e.id !== edgeId))
);
export const clearSelectionAtom = atom(null, (_get, set) => set(selectedNodesAtom, []));

export const undoAtom = atom(null, (get, set) => {
  const idx = get(historyIndexAtom);
  const history = get(canvasHistoryLegacyAtom);
  if (idx > 0) {
    const entry = history[idx - 1];
    set(nodesAtom, entry.nodes);
    set(edgesAtom, entry.edges);
    set(historyIndexAtom, idx - 1);
  }
});

export const redoAtom = atom(null, (get, set) => {
  const idx = get(historyIndexAtom);
  const history = get(canvasHistoryLegacyAtom);
  if (idx < history.length - 1) {
    const entry = history[idx + 1];
    set(nodesAtom, entry.nodes);
    set(edgesAtom, entry.edges);
    set(historyIndexAtom, idx + 1);
  }
});

export const pushHistoryAtom = atom(null, (get, set) => {
  const nodes = get(nodesAtom);
  const edges = get(edgesAtom);
  const history = get(canvasHistoryLegacyAtom);
  const idx = get(historyIndexAtom);
  const trimmed = history.slice(0, idx + 1);
  trimmed.push({ nodes, edges, timestamp: Date.now() });
  const capped = trimmed.slice(-MAX_HISTORY_SIZE);
  set(canvasHistoryLegacyAtom, capped);
  set(historyIndexAtom, capped.length - 1);
});

// Node position persistence (separate from server artifact model)
export const nodePositionsAtom = atom<Record<string, NodePosition>>({});
export const setNodePositionAtom = atom(
  null,
  (get, set, { id, x, y }: { id: string; x: number; y: number }) => {
    set(nodePositionsAtom, { ...get(nodePositionsAtom), [id]: { x, y } });
  }
);

// Code associations
export const codeAssociationsAtom = atom<Map<string, CodeLink[]>>(new Map());

// AtomFamily selectors
export const nodeByIdAtomFamily = atomFamily((id: string) =>
  atom((get) => get(nodesAtom).find((n) => n.id === id))
);
export const isNodeSelectedAtomFamily = atomFamily((id: string) =>
  atom((get) => get(selectedNodesAtom).includes(id))
);
export const nodePositionAtomFamily = atomFamily((id: string) =>
  atom((get) => {
    const n = get(nodesAtom).find((n) => n.id === id);
    return n ? n.position : null;
  })
);

// ============================================================================
// Legacy CanvasState/CanvasElement/CanvasConnection types
// Kept for backward-compatibility with services that use this shape.
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
