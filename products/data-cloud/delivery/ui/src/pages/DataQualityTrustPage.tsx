/**
 * Data Quality Trust Page (DC-P3-003)
 *
 * Canonical Data Plane contract surface for quality/trust scoring.
 *
 * @doc.type page
 * @doc.purpose Display canonical collection-level trust and quality scores
 * @doc.layer frontend
 * @doc.pattern Dashboard
 */

import type { ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, CheckCircle2, MinusCircle } from 'lucide-react';
import { useDataQualityTrustScores } from '../api/data-quality.service';

function scoreTone(score: number): 'good' | 'warn' | 'bad' {
  if (score >= 80) return 'good';
  if (score >= 60) return 'warn';
  return 'bad';
}

function ScoreBadge({ score }: { score: number }): ReactElement {
  const tone = scoreTone(score);
  if (tone === 'good') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-emerald-100 px-2.5 py-1 text-xs font-medium text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-300">
        <CheckCircle2 className="h-3.5 w-3.5" />
        {score}
      </span>
    );
  }
  if (tone === 'warn') {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-amber-100 px-2.5 py-1 text-xs font-medium text-amber-700 dark:bg-amber-900/30 dark:text-amber-300">
        <AlertTriangle className="h-3.5 w-3.5" />
        {score}
      </span>
    );
  }
  return (
    <span className="inline-flex items-center gap-1 rounded-full bg-rose-100 px-2.5 py-1 text-xs font-medium text-rose-700 dark:bg-rose-900/30 dark:text-rose-300">
      <MinusCircle className="h-3.5 w-3.5" />
      {score}
    </span>
  );
}

export function DataQualityTrustPage(): ReactElement {
  const { t } = useTranslation();
  const { data, isLoading, isError, error } = useDataQualityTrustScores();

  return (
    <main className="min-h-screen bg-gray-50 dark:bg-gray-950 p-6" data-testid="data-quality-trust-page">
      <div className="mx-auto max-w-6xl">
        <header className="mb-6">
          <h1 className="text-xl font-bold text-gray-900 dark:text-white">Data Quality Trust Scores</h1>
          <p className="mt-1 text-sm text-gray-500 dark:text-gray-400">
            Canonical Data Plane trust contract derived from collection registry metadata.
          </p>
        </header>

        {isLoading && (
          <div className="rounded-xl border border-gray-200 bg-white p-6 text-sm text-gray-500 dark:border-gray-800 dark:bg-gray-900 dark:text-gray-400">
            {t('loading.trustScores')}
          </div>
        )}

        {isError && (
          <div className="rounded-xl border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700 dark:border-rose-900/40 dark:bg-rose-900/10 dark:text-rose-300">
            {error instanceof Error ? error.message : t('error.failedToLoadTrustScores')}
          </div>
        )}

        {data && (
          <section className="rounded-xl border border-gray-200 bg-white dark:border-gray-800 dark:bg-gray-900">
            <div className="border-b border-gray-100 px-4 py-3 text-xs text-gray-500 dark:border-gray-800 dark:text-gray-400">
              {data.count} collections · Snapshot {new Date(data.generatedAt).toLocaleString()}
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 dark:bg-gray-900/60">
                    <th className="px-4 py-2 text-left text-xs uppercase tracking-wide text-gray-500">Collection</th>
                    <th className="px-4 py-2 text-left text-xs uppercase tracking-wide text-gray-500">Trust</th>
                    <th className="px-4 py-2 text-left text-xs uppercase tracking-wide text-gray-500">Quality</th>
                    <th className="px-4 py-2 text-left text-xs uppercase tracking-wide text-gray-500">Lifecycle</th>
                    <th className="px-4 py-2 text-left text-xs uppercase tracking-wide text-gray-500">Operational</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                  {data.scores.map((score) => (
                    <tr key={score.collection} className="hover:bg-gray-50 dark:hover:bg-gray-800/40">
                      <td className="px-4 py-2 font-medium text-gray-900 dark:text-gray-100">{score.collection}</td>
                      <td className="px-4 py-2"><ScoreBadge score={score.trustScore} /></td>
                      <td className="px-4 py-2 text-gray-600 dark:text-gray-300">{Math.round(score.qualityScore * 100)}%</td>
                      <td className="px-4 py-2 text-gray-600 dark:text-gray-300">{score.lifecycleStatus}</td>
                      <td className="px-4 py-2 text-gray-600 dark:text-gray-300">{score.operationalStatus}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        )}
      </div>
    </main>
  );
}
