/**
 * @ghatana/flow-canvas
 *
 * Platform flow/topology canvas built on @xyflow/react.
 *
 * Exports:
 * - `FlowCanvas` — main self-contained canvas component
 * - Topology node components (HotTierNode, WarmTierNode, ColdTierNode, ArchiveTierNode, AgentNode)
 * - Pipeline node components (PipelineStageNode, OperatorNode)
 * - Edge components (DataFlowEdge)
 * - `FlowControls` — zoom/pan/minimap controls panel
 * - Type definitions
 *
 * @module @ghatana/flow-canvas
 */

// ==================== Main Component ====================
export { default as FlowCanvas } from './FlowCanvas';
export type { FlowCanvasProps } from './FlowCanvas';

// ==================== Nodes ====================
export { default as HotTierNode } from './nodes/HotTierNode';
export { default as WarmTierNode } from './nodes/WarmTierNode';
export { default as ColdTierNode } from './nodes/ColdTierNode';
export { default as ArchiveTierNode } from './nodes/ArchiveTierNode';
export { default as AgentNode } from './nodes/AgentNode';
export { default as PipelineStageNode } from './nodes/PipelineStageNode';
export { default as OperatorNode } from './nodes/OperatorNode';

// ==================== Edges ====================
export { default as DataFlowEdge } from './edges/DataFlowEdge';

// ==================== Controls ====================
export { default as FlowControls } from './controls/FlowControls';
export type { FlowControlsProps } from './controls/FlowControls';

// ==================== Types ====================
export type {
  NodeStatus,
  NodeMetrics,
  TierNodeData,
  AgentNodeData,
  PipelineStageNodeData,
  OperatorNodeData,
  DataFlowEdgeData,
  HotTierNode as HotTierNodeType,
  WarmTierNode as WarmTierNodeType,
  ColdTierNode as ColdTierNodeType,
  ArchiveTierNode as ArchiveTierNodeType,
  AgentNetworkNode,
  PipelineStageNode as PipelineStageNodeType,
  OperatorNode as OperatorNodeType,
  FlowNode,
  FlowEdge,
} from './types';

// ==================== Re-export key @xyflow/react utilities ====================
// Consumers don't need to add @xyflow/react as a direct dependency for basic use.
export {
  useNodesState,
  useEdgesState,
  useReactFlow,
  addEdge,
  MarkerType,
  Position,
  Handle,
  Panel,
  type Node,
  type Edge,
  type NodeProps,
  type EdgeProps,
  type Connection,
  type OnConnect,
  type OnNodesChange,
  type OnEdgesChange,
  type ReactFlowInstance,
} from '@xyflow/react';
