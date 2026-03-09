/**
 * Requirements API Client
 * 
 * Client for interacting with the Requirements API:
 * - Create, update, delete requirements
 * - Query requirements with filters
 * - Get requirements funnel
 * - Quality scoring
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Requirements API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    PaginatedResponse,
    CreateRequirementRequest,
    RequirementResponse,
    QueryRequirementsRequest,
    RequirementsFunnelResponse,
    QualityScore,
} from './types';

/**
 * Client for Requirements API operations
 * 
 * @example
 * ```typescript
 * const client = new RequirementsApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const requirements = await client.queryRequirements({
 *   projectId: 'proj-1',
 *   status: 'IN_REVIEW',
 * });
 * const funnel = await client.getFunnel('proj-1');
 * ```
 */
export class RequirementsApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/requirements';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Create a new requirement
     */
    async createRequirement(request: CreateRequirementRequest): Promise<ApiResponse<RequirementResponse>> {
        return this.post<RequirementResponse>(this.basePath, request);
    }

    /**
     * Get a specific requirement by ID
     */
    async getRequirement(requirementId: string): Promise<ApiResponse<RequirementResponse>> {
        return this.get<RequirementResponse>(`${this.basePath}/${requirementId}`);
    }

    /**
     * Update a requirement
     */
    async updateRequirement(
        requirementId: string,
        updates: Partial<CreateRequirementRequest>
    ): Promise<ApiResponse<RequirementResponse>> {
        return this.put<RequirementResponse>(`${this.basePath}/${requirementId}`, updates);
    }

    /**
     * Delete a requirement
     */
    async deleteRequirement(requirementId: string): Promise<ApiResponse<void>> {
        return this.delete<void>(`${this.basePath}/${requirementId}`);
    }

    /**
     * Query requirements with filters
     */
    async queryRequirements(
        request: QueryRequirementsRequest
    ): Promise<ApiResponse<PaginatedResponse<RequirementResponse>>> {
        return this.get<PaginatedResponse<RequirementResponse>>(this.basePath, request as Record<string, unknown>);
    }

    /**
     * Get requirements funnel for a project
     */
    async getFunnel(projectId: string): Promise<ApiResponse<RequirementsFunnelResponse>> {
        return this.get<RequirementsFunnelResponse>(`${this.basePath}/funnel`, { projectId });
    }

    /**
     * Approve a requirement
     */
    async approveRequirement(
        requirementId: string,
        comment?: string
    ): Promise<ApiResponse<RequirementResponse>> {
        return this.post<RequirementResponse>(
            `${this.basePath}/${requirementId}/approve`,
            { comment }
        );
    }

    /**
     * Reject a requirement
     */
    async rejectRequirement(
        requirementId: string,
        reason: string
    ): Promise<ApiResponse<RequirementResponse>> {
        return this.post<RequirementResponse>(
            `${this.basePath}/${requirementId}/reject`,
            { reason }
        );
    }

    /**
     * Get quality score for a requirement
     */
    async getQualityScore(requirementId: string): Promise<ApiResponse<QualityScore>> {
        return this.get<QualityScore>(`${this.basePath}/${requirementId}/quality`);
    }

    /**
     * Transition requirement to next status
     */
    async transitionStatus(
        requirementId: string,
        targetStatus: string,
        comment?: string
    ): Promise<ApiResponse<RequirementResponse>> {
        return this.post<RequirementResponse>(
            `${this.basePath}/${requirementId}/transition`,
            { targetStatus, comment }
        );
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        const now = new Date();
        const mockRequirements: RequirementResponse[] = [
            {
                id: 'req-1',
                title: 'User Authentication',
                description: 'Implement OAuth2 authentication with SSO support',
                type: 'FUNCTIONAL',
                priority: 'HIGH',
                status: 'IN_DEVELOPMENT',
                projectId: 'proj-1',
                createdBy: 'user-1',
                createdAt: new Date(now.getTime() - 86400000 * 5).toISOString(),
                updatedAt: now.toISOString(),
                acceptanceCriteria: [
                    { id: 'ac-1', description: 'Users can login via SSO', verified: true, verifiedBy: 'user-2', verifiedAt: now.toISOString() },
                    { id: 'ac-2', description: 'Users can logout', verified: false },
                ],
                tags: ['auth', 'security', 'sprint-3'],
                qualityScore: {
                    overall: 85,
                    clarity: 90,
                    completeness: 80,
                    testability: 85,
                    feasibility: 90,
                    consistency: 80,
                    suggestions: ['Consider adding error scenarios'],
                },
                dependencies: ['req-0'],
                children: ['req-1a', 'req-1b'],
            },
            {
                id: 'req-2',
                title: 'Dashboard Analytics',
                description: 'Real-time analytics dashboard for monitoring',
                type: 'FEATURE',
                priority: 'MEDIUM',
                status: 'IN_REVIEW',
                projectId: 'proj-1',
                createdBy: 'user-2',
                createdAt: new Date(now.getTime() - 86400000 * 3).toISOString(),
                updatedAt: now.toISOString(),
                acceptanceCriteria: [
                    { id: 'ac-3', description: 'Dashboard updates in real-time', verified: false },
                ],
                tags: ['dashboard', 'analytics'],
                qualityScore: {
                    overall: 72,
                    clarity: 75,
                    completeness: 65,
                    testability: 70,
                    feasibility: 80,
                    consistency: 75,
                    suggestions: ['Add specific metrics to track', 'Define refresh rate'],
                },
                dependencies: [],
                children: [],
            },
        ];

        // Mock for query requirements
        this.registerMock<PaginatedResponse<RequirementResponse>>(this.basePath, {
            items: mockRequirements,
            totalItems: 2,
            page: 1,
            pageSize: 20,
            totalPages: 1,
            hasNext: false,
            hasPrevious: false,
        });

        // Mock for funnel
        this.registerMock<RequirementsFunnelResponse>(`${this.basePath}/funnel`, {
            projectId: 'proj-1',
            stages: [
                { status: 'DRAFT', count: 5, percentage: 10, avgTimeInStage: 86400, requirements: [] },
                { status: 'SUBMITTED', count: 8, percentage: 16, avgTimeInStage: 43200, requirements: [] },
                { status: 'IN_REVIEW', count: 12, percentage: 24, avgTimeInStage: 172800, requirements: [] },
                { status: 'APPROVED', count: 10, percentage: 20, avgTimeInStage: 21600, requirements: [] },
                { status: 'IN_DEVELOPMENT', count: 8, percentage: 16, avgTimeInStage: 432000, requirements: [] },
                { status: 'TESTING', count: 4, percentage: 8, avgTimeInStage: 172800, requirements: [] },
                { status: 'COMPLETED', count: 3, percentage: 6, avgTimeInStage: 0, requirements: [] },
            ],
            totalRequirements: 50,
            completionRate: 6,
            avgTimeToComplete: 907200,
            bottlenecks: [
                {
                    stage: 'IN_REVIEW',
                    severity: 'MEDIUM',
                    count: 4,
                    avgBlockedTime: 259200,
                    recommendations: ['Add more reviewers', 'Set SLA for reviews'],
                },
            ],
        });
    }
}
