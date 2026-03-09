import { useState, useMemo, useCallback } from 'react';
import type {
    OrgGraphData,
    OrgGraphNode,
    OrgGraphEdge,
    OrgGraphNodeType,
    DepartmentConfig,
    ServiceConfig,
    IntegrationConfig,
} from '@/shared/types/org';

/**
 * OrgGraphCanvas Props
 */
export interface OrgGraphCanvasProps {
    /** Graph data with nodes and edges */
    data: OrgGraphData;
    /** Currently selected node ID */
    selectedNodeId?: string | null;
    /** Callback when a node is clicked */
    onNodeClick?: (node: OrgGraphNode) => void;
    /** Callback when a node is double-clicked (e.g., to open detail) */
    onNodeDoubleClick?: (node: OrgGraphNode) => void;
    /** Filter by node type */
    filterType?: OrgGraphNodeType | 'all';
    /** Filter by department ID */
    filterDepartmentId?: string | null;
    /** Whether to show edges */
    showEdges?: boolean;
    /** Layout mode */
    layout?: 'grid' | 'hierarchical' | 'force';
    /** Additional CSS classes */
    className?: string;
}

/**
 * Node type configuration for styling
 */
const NODE_TYPE_CONFIG: Record<OrgGraphNodeType, { icon: string; color: string; bgColor: string }> = {
    department: { icon: '🏢', color: 'text-blue-700 dark:text-blue-300', bgColor: 'bg-blue-100 dark:bg-blue-900/30' },
    service: { icon: '⚙️', color: 'text-emerald-700 dark:text-emerald-300', bgColor: 'bg-emerald-100 dark:bg-emerald-900/30' },
    workflow: { icon: '🔄', color: 'text-purple-700 dark:text-purple-300', bgColor: 'bg-purple-100 dark:bg-purple-900/30' },
    integration: { icon: '🔌', color: 'text-orange-700 dark:text-orange-300', bgColor: 'bg-orange-100 dark:bg-orange-900/30' },
    persona: { icon: '👤', color: 'text-indigo-700 dark:text-indigo-300', bgColor: 'bg-indigo-100 dark:bg-indigo-900/30' },
};

/**
 * Get status color for integrations
 */
function getStatusColor(status: string): string {
    switch (status) {
        case 'healthy':
            return 'bg-green-500';
        case 'degraded':
            return 'bg-yellow-500';
        case 'down':
            return 'bg-red-500';
        default:
            return 'bg-slate-400';
    }
}

/**
 * Get risk level color for services
 */
function getRiskColor(riskLevel: string): string {
    switch (riskLevel) {
        case 'critical':
            return 'border-red-500';
        case 'high':
            return 'border-orange-500';
        case 'medium':
            return 'border-yellow-500';
        default:
            return 'border-slate-300 dark:border-neutral-600';
    }
}

/**
 * OrgGraphCanvas - Visualizes the organization structure
 *
 * <p><b>Purpose</b><br>
 * Interactive canvas for visualizing departments, services, workflows,
 * integrations, and their relationships. Supports filtering, selection,
 * and navigation to detail views.
 *
 * <p><b>Features</b><br>
 * - Grid layout with nodes grouped by type/department
 * - Node selection with visual feedback
 * - Type-based filtering
 * - Department-based filtering
 * - Status indicators for integrations
 * - Risk level indicators for services
 * - Click and double-click handlers for navigation
 *
 * @doc.type component
 * @doc.purpose Organization graph visualization
 * @doc.layer shared
 * @doc.pattern Visualization Component
 */
export function OrgGraphCanvas({
    data,
    selectedNodeId,
    onNodeClick,
    onNodeDoubleClick,
    filterType = 'all',
    filterDepartmentId,
    showEdges = true,
    layout = 'grid',
    className = '',
}: OrgGraphCanvasProps) {
    const [hoveredNodeId, setHoveredNodeId] = useState<string | null>(null);

    // Filter nodes based on type and department
    const filteredNodes = useMemo(() => {
        let nodes = data.nodes;

        if (filterType !== 'all') {
            nodes = nodes.filter((n) => n.type === filterType);
        }

        if (filterDepartmentId) {
            nodes = nodes.filter((n) => {
                if (n.type === 'department') {
                    return n.id === filterDepartmentId;
                }
                if (n.type === 'service') {
                    return (n.data as ServiceConfig).departmentId === filterDepartmentId;
                }
                if (n.type === 'integration') {
                    return (n.data as IntegrationConfig).departmentIds.includes(filterDepartmentId);
                }
                return true;
            });
        }

        return nodes;
    }, [data.nodes, filterType, filterDepartmentId]);

    // Filter edges to only show connections between visible nodes
    const filteredEdges = useMemo(() => {
        if (!showEdges) return [];
        const nodeIds = new Set(filteredNodes.map((n) => n.id));
        return data.edges.filter((e) => nodeIds.has(e.source) && nodeIds.has(e.target));
    }, [data.edges, filteredNodes, showEdges]);

    // Group nodes by type for grid layout
    const groupedNodes = useMemo(() => {
        const groups: Record<OrgGraphNodeType, OrgGraphNode[]> = {
            department: [],
            service: [],
            workflow: [],
            integration: [],
            persona: [],
        };

        filteredNodes.forEach((node) => {
            groups[node.type].push(node);
        });

        return groups;
    }, [filteredNodes]);

    const handleNodeClick = useCallback(
        (node: OrgGraphNode) => {
            onNodeClick?.(node);
        },
        [onNodeClick]
    );

    const handleNodeDoubleClick = useCallback(
        (node: OrgGraphNode) => {
            onNodeDoubleClick?.(node);
        },
        [onNodeDoubleClick]
    );

    // Render a single node
    const renderNode = (node: OrgGraphNode) => {
        const config = NODE_TYPE_CONFIG[node.type];
        const isSelected = selectedNodeId === node.id;
        const isHovered = hoveredNodeId === node.id;

        // Get additional styling based on node data
        let statusIndicator = null;
        let riskBorder = '';

        if (node.type === 'integration') {
            const integration = node.data as IntegrationConfig;
            statusIndicator = (
                <span
                    className={`absolute top-1 right-1 w-2 h-2 rounded-full ${getStatusColor(integration.status)}`}
                    title={`Status: ${integration.status}`}
                />
            );
        }

        if (node.type === 'service') {
            const service = node.data as ServiceConfig;
            riskBorder = getRiskColor(service.riskLevel);
        }

        return (
            <button
                key={node.id}
                type="button"
                onClick={() => handleNodeClick(node)}
                onDoubleClick={() => handleNodeDoubleClick(node)}
                onMouseEnter={() => setHoveredNodeId(node.id)}
                onMouseLeave={() => setHoveredNodeId(null)}
                className={`
                    relative flex flex-col items-center justify-center p-3 rounded-lg border-2 transition-all
                    min-w-[100px] min-h-[80px]
                    ${config.bgColor}
                    ${isSelected
                        ? 'border-blue-500 ring-2 ring-blue-500/50 shadow-lg'
                        : isHovered
                            ? 'border-slate-400 dark:border-slate-500 shadow-md'
                            : riskBorder || 'border-transparent'
                    }
                    hover:shadow-md focus:outline-none focus:ring-2 focus:ring-blue-500
                `}
            >
                {statusIndicator}
                <span className="text-2xl mb-1">{node.style?.icon || config.icon}</span>
                <span className={`text-xs font-medium text-center ${config.color} line-clamp-2`}>
                    {node.label}
                </span>
                {node.type === 'service' && (
                    <span className="text-[10px] text-slate-500 dark:text-neutral-400 mt-0.5">
                        {(node.data as ServiceConfig).tier}
                    </span>
                )}
            </button>
        );
    };

    // Render a section of nodes by type
    const renderSection = (type: OrgGraphNodeType, nodes: OrgGraphNode[]) => {
        if (nodes.length === 0) return null;

        const config = NODE_TYPE_CONFIG[type];
        const typeLabel = type.charAt(0).toUpperCase() + type.slice(1) + 's';

        return (
            <div key={type} className="mb-6">
                <h3 className={`text-sm font-semibold mb-3 flex items-center gap-2 ${config.color}`}>
                    <span>{config.icon}</span>
                    <span>{typeLabel}</span>
                    <span className="text-slate-400 dark:text-slate-500 font-normal">({nodes.length})</span>
                </h3>
                <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 gap-3">
                    {nodes.map(renderNode)}
                </div>
            </div>
        );
    };

    // Empty state
    if (filteredNodes.length === 0) {
        return (
            <div className={`flex items-center justify-center h-64 ${className}`}>
                <div className="text-center text-slate-500 dark:text-neutral-400">
                    <span className="text-4xl mb-2 block">📭</span>
                    <p className="text-sm">No nodes match the current filters</p>
                </div>
            </div>
        );
    }

    return (
        <div className={`p-4 ${className}`}>
            {/* Edge visualization (simplified - just show count for now) */}
            {showEdges && filteredEdges.length > 0 && (
                <div className="mb-4 text-xs text-slate-500 dark:text-neutral-400">
                    {filteredEdges.length} connection{filteredEdges.length !== 1 ? 's' : ''} between nodes
                </div>
            )}

            {/* Grid layout by type */}
            {layout === 'grid' && (
                <div>
                    {renderSection('department', groupedNodes.department)}
                    {renderSection('service', groupedNodes.service)}
                    {renderSection('workflow', groupedNodes.workflow)}
                    {renderSection('integration', groupedNodes.integration)}
                    {renderSection('persona', groupedNodes.persona)}
                </div>
            )}
        </div>
    );
}

export default OrgGraphCanvas;
