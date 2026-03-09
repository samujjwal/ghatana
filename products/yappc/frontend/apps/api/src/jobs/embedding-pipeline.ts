/**
 * Embedding Pipeline Background Job
 * @doc.type service
 * @doc.purpose Generate and update vector embeddings for semantic search
 * @doc.layer product
 * @doc.pattern Background Job
 */

import { createProviderFactory, ILLMProvider } from '../stubs/ai/providers';

import { getPrismaClient, type PrismaClient } from '../database/client';

/**
 * Create embedding provider based on environment configuration.
 *
 * - If OPENAI_API_KEY is set → uses OpenAI text-embedding-3-small via API
 * - If OLLAMA_BASE_URL is set → uses local Ollama embedding model
 * - Otherwise → falls back to stub provider (zero vectors)
 *
 * The stub provider is intentionally kept as a safe default so that
 * the pipeline can run in CI/dev without external dependencies.
 */
function createConfiguredProvider(): ILLMProvider {
    const openaiKey = process.env.OPENAI_API_KEY;
    const ollamaUrl = process.env.OLLAMA_BASE_URL;

    if (openaiKey) {
        console.log('[EmbeddingPipeline] Using OpenAI embedding provider');
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
                        model: process.env.OPENAI_EMBEDDING_MODEL || 'text-embedding-3-small',
                        input: text,
                    }),
                });
                if (!response.ok) {
                    throw new Error(`OpenAI embedding API error: ${response.status} ${await response.text()}`);
                }
                const json = (await response.json()) as { data: Array<{ embedding: number[] }> };
                return json.data[0].embedding;
            },
            async generateEmbedding(text: string): Promise<number[]> {
                return this.embed(text);
            },
        } as ILLMProvider;
    }

    if (ollamaUrl) {
        const model = process.env.OLLAMA_EMBEDDING_MODEL || 'nomic-embed-text';
        console.log(`[EmbeddingPipeline] Using Ollama embedding provider (${model})`);
        return {
            name: 'ollama',
            async embed(text: string): Promise<number[]> {
                const response = await fetch(`${ollamaUrl}/api/embeddings`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({ model, prompt: text }),
                });
                if (!response.ok) {
                    throw new Error(`Ollama embedding API error: ${response.status} ${await response.text()}`);
                }
                const json = (await response.json()) as { embedding: number[] };
                return json.embedding;
            },
            async generateEmbedding(text: string): Promise<number[]> {
                return this.embed(text);
            },
        } as ILLMProvider;
    }

    // Fallback: stub provider (safe for dev/CI)
    console.warn('[EmbeddingPipeline] No AI provider configured. Using stub (zero vectors).');
    console.warn('[EmbeddingPipeline] Set OPENAI_API_KEY or OLLAMA_BASE_URL for real embeddings.');
    const factory = createProviderFactory();
    return factory.getDefaultProvider();
}

const prisma: PrismaClient = new Proxy({} as PrismaClient, {
    get(_target, property) {
        return (getPrismaClient() as unknown)[property];
    },
});

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
        hash = ((hash << 5) - hash) + char;
        hash = hash & hash; // Convert to 32-bit integer
    }
    return Math.abs(hash).toString(36);
}

/**
 * Extract content from item for embedding generation
 */
function extractItemContent(item: unknown): string {
    const parts: string[] = [];

    if (item.title) parts.push(`Title: ${item.title}`);
    if (item.description) parts.push(`Description: ${item.description}`);
    if (item.acceptanceCriteria) parts.push(`Criteria: ${item.acceptanceCriteria}`);
    if (item.notes) parts.push(`Notes: ${item.notes}`);
    if (item.tags && Array.isArray(item.tags)) {
        parts.push(`Tags: ${item.tags.map((t: unknown) => t.tag?.name).filter(Boolean).join(', ')}`);
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
            (emb: unknown) => emb.model === config.modelName
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
        console.log(`[EmbeddingPipeline] Processing batch of ${batch.length} items`);

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
                console.error(`[EmbeddingPipeline] Error embedding item ${item.itemId}:`, error);

                await trackEmbeddingMetric({
                    agentName: 'embedding-pipeline',
                    model: config.modelName,
                    operation: 'embedding',
                    tokensUsed: 0,
                    latencyMs: Date.now() - startTime,
                    costUSD: 0,
                    success: false,
                    errorMessage: error instanceof Error ? error.message : 'Unknown error',
                });

                return { itemId: item.itemId, success: false, error };
            }
        });

        const results = await Promise.all(embeddingPromises);

        const successCount = results.filter((r) => r.success).length;
        const failureCount = results.filter((r) => !r.success).length;

        console.log(`[EmbeddingPipeline] Batch complete: ${successCount} success, ${failureCount} failures`);

        // If we have failures and retries left, retry failed items
        if (failureCount > 0 && retryCount < config.maxRetries) {
            const failedBatch = batch.filter(
                (item) => !results.find((r) => r.itemId === item.itemId && r.success)
            );

            console.log(`[EmbeddingPipeline] Retrying ${failedBatch.length} failed items (attempt ${retryCount + 1})`);
            await new Promise((resolve) => setTimeout(resolve, config.retryDelayMs));
            await generateEmbeddingsBatch(provider, failedBatch, config, retryCount + 1);
        }
    } catch (error) {
        console.error('[EmbeddingPipeline] Batch processing error:', error);
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
        console.error('[EmbeddingPipeline] Failed to track metric:', error);
    }
}

/**
 * Main embedding pipeline job
 */
export async function runEmbeddingPipeline(
    config: Partial<EmbeddingJobConfig> = {}
): Promise<void> {
    const jobConfig: EmbeddingJobConfig = { ...DEFAULT_CONFIG, ...config };

    console.log('[EmbeddingPipeline] Starting embedding pipeline job');
    console.log('[EmbeddingPipeline] Config:', jobConfig);

    try {
        // Create provider from environment configuration (OpenAI / Ollama / stub)
        const provider = createConfiguredProvider();

        console.log(`[EmbeddingPipeline] Using provider: ${provider.name}`);

        // Find items needing embeddings
        const itemsNeedingEmbeddings = await findItemsNeedingEmbeddings(
            jobConfig,
            jobConfig.batchSize * 10 // Process up to 10 batches per run
        );

        if (itemsNeedingEmbeddings.length === 0) {
            console.log('[EmbeddingPipeline] No items need embedding updates');
            return;
        }

        console.log(`[EmbeddingPipeline] Found ${itemsNeedingEmbeddings.length} items needing embeddings`);

        // Process in batches
        for (let i = 0; i < itemsNeedingEmbeddings.length; i += jobConfig.batchSize) {
            const batch = itemsNeedingEmbeddings.slice(i, i + jobConfig.batchSize);

            console.log(`[EmbeddingPipeline] Processing batch ${Math.floor(i / jobConfig.batchSize) + 1}`);

            await generateEmbeddingsBatch(provider, batch, jobConfig);

            // Small delay between batches to avoid rate limiting
            if (i + jobConfig.batchSize < itemsNeedingEmbeddings.length) {
                await new Promise((resolve) => setTimeout(resolve, 1000));
            }
        }

        console.log('[EmbeddingPipeline] Embedding pipeline job complete');
    } catch (error) {
        console.error('[EmbeddingPipeline] Fatal error:', error);
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

    console.log(`[EmbeddingPipeline] Scheduler started (interval: ${interval}ms)`);

    const runJob = async () => {
        try {
            await runEmbeddingPipeline();
        } catch (error) {
            console.error('[EmbeddingPipeline] Job execution failed:', error);
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

    if (mode === 'schedule') {
        scheduleEmbeddingPipeline().catch((error) => {
            console.error('[EmbeddingPipeline] Scheduler failed:', error);
            process.exit(1);
        });
    } else {
        runEmbeddingPipeline().catch((error) => {
            console.error('[EmbeddingPipeline] Job failed:', error);
            process.exit(1);
        });
    }
}
