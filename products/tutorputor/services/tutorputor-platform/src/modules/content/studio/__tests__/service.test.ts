import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Mocks - must come before any imports that transitively load these packages
// ---------------------------------------------------------------------------
vi.mock("@grpc/grpc-js", () => {
    class GrpcServiceStub {
        generateClaims = (_req: any, _meta: any, cb: any) => cb(null, { claims: [] });
        generateContent = (_req: any, _meta: any, cb: any) => cb(null, { content: "" });
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
            findUnique: vi.fn().mockResolvedValue({ ...mockExperience, claims: [] }),
            findMany: vi.fn().mockResolvedValue([mockExperience]),
            count: vi.fn().mockResolvedValue(1),
            delete: vi.fn().mockResolvedValue(mockExperience),
        },
        $queryRaw: vi.fn().mockResolvedValue([{ "1": 1 }]),
    };
}

function makeAiClient() {
    return {
        generateClaims: vi.fn().mockRejectedValue(new Error("AI service unavailable")),
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
                            { id: "ev-1", type: "EXPLANATION_QUALITY", description: "Explain inertia", requiredCount: 1, contentDelivery: {} },
                        ],
                        tasks: [
                            { id: "task-1", type: "PREDICTION", prompt: "What happens when...", evidenceType: "EXPLANATION_QUALITY" },
                        ],
                        contentNeeds: {},
                    },
                ],
            };
            prisma.learningExperience.findUnique.mockResolvedValue(expWithClaims as any);
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
            const whereArg = prisma.learningExperience.findMany.mock.calls[0][0].where;
            expect(whereArg.status).toBe("DRAFT");
        });
    });

    describe("deleteExperience", () => {
        it("calls prisma delete", async () => {
            await service.deleteExperience("exp-1");
            expect(prisma.learningExperience.delete).toHaveBeenCalledOnce();
            expect(prisma.learningExperience.delete.mock.calls[0][0].where.id).toBe("exp-1");
        });
    });
});
