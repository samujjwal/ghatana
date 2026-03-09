import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';

type Severity = 'critical' | 'high' | 'medium' | 'low' | 'info';

interface ScanFinding {
  id: string;
  title: string;
  severity: Severity;
  category: string;
  file: string;
  line: number;
  description: string;
  ruleId: string;
}

interface ScanSummary {
  critical: number;
  high: number;
  medium: number;
  low: number;
  info: number;
}

interface ScanResult {
  id: string;
  name: string;
  status: 'completed' | 'running' | 'failed';
  scanner: string;
  branch: string;
  startedAt: string;
  completedAt: string;
  summary: ScanSummary;
  findings: ScanFinding[];
}

const SEVERITY_BADGE: Record<Severity, string> = {
  critical: 'bg-red-900/30 text-red-400 border-red-800',
  high: 'bg-orange-900/30 text-orange-400 border-orange-800',
  medium: 'bg-yellow-900/30 text-yellow-400 border-yellow-800',
  low: 'bg-blue-900/30 text-blue-400 border-blue-800',
  info: 'bg-zinc-800 text-zinc-400 border-zinc-700',
};

const SEVERITY_DOT: Record<Severity, string> = {
  critical: 'bg-red-500',
  high: 'bg-orange-500',
  medium: 'bg-yellow-500',
  low: 'bg-blue-500',
  info: 'bg-zinc-500',
};

const SUMMARY_CARD: Record<Severity, { bg: string; text: string; label: string }> = {
  critical: { bg: 'bg-red-950/50 border-red-900/50', text: 'text-red-400', label: 'Critical' },
  high: { bg: 'bg-orange-950/50 border-orange-900/50', text: 'text-orange-400', label: 'High' },
  medium: { bg: 'bg-yellow-950/50 border-yellow-900/50', text: 'text-yellow-400', label: 'Medium' },
  low: { bg: 'bg-blue-950/50 border-blue-900/50', text: 'text-blue-400', label: 'Low' },
  info: { bg: 'bg-zinc-900 border-zinc-800', text: 'text-zinc-400', label: 'Info' },
};

/**
 * ScanResultsPage — Displays security scan summary counts and findings table.
 *
 * @doc.type component
 * @doc.purpose Security scan results with severity breakdown and findings
 * @doc.layer product
 */
const ScanResultsPage: React.FC = () => {
  const { scanId } = useParams<{ scanId: string }>();
  const [severityFilter, setSeverityFilter] = useState<Severity | 'all'>('all');

  const { data: scan, isLoading, error } = useQuery<ScanResult>({
    queryKey: ['scan-results', scanId],
    queryFn: async () => {
      const res = await fetch(`/api/security-scans/${scanId}`, {
        headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
      });
      if (!res.ok) throw new Error('Failed to load scan results');
      return res.json() as Promise<ScanResult>;
    },
    enabled: !!scanId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-red-900/20 border border-red-800 rounded-lg p-4 text-red-400">
          {error instanceof Error ? error.message : 'Failed to load scan results'}
        </div>
      </div>
    );
  }

  const summary = scan?.summary ?? { critical: 0, high: 0, medium: 0, low: 0, info: 0 };
  const totalFindings = summary.critical + summary.high + summary.medium + summary.low + summary.info;
  const findings = scan?.findings ?? [];
  const filteredFindings = severityFilter === 'all'
    ? findings
    : findings.filter((f) => f.severity === severityFilter);

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Link to="/security/scans" className="text-zinc-400 hover:text-zinc-200 transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-zinc-100">{scan?.name ?? 'Scan Results'}</h1>
            <p className="text-sm text-zinc-400 mt-1">
              {scan?.scanner ?? 'Scanner'} &middot; Branch: <span className="font-mono text-zinc-300">{scan?.branch ?? '—'}</span> &middot; {scan?.completedAt ? new Date(scan.completedAt).toLocaleString() : '—'}
            </p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 border border-zinc-700 text-zinc-300 hover:bg-zinc-800 text-sm font-medium rounded-lg transition-colors">Export</button>
          <button className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white text-sm font-medium rounded-lg transition-colors">Re-scan</button>
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-5 gap-4">
        {(['critical', 'high', 'medium', 'low', 'info'] as const).map((sev) => {
          const card = SUMMARY_CARD[sev];
          return (
            <button
              key={sev}
              onClick={() => setSeverityFilter(severityFilter === sev ? 'all' : sev)}
              className={`rounded-lg border p-4 text-center transition-all ${card.bg} ${
                severityFilter === sev ? 'ring-2 ring-blue-500' : ''
              }`}
            >
              <p className={`text-3xl font-bold ${card.text}`}>{summary[sev]}</p>
              <p className="text-xs text-zinc-500 mt-1 uppercase tracking-wide">{card.label}</p>
            </button>
          );
        })}
      </div>

      {/* Total bar */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-4 flex items-center justify-between">
        <span className="text-sm text-zinc-400">Total findings: <span className="text-zinc-200 font-semibold">{totalFindings}</span></span>
        {severityFilter !== 'all' && (
          <button onClick={() => setSeverityFilter('all')} className="text-xs text-blue-400 hover:text-blue-300">
            Clear filter
          </button>
        )}
      </div>

      {/* Findings Table */}
      <div className="bg-zinc-900 border border-zinc-800 rounded-lg overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-zinc-800 text-xs uppercase tracking-wide text-zinc-500">
              <th className="text-left px-5 py-3 font-medium">Severity</th>
              <th className="text-left px-5 py-3 font-medium">Finding</th>
              <th className="text-left px-5 py-3 font-medium">Category</th>
              <th className="text-left px-5 py-3 font-medium">File</th>
              <th className="text-left px-5 py-3 font-medium">Rule</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {filteredFindings.length === 0 ? (
              <tr><td colSpan={5} className="px-5 py-8 text-center text-zinc-500 text-sm">No findings{severityFilter !== 'all' ? ` for ${severityFilter} severity` : ''}.</td></tr>
            ) : (
              filteredFindings.map((f) => (
                <tr key={f.id} className="hover:bg-zinc-800/50 transition-colors">
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-semibold rounded-full border ${SEVERITY_BADGE[f.severity]}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${SEVERITY_DOT[f.severity]}`} />
                      {f.severity}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <p className="text-sm text-zinc-200 font-medium">{f.title}</p>
                    <p className="text-xs text-zinc-500 mt-0.5 line-clamp-1">{f.description}</p>
                  </td>
                  <td className="px-5 py-3 text-sm text-zinc-400">{f.category}</td>
                  <td className="px-5 py-3 text-sm font-mono text-zinc-400">
                    {f.file}<span className="text-zinc-600">:{f.line}</span>
                  </td>
                  <td className="px-5 py-3 text-xs font-mono text-zinc-500">{f.ruleId}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default ScanResultsPage;
