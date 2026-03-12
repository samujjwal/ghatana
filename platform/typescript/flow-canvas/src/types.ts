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

/** Any topology node understood by FlowCanvas */
export type FlowNode =
  | HotTierNode
  | WarmTierNode
  | ColdTierNode
  | ArchiveTierNode
  | AgentNetworkNode
  | Node;

/** A data-flow edge */
export type FlowEdge = Edge<DataFlowEdgeData>;
