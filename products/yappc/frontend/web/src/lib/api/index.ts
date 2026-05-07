/**
 * @ghatana/yappc REST API client.
 *
 * All REST-owned YAPPC endpoints, typed against the OpenAPI specification.
 * See docs/API_SURFACE_CANONICALIZATION.md for the REST/GraphQL surface split.
 *
 * @example
 * ```ts
 * import { yappcApi } from '@/lib/api';
 *
 * const workspaces = await yappcApi.workspaces.list();
 * const project = await yappcApi.projects.get(projectId);
 * ```
 */

export {
  yappcApi,
  ApiRequestError,
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
  ApiError,
  AuthTokenResponse,
  LoginRequest,
  UserProfile,
  Workspace,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  Project,
  ProjectDashboardAction,
  ProjectDashboardActionsResponse,
  ProjectDashboardActionKind,
  ProjectDashboardActionSeverity,
  ExecuteProjectDashboardActionRequest,
  ExecuteProjectDashboardActionResponse,
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
