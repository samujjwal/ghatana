/**
 * KernelGateEvent - event contract for gate evaluation events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for gate evaluation operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

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

export const GateEventPayloadSchema = z
  .object({
    gateId: z.string().trim().min(1),
    passed: z.boolean(),
    reason: z.string().trim().min(1),
    evidence: z.array(z.string().trim().min(1)),
    duration: z.number().nonnegative(),
  })
  .catchall(z.unknown());

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

export const KernelGateEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: GateEventPayloadSchema,
  })
  .strict();

export function validateGateEventPayload(
  value: unknown
): value is GateEventPayload {
  return GateEventPayloadSchema.safeParse(value).success;
}

export function validateKernelGateEvent(
  value: unknown
): value is KernelGateEvent {
  return KernelGateEventSchema.safeParse(value).success;
}
