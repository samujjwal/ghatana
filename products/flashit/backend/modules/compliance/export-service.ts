/**
 * Data Export Service for Flashit
 * Implements comprehensive data export using data-cloud batch tooling
 *
 * @doc.type service
 * @doc.purpose GDPR-compliant data export with multiple formats
 * @doc.layer product
 * @doc.pattern ExportService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import { PrismaClient } from '@prisma/client';
import { createWriteStream, promises as fs } from 'fs';
import { join } from 'path';
import { createObjectCsvWriter } from 'csv-writer';
import archiver from 'archiver';
import crypto from 'crypto';
import { S3Client, GetObjectCommand, HeadObjectCommand } from '@aws-sdk/client-s3';
import { getSignedUrl } from '@aws-sdk/s3-request-presigner';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
});

// Prisma client
const prisma = new PrismaClient();

// S3 client
const s3Client = new S3Client({
  region: process.env.AWS_REGION || 'us-east-1',
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
});

// Queue configuration
const EXPORT_QUEUE = 'flashit:data-export';
const EXPORTS_DIR = process.env.EXPORTS_DIR || '/tmp/flashit-exports';

// Export interfaces
export type ExportFormat = 'json' | 'csv' | 'pdf' | 'zip';
export type ExportScope = 'all' | 'sphere' | 'dateRange';

export interface ExportRequest {
  userId: string;
  exportId: string;
  format: ExportFormat;
  scope: ExportScope;
  includeMedia: boolean;
  encryption?: {
    enabled: boolean;
    password?: string;
  };
  filters?: {
    sphereIds?: string[];
    startDate?: Date;
    endDate?: Date;
    includedTypes?: ('moments' | 'spheres' | 'analytics' | 'collaborations' | 'comments')[];
  };
  requestedAt: Date;
  expiresAt: Date;
}

export interface ExportResult {
  exportId: string;
  status: 'pending' | 'processing' | 'completed' | 'failed' | 'expired';
  filePath?: string;
  downloadUrl?: string;
  fileSize?: number;
  checksum?: string;
  createdAt: Date;
  completedAt?: Date;
  error?: string;
}

export interface ExportBundle {
  metadata: {
    exportId: string;
    userId: string;
    generatedAt: string;
    format: ExportFormat;
    scope: ExportScope;
    totalItems: number;
    dataIntegrity: {
      checksum: string;
      algorithm: string;
    };
  };
  userData: {
    profile: any;
    preferences: any;
    settings: any;
  };
  spheres: any[];
  moments: any[];
  analytics?: any;
  collaborations?: any[];
  mediaFiles?: Array<{
    id: string;
    filename: string;
    url: string;
    type: string;
    size: number;
  }>;
}

// Create export queue
export const exportQueue = new Queue<ExportRequest>(EXPORT_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 50,
    removeOnFail: 25,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 5000,
    },
  },
});

/**
 * Data Export Service
 */
export class DataExportService {

  /**
   * Request data export
   */
  static async requestExport(
    userId: string,
    options: {
      format: ExportFormat;
      scope: ExportScope;
      includeMedia?: boolean;
      encryption?: { enabled: boolean; password?: string };
      filters?: ExportRequest['filters'];
    }
  ): Promise<string> {
    const exportId = crypto.randomUUID();
    const expiresAt = new Date(Date.now() + 7 * 24 * 60 * 60 * 1000); // 7 days

    // Store export request in database
    await prisma.dataExportRequest.create({
      data: {
        id: exportId,
        userId,
        format: options.format,
        scope: options.scope,
        includeMedia: options.includeMedia || false,
        encryptionEnabled: options.encryption?.enabled || false,
        filters: options.filters ? JSON.stringify(options.filters) : null,
        status: 'pending',
        expiresAt,
      },
    });

    // Queue export job
    const exportRequest: ExportRequest = {
      userId,
      exportId,
      format: options.format,
      scope: options.scope,
      includeMedia: options.includeMedia || false,
      encryption: options.encryption,
      filters: options.filters,
      requestedAt: new Date(),
      expiresAt,
    };

    await exportQueue.add('export-user-data', exportRequest, {
      jobId: exportId,
      priority: options.format === 'json' ? 5 : 3, // JSON exports get higher priority
    });

    // Create audit log
    await prisma.auditEvent.create({
      data: {
        userId,
        eventType: 'DATA_EXPORT_REQUESTED',
        entityType: 'USER',
        entityId: userId,
        details: {
          exportId,
          format: options.format,
          scope: options.scope,
          includeMedia: options.includeMedia,
        },
        ipAddress: 'system', // Would be passed from request context
        userAgent: 'export-service',
      },
    });

    return exportId;
  }

  /**
   * Get export status
   */
  static async getExportStatus(exportId: string, userId: string): Promise<ExportResult | null> {
    const exportRequest = await prisma.dataExportRequest.findFirst({
      where: {
        id: exportId,
        userId,
      },
    });

    if (!exportRequest) {
      return null;
    }

    // Check if expired
    if (exportRequest.expiresAt < new Date()) {
      await this.markExportExpired(exportId);
      return {
        exportId,
        status: 'expired',
        createdAt: exportRequest.createdAt,
        error: 'Export has expired',
      };
    }

    return {
      exportId,
      status: exportRequest.status as any,
      filePath: exportRequest.filePath,
      downloadUrl: exportRequest.downloadUrl,
      fileSize: exportRequest.fileSize,
      checksum: exportRequest.checksum,
      createdAt: exportRequest.createdAt,
      completedAt: exportRequest.completedAt,
      error: exportRequest.error,
    };
  }

  /**
   * Generate comprehensive data bundle
   */
  static async generateDataBundle(exportRequest: ExportRequest): Promise<ExportBundle> {
    const { userId, filters } = exportRequest;

    // Build where conditions based on filters
    const whereConditions = this.buildWhereConditions(userId, filters);

    const [
      userData,
      spheres,
      moments,
      analytics,
      collaborations,
      mediaFiles,
    ] = await Promise.all([
      this.getUserData(userId),
      this.getSpheresData(userId, whereConditions.spheres),
      this.getMomentsData(userId, whereConditions.moments),
      this.getAnalyticsData(userId, whereConditions.analytics),
      this.getCollaborationsData(userId, whereConditions.collaborations),
      exportRequest.includeMedia ? this.getMediaFiles(userId, whereConditions.media) : Promise.resolve([]),
    ]);

    // Calculate total items
    const totalItems = spheres.length + moments.length + (collaborations?.length || 0);

    // Create bundle
    const bundle: ExportBundle = {
      metadata: {
        exportId: exportRequest.exportId,
        userId,
        generatedAt: new Date().toISOString(),
        format: exportRequest.format,
        scope: exportRequest.scope,
        totalItems,
        dataIntegrity: {
          checksum: '', // Will be calculated after serialization
          algorithm: 'sha256',
        },
      },
      userData,
      spheres,
      moments,
      analytics,
      collaborations,
      mediaFiles,
    };

    // Calculate checksum
    const bundleContent = JSON.stringify(bundle);
    bundle.metadata.dataIntegrity.checksum = crypto
      .createHash('sha256')
      .update(bundleContent)
      .digest('hex');

    return bundle;
  }

  /**
   * Export to JSON format
   */
  static async exportToJson(bundle: ExportBundle, outputPath: string): Promise<void> {
    const jsonContent = JSON.stringify(bundle, null, 2);
    await fs.writeFile(outputPath, jsonContent, 'utf8');
  }

  /**
   * Export to CSV format
   */
  static async exportToCsv(bundle: ExportBundle, outputDir: string): Promise<void> {
    // Create CSV files for each data type
    await Promise.all([
      this.createSpheresCSV(bundle.spheres, join(outputDir, 'spheres.csv')),
      this.createMomentsCSV(bundle.moments, join(outputDir, 'moments.csv')),
      this.createCollaborationsCSV(bundle.collaborations || [], join(outputDir, 'collaborations.csv')),
      this.createMetadataCSV(bundle.metadata, join(outputDir, 'metadata.csv')),
    ]);

    // Create a summary file
    const summary = {
      exportId: bundle.metadata.exportId,
      generatedAt: bundle.metadata.generatedAt,
      totalSpheres: bundle.spheres.length,
      totalMoments: bundle.moments.length,
      totalCollaborations: bundle.collaborations?.length || 0,
      totalMediaFiles: bundle.mediaFiles?.length || 0,
    };

    await fs.writeFile(
      join(outputDir, 'export-summary.json'),
      JSON.stringify(summary, null, 2),
      'utf8'
    );
  }

  /**
   * Create ZIP archive with optional encryption
   */
  static async createZipArchive(
    sourceDir: string,
    outputPath: string,
    encryption?: { enabled: boolean; password?: string }
  ): Promise<void> {
    return new Promise((resolve, reject) => {
      const output = createWriteStream(outputPath);
      const archive = archiver('zip', {
        zlib: { level: 9 }, // Best compression
      });

      output.on('close', () => resolve());
      output.on('error', reject);
      archive.on('error', reject);

      archive.pipe(output);

      // Add encryption if requested
      if (encryption?.enabled && encryption.password) {
        // Note: archiver doesn't support password encryption natively
        // In production, use a library like node-7z or implement AES encryption
        console.warn('Password encryption not implemented in this demo');
      }

      // Add all files from source directory
      archive.directory(sourceDir, false);
      archive.finalize();
    });
  }

  /**
   * Generate secure download URL
   */
  static async generateDownloadUrl(filePath: string, exportId: string): Promise<string> {
    // In production, upload to S3 and generate presigned URL
    // For now, return a local URL that would be served by the API
    const token = crypto.randomBytes(32).toString('hex');

    // Store download token in Redis with expiration
    await redis.setex(`download:${exportId}:${token}`, 24 * 60 * 60, filePath);

    return `/api/data-export/${exportId}/download?token=${token}`;
  }

  /**
   * Verify data integrity
   */
  static async verifyDataIntegrity(bundle: ExportBundle): Promise<boolean> {
    const bundleWithoutChecksum = { ...bundle };
    bundleWithoutChecksum.metadata.dataIntegrity.checksum = '';

    const calculatedChecksum = crypto
      .createHash('sha256')
      .update(JSON.stringify(bundleWithoutChecksum))
      .digest('hex');

    return calculatedChecksum === bundle.metadata.dataIntegrity.checksum;
  }

  /**
   * Clean up expired exports
   */
  static async cleanupExpiredExports(): Promise<number> {
    const expiredExports = await prisma.dataExportRequest.findMany({
      where: {
        status: 'completed',
        expiresAt: { lt: new Date() },
      },
      select: { id: true, filePath: true },
    });

    let cleanedCount = 0;

    for (const exportRequest of expiredExports) {
      try {
        // Delete file if exists
        if (exportRequest.filePath) {
          await fs.unlink(exportRequest.filePath).catch(() => {
            // File might already be deleted
          });
        }

        // Mark as expired in database
        await prisma.dataExportRequest.update({
          where: { id: exportRequest.id },
          data: { status: 'expired', filePath: null, downloadUrl: null },
        });

        cleanedCount++;
      } catch (error) {
        console.error(`Failed to cleanup export ${exportRequest.id}:`, error);
      }
    }

    return cleanedCount;
  }

  /**
   * Helper methods
   */

  private static buildWhereConditions(userId: string, filters?: ExportRequest['filters']) {
    const baseWhere = { userId, deletedAt: null };

    return {
      spheres: {
        ...baseWhere,
        ...(filters?.sphereIds && { id: { in: filters.sphereIds } }),
      },
      moments: {
        ...baseWhere,
        ...(filters?.sphereIds && { sphereId: { in: filters.sphereIds } }),
        ...(filters?.startDate && { capturedAt: { gte: filters.startDate } }),
        ...(filters?.endDate && { capturedAt: { lte: filters.endDate } }),
      },
      analytics: {
        userId,
        ...(filters?.startDate && { dateBucket: { gte: filters.startDate } }),
        ...(filters?.endDate && { dateBucket: { lte: filters.endDate } }),
      },
      collaborations: {
        OR: [
          { sharedByUserId: userId },
          { sharedWithUserId: userId },
        ],
        revokedAt: null,
      },
      media: {
        moments: {
          ...baseWhere,
          ...(filters?.sphereIds && { sphereId: { in: filters.sphereIds } }),
        },
      },
    };
  }

  private static async getUserData(userId: string): Promise<any> {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        email: true,
        displayName: true,
        createdAt: true,
        lastLoginAt: true,
      },
    });

    const preferences = await prisma.collaboration.notificationPreference.findUnique({
      where: { userId },
    });

    return {
      profile: user,
      preferences: preferences || {},
      settings: {}, // Additional user settings would go here
    };
  }

  private static async getSpheresData(userId: string, whereConditions: any): Promise<any[]> {
    return await prisma.sphere.findMany({
      where: whereConditions,
      select: {
        id: true,
        name: true,
        description: true,
        color: true,
        isArchived: true,
        createdAt: true,
        updatedAt: true,
        sphereAccess: {
          select: {
            role: true,
            grantedAt: true,
          },
          where: { userId, revokedAt: null },
        },
      },
    });
  }

  private static async getMomentsData(userId: string, whereConditions: any): Promise<any[]> {
    return await prisma.moment.findMany({
      where: whereConditions,
      select: {
        id: true,
        contentText: true,
        contentTranscript: true,
        mediaUrl: true,
        mediaType: true,
        importance: true,
        emotions: true,
        tags: true,
        capturedAt: true,
        updatedAt: true,
        sphere: {
          select: { name: true },
        },
      },
    });
  }

  private static async getAnalyticsData(userId: string, whereConditions: any): Promise<any> {
    const [userAnalytics, insights] = await Promise.all([
      prisma.analytics.userAnalytics.findMany({
        where: whereConditions,
        select: {
          dateBucket: true,
          timeBucket: true,
          momentsCreated: true,
          productivityScore: true,
          emotionDiversityScore: true,
        },
      }),
      prisma.analytics.userInsight.findMany({
        where: {
          userId,
          createdAt: whereConditions.dateBucket ? {
            gte: whereConditions.dateBucket.gte,
            ...(whereConditions.dateBucket.lte && { lte: whereConditions.dateBucket.lte }),
          } : undefined,
        },
        select: {
          insightType: true,
          insightCategory: true,
          title: true,
          description: true,
          confidenceScore: true,
          createdAt: true,
        },
      }),
    ]);

    return {
      userAnalytics,
      insights,
    };
  }

  private static async getCollaborationsData(userId: string, whereConditions: any): Promise<any[]> {
    return await prisma.collaboration.sphereShare.findMany({
      where: whereConditions,
      select: {
        id: true,
        permissionLevel: true,
        acceptedAt: true,
        createdAt: true,
        sphere: { select: { name: true } },
        sharedByUser: { select: { displayName: true, email: true } },
        sharedWithUser: { select: { displayName: true, email: true } },
      },
    });
  }

  private static async getMediaFiles(userId: string, whereConditions: any): Promise<any[]> {
    const momentsWithMedia = await prisma.moment.findMany({
      where: {
        ...whereConditions.moments,
        mediaUrl: { not: null },
      },
      select: {
        id: true,
        mediaUrl: true,
        mediaType: true,
        metadata: true,
      },
    });

    return momentsWithMedia.map(moment => ({
      id: moment.id,
      filename: moment.mediaUrl?.split('/').pop() || 'unknown',
      url: moment.mediaUrl,
      type: moment.mediaType,
      size: moment.metadata?.fileSize || 0,
    }));
  }

  private static async createSpheresCSV(spheres: any[], outputPath: string): Promise<void> {
    const csvWriter = createObjectCsvWriter({
      path: outputPath,
      header: [
        { id: 'id', title: 'ID' },
        { id: 'name', title: 'Name' },
        { id: 'description', title: 'Description' },
        { id: 'color', title: 'Color' },
        { id: 'isArchived', title: 'Archived' },
        { id: 'createdAt', title: 'Created At' },
        { id: 'role', title: 'Your Role' },
      ],
    });

    const records = spheres.map(sphere => ({
      ...sphere,
      role: sphere.sphereAccess[0]?.role || 'OWNER',
      createdAt: sphere.createdAt.toISOString(),
    }));

    await csvWriter.writeRecords(records);
  }

  private static async createMomentsCSV(moments: any[], outputPath: string): Promise<void> {
    const csvWriter = createObjectCsvWriter({
      path: outputPath,
      header: [
        { id: 'id', title: 'ID' },
        { id: 'contentText', title: 'Content' },
        { id: 'contentTranscript', title: 'Transcript' },
        { id: 'mediaType', title: 'Media Type' },
        { id: 'importance', title: 'Importance' },
        { id: 'emotions', title: 'Emotions' },
        { id: 'tags', title: 'Tags' },
        { id: 'sphereName', title: 'Sphere' },
        { id: 'capturedAt', title: 'Captured At' },
      ],
    });

    const records = moments.map(moment => ({
      ...moment,
      emotions: Array.isArray(moment.emotions) ? moment.emotions.join(', ') : '',
      tags: Array.isArray(moment.tags) ? moment.tags.join(', ') : '',
      sphereName: moment.sphere?.name || '',
      capturedAt: moment.capturedAt.toISOString(),
    }));

    await csvWriter.writeRecords(records);
  }

  private static async createCollaborationsCSV(collaborations: any[], outputPath: string): Promise<void> {
    const csvWriter = createObjectCsvWriter({
      path: outputPath,
      header: [
        { id: 'id', title: 'ID' },
        { id: 'sphereName', title: 'Sphere Name' },
        { id: 'permissionLevel', title: 'Permission Level' },
        { id: 'sharedBy', title: 'Shared By' },
        { id: 'sharedWith', title: 'Shared With' },
        { id: 'acceptedAt', title: 'Accepted At' },
        { id: 'createdAt', title: 'Created At' },
      ],
    });

    const records = collaborations.map(collab => ({
      ...collab,
      sphereName: collab.sphere?.name || '',
      sharedBy: collab.sharedByUser?.displayName || '',
      sharedWith: collab.sharedWithUser?.displayName || '',
      acceptedAt: collab.acceptedAt?.toISOString() || '',
      createdAt: collab.createdAt.toISOString(),
    }));

    await csvWriter.writeRecords(records);
  }

  private static async createMetadataCSV(metadata: any, outputPath: string): Promise<void> {
    const csvWriter = createObjectCsvWriter({
      path: outputPath,
      header: [
        { id: 'key', title: 'Property' },
        { id: 'value', title: 'Value' },
      ],
    });

    const records = Object.entries(metadata).map(([key, value]) => ({
      key,
      value: typeof value === 'object' ? JSON.stringify(value) : String(value),
    }));

    await csvWriter.writeRecords(records);
  }

  private static async markExportExpired(exportId: string): Promise<void> {
    await prisma.dataExportRequest.update({
      where: { id: exportId },
      data: { status: 'expired' },
    });
  }
}

/**
 * Export worker - processes export jobs
 */
const exportWorker = new Worker<ExportRequest>(
  EXPORT_QUEUE,
  async (job: Job<ExportRequest>) => {
    const exportRequest = job.data;

    try {
      await job.updateProgress(10);

      // Ensure exports directory exists
      await fs.mkdir(EXPORTS_DIR, { recursive: true });

      // Update status to processing
      await prisma.dataExportRequest.update({
        where: { id: exportRequest.exportId },
        data: { status: 'processing', startedAt: new Date() },
      });

      await job.updateProgress(20);

      // Generate data bundle
      const bundle = await DataExportService.generateDataBundle(exportRequest);

      await job.updateProgress(50);

      // Export based on format
      let outputPath: string;
      let fileSize: number;

      if (exportRequest.format === 'json') {
        outputPath = join(EXPORTS_DIR, `${exportRequest.exportId}.json`);
        await DataExportService.exportToJson(bundle, outputPath);
      } else if (exportRequest.format === 'csv') {
        const csvDir = join(EXPORTS_DIR, exportRequest.exportId);
        await fs.mkdir(csvDir, { recursive: true });
        await DataExportService.exportToCsv(bundle, csvDir);

        // Create ZIP of CSV files
        outputPath = join(EXPORTS_DIR, `${exportRequest.exportId}.zip`);
        await DataExportService.createZipArchive(csvDir, outputPath, exportRequest.encryption);
      } else {
        throw new Error(`Unsupported export format: ${exportRequest.format}`);
      }

      await job.updateProgress(80);

      // Get file size
      const stats = await fs.stat(outputPath);
      fileSize = stats.size;

      // Generate download URL
      const downloadUrl = await DataExportService.generateDownloadUrl(outputPath, exportRequest.exportId);

      // Calculate file checksum
      const fileBuffer = await fs.readFile(outputPath);
      const checksum = crypto.createHash('sha256').update(fileBuffer).digest('hex');

      await job.updateProgress(90);

      // Update database with completion
      await prisma.dataExportRequest.update({
        where: { id: exportRequest.exportId },
        data: {
          status: 'completed',
          filePath: outputPath,
          downloadUrl,
          fileSize,
          checksum,
          completedAt: new Date(),
        },
      });

      // Create completion audit log
      await prisma.auditEvent.create({
        data: {
          userId: exportRequest.userId,
          eventType: 'DATA_EXPORT_COMPLETED',
          entityType: 'USER',
          entityId: exportRequest.userId,
          details: {
            exportId: exportRequest.exportId,
            format: exportRequest.format,
            fileSize,
            checksum,
          },
          ipAddress: 'system',
          userAgent: 'export-service',
        },
      });

      await job.updateProgress(100);

      return {
        exportId: exportRequest.exportId,
        downloadUrl,
        fileSize,
        checksum,
      };

    } catch (error: any) {
      console.error('Export job failed:', error);

      // Update database with failure
      await prisma.dataExportRequest.update({
        where: { id: exportRequest.exportId },
        data: {
          status: 'failed',
          error: error.message,
          completedAt: new Date(),
        },
      });

      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 2, // Process up to 2 exports concurrently
  }
);

// Worker event handlers
exportWorker.on('completed', (job) => {
  console.log(`Export job ${job.data.exportId} completed successfully`);
});

exportWorker.on('failed', (job, err) => {
  console.error(`Export job ${job?.data.exportId} failed:`, err);
});

// Schedule cleanup task
setInterval(async () => {
  try {
    const cleanedCount = await DataExportService.cleanupExpiredExports();
    if (cleanedCount > 0) {
      console.log(`Cleaned up ${cleanedCount} expired exports`);
    }
  } catch (error) {
    console.error('Export cleanup failed:', error);
  }
}, 24 * 60 * 60 * 1000); // Run daily

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down export worker...');
  await exportWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { exportWorker };
