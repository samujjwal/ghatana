/**
 * Configuration Dashboard Page
 *
 * Central hub for managing all organization configuration entities.
 * Uses the entity registry to dynamically display all entity types.
 *
 * @doc.type page
 * @doc.purpose Configuration dashboard
 * @doc.layer product
 * @doc.pattern Dashboard
 */

import { Link, useNavigate } from 'react-router';
import { useMemo, useState } from 'react';
import {
    Settings,
    Search,
    Plus,
    ArrowRight,
    Sparkles,
    LayoutGrid,
    List,
} from 'lucide-react';
import { ENTITY_TYPES, getAllEntityTypes } from './entity-registry';
import type { EntityTypeDefinition } from './entity-registry';

// ============================================================================
// Types
// ============================================================================

interface EntityCardProps {
    entity: EntityTypeDefinition;
    count?: number;
    onCreateClick?: () => void;
}

// ============================================================================
// Entity Card Component
// ============================================================================

function EntityCard({ entity, count = 0, onCreateClick }: EntityCardProps) {
    const Icon = entity.icon;
    const navigate = useNavigate();

    const handleCardClick = () => {
        navigate(`/config/${entity.routePath}`);
    };

    const handleCreateClick = (e: React.MouseEvent) => {
        e.stopPropagation();
        if (onCreateClick) {
            onCreateClick();
        } else {
            navigate(`/config/${entity.routePath}/new`);
        }
    };

    return (
        <div
            onClick={handleCardClick}
            className="group relative bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-5 hover:shadow-lg hover:border-blue-300 dark:hover:border-blue-600 transition-all duration-200 cursor-pointer"
        >
            {/* Header */}
            <div className="flex items-start justify-between mb-4">
                <div className={`p-3 rounded-lg ${entity.bgClass}`}>
                    <Icon className={`h-6 w-6 ${entity.colorClass}`} />
                </div>
                {entity.canCreate && (
                    <button
                        onClick={handleCreateClick}
                        className="opacity-0 group-hover:opacity-100 p-2 rounded-lg bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 hover:bg-blue-100 dark:hover:bg-blue-900/50 transition-all duration-200"
                        title={`Create ${entity.name}`}
                    >
                        <Plus className="h-4 w-4" />
                    </button>
                )}
            </div>

            {/* Content */}
            <div className="space-y-1">
                <h3 className="font-semibold text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                    {entity.namePlural}
                </h3>
                <p className="text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                    {entity.description}
                </p>
            </div>

            {/* Footer */}
            <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-700 flex items-center justify-between">
                <span className="text-sm font-medium text-gray-600 dark:text-gray-300">
                    {count} {count === 1 ? entity.name.toLowerCase() : entity.namePlural.toLowerCase()}
                </span>
                <ArrowRight className="h-4 w-4 text-gray-400 group-hover:text-blue-500 group-hover:translate-x-1 transition-all" />
            </div>
        </div>
    );
}

// ============================================================================
// Quick Actions Component
// ============================================================================

function QuickActions() {
    const navigate = useNavigate();
    const creatableEntities = useMemo(() => {
        return getAllEntityTypes().filter((e) => e.canCreate).slice(0, 4);
    }, []);

    return (
        <div className="bg-gradient-to-r from-blue-50 to-indigo-50 dark:from-blue-900/20 dark:to-indigo-900/20 rounded-xl border border-blue-100 dark:border-blue-800 p-5">
            <div className="flex items-center gap-2 mb-4">
                <Sparkles className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                <h3 className="font-semibold text-gray-900 dark:text-gray-100">
                    Quick Actions
                </h3>
            </div>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
                {creatableEntities.map((entity) => {
                    const Icon = entity.icon;
                    return (
                        <button
                            key={entity.id}
                            onClick={() => navigate(`/config/${entity.routePath}/new`)}
                            className="flex flex-col items-center gap-2 p-3 rounded-lg bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-600 hover:shadow-md transition-all duration-200"
                        >
                            <div className={`p-2 rounded-lg ${entity.bgClass}`}>
                                <Icon className={`h-5 w-5 ${entity.colorClass}`} />
                            </div>
                            <span className="text-xs font-medium text-gray-700 dark:text-gray-300">
                                New {entity.name}
                            </span>
                        </button>
                    );
                })}
            </div>
        </div>
    );
}

// ============================================================================
// Main Dashboard Page
// ============================================================================

export function ConfigDashboardPage() {
    const [searchQuery, setSearchQuery] = useState('');
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');

    // Get all entity types from registry
    const allEntities = useMemo(() => getAllEntityTypes(), []);

    // Filter entities based on search
    const filteredEntities = useMemo(() => {
        if (!searchQuery.trim()) return allEntities;
        const query = searchQuery.toLowerCase();
        return allEntities.filter(
            (entity) =>
                entity.name.toLowerCase().includes(query) ||
                entity.namePlural.toLowerCase().includes(query) ||
                entity.description.toLowerCase().includes(query)
        );
    }, [allEntities, searchQuery]);

    // Placeholder counts - in real app, these would come from API
    const entityCounts = useMemo(() => {
        const counts: Record<string, number> = {};
        allEntities.forEach((entity) => {
            // Demo counts - replace with real API calls
            counts[entity.id] = Math.floor(Math.random() * 20) + 1;
        });
        return counts;
    }, [allEntities]);

    return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Header */}
                <div className="mb-8">
                    <div className="flex items-center gap-3 mb-2">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
                            <Settings className="h-6 w-6 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                                Configuration
                            </h1>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                Manage organization entities and templates
                            </p>
                        </div>
                    </div>
                </div>

                {/* Quick Actions */}
                <div className="mb-8">
                    <QuickActions />
                </div>

                {/* Search and View Toggle */}
                <div className="flex items-center justify-between gap-4 mb-6">
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
                        <input
                            type="text"
                            placeholder="Search entities..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 dark:placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </div>
                    <div className="flex items-center gap-1 bg-gray-100 dark:bg-gray-800 rounded-lg p-1">
                        <button
                            onClick={() => setViewMode('grid')}
                            className={`p-2 rounded-md transition-colors ${viewMode === 'grid'
                                    ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                                }`}
                            title="Grid view"
                        >
                            <LayoutGrid className="h-5 w-5" />
                        </button>
                        <button
                            onClick={() => setViewMode('list')}
                            className={`p-2 rounded-md transition-colors ${viewMode === 'list'
                                    ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                                }`}
                            title="List view"
                        >
                            <List className="h-5 w-5" />
                        </button>
                    </div>
                </div>

                {/* Entity Grid/List */}
                {viewMode === 'grid' ? (
                    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
                        {filteredEntities.map((entity) => (
                            <EntityCard
                                key={entity.id}
                                entity={entity}
                                count={entityCounts[entity.id] || 0}
                            />
                        ))}
                    </div>
                ) : (
                    <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                        <table className="w-full">
                            <thead className="bg-gray-50 dark:bg-gray-900">
                                <tr>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Entity Type
                                    </th>
                                    <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Description
                                    </th>
                                    <th className="px-6 py-3 text-center text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Count
                                    </th>
                                    <th className="px-6 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                                {filteredEntities.map((entity) => {
                                    const Icon = entity.icon;
                                    return (
                                        <tr
                                            key={entity.id}
                                            className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors"
                                        >
                                            <td className="px-6 py-4 whitespace-nowrap">
                                                <Link
                                                    to={`/config/${entity.routePath}`}
                                                    className="flex items-center gap-3 group"
                                                >
                                                    <div className={`p-2 rounded-lg ${entity.bgClass}`}>
                                                        <Icon className={`h-5 w-5 ${entity.colorClass}`} />
                                                    </div>
                                                    <span className="font-medium text-gray-900 dark:text-gray-100 group-hover:text-blue-600 dark:group-hover:text-blue-400">
                                                        {entity.namePlural}
                                                    </span>
                                                </Link>
                                            </td>
                                            <td className="px-6 py-4">
                                                <span className="text-sm text-gray-500 dark:text-gray-400 line-clamp-1">
                                                    {entity.description}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 text-center">
                                                <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-gray-100 dark:bg-gray-700 text-gray-800 dark:text-gray-200">
                                                    {entityCounts[entity.id] || 0}
                                                </span>
                                            </td>
                                            <td className="px-6 py-4 text-right">
                                                <div className="flex items-center justify-end gap-2">
                                                    <Link
                                                        to={`/config/${entity.routePath}`}
                                                        className="text-sm text-blue-600 dark:text-blue-400 hover:text-blue-700 dark:hover:text-blue-300"
                                                    >
                                                        View All
                                                    </Link>
                                                    {entity.canCreate && (
                                                        <>
                                                            <span className="text-gray-300 dark:text-gray-600">|</span>
                                                            <Link
                                                                to={`/config/${entity.routePath}/new`}
                                                                className="text-sm text-green-600 dark:text-green-400 hover:text-green-700 dark:hover:text-green-300"
                                                            >
                                                                Create
                                                            </Link>
                                                        </>
                                                    )}
                                                </div>
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </table>
                    </div>
                )}

                {/* Empty State */}
                {filteredEntities.length === 0 && (
                    <div className="text-center py-12">
                        <Search className="h-12 w-12 text-gray-300 dark:text-gray-600 mx-auto mb-4" />
                        <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
                            No entities found
                        </h3>
                        <p className="text-gray-500 dark:text-gray-400">
                            Try adjusting your search query
                        </p>
                    </div>
                )}
            </div>
        </div>
    );
}
