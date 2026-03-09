/**
 * Version History API Client
 * 
 * Client for interacting with the Version History API:
 * - Create versions
 * - Query version history
 * - Compare versions
 * - Rollback versions
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Version History API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    CreateVersionRequest,
    VersionResponse,
    VersionHistoryResponse,
    CompareVersionsResponse,
    RollbackRequest,
} from './types';

/**
 * Client for Version History API operations
 * 
 * @example
 * ```typescript
 * const client = new VersionApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const history = await client.getHistory('requirement-1', 'REQUIREMENT');
 * const comparison = await client.compareVersions('v1', 'v2');
 * ```
 */
export class VersionApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/version';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Create a new version
     */
    async createVersion(request: CreateVersionRequest): Promise<ApiResponse<VersionResponse>> {
        return this.post<VersionResponse>(`${this.basePath}/versions`, request);
    }

    /**
     * Get a specific version by ID
     */
    async getVersion(versionId: string): Promise<ApiResponse<VersionResponse>> {
        return this.get<VersionResponse>(`${this.basePath}/versions/${versionId}`);
    }

    /**
     * Get version history for a resource
     */
    async getHistory(
        resourceId: string,
        resourceType: string
    ): Promise<ApiResponse<VersionHistoryResponse>> {
        return this.get<VersionHistoryResponse>(
            `${this.basePath}/resources/${resourceType}/${resourceId}/history`
        );
    }

    /**
     * Get current version for a resource
     */
    async getCurrentVersion(
        resourceId: string,
        resourceType: string
    ): Promise<ApiResponse<VersionResponse>> {
        return this.get<VersionResponse>(
            `${this.basePath}/resources/${resourceType}/${resourceId}/current`
        );
    }

    /**
     * Compare two versions
     */
    async compareVersions(
        version1Id: string,
        version2Id: string
    ): Promise<ApiResponse<CompareVersionsResponse>> {
        return this.get<CompareVersionsResponse>(
            `${this.basePath}/compare`,
            { version1: version1Id, version2: version2Id }
        );
    }

    /**
     * Rollback to a previous version
     */
    async rollback(request: RollbackRequest): Promise<ApiResponse<VersionResponse>> {
        return this.post<VersionResponse>(`${this.basePath}/rollback`, request);
    }

    /**
     * Get snapshot at a specific version
     */
    async getSnapshot(versionId: string): Promise<ApiResponse<Record<string, unknown>>> {
        return this.get<Record<string, unknown>>(`${this.basePath}/versions/${versionId}/snapshot`);
    }

    /**
     * List all tags for a resource
     */
    async getTags(
        resourceId: string,
        resourceType: string
    ): Promise<ApiResponse<string[]>> {
        return this.get<string[]>(
            `${this.basePath}/resources/${resourceType}/${resourceId}/tags`
        );
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        const now = new Date();
        const mockVersions: VersionResponse[] = [
            {
                id: 'v3',
                resourceId: 'req-1',
                resourceType: 'REQUIREMENT',
                versionNumber: '3.0.0',
                previousVersionId: 'v2',
                status: 'RELEASED',
                createdBy: 'user-1',
                createdAt: now.toISOString(),
                description: 'Added acceptance criteria',
                changes: [
                    { path: 'acceptanceCriteria', oldValue: [], newValue: ['Given...When...Then'], changeType: 'UPDATE' },
                ],
                tags: ['v3', 'latest'],
            },
            {
                id: 'v2',
                resourceId: 'req-1',
                resourceType: 'REQUIREMENT',
                versionNumber: '2.0.0',
                previousVersionId: 'v1',
                status: 'APPROVED',
                createdBy: 'user-2',
                createdAt: new Date(now.getTime() - 86400000).toISOString(),
                description: 'Updated description',
                changes: [
                    { path: 'description', oldValue: 'Old desc', newValue: 'New desc', changeType: 'UPDATE' },
                ],
                tags: ['v2'],
            },
            {
                id: 'v1',
                resourceId: 'req-1',
                resourceType: 'REQUIREMENT',
                versionNumber: '1.0.0',
                status: 'DEPRECATED',
                createdBy: 'user-1',
                createdAt: new Date(now.getTime() - 86400000 * 2).toISOString(),
                description: 'Initial version',
                changes: [
                    { path: 'title', newValue: 'New Requirement', changeType: 'ADD' },
                    { path: 'description', newValue: 'Initial description', changeType: 'ADD' },
                ],
                tags: ['v1', 'initial'],
            },
        ];

        // Mock for version history
        this.registerMock<VersionHistoryResponse>(
            `${this.basePath}/resources/REQUIREMENT/req-1/history`,
            {
                resourceId: 'req-1',
                resourceType: 'REQUIREMENT',
                versions: mockVersions,
                totalVersions: 3,
                currentVersion: mockVersions[0],
                latestVersion: mockVersions[0],
            }
        );

        // Mock for compare versions
        this.registerMock<CompareVersionsResponse>(`${this.basePath}/compare`, {
            version1: mockVersions[2],
            version2: mockVersions[0],
            differences: [
                { path: 'description', valueIn1: 'Initial description', valueIn2: 'New desc', diffType: 'CHANGED' },
                { path: 'acceptanceCriteria', valueIn1: undefined, valueIn2: ['Given...When...Then'], diffType: 'ADDED' },
            ],
            summary: {
                added: 1,
                removed: 0,
                changed: 1,
                unchanged: 5,
            },
        });
    }
}
