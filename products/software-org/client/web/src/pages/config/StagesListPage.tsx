/**
 * Stages List Page
 *
 * @doc.type page
 * @doc.purpose Stages list and visualization
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
import { GitBranch, Layers, Grid3x3, List, ArrowRight } from 'lucide-react';
import { useStages } from '@/hooks/useConfig';
import type { StageMapping } from '@/services/api/configApi';

interface StageCardProps {
    stage: StageMapping;
    viewMode: 'grid' | 'list';
}

function StageCard({ stage, viewMode }: StageCardProps) {
    const phases = stage.phases || [];
    const categoryStyle = getCategoryStyle('orange');

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/stages/${stage.stage}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <GitBranch className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {stage.stage}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {phases.length} phases mapped
                            </p>
                        </div>
                    </div>
                    <ArrowRight className="h-5 w-5 text-slate-400" />
                </div>
            </Link>
        );
    }

    return (
        <Link
            to={`/config/stages/${stage.stage}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <GitBranch className="h-6 w-6" />
                </div>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-4">
                {stage.stage}
            </h3>

            {phases.length > 0 && (
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-2">
                        <Layers className="h-3 w-3 mr-1" /> Mapped Phases
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {phases.slice(0, 3).map((phase, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400 rounded">
                                {phase.split('_').pop()}
                            </span>
                        ))}
                        {phases.length > 3 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{phases.length - 3}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </Link>
    );
}

export function StagesListPage() {
    const { data: stages, isLoading } = useStages();
    const [searchQuery, setSearchQuery] = useState('');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const filteredStages = useMemo(() => {
        if (!stages) return [];
        return stages.filter((stage) => {
            const matchesSearch = !searchQuery ||
                stage.stage.toLowerCase().includes(searchQuery.toLowerCase());
            return matchesSearch;
        });
    }, [stages, searchQuery]);

    if (isLoading) {
        return (
            <PageLayout title="Stages" subtitle="Stage mappings">
                <LoadingState message="Loading stages..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Stages" subtitle="Stage mappings">
            <PageHeader
                title="Stages"
                subtitle="View and manage stage-to-phase mappings"
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
                searchPlaceholder="Search stages..."
            />

            <PageSection title={`${filteredStages.length} Stages`}>
                {filteredStages.length === 0 ? (
                    <EmptyState
                        title="No stages found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No stages configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredStages.map((stage) => (
                            <StageCard key={stage.stage} stage={stage} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default StagesListPage;
