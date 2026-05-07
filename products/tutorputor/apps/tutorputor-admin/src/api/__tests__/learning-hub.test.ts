import { beforeEach, describe, expect, it, vi } from "vitest";

describe("LearningHubApi learning-unit loading", () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it("returns persisted backend learning units when the backend responds", async () => {
    const units = [
      {
        id: "LU_backend_v1",
        title: "Persisted backend unit",
      },
    ];
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ data: units }))),
    );

    const { LearningHubApi } = await import("../learning-hub");

    await expect(LearningHubApi.getLearningUnits()).resolves.toEqual(units);
  });

  it("returns an explicit empty state instead of seed modules when the backend is empty", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response(JSON.stringify({ data: [] }))),
    );

    const { LearningHubApi } = await import("../learning-hub");

    await expect(LearningHubApi.getLearningUnits()).resolves.toEqual([]);
  });

  it("does not fall back to seed modules when the backend is unavailable", async () => {
    vi.stubEnv("VITE_TUTORPUTOR_USE_DEV_LEARNING_UNIT_SEEDS", "false");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("network unavailable");
      }),
    );

    const { LearningHubApi } = await import("../learning-hub");

    await expect(LearningHubApi.getLearningUnits()).resolves.toEqual([]);
  });

  it("uses dev fixtures only when the explicit dev flag is enabled", async () => {
    vi.stubEnv("DEV", true);
    vi.stubEnv("VITE_TUTORPUTOR_USE_DEV_LEARNING_UNIT_SEEDS", "true");
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => {
        throw new Error("network unavailable");
      }),
    );

    const { LearningHubApi } = await import("../learning-hub");
    const result = await LearningHubApi.getLearningUnits();

    expect(result.length).toBeGreaterThan(0);
    expect(result.some((unit) => unit.id === "lu-physics-free-fall-001")).toBe(
      true,
    );
  });
});
