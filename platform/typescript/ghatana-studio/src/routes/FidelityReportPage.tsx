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
import { useState } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';
import type { FidelityReport, LossPoint } from '@ghatana/artifact-contracts';
import {
  artifactFidelityReportAtom,
  artifactRoundTripDiffReportAtom,
} from '../state/artifactWorkflowStore.js';

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
  // Prefer workflow store report (from decompile job); fall back to router state (legacy)
  const storeReport = useAtomValue(artifactFidelityReportAtom);
  const diffReport = useAtomValue(artifactRoundTripDiffReportAtom);
  const report = storeReport ?? state.report ?? null;
  const [triageByDiffId, setTriageByDiffId] = useState<Record<string, 'pending' | 'accepted' | 'escalated'>>({});

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

      {diffReport !== null && (
        <div className="rounded-lg border border-gray-200 bg-white p-4">
          <h3 className="text-base font-semibold text-gray-900">
            Round-trip diff
          </h3>
          <dl className="mt-3 grid gap-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-gray-500">Files</dt>
              <dd className="font-semibold text-gray-900">{diffReport.diffs.length}</dd>
            </div>
            <div>
              <dt className="text-gray-500">Semantic matches</dt>
              <dd className="font-semibold text-gray-900">
                {diffReport.diffs.filter((diff) => diff.semanticallyEquivalent).length}
              </dd>
            </div>
            <div>
              <dt className="text-gray-500">Lossless</dt>
              <dd className="font-semibold text-gray-900">{diffReport.isLossless ? 'Yes' : 'No'}</dd>
            </div>
          </dl>
          {diffReport.diffs.length > 0 && (
            <ul className="mt-3 space-y-2">
              {diffReport.diffs.map((diff) => {
                const triageStatus = triageByDiffId[diff.diffId] ?? 'pending';
                return (
                  <li key={diff.diffId} className="rounded-md border border-gray-200 bg-gray-50 p-3 text-sm">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <span className="font-mono text-xs text-gray-700">{diff.generatedPath}</span>
                      <span className={diff.semanticallyEquivalent ? 'text-green-700' : 'text-red-700'}>
                        {diff.semanticallyEquivalent ? 'Semantic match' : 'Review required'}
                      </span>
                    </div>
                    <p className="mt-1 text-xs text-gray-500">
                      +{diff.addedLines} / -{diff.removedLines} / ={diff.unchangedLines}
                    </p>
                    <div className="mt-2 flex flex-wrap items-center gap-2">
                      <button
                        type="button"
                        onClick={() => {
                          setTriageByDiffId((prev) => ({ ...prev, [diff.diffId]: 'accepted' }));
                        }}
                        className="rounded border border-green-200 bg-green-50 px-2 py-1 text-xs font-medium text-green-700"
                      >
                        Accept diff
                      </button>
                      <button
                        type="button"
                        onClick={() => {
                          setTriageByDiffId((prev) => ({ ...prev, [diff.diffId]: 'escalated' }));
                        }}
                        className="rounded border border-red-200 bg-red-50 px-2 py-1 text-xs font-medium text-red-700"
                      >
                        Escalate residual
                      </button>
                      <span className="text-xs text-gray-500">
                        Triage: {triageStatus}
                      </span>
                    </div>
                    {diff.hunks.length > 0 && (
                      <details className="mt-3 rounded border border-gray-200 bg-white p-2">
                        <summary className="cursor-pointer text-xs font-medium text-gray-700">
                          View diff hunks ({diff.hunks.length})
                        </summary>
                        <ul className="mt-2 space-y-2">
                          {diff.hunks.map((hunk, index) => (
                            <li key={`${diff.diffId}-hunk-${index}`} className="rounded border border-gray-100 bg-gray-50 p-2">
                              <p className="text-[11px] font-medium uppercase tracking-wide text-gray-500">
                                {hunk.kind} · lines {hunk.lineCount}
                              </p>
                              {hunk.originalSnippet && (
                                <pre className="mt-1 overflow-x-auto rounded bg-red-50 p-2 text-[11px] text-red-900">
{hunk.originalSnippet}
                                </pre>
                              )}
                              {hunk.generatedSnippet && (
                                <pre className="mt-1 overflow-x-auto rounded bg-green-50 p-2 text-[11px] text-green-900">
{hunk.generatedSnippet}
                                </pre>
                              )}
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}
