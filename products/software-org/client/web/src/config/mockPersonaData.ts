/**
 * Mock Persona Data for Testing
 *
 * <p><b>Purpose</b><br>
 * Provides realistic mock data for testing persona-driven landing page components.
 * Includes user profiles, pending tasks, activities, and metrics for all 4 personas.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { mockUserProfiles, mockPendingTasks } from '@/config/mockPersonaData';
 *
 * const adminProfile = mockUserProfiles.admin;
 * const tasks = mockPendingTasks;
 * }</pre>
 *
 * @doc.type configuration
 * @doc.purpose Mock data for persona testing
 * @doc.layer product
 * @doc.pattern Test Data
 */

import type { UserProfile, Activity, PendingTasks, PersonaWorkItem } from '@/state/jotai/atoms';
import type { Feature } from '@/shared/components/FeatureGrid';
import type {
    Persona,
    GrowthGoal,
    PlannedAbsence,
    ExecutionContext,
    DevSecOpsPhaseId,
} from '@/shared/types/org';

// ============================================
// USER PROFILES (4 Personas)
// ============================================

export const mockUserProfiles: Record<string, UserProfile> = {
    admin: {
        userId: 'user-001',
        name: 'Alice Administrator',
        email: 'alice@ghatana.com',
        role: 'admin',
        permissions: [
            'manage_users',
            'manage_security',
            'view_audit_logs',
            'manage_compliance',
            'configure_system',
        ],
        preferences: {
            favoriteFeatures: ['audit-logs', 'user-management', 'security-dashboard'],
            quickActions: ['review-security', 'approve-hitl', 'check-compliance'],
            dashboardLayout: 'admin-default',
        },
    },
    lead: {
        userId: 'user-002',
        name: 'Bob Technical Lead',
        email: 'bob@ghatana.com',
        role: 'lead',
        permissions: [
            'manage_team',
            'approve_workflows',
            'view_team_metrics',
            'deploy_production',
            'manage_sprints',
        ],
        preferences: {
            favoriteFeatures: ['team-dashboard', 'workflow-approvals', 'sprint-planning'],
            quickActions: ['approve-hitl', 'review-workflows', 'check-team-metrics'],
            dashboardLayout: 'lead-default',
        },
    },
    engineer: {
        userId: 'user-003',
        name: 'Charlie Engineer',
        email: 'charlie@ghatana.com',
        role: 'engineer',
        permissions: [
            'create_workflows',
            'run_simulations',
            'deploy_staging',
            'view_logs',
            'manage_models',
        ],
        preferences: {
            favoriteFeatures: ['workflow-builder', 'simulation-runner', 'model-registry'],
            quickActions: ['create-workflow', 'run-simulation', 'check-logs'],
            dashboardLayout: 'engineer-default',
        },
    },
    viewer: {
        userId: 'user-004',
        name: 'Diana Analyst',
        email: 'diana@ghatana.com',
        role: 'viewer',
        permissions: ['view_dashboards', 'view_reports', 'export_data'],
        preferences: {
            favoriteFeatures: ['analytics-dashboard', 'reports', 'data-explorer'],
            quickActions: ['view-reports', 'explore-data', 'export-analytics'],
            dashboardLayout: 'viewer-default',
        },
    },
};

// ============================================
// PENDING TASKS
// ============================================

export const mockPendingTasks: PendingTasks = {
    hitlApprovals: 4,
    securityAlerts: 2,
    failedWorkflows: 1,
    modelAlerts: 3,
};

export const mockPendingTasksEmpty: PendingTasks = {
    hitlApprovals: 0,
    securityAlerts: 0,
    failedWorkflows: 0,
    modelAlerts: 0,
};

// ============================================
// RECENT ACTIVITIES
// ============================================

export const mockRecentActivities: Activity[] = [
    {
        id: 'activity-001',
        type: 'approval_completed',
        title: 'Workflow Approval Completed',
        description: 'Approved "Customer Onboarding Flow v2.3" for production deployment',
        timestamp: new Date(Date.now() - 2 * 60 * 60 * 1000), // 2 hours ago
        status: 'success',
        href: '/workflows/onboarding-v2.3',
    },
    {
        id: 'activity-002',
        type: 'simulation_run',
        title: 'Simulation Run Started',
        description: 'Running 1,000 test scenarios for payment processing workflow',
        timestamp: new Date(Date.now() - 4 * 60 * 60 * 1000), // 4 hours ago
        status: 'pending',
        href: '/simulations/payment-sim-2024-01',
    },
    {
        id: 'activity-003',
        type: 'workflow_created',
        title: 'New Workflow Created',
        description: 'Created "Fraud Detection Pipeline v1.0" with 8 operators',
        timestamp: new Date(Date.now() - 6 * 60 * 60 * 1000), // 6 hours ago
        status: 'success',
        href: '/workflows/fraud-detection-v1',
    },
    {
        id: 'activity-004',
        type: 'model_deployed',
        title: 'Model Deployed to Production',
        description: 'Deployed "Credit Risk Model v3.2" to production environment',
        timestamp: new Date(Date.now() - 24 * 60 * 60 * 1000), // 1 day ago
        status: 'success',
        href: '/models/credit-risk-v3.2',
    },
    {
        id: 'activity-005',
        type: 'report_generated',
        title: 'Weekly Report Generated',
        description: 'Generated compliance report for Q4 2024 with 42 workflows analyzed',
        timestamp: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000), // 2 days ago
        status: 'success',
        href: '/reports/compliance-q4-2024',
    },
    {
        id: 'activity-006',
        type: 'workflow_created',
        title: 'Workflow Creation Failed',
        description: 'Failed to create "Data Pipeline v2.0" due to invalid operator configuration',
        timestamp: new Date(Date.now() - 3 * 24 * 60 * 60 * 1000), // 3 days ago
        status: 'failed',
        href: '/workflows/data-pipeline-v2',
    },
];

// ============================================
// PINNED FEATURES
// ============================================

export const mockPinnedFeatures: Feature[] = [
    {
        icon: '🚀',
        title: 'Workflow Builder',
        description: 'Create and manage workflows with drag-and-drop interface',
        href: '/workflows/builder',
        color: 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-300',
        badge: 'Popular',
    },
    {
        icon: '📊',
        title: 'Analytics Dashboard',
        description: 'Real-time insights and metrics for all workflows',
        href: '/analytics',
        color: 'bg-green-100 dark:bg-green-900 text-green-600 dark:text-green-300',
    },
    {
        icon: '🤖',
        title: 'Model Registry',
        description: 'Manage and version AI/ML models',
        href: '/models',
        color: 'bg-purple-100 dark:bg-purple-900 text-purple-600 dark:text-purple-300',
        badge: 'New',
    },
    {
        icon: '🔍',
        title: 'Audit Logs',
        description: 'Track all system activities and changes',
        href: '/audit',
        color: 'bg-amber-100 dark:bg-amber-900 text-amber-600 dark:text-amber-300',
    },
];

// ============================================
// METRIC DATA
// ============================================

export const mockMetricData: Record<string, number> = {
    // Admin metrics
    securityScore: 94,
    complianceRate: 98.5,
    activeUsers: 142,
    auditEventsToday: 1847,

    // Lead metrics
    workflowsActive: 42,
    avgApprovalTime: 3600, // 1 hour in seconds
    hitlBacklog: 8,
    teamVelocity: 87,

    // Engineer metrics
    deploymentsToday: 12,
    simulationsPassed: 96.2,
    failedWorkflows: 3,
    avgTestCoverage: 89.5,

    // Viewer metrics
    reportsGenerated: 24,
    dashboardViews: 387,
    dataExports: 15,
    insightsDiscovered: 52,
};

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Returns mock user profile for given role.
 */
export function getMockUserProfile(role: 'admin' | 'lead' | 'engineer' | 'viewer'): UserProfile {
    return mockUserProfiles[role];
}

/**
 * Returns pending tasks with custom counts.
 */
export function getMockPendingTasks(
    hitl = 4,
    security = 2,
    workflows = 1,
    models = 3
): PendingTasks {
    return {
        hitlApprovals: hitl,
        securityAlerts: security,
        failedWorkflows: workflows,
        modelAlerts: models,
    };
}

/**
 * Returns N most recent activities.
 */
export function getMockRecentActivities(count = 5): Activity[] {
    return mockRecentActivities.slice(0, count);
}

/**
 * Returns N pinned features.
 */
export function getMockPinnedFeatures(count = 4): Feature[] {
    return mockPinnedFeatures.slice(0, count);
}

/**
 * Returns metric data for specific persona.
 */
export function getMockMetricData(role: 'admin' | 'lead' | 'engineer' | 'viewer'): Record<string, number> {
    const roleMetrics: Record<string, string[]> = {
        admin: ['securityScore', 'complianceRate', 'activeUsers', 'auditEventsToday'],
        lead: ['workflowsActive', 'avgApprovalTime', 'hitlBacklog', 'teamVelocity'],
        engineer: ['deploymentsToday', 'simulationsPassed', 'failedWorkflows', 'avgTestCoverage'],
        viewer: ['reportsGenerated', 'dashboardViews', 'dataExports', 'insightsDiscovered'],
    };

    const metrics = roleMetrics[role];
    const data: Record<string, number> = {};
    metrics.forEach((key) => {
        data[key] = mockMetricData[key];
    });

    return data;
}

// ============================================
// UNIFIED PERSONA MOCK DATA (Human/Agent Agnostic)
// ============================================

/**
 * Mock persona work items across DevSecOps phases
 */
export const mockPersonaWorkItems: PersonaWorkItem[] = [
    {
        id: 'WI-1001',
        title: 'Implement user authentication API',
        description: 'Build secure OAuth 2.0 authentication flow with JWT tokens',
        status: 'in-progress',
        priority: 'p1',
        phase: 'build',
        dueDate: '2025-12-05',
        estimatedHours: 16,
        loggedHours: 8,
        tags: ['security', 'api', 'authentication'],
    },
    {
        id: 'WI-1002',
        title: 'Code review for payment service',
        description: 'Review PR #456 for circuit breaker implementation',
        status: 'ready',
        priority: 'p0',
        phase: 'review',
        dueDate: '2025-12-02',
        estimatedHours: 2,
        loggedHours: 0,
        tags: ['review', 'payments'],
    },
    {
        id: 'WI-1003',
        title: 'Fix database connection pooling',
        description: 'Resolve connection exhaustion under high load',
        status: 'in-progress',
        priority: 'p0',
        phase: 'verify',
        dueDate: '2025-12-01',
        estimatedHours: 8,
        loggedHours: 6,
        tags: ['bug', 'database', 'performance'],
    },
    {
        id: 'WI-1004',
        title: 'Deploy notification service to staging',
        description: 'Validate real-time notifications in staging environment',
        status: 'ready',
        priority: 'p2',
        phase: 'staging',
        dueDate: '2025-12-03',
        estimatedHours: 4,
        loggedHours: 0,
        tags: ['deployment', 'notifications'],
    },
    {
        id: 'WI-1005',
        title: 'Monitor production metrics post-deploy',
        description: 'Track error rates and latency after rate limiting release',
        status: 'in-progress',
        priority: 'p1',
        phase: 'operate',
        dueDate: '2025-12-04',
        estimatedHours: 2,
        loggedHours: 1,
        tags: ['monitoring', 'observability'],
    },
    {
        id: 'WI-1006',
        title: 'Plan Q1 feature roadmap',
        description: 'Define technical approach for upcoming features',
        status: 'backlog',
        priority: 'p2',
        phase: 'plan',
        dueDate: '2025-12-10',
        estimatedHours: 8,
        loggedHours: 0,
        tags: ['planning', 'roadmap'],
    },
    {
        id: 'WI-1007',
        title: 'Conduct sprint retrospective',
        description: 'Document learnings and action items from sprint',
        status: 'ready',
        priority: 'p3',
        phase: 'learn',
        dueDate: '2025-12-06',
        estimatedHours: 2,
        loggedHours: 0,
        tags: ['retrospective', 'process'],
    },
];

/**
 * Mock growth goals for personas
 */
export const mockGrowthGoals: GrowthGoal[] = [
    {
        id: 'goal-001',
        title: 'Master Kubernetes orchestration',
        description: 'Complete CKA certification and deploy 3 production services',
        targetDate: '2025-03-31',
        progress: 65,
        status: 'in-progress',
        relatedCapabilities: ['kubernetes', 'devops', 'cloud'],
        milestones: [
            { id: 'ms-001', title: 'Complete K8s fundamentals course', completed: true, completedAt: '2025-10-15' },
            { id: 'ms-002', title: 'Deploy first service to K8s', completed: true, completedAt: '2025-11-01' },
            { id: 'ms-003', title: 'Pass CKA exam', completed: false },
            { id: 'ms-004', title: 'Deploy 3 production services', completed: false },
        ],
    },
    {
        id: 'goal-002',
        title: 'Improve code review efficiency',
        description: 'Reduce average review time to under 4 hours while maintaining quality',
        targetDate: '2025-01-31',
        progress: 40,
        status: 'in-progress',
        relatedCapabilities: ['code-review', 'mentoring', 'communication'],
        milestones: [
            { id: 'ms-005', title: 'Review 50 PRs', completed: true, completedAt: '2025-11-20' },
            { id: 'ms-006', title: 'Achieve < 6hr avg review time', completed: false },
            { id: 'ms-007', title: 'Achieve < 4hr avg review time', completed: false },
        ],
    },
    {
        id: 'goal-003',
        title: 'Learn GraphQL and Apollo',
        description: 'Build proficiency in GraphQL for API development',
        targetDate: '2025-02-28',
        progress: 20,
        status: 'in-progress',
        relatedCapabilities: ['graphql', 'api-design', 'typescript'],
        milestones: [
            { id: 'ms-008', title: 'Complete GraphQL course', completed: true, completedAt: '2025-11-25' },
            { id: 'ms-009', title: 'Build sample GraphQL API', completed: false },
            { id: 'ms-010', title: 'Implement in production service', completed: false },
        ],
    },
];

/**
 * Mock planned absences (PTO for humans, maintenance for agents)
 */
export const mockPlannedAbsences: PlannedAbsence[] = [
    {
        id: 'absence-001',
        startDate: '2025-12-23',
        endDate: '2025-12-27',
        reason: 'Holiday break',
        type: 'holiday',
        status: 'approved',
        approverId: 'user-002',
    },
    {
        id: 'absence-002',
        startDate: '2025-01-15',
        endDate: '2025-01-17',
        reason: 'Personal time off',
        type: 'pto',
        status: 'pending',
    },
];

/**
 * Returns mock unified persona entity
 */
export function getMockPersona(role: 'admin' | 'lead' | 'engineer' | 'viewer' | 'sre' | 'security'): Persona {
    const profile = mockUserProfiles[role] || mockUserProfiles.engineer;
    
    return {
        id: profile.userId,
        name: profile.name,
        avatar: `https://api.dicebear.com/7.x/avataaars/svg?seed=${profile.name}`,
        role: role as Persona['role'],
        executionMode: 'human',
        description: `${role.charAt(0).toUpperCase() + role.slice(1)} persona`,
        departmentId: 'dept-engineering',
        capacity: {
            type: 'hours',
            available: 80,
            allocated: 65,
            dailyLimit: 8,
            weeklyLimit: 40,
            currentUtilization: 81,
            reservedForUrgent: 10,
        },
        availability: {
            status: 'available',
            statusMessage: 'Working on sprint tasks',
            schedule: [
                { dayOfWeek: 1, startTime: '09:00', endTime: '17:00', timezone: 'America/Los_Angeles' },
                { dayOfWeek: 2, startTime: '09:00', endTime: '17:00', timezone: 'America/Los_Angeles' },
                { dayOfWeek: 3, startTime: '09:00', endTime: '17:00', timezone: 'America/Los_Angeles' },
                { dayOfWeek: 4, startTime: '09:00', endTime: '17:00', timezone: 'America/Los_Angeles' },
                { dayOfWeek: 5, startTime: '09:00', endTime: '17:00', timezone: 'America/Los_Angeles' },
            ],
            plannedAbsences: mockPlannedAbsences,
            nextAvailable: null,
            lastActive: new Date().toISOString(),
        },
        growth: {
            level: 'Senior Engineer',
            progressToNext: 45,
            activeGoals: mockGrowthGoals.filter(g => g.status === 'in-progress'),
            completedMilestones: mockGrowthGoals.flatMap(g => g.milestones.filter(m => m.completed)),
            capabilities: [
                { id: 'cap-001', name: 'TypeScript', category: 'technical', proficiency: 90 },
                { id: 'cap-002', name: 'React', category: 'technical', proficiency: 85 },
                { id: 'cap-003', name: 'Node.js', category: 'technical', proficiency: 80 },
                { id: 'cap-004', name: 'Kubernetes', category: 'devops', proficiency: 65 },
                { id: 'cap-005', name: 'System Design', category: 'architecture', proficiency: 75 },
            ],
            experiencePoints: 4500,
        },
        tools: [
            { toolId: 'canvas', name: 'Canvas', enabled: true },
            { toolId: 'terminal', name: 'Terminal', enabled: true },
            { toolId: 'editor', name: 'Code Editor', enabled: true },
            { toolId: 'vcs', name: 'Version Control', enabled: true },
            { toolId: 'ci', name: 'CI/CD', enabled: true },
            { toolId: 'observability', name: 'Observability', enabled: true },
            { toolId: 'ai', name: 'AI Assistant', enabled: true },
        ],
        permissions: profile.permissions,
        metadata: {
            createdAt: '2024-01-15T10:00:00Z',
            updatedAt: new Date().toISOString(),
            tags: ['engineering', 'platform'],
        },
    };
}

/**
 * Returns mock persona work items
 */
export function getMockPersonaWorkItems(phase?: DevSecOpsPhaseId): PersonaWorkItem[] {
    if (phase) {
        return mockPersonaWorkItems.filter(item => item.phase === phase);
    }
    return mockPersonaWorkItems;
}

/**
 * Returns mock growth goals
 */
export function getMockGrowthGoals(): GrowthGoal[] {
    return mockGrowthGoals;
}

/**
 * Returns mock planned absences
 */
export function getMockPlannedAbsences(): PlannedAbsence[] {
    return mockPlannedAbsences;
}

/**
 * Returns mock execution context for a work item
 */
export function getMockExecutionContext(workItemId: string): ExecutionContext {
    const workItem = mockPersonaWorkItems.find(item => item.id === workItemId);
    const phase = workItem?.phase || 'build';
    
    return {
        workItemId,
        currentPhase: phase,
        currentStepId: `engineer-${phase}`,
        canvas: {
            enabled: ['plan', 'build'].includes(phase),
            template: phase === 'plan' ? 'architecture-diagram' : undefined,
            artifacts: [],
        },
        terminal: {
            enabled: ['build', 'verify', 'deploy'].includes(phase),
            allowedCommands: ['npm', 'pnpm', 'git', 'kubectl', 'docker'],
            workingDirectory: '/workspace/project',
        },
        editor: {
            enabled: ['build', 'verify', 'review'].includes(phase),
            files: [
                { path: 'src/index.ts', language: 'typescript' },
                { path: 'src/api/routes.ts', language: 'typescript' },
            ],
        },
        vcs: {
            repoUrl: 'https://github.com/org/repo',
            branch: `feature/${workItemId.toLowerCase()}`,
            pullRequests: [
                {
                    id: 'pr-123',
                    title: `feat: ${workItem?.title || 'Implementation'}`,
                    url: 'https://github.com/org/repo/pull/123',
                    status: 'open',
                    reviewStatus: 'pending',
                },
            ],
            recentCommits: [
                {
                    sha: 'abc123',
                    message: 'Initial implementation',
                    author: 'Charlie Engineer',
                    timestamp: new Date(Date.now() - 3600000).toISOString(),
                },
            ],
        },
        ci: {
            pipelineId: 'pipe-456',
            name: 'CI Pipeline',
            status: 'passed',
            url: 'https://ci.example.com/pipelines/456',
            stages: [
                { id: 'stage-1', name: 'Build', status: 'passed', duration: 120 },
                { id: 'stage-2', name: 'Test', status: 'passed', duration: 300 },
                { id: 'stage-3', name: 'Lint', status: 'passed', duration: 60 },
            ],
        },
        observability: {
            dashboardUrl: 'https://grafana.example.com/d/service',
            metrics: [
                { name: 'Error Rate', value: 0.1, unit: '%', trend: 'down' },
                { name: 'P95 Latency', value: 45, unit: 'ms', trend: 'stable' },
                { name: 'Requests/sec', value: 1250, unit: 'req/s', trend: 'up' },
            ],
            alerts: [],
            logsUrl: 'https://logs.example.com/query',
        },
        security: {
            vulnerabilities: { critical: 0, high: 1, medium: 3, low: 5 },
            complianceStatus: 'compliant',
            scanResultsUrl: 'https://security.example.com/scan/123',
            lastScanAt: new Date(Date.now() - 86400000).toISOString(),
        },
        aiAssistant: {
            enabled: true,
            context: `Working on: ${workItem?.title || 'Work item'}`,
            suggestedActions: [
                {
                    id: 'suggest-1',
                    action: 'Add unit tests',
                    description: 'Consider adding tests for edge cases',
                    confidence: 0.85,
                },
                {
                    id: 'suggest-2',
                    action: 'Update documentation',
                    description: 'API changes should be documented',
                    confidence: 0.72,
                },
            ],
        },
        availableActions: [
            {
                id: 'action-move-review',
                label: 'Move to Review',
                type: 'status-change',
                icon: '👀',
                primary: true,
                enabled: phase === 'build' || phase === 'verify',
                targetStatus: 'in-review',
            },
            {
                id: 'action-add-comment',
                label: 'Add Comment',
                type: 'comment',
                icon: '💬',
                enabled: true,
            },
            {
                id: 'action-link-pr',
                label: 'Link PR',
                type: 'link',
                icon: '🔗',
                enabled: true,
            },
        ],
        nextStepId: getNextStepId(phase),
    };
}

/**
 * Helper to get next step ID based on current phase
 */
function getNextStepId(phase: DevSecOpsPhaseId): string | undefined {
    const phaseOrder: DevSecOpsPhaseId[] = [
        'intake', 'plan', 'build', 'verify', 'review', 'staging', 'deploy', 'operate', 'learn'
    ];
    const currentIndex = phaseOrder.indexOf(phase);
    if (currentIndex >= 0 && currentIndex < phaseOrder.length - 1) {
        return `engineer-${phaseOrder[currentIndex + 1]}`;
    }
    return undefined;
}
