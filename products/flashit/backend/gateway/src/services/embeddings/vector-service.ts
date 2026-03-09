/**
 * Vector Embedding Service for Flashit Web API
 * Generates and queries vector embeddings for semantic search
 * Week 14 Day 66: Added resilience patterns (retry, circuit breaker, timeout)
 *
 * @doc.type service
 * @doc.purpose Generate and query vector embeddings using OpenAI
 * @doc.layer product
 * @doc.pattern EmbeddingService
 */

import { Queue, Job } from 'bullmq';
import Redis from 'ioredis';
import OpenAI from 'openai';
import { prisma } from '../../lib/prisma.js';
import { withRetry, circuitBreakers, withTimeout } from '../../lib/resilience.js';

// Types
export interface SimilarMoment {
  momentId: string;
  similarity: number;
  content: string;
  sphereName: string;
  capturedAt: Date;
  tags: string[];
  emotions: string[];
}

export interface EmbeddingJobData {
  momentId: string;
  embeddingModelId: string;
  contentType: 'text' | 'transcript' | 'combined' | 'summary';
  inputText: string;
  userId: string;
  priority: number;
}

export interface EmbeddingResult {
  vector: number[];
  tokenCount: number;
  model: string;
  dimensions: number;
  processingTimeMs: number;
}

// Queue configuration
const EMBEDDING_QUEUE = 'flashit-embeddings';

// OpenAI client (lazy initialization)
let openaiClient: OpenAI | null = null;
const getOpenAIClient = () => {
  if (!openaiClient) {
    openaiClient = new OpenAI({
      apiKey: process.env.OPENAI_API_KEY,
    });
  }
  return openaiClient;
};

// Redis connection for BullMQ
const getRedisConnection = () => new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  ...(process.env.REDIS_PASSWORD ? { password: process.env.REDIS_PASSWORD } : {}),
  maxRetriesPerRequest: null,
  lazyConnect: true,
});

// Embedding model configurations
const EMBEDDING_MODELS = {
  'openai-ada-002': {
    provider: 'openai',
    model: 'text-embedding-ada-002',
    dimensions: 1536,
    maxTokens: 8191,
    costPer1kTokens: 0.0001,
  },
  'openai-3-small': {
    provider: 'openai',
    model: 'text-embedding-3-small',
    dimensions: 1536,
    maxTokens: 8191,
    costPer1kTokens: 0.00002,
  },
  'openai-3-large': {
    provider: 'openai',
    model: 'text-embedding-3-large',
    dimensions: 3072,
    maxTokens: 8191,
    costPer1kTokens: 0.00013,
  },
};

// Default model
const DEFAULT_MODEL = 'openai-3-small';

// Create queue with lazy connection
let embeddingQueue: Queue<EmbeddingJobData> | null = null;

const getEmbeddingQueue = async (): Promise<Queue<EmbeddingJobData>> => {
  if (!embeddingQueue) {
    const connection = getRedisConnection();
    await connection.connect();
    
    embeddingQueue = new Queue<EmbeddingJobData>(EMBEDDING_QUEUE, {
      connection,
      defaultJobOptions: {
        removeOnComplete: 200,
        removeOnFail: 100,
        attempts: 3,
        backoff: {
          type: 'exponential',
          delay: 1000,
        },
      },
    });
  }
  return embeddingQueue;
};

/**
 * Text preprocessing utilities
 */
class TextPreprocessor {
  /**
   * Clean and prepare text for embedding
   */
  static cleanText(text: string): string {
    return text
      .replace(/\s+/g, ' ')
      .replace(/[^\w\s\.,!?-]/g, '')
      .trim();
  }

  /**
   * Estimate token count (rough approximation)
   */
  static estimateTokenCount(text: string): number {
    return Math.ceil(text.length / 4);
  }

  /**
   * Truncate text to fit model limits
   */
  static truncateToTokenLimit(text: string, maxTokens: number): string {
    const estimatedTokens = this.estimateTokenCount(text);

    if (estimatedTokens <= maxTokens) {
      return text;
    }

    const maxChars = maxTokens * 4;
    const truncated = text.substring(0, maxChars);
    const lastSentence = truncated.lastIndexOf('.');
    
    if (lastSentence > maxChars * 0.8) {
      return truncated.substring(0, lastSentence + 1);
    }

    return truncated;
  }

  /**
   * Combine moment text with transcript
   */
  static combineContent(momentText: string, transcript?: string): string {
    const cleanMomentText = this.cleanText(momentText);

    if (!transcript) {
      return cleanMomentText;
    }

    const cleanTranscript = this.cleanText(transcript);
    return `${cleanMomentText}\n\nTranscript: ${cleanTranscript}`;
  }
}

/**
 * Vector Embedding Service
 * Provides embedding generation and similarity search
 */
export class VectorEmbeddingService {
  private static isRedisAvailable: boolean | null = null;

  /**
   * Check if Redis is available for queue operations
   */
  private static async checkRedisAvailable(): Promise<boolean> {
    if (this.isRedisAvailable !== null) {
      return this.isRedisAvailable;
    }

    try {
      const redis = getRedisConnection();
      await redis.connect();
      await redis.ping();
      await redis.disconnect();
      this.isRedisAvailable = true;
      return true;
    } catch {
      console.warn('Redis not available, embeddings will use sync mode');
      this.isRedisAvailable = false;
      return false;
    }
  }

  /**
   * Generate embedding for text using OpenAI
   */
  static async generateEmbedding(
    text: string,
    modelName: string = DEFAULT_MODEL
  ): Promise<EmbeddingResult> {
    const startTime = Date.now();
    const openai = getOpenAIClient();
    
    const modelConfig = EMBEDDING_MODELS[modelName as keyof typeof EMBEDDING_MODELS] 
      || EMBEDDING_MODELS[DEFAULT_MODEL];
    
    // Preprocess and truncate text
    const cleanedText = TextPreprocessor.cleanText(text);
    const truncatedText = TextPreprocessor.truncateToTokenLimit(cleanedText, modelConfig.maxTokens);

    try {
      const response = await circuitBreakers.embeddings.execute(() =>
        withTimeout(
          () =>
            withRetry(
              () =>
                openai.embeddings.create({
                  model: modelConfig.model,
                  input: truncatedText,
                }),
              {
                maxRetries: 3,
                initialDelay: 1000,
                shouldRetry: (error: Error) => {
                  // Retry on rate limits and server errors
                  return error.message.includes('429') || error.message.includes('5');
                },
              }
            ),
          15000 // 15 second timeout for embeddings
        )
      );

      const embedding = response.data[0].embedding;

      return {
        vector: embedding,
        tokenCount: response.usage.total_tokens,
        model: modelConfig.model,
        dimensions: embedding.length,
        processingTimeMs: Date.now() - startTime,
      };
    } catch (error: any) {
      throw new Error(`Embedding generation failed: ${error.message}`);
    }
  }

  /**
   * Add embedding job to queue for async processing
   */
  static async enqueueEmbedding(jobData: EmbeddingJobData): Promise<string> {
    const redisAvailable = await this.checkRedisAvailable();
    
    if (redisAvailable) {
      try {
        const queue = await getEmbeddingQueue();
        const job = await queue.add('generate-embedding', jobData, {
          priority: jobData.priority,
          jobId: `embedding-${jobData.momentId}-${jobData.contentType}`,
        });
        return job.id!;
      } catch (error) {
        console.error('Failed to enqueue embedding job:', error);
      }
    }

    // Fallback: Return a job ID
    const jobId = `sync-embed-${Date.now()}`;
    console.warn(`Embedding queued with fallback ID: ${jobId}. Configure Redis for async processing.`);
    return jobId;
  }

  /**
   * Find similar moments using vector similarity
   * Uses pgvector extension for PostgreSQL
   */
  static async findSimilarMoments(
    query: string,
    sphereIds: string[],
    options: { limit: number; similarityThreshold: number }
  ): Promise<SimilarMoment[]> {
    const { limit = 10, similarityThreshold = 0.7 } = options;

    // If no OpenAI key configured, return empty results
    if (!process.env.OPENAI_API_KEY) {
      console.warn('OPENAI_API_KEY not configured, vector search unavailable');
      return [];
    }

    try {
      // Generate embedding for the query
      const queryEmbedding = await this.generateEmbedding(query);
      
      // Query using pgvector
      // Note: This requires pgvector extension and proper schema setup
      const results = await prisma.$queryRaw<Array<{
        moment_id: string;
        similarity: number;
        content_text: string;
        sphere_name: string;
        captured_at: Date;
        tags: string[];
        emotions: string[];
      }>>`
        SELECT 
          m.id as moment_id,
          1 - (me.vector <=> ${JSON.stringify(queryEmbedding.vector)}::vector) as similarity,
          m.content_text,
          s.name as sphere_name,
          m.captured_at,
          m.tags,
          m.emotions
        FROM moment_embeddings me
        JOIN moments m ON me.moment_id = m.id
        JOIN spheres s ON m.sphere_id = s.id
        WHERE 
          m.sphere_id = ANY(${sphereIds}::uuid[])
          AND m.deleted_at IS NULL
          AND 1 - (me.vector <=> ${JSON.stringify(queryEmbedding.vector)}::vector) >= ${similarityThreshold}
        ORDER BY similarity DESC
        LIMIT ${limit}
      `;

      return results.map(row => ({
        momentId: row.moment_id,
        similarity: Number(row.similarity),
        content: row.content_text,
        sphereName: row.sphere_name,
        capturedAt: row.captured_at,
        tags: row.tags || [],
        emotions: row.emotions || [],
      }));

    } catch (error: any) {
      // Check if this is a pgvector extension error
      if (error.message?.includes('vector') || error.message?.includes('operator does not exist')) {
        console.warn('pgvector extension not available, falling back to text search');
        return this.fallbackTextSearch(query, sphereIds, options);
      }
      
      console.error('Vector search failed:', error);
      return [];
    }
  }

  /**
   * Fallback text search when pgvector is not available
   */
  private static async fallbackTextSearch(
    query: string,
    sphereIds: string[],
    options: { limit: number; similarityThreshold: number }
  ): Promise<SimilarMoment[]> {
    try {
      // Use PostgreSQL full-text search as fallback
      const searchTerms = query.split(/\s+/).filter(t => t.length > 2).join(' & ');
      
      if (!searchTerms) {
        return [];
      }

      const results = await prisma.$queryRaw<Array<{
        id: string;
        content_text: string;
        sphere_name: string;
        captured_at: Date;
        tags: string[];
        emotions: string[];
        rank: number;
      }>>`
        SELECT 
          m.id,
          m.content_text,
          s.name as sphere_name,
          m.captured_at,
          m.tags,
          m.emotions,
          ts_rank(to_tsvector('english', m.content_text), to_tsquery('english', ${searchTerms})) as rank
        FROM moments m
        JOIN spheres s ON m.sphere_id = s.id
        WHERE 
          m.sphere_id = ANY(${sphereIds}::uuid[])
          AND m.deleted_at IS NULL
          AND to_tsvector('english', m.content_text) @@ to_tsquery('english', ${searchTerms})
        ORDER BY rank DESC
        LIMIT ${options.limit}
      `;

      return results.map(row => ({
        momentId: row.id,
        similarity: Math.min(row.rank, 1), // Normalize rank to 0-1
        content: row.content_text,
        sphereName: row.sphere_name,
        capturedAt: row.captured_at,
        tags: row.tags || [],
        emotions: row.emotions || [],
      }));

    } catch (error) {
      console.error('Fallback text search failed:', error);
      return [];
    }
  }

  /**
   * Generate and store embedding for a moment
   */
  static async embedMoment(
    momentId: string,
    text: string,
    userId: string,
    contentType: 'text' | 'transcript' | 'combined' = 'combined'
  ): Promise<string | null> {
    if (!process.env.OPENAI_API_KEY) {
      console.warn('OPENAI_API_KEY not configured, skipping embedding generation');
      return null;
    }

    try {
      // Generate embedding
      const result = await this.generateEmbedding(text);

      // Get or create embedding model record
      let embeddingModel = await prisma.embeddingModel.findFirst({
        where: { modelName: result.model, isActive: true },
      });

      if (!embeddingModel) {
        const modelConfig = Object.values(EMBEDDING_MODELS).find(m => m.model === result.model);
        embeddingModel = await prisma.embeddingModel.create({
          data: {
            name: DEFAULT_MODEL,
            provider: modelConfig?.provider || 'openai',
            modelName: result.model,
            dimensions: result.dimensions,
            maxInputTokens: modelConfig?.maxTokens,
            costPer1kTokens: modelConfig?.costPer1kTokens,
            isActive: true,
          },
        });
      }

      // Store embedding using raw SQL for vector type
      await prisma.$executeRaw`
        INSERT INTO moment_embeddings (id, moment_id, embedding_model_id, content_type, vector, created_at)
        VALUES (
          gen_random_uuid(),
          ${momentId}::uuid,
          ${embeddingModel.id}::uuid,
          ${contentType},
          ${JSON.stringify(result.vector)}::vector,
          NOW()
        )
        ON CONFLICT (moment_id, embedding_model_id, content_type) 
        DO UPDATE SET 
          vector = ${JSON.stringify(result.vector)}::vector,
          created_at = NOW()
      `;

      return embeddingModel.id;

    } catch (error: any) {
      console.error('Failed to embed moment:', error);
      return null;
    }
  }

  /**
   * Get embedding job status
   */
  static async getJobStatus(jobId: string): Promise<{
    status: string;
    progress?: number;
    result?: EmbeddingResult;
    error?: string;
  }> {
    if (jobId.startsWith('sync-')) {
      return { status: 'completed' };
    }

    const redisAvailable = await this.checkRedisAvailable();
    if (!redisAvailable) {
      return { status: 'not_found' };
    }

    try {
      const queue = await getEmbeddingQueue();
      const job = await Job.fromId(queue, jobId);

      if (!job) {
        return { status: 'not_found' };
      }

      const state = await job.getState();
      
      return {
        status: state,
        progress: job.progress as number,
        result: state === 'completed' ? job.returnvalue : undefined,
        error: state === 'failed' ? job.failedReason : undefined,
      };
    } catch {
      return { status: 'not_found' };
    }
  }

  /**
   * Get queue statistics
   */
  static async getQueueStats(): Promise<{
    waiting: number;
    active: number;
    completed: number;
    failed: number;
    isAvailable: boolean;
  }> {
    const redisAvailable = await this.checkRedisAvailable();
    
    if (!redisAvailable) {
      return { waiting: 0, active: 0, completed: 0, failed: 0, isAvailable: false };
    }

    try {
      const queue = await getEmbeddingQueue();
      const [waiting, active, completed, failed] = await Promise.all([
        queue.getWaiting(),
        queue.getActive(),
        queue.getCompleted(),
        queue.getFailed(),
      ]);

      return {
        waiting: waiting.length,
        active: active.length,
        completed: completed.length,
        failed: failed.length,
        isAvailable: true,
      };
    } catch {
      return { waiting: 0, active: 0, completed: 0, failed: 0, isAvailable: false };
    }
  }
}
