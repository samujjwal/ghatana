/**
 * KernelPreviewSecurityEvent - event contract for preview security events.
 *
 * @doc.type interface
 * @doc.purpose Event contract for preview security operations
 * @doc.layer kernel-product-contracts
 * @doc.pattern Event
 */

import { z } from "zod";
import type { KernelEventMetadata } from "./KernelLifecycleEvent.js";
import { KernelEventMetadataSchema } from "./KernelLifecycleEvent.js";

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

export const PreviewSecurityEventPayloadSchema = z
  .object({
    checkId: z.string().trim().min(1),
    checkType: z.string().trim().min(1),
    result: z.string().trim().min(1),
    severity: z.string().trim().min(1),
    vulnerabilityCount: z.number().int().nonnegative(),
    findings: z.array(
      z
        .object({
          id: z.string().trim().min(1),
          severity: z.string().trim().min(1),
          description: z.string().trim().min(1),
        })
        .strict()
    ),
  })
  .strict();

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

export const KernelPreviewSecurityEventSchema = z
  .object({
    metadata: KernelEventMetadataSchema,
    payload: PreviewSecurityEventPayloadSchema,
  })
  .strict();

export function validatePreviewSecurityEventPayload(
  value: unknown
): value is PreviewSecurityEventPayload {
  return PreviewSecurityEventPayloadSchema.safeParse(value).success;
}

export function validateKernelPreviewSecurityEvent(
  value: unknown
): value is KernelPreviewSecurityEvent {
  return KernelPreviewSecurityEventSchema.safeParse(value).success;
}
