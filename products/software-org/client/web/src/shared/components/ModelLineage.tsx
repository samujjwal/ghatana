import { memo } from 'react';

/**
 * Model lineage visualization showing data flow and feature dependencies.
 *
 * <p><b>Purpose</b><br>
 * Displays end-to-end lineage from raw data sources to model predictions.
 * Helps trace data quality issues and understand feature engineering steps.
 *
 * <p><b>Features</b><br>
 * - Data source nodes (databases, APIs, event streams)
 * - Feature engineering pipeline
 * - Model training steps
 * - Output predictions
 * - Data quality metrics at each stage
 *
 * <p><b>Interactions</b><br>
 * - Hover over nodes for details
 * - Click to drill into specific stage
 * - View quality metrics
 * - Track data volume through pipeline
 *
 * @doc.type component
 * @doc.purpose Model data lineage visualization
 * @doc.layer product
 * @doc.pattern Organism
 */

interface LineageNode {
    id: string;
    name: string;
    type: 'source' | 'feature' | 'model' | 'output';
    status: 'healthy' | 'warning' | 'error';
    metrics?: {
        recordCount?: number;
        qualityScore?: number;
        latency?: number;
    };
}

interface LineageEdge {
    from: string;
    to: string;
    label?: string;
}

interface ModelLineageProps {
    nodes: LineageNode[];
    edges: LineageEdge[];
    modelName?: string;
    modelVersion?: string;
}

export const ModelLineage = memo(function ModelLineage({
    nodes,
    edges,
    modelName = 'ML Model',
    modelVersion = '1.0',
}: ModelLineageProps) {
    const getNodeIcon = (type: LineageNode['type']) => {
        switch (type) {
            case 'source':
                return '💾';
            case 'feature':
                return '⚙️';
            case 'model':
                return '🤖';
            case 'output':
                return '📊';
            default:
                return '•';
        }
    };

    const getStatusColor = (status: LineageNode['status']) => {
        switch (status) {
            case 'healthy':
                return 'border-green-500 bg-green-50 dark:bg-green-600/30';
            case 'warning':
                return 'border-yellow-500 bg-yellow-50 dark:bg-orange-600/30';
            case 'error':
                return 'border-red-500 bg-red-50 dark:bg-rose-600/30';
            default:
                return 'border-slate-300 bg-slate-50 dark:bg-neutral-800';
        }
    };

    const getStatusTextColor = (status: LineageNode['status']) => {
        switch (status) {
            case 'healthy':
                return 'text-green-700 dark:text-green-400';
            case 'warning':
                return 'text-yellow-700 dark:text-yellow-400';
            case 'error':
                return 'text-red-700 dark:text-rose-400';
            default:
                return 'text-slate-700 dark:text-neutral-300';
        }
    };

    // Calculate positions for nodes (simple vertical layout)
    const nodePositions = nodes.reduce(
        (acc, node, i) => {
            const y = (i / (nodes.length - 1 || 1)) * 100;
            acc[node.id] = { x: 20 + (node.type === 'feature' ? 30 : node.type === 'model' ? 60 : 0), y };
            return acc;
        },
        {} as Record<string, { x: number; y: number }>
    );

    return (
        <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <div className="mb-6 space-y-2">
                <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                    📡 Model Lineage & Data Flow
                </h3>
                <p className="text-xs text-slate-600 dark:text-neutral-400">
                    {modelName} (v{modelVersion})
                </p>
            </div>

            {/* Lineage Diagram */}
            <div className="relative overflow-x-auto">
                <svg
                    className="min-h-96 w-full"
                    viewBox="0 0 100 100"
                    preserveAspectRatio="xMidYMid meet"
                >
                    {/* Connection Lines */}
                    <defs>
                        <marker
                            id="arrowhead"
                            markerWidth="10"
                            markerHeight="10"
                            refX="9"
                            refY="3"
                            orient="auto"
                        >
                            <polygon
                                points="0 0, 10 3, 0 6"
                                className="fill-slate-400 dark:fill-slate-600"
                            />
                        </marker>
                    </defs>

                    {edges.map((edge, i) => {
                        const fromPos = nodePositions[edge.from];
                        const toPos = nodePositions[edge.to];
                        if (!fromPos || !toPos) return null;

                        return (
                            <g key={i}>
                                <line
                                    x1={fromPos.x + 8}
                                    y1={fromPos.y}
                                    x2={toPos.x - 8}
                                    y2={toPos.y}
                                    className="stroke-slate-300 dark:stroke-slate-600"
                                    strokeWidth="0.5"
                                    markerEnd="url(#arrowhead)"
                                />
                                {edge.label && (
                                    <text
                                        x={(fromPos.x + toPos.x) / 2}
                                        y={(fromPos.y + toPos.y) / 2 - 1}
                                        className="fill-slate-500 dark:fill-slate-400 text-[2px]"
                                        textAnchor="middle"
                                    >
                                        {edge.label}
                                    </text>
                                )}
                            </g>
                        );
                    })}

                    {/* Nodes */}
                    {nodes.map((node) => {
                        const pos = nodePositions[node.id];
                        if (!pos) return null;

                        return (
                            <g key={node.id}>
                                {/* Node Circle */}
                                <circle
                                    cx={pos.x}
                                    cy={pos.y}
                                    r="3"
                                    className={`${getStatusColor(node.status)} fill-current`}
                                />
                                {/* Node Label */}
                                <text
                                    x={pos.x + 4}
                                    y={pos.y}
                                    className={`text-[2px] ${getStatusTextColor(node.status)}`}
                                    dominantBaseline="middle"
                                >
                                    {node.name}
                                </text>
                            </g>
                        );
                    })}
                </svg>
            </div>

            {/* Node Details Table */}
            <div className="mt-6 overflow-x-auto">
                <table className="w-full text-xs">
                    <thead>
                        <tr className="border-b border-slate-200 dark:border-neutral-600">
                            <th className="px-3 py-2 text-left font-medium text-slate-700 dark:text-neutral-300">
                                Stage
                            </th>
                            <th className="px-3 py-2 text-left font-medium text-slate-700 dark:text-neutral-300">
                                Name
                            </th>
                            <th className="px-3 py-2 text-left font-medium text-slate-700 dark:text-neutral-300">
                                Status
                            </th>
                            <th className="px-3 py-2 text-left font-medium text-slate-700 dark:text-neutral-300">
                                Metrics
                            </th>
                        </tr>
                    </thead>
                    <tbody>
                        {nodes.map((node) => (
                            <tr key={node.id} className="border-b border-slate-100 dark:border-slate-800">
                                <td className="px-3 py-2 text-slate-700 dark:text-neutral-300">
                                    {getNodeIcon(node.type)}
                                </td>
                                <td className="px-3 py-2 font-medium text-slate-900 dark:text-neutral-100">
                                    {node.name}
                                </td>
                                <td className="px-3 py-2">
                                    <span
                                        className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${getStatusTextColor(
                                            node.status
                                        )} ${getStatusColor(node.status)}`}
                                    >
                                        {node.status}
                                    </span>
                                </td>
                                <td className="px-3 py-2 text-slate-600 dark:text-neutral-400">
                                    {node.metrics && (
                                        <div className="space-y-0.5">
                                            {node.metrics.recordCount && (
                                                <div>Records: {node.metrics.recordCount.toLocaleString()}</div>
                                            )}
                                            {node.metrics.qualityScore && (
                                                <div>Quality: {Math.round(node.metrics.qualityScore * 100)}%</div>
                                            )}
                                            {node.metrics.latency && (
                                                <div>Latency: {node.metrics.latency}ms</div>
                                            )}
                                        </div>
                                    )}
                                </td>
                            </tr>
                        ))}
                    </tbody>
                </table>
            </div>

            {/* Legend */}
            <div className="mt-4 flex flex-wrap gap-4 border-t border-slate-200 pt-4 dark:border-neutral-600">
                {(['source', 'feature', 'model', 'output'] as const).map((type) => (
                    <div key={type} className="flex items-center gap-2 text-xs">
                        <span className="text-base">{getNodeIcon(type)}</span>
                        <span className="text-slate-600 dark:text-neutral-400 capitalize">{type}</span>
                    </div>
                ))}
            </div>
        </div>
    );
});
