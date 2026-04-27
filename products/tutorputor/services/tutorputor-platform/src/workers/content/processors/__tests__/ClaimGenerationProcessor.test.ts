/**
 * ClaimGenerationProcessor Tests
 * 
 * Tests for the claim generation processor including:
 * - Basic claim generation
 * - Content needs analysis
 * - Follow-up job queuing
 * - Error handling
 * 
 * @doc.type test
 * @doc.purpose Verify claim generation processor functionality
 * @doc.layer test
 * @doc.pattern Unit Test
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ClaimGenerationProcessor } from '../ClaimGenerationProcessor';
import {
  DEFAULT_FEATURE_FLAGS,
  type ContentGenerationFlags,
} from '../../../../config/feature-flags';
import {
  createMockLogger,
  createMockPrisma,
  createMockQueue,
  createGrpcClaimsResponse,
  createExperience,
} from '../../../../__tests__/test-utils';

describe('ClaimGenerationProcessor', () => {
  let processor: ClaimGenerationProcessor;
  let mockPrisma: ReturnType<typeof createMockPrisma>;
  let mockQueue: ReturnType<typeof createMockQueue>;
  let mockLogger: ReturnType<typeof createMockLogger>;
  let mockGrpcClient: any;

  const createProcessor = (featureFlags?: ContentGenerationFlags) =>
    new ClaimGenerationProcessor(
      mockGrpcClient as any,
      mockPrisma as any,
      mockQueue as any,
      mockLogger as any,
      undefined,
      featureFlags,
    );

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    mockQueue = createMockQueue();
    mockLogger = createMockLogger();
    mockGrpcClient = {
      generateClaims: vi.fn(),
    };

    processor = createProcessor();
  });

  describe('process', () => {
    it('should generate claims from topic successfully', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Newton\'s First Law',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
          maxClaims: 5,
        },
      };

      const grpcResponse = createGrpcClaimsResponse({
        claims: [
          {
            claim_ref: 'C1',
            text: 'An object at rest stays at rest unless acted upon by an external force',
            bloom_level: 'UNDERSTAND',
            content_needs: {
              examples: { required: true, count: 2, types: ['REAL_WORLD'] },
              simulation: { required: true, interaction_type: 'INTERACTIVE' },
              animation: { required: false },
            },
          },
        ],
      });

      mockGrpcClient.generateClaims.mockResolvedValue(grpcResponse);
      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockGrpcClient.generateClaims).toHaveBeenCalledWith(
        expect.objectContaining({
          requestId: expect.any(String),
          tenantId: 'tenant-1',
          topic: 'Newton\'s First Law',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
          maxClaims: 5,
          context: expect.any(Object),
        }),
      );

      expect(mockPrisma.learningClaim.upsert).toHaveBeenCalledWith({
        where: {
          experienceId_claimRef: {
            experienceId: 'exp-1',
            claimRef: 'C1',
          },
        },
        create: expect.objectContaining({
          experienceId: 'exp-1',
          claimRef: 'C1',
          text: 'An object at rest stays at rest unless acted upon by an external force',
          bloomLevel: 'UNDERSTAND',
          contentNeeds: expect.any(Object),
        }),
        update: expect.any(Object),
      });
    });

    it('should queue example generation when contentNeeds.examples.required', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      const grpcResponse = createGrpcClaimsResponse({
        claims: [
          {
            claim_ref: 'C1',
            text: 'Test claim',
            content_needs: {
              examples: { required: true, count: 3, types: ['REAL_WORLD', 'STEP_BY_STEP'] },
              simulation: { required: false },
              animation: { required: false },
            },
          },
        ],
      });

      mockGrpcClient.generateClaims.mockResolvedValue(grpcResponse);
      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });
      mockQueue.add.mockResolvedValue({ id: 'example-job-1' });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-examples',
        expect.objectContaining({
          experienceId: 'exp-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          count: 3,
          types: ['REAL_WORLD', 'STEP_BY_STEP'],
        }),
        expect.any(Object),
      );
    });

    it('should queue simulation generation when contentNeeds.simulation.required', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      const grpcResponse = createGrpcClaimsResponse({
        claims: [
          {
            claim_ref: 'C1',
            text: 'Test claim',
            content_needs: {
              examples: { required: false },
              simulation: {
                required: true,
                interactionType: 'INTERACTIVE_EXPLORATION',
                complexity: 'INTERMEDIATE',
              },
              animation: { required: false },
            },
          },
        ],
      });

      mockGrpcClient.generateClaims.mockResolvedValue(grpcResponse);
      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });
      mockQueue.add.mockResolvedValue({ id: 'sim-job-1' });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-simulation',
        expect.objectContaining({
          experienceId: 'exp-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          domain: 'PHYSICS',
          tenantId: 'tenant-1',
          gradeLevel: 'GRADE_9_12',
          interactionType: 'INTERACTIVE_EXPLORATION',
          complexity: 'INTERMEDIATE',
        }),
        expect.objectContaining({
          jobId: 'generate-simulation:exp-1:C1',
        }),
      );
    });

    it('should queue animation generation when contentNeeds.animation.required', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      const grpcResponse = createGrpcClaimsResponse({
        claims: [
          {
            claim_ref: 'C1',
            text: 'Test claim',
            content_needs: {
              examples: { required: false },
              simulation: { required: false },
              animation: {
                required: true,
                animationType: 'CONCEPT_VISUALIZATION',
                durationSeconds: 120,
              },
            },
          },
        ],
      });

      processor = createProcessor({
        ...DEFAULT_FEATURE_FLAGS,
        enableAnimationGeneration: true,
      });

      mockGrpcClient.generateClaims.mockResolvedValue(grpcResponse);
      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });
      mockQueue.add.mockResolvedValue({ id: 'anim-job-1' });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-animation',
        expect.objectContaining({
          experienceId: 'exp-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          animationType: 'CONCEPT_VISUALIZATION',
          durationSeconds: 120,
        }),
        expect.objectContaining({
          jobId: 'generate-animation:exp-1:C1',
        }),
      );
    });

    it('should queue all three modalities when all are required', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      const grpcResponse = createGrpcClaimsResponse({
        claims: [
          {
            claim_ref: 'C1',
            text: 'Test claim',
            content_needs: {
              examples: { required: true, count: 2, types: ['REAL_WORLD'] },
              simulation: { required: true, interactionType: 'INTERACTIVE' },
              animation: { required: true, animationType: 'VISUALIZATION' },
            },
          },
        ],
      });

      processor = createProcessor({
        ...DEFAULT_FEATURE_FLAGS,
        enableAnimationGeneration: true,
      });

      mockGrpcClient.generateClaims.mockResolvedValue(grpcResponse);
      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });
      mockQueue.add.mockResolvedValue({ id: 'job-123' });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockQueue.add).toHaveBeenCalledTimes(3);
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-examples',
        expect.any(Object),
        expect.any(Object),
      );
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-simulation',
        expect.any(Object),
        expect.any(Object),
      );
      expect(mockQueue.add).toHaveBeenCalledWith(
        'generate-animation',
        expect.any(Object),
        expect.any(Object),
      );
    });

    it('should handle gRPC failure gracefully', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      mockGrpcClient.generateClaims.mockRejectedValue(new Error('gRPC timeout'));

      // Act & Assert
      await expect(processor.process(job as any)).rejects.toThrow('gRPC timeout');
      expect(mockLogger.error).toHaveBeenCalled();
    });

    it('should handle empty claims response', async () => {
      // Arrange
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test Topic',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      mockGrpcClient.generateClaims.mockResolvedValue({ claims: [] });

      // Act
      await processor.process(job as any);

      // Assert
      expect(mockPrisma.learningClaim.upsert).not.toHaveBeenCalled();
      expect(mockQueue.add).not.toHaveBeenCalled();
    });
  });

  describe('mapBloomLevel', () => {
    it('should map numeric bloom levels correctly', async () => {
      // Test through process method with different bloom levels
      const testCases = [
        { input: 1, expected: 'REMEMBER' },
        { input: 2, expected: 'REMEMBER' },
        { input: 3, expected: 'UNDERSTAND' },
        { input: 4, expected: 'APPLY' },
        { input: 5, expected: 'ANALYZE' },
        { input: 6, expected: 'EVALUATE' },
      ];

      for (const testCase of testCases) {
        const job = {
          id: 'job-1',
          data: {
            experienceId: 'exp-1',
            tenantId: 'tenant-1',
            topic: 'Test',
            domain: 'PHYSICS',
            gradeLevel: 'GRADE_9_12',
          },
        };

        mockGrpcClient.generateClaims.mockResolvedValue({
          claims: [{
            claim_ref: 'C1',
            text: 'Test',
            bloom_level: testCase.input,
            content_needs: { examples: { required: false } },
          }],
        });

        mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });

        await processor.process(job as any);

        expect(mockPrisma.learningClaim.upsert).toHaveBeenCalledWith(
          expect.objectContaining({
            create: expect.objectContaining({
              bloomLevel: testCase.expected,
            }),
          })
        );
      }
    });

    it('should map string bloom levels correctly', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      mockGrpcClient.generateClaims.mockResolvedValue({
        claims: [{
          claim_ref: 'C1',
          text: 'Test',
          bloom_level: 'analyze',
          content_needs: { examples: { required: false } },
        }],
      });

      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });

      await processor.process(job as any);

      expect(mockPrisma.learningClaim.upsert).toHaveBeenCalledWith(
        expect.objectContaining({
          create: expect.objectContaining({
            bloomLevel: 'ANALYZE',
          }),
        })
      );
    });

    it('should default to UNDERSTAND for invalid bloom levels', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          topic: 'Test',
          domain: 'PHYSICS',
          gradeLevel: 'GRADE_9_12',
        },
      };

      mockGrpcClient.generateClaims.mockResolvedValue({
        claims: [{
          claim_ref: 'C1',
          text: 'Test',
          bloom_level: 'invalid_level',
          content_needs: { examples: { required: false } },
        }],
      });

      mockPrisma.learningClaim.upsert.mockResolvedValue({ id: 'claim-1' });

      await processor.process(job as any);

      expect(mockPrisma.learningClaim.upsert).toHaveBeenCalledWith(
        expect.objectContaining({
          create: expect.objectContaining({
            bloomLevel: 'UNDERSTAND',
          }),
        })
      );
    });
  });
});
