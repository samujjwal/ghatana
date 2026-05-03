/**
 * @doc.type test-suite
 * @doc.purpose Unit tests for embedding-pipeline — zero-vector anti-corruption guard
 * @doc.layer application
 * @doc.pattern Unit Test
 *
 * Key invariant (P1-B):
 *   When no LLM provider is configured (OPENAI_API_KEY and OLLAMA_BASE_URL are absent)
 *   the pipeline MUST NOT insert any rows into the DB, and MUST emit a structured
 *   warning to stderr instead.
 *
 * Also verifies:
 *   - When a real provider is configured, itemEmbedding.upsert IS called.
 *   - Zero-vector insertion never occurs under any circumstances.
 */

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// ---------------------------------------------------------------------------
// Prisma mock
// ---------------------------------------------------------------------------

const mockUpsert = vi.fn().mockResolvedValue({ id: 'emb-1' });
const mockCreate = vi.fn().mockResolvedValue({ id: 'metric-1' });
const mockFindMany = vi.fn().mockResolvedValue([
  {
    id: 'item-1',
    title: 'Test item',
    description: 'A test',
    acceptanceCriteria: null,
    notes: null,
    tags: [],
    itemEmbeddings: [],
  },
]);
const mockQueryRaw = vi.fn().mockResolvedValue([]);
const mockDisconnect = vi.fn().mockResolvedValue(undefined);

vi.mock('../../database/client', () => ({
  getPrismaClient: () => ({
    item: { findMany: mockFindMany },
    itemEmbedding: { upsert: mockUpsert },
    aIMetric: { create: mockCreate },
    $queryRaw: mockQueryRaw,
    $disconnect: mockDisconnect,
  }),
}));

// ---------------------------------------------------------------------------
// Import after mocking
// ---------------------------------------------------------------------------

import { runEmbeddingPipeline } from '../embedding-pipeline';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function clearProviderEnv() {
  delete process.env.OPENAI_API_KEY;
  delete process.env.OLLAMA_BASE_URL;
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('runEmbeddingPipeline — no provider configured', () => {
  let stderrSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    clearProviderEnv();
    vi.clearAllMocks();
    stderrSpy = vi.spyOn(process.stderr, 'write').mockImplementation(() => true);
  });

  afterEach(() => {
    stderrSpy.mockRestore();
    clearProviderEnv();
  });

  it('does NOT insert any embedding rows when no provider is configured', async () => {
    await runEmbeddingPipeline({ batchSize: 10 });

    expect(mockUpsert).not.toHaveBeenCalled();
  });

  it('emits a structured WARN log to stderr instead of inserting', async () => {
    await runEmbeddingPipeline({ batchSize: 10 });

    const stderrCalls = stderrSpy.mock.calls.map((args) =>
      typeof args[0] === 'string' ? args[0] : ''
    );

    const warnLine = stderrCalls.find((line) => {
      try {
        const parsed = JSON.parse(line) as { level?: string; action?: string };
        return parsed.level === 'warn' && parsed.action === 'embedding_skipped';
      } catch {
        return false;
      }
    });

    expect(warnLine).toBeDefined();
  });

  it('does NOT insert zero-vector embeddings (all-zeros Float32Array)', async () => {
    await runEmbeddingPipeline({ batchSize: 10 });

    // Confirm upsert was never called with a zero-filled buffer
    const zeroArray = new Float32Array(1536).fill(0);
    const zeroBuffer = Buffer.from(zeroArray.buffer);

    for (const call of mockUpsert.mock.calls) {
      const createData = (call[0] as { create?: { embedding?: Buffer } }).create;
      if (createData?.embedding) {
        expect(createData.embedding).not.toEqual(zeroBuffer);
      }
    }
  });
});

describe('runEmbeddingPipeline — with OpenAI provider', () => {
  const fakeEmbedding = new Array(1536).fill(0.1);
  let stderrSpy: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    process.env.OPENAI_API_KEY = 'sk-test-fake-key';
    delete process.env.OLLAMA_BASE_URL;
    vi.clearAllMocks();
    stderrSpy = vi.spyOn(process.stderr, 'write').mockImplementation(() => true);

    // Mock the fetch used inside the OpenAI embed() implementation
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            data: [{ embedding: fakeEmbedding }],
          }),
        text: () => Promise.resolve(JSON.stringify({ data: [{ embedding: fakeEmbedding }] })),
      })
    );
  });

  afterEach(() => {
    stderrSpy.mockRestore();
    clearProviderEnv();
    vi.unstubAllGlobals();
  });

  it('calls itemEmbedding.upsert with real (non-zero) vectors', async () => {
    await runEmbeddingPipeline({ batchSize: 10 });

    expect(mockUpsert).toHaveBeenCalled();

    const firstCall = mockUpsert.mock.calls[0] as [
      { create: { embedding: Buffer; dimensions: number } },
    ];
    const { embedding, dimensions } = firstCall[0].create;

    // Must be non-zero
    const floats = new Float32Array(embedding.buffer, embedding.byteOffset, dimensions);
    const isAllZero = Array.from(floats).every((v) => v === 0);
    expect(isAllZero).toBe(false);
  });

  it('does NOT emit embedding_skipped warning when provider is configured', async () => {
    await runEmbeddingPipeline({ batchSize: 10 });

    const stderrCalls = stderrSpy.mock.calls.map((args) =>
      typeof args[0] === 'string' ? args[0] : ''
    );
    const skippedLine = stderrCalls.find((line) => {
      try {
        const parsed = JSON.parse(line) as { action?: string };
        return parsed.action === 'embedding_skipped';
      } catch {
        return false;
      }
    });
    expect(skippedLine).toBeUndefined();
  });
});
