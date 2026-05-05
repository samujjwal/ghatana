import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const embeddingsCreate = vi.fn();
const chatCreate = vi.fn();
const transcriptionCreate = vi.fn();

vi.mock('openai', () => ({
  default: vi.fn().mockImplementation(() => ({
    embeddings: {
      create: embeddingsCreate,
    },
    chat: {
      completions: {
        create: chatCreate,
      },
    },
    audio: {
      transcriptions: {
        create: transcriptionCreate,
      },
    },
  })),
}));

describe('FlashIt AI disabled mode', () => {
  beforeEach(() => {
    embeddingsCreate.mockReset();
    chatCreate.mockReset();
    transcriptionCreate.mockReset();
    vi.resetModules();
  });

  afterEach(() => {
    vi.unstubAllEnvs();
  });

  it('prevents embedding generation before any OpenAI request is attempted', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'true');
    const { VectorEmbeddingService } = await import('../embeddings/vector-service');

    await expect(VectorEmbeddingService.generateEmbedding('hello world')).rejects.toThrow(/AI is disabled/i);
    expect(embeddingsCreate).not.toHaveBeenCalled();
  });

  it('prevents visual-search client initialization before any OpenAI request is attempted', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'true');
    const { VisualSearchService } = await import('../search/visualSearch');

    expect(() => (VisualSearchService as unknown as { getOpenAI: () => unknown }).getOpenAI()).toThrow(/AI is disabled/i);
    expect(chatCreate).not.toHaveBeenCalled();
  });

  it('prevents LLM completions before any OpenAI request is attempted', async () => {
    vi.stubEnv('FLASHIT_AI_DISABLED', 'true');
    const { getLLMService } = await import('../llm/llm-service');

    expect(() => getLLMService()).toThrow(/AI is disabled/i);
    expect(chatCreate).not.toHaveBeenCalled();
  });
});
