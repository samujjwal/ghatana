/**
 * @fileoverview Legacy Artifact Patch Bundle Client
 *
 * DEPRECATED: This client contains legacy manual HTTP methods for patch bundle workflows
 * that are not yet available in the generated OpenAPI surface.
 *
 * These methods bypass the generated API client and use manual HTTP calls.
 * This file should only be used when the generated API doesn't yet support patch bundle operations.
 *
 * Prefer using the generated API client (ArtifactCompilerClient) for all Group 1-3 operations.
 *
 * @doc.type class
 * @doc.purpose Legacy HTTP client for artifact patch bundle operations
 * @doc.layer product
 * @doc.pattern Service
 * @doc.deprecated Use generated API client for patch bundle operations when available
 */

export interface LegacyPatchBundleConfig {
  baseUrl: string;
  authToken: string;
  tenantId: string;
  timeout?: number;
}

export interface ApproveBundleRequest {
  reviewer: string;
}

export interface RejectBundleRequest {
  reviewer: string;
  reason: string;
}

export interface ApproveBundleResponse {
  success: boolean;
  bundleId: string;
  status: string;
  reviewedBy: string;
}

export interface RejectBundleResponse {
  success: boolean;
  bundleId: string;
  status: string;
  reviewedBy: string;
}

export interface ApplyBundleResponse {
  success: boolean;
  bundleId: string;
  status: string;
}

export interface LegacyPatchBundleScopeConfig {
  workspaceId: string;
  projectId: string;
}

export class LegacyArtifactPatchBundleClient {
  private readonly config: Required<LegacyPatchBundleConfig>;
  private scopeConfig: LegacyPatchBundleScopeConfig | null = null;

  constructor(config: LegacyPatchBundleConfig) {
    this.config = {
      timeout: 10000,
      ...config,
    };
    console.warn(
      '[LegacyArtifactPatchBundleClient] Using legacy manual HTTP client. ' +
      'This client bypasses the generated API client and should only be used when necessary.'
    );
  }

  setAuthToken(token: string): void {
    this.config.authToken = token;
  }

  setTenantId(tenantId: string): void {
    this.config.tenantId = tenantId;
  }

  setScope(scope: LegacyPatchBundleScopeConfig): void {
    this.scopeConfig = scope;
  }

  async approveBundle(bundleId: string, request: ApproveBundleRequest): Promise<ApproveBundleResponse> {
    console.warn(
      '[LegacyArtifactPatchBundleClient] Using legacy manual HTTP method for approveBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<ApproveBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/approve`),
      request,
    );
    return payload;
  }

  async rejectBundle(bundleId: string, request: RejectBundleRequest): Promise<RejectBundleResponse> {
    console.warn(
      '[LegacyArtifactPatchBundleClient] Using legacy manual HTTP method for rejectBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<RejectBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/reject`),
      request,
    );
    return payload;
  }

  async applyBundle(bundleId: string): Promise<ApplyBundleResponse> {
    console.warn(
      '[LegacyArtifactPatchBundleClient] Using legacy manual HTTP method for applyBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<ApplyBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/apply`),
      {},
    );
    return payload;
  }

  private requireWorkspaceId(): string {
    if (!this.scopeConfig?.workspaceId) {
      throw new Error('workspaceId is required. Call setScope first.');
    }
    return this.scopeConfig.workspaceId;
  }

  private requireProjectId(): string {
    if (!this.scopeConfig?.projectId) {
      throw new Error('projectId is required. Call setScope first.');
    }
    return this.scopeConfig.projectId;
  }

  private async postJson<T>(url: string, body: unknown): Promise<T> {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(this.config.authToken ? { Authorization: `Bearer ${this.config.authToken}` } : {}),
        ...(this.config.tenantId ? { 'X-Tenant-ID': this.config.tenantId } : {}),
        ...(this.scopeConfig?.workspaceId ? { 'X-Workspace-ID': this.scopeConfig.workspaceId } : {}),
        ...(this.scopeConfig?.projectId ? { 'X-Project-ID': this.scopeConfig.projectId } : {}),
      },
      body: JSON.stringify(body),
    });

    if (!response.ok) {
      const errorBody = await this.safeJson(response);
      throw this.fromHttpError(response.status, errorBody);
    }

    return (await this.safeJson(response)) as T;
  }

  private async safeJson(response: Response): Promise<unknown> {
    try {
      return await response.json();
    } catch {
      return undefined;
    }
  }

  private fromHttpError(status: number, data: unknown): Error {
    const message = data && typeof data === 'object' && 'message' in data
      ? String(data.message)
      : `Request failed with status ${status}`;
    return new Error(message);
  }

  private buildEndpoint(path: string): string {
    const base = this.config.baseUrl.replace(/\/$/, '');
    const normalized = path.replace(/^\//, '');
    return `${base}/api/v1/yappc/artifact/${normalized}`;
  }
}

let defaultLegacyClient: LegacyArtifactPatchBundleClient | null = null;

export function getLegacyArtifactPatchBundleClient(config?: LegacyPatchBundleConfig): LegacyArtifactPatchBundleClient {
  if (!defaultLegacyClient && config) {
    defaultLegacyClient = new LegacyArtifactPatchBundleClient(config);
  }
  if (!defaultLegacyClient) {
    throw new Error('LegacyArtifactPatchBundleClient not initialized. Provide config on first call.');
  }
  return defaultLegacyClient;
}

export function resetLegacyArtifactPatchBundleClient(): void {
  defaultLegacyClient = null;
}
