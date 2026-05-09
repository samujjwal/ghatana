/**
 * Semantic Evidence Search Service
 *
 * Provides semantic search for evidence and claims using pgvector embeddings.
 * Enables finding semantically similar evidence for fact-checking and validation.
 *
 * @doc.type module
 * @doc.purpose Semantic search for evidence using pgvector
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@tutorputor/core/db';
import type { Logger } from 'pino';

// ============================================================================
// Types
// ============================================================================

export interface EvidenceEmbedding {
  id: string;
  evidenceId: string;
  claimRef: string;
  text: string;
  vector: number[];
  model: string;
  dimensions: number;
  createdAt: Date;
}

export interface EvidenceSearchOptions {
  claimRef: string;
  domain: string;
  gradeBand?: string;
  maxResults?: number;
  threshold?: number;
}

export interface EvidenceSearchResult {
  evidenceId: string;
  claimRef: string;
  text: string;
  similarityScore: number;
  sourceUrl?: string;
  sourceTitle?: string;
  sourceType?: string;
}

export interface EmbeddingConfig {
  model: string;
  dimensions: number;
  apiKey?: string;
  endpoint?: string;
}

// ============================================================================
// Embedding Abstraction
// ============================================================================

/**
 * Embedding provider interface for different embedding models.
 */
export interface EmbeddingProvider {
  generateEmbedding(text: string): Promise<number[]>;
  getModel(): string;
  getDimensions(): number;
}

/**
 * OpenAI embedding provider.
 */
export class OpenAIEmbeddingProvider implements EmbeddingProvider {
  constructor(
    private readonly config: { apiKey: string; model?: string }
  ) {}

  async generateEmbedding(text: string): Promise<number[]> {
    const response = await fetch('https://api.openai.com/v1/embeddings', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${this.config.apiKey}`,
      },
      body: JSON.stringify({
        model: this.config.model || 'text-embedding-ada-002',
        input: text,
      }),
    });

    if (!response.ok) {
      throw new Error(`OpenAI API error: ${response.statusText}`);
    }

    const data = await response.json() as { data?: Array<{ embedding: number[] }> };
    return data.data?.[0]?.embedding ?? [];
  }

  getModel(): string {
    return this.config.model || 'text-embedding-ada-002';
  }

  getDimensions(): number {
    return 1536; // ada-002 dimensions
  }
}

/**
 * Local embedding provider (mock for development).
 */
export class LocalEmbeddingProvider implements EmbeddingProvider {
  async generateEmbedding(text: string): Promise<number[]> {
    // Deterministic pseudo-embedding for development
    const hash = this.hashString(text);
    const dimensions = 1536;
    const vector = new Array(dimensions);
    
    for (let i = 0; i < dimensions; i++) {
      vector[i] = ((hash * (i + 1)) % 10000) / 10000;
    }
    
    return vector;
  }

  getModel(): string {
    return 'local-mock-v1';
  }

  getDimensions(): number {
    return 1536;
  }

  private hashString(str: string): number {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
      const char = str.charCodeAt(i);
      hash = ((hash << 5) - hash) + char;
      hash = hash & hash;
    }
    return Math.abs(hash);
  }
}

// ============================================================================
// Semantic Evidence Search Service
// ============================================================================

export class SemanticEvidenceSearchService {
  private embeddingProvider: EmbeddingProvider;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    config?: EmbeddingConfig
  ) {
    // Initialize embedding provider based on config
    if (config?.apiKey) {
      this.embeddingProvider = new OpenAIEmbeddingProvider({ apiKey: config.apiKey, model: config.model });
    } else {
      this.logger.warn('No API key provided, using local mock embedding provider');
      this.embeddingProvider = new LocalEmbeddingProvider();
    }
  }

  /**
   * Generate and store embedding for evidence.
   */
  async indexEvidence(
    evidenceId: string,
    claimRef: string,
    text: string
  ): Promise<void> {
    try {
      const embedding = await this.embeddingProvider.generateEmbedding(text);

      // Store in database (using pgvector if available, otherwise store as JSON)
      // @ts-ignore - Prisma client needs regeneration after schema update
      await this.prisma.evidenceEmbedding.create({
        data: {
          evidenceId,
          claimRef,
          text,
          vector: embedding, // Store as JSON for now, migrate to pgvector
          model: this.embeddingProvider.getModel(),
          dimensions: this.embeddingProvider.getDimensions(),
        },
      });

      this.logger.info(
        { evidenceId, claimRef, model: this.embeddingProvider.getModel() },
        'Evidence indexed for semantic search'
      );
    } catch (error) {
      this.logger.error(
        { error, evidenceId, claimRef },
        'Failed to index evidence for semantic search'
      );
      throw error;
    }
  }

  /**
   * Search for semantically similar evidence.
   */
  async searchEvidence(
    query: string,
    options: EvidenceSearchOptions
  ): Promise<EvidenceSearchResult[]> {
    try {
      const { claimRef, domain, maxResults = 10, threshold = 0.7 } = options;

      // Generate query embedding
      const queryEmbedding = await this.embeddingProvider.generateEmbedding(query);

      // Search using pgvector similarity if available
      // For now, use cosine similarity calculation in application code
      // @ts-ignore - Prisma client needs regeneration after schema update
      const indexedEvidence = await this.prisma.evidenceEmbedding.findMany({
        where: {
          claimRef,
        },
        take: 100, // Fetch more for similarity calculation
      });

      // Calculate cosine similarity
      const results: EvidenceSearchResult[] = indexedEvidence
        .map((evidence: { evidenceId: string; claimRef: string; text: string; vector: unknown }) => {
          const evidenceVector = JSON.parse(evidence.vector as string) as number[];
          const similarity = this.cosineSimilarity(queryEmbedding, evidenceVector);

          return {
            evidenceId: evidence.evidenceId,
            claimRef: evidence.claimRef,
            text: evidence.text,
            similarityScore: similarity,
          };
        })
        .filter((result: EvidenceSearchResult) => result.similarityScore >= threshold)
        .sort((a: EvidenceSearchResult, b: EvidenceSearchResult) => b.similarityScore - a.similarityScore)
        .slice(0, maxResults);

      this.logger.info(
        { claimRef, query, resultsCount: results.length },
        'Semantic evidence search completed'
      );

      return results;
    } catch (error) {
      this.logger.error(
        { error, query, options },
        'Semantic evidence search failed'
      );
      return [];
    }
  }

  /**
   * Batch index multiple evidence items.
   */
  async batchIndexEvidence(
    items: Array<{ evidenceId: string; claimRef: string; text: string }>
  ): Promise<void> {
    const batchSize = 10;
    for (let i = 0; i < items.length; i += batchSize) {
      const batch = items.slice(i, i + batchSize);
      await Promise.all(
        batch.map(item => this.indexEvidence(item.evidenceId, item.claimRef, item.text))
      );
      this.logger.info(
        { batchStart: i, batchSize: batch.length },
        'Batch indexing progress'
      );
    }
  }

  /**
   * Re-index evidence (update embeddings).
   */
  async reindexEvidence(evidenceId: string): Promise<void> {
    // @ts-ignore - Prisma client needs regeneration after schema update
    const existing = await this.prisma.evidenceEmbedding.findUnique({
      where: { evidenceId },
    });

    if (!existing) {
      throw new Error(`Evidence ${evidenceId} not found`);
    }

    await this.indexEvidence(existing.evidenceId, existing.claimRef, existing.text);
  }

  /**
   * Delete evidence from index.
   */
  async deleteEvidence(evidenceId: string): Promise<void> {
    // @ts-ignore - Prisma client needs regeneration after schema update
    await this.prisma.evidenceEmbedding.delete({
      where: { evidenceId },
    });

    this.logger.info({ evidenceId }, 'Evidence deleted from semantic index');
  }

  /**
   * Calculate cosine similarity between two vectors.
   */
  private cosineSimilarity(a: number[], b: number[]): number {
    if (a.length !== b.length) {
      throw new Error('Vector dimensions must match');
    }

    let dotProduct = 0;
    let normA = 0;
    let normB = 0;

    for (let i = 0; i < a.length; i++) {
      const ai = a[i] ?? 0;
      const bi = b[i] ?? 0;
      dotProduct += ai * bi;
      normA += ai * ai;
      normB += bi * bi;
    }

    if (normA === 0 || normB === 0) {
      return 0;
    }

    return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
  }

  /**
   * Get embedding provider info.
   */
  getProviderInfo(): { model: string; dimensions: number } {
    return {
      model: this.embeddingProvider.getModel(),
      dimensions: this.embeddingProvider.getDimensions(),
    };
  }
}
