export interface CompletionOptions {
  maxTokens?: number;
  temperature?: number;
  signal?: AbortSignal;
  [key: string]: unknown;
}

export interface CompletionResponse {
  content: string;
  metadata?: Record<string, unknown>;
}

export interface CompletionStreamChunk {
  content: string;
  done?: boolean;
}

export interface IAIService {
  complete(
    prompt: string | { messages: Array<{ role: string; content: string }> },
    options?: CompletionOptions
  ): Promise<CompletionResponse>;
  stream(
    prompt: string,
    options?: CompletionOptions
  ): AsyncIterable<CompletionStreamChunk>;
}

export interface SentimentOptions {
  signal?: AbortSignal;
  [key: string]: unknown;
}

export type SentimentLabel = 'positive' | 'neutral' | 'negative';

export interface SentimentResult {
  sentiment: SentimentLabel;
  label?: SentimentLabel;
  confidence: number;
  scores: Record<SentimentLabel, number>;
  emotions?: Record<string, number>;
  keywords?: string[];
  explanation?: string;
}

export interface SentimentAnalyzer {
  analyze(text: string, options?: SentimentOptions): Promise<SentimentResult>;
}
