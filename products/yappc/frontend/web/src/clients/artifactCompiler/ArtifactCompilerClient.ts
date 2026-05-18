/**
 * @fileoverview Artifact Compiler API Client
 *
 * Generated-client-backed wrapper for artifact graph/import APIs, with
 * compatibility methods retained for patch bundle workflows that are not
 * yet available in the generated OpenAPI surface.
 *
 * @doc.type class
 * @doc.purpose Typed HTTP client for artifact compiler API
 * @doc.layer product
 * @doc.pattern Service
 */

import {
  ArtifactGraphService,
  OpenAPI,
  type ArtifactGraphAnalysisRequest,
  type ArtifactGraphIngestRequest,
  type ArtifactGraphMergeRequest,
  type ArtifactGraphQueryRequest,
  type ResidualAnalysisRequest,
  type SourceImportJob,
  type SourceImportRequest,
  type SourceImportResponse,
} from '@/clients/generated/api';

export interface ArtifactCompilerConfig {
  baseUrl: string;
  authToken: string;
  tenantId: string;
  timeout?: number;
  // P0: Feature flag for manual patch-bundle compatibility methods
  // These methods use manual HTTP calls instead of generated API client
  // and should only be used when the generated API doesn't yet support patch bundle workflows
  enableLegacyPatchBundleMethods?: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  data?: T;
  error?: {
    code: string;
    message: string;
  };
  timestamp: string;
}

export interface ApiError {
  code: string;
  message: string;
  details?: {
    status?: number;
    data?: unknown;
  };
}

export type { ArtifactGraphIngestRequest };

export interface ArtifactGraphIngestResponse {
  success: boolean;
  message: string;
  snapshotId?: string;
  versionId?: string;
  nodeCount?: number;
  edgeCount?: number;
}

export type GraphQueryRequest = ArtifactGraphQueryRequest;

export interface GraphQueryScopeMetadata {
  tenantId: string;
  workspaceId: string;
  projectId: string;
  queryType: string;
  pageSize: number;
  hasMore: boolean;
  snapshotId?: string;
  includeUnresolvedEdges?: boolean;
}

export interface GraphQueryResponse {
  items: Record<string, unknown>;
  nextCursor: string | null;
  totalEstimate: number;
  scope: GraphQueryScopeMetadata;
}

export type ArtifactGraphAnalyzeRequest = ArtifactGraphAnalysisRequest;

export type ArtifactGraphMergePayload = ArtifactGraphMergeRequest;

export type ResidualAnalyzeRequest = ResidualAnalysisRequest;

export interface ResidualAnalyzeResponse {
  success: boolean;
  message: string;
  analysis?: {
    totalIslands: number;
    byKind: Record<string, number>;
    reviewRequiredCount: number;
    coverageRatio: number;
  };
}

export interface PatchReviewRequest {
  projectId: string;
  tenantId?: string;
  workspaceId?: string;
  patchSetId: string;
}

export interface PatchReviewResponse {
  success: boolean;
  message: string;
  patchSet?: {
    id: string;
    changeOps: unknown[];
    patches: unknown[];
    preservedResiduals: string[];
    reviewRequiredPatches: string[];
    stats: {
      totalChangeOps: number;
      totalPatches: number;
      autoApplicable: number;
      requiresReview: number;
      preservedResiduals: number;
    };
  };
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

export interface ArtifactCompilerScopeConfig {
  workspaceId: string;
  projectId: string;
}

export class ArtifactCompilerClient {
  private readonly config: Required<ArtifactCompilerConfig>;
  private scopeConfig: ArtifactCompilerScopeConfig | null = null;

  constructor(config: ArtifactCompilerConfig) {
    this.config = {
      timeout: 10000,
      enableLegacyPatchBundleMethods: false, // P0: Default to false to encourage using generated API
      ...config,
    };
  }

  setAuthToken(token: string): void {
    this.config.authToken = token;
  }

  setTenantId(tenantId: string): void {
    this.config.tenantId = tenantId;
  }

  setScope(scope: ArtifactCompilerScopeConfig): void {
    this.scopeConfig = scope;
  }

  async ingestGraph(request: ArtifactGraphIngestRequest): Promise<ApiResponse<ArtifactGraphIngestResponse>> {
    try {
      this.configureGeneratedClient();
      const payload = await ArtifactGraphService.ingestArtifactGraph(
        this.requireTenantId(),
        this.requireWorkspaceId(),
        this.requireProjectId(),
        request,
      );
      return this.ok(payload as ArtifactGraphIngestResponse);
    } catch (error: unknown) {
      throw this.toApiError(error);
    }
  }

  async queryGraph(request: GraphQueryRequest): Promise<ApiResponse<GraphQueryResponse>> {
    try {
      this.configureGeneratedClient();
      const payload = await ArtifactGraphService.queryArtifactGraph(
        this.requireTenantId(),
        this.requireWorkspaceId(),
        this.requireProjectId(),
        request,
      );
      return this.ok(payload as GraphQueryResponse);
    } catch (error: unknown) {
      throw this.toApiError(error);
    }
  }

  async analyzeResidual(request: ResidualAnalyzeRequest): Promise<ApiResponse<ResidualAnalyzeResponse>> {
    try {
      this.configureGeneratedClient();
      const payload = await ArtifactGraphService.analyzeResidual(
        this.requireTenantId(),
        this.requireWorkspaceId(),
        this.requireProjectId(),
        request,
      );
      return this.ok(payload as ResidualAnalyzeResponse);
    } catch (error: unknown) {
      throw this.toApiError(error);
    }
  }

  async importSource(request: SourceImportRequest): Promise<SourceImportResponse> {
    try {
      this.configureGeneratedClient();
      return await ArtifactGraphService.importSource(
        this.requireTenantId(),
        this.requireWorkspaceId(),
        this.requireProjectId(),
        request,
      );
    } catch (error: unknown) {
      throw this.toApiError(error);
    }
  }

  async getSourceImportJob(jobId: string): Promise<{ job: SourceImportJob }> {
    try {
      this.configureGeneratedClient();
      return await ArtifactGraphService.getSourceImportJob(
        jobId,
        this.requireTenantId(),
        this.requireWorkspaceId(),
        this.requireProjectId(),
      );
    } catch (error: unknown) {
      throw this.toApiError(error);
    }
  }

  async approveBundle(bundleId: string, request: ApproveBundleRequest): Promise<ApiResponse<ApproveBundleResponse>> {
    // P0: Feature-gated manual patch-bundle method
    if (!this.config.enableLegacyPatchBundleMethods) {
      throw new Error(
        'Legacy patch-bundle methods are disabled. Set enableLegacyPatchBundleMethods=true in config to use them. ' +
        'Prefer using the generated API client for patch bundle operations.'
      );
    }
    console.warn(
      '[ArtifactCompilerClient] Using legacy manual HTTP method for approveBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<ApproveBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/approve`),
      request,
    );
    return this.ok(payload);
  }

  async rejectBundle(bundleId: string, request: RejectBundleRequest): Promise<ApiResponse<RejectBundleResponse>> {
    // P0: Feature-gated manual patch-bundle method
    if (!this.config.enableLegacyPatchBundleMethods) {
      throw new Error(
        'Legacy patch-bundle methods are disabled. Set enableLegacyPatchBundleMethods=true in config to use them. ' +
        'Prefer using the generated API client for patch bundle operations.'
      );
    }
    console.warn(
      '[ArtifactCompilerClient] Using legacy manual HTTP method for rejectBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<RejectBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/reject`),
      request,
    );
    return this.ok(payload);
  }

  async applyBundle(bundleId: string): Promise<ApiResponse<ApplyBundleResponse>> {
    // P0: Feature-gated manual patch-bundle method
    if (!this.config.enableLegacyPatchBundleMethods) {
      throw new Error(
        'Legacy patch-bundle methods are disabled. Set enableLegacyPatchBundleMethods=true in config to use them. ' +
        'Prefer using the generated API client for patch bundle operations.'
      );
    }
    console.warn(
      '[ArtifactCompilerClient] Using legacy manual HTTP method for applyBundle. ' +
      'This method bypasses the generated API client and should only be used when necessary.'
    );
    const payload = await this.postJson<ApplyBundleResponse>(
      this.buildEndpoint(`patch/bundles/${bundleId}/apply`),
      {},
    );
    return this.ok(payload);
  }

  private configureGeneratedClient(): void {
    OpenAPI.BASE = this.config.baseUrl;
    OpenAPI.TOKEN = this.config.authToken || undefined;
    OpenAPI.HEADERS = {
      ...(this.config.tenantId ? { 'X-Tenant-ID': this.config.tenantId } : {}),
      ...(this.scopeConfig?.workspaceId ? { 'X-Workspace-ID': this.scopeConfig.workspaceId } : {}),
      ...(this.scopeConfig?.projectId ? { 'X-Project-ID': this.scopeConfig.projectId } : {}),
    };
  }

  private requireTenantId(): string {
    if (!this.config.tenantId) {
      throw new Error('tenantId is required');
    }
    return this.config.tenantId;
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

  private fromHttpError(status: number, data: unknown): ApiError {
    if (status === 400) {
      return { code: 'BAD_REQUEST', message: 'Invalid request parameters', details: { status, data } };
    }
    if (status === 401) {
      return { code: 'UNAUTHORIZED', message: 'Authentication required', details: { status, data } };
    }
    if (status === 403) {
      return { code: 'FORBIDDEN', message: 'Access denied', details: { status, data } };
    }
    if (status === 404) {
      return { code: 'NOT_FOUND', message: 'Resource not found', details: { status, data } };
    }
    if (status >= 500) {
      return { code: 'SERVER_ERROR', message: 'Server error occurred', details: { status, data } };
    }
    return { code: 'HTTP_ERROR', message: `Request failed with status ${status}`, details: { status, data } };
  }

  private toApiError(error: unknown): ApiError {
    if (this.isApiError(error)) {
      return error;
    }

    if (error instanceof Error) {
      return {
        code: 'UNKNOWN_ERROR',
        message: error.message,
      };
    }

    return {
      code: 'UNKNOWN_ERROR',
      message: 'An unknown error occurred',
    };
  }

  private isApiError(error: unknown): error is ApiError {
    return Boolean(
      error
      && typeof error === 'object'
      && 'code' in error
      && 'message' in error,
    );
  }

  private ok<T>(data: T): ApiResponse<T> {
    return {
      success: true,
      data,
      timestamp: new Date().toISOString(),
    };
  }

  private buildEndpoint(path: string): string {
    const base = this.config.baseUrl.replace(/\/$/, '');
    const normalized = path.replace(/^\//, '');
    return `${base}/api/v1/yappc/artifact/${normalized}`;
  }
}

let defaultClient: ArtifactCompilerClient | null = null;

export function getArtifactCompilerClient(config?: ArtifactCompilerConfig): ArtifactCompilerClient {
  if (!defaultClient && config) {
    defaultClient = new ArtifactCompilerClient(config);
  }
  if (!defaultClient) {
    throw new Error('ArtifactCompilerClient not initialized. Provide config on first call.');
  }
  return defaultClient;
}

export function resetArtifactCompilerClient(): void {
  defaultClient = null;
}
