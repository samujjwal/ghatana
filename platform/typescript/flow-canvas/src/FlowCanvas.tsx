/**
 * FlowCanvas — main topology/flow diagram component.
 *
 * A ready-to-use React Flow canvas pre-wired with Ghatana's 4-tier
 * topology nodes, agent nodes, animated data-flow edges, and platform
 * zoom/pan controls. Wraps `@xyflow/react` `ReactFlow` with opinionated defaults.
 *
 * Usage:
 * ```tsx
 * import { FlowCanvas } from '@ghatana/flow-canvas';
 * import type { FlowNode, FlowEdge } from '@ghatana/flow-canvas';
 *
 * const nodes: FlowNode[] = [
 *   { id: 'hot-a', type: 'hotTier', position: { x: 0, y: 0 },
 *     data: { label: 'Redis Cluster', status: 'healthy', metrics: { throughput: 42000 } } },
 *   { id: 'warm-a', type: 'warmTier', position: { x: 250, y: 0 },
 *     data: { label: 'PostgreSQL', status: 'healthy' } },
 * ];
 *
 * const edges: FlowEdge[] = [
 *   { id: 'e1', source: 'hot-a', target: 'warm-a', type: 'dataFlow',
 *     data: { throughput: 42000, animated: true } },
 * ];
 *
 * <FlowCanvas nodes={nodes} edges={edges} />
 * ```
 *
 * @doc.type component
 * @doc.purpose Main topology/flow canvas for AEP and Data-Cloud UIs
 * @doc.layer shared
 * @doc.pattern CompositeComponent
 */
import React, { useMemo, useCallback } from 'react';
import ReactFlow, {
  ReactFlowProvider,
  Background,
  BackgroundVariant,
  type Node,
  type Edge,
  type OnNodesChange,
  type OnEdgesChange,
  type OnConnect,
} from '@xyflow/react';

import HotTierNode from './nodes/HotTierNode';
import WarmTierNode from './nodes/WarmTierNode';
import ColdTierNode from './nodes/ColdTierNode';
import ArchiveTierNode from './nodes/ArchiveTierNode';
import AgentNode from './nodes/AgentNode';
import DataFlowEdge from './edges/DataFlowEdge';
import FlowControls, { type FlowControlsProps } from './controls/FlowControls';
import type { FlowNode, FlowEdge } from './types';

// ==================== Built-in node and edge registries ====================

const NODE_TYPES = {
  hotTier: HotTierNode,
  warmTier: WarmTierNode,
  coldTier: ColdTierNode,
  archiveTier: ArchiveTierNode,
  agent: AgentNode,
} as const;

const EDGE_TYPES = {
  dataFlow: DataFlowEdge,
} as const;

// ==================== Props ====================

export interface FlowCanvasProps {
  /** Nodes to render. Use FlowNode for typed tier/agent nodes, or plain Node. */
  nodes: FlowNode[];
  /** Edges to render. Use FlowEdge for typed data-flow edges, or plain Edge. */
  edges: FlowEdge[];
  /** Callback fired when node positions or selection changes (controlled mode). */
  onNodesChange?: OnNodesChange<FlowNode>;
  /** Callback fired when edge changes occur (controlled mode). */
  onEdgesChange?: OnEdgesChange<FlowEdge>;
  /** Callback fired when a new connection is drawn. */
  onConnect?: OnConnect;
  /**
   * Whether nodes can be arranged by user drag. Default: true.
   * Set to false for read-only topology displays.
   */
  nodesDraggable?: boolean;
  /** Whether the viewport (pan/zoom) can be interacted with. Default: true. */
  panOnDrag?: boolean;
  /** Enable scroll-to-zoom. Default: true. */
  zoomOnScroll?: boolean;
  /** Additional custom node types merged with the built-in ones. */
  additionalNodeTypes?: Record<string, React.ComponentType<Node>>;
  /** Additional custom edge types merged with the built-in ones. */
  additionalEdgeTypes?: Record<string, React.ComponentType<Edge>>;
  /** Controls configuration. Defaults to showing both controls and minimap. */
  controls?: FlowControlsProps | false;
  /** Show background grid. Default: true. */
  showBackground?: boolean;
  /** CSS class applied to the root ReactFlow container. */
  className?: string;
  /**
   * Inline style applied to the root container.
   * The container MUST have an explicit height for ReactFlow to render correctly.
   */
  style?: React.CSSProperties;
  /** aria-label for accessibility. */
  ariaLabel?: string;
}

// ==================== Component ====================

/**
 * FlowCanvas inner component (requires `ReactFlowProvider` ancestor).
 * Use the default `FlowCanvas` export for a self-contained version.
 */
function FlowCanvasInner({
  nodes,
  edges,
  onNodesChange,
  onEdgesChange,
  onConnect,
  nodesDraggable = true,
  panOnDrag = true,
  zoomOnScroll = true,
  additionalNodeTypes,
  additionalEdgeTypes,
  controls = {},
  showBackground = true,
  className,
  style,
  ariaLabel = 'Flow diagram canvas',
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
    (connection) => {
      onConnect?.(connection);
    },
    [onConnect],
  );

  return (
    <div
      role="application"
      aria-label={ariaLabel}
      className={className}
      style={{ width: '100%', height: '100%', ...style }}
    >
      <ReactFlow
        nodes={nodes}
        edges={edges}
        onNodesChange={onNodesChange}
        onEdgesChange={onEdgesChange}
        onConnect={handleConnect}
        nodeTypes={mergedNodeTypes}
        edgeTypes={mergedEdgeTypes}
        nodesDraggable={nodesDraggable}
        panOnDrag={panOnDrag}
        zoomOnScroll={zoomOnScroll}
        fitView
        attributionPosition="bottom-left"
        proOptions={{ hideAttribution: true }}
        defaultEdgeOptions={{ type: 'dataFlow' }}
      >
        {showBackground && (
          <Background
            variant={BackgroundVariant.Dots}
            gap={16}
            size={1}
            color="#e2e8f0"
          />
        )}
        {controls !== false && <FlowControls {...(controls ?? {})} />}
      </ReactFlow>
    </div>
  );
}

/**
 * Self-contained `FlowCanvas` — wraps its own `ReactFlowProvider`.
 *
 * Renders topology/flow diagrams using Ghatana's 4-tier EventCloud node types,
 * agent nodes, and animated data-flow edges.
 */
export default function FlowCanvas(props: FlowCanvasProps) {
  return (
    <ReactFlowProvider>
      <FlowCanvasInner {...props} />
    </ReactFlowProvider>
  );
}
