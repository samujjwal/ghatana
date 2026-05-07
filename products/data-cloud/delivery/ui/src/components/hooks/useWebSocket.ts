import { useEffect, useCallback, useState } from 'react';
import { useAtom } from 'jotai';
import { executionAtom, executionStatusAtom } from '@/stores/workflow.store';
import wsClient, { type WebSocketEvent, type ConnectionState } from '@/lib/websocket/client';
import SessionBootstrap from '@/lib/auth/session';
import type { WorkflowExecution, NodeExecutionStatus, ExecutionStatusValue } from '@/types/workflow.types';

/**
 * Hook for WebSocket execution stream subscription.
 *
 * <p><b>Purpose</b><br>
 * Subscribes to real-time workflow execution updates via WebSocket.
 * Handles connection lifecycle, message routing, and state synchronization.
 *
 * <p><b>Features</b><br>
 * - Real-time execution status updates
 * - Node progress tracking
 * - Error notifications
 * - Automatic reconnection
 * - Message queuing for offline scenarios
 * - Connection state tracking
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useExecutionStream } from '@/components/hooks/useWebSocket';
 *
 * export function ExecutionMonitor() {
 *   const { isConnected, error } = useExecutionStream('execution-123');
 *
 *   return (
 *     <div>
 *       {isConnected ? 'Connected' : 'Disconnected'}
 *     </div>
 *   );
 * }
 * }</pre>
 *
 * @doc.type hook
 * @doc.purpose WebSocket execution stream subscription
 * @doc.layer frontend
 * @param executionId the execution ID to subscribe to
 * @param wsUrl optional WebSocket URL
 * @returns hook interface
 */

export interface UseExecutionStreamReturn {
  /**
   * Whether connected to WebSocket
   */
  isConnected: boolean;

  /**
   * Connection error if any
   */
  error: Error | null;

  /**
   * Current execution
   */
  execution: WorkflowExecution | null;

  /**
   * Execution status
   */
  status: 'idle' | 'running' | 'completed' | 'failed';

  /**
   * Disconnect from WebSocket
   */
  disconnect: () => void;

  /**
   * Reconnect to WebSocket
   */
  reconnect: () => void;
}

/**
 * Hook for WebSocket execution stream.
 *
 * @param executionId the execution ID
 * @param wsUrl optional WebSocket URL
 * @returns hook interface
 */
/**
 * Payload shape for execution-related WebSocket events.
 */
interface ExecutionEventPayload {
  executionId: string;
  nodeId?: string;
  workflowId?: string;
  tenantId?: string;
  progress?: number;
  status?: ExecutionStatusValue;
  nodeName?: string;
  output?: Record<string, unknown>;
  error?: string;
  data?: Record<string, unknown>;
}

export function useExecutionStream(
  executionId: string,
  _wsUrl?: string
): UseExecutionStreamReturn {
  const [execution, setExecution] = useAtom(executionAtom);
  const [status, setStatus] = useAtom(executionStatusAtom);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);

  useEffect(() => {
    if (!executionId) return;

    // Track connection state via the canonical wsClient
    const unsubscribeState = wsClient.onStateChange((state: ConnectionState) => {
      setIsConnected(state === 'connected');
    });

    // Ensure connected
    wsClient.connect();

    // Handler factories — all return unsubscribe fns from subscribe()

    const unsubStart = wsClient.subscribe<ExecutionEventPayload>('execution-start', (event: WebSocketEvent<ExecutionEventPayload>) => {
      if (event.payload.executionId !== executionId) return;
      setExecution({
        id: event.payload.executionId,
        workflowId: event.payload.workflowId ?? '',
        status: 'RUNNING',
        progress: 0,
        nodeStatuses: [],
        nodeExecutions: [],
        tenantId: event.payload.tenantId ?? SessionBootstrap.getTenantId() ?? '',
        startedAt: event.timestamp,
      });
    });

    const unsubUpdate = wsClient.subscribe<ExecutionEventPayload>('execution-update', (event: WebSocketEvent<ExecutionEventPayload>) => {
      if (event.payload.executionId !== executionId) return;
      setExecution((prev) =>
        prev
          ? {
              ...prev,
              progress: event.payload.progress ?? prev.progress,
              status: event.payload.status ?? prev.status,
              output: event.payload.output ?? prev.output,
            }
          : null
      );
    });

    const handleNodeEvent = (state: 'RUNNING' | 'COMPLETED' | 'FAILED') =>
      (event: WebSocketEvent<ExecutionEventPayload>) => {
        if (event.payload.executionId !== executionId) return;
        setExecution((prev) => {
          if (!prev) return null;
          const nodeExecutions = [...(prev.nodeExecutions ?? [])];
          const idx = nodeExecutions.findIndex((n) => n.nodeId === event.payload.nodeId);
          const nodeStatus: NodeExecutionStatus = {
            nodeId: event.payload.nodeId ?? '',
            nodeName: event.payload.nodeName ?? `Node ${nodeExecutions.length + 1}`,
            state,
            startedAt: event.timestamp,
            completedAt: state !== 'RUNNING' ? event.timestamp : undefined,
            output: event.payload.output,
            error: event.payload.error,
          };
          if (idx >= 0) {
            nodeExecutions[idx] = { ...nodeExecutions[idx], ...nodeStatus };
          } else {
            nodeExecutions.push(nodeStatus);
          }
          return {
            ...prev,
            nodeExecutions,
            nodeStatuses: nodeExecutions,
            ...(state === 'FAILED'
              ? { status: 'FAILED' as ExecutionStatusValue, error: event.payload.error ?? 'Node failed' }
              : {}),
          };
        });
      };

    const unsubNodeStart = wsClient.subscribe<ExecutionEventPayload>('node-start', handleNodeEvent('RUNNING'));
    const unsubNodeComplete = wsClient.subscribe<ExecutionEventPayload>('node-complete', handleNodeEvent('COMPLETED'));
    const unsubNodeError = wsClient.subscribe<ExecutionEventPayload>('node-error', handleNodeEvent('FAILED'));

    const unsubComplete = wsClient.subscribe<ExecutionEventPayload>('execution-complete', (event: WebSocketEvent<ExecutionEventPayload>) => {
      if (event.payload.executionId !== executionId) return;
      setExecution((prev) =>
        prev
          ? { ...prev, status: 'COMPLETED', progress: 100, completedAt: event.timestamp, output: event.payload.output }
          : null
      );
    });

    const unsubError = wsClient.subscribe<ExecutionEventPayload>('execution-error', (event: WebSocketEvent<ExecutionEventPayload>) => {
      if (event.payload.executionId !== executionId) return;
      setError(new Error(event.payload.error ?? 'Execution failed'));
      setExecution((prev) =>
        prev
          ? { ...prev, status: 'FAILED', completedAt: event.timestamp, error: event.payload.error ?? 'Execution failed' }
          : null
      );
    });

    return () => {
      unsubscribeState();
      unsubStart();
      unsubUpdate();
      unsubNodeStart();
      unsubNodeComplete();
      unsubNodeError();
      unsubComplete();
      unsubError();
    };
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [executionId]);

  const reconnect = useCallback(() => {
    wsClient.disconnect();
    wsClient.connect();
    setError(null);
  }, []);

  const disconnect = useCallback(() => {
    wsClient.disconnect();
    setIsConnected(false);
  }, []);

  return {
    isConnected,
    error,
    execution,
    status,
    disconnect,
    reconnect,
  };
}
