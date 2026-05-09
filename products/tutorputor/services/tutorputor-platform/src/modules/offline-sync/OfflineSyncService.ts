/**
 * Offline Sync Service
 *
 * Manages offline learning data synchronization with conflict resolution.
 * Provides queue-based sync operations with optimistic locking and
 * configurable conflict resolution strategies.
 *
 * @doc.type class
 * @doc.purpose Offline learning data synchronization with conflict resolution
 * @doc.layer platform
 * @doc.pattern Service
 */

import { createStandaloneLogger } from "@tutorputor/core/logger";
import type { TutorPrismaClient } from "@tutorputor/core/db";
import type { TenantId, UserId } from "@tutorputor/contracts/v1/types";
import type {
  SyncOperationType,
  SyncOperationStatus,
  ConflictResolutionStrategy,
} from "@tutorputor/core/prisma";

const logger = createStandaloneLogger({ component: "OfflineSyncService" });

// ============================================================================
// Sync Operation Types
// ============================================================================

export interface SyncOperation {
  id: string;
  tenantId: TenantId;
  userId: UserId;
  deviceId: string;
  operationType: SyncOperationType;
  operationStatus: SyncOperationStatus;
  resourceType: string;
  resourceId: string;
  resourceVersion: number;
  payload: Record<string, unknown>;
  serverVersion?: Record<string, unknown>;
  clientVersion?: Record<string, unknown>;
  conflictDetected: boolean;
  conflictStrategy?: ConflictResolutionStrategy;
  conflictReason?: string;
  attemptCount: number;
  errorMessage?: string;
  createdAt: Date;
  updatedAt: Date;
}

export interface SyncRequest {
  tenantId: TenantId;
  userId: UserId;
  deviceId: string;
  operationType: SyncOperationType;
  resourceType: string;
  resourceId: string;
  payload: Record<string, unknown>;
  expectedVersion?: number;
}

export interface SyncResult {
  success: boolean;
  operationId: string;
  conflictDetected: boolean;
  conflictResolution?: ConflictResolutionStrategy;
  errorMessage?: string;
}

export interface ConflictResolution {
  strategy: ConflictResolutionStrategy;
  resolvedPayload: Record<string, unknown>;
  resolutionNotes: string;
  dataLossRisk: boolean;
}

// ============================================================================
// Offline Sync Service
// ============================================================================

export class OfflineSyncService {
  private static instance: OfflineSyncService;

  private constructor() {}

  static getInstance(): OfflineSyncService {
    if (!OfflineSyncService.instance) {
      OfflineSyncService.instance = new OfflineSyncService();
    }
    return OfflineSyncService.instance;
  }

  /**
   * Queue a sync operation for offline changes
   */
  async queueSyncOperation(
    prisma: TutorPrismaClient,
    request: SyncRequest,
  ): Promise<SyncResult> {
    try {
      // Check for existing pending operations on the same resource
      const existingOperation = await prisma.offlineSyncQueue.findFirst({
        where: {
          tenantId: request.tenantId,
          userId: request.userId,
          deviceId: request.deviceId,
          resourceType: request.resourceType,
          resourceId: request.resourceId,
          operationStatus: { in: ["PENDING", "PROCESSING"] },
        },
      });

      if (existingOperation) {
        logger.warn({
          message: "Pending sync operation exists for resource",
          tenantId: request.tenantId,
          userId: request.userId,
          resourceType: request.resourceType,
          resourceId: request.resourceId,
          existingOperationId: existingOperation.id,
        }, "OfflineSyncService");

        return {
          success: false,
          operationId: existingOperation.id,
          conflictDetected: false,
          errorMessage: "Pending operation exists for this resource",
        };
      }

      // Create new sync operation
      const operation = await prisma.offlineSyncQueue.create({
        data: {
          tenantId: request.tenantId,
          userId: request.userId,
          deviceId: request.deviceId,
          operationType: request.operationType,
          resourceType: request.resourceType,
          resourceId: request.resourceId,
          resourceVersion: request.expectedVersion ?? 1,
          payload: request.payload,
          operationStatus: "PENDING",
        },
      });

      logger.info({
        message: "Sync operation queued",
        tenantId: request.tenantId,
        userId: request.userId,
        operationId: operation.id,
        resourceType: request.resourceType,
        resourceId: request.resourceId,
        operationType: request.operationType,
      }, "OfflineSyncService");

      return {
        success: true,
        operationId: operation.id,
        conflictDetected: false,
      };
    } catch (error) {
      logger.error({
        message: "Failed to queue sync operation",
        tenantId: request.tenantId,
        userId: request.userId,
        error,
      }, "OfflineSyncService");
      throw error;
    }
  }

  /**
   * Process pending sync operations for a user
   */
  async processPendingSyncOperations(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId: UserId,
    deviceId: string,
  ): Promise<SyncResult[]> {
    try {
      const pendingOperations = await prisma.offlineSyncQueue.findMany({
        where: {
          tenantId,
          userId,
          deviceId,
          operationStatus: "PENDING",
          nextAttemptAt: { lte: new Date() },
        },
        orderBy: { createdAt: "asc" },
      });

      const results: SyncResult[] = [];

      for (const operation of pendingOperations) {
        const result = await this.processSyncOperation(prisma, operation.id);
        results.push(result);
      }

      return results;
    } catch (error) {
      logger.error({
        message: "Failed to process pending sync operations",
        tenantId,
        userId,
        deviceId,
        error,
      }, "OfflineSyncService");
      throw error;
    }
  }

  /**
   * Process a single sync operation with conflict detection
   */
  private async processSyncOperation(
    prisma: TutorPrismaClient,
    operationId: string,
  ): Promise<SyncResult> {
    try {
      // Mark as processing
      await prisma.offlineSyncQueue.update({
        where: { id: operationId },
        data: {
          operationStatus: "PROCESSING",
          lastAttemptAt: new Date(),
        },
      });

      const operation = await prisma.offlineSyncQueue.findUnique({
        where: { id: operationId },
      });

      if (!operation) {
        throw new Error(`Sync operation not found: ${operationId}`);
      }

      // Fetch current server state for conflict detection
      const serverState = await this.fetchServerState(
        prisma,
        operation.resourceType,
        operation.resourceId,
      );

      // Detect conflicts
      const conflict = await this.detectConflict(operation, serverState);

      if (conflict.detected) {
        return await this.handleConflict(prisma, operation, serverState, conflict);
      }

      // Apply the operation
      await this.applyOperation(prisma, operation);

      // Mark as completed
      await prisma.offlineSyncQueue.update({
        where: { id: operationId },
        data: {
          operationStatus: "COMPLETED",
          completedAt: new Date(),
        },
      });

      logger.info({
        message: "Sync operation completed",
        operationId,
        resourceType: operation.resourceType,
        resourceId: operation.resourceId,
      }, "OfflineSyncService");

      return {
        success: true,
        operationId,
        conflictDetected: false,
      };
    } catch (error) {
      logger.error({
        message: "Failed to process sync operation",
        operationId,
        error,
      }, "OfflineSyncService");

      // Mark as failed with retry
      await prisma.offlineSyncQueue.update({
        where: { id: operationId },
        data: {
          operationStatus: "FAILED",
          attemptCount: { increment: 1 },
          lastAttemptAt: new Date(),
          nextAttemptAt: this.calculateRetryAttempt(1),
          errorMessage: error instanceof Error ? error.message : String(error),
        },
      });

      return {
        success: false,
        operationId,
        conflictDetected: false,
        errorMessage: error instanceof Error ? error.message : String(error),
      };
    }
  }

  /**
   * Detect conflicts between client and server state
   */
  private async detectConflict(
    operation: SyncOperation,
    serverState: Record<string, unknown> | null,
  ): Promise<{ detected: boolean; reason?: string; serverVersion?: Record<string, unknown> }> {
    // Version mismatch check
    if (serverState && "version" in serverState) {
      const serverVersion = serverState.version as number;
      if (serverVersion > operation.resourceVersion) {
        return {
          detected: true,
          reason: "version_mismatch",
          serverVersion,
        };
      }
    }

    // Concurrent edit detection (if server state was modified recently)
    if (serverState && "updatedAt" in serverState) {
      const serverUpdatedAt = serverState.updatedAt as Date;
      const timeDiff = Date.now() - serverUpdatedAt.getTime();
      // If server was updated within last 5 minutes, potential conflict
      if (timeDiff < 5 * 60 * 1000 && operation.operationType === "UPDATE") {
        return {
          detected: true,
          reason: "concurrent_edit",
          serverVersion: serverState,
        };
      }
    }

    // Data inconsistency check
    if (serverState && this.hasDataInconsistency(operation.payload, serverState)) {
      return {
        detected: true,
        reason: "data_inconsistency",
        serverVersion: serverState,
      };
    }

    return { detected: false };
  }

  /**
   * Handle detected conflicts with resolution strategy
   */
  private async handleConflict(
    prisma: TutorPrismaClient,
    operation: SyncOperation,
    serverState: Record<string, unknown> | null,
    conflict: { detected: boolean; reason?: string; serverVersion?: Record<string, unknown> },
  ): Promise<SyncResult> {
    const strategy = operation.conflictStrategy ?? "SERVER_WINS";
    const resolution = await this.resolveConflict(
      operation.payload,
      conflict.serverVersion ?? {},
      strategy,
    );

    // Log conflict
    await prisma.syncConflictLog.create({
      data: {
        tenantId: operation.tenantId,
        userId: operation.userId,
        deviceId: operation.deviceId,
        syncQueueId: operation.id,
        resourceType: operation.resourceType,
        resourceId: operation.resourceId,
        conflictType: conflict.reason ?? "unknown",
        conflictReason: conflict.reason ?? "unknown",
        clientPayload: operation.payload,
        serverPayload: conflict.serverVersion ?? {},
        mergedPayload: resolution.resolvedPayload,
        resolutionStrategy: strategy,
        resolvedBy: "auto",
        resolvedAt: new Date(),
        resolutionNotes: resolution.resolutionNotes,
        dataLossRisk: resolution.dataLossRisk,
        userNotified: false,
      },
    });

    // Apply resolution if successful
    if (resolution.dataLossRisk === false) {
      await this.applyOperation(prisma, {
        ...operation,
        payload: resolution.resolvedPayload,
      });

      await prisma.offlineSyncQueue.update({
        where: { id: operation.id },
        data: {
          operationStatus: "COMPLETED",
          completedAt: new Date(),
          conflictDetected: true,
          conflictStrategy: strategy,
          conflictReason: conflict.reason,
          resolvedAt: new Date(),
          resolvedBy: "auto",
        },
      });

      return {
        success: true,
        operationId: operation.id,
        conflictDetected: true,
        conflictResolution: strategy,
      };
    } else {
      // Manual resolution required
      await prisma.offlineSyncQueue.update({
        where: { id: operation.id },
        data: {
          operationStatus: "CONFLICT",
          conflictDetected: true,
          conflictStrategy: "MANUAL",
          conflictReason: conflict.reason,
          serverVersion: conflict.serverVersion,
          clientVersion: operation.payload,
        },
      });

      return {
        success: false,
        operationId: operation.id,
        conflictDetected: true,
        conflictResolution: "MANUAL",
        errorMessage: "Manual resolution required due to data loss risk",
      };
    }
  }

  /**
   * Resolve conflict using specified strategy
   */
  private async resolveConflict(
    clientPayload: Record<string, unknown>,
    serverPayload: Record<string, unknown>,
    strategy: ConflictResolutionStrategy,
  ): Promise<ConflictResolution> {
    switch (strategy) {
      case "SERVER_WINS":
        return {
          strategy,
          resolvedPayload: serverPayload,
          resolutionNotes: "Server version accepted, client changes discarded",
          dataLossRisk: true,
        };

      case "CLIENT_WINS":
        return {
          strategy,
          resolvedPayload: clientPayload,
          resolutionNotes: "Client version accepted, server changes discarded",
          dataLossRisk: true,
        };

      case "MERGE":
        return {
          strategy,
          resolvedPayload: this.mergePayloads(clientPayload, serverPayload),
          resolutionNotes: "Client and server versions merged",
          dataLossRisk: false,
        };

      case "MANUAL":
        return {
          strategy,
          resolvedPayload: serverPayload,
          resolutionNotes: "Manual resolution required",
          dataLossRisk: false,
        };

      default:
        return {
          strategy: "SERVER_WINS",
          resolvedPayload: serverPayload,
          resolutionNotes: "Default server wins strategy",
          dataLossRisk: true,
        };
    }
  }

  /**
   * Merge client and server payloads
   */
  private mergePayloads(
    client: Record<string, unknown>,
    server: Record<string, unknown>,
  ): Record<string, unknown> {
    const merged: Record<string, unknown> = { ...server };

    for (const [key, clientValue] of Object.entries(client)) {
      const serverValue = server[key];

      if (serverValue === undefined) {
        // Key only in client, add to merged
        merged[key] = clientValue;
      } else if (
        typeof clientValue === "object" &&
        clientValue !== null &&
        !Array.isArray(clientValue) &&
        typeof serverValue === "object" &&
        serverValue !== null &&
        !Array.isArray(serverValue)
      ) {
        // Both are objects, merge recursively
        merged[key] = this.mergePayloads(
          clientValue as Record<string, unknown>,
          serverValue as Record<string, unknown>,
        );
      }
      // If key exists in both and is not an object, server wins (no change)
    }

    return merged;
  }

  /**
   * Check for data inconsistency between payloads
   */
  private hasDataInconsistency(
    client: Record<string, unknown>,
    server: Record<string, unknown>,
  ): boolean {
    // Check for structural inconsistencies
    const clientKeys = new Set(Object.keys(client));
    const serverKeys = new Set(Object.keys(server));

    // Check for missing required keys
    const requiredKeys = ["id", "createdAt"];
    for (const key of requiredKeys) {
      if (serverKeys.has(key) && !clientKeys.has(key)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Fetch current server state for a resource
   */
  private async fetchServerState(
    prisma: TutorPrismaClient,
    resourceType: string,
    resourceId: string,
  ): Promise<Record<string, unknown> | null> {
    // This would fetch the actual resource from the appropriate table
    // For now, return null to indicate no conflict detection
    // In production, this would switch on resourceType and query the appropriate table
    return null;
  }

  /**
   * Apply sync operation to the database
   */
  private async applyOperation(
    prisma: TutorPrismaClient,
    operation: SyncOperation,
  ): Promise<void> {
    // This would apply the actual operation to the appropriate table
    // For now, this is a placeholder
    // In production, this would switch on resourceType and operationType
    logger.info({
      message: "Applying sync operation",
      operationId: operation.id,
      resourceType: operation.resourceType,
      resourceId: operation.resourceId,
      operationType: operation.operationType,
    }, "OfflineSyncService");
  }

  /**
   * Calculate retry attempt time with exponential backoff
   */
  private calculateRetryAttempt(attemptCount: number): Date {
    const baseDelay = 1000; // 1 second
    const maxDelay = 300000; // 5 minutes
    const delay = Math.min(baseDelay * Math.pow(2, attemptCount), maxDelay);
    return new Date(Date.now() + delay);
  }

  /**
   * Get pending sync operations for a user
   */
  async getPendingOperations(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId: UserId,
    deviceId: string,
  ): Promise<SyncOperation[]> {
    return await prisma.offlineSyncQueue.findMany({
      where: {
        tenantId,
        userId,
        deviceId,
        operationStatus: { in: ["PENDING", "PROCESSING"] },
      },
      orderBy: { createdAt: "asc" },
    });
  }

  /**
   * Get conflict log for a user
   */
  async getConflictLog(
    prisma: TutorPrismaClient,
    tenantId: TenantId,
    userId: UserId,
    deviceId: string,
    limit: number = 50,
  ): Promise<unknown[]> {
    return await prisma.syncConflictLog.findMany({
      where: {
        tenantId,
        userId,
        deviceId,
      },
      orderBy: { createdAt: "desc" },
      take: limit,
    });
  }

  /**
   * Manually resolve a conflict
   */
  async manuallyResolveConflict(
    prisma: TutorPrismaClient,
    operationId: string,
    resolvedPayload: Record<string, unknown>,
    userId: UserId,
    notes: string,
  ): Promise<SyncResult> {
    try {
      const operation = await prisma.offlineSyncQueue.findUnique({
        where: { id: operationId },
      });

      if (!operation) {
        throw new Error(`Sync operation not found: ${operationId}`);
      }

      if (operation.operationStatus !== "CONFLICT") {
        throw new Error(`Operation is not in conflict state: ${operationId}`);
      }

      // Apply the resolved payload
      await this.applyOperation(prisma, {
        ...operation,
        payload: resolvedPayload,
      });

      // Update operation status
      await prisma.offlineSyncQueue.update({
        where: { id: operationId },
        data: {
          operationStatus: "COMPLETED",
          completedAt: new Date(),
          conflictDetected: true,
          conflictStrategy: "MANUAL",
          resolvedAt: new Date(),
          resolvedBy: userId,
          payload: resolvedPayload,
        },
      });

      logger.info({
        message: "Conflict manually resolved",
        operationId,
        resolvedBy: userId,
      }, "OfflineSyncService");

      return {
        success: true,
        operationId,
        conflictDetected: true,
        conflictResolution: "MANUAL",
      };
    } catch (error) {
      logger.error({
        message: "Failed to manually resolve conflict",
        operationId,
        userId,
        error,
      }, "OfflineSyncService");
      throw error;
    }
  }
}

/**
 * Factory function
 */
export function createOfflineSyncService(): OfflineSyncService {
  return OfflineSyncService.getInstance();
}
