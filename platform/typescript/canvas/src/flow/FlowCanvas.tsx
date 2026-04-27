/**
 * @fileoverview FlowCanvas — main topology/flow diagram component.
 *
 * Absorbed from the former `@ghatana/flow-canvas` standalone package.
 * This is a ready-to-use React Flow canvas pre-wired with Ghatana's 4-tier
 * topology nodes, agent nodes, animated data-flow edges, and platform controls.
 *
 * Migration: replace `@ghatana/flow-canvas` imports with `@ghatana/canvas/flow`.
 *
 * @doc.type component
 * @doc.purpose Main topology/flow canvas for AEP and Data-Cloud UIs
 * @doc.layer platform
 * @doc.pattern CompositeComponent
 */
import React, { useMemo, useCallback } from 'react';
import {
  ReactFlow,
  ReactFlowProvider,
  Background,
  BackgroundVariant,
  type Node,
  type Edge,
  type NodeTypes,
  type EdgeTypes,
  type OnNodesChange,
  type OnEdgesChange,
  type OnConnect,
  type ReactFlowInstance,
} from '@xyflow/react';
import type { FlowNode, FlowEdge } from './types.js';

// ── Built-in node/edge/control types ─────────────────────────────────────
import HotTierNode from './nodes/HotTierNode.js';
import WarmTierNode from './nodes/WarmTierNode.js';
import ColdTierNode from './nodes/ColdTierNode.js';
import ArchiveTierNode from './nodes/ArchiveTierNode.js';
import AgentNode from './nodes/AgentNode.js';
import PipelineStageNode from './nodes/PipelineStageNode.js';
import OperatorNode from './nodes/OperatorNode.js';
import DataFlowEdge from './edges/DataFlowEdge.js';
import FlowControls, { type FlowControlsProps } from './controls/FlowControls.js';

// ReactFlow's NodeTypes/EdgeTypes index signature is intentionally loose —
// cast through unknown to register strongly-typed node/edge components.
const NODE_TYPES = {
  hotTier: HotTierNode,
  warmTier: WarmTierNode,
  coldTier: ColdTierNode,
  archiveTier: ArchiveTierNode,
  agent: AgentNode,
  pipelineStage: PipelineStageNode,
  operator: OperatorNode,
} as unknown as NodeTypes;

const EDGE_TYPES = {
  dataFlow: DataFlowEdge,
} as unknown as EdgeTypes;

// ── Props ──────────────────────────────────────────────────────────────────

export interface FlowCanvasProps {
  nodes: FlowNode[];
  edges: FlowEdge[];
  onNodesChange?: OnNodesChange<FlowNode>;
  onEdgesChange?: OnEdgesChange<FlowEdge>;
  onConnect?: OnConnect;
  onNodeClick?: (event: React.MouseEvent, node: FlowNode) => void;
  onEdgeClick?: (event: React.MouseEvent, edge: FlowEdge) => void;
  onPaneClick?: (event: React.MouseEvent) => void;
  onInit?: (instance: ReactFlowInstance<FlowNode, FlowEdge>) => void;
  nodesDraggable?: boolean;
  panOnDrag?: boolean;
  zoomOnScroll?: boolean;
  snapToGrid?: boolean;
  snapGrid?: [number, number];
  deleteKeyCode?: string | null;
  additionalNodeTypes?: Record<string, React.ComponentType<Node>>;
  additionalEdgeTypes?: Record<string, React.ComponentType<Edge>>;
  controls?: FlowControlsProps | false;
  showBackground?: boolean;
  className?: string;
  style?: React.CSSProperties;
  ariaLabel?: string;
  onDrop?: (event: React.DragEvent) => void;
  onDragOver?: (event: React.DragEvent) => void;
  onKeyDown?: (event: React.KeyboardEvent) => void;
  tabIndex?: number;
}

// ── Inner component (requires ReactFlowProvider ancestor) ──────────────────

function FlowCanvasInner({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  onNodeClick,
  onEdgeClick,
  onPaneClick,
  onInit,
  nodesDraggable = true,
  panOnDrag = true,
  zoomOnScroll = true,
  snapToGrid = false,
  snapGrid = [16, 16],
  deleteKeyCode,
  additionalNodeTypes,
  additionalEdgeTypes,
  controls = {},
  showBackground = true,
  className,
  style,
  ariaLabel = 'Flow diagram canvas',
  onDrop,
  onDragOver,
  onKeyDown,
  tabIndex,
}: FlowCanvasProps) {
  const mergedNodeTypes = useMemo(
    () => ({ ...NODE_TYPES, ...additionalNodeTypes }),
    [additionalNodeTypes],
  );
  const mergedEdgeTypes = useMemo(
    () => ({ ...EDGE_TYPES, ...additionalEdgeTypes }),
    [additionalEdgeTypes],
  );
  const handleConnect: OnConnect = useCallback(
    (connection) => onConnect?.(connection),
    [onConnect],
  );

  return (
    <div
      role="application"
      aria-label={ariaLabel}
      className={className}
      style={{ width: '100%', height: '100%', ...style }}
      onDrop={onDrop}
      onDragOver={onDragOver}
      onKeyDown={onKeyDown}
      tabIndex={onKeyDown !== undefined && tabIndex === undefined ? 0 : tabIndex}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        onNodeClick={onNodeClick as never}
        onEdgeClick={onEdgeClick as never}
        onPaneClick={onPaneClick}
        onInit={onInit as never}
        nodeTypes={mergedNodeTypes as NodeTypes}
        edgeTypes={mergedEdgeTypes as EdgeTypes}
        nodesDraggable={nodesDraggable}
        panOnDrag={panOnDrag}
        zoomOnScroll={zoomOnScroll}
        snapToGrid={snapToGrid}
        snapGrid={snapGrid}
        deleteKeyCode={deleteKeyCode}
        fitView
        attributionPosition="bottom-left"
        proOptions={{ hideAttribution: true }}
        defaultEdgeOptions={{ type: 'dataFlow' }}
      >
        {showBackground && (
          <Background variant={BackgroundVariant.Dots} gap={16} size={1} color="#e2e8f0" />
        )}
        {controls !== false && <FlowControls {...(controls ?? {})} />}
      </ReactFlow>
    </div>
  );
}

/**
 * Self-contained `FlowCanvas` — wraps its own `ReactFlowProvider`.
 *
 * Import from `@ghatana/canvas/flow`.
 */
export function FlowCanvas(props: FlowCanvasProps): React.JSX.Element {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner {...props} />
    </ReactFlowProvider>
  );
}
