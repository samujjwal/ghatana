/**
 * DataCloudRuntimeTruthProvider - Data Cloud-backed runtime truth persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed runtime truth provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
  LifecycleRuntimeTruthProvider,
  LifecycleRuntimeTruthSnapshot,
} from '@ghatana/kernel-product-contracts';

export interface DataCloudRuntimeTruthProviderOptions {
  readonly dataCloudUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
}

export class DataCloudRuntimeTruthProvider implements LifecycleRuntimeTruthProvider {
  readonly providerId = 'data-cloud-runtime-truth';
  readonly version = '1.0.0';
  readonly backingStore = 'data-cloud' as const;
  readonly capabilities = ['runtime-truth', 'platform-mode', 'data-cloud-backed'];

  private readonly dataCloudUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;

  constructor(options: DataCloudRuntimeTruthProviderOptions) {
    this.dataCloudUrl = options.dataCloudUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
  }

  async recordRuntimeTruth(
    record: LifecycleRuntimeTruthSnapshot,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('runtime truth write requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/runtime-truth', {
        method: 'POST',
        body: JSON.stringify({
          ...record,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to record runtime truth to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-runtime-truth:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud runtime truth write failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async getRuntimeTruth(productUnitId: string, runId?: string): Promise<LifecycleRuntimeTruthSnapshot | null> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
      productUnitId,
    });

    if (runId !== undefined) {
      params.append('runId', runId);
    }

    try {
      const response = await this.fetch(`/api/v1/runtime-truth?${params}`, {
        method: 'GET',
      });

      if (response.status === 404) {
        return null;
      }

      if (!response.ok) {
        throw new Error(`Failed to get runtime truth from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as { record?: LifecycleRuntimeTruthSnapshot };
      return data.record || null;
    } catch (error) {
      throw new Error(`Data Cloud runtime truth get failed: ${error instanceof Error ? error.message : String(error)}`);
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
    error: required ? message : `optional runtime truth write skipped: ${message}`,
  };
}
