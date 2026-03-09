/**
 * Norms - Organization Rules & Policies Editor
 *
 * Define and manage the norms that govern agent behavior.
 * Supports natural language to rule translation with AI.
 * Connected to backend API for real data.
 *
 * @doc.type route
 * @doc.section MANAGE
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useAtom } from 'jotai';
import { selectedTenantAtom } from '@/state/jotai/session.store';
import {
    PageLayout,
    PageHeader,
    PageFilters,
    PageGrid,
    LoadingState,
    ErrorState,
    EmptyState,
} from '@/components/layouts/page';
import { CARD_STYLES, STATUS_STYLES, getStatusStyle } from '@/components/layouts/page/theme';
import { Card, Button } from '@/components/ui';
import {
    ScrollText,
    Plus,
    Edit2,
    Trash2,
    ToggleLeft,
    ToggleRight,
    Shield,
    AlertTriangle,
    CheckCircle,
    Info,
    Loader2,
    RefreshCw,
} from 'lucide-react';
import { useNorms, useCreateNorm, useUpdateNorm, useDeleteNorm, useToggleNorm, manageQueryKeys } from '@/hooks';
import { useQueryClient } from '@tanstack/react-query';
import type { Norm, CreateNormRequest } from '@/hooks/useManageApi';

// Category colors with dark mode support
const CATEGORY_COLORS: Record<string, { bg: string; text: string; icon: React.ReactNode }> = {
    quality: { bg: 'bg-blue-100 dark:bg-blue-900/30', text: 'text-blue-700 dark:text-blue-400', icon: <CheckCircle className="w-4 h-4" /> },
    security: { bg: 'bg-red-100 dark:bg-red-900/30', text: 'text-red-700 dark:text-red-400', icon: <Shield className="w-4 h-4" /> },
    compliance: { bg: 'bg-purple-100 dark:bg-purple-900/30', text: 'text-purple-700 dark:text-purple-400', icon: <ScrollText className="w-4 h-4" /> },
    operational: { bg: 'bg-orange-100 dark:bg-orange-900/30', text: 'text-orange-700 dark:text-orange-400', icon: <AlertTriangle className="w-4 h-4" /> },
    custom: { bg: 'bg-slate-100 dark:bg-slate-800', text: 'text-slate-700 dark:text-slate-400', icon: <Info className="w-4 h-4" /> },
};

// Severity colors
const SEVERITY_COLORS: Record<string, string> = {
    info: 'bg-blue-500',
    warning: 'bg-yellow-500',
    error: 'bg-orange-500',
    critical: 'bg-red-500',
};

// Norm card component
function NormCard({
    norm,
    onEdit,
    onDelete,
    onToggle,
    isToggling,
}: {
    norm: Norm;
    onEdit: (norm: Norm) => void;
    onDelete: (id: string) => void;
    onToggle: (id: string, enabled: boolean) => void;
    isToggling: boolean;
}) {
    const category = CATEGORY_COLORS[norm.category] || CATEGORY_COLORS.custom;

    return (
        <Card className={`p-4 ${!norm.enabled ? 'opacity-60' : ''} ${CARD_STYLES.base}`}>
            <div className="flex items-start justify-between gap-4">
                <div className="flex-1">
                    {/* Header */}
                    <div className="flex items-center gap-2 mb-2">
                        <span className={`inline-flex items-center gap-1 px-2 py-1 rounded-full text-xs font-medium ${category.bg} ${category.text}`}>
                            {category.icon}
                            {norm.category}
                        </span>
                        <span className={`w-2 h-2 rounded-full ${SEVERITY_COLORS[norm.severity] || SEVERITY_COLORS.info}`} />
                        <span className="text-xs text-slate-500 dark:text-slate-400">{norm.severity}</span>
                    </div>

                    {/* Name & Description */}
                    <h3 className="font-semibold text-slate-900 dark:text-white mb-1">
                        {norm.name}
                    </h3>
                    <p className="text-sm text-slate-600 dark:text-slate-400 mb-2">
                        {norm.description}
                    </p>

                    {/* Rule */}
                    <div className="p-2 bg-slate-50 dark:bg-slate-800 rounded text-sm font-mono text-slate-700 dark:text-slate-300">
                        {norm.rule}
                    </div>

                    {/* Metadata */}
                    <div className="flex items-center gap-4 mt-3 text-xs text-slate-500 dark:text-slate-400">
                        <span>Enforcement: {norm.enforcementLevel}</span>
                        <span>Scope: {norm.scope?.join(', ') || 'All'}</span>
                    </div>
                </div>

                {/* Actions */}
                <div className="flex flex-col items-end gap-2">
                    <button
                        onClick={() => onToggle(norm.id, !norm.enabled)}
                        disabled={isToggling}
                        className="text-slate-500 hover:text-slate-700 dark:hover:text-slate-300"
                    >
                        {isToggling ? (
                            <Loader2 className="w-5 h-5 animate-spin" />
                        ) : norm.enabled ? (
                            <ToggleRight className="w-6 h-6 text-green-500" />
                        ) : (
                            <ToggleLeft className="w-6 h-6" />
                        )}
                    </button>
                    <button
                        onClick={() => onEdit(norm)}
                        className="p-1 text-slate-500 hover:text-blue-500"
                    >
                        <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                        onClick={() => onDelete(norm.id)}
                        className="p-1 text-slate-500 hover:text-red-500"
                    >
                        <Trash2 className="w-4 h-4" />
                    </button>
                </div>
            </div>
        </Card>
    );
}

export default function NormsPage() {
    const [selectedTenant] = useAtom(selectedTenantAtom);
    const tenantId = selectedTenant || '';
    const queryClient = useQueryClient();

    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [togglingId, setTogglingId] = useState<string | null>(null);

    // Fetch norms from API
    const {
        data: norms = [],
        isLoading,
        error,
        refetch,
    } = useNorms(tenantId, selectedCategory !== 'all' ? selectedCategory : undefined);

    // Mutations
    const toggleMutation = useToggleNorm();
    const deleteMutation = useDeleteNorm();

    const handleToggle = useCallback(async (id: string, enabled: boolean) => {
        setTogglingId(id);
        try {
            await toggleMutation.mutateAsync({ id, enabled });
        } finally {
            setTogglingId(null);
        }
    }, [toggleMutation]);

    const handleDelete = useCallback((id: string) => {
        if (confirm('Are you sure you want to delete this norm?')) {
            deleteMutation.mutate(id);
        }
    }, [deleteMutation]);

    const handleEdit = useCallback((norm: Norm) => {
        console.log('Edit norm:', norm);
        // TODO: Open edit modal
    }, []);

    const handleAddNorm = useCallback(() => {
        console.log('Add new norm');
        // TODO: Open create modal
    }, []);

    const handleRefresh = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: manageQueryKeys.norms.all });
        refetch();
    }, [queryClient, refetch]);

    // Filter norms
    const filteredNorms = useMemo(() => norms.filter(norm => {
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            return (
                norm.name.toLowerCase().includes(query) ||
                norm.description.toLowerCase().includes(query) ||
                norm.rule.toLowerCase().includes(query)
            );
        }
        return true;
    }), [norms, searchQuery]);

    const categories = useMemo(() => [
        { id: 'all', label: 'All' },
        { id: 'quality', label: 'Quality' },
        { id: 'security', label: 'Security' },
        { id: 'compliance', label: 'Compliance' },
        { id: 'operational', label: 'Operational' },
        { id: 'custom', label: 'Custom' },
    ], []);

    // Loading state
    if (isLoading) {
        return (
            <PageLayout title="Norms & Policies" subtitle="Rules that govern agent behavior">
                <LoadingState message="Loading norms..." />
            </PageLayout>
        );
    }

    // Error state
    if (error) {
        return (
            <PageLayout title="Norms & Policies" subtitle="Rules that govern agent behavior">
                <ErrorState
                    title="Failed to load norms"
                    message={(error as Error).message || 'An error occurred while loading data'}
                    onRetry={handleRefresh}
                />
            </PageLayout>
        );
    }

    // Add Norm action button
    const addNormAction = (
        <Button onClick={handleAddNorm} className="flex items-center gap-2">
            <Plus className="w-4 h-4" />
            Add Norm
        </Button>
    );

    return (
        <PageLayout title="Norms & Policies" subtitle="Rules that govern agent behavior">
            <PageHeader
                title="Norms & Policies"
                subtitle="Rules that govern agent behavior"
                icon={ScrollText}
                iconBg="bg-purple-100 dark:bg-purple-900/30"
                iconColor="text-purple-600 dark:text-purple-400"
                actions={addNormAction}
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search norms..."
                categories={categories}
                selectedCategory={selectedCategory}
                onCategoryChange={setSelectedCategory}
                showRefresh
                onRefresh={handleRefresh}
            />

            {/* Stats */}
            <PageGrid cols={{ sm: 2, md: 4, lg: 4 }} gap="md" className="mb-6">
                <Card className="p-4">
                    <div className="text-2xl font-bold text-slate-900 dark:text-white">{norms.length}</div>
                    <div className="text-sm text-slate-500 dark:text-slate-400">Total Norms</div>
                </Card>
                <Card className="p-4">
                    <div className="text-2xl font-bold text-green-600 dark:text-green-400">{norms.filter(n => n.enabled).length}</div>
                    <div className="text-sm text-slate-500 dark:text-slate-400">Active</div>
                </Card>
                <Card className="p-4">
                    <div className="text-2xl font-bold text-red-600 dark:text-red-400">{norms.filter(n => n.category === 'security').length}</div>
                    <div className="text-sm text-slate-500 dark:text-slate-400">Security Rules</div>
                </Card>
                <Card className="p-4">
                    <div className="text-2xl font-bold text-purple-600 dark:text-purple-400">{norms.filter(n => n.category === 'compliance').length}</div>
                    <div className="text-sm text-slate-500 dark:text-slate-400">Compliance</div>
                </Card>
            </PageGrid>

            {/* Norms List */}
            {filteredNorms.length === 0 ? (
                <EmptyState
                    icon={ScrollText}
                    title="No norms found"
                    description={searchQuery
                        ? 'No norms match your search criteria.'
                        : 'Start defining the rules and policies that govern your organization.'}
                    actionLabel="Create First Norm"
                    onAction={handleAddNorm}
                />
            ) : (
                <div className="space-y-4">
                    {filteredNorms.map(norm => (
                        <NormCard
                            key={norm.id}
                            norm={norm}
                            onEdit={handleEdit}
                            onDelete={handleDelete}
                            onToggle={handleToggle}
                            isToggling={togglingId === norm.id}
                        />
                    ))}
                </div>
            )}
        </PageLayout>
    );
}
