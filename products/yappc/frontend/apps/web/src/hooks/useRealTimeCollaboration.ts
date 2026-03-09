/**
 * useRealTimeCollaboration - Real-time Collaboration Hook
 *
 * Manages WebSocket connections, presence awareness, and cursor tracking
 *
 * @doc.type hook
 * @doc.purpose Real-time collaboration features
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { useAtom } from 'jotai';
import { collaboratorsAtom } from '../state/atoms/unifiedCanvasAtom';

export interface Collaborator {
  id: string;
  name: string;
  avatar?: string;
  color: string;
  cursor?: { x: number; y: number };
  selectedNodeIds?: string[];
  isTyping?: boolean;
  lastActive: Date;
}

export interface CollaborationOptions {
  projectId: string;
  userId: string;
  userName: string;
  wsUrl?: string;
}

export interface UseRealTimeCollaborationReturn {
  collaborators: Collaborator[];
  isConnected: boolean;
  sendCursorUpdate: (x: number, y: number) => void;
  sendSelection: (nodeIds: string[]) => void;
  sendNodeUpdate: (nodeId: string, updates: unknown) => void;
}

export function useRealTimeCollaboration(
  options: CollaborationOptions
): UseRealTimeCollaborationReturn {
  const {
    projectId,
    userId,
    userName,
    // WebSocket goes through Gateway, not directly to Java backend
    wsUrl = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:7002',
  } = options;

  const [collaborators, setCollaborators] = useAtom(collaboratorsAtom);
  const [isConnected, setIsConnected] = useState(false);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const heartbeatIntervalRef = useRef<NodeJS.Timeout | null>(null);

  /**
   * Connect to WebSocket server
   */
  const connect = useCallback(() => {
    try {
      const ws = new WebSocket(`${wsUrl}/canvas/${projectId}`);
      wsRef.current = ws;

      ws.onopen = () => {
        console.log('[Collaboration] WebSocket connected');
        setIsConnected(true);

        // Send join message with email
        ws.send(
          JSON.stringify({
            type: 'join',
            userId,
            userName,
            userEmail: `${userId}@ghatana.local`, // Use actual email from auth context in production
          })
        );

        // Start heartbeat
        heartbeatIntervalRef.current = setInterval(() => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'ping' }));
          }
        }, 30000); // 30 seconds
      };

      ws.onmessage = (event) => {
        try {
          const message = JSON.parse(event.data);
          handleMessage(message);
        } catch (error) {
          console.error('[Collaboration] Failed to parse message:', error);
        }
      };

      ws.onerror = (error) => {
        console.error('[Collaboration] WebSocket error:', error);
      };

      ws.onclose = () => {
        console.log('[Collaboration] WebSocket closed');
        setIsConnected(false);

        // Clear heartbeat
        if (heartbeatIntervalRef.current) {
          clearInterval(heartbeatIntervalRef.current);
        }

        // Attempt reconnect after 5 seconds
        reconnectTimeoutRef.current = setTimeout(() => {
          console.log('[Collaboration] Attempting to reconnect...');
          connect();
        }, 5000);
      };
    } catch (error) {
      console.error('[Collaboration] Failed to connect:', error);
    }
  }, [projectId, userId, userName, wsUrl]);

  /**
   * Handle incoming WebSocket messages
   */
  const handleMessage = useCallback(
    (message: unknown) => {
      switch (message.type) {
        case 'user-joined':
          console.log('[Collaboration] User joined:', message.user);
          setCollaborators((prev) => {
            const existing = prev.find((c) => c.id === message.user.id);
            if (existing) return prev;
            return [
              ...prev,
              {
                ...message.user,
                lastActive: new Date(),
              },
            ];
          });
          break;

        case 'user-left':
          console.log('[Collaboration] User left:', message.userId);
          setCollaborators((prev) =>
            prev.filter((c) => c.id !== message.userId)
          );
          break;

        case 'cursor-update':
          setCollaborators((prev) =>
            prev.map((c) =>
              c.id === message.userId
                ? { ...c, cursor: message.cursor, lastActive: new Date() }
                : c
            )
          );
          break;

        case 'selection-update':
          setCollaborators((prev) =>
            prev.map((c) =>
              c.id === message.userId
                ? {
                    ...c,
                    selectedNodeIds: message.nodeIds,
                    lastActive: new Date(),
                  }
                : c
            )
          );
          break;

        case 'node-update':
          // Handle node updates from other users
          console.log('[Collaboration] Node updated by:', message.userId);
          // NOTE: Update canvas state
          break;

        case 'users-list':
          console.log('[Collaboration] Received users list:', message.users);
          setCollaborators(
            message.users.map((user: unknown) => ({
              ...user,
              lastActive: new Date(),
            }))
          );
          break;

        case 'pong':
          // Heartbeat response
          break;

        default:
          console.warn('[Collaboration] Unknown message type:', message.type);
      }
    },
    [setCollaborators]
  );

  /**
   * Send cursor position update
   */
  const sendCursorUpdate = useCallback(
    (x: number, y: number) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({
            type: 'cursor-update',
            userId,
            cursor: { x, y },
            timestamp: new Date().toISOString(),
          })
        );
      }
    },
    [userId]
  );

  /**
   * Send selection update
   */
  const sendSelection = useCallback(
    (nodeIds: string[]) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({
            type: 'selection-update',
            userId,
            nodeIds,
            timestamp: new Date().toISOString(),
          })
        );
      }
    },
    [userId]
  );

  /**
   * Send node update
   */
  const sendNodeUpdate = useCallback(
    (nodeId: string, updates: unknown) => {
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({
            type: 'node-update',
            userId,
            nodeId,
            updates,
            timestamp: new Date().toISOString(),
          })
        );
      }
    },
    [userId]
  );

  /**
   * Effect: Connect on mount, disconnect on unmount
   */
  useEffect(() => {
    connect();

    return () => {
      // Send leave message
      if (wsRef.current?.readyState === WebSocket.OPEN) {
        wsRef.current.send(
          JSON.stringify({
            type: 'leave',
            userId,
            timestamp: new Date().toISOString(),
          })
        );
      }

      // Close WebSocket
      if (wsRef.current) {
        wsRef.current.close();
      }

      // Clear timers
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (heartbeatIntervalRef.current) {
        clearInterval(heartbeatIntervalRef.current);
      }
    };
  }, [connect, userId]);

  /**
   * Effect: Clean up stale collaborators
   */
  useEffect(() => {
    const cleanupInterval = setInterval(() => {
      const now = new Date();
      setCollaborators((prev) =>
        prev.filter((c) => {
          const timeSinceActive = now.getTime() - c.lastActive.getTime();
          return timeSinceActive < 60000; // 1 minute timeout
        })
      );
    }, 10000); // Check every 10 seconds

    return () => clearInterval(cleanupInterval);
  }, [setCollaborators]);

  return {
    collaborators,
    isConnected,
    sendCursorUpdate,
    sendSelection,
    sendNodeUpdate,
  };
}
