/**
 * EventCloud Topology Visualization
 *
 * Provides real-time stream topology visualization for Data-Cloud's EventCloud.
 * Built on shared @ghatana/canvas/topology components.
 *
 * Features:
 * - Stream source/processor/sink visualization
 * - Real-time throughput and latency metrics
 * - Status-based node coloring
 * - Auto-layout with dagre algorithm
 * - Interactive node selection
 *
 * @doc.type component
 * @doc.purpose EventCloud stream topology visualization
 * @doc.layer product
 * @doc.pattern Visualization
 */

import React, { useCallback, useMemo, memo } from 'react';
import { ReactFlow, Background, Controls, MiniMap, Panel, type Node } from '@xyflow/react';
import '@xyflow/react/dist/style.css';

import {
    BaseTopologyNode,
    BaseTopologyEdge,
    useTopology,
    type EventCloudNode,
    type EventCloudEdge,
    type EventCloudNodeData,
    type EventCloudEdgeData,
    type TopologyVisualizationConfig,
    STATUS_COLORS,
} from '@ghatana/canvas/topology';

import { cn, cardStyles, textStyles, bgStyles } from '@/lib/theme';
import { dataCloudColors } from '@/lib/theme';

// ============================================
// NODE COMPONENTS
// ============================================

/**
 * Icon mapping for EventCloud node types.
 */
const NODE_ICONS: Record<string, React.ReactNode> = {
    source: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2 1 3 3 3h10c2 0 3-1 3-3V7c0-2-1-3-3-3H7c-2 0-3 1-3 3z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4" />
        </svg>
    ),
    processor: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
    ),
    sink: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
        </svg>
    ),
    router: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M8 7h12m0 0l-4-4m4 4l-4 4m0 6H4m0 0l4 4m-4-4l4-4" />
        </svg>
    ),
    storage: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 7v10c0 2.21 3.582 4 8 4s8-1.79 8-4V7M4 7c0 2.21 3.582 4 8 4s8-1.79 8-4M4 7c0-2.21 3.582-4 8-4s8 1.79 8 4m0 5c0 2.21-3.582 4-8 4s-8-1.79-8-4" />
        </svg>
    ),
    queue: (
        <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
        </svg>
    ),
};

/**
 * Get node color based on type.
 */
function getNodeTypeColor(nodeType: string): keyof typeof dataCloudColors {
    const colorMap: Record<string, keyof typeof dataCloudColors> = {
        source: 'source',
        processor: 'processor',
        sink: 'sink',
        router: 'router',
        storage: 'storage',
    };
    return colorMap[nodeType] ?? 'processor';
}

/**
 * EventCloud-specific node component.
 */
const EventCloudNodeComponent = memo(function EventCloudNodeComponent(props: { data: EventCloudNodeData; selected?: boolean }) {
    const { data, selected } = props;
    const colorKey = getNodeTypeColor(data.nodeType);
    const colors = dataCloudColors[colorKey as keyof typeof dataCloudColors];

    return (
        <BaseTopologyNode
            {...(props as any)}
            icon={
                <div className={cn('p-1 rounded', colors?.bg, colors?.text)}>
                    {NODE_ICONS[data.nodeType] ?? NODE_ICONS.processor}
                </div>
            }
            showMetrics={true}
            body={
                data.topics && data.topics.length > 0 ? (
                    <div className="mt-2 flex flex-wrap gap-1">
                        {data.topics.slice(0, 3).map((topic) => (
                            <span
                                key={topic}
                                className="px-1.5 py-0.5 text-xs bg-gray-100 dark:bg-gray-700 rounded truncate max-w-[80px]"
                                title={topic}
                            >
                                {topic}
                            </span>
                        ))}
                        {data.topics.length > 3 && (
                            <span className="px-1.5 py-0.5 text-xs bg-gray-100 dark:bg-gray-700 rounded">
                                +{data.topics.length - 3}
                            </span>
                        )}
                    </div>
                ) : null
            }
        />
    );
});

/**
 * EventCloud-specific edge component.
 */
const EventCloudEdgeComponent = memo(function EventCloudEdgeComponent(props: any) {
    return <BaseTopologyEdge {...props} pathType="smoothstep" showThroughput={true} />;
});

// ============================================
// NODE/EDGE TYPES
// ============================================

const nodeTypes = {
    eventcloud: EventCloudNodeComponent,
};

const edgeTypes = {
    eventcloud: EventCloudEdgeComponent,
};

// ============================================
// MAIN COMPONENT
// ============================================

export interface EventCloudTopologyProps {
    /** Stream nodes */
    nodes: EventCloudNode[];

    /** Stream edges */
    edges: EventCloudEdge[];

    /** Visualization configuration */
    config?: TopologyVisualizationConfig;

    /** Callback when node is selected */
    onNodeSelect?: (node: EventCloudNode | null) => void;

    /** Callback when topology changes */
    onChange?: (nodes: EventCloudNode[], edges: EventCloudEdge[]) => void;

    /** Loading state */
    isLoading?: boolean;

    /** Error state */
    error?: Error | null;

    /** Read-only mode */
    readOnly?: boolean;

    /** Container className */
    className?: string;

    /** Tenant ID for filtering */
    tenantId?: string;
}

/**
 * EventCloud Topology Visualization Component.
 *
 * Renders a real-time view of stream processing topology with:
 * - Source, processor, sink, router nodes
 * - Throughput metrics on edges
 * - Status-based coloring
 * - Interactive selection
 *
 * @example
 * ```tsx
 * <EventCloudTopology
 *   nodes={streamNodes}
 *   edges={streamEdges}
 *   onNodeSelect={(node) => setSelectedStream(node)}
 *   config={{ layout: 'dagre', layoutDirection: 'LR' }}
 * />
 * ```
 */
export function EventCloudTopology({
    nodes: initialNodes,
    edges: initialEdges,
    config = {},
    onNodeSelect,
    onChange,
    isLoading = false,
    error = null,
    readOnly = false,
    className = '',
    tenantId,
}: EventCloudTopologyProps): React.JSX.Element {
    // Set default config
    const defaultConfig: TopologyVisualizationConfig = {
        layout: 'dagre',
        layoutDirection: 'LR',
        enableAnimations: true,
        showInlineMetrics: true,
        enableZoomControls: true,
        nodeSpacing: 150,
        rankSpacing: 250,
        ...config,
    };

    // Convert nodes to use eventcloud type
    const typedNodes = useMemo(
        () =>
            initialNodes.map((node) => ({
                ...node,
                type: 'eventcloud',
            })),
        [initialNodes]
    );

    // Convert edges to use eventcloud type
    const typedEdges = useMemo(
        () =>
            initialEdges.map((edge) => ({
                ...edge,
                type: 'eventcloud',
            })),
        [initialEdges]
    );

    // Use topology hook for state management
    const {
        nodes,
        edges,
        onNodesChange,
        onEdgesChange,
        onConnect,
        selectedNode,
        selectNode,
        applyLayout,
    } = useTopology<EventCloudNodeData, EventCloudEdgeData>({
        initialNodes: typedNodes,
        initialEdges: typedEdges,
        config: defaultConfig,
        onNodeSelect: onNodeSelect as any,
        onChange: onChange as any,
        readOnly,
    });

    // Handle node click
    const handleNodeClick = useCallback(
        (_event: React.MouseEvent, node: Node) => {
            selectNode(node.id);
        },
        [selectNode]
    );

    // Handle pane click (deselect)
    const handlePaneClick = useCallback(() => {
        selectNode(null);
    }, [selectNode]);

    // Handle layout button
    const handleApplyLayout = useCallback(() => {
        applyLayout('dagre', { direction: defaultConfig.layoutDirection });
    }, [applyLayout, defaultConfig.layoutDirection]);

    // Loading state
    if (isLoading) {
        return (
            <div className={cn('flex items-center justify-center h-full', bgStyles.surface, className)}>
                <div className="flex flex-col items-center gap-4">
                    <div className="w-8 h-8 border-4 border-blue-500 border-t-transparent rounded-full animate-spin" />
                    <span className={textStyles.muted}>Loading topology...</span>
                </div>
            </div>
        );
    }

    // Error state
    if (error) {
        return (
            <div className={cn('flex items-center justify-center h-full', bgStyles.surface, className)}>
                <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-md text-center')}>
                    <div className="text-red-500 mb-2">
                        <svg className="w-12 h-12 mx-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                        </svg>
                    </div>
                    <h3 className={textStyles.h3}>Failed to load topology</h3>
                    <p className={cn(textStyles.muted, 'mt-2')}>{error.message}</p>
                </div>
            </div>
        );
    }

    // Empty state
    if (nodes.length === 0) {
        return (
            <div className={cn('flex items-center justify-center h-full', bgStyles.surface, className)}>
                <div className={cn(cardStyles.base, cardStyles.padded, 'max-w-md text-center')}>
                    <div className="text-gray-400 mb-2">
                        <svg className="w-12 h-12 mx-auto" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 17V7m0 10a2 2 0 01-2 2H5a2 2 0 01-2-2V7a2 2 0 012-2h2a2 2 0 012 2m0 10a2 2 0 002 2h2a2 2 0 002-2M9 7a2 2 0 012-2h2a2 2 0 012 2m0 10V7m0 10a2 2 0 002 2h2a2 2 0 002-2V7a2 2 0 00-2-2h-2a2 2 0 00-2 2" />
                        </svg>
                    </div>
                    <h3 className={textStyles.h3}>No streams configured</h3>
                    <p className={cn(textStyles.muted, 'mt-2')}>
                        Configure stream sources, processors, and sinks to visualize your data flow.
                    </p>
                </div>
            </div>
        );
    }

    return (
        <div className={cn('h-full w-full', className)}>
            <ReactFlow
                nodes={nodes}
                edges={edges}
                onNodesChange={onNodesChange as any}
                onEdgesChange={onEdgesChange as any}
                onConnect={readOnly ? undefined : onConnect}
                onNodeClick={handleNodeClick}
                onPaneClick={handlePaneClick}
                nodeTypes={nodeTypes}
                edgeTypes={edgeTypes}
                fitView
                fitViewOptions={{ padding: 0.2 }}
                proOptions={{ hideAttribution: true }}
                nodesDraggable={!readOnly}
                nodesConnectable={!readOnly}
                elementsSelectable={true}
                className="bg-gray-50 dark:bg-gray-900"
            >
                <Background color="#e5e7eb" gap={16} />
                <Controls showInteractive={!readOnly} />
                {defaultConfig.enableZoomControls && <MiniMap nodeStrokeWidth={3} pannable zoomable />}

                {/* Control Panel */}
                <Panel position="top-right" className="flex gap-2">
                    <button
                        onClick={handleApplyLayout}
                        className="px-3 py-1.5 text-sm bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                        aria-label="Auto-layout"
                    >
                        Auto Layout
                    </button>
                </Panel>

                {/* Legend */}
                <Panel position="bottom-left" className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-sm p-2">
                    <div className="flex items-center gap-3 text-xs">
                        {Object.entries(STATUS_COLORS).slice(0, 4).map(([status, colors]) => (
                            <div key={status} className="flex items-center gap-1">
                                <span className={cn('w-2 h-2 rounded-full', colors.dot)} />
                                <span className="capitalize text-gray-600 dark:text-gray-400">{status}</span>
                            </div>
                        ))}
                    </div>
                </Panel>
            </ReactFlow>
        </div>
    );
}

export default EventCloudTopology;
