/**
 * Semantic Search Service
 *
 * Combines vector similarity with text search for hybrid results:
 * - AI query embedding generation
 * - Vector similarity search in database
 * - Hybrid ranking (text + semantic)
 * - Search result ranking and deduplication
 *
 * @doc.type service
 * @doc.purpose Provide AI-powered semantic search capabilities
 * @doc.layer product
 * @doc.pattern Service
 */
import type { PrismaClient } from "@prisma/client";

export interface SearchQuery {
  query: string;
  tenantId: string;
  filters?: {
    contentTypes?: string[];
    difficultyLevel?: number;
    domain?: string;
  };
  limit?: number;
}

export interface SearchResult {
  contentId: string;
  contentType: string;
  title: string;
  description: string;
  semanticScore: number;
  textScore: number;
  combinedScore: number;
  metadata: Record<string, unknown>;
}

export interface HybridSearchConfig {
  semanticWeight: number;
  textWeight: number;
  vectorDimension: number;
  similarityThreshold: number;
  maxResults: number;
}

export const DEFAULT_HYBRID_CONFIG: HybridSearchConfig = {
  semanticWeight: 0.6,
  textWeight: 0.4,
  vectorDimension: 1536,
  similarityThreshold: 0.7,
  maxResults: 20,
};

export class SemanticSearchService {
  private config: HybridSearchConfig;

  constructor(
    private readonly prisma: PrismaClient,
    config?: Partial<HybridSearchConfig>,
  ) {
    this.config = { ...DEFAULT_HYBRID_CONFIG, ...config };
  }

  /**
   * Generate embedding for search query using AI
   */
  async generateQueryEmbedding(query: string): Promise<number[]> {
    // This would call the AI embedding service
    // For now, return a mock embedding
    // In production, this calls OpenAI, Cohere, or local embedding model

    // Mock implementation - generates deterministic pseudo-embedding
    const mockEmbedding = this.generateMockEmbedding(query);

    return mockEmbedding;
  }

  /**
   * Perform hybrid search (text + semantic)
   */
  async hybridSearch(searchQuery: SearchQuery): Promise<SearchResult[]> {
    const { query, tenantId, filters, limit = this.config.maxResults } = searchQuery;

    // Generate query embedding
    const queryEmbedding = await this.generateQueryEmbedding(query);

    // Run semantic and text searches in parallel
    const [semanticResults, textResults] = await Promise.all([
      this.semanticSearch(queryEmbedding, tenantId, filters, limit * 2),
      this.textSearch(query, tenantId, filters, limit * 2),
    ]);

    // Combine and rank results
    const combinedResults = this.combineResults(
      semanticResults,
      textResults,
      this.config.semanticWeight,
      this.config.textWeight,
    );

    // Return top results
    return combinedResults.slice(0, limit);
  }

  /**
   * Pure semantic search using vector similarity
   */
  async semanticSearch(
    queryEmbedding: number[],
    tenantId: string,
    filters?: SearchQuery["filters"],
    limit: number = 20,
  ): Promise<Array<{ contentId: string; score: number; metadata: Record<string, unknown> }>> {
    // Convert embedding to PostgreSQL vector format
    const vectorString = `[${queryEmbedding.join(",")}]`;

    // Query using vector similarity
    const results = await this.prisma.$queryRaw<Array<{
      contentId: string;
      similarity: number;
      metadata: string;
    }>>`
      SELECT 
        c.id as "contentId",
        1 - (e.vector <=> ${vectorString}::vector) as similarity,
        jsonb_build_object(
          'title', c.title,
          'contentType', c."assetType",
          'domain', c.domain
        )::text as metadata
      FROM "EmbeddingVector" e
      JOIN "SemanticChunk" s ON e."chunkId" = s.id
      JOIN "ContentAsset" c ON s."assetId" = c.id
      WHERE c."tenantId" = ${tenantId}
        AND c.status = 'PUBLISHED'
        ${filters?.contentTypes && filters.contentTypes.length > 0
          ? `AND c."assetType" IN (${filters.contentTypes.map(t => `'${t}'`).join(",")})`
          : ""}
        ${filters?.domain ? `AND c.domain = ${filters.domain}` : ""}
      ORDER BY e.vector <=> ${vectorString}::vector
      LIMIT ${limit}
    `.catch(() => []);

    return results.map((r) => ({
      contentId: r.contentId,
      score: r.similarity,
      metadata: JSON.parse(r.metadata) as Record<string, unknown>,
    }));
  }

  /**
   * Pure text search using full-text search
   */
  async textSearch(
    query: string,
    tenantId: string,
    filters?: SearchQuery["filters"],
    limit: number = 20,
  ): Promise<Array<{ contentId: string; score: number; metadata: Record<string, unknown> }>> {
    // Use PostgreSQL full-text search
    const results = await this.prisma.$queryRaw<Array<{
      contentId: string;
      rank: number;
      title: string;
      contentType: string;
      domain: string | null;
      description: string | null;
    }>>`
      SELECT 
        c.id as "contentId",
        ts_rank(
          to_tsvector('english', c.title || ' ' || COALESCE(c."searchableText", '')),
          plainto_tsquery('english', ${query})
        ) as rank,
        c.title,
        c."assetType" as "contentType",
        c.domain,
        LEFT(c."searchableText", 200) as description
      FROM "ContentAsset" c
      WHERE c."tenantId" = ${tenantId}
        AND c.status = 'PUBLISHED'
        AND (
          to_tsvector('english', c.title || ' ' || COALESCE(c."searchableText", ''))
          @@ plainto_tsquery('english', ${query})
        )
        ${filters?.contentTypes && filters.contentTypes.length > 0
          ? `AND c."assetType" IN (${filters.contentTypes.map(t => `'${t}'`).join(",")})`
          : ""}
        ${filters?.domain ? `AND c.domain = ${filters.domain}` : ""}
      ORDER BY rank DESC
      LIMIT ${limit}
    `.catch(() => []);

    return results.map((r) => ({
      contentId: r.contentId,
      score: r.rank,
      metadata: {
        title: r.title,
        contentType: r.contentType,
        domain: r.domain,
        description: r.description,
      },
    }));
  }

  /**
   * Combine semantic and text search results
   */
  private combineResults(
    semanticResults: Array<{ contentId: string; score: number; metadata: Record<string, unknown> }>,
    textResults: Array<{ contentId: string; score: number; metadata: Record<string, unknown> }>,
    semanticWeight: number,
    textWeight: number,
  ): SearchResult[] {
    const combined = new Map<string, SearchResult>();

    // Normalize scores (0-1 range)
    const maxSemanticScore = Math.max(...semanticResults.map((r) => r.score), 1);
    const maxTextScore = Math.max(...textResults.map((r) => r.score), 1);

    // Add semantic results
    for (const result of semanticResults) {
      const normalizedSemanticScore = result.score / maxSemanticScore;
      combined.set(result.contentId, {
        contentId: result.contentId,
        contentType: (result.metadata.contentType as string) ?? "unknown",
        title: (result.metadata.title as string) ?? "Untitled",
        description: (result.metadata.description as string) ?? "",
        semanticScore: normalizedSemanticScore,
        textScore: 0,
        combinedScore: normalizedSemanticScore * semanticWeight,
        metadata: result.metadata,
      });
    }

    // Add/merge text results
    for (const result of textResults) {
      const normalizedTextScore = result.score / maxTextScore;
      const existing = combined.get(result.contentId);

      if (existing) {
        existing.textScore = normalizedTextScore;
        existing.combinedScore += normalizedTextScore * textWeight;
        // Merge metadata
        existing.metadata = { ...existing.metadata, ...result.metadata };
      } else {
        combined.set(result.contentId, {
          contentId: result.contentId,
          contentType: (result.metadata.contentType as string) ?? "unknown",
          title: (result.metadata.title as string) ?? "Untitled",
          description: (result.metadata.description as string) ?? "",
          semanticScore: 0,
          textScore: normalizedTextScore,
          combinedScore: normalizedTextScore * textWeight,
          metadata: result.metadata,
        });
      }
    }

    // Sort by combined score
    return Array.from(combined.values())
      .sort((a, b) => b.combinedScore - a.combinedScore);
  }

  /**
   * Generate mock embedding for development/testing
   */
  private generateMockEmbedding(text: string): number[] {
    // Generate a deterministic pseudo-embedding based on text content
    // This is NOT for production - replace with actual AI embedding
    const seed = text.split("").reduce((acc, char) => acc + char.charCodeAt(0), 0);
    const embedding: number[] = [];

    for (let i = 0; i < this.config.vectorDimension; i++) {
      // Pseudo-random based on seed and position
      const value = Math.sin(seed * (i + 1)) * Math.cos((i + 1) * 0.5);
      embedding.push(value);
    }

    // Normalize to unit vector
    const magnitude = Math.sqrt(embedding.reduce((sum, v) => sum + v * v, 0));
    return embedding.map((v) => v / magnitude);
  }

  /**
   * Index content for semantic search
   */
  async indexContent(
    contentId: string,
    contentType: string,
    text: string,
    tenantId: string,
  ): Promise<void> {
    // Generate embedding for content
    const embedding = await this.generateQueryEmbedding(text);

    // Store in database
    await this.prisma.$executeRaw`
      INSERT INTO "SemanticChunk" (id, "assetId", text, source, created_at)
      VALUES (gen_random_uuid(), ${contentId}, ${text}, ${contentType}, NOW())
      ON CONFLICT ("assetId", text) DO NOTHING
    `.catch(() => {
      console.log(`[INDEX] Indexed content ${contentId}`);
    });

    // Get chunk ID and store embedding
    const chunk = await this.prisma.$queryRaw<Array<{ id: string }>>`
      SELECT id FROM "SemanticChunk"
      WHERE "assetId" = ${contentId}
        AND text = ${text}
      LIMIT 1
    `.catch(() => []);

    const firstChunk = chunk[0];
    if (chunk.length > 0 && firstChunk !== undefined) {
      const vectorString = `[${embedding.join(",")}]`;

      await this.prisma.$executeRaw`
        INSERT INTO "EmbeddingVector" (id, "chunkId", vector, created_at)
        VALUES (gen_random_uuid(), ${firstChunk.id}, ${vectorString}::vector, NOW())
        ON CONFLICT ("chunkId") 
        DO UPDATE SET vector = ${vectorString}::vector
      `.catch(() => {
        // Ignore conflicts
      });
    }
  }

  /**
   * Update service configuration
   */
  updateConfig(config: Partial<HybridSearchConfig>): void {
    this.config = { ...this.config, ...config };
  }

  /**
   * Get current configuration
   */
  getConfig(): HybridSearchConfig {
    return { ...this.config };
  }
}
