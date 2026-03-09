/**
 * Entity List Page Component
 *
 * A wrapper page component for displaying entity lists with breadcrumbs
 * and consistent layout.
 *
 * @doc.type component
 * @doc.purpose Entity list page wrapper
 * @doc.layer product
 * @doc.pattern Page
 */

import { Link } from 'react-router';
import { ChevronRight, Settings } from 'lucide-react';
import type { EntityTypeDefinition } from './entity-registry';
import { EntityList, type EntityItem } from './EntityList';

// ============================================================================
// Types
// ============================================================================

export interface EntityListPageProps {
    /** Entity type definition */
    entityType: EntityTypeDefinition;
    /** Items to display */
    items: EntityItem[];
    /** Loading state */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
    /** Handler for delete action */
    onDelete?: (item: EntityItem) => void;
    /** Handler for duplicate action */
    onDuplicate?: (item: EntityItem) => void;
    /** Custom page title (defaults to entity plural name) */
    title?: string;
    /** Custom page subtitle */
    subtitle?: string;
}

// ============================================================================
// Main Component
// ============================================================================

export function EntityListPage({
    entityType,
    items,
    isLoading = false,
    error = null,
    onDelete,
    onDuplicate,
    title,
    subtitle,
}: EntityListPageProps) {
    const Icon = entityType.icon;

    return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                {/* Breadcrumb */}
                <nav className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 mb-6">
                    <Link
                        to="/config"
                        className="hover:text-gray-700 dark:hover:text-gray-300 flex items-center gap-1"
                    >
                        <Settings className="h-4 w-4" />
                        Configuration
                    </Link>
                    <ChevronRight className="h-4 w-4" />
                    <span className="text-gray-900 dark:text-gray-100 font-medium">
                        {entityType.namePlural}
                    </span>
                </nav>

                {/* Header */}
                <div className="flex items-start justify-between mb-8">
                    <div className="flex items-center gap-4">
                        <div className={`p-3 rounded-xl ${entityType.bgClass}`}>
                            <Icon className={`h-8 w-8 ${entityType.colorClass}`} />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                                {title || entityType.namePlural}
                            </h1>
                            <p className="text-gray-500 dark:text-gray-400">
                                {subtitle || entityType.description}
                            </p>
                        </div>
                    </div>
                </div>

                {/* Error State */}
                {error && (
                    <div className="mb-6 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                        <p className="text-red-600 dark:text-red-400">{error}</p>
                    </div>
                )}

                {/* Entity List */}
                <EntityList
                    entityType={entityType}
                    items={items}
                    isLoading={isLoading}
                    onDelete={onDelete}
                    onDuplicate={onDuplicate}
                />
            </div>
        </div>
    );
}
