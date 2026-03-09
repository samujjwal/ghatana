/**
 * Dashboard API Hooks
 *
 * React hooks for using Dashboard API clients with:
 * - Automatic configuration from context
 * - TanStack Query integration
 * - Loading/error state management
 * - Caching and refetching
 *
 * @doc.type hook
 * @doc.purpose React hooks for Dashboard API access
 * @doc.layer product
 * @doc.pattern Hook
 */

import { useMemo, useCallback } from 'react';
import {
  useQuery,
  useMutation,
  useQueryClient,
  UseQueryOptions,
  UseMutationOptions,
} from '@tanstack/react-query';
import {
  createDashboardClients,
  DashboardClients,
  DashboardApiConfig,
  AuditApiClient,
  VersionApiClient,
  AuthorizationApiClient,
  RequirementsApiClient,
  AISuggestionsApiClient,
  ArchitectureApiClient,
  WorkspaceApiClient,
  QueryAuditEventsRequest,
  AuditEventsPageResponse,
  VersionHistoryResponse,
  RequirementsFunnelResponse,
  SuggestionsInboxResponse,
  ArchitectureImpactResponse,
  DependencyGraphResponse,
  TechDebtSummary,
  RecordAuditEventRequest,
  CreateVersionRequest,
  CreateRequirementRequest,
  GenerateSuggestionRequest,
  AcceptSuggestionRequest,
  RejectSuggestionRequest,
  ListWorkspacesResponse,
  WorkspaceResponse,
  CreateWorkspaceRequest,
  UpdateWorkspaceRequest,
  ListMembersResponse,
  AddMemberRequest,
  UpdateMemberRequest,
  WorkspaceSettingsResponse,
  UpdateSettingsRequest,
  ApiResponse,
} from '../dashboard';

/**
 * Default API configuration
 * In production, this would come from environment variables
 * Uses VITE_API_ORIGIN for single-port architecture (Gateway on port 7002)
 */
const DEFAULT_API_CONFIG: DashboardApiConfig = {
  baseUrl: import.meta.env.DEV
    ? `${import.meta.env.VITE_API_ORIGIN ?? 'http://localhost:7002'}/api`
    : '/api',
  timeout: 10000,
  maxRetries: 3,
  logRequests: import.meta.env.DEV,
  logResponses: import.meta.env.DEV,
};

/**
 * Hook to get Dashboard API clients
 */
export function useDashboardClients(
  config?: Partial<DashboardApiConfig>
): DashboardClients {
  return useMemo(() => {
    const mergedConfig = { ...DEFAULT_API_CONFIG, ...config };
    return createDashboardClients(mergedConfig);
  }, [config?.baseUrl, config?.tenantId, config?.authToken]);
}

// ============================================================================
// Query Keys
// ============================================================================

export const dashboardQueryKeys = {
  // Audit
  audit: {
    all: ['audit'] as const,
    events: (params?: QueryAuditEventsRequest) =>
      ['audit', 'events', params] as const,
    event: (id: string) => ['audit', 'event', id] as const,
    summary: (params?: Record<string, unknown>) =>
      ['audit', 'summary', params] as const,
    resourceEvents: (resourceId: string, resourceType: string) =>
      ['audit', 'resource', resourceType, resourceId] as const,
  },

  // Version
  version: {
    all: ['version'] as const,
    history: (resourceId: string, resourceType: string) =>
      ['version', 'history', resourceType, resourceId] as const,
    version: (id: string) => ['version', 'detail', id] as const,
    compare: (v1: string, v2: string) =>
      ['version', 'compare', v1, v2] as const,
  },

  // Authorization
  auth: {
    all: ['auth'] as const,
    userPermissions: (userId: string) => ['auth', 'user', userId] as const,
    myPermissions: () => ['auth', 'me'] as const,
    personas: () => ['auth', 'personas'] as const,
    personaPermissions: (persona: string) =>
      ['auth', 'persona', persona] as const,
  },

  // Requirements
  requirements: {
    all: ['requirements'] as const,
    list: (params?: Record<string, unknown>) =>
      ['requirements', 'list', params] as const,
    detail: (id: string) => ['requirements', 'detail', id] as const,
    funnel: (projectId: string) =>
      ['requirements', 'funnel', projectId] as const,
    quality: (id: string) => ['requirements', 'quality', id] as const,
  },

  // AI Suggestions
  ai: {
    all: ['ai'] as const,
    inbox: () => ['ai', 'inbox'] as const,
    suggestion: (id: string) => ['ai', 'suggestion', id] as const,
    pending: (resourceId?: string) => ['ai', 'pending', resourceId] as const,
  },

  // Architecture
  architecture: {
    all: ['architecture'] as const,
    impact: (resourceId: string) =>
      ['architecture', 'impact', resourceId] as const,
    dependencies: (resourceId: string) =>
      ['architecture', 'dependencies', resourceId] as const,
    techDebt: (projectId?: string) =>
      ['architecture', 'tech-debt', projectId] as const,
    patterns: (projectId?: string) =>
      ['architecture', 'patterns', projectId] as const,
  },

  // Workspace
  workspace: {
    all: ['workspace'] as const,
    list: () => ['workspace', 'list'] as const,
    detail: (id: string) => ['workspace', 'detail', id] as const,
    members: (workspaceId: string) =>
      ['workspace', 'members', workspaceId] as const,
    settings: (workspaceId: string) =>
      ['workspace', 'settings', workspaceId] as const,
    teams: (workspaceId: string) =>
      ['workspace', 'teams', workspaceId] as const,
  },
};

// ============================================================================
// Audit Hooks
// ============================================================================

/**
 * Query audit events
 */
export function useAuditEvents(
  params?: QueryAuditEventsRequest,
  options?: Omit<
    UseQueryOptions<ApiResponse<AuditEventsPageResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.audit.events(params),
    queryFn: () => clients.audit.queryEvents(params || {}),
    ...options,
  });
}

/**
 * Mutation to record audit event
 */
export function useRecordAuditEvent(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    RecordAuditEventRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: RecordAuditEventRequest) =>
      clients.audit.recordEvent(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: dashboardQueryKeys.audit.all });
    },
    ...options,
  });
}

// ============================================================================
// Version Hooks
// ============================================================================

/**
 * Query version history for a resource
 */
export function useVersionHistory(
  resourceId: string,
  resourceType: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<VersionHistoryResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.version.history(resourceId, resourceType),
    queryFn: () => clients.version.getHistory(resourceId, resourceType),
    enabled: !!resourceId && !!resourceType,
    ...options,
  });
}

/**
 * Mutation to create a version
 */
export function useCreateVersion(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    CreateVersionRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateVersionRequest) =>
      clients.version.createVersion(request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.version.history(
          variables.resourceId,
          variables.resourceType
        ),
      });
    },
    ...options,
  });
}

// ============================================================================
// Requirements Hooks
// ============================================================================

/**
 * Query requirements funnel
 */
export function useRequirementsFunnel(
  projectId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<RequirementsFunnelResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.requirements.funnel(projectId),
    queryFn: () => clients.requirements.getFunnel(projectId),
    enabled: !!projectId,
    ...options,
  });
}

/**
 * Mutation to create requirement
 */
export function useCreateRequirement(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    CreateRequirementRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateRequirementRequest) =>
      clients.requirements.createRequirement(request),
    onSuccess: (_, variables) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.requirements.all,
      });
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.requirements.funnel(variables.projectId),
      });
    },
    ...options,
  });
}

// ============================================================================
// AI Suggestions Hooks
// ============================================================================

/**
 * Query AI suggestions inbox
 */
export function useAISuggestionsInbox(
  options?: Omit<
    UseQueryOptions<ApiResponse<SuggestionsInboxResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.ai.inbox(),
    queryFn: () => clients.aiSuggestions.getInbox(),
    ...options,
  });
}

/**
 * Mutation to generate AI suggestions
 */
export function useGenerateSuggestions(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    GenerateSuggestionRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: GenerateSuggestionRequest) =>
      clients.aiSuggestions.generateSuggestions(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: dashboardQueryKeys.ai.all });
    },
    ...options,
  });
}

/**
 * Mutation to accept suggestion
 */
export function useAcceptSuggestion(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    AcceptSuggestionRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: AcceptSuggestionRequest) =>
      clients.aiSuggestions.acceptSuggestion(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: dashboardQueryKeys.ai.all });
    },
    ...options,
  });
}

/**
 * Mutation to reject suggestion
 */
export function useRejectSuggestion(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    RejectSuggestionRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: RejectSuggestionRequest) =>
      clients.aiSuggestions.rejectSuggestion(request),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: dashboardQueryKeys.ai.all });
    },
    ...options,
  });
}

// ============================================================================
// Architecture Hooks
// ============================================================================

/**
 * Query architecture impact analysis
 */
export function useArchitectureImpact(
  resourceId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<ArchitectureImpactResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.architecture.impact(resourceId),
    queryFn: () => clients.architecture.getImpactAnalysis(resourceId),
    enabled: !!resourceId,
    ...options,
  });
}

/**
 * Query dependency graph
 */
export function useDependencyGraph(
  resourceId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<DependencyGraphResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.architecture.dependencies(resourceId),
    queryFn: () => clients.architecture.getDependencyGraph(resourceId),
    enabled: !!resourceId,
    ...options,
  });
}

/**
 * Query tech debt summary
 */
export function useTechDebt(
  projectId?: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<TechDebtSummary>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.architecture.techDebt(projectId),
    queryFn: () => clients.architecture.getTechDebt(projectId || ''),
    ...options,
  });
}

// ============================================================================
// Workspace Hooks
// ============================================================================

/**
 * Query list of workspaces
 */
export function useWorkspaces(
  options?: Omit<
    UseQueryOptions<ApiResponse<ListWorkspacesResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.workspace.list(),
    queryFn: () => clients.workspace.listWorkspaces(),
    ...options,
  });
}

/**
 * Query single workspace details
 */
export function useWorkspace(
  workspaceId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<WorkspaceResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.workspace.detail(workspaceId),
    queryFn: () => clients.workspace.getWorkspace(workspaceId),
    enabled: !!workspaceId,
    ...options,
  });
}

/**
 * Mutation to create workspace
 */
export function useCreateWorkspace(
  options?: UseMutationOptions<
    ApiResponse<WorkspaceResponse>,
    Error,
    CreateWorkspaceRequest
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (request: CreateWorkspaceRequest) =>
      clients.workspace.createWorkspace(request),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.all,
      });
    },
    ...options,
  });
}

/**
 * Mutation to update workspace
 */
export function useUpdateWorkspace(
  options?: UseMutationOptions<
    ApiResponse<WorkspaceResponse>,
    Error,
    { workspaceId: string; request: UpdateWorkspaceRequest }
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ workspaceId, request }) =>
      clients.workspace.updateWorkspace(workspaceId, request),
    onSuccess: (_, { workspaceId }) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.detail(workspaceId),
      });
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.list(),
      });
    },
    ...options,
  });
}

/**
 * Mutation to delete workspace
 */
export function useDeleteWorkspace(
  options?: UseMutationOptions<ApiResponse<unknown>, Error, string>
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (workspaceId: string) =>
      clients.workspace.deleteWorkspace(workspaceId),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.all,
      });
    },
    ...options,
  });
}

/**
 * Query workspace members
 */
export function useWorkspaceMembers(
  workspaceId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<ListMembersResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.workspace.members(workspaceId),
    queryFn: () => clients.workspace.listMembers(workspaceId),
    enabled: !!workspaceId,
    ...options,
  });
}

/**
 * Mutation to add member to workspace
 */
export function useAddWorkspaceMember(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    { workspaceId: string; request: AddMemberRequest }
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ workspaceId, request }) =>
      clients.workspace.addMember(workspaceId, request),
    onSuccess: (_, { workspaceId }) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.members(workspaceId),
      });
    },
    ...options,
  });
}

/**
 * Mutation to update workspace member
 */
export function useUpdateWorkspaceMember(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    { workspaceId: string; userId: string; request: UpdateMemberRequest }
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ workspaceId, userId, request }) =>
      clients.workspace.updateMember(workspaceId, userId, request),
    onSuccess: (_, { workspaceId }) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.members(workspaceId),
      });
    },
    ...options,
  });
}

/**
 * Mutation to remove member from workspace
 */
export function useRemoveWorkspaceMember(
  options?: UseMutationOptions<
    ApiResponse<unknown>,
    Error,
    { workspaceId: string; userId: string }
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ workspaceId, userId }) =>
      clients.workspace.removeMember(workspaceId, userId),
    onSuccess: (_, { workspaceId }) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.members(workspaceId),
      });
    },
    ...options,
  });
}

/**
 * Query workspace settings
 */
export function useWorkspaceSettings(
  workspaceId: string,
  options?: Omit<
    UseQueryOptions<ApiResponse<WorkspaceSettingsResponse>>,
    'queryKey' | 'queryFn'
  >
) {
  const clients = useDashboardClients();

  return useQuery({
    queryKey: dashboardQueryKeys.workspace.settings(workspaceId),
    queryFn: () => clients.workspace.getSettings(workspaceId),
    enabled: !!workspaceId,
    ...options,
  });
}

/**
 * Mutation to update workspace settings
 */
export function useUpdateWorkspaceSettings(
  options?: UseMutationOptions<
    ApiResponse<WorkspaceSettingsResponse>,
    Error,
    { workspaceId: string; request: UpdateSettingsRequest }
  >
) {
  const clients = useDashboardClients();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ workspaceId, request }) =>
      clients.workspace.updateSettings(workspaceId, request),
    onSuccess: (_, { workspaceId }) => {
      queryClient.invalidateQueries({
        queryKey: dashboardQueryKeys.workspace.settings(workspaceId),
      });
    },
    ...options,
  });
}
