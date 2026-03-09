/**
 * AI Suggestions API Client
 * 
 * Client for interacting with the AI Suggestions API:
 * - Generate AI suggestions
 * - Accept/reject suggestions
 * - Get suggestions inbox
 * - Provide feedback
 * 
 * @doc.type class
 * @doc.purpose HTTP client for AI Suggestions API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    GenerateSuggestionRequest,
    AISuggestionResponse,
    AcceptSuggestionRequest,
    RejectSuggestionRequest,
    SuggestionsInboxResponse,
    SuggestionFeedback,
} from './types';

/**
 * Client for AI Suggestions API operations
 * 
 * @example
 * ```typescript
 * const client = new AISuggestionsApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const suggestions = await client.generateSuggestions({
 *   resourceId: 'req-1',
 *   resourceType: 'REQUIREMENT',
 *   context: { description: 'Current requirement text' },
 *   suggestionTypes: ['REQUIREMENT_IMPROVEMENT', 'DOCUMENTATION'],
 * });
 * 
 * await client.acceptSuggestion({
 *   suggestionId: 'sug-1',
 *   applyChanges: true,
 * });
 * ```
 */
export class AISuggestionsApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/ai/suggestions';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Generate AI suggestions for a resource
     */
    async generateSuggestions(
        request: GenerateSuggestionRequest
    ): Promise<ApiResponse<AISuggestionResponse[]>> {
        return this.post<AISuggestionResponse[]>(`${this.basePath}/generate`, request);
    }

    /**
     * Get a specific suggestion by ID
     */
    async getSuggestion(suggestionId: string): Promise<ApiResponse<AISuggestionResponse>> {
        return this.get<AISuggestionResponse>(`${this.basePath}/${suggestionId}`);
    }

    /**
     * Accept a suggestion
     */
    async acceptSuggestion(request: AcceptSuggestionRequest): Promise<ApiResponse<AISuggestionResponse>> {
        return this.post<AISuggestionResponse>(`${this.basePath}/accept`, request);
    }

    /**
     * Reject a suggestion
     */
    async rejectSuggestion(request: RejectSuggestionRequest): Promise<ApiResponse<AISuggestionResponse>> {
        return this.post<AISuggestionResponse>(`${this.basePath}/reject`, request);
    }

    /**
     * Get suggestions inbox (pending and recent)
     */
    async getInbox(): Promise<ApiResponse<SuggestionsInboxResponse>> {
        return this.get<SuggestionsInboxResponse>(`${this.basePath}/inbox`);
    }

    /**
     * Get pending suggestions for a resource
     */
    async getPendingSuggestions(
        resourceId?: string,
        resourceType?: string
    ): Promise<ApiResponse<AISuggestionResponse[]>> {
        return this.get<AISuggestionResponse[]>(`${this.basePath}/pending`, { resourceId, resourceType });
    }

    /**
     * Submit feedback for a suggestion
     */
    async submitFeedback(
        suggestionId: string,
        feedback: SuggestionFeedback
    ): Promise<ApiResponse<void>> {
        return this.post<void>(`${this.basePath}/${suggestionId}/feedback`, feedback);
    }

    /**
     * Dismiss/expire a suggestion without rejecting
     */
    async dismissSuggestion(suggestionId: string): Promise<ApiResponse<void>> {
        return this.post<void>(`${this.basePath}/${suggestionId}/dismiss`, {});
    }

    /**
     * Bulk accept suggestions
     */
    async bulkAccept(suggestionIds: string[]): Promise<ApiResponse<AISuggestionResponse[]>> {
        return this.post<AISuggestionResponse[]>(`${this.basePath}/bulk-accept`, { suggestionIds });
    }

    /**
     * Bulk reject suggestions
     */
    async bulkReject(
        suggestionIds: string[],
        reason: string
    ): Promise<ApiResponse<AISuggestionResponse[]>> {
        return this.post<AISuggestionResponse[]>(`${this.basePath}/bulk-reject`, { suggestionIds, reason });
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        const now = new Date();
        const mockSuggestions: AISuggestionResponse[] = [
            {
                id: 'sug-1',
                resourceId: 'req-1',
                resourceType: 'REQUIREMENT',
                type: 'REQUIREMENT_IMPROVEMENT',
                status: 'PENDING',
                title: 'Improve requirement clarity',
                description: 'The requirement description could be more specific about error handling scenarios.',
                suggestedChange: {
                    before: 'Implement user authentication',
                    after: 'Implement OAuth2 user authentication with SSO support. Handle authentication failures with appropriate error messages.',
                    diff: '@@ -1 +1 @@\n-Implement user authentication\n+Implement OAuth2 user authentication with SSO support. Handle authentication failures with appropriate error messages.',
                },
                confidence: 0.87,
                impact: {
                    severity: 'LOW',
                    affectedComponents: ['AuthService', 'LoginUI'],
                    estimatedEffort: '30 minutes',
                    riskLevel: 'LOW',
                    benefits: ['Improved clarity', 'Better testability'],
                },
                reasoning: 'Adding specific authentication protocol and error handling improves implementation guidance.',
                createdAt: new Date(now.getTime() - 3600000).toISOString(),
                expiresAt: new Date(now.getTime() + 86400000 * 7).toISOString(),
            },
            {
                id: 'sug-2',
                resourceId: 'req-2',
                resourceType: 'REQUIREMENT',
                type: 'SECURITY_FIX',
                status: 'PENDING',
                title: 'Add security consideration',
                description: 'Consider adding rate limiting to prevent brute force attacks.',
                suggestedChange: {
                    after: 'Add rate limiting: max 5 failed attempts per minute per IP',
                },
                confidence: 0.92,
                impact: {
                    severity: 'MEDIUM',
                    affectedComponents: ['AuthService', 'RateLimiter'],
                    estimatedEffort: '2 hours',
                    riskLevel: 'LOW',
                    benefits: ['Enhanced security', 'Protection against brute force'],
                },
                reasoning: 'Security best practice for authentication endpoints.',
                createdAt: new Date(now.getTime() - 7200000).toISOString(),
            },
            {
                id: 'sug-3',
                resourceId: 'comp-1',
                resourceType: 'COMPONENT',
                type: 'REFACTORING',
                status: 'PENDING',
                title: 'Extract authentication logic',
                description: 'Consider extracting authentication logic into a separate service.',
                suggestedChange: {
                    before: 'class UserController { authenticate() {...} }',
                    after: 'class AuthService { authenticate() {...} }\nclass UserController { constructor(authService) {...} }',
                },
                confidence: 0.78,
                impact: {
                    severity: 'MEDIUM',
                    affectedComponents: ['UserController', 'AuthService'],
                    estimatedEffort: '4 hours',
                    riskLevel: 'MEDIUM',
                    benefits: ['Better separation of concerns', 'Improved testability'],
                },
                reasoning: 'Single Responsibility Principle suggests separating authentication from user management.',
                createdAt: new Date(now.getTime() - 14400000).toISOString(),
            },
        ];

        // Mock for inbox
        this.registerMock<SuggestionsInboxResponse>(`${this.basePath}/inbox`, {
            pending: mockSuggestions.filter(s => s.status === 'PENDING'),
            recent: [
                {
                    ...mockSuggestions[0],
                    id: 'sug-old-1',
                    status: 'ACCEPTED',
                    processedBy: 'user-1',
                    processedAt: new Date(now.getTime() - 86400000).toISOString(),
                    feedback: { rating: 5, helpful: true, comment: 'Great suggestion!' },
                },
                {
                    ...mockSuggestions[1],
                    id: 'sug-old-2',
                    status: 'REJECTED',
                    processedBy: 'user-2',
                    processedAt: new Date(now.getTime() - 86400000 * 2).toISOString(),
                    feedback: { rating: 2, helpful: false, comment: 'Not applicable to our use case' },
                },
            ],
            summary: {
                totalPending: 3,
                totalAccepted: 45,
                totalRejected: 12,
                avgConfidence: 0.82,
                byType: {
                    CODE_COMPLETION: 5,
                    REFACTORING: 8,
                    SECURITY_FIX: 4,
                    PERFORMANCE: 3,
                    DOCUMENTATION: 10,
                    TEST_GENERATION: 12,
                    ARCHITECTURE: 6,
                    REQUIREMENT_IMPROVEMENT: 12,
                },
                acceptanceRate: 0.79,
            },
        });

        // Mock for generate
        this.registerMock<AISuggestionResponse[]>(`${this.basePath}/generate`, mockSuggestions.slice(0, 2));

        // Mock for pending
        this.registerMock<AISuggestionResponse[]>(`${this.basePath}/pending`, mockSuggestions);
    }
}
