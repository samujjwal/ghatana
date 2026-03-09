import Anthropic from '@anthropic-ai/sdk';

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
 * Anthropic (Claude) provider implementation
 * Supports Claude 3 models (Opus, Sonnet, Haiku)
 */
export class AnthropicProvider extends AIService {
  readonly provider: AIProvider = 'anthropic';
  private client: Anthropic;

  /**
   *
   */
  constructor(config: AIServiceConfig) {
    super(config);

    this.client = new Anthropic({
      apiKey: config.apiKey,
      baseURL: config.baseURL,
      timeout: config.timeout,
      maxRetries: 0, // We handle retries ourselves
    });
  }

  /**
   * Complete a prompt using Anthropic's messages API
   */
  async complete(
    prompt: string,
    options?: CompletionOptions
  ): Promise<CompletionResponse> {
    await this.checkRateLimit();

    const cacheKey = this.generateCacheKey(prompt, options);
    const cached = this.getFromCache<CompletionResponse>(cacheKey);
    if (cached) return cached;

    const model =
      options?.model || this.config.defaultModel || 'claude-3-sonnet-20240229';

    return this.retryWithBackoff(async () => {
      try {
        const messages = this.formatChatMessages(prompt, options);

        // Extract system message if present
        const systemMessage = messages.find((m) => m.role === 'system');
        const userMessages = messages.filter((m) => m.role !== 'system');

        const response = await this.client.messages.create({
          model,
          messages: userMessages.map((m) => ({
            role: m.role as 'user' | 'assistant',
            content: m.content,
          })),
          system: systemMessage?.content || options?.systemPrompt,
          max_tokens: options?.maxTokens || 1024,
          temperature: options?.temperature,
          top_p: options?.topP,
          stop_sequences: options?.stopSequences,
        });

        const content = response.content[0];
        if (!content || content.type !== 'text') {
          throw new AIServiceError(
            'No text content in response',
            'NO_CONTENT',
            undefined,
            this.provider
          );
        }

        const result: CompletionResponse = {
          content: content.text,
          model: response.model,
          finishReason: this.mapFinishReason(response.stop_reason),
          usage: this.mapUsage(response.usage),
          metadata: {
            id: response.id,
            type: response.type,
            role: response.role,
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
   * Stream a completion using Anthropic's streaming API
   */
  async *stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterableIterator<CompletionChunk> {
    await this.checkRateLimit();

    const model =
      options?.model || this.config.defaultModel || 'claude-3-sonnet-20240229';

    try {
      const messages = this.formatChatMessages(prompt, options);

      // Extract system message if present
      const systemMessage = messages.find((m) => m.role === 'system');
      const userMessages = messages.filter((m) => m.role !== 'system');

      const stream = await this.client.messages.stream({
        model,
        messages: userMessages.map((m) => ({
          role: m.role as 'user' | 'assistant',
          content: m.content,
        })),
        system: systemMessage?.content || options?.systemPrompt,
        max_tokens: options?.maxTokens || 1024,
        temperature: options?.temperature,
        top_p: options?.topP,
        stop_sequences: options?.stopSequences,
      });

      for await (const event of stream) {
        if (event.type === 'content_block_delta') {
          if (event.delta.type === 'text_delta') {
            yield {
              content: event.delta.text,
              done: false,
              model,
            };
          }
        } else if (event.type === 'message_stop') {
          yield {
            content: '',
            done: true,
            model,
            finishReason: 'stop',
          };
        }
      }
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Anthropic doesn't provide embeddings API
   * This throws an error
   */
  async embed(_text: string, _model?: string): Promise<EmbeddingResponse> {
    throw new AIServiceError(
      'Anthropic does not support embeddings. Use OpenAI or a local model.',
      'NOT_SUPPORTED',
      undefined,
      this.provider
    );
  }

  /**
   * Get approximate token count
   * Anthropic uses a similar tokenization to OpenAI
   */
  getTokenCount(text: string, _model?: string): number {
    // Rough approximation: ~4 characters per token
    return Math.ceil(text.length / 4);
  }

  /**
   * Health check - verify API key works
   */
  async healthCheck(): Promise<boolean> {
    try {
      // Use a minimal completion to test the API
      await this.client.messages.create({
        model: 'claude-3-haiku-20240307',
        messages: [{ role: 'user', content: 'test' }],
        max_tokens: 1,
      });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Map Anthropic stop reason to our format
   */
  private mapFinishReason(
    reason: string | null | undefined
  ): 'stop' | 'length' | 'content_filter' {
    switch (reason) {
      case 'end_turn':
      case 'stop_sequence':
        return 'stop';
      case 'max_tokens':
        return 'length';
      default:
        return 'stop';
    }
  }

  /**
   * Map Anthropic usage to our format
   */
  private mapUsage(usage: {
    input_tokens: number;
    output_tokens: number;
  }): TokenUsage {
    return {
      promptTokens: usage.input_tokens,
      completionTokens: usage.output_tokens,
      totalTokens: usage.input_tokens + usage.output_tokens,
    };
  }

  /**
   * Handle and transform errors
   */
  private handleError(error: unknown): AIServiceError {
    if (error && typeof error === 'object' && 'status' in error) {
      const apiError = error as { status?: number; message?: string };
      const cause = error instanceof Error ? error : new Error(apiError.message || 'Anthropic API error');
      return new AIServiceError(
        apiError.message || 'Anthropic API error',
        'ANTHROPIC_ERROR',
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
