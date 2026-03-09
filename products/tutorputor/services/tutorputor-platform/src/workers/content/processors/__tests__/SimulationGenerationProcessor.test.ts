/**
 * SimulationGenerationProcessor Tests
 * 
 * @doc.type test
 * @doc.purpose Verify simulation generation processor functionality
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SimulationGenerationProcessor } from '../processors/SimulationGenerationProcessor';
import {
  createMockLogger,
  createMockPrisma,
  createGrpcSimulationResponse,
} from '../../test-utils';

describe('SimulationGenerationProcessor', () => {
  let processor: SimulationGenerationProcessor;
  let mockPrisma: ReturnType<typeof createMockPrisma>;
  let mockLogger: ReturnType<typeof createMockLogger>;
  let mockGrpcClient: any;

  beforeEach(() => {
    mockPrisma = createMockPrisma();
    mockLogger = createMockLogger();
    mockGrpcClient = {
      generateSimulation: vi.fn(),
    };

    processor = new SimulationGenerationProcessor(
      mockPrisma as any,
      mockGrpcClient as any,
      mockLogger as any
    );
  });

  describe('process', () => {
    it('should generate simulation and manifest successfully', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          interactionType: 'INTERACTIVE_EXPLORATION',
          complexity: 'INTERMEDIATE',
        },
      };

      const grpcResponse = createGrpcSimulationResponse({
        manifest: {
          domain: 'PHYSICS',
          title: 'Test Simulation',
          entities: [{ name: 'Object', type: 'DYNAMIC' }],
        },
        interaction_type: 'INTERACTIVE_EXPLORATION',
        goal: 'Explore physics concepts',
        entities: ['Object'],
      });

      mockGrpcClient.generateSimulation.mockResolvedValue(grpcResponse);
      mockPrisma.simulationManifest.upsert.mockResolvedValue({ id: 'manifest-1' });
      mockPrisma.claimSimulation.upsert.mockResolvedValue({ id: 'sim-1' });

      await processor.process(job as any);

      expect(mockGrpcClient.generateSimulation).toHaveBeenCalledWith({
        requestId: expect.any(String),
        tenantId: 'tenant-1',
        claimText: 'Test claim',
        interactionType: 'INTERACTIVE_EXPLORATION',
        complexity: 'INTERMEDIATE',
      });

      expect(mockPrisma.simulationManifest.upsert).toHaveBeenCalled();
      expect(mockPrisma.claimSimulation.upsert).toHaveBeenCalled();
    });

    it('should handle missing manifest gracefully', async () => {
      const job = {
        id: 'job-1',
        data: {
          experienceId: 'exp-1',
          tenantId: 'tenant-1',
          claimRef: 'C1',
          claimText: 'Test claim',
          interactionType: 'INTERACTIVE_EXPLORATION',
        },
      };

      mockGrpcClient.generateSimulation.mockResolvedValue({
        // Missing manifest
        interaction_type: 'INTERACTIVE_EXPLORATION',
      });

      await expect(processor.process(job as any)).rejects.toThrow('No manifest returned');
    });
  });
});
