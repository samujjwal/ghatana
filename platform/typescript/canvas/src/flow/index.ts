/**
 * @fileoverview @ghatana/canvas/flow — Flow/topology canvas subpath.
 *
 * Replaces the former `@ghatana/flow-canvas` standalone package.
 * All product code should migrate imports:
 *   - Before: `import { FlowCanvas } from '@ghatana/flow-canvas'`
 *   - After:  `import { FlowCanvas } from '@ghatana/canvas/flow'`
 *
 * @doc.type module
 * @doc.purpose Flow/graph canvas public API for @ghatana/canvas/flow
 * @doc.layer platform
 */

export { FlowCanvas, type FlowCanvasProps } from './FlowCanvas.js';

export { default as HotTierNode } from './nodes/HotTierNode.js';
export { default as WarmTierNode } from './nodes/WarmTierNode.js';
export { default as ColdTierNode } from './nodes/ColdTierNode.js';
export { default as ArchiveTierNode } from './nodes/ArchiveTierNode.js';
export { default as AgentNode } from './nodes/AgentNode.js';
export { default as PipelineStageNode } from './nodes/PipelineStageNode.js';
export { default as OperatorNode } from './nodes/OperatorNode.js';

export { default as DataFlowEdge } from './edges/DataFlowEdge.js';

export { default as FlowControls, type FlowControlsProps } from './controls/FlowControls.js';

export type {
  NodeStatus,
  NodeMetrics,
  TierNodeData,
  AgentNodeData,
  DataFlowEdgeData,
  PipelineStageNodeData,
  OperatorNodeData,
  HotTierNode as HotTierNodeType,
  WarmTierNode as WarmTierNodeType,
  ColdTierNode as ColdTierNodeType,
  ArchiveTierNode as ArchiveTierNodeType,
  AgentNetworkNode,
  PipelineStageNode as PipelineStageNodeType,
  OperatorNode as OperatorNodeType,
  FlowNode,
  FlowEdge,
} from './types.js';

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
