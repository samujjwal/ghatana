import React, { useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type HealthStatus = 'healthy' | 'degraded' | 'down' | 'unknown';

interface ServiceDependency {
  targetId: string;
  targetName: string;
  type: 'sync' | 'async' | 'database' | 'cache';
  latencyMs?: number;
}

interface Service {
  id: string;
  name: string;
  description: string;
  status: HealthStatus;
  healthScore: number;
  version: string;
  owner: string;
  uptime: string;
  requestsPerSec: number;
  errorRate: number;
  p95LatencyMs: number;
  dependencies: ServiceDependency[];
  tags: string[];
}

interface TopologyData {
  services: Service[];
  lastUpdated: string;
}

type StatusFilter = 'all' | HealthStatus;

// ============================================================================
// API
// ============================================================================

async function fetchTopology(): Promise<TopologyData> {
  const res = await fetch('/api/services/topology', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load service topology');
  return res.json();
}

// ============================================================================
// Helpers
// ============================================================================

const STATUS_CONFIG: Record<HealthStatus, { label: string; dot: string; bg: string; text: string }> = {
  healthy: { label: 'Healthy', dot: 'bg-emerald-400', bg: 'bg-emerald-900/20', text: 'text-emerald-400' },
  degraded: { label: 'Degraded', dot: 'bg-amber-400', bg: 'bg-amber-900/20', text: 'text-amber-400' },
  down: { label: 'Down', dot: 'bg-red-400', bg: 'bg-red-900/20', text: 'text-red-400' },
  unknown: { label: 'Unknown', dot: 'bg-zinc-500', bg: 'bg-zinc-800', text: 'text-zinc-400' },
};

const DEP_TYPE_COLORS: Record<ServiceDependency['type'], string> = {
  sync: 'text-blue-400 bg-blue-900/20',
  async: 'text-purple-400 bg-purple-900/20',
  database: 'text-amber-400 bg-amber-900/20',
  cache: 'text-emerald-400 bg-emerald-900/20',
};

function healthScoreColor(score: number): string {
  if (score >= 90) return 'text-emerald-400';
  if (score >= 70) return 'text-amber-400';
  return 'text-red-400';
}

function healthBarColor(score: number): string {
  if (score >= 90) return 'bg-emerald-500';
  if (score >= 70) return 'bg-amber-500';
  return 'bg-red-500';
}

// ============================================================================
// Component
// ============================================================================

/**
 * ServiceMapPage — Service topology overview.
 *
 * @doc.type component
 * @doc.purpose Service cards with status, dependencies, and health scores
 * @doc.layer product
 */
const ServiceMapPage: React.FC = () => {
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('all');
  const [search, setSearch] = useState('');

  const { data, isLoading, error } = useQuery<TopologyData>({
    queryKey: ['service-topology'],
    queryFn: fetchTopology,
  });

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          Failed to load service map: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center py-24">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  const filtered = data.services.filter((s) => {
    if (statusFilter !== 'all' && s.status !== statusFilter) return false;
    if (search && !s.name.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  });

  const statusCounts = {
    healthy: data.services.filter((s) => s.status === 'healthy').length,
    degraded: data.services.filter((s) => s.status === 'degraded').length,
    down: data.services.filter((s) => s.status === 'down').length,
  };

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-zinc-100">Service Map</h1>
          <p className="text-sm text-zinc-400 mt-1">
            {data.services.length} services &middot; Last updated {data.lastUpdated}
          </p>
        </div>
        <button className="px-4 py-2 bg-blue-600 hover:bg-blue-500 text-white text-sm font-medium rounded-lg transition-colors">
          Register Service
        </button>
      </div>

      {/* Status Summary */}
      <div className="grid grid-cols-3 gap-3">
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex items-center gap-3">
          <span className="w-3 h-3 rounded-full bg-emerald-400" />
          <div>
            <p className="text-xl font-bold text-zinc-100">{statusCounts.healthy}</p>
            <p className="text-xs text-zinc-500">Healthy</p>
          </div>
        </div>
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex items-center gap-3">
          <span className="w-3 h-3 rounded-full bg-amber-400" />
          <div>
            <p className="text-xl font-bold text-zinc-100">{statusCounts.degraded}</p>
            <p className="text-xs text-zinc-500">Degraded</p>
          </div>
        </div>
        <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex items-center gap-3">
          <span className="w-3 h-3 rounded-full bg-red-400" />
          <div>
            <p className="text-xl font-bold text-zinc-100">{statusCounts.down}</p>
            <p className="text-xs text-zinc-500">Down</p>
          </div>
        </div>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <input
          type="text"
          placeholder="Search services..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="flex-1 min-w-[200px] max-w-sm px-3 py-2 bg-zinc-900 border border-zinc-800 rounded-lg text-zinc-100 text-sm placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-blue-500/40 focus:border-blue-500"
        />
        <div className="flex rounded-lg border border-zinc-800 overflow-hidden">
          {(['all', 'healthy', 'degraded', 'down'] as StatusFilter[]).map((s) => (
            <button
              key={s}
              onClick={() => setStatusFilter(s)}
              className={`px-3 py-2 text-xs font-medium capitalize transition-colors ${
                statusFilter === s
                  ? 'bg-blue-600 text-white'
                  : 'bg-zinc-900 text-zinc-400 hover:text-zinc-200'
              }`}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      {/* Service Cards */}
      <div className="grid grid-cols-1 lg:grid-cols-2 xl:grid-cols-3 gap-4">
        {filtered.map((service) => {
          const cfg = STATUS_CONFIG[service.status];
          return (
            <div
              key={service.id}
              className="bg-zinc-900 border border-zinc-800 rounded-xl p-5 hover:border-zinc-700 transition-colors"
            >
              {/* Service Header */}
              <div className="flex items-start justify-between mb-3">
                <div className="flex items-center gap-3">
                  <div className="relative">
                    <div className="w-10 h-10 rounded-lg bg-zinc-800 flex items-center justify-center text-sm font-bold text-zinc-300">
                      {service.name.slice(0, 2).toUpperCase()}
                    </div>
                    <span className={`absolute -bottom-0.5 -right-0.5 w-3 h-3 rounded-full border-2 border-zinc-900 ${cfg.dot}`} />
                  </div>
                  <div>
                    <h3 className="text-sm font-semibold text-zinc-100">{service.name}</h3>
                    <p className="text-xs text-zinc-500">v{service.version} &middot; {service.owner}</p>
                  </div>
                </div>
                <span className={`px-2 py-0.5 rounded text-xs font-medium ${cfg.bg} ${cfg.text}`}>
                  {cfg.label}
                </span>
              </div>

              <p className="text-xs text-zinc-400 mb-4 line-clamp-2">{service.description}</p>

              {/* Health Score Bar */}
              <div className="mb-4">
                <div className="flex items-center justify-between mb-1">
                  <span className="text-xs text-zinc-500">Health Score</span>
                  <span className={`text-sm font-bold ${healthScoreColor(service.healthScore)}`}>
                    {service.healthScore}%
                  </span>
                </div>
                <div className="h-1.5 bg-zinc-800 rounded-full overflow-hidden">
                  <div
                    className={`h-full rounded-full transition-all ${healthBarColor(service.healthScore)}`}
                    style={{ width: `${service.healthScore}%` }}
                  />
                </div>
              </div>

              {/* Metrics */}
              <div className="grid grid-cols-3 gap-3 mb-4">
                <div>
                  <p className="text-xs text-zinc-500">RPS</p>
                  <p className="text-sm font-semibold text-zinc-200">{service.requestsPerSec}</p>
                </div>
                <div>
                  <p className="text-xs text-zinc-500">Error %</p>
                  <p className={`text-sm font-semibold ${service.errorRate > 1 ? 'text-red-400' : 'text-zinc-200'}`}>
                    {service.errorRate}%
                  </p>
                </div>
                <div>
                  <p className="text-xs text-zinc-500">P95 Lat</p>
                  <p className={`text-sm font-semibold ${service.p95LatencyMs > 500 ? 'text-amber-400' : 'text-zinc-200'}`}>
                    {service.p95LatencyMs}ms
                  </p>
                </div>
              </div>

              {/* Dependencies */}
              {service.dependencies.length > 0 && (
                <div className="pt-3 border-t border-zinc-800">
                  <p className="text-xs text-zinc-500 mb-2">Dependencies ({service.dependencies.length})</p>
                  <div className="flex flex-wrap gap-1.5">
                    {service.dependencies.map((dep) => (
                      <span
                        key={dep.targetId}
                        className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-[10px] font-medium ${DEP_TYPE_COLORS[dep.type]}`}
                      >
                        {dep.targetName}
                        {dep.latencyMs !== undefined && (
                          <span className="opacity-70">({dep.latencyMs}ms)</span>
                        )}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              {/* Tags */}
              {service.tags.length > 0 && (
                <div className="flex flex-wrap gap-1 mt-3">
                  {service.tags.map((tag) => (
                    <span key={tag} className="px-1.5 py-0.5 bg-zinc-800 text-zinc-500 text-[10px] rounded">
                      {tag}
                    </span>
                  ))}
                </div>
              )}
            </div>
          );
        })}

        {filtered.length === 0 && (
          <div className="col-span-full py-16 text-center text-zinc-500">
            No services match the current filters
          </div>
        )}
      </div>
    </div>
  );
};

export default ServiceMapPage;
