import { useEffect, useCallback, useRef, useState } from 'react';
import { useAtom } from 'jotai';
import { executionAtom, executionStatusAtom } from '@/stores/workflow.store';
import { getWebSocketService, type WebSocketMessage, type WebSocketEventType } from '@/lib/services/websocketService';
import type { WorkflowExecution, NodeExecutionStatus, ExecutionStatusValue } from '@/types/workflow.types';
import type { WebSocketService } from '@/lib/services/websocketService';

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
  reconnect: () => Promise<void>;
}

/**
 * Hook for WebSocket execution stream.
 *
 * @param executionId the execution ID
 * @param wsUrl optional WebSocket URL
 * @returns hook interface
 */
export function useExecutionStream(
  executionId: string,
  wsUrl?: string
): UseExecutionStreamReturn {
  const [execution, setExecution] = useAtom(executionAtom);
  const [status, setStatus] = useAtom(executionStatusAtom);
  const [isConnected, setIsConnected] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const [reconnectAttempts, setReconnectAttempts] = useState<number>(0);
  const wsRef = useRef<WebSocketService | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const sameOriginWsUrl = (() => {
    if (typeof window === 'undefined') return undefined;
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${protocol}://${window.location.host}/ws`;
  })();

  const resolvedWsUrl = wsUrl ?? import.meta.env.VITE_WS_URL ?? (import.meta.env.PROD ? sameOriginWsUrl : undefined);
  const wsEnabled = Boolean(resolvedWsUrl);

  // Connect to WebSocket
  useEffect(() => {
    if (!executionId) return;

    if (!wsEnabled) {
      return;
    }

    const ws = getWebSocketService(resolvedWsUrl!);
    wsRef.current = ws;

    const handleWebSocketMessage = useCallback(
      (message: WebSocketMessage) => {
        if (message.executionId !== executionId) return;

        switch (message.type) {
          case 'execution-start':
            setExecution({
              id: message.executionId,
              workflowId: String(message.data?.workflowId ?? ''),
              status: 'RUNNING',
              progress: 0,
              nodeStatuses: [],
              nodeExecutions: [],
              tenantId: String(message.data?.tenantId ?? 'default-tenant'),
              startedAt: message.timestamp,
            });
            break;

          case 'execution-update':
            setExecution((prev) =>
              prev
                ? {
                    ...prev,
                    progress: (message.data?.progress as number) ?? prev.progress,
                    status: (message.data?.status as ExecutionStatusValue) ?? prev.status,
                    output: message.data?.output ?? prev.output,
                  }
                : null
            );
            break;

          case 'node-start':
          case 'node-complete':
            setExecution((prev) => {
              if (!prev) return null;

              const nodeExecutions = [...(prev.nodeExecutions ?? [])];
              const existingNodeIndex = nodeExecutions.findIndex((n) => n.nodeId === message.nodeId);
              const nodeStatus: NodeExecutionStatus = {
                nodeId: message.nodeId!,
                nodeName: message.data?.nodeName as string ?? `Node ${nodeExecutions.length + 1}`,
                state: message.type === 'node-start' ? 'RUNNING' : 'COMPLETED',
                startedAt: message.timestamp,
                completedAt: message.type === 'node-complete' ? message.timestamp : undefined,
                output: message.data?.output,
                error: message.data?.error as string | undefined,
              };

              if (existingNodeIndex >= 0) {
                nodeExecutions[existingNodeIndex] = {
                  ...nodeExecutions[existingNodeIndex],
                  ...nodeStatus,
                };
              } else {
                nodeExecutions.push(nodeStatus);
              }

              return {
                ...prev,
                nodeExecutions,
                nodeStatuses: nodeExecutions,
              };
            });
            break;

          case 'node-error':
            setExecution((prev) => {
              if (!prev) return null;

              const nodeExecutions = [...(prev.nodeExecutions ?? [])];
              const existingNodeIndex = nodeExecutions.findIndex((n) => n.nodeId === message.nodeId);
              const errorMessage = message.data?.error as string || 'Unknown error';
              const nodeStatus: NodeExecutionStatus = {
                nodeId: message.nodeId!,
                nodeName: message.data?.nodeName as string ?? 'Unknown Node',
                state: 'FAILED',
                startedAt: message.timestamp,
                completedAt: message.timestamp,
                error: errorMessage,
              };

              if (existingNodeIndex >= 0) {
                nodeExecutions[existingNodeIndex] = {
                  ...nodeExecutions[existingNodeIndex],
                  ...nodeStatus,
                };
              } else {
                nodeExecutions.push(nodeStatus);
              }

              return {
                ...prev,
                nodeExecutions,
                nodeStatuses: nodeExecutions,
                status: 'FAILED',
                error: `Node ${message.nodeId} failed: ${errorMessage}`,
              };
            });
            break;

          case 'execution-complete':
            setExecution((prev) =>
              prev
                ? {
                    ...prev,
                    status: 'COMPLETED',
                    progress: 100,
                    completedAt: message.timestamp,
                    output: message.data?.output,
                  }
                : null
            );
            break;

          case 'execution-error':
            setExecution((prev) =>
              prev
                ? {
                    ...prev,
                    status: 'FAILED',
                    completedAt: message.timestamp,
                    error: message.data?.error as string || 'Execution failed',
                  }
                : null
            );
            break;

          default:
            console.warn('Unhandled WebSocket message type:', message.type);
        }
      },
      // eslint-disable-next-line react-hooks/exhaustive-deps
      [executionId, setExecution]
    );

    // Connect to WebSocket and register for relevant events
    ws.connect().catch((err) => {
      console.error('WebSocket connect failed', err);
      setError(err instanceof Error ? err : new Error(String(err)));
    });

    const onOpen = () => {
      setIsConnected(true);
      setError(null);
      setReconnectAttempts(0);
      // Subscribe by sending a command if needed
      const subscribeMessage: WebSocketMessage = {
        type: 'execution-start',
        executionId,
        timestamp: new Date().toISOString(),
      };
      try {
        ws.send(subscribeMessage);
      } catch (_e) {
        // ignore
      }
    };

    const onError = (e: unknown) => {
      const err = e instanceof Error ? e : new Error(String(e));
      console.error('WebSocket error:', err);
      setError(err);
      setIsConnected(false);
    };

    const onClose = () => {
      setIsConnected(false);
      // Reconnect with backoff
      if (reconnectAttempts < 5) {
        const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000);
        reconnectTimeoutRef.current = setTimeout(() => {
          setReconnectAttempts((prev) => prev + 1);
          ws.connect().catch(() => {});
        }, delay);
      }
    };

    // Register event handlers for domain events
    ws.on('connection-open', onOpen);
    ws.on('connection-error', onError);
    ws.on('connection-close', onClose);
    ws.on('execution-start', handleWebSocketMessage as any);
    ws.on('execution-update', handleWebSocketMessage as any);
    ws.on('node-start', handleWebSocketMessage as any);
    ws.on('node-complete', handleWebSocketMessage as any);
    ws.on('node-error', handleWebSocketMessage as any);
    ws.on('execution-complete', handleWebSocketMessage as any);
    ws.on('execution-error', handleWebSocketMessage as any);

    // Cleanup
    return () => {
      if (reconnectTimeoutRef.current) clearTimeout(reconnectTimeoutRef.current);
      try {
        ws.off('connection-open', onOpen);
        ws.off('connection-error', onError);
        ws.off('connection-close', onClose);
        ws.off('execution-start', handleWebSocketMessage as any);
        ws.off('execution-update', handleWebSocketMessage as any);
        ws.off('node-start', handleWebSocketMessage as any);
        ws.off('node-complete', handleWebSocketMessage as any);
        ws.off('node-error', handleWebSocketMessage as any);
        ws.off('execution-complete', handleWebSocketMessage as any);
        ws.off('execution-error', handleWebSocketMessage as any);
        ws.disconnect();
      } catch (_e) {
        // ignore errors on cleanup
      }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    };
  }, [executionId, setExecution, setStatus, wsEnabled, resolvedWsUrl]);

  // Reconnect handler
  const reconnect = useCallback(async () => {
    if (!wsRef.current) return;
    try {
      await wsRef.current.connect();
      setIsConnected(true);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err : new Error(String(err)));
      setIsConnected(false);
      throw err;
    }
  }, []);

  // Disconnect handler
  const disconnect = useCallback(() => {
    if (wsRef.current) wsRef.current.disconnect();
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
