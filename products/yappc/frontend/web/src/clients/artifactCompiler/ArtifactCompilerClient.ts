/**
 * @fileoverview Artifact Compiler API Client
 *
 * Generated-client-backed wrapper for artifact graph/import APIs (Group 1-3).
 * This client uses only the generated OpenAPI client for all operations.
 *
 * Legacy patch-bundle methods have been moved to LegacyArtifactPatchBundleClient.ts
 * and should be imported explicitly when needed.
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

  async analyzeGraph(request: ArtifactGraphAnalyzeRequest): Promise<ApiResponse<ArtifactGraphIngestResponse>> {
    try {
      this.configureGeneratedClient();
      const payload = await ArtifactGraphService.analyzeArtifactGraph(
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

  async mergeGraph(request: ArtifactGraphMergePayload): Promise<ApiResponse<ArtifactGraphIngestResponse>> {
    try {
      this.configureGeneratedClient();
      const payload = await ArtifactGraphService.mergeArtifactGraph(
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
