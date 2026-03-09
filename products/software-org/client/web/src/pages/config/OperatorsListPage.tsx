/**
 * Operators List Page
 *
 * @doc.type page
 * @doc.purpose Operators list and visualization
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
import { Bot, ArrowRight, Grid3x3, List, Zap, Database, ArrowRightLeft } from 'lucide-react';
import { useOperators } from '@/hooks/useConfig';
import type { OperatorConfig } from '@/services/api/configApi';

interface OperatorCardProps {
    operator: OperatorConfig;
    viewMode: 'grid' | 'list';
}

function OperatorCard({ operator, viewMode }: OperatorCardProps) {
    const domain = operator.domain || 'unknown';
    const categoryStyle = getCategoryStyle(domain.toLowerCase());
    const inputs = operator.inputs || [];
    const outputs = operator.outputs || [];
    const modes = operator.modes || [];

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/operators/${operator.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Bot className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {operator.name || operator.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {domain} • {modes.length} modes
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        <span className="flex items-center text-sm text-slate-500">
                            <ArrowRightLeft className="h-4 w-4 mr-1" />
                            {inputs.length} in / {outputs.length} out
                        </span>
                        <ArrowRight className="h-5 w-5 text-slate-400" />
                    </div>
                </div>
                {operator.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {operator.description}
                    </p>
                )}
            </Link>
        );
    }

    return (
        <Link
            to={`/config/operators/${operator.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Bot className="h-6 w-6" />
                </div>
                <span className={`px-2 py-1 text-xs font-medium rounded ${categoryStyle.bg} ${categoryStyle.text}`}>
                    {domain}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-2">
                {operator.name || operator.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {operator.description || 'No description'}
            </p>

            <div className="grid grid-cols-2 gap-4 text-sm">
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-1">
                        <Database className="h-3 w-3 mr-1" /> Inputs
                    </div>
                    <span className="text-slate-700 dark:text-slate-300">{inputs.length} fields</span>
                </div>
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-1">
                        <ArrowRight className="h-3 w-3 mr-1" /> Outputs
                    </div>
                    <span className="text-slate-700 dark:text-slate-300">{outputs.length} fields</span>
                </div>
            </div>

            {modes.length > 0 && (
                <div className="mt-4">
                    <div className="flex items-center text-xs text-slate-500 mb-2">
                        <Zap className="h-3 w-3 mr-1" /> Modes
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {modes.slice(0, 3).map((mode, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-400 rounded">
                                {typeof mode === 'string' ? mode : mode.name || mode.id}
                            </span>
                        ))}
                        {modes.length > 3 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{modes.length - 3}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </Link>
    );
}

export function OperatorsListPage() {
    const { data: operators, isLoading } = useOperators();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedDomain, setSelectedDomain] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const domains = useMemo(() => {
        if (!operators) return [];
        const domainSet = new Set(operators.map((o) => o.domain).filter((x): x is string => Boolean(x)));
        return Array.from(domainSet).sort();
    }, [operators]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Domains' },
        ...domains.map(domain => ({ id: domain, label: domain }))
    ], [domains]);

    const filteredOperators = useMemo(() => {
        if (!operators) return [];
        return operators.filter((operator) => {
            const matchesSearch = !searchQuery ||
                (operator.name || operator.id).toLowerCase().includes(searchQuery.toLowerCase()) ||
                operator.description?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesDomain = selectedDomain === 'all' || operator.domain === selectedDomain;
            return matchesSearch && matchesDomain;
        });
    }, [operators, searchQuery, selectedDomain]);

    if (isLoading) {
        return (
            <PageLayout title="Operators" subtitle="Domain operators">
                <LoadingState message="Loading operators..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Operators" subtitle="Domain operators">
            <PageHeader
                title="Operators"
                subtitle="View and manage domain operators"
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
                searchPlaceholder="Search operators..."
                categories={categoryList}
                selectedCategory={selectedDomain}
                onCategoryChange={setSelectedDomain}
            />

            <PageSection title={`${filteredOperators.length} Operators`}>
                {filteredOperators.length === 0 ? (
                    <EmptyState
                        title="No operators found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No operators configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredOperators.map((operator) => (
                            <OperatorCard key={operator.id} operator={operator} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default OperatorsListPage;
