import { describe, expect, it } from "vitest";
import {
  buildLearningRecommendations,
  calculateBayesianMasteryUpdate,
  inferDominantModality,
  inferLearnerProfileSignalUpdate,
  inferLearningStyleScores,
} from "./learner-profile-service.js";

describe("calculateBayesianMasteryUpdate", () => {
  it("raises mastery after a correct response", () => {
    const result = calculateBayesianMasteryUpdate({
      prior: 0.35,
      correct: true,
      confidence: 0.8,
    });

    expect(result.posterior).toBeGreaterThan(0.35);
    expect(result.nextReviewDays).toBeGreaterThanOrEqual(1);
  });

  it("reduces mastery after an incorrect response", () => {
    const result = calculateBayesianMasteryUpdate({
      prior: 0.8,
      correct: false,
      confidence: 0.7,
    });

    expect(result.posterior).toBeLessThan(0.8);
    expect(result.nextReviewDays).toBeLessThanOrEqual(7);
  });

  it("updates session and modality signals from mastery evidence", () => {
    const result = inferLearnerProfileSignalUpdate({
      profile: {
        avgSessionMinutes: 30,
        preferredSessionMinutes: 30,
        preferredTimeOfDay: null,
        streakDays: 2,
        lastActiveAt: new Date("2026-03-29T14:00:00.000Z"),
        visualLearningScore: 0.25,
        auditoryLearningScore: 0.25,
        kinestheticLearningScore: 0.25,
        readingLearningScore: 0.25,
      },
      input: {
        conceptId: "forces",
        correct: true,
        confidence: 0.9,
        timeSpentSeconds: 2400,
        attempts: 2,
        hintsUsed: 1,
        modalityUsed: "VISUAL",
      },
      observedAt: new Date("2026-03-30T09:30:00.000Z"),
    });

    expect(result.streakDays).toBe(3);
    expect(result.preferredTimeOfDay).toBe("morning");
    expect(result.avgSessionMinutes).toBeGreaterThan(30);
    expect(result.visualLearningScore).toBeGreaterThan(
      result.auditoryLearningScore,
    );
  });

  it("normalizes learning style scores after behavior updates", () => {
    const result = inferLearningStyleScores({
      visualLearningScore: 0.25,
      auditoryLearningScore: 0.25,
      kinestheticLearningScore: 0.25,
      readingLearningScore: 0.25,
      modalityUsed: "READING",
      hintsUsed: 3,
      attempts: 1,
      timeSpentSeconds: 1200,
      confidence: 0.8,
    });

    const total =
      result.visualLearningScore +
      result.auditoryLearningScore +
      result.kinestheticLearningScore +
      result.readingLearningScore;

    expect(total).toBeCloseTo(1, 1);
    expect(result.readingLearningScore).toBeGreaterThan(0.25);
  });

  it("prefers urgent prerequisite and goal-aligned recommendations", () => {
    const recommendations = buildLearningRecommendations({
      profile: {
        preferredDifficulty: "MEDIUM",
        preferredModality: "VISUAL",
        preferredPacing: "ADAPTIVE",
        preferredSessionMinutes: 20,
        notificationFrequency: "daily",
        visualLearningScore: 0.5,
        auditoryLearningScore: 0.15,
        kinestheticLearningScore: 0.2,
        readingLearningScore: 0.15,
        avgSessionMinutes: 22,
        preferredTimeOfDay: "evening",
        streakDays: 5,
      } as never,
      mastery: [
        {
          conceptId: "fractions",
          masteryProbability: 0.48,
          nextReviewAt: new Date(Date.now() - 86_400_000),
        },
        {
          conceptId: "equations",
          masteryProbability: 0.91,
          nextReviewAt: new Date(Date.now() + 10 * 86_400_000),
        },
      ] as never,
      gaps: [
        {
          conceptId: "algebra-2",
          prerequisiteId: "fractions",
          severity: "HIGH",
        },
      ] as never,
      conceptMap: new Map([
        ["fractions", "Fractions"],
        ["equations", "Equations"],
        ["algebra-2", "Algebra II"],
      ]),
      context: {
        currentConceptId: "algebra-2",
        goalConceptId: "fractions",
        availableTimeMinutes: 20,
      },
    });

    expect(recommendations[0]?.conceptId).toBe("fractions");
    expect(recommendations[0]?.type).toBe("prerequisite");
    expect(recommendations[0]?.suggestedModality).toBe(
      inferDominantModality({
        preferredModality: "VISUAL",
        visualLearningScore: 0.5,
        auditoryLearningScore: 0.15,
        kinestheticLearningScore: 0.2,
        readingLearningScore: 0.15,
      } as never),
    );
  });
});
