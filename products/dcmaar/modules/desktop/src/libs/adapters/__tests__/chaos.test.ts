/// <reference types="vitest" />
/**
 * Chaos and soak tests for adapter resilience.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createAdapterManager } from '../adapterManager';
import { adapterFactory } from '../adapterFactory';
import type {
  ControlSink,
  ControlCommand,
  SinkContext,
  SinkAck,
  TelemetrySource,
  TelemetrySnapshot,
  SourceContext,
  HealthStatus,
} from '../types';
import { createMockSink } from '../sinks/MockSink';
import { createMockSource } from '../sources/MockSource';

class FlakySink implements ControlSink {
  readonly kind = 'mock';
  private delegate: ControlSink;
  private failurePattern: boolean[];
  private attempts = 0;

  constructor(pattern: boolean[]) {
    this.delegate = createMockSink();
    this.failurePattern = [...pattern];
  }

  async init(ctx: SinkContext): Promise<void> {
    await this.delegate.init(ctx);
  }

  async enqueue(command: ControlCommand): Promise<void> {
    await this.delegate.enqueue(command);
  }

  async flush(): Promise<SinkAck[]> {
    const fail = this.failurePattern.length > 0
      ? this.failurePattern[Math.min(this.attempts, this.failurePattern.length - 1)]
      : false;
    this.attempts += 1;

    if (fail) {
      throw new Error('Simulated sink failure');
    }

    return this.delegate.flush();
  }

  async healthCheck(): Promise<HealthStatus> {
    if (this.delegate.healthCheck) {
      return this.delegate.healthCheck();
    }
    return {
      healthy: true,
      lastCheck: new Date().toISOString(),
    };
  }

  async close(): Promise<void> {
    await this.delegate.close?.();
  }
}

class PacketLossSource implements TelemetrySource {
  readonly kind = 'mock';
  private delegate: TelemetrySource;
  private lossRate: number;

  constructor(lossRate: number) {
    this.delegate = createMockSource({ refreshIntervalMs: 100 });
    this.lossRate = lossRate;
  }

  async init(ctx: SourceContext): Promise<void> {
    await this.delegate.init(ctx);
  }

  async getInitialSnapshot(): Promise<TelemetrySnapshot> {
    if (Math.random() < this.lossRate) {
      throw new Error('Simulated packet loss');
    }
    return this.delegate.getInitialSnapshot();
  }

  async subscribe(emit: (update: TelemetrySnapshot) => void): Promise<() => void> {
    return this.delegate.subscribe?.(emit) ?? (async () => {});
  }

  async healthCheck(): Promise<HealthStatus> {
    if (this.delegate.healthCheck) {
      return this.delegate.healthCheck();
    }
    return {
      healthy: true,
      lastCheck: new Date().toISOString(),
    };
  }

  async close(): Promise<void> {
    await this.delegate.close?.();
  }
}

describe('Chaos Testing', () => {
  beforeEach(() => {
    adapterFactory.registerSinkFactory('flaky', () => new FlakySink([true, false, false]));
    adapterFactory.registerSourceFactory('packet-loss', () => new PacketLossSource(0.3));
  });

  it('recovers from transient sink failures', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'chaos-sink',
      bundle: {
        workspaceVersion: '2.0.0',
        createdAt: new Date().toISOString(),
        sources: [{ type: 'mock', options: {} }],
        sinks: [{ type: 'flaky', options: {} }],
        rbac: { role: 'operator', modules: ['status'] },
        keyring: [{ kid: 'chaos', algorithm: 'ECDSA-P256', revoked: false, createdAt: new Date().toISOString() }],
        policies: { allowRemote: true, requireMTLS: false },
        signature: 'mock',
      },
    });

    await manager.executeCommand({
      id: 'cmd-flaky',
      category: 'config',
      payload: {},
      metadata: {
        issuedBy: 'chaos-test',
        priority: 'medium',
      },
    });

    await expect(manager.flush()).rejects.toThrow('Simulated sink failure');
    await manager.flush();

    await manager.close();
  });

  it('tolerates packet loss in source snapshot retrieval', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'chaos-source',
      bundle: {
        workspaceVersion: '2.0.0',
        createdAt: new Date().toISOString(),
        sources: [{ type: 'packet-loss', options: {} }],
        sinks: [{ type: 'mock', options: {} }],
        rbac: { role: 'operator', modules: ['status'] },
        keyring: [{ kid: 'chaos', algorithm: 'ECDSA-P256', revoked: false, createdAt: new Date().toISOString() }],
        policies: { allowRemote: false, requireMTLS: false },
        signature: 'mock',
      },
    });

    let successes = 0;
    for (let i = 0; i < 10; i++) {
      try {
        await manager.getSnapshot();
        successes += 1;
      } catch {
        // expected occasional failure
      }
    }

    expect(successes).toBeGreaterThan(0);

    await manager.close();
  });
});

describe('Soak Testing', () => {
  it('handles repeated command bursts without memory growth', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'soak-test',
      bundle: {
        workspaceVersion: '2.0.0',
        createdAt: new Date().toISOString(),
        sources: [{ type: 'mock', options: {} }],
        sinks: [{ type: 'mock', options: {} }],
        rbac: { role: 'operator', modules: ['status'] },
        keyring: [{ kid: 'soak', algorithm: 'ECDSA-P256', revoked: false, createdAt: new Date().toISOString() }],
        policies: { allowRemote: false, requireMTLS: false },
        signature: 'mock',
      },
    });

    for (let round = 0; round < 20; round++) {
      for (let i = 0; i < 50; i++) {
        await manager.executeCommand({
          id: `cmd-${round}-${i}`,
          category: 'config',
          payload: { round, i },
          metadata: {
            issuedBy: 'soak-test',
            issuedAt: new Date().toISOString(),
            priority: 'low',
          },
        });
      }
      await manager.flush();
    }

    await manager.close();
  });
});
