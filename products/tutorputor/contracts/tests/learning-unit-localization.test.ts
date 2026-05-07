import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, expectTypeOf, it } from "vitest";
import type { LearningUnit, LocalizationConfig, LocalizedTextResource } from "../v1/learning-unit";

const learningUnitSchema = JSON.parse(
  readFileSync(join(import.meta.dirname, "../v1/learning-unit.schema.json"), "utf8"),
) as {
  required: string[];
  definitions: Record<string, { required?: string[] }>;
};

const englishSpanish: LocalizedTextResource = {
  default: "Explain how velocity changes when acceleration is positive.",
  translations: {
    "es": "Explica como cambia la velocidad cuando la aceleracion es positiva.",
  },
};

const localization: LocalizationConfig = {
  sourceLocale: "en",
  supportedLocales: ["en", "es"],
  requiredFields: {
    contentBlocks: true,
    captions: true,
    transcripts: true,
    prompts: true,
    feedback: true,
    rubrics: true,
    accessibilityAlternatives: true,
  },
  publishRequiresCompleteCoverage: true,
};

function createLocalizedLearningUnit(): LearningUnit {
  return {
    id: "LU_motion_v1",
    version: 1,
    domain: "physics",
    level: "secondary",
    status: "review",
    intent: {
      problem: "Learners often confuse velocity and acceleration in visual motion evidence.",
      motivation: "Motion graphs become easier when evidence is connected to variables.",
    },
    claims: [
      {
        id: "C1",
        text: "Learner can explain positive acceleration from changing velocity evidence.",
        bloom: "analyze",
        localizedText: englishSpanish,
      },
    ],
    evidence: [
      {
        id: "E1",
        claimRef: "C1",
        type: "explanation_quality",
        description: "Learner explains velocity changes using simulation observations.",
        localizedDescription: englishSpanish,
        observables: [{ name: "uses_velocity_evidence", type: "boolean" }],
      },
    ],
    tasks: [
      {
        id: "T1",
        type: "explanation",
        claimRef: "C1",
        evidenceRef: "E1",
        prompt: "Explain what the simulation shows about acceleration.",
        localizedPrompt: englishSpanish,
        localizedFeedback: {
          misconception: englishSpanish,
        },
        rubricRef: "rubric-motion",
        localizedRubric: englishSpanish,
      },
    ],
    artifacts: [
      {
        type: "simulation",
        ref: "sim-motion",
        claims: ["C1"],
        scaffolds: ["T1"],
        localization: {
          title: englishSpanish,
          captions: { en: "Velocity increases.", es: "La velocidad aumenta." },
          transcript: englishSpanish,
          accessibilityAlternative: englishSpanish,
        },
      },
    ],
    telemetry: {
      events: ["sim.capture", "assess.answer", "assist.hint"],
      processFeatures: ["time_on_task"],
    },
    assessment: {
      model: "cbm_plus_process",
      confidenceLevels: ["low", "medium", "high"],
      scoring: {
        correctHighConfidence: 3,
        correctMediumConfidence: 2,
        correctLowConfidence: 1,
        incorrectHighConfidence: -6,
        incorrectMediumConfidence: -2,
        incorrectLowConfidence: 0,
      },
      localization: {
        instructions: englishSpanish,
        feedback: { misconception: englishSpanish },
        rubrics: { "rubric-motion": englishSpanish },
      },
    },
    localization,
    createdAt: "2026-05-06T00:00:00.000Z",
    updatedAt: "2026-05-06T00:00:00.000Z",
    createdBy: "author-1",
    tenantId: "tenant-1",
  };
}

describe("learning-unit localization contract", () => {
  it("requires localization as a first-class learning-unit field", () => {
    expect(learningUnitSchema.required).toContain("localization");
    expect(learningUnitSchema.definitions).toHaveProperty("LocalizationConfig");
    expect(learningUnitSchema.definitions.LocalizationConfig.required).toEqual([
      "sourceLocale",
      "supportedLocales",
      "requiredFields",
      "publishRequiresCompleteCoverage",
    ]);
  });

  it("models locale-ready content, captions, transcripts, prompts, feedback, rubrics, and accessibility alternatives", () => {
    const unit = createLocalizedLearningUnit();

    expectTypeOf(unit.localization).toEqualTypeOf<LocalizationConfig>();
    expect(unit.localization.requiredFields).toMatchObject({
      contentBlocks: true,
      captions: true,
      transcripts: true,
      prompts: true,
      feedback: true,
      rubrics: true,
      accessibilityAlternatives: true,
    });
    expect(unit.tasks[0].localizedPrompt?.translations.es).toContain("Explica");
    expect(unit.tasks[0].localizedFeedback?.misconception.translations.es).toContain("Explica");
    expect(unit.artifacts[0].localization?.captions?.es).toContain("velocidad");
    expect(unit.artifacts[0].localization?.transcript?.translations.es).toContain("Explica");
    expect(unit.artifacts[0].localization?.accessibilityAlternative?.translations.es).toContain("Explica");
    expect(unit.assessment.localization?.rubrics?.["rubric-motion"].translations.es).toContain("Explica");
  });
});
