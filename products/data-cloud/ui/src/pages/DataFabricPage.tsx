/**
 * DataFabricPage — Four-tier data fabric topology visualizer.
 *
 * Renders the live HOT→WARM→COOL→COLD event-cloud topology using
 * `@ghatana/flow-canvas` with real-time throughput metrics from DC API.
 *
 * This is the first production consumer of the `@ghatana/flow-canvas` library.
 *
 * @doc.type component
 * @doc.purpose Four-tier data fabric topology visualizer
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useMemo, useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  FlowCanvas,
  FlowControls,
  useNodesState,
  useEdgesState,
  addEdge,
  MarkerType,
  type FlowNode,
  type FlowEdge,
  type OnConnect,
} from '@ghatana/flow-canvas';
import axios from 'axios';

// =============================================================================
// Types
// =============================================================================

interface TierMetrics {
  tier: 'HOT' | 'WARM' | 'COOL' | 'COLD';
  label: string;
  throughputEps: number;      // events/sec
  latencyP99Ms: number;       // p99 latency ms
  errorRate: number;          // 0-1
  queueDepth: number;
  status: 'healthy' | 'warning' | 'error' | 'inactive';
  instanceCount: number;
  storageGb?: number;
}

interface FabricMetricsResponse {
  tiers: TierMetrics[];
  totalEventsPerSec: number;
  totalStorageGb: number;
  lastUpdated: string;
}

// =============================================================================
// API
// =============================================================================

const DC_BASE = import.meta.env.VITE_DC_API_URL ?? '/api';
const dc = axios.create({ baseURL: DC_BASE });

async function fetchFabricMetrics(): Promise<FabricMetricsResponse> {
  const { data } = await dc.get<FabricMetricsResponse>('/dc/fabric/metrics');
  return data;
}

// =============================================================================
// Node layout
// =============================================================================

/** Build topology nodes from live tier metrics */
function buildNodes(tiers: TierMetrics[]): FlowNode[] {
  const byTier = new Map(tiers.map((t) => [t.tier, t]));

  const hot = byTier.get('HOT');
  const warm = byTier.get('WARM');
  const cool = byTier.get('COOL');
  const cold = byTier.get('COLD');

  const nodes: FlowNode[] = [];

  if (hot) {
    nodes.push({
      id: 'hot-tier',
      type: 'hotTier',
      position: { x: 50, y: 200 },
      data: {
        label: hot.label || 'HOT Tier (Redis)',
        status: hot.status,
        description: `${hot.instanceCount} instance(s) · P99 ${hot.latencyP99Ms}ms`,
        metrics: {
          throughput: hot.throughputEps,
          latencyMs: hot.latencyP99Ms,
          errorRate: hot.errorRate,
          queueDepth: hot.queueDepth,
        },
      },
    });
  }

  if (warm) {
    nodes.push({
      id: 'warm-tier',
      type: 'warmTier',
      position: { x: 350, y: 200 },
      data: {
        label: warm.label || 'WARM Tier (PostgreSQL)',
        status: warm.status,
        description: `${warm.instanceCount} instance(s) · P99 ${warm.latencyP99Ms}ms`,
        metrics: {
          throughput: warm.throughputEps,
          latencyMs: warm.latencyP99Ms,
          errorRate: warm.errorRate,
          queueDepth: warm.queueDepth,
        },
      },
    });
  }

  if (cool) {
    nodes.push({
      id: 'cool-tier',
      type: 'coldTier',
      position: { x: 650, y: 200 },
      data: {
        label: cool.label || 'COOL Tier (Iceberg)',
        status: cool.status,
        description: `${cool.instanceCount} instance(s)` +
          (cool.storageGb ? ` · ${cool.storageGb.toFixed(1)} GB` : ''),
        metrics: {
          throughput: cool.throughputEps,
          latencyMs: cool.latencyP99Ms,
          errorRate: cool.errorRate,
        },
      },
    });
  }

  if (cold) {
    nodes.push({
      id: 'cold-tier',
      type: 'archiveTier',
      position: { x: 950, y: 200 },
      data: {
        label: cold.label || 'COLD Tier (S3/Archive)',
        status: cold.status,
        description: cold.storageGb ? `${cold.storageGb.toFixed(1)} GB archived` : 'Archive storage',
        metrics: {
          throughput: cold.throughputEps,
          latencyMs: cold.latencyP99Ms,
          errorRate: cold.errorRate,
        },
      },
    });
  }

  return nodes;
}

/** Build data-flow edges between tiers */
function buildEdges(tiers: TierMetrics[]): FlowEdge[] {
  const byTier = new Map(tiers.map((t) => [t.tier, t]));
  const edges: FlowEdge[] = [];

  const tierPairs: Array<[string, string, 'HOT' | 'WARM' | 'COOL' | 'COLD']> = [
    ['hot-tier', 'warm-tier', 'HOT'],
    ['warm-tier', 'cool-tier', 'WARM'],
    ['cool-tier', 'cold-tier', 'COOL'],
  ];

  for (const [source, target, sourceTier] of tierPairs) {
    const src = byTier.get(sourceTier);
    if (!src) continue;
    edges.push({
      id: `${source}-to-${target}`,
      source,
      target,
      type: 'dataFlow',
      animated: true,
      markerEnd: { type: MarkerType.ArrowClosed },
      data: {
        throughput: src.throughputEps,
        animated: true,
      },
    });
  }

  return edges;
}

// =============================================================================
// Statistics bar
// =============================================================================

function StatBar({
  metrics,
}: {
  metrics: FabricMetricsResponse | undefined;
}): React.ReactElement {
  if (!metrics) return <></>;
  return (
    <div className="flex items-center gap-6 px-6 py-3 bg-gray-50 border-b border-gray-200 text-sm">
      <span className="text-gray-600">
        Total throughput: <strong>{metrics.totalEventsPerSec.toFixed(1)} events/sec</strong>
      </span>
      <span className="text-gray-600">
        Total storage: <strong>{metrics.totalStorageGb.toFixed(1)} GB</strong>
      </span>
      <span className="text-xs text-gray-400 ml-auto">
        Updated: {new Date(metrics.lastUpdated).toLocaleTimeString()}
      </span>
    </div>
  );
}

// =============================================================================
// Tier Legend
// =============================================================================

function TierLegend(): React.ReactElement {
  return (
    <div className="absolute bottom-4 left-4 z-10 bg-white border border-gray-200 rounded-lg shadow-sm px-4 py-3 text-xs">
      <p className="font-semibold text-gray-700 mb-2">Tier Legend</p>
      {[
        { label: 'HOT (Redis)', color: 'bg-red-500' },
        { label: 'WARM (PostgreSQL)', color: 'bg-orange-500' },
        { label: 'COOL (Iceberg)', color: 'bg-blue-500' },
        { label: 'COLD (S3/Archive)', color: 'bg-slate-500' },
      ].map(({ label, color }) => (
        <div key={label} className="flex items-center gap-2 mb-1">
          <span className={`inline-block w-3 h-3 rounded-sm ${color}`} />
          <span className="text-gray-600">{label}</span>
        </div>
      ))}
    </div>
  );
}

// =============================================================================
// Page
// =============================================================================

/**
 * DataFabricPage — four-tier topology visualizer using `@ghatana/flow-canvas`.
 *
 * @doc.type component
 * @doc.purpose Data fabric topology view with live metrics
 * @doc.layer product
 * @doc.pattern Page
 */
export function DataFabricPage(): React.ReactElement {
  const { data: fabricMetrics, isLoading, error } = useQuery({
    queryKey: ['dc', 'fabric', 'metrics'],
    queryFn: fetchFabricMetrics,
    refetchInterval: 15_000,
    staleTime: 10_000,
    retry: 1,
  });

  const initialNodes = useMemo<FlowNode[]>(
    () => (fabricMetrics ? buildNodes(fabricMetrics.tiers) : []),
    [fabricMetrics],
  );

  const initialEdges = useMemo<FlowEdge[]>(
    () => (fabricMetrics ? buildEdges(fabricMetrics.tiers) : []),
    [fabricMetrics],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(initialEdges);

  // Sync nodes/edges when live data updates
  React.useEffect(() => {
    if (fabricMetrics) {
      setNodes(buildNodes(fabricMetrics.tiers));
      setEdges(buildEdges(fabricMetrics.tiers));
    }
  }, [fabricMetrics, setNodes, setEdges]);

  const onConnect = useCallback<OnConnect>(
    (connection) => setEdges((eds) => addEdge(connection, eds)),
    [setEdges],
  );

  return (
    <div className="flex flex-col h-full bg-white" data-testid="data-fabric-page">
      {/* Header */}
      <div className="px-6 py-4 border-b border-gray-200 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900">Data Fabric</h1>
          <p className="text-sm text-gray-500 mt-0.5">
            Live four-tier event cloud topology — HOT → WARM → COOL → COLD
          </p>
        </div>
        {isLoading && (
          <span className="text-sm text-gray-400 animate-pulse">Fetching metrics…</span>
        )}
      </div>

      <StatBar metrics={fabricMetrics} />

      {/* Main canvas area */}
      <div className="flex-1 relative">
        {error instanceof Error && (
          <div className="absolute inset-0 flex items-center justify-center z-20">
            <div className="bg-red-50 border border-red-200 rounded-lg p-6 max-w-md text-center">
              <p className="text-red-700 font-medium">Failed to load fabric metrics</p>
              <p className="text-red-500 text-sm mt-1">{error.message}</p>
              <p className="text-gray-500 text-xs mt-3">
                Showing topology skeleton — connect to Data-Cloud API for live data
              </p>
            </div>
          </div>
        )}

        {!isLoading && nodes.length === 0 && !error && (
          <div className="absolute inset-0 flex items-center justify-center z-10 text-gray-400 text-sm">
            No tier data available
          </div>
        )}

        <FlowCanvas
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
          minZoom={0.3}
          maxZoom={2}
          proOptions={{ hideAttribution: true }}
        >
          <TierLegend />
          <FlowControls position="top-right" showMiniMap showFitView />
        </FlowCanvas>
      </div>
    </div>
  );
}
