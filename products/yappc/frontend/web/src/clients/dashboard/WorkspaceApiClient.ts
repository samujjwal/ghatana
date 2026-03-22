/**
 * Workspace API Client
 * 
 * Client for interacting with the Workspace Management API:
 * - Workspace CRUD operations
 * - Member management
 * - Team management
 * - Settings management
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Workspace API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    WorkspaceResponse,
    CreateWorkspaceRequest,
    UpdateWorkspaceRequest,
    ListWorkspacesResponse,
    WorkspaceMemberResponse,
    ListMembersResponse,
    AddMemberRequest,
    UpdateMemberRequest,
    WorkspaceSettingsResponse,
    UpdateSettingsRequest,
    TeamResponse,
    CreateTeamRequest,
} from './types';

/**
 * Client for Workspace API operations
 * 
 * @example
 * ```typescript
 * const client = new WorkspaceApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * // List workspaces
 * const workspaces = await client.listWorkspaces();
 * 
 * // Create a new workspace
 * const workspace = await client.createWorkspace({
 *   name: 'My Project',
 *   description: 'A new project workspace',
 * });
 * 
 * // Add a member
 * await client.addMember(workspace.data.id, {
 *   email: 'user@example.com',
 *   name: 'John Doe',
 *   role: 'MEMBER',
 *   persona: 'SOFTWARE_ENGINEER',
 * });
 * ```
 */
export class WorkspaceApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/workspaces';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    // ========== Workspace CRUD ==========

    /**
     * List all workspaces accessible by the current user
     */
    async listWorkspaces(): Promise<ApiResponse<ListWorkspacesResponse>> {
        return this.get<ListWorkspacesResponse>(this.basePath);
    }

    /**
     * Get a specific workspace by ID
     */
    async getWorkspace(workspaceId: string): Promise<ApiResponse<WorkspaceResponse>> {
        return this.get<WorkspaceResponse>(`${this.basePath}/${workspaceId}`);
    }

    /**
     * Create a new workspace
     */
    async createWorkspace(
        request: CreateWorkspaceRequest
    ): Promise<ApiResponse<WorkspaceResponse>> {
        return this.post<WorkspaceResponse>(this.basePath, request);
    }

    /**
     * Update a workspace
     */
    async updateWorkspace(
        workspaceId: string,
        request: UpdateWorkspaceRequest
    ): Promise<ApiResponse<WorkspaceResponse>> {
        return this.put<WorkspaceResponse>(`${this.basePath}/${workspaceId}`, request);
    }

    /**
     * Delete a workspace
     */
    async deleteWorkspace(workspaceId: string): Promise<ApiResponse<{ workspaceId: string; status: string; deletedAt: string }>> {
        return this.delete<{ workspaceId: string; status: string; deletedAt: string }>(
            `${this.basePath}/${workspaceId}`
        );
    }

    // ========== Member Management ==========

    /**
     * List all members of a workspace
     */
    async listMembers(workspaceId: string): Promise<ApiResponse<ListMembersResponse>> {
        return this.get<ListMembersResponse>(`${this.basePath}/${workspaceId}/members`);
    }

    /**
     * Add a member to a workspace
     */
    async addMember(
        workspaceId: string,
        request: AddMemberRequest
    ): Promise<ApiResponse<WorkspaceMemberResponse>> {
        return this.post<WorkspaceMemberResponse>(
            `${this.basePath}/${workspaceId}/members`,
            request
        );
    }

    /**
     * Update a member's role or persona
     */
    async updateMember(
        workspaceId: string,
        userId: string,
        request: UpdateMemberRequest
    ): Promise<ApiResponse<WorkspaceMemberResponse>> {
        return this.put<WorkspaceMemberResponse>(
            `${this.basePath}/${workspaceId}/members/${userId}`,
            request
        );
    }

    /**
     * Remove a member from a workspace
     */
    async removeMember(
        workspaceId: string,
        userId: string
    ): Promise<ApiResponse<{ workspaceId: string; userId: string; status: string; removedAt: string }>> {
        return this.delete<{ workspaceId: string; userId: string; status: string; removedAt: string }>(
            `${this.basePath}/${workspaceId}/members/${userId}`
        );
    }

    // ========== Settings Management ==========

    /**
     * Get workspace settings
     */
    async getSettings(workspaceId: string): Promise<ApiResponse<WorkspaceSettingsResponse>> {
        return this.get<WorkspaceSettingsResponse>(`${this.basePath}/${workspaceId}/settings`);
    }

    /**
     * Update workspace settings
     */
    async updateSettings(
        workspaceId: string,
        request: UpdateSettingsRequest
    ): Promise<ApiResponse<WorkspaceSettingsResponse>> {
        return this.put<WorkspaceSettingsResponse>(
            `${this.basePath}/${workspaceId}/settings`,
            request
        );
    }

    // ========== Team Management ==========

    /**
     * List all teams in a workspace
     */
    async listTeams(workspaceId: string): Promise<ApiResponse<{ teams: TeamResponse[]; total: number }>> {
        return this.get<{ teams: TeamResponse[]; total: number }>(
            `${this.basePath}/${workspaceId}/teams`
        );
    }

    /**
     * Create a new team in a workspace
     */
    async createTeam(
        workspaceId: string,
        request: CreateTeamRequest
    ): Promise<ApiResponse<TeamResponse>> {
        return this.post<TeamResponse>(`${this.basePath}/${workspaceId}/teams`, request);
    }

    /**
     * Get a specific team
     */
    async getTeam(workspaceId: string, teamId: string): Promise<ApiResponse<TeamResponse>> {
        return this.get<TeamResponse>(`${this.basePath}/${workspaceId}/teams/${teamId}`);
    }

    /**
     * Add a member to a team
     */
    async addTeamMember(
        workspaceId: string,
        teamId: string,
        userId: string
    ): Promise<ApiResponse<TeamResponse>> {
        return this.post<TeamResponse>(
            `${this.basePath}/${workspaceId}/teams/${teamId}/members`,
            { userId }
        );
    }

    /**
     * Remove a member from a team
     */
    async removeTeamMember(
        workspaceId: string,
        teamId: string,
        userId: string
    ): Promise<ApiResponse<{ teamId: string; userId: string; status: string }>> {
        return this.delete<{ teamId: string; userId: string; status: string }>(
            `${this.basePath}/${workspaceId}/teams/${teamId}/members/${userId}`
        );
    }

    // ========== Mock Registration ==========

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        const now = new Date().toISOString();

        // Mock workspace list
        this.registerMock('GET:/workspaces', {
            success: true,
            data: {
                workspaces: [
                    {
                        id: 'ws-1',
                        name: 'Default Workspace',
                        description: 'Main project workspace',
                        tenantId: 'tenant-1',
                        ownerId: 'user-1',
                        status: 'ACTIVE',
                        createdAt: now,
                        updatedAt: now,
                        teamsCount: 2,
                        membersCount: 5,
                        stats: { totalRequirements: 25, pendingReviews: 3, pendingSuggestions: 8 },
                        metadata: {},
                    },
                ],
                total: 1,
            },
            timestamp: now,
        });

        // Mock single workspace
        this.registerMock('GET:/workspaces/ws-1', {
            success: true,
            data: {
                id: 'ws-1',
                name: 'Default Workspace',
                description: 'Main project workspace',
                tenantId: 'tenant-1',
                ownerId: 'user-1',
                status: 'ACTIVE',
                createdAt: now,
                updatedAt: now,
                teamsCount: 2,
                membersCount: 5,
                stats: { totalRequirements: 25, pendingReviews: 3, pendingSuggestions: 8 },
                metadata: {},
            },
            timestamp: now,
        });

        // Mock members list
        this.registerMock('GET:/workspaces/ws-1/members', {
            success: true,
            data: {
                workspaceId: 'ws-1',
                members: [
                    {
                        userId: 'user-1',
                        email: 'owner@example.com',
                        name: 'John Owner',
                        role: 'OWNER',
                        persona: 'PRODUCT_MANAGER',
                        joinedAt: now,
                        lastActiveAt: now,
                        status: 'ACTIVE',
                    },
                    {
                        userId: 'user-2',
                        email: 'admin@example.com',
                        name: 'Jane Admin',
                        role: 'ADMIN',
                        persona: 'SOFTWARE_ENGINEER',
                        joinedAt: now,
                        lastActiveAt: now,
                        status: 'ACTIVE',
                    },
                ],
                total: 2,
            },
            timestamp: now,
        });

        // Mock settings
        this.registerMock('GET:/workspaces/ws-1/settings', {
            success: true,
            data: {
                workspaceId: 'ws-1',
                aiSuggestionsEnabled: true,
                autoVersioningEnabled: true,
                requireApprovalForChanges: false,
                defaultReviewers: 2,
                suggestionExpirationDays: 7,
                timezone: 'UTC',
                language: 'en',
                customSettings: {},
            },
            timestamp: now,
        });

        // Mock teams list
        this.registerMock('GET:/workspaces/ws-1/teams', {
            success: true,
            data: {
                teams: [
                    {
                        id: 'team-1',
                        name: 'Engineering',
                        description: 'Engineering team',
                        parentTeamId: null,
                        memberUserIds: ['user-2'],
                        leaderId: 'user-2',
                        createdAt: now,
                    },
                    {
                        id: 'team-2',
                        name: 'Frontend',
                        description: 'Frontend engineering team',
                        parentTeamId: 'team-1',
                        memberUserIds: [],
                        leaderId: null,
                        createdAt: now,
                    },
                ],
                total: 2,
            },
            timestamp: now,
        });
    }
}
