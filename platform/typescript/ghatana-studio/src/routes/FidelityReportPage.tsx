/**
 * @fileoverview Fidelity Report display page.
 *
 * Renders a detailed view of a `FidelityReport` produced by the artifact
 * decompiler.  In the Studio workflow, users land here after completing an
 * import/decompile job so they can review loss points and decide which nodes
 * are safe to push into the Builder.
 *
 * The page receives the report via React Router state (passed from
 * ImportDecompilePage via `navigate("/fidelity-report", { state: { report } })`).
 *
 * @doc.type component
 * @doc.purpose Fidelity report review UI
 * @doc.layer studio
 */

import type { ReactElement } from 'react';
import { useLocation, useNavigate } from 'react-router';
import type { FidelityReport, LossPoint } from '@ghatana/artifact-contracts';

// ============================================================================
// Severity colours
// ============================================================================

const SEVERITY_CLASSES: Record<LossPoint['severity'], string> = {
  critical: 'text-red-700 bg-red-50 border-red-200',
  warning: 'text-yellow-700 bg-yellow-50 border-yellow-200',
  info: 'text-blue-700 bg-blue-50 border-blue-200',
};

// ============================================================================
// Component
// ============================================================================

interface FidelityReportPageState {
  report?: FidelityReport;
}

export default function FidelityReportPage(): ReactElement {
  const location = useLocation();
  const navigate = useNavigate();
  const state = (location.state ?? {}) as FidelityReportPageState;
  const report = state.report ?? null;

  if (report === null) {
    return (
      <section className="p-6" aria-labelledby="fidelity-title">
        <h2
          id="fidelity-title"
          className="text-2xl font-semibold text-gray-950 mb-4"
        >
          Fidelity Report
        </h2>
        <p className="text-sm text-gray-500">
          No fidelity report data available. Return to the{' '}
          <button
            type="button"
            onClick={() => navigate('/import')}
            className="underline text-blue-600 hover:text-blue-800"
          >
            Import &amp; Decompile
          </button>{' '}
          page to run a decompile job first.
        </p>
      </section>
    );
  }

  const pct = Math.round(report.score * 100);
  const criticalCount = report.lossPoints.filter((lp: LossPoint) => lp.severity === 'critical').length;
  const warningCount = report.lossPoints.filter((lp: LossPoint) => lp.severity === 'warning').length;
  const infoCount = report.lossPoints.filter((lp: LossPoint) => lp.severity === 'info').length;

  const scoreColorClass =
    report.score >= 0.95
      ? 'text-green-700'
      : report.score >= 0.75
      ? 'text-yellow-700'
      : 'text-red-700';

  return (
    <section className="space-y-6 p-6" aria-labelledby="fidelity-title">
      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <h2
            id="fidelity-title"
            className="text-2xl font-semibold text-gray-950"
          >
            Fidelity Report
          </h2>
          <p className="text-sm text-gray-500">
            Pipeline: <code className="font-mono text-xs">{report.scopeId ?? '—'}</code>
          </p>
        </div>
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="text-sm text-gray-600 underline hover:text-gray-900"
        >
          ← Back
        </button>
      </div>

      {/* Score summary */}
      <div className="rounded-lg border border-gray-200 bg-white p-6">
        <p className="text-4xl font-bold tabular-nums" aria-label={`Overall fidelity score: ${pct} percent`}>
          <span className={scoreColorClass}>{pct}%</span>
        </p>
        <p className="mt-1 text-sm text-gray-500">Overall fidelity score</p>
        <dl className="mt-4 grid grid-cols-3 gap-4 text-sm">
          <div>
            <dt className="text-gray-500">Critical</dt>
            <dd className="font-semibold text-red-700">{criticalCount}</dd>
          </div>
          <div>
            <dt className="text-gray-500">Warning</dt>
            <dd className="font-semibold text-yellow-700">{warningCount}</dd>
          </div>
          <div>
            <dt className="text-gray-500">Info</dt>
            <dd className="font-semibold text-blue-700">{infoCount}</dd>
          </div>
        </dl>
      </div>

      {/* Loss points */}
      {report.lossPoints.length === 0 ? (
        <div className="rounded-lg border border-green-200 bg-green-50 p-4">
          <p className="text-sm font-medium text-green-700">
            No loss points detected — this model has perfect fidelity.
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          <h3 className="text-base font-semibold text-gray-900">
            Loss points ({report.lossPoints.length})
          </h3>
          <ul className="space-y-2">
            {report.lossPoints.map((lp: LossPoint, idx: number) => (
              <li
                key={`${lp.code}-${idx}`}
                className={`rounded-lg border p-4 ${SEVERITY_CLASSES[lp.severity]}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold">{lp.code}</p>
                    <p className="text-sm mt-0.5">{lp.description}</p>
                  </div>
                  <span
                    className="shrink-0 rounded-full px-2 py-0.5 text-xs font-medium uppercase tracking-wide border"
                  >
                    {lp.severity}
                  </span>
                </div>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}
