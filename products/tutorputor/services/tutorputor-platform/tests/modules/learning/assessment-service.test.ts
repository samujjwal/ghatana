import { describe, it, expect, vi, beforeEach } from "vitest";
import { createAssessmentService } from "../../../src/modules/learning/assessment-service";
import { aiClient } from "../../../src/clients/ai-client";

// Mock dependencies
const mockPrisma = {
  module: {
    findFirst: vi.fn(),
  },
  assessment: {
    findMany: vi.fn(),
    findFirst: vi.fn(),
    create: vi.fn(),
  },
  assessmentDraft: {
    create: vi.fn().mockResolvedValue({ id: 'draft-1' }),
  },
} as any;

// Mock AI Client
vi.mock("../../../src/clients/ai-client", () => ({
  aiClient: {
    generateAssessmentItems: vi.fn(),
  },
}));

describe("AssessmentService", () => {
  let service: any;

  beforeEach(() => {
    vi.clearAllMocks();
    service = createAssessmentService(mockPrisma);
  });

  describe("generateAssessmentItems", () => {
    it("should use AI client to generate items", async () => {
      // Setup module
      mockPrisma.module.findFirst.mockResolvedValue({
        id: "mod-1",
        title: "Math",
        slug: "math",
        learningObjectives: [
          { id: "obj-1", label: "Addition", taxonomyLevel: "apply" },
        ],
      });

      // Setup AI response
      vi.mocked(aiClient.generateAssessmentItems).mockResolvedValue({
        items: [
          {
            prompt: "What is 2+2?",
            choices: [{ label: "4", is_correct: true }],
            points: 10,
          },
        ],
      });

      // Execute
      const result = await service.generateAssessmentItems({
        tenantId: "t1",
        userId: "u1",
        moduleId: "mod-1",
        count: 1,
        objectiveIds: ["obj-1"],
        difficulty: "EASY",
      });

      // Verify
      expect(aiClient.generateAssessmentItems).toHaveBeenCalled();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].prompt).toBe("What is 2+2?");
      expect(result.model).toContain("tutorputor-ai");
    });

    it("should fallback to deterministic gen if AI fails", async () => {
      // Setup module
      mockPrisma.module.findFirst.mockResolvedValue({
        id: "mod-1",
        title: "Math",
        slug: "math",
        learningObjectives: [
          { id: "obj-1", label: "Addition", taxonomyLevel: "apply" },
        ],
      });

      // Setup AI failure
      vi.mocked(aiClient.generateAssessmentItems).mockRejectedValue(
        new Error("Timeout"),
      );

      // Execute
      const result = await service.generateAssessmentItems({
        tenantId: "t1",
        userId: "u1",
        moduleId: "mod-1",
        count: 1, // requesting 1 item
        objectiveIds: [],
        difficulty: "EASY",
      });

      // Verify
      expect(aiClient.generateAssessmentItems).toHaveBeenCalled();
      expect(result.items).toHaveLength(1);
      expect(result.items[0].choices[0].label).toContain("Unrelated fact"); // Check deterministic logic output
      expect(result.warnings).toContain(
        "AI service unavailable, using backup generator.",
      );
    });
  });
});
