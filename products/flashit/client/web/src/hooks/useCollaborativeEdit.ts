/**
 * Collaborative Editing Hook for Flashit Web
 * Real-time collaborative editing with CRDT
 *
 * @doc.type hook
 * @doc.purpose React hook for collaborative editing
 * @doc.layer product
 * @doc.pattern CustomHook
 */

import { useState, useEffect, useCallback, useRef } from 'react';
import { createCRDTEngine, Operation, CRDTState } from '../services/crdtEngine';
import { io, Socket } from 'socket.io-client';

// ============================================================================
// Types & Interfaces
// ============================================================================

export interface CollaborativeEditConfig {
  documentId: string;
  userId: string;
  websocketUrl?: string;
  autoSave?: boolean;
  autoSaveDelay?: number;
}

export interface Collaborator {
  id: string;
  name: string;
  color: string;
  cursor?: number;
  selection?: { start: number; end: number };
  lastActive: Date;
}

export interface UseCollaborativeEditReturn {
  text: string;
  collaborators: Collaborator[];
  isConnected: boolean;
  isSyncing: boolean;
  insert: (index: number, value: string) => void;
  delete: (index: number, length?: number) => void;
  setText: (text: string) => void;
  updateCursor: (position: number) => void;
  updateSelection: (start: number, end: number) => void;
  undo: () => void;
  redo: () => void;
  saveDocument: () => Promise<void>;
  disconnect: () => void;
}

// ============================================================================
// Utilities
// ============================================================================

const COLORS = [
  '#3b82f6', '#ef4444', '#10b981', '#f59e0b', '#8b5cf6',
  '#ec4899', '#06b6d4', '#84cc16', '#f97316', '#6366f1',
];

function generateColor(userId: string): string {
  const hash = userId.split('').reduce((acc, char) => {
    return char.charCodeAt(0) + ((acc << 5) - acc);
  }, 0);
  return COLORS[Math.abs(hash) % COLORS.length];
}

// ============================================================================
// Collaborative Edit Hook
// ============================================================================

/**
 * useCollaborativeEdit hook for real-time collaborative editing
 */
export function useCollaborativeEdit(
  config: CollaborativeEditConfig
): UseCollaborativeEditReturn {
  const {
    documentId,
    userId,
    websocketUrl = process.env.NEXT_PUBLIC_WS_URL || 'ws://localhost:3001',
    autoSave = true,
    autoSaveDelay = 2000,
  } = config;

  // State
  const [text, setText] = useState('');
  const [collaborators, setCollaborators] = useState<Collaborator[]>([]);
  const [isConnected, setIsConnected] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);

  // Refs
  const engineRef = useRef(createCRDTEngine(userId));
  const socketRef = useRef<Socket | null>(null);
  const saveTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const historyRef = useRef<string[]>([]);
  const historyIndexRef = useRef(-1);

  /**
   * Initialize WebSocket connection
   */
  useEffect(() => {
    const socket = io(websocketUrl, {
      query: { documentId, userId },
    });

    socketRef.current = socket;

    // Connection events
    socket.on('connect', () => {
      setIsConnected(true);
      console.log('Connected to collaborative editing server');
    });

    socket.on('disconnect', () => {
      setIsConnected(false);
      console.log('Disconnected from collaborative editing server');
    });

    // Document initialization
    socket.on('document:init', (data: { state: CRDTState; collaborators: Collaborator[] }) => {
      engineRef.current = createCRDTEngine(userId, data.state);
      setText(engineRef.current.getText());
      setCollaborators(data.collaborators);
      setIsSyncing(false);
    });

    // Remote operations
    socket.on('operation:remote', (operation: Operation) => {
      engineRef.current.applyOperation(operation);
      setText(engineRef.current.getText());
    });

    // Collaborator updates
    socket.on('collaborators:update', (updatedCollaborators: Collaborator[]) => {
      setCollaborators(updatedCollaborators);
    });

    socket.on('collaborator:joined', (collaborator: Collaborator) => {
      setCollaborators((prev) => [...prev, collaborator]);
    });

    socket.on('collaborator:left', (collaboratorId: string) => {
      setCollaborators((prev) => prev.filter((c) => c.id !== collaboratorId));
    });

    socket.on('cursor:update', (data: { userId: string; position: number }) => {
      setCollaborators((prev) =>
        prev.map((c) =>
          c.id === data.userId ? { ...c, cursor: data.position, lastActive: new Date() } : c
        )
      );
    });

    socket.on('selection:update', (data: { userId: string; start: number; end: number }) => {
      setCollaborators((prev) =>
        prev.map((c) =>
          c.id === data.userId
            ? { ...c, selection: { start: data.start, end: data.end }, lastActive: new Date() }
            : c
        )
      );
    });

    // Request initial state
    setIsSyncing(true);
    socket.emit('document:join', { documentId, userId, name: 'User', color: generateColor(userId) });

    return () => {
      socket.emit('document:leave', { documentId, userId });
      socket.disconnect();
    };
  }, [documentId, userId, websocketUrl]);

  /**
   * Subscribe to local operations
   */
  useEffect(() => {
    const unsubscribe = engineRef.current.subscribe((operation) => {
      if (socketRef.current?.connected) {
        socketRef.current.emit('operation:local', operation);
      }
    });

    return unsubscribe;
  }, []);

  /**
   * Auto-save functionality
   */
  useEffect(() => {
    if (!autoSave) return;

    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    saveTimeoutRef.current = setTimeout(() => {
      saveDocument();
    }, autoSaveDelay);

    return () => {
      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
    };
  }, [text, autoSave, autoSaveDelay]);

  /**
   * Insert text at position
   */
  const insert = useCallback((index: number, value: string) => {
    for (let i = 0; i < value.length; i++) {
      engineRef.current.insert(index + i, value[i]);
    }
    
    const newText = engineRef.current.getText();
    setText(newText);
    addToHistory(newText);
  }, []);

  /**
   * Delete text at position
   */
  const deleteText = useCallback((index: number, length: number = 1) => {
    for (let i = 0; i < length; i++) {
      engineRef.current.delete(index);
    }
    
    const newText = engineRef.current.getText();
    setText(newText);
    addToHistory(newText);
  }, []);

  /**
   * Set entire text (for paste operations)
   */
  const setTextContent = useCallback((newText: string) => {
    const currentText = engineRef.current.getText();
    
    // Calculate diff and apply operations
    let i = 0;
    const minLength = Math.min(currentText.length, newText.length);
    
    // Find common prefix
    while (i < minLength && currentText[i] === newText[i]) {
      i++;
    }
    
    // Delete from divergence point
    if (i < currentText.length) {
      for (let j = currentText.length - 1; j >= i; j--) {
        engineRef.current.delete(j);
      }
    }
    
    // Insert new characters
    if (i < newText.length) {
      for (let j = i; j < newText.length; j++) {
        engineRef.current.insert(j, newText[j]);
      }
    }
    
    setText(engineRef.current.getText());
    addToHistory(newText);
  }, []);

  /**
   * Update cursor position
   */
  const updateCursor = useCallback((position: number) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('cursor:update', { position });
    }
  }, []);

  /**
   * Update selection
   */
  const updateSelection = useCallback((start: number, end: number) => {
    if (socketRef.current?.connected) {
      socketRef.current.emit('selection:update', { start, end });
    }
  }, []);

  /**
   * Add to history
   */
  const addToHistory = useCallback((newText: string) => {
    // Remove future history if we're not at the end
    if (historyIndexRef.current < historyRef.current.length - 1) {
      historyRef.current = historyRef.current.slice(0, historyIndexRef.current + 1);
    }
    
    historyRef.current.push(newText);
    historyIndexRef.current = historyRef.current.length - 1;
    
    // Keep only last 100 states
    if (historyRef.current.length > 100) {
      historyRef.current.shift();
      historyIndexRef.current--;
    }
  }, []);

  /**
   * Undo
   */
  const undo = useCallback(() => {
    if (historyIndexRef.current > 0) {
      historyIndexRef.current--;
      const previousText = historyRef.current[historyIndexRef.current];
      setTextContent(previousText);
    }
  }, [setTextContent]);

  /**
   * Redo
   */
  const redo = useCallback(() => {
    if (historyIndexRef.current < historyRef.current.length - 1) {
      historyIndexRef.current++;
      const nextText = historyRef.current[historyIndexRef.current];
      setTextContent(nextText);
    }
  }, [setTextContent]);

  /**
   * Save document
   */
  const saveDocument = useCallback(async () => {
    if (!socketRef.current?.connected) return;

    try {
      const state = engineRef.current.getState();
      
      socketRef.current.emit('document:save', {
        documentId,
        state,
        text: engineRef.current.getText(),
      });
    } catch (error) {
      console.error('Failed to save document:', error);
    }
  }, [documentId]);

  /**
   * Disconnect
   */
  const disconnect = useCallback(() => {
    if (socketRef.current) {
      socketRef.current.disconnect();
      socketRef.current = null;
    }
  }, []);

  return {
    text,
    collaborators,
    isConnected,
    isSyncing,
    insert,
    delete: deleteText,
    setText: setTextContent,
    updateCursor,
    updateSelection,
    undo,
    redo,
    saveDocument,
    disconnect,
  };
}

export default useCollaborativeEdit;
