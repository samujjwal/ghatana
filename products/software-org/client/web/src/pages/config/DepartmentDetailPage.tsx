/**
 * Department Detail Page (Config)
 *
 * Detailed view of a department configuration with teams, agents, workflows, and KPIs.
 *
 * @doc.type page
 * @doc.purpose Department detail view
 * @doc.layer product
 */

import { useParams, useNavigate, useLocation } from 'react-router';
import { Building2, Users, Bot, Workflow, BarChart3, AlertCircle, Settings } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { useConfigDepartments, useAgents, useConfigWorkflows, useKpis } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Mock department data with complete information
interface MockDepartment {
    id: string;
    name: string;
    type: string;
    description: string;
    head: {
        name: string;
        email: string;
        title: string;
    };
    teams: {
        id: string;
        name: string;
        size: number;
        lead: string;
    }[];
    metrics: {
        headcount: number;
        activeAgents: number;
        activeWorkflows: number;
        automationLevel: number;
        avgVelocity: number;
        satisfaction: number;
    };
    tools: string[];
    integrations: string[];
    status: 'active' | 'inactive';
    createdAt: string;
    updatedAt: string;
}

const mockDepartments: MockDepartment[] = [
    {
        id: 'engineering',
        name: 'Engineering',
        type: 'ENGINEERING',
        description: 'Core software development and technical infrastructure. Responsible for building and maintaining all product features.',
        head: {
            name: 'Sarah Chen',
            email: 'sarah.chen@example.com',
            title: 'VP of Engineering',
        },
        teams: [
            { id: 'team-backend', name: 'Backend Team', size: 12, lead: 'Mike Johnson' },
            { id: 'team-frontend', name: 'Frontend Team', size: 10, lead: 'Lisa Park' },
            { id: 'team-platform', name: 'Platform Team', size: 8, lead: 'David Kim' },
            { id: 'team-mobile', name: 'Mobile Team', size: 6, lead: 'Anna Smith' },
        ],
        metrics: {
            headcount: 36,
            activeAgents: 8,
            activeWorkflows: 12,
            automationLevel: 78,
            avgVelocity: 92,
            satisfaction: 4.2,
        },
        tools: ['GitHub', 'Jira', 'Slack', 'Datadog', 'PagerDuty'],
        integrations: ['ci-cd', 'monitoring', 'alerting'],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'devops',
        name: 'DevOps',
        type: 'DEVOPS',
        description: 'Infrastructure, deployment, and site reliability. Ensures system uptime and deployment automation.',
        head: {
            name: 'James Wilson',
            email: 'james.wilson@example.com',
            title: 'Director of DevOps',
        },
        teams: [
            { id: 'team-sre', name: 'SRE Team', size: 6, lead: 'Tom Brown' },
            { id: 'team-infra', name: 'Infrastructure Team', size: 5, lead: 'Emily Davis' },
        ],
        metrics: {
            headcount: 11,
            activeAgents: 5,
            activeWorkflows: 8,
            automationLevel: 92,
            avgVelocity: 88,
            satisfaction: 4.4,
        },
        tools: ['Terraform', 'Kubernetes', 'ArgoCD', 'Prometheus', 'Grafana'],
        integrations: ['cloud-provider', 'monitoring', 'incident-management'],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'security',
        name: 'Security',
        type: 'SECURITY',
        description: 'Application and infrastructure security. Responsible for vulnerability management and compliance.',
        head: {
            name: 'Rachel Green',
            email: 'rachel.green@example.com',
            title: 'CISO',
        },
        teams: [
            { id: 'team-appsec', name: 'Application Security', size: 4, lead: 'Chris Lee' },
            { id: 'team-compliance', name: 'Compliance Team', size: 3, lead: 'Maria Garcia' },
        ],
        metrics: {
            headcount: 7,
            activeAgents: 3,
            activeWorkflows: 5,
            automationLevel: 85,
            avgVelocity: 90,
            satisfaction: 4.3,
        },
        tools: ['Snyk', 'SonarQube', 'Vault', 'SIEM'],
        integrations: ['vulnerability-scanner', 'compliance-tracker'],
        status: 'active',
        createdAt: '2022-06-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'product',
        name: 'Product',
        type: 'PRODUCT',
        description: 'Product management and strategy. Defines roadmap and prioritizes features.',
        head: {
            name: 'Alex Thompson',
            email: 'alex.thompson@example.com',
            title: 'VP of Product',
        },
        teams: [
            { id: 'team-pm', name: 'Product Managers', size: 5, lead: 'Jennifer Wu' },
            { id: 'team-design', name: 'Design Team', size: 4, lead: 'Mark Taylor' },
        ],
        metrics: {
            headcount: 9,
            activeAgents: 2,
            activeWorkflows: 4,
            automationLevel: 45,
            avgVelocity: 85,
            satisfaction: 4.5,
        },
        tools: ['Figma', 'Notion', 'ProductBoard', 'Amplitude'],
        integrations: ['analytics', 'user-research'],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
];

function DepartmentMetricsGrid({ metrics }: { metrics: MockDepartment['metrics'] }) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
            <div className="p-4 rounded-lg bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800">
                <div className="flex items-center gap-2 mb-2">
                    <Users className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                    <span className="text-sm font-medium text-blue-700 dark:text-blue-300">Headcount</span>
                </div>
                <div className="text-2xl font-bold text-blue-900 dark:text-blue-100">{metrics.headcount}</div>
            </div>
            <div className="p-4 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800">
                <div className="flex items-center gap-2 mb-2">
                    <Bot className="w-5 h-5 text-emerald-600 dark:text-emerald-400" />
                    <span className="text-sm font-medium text-emerald-700 dark:text-emerald-300">Active Agents</span>
                </div>
                <div className="text-2xl font-bold text-emerald-900 dark:text-emerald-100">{metrics.activeAgents}</div>
            </div>
            <div className="p-4 rounded-lg bg-rose-50 dark:bg-rose-900/30 border border-rose-200 dark:border-rose-800">
                <div className="flex items-center gap-2 mb-2">
                    <Workflow className="w-5 h-5 text-rose-600 dark:text-rose-400" />
                    <span className="text-sm font-medium text-rose-700 dark:text-rose-300">Workflows</span>
                </div>
                <div className="text-2xl font-bold text-rose-900 dark:text-rose-100">{metrics.activeWorkflows}</div>
            </div>
            <div className="p-4 rounded-lg bg-violet-50 dark:bg-violet-900/30 border border-violet-200 dark:border-violet-800">
                <div className="flex items-center gap-2 mb-2">
                    <BarChart3 className="w-5 h-5 text-violet-600 dark:text-violet-400" />
                    <span className="text-sm font-medium text-violet-700 dark:text-violet-300">Automation</span>
                </div>
                <div className="text-2xl font-bold text-violet-900 dark:text-violet-100">{metrics.automationLevel}%</div>
            </div>
            <div className="p-4 rounded-lg bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800">
                <div className="flex items-center gap-2 mb-2">
                    <span className="text-sm font-medium text-amber-700 dark:text-amber-300">Velocity</span>
                </div>
                <div className="text-2xl font-bold text-amber-900 dark:text-amber-100">{metrics.avgVelocity}%</div>
            </div>
            <div className="p-4 rounded-lg bg-cyan-50 dark:bg-cyan-900/30 border border-cyan-200 dark:border-cyan-800">
                <div className="flex items-center gap-2 mb-2">
                    <span className="text-sm font-medium text-cyan-700 dark:text-cyan-300">Satisfaction</span>
                </div>
                <div className="text-2xl font-bold text-cyan-900 dark:text-cyan-100">{metrics.satisfaction}/5</div>
            </div>
        </div>
    );
}

function TeamsList({ teams }: { teams: MockDepartment['teams'] }) {
    return (
        <div className="space-y-3">
            {teams.map((team) => (
                <div
                    key={team.id}
                    className={cn(
                        'flex items-center justify-between p-4 rounded-lg border',
                        'bg-white dark:bg-slate-800 border-gray-200 dark:border-gray-700',
                        'hover:border-blue-300 dark:hover:border-blue-700 transition-colors cursor-pointer'
                    )}
                >
                    <div className="flex items-center gap-3">
                        <div className="w-10 h-10 rounded-lg bg-blue-100 dark:bg-blue-900/50 flex items-center justify-center">
                            <Users className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <div className="font-medium text-gray-900 dark:text-gray-100">{team.name}</div>
                            <div className="text-sm text-gray-500 dark:text-gray-400">Lead: {team.lead}</div>
                        </div>
                    </div>
                    <div className="text-right">
                        <div className="text-lg font-semibold text-gray-900 dark:text-gray-100">{team.size}</div>
                        <div className="text-xs text-gray-500 dark:text-gray-400">members</div>
                    </div>
                </div>
            ))}
        </div>
    );
}

export function DepartmentDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();
    const location = useLocation();

    // Determine back path based on current route
    const isConfigRoute = location.pathname.startsWith('/config');
    const backHref = isConfigRoute ? '/config/departments' : '/departments';

    const { data: departments, isLoading } = useConfigDepartments();
    const { data: agents } = useAgents();
    const { data: workflows } = useConfigWorkflows();
    const { data: kpis } = useKpis();

    // Find department from API or mock data
    const department = departments?.find(d => d.id === id) || mockDepartments.find(d => d.id === id);
    const mockData = mockDepartments.find(d => d.id === id);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!department && !mockData) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Department Not Found</h2>
                <p className="text-gray-500 mt-2">The department "{id}" could not be found.</p>
                <button
                    onClick={() => navigate(backHref)}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Departments
                </button>
            </div>
        );
    }

    const displayData = mockData || department;

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Name', value: displayData?.name },
                { key: 'type', label: 'Type', value: mockData?.type || department?.type, type: 'badge' },
                { key: 'status', label: 'Status', value: mockData?.status, type: 'badge' },
                { key: 'head', label: 'Department Head', value: mockData?.head?.name },
                { key: 'headEmail', label: 'Head Email', value: mockData?.head?.email },
                { key: 'headTitle', label: 'Head Title', value: mockData?.head?.title },
            ],
        },
        {
            id: 'metrics',
            title: 'Department Metrics',
            content: mockData?.metrics ? (
                <DepartmentMetricsGrid metrics={mockData.metrics} />
            ) : (
                <p className="text-gray-500">No metrics available</p>
            ),
        },
        {
            id: 'teams',
            title: 'Teams',
            description: `${mockData?.teams?.length || 0} teams in this department`,
            content: mockData?.teams ? (
                <TeamsList teams={mockData.teams} />
            ) : (
                <p className="text-gray-500">No teams configured</p>
            ),
        },
        {
            id: 'tools',
            title: 'Tools & Integrations',
            fields: [
                { key: 'tools', label: 'Tools', value: mockData?.tools, type: 'array' },
                { key: 'integrations', label: 'Integrations', value: mockData?.integrations, type: 'array' },
            ],
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Department ID', value: displayData?.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: mockData?.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: mockData?.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    // Add related agents
    const deptAgents = agents?.filter(a => a.department === id) || [];
    deptAgents.slice(0, 5).forEach(agent => {
        relatedEntities.push({
            id: agent.id,
            name: agent.name,
            type: 'Agent',
            href: `/config/agents/${agent.id}`,
            status: 'active',
        });
    });

    // Add related workflows
    const deptWorkflows = workflows?.filter(w => (w as any).department === id) || [];
    deptWorkflows.slice(0, 5).forEach(wf => {
        relatedEntities.push({
            id: wf.id,
            name: wf.name,
            type: 'Workflow',
            href: `/config/workflows/${wf.id}`,
            status: 'active',
        });
    });

    // Add related KPIs
    const deptKpis = kpis?.filter(k => (k as any).department === id) || [];
    deptKpis.slice(0, 3).forEach(kpi => {
        relatedEntities.push({
            id: kpi.id,
            name: kpi.name,
            type: 'KPI',
            href: `/config/kpis/${kpi.id}`,
            status: 'active',
        });
    });

    return (
        <EntityDetailPage
            entityType="Department"
            entityId={displayData?.id || id || ''}
            title={displayData?.name || 'Unknown Department'}
            subtitle={mockData?.head?.title}
            description={mockData?.description || department?.description}
            status={mockData?.status}
            icon={<Building2 className="w-6 h-6" />}
            backHref={backHref}
            backLabel="Back to Departments"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit department')}
            actions={[
                {
                    id: 'settings',
                    label: 'Settings',
                    icon: <Settings className="w-4 h-4" />,
                    onClick: () => console.log('Department settings'),
                    variant: 'secondary',
                },
            ]}
        />
    );
}

export default DepartmentDetailPage;
