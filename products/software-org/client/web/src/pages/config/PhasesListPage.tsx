/**
 * Phases List Page
 *
 * @doc.type page
 * @doc.purpose Phases list and visualization
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
import { Layers, Users, Grid3x3, List, ArrowRight } from 'lucide-react';
import { usePhases } from '@/hooks/useConfig';
import type { PhaseConfig } from '@/services/api/configApi';

interface PhaseCardProps {
    phase: PhaseConfig;
    viewMode: 'grid' | 'list';
}

function PhaseCard({ phase, viewMode }: PhaseCardProps) {
    const personas = phase.personas || [];
    const categoryStyle = getCategoryStyle('green');

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/phases/${phase.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Layers className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {phase.display_name || phase.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400 line-clamp-1">
                                {phase.description || phase.id}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        <span className="flex items-center text-sm text-slate-500">
                            <Users className="h-4 w-4 mr-1" />
                            {personas.length} personas
                        </span>
                        <ArrowRight className="h-5 w-5 text-slate-400" />
                    </div>
                </div>
            </Link>
        );
    }

    return (
        <Link
            to={`/config/phases/${phase.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Layers className="h-6 w-6" />
                </div>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                {phase.display_name || phase.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {phase.description || 'No description'}
            </p>

            {personas.length > 0 && (
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-2">
                        <Users className="h-3 w-3 mr-1" /> Assigned Personas
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {personas.slice(0, 3).map((persona, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400 rounded">
                                {persona}
                            </span>
                        ))}
                        {personas.length > 3 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{personas.length - 3}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </Link>
    );
}

export function PhasesListPage() {
    const { data: phases, isLoading } = usePhases();
    const [searchQuery, setSearchQuery] = useState('');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const filteredPhases = useMemo(() => {
        if (!phases) return [];
        return phases.filter((phase) => {
            const matchesSearch = !searchQuery ||
                phase.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
                phase.display_name?.toLowerCase().includes(searchQuery.toLowerCase()) ||
                phase.description?.toLowerCase().includes(searchQuery.toLowerCase());
            return matchesSearch;
        });
    }, [phases, searchQuery]);

    if (isLoading) {
        return (
            <PageLayout title="Phases" subtitle="Lifecycle phases">
                <LoadingState message="Loading phases..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Phases" subtitle="Lifecycle phases">
            <PageHeader
                title="Phases"
                subtitle="View and manage lifecycle phases"
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
                searchPlaceholder="Search phases..."
            />

            <PageSection title={`${filteredPhases.length} Phases`}>
                {filteredPhases.length === 0 ? (
                    <EmptyState
                        title="No phases found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No phases configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredPhases.map((phase) => (
                            <PhaseCard key={phase.id} phase={phase} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default PhasesListPage;
