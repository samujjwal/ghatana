/**
 * Integration tests for adapter system end-to-end flows.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createAdapterManager } from '../adapterManager';
import type { WorkspaceBundle } from '../types';

describe('Adapter Integration', () => {
  let mockBundle: WorkspaceBundle;

  beforeEach(() => {
    mockBundle = {
      workspaceVersion: '2.0.0',
      createdAt: new Date().toISOString(),
      sources: [
        {
          type: 'mock',
          options: {},
        },
      ],
      sinks: [
        {
          type: 'mock',
          options: {},
        },
      ],
      rbac: {
        role: 'operator',
        modules: ['status', 'config'],
      },
      keyring: [
        {
          kid: 'test-key',
          algorithm: 'ECDSA-P256',
          revoked: false,
          createdAt: new Date().toISOString(),
        },
      ],
      policies: {
        allowRemote: true,
        requireMTLS: false,
      },
      signature: 'mock-signature',
    };
  });

  it('should initialize adapter manager with valid bundle', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'test-workspace',
      bundle: mockBundle,
    });

    expect(manager).toBeDefined();
    await manager.close();
  });

  it('should fetch telemetry snapshot', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'test-workspace',
      bundle: mockBundle,
    });

    const snapshot = await manager.getSnapshot();

    expect(snapshot).toHaveProperty('version');
    expect(snapshot).toHaveProperty('collectedAt');
    expect(snapshot).toHaveProperty('agents');

    await manager.close();
  });

  it('should execute and flush commands', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'test-workspace',
      bundle: mockBundle,
    });

    await manager.executeCommand({
      id: 'cmd-1',
      category: 'config',
      payload: { test: true },
      metadata: {
        issuedBy: 'test',
        issuedAt: new Date().toISOString(),
        priority: 'medium',
      },
    });

    await manager.flush();
    await manager.close();
  });

  it('should perform health checks', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'test-workspace',
      bundle: mockBundle,
    });

    const health = await manager.healthCheck();

    expect(health).toHaveProperty('source');
    expect(health).toHaveProperty('sinks');

    await manager.close();
  });
});
