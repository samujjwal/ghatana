/**
 * useUnifiedCanvas - Main Canvas Hook
 *
 * Integrates all canvas managers and provides unified API
 *
 * @doc.type hook
 * @doc.purpose Central canvas management hook
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback, useEffect, useMemo } from 'react';
import { useAtom, useSetAtom, useAtomValue } from 'jotai';
import {
  canvasAtom,
  visibleNodesAtom,
  uiAtom,
  activeToolAtom,
  type Tool,
} from '../state/atoms/unifiedCanvasAtom';
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
  type Connection,
} from '../lib/canvas/NodeManipulation';
import {
  AlignmentEngine,
  SmartGuidesSystem,
  type AlignmentType,
  type DistributionAxis,
} from '../lib/canvas/AlignmentEngine';
import {
  undoCommandAtom,
  redoCommandAtom,
  canUndoCommandAtom,
  canRedoCommandAtom,
} from '../components/canvas/workspace/canvasCommands';
import {
  DrawingManager,
  type DrawingStroke,
  type Point,
} from '../lib/canvas/DrawingManager';
import {
  SpatialIndex,
  getVisibleNodes,
  type Viewport,
} from '../lib/canvas/ViewportCulling';
import { CanvasExporter } from '../lib/canvas/CanvasExporter';
import { MindMapEngine } from '../lib/canvas/MindMapEngine';

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
  // State - use canvasAtom directly for reliability
  const [canvasState, setCanvasState] = useAtom(canvasAtom);
  useAtomValue(visibleNodesAtom);
  useAtom(uiAtom);
  const [activeTool, setActiveTool] = useAtom(activeToolAtom);

  // Extract values from canvas state
  const nodes = canvasState.nodes || [];
  const connections = canvasState.connections || [];
  const selectedNodeIds = canvasState.selectedNodeIds || [];
  const viewport = canvasState.viewport || { x: 0, y: 0, zoom: 0.5 };

  // Initialize spatial index for viewport culling
  const spatialIndex = useMemo(() => new SpatialIndex(), []);

  // Update spatial index when nodes change
  useEffect(() => {
    // Filter nodes with valid positions
    const validNodes = nodes.filter(
      (node) =>
        node.position &&
        typeof node.position.x === 'number' &&
        typeof node.position.y === 'number'
    );
    spatialIndex.indexNodes(validNodes as unknown);
  }, [nodes, spatialIndex]);

  // Compute visible nodes using viewport culling
  const culledVisibleNodes = useMemo(() => {
    console.log(
      '[useUnifiedCanvas] Culling nodes:',
      nodes.length,
      'total, viewport:',
      viewport
    );
    if (nodes.length < 50) {
      console.log(
        '[useUnifiedCanvas] Skipping culling (< 50 nodes), returning all nodes'
      );
      return nodes; // Skip culling for small datasets
    }

    const viewportBounds: Viewport = {
      x: viewport.x,
      y: viewport.y,
      zoom: viewport.zoom,
      width: typeof window !== 'undefined' ? window.innerWidth : 1920,
      height: typeof window !== 'undefined' ? window.innerHeight : 1080,
    };

    const visible = getVisibleNodes(nodes, viewportBounds);
    console.log(
      '[useUnifiedCanvas] After culling:',
      visible.length,
      'visible nodes'
    );
    return visible;
  }, [nodes, viewport]);

  // Node operations callbacks
  const getNode = useCallback(
    (id: string) => {
      return nodes.find((n) => n.id === id);
    },
    [nodes]
  );

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

  // Initialize managers
  const zoomManager = useMemo(
    () => new ZoomManager(getNode, updateViewportCallback),
    [getNode, updateViewportCallback]
  );

  // Initialize manager once, update nodes via effect
  const hierarchyManager = useMemo(() => {
    return new HierarchyManager(updateNodeCallback, (updatedNodes) => {
      setCanvasState((prev) => ({ ...prev, nodes: updatedNodes }));
    });
  }, [updateNodeCallback, setCanvasState]);

  // Sync nodes to manager when they change
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

  const layerManager = useMemo(() => {
    const manager = new LayerManager(updateNodeCallback);
    return manager;
  }, [updateNodeCallback]);

  const groupManager = useMemo(
    () =>
      new GroupManager(getNode, updateNodeCallback, (parentId, child) =>
        hierarchyManager.addChild(parentId, child)
      ),
    [getNode, updateNodeCallback, hierarchyManager]
  );

  const connectionManager = useMemo(() => {
    return new ConnectionManager();
  }, []);

  // Sync connections to manager when they change
  useEffect(() => {
    // Initialize connection manager with existing connections if it has a method for that
    // For now, connections are managed through the manager's create/remove methods
  }, [connections, connectionManager]);

  const alignmentEngine = useMemo(
    () =>
      new AlignmentEngine(getNode, updateNodeCallback, (_nodeIds) => {
        // Notify nodes changed
      }),
    [getNode, updateNodeCallback]
  );

  const smartGuides = useMemo(
    () => new SmartGuidesSystem(() => nodes),
    [nodes]
  );

  // Undo/redo wired to the command-pattern atoms from canvasCommands.ts
  const dispatchUndo = useSetAtom(undoCommandAtom);
  const dispatchRedo = useSetAtom(redoCommandAtom);
  const canUndo = useAtomValue(canUndoCommandAtom);
  const canRedo = useAtomValue(canRedoCommandAtom);

  // Initialize drawing manager
  const drawingManager = useMemo(() => new DrawingManager(), []);

  // Initialize canvas exporter
  const canvasExporter = useMemo(() => new CanvasExporter(), []);

  // Initialize mind map engine
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

  // Sync zoom manager with viewport
  useEffect(() => {
    const unsubscribe = zoomManager.onViewportChange((newViewport) => {
      setCanvasState((prev) => ({
        ...prev,
        viewport: newViewport,
      }));
    });
    return unsubscribe;
  }, [zoomManager, setCanvasState]);

  // Node operations
  const addNode = useCallback(
    (node: Partial<HierarchicalNode>): HierarchicalNode => {
      const newNode: HierarchicalNode = {
        id:
          node.id ||
          `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
        type: node.type || 'default',
        position: node.position || { x: 0, y: 0 },
        size: node.size || { width: 200, height: 150 },
        data: node.data || {},
        parentId: node.parentId,
        children: [],
        childrenVisible: true,
        depth: 0,
        path: [],
        isContainer: node.isContainer ?? false,
        autoResize: node.autoResize ?? false,
        padding: node.padding || { top: 10, right: 10, bottom: 10, left: 10 },
        childLayout: node.childLayout || 'free',
        childSpacing: node.childSpacing ?? 10,
        childAlignment: node.childAlignment || 'start',
      };

      setCanvasState((prev) => ({
        ...prev,
        nodes: [...prev.nodes, newNode],
      }));
      return newNode;
    },
    [setCanvasState]
  );

  const updateNode = useCallback(
    (nodeId: string, updates: Partial<HierarchicalNode>) => {
      updateNodeCallback(nodeId, updates);
    },
    [updateNodeCallback]
  );

  const removeNode = useCallback(
    (nodeId: string) => {
      hierarchyManager.removeNode(nodeId);
      setCanvasState((prev) => ({
        ...prev,
        nodes: hierarchyManager.getAllNodes(),
      }));
    },
    [hierarchyManager, setCanvasState]
  );

  const duplicateNode = useCallback(
    (nodeId: string): HierarchicalNode => {
      const node = getNode(nodeId);
      if (!node) throw new Error('Node not found');

      const duplicate = addNode({
        ...node,
        id: undefined, // Generate new ID
        position: { x: node.position.x + 20, y: node.position.y + 20 },
      });

      return duplicate;
    },
    [getNode, addNode]
  );

  // Selection
  const selectNode = useCallback(
    (nodeId: string, addToSelection = false) => {
      setCanvasState((prev) => ({
        ...prev,
        selectedNodeIds: addToSelection
          ? prev.selectedNodeIds.includes(nodeId)
            ? prev.selectedNodeIds
            : [...prev.selectedNodeIds, nodeId]
          : [nodeId],
      }));
    },
    [setCanvasState]
  );

  const selectNodes = useCallback(
    (nodeIds: string[]) => {
      setCanvasState((prev) => ({
        ...prev,
        selectedNodeIds: nodeIds,
      }));
    },
    [setCanvasState]
  );

  const deselectAll = useCallback(() => {
    setCanvasState((prev) => ({
      ...prev,
      selectedNodeIds: [],
    }));
  }, [setCanvasState]);

  // Hierarchy operations
  const addChildToNode = useCallback(
    (parentId: string, child: Partial<HierarchicalNode>): HierarchicalNode => {
      const newChild = hierarchyManager.addChild(parentId, child);
      setCanvasState((prev) => ({
        ...prev,
        nodes: hierarchyManager.getAllNodes(),
      }));
      return newChild;
    },
    [hierarchyManager, setCanvasState]
  );

  const reparentNode = useCallback(
    (nodeId: string, newParentId: string) => {
      hierarchyManager.reparent(nodeId, newParentId);
      setCanvasState((prev) => ({
        ...prev,
        nodes: hierarchyManager.getAllNodes(),
      }));
    },
    [hierarchyManager, setCanvasState]
  );

  const zoomIntoNode = useCallback(
    async (nodeId: string) => {
      await zoomManager.zoomIntoNode(nodeId);
      // Update hierarchy trail
      setCanvasState((prev) => ({
        ...prev,
        hierarchyTrail: [...((prev as unknown).hierarchyTrail || []), nodeId],
      }));
    },
    [zoomManager, setCanvasState]
  );

  const zoomOutToParent = useCallback(async () => {
    await zoomManager.zoomOutToParent();
    // Update hierarchy trail
    setCanvasState((prev) => ({
      ...prev,
      hierarchyTrail: ((prev as unknown).hierarchyTrail || []).slice(0, -1),
    }));
  }, [zoomManager, setCanvasState]);

  // Connection operations
  const createConnection = useCallback(
    (source: string, target: string): Connection => {
      const connection = connectionManager.createConnection(source, target);
      setCanvasState((prev) => ({
        ...prev,
        connections: connectionManager.getConnections(),
      }));
      return connection;
    },
    [connectionManager, setCanvasState]
  );

  const removeConnection = useCallback(
    (connectionId: string) => {
      connectionManager.removeConnection(connectionId);
      setCanvasState((prev) => ({
        ...prev,
        connections: connectionManager.getConnections(),
      }));
    },
    [connectionManager, setCanvasState]
  );

  // Group operations
  const createGroup = useCallback(
    (nodeIds: string[], name?: string) => {
      const group = groupManager.createGroup(nodeIds, name);
      setCanvasState((prev) => ({
        ...prev,
        nodes: hierarchyManager.getAllNodes(),
        groups: [...prev.groups, group],
      }));
    },
    [groupManager, hierarchyManager, setCanvasState]
  );

  const ungroup = useCallback(
    (groupId: string) => {
      groupManager.ungroup(groupId);
      setCanvasState((prev) => ({
        ...prev,
        nodes: hierarchyManager.getAllNodes(),
        groups: prev.groups.filter((g) => g.id !== groupId),
      }));
    },
    [groupManager, hierarchyManager, setCanvasState]
  );

  // Alignment operations
  const alignNodes = useCallback(
    (alignment: AlignmentType) => {
      if (selectedNodeIds.length < 2) return;
      alignmentEngine.align(selectedNodeIds, alignment);
    },
    [alignmentEngine, selectedNodeIds]
  );

  const distributeNodes = useCallback(
    (axis: DistributionAxis) => {
      if (selectedNodeIds.length < 3) return;
      alignmentEngine.distribute(selectedNodeIds, axis);
    },
    [alignmentEngine, selectedNodeIds]
  );

  // Layer operations
  const bringForward = useCallback(
    (nodeId: string) => {
      layerManager.bringForward(nodeId);
    },
    [layerManager]
  );

  const sendBackward = useCallback(
    (nodeId: string) => {
      layerManager.sendBackward(nodeId);
    },
    [layerManager]
  );

  const bringToFront = useCallback(
    (nodeId: string) => {
      layerManager.bringToFront(nodeId);
    },
    [layerManager]
  );

  const sendToBack = useCallback(
    (nodeId: string) => {
      layerManager.sendToBack(nodeId);
    },
    [layerManager]
  );

  // Zoom operations
  const zoomIn = useCallback(() => zoomManager.zoomIn(), [zoomManager]);
  const zoomOut = useCallback(() => zoomManager.zoomOut(), [zoomManager]);
  const resetZoom = useCallback(() => zoomManager.resetZoom(), [zoomManager]);
  const zoomToFit = useCallback(() => {
    // Calculate bounds of all nodes
    // zoomManager.zoomToFit(bounds);
    return Promise.resolve();
  }, [zoomManager]);

  // Viewport operations
  const updateViewport = useCallback(
    (viewportUpdate: Partial<{ x: number; y: number; zoom: number }>) => {
      updateViewportCallback(viewportUpdate);
    },
    [updateViewportCallback]
  );

  // History operations — delegate to command-pattern atoms
  const undo = useCallback(() => {
    dispatchUndo();
    return true;
  }, [dispatchUndo]);

  const redo = useCallback(() => {
    dispatchRedo();
    return true;
  }, [dispatchRedo]);

  // Drawing operations
  const startDrawing = useCallback(
    (
      point: Point,
      tool: 'pen' | 'pencil' | 'marker' | 'highlighter' | 'eraser' = 'pen',
      color: string = '#000000',
      width: number = 2,
      opacity: number = 1
    ) => {
      console.log('[Canvas] Starting drawing at:', point);
      drawingManager.startStroke(point, tool, color, width, opacity);
    },
    [drawingManager]
  );

  const clearDrawings = useCallback(() => {
    setCanvasState((prev) => ({ ...prev, drawings: [] }));
  }, [setCanvasState]);

  const continueDrawing = useCallback(
    (point: Point) => {
      drawingManager.addPoint(point);
    },
    [drawingManager]
  );

  const endDrawing = useCallback((): DrawingStroke | null => {
    const stroke = drawingManager.endStroke();
    console.log('[Canvas] Ended drawing, stroke:', stroke);

    if (stroke && stroke.points.length > 1) {
      // Add stroke to canvas drawings (SVG overlay only, NO ReactFlow node)
      setCanvasState((prev) => {
        const drawings = Array.isArray(prev.drawings) ? prev.drawings : [];
        return {
          ...prev,
          drawings: [...drawings, stroke],
        };
      });

      console.log('[Canvas] Drawing persisted to SVG overlay:', stroke.id);
      return stroke;
    }
    return null;
  }, [drawingManager, setCanvasState, addNode]);

  // Create sticky note
  const createStickyNote = useCallback(
    (
      position: { x: number; y: number },
      color: 'yellow' | 'pink' | 'blue' | 'green' | 'purple' = 'yellow'
    ): HierarchicalNode => {
      console.log(
        '[Canvas] Creating sticky note at:',
        position,
        'color:',
        color
      );

      const stickyNode = addNode({
        type: 'sticky',
        position,
        size: { width: 200, height: 200 },
        data: {
          text: '',
          color,
          fontSize: 'medium',
        },
      });

      return stickyNode;
    },
    [addNode]
  );

  return {
    // State
    nodes,
    visibleNodes: culledVisibleNodes,
    connections,
    drawings: canvasState.drawings || [],
    selectedNodeIds,
    viewport,
    activeTool,

    // Managers
    zoomManager,
    hierarchyManager,
    resizeManager,
    layerManager,
    groupManager,
    connectionManager,
    alignmentEngine,
    smartGuides,
    drawingManager,

    // Node operations
    addNode,
    updateNode,
    removeNode,
    duplicateNode,

    // Selection
    selectNode,
    selectNodes,
    deselectAll,

    // Hierarchy
    addChildToNode,
    reparentNode,
    zoomIntoNode,
    zoomOutToParent,

    // Connections
    createConnection,
    removeConnection,

    // Groups
    createGroup,
    ungroup,

    // Alignment
    alignNodes,
    distributeNodes,

    // Layers
    bringForward,
    sendBackward,
    bringToFront,
    sendToBack,

    // Zoom
    zoomIn,
    zoomOut,
    resetZoom,
    zoomToFit,

    // Tools
    setActiveTool,

    // Viewport
    updateViewport,

    // Drawing operations
    startDrawing,
    continueDrawing,
    endDrawing,
    clearDrawings,
    createStickyNote,

    // History
    undo,
    redo,
    canUndo,
    canRedo,

    // Managers for export
    canvasExporter,
    mindMapEngine,

    // Export/Import operations
    exportToJSON: useCallback(() => {
      return canvasExporter.exportToJSON(
        nodes,
        connections,
        canvasState.drawings || [],
        viewport,
        projectId
      );
    }, [
      canvasExporter,
      nodes,
      connections,
      canvasState.drawings,
      viewport,
      projectId,
    ]),

    exportToSVG: useCallback(() => {
      return canvasExporter.exportToSVG(
        nodes,
        connections,
        canvasState.drawings || []
      );
    }, [canvasExporter, nodes, connections, canvasState.drawings]),

    exportToPNG: useCallback(
      async (element: HTMLElement) => {
        return await canvasExporter.exportToPNG(element);
      },
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
        const json = canvasExporter.exportToJSON(
          nodes,
          connections,
          canvasState.drawings || [],
          viewport,
          projectId
        );
        const name = filename || `canvas-${Date.now()}.json`;
        canvasExporter.downloadFile(json, name, 'application/json');
      },
      [
        canvasExporter,
        nodes,
        connections,
        canvasState.drawings,
        viewport,
        projectId,
      ]
    ),

    downloadSVG: useCallback(
      (filename?: string) => {
        const svg = canvasExporter.exportToSVG(
          nodes,
          connections,
          canvasState.drawings || []
        );
        const name = filename || `canvas-${Date.now()}.svg`;
        canvasExporter.downloadFile(svg, name, 'image/svg+xml');
      },
      [canvasExporter, nodes, connections, canvasState.drawings]
    ),

    // Mind Map Layout
    applyMindMapLayout: useCallback(
      (layoutType: 'tree' | 'radial' | 'fishbone' = 'tree') => {
        // Update engine config
        mindMapEngine.updateConfig({ type: layoutType });

        // Apply layout to mind map nodes
        const mindMapNodes = nodes.filter(
          (n) => n.type === 'mindmap' || n.data.isMindMap
        );

        if (mindMapNodes.length === 0) {
          console.warn('[Canvas] No mind map nodes to layout');
          return;
        }

        const layoutedNodes = mindMapEngine.layoutNodes(mindMapNodes as unknown);

        // Update positions
        layoutedNodes.forEach((layouted) => {
          updateNodeCallback(layouted.id, {
            position: layouted.position,
          });
        });
      },
      [mindMapEngine, nodes, updateNodeCallback]
    ),
  };
}
