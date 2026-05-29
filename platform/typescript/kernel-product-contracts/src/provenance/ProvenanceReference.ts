/**
 * Provenance references for runtime truth and artifact intelligence evidence.
 */

import { z } from "zod";

export const ProvenanceSubjectSchema = z
  .object({
    type: z.enum([
      "lifecycle-run",
      "gate-result",
      "artifact-manifest",
      "deployment-manifest",
      "verify-health-report",
      "agent-action",
      "semantic-artifact-output",
    ]),
    id: z.string().trim().min(1),
  })
  .strict();

export const ProvenanceReferenceSchema = z
  .object({
    provenanceId: z.string().trim().min(1),
    subject: ProvenanceSubjectSchema,
    source: z.string().trim().min(1),
    assertion: z.string().trim().min(1),
    confidence: z.number().min(0).max(1),
    evidenceRefs: z.array(z.string().trim().min(1)).min(1),
    privacyClassification: z
      .enum(["public", "internal", "confidential", "restricted"])
      .default("internal"),
    recordedAt: z.string().datetime({ offset: true }),
  })
  .strict();

export type ProvenanceSubject = z.infer<typeof ProvenanceSubjectSchema>;
export type ProvenanceReference = z.infer<typeof ProvenanceReferenceSchema>;

export interface EvidenceRedactionOptions {
  readonly revealSensitive?: boolean;
}

export const EvidenceRedactionOptionsSchema = z
  .object({
    revealSensitive: z.boolean().optional(),
  })
  .strict();

export function validateEvidenceRedactionOptions(
  value: unknown,
): value is EvidenceRedactionOptions {
  return EvidenceRedactionOptionsSchema.safeParse(value).success;
}

export function redactEvidenceRef(
  evidenceRef: string,
  privacyClassification: ProvenanceReference["privacyClassification"],
  options: EvidenceRedactionOptions = {},
): string {
  if (
    options.revealSensitive === true ||
    privacyClassification === "public" ||
    privacyClassification === "internal"
  ) {
    return evidenceRef;
  }
  const lastSegment =
    evidenceRef.split("/").filter(Boolean).at(-1) ?? "evidence";
  return `redacted://${lastSegment}`;
}

export function redactProvenanceReference(
  reference: ProvenanceReference,
  options: EvidenceRedactionOptions = {},
): ProvenanceReference {
  return {
    ...reference,
    evidenceRefs: reference.evidenceRefs.map((evidenceRef) =>
      redactEvidenceRef(evidenceRef, reference.privacyClassification, options),
    ),
  };
}
