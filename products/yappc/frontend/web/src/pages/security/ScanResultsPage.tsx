import React, { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { parseJsonResponse, readErrorResponse } from '@/lib/http';

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
  critical: 'bg-destructive-bg/30 text-destructive border-destructive-border',
  high: 'bg-warning-bg/30 text-warning-color border-warning-border',
  medium: 'bg-warning-bg/30 text-warning-color border-warning-border',
  low: 'bg-info-bg/30 text-info-color border-info-border',
  info: 'bg-surface text-fg-muted border-border',
};

const SEVERITY_DOT: Record<Severity, string> = {
  critical: 'bg-destructive-bg',
  high: 'bg-warning-bg',
  medium: 'bg-warning-bg',
  low: 'bg-info-bg',
  info: 'bg-surface-muted',
};

const SUMMARY_CARD: Record<Severity, { bg: string; text: string; label: string }> = {
  critical: { bg: 'bg-destructive-bg/50 border-destructive-border/50', text: 'text-destructive', label: 'Critical' },
  high: { bg: 'bg-warning-bg/50 border-warning-border/50', text: 'text-warning-color', label: 'High' },
  medium: { bg: 'bg-warning-bg/50 border-warning-border/50', text: 'text-warning-color', label: 'Medium' },
  low: { bg: 'bg-info-bg/50 border-info-border/50', text: 'text-info-color', label: 'Low' },
  info: { bg: 'bg-surface border-border', text: 'text-fg-muted', label: 'Info' },
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
      if (!res.ok) {
        throw new Error(await readErrorResponse(res, 'Failed to load scan results'));
      }
      return parseJsonResponse<ScanResult>(res, 'scan results');
    },
    enabled: !!scanId,
  });

  if (isLoading) {
    return (
      <div className="flex items-center justify-center min-h-[50vh]">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-info-border" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-8">
        <div className="bg-destructive-bg/20 border border-destructive-border rounded-lg p-4 text-destructive">
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
          <Link to="/security/scans" className="text-fg-muted hover:text-fg-muted transition-colors">
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" /></svg>
          </Link>
          <div>
            <h1 className="text-2xl font-bold text-fg-muted">{scan?.name ?? 'Scan Results'}</h1>
            <p className="text-sm text-fg-muted mt-1">
              {scan?.scanner ?? 'Scanner'} &middot; Branch: <span className="font-mono text-fg-muted">{scan?.branch ?? '—'}</span> &middot; {scan?.completedAt ? new Date(scan.completedAt).toLocaleString() : '—'}
            </p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 border border-border text-fg-muted hover:bg-surface text-sm font-medium rounded-lg transition-colors">Export</button>
          <button className="px-4 py-2 bg-primary hover:bg-info-bg text-white text-sm font-medium rounded-lg transition-colors">Re-scan</button>
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
              <p className="text-xs text-fg-muted mt-1 uppercase tracking-wide">{card.label}</p>
            </button>
          );
        })}
      </div>

      {/* Total bar */}
      <div className="bg-surface border border-border rounded-lg p-4 flex items-center justify-between">
        <span className="text-sm text-fg-muted">Total findings: <span className="text-fg-muted font-semibold">{totalFindings}</span></span>
        {severityFilter !== 'all' && (
          <button onClick={() => setSeverityFilter('all')} className="text-xs text-info-color hover:text-info-color">
            Clear filter
          </button>
        )}
      </div>

      {/* Findings Table */}
      <div className="bg-surface border border-border rounded-lg overflow-hidden">
        <table className="w-full">
          <thead>
            <tr className="border-b border-border text-xs uppercase tracking-wide text-fg-muted">
              <th className="text-left px-5 py-3 font-medium">Severity</th>
              <th className="text-left px-5 py-3 font-medium">Finding</th>
              <th className="text-left px-5 py-3 font-medium">Category</th>
              <th className="text-left px-5 py-3 font-medium">File</th>
              <th className="text-left px-5 py-3 font-medium">Rule</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-zinc-800">
            {filteredFindings.length === 0 ? (
              <tr><td colSpan={5} className="px-5 py-8 text-center text-fg-muted text-sm">No findings{severityFilter !== 'all' ? ` for ${severityFilter} severity` : ''}.</td></tr>
            ) : (
              filteredFindings.map((f) => (
                <tr key={f.id} className="hover:bg-surface/50 transition-colors">
                  <td className="px-5 py-3">
                    <span className={`inline-flex items-center gap-1.5 px-2 py-0.5 text-xs font-semibold rounded-full border ${SEVERITY_BADGE[f.severity]}`}>
                      <span className={`w-1.5 h-1.5 rounded-full ${SEVERITY_DOT[f.severity]}`} />
                      {f.severity}
                    </span>
                  </td>
                  <td className="px-5 py-3">
                    <p className="text-sm text-fg-muted font-medium">{f.title}</p>
                    <p className="text-xs text-fg-muted mt-0.5 line-clamp-1">{f.description}</p>
                  </td>
                  <td className="px-5 py-3 text-sm text-fg-muted">{f.category}</td>
                  <td className="px-5 py-3 text-sm font-mono text-fg-muted">
                    {f.file}<span className="text-fg-muted">:{f.line}</span>
                  </td>
                  <td className="px-5 py-3 text-xs font-mono text-fg-muted">{f.ruleId}</td>
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
