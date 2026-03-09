/**
 * Upload routes for Flashit Web API
 * Handles S3 signed URL generation and media upload coordination
 *
 * @doc.type route
 * @doc.purpose Provide secure media upload endpoints with S3 integration
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { randomUUID } from 'crypto';
import { requireAuth } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';
import { getStorageService } from '../services/storage/storage-service';
import { WhisperTranscriptionService } from '../services/transcription/whisper-service.js';
import { S3Client, HeadObjectCommand } from '@aws-sdk/client-s3';

const s3 = new S3Client({
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
  region: process.env.AWS_REGION || 'us-east-1',
});

const S3_BUCKET = process.env.S3_BUCKET || 'flashit-uploads';
const MAX_FILE_SIZE = 50 * 1024 * 1024; // 50MB
const SIGNED_URL_EXPIRES = 15 * 60; // 15 minutes

// Get storage service instance
const storage = getStorageService();

// Validation schemas
const presignedUrlSchema = z.object({
  fileName: z.string().min(1).max(255),
  fileType: z.string().regex(/^(audio|video|image)\/(mp4|webm|wav|mpeg|mp3|jpeg|jpg|png|gif)$/i),
  fileSize: z.number().min(1).max(MAX_FILE_SIZE),
  momentId: z.string().uuid(),
});

const uploadCompleteSchema = z.object({
  uploadId: z.string().uuid(),
  s3Key: z.string().min(1).max(1024),
  actualSize: z.number().min(1).max(MAX_FILE_SIZE),
});

// Rate limiting map (in production, use Redis)
const uploadAttempts = new Map<string, { count: number; lastAttempt: number }>();
const RATE_LIMIT_WINDOW = 60 * 1000; // 1 minute
const RATE_LIMIT_MAX_ATTEMPTS = 10;

export default async function uploadRoutes(fastify: FastifyInstance) {
  // Rate limiting middleware for uploads
  const rateLimitUpload = async (userId: string) => {
    const now = Date.now();
    const userAttempts = uploadAttempts.get(userId);

    if (!userAttempts) {
      uploadAttempts.set(userId, { count: 1, lastAttempt: now });
      return true;
    }

    if (now - userAttempts.lastAttempt > RATE_LIMIT_WINDOW) {
      // Reset window
      uploadAttempts.set(userId, { count: 1, lastAttempt: now });
      return true;
    }

    if (userAttempts.count >= RATE_LIMIT_MAX_ATTEMPTS) {
      return false;
    }

    userAttempts.count++;
    userAttempts.lastAttempt = now;
    return true;
  };

  /**
   * Generate presigned URL for S3 upload
   * POST /api/upload/presigned-url
   */
  fastify.post('/presigned-url', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(presignedUrlSchema),
      response: {
        200: zodToJsonSchema(z.object({
          uploadId: z.string().uuid(),
          presignedUrl: z.string().url(),
          s3Key: z.string(),
          expiresIn: z.number(),
        })),
        429: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { fileName, fileType, fileSize, momentId } = request.body;
    const userId = request.user.userId;

    // Rate limiting
    const allowed = await rateLimitUpload(userId);
    if (!allowed) {
      return reply.status(429).send({
        error: 'Rate Limit Exceeded',
        message: 'Too many upload attempts. Please wait before trying again.',
      });
    }

    try {
      // Verify moment exists and user has access
      const moment = await prisma.moment.findFirst({
        where: {
          id: momentId,
          userId,
          deletedAt: null,
        },
        include: {
          sphere: {
            include: {
              sphereAccess: {
                where: {
                  userId,
                  revokedAt: null,
                },
              },
            },
          },
        },
      });

      if (!moment) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Moment not found or access denied',
        });
      }

      // Check if user has EDITOR or OWNER role
      const hasEditAccess = moment.sphere.sphereAccess.some(
        access => access.userId === userId && ['EDITOR', 'OWNER'].includes(access.role)
      );

      if (!hasEditAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Insufficient permissions to upload to this sphere',
        });
      }

      // Generate upload ID
      const uploadId = randomUUID();

      // Generate presigned URL using storage service
      const { uploadUrl, fileKey, expiresIn } = await storage.generatePresignedUrl({
        fileName,
        contentType: fileType,
        expiresIn: SIGNED_URL_EXPIRES,
      });

      // Store upload metadata
      await prisma.mediaReference.create({
        data: {
          id: uploadId,
          momentId,
          s3Bucket: process.env.S3_BUCKET || 'local',
          s3Key: fileKey,
          fileName,
          mimeType: fileType,
          sizeBytes: BigInt(fileSize),
          uploadStatus: 'PENDING',
          expiresAt: new Date(Date.now() + expiresIn * 1000),
        },
      });

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: 'MEDIA_UPLOAD_INITIATED',
          userId,
          momentId,
          actor: request.user.email,
          action: 'UPLOAD_INITIATED',
          resourceType: 'media_reference',
          resourceId: uploadId,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            fileName,
            fileType,
            fileSize,
            fileKey,
          },
        },
      });

      return {
        uploadId,
        uploadUrl,
        fileKey,
        expiresIn,
        maxFileSize: MAX_FILE_SIZE,
      };

    } catch (error) {
      fastify.log.error('Presigned URL generation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to generate upload URL',
      });
    }
  });

  /**
   * Complete upload - verify file was uploaded to S3
   * POST /api/upload/complete
   */
  fastify.post('/complete', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(uploadCompleteSchema),
      response: {
        200: zodToJsonSchema(z.object({
          mediaReference: z.object({
            id: z.string(),
            s3Key: z.string(),
            fileName: z.string(),
            mimeType: z.string(),
            sizeBytes: z.number(),
            status: z.string(),
          }),
        })),
      },
    },
  }, async (request, reply) => {
    const { uploadId, s3Key, actualSize } = request.body;
    const userId = request.user.userId;

    try {
      // Get media reference
      const mediaRef = await prisma.mediaReference.findUnique({
        where: { id: uploadId },
        include: {
          moment: {
            include: {
              sphere: {
                include: {
                  sphereAccess: {
                    where: {
                      userId,
                      revokedAt: null,
                    },
                  },
                },
              },
            },
          },
        },
      });

      if (!mediaRef) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload not found',
        });
      }

      // Verify ownership
      const hasAccess = mediaRef.moment.sphere.sphereAccess.some(
        access => access.userId === userId && ['EDITOR', 'OWNER'].includes(access.role)
      );

      if (!hasAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Access denied',
        });
      }

      // Verify file exists in S3
      try {
        const headResult = await s3.send(new HeadObjectCommand({
          Bucket: S3_BUCKET,
          Key: s3Key,
        }));

        // Update media reference
        const updatedMediaRef = await prisma.mediaReference.update({
          where: { id: uploadId },
          data: {
            uploadStatus: 'COMPLETED',
            sizeBytes: actualSize,
            uploadedAt: new Date(),
            s3ETag: headResult.ETag,
            s3LastModified: headResult.LastModified,
          },
        });

        // Create moment-media link
        await prisma.momentMedia.create({
          data: {
            momentId: mediaRef.momentId,
            mediaReferenceId: uploadId,
            mediaType: mediaRef.mimeType.startsWith('video') ? 'VIDEO' : 'AUDIO',
            order: 1, // For now, single media per moment
          },
        });

        // Trigger Auto-Transcription for Audio/Video
        if (mediaRef.mimeType.startsWith('audio') || mediaRef.mimeType.startsWith('video')) {
          try {
            fastify.log.info(`Enqueuing transcription for upload ${uploadId}`);
            await WhisperTranscriptionService.enqueueTranscription({
              mediaReferenceId: uploadId,
              s3Bucket: mediaRef.s3Bucket || process.env.S3_BUCKET || 'flashit-uploads',
              s3Key: mediaRef.s3Key,
              userId,
              momentId: mediaRef.momentId,
              audioFormat: mediaRef.mimeType.split('/')[1] || 'mp3',
              priority: 'normal',
            });
          } catch (tError) {
            // Log but don't fail the upload completion
            fastify.log.error('Failed to enqueue transcription:', tError);
          }
        }

        // Audit log
        await prisma.auditEvent.create({
          data: {
            eventType: 'MEDIA_UPLOAD_COMPLETED',
            userId,
            momentId: mediaRef.momentId,
            actor: request.user.email,
            action: 'UPLOAD_COMPLETED',
            resourceType: 'media_reference',
            resourceId: uploadId,
            ipAddress: request.ip,
            userAgent: request.headers['user-agent'] || 'Unknown',
            details: {
              s3Key,
              actualSize,
              s3ETag: headResult.ETag,
            },
          },
        });

        return {
          mediaReference: {
            id: updatedMediaRef.id,
            s3Key: updatedMediaRef.s3Key,
            fileName: updatedMediaRef.fileName,
            mimeType: updatedMediaRef.mimeType,
            sizeBytes: updatedMediaRef.sizeBytes,
            status: updatedMediaRef.uploadStatus,
          },
        };

      } catch (s3Error: any) {
        // File not found in S3
        await prisma.mediaReference.update({
          where: { id: uploadId },
          data: { uploadStatus: 'FAILED' },
        });

        return reply.status(400).send({
          error: 'Upload Verification Failed',
          message: 'File not found in S3',
        });
      }

    } catch (error) {
      fastify.log.error('Upload completion failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to complete upload',
      });
    }
  });

  /**
   * Get upload status
   * GET /api/upload/:uploadId/status
   */
  fastify.get('/:uploadId/status', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        uploadId: z.string().uuid(),
      })),
    },
  }, async (request, reply) => {
    const { uploadId } = request.params;
    const userId = request.user.userId;

    try {
      const mediaRef = await prisma.mediaReference.findFirst({
        where: {
          id: uploadId,
          moment: {
            sphere: {
              sphereAccess: {
                some: {
                  userId,
                  revokedAt: null,
                },
              },
            },
          },
        },
      });

      if (!mediaRef) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload not found',
        });
      }

      return {
        uploadId,
        status: mediaRef.uploadStatus,
        fileName: mediaRef.fileName,
        mimeType: mediaRef.mimeType,
        sizeBytes: mediaRef.sizeBytes,
        uploadedAt: mediaRef.uploadedAt,
        expiresAt: mediaRef.expiresAt,
      };

    } catch (error) {
      fastify.log.error('Upload status check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get upload status',
      });
    }
  });

  /**
   * Cancel/delete upload
   * DELETE /api/upload/:uploadId
   */
  fastify.delete('/:uploadId', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        uploadId: z.string().uuid(),
      })),
    },
  }, async (request, reply) => {
    const { uploadId } = request.params;
    const userId = request.user.userId;

    try {
      const mediaRef = await prisma.mediaReference.findFirst({
        where: {
          id: uploadId,
          moment: {
            userId, // Only owner can delete
          },
        },
      });

      if (!mediaRef) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload not found',
        });
      }

      // Delete from S3 if it exists
      if (mediaRef.uploadStatus === 'COMPLETED') {
        try {
          await s3.deleteObject({
            Bucket: S3_BUCKET,
            Key: mediaRef.s3Key,
          }).promise();
        } catch (s3Error) {
          fastify.log.warn('Failed to delete from S3:', s3Error);
          // Continue with database cleanup
        }
      }

      // Delete moment-media link if exists
      await prisma.momentMedia.deleteMany({
        where: { mediaReferenceId: uploadId },
      });

      // Delete media reference
      await prisma.mediaReference.delete({
        where: { id: uploadId },
      });

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: 'MEDIA_DELETED',
          userId,
          momentId: mediaRef.momentId,
          actor: request.user.email,
          action: 'MEDIA_DELETED',
          resourceType: 'media_reference',
          resourceId: uploadId,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            s3Key: mediaRef.s3Key,
            fileName: mediaRef.fileName,
          },
        },
      });

      return reply.status(204).send();

    } catch (error) {
      fastify.log.error('Upload deletion failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to delete upload',
      });
    }
  });
}
