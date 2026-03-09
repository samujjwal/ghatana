/**
 * Stage Detail Page
 *
 * Detailed view of a stage configuration with phases and workflow mappings.
 *
 * @doc.type page
 * @doc.purpose Stage detail view
 * @doc.layer product
 */

import { useParams, useNavigate } from 'react-router';
import { GitBranch, ArrowRight, AlertCircle } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { useStages, usePhases } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Mock stage data with complete information
interface MockStage {
    id: string;
    name: string;
    description: string;
    order: number;
    phases: string[];
    color: string;
    icon: string;
    automationLevel: number;
    avgDuration: string;
    successRate: number;
    activeWorkItems: number;
    completedWorkItems: number;
    gates: {
        name: string;
        type: 'manual' | 'automated';
        required: boolean;
    }[];
    createdAt: string;
    updatedAt: string;
}

const mockStages: MockStage[] = [
    {
        id: 'stage-plan',
        name: 'Plan',
        description: 'Planning and requirements gathering phase including story creation and sprint planning',
        order: 1,
        phases: ['PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY', 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN'],
        color: 'blue',
        icon: '📋',
        automationLevel: 45,
        avgDuration: '3 days',
        successRate: 92,
        activeWorkItems: 12,
        completedWorkItems: 156,
        gates: [
            { name: 'Requirements Review', type: 'manual', required: true },
            { name: 'Design Approval', type: 'manual', required: true },
        ],
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'stage-develop',
        name: 'Develop',
        description: 'Development and coding phase including implementation and unit testing',
        order: 2,
        phases: ['PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE'],
        color: 'green',
        icon: '💻',
        automationLevel: 78,
        avgDuration: '5 days',
        successRate: 88,
        activeWorkItems: 24,
        completedWorkItems: 312,
        gates: [
            { name: 'Code Review', type: 'manual', required: true },
            { name: 'Unit Tests Pass', type: 'automated', required: true },
            { name: 'Lint Check', type: 'automated', required: true },
        ],
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'stage-test',
        name: 'Test',
        description: 'Testing phase including integration testing, QA, and security scanning',
        order: 3,
        phases: ['PRODUCT_LIFECYCLE_PHASE_VALIDATE'],
        color: 'yellow',
        icon: '🧪',
        automationLevel: 85,
        avgDuration: '2 days',
        successRate: 94,
        activeWorkItems: 8,
        completedWorkItems: 289,
        gates: [
            { name: 'Integration Tests', type: 'automated', required: true },
            { name: 'Security Scan', type: 'automated', required: true },
            { name: 'QA Sign-off', type: 'manual', required: true },
        ],
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
    {
        id: 'stage-deploy',
        name: 'Deploy',
        description: 'Deployment phase including staging, production deployment, and monitoring',
        order: 4,
        phases: ['PRODUCT_LIFECYCLE_PHASE_RELEASE', 'PRODUCT_LIFECYCLE_PHASE_OPERATE'],
        color: 'purple',
        icon: '🚀',
        automationLevel: 92,
        avgDuration: '4 hours',
        successRate: 97,
        activeWorkItems: 3,
        completedWorkItems: 245,
        gates: [
            { name: 'Staging Verification', type: 'automated', required: true },
            { name: 'Production Approval', type: 'manual', required: true },
            { name: 'Health Check', type: 'automated', required: true },
        ],
        createdAt: '2023-01-01T00:00:00Z',
        updatedAt: '2024-01-15T00:00:00Z',
    },
];

function StageGates({ gates }: { gates: MockStage['gates'] }) {
    return (
        <div className="space-y-3">
            {gates.map((gate, i) => (
                <div
                    key={i}
                    className={cn(
                        'flex items-center justify-between p-3 rounded-lg border',
                        'bg-gray-50 dark:bg-gray-800 border-gray-200 dark:border-gray-700'
                    )}
                >
                    <div className="flex items-center gap-3">
                        <span className="text-lg">
                            {gate.type === 'automated' ? '🤖' : '👤'}
                        </span>
                        <div>
                            <span className="font-medium text-gray-900 dark:text-gray-100">
                                {gate.name}
                            </span>
                            <span className={cn(
                                'ml-2 text-xs px-2 py-0.5 rounded',
                                gate.type === 'automated'
                                    ? 'bg-blue-100 dark:bg-blue-900/50 text-blue-700 dark:text-blue-300'
                                    : 'bg-purple-100 dark:bg-purple-900/50 text-purple-700 dark:text-purple-300'
                            )}>
                                {gate.type}
                            </span>
                        </div>
                    </div>
                    {gate.required && (
                        <span className="text-xs font-medium text-red-600 dark:text-red-400">
                            Required
                        </span>
                    )}
                </div>
            ))}
        </div>
    );
}

function StagePipeline({ stages, currentStage }: { stages: MockStage[]; currentStage: string }) {
    return (
        <div className="flex items-center gap-2 overflow-x-auto pb-2">
            {stages.sort((a, b) => a.order - b.order).map((stage, i) => (
                <div key={stage.id} className="flex items-center">
                    <div className={cn(
                        'flex items-center gap-2 px-4 py-2 rounded-lg border-2 transition-all',
                        stage.id === currentStage
                            ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/30'
                            : 'border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800'
                    )}>
                        <span className="text-xl">{stage.icon}</span>
                        <span className={cn(
                            'font-medium',
                            stage.id === currentStage
                                ? 'text-blue-700 dark:text-blue-300'
                                : 'text-gray-700 dark:text-gray-300'
                        )}>
                            {stage.name}
                        </span>
                    </div>
                    {i < stages.length - 1 && (
                        <ArrowRight className="w-5 h-5 text-gray-400 mx-2 flex-shrink-0" />
                    )}
                </div>
            ))}
        </div>
    );
}

export function StageDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const { data: stages, isLoading } = useStages();
    const { data: phases } = usePhases();

    // Find stage from API or mock data
    const stage = stages?.find(s => s.stage === id) || mockStages.find(s => s.id === id);
    const mockData = mockStages.find(s => s.id === id);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600" />
            </div>
        );
    }

    if (!stage && !mockData) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Stage Not Found</h2>
                <p className="text-gray-500 mt-2">The stage "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/config/stages')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Stages
                </button>
            </div>
        );
    }

    const displayData = mockData || { id: id || '', name: id || '', description: '', phases: (stage as any)?.phases || [] };

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'pipeline',
            title: 'Stage Pipeline',
            description: 'Position in the DevSecOps lifecycle',
            content: <StagePipeline stages={mockStages} currentStage={displayData.id} />,
        },
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Name', value: displayData.name },
                { key: 'order', label: 'Order', value: mockData?.order },
                { key: 'automationLevel', label: 'Automation Level', value: mockData ? `${mockData.automationLevel}%` : undefined },
                { key: 'avgDuration', label: 'Average Duration', value: mockData?.avgDuration },
                { key: 'successRate', label: 'Success Rate', value: mockData ? `${mockData.successRate}%` : undefined },
            ],
        },
        {
            id: 'workItems',
            title: 'Work Items',
            fields: [
                { key: 'active', label: 'Active Work Items', value: mockData?.activeWorkItems },
                { key: 'completed', label: 'Completed Work Items', value: mockData?.completedWorkItems },
            ],
        },
        {
            id: 'gates',
            title: 'Quality Gates',
            description: 'Gates that must pass before moving to the next stage',
            content: mockData?.gates ? (
                <StageGates gates={mockData.gates} />
            ) : (
                <p className="text-gray-500">No gates configured</p>
            ),
        },
        {
            id: 'phases',
            title: 'Associated Phases',
            description: 'Product lifecycle phases mapped to this stage',
            content: (
                <div className="flex flex-wrap gap-2">
                    {(mockData?.phases || displayData.phases || []).map((phaseId: string) => {
                        const phase = phases?.find(p => p.id === phaseId);
                        return (
                            <span
                                key={phaseId}
                                className="inline-flex items-center px-3 py-1.5 rounded-lg text-sm font-medium bg-green-100 dark:bg-green-900/50 text-green-700 dark:text-green-300"
                            >
                                {phase?.display_name || phaseId}
                            </span>
                        );
                    })}
                </div>
            ),
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Stage ID', value: displayData.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: mockData?.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: mockData?.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    // Add related phases
    (mockData?.phases || []).forEach(phaseId => {
        const phase = phases?.find(p => p.id === phaseId);
        relatedEntities.push({
            id: phaseId,
            name: phase?.display_name || phaseId,
            type: 'Phase',
            href: `/config/phases/${phaseId}`,
            status: 'active',
        });
    });

    return (
        <EntityDetailPage
            entityType="Stage"
            entityId={displayData.id}
            title={displayData.name}
            subtitle={mockData?.icon}
            description={mockData?.description}
            icon={<GitBranch className="w-6 h-6" />}
            backHref="/config/stages"
            backLabel="Back to Stages"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit stage')}
        />
    );
}

export default StageDetailPage;
