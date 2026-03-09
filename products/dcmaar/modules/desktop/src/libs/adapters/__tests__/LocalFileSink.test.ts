/**
 * Unit tests for LocalFileSink adapter.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { LocalFileSink } from '../sinks/LocalFileSink';
import type { SinkContext, ControlCommand } from '../types';
import { createLogger } from '../logger';
import { createTracer } from '../tracer';
import { IndexedDBQueue } from '../queue';

describe('LocalFileSink', () => {
  let mockContext: SinkContext;
  let sink: LocalFileSink;

  beforeEach(async () => {
    const queue = new IndexedDBQueue({
      dbName: 'test-queue',
      storeName: 'commands',
      maxSizeMB: 10,
    });

    mockContext = {
      workspaceId: 'test-workspace',
      keyring: {
        verify: vi.fn(),
        sign: vi.fn().mockResolvedValue('mock-signature'),
        getPublicKey: vi.fn(),
        listKeys: vi.fn(),
      },
      queue,
      logger: createLogger({ level: 'debug' }),
      tracer: createTracer({ serviceName: 'test', enabled: false }),
    };

    sink = new LocalFileSink({
      outDir: '/test/output',
      rotateMB: 5,
      rotateMinutes: 15,
    });
  });

  it('should initialize successfully', async () => {
    await expect(sink.init(mockContext)).resolves.toBeUndefined();
  });

  it('should have correct kind', () => {
    expect(sink.kind).toBe('file');
  });

  it('should enqueue commands', async () => {
    await sink.init(mockContext);

    const command: ControlCommand = {
      id: 'cmd-1',
      category: 'config',
      payload: { key: 'value' },
      metadata: {
        issuedBy: 'test-user',
        issuedAt: new Date().toISOString(),
        priority: 'medium',
      },
    };

    await expect(sink.enqueue(command)).resolves.toBeUndefined();
  });

  it('should flush commands and return acks', async () => {
    await sink.init(mockContext);

    const commands: ControlCommand[] = [
      {
        id: 'cmd-1',
        category: 'config',
        payload: {},
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'high',
        },
      },
      {
        id: 'cmd-2',
        category: 'action',
        payload: {},
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'low',
        },
      },
    ];

    for (const cmd of commands) {
      await sink.enqueue(cmd);
    }

    const acks = await sink.flush();

    expect(acks).toHaveLength(2);
    expect(acks[0]).toHaveProperty('commandId', 'cmd-1');
    expect(acks[1]).toHaveProperty('commandId', 'cmd-2');
  });

  it('should handle health check', async () => {
    await sink.init(mockContext);
    const health = await sink.healthCheck();

    expect(health).toHaveProperty('healthy');
    expect(health).toHaveProperty('lastCheck');
    expect(health.details).toHaveProperty('pendingCount');
  });

  it('should flush pending commands on close', async () => {
    await sink.init(mockContext);

    const command: ControlCommand = {
      id: 'cmd-final',
      category: 'config',
      payload: {},
      metadata: {
        issuedBy: 'test',
        issuedAt: new Date().toISOString(),
        priority: 'urgent',
      },
    };

    await sink.enqueue(command);
    await sink.close();

    // Verify flush was called (commands cleared)
    const health = await sink.healthCheck();
    expect(health.details?.pendingCount).toBe(0);
  });
});
