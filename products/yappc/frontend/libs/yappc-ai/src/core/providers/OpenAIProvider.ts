import OpenAI from 'openai';

import { AIService } from '../AIService';
import { AIServiceError } from '../types';

import type {
  AIProvider,
  AIServiceConfig,
  CompletionChunk,
  CompletionOptions,
  CompletionResponse,
  EmbeddingResponse,
  TokenUsage,
} from '../types';

/**
 * OpenAI provider implementation
 * Supports GPT-3.5, GPT-4, and embedding models
 */
export class OpenAIProvider extends AIService {
  readonly provider: AIProvider = 'openai';
  private client: OpenAI;

  /**
   *
   */
  constructor(config: AIServiceConfig) {
    super(config);

    this.client = new OpenAI({
      apiKey: config.apiKey,
      baseURL: config.baseURL,
      organization: config.organization,
      timeout: config.timeout,
      maxRetries: 0, // We handle retries ourselves
    });
  }

  /**
   * Complete a prompt using OpenAI's chat completion API
   */
  async complete(
    prompt: string,
    options?: CompletionOptions
  ): Promise<CompletionResponse> {
    await this.checkRateLimit();

    const cacheKey = this.generateCacheKey(prompt, options);
    const cached = this.getFromCache<CompletionResponse>(cacheKey);
    if (cached) return cached;

    const model = options?.model || this.config.defaultModel || 'gpt-3.5-turbo';

    return this.retryWithBackoff(async () => {
      try {
        const messages = this.formatChatMessages(prompt, options);

        const response = await this.client.chat.completions.create({
          model,
          messages,
          temperature: options?.temperature,
          max_tokens: options?.maxTokens,
          top_p: options?.topP,
          frequency_penalty: options?.frequencyPenalty,
          presence_penalty: options?.presencePenalty,
          stop: options?.stopSequences,
          user: options?.user,
        });

        const choice = response.choices[0];
        if (!choice) {
          throw new AIServiceError(
            'No completion choice returned',
            'NO_CHOICE',
            undefined,
            this.provider
          );
        }

        const result: CompletionResponse = {
          content: choice.message.content || '',
          model: response.model,
          finishReason: this.mapFinishReason(choice.finish_reason),
          usage: this.mapUsage(response.usage),
          metadata: {
            id: response.id,
            created: response.created,
          },
        };

        this.saveToCache(cacheKey, result);
        return result;
      } catch (error) {
        throw this.handleError(error);
      }
    });
  }

  /**
   * Stream a completion using OpenAI's streaming API
   */
  async *stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterableIterator<CompletionChunk> {
    await this.checkRateLimit();

    const model = options?.model || this.config.defaultModel || 'gpt-3.5-turbo';

    try {
      const messages = this.formatChatMessages(prompt, options);

      const stream = await this.client.chat.completions.create({
        model,
        messages,
        temperature: options?.temperature,
        max_tokens: options?.maxTokens,
        top_p: options?.topP,
        frequency_penalty: options?.frequencyPenalty,
        presence_penalty: options?.presencePenalty,
        stop: options?.stopSequences,
        user: options?.user,
        stream: true,
      });

      for await (const chunk of stream) {
        const delta = chunk.choices[0]?.delta;
        const content = delta?.content || '';
        const finishReason = chunk.choices[0]?.finish_reason;

        yield {
          content,
          done: finishReason !== null && finishReason !== undefined,
          model: chunk.model,
          finishReason: finishReason
            ? this.mapFinishReason(finishReason)
            : undefined,
        };
      }
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Generate embeddings using OpenAI's embedding API
   */
  async embed(text: string, model?: string): Promise<EmbeddingResponse> {
    await this.checkRateLimit();

    const embeddingModel = model || 'text-embedding-ada-002';
    const cacheKey = `embed:${embeddingModel}:${text}`;
    const cached = this.getFromCache<EmbeddingResponse>(cacheKey);
    if (cached) return cached;

    return this.retryWithBackoff(async () => {
      try {
        const response = await this.client.embeddings.create({
          model: embeddingModel,
          input: text,
        });

        const result: EmbeddingResponse = {
          embedding: response.data[0].embedding,
          model: response.model,
          usage: this.mapUsage(response.usage),
        };

        this.saveToCache(cacheKey, result);
        return result;
      } catch (error) {
        throw this.handleError(error);
      }
    });
  }

  /**
   * Get approximate token count (rough estimation)
   * For accurate counts, use tiktoken library
   */
  getTokenCount(text: string, _model?: string): number {
    // Rough approximation: ~4 characters per token for English
    // This is a simplification; use tiktoken for accurate counts
    return Math.ceil(text.length / 4);
  }

  /**
   * Health check - verify API key works
   */
  async healthCheck(): Promise<boolean> {
    try {
      // Use a minimal completion to test the API
      await this.client.chat.completions.create({
        model: 'gpt-3.5-turbo',
        messages: [{ role: 'user', content: 'test' }],
        max_tokens: 1,
      });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Map OpenAI finish reason to our format
   */
  private mapFinishReason(
    reason: string | null | undefined
  ): 'stop' | 'length' | 'content_filter' {
    switch (reason) {
      case 'stop':
        return 'stop';
      case 'length':
        return 'length';
      case 'content_filter':
        return 'content_filter';
      default:
        return 'stop';
    }
  }

  /**
   * Map OpenAI usage to our format
   */
  private mapUsage(
    usage:
      | OpenAI.Completions.CompletionUsage
      | { prompt_tokens?: number; completion_tokens?: number; total_tokens?: number }
      | undefined
  ): TokenUsage {
    return {
      promptTokens: usage?.prompt_tokens || 0,
      completionTokens: usage?.completion_tokens || 0,
      totalTokens: usage?.total_tokens || 0,
    };
  }

  /**
   * Handle and transform errors
   */
  private handleError(error: unknown): AIServiceError {
    if (error && typeof error === 'object' && 'status' in error) {
      const apiError = error as { status?: number; message?: string; code?: string };
      const cause = error instanceof Error ? error : new Error(apiError.message || 'OpenAI API error');
      return new AIServiceError(
        apiError.message || 'OpenAI API error',
        apiError.code || 'OPENAI_ERROR',
        apiError.status,
        this.provider,
        cause
      );
    }

    if (error instanceof Error) {
      return new AIServiceError(
        error.message,
        'UNKNOWN_ERROR',
        undefined,
        this.provider,
        error
      );
    }

    return new AIServiceError(
      'Unknown error occurred',
      'UNKNOWN_ERROR',
      undefined,
      this.provider
    );
  }
}
