/**
 * NLP Client
 * 
 * Provides high-level interface for Natural Language Processing
 * through the Java Agent Service (entity extraction, sentiment analysis, mood detection).
 * 
 * @doc.type service
 * @doc.purpose Natural Language Processing client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type { NLPRequest, NLPResponse } from './agent-client.js';
import { prisma } from '../../lib/prisma.js';

/** Default empty NLP response for moments with no text content */
const EMPTY_NLP_RESPONSE: NLPResponse = {
  entities: [],
  processingTimeMs: 0,
  model: 'none',
};

/**
 * Extract entities from moment content
 * 
 * @param momentId - Moment ID
 * @param userId - User ID for authorization
 * @param storeInDb - Automatically save entities as tags
 * @returns Extracted entities (people, places, organizations, etc.)
 */
export async function extractEntitiesFromMoment(
  momentId: string,
  userId: string,
  storeInDb: boolean = true
): Promise<NLPResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot extract entities.');
  }

  // Fetch moment and verify ownership
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: {
      id: true,
      contentText: true,
      contentTranscript: true,
      tags: true,
    },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found or access denied.`);
  }

  const text = moment.contentText || moment.contentTranscript || '';
  
  if (!text || text.trim().length === 0) {
    return EMPTY_NLP_RESPONSE;
  }

  const client = getJavaAgentClient();
  
  const request: NLPRequest = {
    text,
    userId,
    momentId,
    analysisTypes: ['entity_extraction'],
  };

  try {
    const result = await client.extractEntities(request);

    // Store entities as tags if requested
    if (storeInDb && result.entities && result.entities.length > 0) {
      const existingTags = (moment.tags as string[]) || [];
      const entityTags = result.entities
        .filter((e) => e.type === 'PERSON' || e.type === 'LOCATION' || e.type === 'ORGANIZATION')
        .map((e) => e.text);
      
      const mergedTags = Array.from(new Set([...existingTags, ...entityTags]));

      await prisma.moment.update({
        where: { id: momentId },
        data: {
          tags: mergedTags,
          updatedAt: new Date(),
        },
      });
    }

    return result;
  } catch (error) {
    throw new Error(
      `Failed to extract entities: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Analyze sentiment of moment content
 * 
 * @param momentId - Moment ID
 * @param userId - User ID for authorization
 * @param storeInDb - Automatically save sentiment to database
 * @returns Sentiment analysis (positive, negative, neutral with confidence)
 */
export async function analyzeMomentSentiment(
  momentId: string,
  userId: string,
  storeInDb: boolean = true
): Promise<NLPResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot analyze sentiment.');
  }

  // Fetch moment and verify ownership
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: {
      id: true,
      contentText: true,
      contentTranscript: true,
    },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found or access denied.`);
  }

  const text = moment.contentText || moment.contentTranscript || '';
  
  if (!text || text.trim().length === 0) {
    return EMPTY_NLP_RESPONSE;
  }

  const client = getJavaAgentClient();
  
  const request: NLPRequest = {
    text,
    userId,
    momentId,
    analysisTypes: ['sentiment_analysis'],
  };

  try {
    const result = await client.analyzeSentiment(request);

    // Store sentiment metadata if requested
    if (storeInDb && result.sentiment) {
      await prisma.moment.update({
        where: { id: momentId },
        data: {
          metadata: {
            sentiment: result.sentiment,
          } as any,
          updatedAt: new Date(),
        },
      });
    }

    return result;
  } catch (error) {
    throw new Error(
      `Failed to analyze sentiment: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Detect mood from moment content
 * 
 * @param momentId - Moment ID
 * @param userId - User ID for authorization
 * @param storeInDb - Automatically save mood as emotion
 * @returns Detected mood (happy, sad, anxious, calm, etc.)
 */
export async function detectMomentMood(
  momentId: string,
  userId: string,
  storeInDb: boolean = true
): Promise<NLPResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot detect mood.');
  }

  // Fetch moment and verify ownership
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: {
      id: true,
      contentText: true,
      contentTranscript: true,
      emotions: true,
    },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found or access denied.`);
  }

  const text = moment.contentText || moment.contentTranscript || '';
  
  if (!text || text.trim().length === 0) {
    return EMPTY_NLP_RESPONSE;
  }

  const client = getJavaAgentClient();
  
  const request: NLPRequest = {
    text,
    userId,
    momentId,
    analysisTypes: ['mood_detection'],
  };

  try {
    const result = await client.detectMood(request);

    // Store mood as emotion if requested
    if (storeInDb && result.mood) {
      const existingEmotions = (moment.emotions as string[]) || [];
      const moodString = result.mood.primaryMood || 'neutral';
      const mergedEmotions = Array.from(new Set([...existingEmotions, moodString]));

      await prisma.moment.update({
        where: { id: momentId },
        data: {
          emotions: mergedEmotions,
          updatedAt: new Date(),
        },
      });
    }

    return result;
  } catch (error) {
    throw new Error(
      `Failed to detect mood: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Comprehensive NLP analysis (entities + sentiment + mood)
 * 
 * @param momentId - Moment ID
 * @param userId - User ID for authorization
 * @param storeInDb - Automatically save all results to database
 * @returns Complete NLP analysis
 */
export async function analyzeMomentComprehensive(
  momentId: string,
  userId: string,
  storeInDb: boolean = true
): Promise<NLPResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot perform NLP analysis.');
  }

  // Fetch moment and verify ownership
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: {
      id: true,
      contentText: true,
      contentTranscript: true,
      tags: true,
      emotions: true,
    },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found or access denied.`);
  }

  const text = moment.contentText || moment.contentTranscript || '';
  
  if (!text || text.trim().length === 0) {
    return EMPTY_NLP_RESPONSE;
  }

  const client = getJavaAgentClient();
  
  const request: NLPRequest = {
    text,
    userId,
    momentId,
    analysisTypes: ['entity_extraction', 'sentiment_analysis', 'mood_detection'],
  };

  try {
    // Perform all NLP tasks together
    const [entitiesResult, sentimentResult, moodResult] = await Promise.all([
      client.extractEntities(request),
      client.analyzeSentiment(request),
      client.detectMood(request),
    ]);

    // Merge results
    const result: NLPResponse = {
      entities: entitiesResult.entities || [],
      sentiment: sentimentResult.sentiment,
      mood: moodResult.mood,
      processingTimeMs:
        (entitiesResult.processingTimeMs || 0) +
        (sentimentResult.processingTimeMs || 0) +
        (moodResult.processingTimeMs || 0),
      model: entitiesResult.model || 'unknown',
    };

    // Store all results if requested
    if (storeInDb) {
      const existingTags = (moment.tags as string[]) || [];
      const existingEmotions = (moment.emotions as string[]) || [];

      const entityTags = result.entities
        ? result.entities
            .filter((e) => e.type === 'PERSON' || e.type === 'LOCATION' || e.type === 'ORGANIZATION')
            .map((e) => e.text)
        : [];
      
      const mergedTags = Array.from(new Set([...existingTags, ...entityTags]));
      const moodString = result.mood?.primaryMood || 'neutral';
      const mergedEmotions = Array.from(
        new Set([...existingEmotions, moodString])
      );

      await prisma.moment.update({
        where: { id: momentId },
        data: {
          tags: mergedTags,
          emotions: mergedEmotions,
          metadata: {
            sentiment: result.sentiment,
          } as any,
          updatedAt: new Date(),
        },
      });
    }

    return result;
  } catch (error) {
    throw new Error(
      `Failed to perform comprehensive NLP analysis: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Batch analyze multiple moments
 * 
 * @param momentIds - Array of moment IDs
 * @param userId - User ID for authorization
 * @param storeInDb - Automatically save results to database
 * @returns Array of NLP analysis results
 */
export async function analyzeBatch(
  momentIds: string[],
  userId: string,
  storeInDb: boolean = true
): Promise<Array<{
  momentId: string;
  result?: NLPResponse;
  error?: string;
}>> {
  const results = await Promise.allSettled(
    momentIds.map((momentId) =>
      analyzeMomentComprehensive(momentId, userId, storeInDb)
    )
  );

  return results.map((result, index) => {
    if (result.status === 'fulfilled') {
      return {
        momentId: momentIds[index],
        result: result.value,
      };
    } else {
      return {
        momentId: momentIds[index],
        error: result.reason?.message || 'Analysis failed',
      };
    }
  });
}

/**
 * Find moments that need NLP analysis
 * 
 * @param userId - User ID
 * @param limit - Maximum number of moments to return
 * @returns Array of moment IDs that need analysis
 */
export async function findMomentsNeedingAnalysis(
  userId: string,
  limit: number = 50
): Promise<string[]> {
  const moments = await prisma.moment.findMany({
    where: {
      userId,
      OR: [
        { tags: { equals: [] } },
        { emotions: { equals: [] } },
      ],
    },
    select: {
      id: true,
    },
    take: limit,
    orderBy: {
      capturedAt: 'desc',
    },
  });

  return moments.map((m) => m.id);
}

/**
 * Check if NLP service is available
 * 
 * @returns Service availability status
 */
export async function isNLPServiceAvailable(): Promise<boolean> {
  return isJavaAgentServiceAvailable();
}
