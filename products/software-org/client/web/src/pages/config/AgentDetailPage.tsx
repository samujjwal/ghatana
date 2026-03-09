/**
 * Agent Detail Page
 *
 * Detailed view of an AI agent configuration with capabilities, personality, and activity.
 * Includes edit functionality for all agent properties.
 *
 * @doc.type page
 * @doc.purpose Agent detail view and editing
 * @doc.layer product
 */

import { useState, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router';
import { Bot, Zap, AlertCircle, Settings, Edit2 } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { useAgents, useConfigDepartments, useConfigWorkflows } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';
import { AgentEditDrawer, type AgentData } from '@/components/agent/AgentEditDrawer';

// Mock agent data with complete information
interface MockAgent {
    id: string;
    name: string;
    role: string;
    description: string;
    department: string;
    status: 'active' | 'idle' | 'disabled';
    capabilities: string[];
    personality: {
        temperature: number;
        creativity: number;
        assertiveness: number;
    };
    model: {
        id: string;
        provider: string;
        maxTokens: number;
    };
    systemPrompt: string;
    metrics: {
        tasksCompleted: number;
        successRate: number;
        avgResponseTime: string;
        lastActive: string;
    };
    workflows: string[];
    permissions: string[];
    createdAt: string;
    updatedAt: string;
}

const mockAgents: MockAgent[] = [
    {
        id: 'agent-build',
        name: 'Build Agent',
        role: 'CI/CD Automation',
        description: 'Handles automated builds, compilation, and artifact generation for all projects',
        department: 'engineering',
        status: 'active',
        capabilities: ['compile', 'package', 'artifact-upload', 'cache-management', 'parallel-builds'],
        personality: {
            temperature: 0.3,
            creativity: 0.2,
            assertiveness: 0.8,
        },
        model: {
            id: 'gpt-4-turbo',
            provider: 'OpenAI',
            maxTokens: 4096,
        },
        systemPrompt: 'You are a build automation agent. Your role is to compile code, manage dependencies, and produce build artifacts efficiently.',
        metrics: {
            tasksCompleted: 1247,
            successRate: 98.2,
            avgResponseTime: '2.3s',
            lastActive: '2024-01-15T10:30:00Z',
        },
        workflows: ['wf-ci-cd-pipeline'],
        permissions: ['read:code', 'write:artifacts', 'execute:builds'],
        createdAt: '2023-06-01T00:00:00Z',
        updatedAt: '2024-01-15T10:30:00Z',
    },
    {
        id: 'agent-security',
        name: 'Security Agent',
        role: 'Security Scanning',
        description: 'Performs automated security scans, vulnerability detection, and compliance checks',
        department: 'security',
        status: 'active',
        capabilities: ['vulnerability-scan', 'sast', 'dast', 'dependency-audit', 'compliance-check'],
        personality: {
            temperature: 0.1,
            creativity: 0.1,
            assertiveness: 0.9,
        },
        model: {
            id: 'gpt-4-turbo',
            provider: 'OpenAI',
            maxTokens: 8192,
        },
        systemPrompt: 'You are a security agent. Your role is to identify vulnerabilities, assess risks, and ensure compliance with security policies.',
        metrics: {
            tasksCompleted: 892,
            successRate: 99.1,
            avgResponseTime: '5.7s',
            lastActive: '2024-01-15T02:00:00Z',
        },
        workflows: ['wf-security-scan', 'wf-ci-cd-pipeline'],
        permissions: ['read:code', 'read:dependencies', 'write:reports', 'execute:scans'],
        createdAt: '2023-03-15T00:00:00Z',
        updatedAt: '2024-01-15T02:00:00Z',
    },
    {
        id: 'agent-deploy',
        name: 'Deploy Agent',
        role: 'Deployment Automation',
        description: 'Manages deployments to staging and production environments with rollback capabilities',
        department: 'devops',
        status: 'active',
        capabilities: ['deploy', 'rollback', 'health-check', 'traffic-shift', 'canary-release'],
        personality: {
            temperature: 0.2,
            creativity: 0.3,
            assertiveness: 0.7,
        },
        model: {
            id: 'gpt-4-turbo',
            provider: 'OpenAI',
            maxTokens: 4096,
        },
        systemPrompt: 'You are a deployment agent. Your role is to safely deploy applications, monitor health, and perform rollbacks when necessary.',
        metrics: {
            tasksCompleted: 456,
            successRate: 97.8,
            avgResponseTime: '8.2s',
            lastActive: '2024-01-15T10:45:00Z',
        },
        workflows: ['wf-ci-cd-pipeline'],
        permissions: ['read:artifacts', 'write:deployments', 'execute:deploys', 'execute:rollbacks'],
        createdAt: '2023-06-01T00:00:00Z',
        updatedAt: '2024-01-15T10:45:00Z',
    },
    {
        id: 'agent-triage',
        name: 'Triage Agent',
        role: 'Incident Triage',
        description: 'Analyzes incidents, gathers context, and provides initial assessment and recommendations',
        department: 'devops',
        status: 'active',
        capabilities: ['incident-analysis', 'context-gathering', 'severity-assessment', 'runbook-lookup', 'escalation'],
        personality: {
            temperature: 0.4,
            creativity: 0.5,
            assertiveness: 0.6,
        },
        model: {
            id: 'gpt-4-turbo',
            provider: 'OpenAI',
            maxTokens: 8192,
        },
        systemPrompt: 'You are an incident triage agent. Your role is to quickly analyze incidents, gather relevant context, and provide actionable recommendations.',
        metrics: {
            tasksCompleted: 234,
            successRate: 91.5,
            avgResponseTime: '12.4s',
            lastActive: '2024-01-14T18:45:00Z',
        },
        workflows: ['wf-incident-response'],
        permissions: ['read:logs', 'read:metrics', 'read:incidents', 'write:assessments'],
        createdAt: '2023-09-01T00:00:00Z',
        updatedAt: '2024-01-14T18:45:00Z',
    },
];

function PersonalityMeter({ label, value }: { label: string; value: number }) {
    return (
        <div className="space-y-1">
            <div className="flex justify-between text-sm">
                <span className="text-gray-600 dark:text-gray-400">{label}</span>
                <span className="font-medium text-gray-900 dark:text-gray-100">{(value * 100).toFixed(0)}%</span>
            </div>
            <div className="h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                <div
                    className="h-full bg-blue-500 rounded-full transition-all"
                    style={{ width: `${value * 100}%` }}
                />
            </div>
        </div>
    );
}

function CapabilityBadges({ capabilities }: { capabilities: string[] }) {
    return (
        <div className="flex flex-wrap gap-2">
            {capabilities.map((cap) => (
                <span
                    key={cap}
                    className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg text-sm font-medium bg-emerald-100 dark:bg-emerald-900/50 text-emerald-700 dark:text-emerald-300"
                >
                    <Zap className="w-3 h-3" />
                    {cap}
                </span>
            ))}
        </div>
    );
}

function AgentMetrics({ metrics }: { metrics: MockAgent['metrics'] }) {
    return (
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800">
                <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {metrics.tasksCompleted.toLocaleString()}
                </div>
                <div className="text-sm text-gray-500 dark:text-gray-400">Tasks Completed</div>
            </div>
            <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800">
                <div className="text-2xl font-bold text-green-600 dark:text-green-400">
                    {metrics.successRate}%
                </div>
                <div className="text-sm text-gray-500 dark:text-gray-400">Success Rate</div>
            </div>
            <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800">
                <div className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                    {metrics.avgResponseTime}
                </div>
                <div className="text-sm text-gray-500 dark:text-gray-400">Avg Response</div>
            </div>
            <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800">
                <div className="text-sm font-medium text-gray-900 dark:text-gray-100">
                    {new Date(metrics.lastActive).toLocaleString()}
                </div>
                <div className="text-sm text-gray-500 dark:text-gray-400">Last Active</div>
            </div>
        </div>
    );
}

export function AgentDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    // Edit drawer state
    const [isEditDrawerOpen, setIsEditDrawerOpen] = useState(false);

    const { data: agents, isLoading, refetch } = useAgents();
    const { data: departments } = useConfigDepartments();
    const { data: workflows } = useConfigWorkflows();

    // Find agent from API or mock data
    const agent = agents?.find(a => a.id === id) || mockAgents.find(a => a.id === id);
    const mockData = mockAgents.find(a => a.id === id);

    // Convert to AgentData format for editing
    const getAgentDataForEdit = useCallback((): AgentData | null => {
        if (!mockData && !agent) return null;
        const data = mockData || agent;
        return {
            id: data?.id || '',
            name: data?.name || '',
            role: mockData?.role || (typeof agent?.role === 'string' ? agent.role : ''),
            description: mockData?.description,
            department: mockData?.department,
            status: mockData?.status,
            capabilities: mockData?.capabilities || agent?.capabilities || [],
            personality: mockData?.personality,
            model: mockData?.model ? {
                id: mockData.model.id,
                provider: mockData.model.provider,
                maxTokens: mockData.model.maxTokens,
            } : undefined,
            systemPrompt: mockData?.systemPrompt,
            permissions: mockData?.permissions,
        };
    }, [mockData, agent]);

    // Handle save from edit drawer
    const handleSaveAgent = useCallback(async (updatedAgent: AgentData) => {
        // TODO: Call API to update agent when backend supports it
        console.log('Saving agent:', updatedAgent);
        // For now, just simulate save
        await new Promise(resolve => setTimeout(resolve, 500));
        // Refetch agents to update the display
        refetch();
    }, [refetch]);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!agent && !mockData) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Agent Not Found</h2>
                <p className="text-gray-500 mt-2">The agent "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/config/agents')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Agents
                </button>
            </div>
        );
    }

    const displayData = mockData || agent;

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Name', value: displayData?.name },
                { key: 'role', label: 'Role', value: mockData?.role, type: 'badge' },
                { key: 'status', label: 'Status', value: mockData?.status, type: 'badge' },
                { key: 'department', label: 'Department', value: mockData?.department, type: 'link', linkTo: `/config/departments/${mockData?.department}` },
            ],
        },
        {
            id: 'metrics',
            title: 'Performance Metrics',
            content: mockData?.metrics ? (
                <AgentMetrics metrics={mockData.metrics} />
            ) : (
                <p className="text-gray-500">No metrics available</p>
            ),
        },
        {
            id: 'capabilities',
            title: 'Capabilities',
            description: 'Actions this agent can perform',
            content: mockData?.capabilities ? (
                <CapabilityBadges capabilities={mockData.capabilities} />
            ) : agent?.capabilities ? (
                <CapabilityBadges capabilities={agent.capabilities} />
            ) : (
                <p className="text-gray-500">No capabilities defined</p>
            ),
        },
        {
            id: 'personality',
            title: 'Personality Configuration',
            description: 'AI behavior parameters',
            content: mockData?.personality ? (
                <div className="space-y-4 max-w-md">
                    <PersonalityMeter label="Temperature" value={mockData.personality.temperature} />
                    <PersonalityMeter label="Creativity" value={mockData.personality.creativity} />
                    <PersonalityMeter label="Assertiveness" value={mockData.personality.assertiveness} />
                </div>
            ) : (
                <p className="text-gray-500">Default personality settings</p>
            ),
        },
        {
            id: 'model',
            title: 'Model Configuration',
            fields: mockData?.model ? [
                { key: 'modelId', label: 'Model ID', value: mockData.model.id, type: 'code' },
                { key: 'provider', label: 'Provider', value: mockData.model.provider },
                { key: 'maxTokens', label: 'Max Tokens', value: mockData.model.maxTokens },
            ] : [],
        },
        {
            id: 'systemPrompt',
            title: 'System Prompt',
            content: mockData?.systemPrompt ? (
                <div className="p-4 rounded-lg bg-gray-50 dark:bg-gray-800 border border-gray-200 dark:border-gray-700">
                    <pre className="text-sm text-gray-700 dark:text-gray-300 whitespace-pre-wrap font-mono">
                        {mockData.systemPrompt}
                    </pre>
                </div>
            ) : (
                <p className="text-gray-500">No system prompt configured</p>
            ),
        },
        {
            id: 'permissions',
            title: 'Permissions',
            fields: [
                { key: 'permissions', label: 'Granted Permissions', value: mockData?.permissions, type: 'array' },
            ],
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Agent ID', value: displayData?.id, type: 'code' },
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

    // Add related workflows
    mockData?.workflows?.forEach(wfId => {
        const wf = workflows?.find(w => w.id === wfId);
        relatedEntities.push({
            id: wfId,
            name: wf?.name || wfId,
            type: 'Workflow',
            href: `/config/workflows/${wfId}`,
            status: 'active',
        });
    });

    return (
        <>
            <EntityDetailPage
                entityType="Agent"
                entityId={displayData?.id || id || ''}
                title={displayData?.name || 'Unknown Agent'}
                subtitle={mockData?.role}
                description={mockData?.description}
                status={mockData?.status}
                icon={<Bot className="w-6 h-6" />}
                backHref="/config/agents"
                backLabel="Back to Agents"
                sections={sections}
                relatedEntities={relatedEntities}
                onEdit={() => setIsEditDrawerOpen(true)}
                actions={[
                    {
                        id: 'edit',
                        label: 'Edit Agent',
                        icon: <Edit2 className="w-4 h-4" />,
                        onClick: () => setIsEditDrawerOpen(true),
                        variant: 'primary',
                    },
                    {
                        id: 'configure',
                        label: 'Configure',
                        icon: <Settings className="w-4 h-4" />,
                        onClick: () => setIsEditDrawerOpen(true),
                        variant: 'secondary',
                    },
                ]}
            />

            {/* Agent Edit Drawer */}
            <AgentEditDrawer
                isOpen={isEditDrawerOpen}
                onClose={() => setIsEditDrawerOpen(false)}
                agent={getAgentDataForEdit()}
                onSave={handleSaveAgent}
            />
        </>
    );
}

export default AgentDetailPage;
