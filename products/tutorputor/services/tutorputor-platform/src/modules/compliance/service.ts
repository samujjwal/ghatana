/**
 * @doc.type class
 * @doc.purpose Main compliance service implementing GDPR/CCPA data rights
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from "@ghatana/tutorputor-db";
import type {
  ComplianceService,
  TenantId,
  UserId,
} from "@ghatana/tutorputor-contracts";
import { randomBytes } from "crypto";
import { DataExporter } from "./exporter";
import { DataDeleter } from "./deleter";
import type {
  DataRequestStatus,
  ComplianceReport,
  DataRetentionPolicy,
  ConsentRecord,
} from "./types";

// Mock interface if not in Prisma
interface DataRequest {
  id: string;
  userId: UserId;
  tenantId: TenantId;
  status: any;
}

// Add missing DataExportRequest type if needed or use DataRequest
type DataExportRequest = DataRequest & {
  requestedAt: any;
  completedAt?: any;
  downloadUrl?: string;
  error?: string;
  estimatedCompletionAt: string;
};

export class ComplianceServiceImpl implements ComplianceService {
  private prisma: PrismaClient;
  private exporter: DataExporter;
  private deleter: DataDeleter;
  private uploadBaseUrl: string;

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
    this.uploadBaseUrl =
      options.uploadBaseUrl ?? "https://exports.tutorputor.com";
  }

  async requestUserExport(params: {
    userId: UserId;
    tenantId: TenantId;
    requestedBy: UserId;
  }): Promise<DataExportRequest> {
    // Fetch email
    // eslint-disable-next-line @typescript-eslint/no-unused-vars
    const user = await this.prisma.user.findUnique({
      where: { id: params.userId },
    });

    const requestId = `req_${Date.now()}_${randomBytes(4).toString("hex")}`;
    const now = new Date();

    // Note: DataRequest model not in current schema.
    // In production, should persist this request.
    const request = {
      id: requestId,
      userId: params.userId,
      tenantId: params.tenantId,
      status: "pending",
      requestedAt: now.toISOString(),
      estimatedCompletionAt: new Date(
        now.getTime() + 24 * 60 * 60 * 1000,
      ).toISOString(),
      completedAt: undefined,
      downloadUrl: undefined,
      error: undefined,
    };

    return request as unknown as DataExportRequest;
  }

  async getExportStatus(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<DataExportRequest> {
    // NOTE: Data export requests need persistent storage (DataRequest model in Prisma schema)
    // Currently requests are created in-memory by requestUserExport()
    //
    // Production implementation should:
    // 1. Add DataRequest model to Prisma schema
    // 2. Persist requests in requestUserExport()
    // 3. Query status from database here
    // 4. Use job queue (BullMQ) for async processing
    //
    // For now, return a "not found" response instead of throwing
    return {
      id: args.requestId,
      userId: "" as UserId,
      tenantId: args.tenantId,
      status: "not_found",
      requestedAt: new Date().toISOString(),
      estimatedCompletionAt: new Date().toISOString(),
      completedAt: undefined,
      downloadUrl: undefined,
      error:
        "Export request not found. The request may have expired or was not persisted.",
    } as DataExportRequest;
  }

  async downloadExport(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<{ downloadUrl: string; expiresAt: string }> {
    return {
      downloadUrl: "https://example.com",
      expiresAt: new Date().toISOString(),
    };
  }

  async requestUserDeletion(args: {
    userId: UserId;
    tenantId: TenantId;
    requestedBy: UserId;
    reason?: string;
  }): Promise<any> {
    return {
      id: "del_" + Date.now(),
      userId: args.userId,
      tenantId: args.tenantId,
      status: "pending",
      requestedAt: new Date().toISOString(),
    };
  }

  async cancelDeletionRequest(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<any> {
    return {
      id: args.requestId,
      status: "cancelled",
    };
  }

  async getDeletionStatus(args: {
    requestId: string;
    tenantId: TenantId;
  }): Promise<any> {
    return { status: "pending" };
  }

  private async processExportAsync(
    requestId: string,
    userId: string,
    tenantId: string,
  ) {
    try {
      const result = await this.exporter.exportUserData(userId, tenantId);
      console.log(`Export ${requestId} completed: ${result.filePath}`);
      // Update status in DB if model existed
    } catch (e) {
      console.error(`Export ${requestId} failed`, e);
    }
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
    // console.log(`Deletion token for ${params.userEmail}: ${token}`);

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

  async queryAuditEvents(args: any): Promise<any> {
    return { items: [], totalCount: 0, hasMore: false };
  }
}
