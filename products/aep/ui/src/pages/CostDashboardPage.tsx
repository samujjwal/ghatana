/**
 * CostDashboardPage — operating spend visibility for AEP.
 *
 * @doc.type page
 * @doc.purpose Cost summary and spend concentration view for operators
 * @doc.layer frontend
 */
import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAtomValue } from 'jotai';
import {
  Bar,
  BarChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { getCostSummary } from '@/api/aep.api';
import { tenantIdAtom } from '@/stores/tenant.store';

function Currency({ value }: { value: number }) {
  return <>{new Intl.NumberFormat(undefined, { style: 'currency', currency: 'USD' }).format(value)}</>;
}

function StatCard({ title, value, detail }: { title: string; value: React.ReactNode; detail: string }) {
  return (
    <div className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
      <p className="text-xs font-semibold uppercase tracking-wide text-gray-400">{title}</p>
      <div className="mt-3 text-2xl font-semibold text-gray-900 dark:text-gray-100">{value}</div>
      <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{detail}</p>
    </div>
  );
}

export function CostDashboardPage() {
  const tenantId = useAtomValue(tenantIdAtom);
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cost-summary', tenantId],
    queryFn: () => getCostSummary(tenantId),
    staleTime: 30_000,
  });

  if (isLoading) {
    return <div className="p-6 text-sm text-gray-500 dark:text-gray-400">Loading cost telemetry…</div>;
  }

  if (isError || !data) {
    return (
      <div className="p-6 text-sm text-red-600 dark:text-red-400">
        Failed to load cost summary: {error instanceof Error ? error.message : 'Unknown error'}
      </div>
    );
  }

  return (
    <div className="flex h-full flex-col overflow-y-auto bg-gray-50 p-6 dark:bg-gray-950">
      <div className="mb-6 flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
        <div>
          <h1 className="text-xl font-semibold text-gray-900 dark:text-gray-100">Cost Dashboard</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Tenant operating spend across pipelines and agents, with budget alerts and telemetry provenance.
          </p>
        </div>
        <div className="rounded-full bg-white px-4 py-2 text-xs font-medium text-gray-500 shadow-sm dark:bg-gray-900 dark:text-gray-300">
          Source: {data.dataSource} • Allocation: {data.allocationModel}
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatCard
          title="Observed Spend"
          value={<Currency value={data.totalCostUsd} />}
          detail="Observed across the current dashboard window"
        />
        <StatCard
          title="Projected Monthly"
          value={<Currency value={data.projectedMonthlyCostUsd} />}
          detail="Projection from the current observation window"
        />
        <StatCard
          title="Average Per Run"
          value={<Currency value={data.averageCostPerRunUsd} />}
          detail="Average cost allocation per completed or active run"
        />
        <StatCard
          title="Active Alerts"
          value={data.alerts.length}
          detail="Budget, concentration, and telemetry alerts currently raised"
        />
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,1.25fr)_minmax(340px,0.9fr)]">
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Pipeline Cost Concentration</h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                Highest-cost pipelines observed in the current telemetry window.
              </p>
            </div>
          </div>
          <div className="mt-5 h-72">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={data.perPipeline} margin={{ top: 8, right: 16, left: 0, bottom: 16 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#E5E7EB" />
                <XAxis dataKey="name" tick={{ fontSize: 12 }} angle={-15} textAnchor="end" height={50} />
                <YAxis tickFormatter={(value: number) => `$${value.toFixed(2)}`} tick={{ fontSize: 12 }} />
                <Tooltip />
                <Bar dataKey="costUsd" fill="#2563EB" radius={[8, 8, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </section>

        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Alerts</h2>
          <div className="mt-4 space-y-3">
            {data.alerts.length === 0 ? (
              <p className="text-sm text-gray-500 dark:text-gray-400">No cost alerts are active.</p>
            ) : (
              data.alerts.map((alert) => (
                <div
                  key={alert.id}
                  className={[
                    'rounded-xl border p-4',
                    alert.severity === 'critical'
                      ? 'border-red-200 bg-red-50 dark:border-red-900 dark:bg-red-950/30'
                      : alert.severity === 'warning'
                        ? 'border-amber-200 bg-amber-50 dark:border-amber-900 dark:bg-amber-950/30'
                        : 'border-blue-200 bg-blue-50 dark:border-blue-900 dark:bg-blue-950/30',
                  ].join(' ')}
                >
                  <div className="flex items-center justify-between gap-3">
                    <p className="text-sm font-semibold text-gray-900 dark:text-gray-100">{alert.title}</p>
                    <span className="text-[11px] uppercase tracking-wide text-gray-500 dark:text-gray-400">
                      {alert.severity}
                    </span>
                  </div>
                  <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">{alert.description}</p>
                  <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                    Current {alert.currentValue.toFixed(2)} • Threshold {alert.thresholdValue.toFixed(2)}
                  </p>
                </div>
              ))
            )}
          </div>
        </section>
      </div>

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Per-Pipeline Breakdown</h2>
            <span className="text-xs text-gray-500 dark:text-gray-400">{data.perPipeline.length} pipelines</span>
          </div>
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-800">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-gray-400">
                  <th className="pb-3 pr-4">Pipeline</th>
                  <th className="pb-3 pr-4">Cost</th>
                  <th className="pb-3 pr-4">Share</th>
                  <th className="pb-3">Runs</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-900">
                {data.perPipeline.map((item) => (
                  <tr key={item.id}>
                    <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">{item.name}</td>
                    <td className="py-3 pr-4 text-gray-600 dark:text-gray-300"><Currency value={item.costUsd} /></td>
                    <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">{item.sharePercent.toFixed(1)}%</td>
                    <td className="py-3 text-gray-600 dark:text-gray-300">{item.runCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>

        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between gap-3">
            <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Per-Agent Breakdown</h2>
            <span className="text-xs text-gray-500 dark:text-gray-400">{data.perAgent.length} agents</span>
          </div>
          <div className="mt-4 overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-800">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-gray-400">
                  <th className="pb-3 pr-4">Agent</th>
                  <th className="pb-3 pr-4">Cost</th>
                  <th className="pb-3 pr-4">Share</th>
                  <th className="pb-3">Runs</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-900">
                {data.perAgent.length === 0 ? (
                  <tr>
                    <td colSpan={4} className="py-6 text-center text-sm text-gray-500 dark:text-gray-400">
                      No per-agent breakdown is available for the current tenant yet.
                    </td>
                  </tr>
                ) : (
                  data.perAgent.map((item) => (
                    <tr key={item.id}>
                      <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">{item.name}</td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300"><Currency value={item.costUsd} /></td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">{item.sharePercent.toFixed(1)}%</td>
                      <td className="py-3 text-gray-600 dark:text-gray-300">{item.runCount}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}