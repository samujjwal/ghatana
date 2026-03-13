/**
 * @ghatana/flow-canvas — Shared type definitions.
 *
 * @doc.type types
 * @doc.purpose Type definitions for FlowCanvas nodes and edges
 * @doc.layer shared
 */
import type { Node, Edge } from '@xyflow/react';

// ==================== Node Status ====================

export type NodeStatus =
  | 'healthy'
  | 'warning'
  | 'error'
  | 'inactive'
  | 'processing'
  | 'pending';

// ==================== Node Metrics ====================

export interface NodeMetrics {
  /** Events per second throughput */
  throughput?: number;
  /** P99 latency in milliseconds */
  latencyMs?: number;
  /** Error rate 0-1 */
  errorRate?: number;
  /** Queue depth */
  queueDepth?: number;
}

// ==================== Tier Node Data ====================

/**
 * Data for 4-tier EventCloud topology nodes (HOT / WARM / COLD / ARCHIVE).
 */
export interface TierNodeData extends Record<string, unknown> {
  /** Display label */
  label: string;
  /** Current operational status */
  status?: NodeStatus;
  /** Live metrics */
  metrics?: NodeMetrics;
  /** Description shown in tooltip or detail panel */
  description?: string;
}

// ==================== Agent Node Data ====================

/**
 * Data for AEP agent network nodes.
 */
export interface AgentNodeData extends Record<string, unknown> {
  /** Agent display name */
  label: string;
  /** Agent type (LLM, RULE, ML, etc.) */
  agentType: string;
  /** Agent operational status */
  status?: 'active' | 'idle' | 'error' | 'training';
  /** Capability tags */
  capabilities?: string[];
  /** Total memory item count */
  memoryCount?: number;
}

// ==================== Edge Data ====================

/**
 * Data for DataFlowEdge connections.
 */
export interface DataFlowEdgeData extends Record<string, unknown> {
  /** Optional edge label */
  label?: string;
  /** Throughput for this connection (events/sec) */
  throughput?: number;
  /** Enable animated dash flow. Default: true. */
  animated?: boolean;
}

// ==================== Pipeline Stage Node Data ====================

/**
 * Data for AEP pipeline stage nodes (INGEST / TRANSFORM / ENRICH / FILTER / ROUTE / SINK).
 */
export interface PipelineStageNodeData extends Record<string, unknown> {
  /** Stage display name */
  label: string;
  /** Stage type classification (affects color and icon) */
  stageType?: 'INGEST' | 'TRANSFORM' | 'ENRICH' | 'FILTER' | 'ROUTE' | 'SINK' | string;
  /** Operational status of the stage */
  status?: 'running' | 'idle' | 'error' | 'warning';
  /** Number of operators configured in this stage */
  operatorCount?: number;
  /** When true, renders a BOTTOM source handle for fan-out routing */
  hasMultipleOutputs?: boolean;
  /** Short description shown below the label */
  description?: string;
}

// ==================== Operator Node Data ====================

/**
 * Data for AEP pipeline operator nodes (MAP / FILTER / JOIN / AGGREGATE / etc.).
 */
export interface OperatorNodeData extends Record<string, unknown> {
  /** Operator display name */
  label: string;
  /** Operator type classification */
  operatorType?: 'MAP' | 'FILTER' | 'JOIN' | 'AGGREGATE' | 'SPLIT' | 'MERGE' | 'VALIDATE' | 'ENRICH' | 'AI' | string;
  /** Whether the operator has been fully configured */
  configured?: boolean;
  /** Operational status */
  status?: 'running' | 'idle' | 'error' | 'warning';
  /** Human-readable input schema name (e.g., "EventProto") */
  inputSchema?: string;
  /** Human-readable output schema name (e.g., "EnrichedEvent") */
  outputSchema?: string;
}

// ==================== Convenience Type Aliases ====================

/** A HOT-tier topology node */
export type HotTierNode = Node<TierNodeData, 'hotTier'>;
/** A WARM-tier topology node */
export type WarmTierNode = Node<TierNodeData, 'warmTier'>;
/** A COLD-tier topology node */
export type ColdTierNode = Node<TierNodeData, 'coldTier'>;
/** An ARCHIVE-tier topology node */
export type ArchiveTierNode = Node<TierNodeData, 'archiveTier'>;
/** An AEP agent network node */
export type AgentNetworkNode = Node<AgentNodeData, 'agent'>;
/** An AEP pipeline stage node */
export type PipelineStageNode = Node<PipelineStageNodeData, 'pipelineStage'>;
/** An AEP pipeline operator node */
export type OperatorNode = Node<OperatorNodeData, 'operator'>;

/** Any topology node understood by FlowCanvas */
export type FlowNode =
  | HotTierNode
  | WarmTierNode
  | ColdTierNode
  | ArchiveTierNode
  | AgentNetworkNode
  | PipelineStageNode
  | OperatorNode
  | Node;

/** A data-flow edge */
export type FlowEdge = Edge<DataFlowEdgeData>;
