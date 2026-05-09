/**
 * Runtime Truth Dashboard (DC-P3-002)
 *
 * Visual dashboard showing Data Cloud plane health with surface/dependency drilldown.
 * Groups runtime surfaces by plane: Data Plane, Action Plane, Operations Plane.
 *
 * @doc.type page
 * @doc.purpose Plane/surface/dependency drilldown for Data Cloud runtime truth
 * @doc.layer frontend
 * @doc.pattern Dashboard
 */

import type { ReactElement } from 'react';
import { useState } from 'react';
import { ChevronDown, ChevronRight, CheckCircle2, AlertTriangle, MinusCircle, HelpCircle } from 'lucide-react';
import { useSurfaceRegistry, type SurfaceSignal, type SurfaceStatus } from '@/api/surfaces.service';
import { cn } from '@/lib/theme';

// ── Plane taxonomy ────────────────────────────────────────────────────────────

interface Plane {
  id: string;
  label: string;
  description: string;
  /** Surface key prefixes that belong to this plane. */
  keyPrefixes: string[];
}

const PLANES: Plane[] = [
  {
    id: 'data',
    label: 'Data Plane',
    description: 'Entity storage, schema management, collection CRUD, lineage, and data quality.',
    keyPrefixes: ['entity', 'collection', 'schema', 'lineage', 'data', 'clickhouse', 'postgres', 'store', 'storage'],
  },
  {
    id: 'action',
    label: 'Action Plane',
    description: 'Event log, AEP integration, agent execution, workflow orchestration, and AI features.',
    keyPrefixes: ['event', 'aep', 'agent', 'workflow', 'ai', 'llm', 'embedding', 'memory', 'action'],
  },
  {
    id: 'operations',
    label: 'Operations Plane',
    description: 'Observability, alerting, audit, policy governance, plugin management, and release truth.',
    keyPrefixes: ['alert', 'audit', 'metric', 'trace', 'log', 'plugin', 'policy', 'release', 'operations', 'governance', 'security', 'auth', 'profile', '_meta'],
  },
];

// ── Status helpers ─────────────────────────────────────────────────────────────

interface StatusConfig {
  icon: ReactElement;
  badge: string;
  row: string;
}

function statusConfig(status: SurfaceStatus): StatusConfig {
  switch (status) {
    case 'LIVE':
      return {
        icon: <CheckCircle2 className="h-4 w-4 text-emerald-500" />,
        badge: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300',
        row: 'hover:bg-emerald-50 dark:hover:bg-emerald-900/10',
      };
    case 'DEGRADED':
    case 'PREVIEW':
      return {
        icon: <AlertTriangle className="h-4 w-4 text-amber-500" />,
        badge: 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-300',
        row: 'hover:bg-amber-50 dark:hover:bg-amber-900/10',
      };
    case 'UNAVAILABLE':
    case 'DISABLED':
    case 'MISCONFIGURED':
      return {
        icon: <MinusCircle className="h-4 w-4 text-rose-500" />,
        badge: 'bg-rose-100 text-rose-800 dark:bg-rose-900/30 dark:text-rose-300',
        row: 'hover:bg-rose-50 dark:hover:bg-rose-900/10',
      };
    default:
      return {
        icon: <HelpCircle className="h-4 w-4 text-slate-400" />,
        badge: 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300',
        row: 'hover:bg-slate-50 dark:hover:bg-slate-800/30',
      };
  }
}

function aggregatePlaneStatus(signals: SurfaceSignal[]): SurfaceStatus {
  if (signals.some((s) => s.status === 'UNAVAILABLE' || s.status === 'DISABLED' || s.status === 'MISCONFIGURED')) return 'UNAVAILABLE';
  if (signals.some((s) => s.status === 'DEGRADED' || s.status === 'PREVIEW')) return 'DEGRADED';
  if (signals.every((s) => s.status === 'LIVE')) return 'LIVE';
  return 'UNAVAILABLE';
}

// ── Plane row ─────────────────────────────────────────────────────────────────

interface PlaneRowProps {
  plane: Plane;
  signals: SurfaceSignal[];
}

function PlaneRow({ plane, signals }: PlaneRowProps): ReactElement {
  const [open, setOpen] = useState(false);
  const status = aggregatePlaneStatus(signals);
  const config = statusConfig(status);

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-xl overflow-hidden mb-3">
      {/* Plane header */}
      <button
        type="button"
        onClick={() => { setOpen((o) => !o); }}
        aria-expanded={open}
        className={cn(
          'w-full flex items-center gap-3 px-4 py-3 text-left bg-white dark:bg-gray-800',
          'focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-500',
        )}
      >
        {open
          ? <ChevronDown className="h-4 w-4 text-gray-400 flex-shrink-0" />
          : <ChevronRight className="h-4 w-4 text-gray-400 flex-shrink-0" />}
        <span className="flex-1 min-w-0">
          <span className="block text-sm font-semibold text-gray-900 dark:text-white">{plane.label}</span>
          <span className="block text-xs text-gray-500 dark:text-gray-400 mt-0.5 truncate">{plane.description}</span>
        </span>
        <span className="flex items-center gap-2 ml-2 flex-shrink-0">
          <span className="text-xs text-gray-400">{signals.length} surfaces</span>
          <span className={cn('inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium', config.badge)}>
            {config.icon}
            {status.toUpperCase()}
          </span>
        </span>
      </button>

      {/* Drilldown table */}
      {open && signals.length > 0 && (
        <div className="border-t border-gray-100 dark:border-gray-700">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 dark:bg-gray-900/40">
                <th className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Surface / Key</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Label</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Status</th>
                <th className="text-left px-4 py-2 text-xs font-medium text-gray-500 uppercase tracking-wide">Summary</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
              {signals.map((signal) => {
                const sc = statusConfig(signal.status);
                return (
                  <tr key={signal.key} className={cn('text-gray-700 dark:text-gray-300 transition-colors', sc.row)}>
                    <td className="px-4 py-2 font-mono text-xs text-gray-500 dark:text-gray-400 whitespace-nowrap">{signal.key}</td>
                    <td className="px-4 py-2 text-xs font-medium">{signal.label}</td>
                    <td className="px-4 py-2">
                      <span className={cn('inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium', sc.badge)}>
                        {sc.icon}
                        {signal.status}
                      </span>
                    </td>
                    <td className="px-4 py-2 text-xs text-gray-500 dark:text-gray-400 max-w-xs truncate" title={signal.detail ?? signal.summary}>
                      {signal.summary}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {open && signals.length === 0 && (
        <div className="border-t border-gray-100 dark:border-gray-700 px-4 py-3 text-xs text-gray-400 italic">
          No surface signals registered for this plane.
        </div>
      )}
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export function RuntimeTruthPage(): ReactElement {
  const { data, isLoading, isError, error } = useSurfaceRegistry();

  const allSignals = data?.surfaces ?? [];

  // Partition signals into planes; unmatched go to 'operations' (catch-all)
  const planeSignals = PLANES.map((plane) => {
    const signals = allSignals.filter((s) =>
      plane.keyPrefixes.some((prefix) =>
        s.key.toLowerCase().startsWith(prefix.toLowerCase()),
      ),
    );
    return { plane, signals };
  });

  // Unclassified signals — assign to operations as catch-all
  const classified = new Set(planeSignals.flatMap(({ signals }) => signals.map((s) => s.key)));
  const unclassified = allSignals.filter((s) => !classified.has(s.key));
  if (unclassified.length > 0) {
    const opsEntry = planeSignals.find(({ plane }) => plane.id === 'operations');
    if (opsEntry) {
      opsEntry.signals = [...opsEntry.signals, ...unclassified];
    }
  }

  const overallStatus = aggregatePlaneStatus(allSignals);
  const overallConfig = statusConfig(overallStatus);

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-950 p-6">
      <div className="max-w-5xl mx-auto">
        {/* Header */}
        <header className="mb-6">
          <div className="flex items-center justify-between">
            <div>
              <h1 className="text-xl font-bold text-gray-900 dark:text-white">Runtime Truth</h1>
              <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                Live plane and surface status for this Data Cloud instance. Expand a plane for surface drilldown.
              </p>
            </div>
            {!isLoading && !isError && (
              <span className={cn('inline-flex items-center gap-2 rounded-full px-3 py-1.5 text-sm font-medium', overallConfig.badge)}>
                {overallConfig.icon}
                Overall: {overallStatus.toUpperCase()}
              </span>
            )}
          </div>
          {data && (
            <p className="text-xs text-gray-400 mt-2">
              Snapshot at {new Date(data.generatedAt).toLocaleString()} · Tenant: {data.tenantId} · Request: {data.requestId}
            </p>
          )}
        </header>

        {/* Loading */}
        {isLoading && (
          <div className="text-center py-16 text-gray-400 text-sm" role="status" aria-live="polite">
            Loading runtime surface registry…
          </div>
        )}

        {/* Error */}
        {isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 dark:bg-rose-900/20 dark:border-rose-800 px-4 py-3" role="alert">
            <p className="text-sm font-medium text-rose-800 dark:text-rose-300">Failed to load runtime surfaces</p>
            <p className="text-xs text-rose-600 dark:text-rose-400 mt-1">
              {error instanceof Error ? error.message : 'Unknown error — check server connectivity.'}
            </p>
          </div>
        )}

        {/* Plane rows */}
        {!isLoading && !isError && (
          <div>
            {planeSignals.map(({ plane, signals }) => (
              <PlaneRow key={plane.id} plane={plane} signals={signals} />
            ))}
            <p className="text-xs text-gray-400 mt-4 text-center">
              Total surfaces: {allSignals.length} across {PLANES.length} planes
            </p>
          </div>
        )}
      </div>
    </main>
  );
}
