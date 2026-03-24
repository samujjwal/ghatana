/**
 * Consolidated Canvas Collaboration Hook
 * 
 * Replaces: useCollaboration, useAdvancedCollaboration, useCanvasCollaborationBackend, useBidirectionalSync
 * Provides: Real-time collaboration, presence, cursors, comments, sync
 * 
 * @doc.type hook
 * @doc.purpose Consolidated collaboration features
 * @doc.layer presentation
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAtom } from 'jotai';
import { atom } from 'jotai';

export interface User {
  id: string;
  name: string;
  email: string;
  avatar?: string;
  color: string;
}

export interface Cursor {
  userId: string;
  x: number;
  y: number;
  timestamp: number;
}

export interface Comment {
  id: string;
  nodeId: string;
  userId: string;
  userName: string;
  text: string;
  createdAt: Date;
  resolved: boolean;
  replies: Comment[];
}

export interface Conflict {
  id: string;
  type: 'node' | 'edge' | 'viewport';
  entityId: string;
  localVersion: unknown;
  remoteVersion: unknown;
  timestamp: Date;
}

export interface Resolution {
  conflictId: string;
  choice: 'local' | 'remote' | 'merge';
  mergedData?: unknown;
}

export type SyncStatus = 'connected' | 'disconnected' | 'syncing' | 'error';

export interface UseCanvasCollaborationOptions {
  canvasId: string;
  userId: string;
  enablePresence?: boolean;
  enableCursors?: boolean;
  enableComments?: boolean;
  websocketUrl?: string;
}

export interface UseCanvasCollaborationReturn {
  // Presence
  activeUsers: User[];
  isUserActive: (userId: string) => boolean;
  
  // Cursors
  userCursors: Map<string, Cursor>;
  updateCursor: (x: number, y: number) => void;
  
  // Sync
  syncStatus: SyncStatus;
  lastSyncTime: Date | null;
  conflicts: Conflict[];
  resolveConflict: (conflictId: string, resolution: Resolution) => void;
  forceSyncNow: () => Promise<void>;
  
  // Comments
  comments: Comment[];
  addComment: (nodeId: string, text: string) => Promise<void>;
  replyToComment: (commentId: string, text: string) => Promise<void>;
  resolveComment: (commentId: string) => Promise<void>;
  deleteComment: (commentId: string) => Promise<void>;
  
  // Connection
  isConnected: boolean;
  reconnect: () => void;
  disconnect: () => void;
}

// Atoms for collaboration state
const activeUsersAtom = atom<User[]>([]);
const userCursorsAtom = atom<Map<string, Cursor>>(new Map());
const syncStatusAtom = atom<SyncStatus>('disconnected');
const conflictsAtom = atom<Conflict[]>([]);
const commentsAtom = atom<Comment[]>([]);

export function useCanvasCollaboration(
  options: UseCanvasCollaborationOptions
): UseCanvasCollaborationReturn {
  const {
    canvasId,
    userId,
    enablePresence = true,
    enableCursors = true,
    enableComments = true,
    websocketUrl = 'ws://localhost:7001/ws/canvas',
  } = options;

  // State
  const [activeUsers, setActiveUsers] = useAtom(activeUsersAtom);
  const [userCursors, setUserCursors] = useAtom(userCursorsAtom);
  const [syncStatus, setSyncStatus] = useAtom(syncStatusAtom);
  const [conflicts, setConflicts] = useAtom(conflictsAtom);
  const [comments, setComments] = useAtom(commentsAtom);
  const [lastSyncTime, setLastSyncTime] = useState<Date | null>(null);
  const [ws, setWs] = useState<WebSocket | null>(null);

  // Connection status
  const isConnected = syncStatus === 'connected';

  // Presence
  const isUserActive = useCallback(
    (checkUserId: string) => {
      return activeUsers.some((user) => user.id === checkUserId);
    },
    [activeUsers]
  );

  // Cursor updates
  const updateCursor = useCallback(
    (x: number, y: number) => {
      if (!enableCursors || !ws || syncStatus !== 'connected') return;

      const cursor: Cursor = {
        userId,
        x,
        y,
        timestamp: Date.now(),
      };

      // Send cursor update via WebSocket
      ws.send(
        JSON.stringify({
          type: 'cursor_update',
          canvasId,
          cursor,
        })
      );

      // Update local state
      setUserCursors((prev) => {
        const next = new Map(prev);
        next.set(userId, cursor);
        return next;
      });
    },
    [enableCursors, ws, syncStatus, userId, canvasId, setUserCursors]
  );

  // Conflict resolution
  const resolveConflict = useCallback(
    (conflictId: string, resolution: Resolution) => {
      setConflicts((prev) => prev.filter((c) => c.id !== conflictId));

      // Send resolution to backend
      if (ws && syncStatus === 'connected') {
        ws.send(
          JSON.stringify({
            type: 'resolve_conflict',
            canvasId,
            conflictId,
            resolution,
          })
        );
      }
    },
    [ws, syncStatus, canvasId, setConflicts]
  );

  // Force sync
  const forceSyncNow = useCallback(async () => {
    if (!ws || syncStatus !== 'connected') {
      throw new Error('Not connected to collaboration server');
    }

    setSyncStatus('syncing');

    return new Promise<void>((resolve, reject) => {
      const timeout = setTimeout(() => {
        reject(new Error('Sync timeout'));
        setSyncStatus('connected');
      }, 10000);

      const handleMessage = (event: MessageEvent) => {
        const data = JSON.parse(event.data);
        if (data.type === 'sync_complete') {
          clearTimeout(timeout);
          setLastSyncTime(new Date());
          setSyncStatus('connected');
          ws.removeEventListener('message', handleMessage);
          resolve();
        }
      };

      ws.addEventListener('message', handleMessage);
      ws.send(
        JSON.stringify({
          type: 'force_sync',
          canvasId,
          userId,
        })
      );
    });
  }, [ws, syncStatus, canvasId, userId, setSyncStatus]);

  // Comments
  const addComment = useCallback(
    async (nodeId: string, text: string) => {
      const comment: Comment = {
        id: `comment-${Date.now()}`,
        nodeId,
        userId,
        userName: 'Current User', // NOTE: Get from user context
        text,
        createdAt: new Date(),
        resolved: false,
        replies: [],
      };

      setComments((prev) => [...prev, comment]);

      // Send to backend
      if (ws && syncStatus === 'connected') {
        ws.send(
          JSON.stringify({
            type: 'add_comment',
            canvasId,
            comment,
          })
        );
      }
    },
    [userId, canvasId, ws, syncStatus, setComments]
  );

  const replyToComment = useCallback(
    async (commentId: string, text: string) => {
      const reply: Comment = {
        id: `reply-${Date.now()}`,
        nodeId: '', // Parent comment's nodeId
        userId,
        userName: 'Current User',
        text,
        createdAt: new Date(),
        resolved: false,
        replies: [],
      };

      setComments((prev) =>
        prev.map((comment) =>
          comment.id === commentId
            ? { ...comment, replies: [...comment.replies, reply] }
            : comment
        )
      );

      if (ws && syncStatus === 'connected') {
        ws.send(
          JSON.stringify({
            type: 'reply_comment',
            canvasId,
            commentId,
            reply,
          })
        );
      }
    },
    [userId, canvasId, ws, syncStatus, setComments]
  );

  const resolveComment = useCallback(
    async (commentId: string) => {
      setComments((prev) =>
        prev.map((comment) =>
          comment.id === commentId ? { ...comment, resolved: true } : comment
        )
      );

      if (ws && syncStatus === 'connected') {
        ws.send(
          JSON.stringify({
            type: 'resolve_comment',
            canvasId,
            commentId,
          })
        );
      }
    },
    [canvasId, ws, syncStatus, setComments]
  );

  const deleteComment = useCallback(
    async (commentId: string) => {
      setComments((prev) => prev.filter((c) => c.id !== commentId));

      if (ws && syncStatus === 'connected') {
        ws.send(
          JSON.stringify({
            type: 'delete_comment',
            canvasId,
            commentId,
          })
        );
      }
    },
    [canvasId, ws, syncStatus, setComments]
  );

  // Connection management
  const connect = useCallback(() => {
    if (ws?.readyState === WebSocket.OPEN) return;

    const websocket = new WebSocket(`${websocketUrl}?canvasId=${canvasId}&userId=${userId}`);

    websocket.onopen = () => {
      console.log('Connected to collaboration server');
      setSyncStatus('connected');
      setLastSyncTime(new Date());
    };

    websocket.onmessage = (event) => {
      const data = JSON.parse(event.data);

      switch (data.type) {
        case 'user_joined':
          setActiveUsers((prev) => [...prev, data.user]);
          break;

        case 'user_left':
          setActiveUsers((prev) => prev.filter((u) => u.id !== data.userId));
          setUserCursors((prev) => {
            const next = new Map(prev);
            next.delete(data.userId);
            return next;
          });
          break;

        case 'cursor_update':
          setUserCursors((prev) => {
            const next = new Map(prev);
            next.set(data.cursor.userId, data.cursor);
            return next;
          });
          break;

        case 'conflict_detected':
          setConflicts((prev) => [...prev, data.conflict]);
          break;

        case 'comment_added':
          setComments((prev) => [...prev, data.comment]);
          break;

        case 'comment_updated':
          setComments((prev) =>
            prev.map((c) => (c.id === data.comment.id ? data.comment : c))
          );
          break;

        case 'sync_update':
          setLastSyncTime(new Date());
          break;

        default:
          console.log('Unknown message type:', data.type);
      }
    };

    websocket.onerror = (error) => {
      console.error('WebSocket error:', error);
      setSyncStatus('error');
    };

    websocket.onclose = () => {
      console.log('Disconnected from collaboration server');
      setSyncStatus('disconnected');
      setActiveUsers([]);
      setUserCursors(new Map());
    };

    setWs(websocket);
  }, [websocketUrl, canvasId, userId, ws, setSyncStatus, setActiveUsers, setUserCursors, setConflicts, setComments]);

  const reconnect = useCallback(() => {
    if (ws) {
      ws.close();
    }
    connect();
  }, [ws, connect]);

  const disconnect = useCallback(() => {
    if (ws) {
      ws.close();
      setWs(null);
    }
  }, [ws]);

  // Auto-connect on mount
  useEffect(() => {
    connect();

    return () => {
      disconnect();
    };
  }, [canvasId, userId]); // Only reconnect if canvasId or userId changes

  // Cursor cleanup (remove stale cursors)
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      setUserCursors((prev) => {
        const next = new Map(prev);
        for (const [userId, cursor] of next.entries()) {
          if (now - cursor.timestamp > 5000) {
            // 5 seconds
            next.delete(userId);
          }
        }
        return next;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [setUserCursors]);

  return {
    // Presence
    activeUsers,
    isUserActive,

    // Cursors
    userCursors,
    updateCursor,

    // Sync
    syncStatus,
    lastSyncTime,
    conflicts,
    resolveConflict,
    forceSyncNow,

    // Comments
    comments,
    addComment,
    replyToComment,
    resolveComment,
    deleteComment,

    // Connection
    isConnected,
    reconnect,
    disconnect,
  };
}
