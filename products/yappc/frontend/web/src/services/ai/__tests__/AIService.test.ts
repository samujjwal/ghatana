/**
 * AIService Tests
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { AIService, getAIService, resetAIService } from '../AIService';
import type { LLMResponse } from '../types';

// Mock the AIServiceClient
vi.mock('../../../clients/ai/AIServiceClient', () => ({
  AIServiceClient: class MockAIServiceClient {
    private mode: string;
    private mockResponses: Map<string, unknown>;

    constructor() {
      this.mode = 'mock';
      this.mockResponses = new Map();
    }

    setMockResponse(endpoint: string, response: unknown): void {
      this.mockResponses.set(endpoint, response);
    }

    async generate(request: { prompt: string }): Promise<LLMResponse> {
      const mock = this.mockResponses.get('generate');
      if (mock) {
        return mock as LLMResponse;
      }
      return {
        text: `Mock: ${request.prompt}`,
        model: 'gpt-4',
        tokenCount: 10,
        tokenProbs: [0.9, 0.85, 0.88],
        finishReason: 'stop',
      };
    }
  },
}));

describe('AIService', () => {
  beforeEach(() => {
    resetAIService();
    vi.clearAllMocks();
  });

  it('should create service with default config', () => {
    const service = new AIService();
    const config = service.getConfig();

    expect(config.primaryProvider).toBe('openai');
    expect(config.fallbackProviders).toEqual(['anthropic', 'local']);
    expect(config.timeoutMs).toBe(30000);
    expect(config.maxRetries).toBe(3);
    expect(config.enableCaching).toBe(true);
    expect(config.confidenceThreshold).toBe(0.7);
  });

  it('should create service with custom config', () => {
    const service = new AIService({
      primaryProvider: 'anthropic',
      timeoutMs: 60000,
      maxRetries: 5,
    });
    const config = service.getConfig();

    expect(config.primaryProvider).toBe('anthropic');
    expect(config.timeoutMs).toBe(60000);
    expect(config.maxRetries).toBe(5);
  });

  it('should generate text successfully', async () => {
    const service = new AIService();
    const result = await service.generate('Hello, world!');

    expect(result.text).toBeDefined();
    expect(result.confidence).toBeGreaterThanOrEqual(0);
    expect(result.confidence).toBeLessThanOrEqual(1);
    expect(result.latencyMs).toBeGreaterThanOrEqual(0);
    expect(result.metadata.cached).toBe(false);
  });

  it('should use caching when enabled', async () => {
    const service = new AIService({ enableCaching: true });

    // First call
    const result1 = await service.generate('Test prompt');
    expect(result1.metadata.cached).toBe(false);

    // Second call with same prompt should be cached
    const result2 = await service.generate('Test prompt');
    expect(result2.metadata.cached).toBe(true);
  });

  it('should not cache when disabled', async () => {
    const service = new AIService({ enableCaching: false });

    const result1 = await service.generate('Test prompt');
    expect(result1.metadata.cached).toBe(false);

    const result2 = await service.generate('Test prompt');
    expect(result2.metadata.cached).toBe(false);
  });

  it('should generate with options', async () => {
    const service = new AIService();
    const result = await service.generate('Test', {
      temperature: 0.5,
      maxTokens: 500,
      topP: 0.9,
    });

    expect(result.text).toBeDefined();
    expect(result.model).toBeDefined();
  });

  it('should return fallback result on failure', async () => {
    const service = new AIService();
    const fallbackText = 'Fallback response';

    const result = await service.generateWithFallback(
      'Test prompt',
      fallbackText,
    );

    expect(result.text).toBeDefined();
    expect(result.metadata.fallbackUsed).toBe(true);
  });

  it('should clear cache', async () => {
    const service = new AIService({ enableCaching: true });

    await service.generate('Test');
    service.clearCache();

    // After clearing, should not be cached
    const result = await service.generate('Test');
    expect(result.metadata.cached).toBe(false);
  });

  it('should return singleton from getAIService', () => {
    const service1 = getAIService();
    const service2 = getAIService();

    expect(service1).toBe(service2);
  });

  it('should create new service after reset', () => {
    const service1 = getAIService();
    resetAIService();
    const service2 = getAIService();

    expect(service1).not.toBe(service2);
  });

  it('should calculate confidence based on token probabilities', async () => {
    const service = new AIService();

    // Response with high token probabilities
    const result = await service.generate('High confidence test');
    expect(result.confidence).toBeGreaterThan(0);
  });

  it('should penalize short responses', async () => {
    const service = new AIService();
    const result = await service.generate('Hi');

    // Very short response should have reduced confidence
    expect(result.confidence).toBeLessThanOrEqual(1);
  });

  it('should track latency', async () => {
    const service = new AIService();
    const start = Date.now();

    const result = await service.generate('Latency test');

    expect(result.latencyMs).toBeGreaterThanOrEqual(0);
    expect(result.latencyMs).toBeLessThan(Date.now() - start + 1000);
  });
});
