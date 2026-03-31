import { describe, expect, it } from "vitest";
import { SessionAdaptationEngine } from "./session-engine.js";

function makeLearnerProfileService() {
  return {
    async getPersonalizationSnapshot() {
      return {
        learnerId: "user-1",
        preferredDifficulty: "MEDIUM",
        preferredModality: "VISUAL",
        preferredPacing: "ADAPTIVE",
        adjustedDifficulty: "beginner",
        preferences: [],
        knowledgeGaps: [],
        masterySummary: {
          averageMastery: 0.42,
          conceptCount: 2,
          lowMasteryConcepts: ["concept-motion"],
        },
        learningStyleScores: {
          visual: 0.8,
          auditory: 0.4,
          kinesthetic: 0.5,
          reading: 0.6,
        },
        sessionPreferences: {
          preferredSessionMinutes: 30,
          notificationFrequency: "daily",
          preferredTimeOfDay: null,
        },
      };
    },
  };
}

function makeVariationService() {
  return {
    async generateDifficultyVariants() {
      return {
        easy: { key: "easy", family: "difficulty", variantId: "v1", assetId: "asset-1" },
        medium: { key: "medium", family: "difficulty", variantId: "v2", assetId: "asset-1" },
        hard: { key: "hard", family: "difficulty", variantId: "v3", assetId: "asset-1" },
        expert: { key: "expert", family: "difficulty", variantId: "v4", assetId: "asset-1" },
      };
    },
    async generateExplanationVariants() {
      return {
        minimal: { key: "minimal", family: "explanation", variantId: "e1", assetId: "asset-1" },
        standard: { key: "standard", family: "explanation", variantId: "e2", assetId: "asset-1" },
        detailed: { key: "detailed", family: "explanation", variantId: "e3", assetId: "asset-1" },
        scaffolded: { key: "scaffolded", family: "explanation", variantId: "e4", assetId: "asset-1" },
      };
    },
    async generateModalityVariants() {
      return {
        visual: { key: "visual", family: "modality", variantId: "m1", assetId: "asset-1" },
        auditory: { key: "auditory", family: "modality", variantId: "m2", assetId: "asset-1" },
        kinesthetic: { key: "kinesthetic", family: "modality", variantId: "m3", assetId: "asset-1" },
        reading: { key: "reading", family: "modality", variantId: "m4", assetId: "asset-1" },
      };
    },
  };
}

describe("SessionAdaptationEngine", () => {
  it("triggers an easier difficulty adaptation after repeated incorrect answers", async () => {
    const engine = new SessionAdaptationEngine(
      makeLearnerProfileService() as never,
      makeVariationService() as never,
    );

    await engine.processEvent({
      tenantId: "tenant-1",
      userId: "user-1",
      sessionId: "session-1",
      assetId: "asset-1",
      eventType: "ANSWER_SUBMITTED",
      correct: false,
      responseLatencyMs: 7000,
    });
    await engine.processEvent({
      tenantId: "tenant-1",
      userId: "user-1",
      sessionId: "session-1",
      assetId: "asset-1",
      eventType: "ANSWER_SUBMITTED",
      correct: false,
      responseLatencyMs: 6500,
    });
    const decision = await engine.processEvent({
      tenantId: "tenant-1",
      userId: "user-1",
      sessionId: "session-1",
      assetId: "asset-1",
      eventType: "ANSWER_SUBMITTED",
      correct: false,
      responseLatencyMs: 6000,
    });

    expect(decision.adapted).toBe(true);
    expect(decision.trigger).toBe("REPEATED_ERRORS");
    expect(decision.variant?.key).toBe("easy");
  });

  it("returns the cached decision for the current session asset", async () => {
    const engine = new SessionAdaptationEngine(
      makeLearnerProfileService() as never,
      makeVariationService() as never,
    );

    await engine.processEvent({
      tenantId: "tenant-1",
      userId: "user-1",
      sessionId: "session-2",
      assetId: "asset-1",
      eventType: "IDLE",
      inactivityMs: 240000,
    });

    const decision = await engine.getCurrentAdaptation("session-2", "asset-1");

    expect(decision?.adapted).toBe(true);
    expect(decision?.variant?.key).toBe("visual");
  });
});
