import { describe, expect, it } from "vitest";
import { validateGeneratedArtifactProvenance } from "./provenance-policy";
import type { ArtifactManifest } from "@tutorputor/contracts/v1/content-studio";

function generatedArtifact(
  overrides: Partial<ArtifactManifest> = {},
): Pick<ArtifactManifest, "generatedBy" | "generationId" | "generationProvenance"> {
  return {
    generatedBy: "ai",
    generationId: "gen-1",
    generationProvenance: {
      sourcePromptId: "prompt-simulation",
      sourcePromptVersion: "2",
      modelProvider: "openai",
      modelVersion: "edu-author-1",
      retrievedSources: [{ id: "openstax-physics-2.1" }],
      generatedAt: "2026-05-06T10:00:00.000Z",
      confidenceScore: 0.91,
      validationStatus: "passed",
      smeReviewStatus: "approved",
      publishEligibility: "eligible",
    },
    ...overrides,
  };
}

describe("generated artifact provenance policy", () => {
  it("allows human-authored artifacts without generation provenance", () => {
    expect(
      validateGeneratedArtifactProvenance({ generatedBy: "human" }),
    ).toEqual({
      publishable: true,
      errors: [],
    });
  });

  it("allows publish when AI-created artifact provenance is complete and approved", () => {
    expect(validateGeneratedArtifactProvenance(generatedArtifact())).toEqual({
      publishable: true,
      errors: [],
    });
  });

  it("rejects AI-created artifacts without provenance", () => {
    const result = validateGeneratedArtifactProvenance(
      generatedArtifact({
        generationId: undefined,
        generationProvenance: undefined,
      }),
    );

    expect(result.publishable).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "AI-created artifact is missing generationId",
        "AI-created artifact is missing generationProvenance",
      ]),
    );
  });

  it("blocks publish until validation and SME review pass", () => {
    const result = validateGeneratedArtifactProvenance(
      generatedArtifact({
        generationProvenance: {
          ...generatedArtifact().generationProvenance!,
          validationStatus: "failed",
          smeReviewStatus: "pending",
          publishEligibility: "blocked",
        },
      }),
    );

    expect(result.publishable).toBe(false);
    expect(result.errors).toEqual(
      expect.arrayContaining([
        "AI-created artifact validation must pass before publish",
        "AI-created artifact requires approved SME review before publish",
        "AI-created artifact publishEligibility must be eligible",
      ]),
    );
  });
});
