/**
 * Audit Trail API Client
 * 
 * Client for interacting with the Audit Trail API:
 * - Record audit events
 * - Query audit history
 * - Get audit statistics
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Audit Trail API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    RecordAuditEventRequest,
    QueryAuditEventsRequest,
    AuditEventResponse,
    AuditEventsPageResponse,
    AuditSummary,
} from './types';

/**
 * Client for Audit Trail API operations
 * 
 * @example
 * ```typescript
 * const client = new AuditApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const events = await client.queryEvents({
 *   category: 'REQUIREMENT',
 *   action: 'CREATE',
 *   page: 1,
 *   pageSize: 20,
 * });
 * ```
 */
export class AuditApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/audit';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Record a new audit event
     */
    async recordEvent(request: RecordAuditEventRequest): Promise<ApiResponse<AuditEventResponse>> {
        return this.post<AuditEventResponse>(`${this.basePath}/events`, request);
    }

    /**
     * Query audit events with filters
     */
    async queryEvents(request: QueryAuditEventsRequest): Promise<ApiResponse<AuditEventsPageResponse>> {
        return this.get<AuditEventsPageResponse>(`${this.basePath}/events`, request as Record<string, unknown>);
    }

    /**
     * Get a specific audit event by ID
     */
    async getEvent(eventId: string): Promise<ApiResponse<AuditEventResponse>> {
        return this.get<AuditEventResponse>(`${this.basePath}/events/${eventId}`);
    }

    /**
     * Get audit events for a specific resource
     */
    async getResourceEvents(
        resourceId: string,
        resourceType: string
    ): Promise<ApiResponse<AuditEventsPageResponse>> {
        return this.get<AuditEventsPageResponse>(`${this.basePath}/resources/${resourceType}/${resourceId}`);
    }

    /**
     * Get audit statistics/summary
     */
    async getSummary(params?: {
        startTime?: string;
        endTime?: string;
        category?: string;
    }): Promise<ApiResponse<AuditSummary>> {
        return this.get<AuditSummary>(`${this.basePath}/summary`, params);
    }

    /**
     * Get user's recent activity
     */
    async getUserActivity(
        userId: string,
        limit?: number
    ): Promise<ApiResponse<AuditEventsPageResponse>> {
        return this.get<AuditEventsPageResponse>(`${this.basePath}/users/${userId}/activity`, { limit });
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        // Mock for query events
        this.registerMock<AuditEventsPageResponse>(`${this.basePath}/events`, {
            items: [
                {
                    id: 'audit-1',
                    tenantId: 'tenant-123',
                    resourceId: 'req-1',
                    resourceType: 'REQUIREMENT',
                    action: 'CREATE',
                    category: 'REQUIREMENT',
                    severity: 'INFO',
                    userId: 'user-1',
                    userName: 'John Doe',
                    userEmail: 'john@example.com',
                    timestamp: new Date().toISOString(),
                    details: { title: 'New requirement created' },
                },
                {
                    id: 'audit-2',
                    tenantId: 'tenant-123',
                    resourceId: 'comp-1',
                    resourceType: 'COMPONENT',
                    action: 'UPDATE',
                    category: 'COMPONENT',
                    severity: 'INFO',
                    userId: 'user-2',
                    userName: 'Jane Smith',
                    userEmail: 'jane@example.com',
                    timestamp: new Date(Date.now() - 3600000).toISOString(),
                    details: { field: 'description', oldValue: 'Old', newValue: 'New' },
                },
            ],
            totalItems: 2,
            page: 1,
            pageSize: 20,
            totalPages: 1,
            hasNext: false,
            hasPrevious: false,
        });

        // Mock for summary
        this.registerMock<AuditSummary>(`${this.basePath}/summary`, {
            totalEvents: 1250,
            byCategory: {
                REQUIREMENT: 450,
                COMPONENT: 320,
                WORKFLOW: 180,
                USER: 100,
                SECURITY: 50,
                AI_SUGGESTION: 80,
                VERSION: 40,
                DEPLOYMENT: 20,
                INTEGRATION: 5,
                CONFIGURATION: 5,
            },
            byAction: {
                CREATE: 400,
                UPDATE: 500,
                DELETE: 50,
                VIEW: 200,
                APPROVE: 30,
                REJECT: 20,
                EXECUTE: 25,
                ACCEPT: 15,
                DEPLOY: 5,
                ROLLBACK: 3,
                CONFIGURE: 1,
                ACCESS: 1,
            },
            bySeverity: {
                INFO: 1100,
                WARNING: 100,
                ERROR: 40,
                CRITICAL: 10,
            },
            timeRange: {
                startTime: new Date(Date.now() - 86400000 * 7).toISOString(),
                endTime: new Date().toISOString(),
            },
        });
    }
}
