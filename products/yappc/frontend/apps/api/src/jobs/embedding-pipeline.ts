/**
 * Embedding Pipeline Background Job
 * @doc.type service
 * @doc.purpose Generate and update vector embeddings for semantic search
 * @doc.layer product
 * @doc.pattern Background Job
 */

import { getPrismaClient, type PrismaClient } from '../database/client';
import { getArray, getString, isRecord } from '../utils/type-guards';

async function readResponseText(response: Response): Promise<string> {
  try {
    return await response.text();
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`Failed to read embedding provider response body: ${detail}`);
  }
}

async function parseJsonResponse<T>(
  response: Response,
  context: string
): Promise<T> {
  const raw = await readResponseText(response);

  if (!raw) {
    throw new Error(`${context}: empty response body`);
  }

  try {
    return JSON.parse(raw) as T;
  } catch (error) {
    const detail = error instanceof Error ? error.message : String(error);
    throw new Error(`${context}: invalid JSON response (${detail})`);
  }
}

/**
 * Interface for embedding providers.
 * Matches the shape expected by the pipeline without importing from stubs.
 */
interface ILLMProvider {
  name: string;
  embed(text: string): Promise<number[]>;
  generateEmbedding(text: string): Promise<number[]>;
}

/**
 * Sentinel value: when no LLM provider is configured the pipeline
 * must not insert zero-vector rows. Returning null here causes the
 * pipeline to skip the DB write and emit a structured warning instead.
 */
const NO_PROVIDER: null = null;

/**
 * Create embedding provider based on environment configuration.
 *
 * - If OPENAI_API_KEY is set → uses OpenAI text-embedding-3-small via API
 * - If OLLAMA_BASE_URL is set → uses local Ollama embedding model
 * - Otherwise → returns null (pipeline will skip inserts)
 *
 * Zero-vector insertion is explicitly prohibited: it silently corrupts
 * semantic search results and is harder to detect than a missing row.
 */
function createConfiguredProvider(): ILLMProvider | null {
  const openaiKey = process.env.OPENAI_API_KEY;
  const ollamaUrl = process.env.OLLAMA_BASE_URL;

  if (openaiKey) {
    process.stderr.write(JSON.stringify({ level: 'info', event: 'embedding_pipeline.provider_selected', provider: 'openai' }) + '\n');
    return {
      name: 'openai',
      async embed(text: string): Promise<number[]> {
        const response = await fetch('https://api.openai.com/v1/embeddings', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            Authorization: `Bearer ${openaiKey}`,
          },
          body: JSON.stringify({
            model:
              process.env.OPENAI_EMBEDDING_MODEL || 'text-embedding-3-small',
            input: text,
          }),
        });
        if (!response.ok) {
          const errorBody = await readResponseText(response);
          throw new Error(
            `OpenAI embedding API error: ${response.status} ${errorBody}`
          );
        }
        const json = await parseJsonResponse<{
          data: Array<{ embedding: number[] }>;
        }>(response, 'OpenAI embedding API');
        return json.data[0].embedding;
      },
      async generateEmbedding(text: string): Promise<number[]> {
        return this.embed(text);
      },
    } as ILLMProvider;
  }

  if (ollamaUrl) {
    const model = process.env.OLLAMA_EMBEDDING_MODEL || 'nomic-embed-text';
    process.stderr.write(JSON.stringify({ level: 'info', event: 'embedding_pipeline.provider_selected', provider: 'ollama', model }) + '\n');
    return {
      name: 'ollama',
      async embed(text: string): Promise<number[]> {
        const response = await fetch(`${ollamaUrl}/api/embeddings`, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ model, prompt: text }),
        });
        if (!response.ok) {
          const errorBody = await readResponseText(response);
          throw new Error(
            `Ollama embedding API error: ${response.status} ${errorBody}`
          );
        }
        const json = await parseJsonResponse<{ embedding: number[] }>(
          response,
          'Ollama embedding API'
        );
        return json.embedding;
      },
      async generateEmbedding(text: string): Promise<number[]> {
        return this.embed(text);
      },
    } as ILLMProvider;
  }

  // No provider configured — skip inserts rather than pollute the DB with zero vectors
  process.stderr.write(
    JSON.stringify({
      level: 'warn',
      event: 'embedding_pipeline.no_provider',
      action: 'embedding_skipped',
      message:
        'No embedding provider configured (OPENAI_API_KEY or OLLAMA_BASE_URL). ' +
        'Embedding rows will NOT be inserted. ' +
        'Configure a provider to enable semantic search.',
    }) + '\n'
  );
  return NO_PROVIDER;
}

const prisma: PrismaClient = getPrismaClient();

interface EmbeddingBatch {
  itemId: string;
  content: string;
  contentHash: string;
}

interface EmbeddingJobConfig {
  batchSize: number;
  maxRetries: number;
  retryDelayMs: number;
  modelName: string;
  dimensions: number;
}

const DEFAULT_CONFIG: EmbeddingJobConfig = {
  batchSize: 50,
  maxRetries: 3,
  retryDelayMs: 5000,
  modelName: 'text-embedding-3-small',
  dimensions: 1536,
};

/**
 * Compute content hash for change detection
 */
function computeContentHash(content: string): string {
  // Simple hash implementation - replace with crypto.createHash in production
  let hash = 0;
  for (let i = 0; i < content.length; i++) {
    const char = content.charCodeAt(i);
    hash = (hash << 5) - hash + char;
    hash = hash & hash; // Convert to 32-bit integer
  }
  return Math.abs(hash).toString(36);
}

/**
 * Extract content from item for embedding generation
 */
function extractItemContent(item: unknown): string {
  if (!isRecord(item)) {
    return '';
  }

  const parts: string[] = [];

  if (getString(item.title)) parts.push(`Title: ${getString(item.title)}`);
  if (getString(item.description)) {
    parts.push(`Description: ${getString(item.description)}`);
  }
  if (getString(item.acceptanceCriteria)) {
    parts.push(`Criteria: ${getString(item.acceptanceCriteria)}`);
  }
  if (getString(item.notes)) parts.push(`Notes: ${getString(item.notes)}`);
  if (Array.isArray(item.tags)) {
    parts.push(
      `Tags: ${getArray<unknown>(item.tags)
        .map((tag) => (isRecord(tag) && isRecord(tag.tag) ? getString(tag.tag.name) : undefined))
        .filter((tagName): tagName is string => typeof tagName === 'string')
        .join(', ')}`
    );
  }

  return parts.join('\n');
}

/**
 * Find items that need embedding updates
 */
async function findItemsNeedingEmbeddings(
  config: EmbeddingJobConfig,
  limit: number
): Promise<EmbeddingBatch[]> {
  // Get items without embeddings or with outdated content
  const items = await prisma.item.findMany({
    where: {
      OR: [
        // Items without any embeddings
        { itemEmbeddings: { none: {} } },
        // Items with embeddings but potentially outdated content
        {
          updatedAt: {
            gte: new Date(Date.now() - 7 * 24 * 60 * 60 * 1000), // Updated in last 7 days
          },
        },
      ],
    },
    include: {
      tags: true,
      itemEmbeddings: {
        where: {
          model: config.modelName,
        },
      },
    },
    take: limit,
  });

  const batches: EmbeddingBatch[] = [];

  for (const item of items) {
    const content = extractItemContent(item);
    const contentHash = computeContentHash(content);

    // Check if embedding exists and is up-to-date
    const existingEmbedding = item.itemEmbeddings.find(
      (embedding: { model: string; contentHash: string }) => embedding.model === config.modelName
    );

    if (!existingEmbedding || existingEmbedding.contentHash !== contentHash) {
      batches.push({
        itemId: item.id,
        content,
        contentHash,
      });
    }
  }

  return batches;
}

/**
 * Generate embeddings for a batch of items
 */
async function generateEmbeddingsBatch(
  provider: ILLMProvider,
  batch: EmbeddingBatch[],
  config: EmbeddingJobConfig,
  retryCount = 0
): Promise<void> {
  try {
    process.stderr.write(JSON.stringify({ level: 'info', event: 'embedding_pipeline.batch_start', itemCount: batch.length }) + '\n');

    const startTime = Date.now();

    // Generate embeddings in parallel
    const embeddingPromises = batch.map(async (item) => {
      try {
        const embedding = await provider.embed(item.content);

        // Store embedding in database
        await prisma.itemEmbedding.upsert({
          where: {
            itemId_model: {
              itemId: item.itemId,
              model: config.modelName,
            },
          },
          update: {
            embedding: Buffer.from(new Float32Array(embedding).buffer),
            contentHash: item.contentHash,
            dimensions: embedding.length,
            updatedAt: new Date(),
          },
          create: {
            itemId: item.itemId,
            model: config.modelName,
            embedding: Buffer.from(new Float32Array(embedding).buffer),
            contentHash: item.contentHash,
            dimensions: embedding.length,
          },
        });

        // Track metrics
        await trackEmbeddingMetric({
          agentName: 'embedding-pipeline',
          model: config.modelName,
          operation: 'embedding',
          tokensUsed: Math.ceil(item.content.length / 4), // Rough estimate
          latencyMs: Date.now() - startTime,
          costUSD: estimateEmbeddingCost(item.content),
          success: true,
        });

        return { itemId: item.itemId, success: true };
      } catch (error) {
        process.stderr.write(
          JSON.stringify({
            level: 'error',
            event: 'embedding_pipeline.item_error',
            itemId: item.itemId,
            error: error instanceof Error ? error.message : String(error),
          }) + '\n'
        );

        await trackEmbeddingMetric({
          agentName: 'embedding-pipeline',
          model: config.modelName,
          operation: 'embedding',
          tokensUsed: 0,
          latencyMs: Date.now() - startTime,
          costUSD: 0,
          success: false,
          errorMessage:
            error instanceof Error ? error.message : 'Unknown error',
        });

        return { itemId: item.itemId, success: false, error };
      }
    });

    const results = await Promise.all(embeddingPromises);

    const successCount = results.filter((r) => r.success).length;
    const failureCount = results.filter((r) => !r.success).length;

    process.stderr.write(
      JSON.stringify({
        level: 'info',
        event: 'embedding_pipeline.batch_complete',
        successCount,
        failureCount,
      }) + '\n'
    );

    // If we have failures and retries left, retry failed items
    if (failureCount > 0 && retryCount < config.maxRetries) {
      const failedBatch = batch.filter(
        (item) => !results.find((r) => r.itemId === item.itemId && r.success)
      );

      process.stderr.write(
        JSON.stringify({
          level: 'info',
          event: 'embedding_pipeline.batch_retry',
          failedCount: failedBatch.length,
          attempt: retryCount + 1,
        }) + '\n'
      );
      await new Promise((resolve) => setTimeout(resolve, config.retryDelayMs));
      await generateEmbeddingsBatch(
        provider,
        failedBatch,
        config,
        retryCount + 1
      );
    }
  } catch (error) {
    process.stderr.write(
      JSON.stringify({
        level: 'error',
        event: 'embedding_pipeline.batch_error',
        error: error instanceof Error ? error.message : String(error),
      }) + '\n'
    );
    throw error;
  }
}

/**
 * Estimate embedding cost (OpenAI pricing: $0.00002 per 1K tokens, Ollama: free)
 */
function estimateEmbeddingCost(content: string): number {
  const tokens = Math.ceil(content.length / 4);
  return (tokens / 1000) * 0.00002;
}

/**
 * Track embedding metric
 */
async function trackEmbeddingMetric(data: {
  agentName: string;
  model: string;
  operation: string;
  tokensUsed: number;
  latencyMs: number;
  costUSD: number;
  success: boolean;
  errorMessage?: string;
  userId?: string;
  sessionId?: string;
}): Promise<void> {
  try {
    await prisma.aIMetric.create({
      data: {
        agentName: data.agentName,
        model: data.model,
        operation: data.operation,
        tokensUsed: data.tokensUsed,
        latencyMs: data.latencyMs,
        costUSD: data.costUSD,
        success: data.success,
        errorMessage: data.errorMessage,
        userId: data.userId,
        sessionId: data.sessionId,
        timestamp: new Date(),
      },
    });
  } catch (error) {
    process.stderr.write(
      JSON.stringify({
        level: 'error',
        event: 'embedding_pipeline.metric_track_error',
        error: error instanceof Error ? error.message : String(error),
      }) + '\n'
    );
  }
}

/**
 * Main embedding pipeline job
 */
export async function runEmbeddingPipeline(
  config: Partial<EmbeddingJobConfig> = {}
): Promise<void> {
  const jobConfig: EmbeddingJobConfig = { ...DEFAULT_CONFIG, ...config };

  process.stderr.write(
    JSON.stringify({ level: 'info', event: 'embedding_pipeline.start', config: jobConfig }) + '\n'
  );

  try {
    // Resolve provider; null means no provider is configured \u2014 skip the run entirely
    // to avoid inserting zero-vector rows that would corrupt semantic search results.
    const provider = createConfiguredProvider();

    if (provider === null) {
      process.stderr.write(
        JSON.stringify({
          level: 'warn',
          event: 'embedding_pipeline.skipped',
          action: 'embedding_skipped',
          message:
            'Embedding pipeline skipped: no LLM provider configured. ' +
            'Set OPENAI_API_KEY or OLLAMA_BASE_URL to enable semantic search.',
        }) + '\n'
      );
      return;
    }

    process.stderr.write(
      JSON.stringify({ level: 'info', event: 'embedding_pipeline.provider', provider: provider.name }) +
        '\n'
    );

    // Find items needing embeddings
    const itemsNeedingEmbeddings = await findItemsNeedingEmbeddings(
      jobConfig,
      jobConfig.batchSize * 10 // Process up to 10 batches per run
    );

    if (itemsNeedingEmbeddings.length === 0) {
      process.stderr.write(
        JSON.stringify({ level: 'info', event: 'embedding_pipeline.nothing_to_do' }) + '\n'
      );
      return;
    }

    process.stderr.write(
      JSON.stringify({
        level: 'info',
        event: 'embedding_pipeline.items_found',
        count: itemsNeedingEmbeddings.length,
      }) + '\n'
    );

    // Process in batches
    for (
      let i = 0;
      i < itemsNeedingEmbeddings.length;
      i += jobConfig.batchSize
    ) {
      const batch = itemsNeedingEmbeddings.slice(i, i + jobConfig.batchSize);

      process.stderr.write(
        JSON.stringify({
          level: 'info',
          event: 'embedding_pipeline.batch',
          batchIndex: Math.floor(i / jobConfig.batchSize) + 1,
          batchSize: batch.length,
        }) + '\n'
      );

      await generateEmbeddingsBatch(provider, batch, jobConfig);

      // Small delay between batches to avoid rate limiting
      if (i + jobConfig.batchSize < itemsNeedingEmbeddings.length) {
        await new Promise((resolve) => setTimeout(resolve, 1000));
      }
    }

    process.stderr.write(JSON.stringify({ level: 'info', event: 'embedding_pipeline.complete' }) + '\n');
  } catch (error) {
    process.stderr.write(
      JSON.stringify({
        level: 'error',
        event: 'embedding_pipeline.fatal_error',
        error: error instanceof Error ? error.message : String(error),
      }) + '\n'
    );
    throw error;
  } finally {
    await prisma.$disconnect();
  }
}

/**
 * Scheduled job entry point (run via cron or queue)
 */
export async function scheduleEmbeddingPipeline(): Promise<void> {
  // Run every 15 minutes with default config
  const interval = 15 * 60 * 1000;

  process.stderr.write(JSON.stringify({ level: 'info', event: 'embedding_pipeline.scheduler_started', intervalMs: interval }) + '\n');

  const runJob = async () => {
    try {
      await runEmbeddingPipeline();
    } catch (error) {
      process.stderr.write(
        JSON.stringify({
          level: 'error',
          event: 'embedding_pipeline.job_error',
          error: error instanceof Error ? error.message : String(error),
        }) + '\n'
      );
    }
  };

  // Run immediately on start
  await runJob();

  // Then run on interval
  setInterval(runJob, interval);
}

// CLI entry point
if (require.main === module) {
  const args = process.argv.slice(2);
  const mode = args[0] || 'once';

  void (async () => {
    try {
      if (mode === 'schedule') {
        await scheduleEmbeddingPipeline();
      } else {
        await runEmbeddingPipeline();
      }
    } catch (error) {
      process.stderr.write(
        JSON.stringify({
          level: 'error',
          event: mode === 'schedule' ? 'embedding_pipeline.scheduler_failed' : 'embedding_pipeline.job_failed',
          error: error instanceof Error ? error.message : String(error),
        }) + '\n'
      );
      process.exit(1);
    }
  })();
}
