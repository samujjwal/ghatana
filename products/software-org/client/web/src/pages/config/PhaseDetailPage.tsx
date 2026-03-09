/**
 * Phase Detail Page
 *
 * Detailed view of a product lifecycle phase with associated personas, stages, and workflows.
 *
 * @doc.type page
 * @doc.purpose Phase detail view
 * @doc.layer product
 */

import { useParams, useNavigate } from 'react-router';
import { Layers, GitBranch, AlertCircle, Settings, ArrowRight } from 'lucide-react';
import { EntityDetailPage, type EntitySection, type RelatedEntity } from '@/shared/components/EntityDetailPage';
import { usePhases, usePersonas, useStages } from '@/hooks/useConfig';
import { components, typography, cn } from '@/lib/theme';

// Extended phase data with complete information
interface ExtendedPhase {
    id: string;
    display_name: string;
    description: string;
    personas: string[];
    order: number;
    duration: string;
    objectives: string[];
    deliverables: string[];
    metrics: {
        avgDuration: string;
        completionRate: number;
        activeProjects: number;
    };
    gates: {
        name: string;
        criteria: string[];
        required: boolean;
    }[];
    status: 'active' | 'inactive';
    createdAt: string;
    updatedAt: string;
}

const extendedPhases: ExtendedPhase[] = [
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_PROBLEM_DISCOVERY',
        display_name: 'Problem Discovery',
        description: 'Identify and validate customer problems through research, interviews, and data analysis. This phase ensures we are solving the right problems before investing in solutions.',
        personas: ['product_manager'],
        order: 1,
        duration: '1-2 weeks',
        objectives: [
            'Identify customer pain points',
            'Validate problem significance',
            'Understand market opportunity',
            'Define success criteria',
        ],
        deliverables: [
            'Problem statement document',
            'Customer interview summaries',
            'Market analysis report',
            'Opportunity assessment',
        ],
        metrics: {
            avgDuration: '8 days',
            completionRate: 92,
            activeProjects: 5,
        },
        gates: [
            {
                name: 'Problem Validation Gate',
                criteria: ['Problem validated with 5+ customers', 'Market size estimated', 'Success metrics defined'],
                required: true,
            },
        ],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_SOLUTION_DESIGN',
        display_name: 'Solution Design',
        description: 'Design solutions to validated problems through ideation, prototyping, and user testing. Focus on creating the right solution architecture and user experience.',
        personas: ['product_manager', 'backend_engineer'],
        order: 2,
        duration: '1-3 weeks',
        objectives: [
            'Generate solution alternatives',
            'Create prototypes and mockups',
            'Validate solution with users',
            'Define technical architecture',
        ],
        deliverables: [
            'Solution design document',
            'UI/UX mockups',
            'Technical architecture diagram',
            'User validation results',
        ],
        metrics: {
            avgDuration: '12 days',
            completionRate: 88,
            activeProjects: 8,
        },
        gates: [
            {
                name: 'Design Review Gate',
                criteria: ['Design approved by stakeholders', 'Technical feasibility confirmed', 'User testing completed'],
                required: true,
            },
        ],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_BUILD_AND_INTEGRATE',
        display_name: 'Build & Integrate',
        description: 'Implement and integrate solutions according to the design specifications. Focus on code quality, testing, and continuous integration.',
        personas: ['backend_engineer'],
        order: 3,
        duration: '2-6 weeks',
        objectives: [
            'Implement features according to specs',
            'Write comprehensive tests',
            'Integrate with existing systems',
            'Maintain code quality standards',
        ],
        deliverables: [
            'Working code in repository',
            'Unit and integration tests',
            'API documentation',
            'Code review approvals',
        ],
        metrics: {
            avgDuration: '21 days',
            completionRate: 95,
            activeProjects: 12,
        },
        gates: [
            {
                name: 'Code Review Gate',
                criteria: ['All tests passing', 'Code review approved', 'Documentation complete'],
                required: true,
            },
        ],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_VALIDATE',
        display_name: 'Validate',
        description: 'Test and validate implementations through QA, performance testing, and security scanning. Ensure the solution meets quality standards.',
        personas: ['backend_engineer', 'sre'],
        order: 4,
        duration: '1-2 weeks',
        objectives: [
            'Execute QA test plans',
            'Perform load and performance testing',
            'Complete security scanning',
            'Validate against acceptance criteria',
        ],
        deliverables: [
            'QA test results',
            'Performance test report',
            'Security scan report',
            'Sign-off from stakeholders',
        ],
        metrics: {
            avgDuration: '7 days',
            completionRate: 97,
            activeProjects: 6,
        },
        gates: [
            {
                name: 'QA Gate',
                criteria: ['All critical bugs resolved', 'Performance benchmarks met', 'Security scan passed'],
                required: true,
            },
        ],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_RELEASE',
        display_name: 'Release',
        description: 'Deploy to production environments with proper rollout strategies. Manage feature flags, canary deployments, and rollback procedures.',
        personas: ['sre'],
        order: 5,
        duration: '1-3 days',
        objectives: [
            'Execute deployment plan',
            'Monitor rollout metrics',
            'Manage feature flags',
            'Prepare rollback procedures',
        ],
        deliverables: [
            'Deployment checklist completed',
            'Rollout monitoring dashboard',
            'Rollback plan documented',
            'Release notes published',
        ],
        metrics: {
            avgDuration: '2 days',
            completionRate: 99,
            activeProjects: 3,
        },
        gates: [
            {
                name: 'Release Gate',
                criteria: ['Deployment successful', 'No critical errors in monitoring', 'Stakeholder sign-off'],
                required: true,
            },
        ],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
    {
        id: 'PRODUCT_LIFECYCLE_PHASE_OPERATE',
        display_name: 'Operate',
        description: 'Monitor and maintain production systems. Handle incidents, optimize performance, and gather feedback for future improvements.',
        personas: ['sre'],
        order: 6,
        duration: 'Ongoing',
        objectives: [
            'Monitor system health',
            'Respond to incidents',
            'Optimize performance',
            'Gather user feedback',
        ],
        deliverables: [
            'SLO/SLI dashboards',
            'Incident reports',
            'Performance optimization recommendations',
            'User feedback summaries',
        ],
        metrics: {
            avgDuration: 'Ongoing',
            completionRate: 100,
            activeProjects: 15,
        },
        gates: [],
        status: 'active',
        createdAt: '2022-01-01T00:00:00Z',
        updatedAt: '2024-01-10T00:00:00Z',
    },
];

function ObjectivesList({ objectives }: { objectives: string[] }) {
    return (
        <ul className="space-y-2">
            {objectives.map((objective, index) => (
                <li key={index} className="flex items-start gap-3">
                    <span className="flex-shrink-0 w-6 h-6 rounded-full bg-indigo-100 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-xs font-bold">
                        {index + 1}
                    </span>
                    <span className="text-gray-700 dark:text-gray-300">{objective}</span>
                </li>
            ))}
        </ul>
    );
}

function DeliverablesGrid({ deliverables }: { deliverables: string[] }) {
    return (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {deliverables.map((deliverable, index) => (
                <div
                    key={index}
                    className="flex items-center gap-3 p-3 rounded-lg bg-gray-50 dark:bg-slate-800 border border-gray-200 dark:border-slate-700"
                >
                    <ArrowRight className="w-4 h-4 text-gray-400 flex-shrink-0" />
                    <span className="text-sm text-gray-700 dark:text-gray-300">{deliverable}</span>
                </div>
            ))}
        </div>
    );
}

function GatesSection({ gates }: { gates: ExtendedPhase['gates'] }) {
    if (!gates || gates.length === 0) {
        return <p className="text-gray-500 dark:text-gray-400">No gates defined for this phase</p>;
    }

    return (
        <div className="space-y-4">
            {gates.map((gate, index) => (
                <div
                    key={index}
                    className="p-4 rounded-lg bg-amber-50 dark:bg-amber-900/30 border border-amber-200 dark:border-amber-800"
                >
                    <div className="flex items-center gap-2 mb-3">
                        <GitBranch className="w-5 h-5 text-amber-600 dark:text-amber-400" />
                        <span className="font-semibold text-amber-800 dark:text-amber-200">{gate.name}</span>
                        {gate.required && (
                            <span className="px-2 py-0.5 text-xs font-medium bg-amber-200 dark:bg-amber-800 text-amber-800 dark:text-amber-200 rounded">
                                Required
                            </span>
                        )}
                    </div>
                    <ul className="space-y-1">
                        {gate.criteria.map((criterion, idx) => (
                            <li key={idx} className="flex items-center gap-2 text-sm text-amber-700 dark:text-amber-300">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-500" />
                                {criterion}
                            </li>
                        ))}
                    </ul>
                </div>
            ))}
        </div>
    );
}

function MetricsGrid({ metrics }: { metrics: ExtendedPhase['metrics'] }) {
    return (
        <div className="grid grid-cols-3 gap-4">
            <div className="p-4 rounded-lg bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-800">
                <div className="text-sm font-medium text-blue-700 dark:text-blue-300 mb-1">Avg Duration</div>
                <div className="text-xl font-bold text-blue-900 dark:text-blue-100">{metrics.avgDuration}</div>
            </div>
            <div className="p-4 rounded-lg bg-emerald-50 dark:bg-emerald-900/30 border border-emerald-200 dark:border-emerald-800">
                <div className="text-sm font-medium text-emerald-700 dark:text-emerald-300 mb-1">Completion Rate</div>
                <div className="text-xl font-bold text-emerald-900 dark:text-emerald-100">{metrics.completionRate}%</div>
            </div>
            <div className="p-4 rounded-lg bg-purple-50 dark:bg-purple-900/30 border border-purple-200 dark:border-purple-800">
                <div className="text-sm font-medium text-purple-700 dark:text-purple-300 mb-1">Active Projects</div>
                <div className="text-xl font-bold text-purple-900 dark:text-purple-100">{metrics.activeProjects}</div>
            </div>
        </div>
    );
}

export function PhaseDetailPage() {
    const { id } = useParams<{ id: string }>();
    const navigate = useNavigate();

    const { data: phases, isLoading } = usePhases();
    const { data: personas } = usePersonas();
    const { data: stages } = useStages();

    // Find phase from API or extended data
    const phase = phases?.find(p => p.id === id);
    const extendedData = extendedPhases.find(p => p.id === id);

    if (isLoading) {
        return (
            <div className="flex items-center justify-center h-64">
                <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-indigo-600" />
            </div>
        );
    }

    if (!phase && !extendedData) {
        return (
            <div className="p-8 text-center">
                <AlertCircle className="w-12 h-12 text-gray-400 mx-auto mb-4" />
                <h2 className={typography.h3}>Phase Not Found</h2>
                <p className="text-gray-500 mt-2">The phase "{id}" could not be found.</p>
                <button
                    onClick={() => navigate('/phases')}
                    className={cn(components.button.primary, 'mt-4')}
                >
                    Back to Phases
                </button>
            </div>
        );
    }

    const displayData = extendedData || {
        ...phase,
        description: phase?.description || 'No description available',
        order: 0,
        duration: 'Unknown',
        objectives: [],
        deliverables: [],
        metrics: { avgDuration: 'N/A', completionRate: 0, activeProjects: 0 },
        gates: [],
        status: 'active' as const,
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    };

    // Build sections
    const sections: EntitySection[] = [
        {
            id: 'overview',
            title: 'Overview',
            fields: [
                { key: 'name', label: 'Phase Name', value: displayData.display_name },
                { key: 'order', label: 'Order', value: `Phase ${displayData.order}`, type: 'badge' },
                { key: 'duration', label: 'Typical Duration', value: displayData.duration },
                { key: 'status', label: 'Status', value: displayData.status, type: 'badge' },
            ],
        },
        {
            id: 'metrics',
            title: 'Phase Metrics',
            content: extendedData?.metrics ? (
                <MetricsGrid metrics={extendedData.metrics} />
            ) : (
                <p className="text-gray-500">No metrics available</p>
            ),
        },
        {
            id: 'objectives',
            title: 'Objectives',
            content: extendedData?.objectives?.length ? (
                <ObjectivesList objectives={extendedData.objectives} />
            ) : (
                <p className="text-gray-500">No objectives defined</p>
            ),
        },
        {
            id: 'deliverables',
            title: 'Deliverables',
            content: extendedData?.deliverables?.length ? (
                <DeliverablesGrid deliverables={extendedData.deliverables} />
            ) : (
                <p className="text-gray-500">No deliverables defined</p>
            ),
        },
        {
            id: 'gates',
            title: 'Quality Gates',
            content: <GatesSection gates={extendedData?.gates || []} />,
        },
        {
            id: 'metadata',
            title: 'Metadata',
            fields: [
                { key: 'id', label: 'Phase ID', value: displayData.id, type: 'code' },
                { key: 'createdAt', label: 'Created', value: displayData.createdAt, type: 'date' },
                { key: 'updatedAt', label: 'Last Updated', value: displayData.updatedAt, type: 'date' },
            ],
        },
    ];

    // Build related entities
    const relatedEntities: RelatedEntity[] = [];

    // Add related personas
    const phasePersonas = displayData.personas || [];
    phasePersonas.forEach(personaId => {
        const persona = personas?.find(p => p.id === personaId);
        if (persona) {
            relatedEntities.push({
                id: persona.id,
                name: persona.display_name,
                type: 'Persona',
                href: `/personas/${persona.id}`,
                status: 'active',
            });
        }
    });

    // Add related stages
    const phaseStages = stages?.filter(s => s.phases?.includes(id || '')) || [];
    phaseStages.forEach(stage => {
        relatedEntities.push({
            id: stage.stage,
            name: stage.stage,
            type: 'Stage',
            href: `/stages/${stage.stage}`,
            status: 'active',
        });
    });

    return (
        <EntityDetailPage
            entityType="Phase"
            entityId={displayData.id || id || ''}
            title={displayData.display_name || 'Unknown Phase'}
            subtitle={`Phase ${displayData.order} • ${displayData.duration}`}
            description={displayData.description}
            status={displayData.status}
            icon={<Layers className="w-6 h-6" />}
            backHref="/phases"
            backLabel="Back to Phases"
            sections={sections}
            relatedEntities={relatedEntities}
            onEdit={() => console.log('Edit phase')}
            actions={[
                {
                    id: 'settings',
                    label: 'Settings',
                    icon: <Settings className="w-4 h-4" />,
                    onClick: () => console.log('Phase settings'),
                    variant: 'secondary',
                },
            ]}
        />
    );
}

export default PhaseDetailPage;
