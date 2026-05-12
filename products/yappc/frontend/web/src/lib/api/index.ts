/**
 * @ghatana/yappc REST API client.
 *
 * All REST-owned YAPPC endpoints, typed against the OpenAPI specification.
 * See docs/API_SURFACE_CANONICALIZATION.md for the REST/GraphQL surface split.
 *
 * This module re-exports from split client modules:
 * - errorMapper: Error handling logic
 * - scopeHeaders: Scope and header utilities
 * - generatedClientAdapter: OpenAPI-generated client adapter
 * - legacyClientAdapter: Legacy fetch-based implementations
 * - client: Main barrel combining all endpoint groups (yappcApi)
 *
 * @example
 * ```ts
 * import { yappcApi } from '@/lib/api';
 *
 * const workspaces = await yappcApi.workspaces.list();
 * const project = await yappcApi.projects.get(projectId);
 * ```
 */

// Error handling
export type { ApiError } from './errorMapper';
export { ApiRequestError, handleError } from './errorMapper';

// Scope and header utilities
export type { ScopeLocation, ScopedRequestOptions } from './scopeHeaders';
export { addScopeToRequest } from './scopeHeaders';

// Scoped request helpers (from legacy adapter)
export { getScoped, postScoped, patchScoped, putScoped } from './legacyClientAdapter';

// Generated client adapter (delegating to OpenAPI-generated client)
export type {
  LoginRequest,
  AuthTokenResponse,
  LoginSessionResponse,
  AuthProfileUpdateRequest,
  UserProfile,
  Workspace,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  ProjectBase,
} from './generatedClientAdapter';

export {
  auth as generatedAuth,
  workspaces as generatedWorkspaces,
  projects as generatedProjects,
} from './generatedClientAdapter';

// Legacy client adapter (fetch-based implementations)
export type {
  AccountProfile,
  Project,
  ProjectListResponse,
  ProjectActivityEvent,
  ProjectActivityResponse,
  ProjectArtifactsResponse,
  ProjectSprintCurrentResponse,
  ProjectBacklogResponse,
  ProjectRunsResponse,
  ProjectIncludeRequest,
  ProjectNameSuggestionRequest,
  ProjectNameSuggestionResponse,
  ProjectDashboardActionKind,
  ProjectDashboardActionSeverity,
  ProjectDashboardAction,
  ProjectDashboardActionsResponse,
  ExecuteProjectDashboardActionRequest,
  ExecuteProjectDashboardActionResponse,
  PhaseTransitionPreviewResponse,
} from './legacyClientAdapter';

export {
  auth as legacyAuth,
  userProfile,
  workspaces as legacyWorkspaces,
  projects as legacyProjects,
} from './legacyClientAdapter';

// Main client barrel (yappcApi and all endpoint groups)
// This maintains backward compatibility while we migrate to split modules
export {
  yappcApi,
  auth,
  workspaces,
  projects,
  lifecycle,
  intent,
  shape,
  generate,
  validate,
  artifacts,
  codeAssociations,
  gates,
  telemetry,
  personas,
  phases,
  userData,
  results,
} from './client';

export type {
  ApiError as ClientApiError,
  AuthTokenResponse as ClientAuthTokenResponse,
  LoginRequest as ClientLoginRequest,
  UserProfile as ClientUserProfile,
  Workspace as ClientWorkspace,
  CreateWorkspaceRequest as ClientCreateWorkspaceRequest,
  UpdateWorkspaceRequest as ClientUpdateWorkspaceRequest,
  Project as ClientProject,
  ProjectDashboardAction as ClientProjectDashboardAction,
  ProjectDashboardActionsResponse as ClientProjectDashboardActionsResponse,
  ProjectDashboardActionKind as ClientProjectDashboardActionKind,
  ProjectDashboardActionSeverity as ClientProjectDashboardActionSeverity,
  ExecuteProjectDashboardActionRequest as ClientExecuteProjectDashboardActionRequest,
  ExecuteProjectDashboardActionResponse as ClientExecuteProjectDashboardActionResponse,
  CreateProjectRequest,
  UpdateProjectRequest,
  LifecyclePhaseInfo,
  LifecycleAdvanceRequest,
  LifecycleAdvanceResponse,
  Artifact,
  CodeAssociation,
  CreateCodeAssociationRequest,
  GateValidationRequest,
  GateValidationResponse,
  FrontendErrorReport,
  DeleteMyDataResponse,
  YappcApiClient,
} from './client';
