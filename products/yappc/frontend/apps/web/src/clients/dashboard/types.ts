/**
 * Dashboard API Type Definitions
 * 
 * Types for communicating with the YAPPC Dashboard Backend APIs:
 * - Audit Trail API
 * - Version History API
 * - Authorization API
 * - Requirements API
 * - AI Suggestions API
 * - Architecture Analysis API
 * 
 * @doc.type types
 * @doc.purpose Type definitions for dashboard API clients
 * @doc.layer product
 * @doc.pattern DTO
 */

// ============================================================================
// Common Types
// ============================================================================

/**
 * Standard API response wrapper
 */
export interface ApiResponse<T> {
    success: boolean;
    data?: T;
    error?: ApiError;
    timestamp: string;
}

/**
 * API error structure
 */
export interface ApiError {
    code: string;
    message: string;
    details?: Record<string, unknown>;
}

/**
 * Pagination parameters
 */
export interface PaginationParams {
    page?: number;
    pageSize?: number;
    sortBy?: string;
    sortOrder?: 'asc' | 'desc';
}

/**
 * Paginated response
 */
export interface PaginatedResponse<T> {
    items: T[];
    totalItems: number;
    page: number;
    pageSize: number;
    totalPages: number;
    hasNext: boolean;
    hasPrevious: boolean;
}

/**
 * Time range for queries
 */
export interface TimeRange {
    startTime?: string;
    endTime?: string;
}

// ============================================================================
// Audit Trail Types
// ============================================================================

/**
 * Audit event categories
 */
export type AuditCategory =
    | 'REQUIREMENT'
    | 'COMPONENT'
    | 'WORKFLOW'
    | 'USER'
    | 'SECURITY'
    | 'AI_SUGGESTION'
    | 'VERSION'
    | 'DEPLOYMENT'
    | 'INTEGRATION'
    | 'CONFIGURATION';

/**
 * Audit event actions
 */
export type AuditAction =
    | 'CREATE'
    | 'UPDATE'
    | 'DELETE'
    | 'VIEW'
    | 'APPROVE'
    | 'REJECT'
    | 'EXECUTE'
    | 'ACCEPT'
    | 'DEPLOY'
    | 'ROLLBACK'
    | 'CONFIGURE'
    | 'ACCESS';

/**
 * Audit event severity
 */
export type AuditSeverity = 'INFO' | 'WARNING' | 'ERROR' | 'CRITICAL';

/**
 * Request to record an audit event
 */
export interface RecordAuditEventRequest {
    resourceId: string;
    resourceType: string;
    action: AuditAction;
    category: AuditCategory;
    severity?: AuditSeverity;
    details?: Record<string, unknown>;
    metadata?: Record<string, unknown>;
}

/**
 * Query parameters for audit events
 */
export interface QueryAuditEventsRequest extends PaginationParams, TimeRange {
    resourceId?: string;
    resourceType?: string;
    userId?: string;
    category?: AuditCategory;
    action?: AuditAction;
    severity?: AuditSeverity;
}

/**
 * Audit event response
 */
export interface AuditEventResponse {
    id: string;
    tenantId: string;
    resourceId: string;
    resourceType: string;
    action: AuditAction;
    category: AuditCategory;
    severity: AuditSeverity;
    userId: string;
    userName?: string;
    userEmail?: string;
    timestamp: string;
    details: Record<string, unknown>;
    metadata?: Record<string, unknown>;
    ipAddress?: string;
    userAgent?: string;
}

/**
 * Paginated audit events response
 */
export interface AuditEventsPageResponse extends PaginatedResponse<AuditEventResponse> {
    summary?: AuditSummary;
}

/**
 * Audit summary statistics
 */
export interface AuditSummary {
    totalEvents: number;
    byCategory: Record<AuditCategory, number>;
    byAction: Record<AuditAction, number>;
    bySeverity: Record<AuditSeverity, number>;
    timeRange: TimeRange;
}

// ============================================================================
// Version History Types
// ============================================================================

/**
 * Version status
 */
export type VersionStatus = 'DRAFT' | 'PENDING' | 'APPROVED' | 'RELEASED' | 'DEPRECATED';

/**
 * Request to create a new version
 */
export interface CreateVersionRequest {
    resourceId: string;
    resourceType: string;
    versionNumber?: string;
    description?: string;
    changes: VersionChange[];
    tags?: string[];
    metadata?: Record<string, unknown>;
}

/**
 * Individual change in a version
 */
export interface VersionChange {
    path: string;
    oldValue?: unknown;
    newValue?: unknown;
    changeType: 'ADD' | 'UPDATE' | 'DELETE';
}

/**
 * Version response
 */
export interface VersionResponse {
    id: string;
    resourceId: string;
    resourceType: string;
    versionNumber: string;
    previousVersionId?: string;
    status: VersionStatus;
    createdBy: string;
    createdAt: string;
    description?: string;
    changes: VersionChange[];
    tags: string[];
    metadata?: Record<string, unknown>;
    snapshot?: Record<string, unknown>;
}

/**
 * Version history response
 */
export interface VersionHistoryResponse {
    resourceId: string;
    resourceType: string;
    versions: VersionResponse[];
    totalVersions: number;
    currentVersion?: VersionResponse;
    latestVersion?: VersionResponse;
}

/**
 * Version comparison result
 */
export interface CompareVersionsResponse {
    version1: VersionResponse;
    version2: VersionResponse;
    differences: VersionDiff[];
    summary: DiffSummary;
}

/**
 * Individual diff between versions
 */
export interface VersionDiff {
    path: string;
    valueIn1?: unknown;
    valueIn2?: unknown;
    diffType: 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED';
}

/**
 * Diff summary statistics
 */
export interface DiffSummary {
    added: number;
    removed: number;
    changed: number;
    unchanged: number;
}

/**
 * Request to rollback to a previous version
 */
export interface RollbackRequest {
    targetVersionId: string;
    reason: string;
    createBackup?: boolean;
}

// ============================================================================
// Authorization Types
// ============================================================================

/**
 * User persona types
 */
export type Persona =
    | 'Business Analyst'
    | 'Product Owner'
    | 'Requirements Engineer'
    | 'UX Designer'
    | 'UI Developer'
    | 'Backend Developer'
    | 'Full Stack Developer'
    | 'DevOps Engineer'
    | 'Security Engineer'
    | 'QA Engineer'
    | 'Performance Engineer'
    | 'Data Engineer'
    | 'ML Engineer'
    | 'Architect'
    | 'Tech Lead'
    | 'Engineering Manager'
    | 'Scrum Master'
    | 'Release Manager'
    | 'Compliance Officer'
    | 'Auditor'
    | 'Executive';

/**
 * Permission check request
 */
export interface CheckPermissionRequest {
    userId: string;
    resource: string;
    action: string;
    context?: Record<string, unknown>;
}

/**
 * Permission check response
 */
export interface CheckPermissionResponse {
    allowed: boolean;
    reason?: string;
    requiredPermissions: string[];
    userPermissions: string[];
    persona?: Persona;
    role?: string;
}

/**
 * User permissions response
 */
export interface UserPermissionsResponse {
    userId: string;
    persona: Persona;
    role: string;
    permissions: string[];
    effectivePermissions: string[];
    deniedPermissions: string[];
    groups: string[];
}

/**
 * Persona permissions mapping
 */
export interface PersonaPermissionsResponse {
    persona: Persona;
    role: string;
    permissions: string[];
    description: string;
}

// ============================================================================
// Requirements Types
// ============================================================================

/**
 * Requirement priority levels
 */
export type RequirementPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

/**
 * Requirement status in funnel
 */
export type RequirementStatus =
    | 'DRAFT'
    | 'SUBMITTED'
    | 'IN_REVIEW'
    | 'APPROVED'
    | 'IN_DEVELOPMENT'
    | 'TESTING'
    | 'COMPLETED';

/**
 * Requirement type
 */
export type RequirementType =
    | 'FUNCTIONAL'
    | 'NON_FUNCTIONAL'
    | 'TECHNICAL'
    | 'BUSINESS'
    | 'USER_STORY'
    | 'EPIC'
    | 'FEATURE';

/**
 * Request to create a requirement
 */
export interface CreateRequirementRequest {
    title: string;
    description: string;
    type: RequirementType;
    priority: RequirementPriority;
    projectId: string;
    parentId?: string;
    acceptanceCriteria?: string[];
    tags?: string[];
    metadata?: Record<string, unknown>;
}

/**
 * Requirement response
 */
export interface RequirementResponse {
    id: string;
    title: string;
    description: string;
    type: RequirementType;
    priority: RequirementPriority;
    status: RequirementStatus;
    projectId: string;
    parentId?: string;
    createdBy: string;
    createdAt: string;
    updatedAt: string;
    acceptanceCriteria: AcceptanceCriterion[];
    tags: string[];
    metadata?: Record<string, unknown>;
    qualityScore: QualityScore;
    dependencies: string[];
    children: string[];
}

/**
 * Acceptance criterion
 */
export interface AcceptanceCriterion {
    id: string;
    description: string;
    verified: boolean;
    verifiedBy?: string;
    verifiedAt?: string;
}

/**
 * Quality score breakdown
 */
export interface QualityScore {
    overall: number;
    clarity: number;
    completeness: number;
    testability: number;
    feasibility: number;
    consistency: number;
    suggestions: string[];
}

/**
 * Query requirements request
 */
export interface QueryRequirementsRequest extends PaginationParams {
    projectId?: string;
    status?: RequirementStatus;
    type?: RequirementType;
    priority?: RequirementPriority;
    createdBy?: string;
    tags?: string[];
    search?: string;
}

/**
 * Requirements funnel response
 */
export interface RequirementsFunnelResponse {
    projectId: string;
    stages: FunnelStage[];
    totalRequirements: number;
    completionRate: number;
    avgTimeToComplete: number;
    bottlenecks: FunnelBottleneck[];
}

/**
 * Funnel stage
 */
export interface FunnelStage {
    status: RequirementStatus;
    count: number;
    percentage: number;
    avgTimeInStage: number;
    requirements: RequirementResponse[];
}

/**
 * Funnel bottleneck
 */
export interface FunnelBottleneck {
    stage: RequirementStatus;
    severity: 'LOW' | 'MEDIUM' | 'HIGH';
    count: number;
    avgBlockedTime: number;
    recommendations: string[];
}

// ============================================================================
// AI Suggestions Types
// ============================================================================

/**
 * AI suggestion types
 */
export type SuggestionType =
    | 'CODE_COMPLETION'
    | 'REFACTORING'
    | 'SECURITY_FIX'
    | 'PERFORMANCE'
    | 'DOCUMENTATION'
    | 'TEST_GENERATION'
    | 'ARCHITECTURE'
    | 'REQUIREMENT_IMPROVEMENT';

/**
 * Suggestion status
 */
export type SuggestionStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'EXPIRED';

/**
 * Request to generate AI suggestions
 */
export interface GenerateSuggestionRequest {
    resourceId: string;
    resourceType: string;
    context: Record<string, unknown>;
    suggestionTypes?: SuggestionType[];
    maxSuggestions?: number;
    priority?: 'LOW' | 'MEDIUM' | 'HIGH';
}

/**
 * AI suggestion response
 */
export interface AISuggestionResponse {
    id: string;
    resourceId: string;
    resourceType: string;
    type: SuggestionType;
    status: SuggestionStatus;
    title: string;
    description: string;
    suggestedChange: SuggestedChange;
    confidence: number;
    impact: ImpactAnalysis;
    reasoning: string;
    createdAt: string;
    expiresAt?: string;
    processedBy?: string;
    processedAt?: string;
    feedback?: SuggestionFeedback;
}

/**
 * Suggested change details
 */
export interface SuggestedChange {
    before?: unknown;
    after: unknown;
    diff?: string;
    preview?: string;
}

/**
 * Impact analysis of suggestion
 */
export interface ImpactAnalysis {
    severity: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
    affectedComponents: string[];
    estimatedEffort: string;
    riskLevel: 'LOW' | 'MEDIUM' | 'HIGH';
    benefits: string[];
}

/**
 * Feedback on suggestion
 */
export interface SuggestionFeedback {
    rating: number;
    helpful: boolean;
    comment?: string;
}

/**
 * Request to accept a suggestion
 */
export interface AcceptSuggestionRequest {
    suggestionId: string;
    applyChanges?: boolean;
    modifications?: Record<string, unknown>;
    feedback?: SuggestionFeedback;
}

/**
 * Request to reject a suggestion
 */
export interface RejectSuggestionRequest {
    suggestionId: string;
    reason: string;
    feedback?: SuggestionFeedback;
}

/**
 * AI suggestions inbox response
 */
export interface SuggestionsInboxResponse {
    pending: AISuggestionResponse[];
    recent: AISuggestionResponse[];
    summary: SuggestionsSummary;
}

/**
 * Suggestions summary
 */
export interface SuggestionsSummary {
    totalPending: number;
    totalAccepted: number;
    totalRejected: number;
    avgConfidence: number;
    byType: Record<SuggestionType, number>;
    acceptanceRate: number;
}

// ============================================================================
// Architecture Analysis Types
// ============================================================================

/**
 * Risk level for architecture changes
 */
export type RiskLevel = 'NONE' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

/**
 * Architecture impact response
 */
export interface ArchitectureImpactResponse {
    resourceId: string;
    resourceType: string;
    riskLevel: RiskLevel;
    blastRadius: BlastRadius;
    impactedComponents: ImpactedComponent[];
    patternWarnings: PatternWarning[];
    techDebt: TechDebtSummary;
    recommendations: string[];
}

/**
 * Blast radius of a change
 */
export interface BlastRadius {
    directlyAffected: number;
    indirectlyAffected: number;
    potentiallyAffected: number;
    scope: 'LOCAL' | 'MODULE' | 'SERVICE' | 'SYSTEM' | 'GLOBAL';
}

/**
 * Impacted component details
 */
export interface ImpactedComponent {
    id: string;
    name: string;
    type: string;
    impactType: 'DIRECT' | 'INDIRECT' | 'POTENTIAL';
    severity: RiskLevel;
    reason: string;
    mitigations: string[];
}

/**
 * Pattern warning
 */
export interface PatternWarning {
    pattern: string;
    description: string;
    severity: RiskLevel;
    location: string;
    recommendation: string;
}

/**
 * Tech debt summary
 */
export interface TechDebtSummary {
    score: number;
    items: TechDebtItem[];
    trend: 'IMPROVING' | 'STABLE' | 'WORSENING';
    estimatedEffort: string;
}

/**
 * Individual tech debt item
 */
export interface TechDebtItem {
    id: string;
    type: string;
    description: string;
    severity: RiskLevel;
    effort: string;
    impact: string;
    createdAt: string;
}

/**
 * Dependency graph response
 */
export interface DependencyGraphResponse {
    rootId: string;
    nodes: GraphNode[];
    edges: GraphEdge[];
    clusters: GraphCluster[];
    statistics: GraphStatistics;
}

/**
 * Graph node
 */
export interface GraphNode {
    id: string;
    label: string;
    type: string;
    properties: Record<string, unknown>;
    clusterId?: string;
}

/**
 * Graph edge
 */
export interface GraphEdge {
    id: string;
    source: string;
    target: string;
    type: string;
    weight?: number;
    properties?: Record<string, unknown>;
}

/**
 * Graph cluster
 */
export interface GraphCluster {
    id: string;
    label: string;
    nodeIds: string[];
    color?: string;
}

/**
 * Graph statistics
 */
export interface GraphStatistics {
    nodeCount: number;
    edgeCount: number;
    clusterCount: number;
    avgDegree: number;
    maxDegree: number;
    density: number;
    hasCircularDependencies: boolean;
    circularDependencies: string[][];
}

// ============================================================================
// Client Configuration
// ============================================================================

/**
 * Dashboard API client configuration
 */
export interface DashboardApiConfig {
    /** Base URL for the API */
    baseUrl: string;

    /** Request timeout in milliseconds */
    timeout?: number;

    /** Maximum retry attempts */
    maxRetries?: number;

    /** Retry backoff multiplier */
    retryBackoffMultiplier?: number;

    /** Whether to log requests */
    logRequests?: boolean;

    /** Whether to log responses */
    logResponses?: boolean;

    /** Tenant ID for multi-tenant isolation */
    tenantId?: string;

    /** Auth token for authenticated requests */
    authToken?: string;
}

// ============================================================================
// Workspace Types
// ============================================================================

/**
 * Workspace status
 */
export type WorkspaceStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED' | 'DELETED';

/**
 * Member role within a workspace
 */
export type MemberRole = 'OWNER' | 'ADMIN' | 'MEMBER' | 'VIEWER';

/**
 * Member status within a workspace
 */
export type MemberStatus = 'ACTIVE' | 'PENDING_INVITE' | 'SUSPENDED' | 'REMOVED';

/**
 * Workspace response from API
 */
export interface WorkspaceResponse {
    id: string;
    name: string;
    description: string;
    tenantId: string;
    ownerId: string;
    status: WorkspaceStatus;
    createdAt: string;
    updatedAt: string;
    teamsCount: number;
    membersCount: number;
    stats: WorkspaceStats;
    metadata: Record<string, unknown>;
}

/**
 * Workspace statistics
 */
export interface WorkspaceStats {
    totalRequirements: number;
    pendingReviews: number;
    pendingSuggestions: number;
}

/**
 * Request to create a new workspace
 */
export interface CreateWorkspaceRequest {
    name: string;
    description?: string;
    metadata?: Record<string, unknown>;
}

/**
 * Request to update a workspace
 */
export interface UpdateWorkspaceRequest {
    name?: string;
    description?: string;
    status?: WorkspaceStatus;
    metadata?: Record<string, unknown>;
}

/**
 * Workspace member response
 */
export interface WorkspaceMemberResponse {
    userId: string;
    email: string;
    name: string;
    role: MemberRole;
    persona: string;
    joinedAt: string;
    lastActiveAt?: string;
    status: MemberStatus;
}

/**
 * Request to add a member to workspace
 */
export interface AddMemberRequest {
    email: string;
    name: string;
    role: MemberRole;
    persona?: string;
}

/**
 * Request to update a member
 */
export interface UpdateMemberRequest {
    role?: MemberRole;
    persona?: string;
}

/**
 * Workspace settings response
 */
export interface WorkspaceSettingsResponse {
    workspaceId: string;
    aiSuggestionsEnabled: boolean;
    autoVersioningEnabled: boolean;
    requireApprovalForChanges: boolean;
    defaultReviewers: number;
    suggestionExpirationDays: number;
    timezone: string;
    language: string;
    customSettings: Record<string, unknown>;
}

/**
 * Request to update workspace settings
 */
export interface UpdateSettingsRequest {
    aiSuggestionsEnabled?: boolean;
    autoVersioningEnabled?: boolean;
    requireApprovalForChanges?: boolean;
    defaultReviewers?: number;
    suggestionExpirationDays?: number;
    timezone?: string;
    language?: string;
    customSettings?: Record<string, unknown>;
}

/**
 * Workspace team response
 */
export interface TeamResponse {
    id: string;
    name: string;
    description: string;
    parentTeamId?: string;
    memberUserIds: string[];
    leaderId?: string;
    createdAt: string;
}

/**
 * Request to create a team
 */
export interface CreateTeamRequest {
    name: string;
    description?: string;
    parentTeamId?: string;
    memberUserIds?: string[];
    leaderId?: string;
}

/**
 * List workspaces response
 */
export interface ListWorkspacesResponse {
    workspaces: WorkspaceResponse[];
    total: number;
}

/**
 * List members response
 */
export interface ListMembersResponse {
    workspaceId: string;
    members: WorkspaceMemberResponse[];
    total: number;
}
