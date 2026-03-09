/**
 * Workflows List Page
 *
 * <p><b>Purpose</b><br>
 * Display all workflows from YAML configuration with visual flow diagrams,
 * filtering, and detailed information about triggers, steps, and dependencies.
 *
 * <p><b>Features</b><br>
 * - Visual workflow cards with step indicators
 * - Filter by trigger type, status
 * - Search across workflows
 * - Flow diagram visualization
 * - Navigate to detail pages
 *
 * @doc.type page
 * @doc.purpose Workflows list and visualization
 * @doc.layer product
 * @doc.pattern List Page
 */

import { useState, useMemo } from 'react';
import { Link } from 'react-router';
import {
    PageLayout,
    PageHeader,
    PageFilters,
    PageGrid,
    LoadingState,
    EmptyState,
} from '@/components/layouts/page';
import { CARD_STYLES } from '@/components/layouts/page/theme';
import {
    Workflow,
    PlayCircle,
    Clock,
    GitBranch,
    Users,
    Zap,
    ListTree,
    LucideIcon,
} from 'lucide-react';
import { useConfigWorkflows } from '@/hooks/useConfig';
import type { WorkflowConfig, WorkflowStep } from '@/services/api/configApi';

interface WorkflowCardProps {
    workflow: WorkflowConfig;
}

function WorkflowCard({ workflow }: WorkflowCardProps) {
    const steps = workflow.steps || [];
    const trigger = workflow.trigger;
    const triggerIcons: Record<string, LucideIcon> = {
        manual: PlayCircle,
        scheduled: Clock,
        event: Zap,
        webhook: GitBranch,
    };
    const triggerType = trigger?.event || 'manual';
    const TriggerIcon = triggerIcons[triggerType] || PlayCircle;

    return (
        <Link
            to={`/config/workflows/${workflow.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center space-x-3 flex-1">
                    <div className="flex-shrink-0 bg-green-100 dark:bg-green-900/30 rounded-lg p-3">
                        <Workflow className="h-6 w-6 text-green-600 dark:text-green-400" />
                    </div>
                    <div className="min-w-0 flex-1">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-white truncate">
                            {workflow.name}
                        </h3>
                        <p className="text-sm text-slate-600 dark:text-slate-400 mt-1">
                            {workflow.steps.length} steps
                        </p>
                    </div>
                </div>
            </div>

            {/* Trigger Info */}
            <div className="flex items-center text-sm text-slate-600 dark:text-slate-400 mb-4">
                <TriggerIcon className="h-4 w-4 mr-2 text-slate-400" />
                <span className="font-medium">Trigger:</span>
                <span className="ml-2 capitalize">{triggerType}</span>
            </div>

            {/* Steps Progress Bar */}
            {steps.length > 0 && (
                <div>
                    <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 mb-2">
                        <span className="flex items-center">
                            <ListTree className="h-3 w-3 mr-1" />
                            {steps.length} Steps
                        </span>
                    </div>
                    <div className="flex space-x-1">
                        {steps.slice(0, 8).map((step: WorkflowStep, idx: number) => (
                            <div
                                key={idx}
                                className="flex-1 h-2 bg-green-200 dark:bg-green-800 rounded-full relative group"
                                title={step.description || step.action || step.id}
                            >
                                <div className="absolute bottom-full mb-2 hidden group-hover:block bg-slate-900 text-white text-xs px-2 py-1 rounded whitespace-nowrap z-10">
                                    {step.description || step.action || step.id}
                                </div>
                            </div>
                        ))}
                        {steps.length > 8 && (
                            <div className="flex items-center text-xs text-slate-500">
                                +{steps.length - 8}
                            </div>
                        )}
                    </div>
                </div>
            )}

            {/* Involved Agents */}
            {workflow.steps.filter(s => s.agent).length > 0 && (
                <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-700">
                    <div className="flex items-center text-xs text-slate-500 dark:text-slate-400">
                        <Users className="h-3 w-3 mr-1" />
                        <span>{workflow.steps.filter(s => s.agent).length} agent steps</span>
                    </div>
                </div>
            )}
        </Link>
    );
}

export function WorkflowsListPage() {
    const { data: workflows, isLoading } = useConfigWorkflows();
    const [searchQuery, setSearchQuery] = useState('');
    const [triggerFilter, setTriggerFilter] = useState<string>('all');

    // Extract unique trigger types
    const triggerTypes = useMemo(() => {
        if (!workflows) return [];
        const types = new Set(
            workflows
                .map((w) => w.trigger?.event || 'manual')
                .filter(Boolean)
        );
        return Array.from(types).sort();
    }, [workflows]);

    // Build category list for filter
    const categoryList = useMemo(() => {
        return [
            { id: 'all', label: 'All Triggers' },
            ...triggerTypes.map(type => ({
                id: type,
                label: type.charAt(0).toUpperCase() + type.slice(1)
            }))
        ];
    }, [triggerTypes]);

    // Filter workflows
    const filteredWorkflows = useMemo(() => {
        if (!workflows) return [];
        return workflows.filter((workflow) => {
            const matchesSearch =
                !searchQuery ||
                workflow.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                workflow.id.toLowerCase().includes(searchQuery.toLowerCase());

            const triggerType = workflow.trigger?.event || 'manual';
            const matchesTrigger =
                triggerFilter === 'all' || triggerType === triggerFilter;

            return matchesSearch && matchesTrigger;
        });
    }, [workflows, searchQuery, triggerFilter]);

    if (isLoading) {
        return (
            <PageLayout title="Workflows">
                <LoadingState message="Loading workflows..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Workflows">
            <PageHeader
                title="Workflows"
                subtitle={`${workflows?.length || 0} workflows configured`}
                icon={Workflow}
                iconBg="bg-green-100 dark:bg-green-900/30"
                iconColor="text-green-600 dark:text-green-400"
                backLink="/config"
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search workflows by name or description..."
                categories={categoryList}
                selectedCategory={triggerFilter}
                onCategoryChange={setTriggerFilter}
            />

            {filteredWorkflows.length === 0 ? (
                <EmptyState
                    icon={Workflow}
                    title="No workflows found"
                    description={searchQuery || triggerFilter !== 'all'
                        ? "Try adjusting your search or filter criteria"
                        : "No workflows have been configured yet"
                    }
                />
            ) : (
                <PageGrid cols={{ sm: 1, md: 2, lg: 3 }}>
                    {filteredWorkflows.map((workflow) => (
                        <WorkflowCard key={workflow.id || workflow.name} workflow={workflow} />
                    ))}
                </PageGrid>
            )}
        </PageLayout>
    );
}

export default WorkflowsListPage;
