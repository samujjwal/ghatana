import { useState, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { atom, useAtom } from 'jotai';

// ── Jotai atoms ────────────────────────────────────────────────────────────

const dateFromAtom = atom<string>('');
const dateToAtom   = atom<string>('');
const jurisdictionAtom = atom<string>('ALL');

// ── API helpers ────────────────────────────────────────────────────────────

async function fetchDashboardStats(jurisdiction: string, dateFrom: string, dateTo: string) {
  const params = new URLSearchParams({ jurisdiction, dateFrom, dateTo });
  const res = await fetch(`/api/regulator/analytics/dashboard?${params}`);
  if (!res.ok) throw new Error('Failed to fetch dashboard stats');
  return res.json();
}

async function fetchFirmComparison(jurisdiction: string, dateFrom: string, dateTo: string) {
  const params = new URLSearchParams({ jurisdiction, dateFrom, dateTo });
  const res = await fetch(`/api/regulator/analytics/firm-comparison?${params}`);
  if (!res.ok) throw new Error('Failed to fetch firm comparison');
  return res.json();
}

async function requestPdfExport(jurisdiction: string, dateFrom: string, dateTo: string) {
  const res = await fetch('/api/regulator/analytics/export-pdf', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ jurisdiction, dateFrom, dateTo }),
  });
  if (!res.ok) throw new Error('PDF export failed');
  return res.json();
}

// ── Sub-components ─────────────────────────────────────────────────────────

function StatCard({ label, value, sub }: { label: string; value: string | number; sub?: string }) {
  return (
    <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-5 flex flex-col gap-1">
      <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</span>
      <span className="text-2xl font-bold text-gray-900">{value}</span>
      {sub && <span className="text-xs text-gray-400">{sub}</span>}
    </div>
  );
}

function FirmComparisonTable({ data }: { data: Array<Record<string, unknown>> }) {
  if (!data || data.length === 0)
    return <p className="text-sm text-gray-400 py-4 text-center">No firm data for selected period.</p>;
  const cols = Object.keys(data[0]);
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm text-left">
        <thead>
          <tr className="border-b border-gray-200">
            {cols.map(col => (
              <th key={col} className="pb-2 pr-6 font-medium text-gray-500 capitalize">
                {col.replace(/_/g, ' ')}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-gray-50">
          {data.map((row, i) => (
            <tr key={i} className="hover:bg-gray-50">
              {cols.map(col => (
                <td key={col} className="py-2 pr-6 text-gray-700">
                  {String(row[col] ?? '')}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Inline SVG bar chart for compliance trend data (no external library required). */
function ComplianceTrendChart({ data }: { data: { label: string; value: number }[] }) {
  const chartWidth = 600;
  const chartHeight = 160;
  const paddingLeft = 36;
  const paddingBottom = 24;
  const paddingTop = 8;
  const barGap = 4;

  const plotW = chartWidth - paddingLeft;
  const plotH = chartHeight - paddingBottom - paddingTop;
  const barW = Math.max(4, (plotW - barGap * data.length) / data.length);
  const maxVal = 100; // percentages capped to 100

  return (
    <svg viewBox={`0 0 ${chartWidth} ${chartHeight}`} className="w-full" role="img" aria-label="Compliance Rate Trend">
      {/* Y-axis grid lines */}
      {[0, 25, 50, 75, 100].map((tick) => {
        const y = paddingTop + plotH - (tick / maxVal) * plotH;
        return (
          <g key={tick}>
            <line x1={paddingLeft} y1={y} x2={chartWidth} y2={y} stroke="#e5e7eb" strokeWidth="1" />
            <text x={paddingLeft - 4} y={y + 3} textAnchor="end" fontSize="9" fill="#9ca3af">{tick}%</text>
          </g>
        );
      })}
      {/* Bars */}
      {data.map((pt, i) => {
        const barH = Math.max(1, (Math.min(pt.value, maxVal) / maxVal) * plotH);
        const x = paddingLeft + i * (barW + barGap) + barGap / 2;
        const y = paddingTop + plotH - barH;
        return (
          <g key={pt.label}>
            <rect x={x} y={y} width={barW} height={barH} rx={2} fill="#3b82f6">
              <title>{`${pt.label}: ${pt.value}%`}</title>
            </rect>
            <text
              x={x + barW / 2}
              y={chartHeight - 4}
              textAnchor="middle"
              fontSize="8"
              fill="#9ca3af"
            >
              {pt.label}
            </text>
          </g>
        );
      })}
    </svg>
  );
}

// Nepali BS calendar selector (simple text inputs; integrate BS picker as needed)
function DateRangeSelector() {
  const [dateFrom, setDateFrom] = useAtom(dateFromAtom);
  const [dateTo, setDateTo]     = useAtom(dateToAtom);

  return (
    <div className="flex items-center gap-3 text-sm">
      <label className="text-gray-500 font-medium">From (BS)</label>
      <input
        type="text"
        placeholder="2081-01-01"
        value={dateFrom}
        onChange={e => setDateFrom(e.target.value)}
        className="border border-gray-300 rounded-lg px-3 py-1.5 w-32 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
      <label className="text-gray-500 font-medium">To (BS)</label>
      <input
        type="text"
        placeholder="2081-12-29"
        value={dateTo}
        onChange={e => setDateTo(e.target.value)}
        className="border border-gray-300 rounded-lg px-3 py-1.5 w-32 focus:outline-none focus:ring-2 focus:ring-blue-500"
      />
    </div>
  );
}

// ── Main Page ──────────────────────────────────────────────────────────────

export default function RegulatorAnalyticsDashboardPage() {
  const [dateFrom]       = useAtom(dateFromAtom);
  const [dateTo]         = useAtom(dateToAtom);
  const [jurisdiction, setJurisdiction] = useAtom(jurisdictionAtom);
  const [exportStatus, setExportStatus] = useState<string | null>(null);

  const { data: stats, isLoading: statsLoading } = useQuery({
    queryKey: ['regulator-analytics', jurisdiction, dateFrom, dateTo],
    queryFn:  () => fetchDashboardStats(jurisdiction, dateFrom, dateTo),
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
    enabled: !!(dateFrom && dateTo),
  });

  const { data: firmData, isLoading: firmLoading } = useQuery({
    queryKey: ['regulator-firm-comparison', jurisdiction, dateFrom, dateTo],
    queryFn:  () => fetchFirmComparison(jurisdiction, dateFrom, dateTo),
    refetchInterval: 30_000,
    placeholderData: (prev) => prev,
    enabled: !!(dateFrom && dateTo),
  });

  const statCards = useMemo(() => {
    if (!stats) return [];
    return [
      { label: 'Total Transactions', value: stats.totalTransactions?.toLocaleString() ?? '—' },
      { label: 'Settlement Volume',  value: stats.settlementVolume  ?? '—', sub: stats.currency },
      { label: 'AML Flags',          value: stats.amlFlags          ?? '—' },
      { label: 'Compliance Rate',    value: stats.complianceRate ? `${stats.complianceRate}%` : '—' },
      { label: 'Active Investigations', value: stats.activeInvestigations ?? '—' },
      { label: 'Pending Queries',    value: stats.pendingQueries       ?? '—' },
    ];
  }, [stats]);

  async function handlePdfExport() {
    try {
      setExportStatus('generating');
      const result = await requestPdfExport(jurisdiction, dateFrom, dateTo);
      setExportStatus(null);
      if (result.downloadUrl) window.open(result.downloadUrl, '_blank');
    } catch {
      setExportStatus('error');
      setTimeout(() => setExportStatus(null), 4000);
    }
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <div className="bg-white border-b border-gray-200 px-6 py-4">
        <div className="max-w-7xl mx-auto flex items-center justify-between">
          <div>
            <h1 className="text-xl font-bold text-gray-900">Regulatory Analytics Dashboard</h1>
            <p className="text-sm text-gray-500 mt-0.5">Cross-firm compliance and settlement monitoring</p>
          </div>
          <div className="flex items-center gap-3">
            <select
              value={jurisdiction}
              onChange={e => setJurisdiction(e.target.value)}
              className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              <option value="ALL">All Jurisdictions</option>
              <option value="NPL">NPL - Nepal</option>
              <option value="IND">IND - India</option>
              <option value="SGP">SGP - Singapore</option>
            </select>
            <button
              onClick={handlePdfExport}
              disabled={!dateFrom || !dateTo || exportStatus === 'generating'}
              className="flex items-center gap-2 px-4 py-1.5 bg-blue-600 text-white text-sm font-medium rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {/* Download icon */}
              <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
                  d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
              </svg>
              {exportStatus === 'generating' ? 'Generating…' : 'Export PDF'}
            </button>
          </div>
        </div>

        {/* Date range */}
        <div className="max-w-7xl mx-auto mt-3">
          <DateRangeSelector />
        </div>

        {exportStatus === 'error' && (
          <p className="max-w-7xl mx-auto mt-2 text-sm text-red-600">PDF export failed. Please try again.</p>
        )}
      </div>

      {/* Body */}
      <div className="max-w-7xl mx-auto px-6 py-6 space-y-6">

        {/* KPI Cards */}
        {!dateFrom || !dateTo ? (
          <div className="bg-blue-50 border border-blue-200 rounded-xl p-6 text-center text-sm text-blue-700">
            Select a BS date range above to load analytics.
          </div>
        ) : (
          <>
            {statsLoading ? (
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                {Array.from({ length: 6 }).map((_, i) => (
                  <div key={i} className="bg-white rounded-xl border border-gray-100 p-5 animate-pulse h-20" />
                ))}
              </div>
            ) : (
              <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                {statCards.map(c => <StatCard key={c.label} {...c} />)}
              </div>
            )}

            {/* Cross-firm comparison */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-base font-semibold text-gray-800 mb-4">Cross-Firm Comparison</h2>
              {firmLoading ? (
                <div className="space-y-2">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <div key={i} className="h-8 bg-gray-100 rounded animate-pulse" />
                  ))}
                </div>
              ) : (
                <FirmComparisonTable data={firmData?.firms ?? []} />
              )}
            </div>

            {/* Compliance Rate Trend — inline SVG bar chart */}
            <div className="bg-white rounded-xl shadow-sm border border-gray-100 p-6">
              <h2 className="text-base font-semibold text-gray-800 mb-4">Compliance Rate Trend</h2>
              {stats?.complianceTrend ? (
                <ComplianceTrendChart data={stats.complianceTrend as { label: string; value: number }[]} />
              ) : (
                <p className="text-sm text-gray-400 text-center py-6">No trend data available.</p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  );
}
