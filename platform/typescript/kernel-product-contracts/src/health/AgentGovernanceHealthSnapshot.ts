/**
 * AgentGovernanceHealthSnapshot - health snapshot for agent governance.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for agent governance operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import type { HealthStatus } from "./HealthStatus";

/**
 * Mastery states (aligned with Java MasteryState enum).
 */
export type MasteryState =
  | 'UNKNOWN'
  | 'OBSERVED'
  | 'PRACTICED'
  | 'COMPETENT'
  | 'MASTERED'
  | 'MAINTENANCE_ONLY'
  | 'OBSOLETE'
  | 'RETIRED'
  | 'QUARANTINED';

/**
 * Governance states for mastery items.
 */
export type GovernanceState =
  | 'obsolete'
  | 'quarantined'
  | 'requires-approval'
  | 'requires-verification';

/**
 * Agent governance status.
 */
export interface AgentGovernanceStatus {
  readonly agentId: string;
  readonly status: HealthStatus;
  readonly governanceState?: GovernanceState;
  readonly masteryState?: MasteryState;
  readonly message: string;
  readonly lastEvaluated: string;
}

/**
 * Agent governance health snapshot.
 */
export interface AgentGovernanceHealthSnapshot {
  /**
   * ProductUnit identifier.
   */
  readonly productUnitId: string;

  /**
   * Overall health status.
   */
  readonly status: HealthStatus;

  /**
   * Agent governance statuses.
   */
  readonly agents: readonly AgentGovernanceStatus[];

  /**
   * Snapshot timestamp.
   */
  readonly snapshotAt: string;
}
