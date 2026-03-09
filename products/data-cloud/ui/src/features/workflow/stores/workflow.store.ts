/**
 * Workflow state management store using Jotai.
 *
 * <p><b>Purpose</b><br>
 * Manages workflow definition state including nodes, edges, and selection.
 * Provides atoms for workflow CRUD operations and UI state.
 *
 * <p><b>Architecture</b><br>
 * - Base atoms for workflow and UI state
 * - Derived atoms for computed values
 * - Action atoms for state mutations
 * - Feature-scoped state management
 *
 * @doc.type store
 * @doc.purpose Workflow state management
 * @doc.layer frontend
 * @doc.pattern Jotai Store
 */

import { atom, Getter, SetStateAction, WritableAtom } from 'jotai';
import type {
  WorkflowDefinition,
  WorkflowNode,
  WorkflowEdge,
  ExecutionStatus,
  NodeExecutionStatus,
} from '../types/workflow.types';

type Setter = <Value, Result extends void | Promise<void>>(
  writeAtom: WritableAtom<Value, [SetStateAction<Value>], Result>,
  update: SetStateAction<Value>
) => Result;

type AtomAction<Args extends unknown[], Result = void> = WritableAtom<null, Args, Result>;

export interface WorkflowState extends WorkflowDefinition {
  isDirty: boolean;
  isSaving: boolean;
  error: string | null;
  executionStatus?: {
    status: ExecutionStatus;
    nodeStatuses: NodeExecutionStatus[];
  };
}

const MAX_HISTORY = 100;

const cloneWorkflowState = (workflow: WorkflowState): WorkflowState =>
  JSON.parse(JSON.stringify(workflow)) as WorkflowState;

const initialWorkflowState: WorkflowState = {
  id: '',
  tenantId: '',
  collectionId: '',
  name: 'Untitled Workflow',
  description: '',
  status: 'DRAFT',
  version: 1,
  active: false,
  nodes: [],
  edges: [],
  triggers: [],
  variables: {},
  tags: [],
  createdBy: '',
  updatedBy: '',
  createdAt: new Date(0).toISOString(),
  updatedAt: new Date(0).toISOString(),
  isDirty: false,
  isSaving: false,
  error: null,
};

// Base workflow state atom
export const workflowAtom = atom<WorkflowState>(initialWorkflowState);

// Atom for selected node ID
export const selectedNodeIdAtom = atom<string | null>(null);

// Atom for selected edge ID
export const selectedEdgeIdAtom = atom<string | null>(null);

// Atom for workflow history
export const workflowHistoryAtom = atom<WorkflowState[]>([
  cloneWorkflowState(initialWorkflowState),
]);

// Atom for history index
export const historyIndexAtom = atom<number>(0);

// Derived atoms
export const selectedNodeAtom = atom(
  (get: Getter) => {
    const workflow = get(workflowAtom);
    const selectedId = get(selectedNodeIdAtom);
    return selectedId ? workflow.nodes.find((n) => n.id === selectedId) : null;
  }
);

export const selectedEdgeAtom = atom(
  (get: Getter) => {
    const workflow = get(workflowAtom);
    const selectedId = get(selectedEdgeIdAtom);
    return selectedId ? workflow.edges.find((edge) => edge.id === selectedId) : null;
  }
);

export const canUndoAtom = atom((get: Getter) => get(historyIndexAtom) > 0);

export const canRedoAtom = atom((get: Getter) => {
  const history = get(workflowHistoryAtom);
  return get(historyIndexAtom) < history.length - 1;
});

/**
 * Derived atom: node count.
 *
 * Returns the total number of nodes in the workflow.
 */
export const nodeCountAtom = atom((get: Getter) => get(workflowAtom).nodes.length);

/**
 * Derived atom: edge count.
 *
 * Returns the total number of edges in the workflow.
 */
export const edgeCountAtom = atom((get: Getter) => get(workflowAtom).edges.length);

/**
 * Derived atom: triggers count.
 */
export const triggerCountAtom = atom((get: Getter) => get(workflowAtom).triggers.length);

/**
 * Action atom: add node.
 *
 * Adds a new node to the workflow and updates history.
 *
 * @param node the node to add
 */
const pushHistory = (get: Getter, set: Setter, workflow: WorkflowState) => {
  const history = get(workflowHistoryAtom);
  const index = get(historyIndexAtom);
  const truncated = history.slice(0, index + 1);
  truncated.push(cloneWorkflowState(workflow));

  while (truncated.length > MAX_HISTORY) {
    truncated.shift();
  }

  const nextIndex = truncated.length - 1;
  set(workflowHistoryAtom, truncated);
  set(historyIndexAtom, nextIndex);
};

export const addNodeAtom: AtomAction<[WorkflowNode]> = atom(
  null,
  (get: Getter, set: Setter, node: WorkflowNode) => {
    const workflow = get(workflowAtom);
    const updatedWorkflow: WorkflowState = {
      ...workflow,
      nodes: [...workflow.nodes, node],
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Updates an existing edge and updates history.
 *
 * @param edgeId the ID of the edge to update
 * @param updates the properties to update
 */
export const updateEdgeAtom: AtomAction<[string, Partial<WorkflowEdge>]> = atom(
  null,
  (get: Getter, set: Setter, edgeId: string, updates: Partial<WorkflowEdge>) => {
    const workflow = get(workflowAtom);
    const updatedWorkflow: WorkflowState = {
      ...workflow,
      edges: workflow.edges.map((edge) =>
        edge.id === edgeId ? { ...edge, ...updates } : edge
      ),
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Action atom: update node
 *
 * Updates an existing node and updates history.
 *
 * @param nodeId the ID of the node to update
 * @param updates the properties to update
 */
export const updateNodeAtom: AtomAction<[string, Partial<WorkflowNode>]> = atom(
  null,
  (get: Getter, set: Setter, nodeId: string, updates: Partial<WorkflowNode>) => {
    const workflow = get(workflowAtom);
    const updatedWorkflow: WorkflowState = {
      ...workflow,
      nodes: workflow.nodes.map((node) =>
        node.id === nodeId ? { ...node, ...updates } : node
      ),
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Action atom: delete edge
 *
 * Removes an edge from the workflow.
 *
 * @param edgeId the ID of the edge to delete
 */
export const deleteEdgeAtom: AtomAction<[string]> = atom(
  null,
  (get: Getter, set: Setter, edgeId: string) => {
    const workflow = get(workflowAtom);
    const updatedWorkflow: WorkflowState = {
      ...workflow,
      edges: workflow.edges.filter((edge) => edge.id !== edgeId),
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    set(selectedEdgeIdAtom, (current) => (current === edgeId ? null : current));
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Action atom: add node
 *
 * Adds a new node to the workflow and updates history.
 *
 * @param node the node to add
 */
/**
 * Action atom: add edge.
 */
export const addEdgeAtom: AtomAction<[WorkflowEdge]> = atom(
  null,
  (get: Getter, set: Setter, edge: WorkflowEdge) => {
    const workflow = get(workflowAtom);

    // Prevent duplicate edges with same id
    if (workflow.edges.some((existing) => existing.id === edge.id)) {
      return;
    }

    const updatedWorkflow: WorkflowState = {
      ...workflow,
      edges: [...workflow.edges, edge],
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Action atom: delete node and associated edges.
 */
export const deleteNodeAtom: AtomAction<[string]> = atom(
  null,
  (get: Getter, set: Setter, nodeId: string) => {
    const workflow = get(workflowAtom);
    const updatedWorkflow: WorkflowState = {
      ...workflow,
      nodes: workflow.nodes.filter((node) => node.id !== nodeId),
      edges: workflow.edges.filter(
        (edge) => edge.source !== nodeId && edge.target !== nodeId
      ),
      isDirty: true,
      updatedAt: new Date().toISOString(),
    };

    set(workflowAtom, updatedWorkflow);
    set(selectedNodeIdAtom, (current) => (current === nodeId ? null : current));
    set(selectedEdgeIdAtom, (current) =>
      current && updatedWorkflow.edges.every((edge) => edge.id !== current) ? null : current
    );
    pushHistory(get, set, updatedWorkflow);
  }
);

/**
 * Action atom: undo.
 */
export const undoAtom: AtomAction<[]> = atom(
  null,
  (get: Getter, set: Setter) => {
    const index = get(historyIndexAtom);
    if (index > 0) {
      const history = get(workflowHistoryAtom);
      const previous = cloneWorkflowState(history[index - 1]);
      set(workflowAtom, previous);
      set(historyIndexAtom, index - 1);
      set(selectedNodeIdAtom, null);
    }
  }
);

/**
 * Action atom: redo.
 */
export const redoAtom: AtomAction<[]> = atom(
  null,
  (get: Getter, set: Setter) => {
    const index = get(historyIndexAtom);
    const history = get(workflowHistoryAtom);
    if (index < history.length - 1) {
      const next = cloneWorkflowState(history[index + 1]);
      set(workflowAtom, next);
      set(historyIndexAtom, index + 1);
      set(selectedNodeIdAtom, null);
    }
  }
);

/**
 * Action atom: reset workflow to defaults.
 */
export const resetWorkflowAtom: AtomAction<[]> = atom(
  null,
  (_get: Getter, set: Setter) => {
    const snapshot = cloneWorkflowState(initialWorkflowState);
    set(workflowAtom, snapshot);
    set(workflowHistoryAtom, [snapshot]);
    set(historyIndexAtom, 0);
    set(selectedNodeIdAtom, null);
  }
);

/**
 * Action atom: load workflow definition.
 */
export const loadWorkflowAtom: AtomAction<[WorkflowDefinition]> = atom(
  null,
  (_get: Getter, set: Setter, workflow: WorkflowDefinition) => {
    const workflowState: WorkflowState = {
      ...workflow,
      triggers: workflow.triggers ?? [],
      variables: workflow.variables ?? {},
      tags: workflow.tags ?? [],
      isDirty: false,
      isSaving: false,
      error: null,
    };

    const snapshot = cloneWorkflowState(workflowState);
    set(workflowAtom, snapshot);
    set(workflowHistoryAtom, [snapshot]);
    set(historyIndexAtom, 0);
    set(selectedNodeIdAtom, null);
  }
);
