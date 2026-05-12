/**
 * Shared Scope Context
 * 
 * Canonical scope transport DTO for YAPPC API calls.
 * Defines the standard way to transport scope information (tenant, workspace, project)
 * across the frontend-backend boundary.
 * 
 * Scope Transport Conventions:
 * - Path params: When scope is part of the route (e.g., /api/v1/workspaces/{workspaceId})
 * - Query params: For read routes where scope is optional or for filtering
 * - Headers: Only for cross-cutting scope that applies to the entire request
 * - Body: Only for controller-level validation after authorization
 * 
 * @doc.type interface
 * @doc.purpose Shared scope context for API calls
 * @doc.layer product
 * @doc.pattern DTO
 */

/**
 * Scope location in the HTTP request
 */
export type ScopeLocation = 'path' | 'query' | 'header' | 'body';

/**
 * Canonical scope context for API requests
 * 
 * This DTO defines the standard scope fields that should be passed
 * to API endpoints. The actual transport mechanism (path/query/header/body)
 * depends on the endpoint and is handled by the scopeHeaders utility.
 */
export interface ScopeContext {
  /** Tenant identifier (multi-tenant isolation) */
  tenantId: string;
  
  /** Workspace identifier (workspace-level isolation) */
  workspaceId?: string;
  
  /** Project identifier (project-level isolation) */
  projectId?: string;
  
  /** Artifact identifier (for artifact-specific operations) */
  artifactId?: string;
  
  /** Actor identifier (who is performing the operation) */
  actorId?: string;
  
  /** Generation run identifier (for generation-specific operations) */
  generationRunId?: string;
  
  /** Canvas node identifier (for canvas-specific operations) */
  canvasNodeId?: string;
}

/**
 * Request scope options for scoped API calls
 */
export interface ScopedRequestOptions {
  /** Scope context with tenant, workspace, project IDs */
  scope: ScopeContext;
  
  /** Additional headers to include in the request */
  headers?: Record<string, string>;
  
  /** Override the default scope location (defaults to path param for workspace/project) */
  scopeLocation?: ScopeLocation;
}

/**
 * Creates a scope context from individual parameters
 */
export function createScopeContext(params: {
  tenantId: string;
  workspaceId?: string;
  projectId?: string;
  artifactId?: string;
  actorId?: string;
  generationRunId?: string;
  canvasNodeId?: string;
}): ScopeContext {
  return {
    tenantId: params.tenantId,
    workspaceId: params.workspaceId,
    projectId: params.projectId,
    artifactId: params.artifactId,
    actorId: params.actorId,
    generationRunId: params.generationRunId,
    canvasNodeId: params.canvasNodeId,
  };
}

/**
 * Validates that required scope fields are present
 */
export function validateScopeContext(
  scope: ScopeContext,
  requiredFields: (keyof ScopeContext)[]
): void {
  const missing = requiredFields.filter((field) => !scope[field]);
  if (missing.length > 0) {
    throw new Error(`Missing required scope fields: ${missing.join(', ')}`);
  }
}

/**
 * Extracts scope from URL path parameters
 */
export function extractScopeFromPath(path: string, pattern: string): Partial<ScopeContext> {
  // Simple pattern matching to extract IDs from path
  // Example: /api/v1/workspaces/{workspaceId}/projects/{projectId}
  const pathParts = path.split('/').filter(Boolean);
  const patternParts = pattern.split('/').filter(Boolean);
  
  const scope: Partial<ScopeContext> = {};
  
  patternParts.forEach((part, index) => {
    if (pathParts[index] && part.startsWith('{') && part.endsWith('}')) {
      const fieldName = part.slice(1, -1);
      const value = pathParts[index];
      
      switch (fieldName) {
        case 'tenantId':
          scope.tenantId = value;
          break;
        case 'workspaceId':
          scope.workspaceId = value;
          break;
        case 'projectId':
          scope.projectId = value;
          break;
        case 'artifactId':
          scope.artifactId = value;
          break;
        case 'generationRunId':
          scope.generationRunId = value;
          break;
        case 'canvasNodeId':
          scope.canvasNodeId = value;
          break;
      }
    }
  });
  
  return scope;
}
