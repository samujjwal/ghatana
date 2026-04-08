/**
 * useAIQuality Hook Tests
 */

import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAIQuality } from '../useAIQuality';

describe('useAIQuality', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('should return empty metrics initially', () => {
    const { result } = renderHook(() => useAIQuality());

    expect(result.current.metrics).toEqual([]);
    expect(result.current.summary).toBeNull();
  });

  it('should record a metric', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      result.current.recordMetric({
        requestId: 'req-1',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0.95,
        latencyMs: 1200,
        tokenCount: 150,
        success: true,
        cached: false,
        fallbackUsed: false,
      });
    });

    expect(result.current.metrics.length).toBe(1);
    expect(result.current.metrics[0].requestId).toBe('req-1');
    expect(result.current.metrics[0].timestamp).toBeGreaterThan(0);
  });

  it('should calculate quality summary', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      result.current.recordMetric({
        requestId: 'req-1',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0.95,
        latencyMs: 1000,
        tokenCount: 100,
        success: true,
        cached: false,
        fallbackUsed: false,
      });

      result.current.recordMetric({
        requestId: 'req-2',
        model: 'claude',
        provider: 'anthropic',
        confidence: 0.88,
        latencyMs: 1500,
        tokenCount: 200,
        success: true,
        cached: true,
        fallbackUsed: false,
      });
    });

    const summary = result.current.summary;
    expect(summary).not.toBeNull();
    expect(summary!.totalRequests).toBe(2);
    expect(summary!.successfulRequests).toBe(2);
    expect(summary!.cacheHitRate).toBe(0.5);
  });

  it('should calculate confidence score', () => {
    const { result } = renderHook(() => useAIQuality());

    const score = result.current.calculateConfidence('Test response', [0.9, 0.85, 0.88]);

    expect(score.overall).toBeGreaterThan(0);
    expect(score.overall).toBeLessThanOrEqual(1);
    expect(score.factors.tokenConfidence).toBeGreaterThan(0);
    expect(score.reasoning).toBeDefined();
  });

  it('should penalize short responses', () => {
    const { result } = renderHook(() => useAIQuality());

    const shortScore = result.current.calculateConfidence('Hi', [0.9]);
    const normalScore = result.current.calculateConfidence('This is a normal length response.', [0.9]);

    expect(shortScore.factors.lengthPenalty).toBe(0.8);
    expect(normalScore.factors.lengthPenalty).toBe(1.0);
  });

  it('should penalize error indicators', () => {
    const { result } = renderHook(() => useAIQuality());

    const errorScore = result.current.calculateConfidence('I am sorry, I cannot help', [0.9]);
    const normalScore = result.current.calculateConfidence('Here is the help you requested', [0.9]);

    expect(errorScore.factors.errorIndicatorPenalty).toBe(0.7);
    expect(normalScore.factors.errorIndicatorPenalty).toBe(1.0);
  });

  it('should provide default confidence without token probs', () => {
    const { result } = renderHook(() => useAIQuality());

    const score = result.current.calculateConfidence('Test response');

    expect(score.overall).toBeGreaterThan(0);
    expect(score.factors.tokenConfidence).toBe(0.8);
  });

  it('should check provider health', () => {
    const { result } = renderHook(() => useAIQuality());

    // No metrics yet - should be healthy by default
    const health = result.current.getProviderHealth('openai');
    expect(health.healthy).toBe(true);
    expect(health.score).toBe(1);
  });

  it('should calculate provider health from metrics', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      // Successful requests
      for (let i = 0; i < 5; i++) {
        result.current.recordMetric({
          requestId: `req-${i}`,
          model: 'gpt-4',
          provider: 'openai',
          confidence: 0.9,
          latencyMs: 1000,
          tokenCount: 100,
          success: true,
          cached: false,
          fallbackUsed: false,
        });
      }

      // Failed request
      result.current.recordMetric({
        requestId: 'req-fail',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0,
        latencyMs: 5000,
        tokenCount: 0,
        success: false,
        errorCode: 'TIMEOUT',
        cached: false,
        fallbackUsed: false,
      });
    });

    const health = result.current.getProviderHealth('openai');
    expect(health.score).toBeLessThan(1);
  });

  it('should reset metrics', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      result.current.recordMetric({
        requestId: 'req-1',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0.95,
        latencyMs: 1000,
        tokenCount: 100,
        success: true,
        cached: false,
        fallbackUsed: false,
      });
    });

    expect(result.current.metrics.length).toBe(1);

    act(() => {
      result.current.resetMetrics();
    });

    expect(result.current.metrics).toEqual([]);
    expect(result.current.summary).toBeNull();
  });

  it('should track error breakdown in summary', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      result.current.recordMetric({
        requestId: 'req-1',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0,
        latencyMs: 0,
        tokenCount: 0,
        success: false,
        errorCode: 'RATE_LIMIT',
        cached: false,
        fallbackUsed: false,
      });

      result.current.recordMetric({
        requestId: 'req-2',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0,
        latencyMs: 0,
        tokenCount: 0,
        success: false,
        errorCode: 'TIMEOUT',
        cached: false,
        fallbackUsed: false,
      });
    });

    const summary = result.current.summary;
    expect(summary!.errorBreakdown.RATE_LIMIT).toBe(1);
    expect(summary!.errorBreakdown.TIMEOUT).toBe(1);
  });

  it('should track provider distribution', () => {
    const { result } = renderHook(() => useAIQuality());

    act(() => {
      result.current.recordMetric({
        requestId: 'req-1',
        model: 'gpt-4',
        provider: 'openai',
        confidence: 0.95,
        latencyMs: 1000,
        tokenCount: 100,
        success: true,
        cached: false,
        fallbackUsed: false,
      });

      result.current.recordMetric({
        requestId: 'req-2',
        model: 'claude',
        provider: 'anthropic',
        confidence: 0.9,
        latencyMs: 1200,
        tokenCount: 150,
        success: true,
        cached: false,
        fallbackUsed: false,
      });
    });

    const summary = result.current.summary;
    expect(summary!.providerDistribution.openai).toBe(1);
    expect(summary!.providerDistribution.anthropic).toBe(1);
  });
});
