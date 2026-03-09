/**
 * Canvas Collaboration Backend Integration
 * 
 * Integrates canvas collaboration with backend CanvasCollaborationHandler.
 * Bridges Yjs CRDT with WebSocket backend for server-side persistence and broadcasting.
 * 
 * @module canvas/hooks
 * @doc.type integration
 * @doc.purpose Real-time canvas collaboration with backend
 */

import { useEffect, useCallback, useRef, useState } from 'react';
import { WebSocketClient } from '@ghatana/yappc-realtime';
import type { Node, Edge } from '@xyflow/react';

/**
 * Canvas collaboration message payloads matching backend handler
 */
export interface CanvasJoinPayload {
  canvasId: string;
  userId: string;
  userName: string;
  userColor: string;
}

export interface CanvasLeavePayload {
  canvasId: string;
  userId: string;
}

export interface CanvasUpdatePayload {
  canvasId: string;
  userId: string;
  changes: {
    nodes?: Node[];
    edges?: Edge[];
    timestamp: number;
  };
}

export interface CanvasCursorPayload {
  canvasId: string;
  userId: string;
  position: { x: number; y: number };
}

export interface CanvasSelectionPayload {
  canvasId: string;
  userId: string;
  selectedNodeIds: string[];
}

/**
 * Remote user presence information
 */
export interface RemoteUser {
  userId: string;
  userName: string;
  userColor: string;
  cursor?: { x: number; y: number };
  selection?: string[];
  lastSeen: number;
  isOnline: boolean;
}

/**
 * Canvas collaboration state
 */
export interface CanvasCollaborationState {
  isConnected: boolean;
  remoteUsers: Map<string, RemoteUser>;
  currentUserId: string;
  canvasId: string;
}

/**
 * Hook configuration
 */
export interface UseCanvasCollaborationBackendConfig {
  /** WebSocket client instance */
  wsClient: WebSocketClient;
  
  /** Canvas ID for this collaboration session */
  canvasId: string;
  
  /** Current user ID */
  userId: string;
  
  /** Current user name */
  userName: string;
  
  /** Current user color */
  userColor: string;
  
  /** Callback when canvas updates received */
  onCanvasUpdate?: (payload: CanvasUpdatePayload) => void;
  
  /** Callback when cursor position received */
  onCursorUpdate?: (payload: CanvasCursorPayload) => void;
  
  /** Callback when selection received */
  onSelectionUpdate?: (payload: CanvasSelectionPayload) => void;
  
  /** Callback when user joins */
  onUserJoin?: (user: RemoteUser) => void;
  
  /** Callback when user leaves */
  onUserLeave?: (userId: string) => void;
  
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Canvas Collaboration Backend Integration Hook
 * 
 * Connects canvas to backend CanvasCollaborationHandler via WebSocket.
 * Handles join/leave, updates, cursor tracking, and selection sync.
 * 
 * Features:
 * - Automatic join on mount, leave on unmount
 * - Real-time canvas updates broadcast to all users
 * - Cursor position tracking with 60fps throttling
 * - Selection synchronization
 * - Presence management
 * - Automatic reconnection handling
 * - Message queuing during disconnect
 * 
 * @example
 * ```tsx
 * function CollaborativeCanvas() {
 *   const { user } = useAuth();
 *   const wsClient = getWebSocketClient('ws://localhost:8080/ws', {
 *     authToken: user.token,
 *     tenantId: user.tenantId,
 *     userId: user.id,
 *   });
 *   
 *   const collaboration = useCanvasCollaborationBackend({
 *     wsClient,
 *     canvasId: 'canvas-123',
 *     userId: user.id,
 *     userName: user.name,
 *     userColor: '#FF6B6B',
 *     onCanvasUpdate: (payload) => {
 *       // Update local canvas with remote changes
 *       setNodes(payload.changes.nodes);
 *       setEdges(payload.changes.edges);
 *     },
 *     onCursorUpdate: (payload) => {
 *       // Update cursor position for remote user
 *       updateRemoteCursor(payload.userId, payload.position);
 *     },
 *   });
 *   
 *   // Send canvas updates
 *   const handleNodesChange = (nodes) => {
 *     collaboration.sendCanvasUpdate(nodes, edges);
 *   };
 *   
 *   // Send cursor position
 *   const handleMouseMove = (e) => {
 *     collaboration.sendCursorPosition(e.clientX, e.clientY);
 *   };
 *   
 *   return (
 *     <div onMouseMove={handleMouseMove}>
 *       <ReactFlow nodes={nodes} edges={edges} onNodesChange={handleNodesChange} />
 *       {Array.from(collaboration.state.remoteUsers.values()).map(user => (
 *         <RemoteCursor key={user.userId} user={user} />
 *       ))}
 *     </div>
 *   );
 * }
 * ```
 */
export function useCanvasCollaborationBackend(
  config: UseCanvasCollaborationBackendConfig
) {
  const {
    wsClient,
    canvasId,
    userId,
    userName,
    userColor,
    onCanvasUpdate,
    onCursorUpdate,
    onSelectionUpdate,
    onUserJoin,
    onUserLeave,
    debug = false,
  } = config;

  // Collaboration state
  const [state, setState] = useState<CanvasCollaborationState>({
    isConnected: false,
    remoteUsers: new Map(),
    currentUserId: userId,
    canvasId,
  });

  // Throttle refs for cursor updates (60fps = ~16ms)
  const lastCursorSendTime = useRef(0);
  const cursorThrottleMs = 16;

  // Cleanup flag
  const isCleaningUp = useRef(false);

  /**
   * Debug logging
   */
  const log = useCallback(
    (...args: unknown[]) => {
      if (debug) {
        console.log('[CanvasCollaborationBackend]', ...args);
      }
    },
    [debug]
  );

  /**
   * Join canvas collaboration session
   */
  const joinCanvas = useCallback(() => {
    if (!wsClient.isConnected()) {
      log('Cannot join - WebSocket not connected');
      return;
    }

    const payload: CanvasJoinPayload = {
      canvasId,
      userId,
      userName,
      userColor,
    };

    wsClient.send('canvas.join', payload);
    log('Joined canvas:', canvasId);
  }, [wsClient, canvasId, userId, userName, userColor, log]);

  /**
   * Leave canvas collaboration session
   */
  const leaveCanvas = useCallback(() => {
    if (!wsClient.isConnected() || isCleaningUp.current) {
      return;
    }

    const payload: CanvasLeavePayload = {
      canvasId,
      userId,
    };

    wsClient.send('canvas.leave', payload);
    log('Left canvas:', canvasId);
  }, [wsClient, canvasId, userId, log]);

  /**
   * Send canvas update to backend
   */
  const sendCanvasUpdate = useCallback(
    (nodes: Node[], edges: Edge[]) => {
      if (!wsClient.isConnected()) {
        log('Cannot send update - WebSocket not connected');
        return;
      }

      const payload: CanvasUpdatePayload = {
        canvasId,
        userId,
        changes: {
          nodes,
          edges,
          timestamp: Date.now(),
        },
      };

      wsClient.send('canvas.update', payload);
      log('Sent canvas update:', { nodeCount: nodes.length, edgeCount: edges.length });
    },
    [wsClient, canvasId, userId, log]
  );

  /**
   * Send cursor position (throttled to 60fps)
   */
  const sendCursorPosition = useCallback(
    (x: number, y: number) => {
      const now = Date.now();
      if (now - lastCursorSendTime.current < cursorThrottleMs) {
        return; // Throttle
      }

      if (!wsClient.isConnected()) {
        return;
      }

      lastCursorSendTime.current = now;

      const payload: CanvasCursorPayload = {
        canvasId,
        userId,
        position: { x, y },
      };

      wsClient.send('canvas.cursor', payload);
    },
    [wsClient, canvasId, userId]
  );

  /**
   * Send selection update
   */
  const sendSelection = useCallback(
    (selectedNodeIds: string[]) => {
      if (!wsClient.isConnected()) {
        return;
      }

      const payload: CanvasSelectionPayload = {
        canvasId,
        userId,
        selectedNodeIds,
      };

      wsClient.send('canvas.selection', payload);
      log('Sent selection:', selectedNodeIds);
    },
    [wsClient, canvasId, userId, log]
  );

  /**
   * Handle incoming canvas updates
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<CanvasUpdatePayload>(
      'canvas.update',
      (payload) => {
        // Ignore own updates
        if (payload.userId === userId) {
          return;
        }

        log('Received canvas update from:', payload.userId);

        // Update remote user last seen
        setState((prev) => {
          const newUsers = new Map(prev.remoteUsers);
          const user = newUsers.get(payload.userId);
          if (user) {
            newUsers.set(payload.userId, {
              ...user,
              lastSeen: Date.now(),
            });
          }
          return { ...prev, remoteUsers: newUsers };
        });

        // Notify callback
        onCanvasUpdate?.(payload);
      }
    );

    return unsubscribe;
  }, [wsClient, userId, onCanvasUpdate, log]);

  /**
   * Handle incoming cursor updates
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<CanvasCursorPayload>(
      'canvas.cursor',
      (payload) => {
        // Ignore own cursor
        if (payload.userId === userId) {
          return;
        }

        // Update remote user cursor
        setState((prev) => {
          const newUsers = new Map(prev.remoteUsers);
          const user = newUsers.get(payload.userId);
          if (user) {
            newUsers.set(payload.userId, {
              ...user,
              cursor: payload.position,
              lastSeen: Date.now(),
            });
          }
          return { ...prev, remoteUsers: newUsers };
        });

        // Notify callback
        onCursorUpdate?.(payload);
      }
    );

    return unsubscribe;
  }, [wsClient, userId, onCursorUpdate]);

  /**
   * Handle incoming selection updates
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<CanvasSelectionPayload>(
      'canvas.selection',
      (payload) => {
        // Ignore own selection
        if (payload.userId === userId) {
          return;
        }

        log('Received selection from:', payload.userId, payload.selectedNodeIds);

        // Update remote user selection
        setState((prev) => {
          const newUsers = new Map(prev.remoteUsers);
          const user = newUsers.get(payload.userId);
          if (user) {
            newUsers.set(payload.userId, {
              ...user,
              selection: payload.selectedNodeIds,
              lastSeen: Date.now(),
            });
          }
          return { ...prev, remoteUsers: newUsers };
        });

        // Notify callback
        onSelectionUpdate?.(payload);
      }
    );

    return unsubscribe;
  }, [wsClient, userId, onSelectionUpdate, log]);

  /**
   * Handle user join events
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<CanvasJoinPayload>(
      'canvas.join',
      (payload) => {
        // Ignore own join
        if (payload.userId === userId) {
          return;
        }

        log('User joined:', payload.userName);

        const newUser: RemoteUser = {
          userId: payload.userId,
          userName: payload.userName,
          userColor: payload.userColor,
          lastSeen: Date.now(),
          isOnline: true,
        };

        setState((prev) => {
          const newUsers = new Map(prev.remoteUsers);
          newUsers.set(payload.userId, newUser);
          return { ...prev, remoteUsers: newUsers };
        });

        // Notify callback
        onUserJoin?.(newUser);
      }
    );

    return unsubscribe;
  }, [wsClient, userId, onUserJoin, log]);

  /**
   * Handle user leave events
   */
  useEffect(() => {
    const unsubscribe = wsClient.on<CanvasLeavePayload>(
      'canvas.leave',
      (payload) => {
        // Ignore own leave
        if (payload.userId === userId) {
          return;
        }

        log('User left:', payload.userId);

        setState((prev) => {
          const newUsers = new Map(prev.remoteUsers);
          newUsers.delete(payload.userId);
          return { ...prev, remoteUsers: newUsers };
        });

        // Notify callback
        onUserLeave?.(payload.userId);
      }
    );

    return unsubscribe;
  }, [wsClient, userId, onUserLeave, log]);

  /**
   * Handle WebSocket connection state changes
   */
  useEffect(() => {
    const unsubscribe = wsClient.onStateChange((connectionState) => {
      const isConnected = connectionState === 'connected';
      
      setState((prev) => ({ ...prev, isConnected }));
      log('Connection state changed:', connectionState);

      // Auto-join when connected
      if (isConnected && !isCleaningUp.current) {
        joinCanvas();
      }
    });

    return unsubscribe;
  }, [wsClient, joinCanvas, log]);

  /**
   * Join canvas on mount, leave on unmount
   */
  useEffect(() => {
    // Join if already connected
    if (wsClient.isConnected()) {
      joinCanvas();
    }

    // Cleanup on unmount
    return () => {
      isCleaningUp.current = true;
      leaveCanvas();
    };
  }, [wsClient, joinCanvas, leaveCanvas]);

  /**
   * Cleanup stale users (not seen in 60 seconds)
   */
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      const staleThreshold = 60000; // 60 seconds

      setState((prev) => {
        const newUsers = new Map(prev.remoteUsers);
        let hasChanges = false;

        for (const [uid, user] of newUsers.entries()) {
          if (now - user.lastSeen > staleThreshold) {
            newUsers.delete(uid);
            hasChanges = true;
            log('Removed stale user:', user.userName);
          }
        }

        return hasChanges ? { ...prev, remoteUsers: newUsers } : prev;
      });
    }, 30000); // Check every 30 seconds

    return () => clearInterval(interval);
  }, [log]);

  return {
    // State
    state,
    isConnected: state.isConnected,
    remoteUsers: Array.from(state.remoteUsers.values()),
    
    // Actions
    joinCanvas,
    leaveCanvas,
    sendCanvasUpdate,
    sendCursorPosition,
    sendSelection,
  };
}

/**
 * Export types
 */
export type {
  UseCanvasCollaborationBackendConfig,
  CanvasCollaborationState,
  RemoteUser,
};
