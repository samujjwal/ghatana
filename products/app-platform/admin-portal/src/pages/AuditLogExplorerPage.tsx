import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface AuditEntry {
  auditId: string;
  action: string;
  actor: string;
  entityId: string;
  entityType: string;
  beforeState: Record<string, unknown> | null;
  afterState:  Record<string, unknown> | null;
  ipAddress: string | null;
  sessionId: string | null;
  tamperDetected: boolean;
  hashChain: string;
  occurredAt: string;
}

interface AuditPage {
  entries: AuditEntry[];
  total: number;
  page: number;
  pageSize: number;
}

interface AuditFilters {
  search: string;
  actor: string;
  entityType: string;
  action: string;
  fromDate: string;
  toDate: string;
  tamperOnly: boolean;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

const API = "/api/admin/audit";

async function fetchAuditLogs(filters: AuditFilters, page: number, pageSize: number): Promise<AuditPage> {
  const params = new URLSearchParams({ page: String(page), pageSize: String(pageSize) });
  if (filters.search)     params.set("search",     filters.search);
  if (filters.actor)      params.set("actor",      filters.actor);
  if (filters.entityType) params.set("entityType", filters.entityType);
  if (filters.action)     params.set("action",     filters.action);
  if (filters.fromDate)   params.set("from",       filters.fromDate);
  if (filters.toDate)     params.set("to",         filters.toDate);
  if (filters.tamperOnly) params.set("tamperOnly", "true");
  const res = await fetch(`${API}?${params}`);
  if (!res.ok) throw new Error("Failed to load audit logs");
  return res.json();
}

async function exportPdf(filters: AuditFilters): Promise<void> {
  const params = new URLSearchParams({ format: "pdf" });
  if (filters.actor)      params.set("actor",      filters.actor);
  if (filters.entityType) params.set("entityType", filters.entityType);
  if (filters.fromDate)   params.set("from",       filters.fromDate);
  if (filters.toDate)     params.set("to",         filters.toDate);
  if (filters.tamperOnly) params.set("tamperOnly", "true");

  const res = await fetch(`${API}/export?${params}`);
  if (!res.ok) throw new Error("Failed to export audit log");
  const blob = await res.blob();
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement("a");
  a.href     = url;
  a.download = `audit-${new Date().toISOString().slice(0, 10)}.pdf`;
  a.click();
  URL.revokeObjectURL(url);
}

// ---------------------------------------------------------------------------
// Default filter state
// ---------------------------------------------------------------------------

const DEFAULT_FILTERS: AuditFilters = {
  search: "", actor: "", entityType: "", action: "", fromDate: "", toDate: "", tamperOnly: false,
};

// ---------------------------------------------------------------------------
// State-diff viewer
// ---------------------------------------------------------------------------

function StateDiff({ before, after }: { before: Record<string, unknown> | null; after: Record<string, unknown> | null }) {
  if (!before && !after) return <p className="text-gray-400 text-xs">No state recorded</p>;

  const keys = new Set([...Object.keys(before ?? {}), ...Object.keys(after ?? {})]);
  return (
    <div className="font-mono text-xs space-y-0.5">
      {[...keys].map((key) => {
        const bVal = before?.[key];
        const aVal = after?.[key];
        if (bVal === aVal) return null;
        return (
          <div key={key} className="flex gap-2">
            <span className="text-gray-400 w-32 truncate">{key}:</span>
            {bVal !== undefined && (
              <span className="text-red-500 bg-red-50 px-1 rounded">{JSON.stringify(bVal)}</span>
            )}
            {bVal !== undefined && aVal !== undefined && (
              <span className="text-gray-400">→</span>
            )}
            {aVal !== undefined && (
              <span className="text-green-600 bg-green-50 px-1 rounded">{JSON.stringify(aVal)}</span>
            )}
          </div>
        );
      })}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Audit detail drawer
// ---------------------------------------------------------------------------

function AuditDrawer({ entry, onClose }: { entry: AuditEntry; onClose: () => void }) {
  return (
    <div className="fixed inset-y-0 right-0 w-[480px] bg-white shadow-2xl border-l border-gray-200 flex flex-col z-40">
      <div className="px-5 py-4 border-b flex justify-between items-start">
        <div>
          <h2 className="font-semibold text-gray-800">{entry.action}</h2>
          <p className="text-xs text-gray-400 font-mono">{entry.auditId}</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600">✕</button>
      </div>
      <div className="flex-1 overflow-auto p-5 space-y-4 text-sm">
        {entry.tamperDetected && (
          <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-red-700 font-medium flex items-center gap-2">
            <span>⚠️</span> Tamper detected — hash chain invalid for this entry.
          </div>
        )}

        <dl className="grid grid-cols-2 gap-y-2">
          <dt className="text-gray-500">Actor</dt>      <dd className="font-mono text-xs">{entry.actor}</dd>
          <dt className="text-gray-500">Entity Type</dt><dd>{entry.entityType}</dd>
          <dt className="text-gray-500">Entity ID</dt> <dd className="font-mono text-xs break-all">{entry.entityId}</dd>
          <dt className="text-gray-500">IP Address</dt> <dd className="font-mono text-xs">{entry.ipAddress ?? "—"}</dd>
          <dt className="text-gray-500">Session</dt>    <dd className="font-mono text-xs">{entry.sessionId ?? "—"}</dd>
          <dt className="text-gray-500">Occurred At</dt><dd className="text-xs">{new Date(entry.occurredAt).toLocaleString()}</dd>
        </dl>

        <div>
          <h3 className="font-medium text-gray-700 mb-2">State Change</h3>
          <StateDiff before={entry.beforeState} after={entry.afterState} />
        </div>

        <div>
          <h3 className="font-medium text-gray-700 mb-1">Hash Chain</h3>
          <p className="font-mono text-xs text-gray-500 break-all bg-gray-50 rounded px-2 py-1">{entry.hashChain}</p>
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Filter panel
// ---------------------------------------------------------------------------

function FilterPanel({ filters, onChange }: { filters: AuditFilters; onChange: (f: AuditFilters) => void }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-sm grid grid-cols-2 md:grid-cols-4 gap-3">
      <input type="text" placeholder="Search (action, entity…)" value={filters.search}
        onChange={(e) => onChange({ ...filters, search: e.target.value })}
        className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm col-span-2 focus:outline-none focus:ring-2 focus:ring-blue-500" />
      <input type="text" placeholder="Actor" value={filters.actor}
        onChange={(e) => onChange({ ...filters, actor: e.target.value })}
        className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none" />
      <input type="text" placeholder="Entity type" value={filters.entityType}
        onChange={(e) => onChange({ ...filters, entityType: e.target.value })}
        className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none" />
      <div className="flex flex-col">
        <label className="text-xs text-gray-500 mb-0.5">From</label>
        <input type="date" value={filters.fromDate}
          onChange={(e) => onChange({ ...filters, fromDate: e.target.value })}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none" />
      </div>
      <div className="flex flex-col">
        <label className="text-xs text-gray-500 mb-0.5">To</label>
        <input type="date" value={filters.toDate}
          onChange={(e) => onChange({ ...filters, toDate: e.target.value })}
          className="border border-gray-300 rounded-lg px-3 py-1.5 text-sm focus:outline-none" />
      </div>
      <label className="flex items-center gap-2 text-sm col-span-2 cursor-pointer">
        <input type="checkbox" checked={filters.tamperOnly}
          onChange={(e) => onChange({ ...filters, tamperOnly: e.target.checked })}
          className="accent-red-600 h-4 w-4" />
        <span className="text-red-600 font-medium">Tamper-flagged entries only</span>
      </label>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function AuditLogExplorerPage() {
  const [filters, setFilters] = useState<AuditFilters>(DEFAULT_FILTERS);
  const [page, setPage] = useState(1);
  const PAGE_SIZE = 25;
  const [detailEntry, setDetailEntry] = useState<AuditEntry | null>(null);
  const [exporting, setExporting] = useState(false);

  const { data, isLoading } = useQuery({
    queryKey: ["audit-logs", filters, page],
    queryFn: () => fetchAuditLogs(filters, page, PAGE_SIZE),
    placeholderData: (prev) => prev,
  });

  function handleFilterChange(f: AuditFilters) {
    setFilters(f);
    setPage(1);
  }

  async function handleExportPdf() {
    setExporting(true);
    try {
      await exportPdf(filters);
    } finally {
      setExporting(false);
    }
  }

  const totalPages = data ? Math.ceil(data.total / PAGE_SIZE) : 1;

  return (
    <div className="space-y-4">
      {/* Filter panel */}
      <FilterPanel filters={filters} onChange={handleFilterChange} />

      {/* Toolbar */}
      <div className="flex items-center justify-between">
        <span className="text-sm text-gray-500">{data ? `${data.total.toLocaleString()} entries` : ""}</span>
        <div className="flex gap-2">
          <button onClick={() => setFilters(DEFAULT_FILTERS)}
            className="px-3 py-1.5 text-sm border border-gray-300 rounded-lg hover:bg-gray-50">
            Clear filters
          </button>
          <button onClick={handleExportPdf} disabled={exporting}
            className="px-3 py-1.5 text-sm bg-slate-800 text-white rounded-lg hover:bg-slate-700 disabled:opacity-50">
            {exporting ? "Exporting…" : "Export PDF"}
          </button>
        </div>
      </div>

      {/* Table */}
      <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50 border-b">
            <tr>
              {["Time", "Action", "Actor", "Entity", "Entity ID", "Tamper", ""].map((h) => (
                <th key={h} className="text-left px-4 py-2.5 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {isLoading
              ? <tr><td colSpan={7} className="text-center py-8 text-gray-400">Loading…</td></tr>
              : (data?.entries ?? []).map((entry) => (
                <tr key={entry.auditId}
                  className={`hover:bg-gray-50 ${entry.tamperDetected ? "bg-red-50" : ""}`}>
                  <td className="px-4 py-3 text-xs text-gray-400 whitespace-nowrap">
                    {new Date(entry.occurredAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    <span className="font-mono text-xs bg-slate-100 text-slate-700 px-1.5 py-0.5 rounded">
                      {entry.action}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600 font-mono">{entry.actor}</td>
                  <td className="px-4 py-3 text-xs text-gray-500">{entry.entityType}</td>
                  <td className="px-4 py-3 text-xs font-mono text-gray-400 max-w-[140px] truncate" title={entry.entityId}>
                    {entry.entityId}
                  </td>
                  <td className="px-4 py-3">
                    {entry.tamperDetected
                      ? <span className="text-xs bg-red-100 text-red-700 px-2 py-0.5 rounded-full font-medium">⚠ TAMPER</span>
                      : <span className="text-xs text-gray-300">✓</span>}
                  </td>
                  <td className="px-4 py-3">
                    <button onClick={() => setDetailEntry(entry)}
                      className="text-xs text-blue-600 hover:underline">Details</button>
                  </td>
                </tr>
              ))}
            {!isLoading && data?.entries.length === 0 && (
              <tr><td colSpan={7} className="text-center py-8 text-gray-400">No audit log entries found.</td></tr>
            )}
          </tbody>
        </table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-sm text-gray-500">
        <span>Page {page} of {totalPages}</span>
        <div className="flex gap-2">
          <button onClick={() => setPage((p) => Math.max(1, p - 1))} disabled={page === 1}
            className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">← Prev</button>
          <button onClick={() => setPage((p) => Math.min(totalPages, p + 1))} disabled={page === totalPages}
            className="px-3 py-1 border rounded-lg disabled:opacity-40 hover:bg-gray-50">Next →</button>
        </div>
      </div>

      {detailEntry && <AuditDrawer entry={detailEntry} onClose={() => setDetailEntry(null)} />}
    </div>
  );
}
