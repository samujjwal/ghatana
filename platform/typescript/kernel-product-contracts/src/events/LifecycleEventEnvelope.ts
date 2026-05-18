/**
 * LifecycleEventEnvelope - wraps lifecycle events for streaming and queuing.
 *
 * @doc.type module
 * @doc.purpose Lifecycle event envelope and correlation ID contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// LifecycleEventType (alias — canonical source is KernelLifecycleEvent.ts)
// ---------------------------------------------------------------------------

export type LifecycleEventType = string;

// ---------------------------------------------------------------------------
// LifecycleEventEnvelope
// ---------------------------------------------------------------------------

export const LifecycleEventEnvelopeSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  envelopeId: z.string().min(1),
  eventType: z.string().min(1),
  correlationId: z.string().min(1),
  runId: z.string().min(1),
  productId: z.string().min(1),
  source: z.string().min(1),
  publishedAt: z.string().datetime(),
  sequenceNumber: z.number().int().nonnegative().optional(),
  payload: z.unknown(),
});

export type LifecycleEventEnvelope = z.infer<
  typeof LifecycleEventEnvelopeSchema
>;

export function parseLifecycleEventEnvelope(
  input: unknown,
): LifecycleEventEnvelope {
  return LifecycleEventEnvelopeSchema.parse(input);
}
