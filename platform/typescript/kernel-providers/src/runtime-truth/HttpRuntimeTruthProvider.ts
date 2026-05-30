/**
 * HttpRuntimeTruthProvider - HTTP-backed runtime truth persistence.
 *
 * @doc.type class
 * @doc.purpose Generic HTTP-backed runtime truth provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
  LifecycleRuntimeTruthProvider,
  LifecycleRuntimeTruthSnapshot,
} from '@ghatana/kernel-product-contracts';

export interface HttpRuntimeTruthProviderOptions {
  readonly baseUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
  readonly endpointPrefix?: string;
}

export class HttpRuntimeTruthProvider implements LifecycleRuntimeTruthProvider {
  readonly providerId = 'http-runtime-truth';
  readonly version = '1.0.0';
  readonly backingStore = 'external' as const;
  readonly capabilities = ['runtime-truth', 'platform-mode', 'http-backed'];

  private readonly baseUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;
  private readonly endpointPrefix: string;

  constructor(options: HttpRuntimeTruthProviderOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
    this.endpointPrefix = options.endpointPrefix ?? '/api/v1';
  }

  async recordRuntimeTruth(
    record: LifecycleRuntimeTruthSnapshot,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('runtime truth write requires correlationId', options.required);
    }

    try {
      const response = await this.fetch(`${this.endpointPrefix}/runtime-truth`, {
        method: 'POST',
        body: JSON.stringify({
          ...record,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to record runtime truth: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `http-runtime-truth:${result.id}` };
    } catch (error) {
      return fail(`Runtime truth write failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
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
      const response = await this.fetch(`${this.endpointPrefix}/runtime-truth?${params}`, {
        method: 'GET',
      });

      if (response.status === 404) {
        return null;
      }

      if (!response.ok) {
        throw new Error(`Failed to get runtime truth: ${response.status}`);
      }

      const data = await response.json() as { record?: LifecycleRuntimeTruthSnapshot };
      return data.record || null;
    } catch (error) {
      throw new Error(`Runtime truth get failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  private async fetch(path: string, options: RequestInit = {}): Promise<Response> {
    const url = `${this.baseUrl}${path}`;
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
