import type {
  AIProvider,
  AIServiceConfig,
  AIServiceError,
  CacheEntry,
  ChatCompletionOptions,
  ChatMessage,
  CompletionChunk,
  CompletionOptions,
  CompletionResponse,
  EmbeddingResponse,
  IAIService,
  RateLimiterState,
} from './types';

/**
 * Base AI service class with common functionality
 */
export abstract class AIService implements IAIService {
  abstract readonly provider: AIProvider;
  readonly config: AIServiceConfig;

  private cache: Map<
    string,
    CacheEntry<CompletionResponse | EmbeddingResponse>
  > = new Map();
  private rateLimiter: RateLimiterState;

  /**
   *
   */
  constructor(config: AIServiceConfig) {
    this.config = {
      timeout: 30000,
      maxRetries: 3,
      enableCache: true,
      cacheTTL: 60 * 60 * 1000, // 1 hour
      rateLimit: 60, // 60 requests per minute
      ...config,
    };

    this.rateLimiter = {
      requests: [],
      limit: this.config.rateLimit || 60,
      window: 60 * 1000, // 1 minute
    };
  }

  /**
   * Complete a prompt (must be implemented by subclasses)
   */
  abstract complete(
    prompt: string,
    options?: CompletionOptions
  ): Promise<CompletionResponse>;

  /**
   * Stream a completion (must be implemented by subclasses)
   */
  abstract stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterableIterator<CompletionChunk>;

  /**
   * Generate embeddings (must be implemented by subclasses)
   */
  abstract embed(text: string, model?: string): Promise<EmbeddingResponse>;

  /**
   * Get token count (must be implemented by subclasses)
   */
  abstract getTokenCount(text: string, model?: string): number;

  /**
   * Health check (must be implemented by subclasses)
   */
  abstract healthCheck(): Promise<boolean>;

  /**
   * Format messages for chat completion
   */
  protected formatChatMessages(
    prompt: string,
    options?: ChatCompletionOptions
  ): ChatMessage[] {
    const messages: ChatMessage[] = [];

    // Add system message if provided
    if (options?.systemPrompt) {
      messages.push({
        role: 'system',
        content: options.systemPrompt,
      });
    }

    // Add any pre-existing messages
    if (options?.messages) {
      messages.push(...options.messages);
    }

    // Add user message
    messages.push({
      role: 'user',
      content: prompt,
    });

    return messages;
  }

  /**
   * Get cached response
   */
  protected getFromCache<T extends CompletionResponse | EmbeddingResponse>(
    key: string
  ): T | null {
    if (!this.config.enableCache) return null;

    const entry = this.cache.get(key) as CacheEntry<T> | undefined;
    if (!entry) return null;

    // Check if cache entry is expired
    const now = Date.now();
    if (now - entry.timestamp > entry.ttl) {
      this.cache.delete(key);
      return null;
    }

    return entry.data;
  }

  /**
   * Save to cache
   */
  protected saveToCache<T extends CompletionResponse | EmbeddingResponse>(
    key: string,
    data: T,
    ttl?: number
  ): void {
    if (!this.config.enableCache) return;

    this.cache.set(key, {
      data,
      timestamp: Date.now(),
      ttl: ttl || this.config.cacheTTL || 60 * 60 * 1000,
    });
  }

  /**
   * Generate cache key
   */
  protected generateCacheKey(
    prompt: string,
    options?: CompletionOptions
  ): string {
    const optionsStr = options
      ? JSON.stringify({
          model: options.model,
          temperature: options.temperature,
          maxTokens: options.maxTokens,
          systemPrompt: options.systemPrompt,
        })
      : '';
    return `${this.provider}:${prompt}:${optionsStr}`;
  }

  /**
   * Check rate limit
   */
  protected async checkRateLimit(): Promise<void> {
    const now = Date.now();
    const windowStart = now - this.rateLimiter.window;

    // Remove old requests outside the window
    this.rateLimiter.requests = this.rateLimiter.requests.filter(
      (time) => time > windowStart
    );

    // Check if we've exceeded the limit
    if (this.rateLimiter.requests.length >= this.rateLimiter.limit) {
      const oldestRequest = this.rateLimiter.requests[0];
      const waitTime = this.rateLimiter.window - (now - oldestRequest);

      if (waitTime > 0) {
        // Wait until the oldest request falls outside the window
        await new Promise((resolve) => setTimeout(resolve, waitTime));
        return this.checkRateLimit(); // Re-check after waiting
      }
    }

    // Add current request
    this.rateLimiter.requests.push(now);
  }

  /**
   * Retry with exponential backoff
   */
  protected async retryWithBackoff<T>(
    fn: () => Promise<T>,
    maxRetries: number = this.config.maxRetries || 3
  ): Promise<T> {
    let lastError: Error | undefined;

    for (let attempt = 0; attempt <= maxRetries; attempt++) {
      try {
        return await fn();
      } catch (error) {
        lastError = error as Error;

        // Don't retry on client errors (4xx)
        if (error instanceof Error && 'statusCode' in error) {
          const statusCode = (error as AIServiceError).statusCode;
          if (statusCode && statusCode >= 400 && statusCode < 500) {
            throw error;
          }
        }

        // Don't retry on last attempt
        if (attempt === maxRetries) {
          throw error;
        }

        // Exponential backoff: 2^attempt * 1000ms
        const delay = Math.pow(2, attempt) * 1000;
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }

    throw lastError;
  }

  /**
   * Clear cache
   */
  public clearCache(): void {
    this.cache.clear();
  }

  /**
   * Get cache size
   */
  public getCacheSize(): number {
    return this.cache.size;
  }

  /**
   * Get rate limit status
   */
  public getRateLimitStatus(): {
    used: number;
    limit: number;
    resetsIn: number;
  } {
    const now = Date.now();
    const windowStart = now - this.rateLimiter.window;
    const activeRequests = this.rateLimiter.requests.filter(
      (time) => time > windowStart
    );

    const oldestRequest = activeRequests[0] || now;
    const resetsIn = Math.max(
      0,
      this.rateLimiter.window - (now - oldestRequest)
    );

    return {
      used: activeRequests.length,
      limit: this.rateLimiter.limit,
      resetsIn,
    };
  }
}
