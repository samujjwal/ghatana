/**
 * useCanvasManagers - Canvas Manager Initialization Hook
 *
 * Instantiates and synchronises all canvas manager objects (zoom, hierarchy,
 * resize, layer, group, connection, alignment, guides, drawing, export, mindmap).
 *
 * @doc.type hook
 * @doc.purpose Centralised canvas manager instantiation
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback, useEffect, useMemo } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import { canvasAtom } from '../state/atoms/unifiedCanvasAtom';
import { ZoomManager } from '../lib/canvas/ZoomManager';
import {
  HierarchyManager,
  type HierarchicalNode,
} from '../lib/canvas/HierarchyManager';
import {
  NodeResizeManager,
  LayerManager,
  GroupManager,
  ConnectionManager,
} from '../lib/canvas/NodeManipulation';
import {
  AlignmentEngine,
  SmartGuidesSystem,
} from '../lib/canvas/AlignmentEngine';
import { DrawingManager } from '../lib/canvas/DrawingManager';
import { CanvasExporter } from '../lib/canvas/CanvasExporter';
import { MindMapEngine } from '../lib/canvas/MindMapEngine';
import {
  undoCommandAtom,
  redoCommandAtom,
  canUndoCommandAtom,
  canRedoCommandAtom,
} from '../components/canvas/workspace/canvasCommands';

export interface UseCanvasManagersReturn {
  zoomManager: ZoomManager;
  hierarchyManager: HierarchyManager;
  resizeManager: NodeResizeManager;
  layerManager: LayerManager;
  groupManager: GroupManager;
  connectionManager: ConnectionManager;
  alignmentEngine: AlignmentEngine;
  smartGuides: SmartGuidesSystem;
  drawingManager: DrawingManager;
  canvasExporter: CanvasExporter;
  mindMapEngine: MindMapEngine;
  canUndo: boolean;
  canRedo: boolean;
  dispatchUndo: () => void;
  dispatchRedo: () => void;
  updateNodeCallback: (
    nodeId: string,
    updates: Partial<HierarchicalNode>
  ) => void;
  updateViewportCallback: (
    updates: Partial<{ x: number; y: number; zoom: number }>
  ) => void;
  getNode: (id: string) => HierarchicalNode | undefined;
}

export function useCanvasManagers(): UseCanvasManagersReturn {
  const [canvasState, setCanvasState] = useAtom(canvasAtom);
  const nodes = canvasState.nodes || [];
  const viewport = canvasState.viewport || { x: 0, y: 0, zoom: 0.5 };

  const updateNodeCallback = useCallback(
    (nodeId: string, updates: Partial<HierarchicalNode>) => {
      setCanvasState((prev) => ({
        ...prev,
        nodes: prev.nodes.map((node) =>
          node.id === nodeId ? { ...node, ...updates } : node
        ),
      }));
    },
    [setCanvasState]
  );

  const updateViewportCallback = useCallback(
    (updates: Partial<typeof viewport>) => {
      setCanvasState((prev) => ({
        ...prev,
        viewport: { ...prev.viewport, ...updates },
      }));
    },
    [setCanvasState]
  );

  const getNode = useCallback(
    (id: string) => nodes.find((n) => n.id === id),
    [nodes]
  );

  const zoomManager = useMemo(
    () => new ZoomManager(getNode, updateViewportCallback),
    [getNode, updateViewportCallback]
  );

  const hierarchyManager = useMemo(
    () =>
      new HierarchyManager(updateNodeCallback, (updatedNodes) => {
        setCanvasState((prev) => ({ ...prev, nodes: updatedNodes }));
      }),
    [updateNodeCallback, setCanvasState]
  );

  useEffect(() => {
    hierarchyManager.setNodes(nodes);
  }, [nodes, hierarchyManager]);

  const resizeManager = useMemo(
    () =>
      new NodeResizeManager(getNode, updateNodeCallback, (nodeId) =>
        hierarchyManager.layoutChildren(nodeId)
      ),
    [getNode, updateNodeCallback, hierarchyManager]
  );

  const layerManager = useMemo(
    () => new LayerManager(updateNodeCallback),
    [updateNodeCallback]
  );

  const groupManager = useMemo(
    () =>
      new GroupManager(getNode, updateNodeCallback, (parentId, child) =>
        hierarchyManager.addChild(parentId, child)
      ),
    [getNode, updateNodeCallback, hierarchyManager]
  );

  const connectionManager = useMemo(() => new ConnectionManager(), []);

  const alignmentEngine = useMemo(
    () => new AlignmentEngine(getNode, updateNodeCallback, (_nodeIds) => {}),
    [getNode, updateNodeCallback]
  );

  const smartGuides = useMemo(
    () => new SmartGuidesSystem(() => nodes),
    [nodes]
  );

  const drawingManager = useMemo(() => new DrawingManager(), []);
  const canvasExporter = useMemo(() => new CanvasExporter(), []);

  const mindMapEngine = useMemo(
    () =>
      new MindMapEngine({
        type: 'tree',
        direction: 'horizontal',
        levelSpacing: 200,
        siblingSpacing: 100,
      }),
    []
  );

  // Sync zoom manager viewport changes back to atom
  useEffect(() => {
    const unsubscribe = zoomManager.onViewportChange((newViewport) => {
      setCanvasState((prev) => ({ ...prev, viewport: newViewport }));
    });
    return unsubscribe;
  }, [zoomManager, setCanvasState]);

  const dispatchUndo = useSetAtom(undoCommandAtom);
  const dispatchRedo = useSetAtom(redoCommandAtom);
  const canUndo = useAtomValue(canUndoCommandAtom);
  const canRedo = useAtomValue(canRedoCommandAtom);

  return {
    zoomManager,
    hierarchyManager,
    resizeManager,
    layerManager,
    groupManager,
    connectionManager,
    alignmentEngine,
    smartGuides,
    drawingManager,
    canvasExporter,
    mindMapEngine,
    canUndo,
    canRedo,
    dispatchUndo,
    dispatchRedo,
    updateNodeCallback,
    updateViewportCallback,
    getNode,
  };
}
