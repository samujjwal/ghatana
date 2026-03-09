/**
 * Stage Dashboard (Dynamic Route)
 *
 * Displays detailed view of a specific DevSecOps stage including KPIs,
 * work items, timeline, and stage-specific actions.
 *
 * @doc.type route
 * @doc.section OPERATE
 */

import { MainLayout } from '@/app/Layout';
import { useParams, Link } from 'react-router';
import { useState, useMemo } from 'react';
import type { StageHealth } from '@/types/devsecops';
import { useStages } from '@/hooks/useConfig';
import { useDevSecOpsItems, useStageHealth } from '@/hooks/useDevSecOpsApi';
import { getStageMetadata } from '@/lib/devsecops/stageMetadata';
import { ConnectionStatus } from '@/features/devsecops/ConnectionStatus';
import { useStageUpdates } from '@/features/devsecops/useDevSecOpsUpdates';
import { mockDevSecOpsItems } from '@/features/devsecops/mockDevSecOpsItems';
import { mockStageEvents } from '@/features/devsecops/mockStageEvents';
import {
    ArrowLeft,
    FileText,
    GitBranch,
    Shield,
    Rocket,
} from 'lucide-react';
import {
  KanbanBoard,
  DataTable,
  Timeline,
  SearchBar,
  FilterPanel,
} from '@ghatana/yappc-ui';
import type { Item, ItemFilter } from '@ghatana/yappc-types/devsecops';
import { Badge, KpiCard } from '@/components/ui';

// Mock stage health data
const mockStageHealth: Record<string, StageHealth> = {
    plan: { stage: 'plan', status: 'on-track', itemsTotal: 12, itemsCompleted: 10, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    design: { stage: 'design', status: 'on-track', itemsTotal: 8, itemsCompleted: 6, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    develop: { stage: 'develop', status: 'at-risk', itemsTotal: 25, itemsCompleted: 18, itemsBlocked: 2, itemsInProgress: 5, criticalIssues: 1, lastUpdated: new Date().toISOString() },
    build: { stage: 'build', status: 'on-track', itemsTotal: 30, itemsCompleted: 28, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    test: { stage: 'test', status: 'at-risk', itemsTotal: 45, itemsCompleted: 35, itemsBlocked: 3, itemsInProgress: 7, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    secure: { stage: 'secure', status: 'blocked', itemsTotal: 15, itemsCompleted: 8, itemsBlocked: 5, itemsInProgress: 2, criticalIssues: 3, lastUpdated: new Date().toISOString() },
    compliance: { stage: 'compliance', status: 'on-track', itemsTotal: 10, itemsCompleted: 9, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    staging: { stage: 'staging', status: 'on-track', itemsTotal: 6, itemsCompleted: 5, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    deploy: { stage: 'deploy', status: 'blocked', itemsTotal: 8, itemsCompleted: 3, itemsBlocked: 4, itemsInProgress: 1, criticalIssues: 2, lastUpdated: new Date().toISOString() },
    operate: { stage: 'operate', status: 'on-track', itemsTotal: 20, itemsCompleted: 18, itemsBlocked: 0, itemsInProgress: 2, criticalIssues: 0, lastUpdated: new Date().toISOString() },
    monitor: { stage: 'monitor', status: 'on-track', itemsTotal: 15, itemsCompleted: 14, itemsBlocked: 0, itemsInProgress: 1, criticalIssues: 0, lastUpdated: new Date().toISOString() },
};

// Placeholder: YAPPC UI components not yet available
// TODO: Implement proper KanbanBoard, DataTable, Timeline components

// Convert DevSecOpsItem to YAPPC Item type
const convertToYappcItem = (item: typeof mockDevSecOpsItems[string][number], phase: string): Item => ({
    id: item.id,
    title: item.title,
    description: item.description || '',
    type: 'task',
    status: item.status,
    priority: item.priority,
    phaseId: phase,
    owners: [{ id: '1', name: 'Unknown', email: 'unknown@example.com', role: 'Developer' }],
    tags: [],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
    progress: item.status === 'completed' ? 100 : item.status === 'in-progress' ? 50 : 0,
    artifacts: [],
});

export default function StageDashboardPage() {
    const { stageKey } = useParams<{ stageKey: string }>();
    const { data: stageMappings, isLoading: isLoadingStages } = useStages();

    // Enrich stage with metadata
    const stageMapping = stageMappings?.find((s) => s.stage === stageKey);
    const stage = useMemo(() => {
        if (!stageMapping) return null;
        return {
            ...stageMapping,
            ...getStageMetadata(stageMapping.stage)
        };
    }, [stageMapping]);

    // Fetch DevSecOps items from API (with fallback to mock data)
    const { data: apiItems } = useDevSecOpsItems({
        stage: stageKey,
    });

    // Memoize items selection to avoid dependency issues
    const devSecOpsItems = useMemo(() => {
        return apiItems && apiItems.length > 0
            ? apiItems
            : (stageKey ? mockDevSecOpsItems[stageKey] || [] : []);
    }, [apiItems, stageKey]);

    // Fetch stage health from API (with fallback to mock data)
    const { data: apiHealth } = useStageHealth(stageKey || '');
    const [stageHealth, setStageHealth] = useState<StageHealth | undefined>(
        stageKey ? mockStageHealth[stageKey] : undefined
    );

    // Update health when API data changes
    useMemo(() => {
        if (apiHealth) {
            setStageHealth(apiHealth);
        }
    }, [apiHealth]);

    // Convert DevSecOpsItems to YAPPC Items
    const yappcItems = useMemo(
        () => devSecOpsItems.map(item => convertToYappcItem(item, stage?.stage || 'develop')),
        [devSecOpsItems, stage]
    );

    // Search and filter state
    const [searchQuery, setSearchQuery] = useState('');
    const [filters, setFilters] = useState<ItemFilter>({
        phaseIds: [],
        status: [],
        priority: [],
        tags: [],
    });
    const [filterPanelOpen, setFilterPanelOpen] = useState(false);
    const [viewMode, setViewMode] = useState<'kanban' | 'table' | 'timeline'>('kanban');

    // Apply search and filter panel filters
    const filteredItems = useMemo(() => {
        let items = yappcItems;

        // Apply search query
        if (searchQuery) {
            const query = searchQuery.toLowerCase();
            items = items.filter(item =>
                item.title.toLowerCase().includes(query) ||
                (item.description?.toLowerCase() || '').includes(query)
            );
        }

        // Apply status filter
        if (filters.status && filters.status.length > 0) {
            items = items.filter(item => filters.status!.includes(item.status));
        }

        // Apply priority filter
        if (filters.priority && filters.priority.length > 0) {
            items = items.filter(item => filters.priority!.includes(item.priority));
        }

        // Apply tags filter
        if (filters.tags && filters.tags.length > 0) {
            items = items.filter(item =>
                filters.tags!.some(tag => item.tags.includes(tag))
            );
        }

        return items;
    }, [yappcItems, searchQuery, filters]);

    // Real-time WebSocket updates
    const { isConnected, isReconnecting, lastUpdate, reconnect } = useStageUpdates(
        stageKey || '',
        {
            notifications: true,
            onHealthUpdate: (data) => {
                setStageHealth(data.health);
            },
        }
    );

    // Legacy health fallback for mock data
    const health = stageHealth;

    if (!stage || isLoadingStages) {
        return (
            <MainLayout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <div className="text-slate-600 dark:text-neutral-400">Loading stage...</div>
                    </div>
                </div>
            </MainLayout>
        );
    }

    if (!stage) {
        return (
            <MainLayout>
                <div className="flex items-center justify-center h-96">
                    <div className="text-center">
                        <h2 className="text-2xl font-bold text-slate-900 dark:text-neutral-100 mb-2">
                            Stage not found
                        </h2>
                        <p className="text-slate-600 dark:text-neutral-400 mb-4">
                            The stage "{stageKey}" does not exist
                        </p>
                        <Link
                            to="/operate/stages"
                            className="inline-flex items-center gap-2 text-blue-600 dark:text-blue-400 hover:underline"
                        >
                            <ArrowLeft className="h-4 w-4" />
                            Back to stages
                        </Link>
                    </div>
                </div>
            </MainLayout>
        );
    }

    const completionRate = health ? Math.round((health.itemsCompleted / health.itemsTotal) * 100) : 0;

    return (
        <MainLayout>
            <div className="space-y-6">
                {/* Breadcrumb */}
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                    <Link to="/" className="hover:text-blue-600 dark:hover:text-blue-400">
                        Dashboard
                    </Link>
                    <span>/</span>
                    <Link to="/operate/stages" className="hover:text-blue-600 dark:hover:text-blue-400">
                        Stages
                    </Link>
                    <span>/</span>
                    <span className="text-slate-900 dark:text-neutral-100">{stage.label}</span>
                </div>

                {/* Header */}
                <div className="flex items-start justify-between">
                    <div>
                        <h1 className="text-2xl font-bold text-slate-900 dark:text-neutral-100">
                            {stage.label} Stage
                        </h1>
                        <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                            {stage.description}
                        </p>
                        <div className="flex items-center gap-2 mt-2">
                            <Badge variant="neutral">Category: {stage.category}</Badge>
                            {stage.phases.map((phase) => (
                                <Badge key={phase} variant="primary">
                                    {phase}
                                </Badge>
                            ))}
                        </div>
                    </div>
                    <div className="flex flex-col items-end gap-3">
                        <ConnectionStatus
                            isConnected={isConnected}
                            isReconnecting={isReconnecting}
                            lastUpdate={lastUpdate}
                            onReconnect={reconnect}
                        />
                        {health && (
                            <div className="text-right">
                                <p className="text-xs text-slate-500 dark:text-neutral-500">Last updated</p>
                                <p className="text-sm text-slate-900 dark:text-neutral-100">
                                    {new Date(health.lastUpdated).toLocaleString()}
                                </p>
                            </div>
                        )}
                    </div>
                </div>

                {/* KPIs */}
                {health && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                        <KpiCard
                            title="Completion Rate"
                            value={`${completionRate}%`}
                            subtitle={`${health.itemsCompleted} of ${health.itemsTotal} items`}
                            trend={completionRate >= 80 ? 'up' : 'down'}
                            trendValue={completionRate >= 80 ? '+12%' : '-5%'}
                        />
                        <KpiCard
                            title="In Progress"
                            value={health.itemsInProgress}
                            subtitle="Active work items"
                        />
                        <KpiCard
                            title="Blocked Items"
                            value={health.itemsBlocked}
                            subtitle="Requiring attention"
                            trend={health.itemsBlocked === 0 ? 'up' : 'down'}
                            trendValue={health.itemsBlocked === 0 ? 'All clear' : 'Needs attention'}
                        />
                        <KpiCard
                            title="Critical Issues"
                            value={health.criticalIssues}
                            subtitle="High priority problems"
                            trend={health.criticalIssues === 0 ? 'up' : 'down'}
                            trendValue={health.criticalIssues === 0 ? 'None' : 'Urgent'}
                        />
                    </div>
                )}

                {/* Work Items Board/Table */}
                {yappcItems.length > 0 && (
                    <div className="space-y-4">
                        <div className="flex items-center justify-between">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                                Work Items
                            </h2>
                            <Badge variant="neutral">
                                {filteredItems.length} of {devSecOpsItems.length} item{devSecOpsItems.length !== 1 ? 's' : ''}
                            </Badge>
                        </div>

                        {/* Search and Filters */}
                        <div className="flex items-center gap-4 mb-4">
                            <div className="flex-1">
                                <SearchBar
                                    value={searchQuery}
                                    onChange={setSearchQuery}
                                    placeholder="Search work items..."
                                    resultsCount={filteredItems.length}
                                />
                            </div>
                            <button
                                onClick={() => setFilterPanelOpen(true)}
                                className="px-4 py-2 bg-white dark:bg-slate-800 border border-gray-200 dark:border-slate-700 rounded-lg text-sm font-medium hover:bg-slate-50 dark:hover:bg-slate-700"
                            >
                                Filters
                            </button>
                        </div>

                        {/* Filter Panel */}
                        <FilterPanel
                            filters={filters}
                            onChange={setFilters}
                            open={filterPanelOpen}
                            onClose={() => setFilterPanelOpen(false)}
                            variant="drawer"
                            availableTags={Array.from(new Set(yappcItems.flatMap(item => item.tags)))}
                        />

                        {/* View Mode Switcher */}
                        <div className="flex items-center gap-2 mb-4">
                            <button
                                onClick={() => setViewMode('kanban')}
                                className={`px-4 py-2 rounded-lg text-sm font-medium ${viewMode === 'kanban'
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-white dark:bg-slate-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-slate-700'
                                    }`}
                            >
                                Kanban
                            </button>
                            <button
                                onClick={() => setViewMode('table')}
                                className={`px-4 py-2 rounded-lg text-sm font-medium ${viewMode === 'table'
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-white dark:bg-slate-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-slate-700'
                                    }`}
                            >
                                Table
                            </button>
                            <button
                                onClick={() => setViewMode('timeline')}
                                className={`px-4 py-2 rounded-lg text-sm font-medium ${viewMode === 'timeline'
                                        ? 'bg-blue-600 text-white'
                                        : 'bg-white dark:bg-slate-800 text-gray-700 dark:text-gray-300 border border-gray-200 dark:border-slate-700'
                                    }`}
                            >
                                Timeline
                            </button>
                        </div>

                        {/* Work Items View */}
                        {viewMode === 'kanban' && (
                            <KanbanBoard
                                items={filteredItems}
                                onItemClick={(item: Item) => console.log('Item clicked:', item)}
                                onItemMove={(data: unknown) => console.log('Item moved:', data)}
                            />
                        )}

                        {viewMode === 'table' && (
                            <DataTable
                                data={filteredItems}
                                columns={[
                                    { id: 'title', label: 'Title', field: 'title', sortable: true },
                                    { id: 'status', label: 'Status', field: 'status', sortable: true },
                                    { id: 'priority', label: 'Priority', field: 'priority', sortable: true },
                                    {
                                        id: 'owners',
                                        label: 'Owners',
                                        field: 'owners',
                                        sortable: false,
                                        format: (value: unknown) => {
                                            if (!Array.isArray(value)) return '';
                                            const names = value
                                                .map((owner) => {
                                                    const name = (owner as { name?: unknown } | null | undefined)?.name;
                                                    return typeof name === 'string' ? name : '';
                                                })
                                                .filter((name) => name.length > 0);
                                            return names.join(', ');
                                        },
                                    },
                                    {
                                        id: 'tags',
                                        label: 'Tags',
                                        field: 'tags',
                                        sortable: false,
                                        format: (value: unknown) =>
                                            Array.isArray(value)
                                                ? value
                                                    .filter((tag): tag is string => typeof tag === 'string')
                                                    .join(', ')
                                                : '',
                                    },
                                ]}
                                onRowClick={(item: Item) => console.log('Row clicked:', item)}
                                showPagination
                                paginationConfig={{ page: 0, rowsPerPage: 10, totalRows: filteredItems.length }}
                            />
                        )}

                        {viewMode === 'timeline' && (
                            <Timeline
                                items={filteredItems}
                                viewMode="week"
                                height={300}
                                showToday
                            />
                        )}

                        {filteredItems.length === 0 && (
                            <div className="text-center py-12 text-gray-500 dark:text-gray-400">
                                No work items found
                            </div>
                        )}
                    </div>
                )}

                {/* Recent Activity */}
                {(() => {
                    const events = stageKey ? mockStageEvents[stageKey] || [] : [];
                    return events.length > 0 ? (
                        <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Recent Activity
                            </h2>
                            <div className="space-y-4">
                                {events.slice(0, 5).map((event) => (
                                    <div key={event.id} className="flex items-start gap-3">
                                        <div className={`w-2 h-2 rounded-full mt-2 ${event.status === 'success' ? 'bg-green-500' : event.status === 'failure' ? 'bg-red-500' : 'bg-yellow-500'}`} />
                                        <div className="flex-1">
                                            <p className="font-medium text-gray-900 dark:text-white">{event.title}</p>
                                            {event.description && (
                                                <p className="text-sm text-gray-600 dark:text-gray-400 mt-1">{event.description}</p>
                                            )}
                                            <p className="text-xs text-gray-500 dark:text-gray-500 mt-1">
                                                {event.actor} • {new Date(event.timestamp).toLocaleString()}
                                            </p>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        </div>
                    ) : null;
                })()}

                {/* Quick Actions */}
                <div className="bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-lg p-6">
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                        Quick Actions
                    </h2>
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-3">
                        {stageKey === 'build' && (
                            <Link
                                to="/build/workflows"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <GitBranch className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                                <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                    View Workflows
                                </span>
                            </Link>
                        )}
                        {stageKey === 'secure' && (
                            <Link
                                to="/admin/security"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <Shield className="h-5 w-5 text-red-600 dark:text-red-400" />
                                <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                    Security Center
                                </span>
                            </Link>
                        )}
                        {stageKey === 'deploy' && (
                            <Link
                                to="/operate/queue"
                                className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                            >
                                <Rocket className="h-5 w-5 text-green-600 dark:text-green-400" />
                                <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                    Deployment Queue
                                </span>
                            </Link>
                        )}
                        <Link
                            to="/observe/reports"
                            className="flex items-center gap-3 p-3 rounded-lg bg-slate-50 dark:bg-slate-800/70 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                        >
                            <FileText className="h-5 w-5 text-purple-600 dark:text-purple-400" />
                            <span className="text-sm font-medium text-slate-900 dark:text-neutral-100">
                                View Reports
                            </span>
                        </Link>
                    </div>
                </div>
            </div>
        </MainLayout>
    );
}
