/**
 * ExampleGenerationProcessor Tests
 * 
 * @doc.type test
 * @doc.purpose Verify example generation processor functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ExampleGenerationProcessor } from '../ExampleGenerationProcessor';
import {
  createMockLogger,
  createMockPrisma,
  createGrpcExamplesResponse,
} from '../../../../__tests__/test-utils';

describe('ExampleGenerationProcessor', () => {
  let processor: ExampleGenerationProcessor;
  let mockPrisma: ReturnType<typeof createMockPrisma>;
  let mockLogger: ReturnType<typeof createMockLogger>;
  let mockGrpcClient: any;

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    mockLogger = createMockLogger();
    mockGrpcClient = {
      generateExamples: vi.fn(),
    };

    processor = new ExampleGenerationProcessor(
      mockGrpcClient as any,
      mockPrisma as any,
      mockLogger as any
    );
  });

  describe('process', () => {
    it('should generate examples successfully', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          gradeLevel: 'GRADE_9_12',
          domain: 'PHYSICS',
          types: ['REAL_WORLD_APPLICATION'],
          count: 2,
        },
      };

      const grpcResponse = createGrpcExamplesResponse({
        examples: [
          { type: 'REAL_WORLD_APPLICATION', title: 'Example 1', content: { text: 'Content 1' } },
          { type: 'REAL_WORLD_APPLICATION', title: 'Example 2', content: { text: 'Content 2' } },
        ],
      });

      mockGrpcClient.generateExamples.mockResolvedValue(grpcResponse);
      mockPrisma.claimExample.create.mockResolvedValue({ id: 'ex-1' });

      await processor.process(job as any);

      expect(mockGrpcClient.generateExamples).toHaveBeenCalledWith(
        expect.objectContaining({
          requestId: expect.any(String),
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          gradeLevel: 'GRADE_9_12',
          domain: 'PHYSICS',
          types: ['REAL_WORLD_APPLICATION'],
          count: 2,
        }),
      );

      expect(mockPrisma.claimExample.create).toHaveBeenCalledTimes(2);
    });

    it('should use versioned artifact revisioning for regeneration', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          gradeLevel: 'GRADE_9_12',
          domain: 'PHYSICS',
          types: ['REAL_WORLD'],
          count: 1,
        },
      };

      const existingAsset = {
        id: 'asset-1',
        tenantId: 'tenant-1',
        slug: 'c1-worked-solution-primary',
        currentVersion: 1,
      };

      mockGrpcClient.generateExamples.mockResolvedValue(createGrpcExamplesResponse());
      mockPrisma.contentAsset.findUnique.mockResolvedValue(existingAsset);
      mockPrisma.contentAsset.update.mockResolvedValue({ ...existingAsset, currentVersion: 2 });
      mockPrisma.artifactManifest.create.mockResolvedValue({ id: 'manifest-1' });
      mockPrisma.claimExample.upsert.mockResolvedValue({ id: 'ex-1' });

      await processor.process(job as any);

      expect(mockPrisma.contentAsset.update).toHaveBeenCalledWith({
        where: { id: 'asset-1' },
        data: expect.objectContaining({
          currentVersion: 2,
          status: 'DRAFT',
        }),
      });

      expect(mockPrisma.artifactManifest.create).toHaveBeenCalledWith(
        expect.objectContaining({
          manifestType: 'WORKED_EXAMPLE',
          version: '2.0.0',
          schema: '1.0.0',
        }),
      );
    });

    it('should validate typed WorkedExampleManifest before persistence', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          gradeLevel: 'GRADE_9_12',
          domain: 'PHYSICS',
          types: ['REAL_WORLD'],
          count: 1,
        },
      };

      const grpcResponse = createGrpcExamplesResponse({
        examples: [
          {
            type: 'REAL_WORLD',
            title: 'Valid Example',
            content: {
              problemStatement: 'What is 2+2?',
              solution: { steps: [{ explanation: 'Add 2 and 2', checkpoint: 'Result is 4' }], finalAnswer: '4' },
            },
          },
        ],
      });

      mockGrpcClient.generateExamples.mockResolvedValue(grpcResponse);
      mockPrisma.contentAsset.findUnique.mockResolvedValue(null);
      mockPrisma.contentAsset.create.mockResolvedValue({ id: 'asset-1', currentVersion: 1 });
      mockPrisma.artifactManifest.create.mockResolvedValue({ id: 'manifest-1' });
      mockPrisma.claimExample.upsert.mockResolvedValue({ id: 'ex-1' });

      await processor.process(job as any);

      expect(mockPrisma.artifactManifest.create).toHaveBeenCalledWith(
        expect.objectContaining({
          manifest: expect.objectContaining({
            schemaVersion: '1.0.0',
            manifestType: 'WorkedExample',
          }),
          isValid: true,
          validationErrors: null,
        }),
      );
    });
  });
});
