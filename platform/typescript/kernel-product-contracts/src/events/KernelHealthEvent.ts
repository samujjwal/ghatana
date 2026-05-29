/**
 * KernelHealthEvent - event contract for health check events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for health check operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

/**
 * Health check event payload.
 */
export interface HealthEventPayload {
  /**
   * Check identifier.
   */
  readonly checkId: string;

  /**
   * Check name.
   */
  readonly checkName: string;

  /**
   * Health status.
   */
  readonly status: "healthy" | "degraded" | "blocked" | "failed" | "skipped" | "unknown";

  /**
   * Health check message.
   */
  readonly message: string;

  /**
   * Deployment identifier (if applicable).
   */
  readonly deploymentId?: string;

  /**
   * Environment (if applicable).
   */
  readonly environment?: string;

  /**
   * Check duration in milliseconds.
   */
  readonly duration: number;

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

const HealthEventStatusSchema = z.enum([
  "healthy",
  "degraded",
  "blocked",
  "failed",
  "skipped",
  "unknown",
]);

export const HealthEventPayloadSchema = z
  .object({
    checkId: z.string().trim().min(1),
    checkName: z.string().trim().min(1),
    status: HealthEventStatusSchema,
    message: z.string().trim().min(1),
    deploymentId: z.string().trim().min(1).optional(),
    environment: z.string().trim().min(1).optional(),
    duration: z.number().nonnegative(),
  })
  .catchall(z.unknown());

/**
 * Health check event.
 */
export interface KernelHealthEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Health-specific payload.
   */
  readonly payload: HealthEventPayload;
}

export const KernelHealthEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: HealthEventPayloadSchema,
  })
  .strict();

export function validateHealthEventPayload(
  value: unknown
): value is HealthEventPayload {
  return HealthEventPayloadSchema.safeParse(value).success;
}

export function validateKernelHealthEvent(
  value: unknown
): value is KernelHealthEvent {
  return KernelHealthEventSchema.safeParse(value).success;
}
