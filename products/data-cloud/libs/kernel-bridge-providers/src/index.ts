import type {
  ApprovalDecision,
  ApprovalRequest,
  KernelLifecycleEvent,
  KernelLifecycleProviderContext,
  LifecycleApprovalProvider,
  LifecycleArtifactManifestRef,
  LifecycleArtifactProvider,
  LifecycleEventProvider,
  LifecycleHealthProvider,
  LifecycleHealthSnapshotRef,
  LifecycleMemoryProvider,
  LifecycleMemoryRecord,
  LifecycleProviderQuery,
  LifecycleProviderResult,
  LifecycleProviderWriteOptions,
  LifecycleProvenanceProvider,
  LifecycleProvenanceRecord,
  LifecycleRuntimeTruthProvider,
  LifecycleRuntimeTruthSnapshot,
} from '@ghatana/kernel-product-contracts';
import { z } from 'zod';

// Zod schemas for Data Cloud provider responses
const DataCloudProviderResponseSchema = z.object({
  success: z.boolean(),
  ref: z.string().optional(),
  error: z.string().optional(),
});

const EventListResponseSchema = z.object({
  items: z.array(z.unknown()),
});

const ArtifactManifestRefSchema = z.object({
  productUnitId: z.string(),
  manifestPath: z.string(),
});

const HealthSnapshotRefSchema = z.object({
  productUnitId: z.string(),
  snapshotPath: z.string(),
});

const ProvenanceRecordSchema = z.object({
  provenanceId: z.string(),
  evidenceRefs: z.array(z.unknown()),
});

const MemoryRecordSchema = z.object({
  memoryId: z.string(),
  contentRef: z.string(),
});

const RuntimeTruthSnapshotSchema = z.object({
  productUnitId: z.string(),
  observedAt: z.string(),
});

const KernelLifecycleEventSchema = z.object({
  metadata: z.object({
    eventId: z.string(),
  }),
});

// Privacy and retention metadata schema
const PrivacyMetadataSchema = z.object({
  privacyClassification: z.string().optional(),
  retention: z.object({
    expiresAt: z.string().optional(),
  }).optional(),
});

// Redact sensitive data from request bodies
function redactSensitiveData(data: unknown): unknown {
  if (typeof data !== 'object' || data === null) {
    return data;
  }
  if (Array.isArray(data)) {
    return data.map(redactSensitiveData);
  }
  const redacted: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(data)) {
    // Redact auth tokens
    if (key === 'authToken' || key === 'authorization' || key === 'token') {
      redacted[key] = '[REDACTED]';
    }
    // Redact evidence payload fields marked restricted
    else if (key === 'evidenceRefs' && Array.isArray(value)) {
      redacted[key] = value.map((evidence: unknown) => {
        if (typeof evidence === 'object' && evidence !== null && 'privacyClassification' in evidence) {
          const ev = evidence as Record<string, unknown>;
          if (ev.privacyClassification === 'restricted') {
            return { ...ev, payload: '[REDACTED]', content: '[REDACTED]' };
          }
        }
        return evidence;
      });
    }
    // Redact memory content refs if privacy level is restricted
    else if (key === 'privacyClassification' && value === 'restricted') {
      redacted[key] = value;
    }
    else if (key === 'contentRef' || key === 'content' || key === 'payload') {
      // Check if this is in a restricted context by looking at sibling fields
      if ('privacyClassification' in data && (data as Record<string, unknown>).privacyClassification === 'restricted') {
        redacted[key] = '[REDACTED]';
      } else {
        redacted[key] = redactSensitiveData(value);
      }
    }
    else {
      redacted[key] = redactSensitiveData(value);
    }
  }
  return redacted;
}

// Provider observability instrumentation interface
export interface DataCloudKernelProviderInstrumentation {
  recordRequestStart(params: {
    providerId: string;
    operation: string;
    method: string;
    path: string;
  }): void;
  recordRequestComplete(params: {
    providerId: string;
    operation: string;
    method: string;
    path: string;
    statusCode: number;
    durationMs: number;
    reasonCode?: string;
  }): void;
  recordRequestFailure(params: {
    providerId: string;
    operation: string;
    method: string;
    path: string;
    durationMs: number;
    error: string;
  }): void;
}

// Helper functions for error mapping
function safeErrorMessage(value: unknown, fallback: string): string {
  if (isRecord(value) && typeof value.error === 'string') {
    return value.error;
  }
  if (isRecord(value) && typeof value.message === 'string') {
    return value.message;
  }
  return fallback;
}

function extractReasonCode(value: unknown): string | undefined {
  if (isRecord(value) && typeof value.reasonCode === 'string') {
    return value.reasonCode;
  }
  return undefined;
}

function extractCorrelationId(value: unknown): string | undefined {
  if (isRecord(value) && typeof value.correlationId === 'string') {
    return value.correlationId;
  }
  return undefined;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

export interface DataCloudKernelProviderClientOptions {
  readonly baseUrl: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly authToken?: string;
  readonly timeoutMs?: number;
  readonly fetchImpl?: typeof fetch;
  readonly instrumentation?: DataCloudKernelProviderInstrumentation;
}

interface DataCloudRequestOptions {
  readonly correlationId?: string;
  readonly query?: Record<string, string | number | undefined>;
}

interface DataCloudProviderResponse {
  readonly success: boolean;
  readonly ref?: string;
  readonly error?: string;
}

export class DataCloudKernelProviderClient {
  private readonly baseUrl: string;
  private readonly tenantId: string;
  private readonly workspaceId: string;
  private readonly projectId: string;
  private readonly authToken: string | undefined;
  private readonly timeoutMs: number;
  private readonly fetchImpl: typeof fetch;
  private readonly instrumentation: DataCloudKernelProviderInstrumentation | undefined;

  constructor(options: DataCloudKernelProviderClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.projectId = options.projectId;
    this.authToken = options.authToken;
    this.timeoutMs = options.timeoutMs ?? 10_000;
    this.fetchImpl = options.fetchImpl ?? fetch;
    this.instrumentation = options.instrumentation;
  }

  async post<TBody extends object>(
    path: string,
    body: TBody,
    options: DataCloudRequestOptions = {},
  ): Promise<unknown> {
    // Preserve payload for storage - redaction happens only at logging/telemetry layer
    return this.request(path, {
      method: 'POST',
      body: JSON.stringify(body),
      ...(options.correlationId ? { correlationId: options.correlationId } : {}),
    });
  }

  async get(path: string, options: DataCloudRequestOptions = {}): Promise<unknown> {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined) {
        query.set(key, String(value));
      }
    }
    const suffix = query.size > 0 ? `?${query.toString()}` : '';
    return this.request(`${path}${suffix}`, {
      method: 'GET',
      ...(options.correlationId ? { correlationId: options.correlationId } : {}),
    });
  }

  private async request(
    path: string,
    options: { readonly method: 'GET' | 'POST'; readonly body?: string; readonly correlationId?: string },
  ): Promise<unknown> {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), this.timeoutMs);
    const startTimeMs = Date.now();

    // Extract operation name from path for instrumentation
    const operation = path.split('/').filter(Boolean).pop() ?? 'unknown';

    if (this.instrumentation) {
      this.instrumentation.recordRequestStart({
        providerId: 'data-cloud-kernel-provider',
        operation,
        method: options.method,
        path,
      });
    }

    try {
      const requestInit: RequestInit = {
        method: options.method,
        signal: controller.signal,
        headers: this.headers(options.correlationId),
        ...(options.body ? { body: options.body } : {}),
      };
      const response = await this.fetchImpl(`${this.baseUrl}${path}`, requestInit);
      const payload = await response.json().catch(() => ({}));
      const durationMs = Date.now() - startTimeMs;

      if (!response.ok) {
        const reasonCode = extractReasonCode(payload);
        const errorCorrelationId = extractCorrelationId(payload);
        const errorMessage = safeErrorMessage(payload, `Data Cloud request failed with ${response.status}`);

        if (this.instrumentation) {
          this.instrumentation.recordRequestFailure({
            providerId: 'data-cloud-kernel-provider',
            operation,
            method: options.method,
            path,
            durationMs,
            error: errorMessage,
          });
        }

        throw new Error(
          errorMessage +
            (reasonCode ? ` [reasonCode: ${reasonCode}]` : '') +
            (errorCorrelationId ? ` [correlationId: ${errorCorrelationId}]` : ''),
        );
      }

      if (this.instrumentation) {
        const reasonCode = extractReasonCode(payload);
        const completeParams: {
          providerId: string;
          operation: string;
          method: string;
          path: string;
          statusCode: number;
          durationMs: number;
          reasonCode?: string;
        } = {
          providerId: 'data-cloud-kernel-provider',
          operation,
          method: options.method,
          path,
          statusCode: response.status,
          durationMs,
        };
        if (reasonCode !== undefined) {
          completeParams.reasonCode = reasonCode;
        }
        this.instrumentation.recordRequestComplete(completeParams);
      }

      return payload;
    } catch (err: unknown) {
      const durationMs = Date.now() - startTimeMs;
      const errorMessage = err instanceof Error ? err.message : String(err);

      if (this.instrumentation && err instanceof Error && err.name !== 'AbortError') {
        this.instrumentation.recordRequestFailure({
          providerId: 'data-cloud-kernel-provider',
          operation,
          method: options.method,
          path,
          durationMs,
          error: errorMessage,
        });
      }

      throw err;
    } finally {
      clearTimeout(timeout);
    }
  }

  private headers(correlationId: string | undefined): HeadersInit {
    return {
      'content-type': 'application/json',
      'x-ghatana-tenant-id': this.tenantId,
      'x-ghatana-workspace-id': this.workspaceId,
      'x-ghatana-project-id': this.projectId,
      'x-ghatana-provider-mode': 'platform',
      ...(correlationId ? { 'x-correlation-id': correlationId } : {}),
      ...(this.authToken ? { authorization: `Bearer ${this.authToken}` } : {}),
    };
  }
}

abstract class DataCloudProviderBase {
  readonly version = '1.0.0';

  protected constructor(protected readonly client: DataCloudKernelProviderClient) {}

  protected async write(
    path: string,
    body: object,
    options: LifecycleProviderWriteOptions,
  ): Promise<LifecycleProviderResult> {
    try {
      // Validate privacy and retention metadata if present
      if (options.privacyClassification || options.retention) {
        const privacyMetadata = PrivacyMetadataSchema.safeParse({
          privacyClassification: options.privacyClassification,
          retention: options.retention,
        });
        if (!privacyMetadata.success) {
          throw new Error(`Invalid privacy or retention metadata: ${privacyMetadata.error.message}`);
        }
      }
      // Send raw body without writeOptions wrapper - gateway expects canonical envelope
      const response = await this.client.post(path, body, {
        correlationId: options.correlationId,
      });
      return parseProviderResult(response);
    } catch (error) {
      return fail(error, options.required);
    }
  }

  protected async list<T>(
    path: string,
    query: LifecycleProviderQuery,
    schema: z.ZodSchema<T>,
  ): Promise<readonly T[]> {
    const response = await this.client.get(path, {
      ...(query.correlationId ? { correlationId: query.correlationId } : {}),
      query: {
        productUnitId: query.productUnitId,
        runId: query.runId,
        correlationId: query.correlationId,
        limit: query.limit,
        cursor: query.cursor,
      },
    });
    const listResult = EventListResponseSchema.safeParse(response);
    if (!listResult.success) {
      throw new Error(`Data Cloud provider list response has invalid shape: ${listResult.error.message}`);
    }
    const items: T[] = [];
    for (const item of listResult.data.items) {
      const itemResult = schema.safeParse(item);
      if (!itemResult.success) {
        throw new Error(`Data Cloud provider list item has invalid shape: ${itemResult.error.message}`);
      }
      items.push(itemResult.data);
    }
    return items;
  }
}

export class DataCloudLifecycleEventProvider extends DataCloudProviderBase implements LifecycleEventProvider {
  readonly providerId = 'data-cloud-lifecycle-events';
  readonly capabilities = ['lifecycle-events', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async appendEvent(event: KernelLifecycleEvent, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/events', { event }, options);
  }

  async listEvents(query: LifecycleProviderQuery): Promise<readonly KernelLifecycleEvent[]> {
    return this.list('/api/v1/kernel/providers/events', query, KernelLifecycleEventSchema);
  }
}

export class DataCloudArtifactProvider extends DataCloudProviderBase implements LifecycleArtifactProvider {
  readonly providerId = 'data-cloud-artifacts';
  readonly capabilities = ['artifact-manifests', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async recordArtifactManifest(manifest: LifecycleArtifactManifestRef, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/artifacts', { manifest }, options);
  }

  async listArtifactManifests(query: LifecycleProviderQuery): Promise<readonly LifecycleArtifactManifestRef[]> {
    return this.list('/api/v1/kernel/providers/artifacts', query, ArtifactManifestRefSchema);
  }
}

export class DataCloudHealthProvider extends DataCloudProviderBase implements LifecycleHealthProvider {
  readonly providerId = 'data-cloud-health';
  readonly capabilities = ['health-snapshots', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async recordHealthSnapshot(snapshot: LifecycleHealthSnapshotRef, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/health', { snapshot }, options);
  }

  async getLatestHealthSnapshot(productUnitId: string): Promise<LifecycleHealthSnapshotRef | null> {
    const response = await this.client.get(`/api/v1/kernel/providers/health/${encodeURIComponent(productUnitId)}/latest`);
    const result = HealthSnapshotRefSchema.safeParse(response);
    return result.success ? result.data : null;
  }
}

export class DataCloudApprovalProvider extends DataCloudProviderBase implements LifecycleApprovalProvider {
  readonly providerId = 'data-cloud-approvals';
  readonly capabilities = ['approvals', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async requestLifecycleApproval(request: ApprovalRequest, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/approvals/requests', { request }, options);
  }

  async decideLifecycleApproval(decision: ApprovalDecision, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/approvals/decisions', { decision }, options);
  }
}

export class DataCloudProvenanceProvider extends DataCloudProviderBase implements LifecycleProvenanceProvider {
  readonly providerId = 'data-cloud-provenance';
  readonly capabilities = ['provenance', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async recordProvenance(record: LifecycleProvenanceRecord, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/provenance', { record }, options);
  }

  async listProvenance(query: LifecycleProviderQuery): Promise<readonly LifecycleProvenanceRecord[]> {
    return this.list('/api/v1/kernel/providers/provenance', query, ProvenanceRecordSchema);
  }
}

export class DataCloudMemoryProvider extends DataCloudProviderBase implements LifecycleMemoryProvider {
  readonly providerId = 'data-cloud-memory';
  readonly capabilities = ['memory', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async recordMemory(record: LifecycleMemoryRecord, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/memory', { record }, options);
  }

  async listMemory(query: LifecycleProviderQuery): Promise<readonly LifecycleMemoryRecord[]> {
    return this.list('/api/v1/kernel/providers/memory', query, MemoryRecordSchema);
  }
}

export class DataCloudRuntimeTruthProvider extends DataCloudProviderBase implements LifecycleRuntimeTruthProvider {
  readonly providerId = 'data-cloud-runtime-truth';
  readonly capabilities = ['runtime-truth', 'platform-mode', 'data-cloud-backed'];

  constructor(client: DataCloudKernelProviderClient) {
    super(client);
  }

  async recordRuntimeTruth(snapshot: LifecycleRuntimeTruthSnapshot, options: LifecycleProviderWriteOptions): Promise<LifecycleProviderResult> {
    return this.write('/api/v1/kernel/providers/runtime-truth', { snapshot }, options);
  }

  async getRuntimeTruth(productUnitId: string): Promise<LifecycleRuntimeTruthSnapshot | null> {
    const response = await this.client.get(`/api/v1/kernel/providers/runtime-truth/${encodeURIComponent(productUnitId)}/latest`);
    const result = RuntimeTruthSnapshotSchema.safeParse(response);
    return result.success ? result.data : null;
  }
}

export function createDataCloudKernelProviderContext(
  options: DataCloudKernelProviderClientOptions,
): KernelLifecycleProviderContext {
  const client = new DataCloudKernelProviderClient(options);
  return {
    mode: 'platform',
    events: new DataCloudLifecycleEventProvider(client),
    artifacts: new DataCloudArtifactProvider(client),
    health: new DataCloudHealthProvider(client),
    approvals: new DataCloudApprovalProvider(client),
    provenance: new DataCloudProvenanceProvider(client),
    memory: new DataCloudMemoryProvider(client),
    runtimeTruth: new DataCloudRuntimeTruthProvider(client),
  };
}

function parseProviderResult(value: unknown): LifecycleProviderResult {
  if (!isRecord(value) || typeof value.success !== 'boolean') {
    throw new Error('Data Cloud provider write response has invalid shape');
  }
  const response: DataCloudProviderResponse = {
    success: value.success,
    ...(typeof value.ref === 'string' ? { ref: value.ref } : {}),
    ...(typeof value.error === 'string' ? { error: value.error } : {}),
  };
  return {
    success: response.success,
    ...(typeof response.ref === 'string' ? { ref: response.ref } : {}),
    ...(typeof response.error === 'string' ? { error: response.error } : {}),
  };
}

function fail(error: unknown, required: boolean): LifecycleProviderResult {
  const message = error instanceof Error ? error.message : String(error);
  return {
    success: false,
    error: required ? message : `optional Data Cloud provider write skipped: ${message}`,
  };
}
