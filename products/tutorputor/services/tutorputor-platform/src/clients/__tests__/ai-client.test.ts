/**
 * @doc.type test
 * @doc.purpose Unit tests for AiClient — circuit breaker behavior and gRPC method dispatch
 * @doc.layer product
 * @doc.pattern UnitTest
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ---------------------------------------------------------------------------
// Hoisted mock state — must be defined before vi.mock() factories reference them
// ---------------------------------------------------------------------------
const mocks = vi.hoisted(() => ({
  breakerFire: vi.fn(),
  breakerOn: vi.fn(),
  breakerFallback: vi.fn(),
}));

// ---------------------------------------------------------------------------
// Module mocks (hoisted to top by Vitest)
// ---------------------------------------------------------------------------
vi.mock("opossum", () => ({
  default: class MockCircuitBreaker {
    fire = mocks.breakerFire;
    on = mocks.breakerOn;
    fallback = mocks.breakerFallback;
  },
}));

vi.mock("@grpc/proto-loader", () => ({
  loadSync: vi.fn().mockReturnValue({}),
}));

vi.mock("@grpc/grpc-js", () => ({
  credentials: { createInsecure: vi.fn().mockReturnValue({}) },
  loadPackageDefinition: vi.fn().mockImplementation(() => ({
    tutorputor: {
      ai_learning: {
        AiLearningService: class MockAiLearningService {},
      },
      content_generation: {
        ContentGenerationService: class MockContentGenerationService {},
      },
    },
  })),
}));

vi.mock("@tutorputor/core/logger", () => ({
  createStandaloneLogger: vi.fn().mockReturnValue({
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  }),
}));

// Import after mocks
import { AiClient } from "../ai-client";
import type {
  GeneratePathRequest,
  GradeAssessmentRequest,
  GradeResponseRequest,
  GenerateItemsRequest,
  RemediationRequest,
  GenerateClaimsRequest,
  ValidateContentRequest,
  AnalyzeContentNeedsRequest,
  GenerateExamplesRequest,
  GenerateSimulationRequest,
} from "../ai-client";

describe("AiClient", () => {
  let client: AiClient;

  beforeEach(() => {
    vi.clearAllMocks();
    client = new AiClient();
  });

  describe("generateLearningPath", () => {
    it("fires breaker with learning client and correct method name", async () => {
      const response = {
        path_id: "p1",
        title: "Path",
        description: "",
        nodes: [],
      };
      mocks.breakerFire.mockResolvedValueOnce(response);

      const req: GeneratePathRequest = {
        subject: "Math",
        goal: "Understand calculus",
        learner_level: "beginner",
      };
      const result = await client.generateLearningPath(req);

      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(), // learning client instance
        "GenerateLearningPath",
        req,
      );
      expect(result).toEqual(response);
    });

    it("returns null when circuit breaker fallback activates", async () => {
      mocks.breakerFire.mockResolvedValueOnce(null);
      const result = await client.generateLearningPath({
        subject: "Physics",
        goal: "x",
        learner_level: "advanced",
      });
      expect(result).toBeNull();
    });
  });

  describe("gradeAssessment", () => {
    it("fires breaker with GradeAssessment method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        total_score: 80,
        feedback: [],
        overall_comments: "",
        passed: true,
      });
      const req: GradeAssessmentRequest = {
        assessment_id: "a1",
        student_id: "s1",
        answers: [],
      };
      await client.gradeAssessment(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "GradeAssessment",
        req,
      );
    });
  });

  describe("generateAssessmentItems", () => {
    it("fires breaker with GenerateAssessmentItems method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({ items: [] });
      const req: GenerateItemsRequest = {
        topic: "Algebra",
        objectives: ["solve equations"],
        difficulty: "medium",
        count: 5,
        learner_level: "beginner",
      };
      await client.generateAssessmentItems(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "GenerateAssessmentItems",
        req,
      );
    });
  });

  describe("suggestRemediation", () => {
    it("fires breaker with SuggestRemediation method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({ resources: [] });
      const req: RemediationRequest = {
        student_id: "s1",
        topic: "Fractions",
        struggle_concepts: ["division"],
      };
      await client.suggestRemediation(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "SuggestRemediation",
        req,
      );
    });
  });

  describe("generateClaims", () => {
    it("fires breaker with GenerateClaims method using content client", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        context: {},
        claims: [],
        generation_model: "",
        confidence_score: 0.9,
        warnings: [],
      });
      const req: GenerateClaimsRequest = {
        context: { request_id: "r1", tenant_id: "t1", metadata: {} },
        topic: "Photosynthesis",
        grade_level: "7",
        domain: "Science",
        max_claims: 5,
        context_params: {},
        language: "en",
      };
      await client.generateClaims(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "GenerateClaims",
        req,
      );
    });
  });

  describe("validateContent", () => {
    it("fires breaker with ValidateContent method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        context: {},
        result: {
          is_valid: true,
          confidence_score: 1.0,
          issues: [],
          suggestions: [],
          overall_assessment: "ok",
        },
      });
      const req: ValidateContentRequest = {
        context: { request_id: "r1", tenant_id: "t1", metadata: {} },
        validation_type: "claim",
        validation_rules: [],
      };
      await client.validateContent(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "ValidateContent",
        req,
      );
    });
  });

  describe("analyzeContentNeeds", () => {
    it("fires breaker with AnalyzeContentNeeds method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        context: {},
        analysis: {
          gaps: [],
          suggestions: [],
          recommended_examples_count: 0,
          recommended_simulations_count: 0,
          complexity_score: 0,
        },
      });
      const req: AnalyzeContentNeedsRequest = {
        context: { request_id: "r1", tenant_id: "t1", metadata: {} },
        claims: [],
        target_grade_level: "8",
        learning_objective: "x",
        available_resources: [],
      };
      await client.analyzeContentNeeds(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "AnalyzeContentNeeds",
        req,
      );
    });
  });

  describe("generateExamples", () => {
    it("fires breaker with GenerateExamples method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        context: {},
        examples: [],
        generation_model: "",
        confidence_score: 1,
        warnings: [],
      });
      const req: GenerateExamplesRequest = {
        context: { request_id: "r1", tenant_id: "t1", metadata: {} },
        claim_ref: "c1",
        claim_text: "text",
        grade_level: "5",
        count: 3,
        example_types: ["worked"],
        context_params: {},
      };
      await client.generateExamples(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "GenerateExamples",
        req,
      );
    });
  });

  describe("generateSimulation", () => {
    it("fires breaker with GenerateSimulation method", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        context: {},
        simulation: {},
        generation_model: "",
        confidence_score: 1,
        warnings: [],
      });
      const req: GenerateSimulationRequest = {
        context: { request_id: "r1", tenant_id: "t1", metadata: {} },
        claim_ref: "c1",
        claim_text: "text",
        grade_level: "9",
        simulation_type: "parameter_tuning",
        max_steps: 5,
        duration_minutes: 10,
        context_params: {},
      };
      await client.generateSimulation(req);
      expect(mocks.breakerFire).toHaveBeenCalledWith(
        expect.anything(),
        "GenerateSimulation",
        req,
      );
    });
  });

  describe("gradeResponse", () => {
    const req: GradeResponseRequest = {
      question: "What is Newton's second law?",
      response: "Force equals mass times acceleration.",
    };

    it("derives confidence from average feedback score when feedback is present", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        total_score: 80,
        passed: true,
        overall_comments: "Good",
        feedback: [
          { question_id: "q1", score: 90, feedback_text: "Excellent", correct_answer_explanation: "", is_correct: true },
          { question_id: "q2", score: 70, feedback_text: "Mostly right", correct_answer_explanation: "", is_correct: false },
        ],
      });

      const result = await client.gradeResponse(req);

      // confidence = (90 + 70) / 2 / 100 = 0.80
      expect(result).not.toBeNull();
      expect(result!.confidence).toBeCloseTo(0.8, 5);
    });

    it("falls back to total_score / 100 for confidence when feedback array is empty", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        total_score: 60,
        passed: false,
        overall_comments: "Needs work",
        feedback: [],
      });

      const result = await client.gradeResponse(req);

      // confidence = 60 / 100 = 0.60
      expect(result).not.toBeNull();
      expect(result!.confidence).toBeCloseTo(0.6, 5);
    });

    it("returns null when circuit breaker returns null", async () => {
      mocks.breakerFire.mockResolvedValueOnce(null);

      const result = await client.gradeResponse(req);

      expect(result).toBeNull();
    });

    it("clamps confidence to [0, 1] range", async () => {
      mocks.breakerFire.mockResolvedValueOnce({
        total_score: 150,
        passed: true,
        overall_comments: "",
        feedback: [],
      });

      const result = await client.gradeResponse(req);

      expect(result!.confidence).toBeLessThanOrEqual(1.0);
      expect(result!.confidence).toBeGreaterThanOrEqual(0.0);
    });
  });

  describe("circuit breaker event wiring", () => {
    it("registers open, halfOpen, close, and failure event handlers", () => {
      // Each call to `on` corresponds to one event registration
      expect(mocks.breakerOn).toHaveBeenCalledWith(
        "open",
        expect.any(Function),
      );
      expect(mocks.breakerOn).toHaveBeenCalledWith(
        "halfOpen",
        expect.any(Function),
      );
      expect(mocks.breakerOn).toHaveBeenCalledWith(
        "close",
        expect.any(Function),
      );
      expect(mocks.breakerOn).toHaveBeenCalledWith(
        "failure",
        expect.any(Function),
      );
    });

    it("registers a fallback handler", () => {
      expect(mocks.breakerFallback).toHaveBeenCalledWith(expect.any(Function));
    });
  });
});
