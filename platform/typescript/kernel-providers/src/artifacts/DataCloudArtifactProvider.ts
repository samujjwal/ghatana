/**
 * DataCloudArtifactProvider - Data Cloud-backed artifact manifest persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed artifact manifest provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  ArtifactManifest,
} from '@ghatana/kernel-artifacts';
import type {
  LifecycleArtifactManifestRef,
  LifecycleArtifactProvider,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from '@ghatana/kernel-product-contracts';

export interface DataCloudArtifactProviderOptions {
  readonly dataCloudUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
}

export interface DataCloudArtifactManifestWriteOptions
  extends LifecycleProviderWriteOptions {
  readonly runId: string;
}

export class DataCloudArtifactProvider implements LifecycleArtifactProvider {
  readonly providerId = 'data-cloud-artifact-manifests';
  readonly version = '1.0.0';
  readonly backingStore = 'data-cloud' as const;
  readonly capabilities = ['artifact-manifests', 'platform-mode', 'data-cloud-backed'];

  private readonly dataCloudUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;

  constructor(options: DataCloudArtifactProviderOptions) {
    this.dataCloudUrl = options.dataCloudUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
  }

  async writeArtifactManifest(
    manifest: ArtifactManifest,
    options: DataCloudArtifactManifestWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('artifact manifest write requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/artifacts', {
        method: 'POST',
        body: JSON.stringify({
          manifest,
          runId: options.runId,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to write artifact manifest to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-artifact:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud artifact manifest write failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async recordArtifactManifest(
    manifest: LifecycleArtifactManifestRef,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    if (options.correlationId.trim().length === 0) {
      return fail('artifact manifest record requires correlationId', options.required);
    }

    try {
      const response = await this.fetch('/api/v1/artifact-refs', {
        method: 'POST',
        body: JSON.stringify({
          ...manifest,
          tenantId: this.tenantId,
          correlationId: options.correlationId,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to record artifact manifest to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-artifact-ref:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud artifact manifest record failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async listArtifactManifests(
    query: LifecycleProviderQuery
  ): Promise<readonly LifecycleArtifactManifestRef[]> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
    });

    if (query.productUnitId !== undefined) {
      params.append('productUnitId', query.productUnitId);
    }
    if (query.runId !== undefined) {
      params.append('runId', query.runId);
    }
    if (query.correlationId !== undefined) {
      params.append('correlationId', query.correlationId);
    }
    if (query.cursor !== undefined) {
      params.append('cursor', query.cursor);
    }
    if (query.limit !== undefined) {
      params.append('limit', String(query.limit));
    }

    try {
      const response = await this.fetch(`/api/v1/artifact-refs?${params}`, {
        method: 'GET',
      });

      if (!response.ok) {
        throw new Error(`Failed to list artifact manifests from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as { manifests?: LifecycleArtifactManifestRef[] };
      return data.manifests || [];
    } catch (error) {
      throw new Error(`Data Cloud artifact manifest list failed: ${error instanceof Error ? error.message : String(error)}`);
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
    error: required ? message : `optional artifact manifest write skipped: ${message}`,
  };
}
