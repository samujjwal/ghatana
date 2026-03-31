import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  breakerFire: vi.fn(),
  breakerOn: vi.fn(),
  breakerFallback: vi.fn(),
}));

vi.mock("opossum", () => ({
  default: class MockCircuitBreaker {
    fire = mocks.breakerFire;
    on = mocks.breakerOn;
    fallback = mocks.breakerFallback;
  },
}));

vi.mock("@grpc/proto-loader", () => ({
  loadSync: vi.fn().mockReturnValue({}),
}));

vi.mock("@grpc/grpc-js", () => ({
  credentials: { createInsecure: vi.fn().mockReturnValue({}) },
  loadPackageDefinition: vi.fn().mockImplementation(() => ({
    tutorputor: {
      learner_profile: {
        LearnerProfileService: class MockLearnerProfileService {},
      },
    },
  })),
}));

import { LearnerProfileClient } from "../learner-profile-client.js";

describe("LearnerProfileClient", () => {
  let client: LearnerProfileClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new LearnerProfileClient();
  });

  it("dispatches GetProfile through the circuit breaker", async () => {
    mocks.breakerFire.mockResolvedValueOnce({
      profile: { learner_id: "learner-1" },
    });

    const result = await client.getProfile({
      tenant_id: "tenant-1",
      learner_id: "learner-1",
    });

    expect(mocks.breakerFire).toHaveBeenCalledWith(
      expect.anything(),
      "GetProfile",
      {
        tenant_id: "tenant-1",
        learner_id: "learner-1",
      },
    );
    expect(result?.profile.learner_id).toBe("learner-1");
  });

  it("normalizes optional mastery fields before dispatch", async () => {
    mocks.breakerFire.mockResolvedValueOnce({
      learner_id: "learner-1",
      concept_id: "concept-1",
      mastery_probability: 0.7,
      next_review_days: 3,
    });

    await client.updateMastery({
      tenant_id: "tenant-1",
      learner_id: "learner-1",
      concept_id: "concept-1",
      correct: true,
    });

    expect(mocks.breakerFire).toHaveBeenCalledWith(
      expect.anything(),
      "UpdateMastery",
      expect.objectContaining({
        confidence: 0.5,
        time_spent_seconds: 0,
        hints_used: 0,
        attempts: 1,
      }),
    );
  });

  it("fills defaults for recommendation and gap calls", async () => {
    mocks.breakerFire.mockResolvedValueOnce({ status: "OPEN" });
    await client.recordGap({
      tenant_id: "tenant-1",
      learner_id: "learner-1",
      concept_id: "concept-1",
      prerequisite_id: "concept-0",
    });

    expect(mocks.breakerFire).toHaveBeenCalledWith(
      expect.anything(),
      "RecordGap",
      expect.objectContaining({
        severity: "MEDIUM",
        detected_by: "SYSTEM",
      }),
    );

    mocks.breakerFire.mockResolvedValueOnce({ recommendations: [] });
    await client.getRecommendations({
      tenant_id: "tenant-1",
      learner_id: "learner-1",
    });

    expect(mocks.breakerFire).toHaveBeenCalledWith(
      expect.anything(),
      "GetRecommendations",
      expect.objectContaining({
        available_time_minutes: 0,
        current_concept_id: "",
        goal_concept_id: "",
      }),
    );
  });
});
