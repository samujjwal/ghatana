/**
 * ArtifactReferences - canonical artifact reference, fingerprint, and digest schemas.
 *
 * Provides typed reference contracts for artifacts produced during lifecycle execution.
 *
 * @doc.type module
 * @doc.purpose Artifact reference and fingerprint contracts
 * @doc.layer kernel-product-contracts
 * @doc.pattern Contract
 */

import { z } from "zod";

// ---------------------------------------------------------------------------
// ArtifactFingerprint / ArtifactDigest
// ---------------------------------------------------------------------------

/** Supported digest algorithms. */
export const DIGEST_ALGORITHMS = ["sha256", "sha512", "sha384"] as const;

export type DigestAlgorithm = (typeof DIGEST_ALGORITHMS)[number];

export const ArtifactDigestSchema = z.object({
  algorithm: z.enum(DIGEST_ALGORITHMS),
  value: z.string().min(1),
});

export type ArtifactDigest = z.infer<typeof ArtifactDigestSchema>;

export const ArtifactFingerprintSchema = z.object({
  artifactId: z.string().min(1),
  digest: ArtifactDigestSchema,
  generatedAt: z.string().datetime(),
  sizeBytes: z.number().int().nonnegative().optional(),
});

export type ArtifactFingerprint = z.infer<typeof ArtifactFingerprintSchema>;

// ---------------------------------------------------------------------------
// LifecycleArtifactReference
// ---------------------------------------------------------------------------

/** Artifact types recognized by the Kernel. */
export const ARTIFACT_TYPES = [
  "jvm-service",
  "static-web-bundle",
  "docker-image",
  "ipa",
  "aab",
  "sdk-library",
  "domain-pack",
  "agent-runtime",
  "data-pipeline",
  "schema",
  "report",
  "unknown",
] as const;

export type ArtifactType = (typeof ARTIFACT_TYPES)[number];

export const LifecycleArtifactReferenceSchema = z.object({
  artifactId: z.string().min(1),
  artifactType: z.enum(ARTIFACT_TYPES),
  path: z.string().min(1),
  fingerprint: ArtifactFingerprintSchema.optional(),
  producedByStepId: z.string().optional(),
  producedAt: z.string().datetime().optional(),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
});

export type LifecycleArtifactReference = z.infer<
  typeof LifecycleArtifactReferenceSchema
>;

// ---------------------------------------------------------------------------
// ArtifactManifestReference
// ---------------------------------------------------------------------------

export const ArtifactManifestReferenceSchema = z.object({
  schemaVersion: z.literal("1.0.0"),
  runId: z.string().min(1),
  correlationId: z.string().min(1),
  createdAt: z.string().datetime(),
  productId: z.string().min(1),
  phase: z.string().min(1),
  manifestPath: z.string().min(1),
  artifacts: z.array(LifecycleArtifactReferenceSchema),
});

export type ArtifactManifestReference = z.infer<
  typeof ArtifactManifestReferenceSchema
>;

export function parseArtifactManifestReference(
  input: unknown,
): ArtifactManifestReference {
  return ArtifactManifestReferenceSchema.parse(input);
}
