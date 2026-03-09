/**
 * Unit tests for LocalFileSource adapter.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { LocalFileSource } from '../sources/LocalFileSource';
import type { SourceContext, TelemetrySnapshot } from '../types';
import { createLogger } from '../logger';
import { createTracer } from '../tracer';

describe('LocalFileSource', () => {
  let mockContext: SourceContext;
  let source: LocalFileSource;

  beforeEach(() => {
    vi.useFakeTimers();
    mockContext = {
      workspaceId: 'test-workspace',
      keyring: {
        verify: vi.fn().mockResolvedValue(true),
        sign: vi.fn(),
        getPublicKey: vi.fn(),
        listKeys: vi.fn(),
      },
      logger: createLogger({ level: 'debug' }),
      tracer: createTracer({ serviceName: 'test', enabled: false }),
    };

    source = new LocalFileSource({
      snapshotPath: '/test/snapshot.json',
      pollMs: 1000,
      verifySignature: false,
    });
  });

  afterEach(async () => {
    await source.close();
    vi.useRealTimers();
  });

  it('should initialize successfully', async () => {
    await expect(source.init(mockContext)).resolves.toBeUndefined();
  });

  it('should have correct kind', () => {
    expect(source.kind).toBe('file');
  });

  it('should handle health check', async () => {
    await source.init(mockContext);
    const health = await source.healthCheck();

    expect(health).toHaveProperty('healthy');
    expect(health).toHaveProperty('lastCheck');
  });

  it('should clean up on close', async () => {
    await source.init(mockContext);
    const unsubscribe = await source.subscribe(() => {});

    await source.close();
    unsubscribe();

    // Verify no timers are running
    expect(vi.getTimerCount()).toBe(0);
  });

  it('should create spans for operations', async () => {
    const tracerWithSpan = createTracer({ serviceName: 'test', enabled: true });
    const spanEndSpy = vi.fn();

    vi.spyOn(tracerWithSpan, 'startSpan').mockReturnValue({
      setAttribute: vi.fn(),
      setStatus: vi.fn(),
      end: spanEndSpy,
    });

    mockContext.tracer = tracerWithSpan;
    await source.init(mockContext);

    // Trigger operation that creates span
    try {
      await source.getInitialSnapshot();
    } catch {
      // Expected to fail without real file system
    }

    expect(spanEndSpy).toHaveBeenCalled();
  });
});
