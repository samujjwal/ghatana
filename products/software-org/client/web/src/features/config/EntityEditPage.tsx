/**
 * Entity Edit Page Component
 *
 * A unified create/edit page that wraps EntityForm with consistent layout,
 * breadcrumbs, and data handling.
 *
 * @doc.type component
 * @doc.purpose Entity create/edit page wrapper
 * @doc.layer product
 * @doc.pattern Page
 */

import { useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router';
import type { EntityTypeDefinition, FieldOption } from './entity-registry';
import { EntityForm } from './EntityForm';
import { ChevronRight, Settings, Loader2, ArrowLeft } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface EntityEditPageProps {
    /** Entity type definition */
    entityType: EntityTypeDefinition;
    /** Mode: create or edit */
    mode: 'create' | 'edit';
    /** Entity ID (for edit mode) */
    entityId?: string;
    /** Fetch entity data (for edit mode) */
    fetchEntity?: (id: string) => Promise<Record<string, unknown>>;
    /** Create entity handler */
    createEntity?: (data: Record<string, unknown>) => Promise<void>;
    /** Update entity handler */
    updateEntity?: (id: string, data: Record<string, unknown>) => Promise<void>;
    /** Load dynamic options for select fields */
    loadDynamicOptions?: () => Promise<Record<string, FieldOption[]>>;
}

// ============================================================================
// Main Component
// ============================================================================

export function EntityEditPage({
    entityType,
    mode,
    entityId,
    fetchEntity,
    createEntity,
    updateEntity,
    loadDynamicOptions,
}: EntityEditPageProps) {
    const [isLoading, setIsLoading] = useState(mode === 'edit');
    const [isSaving, setIsSaving] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [initialValues, setInitialValues] = useState<Record<string, unknown>>({});
    const [dynamicOptions, setDynamicOptions] = useState<Record<string, FieldOption[]>>({});
    const navigate = useNavigate();

    const Icon = entityType.icon;

    // Load entity data for edit mode
    useEffect(() => {
        const loadData = async () => {
            if (mode === 'edit' && entityId && fetchEntity) {
                try {
                    setIsLoading(true);
                    const data = await fetchEntity(entityId);
                    setInitialValues(data);
                } catch (err) {
                    setError(err instanceof Error ? err.message : 'Failed to load entity');
                } finally {
                    setIsLoading(false);
                }
            }

            // Load dynamic options
            if (loadDynamicOptions) {
                try {
                    const options = await loadDynamicOptions();
                    setDynamicOptions(options);
                } catch (err) {
                    console.error('Failed to load dynamic options:', err);
                }
            }
        };

        loadData();
    }, [mode, entityId, fetchEntity, loadDynamicOptions]);

    // Handle form submission
    const handleSubmit = async (data: Record<string, unknown>) => {
        try {
            setIsSaving(true);
            setError(null);

            if (mode === 'create' && createEntity) {
                await createEntity(data);
            } else if (mode === 'edit' && entityId && updateEntity) {
                await updateEntity(entityId, data);
            }

            // Navigate back to list on success
            navigate(`/config/${entityType.routePath}`);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to save');
        } finally {
            setIsSaving(false);
        }
    };

    // Handle cancel
    const handleCancel = () => {
        navigate(-1);
    };

    // Loading state
    if (isLoading) {
        return (
            <div className="min-h-screen bg-gray-50 dark:bg-gray-900 flex items-center justify-center">
                <div className="text-center">
                    <Loader2 className="h-12 w-12 text-blue-600 animate-spin mx-auto mb-4" />
                    <p className="text-gray-500 dark:text-gray-400">
                        Loading {entityType.name.toLowerCase()}...
                    </p>
                </div>
            </div>
        );
    }

    const pageTitle = mode === 'create' ? `New ${entityType.name}` : `Edit ${entityType.name}`;

    return (
        <div className="min-h-screen bg-gray-50 dark:bg-gray-900">
            <div className="max-w-4xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
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
                        {pageTitle}
                    </span>
                </nav>

                {/* Back Link */}
                <button
                    onClick={() => navigate(-1)}
                    className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-300 mb-6"
                >
                    <ArrowLeft className="h-4 w-4" />
                    Back
                </button>

                {/* Header */}
                <div className="flex items-center gap-4 mb-8">
                    <div className={`p-3 rounded-xl ${entityType.bgClass}`}>
                        <Icon className={`h-8 w-8 ${entityType.colorClass}`} />
                    </div>
                    <div>
                        <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
                            {pageTitle}
                        </h1>
                        <p className="text-gray-500 dark:text-gray-400">
                            {mode === 'create'
                                ? `Create a new ${entityType.name.toLowerCase()}`
                                : `Update ${entityType.name.toLowerCase()} details`}
                        </p>
                    </div>
                </div>

                {/* Error Alert */}
                {error && (
                    <div className="mb-6 p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                        <p className="text-red-600 dark:text-red-400">{error}</p>
                    </div>
                )}

                {/* Form Card */}
                <div className="bg-white dark:bg-gray-800 rounded-xl border border-gray-200 dark:border-gray-700 p-6">
                    <EntityForm
                        entityType={entityType}
                        initialValues={initialValues}
                        dynamicOptions={dynamicOptions}
                        onSubmit={handleSubmit}
                        onCancel={handleCancel}
                        isLoading={isSaving}
                        mode={mode}
                        showCancel
                    />
                </div>
            </div>
        </div>
    );
}
