/**
 * DataFabricPage — Four-tier data fabric topology visualizer.
 *
 * Renders the HOT→WARM→COOL→COLD event-cloud topology using
 * `@ghatana/canvas/flow` with throughput metrics from DC API.
 * This surface is in preview — live topology metrics are not yet connected.
 *
 * This is the first production consumer of the `@ghatana/canvas/flow` library.
 *
 * @doc.type component
 * @doc.purpose Four-tier data fabric topology visualizer
 * @doc.layer product
 * @doc.pattern Page
 */

import React, { useMemo, useCallback, useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { Button } from '@ghatana/design-system';
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
import { apiClient } from '../lib/api/client';
import { migrateCollection as migrateCollectionApi, type MigrationTargetTier } from '../api/cost.service';
import { aiOperationsService, type AiFabricAdvisory } from '../api/ai-operations.service';
import { UnsupportedRuntimeBoundaryError } from '../lib/runtime-boundaries';
import { UnsupportedSurfaceBoundary } from '../components/common/UnsupportedSurfaceBoundary';
import { dataFabricMetricsBoundary } from '../components/common/unsupportedSurfaceRegistry';
import { GuardedAction } from '../components/common/GuardedAction';
import { AIAssistSuggestion } from '../components/common/AIAssistSuggestion';

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

interface PlacementRecommendation {
  targetTier: MigrationTargetTier;
  confidence: number;
  rationale: string;
  evidence: string[];
}

async function fetchFabricMetrics(): Promise<FabricMetricsResponse> {
  return apiClient.get<FabricMetricsResponse>('/data-fabric/metrics');
}

function derivePlacementRecommendation(metrics: FabricMetricsResponse | undefined): PlacementRecommendation {
  if (!metrics || metrics.tiers.length === 0) {
    return {
      targetTier: 'WARM',
      confidence: 0.35,
      rationale: 'No live metrics available. Keep workloads on WARM tier until fabric telemetry is connected.',
      evidence: ['No data-fabric tier metrics returned by API.', 'Recommendation is heuristic fallback only.'],
    };
  }

  const hotTier = metrics.tiers.find((tier) => tier.tier === 'HOT');
  const warmTier = metrics.tiers.find((tier) => tier.tier === 'WARM');

  if (hotTier && hotTier.throughputEps < 120 && hotTier.queueDepth < 20) {
    return {
      targetTier: 'WARM',
      confidence: 0.78,
      rationale: 'HOT tier usage is low. Migrating inactive collections to WARM reduces hot-storage spend while preserving query latency.',
      evidence: [
        `HOT throughput: ${hotTier.throughputEps.toFixed(1)} events/sec`,
        `HOT queue depth: ${hotTier.queueDepth}`,
        `WARM status: ${warmTier?.status ?? 'unknown'}`,
      ],
    };
  }

  return {
    targetTier: 'COLD',
    confidence: 0.61,
    rationale: 'Current metrics suggest long-tail archival opportunity. Move stale collections to COLD after validating retention constraints.',
    evidence: [
      `Total storage footprint: ${metrics.totalStorageGb.toFixed(1)} GB`,
      `Tier count sampled: ${metrics.tiers.length}`,
      'Recommendation is advisory and requires operator confirmation.',
    ],
  };
}

// =============================================================================
// Node layout
// =============================================================================

/** Build topology nodes from tier metrics */
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
 * @doc.purpose Data fabric topology view (preview — metrics not yet live)
 * @doc.layer product
 * @doc.pattern Page
 */
export function DataFabricPage(): React.ReactElement {
  // Fabric metrics from DC API (preview — live data not yet connected)
  const { data: fabricMetrics } = useQuery<FabricMetricsResponse>({
    queryKey: ['data-fabric', 'metrics'],
    queryFn: fetchFabricMetrics,
    staleTime: 60_000,
    refetchInterval: 60_000,
    refetchOnWindowFocus: false,
  });

  // B10: Manual tier migration state
  const [migrateCollection, setMigrateCollection] = useState('');
  const [migrateTargetTier, setMigrateTargetTier] = useState<MigrationTargetTier>('WARM');
  const [migrateOpen, setMigrateOpen] = useState(false);
  const [migrationReason, setMigrationReason] = useState('');

  const migrateMutation = useMutation({
    mutationFn: () => migrateCollection
      ? migrateCollectionApi(migrateCollection, migrateTargetTier)
      : Promise.reject(new Error('Collection is required')),
    onSuccess: () => {
      setMigrateCollection('');
      setMigrationReason('');
      setMigrateOpen(false);
    },
  });

  // AI-backed topology advisories — backend-first, heuristic fallback when ML platform is unavailable.
  const { data: fabricAdvisories } = useQuery<AiFabricAdvisory, Error>({
    queryKey: ['ai', 'advisories', 'fabric', 'topology'],
    queryFn: () => aiOperationsService.getFabricAdvisories('topology'),
    staleTime: 5 * 60_000,
    retry: false,
    refetchOnWindowFocus: false,
  });

  const placementRecommendation = useMemo(() => {
    // Prefer ML-backed advisory when available; fall back to deterministic heuristic.
    const topAdvisory = fabricAdvisories?.advisories[0];
    if (topAdvisory) {
      return {
        rationale: topAdvisory.description,
        confidence: topAdvisory.confidence,
        evidence: topAdvisory.suggestedAction ? [topAdvisory.suggestedAction] : [],
      };
    }
    return derivePlacementRecommendation(fabricMetrics);
  }, [fabricAdvisories, fabricMetrics]);

  const initialNodes = useMemo<FlowNode[]>(
    () => (fabricMetrics?.tiers ? buildNodes(fabricMetrics.tiers) : []),
    [fabricMetrics],
  );

  const initialEdges = useMemo<FlowEdge[]>(
    () => (fabricMetrics?.tiers ? buildEdges(fabricMetrics.tiers) : []),
    [fabricMetrics],
  );

  const [nodes, setNodes, onNodesChange] = useNodesState<FlowNode>(initialNodes);
  const [edges, setEdges, onEdgesChange] = useEdgesState<FlowEdge>(initialEdges);

  // Sync nodes/edges when metrics update
  React.useEffect(() => {
    if (fabricMetrics?.tiers) {
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
            Four-tier event cloud topology — HOT → WARM → COOL → COLD
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => setMigrateOpen((open) => !open)}
            data-testid="fabric-open-migration-panel"
          >
            {migrateOpen ? 'Hide Migration Panel' : 'Migrate Tier'}
          </Button>
        </div>
      </div>

      <div className="mx-6 mt-4">
        <AIAssistSuggestion
          headingLabel="Placement recommendation"
          suggestion={placementRecommendation.rationale}
          confidence={placementRecommendation.confidence}
          evidence={placementRecommendation.evidence}
          canApply={false}
          data-testid="fabric-placement-recommendation"
        />
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
          <input
            type="text"
            placeholder="Reason for migration"
            aria-label="Reason for migration"
            value={migrationReason}
            onChange={(event: React.ChangeEvent<HTMLInputElement>) => setMigrationReason(event.target.value)}
            className="border border-gray-300 rounded px-2 py-1 text-sm w-56"
          />
          <GuardedAction
            label="Start governed migration"
            impact={`Collection ${migrateCollection || '(unset)'} will be migrated to ${migrateTargetTier}. This changes storage placement and may affect downstream query latency.`}
            requiresReason
            reasonPrompt="Confirm why this migration is required"
            confirmLabel="Start migration"
            onConfirm={() => {
              if (!migrateCollection || !migrationReason.trim()) {
                return;
              }
              migrateMutation.mutate();
            }}
            isExecuting={migrateMutation.isPending}
          >
            {({ open }) => (
              <Button
                type="button"
                size="sm"
                disabled={!migrateCollection || !migrationReason.trim() || migrateMutation.isPending}
                onClick={open}
              >
                {migrateMutation.isPending ? 'Migrating…' : 'Start Migration'}
              </Button>
            )}
          </GuardedAction>
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
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={() => { setMigrateOpen(false); migrateMutation.reset(); }}
            className="ml-auto"
          >
            Close
          </Button>
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
