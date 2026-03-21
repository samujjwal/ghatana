import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  TracingService,
  initializeTracing,
  fastifyTracingPlugin,
  traceDatabaseQuery,
  traceAIRequest,
} from './index';
import { NodeSDK } from '@opentelemetry/sdk-node';

// Mock OpenTelemetry
vi.mock('@opentelemetry/sdk-node', () => ({
  NodeSDK: vi.fn().mockImplementation(() => ({
    start: vi.fn().mockResolvedValue(undefined),
    shutdown: vi.fn().mockResolvedValue(undefined),
  })),
}));

vi.mock('@opentelemetry/auto-instrumentations-node', () => ({
  getNodeAutoInstrumentations: vi.fn().mockReturnValue([]),
}));

describe('TracingService', () => {
  let tracingService: TracingService;

  beforeEach(() => {
    tracingService = new TracingService({
      serviceName: 'test-service',
      serviceVersion: '1.0.0',
      otlpEndpoint: 'http://localhost:4318',
    });
  });

  describe('constructor', () => {
    it('should create a tracing service with default options', () => {
      const service = new TracingService({
        serviceName: 'test',
      });
      expect(service).toBeDefined();
    });

    it('should create a tracing service with all options', () => {
      expect(tracingService).toBeDefined();
    });
  });

  describe('start', () => {
    it('should start the tracing SDK', async () => {
      await tracingService.start();
      expect(NodeSDK).toHaveBeenCalled();
    });
  });

  describe('shutdown', () => {
    it('should shutdown the tracing SDK', async () => {
      await tracingService.start();
      await tracingService.shutdown();
      // Shutdown should not throw
    });
  });
});

describe('initializeTracing', () => {
  it('should initialize tracing with default config', async () => {
    const sdk = await initializeTracing({
      serviceName: 'test-service',
    });
    expect(sdk).toBeDefined();
  });
});

describe('fastifyTracingPlugin', () => {
  it('should return a Fastify plugin', () => {
    const plugin = fastifyTracingPlugin({
      serviceName: 'test-service',
    });
    expect(plugin).toBeDefined();
  });
});

describe('traceDatabaseQuery', () => {
  it('should trace a database query', async () => {
    const queryFn = vi.fn().mockResolvedValue({ id: 1, name: 'Test' });
    
    const result = await traceDatabaseQuery(
      'user.findUnique',
      { where: { id: 1 } },
      queryFn
    );
    
    expect(result).toEqual({ id: 1, name: 'Test' });
    expect(queryFn).toHaveBeenCalled();
  });

  it('should handle query errors', async () => {
    const queryFn = vi.fn().mockRejectedValue(new Error('Query failed'));
    
    await expect(
      traceDatabaseQuery('user.findMany', {}, queryFn)
    ).rejects.toThrow('Query failed');
  });
});

describe('traceAIRequest', () => {
  it('should trace an AI request', async () => {
    const requestFn = vi.fn().mockResolvedValue({
      content: 'Generated content',
      tokens: 150,
    });
    
    const result = await traceAIRequest(
      'generate-animation',
      { description: 'test' },
      requestFn
    );
    
    expect(result).toEqual({
      content: 'Generated content',
      tokens: 150,
    });
    expect(requestFn).toHaveBeenCalled();
  });
});
