/**
 * Collaboration React Hooks
 *
 * @description React hooks for easy integration of real-time
 * collaboration features in components.
 */

import { useState, useEffect, useCallback, useMemo, useRef } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import {
  CollaborationManager,
  CollaborationConfig,
  CollaborationState,
  CollaborationUser,
  getCollaboration,
  destroyCollaboration,
} from './CollaborationManager';
import {
  CanvasCollaboration,
  CanvasNode,
  CanvasEdge,
  CanvasViewport,
  UserCursor,
  CanvasSelection,
} from './CanvasCollaboration';
import {
  DocumentCollaboration,
  TextCursor,
  DocumentComment,
  DocumentVersion,
} from './DocumentCollaboration';
import {
  PresenceManager,
  PresenceUser,
  PresenceState,
  PresenceLocation,
  getPresenceStatusColor,
  getPresenceStatusLabel,
  formatPresenceLocation,
} from './PresenceManager';

// =============================================================================
// Types
// =============================================================================

export interface UseCollaborationOptions {
  roomId: string;
  userId: string;
  userName: string;
  userColor?: string;
  serverUrl?: string;
  autoConnect?: boolean;
}

export interface UseCollaborationReturn {
  state: CollaborationState;
  connect: () => Promise<void>;
  disconnect: () => void;
  isConnecting: boolean;
  error: Error | null;
  manager: CollaborationManager | null;
}

export interface UseCanvasCollaborationOptions {
  manager: CollaborationManager;
  userId: string;
  initialNodes?: CanvasNode[];
  initialEdges?: CanvasEdge[];
}

export interface UseCanvasCollaborationReturn {
  nodes: CanvasNode[];
  edges: CanvasEdge[];
  cursors: UserCursor[];
  selections: Map<string, CanvasSelection>;
  addNode: (node: CanvasNode) => void;
  updateNode: (id: string, updates: Partial<CanvasNode>) => void;
  deleteNode: (id: string) => void;
  addEdge: (edge: CanvasEdge) => void;
  updateEdge: (id: string, updates: Partial<CanvasEdge>) => void;
  deleteEdge: (id: string) => void;
  updateNodePosition: (id: string, position: { x: number; y: number }) => void;
  updateCursor: (x: number, y: number) => void;
  updateSelection: (selection: CanvasSelection) => void;
  clearCanvas: () => void;
  exportCanvas: () => { nodes: CanvasNode[]; edges: CanvasEdge[] };
  importCanvas: (data: { nodes: CanvasNode[]; edges: CanvasEdge[] }) => void;
}

export interface UseDocumentCollaborationOptions {
  manager: CollaborationManager;
  userId: string;
  userName: string;
  userColor: string;
  documentId?: string;
  initialContent?: string;
}

export interface UseDocumentCollaborationReturn {
  text: string;
  cursors: TextCursor[];
  comments: DocumentComment[];
  versions: DocumentVersion[];
  insert: (position: number, content: string) => void;
  delete: (position: number, length: number) => void;
  setText: (content: string) => void;
  updateCursor: (position: number) => void;
  updateSelection: (start: number, end: number) => void;
  addComment: (start: number, end: number, content: string) => string;
  resolveComment: (id: string) => void;
  deleteComment: (id: string) => void;
  addReply: (commentId: string, content: string) => string;
  createVersion: (description: string) => string;
  restoreVersion: (versionId: string) => boolean;
}

export interface UsePresenceOptions {
  manager: CollaborationManager;
  userId: string;
  userName: string;
  userColor: string;
}

export interface UsePresenceReturn {
  users: PresenceUser[];
  onlineUsers: PresenceUser[];
  status: PresenceState['status'];
  setStatus: (status: PresenceState['status']) => void;
  setActivity: (activity: string) => void;
  setLocation: (location: PresenceLocation) => void;
  getStatusColor: (status: PresenceState['status']) => string;
  getStatusLabel: (status: PresenceState['status']) => string;
  formatLocation: (location: PresenceLocation) => string;
}

// =============================================================================
// Generate User Color
// =============================================================================

function generateUserColor(userId: string): string {
  const colors = [
    '#ef4444', // red
    '#f97316', // orange
    '#eab308', // yellow
    '#22c55e', // green
    '#14b8a6', // teal
    '#3b82f6', // blue
    '#8b5cf6', // violet
    '#ec4899', // pink
  ];

  let hash = 0;
  for (let i = 0; i < userId.length; i++) {
    hash = userId.charCodeAt(i) + ((hash << 5) - hash);
  }
  return colors[Math.abs(hash) % colors.length];
}

// =============================================================================
// useCollaboration Hook
// =============================================================================

export function useCollaboration(
  options: UseCollaborationOptions
): UseCollaborationReturn {
  const {
    roomId,
    userId,
    userName,
    userColor = generateUserColor(userId),
    serverUrl,
    autoConnect = true,
  } = options;

  const [state, setState] = useState<CollaborationState>({
    connected: false,
    synced: false,
    users: [],
  });
  const [isConnecting, setIsConnecting] = useState(false);
  const [error, setError] = useState<Error | null>(null);
  const managerRef = useRef<CollaborationManager | null>(null);

  const connect = useCallback(async () => {
    if (managerRef.current) return;

    setIsConnecting(true);
    setError(null);

    try {
      const config: CollaborationConfig = {
        roomId,
        userId,
        userName,
        userColor,
        serverUrl,
      };

      const manager = new CollaborationManager(config);
      managerRef.current = manager;

      // Set up state listeners
      manager.on('connection-change', ({ connected }) => {
        setState((prev) => ({ ...prev, connected }));
      });

      manager.on('sync-change', ({ synced }) => {
        setState((prev) => ({ ...prev, synced }));
      });

      manager.on('awareness-change', ({ users }) => {
        setState((prev) => ({ ...prev, users }));
      });

      await manager.connect();
    } catch (err) {
      setError(err as Error);
      managerRef.current = null;
    } finally {
      setIsConnecting(false);
    }
  }, [roomId, userId, userName, userColor, serverUrl]);

  const disconnect = useCallback(() => {
    if (managerRef.current) {
      managerRef.current.disconnect();
      managerRef.current = null;
      setState({
        connected: false,
        synced: false,
        users: [],
      });
    }
  }, []);

  // Auto-connect on mount
  useEffect(() => {
    if (autoConnect) {
      connect();
    }

    return () => {
      disconnect();
    };
  }, [autoConnect, connect, disconnect]);

  return {
    state,
    connect,
    disconnect,
    isConnecting,
    error,
    manager: managerRef.current,
  };
}

// =============================================================================
// useCanvasCollaboration Hook
// =============================================================================

export function useCanvasCollaboration(
  options: UseCanvasCollaborationOptions
): UseCanvasCollaborationReturn {
  const { manager, userId, initialNodes = [], initialEdges = [] } = options;

  const [nodes, setNodes] = useState<CanvasNode[]>(initialNodes);
  const [edges, setEdges] = useState<CanvasEdge[]>(initialEdges);
  const [cursors, setCursors] = useState<UserCursor[]>([]);
  const [selections, setSelections] = useState<Map<string, CanvasSelection>>(
    new Map()
  );

  const collabRef = useRef<CanvasCollaboration | null>(null);

  // Initialize canvas collaboration
  useEffect(() => {
    if (!manager) return;

    const collab = new CanvasCollaboration(manager, userId);
    collabRef.current = collab;

    // Initialize with any existing data or initial data
    const existingNodes = collab.getNodes();
    const existingEdges = collab.getEdges();

    if (existingNodes.length === 0 && initialNodes.length > 0) {
      collab.setCanvasState(initialNodes, initialEdges);
    } else {
      setNodes(existingNodes);
      setEdges(existingEdges);
    }

    // Set up listeners
    const unsubNodes = collab.on('nodes-change', ({ nodes }) => {
      setNodes(nodes);
    });

    const unsubEdges = collab.on('edges-change', ({ edges }) => {
      setEdges(edges);
    });

    const unsubCursors = collab.on('cursors-change', ({ cursors }) => {
      setCursors(cursors);
    });

    const unsubSelections = collab.on('selections-change', ({ selections }) => {
      setSelections(selections);
    });

    // Start cursor broadcasting
    collab.startCursorBroadcast();

    return () => {
      unsubNodes();
      unsubEdges();
      unsubCursors();
      unsubSelections();
      collab.destroy();
      collabRef.current = null;
    };
  }, [manager, userId]);

  // Node operations
  const addNode = useCallback((node: CanvasNode) => {
    collabRef.current?.addNode(node);
  }, []);

  const updateNode = useCallback((id: string, updates: Partial<CanvasNode>) => {
    collabRef.current?.updateNode(id, updates);
  }, []);

  const deleteNode = useCallback((id: string) => {
    collabRef.current?.deleteNode(id);
  }, []);

  const updateNodePosition = useCallback(
    (id: string, position: { x: number; y: number }) => {
      collabRef.current?.updateNodePosition(id, position);
    },
    []
  );

  // Edge operations
  const addEdge = useCallback((edge: CanvasEdge) => {
    collabRef.current?.addEdge(edge);
  }, []);

  const updateEdge = useCallback((id: string, updates: Partial<CanvasEdge>) => {
    collabRef.current?.updateEdge(id, updates);
  }, []);

  const deleteEdge = useCallback((id: string) => {
    collabRef.current?.deleteEdge(id);
  }, []);

  // Cursor and selection
  const updateCursor = useCallback((x: number, y: number) => {
    collabRef.current?.updateCursorPosition(x, y);
  }, []);

  const updateSelection = useCallback((selection: CanvasSelection) => {
    collabRef.current?.updateSelection(selection);
  }, []);

  // Bulk operations
  const clearCanvas = useCallback(() => {
    collabRef.current?.clearCanvas();
  }, []);

  const exportCanvas = useCallback(() => {
    return collabRef.current?.exportToJSON() || { nodes: [], edges: [] };
  }, []);

  const importCanvas = useCallback(
    (data: { nodes: CanvasNode[]; edges: CanvasEdge[] }) => {
      collabRef.current?.importFromJSON(data);
    },
    []
  );

  return {
    nodes,
    edges,
    cursors,
    selections,
    addNode,
    updateNode,
    deleteNode,
    addEdge,
    updateEdge,
    deleteEdge,
    updateNodePosition,
    updateCursor,
    updateSelection,
    clearCanvas,
    exportCanvas,
    importCanvas,
  };
}

// =============================================================================
// useDocumentCollaboration Hook
// =============================================================================

export function useDocumentCollaboration(
  options: UseDocumentCollaborationOptions
): UseDocumentCollaborationReturn {
  const {
    manager,
    userId,
    userName,
    userColor,
    documentId = 'main',
    initialContent,
  } = options;

  const [text, setText] = useState('');
  const [cursors, setCursors] = useState<TextCursor[]>([]);
  const [comments, setComments] = useState<DocumentComment[]>([]);
  const [versions, setVersions] = useState<DocumentVersion[]>([]);

  const collabRef = useRef<DocumentCollaboration | null>(null);

  // Initialize document collaboration
  useEffect(() => {
    if (!manager) return;

    const collab = new DocumentCollaboration(manager, userId, documentId);
    collabRef.current = collab;

    // Initialize with content if provided and document is empty
    const existingText = collab.getText();
    if (!existingText && initialContent) {
      collab.setText(initialContent);
    } else {
      setText(existingText);
    }

    // Load initial data
    setComments(collab.getComments());
    setVersions(collab.getVersions());

    // Set up listeners
    const unsubText = collab.on('text-change', ({ text }) => {
      setText(text);
    });

    const unsubCursors = collab.on('cursors-change', ({ cursors }) => {
      setCursors(cursors);
    });

    const unsubComments = collab.on('comments-change', ({ comments }) => {
      setComments(comments);
    });

    const unsubVersions = collab.on('versions-change', ({ versions }) => {
      setVersions(versions);
    });

    return () => {
      unsubText();
      unsubCursors();
      unsubComments();
      unsubVersions();
      collab.destroy();
      collabRef.current = null;
    };
  }, [manager, userId, documentId]);

  // Text operations
  const insert = useCallback((position: number, content: string) => {
    collabRef.current?.insert(position, content);
  }, []);

  const deleteText = useCallback((position: number, length: number) => {
    collabRef.current?.delete(position, length);
  }, []);

  const setTextContent = useCallback((content: string) => {
    collabRef.current?.setText(content);
  }, []);

  // Cursor operations
  const updateCursor = useCallback((position: number) => {
    collabRef.current?.updateCursor(position);
  }, []);

  const updateSelection = useCallback((start: number, end: number) => {
    collabRef.current?.updateSelection(start, end);
  }, []);

  // Comment operations
  const addComment = useCallback(
    (start: number, end: number, content: string) => {
      return (
        collabRef.current?.addComment(start, end, content, userName, userColor) ||
        ''
      );
    },
    [userName, userColor]
  );

  const resolveComment = useCallback((id: string) => {
    collabRef.current?.resolveComment(id);
  }, []);

  const deleteComment = useCallback((id: string) => {
    collabRef.current?.deleteComment(id);
  }, []);

  const addReply = useCallback(
    (commentId: string, content: string) => {
      return collabRef.current?.addReply(commentId, content, userName) || '';
    },
    [userName]
  );

  // Version operations
  const createVersion = useCallback(
    (description: string) => {
      return collabRef.current?.createVersion(description, userName) || '';
    },
    [userName]
  );

  const restoreVersion = useCallback((versionId: string) => {
    return collabRef.current?.restoreVersion(versionId) || false;
  }, []);

  return {
    text,
    cursors,
    comments,
    versions,
    insert,
    delete: deleteText,
    setText: setTextContent,
    updateCursor,
    updateSelection,
    addComment,
    resolveComment,
    deleteComment,
    addReply,
    createVersion,
    restoreVersion,
  };
}

// =============================================================================
// usePresence Hook
// =============================================================================

export function usePresence(options: UsePresenceOptions): UsePresenceReturn {
  const { manager, userId, userName, userColor } = options;

  const [users, setUsers] = useState<PresenceUser[]>([]);
  const [status, setStatusState] = useState<PresenceState['status']>('online');

  const presenceRef = useRef<PresenceManager | null>(null);

  // Initialize presence manager
  useEffect(() => {
    if (!manager) return;

    const presence = new PresenceManager(manager, userId, userName, userColor);
    presenceRef.current = presence;

    // Set up listeners
    const unsubChange = presence.on('presence-change', ({ users }) => {
      if (users) setUsers(users);
    });

    return () => {
      unsubChange();
      presence.destroy();
      presenceRef.current = null;
    };
  }, [manager, userId, userName, userColor]);

  const onlineUsers = useMemo(() => {
    return users.filter(
      (user) =>
        user.presence.status === 'online' || user.presence.status === 'busy'
    );
  }, [users]);

  const setStatus = useCallback((newStatus: PresenceState['status']) => {
    setStatusState(newStatus);
    presenceRef.current?.setStatus(newStatus);
  }, []);

  const setActivity = useCallback((activity: string) => {
    presenceRef.current?.setActivity(activity);
  }, []);

  const setLocation = useCallback((location: PresenceLocation) => {
    presenceRef.current?.setLocation(location);
  }, []);

  return {
    users,
    onlineUsers,
    status,
    setStatus,
    setActivity,
    setLocation,
    getStatusColor: getPresenceStatusColor,
    getStatusLabel: getPresenceStatusLabel,
    formatLocation: formatPresenceLocation,
  };
}

// =============================================================================
// useCollaborationCursors Hook (for canvas overlay)
// =============================================================================

export interface CollaborationCursor {
  id: string;
  name: string;
  color: string;
  x: number;
  y: number;
  visible: boolean;
}

export function useCollaborationCursors(cursors: UserCursor[]): CollaborationCursor[] {
  const [visibleCursors, setVisibleCursors] = useState<CollaborationCursor[]>([]);

  useEffect(() => {
    const CURSOR_TIMEOUT = 5000; // Hide cursor after 5 seconds of inactivity
    const now = Date.now();

    const visible = cursors
      .filter((cursor) => now - cursor.timestamp < CURSOR_TIMEOUT)
      .map((cursor) => ({
        id: cursor.userId,
        name: cursor.userName,
        color: cursor.userColor,
        x: cursor.position.x,
        y: cursor.position.y,
        visible: true,
      }));

    setVisibleCursors(visible);
  }, [cursors]);

  return visibleCursors;
}

export default {
  useCollaboration,
  useCanvasCollaboration,
  useDocumentCollaboration,
  usePresence,
  useCollaborationCursors,
};
