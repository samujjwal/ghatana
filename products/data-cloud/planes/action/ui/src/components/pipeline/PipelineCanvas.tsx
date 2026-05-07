/**
 * PipelineCanvas — Main canvas for visual pipeline design.
 *
 * Built on `@ghatana/canvas/flow` (platform shared canvas). Orchestrates:
 * - Drag-and-drop from StagePalette to create stage/connector nodes
 * - Edge connections between stages (sequential pipeline flow)
 * - Selection sync with Jotai store → PipelinePropertyPanel
 * - Keyboard shortcuts (Delete, Backspace)
 *
 * @doc.type component
 * @doc.purpose Main pipeline editor canvas — wraps @ghatana/canvas/flow
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useRef, type DragEvent } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  ReactFlow,
  addEdge,
  useNodesState,
  useEdgesState,
  type Node,
  type Edge,
  type Connection,
  type ReactFlowInstance,
} from '@xyflow/react';
import '@xyflow/react/dist/style.css';

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
type CanvasEdge = Edge;

// ─── AEP-specific node types (passed to ReactFlow) ────

const PIPELINE_NODE_TYPES = {
  stage: StageNode,
  connector: ConnectorNode,
};

// ─── Helpers ─────────────────────────────────────────────────────────

let nodeIdCounter = 0;
function nextId(prefix: string): string {
  return `${prefix}-${++nodeIdCounter}-${Date.now().toString(36)}`;
}

// ─── Component ───────────────────────────────────────────────────────

export function PipelineCanvas() {
  const rfInstance = useRef<ReactFlowInstance<CanvasNode, CanvasEdge> | null>(null);

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
    <div
      data-testid="pipeline-canvas"
      className="flex-1 h-full min-h-[400px]"
      style={{ width: '100%', height: '100%' }}
      onKeyDown={onKeyDown}
      tabIndex={0}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={onConnect}
        onNodeClick={onNodeClick}
        onEdgeClick={onEdgeClick}
        onPaneClick={onPaneClick}
        onInit={(instance) => {
          rfInstance.current = instance;
        }}
        nodeTypes={PIPELINE_NODE_TYPES}
        snapToGrid
        snapGrid={[16, 16]}
        deleteKeyCode={null}
        onDrop={onDrop}
        onDragOver={onDragOver}
        onKeyDown={onKeyDown}
        style={{ width: '100%', height: '100%' }}
        aria-label="AEP pipeline builder canvas"
      />
    </div>
  );
}

