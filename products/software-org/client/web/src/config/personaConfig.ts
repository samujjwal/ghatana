/**
 * Persona Configuration for Role-Based Landing Page
 *
 * <p><b>Purpose</b><br>
 * Defines quick actions, metrics, and features for each user persona:
 * - Admin: Security, compliance, user management
 * - Lead: Team KPIs, approvals, workflow oversight
 * - Engineer: Workflow creation, testing, deployments
 * - Viewer: Dashboards, reports, read-only access
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { getPersonaConfig } from '@/config/personaConfig';
 *
 * const config = getPersonaConfig('engineer');
 * const quickActions = config.quickActions;
 * }</pre>
 *
 * @doc.type configuration
 * @doc.purpose Persona-based configuration for landing page
 * @doc.layer product
 * @doc.pattern Configuration
 */

export type UserRole = 'admin' | 'lead' | 'engineer' | 'viewer';

export interface QuickAction {
    id: string;
    title: string;
    description: string;
    icon: string;
    href: string;
    variant: 'primary' | 'secondary' | 'warning' | 'success';
    badgeKey?: string; // Dynamic badge count key (e.g., 'pendingApprovals')
    permissions?: string[];
    shortcut?: string; // Keyboard shortcut (e.g., 'Ctrl+A')
}

export interface MetricDefinition {
    id: string;
    title: string;
    icon: string;
    color: string;
    dataKey: string; // API endpoint or state key
    format: 'number' | 'percentage' | 'duration';
    threshold?: {
        warning: number;
        critical: number;
    };
}

export interface FeatureConfig {
    id: string;
    title: string;
    description: string;
    icon: string;
    href: string;
    color: string;
    badge?: string;
    priority: number; // 1=highest
    permissions?: string[];
}

export interface PersonaConfig {
    role: UserRole;
    displayName: string;
    tagline: string;
    welcomeMessage: string;
    quickActions: QuickAction[];
    metrics: MetricDefinition[];
    features: FeatureConfig[];
    contextualTips: string[];
    permissions: string[];
}

/**
 * Admin persona configuration
 * Focus: Security, compliance, user management, system configuration
 */
const adminConfig: PersonaConfig = {
    role: 'admin',
    displayName: 'Platform Administrator',
    tagline: 'Manage security, compliance, and system configuration',
    welcomeMessage: 'Monitor system health, review security alerts, and manage user access.',
    quickActions: [
        {
            id: 'review-approvals',
            title: 'Review Approvals',
            description: 'Pending HITL actions requiring admin approval',
            icon: '✋',
            href: '/hitl?filter=pending&priority=high',
            variant: 'warning',
            badgeKey: 'hitlApprovals',
            permissions: ['hitl:approve'],
            shortcut: 'Ctrl+H',
        },
        {
            id: 'security-alerts',
            title: 'Security Alerts',
            description: 'Active security incidents and compliance issues',
            icon: '🔒',
            href: '/security?tab=alerts',
            variant: 'warning',
            badgeKey: 'securityAlerts',
            permissions: ['security:read'],
            shortcut: 'Ctrl+S',
        },
        {
            id: 'audit-logs',
            title: 'Audit Logs',
            description: 'Review system activity and compliance records',
            icon: '📋',
            href: '/audit?timeRange=24h',
            variant: 'secondary',
            permissions: ['audit:read'],
            shortcut: 'Ctrl+L',
        },
        {
            id: 'manage-users',
            title: 'Manage Users',
            description: 'Add, remove, or update user access and roles',
            icon: '👥',
            href: '/settings?tab=users',
            variant: 'secondary',
            permissions: ['users:manage'],
        },
        {
            id: 'compliance-report',
            title: 'Compliance Report',
            description: 'Generate audit reports for compliance review',
            icon: '📊',
            href: '/reports?type=compliance',
            variant: 'primary',
            permissions: ['reports:generate'],
        },
        {
            id: 'system-settings',
            title: 'System Settings',
            description: 'Configure integrations and platform settings',
            icon: '⚙️',
            href: '/settings',
            variant: 'secondary',
            permissions: ['settings:manage'],
        },
        {
            id: 'devsecops-security-posture',
            title: 'DevSecOps Security',
            description: 'View security posture and compliance across services',
            icon: '🛡️',
            href: '/security',
            variant: 'primary',
            permissions: ['security:read'],
        },
    ],
    metrics: [
        {
            id: 'security-score',
            title: 'Security Score',
            icon: '🛡️',
            color: 'green',
            dataKey: 'securityScore',
            format: 'percentage',
            threshold: { warning: 85, critical: 70 },
        },
        {
            id: 'compliance-status',
            title: 'Compliance Status',
            icon: '✓',
            color: 'blue',
            dataKey: 'complianceStatus',
            format: 'percentage',
            threshold: { warning: 95, critical: 90 },
        },
        {
            id: 'uptime',
            title: 'System Uptime',
            icon: '⏱️',
            color: 'emerald',
            dataKey: 'uptime',
            format: 'percentage',
            threshold: { warning: 99.5, critical: 99.0 },
        },
        {
            id: 'active-users',
            title: 'Active Users',
            icon: '👤',
            color: 'purple',
            dataKey: 'activeUsers',
            format: 'number',
        },
    ],
    features: [],
    contextualTips: [
        'Use the Audit Trail to track all sensitive operations and maintain compliance.',
        'Set up automated security alerts to stay informed about potential threats.',
        'Regularly review user access to ensure proper role assignments.',
    ],
    permissions: ['*'], // Admin has all permissions
};

/**
 * Lead persona configuration
 * Focus: Team oversight, workflow management, model health, approvals
 */
const leadConfig: PersonaConfig = {
    role: 'lead',
    displayName: 'Technical Lead',
    tagline: 'Oversee team productivity and project delivery',
    welcomeMessage: 'Monitor team KPIs, approve changes, and track workflow health.',
    quickActions: [
        {
            id: 'team-kpis',
            title: 'Team KPIs',
            description: 'View team performance metrics and velocity',
            icon: '📊',
            href: '/dashboard?view=team',
            variant: 'primary',
            shortcut: 'Ctrl+K',
        },
        {
            id: 'workflow-status',
            title: 'Workflow Status',
            description: 'Monitor active workflows and execution health',
            icon: '🔄',
            href: '/workflows?status=active',
            variant: 'secondary',
            shortcut: 'Ctrl+W',
        },
        {
            id: 'approve-changes',
            title: 'Approve Changes',
            description: 'Review and approve workflow/deployment changes',
            icon: '✋',
            href: '/hitl?assignee=me',
            variant: 'warning',
            badgeKey: 'hitlApprovals',
            shortcut: 'Ctrl+A',
        },
        {
            id: 'model-health',
            title: 'Model Health',
            description: 'Check ML model accuracy and performance',
            icon: '🧠',
            href: '/ml-observatory?view=health',
            variant: 'secondary',
            badgeKey: 'modelAlerts',
            shortcut: 'Ctrl+M',
        },
        {
            id: 'department-overview',
            title: 'Department Overview',
            description: 'View organization structure and team metrics',
            icon: '🏢',
            href: '/departments',
            variant: 'secondary',
        },
        {
            id: 'team-reports',
            title: 'Team Reports',
            description: 'Generate performance and delivery reports',
            icon: '📈',
            href: '/reports?type=team',
            variant: 'primary',
        },
        {
            id: 'devsecops-portfolio',
            title: 'DevSecOps Portfolio',
            description: 'View portfolio of work items by DevSecOps phase',
            icon: '📊',
            href: '/devsecops/board?persona=lead',
            variant: 'primary',
        },
    ],
    metrics: [
        {
            id: 'team-velocity',
            title: 'Team Velocity',
            icon: '⚡',
            color: 'blue',
            dataKey: 'teamVelocity',
            format: 'number',
        },
        {
            id: 'workflow-health',
            title: 'Workflow Health',
            icon: '🔄',
            color: 'green',
            dataKey: 'workflowSuccessRate',
            format: 'percentage',
            threshold: { warning: 95, critical: 90 },
        },
        {
            id: 'model-accuracy',
            title: 'Model Accuracy',
            icon: '🎯',
            color: 'purple',
            dataKey: 'avgModelAccuracy',
            format: 'percentage',
            threshold: { warning: 90, critical: 85 },
        },
        {
            id: 'pending-approvals',
            title: 'Pending Approvals',
            icon: '✋',
            color: 'amber',
            dataKey: 'pendingApprovals',
            format: 'number',
        },
    ],
    features: [],
    contextualTips: [
        'Review pending approvals daily to keep workflows moving smoothly.',
        'Monitor workflow health to identify bottlenecks and optimization opportunities.',
        'Check model performance regularly to ensure ML systems are delivering value.',
    ],
    permissions: [
        'workflows:read',
        'workflows:approve',
        'departments:read',
        'reports:read',
        'ml:read',
        'hitl:approve',
    ],
};

/**
 * Engineer persona configuration
 * Focus: Building, testing, deploying workflows and ML models
 */
const engineerConfig: PersonaConfig = {
    role: 'engineer',
    displayName: 'Software Engineer',
    tagline: 'Build, test, and deploy workflows and ML models',
    welcomeMessage: 'Create workflows, run simulations, and deploy models with confidence.',
    quickActions: [
        {
            id: 'my-stories',
            title: 'My Stories',
            description: 'View your assigned work items and stories',
            icon: '📋',
            href: '/persona-dashboard#my-stories',
            variant: 'primary',
            badgeKey: 'activeStories',
            shortcut: 'Ctrl+S',
        },
        {
            id: 'create-workflow',
            title: 'Create Workflow',
            description: 'Build a new automation workflow',
            icon: '🔄',
            href: '/workflows/create',
            variant: 'secondary',
            permissions: ['workflows:create'],
            shortcut: 'Ctrl+N',
        },
        {
            id: 'run-simulation',
            title: 'Run Simulation',
            description: 'Test event scenarios and validate pipelines',
            icon: '⚡',
            href: '/simulator',
            variant: 'secondary',
            shortcut: 'Ctrl+T',
        },
        {
            id: 'monitor-events',
            title: 'Monitor Events',
            description: 'View real-time events and debug issues',
            icon: '⏱️',
            href: '/realtime-monitor',
            variant: 'secondary',
            shortcut: 'Ctrl+E',
        },
        {
            id: 'deploy-model',
            title: 'Deploy Model',
            description: 'Deploy ML model from catalog',
            icon: '🎓',
            href: '/models?action=deploy',
            variant: 'secondary',
            permissions: ['ml:deploy'],
            shortcut: 'Ctrl+D',
        },
        {
            id: 'view-executions',
            title: 'Recent Executions',
            description: 'View workflow execution history and logs',
            icon: '📜',
            href: '/automation?view=history',
            variant: 'secondary',
        },
        {
            id: 'debug-failures',
            title: 'Debug Failures',
            description: 'Troubleshoot failed workflows',
            icon: '🐛',
            href: '/workflows?status=failed',
            variant: 'warning',
            badgeKey: 'failedWorkflows',
            shortcut: 'Ctrl+F',
        },
        {
            id: 'devsecops-board',
            title: 'DevSecOps Board',
            description: 'View your stories organized by DevSecOps phase',
            icon: '📊',
            href: '/devsecops/board?persona=engineer',
            variant: 'secondary',
        },
    ],
    metrics: [
        {
            id: 'active-stories',
            title: 'Active Stories',
            icon: '📋',
            color: 'blue',
            dataKey: 'activeStories',
            format: 'number',
        },
        {
            id: 'active-workflows',
            title: 'Active Workflows',
            icon: '🔄',
            color: 'cyan',
            dataKey: 'activeWorkflows',
            format: 'number',
        },
        {
            id: 'test-pass-rate',
            title: 'Test Pass Rate',
            icon: '✓',
            color: 'green',
            dataKey: 'testPassRate',
            format: 'percentage',
            threshold: { warning: 95, critical: 90 },
        },
        {
            id: 'deployments-today',
            title: 'Deployments Today',
            icon: '🚀',
            color: 'purple',
            dataKey: 'deploymentsToday',
            format: 'number',
        },
    ],
    features: [],
    contextualTips: [
        'Use the Event Simulator to test workflows before deploying to production.',
        'Monitor real-time events to debug issues and validate expected behavior.',
        'Check your active stories daily to stay on top of deliverables.',
        'Use implementation plans to document your approach before coding.',
    ],
    permissions: [
        'workflows:create',
        'workflows:edit',
        'workflows:delete',
        'simulator:run',
        'ml:deploy',
        'events:read',
        'automation:read',
    ],
};

/**
 * Viewer persona configuration
 * Focus: Read-only access to dashboards, reports, and metrics
 */
const viewerConfig: PersonaConfig = {
    role: 'viewer',
    displayName: 'Analyst / Observer',
    tagline: 'View reports, dashboards, and system status',
    welcomeMessage: 'Access dashboards, generate reports, and monitor system metrics.',
    quickActions: [
        {
            id: 'view-dashboard',
            title: 'View Dashboard',
            description: 'Control tower with KPIs and AI insights',
            icon: '📊',
            href: '/dashboard',
            variant: 'primary',
            shortcut: 'Ctrl+D',
        },
        {
            id: 'generate-report',
            title: 'Generate Report',
            description: 'Create analytics or compliance report',
            icon: '📈',
            href: '/reports',
            variant: 'primary',
            shortcut: 'Ctrl+R',
        },
        {
            id: 'export-data',
            title: 'Export Data',
            description: 'Export metrics and data for analysis',
            icon: '📥',
            href: '/export',
            variant: 'secondary',
            shortcut: 'Ctrl+X',
        },
        {
            id: 'check-alerts',
            title: 'Check Alerts',
            description: 'View system alerts and notifications',
            icon: '🔔',
            href: '/realtime-monitor?view=alerts',
            variant: 'secondary',
        },
        {
            id: 'view-organization',
            title: 'Organization Overview',
            description: 'View departments and team structure',
            icon: '🏢',
            href: '/departments',
            variant: 'secondary',
        },
        {
            id: 'help-center',
            title: 'Help Center',
            description: 'Access documentation and tutorials',
            icon: '❓',
            href: '/help',
            variant: 'secondary',
        },
    ],
    metrics: [
        {
            id: 'report-count',
            title: 'Reports Generated',
            icon: '📊',
            color: 'blue',
            dataKey: 'reportCount',
            format: 'number',
        },
        {
            id: 'data-freshness',
            title: 'Data Freshness',
            icon: '🔄',
            color: 'green',
            dataKey: 'dataFreshness',
            format: 'duration',
        },
        {
            id: 'alert-status',
            title: 'Active Alerts',
            icon: '🔔',
            color: 'amber',
            dataKey: 'activeAlerts',
            format: 'number',
        },
        {
            id: 'dashboards',
            title: 'Available Dashboards',
            icon: '📈',
            color: 'purple',
            dataKey: 'dashboardCount',
            format: 'number',
        },
    ],
    features: [],
    contextualTips: [
        'Use the Control Tower to get a high-level view of all key metrics.',
        'Export data regularly to create custom analysis in your preferred tools.',
        'Subscribe to alerts to stay informed about important system changes.',
    ],
    permissions: [
        'dashboard:read',
        'reports:read',
        'departments:read',
        'export:data',
        'realtime:read',
        'audit:read',
    ],
};

/**
 * Persona configuration registry
 */
export const PERSONA_CONFIGS: Record<UserRole, PersonaConfig> = {
    admin: adminConfig,
    lead: leadConfig,
    engineer: engineerConfig,
    viewer: viewerConfig,
};

/**
 * Get persona configuration by role
 *
 * @param role User role
 * @returns PersonaConfig or null if role not found
 */
export function getPersonaConfig(role: UserRole | string): PersonaConfig | null {
    return PERSONA_CONFIGS[role as UserRole] || null;
}

/**
 * Get quick actions for a persona
 *
 * @param role User role
 * @returns Array of quick actions
 */
export function getQuickActions(role: UserRole | string): QuickAction[] {
    const config = getPersonaConfig(role);
    return config?.quickActions || [];
}

/**
 * Get metrics for a persona
 *
 * @param role User role
 * @returns Array of metric definitions
 */
export function getMetrics(role: UserRole | string): MetricDefinition[] {
    const config = getPersonaConfig(role);
    return config?.metrics || [];
}

/**
 * Get contextual tip for a persona
 *
 * @param role User role
 * @param index Tip index (random if not provided)
 * @returns Contextual tip string
 */
export function getContextualTip(role: UserRole | string, index?: number): string {
    const config = getPersonaConfig(role);
    if (!config || config.contextualTips.length === 0) {
        return 'Use the Help Center for documentation and tutorials.';
    }

    const tipIndex = index !== undefined
        ? index % config.contextualTips.length
        : Math.floor(Math.random() * config.contextualTips.length);

    return config.contextualTips[tipIndex];
}

/**
 * Check if user has permission
 *
 * @param role User role
 * @param permission Permission string
 * @returns True if user has permission
 */
export function hasPermission(role: UserRole | string, permission: string): boolean {
    const config = getPersonaConfig(role);
    if (!config) return false;

    // Admin has all permissions
    if (config.permissions.includes('*')) return true;

    return config.permissions.includes(permission);
}
