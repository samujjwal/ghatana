import type { Edge, Node } from '@xyflow/react';
import { useCallback, useEffect, useRef, useState } from 'react';

import type {
  WebSocketClient,
  WebSocketConnectionState,
  WebSocketMessage,
} from '@ghatana/realtime';

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

export interface RemoteUser {
  userId: string;
  userName: string;
  userColor: string;
  cursor?: { x: number; y: number };
  selection?: string[];
  lastSeen: number;
  isOnline: boolean;
}

export interface CanvasCollaborationState {
  isConnected: boolean;
  remoteUsers: Map<string, RemoteUser>;
  currentUserId: string;
  canvasId: string;
}

export interface UseCanvasCollaborationBackendConfig {
  wsClient: WebSocketClient;
  canvasId: string;
  userId: string;
  userName: string;
  userColor: string;
  onCanvasUpdate?: (payload: CanvasUpdatePayload) => void;
  onCursorUpdate?: (payload: CanvasCursorPayload) => void;
  onSelectionUpdate?: (payload: CanvasSelectionPayload) => void;
  onUserJoin?: (user: RemoteUser) => void;
  onUserLeave?: (userId: string) => void;
  debug?: boolean;
}

export interface UseCanvasCollaborationBackendReturn {
  state: CanvasCollaborationState;
  isConnected: boolean;
  remoteUsers: RemoteUser[];
  joinCanvas: () => void;
  leaveCanvas: () => void;
  sendCanvasUpdate: (nodes: Node[], edges: Edge[]) => void;
  sendCursorPosition: (x: number, y: number) => void;
  sendSelection: (nodeIds: string[]) => void;
}

export function useCanvasCollaborationBackend(
  config: UseCanvasCollaborationBackendConfig
): UseCanvasCollaborationBackendReturn {
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

  const [state, setState] = useState<CanvasCollaborationState>({
    isConnected: false,
    remoteUsers: new Map(),
    currentUserId: userId,
    canvasId,
  });
  const lastCursorSendTime = useRef(0);
  const isCleaningUp = useRef(false);

  const log = useCallback(
    (...args: unknown[]) => {
      if (debug) {
        console.log('[CanvasCollaborationBackend]', ...args);
      }
    },
    [debug]
  );

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

    wsClient.send({ type: 'canvas.join', payload });
  }, [canvasId, log, userColor, userId, userName, wsClient]);

  const leaveCanvas = useCallback(() => {
    if (!wsClient.isConnected() || isCleaningUp.current) {
      return;
    }

    const payload: CanvasLeavePayload = {
      canvasId,
      userId,
    };

    wsClient.send({ type: 'canvas.leave', payload });
  }, [canvasId, userId, wsClient]);

  const sendCanvasUpdate = useCallback(
    (nodes: Node[], edges: Edge[]) => {
      if (!wsClient.isConnected()) {
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

      wsClient.send({ type: 'canvas.update', payload });
    },
    [canvasId, userId, wsClient]
  );

  const sendCursorPosition = useCallback(
    (x: number, y: number) => {
      const now = Date.now();
      if (now - lastCursorSendTime.current < 16) {
        return;
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

      wsClient.send({ type: 'canvas.cursor', payload });
    },
    [canvasId, userId, wsClient]
  );

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

      wsClient.send({ type: 'canvas.selection', payload });
    },
    [canvasId, userId, wsClient]
  );

  useEffect(() => {
    const unsubscribe = wsClient.subscribe<CanvasUpdatePayload>('canvas.update', (message: WebSocketMessage<CanvasUpdatePayload>) => {
      const payload = message.payload;
      if (payload.userId === userId) {
        return;
      }

      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        const user = remoteUsers.get(payload.userId);
        if (user) {
          remoteUsers.set(payload.userId, {
            ...user,
            lastSeen: Date.now(),
          });
        }
        return { ...previous, remoteUsers };
      });

      onCanvasUpdate?.(payload);
    });

    return unsubscribe;
  }, [onCanvasUpdate, userId, wsClient]);

  useEffect(() => {
    const unsubscribe = wsClient.subscribe<CanvasCursorPayload>('canvas.cursor', (message: WebSocketMessage<CanvasCursorPayload>) => {
      const payload = message.payload;
      if (payload.userId === userId) {
        return;
      }

      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        const user = remoteUsers.get(payload.userId);
        if (user) {
          remoteUsers.set(payload.userId, {
            ...user,
            cursor: payload.position,
            lastSeen: Date.now(),
          });
        }
        return { ...previous, remoteUsers };
      });

      onCursorUpdate?.(payload);
    });

    return unsubscribe;
  }, [onCursorUpdate, userId, wsClient]);

  useEffect(() => {
    const unsubscribe = wsClient.subscribe<CanvasSelectionPayload>('canvas.selection', (message: WebSocketMessage<CanvasSelectionPayload>) => {
      const payload = message.payload;
      if (payload.userId === userId) {
        return;
      }

      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        const user = remoteUsers.get(payload.userId);
        if (user) {
          remoteUsers.set(payload.userId, {
            ...user,
            selection: payload.selectedNodeIds,
            lastSeen: Date.now(),
          });
        }
        return { ...previous, remoteUsers };
      });

      onSelectionUpdate?.(payload);
    });

    return unsubscribe;
  }, [onSelectionUpdate, userId, wsClient]);

  useEffect(() => {
    const unsubscribe = wsClient.subscribe<CanvasJoinPayload>('canvas.join', (message: WebSocketMessage<CanvasJoinPayload>) => {
      const payload = message.payload;
      if (payload.userId === userId) {
        return;
      }

      const newUser: RemoteUser = {
        userId: payload.userId,
        userName: payload.userName,
        userColor: payload.userColor,
        lastSeen: Date.now(),
        isOnline: true,
      };

      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        remoteUsers.set(payload.userId, newUser);
        return { ...previous, remoteUsers };
      });

      onUserJoin?.(newUser);
    });

    return unsubscribe;
  }, [onUserJoin, userId, wsClient]);

  useEffect(() => {
    const unsubscribe = wsClient.subscribe<CanvasLeavePayload>('canvas.leave', (message: WebSocketMessage<CanvasLeavePayload>) => {
      const payload = message.payload;
      if (payload.userId === userId) {
        return;
      }

      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        remoteUsers.delete(payload.userId);
        return { ...previous, remoteUsers };
      });

      onUserLeave?.(payload.userId);
    });

    return unsubscribe;
  }, [onUserLeave, userId, wsClient]);

  useEffect(() => {
    const unsubscribe = wsClient.onStateChange((connectionState: WebSocketConnectionState) => {
      const isConnected = connectionState.status === 'connected';
      setState((previous) => ({ ...previous, isConnected }));
      if (isConnected && !isCleaningUp.current) {
        joinCanvas();
      }
    });

    return unsubscribe;
  }, [joinCanvas, wsClient]);

  useEffect(() => {
    if (wsClient.isConnected()) {
      joinCanvas();
    }

    return () => {
      isCleaningUp.current = true;
      leaveCanvas();
    };
  }, [joinCanvas, leaveCanvas, wsClient]);

  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      setState((previous) => {
        const remoteUsers = new Map(previous.remoteUsers);
        let hasChanges = false;

        for (const [remoteUserId, remoteUser] of remoteUsers.entries()) {
          if (now - remoteUser.lastSeen > 60000) {
            remoteUsers.delete(remoteUserId);
            hasChanges = true;
            log('Removed stale user:', remoteUser.userName);
          }
        }

        return hasChanges ? { ...previous, remoteUsers } : previous;
      });
    }, 30000);

    return () => clearInterval(interval);
  }, [log]);

  return {
    state,
    isConnected: state.isConnected,
    remoteUsers: Array.from(state.remoteUsers.values()),
    joinCanvas,
    leaveCanvas,
    sendCanvasUpdate,
    sendCursorPosition,
    sendSelection,
  };
}