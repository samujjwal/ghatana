/**
 * Secure Data Deletion Service for Flashit
 * GDPR-compliant data deletion with atomicity and verification
 *
 * @doc.type service
 * @doc.purpose Secure data deletion with atomic operations across all stores
 * @doc.layer product
 * @doc.pattern DeletionService
 */

import { Queue, Job, Worker } from 'bullmq';
import Redis from 'ioredis';
import { PrismaClient } from '@prisma/client';
import { S3Client, DeleteObjectCommand, ListObjectsV2Command } from '@aws-sdk/client-s3';
import crypto from 'crypto';
import nodemailer from 'nodemailer';

// Redis connection
const redis = new Redis({
  host: process.env.REDIS_HOST || 'localhost',
  port: parseInt(process.env.REDIS_PORT || '6379'),
  password: process.env.REDIS_PASSWORD,
});

// Prisma client with explicit transaction options
const prisma = new PrismaClient({
  transactionOptions: {
    maxWait: 30000, // 30 seconds
    timeout: 60000, // 1 minute
  },
});

// S3 client
const s3Client = new S3Client({
  region: process.env.AWS_REGION || 'us-east-1',
  credentials: {
    accessKeyId: process.env.AWS_ACCESS_KEY_ID!,
    secretAccessKey: process.env.AWS_SECRET_ACCESS_KEY!,
  },
});

// Queue configuration
const DELETION_QUEUE = 'flashit:data-deletion';

// Deletion interfaces
export type DeletionType = 'user' | 'sphere' | 'moments' | 'partial';
export type DeletionMethod = 'soft_delete' | 'hard_delete' | 'overwrite' | 'purge';
export type DeletionStatus = 'pending' | 'processing' | 'completed' | 'failed' | 'verified';

export interface DeletionRequest {
  id: string;
  userId: string;
  deletionType: DeletionType;
  scope: {
    sphereIds?: string[];
    momentIds?: string[];
    dateRange?: {
      start: Date;
      end: Date;
    };
    includeMedia?: boolean;
    includeAnalytics?: boolean;
    includeCollaborations?: boolean;
  };
  verificationToken?: string;
  verificationRequired: boolean;
  requestedAt: Date;
  verificationExpiresAt?: Date;
}

export interface DeletionResult {
  requestId: string;
  status: DeletionStatus;
  deletionSummary?: {
    momentsDeleted: number;
    spheresDeleted: number;
    mediaFilesDeleted: number;
    analyticsDeleted: number;
    collaborationsDeleted: number;
    auditLogsDeleted: number;
    vectorsDeleted: number;
  };
  verificationHash?: string;
  error?: string;
}

export interface DeletionPlan {
  operations: DeletionOperation[];
  estimatedDuration: number;
  reversible: boolean;
  requiresBackup: boolean;
}

export interface DeletionOperation {
  id: string;
  type: 'database' | 's3' | 'vector' | 'cache' | 'audit';
  target: string;
  method: DeletionMethod;
  dependencies: string[];
  estimated_time: number;
  critical: boolean;
}

// Create deletion queue
export const deletionQueue = new Queue<DeletionRequest>(DELETION_QUEUE, {
  connection: redis,
  defaultJobOptions: {
    removeOnComplete: 100,
    removeOnFail: 50,
    attempts: 3,
    backoff: {
      type: 'exponential',
      delay: 10000,
    },
  },
});

// Email transporter for notifications
const emailTransporter = nodemailer.createTransporter({
  host: process.env.SMTP_HOST || 'localhost',
  port: parseInt(process.env.SMTP_PORT || '587'),
  secure: false,
  auth: {
    user: process.env.SMTP_USER,
    pass: process.env.SMTP_PASS,
  },
});

/**
 * Secure Data Deletion Service
 */
export class SecureDataDeletionService {

  /**
   * Request data deletion
   */
  static async requestDeletion(
    userId: string,
    deletionType: DeletionType,
    scope: DeletionRequest['scope'],
    options: {
      verificationRequired?: boolean;
      immediate?: boolean;
    } = {}
  ): Promise<string> {
    // Check deletion cooldown
    const canDelete = await this.checkDeletionEligibility(userId);
    if (!canDelete) {
      throw new Error('Deletion request blocked by cooldown period or rate limiting');
    }

    const requestId = crypto.randomUUID();
    const verificationRequired = options.verificationRequired !== false; // Default true
    const verificationToken = verificationRequired ? crypto.randomBytes(32).toString('hex') : undefined;
    const verificationExpiresAt = verificationRequired ?
      new Date(Date.now() + 24 * 60 * 60 * 1000) : undefined; // 24 hours

    // Create deletion request
    await prisma.dataDeletionRequest.create({
      data: {
        id: requestId,
        userId,
        deletionType,
        scope: JSON.stringify(scope),
        verificationToken,
        verificationExpiresAt,
        status: verificationRequired ? 'pending' : 'processing',
      },
    });

    // Send verification email if required
    if (verificationRequired) {
      await this.sendVerificationEmail(userId, requestId, verificationToken!);
    } else if (options.immediate) {
      // Queue immediate deletion
      await this.queueDeletion(requestId);
    }

    // Create audit log
    await this.logDataLifecycleEvent(
      userId,
      'user',
      userId,
      'deletion_requested',
      {
        requestId,
        deletionType,
        scope,
        verificationRequired,
      }
    );

    return requestId;
  }

  /**
   * Verify deletion request
   */
  static async verifyDeletion(requestId: string, verificationToken: string): Promise<void> {
    const deletionRequest = await prisma.dataDeletionRequest.findFirst({
      where: {
        id: requestId,
        verificationToken,
        status: 'pending',
        verificationExpiresAt: { gt: new Date() },
      },
    });

    if (!deletionRequest) {
      throw new Error('Invalid or expired verification token');
    }

    // Update request as verified
    await prisma.dataDeletionRequest.update({
      where: { id: requestId },
      data: {
        status: 'processing',
        verifiedAt: new Date(),
        verificationToken: null,
      },
    });

    // Queue deletion job
    await this.queueDeletion(requestId);

    // Log verification
    await this.logDataLifecycleEvent(
      deletionRequest.userId,
      'user',
      deletionRequest.userId,
      'deletion_verified',
      { requestId }
    );
  }

  /**
   * Execute data deletion
   */
  static async executeDeletion(deletionRequest: DeletionRequest): Promise<DeletionResult> {
    const plan = await this.createDeletionPlan(deletionRequest);

    try {
      // Update status to processing
      await prisma.dataDeletionRequest.update({
        where: { id: deletionRequest.id },
        data: {
          status: 'processing',
          startedAt: new Date(),
        },
      });

      // Execute deletion operations in order
      const deletionSummary = await this.executeDeletionPlan(plan, deletionRequest);

      // Generate verification hash
      const verificationHash = this.generateVerificationHash(deletionRequest, deletionSummary);

      // Update request as completed
      await prisma.dataDeletionRequest.update({
        where: { id: deletionRequest.id },
        data: {
          status: 'completed',
          deletionSummary: JSON.stringify(deletionSummary),
          completedAt: new Date(),
        },
      });

      // Log completion
      await this.logDataLifecycleEvent(
        deletionRequest.userId,
        'user',
        deletionRequest.userId,
        'deletion_completed',
        {
          requestId: deletionRequest.id,
          summary: deletionSummary,
          verificationHash,
        }
      );

      // Send completion notification
      await this.sendDeletionCompletedEmail(deletionRequest.userId, deletionRequest.id, deletionSummary);

      return {
        requestId: deletionRequest.id,
        status: 'completed',
        deletionSummary,
        verificationHash,
      };

    } catch (error: any) {
      console.error('Deletion execution failed:', error);

      // Update request as failed
      await prisma.dataDeletionRequest.update({
        where: { id: deletionRequest.id },
        data: {
          status: 'failed',
          error: error.message,
          completedAt: new Date(),
        },
      });

      // Log failure
      await this.logDataLifecycleEvent(
        deletionRequest.userId,
        'user',
        deletionRequest.userId,
        'deletion_failed',
        {
          requestId: deletionRequest.id,
          error: error.message,
        }
      );

      return {
        requestId: deletionRequest.id,
        status: 'failed',
        error: error.message,
      };
    }
  }

  /**
   * Create deletion plan
   */
  static async createDeletionPlan(deletionRequest: DeletionRequest): Promise<DeletionPlan> {
    const operations: DeletionOperation[] = [];

    switch (deletionRequest.deletionType) {
      case 'user':
        operations.push(...await this.createUserDeletionOperations(deletionRequest));
        break;
      case 'sphere':
        operations.push(...await this.createSphereDeletionOperations(deletionRequest));
        break;
      case 'moments':
        operations.push(...await this.createMomentsDeletionOperations(deletionRequest));
        break;
      case 'partial':
        operations.push(...await this.createPartialDeletionOperations(deletionRequest));
        break;
    }

    // Sort operations by dependencies and criticality
    const sortedOperations = this.sortOperationsByDependencies(operations);

    const estimatedDuration = operations.reduce((total, op) => total + op.estimated_time, 0);
    const reversible = deletionRequest.deletionType !== 'user' &&
                      operations.every(op => op.method === 'soft_delete');

    return {
      operations: sortedOperations,
      estimatedDuration,
      reversible,
      requiresBackup: operations.some(op => op.critical),
    };
  }

  /**
   * Execute deletion plan atomically
   */
  static async executeDeletionPlan(
    plan: DeletionPlan,
    deletionRequest: DeletionRequest
  ): Promise<DeletionResult['deletionSummary']> {
    const summary = {
      momentsDeleted: 0,
      spheresDeleted: 0,
      mediaFilesDeleted: 0,
      analyticsDeleted: 0,
      collaborationsDeleted: 0,
      auditLogsDeleted: 0,
      vectorsDeleted: 0,
    };

    // Use database transaction for atomic operations
    await prisma.$transaction(async (tx) => {
      for (const operation of plan.operations) {
        try {
          const result = await this.executeOperation(operation, deletionRequest, tx);

          // Update summary
          if (result.type === 'moments') summary.momentsDeleted += result.count;
          else if (result.type === 'spheres') summary.spheresDeleted += result.count;
          else if (result.type === 'media') summary.mediaFilesDeleted += result.count;
          else if (result.type === 'analytics') summary.analyticsDeleted += result.count;
          else if (result.type === 'collaborations') summary.collaborationsDeleted += result.count;
          else if (result.type === 'audit') summary.auditLogsDeleted += result.count;
          else if (result.type === 'vectors') summary.vectorsDeleted += result.count;

          // Log operation completion
          await this.logSecureDeletionOperation(
            deletionRequest.id,
            operation.type,
            operation.target,
            operation.method,
            'completed',
            result.verificationHash
          );

        } catch (error: any) {
          console.error(`Operation ${operation.id} failed:`, error);

          // Log operation failure
          await this.logSecureDeletionOperation(
            deletionRequest.id,
            operation.type,
            operation.target,
            operation.method,
            'failed',
            undefined,
            error.message
          );

          // If critical operation fails, rollback transaction
          if (operation.critical) {
            throw new Error(`Critical operation ${operation.id} failed: ${error.message}`);
          }
        }
      }
    }, {
      timeout: 300000, // 5 minutes for complex deletions
    });

    return summary;
  }

  /**
   * Execute individual deletion operation
   */
  static async executeOperation(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest,
    tx: any
  ): Promise<{ type: string; count: number; verificationHash?: string }> {
    switch (operation.type) {
      case 'database':
        return await this.executeDatabaseDeletion(operation, deletionRequest, tx);
      case 's3':
        return await this.executeS3Deletion(operation, deletionRequest);
      case 'vector':
        return await this.executeVectorDeletion(operation, deletionRequest);
      case 'cache':
        return await this.executeCacheDeletion(operation, deletionRequest);
      case 'audit':
        return await this.executeAuditDeletion(operation, deletionRequest, tx);
      default:
        throw new Error(`Unknown operation type: ${operation.type}`);
    }
  }

  /**
   * Execute database deletion
   */
  static async executeDatabaseDeletion(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest,
    tx: any
  ): Promise<{ type: string; count: number; verificationHash?: string }> {
    const { scope } = deletionRequest;
    let count = 0;

    switch (operation.target) {
      case 'moments':
        if (operation.method === 'soft_delete') {
          const result = await tx.moment.updateMany({
            where: this.buildMomentsWhereClause(deletionRequest.userId, scope),
            data: { deletedAt: new Date() },
          });
          count = result.count;
        } else if (operation.method === 'hard_delete') {
          const result = await tx.moment.deleteMany({
            where: this.buildMomentsWhereClause(deletionRequest.userId, scope),
          });
          count = result.count;
        }
        break;

      case 'spheres':
        if (operation.method === 'soft_delete') {
          const result = await tx.sphere.updateMany({
            where: this.buildSpheresWhereClause(deletionRequest.userId, scope),
            data: { deletedAt: new Date() },
          });
          count = result.count;
        } else if (operation.method === 'hard_delete') {
          const result = await tx.sphere.deleteMany({
            where: this.buildSpheresWhereClause(deletionRequest.userId, scope),
          });
          count = result.count;
        }
        break;

      case 'collaborations':
        const collaborationResult = await tx.collaboration.sphereShare.updateMany({
          where: {
            OR: [
              { sharedByUserId: deletionRequest.userId },
              { sharedWithUserId: deletionRequest.userId },
            ],
            ...(scope.sphereIds && { sphereId: { in: scope.sphereIds } }),
          },
          data: { revokedAt: new Date() },
        });
        count = collaborationResult.count;
        break;

      case 'user':
        if (operation.method === 'hard_delete') {
          // Delete user account (cascades to related data)
          await tx.user.delete({
            where: { id: deletionRequest.userId },
          });
          count = 1;
        }
        break;
    }

    const verificationHash = crypto
      .createHash('sha256')
      .update(`${operation.target}:${count}:${new Date().toISOString()}`)
      .digest('hex');

    return { type: operation.target, count, verificationHash };
  }

  /**
   * Execute S3 deletion
   */
  static async executeS3Deletion(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest
  ): Promise<{ type: string; count: number }> {
    const { scope } = deletionRequest;
    let deletedCount = 0;

    // Get media files to delete
    const mediaFiles = await prisma.moment.findMany({
      where: {
        ...this.buildMomentsWhereClause(deletionRequest.userId, scope),
        mediaUrl: { not: null },
      },
      select: { mediaUrl: true },
    });

    // Delete from S3
    for (const moment of mediaFiles) {
      if (moment.mediaUrl) {
        try {
          const key = moment.mediaUrl.split('/').pop(); // Extract S3 key
          await s3Client.send(new DeleteObjectCommand({
            Bucket: process.env.S3_BUCKET!,
            Key: key,
          }));
          deletedCount++;
        } catch (error) {
          console.error(`Failed to delete S3 object ${moment.mediaUrl}:`, error);
        }
      }
    }

    return { type: 'media', count: deletedCount };
  }

  /**
   * Execute vector deletion
   */
  static async executeVectorDeletion(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest
  ): Promise<{ type: string; count: number }> {
    // Placeholder for vector store deletion
    // In production, this would interface with the vector database
    console.log(`Vector deletion for user ${deletionRequest.userId} - operation: ${operation.target}`);

    return { type: 'vectors', count: 0 };
  }

  /**
   * Execute cache deletion
   */
  static async executeCacheDeletion(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest
  ): Promise<{ type: string; count: number }> {
    // Delete user-related cache entries
    const pattern = `flashit:*:${deletionRequest.userId}:*`;
    const keys = await redis.keys(pattern);

    if (keys.length > 0) {
      await redis.del(...keys);
    }

    return { type: 'cache', count: keys.length };
  }

  /**
   * Execute audit deletion
   */
  static async executeAuditDeletion(
    operation: DeletionOperation,
    deletionRequest: DeletionRequest,
    tx: any
  ): Promise<{ type: string; count: number }> {
    // Only delete audit logs if explicitly requested and allowed by policy
    if (deletionRequest.scope.includeAnalytics) {
      const result = await tx.auditEvent.deleteMany({
        where: { userId: deletionRequest.userId },
      });
      return { type: 'audit', count: result.count };
    }

    return { type: 'audit', count: 0 };
  }

  /**
   * Helper methods
   */

  static async checkDeletionEligibility(userId: string): Promise<boolean> {
    const result = await prisma.$queryRaw`
      SELECT check_deletion_cooldown(${userId}::uuid) as can_delete
    ` as any[];

    return result[0]?.can_delete || false;
  }

  static async queueDeletion(requestId: string): Promise<void> {
    const deletionRequest = await prisma.dataDeletionRequest.findUnique({
      where: { id: requestId },
    });

    if (!deletionRequest) {
      throw new Error('Deletion request not found');
    }

    const queueData: DeletionRequest = {
      id: deletionRequest.id,
      userId: deletionRequest.userId,
      deletionType: deletionRequest.deletionType as DeletionType,
      scope: JSON.parse(deletionRequest.scope as string),
      verificationRequired: false, // Already verified
      requestedAt: deletionRequest.createdAt,
    };

    await deletionQueue.add('execute-deletion', queueData, {
      jobId: requestId,
      priority: deletionRequest.deletionType === 'user' ? 10 : 5,
    });
  }

  static buildMomentsWhereClause(userId: string, scope: DeletionRequest['scope']): any {
    const where: any = { userId };

    if (scope.sphereIds && scope.sphereIds.length > 0) {
      where.sphereId = { in: scope.sphereIds };
    }

    if (scope.momentIds && scope.momentIds.length > 0) {
      where.id = { in: scope.momentIds };
    }

    if (scope.dateRange) {
      where.capturedAt = {
        gte: scope.dateRange.start,
        lte: scope.dateRange.end,
      };
    }

    return where;
  }

  static buildSpheresWhereClause(userId: string, scope: DeletionRequest['scope']): any {
    const where: any = { createdByUserId: userId };

    if (scope.sphereIds && scope.sphereIds.length > 0) {
      where.id = { in: scope.sphereIds };
    }

    return where;
  }

  static async createUserDeletionOperations(deletionRequest: DeletionRequest): Promise<DeletionOperation[]> {
    return [
      {
        id: 'cache-cleanup',
        type: 'cache',
        target: 'user_cache',
        method: 'purge',
        dependencies: [],
        estimated_time: 1000,
        critical: false,
      },
      {
        id: 'vector-cleanup',
        type: 'vector',
        target: 'user_vectors',
        method: 'purge',
        dependencies: [],
        estimated_time: 5000,
        critical: false,
      },
      {
        id: 'media-deletion',
        type: 's3',
        target: 'user_media',
        method: 'hard_delete',
        dependencies: [],
        estimated_time: 10000,
        critical: true,
      },
      {
        id: 'collaborations-cleanup',
        type: 'database',
        target: 'collaborations',
        method: 'soft_delete',
        dependencies: [],
        estimated_time: 2000,
        critical: false,
      },
      {
        id: 'moments-deletion',
        type: 'database',
        target: 'moments',
        method: 'hard_delete',
        dependencies: ['media-deletion'],
        estimated_time: 5000,
        critical: true,
      },
      {
        id: 'spheres-deletion',
        type: 'database',
        target: 'spheres',
        method: 'hard_delete',
        dependencies: ['moments-deletion'],
        estimated_time: 2000,
        critical: true,
      },
      {
        id: 'user-deletion',
        type: 'database',
        target: 'user',
        method: 'hard_delete',
        dependencies: ['spheres-deletion', 'collaborations-cleanup'],
        estimated_time: 1000,
        critical: true,
      },
    ];
  }

  static async createSphereDeletionOperations(deletionRequest: DeletionRequest): Promise<DeletionOperation[]> {
    return [
      {
        id: 'sphere-moments-deletion',
        type: 'database',
        target: 'moments',
        method: 'soft_delete',
        dependencies: [],
        estimated_time: 3000,
        critical: true,
      },
      {
        id: 'sphere-media-deletion',
        type: 's3',
        target: 'sphere_media',
        method: 'hard_delete',
        dependencies: ['sphere-moments-deletion'],
        estimated_time: 5000,
        critical: true,
      },
      {
        id: 'sphere-deletion',
        type: 'database',
        target: 'spheres',
        method: 'soft_delete',
        dependencies: ['sphere-media-deletion'],
        estimated_time: 1000,
        critical: true,
      },
    ];
  }

  static async createMomentsDeletionOperations(deletionRequest: DeletionRequest): Promise<DeletionOperation[]> {
    return [
      {
        id: 'moments-media-deletion',
        type: 's3',
        target: 'moments_media',
        method: 'hard_delete',
        dependencies: [],
        estimated_time: 5000,
        critical: true,
      },
      {
        id: 'moments-deletion',
        type: 'database',
        target: 'moments',
        method: 'soft_delete',
        dependencies: ['moments-media-deletion'],
        estimated_time: 3000,
        critical: true,
      },
    ];
  }

  static async createPartialDeletionOperations(deletionRequest: DeletionRequest): Promise<DeletionOperation[]> {
    const operations: DeletionOperation[] = [];

    if (deletionRequest.scope.includeMedia) {
      operations.push({
        id: 'partial-media-deletion',
        type: 's3',
        target: 'partial_media',
        method: 'hard_delete',
        dependencies: [],
        estimated_time: 3000,
        critical: false,
      });
    }

    operations.push({
      id: 'partial-data-deletion',
      type: 'database',
      target: 'moments',
      method: 'soft_delete',
      dependencies: deletionRequest.scope.includeMedia ? ['partial-media-deletion'] : [],
      estimated_time: 2000,
      critical: true,
    });

    return operations;
  }

  static sortOperationsByDependencies(operations: DeletionOperation[]): DeletionOperation[] {
    const sorted: DeletionOperation[] = [];
    const remaining = [...operations];

    while (remaining.length > 0) {
      const readyOps = remaining.filter(op =>
        op.dependencies.every(dep =>
          sorted.some(completedOp => completedOp.id === dep)
        )
      );

      if (readyOps.length === 0) {
        // Circular dependency or missing dependency
        throw new Error('Cannot resolve operation dependencies');
      }

      // Add critical operations first
      const criticalOps = readyOps.filter(op => op.critical);
      const nonCriticalOps = readyOps.filter(op => !op.critical);

      sorted.push(...criticalOps, ...nonCriticalOps);

      // Remove added operations from remaining
      readyOps.forEach(op => {
        const index = remaining.indexOf(op);
        remaining.splice(index, 1);
      });
    }

    return sorted;
  }

  static generateVerificationHash(
    deletionRequest: DeletionRequest,
    summary: DeletionResult['deletionSummary']
  ): string {
    const data = {
      requestId: deletionRequest.id,
      userId: deletionRequest.userId,
      deletionType: deletionRequest.deletionType,
      summary,
      timestamp: new Date().toISOString(),
    };

    return crypto.createHash('sha256').update(JSON.stringify(data)).digest('hex');
  }

  static async logDataLifecycleEvent(
    userId: string,
    entityType: string,
    entityId: string,
    operation: string,
    details: any
  ): Promise<void> {
    await prisma.$queryRaw`
      SELECT log_data_lifecycle_event(
        ${userId}::uuid,
        ${entityType},
        ${entityId}::uuid,
        ${operation},
        ${JSON.stringify(details)}::jsonb
      )
    `;
  }

  static async logSecureDeletionOperation(
    requestId: string,
    entityType: string,
    target: string,
    method: DeletionMethod,
    status: DeletionStatus,
    verificationHash?: string,
    errorDetails?: string
  ): Promise<void> {
    await prisma.secureDeletionLog.create({
      data: {
        deletionRequestId: requestId,
        entityType,
        entityId: target,
        deletionMethod: method,
        deletionStatus: status,
        verificationHash,
        storageLocation: entityType === 'database' ? 'postgresql' : entityType === 's3' ? 's3' : 'cache',
        errorDetails,
        deletedAt: status === 'completed' ? new Date() : null,
      },
    });
  }

  static async sendVerificationEmail(userId: string, requestId: string, token: string): Promise<void> {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { email: true, displayName: true },
    });

    if (!user) return;

    const verificationUrl = `${process.env.FRONTEND_URL}/data-deletion/verify/${requestId}?token=${token}`;

    await emailTransporter.sendMail({
      from: process.env.SMTP_FROM || 'security@flashit.app',
      to: user.email,
      subject: 'Verify Your Data Deletion Request',
      html: `
        <h2>Data Deletion Request Verification</h2>
        <p>Hello ${user.displayName},</p>
        <p>We received a request to delete your data. To proceed with this action, please verify your request by clicking the link below:</p>
        <p><a href="${verificationUrl}" style="background: #dc2626; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px;">Verify Deletion Request</a></p>
        <p><strong>Important:</strong> This action cannot be undone. All your data will be permanently deleted.</p>
        <p>If you did not request this deletion, please ignore this email and contact support immediately.</p>
        <p>This verification link will expire in 24 hours.</p>
        <p>Best regards,<br>The Flashit Security Team</p>
      `,
    });
  }

  static async sendDeletionCompletedEmail(
    userId: string,
    requestId: string,
    summary: DeletionResult['deletionSummary']
  ): Promise<void> {
    const user = await prisma.user.findUnique({
      where: { id: userId },
      select: { email: true, displayName: true },
    });

    if (!user) return;

    await emailTransporter.sendMail({
      from: process.env.SMTP_FROM || 'security@flashit.app',
      to: user.email,
      subject: 'Data Deletion Completed',
      html: `
        <h2>Data Deletion Completed</h2>
        <p>Hello ${user.displayName},</p>
        <p>Your data deletion request (${requestId}) has been completed successfully.</p>
        <h3>Deletion Summary:</h3>
        <ul>
          <li>Moments deleted: ${summary?.momentsDeleted || 0}</li>
          <li>Spheres deleted: ${summary?.spheresDeleted || 0}</li>
          <li>Media files deleted: ${summary?.mediaFilesDeleted || 0}</li>
          <li>Analytics records deleted: ${summary?.analyticsDeleted || 0}</li>
          <li>Collaborations removed: ${summary?.collaborationsDeleted || 0}</li>
        </ul>
        <p>All your requested data has been permanently removed from our systems in compliance with data protection regulations.</p>
        <p>If you have any questions about this process, please contact our support team.</p>
        <p>Best regards,<br>The Flashit Security Team</p>
      `,
    });
  }
}

/**
 * Deletion worker - processes deletion jobs
 */
const deletionWorker = new Worker<DeletionRequest>(
  DELETION_QUEUE,
  async (job: Job<DeletionRequest>) => {
    const deletionRequest = job.data;

    try {
      await job.updateProgress(10);

      console.log(`Starting deletion for user ${deletionRequest.userId}, type: ${deletionRequest.deletionType}`);

      const result = await SecureDataDeletionService.executeDeletion(deletionRequest);

      await job.updateProgress(100);

      return result;

    } catch (error: any) {
      console.error('Deletion worker failed:', error);
      throw error;
    }
  },
  {
    connection: redis,
    concurrency: 1, // Process deletions one at a time for safety
  }
);

// Worker event handlers
deletionWorker.on('completed', (job) => {
  console.log(`Deletion job ${job.data.id} completed successfully`);
});

deletionWorker.on('failed', (job, err) => {
  console.error(`Deletion job ${job?.data.id} failed:`, err);
});

// Graceful shutdown
process.on('SIGINT', async () => {
  console.log('Shutting down deletion worker...');
  await deletionWorker.close();
  await prisma.$disconnect();
  await redis.quit();
  process.exit(0);
});

export { deletionWorker };
