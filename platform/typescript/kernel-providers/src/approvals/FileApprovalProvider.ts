/**
 * FileApprovalProvider - bootstrap approval workflow persistence.
 *
 * @doc.type class
 * @doc.purpose File-backed approval provider for Kernel bootstrap mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import * as fs from "node:fs/promises";
import * as path from "node:path";
import {
  ProductApprovalGateSchema,
  type ProductApprovalGate,
} from "@ghatana/kernel-release";
import type {
  ApprovalDecision,
  ApprovalProvider,
  ApprovalRequest,
  LifecycleApprovalProvider,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from "@ghatana/kernel-product-contracts";

export interface FileApprovalProviderOptions {
  readonly outputDirectory: string;
  readonly defaultEnvironment?: string;
  readonly allowDuplicateDecisions?: boolean;
}

export type ApprovalWorkflowStatus = "pending" | "approved" | "rejected";

interface StoredApprovals {
  readonly schemaVersion: "1.0.0";
  readonly requests: readonly ApprovalRequest[];
  readonly decisions: readonly ApprovalDecision[];
}

interface StoredApprovalGateRecord {
  readonly schemaVersion: "1.0.0";
  readonly gate: ProductApprovalGate;
}

export class FileApprovalProvider
  implements ApprovalProvider, LifecycleApprovalProvider
{
  readonly providerId = "file-approvals";
  readonly version = "1.0.0";
  readonly capabilities = ["approvals", "bootstrap-mode", "file-backed"];

  private readonly outputDirectory: string;
  private readonly defaultEnvironment: string;
  private readonly allowDuplicateDecisions: boolean;

  constructor(options: FileApprovalProviderOptions) {
    this.outputDirectory = options.outputDirectory;
    this.defaultEnvironment = options.defaultEnvironment ?? "bootstrap";
    this.allowDuplicateDecisions = options.allowDuplicateDecisions ?? false;
  }

  async requestApproval(request: ApprovalRequest): Promise<void> {
    const result = await this.requestLifecycleApproval(request, {
      required: true,
      correlationId: request.approvalId,
    });
    if (!result.success) {
      throw new Error(result.error);
    }
  }

  async requestLifecycleApproval(
    request: ApprovalRequest,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateApprovalRequest(request);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("approval request write requires correlationId", options.required);
    }

    try {
      const stored = await this.readStoredApprovals();
      if (stored.requests.some((storedRequest) => storedRequest.approvalId === request.approvalId)) {
        return fail(`approval request already exists: ${request.approvalId}`, options.required);
      }
      const nextStored = {
        schemaVersion: "1.0.0" as const,
        requests: [...stored.requests, request],
        decisions: stored.decisions,
      };
      await this.writeStoredApprovals(nextStored);
      await this.writeApprovalGate(request, stored.decisions);
      return { success: true, ref: this.approvalsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async getApprovalStatus(approvalId: string): Promise<{
    status: ApprovalWorkflowStatus;
    decision: ApprovalDecision | null;
  }> {
    const stored = await this.readStoredApprovals();
    const request = stored.requests.find(
      (storedRequest) => storedRequest.approvalId === approvalId
    );
    const decisions = stored.decisions.filter(
      (decision) => decision.approvalId === approvalId
    );
    const latestDecision = decisions.at(-1) ?? null;
    if (request === undefined) {
      return { status: "rejected", decision: latestDecision };
    }
    return {
      status: resolveApprovalStatus(request, decisions),
      decision: latestDecision,
    };
  }

  async recordDecision(decision: ApprovalDecision): Promise<void> {
    const result = await this.decideLifecycleApproval(decision, {
      required: true,
      correlationId: decision.approvalId,
    });
    if (!result.success) {
      throw new Error(result.error);
    }
  }

  async decideLifecycleApproval(
    decision: ApprovalDecision,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateApprovalDecision(decision);
    if (validation.length > 0) {
      return fail(validation.join("; "), options.required);
    }
    if (options.correlationId.trim().length === 0) {
      return fail("approval decision write requires correlationId", options.required);
    }

    try {
      const stored = await this.readStoredApprovals();
      const request = stored.requests.find(
        (storedRequest) => storedRequest.approvalId === decision.approvalId
      );
      if (request === undefined) {
        return fail(`approval request not found: ${decision.approvalId}`, options.required);
      }
      const duplicate = stored.decisions.some(
        (storedDecision) =>
          storedDecision.approvalId === decision.approvalId &&
          storedDecision.approvedBy === decision.approvedBy
      );
      if (duplicate && !this.allowDuplicateDecisions) {
        return fail(
          `approval decision already recorded for ${decision.approvalId} by ${decision.approvedBy}`,
          options.required
        );
      }
      const decisions = [...stored.decisions, decision];
      await this.writeStoredApprovals({
        schemaVersion: "1.0.0",
        requests: stored.requests,
        decisions,
      });
      await this.writeApprovalGate(request, decisions);
      return { success: true, ref: this.approvalsPath };
    } catch (error) {
      return fail(String(error).replace(/^Error: /, ""), options.required);
    }
  }

  async listPendingApprovals(): Promise<readonly ApprovalRequest[]> {
    const stored = await this.readStoredApprovals();
    return stored.requests.filter((request) => {
      const decisions = stored.decisions.filter(
        (decision) => decision.approvalId === request.approvalId
      );
      return resolveApprovalStatus(request, decisions) === "pending";
    });
  }

  private get approvalsPath(): string {
    return path.join(this.outputDirectory, "approvals.json");
  }

  private getApprovalGatePath(approvalId: string): string {
    return path.join(this.outputDirectory, "approval-gates", `${approvalId}.json`);
  }

  private async readStoredApprovals(): Promise<StoredApprovals> {
    try {
      const content = await fs.readFile(this.approvalsPath, "utf-8");
      const parsed = JSON.parse(content) as Partial<StoredApprovals>;
      if (
        parsed.schemaVersion !== "1.0.0" ||
        !Array.isArray(parsed.requests) ||
        !Array.isArray(parsed.decisions)
      ) {
        throw new Error("approvals file has invalid shape");
      }
      return {
        schemaVersion: "1.0.0",
        requests: parsed.requests,
        decisions: parsed.decisions,
      };
    } catch (error) {
      if (isFileNotFound(error)) {
        return { schemaVersion: "1.0.0", requests: [], decisions: [] };
      }
      throw error;
    }
  }

  private async writeStoredApprovals(stored: StoredApprovals): Promise<void> {
    await this.writeJsonFile(this.approvalsPath, stored);
  }

  private async writeApprovalGate(
    request: ApprovalRequest,
    decisions: readonly ApprovalDecision[]
  ): Promise<void> {
    const requestDecisions = decisions.filter(
      (decision) => decision.approvalId === request.approvalId
    );
    const gate = buildApprovalGate(
      request,
      requestDecisions,
      this.defaultEnvironment,
      this.allowDuplicateDecisions
    );
    ProductApprovalGateSchema.parse(gate);
    await this.writeJsonFile(this.getApprovalGatePath(request.approvalId), {
      schemaVersion: "1.0.0",
      gate,
    } satisfies StoredApprovalGateRecord);
  }

  private async writeJsonFile(filePath: string, content: unknown): Promise<void> {
    await fs.mkdir(path.dirname(filePath), { recursive: true });
    const tempPath = `${filePath}.${process.pid}.${Date.now()}.tmp`;
    await fs.writeFile(tempPath, `${JSON.stringify(content, null, 2)}\n`, "utf-8");
    await fs.rename(tempPath, filePath);
  }
}

function buildApprovalGate(
  request: ApprovalRequest,
  decisions: readonly ApprovalDecision[],
  environment: string,
  allowDuplicateApprovals: boolean
): ProductApprovalGate {
  return {
    approvalId: request.approvalId,
    productId: request.productUnitId,
    runId: request.runId ?? request.approvalId,
    correlationId: request.correlationId ?? request.approvalId,
    environment: request.environment ?? environment,
    gateName: request.approvalId,
    action: request.action ?? request.reason,
    riskLevel: request.riskLevel ?? "medium",
    requestedBy: request.requestedBy,
    requestedAt: request.requestedAt ?? new Date().toISOString(),
    evidenceRefs: [...(request.evidenceRefs ?? [])],
    approvalPolicy: {
      minApprovals: request.requiredApprovers.length,
      allowDuplicateApprovals,
      requireEvidenceForRisk: ["critical"],
      requireCommentOnRejection: true,
    },
    required: true,
    approvers: [...request.requiredApprovers],
    approvals: decisions.map((decision) => ({
      approvalId: decision.approvalId,
      approver: decision.approvedBy,
      approved: decision.approved,
      timestamp: decision.decidedAt,
      decidedAt: decision.decidedAt,
      comment: decision.reason,
      evidenceRefs: [...(decision.evidenceRefs ?? [])],
    })),
    status: resolveApprovalStatus(request, decisions),
    requiredApprovals: request.requiredApprovers.length,
    ...(resolveApprovalStatus(request, decisions) !== "pending"
      ? { decidedAt: decisions.at(-1)?.decidedAt ?? new Date().toISOString() }
      : {}),
  };
}

function resolveApprovalStatus(
  request: ApprovalRequest,
  decisions: readonly ApprovalDecision[]
): ApprovalWorkflowStatus {
  if (decisions.some((decision) => !decision.approved)) {
    return "rejected";
  }
  const approvedPrincipals = new Set(
    decisions
      .filter((decision) => decision.approved)
      .map((decision) => decision.approvedBy)
  );
  return request.requiredApprovers.every((approver) =>
    approvedPrincipals.has(approver)
  )
    ? "approved"
    : "pending";
}

function validateApprovalRequest(request: ApprovalRequest): string[] {
  const errors: string[] = [];
  if (request.approvalId.trim().length === 0) {
    errors.push("approval request requires approvalId");
  }
  if (request.productUnitId.trim().length === 0) {
    errors.push("approval request requires productUnitId");
  }
  if (request.requestedBy.trim().length === 0) {
    errors.push("approval request requires requestedBy");
  }
  if (request.reason.trim().length === 0) {
    errors.push("approval request requires reason");
  }
  if (request.requiredApprovers.length === 0) {
    errors.push("approval request requires approvers");
  }
  if (request.requiredApprovers.some((approver) => approver.trim().length === 0)) {
    errors.push("approval request approvers must be non-empty");
  }
  if (!isIsoTimestamp(request.expiresAt)) {
    errors.push("approval request requires ISO expiresAt");
  }
  return errors;
}

function validateApprovalDecision(decision: ApprovalDecision): string[] {
  const errors: string[] = [];
  if (decision.approvalId.trim().length === 0) {
    errors.push("approval decision requires approvalId");
  }
  if (decision.approvedBy.trim().length === 0) {
    errors.push("approval decision requires approvedBy");
  }
  if (decision.reason.trim().length === 0) {
    errors.push("approval decision requires reason");
  }
  if (!isIsoTimestamp(decision.decidedAt)) {
    errors.push("approval decision requires ISO decidedAt");
  }
  return errors;
}

function isIsoTimestamp(value: string): boolean {
  return !Number.isNaN(Date.parse(value));
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional approval write skipped: ${message}`,
  };
}

function isFileNotFound(error: unknown): boolean {
  return (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    (error as { readonly code?: unknown }).code === "ENOENT"
  );
}
