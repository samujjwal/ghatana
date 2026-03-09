/**
 * PipelineCanvas — Main ReactFlow canvas for visual pipeline design.
 *
 * Orchestrates:
 * - Drag-and-drop from StagePalette to create stage/connector nodes
 * - Edge connections between stages (sequential pipeline flow)
 * - Selection sync with Jotai store → PipelinePropertyPanel
 * - Keyboard shortcuts (Delete, Ctrl+Z, Ctrl+Y)
 * - Minimap, controls, grid background
 *
 * @doc.type component
 * @doc.purpose Main pipeline editor canvas
 * @doc.layer frontend
 */
import React, { useCallback, useEffect, useRef, type DragEvent } from 'react';
import { useAtom, useSetAtom } from 'jotai';
import {
  ReactFlow,
  addEdge,
  Background,
  Controls,
  MiniMap,
  useNodesState,
  useEdgesState,
  type Connection,
  type Node,
  type Edge,
  type ReactFlowInstance,
  BackgroundVariant,
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

// ─── Custom node type registry ───────────────────────────────────────

const nodeTypes = {
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
  const reactFlowWrapper = useRef<HTMLDivElement>(null);
  const rfInstance = useRef<ReactFlowInstance | null>(null);

  // Jotai ↔ local state sync
  const [storeNodes, setStoreNodes] = useAtom(nodesAtom);
  const [storeEdges, setStoreEdges] = useAtom(edgesAtom);
  const setSelectedNodeId = useSetAtom(selectedNodeIdAtom);
  const setSelectedEdgeId = useSetAtom(selectedEdgeIdAtom);
  const setDirty = useSetAtom(isDirtyAtom);
  const [history, setHistory] = useAtom(historyAtom);
  const [historyIndex, setHistoryIndex] = useAtom(historyIndexAtom);

  // ReactFlow local state
  const [nodes, setNodes, onNodesChange] = useNodesState<CanvasNode>(storeNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState(storeEdges);

  // ── Reseed ReactFlow when Jotai store is mutated externally (e.g. handleNew, undo/redo)
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

      const bounds = reactFlowWrapper.current?.getBoundingClientRect();
      if (!bounds || !rfInstance.current) return;

      const position = rfInstance.current.screenToFlowPosition({
        x: e.clientX - bounds.left,
        y: e.clientY - bounds.top,
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
        newNode = {
          id: nextId('stage'),
          type: 'stage',
          position,
          data,
        };
      } else {
        const palette: ConnectorPaletteItem = JSON.parse(rawData);
        const data: ConnectorNodeData = {
          label: palette.label,
          connectorId: nextId('conn'),
          type: palette.type,
          direction: palette.direction,
        };
        newNode = {
          id: nextId('connector'),
          type: 'connector',
          position,
          data,
        };
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
    [setNodes, setEdges, setStoreNodes, setStoreEdges, setDirty, pushHistory, nodes, edges],
  );

  // ── Render ──────────────────────────────────────────────────────

  return (
    <div
      ref={reactFlowWrapper}
      className="flex-1 h-full"
      onDragOver={onDragOver}
      onDrop={onDrop}
      onKeyDown={onKeyDown}
      tabIndex={0}
      data-testid="pipeline-canvas"
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
          rfInstance.current = instance as unknown as ReactFlowInstance;
        }}
        nodeTypes={nodeTypes}
        fitView
        snapToGrid
        snapGrid={[16, 16]}
        deleteKeyCode={null} // We handle delete ourselves
      >
        <Background variant={BackgroundVariant.Dots} gap={16} size={1} />
        <Controls position="bottom-right" />
        <MiniMap
          nodeStrokeWidth={3}
          pannable
          zoomable
          position="bottom-left"
        />
      </ReactFlow>
    </div>
  );
}
