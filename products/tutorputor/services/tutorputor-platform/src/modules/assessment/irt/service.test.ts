import { describe, expect, it } from "vitest";
import { IRTCalibrationService } from "./service.js";

describe("IRTCalibrationService", () => {
  const service = new IRTCalibrationService();

  it("estimates theta from mastery", () => {
    expect(service.estimateThetaFromMastery(0.2)).toBeLessThan(0);
    expect(service.estimateThetaFromMastery(0.8)).toBeGreaterThan(0);
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
});
