// @ts-nocheck
import { atom } from 'jotai';

import { activePersonaAtom as activeWorkspacePersonaAtom } from '@/stores/user.store';

export type CanvasInteractionMode = 'select' | 'pan' | 'draw' | 'text' | 'navigate';
export type SketchTool = 'select' | 'pen' | 'rectangle';
export type DiagramType = 'architecture' | 'sequence' | 'flowchart' | 'custom';
export type LifecyclePhase = 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'improve';
export type TaskStatus = 'todo' | 'in-progress' | 'blocked' | 'done';
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

export interface CanvasCommandAction {
  id: string;
  label: string;
  shortcut?: string;
  keywords?: string[];
  category?: string;
  handler?: () => void;
}

export interface PhaseProgress {
  phase: LifecyclePhase;
  progress: number;
  completed?: boolean;
}

export interface CanvasTask {
  id: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  phase?: LifecyclePhase | string;
}

export interface AISuggestion {
  id: string;
  type: string;
  title: string;
  description?: string;
  confidence?: number;
}

export interface ValidationIssue {
  id: string;
  severity: 'error' | 'warning' | 'info';
  message: string;
  nodeId?: string;
}

export interface Collaborator {
  id: string;
  name: string;
  online?: boolean;
  isOnline?: boolean;
  color?: string;
}

export interface CanvasProjectMetadata {
  id?: string;
  name?: string;
  description?: string;
  [key: string]: unknown;
}

const defaultDocument = () => ({
  version: '1.0.0',
  id: 'canvas-document',
  title: 'Untitled Canvas',
  viewport: { center: { x: 0, y: 0 }, zoom: 1 },
  elements: {},
  elementOrder: [],
  metadata: {},
  capabilities: {
    canEdit: true,
    canZoom: true,
    canPan: true,
    canSelect: true,
    canUndo: true,
    canRedo: true,
    canExport: true,
    canImport: true,
    canCollaborate: true,
    canPersist: true,
    allowedElementTypes: ['node', 'edge', 'group', 'annotation'],
  },
  createdAt: new Date(),
  updatedAt: new Date(),
});

export const hybridCanvasStateAtom = atom({
  elements: [],
  connections: [],
  metadata: {},
  selectedElements: [],
  viewport: { x: 0, y: 0, zoom: 1 },
  viewportPosition: { x: 0, y: 0 },
  zoomLevel: 1,
  draggedElement: null,
  isReadOnly: false,
  layers: [],
  history: null,
});

export const renderingModeAtom = atom<'reactflow' | 'document'>('reactflow');
export const activeLayerAtom = atom<string | null>(null);
export const canvasDocumentAtom = atom(defaultDocument());
export const canvasSelectionAtom = atom({ selectedIds: [], focusedId: undefined, hoveredId: undefined });
export const canvasViewportAtom = atom({ center: { x: 0, y: 0 }, zoom: 1, bounds: { x: -1000, y: -1000, width: 2000, height: 2000 } });
export const canvasHistoryAtom = atom({ entries: [], currentIndex: -1 });
export const canvasUIStateAtom = atom({ isDragging: false, isSelecting: false, isPanning: false, isLoading: false, mode: 'select' });
export const canvasPerformanceAtom = atom({ renderTime: 0, fps: 60, lastUpdate: new Date() });
export const canvasCollaborationAtom = atom({ collaborators: [] });
export const canvasInteractionModeAtom = atom<CanvasInteractionMode>('select');
export const sketchToolAtom = atom<SketchTool>('select');
export const sketchColorAtom = atom('#0f172a');
export const sketchStrokeWidthAtom = atom(2);
export const diagramTypeAtom = atom<DiagramType>('flowchart');
export const diagramContentAtom = atom<Record<string, unknown> | null>(null);
export const diagramZoomAtom = atom(1);
export const showDiagramEditorAtom = atom(false);
export const commandRegistryAtom = atom<CanvasCommandAction[]>([]);
export const sortedCommandsAtom = atom((get) =>
  [...get(commandRegistryAtom)].sort((left, right) =>
    left.label.localeCompare(right.label)
  )
);
export const registerCommandsAtom = atom(null, (get, set, actions: CanvasCommandAction[] | CanvasCommandAction) => {
  const nextActions = Array.isArray(actions) ? actions : [actions];
  const existing = get(commandRegistryAtom);
  const merged = [...existing];
  nextActions.forEach((action) => {
    const index = merged.findIndex((entry) => entry.id === action.id);
    if (index >= 0) {
      merged[index] = action;
    } else {
      merged.push(action);
    }
  });
  set(commandRegistryAtom, merged);
});
export const unregisterCommandsAtom = atom(null, (get, set, ids: string[] | string) => {
  const removeIds = new Set(Array.isArray(ids) ? ids : [ids]);
  set(commandRegistryAtom, get(commandRegistryAtom).filter((entry) => !removeIds.has(entry.id)));
});
export const activePersonaAtom = activeWorkspacePersonaAtom;
export const isAIModalOpenAtom = atom(false);
export const isProjectSwitcherOpenAtom = atom(false);
export const isInspectorOpenAtom = atom(false);
export const isCommandPaletteOpenAtom = atom(false);
export const isSearchOpenAtom = atom(false);
export const prefersReducedMotionAtom = atom(false);
export const prefersDarkModeAtom = atom(false);
export const canvasAnnouncementAtom = atom('');
export const alignmentGuidesAtom = atom<{ vertical: number | null; horizontal: number | null }>({ vertical: null, horizontal: null });
export const PHASE_ZONE_CENTERS = {
  intent: { x: -1200, y: 0 },
  INTENT: { x: -1200, y: 0 },
  shape: { x: -600, y: 0 },
  SHAPE: { x: -600, y: 0 },
  validate: { x: 0, y: 0 },
  VALIDATE: { x: 0, y: 0 },
  generate: { x: 600, y: 0 },
  GENERATE: { x: 600, y: 0 },
  run: { x: 1200, y: 0 },
  RUN: { x: 1200, y: 0 },
  observe: { x: 1800, y: 0 },
  OBSERVE: { x: 1800, y: 0 },
  improve: { x: 2400, y: 0 },
  IMPROVE: { x: 2400, y: 0 },
};
export const MAX_HISTORY_SIZE = 50;
export const lifecyclePhaseAtom = atom<LifecyclePhase>('shape');
export const phaseProgressAtom = atom<PhaseProgress[]>([]);
export const canvasTasksAtom = atom<CanvasTask[]>([]);
export const tasksByPhaseAtom = atom((get) => {
  const grouped: Record<string, CanvasTask[]> = {};
  get(canvasTasksAtom).forEach((task) => {
    const phase = task.phase ?? 'unassigned';
    grouped[phase] ??= [];
    grouped[phase].push(task);
  });
  return grouped;
});
export const blockedTasksAtom = atom((get) =>
  get(canvasTasksAtom).filter((task) => task.status === 'blocked')
);
export const nextBestTaskAtom = atom((get) => get(canvasTasksAtom)[0] ?? null);
export const aiSuggestionsAtom = atom<AISuggestion[]>([]);
export const validationIssuesAtom = atom<ValidationIssue[]>([]);
export const validationScoreAtom = atom(100);
export const collaboratorsAtom = atom<Collaborator[]>([]);
export const onlineCollaboratorsAtom = atom((get) =>
  get(collaboratorsAtom).filter((collaborator) => collaborator.online || collaborator.isOnline)
);
export const canvasProjectMetadataAtom = atom<CanvasProjectMetadata | null>(null);

export const canvasElementsArrayAtom = atom((get) =>
  get(canvasDocumentAtom).elementOrder
    .map((id) => get(canvasDocumentAtom).elements[id])
    .filter(Boolean)
);
export const selectedElementsAtom = atom((get) => {
  const document = get(canvasDocumentAtom);
  const selection = get(canvasSelectionAtom).selectedIds;
  return selection.map((id) => document.elements[id]).filter(Boolean);
});
export const canvasCapabilitiesAtom = atom((get) => get(canvasDocumentAtom).capabilities);
export const hasUnsavedChangesAtom = atom(false);
export const boundingBoxAtom = atom((get) => {
  const elements = get(canvasElementsArrayAtom);
  if (elements.length === 0) {
    return null;
  }

  const xs = elements.map((element) => element.bounds?.x ?? element.transform?.position?.x ?? 0);
  const ys = elements.map((element) => element.bounds?.y ?? element.transform?.position?.y ?? 0);
  const widths = elements.map((element) => element.bounds?.width ?? 0);
  const heights = elements.map((element) => element.bounds?.height ?? 0);
  const minX = Math.min(...xs);
  const minY = Math.min(...ys);
  const maxX = Math.max(...xs.map((x, index) => x + widths[index]));
  const maxY = Math.max(...ys.map((y, index) => y + heights[index]));

  return { x: minX, y: minY, width: maxX - minX, height: maxY - minY };
});

export const updateDocumentAtom = atom(null, (_get, set, document) => {
  set(canvasDocumentAtom, document);
  set(hasUnsavedChangesAtom, true);
});
export const addElementAtom = atom(null, (get, set, element) => {
  const document = get(canvasDocumentAtom);
  const elementOrder = document.elementOrder.includes(element.id)
    ? document.elementOrder
    : [...document.elementOrder, element.id];
  set(canvasDocumentAtom, {
    ...document,
    elements: { ...document.elements, [element.id]: element },
    elementOrder,
    updatedAt: new Date(),
  });
  set(hasUnsavedChangesAtom, true);
});
export const updateElementAtom = atom(null, (get, set, payload) => {
  const { id, changes } = payload;
  const document = get(canvasDocumentAtom);
  const current = document.elements[id];
  if (!current) {
    return;
  }
  set(canvasDocumentAtom, {
    ...document,
    elements: {
      ...document.elements,
      [id]: { ...current, ...changes },
    },
    updatedAt: new Date(),
  });
  set(hasUnsavedChangesAtom, true);
});
export const removeElementAtom = atom(null, (get, set, elementId: string) => {
  const document = get(canvasDocumentAtom);
  const { [elementId]: _removed, ...remainingElements } = document.elements;
  set(canvasDocumentAtom, {
    ...document,
    elements: remainingElements,
    elementOrder: document.elementOrder.filter((id) => id !== elementId),
    updatedAt: new Date(),
  });
  set(canvasSelectionAtom, {
    ...get(canvasSelectionAtom),
    selectedIds: get(canvasSelectionAtom).selectedIds.filter((id) => id !== elementId),
  });
  set(hasUnsavedChangesAtom, true);
});
export const updateSelectionAtom = atom(null, (get, set, payload) => {
  set(canvasSelectionAtom, {
    ...get(canvasSelectionAtom),
    selectedIds: payload.selectedIds ?? [],
  });
});
export const addHistoryEntryAtom = atom(null, (get, set, entry) => {
  const history = get(canvasHistoryAtom);
  const entries = [...history.entries, entry].slice(-MAX_HISTORY_SIZE);
  set(canvasHistoryAtom, { entries, currentIndex: entries.length - 1 });
});
export const resetUIStateAtom = atom(null, (_get, set) => {
  set(canvasUIStateAtom, { isDragging: false, isSelecting: false, isPanning: false, isLoading: false, mode: 'select' });
});
export const batchUpdateElementsAtom = atom(null, (_get, set, updates) => {
  updates.forEach((update) => set(updateElementAtom, update));
});
export const panViewportAtom = atom(null, (get, set, delta) => {
  const viewport = get(canvasViewportAtom);
  set(canvasViewportAtom, {
    ...viewport,
    center: { x: viewport.center.x + delta.x, y: viewport.center.y + delta.y },
  });
});
export const zoomViewportAtom = atom(null, (get, set, zoom: number) => {
  const viewport = get(canvasViewportAtom);
  set(canvasViewportAtom, { ...viewport, zoom });
});
export const clearHistoryAtom = atom(null, (_get, set) => {
  set(canvasHistoryAtom, { entries: [], currentIndex: -1 });
});
export const updateUIStateAtom = atom(null, (get, set, patch) => {
  set(canvasUIStateAtom, { ...get(canvasUIStateAtom), ...patch });
});
export const updatePerformanceAtom = atom(null, (get, set, patch) => {
  set(canvasPerformanceAtom, { ...get(canvasPerformanceAtom), ...patch, lastUpdate: new Date() });
});