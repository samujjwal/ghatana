/**
 * Performance benchmarks for adapter system.
 * Measures throughput, latency, and resource usage.
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { createAdapterManager } from '../adapterManager';
import { createMockSource } from '../sources/MockSource';
import { createMockSink } from '../sinks/MockSink';
import { createQueue } from '../queue';
import { createCryptoService } from '../crypto';
import type { WorkspaceBundle, ControlCommand } from '../types';

describe('Performance Benchmarks', () => {
  let mockBundle: WorkspaceBundle;

  beforeEach(() => {
    mockBundle = {
      workspaceVersion: '2.0.0',
      createdAt: new Date().toISOString(),
      sources: [{ type: 'mock', options: {} }],
      sinks: [{ type: 'mock', options: {} }],
      rbac: { role: 'operator', modules: ['status'] },
      keyring: [{
        kid: 'test',
        algorithm: 'ECDSA-P256',
        revoked: false,
        createdAt: new Date().toISOString(),
      }],
      policies: { allowRemote: false, requireMTLS: false },
      signature: 'mock',
    };
  });

  it('should measure snapshot fetch latency', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'perf-test',
      bundle: mockBundle,
    });

    const iterations = 100;
    const latencies: number[] = [];

    for (let i = 0; i < iterations; i++) {
      const start = performance.now();
      await manager.getSnapshot();
      const end = performance.now();
      latencies.push(end - start);
    }

    const avg = latencies.reduce((a, b) => a + b, 0) / latencies.length;
    const p95 = latencies.sort((a, b) => a - b)[Math.floor(latencies.length * 0.95)];

    console.log(`Snapshot fetch - Avg: ${avg.toFixed(2)}ms, P95: ${p95.toFixed(2)}ms`);

    expect(avg).toBeLessThan(50); // Target: <50ms average
    expect(p95).toBeLessThan(100); // Target: <100ms P95

    await manager.close();
  });

  it('should measure command enqueue throughput', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'perf-test',
      bundle: mockBundle,
    });

    const commandCount = 1000;
    const commands: ControlCommand[] = Array.from({ length: commandCount }, (_, i) => ({
      id: `cmd-${i}`,
      category: 'config',
      payload: { index: i },
      metadata: {
        issuedBy: 'perf-test',
        issuedAt: new Date().toISOString(),
        priority: 'medium',
      },
    }));

    const start = performance.now();

    for (const cmd of commands) {
      await manager.executeCommand(cmd);
    }

    const end = performance.now();
    const duration = end - start;
    const throughput = (commandCount / duration) * 1000; // commands per second

    console.log(`Command enqueue - ${throughput.toFixed(0)} commands/sec`);

    expect(throughput).toBeGreaterThan(1000); // Target: >1000 commands/sec

    await manager.close();
  });

  it('should measure flush performance', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'perf-test',
      bundle: mockBundle,
    });

    // Enqueue 100 commands
    for (let i = 0; i < 100; i++) {
      await manager.executeCommand({
        id: `cmd-${i}`,
        category: 'config',
        payload: {},
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'low',
        },
      });
    }

    const start = performance.now();
    await manager.flush();
    const end = performance.now();

    console.log(`Flush 100 commands: ${(end - start).toFixed(2)}ms`);

    expect(end - start).toBeLessThan(500); // Target: <500ms for 100 commands

    await manager.close();
  });

  it('should measure queue operations', async () => {
    const queue = await createQueue({
      dbName: 'perf-queue',
      storeName: 'commands',
      maxSizeMB: 10,
    });

    const iterations = 1000;

    // Measure enqueue
    const enqueueStart = performance.now();
    for (let i = 0; i < iterations; i++) {
      await queue.enqueue({
        id: `cmd-${i}`,
        category: 'config',
        payload: {},
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'medium',
        },
      });
    }
    const enqueueEnd = performance.now();

    console.log(`Queue enqueue: ${((enqueueEnd - enqueueStart) / iterations).toFixed(2)}ms per item`);

    // Measure dequeue
    const dequeueStart = performance.now();
    for (let i = 0; i < iterations; i++) {
      await queue.dequeue();
    }
    const dequeueEnd = performance.now();

    console.log(`Queue dequeue: ${((dequeueEnd - dequeueStart) / iterations).toFixed(2)}ms per item`);

    expect((enqueueEnd - enqueueStart) / iterations).toBeLessThan(5); // <5ms per enqueue
    expect((dequeueEnd - dequeueStart) / iterations).toBeLessThan(5); // <5ms per dequeue
  });

  it('should measure encryption overhead', async () => {
    const crypto = createCryptoService();
    const key = await crypto.generateKey();

    const testData = {
      id: 'test-command',
      payload: { data: 'x'.repeat(1000) }, // 1KB payload
    };

    const iterations = 100;

    // Measure encryption
    const encryptStart = performance.now();
    const encrypted = [];
    for (let i = 0; i < iterations; i++) {
      encrypted.push(await crypto.encrypt(testData, key));
    }
    const encryptEnd = performance.now();

    // Measure decryption
    const decryptStart = performance.now();
    for (const enc of encrypted) {
      await crypto.decrypt(enc, key);
    }
    const decryptEnd = performance.now();

    const encryptAvg = (encryptEnd - encryptStart) / iterations;
    const decryptAvg = (decryptEnd - decryptStart) / iterations;

    console.log(`Encryption: ${encryptAvg.toFixed(2)}ms per 1KB`);
    console.log(`Decryption: ${decryptAvg.toFixed(2)}ms per 1KB`);

    expect(encryptAvg).toBeLessThan(10); // <10ms per 1KB
    expect(decryptAvg).toBeLessThan(10); // <10ms per 1KB
  });

  it('should measure memory usage', async () => {
    const manager = await createAdapterManager({
      workspaceId: 'mem-test',
      bundle: mockBundle,
    });

    const initialMemory = (performance as any).memory?.usedJSHeapSize ?? 0;

    // Create load
    for (let i = 0; i < 1000; i++) {
      await manager.executeCommand({
        id: `cmd-${i}`,
        category: 'config',
        payload: { data: 'x'.repeat(100) },
        metadata: {
          issuedBy: 'test',
          issuedAt: new Date().toISOString(),
          priority: 'medium',
        },
      });
    }

    await manager.flush();

    const finalMemory = (performance as any).memory?.usedJSHeapSize ?? 0;
    const memoryIncrease = (finalMemory - initialMemory) / (1024 * 1024); // MB

    console.log(`Memory increase: ${memoryIncrease.toFixed(2)}MB for 1000 commands`);

    // Should not leak significant memory
    expect(memoryIncrease).toBeLessThan(50); // <50MB increase

    await manager.close();
  });
});
