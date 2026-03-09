/**
 * KPIs List Page
 *
 * @doc.type page
 * @doc.purpose KPIs list and visualization
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
import { BarChart3, ArrowRight, Grid3x3, List, Target, TrendingUp, TrendingDown } from 'lucide-react';
import { useKpis } from '@/hooks/useConfig';
import type { KpiConfig } from '@/services/api/configApi';

interface KpiCardProps {
    kpi: KpiConfig;
    viewMode: 'grid' | 'list';
}

function KpiCard({ kpi, viewMode }: KpiCardProps) {
    const category = kpi.category || 'general';
    const categoryStyle = getCategoryStyle(category.toLowerCase());

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/kpis/${kpi.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <BarChart3 className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {kpi.name || kpi.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {category} • {kpi.unit || 'value'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        {kpi.target !== undefined && (
                            <span className="flex items-center text-sm text-slate-500">
                                <Target className="h-4 w-4 mr-1" />
                                Target: {kpi.target}
                            </span>
                        )}
                        <ArrowRight className="h-5 w-5 text-slate-400" />
                    </div>
                </div>
                {kpi.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {kpi.description}
                    </p>
                )}
            </Link>
        );
    }

    return (
        <Link
            to={`/config/kpis/${kpi.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <BarChart3 className="h-6 w-6" />
                </div>
                <span className={`px-2 py-1 text-xs font-medium rounded ${categoryStyle.bg} ${categoryStyle.text}`}>
                    {category}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                {kpi.name || kpi.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {kpi.description || 'No description'}
            </p>

            <div className="grid grid-cols-2 gap-4 text-sm">
                {kpi.target !== undefined && (
                    <div>
                        <div className="flex items-center text-xs text-slate-500 mb-1">
                            <Target className="h-3 w-3 mr-1" /> Target
                        </div>
                        <span className="text-slate-700 dark:text-slate-300 font-medium">
                            {kpi.target} {kpi.unit || ''}
                        </span>
                    </div>
                )}
                {kpi.trend && (
                    <div>
                        <div className="flex items-center text-xs text-slate-500 mb-1">
                            {kpi.trend === 'up' ? (
                                <TrendingUp className="h-3 w-3 mr-1 text-green-500" />
                            ) : (
                                <TrendingDown className="h-3 w-3 mr-1 text-red-500" />
                            )}
                            Trend
                        </div>
                        <span className={`font-medium ${kpi.trend === 'up' ? 'text-green-600' : 'text-red-600'}`}>
                            {kpi.trend === 'up' ? 'Improving' : 'Declining'}
                        </span>
                    </div>
                )}
            </div>

            {kpi.thresholds && (
                <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-700">
                    <div className="flex justify-between text-xs">
                        <span className="text-red-600">Warning: {kpi.thresholds.warning}</span>
                        <span className="text-red-700">Critical: {kpi.thresholds.critical}</span>
                    </div>
                </div>
            )}
        </Link>
    );
}

export function KpisListPage() {
    const { data: kpis, isLoading } = useKpis();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const categories = useMemo(() => {
        if (!kpis) return [];
        const categorySet = new Set(kpis.map((k) => k.category).filter((x): x is string => Boolean(x)));
        return Array.from(categorySet).sort();
    }, [kpis]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Categories' },
        ...categories.map(cat => ({ id: cat, label: cat }))
    ], [categories]);

    const filteredKpis = useMemo(() => {
        if (!kpis) return [];
        return kpis.filter((kpi) => {
            const matchesSearch = !searchQuery ||
                (kpi.name || kpi.id).toLowerCase().includes(searchQuery.toLowerCase()) ||
                kpi.description?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesCategory = selectedCategory === 'all' || kpi.category === selectedCategory;
            return matchesSearch && matchesCategory;
        });
    }, [kpis, searchQuery, selectedCategory]);

    if (isLoading) {
        return (
            <PageLayout title="KPIs" subtitle="Performance indicators">
                <LoadingState message="Loading KPIs..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="KPIs" subtitle="Performance indicators">
            <PageHeader
                title="KPIs"
                subtitle="View and manage performance indicators"
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
                searchPlaceholder="Search KPIs..."
                categories={categoryList}
                selectedCategory={selectedCategory}
                onCategoryChange={setSelectedCategory}
            />

            <PageSection title={`${filteredKpis.length} KPIs`}>
                {filteredKpis.length === 0 ? (
                    <EmptyState
                        title="No KPIs found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No KPIs configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredKpis.map((kpi) => (
                            <KpiCard key={kpi.id} kpi={kpi} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default KpisListPage;
