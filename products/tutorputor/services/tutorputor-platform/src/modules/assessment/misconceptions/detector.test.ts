import { describe, expect, it } from "vitest";
import { MisconceptionDetector } from "./detector.js";

describe("MisconceptionDetector", () => {
  it("detects misconception-aligned distractor choices", () => {
    const detector = new MisconceptionDetector();
    const signals = detector.detectFromAttempt({
      domain: "SCIENCE",
      items: [
        {
          id: "item-1" as never,
          type: "multiple_choice_single" as never,
          prompt: "What keeps an object moving?",
          points: 10,
          choices: [
            { id: "a", label: "Objects must keep experiencing force to remain in motion." },
            { id: "b", label: "Motion persists without net force.", isCorrect: true },
          ],
          metadata: { topic: "force and motion", conceptId: "newton-laws" },
        },
      ],
      responses: {
        "item-1": {
          type: "multiple_choice",
          selectedChoiceIds: ["a"],
        },
      } as never,
      feedback: [
        {
          itemId: "item-1" as never,
          scorePercent: 0,
          needsReview: true,
        },
      ],
    });

    expect(signals).toHaveLength(1);
    expect(signals[0]?.misconceptionId).toBe("physics-force-motion");
  });
});
