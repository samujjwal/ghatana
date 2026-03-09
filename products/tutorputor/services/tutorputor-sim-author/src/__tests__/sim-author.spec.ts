/**
 * SimAuthor Service Integration Tests
 *
 * Tests the AI-powered simulation manifest generation service
 * with comprehensive coverage for all generation scenarios.
 *
 * @doc.type test
 * @doc.purpose Comprehensive testing of AI simulation generation
 * @doc.layer product
 * @doc.pattern IntegrationTest
 */

import {
  describe,
  it,
  expect,
  beforeAll,
  afterAll,
  vi,
  beforeEach,
} from "vitest";
import type {
  SimulationManifest,
  SimulationDomain,
  GenerateManifestRequest,
  GenerateManifestResult,
  SimulationId,
  SimEntityId,
} from "@ghatana/tutorputor-contracts/v1/simulation/types";
import { createSimulationAuthorService } from "../src/service";

describe("SimAuthorService", () => {
  let service: any;
  let mockPrisma: any;
  let mockAIProvider: any;

  beforeEach(() => {
    // Mock Prisma client
    mockPrisma = {
      simulationManifest: {
        upsert: vi.fn(),
        findUnique: vi.fn(),
        create: vi.fn(),
      },
    };

    // Mock AI provider
    mockAIProvider = {
      complete: vi.fn(),
      chat: vi.fn(),
    };

    // Create service with test configuration
    service = createSimulationAuthorService(mockPrisma, {
      providers: [
        {
          name: "test-provider",
          config: {
            apiKey: "test-key",
            model: "gpt-4",
            maxTokens: 2000,
            temperature: 0.3,
          },
          isDefault: true,
        },
      ],
      maxRetries: 3,
      cacheEnabled: false, // Disable for testing
    });
  });

  describe("generateManifest", () => {
    const validRequest: GenerateManifestRequest = {
      prompt: "Create a simple pendulum simulation",
      domain: "PHYSICS",
      tenantId: "test-tenant",
      userId: "test-user",
      constraints: {
        maxSteps: 50,
        targetDuration: 300,
      },
    };

    it("should generate a valid physics simulation manifest", async () => {
      // Mock AI response
      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          id: "pendulum-sim" as SimulationId,
          version: "1.0",
          domain: "PHYSICS",
          title: "Simple Pendulum",
          description: "A simple pendulum simulation",
          canvas: {
            width: 800,
            height: 600,
            backgroundColor: "#ffffff",
          },
          playback: {
            defaultSpeed: 1.0,
            allowSpeedChange: true,
            allowScrubbing: true,
            autoPlay: false,
            loop: false,
          },
          initialEntities: [
            {
              id: "pivot" as SimEntityId,
              type: "rigidBody",
              x: 400,
              y: 100,
              fixed: true,
              mass: 0,
            },
            {
              id: "bob" as SimEntityId,
              type: "rigidBody",
              x: 400,
              y: 300,
              fixed: false,
              mass: 1,
            },
          ],
          steps: [
            {
              id: "step-1",
              timestamp: 0,
              actions: [
                {
                  type: "APPLY_FORCE",
                  entityId: "bob" as SimEntityId,
                  force: { x: 0, y: 9.8 },
                },
              ],
            },
          ],
          schemaVersion: "1.0",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
        confidence: 0.85,
      });

      const result: GenerateManifestResult =
        await service.generateManifest(validRequest);

      expect(result).toBeDefined();
      expect(result.manifest).toBeDefined();
      expect(result.manifest.domain).toBe("PHYSICS");
      expect(result.manifest.title).toBe("Simple Pendulum");
      expect(result.confidence).toBe(0.85);
      expect(result.needsReview).toBe(false);
      expect(result.manifest.initialEntities).toHaveLength(2);
      expect(result.manifest.steps).toHaveLength(1);
    });

    it("should handle different domains correctly", async () => {
      const domains: SimulationDomain[] = [
        "CHEMISTRY",
        "BIOLOGY",
        "CS_DISCRETE",
        "ECONOMICS",
      ];

      for (const domain of domains) {
        const domainRequest = { ...validRequest, domain };

        mockAIProvider.complete.mockResolvedValue({
          content: JSON.stringify({
            id: `${domain.toLowerCase()}-sim`,
            version: "1.0",
            domain,
            title: `${domain} Simulation`,
            canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
            playback: { duration: 300, frameRate: 30 },
            initialEntities: [],
            steps: [],
            schemaVersion: "1.0",
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
          confidence: 0.8,
        });

        const result = await service.generateManifest(domainRequest);

        expect(result.manifest.domain).toBe(domain);
        expect(result.manifest.title).toBe(`${domain} Simulation`);
      }
    });

    it("should mark low-confidence manifests for review", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          id: "low-confidence-sim",
          version: "1.0",
          domain: "PHYSICS",
          title: "Low Confidence Simulation",
          canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
          playback: { duration: 300, frameRate: 30 },
          initialEntities: [],
          steps: [],
          schemaVersion: "1.0",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
        confidence: 0.5, // Below threshold
      });

      const result = await service.generateManifest(validRequest);

      expect(result.needsReview).toBe(true);
      expect(result.confidence).toBe(0.5);
    });

    it("should handle malformed AI responses gracefully", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: "Invalid JSON response",
        confidence: 0.9,
      });

      await expect(service.generateManifest(validRequest)).rejects.toThrow();
    });

    it("should apply constraints to generated manifests", async () => {
      const constrainedRequest = {
        ...validRequest,
        constraints: {
          maxSteps: 10,
          targetDuration: 120,
          maxEntities: 3,
        },
      };

      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          id: "constrained-sim",
          version: "1.0",
          domain: "PHYSICS",
          title: "Constrained Simulation",
          canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
          playback: { duration: 120, frameRate: 30 },
          initialEntities: [
            { id: "entity-1", type: "rigidBody", x: 0, y: 0 },
            { id: "entity-2", type: "rigidBody", x: 100, y: 100 },
            { id: "entity-3", type: "rigidBody", x: 200, y: 200 },
          ],
          steps: Array.from({ length: 10 }, (_, i) => ({
            id: `step-${i + 1}`,
            timestamp: i * 12,
            actions: [],
          })),
          schemaVersion: "1.0",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
        confidence: 0.8,
      });

      const result = await service.generateManifest(constrainedRequest);

      expect(result.manifest.steps).toHaveLength(10);
      expect(result.manifest.initialEntities).toHaveLength(3);
      expect(result.manifest.playback.duration).toBe(120);
    });

    it("should cache manifests when enabled", async () => {
      // Create service with cache enabled
      const cachedService = createSimulationAuthorService(mockPrisma, {
        providers: [
          {
            name: "test-provider",
            config: { apiKey: "test-key", model: "gpt-4" },
            isDefault: true,
          },
        ],
        cacheEnabled: true,
      });

      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          id: "cached-sim",
          version: "1.0",
          domain: "PHYSICS",
          title: "Cached Simulation",
          canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
          playback: { duration: 300, frameRate: 30 },
          initialEntities: [],
          steps: [],
          schemaVersion: "1.0",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        }),
        confidence: 0.8,
      });

      mockPrisma.simulationManifest.upsert.mockResolvedValue({
        id: "cached-sim",
      });

      await cachedService.generateManifest(validRequest);

      expect(mockPrisma.simulationManifest.upsert).toHaveBeenCalledWith(
        expect.objectContaining({
          where: { id: "cached-sim" },
          create: expect.objectContaining({
            id: "cached-sim",
            tenantId: "test-tenant",
            authorId: "test-user",
            title: "Cached Simulation",
            domain: "PHYSICS",
          }),
        }),
      );
    });

    it("should handle AI service failures with retries", async () => {
      mockAIProvider.complete
        .mockRejectedValueOnce(new Error("AI service unavailable"))
        .mockRejectedValueOnce(new Error("AI service unavailable"))
        .mockResolvedValueOnce({
          content: JSON.stringify({
            id: "retry-sim",
            version: "1.0",
            domain: "PHYSICS",
            title: "Retry Success",
            canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
            playback: { duration: 300, frameRate: 30 },
            initialEntities: [],
            steps: [],
            schemaVersion: "1.0",
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
          }),
          confidence: 0.8,
        });

      const result = await service.generateManifest(validRequest);

      expect(result.manifest.title).toBe("Retry Success");
      expect(mockAIProvider.complete).toHaveBeenCalledTimes(3);
    });

    it("should fail after max retries", async () => {
      mockAIProvider.complete.mockRejectedValue(
        new Error("Persistent failure"),
      );

      await expect(service.generateManifest(validRequest)).rejects.toThrow(
        "Persistent failure",
      );
      expect(mockAIProvider.complete).toHaveBeenCalledTimes(3); // maxRetries
    });
  });

  describe("refineManifest", () => {
    const baseManifest: SimulationManifest = {
      id: "base-sim",
      version: "1.0",
      domain: "PHYSICS",
      title: "Base Simulation",
      canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
      playback: { duration: 300, frameRate: 30 },
      initialEntities: [
        { id: "entity-1", type: "rigidBody", x: 0, y: 0, mass: 1 },
      ],
      steps: [],
      schemaVersion: "1.0",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };

    it("should refine an existing manifest", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          ...baseManifest,
          title: "Refined Simulation",
          initialEntities: [
            ...baseManifest.initialEntities,
            { id: "entity-2", type: "rigidBody", x: 100, y: 100, mass: 2 },
          ],
        }),
        confidence: 0.9,
      });

      const result = await service.refineManifest({
        manifest: baseManifest,
        prompt: "Add another entity",
        userId: "test-user",
        tenantId: "test-tenant",
      });

      expect(result.manifest.title).toBe("Refined Simulation");
      expect(result.manifest.initialEntities).toHaveLength(2);
      expect(result.confidence).toBe(0.9);
    });

    it("should preserve manifest structure during refinement", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify(baseManifest),
        confidence: 0.8,
      });

      const result = await service.refineManifest({
        manifest: baseManifest,
        prompt: "Keep the same structure",
        userId: "test-user",
        tenantId: "test-tenant",
      });

      expect(result.manifest.id).toBe(baseManifest.id);
      expect(result.manifest.domain).toBe(baseManifest.domain);
      expect(result.manifest.schemaVersion).toBe(baseManifest.schemaVersion);
    });
  });

  describe("suggestParameters", () => {
    it("should suggest parameters for a simulation", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: JSON.stringify({
          suggestions: [
            { parameter: "gravity", value: 9.8, range: [1, 20], unit: "m/s²" },
            { parameter: "mass", value: 1.0, range: [0.1, 10], unit: "kg" },
            { parameter: "length", value: 2.0, range: [0.5, 5], unit: "m" },
          ],
        }),
        confidence: 0.85,
      });

      const result = await service.suggestParameters({
        manifest: {
          id: "param-sim",
          version: "1.0",
          domain: "PHYSICS",
          title: "Parameter Simulation",
          canvas: { width: 800, height: 600, backgroundColor: "#ffffff" },
          playback: { duration: 300, frameRate: 30 },
          initialEntities: [],
          steps: [],
          schemaVersion: "1.0",
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
        },
        userId: "test-user",
      });

      expect(result.suggestions).toHaveLength(3);
      expect(result.suggestions[0]).toEqual({
        parameter: "gravity",
        value: 9.8,
        range: [1, 20],
        unit: "m/s²",
      });
    });
  });

  describe("Health Check", () => {
    it("should return healthy when AI service is available", async () => {
      mockAIProvider.complete.mockResolvedValue({
        content: "test",
        confidence: 0.5,
      });

      const isHealthy = await service.checkHealth();
      expect(isHealthy).toBe(true);
    });

    it("should return unhealthy when AI service fails", async () => {
      mockAIProvider.complete.mockRejectedValue(
        new Error("Service unavailable"),
      );

      const isHealthy = await service.checkHealth();
      expect(isHealthy).toBe(false);
    });
  });
});
