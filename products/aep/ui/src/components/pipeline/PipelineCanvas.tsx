/**
 * PipelineCanvas — Main canvas for visual pipeline design.
 *
 * Built on `@ghatana/flow-canvas` (platform shared canvas). Orchestrates:
 * - Drag-and-drop from StagePalette to create stage/connector nodes
 * - Edge connections between stages (sequential pipeline flow)
 * - Selection sync with Jotai store → PipelinePropertyPanel
 * - Keyboard shortcuts (Delete, Backspace)
 *
 * @doc.type component
 * @doc.purpose Main pipeline editor canvas — wraps @ghatana/flow-canvas
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useRef, type DragEvent } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  FlowCanvas,
  addEdge,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type Connection,
  type ReactFlowInstance,
} from '@ghatana/flow-canvas';

import { StageNode, ConnectorNode } from './nodes';
import {
  nodesAtom,
  edgesAtom,
  selectedNodeIdAtom,
  selectedEdgeIdAtom,
  isDirtyAtom,
  historyAtom,
  historyIndexAtom,
} from '@/stores/pipeline.store';
import type {
  StageNodeData,
  ConnectorNodeData,
  PaletteItem,
  ConnectorPaletteItem,
  StageKind,
} from '@/types/pipeline.types';

type CanvasNode = Node<StageNodeData | ConnectorNodeData>;

// ─── AEP-specific node types (passed as additionalNodeTypes to FlowCanvas) ────

const PIPELINE_NODE_TYPES = {
  stage: StageNode as React.ComponentType<Node>,
  connector: ConnectorNode as React.ComponentType<Node>,
};

// ─── Helpers ─────────────────────────────────────────────────────────

let nodeIdCounter = 0;
function nextId(prefix: string): string {
  return `${prefix}-${++nodeIdCounter}-${Date.now().toString(36)}`;
}

// ─── Component ───────────────────────────────────────────────────────

export function PipelineCanvas() {
  const rfInstance = useRef<ReactFlowInstance | null>(null);

  // Jotai ↔ local state sync
  const [storeNodes, setStoreNodes] = useAtom(nodesAtom);
  const [storeEdges, setStoreEdges] = useAtom(edgesAtom);
  const setSelectedNodeId = useSetAtom(selectedNodeIdAtom);
  const setSelectedEdgeId = useSetAtom(selectedEdgeIdAtom);
  const setDirty = useSetAtom(isDirtyAtom);
  const [history, setHistory] = useAtom(historyAtom);
  const [historyIndex, setHistoryIndex] = useAtom(historyIndexAtom);

  // ReactFlow local state (using re-exported hooks from flow-canvas)
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>(storeNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(storeEdges);

  // ── Reseed local state when Jotai store changes externally (undo/redo, handleNew)
  useEffect(() => {
    setNodes(storeNodes);
  }, [storeNodes, setNodes]);

  useEffect(() => {
    setEdges(storeEdges);
  }, [storeEdges, setEdges]);

  // ── History push helper ─────────────────────────────────────────
  const pushHistory = useCallback(
    (newNodes: CanvasNode[], newEdges: Edge[]) => {
      const snapshot = { nodes: newNodes, edges: newEdges };
      const truncated = history.slice(0, historyIndex + 1);
      setHistory([...truncated, snapshot]);
      setHistoryIndex(truncated.length);
    },
    [history, historyIndex, setHistory, setHistoryIndex],
  );

  // ── Connection handler ──────────────────────────────────────────

  const onConnect = useCallback(
    (connection: Connection) => {
      setEdges((eds) => {
        const next = addEdge(
          {
            ...connection,
            animated: true,
            style: { strokeWidth: 2, stroke: '#6366f1' },
          },
          eds,
        );
        setStoreEdges(next);
        setDirty(true);
        pushHistory(nodes, next);
        return next;
      });
    },
    [setEdges, setStoreEdges, setDirty, pushHistory, nodes],
  );

  // ── Selection handlers ──────────────────────────────────────────

  const onNodeClick = useCallback(
    (_: React.MouseEvent, node: CanvasNode) => {
      setSelectedNodeId(node.id);
      setSelectedEdgeId(null);
    },
    [setSelectedNodeId, setSelectedEdgeId],
  );

  const onEdgeClick = useCallback(
    (_: React.MouseEvent, edge: Edge) => {
      setSelectedEdgeId(edge.id);
      setSelectedNodeId(null);
    },
    [setSelectedEdgeId, setSelectedNodeId],
  );

  const onPaneClick = useCallback(() => {
    setSelectedNodeId(null);
    setSelectedEdgeId(null);
  }, [setSelectedNodeId, setSelectedEdgeId]);

  // ── Drag-and-drop from palette ──────────────────────────────────

  const onDragOver = useCallback((e: DragEvent) => {
    e.preventDefault();
    e.dataTransfer.dropEffect = 'move';
  }, []);

  const onDrop = useCallback(
    (e: DragEvent) => {
      e.preventDefault();
      const nodeType = e.dataTransfer.getData('application/reactflow-type');
      const rawData = e.dataTransfer.getData('application/reactflow-data');
      if (!nodeType || !rawData) return;
      if (!rfInstance.current) return;

      const position = rfInstance.current.screenToFlowPosition({
        x: e.clientX,
        y: e.clientY,
      });

      let newNode: CanvasNode;

      if (nodeType === 'stage') {
        const palette: PaletteItem = JSON.parse(rawData);
        const data: StageNodeData = {
          label: palette.label,
          kind: palette.kind as StageKind,
          agents: palette.defaultAgents ?? [],
          agentCount: palette.defaultAgents?.length ?? 0,
          description: palette.description,
        };
        newNode = { id: nextId('stage'), type: 'stage', position, data };
      } else {
        const palette: ConnectorPaletteItem = JSON.parse(rawData);
        const data: ConnectorNodeData = {
          label: palette.label,
          connectorId: nextId('conn'),
          type: palette.type,
          direction: palette.direction,
        };
        newNode = { id: nextId('connector'), type: 'connector', position, data };
      }

      setNodes((nds) => {
        const next = [...nds, newNode];
        setStoreNodes(next);
        setDirty(true);
        pushHistory(next, edges);
        return next;
      });
    },
    [setNodes, setStoreNodes, setDirty, pushHistory, edges],
  );

  // ── Keyboard shortcuts ──────────────────────────────────────────

  const onKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Delete' || e.key === 'Backspace') {
        setNodes((nds) => {
          const selected = nds.filter((n) => n.selected);
          if (selected.length === 0) return nds;
          const selectedIds = new Set(selected.map((n) => n.id));
          const next = nds.filter((n) => !selectedIds.has(n.id));
          setStoreNodes(next);
          setDirty(true);
          return next;
        });
        setEdges((eds) => {
          const next = eds.filter((e) => !e.selected);
          setStoreEdges(next);
          return next;
        });
      }
    },
    [setNodes, setEdges, setStoreNodes, setStoreEdges, setDirty],
  );

  // ── Render ──────────────────────────────────────────────────────

  return (
    <FlowCanvas
      nodes={nodes as never}
      edges={edges as never}
      onNodesChange={onNodesChange as never}
      onEdgesChange={onEdgesChange as never}
      onConnect={onConnect}
      onNodeClick={onNodeClick as never}
      onEdgeClick={onEdgeClick as never}
      onPaneClick={onPaneClick}
      onInit={(instance) => {
        rfInstance.current = instance as never;
      }}
      additionalNodeTypes={PIPELINE_NODE_TYPES}
      snapToGrid
      snapGrid={[16, 16]}
      deleteKeyCode={null}
      onDrop={onDrop}
      onDragOver={onDragOver}
      onKeyDown={onKeyDown}
      className="flex-1 h-full"
      ariaLabel="AEP pipeline builder canvas"
    />
  );
}

