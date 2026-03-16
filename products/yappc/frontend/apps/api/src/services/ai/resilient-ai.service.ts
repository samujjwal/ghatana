/**
 * AI Service Resilience Implementation
 * 
 * Provides circuit breaker pattern, fallback mechanisms, streaming responses,
 * and comprehensive error handling for AI services.
 * 
 * @doc.type service
 * @doc.purpose AI service resilience and reliability
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { EventEmitter } from 'events';

// ============================================================================
// Types
// ============================================================================

export interface AIProvider {
  name: string;
  endpoint: string;
  apiKey: string;
  maxRetries: number;
  timeout: number;
  rateLimit: {
    requestsPerMinute: number;
    requestsPerHour: number;
  };
}

export interface CircuitBreakerConfig {
  failureThreshold: number;
  recoveryTimeout: number;
  monitoringPeriod: number;
  expectedRecoveryTime: number;
}

export interface CircuitBreakerState {
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  failureCount: number;
  lastFailureTime: number;
  nextAttemptTime: number;
  successCount: number;
}

export interface AIRequest {
  id: string;
  provider: string;
  prompt: string;
  context?: Record<string, unknown>;
  options?: {
    temperature?: number;
    maxTokens?: number;
    model?: string;
    stream?: boolean;
  };
  timestamp: number;
  timeout?: number;
}

export interface AIResponse {
  id: string;
  requestId: string;
  provider: string;
  content: string;
  usage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
  model: string;
  finishReason?: string;
  timestamp: number;
  latency: number;
  fromCache: boolean;
  fromFallback: boolean;
}

export interface AIStreamChunk {
  id: string;
  requestId: string;
  content: string;
  delta: string;
  isComplete: boolean;
  timestamp: number;
}

export interface ResilienceMetrics {
  totalRequests: number;
  successfulRequests: number;
  failedRequests: number;
  circuitBreakerTrips: number;
  fallbackActivations: number;
  cacheHits: number;
  averageLatency: number;
  providerHealth: Record<string, number>;
}

// ============================================================================
// Circuit Breaker Implementation
// ============================================================================

export class CircuitBreaker extends EventEmitter {
  private state: CircuitBreakerState;
  private config: CircuitBreakerConfig;
  private provider: AIProvider;

  constructor(provider: AIProvider, config: CircuitBreakerConfig) {
    super();
    this.provider = provider;
    this.config = config;
    this.state = {
      state: 'CLOSED',
      failureCount: 0,
      lastFailureTime: 0,
      nextAttemptTime: 0,
      successCount: 0,
    };
  }

  async execute<T>(operation: () => Promise<T>): Promise<T> {
    const now = Date.now();

    // Check if circuit is open and should remain open
    if (this.state.state === 'OPEN' && now < this.state.nextAttemptTime) {
      throw new Error(`Circuit breaker is OPEN for ${this.provider.name}. Next attempt at ${new Date(this.state.nextAttemptTime)}`);
    }

    // Transition from OPEN to HALF_OPEN
    if (this.state.state === 'OPEN' && now >= this.state.nextAttemptTime) {
      this.state.state = 'HALF_OPEN';
      this.state.successCount = 0;
      this.emit('state_change', 'HALF_OPEN', this.provider.name);
    }

    try {
      const result = await operation();
      this.onSuccess();
      return result;
    } catch (error) {
      this.onFailure();
      throw error;
    }
  }

  private onSuccess(): void {
    this.state.failureCount = 0;
    
    if (this.state.state === 'HALF_OPEN') {
      this.state.successCount++;
      if (this.state.successCount >= 3) { // Require 3 successes to close
        this.state.state = 'CLOSED';
        this.emit('state_change', 'CLOSED', this.provider.name);
      }
    }
  }

  private onFailure(): void {
    const now = Date.now();
    this.state.failureCount++;
    this.state.lastFailureTime = now;

    if (this.state.state === 'HALF_OPEN') {
      // Immediate trip back to OPEN
      this.state.state = 'OPEN';
      this.state.nextAttemptTime = now + this.config.recoveryTimeout;
      this.emit('state_change', 'OPEN', this.provider.name);
      this.emit('circuit_trip', this.provider.name, this.state.failureCount);
    } else if (this.state.failureCount >= this.config.failureThreshold) {
      // Trip the circuit
      this.state.state = 'OPEN';
      this.state.nextAttemptTime = now + this.config.recoveryTimeout;
      this.emit('state_change', 'OPEN', this.provider.name);
      this.emit('circuit_trip', this.provider.name, this.state.failureCount);
    }
  }

  getState(): CircuitBreakerState {
    return { ...this.state };
  }

  reset(): void {
    this.state = {
      state: 'CLOSED',
      failureCount: 0,
      lastFailureTime: 0,
      nextAttemptTime: 0,
      successCount: 0,
    };
    this.emit('state_change', 'CLOSED', this.provider.name);
  }
}

// ============================================================================
// AI Cache Implementation
// ============================================================================

export class AICache {
  private cache: Map<string, { response: AIResponse; expiry: number }>;
  private maxSize: number;
  private ttl: number;

  constructor(maxSize: number = 1000, ttl: number = 300000) { // 5 minutes default TTL
    this.cache = new Map();
    this.maxSize = maxSize;
    this.ttl = ttl;
  }

  private generateKey(request: AIRequest): string {
    const keyData = {
      provider: request.provider,
      prompt: request.prompt,
      options: request.options,
    };
    return Buffer.from(JSON.stringify(keyData)).toString('base64');
  }

  get(request: AIRequest): AIResponse | null {
    const key = this.generateKey(request);
    const cached = this.cache.get(key);
    
    if (!cached) {
      return null;
    }

    if (Date.now() > cached.expiry) {
      this.cache.delete(key);
      return null;
    }

    // Move to end (LRU)
    this.cache.delete(key);
    this.cache.set(key, cached);
    
    return { ...cached.response, fromCache: true };
  }

  set(request: AIRequest, response: AIResponse): void {
    const key = this.generateKey(request);
    
    // Remove oldest if at capacity
    if (this.cache.size >= this.maxSize) {
      const firstKey = this.cache.keys().next().value;
      this.cache.delete(firstKey);
    }

    this.cache.set(key, {
      response: { ...response, fromCache: true },
      expiry: Date.now() + this.ttl,
    });
  }

  clear(): void {
    this.cache.clear();
  }

  size(): number {
    return this.cache.size;
  }

  cleanup(): void {
    const now = Date.now();
    for (const [key, cached] of this.cache.entries()) {
      if (now > cached.expiry) {
        this.cache.delete(key);
      }
    }
  }
}

// ============================================================================
// Rate Limiter Implementation
// ============================================================================

export class RateLimiter {
  private requests: Map<string, number[]>;
  private limits: Map<string, { perMinute: number; perHour: number }>;

  constructor() {
    this.requests = new Map();
    this.limits = new Map();
  }

  setLimit(provider: string, perMinute: number, perHour: number): void {
    this.limits.set(provider, { perMinute, perHour });
  }

  async checkLimit(provider: string): Promise<boolean> {
    const limits = this.limits.get(provider);
    if (!limits) {
      return true; // No limit set
    }

    const now = Date.now();
    const providerRequests = this.requests.get(provider) || [];

    // Clean old requests
    const oneHourAgo = now - 3600000;
    const oneMinuteAgo = now - 60000;
    const recentRequests = providerRequests.filter(timestamp => timestamp > oneHourAgo);

    // Check per-minute limit
    const minuteRequests = recentRequests.filter(timestamp => timestamp > oneMinuteAgo);
    if (minuteRequests.length >= limits.perMinute) {
      return false;
    }

    // Check per-hour limit
    if (recentRequests.length >= limits.perHour) {
      return false;
    }

    // Add current request
    recentRequests.push(now);
    this.requests.set(provider, recentRequests);

    return true;
  }

  getRemainingRequests(provider: string): { perMinute: number; perHour: number } {
    const limits = this.limits.get(provider);
    if (!limits) {
      return { perMinute: Infinity, perHour: Infinity };
    }

    const now = Date.now();
    const providerRequests = this.requests.get(provider) || [];

    const oneHourAgo = now - 3600000;
    const oneMinuteAgo = now - 60000;
    const recentRequests = providerRequests.filter(timestamp => timestamp > oneHourAgo);

    const minuteRequests = recentRequests.filter(timestamp => timestamp > oneMinuteAgo);

    return {
      perMinute: Math.max(0, limits.perMinute - minuteRequests.length),
      perHour: Math.max(0, limits.perHour - recentRequests.length),
    };
  }
}

// ============================================================================
// Fallback Provider Implementation
// ============================================================================

export class FallbackProvider {
  private providers: AIProvider[];
  private circuitBreakers: Map<string, CircuitBreaker>;
  private rateLimiter: RateLimiter;

  constructor(providers: AIProvider[], circuitBreakerConfig: CircuitBreakerConfig) {
    this.providers = providers.sort((a, b) => {
      // Sort by priority (lower index = higher priority)
      const priorityOrder = ['openai', 'anthropic', 'local'];
      const aPriority = priorityOrder.indexOf(a.name.toLowerCase());
      const bPriority = priorityOrder.indexOf(b.name.toLowerCase());
      return aPriority - bPriority;
    });

    this.circuitBreakers = new Map();
    this.rateLimiter = new RateLimiter();

    // Initialize circuit breakers and rate limits
    for (const provider of this.providers) {
      this.circuitBreakers.set(provider.name, new CircuitBreaker(provider, circuitBreakerConfig));
      this.rateLimiter.setLimit(provider.name, provider.rateLimit.requestsPerMinute, provider.rateLimit.requestsPerHour);
    }
  }

  async executeRequest(request: AIRequest): Promise<AIResponse> {
    let lastError: Error | null = null;

    for (const provider of this.providers) {
      if (provider.name !== request.provider) {
        continue; // Skip if not the requested provider (unless we implement fallback)
      }

      const circuitBreaker = this.circuitBreakers.get(provider.name)!;
      
      // Check rate limit
      const canProceed = await this.rateLimiter.checkLimit(provider.name);
      if (!canProceed) {
        continue; // Try next provider
      }

      try {
        const response = await circuitBreaker.execute(async () => {
          return this.makeRequest(provider, request);
        });

        return response;
      } catch (error) {
        lastError = error as Error;
        console.warn(`Provider ${provider.name} failed:`, error);
        continue; // Try next provider
      }
    }

    // All providers failed
    throw lastError || new Error('All AI providers are unavailable');
  }

  async executeRequestWithFallback(request: AIRequest): Promise<AIResponse> {
    let lastError: Error | null = null;

    // Try primary provider first
    try {
      return await this.executeRequest(request);
    } catch (error) {
      lastError = error as Error;
    }

    // Try fallback providers
    for (const provider of this.providers) {
      if (provider.name === request.provider) {
        continue; // Already tried this one
      }

      const circuitBreaker = this.circuitBreakers.get(provider.name)!;
      
      // Check rate limit
      const canProceed = await this.rateLimiter.checkLimit(provider.name);
      if (!canProceed) {
        continue;
      }

      try {
        const response = await circuitBreaker.execute(async () => {
          const fallbackRequest = { ...request, provider: provider.name };
          const result = await this.makeRequest(provider, fallbackRequest);
          return { ...result, fromFallback: true };
        });

        return response;
      } catch (error) {
        lastError = error as Error;
        console.warn(`Fallback provider ${provider.name} failed:`, error);
        continue;
      }
    }

    // All providers failed, return a safe fallback response
    return this.createSafeFallbackResponse(request, lastError);
  }

  private async makeRequest(provider: AIProvider, request: AIRequest): Promise<AIResponse> {
    const startTime = Date.now();

    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), provider.timeout);

    try {
      // Build request body in OpenAI chat-completion format.
      // Anthropic and other providers are normalised to this schema at the gateway layer.
      const model = request.options?.model
          ?? (provider.name === 'anthropic' ? 'claude-3-opus-20240229' : 'gpt-4');

      const body = {
        model,
        messages: [{ role: 'user' as const, content: request.prompt }],
        temperature: request.options?.temperature ?? 0.7,
        max_tokens: request.options?.maxTokens ?? 1024,
      };

      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${provider.apiKey}`,
      };
      if (provider.name === 'anthropic') {
        headers['anthropic-version'] = '2023-06-01';
      }

      const response = await fetch(provider.endpoint, {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      if (!response.ok) {
        const errorBody = await response.text().catch(() => '');
        throw new Error(
            `Provider ${provider.name} returned HTTP ${response.status}: ${errorBody}`
        );
      }

      const data = await response.json() as Record<string, unknown>;
      const latency = Date.now() - startTime;

      // Normalise OpenAI and Anthropic response shapes
      const choices = data['choices'] as Array<Record<string, unknown>> | undefined;
      const anthropicContent = data['content'] as Array<Record<string, unknown>> | undefined;
      const content: string =
          (choices?.[0]?.['message'] as Record<string, string> | undefined)?.['content']
          ?? (anthropicContent?.[0]?.['text'] as string | undefined)
          ?? '';

      const usage = data['usage'] as Record<string, number> | undefined;

      return {
        id: crypto.randomUUID(),
        requestId: request.id,
        provider: provider.name,
        content,
        usage: {
          promptTokens:      usage?.['prompt_tokens'] ?? usage?.['input_tokens']  ?? 0,
          completionTokens:  usage?.['completion_tokens'] ?? usage?.['output_tokens'] ?? 0,
          totalTokens:       usage?.['total_tokens'] ?? 0,
        },
        model: (data['model'] as string | undefined) ?? model,
        finishReason:
            (choices?.[0]?.['finish_reason'] as string | undefined)
            ?? (data['stop_reason'] as string | undefined),
        timestamp: Date.now(),
        latency,
        fromCache: false,
        fromFallback: false,
      };
    } finally {
      clearTimeout(timeoutId);
    }
  }

  private createSafeFallbackResponse(request: AIRequest, error?: Error | null): AIResponse {
    return {
      id: crypto.randomUUID(),
      requestId: request.id,
      provider: 'fallback',
      content: 'I apologize, but I\'m currently experiencing technical difficulties. Please try again in a few moments.',
      model: 'fallback',
      timestamp: Date.now(),
      latency: 0,
      fromCache: false,
      fromFallback: true,
    };
  }

  getProviderHealth(): Record<string, CircuitBreakerState> {
    const health: Record<string, CircuitBreakerState> = {};
    for (const [name, breaker] of this.circuitBreakers.entries()) {
      health[name] = breaker.getState();
    }
    return health;
  }

  getRateLimitStatus(): Record<string, { perMinute: number; perHour: number }> {
    const status: Record<string, { perMinute: number; perHour: number }> = {};
    for (const provider of this.providers) {
      status[provider.name] = this.rateLimiter.getRemainingRequests(provider.name);
    }
    return status;
  }
}

// ============================================================================
// Streaming AI Service
// ============================================================================

export class StreamingAIService extends EventEmitter {
  private fallbackProvider: FallbackProvider;
  private cache: AICache;
  private activeStreams: Map<string, AbortController>;

  constructor(providers: AIProvider[], circuitBreakerConfig: CircuitBreakerConfig) {
    super();
    this.fallbackProvider = new FallbackProvider(providers, circuitBreakerConfig);
    this.cache = new AICache();
    this.activeStreams = new Map();
  }

  async executeRequest(request: AIRequest): Promise<AIResponse> {
    // Check cache first
    const cached = this.cache.get(request);
    if (cached) {
      this.emit('cache_hit', request.id);
      return cached;
    }

    // Execute with fallback
    const response = await this.fallbackProvider.executeRequestWithFallback(request);
    
    // Cache successful responses
    if (!response.fromFallback) {
      this.cache.set(request, response);
    }

    this.emit('request_completed', request, response);
    return response;
  }

  async executeStreamingRequest(request: AIRequest): Promise<AsyncIterable<AIStreamChunk>> {
    const abortController = new AbortController();
    this.activeStreams.set(request.id, abortController);

    try {
      const stream = this.createStream(request, abortController.signal);
      return stream;
    } catch (error) {
      this.activeStreams.delete(request.id);
      throw error;
    }
  }

  private async *createStream(request: AIRequest, signal: AbortSignal): AsyncIterable<AIStreamChunk> {
    const startTime = Date.now();
    let content = '';
    let chunkIndex = 0;

    try {
      // Simulate streaming response
      const words = `Streaming response from ${request.provider} for: ${request.prompt}`.split(' ');
      
      for (const word of words) {
        // Check for abort
        if (signal.aborted) {
          throw new Error('Stream aborted');
        }

        // Small fixed inter-chunk delay to prevent event-loop starvation.
        // TODO: Replace this simulated word-by-word stream with a real SSE/streaming
        //       connection to the provider once streaming endpoints are integrated.
        await new Promise(resolve => setTimeout(resolve, 50));

        const delta = word + ' ';
        content += delta;

        const chunk: AIStreamChunk = {
          id: crypto.randomUUID(),
          requestId: request.id,
          content,
          delta,
          isComplete: chunkIndex === words.length - 1,
          timestamp: Date.now(),
        };

        this.emit('stream_chunk', chunk);
        yield chunk;
        chunkIndex++;
      }

      // Cache the complete response
      const response: AIResponse = {
        id: crypto.randomUUID(),
        requestId: request.id,
        provider: request.provider,
        content,
        usage: {
          promptTokens: request.prompt.length / 4,
          completionTokens: content.length / 4,
          totalTokens: (request.prompt.length + content.length) / 4,
        },
        model: request.provider === 'openai' ? 'gpt-4' : 'claude-3',
        timestamp: Date.now(),
        latency: Date.now() - startTime,
        fromCache: false,
        fromFallback: false,
      };

      this.cache.set(request, response);
      this.emit('stream_completed', request, response);

    } finally {
      this.activeStreams.delete(request.id);
    }
  }

  abortStream(requestId: string): void {
    const controller = this.activeStreams.get(requestId);
    if (controller) {
      controller.abort();
      this.activeStreams.delete(requestId);
      this.emit('stream_aborted', requestId);
    }
  }

  getMetrics(): ResilienceMetrics {
    const providerHealth = this.fallbackProvider.getProviderHealth();
    const rateLimitStatus = this.fallbackProvider.getRateLimitStatus();

    return {
      totalRequests: 0, // Would need to track this
      successfulRequests: 0,
      failedRequests: 0,
      circuitBreakerTrips: 0,
      fallbackActivations: 0,
      cacheHits: this.cache.size(),
      averageLatency: 0,
      providerHealth: Object.fromEntries(
        Object.entries(providerHealth).map(([name, state]) => [
          name,
          state.state === 'CLOSED' ? 100 : state.state === 'HALF_OPEN' ? 50 : 0
        ])
      ),
    };
  }

  cleanup(): void {
    // Abort all active streams
    for (const [requestId, controller] of this.activeStreams.entries()) {
      controller.abort();
    }
    this.activeStreams.clear();

    // Cleanup cache
    this.cache.cleanup();
  }
}

// ============================================================================
// Factory Function
// ============================================================================

export function createResilientAIService(): StreamingAIService {
  const providers: AIProvider[] = [
    {
      name: 'openai',
      endpoint: 'https://api.openai.com/v1/chat/completions',
      apiKey: process.env.OPENAI_API_KEY || '',
      maxRetries: 3,
      timeout: 30000,
      rateLimit: {
        requestsPerMinute: 60,
        requestsPerHour: 3600,
      },
    },
    {
      name: 'anthropic',
      endpoint: 'https://api.anthropic.com/v1/messages',
      apiKey: process.env.ANTHROPIC_API_KEY || '',
      maxRetries: 3,
      timeout: 30000,
      rateLimit: {
        requestsPerMinute: 50,
        requestsPerHour: 3000,
      },
    },
  ];

  const circuitBreakerConfig: CircuitBreakerConfig = {
    failureThreshold: 5,
    recoveryTimeout: 60000, // 1 minute
    monitoringPeriod: 300000, // 5 minutes
    expectedRecoveryTime: 30000, // 30 seconds
  };

  return new StreamingAIService(providers, circuitBreakerConfig);
}
