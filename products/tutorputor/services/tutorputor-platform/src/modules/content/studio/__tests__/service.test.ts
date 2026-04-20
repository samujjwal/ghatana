import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Mocks - must come before any imports that transitively load these packages
// ---------------------------------------------------------------------------
vi.mock("@grpc/grpc-js", () => {
  class GrpcServiceStub {
    generateClaims = (_req: any, _meta: any, cb: any) =>
      cb(null, { claims: [] });
    generateContent = (_req: any, _meta: any, cb: any) =>
      cb(null, { content: "" });
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
    constructor(_fn: any) {}
    fire = (_fn: any, ...args: any[]) => Promise.resolve();
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

const mockExperience = {
  id: "exp-1",
  tenantId: "tenant-1",
  slug: "intro-to-physics",
  title: "Intro to Physics",
  description: "Learn physics basics",
  status: "DRAFT",
  version: 1,
  gradeRange: "GRADE_6_8",
  mathLevel: "PRE_ALGEBRA",
  rigorLevel: "PROCEDURAL",
  scaffoldingLevel: "MEDIUM",
  vocabularyComplexity: 6,
  readingLevel: 7,
  prerequisiteConcepts: [],
  estimatedTimeMinutes: 30,
  keywords: [],
  moduleId: null,
  authorId: "user-1",
  createdAt: new Date("2024-01-01"),
  updatedAt: new Date("2024-01-01"),
  claims: [],
};

function makePrisma() {
  return {
    learningExperience: {
      create: vi.fn().mockResolvedValue(mockExperience),
      findFirst: vi.fn().mockResolvedValue(mockExperience),
      findUnique: vi.fn().mockResolvedValue({
        ...mockExperience,
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      }),
      findMany: vi.fn().mockResolvedValue([mockExperience]),
      count: vi.fn().mockResolvedValue(1),
      delete: vi.fn().mockResolvedValue(mockExperience),
      update: vi
        .fn()
        .mockImplementation((args: any) =>
          Promise.resolve({ ...mockExperience, ...args.data }),
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
    experienceRevision: {
      create: vi.fn().mockResolvedValue({ id: "rev-1" }),
    },
    auditLog: {
      create: vi.fn().mockResolvedValue({ id: "audit-1" }),
    },
    evidenceBundleMetadata: {
      findMany: vi.fn().mockResolvedValue([]),
    },
    aiGenerationLog: {
      findFirst: vi.fn().mockResolvedValue({
        promptHash: "prompt-hash-1",
        model: "gpt-4.1",
        modelVersion: "2026-04",
        guardrailsVersion: "guardrails-v2",
        createdAt: new Date("2024-01-01T00:00:00.000Z"),
      }),
    },
    experienceAnalytics: {
      findUnique: vi.fn().mockResolvedValue(null),
    },
    $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
  };
}

function makeAiClient() {
  return {
    generateClaims: vi
      .fn()
      .mockRejectedValue(new Error("AI service unavailable")),
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------
describe("ContentStudioService", () => {
  let service: ReturnType<typeof createContentStudioService>;
  let prisma: ReturnType<typeof makePrisma>;

  beforeEach(() => {
    prisma = makePrisma();
    service = createContentStudioService(prisma as any, {
      openaiApiKey: "test-key",
    });
  });

  describe("checkHealth", () => {
    it("returns true when DB responds", async () => {
      const healthy = await service.checkHealth();
      expect(healthy).toBe(true);
    });

    it("returns false when DB throws", async () => {
      prisma.$queryRaw.mockRejectedValue(new Error("DB down"));
      const healthy = await service.checkHealth();
      expect(healthy).toBe(false);
    });
  });

  describe("createExperience", () => {
    it("creates an experience and returns operation result", async () => {
      const result = await service.createExperience({
        tenantId: "tenant-1",
        title: "Intro to Physics",
        description: "Learn physics",
        gradeRange: "grade_6_8",
        authorId: "user-1",
      });
      expect(prisma.learningExperience.create).toHaveBeenCalledOnce();
      expect(prisma.experienceEvent.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            experienceId: "exp-1",
            eventType: "CREATED",
            actorId: "user-1",
          }),
        }),
      );
      expect(result).toBeDefined();
      expect(result?.id ?? (result as any)?.experience?.id).toBeTruthy();
    });

    it("infers PHYSICS domain from title", async () => {
      await service.createExperience({
        tenantId: "tenant-1",
        title: "Newton's Laws of Motion",
        description: "Force and motion",
        gradeRange: "grade_9_12",
        authorId: "user-1",
      });
      const createCall = prisma.learningExperience.create.mock.calls[0][0];
      expect(createCall).toBeDefined();
    });

    it("infers MATH domain from title", async () => {
      await service.createExperience({
        tenantId: "tenant-1",
        title: "Introduction to Algebra",
        description: "Solving equations",
        gradeRange: "grade_6_8",
        authorId: "user-1",
      });
      expect(prisma.learningExperience.create).toHaveBeenCalledOnce();
    });
  });

  describe("getExperience", () => {
    it("returns mapped experience with claims", async () => {
      const result = await service.getExperience("exp-1");
      expect(result).not.toBeNull();
      expect(result?.id).toBe("exp-1");
      expect(result?.title).toBe("Intro to Physics");
      expect(result?.claims).toBeInstanceOf(Array);
      expect(result?.gradeAdaptation).toBeDefined();
    });

    it("maps DB gradeRange to contract format", async () => {
      const result = await service.getExperience("exp-1");
      expect(result?.gradeAdaptation.gradeRange).toBe("grade_6_8");
    });

    it("returns null when experience not found", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue(null);
      const result = await service.getExperience("missing");
      expect(result).toBeNull();
    });

    it("maps claims from DB to contract shape", async () => {
      const expWithClaims = {
        ...mockExperience,
        claims: [
          {
            id: "claim-1",
            statement: "Newton's first law states...",
            bloomLevel: "UNDERSTAND",
            orderIndex: 0,
            evidence: [
              {
                id: "ev-1",
                type: "EXPLANATION_QUALITY",
                description: "Explain inertia",
                requiredCount: 1,
                contentDelivery: {},
              },
            ],
            tasks: [
              {
                id: "task-1",
                type: "PREDICTION",
                prompt: "What happens when...",
                evidenceType: "EXPLANATION_QUALITY",
              },
            ],
            contentNeeds: {},
          },
        ],
      };
      prisma.learningExperience.findUnique.mockResolvedValue(
        expWithClaims as any,
      );
      const result = await service.getExperience("exp-1");
      expect(result?.claims).toHaveLength(1);
      expect(result?.claims[0].id).toBe("claim-1");
      expect(result?.claims[0].bloomLevel).toBe("understand");
      expect(result?.claims[0].evidenceRequirements).toHaveLength(1);
      expect(result?.claims[0].tasks).toHaveLength(1);
    });
  });

  describe("listExperiences", () => {
    it("returns paginated experiences", async () => {
      const result = await service.listExperiences({
        tenantId: "tenant-1",
      });
      expect(result.experiences).toHaveLength(1);
      expect(result.total).toBe(1);
    });

    it("filters by status", async () => {
      await service.listExperiences({
        tenantId: "tenant-1",
        status: "draft",
      });
      const whereArg =
        prisma.learningExperience.findMany.mock.calls[0][0].where;
      expect(whereArg.status).toBe("DRAFT");
    });
  });

  describe("deleteExperience", () => {
    it("calls prisma delete", async () => {
      await service.deleteExperience("exp-1");
      expect(prisma.learningExperience.delete).toHaveBeenCalledOnce();
      expect(prisma.learningExperience.delete.mock.calls[0][0].where.id).toBe(
        "exp-1",
      );
    });
  });

  // ------------------------------------------------------------------------
  // validateExperience — evidence-based validation
  // ------------------------------------------------------------------------
  describe("validateExperience", () => {
    const baseExp = {
      id: "exp-1",
      tenantId: "tenant-1",
      status: "DRAFT",
      version: 1,
      gradeRange: "GRADE_6_8",
      gradeAdaptations: [],
      claims: [],
      evidences: [],
      experienceTasks: [],
    };

    it("fails with canPublish=false when experience has no claims", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims: [],
      });
      const result = await service.validateExperience("exp-1");
      expect(result.canPublish).toBe(false);
      expect(result.status).toBe("invalid");
      expect(
        result.checks.some((c: any) => c.checkId === "claims-required"),
      ).toBe(true);
      expect(prisma.validationRecord.create).toHaveBeenCalledOnce();
    });

    it("fails when claims exist but none have tasks or artifacts", async () => {
      const claim = {
        id: "c1",
        claimRef: "claim-1",
        bloomLevel: "UNDERSTAND",
        examples: [],
        simulations: [],
        animations: [],
      };
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims: [claim],
        experienceTasks: [],
      });

      const result = await service.validateExperience("exp-1");
      expect(result.canPublish).toBe(false);
      // allClaimsMeetBaseline is false  — no tasks, no artifacts
      expect(result.pillarScores.experiential).toBe(0);
      expect(
        result.checks.find((c: any) => c.checkId === "claim-artifacts")?.passed,
      ).toBe(false);
      expect(
        result.checks.find((c: any) => c.checkId === "claim-artifacts")?.severity,
      ).toBe("error");
      expect(
        result.checks.find((c: any) => c.checkId === "claim-tasks")?.severity,
      ).toBe("error");
    });

    it("fails when tasks reference a claim that no longer exists", async () => {
      const claim = {
        id: "c1",
        claimRef: "claim-1",
        bloomLevel: "APPLY",
        examples: [{ id: "ex-1" }],
        simulations: [],
        animations: [],
      };
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims: [claim],
        experienceTasks: [{ id: "task-1", claimRef: "claim-orphan" }],
      });

      const result = await service.validateExperience("exp-1");

      expect(result.canPublish).toBe(false);
      expect(
        result.checks.find((c: any) => c.checkId === "task-claim-links")?.passed,
      ).toBe(false);
      expect(
        result.checks.find((c: any) => c.checkId === "task-claim-links")?.severity,
      ).toBe("error");
    });

    it("passes with canPublish=true when all claims have tasks and artifacts", async () => {
      const claim = {
        id: "c1",
        claimRef: "claim-1",
        bloomLevel: "APPLY",
        examples: [{ id: "ex-1" }],
        simulations: [],
        animations: [],
      };
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims: [claim],
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-1");
      expect(result.canPublish).toBe(true);
      expect(result.status).toBe("valid");
      expect(result.score).toBeGreaterThanOrEqual(60);
      expect(result.pillarScores.educational).toBe(100);
      expect(result.pillarScores.experiential).toBe(100);
    });

    it("computes correct pillar scores for partial coverage", async () => {
      // 2 claims: only one has artifacts; both have bloom; only one has tasks
      const claims = [
        {
          id: "c1",
          claimRef: "claim-1",
          bloomLevel: "REMEMBER",
          examples: [{ id: "ex-1" }],
          simulations: [],
          animations: [],
        },
        {
          id: "c2",
          claimRef: "claim-2",
          bloomLevel: "UNDERSTAND",
          examples: [],
          simulations: [],
          animations: [],
        },
      ];
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims,
        experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
      });

      const result = await service.validateExperience("exp-1");
      // claimsWithBloom=2/2, claimsWithTasks=1/2 → educational = 50*1 + 50*0.5 = 75
      expect(result.pillarScores.educational).toBe(75);
      // claimsWithArtifacts=1/2 → experiential = 50
      expect(result.pillarScores.experiential).toBe(50);
    });

    it("persists a ValidationRecord on every call", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({ ...baseExp });
      await service.validateExperience("exp-1");
      expect(prisma.validationRecord.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({ experienceId: "exp-1" }),
        }),
      );
    });

    it("records a VALIDATED experience event", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...baseExp,
        claims: [],
      });

      await service.validateExperience("exp-1");

      expect(prisma.experienceEvent.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            experienceId: "exp-1",
            eventType: "VALIDATED",
            actorId: "system",
          }),
        }),
      );
    });

    it("throws when experience not found", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue(null);
      await expect(service.validateExperience("missing")).rejects.toThrow(
        "Experience not found",
      );
    });
  });

  // ------------------------------------------------------------------------
  // publishExperience — validation-gated publishing
  // ------------------------------------------------------------------------
  describe("publishExperience", () => {
    it("throws when validation canPublish is false", async () => {
      // Empty claims → canPublish=false
      prisma.learningExperience.findUnique.mockResolvedValue({
        id: "exp-1",
        tenantId: "tenant-1",
        status: "DRAFT",
        gradeAdaptations: [],
        claims: [],
        evidences: [],
        experienceTasks: [],
      });

      await expect(
        service.publishExperience("exp-1", "user-1"),
      ).rejects.toThrow(/Cannot publish/);
      expect(prisma.learningExperience.update).not.toHaveBeenCalled();
    });

    it("includes blocking artifact-graph reasons in publish errors", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        id: "exp-1",
        tenantId: "tenant-1",
        status: "DRAFT",
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: "UNDERSTAND",
            examples: [],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [],
      });

      await expect(
        service.publishExperience("exp-1", "user-1"),
      ).rejects.toThrow(/missing tasks.*examples, simulations, or animations/i);
    });

    it("updates status to PUBLISHED when validation passes", async () => {
      const claim = {
        id: "c1",
        claimRef: "claim-1",
        bloomLevel: "APPLY",
        examples: [{ id: "ex-1" }],
        simulations: [],
        animations: [],
      };
      // First call (validateExperience) and second call (mapExperience) both use findUnique
      prisma.learningExperience.findUnique
        .mockResolvedValueOnce({
          id: "exp-1",
          tenantId: "tenant-1",
          status: "DRAFT",
          gradeAdaptations: [],
          claims: [claim],
          evidences: [],
          experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
        })
        // mapExperience re-fetches the experience after update
        .mockResolvedValueOnce({
          ...mockExperience,
          status: "PUBLISHED",
          claims: [],
        });

      await service.publishExperience("exp-1", "user-1");

      expect(prisma.learningExperience.update).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "exp-1" },
          data: expect.objectContaining({ status: "PUBLISHED" }),
        }),
      );
      expect(prisma.experienceEvent.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            experienceId: "exp-1",
            eventType: "PUBLISHED",
            actorId: "user-1",
          }),
        }),
      );
      expect(prisma.experienceRevision.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            experienceId: "exp-1",
            version: 1,
            authorId: "user-1",
            promptHash: "prompt-hash-1",
            diff: expect.objectContaining({
              validation: expect.objectContaining({
                source: "content_studio.validateExperience",
              }),
            }),
          }),
        }),
      );
      expect(prisma.auditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            tenantId: "tenant-1",
            actorId: "user-1",
            action: "experience_published",
            resourceType: "LearningExperience",
            resourceId: "exp-1",
          }),
        }),
      );
    });

    it("records publish provenance with validation and evidence bundle metadata", async () => {
      const claim = {
        id: "claim-1",
        text: "Explain the relationship between force and acceleration.",
        bloom: "apply",
        tasks: [{ id: "task-1" }],
        evidenceRequirements: [{ id: "evidence-1" }],
        contentNeeds: { examples: 1 },
      };

      prisma.evidenceBundleMetadata.findMany.mockResolvedValueOnce([
        {
          claimRef: "claim-1",
          bundleConfidence: 0.93,
          coverageScore: 0.88,
          contradictionDetected: false,
          freshnessOverall: "CURRENT",
          evidenceCount: 4,
          generatedAt: new Date("2024-01-02T00:00:00.000Z"),
          regeneratedAt: null,
          generationJobId: "job-123",
        },
      ]);
      prisma.aiGenerationLog.findFirst.mockResolvedValueOnce({
        promptHash: "prompt-hash-99",
        model: "gpt-4.1",
        modelVersion: "2026-04",
        guardrailsVersion: "guardrails-v2",
        createdAt: new Date("2024-01-03T00:00:00.000Z"),
      });
      prisma.learningExperience.findUnique
        .mockResolvedValueOnce({
          id: "exp-1",
          tenantId: "tenant-1",
          status: "DRAFT",
          gradeAdaptations: [],
          claims: [
            {
              id: "claim-1",
              claimRef: "claim-1",
              bloomLevel: "APPLY",
              examples: [{ id: "ex-1" }],
              simulations: [],
              animations: [],
            },
          ],
          evidences: [],
          experienceTasks: [{ id: "task-1", claimRef: "claim-1" }],
        })
        .mockResolvedValueOnce({
          ...mockExperience,
          status: "PUBLISHED",
          claims: [claim],
          gradeAdaptation: {
            gradeRange: "grade_6_8",
            mathLevel: "pre_algebra",
            rigorLevel: "procedural",
            scaffoldingLevel: "medium",
            vocabularyComplexity: 6,
            readingLevel: 7,
            prerequisiteConcepts: [],
          },
        });

      await service.publishExperience("exp-1", "publisher-7");

      expect(prisma.experienceRevision.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            experienceId: "exp-1",
            promptHash: "prompt-hash-99",
            diff: expect.objectContaining({
              validation: expect.objectContaining({
                source: "content_studio.validateExperience",
                version: "heuristic-v2",
              }),
              latestGeneration: expect.objectContaining({
                promptHash: "prompt-hash-99",
                model: "gpt-4.1",
              }),
              evidenceBundles: [
                expect.objectContaining({
                  claimRef: "claim-1",
                  bundleConfidence: 0.93,
                  coverageScore: 0.88,
                  generationJobId: "job-123",
                }),
              ],
              publishedBy: "publisher-7",
            }),
          }),
        }),
      );

      const experienceRevisionCall = prisma.experienceRevision.create.mock.calls[0][0];
      expect(experienceRevisionCall.data.diff.validation.score).toBeGreaterThanOrEqual(60);

      expect(prisma.auditLog.create).toHaveBeenCalledWith(
        expect.objectContaining({
          data: expect.objectContaining({
            metadata: expect.stringContaining('"bundleConfidence":0.93'),
          }),
        }),
      );
    });
  });

  describe("getExperienceAnalytics", () => {
    it("returns analytics enriched with latest validation and recent events", async () => {
      prisma.experienceAnalytics.findUnique.mockResolvedValue({
        experienceId: "exp-1",
        views: 12,
      });
      prisma.validationRecord.findFirst.mockResolvedValue({
        experienceId: "exp-1",
        overallStatus: "WARN",
        validatedAt: new Date("2024-02-01T00:00:00.000Z"),
        accessibilityScore: 85,
        authorityScore: 75,
        accuracyScore: 80,
        usefulnessScore: 70,
        harmlessnessScore: 100,
        suggestions: ["Add one more artifact"],
      });
      prisma.experienceEvent.findMany.mockResolvedValue([
        {
          id: "evt-1",
          eventType: "VALIDATED",
          actorId: "system",
          metadata: { score: 76 },
          createdAt: new Date("2024-02-02T00:00:00.000Z"),
        },
      ]);

      const analytics = await service.getExperienceAnalytics("exp-1");

      expect(prisma.experienceAnalytics.findUnique).toHaveBeenCalledWith({
        where: { experienceId: "exp-1" },
      });
      expect(analytics).toMatchObject({
        experienceId: "exp-1",
        views: 12,
        latestValidation: {
          status: "WARN",
          accessibilityScore: 85,
        },
        recentEvents: [
          expect.objectContaining({
            id: "evt-1",
            type: "VALIDATED",
            actorId: "system",
          }),
        ],
      });
      expect(analytics.latestValidation.validatedAt).toBe(
        "2024-02-01T00:00:00.000Z",
      );
      expect(analytics.recentEvents[0].createdAt).toBe(
        "2024-02-02T00:00:00.000Z",
      );
    });
  });

  // --------------------------------------------------------------------------
  // P0.2 — Type & Status Convergence
  // --------------------------------------------------------------------------
  describe("type convergence (P0.2)", () => {
    it("getExperience maps DRAFT → 'draft' (contract ExperienceStatus)", async () => {
      const result = await service.getExperience("exp-1");
      expect(result?.status).toBe("draft");
    });

    it("getExperience maps PUBLISHED → 'published'", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...mockExperience,
        status: "PUBLISHED",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });
      const result = await service.getExperience("exp-1");
      expect(result?.status).toBe("published");
    });

    it("listExperiences converts contract status 'review' → Prisma 'REVIEW'", async () => {
      await service.listExperiences({
        tenantId: "tenant-1",
        status: "review",
      });
      const whereArg =
        prisma.learningExperience.findMany.mock.calls[0][0].where;
      expect(whereArg.status).toBe("REVIEW");
    });

    it("listExperiences converts contract status 'archived' → Prisma 'ARCHIVED'", async () => {
      await service.listExperiences({
        tenantId: "tenant-1",
        status: "archived",
      });
      const whereArg =
        prisma.learningExperience.findMany.mock.calls[0][0].where;
      expect(whereArg.status).toBe("ARCHIVED");
    });

    it("validation checks use lowercase pillar values matching ValidationPillar contract", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        id: "exp-1",
        tenantId: "tenant-1",
        status: "DRAFT",
        gradeAdaptations: [],
        claims: [
          {
            id: "c1",
            claimRef: "claim-1",
            bloomLevel: "UNDERSTAND",
            examples: [],
            simulations: [],
            animations: [],
          },
        ],
        evidences: [],
        experienceTasks: [],
      });

      const result = await service.validateExperience("exp-1");

      const validPillars = new Set([
        "educational",
        "experiential",
        "safety",
        "technical",
        "accessibility",
      ]);
      for (const check of result.checks) {
        expect(validPillars.has(check.pillar)).toBe(true);
      }
    });

    it("validation result status uses contract literal union ('valid' | 'invalid' | 'warnings')", async () => {
      // Empty claims → should be "invalid"
      prisma.learningExperience.findUnique.mockResolvedValue({
        id: "exp-1",
        tenantId: "tenant-1",
        status: "DRAFT",
        gradeAdaptations: [],
        claims: [],
        evidences: [],
        experienceTasks: [],
      });

      const result = await service.validateExperience("exp-1");
      expect(["valid", "invalid", "warnings"]).toContain(result.status);
      expect(result.status).toBe("invalid");
    });
  });

  // --------------------------------------------------------------------------
  // P0.3 — Authoring Lifecycle Event Instrumentation
  // --------------------------------------------------------------------------
  describe("authoring lifecycle events (P0.3)", () => {
    it("archiveExperience records an ARCHIVED event (not UPDATED)", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...mockExperience,
        status: "ARCHIVED",
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.archiveExperience("exp-1");

      const eventCalls = prisma.experienceEvent.create.mock.calls;
      const archiveEvent = eventCalls.find(
        (call: any) => call[0]?.data?.eventType === "ARCHIVED",
      );
      expect(archiveEvent).toBeDefined();
    });

    it("adaptGrade records a GRADE_ADAPTED event", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...mockExperience,
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.adaptGrade("exp-1", {
        gradeRange: "grade_9_12",
        userId: "user-1",
      });

      const eventCalls = prisma.experienceEvent.create.mock.calls;
      const gradeEvent = eventCalls.find(
        (call: any) => call[0]?.data?.eventType === "GRADE_ADAPTED",
      );
      expect(gradeEvent).toBeDefined();
      expect(gradeEvent[0].data.actorId).toBe("user-1");
    });

    it("refineContent records a REFINED event (not CONTENT_CHANGED)", async () => {
      prisma.learningExperience.findUnique.mockResolvedValue({
        ...mockExperience,
        claims: [],
        evidences: [],
        experienceTasks: [],
        gradeAdaptations: [],
      });

      await service.refineContent("exp-1", {
        refinementPrompt: "Add more detail",
        userId: "user-2",
      });

      const eventCalls = prisma.experienceEvent.create.mock.calls;
      const refineEvent = eventCalls.find(
        (call: any) => call[0]?.data?.eventType === "REFINED",
      );
      expect(refineEvent).toBeDefined();
      expect(refineEvent[0].data.actorId).toBe("user-2");
    });

    it("generateClaims records a CLAIMS_GENERATED event", async () => {
      await service.generateClaims("exp-1", { maxClaims: 3 });

      const eventCalls = prisma.experienceEvent.create.mock.calls;
      const claimsEvent = eventCalls.find(
        (call: any) => call[0]?.data?.eventType === "CLAIMS_GENERATED",
      );
      expect(claimsEvent).toBeDefined();
    });

    it("getExperienceEvents returns timeline ordered by createdAt desc", async () => {
      prisma.experienceEvent.findMany.mockResolvedValue([
        {
          id: "evt-2",
          eventType: "VALIDATED",
          actorId: "system",
          metadata: { score: 80 },
          createdAt: new Date("2024-03-02"),
        },
        {
          id: "evt-1",
          eventType: "CREATED",
          actorId: "user-1",
          metadata: null,
          createdAt: new Date("2024-03-01"),
        },
      ]);

      const events = await service.getExperienceEvents("exp-1");

      expect(events).toHaveLength(2);
      expect(events[0].type).toBe("VALIDATED");
      expect(events[1].type).toBe("CREATED");
      expect(events[0].actorId).toBe("system");
    });

    it("getExperienceEvents filters by eventType when specified", async () => {
      await service.getExperienceEvents("exp-1", { eventType: "PUBLISHED" });

      expect(prisma.experienceEvent.findMany).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { experienceId: "exp-1", eventType: "PUBLISHED" },
        }),
      );
    });

    it("getExperienceEvents respects limit parameter", async () => {
      prisma.experienceEvent.findMany.mockResolvedValue([]);

      await service.getExperienceEvents("exp-1", { limit: 10 });

      expect(prisma.experienceEvent.findMany).toHaveBeenCalledWith(
        expect.objectContaining({ take: 10 }),
      );
    });
  });

  // =========================================================================
  // P1.1 – Canonical ContentAsset Schema Contract Tests
  // =========================================================================
  describe("canonical content asset schema (P1.1)", () => {
    it("ContentAssetType covers all 8 required asset classes", () => {
      const assetTypes: import("@tutorputor/contracts/v1/content-studio").ContentAssetType[] =
        [
          "explainer",
          "module",
          "example_set",
          "simulation",
          "animation",
          "assessment",
          "pathway",
          "reference_pack",
        ];
      expect(assetTypes).toHaveLength(8);
      expect(new Set(assetTypes).size).toBe(8);
    });

    it("ContentAssetStatus follows draft → validating → review → approved → published → archived lifecycle", () => {
      const statuses: import("@tutorputor/contracts/v1/content-studio").ContentAssetStatus[] =
        ["draft", "validating", "review", "approved", "published", "archived"];
      expect(statuses).toHaveLength(6);
      expect(statuses[0]).toBe("draft");
      expect(statuses[statuses.length - 1]).toBe("archived");
    });

    it("ContentBlockType covers all 12 typed block classes", () => {
      const blockTypes: import("@tutorputor/contracts/v1/content-studio").ContentBlockType[] =
        [
          "text_explainer",
          "worked_example",
          "data_table",
          "visual_sequence",
          "simulation_entry",
          "animation_entry",
          "question_set",
          "task",
          "reflection",
          "hint",
          "tutor_prompt",
          "evidence_capture",
        ];
      expect(blockTypes).toHaveLength(12);
      expect(new Set(blockTypes).size).toBe(12);
    });

    it("ArtifactManifestType covers 4 artifact classes", () => {
      const manifestTypes: import("@tutorputor/contracts/v1/content-studio").ArtifactManifestType[] =
        ["worked_example", "simulation", "animation", "assessment"];
      expect(manifestTypes).toHaveLength(4);
    });

    it("ContentAsset interface carries discovery fields", () => {
      const asset: import("@tutorputor/contracts/v1/content-studio").ContentAsset =
        {
          id: "ca-1",
          tenantId: "t-1",
          slug: "newtons-first-law",
          title: "Newton's First Law",
          assetType: "explainer",
          domain: "PHYSICS",
          status: "draft",
          currentVersion: 1,
          targetGrades: ["grade_9_12"],
          authorId: "author-1",
          riskLevel: "LOW",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };

      expect(asset.slug).toBe("newtons-first-law");
      expect(asset.assetType).toBe("explainer");
      expect(asset.status).toBe("draft");
      expect(asset.targetGrades).toContain("grade_9_12");
      expect(asset.semanticIndexStatus).toBeUndefined();
    });

    it("ContentAssetRevision captures immutable version snapshots", () => {
      const revision: import("@tutorputor/contracts/v1/content-studio").ContentAssetRevision =
        {
          id: "rev-1",
          assetId: "ca-1",
          version: 1,
          changeNote: "Initial creation",
          snapshot: { title: "Newton's First Law", blocks: [] },
          createdBy: "author-1",
          createdAt: new Date().toISOString(),
        };

      expect(revision.version).toBe(1);
      expect(revision.snapshot).toHaveProperty("title");
    });

    it("ContentBlock is typed with blockRef and claimRefs", () => {
      const block: import("@tutorputor/contracts/v1/content-studio").ContentBlock =
        {
          id: "blk-1",
          assetId: "ca-1",
          blockRef: "B1",
          blockType: "text_explainer",
          orderIndex: 0,
          title: "Introduction",
          payload: { text: "Newton's First Law states..." },
          claimRefs: ["C1"],
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };

      expect(block.blockType).toBe("text_explainer");
      expect(block.claimRefs).toContain("C1");
      expect(block.orderIndex).toBe(0);
    });

    it("ArtifactManifest supports validation state and provenance", () => {
      const manifest: import("@tutorputor/contracts/v1/content-studio").ArtifactManifest =
        {
          id: "am-1",
          assetId: "ca-1",
          manifestType: "simulation",
          version: "1.0.0",
          claimRef: "C1",
          manifest: { entities: [], steps: [], keyframes: [] },
          isValid: true,
          generatedBy: "ai",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };

      expect(manifest.manifestType).toBe("simulation");
      expect(manifest.isValid).toBe(true);
      expect(manifest.generatedBy).toBe("ai");
      expect(manifest.version).toBe("1.0.0");
    });

    it("ContentAsset carries legacy migration links", () => {
      const asset: import("@tutorputor/contracts/v1/content-studio").ContentAsset =
        {
          id: "ca-2",
          tenantId: "t-1",
          slug: "migrated-module",
          title: "Migrated Module",
          assetType: "module",
          domain: "MATH",
          status: "published",
          currentVersion: 3,
          targetGrades: ["grade_6_8"],
          authorId: "author-1",
          riskLevel: "LOW",
          legacyModuleId: "mod-old-1",
          legacyExperienceId: "exp-old-1",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        };

      expect(asset.legacyModuleId).toBe("mod-old-1");
      expect(asset.legacyExperienceId).toBe("exp-old-1");
      expect(asset.assetType).toBe("module");
    });
  });
});
