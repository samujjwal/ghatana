/**
 * Collaborative Canvas Wrapper
 * 
 * Wraps ProjectCanvas with real-time collaboration features.
 * Integrates CanvasCollaborationProvider, CollaborationBar, and RemoteCursor.
 * 
 * @module components
 */

import React, { useCallback, useState } from 'react';
import { ReactFlowProvider } from '@xyflow/react';
import { 
  CanvasCollaborationProvider, 
  useCanvasCollaboration,
  CollaborationBar,
  RemoteCursor,
} from '@ghatana/yappc-canvas';
import { useAuth } from '../hooks/useAuth';
import { LifecyclePhase } from '../types/lifecycle';
import { FOWStage } from '../types/fow-stages';
import { SimplifiedCanvasWorkspace } from './canvas/SimplifiedCanvasWorkspace';

/**
 * Collaborative Canvas Props
 */
export interface CollaborativeCanvasProps {
  /** Project ID */
  projectId?: string;
  
  /** Canvas ID (defaults to projectId) */
  canvasId?: string;
  
  /** Read-only mode */
  readOnly?: boolean;
  
  /** Node selection callback */
  onNodeSelect?: (nodeId: string) => void;
  
  /** Architecture change callback */
  onArchitectureChange?: () => void;
  
  /** WebSocket endpoint */
  wsEndpoint?: string;
  
  /** Enable debug logging */
  debug?: boolean;

  /** Current lifecycle phase for the canvas workspace */
  currentPhase?: LifecyclePhase;

  /** Current FOW stage for gate status queries */
  fowStage?: FOWStage;
}

/**
 * Inner Canvas Component (has access to collaboration context)
 */
const CollaborativeCanvasInner: React.FC<Omit<CollaborativeCanvasProps, 'canvasId' | 'wsEndpoint' | 'debug'>> = ({
  projectId,
  readOnly,
  onNodeSelect,
  onArchitectureChange,
  currentPhase = LifecyclePhase.SHAPE,
  fowStage = FOWStage.FOUNDATION,
}) => {
  const {
    backend,
    yjs,
    isConnected,
    remoteUsers,
    updateCursor,
    updateSelection,
  } = useCanvasCollaboration();

  const [, setSelectedNodes] = useState<string[]>([]);

  // Handle mouse move for cursor tracking
  const handleMouseMove = useCallback((event: React.MouseEvent) => {
    if (isConnected) {
      updateCursor(event.clientX, event.clientY);
    }
  }, [isConnected, updateCursor]);

  // Handle node selection
  const handleNodeSelect = useCallback((nodeId: string) => {
    const newSelection = [nodeId];
    setSelectedNodes(newSelection);
    updateSelection(newSelection);
    onNodeSelect?.(nodeId);
  }, [updateSelection, onNodeSelect]);

  // Handle canvas changes
  const handleArchitectureChange = useCallback(() => {
    // Canvas changes are automatically synced via Yjs
    // Just notify parent component
    onArchitectureChange?.();
  }, [onArchitectureChange]);

  return (
    <div 
      className="relative w-full h-full"
      onMouseMove={handleMouseMove}
    >
      {/* Collaboration Bar */}
      <div className="absolute top-4 right-4 z-10">
        <CollaborationBar
          currentUser={{
            userId: backend.state.currentUserId,
            userName: yjs.currentUser.name,
            userColor: yjs.currentUser.color,
          }}
          remoteUsers={remoteUsers}
          isConnected={isConnected}
          syncStatus={yjs.syncStatus}
        />
      </div>

      {/* Canvas Workspace */}
      <SimplifiedCanvasWorkspace
        projectId={projectId || 'default'}
        currentPhase={currentPhase}
        fowStage={fowStage}
      />

      {/* Remote Cursors */}
      {remoteUsers.map((user) => (
        user.cursor && (
          <RemoteCursor
            key={user.userId}
            user={user}
            showLabel={true}
          />
        )
      ))}
    </div>
  );
};

/**
 * Collaborative Canvas Component
 * 
 * Provides real-time collaboration for canvas editing.
 * Automatically handles WebSocket connection, presence, and sync.
 * 
 * @example
 * ```tsx
 * <CollaborativeCanvas
 *   projectId="project-123"
 *   onNodeSelect={(nodeId) => console.log('Selected:', nodeId)}
 *   onArchitectureChange={() => console.log('Canvas changed')}
 * />
 * ```
 */
export const CollaborativeCanvas: React.FC<CollaborativeCanvasProps> = ({
  projectId,
  canvasId,
  readOnly = false,
  onNodeSelect,
  onArchitectureChange,
  wsEndpoint = process.env.REACT_APP_WS_ENDPOINT || 'ws://localhost:8080/ws',
  debug = false,
  currentPhase = LifecyclePhase.SHAPE,
  fowStage = FOWStage.FOUNDATION,
}) => {
  const { isAuthenticated, currentUser, getToken } = useAuth();
  
  // Use projectId as canvasId if not provided
  const effectiveCanvasId = canvasId || projectId || 'default-canvas';

  // Don't render if not authenticated
  if (!isAuthenticated || !currentUser) {
    return (
      <div className="w-full h-full flex items-center justify-center bg-zinc-950">
        <div className="text-zinc-400">
          Please log in to access collaborative canvas
        </div>
      </div>
    );
  }

  return (
    <ReactFlowProvider>
      <CanvasCollaborationProvider
        canvasId={effectiveCanvasId}
        userId={currentUser.id}
        userName={currentUser.username || currentUser.email}
        wsEndpoint={wsEndpoint}
        authToken={getToken() || ''}
        tenantId="default"
        debug={debug}
      >
        <CollaborativeCanvasInner
          projectId={projectId}
          readOnly={readOnly}
          onNodeSelect={onNodeSelect}
          onArchitectureChange={onArchitectureChange}
          currentPhase={currentPhase}
          fowStage={fowStage}
        />
      </CanvasCollaborationProvider>
    </ReactFlowProvider>
  );
};

export default CollaborativeCanvas;
