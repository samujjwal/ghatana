/**
 * ContentValidationProcessor Tests
 * 
 * @doc.type test
 * @doc.purpose Verify content validation processor functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ContentValidationProcessor } from '../processors/ContentValidationProcessor';
import {
  createMockLogger,
  createMockPrisma,
  createValidationRecord,
  createGrpcValidationResponse,
} from '../../test-utils';

describe('ContentValidationProcessor', () => {
  let processor: ContentValidationProcessor;
  let mockPrisma: ReturnType<typeof createMockPrisma>;
  let mockLogger: ReturnType<typeof createMockLogger>;
  let mockGrpcClient: any;

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    mockLogger = createMockLogger();
    mockGrpcClient = {
      validateContent: vi.fn(),
    };

    processor = new ContentValidationProcessor(
      mockPrisma as any,
      mockGrpcClient as any,
      mockLogger as any
    );
  });

  describe('process', () => {
    it('should validate content and store PASS result', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
        },
      };

      mockPrisma.learningExperience.findUnique.mockResolvedValue({
        id: 'exp-1',
        tenantId: 'tenant-1',
        title: 'Test Experience',
        claims: [{
          id: 'claim-1',
          claimRef: 'C1',
          text: 'Test claim',
          examples: [],
          simulations: [],
          animations: [],
        }],
      });

      mockGrpcClient.validateContent.mockResolvedValue(createGrpcValidationResponse({
        status: 'PASS',
        overall_score: 90,
      }));

      mockPrisma.validationRecord.create.mockResolvedValue(createValidationRecord());
      mockPrisma.learningExperience.update.mockResolvedValue({ id: 'exp-1', status: 'REVIEW' });

      await processor.process(job as any);

      expect(mockGrpcClient.validateContent).toHaveBeenCalled();
      expect(mockPrisma.validationRecord.create).toHaveBeenCalled();
      expect(mockPrisma.learningExperience.update).toHaveBeenCalledWith({
        where: { id: 'exp-1' },
        data: { status: 'REVIEW' },
      });
    });

    it('should handle validation FAIL result', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
        },
      };

      mockPrisma.learningExperience.findUnique.mockResolvedValue({
        id: 'exp-1',
        claims: [],
      });

      mockGrpcClient.validateContent.mockResolvedValue(createGrpcValidationResponse({
        status: 'FAIL',
        overall_score: 45,
        details: {
          correctness: { score: 40, passed: false },
          completeness: { score: 50, passed: false },
          concreteness: { score: 45, passed: false },
          conciseness: { score: 45, passed: false },
        },
      }));

      mockPrisma.validationRecord.create.mockResolvedValue(createValidationRecord());

      await processor.process(job as any);

      expect(mockPrisma.learningExperience.update).not.toHaveBeenCalled();
      expect(mockLogger.warn).toHaveBeenCalledWith(
        expect.any(Object),
        'Validation failed for experience'
      );
    });

    it('should throw error if experience not found', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
        },
      };

      mockPrisma.learningExperience.findUnique.mockResolvedValue(null);

      await expect(processor.process(job as any)).rejects.toThrow('Experience not found');
    });
  });
});
