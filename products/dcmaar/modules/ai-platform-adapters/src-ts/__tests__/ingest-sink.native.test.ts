import { describe, it, expect, vi, afterEach } from 'vitest';
import type { NativeBridge, BridgeStats } from '../native/bridge';

const defaultStats: BridgeStats = {
  batchesProcessed: 0,
  eventsProcessed: 0,
  usingRealClient: true,
  startedAt: new Date().toISOString(),
  uptimeMs: 0,
  lastError: null,
};

afterEach(() => {
  vi.restoreAllMocks();
  vi.resetModules();
});

async function setupConnector(bridge: NativeBridge | null, stats: BridgeStats = defaultStats) {
  vi.resetModules();

  const bridgeModule = await import('../native/bridge');
  vi.spyOn(bridgeModule, 'createNativeBridge').mockReturnValue(bridge);
  vi.spyOn(bridgeModule, 'getLastBridgeError').mockReturnValue(null);
  vi.spyOn(bridgeModule, 'parseBridgeStats').mockImplementation(async () => stats);

  const { IngestSinkAdapter } = await import('../adapters/sinks/IngestSinkAdapter');
  const adapter = new IngestSinkAdapter();

  const connector = (await adapter.create({
    id: 'test-ingest',
    type: 'ingest',
    metadata: {
      batchSize: 2,
      batchInterval: 0,
    },
  })) as any;

  await connector.connect();
  return connector;
}

describe('IngestSinkAdapter – native bridge integration', () => {
  it('submits batches through the native bridge when available', async () => {
    const submitBatch = vi.fn(async (payload: string) => {
      const parsed = JSON.parse(payload);
      expect(parsed).toHaveLength(2);
      return parsed.length;
    });

    const bridgeStub: NativeBridge = {
      submitBatch,
      submitEvent: vi.fn(async () => undefined),
      getStats: vi.fn(async () => JSON.stringify(defaultStats)),
      healthCheck: vi.fn(async () => true),
      getVersion: vi.fn(() => '0.1.0-test'),
    } as unknown as NativeBridge;

    const connector = await setupConnector(bridgeStub);

    await connector.send({
      id: 'event-1',
      type: 'metric.test',
      timestamp: Date.now(),
      payload: { value: 1 },
    });

    await connector.send({
      id: 'event-2',
      type: 'metric.test',
      timestamp: Date.now(),
      payload: { value: 2 },
    });

    expect(submitBatch).toHaveBeenCalledTimes(1);

    const stats = await connector.getStats();
    expect(stats.bridgeEnabled).toBe(true);
    expect(stats.pending).toBe(0);

    await connector.disconnect();
  });

  it('falls back to log mode when native submission fails', async () => {
    const submitBatch = vi.fn(async () => {
      throw new Error('bridge failure');
    });

    const bridgeStub: NativeBridge = {
      submitBatch,
      submitEvent: vi.fn(async () => undefined),
      getStats: vi.fn(async () => JSON.stringify(defaultStats)),
      healthCheck: vi.fn(async () => true),
      getVersion: vi.fn(() => '0.1.0-test'),
    } as unknown as NativeBridge;

    const connector = await setupConnector(bridgeStub);

    await connector.send({
      id: 'event-1',
      type: 'metric.test',
      timestamp: Date.now(),
      payload: { value: 1 },
    });

    await expect(
      connector.send({
        id: 'event-2',
        type: 'metric.test',
        timestamp: Date.now(),
        payload: { value: 2 },
      })
    ).rejects.toThrow('bridge failure');

    const stats = await connector.getStats();
    expect(stats.bridgeEnabled).toBe(false);
    expect(stats.pending).toBeGreaterThanOrEqual(2);

    await connector.disconnect();
  });
});
