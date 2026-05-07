import type {
  ArtifactManifest,
  GeneratedContentProvenance,
} from "@tutorputor/contracts/v1/content-studio";

export interface GeneratedArtifactProvenanceResult {
  publishable: boolean;
  errors: string[];
}

export function validateGeneratedArtifactProvenance(
  artifact: Pick<
    ArtifactManifest,
    "generatedBy" | "generationId" | "generationProvenance"
  >,
): GeneratedArtifactProvenanceResult {
  if (artifact.generatedBy !== "ai" && artifact.generatedBy !== "hybrid") {
    return { publishable: true, errors: [] };
  }

  const errors: string[] = [];

  if (!artifact.generationId) {
    errors.push("AI-created artifact is missing generationId");
  }

  if (!artifact.generationProvenance) {
    errors.push("AI-created artifact is missing generationProvenance");
    return { publishable: false, errors };
  }

  errors.push(...validateProvenanceFields(artifact.generationProvenance));

  if (artifact.generationProvenance.validationStatus !== "passed") {
    errors.push("AI-created artifact validation must pass before publish");
  }

  if (artifact.generationProvenance.smeReviewStatus !== "approved") {
    errors.push("AI-created artifact requires approved SME review before publish");
  }

  if (artifact.generationProvenance.publishEligibility !== "eligible") {
    errors.push("AI-created artifact publishEligibility must be eligible");
  }

  return {
    publishable: errors.length === 0,
    errors,
  };
}

function validateProvenanceFields(
  provenance: GeneratedContentProvenance,
): string[] {
  const errors: string[] = [];
  const requiredStrings: Array<[keyof GeneratedContentProvenance, string]> = [
    ["sourcePromptId", "sourcePromptId"],
    ["sourcePromptVersion", "sourcePromptVersion"],
    ["modelProvider", "modelProvider"],
    ["modelVersion", "modelVersion"],
    ["generatedAt", "generatedAt"],
  ];

  for (const [field, label] of requiredStrings) {
    if (typeof provenance[field] !== "string" || provenance[field].trim() === "") {
      errors.push(`AI-created artifact provenance is missing ${label}`);
    }
  }

  if (!Array.isArray(provenance.retrievedSources)) {
    errors.push("AI-created artifact provenance retrievedSources must be an array");
  }

  if (
    typeof provenance.confidenceScore !== "number" ||
    provenance.confidenceScore < 0 ||
    provenance.confidenceScore > 1
  ) {
    errors.push("AI-created artifact confidenceScore must be between 0 and 1");
  }

  return errors;
}
