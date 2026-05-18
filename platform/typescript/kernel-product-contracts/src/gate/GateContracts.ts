/**
 * GateContracts - canonical gate definition, result manifest, and reference schemas.
 *
 * Extends the minimal ProductGate with full governance gate contracts.
 *
 * @doc.type module
 * @doc.purpose Canonical gate definition and result manifest contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// GateDefinition
// ---------------------------------------------------------------------------

/** Canonical gate kinds. */
export const GATE_KINDS = [
  "policy",
  "compliance",
  "security",
  "artifact",
  "health",
  "approval",
  "test",
  "build",
  "custom",
] as const;

export type GateKind = (typeof GATE_KINDS)[number];

export const GateDefinitionSchema = z.object({
  gateId: z.string().min(1),
  displayName: z.string().min(1),
  kind: z.enum(GATE_KINDS),
  phase: z.string().min(1),
  required: z.boolean(),
  description: z.string().optional(),
  providerId: z.string().optional(),
  policyRef: z.string().optional(),
  tags: z.array(z.string()).optional(),
});

export type GateDefinition = z.infer<typeof GateDefinitionSchema>;

// ---------------------------------------------------------------------------
// GateFailureReason
// ---------------------------------------------------------------------------

/** Discriminated union of gate failure reasons. */
export type GateFailureReason =
  | { readonly kind: "policy-denied"; readonly policyRef: string }
  | { readonly kind: "artifact-missing"; readonly artifactRef: string }
  | { readonly kind: "health-check-failed"; readonly healthRef: string }
  | {
      readonly kind: "approval-not-granted";
      readonly approvalId: string;
      readonly requiredApprovers: readonly string[];
    }
  | { readonly kind: "test-failed"; readonly testRef: string }
  | { readonly kind: "provider-unavailable"; readonly providerId: string }
  | { readonly kind: "timeout"; readonly durationMs: number }
  | { readonly kind: "unknown"; readonly message: string };

// ---------------------------------------------------------------------------
// RequiredGateReference
// ---------------------------------------------------------------------------

export const RequiredGateReferenceSchema = z.object({
  gateId: z.string().min(1),
  phase: z.string().min(1),
  required: z.boolean(),
  providerId: z.string().optional(),
});

export type RequiredGateReference = z.infer<typeof RequiredGateReferenceSchema>;

// ---------------------------------------------------------------------------
// GateResultManifest
// ---------------------------------------------------------------------------

export const GateResultEntrySchema = z.object({
  gateId: z.string().min(1),
  phase: z.string().min(1),
  required: z.boolean(),
  passed: z.boolean(),
  evaluatedAt: z.string().datetime(),
  durationMs: z.number().int().nonnegative(),
  reason: z.string(),
  evidenceRefs: z.array(z.string()),
});

export type GateResultEntry = z.infer<typeof GateResultEntrySchema>;

export const GateResultManifestSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  phase: z.string().min(1),
  overallPassed: z.boolean(),
  gates: z.array(GateResultEntrySchema),
});

export type GateResultManifest = z.infer<typeof GateResultManifestSchema>;

export function parseGateResultManifest(input: unknown): GateResultManifest {
  return GateResultManifestSchema.parse(input);
}
