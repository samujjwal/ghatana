/**
 * Content Block Generator Tests
 *
 * @doc.type test
 * @doc.purpose Test content block generation from domain concepts
 * @doc.layer product
 * @doc.pattern Test
 */

import { describe, it, expect, vi, beforeEach, type Mock } from "vitest";
import { generateContentBlocks } from "../src/generators/content-block-generator";
import type { DomainConcept, ConceptId } from "@ghatana/tutorputor-contracts/v1/curriculum/types";
import type { TutorPrismaClient } from "../src/prisma-utils";

// =============================================================================
// Mock Data
// =============================================================================

const createMockConcept = (overrides: Partial<DomainConcept> = {}): DomainConcept => ({
    id: "phy_F_1" as ConceptId,
    name: "Scalars and Vectors",
    description: "Understand what physical quantities have only magnitude vs magnitude + direction.",
    domain: "PHYSICS",
    level: "FOUNDATIONAL",
    prerequisites: [] as ConceptId[],
    audienceTags: ["K-12", "Independent_Study"],
    keywords: ["scalar", "vector", "magnitude", "direction"],
    simulationMetadata: {
        simulationType: "interactive_visualization",
        recommendedInteractivity: "low",
        purpose: "Visualize difference between scalar vs vector",
        estimatedTimeMinutes: 10,
    },
    crossDomainLinks: [],
    learningObjectMetadata: {
        author: "TutorPutor",
        version: "1.0.0",
        status: "published",
        intendedRoles: ["student"],
        contexts: ["K-12", "Independent_Study"],
        difficulty: "INTRO",
        typicalLearningTimeMinutes: 20,
        learningObjectType: "lesson",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    },
    pedagogicalMetadata: {
        learningObjectives: [
            "Define scalar and vector quantities",
            "Identify examples of scalars and vectors",
            "Understand the concept of direction in vectors",
        ],
        competencies: ["scalar_analysis", "vector_analysis"],
        scaffoldingLevel: "standalone",
        accessibilityNotes: "Visual simulation with text descriptions",
    },
    ...overrides,
});

// =============================================================================
// Mock Prisma Client
// =============================================================================

const createMockPrisma = (): TutorPrismaClient => {
    const createdBlocks: unknown[] = [];

    return {
        moduleContentBlock: {
            deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
            create: vi.fn().mockImplementation((args) => {
                createdBlocks.push(args.data);
                return Promise.resolve(args.data);
            }),
        },
        // Expose for assertions
        _createdBlocks: createdBlocks,
    } as unknown as TutorPrismaClient;
};

// =============================================================================
// Tests
// =============================================================================

describe("Content Block Generator", () => {
    let mockPrisma: TutorPrismaClient;
    let createdBlocks: unknown[];

    beforeEach(() => {
        mockPrisma = createMockPrisma();
        createdBlocks = (mockPrisma as unknown as { _createdBlocks: unknown[] })._createdBlocks;
    });

    describe("generateContentBlocks", () => {
        it("should generate all block types for a complete concept", async () => {
            const concept = createMockConcept();
            const moduleId = "module-123";

            const result = await generateContentBlocks(mockPrisma, moduleId, concept);

            expect(result.blocksCreated).toBeGreaterThanOrEqual(5);
            expect(result.blockTypes).toContain("rich_text");
            expect(result.blockTypes).toContain("simulation");
            expect(result.blockTypes).toContain("ai_tutor_prompt");
            expect(result.blockTypes).toContain("exercise");
            expect(result.moduleId).toBe(moduleId);
            expect(result.warnings).toEqual([]);
        });

        it("should delete existing blocks before creating new ones", async () => {
            const concept = createMockConcept();
            const moduleId = "module-456";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            expect(mockPrisma.moduleContentBlock.deleteMany).toHaveBeenCalledWith({
                where: { moduleId },
            });
        });

        it("should create introduction block with concept name and description", async () => {
            const concept = createMockConcept({
                name: "Test Concept",
                description: "This is a test description.",
            });
            const moduleId = "module-789";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            const introBlock = createdBlocks.find(
                (b: { blockType?: string }) => b.blockType === "rich_text" && b.orderIndex === 0
            );
            expect(introBlock).toBeDefined();
            expect((introBlock as { payload: { content: string } }).payload.content).toContain("Test Concept");
            expect((introBlock as { payload: { content: string } }).payload.content).toContain("This is a test description.");
        });

        it("should create learning objectives block when objectives exist", async () => {
            const concept = createMockConcept({
                pedagogicalMetadata: {
                    learningObjectives: ["Objective 1", "Objective 2"],
                    competencies: [],
                    scaffoldingLevel: "standalone",
                    accessibilityNotes: "",
                },
            });
            const moduleId = "module-obj";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            const objectivesBlock = createdBlocks.find(
                (b: { blockType?: string; orderIndex?: number }) =>
                    b.blockType === "rich_text" && b.orderIndex === 1
            );
            expect(objectivesBlock).toBeDefined();
            expect((objectivesBlock as { payload: { content: string } }).payload.content).toContain("Objective 1");
            expect((objectivesBlock as { payload: { content: string } }).payload.content).toContain("Objective 2");
        });

        it("should skip simulation block when includeSimulation is false", async () => {
            const concept = createMockConcept();
            const moduleId = "module-no-sim";

            const result = await generateContentBlocks(mockPrisma, moduleId, concept, {
                includeSimulation: false,
            });

            expect(result.blockTypes).not.toContain("simulation");
        });

        it("should skip AI tutor block when includeAiTutor is false", async () => {
            const concept = createMockConcept();
            const moduleId = "module-no-ai";

            const result = await generateContentBlocks(mockPrisma, moduleId, concept, {
                includeAiTutor: false,
            });

            expect(result.blockTypes).not.toContain("ai_tutor_prompt");
        });

        it("should skip exercise block when includeExercise is false", async () => {
            const concept = createMockConcept();
            const moduleId = "module-no-exercise";

            const result = await generateContentBlocks(mockPrisma, moduleId, concept, {
                includeExercise: false,
            });

            expect(result.blockTypes).not.toContain("exercise");
        });

        it("should skip exercise block when no learning objectives exist", async () => {
            const concept = createMockConcept({
                pedagogicalMetadata: {
                    learningObjectives: [],
                    competencies: [],
                    scaffoldingLevel: "standalone",
                    accessibilityNotes: "",
                },
            });
            const moduleId = "module-no-obj";

            const result = await generateContentBlocks(mockPrisma, moduleId, concept);

            expect(result.blockTypes).not.toContain("exercise");
        });

        it("should create blocks with correct order indices", async () => {
            const concept = createMockConcept();
            const moduleId = "module-order";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            const orderIndices = createdBlocks.map(
                (b: { orderIndex?: number }) => b.orderIndex
            );
            // Should be sequential starting from 0
            expect(orderIndices[0]).toBe(0);
            for (let i = 1; i < orderIndices.length; i++) {
                expect(orderIndices[i]).toBe(orderIndices[i - 1]! + 1);
            }
        });

        it("should include prerequisites note when prerequisites exist", async () => {
            const concept = createMockConcept({
                prerequisites: ["phy_F_0" as ConceptId, "phy_F_0b" as ConceptId],
            });
            const moduleId = "module-prereq";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            const introBlock = createdBlocks.find(
                (b: { blockType?: string }) => b.blockType === "rich_text" && b.orderIndex === 0
            );
            expect((introBlock as { payload: { content: string } }).payload.content).toContain("2 prior concept(s)");
        });

        it("should include domain badge in introduction", async () => {
            const concept = createMockConcept({ domain: "CHEMISTRY" });
            const moduleId = "module-domain";

            await generateContentBlocks(mockPrisma, moduleId, concept);

            const introBlock = createdBlocks.find(
                (b: { orderIndex?: number }) => b.orderIndex === 0
            );
            expect((introBlock as { payload: { content: string } }).payload.content).toContain("CHEMISTRY");
        });

        it("should create key concepts block with keywords", async () => {
            const concept = createMockConcept({
                keywords: ["keyword1", "keyword2", "keyword3"],
            });
            const moduleId = "module-keywords";

            await generateContentBlocks(mockPrisma, moduleId, concept, {
                includeCompetencies: true,
            });

            // Find key concepts block (appears after simulation)
            const keyConceptsBlock = createdBlocks.find(
                (b: { payload?: { metadata?: { section?: string } } }) =>
                    b.payload?.metadata?.section === "key_concepts"
            );
            expect(keyConceptsBlock).toBeDefined();
        });
    });
});
