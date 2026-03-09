import { z } from 'zod';

/**
 * Completion options for AI services
 */
export interface CompletionOptions {
  /** Model to use (e.g., 'gpt-4', 'claude-3-opus') */
  model?: string;
  /** Temperature (0-2, higher = more creative) */
  temperature?: number;
  /** Maximum tokens to generate */
  maxTokens?: number;
  /** Stop sequences */
  stopSequences?: string[];
  /** System prompt/instructions */
  systemPrompt?: string;
  /** Top-p sampling */
  topP?: number;
  /** Frequency penalty */
  frequencyPenalty?: number;
  /** Presence penalty */
  presencePenalty?: number;
  /** User identifier for tracking */
  user?: string;
}

/**
 * Streaming completion chunk
 */
export interface CompletionChunk {
  /** Chunk content */
  content: string;
  /** Whether this is the final chunk */
  done: boolean;
  /** Model used */
  model?: string;
  /** Finish reason if done */
  finishReason?: 'stop' | 'length' | 'content_filter' | 'error';
}

/**
 * Completion response
 */
export interface CompletionResponse {
  /** Generated content */
  content: string;
  /** Model used */
  model: string;
  /** Finish reason */
  finishReason: 'stop' | 'length' | 'content_filter';
  /** Token usage */
  usage: TokenUsage;
  /** Response metadata */
  metadata?: Record<string, unknown>;
}

/**
 * Token usage information
 */
export interface TokenUsage {
  /** Prompt tokens */
  promptTokens: number;
  /** Completion tokens */
  completionTokens: number;
  /** Total tokens */
  totalTokens: number;
}

/**
 * Embedding response
 */
export interface EmbeddingResponse {
  /** Embedding vector */
  embedding: number[];
  /** Model used */
  model: string;
  /** Token usage */
  usage: TokenUsage;
}

/**
 * AI service configuration
 */
export interface AIServiceConfig {
  /** API key */
  apiKey: string;
  /** Base URL (for custom endpoints) */
  baseURL?: string;
  /** Default model */
  defaultModel?: string;
  /** Timeout in milliseconds */
  timeout?: number;
  /** Max retries on failure */
  maxRetries?: number;
  /** Enable response caching */
  enableCache?: boolean;
  /** Cache TTL in milliseconds */
  cacheTTL?: number;
  /** Rate limit (requests per minute) */
  rateLimit?: number;
  /** Organization ID (OpenAI) */
  organization?: string;
}

/**
 * AI service provider type
 */
export type AIProvider = 'openai' | 'anthropic' | 'local' | 'custom';

/**
 * AI service error
 */
export class AIServiceError extends Error {
  /**
   *
   */
  constructor(
    message: string,
    public code: string,
    public statusCode?: number,
    public provider?: AIProvider,
    public cause?: Error
  ) {
    super(message);
    this.name = 'AIServiceError';
  }
}

/**
 * Zod schema for completion options validation
 */
export const CompletionOptionsSchema = z.object({
  model: z.string().optional(),
  temperature: z.number().min(0).max(2).optional(),
  maxTokens: z.number().positive().optional(),
  stopSequences: z.array(z.string()).optional(),
  systemPrompt: z.string().optional(),
  topP: z.number().min(0).max(1).optional(),
  frequencyPenalty: z.number().min(-2).max(2).optional(),
  presencePenalty: z.number().min(-2).max(2).optional(),
  user: z.string().optional(),
});

/**
 * Zod schema for AI service configuration validation
 */
export const AIServiceConfigSchema = z.object({
  apiKey: z.string().min(1),
  baseURL: z.string().url().optional(),
  defaultModel: z.string().optional(),
  timeout: z.number().positive().optional(),
  maxRetries: z.number().min(0).max(10).optional(),
  enableCache: z.boolean().optional(),
  cacheTTL: z.number().positive().optional(),
  rateLimit: z.number().positive().optional(),
  organization: z.string().optional(),
});

/**
 * Cache entry
 */
export interface CacheEntry<T> {
  data: T;
  timestamp: number;
  ttl: number;
}

/**
 * Rate limiter state
 */
export interface RateLimiterState {
  requests: number[];
  limit: number;
  window: number; // in milliseconds
}

/**
 * AI service interface
 */
export interface IAIService {
  /** Provider name */
  readonly provider: AIProvider;

  /** Configuration */
  readonly config: AIServiceConfig;

  /**
   * Complete a prompt
   */
  complete(
    prompt: string,
    options?: CompletionOptions
  ): Promise<CompletionResponse>;

  /**
   * Stream a completion
   */
  stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterableIterator<CompletionChunk>;

  /**
   * Generate embeddings
   */
  embed(text: string, model?: string): Promise<EmbeddingResponse>;

  /**
   * Get token count for text
   */
  getTokenCount(text: string, model?: string): number;

  /**
   * Check if service is healthy
   */
  healthCheck(): Promise<boolean>;
}

/**
 * Message role for chat completions
 */
export type MessageRole = 'system' | 'user' | 'assistant';

/**
 * Chat message
 */
export interface ChatMessage {
  role: MessageRole;
  content: string;
  name?: string;
}

/**
 * Chat completion options (extends CompletionOptions)
 */
export interface ChatCompletionOptions extends CompletionOptions {
  messages?: ChatMessage[];
}
