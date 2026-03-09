/**
 * Interactions List Page
 *
 * <p><b>Purpose</b><br>
 * Display all department interactions from YAML configuration with visual
 * flow diagrams showing collaboration, handoffs, and escalations between teams.
 *
 * <p><b>Features</b><br>
 * - Visual flow cards showing source → target
 * - Filter by interaction type
 * - Filter by departments involved
 * - Search across interactions
 * - Interaction type badges (handoff, collaboration, escalation)
 * - Navigate to detail pages
 *
 * @doc.type page
 * @doc.purpose Interactions list and visualization
 * @doc.layer product
 * @doc.pattern List Page
 */

import { useState, useMemo } from 'react';
import { Link } from 'react-router';
import {
    PageLayout,
    PageHeader,
    PageFilters,
    PageGrid,
    LoadingState,
    EmptyState,
} from '@/components/layouts/page';
import { CARD_STYLES } from '@/components/layouts/page/theme';
import {
    GitMerge,
    ArrowRight,
    Building2,
    AlertCircle,
    Users,
    Handshake,
    TrendingUp,
    LucideIcon,
} from 'lucide-react';
import { useInteractions } from '@/hooks/useConfig';
import type { InteractionConfig } from '@/services/api/configApi';

interface InteractionCardProps {
    interaction: InteractionConfig;
}

function InteractionCard({ interaction }: InteractionCardProps) {
    const type = interaction.type || 'collaboration';
    const source = interaction.sourceDepartment || 'Unknown';
    const target = interaction.targetDepartment || 'Unknown';
    const conditions = interaction.trigger?.events || [];

    const typeConfig: Record<
        string,
        { icon: LucideIcon; color: string; bgColor: string; label: string }
    > = {
        handoff: {
            icon: ArrowRight,
            color: 'text-blue-700 dark:text-blue-400',
            bgColor: 'bg-blue-100 dark:bg-blue-900/30',
            label: 'Handoff',
        },
        collaboration: {
            icon: Handshake,
            color: 'text-green-700 dark:text-green-400',
            bgColor: 'bg-green-100 dark:bg-green-900/30',
            label: 'Collaboration',
        },
        escalation: {
            icon: TrendingUp,
            color: 'text-orange-700 dark:text-orange-400',
            bgColor: 'bg-orange-100 dark:bg-orange-900/30',
            label: 'Escalation',
        },
        notification: {
            icon: AlertCircle,
            color: 'text-purple-700 dark:text-purple-400',
            bgColor: 'bg-purple-100 dark:bg-purple-900/30',
            label: 'Notification',
        },
    };

    const config = typeConfig[type] || typeConfig.collaboration;
    const Icon = config.icon;

    return (
        <Link
            to={`/config/interactions/${interaction.id}`}
            className={`block ${CARD_STYLES.base} ${CARD_STYLES.hover} p-6`}
        >
            {/* Header */}
            <div className="flex items-start justify-between mb-4">
                <div className="flex items-center space-x-3 flex-1">
                    <div className={`flex-shrink-0 ${config.bgColor} rounded-lg p-3`}>
                        <Icon className={`h-6 w-6 ${config.color}`} />
                    </div>
                    <div className="min-w-0 flex-1">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-white truncate">
                            {interaction.name}
                        </h3>
                        {interaction.description && (
                            <p className="text-sm text-slate-600 dark:text-slate-400 mt-1 line-clamp-2">
                                {interaction.description}
                            </p>
                        )}
                    </div>
                </div>
                <span
                    className={`px-2 py-1 text-xs font-medium rounded ${config.bgColor} ${config.color}`}
                >
                    {config.label}
                </span>
            </div>

            {/* Flow Visualization */}
            <div className="bg-slate-50 dark:bg-slate-800 rounded-lg p-4 mb-4">
                <div className="flex items-center justify-between">
                    {/* Source Department */}
                    <div className="flex items-center space-x-2 flex-1">
                        <Building2 className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        <div>
                            <p className="text-xs text-slate-500 dark:text-slate-400">From</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-white">{source}</p>
                        </div>
                    </div>

                    {/* Arrow */}
                    <div className="flex-shrink-0 mx-4">
                        <ArrowRight className="h-6 w-6 text-slate-400" />
                    </div>

                    {/* Target Department */}
                    <div className="flex items-center space-x-2 flex-1 justify-end text-right">
                        <div>
                            <p className="text-xs text-slate-500 dark:text-slate-400">To</p>
                            <p className="text-sm font-medium text-slate-900 dark:text-white">{target}</p>
                        </div>
                        <Building2 className="h-5 w-5 text-green-600 dark:text-green-400" />
                    </div>
                </div>
            </div>

            {/* Trigger Conditions */}
            {conditions.length > 0 && (
                <div>
                    <p className="text-xs font-medium text-slate-500 dark:text-slate-400 mb-2">
                        Trigger Conditions:
                    </p>
                    <div className="flex flex-wrap gap-1">
                        {conditions.slice(0, 3).map((condition, idx: number) => (
                            <span
                                key={idx}
                                className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-purple-100 text-purple-800 dark:bg-purple-900/30 dark:text-purple-400"
                            >
                                {condition.type}
                            </span>
                        ))}
                        {conditions.length > 3 && (
                            <span className="inline-flex items-center px-2 py-1 rounded text-xs font-medium bg-slate-100 text-slate-800 dark:bg-slate-700 dark:text-slate-300">
                                +{conditions.length - 3} more
                            </span>
                        )}
                    </div>
                </div>
            )}

            {/* Protocol Info */}
            {interaction.protocol && (
                <div className="mt-4 pt-4 border-t border-slate-100 dark:border-slate-700">
                    <div className="flex items-center text-xs text-slate-500 dark:text-slate-400">
                        <Users className="h-3 w-3 mr-1" />
                        <span>Protocol: {interaction.protocol.type}</span>
                    </div>
                </div>
            )}
        </Link>
    );
}

export function InteractionsListPage() {
    const { data: interactions, isLoading } = useInteractions();
    const [searchQuery, setSearchQuery] = useState('');
    const [typeFilter, setTypeFilter] = useState<string>('all');

    // Extract unique types
    const types = useMemo(() => {
        if (!interactions) return [];
        const typeSet = new Set(
            interactions.map((i) => i.type || 'collaboration').filter(Boolean)
        );
        return Array.from(typeSet).sort();
    }, [interactions]);

    // Build category list for filter
    const categoryList = useMemo(() => {
        return [
            { id: 'all', label: 'All Types' },
            ...types.map(type => ({
                id: type,
                label: type.charAt(0).toUpperCase() + type.slice(1)
            }))
        ];
    }, [types]);

    // Filter interactions
    const filteredInteractions = useMemo(() => {
        if (!interactions) return [];
        return interactions.filter((interaction) => {
            const matchesSearch =
                !searchQuery ||
                interaction.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
                interaction.description
                    ?.toLowerCase()
                    .includes(searchQuery.toLowerCase()) ||
                interaction.id.toLowerCase().includes(searchQuery.toLowerCase());

            const matchesType =
                typeFilter === 'all' ||
                (interaction.type || 'collaboration') === typeFilter;

            return matchesSearch && matchesType;
        });
    }, [interactions, searchQuery, typeFilter]);

    if (isLoading) {
        return (
            <PageLayout title="Department Interactions">
                <LoadingState message="Loading interactions..." />
            </PageLayout>
        );
    }

    return (
        <PageLayout title="Department Interactions">
            <PageHeader
                title="Department Interactions"
                subtitle={`${interactions?.length || 0} interactions configured`}
                icon={GitMerge}
                iconBg="bg-purple-100 dark:bg-purple-900/30"
                iconColor="text-purple-600 dark:text-purple-400"
                backLink="/config"
            />

            <PageFilters
                searchQuery={searchQuery}
                onSearchChange={setSearchQuery}
                searchPlaceholder="Search interactions by name or description..."
                categories={categoryList}
                selectedCategory={typeFilter}
                onCategoryChange={setTypeFilter}
            />

            {filteredInteractions.length === 0 ? (
                <EmptyState
                    icon={GitMerge}
                    title="No interactions found"
                    description={searchQuery || typeFilter !== 'all'
                        ? "Try adjusting your search or filter criteria"
                        : "No interactions have been configured yet"
                    }
                />
            ) : (
                <PageGrid cols={{ sm: 1, md: 2, lg: 3 }}>
                    {filteredInteractions.map((interaction) => (
                        <InteractionCard key={interaction.id} interaction={interaction} />
                    ))}
                </PageGrid>
            )}
        </PageLayout>
    );
}

export default InteractionsListPage;
