/**
 * AI Audit Service
 *
 * Logs every AI inference with tenant, user, model, version, policy decision, timestamp.
 *
 * @doc.type service
 * @doc.purpose AI model usage audit logging
 * @doc.layer platform
 * @doc.pattern Service
 */
import type { PrismaClient } from "@tutorputor/core/db";

interface AIAuditLogDelegate {
  create(args: { data: Record<string, unknown> }): Promise<unknown>;
}

interface PrismaWithOptionalAIAuditLog {
  aIAuditLog?: AIAuditLogDelegate;
}

export interface AIAuditLogEntry {
  tenantId: string;
  userId: string;
  modelId: string;
  modelName?: string;
  modelVersion?: string;
  endpoint: string;
  requestPayload?: string;
  responsePayload?: string;
  policyDecision?: string;
  failureReason?: "policy_blocked" | "service_unavailable" | "rate_limited" | "validation_error" | "service_error";
  latencyMs?: number;
  success: boolean;
  errorMessage?: string;
  ipAddress?: string;
  userAgent?: string;
}

export class AIAuditService {
  constructor(private prisma: PrismaClient) {}

  /**
   * Log an AI inference
   */
  async logInference(entry: AIAuditLogEntry): Promise<void> {
    const auditLogDelegate = (
      this.prisma as unknown as PrismaWithOptionalAIAuditLog | undefined
    )?.aIAuditLog;
    if (!auditLogDelegate) {
      console.warn("ai_audit_log_delegate_unavailable", {
        tenantId: entry.tenantId,
        userId: entry.userId,
        modelId: entry.modelId,
        endpoint: entry.endpoint,
        success: entry.success,
      });
      return;
    }

    // Truncate large payloads to avoid database bloat
    const maxPayloadSize = 10000; // 10KB
    const requestPayload = entry.requestPayload
      ? entry.requestPayload.substring(0, maxPayloadSize)
      : null;
    const responsePayload = entry.responsePayload
      ? entry.responsePayload.substring(0, maxPayloadSize)
      : null;

    try {
      await auditLogDelegate.create({
        data: {
          tenantId: entry.tenantId,
          userId: entry.userId,
          modelId: entry.modelId,
          modelName: entry.modelName || null,
          modelVersion: entry.modelVersion || null,
          endpoint: entry.endpoint,
          requestPayload,
          responsePayload,
          policyDecision: entry.policyDecision || null,
          latencyMs: entry.latencyMs || null,
          success: entry.success,
          errorMessage: entry.errorMessage || null,
          ipAddress: entry.ipAddress || null,
          userAgent: entry.userAgent || null,
        },
      });
    } catch (error: unknown) {
      console.warn("ai_audit_log_write_failed", {
        tenantId: entry.tenantId,
        userId: entry.userId,
        modelId: entry.modelId,
        endpoint: entry.endpoint,
        success: entry.success,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  /**
   * Get AI audit logs for a tenant
   */
  async getTenantAuditLogs(
    tenantId: string,
    options: {
      limit?: number;
      offset?: number;
      userId?: string;
      modelId?: string;
      endpoint?: string;
      startDate?: Date;
      endDate?: Date;
    } = {},
  ) {
    const { limit = 100, offset = 0, userId, modelId, endpoint, startDate, endDate } = options;

    const where: {
      tenantId: string;
      userId?: string;
      modelId?: string;
      endpoint?: string;
      createdAt?: {
        gte?: Date;
        lte?: Date;
      };
    } = { tenantId };

    if (userId) where.userId = userId;
    if (modelId) where.modelId = modelId;
    if (endpoint) where.endpoint = endpoint;
    if (startDate || endDate) {
      where.createdAt = {};
      if (startDate) where.createdAt.gte = startDate;
      if (endDate) where.createdAt.lte = endDate;
    }

    const [logs, total] = await Promise.all([
      this.prisma.aIAuditLog.findMany({
        where,
        orderBy: { createdAt: "desc" },
        take: limit,
        skip: offset,
      }),
      this.prisma.aIAuditLog.count({ where }),
    ]);

    return { logs, total };
  }

  /**
   * Get AI usage statistics for a tenant
   */
  async getTenantUsageStats(tenantId: string, startDate?: Date, endDate?: Date) {
    const where: {
      tenantId: string;
      createdAt?: {
        gte?: Date;
        lte?: Date;
      };
    } = { tenantId };
    if (startDate || endDate) {
      where.createdAt = {};
      if (startDate) where.createdAt.gte = startDate;
      if (endDate) where.createdAt.lte = endDate;
    }

    const [total, success, failed, byEndpoint, byModel] = await Promise.all([
      this.prisma.aIAuditLog.count({ where }),
      this.prisma.aIAuditLog.count({ where: { ...where, success: true } }),
      this.prisma.aIAuditLog.count({ where: { ...where, success: false } }),
      this.prisma.aIAuditLog.groupBy({
        by: ["endpoint"],
        where,
        _count: true,
        orderBy: { _count: { endpoint: "desc" } },
      }),
      this.prisma.aIAuditLog.groupBy({
        by: ["modelId", "modelName"],
        where,
        _count: true,
        orderBy: { _count: { modelId: "desc" } },
      }),
    ]);

    const avgLatency = await this.prisma.aIAuditLog.aggregate({
      where: { ...where, latencyMs: { not: null } },
      _avg: { latencyMs: true },
    });

    return {
      total,
      success,
      failed,
      successRate: total > 0 ? (success / total) * 100 : 0,
      avgLatencyMs: avgLatency._avg.latencyMs,
      byEndpoint,
      byModel,
    };
  }
}
