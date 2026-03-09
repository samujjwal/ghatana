/**
 * Unified Entity List Component
 *
 * A reusable list component that displays entities in grid or table view.
 * Supports search, filtering, and actions.
 *
 * @doc.type component
 * @doc.purpose Entity list display with grid/table views
 * @doc.layer product
 * @doc.pattern List
 */

import React, { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import type { EntityTypeDefinition, EntityField } from './entity-registry';
import {
    Search,
    LayoutGrid,
    List,
    MoreVertical,
    Eye,
    Edit2,
    Copy,
    Trash2,
    ArrowRight,
    Plus,
} from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface EntityItem {
    id: string;
    [key: string]: unknown;
}

export interface EntityListProps {
    /** Entity type definition */
    entityType: EntityTypeDefinition;
    /** Items to display */
    items: EntityItem[];
    /** Loading state */
    isLoading?: boolean;
    /** Handler when item is clicked */
    onItemClick?: (item: EntityItem) => void;
    /** Handler for edit action */
    onEdit?: (item: EntityItem) => void;
    /** Handler for delete action */
    onDelete?: (item: EntityItem) => void;
    /** Handler for duplicate action */
    onDuplicate?: (item: EntityItem) => void;
    /** Initial view mode */
    initialViewMode?: 'grid' | 'table';
    /** Show search bar */
    showSearch?: boolean;
    /** Show create button */
    showCreateButton?: boolean;
    /** Custom empty state message */
    emptyMessage?: string;
}

// ============================================================================
// Helper Components
// ============================================================================

interface CellValueProps {
    value: unknown;
    field?: EntityField;
}

function CellValue({ value, field }: CellValueProps) {
    if (value === null || value === undefined) {
        return <span className="text-gray-400">—</span>;
    }

    if (typeof value === 'boolean') {
        return (
            <span
                className={`inline-flex px-2 py-0.5 text-xs rounded-full ${value
                        ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400'
                        : 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-400'
                    }`}
            >
                {value ? 'Yes' : 'No'}
            </span>
        );
    }

    if (Array.isArray(value)) {
        if (value.length === 0) {
            return <span className="text-gray-400">—</span>;
        }
        return (
            <div className="flex flex-wrap gap-1">
                {value.slice(0, 3).map((item, i) => (
                    <span
                        key={i}
                        className="inline-flex px-2 py-0.5 text-xs bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-full"
                    >
                        {typeof item === 'object' ? JSON.stringify(item) : String(item)}
                    </span>
                ))}
                {value.length > 3 && (
                    <span className="text-xs text-gray-500">+{value.length - 3} more</span>
                )}
            </div>
        );
    }

    if (typeof value === 'object') {
        return (
            <span className="text-xs font-mono text-gray-600 dark:text-gray-400">
                {JSON.stringify(value).slice(0, 50)}...
            </span>
        );
    }

    // Handle status values specially
    if (field?.key === 'status' || typeof value === 'string') {
        const strValue = String(value).toLowerCase();
        if (['active', 'enabled', 'running', 'success', 'completed'].includes(strValue)) {
            return (
                <span className="inline-flex px-2 py-0.5 text-xs bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400 rounded-full">
                    {String(value)}
                </span>
            );
        }
        if (['inactive', 'disabled', 'stopped', 'paused', 'pending'].includes(strValue)) {
            return (
                <span className="inline-flex px-2 py-0.5 text-xs bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400 rounded-full">
                    {String(value)}
                </span>
            );
        }
        if (['error', 'failed', 'critical'].includes(strValue)) {
            return (
                <span className="inline-flex px-2 py-0.5 text-xs bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400 rounded-full">
                    {String(value)}
                </span>
            );
        }
    }

    return <span className="text-gray-900 dark:text-gray-100">{String(value)}</span>;
}

// Dropdown Menu Component
interface ActionMenuProps {
    onView: () => void;
    onEdit?: () => void;
    onDuplicate?: () => void;
    onDelete?: () => void;
}

function ActionMenu({ onView, onEdit, onDuplicate, onDelete }: ActionMenuProps) {
    const [isOpen, setIsOpen] = useState(false);

    return (
        <div className="relative">
            <button
                onClick={(e: React.MouseEvent) => {
                    e.stopPropagation();
                    setIsOpen(!isOpen);
                }}
                className="p-1.5 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
            >
                <MoreVertical className="h-4 w-4 text-gray-500" />
            </button>
            {isOpen && (
                <>
                    <div
                        className="fixed inset-0 z-10"
                        onClick={(e: React.MouseEvent) => {
                            e.stopPropagation();
                            setIsOpen(false);
                        }}
                    />
                    <div className="absolute right-0 top-full mt-1 w-40 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-20 py-1">
                        <button
                            onClick={(e: React.MouseEvent) => {
                                e.stopPropagation();
                                onView();
                                setIsOpen(false);
                            }}
                            className="w-full px-3 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
                        >
                            <Eye className="h-4 w-4" />
                            View
                        </button>
                        {onEdit && (
                            <button
                                onClick={(e: React.MouseEvent) => {
                                    e.stopPropagation();
                                    onEdit();
                                    setIsOpen(false);
                                }}
                                className="w-full px-3 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
                            >
                                <Edit2 className="h-4 w-4" />
                                Edit
                            </button>
                        )}
                        {onDuplicate && (
                            <button
                                onClick={(e: React.MouseEvent) => {
                                    e.stopPropagation();
                                    onDuplicate();
                                    setIsOpen(false);
                                }}
                                className="w-full px-3 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
                            >
                                <Copy className="h-4 w-4" />
                                Duplicate
                            </button>
                        )}
                        {onDelete && (
                            <>
                                <hr className="my-1 border-gray-200 dark:border-gray-700" />
                                <button
                                    onClick={(e: React.MouseEvent) => {
                                        e.stopPropagation();
                                        onDelete();
                                        setIsOpen(false);
                                    }}
                                    className="w-full px-3 py-2 text-left text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2"
                                >
                                    <Trash2 className="h-4 w-4" />
                                    Delete
                                </button>
                            </>
                        )}
                    </div>
                </>
            )}
        </div>
    );
}

// ============================================================================
// Main Component
// ============================================================================

export function EntityList({
    entityType,
    items,
    isLoading = false,
    onItemClick,
    onEdit,
    onDelete,
    onDuplicate,
    initialViewMode = 'grid',
    showSearch = true,
    showCreateButton = true,
    emptyMessage,
}: EntityListProps) {
    const [viewMode, setViewMode] = useState(initialViewMode);
    const [searchQuery, setSearchQuery] = useState('');
    const navigate = useNavigate();

    // Filter items based on search
    const filteredItems = useMemo(() => {
        if (!searchQuery.trim()) return items;
        const query = searchQuery.toLowerCase();
        return items.filter((item) =>
            entityType.searchFields.some((fieldKey) => {
                const value = item[fieldKey];
                return value && String(value).toLowerCase().includes(query);
            })
        );
    }, [items, searchQuery, entityType.searchFields]);

    // Handle item click
    const handleItemClick = (item: EntityItem) => {
        if (onItemClick) {
            onItemClick(item);
        } else {
            navigate(`/config/${entityType.routePath}/${item.id}`);
        }
    };

    // Handle edit
    const handleEdit = (item: EntityItem) => {
        if (onEdit) {
            onEdit(item);
        } else {
            navigate(`/config/${entityType.routePath}/${item.id}/edit`);
        }
    };

    const Icon = entityType.icon;

    // Loading state
    if (isLoading) {
        return (
            <div className="flex items-center justify-center py-12">
                <div className="animate-pulse text-center">
                    <div className="h-12 w-12 bg-gray-200 dark:bg-gray-700 rounded-lg mx-auto mb-4" />
                    <div className="h-4 w-32 bg-gray-200 dark:bg-gray-700 rounded mx-auto" />
                </div>
            </div>
        );
    }

    return (
        <div className="space-y-4">
            {/* Header with search and controls */}
            <div className="flex items-center justify-between gap-4">
                {showSearch && (
                    <div className="relative flex-1 max-w-md">
                        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-5 w-5 text-gray-400" />
                        <input
                            type="text"
                            placeholder={`Search ${entityType.namePlural.toLowerCase()}...`}
                            value={searchQuery}
                            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setSearchQuery(e.target.value)}
                            className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-500 focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                        />
                    </div>
                )}
                <div className="flex items-center gap-2">
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
                            onClick={() => setViewMode('table')}
                            className={`p-2 rounded-md transition-colors ${viewMode === 'table'
                                    ? 'bg-white dark:bg-gray-700 text-gray-900 dark:text-gray-100 shadow-sm'
                                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300'
                                }`}
                            title="Table view"
                        >
                            <List className="h-5 w-5" />
                        </button>
                    </div>
                    {showCreateButton && entityType.canCreate && (
                        <Link
                            to={`/config/${entityType.routePath}/new`}
                            className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg transition-colors"
                        >
                            <Plus className="h-4 w-4" />
                            New {entityType.name}
                        </Link>
                    )}
                </div>
            </div>

            {/* Empty state */}
            {filteredItems.length === 0 && (
                <div className="text-center py-12 bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700">
                    <div className={`p-4 rounded-xl ${entityType.bgClass} inline-block mb-4`}>
                        <Icon className={`h-8 w-8 ${entityType.colorClass}`} />
                    </div>
                    <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100 mb-2">
                        {searchQuery ? `No ${entityType.namePlural.toLowerCase()} found` : `No ${entityType.namePlural.toLowerCase()} yet`}
                    </h3>
                    <p className="text-gray-500 dark:text-gray-400 mb-4">
                        {emptyMessage ||
                            (searchQuery
                                ? 'Try adjusting your search query'
                                : `Create your first ${entityType.name.toLowerCase()} to get started`)}
                    </p>
                    {!searchQuery && entityType.canCreate && (
                        <Link
                            to={`/config/${entityType.routePath}/new`}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg transition-colors"
                        >
                            <Plus className="h-4 w-4" />
                            Create {entityType.name}
                        </Link>
                    )}
                </div>
            )}

            {/* Grid View */}
            {filteredItems.length > 0 && viewMode === 'grid' && (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
                    {filteredItems.map((item) => (
                        <div
                            key={item.id}
                            onClick={() => handleItemClick(item)}
                            className="group bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-4 hover:shadow-lg hover:border-blue-300 dark:hover:border-blue-600 transition-all cursor-pointer"
                        >
                            <div className="flex items-start justify-between mb-3">
                                <div className={`p-2 rounded-lg ${entityType.bgClass}`}>
                                    <Icon className={`h-5 w-5 ${entityType.colorClass}`} />
                                </div>
                                <ActionMenu
                                    onView={() => handleItemClick(item)}
                                    onEdit={entityType.canEdit ? () => handleEdit(item) : undefined}
                                    onDuplicate={onDuplicate ? () => onDuplicate(item) : undefined}
                                    onDelete={entityType.canDelete && onDelete ? () => onDelete(item) : undefined}
                                />
                            </div>
                            <h3 className="font-semibold text-gray-900 dark:text-gray-100 mb-1 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                                {String(item[entityType.primaryField] || item.name || item.id)}
                            </h3>
                            {entityType.secondaryField && item[entityType.secondaryField] != null && (
                                <p className="text-sm text-gray-500 dark:text-gray-400 mb-3">
                                    {String(item[entityType.secondaryField])}
                                </p>
                            )}
                            <div className="flex items-center justify-between pt-3 border-t border-gray-100 dark:border-gray-700">
                                {item.status != null && <CellValue value={item.status} />}
                                <ArrowRight className="h-4 w-4 text-gray-400 group-hover:text-blue-500 group-hover:translate-x-1 transition-all" />
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Table View */}
            {filteredItems.length > 0 && viewMode === 'table' && (
                <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                    <div className="overflow-x-auto">
                        <table className="w-full">
                            <thead className="bg-gray-50 dark:bg-gray-900">
                                <tr>
                                    {entityType.listFields.map((fieldKey) => (
                                        <th
                                            key={fieldKey}
                                            className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider"
                                        >
                                            {fieldKey.replace(/([A-Z])/g, ' $1').trim()}
                                        </th>
                                    ))}
                                    <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                                        Actions
                                    </th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
                                {filteredItems.map((item) => (
                                    <tr
                                        key={item.id}
                                        onClick={() => handleItemClick(item)}
                                        className="hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors cursor-pointer"
                                    >
                                        {entityType.listFields.map((fieldKey) => {
                                            const field = entityType.fields.find((f) => f.key === fieldKey);
                                            return (
                                                <td key={fieldKey} className="px-4 py-3 text-sm">
                                                    <CellValue value={item[fieldKey]} field={field} />
                                                </td>
                                            );
                                        })}
                                        <td className="px-4 py-3 text-right">
                                            <ActionMenu
                                                onView={() => handleItemClick(item)}
                                                onEdit={entityType.canEdit ? () => handleEdit(item) : undefined}
                                                onDuplicate={onDuplicate ? () => onDuplicate(item) : undefined}
                                                onDelete={entityType.canDelete && onDelete ? () => onDelete(item) : undefined}
                                            />
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
}
