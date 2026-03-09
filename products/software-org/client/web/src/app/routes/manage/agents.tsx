/**
 * Agent Marketplace - Browse and Deploy AI Agents
 *
 * Discover, customize, and deploy AI agents for your organization.
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
    PageSection,
    LoadingState,
    ErrorState,
    EmptyState,
} from '@/components/layouts/page';
import { CATEGORY_STYLES, getCategoryStyle, CARD_STYLES } from '@/components/layouts/page/theme';
import { Card, Button } from '@/components/ui';
import {
    Bot,
    Plus,
    Star,
    Download,
    Loader2,
    RefreshCw,
    Sparkles,
    Shield,
    Code,
    Database,
    Settings,
    Zap,
} from 'lucide-react';
import { useAgentTemplates, useDeployAgent, manageQueryKeys } from '@/hooks';
import { useQueryClient } from '@tanstack/react-query';
import type { AgentTemplate, DeployAgentRequest } from '@/hooks/useManageApi';

// Category icons
const CATEGORY_ICONS: Record<string, React.ReactNode> = {
    engineering: <Code className="w-5 h-5" />,
    qa: <Shield className="w-5 h-5" />,
    devops: <Settings className="w-5 h-5" />,
    security: <Shield className="w-5 h-5" />,
    data: <Database className="w-5 h-5" />,
    management: <Zap className="w-5 h-5" />,
};

// Pricing badge colors
const PRICING_COLORS: Record<string, string> = {
    free: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    standard: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
    premium: 'bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400',
};

// Agent template card
function AgentCard({
    template,
    onDeploy,
    isDeploying,
}: {
    template: AgentTemplate;
    onDeploy: (template: AgentTemplate) => void;
    isDeploying: boolean;
}) {
    const categoryStyle = getCategoryStyle(template.category);

    return (
        <Card className={`p-4 ${CARD_STYLES.hover}`}>
            {/* Header */}
            <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                    <div className={`w-12 h-12 rounded-lg ${categoryStyle.bg} ${categoryStyle.icon} flex items-center justify-center`}>
                        {CATEGORY_ICONS[template.category] || <Bot className="w-6 h-6" />}
                    </div>
                    <div>
                        <div className="flex items-center gap-2">
                            <h3 className="font-semibold text-slate-900 dark:text-white">
                                {template.name}
                            </h3>
                            {template.isNew && (
                                <span className="px-1.5 py-0.5 bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400 text-xs rounded-full">
                                    New
                                </span>
                            )}
                            {template.isFeatured && (
                                <Sparkles className="w-4 h-4 text-yellow-500" />
                            )}
                        </div>
                        <p className="text-sm text-slate-500 dark:text-slate-400">
                            {typeof template.role === 'string'
                                ? template.role
                                : (template.role as any)?.title || (template.role as any)?.name || 'Agent'}
                        </p>
                    </div>
                </div>
                {template.pricing && (
                    <span className={`px-2 py-1 text-xs font-medium rounded-full ${PRICING_COLORS[template.pricing.type] || PRICING_COLORS.standard}`}>
                        {template.pricing.type}
                    </span>
                )}
            </div>

            {/* Description */}
            <p className="text-sm text-slate-600 dark:text-slate-400 mb-3 line-clamp-2">
                {template.description}
            </p>

            {/* Capabilities */}
            <div className="flex flex-wrap gap-1 mb-3">
                {(Array.isArray(template.capabilities) ? template.capabilities : []).slice(0, 3).map((cap, i) => (
                    <span
                        key={i}
                        className="px-2 py-0.5 bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-400 text-xs rounded-full"
                    >
                        {cap}
                    </span>
                ))}
                {(Array.isArray(template.capabilities) ? template.capabilities : []).length > 3 && (
                    <span className="px-2 py-0.5 text-slate-400 text-xs">
                        +{(Array.isArray(template.capabilities) ? template.capabilities : []).length - 3} more
                    </span>
                )}
            </div>

            {/* Stats & Actions */}
            <div className="flex items-center justify-between pt-3 border-t border-slate-200 dark:border-slate-700">
                <div className="flex items-center gap-4 text-sm text-slate-500">
                    <span className="flex items-center gap-1">
                        <Star className="w-4 h-4 text-yellow-500" />
                        {template.rating.toFixed(1)}
                    </span>
                    <span className="flex items-center gap-1">
                        <Download className="w-4 h-4" />
                        {template.popularity}
                    </span>
                </div>
                <Button
                    size="sm"
                    onClick={() => onDeploy(template)}
                    disabled={isDeploying}
                    className="flex items-center gap-1"
                >
                    {isDeploying ? (
                        <Loader2 className="w-3 h-3 animate-spin" />
                    ) : (
                        <Plus className="w-3 h-3" />
                    )}
                    Deploy
                </Button>
            </div>
        </Card>
    );
}

export default function AgentsMarketplacePage() {
    const [selectedTenant] = useAtom(selectedTenantAtom);
    const tenantId = selectedTenant || '';
    const queryClient = useQueryClient();

    const [selectedCategory, setSelectedCategory] = useState<string>('all');
    const [searchQuery, setSearchQuery] = useState('');
    const [deployingId, setDeployingId] = useState<string | null>(null);

    // Fetch agent templates from API
    const {
        data: templates = [],
        isLoading,
        error,
        refetch,
    } = useAgentTemplates(selectedCategory !== 'all' ? selectedCategory : undefined);

    // Deploy mutation
    const deployMutation = useDeployAgent();

    const handleDeploy = useCallback(async (template: AgentTemplate) => {
        setDeployingId(template.id);
        try {
            await deployMutation.mutateAsync({
                templateId: template.id,
                name: `${template.name} Instance`,
                departmentId: '', // TODO: Show department selector modal
            });
            // TODO: Show success toast
        } catch (error) {
            console.error('Failed to deploy agent:', error);
            // TODO: Show error toast
        } finally {
            setDeployingId(null);
        }
    }, [deployMutation]);

    const handleRefresh = useCallback(() => {
        queryClient.invalidateQueries({ queryKey: manageQueryKeys.agents.all });
        refetch();
    }, [queryClient, refetch]);

    // Filter templates
    const filteredTemplates = useMemo(() => templates.filter(template => {
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            const roleStr = typeof template.role === 'string'
                ? template.role
                : (template.role as any)?.title || (template.role as any)?.name || '';
            return (
                template.name.toLowerCase().includes(query) ||
                template.description.toLowerCase().includes(query) ||
                roleStr.toLowerCase().includes(query) ||
                (Array.isArray(template.capabilities) && template.capabilities.some(c => c.toLowerCase().includes(query)))
            );
        }
        return true;
    }), [templates, searchQuery]);

    const categories = useMemo(() => [
        { id: 'all', label: 'All' },
        { id: 'engineering', label: 'Engineering' },
        { id: 'qa', label: 'QA' },
        { id: 'devops', label: 'DevOps' },
        { id: 'security', label: 'Security' },
        { id: 'data', label: 'Data' },
        { id: 'management', label: 'Management' },
    ], []);

    const featuredTemplates = useMemo(() =>
        filteredTemplates.filter(t => t.isFeatured),
        [filteredTemplates]);

    const regularTemplates = useMemo(() =>
        filteredTemplates.filter(t => !t.isFeatured),
        [filteredTemplates]);

    // Loading state
    if (isLoading) {
        return (
            <PageLayout title="Agent Marketplace" subtitle="Browse and deploy AI agents">
                <LoadingState message="Loading agents..." />
            </PageLayout>
        );
    }

    // Error state
    if (error) {
        return (
            <PageLayout title="Agent Marketplace" subtitle="Browse and deploy AI agents">
                <ErrorState
                    title="Failed to load agents"
                    message={(error as Error).message || 'An error occurred while loading data'}
                    onRetry={handleRefresh}
                />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Agent Marketplace" subtitle="Browse and deploy AI agents">
            <PageHeader
                title="Agent Marketplace"
                subtitle="Browse and deploy AI agents"
                icon={Bot}
                iconBg="bg-blue-100 dark:bg-blue-900/30"
                iconColor="text-blue-600 dark:text-blue-400"
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search agents..."
                categories={categories}
                selectedCategory={selectedCategory}
                onCategoryChange={setSelectedCategory}
                showRefresh
                onRefresh={handleRefresh}
            />

            {/* Featured Section */}
            {featuredTemplates.length > 0 && (
                <PageSection
                    title="Featured Agents"
                    subtitle={`${featuredTemplates.length} featured`}
                >
                    <div className="flex items-center gap-2 mb-4">
                        <Sparkles className="w-5 h-5 text-yellow-500" />
                        <span className="text-lg font-semibold text-slate-900 dark:text-white">Featured Agents</span>
                    </div>
                    <PageGrid cols={{ sm: 1, md: 2, lg: 3 }}>
                        {featuredTemplates.map(template => (
                            <AgentCard
                                key={template.id}
                                template={template}
                                onDeploy={handleDeploy}
                                isDeploying={deployingId === template.id}
                            />
                        ))}
                    </PageGrid>
                </PageSection>
            )}

            {/* All Agents */}
            <PageSection
                title={selectedCategory === 'all' ? 'All Agents' : `${selectedCategory.charAt(0).toUpperCase() + selectedCategory.slice(1)} Agents`}
                subtitle={`${regularTemplates.length} agent${regularTemplates.length !== 1 ? 's' : ''}`}
            >
                {filteredTemplates.length === 0 ? (
                    <EmptyState
                        icon={Bot}
                        title="No agents found"
                        description={searchQuery
                            ? 'No agents match your search criteria.'
                            : 'No agents available in this category.'}
                    />
                ) : (
                    <PageGrid cols={{ sm: 1, md: 2, lg: 3 }}>
                        {regularTemplates.map(template => (
                            <AgentCard
                                key={template.id}
                                template={template}
                                onDeploy={handleDeploy}
                                isDeploying={deployingId === template.id}
                            />
                        ))}
                    </PageGrid>
                )}
            </PageSection>
        </PageLayout>
    );
}
