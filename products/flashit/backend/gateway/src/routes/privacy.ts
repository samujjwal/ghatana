/**
 * Data Privacy API Routes for Flashit
 * GDPR-compliant data export, deletion, and privacy management
 *
 * @doc.type routes
 * @doc.purpose Privacy and data rights management API
 * @doc.layer product
 * @doc.pattern APIRoutes
 */

import { FastifyInstance } from 'fastify';
import { z } from 'zod';
import { zodToJsonSchema } from 'zod-to-json-schema';
import { requireAuth } from '../lib/auth.js';
import { DataExportService } from '../services/data-export/export-service';
import { SecureDataDeletionService } from '../services/data-deletion/deletion-service';
import { prisma } from '../lib/prisma.js';
import { createReadStream } from 'fs';
import { stat } from 'fs/promises';

// Validation schemas
const exportRequestSchema = z.object({
  format: z.enum(['json', 'csv', 'pdf', 'zip']).default('json'),
  scope: z.enum(['all', 'sphere', 'dateRange']).default('all'),
  includeMedia: z.boolean().default(false),
  encryption: z.object({
    enabled: z.boolean().default(false),
    password: z.string().min(8).max(100).optional(),
  }).optional(),
  filters: z.object({
    sphereIds: z.array(z.string().uuid()).optional(),
    startDate: z.string().datetime().optional(),
    endDate: z.string().datetime().optional(),
    includedTypes: z.array(z.enum(['moments', 'spheres', 'analytics', 'collaborations', 'comments'])).optional(),
  }).optional(),
});

const deletionRequestSchema = z.object({
  deletionType: z.enum(['user', 'sphere', 'moments', 'partial']),
  scope: z.object({
    sphereIds: z.array(z.string().uuid()).optional(),
    momentIds: z.array(z.string().uuid()).optional(),
    dateRange: z.object({
      start: z.string().datetime(),
      end: z.string().datetime(),
    }).optional(),
    includeMedia: z.boolean().default(true),
    includeAnalytics: z.boolean().default(false),
    includeCollaborations: z.boolean().default(true),
  }),
  immediate: z.boolean().default(false),
});

const verifyDeletionSchema = z.object({
  requestId: z.string().uuid(),
  token: z.string(),
});

const privacySettingsSchema = z.object({
  dataRetentionDays: z.number().int().min(30).max(3650).optional(), // 30 days to 10 years
  autoDeleteEnabled: z.boolean().optional(),
  analyticsOptOut: z.boolean().optional(),
  thirdPartyAharingOptOut: z.boolean().optional(),
  marketingEmailsOptOut: z.boolean().optional(),
  exportFrequencyLimit: z.number().int().min(1).max(20).optional(),
  deletionCooldownDays: z.number().int().min(1).max(90).optional(),
});

const consentUpdateSchema = z.object({
  consentType: z.string(),
  consentGiven: z.boolean(),
  consentVersion: z.string(),
});

export default async function privacyRoutes(fastify: FastifyInstance) {

  /**
   * Request data export
   * POST /api/privacy/export
   */
  fastify.post('/export', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(exportRequestSchema),
      response: {
        200: zodToJsonSchema(z.object({
          exportId: z.string(),
          message: z.string(),
          estimatedCompletion: z.string(),
        })),
        429: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string(),
          retryAfter: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const exportOptions = request.body;

    try {
      // Check export eligibility (rate limiting)
      const eligible = await prisma.$queryRaw`
        SELECT check_export_eligibility(${userId}::uuid) as eligible
      ` as any[];

      if (!eligible[0]?.eligible) {
        return reply.status(429).send({
          error: 'Rate Limit Exceeded',
          message: 'You have exceeded the maximum number of exports allowed per month',
          retryAfter: 30 * 24 * 60 * 60, // 30 days
        });
      }

      // Convert string dates to Date objects
      const filters = exportOptions.filters ? {
        ...exportOptions.filters,
        startDate: exportOptions.filters.startDate ? new Date(exportOptions.filters.startDate) : undefined,
        endDate: exportOptions.filters.endDate ? new Date(exportOptions.filters.endDate) : undefined,
      } : undefined;

      // Request export
      const exportId = await DataExportService.requestExport(userId, {
        format: exportOptions.format,
        scope: exportOptions.scope,
        includeMedia: exportOptions.includeMedia,
        encryption: exportOptions.encryption,
        filters,
      });

      // Estimate completion time based on scope and format
      const estimatedMinutes = exportOptions.scope === 'all' ?
        (exportOptions.includeMedia ? 15 : 5) :
        (exportOptions.includeMedia ? 5 : 2);

      const estimatedCompletion = new Date(Date.now() + estimatedMinutes * 60 * 1000).toISOString();

      return {
        exportId,
        message: `Export request submitted successfully. ${exportOptions.format.toUpperCase()} export will be ready in approximately ${estimatedMinutes} minutes.`,
        estimatedCompletion,
      };

    } catch (error: any) {
      fastify.log.error('Export request failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to process export request',
      });
    }
  });

  /**
   * Get export status
   * GET /api/privacy/export/:exportId/status
   */
  fastify.get('/export/:exportId/status', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        exportId: z.string().uuid(),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          exportId: z.string(),
          status: z.string(),
          filePath: z.string().optional(),
          downloadUrl: z.string().optional(),
          fileSize: z.number().optional(),
          checksum: z.string().optional(),
          createdAt: z.string(),
          completedAt: z.string().optional(),
          error: z.string().optional(),
        })),
      },
    },
  }, async (request, reply) => {
    const { exportId } = request.params;
    const userId = request.user.userId;

    try {
      const exportStatus = await DataExportService.getExportStatus(exportId, userId);

      if (!exportStatus) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Export request not found',
        });
      }

      return {
        exportId: exportStatus.exportId,
        status: exportStatus.status,
        filePath: exportStatus.filePath,
        downloadUrl: exportStatus.downloadUrl,
        fileSize: exportStatus.fileSize,
        checksum: exportStatus.checksum,
        createdAt: exportStatus.createdAt.toISOString(),
        completedAt: exportStatus.completedAt?.toISOString(),
        error: exportStatus.error,
      };

    } catch (error: any) {
      fastify.log.error('Export status check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get export status',
      });
    }
  });

  /**
   * Download export file
   * GET /api/privacy/export/:exportId/download
   */
  fastify.get('/export/:exportId/download', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        exportId: z.string().uuid(),
      })),
      querystring: zodToJsonSchema(z.object({
        token: z.string().optional(),
      })),
    },
  }, async (request, reply) => {
    const { exportId } = request.params;
    const { token } = request.query;
    const userId = request.user.userId;

    try {
      const exportStatus = await DataExportService.getExportStatus(exportId, userId);

      if (!exportStatus) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Export not found',
        });
      }

      if (exportStatus.status !== 'completed') {
        return reply.status(400).send({
          error: 'Bad Request',
          message: `Export is not ready. Current status: ${exportStatus.status}`,
        });
      }

      if (!exportStatus.filePath) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Export file not found',
        });
      }

      // Verify file exists
      const fileStats = await stat(exportStatus.filePath);

      // Determine content type and filename based on format
      const extension = exportStatus.filePath.split('.').pop();
      const contentTypes = {
        json: 'application/json',
        csv: 'text/csv',
        zip: 'application/zip',
        pdf: 'application/pdf',
      };

      const contentType = contentTypes[extension as keyof typeof contentTypes] || 'application/octet-stream';
      const filename = `flashit-export-${exportId}.${extension}`;

      return reply
        .type(contentType)
        .header('Content-Disposition', `attachment; filename="${filename}"`)
        .header('Content-Length', fileStats.size.toString())
        .send(createReadStream(exportStatus.filePath));

    } catch (error: any) {
      fastify.log.error('Export download failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to download export',
      });
    }
  });

  /**
   * Request data deletion
   * POST /api/privacy/deletion
   */
  fastify.post('/deletion', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(deletionRequestSchema),
      response: {
        200: zodToJsonSchema(z.object({
          requestId: z.string(),
          message: z.string(),
          verificationRequired: z.boolean(),
          verificationExpiresAt: z.string().optional(),
        })),
        429: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string(),
          cooldownEndsAt: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const deletionOptions = request.body;

    try {
      // Check deletion cooldown
      const canDelete = await prisma.$queryRaw`
        SELECT check_deletion_cooldown(${userId}::uuid) as can_delete
      ` as any[];

      if (!canDelete[0]?.can_delete) {
        const lastDeletion = await prisma.dataDeletionRequest.findFirst({
          where: { userId, status: 'completed' },
          orderBy: { createdAt: 'desc' },
          select: { createdAt: true },
        });

        const cooldownEndsAt = lastDeletion
          ? new Date(lastDeletion.createdAt.getTime() + 30 * 24 * 60 * 60 * 1000)
          : new Date();

        return reply.status(429).send({
          error: 'Cooldown Active',
          message: 'You must wait before making another deletion request',
          cooldownEndsAt: cooldownEndsAt.toISOString(),
        });
      }

      // Convert date range if provided
      const scope = {
        ...deletionOptions.scope,
        dateRange: deletionOptions.scope.dateRange ? {
          start: new Date(deletionOptions.scope.dateRange.start),
          end: new Date(deletionOptions.scope.dateRange.end),
        } : undefined,
      };

      // Request deletion
      const requestId = await SecureDataDeletionService.requestDeletion(
        userId,
        deletionOptions.deletionType,
        scope,
        {
          verificationRequired: !deletionOptions.immediate,
          immediate: deletionOptions.immediate,
        }
      );

      const verificationRequired = !deletionOptions.immediate;
      const verificationExpiresAt = verificationRequired
        ? new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString()
        : undefined;

      return {
        requestId,
        message: verificationRequired
          ? 'Deletion request submitted. Please check your email to verify.'
          : 'Deletion request submitted and will be processed shortly.',
        verificationRequired,
        verificationExpiresAt,
      };

    } catch (error: any) {
      fastify.log.error('Deletion request failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: error.message || 'Failed to process deletion request',
      });
    }
  });

  /**
   * Verify deletion request
   * POST /api/privacy/deletion/verify
   */
  fastify.post('/deletion/verify', {
    schema: {
      body: zodToJsonSchema(verifyDeletionSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          requestId: z.string(),
        })),
        404: zodToJsonSchema(z.object({
          error: z.string(),
          message: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const { requestId, token } = request.body;

    try {
      await SecureDataDeletionService.verifyDeletion(requestId, token);

      return {
        message: 'Deletion request verified successfully. Processing will begin shortly.',
        requestId,
      };

    } catch (error: any) {
      fastify.log.error('Deletion verification failed:', error);
      return reply.status(404).send({
        error: 'Verification Failed',
        message: error.message || 'Invalid or expired verification token',
      });
    }
  });

  /**
   * Get deletion status
   * GET /api/privacy/deletion/:requestId/status
   */
  fastify.get('/deletion/:requestId/status', {
    onRequest: [requireAuth],
    schema: {
      params: zodToJsonSchema(z.object({
        requestId: z.string().uuid(),
      })),
      response: {
        200: zodToJsonSchema(z.object({
          requestId: z.string(),
          status: z.string(),
          deletionSummary: z.any().optional(),
          createdAt: z.string(),
          completedAt: z.string().optional(),
          error: z.string().optional(),
        })),
      },
    },
  }, async (request, reply) => {
    const { requestId } = request.params;
    const userId = request.user.userId;

    try {
      const deletionRequest = await prisma.dataDeletionRequest.findFirst({
        where: { id: requestId, userId },
      });

      if (!deletionRequest) {
        return reply.status(404).send({
          error: 'Not Found',
          message: 'Deletion request not found',
        });
      }

      return {
        requestId: deletionRequest.id,
        status: deletionRequest.status,
        deletionSummary: deletionRequest.deletionSummary
          ? JSON.parse(deletionRequest.deletionSummary as string)
          : undefined,
        createdAt: deletionRequest.createdAt.toISOString(),
        completedAt: deletionRequest.completedAt?.toISOString(),
        error: deletionRequest.error,
      };

    } catch (error: any) {
      fastify.log.error('Deletion status check failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get deletion status',
      });
    }
  });

  /**
   * Get/Update privacy settings
   * GET/PUT /api/privacy/settings
   */
  fastify.get('/settings', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          dataRetentionDays: z.number(),
          autoDeleteEnabled: z.boolean(),
          analyticsOptOut: z.boolean(),
          thirdPartySharingOptOut: z.boolean(),
          marketingEmailsOptOut: z.boolean(),
          dataProcessingConsent: z.boolean(),
          dataProcessingConsentDate: z.string().optional(),
          exportFrequencyLimit: z.number(),
          deletionCooldownDays: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      let settings = await prisma.privacySettings.findUnique({
        where: { userId },
      });

      // Create default settings if none exist
      if (!settings) {
        settings = await prisma.privacySettings.create({
          data: {
            userId,
            dataProcessingConsentDate: new Date(),
          },
        });
      }

      return {
        dataRetentionDays: settings.dataRetentionDays,
        autoDeleteEnabled: settings.autoDeleteEnabled,
        analyticsOptOut: settings.analyticsOptOut,
        thirdPartySharingOptOut: settings.thirdPartySharingOptOut,
        marketingEmailsOptOut: settings.marketingEmailsOptOut,
        dataProcessingConsent: settings.dataProcessingConsent,
        dataProcessingConsentDate: settings.dataProcessingConsentDate?.toISOString(),
        exportFrequencyLimit: settings.exportFrequencyLimit,
        deletionCooldownDays: settings.deletionCooldownDays,
      };

    } catch (error: any) {
      fastify.log.error('Privacy settings fetch failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get privacy settings',
      });
    }
  });

  fastify.put('/settings', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(privacySettingsSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          settings: z.any(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const updates = request.body;

    try {
      const settings = await prisma.privacySettings.upsert({
        where: { userId },
        create: {
          userId,
          ...updates,
          dataProcessingConsentDate: new Date(),
        },
        update: updates,
      });

      return {
        message: 'Privacy settings updated successfully',
        settings: {
          dataRetentionDays: settings.dataRetentionDays,
          autoDeleteEnabled: settings.autoDeleteEnabled,
          analyticsOptOut: settings.analyticsOptOut,
          thirdPartySharingOptOut: settings.thirdPartySharingOptOut,
          marketingEmailsOptOut: settings.marketingEmailsOptOut,
          dataProcessingConsent: settings.dataProcessingConsent,
          exportFrequencyLimit: settings.exportFrequencyLimit,
          deletionCooldownDays: settings.deletionCooldownDays,
        },
      };

    } catch (error: any) {
      fastify.log.error('Privacy settings update failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to update privacy settings',
      });
    }
  });

  /**
   * Update consent
   * POST /api/privacy/consent
   */
  fastify.post('/consent', {
    onRequest: [requireAuth],
    schema: {
      body: zodToJsonSchema(consentUpdateSchema),
      response: {
        200: zodToJsonSchema(z.object({
          message: z.string(),
          consentId: z.string(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;
    const { consentType, consentGiven, consentVersion } = request.body;

    try {
      // Withdraw previous consent of same type if giving new consent
      if (consentGiven) {
        await prisma.consentRecord.updateMany({
          where: {
            userId,
            consentType,
            withdrawnAt: null,
          },
          data: { withdrawnAt: new Date() },
        });
      }

      // Create new consent record
      const consentRecord = await prisma.consentRecord.create({
        data: {
          userId,
          consentType,
          consentVersion,
          consentGiven,
          consentMethod: 'api',
          ipAddress: request.ip,
          userAgent: request.headers['user-agent'] || 'unknown',
        },
      });

      // Update privacy settings if related to data processing
      if (consentType === 'data_processing') {
        await prisma.privacySettings.upsert({
          where: { userId },
          create: {
            userId,
            dataProcessingConsent: consentGiven,
            dataProcessingConsentDate: new Date(),
          },
          update: {
            dataProcessingConsent: consentGiven,
            dataProcessingConsentDate: new Date(),
          },
        });
      }

      return {
        message: `Consent ${consentGiven ? 'granted' : 'withdrawn'} successfully`,
        consentId: consentRecord.id,
      };

    } catch (error: any) {
      fastify.log.error('Consent update failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to update consent',
      });
    }
  });

  /**
   * Get data processing summary
   * GET /api/privacy/data-summary
   */
  fastify.get('/data-summary', {
    onRequest: [requireAuth],
    schema: {
      response: {
        200: zodToJsonSchema(z.object({
          summary: z.object({
            totalMoments: z.number(),
            totalSpheres: z.number(),
            totalAnalyticsRecords: z.number(),
            totalCollaborations: z.number(),
            dataRetentionStatus: z.object({
              momentsExpiringSoon: z.number(),
              spheresExpiringSoon: z.number(),
              oldestMoment: z.string().optional(),
              newestMoment: z.string().optional(),
            }),
            storageUsage: z.object({
              totalMediaFiles: z.number(),
              totalMediaSize: z.number(),
              estimatedDatabaseSize: z.number(),
            }),
          }),
          lastExport: z.string().optional(),
          activeDeletionRequests: z.number(),
        })),
      },
    },
  }, async (request, reply) => {
    const userId = request.user.userId;

    try {
      const [
        momentStats,
        sphereStats,
        analyticsStats,
        collaborationStats,
        retentionStats,
        mediaStats,
        lastExport,
        activeDeletions,
      ] = await Promise.all([
        // Moments statistics
        prisma.moment.aggregate({
          where: { userId, deletedAt: null },
          _count: { id: true },
          _min: { capturedAt: true },
          _max: { capturedAt: true },
        }),

        // Spheres statistics
        prisma.sphere.count({
          where: { createdByUserId: userId, deletedAt: null },
        }),

        // Analytics statistics
        prisma.analytics.userAnalytics.count({
          where: { userId },
        }),

        // Collaborations statistics
        prisma.collaboration.sphereShare.count({
          where: {
            OR: [
              { sharedByUserId: userId },
              { sharedWithUserId: userId },
            ],
            revokedAt: null,
          },
        }),

        // Retention statistics
        prisma.$queryRaw`
          SELECT
            COUNT(*) FILTER (WHERE m.retention_date <= CURRENT_DATE + INTERVAL '30 days') as moments_expiring_soon,
            COUNT(*) FILTER (WHERE s.retention_date <= CURRENT_DATE + INTERVAL '30 days') as spheres_expiring_soon
          FROM moments m
          FULL OUTER JOIN spheres s ON s.created_by_user_id = ${userId}::uuid AND s.deleted_at IS NULL
          WHERE m.user_id = ${userId}::uuid AND m.deleted_at IS NULL
        ` as any[],

        // Media statistics
        prisma.$queryRaw`
          SELECT
            COUNT(*) as total_files,
            COALESCE(SUM((metadata->>'fileSize')::bigint), 0) as total_size
          FROM moments
          WHERE user_id = ${userId}::uuid
            AND media_url IS NOT NULL
            AND deleted_at IS NULL
        ` as any[],

        // Last export
        prisma.dataExportRequest.findFirst({
          where: { userId, status: 'completed' },
          orderBy: { createdAt: 'desc' },
          select: { createdAt: true },
        }),

        // Active deletion requests
        prisma.dataDeletionRequest.count({
          where: { userId, status: { in: ['pending', 'processing'] } },
        }),
      ]);

      const retention = retentionStats[0] || {};
      const media = mediaStats[0] || {};

      const summary = {
        totalMoments: momentStats._count.id,
        totalSpheres: sphereStats,
        totalAnalyticsRecords: analyticsStats,
        totalCollaborations: collaborationStats,
        dataRetentionStatus: {
          momentsExpiringSoon: parseInt(retention.moments_expiring_soon) || 0,
          spheresExpiringSoon: parseInt(retention.spheres_expiring_soon) || 0,
          oldestMoment: momentStats._min.capturedAt?.toISOString(),
          newestMoment: momentStats._max.capturedAt?.toISOString(),
        },
        storageUsage: {
          totalMediaFiles: parseInt(media.total_files) || 0,
          totalMediaSize: parseInt(media.total_size) || 0,
          estimatedDatabaseSize: (momentStats._count.id * 2048) + (sphereStats * 512), // Rough estimate in bytes
        },
      };

      return {
        summary,
        lastExport: lastExport?.createdAt.toISOString(),
        activeDeletionRequests: activeDeletions,
      };

    } catch (error: any) {
      fastify.log.error('Data summary fetch failed:', error);
      return reply.status(500).send({
        error: 'Internal Server Error',
        message: 'Failed to get data summary',
      });
    }
  });
}
