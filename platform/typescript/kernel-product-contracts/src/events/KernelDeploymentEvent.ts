/**
 * KernelDeploymentEvent - event contract for deployment events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for deployment operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent";

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
