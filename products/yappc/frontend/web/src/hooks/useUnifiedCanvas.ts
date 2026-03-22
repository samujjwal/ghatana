/**
 * useUnifiedCanvas - Main Canvas Hook
 *
 * Thin orchestrator: delegates manager lifecycle to useCanvasManagers,
 * node/selection/structural operations to useCanvasNodeOps, and drawing
 * operations to useCanvasDrawing. Provides a single unified API surface
 * to consumers.
 *
 * @doc.type hook
 * @doc.purpose Central canvas management hook — orchestrates sub-hooks
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback, useMemo } from 'react';
import { useAtom, useAtomValue } from 'jotai';
import {
  canvasAtom,
  visibleNodesAtom,
  uiAtom,
  activeToolAtom,
  type Tool,
} from '../state/atoms/unifiedCanvasAtom';
import { type ZoomManager } from '../lib/canvas/ZoomManager';
import { type HierarchicalNode } from '../lib/canvas/HierarchyManager';
import {
  type NodeResizeManager,
  type LayerManager,
  type GroupManager,
  type ConnectionManager,
  type Connection,
} from '../lib/canvas/NodeManipulation';
import {
  type AlignmentEngine,
  type SmartGuidesSystem,
  type AlignmentType,
  type DistributionAxis,
} from '../lib/canvas/AlignmentEngine';
import {
  type DrawingManager,
  type DrawingStroke,
  type Point,
} from '../lib/canvas/DrawingManager';
import {
  SpatialIndex,
  getVisibleNodes,
  type Viewport,
} from '../lib/canvas/ViewportCulling';
import { type CanvasExporter } from '../lib/canvas/CanvasExporter';
import { type MindMapEngine } from '../lib/canvas/MindMapEngine';
import { useCanvasManagers } from './useCanvasManagers';
import { useCanvasNodeOps } from './useCanvasNodeOps';
import { useCanvasDrawing } from './useCanvasDrawing';

export interface UseUnifiedCanvasReturn {
  // State
  nodes: HierarchicalNode[];
  visibleNodes: HierarchicalNode[];
  connections: Connection[];
  drawings: DrawingStroke[];
  selectedNodeIds: string[];
  viewport: { x: number; y: number; zoom: number };
  activeTool: Tool;

  // Managers
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

  // Node operations
  addNode: (node: Partial<HierarchicalNode>) => HierarchicalNode;
  updateNode: (nodeId: string, updates: Partial<HierarchicalNode>) => void;
  removeNode: (nodeId: string) => void;
  duplicateNode: (nodeId: string) => HierarchicalNode;

  // Selection
  selectNode: (nodeId: string, addToSelection?: boolean) => void;
  selectNodes: (nodeIds: string[]) => void;
  deselectAll: () => void;

  // Hierarchy
  addChildToNode: (
    parentId: string,
    child: Partial<HierarchicalNode>
  ) => HierarchicalNode;
  reparentNode: (nodeId: string, newParentId: string) => void;
  zoomIntoNode: (nodeId: string) => Promise<void>;
  zoomOutToParent: () => Promise<void>;

  // Connections
  createConnection: (source: string, target: string) => Connection;
  removeConnection: (connectionId: string) => void;

  // Groups
  createGroup: (nodeIds: string[], name?: string) => void;
  ungroup: (groupId: string) => void;

  // Alignment
  alignNodes: (alignment: AlignmentType) => void;
  distributeNodes: (axis: DistributionAxis) => void;

  // Layers
  bringForward: (nodeId: string) => void;
  sendBackward: (nodeId: string) => void;
  bringToFront: (nodeId: string) => void;
  sendToBack: (nodeId: string) => void;

  // Zoom
  zoomIn: () => Promise<void>;
  zoomOut: () => Promise<void>;
  resetZoom: () => Promise<void>;
  zoomToFit: () => Promise<void>;

  // Tools
  setActiveTool: (tool: Tool) => void;

  // Viewport
  updateViewport: (
    viewportUpdate: Partial<{ x: number; y: number; zoom: number }>
  ) => void;

  // Drawing operations
  startDrawing: (
    point: Point,
    tool?: 'pen' | 'pencil' | 'marker' | 'highlighter'
  ) => void;
  continueDrawing: (point: Point) => void;
  endDrawing: () => DrawingStroke | null;
  clearDrawings: () => void;
  createStickyNote: (
    position: { x: number; y: number },
    color?: 'yellow' | 'pink' | 'blue' | 'green' | 'purple'
  ) => HierarchicalNode;

  // History
  undo: () => boolean;
  redo: () => boolean;
  canUndo: boolean;
  canRedo: boolean;

  // Export/Import
  exportToJSON: () => string;
  exportToSVG: () => string;
  exportToPNG: (element: HTMLElement) => Promise<string>;
  importFromJSON: (json: string) => void;
  downloadJSON: (filename?: string) => void;
  downloadSVG: (filename?: string) => void;

  // Mind Map Layout
  applyMindMapLayout: (layoutType?: 'tree' | 'radial' | 'fishbone') => void;
}

export function useUnifiedCanvas(projectId?: string): UseUnifiedCanvasReturn {
  // Atom state
  const [canvasState, setCanvasState] = useAtom(canvasAtom);
  useAtomValue(visibleNodesAtom);
  useAtom(uiAtom);
  const [activeTool, setActiveTool] = useAtom(activeToolAtom);

  const nodes = canvasState.nodes || [];
  const connections = canvasState.connections || [];
  const selectedNodeIds = canvasState.selectedNodeIds || [];
  const viewport = canvasState.viewport || { x: 0, y: 0, zoom: 0.5 };

  // Viewport culling for large canvases
  const spatialIndex = useMemo(() => new SpatialIndex(), []);
  const culledVisibleNodes = useMemo(() => {
    console.log('[useUnifiedCanvas] Culling nodes:', nodes.length, 'total, viewport:', viewport);
    if (nodes.length < 50) {
      console.log('[useUnifiedCanvas] Skipping culling (< 50 nodes), returning all nodes');
      return nodes;
    }
    const viewportBounds: Viewport = {
      x: viewport.x,
      y: viewport.y,
      zoom: viewport.zoom,
      width: typeof window !== 'undefined' ? window.innerWidth : 1920,
      height: typeof window !== 'undefined' ? window.innerHeight : 1080,
    };
    const visible = getVisibleNodes(nodes, viewportBounds);
    console.log('[useUnifiedCanvas] After culling:', visible.length, 'visible nodes');
    return visible;
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [nodes, viewport, spatialIndex]);

  // Sub-hooks
  const managers = useCanvasManagers();
  const nodeOps = useCanvasNodeOps(managers, selectedNodeIds);
  const drawingOps = useCanvasDrawing(managers.drawingManager, nodeOps.addNode);

  // History operations — delegate to command-pattern atoms
  const undo = useCallback(() => { managers.dispatchUndo(); return true; }, [managers]);
  const redo = useCallback(() => { managers.dispatchRedo(); return true; }, [managers]);

  // Export/Import operations
  const { canvasExporter, mindMapEngine, updateNodeCallback } = managers;

  return {
    // State
    nodes,
    visibleNodes: culledVisibleNodes,
    connections,
    drawings: canvasState.drawings || [],
    selectedNodeIds,
    viewport,
    activeTool,

    // Managers (exposed for direct use by consumers)
    zoomManager: managers.zoomManager,
    hierarchyManager: managers.hierarchyManager,
    resizeManager: managers.resizeManager,
    layerManager: managers.layerManager,
    groupManager: managers.groupManager,
    connectionManager: managers.connectionManager,
    alignmentEngine: managers.alignmentEngine,
    smartGuides: managers.smartGuides,
    drawingManager: managers.drawingManager,
    canvasExporter,
    mindMapEngine,

    // Node operations
    ...nodeOps,

    // Tools
    setActiveTool,

    // Drawing operations
    ...drawingOps,

    // History
    undo,
    redo,
    canUndo: managers.canUndo,
    canRedo: managers.canRedo,

    // Export/Import
    exportToJSON: useCallback(
      () => canvasExporter.exportToJSON(nodes, connections, canvasState.drawings || [], viewport, projectId),
      [canvasExporter, nodes, connections, canvasState.drawings, viewport, projectId]
    ),
    exportToSVG: useCallback(
      () => canvasExporter.exportToSVG(nodes, connections, canvasState.drawings || []),
      [canvasExporter, nodes, connections, canvasState.drawings]
    ),
    exportToPNG: useCallback(
      async (element: HTMLElement) => canvasExporter.exportToPNG(element),
      [canvasExporter]
    ),
    importFromJSON: useCallback(
      (json: string) => {
        const data = canvasExporter.importFromJSON(json);
        setCanvasState((prev) => ({
          ...prev,
          nodes: data.nodes,
          connections: data.connections,
          drawings: data.drawings,
          viewport: data.viewport,
        }));
      },
      [canvasExporter, setCanvasState]
    ),
    downloadJSON: useCallback(
      (filename?: string) => {
        const json = canvasExporter.exportToJSON(nodes, connections, canvasState.drawings || [], viewport, projectId);
        canvasExporter.downloadFile(json, filename || `canvas-${Date.now()}.json`, 'application/json');
      },
      [canvasExporter, nodes, connections, canvasState.drawings, viewport, projectId]
    ),
    downloadSVG: useCallback(
      (filename?: string) => {
        const svg = canvasExporter.exportToSVG(nodes, connections, canvasState.drawings || []);
        canvasExporter.downloadFile(svg, filename || `canvas-${Date.now()}.svg`, 'image/svg+xml');
      },
      [canvasExporter, nodes, connections, canvasState.drawings]
    ),

    // Mind Map Layout
    applyMindMapLayout: useCallback(
      (layoutType: 'tree' | 'radial' | 'fishbone' = 'tree') => {
        mindMapEngine.updateConfig({ type: layoutType });
        const mindMapNodes = nodes.filter((n) => n.type === 'mindmap' || n.data.isMindMap);
        if (mindMapNodes.length === 0) {
          console.warn('[Canvas] No mind map nodes to layout');
          return;
        }
        const layoutedNodes = mindMapEngine.layoutNodes(mindMapNodes as unknown);
        layoutedNodes.forEach((layouted) => {
          updateNodeCallback(layouted.id, { position: layouted.position });
        });
      },
      [mindMapEngine, nodes, updateNodeCallback]
    ),
  };
}
