/**
 * Test suite for AIContentGenerationService
 *
 * @doc.type tests
 * @doc.purpose Unit tests for AI content generation
 * @doc.layer platform
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { AIContentGenerationService } from "../AIContentGenerationService";
import type { AIProxyService } from "@ghatana/tutorputor-contracts/v1/services";
import type { TutorResponsePayload } from "@ghatana/tutorputor-contracts/v1/types";

// Mock AI Proxy Service
function createMockAIProxyService(): AIProxyService {
  return {
    handleTutorQuery: vi.fn(),
    parseSimulationIntent: vi.fn(),
    explainSimulation: vi.fn(),
    generateLearningUnitDraft: vi.fn(),
    parseContentQuery: vi.fn(),
  };
}

describe("AIContentGenerationService", () => {
  let service: AIContentGenerationService;
  let mockAIProxy: ReturnType<typeof createMockAIProxyService>;

  beforeEach(() => {
    mockAIProxy = createMockAIProxyService();
    service = new AIContentGenerationService(mockAIProxy);
  });

  describe("generateConceptFromName", () => {
    it("generates concept with valid AI response", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description:
            "Photosynthesis is the process by which plants convert light energy into chemical energy.",
          learningObjectives: [
            {
              text: "Understand the basic process of photosynthesis",
              example: {
                title: "Leaf experiment",
                description: "Observe how leaves produce oxygen",
                type: "experiment",
              },
            },
          ],
          prerequisites: ["Basic chemistry", "Cell biology"],
          competencies: ["Explain photosynthesis", "Identify key components"],
          keywords: ["chlorophyll", "light energy", "glucose"],
          level: "FOUNDATIONAL",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Photosynthesis",
        "BIOLOGY",
        "tenant-1",
      );

      expect(result.name).toBe("Photosynthesis");
      expect(result.description).toContain("plants convert light energy");
      expect(result.learningObjectives).toHaveLength(1);
      expect(result.prerequisites).toContain("Basic chemistry");
    });

    it("handles plain text AI response", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: `
          Description: Gravity is a force that attracts objects toward each other.
          Learning Objectives:
          - Understand gravitational force
          - Calculate gravitational acceleration
          Prerequisites: Basic physics
          Keywords: force, mass, acceleration
          Level: FOUNDATIONAL
        `,
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Gravity",
        "PHYSICS",
        "tenant-1",
      );

      expect(result.name).toBe("Gravity");
      expect(result.description).toBeDefined();
    });

    it("retries on empty response", async () => {
      const emptyResponse: TutorResponsePayload = {
        answer: "",
        safety: { blocked: false },
      };

      const validResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description: "A valid description",
          learningObjectives: [{ text: "Learn something" }],
          prerequisites: [],
          competencies: [],
          keywords: [],
          level: "FOUNDATIONAL",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any)
        .mockResolvedValueOnce(emptyResponse)
        .mockResolvedValueOnce(validResponse);

      const result = await service.generateConceptFromName(
        "Test Concept",
        "TEST",
        "tenant-1",
      );

      expect(mockAIProxy.handleTutorQuery).toHaveBeenCalledTimes(2);
      expect(result.description).toBe("A valid description");
    });

    it("returns fallback after max retries with empty responses", async () => {
      const emptyResponse: TutorResponsePayload = {
        answer: "",
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValue(emptyResponse);

      const result = await service.generateConceptFromName("Test", "TEST", "tenant-1");

      expect(result.name).toBe("Test");
      expect(result.description).toContain("AI concept generation incomplete");
      expect(mockAIProxy.handleTutorQuery).toHaveBeenCalledTimes(3);
    });

    it("passes correct parameters to AI proxy", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description: "Test",
          learningObjectives: [{ text: "Test" }],
          prerequisites: [],
          competencies: [],
          keywords: [],
          level: "FOUNDATIONAL",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      await service.generateConceptFromName(
        "Algebra",
        "MATHEMATICS",
        "tenant-xyz",
      );

      expect(mockAIProxy.handleTutorQuery).toHaveBeenCalledWith(
        expect.objectContaining({
          tenantId: "tenant-xyz",
          userId: "system",
          locale: "en",
        }),
      );

      const call = (mockAIProxy.handleTutorQuery as any).mock.calls[0][0];
      expect(call.question).toContain("Algebra");
      expect(call.question).toContain("MATHEMATICS");
    });

    it("handles API errors gracefully", async () => {
      (mockAIProxy.handleTutorQuery as any).mockRejectedValue(
        new Error("AI service unavailable"),
      );

      const result = await service.generateConceptFromName("Test", "TEST", "tenant-1");

      expect(result.name).toBe("Test");
      expect(result.description).toContain("AI concept generation incomplete");
    });
  });

  describe("generateSimulationManifest", () => {
    it("generates simulation manifest with valid AI response", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          type: "PHYSICS_2D",
          manifest: {
            title: "Projectile Motion Simulation",
            description: "Explore projectile motion with different angles",
            entities: [
              {
                id: "ball",
                type: "projectile",
                properties: { mass: 1, radius: 0.1 },
              },
            ],
            interactions: [{ type: "gravity", config: { g: 9.8 } }],
          },
          estimatedTimeMinutes: 15,
          purpose: "Demonstrate projectile motion concepts",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateSimulationManifest(
        "A simulation showing projectile motion",
        "Projectile Motion",
        "PHYSICS",
        "tenant-1",
      );

      expect(result.type).toBe("PHYSICS_2D");
      expect(result.manifest.title).toContain("Projectile");
      expect(result.manifest.entities).toHaveLength(1);
      expect(result.estimatedTimeMinutes).toBe(15);
    });

    it("handles JSON in markdown code blocks", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: `Here's the simulation manifest:
\`\`\`json
{
  "type": "CHEMISTRY",
  "manifest": {
    "title": "Molecule Builder",
    "description": "Build molecules",
    "entities": [],
    "interactions": []
  },
  "estimatedTimeMinutes": 20,
  "purpose": "Learn molecular structures"
}
\`\`\``,
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateSimulationManifest(
        "Build molecules",
        "Molecular Structure",
        "CHEMISTRY",
        "tenant-1",
      );

      expect(result.type).toBe("CHEMISTRY");
      expect(result.manifest.title).toBe("Molecule Builder");
    });

    it("throws on invalid response format", async () => {
      const invalidResponse: TutorResponsePayload = {
        answer: "This is not valid JSON",
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(invalidResponse);

      await expect(
        service.generateSimulationManifest(
          "Test simulation",
          "Test",
          "TEST",
          "tenant-1",
        ),
      ).rejects.toThrow("AI simulation generation failed");
    });

    it("passes concept and domain context to AI", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          type: "BIOLOGY",
          manifest: {
            title: "Cell",
            description: "Cell",
            entities: [],
            interactions: [],
          },
          estimatedTimeMinutes: 10,
          purpose: "Learn cells",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      await service.generateSimulationManifest(
        "Show how cells divide",
        "Mitosis",
        "BIOLOGY",
        "tenant-1",
      );

      const call = (mockAIProxy.handleTutorQuery as any).mock.calls[0][0];
      expect(call.question).toContain("Show how cells divide");
      expect(call.question).toContain("Mitosis");
      expect(call.question).toContain("BIOLOGY");
    });
  });

  describe("Response Parsing", () => {
    it("extracts response text from answer field", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description: "Test description",
          learningObjectives: [],
          prerequisites: [],
          competencies: [],
          keywords: [],
          level: "FOUNDATIONAL",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Test",
        "TEST",
        "t1",
      );

      expect(result.description).toBe("Test description");
    });

    it("handles nested JSON response", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          response: {
            description: "Nested description",
            learningObjectives: [{ text: "Learn" }],
            prerequisites: [],
            competencies: [],
            keywords: [],
            level: "FOUNDATIONAL",
          },
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Test",
        "TEST",
        "t1",
      );

      // Should still work with nested response
      expect(result).toBeDefined();
    });
  });

  describe("Error Handling", () => {
    it("handles network timeout", async () => {
      (mockAIProxy.handleTutorQuery as any).mockRejectedValue(
        new Error("Request timeout"),
      );

      const result = await service.generateConceptFromName("Test", "TEST", "t1");

      expect(result.name).toBe("Test");
      expect(result.description).toContain("AI concept generation incomplete");
    });

    it("handles safety blocked response", async () => {
      const blockedResponse: TutorResponsePayload = {
        answer: "",
        safety: { blocked: true, reason: "Content policy violation" },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValue(blockedResponse);

      const result = await service.generateConceptFromName("Test", "TEST", "t1");

      expect(result.name).toBe("Test");
      expect(result.description).toContain("AI concept generation incomplete");
    });
  });

  describe("Level Classification", () => {
    it("classifies FOUNDATIONAL level correctly", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description: "Basic intro concept",
          learningObjectives: [{ text: "Learn basics" }],
          prerequisites: [],
          competencies: [],
          keywords: [],
          level: "FOUNDATIONAL",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Basic Math",
        "MATHEMATICS",
        "t1",
      );

      expect(result.level).toBe("FOUNDATIONAL");
    });

    it("classifies ADVANCED level correctly", async () => {
      const mockResponse: TutorResponsePayload = {
        answer: JSON.stringify({
          description: "Complex advanced topic",
          learningObjectives: [{ text: "Master advanced concepts" }],
          prerequisites: ["Intermediate knowledge"],
          competencies: [],
          keywords: [],
          level: "ADVANCED",
        }),
        safety: { blocked: false },
      };

      (mockAIProxy.handleTutorQuery as any).mockResolvedValueOnce(mockResponse);

      const result = await service.generateConceptFromName(
        "Quantum Physics",
        "PHYSICS",
        "t1",
      );

      expect(result.level).toBe("ADVANCED");
    });
  });
});
