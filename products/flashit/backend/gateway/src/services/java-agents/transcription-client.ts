/**
 * Transcription Client
 * 
 * Provides high-level interface for audio/video transcription
 * through the Java Agent Service.
 * 
 * @doc.type service
 * @doc.purpose Audio/video transcription client
 * @doc.layer infrastructure
 * @doc.pattern Client
 */

import { getJavaAgentClient, isJavaAgentServiceAvailable } from './agent-client.js';
import type { TranscriptionRequest, TranscriptionResponse } from './agent-client.js';
import { prisma } from '../../lib/prisma.js';

/**
 * Transcribe audio from a moment
 * 
 * @param momentId - Moment containing audio/video
 * @param userId - User ID for authorization
 * @param language - Language code (e.g., 'en', 'es', 'fr')
 * @param includeTimestamps - Include word-level timestamps
 * @param storeInDb - Automatically save transcript to database
 * @returns Transcription result with text and metadata
 */
export async function transcribeMoment(
  momentId: string,
  userId: string,
  language: string = 'en',
  includeTimestamps: boolean = true,
  storeInDb: boolean = true
): Promise<TranscriptionResponse> {
  const isAvailable = await isJavaAgentServiceAvailable();
  
  if (!isAvailable) {
    throw new Error('Java Agent Service is not available. Cannot transcribe audio.');
  }

  // Fetch moment and verify ownership
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    include: {
      media: true,
    },
  });

  if (!moment) {
    throw new Error(`Moment ${momentId} not found or access denied.`);
  }

  // Find audio/video media
  const audioMedia = moment.media.find(
    (m) => m.type === 'audio' || m.type === 'video'
  );

  if (!audioMedia) {
    throw new Error('No audio or video media found in moment.');
  }

  const client = getJavaAgentClient();
  
  const request: TranscriptionRequest = {
    audioUrl: audioMedia.url,
    language,
    includeTimestamps,
    userId,
    momentId,
  };

  try {
    const result = await client.transcribe(request);

    // Store transcript in database if requested
    if (storeInDb && result.transcript) {
      await prisma.moment.update({
        where: { id: momentId },
        data: {
          contentTranscript: result.transcript,
          updatedAt: new Date(),
        },
      });
    }

    return result;
  } catch (error) {
    throw new Error(
      `Failed to transcribe audio: ${
        error instanceof Error ? error.message : 'Unknown error'
      }`
    );
  }
}

/**
 * Batch transcribe multiple moments
 * 
 * @param momentIds - Array of moment IDs to transcribe
 * @param userId - User ID for authorization
 * @param language - Language code
 * @param storeInDb - Automatically save transcripts to database
 * @returns Array of transcription results
 */
export async function transcribeBatch(
  momentIds: string[],
  userId: string,
  language: string = 'en',
  storeInDb: boolean = true
): Promise<Array<{
  momentId: string;
  result?: TranscriptionResponse;
  error?: string;
}>> {
  const results = await Promise.allSettled(
    momentIds.map((momentId) =>
      transcribeMoment(momentId, userId, language, true, storeInDb)
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
        error: result.reason?.message || 'Transcription failed',
      };
    }
  });
}

/**
 * Get existing transcript for a moment
 * 
 * @param momentId - Moment ID
 * @param userId - User ID for authorization
 * @returns Transcript if available, null otherwise
 */
export async function getTranscript(
  momentId: string,
  userId: string
): Promise<{
  transcript: string;
  language: string | null;
  createdAt: Date;
} | null> {
  const moment = await prisma.moment.findFirst({
    where: { id: momentId, userId },
    select: {
      contentTranscript: true,
      updatedAt: true,
    },
  });

  if (!moment || !moment.contentTranscript) {
    return null;
  }

  return {
    transcript: moment.contentTranscript,
    language: null,
    createdAt: moment.updatedAt,
  };
}

/**
 * Retranscribe a moment with different settings
 * 
 * @param momentId - Moment ID
 * @param userId - User ID
 * @param language - New language code
 * @returns New transcription result
 */
export async function retranscribeMoment(
  momentId: string,
  userId: string,
  language: string = 'en'
): Promise<TranscriptionResponse> {
  return transcribeMoment(momentId, userId, language, true, true);
}

/**
 * Find moments that need transcription
 * 
 * @param userId - User ID
 * @param limit - Maximum number of moments to return
 * @returns Array of moment IDs that need transcription
 */
export async function findMomentsNeedingTranscription(
  userId: string,
  limit: number = 50
): Promise<string[]> {
  const moments = await prisma.moment.findMany({
    where: {
      userId,
      transcript: null,
      media: {
        some: {
          type: {
            in: ['audio', 'video'],
          },
        },
      },
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
 * Auto-transcribe all pending moments for a user
 * 
 * @param userId - User ID
 * @param maxBatch - Maximum number of moments to process
 * @returns Summary of transcription results
 */
export async function autoTranscribePendingMoments(
  userId: string,
  maxBatch: number = 10
): Promise<{
  total: number;
  successful: number;
  failed: number;
  errors: string[];
}> {
  const momentIds = await findMomentsNeedingTranscription(userId, maxBatch);

  if (momentIds.length === 0) {
    return {
      total: 0,
      successful: 0,
      failed: 0,
      errors: [],
    };
  }

  const results = await transcribeBatch(momentIds, userId);

  const successful = results.filter((r) => !r.error).length;
  const failed = results.filter((r) => r.error).length;
  const errors = results
    .filter((r) => r.error)
    .map((r) => `${r.momentId}: ${r.error}`);

  return {
    total: momentIds.length,
    successful,
    failed,
    errors,
  };
}

/**
 * Get transcription statistics for a user
 * 
 * @param userId - User ID
 * @returns Transcription statistics
 */
export async function getTranscriptionStats(
  userId: string
): Promise<{
  totalMoments: number;
  transcribed: number;
  pending: number;
  percentageComplete: number;
}> {
  const [totalWithMedia, transcribed, pending] = await Promise.all([
    prisma.moment.count({
      where: {
        userId,
        media: {
          some: {
            type: {
              in: ['audio', 'video'],
            },
          },
        },
      },
    }),
    prisma.moment.count({
      where: {
        userId,
        transcript: { not: null },
        media: {
          some: {
            type: {
              in: ['audio', 'video'],
            },
          },
        },
      },
    }),
    prisma.moment.count({
      where: {
        userId,
        transcript: null,
        media: {
          some: {
            type: {
              in: ['audio', 'video'],
            },
          },
        },
      },
    }),
  ]);

  const percentageComplete =
    totalWithMedia > 0 ? (transcribed / totalWithMedia) * 100 : 0;

  return {
    totalMoments: totalWithMedia,
    transcribed,
    pending,
    percentageComplete: Math.round(percentageComplete * 10) / 10,
  };
}

/**
 * Check if transcription service is available
 * 
 * @returns Service availability status
 */
export async function isTranscriptionServiceAvailable(): Promise<boolean> {
  return isJavaAgentServiceAvailable();
}
