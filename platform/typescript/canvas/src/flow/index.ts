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

import _HotTierNode from './nodes/HotTierNode.js';
import _WarmTierNode from './nodes/WarmTierNode.js';
import _ColdTierNode from './nodes/ColdTierNode.js';
import _ArchiveTierNode from './nodes/ArchiveTierNode.js';
import _AgentNode from './nodes/AgentNode.js';
import _PipelineStageNode from './nodes/PipelineStageNode.js';
import _OperatorNode from './nodes/OperatorNode.js';

export const HotTierNode = _HotTierNode;
export const WarmTierNode = _WarmTierNode;
export const ColdTierNode = _ColdTierNode;
export const ArchiveTierNode = _ArchiveTierNode;
export const AgentNode = _AgentNode;
export const PipelineStageNode = _PipelineStageNode;
export const OperatorNode = _OperatorNode;

import _DataFlowEdge from './edges/DataFlowEdge.js';
export const DataFlowEdge = _DataFlowEdge;

import _FlowControls from './controls/FlowControls.js';
export const FlowControls = _FlowControls;
export type { FlowControlsProps } from './controls/FlowControls.js';

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
