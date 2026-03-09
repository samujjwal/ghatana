/**
 * Custom hook for workflow execution management.
 *
 * <p><b>Purpose</b><br>
 * Provides workflow execution operations (execute, monitor, cancel).
 * Integrates with Jotai state management and API client.
 *
 * <p><b>Architecture</b><br>
 * - Workflow execution operations
 * - State management via Jotai
 * - Error handling and loading states
 * - Real-time status tracking
 *
 * @doc.type hook
 * @doc.purpose Workflow execution management
 * @doc.layer frontend
 * @doc.pattern Custom Hook
 */

import { useCallback, useState } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import { workflowClient } from '../../../lib/api/workflow-client';
import {
  executionAtom,
  startExecutionAtom,
  completeExecutionAtom,
  resetExecutionAtom,
} from '../stores/execution.store';
import type { ExecuteWorkflowRequest, ExecutionStatusValue } from '../types/workflow.types';

/**
 * Hook state type.
 *
 * @doc.type type
 */
export type UseWorkflowExecutionState = {
  loading: boolean;
  error: string | null;
};

/**
 * useWorkflowExecution hook.
 *
 * Provides workflow execution operations and state management.
 *
 * @returns execution operations and state
 */
export function useWorkflowExecution() {
  const [state, setState] = useState<UseWorkflowExecutionState>({
    loading: false,
    error: null,
  });

  const [execution, setExecution] = useAtom(executionAtom);
  const startExecution = useSetAtom(startExecutionAtom);
  const completeExecution = useSetAtom(completeExecutionAtom);
  const resetExecution = useSetAtom(resetExecutionAtom);

  /**
   * Executes a workflow.
   *
   * @param workflowId the workflow ID
   * @param request optional execution request
   * @returns the execution ID
   */
  const executeWorkflow = useCallback(
    async (workflowId: string, request: ExecuteWorkflowRequest = {}) => {
      setState({ loading: true, error: null });
      try {
        const response = await workflowClient.executeWorkflow(workflowId, request);

        // Load the execution
        const executionData = await workflowClient.getExecutionStatus(response.executionId);
        startExecution(executionData);

        return response.executionId;
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to execute workflow';
        setState({ loading: false, error: message });
        throw error;
      } finally {
        setState((prev) => ({ ...prev, loading: false }));
      }
    },
    [startExecution]
  );

  /**
   * Gets execution status.
   *
   * @param executionId the execution ID
   * @returns the execution status
   */
  const getExecutionStatus = useCallback(
    async (executionId: string) => {
      setState({ loading: true, error: null });
      try {
        const executionData = await workflowClient.getExecutionStatus(executionId);
        startExecution(executionData);
        return executionData;
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to get execution status';
        setState({ loading: false, error: message });
        throw error;
      } finally {
        setState((prev) => ({ ...prev, loading: false }));
      }
    },
    [startExecution]
  );

  /**
   * Cancels an execution.
   *
   * @param executionId the execution ID
   */
  const cancelExecution = useCallback(
    async (executionId: string) => {
      setState({ loading: true, error: null });
      try {
        await workflowClient.cancelExecution(executionId);
        completeExecution('CANCELLED');
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to cancel execution';
        setState({ loading: false, error: message });
        throw error;
      } finally {
        setState((prev) => ({ ...prev, loading: false }));
      }
    },
    [completeExecution]
  );

  /**
   * Marks execution as complete.
   *
   * @param status the final status
   * @param output optional output data
   * @param error optional error message
   */
  const markComplete = useCallback(
    (status: ExecutionStatusValue, output?: unknown, error?: string) => {
      completeExecution(status, output, error);
    },
    [completeExecution]
  );

  return {
    // State
    execution,
    loading: state.loading,
    error: state.error,

    // Operations
    executeWorkflow,
    getExecutionStatus,
    cancelExecution,
    markComplete,
    resetExecution,
  };
}
