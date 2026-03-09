/**
 * Persona Hooks - Index
 *
 * Central export file for all persona-related React Query hooks.
 * These hooks provide real-time data polling and optimistic updates
 * for persona dashboard components.
 *
 * @doc.type index
 * @doc.purpose Centralized hook exports
 * @doc.layer product
 */

export { usePendingTasks, getTotalPendingTasks, useOptimisticTaskUpdate } from './usePendingTasks';
export type { UsePendingTasksOptions, UsePendingTasksResult } from './usePendingTasks';

export { useRecentActivities, filterActivitiesByStatus, groupActivitiesByDate } from './useRecentActivities';
export type { UseRecentActivitiesOptions, UseRecentActivitiesResult } from './useRecentActivities';

export { useMetrics, calculateMetricChange, formatMetricValue } from './useMetrics';
export type { UseMetricsOptions, UseMetricsResult } from './useMetrics';

export { usePinnedFeatures } from './usePinnedFeatures';
export type { UsePinnedFeaturesResult } from './usePinnedFeatures';

// Work Items hooks for Engineer Flow
export {
    useMyWorkItems,
    useWorkItem,
    useUpdateWorkItemStatus,
    useUpdateWorkItemPlan,
    useCompleteWorkItem,
    WORK_ITEMS_KEYS,
} from './useMyWorkItems';
export type { UseMyWorkItemsOptions, UseMyWorkItemsResult } from './useMyWorkItems';

// Unified Persona hooks (Human/Agent Agnostic)
export {
    useCurrentPersona,
    usePersonaWorkItems,
    useWorkSession,
    useGrowthGoals,
    usePlannedAbsences,
    useExecutionContext,
    useAvailabilityStatus,
    usePersonaDashboard,
    personaQueryKeys,
} from './useUnifiedPersona';

// Admin API hooks for Organization, Security, and Settings
export {
    // Query Keys
    ADMIN_QUERY_KEYS,
    // Tenant hooks
    useTenants,
    useTenant,
    useCreateTenant,
    useUpdateTenant,
    useTenantDeactivationCheck,
    useDeactivateTenant,
    // Environment hooks
    useTenantEnvironments,
    useCreateEnvironment,
    // Department hooks
    useDepartments,
    useDepartment,
    useCreateDepartment,
    // Team hooks
    useTeams,
    useTeam,
    useTeamServices,
    useCreateTeam,
    useUpdateTeam,
    // Service hooks
    useServices,
    useService,
    useCreateService,
    useUpdateServiceOwnership,
    useLinkServiceWorkflows,
    // Persona hooks
    usePersonas,
    usePersona,
    useCreatePersona,
    useUpdatePersonaMembers,
    useUpdatePersonaRoles,
    // Role hooks
    useRoles,
    useRole,
    useCreateRole,
    useUpdateRole,
    // Role assignment hooks
    useRoleAssignments,
    useCreateRoleAssignment,
    // Policy hooks
    usePolicies,
    usePolicy,
    useCreatePolicy,
    useUpdatePolicy,
    useSimulatePolicy,
    useUpdatePolicyStatus,
    // Permission simulator hooks
    useSimulatePermission,
    // Audit log hooks
    useAuditLog,
    useAuditEvent,
    useExportAuditLog,
    // Platform settings hooks
    usePlatformSettings,
    useUpdatePlatformSettings,
    // Integration hooks
    useIntegrations,
    useIntegration,
    useIntegrationsHealth,
    useCreateIntegration,
    useUpdateIntegration,
    useTestIntegration,
    useUpdateIntegrationStatus,
    // AI & Agent settings hooks
    useAIAgentSettings,
    useUpdateAIAgentSettings,
} from './useAdminApi';

export type {
    TenantResponse,
    TenantCreateBody,
    EnvironmentResponse,
    DepartmentResponse,
    TeamResponse,
    TeamCreateBody,
    ServiceResponse,
    ServiceCreateBody,
    PersonaResponse,
    PersonaCreateBody,
    RoleResponse,
    RoleCreateBody,
    PolicyResponse,
    PolicyCreateBody,
    PolicySimulateBody,
    PolicySimulateResponse,
    AuditEventResponse,
    AuditLogQuery,
    PlatformSettingsResponse,
    IntegrationResponse,
    IntegrationCreateBody,
    DeactivationCheckResponse,
    PaginatedResponse,
    AIAgentSettingsResponse,
} from './useAdminApi';

// Approvals API hooks
export { useDelegateApproval } from './useApprovalsApi';

// Organization & Structure API hooks
export {
    useOrgStructure,
    useDepartments as useOrgDepartments,
    useDepartment as useOrgDepartment,
    useAgents,
    useOrgGraph,
    useGenerateOrganization,
    useMaterializeOrganization,
    useAddAgentToDepartment,
    useMoveAgent,
    useCreateDepartment as useCreateOrgDepartment,
    orgQueryKeys,
} from './useOrganizationApi';

export type {
    Department as OrgDepartment,
    Agent,
    Team as OrgTeam,
    OrgStructure,
    GenesisRequest,
    GeneratedOrg,
} from './useOrganizationApi';

// Manage API hooks (Norms, Budget, Agent Marketplace)
export {
    // Norms
    useNorms,
    useNorm,
    useCreateNorm,
    useUpdateNorm,
    useDeleteNorm,
    useToggleNorm,
    // Budget
    useBudgetPlan,
    useUpdateBudget,
    useSubmitBudget,
    // Agent Marketplace
    useAgentTemplates,
    useAgentTemplate,
    useDeployAgent,
    useDeployedAgents,
    manageQueryKeys,
} from './useManageApi';

export type {
    Norm,
    CreateNormRequest,
    BudgetCategory,
    DepartmentBudget,
    BudgetPlan,
    BudgetUpdateRequest,
    AgentTemplate,
    DeployAgentRequest,
} from './useManageApi';

// Operate API hooks (Dashboard, Incidents, Queue, Tasks, Live Feed, Insights)
export {
    useDashboardStats,
    useRecentActivity,
    useIncidents,
    useIncident,
    useAcknowledgeIncident,
    useAssignIncident,
    useUpdateIncidentStatus,
    useQueueItems,
    useQueueItem,
    useApproveQueueItem,
    useRejectQueueItem,
    useLiveFeed,
    useTasks,
    useTask,
    useExecuteTaskAction,
    useAskInsights,
    useInsightsSuggestions,
    operateQueryKeys,
} from './useOperateApi';

export type {
    DashboardStats,
    Activity,
    Incident,
    QueueItem,
    Task,
    InsightMessage,
    InsightQuery,
} from './useOperateApi';

// People API hooks (Performance Reviews, Growth Plans)
export {
    // Performance Reviews
    usePerformanceReviews,
    usePerformanceReview,
    useDueReviews,
    useCreatePerformanceReview,
    useUpdatePerformanceReview,
    useSubmitReview,
    useGenerateReviewInsights,
    // Growth Plans
    useGrowthPlans,
    useGrowthPlan,
    useCreateGrowthPlan,
    useUpdateGrowthPlan,
    useCompleteGrowthPlan,
    peopleQueryKeys,
} from './usePeopleApi';

export type {
    PerformanceReview,
    CreateReviewRequest,
    UpdateReviewRequest,
    GrowthPlan,
    CreateGrowthPlanRequest,
    UpdateGrowthPlanRequest,
} from './usePeopleApi';
