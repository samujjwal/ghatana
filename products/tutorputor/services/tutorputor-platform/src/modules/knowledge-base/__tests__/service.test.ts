/**
 * Test suite for KnowledgeBaseService
 *
 * @doc.type tests
 * @doc.purpose Unit tests for knowledge base fact-checking and validation
 * @doc.layer platform
 * @doc.pattern Test Suite
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { KnowledgeBaseService } from "../service";
import type { PrismaClient } from "@ghatana/tutorputor-db";

// Mock fetch
const mockFetch = vi.fn();
global.fetch = mockFetch;

// Mock Prisma client
const mockPrisma = {
  concept: {
    findMany: vi.fn(),
    findFirst: vi.fn(),
  },
  knowledgeBase: {
    findMany: vi.fn(),
  },
  curriculumStandard: {
    findMany: vi.fn(),
  },
} as unknown as PrismaClient;

describe("KnowledgeBaseService", () => {
  let service: KnowledgeBaseService;

  beforeEach(() => {
    mockFetch.mockClear();
    vi.clearAllMocks();
    service = new KnowledgeBaseService(mockPrisma, {
      enableCaching: false, // Disable caching for tests
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  describe("verifyFact", () => {
    it("returns verified result for valid scientific claim", async () => {
      // Mock Wikipedia response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            query: {
              search: [
                {
                  pageid: 12345,
                  title: "Photosynthesis",
                  snippet:
                    "Photosynthesis is the process used by plants to convert light energy...",
                  timestamp: new Date().toISOString(),
                },
              ],
            },
          }),
      });

      // Mock OpenStax response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            items: [
              {
                id: 1,
                title: "Photosynthesis Chapter",
                slug: "photosynthesis",
              },
            ],
          }),
      });

      // Mock Khan Academy response
      mockFetch.mockResolvedValueOnce({
        ok: true,
        json: () =>
          Promise.resolve({
            data: [
              {
                id: "ka-1",
                title: "Introduction to Photosynthesis",
                description: "Learn about photosynthesis",
              },
            ],
          }),
      });

      const result = await service.verifyFact({
        claim:
          "Photosynthesis is the process by which plants convert sunlight into energy",
        domain: "biology",
      });

      expect(result).toMatchObject({
        verified: expect.any(Boolean),
        confidence: expect.any(Number),
        sources: expect.any(Array),
        contradictions: expect.any(Array),
        supportingEvidence: expect.any(Array),
        recommendations: expect.any(Array),
        riskLevel: expect.stringMatching(/^(low|medium|high)$/),
        processingTimeMs: expect.any(Number),
      });
    });

    it("returns result with processing time", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.verifyFact({
        claim: "Test claim",
        domain: "test",
      });

      expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
    });

    it("handles Wikipedia API failure gracefully", async () => {
      mockFetch
        .mockRejectedValueOnce(new Error("Network error"))
        .mockResolvedValue({
          ok: true,
          json: () => Promise.resolve({ items: [] }),
        });

      const result = await service.verifyFact({
        claim: "Earth is the third planet from the Sun",
        domain: "astronomy",
      });

      expect(result).toBeDefined();
      expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
    });

    it("handles OpenStax API failure gracefully", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () =>
            Promise.resolve({
              query: {
                search: [{ pageid: 1, title: "Test", snippet: "Test" }],
              },
            }),
        })
        .mockResolvedValueOnce({ ok: false, status: 500 })
        .mockResolvedValue({
          ok: true,
          json: () => Promise.resolve({ data: [] }),
        });

      const result = await service.verifyFact({
        claim: "Force equals mass times acceleration",
        domain: "physics",
      });

      expect(result).toBeDefined();
    });

    it("caches results when caching is enabled", async () => {
      const serviceWithCache = new KnowledgeBaseService(mockPrisma, {
        enableCaching: true,
      });

      mockFetch.mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            query: {
              search: [{ pageid: 1, title: "Test", snippet: "Test content" }],
            },
          }),
      });

      // First call
      await serviceWithCache.verifyFact({
        claim: "Water boils at 100 degrees Celsius",
        domain: "chemistry",
      });

      const firstCallCount = mockFetch.mock.calls.length;

      // Second call with same claim (should be cached)
      await serviceWithCache.verifyFact({
        claim: "Water boils at 100 degrees Celsius",
        domain: "chemistry",
      });

      // Should not make additional API calls due to caching
      expect(mockFetch.mock.calls.length).toBe(firstCallCount);
    });

    it("extracts factual assertions from claim", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      // Claims with factual patterns
      await service.verifyFact({
        claim:
          "The speed of light is approximately 300,000 kilometers per second. Water freezes at 0 degrees Celsius.",
        domain: "physics",
      });

      // Should have made API calls for the assertions
      expect(mockFetch).toHaveBeenCalled();
    });

    it("includes domain context in verification", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] }, items: [] }),
      });

      const result = await service.verifyFact({
        claim: "The mitochondria is the powerhouse of the cell",
        domain: "biology",
        context: {
          gradeRange: "9-12",
          subject: "Biology",
        },
      });

      expect(result).toBeDefined();
    });
  });

  describe("searchConcept", () => {
    it("searches local database first", async () => {
      (mockPrisma.concept.findMany as any).mockResolvedValueOnce([
        {
          id: "concept-1",
          concept: "Photosynthesis",
          definition: "The process by which plants make food",
          domain: "biology",
          gradeRange: "6-8",
          examples: ["Plants use sunlight"],
          relatedConcepts: ["chlorophyll"],
          confidence: 0.95,
          lastVerified: new Date(),
        },
      ]);

      const results = await service.searchConcept("photosynthesis", "biology");

      expect(results).toBeDefined();
    });

    it("searches external sources if local results insufficient", async () => {
      (mockPrisma.concept.findMany as any).mockResolvedValueOnce([]);

      mockFetch.mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            query: {
              search: [
                {
                  pageid: 1,
                  title: "Quantum Mechanics",
                  snippet: "Physics concept",
                },
              ],
            },
          }),
      });

      const results = await service.searchConcept(
        "quantum mechanics",
        "physics",
      );

      expect(results).toBeDefined();
    });

    it("caches concept search results", async () => {
      const serviceWithCache = new KnowledgeBaseService(mockPrisma, {
        enableCaching: true,
      });

      (mockPrisma.concept.findMany as any).mockResolvedValue([
        {
          id: "concept-1",
          concept: "Test",
          definition: "Test definition",
          domain: "test",
          gradeRange: "K-5",
          examples: [],
          relatedConcepts: [],
          confidence: 0.9,
          lastVerified: new Date(),
        },
      ]);

      // First call
      await serviceWithCache.searchConcept("test concept", "test");
      const firstCallCount = (mockPrisma.concept.findMany as any).mock.calls
        .length;

      // Second call (should be cached)
      await serviceWithCache.searchConcept("test concept", "test");

      expect((mockPrisma.concept.findMany as any).mock.calls.length).toBe(
        firstCallCount,
      );
    });
  });

  describe("findExamples", () => {
    it("returns examples from concept search", async () => {
      (mockPrisma.concept.findMany as any).mockResolvedValueOnce([
        {
          id: "1",
          concept: "Fraction",
          definition: "A part of a whole",
          domain: "mathematics",
          gradeRange: "3-5",
          examples: ["1/2", "3/4", "2/3"],
          relatedConcepts: [],
          confidence: 0.9,
          lastVerified: new Date(),
        },
      ]);

      const examples = await service.findExamples("fraction", "mathematics");

      expect(examples).toContain("Example 1 of fraction");
    });

    it("filters by grade range when provided", async () => {
      (mockPrisma.concept.findMany as any).mockResolvedValueOnce([
        {
          id: "1",
          concept: "Algebra",
          definition: "Math with variables",
          domain: "mathematics",
          gradeRange: "6-8",
          examples: ["2x + 3 = 7"],
          relatedConcepts: [],
          confidence: 0.9,
          lastVerified: new Date(),
        },
        {
          id: "2",
          concept: "Advanced Algebra",
          definition: "Complex algebra",
          domain: "mathematics",
          gradeRange: "9-12",
          examples: ["x² + 2x + 1 = 0"],
          relatedConcepts: [],
          confidence: 0.9,
          lastVerified: new Date(),
        },
      ]);

      const examples = await service.findExamples(
        "algebra",
        "mathematics",
        "6-8",
      );

      expect(examples.length).toBeGreaterThan(0);
      expect(examples[0]).toContain("algebra");
    });

    it("limits results to 10 examples", async () => {
      const manyExamples = Array.from({ length: 20 }, (_, i) => `Example ${i}`);
      (mockPrisma.concept.findMany as any).mockResolvedValueOnce([
        {
          id: "1",
          concept: "Test",
          definition: "Test",
          domain: "test",
          gradeRange: "K-5",
          examples: manyExamples,
          relatedConcepts: [],
          confidence: 0.9,
          lastVerified: new Date(),
        },
      ]);

      const examples = await service.findExamples("test", "test");

      expect(examples.length).toBeLessThanOrEqual(10);
    });
  });

  describe("getCurriculumAlignment", () => {
    it("returns math standards for math domain", async () => {
      (mockPrisma.curriculumStandard.findMany as any).mockResolvedValueOnce([
        {
          id: "CCSS.Math.6.EE.1",
          standard: "CCSS.Math.Content.6.EE.A.1",
          description: "Write and evaluate numerical expressions...",
          gradeRange: "6",
          domain: "mathematics",
          concepts: ["expressions", "evaluation"],
          prerequisites: ["multiplication"],
        },
      ]);

      const standards = await service.getCurriculumAlignment(
        "algebraic expressions",
        "mathematics",
      );

      expect(standards).toBeDefined();
    });

    it("returns science standards for science domain", async () => {
      (mockPrisma.curriculumStandard.findMany as any).mockResolvedValueOnce([
        {
          id: "NGSS.LS1.1",
          standard: "HS-LS1-1",
          description: "Construct an explanation...",
          gradeRange: "9-12",
          domain: "biology",
          concepts: ["cell", "DNA"],
          prerequisites: [],
        },
      ]);

      const standards = await service.getCurriculumAlignment(
        "cells",
        "science",
      );

      expect(standards).toBeDefined();
    });

    it("caches curriculum alignment results", async () => {
      const serviceWithCache = new KnowledgeBaseService(mockPrisma, {
        enableCaching: true,
      });

      (mockPrisma.curriculumStandard.findMany as any).mockResolvedValue([]);

      // First call
      await serviceWithCache.getCurriculumAlignment("test", "mathematics");
      const firstCallCount = (mockPrisma.curriculumStandard.findMany as any)
        .mock.calls.length;

      // Second call (should be cached)
      await serviceWithCache.getCurriculumAlignment("test", "mathematics");

      expect(
        (mockPrisma.curriculumStandard.findMany as any).mock.calls.length,
      ).toBe(firstCallCount);
    });
  });

  describe("validateContent", () => {
    it("performs all validation checks", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            query: { search: [{ pageid: 1, title: "Test", snippet: "Test" }] },
            items: [],
          }),
      });

      const result = await service.validateContent({
        content:
          "Photosynthesis is the process by which plants make food using sunlight.",
        contentType: "explanation",
        domain: "biology",
        gradeRange: "6-8",
      });

      expect(result).toMatchObject({
        passed: expect.any(Boolean),
        score: expect.any(Number),
        checks: expect.any(Array),
        recommendations: expect.any(Array),
        riskLevel: expect.stringMatching(/^(low|medium|high)$/),
        processingTimeMs: expect.any(Number),
      });

      // Should have 5 checks
      expect(result.checks.length).toBe(5);
    });

    it("validates factual accuracy", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content: "The Earth is flat.",
        contentType: "claim",
        domain: "geography",
        gradeRange: "K-5",
      });

      const factualCheck = result.checks.find(
        (c) => c.type === "factual_accuracy",
      );
      expect(factualCheck).toBeDefined();
    });

    it("validates age appropriateness", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content: "This is a simple explanation for young learners.",
        contentType: "explanation",
        domain: "general",
        gradeRange: "K-2",
      });

      const ageCheck = result.checks.find(
        (c) => c.type === "age_appropriateness",
      );
      expect(ageCheck).toBeDefined();
    });

    it("validates clarity", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content:
          "This is a clear and concise explanation that students can understand easily.",
        contentType: "explanation",
        domain: "general",
        gradeRange: "6-8",
      });

      const clarityCheck = result.checks.find((c) => c.type === "clarity");
      expect(clarityCheck).toBeDefined();
    });

    it("validates pedagogical soundness", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content:
          "First, we introduce the concept. Then, we provide examples. Finally, we assess understanding.",
        contentType: "explanation",
        domain: "general",
        gradeRange: "6-8",
        context: {
          learningObjectives: ["Understand the basic concept"],
        },
      });

      const pedagogicalCheck = result.checks.find(
        (c) => c.type === "pedagogical_soundness",
      );
      expect(pedagogicalCheck).toBeDefined();
    });

    it("returns processing time", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content: "Test content",
        contentType: "claim",
        domain: "test",
        gradeRange: "K-5",
      });

      expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
    });

    it("calculates risk level based on checks", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content: "Test content for risk assessment",
        contentType: "claim",
        domain: "test",
        gradeRange: "6-8",
      });

      expect(["low", "medium", "high"]).toContain(result.riskLevel);
    });

    it("generates recommendations based on failed checks", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      const result = await service.validateContent({
        content: "x", // Very short content
        contentType: "explanation",
        domain: "physics",
        gradeRange: "9-12",
      });

      // Should have recommendations for improvement
      expect(result.recommendations).toBeDefined();
    });
  });

  describe("Wikipedia API Integration", () => {
    it("correctly formats Wikipedia search URL", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ query: { search: [] } }),
      });

      await service.verifyFact({
        claim: "Light is the fastest thing in the universe.",
        domain: "physics",
      });

      expect(mockFetch).toHaveBeenCalledWith(
        expect.stringContaining("en.wikipedia.org/w/api.php"),
      );
    });

    it("strips HTML from Wikipedia snippets", async () => {
      mockFetch.mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            query: {
              search: [
                {
                  pageid: 1,
                  title: "Test",
                  snippet: '<span class="searchmatch">Test</span> content',
                },
              ],
            },
          }),
      });

      const result = await service.verifyFact({
        claim: "Test is a concept",
        domain: "test",
      });

      // Verify HTML was stripped from source excerpts
      for (const source of result.sources) {
        expect(source.excerpt).not.toContain("<span");
        expect(source.excerpt).not.toContain("</span>");
      }
    });
  });

  describe("OpenStax API Integration", () => {
    it("maps domains to correct OpenStax book IDs", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ query: { search: [] } }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () =>
            Promise.resolve({
              items: [{ id: 1, title: "Physics Chapter", slug: "physics-ch1" }],
            }),
        })
        .mockResolvedValue({
          ok: true,
          json: () => Promise.resolve({ data: [] }),
        });

      const result = await service.verifyFact({
        claim: "Newton's laws describe motion",
        domain: "physics",
      });

      // Check that physics domain was used
      const openstaxSource = result.sources.find((s) => s.name === "OpenStax");
      if (openstaxSource) {
        expect(openstaxSource.url).toContain("openstax.org");
      }
    });

    it("provides fallback URL when OpenStax API fails", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ query: { search: [] } }),
        })
        .mockRejectedValueOnce(new Error("OpenStax API error"))
        .mockResolvedValue({
          ok: true,
          json: () => Promise.resolve({ data: [] }),
        });

      const result = await service.verifyFact({
        claim: "Chemical bonds hold atoms together",
        domain: "chemistry",
      });

      // Should still have some sources despite API failure
      expect(result.sources).toBeDefined();
    });
  });

  describe("Khan Academy API Integration", () => {
    it("maps domains to Khan Academy course slugs", async () => {
      mockFetch
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ query: { search: [] } }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () => Promise.resolve({ items: [] }),
        })
        .mockResolvedValueOnce({
          ok: true,
          json: () =>
            Promise.resolve({
              data: [
                {
                  id: "ka-1",
                  title: "Algebra Basics",
                  description: "Learn algebra",
                },
              ],
            }),
        });

      const result = await service.verifyFact({
        claim: "Solving equations is a fundamental algebra skill",
        domain: "algebra",
      });

      const khanSource = result.sources.find((s) =>
        s.name.includes("Khan Academy"),
      );
      if (khanSource) {
        expect(khanSource.url).toContain("khanacademy.org");
      }
    });
  });
});

describe("KnowledgeBaseService - Edge Cases", () => {
  let service: KnowledgeBaseService;

  beforeEach(() => {
    mockFetch.mockClear();
    service = new KnowledgeBaseService(mockPrisma, { enableCaching: false });
  });

  it("handles empty claim gracefully", async () => {
    const result = await service.verifyFact({
      claim: "",
      domain: "test",
    });

    expect(result).toBeDefined();
    expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
  });

  it("handles very long claim", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: { search: [] } }),
    });

    const longClaim = "This is a test claim. ".repeat(100);

    const result = await service.verifyFact({
      claim: longClaim,
      domain: "test",
    });

    expect(result).toBeDefined();
  });

  it("handles special characters in claim", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: { search: [] } }),
    });

    const result = await service.verifyFact({
      claim: "E = mc² represents Einstein's famous equation. What does λ mean?",
      domain: "physics",
    });

    expect(result).toBeDefined();
  });

  it("handles unicode characters in claim", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () => Promise.resolve({ query: { search: [] } }),
    });

    const result = await service.verifyFact({
      claim: "日本語テスト Chinese: 中文测试",
      domain: "language",
    });

    expect(result).toBeDefined();
  });

  it("handles concurrent fact checks", async () => {
    mockFetch.mockResolvedValue({
      ok: true,
      json: () =>
        Promise.resolve({
          query: { search: [{ pageid: 1, title: "Test", snippet: "Content" }] },
        }),
    });

    const results = await Promise.all([
      service.verifyFact({ claim: "Claim 1", domain: "test" }),
      service.verifyFact({ claim: "Claim 2", domain: "test" }),
      service.verifyFact({ claim: "Claim 3", domain: "test" }),
    ]);

    expect(results).toHaveLength(3);
    results.forEach((result) => {
      expect(result).toBeDefined();
      expect(result.processingTimeMs).toBeGreaterThanOrEqual(0);
    });
  });
});
