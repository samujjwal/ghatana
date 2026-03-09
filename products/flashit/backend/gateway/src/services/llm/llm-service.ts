/**
 * LLM Service
 * Abstraction layer for LLM providers supporting OpenAI, Ollama, and others
 */

import OpenAI from 'openai';

export type LLMProvider = 'openai' | 'ollama' | 'anthropic';

export interface LLMConfig {
  provider: LLMProvider;
  openai?: {
    apiKey: string;
    model?: string;
  };
  ollama?: {
    baseUrl: string;
    model?: string;
  };
  anthropic?: {
    apiKey: string;
    model?: string;
  };
}

export interface CompletionOptions {
  model?: string;
  temperature?: number;
  maxTokens?: number;
  systemPrompt?: string;
}

export interface TranscriptionOptions {
  language?: string;
  prompt?: string;
  temperature?: number;
}

export class LLMService {
  private config: LLMConfig;
  private openaiClient?: OpenAI;
  private ollamaClient?: OpenAI; // Ollama uses OpenAI-compatible API

  constructor(config: LLMConfig) {
    this.config = config;

    if (config.provider === 'openai' && config.openai) {
      this.openaiClient = new OpenAI({
        apiKey: config.openai.apiKey,
      });
    } else if (config.provider === 'ollama' && config.ollama) {
      // Ollama uses OpenAI-compatible API
      this.ollamaClient = new OpenAI({
        apiKey: 'ollama', // Ollama doesn't require a real API key
        baseURL: config.ollama.baseUrl,
      });
    }
  }

  /**
   * Generate a chat completion
   */
  async complete(
    messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }>,
    options?: CompletionOptions
  ): Promise<string> {
    const client = this.getClient();
    const model = options?.model || this.getDefaultModel();

    const response = await client.chat.completions.create({
      model,
      messages,
      temperature: options?.temperature ?? 0.7,
      max_tokens: options?.maxTokens,
    });

    return response.choices[0]?.message?.content || '';
  }

  /**
   * Transcribe audio using Whisper (OpenAI only for now)
   */
  async transcribe(
    audioFile: File | Buffer | ReadableStream,
    options?: TranscriptionOptions
  ): Promise<{
    text: string;
    language?: string;
    duration?: number;
    segments?: Array<{
      text: string;
      start: number;
      end: number;
    }>;
  }> {
    if (this.config.provider !== 'openai' || !this.openaiClient) {
      throw new Error('Transcription is only supported with OpenAI provider');
    }

    const response = await this.openaiClient.audio.transcriptions.create({
      file: audioFile as any,
      model: 'whisper-1',
      language: options?.language,
      prompt: options?.prompt,
      temperature: options?.temperature,
      response_format: 'verbose_json',
    });

    return {
      text: response.text,
      language: (response as any).language,
      duration: (response as any).duration,
      segments: (response as any).segments,
    };
  }

  /**
   * Generate embeddings for text
   */
  async embed(text: string, model?: string): Promise<number[]> {
    const client = this.getClient();
    const embeddingModel = model || this.getDefaultEmbeddingModel();

    const response = await client.embeddings.create({
      model: embeddingModel,
      input: text,
    });

    return response.data[0].embedding;
  }

  /**
   * Generate embeddings for multiple texts
   */
  async embedBatch(texts: string[], model?: string): Promise<number[][]> {
    const client = this.getClient();
    const embeddingModel = model || this.getDefaultEmbeddingModel();

    const response = await client.embeddings.create({
      model: embeddingModel,
      input: texts,
    });

    return response.data.map(d => d.embedding);
  }

  /**
   * Get the appropriate client based on provider
   */
  private getClient(): OpenAI {
    if (this.config.provider === 'openai' && this.openaiClient) {
      return this.openaiClient;
    } else if (this.config.provider === 'ollama' && this.ollamaClient) {
      return this.ollamaClient;
    }

    throw new Error(`LLM provider ${this.config.provider} not configured`);
  }

  /**
   * Get default model for the provider
   */
  private getDefaultModel(): string {
    switch (this.config.provider) {
      case 'openai':
        return this.config.openai?.model || 'gpt-4o-mini';
      case 'ollama':
        return this.config.ollama?.model || 'llama3';
      case 'anthropic':
        return this.config.anthropic?.model || 'claude-3-sonnet-20240229';
      default:
        return 'gpt-4o-mini';
    }
  }

  /**
   * Get default embedding model for the provider
   */
  private getDefaultEmbeddingModel(): string {
    switch (this.config.provider) {
      case 'openai':
        return 'text-embedding-3-small';
      case 'ollama':
        return 'nomic-embed-text';
      default:
        return 'text-embedding-3-small';
    }
  }
}

// Singleton instance
let llmService: LLMService | null = null;

export function getLLMService(): LLMService {
  if (!llmService) {
    const provider = (process.env.LLM_PROVIDER || 'openai') as LLMProvider;
    
    const config: LLMConfig = {
      provider,
      openai: provider === 'openai' ? {
        apiKey: process.env.OPENAI_API_KEY || '',
        model: process.env.OPENAI_MODEL,
      } : undefined,
      ollama: provider === 'ollama' ? {
        baseUrl: process.env.OLLAMA_BASE_URL || 'http://localhost:11434/v1',
        model: process.env.OLLAMA_MODEL,
      } : undefined,
      anthropic: provider === 'anthropic' ? {
        apiKey: process.env.ANTHROPIC_API_KEY || '',
        model: process.env.ANTHROPIC_MODEL,
      } : undefined,
    };

    llmService = new LLMService(config);
  }

  return llmService;
}
