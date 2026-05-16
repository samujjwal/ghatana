/**
 * DataCloudApprovalProvider - Data Cloud-backed approval persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed approval provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  ApprovalDecision,
  ApprovalProvider,
  ApprovalRequest,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from '@ghatana/kernel-product-contracts';

export type ApprovalWorkflowStatus = 'pending' | 'approved' | 'rejected';

export interface DataCloudApprovalProviderOptions {
  readonly dataCloudUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
}

export class DataCloudApprovalProvider implements ApprovalProvider {
  readonly providerId = 'data-cloud-approvals';
  readonly version = '1.0.0';
  readonly backingStore = 'data-cloud' as const;
  readonly capabilities = ['approvals', 'platform-mode', 'data-cloud-backed'];

  private readonly dataCloudUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;

  constructor(options: DataCloudApprovalProviderOptions) {
    this.dataCloudUrl = options.dataCloudUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
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
    if (options.correlationId.trim().length === 0) {
      return fail('approval request requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/approvals/request', {
        method: 'POST',
        body: JSON.stringify({
          ...request,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to request approval from Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-approval-request:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud approval request failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async decideLifecycleApproval(
    decision: ApprovalDecision,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('approval decision requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/approvals/decide', {
        method: 'POST',
        body: JSON.stringify({
          ...decision,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to decide approval in Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-approval-decision:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud approval decision failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async getApprovalStatus(approvalId: string): Promise<{
    status: ApprovalWorkflowStatus;
    decision: ApprovalDecision | null;
  }> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
      approvalId,
    });

    try {
      const response = await this.fetch(`/api/v1/approvals/status?${params}`, {
        method: 'GET',
      });

      if (!response.ok) {
        throw new Error(`Failed to get approval status from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as {
        status: ApprovalWorkflowStatus;
        decision?: ApprovalDecision;
      };
      return {
        status: data.status,
        decision: data.decision || null,
      };
    } catch (error) {
      throw new Error(`Data Cloud approval status get failed: ${error instanceof Error ? error.message : String(error)}`);
    }
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

  async listPendingApprovals(): Promise<readonly ApprovalRequest[]> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
    });

    try {
      const response = await this.fetch(`/api/v1/approvals/pending?${params}`, {
        method: 'GET',
      });

      if (!response.ok) {
        throw new Error(`Failed to list pending approvals from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as { approvals?: ApprovalRequest[] };
      return data.approvals || [];
    } catch (error) {
      throw new Error(`Data Cloud pending approvals list failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private async fetch(path: string, options: RequestInit = {}): Promise<Response> {
    const url = `${this.dataCloudUrl}${path}`;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const response = await fetch(url, {
        ...options,
        signal: controller.signal,
        headers: {
          'Content-Type': 'application/json',
          'X-Tenant-ID': this.tenantId,
          ...(this.apiKey ? { 'Authorization': `Bearer ${this.apiKey}` } : {}),
          ...options.headers,
        },
      });

      return response;
    } finally {
      clearTimeout(timeoutId);
    }
  }
}

function fail(message: string, required: boolean): LifecycleProviderResult {
  return {
    success: false,
    error: required ? message : `optional approval write skipped: ${message}`,
  };
}
