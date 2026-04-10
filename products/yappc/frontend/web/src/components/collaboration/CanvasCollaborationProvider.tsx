import type { Edge, Node } from '@xyflow/react';
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';

import { getWebSocketClient } from '@ghatana/realtime';

import {
  type RemoteUser,
  useCanvasCollaborationBackend,
} from './useCanvasCollaborationBackend';

interface CanvasCollaborationContextValue {
  currentUser: {
    userId: string;
    userName: string;
    userColor: string;
  };
  isConnected: boolean;
  remoteUsers: RemoteUser[];
  syncStatus: 'synced' | 'offline';
  updateCanvas: (nodes: Node[], edges: Edge[]) => void;
  updateCursor: (x: number, y: number) => void;
  updateSelection: (nodeIds: string[]) => void;
}

const CanvasCollaborationContext =
  createContext<CanvasCollaborationContextValue | null>(null);

export interface CanvasCollaborationProviderProps {
  canvasId: string;
  userId: string;
  userName: string;
  wsEndpoint: string;
  authToken?: string;
  tenantId?: string;
  children: React.ReactNode;
  debug?: boolean;
}

export const CanvasCollaborationProvider: React.FC<
  CanvasCollaborationProviderProps
> = ({
  canvasId,
  userId,
  userName,
  wsEndpoint,
  authToken,
  tenantId,
  children,
  debug = false,
}) => {
  const [userColor] = useState(() => generateUserColor(userId));
  const resolvedEndpoint = useMemo(() => {
    const baseUrl = typeof window !== 'undefined' ? window.location.origin : 'http://localhost';
    const url = new URL(wsEndpoint, baseUrl);

    if (authToken) {
      url.searchParams.set('authToken', authToken);
    }
    if (tenantId) {
      url.searchParams.set('tenantId', tenantId);
    }
    url.searchParams.set('userId', userId);

    return url.toString();
  }, [authToken, tenantId, userId, wsEndpoint]);
  const [wsClient] = useState(() =>
    getWebSocketClient({
      url: resolvedEndpoint,
      logger: debug ? console : undefined,
    })
  );

  useEffect(() => {
    wsClient.connect().catch((error: unknown) => {
      console.error('Failed to connect WebSocket:', error);
    });

    return () => {
      wsClient.disconnect();
    };
  }, [wsClient]);

  const backend = useCanvasCollaborationBackend({
    wsClient,
    canvasId,
    userId,
    userName,
    userColor,
    debug,
  });

  const value = useMemo<CanvasCollaborationContextValue>(
    () => ({
      currentUser: {
        userId,
        userName,
        userColor,
      },
      isConnected: backend.isConnected,
      remoteUsers: backend.remoteUsers,
      syncStatus: backend.isConnected ? 'synced' : 'offline',
      updateCanvas: backend.sendCanvasUpdate,
      updateCursor: backend.sendCursorPosition,
      updateSelection: backend.sendSelection,
    }),
    [backend, userColor, userId, userName]
  );

  return (
    <CanvasCollaborationContext.Provider value={value}>
      {children}
    </CanvasCollaborationContext.Provider>
  );
};

export function useCanvasCollaboration(): CanvasCollaborationContextValue {
  const context = useContext(CanvasCollaborationContext);
  if (!context) {
    throw new Error(
      'useCanvasCollaboration must be used within CanvasCollaborationProvider'
    );
  }

  return context;
}

function generateUserColor(userId: string): string {
  const colors = [
    '#FF6B6B',
    '#4ECDC4',
    '#45B7D1',
    '#96CEB4',
    '#FFEAA7',
    '#DDA0DD',
    '#98D8C8',
    '#F7DC6F',
    '#BB8FCE',
    '#85C1E9',
  ];

  let hash = 0;
  for (let index = 0; index < userId.length; index += 1) {
    hash = userId.charCodeAt(index) + ((hash << 5) - hash);
  }

  return colors[Math.abs(hash) % colors.length];
}