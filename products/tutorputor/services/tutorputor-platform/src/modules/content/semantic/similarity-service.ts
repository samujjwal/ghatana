/**
 * Content Similarity Search Service
 *
 * Provides semantic similarity detection for content using vector embeddings.
 * Enables duplicate detection and content similarity warnings during generation.
 *
 * @doc.type class
 * @doc.purpose Detect similar content using vector embeddings
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import type { Logger } from "pino";

export interface SimilaritySearchOptions {
  tenantId: string;
  query: string;
  threshold?: number; // 0-1 similarity threshold
  maxResults?: number;
  excludeAssetId?: string | undefined; // Exclude self from results
  domain?: string | undefined; // Optional domain filter
}

export interface ContentSimilarityMatch {
  assetId: string;
  assetTitle: string;
  assetType: string;
  chunkText: string;
  similarityScore: number; // 0-1
  matchReason: string;
  chunkSource: string;
}

export interface DuplicateCheckResult {
  hasDuplicates: boolean;
  matches: ContentSimilarityMatch[];
  highestSimilarity: number;
  recommendedAction: "proceed" | "review" | "block";
}

/**
 * Service for semantic content similarity search
 */
export class ContentSimilarityService {
  // Default threshold for considering content as duplicate
  private readonly DEFAULT_DUPLICATE_THRESHOLD = 0.85;
  private readonly DEFAULT_SIMILARITY_THRESHOLD = 0.70;
  private readonly DEFAULT_MAX_RESULTS = 5;

  constructor(
    private readonly prisma: PrismaClient,
    private readonly logger: Logger,
    private readonly embeddingClient?: {
      generateEmbedding: (text: string) => Promise<number[]>;
    },
  ) {}

  /**
   * Check for duplicate/similar content during generation
   */
  async checkForDuplicates(
    title: string,
    description: string,
    tenantId: string,
    options?: {
      threshold?: number;
      excludeAssetId?: string;
      domain?: string;
    },
  ): Promise<DuplicateCheckResult> {
    const combinedText = `${title} ${description}`.trim();

    if (!combinedText) {
      return {
        hasDuplicates: false,
        matches: [],
        highestSimilarity: 0,
        recommendedAction: "proceed",
      };
    }

    const matches = await this.findSimilarContent({
      tenantId,
      query: combinedText,
      threshold: options?.threshold ?? this.DEFAULT_DUPLICATE_THRESHOLD,
      maxResults: 3,
      excludeAssetId: options?.excludeAssetId,
      domain: options?.domain,
    });

    const highestSimilarity = matches.length > 0 ? matches[0].similarityScore : 0;

    // Determine recommended action based on similarity
    let recommendedAction: "proceed" | "review" | "block" = "proceed";
    if (highestSimilarity >= 0.95) {
      recommendedAction = "block";
    } else if (highestSimilarity >= this.DEFAULT_DUPLICATE_THRESHOLD) {
      recommendedAction = "review";
    }

    this.logger.info(
      {
        tenantId,
        queryLength: combinedText.length,
        matchCount: matches.length,
        highestSimilarity,
        recommendedAction,
      },
      "Duplicate check completed",
    );

    return {
      hasDuplicates: matches.length > 0,
      matches,
      highestSimilarity,
      recommendedAction,
    };
  }

  /**
   * Find semantically similar content
   */
  async findSimilarContent(options: SimilaritySearchOptions): Promise<ContentSimilarityMatch[]> {
    const {
      tenantId,
      query,
      threshold = this.DEFAULT_SIMILARITY_THRESHOLD,
      maxResults = this.DEFAULT_MAX_RESULTS,
      excludeAssetId,
      domain,
    } = options;

    // Generate embedding for query
    const queryVector = await this.generateQueryEmbedding(query);

    // Search for similar embeddings
    const similarChunks = await this.searchSimilarEmbeddings({
      tenantId,
      queryVector,
      threshold,
      maxResults: maxResults * 2, // Fetch more for deduplication
      excludeAssetId,
      domain,
    });

    // Deduplicate by asset and format results
    const matches = this.deduplicateAndFormatResults(similarChunks, maxResults);

    return matches;
  }

  /**
   * Get similarity threshold configuration
   * Note: Thresholds are currently static defaults
   */
  async getSimilarityThreshold(_tenantId: string, contentType?: string): Promise<number> {
    // Type-specific thresholds
    if (contentType) {
      const typeThresholds: Record<string, number> = {
        simulation: 0.80,
        animation: 0.75,
        assessment: 0.90,
        default: this.DEFAULT_DUPLICATE_THRESHOLD,
      };
      return typeThresholds[contentType] ?? typeThresholds.default;
    }

    return this.DEFAULT_DUPLICATE_THRESHOLD;
  }

  /**
   * Generate embedding vector for query text
   */
  private async generateQueryEmbedding(text: string): Promise<number[]> {
    // If we have an embedding client, use it
    if (this.embeddingClient) {
      return this.embeddingClient.generateEmbedding(text);
    }

    // Fallback: Use database to call embedding function or throw error
    // This would typically call an AI service
    throw new Error("Embedding client not configured");
  }

  /**
   * Search for similar embeddings in the database
   */
  private async searchSimilarEmbeddings(params: {
    tenantId: string;
    queryVector: number[];
    threshold: number;
    maxResults: number;
    excludeAssetId?: string | undefined;
    domain?: string | undefined;
  }): Promise<
    Array<{
      chunkId: string;
      assetId: string;
      chunkText: string;
      chunkSource: string;
      assetTitle: string;
      assetType: string;
      vector: Buffer;
      domain: string | null;
      similarity: number;
    }>
  > {
    const { tenantId, queryVector, threshold, maxResults, excludeAssetId, domain } = params;

    // Build the where clause
    const whereClause: Record<string, unknown> = {
      embeddingStatus: "READY",
      asset: {
        tenantId,
        status: { in: ["PUBLISHED", "DRAFT"] },
        ...(excludeAssetId && { id: { not: excludeAssetId } }),
        ...(domain && { domain }),
      },
    };

    // Fetch candidate chunks with embeddings using include for relations
    const chunks = await this.prisma.semanticChunk.findMany({
      where: whereClause,
      include: {
        asset: {
          select: {
            id: true,
            title: true,
            assetType: true,
            domain: true,
          },
        },
        embedding: {
          select: {
            vector: true,
          },
        },
      },
      take: 100, // Limit candidates for in-memory comparison
    });

    // Calculate cosine similarity for each chunk
    const scoredChunks = chunks
      .filter((chunk) => chunk.embedding?.vector)
      .map((chunk) => {
        const vector = this.bufferToFloatArray(chunk.embedding!.vector);
        const similarity = this.cosineSimilarity(queryVector, vector);

        return {
          chunkId: chunk.id,
          assetId: chunk.asset.id,
          chunkText: chunk.text,
          chunkSource: chunk.source,
          assetTitle: chunk.asset.title,
          assetType: chunk.asset.assetType,
          vector: chunk.embedding!.vector,
          domain: chunk.asset.domain,
          similarity,
        };
      })
      .filter((item) => item.similarity >= threshold)
      .sort((a, b) => b.similarity - a.similarity)
      .slice(0, maxResults);

    return scoredChunks;
  }

  /**
   * Deduplicate results by asset and format
   */
  private deduplicateAndFormatResults(
    chunks: Array<{
      chunkId: string;
      assetId: string;
      assetTitle: string;
      assetType: string;
      chunkText: string;
      chunkSource: string;
      similarity: number;
      domain: string | null;
    }>,
    maxResults: number,
  ): ContentSimilarityMatch[] {
    const seenAssets = new Set<string>();
    const results: ContentSimilarityMatch[] = [];

    for (const chunk of chunks) {
      // Only take the highest similarity match per asset
      if (seenAssets.has(chunk.assetId)) {
        continue;
      }

      seenAssets.add(chunk.assetId);

      results.push({
        assetId: chunk.assetId,
        assetTitle: chunk.assetTitle,
        assetType: chunk.assetType,
        chunkText: chunk.chunkText.substring(0, 200) + "...", // Truncate for display
        similarityScore: Math.round(chunk.similarity * 100) / 100, // Round to 2 decimals
        matchReason: this.generateMatchReason(chunk.similarity),
        chunkSource: chunk.chunkSource,
      });

      if (results.length >= maxResults) {
        break;
      }
    }

    return results;
  }

  /**
   * Calculate cosine similarity between two vectors
   */
  private cosineSimilarity(a: number[], b: number[]): number {
    if (a.length !== b.length) {
      throw new Error("Vectors must have same dimensions");
    }

    let dotProduct = 0;
    let normA = 0;
    let normB = 0;

    for (let i = 0; i < a.length; i++) {
      dotProduct += a[i] * b[i];
      normA += a[i] * a[i];
      normB += b[i] * b[i];
    }

    const magnitude = Math.sqrt(normA) * Math.sqrt(normB);
    return magnitude === 0 ? 0 : dotProduct / magnitude;
  }

  /**
   * Convert buffer/Uint8Array to float array
   */
  private bufferToFloatArray(buffer: Buffer | Uint8Array): number[] {
    const arrayBuffer = buffer.buffer.slice(buffer.byteOffset, buffer.byteOffset + buffer.byteLength);
    const floatArray = new Float32Array(arrayBuffer);
    return Array.from(floatArray);
  }

  /**
   * Generate human-readable match reason
   */
  private generateMatchReason(similarity: number): string {
    if (similarity >= 0.95) {
      return "Very similar content detected";
    } else if (similarity >= 0.90) {
      return "Highly similar concepts";
    } else if (similarity >= 0.80) {
      return "Related content found";
    } else {
      return "Some content overlap";
    }
  }
}
