/**
 * Search Service
 *
 * Provides semantic search across all content types using AI-powered matching.
 * Integrates with existing fuzzy search to provide intelligent results.
 *
 * @doc.type service
 * @doc.purpose Semantic search service
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface SearchDocument {
  id: string;
  title: string;
  content: string;
  type: 'page' | 'task' | 'file' | 'user' | 'setting' | 'artifact';
  path: string;
  metadata?: Record<string, unknown>;
  embedding?: number[]; // Vector embedding for semantic search
}

export interface SearchResult {
  document: SearchDocument;
  score: number;
  matchType: 'exact' | 'fuzzy' | 'semantic';
  highlights?: string[];
}

export interface SemanticSearchRequest {
  query: string;
  documents: SearchDocument[];
  limit?: number;
  threshold?: number;
}

export interface SemanticSearchResponse {
  results: SearchResult[];
  metadata: {
    query: string;
    totalResults: number;
    searchTime: number;
    algorithm: 'hybrid';
  };
}

// ============================================================================
// Fuzzy Search Algorithm
// ============================================================================

/**
 * Simple fuzzy matching algorithm
 * Returns a score between 0 and 1 (higher is better match)
 */
function fuzzyMatch(query: string, text: string): number {
  const queryLower = query.toLowerCase();
  const textLower = text.toLowerCase();

  // Exact match
  if (textLower === queryLower) return 1.0;

  // Contains match
  if (textLower.includes(queryLower)) return 0.8;

  // Fuzzy character matching
  let queryIndex = 0;
  let textIndex = 0;
  let matches = 0;
  let consecutiveMatches = 0;
  let maxConsecutive = 0;

  while (queryIndex < queryLower.length && textIndex < textLower.length) {
    if (queryLower[queryIndex] === textLower[textIndex]) {
      matches++;
      consecutiveMatches++;
      maxConsecutive = Math.max(maxConsecutive, consecutiveMatches);
      queryIndex++;
    } else {
      consecutiveMatches = 0;
    }
    textIndex++;
  }

  if (queryIndex !== queryLower.length) return 0;

  const matchRatio = matches / queryLower.length;
  const consecutiveBonus = maxConsecutive / queryLower.length;

  return matchRatio * 0.7 + consecutiveBonus * 0.3;
}

/**
 * Calculate semantic similarity using cosine similarity
 * This is a simplified version - in production, use actual embeddings
 */
function semanticSimilarity(queryEmbedding: number[], docEmbedding: number[]): number {
  if (!queryEmbedding || !docEmbedding) return 0;

  // Cosine similarity
  let dotProduct = 0;
  let queryNorm = 0;
  let docNorm = 0;

  for (let i = 0; i < Math.min(queryEmbedding.length, docEmbedding.length); i++) {
    dotProduct += queryEmbedding[i] * docEmbedding[i];
    queryNorm += queryEmbedding[i] * queryEmbedding[i];
    docNorm += docEmbedding[i] * docEmbedding[i];
  }

  if (queryNorm === 0 || docNorm === 0) return 0;

  return dotProduct / (Math.sqrt(queryNorm) * Math.sqrt(docNorm));
}

/**
 * Generate a simple embedding from text (mock implementation)
 * In production, use actual embedding model
 */
function generateEmbedding(text: string): number[] {
  // Simple hash-based embedding (mock)
  // In production, use actual ML model like OpenAI embeddings
  const words = text.toLowerCase().split(/\s+/);
  const embedding = new Array(128).fill(0);

  words.forEach((word, i) => {
    for (let j = 0; j < word.length; j++) {
      const idx = (word.charCodeAt(j) + i * 7) % 128;
      embedding[idx] += 1 / (i + 1);
    }
  });

  // Normalize
  const norm = Math.sqrt(embedding.reduce((sum, val) => sum + val * val, 0));
  if (norm > 0) {
    embedding.forEach((val, i) => {
      embedding[i] = val / norm;
    });
  }

  return embedding;
}

// ============================================================================
// Hybrid Search Implementation
// ============================================================================

/**
 * Perform hybrid semantic search combining fuzzy and semantic matching
 */
export async function semanticSearch(
  request: SemanticSearchRequest
): Promise<SemanticSearchResponse> {
  const startTime = performance.now();
  const { query, documents, limit = 10, threshold = 0.3 } = request;

  const queryEmbedding = generateEmbedding(query);

  // Score all documents using hybrid approach
  const scoredDocuments = documents.map((document) => {
    const fuzzyScoreTitle = fuzzyMatch(query, document.title);
    const fuzzyScoreContent = fuzzyMatch(query, document.content);
    const fuzzyScore = Math.max(fuzzyScoreTitle, fuzzyScoreContent);

    // Generate or use existing embedding
    const docEmbedding = document.embedding || generateEmbedding(document.content);
    const semanticScore = semanticSimilarity(queryEmbedding, docEmbedding);

    // Hybrid score: 60% fuzzy, 40% semantic
    const hybridScore = fuzzyScore * 0.6 + semanticScore * 0.4;

    // Determine match type
    let matchType: SearchResult['matchType'] = 'fuzzy';
    if (fuzzyScore === 1.0) {
      matchType = 'exact';
    } else if (semanticScore > 0.7) {
      matchType = 'semantic';
    }

    return {
      document,
      score: hybridScore,
      matchType,
    };
  });

  // Filter and sort
  const results = scoredDocuments
    .filter((result) => result.score >= threshold)
    .sort((a, b) => b.score - a.score)
    .slice(0, limit);

  const searchTime = performance.now() - startTime;

  return {
    results,
    metadata: {
      query,
      totalResults: results.length,
      searchTime,
      algorithm: 'hybrid',
    },
  };
}

/**
 * Index documents for search
 */
export async function indexDocuments(documents: SearchDocument[]): Promise<void> {
  // In production, this would store embeddings in a vector database
  // For now, we generate embeddings on the fly
  documents.forEach((doc) => {
    if (!doc.embedding) {
      doc.embedding = generateEmbedding(doc.content);
    }
  });
}

/**
 * Get search highlights
 */
export function getHighlights(query: string, content: string, maxHighlights: number = 3): string[] {
  const queryLower = query.toLowerCase();
  const contentLower = content.toLowerCase();
  const highlights: string[] = [];

  const words = contentLower.split(/\s+/);
  const queryWords = queryLower.split(/\s+/);

  queryWords.forEach((queryWord) => {
    const index = words.findIndex((word) => word.includes(queryWord));
    if (index !== -1) {
      const start = Math.max(0, index - 3);
      const end = Math.min(words.length, index + 4);
      const highlight = words.slice(start, end).join(' ');
      if (!highlights.includes(highlight)) {
        highlights.push(highlight);
      }
    }
  });

  return highlights.slice(0, maxHighlights);
}
