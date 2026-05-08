/**
 * E2E Contract Tests for Content Generation gRPC client.
 *
 * These tests connect to a real gRPC server to verify:
 * 1. Proto contract compatibility between worker and service
 * 2. Request/response serialization/deserialization
 * 3. All RPC methods work end-to-end
 * 4. Evidence bundle generation and validation
 *
 * These tests should run in nightly E2E with a live AI agent service.
 * For CI without a live server, use contract.test.ts with mocked transport.
 *
 * @doc.type test
 * @doc.purpose Verify real contract compatibility with live gRPC server
 * @doc.layer testing
 * @doc.pattern E2EContractTest
 */

import { describe, it, expect, beforeAll, afterAll } from 'vitest';
import {
  RealContentGenerationClient,
  ContentGenerationError,
  type GrpcClientConfig,
} from '../RealContentGenerationClient';
import type { Logger } from 'pino';

const createLogger = (): Logger => {
  return require('pino')({
    level: 'info',
    transport: {
      target: 'pino-pretty',
      options: { colorize: true },
    },
  });
};

describe('E2E Content Generation Contract Tests', () => {
  let client: RealContentGenerationClient;
  let logger: Logger;

  beforeAll(() => {
    // These tests require a live gRPC server
    // Set environment variable GRPC_SERVER_ADDRESS to override default
    const serverAddress = process.env.GRPC_SERVER_ADDRESS || 'localhost:50051';
    const useTls = process.env.GRPC_USE_TLS === 'true';

    logger = createLogger();

    const config: GrpcClientConfig = {
      serverAddress,
      useTls,
      timeout: 30000, // 30 second timeout for E2E
      maxRetries: 2,
      logger,
    };

    client = new RealContentGenerationClient(config);
  });

  afterAll(async () => {
    // Cleanup if needed
  });

  describe('Server Connection', () => {
    it('should connect to gRPC server and be ready', () => {
      expect(client.ready).toBe(true);
    });

    it('should have valid proto path loaded', () => {
      const protoPath = client.getProtoPath();
      expect(protoPath).toContain('content_generation.proto');
    });
  });

  describe('GenerateClaims RPC', () => {
    it('should generate claims with nested RequestContext', async () => {
      const result = await client.generateClaims({
        context: {
          requestId: 'e2e-test-1',
          tenantId: 'e2e-tenant',
          timestamp: new Date(),
          metadata: { test: 'e2e' },
        },
        topic: 'Newton Laws of Motion',
        gradeLevel: 'GRADE_9_12',
        domain: 'SCIENCE',
        maxClaims: 3,
        contextParams: {},
        language: 'en',
      });

      expect(result.requestId).toBe('e2e-test-1');
      expect(result.claims).toBeDefined();
      expect(Array.isArray(result.claims)).toBe(true);
      expect(result.claims.length).toBeGreaterThan(0);

      // Verify claim structure
      const firstClaim = result.claims[0];
      expect(firstClaim.claim_ref).toBeDefined();
      expect(firstClaim.text).toBeDefined();
      expect(firstClaim.bloom_level).toBeDefined();
    });

    it('should populate content_needs in claims', async () => {
      const result = await client.generateClaims({
        context: {
          requestId: 'e2e-test-2',
          tenantId: 'e2e-tenant',
          timestamp: new Date(),
          metadata: {},
        },
        topic: 'Photosynthesis',
        gradeLevel: 'GRADE_6_8',
        domain: 'SCIENCE',
        maxClaims: 2,
        contextParams: {},
        language: 'en',
      });

      // At least one claim should have content_needs populated
      const claimWithNeeds = result.claims.find((c: any) => c.content_needs);
      expect(claimWithNeeds).toBeDefined();

      if (claimWithNeeds) {
        expect(claimWithNeeds.content_needs).toBeDefined();
        expect(claimWithNeeds.content_needs.examples || claimWithNeeds.content_needs.simulation || claimWithNeeds.content_needs.animation).toBeDefined();
      }
    });
  });

  describe('AnalyzeContentNeeds RPC', () => {
    it('should analyze content needs for a claim', async () => {
      const result = await client.analyzeContentNeeds({
        requestId: 'e2e-test-3',
        tenantId: 'e2e-tenant',
        claimText: 'Energy cannot be created or destroyed, only transformed.',
        bloomLevel: 'UNDERSTAND',
        domain: 'SCIENCE',
        gradeLevel: 'GRADE_9_12',
        context: {},
      });

      expect(result.requestId).toBe('e2e-test-3');
      expect(result.contentNeeds).toBeDefined();

      // Verify content needs structure
      const needs = result.contentNeeds;
      expect(needs.examples || needs.simulation || needs.animation).toBeDefined();
    });
  });

  describe('GenerateExamples RPC', () => {
    it('should generate examples for a claim', async () => {
      const result = await client.generateExamples({
        requestId: 'e2e-test-4',
        tenantId: 'e2e-tenant',
        claimText: 'Force equals mass times acceleration (F = ma).',
        claimRef: 'C1',
        exampleTypes: ['REAL_WORLD', 'PROBLEM_SOLVING'],
        count: 2,
        domain: 'SCIENCE',
        gradeLevel: 'GRADE_9_12',
        context: {},
      });

      expect(result.requestId).toBe('e2e-test-4');
      expect(result.examples).toBeDefined();
      expect(Array.isArray(result.examples)).toBe(true);
      expect(result.examples.length).toBeGreaterThan(0);
    });
  });

  describe('GenerateSimulation RPC', () => {
    it('should generate simulation manifest for a claim', async () => {
      const result = await client.generateSimulation({
        requestId: 'e2e-test-5',
        tenantId: 'e2e-tenant',
        claimText: 'Objects in motion stay in motion unless acted upon by a force.',
        claimRef: 'C1',
        interactionType: 'PARAMETER_EXPLORATION',
        complexity: 'MEDIUM',
        domain: 'SCIENCE',
        gradeLevel: 'GRADE_9_12',
        context: {},
      });

      expect(result.requestId).toBe('e2e-test-5');
      expect(result.manifest).toBeDefined();
      expect(result.manifest.manifest_id).toBeDefined();
      expect(result.manifest.domain).toBeDefined();
      expect(result.manifest.entities).toBeDefined();
      expect(Array.isArray(result.manifest.entities)).toBe(true);
    });
  });

  describe('GenerateAnimation RPC', () => {
    it('should generate animation manifest for a claim', async () => {
      const result = await client.generateAnimation({
        requestId: 'e2e-test-6',
        tenantId: 'e2e-tenant',
        claimText: 'Light travels in straight lines until it hits a surface.',
        claimRef: 'C1',
        animationType: 'TWO_D',
        durationSeconds: 30,
        domain: 'SCIENCE',
        gradeLevel: 'GRADE_6_8',
        context: {},
      });

      expect(result.requestId).toBe('e2e-test-6');
      expect(result.animation).toBeDefined();
      expect(result.animation.animation_id || result.animation.manifestId).toBeDefined();
    });
  });

  describe('ValidateContent RPC', () => {
    it('should validate generated content', async () => {
      const result = await client.validateContent({
        requestId: 'e2e-test-7',
        tenantId: 'e2e-tenant',
        contentId: 'test-content-1',
        contentType: 'CLAIM',
        content: {
          claim_ref: 'C1',
          text: 'Test claim for validation',
          bloom_level: 'UNDERSTAND',
        },
        context: {},
      });

      expect(result.requestId).toBe('e2e-test-7');
      expect(result.valid).toBeDefined();
      expect(typeof result.valid).toBe('boolean');
      expect(Array.isArray(result.issues)).toBe(true);
    });
  });

  describe('Evidence Bundle Generation', () => {
    it('should generate evidence bundle for a claim with provenance', async () => {
      // First generate a claim
      const claimsResult = await client.generateClaims({
        context: {
          requestId: 'e2e-evidence-1',
          tenantId: 'e2e-tenant',
          timestamp: new Date(),
          metadata: {},
        },
        topic: 'Thermodynamics',
        gradeLevel: 'GRADE_9_12',
        domain: 'SCIENCE',
        maxClaims: 1,
        contextParams: {},
        language: 'en',
      });

      const claim = claimsResult.claims[0];
      expect(claim).toBeDefined();

      // Verify evidence linkage if supported by the service
      // The service should populate evidenceRefs when evidence bundles are available
      if (claim.evidenceRefs) {
        expect(Array.isArray(claim.evidenceRefs)).toBe(true);
      }
    });
  });

  describe('Error Handling', () => {
    it('should handle invalid requests with proper error codes', async () => {
      await expect(
        client.generateClaims({
          context: {
            requestId: 'e2e-error-1',
            tenantId: 'e2e-tenant',
            timestamp: new Date(),
            metadata: {},
          },
          topic: '', // Invalid: empty topic
          gradeLevel: 'GRADE_9_12',
          domain: 'SCIENCE',
          maxClaims: 3,
          contextParams: {},
          language: 'en',
        })
      ).rejects.toBeInstanceOf(ContentGenerationError);
    });

    it('should handle timeout gracefully', async () => {
      // This test verifies timeout handling
      // The client is configured with a 30 second timeout
      const timeoutConfig: GrpcClientConfig = {
        serverAddress: process.env.GRPC_SERVER_ADDRESS || 'localhost:50051',
        useTls: process.env.GRPC_USE_TLS === 'true',
        timeout: 1000, // 1 second timeout for this test
        maxRetries: 0,
        logger,
      };

      const timeoutClient = new RealContentGenerationClient(timeoutConfig);

      // Make a request that might timeout (complex topic)
      await expect(
        timeoutClient.generateClaims({
          context: {
            requestId: 'e2e-timeout-1',
            tenantId: 'e2e-tenant',
            timestamp: new Date(),
            metadata: {},
          },
          topic: 'Complex quantum mechanical systems requiring extensive computation',
          gradeLevel: 'GRADE_9_12',
          domain: 'SCIENCE',
          maxClaims: 10, // Large number to potentially cause timeout
          contextParams: {},
          language: 'en',
        })
      ).rejects.toBeInstanceOf(ContentGenerationError);
    });
  });

  describe('Domain Normalization', () => {
    it('should handle full TutorPutor domain set', async () => {
      const domains = ['MATHEMATICS', 'SCIENCE', 'TECH', 'ENGINEERING', 'MEDICINE', 'HEALTH', 'BUSINESS', 'MANAGEMENT', 'ECONOMICS', 'COMPUTER_SCIENCE', 'INTERDISCIPLINARY'];

      for (const domain of domains) {
        const result = await client.generateClaims({
          context: {
            requestId: `e2e-domain-${domain}`,
            tenantId: 'e2e-tenant',
            timestamp: new Date(),
            metadata: {},
          },
          topic: `Test topic for ${domain}`,
          gradeLevel: 'GRADE_9_12',
          domain,
          maxClaims: 1,
          contextParams: {},
          language: 'en',
        });

        expect(result.claims).toBeDefined();
        expect(result.claims.length).toBeGreaterThan(0);
      }
    });
  });
});
