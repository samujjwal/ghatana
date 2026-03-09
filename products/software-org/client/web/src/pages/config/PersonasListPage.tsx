/**
 * Personas List Page
 *
 * @doc.type page
 * @doc.purpose Personas list and visualization
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
import { Users, Shield, Tag, Grid3x3, List, ArrowRight } from 'lucide-react';
import { usePersonas } from '@/hooks/useConfig';
import type { PersonaConfig } from '@/services/api/configApi';

interface PersonaCardProps {
    persona: PersonaConfig;
    viewMode: 'grid' | 'list';
}

function PersonaCard({ persona, viewMode }: PersonaCardProps) {
    const tags = persona.tags || [];
    const permissions = persona.permissions || [];
    const categoryStyle = getCategoryStyle(tags[0]?.toLowerCase() || 'default');

    if (viewMode === 'list') {
        return (
            <Link
                to={`/config/personas/${persona.id}`}
                className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover}`}
            >
                <div className="flex items-center justify-between">
                    <div className="flex items-center space-x-4">
                        <div className={`flex-shrink-0 rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                            <Users className="h-6 w-6" />
                        </div>
                        <div>
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-white">
                                {persona.display_name || persona.id}
                            </h3>
                            <p className="text-sm text-slate-600 dark:text-slate-400">{persona.id}</p>
                        </div>
                    </div>
                    <div className="flex items-center space-x-4">
                        {tags.length > 0 && (
                            <div className="flex items-center space-x-1">
                                {tags.slice(0, 3).map((tag, idx) => (
                                    <span key={idx} className="px-2 py-1 text-xs bg-slate-100 dark:bg-slate-700 rounded">
                                        {tag}
                                    </span>
                                ))}
                            </div>
                        )}
                        <ArrowRight className="h-5 w-5 text-slate-400" />
                    </div>
                </div>
            </Link>
        );
    }

    return (
        <Link
            to={`/config/personas/${persona.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            <div className="flex items-start justify-between mb-4">
                <div className={`rounded-lg p-3 ${categoryStyle.bg} ${categoryStyle.icon}`}>
                    <Users className="h-6 w-6" />
                </div>
            </div>
            <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-1">
                {persona.display_name || persona.id}
            </h3>
            <p className="text-sm text-slate-500 dark:text-slate-400 mb-4">{persona.id}</p>

            {tags.length > 0 && (
                <div className="mb-3">
                    <div className="flex items-center text-xs text-slate-500 mb-1">
                        <Tag className="h-3 w-3 mr-1" /> Tags
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {tags.map((tag, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400 rounded">
                                {tag}
                            </span>
                        ))}
                    </div>
                </div>
            )}

            {permissions.length > 0 && (
                <div>
                    <div className="flex items-center text-xs text-slate-500 mb-1">
                        <Shield className="h-3 w-3 mr-1" /> Permissions
                    </div>
                    <div className="flex flex-wrap gap-1">
                        {permissions.slice(0, 3).map((perm, idx) => (
                            <span key={idx} className="px-2 py-1 text-xs bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400 rounded">
                                {perm}
                            </span>
                        ))}
                        {permissions.length > 3 && (
                            <span className="px-2 py-1 text-xs bg-slate-100 text-slate-600 rounded">
                                +{permissions.length - 3}
                            </span>
                        )}
                    </div>
                </div>
            )}
        </Link>
    );
}

export function PersonasListPage() {
    const { data: personas, isLoading } = usePersonas();
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedTag, setSelectedTag] = useState<string>('all');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    const tags = useMemo(() => {
        if (!personas) return [];
        const tagSet = new Set<string>();
        personas.forEach((p) => p.tags?.forEach((t) => tagSet.add(t)));
        return Array.from(tagSet).sort();
    }, [personas]);

    const categoryList = useMemo(() => [
        { id: 'all', label: 'All Tags' },
        ...tags.map(tag => ({ id: tag, label: tag }))
    ], [tags]);

    const filteredPersonas = useMemo(() => {
        if (!personas) return [];
        return personas.filter((persona) => {
            const matchesSearch = !searchQuery ||
                persona.id.toLowerCase().includes(searchQuery.toLowerCase()) ||
                persona.display_name?.toLowerCase().includes(searchQuery.toLowerCase());
            const matchesTag = selectedTag === 'all' || persona.tags?.includes(selectedTag);
            return matchesSearch && matchesTag;
        });
    }, [personas, searchQuery, selectedTag]);

    if (isLoading) {
        return (
            <PageLayout title="Personas" subtitle="Role definitions">
                <LoadingState message="Loading personas..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Personas" subtitle="Role definitions">
            <PageHeader
                title="Personas"
                subtitle="View and manage role definitions"
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
                searchPlaceholder="Search personas..."
                categories={categoryList}
                selectedCategory={selectedTag}
                onCategoryChange={setSelectedTag}
            />

            <PageSection title={`${filteredPersonas.length} Personas`}>
                {filteredPersonas.length === 0 ? (
                    <EmptyState
                        title="No personas found"
                        description={searchQuery ? 'Try adjusting your search.' : 'No personas configured.'}
                    />
                ) : (
                    <PageGrid cols={viewMode === 'grid' ? { sm: 1, md: 2, lg: 3 } : { sm: 1, md: 1, lg: 1 }}>
                        {filteredPersonas.map((persona) => (
                            <PersonaCard key={persona.id} persona={persona} viewMode={viewMode} />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}

export default PersonasListPage;
