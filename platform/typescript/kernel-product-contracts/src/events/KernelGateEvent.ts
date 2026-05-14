/**
 * KernelGateEvent - event contract for gate evaluation events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for gate evaluation operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent";

/**
 * Gate evaluation event payload.
 */
export interface GateEventPayload {
  /**
   * Gate identifier.
   */
  readonly gateId: string;

  /**
   * Gate evaluation result (passed/failed).
   */
  readonly passed: boolean;

  /**
   * Reason for gate decision.
   */
  readonly reason: string;

  /**
   * Evidence collected during gate evaluation.
   */
  readonly evidence: readonly string[];

  /**
   * Gate evaluation duration in milliseconds.
   */
  readonly duration: number;

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

/**
 * Gate evaluation event.
 */
export interface KernelGateEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Gate-specific payload.
   */
  readonly payload: GateEventPayload;
}
