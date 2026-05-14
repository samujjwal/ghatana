/**
 * KernelPreviewSecurityEvent - event contract for preview security events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for preview security operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import type { KernelEventMetadata } from "./KernelLifecycleEvent";

/**
 * Preview security event payload.
 */
export interface PreviewSecurityEventPayload {
  /**
   * Security check identifier.
   */
  readonly checkId: string;

  /**
   * Security check type (e.g., "dependency-scan", "sast", "container-scan").
   */
  readonly checkType: string;

  /**
   * Security check result (passed/failed).
   */
  readonly result: string;

  /**
   * Severity level (critical, high, medium, low, info).
   */
  readonly severity: string;

  /**
   * Vulnerability count.
   */
  readonly vulnerabilityCount: number;

  /**
   * Security findings.
   */
  readonly findings: readonly {
    readonly id: string;
    readonly severity: string;
    readonly description: string;
  }[];
}

/**
 * Preview security event.
 */
export interface KernelPreviewSecurityEvent {
  /**
   * Event metadata.
   */
  readonly metadata: KernelEventMetadata;

  /**
   * Preview security-specific payload.
   */
  readonly payload: PreviewSecurityEventPayload;
}
