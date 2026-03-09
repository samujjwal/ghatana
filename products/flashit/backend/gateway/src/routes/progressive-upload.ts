/**
 * Progressive upload routes for Flashit Web API
 * Handles chunked media uploads and live streaming
 *
 * @doc.type route
 * @doc.purpose Support progressive and live media upload modes
 * @doc.layer product
 * @doc.pattern APIRoute
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import AWS from 'aws-sdk';
import { randomUUID } from 'crypto';
import { requireAuth } from '../lib/auth.js';
import { prisma } from '../lib/prisma.js';

// S3 configuration
const s3 = new AWS.S3({
  accessKeyId: process.env.AWS_ACCESS_KEY_ID,
  secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY,
  region: process.env.AWS_REGION || 'us-east-1',
  signatureVersion: 'v4',
});

const S3_BUCKET = process.env.S3_BUCKET || 'flashit-media-dev';

// Validation schemas
const initProgressiveUploadSchema = z.object({
  fileName: z.string().min(1).max(255),
  fileType: z.string().regex(/^(audio|video)\/(mp4|webm|wav)$/),
  totalSize: z.number().min(1).max(500 * 1024 * 1024), // 500MB max for progressive
  chunkCount: z.number().min(1).max(1000),
  momentId: z.string().uuid(),
});

const uploadChunkSchema = z.object({
  uploadId: z.string().uuid(),
  chunkIndex: z.number().min(0),
  isLastChunk: z.boolean().optional().default(false),
});

const completeProgressiveUploadSchema = z.object({
  uploadId: z.string().uuid(),
  chunkHashes: z.array(z.string()).min(1), // SHA256 hashes for verification
});

// In-memory progressive upload tracking (in production, use Redis)
interface ProgressiveUploadSession {
  id: string;
  momentId: string;
  userId: string;
  fileName: string;
  mimeType: string;
  totalSize: number;
  chunkCount: number;
  uploadedChunks: Set<number>;
  s3Keys: Map<number, string>; // chunk index -> S3 key
  multipartUploadId?: string; // S3 multipart upload ID
  parts: Array<{ ETag: string; PartNumber: number }>; // S3 parts
  createdAt: number;
  lastActivity: number;
}

const progressiveSessions = new Map<string, ProgressiveUploadSession>();

// Cleanup expired sessions (run periodically)
const cleanupExpiredSessions = () => {
  const now = Date.now();
  const EXPIRY_TIME = 24 * 60 * 60 * 1000; // 24 hours

  for (const [sessionId, session] of progressiveSessions.entries()) {
    if (now - session.lastActivity > EXPIRY_TIME) {
      // Cleanup S3 multipart upload if exists
      if (session.multipartUploadId) {
        s3.abortMultipartUpload({
          Bucket: S3_BUCKET,
          Key: `progressive/${session.userId}/${session.id}/${session.fileName}`,
          UploadId: session.multipartUploadId,
        }).promise().catch(console.error);
      }
      progressiveSessions.delete(sessionId);
    }
  }
};

// Run cleanup every hour
setInterval(cleanupExpiredSessions, 60 * 60 * 1000);

export default async function progressiveUploadRoutes(fastify: FastifyInstance) {

  /**
   * Initialize progressive upload session
   * POST /api/progressive/init
   */
  fastify.post('/init', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(initProgressiveUploadSchema),
      response: {
        200: zodToJsonSchema(z.object({
          uploadId: z.string().uuid(),
          chunkSize: z.number(),
          s3MultipartUploadId: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { fileName, fileType, totalSize, chunkCount, momentId } = request.body;
    const userId = request.user.userId;

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

      const hasEditAccess = moment.sphere.sphereAccess.some(
        access => access.userId === userId && ['EDITOR', 'OWNER'].includes(access.role)
      );

      if (!hasEditAccess) {
        return reply.status(403).send({
          error: 'Forbidden',
          message: 'Insufficient permissions to upload to this sphere',
        });
      }

      // Create progressive upload session
      const uploadId = randomUUID();
      const s3Key = `progressive/${userId}/${uploadId}/${fileName}`;

      // Initialize S3 multipart upload
      const multipartUpload = await s3.createMultipartUpload({
        Bucket: S3_BUCKET,
        Key: s3Key,
        ContentType: fileType,
        Metadata: {
          userId,
          momentId,
          uploadId,
          originalFileName: fileName,
          uploadMode: 'progressive',
        },
      }).promise();

      // Create session
      const session: ProgressiveUploadSession = {
        id: uploadId,
        momentId,
        userId,
        fileName,
        mimeType: fileType,
        totalSize,
        chunkCount,
        uploadedChunks: new Set(),
        s3Keys: new Map(),
        multipartUploadId: multipartUpload.UploadId!,
        parts: [],
        createdAt: Date.now(),
        lastActivity: Date.now(),
      };

      progressiveSessions.set(uploadId, session);

      // Store in database
      await prisma.mediaReference.create({
        data: {
          id: uploadId,
          momentId,
          s3Bucket: S3_BUCKET,
          s3Key,
          fileName,
          mimeType: fileType,
          sizeBytes: totalSize,
          uploadStatus: 'PENDING',
          expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
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
            fileSize: totalSize,
            s3Key,
          },
        },
      });

      return {
        uploadId,
        chunkSize: Math.ceil(totalSize / chunkCount),
        s3MultipartUploadId: multipartUpload.UploadId!,
      };

    } catch (error) {
      fastify.log.error('Progressive upload initialization failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to initialize progressive upload',
      });
    }
  });

  /**
   * Upload chunk
   * POST /api/progressive/chunk
   */
  fastify.post('/chunk', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(uploadChunkSchema),
      consumes: ['multipart/form-data'],
    },
  }, async (request, reply) => {
    const { uploadId, chunkIndex, isLastChunk } = request.body;
    const userId = request.user.userId;

    try {
      const session = progressiveSessions.get(uploadId);
      if (!session || session.userId !== userId) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload session not found',
        });
      }

      // Check if chunk already uploaded
      if (session.uploadedChunks.has(chunkIndex)) {
        return reply.status(409).send({
          error: 'Conflict',
          message: 'Chunk already uploaded',
        });
      }

      // Get chunk data from multipart form
      const data = await request.file();
      if (!data) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: 'No chunk data provided',
        });
      }

      const chunkBuffer = await data.toBuffer();

      // Upload chunk to S3 as part of multipart upload
      const partNumber = chunkIndex + 1; // S3 part numbers start at 1
      const uploadPartResponse = await s3.uploadPart({
        Bucket: S3_BUCKET,
        Key: `progressive/${session.userId}/${session.id}/${session.fileName}`,
        UploadId: session.multipartUploadId!,
        PartNumber: partNumber,
        Body: chunkBuffer,
      }).promise();

      // Track uploaded part
      session.parts.push({
        ETag: uploadPartResponse.ETag!,
        PartNumber: partNumber,
      });

      session.uploadedChunks.add(chunkIndex);
      session.lastActivity = Date.now();

      const progress = {
        uploadedChunks: session.uploadedChunks.size,
        totalChunks: session.chunkCount,
        uploadedBytes: Array.from(session.uploadedChunks).length * Math.ceil(session.totalSize / session.chunkCount),
        totalBytes: session.totalSize,
        percentage: Math.round((session.uploadedChunks.size / session.chunkCount) * 100),
      };

      return {
        chunkIndex,
        uploaded: true,
        progress,
        isComplete: session.uploadedChunks.size === session.chunkCount,
      };

    } catch (error) {
      fastify.log.error('Chunk upload failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to upload chunk',
      });
    }
  });

  /**
   * Complete progressive upload
   * POST /api/progressive/complete
   */
  fastify.post('/complete', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(completeProgressiveUploadSchema),
    },
  }, async (request, reply) => {
    const { uploadId, chunkHashes } = request.body;
    const userId = request.user.userId;

    try {
      const session = progressiveSessions.get(uploadId);
      if (!session || session.userId !== userId) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload session not found',
        });
      }

      // Verify all chunks uploaded
      if (session.uploadedChunks.size !== session.chunkCount) {
        return reply.status(400).send({
          error: 'Bad Request',
          message: `Missing chunks. Uploaded: ${session.uploadedChunks.size}, Expected: ${session.chunkCount}`,
        });
      }

      // Complete S3 multipart upload
      const s3Key = `progressive/${session.userId}/${session.id}/${session.fileName}`;

      // Sort parts by part number
      const sortedParts = session.parts.sort((a, b) => a.PartNumber - b.PartNumber);

      const completeUploadResponse = await s3.completeMultipartUpload({
        Bucket: S3_BUCKET,
        Key: s3Key,
        UploadId: session.multipartUploadId!,
        MultipartUpload: {
          Parts: sortedParts,
        },
      }).promise();

      // Update media reference
      const updatedMediaRef = await prisma.mediaReference.update({
        where: { id: uploadId },
        data: {
          uploadStatus: 'COMPLETED',
          uploadedAt: new Date(),
          s3ETag: completeUploadResponse.ETag,
        },
      });

      // Create moment-media link
      await prisma.momentMedia.create({
        data: {
          momentId: session.momentId,
          mediaReferenceId: uploadId,
          mediaType: session.mimeType.startsWith('video') ? 'VIDEO' : 'AUDIO',
          order: 1,
        },
      });

      // Clean up session
      progressiveSessions.delete(uploadId);

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: 'MEDIA_UPLOAD_COMPLETED',
          userId,
          momentId: updatedMediaRef.momentId,
          actor: request.user.email,
          action: 'UPLOAD_COMPLETED',
          resourceType: 'media_reference',
          resourceId: uploadId,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            s3Key,
            transcodingJobId: null,
            fileSize: updatedMediaRef.sizeBytes,
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
        s3Location: completeUploadResponse.Location,
      };

    } catch (error) {
      fastify.log.error('Progressive upload completion failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to complete progressive upload',
      });
    }
  });

  /**
   * Get upload progress
   * GET /api/progressive/:uploadId/progress
   */
  fastify.get('/:uploadId/progress', {
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
      const session = progressiveSessions.get(uploadId);
      if (!session || session.userId !== userId) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload session not found',
        });
      }

      const progress = {
        uploadId,
        uploadedChunks: session.uploadedChunks.size,
        totalChunks: session.chunkCount,
        uploadedBytes: Array.from(session.uploadedChunks).length * Math.ceil(session.totalSize / session.chunkCount),
        totalBytes: session.totalSize,
        percentage: Math.round((session.uploadedChunks.size / session.chunkCount) * 100),
        missingChunks: Array.from({ length: session.chunkCount }, (_, i) => i).filter(
          i => !session.uploadedChunks.has(i)
        ),
        isComplete: session.uploadedChunks.size === session.chunkCount,
        lastActivity: new Date(session.lastActivity).toISOString(),
      };

      return progress;

    } catch (error) {
      fastify.log.error('Progress check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get upload progress',
      });
    }
  });

  /**
   * Cancel progressive upload
   * DELETE /api/progressive/:uploadId
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
      const session = progressiveSessions.get(uploadId);
      if (!session || session.userId !== userId) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Upload session not found',
        });
      }

      // Abort S3 multipart upload
      if (session.multipartUploadId) {
        await s3.abortMultipartUpload({
          Bucket: S3_BUCKET,
          Key: `progressive/${session.userId}/${session.id}/${session.fileName}`,
          UploadId: session.multipartUploadId,
        }).promise();
      }

      // Clean up database
      await prisma.mediaReference.delete({
        where: { id: uploadId },
      });

      // Clean up session
      progressiveSessions.delete(uploadId);

      // Audit log
      await prisma.auditEvent.create({
        data: {
          eventType: AuditEventType.MEDIA_DELETED,
          userId,
          momentId: session.momentId,
          actor: request.user.email,
          action: 'PROGRESSIVE_UPLOAD_CANCELLED',
          resourceType: 'media_reference',
          resourceId: uploadId,
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'Unknown',
          details: {
            fileName: session.fileName,
            totalSize: session.totalSize,
            uploadedChunks: session.uploadedChunks.size,
            totalChunks: session.chunkCount,
            mode: 'progressive',
          },
        },
      });

      return reply.status(204).send();

    } catch (error) {
      fastify.log.error('Progressive upload cancellation failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to cancel progressive upload',
      });
    }
  });
}
