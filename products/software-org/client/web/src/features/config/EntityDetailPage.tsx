/**
 * Entity Detail Page Component
 *
 * A unified detail view component for displaying entity information
 * with consistent layout, actions, and field grouping.
 *
 * @doc.type component
 * @doc.purpose Entity detail display
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState } from 'react';
import { Link, useNavigate } from 'react-router';
import type { EntityTypeDefinition, EntityField } from './entity-registry';
import {
    ChevronRight,
    Settings,
    Edit2,
    Trash2,
    Copy,
    MoreVertical,
    ArrowLeft,
    Loader2,
} from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface EntityDetailPageProps {
    /** Entity type definition */
    entityType: EntityTypeDefinition;
    /** Entity data */
    data: Record<string, unknown> | null;
    /** Loading state */
    isLoading?: boolean;
    /** Error message */
    error?: string | null;
    /** Handler for delete action */
    onDelete?: () => Promise<void>;
    /** Handler for duplicate action */
    onDuplicate?: () => void;
    /** Is delete in progress */
    isDeleting?: boolean;
}

// ============================================================================
// Helper Components
// ============================================================================

interface FieldValueProps {
    value: unknown;
    field?: EntityField;
}

function FieldValue({ value, field }: FieldValueProps) {
    if (value === null || value === undefined || value === '') {
        return <span className="text-gray-400 dark:text-gray-500">Not set</span>;
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
            return <span className="text-gray-400 dark:text-gray-500">None</span>;
        }
        return (
            <div className="flex flex-wrap gap-1.5">
                {value.map((item, i) => (
                    <span
                        key={i}
                        className="inline-flex px-2 py-0.5 text-xs bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 rounded-full"
                    >
                        {typeof item === 'object' ? JSON.stringify(item) : String(item)}
                    </span>
                ))}
            </div>
        );
    }

    if (typeof value === 'object') {
        return (
            <pre className="text-sm font-mono bg-gray-50 dark:bg-gray-900 p-3 rounded-lg overflow-x-auto">
                {JSON.stringify(value, null, 2)}
            </pre>
        );
    }

    // Handle status values specially
    const strValue = String(value).toLowerCase();
    if (field?.key === 'status' || strValue === 'active' || strValue === 'inactive') {
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

// ============================================================================
// Main Component
// ============================================================================

export function EntityDetailPage({
    entityType,
    data,
    isLoading = false,
    error = null,
    onDelete,
    onDuplicate,
    isDeleting = false,
}: EntityDetailPageProps) {
    const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
    const [showMenu, setShowMenu] = useState(false);
    const navigate = useNavigate();
    const Icon = entityType.icon;

    // Handle delete
    const handleDelete = async () => {
        if (onDelete) {
            await onDelete();
        }
        setShowDeleteConfirm(false);
    };

    // Loading state
    if (isLoading) {
        return (
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <Loader2 className="h-12 w-12 text-blue-600 animate-spin mx-auto mb-4" />
                    <p className="text-gray-500 dark:text-gray-400">Loading {entityType.name.toLowerCase()}...</p>
                </div>
            </div>
        );
    }

    // Error state
    if (error) {
        return (
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                    <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-6">
                        <h2 className="text-lg font-semibold text-red-800 dark:text-red-200 mb-2">Error</h2>
                        <p className="text-red-600 dark:text-red-400">{error}</p>
                        <button
                            onClick={() => navigate(`/config/${entityType.routePath}`)}
                            className="mt-4 px-4 py-2 bg-red-600 text-white rounded-lg hover:bg-red-700 transition-colors"
                        >
                            Back to {entityType.namePlural}
                        </button>
                    </div>
                </div>
            </div>
        );
    }

    // Not found state
    if (!data) {
        return (
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
                <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
                    <div className="text-center py-12">
                        <Icon className={`h-16 w-16 ${entityType.colorClass} mx-auto mb-4 opacity-50`} />
                        <h2 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
                            {entityType.name} not found
                        </h2>
                        <p className="text-gray-500 dark:text-gray-400 mb-4">
                            The requested {entityType.name.toLowerCase()} could not be found.
                        </p>
                        <Link
                            to={`/config/${entityType.routePath}`}
                            className="inline-flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                        >
                            <ArrowLeft className="h-4 w-4" />
                            Back to {entityType.namePlural}
                        </Link>
                    </div>
                </div>
            </div>
        );
    }

    // Group fields by their group property
    const groupedFields: Record<string, EntityField[]> = {};
    const ungroupedFields: EntityField[] = [];

    entityType.fields.forEach((field) => {
        if (field.group) {
            if (!groupedFields[field.group]) {
                groupedFields[field.group] = [];
            }
            groupedFields[field.group].push(field);
        } else {
            ungroupedFields.push(field);
        }
    });

    // Sort fields within each group by order
    Object.values(groupedFields).forEach((fields) => {
        fields.sort((a, b) => (a.order || 0) - (b.order || 0));
    });

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
                    <Link
                        to={`/config/${entityType.routePath}`}
                        className="hover:text-gray-700 dark:hover:text-gray-300"
                    >
                        {entityType.namePlural}
                    </Link>
                    <ChevronRight className="h-4 w-4" />
                    <span className="text-gray-900 dark:text-gray-100 font-medium">
                        {String(data[entityType.primaryField] || data.name || data.id)}
                    </span>
                </nav>

                {/* Header */}
                <div className="flex items-start justify-between mb-8">
                    <div className="flex items-center gap-4">
                        <div className={`p-4 rounded-xl ${entityType.bgClass}`}>
                            <Icon className={`h-10 w-10 ${entityType.colorClass}`} />
                        </div>
                        <div>
                            <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                                {String(data[entityType.primaryField] || data.name || data.id)}
                            </h1>
                            {entityType.secondaryField && data[entityType.secondaryField] != null && (
                                <p className="text-gray-500 dark:text-gray-400">
                                    {String(data[entityType.secondaryField])}
                                </p>
                            )}
                            {data.status != null && (
                                <div className="mt-2">
                                    <FieldValue value={data.status} />
                                </div>
                            )}
                        </div>
                    </div>
                    <div className="flex items-center gap-2">
                        {entityType.canEdit && (
                            <Link
                                to={`/config/${entityType.routePath}/${data.id}/edit`}
                                className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                            >
                                <Edit2 className="h-4 w-4" />
                                Edit
                            </Link>
                        )}
                        <div className="relative">
                            <button
                                onClick={() => setShowMenu(!showMenu)}
                                className="p-2 rounded-lg border border-gray-300 dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                            >
                                <MoreVertical className="h-5 w-5 text-gray-500" />
                            </button>
                            {showMenu && (
                                <>
                                    <div className="fixed inset-0 z-10" onClick={() => setShowMenu(false)} />
                                    <div className="absolute right-0 top-full mt-1 w-48 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-20 py-1">
                                        {onDuplicate && (
                                            <button
                                                onClick={() => {
                                                    onDuplicate();
                                                    setShowMenu(false);
                                                }}
                                                className="w-full px-4 py-2 text-left text-sm text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 flex items-center gap-2"
                                            >
                                                <Copy className="h-4 w-4" />
                                                Duplicate
                                            </button>
                                        )}
                                        {entityType.canDelete && onDelete && (
                                            <>
                                                <hr className="my-1 border-gray-200 dark:border-gray-700" />
                                                <button
                                                    onClick={() => {
                                                        setShowDeleteConfirm(true);
                                                        setShowMenu(false);
                                                    }}
                                                    className="w-full px-4 py-2 text-left text-sm text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center gap-2"
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
                    </div>
                </div>

                {/* Details Card */}
                <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 overflow-hidden">
                    {/* Ungrouped Fields */}
                    {ungroupedFields.length > 0 && (
                        <div className="p-6 space-y-4">
                            {ungroupedFields.map((field) => (
                                <div key={field.key} className="flex flex-col sm:flex-row sm:items-start gap-2">
                                    <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 sm:w-48 flex-shrink-0">
                                        {field.label}
                                    </dt>
                                    <dd className="text-sm">
                                        <FieldValue value={data[field.key]} field={field} />
                                    </dd>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* Grouped Fields */}
                    {entityType.fieldGroups?.map((group) => {
                        const fields = groupedFields[group.id];
                        if (!fields || fields.length === 0) return null;

                        return (
                            <div key={group.id} className="border-t border-gray-200 dark:border-gray-700">
                                <div className="px-6 py-4 bg-gray-50 dark:bg-gray-900/50">
                                    <h3 className="font-semibold text-gray-900 dark:text-gray-100">
                                        {group.label}
                                    </h3>
                                    {group.description && (
                                        <p className="text-sm text-gray-500 dark:text-gray-400">{group.description}</p>
                                    )}
                                </div>
                                <div className="p-6 grid gap-4 sm:grid-cols-2">
                                    {fields.map((field) => (
                                        <div key={field.key}>
                                            <dt className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-1">
                                                {field.label}
                                            </dt>
                                            <dd className="text-sm">
                                                <FieldValue value={data[field.key]} field={field} />
                                            </dd>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        );
                    })}

                    {/* Metadata */}
                    {(data.createdAt != null || data.updatedAt != null) && (
                        <div className="border-t border-gray-200 dark:border-gray-700 px-6 py-4 bg-gray-50 dark:bg-gray-900/50">
                            <dl className="flex flex-wrap gap-6 text-sm">
                                {data.createdAt != null && (
                                    <div>
                                        <dt className="text-gray-500 dark:text-gray-400">Created</dt>
                                        <dd className="text-gray-900 dark:text-gray-100">
                                            {new Date(data.createdAt as string).toLocaleString()}
                                        </dd>
                                    </div>
                                )}
                                {data.updatedAt != null && (
                                    <div>
                                        <dt className="text-gray-500 dark:text-gray-400">Last Updated</dt>
                                        <dd className="text-gray-900 dark:text-gray-100">
                                            {new Date(data.updatedAt as string).toLocaleString()}
                                        </dd>
                                    </div>
                                )}
                            </dl>
                        </div>
                    )}
                </div>
            </div>

            {/* Delete Confirmation Modal */}
            {showDeleteConfirm && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50">
                    <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl max-w-md w-full mx-4 p-6">
                        <h3 className="text-lg font-semibold text-gray-900 dark:text-gray-100 mb-2">
                            Delete {entityType.name}?
                        </h3>
                        <p className="text-gray-500 dark:text-gray-400 mb-6">
                            Are you sure you want to delete "{String(data[entityType.primaryField] || data.name || data.id)}"?
                            This action cannot be undone.
                        </p>
                        <div className="flex justify-end gap-3">
                            <button
                                onClick={() => setShowDeleteConfirm(false)}
                                disabled={isDeleting}
                                className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-50"
                            >
                                Cancel
                            </button>
                            <button
                                onClick={handleDelete}
                                disabled={isDeleting}
                                className="px-4 py-2 bg-red-600 text-white hover:bg-red-700 rounded-lg transition-colors disabled:opacity-50 flex items-center gap-2"
                            >
                                {isDeleting && <Loader2 className="h-4 w-4 animate-spin" />}
                                Delete
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
