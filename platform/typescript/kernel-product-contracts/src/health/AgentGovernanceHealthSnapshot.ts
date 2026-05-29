/**
 * AgentGovernanceHealthSnapshot - health snapshot for agent governance.
 *
 * @doc.type interface
 * @doc.purpose Health snapshot for agent governance operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Snapshot
 */

import { z } from "zod";
import { HealthStatusSchema, type HealthStatus } from "./HealthStatus.js";

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

export const MasteryStateSchema = z.enum([
  "UNKNOWN",
  "OBSERVED",
  "PRACTICED",
  "COMPETENT",
  "MASTERED",
  "MAINTENANCE_ONLY",
  "OBSOLETE",
  "RETIRED",
  "QUARANTINED",
]);

/**
 * Governance states for mastery items.
 */
export type GovernanceState =
  | 'obsolete'
  | 'quarantined'
  | 'requires-approval'
  | 'requires-verification';

export const GovernanceStateSchema = z.enum([
  "obsolete",
  "quarantined",
  "requires-approval",
  "requires-verification",
]);

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

export const AgentGovernanceStatusSchema = z
  .object({
    agentId: z.string().trim().min(1),
    status: HealthStatusSchema,
    governanceState: GovernanceStateSchema.optional(),
    masteryState: MasteryStateSchema.optional(),
    message: z.string().trim().min(1),
    lastEvaluated: z.string().datetime({ offset: true }),
  })
  .strict();

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

export const AgentGovernanceHealthSnapshotSchema = z
  .object({
    productUnitId: z.string().trim().min(1),
    status: HealthStatusSchema,
    agents: z.array(AgentGovernanceStatusSchema),
    snapshotAt: z.string().datetime({ offset: true }),
  })
  .strict();

export function validateMasteryState(value: unknown): value is MasteryState {
  return MasteryStateSchema.safeParse(value).success;
}

export function validateGovernanceState(
  value: unknown
): value is GovernanceState {
  return GovernanceStateSchema.safeParse(value).success;
}

export function validateAgentGovernanceStatus(
  value: unknown
): value is AgentGovernanceStatus {
  return AgentGovernanceStatusSchema.safeParse(value).success;
}

export function validateAgentGovernanceHealthSnapshot(
  value: unknown
): value is AgentGovernanceHealthSnapshot {
  return AgentGovernanceHealthSnapshotSchema.safeParse(value).success;
}
