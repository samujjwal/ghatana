/**
 * @ghatana/yappc-ide - IDE Collaboration Hook
 * 
 * React hook for managing IDE collaboration features including
 * real-time synchronization, presence tracking, and conflict resolution.
 * 
 * @doc.type hook
 * @doc.purpose IDE collaboration management
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect, useRef, useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import { WebsocketProvider } from 'y-websocket';
import * as Y from 'yjs';
import type { CRDTOperation, VectorClock } from '../../../crdt-ide/src';
import { IDECRDTHandler } from '../crdt/ide-handler';
import { IDECanvasBridge } from '../crdt/ide-canvas-bridge';
import { createInitialIDEState } from '../crdt/ide-schema';
import {
  ideStateAtom,
  idePresenceAtom,
  ideActiveFileAtom,
} from '../state/atoms';
import type { IDECRDTState, IDEPresence, IDEState } from '../types';

/**
 * Collaboration hook configuration
 */
export interface UseIDECollaborationConfig {
  /** WebSocket URL for real-time sync */
  websocketUrl?: string;
  /** Room ID for collaboration session */
  roomId?: string;
  /** User ID for current user */
  userId: string;
  /** User name for display */
  userName: string;
  /** User color for presence indicators */
  userColor?: string;
  /** Enable real-time collaboration */
  enableRealTime?: boolean;
  /** Enable presence tracking */
  enablePresence?: boolean;
  /** Enable conflict resolution */
  enableConflictResolution?: boolean;
  /** Auto-save interval in milliseconds */
  autoSaveInterval?: number;
}

/**
 * Collaboration hook return value
 */
export interface UseIDECollaborationReturn {
  /** Current collaboration state */
  state: IDECRDTState;
  /** Connected users presence */
  presence: Record<string, IDEPresence>;
  /** Current user ID */
  userId: string;
  /** Connection status */
  isConnected: boolean;
  /** Sync status */
  isSyncing: boolean;
  /** Apply local operation */
  applyOperation: (operation: CRDTOperation) => Promise<boolean>;
  /** Create file */
  createFile: (path: string, content: string, language: string) => Promise<boolean>;
  /** Update file content */
  updateFileContent: (fileId: string, content: string) => Promise<boolean>;
  /** Delete file */
  deleteFile: (fileId: string) => Promise<boolean>;
  /** Update cursor position */
  updateCursorPosition: (line: number, column: number) => void;
  /** Update active file */
  updateActiveFile: (fileId: string | null) => void;
  /** Get file content */
  getFileContent: (fileId: string) => string;
  /** Save current state */
  saveState: () => Promise<boolean>;
  /** Force sync with server */
  forceSync: () => Promise<void>;
}

/**
 * Generate random user color
 */
function generateUserColor(): string {
  const colors = [
    '#FF6B6B', '#4ECDC4', '#45B7D1', '#96CEB4', '#FFEAA7',
    '#DDA0DD', '#98D8C8', '#FFD93D', '#6BCB77', '#FF6B9D',
  ];
  return colors[Math.floor(Math.random() * colors.length)];
}

/**
 * Create vector clock for operation
 */
function createVectorClock(replicaId: string, timestamp: number): VectorClock {
  return {
    id: replicaId,
    values: new Map([[replicaId, 1]]),
    timestamp,
  };
}

/**
 * IDE Collaboration Hook
 * 
 * @doc.param config - Hook configuration
 * @doc.returns Collaboration utilities and state
 */
export function useIDECollaboration(
  config: UseIDECollaborationConfig
): UseIDECollaborationReturn {
  const {
    websocketUrl = 'ws://localhost:1234',
    roomId = 'ide-room',
    userId,
    userName,
    userColor = generateUserColor(),
    enableRealTime = true,
    enablePresence = true,
    autoSaveInterval = 5000,
  } = config;

  // State management
  const [ideState, setIDEState] = useAtom(ideStateAtom);
  const [presence, setPresence] = useAtom(idePresenceAtom);
  const [activeFile] = useAtom(ideActiveFileAtom);

  // Refs for persistent objects
  const yDocRef = useRef<Y.Doc | null>(null);
  const providerRef = useRef<WebsocketProvider | null>(null);
  const ideHandlerRef = useRef<IDECRDTHandler | null>(null);
  const bridgeRef = useRef<IDECanvasBridge | null>(null);
  const isConnectedRef = useRef(false);
  const isSyncingRef = useRef(false);
  const autoSaveTimerRef = useRef<NodeJS.Timeout | null>(null);

  // Initialize collaboration
  useEffect(() => {
    if (!enableRealTime) return;

    const initializeCollaboration = async () => {
      try {
        // Initialize Yjs document
        yDocRef.current = new Y.Doc();

        // Initialize WebSocket provider
        providerRef.current = new WebsocketProvider(websocketUrl, roomId, yDocRef.current);

        // Initialize IDE state
        const initialState = createInitialIDEState();
        ideHandlerRef.current = new IDECRDTHandler(initialState);

        // Initialize bridge (if canvas is available)
        // bridgeRef.current = new IDECanvasBridge(ideHandlerRef.current, canvasState);

        // Set up event listeners
        providerRef.current.on('status', (event: { status: string }) => {
          isConnectedRef.current = event.status === 'connected';
          setPresence({
            ...presence,
            [userId]: {
              userId,
              userName,
              userColor,
              activeFileId: activeFile?.id || null,
              cursorPosition: null,
              selection: null,
              lastActivity: Date.now(),
            },
          });
        });

        providerRef.current.on('sync', (isSynced: boolean) => {
          isSyncingRef.current = !isSynced;
        });

        // Set up awareness for presence
        if (enablePresence && providerRef.current.awareness) {
          providerRef.current.awareness.setLocalStateField('user', {
            userId,
            userName,
            userColor,
            activeFileId: activeFile?.id || null,
            cursorPosition: null,
            lastActivity: Date.now(),
          });

          providerRef.current.awareness.on('change', () => {
            const states = providerRef.current?.awareness.getStates() || new Map();
            const presenceMap: Record<string, IDEPresence> = {};

            for (const [key, state] of states) {
              const userState = state as { user?: IDEPresence };
              if (userState.user) {
                presenceMap[key] = userState.user;
              }
            }

            setPresence(presenceMap);
          });
        }

        // Set up auto-save
        if (autoSaveInterval > 0) {
          autoSaveTimerRef.current = setInterval(() => {
            saveState();
          }, autoSaveInterval);
        }

        // Load initial state
        await loadState();

      } catch (error) {
        console.error('Failed to initialize collaboration:', error);
      }
    };

    initializeCollaboration();

    return () => {
      // Cleanup
      if (autoSaveTimerRef.current) {
        clearInterval(autoSaveTimerRef.current);
      }
      if (providerRef.current) {
        providerRef.current.destroy();
      }
      if (yDocRef.current) {
        yDocRef.current.destroy();
      }
      if (bridgeRef.current) {
        bridgeRef.current.cleanup();
      }
    };
  }, [
    websocketUrl,
    roomId,
    userId,
    userName,
    userColor,
    enableRealTime,
    enablePresence,
    activeFile,
  ]);

  // Update presence when active file changes
  useEffect(() => {
    if (!enablePresence || !providerRef.current?.awareness) return;

    providerRef.current.awareness.setLocalStateField('user', {
      userId,
      userName,
      userColor,
      activeFileId: activeFile?.id || null,
      cursorPosition: null,
      lastActivity: Date.now(),
    });
  }, [activeFile, userId, userName, userColor, enablePresence]);

  // Apply operation
  const applyOperation = useCallback(async (operation: CRDTOperation): Promise<boolean> => {
    if (!ideHandlerRef.current) return false;

    try {
      const result = ideHandlerRef.current.applyOperation(operation);

      if (result.success) {
        // Update local state
        const newState = ideHandlerRef.current.getState();
        setIDEState(newState as unknown as IDEState);

        // Broadcast to other clients
        if (enableRealTime && providerRef.current) {
          const yMap = yDocRef.current?.getMap('operations');
          if (yMap) {
            yMap.set(operation.id, operation);
          }
        }

        return true;
      } else {
        console.error('Operation failed:', result.error);
        return false;
      }
    } catch (error) {
      console.error('Error applying operation:', error);
      return false;
    }
  }, [enableRealTime, setIDEState]);

  // Create file
  const createFile = useCallback(async (
    path: string,
    content: string,
    language: string
  ): Promise<boolean> => {
    const operation = {
      id: `file-${Date.now()}-${Math.random()}`,
      replicaId: userId,
      type: 'insert' as const,
      targetId: 'ide-workspace',
      vectorClock: createVectorClock(userId, Date.now()),
      data: {
        path,
        content,
        language,
        metadata: {
          createdAt: Date.now(),
          size: content.length,
        },
      },
      timestamp: Date.now(),
      parents: [],
    };

    return applyOperation(operation);
  }, [userId, applyOperation]);

  // Update file content
  const updateFileContent = useCallback(async (
    fileId: string,
    content: string
  ): Promise<boolean> => {
    const operation = {
      id: `update-${Date.now()}-${Math.random()}`,
      replicaId: userId,
      type: 'update' as const,
      targetId: 'ide-workspace',
      vectorClock: createVectorClock(userId, Date.now()),
      data: {
        fileId,
        changes: [
          { index: 0, delete: 999999 }, // Delete all existing content
          { index: 0, insert: content }, // Insert new content
        ],
      },
      timestamp: Date.now(),
      parents: [],
    };

    return applyOperation(operation);
  }, [userId, applyOperation]);

  // Delete file
  const deleteFile = useCallback(async (fileId: string): Promise<boolean> => {
    const operation = {
      id: `delete-${Date.now()}-${Math.random()}`,
      replicaId: userId,
      type: 'delete' as const,
      targetId: 'ide-workspace',
      vectorClock: createVectorClock(userId, Date.now()),
      data: { fileId },
      timestamp: Date.now(),
      parents: [],
    };

    return applyOperation(operation);
  }, [userId, applyOperation]);

  // Update cursor position
  const updateCursorPosition = useCallback((line: number, column: number) => {
    if (!enablePresence || !providerRef.current?.awareness) return;

    providerRef.current.awareness.setLocalStateField('user', {
      userId,
      userName,
      userColor,
      activeFileId: activeFile?.id || null,
      cursorPosition: { line, column },
      lastActivity: Date.now(),
    });
  }, [userId, userName, userColor, activeFile, enablePresence]);

  // Update active file
  const updateActiveFile = useCallback((fileId: string | null) => {
    if (!enablePresence || !providerRef.current?.awareness) return;

    providerRef.current.awareness.setLocalStateField('user', {
      userId,
      userName,
      userColor,
      activeFileId: fileId,
      cursorPosition: null,
      lastActivity: Date.now(),
    });
  }, [userId, userName, userColor, enablePresence]);

  // Get file content
  const getFileContent = useCallback((fileId: string): string => {
    if (!ideHandlerRef.current) return '';
    return ideHandlerRef.current.getFileContent(fileId);
  }, []);

  // Save state
  const saveState = useCallback(async (): Promise<boolean> => {
    if (!ideHandlerRef.current) return false;

    try {
      const state = ideHandlerRef.current.getState();

      // Save to localStorage as backup
      localStorage.setItem('ide-state-backup', JSON.stringify({
        files: Array.from(state.files.entries()),
        folders: Array.from(state.folders.entries()),
        settings: state.settings,
        timestamp: Date.now(),
      }));

      // Save to server if connected
      if (enableRealTime && providerRef.current) {
        const yMap = yDocRef.current?.getMap('state');
        if (yMap) {
          yMap.set('current', state);
        }
      }

      return true;
    } catch (error) {
      console.error('Failed to save state:', error);
      return false;
    }
  }, [enableRealTime]);

  // Load state
  const loadState = useCallback(async () => {
    try {
      // Try to load from server first
      if (enableRealTime && providerRef.current) {
        const yMap = yDocRef.current?.getMap('state');
        if (yMap) {
          const serverState = yMap.get('current');
          if (serverState) {
            setIDEState(serverState as unknown as IDEState);
            return;
          }
        }
      }

      // Fallback to localStorage
      const backup = localStorage.getItem('ide-state-backup');
      if (backup) {
        const parsed = JSON.parse(backup);
        // Restore state from backup
        setIDEState(parsed as unknown as IDEState);
      }
    } catch (error) {
      console.error('Failed to load state:', error);
    }
  }, [enableRealTime, setIDEState]);

  // Force sync
  const forceSync = useCallback(async () => {
    if (!enableRealTime || !providerRef.current) return;

    try {
      isSyncingRef.current = true;
      // Note: WebsocketProvider doesn't have a sync method
      // This would need to be implemented based on the actual provider API
      await loadState();
    } catch (error) {
      console.error('Failed to force sync:', error);
    } finally {
      isSyncingRef.current = false;
    }
  }, [enableRealTime, loadState]);

  // Memoized return value
  return useMemo(() => ({
    state: ideState,
    presence,
    userId,
    isConnected: isConnectedRef.current,
    isSyncing: isSyncingRef.current,
    applyOperation,
    createFile,
    updateFileContent,
    deleteFile,
    updateCursorPosition,
    updateActiveFile,
    getFileContent,
    saveState,
    forceSync,
  }), [
    ideState,
    presence,
    userId,
    applyOperation,
    createFile,
    updateFileContent,
    deleteFile,
    updateCursorPosition,
    updateActiveFile,
    getFileContent,
    saveState,
    forceSync,
  ]);
}
