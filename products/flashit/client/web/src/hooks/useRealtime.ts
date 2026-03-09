/**
 * Real-Time WebSocket Client Hook for Web
 * Connects to the collaboration server for real-time updates
 * 
 * @doc.type hook
 * @doc.purpose Provide real-time updates and presence for web app
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { io, Socket } from 'socket.io-client';

// Re-export types from server (for type consistency)
export type CollaborationEventType =
  | 'sphere_shared' | 'sphere_unshared' | 'sphere_joined' | 'sphere_left'
  | 'moment_created' | 'moment_edited' | 'moment_deleted' | 'moment_commented'
  | 'comment_created' | 'comment_edited' | 'comment_deleted'
  | 'user_followed' | 'user_unfollowed' | 'presence_update';

export type PresenceStatus = 'active' | 'idle' | 'away';

export interface PresenceData {
  userId: string;
  displayName: string;
  avatar?: string;
  status: PresenceStatus;
  currentLocation?: {
    type: 'sphere' | 'moment' | 'search' | 'analytics';
    id?: string;
    label?: string;
  };
  cursor?: {
    momentId: string;
    position: number;
    selection?: [number, number];
  };
  lastActivity: string;
}

export interface MomentUpdate {
  id: string;
  sphereId: string;
  content?: string;
  type?: string;
  updatedBy: string;
  timestamp: string;
}

export interface CommentUpdate {
  id: string;
  momentId: string;
  content: string;
  authorId: string;
  authorName: string;
  timestamp: string;
}

export interface TypingIndicator {
  userId: string;
  displayName: string;
  momentId: string;
  isTyping: boolean;
}

export interface ReactionUpdate {
  momentId: string;
  userId: string;
  reaction: string;
  action: 'add' | 'remove';
}

export interface ConnectionState {
  isConnected: boolean;
  isConnecting: boolean;
  isReconnecting: boolean;
  error: string | null;
  reconnectAttempts: number;
}

export interface UseRealtimeOptions {
  serverUrl?: string;
  authToken?: string;
  autoConnect?: boolean;
  reconnectionAttempts?: number;
  reconnectionDelay?: number;
  onConnect?: () => void;
  onDisconnect?: (reason: string) => void;
  onError?: (error: string) => void;
}

export interface RealtimeControls {
  connect: () => void;
  disconnect: () => void;
  joinSphere: (sphereId: string) => void;
  leaveSphere: (sphereId: string) => void;
  joinMoment: (momentId: string) => void;
  leaveMoment: (momentId: string) => void;
  updatePresence: (presence: Partial<PresenceData>) => void;
  sendTypingIndicator: (momentId: string, isTyping: boolean) => void;
  sendReaction: (momentId: string, reaction: string, action: 'add' | 'remove') => void;
}

const DEFAULT_SERVER_URL = process.env.NEXT_PUBLIC_WS_URL || 'http://localhost:3001';

/**
 * Real-time WebSocket hook for live updates and collaboration
 * 
 * @example
 * ```tsx
 * const { 
 *   state, 
 *   controls, 
 *   presence, 
 *   momentUpdates 
 * } = useRealtime({
 *   authToken: userToken,
 *   onConnect: () => console.log('Connected'),
 * });
 * 
 * // Join a sphere for updates
 * useEffect(() => {
 *   controls.joinSphere(activeSphereId);
 *   return () => controls.leaveSphere(activeSphereId);
 * }, [activeSphereId]);
 * ```
 */
export function useRealtime(options: UseRealtimeOptions = {}): {
  state: ConnectionState;
  controls: RealtimeControls;
  presence: Map<string, PresenceData>;
  typingUsers: TypingIndicator[];
  momentUpdates: MomentUpdate[];
  commentUpdates: CommentUpdate[];
  reactionUpdates: ReactionUpdate[];
} {
  const {
    serverUrl = DEFAULT_SERVER_URL,
    authToken,
    autoConnect = true,
    reconnectionAttempts = 5,
    reconnectionDelay = 1000,
    onConnect,
    onDisconnect,
    onError,
  } = options;

  // State
  const [state, setState] = useState<ConnectionState>({
    isConnected: false,
    isConnecting: false,
    isReconnecting: false,
    error: null,
    reconnectAttempts: 0,
  });

  const [presence, setPresence] = useState<Map<string, PresenceData>>(new Map());
  const [typingUsers, setTypingUsers] = useState<TypingIndicator[]>([]);
  const [momentUpdates, setMomentUpdates] = useState<MomentUpdate[]>([]);
  const [commentUpdates, setCommentUpdates] = useState<CommentUpdate[]>([]);
  const [reactionUpdates, setReactionUpdates] = useState<ReactionUpdate[]>([]);

  // Refs
  const socketRef = useRef<Socket | null>(null);
  const joinedSpheresRef = useRef<Set<string>>(new Set());
  const joinedMomentsRef = useRef<Set<string>>(new Set());
  const typingTimeoutsRef = useRef<Map<string, NodeJS.Timeout>>(new Map());

  // Create socket connection
  const createSocket = useCallback(() => {
    if (socketRef.current?.connected) return;

    setState(prev => ({ ...prev, isConnecting: true, error: null }));

    socketRef.current = io(serverUrl, {
      auth: { token: authToken },
      reconnection: true,
      reconnectionAttempts,
      reconnectionDelay,
      transports: ['websocket', 'polling'],
    });

    // Connection handlers
    socketRef.current.on('connect', () => {
      setState({
        isConnected: true,
        isConnecting: false,
        isReconnecting: false,
        error: null,
        reconnectAttempts: 0,
      });

      // Rejoin previously joined rooms
      joinedSpheresRef.current.forEach(sphereId => {
        socketRef.current?.emit('join_sphere', { sphereId });
      });
      joinedMomentsRef.current.forEach(momentId => {
        socketRef.current?.emit('join_moment', { momentId });
      });

      onConnect?.();
    });

    socketRef.current.on('disconnect', (reason) => {
      setState(prev => ({
        ...prev,
        isConnected: false,
        isReconnecting: reason === 'io server disconnect' ? false : true,
      }));
      onDisconnect?.(reason);
    });

    socketRef.current.on('connect_error', (error) => {
      setState(prev => ({
        ...prev,
        isConnecting: false,
        error: error.message,
      }));
      onError?.(error.message);
    });

    socketRef.current.on('reconnect_attempt', (attempt) => {
      setState(prev => ({
        ...prev,
        isReconnecting: true,
        reconnectAttempts: attempt,
      }));
    });

    socketRef.current.on('reconnect_failed', () => {
      setState(prev => ({
        ...prev,
        isReconnecting: false,
        error: 'Failed to reconnect after maximum attempts',
      }));
    });

    // Presence updates
    socketRef.current.on('presence_update', (data: PresenceData) => {
      setPresence(prev => {
        const newMap = new Map(prev);
        newMap.set(data.userId, data);
        return newMap;
      });
    });

    socketRef.current.on('user_left', (data: { userId: string }) => {
      setPresence(prev => {
        const newMap = new Map(prev);
        newMap.delete(data.userId);
        return newMap;
      });
    });

    // Typing indicators
    socketRef.current.on('typing_indicator', (data: TypingIndicator) => {
      setTypingUsers(prev => {
        if (data.isTyping) {
          // Add or update typing user
          const existing = prev.filter(t => t.userId !== data.userId);
          return [...existing, data];
        } else {
          // Remove typing user
          return prev.filter(t => t.userId !== data.userId);
        }
      });

      // Auto-clear typing indicator after 5 seconds
      const existingTimeout = typingTimeoutsRef.current.get(data.userId);
      if (existingTimeout) clearTimeout(existingTimeout);
      
      if (data.isTyping) {
        const timeout = setTimeout(() => {
          setTypingUsers(prev => prev.filter(t => t.userId !== data.userId));
        }, 5000);
        typingTimeoutsRef.current.set(data.userId, timeout);
      }
    });

    // Moment updates
    socketRef.current.on('moment_created', (data: MomentUpdate) => {
      setMomentUpdates(prev => [...prev.slice(-49), data]);
    });

    socketRef.current.on('moment_edited', (data: MomentUpdate) => {
      setMomentUpdates(prev => [...prev.slice(-49), data]);
    });

    socketRef.current.on('moment_deleted', (data: { id: string; sphereId: string }) => {
      setMomentUpdates(prev => [...prev.slice(-49), { 
        ...data, 
        updatedBy: 'system', 
        timestamp: new Date().toISOString() 
      }]);
    });

    // Comment updates
    socketRef.current.on('comment_created', (data: CommentUpdate) => {
      setCommentUpdates(prev => [...prev.slice(-49), data]);
    });

    socketRef.current.on('comment_deleted', (data: { id: string; momentId: string }) => {
      setCommentUpdates(prev => [...prev.slice(-49), { 
        ...data, 
        content: '', 
        authorId: 'system', 
        authorName: 'System',
        timestamp: new Date().toISOString() 
      }]);
    });

    // Reaction updates
    socketRef.current.on('reaction_update', (data: ReactionUpdate) => {
      setReactionUpdates(prev => [...prev.slice(-99), data]);
    });

  }, [serverUrl, authToken, reconnectionAttempts, reconnectionDelay, onConnect, onDisconnect, onError]);

  // Connect
  const connect = useCallback(() => {
    if (!authToken) {
      setState(prev => ({ ...prev, error: 'Authentication token required' }));
      return;
    }
    createSocket();
  }, [authToken, createSocket]);

  // Disconnect
  const disconnect = useCallback(() => {
    if (socketRef.current) {
      socketRef.current.disconnect();
      socketRef.current = null;
    }
    setState({
      isConnected: false,
      isConnecting: false,
      isReconnecting: false,
      error: null,
      reconnectAttempts: 0,
    });
    setPresence(new Map());
    setTypingUsers([]);
    joinedSpheresRef.current.clear();
    joinedMomentsRef.current.clear();
  }, []);

  // Join sphere
  const joinSphere = useCallback((sphereId: string) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('join_sphere', { sphereId });
    }
    joinedSpheresRef.current.add(sphereId);
  }, []);

  // Leave sphere
  const leaveSphere = useCallback((sphereId: string) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('leave_sphere', { sphereId });
    }
    joinedSpheresRef.current.delete(sphereId);
  }, []);

  // Join moment
  const joinMoment = useCallback((momentId: string) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('join_moment', { momentId });
    }
    joinedMomentsRef.current.add(momentId);
  }, []);

  // Leave moment
  const leaveMoment = useCallback((momentId: string) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('leave_moment', { momentId });
    }
    joinedMomentsRef.current.delete(momentId);
  }, []);

  // Update presence
  const updatePresence = useCallback((presenceData: Partial<PresenceData>) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('presence_update', presenceData);
    }
  }, []);

  // Send typing indicator
  const sendTypingIndicator = useCallback((momentId: string, isTyping: boolean) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('typing_indicator', { momentId, isTyping });
    }
  }, []);

  // Send reaction
  const sendReaction = useCallback((momentId: string, reaction: string, action: 'add' | 'remove') => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('reaction', { momentId, reaction, action });
    }
  }, []);

  // Auto-connect on mount if enabled
  useEffect(() => {
    if (autoConnect && authToken) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, authToken]); // Note: intentionally not including connect/disconnect to avoid loops

  // Cleanup typing timeouts on unmount
  useEffect(() => {
    return () => {
      typingTimeoutsRef.current.forEach(timeout => clearTimeout(timeout));
    };
  }, []);

  return {
    state,
    controls: {
      connect,
      disconnect,
      joinSphere,
      leaveSphere,
      joinMoment,
      leaveMoment,
      updatePresence,
      sendTypingIndicator,
      sendReaction,
    },
    presence,
    typingUsers,
    momentUpdates,
    commentUpdates,
    reactionUpdates,
  };
}

export default useRealtime;
