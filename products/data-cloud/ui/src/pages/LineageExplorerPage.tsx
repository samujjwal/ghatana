/**
 * Lineage Explorer Page
 *
 * Visualize end-to-end data lineage across datasets, workflows, and reports.
 * Part of Journey 9: Lineage Explorer & Root Cause Analysis
 *
 * @doc.type page
 * @doc.purpose Interactive lineage graph visualization
 * @doc.layer frontend
 */

import React, { useCallback, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  GitBranch,
  Search,
  Filter,
  Clock,
  Database,
  AlertTriangle,
  TrendingUp,
  Workflow,
  FileText,
  ZoomIn,
  ZoomOut,
  Maximize2,
} from 'lucide-react';
import { cn, bgStyles, inputStyles, textStyles, buttonStyles, badgeStyles } from '../lib/theme';
import { lineageService } from '../api/lineage.service';
import { LineageGraph } from '../components/lineage/LineageGraph';
import { DashboardKPI } from '../components/cards/DashboardCard';

type NodeType = 'source' | 'collection' | 'workflow' | 'output' | 'report';

interface LineageNode {
  id: string;
  name: string;
  type: NodeType;
  description: string;
}


/**
 * Lineage edge interface
 */
interface LineageEdge {
  id: string;
  source: string;
  target: string;
  label?: string;
}

/**
 * Mock lineage data
 */
const mockNodes: LineageNode[] = [
  { id: 'src-1', name: 'User Service API', type: 'source', description: 'REST API events' },
  { id: 'src-2', name: 'Payment Gateway', type: 'source', description: 'Transaction events' },
  { id: 'col-1', name: 'user_events', type: 'collection', description: 'Raw user activity' },
  { id: 'col-2', name: 'transactions', type: 'collection', description: 'Payment records' },
  { id: 'wf-1', name: 'ETL Pipeline', type: 'workflow', description: 'Data transformation' },
  { id: 'wf-2', name: 'Fraud Detection', type: 'workflow', description: 'ML pipeline' },
  { id: 'col-3', name: 'enriched_events', type: 'collection', description: 'Processed events' },
  { id: 'col-4', name: 'fraud_signals', type: 'output', description: 'Fraud indicators' },
  { id: 'rpt-1', name: 'Daily Dashboard', type: 'report', description: 'KPI metrics' },
];

const mockEdges: LineageEdge[] = [
  { id: 'e1', source: 'src-1', target: 'col-1' },
  { id: 'e2', source: 'src-2', target: 'col-2' },
  { id: 'e3', source: 'col-1', target: 'wf-1' },
  { id: 'e4', source: 'col-2', target: 'wf-1' },
  { id: 'e5', source: 'wf-1', target: 'col-3' },
  { id: 'e6', source: 'col-3', target: 'wf-2' },
  { id: 'e7', source: 'col-2', target: 'wf-2' },
  { id: 'e8', source: 'wf-2', target: 'col-4' },
  { id: 'e9', source: 'col-3', target: 'rpt-1' },
];

/**
 * Node type styles
 */
const nodeTypeStyles: Record<NodeType, { bg: string; border: string; icon: React.ReactNode }> = {
  source: {
    bg: 'bg-blue-500',
    border: 'border-blue-500',
    icon: <Database className="h-4 w-4" />,
  },
  collection: {
    bg: 'bg-green-500',
    border: 'border-green-500',
    icon: <Database className="h-4 w-4" />,
  },
  workflow: {
    bg: 'bg-purple-500',
    border: 'border-purple-500',
    icon: <Workflow className="h-4 w-4" />,
  },
  output: {
    bg: 'bg-orange-500',
    border: 'border-orange-500',
    icon: <FileText className="h-4 w-4" />,
  },
  report: {
    bg: 'bg-pink-500',
    border: 'border-pink-500',
    icon: <FileText className="h-4 w-4" />,
  },
};

/**
 * Node positions for visualization (simplified layout)
 */
const nodePositions: Record<string, { x: number; y: number }> = {
  'src-1': { x: 50, y: 100 },
  'src-2': { x: 50, y: 250 },
  'col-1': { x: 200, y: 100 },
  'col-2': { x: 200, y: 250 },
  'wf-1': { x: 350, y: 175 },
  'col-3': { x: 500, y: 100 },
  'wf-2': { x: 500, y: 250 },
  'col-4': { x: 650, y: 250 },
  'rpt-1': { x: 650, y: 100 },
};

/**
 * Lineage Explorer Page Component
 *
 * @returns JSX element
 */
export function LineageExplorerPage() {
  const [selectedDatasetId, setSelectedDatasetId] = useState('ds-001');
  const [selectedNode, setSelectedNode] = useState<LineageNode | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [timeTravelDate, setTimeTravelDate] = useState<string | undefined>();
  const [zoom, setZoom] = useState(1);

  const apiUrl = (import.meta.env as any).VITE_API_URL as string | undefined;
  const apiEnabled = import.meta.env.PROD || Boolean(apiUrl);

  // Fetch lineage data
  const { data: lineageData, isLoading } = useQuery({
    queryKey: ['lineage', selectedDatasetId, timeTravelDate],
    enabled: apiEnabled,
    queryFn: () => lineageService.getLineage(selectedDatasetId, 'BOTH', 3),
  });

  // Fetch impact analysis
  const { data: impactData } = useQuery({
    queryKey: ['impact-analysis', selectedDatasetId],
    enabled: apiEnabled,
    queryFn: () => lineageService.getImpactAnalysis(selectedDatasetId),
  });

  // Fetch execution logs
  const { data: executionLogs } = useQuery({
    queryKey: ['execution-logs', selectedDatasetId],
    enabled: apiEnabled,
    queryFn: () => lineageService.getExecutionLogs(selectedDatasetId, 10),
  });
  const [filterType, setFilterType] = useState<NodeType | 'all'>('all');

  const filteredNodes = mockNodes.filter((node) => {
    if (filterType !== 'all' && node.type !== filterType) return false;
    if (searchQuery && !node.name.toLowerCase().includes(searchQuery.toLowerCase())) return false;
    return true;
  });

  const handleZoomIn = useCallback(() => setZoom((z) => Math.min(z + 0.1, 2)), []);
  const handleZoomOut = useCallback(() => setZoom((z) => Math.max(z - 0.1, 0.5)), []);
  const handleResetZoom = useCallback(() => setZoom(1), []);

  return (
    <div className={cn('min-h-screen', bgStyles.page)}>
      <div className="flex h-screen">
        {/* Sidebar */}
        <aside className="w-80 border-r border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 flex flex-col">
          {/* Search */}
          <div className="p-4 border-b border-gray-200 dark:border-gray-700">
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400" />
              <input
                type="text"
                placeholder="Search nodes..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className={cn(inputStyles.base, 'pl-10')}
              />
            </div>
            <div className="mt-3 flex gap-2">
              <select
                value={filterType}
                onChange={(e) => setFilterType(e.target.value as NodeType | 'all')}
                className={cn(inputStyles.select, 'text-sm')}
              >
                <option value="all">All Types</option>
                <option value="source">Sources</option>
                <option value="collection">Collections</option>
                <option value="workflow">Workflows</option>
                <option value="output">Outputs</option>
                <option value="report">Reports</option>
              </select>
            </div>
          </div>

          {/* Node List */}
          <div className="flex-1 overflow-auto p-2">
            <p className={cn(textStyles.xs, 'px-2 py-1')}>
              {filteredNodes.length} nodes
            </p>
            {filteredNodes.map((node) => (
              <button
                key={node.id}
                onClick={() => setSelectedNode(node)}
                className={cn(
                  'w-full text-left p-3 rounded-lg mb-1 transition-colors',
                  selectedNode?.id === node.id
                    ? 'bg-blue-50 dark:bg-blue-900/30 border border-blue-200 dark:border-blue-700'
                    : 'hover:bg-gray-100 dark:hover:bg-gray-700'
                )}
              >
                <div className="flex items-center gap-2">
                  <div className={cn('p-1 rounded text-white', nodeTypeStyles[node.type].bg)}>
                    {nodeTypeStyles[node.type].icon}
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={textStyles.h4}>{node.name}</p>
                    <p className={cn(textStyles.xs, 'truncate')}>{node.description}</p>
                  </div>
                </div>
              </button>
            ))}
          </div>

          {/* Legend */}
          <div className="p-4 border-t border-gray-200 dark:border-gray-700">
            <p className={cn(textStyles.label, 'mb-2')}>Legend</p>
            <div className="grid grid-cols-2 gap-2 text-xs">
              {Object.entries(nodeTypeStyles).map(([type, style]) => (
                <div key={type} className="flex items-center gap-2">
                  <div className={cn('w-3 h-3 rounded', style.bg)} />
                  <span className={textStyles.small}>{type}</span>
                </div>
              ))}
            </div>
          </div>
        </aside>

        {/* Main Content */}
        <main className="flex-1 flex flex-col">
          {/* Toolbar */}
          <div className={cn('border-b border-gray-200 dark:border-gray-700 p-4', bgStyles.surface)}>
            <div className="flex items-center justify-between">
              <div>
                <h1 className={textStyles.h1}>Lineage Explorer</h1>
                <p className={textStyles.muted}>
                  Visualize upstream and downstream data relationships
                </p>
              </div>
              <div className="flex items-center gap-2">
                <div className="flex items-center border border-gray-200 dark:border-gray-700 rounded-lg">
                  <button
                    onClick={handleZoomOut}
                    className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-l-lg"
                  >
                    <ZoomOut className="h-4 w-4" />
                  </button>
                  <span className={cn(textStyles.small, 'px-2 min-w-[60px] text-center')}>
                    {Math.round(zoom * 100)}%
                  </span>
                  <button
                    onClick={handleZoomIn}
                    className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-r-lg"
                  >
                    <ZoomIn className="h-4 w-4" />
                  </button>
                </div>
                <button
                  onClick={handleResetZoom}
                  className={cn(buttonStyles.secondary, 'p-2')}
                >
                  <Maximize2 className="h-4 w-4" />
                </button>
              </div>
            </div>
          </div>

          {/* Graph Canvas */}
          <div className="flex-1 overflow-auto bg-gray-50 dark:bg-gray-900 relative">
            <svg
              className="w-full h-full min-w-[800px] min-h-[400px]"
              style={{ transform: `scale(${zoom})`, transformOrigin: 'top left' }}
            >
              {/* Edges */}
              <defs>
                <marker
                  id="arrowhead"
                  markerWidth="10"
                  markerHeight="7"
                  refX="9"
                  refY="3.5"
                  orient="auto"
                >
                  <polygon
                    points="0 0, 10 3.5, 0 7"
                    fill="currentColor"
                    className="text-gray-400"
                  />
                </marker>
              </defs>
              {mockEdges.map((edge) => {
                const sourcePos = nodePositions[edge.source];
                const targetPos = nodePositions[edge.target];
                if (!sourcePos || !targetPos) return null;

                return (
                  <line
                    key={edge.id}
                    x1={sourcePos.x + 60}
                    y1={sourcePos.y + 25}
                    x2={targetPos.x - 10}
                    y2={targetPos.y + 25}
                    stroke="currentColor"
                    strokeWidth="2"
                    className="text-gray-300 dark:text-gray-600"
                    markerEnd="url(#arrowhead)"
                  />
                );
              })}
            </svg>

            {/* Nodes */}
            {mockNodes.map((node) => {
              const pos = nodePositions[node.id];
              if (!pos) return null;
              const style = nodeTypeStyles[node.type];
              const isSelected = selectedNode?.id === node.id;

              return (
                <div
                  key={node.id}
                  onClick={() => setSelectedNode(node)}
                  className={cn(
                    'absolute cursor-pointer transition-all',
                    'bg-white dark:bg-gray-800 rounded-lg shadow-md',
                    'border-2 p-3 min-w-[120px]',
                    isSelected ? style.border : 'border-gray-200 dark:border-gray-700',
                    isSelected && 'ring-2 ring-offset-2 ring-blue-500'
                  )}
                  style={{
                    left: pos.x * zoom,
                    top: pos.y * zoom,
                    transform: `scale(${zoom})`,
                    transformOrigin: 'top left',
                  }}
                >
                  <div className="flex items-center gap-2">
                    <div className={cn('p-1 rounded text-white', style.bg)}>
                      {style.icon}
                    </div>
                    <div>
                      <p className={cn(textStyles.h4, 'text-xs')}>{node.name}</p>
                      <p className={cn(textStyles.xs, 'text-[10px]')}>{node.type}</p>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>

          {/* Node Details Panel */}
          {selectedNode && (
            <div className={cn('border-t border-gray-200 dark:border-gray-700 p-4', bgStyles.surface)}>
              <div className="flex items-start justify-between">
                <div className="flex items-center gap-3">
                  <div className={cn('p-2 rounded text-white', nodeTypeStyles[selectedNode.type].bg)}>
                    {nodeTypeStyles[selectedNode.type].icon}
                  </div>
                  <div>
                    <h3 className={textStyles.h3}>{selectedNode.name}</h3>
                    <p className={textStyles.muted}>{selectedNode.description}</p>
                  </div>
                </div>
                <div className="flex gap-2">
                  <button className={buttonStyles.secondary}>View Details</button>
                  <button className={buttonStyles.primary}>Trace Lineage</button>
                </div>
              </div>
              <div className="mt-4 flex gap-6">
                <div>
                  <p className={textStyles.label}>Type</p>
                  <span className={cn(badgeStyles.info, 'mt-1 inline-block')}>{selectedNode.type}</span>
                </div>
                <div>
                  <p className={textStyles.label}>ID</p>
                  <p className={textStyles.mono}>{selectedNode.id}</p>
                </div>
                <div>
                  <p className={textStyles.label}>Upstream</p>
                  <p className={textStyles.body}>
                    {mockEdges.filter((e) => e.target === selectedNode.id).length} sources
                  </p>
                </div>
                <div>
                  <p className={textStyles.label}>Downstream</p>
                  <p className={textStyles.body}>
                    {mockEdges.filter((e) => e.source === selectedNode.id).length} targets
                  </p>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}

