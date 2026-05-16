/**
 * DataCloudLifecycleEventProvider - Data Cloud-backed lifecycle event persistence.
 *
 * @doc.type class
 * @doc.purpose Data Cloud-backed lifecycle event provider for Kernel platform mode
 * @doc.layer kernel-providers
 * @doc.pattern Provider
 */

import type {
  KernelLifecycleEvent,
  LifecycleEventProvider,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
} from '@ghatana/kernel-product-contracts';
import { validateKernelLifecycleEvent } from '@ghatana/kernel-product-contracts';

export interface DataCloudLifecycleEventProviderOptions {
  readonly dataCloudUrl: string;
  readonly tenantId: string;
  readonly apiKey?: string;
  readonly timeoutMs?: number;
}

export interface DataCloudLifecycleEventProviderScope {
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
}

interface StoredLifecycleEvents {
  readonly schemaVersion: '1.0.0';
  readonly events: readonly KernelLifecycleEvent[];
}

export class DataCloudLifecycleEventProvider implements LifecycleEventProvider {
  readonly providerId = 'data-cloud-lifecycle-events';
  readonly version = '1.0.0';
  readonly backingStore = 'data-cloud' as const;
  readonly capabilities = ['lifecycle-events', 'platform-mode', 'data-cloud-backed'];

  private readonly dataCloudUrl: string;
  private readonly tenantId: string;
  private readonly apiKey: string | undefined;
  private readonly timeoutMs: number;

  constructor(options: DataCloudLifecycleEventProviderOptions) {
    this.dataCloudUrl = options.dataCloudUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.apiKey = options.apiKey;
    this.timeoutMs = options.timeoutMs ?? 30000;
  }

  async appendEvent(
    event: KernelLifecycleEvent,
    options: LifecycleProviderWriteOptions
  ): Promise<LifecycleProviderResult> {
    const validation = validateKernelLifecycleEvent(event);
    if (!validation.valid) {
      return fail(`Invalid lifecycle event: ${validation.errors.join('; ')}`, options.required);
    }
    if (event.metadata.correlationId !== options.correlationId) {
      return fail(
        `Lifecycle event correlationId ${event.metadata.correlationId} does not match write correlationId ${options.correlationId}`,
        options.required
      );
    }

    try {
      const response = await this.fetch('/api/v1/events', {
        method: 'POST',
        body: JSON.stringify(event),
      });

      if (!response.ok) {
        const errorText = await response.text();
        return fail(`Failed to append event to Data Cloud: ${response.status} ${errorText}`, options.required);
      }

      const result = await response.json() as { ref?: string; id?: string };
      return { success: true, ref: result.ref || `data-cloud-event:${result.id}` };
    } catch (error) {
      return fail(`Data Cloud event append failed: ${error instanceof Error ? error.message : String(error)}`, options.required);
    }
  }

  async listEvents(query: LifecycleProviderQuery): Promise<readonly KernelLifecycleEvent[]> {
    const params = new URLSearchParams({
      tenantId: this.tenantId,
      productUnitId: query.productUnitId,
    });

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
      const response = await this.fetch(`/api/v1/events?${params}`, {
        method: 'GET',
      });

      if (!response.ok) {
        throw new Error(`Failed to list events from Data Cloud: ${response.status}`);
      }

      const data = await response.json() as { events?: KernelLifecycleEvent[] };
      return data.events || [];
    } catch (error) {
      throw new Error(`Data Cloud event list failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }

  async cleanupRetainedEvents(): Promise<LifecycleProviderResult> {
    try {
      // Data Cloud events are append-only; cleanup is handled by retention policies
      return { success: true, ref: 'data-cloud-retention-policy' };
    } catch (error) {
      return fail(`Data Cloud event cleanup failed: ${error instanceof Error ? error.message : String(error)}`, true);
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
    error: required ? message : `optional event write skipped: ${message}`,
  };
}
