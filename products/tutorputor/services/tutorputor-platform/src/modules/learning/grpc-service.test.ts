import { describe, expect, it, vi } from "vitest";
import { createLearnerProfileGrpcHandlers } from "./grpc-service.js";

describe("createLearnerProfileGrpcHandlers", () => {
  it("maps personalization snapshots into grpc GetProfile responses", async () => {
    const service = {
      getPersonalizationSnapshot: vi.fn().mockResolvedValue({
        learnerId: "learner-1",
        preferredDifficulty: "MEDIUM",
        preferredModality: "VISUAL",
        preferredPacing: "ADAPTIVE",
        adjustedDifficulty: "medium",
        preferences: ["visual-learning"],
        knowledgeGaps: ["forces"],
        masterySummary: {
          averageMastery: 0.72,
          conceptCount: 4,
          lowMasteryConcepts: ["forces"],
        },
        learningStyleScores: {
          visual: 0.9,
          auditory: 0.4,
          kinesthetic: 0.5,
          reading: 0.6,
        },
        sessionPreferences: {
          preferredSessionMinutes: 25,
          notificationFrequency: "daily",
          preferredTimeOfDay: null,
        },
      }),
    };

    const handlers = createLearnerProfileGrpcHandlers(service as never);
    const callback = vi.fn();

    await handlers.GetProfile?.(
      { request: { tenant_id: "tenant-1", learner_id: "learner-1" } } as never,
      callback as never,
    );

    expect(callback).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        profile: expect.objectContaining({
          learner_id: "learner-1",
          adjusted_difficulty: "MEDIUM",
        }),
      }),
    );
  });

  it("maps recommendation payloads into grpc response messages", async () => {
    const service = {
      getRecommendations: vi.fn().mockResolvedValue([
        {
          conceptId: "concept-1",
          conceptName: "Momentum",
          type: "next",
          reason: "High mastery on prerequisites",
          confidence: 0.82,
          estimatedTimeMinutes: 15,
          suggestedModality: "VISUAL",
        },
      ]),
    };

    const handlers = createLearnerProfileGrpcHandlers(service as never);
    const callback = vi.fn();

    await handlers.GetRecommendations?.(
      { request: { tenant_id: "tenant-1", learner_id: "learner-1" } } as never,
      callback as never,
    );

    expect(callback).toHaveBeenCalledWith(
      null,
      expect.objectContaining({
        recommendations: [
          expect.objectContaining({
            concept_id: "concept-1",
            type: "NEXT",
          }),
        ],
      }),
    );
  });
});
