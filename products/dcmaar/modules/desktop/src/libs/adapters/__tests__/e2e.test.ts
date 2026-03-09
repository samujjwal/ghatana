/**
 * End-to-end tests for complete adapter workflows.
 * Tests full lifecycle: init → snapshot → command → flush → close.
 */

import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { createAdapterManager } from '../adapterManager';
import type { WorkspaceBundle, ControlCommand } from '../types';

describe('E2E: Complete Adapter Workflows', () => {
  let mockBundle: WorkspaceBundle;

  beforeEach(() => {
    mockBundle = {
      workspaceVersion: '2.0.0',
      createdAt: new Date().toISOString(),
      sources: [{ type: 'mock', options: { refreshIntervalMs: 1000 } }],
      sinks: [{ type: 'mock', options: { persistToAudit: true } }],
      rbac: { role: 'operator', modules: ['status', 'config'] },
      keyring: [
        {
          kid: 'test-key',
          algorithm: 'ECDSA-P256',
          revoked: false,
          createdAt: new Date().toISOString(),
        },
      ],
      policies: { allowRemote: false, requireMTLS: false },
      signature: 'mock-signature',
    };
  });

  it('should complete full lifecycle: init → snapshot → command → flush → close', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'e2e-test',
      bundle: mockBundle,
    });

    // 1. Get initial snapshot
    const snapshot = await manager.getSnapshot();
    expect(snapshot).toHaveProperty('version');
    expect(snapshot).toHaveProperty('agents');

    // 2. Execute commands
    const commands: ControlCommand[] = [
      {
        id: 'cmd-1',
        category: 'config',
        payload: { action: 'update', key: 'value1' },
        metadata: {
          issuedBy: 'e2e-test',
          issuedAt: new Date().toISOString(),
          priority: 'high',
        },
      },
      {
        id: 'cmd-2',
        category: 'action',
        payload: { action: 'restart' },
        metadata: {
          issuedBy: 'e2e-test',
          issuedAt: new Date().toISOString(),
          priority: 'urgent',
        },
      },
    ];

    for (const cmd of commands) {
      await manager.executeCommand(cmd);
    }

    // 3. Flush commands
    await manager.flush();

    // 4. Health check
    const health = await manager.healthCheck();
    expect(health.source).toBeDefined();
    expect(health.sinks).toHaveLength(1);

    // 5. Close gracefully
    await manager.close();
  });

  it('should handle multi-sink routing', async () => {
    const multiSinkBundle: WorkspaceBundle = {
      ...mockBundle,
      sinks: [
        { type: 'mock', options: {} },
        { type: 'mock', options: {} },
      ],
    };

    const manager = await createAdapterManager({
      workspaceId: 'multi-sink-test',
      bundle: multiSinkBundle,
    });

    await manager.executeCommand({
      id: 'cmd-multi',
      category: 'config',
      payload: {},
      metadata: {
        issuedBy: 'test',
        issuedAt: new Date().toISOString(),
        priority: 'medium',
      },
    });

    await manager.flush();

    const health = await manager.healthCheck();
    expect(health.sinks).toHaveLength(2);

    await manager.close();
  });

  it('should handle errors gracefully', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'error-test',
      bundle: mockBundle,
    });

    // Test invalid command handling
    try {
      await manager.executeCommand({
        id: '',
        category: 'config',
        payload: null,
        metadata: {
          issuedBy: '',
          issuedAt: '',
          priority: 'low',
        },
      });
    } catch (error) {
      // Expected to handle gracefully
    }

    await manager.close();
  });

  it('should maintain state across operations', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'state-test',
      bundle: mockBundle,
    });

    // First snapshot
    const snapshot1 = await manager.getSnapshot();

    // Execute command
    await manager.executeCommand({
      id: 'cmd-state',
      category: 'config',
      payload: { test: true },
      metadata: {
        issuedBy: 'test',
        issuedAt: new Date().toISOString(),
        priority: 'medium',
      },
    });

    // Second snapshot (should reflect changes)
    const snapshot2 = await manager.getSnapshot();

    expect(snapshot1.collectedAt).not.toBe(snapshot2.collectedAt);

    await manager.close();
  });
});
