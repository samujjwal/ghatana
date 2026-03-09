/**
 * Architecture Analysis API Client
 * 
 * Client for interacting with the Architecture Analysis API:
 * - Get architecture impact analysis
 * - Get dependency graphs
 * - Analyze tech debt
 * - Check pattern compliance
 * 
 * @doc.type class
 * @doc.purpose HTTP client for Architecture Analysis API
 * @doc.layer product
 * @doc.pattern Service
 */

import { BaseDashboardApiClient, ClientMode } from './BaseDashboardApiClient';
import type {
    DashboardApiConfig,
    ApiResponse,
    ArchitectureImpactResponse,
    DependencyGraphResponse,
    TechDebtSummary,
    PatternWarning,
} from './types';

/**
 * Request for impact simulation
 */
export interface SimulateImpactRequest {
    resourceId: string;
    resourceType: string;
    proposedChanges: Record<string, unknown>;
}

/**
 * Client for Architecture Analysis API operations
 * 
 * @example
 * ```typescript
 * const client = new ArchitectureApiClient({
 *   baseUrl: 'http://localhost:8080/api',
 *   tenantId: 'tenant-123',
 *   authToken: 'jwt-token',
 * });
 * 
 * const impact = await client.getImpactAnalysis('comp-1');
 * const graph = await client.getDependencyGraph('service-1');
 * const techDebt = await client.getTechDebt('proj-1');
 * ```
 */
export class ArchitectureApiClient extends BaseDashboardApiClient {
    private readonly basePath = '/architecture';

    constructor(config: DashboardApiConfig, mode: ClientMode = 'http') {
        super(config, mode);
        this.registerDefaultMocks();
    }

    /**
     * Get impact analysis for a resource
     */
    async getImpactAnalysis(resourceId: string): Promise<ApiResponse<ArchitectureImpactResponse>> {
        return this.get<ArchitectureImpactResponse>(`${this.basePath}/impact/${resourceId}`);
    }

    /**
     * Get dependency graph for a resource
     */
    async getDependencyGraph(resourceId: string): Promise<ApiResponse<DependencyGraphResponse>> {
        return this.get<DependencyGraphResponse>(`${this.basePath}/dependencies/${resourceId}`);
    }

    /**
     * Get tech debt summary for a project
     */
    async getTechDebt(projectId: string): Promise<ApiResponse<TechDebtSummary>> {
        return this.get<TechDebtSummary>(`${this.basePath}/tech-debt`, { projectId });
    }

    /**
     * Get pattern compliance warnings
     */
    async getPatternWarnings(projectId?: string): Promise<ApiResponse<PatternWarning[]>> {
        return this.get<PatternWarning[]>(`${this.basePath}/patterns`, { projectId });
    }

    /**
     * Simulate impact of proposed changes
     */
    async simulateImpact(request: SimulateImpactRequest): Promise<ApiResponse<ArchitectureImpactResponse>> {
        return this.post<ArchitectureImpactResponse>(`${this.basePath}/simulate`, request);
    }

    /**
     * Get circular dependencies
     */
    async getCircularDependencies(projectId?: string): Promise<ApiResponse<string[][]>> {
        return this.get<string[][]>(`${this.basePath}/circular-dependencies`, { projectId });
    }

    /**
     * Get component coupling metrics
     */
    async getCouplingMetrics(componentId: string): Promise<ApiResponse<{
        afferentCoupling: number;
        efferentCoupling: number;
        instability: number;
        abstractness: number;
        distanceFromMain: number;
    }>> {
        return this.get(`${this.basePath}/metrics/coupling/${componentId}`);
    }

    /**
     * Register default mock responses for testing
     */
    private registerDefaultMocks(): void {
        // Mock for impact analysis
        this.registerMock<ArchitectureImpactResponse>(`${this.basePath}/impact/comp-1`, {
            resourceId: 'comp-1',
            resourceType: 'COMPONENT',
            riskLevel: 'MEDIUM',
            blastRadius: {
                directlyAffected: 3,
                indirectlyAffected: 7,
                potentiallyAffected: 12,
                scope: 'SERVICE',
            },
            impactedComponents: [
                {
                    id: 'comp-2',
                    name: 'UserService',
                    type: 'SERVICE',
                    impactType: 'DIRECT',
                    severity: 'MEDIUM',
                    reason: 'Direct dependency on AuthService',
                    mitigations: ['Add abstraction layer', 'Use dependency injection'],
                },
                {
                    id: 'comp-3',
                    name: 'OrderService',
                    type: 'SERVICE',
                    impactType: 'INDIRECT',
                    severity: 'LOW',
                    reason: 'Indirect dependency via UserService',
                    mitigations: ['Verify user context handling'],
                },
                {
                    id: 'comp-4',
                    name: 'NotificationService',
                    type: 'SERVICE',
                    impactType: 'POTENTIAL',
                    severity: 'LOW',
                    reason: 'May require user context',
                    mitigations: ['Review notification triggers'],
                },
            ],
            patternWarnings: [
                {
                    pattern: 'Circular Dependency',
                    description: 'AuthService <-> UserService creates a circular dependency',
                    severity: 'HIGH',
                    location: 'AuthService.ts:45',
                    recommendation: 'Extract shared logic into a separate module',
                },
                {
                    pattern: 'God Object',
                    description: 'AuthService has too many responsibilities',
                    severity: 'MEDIUM',
                    location: 'AuthService.ts',
                    recommendation: 'Split into AuthenticationService and AuthorizationService',
                },
            ],
            techDebt: {
                score: 65,
                items: [
                    {
                        id: 'td-1',
                        type: 'CODE_SMELL',
                        description: 'Large method in AuthService.authenticate()',
                        severity: 'MEDIUM',
                        effort: '2 hours',
                        impact: 'Maintainability',
                        createdAt: new Date().toISOString(),
                    },
                    {
                        id: 'td-2',
                        type: 'OUTDATED_DEPENDENCY',
                        description: 'JWT library is 2 major versions behind',
                        severity: 'HIGH',
                        effort: '4 hours',
                        impact: 'Security',
                        createdAt: new Date().toISOString(),
                    },
                ],
                trend: 'STABLE',
                estimatedEffort: '8 hours',
            },
            recommendations: [
                'Consider breaking circular dependency before making changes',
                'Update JWT library to address known vulnerabilities',
                'Add integration tests before refactoring',
            ],
        });

        // Mock for dependency graph
        this.registerMock<DependencyGraphResponse>(`${this.basePath}/dependencies/service-1`, {
            rootId: 'service-1',
            nodes: [
                { id: 'auth-service', label: 'AuthService', type: 'SERVICE', properties: { layer: 'application' } },
                { id: 'user-service', label: 'UserService', type: 'SERVICE', properties: { layer: 'application' } },
                { id: 'order-service', label: 'OrderService', type: 'SERVICE', properties: { layer: 'application' } },
                { id: 'user-repo', label: 'UserRepository', type: 'REPOSITORY', properties: { layer: 'infrastructure' } },
                { id: 'db-client', label: 'DatabaseClient', type: 'CLIENT', properties: { layer: 'infrastructure' } },
                { id: 'cache-client', label: 'CacheClient', type: 'CLIENT', properties: { layer: 'infrastructure' } },
            ],
            edges: [
                { id: 'e1', source: 'auth-service', target: 'user-service', type: 'DEPENDS_ON', weight: 1 },
                { id: 'e2', source: 'user-service', target: 'user-repo', type: 'USES', weight: 1 },
                { id: 'e3', source: 'order-service', target: 'user-service', type: 'DEPENDS_ON', weight: 0.5 },
                { id: 'e4', source: 'user-repo', target: 'db-client', type: 'USES', weight: 1 },
                { id: 'e5', source: 'auth-service', target: 'cache-client', type: 'USES', weight: 0.8 },
                { id: 'e6', source: 'user-service', target: 'auth-service', type: 'DEPENDS_ON', weight: 0.3 },
            ],
            clusters: [
                { id: 'app-layer', label: 'Application Layer', nodeIds: ['auth-service', 'user-service', 'order-service'], color: '#4CAF50' },
                { id: 'infra-layer', label: 'Infrastructure Layer', nodeIds: ['user-repo', 'db-client', 'cache-client'], color: '#2196F3' },
            ],
            statistics: {
                nodeCount: 6,
                edgeCount: 6,
                clusterCount: 2,
                avgDegree: 2,
                maxDegree: 4,
                density: 0.33,
                hasCircularDependencies: true,
                circularDependencies: [['auth-service', 'user-service', 'auth-service']],
            },
        });

        // Mock for tech debt
        this.registerMock<TechDebtSummary>(`${this.basePath}/tech-debt`, {
            score: 72,
            items: [
                {
                    id: 'td-1',
                    type: 'CODE_SMELL',
                    description: 'Duplicated authentication logic in 3 files',
                    severity: 'MEDIUM',
                    effort: '3 hours',
                    impact: 'Maintainability',
                    createdAt: new Date(Date.now() - 86400000 * 7).toISOString(),
                },
                {
                    id: 'td-2',
                    type: 'OUTDATED_DEPENDENCY',
                    description: '5 npm packages have available security updates',
                    severity: 'HIGH',
                    effort: '2 hours',
                    impact: 'Security',
                    createdAt: new Date(Date.now() - 86400000 * 14).toISOString(),
                },
                {
                    id: 'td-3',
                    type: 'MISSING_TEST',
                    description: 'OrderService has 45% test coverage (target: 80%)',
                    severity: 'MEDIUM',
                    effort: '8 hours',
                    impact: 'Reliability',
                    createdAt: new Date(Date.now() - 86400000 * 3).toISOString(),
                },
                {
                    id: 'td-4',
                    type: 'DOCUMENTATION',
                    description: 'API endpoints missing OpenAPI documentation',
                    severity: 'LOW',
                    effort: '4 hours',
                    impact: 'Developer Experience',
                    createdAt: new Date(Date.now() - 86400000 * 21).toISOString(),
                },
            ],
            trend: 'IMPROVING',
            estimatedEffort: '17 hours',
        });

        // Mock for pattern warnings
        this.registerMock<PatternWarning[]>(`${this.basePath}/patterns`, [
            {
                pattern: 'Circular Dependency',
                description: 'AuthService and UserService have bidirectional dependencies',
                severity: 'HIGH',
                location: 'src/services/',
                recommendation: 'Extract shared types to a common module',
            },
            {
                pattern: 'Feature Envy',
                description: 'OrderController accesses UserService data directly',
                severity: 'MEDIUM',
                location: 'src/controllers/OrderController.ts:78',
                recommendation: 'Create a facade or use domain events',
            },
            {
                pattern: 'Inappropriate Intimacy',
                description: 'PaymentService directly modifies Order entity',
                severity: 'MEDIUM',
                location: 'src/services/PaymentService.ts:123',
                recommendation: 'Use domain events for cross-aggregate communication',
            },
        ]);
    }
}
