/**
 * @fileoverview Artifact Compiler API Client
 *
 * P2-1: Typed client for artifact compiler operations:
 * - Import job (ingest)
 * - Graph summary (query/stats)
 * - Residual review (analyzeResidual)
 * - Patch review (placeholder for future implementation)
 *
 * @doc.type class
 * @doc.purpose Typed HTTP client for artifact compiler API
 * @doc.layer product
 * @doc.pattern Service
 */

import axios, { AxiosInstance } from 'axios';

// ============================================================================
// Types
// ============================================================================

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

// ============================================================================
// Request/Response Types
// ============================================================================

export interface ArtifactNodeDto {
  id: string;
  type: string;
  name: string;
  filePath?: string;
  content?: string;
  properties?: Record<string, unknown>;
  tags?: string[];
  tenantId?: string;
  projectId?: string;
  sourceLocation?: {
    filePath: string;
    startLine: number;
    startColumn: number;
    endLine: number;
    endColumn: number;
  };
  extractorId?: string;
  extractorVersion?: string;
  confidence?: number;
  provenance?: string;
  privacySecurityFlags?: string[];
  residualFragmentIds?: string[];
  sourceRef?: string;
  symbolRef?: string;
}

export interface ArtifactEdgeDto {
  edgeId?: string;
  sourceNodeId: string;
  targetNodeId: string;
  relationshipType: string;
  confidence?: number;
  bidirectional?: boolean;
  metadata?: Record<string, unknown>;
  properties?: Record<string, unknown>;
  snapshotId?: string;
  versionId?: string;
}

export interface ResidualIslandPayload {
  id: string;
  islandType: string;
  summary: string;
  originalSource?: string; // P0: Added for round-trip fidelity
  sourceLocation?: {
    filePath: string;
    startLine: number;
    startColumn: number;
    endLine: number;
    endColumn: number;
  };
  sourceSpan?: string;
  checksum?: string;
  rawFragmentRef?: string;
  reason?: string;
  confidence: number;
  reviewRequired: boolean;
  riskScore?: number;
  fileCount?: number;
  metadata?: Record<string, string>;
  tenantId?: string;
  projectId?: string;
  workspaceId?: string;
  snapshotId?: string;
}

export interface ArtifactGraphIngestRequest {
  projectId: string;
  tenantId?: string;
  workspaceId?: string;
  nodes: ArtifactNodeDto[];
  edges: ArtifactEdgeDto[];
  snapshotRef?: string;
  snapshotId?: string;
  versionId?: string;
  contentChecksum?: string;
  unresolvedEdges?: Array<{
    id?: string;
    sourceNodeId: string;
    targetRef: string;
    relationshipType: string; // P0: Normalized from relationship to match proto
    targetKindHint?: string;
    confidence?: number;
    metadata?: Record<string, unknown>;
  }>;
  edgeResolutionRecords?: Array<{
    id?: string;
    unresolvedEdgeId: string;
    status: string;
    resolvedTargetId?: string;
    candidateIds?: string[];
    reviewRequired?: boolean;
  }>;
  residualIslands?: ResidualIslandPayload[];
}

export interface ArtifactGraphIngestResponse {
  success: boolean;
  message: string;
  snapshotId?: string;
  versionId?: string;
  nodeCount?: number;
  edgeCount?: number;
}

export interface GraphQueryRequest {
  projectId: string;
  tenantId?: string;
  workspaceId?: string;
  queryType: 'orphaned' | 'dependencies' | 'dependents' | 'stats';
  seedNodeIds?: string[];
  cursor?: string;
  limit?: number;
  snapshotId?: string;
  includeUnresolvedEdges?: boolean;
}

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

export interface ResidualIsland {
  id: string;
  islandType: string;
  summary: string;
  sourceSpan?: string;
  checksum?: string;
  rawFragmentRef?: string;
  reason?: string;
  confidence: number;
  reviewRequired: boolean;
  riskScore?: number;
  fileCount?: number;
  metadata?: Record<string, string>;
}

export interface ResidualAnalyzeRequest {
  projectId: string;
  tenantId?: string;
  workspaceId?: string;
  residualIslands: ResidualIsland[];
}

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

// ============================================================================
// Artifact Compiler Client
// ============================================================================

export interface ArtifactCompilerScopeConfig {
  workspaceId: string;
  projectId: string;
}

export class ArtifactCompilerClient {
  private httpClient: AxiosInstance;
  private config: Required<ArtifactCompilerConfig>;
  private scopeConfig: ArtifactCompilerScopeConfig | null = null;

  constructor(config: ArtifactCompilerConfig) {
    this.config = {
      timeout: 10000,
      ...config,
    };

    this.httpClient = axios.create({
      baseURL: this.config.baseUrl,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
        'User-Agent': 'YAPPC-ArtifactCompiler/1.0',
      },
    });

    // Add request interceptor for auth, tenant and workspace/project scope
    this.httpClient.interceptors.request.use(
      (config) => {
        if (this.config.authToken) {
          config.headers.Authorization = `Bearer ${this.config.authToken}`;
        }
        if (this.config.tenantId) {
          config.headers['X-Tenant-Id'] = this.config.tenantId;
        }
        if (this.scopeConfig?.workspaceId) {
          config.headers['X-Workspace-ID'] = this.scopeConfig.workspaceId;
        }
        if (this.scopeConfig?.projectId) {
          config.headers['X-Project-ID'] = this.scopeConfig.projectId;
        }
        return config;
      },
      (error) => Promise.reject(error)
    );

    // Add response interceptor for error handling
    this.httpClient.interceptors.response.use(
      (response) => response,
      (error) => this.handleError(error)
    );
  }

  /**
   * P2-1: Ingest artifact nodes and edges extracted by the TypeScript scanner.
   * POST /api/v1/yappc/artifact/graph/ingest
   */
  async ingestGraph(request: ArtifactGraphIngestRequest): Promise<ApiResponse<ArtifactGraphIngestResponse>> {
    try {
      const response = await this.httpClient.post<ApiResponse<ArtifactGraphIngestResponse>>(
        '/api/v1/yappc/artifact/graph/ingest',
        request
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * P2-1: Query the artifact graph for summary information.
   * POST /api/v1/yappc/artifact/graph/query
   */
  async queryGraph(request: GraphQueryRequest): Promise<ApiResponse<GraphQueryResponse>> {
    try {
      const response = await this.httpClient.post<ApiResponse<GraphQueryResponse>>(
        '/api/v1/yappc/artifact/graph/query',
        request
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * P2-1: Analyze residual islands flagged by the TypeScript scanner.
   * POST /api/v1/yappc/artifact/residual/analyze
   */
  async analyzeResidual(request: ResidualAnalyzeRequest): Promise<ApiResponse<ResidualAnalyzeResponse>> {
    try {
      const response = await this.httpClient.post<ApiResponse<ResidualAnalyzeResponse>>(
        '/api/v1/yappc/artifact/residual/analyze',
        request
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * P0: Review a patch set for approval or rejection.
   * POST /api/v1/yappc/artifact/patch/review
   * NOTE: This endpoint is not yet implemented in the backend.
   * Will throw 404 until ArtifactPatchController is added (P1-8).
   */
  async reviewPatch(request: PatchReviewRequest): Promise<ApiResponse<PatchReviewResponse>> {
    try {
      const response = await this.httpClient.post<ApiResponse<PatchReviewResponse>>(
        '/api/v1/yappc/artifact/patch/review',
        request
      );
      return response.data;
    } catch (error) {
      throw this.handleError(error);
    }
  }

  /**
   * Update auth token
   */
  setAuthToken(token: string): void {
    this.config.authToken = token;
  }

  /**
   * Update tenant ID
   */
  setTenantId(tenantId: string): void {
    this.config.tenantId = tenantId;
  }

  /**
   * Set workspace and project scope headers for all subsequent requests.
   * Required for ingest, query, analyze, and patch operations.
   */
  setScope(scope: ArtifactCompilerScopeConfig): void {
    this.scopeConfig = scope;
  }

  /**
   * Handle axios errors
   */
  private handleError(error: unknown): ApiError {
    const apiError: ApiError = {
      code: 'UNKNOWN_ERROR',
      message: 'An unknown error occurred',
    };

    if (axios.isAxiosError(error)) {
      apiError.code = error.code || 'AXIOS_ERROR';
      apiError.message = error.message;
      apiError.details = {
        status: error.response?.status,
        data: error.response?.data,
      };

      if (error.response) {
        switch (error.response.status) {
          case 400:
            apiError.code = 'BAD_REQUEST';
            apiError.message = 'Invalid request parameters';
            break;
          case 401:
            apiError.code = 'UNAUTHORIZED';
            apiError.message = 'Authentication required';
            break;
          case 403:
            apiError.code = 'FORBIDDEN';
            apiError.message = 'Access denied';
            break;
          case 404:
            apiError.code = 'NOT_FOUND';
            apiError.message = 'Resource not found';
            break;
          case 429:
            apiError.code = 'RATE_LIMITED';
            apiError.message = 'Too many requests';
            break;
          default:
            if (error.response.status >= 500) {
              apiError.code = 'SERVER_ERROR';
              apiError.message = 'Server error occurred';
            }
        }
      } else if (error.request) {
        apiError.code = 'NETWORK_ERROR';
        apiError.message = 'Network error - please check your connection';
      }
    }

    console.error('[Artifact Compiler Client] Error:', apiError);
    return apiError;
  }
}

// ============================================================================
// Default client instance
// ============================================================================

let defaultClient: ArtifactCompilerClient | null = null;

/**
 * Get or create the default artifact compiler client instance.
 */
export function getArtifactCompilerClient(config?: ArtifactCompilerConfig): ArtifactCompilerClient {
  if (!defaultClient && config) {
    defaultClient = new ArtifactCompilerClient(config);
  }
  if (!defaultClient) {
    throw new Error('ArtifactCompilerClient not initialized. Provide config on first call.');
  }
  return defaultClient;
}

/**
 * Reset the default client instance (useful for testing).
 */
export function resetArtifactCompilerClient(): void {
  defaultClient = null;
}
