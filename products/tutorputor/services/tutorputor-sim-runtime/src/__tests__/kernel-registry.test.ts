/**
 * Kernel Registry Unit Tests
 *
 * @doc.type test
 * @doc.purpose Test kernel registry routing and lifecycle management
 * @doc.layer product
 * @doc.pattern UnitTest
 */

import { describe, it, expect, vi } from 'vitest';
import { KernelRegistry } from '../kernel-registry';
import type { SimulationManifest } from '@ghatana/tutorputor-contracts/v1/simulation/types';
import type { SimKernelService } from '@ghatana/tutorputor-contracts/v1/simulation/services';

function createMockKernel(): SimKernelService {
  return {
    initialize: vi.fn(),
    step: vi.fn(),
    reset: vi.fn(),
    serialize: vi.fn(async () => ({})),
    deserialize: vi.fn(),
    dispose: vi.fn(),
    interpolate: vi.fn(),
    getAnalytics: vi.fn(),
  } as unknown as SimKernelService;
}

function createTestManifest(domain: string): SimulationManifest {
  return {
    id: 'test-manifest' as any,
    version: '1.0',
    domain: domain as any,
    title: 'Test Simulation',
    description: 'A test simulation',
    authorId: 'test-user' as any,
    tenantId: 'test-tenant' as any,
    canvas: { width: 800, height: 600 },
    playback: { defaultSpeed: 1, autoPlay: false },
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    schemaVersion: '1.0',
    initialEntities: [],
    steps: [],
    domainMetadata: { domain: domain as any },
  };
}

describe('KernelRegistry', () => {
  describe('registerKernel()', () => {
    it('should register a kernel by domain', () => {
      const kernel = createMockKernel();
      KernelRegistry.registerKernel('TEST_DOMAIN', {
        name: 'Test Kernel',
        description: 'Test',
        supportedTypes: [],
        factory: () => kernel,
        isAsync: false
      });
      expect(KernelRegistry.hasKernel('TEST_DOMAIN')).toBe(true);
    });
  });

  describe('getKernel()', () => {
    it('should return kernel for registered domain', async () => {
      const kernel = createMockKernel();
      KernelRegistry.registerKernel('TEST_DOMAIN_2', {
        name: 'Test Kernel 2',
        description: 'Test',
        supportedTypes: [],
        factory: () => kernel,
        isAsync: false
      });

      const manifest = createTestManifest('TEST_DOMAIN_2');
      const retrieved = await KernelRegistry.getKernel(manifest);
      expect(retrieved).toBe(kernel);
    });
  });
});
