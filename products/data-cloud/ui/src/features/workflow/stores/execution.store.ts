/**
 * Workflow execution state management store using Jotai.
 *
 * <p><b>Purpose</b><br>
 * Manages workflow execution state including status, node statuses, and progress.
 * Provides atoms for tracking real-time execution updates via WebSocket.
 *
 * <p><b>Architecture</b><br>
 * - Base atoms for execution state
 * - Derived atoms for computed values
 * - Action atoms for state mutations
 * - WebSocket message handling
 *
 * @doc.type store
 * @doc.purpose Workflow execution state management
 * @doc.layer frontend
 * @doc.pattern Jotai Store
 */

import { atom } from 'jotai';
import { 
  ExecutionStatus, 
  NodeState, 
  type ExecutionStatusValue, 
  type NodeStateValue,
  type NodeExecutionStatus,
  type WorkflowExecution
} from '@/features/workflow/types/workflow.types';

/**
 * Execution state type.
 *
 * @doc.type type
 */
interface ExecutionState {
  id: string | null;
  workflowId: string | null;
  status: ExecutionStatus;
  progress: number;
  startedAt: string | null;
  completedAt: string | null;
  duration: number | null;
  nodeStatuses: NodeExecutionStatus[];
  output: unknown | null;
  error: string | null;
  isConnected: boolean;
}

const initialExecutionState: ExecutionState = {
  id: null,
  workflowId: null,
  status: ExecutionStatus.PENDING,
  progress: 0,
  startedAt: null,
  completedAt: null,
  duration: null,
  nodeStatuses: [],
  output: null,
  error: null,
  isConnected: false,
};

/**
 * Base execution atom.
 *
 * Stores the current execution state and metadata.
 */
export const executionAtom = atom<ExecutionState>(initialExecutionState);

/**
 * WebSocket connection status atom.
 *
 * Tracks whether the WebSocket is connected.
 */
export const wsConnectedAtom = atom<boolean>(false);

/**
 * Normalizes execution status to ensure it's a valid ExecutionStatus.
 */
const normalizeExecutionStatus = (status?: ExecutionStatusValue | null): ExecutionStatus => {
  if (!status) return ExecutionStatus.PENDING;
  
  const statusStr = status.toString().toUpperCase();
  switch (statusStr) {
    case 'PENDING':
    case 'RUNNING':
    case 'COMPLETED':
    case 'FAILED':
    case 'CANCELLED':
      return statusStr as ExecutionStatus;
    default:
      return ExecutionStatus.PENDING;
  }
};

/**
 * Normalizes node state to ensure it's a valid NodeState.
 */
const normalizeNodeState = (state?: NodeStateValue | null): NodeState => {
  if (!state) return NodeState.PENDING;
  
  const stateStr = state.toString().toUpperCase();
  switch (stateStr) {
    case 'PENDING':
    case 'RUNNING':
    case 'COMPLETED':
    case 'FAILED':
    case 'SKIPPED':
      return stateStr as NodeState;
    default:
      return NodeState.PENDING;
  }
};

/**
 * Normalizes node statuses to ensure all states are valid.
 */
const normalizeNodeStatuses = (statuses: NodeExecutionStatus[]): NodeExecutionStatus[] =>
  statuses.map((status) => ({
    ...status,
    state: normalizeNodeState(status.state),
  }));

/**
 * Checks if the given status matches the expected status.
 */
const matchesStatus = (
  status: ExecutionStatusValue,
  expected: ExecutionStatus
): boolean => normalizeExecutionStatus(status) === expected;

/**
 * Checks if the given state matches the expected state.
 */
const matchesState = (state: NodeStateValue, expected: NodeState): boolean =>
  normalizeNodeState(state) === expected;

// Derived atoms
export const isExecutingAtom = atom((get) => {
  const execution = get(executionAtom);
  return matchesStatus(execution.status, ExecutionStatus.RUNNING);
});

export const isExecutionCompleteAtom = atom((get) => {
  const execution = get(executionAtom);
  return (
    matchesStatus(execution.status, ExecutionStatus.COMPLETED) ||
    matchesStatus(execution.status, ExecutionStatus.FAILED) ||
    matchesStatus(execution.status, ExecutionStatus.CANCELLED)
  );
});

export const executionSuccessAtom = atom((get) => {
  const execution = get(executionAtom);
  return matchesStatus(execution.status, ExecutionStatus.COMPLETED);
});

export const activeNodeIdAtom = atom((get) => {
  const execution = get(executionAtom);
  const runningNode = execution.nodeStatuses.find((ns) =>
    matchesState(ns.state, NodeState.RUNNING)
  );
  return runningNode?.nodeId || null;
});

export const completedNodesCountAtom = atom((get) => {
  const execution = get(executionAtom);
  return execution.nodeStatuses.filter((ns) => matchesState(ns.state, NodeState.COMPLETED)).length;
});

export const failedNodesCountAtom = atom((get) => {
  const execution = get(executionAtom);
  return execution.nodeStatuses.filter((ns) => matchesState(ns.state, NodeState.FAILED)).length;
});

export const totalNodesCountAtom = atom((get) => {
  const execution = get(executionAtom);
  return execution.nodeStatuses.length;
});

// Action atoms
export const startExecutionAtom = atom(
  null,
  (get, set, execution: WorkflowExecution) => {
    const state: ExecutionState = {
      id: execution.id,
      workflowId: execution.workflowId,
      status: normalizeExecutionStatus(execution.status),
      progress: execution.progress,
      startedAt: execution.startedAt,
      completedAt: execution.completedAt || null,
      duration: execution.duration || null,
      nodeStatuses: normalizeNodeStatuses(execution.nodeStatuses ?? []),
      output: execution.output || null,
      error: execution.error || null,
      isConnected: true,
    };

    set(executionAtom, state);
    set(wsConnectedAtom, true);
  }
);

export const updateExecutionAtom = atom(
  null,
  (get, set, update: Partial<ExecutionState> & { nodeStatuses?: NodeExecutionStatus[]; nodeStatus?: NodeExecutionStatus; executionId?: string }) => {
    const execution = get(executionAtom);
    
    // Normalize status if provided
    if ('status' in update) {
      update.status = normalizeExecutionStatus(update.status);
    }
    
    // Normalize node statuses if provided
    if (update.nodeStatuses) {
      update.nodeStatuses = normalizeNodeStatuses(update.nodeStatuses);
    }

    if (execution.id !== update.executionId) {
      return; // Ignore updates for different executions
    }

    const updatedExecution: ExecutionState = {
      ...execution,
      status: update.status || execution.status,
      progress: update.progress !== undefined ? update.progress : execution.progress,
      error: update.error || execution.error,
    };

    // Update node status if provided
    if (update.nodeStatus) {
      const normalizedNodeStatus: NodeExecutionStatus = {
        ...update.nodeStatus,
        state: normalizeNodeState(update.nodeStatus.state),
      };
      const nodeIndex = updatedExecution.nodeStatuses.findIndex(
        (ns) => ns.nodeId === normalizedNodeStatus.nodeId
      );

      if (nodeIndex >= 0) {
        updatedExecution.nodeStatuses = [
          ...updatedExecution.nodeStatuses.slice(0, nodeIndex),
          normalizedNodeStatus,
          ...updatedExecution.nodeStatuses.slice(nodeIndex + 1),
        ];
      } else {
        updatedExecution.nodeStatuses = [
          ...updatedExecution.nodeStatuses,
          normalizedNodeStatus,
        ];
      }
    }

    // Update completion time if execution is complete
    if (
      matchesStatus(updatedExecution.status, ExecutionStatus.COMPLETED) ||
      matchesStatus(updatedExecution.status, ExecutionStatus.FAILED) ||
      matchesStatus(updatedExecution.status, ExecutionStatus.CANCELLED)
    ) {
      updatedExecution.completedAt = new Date().toISOString();
      if (updatedExecution.startedAt) {
        updatedExecution.duration =
          new Date(updatedExecution.completedAt).getTime() -
          new Date(updatedExecution.startedAt).getTime();
      }
    }

    set(executionAtom, updatedExecution);
  }
);

export const updateNodeStatusAtom = atom(
  null,
  (get, set, nodeStatus: NodeExecutionStatus) => {
    const execution = get(executionAtom);
    const nodeIndex = execution.nodeStatuses.findIndex(
      (ns) => ns.nodeId === nodeStatus.nodeId
    );

    const normalizedNodeStatus: NodeExecutionStatus = {
      ...nodeStatus,
      state: normalizeNodeState(nodeStatus.state),
    };

    let updatedNodeStatuses: NodeExecutionStatus[];

    if (nodeIndex >= 0) {
      updatedNodeStatuses = [
        ...execution.nodeStatuses.slice(0, nodeIndex),
        normalizedNodeStatus,
        ...execution.nodeStatuses.slice(nodeIndex + 1),
      ];
    } else {
      updatedNodeStatuses = [...execution.nodeStatuses, normalizedNodeStatus];
    }

    set(executionAtom, {
      ...execution,
      nodeStatuses: updatedNodeStatuses,
    });
  }
);

export const setWsConnectedAtom = atom(null, (get, set, connected: boolean) => {
  set(wsConnectedAtom, connected);
  set(executionAtom, (prev) => ({
    ...prev,
    isConnected: connected,
  }));
});

export const completeExecutionAtom = atom(
  null,
  (get, set, status: ExecutionStatusValue, output?: unknown, error?: string) => {
    const execution = get(executionAtom);
    const now = new Date().toISOString();

    set(executionAtom, {
      ...execution,
      status: normalizeExecutionStatus(status),
      output: output || null,
      error: error || null,
      completedAt: now,
      duration: execution.startedAt
        ? new Date(now).getTime() - new Date(execution.startedAt).getTime()
        : null,
    });
  }
);

export const resetExecutionAtom = atom(null, (get, set) => {
  set(executionAtom, initialExecutionState);
  set(wsConnectedAtom, false);
});
