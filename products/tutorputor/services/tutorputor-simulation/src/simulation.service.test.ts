/**
 * Simulation Service Tests
 * Part of Execution Plan item #5: Improve Test Coverage to 60%
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SimulationService } from './simulation.service';
import { SimulationRuntimeService } from '@tutorputor/simulation-engine/runtime';

// Mock simulation engine
vi.mock('@tutorputor/simulation-engine/runtime', () => ({
  SimulationRuntimeService: vi.fn().mockImplementation(() => ({
    createSession: vi.fn(),
    runSession: vi.fn(),
    pauseSession: vi.fn(),
    getSessionState: vi.fn(),
  })),
}));

describe('SimulationService', () => {
  let service: SimulationService;
  let mockRuntime: SimulationRuntimeService;

  beforeEach(() => {
    mockRuntime = new SimulationRuntimeService({} as any);
    service = new SimulationService(mockRuntime);
  });

  describe('createSimulation', () => {
    it('should create a new simulation', async () => {
      const manifest = {
        id: 'sim-1',
        type: 'physics',
        title: 'Pendulum Demo',
        entities: [],
      };

      const result = await service.createSimulation(manifest);

      expect(result.id).toBeDefined();
      expect(result.manifest).toEqual(manifest);
    });
  });

  describe('runSimulation', () => {
    it('should start a simulation session', async () => {
      const sessionId = 'session-1';
      (mockRuntime.createSession as any).mockResolvedValue({ id: sessionId });
      (mockRuntime.runSession as any).mockResolvedValue({ status: 'running' });

      const result = await service.runSimulation('sim-1', { speed: 1 });

      expect(result.sessionId).toBe(sessionId);
      expect(result.status).toBe('running');
    });
  });

  describe('listSimulations', () => {
    it('should filter by domain', async () => {
      const simulations = [
        { id: 'sim-1', domain: 'physics' },
        { id: 'sim-2', domain: 'chemistry' },
      ];

      const result = await service.listSimulations({ domain: 'physics' });

      expect(result.every(s => s.domain === 'physics')).toBe(true);
    });
  });
});
