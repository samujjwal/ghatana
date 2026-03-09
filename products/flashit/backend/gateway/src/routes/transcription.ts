/**
 * Transcription API routes for Flashit Web API
 * Manages transcription using Java Agent Service
 *
 * @doc.type route
 * @doc.purpose Transcription endpoints powered by Java Agent Service
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { WhisperTranscriptionService } from '../services/transcription/whisper-service.js';
import { prisma } from '../lib/prisma.js';
import { checkTranscriptionLimit } from '../services/billing/usage-limits.js';
import { StripeBillingService } from '../services/billing/stripe-service.js';

// Validation schemas
const transcribeMomentSchema = z.object({
  momentId: z.string().uuid(),
  language: z.string().optional().default('en'),
});

const transcribeBatchSchema = z.object({
  momentIds: z.array(z.string().uuid()).min(1).max(50),
  language: z.string().optional().default('en'),
});

const getMomentTranscriptSchema = z.object({
  momentId: z.string().uuid(),
});

export default async function transcriptionRoutes(fastify: FastifyInstance) {

  /**
   * Transcribe a moment's audio/video
   * POST /api/transcription/transcribe
   */
  fastify.post('/transcribe', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(transcribeMomentSchema),
      response: {
        202: zodToJsonSchema(z.object({
          momentId: z.string(),
          jobId: z.string(),
          status: z.string(),
          message: z.string()
        })),
        404: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string()
        }))
      },
    },
  }, async (request, reply) => {
    const { momentId, language } = request.body;
    const userId = (request.user as any).userId;

    try {
      // Check transcription limit for user's billing tier
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkTranscriptionLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.status(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'Transcription hour limit reached for your plan',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      // Find the media reference for this moment
      // We look for the first video or audio
      const mediaRef = await prisma.mediaReference.findFirst({
        where: {
          momentId: momentId,
          moment: {
            userId: userId
          },
          mimeType: {
            contains: 'audio' // Simplification: assume audio or video
          }
        }
      });
      // Try video if audio not found, or use OR query
      const targetMedia = mediaRef || await prisma.mediaReference.findFirst({
        where: {
          momentId: momentId,
          moment: { userId },
          mimeType: { contains: 'video' }
        }
      });

      if (!targetMedia) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'No suitable media found for transcription in this moment',
        });
      }

      const jobId = await WhisperTranscriptionService.enqueueTranscription({
        mediaReferenceId: targetMedia.id,
        s3Bucket: targetMedia.s3Bucket || process.env.S3_BUCKET || 'flashit-uploads',
        s3Key: targetMedia.s3Key,
        userId,
        momentId,
      });

      return reply.status(202).send({
        momentId,
        jobId,
        status: 'queued',
        message: 'Transcription job queued successfully'
      });

    } catch (error) {
      fastify.log.error('Transcription failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to queue transcription',
      });
    }
  });

  /**
   * Batch transcribe multiple moments
   * POST /api/transcription/batch
   */
  fastify.post('/batch', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(transcribeBatchSchema),
      response: {
        200: zodToJsonSchema(z.object({
          results: z.array(z.object({
            momentId: z.string(),
            transcript: z.string().optional(),
            language: z.string().optional(),
            error: z.string().optional(),
          })),
          successful: z.number(),
          failed: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentIds, language } = request.body;
    const userId = (request.user as any).userId;

    try {
      // Check transcription limit for user's billing tier
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkTranscriptionLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.status(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'Transcription hour limit reached for your plan',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const isAvailable = await isTranscriptionServiceAvailable();
      if (!isAvailable) {
        return reply.status(503).send({
          error: 'Service Unavailable',
          message: 'Transcription service is currently unavailable',
        });
      }

      const results = await transcribeBatch(momentIds, userId, language, true);

      const successful = results.filter(r => !r.error).length;
      const failed = results.filter(r => r.error).length;

      return {
        results: results.map(r => ({
          momentId: r.momentId,
          transcript: r.result?.transcript,
          language: r.result?.language,
          error: r.error,
        })),
        successful,
        failed,
      };

    } catch (error) {
      fastify.log.error('Batch transcription failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to transcribe moments',
      });
    }
  });

  /**
   * Get transcript for a moment
   * GET /api/transcription/:momentId
   */
  fastify.get('/:momentId', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(getMomentTranscriptSchema),
      response: {
        200: zodToJsonSchema(z.object({
          transcript: z.string(),
          language: z.string().nullable(),
          createdAt: z.string(),
        })),
        404: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { momentId } = request.params;
    const userId = (request.user as any).userId;

    try {
      const transcript = await getTranscript(momentId, userId);

      if (!transcript) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Transcript not found for this moment',
        });
      }

      return {
        transcript: transcript.transcript,
        language: transcript.language,
        createdAt: transcript.createdAt.toISOString(),
      };

    } catch (error) {
      fastify.log.error('Get transcript failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to retrieve transcript',
      });
    }
  });

  /**
   * Get transcription statistics for user
   * GET /api/transcription/stats
   */
  fastify.get('/stats', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          totalMoments: z.number(),
          transcribed: z.number(),
          pending: z.number(),
          percentageComplete: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = (request.user as any).userId;

    try {
      const stats = await getTranscriptionStats(userId);
      return stats;

    } catch (error) {
      fastify.log.error('Get transcription stats failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to retrieve transcription statistics',
      });
    }
  });

  /**
   * Auto-transcribe pending moments
   * POST /api/transcription/auto-transcribe
   */
  fastify.post('/auto-transcribe', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(z.object({
        maxBatch: z.number().min(1).max(50).optional().default(10),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          total: z.number(),
          successful: z.number(),
          failed: z.number(),
          errors: z.array(z.string()),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = (request.user as any).userId;
    const { maxBatch = 10 } = request.body || {};

    try {
      // Check transcription limit for user's billing tier
      const subscriptionInfo = await StripeBillingService.getSubscriptionInfo(userId);
      const limitCheck = await checkTranscriptionLimit(userId, subscriptionInfo.tier);
      if (!limitCheck.allowed) {
        return reply.status(403).send({
          error: 'Limit Reached',
          message: limitCheck.upgradePrompt?.message || 'Transcription hour limit reached for your plan',
          upgradeUrl: limitCheck.upgradePrompt?.actionUrl,
        });
      }

      const isAvailable = await isTranscriptionServiceAvailable();
      if (!isAvailable) {
        return reply.status(503).send({
          error: 'Service Unavailable',
          message: 'Transcription service is currently unavailable',
        });
      }

      const result = await autoTranscribePendingMoments(userId, maxBatch);
      return result;

    } catch (error) {
      fastify.log.error('Auto-transcribe failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to auto-transcribe pending moments',
      });
    }
  });

  /**
   * Check transcription service health
   * GET /api/transcription/health
   */
  fastify.get('/health', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          available: z.boolean(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    try {
      const isAvailable = await isTranscriptionServiceAvailable();

      return {
        available: isAvailable,
        message: isAvailable
          ? 'Transcription service is available'
          : 'Transcription service is currently unavailable',
      };

    } catch (error) {
      fastify.log.error('Health check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to check transcription service health',
      });
    }
  });
}
