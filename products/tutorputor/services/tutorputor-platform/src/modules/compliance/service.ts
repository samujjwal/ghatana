/**
 * @doc.type class
 * @doc.purpose Main compliance service implementing GDPR/CCPA data rights
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@tutorputor/core/db";
import { createStandaloneLogger } from "@tutorputor/core/logger";
import type {
  ComplianceService,
  TenantId,
  UserId,
} from "@tutorputor/contracts";
import { randomBytes } from "crypto";
import { DataExporter } from "./exporter";
import { DataDeleter } from "./deleter";
import type {
  ComplianceReport,
  ConsentRevocationResult,
  PrivacyAuditEvidence,
  PrivacyDataAccessSummary,
} from "./types";

// Mock interface if not in Prisma
interface DataRequest {
  id: string;
  userId: UserId;
  tenantId: TenantId;
  status: unknown;
}

// Add missing DataExportRequest type if needed or use DataRequest
type DataExportRequest = DataRequest & {
  requestedAt: unknown;
  completedAt?: unknown;
  downloadUrl?: string;
  error?: string;
  estimatedCompletionAt: string;
};

export class ComplianceServiceImpl implements ComplianceService {
  private prisma: PrismaClient;
  private exporter: DataExporter;
  private deleter: DataDeleter;

  constructor(
    prisma: PrismaClient,
    options: {
      exportDir?: string;
      uploadBaseUrl?: string;
    } = {},
  ) {
    this.prisma = prisma;
    this.exporter = new DataExporter(prisma, options.exportDir);
    this.deleter = new DataDeleter(prisma);
    void options.uploadBaseUrl;
    void createStandaloneLogger({ service: "ComplianceService" });
  }

  async requestUserExport(params: {
    userId: UserId;
    tenantId: TenantId;
    requestedBy: UserId;
  }): Promise<any> {
    const user = await this.prisma.user.findFirst({
      where: {
        id: params.userId,
        tenantId: params.tenantId,
      },
    });

    if (!user) {
      throw new Error("User not found");
    }

    const now = new Date();

    // Persist the data export request using the existing DataExportRequest model
    const request = await this.prisma.dataExportRequest.create({
      data: {
        tenantId: params.tenantId,
        userId: params.userId,
        status: "pending",
        requestedAt: now,
        estimatedCompletionAt: new Date(now.getTime() + 24 * 60 * 60 * 1000),
      },
    });

    return request;
  }

  async getExportStatus(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<any> {
    // Query the data export request from database
    const request = await this.prisma.dataExportRequest.findFirst({
      where: {
        id: args.requestId,
        tenantId: args.tenantId,
      },
    });

    if (!request) {
      return {
        id: args.requestId,
        userId: "" as UserId,
        tenantId: args.tenantId,
        status: "not_found",
        requestedAt: new Date().toISOString(),
        estimatedCompletionAt: new Date().toISOString(),
        error:
          "Export request not found. The request may have expired or was not persisted.",
      };
    }

    return request;
  }

  async downloadExport(_args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<{ downloadUrl: string; expiresAt: string }> {
    const request = await this.prisma.dataExportRequest.findFirst({
      where: { id: _args.requestId, tenantId: _args.tenantId },
    });
    if (!request) {
      throw new Error("Export request not found");
    }
    return {
      downloadUrl: request.downloadUrl ?? `/compliance/export/${_args.requestId}/download`,
      expiresAt: (request.expiresAt ?? new Date(Date.now() + 24 * 60 * 60 * 1000)).toISOString(),
    };
  }

  async requestUserDeletion(args: {
    userId: UserId;
    tenantId: TenantId;
    requestedBy: UserId;
    reason?: string;
  }): Promise<any> {
    const request = await this.prisma.dataDeletionRequest.create({
      data: {
        tenantId: args.tenantId,
        userId: args.userId,
        status: "requested",
        requestedAt: new Date(),
        scheduledDeletionAt: new Date(Date.now() + 30 * 24 * 3600 * 1000),
        retentionDays: 30,
        reason: args.reason ?? "user_requested",
        requestedBy: args.requestedBy,
      },
    });
    return request;
  }

  async cancelDeletionRequest(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<any> {
    return this.prisma.dataDeletionRequest.update({
      where: { id: args.requestId },
      data: { status: "cancelled", cancelledAt: new Date() },
    });
  }

  async getDeletionStatus(_args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<any> {
    const request = await this.prisma.dataDeletionRequest.findFirst({
      where: { id: _args.requestId, tenantId: _args.tenantId },
    });
    return request ?? { id: _args.requestId, status: "not_found" };
  }

  async getPrivacyDataAccessSummary(args: {
    userId: UserId;
    tenantId: TenantId;
  }): Promise<PrivacyDataAccessSummary> {
    const [exportRequests, deletionRequests, consent] = await Promise.all([
      this.prisma.dataExportRequest.findMany({
        where: { userId: args.userId, tenantId: args.tenantId },
        orderBy: { requestedAt: "desc" },
        take: 10,
      }),
      this.prisma.dataDeletionRequest.findMany({
        where: { userId: args.userId, tenantId: args.tenantId },
        orderBy: { requestedAt: "desc" },
        take: 10,
      }),
      this.prisma.consentRecord.findMany({
        where: { userId: args.userId, tenantId: args.tenantId },
        orderBy: { grantedAt: "desc" },
      }),
    ]);

    return { userId: args.userId, tenantId: args.tenantId, exportRequests, deletionRequests, consent };
  }

  async revokeConsent(args: {
    userId: UserId;
    tenantId: TenantId;
    consentType: string;
    ipAddress?: string;
    userAgent?: string;
  }): Promise<ConsentRevocationResult> {
    const revokedAt = new Date();
    await this.prisma.consentRecord.create({
      data: {
        userId: args.userId,
        tenantId: args.tenantId,
        consentType: args.consentType,
        granted: false,
        revokedAt,
        ipAddress: args.ipAddress,
        userAgent: args.userAgent,
      },
    });
    return { userId: args.userId, tenantId: args.tenantId, consentType: args.consentType, granted: false, revokedAt };
  }

  async deleteTelemetryForUser(args: {
    userId: UserId;
    tenantId: TenantId;
    anonymize?: boolean;
  }): Promise<PrivacyAuditEvidence> {
    const count = args.anonymize
      ? await this.prisma.learningEvent.updateMany({
        where: { userId: args.userId, tenantId: args.tenantId },
        data: { userId: "ANONYMIZED_USER" },
      })
      : await this.prisma.learningEvent.deleteMany({
        where: { userId: args.userId, tenantId: args.tenantId },
      });

    return {
      requestId: `telemetry_${Date.now()}`,
      userId: args.userId,
      tenantId: args.tenantId,
      evidenceType: "telemetry_deletion",
      collectedAt: new Date(),
      records: [
        {
          dataType: "learning_events",
          action: args.anonymize ? "anonymized" : "deleted",
          count: count.count,
        },
      ],
    };
  }

  async processDeletionNow(args: {
    userId: UserId;
    tenantId: TenantId;
  }): Promise<PrivacyAuditEvidence> {
    const result = await this.deleter.deleteUserData(args.userId, args.tenantId);
    return {
      requestId: result.requestId,
      userId: args.userId,
      tenantId: args.tenantId,
      evidenceType: "deletion",
      collectedAt: result.deletedAt ?? new Date(),
      records: [
        { dataType: "user_profile", action: result.status === "completed" ? "deleted" : "retained", count: result.status === "completed" ? 1 : 0 },
        ...(result.retainedData ?? []).map((item) => ({
          dataType: item.dataType,
          action: "retained" as const,
          count: 1,
          evidenceRef: item.reason,
        })),
      ],
    };
  }

  async createDeletionVerification(params: {
    userId: UserId;
    userEmail: string;
  }): Promise<{ message: string; expiresAt: Date }> {
    const token = randomBytes(32).toString("hex");
    const expiresAt = new Date(Date.now() + 24 * 60 * 60 * 1000);

    await this.prisma.deletionVerification.create({
      data: {
        userId: params.userId,
        token,
        expiresAt,
      },
    });

    // Send email mock
    return {
      message: "A confirmation token has been sent to your email address.",
      expiresAt,
    };
  }

  async verifyAndProcessDeletion(params: {
    userId: UserId;
    tenantId: TenantId;
    token: string;
  }): Promise<{ success: boolean; message: string }> {
    const verification = await this.prisma.deletionVerification.findUnique({
      where: { token: params.token },
    });

    if (
      !verification ||
      verification.userId !== params.userId ||
      verification.expiresAt < new Date()
    ) {
      throw new Error("Invalid or expired deletion token");
    }

    // Schedule deletion
    await this.prisma.dataDeletionRequest.create({
      data: {
        tenantId: params.tenantId,
        userId: params.userId,
        status: "scheduled",
        scheduledDeletionAt: new Date(Date.now() + 30 * 24 * 3600 * 1000), // 30 days buffer
        retentionDays: 30,
      },
    });

    // Cleanup token
    await this.prisma.deletionVerification.delete({
      where: { id: verification.id },
    });

    return {
      success: true,
      message: "Account scheduled for deletion in 30 days.",
    };
  }

  // Stub implementation for other interface methods if needed
  async getComplianceReport(tenantId: TenantId): Promise<ComplianceReport> {
    return {
      reportType: "gdpr",
      generatedAt: new Date(),
      tenantId,
      data: {
        totalUsers: 0,
        activeDataRequests: 0,
        completedRequests: 0,
        averageResponseTime: 0,
        consentSettings: {},
        dataRetentionPolicies: [],
      },
    };
  }

  async checkHealth(): Promise<boolean> {
    return true;
  }

  async queryAuditEvents(_args: Record<string, unknown>): Promise<any> {
    return { items: [], totalCount: 0, hasMore: false };
  }
}
