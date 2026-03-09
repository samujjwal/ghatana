/**
 * Canvas Collaboration Provider
 * 
 * Complete integration example combining Yjs CRDT with WebSocket backend.
 * Provides both local CRDT sync and server-side persistence/broadcasting.
 * 
 * @module canvas/integration
 */

import React, { createContext, useContext, useEffect, useState } from 'react';
import { useReactFlow } from '@xyflow/react';
import { getWebSocketClient } from '@ghatana/yappc-realtime';
import { useCanvasCollaborationBackend } from '../hooks/useCanvasCollaborationBackend';
import { useCollaboration } from '../hooks/useCollaboration';
import type { Node, Edge } from '@xyflow/react';

/**
 * Collaboration context value
 */
interface CanvasCollaborationContextValue {
  // Backend integration
  backend: ReturnType<typeof useCanvasCollaborationBackend>;
  
  // Yjs CRDT integration
  yjs: ReturnType<typeof useCollaboration>;
  
  // Combined state
  isConnected: boolean;
  remoteUsers: unknown[];
  
  // Actions
  updateCanvas: (nodes: Node[], edges: Edge[]) => void;
  updateCursor: (x: number, y: number) => void;
  updateSelection: (nodeIds: string[]) => void;
}

const CanvasCollaborationContext = createContext<CanvasCollaborationContextValue | null>(null);

/**
 * Canvas Collaboration Provider Props
 */
export interface CanvasCollaborationProviderProps {
  /** Canvas/room ID */
  canvasId: string;
  
  /** Current user ID */
  userId: string;
  
  /** Current user name */
  userName: string;
  
  /** WebSocket endpoint */
  wsEndpoint: string;
  
  /** Auth token */
  authToken?: string;
  
  /** Tenant ID */
  tenantId?: string;
  
  /** Children */
  children: React.ReactNode;
  
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Canvas Collaboration Provider
 * 
 * Provides complete canvas collaboration with both Yjs CRDT and backend integration.
 * Combines local conflict-free editing with server-side persistence and broadcasting.
 * 
 * Architecture:
 * - Yjs CRDT: Local conflict-free collaborative editing
 * - WebSocket Backend: Server-side persistence, broadcasting, presence
 * - Hybrid approach: Best of both worlds
 * 
 * @example
 * ```tsx
 * <CanvasCollaborationProvider
 *   canvasId="canvas-123"
 *   userId={user.id}
 *   userName={user.name}
 *   wsEndpoint="ws://localhost:8080/ws"
 *   authToken={user.token}
 *   tenantId={user.tenantId}
 * >
 *   <ReactFlowProvider>
 *     <CollaborativeCanvas />
 *   </ReactFlowProvider>
 * </CanvasCollaborationProvider>
 * ```
 */
export const CanvasCollaborationProvider: React.FC<CanvasCollaborationProviderProps> = ({
  canvasId,
  userId,
  userName,
  wsEndpoint,
  authToken,
  tenantId,
  children,
  debug = false,
}) => {
  // Generate user color
  const [userColor] = useState(() => generateUserColor(userId));

  // Initialize WebSocket client
  const [wsClient] = useState(() =>
    getWebSocketClient(wsEndpoint, {
      authToken,
      tenantId,
      userId,
      debug,
    })
  );

  // Connect WebSocket on mount
  useEffect(() => {
    wsClient.connect().catch((err) => {
      console.error('Failed to connect WebSocket:', err);
    });

    return () => {
      wsClient.disconnect();
    };
  }, [wsClient]);

  // Yjs CRDT collaboration
  const yjs = useCollaboration(canvasId, userId, userName);

  // Backend WebSocket collaboration
  const backend = useCanvasCollaborationBackend({
    wsClient,
    canvasId,
    userId,
    userName,
    userColor,
    debug,
    
    // Handle backend updates
    onCanvasUpdate: (payload) => {
      if (debug) {
        console.log('[CanvasCollaboration] Backend update received:', payload);
      }
      // Yjs will handle the actual node/edge updates via CRDT
    },
    
    onCursorUpdate: (payload) => {
      if (debug) {
        console.log('[CanvasCollaboration] Cursor update:', payload);
      }
    },
    
    onSelectionUpdate: (payload) => {
      if (debug) {
        console.log('[CanvasCollaboration] Selection update:', payload);
      }
    },
    
    onUserJoin: (user) => {
      if (debug) {
        console.log('[CanvasCollaboration] User joined:', user.userName);
      }
    },
    
    onUserLeave: (userId) => {
      if (debug) {
        console.log('[CanvasCollaboration] User left:', userId);
      }
    },
  });

  // Combined update functions
  const updateCanvas = (nodes: Node[], edges: Edge[]) => {
    // Update Yjs CRDT (local sync)
    yjs.syncLocalToYjs(nodes, edges);
    
    // Send to backend (server persistence + broadcast)
    backend.sendCanvasUpdate(nodes, edges);
  };

  const updateCursor = (x: number, y: number) => {
    // Update Yjs awareness
    yjs.updateCursor(x, y);
    
    // Send to backend
    backend.sendCursorPosition(x, y);
  };

  const updateSelection = (nodeIds: string[]) => {
    // Update Yjs awareness
    yjs.updateSelection(nodeIds);
    
    // Send to backend
    backend.sendSelection(nodeIds);
  };

  // Combined state
  const isConnected = backend.isConnected && yjs.isConnected;
  const remoteUsers = [
    ...backend.remoteUsers,
    ...yjs.remoteUsers,
  ];

  const value: CanvasCollaborationContextValue = {
    backend,
    yjs,
    isConnected,
    remoteUsers,
    updateCanvas,
    updateCursor,
    updateSelection,
  };

  return (
    <CanvasCollaborationContext.Provider value={value}>
      {children}
    </CanvasCollaborationContext.Provider>
  );
};

/**
 * Hook to use canvas collaboration context
 */
export function useCanvasCollaboration() {
  const context = useContext(CanvasCollaborationContext);
  if (!context) {
    throw new Error(
      'useCanvasCollaboration must be used within CanvasCollaborationProvider'
    );
  }
  return context;
}

/**
 * Generate consistent user color
 */
function generateUserColor(userId: string): string {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E9',
  ];

  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash);
  }

  return colors[Math.abs(hash) % colors.length];
}

export default CanvasCollaborationProvider;
