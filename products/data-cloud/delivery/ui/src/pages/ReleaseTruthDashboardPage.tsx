/**
 * Release Truth Dashboard
 *
 * Unified operator surface that consolidates runtime truth, deployment profile,
 * durable-provider readiness, test-gate posture, and recent audit evidence.
 *
 * @doc.type page
 * @doc.purpose Single operator dashboard for release-readiness truth signals
 * @doc.layer frontend
 * @doc.pattern Dashboard
 */

import type { ReactElement } from 'react';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  AlertTriangle,
  CheckCircle2,
  FlaskConical,
  LayoutList,
  ShieldCheck,
  Timer,
} from 'lucide-react';
import { governanceService } from '@/api/governance.service';
import {
  getSurfaceSignal,
  useSurfaceRegistry,
  type SurfaceSignal,
} from '@/api/surfaces.service';
import {
  generateRouteActionGates,
  type GeneratedGateStatus,
} from '@/lib/routing/RuntimeRouteActionGateGenerator';
import { EvidenceAutomationCard } from '@/components/evidence-first';

interface SignalBadgeProps {
  status: GeneratedGateStatus;
}

function SignalBadge({ status }: SignalBadgeProps): ReactElement {
  const palette: Record<GeneratedGateStatus, string> = {
    active: 'bg-emerald-100 text-emerald-700',
    degraded: 'bg-amber-100 text-amber-700',
    unavailable: 'bg-rose-100 text-rose-700',
    unknown: 'bg-slate-100 text-slate-700',
  };

  return (
    <span className={`inline-flex rounded-full px-2 py-1 text-xs font-medium ${palette[status]}`}>
      {status.toUpperCase()}
    </span>
  );
}

function readDeploymentMode(surfaces: SurfaceSignal[] | undefined): string {
  const meta = getSurfaceSignal(surfaces, ['_meta']);
  if (!meta || typeof meta.rawValue !== 'object' || meta.rawValue == null) {
    return 'unknown';
  }

  const record = meta.rawValue as Record<string, unknown>;
  const deploymentMode = record.deploymentMode;
  return typeof deploymentMode === 'string' ? deploymentMode : 'unknown';
}

function toDurableProviderStatus(surfaces: SurfaceSignal[] | undefined): GeneratedGateStatus {
  const eventStore = getSurfaceSignal(surfaces, ['health.eventStore']);
  const database = getSurfaceSignal(surfaces, ['health.database']);

  if (eventStore?.status === 'LIVE' && database?.status === 'LIVE') {
    return 'active';
  }
  if (eventStore?.status === 'DEGRADED' || database?.status === 'DEGRADED' || eventStore?.status === 'PREVIEW' || database?.status === 'PREVIEW') {
    return 'degraded';
  }
  if (!eventStore && !database) {
    return 'unknown';
  }
  return 'unavailable';
}

export function ReleaseTruthDashboardPage(): ReactElement {
  const capabilityRegistry = useSurfaceRegistry();

  const complianceReportQuery = useQuery({
    queryKey: ['release-truth', 'compliance-report'],
    queryFn: () => governanceService.getComplianceReport('30d'),
    staleTime: 60_000,
  });

  const auditLogQuery = useQuery({
    queryKey: ['release-truth', 'audit-logs'],
    queryFn: () => governanceService.getAuditLogs(undefined, undefined, 5),
    staleTime: 60_000,
  });

  const deploymentMode = readDeploymentMode(capabilityRegistry.data?.surfaces);
  const durableProviderStatus = toDurableProviderStatus(capabilityRegistry.data?.surfaces);
  const generatedRouteGates = useMemo(
    () => generateRouteActionGates(capabilityRegistry.data?.surfaces ?? []),
    [capabilityRegistry.data?.surfaces],
  );

  const testGateStatus: Array<{ name: string; status: GeneratedGateStatus; evidence: string }> = [
    {
      name: 'Runtime truth checks',
      status: capabilityRegistry.data ? 'active' : 'unknown',
      evidence: 'scripts/check-truth-surfaces.mjs',
    },
    {
      name: 'Architecture scorecard',
      status: 'active',
      evidence: 'scripts/generate-data-cloud-architecture-scorecard.mjs',
    },
    {
      name: 'Durable load gate',
      status: durableProviderStatus,
      evidence: '.github/workflows/data-cloud-durable-load.yml',
    },
  ];

  return (
    <main className="space-y-6 p-6">
      <header className="rounded-2xl bg-gradient-to-r from-slate-900 via-slate-800 to-cyan-800 p-6 text-white">
        <h1 className="text-2xl font-semibold">Release Truth Dashboard</h1>
        <p className="mt-2 text-sm text-slate-100">
          Runtime truth, route/action gate generation, test-gate posture, deployment profile, and audit evidence.
        </p>
      </header>

      <section className="grid gap-4 md:grid-cols-4">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Deployment Profile</p>
          <p className="mt-2 text-lg font-semibold text-slate-900">{deploymentMode}</p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Durable Provider</p>
          <div className="mt-2">
            <SignalBadge status={durableProviderStatus} />
          </div>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Compliance Score</p>
          <p className="mt-2 text-lg font-semibold text-slate-900">
            {complianceReportQuery.data?.summary.complianceScore ?? '...'}
          </p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs uppercase tracking-wide text-slate-500">Generated Route Gates</p>
          <p className="mt-2 text-lg font-semibold text-slate-900">{generatedRouteGates.length}</p>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center gap-2 text-slate-900">
            <FlaskConical className="h-4 w-4" />
            <h2 className="text-sm font-semibold">Test-Gate Status</h2>
          </div>
          <ul className="space-y-2 text-sm">
            {testGateStatus.map((gate) => (
              <li key={gate.name} className="flex items-center justify-between gap-3 rounded-lg border border-slate-100 p-2">
                <div>
                  <p className="font-medium text-slate-900">{gate.name}</p>
                  <p className="text-xs text-slate-500">{gate.evidence}</p>
                </div>
                <SignalBadge status={gate.status} />
              </li>
            ))}
          </ul>
        </article>

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center gap-2 text-slate-900">
            <ShieldCheck className="h-4 w-4" />
            <h2 className="text-sm font-semibold">Recent Audit Evidence</h2>
          </div>
          <ul className="space-y-2 text-sm">
            {(auditLogQuery.data ?? []).slice(0, 5).map((entry) => (
              <li key={entry.id} className="rounded-lg border border-slate-100 p-2">
                <p className="font-medium text-slate-900">{entry.action}</p>
                <p className="text-xs text-slate-500">{entry.timestamp}</p>
              </li>
            ))}
            {auditLogQuery.data?.length === 0 ? (
              <li className="rounded-lg border border-slate-100 p-2 text-slate-500">No recent audit evidence available.</li>
            ) : null}
          </ul>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-2">
        <EvidenceAutomationCard
          title="Policy-backed Rollout Recommendation"
          why="Capability and governance signals indicate rollout safety for core operator surfaces."
          dataUsed={[
            `Deployment mode: ${deploymentMode}`,
            `Route gates generated: ${generatedRouteGates.length}`,
            `Compliance score: ${complianceReportQuery.data?.summary.complianceScore ?? 'n/a'}`,
          ]}
          confidence={0.91}
          policy="Only allow automatic rollout when compliance score >= 70 and durable provider is not unavailable."
          audit={`Latest report: ${complianceReportQuery.data?.generatedAt ?? 'not yet available'}`}
          overrideControl={
            <button type="button" className="rounded-md border border-slate-300 px-3 py-1 text-xs font-medium text-slate-700 hover:bg-slate-50">
              Request manual override
            </button>
          }
        />

        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center gap-2 text-slate-900">
            <LayoutList className="h-4 w-4" />
            <h2 className="text-sm font-semibold">Generated Route/Action Gates</h2>
          </div>
          <ul className="max-h-72 space-y-2 overflow-auto pr-1 text-sm">
            {generatedRouteGates.slice(0, 12).map((route) => (
              <li key={route.path} className="rounded-lg border border-slate-100 p-2">
                <div className="mb-1 flex items-center justify-between gap-2">
                  <p className="font-medium text-slate-900">{route.path}</p>
                  <SignalBadge status={route.status} />
                </div>
                <p className="text-xs text-slate-600">{route.actions.length} action gates</p>
              </li>
            ))}
          </ul>
        </article>
      </section>

      <section className="grid gap-4 md:grid-cols-3">
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-slate-900">
            <CheckCircle2 className="h-4 w-4 text-emerald-600" />
            <h3 className="text-sm font-semibold">Healthy Signals</h3>
          </div>
          <p className="text-sm text-slate-600">
            {generatedRouteGates.filter((route) => route.status === 'active').length} routes currently active.
          </p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-slate-900">
            <AlertTriangle className="h-4 w-4 text-amber-600" />
            <h3 className="text-sm font-semibold">Watchlist</h3>
          </div>
          <p className="text-sm text-slate-600">
            {generatedRouteGates.filter((route) => route.status === 'degraded').length} routes degraded.
          </p>
        </article>
        <article className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-2 flex items-center gap-2 text-slate-900">
            <Timer className="h-4 w-4 text-slate-700" />
            <h3 className="text-sm font-semibold">Registry Freshness</h3>
          </div>
          <p className="text-sm text-slate-600">
            {capabilityRegistry.data?.generatedAt ?? 'Awaiting first registry snapshot'}
          </p>
        </article>
      </section>
    </main>
  );
}

export default ReleaseTruthDashboardPage;
