/**
 * Embedding Client
 * 
 * Provides high-level interface for embedding generation and semantic search
 * through the Java Agent Service.
 * 
 * @doc.type service
 * @doc.purpose Embedding generation and semantic search client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type { EmbeddingRequest, EmbeddingResponse, SemanticSearchRequest, SemanticSearchResponse } from './agent-client.js';
import { prisma } from '../../lib/prisma.js';

/**
 * Generate embedding for a moment's content
 * 
 * @param momentId - Unique moment identifier
 * @param text - Text content to embed
 * @param userId - User who owns the moment
 * @param contentType - Type of content (text, transcript, etc.)
 * @param store - Whether to persist the embedding
 * @returns Embedding response with vector and metadata
 */
export async function generateMomentEmbedding(
  momentId: string,
  text: string,
  userId: string,
  contentType: 'text' | 'transcript' | 'combined' = 'text',
  store: boolean = true
): Promise<EmbeddingResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot generate embeddings.');
  }

  const client = getJavaAgentClient();
  
  const request: EmbeddingRequest = {
    momentId,
    text,
    userId,
    contentType,
    store,
  };

  try {
    const response = await client.generateEmbedding(request);
    
    // If store is true and we got an embedding, save to database
    if (store && response.embedding && response.embedding.length > 0) {
      await prisma.momentEmbedding.upsert({
        where: { momentId },
        create: {
          momentId,
          embedding: response.embedding,
          model: response.model,
          dimensions: response.dimensions,
        },
        update: {
          embedding: response.embedding,
          model: response.model,
          dimensions: response.dimensions,
          createdAt: new Date(),
        },
      });
    }

    return response;
  } catch (error) {
    throw new Error(
      `Failed to generate embedding for moment ${momentId}: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Generate embeddings for multiple moments in batch
 * 
 * @param moments - Array of moment data
 * @returns Array of embedding responses
 */
export async function generateBatchEmbeddings(
  moments: Array<{
    momentId: string;
    text: string;
    userId: string;
    contentType?: 'text' | 'transcript' | 'combined';
    store?: boolean;
  }>
): Promise<EmbeddingResponse[]> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot generate embeddings.');
  }

  const client = getJavaAgentClient();
  
  const requests: EmbeddingRequest[] = moments.map((moment) => ({
    momentId: moment.momentId,
    text: moment.text,
    userId: moment.userId,
    contentType: moment.contentType || 'text',
    store: moment.store !== false, // Default to true
  }));

  try {
    const responses = await client.generateBatchEmbeddings(requests);
    
    // Store embeddings in database
    const storedEmbeddings = responses.filter(
      (r) => r.stored && r.embedding && r.embedding.length > 0
    );
    
    if (storedEmbeddings.length > 0) {
      await prisma.$transaction(
        storedEmbeddings.map((response) =>
          prisma.momentEmbedding.upsert({
            where: { momentId: response.momentId! },
            create: {
              momentId: response.momentId!,
              embedding: response.embedding,
              model: response.model,
              dimensions: response.dimensions,
            },
            update: {
              embedding: response.embedding,
              model: response.model,
              dimensions: response.dimensions,
              createdAt: new Date(),
            },
          })
        )
      );
    }

    return responses;
  } catch (error) {
    throw new Error(
      `Failed to generate batch embeddings: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Perform semantic search across user's moments
 * 
 * @param query - Natural language search query
 * @param userId - User performing the search
 * @param sphereIds - Optional sphere filters
 * @param limit - Maximum number of results
 * @param similarityThreshold - Minimum similarity score (0-1)
 * @returns Search results with similarity scores
 */
export async function searchMomentsBySemantic(
  query: string,
  userId: string,
  sphereIds: string[] = [],
  limit: number = 20,
  similarityThreshold: number = 0.7
): Promise<SemanticSearchResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot perform semantic search.');
  }

  const client = getJavaAgentClient();
  
  const request: SemanticSearchRequest = {
    query,
    userId,
    sphereIds,
    limit,
    similarityThreshold,
  };

  try {
    const response = await client.semanticSearch(request);
    
    // Enrich results with full moment data
    if (response.results && response.results.length > 0) {
      const momentIds = response.results.map((r) => r.momentId);
      
      const moments = await prisma.moment.findMany({
        where: {
          id: { in: momentIds },
          userId,
        },
        include: {
          sphere: {
            select: {
              id: true,
              name: true,
              type: true,
            },
          },
          mediaReferences: {
            select: {
              id: true,
              fileName: true,
              mimeType: true,
            },
          },
        },
      });

      // Map similarity scores back to moments
      const momentMap = new Map(moments.map((m) => [m.id, m]));
      
      response.results.forEach((result) => {
        const moment = momentMap.get(result.momentId);
        if (moment) {
          // Attach full moment data to result
          (result as any).moment = moment;
        }
      });
    }

    return response;
  } catch (error) {
    throw new Error(
      `Failed to perform semantic search: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Find similar moments to a given moment
 * 
 * @param momentId - Reference moment ID
 * @param userId - User who owns the moment
 * @param limit - Maximum number of similar moments
 * @param similarityThreshold - Minimum similarity score
 * @returns Array of similar moments with scores
 */
export async function findSimilarMoments(
  momentId: string,
  userId: string,
  limit: number = 10,
  similarityThreshold: number = 0.75
): Promise<SemanticSearchResponse> {
  // Get the reference moment's content
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: { contentText: true, contentTranscript: true },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found`);
  }

  // Use content or transcript as query
  const query = moment.contentText || moment.contentTranscript || '';

  return searchMomentsBySemantic(
    query,
    userId,
    [], // No sphere filter
    limit,
    similarityThreshold
  );
}

/**
 * Get embedding dimensions and model info
 * 
 * @returns Model information and dimensions
 */
export async function getEmbeddingInfo(): Promise<{
  model: string;
  dimensions: number;
  available: boolean;
}> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    return {
      model: 'unavailable',
      dimensions: 0,
      available: false,
    };
  }

  try {
    // Generate a test embedding to get model info
    const client = getJavaAgentClient();
    const testResponse = await client.generateEmbedding({
      text: 'test',
      userId: 'system',
      contentType: 'text',
      store: false,
    });

    return {
      model: testResponse.model,
      dimensions: testResponse.dimensions,
      available: true,
    };
  } catch {
    return {
      model: 'error',
      dimensions: 0,
      available: false,
    };
  }
}
