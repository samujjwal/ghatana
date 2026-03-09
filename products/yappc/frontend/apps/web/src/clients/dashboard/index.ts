/**
 * Dashboard API Clients
 * 
 * Exports all API clients for the YAPPC Dashboard:
 * - AuditApiClient - Audit trail operations
 * - VersionApiClient - Version history operations
 * - AuthorizationApiClient - Permission checks
 * - RequirementsApiClient - Requirements management
 * - AISuggestionsApiClient - AI suggestion handling
 * - ArchitectureApiClient - Architecture analysis
 * - WorkspaceApiClient - Workspace management
 * 
 * @doc.type module
 * @doc.purpose API client exports for dashboard
 * @doc.layer product
 * @doc.pattern Module
 */

// Base client
export { BaseDashboardApiClient, type ClientMode } from './BaseDashboardApiClient';

// API Clients
import { AuditApiClient } from './AuditApiClient';
import { VersionApiClient } from './VersionApiClient';
import { AuthorizationApiClient } from './AuthorizationApiClient';
import { RequirementsApiClient } from './RequirementsApiClient';
import { AISuggestionsApiClient } from './AISuggestionsApiClient';
import { ArchitectureApiClient } from './ArchitectureApiClient';
import { WorkspaceApiClient } from './WorkspaceApiClient';
import { WorkflowAgentApiClient } from './WorkflowAgentApiClient';

// Re-export API Clients
export { AuditApiClient, VersionApiClient, AuthorizationApiClient, RequirementsApiClient, AISuggestionsApiClient, ArchitectureApiClient, WorkspaceApiClient, WorkflowAgentApiClient };

// Re-export Workflow Agent types
export type {
    WorkflowAgentRole,
    ExecutionPriority,
    ExecutionStatus,
    ExecuteAgentRequest,
    ExecuteBatchRequest,
    ExecutionMetrics,
    AgentExecutionResult,
    BatchExecutionResponse,
    AgentInfo,
    ListAgentsResponse,
    AgentsByRoleResponse,
    AgentHealthInfo,
    ExecutionStatusResponse,
    CancellationResponse,
} from './WorkflowAgentApiClient';

// Types
export type {
    // Common types
    ApiResponse,
    ApiError,
    PaginationParams,
    PaginatedResponse,
    TimeRange,
    DashboardApiConfig,

    // Audit types
    AuditCategory,
    AuditAction,
    AuditSeverity,
    RecordAuditEventRequest,
    QueryAuditEventsRequest,
    AuditEventResponse,
    AuditEventsPageResponse,
    AuditSummary,

    // Version types
    VersionStatus,
    CreateVersionRequest,
    VersionChange,
    VersionResponse,
    VersionHistoryResponse,
    CompareVersionsResponse,
    VersionDiff,
    DiffSummary,
    RollbackRequest,

    // Authorization types
    Persona,
    CheckPermissionRequest,
    CheckPermissionResponse,
    UserPermissionsResponse,
    PersonaPermissionsResponse,

    // Requirements types
    RequirementPriority,
    RequirementStatus,
    RequirementType,
    CreateRequirementRequest,
    RequirementResponse,
    AcceptanceCriterion,
    QualityScore,
    QueryRequirementsRequest,
    RequirementsFunnelResponse,
    FunnelStage,
    FunnelBottleneck,

    // AI Suggestions types
    SuggestionType,
    SuggestionStatus,
    GenerateSuggestionRequest,
    AISuggestionResponse,
    SuggestedChange,
    ImpactAnalysis,
    SuggestionFeedback,
    AcceptSuggestionRequest,
    RejectSuggestionRequest,
    SuggestionsInboxResponse,
    SuggestionsSummary,

    // Architecture types
    RiskLevel,
    ArchitectureImpactResponse,
    BlastRadius,
    ImpactedComponent,
    PatternWarning,
    TechDebtSummary,
    TechDebtItem,
    DependencyGraphResponse,
    GraphNode,
    GraphEdge,
    GraphCluster,
    GraphStatistics,

    // Workspace types
    WorkspaceStatus,
    MemberRole,
    MemberStatus,
    WorkspaceResponse,
    WorkspaceStats,
    CreateWorkspaceRequest,
    UpdateWorkspaceRequest,
    WorkspaceMemberResponse,
    AddMemberRequest,
    UpdateMemberRequest,
    WorkspaceSettingsResponse,
    UpdateSettingsRequest,
    TeamResponse,
    CreateTeamRequest,
    ListWorkspacesResponse,
    ListMembersResponse,
} from './types';

/**
 * Create all dashboard API clients with shared configuration
 */
export function createDashboardClients(config: import('./types').DashboardApiConfig) {
    return {
        audit: new AuditApiClient(config),
        version: new VersionApiClient(config),
        authorization: new AuthorizationApiClient(config),
        requirements: new RequirementsApiClient(config),
        aiSuggestions: new AISuggestionsApiClient(config),
        architecture: new ArchitectureApiClient(config),
        workspace: new WorkspaceApiClient(config),
        workflowAgent: new WorkflowAgentApiClient(config),
    };
}

/**
 * Dashboard API client collection type
 */
export type DashboardClients = ReturnType<typeof createDashboardClients>;
