/**
 * @ghatana/yappc-ide - Collaborative Editing Hook
 * 
 * Real-time collaborative editing with cursor tracking,
 * presence awareness, and conflict resolution.
 * 
 * @doc.type module
 * @doc.purpose Collaborative editing hook for IDE
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useAtom } from 'jotai';
import { useState, useCallback, useEffect, useRef } from 'react';

import { useWebSocketService, useMockWebSocketService } from './useWebSocketService';
import { ideCollaborationAtom, ideActiveFileIdAtom } from '../state/atoms';

/**
 * User presence information
 */
export interface UserPresence {
  userId: string;
  userName: string;
  userColor: string;
  cursor: {
    fileId: string | null;
    position: { line: number; column: number };
    selection?: { start: { line: number; column: number }; end: { line: number; column: number } };
  };
  activity: 'idle' | 'typing' | 'selecting';
  lastSeen: number;
  isOnline: boolean;
  avatar?: string;
}

/**
 * Collaboration event types
 */
export interface CollaborationEvent {
  type: 'cursor-move' | 'selection-change' | 'text-edit' | 'user-join' | 'user-leave' | 'presence-update';
  userId: string;
  data: unknown;
  timestamp: number;
  fileId?: string;
}

/**
 * Conflict information
 */
export interface Conflict {
  id: string;
  fileId: string;
  type: 'concurrent-edit' | 'selection-overlap' | 'file-lock';
  users: string[];
  position: { line: number; column: number };
  timestamp: number;
  resolved: boolean;
}

/**
 * Collaboration settings
 */
export interface CollaborationSettings {
  showCursors: boolean;
  showSelections: boolean;
  showAvatars: boolean;
  enableTypingIndicators: boolean;
  autoResolveConflicts: boolean;
  conflictResolutionStrategy: 'latest-wins' | 'manual' | 'merge';
}

/**
 * Hook for collaborative editing
 */
export function useCollaborativeEditing(fileId?: string) {
  const [activeFileId] = useAtom(ideActiveFileIdAtom);
  const [, _setCollaborationState] = useAtom(ideCollaborationAtom);

  // Try to use real WebSocket service, fallback to mock
  let wsService;
  try {
    wsService = useWebSocketService();
  } catch (error) {
    console.warn('WebSocket service unavailable, using mock service:', error);
    wsService = useMockWebSocketService();
  }

  const { sendMessage, addEventListener, removeEventListener } = wsService;

  // Local state
  const [activeUsers, setActiveUsers] = useState<UserPresence[]>([]);
  const [conflicts, setConflicts] = useState<Conflict[]>([]);
  const [settings, setSettings] = useState<CollaborationSettings>({
    showCursors: true,
    showSelections: true,
    showAvatars: true,
    enableTypingIndicators: true,
    autoResolveConflicts: false,
    conflictResolutionStrategy: 'manual',
  });

  // Refs for performance
  const lastActivityRef = useRef<number>(Date.now());
  const typingTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const activeFileRef = useRef<string | null>(fileId || activeFileId);

  // Update active file ref
  useEffect(() => {
    activeFileRef.current = fileId || activeFileId;
  }, [fileId, activeFileId]);

  // Broadcast cursor position
  const broadcastCursor = useCallback((position: { line: number; column: number }) => {
    const currentFileId = activeFileRef.current;
    if (!currentFileId) return;

    const event: CollaborationEvent = {
      type: 'cursor-move',
      userId: 'current-user', // Will be replaced with actual user ID
      data: { position, fileId: currentFileId },
      timestamp: Date.now(),
      fileId: currentFileId,
    };

    sendMessage('collaboration', event);
    lastActivityRef.current = Date.now();
  }, [sendMessage]);

  // Broadcast selection change
  const broadcastSelection = useCallback((
    selection: { start: { line: number; column: number }; end: { line: number; column: number } }
  ) => {
    const currentFileId = activeFileRef.current;
    if (!currentFileId) return;

    const event: CollaborationEvent = {
      type: 'selection-change',
      userId: 'current-user', // Will be replaced with actual user ID
      data: { selection, fileId: currentFileId },
      timestamp: Date.now(),
      fileId: currentFileId,
    };

    sendMessage('collaboration', event);
    lastActivityRef.current = Date.now();
  }, [sendMessage]);

  // Update user presence
  const updatePresence = useCallback((activity: UserPresence['activity']) => {
    const currentFileId = activeFileRef.current;
    if (!currentFileId) return;

    const presence: UserPresence = {
      userId: 'current-user', // Will be replaced with actual user ID
      userName: 'Current User',
      userColor: '#3B82F6',
      cursor: {
        fileId: currentFileId,
        position: { line: 0, column: 0 },
      },
      activity,
      lastSeen: Date.now(),
      isOnline: true,
    };

    const event: CollaborationEvent = {
      type: 'presence-update',
      userId: 'current-user',
      data: presence,
      timestamp: Date.now(),
      fileId: currentFileId,
    };

    sendMessage('collaboration', event);
  }, [sendMessage]);

  // Broadcast text edit
  const broadcastTextEdit = useCallback((
    operation: { type: 'insert' | 'delete'; position: { line: number; column: number }; text?: string; length?: number }
  ) => {
    const currentFileId = activeFileRef.current;
    if (!currentFileId) return;

    const event: CollaborationEvent = {
      type: 'text-edit',
      userId: 'current-user', // Will be replaced with actual user ID
      data: { ...operation, fileId: currentFileId },
      timestamp: Date.now(),
      fileId: currentFileId,
    };

    sendMessage('collaboration', event);
    lastActivityRef.current = Date.now();

    // Clear typing timeout
    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    }

    // Set typing indicator
    updatePresence('typing');

    // Clear typing indicator after 2 seconds of inactivity
    typingTimeoutRef.current = setTimeout(() => {
      updatePresence('idle');
    }, 2000);
  }, [sendMessage, updatePresence]);

  // Handle incoming collaboration events
  const handleCollaborationEvent = useCallback((event: CollaborationEvent) => {
    switch (event.type) {
      case 'cursor-move':
        setActiveUsers(prev => prev.map(user =>
          user.userId === event.userId
            ? { ...user, cursor: event.data as UserPresence['cursor'], lastSeen: Date.now() }
            : user
        ));
        break;

      case 'selection-change':
        setActiveUsers(prev => prev.map(user =>
          user.userId === event.userId
            ? { ...user, cursor: { ...user.cursor, selection: event.data as UserPresence['cursor']['selection'] }, lastSeen: Date.now() }
            : user
        ));
        break;

      case 'text-edit':
        setActiveUsers(prev => prev.map(user =>
          user.userId === event.userId
            ? { ...user, activity: 'typing', lastSeen: Date.now() }
            : user
        ));
        break;

      case 'user-join': {
        const newUser = event.data as UserPresence;
        setActiveUsers(prev => [...prev.filter(u => u.userId !== newUser.userId), newUser]);
        break;
      }

      case 'user-leave':
        setActiveUsers(prev => prev.filter(u => u.userId !== event.userId));
        break;

      case 'presence-update':
        setActiveUsers(prev => prev.map(user =>
          user.userId === event.userId
            ? { ...user, ...(event.data as Partial<UserPresence>) }
            : user
        ));
        break;
    }
  }, []);

  // Handle conflict events
  const handleConflictEvent = useCallback((conflict: Conflict) => {
    setConflicts(prev => {
      const existing = prev.find(c => c.id === conflict.id);
      if (existing) {
        return prev.map(c => c.id === conflict.id ? conflict : c);
      }
      return [...prev, conflict];
    });
  }, []);

  // Resolve conflict
  const resolveConflict = useCallback(async (conflictId: string, resolution: 'accept' | 'reject' | 'merge') => {
    const conflict = conflicts.find(c => c.id === conflictId);
    if (!conflict) return;

    try {
      // Send resolution to server
      sendMessage('conflict-resolution', {
        conflictId,
        resolution,
        timestamp: Date.now(),
      });

      // Update local state
      setConflicts(prev => prev.map(c =>
        c.id === conflictId ? { ...c, resolved: true } : c
      ));
    } catch (error) {
      console.error('Failed to resolve conflict:', error);
    }
  }, [conflicts, sendMessage]);

  // Get users in current file
  const getUsersInFile = useCallback((targetFileId?: string) => {
    const fileId = targetFileId || activeFileRef.current;
    if (!fileId) return [];

    return activeUsers.filter(user =>
      user.cursor.fileId === fileId && user.isOnline
    );
  }, [activeUsers]);

  // Get conflicts for current file
  const getConflictsInFile = useCallback((targetFileId?: string) => {
    const fileId = targetFileId || activeFileRef.current;
    if (!fileId) return [];

    return conflicts.filter(conflict =>
      conflict.fileId === fileId && !conflict.resolved
    );
  }, [conflicts]);

  // Update settings
  const updateSettings = useCallback((newSettings: Partial<CollaborationSettings>) => {
    setSettings(prev => ({ ...prev, ...newSettings }));
  }, []);

  // Cleanup inactive users
  useEffect(() => {
    const cleanup = setInterval(() => {
      const now = Date.now();
      const timeout = 30000; // 30 seconds

      setActiveUsers(prev => prev.filter(user =>
        now - user.lastSeen < timeout || user.userId === 'current-user'
      ));
    }, 10000);

    return () => clearInterval(cleanup);
  }, []);

  // Setup event listeners
  const handleCollaborationWrapper = useCallback((data: unknown) => handleCollaborationEvent(data as CollaborationEvent), [handleCollaborationEvent]);
  const handleConflictWrapper = useCallback((data: unknown) => handleConflictEvent(data as Conflict), [handleConflictEvent]);

  useEffect(() => {
    addEventListener('collaboration', handleCollaborationWrapper);
    addEventListener('conflict', handleConflictWrapper);

    return () => {
      removeEventListener('collaboration', handleCollaborationWrapper);
      removeEventListener('conflict', handleConflictWrapper);
    };
  }, [addEventListener, removeEventListener, handleCollaborationWrapper, handleConflictWrapper]);

  // Cleanup typing timeout
  useEffect(() => {
    return () => {
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
    };
  }, []);

  return {
    // State
    activeUsers,
    conflicts,
    settings,

    // Actions
    broadcastCursor,
    broadcastSelection,
    broadcastTextEdit,
    updatePresence,
    resolveConflict,
    updateSettings,

    // Queries
    getUsersInFile,
    getConflictsInFile,

    // Utilities
    activeFileId: activeFileRef.current,
    isCollaborating: activeUsers.length > 1,
    userCount: activeUsers.length,
    conflictCount: conflicts.filter(c => !c.resolved).length,
  };
}

/**
 * Generate user color based on user ID
 */
export function generateUserColor(userId: string): string {
  const colors = [
    '#3B82F6', // blue
    '#10B981', // emerald
    '#F59E0B', // amber
    '#EF4444', // red
    '#8B5CF6', // violet
    '#EC4899', // pink
    '#14B8A6', // teal
    '#F97316', // orange
  ];

  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash);
  }

  return colors[Math.abs(hash) % colors.length];
}

/**
 * Generate user avatar URL
 */
export function generateUserAvatar(userName: string, userColor: string): string {
  const initials = userName
    .split(' ')
    .map(word => word.charAt(0).toUpperCase())
    .slice(0, 2)
    .join('');

  // Create a simple avatar using a data URL
  const canvas = document.createElement('canvas');
  canvas.width = 40;
  canvas.height = 40;
  const ctx = canvas.getContext('2d');

  if (ctx) {
    // Background
    ctx.fillStyle = userColor;
    ctx.fillRect(0, 0, 40, 40);

    // Text
    ctx.fillStyle = 'white';
    ctx.font = 'bold 16px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(initials, 20, 20);
  }

  return canvas.toDataURL();
}
