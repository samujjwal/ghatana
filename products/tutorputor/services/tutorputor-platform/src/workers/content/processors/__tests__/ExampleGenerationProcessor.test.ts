/**
 * ExampleGenerationProcessor Tests
 * 
 * @doc.type test
 * @doc.purpose Verify example generation processor functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ExampleGenerationProcessor } from '../processors/ExampleGenerationProcessor';
import {
  createMockLogger,
  createMockPrisma,
  createGrpcExamplesResponse,
} from '../../test-utils';

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
      mockPrisma as any,
      mockGrpcClient as any,
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

      expect(mockGrpcClient.generateExamples).toHaveBeenCalledWith({
        requestId: expect.any(String),
        tenantId: 'tenant-1',
        claimText: 'Test claim',
        types: ['REAL_WORLD_APPLICATION'],
        count: 2,
      });

      expect(mockPrisma.claimExample.create).toHaveBeenCalledTimes(2);
    });

    it('should delete existing examples before creating new ones', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          types: ['REAL_WORLD'],
          count: 1,
        },
      };

      mockGrpcClient.generateExamples.mockResolvedValue(createGrpcExamplesResponse());
      mockPrisma.claimExample.deleteMany.mockResolvedValue({ count: 2 });
      mockPrisma.claimExample.create.mockResolvedValue({ id: 'ex-1' });

      await processor.process(job as any);

      expect(mockPrisma.claimExample.deleteMany).toHaveBeenCalledWith({
        where: { experienceId: 'exp-1', claimRef: 'C1' },
      });
    });
  });
});
