/**
 * Integrations List Page
 *
 * @doc.type page
 * @doc.purpose Integrations list and visualization
 * @doc.layer product
 * @doc.pattern List Page
 */

import { useState, useMemo } from 'react';
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
import { Plug, ExternalLink, Grid3x3, List, Key, Shield } from 'lucide-react';
import { useConfigIntegrations } from '@/hooks/useConfig';
import type { IntegrationConfig } from '@/services/api/configApi';

interface IntegrationCardProps {
    integration: IntegrationConfig;
    viewMode: 'grid' | 'list';
}

function IntegrationCard({ integration, viewMode }: IntegrationCardProps) {
    const categoryStyle = getCategoryStyle('pink');
    const capabilities = integration.capabilities || [];

    if (viewMode === 'list') {
        return (
            <div className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}>
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Plug className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {integration.name || integration.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">
                                {integration.type || 'External Tool'}
                            </p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        <span className={`px-2 py-1 text-xs font-medium rounded ${
                            integration.enabled !== false
                                ? 'bg-green-100 text-green-800' 
                                : 'bg-slate-100 text-slate-600'
                        }`}>
                            {integration.enabled !== false ? 'enabled' : 'disabled'}
                        </span>
                    </div>
                </div>
                {integration.description && (
                    <p className="mt-2 text-sm text-slate-500 dark:text-slate-400 ml-16">
                        {integration.description}
                    </p>
                )}
            </div>
        );
    }

    return (
        <div className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}>
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Plug className="h-6 w-6" />
                </div>
                <span className={`px-2 py-1 text-xs font-medium rounded ${
                    integration.enabled !== false
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400' 
                        : 'bg-slate-100 text-slate-600 dark:bg-slate-700 dark:text-slate-300'
                }`}>
                    {integration.enabled !== false ? 'enabled' : 'disabled'}
                </span>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-1">
                {integration.name || integration.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 mb-2">
                {integration.type || 'External Tool'}
            </p>
            <p className="text-sm text-slate-500 dark:text-slate-400 line-clamp-2 mb-4">
                {integration.description || 'No description'}
            </p>

            {integration.auth_type && (
                <div className="flex items-center text-sm text-slate-500 dark:text-slate-400 mb-2">
                    <Key className="h-4 w-4 mr-2" />
                    Auth: {integration.auth_type}
                </div>
            )}

            {capabilities.length > 0 && (
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-2">
                        <Shield className="h-3 w-3 mr-1" /> Capabilities
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {capabilities.slice(0, 3).map((cap, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-pink-100 text-pink-800 dark:bg-pink-900/30 dark:text-pink-400 rounded">
                                {cap}
                            </span>
                        ))}
                        {capabilities.length > 3 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{capabilities.length - 3}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

export function IntegrationsListPage() {
    const { data: integrations, isLoading } = useConfigIntegrations();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedType, setSelectedType] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const types = useMemo(() => {
        if (!integrations) return [];
        const typeSet = new Set(integrations.map((i) => i.type).filter((x): x is string => Boolean(x)));
        return Array.from(typeSet).sort();
    }, [integrations]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Types' },
        ...types.map(type => ({ id: type, label: type }))
    ], [types]);

    const filteredIntegrations = useMemo(() => {
        if (!integrations) return [];
        return integrations.filter((integration) => {
            const matchesSearch = !searchQuery ||
                (integration.name || integration.id).toLowerCase().includes(searchQuery.toLowerCase()) ||
                integration.description?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesType = selectedType === 'all' || integration.type === selectedType;
            return matchesSearch && matchesType;
        });
    }, [integrations, searchQuery, selectedType]);

    if (isLoading) {
        return (
            <PageLayout title="Integrations" subtitle="External tools">
                <LoadingState message="Loading integrations..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Integrations" subtitle="External tools">
            <PageHeader
                title="Integrations"
                subtitle="View and manage external tool integrations"
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
                searchPlaceholder="Search integrations..."
                categories={categoryList}
                selectedCategory={selectedType}
                onCategoryChange={setSelectedType}
            />

            <PageSection title={`${filteredIntegrations.length} Integrations`}>
                {filteredIntegrations.length === 0 ? (
                    <EmptyState
                        title="No integrations found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No integrations configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredIntegrations.map((integration) => (
                            <IntegrationCard key={integration.id} integration={integration} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default IntegrationsListPage;
