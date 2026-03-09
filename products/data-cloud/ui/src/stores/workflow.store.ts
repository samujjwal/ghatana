import { atom } from 'jotai';
import type {
  WorkflowDefinition,
  WorkflowNode,
  WorkflowEdge,
  WorkflowExecution,
} from '@/types/workflow.types';

/**
 * Workflow store using Jotai atoms.
 *
 * <p><b>Purpose</b><br>
 * Manages workflow state including definition, execution, and UI state.
 * Uses Jotai for app-scoped state management.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useAtom } from 'jotai';
 * import { workflowAtom, selectedNodeAtom } from '@/stores/workflow.store';
 *
 * function WorkflowEditor() {
 *   const [workflow, setWorkflow] = useAtom(workflowAtom);
 *   const [selectedNodeId, setSelectedNodeId] = useAtom(selectedNodeAtom);
 *
 *   return (
 *     <div>
 *       {workflow && <h1>{workflow.name}</h1>}
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * @doc.type store
 * @doc.purpose Jotai atoms for workflow state management
 * @doc.layer frontend
 */

/**
 * Current workflow definition.
 *
 * @doc.atom
 * @doc.purpose Stores the active workflow definition
 */
export const workflowAtom = atom<WorkflowDefinition | null>(null);

/**
 * Workflow nodes.
 *
 * @doc.atom
 * @doc.purpose Stores workflow nodes
 */
export const nodesAtom = atom<WorkflowNode[]>([]);

/**
 * Workflow edges.
 *
 * @doc.atom
 * @doc.purpose Stores workflow edges
 */
export const edgesAtom = atom<WorkflowEdge[]>([]);

/**
 * Selected node ID.
 *
 * @doc.atom
 * @doc.purpose Stores the currently selected node ID
 */
export const selectedNodeAtom = atom<string | null>(null);

/**
 * Selected edge ID.
 *
 * @doc.atom
 * @doc.purpose Stores the currently selected edge ID
 */
export const selectedEdgeAtom = atom<string | null>(null);

/**
 * Workflow execution state.
 *
 * @doc.atom
 * @doc.purpose Stores the current workflow execution
 */
export const executionAtom = atom<WorkflowExecution | null>(null);

/**
 * Workflow execution status.
 *
 * @doc.atom
 * @doc.purpose Stores the current execution status
 */
export const executionStatusAtom = atom<'idle' | 'running' | 'completed' | 'failed'>(
  'idle'
);

/**
 * Workflow dirty flag.
 *
 * @doc.atom
 * @doc.purpose Indicates if workflow has unsaved changes
 */
export const isDirtyAtom = atom<boolean>(false);

/**
 * Workflow validation errors.
 *
 * @doc.atom
 * @doc.purpose Stores workflow validation errors
 */
export const validationErrorsAtom = atom<string[]>([]);

/**
 * Zoom level for canvas.
 *
 * @doc.atom
 * @doc.purpose Stores canvas zoom level
 */
export const zoomAtom = atom<number>(1);

/**
 * Pan position for canvas.
 *
 * @doc.atom
 * @doc.purpose Stores canvas pan position
 */
export const panAtom = atom<{ x: number; y: number }>({ x: 0, y: 0 });

/**
 * Undo/redo history.
 *
 * @doc.atom
 * @doc.purpose Stores workflow history for undo/redo
 */
export const historyAtom = atom<WorkflowDefinition[]>([]);

/**
 * Current history index.
 *
 * @doc.atom
 * @doc.purpose Current position in undo/redo history
 */
export const historyIndexAtom = atom<number>(-1);

/**
 * Add node to workflow.
 *
 * @doc.atom
 * @doc.purpose Action atom to add a node
 */
export const addNodeAtom = atom(
  null,
  (get, set, node: WorkflowNode) => {
    const nodes = get(nodesAtom);
    set(nodesAtom, [...nodes, node]);
    set(isDirtyAtom, true);
  }
);

/**
 * Remove node from workflow.
 *
 * @doc.atom
 * @doc.purpose Action atom to remove a node
 */
export const removeNodeAtom = atom(
  null,
  (get, set, nodeId: string) => {
    const nodes = get(nodesAtom);
    const edges = get(edgesAtom);

    // Remove node
    set(nodesAtom, nodes.filter((n) => n.id !== nodeId));

    // Remove connected edges
    set(edgesAtom, edges.filter((e) => e.source !== nodeId && e.target !== nodeId));

    // Clear selection if this node was selected
    if (get(selectedNodeAtom) === nodeId) {
      set(selectedNodeAtom, null);
    }

    set(isDirtyAtom, true);
  }
);

/**
 * Update node in workflow.
 *
 * @doc.atom
 * @doc.purpose Action atom to update a node
 */
export const updateNodeAtom = atom(
  null,
  (get, set, nodeId: string, updates: Partial<WorkflowNode>) => {
    const nodes = get(nodesAtom);
    set(
      nodesAtom,
      nodes.map((n) => (n.id === nodeId ? { ...n, ...updates } : n))
    );
    set(isDirtyAtom, true);
  }
);

/**
 * Add edge to workflow.
 *
 * @doc.atom
 * @doc.purpose Action atom to add an edge
 */
export const addEdgeAtom = atom(
  null,
  (get, set, edge: WorkflowEdge) => {
    const edges = get(edgesAtom);
    set(edgesAtom, [...edges, edge]);
    set(isDirtyAtom, true);
  }
);

/**
 * Remove edge from workflow.
 *
 * @doc.atom
 * @doc.purpose Action atom to remove an edge
 */
export const removeEdgeAtom = atom(
  null,
  (get, set, edgeId: string) => {
    const edges = get(edgesAtom);
    set(edgesAtom, edges.filter((e) => e.id !== edgeId));

    if (get(selectedEdgeAtom) === edgeId) {
      set(selectedEdgeAtom, null);
    }

    set(isDirtyAtom, true);
  }
);

/**
 * Save workflow to history.
 *
 * @doc.atom
 * @doc.purpose Action atom to save workflow state to history
 */
export const saveToHistoryAtom = atom(
  null,
  (get, set) => {
    const workflow = get(workflowAtom);
    if (!workflow) return;

    const history = get(historyAtom);
    const index = get(historyIndexAtom);

    // Remove any future history if we're not at the end
    const newHistory = history.slice(0, index + 1);
    newHistory.push(workflow);

    set(historyAtom, newHistory);
    set(historyIndexAtom, newHistory.length - 1);
  }
);

/**
 * Undo last change.
 *
 * @doc.atom
 * @doc.purpose Action atom to undo last change
 */
export const undoAtom = atom(
  null,
  (get, set) => {
    const history = get(historyAtom);
    const index = get(historyIndexAtom);

    if (index > 0) {
      const newIndex = index - 1;
      set(historyIndexAtom, newIndex);
      set(workflowAtom, history[newIndex]);
    }
  }
);

/**
 * Redo last undone change.
 *
 * @doc.atom
 * @doc.purpose Action atom to redo last undone change
 */
export const redoAtom = atom(
  null,
  (get, set) => {
    const history = get(historyAtom);
    const index = get(historyIndexAtom);

    if (index < history.length - 1) {
      const newIndex = index + 1;
      set(historyIndexAtom, newIndex);
      set(workflowAtom, history[newIndex]);
    }
  }
);
