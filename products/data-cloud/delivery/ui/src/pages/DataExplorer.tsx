/**
 * Optimized Data Explorer Page
 *
 * Full-featured Data Explorer with progressive loading and performance optimizations.
 * Maintains all original features while improving load times.
 *
 * DC-P2-007: Added sorting functionality for collections list
 *
 * @doc.type page
 * @doc.purpose Feature-complete data exploration with optimized loading and sorting
 * @doc.layer frontend
 */

import React, { useEffect, useMemo, useState, Suspense } from 'react';
import { useSearchParams, useNavigate, useParams } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import {
    Database,
    Table,
    GitBranch,
    Activity,
    Code,
    Search,
    Plus,
    MoreVertical,
    Eye,
    Edit,
    RefreshCw,
    Loader2,
    ArrowUpDown,
    ArrowUp,
    ArrowDown,
    Trash2,
    Archive,
    ChevronRight,
} from 'lucide-react';
import { cn } from '../lib/theme';
import { collectionsApi, type Collection } from '../lib/api/collections';
import { lineageService } from '../api/lineage.service';
import { aiOperationsService, type AiQualityAdvisory } from '../api/ai-operations.service';
import { LineageGraph } from '../components/lineage/LineageGraph';
import { AIAssistSuggestion } from '../components/common/AIAssistSuggestion';
import { DataSummary } from '../components/common/DataSummary';

/**
 * View modes for the explorer
 */
const DATA_EXPLORER_VIEW_MODES = ['table', 'lineage', 'quality', 'schema'] as const;
const QUALITY_VIEW_ENABLED = true;

type ViewMode = (typeof DATA_EXPLORER_VIEW_MODES)[number];

/**
 * Sort fields for collections
 */
type SortField = 'name' | 'entityCount' | 'updatedAt' | 'qualityScore' | 'createdAt';

/**
 * Sort direction
 */
type SortDirection = 'asc' | 'desc';

interface QualityAdvisory {
    suggestion: string;
    confidence: number;
    evidence: string[];
}

const advisoryUnavailable: QualityAdvisory = {
    suggestion: 'No backend quality advisory is currently available for this collection. Retry after the quality advisory pipeline finishes.',
    confidence: 0.5,
    evidence: ['No advisory payload returned by /api/v1/action/ai/quality-advisories.'],
};

function normalizeViewMode(view: string | null): ViewMode {
    if (view === 'quality' && !QUALITY_VIEW_ENABLED) {
        return 'table';
    }
    if (view && DATA_EXPLORER_VIEW_MODES.includes(view as ViewMode)) {
        return view as ViewMode;
    }
    return 'table';
}

function advisoryFromBackend(payload: AiQualityAdvisory): QualityAdvisory {
    const highestImpact = [...payload.advisories]
        .sort((a, b) => b.affectedCount - a.affectedCount)
        .slice(0, 3);
    const topConfidence = payload.advisories.reduce((max, item) => Math.max(max, item.confidence), 0);

    if (highestImpact.length === 0) {
        return {
            suggestion: `AI advisory for collection ${payload.collectionId} reports no active quality remediation items.`,
            confidence: Math.max(0.65, topConfidence || 0.65),
            evidence: [
                `overallScore=${payload.overallScore.toFixed(2)}`,
                `generatedAt=${payload.generatedAt}`,
            ],
        };
    }

    const primary = highestImpact[0];
    const evidence = highestImpact.map((item) =>
        `${item.type}: affected=${item.affectedCount}, confidence=${item.confidence.toFixed(2)}`
    );

    return {
        suggestion: primary.suggestedAction ?? primary.description,
        confidence: Math.max(primary.confidence, topConfidence),
        evidence,
    };
}

/**
 * Schema type badge
 */
function SchemaTypeBadge({ schemaType }: { schemaType: Collection['schemaType'] }) {
    const colors: Record<Collection['schemaType'], string> = {
        entity: 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300',
        event: 'bg-purple-100 text-purple-700 dark:bg-purple-900 dark:text-purple-300',
        timeseries: 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300',
        graph: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
        document: 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-300',
    };
    return (
        <span className={cn('px-2 py-0.5 rounded-full text-xs font-medium', colors[schemaType])}>
            {schemaType}
        </span>
    );
}

/**
 * Status badge
 */
function StatusBadge({ status }: { status: Collection['status'] }) {
    const colors: Record<Collection['status'], string> = {
        active: 'text-green-600',
        draft: 'text-gray-500',
        archived: 'text-amber-600',
        processing: 'text-blue-600',
    };
    const dot: Record<Collection['status'], string> = {
        active: 'bg-green-500',
        draft: 'bg-gray-400',
        archived: 'bg-amber-500',
        processing: 'bg-blue-500',
    };
    return (
        <span className={cn('inline-flex items-center gap-1 text-xs', colors[status])}>
            <span className={cn('h-1.5 w-1.5 rounded-full', dot[status])} />
            {status}
        </span>
    );
}

/**
 * Lifecycle status badge
 */
function LifecycleBadge({ status }: { status: Collection['lifecycleStatus'] }) {
    const styles: Record<Collection['lifecycleStatus'], string> = {
        DRAFT: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
        PUBLISHED: 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900 dark:text-emerald-300',
        DEPRECATED: 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300',
        ARCHIVED: 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-400',
        UNKNOWN: 'bg-gray-100 text-gray-400 dark:bg-gray-800 dark:text-gray-500',
    };
    return (
        <span className={cn('px-1.5 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wider', styles[status])}>
            {status}
        </span>
    );
}

/**
 * Operational status indicator
 */
function OperationalStatusDot({ status }: { status: Collection['operationalStatus'] }) {
    const colors: Record<string, string> = {
        healthy: 'bg-emerald-500',
        degraded: 'bg-amber-500',
        unavailable: 'bg-red-500',
        maintenance: 'bg-blue-500',
        unknown: 'bg-gray-400',
    };
    return (
        <span
            className={cn('inline-block h-2 w-2 rounded-full', colors[status] ?? colors.unknown)}
            title={`Operational: ${status}`}
        />
    );
}

/**
 * Collection Row Component
 * P6.2: Added deterministic action handlers for quality, lineage, schema, delete, and archive
 */
function CollectionRow({
    collection,
    onView,
    onEdit,
    onQuality,
    onLineage,
    onSchema,
    onDelete,
    onArchive,
    viewMode,
}: {
    collection: Collection;
    onView: () => void;
    onEdit: () => void;
    onQuality: () => void;
    onLineage: () => void;
    onSchema: () => void;
    onDelete: () => void;
    onArchive: () => void;
    viewMode: ViewMode;
}) {
    return (
        <div
            data-testid="collection-item"
            className={cn(
                'flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg',
                'hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors cursor-pointer'
            )}
            onClick={onView}
        >
            {/* Icon */}
            <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                <Database className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>

            {/* Main Content */}
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1 flex-wrap">
                    <h3 className="font-semibold text-gray-900 dark:text-white truncate">
                        {collection.name}
                    </h3>
                    <SchemaTypeBadge schemaType={collection.schemaType} />
                    <LifecycleBadge status={collection.lifecycleStatus} />
                    <OperationalStatusDot status={collection.operationalStatus} />
                    <StatusBadge status={collection.status} />
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-400">
                    {collection.description}
                </p>
                <div className="flex items-center gap-3 mt-1 text-[11px] text-gray-500 dark:text-gray-400">
                    <span>Owner: {collection.owner}</span>
                    {collection.qualityScore != null && (
                        <span className="flex items-center gap-1">
                            Quality: <strong className={collection.qualityScore >= 0.8 ? 'text-emerald-600' : collection.qualityScore >= 0.5 ? 'text-amber-600' : 'text-red-600'}>{collection.qualityScore.toFixed(2)}</strong>
                        </span>
                    )}
                    {viewMode === 'schema' && collection.schema?.fields && (
                        <span>{collection.schema.fields.length} fields</span>
                    )}
                </div>
            </div>

            {/* Stats */}
            <div className="text-right w-32">
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                    {collection.entityCount.toLocaleString()}
                </p>
                <p className="text-xs text-gray-500">records</p>
            </div>

            {/* Last Updated */}
            <div className="text-right w-24">
                <p className="text-xs text-gray-500">
                    {new Date(collection.updatedAt).toLocaleDateString()}
                </p>
            </div>

            {/* Actions */}
            <div className="flex items-center gap-1" aria-label={`Actions for ${collection.name}`}>
                <button
                    onClick={(e) => { e.stopPropagation(); onView(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="View"
                    aria-label={`View ${collection.name}`}
                >
                    <Eye className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onEdit(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Edit"
                    aria-label={`Edit ${collection.name}`}
                >
                    <Edit className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onQuality(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Quality"
                    aria-label={`View quality for ${collection.name}`}
                >
                    <Activity className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onLineage(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Lineage"
                    aria-label={`View lineage for ${collection.name}`}
                >
                    <GitBranch className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onSchema(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Schema"
                    aria-label={`View schema for ${collection.name}`}
                >
                    <Code className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onArchive(); }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Archive"
                    aria-label={`Archive ${collection.name}`}
                >
                    <Archive className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => { e.stopPropagation(); onDelete(); }}
                    className="p-1.5 hover:bg-red-100 dark:hover:bg-red-900 rounded"
                    title="Delete"
                    aria-label={`Delete ${collection.name}`}
                >
                    <Trash2 className="h-4 w-4 text-red-400" />
                </button>
            </div>
        </div>
    );
}

/**
 * Loading Component for lazy loaded sections
 */
function LazyLoading({ message = 'Loading...' }: { message?: string }) {
    return (
        <div className="flex items-center justify-center p-8">
            <div className="flex items-center gap-2 text-gray-500">
                <Loader2 className="h-4 w-4 animate-spin" />
                <span className="text-sm">{message}</span>
            </div>
        </div>
    );
}

/**
 * Optimized Data Explorer Component
 */
export function DataExplorer() {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const params = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    const rawViewMode = searchParams.get('view');
    const viewMode = normalizeViewMode(rawViewMode);
    const [searchQuery, setSearchQuery] = useState('');
    // DC-UX-012: Filter state wired to API — was accepted by API params but ignored by the call
    const [statusFilter, setStatusFilter] = useState<Collection['status'] | 'all'>('all');
    const [schemaTypeFilter, setSchemaTypeFilter] = useState<Collection['schemaType'] | 'all'>('all');
    // DC-P2-007: Sort state
    const [sortField, setSortField] = useState<SortField>('updatedAt');
    const [sortDirection, setSortDirection] = useState<SortDirection>('desc');
    const [selectedCollection, setSelectedCollection] = useState<Collection | null>(null);

    useEffect(() => {
        if (rawViewMode && rawViewMode !== viewMode) {
            const normalizedParams = new URLSearchParams(searchParams);
            normalizedParams.set('view', viewMode);
            setSearchParams(normalizedParams, { replace: true });
        }
    }, [rawViewMode, searchParams, setSearchParams, viewMode]);

    // DC-UX-012: Fetch real collections — all filter/sort params wired to queryKey and API call
    const { data: collectionsPage, isLoading, isError, refetch } = useQuery({
        queryKey: ['collections', searchQuery, statusFilter, schemaTypeFilter, sortField, sortDirection],
        queryFn: () => collectionsApi.list({
            search: searchQuery || undefined,
            pageSize: 50,
            status: statusFilter !== 'all' ? statusFilter : undefined,
            schemaType: schemaTypeFilter !== 'all' ? schemaTypeFilter : undefined,
            sortBy: sortField,
            sortOrder: sortDirection,
        }),
        staleTime: 60_000,
    });

    const collections = collectionsPage?.items ?? [];

    // P6.1: Load selected collection from route param for deep-link support
    const collectionIdFromRoute = params.id;
    useEffect(() => {
        if (collectionIdFromRoute && collections.length > 0) {
            const collection = collections.find(c => c.id === collectionIdFromRoute);
            if (collection && (!selectedCollection || selectedCollection.id !== collectionIdFromRoute)) {
                setSelectedCollection(collection);
            }
        }
    }, [collectionIdFromRoute, collections, selectedCollection]);

    const advisoryCollectionId = selectedCollection?.id ?? collections[0]?.id;
    const { data: backendQualityAdvisory } = useQuery({
        queryKey: ['quality-advisory', advisoryCollectionId],
        queryFn: () => aiOperationsService.getQualityAdvisories(advisoryCollectionId as string),
        enabled: Boolean(advisoryCollectionId),
        staleTime: 60_000,
        retry: false,
    });
    const qualityAdvisory = useMemo(() => {
        if (backendQualityAdvisory) {
            return advisoryFromBackend(backendQualityAdvisory);
        }
        return advisoryUnavailable;
    }, [backendQualityAdvisory]);

    const { data: lineageGraph, isLoading: lineageLoading } = useQuery({
        queryKey: ['lineage', selectedCollection?.id, viewMode],
        queryFn: () => lineageService.getLineage(selectedCollection!.id),
        enabled: viewMode === 'lineage' && selectedCollection !== null,
        staleTime: 60_000,
    });

    const { data: impactAnalysis } = useQuery({
        queryKey: ['lineage-impact', selectedCollection?.id, viewMode],
        queryFn: () => lineageService.getImpactAnalysis(selectedCollection!.id),
        enabled: viewMode === 'lineage' && selectedCollection !== null,
        staleTime: 60_000,
    });

    const viewModes: { id: ViewMode; label: string; icon: React.ReactNode }[] = [
        { id: 'table', label: 'Table', icon: <Table className="h-4 w-4" /> },
        { id: 'lineage', label: 'Lineage', icon: <GitBranch className="h-4 w-4" /> },
        { id: 'quality', label: 'Quality', icon: <Activity className="h-4 w-4" /> },
        { id: 'schema', label: 'Schema', icon: <Code className="h-4 w-4" /> },
    ];

    const handleViewModeChange = (mode: ViewMode) => {
        setSearchParams({ view: mode });
    };

    const handleCollectionClick = (collection: Collection) => {
        setSelectedCollection(collection);
        navigate(`/data/${collection.id}?view=${viewMode}`);
    };

    // DC-P2-007: Handle sort field change
    const handleSortFieldChange = (field: SortField) => {
        if (sortField === field) {
            // Toggle direction if same field
            setSortDirection(sortDirection === 'asc' ? 'desc' : 'asc');
        } else {
            // New field, default to desc
            setSortField(field);
            setSortDirection('desc');
        }
    };

    // DC-P2-007: Get sort icon based on current field and direction
    const getSortIcon = (field: SortField) => {
        if (sortField !== field) return <ArrowUpDown className="h-4 w-4" />;
        return sortDirection === 'asc' ? <ArrowUp className="h-4 w-4" /> : <ArrowDown className="h-4 w-4" />;
    };

    return (
        <section className="p-6" data-testid="data-explorer-page" aria-label="Data Explorer">
            {/* Header */}
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                    Data Explorer
                </h1>
                <p className="text-gray-600 dark:text-gray-400">
                    Explore and manage your data collections with quality insights and lineage context
                </p>
            </div>

            <div className="mb-6" data-testid="data-explorer-quality-advisory">
                <AIAssistSuggestion
                    headingLabel="Quality advisory"
                    suggestion={qualityAdvisory.suggestion}
                    confidence={qualityAdvisory.confidence}
                    evidence={qualityAdvisory.evidence}
                    canApply={false}
                />
            </div>

            {/* Search and Actions — DC-UX-012: filter controls wired to API */}
            <div className="flex flex-wrap items-center gap-3 mb-6">
                <div className="flex-1 min-w-48 relative">
                    <label htmlFor="collection-search" className="sr-only">Search collections</label>
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" aria-hidden="true" />
                    <input
                        id="collection-search"
                        data-testid="collection-search-input"
                        type="text"
                        placeholder="Search collections..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
                <label htmlFor="collection-status-filter" className="sr-only">Filter by status</label>
                <select
                    id="collection-status-filter"
                    data-testid="collection-status-filter"
                    value={statusFilter}
                    onChange={(e) => setStatusFilter(e.target.value as Collection['status'] | 'all')}
                    className="py-2 pl-3 pr-8 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm text-gray-900 dark:text-white"
                >
                    <option value="all">All statuses</option>
                    <option value="active">Active</option>
                    <option value="draft">Draft</option>
                    <option value="archived">Archived</option>
                    <option value="processing">Processing</option>
                </select>
                <label htmlFor="collection-schema-filter" className="sr-only">Filter by schema type</label>
                <select
                    id="collection-schema-filter"
                    data-testid="collection-schema-filter"
                    value={schemaTypeFilter}
                    onChange={(e) => setSchemaTypeFilter(e.target.value as Collection['schemaType'] | 'all')}
                    className="py-2 pl-3 pr-8 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-sm text-gray-900 dark:text-white"
                >
                    <option value="all">All types</option>
                    <option value="entity">Entity</option>
                    <option value="event">Event</option>
                    <option value="timeseries">Timeseries</option>
                    <option value="graph">Graph</option>
                    <option value="document">Document</option>
                </select>
                {/* DC-P2-007: Sort controls */}
                <label htmlFor="collection-sort-field" className="sr-only">Sort by</label>
                <div className="flex items-center gap-1 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800">
                    <button
                        onClick={() => handleSortFieldChange('name')}
                        data-testid="sort-by-name"
                        className={cn(
                            'flex items-center gap-1 px-3 py-2 text-sm transition-colors rounded-l-lg',
                            sortField === 'name' ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
                        )}
                    >
                        {getSortIcon('name')}
                        Name
                    </button>
                    <button
                        onClick={() => handleSortFieldChange('entityCount')}
                        data-testid="sort-by-entityCount"
                        className={cn(
                            'flex items-center gap-1 px-3 py-2 text-sm transition-colors border-l border-gray-300 dark:border-gray-600',
                            sortField === 'entityCount' ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
                        )}
                    >
                        {getSortIcon('entityCount')}
                        Records
                    </button>
                    <button
                        onClick={() => handleSortFieldChange('qualityScore')}
                        data-testid="sort-by-qualityScore"
                        className={cn(
                            'flex items-center gap-1 px-3 py-2 text-sm transition-colors border-l border-gray-300 dark:border-gray-600',
                            sortField === 'qualityScore' ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
                        )}
                    >
                        {getSortIcon('qualityScore')}
                        Quality
                    </button>
                    <button
                        onClick={() => handleSortFieldChange('updatedAt')}
                        data-testid="sort-by-updatedAt"
                        className={cn(
                            'flex items-center gap-1 px-3 py-2 text-sm transition-colors border-l border-gray-300 dark:border-gray-600 rounded-r-lg',
                            sortField === 'updatedAt' ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'text-gray-600 hover:bg-gray-50 dark:text-gray-400 dark:hover:bg-gray-800'
                        )}
                    >
                        {getSortIcon('updatedAt')}
                        Updated
                    </button>
                </div>
                <div className="flex items-center gap-2 ml-auto">
                    <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg" onClick={() => void refetch()} aria-label="Refresh">
                        <RefreshCw className={cn('h-4 w-4 text-gray-500', isLoading && 'animate-spin')} />
                    </button>
                    <button
                        onClick={() => navigate('/data/new')}
                        data-testid="create-collection-button"
                        className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        New Collection
                    </button>
                </div>
            </div>

            {/* View Mode Toggle */}
            <div className="flex items-center gap-2 mb-6" data-testid="data-explorer-view-toggle">
                <span className="text-sm text-gray-600 dark:text-gray-400">View:</span>
                <div className="flex gap-1">
                    {viewModes.map((mode) => (
                        <button
                            key={mode.id}
                            onClick={() => {
                                if (mode.id === 'quality' && !QUALITY_VIEW_ENABLED) {
                                    return;
                                }
                                handleViewModeChange(mode.id);
                            }}
                            disabled={mode.id === 'quality' && !QUALITY_VIEW_ENABLED}
                            data-testid={`collection-view-${mode.id}`}
                            className={cn(
                                'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm transition-colors',
                                viewMode === mode.id
                                    ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
                                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800',
                                mode.id === 'quality' && !QUALITY_VIEW_ENABLED && 'cursor-not-allowed opacity-50 hover:bg-transparent dark:hover:bg-transparent'
                            )}
                            title={mode.id === 'quality' && !QUALITY_VIEW_ENABLED ? 'Quality mode is temporarily unavailable in this deployment' : undefined}
                        >
                            {mode.icon}
                            {mode.label}
                        </button>
                    ))}
                </div>
                {collectionsPage && (
                    <span className="ml-auto text-xs text-gray-500">
                        {collectionsPage.total} collection{collectionsPage.total !== 1 ? 's' : ''}
                    </span>
                )}
            </div>

            {/* Loading */}
            {isLoading && (
                <div className="flex items-center justify-center py-12">
                    <Loader2 className="h-6 w-6 animate-spin text-blue-500" />
                </div>
            )}

            {/* Collections List */}
                {/* DC-UX-009: Surface query errors rather than silently showing an empty list */}
                {!isLoading && isError && (
                    <div className="rounded-lg border border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/30 p-4 text-sm text-red-700 dark:text-red-400">
                        <p className="font-medium">Failed to load collections</p>
                        <p className="mt-1 text-xs">Check your connection and try again.</p>
                        <button
                            onClick={() => void refetch()}
                            className="mt-2 text-xs underline hover:no-underline"
                        >
                            Retry
                        </button>
                    </div>
                )}
                {!isLoading && !isError && (
                <div className="space-y-4">
                    {collections.map((collection) => (
                        <CollectionRow
                            key={collection.id}
                            collection={collection}
                            onView={() => handleCollectionClick(collection)}
                            onEdit={() => navigate(`/data/${collection.id}/edit`)}
                            onQuality={() => navigate(`/data/${collection.id}?view=quality`)}
                            onLineage={() => navigate(`/data/${collection.id}?view=lineage`)}
                            onSchema={() => navigate(`/data/${collection.id}?view=schema`)}
                            onDelete={async () => {
                                if (confirm(`Are you sure you want to delete "${collection.name}"? This action cannot be undone.`)) {
                                    try {
                                        await collectionsApi.delete(collection.id);
                                        toast.success(`Collection "${collection.name}" deleted successfully`);
                                        await refetch();
                                        if (selectedCollection?.id === collection.id) {
                                            setSelectedCollection(null);
                                            navigate('/data');
                                        }
                                    } catch (error) {
                                        toast.error(`Failed to delete collection: ${error instanceof Error ? error.message : 'Unknown error'}`);
                                    }
                                }
                            }}
                            onArchive={async () => {
                                try {
                                    await collectionsApi.archive(collection.id);
                                    toast.success(`Collection "${collection.name}" archived successfully`);
                                    await refetch();
                                } catch (error) {
                                    toast.error(`Failed to archive collection: ${error instanceof Error ? error.message : 'Unknown error'}`);
                                }
                            }}
                            viewMode={viewMode}
                        />
                    ))}
                </div>
            )}

            {/* Detail View */}
            {selectedCollection && (
                <div className="mt-8 p-6 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg" data-testid="collection-detail-panel">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                        {selectedCollection.name} — {viewMode.charAt(0).toUpperCase() + viewMode.slice(1)} View
                    </h2>

                    <Suspense fallback={<LazyLoading message="Loading detailed view..." />}>
                        {viewMode === 'schema' && selectedCollection.schema?.fields && (
                            <div className="space-y-2">
                                {selectedCollection.schema.fields.map((field) => (
                                    <div
                                        key={field.name}
                                        className="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg"
                                    >
                                        <Code className="h-4 w-4 text-gray-400" />
                                        <span className="font-mono text-sm">{field.name}</span>
                                        <span className="text-xs bg-gray-200 dark:bg-gray-600 px-2 py-1 rounded">
                                            {field.type}
                                        </span>
                                        {!field.required && (
                                            <span className="text-xs text-gray-400">optional</span>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}

                        {viewMode === 'quality' && (
                            <div className="space-y-6">
                                {/* Registry-level quality summary */}
                                <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
                                    <div className="p-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30">
                                        <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Quality Score</p>
                                        <p className="text-2xl font-bold mt-1 text-gray-900 dark:text-white">
                                            {selectedCollection.qualityScore != null ? selectedCollection.qualityScore.toFixed(2) : 'N/A'}
                                        </p>
                                    </div>
                                    <div className="p-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30">
                                        <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Lifecycle</p>
                                        <p className="text-lg font-semibold mt-1 text-gray-900 dark:text-white">{selectedCollection.lifecycleStatus}</p>
                                    </div>
                                    <div className="p-4 rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30">
                                        <p className="text-xs text-gray-500 dark:text-gray-400 uppercase tracking-wide">Operational</p>
                                        <p className="text-lg font-semibold mt-1 text-gray-900 dark:text-white capitalize">{selectedCollection.operationalStatus}</p>
                                    </div>
                                </div>

                                {/* Quality metrics breakdown */}
                                {selectedCollection.qualityMetrics && Object.keys(selectedCollection.qualityMetrics).length > 0 && (
                                    <div>
                                        <h3 className="text-sm font-semibold text-gray-800 dark:text-gray-200 mb-3">Quality Metrics</h3>
                                        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                                            {Object.entries(selectedCollection.qualityMetrics).map(([key, value]) => (
                                                <div key={key} className="p-3 rounded-lg border border-gray-200 dark:border-gray-700">
                                                    <p className="text-xs text-gray-500 dark:text-gray-400 capitalize">{key}</p>
                                                    <div className="flex items-center gap-2 mt-1">
                                                        <div className="flex-1 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                                                            <div
                                                                className={cn(
                                                                    'h-full rounded-full',
                                                                    value >= 0.8 ? 'bg-emerald-500' : value >= 0.5 ? 'bg-amber-500' : 'bg-red-500'
                                                                )}
                                                                style={{ width: `${Math.max(0, Math.min(1, value)) * 100}%` }}
                                                            />
                                                        </div>
                                                        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">{value.toFixed(2)}</span>
                                                    </div>
                                                </div>
                                            ))}
                                        </div>
                                    </div>
                                )}

                                {/* Retention Policy */}
                                {selectedCollection.retentionPolicy && (
                                    <DataSummary
                                        title="Retention Policy"
                                        description="Data retention and archival configuration"
                                        data={selectedCollection.retentionPolicy as Record<string, unknown>}
                                        highlightFields={['retentionPeriod', 'archiveAfter', 'deleteAfter']}
                                    />
                                )}

                                {/* Lineage */}
                                {selectedCollection.lineage && (
                                    <DataSummary
                                        title="Lineage"
                                        description="Data lineage and provenance information"
                                        data={selectedCollection.lineage as Record<string, unknown>}
                                        highlightFields={['upstream', 'downstream', 'source']}
                                    />
                                )}

                                {!selectedCollection.qualityScore && !selectedCollection.qualityMetrics && (
                                    <p className="text-sm text-gray-500">
                                        No quality metrics computed for this collection yet. Run schema inference or the quality pipeline to populate metrics.
                                    </p>
                                )}
                            </div>
                        )}

                        {viewMode === 'lineage' && (
                            <div className="space-y-4" data-testid="data-explorer-lineage-panel">
                                <div className="flex items-center justify-between gap-4">
                                    <div>
                                        <p className="font-medium text-gray-700 dark:text-gray-300" data-testid="lineage-preview-title">Lineage preview</p>
                                        <p className="text-sm mt-2 text-gray-500">
                                            Live upstream and downstream lineage from the canonical launcher route.
                                        </p>
                                    </div>
                                    {impactAnalysis && (
                                        <div className="text-right text-sm text-gray-600 dark:text-gray-300">
                                            <p>
                                                Impact level: <strong>{impactAnalysis.affectedDatasets > 1 ? 'HIGH' : impactAnalysis.affectedDatasets === 1 ? 'MEDIUM' : 'LOW'}</strong>
                                            </p>
                                            <p>
                                                Affected datasets: <strong>{impactAnalysis.affectedDatasets}</strong>
                                            </p>
                                        </div>
                                    )}
                                </div>

                                {lineageLoading && <LazyLoading message="Loading lineage graph..." />}

                                {!lineageLoading && lineageGraph && (
                                    <>
                                        <LineageGraph
                                            nodes={lineageGraph.nodes}
                                            edges={lineageGraph.edges}
                                            rootNode={lineageGraph.rootNode}
                                            height="420px"
                                        />

                                        {impactAnalysis && impactAnalysis.details.length > 0 && (
                                            <div className="rounded-lg border border-gray-200 dark:border-gray-700 p-4 bg-gray-50 dark:bg-gray-900/30" data-testid="lineage-impact-panel">
                                                <p className="text-sm font-medium text-gray-800 dark:text-gray-200 mb-2">Downstream impact</p>
                                                <div className="space-y-1 text-sm text-gray-600 dark:text-gray-300">
                                                    {impactAnalysis.details.map((detail) => (
                                                        <p key={detail.id}>
                                                            {detail.name} • {detail.impact} impact
                                                        </p>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </>
                                )}
                            </div>
                        )}
                    </Suspense>
                </div>
            )}

            {/* Empty State */}
            {!isLoading && !isError && collections.length === 0 && (
                <div className="text-center py-12">
                    <Database className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                    <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                        No collections found
                    </h3>
                    <p className="text-gray-600 dark:text-gray-400">
                        {searchQuery ? 'Try adjusting your search terms' : 'Create your first collection to get started'}
                    </p>
                </div>
            )}
        </section>
    );
}

export default DataExplorer;
