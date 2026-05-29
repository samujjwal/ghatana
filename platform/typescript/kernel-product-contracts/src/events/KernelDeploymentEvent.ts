/**
 * KernelDeploymentEvent - event contract for deployment events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

/**
 * Deployment event payload.
 */
export interface DeploymentEventPayload {
  /**
   * Deployment identifier.
   */
  readonly deploymentId: string;

  /**
   * Target environment.
   */
  readonly environment: string;

  /**
   * Deployment status.
   */
  readonly status: string;

  /**
   * Deployed artifact identifiers.
   */
  readonly artifactIds: readonly string[];

  /**
   * Deployment endpoints (URLs, service names, etc.).
   */
  readonly endpoints: readonly string[];

  /**
   * Deployment duration in milliseconds.
   */
  readonly duration: number;

  /**
   * Index signature for additional properties.
   */
  readonly [key: string]: unknown;
}

export const DeploymentEventPayloadSchema = z
  .object({
    deploymentId: z.string().trim().min(1),
    environment: z.string().trim().min(1),
    status: z.string().trim().min(1),
    artifactIds: z.array(z.string().trim().min(1)),
    endpoints: z.array(z.string().trim().min(1)),
    duration: z.number().nonnegative(),
  })
  .catchall(z.unknown());

/**
 * Deployment event.
 */
export interface KernelDeploymentEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Deployment-specific payload.
   */
  readonly payload: DeploymentEventPayload;
}

export const KernelDeploymentEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: DeploymentEventPayloadSchema,
  })
  .strict();

export function validateDeploymentEventPayload(
  value: unknown
): value is DeploymentEventPayload {
  return DeploymentEventPayloadSchema.safeParse(value).success;
}

export function validateKernelDeploymentEvent(
  value: unknown
): value is KernelDeploymentEvent {
  return KernelDeploymentEventSchema.safeParse(value).success;
}
