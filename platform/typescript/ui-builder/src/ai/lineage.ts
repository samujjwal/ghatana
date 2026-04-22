/**
 * @fileoverview AI action lineage — records provenance, confidence, reason,
 * reversibility, and review state for every AI action applied to a
 * BuilderDocument.  This is the canonical lineage type used by all builder
 * AI hooks to satisfy the transparency and audit requirements in the
 * live-builder architecture.
 */

import type { NodeId } from '../core/types.js';

// ============================================================================
// Core Lineage Types
// ============================================================================

/** Identifies which AI hook produced the action. */
export type AIHookKind =
  | 'missing-prop-repair'
  | 'token-normalization'
  | 'auto-layout-cleanup'
  | 'accessibility-fix'
  | 'responsive-adjustment'
  | 'property-completion'
  | 'action-wiring';

/** Review state of an AI action. */
export type AIReviewState =
  | 'pending'        // Applied but awaiting user review
  | 'accepted'       // User accepted the change
  | 'rejected'       // User rejected (action will be rolled back)
  | 'auto-accepted'; // Policy allows auto-acceptance at this confidence level

/** Captures the full provenance of a single AI-applied change. */
export interface AIActionLineage {
  /** Unique ID for this specific action instance. */
  readonly actionId: string;
  /** Which hook produced this action. */
  readonly hookKind: AIHookKind;
  /** Human-readable description of what was changed and why. */
  readonly reason: string;
  /** Confidence score in [0.0, 1.0]. */
  readonly confidence: number;
  /** Whether this action can be undone via the builder operation stack. */
  readonly reversible: boolean;
  /** Current review state. */
  reviewState: AIReviewState;
  /** Nodes that were directly modified by this action. */
  readonly affectedNodeIds: readonly NodeId[];
  /** Correlation ID linking this action to a platform event (if emitted). */
  readonly correlationId?: string;
  /** ISO timestamp of when the action was applied. */
  readonly appliedAt: string;
  /** Evidence snippets or references that informed the action. */
  readonly evidence: readonly string[];
}

// ============================================================================
// Lineage Tracker
// ============================================================================

/**
 * In-memory lineage tracker for builder AI actions within a session.
 * Designed to be scoped to a single editing session — not persisted across
 * reloads.  Product code that needs durability should sync this to a
 * product-owned state store (e.g., `yappc-state`).
 */
export class AIActionLineageTracker {
  private readonly entries = new Map<string, AIActionLineage>();

  /** Records a new lineage entry. */
  record(entry: AIActionLineage): void {
    this.entries.set(entry.actionId, entry);
  }

  /** Transitions the review state of an action. Returns false if not found. */
  setReviewState(actionId: string, state: AIReviewState): boolean {
    const entry = this.entries.get(actionId);
    if (!entry) return false;
    entry.reviewState = state;
    return true;
  }

  /** Returns all lineage entries for the given node, or all entries if no node filter. */
  getByNode(nodeId: NodeId): readonly AIActionLineage[] {
    const result: AIActionLineage[] = [];
    for (const entry of this.entries.values()) {
      if (entry.affectedNodeIds.includes(nodeId)) {
        result.push(entry);
      }
    }
    return result;
  }

  /** Returns all recorded lineage entries in insertion order. */
  getAll(): readonly AIActionLineage[] {
    return [...this.entries.values()];
  }

  /** Returns only pending entries (applied but not yet user-reviewed). */
  getPending(): readonly AIActionLineage[] {
    return this.getAll().filter((e) => e.reviewState === 'pending');
  }

  /** Returns all reversible entries (can be rolled back). */
  getReversible(): readonly AIActionLineage[] {
    return this.getAll().filter((e) => e.reversible);
  }

  /** Clears all lineage entries. */
  clear(): void {
    this.entries.clear();
  }
}

// ============================================================================
// Factory
// ============================================================================

/** Creates a new lineage entry with a generated actionId and timestamp. */
export function createLineageEntry(
  hookKind: AIHookKind,
  reason: string,
  confidence: number,
  affectedNodeIds: readonly NodeId[],
  options: {
    reversible?: boolean;
    reviewState?: AIReviewState;
    correlationId?: string;
    evidence?: readonly string[];
  } = {},
): AIActionLineage {
  return {
    actionId: `ai-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`,
    hookKind,
    reason,
    confidence,
    reversible: options.reversible ?? true,
    reviewState: options.reviewState ?? 'pending',
    affectedNodeIds,
    correlationId: options.correlationId,
    appliedAt: new Date().toISOString(),
    evidence: options.evidence ?? [],
  };
}
