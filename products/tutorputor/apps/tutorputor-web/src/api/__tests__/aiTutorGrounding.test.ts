/**
 * @doc.type tests
 * @doc.purpose Unit tests for buildAITutorGroundingPayload — verifies defaults
 *   contain no synthetic placeholder IDs and that caller overrides are honoured.
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect } from "vitest";

import {
  buildAITutorGroundingPayload,
  type AITutorGroundingPayload,
} from "../aiTutorGrounding";

describe("buildAITutorGroundingPayload", () => {
  it("returns empty moduleId by default (no synthetic placeholder)", () => {
    const payload = buildAITutorGroundingPayload();
    expect(payload.moduleId).toBe("");
  });

  it("returns empty claimIds array by default (no synthetic placeholder)", () => {
    const payload = buildAITutorGroundingPayload();
    expect(payload.claimIds).toEqual([]);
  });

  it("returns empty recentAttempts array by default (no synthetic placeholder)", () => {
    const payload = buildAITutorGroundingPayload();
    expect(payload.recentAttempts).toEqual([]);
  });

  it("does not contain synthetic placeholder strings in default values", () => {
    const payload = buildAITutorGroundingPayload();
    const serialised = JSON.stringify(payload);
    expect(serialised).not.toContain("current-module");
    expect(serialised).not.toContain("current-claim");
    expect(serialised).not.toContain("current-session");
  });

  it("passes through moduleId override correctly", () => {
    const payload = buildAITutorGroundingPayload({ moduleId: "module-abc" });
    expect(payload.moduleId).toBe("module-abc");
  });

  it("passes through claimIds override correctly", () => {
    const payload = buildAITutorGroundingPayload({ claimIds: ["claim-1", "claim-2"] });
    expect(payload.claimIds).toEqual(["claim-1", "claim-2"]);
  });

  it("passes through recentAttempts override correctly", () => {
    const attempts: AITutorGroundingPayload["recentAttempts"] = [
      { attemptId: "attempt-1", correct: true, confidence: "high" },
    ];
    const payload = buildAITutorGroundingPayload({ recentAttempts: attempts });
    expect(payload.recentAttempts).toEqual(attempts);
  });

  it("uses socratic as the default help mode", () => {
    const payload = buildAITutorGroundingPayload();
    expect(payload.allowedHelpMode).toBe("socratic");
  });

  it("passes through allowedHelpMode override", () => {
    const payload = buildAITutorGroundingPayload({ allowedHelpMode: "hint" });
    expect(payload.allowedHelpMode).toBe("hint");
  });
});
