/**
 * Learning Unit Lifecycle Tests
 *
 * Covers LU state machine transitions and publishing gate boundaries
 * that are not tested in the main service test suite.
 *
 * @doc.type test
 * @doc.purpose LU lifecycle state transitions: unpublish, archive, publishing gate boundaries, and validation score thresholds
 * @doc.layer product
 * @doc.pattern UnitTest
 */

// ---------------------------------------------------------------------------
// Mocks — must precede any imports that transitively load these packages
// ---------------------------------------------------------------------------
import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("@grpc/grpc-js", () => {
  class GrpcServiceStub {
    generateClaims = (
      _req: unknown,
      _meta: unknown,
      cb: (err: null, result: { claims: unknown[] }) => void,
    ) => cb(null, { claims: [] });
    generateContent = (
      _req: unknown,
      _meta: unknown,
      cb: (err: null, result: { content: string }) => void,
    ) => cb(null, { content: "" });
  }
  const descriptor = {
    tutorputor: {
      ai_learning: { AiLearningService: GrpcServiceStub },
      content_generation: { ContentGenerationService: GrpcServiceStub },
    },
  };
  return {
    default: {},
    credentials: { createInsecure: () => ({}) },
    loadPackageDefinition: () => descriptor,
    ServerCredentials: { createInsecure: () => ({}) },
  };
});

vi.mock("@grpc/proto-loader", () => ({
  default: {},
  loadSync: () => ({}),
}));

vi.mock("opossum", () => ({
  default: class CircuitBreaker {
    constructor(_fn: unknown) {}
    fire = (_fn: unknown, ..._args: unknown[]) => Promise.resolve();
    fallback = () => this;
    on = () => this;
  },
}));

vi.mock("openai", () => ({
  default: class {
    chat = {
      completions: {
        create: vi.fn().mockResolvedValue({
          choices: [{ message: { content: "{}" } }],
        }),
      },
    };
  },
}));

import { createContentStudioService } from "../service.js";

// ---------------------------------------------------------------------------
// Shared fixtures
// ---------------------------------------------------------------------------
const BASE_EXPERIENCE = {
  id: "exp-lc-1",
  tenantId: "tenant-1",
  slug: "newton-laws",
  title: "Newton's Laws",
  description: "Force and motion",
  status: "DRAFT",
  version: 1,
  gradeRange: "GRADE_9_12",
  mathLevel: "ALGEBRA",
  rigorLevel: "ANALYTICAL",
  scaffoldingLevel: "MEDIUM",
  vocabularyComplexity: 7,
  readingLevel: 9,
  prerequisiteConcepts: [],
  estimatedTimeMinutes: 30,
  keywords: [],
  moduleId: null,
  authorId: "user-1",
  createdAt: new Date("2024-01-01"),
  updatedAt: new Date("2024-01-01"),
};

function makePrisma() {
  return {
    learningExperience: {
      create: vi.fn().mockResolvedValue(BASE_EXPERIENCE),
      findFirst: vi.fn().mockResolvedValue(BASE_EXPERIENCE),
      findUnique: vi.fn().mockResolvedValue({
        ...BASE_EXPERIENCE,
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      }),
      findMany: vi.fn().mockResolvedValue([BASE_EXPERIENCE]),
      count: vi.fn().mockResolvedValue(1),
      delete: vi.fn().mockResolvedValue(BASE_EXPERIENCE),
      update: vi
        .fn()
        .mockImplementation((args: { data: Record<string, unknown> }) =>
          Promise.resolve({ ...BASE_EXPERIENCE, ...args.data }),
        ),
    },
    validationRecord: {
      create: vi.fn().mockResolvedValue({ id: "val-1" }),
      findFirst: vi.fn().mockResolvedValue(null),
      findMany: vi.fn().mockResolvedValue([]),
    },
    experienceEvent: {
      create: vi.fn().mockResolvedValue({ id: "evt-1" }),
      findMany: vi.fn().mockResolvedValue([]),
    },
    experienceAnalytics: {
      findUnique: vi.fn().mockResolvedValue(null),
    },
    $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("LU lifecycle state transitions", () => {
  let service: ReturnType<typeof createContentStudioService>;
  let prisma: ReturnType<typeof makePrisma>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createContentStudioService(prisma as never, {
      openaiApiKey: "test-key",
    });
  });

  // --------------------------------------------------------------------------
  // unpublishExperience
  // --------------------------------------------------------------------------
  describe("unpublishExperience", () => {
    it("sets DB status to REVIEW when unpublishing a published experience", async () => {
      // Stub the two findUnique calls: one in the update flow, one in mapExperience
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        status: "REVIEW",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.unpublishExperience("exp-lc-1");

      expect(prisma.learningExperience.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "exp-lc-1" },
          data: expect.objectContaining({ status: "REVIEW" }),
        }),
      );
    });

    it("records an UNPUBLISHED event with system as actor", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        status: "REVIEW",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.unpublishExperience("exp-lc-1");

      const calls = prisma.experienceEvent.create.mock.calls;
      const unpublishedEvent = calls.find(
        (call: unknown[]) =>
          (call[0] as { data?: { eventType?: string } })?.data?.eventType ===
          "UNPUBLISHED",
      );
      expect(unpublishedEvent).toBeDefined();
      expect(
        (unpublishedEvent?.[0] as { data?: { actorId?: string } })?.data
          ?.actorId,
      ).toBe("system");
    });

    it("passes the reason through the UNPUBLISHED event metadata when provided", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        status: "REVIEW",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.unpublishExperience("exp-lc-1", "Content needs revision");

      const calls = prisma.experienceEvent.create.mock.calls;
      const unpublishedEvent = calls.find(
        (call: unknown[]) =>
          (call[0] as { data?: { eventType?: string } })?.data?.eventType ===
          "UNPUBLISHED",
      );
      const metadata = (
        unpublishedEvent?.[0] as { data?: { metadata?: unknown } }
      )?.data?.metadata;
      expect(JSON.stringify(metadata)).toContain("Content needs revision");
    });
  });

  // --------------------------------------------------------------------------
  // archiveExperience — DB update assertion
  // --------------------------------------------------------------------------
  describe("archiveExperience", () => {
    it("sets DB status to ARCHIVED when archiving", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        status: "ARCHIVED",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.archiveExperience("exp-lc-1");

      expect(prisma.learningExperience.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "exp-lc-1" },
          data: expect.objectContaining({ status: "ARCHIVED" }),
        }),
      );
    });

    it("records an ARCHIVED event with action metadata", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        status: "ARCHIVED",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.archiveExperience("exp-lc-1");

      const calls = prisma.experienceEvent.create.mock.calls;
      const archiveEvent = calls.find(
        (call: unknown[]) =>
          (call[0] as { data?: { eventType?: string } })?.data?.eventType ===
          "ARCHIVED",
      );
      expect(archiveEvent).toBeDefined();
    });
  });

  // --------------------------------------------------------------------------
  // validateExperience — score threshold boundaries
  // --------------------------------------------------------------------------
  describe("validateExperience score thresholds", () => {
    /**
     * Configuration to produce score ~48 (in the "warnings" range 45-59):
     *   - 2 claims, neither has bloom
     *   - Claim 1: has tasks (hasTasks=true), no artifacts
     *   - Claim 2: no tasks, has artifacts (hasArtifacts=true)
     *   - allClaimsMeetBaseline = true (each has tasks OR artifacts)
     *
     * Score calculation:
     *   educational = 50*(0/2) + 50*(1/2) = 25
     *   experiential = (1/2)*100 = 50
     *   technical = 50 (no gradeAdaptation)
     *   safety = 100
     *   accessibility = 50 (no gradeAdaptation)
     *   overall = 25*0.3 + 50*0.35 + 50*0.15 + 100*0.1 + 50*0.1
     *           = 7.5 + 17.5 + 7.5 + 10 + 5 = 47.5 → 48
     *   canPublish = true && true && (48 >= 60) → false
     *   status = "warnings" (48 >= 45)
     */
    it("returns status='warnings' when score is between 45 and 59 with allClaimsMeetBaseline=true", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: null, // no bloom
            examples: [],
            simulations: [],
            animations: [],
          },
          {
            id: "c2",
            claimRef: "claim-2",
            bloomLevel: null, // no bloom
            examples: [{ id: "ex-1" }], // has artifact
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        // Only claim-1 has a task (claim-2 has artifacts instead)
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-lc-1");

      expect(result.canPublish).toBe(false);
      expect(result.status).toBe("warnings");
      expect(result.score).toBeGreaterThanOrEqual(45);
      expect(result.score).toBeLessThan(60);
    });

    /**
     * Configuration to produce score < 45 ("invalid"):
     *   - 2 claims, no bloom, no tasks, only 1 has artifact
     *   - allClaimsMeetBaseline = false (claim-1 has neither tasks nor artifacts)
     *
     * Score calculation:
     *   educational = 0
     *   experiential = (1/2)*100 = 50
     *   technical = 50
     *   safety = 100
     *   accessibility = 50
     *   overall = 0 + 50*0.35 + 50*0.15 + 10 + 5 = 17.5 + 7.5 + 10 + 5 = 40
     *   status = "invalid" (40 < 45)
     */
    it("returns status='invalid' when score is below 45", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: null,
            examples: [], // no artifact
            simulations: [],
            animations: [],
          },
          {
            id: "c2",
            claimRef: "claim-2",
            bloomLevel: null,
            examples: [{ id: "ex-1" }], // has artifact
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [], // no tasks for any claim
      });

      const result = await service.validateExperience("exp-lc-1");

      expect(result.canPublish).toBe(false);
      expect(result.status).toBe("invalid");
      expect(result.score).toBeLessThan(45);
    });

    it("returns status='valid' when all claims have both bloom, tasks, and artifacts", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: "APPLY",
            examples: [{ id: "ex-1" }],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-lc-1");

      expect(result.canPublish).toBe(true);
      expect(result.status).toBe("valid");
      expect(result.score).toBeGreaterThanOrEqual(60);
    });

    it("returns pillarScores.accessibility=85 when gradeAdaptations is non-empty", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [{ gradeRange: "GRADE_9_12" }], // non-empty
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: "APPLY",
            examples: [{ id: "ex-1" }],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-lc-1");

      expect(result.pillarScores.accessibility).toBe(85);
    });

    it("returns pillarScores.accessibility=50 when gradeAdaptations is empty", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [], // empty → accessibilityScore=50
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: "APPLY",
            examples: [{ id: "ex-1" }],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-lc-1");

      expect(result.pillarScores.accessibility).toBe(50);
    });
  });

  // --------------------------------------------------------------------------
  // Publication gate — score < 60 blocks publishing
  // --------------------------------------------------------------------------
  describe("publishExperience gate at score boundary", () => {
    it("throws when score is in warnings range (45-59) even though baseline is met", async () => {
      // Same "warnings" configuration used above (score≈48, allClaimsMeetBaseline=true)
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...BASE_EXPERIENCE,
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: null,
            examples: [],
            simulations: [],
            animations: [],
          },
          {
            id: "c2",
            claimRef: "claim-2",
            bloomLevel: null,
            examples: [{ id: "ex-1" }],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      await expect(
        service.publishExperience("exp-lc-1", "user-1"),
      ).rejects.toThrow(/Cannot publish/);

      // The update must NOT have been called since the gate blocked publishing
      expect(prisma.learningExperience.update).not.toHaveBeenCalled();
    });
  });
});
