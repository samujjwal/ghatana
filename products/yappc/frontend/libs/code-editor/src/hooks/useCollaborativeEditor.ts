/**
 * useCollaborativeEditor Hook
 * 
 * React hook for integrating collaborative editing with Monaco editors.
 * Provides automatic setup, cleanup, and state management.
 * 
 * Features:
 * - 🎣 Easy React integration with hooks
 * - 🔄 Automatic CRDT binding setup
 * - 👥 Real-time cursor and presence tracking
 * - ⚡ Performance optimizations
 * - 🧼 Automatic cleanup on unmount
 * 
 * @doc.type hook
 * @doc.purpose Collaborative editor React hook
 * @doc.layer product
 * @doc.pattern React Hook
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import * as Y from 'yjs';
import type { editor } from 'monaco-editor';

import { 
  createYjsMonacoBinding,
  type YjsMonacoBinding,
  type YjsMonacoBindingConfig 
} from '../bindings/YjsMonacoBinding';
import { 
  createCollaborativeEditingManager,
  type CollaborativeEditingManager,
  type CollaborativeEditingConfig,
  type UserPresence,
  type CollaborativeCursor,
  type EditConflict,
} from '../managers/CollaborativeEditingManager';

/**
 * Collaborative editor hook configuration
 */
export interface UseCollaborativeEditorConfig {
  /** Local user ID */
  userId: string;
  /** Local user name */
  userName: string;
  /** File ID for this editor instance */
  fileId: string;
  /** Yjs document */
  ydoc: Y.Doc;
  /** Yjs binding configuration */
  bindingConfig?: Partial<YjsMonacoBindingConfig>;
  /** Collaborative editing configuration */
  collaborationConfig?: Partial<CollaborativeEditingConfig>;
}

/**
 * Collaborative editor hook state
 */
export interface CollaborativeEditorState {
  /** Is editor ready for collaboration */
  isReady: boolean;
  /** Active users in the session */
  activeUsers: UserPresence[];
  /** Collaborative cursors in this file */
  cursors: CollaborativeCursor[];
  /** Active conflicts */
  conflicts: EditConflict[];
  /** Binding metrics */
  metrics?: {
    syncOperations: number;
    conflicts: number;
    averageSyncTime: number;
    textLength: number;
    lastSync: number;
  };
  /** Is user actively editing */
  isActive: boolean;
}

/**
 * Collaborative editor hook actions
 */
export interface CollaborativeEditorActions {
  /** Set user active status */
  setActive: (active: boolean) => void;
  /** Force sync to Yjs */
  syncToYjs: () => void;
  /** Force sync from Yjs */
  syncFromYjs: () => void;
  /** Resolve conflict */
  resolveConflict: (conflictId: string) => void;
  /** Get binding instance */
  getBinding: () => YjsMonacoBinding | null;
  /** Get collaboration manager */
  getManager: () => CollaborativeEditingManager | null;
}

/**
 * Collaborative editor hook return value
 */
export interface UseCollaborativeEditorReturn extends 
  CollaborativeEditorState,
  CollaborativeEditorActions {
  /** Setup collaborative editing for editor */
  setupEditor: (editor: editor.IStandaloneCodeEditor, monaco: typeof import('monaco-editor')) => void;
  /** Cleanup collaborative editing */
  cleanup: () => void;
}

/**
 * React hook for collaborative editing
 */
export function useCollaborativeEditor(
  config: UseCollaborativeEditorConfig
): UseCollaborativeEditorReturn {
  const {
    userId,
    userName,
    fileId,
    ydoc,
    bindingConfig,
    collaborationConfig,
  } = config;

  // State
  const [state, setState] = useState<CollaborativeEditorState>({
    isReady: false,
    activeUsers: [],
    cursors: [],
    conflicts: [],
    isActive: false,
  });

  // Refs for managers and bindings
  const bindingRef = useRef<YjsMonacoBinding | null>(null);
  const managerRef = useRef<CollaborativeEditingManager | null>(null);
  const editorRef = useRef<editor.IStandaloneCodeEditor | null>(null);

  // Setup collaborative editing manager
  useEffect(() => {
    const manager = createCollaborativeEditingManager(
      ydoc,
      userId,
      userName,
      collaborationConfig,
      {
        onUserJoined: (user) => {
          setState(prev => ({
            ...prev,
            activeUsers: [...prev.activeUsers.filter(u => u.userId !== user.userId), user],
          }));
        },
        onUserLeft: (userId) => {
          setState(prev => ({
            ...prev,
            activeUsers: prev.activeUsers.filter(u => u.userId !== userId),
            cursors: prev.cursors.filter(c => c.userId !== userId),
          }));
        },
        onCursorUpdated: (cursor) => {
          if (cursor.userId !== userId) {
            setState(prev => ({
              ...prev,
              cursors: [...prev.cursors.filter(c => c.userId !== cursor.userId), cursor],
            }));
          }
        },
        onConflictDetected: (conflict) => {
          setState(prev => ({
            ...prev,
            conflicts: [...prev.conflicts.filter(c => c.id !== conflict.id), conflict],
          }));
        },
        onConflictResolved: (conflictId) => {
          setState(prev => ({
            ...prev,
            conflicts: prev.conflicts.filter(c => c.id !== conflictId),
          }));
        },
      }
    );

    managerRef.current = manager;

    // Set initial active users and cursors
    setState(prev => ({
      ...prev,
      activeUsers: manager.getActiveUsers(),
      cursors: manager.getCursorsForFile(fileId),
      conflicts: manager.getConflicts(),
    }));

    return () => {
      manager.dispose();
    };
  }, [ydoc, userId, userName, fileId, collaborationConfig]);

  // Setup collaborative editing for editor
  const setupEditor = useCallback((
    editor: editor.IStandaloneCodeEditor,
    monaco: typeof import('monaco-editor')
  ) => {
    if (!managerRef.current) return;

    editorRef.current = editor;

    // Get Y.Text for this file
    const ymap = ydoc.getMap('files');
    let ytext = ymap.get(fileId) as Y.Text;
    
    if (!ytext) {
      ytext = new Y.Text();
      ymap.set(fileId, ytext);
    }

    // Create Yjs-Monaco binding
    const binding = createYjsMonacoBinding(
      editor,
      monaco,
      ytext,
      {
        config: bindingConfig,
        callbacks: {
          onMetricsUpdate: (metrics) => {
            setState(prev => ({ ...prev, metrics }));
          },
        },
      }
    );

    bindingRef.current = binding;

    // Add editor to collaborative manager
    managerRef.current.addEditor(fileId, editor);

    // Set as ready
    setState(prev => ({ ...prev, isReady: true }));
  }, [ydoc, fileId, bindingConfig]);

  // Cleanup
  const cleanup = useCallback(() => {
    if (bindingRef.current) {
      bindingRef.current.dispose();
      bindingRef.current = null;
    }

    if (managerRef.current && editorRef.current) {
      managerRef.current.removeEditor(fileId);
    }

    editorRef.current = null;
    setState(prev => ({ ...prev, isReady: false }));
  }, [fileId]);

  // Actions
  const setActive = useCallback((active: boolean) => {
    if (managerRef.current) {
      managerRef.current.setActive(active);
      setState(prev => ({ ...prev, isActive: active }));
    }
  }, []);

  const syncToYjs = useCallback(() => {
    if (bindingRef.current) {
      bindingRef.current.forceSyncToYjs();
    }
  }, []);

  const syncFromYjs = useCallback(() => {
    if (bindingRef.current) {
      bindingRef.current.forceSyncToMonaco();
    }
  }, []);

  const resolveConflict = useCallback((conflictId: string) => {
    if (managerRef.current) {
      managerRef.current.resolveConflict(conflictId);
    }
  }, []);

  const getBinding = useCallback(() => bindingRef.current, []);
  const getManager = useCallback(() => managerRef.current, []);

  // Auto-cleanup on unmount
  useEffect(() => {
    return cleanup;
  }, [cleanup]);

  return {
    ...state,
    setActive,
    syncToYjs,
    syncFromYjs,
    resolveConflict,
    getBinding,
    getManager,
    setupEditor,
    cleanup,
  };
}

/**
 * Enhanced collaborative editor hook with additional features
 */
export function useEnhancedCollaborativeEditor(
  config: UseCollaborativeEditorConfig & {
    /** Enable performance monitoring */
    enablePerformanceMonitoring?: boolean;
    /** Enable auto-save */
    enableAutoSave?: boolean;
    /** Auto-save interval (ms) */
    autoSaveInterval?: number;
  }
): UseCollaborativeEditorReturn & {
  /** Performance metrics */
  performanceMetrics?: {
    renderTime: number;
    syncTime: number;
    memoryUsage: number;
  };
  /** Save status */
  saveStatus: 'saved' | 'saving' | 'error';
  /** Manual save */
  save: () => Promise<void>;
} {
  const {
    enablePerformanceMonitoring = false,
    enableAutoSave = false,
    autoSaveInterval = 30000,
    ...baseConfig
  } = config;

  const baseHook = useCollaborativeEditor(baseConfig);
  const [performanceMetrics, setPerformanceMetrics] = useState<{
    renderTime: number;
    syncTime: number;
    memoryUsage: number;
  } | undefined>();
  const [saveStatus, setSaveStatus] = useState<'saved' | 'saving' | 'error'>('saved');

  // Performance monitoring
  useEffect(() => {
    if (!enablePerformanceMonitoring || !baseHook.isReady) return;

    const updateMetrics = () => {
      const binding = baseHook.getBinding();
      if (binding) {
        const metrics = binding.getMetrics();
        setPerformanceMetrics({
          renderTime: metrics.averageSyncTime,
          syncTime: metrics.averageSyncTime,
          memoryUsage: (performance as unknown as { memory?: { usedJSHeapSize: number } }).memory?.usedJSHeapSize || 0,
        });
      }
    };

    const interval = setInterval(updateMetrics, 5000);
    return () => clearInterval(interval);
  }, [enablePerformanceMonitoring, baseHook.isReady, baseHook.getBinding]);

  // Auto-save functionality
  useEffect(() => {
    if (!enableAutoSave || !baseHook.isReady) return;

    const interval = setInterval(async () => {
      try {
        setSaveStatus('saving');
        baseHook.syncToYjs();
        setSaveStatus('saved');
      } catch (error) {
        setSaveStatus('error');
        console.error('Auto-save failed:', error);
      }
    }, autoSaveInterval);

    return () => clearInterval(interval);
  }, [enableAutoSave, autoSaveInterval, baseHook.isReady, baseHook.syncToYjs]);

  // Manual save
  const save = useCallback(async () => {
    try {
      setSaveStatus('saving');
      baseHook.syncToYjs();
      setSaveStatus('saved');
    } catch (error) {
      setSaveStatus('error');
      console.error('Save failed:', error);
    }
  }, [baseHook.syncToYjs]);

  return {
    ...baseHook,
    performanceMetrics,
    saveStatus,
    save,
  };
}
