/**
 * Memory domain types for the AEP agent memory subsystem.
 *
 * These mirror the GAA memory model: episodic → semantic → procedural
 * consolidation pipeline. Used by AgentDetailPage and the learning API.
 *
 * @doc.type types
 * @doc.purpose AEP agent memory domain types
 * @doc.layer frontend
 */

// ─── Memory item types ───────────────────────────────────────────────

export type MemoryItemType =
  | 'EPISODIC'
  | 'SEMANTIC'
  | 'PROCEDURAL'
  | 'PREFERENCE'
  | 'WORKING'
  | 'ARTIFACT';

export type ValidityStatus = 'VALID' | 'INVALID' | 'UNCERTAIN' | 'DEPRECATED';

export interface MemoryItem {
  id: string;
  agentId: string;
  tenantId: string;
  type: MemoryItemType;
  content: string;
  /** 0.0–1.0 confidence score */
  confidence: number;
  validityStatus: ValidityStatus;
  /** Incremented each time the item is retrieved and reinforced */
  entrenchmentCount: number;
  createdAt: string;
  updatedAt: string;
  expiresAt?: string;
  tags?: string[];
}

// ─── Episode ─────────────────────────────────────────────────────────

export type EpisodeOutcome = 'SUCCESS' | 'FAILURE' | 'TIMEOUT' | 'CANCELLED';

export interface Episode {
  id: string;
  agentId: string;
  tenantId: string;
  turnId: string;
  input: string;
  output: string;
  outcome: EpisodeOutcome;
  durationMs: number;
  /** Patterns or facts extracted during REFLECT */
  extractedFacts?: string[];
  createdAt: string;
}

// ─── Procedure / Policy ──────────────────────────────────────────────

export type PolicyStatus =
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'ACTIVE'
  | 'DEPRECATED';

export interface LearnedPolicy {
  id: string;
  agentId: string;
  tenantId: string;
  name: string;
  description: string;
  /** YAML or JSON procedure definition */
  definition: string;
  /** 0.0–1.0 confidence from pattern induction */
  confidence: number;
  status: PolicyStatus;
  /** ISO timestamp of the latest approval/rejection decision */
  decidedAt?: string;
  createdAt: string;
  updatedAt: string;
}

// ─── Memory summary (used in AgentDetailPage) ────────────────────────

export interface AgentMemorySummary {
  agentId: string;
  tenantId: string;
  total: number;
  byType: Record<MemoryItemType, number>;
  timestamp: string;
}
