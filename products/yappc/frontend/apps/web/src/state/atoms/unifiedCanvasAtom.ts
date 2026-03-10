/**
 * Unified Canvas State Management
 *
 * **DEPRECATED**: Shared atoms (lifecycle, tasks, AI, collaboration, project
 * metadata) are now canonical in `@ghatana/yappc-canvas/state`.
 * Import from there for new code.
 *
 * HierarchicalNode-specific state remains here until the HierarchicalNode
 * model is migrated to the platform library.
 *
 * Complete Jotai atom-based state for unified canvas
 *
 * @doc.type module
 * @doc.purpose Central state management for all canvas features
 * @doc.layer state
 * @doc.pattern Atom
 * @deprecated Import shared atoms from `@ghatana/yappc-canvas/state` instead.
 */

import { atom } from 'jotai';
import { atomWithStorage } from 'jotai/utils';
import type { HierarchicalNode } from '../../lib/canvas/HierarchyManager';
import type { Layer, Group, Connection } from '../../lib/canvas/NodeManipulation';
import type { ViewportState, ZoomRange } from '../../lib/canvas/ZoomManager';
import type { DrawingStroke } from '../../lib/canvas/DrawingManager';

// ============================================================================
// CANVAS STATE
// ============================================================================

export interface CanvasState {
    nodes: HierarchicalNode[];
    connections: Connection[];
    drawings: DrawingStroke[];
    selectedNodeIds: string[];
    clipboard: HierarchicalNode[];
    viewport: ViewportState;
    layers: Layer[];
    groups: Group[];
}

export const canvasAtom = atom<CanvasState>({
    nodes: [],
    connections: [],
    drawings: [],
    selectedNodeIds: [],
    clipboard: [],
    viewport: { x: 0, y: 0, zoom: 0.5 },  // Default 50% zoom
    layers: [],
    groups: []
});

// Derived atoms

export const nodesAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return Array.isArray(state?.nodes) ? state.nodes : [];
    },
    (get, set, nodes: HierarchicalNode[]) => {
        const state = get(canvasAtom) || {};
        set(canvasAtom, { ...state, nodes });
    }
);

export const connectionsAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return Array.isArray(state?.connections) ? state.connections : [];
    },
    (get, set, connections: Connection[]) => {
        const state = get(canvasAtom) || {};
        set(canvasAtom, { ...state, connections });
    }
);

export const selectedNodeIdsAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return Array.isArray(state?.selectedNodeIds) ? state.selectedNodeIds : [];
    },
    (get, set, selectedNodeIds: string[]) => {
        const state = get(canvasAtom) || {};
        set(canvasAtom, { ...state, selectedNodeIds });
    }
);

export const selectedNodesAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        const nodes = Array.isArray(state?.nodes) ? state.nodes : [];
        const selectedNodeIds = Array.isArray(state?.selectedNodeIds) ? state.selectedNodeIds : [];
        return nodes.filter(node => selectedNodeIds.includes(node.id));
    }
);

export const cameraAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return state?.viewport || { x: 0, y: 0, zoom: 0.5 };
    },
    (get, set, viewport: ViewportState) => {
        const state = get(canvasAtom);
        set(canvasAtom, { ...(state || {}), viewport });
    }
);

export const zoomLevelAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return state?.viewport?.zoom || 0.5;
    }
);

export const layersAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return Array.isArray(state?.layers) ? state.layers : [];
    },
    (get, set, layers: Layer[]) => {
        const state = get(canvasAtom) || {};
        set(canvasAtom, { ...state, layers });
    }
);

export const groupsAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        return Array.isArray(state?.groups) ? state.groups : [];
    },
    (get, set, groups: Group[]) => {
        const state = get(canvasAtom) || {};
        set(canvasAtom, { ...state, groups });
    }
);

// Visibility filtering based on zoom
export const visibleNodesAtom = atom(
    (get) => {
        const state = get(canvasAtom);
        const nodes = Array.isArray(state?.nodes) ? state.nodes : [];
        const viewport = state?.viewport || { x: 0, y: 0, zoom: 0.5 };
        const layers = Array.isArray(state?.layers) ? state.layers : [];
        const zoom = viewport.zoom;

        return nodes.filter(node => {
            // Check zoom visibility
            if (node.minZoom && zoom < node.minZoom) return false;
            if (node.maxZoom && zoom > node.maxZoom) return false;

            // Check layer visibility
            const nodeLayerId = node.data.layerId as string | undefined;
            if (nodeLayerId) {
                const layer = layers.find(l => l.id === nodeLayerId);
                if (layer && !layer.visible) return false;
            }

            // Check parent visibility (collapsed)
            if (node.parentId) {
                const parent = nodes.find(n => n.id === node.parentId);
                if (parent && !parent.childrenVisible) return false;
            }

            // Check explicit visibility
            if (node.data.visible === false) return false;

            return true;
        });
    }
);

// Hierarchical tree structure
export const hierarchyTreeAtom = atom(
    (get) => {
        const nodes = get(nodesAtom);
        const rootNodes = nodes.filter(node => !node.parentId);

        const buildTree = (parentId?: string): HierarchicalNode[] => {
            return nodes
                .filter(node => node.parentId === parentId)
                .map(node => ({
                    ...node,
                    children: buildTree(node.id).map(n => n.id)
                }));
        };

        return rootNodes.map(root => ({
            ...root,
            children: buildTree(root.id).map(n => n.id)
        }));
    }
);

// ============================================================================
// UI STATE
// ============================================================================

export type Tool =
    | 'select'
    | 'pan'
    | 'draw'
    | 'text'
    | 'code'
    | 'sticky'
    | 'rectangle'
    | 'ellipse'
    | 'line'
    | 'arrow'
    | 'connector'   // AFFiNE-style connector with multiple modes
    | 'frame'       // Frame/group for organizing elements
    | 'mindmap'     // Mind map node
    | 'embed'       // Embedded content (web page, iframe)
    | 'image';      // Image upload

// Connector modes (AFFiNE-style)
export type ConnectorMode = 'straight' | 'elbow' | 'curve';

export interface UIState {
    activeTool: Tool;
    leftPanelOpen: boolean;
    leftPanelTab: 'tasks' | 'widgets' | 'artifacts' | 'phases' | 'history';
    rightPanelOpen: boolean;
    rightPanelTab: 'guidance' | 'ai' | 'validate' | 'generate';
    showGrid: boolean;
    snapToGrid: boolean;
    gridSize: number;
    showMinimap: boolean;
    showRulers: boolean;
}

export const uiAtom = atomWithStorage<UIState>('canvas-ui-state', {
    activeTool: 'select',
    leftPanelOpen: false,
    leftPanelTab: 'tasks',
    rightPanelOpen: true,
    rightPanelTab: 'ai',
    showGrid: true,
    snapToGrid: false,
    gridSize: 20,
    showMinimap: true,
    showRulers: false
});

export const activeToolAtom = atom(
    (get) => get(uiAtom).activeTool,
    (get, set, tool: Tool) => {
        set(uiAtom, { ...get(uiAtom), activeTool: tool });
    }
);

export const leftPanelOpenAtom = atom(
    (get) => get(uiAtom).leftPanelOpen,
    (get, set, open: boolean) => {
        set(uiAtom, { ...get(uiAtom), leftPanelOpen: open });
    }
);

export const rightPanelOpenAtom = atom(
    (get) => get(uiAtom).rightPanelOpen,
    (get, set, open: boolean) => {
        set(uiAtom, { ...get(uiAtom), rightPanelOpen: open });
    }
);

// ============================================================================
// TASK STATE
// ============================================================================

export type TaskStatus = 'todo' | 'in-progress' | 'blocked' | 'done';
export type TaskPriority = 'low' | 'medium' | 'high' | 'critical';

export interface Task {
    id: string;
    title: string;
    description?: string;
    status: TaskStatus;
    priority: TaskPriority;
    assignedTo?: string;
    phase?: string;
    estimate?: number;  // hours
    timeSpent?: number; // hours
    dueDate?: Date;
    tags: string[];
    nodeIds: string[];  // Associated canvas nodes
    blockedBy?: string[];  // Task IDs
    blocks?: string[];     // Task IDs
    createdAt: Date;
    updatedAt: Date;
}

export const tasksAtom = atom<Task[]>([]);

export const tasksByPhaseAtom = atom(
    (get) => {
        const tasks = get(tasksAtom);
        const byPhase: Record<string, Task[]> = {};

        for (const task of tasks) {
            const phase = task.phase || 'unassigned';
            if (!byPhase[phase]) {
                byPhase[phase] = [];
            }
            byPhase[phase].push(task);
        }

        return byPhase;
    }
);

export const blockedTasksAtom = atom(
    (get) => {
        return get(tasksAtom).filter(task => task.status === 'blocked');
    }
);

export const nextBestTaskAtom = atom(
    (get) => {
        const tasks = get(tasksAtom);

        // Find highest priority unblocked task not in progress or done
        return tasks
            .filter(task =>
                task.status !== 'done' &&
                task.status !== 'in-progress' &&
                task.status !== 'blocked'
            )
            .sort((a, b) => {
                // Sort by priority
                const priorityOrder = { critical: 0, high: 1, medium: 2, low: 3 };
                return priorityOrder[a.priority] - priorityOrder[b.priority];
            })[0];
    }
);

// ============================================================================
// LIFECYCLE STATE
// ============================================================================

export type LifecyclePhase =
    | 'intent'
    | 'shape'
    | 'validate'
    | 'generate'
    | 'run'
    | 'observe'
    | 'improve';

export interface PhaseProgress {
    phase: LifecyclePhase;
    progress: number;  // 0-100
    completed: boolean;
    criteriaTotal: number;
    criteriaCompleted: number;
}

export const lifecycleAtom = atom({
    currentPhase: 'shape' as LifecyclePhase,
    phaseProgress: [] as PhaseProgress[]
});

export const currentPhaseAtom = atom(
    (get) => get(lifecycleAtom).currentPhase,
    (get, set, phase: LifecyclePhase) => {
        set(lifecycleAtom, { ...get(lifecycleAtom), currentPhase: phase });
    }
);

export const currentPhaseProgressAtom = atom(
    (get) => {
        const { currentPhase, phaseProgress } = get(lifecycleAtom);
        return phaseProgress.find(p => p.phase === currentPhase);
    }
);

// ============================================================================
// AI STATE
// ============================================================================

export interface AISuggestion {
    id: string;
    type: 'node' | 'connection' | 'task' | 'validation' | 'guidance';
    title: string;
    description: string;
    confidence: number;  // 0-1
    accepted: boolean;
    dismissed: boolean;
    createdAt: Date;
    data?: unknown;
}

export interface ValidationIssue {
    id: string;
    severity: 'error' | 'warning' | 'info';
    message: string;
    nodeId?: string;
    autoFixAvailable: boolean;
}

export const aiAtom = atom({
    suggestions: [] as AISuggestion[],
    validationIssues: [] as ValidationIssue[],
    validationScore: 100,
    aiPanelOpen: false,
    processing: false
});

export const aiSuggestionsAtom = atom(
    (get) => get(aiAtom).suggestions.filter(s => !s.dismissed)
);

export const validationIssuesAtom = atom(
    (get) => get(aiAtom).validationIssues
);

export const validationScoreAtom = atom(
    (get) => get(aiAtom).validationScore
);

// ============================================================================
// HISTORY STATE (Undo/Redo)
// ============================================================================

export interface HistoryState {
    past: CanvasState[];
    present: CanvasState;
    future: CanvasState[];
    maxHistoryLength: number;
}

export const historyAtom = atom<HistoryState>({
    past: [],
    present: {
        nodes: [],
        connections: [],
        selectedNodeIds: [],
        clipboard: [],
        viewport: { x: 0, y: 0, zoom: 0.5 },
        layers: [],
        groups: []
    },
    future: [],
    maxHistoryLength: 50
});

export const canUndoAtom = atom(
    (get) => get(historyAtom).past.length > 0
);

export const canRedoAtom = atom(
    (get) => get(historyAtom).future.length > 0
);

// ============================================================================
// COLLABORATION STATE
// ============================================================================

export interface Collaborator {
    id: string;
    name: string;
    avatar?: string;
    color: string;
    cursor?: { x: number; y: number };
    selectedNodeIds?: string[];
    online: boolean;
}

export const collaboratorsAtom = atom<Collaborator[]>([]);

export const onlineCollaboratorsAtom = atom(
    (get) => get(collaboratorsAtom).filter(c => c.online)
);

// ============================================================================
// PROJECT STATE
// ============================================================================

export interface ProjectMetadata {
    id: string;
    name: string;
    description?: string;
    techStack: string[];
    owner: string;
    createdAt: Date;
    updatedAt: Date;
}

export const projectAtom = atom<ProjectMetadata | null>(null);

// ============================================================================
// KEYBOARD SHORTCUTS STATE
// ============================================================================

export interface ShortcutAction {
    key: string;
    ctrl?: boolean;
    meta?: boolean;
    shift?: boolean;
    alt?: boolean;
    action: () => void;
    description: string;
}

export const shortcutsAtom = atom<ShortcutAction[]>([]);
