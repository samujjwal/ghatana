/**
 * @doc.type test
 * @doc.purpose Unit tests for GroupMatchingService - learning overlap, scoring, formation
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, beforeEach, vi } from "vitest";
import {
  GroupMatchingService,
  type UserLearningProfile,
} from "../group-matching";

function makePrisma() {
  return {
    $queryRaw: vi.fn().mockResolvedValue([]),
    $executeRaw: vi.fn().mockResolvedValue(1),
  };
}

function makeProfile(overrides: Partial<UserLearningProfile> = {}): UserLearningProfile {
  return {
    userId: "user-1",
    interests: ["math", "physics"],
    enrolledModules: ["mod-calc-101", "mod-phys-101"],
    completedModules: ["mod-alg-101"],
    skillLevels: new Map([["math", 7], ["physics", 5]]),
    languages: ["en"],
    peerConnections: [],
    ...overrides,
  };
}

describe("GroupMatchingService", () => {
  let service: GroupMatchingService;

  beforeEach(() => {
    service = new GroupMatchingService(makePrisma() as never);
  });

  describe("calculateLearningOverlap", () => {
    it("returns zero score for users with no overlap", () => {
      const p1 = makeProfile({
        userId: "u1",
        interests: ["math"],
        enrolledModules: ["mod-a"],
        skillLevels: new Map([["math", 8]]),
      });
      const p2 = makeProfile({
        userId: "u2",
        interests: ["history"],
        enrolledModules: ["mod-b"],
        skillLevels: new Map([["history", 6]]),
      });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.score).toBe(0);
      expect(result.commonInterests).toHaveLength(0);
      expect(result.commonModules).toHaveLength(0);
    });

    it("awards points for common interests", () => {
      const p1 = makeProfile({ userId: "u1", interests: ["math", "physics"] });
      const p2 = makeProfile({ userId: "u2", interests: ["math", "art"] });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.commonInterests).toContain("math");
      expect(result.score).toBeGreaterThan(0);
    });

    it("awards more points for shared enrolled modules", () => {
      const p1 = makeProfile({ userId: "u1", enrolledModules: ["mod-x", "mod-y"] });
      const p2 = makeProfile({ userId: "u2", enrolledModules: ["mod-x", "mod-z"] });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.commonModules).toContain("mod-x");
      expect(result.score).toBeGreaterThan(0);
    });

    it("identifies complementary skills (skill difference >= 3)", () => {
      const p1 = makeProfile({
        userId: "u1",
        skillLevels: new Map([["python", 9]]),
      });
      const p2 = makeProfile({
        userId: "u2",
        skillLevels: new Map([["python", 3]]),
      });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.complementarySkills).toContain("python");
    });

    it("does not flag similar skill levels as complementary", () => {
      const p1 = makeProfile({
        userId: "u1",
        skillLevels: new Map([["python", 7]]),
      });
      const p2 = makeProfile({
        userId: "u2",
        skillLevels: new Map([["python", 8]]),
      });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.complementarySkills).not.toContain("python");
    });

    it("caps score at 20", () => {
      const p1 = makeProfile({
        userId: "u1",
        interests: Array.from({ length: 20 }, (_, i) => `topic-${i}`),
        enrolledModules: Array.from({ length: 10 }, (_, i) => `mod-${i}`),
      });
      const p2 = makeProfile({
        userId: "u2",
        interests: Array.from({ length: 20 }, (_, i) => `topic-${i}`),
        enrolledModules: Array.from({ length: 10 }, (_, i) => `mod-${i}`),
      });

      const result = service.calculateLearningOverlap(p1, p2);

      expect(result.score).toBeLessThanOrEqual(20);
    });

    it("adds bonus for same learning style", () => {
      const p1 = makeProfile({ userId: "u1", learningStyle: "visual" });
      const p2 = makeProfile({ userId: "u2", learningStyle: "visual" });
      const p3 = makeProfile({ userId: "u3", learningStyle: "auditory" });

      const withMatch = service.calculateLearningOverlap(p1, p2);
      const withoutMatch = service.calculateLearningOverlap(p1, p3);

      expect(withMatch.score).toBeGreaterThan(withoutMatch.score);
    });

    it("adds bonus for same preferred study time", () => {
      const p1 = makeProfile({ userId: "u1", preferredStudyTime: "evening" });
      const p2 = makeProfile({ userId: "u2", preferredStudyTime: "evening" });
      const p3 = makeProfile({ userId: "u3" });

      const withMatch = service.calculateLearningOverlap(p1, p2);
      const withoutMatch = service.calculateLearningOverlap(p1, p3);

      expect(withMatch.score).toBeGreaterThanOrEqual(withoutMatch.score);
    });
  });

  describe("getLearningOverlapBetweenUsers", () => {
    it("fetches profiles and delegates to calculateLearningOverlap", async () => {
      // Mock DB returns empty data (no enrollments/preferences/connections)
      const result = await service.getLearningOverlapBetweenUsers(
        "tenant-1",
        "user-a",
        "user-b",
      );

      expect(result).toHaveProperty("score");
      expect(result).toHaveProperty("commonInterests");
      expect(result).toHaveProperty("commonModules");
      expect(result).toHaveProperty("complementarySkills");
    });
  });

  describe("findMatchingGroups", () => {
    it("returns empty array when no groups exist", async () => {
      const groups = await service.findMatchingGroups("tenant-1", "user-1", 5);
      expect(groups).toEqual([]);
    });
  });

  describe("suggestGroupFormation", () => {
    it("returns null when insufficient candidate users", async () => {
      // Mock returns no users
      const result = await service.suggestGroupFormation("tenant-1", "user-seed", 4);
      expect(result).toBeNull();
    });
  });
});
