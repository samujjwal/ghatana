/**
 * @fileoverview @ghatana/canvas/flow — type definitions.
 *
 * Absorbed from the former standalone `@ghatana/flow-canvas` package.
 * All product imports should migrate from `@ghatana/flow-canvas` to
 * `@ghatana/canvas/flow`.
 *
 * @doc.type types
 * @doc.purpose Shared node, edge, and flow-graph type definitions
 * @doc.layer platform
 */
import type { Node, Edge } from '@xyflow/react';

// ── Node status ────────────────────────────────────────────────────────────

export type NodeStatus =
  | 'healthy'
  | 'warning'
  | 'error'
  | 'inactive'
  | 'processing'
  | 'pending';

// ── Node metrics ───────────────────────────────────────────────────────────

export interface NodeMetrics {
  /** Events per second throughput */
  throughput?: number;
  /** P99 latency in milliseconds */
  latencyMs?: number;
  /** Error rate 0–1 */
  errorRate?: number;
  /** Queue depth */
  queueDepth?: number;
}

// ── Tier node data ─────────────────────────────────────────────────────────

/** Data for 4-tier EventCloud topology nodes (HOT / WARM / COLD / ARCHIVE). */
export interface TierNodeData extends Record<string, unknown> {
  label: string;
  status?: NodeStatus;
  metrics?: NodeMetrics;
  description?: string;
}

// ── Agent node data ────────────────────────────────────────────────────────

/** Data for AEP agent network nodes. */
export interface AgentNodeData extends Record<string, unknown> {
  label: string;
  agentType: string;
  status?: 'active' | 'idle' | 'error' | 'training';
  capabilities?: string[];
  memoryCount?: number;
}

// ── Edge data ──────────────────────────────────────────────────────────────

/** Data for DataFlowEdge connections. */
export interface DataFlowEdgeData extends Record<string, unknown> {
  label?: string;
  throughput?: number;
  animated?: boolean;
}

// ── Pipeline stage node data ───────────────────────────────────────────────

/** Data for AEP pipeline stage nodes. */
export interface PipelineStageNodeData extends Record<string, unknown> {
  label: string;
  stageType?: 'INGEST' | 'TRANSFORM' | 'ENRICH' | 'FILTER' | 'ROUTE' | 'SINK' | string;
  status?: 'running' | 'idle' | 'error' | 'warning';
  operatorCount?: number;
  hasMultipleOutputs?: boolean;
  description?: string;
}

// ── Operator node data ─────────────────────────────────────────────────────

/** Data for AEP pipeline operator nodes. */
export interface OperatorNodeData extends Record<string, unknown> {
  label: string;
  operatorType?: 'MAP' | 'FILTER' | 'JOIN' | 'AGGREGATE' | 'SPLIT' | 'MERGE' | 'VALIDATE' | 'ENRICH' | 'AI' | string;
  configured?: boolean;
  status?: 'running' | 'idle' | 'error' | 'warning';
  inputSchema?: string;
  outputSchema?: string;
}

// ── Convenience aliases ────────────────────────────────────────────────────

export type HotTierNode = Node<TierNodeData, 'hotTier'>;
export type WarmTierNode = Node<TierNodeData, 'warmTier'>;
export type ColdTierNode = Node<TierNodeData, 'coldTier'>;
export type ArchiveTierNode = Node<TierNodeData, 'archiveTier'>;
export type AgentNetworkNode = Node<AgentNodeData, 'agent'>;
export type PipelineStageNode = Node<PipelineStageNodeData, 'pipelineStage'>;
export type OperatorNode = Node<OperatorNodeData, 'operator'>;

export type FlowNode =
  | HotTierNode
  | WarmTierNode
  | ColdTierNode
  | ArchiveTierNode
  | AgentNetworkNode
  | PipelineStageNode
  | OperatorNode
  | Node;

export type FlowEdge = Edge<DataFlowEdgeData>;
