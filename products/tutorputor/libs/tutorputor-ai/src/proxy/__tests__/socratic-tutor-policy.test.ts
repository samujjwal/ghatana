import { describe, expect, it } from "vitest";
import {
  buildSocraticTutorPrompt,
  classifyTutorQuestion,
  validateTutorGroundingContext,
} from "../socratic-tutor-policy";

const groundedContext = {
  moduleId: "module-linear-motion",
  moduleTitle: "Linear Motion",
  claimIds: ["claim-slope-velocity"],
  currentSimulationState: {
    simulationId: "sim-motion-1",
    position: 12,
    velocity: 4,
  },
  recentAttempts: [
    {
      attemptId: "attempt-1",
      taskId: "task-predict-position",
      correct: false,
      confidence: "high" as const,
      misconceptionId: "confuses-position-and-velocity",
    },
  ],
  misconceptions: ["confuses-position-and-velocity"],
  allowedHelpMode: "socratic" as const,
  learningObjectives: ["Use slope to reason about velocity."],
  relevantContent: [
    {
      blockId: "content-slope",
      blockType: "text",
      textContent: "Velocity is the slope of a position-time graph.",
    },
  ],
};

describe("Socratic tutor policy", () => {
  it("requires module, claim, simulation, attempt, misconception, and help-mode context", () => {
    const validation = validateTutorGroundingContext({
      moduleId: "module-1",
      claimIds: [],
      currentSimulationState: undefined,
      recentAttempts: [],
      misconceptions: undefined,
      allowedHelpMode: undefined,
    });

    expect(validation.publishable).toBe(false);
    expect(validation.errors).toEqual([
      "claimIds are required",
      "currentSimulationState is required",
      "recentAttempts are required",
      "misconceptions are required",
      "allowedHelpMode is required",
    ]);
  });

  it("flags direct-answer requests for Socratic redirection", () => {
    expect(classifyTutorQuestion("Give me the answer now").answerSeeking).toBe(
      true,
    );
    expect(classifyTutorQuestion("Can you nudge me?").answerSeeking).toBe(
      false,
    );
  });

  it("flags off-topic questions for module redirection", () => {
    expect(
      classifyTutorQuestion("What is the sports score tonight?").offTopic,
    ).toBe(true);
  });

  it("builds prompts grounded in simulation state, attempts, misconceptions, and claims", () => {
    const prompt = buildSocraticTutorPrompt(
      "Just answer: where will the object be after 3 seconds?",
      groundedContext,
    );

    expect(prompt).toContain("Module ID: module-linear-motion");
    expect(prompt).toContain("Claim IDs: claim-slope-velocity");
    expect(prompt).toContain("Current simulation state");
    expect(prompt).toContain("attempt-1");
    expect(prompt).toContain("confuses-position-and-velocity");
    expect(prompt).toContain("Do not give direct final answers");
    expect(prompt).toContain("answerSeeking: true");
  });
});
