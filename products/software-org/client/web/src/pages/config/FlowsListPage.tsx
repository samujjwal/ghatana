/**
 * Flows List Page
 *
 * @doc.type page
 * @doc.purpose Flows list and visualization
 * @doc.layer product
 * @doc.pattern List Page
 */

import { useState, useMemo } from 'react';
import { Link } from 'react-router';
import {
    PageLayout,
    PageHeader,
    PageFilters,
    PageSection,
    PageGrid,
    LoadingState,
    EmptyState,
} from '@/components/layouts/page';
import { CARD_STYLES, getCategoryStyle } from '@/components/layouts/page/theme';
import { Workflow, ArrowRight, Grid3x3, List, Layers } from 'lucide-react';
import { useFlows } from '@/hooks/useConfig';
import type { FlowConfig } from '@/services/api/configApi';

interface FlowCardProps {
    flow: FlowConfig;
    viewMode: 'grid' | 'list';
}

function FlowCard({ flow, viewMode }: FlowCardProps) {
    const categoryStyle = getCategoryStyle('indigo');
    const steps = flow.steps || [];

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/flows/${flow.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Workflow className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {flow.name || flow.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {steps.length} steps
                            </p>
                        </div>
                    </div>
                    <ArrowRight className="h-5 w-5 text-slate-400" />
                </div>
                {flow.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {flow.description}
                    </p>
                )}
            </Link>
        );
    }

    return (
        <Link
            to={`/config/flows/${flow.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Workflow className="h-6 w-6" />
                </div>
                <span className="px-2 py-1 text-xs font-medium rounded bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400">
                    {flow.type || 'flow'}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                {flow.name || flow.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {flow.description || 'No description'}
            </p>

            {steps.length > 0 && (
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-2">
                        <Layers className="h-3 w-3 mr-1" /> Steps
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {steps.slice(0, 4).map((step, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-indigo-100 text-indigo-800 dark:bg-indigo-900/30 dark:text-indigo-400 rounded">
                                {typeof step === 'string' ? step : step.name || step.id || `Step ${idx + 1}`}
                            </span>
                        ))}
                        {steps.length > 4 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{steps.length - 4}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </Link>
    );
}

export function FlowsListPage() {
    const { data: flows, isLoading } = useFlows();
    const [searchQuery, setSearchQuery] = useState('');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const filteredFlows = useMemo(() => {
        if (!flows) return [];
        return flows.filter((flow) => {
            const matchesSearch = !searchQuery ||
                (flow.name || flow.id).toLowerCase().includes(searchQuery.toLowerCase()) ||
                flow.description?.toLowerCase().includes(searchQuery.toLowerCase());
            return matchesSearch;
        });
    }, [flows, searchQuery]);

    if (isLoading) {
        return (
            <PageLayout title="Flows" subtitle="DevSecOps flows">
                <LoadingState message="Loading flows..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Flows" subtitle="DevSecOps flows">
            <PageHeader
                title="Flows"
                subtitle="View and manage DevSecOps flows"
                backLink="/config"
                actions={
                    <div className="flex items-center space-x-2">
                        <button
                            onClick={() => setViewMode('grid')}
                            className={`p-2 rounded ${viewMode === 'grid' ? 'bg-primary-100 text-primary-600' : 'text-slate-400 hover:text-slate-600'}`}
                        >
                            <Grid3x3 className="h-5 w-5" />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            className={`p-2 rounded ${viewMode === 'list' ? 'bg-primary-100 text-primary-600' : 'text-slate-400 hover:text-slate-600'}`}
                        >
                            <List className="h-5 w-5" />
                        </button>
                    </div>
                }
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search flows..."
            />

            <PageSection title={`${filteredFlows.length} Flows`}>
                {filteredFlows.length === 0 ? (
                    <EmptyState
                        title="No flows found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No flows configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredFlows.map((flow) => (
                            <FlowCard key={flow.id} flow={flow} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default FlowsListPage;
