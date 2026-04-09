/**
 * AI Service
 *
 * Core service for LLM integration with fallback behavior, error handling,
 * and confidence scoring. Wraps the HTTP client with higher-level business logic.
 *
 * @doc.type service
 * @doc.purpose LLM integration with resilience patterns
 * @doc.layer product
 * @doc.pattern Service Layer
 */

import { AIServiceClient } from '../../clients/ai/AIServiceClient';
import type {
  LLMRequest,
  LLMResponse,
  ModelProvider,
  AIError,
  AIErrorCode,
  RequestMetadata,
} from './types';

// ============================================================================
// Types
// ============================================================================

export interface AIServiceConfig {
  primaryProvider: ModelProvider;
  fallbackProviders: ModelProvider[];
  timeoutMs: number;
  maxRetries: number;
  enableCaching: boolean;
  confidenceThreshold: number;
}

export interface GenerateOptions {
  temperature?: number;
  maxTokens?: number;
  topP?: number;
  frequencyPenalty?: number;
  presencePenalty?: number;
  stopSequences?: string[];
}

export interface GenerateResult {
  text: string;
  confidence: number;
  model: string;
  provider: ModelProvider;
  latencyMs: number;
  tokenCount: number;
  metadata: RequestMetadata;
}

// ============================================================================
// Constants
// ============================================================================

const DEFAULT_CONFIG: AIServiceConfig = {
  primaryProvider: 'openai',
  fallbackProviders: ['anthropic', 'local'],
  timeoutMs: 30000,
  maxRetries: 3,
  enableCaching: true,
  confidenceThreshold: 0.7,
};

const ERROR_CODES: Record<string, AIErrorCode> = {
  TIMEOUT: 'TIMEOUT',
  RATE_LIMIT: 'RATE_LIMIT',
  INVALID_REQUEST: 'INVALID_REQUEST',
  AUTHENTICATION: 'AUTHENTICATION',
  SERVER_ERROR: 'SERVER_ERROR',
  NETWORK_ERROR: 'NETWORK_ERROR',
  UNKNOWN: 'UNKNOWN',
};

// ============================================================================
// Cache
// ============================================================================

interface CacheEntry {
  result: GenerateResult;
  timestamp: number;
}

class ResponseCache {
  private cache = new Map<string, CacheEntry>();
  private readonly ttlMs: number;

  constructor(ttlMs = 5 * 60 * 1000) {
    this.ttlMs = ttlMs;
  }

  get(key: string): GenerateResult | null {
    const entry = this.cache.get(key);
    if (!entry) return null;

    if (Date.now() - entry.timestamp > this.ttlMs) {
      this.cache.delete(key);
      return null;
    }

    return entry.result;
  }

  set(key: string, result: GenerateResult): void {
    this.cache.set(key, { result, timestamp: Date.now() });
  }

  clear(): void {
    this.cache.clear();
  }
}

// ============================================================================
// Error Classification
// ============================================================================

function classifyError(error: unknown): AIError {
  if (error instanceof Error) {
    const message = error.message.toLowerCase();

    if (message.includes('timeout')) {
      return { code: ERROR_CODES.TIMEOUT, message: error.message, retryable: true };
    }
    if (message.includes('rate limit') || message.includes('429')) {
      return { code: ERROR_CODES.RATE_LIMIT, message: error.message, retryable: true };
    }
    if (message.includes('auth') || message.includes('401') || message.includes('403')) {
      return { code: ERROR_CODES.AUTHENTICATION, message: error.message, retryable: false };
    }
    if (message.includes('network') || message.includes('fetch')) {
      return { code: ERROR_CODES.NETWORK_ERROR, message: error.message, retryable: true };
    }
    if (message.includes('500') || message.includes('502') || message.includes('503')) {
      return { code: ERROR_CODES.SERVER_ERROR, message: error.message, retryable: true };
    }
    if (message.includes('invalid') || message.includes('400')) {
      return { code: ERROR_CODES.INVALID_REQUEST, message: error.message, retryable: false };
    }
  }

  return {
    code: ERROR_CODES.UNKNOWN,
    message: error instanceof Error ? error.message : 'Unknown error',
    retryable: false,
  };
}

// ============================================================================
// Service Implementation
// ============================================================================

export class AIService {
  private config: AIServiceConfig;
  private cache: ResponseCache;
  private clients: Map<ModelProvider, AIServiceClient>;

  constructor(config: Partial<AIServiceConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
    this.cache = new ResponseCache();
    this.clients = new Map();

    // Initialize clients for each provider
    for (const provider of [this.config.primaryProvider, ...this.config.fallbackProviders]) {
      if (!this.clients.has(provider)) {
        this.clients.set(
          provider,
          new AIServiceClient({
            baseUrl: this.getProviderUrl(provider),
            timeout: this.config.timeoutMs,
            maxRetries: this.config.maxRetries,
          }),
        );
      }
    }
  }

  private getProviderUrl(provider: ModelProvider): string {
    const urls: Record<ModelProvider, string> = {
      openai: '/api/ai/openai',
      anthropic: '/api/ai/anthropic',
      local: '/api/ai/local',
      azure: '/api/ai/azure',
    };
    return urls[provider] || '/api/ai';
  }

  private generateCacheKey(prompt: string, options: GenerateOptions): string {
    return `${prompt}:${JSON.stringify(options)}`;
  }

  private calculateConfidence(response: LLMResponse): number {
    // Calculate confidence based on token probabilities and response quality
    const tokenProbs = response.tokenProbs ?? [];
    const baseConfidence = tokenProbs.length > 0
      ? tokenProbs.reduce((sum: number, prob: number) => sum + prob, 0) / tokenProbs.length
      : 0.8;

    // Penalize very short or very long responses
    const lengthPenalty = response.text.length < 10 || response.text.length > 4000 ? 0.8 : 1.0;

    // Penalize responses with error indicators
    const errorIndicators = ['error', 'failed', 'unable', 'cannot', 'sorry'];
    const hasErrorIndicator = errorIndicators.some((word) =>
      response.text.toLowerCase().includes(word),
    );
    const errorPenalty = hasErrorIndicator ? 0.7 : 1.0;

    return Math.min(1, baseConfidence * lengthPenalty * errorPenalty);
  }

  async generate(prompt: string, options: GenerateOptions = {}): Promise<GenerateResult> {
    const cacheKey = this.generateCacheKey(prompt, options);

    // Check cache
    if (this.config.enableCaching) {
      const cached = this.cache.get(cacheKey);
      if (cached) {
        return { ...cached, metadata: { ...cached.metadata, cached: true } };
      }
    }

    const providers = [this.config.primaryProvider, ...this.config.fallbackProviders];
    const startTime = Date.now();
    let lastError: AIError | null = null;

    for (const provider of providers) {
      try {
        const client = this.clients.get(provider);
        if (!client) continue;

        const request: LLMRequest = {
          prompt,
          temperature: options.temperature ?? 0.7,
          maxTokens: options.maxTokens ?? 1000,
          topP: options.topP ?? 1.0,
          frequencyPenalty: options.frequencyPenalty ?? 0,
          presencePenalty: options.presencePenalty ?? 0,
          stopSequences: options.stopSequences,
        };

        const response: LLMResponse = await client.generate(request);
        const confidence = this.calculateConfidence(response);

        const result: GenerateResult = {
          text: response.text,
          confidence,
          model: response.model,
          provider,
          latencyMs: Date.now() - startTime,
          tokenCount: response.tokenCount ?? 0,
          metadata: {
            cached: false,
            fallbackUsed: provider !== this.config.primaryProvider,
            retryCount: 0,
            timestamp: Date.now(),
          },
        };

        // Cache successful responses
        if (this.config.enableCaching && confidence >= this.config.confidenceThreshold) {
          this.cache.set(cacheKey, result);
        }

        return result;
      } catch (error) {
        lastError = classifyError(error);

        // Don't retry non-retryable errors
        if (!lastError.retryable) {
          break;
        }

        // Continue to next provider (fallback)
        continue;
      }
    }

    // All providers failed
    throw new Error(
      `AI generation failed after trying ${providers.length} providers. ` +
        `Last error: ${lastError?.code} - ${lastError?.message}`,
    );
  }

  async generateWithFallback(
    prompt: string,
    fallbackText: string,
    options: GenerateOptions = {},
  ): Promise<GenerateResult> {
    try {
      return await this.generate(prompt, options);
    } catch {
      // Return fallback result on failure
      return {
        text: fallbackText,
        confidence: 0,
        model: 'fallback',
        provider: 'local',
        latencyMs: 0,
        tokenCount: 0,
        metadata: {
          cached: false,
          fallbackUsed: true,
          retryCount: 0,
          timestamp: Date.now(),
        },
      };
    }
  }

  clearCache(): void {
    this.cache.clear();
  }

  getConfig(): AIServiceConfig {
    return { ...this.config };
  }
}

// ============================================================================
// Singleton Export
// ============================================================================

let globalService: AIService | null = null;

export function getAIService(config?: Partial<AIServiceConfig>): AIService {
  if (!globalService) {
    globalService = new AIService(config);
  }
  return globalService;
}

export function resetAIService(): void {
  globalService = null;
}
