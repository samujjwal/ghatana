import type {
    ActivityLog,
    DevSecOpsOverview,
    Item,
    ItemStatus,
    KPI,
    Milestone,
    PersonaDashboardSummary,
    Phase,
    Priority,
    User,
} from './index';

// Core phase fixtures
export const devsecopsPhases: Phase[] = [
    {
        id: 'ideation',
        key: 'ideation',
        title: 'Ideation',
        description: 'Brainstorming and concept development phase',
        order: 1,
        color: '#8b5cf6',
        icon: '💡',
    },
    {
        id: 'planning',
        key: 'planning',
        title: 'Planning',
        description: 'Detailed planning and architecture design',
        order: 2,
        color: '#3b82f6',
        icon: '📋',
    },
    {
        id: 'development',
        key: 'development',
        title: 'Development',
        description: 'Implementation and coding phase',
        order: 3,
        color: '#10b981',
        icon: '💻',
    },
    {
        id: 'security',
        key: 'security',
        title: 'Security',
        description: 'Security assessment and vulnerability testing',
        order: 4,
        color: '#ef4444',
        icon: '🔒',
    },
    {
        id: 'testing',
        key: 'testing',
        title: 'Testing',
        description: 'Quality assurance and testing phase',
        order: 5,
        color: '#f59e0b',
        icon: '🧪',
    },
    {
        id: 'deployment',
        key: 'deployment',
        title: 'Deployment',
        description: 'Production deployment and release',
        order: 6,
        color: '#06b6d4',
        icon: '🚀',
    },
    {
        id: 'operations',
        key: 'operations',
        title: 'Operations',
        description: 'Monitoring and maintenance phase',
        order: 7,
        color: '#6366f1',
        icon: '⚙️',
    },
];

// Milestone fixtures (canonical statuses)
export const devsecopsMilestones: Milestone[] = [
    {
        id: 'milestone-1',
        title: 'MVP Release',
        description: 'First release with core features',
        dueDate: '2025-03-01T00:00:00Z',
        status: 'pending',
    },
    {
        id: 'milestone-2',
        title: 'Security Audit',
        description: 'Comprehensive security testing and remediation',
        dueDate: '2025-04-15T00:00:00Z',
        status: 'pending',
    },
    {
        id: 'milestone-3',
        title: 'Performance Optimization',
        description: 'Scale testing and performance improvements',
        dueDate: '2025-05-30T00:00:00Z',
        status: 'pending',
    },
];

const devsecopsUsers: User[] = [
    {
        id: 'user-1',
        name: 'Alex Johnson',
        email: 'alex.johnson@example.com',
        role: 'Executive',
        teams: ['leadership'],
    },
    {
        id: 'user-2',
        name: 'Morgan Lee',
        email: 'morgan.lee@example.com',
        role: 'Developer',
        teams: ['platform'],
    },
    {
        id: 'user-3',
        name: 'Jamie Patel',
        email: 'jamie.patel@example.com',
        role: 'DevOps',
        teams: ['sre'],
    },
    {
        id: 'user-4',
        name: 'Taylor Swift',
        email: 'taylor.swift@example.com',
        role: 'Security',
        teams: ['security'],
    },
];

// Simple item generator used by fixtures
export function generateDevSecOpsItems(count: number = 40): Item[] {
    const statuses: ItemStatus[] = ['not-started', 'in-progress', 'blocked', 'completed'];
    const priorities: Priority[] = ['low', 'medium', 'high', 'critical'];

    const titles = [
        'Implement user authentication',
        'Design system architecture',
        'Setup CI/CD pipeline',
        'Write unit tests',
        'Configure database schema',
        'Add error handling middleware',
        'Optimize API performance',
        'Update documentation',
        'Fix security vulnerabilities',
        'Refactor legacy code',
        'Implement caching layer',
        'Add monitoring dashboards',
        'Setup logging infrastructure',
        'Implement rate limiting',
        'Add API versioning',
        'Create deployment scripts',
        'Setup backup procedures',
        'Implement data migration',
        'Add feature flags',
        'Create admin panel',
    ];

    const tags = [
        'backend',
        'frontend',
        'security',
        'performance',
        'bug',
        'feature',
        'enhancement',
        'documentation',
        'testing',
        'infrastructure',
    ];

    return Array.from({ length: count }, (_, i) => {
        const status = statuses[i % statuses.length];
        const priority = priorities[i % priorities.length];
        const phase = devsecopsPhases[i % devsecopsPhases.length];
        const owner = devsecopsUsers[i % devsecopsUsers.length];

        const createdDate = new Date(2025, 0, (i % 30) + 1);
        const updatedDate = new Date(2025, 0, (i % 30) + 1 + Math.floor(i / 10));

        return {
            id: `item-${i + 1}`,
            title: `${titles[i % titles.length]} ${Math.floor(i / titles.length) + 1}`,
            description:
                `This is a detailed description for ${titles[i % titles.length].toLowerCase()
                }. It includes requirements, implementation notes, and acceptance criteria.`,
            type: 'task',
            status,
            priority,
            phaseId: phase.id,
            workflowId: i % 5 === 0 ? `workflow-${i}` : undefined,
            owners: [owner],
            tags: [
                tags[i % tags.length],
                tags[(i + 1) % tags.length],
                tags[(i + 2) % tags.length],
            ].slice(0, 2 + (i % 2)),
            createdAt: createdDate.toISOString(),
            updatedAt: updatedDate.toISOString(),
            estimatedHours: (i % 40) + 8,
            actualHours: status === 'completed' ? (i % 35) + 5 : undefined,
            dueDate: new Date(2025, 0, (i % 30) + 15).toISOString(),
            completedAt:
                status === 'completed'
                    ? new Date(2025, 0, (i % 30) + 10).toISOString()
                    : undefined,
            progress: status === 'completed' ? 100 : status === 'in-progress' ? 50 : 0,
            artifacts: [],
            integrations: [],
            metadata: {},
        } satisfies Item;
    });
}

export const devsecopsItems: Item[] = generateDevSecOpsItems(40);

// KPI fixtures (adapted from mock backend)
export const devsecopsKpis: KPI[] = [
    {
        id: 'kpi-1',
        name: 'System Uptime',
        category: 'operations',
        value: 99.8,
        unit: '%',
        target: 99.9,
        trend: { direction: 'up', percentage: 0.2 },
    },
    {
        id: 'kpi-2',
        name: 'Deployment Frequency',
        category: 'velocity',
        value: 24,
        unit: ' per week',
        trend: { direction: 'up', percentage: 12.5 },
    },
    {
        id: 'kpi-3',
        name: 'Mean Time to Recovery',
        category: 'operations',
        value: 45,
        unit: ' min',
        trend: { direction: 'down', percentage: -8.3 },
    },
    {
        id: 'kpi-4',
        name: 'Code Coverage',
        category: 'quality',
        value: 87.5,
        unit: '%',
        target: 90,
        trend: { direction: 'up', percentage: 2.1 },
    },
    {
        id: 'kpi-5',
        name: 'Security Vulnerabilities',
        category: 'security',
        value: 5,
        threshold: { warning: 10, critical: 20 },
        trend: { direction: 'down', percentage: -3.2 },
    },
];

// Activity fixtures encoded as ActivityLog with rich metadata
export const devsecopsActivity: ActivityLog[] = [
    {
        id: 'act-1',
        itemId: undefined,
        phaseId: undefined,
        userId: 'executive-agent',
        action: 'devsecops:activity',
        description: 'AI insight synthesis ready for review',
        timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
        metadata: {
            type: 'meeting',
            user: 'Executive Agent',
            status: 'pending',
            assignee: 'Leadership Council',
            updatedAt: new Date().toISOString(),
        },
    },
    {
        id: 'act-2',
        itemId: undefined,
        phaseId: undefined,
        userId: 'cicd-bot',
        action: 'devsecops:activity',
        description: 'Prod release pipeline gated on SAST policy',
        timestamp: new Date(Date.now() - 35 * 60 * 1000).toISOString(),
        metadata: {
            type: 'security-scan',
            user: 'CI/CD Bot',
            status: 'failed',
            link: '/pipelines/prod-release',
            updatedAt: new Date(Date.now() - 20 * 60 * 1000).toISOString(),
        },
    },
    {
        id: 'act-3',
        itemId: undefined,
        phaseId: undefined,
        userId: 'ops-team',
        action: 'devsecops:activity',
        description: 'Executive dashboard snapshot published',
        timestamp: new Date(Date.now() - 90 * 60 * 1000).toISOString(),
        metadata: {
            type: 'deployment',
            user: 'Ops Team',
            status: 'success',
            link: '/dashboards/executive',
            updatedAt: new Date(Date.now() - 80 * 60 * 1000).toISOString(),
        },
    },
    {
        id: 'act-4',
        itemId: undefined,
        phaseId: undefined,
        userId: 'compliance-bot',
        action: 'devsecops:activity',
        description: 'Compliance evidence package exported',
        timestamp: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
        metadata: {
            type: 'other',
            user: 'Compliance Bot',
            status: 'success',
            link: '/compliance/exports/latest',
            updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
        },
    },
];

// Persona dashboard fixtures (lifted from app mocks)
export const devsecopsPersonaDashboards: PersonaDashboardSummary[] = [
    {
        persona: 'CISO',
        slug: 'ciso-executive-overview',
        title: 'Executive Risk & Compliance Overview',
        description:
            'Strategic metrics covering organizational risk posture, compliance readiness, and financial exposure.',
        focusAreas: ['Risk posture trends', 'Regulatory compliance status', 'Operational resilience'],
        tasks: [
            {
                id: 'task-ciso-1',
                title: 'Review Quarterly Risk Report',
                description: 'Approve the Q4 risk assessment report.',
                workflowId: 'risk-review-workflow',
                status: 'pending',
            },
            {
                id: 'task-ciso-2',
                title: 'Approve Security Budget',
                description: 'Review and sign off on the 2026 security budget.',
                workflowId: 'budget-approval-workflow',
                status: 'pending',
            },
        ],
        kpis: [
            {
                id: 'risk-score',
                label: 'Residual Risk Score',
                value: '32 (Low)',
                trend: 'down',
                change: '-4 vs last month',
            },
            {
                id: 'compliance',
                label: 'Compliance Coverage',
                value: '92%',
                trend: 'up',
                change: '+3%',
            },
            {
                id: 'breach-likelihood',
                label: 'Breach Likelihood',
                value: 'Low',
                trend: 'neutral',
            },
        ],
        insights: [
            'SOC2 Domains A & B ready for audit window.',
            'Cloud misconfiguration hotspot reduced 18% after guardrail rollout.',
            'Critical vulnerability SLA adherence at 96% trailing 30 days.',
        ],
        primaryAction: {
            label: 'Open Executive Canvas View',
            href: '/devsecops/canvas?template=executive-dashboard',
        },
    },
    {
        persona: 'DEVSECOPS_ENGINEER',
        slug: 'devsecops-engineer-operations',
        title: 'DevSecOps Engineering Operations',
        description:
            'Operational dashboard for daily engineering enablement, pipeline health, and security gates.',
        focusAreas: [
            'Pipeline throughput & failures',
            'Security gate adherence',
            'Hotspot remediation velocity',
        ],
        tasks: [
            {
                id: 'task-eng-1',
                title: 'Deploy to Production',
                description: 'Release v2.3.0 to production environment.',
                workflowId: 'deploy-prod-workflow',
                status: 'pending',
            },
            {
                id: 'task-eng-2',
                title: 'Investigate Security Incident',
                description: 'Analyze logs for incident #402.',
                workflowId: 'incident-response-workflow',
                status: 'pending',
            },
        ],
        kpis: [
            {
                id: 'deployments',
                label: 'Deployments / Week',
                value: '28',
                trend: 'up',
                change: '+6',
            },
            {
                id: 'gate-pass',
                label: 'Security Gate Pass Rate',
                value: '94%',
                trend: 'down',
                change: '-1%',
            },
            {
                id: 'mttr',
                label: 'Mean Time to Restore',
                value: '42m',
                trend: 'down',
                change: '-8m',
            },
        ],
        insights: [
            'SAST false-positive rate increased by 12%; review tuning backlog.',
            'Pipeline "web-app-prod" exceeded deployment SLO yesterday.',
            'Two critical CVEs pending rollout in staging; blocked by QA signoff.',
        ],
        primaryAction: {
            label: 'View Phase Board',
            href: '/devsecops/phase/development',
        },
    },
    {
        persona: 'COMPLIANCE_OFFICER',
        slug: 'compliance-officer-assurance',
        title: 'Compliance Officer Assurance Workspace',
        description:
            'Evidence coverage, control health, and policy exceptions across active frameworks.',
        focusAreas: [
            'Control assessment cycle',
            'Evidence collection progress',
            'Exception backlog visibility',
        ],
        kpis: [
            {
                id: 'controls',
                label: 'Controls Reviewed',
                value: '148 / 162',
                trend: 'up',
                change: '+12 this week',
            },
            {
                id: 'evidence',
                label: 'Evidence Coverage',
                value: '88%',
                trend: 'up',
                change: '+5%',
            },
            {
                id: 'exceptions',
                label: 'Open Policy Exceptions',
                value: '9',
                trend: 'down',
                change: '-3',
            },
        ],
        insights: [
            'PCI-DSS testing window opens in 14 days—schedule final walkthrough.',
            'Three SOX controls flagged "needs improvement" awaiting mitigation tasks.',
            'EU GDPR data processing audit evidence export ready for review.',
        ],
        primaryAction: {
            label: 'Open Compliance Workspace',
            href: '/devsecops/settings',
        },
    },
];

export function createDevSecOpsOverview(): DevSecOpsOverview {
    const overview: DevSecOpsOverview = {
        phases: devsecopsPhases,
        items: devsecopsItems,
        milestones: devsecopsMilestones,
        kpis: devsecopsKpis,
        activity: devsecopsActivity,
        personaDashboards: devsecopsPersonaDashboards,
    };

    return overview;
}
