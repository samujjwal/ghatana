import React from 'react';
import { usePerformanceSummary, useAlerts } from '../hooks/useAnalytics';

export const AccessibilityPage: React.FC = () => {
  const { data: summary, isLoading } = usePerformanceSummary();
  const { data: alerts = [] } = useAlerts();

  const accessibilityAlerts = alerts.filter((alert) => alert.metric === 'accessibility');
  const score = summary?.summary?.accessibilityScore;

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-semibold text-slate-900">Accessibility Monitoring</h1>
        <p className="text-sm text-slate-500">
          Automated audit results and accessibility signals captured in real time.
        </p>
      </header>

      <section className="grid gap-4 md:grid-cols-3">
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Compliance</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {typeof score === 'number' ? `${(score * 100).toFixed(0)}%` : isLoading ? '…' : 'Pending'}
          </p>
          <p className="mt-1 text-xs text-slate-500">Aggregate compliance across latest audits.</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Open Alerts</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">{accessibilityAlerts.length}</p>
          <p className="mt-1 text-xs text-slate-500">Critical and warning level accessibility alerts.</p>
        </div>
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Last Audit</p>
          <p className="mt-2 text-2xl font-semibold text-slate-900">
            {summary?.timestamp ? new Date(summary.timestamp).toLocaleTimeString() : '—'}
          </p>
          <p className="mt-1 text-xs text-slate-500">Timestamp from the most recent metric batch.</p>
        </div>
      </section>

      <section className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <h2 className="text-lg font-semibold text-slate-900">Accessibility Alerts</h2>
        {accessibilityAlerts.length === 0 ? (
          <p className="mt-2 text-sm text-slate-500">No accessibility alerts active.</p>
        ) : (
          <ul className="mt-3 space-y-2">
            {accessibilityAlerts
              .slice()
              .reverse()
              .map((alert) => (
                <li
                  key={alert.id}
                  className="rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800"
                >
                  <div className="font-semibold">{alert.message}</div>
                  <div className="text-xs text-amber-700/80">
                    {new Date(alert.timestamp).toLocaleString()} • Severity {alert.severity.toUpperCase()}
                  </div>
                </li>
              ))}
          </ul>
        )}
      </section>
    </div>
  );
};
