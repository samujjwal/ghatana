// @ts-nocheck
/**
 * useCanvasNodeOps - Canvas Node Operations Hook
 *
 * Provides node CRUD, selection, hierarchy, connection, group, alignment,
 * layer, and zoom operation callbacks for the canvas.
 *
 * @doc.type hook
 * @doc.purpose Canvas node, selection, and structural operations
 * @doc.layer hooks
 * @doc.pattern Hook
 */

import { useCallback } from 'react';
import { useAtom } from 'jotai';
import { canvasAtom } from '../state/atoms/unifiedCanvasAtom';
import { type HierarchicalNode } from '../lib/canvas/HierarchyManager';
import { type Connection } from '../lib/canvas/NodeManipulation';
import { type AlignmentType, type DistributionAxis } from '../lib/canvas/AlignmentEngine';
import { type UseCanvasManagersReturn } from './useCanvasManagers';

export interface UseCanvasNodeOpsReturn {
  addNode: (node: Partial<HierarchicalNode>) => HierarchicalNode;
  updateNode: (nodeId: string, updates: Partial<HierarchicalNode>) => void;
  removeNode: (nodeId: string) => void;
  duplicateNode: (nodeId: string) => HierarchicalNode;
  selectNode: (nodeId: string, addToSelection?: boolean) => void;
  selectNodes: (nodeIds: string[]) => void;
  deselectAll: () => void;
  addChildToNode: (parentId: string, child: Partial<HierarchicalNode>) => HierarchicalNode;
  reparentNode: (nodeId: string, newParentId: string) => void;
  zoomIntoNode: (nodeId: string) => Promise<void>;
  zoomOutToParent: () => Promise<void>;
  createConnection: (source: string, target: string) => Connection;
  removeConnection: (connectionId: string) => void;
  createGroup: (nodeIds: string[], name?: string) => void;
  ungroup: (groupId: string) => void;
  alignNodes: (alignment: AlignmentType) => void;
  distributeNodes: (axis: DistributionAxis) => void;
  bringForward: (nodeId: string) => void;
  sendBackward: (nodeId: string) => void;
  bringToFront: (nodeId: string) => void;
  sendToBack: (nodeId: string) => void;
  zoomIn: () => Promise<void>;
  zoomOut: () => Promise<void>;
  resetZoom: () => Promise<void>;
  zoomToFit: () => Promise<void>;
  updateViewport: (update: Partial<{ x: number; y: number; zoom: number }>) => void;
}

export function useCanvasNodeOps(
  managers: UseCanvasManagersReturn,
  selectedNodeIds: string[]
): UseCanvasNodeOpsReturn {
  const [, setCanvasState] = useAtom(canvasAtom);

  const {
    hierarchyManager,
    layerManager,
    groupManager,
    connectionManager,
    alignmentEngine,
    zoomManager,
    getNode,
    updateNodeCallback,
    updateViewportCallback,
  } = managers;

  const addNode = useCallback(
    (node: Partial<HierarchicalNode>): HierarchicalNode => {
      const newNode: HierarchicalNode = {
        id: node.id || `node_${Date.now()}_${Math.random().toString(36).substr(2, 9)}`,
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
      setCanvasState((prev) => ({ ...prev, nodes: [...prev.nodes, newNode] }));
      return newNode;
    },
    [setCanvasState]
  );

  const updateNode = useCallback(
    (nodeId: string, updates: Partial<HierarchicalNode>) => updateNodeCallback(nodeId, updates),
    [updateNodeCallback]
  );

  const removeNode = useCallback(
    (nodeId: string) => {
      hierarchyManager.removeNode(nodeId);
      setCanvasState((prev) => ({ ...prev, nodes: hierarchyManager.getAllNodes() }));
    },
    [hierarchyManager, setCanvasState]
  );

  const duplicateNode = useCallback(
    (nodeId: string): HierarchicalNode => {
      const node = getNode(nodeId);
      if (!node) throw new Error('Node not found');
      return addNode({
        ...node,
        id: undefined,
        position: { x: node.position.x + 20, y: node.position.y + 20 },
      });
    },
    [getNode, addNode]
  );

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
      setCanvasState((prev) => ({ ...prev, selectedNodeIds: nodeIds }));
    },
    [setCanvasState]
  );

  const deselectAll = useCallback(() => {
    setCanvasState((prev) => ({ ...prev, selectedNodeIds: [] }));
  }, [setCanvasState]);

  const addChildToNode = useCallback(
    (parentId: string, child: Partial<HierarchicalNode>): HierarchicalNode => {
      const newChild = hierarchyManager.addChild(parentId, child);
      setCanvasState((prev) => ({ ...prev, nodes: hierarchyManager.getAllNodes() }));
      return newChild;
    },
    [hierarchyManager, setCanvasState]
  );

  const reparentNode = useCallback(
    (nodeId: string, newParentId: string) => {
      hierarchyManager.reparent(nodeId, newParentId);
      setCanvasState((prev) => ({ ...prev, nodes: hierarchyManager.getAllNodes() }));
    },
    [hierarchyManager, setCanvasState]
  );

  const zoomIntoNode = useCallback(
    async (nodeId: string) => {
      await zoomManager.zoomIntoNode(nodeId);
      setCanvasState((prev) => ({
        ...prev,
        hierarchyTrail: [...(((prev as unknown) as Record<string, unknown>).hierarchyTrail as string[] || []), nodeId],
      }));
    },
    [zoomManager, setCanvasState]
  );

  const zoomOutToParent = useCallback(async () => {
    await zoomManager.zoomOutToParent();
    setCanvasState((prev) => ({
      ...prev,
      hierarchyTrail: ((((prev as unknown) as Record<string, unknown>).hierarchyTrail as string[]) || []).slice(0, -1),
    }));
  }, [zoomManager, setCanvasState]);

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

  const bringForward = useCallback(
    (nodeId: string) => layerManager.bringForward(nodeId),
    [layerManager]
  );
  const sendBackward = useCallback(
    (nodeId: string) => layerManager.sendBackward(nodeId),
    [layerManager]
  );
  const bringToFront = useCallback(
    (nodeId: string) => layerManager.bringToFront(nodeId),
    [layerManager]
  );
  const sendToBack = useCallback(
    (nodeId: string) => layerManager.sendToBack(nodeId),
    [layerManager]
  );

  const zoomIn = useCallback(() => zoomManager.zoomIn(), [zoomManager]);
  const zoomOut = useCallback(() => zoomManager.zoomOut(), [zoomManager]);
  const resetZoom = useCallback(() => zoomManager.resetZoom(), [zoomManager]);
  const zoomToFit = useCallback(() => Promise.resolve(), []);

  const updateViewport = useCallback(
    (update: Partial<{ x: number; y: number; zoom: number }>) =>
      updateViewportCallback(update),
    [updateViewportCallback]
  );

  return {
    addNode,
    updateNode,
    removeNode,
    duplicateNode,
    selectNode,
    selectNodes,
    deselectAll,
    addChildToNode,
    reparentNode,
    zoomIntoNode,
    zoomOutToParent,
    createConnection,
    removeConnection,
    createGroup,
    ungroup,
    alignNodes,
    distributeNodes,
    bringForward,
    sendBackward,
    bringToFront,
    sendToBack,
    zoomIn,
    zoomOut,
    resetZoom,
    zoomToFit,
    updateViewport,
  };
}
