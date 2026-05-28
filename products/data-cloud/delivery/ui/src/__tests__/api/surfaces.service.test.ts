/**
 * Surfaces Service Contract Test
 *
 * P0: Verify Runtime Truth contract alignment between backend and frontend.
 * Backend returns surfaces as an array of SurfaceRecord objects.
 * Frontend schema and service must handle this array format correctly.
 *
 * @doc.type test
 * @doc.purpose Contract test for /surfaces endpoint
 * @doc.layer frontend
 */

import { describe, expect, it, beforeEach, vi } from 'vitest';
import { SurfaceRegistryEnvelopeSchema } from '../../contracts/schemas';

describe('Surfaces Service Contract (P0 Runtime Truth)', () => {
  describe('SurfaceRegistryEnvelopeSchema validates array format', () => {
    it('accepts valid array response from backend', () => {
      const validResponse = {
        data: {
          surfaces: [
            {
              surfaceId: 'ai.assist',
              state: 'LIVE',
              status: 'active',
              ownerPlane: 'intelligence',
              requiredDependencies: ['llm-gateway', 'memory-store'],
              dependencyProbes: [
                {
                  name: 'llm-gateway',
                  healthy: true,
                  status: 'healthy',
                  message: 'LLM gateway responding',
                },
              ],
              tenantScope: 'tenant',
              runtimeProfile: 'production',
              lastCheckedAt: '2024-01-15T10:30:00Z',
              evidence: { probeTimestamp: '2024-01-15T10:30:00Z' },
              limitations: '',
              actionsAllowed: ['query', 'suggest'],
            },
          ],
          count: 1,
          generatedAt: '2024-01-15T10:30:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-abc',
          apiVersion: '1.0.0',
          timestamp: '2024-01-15T10:30:00Z',
        },
      };

      const result = SurfaceRegistryEnvelopeSchema.parse(validResponse);
      expect(result.data.surfaces).toHaveLength(1);
      expect(result.data.surfaces[0].surfaceId).toBe('ai.assist');
      expect(result.data.count).toBe(1);
    });

    it('rejects old object/map format (regression test)', () => {
      const oldFormatResponse = {
        data: {
          surfaces: {
            'ai.assist': {
              state: 'LIVE',
              status: 'active',
            },
          },
          generatedAt: '2024-01-15T10:30:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-abc',
          apiVersion: '1.0.0',
          timestamp: '2024-01-15T10:30:00Z',
        },
      };

      expect(() => SurfaceRegistryEnvelopeSchema.parse(oldFormatResponse)).toThrow();
    });

    it('requires count field in response', () => {
      const missingCountResponse = {
        data: {
          surfaces: [
            {
              surfaceId: 'ai.assist',
              state: 'LIVE',
              status: 'active',
              ownerPlane: 'intelligence',
              requiredDependencies: [],
              dependencyProbes: [],
              tenantScope: 'global',
              runtimeProfile: 'local',
              lastCheckedAt: '2024-01-15T10:30:00Z',
              evidence: {},
              limitations: '',
              actionsAllowed: [],
            },
          ],
          // count field missing
          generatedAt: '2024-01-15T10:30:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-abc',
          apiVersion: '1.0.0',
          timestamp: '2024-01-15T10:30:00Z',
        },
      };

      expect(() => SurfaceRegistryEnvelopeSchema.parse(missingCountResponse)).toThrow();
    });

    it('accepts empty surfaces array', () => {
      const emptyResponse = {
        data: {
          surfaces: [],
          count: 0,
          generatedAt: '2024-01-15T10:30:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-abc',
          apiVersion: '1.0.0',
          timestamp: '2024-01-15T10:30:00Z',
        },
      };

      const result = SurfaceRegistryEnvelopeSchema.parse(emptyResponse);
      expect(result.data.surfaces).toHaveLength(0);
      expect(result.data.count).toBe(0);
    });

    it('accepts runtimePosture when present', () => {
      const withPostureResponse = {
        data: {
          surfaces: [
            {
              surfaceId: 'ai.assist',
              state: 'LIVE',
              status: 'active',
              ownerPlane: 'intelligence',
              requiredDependencies: [],
              dependencyProbes: [],
              tenantScope: 'global',
              runtimeProfile: 'production',
              lastCheckedAt: '2024-01-15T10:30:00Z',
              evidence: {},
              limitations: '',
              actionsAllowed: [],
              runtimePosture: {
                auth: 'jwt',
                audit: 'enabled',
                policy: 'enforced',
                metrics: 'prometheus',
                tracing: 'jaeger',
              },
            },
          ],
          count: 1,
          generatedAt: '2024-01-15T10:30:00Z',
        },
        meta: {
          requestId: 'req-123',
          tenantId: 'tenant-abc',
          apiVersion: '1.0.0',
          timestamp: '2024-01-15T10:30:00Z',
        },
      };

      const result = SurfaceRegistryEnvelopeSchema.parse(withPostureResponse);
      expect(result.data.surfaces[0].runtimePosture).toBeDefined();
      expect(result.data.surfaces[0].runtimePosture?.auth).toBe('jwt');
    });
  });
});
