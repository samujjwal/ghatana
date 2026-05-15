/**
 * KernelAgentGovernanceEvent - event contract for agent governance events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for agent governance operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";

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
