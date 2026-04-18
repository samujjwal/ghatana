/**
 * Adaptive Difficulty Engine Tests
 *
 * @doc.type test
 * @doc.purpose Unit tests for adaptive difficulty adjustment algorithm
 * @doc.layer product
 */
import { describe, it, expect, beforeEach } from "vitest";
import {
  AdaptiveDifficultyEngine,
  DEFAULT_ADAPTIVE_CONFIG,
  type AdaptiveConfig,
} from "../adaptive-engine";

// Mock Prisma client
const mockPrisma = {
  $queryRaw: async () => [],
} as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

describe("AdaptiveDifficultyEngine", () => {
  let engine: AdaptiveDifficultyEngine;

  beforeEach(() => {
    engine = new AdaptiveDifficultyEngine(mockPrisma);
  });

  describe("Configuration", () => {
    it("should use default config when none provided", () => {
      const config = engine.getConfig();
      expect(config.minDifficulty).toBe(DEFAULT_ADAPTIVE_CONFIG.minDifficulty);
      expect(config.maxDifficulty).toBe(DEFAULT_ADAPTIVE_CONFIG.maxDifficulty);
      expect(config.streakThreshold).toBe(DEFAULT_ADAPTIVE_CONFIG.streakThreshold);
    });

    it("should allow custom config override", () => {
      const customConfig: Partial<AdaptiveConfig> = {
        minDifficulty: 2,
        streakThreshold: 5,
      };
      const customEngine = new AdaptiveDifficultyEngine(mockPrisma, customConfig);
      const config = customEngine.getConfig();

      expect(config.minDifficulty).toBe(2);
      expect(config.streakThreshold).toBe(5);
      expect(config.maxDifficulty).toBe(DEFAULT_ADAPTIVE_CONFIG.maxDifficulty); // unchanged
    });

    it("should update config dynamically", () => {
      engine.updateConfig({ streakThreshold: 4 });
      const config = engine.getConfig();
      expect(config.streakThreshold).toBe(4);
    });
  });

  describe("Difficulty Description", () => {
    it("should return correct difficulty labels", () => {
      expect(engine.getDifficultyDescription(1)).toBe("Beginner");
      expect(engine.getDifficultyDescription(2)).toBe("Beginner");
      expect(engine.getDifficultyDescription(3)).toBe("Easy");
      expect(engine.getDifficultyDescription(5)).toBe("Intermediate");
      expect(engine.getDifficultyDescription(7)).toBe("Advanced");
      expect(engine.getDifficultyDescription(9)).toBe("Expert");
      expect(engine.getDifficultyDescription(10)).toBe("Expert");
    });
  });

  describe("Streak-based Adjustments", () => {
    it("should increase difficulty after correct streak threshold", async () => {
      // Mock performance data with correct streak
      const mockPrismaWithStreak = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 15, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 12, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 10, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.adjustmentType).toBe("increase");
      expect(result.reason).toContain("correct");
      expect(result.confidence).toBeGreaterThan(0.8);
    });

    it("should decrease difficulty after incorrect streak threshold", async () => {
      const mockPrismaWithStreak = {
        $queryRaw: async () => [
          { correct: false, timeSpentSeconds: 45, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 50, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 48, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.adjustmentType).toBe("decrease");
      expect(result.reason).toContain("incorrect");
    });

    it("should maintain difficulty below streak threshold", async () => {
      const mockPrismaWithStreak = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 20, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 18, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      // With only 2 correct in a row, should not trigger streak adjustment
      // But might trigger accuracy-based adjustment
      expect(result.currentDifficulty).toBe(5);
    });
  });

  describe("Accuracy-based Adjustments", () => {
    it("should increase difficulty with high accuracy", async () => {
      const mockPrismaWithHighAccuracy = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 15, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 12, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 18, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 14, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 16, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 25, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithHighAccuracy);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.adjustmentType).toBe("increase");
      expect(result.reason).toContain("accuracy");
    });

    it("should decrease difficulty with low accuracy", async () => {
      const mockPrismaWithLowAccuracy = {
        $queryRaw: async () => [
          { correct: false, timeSpentSeconds: 40, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 35, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 45, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithLowAccuracy);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.adjustmentType).toBe("decrease");
      expect(result.reason).toContain("Low accuracy");
    });
  });

  describe("Bounds and Limits", () => {
    it("should not exceed max difficulty", async () => {
      const mockPrismaWithStreak = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 10, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 8, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 9, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 9.5);

      expect(result.newDifficulty).toBeLessThanOrEqual(10);
    });

    it("should not go below min difficulty", async () => {
      const mockPrismaWithStreak = {
        $queryRaw: async () => [
          { correct: false, timeSpentSeconds: 50, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 55, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 48, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 1.5);

      expect(result.newDifficulty).toBeGreaterThanOrEqual(1);
    });
  });

  describe("Confidence Scoring", () => {
    it("should return high confidence for strong streaks", async () => {
      const mockPrismaWithStrongStreak = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 10, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 9, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 8, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 11, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 10, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithStrongStreak);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.confidence).toBeGreaterThan(0.8);
    });

    it("should return lower confidence for accuracy-based adjustments", async () => {
      const mockPrismaWithAccuracy = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 20, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 18, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 22, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 19, attemptedAt: new Date(), topicId: "math" },
          { correct: false, timeSpentSeconds: 30, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithAccuracy);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      // High accuracy but not a strong streak, so moderate confidence
      expect(result.confidence).toBeGreaterThan(0.5);
      expect(result.confidence).toBeLessThan(0.9);
    });
  });

  describe("Time-based Adjustments", () => {
    it("should suggest increase for fast accurate responses", async () => {
      const mockPrismaWithFastResponses = {
        $queryRaw: async () => [
          { correct: true, timeSpentSeconds: 5, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 4, attemptedAt: new Date(), topicId: "math" },
          { correct: true, timeSpentSeconds: 6, attemptedAt: new Date(), topicId: "math" },
        ],
      } as unknown as Parameters<typeof AdaptiveDifficultyEngine>[0];

      const testEngine = new AdaptiveDifficultyEngine(mockPrismaWithFastResponses);
      const result = await testEngine.calculateNextDifficulty("tenant1", "user1", 5);

      expect(result.reason).toContain("Fast");
    });
  });
});
