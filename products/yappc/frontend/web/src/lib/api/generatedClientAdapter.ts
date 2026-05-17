/**
 * Generated Client Adapter Module
 *
 * Adapter layer delegating to OpenAPI-generated client for type safety and contract guarantees.
 * Maintains backward compatibility while delegating to generated services.
 *
 * @doc.type module
 * @doc.purpose OpenAPI-generated client adapter
 * @doc.layer product
 * @doc.pattern Adapter
 */

import { OpenAPI, AuthService as GeneratedAuthService, WorkspacesService, ProjectsService, LifecycleService, IntentService, ShapeService, GenerateService, ValidateService, PreviewService, AuditService, TelemetryService, UserDataService, CodeAssociationsService, ArtifactGraphService } from '../../clients/generated/api';
import type {
  LoginRequest as GeneratedLoginRequestType,
  LoginResponse as GeneratedLoginResponseType,
  Workspace as GeneratedWorkspace,
  Project as GeneratedProject,
  CreateWorkspaceRequest as GeneratedCreateWorkspaceRequest,
  UpdateWorkspaceRequest as GeneratedUpdateWorkspaceRequest,
  PhaseCockpitPacket,
  ProjectDashboardActions,
  PhaseList,
  Phase,
  AdvancePhaseRequest as GeneratedAdvancePhaseRequest,
  PhaseTransitionResult,
  GateValidationResult,
  GenerateArtifactsRequest as GeneratedGenerateArtifactsRequest,
  GenerateArtifactsResponse as GeneratedGenerateArtifactsResponse,
  RegenerateDiffRequest as GeneratedRegenerateDiffRequest,
  RegenerateDiffResponse as GeneratedRegenerateDiffResponse,
  GenerateReviewDecisionRequest as GeneratedGenerateReviewDecisionRequest,
  GenerateReviewDecisionResponse as GeneratedGenerateReviewDecisionResponse,
  CaptureIntentRequest as GeneratedCaptureIntentRequest,
  IntentResponse as GeneratedIntentResponse,
  DeriveShapeRequest as GeneratedDeriveShapeRequest,
  GenerateModelRequest as GeneratedGenerateModelRequest,
  CreatePreviewSessionRequest,
  CreatePreviewSessionResponse,
  ValidatePreviewSessionRequest,
  ValidatePreviewSessionResponse,
  AuditEventRequest as GeneratedAuditEventRequest,
  AuditEventResponse as GeneratedAuditEventResponse,
  FrontendErrorReport as GeneratedFrontendErrorReport,
  DeleteMyDataResponse as GeneratedDeleteMyDataResponse,
  Artifact,
  CreateArtifactRequest as GeneratedCreateArtifactRequest,
  UpdateArtifactRequest as GeneratedUpdateArtifactRequest,
  CodeAssociation as GeneratedCodeAssociation,
  CreateCodeAssociationRequest as GeneratedCreateCodeAssociationRequest,
} from '../../clients/generated/api';

// Configure OpenAPI client for cookie-session mode
OpenAPI.BASE = '/api';
OpenAPI.WITH_CREDENTIALS = true;
OpenAPI.CREDENTIALS = 'same-origin';

// ============================================================================
// Auth Types (backward compatibility)
// ============================================================================

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthTokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface LoginSessionResponse {
  user: {
    id: string;
    email: string;
    name: string;
    firstName?: string;
    lastName?: string;
    avatar?: string;
    avatarUrl?: string;
    tenantId?: string;
    workspaceIds?: string[];
    roles?: string[];
    role: 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER';
  };
  tokens: AuthTokenResponse;
}

export interface AuthProfileUpdateRequest {
  firstName?: string;
  lastName?: string;
  username?: string;
  email?: string;
  avatar?: string;
}

export interface UserProfile {
  id: string;
  email: string;
  name: string;
  role: string;
  tenantId: string;
}

// ============================================================================
// Workspace Types (backward compatibility)
// ============================================================================

export type Workspace = GeneratedWorkspace;
export type CreateWorkspaceRequest = GeneratedCreateWorkspaceRequest;
export type UpdateWorkspaceRequest = GeneratedUpdateWorkspaceRequest;

// ============================================================================
// Project Types (backward compatibility)
// ============================================================================

export type ProjectBase = GeneratedProject;

// ============================================================================
// Lifecycle Types (backward compatibility)
// ============================================================================

export type LifecyclePhaseInfo = Phase;
export interface LifecycleAdvanceRequest extends GeneratedAdvancePhaseRequest {}
export interface LifecycleAdvanceResponse extends PhaseTransitionResult {}
export interface GateValidationRequest {
  projectId: string;
  fromPhase: string;
  toPhase: string;
}
export interface GateValidationResponse extends GateValidationResult {}

// ============================================================================
// Generate Types (backward compatibility)
// ============================================================================

export interface GenerateArtifactsRequest extends GeneratedGenerateArtifactsRequest {}
export interface GenerateArtifactProvenance {
  readonly requirementId: string;
  readonly phase: string;
  readonly canvasNodeId: string;
  readonly sourceArtifactId: string;
  readonly confidence: number;
  readonly approvingActorId: string;
  readonly approvedAt?: string;
}
export interface GenerateDiffRegion {
  readonly id: string;
  readonly type: 'addition' | 'deletion' | 'modification';
  readonly startLine: number;
  readonly endLine: number;
  readonly originalContent: string;
  readonly newContent: string;
  readonly owner: 'system' | 'user';
  readonly provenance: GenerateArtifactProvenance;
}
export interface GeneratedFileDiff {
  readonly id: string;
  readonly path: string;
  readonly language: string;
  readonly artifactType: 'source' | 'test' | 'config' | 'documentation' | 'schema' | 'api' | 'infrastructure';
  readonly provenance: GenerateArtifactProvenance;
  readonly diffRegions: readonly GenerateDiffRegion[];
}
export interface GenerateDiffReview {
  readonly files: readonly GeneratedFileDiff[];
}
export interface GenerateArtifactsResponse {
  readonly runId: string;
  readonly executionId?: string;
  readonly status: 'pending' | 'running' | 'completed' | 'failed' | 'degraded';
  readonly reviewRequired: boolean;
  readonly diff?: GenerateDiffReview;
  readonly traceId?: string;
  readonly evidenceIds?: readonly string[];
  readonly policyDecisionId?: string;
  readonly degraded?: boolean;
  readonly degradedReason?: string;
  readonly generatedAt: string;
  readonly model?: string;
  readonly confidence?: number;
  readonly confidenceReason?: string;
}
export interface RegenerateDiffRequest extends GeneratedRegenerateDiffRequest {}
export interface RegenerateDiffResponse {
  readonly runId: string;
  readonly status: 'pending' | 'running' | 'completed' | 'failed' | 'degraded';
  readonly diff?: GenerateDiffReview;
  readonly reviewRequired: boolean;
  readonly traceId?: string;
  readonly evidenceIds?: readonly string[];
  readonly policyDecisionId?: string;
  readonly degraded?: boolean;
  readonly degradedReason?: string;
  readonly generatedAt: string;
}
export type GenerateReviewDecision = 'apply' | 'reject' | 'rollback';
export interface GenerateReviewDecisionRequest extends GeneratedGenerateReviewDecisionRequest {}
export interface GenerateReviewDecisionResponse extends GeneratedGenerateReviewDecisionResponse {}

// ============================================================================
// Preview Types (backward compatibility)
// ============================================================================

export interface PreviewSessionContext extends CreatePreviewSessionRequest {}
export interface PreviewSessionIssueResponse extends CreatePreviewSessionResponse {}
export interface PreviewSessionValidateResponse extends ValidatePreviewSessionResponse {}

export interface CaptureIntentRequest extends GeneratedCaptureIntentRequest {}
export interface IntentResponse extends GeneratedIntentResponse {}
export interface DeriveShapeRequest extends GeneratedDeriveShapeRequest {}
export interface GenerateModelRequest extends GeneratedGenerateModelRequest {}

// ============================================================================
// Audit/Telemetry Types (backward compatibility)
// ============================================================================

export interface AuditEventRequest extends GeneratedAuditEventRequest {}
export interface AuditEventResponse extends GeneratedAuditEventResponse {}
export interface FrontendErrorReport extends GeneratedFrontendErrorReport {}
export interface DeleteMyDataResponse extends GeneratedDeleteMyDataResponse {}

// ============================================================================
// Artifact Types (backward compatibility)
// ============================================================================

export interface ArtifactBase extends Artifact {}
export interface CreateArtifactRequest extends GeneratedCreateArtifactRequest {}
export interface UpdateArtifactRequest extends GeneratedUpdateArtifactRequest {}
export interface CodeAssociationBase extends GeneratedCodeAssociation {}
export interface CreateCodeAssociationRequest extends GeneratedCreateCodeAssociationRequest {}

// ============================================================================
// Adapter Functions
// ============================================================================

function adaptLoginRequest(request: LoginRequest): GeneratedLoginRequestType {
  return { email: request.email, password: request.password };
}

function adaptLoginResponse(response: GeneratedLoginResponseType): LoginSessionResponse {
  // Generated client returns cookie-session response with user and session info
  // For backward compatibility, construct a token response from session info
  const expiresIn = response.session?.expiresAt 
    ? Math.floor((new Date(response.session.expiresAt).getTime() - Date.now()) / 1000)
    : 3600;
  
  return {
    user: {
      id: response.user?.id || '',
      email: response.user?.email || '',
      name: response.user?.name || '',
      firstName: response.user?.firstName,
      lastName: response.user?.lastName,
      avatar: response.user?.avatar,
      avatarUrl: response.user?.avatarUrl,
      tenantId: response.user?.tenantId,
      workspaceIds: response.user?.workspaceIds,
      roles: response.user?.roles,
      role: (response.user?.role || 'VIEWER') as 'VIEWER' | 'EDITOR' | 'ADMIN' | 'OWNER',
    },
    tokens: {
      accessToken: '', // Cookie-session mode: no access token in response
      refreshToken: '', // Cookie-session mode: no refresh token in response
      expiresIn,
    },
  };
}

// ============================================================================
// Auth Client (delegating to generated client)
// ============================================================================

export const auth = {
  // Delegate to generated client
  login: (body: LoginRequest): Promise<LoginSessionResponse> => {
    const generatedRequest = adaptLoginRequest(body);
    return GeneratedAuthService.login(generatedRequest).then(adaptLoginResponse);
  },
  // Alias for login (backward compatibility)
  loginSession: (body: LoginRequest): Promise<LoginSessionResponse> => {
    return auth.login(body);
  },
  // For cookie-session mode, validate is handled via generated client
  validate: (): Promise<{ valid: boolean }> => {
    return GeneratedAuthService.validateSession().then((response) => ({
      valid: Boolean(response.user?.id),
    }));
  },
  // For cookie-session mode, me is handled via generated client
  me: (): Promise<UserProfile> => {
    return GeneratedAuthService.currentUser().then(response => ({
      id: response.id || '',
      email: response.email || '',
      name: response.name || '',
      role: response.role || '',
      tenantId: response.tenantId || '',
    }));
  },
} as const;

// ============================================================================
// Workspaces Client (delegating to generated client)
// ============================================================================

export const workspaces = {
  // Delegate to generated client
  list: () => WorkspacesService.listWorkspaces(),
  get: (workspaceId: string) => WorkspacesService.getWorkspace(workspaceId),
  create: (body: CreateWorkspaceRequest) => WorkspacesService.createWorkspace(body),
  update: (workspaceId: string, body: UpdateWorkspaceRequest) =>
    WorkspacesService.updateWorkspace(workspaceId, body),
  delete: (workspaceId: string) => WorkspacesService.deleteWorkspace(workspaceId),
} as const;

// ============================================================================
// Projects Client (delegating to generated client for basic CRUD)
// ============================================================================

export const projects = {
  // Delegate to generated client for basic read operations
  list: (workspaceId?: string) => ProjectsService.listProjects(workspaceId || ''),
  get: (projectId: string, workspaceId?: string) => ProjectsService.getProject(projectId, workspaceId),
  delete: (projectId: string, workspaceId: string) => ProjectsService.deleteProject(projectId, workspaceId),
} as const;

// ============================================================================
// Lifecycle Client (delegating to generated client)
// ============================================================================

export const lifecycle = {
  phases: () => LifecycleService.listPhases(),
  advance: (body: LifecycleAdvanceRequest) => LifecycleService.advancePhase(body),
  getPhasePacket: (phase: 'intent' | 'shape' | 'validate' | 'generate' | 'run' | 'observe' | 'learn' | 'evolve', projectId: string, workspaceId?: string, correlationId?: string) => 
    LifecycleService.getPhasePacket(phase, projectId, workspaceId, correlationId),
  getDashboardActions: (workspaceId: string, correlationId?: string) => 
    LifecycleService.getDashboardActions(workspaceId, correlationId),
  validateGate: (body: { phase: string; projectId: string }) => 
    LifecycleService.validateGate(body),
} as const;

// ============================================================================
// Intent/Shape Client (delegating to generated client)
// ============================================================================

export const intent = {
  capture: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: CaptureIntentRequest) =>
    IntentService.captureIntent(xTenantId, xWorkspaceId, xProjectId, body),
  analyze: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: { intentId: string }) =>
    IntentService.analyzeIntent(xTenantId, xWorkspaceId, xProjectId, body),
  get: (id: string, xTenantId: string, xWorkspaceId: string, xProjectId: string) =>
    IntentService.getIntent(id, xTenantId, xWorkspaceId, xProjectId),
} as const;

export const shape = {
  derive: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: DeriveShapeRequest) =>
    ShapeService.deriveShape(xTenantId, xWorkspaceId, xProjectId, body),
  model: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: GenerateModelRequest) =>
    ShapeService.modelShape(xTenantId, xWorkspaceId, xProjectId, body),
  get: (id: string) =>
    ShapeService.getShape(id),
} as const;

// ============================================================================
// Generate Client (delegating to generated client)
// ============================================================================

export const generate = {
  run: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: GenerateArtifactsRequest) =>
    GenerateService.generateArtifacts(xTenantId, xWorkspaceId, xProjectId, body),
  diff: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: RegenerateDiffRequest) =>
    GenerateService.generateDiff(xTenantId, xWorkspaceId, xProjectId, body),
  review: (runId: string, decision: GenerateReviewDecision, xTenantId: string, xWorkspaceId: string, xProjectId: string, body: GenerateReviewDecisionRequest) => {
    switch (decision) {
      case 'apply':
        return GenerateService.applyGenerationRun(runId, xTenantId, xWorkspaceId, xProjectId, body);
      case 'reject':
        return GenerateService.rejectGenerationRun(runId, xTenantId, xWorkspaceId, xProjectId, body);
      case 'rollback':
        return GenerateService.rollbackGenerationRun(runId, body);
    }
  },
  artifact: (id: string) =>
    GenerateService.getArtifacts(id),
} as const;

// ============================================================================
// Validate Client (delegating to generated client)
// ============================================================================

export const validate = {
  run: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: { executionId: string; projectId: string }) =>
    ValidateService.validateArtifacts(xTenantId, xWorkspaceId, xProjectId, body),
  withConfig: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: { executionId: string; projectId: string; config: Record<string, unknown> }) =>
    ValidateService.validateWithConfig(xTenantId, xWorkspaceId, xProjectId, body),
  withPolicy: (body: { executionId: string; policyId: string; projectId: string }) =>
    ValidateService.validateWithPolicy(body),
} as const;

// ============================================================================
// Audit Client (delegating to generated client)
// ============================================================================

export const audit = {
  emit: (body: AuditEventRequest) =>
    AuditService.recordAuditEvent(body),
} as const;

// ============================================================================
// Artifacts Client (delegating to generated client)
// ============================================================================

export const artifacts = {
  list: () => LifecycleService.listArtifacts(),
  get: (artifactId: string) => LifecycleService.getArtifact(artifactId),
  create: (body: CreateArtifactRequest) => LifecycleService.createArtifact(body),
  update: (artifactId: string, body: UpdateArtifactRequest) => LifecycleService.updateArtifact(artifactId, body),
  delete: (artifactId: string) => LifecycleService.deleteArtifact(artifactId),
} as const;

// ============================================================================
// Code Associations Client (delegating to generated client)
// ============================================================================

export const codeAssociations = {
  list: () => CodeAssociationsService.listCodeAssociations(),
  listForArtifact: (artifactId: string) => CodeAssociationsService.listArtifactCodeAssociations(artifactId),
  stats: (artifactId: string) => CodeAssociationsService.getCodeAssociationStats(artifactId),
  create: (body: CreateCodeAssociationRequest) => CodeAssociationsService.createCodeAssociation(body),
  delete: (associationId: string) => CodeAssociationsService.deleteCodeAssociation(associationId),
} as const;

// ============================================================================
// User Data Client (delegating to generated client)
// ============================================================================

export const userData = {
  requestDeletion: async (): Promise<{ statusUrl: string | null }> => {
    const response = await UserDataService.requestCurrentUserDataDeletion();
    // Generated service returns DeleteMyDataResponse with statusUrl
    return { statusUrl: response.statusUrl ?? null };
  },
} as const;

// ============================================================================
// Preview Session Client (delegating to generated client)
// ============================================================================

export const previewSessions = {
  issue: (xTenantId: string, xWorkspaceId: string, xProjectId: string, body: PreviewSessionContext) =>
    PreviewService.createPreviewSession(xTenantId, xWorkspaceId, xProjectId, body),
  validate: (body: ValidatePreviewSessionRequest) =>
    PreviewService.validatePreviewSession(body),
} as const;
