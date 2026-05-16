/**
 * DataCloudHealthProvider - Data Cloud-backed health snapshot persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed health snapshot provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  HealthStatus,
  LifecycleHealthProvider,
  LifecycleHealthSnapshot,
  LifecycleHealthSnapshotRef,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from '@ghatana/kernel-product-contracts';

export interface DataCloudHealthProviderOptions {
  readonly dataCloudUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
}

export interface DataCloudLifecycleHealthSnapshotWriteOptions
  extends LifecycleProviderWriteOptions {
  readonly runId: string;
}

export class DataCloudHealthProvider implements LifecycleHealthProvider {
  readonly providerId = 'data-cloud-health-snapshots';
  readonly version = '1.0.0';
  readonly backingStore = 'data-cloud' as const;
  readonly capabilities = ['health-snapshots', 'platform-mode', 'data-cloud-backed'];

  private readonly dataCloudUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;

  constructor(options: DataCloudHealthProviderOptions) {
    this.dataCloudUrl = options.dataCloudUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
  }

  async writeLifecycleHealthSnapshot(
    snapshot: LifecycleHealthSnapshot,
    options: DataCloudLifecycleHealthSnapshotWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('health snapshot write requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/health-snapshots', {
        method: 'POST',
        body: JSON.stringify({
          snapshot,
          runId: options.runId,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to write health snapshot to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-health-snapshot:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud health snapshot write failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async recordHealthSnapshot(
    snapshot: LifecycleHealthSnapshotRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('health snapshot record requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/health-snapshot-refs', {
        method: 'POST',
        body: JSON.stringify({
          ...snapshot,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to record health snapshot to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-health-snapshot-ref:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud health snapshot record failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async getLatestHealthSnapshot(
    productUnitId: string
  ): Promise<LifecycleHealthSnapshotRef | null> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
      productUnitId,
    });

    try {
      const response = await this.fetch(`/api/v1/health-snapshot-refs/latest?${params}`, {
        method: 'GET',
      });

      if (response.status === 404) {
        return null;
      }

      if (!response.ok) {
        throw new Error(`Failed to get latest health snapshot from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as { snapshot?: LifecycleHealthSnapshotRef };
      return data.snapshot || null;
    } catch (error) {
      throw new Error(`Data Cloud health snapshot get failed: ${error instanceof Error ? error.message : String(error)}`);
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
    error: required ? message : `optional health snapshot write skipped: ${message}`,
  };
}
