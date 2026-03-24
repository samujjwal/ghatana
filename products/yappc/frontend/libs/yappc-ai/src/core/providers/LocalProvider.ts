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
 * Local model provider implementation
 * Supports Ollama, LM Studio, and other OpenAI-compatible local APIs
 */
export class LocalProvider extends AIService {
  readonly provider: AIProvider = 'local';
  private baseURL: string;

  /**
   *
   */
  constructor(config: AIServiceConfig) {
    super(config);

    // Default to Ollama's default endpoint
    this.baseURL = config.baseURL || 'http://localhost:11434';
  }

  /**
   * Complete a prompt using local model API
   */
  async complete(
    prompt: string,
    options?: CompletionOptions
  ): Promise<CompletionResponse> {
    await this.checkRateLimit();

    const cacheKey = this.generateCacheKey(prompt, options);
    const cached = this.getFromCache<CompletionResponse>(cacheKey);
    if (cached) return cached;

    const model = options?.model || this.config.defaultModel || 'llama2';

    return this.retryWithBackoff(async () => {
      try {
        const messages = this.formatChatMessages(prompt, options);

        const response = await fetch(`${this.baseURL}/api/chat`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            model,
            messages,
            options: {
              temperature: options?.temperature,
              num_predict: options?.maxTokens,
              top_p: options?.topP,
              stop: options?.stopSequences,
            },
            stream: false,
          }),
          signal: AbortSignal.timeout(this.config.timeout || 30000),
        });

        if (!response.ok) {
          throw new AIServiceError(
            `HTTP ${response.status}: ${response.statusText}`,
            'HTTP_ERROR',
            response.status,
            this.provider
          );
        }

        const data = await response.json();

        // Handle Ollama response format
        const content = data.message?.content || data.response || '';

        const result: CompletionResponse = {
          content,
          model: data.model || model,
          finishReason: this.mapFinishReason(data.done_reason),
          usage: this.mapUsage(data),
          metadata: {
            created_at: data.created_at,
            done: data.done,
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
   * Stream a completion using local model API
   */
  async *stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterableIterator<CompletionChunk> {
    await this.checkRateLimit();

    const model = options?.model || this.config.defaultModel || 'llama2';

    try {
      const messages = this.formatChatMessages(prompt, options);

      const response = await fetch(`${this.baseURL}/api/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          model,
          messages,
          options: {
            temperature: options?.temperature,
            num_predict: options?.maxTokens,
            top_p: options?.topP,
            stop: options?.stopSequences,
          },
          stream: true,
        }),
        signal: AbortSignal.timeout(this.config.timeout || 30000),
      });

      if (!response.ok) {
        throw new AIServiceError(
          `HTTP ${response.status}: ${response.statusText}`,
          'HTTP_ERROR',
          response.status,
          this.provider
        );
      }

      const reader = response.body?.getReader();
      if (!reader) {
        throw new AIServiceError(
          'No response body',
          'NO_BODY',
          undefined,
          this.provider
        );
      }

      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();

        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.trim()) continue;

          try {
            const data = JSON.parse(line);
            const content = data.message?.content || data.response || '';

            yield {
              content,
              done: data.done === true,
              model: data.model || model,
              finishReason: data.done
                ? this.mapFinishReason(data.done_reason)
                : undefined,
            };

            if (data.done) {
              return;
            }
          } catch {
            // Ignore JSON parse errors in streaming
            continue;
          }
        }
      }
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Generate embeddings using local model
   */
  async embed(text: string, model?: string): Promise<EmbeddingResponse> {
    await this.checkRateLimit();

    const embeddingModel = model || 'nomic-embed-text';
    const cacheKey = `embed:${embeddingModel}:${text}`;
    const cached = this.getFromCache<EmbeddingResponse>(cacheKey);
    if (cached) return cached;

    return this.retryWithBackoff(async () => {
      try {
        const response = await fetch(`${this.baseURL}/api/embeddings`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            model: embeddingModel,
            prompt: text,
          }),
          signal: AbortSignal.timeout(this.config.timeout || 30000),
        });

        if (!response.ok) {
          throw new AIServiceError(
            `HTTP ${response.status}: ${response.statusText}`,
            'HTTP_ERROR',
            response.status,
            this.provider
          );
        }

        const data = await response.json();

        const result: EmbeddingResponse = {
          embedding: data.embedding,
          model: embeddingModel,
          usage: {
            promptTokens: this.getTokenCount(text),
            completionTokens: 0,
            totalTokens: this.getTokenCount(text),
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
   * Get approximate token count
   */
  getTokenCount(text: string, _model?: string): number {
    // Rough approximation: ~4 characters per token
    return Math.ceil(text.length / 4);
  }

  /**
   * Health check - verify local server is running
   */
  async healthCheck(): Promise<boolean> {
    try {
      const response = await fetch(`${this.baseURL}/api/tags`, {
        signal: AbortSignal.timeout(5000),
      });
      return response.ok;
    } catch {
      return false;
    }
  }

  /**
   * Map local model finish reason to our format
   */
  private mapFinishReason(
    reason: string | null | undefined
  ): 'stop' | 'length' | 'content_filter' {
    switch (reason) {
      case 'stop':
      case 'eos':
        return 'stop';
      case 'length':
        return 'length';
      default:
        return 'stop';
    }
  }

  /**
   * Map local model usage to our format
   */
  private mapUsage(data: unknown): TokenUsage {
    return {
      promptTokens: data.prompt_eval_count || 0,
      completionTokens: data.eval_count || 0,
      totalTokens: (data.prompt_eval_count || 0) + (data.eval_count || 0),
    };
  }

  /**
   * Handle and transform errors
   */
  private handleError(error: unknown): AIServiceError {
    if (error instanceof AIServiceError) {
      return error;
    }

    if (error instanceof Error) {
      // Check for network errors
      if (error.name === 'AbortError') {
        return new AIServiceError(
          'Request timeout',
          'TIMEOUT',
          undefined,
          this.provider,
          error
        );
      }

      if (error.message.includes('fetch')) {
        return new AIServiceError(
          'Network error. Is the local model server running?',
          'NETWORK_ERROR',
          undefined,
          this.provider,
          error
        );
      }

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
