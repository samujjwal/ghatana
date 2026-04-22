/**
 * DataFabricPage — Four-tier data fabric topology visualizer.
 *
 * Renders the live HOT→WARM→COOL→COLD event-cloud topology using
 * `@ghatana/canvas/flow` with real-time throughput metrics from DC API.
 *
 * This is the first production consumer of the `@ghatana/canvas/flow` library.
 *
 * @doc.type component
 * @doc.purpose Four-tier data fabric topology visualizer
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useMemo, useCallback, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
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
} from '@ghatana/canvas/flow';
import { migrateCollection as migrateCollectionApi, type MigrationTargetTier } from '../api/cost.service';
import { UnsupportedSurfaceBoundary } from '../components/common/UnsupportedSurfaceBoundary';
import { dataFabricMetricsBoundary } from '../components/common/unsupportedSurfaceRegistry';

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

const PREVIEW_FABRIC_METRICS: FabricMetricsResponse = {
  tiers: [
    {
      tier: 'HOT',
      label: 'HOT Tier (Redis)',
      throughputEps: 18250,
      latencyP99Ms: 18,
      errorRate: 0.001,
      queueDepth: 42,
      status: 'healthy',
      instanceCount: 3,
    },
    {
      tier: 'WARM',
      label: 'WARM Tier (PostgreSQL)',
      throughputEps: 9300,
      latencyP99Ms: 64,
      errorRate: 0.003,
      queueDepth: 17,
      status: 'healthy',
      instanceCount: 2,
      storageGb: 460.2,
    },
    {
      tier: 'COOL',
      label: 'COOL Tier (Iceberg)',
      throughputEps: 1200,
      latencyP99Ms: 140,
      errorRate: 0,
      queueDepth: 3,
      status: 'healthy',
      instanceCount: 1,
      storageGb: 1820.7,
    },
    {
      tier: 'COLD',
      label: 'COLD Tier (S3/Archive)',
      throughputEps: 85,
      latencyP99Ms: 1900,
      errorRate: 0,
      queueDepth: 0,
      status: 'healthy',
      instanceCount: 1,
      storageGb: 12240.4,
    },
  ],
  totalEventsPerSec: 28835,
  totalStorageGb: 14521.3,
  lastUpdated: 'preview',
};

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
 * DataFabricPage — four-tier topology visualizer using `@ghatana/canvas/flow`.
 *
 * @doc.type component
 * @doc.purpose Data fabric topology view with live metrics
 * @doc.layer product
 * @doc.pattern Page
 */
export function DataFabricPage(): React.ReactElement {
  const fabricMetrics = PREVIEW_FABRIC_METRICS;

  // B10: Manual tier migration state
  const [migrateCollection, setMigrateCollection] = useState('');
  const [migrateTargetTier, setMigrateTargetTier] = useState<MigrationTargetTier>('WARM');
  const [migrateOpen, setMigrateOpen] = useState(false);

  const migrateMutation = useMutation({
    mutationFn: () => migrateCollection
      ? migrateCollectionApi(migrateCollection, migrateTargetTier)
      : Promise.reject(new Error('Collection is required')),
    onSuccess: () => {
      setMigrateCollection('');
      setMigrateOpen(false);
    },
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
        <div className="flex items-center gap-3">
          {/* B10: Manual Tier Migration */}
          <button
            type="button"
            onClick={() => setMigrateOpen((v) => !v)}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-md hover:bg-gray-50 text-gray-700"
          >
            Migrate Tier
          </button>
        </div>
      </div>

      {/* B10: Tier migration inline panel */}
      {migrateOpen && (
        <div className="px-6 py-3 border-b border-amber-200 bg-amber-50 flex items-center gap-3 flex-wrap">
          <span className="text-sm font-medium text-amber-800">Manual tier migration</span>
          <input
            type="text"
            placeholder="Collection / stream name"
            aria-label="Collection or stream name for tier migration"
            value={migrateCollection}
            onChange={(e: React.ChangeEvent<HTMLInputElement>) => setMigrateCollection(e.target.value)}
            className="border border-gray-300 rounded px-2 py-1 text-sm w-52"
          />
          <select
            aria-label="Target tier for migration"
            value={migrateTargetTier}
            onChange={(e: React.ChangeEvent<HTMLSelectElement>) =>
              setMigrateTargetTier(e.target.value as MigrationTargetTier)
            }
            className="border border-gray-300 rounded px-2 py-1 text-sm"
          >
            <option value="WARM">→ WARM (L1→L2 Iceberg)</option>
            <option value="COLD">→ COLD (L2→L3 S3 Archive)</option>
          </select>
          <button
            type="button"
            disabled={!migrateCollection || migrateMutation.isPending}
            onClick={() => migrateMutation.mutate()}
            className="px-3 py-1 text-sm bg-amber-600 text-white rounded hover:bg-amber-700 disabled:opacity-50"
          >
            {migrateMutation.isPending ? 'Migrating…' : 'Start Migration'}
          </button>
          {migrateMutation.isSuccess && (
            <span className="text-sm text-green-700">
              ✓ {migrateMutation.data?.status} — {migrateMutation.data?.eventsMigrated} events
            </span>
          )}
          {migrateMutation.isError && (
            <span className="text-sm text-red-600">
              Error: {migrateMutation.error instanceof Error ? migrateMutation.error.message : 'Migration failed'}
            </span>
          )}
          <button
            type="button"
            onClick={() => { setMigrateOpen(false); migrateMutation.reset(); }}
            className="text-sm text-gray-500 hover:text-gray-700 ml-auto"
          >
            Close
          </button>
        </div>
      )}

      <UnsupportedSurfaceBoundary
        className="mx-6 mt-4"
        title={dataFabricMetricsBoundary.title}
        summary={dataFabricMetricsBoundary.summary}
        details={dataFabricMetricsBoundary.details}
        state={dataFabricMetricsBoundary.state}
      />

      <StatBar metrics={fabricMetrics} />

      {/* Main canvas area */}
      <div className="flex-1 relative">
        {nodes.length === 0 && (
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
          controls={{ showMiniMap: true, showControls: true, controlsPosition: 'top-right' }}
        >
          <FlowControls position="top-right" showZoom showFitView showInteractive />
        </FlowCanvas>
        <TierLegend />
      </div>
    </div>
  );
}
