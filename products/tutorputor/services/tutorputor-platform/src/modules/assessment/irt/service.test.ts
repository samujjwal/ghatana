import { describe, expect, it } from "vitest";
import { IRTCalibrationService } from "./service.js";

describe("IRTCalibrationService", () => {
  const service = new IRTCalibrationService();

  it("estimates theta from mastery", () => {
    expect(service.estimateThetaFromMastery(0.2)).toBeLessThan(0);
    expect(service.estimateThetaFromMastery(0.8)).toBeGreaterThan(0);
  });

  it("clamps theta estimates to the supported range", () => {
    expect(service.estimateThetaFromMastery(-10)).toBe(-3);
    expect(service.estimateThetaFromMastery(10)).toBe(3);
  });

  it("applies taxonomy boosts and defaults unknown difficulty levels", () => {
    expect(
      service.calibrateForDifficulty("ADVANCED", "analyze"),
    ).toEqual({
      discrimination: 1.3,
      difficulty: 1.5,
      guessing: 0.2,
    });

    expect(
      service.calibrateForDifficulty("custom", "remember"),
    ).toEqual({
      discrimination: 1,
      difficulty: 0,
      guessing: 0.2,
    });
  });

  it("prefers informative items close to target theta", () => {
    const result = service.selectNextItems(
      [
        {
          item: "easy",
          irt: { discrimination: 1, difficulty: -1.5, guessing: 0.2 },
        },
        {
          item: "matched",
          irt: { discrimination: 1.3, difficulty: 0.1, guessing: 0.2 },
        },
        {
          item: "hard",
          irt: { discrimination: 1, difficulty: 2, guessing: 0.2 },
        },
      ],
      0,
      1,
    );

    expect(result.items).toEqual(["matched"]);
  });

  it("limits selection to available ranked items in deterministic order", () => {
    const result = service.selectNextItems(
      [
        {
          item: "first",
          irt: { discrimination: 1.2, difficulty: 0.1, guessing: 0.2 },
        },
        {
          item: "second",
          irt: { discrimination: 1.2, difficulty: 0.2, guessing: 0.2 },
        },
      ],
      0,
      5,
    );

    expect(result.items).toEqual(["first", "second"]);
  });
});
