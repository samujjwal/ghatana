/**
 * Consolidated Canvas Core Hook
 *
 * Provides core canvas state (nodes, edges, viewport), authentication-aware
 * save with JWT Bearer token, auto-save, and proper dirty tracking.
 *
 * Auth is injected via `getToken` in options so this library hook never
 * imports directly from an application layer.
 *
 * @doc.type hook
 * @doc.purpose Consolidated core canvas operations
 * @doc.layer presentation
 */

import { useCallback, useEffect, useMemo, useRef } from 'react';
import { atom, useAtom } from 'jotai';
import type { Node, Edge, Viewport } from '@xyflow/react';

// Module-level atoms — one store per application, shared across hook instances
const _nodesAtom = atom<Node[]>([]);
const _edgesAtom = atom<Edge[]>([]);
const _cameraAtom = atom<Viewport>({ x: 0, y: 0, zoom: 1 });

export interface UseCanvasCoreOptions {
  canvasId: string;
  tenantId: string;
  userId: string;
  mode?: 'view' | 'edit' | 'fullstack';
  enableAutoSave?: boolean;
  /** Returns the current Bearer token, or null when unauthenticated */
  getToken?: () => string | null;
  /** Whether the current user is authenticated */
  isAuthenticated?: boolean;
  /** Permissions the current user holds (e.g. ['canvas:write', 'canvas:delete']) */
  userPermissions?: string[];
}

export interface Permission {
  resource: string;
  action: 'read' | 'write' | 'delete' | 'admin';
}

export interface UseCanvasCoreReturn {
  // Node operations
  nodes: Node[];
  addNode: (node: Omit<Node, 'id'>) => void;
  updateNode: (id: string, data: Partial<Node>) => void;
  deleteNode: (id: string) => void;
  selectNode: (id: string) => void;
  deselectAll: () => void;
  
  // Edge operations
  edges: Edge[];
  addEdge: (edge: Omit<Edge, 'id'>) => void;
  updateEdge: (id: string, data: Partial<Edge>) => void;
  deleteEdge: (id: string) => void;
  
  // Viewport operations
  viewport: Viewport;
  setViewport: (viewport: Viewport) => void;
  zoomIn: () => void;
  zoomOut: () => void;
  fitView: () => void;
  centerNode: (nodeId: string) => void;
  
  // Selection
  selectedNodes: Node[];
  selectedEdges: Edge[];
  
  // Canvas state
  canvasId: string;
  mode: 'view' | 'edit' | 'fullstack';
  isDirty: boolean;
  
  // Auth & permissions
  isAuthenticated: boolean;
  permissions: Permission[];
  canEdit: boolean;
  canDelete: boolean;
  
  // Actions
  save: () => Promise<void>;
  undo: () => void;
  redo: () => void;
  clear: () => void;
}

export function useCanvasCore(options: UseCanvasCoreOptions): UseCanvasCoreReturn {
  const {
    canvasId,
    tenantId,
    mode = 'edit',
    enableAutoSave = true,
    getToken,
    isAuthenticated = false,
    userPermissions = [],
  } = options;

  // State atoms — use module-level atoms shared per Jotai store
  const [nodes, setNodes] = useAtom(_nodesAtom);
  const [edges, setEdges] = useAtom(_edgesAtom);
  const [viewport, setViewport] = useAtom(_cameraAtom);

  // Node operations
  const addNode = useCallback((node: Omit<Node, 'id'>) => {
    const newNode: Node = {
      ...node,
      id: `node-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    };
    setNodes((prev: Node[]) => [...prev, newNode]);
  }, [setNodes]);

  const updateNode = useCallback((id: string, data: Partial<Node>) => {
    setNodes((prev: Node[]) =>
      prev.map((n: Node) => (n.id === id ? { ...n, ...data } : n))
    );
  }, [setNodes]);

  const deleteNode = useCallback((id: string) => {
    setNodes((prev: Node[]) => prev.filter((n: Node) => n.id !== id));
    setEdges((prev: Edge[]) => prev.filter((e: Edge) => e.source !== id && e.target !== id));
  }, [setNodes, setEdges]);

  const selectNode = useCallback((id: string) => {
    setNodes((prev: Node[]) =>
      prev.map((n: Node) => ({ ...n, selected: n.id === id }))
    );
  }, [setNodes]);

  const deselectAll = useCallback(() => {
    setNodes((prev: Node[]) => prev.map((n: Node) => ({ ...n, selected: false })));
    setEdges((prev: Edge[]) => prev.map((e: Edge) => ({ ...e, selected: false })));
  }, [setNodes, setEdges]);

  // Edge operations
  const addEdge = useCallback((edge: Omit<Edge, 'id'>) => {
    const newEdge: Edge = {
      ...edge,
      id: `edge-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`,
    };
    setEdges((prev: Edge[]) => [...prev, newEdge]);
  }, [setEdges]);

  const updateEdge = useCallback((id: string, data: Partial<Edge>) => {
    setEdges((prev: Edge[]) =>
      prev.map((e: Edge) => (e.id === id ? { ...e, ...data } : e))
    );
  }, [setEdges]);

  const deleteEdge = useCallback((id: string) => {
    setEdges((prev: Edge[]) => prev.filter((e: Edge) => e.id !== id));
  }, [setEdges]);

  // Viewport operations
  const zoomIn = useCallback(() => {
    setViewport((prev: Viewport) => ({ ...prev, zoom: Math.min(prev.zoom * 1.2, 4) }));
  }, [setViewport]);

  const zoomOut = useCallback(() => {
    setViewport((prev: Viewport) => ({ ...prev, zoom: Math.max(prev.zoom / 1.2, 0.1) }));
  }, [setViewport]);

  const fitView = useCallback(() => {
    if (nodes.length === 0) return;
    const bounds = nodes.reduce(
      (acc: { minX: number; minY: number; maxX: number; maxY: number }, n: Node) => ({
        minX: Math.min(acc.minX, n.position.x),
        minY: Math.min(acc.minY, n.position.y),
        maxX: Math.max(acc.maxX, n.position.x + (n.width ?? 200)),
        maxY: Math.max(acc.maxY, n.position.y + (n.height ?? 100)),
      }),
      { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
    );
    const w = bounds.maxX - bounds.minX;
    const h = bounds.maxY - bounds.minY;
    setViewport({
      x: -(bounds.minX + w / 2),
      y: -(bounds.minY + h / 2),
      zoom: Math.min(window.innerWidth / w, window.innerHeight / h) * 0.8,
    });
  }, [nodes, setViewport]);

  const centerNode = useCallback((nodeId: string) => {
    const n = nodes.find((node: Node) => node.id === nodeId);
    if (!n) return;
    setViewport({
      x: -n.position.x + window.innerWidth / 2,
      y: -n.position.y + window.innerHeight / 2,
      zoom: viewport.zoom,
    });
  }, [nodes, viewport.zoom, setViewport]);

  // Selection
  const selectedNodes = useMemo(() => nodes.filter((n: Node) => n.selected), [nodes]);
  const selectedEdges = useMemo(() => edges.filter((e: Edge) => e.selected), [edges]);

  // Dirty tracking against last-saved snapshot
  const savedSnapshotRef = useRef<{ nodes: Node[]; edges: Edge[] } | null>(null);
  const isDirty = useMemo(() => {
    const snap = savedSnapshotRef.current;
    if (!snap) return nodes.length > 0 || edges.length > 0;
    if (snap.nodes.length !== nodes.length || snap.edges.length !== edges.length) return true;
    return (
      snap.nodes.some((s: Node, i: number) =>
        s.id !== nodes[i]?.id ||
        s.position.x !== nodes[i]?.position.x ||
        s.position.y !== nodes[i]?.position.y
      ) ||
      snap.edges.some((s: Edge, i: number) => s.id !== edges[i]?.id)
    );
  }, [nodes, edges]);

  // Auth & permissions — derived from injected options
  const permissions: Permission[] = useMemo((): Permission[] => {
    if (!isAuthenticated) return [];
    const hasAll = userPermissions.includes('*');
    const base: Permission[] = [{ resource: 'canvas', action: 'read' }];
    if (hasAll || userPermissions.includes('canvas:write')) {
      base.push({ resource: 'canvas', action: 'write' });
    }
    if (hasAll || userPermissions.includes('canvas:delete')) {
      base.push({ resource: 'canvas', action: 'delete' });
    }
    return base;
  }, [isAuthenticated, userPermissions]);

  const canEdit = (mode === 'edit' || mode === 'fullstack') && permissions.some((p) => p.action === 'write');
  const canDelete = permissions.some((p) => p.action === 'delete');

  // Save — attaches JWT Bearer token when available
  const save = useCallback(async () => {
    const token = getToken?.() ?? null;
    const headers: Record<string, string> = { 'Content-Type': 'application/json', 'X-Tenant-ID': tenantId };
    if (token) headers['Authorization'] = `Bearer ${token}`;

    const response = await fetch(`/api/canvas/${canvasId}`, {
      method: 'PUT',
      headers,
      body: JSON.stringify({ nodes, edges, viewport }),
    });
    if (!response.ok) throw new Error(`Failed to save canvas: ${response.statusText}`);

    savedSnapshotRef.current = { nodes: [...nodes], edges: [...edges] };
  }, [canvasId, tenantId, getToken, nodes, edges, viewport]);

  const undo = useCallback(() => {
    // Undo/redo delegates to canvasCommands.ts in CanvasWorkspace.
    // Outside CanvasWorkspace use undoCommandAtom directly.
    console.warn('[useCanvasCore] undo — wire undoCommandAtom from canvasCommands.ts');
  }, []);

  const redo = useCallback(() => {
    console.warn('[useCanvasCore] redo — wire redoCommandAtom from canvasCommands.ts');
  }, []);

  const clear = useCallback(() => {
    setNodes([]);
    setEdges([]);
    setViewport({ x: 0, y: 0, zoom: 1 });
  }, [setNodes, setEdges, setViewport]);

  // Auto-save: debounced 5 s after any dirty change, only when authenticated
  useEffect(() => {
    if (!enableAutoSave || !isDirty || !isAuthenticated) return;
    const timer = setTimeout(() => {
      save().catch((err: unknown) => console.error('[useCanvasCore] auto-save failed:', err));
    }, 5000);
    return () => clearTimeout(timer);
  }, [enableAutoSave, isDirty, isAuthenticated, save]);

  return {
    nodes,
    addNode,
    updateNode,
    deleteNode,
    selectNode,
    deselectAll,
    edges,
    addEdge,
    updateEdge,
    deleteEdge,
    viewport,
    setViewport,
    zoomIn,
    zoomOut,
    fitView,
    centerNode,
    selectedNodes,
    selectedEdges,
    canvasId,
    mode,
    isDirty,
    isAuthenticated,
    permissions,
    canEdit,
    canDelete,
    save,
    undo,
    redo,
    clear,
  };
}
