import { describe, expect, it } from "vitest";
import {
  AIGovernanceError,
  assertAIInteractionAllowed,
  buildAIAuditPayload,
  buildAIGovernanceMetadata,
} from "../governance.js";

describe("AI governance policy", () => {
  it("rejects AI interactions when consent is missing", () => {
    const governance = buildAIGovernanceMetadata({
      consentState: "missing",
      learnerContextScope: "module",
      promptVersion: "tutor-v2",
      modelVersion: "gpt-test-2026-05",
    });

    expect(() => assertAIInteractionAllowed(governance)).toThrow(
      AIGovernanceError,
    );
    expect(() => assertAIInteractionAllowed(governance)).toThrow(
      "consent is missing",
    );
  });

  it("rejects AI interactions when consent has been revoked", () => {
    const governance = buildAIGovernanceMetadata({
      consentState: "revoked",
      learnerContextScope: "assessment",
    });

    expect(() => assertAIInteractionAllowed(governance)).toThrow(
      "consent was revoked",
    );
  });

  it("rejects blocked or human-review-required safety outcomes", () => {
    const blocked = buildAIGovernanceMetadata({
      learnerContextScope: "module",
      safetyFilterResult: "blocked",
    });
    const reviewRequired = buildAIGovernanceMetadata({
      learnerContextScope: "module",
      humanReviewRequired: true,
    });

    expect(() => assertAIInteractionAllowed(blocked)).toThrow(
      "safety filter",
    );
    expect(() => assertAIInteractionAllowed(reviewRequired)).toThrow(
      "human review",
    );
  });

  it("builds sanitized audit payloads without direct learner PII", () => {
    const governance = buildAIGovernanceMetadata({
      learnerContextScope: "simulation",
      promptVersion: "socratic-v3",
      modelVersion: "model-2026-05",
      retrievedContentIds: ["content-1", "claim-2"],
      latencyMs: 123,
      tokenUsage: {
        inputTokens: 50,
        outputTokens: 25,
        totalTokens: 75,
      },
      costUsd: 0.002,
      confidence: 0.82,
    });

    const payload = buildAIAuditPayload({
      endpoint: "tutor/query",
      useCase: "tutor",
      governance,
      request: {
        question: "My email is learner@example.com; give me the answer",
        moduleId: "module-1",
        learnerName: "A. Student",
      },
      response: {
        answer: "Try comparing the slope before and after the change.",
        modelId: "model-1",
      },
    });

    expect(payload.requestPayload).toContain('"containsDirectPii":false');
    expect(payload.requestPayload).toContain('"questionRedacted":true');
    expect(payload.requestPayload).toContain('"moduleId":"module-1"');
    expect(payload.requestPayload).not.toContain("learner@example.com");
    expect(payload.responsePayload).toContain('"containsDirectPii":false');
    expect(payload.responsePayload).toContain('"answerRedacted":true');
  });
});
