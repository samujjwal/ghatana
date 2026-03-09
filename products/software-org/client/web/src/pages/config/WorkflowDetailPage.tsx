/**
 * Workflow Detail Page
 *
 * Detailed view of a workflow configuration with steps, triggers, and related entities.
 *
 * @doc.type page
 * @doc.purpose Workflow detail view
 * @doc.layer product
 */

import { useParams, useNavigate } from 'react-router';
import { FileCode, Play, Clock, AlertCircle } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { useConfigWorkflows, useAgents, useConfigDepartments } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Step type for workflow steps
interface WorkflowStep {
    id: string;
    agent: string;
    action: string;
    description: string;
    timeout: string;
    type: string;
    condition?: string;
    requires_approval?: boolean;
}

interface MockWorkflow {
    id: string;
    name: string;
    description: string;
    department: string;
    status: string;
    trigger: { event: string; branch?: string; cron?: string; severity?: string };
    schedule: string | null;
    steps: WorkflowStep[];
    metrics: {
        totalRuns: number;
        successRate: number;
        avgDuration: string;
        lastRun: string;
    };
    createdAt: string;
    updatedAt: string;
}

// Mock workflow data with complete information
const mockWorkflows: MockWorkflow[] = [
    {
        id: 'wf-ci-cd-pipeline',
        name: 'CI/CD Pipeline',
        description: 'Automated build, test, and deployment pipeline for production releases',
        department: 'engineering',
        status: 'active',
        trigger: { event: 'git.push', branch: 'main' },
        schedule: null,
        steps: [
            { id: 'step-1', agent: 'build-agent', action: 'compile', description: 'Compile source code', timeout: '10m', type: 'build' },
            { id: 'step-2', agent: 'test-agent', action: 'run-tests', description: 'Run unit and integration tests', timeout: '30m', type: 'test' },
            { id: 'step-3', agent: 'security-agent', action: 'scan', description: 'Security vulnerability scan', timeout: '15m', type: 'security' },
            { id: 'step-4', agent: 'deploy-agent', action: 'deploy', description: 'Deploy to staging environment', timeout: '20m', type: 'deploy', condition: 'tests.passed && security.passed' },
            { id: 'step-5', agent: 'deploy-agent', action: 'promote', description: 'Promote to production', timeout: '10m', type: 'deploy', requires_approval: true },
        ],
        metrics: {
            totalRuns: 1247,
            successRate: 94.2,
            avgDuration: '45m',
            lastRun: '2024-01-15T10:30:00Z',
        },
        createdAt: '2023-06-01T00:00:00Z',
        updatedAt: '2024-01-15T10:30:00Z',
    },
    {
        id: 'wf-security-scan',
        name: 'Security Scan',
        description: 'Automated security scanning for vulnerabilities and compliance',
        department: 'security',
        status: 'active',
        trigger: { event: 'schedule', cron: '0 2 * * *' },
        schedule: 'Daily at 2:00 AM',
        steps: [
            { id: 'step-1', agent: 'security-agent', action: 'dependency-scan', description: 'Scan dependencies for vulnerabilities', timeout: '20m', type: 'security' },
            { id: 'step-2', agent: 'security-agent', action: 'sast', description: 'Static application security testing', timeout: '30m', type: 'security' },
            { id: 'step-3', agent: 'security-agent', action: 'dast', description: 'Dynamic application security testing', timeout: '45m', type: 'security' },
            { id: 'step-4', agent: 'notification-agent', action: 'notify', description: 'Send security report', timeout: '5m', type: 'notification' },
        ],
        metrics: {
            totalRuns: 365,
            successRate: 98.1,
            avgDuration: '1h 35m',
            lastRun: '2024-01-15T02:00:00Z',
        },
        createdAt: '2023-03-15T00:00:00Z',
        updatedAt: '2024-01-15T02:00:00Z',
    },
    {
        id: 'wf-incident-response',
        name: 'Incident Response',
        description: 'Automated incident detection, triage, and response workflow',
        department: 'devops',
        status: 'active',
        trigger: { event: 'alert.triggered', severity: 'critical' },
        schedule: null,
        steps: [
            { id: 'step-1', agent: 'triage-agent', action: 'analyze', description: 'Analyze incident and gather context', timeout: '5m', type: 'analysis' },
            { id: 'step-2', agent: 'notification-agent', action: 'page', description: 'Page on-call engineer', timeout: '2m', type: 'notification' },
            { id: 'step-3', agent: 'remediation-agent', action: 'auto-remediate', description: 'Attempt automatic remediation', timeout: '10m', type: 'remediation', condition: 'incident.auto_remediable' },
            { id: 'step-4', agent: 'documentation-agent', action: 'create-postmortem', description: 'Generate postmortem template', timeout: '5m', type: 'documentation' },
        ],
        metrics: {
            totalRuns: 89,
            successRate: 87.6,
            avgDuration: '22m',
            lastRun: '2024-01-14T18:45:00Z',
        },
        createdAt: '2023-09-01T00:00:00Z',
        updatedAt: '2024-01-14T18:45:00Z',
    },
];

function WorkflowStepsTimeline({ steps }: { steps: typeof mockWorkflows[0]['steps'] }) {
    const getStepIcon = (type: string) => {
        switch (type) {
            case 'build': return '🔨';
            case 'test': return '🧪';
            case 'security': return '🔒';
            case 'deploy': return '🚀';
            case 'notification': return '📢';
            case 'analysis': return '🔍';
            case 'remediation': return '🔧';
            case 'documentation': return '📝';
            default: return '⚙️';
        }
    };

    return (
        <div className="relative">
            {/* Timeline line */}
            <div className="absolute left-6 top-0 bottom-0 w-0.5 bg-gray-200 dark:bg-gray-700" />

            <div className="space-y-4">
                {steps.map((step, index) => (
                    <div key={step.id} className="relative flex items-start gap-4">
                        {/* Step indicator */}
                        <div className="relative z-10 flex-shrink-0 w-12 h-12 rounded-xl bg-white dark:bg-slate-800 border-2 border-gray-200 dark:border-gray-700 flex items-center justify-center text-xl">
                            {getStepIcon(step.type || 'default')}
                        </div>

                        {/* Step content */}
                        <div className={cn(
                            'flex-1 p-4 rounded-lg border',
                            'bg-white dark:bg-slate-900 border-gray-200 dark:border-slate-700'
                        )}>
                            <div className="flex items-start justify-between">
                                <div>
                                    <div className="flex items-center gap-2">
                                        <span className="font-medium text-gray-900 dark:text-gray-100">
                                            {step.description}
                                        </span>
                                        {step.requires_approval && (
                                            <span className="inline-flex items-center px-2 py-0.5 rounded text-xs font-medium bg-amber-100 dark:bg-amber-900/50 text-amber-700 dark:text-amber-300">
                                                Requires Approval
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex items-center gap-4 mt-2 text-sm text-gray-500 dark:text-gray-400">
                                        <span className="inline-flex items-center gap-1">
                                            <span className="font-mono text-xs bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">
                                                {step.agent}
                                            </span>
                                            <span>→</span>
                                            <span className="font-mono text-xs bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">
                                                {step.action}
                                            </span>
                                        </span>
                                        <span className="inline-flex items-center gap-1">
                                            <Clock className="w-3 h-3" />
                                            {step.timeout}
                                        </span>
                                    </div>
                                    {step.condition && (
                                        <div className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                                            <span className="font-medium">Condition:</span>{' '}
                                            <code className="bg-gray-100 dark:bg-gray-800 px-1 py-0.5 rounded">
                                                {step.condition}
                                            </code>
                                        </div>
                                    )}
                                </div>
                                <span className="text-xs font-medium text-gray-400 dark:text-gray-500">
                                    Step {index + 1}
                                </span>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export function WorkflowDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const { data: workflows, isLoading: workflowsLoading } = useConfigWorkflows();
    const { data: agents } = useAgents();
    const { data: departments } = useConfigDepartments();

    // Find workflow from API or mock data
    const workflow = workflows?.find(w => w.id === id) || mockWorkflows.find(w => w.id === id);
    const mockData = mockWorkflows.find(w => w.id === id);

    if (workflowsLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!workflow) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Workflow Not Found</h2>
                <p className="text-gray-500 mt-2">The workflow "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/config/workflows')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Workflows
                </button>
            </div>
        );
    }

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Name', value: workflow.name },
                { key: 'status', label: 'Status', value: mockData?.status || 'active', type: 'badge' },
                { key: 'department', label: 'Department', value: mockData?.department || workflow.trigger?.event, type: 'link', linkTo: `/config/departments/${mockData?.department}` },
                { key: 'trigger', label: 'Trigger Event', value: mockData?.trigger?.event || workflow.trigger?.event, type: 'code' },
                { key: 'schedule', label: 'Schedule', value: mockData?.schedule },
            ],
        },
        {
            id: 'metrics',
            title: 'Performance Metrics',
            fields: mockData?.metrics ? [
                { key: 'totalRuns', label: 'Total Runs', value: mockData.metrics.totalRuns },
                { key: 'successRate', label: 'Success Rate', value: `${mockData.metrics.successRate}%` },
                { key: 'avgDuration', label: 'Average Duration', value: mockData.metrics.avgDuration },
                { key: 'lastRun', label: 'Last Run', value: mockData.metrics.lastRun, type: 'date' },
            ] : [
                { key: 'steps', label: 'Steps', value: workflow.steps?.length || 0 },
            ],
        },
        {
            id: 'steps',
            title: 'Workflow Steps',
            description: 'Sequential steps executed in this workflow',
            content: mockData?.steps ? (
                <WorkflowStepsTimeline steps={mockData.steps} />
            ) : workflow.steps ? (
                <div className="space-y-2">
                    {workflow.steps.map((step, i) => (
                        <div key={step.id} className="p-3 bg-gray-50 dark:bg-gray-800 rounded-lg">
                            <span className="font-medium">{i + 1}. {step.description || step.action}</span>
                            {step.agent && (
                                <span className="ml-2 text-sm text-gray-500">({step.agent})</span>
                            )}
                        </div>
                    ))}
                </div>
            ) : (
                <p className="text-gray-500">No steps defined</p>
            ),
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Workflow ID', value: workflow.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: mockData?.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: mockData?.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    // Add related department
    if (mockData?.department) {
        const dept = departments?.find(d => d.id === mockData.department);
        relatedEntities.push({
            id: mockData.department,
            name: dept?.name || mockData.department,
            type: 'Department',
            href: `/config/departments/${mockData.department}`,
            status: 'active',
        });
    }

    // Add related agents
    const workflowAgents = new Set(mockData?.steps?.map(s => s.agent) || []);
    workflowAgents.forEach(agentId => {
        const agent = agents?.find(a => a.id === agentId);
        relatedEntities.push({
            id: agentId,
            name: agent?.name || agentId,
            type: 'Agent',
            href: `/config/agents/${agentId}`,
            status: 'active',
        });
    });

    return (
        <EntityDetailPage
            entityType="Workflow"
            entityId={workflow.id}
            title={workflow.name}
            description={mockData?.description}
            status={mockData?.status}
            icon={<FileCode className="w-6 h-6" />}
            backHref="/config/workflows"
            backLabel="Back to Workflows"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit workflow')}
            actions={[
                {
                    id: 'run',
                    label: 'Run Now',
                    icon: <Play className="w-4 h-4" />,
                    onClick: () => console.log('Run workflow'),
                    variant: 'primary',
                },
            ]}
        />
    );
}

export default WorkflowDetailPage;
