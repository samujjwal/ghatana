/**
 * CostDashboardPage — cost analysis and optimization dashboard.
 *
 * @doc.type page
 * @doc.purpose Cost monitoring and optimization
 * @doc.layer frontend
 */
/* eslint-disable ghatana/prefer-design-system-primitives */
import React, { useState } from 'react';
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
import { Link } from 'react-router';
import {
  getEditPipelineUrl,
  getOperateUrl,
  getPipelineListUrl,
  getRunDetailUrl,
} from '@/lib/routes';
import { EmptyState } from '@/components/core/EmptyState';
import { ErrorState } from '@/components/core/ErrorState';

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
  const [isUpdatingBudget, startBudgetTransition] = React.useTransition();
  const [draftBudget, setDraftBudget] = React.useState({
    dailyBudgetUsd: '25',
    monthlyBudgetUsd: '750',
  });
  const [appliedBudget, setAppliedBudget] = React.useState({
    dailyBudgetUsd: 25,
    monthlyBudgetUsd: 750,
  });
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['cost-summary', tenantId, appliedBudget.dailyBudgetUsd, appliedBudget.monthlyBudgetUsd],
    queryFn: () => getCostSummary(tenantId, appliedBudget),
    staleTime: 30_000,
  });

  if (isLoading) {
    return <EmptyState title="Loading cost telemetry…" description="Fetching spend data for the current tenant." />;
  }

  if (isError || !data) {
    return (
      <ErrorState
        title="Failed to load cost summary"
        message={error instanceof Error ? error.message : 'Unknown error'}
        onRetry={() => window.location.reload()}
      />
    );
  }

  const applyBudgetThresholds = () => {
    const nextDaily = Number.parseFloat(draftBudget.dailyBudgetUsd);
    const nextMonthly = Number.parseFloat(draftBudget.monthlyBudgetUsd);
    startBudgetTransition(() => {
      setAppliedBudget({
        dailyBudgetUsd: Number.isFinite(nextDaily) && nextDaily > 0 ? nextDaily : 25,
        monthlyBudgetUsd: Number.isFinite(nextMonthly) && nextMonthly > 0 ? nextMonthly : 750,
      });
    });
  };

  const dailyBudget = data.budget.daily;
  const monthlyBudget = data.budget.monthly;

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

      {data.estimated && (
        <div
          role="status"
          aria-live="polite"
          className="mb-4 flex items-start gap-3 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950 dark:text-amber-200"
        >
          <span aria-hidden="true" className="mt-0.5 shrink-0 text-base">⚠️</span>
          <span>
            <strong>Estimated costs</strong> — Billing telemetry is not yet available for this tenant.
            Figures are approximated using a synthetic pricing formula and may differ from actual charges.
          </span>
        </div>
      )}

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

      <div className="mt-6 grid gap-6 xl:grid-cols-[minmax(0,0.95fr)_minmax(0,1.05fr)]">
        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex flex-col gap-2 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Budget guardrails</h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                Tune daily and monthly budget thresholds, then compare observed and projected usage against them.
              </p>
            </div>
            <div className="text-xs text-gray-500 dark:text-gray-400">
              Budget state refreshes from the live cost summary endpoint.
            </div>
          </div>

          <div className="mt-5 grid gap-4 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)_auto]">
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Daily budget (USD)</span>
              <input
                value={draftBudget.dailyBudgetUsd}
                onChange={(event) => setDraftBudget((current) => ({ ...current, dailyBudgetUsd: event.target.value }))}
                inputMode="decimal"
                className="mt-2 h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-sm text-gray-900 shadow-sm outline-none transition focus:border-indigo-400 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
              />
            </label>
            <label className="block">
              <span className="text-sm font-medium text-gray-700 dark:text-gray-200">Monthly budget (USD)</span>
              <input
                value={draftBudget.monthlyBudgetUsd}
                onChange={(event) => setDraftBudget((current) => ({ ...current, monthlyBudgetUsd: event.target.value }))}
                inputMode="decimal"
                className="mt-2 h-11 w-full rounded-xl border border-gray-200 bg-white px-3 text-sm text-gray-900 shadow-sm outline-none transition focus:border-indigo-400 dark:border-gray-700 dark:bg-gray-950 dark:text-gray-100"
              />
            </label>
            <button
              type="button"
              onClick={applyBudgetThresholds}
              disabled={isUpdatingBudget}
              className="h-11 self-end rounded-xl bg-indigo-600 px-4 text-sm font-medium text-white transition hover:bg-indigo-500 disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isUpdatingBudget ? 'Refreshing…' : 'Apply thresholds'}
            </button>
          </div>

          <div className="mt-5 grid gap-4 md:grid-cols-2">
            <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-950">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Daily budget</p>
                  <p className="mt-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
                    <Currency value={dailyBudget.budgetUsd} />
                  </p>
                </div>
                <span className="rounded-full bg-white px-3 py-1 text-[11px] font-medium uppercase tracking-wide text-gray-600 shadow-sm dark:bg-gray-900 dark:text-gray-300">
                  {dailyBudget.status}
                </span>
              </div>
              <p className="mt-3 text-sm text-gray-600 dark:text-gray-300">
                Observed <Currency value={dailyBudget.observedUsd} /> • Remaining <Currency value={dailyBudget.remainingUsd} />
              </p>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{dailyBudget.usagePercent.toFixed(1)}% of threshold used</p>
            </div>

            <div className="rounded-2xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-800 dark:bg-gray-950">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">Monthly projection</p>
                  <p className="mt-2 text-lg font-semibold text-gray-900 dark:text-gray-100">
                    <Currency value={monthlyBudget.budgetUsd} />
                  </p>
                </div>
                <span className="rounded-full bg-white px-3 py-1 text-[11px] font-medium uppercase tracking-wide text-gray-600 shadow-sm dark:bg-gray-900 dark:text-gray-300">
                  {monthlyBudget.status}
                </span>
              </div>
              <p className="mt-3 text-sm text-gray-600 dark:text-gray-300">
                Projection <Currency value={monthlyBudget.observedUsd} /> • Remaining <Currency value={monthlyBudget.remainingUsd} />
              </p>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{monthlyBudget.usagePercent.toFixed(1)}% of threshold used</p>
            </div>
          </div>
        </section>

        <section className="rounded-2xl border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-800 dark:bg-gray-900">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h2 className="text-base font-semibold text-gray-900 dark:text-gray-100">Model Spend Breakdown</h2>
              <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
                Model-level cost concentration is shown when telemetry publishes `cost.model.usd` or model tags.
              </p>
            </div>
            <span className="text-xs text-gray-500 dark:text-gray-400">{data.perModel.length} models</span>
          </div>
          <div className="mt-4 overflow-x-auto">
            {data.perModel.length === 0 ? (
              <EmptyState
                title="No per-model breakdown"
                description="Model cost shares will appear once the runtime publishes model-tagged telemetry."
              />
            ) : (
              <table className="min-w-full divide-y divide-gray-200 text-sm dark:divide-gray-800">
                <thead>
                  <tr className="text-left text-xs uppercase tracking-wide text-gray-400">
                    <th className="pb-3 pr-4">Model</th>
                    <th className="pb-3 pr-4">Cost</th>
                    <th className="pb-3 pr-4">Share</th>
                    <th className="pb-3">Runs</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-900">
                  {data.perModel.map((item) => (
                    <tr key={item.id}>
                      <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">{item.name}</td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300"><Currency value={item.costUsd} /></td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">{item.sharePercent.toFixed(1)}%</td>
                      <td className="py-3 text-gray-600 dark:text-gray-300">{item.runCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
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
              <EmptyState title="No cost alerts" description="Budget thresholds are within limits." />
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
                  {/* Lifecycle badges */}
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {alert.acknowledgedAt && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300 font-medium">
                        Acknowledged
                      </span>
                    )}
                    {alert.resolvedAt && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-300 font-medium">
                        Resolved
                      </span>
                    )}
                    {alert.snoozedUntil && new Date(alert.snoozedUntil) > new Date() && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300 font-medium">
                        Snoozed until {new Date(alert.snoozedUntil).toLocaleDateString()}
                      </span>
                    )}
                    {alert.owner && (
                      <span className="text-[10px] px-1.5 py-0.5 rounded bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300 font-medium">
                        Owner: {alert.owner}
                      </span>
                    )}
                  </div>

                  <div className="mt-3 flex gap-2 flex-wrap">
                    {alert.relatedPipelineId ? (
                      <Link
                        to={getEditPipelineUrl(alert.relatedPipelineId)}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Open pipeline →
                      </Link>
                    ) : (
                      <Link
                        to={getPipelineListUrl()}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Review pipelines →
                      </Link>
                    )}
                    {alert.relatedRunId ? (
                      <Link
                        to={getRunDetailUrl(alert.relatedRunId)}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        View run →
                      </Link>
                    ) : (
                      <Link
                        to={getOperateUrl()}
                        className="text-xs text-indigo-600 dark:text-indigo-400 hover:underline font-medium"
                      >
                        Check runs →
                      </Link>
                    )}
                  </div>
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
                    <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">
                      <Link to={getEditPipelineUrl(item.id)} className="hover:text-indigo-600 dark:hover:text-indigo-400 hover:underline transition-colors">
                        {item.name}
                      </Link>
                    </td>
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
            {data.perAgent.length === 0 ? (
              <EmptyState
                title="No per-agent breakdown"
                description="Agent cost data will appear once agents are assigned to pipeline runs."
              />
            ) : (
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
                  {data.perAgent.map((item) => (
                    <tr key={item.id}>
                      <td className="py-3 pr-4 font-medium text-gray-900 dark:text-gray-100">
                        <Link to={`/catalog/agents/${encodeURIComponent(item.id)}`} className="hover:text-indigo-600 dark:hover:text-indigo-400 hover:underline transition-colors">
                          {item.name}
                        </Link>
                      </td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300"><Currency value={item.costUsd} /></td>
                      <td className="py-3 pr-4 text-gray-600 dark:text-gray-300">{item.sharePercent.toFixed(1)}%</td>
                      <td className="py-3 text-gray-600 dark:text-gray-300">{item.runCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
