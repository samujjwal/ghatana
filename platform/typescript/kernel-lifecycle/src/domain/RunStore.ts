/**
 * Run store - durable storage for lifecycle execution history.
 *
 * This module provides contracts for persisting and querying lifecycle run history,
 * enabling replay-safe and queryable run history.
 *
 * @doc.type module
 * @doc.purpose Run store contracts for durable execution history
 * @doc.layer platform
 * @doc.pattern Repository
 */

import type { PhaseGraph } from "./PhaseGraph.js";
import type { ProductLifecycleResult } from "./ProductLifecyclePhase.js";

/**
 * Run record - persisted execution record.
 */
export interface RunRecord {
  /**
   * Schema version for contract compatibility.
   */
  readonly schemaVersion: "1.0.0";

  /**
   * Product identifier.
   */
  readonly productId: string;

  /**
   * Run identifier.
   */
  readonly runId: string;

  /**
   * Correlation identifier for tracing.
   */
  readonly correlationId?: string;

  /**
   * Lifecycle phase executed.
   */
  readonly phase: string;

  /**
   * Execution status.
   */
  readonly status: "running" | "succeeded" | "failed" | "skipped" | "blocked";

  /**
   * Timestamp when the run started.
   */
  readonly startedAt: string;

  /**
   * Timestamp when the run completed (null if still running).
   */
  readonly completedAt?: string;

  /**
   * Duration in milliseconds (null if still running).
   */
  readonly durationMs?: number;

  /**
   * Phase graph snapshot for this run.
   */
  readonly phaseGraph?: PhaseGraph;

  /**
   * Lifecycle result (null if still running).
   */
  readonly result?: ProductLifecycleResult;

  /**
   * Metadata about the run.
   */
  readonly metadata: RunMetadata;

  /**
   * Timestamp when the record was last updated.
   */
  readonly updatedAt: string;
}

/**
 * Run metadata.
 */
export interface RunMetadata {
  /**
   * Lifecycle profile used.
   */
  readonly lifecycleProfile?: string;

  /**
   * Provider mode used.
   */
  readonly providerMode?: "bootstrap" | "platform";

  /**
   * Environment for the run.
   */
  readonly environment?: string;

  /**
   * Source reference.
   */
  readonly sourceRef?: string;

  /**
   * Product unit reference.
   */
  readonly productUnitRef?: string;

  /**
   * Trigger for the run (e.g., "manual", "ci", "scheduled").
   */
  readonly trigger?: string;

  /**
   * User who initiated the run (if applicable).
   */
  readonly initiatedBy?: string;
}

/**
 * Run query filter.
 */
export interface RunQueryFilter {
  /**
   * Product identifier to filter by.
   */
  readonly productId?: string;

  /**
   * Lifecycle phase to filter by.
   */
  readonly phase?: string;

  /**
   * Status to filter by.
   */
  readonly status?: RunRecord["status"];

  /**
   * Minimum start timestamp (ISO string).
   */
  readonly startedAfter?: string;

  /**
   * Maximum start timestamp (ISO string).
   */
  readonly startedBefore?: string;

  /**
   * Correlation identifier to filter by.
   */
  readonly correlationId?: string;

  /**
   * Lifecycle profile to filter by.
   */
  readonly lifecycleProfile?: string;

  /**
   * Provider mode to filter by.
   */
  readonly providerMode?: RunMetadata["providerMode"];

  /**
   * Environment to filter by.
   */
  readonly environment?: string;

  /**
   * Source reference to filter by.
   */
  readonly sourceRef?: string;

  /**
   * Limit the number of results.
   */
  readonly limit?: number;

  /**
   * Offset for pagination.
   */
  readonly offset?: number;
}

/**
 * Run query result.
 */
export interface RunQueryResult {
  /**
   * Matching run records.
   */
  readonly runs: readonly RunRecord[];

  /**
   * Total count of matching runs (before limit/offset).
   */
  readonly totalCount: number;
}

/**
 * Run store interface - abstracts durable storage for run history.
 */
export interface RunStore {
  /**
   * Create a new run record.
   */
  createRun(record: RunRecord): Promise<void>;

  /**
   * Update an existing run record.
   */
  updateRun(runId: string, updates: Partial<RunRecord>): Promise<void>;

  /**
   * Get a run record by run ID.
   */
  getRun(runId: string): Promise<RunRecord | null>;

  /**
   * Query run records with optional filters.
   */
  queryRuns(filter: RunQueryFilter): Promise<RunQueryResult>;

  /**
   * Get the latest run for a product and phase.
   */
  getLatestRun(productId: string, phase: string): Promise<RunRecord | null>;

  /**
   * Get running runs for a product.
   */
  getRunningRuns(productId: string): Promise<readonly RunRecord[]>;

  /**
   * Delete a run record.
   */
  deleteRun(runId: string): Promise<void>;

  /**
   * Delete runs older than a given timestamp.
   */
  deleteRunsOlderThan(timestamp: string): Promise<number>;
}

/**
 * In-memory run store implementation for testing.
 */
export class InMemoryRunStore implements RunStore {
  private readonly records: Map<string, RunRecord> = new Map();

  async createRun(record: RunRecord): Promise<void> {
    this.records.set(record.runId, record);
  }

  async updateRun(runId: string, updates: Partial<RunRecord>): Promise<void> {
    const existing = this.records.get(runId);
    if (!existing) {
      throw new Error(`Run record not found: ${runId}`);
    }
    this.records.set(runId, { ...existing, ...updates, updatedAt: new Date().toISOString() });
  }

  async getRun(runId: string): Promise<RunRecord | null> {
    return this.records.get(runId) ?? null;
  }

  async queryRuns(filter: RunQueryFilter): Promise<RunQueryResult> {
    let matches = Array.from(this.records.values());

    if (filter.productId) {
      matches = matches.filter((r) => r.productId === filter.productId);
    }
    if (filter.phase) {
      matches = matches.filter((r) => r.phase === filter.phase);
    }
    if (filter.status) {
      matches = matches.filter((r) => r.status === filter.status);
    }
    if (filter.startedAfter) {
      matches = matches.filter((r) => r.startedAt >= filter.startedAfter!);
    }
    if (filter.startedBefore) {
      matches = matches.filter((r) => r.startedAt <= filter.startedBefore!);
    }
    if (filter.correlationId) {
      matches = matches.filter((r) => r.correlationId === filter.correlationId);
    }
    if (filter.lifecycleProfile) {
      matches = matches.filter((r) => r.metadata.lifecycleProfile === filter.lifecycleProfile);
    }
    if (filter.providerMode) {
      matches = matches.filter((r) => r.metadata.providerMode === filter.providerMode);
    }
    if (filter.environment) {
      matches = matches.filter((r) => r.metadata.environment === filter.environment);
    }
    if (filter.sourceRef) {
      matches = matches.filter((r) => r.metadata.sourceRef === filter.sourceRef);
    }

    // Sort by startedAt descending (most recent first)
    matches.sort((a, b) => b.startedAt.localeCompare(a.startedAt));

    const totalCount = matches.length;

    if (filter.offset) {
      matches = matches.slice(filter.offset);
    }
    if (filter.limit) {
      matches = matches.slice(0, filter.limit);
    }

    return { runs: matches, totalCount };
  }

  async getLatestRun(productId: string, phase: string): Promise<RunRecord | null> {
    const matches = Array.from(this.records.values())
      .filter((r) => r.productId === productId && r.phase === phase)
      .sort((a, b) => b.startedAt.localeCompare(a.startedAt));

    return matches[0] ?? null;
  }

  async getRunningRuns(productId: string): Promise<readonly RunRecord[]> {
    return Array.from(this.records.values())
      .filter((r) => r.productId === productId && r.status === "running")
      .sort((a, b) => b.startedAt.localeCompare(a.startedAt));
  }

  async deleteRun(runId: string): Promise<void> {
    this.records.delete(runId);
  }

  async deleteRunsOlderThan(timestamp: string): Promise<number> {
    let count = 0;
    for (const [runId, record] of this.records.entries()) {
      if (record.startedAt < timestamp) {
        this.records.delete(runId);
        count++;
      }
    }
    return count;
  }

  /**
   * Clear all records (for testing).
   */
  clear(): void {
    this.records.clear();
  }
}
