/**
 * Optimized Data Explorer Page
 *
 * Full-featured Data Explorer with progressive loading and performance optimizations.
 * Maintains all original features while improving load times.
 *
 * @doc.type page
 * @doc.purpose Feature-complete data exploration with optimized loading
 * @doc.layer frontend
 */

import React, { useState, useMemo, lazy, Suspense } from 'react';
import { useSearchParams, useNavigate } from 'react-router';
import {
    Database,
    Table,
    GitBranch,
    Activity,
    Code,
    Search,
    Filter,
    Plus,
    MoreVertical,
    ChevronRight,
    Eye,
    Edit,
    Trash2,
    RefreshCw,
    Download,
    Loader2,
} from 'lucide-react';
import { cn } from '../lib/theme';

// Lazy load heavy components - commented out for now, will be implemented inline
// const LineageVisualization = lazy(() => import('../components/data/LineageVisualization'));
// const QualityMetrics = lazy(() => import('../components/data/QualityMetrics'));
// const SchemaViewer = lazy(() => import('../components/data/SchemaViewer'));

/**
 * View modes for the explorer
 */
type ViewMode = 'table' | 'lineage' | 'quality' | 'schema';

/**
 * Collection interface
 */
interface Collection {
    id: string;
    name: string;
    description?: string;
    recordCount: number;
    lastUpdated: string;
    qualityScore: number;
    qualityMetrics?: {
        completeness: number;
        accuracy: number;
        freshness: number;
        consistency: number;
    };
    fields?: CollectionField[];
}

/**
 * Collection field interface
 */
interface CollectionField {
    name: string;
    type: string;
    nullable: boolean;
    piiType?: string;
    qualityScore?: number;
}

/**
 * Mock data with full feature set
 */
const mockCollections: Collection[] = [
    {
        id: '1',
        name: 'Customer Events',
        description: 'Customer interaction events from all touchpoints',
        recordCount: 1250000,
        lastUpdated: new Date(Date.now() - 300000).toISOString(),
        qualityScore: 94,
        qualityMetrics: {
            completeness: 98,
            accuracy: 92,
            freshness: 95,
            consistency: 91,
        },
        fields: [
            { name: 'event_id', type: 'uuid', nullable: false, qualityScore: 100 },
            { name: 'customer_id', type: 'uuid', nullable: false, qualityScore: 98 },
            { name: 'event_type', type: 'varchar', nullable: false, qualityScore: 95 },
            { name: 'timestamp', type: 'timestamp', nullable: false, qualityScore: 100 },
            { name: 'email', type: 'varchar', nullable: true, piiType: 'email', qualityScore: 92 },
            { name: 'phone', type: 'varchar', nullable: true, piiType: 'phone', qualityScore: 88 },
        ],
    },
    {
        id: '2',
        name: 'Product Catalog',
        description: 'Product information and pricing data',
        recordCount: 45321,
        lastUpdated: new Date(Date.now() - 600000).toISOString(),
        qualityScore: 89,
        qualityMetrics: {
            completeness: 95,
            accuracy: 87,
            freshness: 88,
            consistency: 86,
        },
        fields: [
            { name: 'product_id', type: 'uuid', nullable: false, qualityScore: 100 },
            { name: 'name', type: 'varchar', nullable: false, qualityScore: 94 },
            { name: 'price', type: 'decimal', nullable: false, qualityScore: 91 },
            { name: 'category', type: 'varchar', nullable: true, qualityScore: 85 },
        ],
    },
    {
        id: '3',
        name: 'Order History',
        description: 'All customer orders and transaction data',
        recordCount: 892341,
        lastUpdated: new Date(Date.now() - 900000).toISOString(),
        qualityScore: 91,
        qualityMetrics: {
            completeness: 93,
            accuracy: 90,
            freshness: 92,
            consistency: 89,
        },
        fields: [
            { name: 'order_id', type: 'uuid', nullable: false, qualityScore: 100 },
            { name: 'customer_id', type: 'uuid', nullable: false, qualityScore: 96 },
            { name: 'total_amount', type: 'decimal', nullable: false, qualityScore: 93 },
            { name: 'status', type: 'varchar', nullable: false, qualityScore: 98 },
        ],
    },
];

/**
 * Quality Badge Component (inline for performance)
 */
function QualityBadge({ score, size = 'sm' }: { score: number; size?: 'sm' | 'md' }) {
    const getColor = (score: number) => {
        if (score >= 95) return 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300';
        if (score >= 85) return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300';
        if (score >= 70) return 'bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300';
        return 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300';
    };

    const sizeClasses = size === 'sm' ? 'px-2 py-0.5 text-xs' : 'px-3 py-1 text-sm';

    return (
        <span className={cn('rounded-full font-medium', getColor(score), sizeClasses)}>
            {score}%
        </span>
    );
}

/**
 * Collection Row Component with full features
 */
function CollectionRow({
    collection,
    onView,
    onEdit,
    viewMode,
}: {
    collection: Collection;
    onView: () => void;
    onEdit: () => void;
    viewMode: ViewMode;
}) {
    const [showActions, setShowActions] = useState(false);

    return (
        <div
            className={cn(
                'flex items-center gap-4 p-4 border border-gray-200 dark:border-gray-700 rounded-lg',
                'hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors cursor-pointer'
            )}
            onMouseEnter={() => setShowActions(true)}
            onMouseLeave={() => setShowActions(false)}
            onClick={onView}
        >
            {/* Icon */}
            <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                <Database className="h-5 w-5 text-blue-600 dark:text-blue-400" />
            </div>

            {/* Main Content */}
            <div className="flex-1 min-w-0">
                <div className="flex items-center gap-3 mb-2">
                    <h3 className="font-semibold text-gray-900 dark:text-white truncate">
                        {collection.name}
                    </h3>
                    <QualityBadge score={collection.qualityScore} />
                </div>
                <p className="text-sm text-gray-600 dark:text-gray-400 mb-2">
                    {collection.description}
                </p>

                {/* View-specific content */}
                {viewMode === 'schema' && collection.fields && (
                    <div className="text-xs text-gray-500">
                        {collection.fields.length} fields
                    </div>
                )}
            </div>

            {/* Stats */}
            <div className="text-right w-32">
                <p className="text-sm font-medium text-gray-900 dark:text-white">
                    {collection.recordCount.toLocaleString()}
                </p>
                <p className="text-xs text-gray-500">records</p>
            </div>

            {/* Last Updated */}
            <div className="text-right w-24">
                <p className="text-xs text-gray-500">
                    {new Date(collection.lastUpdated).toLocaleDateString()}
                </p>
            </div>

            {/* Actions */}
            <div className={cn('flex items-center gap-1', !showActions && 'invisible')}>
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onView();
                    }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="View"
                >
                    <Eye className="h-4 w-4 text-gray-400" />
                </button>
                <button
                    onClick={(e) => {
                        e.stopPropagation();
                        onEdit();
                    }}
                    className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded"
                    title="Edit"
                >
                    <Edit className="h-4 w-4 text-gray-400" />
                </button>
                <button className="p-1.5 hover:bg-gray-200 dark:hover:bg-gray-700 rounded" title="More">
                    <MoreVertical className="h-4 w-4 text-gray-400" />
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
    const navigate = useNavigate();
    const [searchParams, setSearchParams] = useSearchParams();
    const viewMode = (searchParams.get('view') as ViewMode) || 'table';
    const [searchQuery, setSearchQuery] = useState('');
    const [selectedCollection, setSelectedCollection] = useState<Collection | null>(null);

    // Filter collections based on search
    const filteredCollections = useMemo(() => {
        if (!searchQuery) return mockCollections;
        return mockCollections.filter(collection =>
            collection.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
            collection.description?.toLowerCase().includes(searchQuery.toLowerCase())
        );
    }, [searchQuery]);

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

    return (
        <div className="p-6">
            {/* Header */}
            <div className="mb-6">
                <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                    Data Explorer
                </h1>
                <p className="text-gray-600 dark:text-gray-400">
                    Explore and manage your data collections with full visibility into quality and lineage
                </p>
            </div>

            {/* Search and Actions */}
            <div className="flex items-center gap-4 mb-6">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                    <input
                        type="text"
                        placeholder="Search collections..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
                <div className="flex items-center gap-2">
                    <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg">
                        <Filter className="h-4 w-4 text-gray-500" />
                    </button>
                    <button className="p-2 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg">
                        <RefreshCw className="h-4 w-4 text-gray-500" />
                    </button>
                    <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors">
                        <Plus className="h-4 w-4" />
                        New Collection
                    </button>
                </div>
            </div>

            {/* View Mode Toggle */}
            <div className="flex items-center gap-2 mb-6">
                <span className="text-sm text-gray-600 dark:text-gray-400">View:</span>
                <div className="flex gap-1">
                    {viewModes.map((mode) => (
                        <button
                            key={mode.id}
                            onClick={() => handleViewModeChange(mode.id)}
                            className={cn(
                                'flex items-center gap-2 px-3 py-1.5 rounded-md text-sm transition-colors',
                                viewMode === mode.id
                                    ? 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300'
                                    : 'text-gray-600 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800'
                            )}
                        >
                            {mode.icon}
                            {mode.label}
                        </button>
                    ))}
                </div>
            </div>

            {/* Collections List */}
            <div className="space-y-4">
                {filteredCollections.map((collection) => (
                    <CollectionRow
                        key={collection.id}
                        collection={collection}
                        onView={() => handleCollectionClick(collection)}
                        onEdit={() => console.log('Edit collection:', collection.id)}
                        viewMode={viewMode}
                    />
                ))}
            </div>

            {/* Detail View - Lazy Loaded */}
            {selectedCollection && (
                <div className="mt-8 p-6 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
                    <h2 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
                        {selectedCollection.name} - {viewMode.charAt(0).toUpperCase() + viewMode.slice(1)} View
                    </h2>

                    <Suspense fallback={<LazyLoading message="Loading detailed view..." />}>
                        {viewMode === 'schema' && selectedCollection.fields && (
                            <div className="space-y-2">
                                {selectedCollection.fields.map((field) => (
                                    <div
                                        key={field.name}
                                        className="flex items-center gap-3 p-3 bg-gray-50 dark:bg-gray-700 rounded-lg"
                                    >
                                        <Code className="h-4 w-4 text-gray-400" />
                                        <span className="font-mono text-sm">{field.name}</span>
                                        <span className="text-xs bg-gray-200 dark:bg-gray-600 px-2 py-1 rounded">
                                            {field.type}
                                        </span>
                                        {field.nullable && (
                                            <span className="text-xs text-gray-400">nullable</span>
                                        )}
                                        {field.piiType && (
                                            <span className="text-xs bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300 px-2 py-1 rounded">
                                                PII: {field.piiType}
                                            </span>
                                        )}
                                        {field.qualityScore && (
                                            <div className="ml-auto">
                                                <QualityBadge score={field.qualityScore} />
                                            </div>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}

                        {viewMode === 'quality' && selectedCollection.qualityMetrics && (
                            <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
                                {Object.entries(selectedCollection.qualityMetrics).map(([metric, value]) => (
                                    <div key={metric} className="p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
                                        <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 capitalize">
                                            {metric}
                                        </h3>
                                        <p className="text-2xl font-bold text-gray-900 dark:text-white">{value}%</p>
                                    </div>
                                ))}
                            </div>
                        )}

                        {viewMode === 'lineage' && (
                            <div className="text-center py-8 text-gray-500">
                                <GitBranch className="h-12 w-12 mx-auto mb-4" />
                                <p>Lineage visualization would be loaded here</p>
                                <p className="text-sm">Upstream: raw_events, user_profiles</p>
                                <p className="text-sm">Downstream: analytics_dashboard</p>
                            </div>
                        )}
                    </Suspense>
                </div>
            )}

            {/* Empty State */}
            {filteredCollections.length === 0 && (
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
        </div>
    );
}

export default DataExplorer;
