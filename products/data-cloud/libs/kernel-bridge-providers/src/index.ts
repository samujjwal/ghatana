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

export interface DataCloudKernelProviderClientOptions {
  readonly baseUrl: string;
  readonly tenantId: string;
  readonly workspaceId: string;
  readonly projectId: string;
  readonly authToken?: string;
  readonly timeoutMs?: number;
  readonly fetchImpl?: typeof fetch;
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

  constructor(options: DataCloudKernelProviderClientOptions) {
    this.baseUrl = options.baseUrl.replace(/\/$/, '');
    this.tenantId = options.tenantId;
    this.workspaceId = options.workspaceId;
    this.projectId = options.projectId;
    this.authToken = options.authToken;
    this.timeoutMs = options.timeoutMs ?? 10_000;
    this.fetchImpl = options.fetchImpl ?? fetch;
  }

  async post<TBody extends object>(
    path: string,
    body: TBody,
    options: DataCloudRequestOptions = {},
  ): Promise<unknown> {
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
    try {
      const requestInit: RequestInit = {
        method: options.method,
        signal: controller.signal,
        headers: this.headers(options.correlationId),
        ...(options.body ? { body: options.body } : {}),
      };
      const response = await this.fetchImpl(`${this.baseUrl}${path}`, requestInit);
      const payload = await response.json().catch(() => ({}));
      if (!response.ok) {
        throw new Error(safeErrorMessage(payload, `Data Cloud request failed with ${response.status}`));
      }
      return payload;
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
      const response = await this.client.post(path, { ...body, writeOptions: options }, {
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
    predicate: (value: unknown) => value is T,
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
    if (!isRecord(response) || !Array.isArray(response.items)) {
      throw new Error('Data Cloud provider list response has invalid shape');
    }
    return response.items.filter(predicate);
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
    return this.list('/api/v1/kernel/providers/events', query, isKernelLifecycleEvent);
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
    return this.list('/api/v1/kernel/providers/artifacts', query, isArtifactManifestRef);
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
    return isHealthSnapshotRef(response) ? response : null;
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
    return this.list('/api/v1/kernel/providers/provenance', query, isProvenanceRecord);
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
    return this.list('/api/v1/kernel/providers/memory', query, isMemoryRecord);
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
    return isRuntimeTruthSnapshot(response) ? response : null;
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

function safeErrorMessage(value: unknown, fallback: string): string {
  if (isRecord(value) && typeof value.error === 'string') {
    return value.error;
  }
  if (isRecord(value) && typeof value.message === 'string') {
    return value.message;
  }
  return fallback;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isKernelLifecycleEvent(value: unknown): value is KernelLifecycleEvent {
  return isRecord(value) && isRecord(value.metadata) && typeof value.metadata.eventId === 'string';
}

function isArtifactManifestRef(value: unknown): value is LifecycleArtifactManifestRef {
  return isRecord(value) && typeof value.productUnitId === 'string' && typeof value.manifestPath === 'string';
}

function isHealthSnapshotRef(value: unknown): value is LifecycleHealthSnapshotRef {
  return isRecord(value) && typeof value.productUnitId === 'string' && typeof value.snapshotPath === 'string';
}

function isProvenanceRecord(value: unknown): value is LifecycleProvenanceRecord {
  return isRecord(value) && typeof value.provenanceId === 'string' && Array.isArray(value.evidenceRefs);
}

function isMemoryRecord(value: unknown): value is LifecycleMemoryRecord {
  return isRecord(value) && typeof value.memoryId === 'string' && typeof value.contentRef === 'string';
}

function isRuntimeTruthSnapshot(value: unknown): value is LifecycleRuntimeTruthSnapshot {
  return isRecord(value) && typeof value.productUnitId === 'string' && typeof value.observedAt === 'string';
}
