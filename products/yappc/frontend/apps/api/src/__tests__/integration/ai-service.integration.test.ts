/**
 * @doc.type test-suite
 * @doc.purpose Integration tests for the BFF AI service path:
 *   FallbackProvider → provider endpoint (OpenAI / Anthropic / Local) → normalised AIResponse.
 *   Covers: provider routing, response formatting, error handling,
 *   circuit breaker, cache, and rate-limiter behaviour.
 * @doc.layer application
 * @doc.pattern Integration Test
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  AICache,
  AIProvider,
  AIRequest,
  AIResponse,
  CircuitBreaker,
  CircuitBreakerConfig,
  FallbackProvider,
  RateLimiter,
  StreamingAIService,
} from '../../services/ai/resilient-ai.service';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function makeProvider(overrides: Partial<AIProvider> = {}): AIProvider {
  return {
    name: 'openai',
    endpoint: 'https://api.openai.com/v1/chat/completions',
    apiKey: 'sk-test-key',
    maxRetries: 2,
    timeout: 5000,
    rateLimit: { requestsPerMinute: 60, requestsPerHour: 1000 },
    ...overrides,
  };
}

function makeRequest(overrides: Partial<AIRequest> = {}): AIRequest {
  return {
    id: 'req-001',
    provider: 'openai',
    prompt: 'Explain async/await in one sentence.',
    timestamp: Date.now(),
    ...overrides,
  };
}

function makeCircuitBreakerConfig(overrides: Partial<CircuitBreakerConfig> = {}): CircuitBreakerConfig {
  return {
    failureThreshold: 3,
    recoveryTimeout: 10_000,
    monitoringPeriod: 60_000,
    expectedRecoveryTime: 30_000,
    ...overrides,
  };
}

/** Build a minimal OpenAI-format JSON response string */
function openAIResponseBody(content: string, model = 'gpt-4'): string {
  return JSON.stringify({
    id: 'chatcmpl-abc',
    model,
    choices: [{ message: { role: 'assistant', content }, finish_reason: 'stop' }],
    usage: { prompt_tokens: 10, completion_tokens: 20, total_tokens: 30 },
  });
}

/** Build a minimal Anthropic-format JSON response string */
function anthropicResponseBody(text: string): string {
  return JSON.stringify({
    id: 'msg-abc',
    model: 'claude-3-opus-20240229',
    content: [{ type: 'text', text }],
    stop_reason: 'end_turn',
    usage: { input_tokens: 8, output_tokens: 15 },
  });
}

// ---------------------------------------------------------------------------
// FallbackProvider — provider routing tests
// ---------------------------------------------------------------------------

describe('FallbackProvider — provider routing', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('routes an OpenAI request and returns a normalised AIResponse', async () => {
    const responseContent = 'Async/await lets you write asynchronous code that looks synchronous.';
    fetchMock.mockResolvedValueOnce(
      new Response(openAIResponseBody(responseContent), { status: 200 })
    );

    const provider = makeProvider({ name: 'openai' });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    const response = await fallback.executeRequest(makeRequest({ provider: 'openai' }));

    expect(response.provider).toBe('openai');
    expect(response.content).toBe(responseContent);
    expect(response.model).toBe('gpt-4');
    expect(response.usage?.totalTokens).toBe(30);
    expect(response.fromCache).toBe(false);
    expect(response.fromFallback).toBe(false);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('routes an Anthropic request and normalises the content array response', async () => {
    const responseText = 'Async/await is syntactic sugar over Promises.';
    fetchMock.mockResolvedValueOnce(
      new Response(anthropicResponseBody(responseText), { status: 200 })
    );

    const provider = makeProvider({
      name: 'anthropic',
      endpoint: 'https://api.anthropic.com/v1/messages',
    });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    const response = await fallback.executeRequest(makeRequest({ provider: 'anthropic' }));

    expect(response.provider).toBe('anthropic');
    expect(response.content).toBe(responseText);
    expect(response.usage?.promptTokens).toBe(8);
    expect(response.usage?.completionTokens).toBe(15);
    expect(response.fromFallback).toBe(false);
  });

  it('routes a local (Java-backend) request using OpenAI-compatible format', async () => {
    const localContent = 'Local LLM response: async/await simplifies promise chaining.';
    fetchMock.mockResolvedValueOnce(
      new Response(openAIResponseBody(localContent, 'local-llm'), { status: 200 })
    );

    const provider = makeProvider({
      name: 'local',
      endpoint: 'http://localhost:8080/api/v1/chat/completions',
      apiKey: 'local-no-key',
    });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    const response = await fallback.executeRequest(makeRequest({ provider: 'local' }));

    expect(response.provider).toBe('local');
    expect(response.content).toBe(localContent);
    expect(response.model).toBe('local-llm');
  });

  it('includes the Authorization header with Bearer token on every request', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(openAIResponseBody('ok'), { status: 200 })
    );

    const provider = makeProvider({ apiKey: 'sk-secret-456' });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    await fallback.executeRequest(makeRequest());

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)['Authorization']).toBe('Bearer sk-secret-456');
  });

  it('adds the anthropic-version header for Anthropic requests', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(anthropicResponseBody('ok'), { status: 200 })
    );

    const provider = makeProvider({ name: 'anthropic' });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    await fallback.executeRequest(makeRequest({ provider: 'anthropic' }));

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect((init.headers as Record<string, string>)['anthropic-version']).toBe('2023-06-01');
  });
});

// ---------------------------------------------------------------------------
// FallbackProvider — error handling
// ---------------------------------------------------------------------------

describe('FallbackProvider — error handling', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('throws when provider returns a non-OK HTTP status', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('{"error":"Unauthorized"}', { status: 401 })
    );

    const provider = makeProvider();
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());

    await expect(fallback.executeRequest(makeRequest())).rejects.toThrow(/401/);
  });

  it('returns safe fallback response when all providers fail (executeRequestWithFallback)', async () => {
    fetchMock.mockRejectedValue(new Error('Network error'));

    const provider = makeProvider();
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());
    const response = await fallback.executeRequestWithFallback(makeRequest());

    expect(response.fromFallback).toBe(true);
    expect(response.provider).toBe('fallback');
    expect(typeof response.content).toBe('string');
    expect(response.content.length).toBeGreaterThan(0);
  });

  it('falls back to secondary provider when primary fails (executeRequestWithFallback)', async () => {
    const anthropicContent = 'Fallback: async/await wraps a Promise.';
    fetchMock
      .mockRejectedValueOnce(new Error('OpenAI down'))
      .mockResolvedValueOnce(new Response(anthropicResponseBody(anthropicContent), { status: 200 }));

    const openai = makeProvider({ name: 'openai' });
    const anthropic = makeProvider({
      name: 'anthropic',
      endpoint: 'https://api.anthropic.com/v1/messages',
    });
    const fallback = new FallbackProvider([openai, anthropic], makeCircuitBreakerConfig());
    const response = await fallback.executeRequestWithFallback(makeRequest({ provider: 'openai' }));

    expect(response.fromFallback).toBe(true);
    expect(response.content).toBe(anthropicContent);
  });

  it('throws for unrecognised provider name in executeRequest', async () => {
    const provider = makeProvider({ name: 'openai' });
    const fallback = new FallbackProvider([provider], makeCircuitBreakerConfig());

    // Requesting 'anthropic' but only 'openai' is registered
    await expect(
      fallback.executeRequest(makeRequest({ provider: 'anthropic' }))
    ).rejects.toThrow();
  });
});

// ---------------------------------------------------------------------------
// CircuitBreaker
// ---------------------------------------------------------------------------

describe('CircuitBreaker — state machine', () => {
  it('starts in CLOSED state', () => {
    const breaker = new CircuitBreaker(makeProvider(), makeCircuitBreakerConfig({ failureThreshold: 2 }));
    expect(breaker.getState().state).toBe('CLOSED');
  });

  it('transitions to OPEN after failureThreshold consecutive failures', async () => {
    const breaker = new CircuitBreaker(makeProvider(), makeCircuitBreakerConfig({ failureThreshold: 2 }));

    const failOp = () => Promise.reject(new Error('fail'));
    await breaker.execute(failOp).catch(() => undefined);
    await breaker.execute(failOp).catch(() => undefined);

    expect(breaker.getState().state).toBe('OPEN');
  });

  it('rejects immediately when OPEN without calling the operation', async () => {
    const breaker = new CircuitBreaker(
      makeProvider(),
      makeCircuitBreakerConfig({ failureThreshold: 1, recoveryTimeout: 60_000 })
    );

    await breaker.execute(() => Promise.reject(new Error('fail'))).catch(() => undefined);
    expect(breaker.getState().state).toBe('OPEN');

    const spy = vi.fn(() => Promise.resolve('ok'));
    await expect(breaker.execute(spy)).rejects.toThrow(/OPEN/);
    expect(spy).not.toHaveBeenCalled();
  });

  it('resets to CLOSED state when reset() is called', async () => {
    const breaker = new CircuitBreaker(makeProvider(), makeCircuitBreakerConfig({ failureThreshold: 1 }));
    await breaker.execute(() => Promise.reject(new Error('fail'))).catch(() => undefined);
    expect(breaker.getState().state).toBe('OPEN');

    breaker.reset();
    expect(breaker.getState().state).toBe('CLOSED');
    expect(breaker.getState().failureCount).toBe(0);
  });
});

// ---------------------------------------------------------------------------
// AICache
// ---------------------------------------------------------------------------

describe('AICache', () => {
  const request = makeRequest();
  const baseResponse: AIResponse = {
    id: 'resp-001',
    requestId: request.id,
    provider: 'openai',
    content: 'Cached content',
    model: 'gpt-4',
    timestamp: Date.now(),
    latency: 100,
    fromCache: false,
    fromFallback: false,
  };

  it('returns null for a cache miss', () => {
    const cache = new AICache();
    expect(cache.get(request)).toBeNull();
  });

  it('returns a hit with fromCache=true after set()', () => {
    const cache = new AICache();
    cache.set(request, baseResponse);
    const hit = cache.get(request);

    expect(hit).not.toBeNull();
    expect(hit?.fromCache).toBe(true);
    expect(hit?.content).toBe(baseResponse.content);
  });

  it('returns null after the TTL expires', async () => {
    const cache = new AICache(100, 1); // 1 ms TTL
    cache.set(request, baseResponse);

    await new Promise(resolve => setTimeout(resolve, 5));
    expect(cache.get(request)).toBeNull();
  });

  it('evicts the oldest entry when the cache is full', () => {
    const cache = new AICache(2, 60_000);
    const req1 = makeRequest({ id: 'r1', prompt: 'one' });
    const req2 = makeRequest({ id: 'r2', prompt: 'two' });
    const req3 = makeRequest({ id: 'r3', prompt: 'three' });
    const resp = (id: string): AIResponse => ({ ...baseResponse, id, requestId: id });

    cache.set(req1, resp('r1'));
    cache.set(req2, resp('r2'));
    cache.set(req3, resp('r3')); // evicts req1

    expect(cache.get(req1)).toBeNull();
    expect(cache.get(req2)).not.toBeNull();
    expect(cache.get(req3)).not.toBeNull();
  });

  it('size() returns the current number of entries', () => {
    const cache = new AICache();
    const r1 = makeRequest({ id: 'r1', prompt: 'p1' });
    const r2 = makeRequest({ id: 'r2', prompt: 'p2' });
    cache.set(r1, { ...baseResponse, requestId: 'r1' });
    cache.set(r2, { ...baseResponse, requestId: 'r2' });
    expect(cache.size()).toBe(2);
  });

  it('clear() empties the cache', () => {
    const cache = new AICache();
    cache.set(request, baseResponse);
    cache.clear();
    expect(cache.size()).toBe(0);
    expect(cache.get(request)).toBeNull();
  });
});

// ---------------------------------------------------------------------------
// RateLimiter
// ---------------------------------------------------------------------------

describe('RateLimiter', () => {
  it('allows all requests when no limit is set for the provider', async () => {
    const limiter = new RateLimiter();
    const allowed = await limiter.checkLimit('openai');
    expect(allowed).toBe(true);
  });

  it('allows requests within the per-minute limit', async () => {
    const limiter = new RateLimiter();
    limiter.setLimit('openai', 3, 100);

    for (let i = 0; i < 3; i++) {
      expect(await limiter.checkLimit('openai')).toBe(true);
    }
  });

  it('blocks requests that exceed the per-minute limit', async () => {
    const limiter = new RateLimiter();
    limiter.setLimit('openai', 2, 100);

    await limiter.checkLimit('openai');
    await limiter.checkLimit('openai');
    const blocked = await limiter.checkLimit('openai');

    expect(blocked).toBe(false);
  });

  it('returns Infinity remaining when no limit is set', () => {
    const limiter = new RateLimiter();
    const remaining = limiter.getRemainingRequests('openai');
    expect(remaining.perMinute).toBe(Infinity);
    expect(remaining.perHour).toBe(Infinity);
  });

  it('decrements remaining count as requests are made', async () => {
    const limiter = new RateLimiter();
    limiter.setLimit('anthropic', 5, 50);

    await limiter.checkLimit('anthropic');
    await limiter.checkLimit('anthropic');

    const remaining = limiter.getRemainingRequests('anthropic');
    expect(remaining.perMinute).toBe(3);
  });
});

// ---------------------------------------------------------------------------
// StreamingAIService — cache integration
// ---------------------------------------------------------------------------

describe('StreamingAIService — cache integration', () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('serves a cache hit without hitting the provider endpoint', async () => {
    const content = 'Await pauses execution until the Promise settles.';
    fetchMock.mockResolvedValue(
      new Response(openAIResponseBody(content), { status: 200 })
    );

    const provider = makeProvider();
    const service = new StreamingAIService([provider], makeCircuitBreakerConfig());
    const request = makeRequest();

    // First call primes the cache
    const first = await service.executeRequest(request);
    expect(fetchMock).toHaveBeenCalledOnce();
    expect(first.fromCache).toBe(false);

    // Second call hits the cache
    const second = await service.executeRequest(request);
    expect(fetchMock).toHaveBeenCalledOnce(); // still only once
    expect(second.fromCache).toBe(true);
  });

  it('does not cache responses that came from the safe fallback (fromFallback=true)', async () => {
    fetchMock.mockRejectedValue(new Error('provider down'));

    const provider = makeProvider();
    const service = new StreamingAIService([provider], makeCircuitBreakerConfig());
    const request = makeRequest();

    // First call → fallback response (not cached)
    const first = await service.executeRequest(request);
    expect(first.fromFallback).toBe(true);

    // Second call → provider still called (no cache hit from fallback)
    await service.executeRequest(request);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('response latency is a non-negative number', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(openAIResponseBody('Hello'), { status: 200 })
    );

    const provider = makeProvider();
    const service = new StreamingAIService([provider], makeCircuitBreakerConfig());
    const response = await service.executeRequest(makeRequest());

    expect(response.latency).toBeGreaterThanOrEqual(0);
  });
});
