import { expect, test } from "@playwright/test";

type DashboardState = {
  state:
    | "new learner"
    | "struggling learner"
    | "mastered learner"
    | "remediation due"
    | "offline-resumed learner";
  claimMastery: number;
  nextBestLesson: string | null;
  misconceptions: number;
  spacedReview: number;
  simulationsNeedingReview: number;
  remediation: number;
  offlinePending: number;
};

const dashboardStates: DashboardState[] = [
  {
    state: "new learner",
    claimMastery: 0,
    nextBestLesson: "Diagnostic",
    misconceptions: 0,
    spacedReview: 0,
    simulationsNeedingReview: 0,
    remediation: 0,
    offlinePending: 0,
  },
  {
    state: "struggling learner",
    claimMastery: 0.42,
    nextBestLesson: "Velocity remediation",
    misconceptions: 1,
    spacedReview: 1,
    simulationsNeedingReview: 1,
    remediation: 1,
    offlinePending: 0,
  },
  {
    state: "mastered learner",
    claimMastery: 0.94,
    nextBestLesson: "Credential check",
    misconceptions: 0,
    spacedReview: 0,
    simulationsNeedingReview: 0,
    remediation: 0,
    offlinePending: 0,
  },
  {
    state: "remediation due",
    claimMastery: 0.58,
    nextBestLesson: "Targeted evidence task",
    misconceptions: 1,
    spacedReview: 1,
    simulationsNeedingReview: 0,
    remediation: 2,
    offlinePending: 0,
  },
  {
    state: "offline-resumed learner",
    claimMastery: 0.76,
    nextBestLesson: "Continue synced lesson",
    misconceptions: 0,
    spacedReview: 1,
    simulationsNeedingReview: 0,
    remediation: 0,
    offlinePending: 0,
  },
];

test.describe("learner dashboard actionable states", () => {
  for (const state of dashboardStates) {
    test(`${state.state} exposes actionable learning decisions`, () => {
      expect(state.nextBestLesson).toBeTruthy();
      expect(state.claimMastery).toBeGreaterThanOrEqual(0);
      expect(state.claimMastery).toBeLessThanOrEqual(1);

      if (state.state === "struggling learner") {
        expect(state.misconceptions).toBeGreaterThan(0);
        expect(state.simulationsNeedingReview).toBeGreaterThan(0);
      }

      if (state.state === "mastered learner") {
        expect(state.claimMastery).toBeGreaterThanOrEqual(0.9);
        expect(state.remediation).toBe(0);
      }

      if (state.state === "remediation due") {
        expect(state.remediation).toBeGreaterThan(0);
        expect(state.spacedReview).toBeGreaterThan(0);
      }

      if (state.state === "offline-resumed learner") {
        expect(state.offlinePending).toBe(0);
        expect(state.nextBestLesson).toContain("synced");
      }
    });
  }
});
