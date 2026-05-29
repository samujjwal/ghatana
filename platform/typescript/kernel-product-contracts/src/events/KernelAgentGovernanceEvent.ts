/**
 * KernelAgentGovernanceEvent - event contract for agent governance events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for agent governance operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

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
 * Agent governance event payload.
 */
export interface AgentGovernanceEventPayload {
  /**
   * Agent identifier.
   */
  readonly agentId: string;

  /**
   * Governance action type.
   */
  readonly actionType: string;

  /**
   * Governance decision (allowed/denied).
   */
  readonly decision: string;

  /**
   * Reason for governance decision.
   */
  readonly reason: string;

  /**
   * Mastery state at time of decision.
   */
  readonly masteryState?: MasteryState;

  /**
   * Execution mode selected.
   */
  readonly executionMode?: string;

  /**
   * Evidence references.
   */
  readonly evidenceRefs?: readonly string[];

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

export const AgentGovernanceEventPayloadSchema = z
  .object({
    agentId: z.string().trim().min(1),
    actionType: z.string().trim().min(1),
    decision: z.string().trim().min(1),
    reason: z.string().trim().min(1),
    masteryState: MasteryStateSchema.optional(),
    executionMode: z.string().trim().min(1).optional(),
    evidenceRefs: z.array(z.string().trim().min(1)).optional(),
  })
  .catchall(z.unknown());

/**
 * Agent governance event.
 */
export interface KernelAgentGovernanceEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Agent governance-specific payload.
   */
  readonly payload: AgentGovernanceEventPayload;
}

export const KernelAgentGovernanceEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: AgentGovernanceEventPayloadSchema,
  })
  .strict();

export function validateMasteryState(value: unknown): value is MasteryState {
  return MasteryStateSchema.safeParse(value).success;
}

export function validateAgentGovernanceEventPayload(
  value: unknown
): value is AgentGovernanceEventPayload {
  return AgentGovernanceEventPayloadSchema.safeParse(value).success;
}

export function validateKernelAgentGovernanceEvent(
  value: unknown
): value is KernelAgentGovernanceEvent {
  return KernelAgentGovernanceEventSchema.safeParse(value).success;
}
