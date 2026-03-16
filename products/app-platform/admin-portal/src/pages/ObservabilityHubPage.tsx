/**
 * STORY-K13-012: Observability Hub in Admin Portal
 * Embedded Grafana dashboards (iframe + SSO), alert rule management (K-06), Jaeger trace explorer,
 * Loki log search, and quick-links from service health to relevant dashboard.
 */

import React, { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type TabId = "DASHBOARDS" | "ALERTS" | "TRACES" | "LOGS";

interface GrafanaDashboard {
  uid: string;
  title: string;
  tags: string[];
  service?: string;
  url: string;
  updatedAt: string;
}

interface AlertRule {
  ruleId: string;
  name: string;
  service: string;
  condition: string;
  threshold: number;
  interval: string;   // e.g., "1m"
  severity: "P1" | "P2" | "P3" | "P4";
  state: "NORMAL" | "PENDING" | "FIRING";
  lastEvaluated: string;
}

interface Trace {
  traceId: string;
  service: string;
  operation: string;
  durationMs: number;
  startedAt: string;
  status: "OK" | "ERROR";
}

interface LogEntry {
  timestamp: string;
  level: "DEBUG" | "INFO" | "WARN" | "ERROR" | "FATAL";
  service: string;
  message: string;
  traceId?: string;
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  getDashboards: async (): Promise<GrafanaDashboard[]> => {
    const r = await fetch("/api/admin/observability/dashboards");
    if (!r.ok) throw new Error("Failed to fetch dashboards");
    return r.json();
  },
  getGrafanaSsoUrl: async (uid: string): Promise<{ url: string }> => {
    const r = await fetch(`/api/admin/observability/dashboards/${uid}/sso`);
    if (!r.ok) throw new Error("Failed to get SSO URL");
    return r.json();
  },
  listAlertRules: async (): Promise<AlertRule[]> => {
    const r = await fetch("/api/admin/observability/alerts");
    if (!r.ok) throw new Error("Failed to fetch alerts");
    return r.json();
  },
  createAlertRule: async (rule: Omit<AlertRule, "ruleId" | "state" | "lastEvaluated">): Promise<void> => {
    const r = await fetch("/api/admin/observability/alerts", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(rule),
    });
    if (!r.ok) throw new Error("Failed to create alert rule");
  },
  deleteAlertRule: async (ruleId: string): Promise<void> => {
    const r = await fetch(`/api/admin/observability/alerts/${ruleId}`, { method: "DELETE" });
    if (!r.ok) throw new Error("Failed to delete alert rule");
  },
  searchTraces: async (service: string, minDurationMs: number): Promise<Trace[]> => {
    const url = new URL("/api/admin/observability/traces", window.location.origin);
    if (service) url.searchParams.set("service", service);
    if (minDurationMs) url.searchParams.set("minDurationMs", String(minDurationMs));
    const r = await fetch(url.toString());
    if (!r.ok) throw new Error("Failed to search traces");
    return r.json();
  },
  searchLogs: async (query: string, service: string, level: string): Promise<LogEntry[]> => {
    const url = new URL("/api/admin/observability/logs", window.location.origin);
    if (query) url.searchParams.set("q", query);
    if (service) url.searchParams.set("service", service);
    if (level) url.searchParams.set("level", level);
    const r = await fetch(url.toString());
    if (!r.ok) throw new Error("Failed to search logs");
    return r.json();
  },
};

// ── Dashboard panel ───────────────────────────────────────────────────────────

function DashboardsPanel() {
  const { data: dashboards = [], isLoading } = useQuery({
    queryKey: ["grafana-dashboards"],
    queryFn: api.getDashboards,
  });

  const [activeUid, setActiveUid] = useState<string | null>(null);
  const [ssoUrl, setSsoUrl] = useState<string | null>(null);
  const [tagFilter, setTagFilter] = useState("");

  const allTags = Array.from(new Set(dashboards.flatMap((d) => d.tags)));
  const filtered = dashboards.filter(
    (d) => !tagFilter || d.tags.includes(tagFilter)
  );

  const openDashboard = async (uid: string) => {
    setActiveUid(uid);
    const { url } = await api.getGrafanaSsoUrl(uid);
    setSsoUrl(url);
  };

  return (
    <div>
      <div className="flex items-center gap-2 mb-4">
        <span className="text-sm text-gray-600">Filter by tag:</span>
        <select
          value={tagFilter}
          onChange={(e) => setTagFilter(e.target.value)}
          className="border rounded px-2 py-1 text-sm"
        >
          <option value="">All tags</option>
          {allTags.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-3 mb-6">
        {isLoading ? (
          <p className="text-sm text-gray-500">Loading dashboards…</p>
        ) : filtered.map((d) => (
          <div
            key={d.uid}
            onClick={() => openDashboard(d.uid)}
            className={`border rounded-lg p-4 cursor-pointer transition-colors hover:bg-blue-50
              ${activeUid === d.uid ? "border-blue-500 bg-blue-50" : ""}`}
          >
            <p className="font-medium text-sm mb-1">{d.title}</p>
            <div className="flex flex-wrap gap-1 mb-2">
              {d.tags.map((tag) => (
                <span key={tag} className="px-1.5 py-0.5 rounded bg-gray-100 text-gray-600 text-xs">{tag}</span>
              ))}
            </div>
            {d.service && <p className="text-xs text-gray-400">Service: {d.service}</p>}
            <p className="text-xs text-gray-400">Updated {new Date(d.updatedAt).toLocaleDateString()}</p>
          </div>
        ))}
      </div>

      {/* Inline Grafana iframe */}
      {ssoUrl && (
        <div className="border rounded-lg overflow-hidden bg-gray-50" style={{ height: 600 }}>
          <div className="px-4 py-2 border-b bg-white flex items-center justify-between">
            <span className="text-sm font-medium">{dashboards.find((d) => d.uid === activeUid)?.title}</span>
            <a href={ssoUrl} target="_blank" rel="noreferrer" className="text-xs text-blue-600 hover:underline">
              Open in Grafana ↗
            </a>
          </div>
          <iframe
            key={ssoUrl}
            src={ssoUrl}
            className="w-full"
            style={{ height: "calc(600px - 41px)" }}
            title="Grafana Dashboard"
          />
        </div>
      )}
    </div>
  );
}

// ── Alert rules panel ─────────────────────────────────────────────────────────

function AlertsPanel() {
  const qc = useQueryClient();
  const { data: rules = [], isLoading } = useQuery({
    queryKey: ["alert-rules"],
    queryFn: api.listAlertRules,
    refetchInterval: 30000,
  });

  const [showCreate, setShowCreate] = useState(false);
  const [form, setForm] = useState({ name: "", service: "", condition: "", threshold: "", interval: "1m", severity: "P3" });

  const createMut = useMutation({
    mutationFn: () => api.createAlertRule({
      name: form.name,
      service: form.service,
      condition: form.condition,
      threshold: parseFloat(form.threshold),
      interval: form.interval,
      severity: form.severity as AlertRule["severity"],
    }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ["alert-rules"] });
      setShowCreate(false);
    },
  });

  const deleteMut = useMutation({
    mutationFn: (id: string) => api.deleteAlertRule(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ["alert-rules"] }),
  });

  const stateColor: Record<AlertRule["state"], string> = {
    NORMAL: "bg-green-100 text-green-700",
    PENDING: "bg-yellow-100 text-yellow-700",
    FIRING: "bg-red-100 text-red-700 font-semibold",
  };
  const sevColor: Record<AlertRule["severity"], string> = {
    P1: "text-red-700 font-bold",
    P2: "text-orange-700 font-semibold",
    P3: "text-amber-700",
    P4: "text-gray-500",
  };

  return (
    <div>
      <div className="flex justify-end mb-4">
        <button
          onClick={() => setShowCreate(true)}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          + New Alert Rule
        </button>
      </div>

      {isLoading ? (
        <p className="text-sm text-gray-500">Loading…</p>
      ) : (
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              {["Rule", "Service", "Condition", "Severity", "State", "Last Evaluated", "Actions"].map((h) => (
                <th key={h} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {rules.map((rule) => (
              <tr key={rule.ruleId} className={rule.state === "FIRING" ? "bg-red-50" : "hover:bg-gray-50"}>
                <td className="px-3 py-2 font-medium">{rule.name}</td>
                <td className="px-3 py-2 text-gray-500">{rule.service}</td>
                <td className="px-3 py-2 font-mono text-xs">{rule.condition} &gt; {rule.threshold}</td>
                <td className={`px-3 py-2 ${sevColor[rule.severity]}`}>{rule.severity}</td>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded text-xs ${stateColor[rule.state]}`}>{rule.state}</span>
                </td>
                <td className="px-3 py-2 text-xs text-gray-400">{new Date(rule.lastEvaluated).toLocaleString()}</td>
                <td className="px-3 py-2">
                  <button
                    onClick={() => { if (window.confirm(`Delete alert "${rule.name}"?`)) deleteMut.mutate(rule.ruleId); }}
                    className="text-xs text-red-600 hover:underline"
                  >
                    Delete
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {showCreate && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl w-full max-w-md p-6">
            <h2 className="text-lg font-semibold mb-4">New Alert Rule</h2>
            {[
              { label: "Rule Name", key: "name", type: "text" },
              { label: "Service", key: "service", type: "text" },
              { label: "Condition Expression", key: "condition", type: "text" },
              { label: "Threshold", key: "threshold", type: "number" },
              { label: "Evaluation Interval", key: "interval", type: "text" },
            ].map(({ label, key, type }) => (
              <div key={key} className="mb-3">
                <label className="block text-sm font-medium text-gray-700 mb-1">{label}</label>
                <input
                  type={type}
                  value={(form as Record<string, string>)[key]}
                  onChange={(e) => setForm((f) => ({ ...f, [key]: e.target.value }))}
                  className="w-full border rounded px-3 py-2 text-sm"
                />
              </div>
            ))}
            <div className="mb-4">
              <label className="block text-sm font-medium text-gray-700 mb-1">Severity</label>
              <select
                value={form.severity}
                onChange={(e) => setForm((f) => ({ ...f, severity: e.target.value }))}
                className="w-full border rounded px-3 py-2 text-sm"
              >
                {["P1", "P2", "P3", "P4"].map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setShowCreate(false)} className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50">Cancel</button>
              <button
                onClick={() => createMut.mutate()}
                disabled={createMut.isPending}
                className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
              >
                Create
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

// ── Traces panel ──────────────────────────────────────────────────────────────

function TracesPanel() {
  const [service, setService] = useState("");
  const [minDurationMs, setMinDurationMs] = useState(0);
  const [submitted, setSubmitted] = useState(false);

  const { data: traces = [], isFetching } = useQuery({
    queryKey: ["traces", service, minDurationMs, submitted],
    queryFn: () => api.searchTraces(service, minDurationMs),
    enabled: submitted,
  });

  return (
    <div>
      <div className="flex gap-3 mb-4">
        <input
          type="text"
          placeholder="Service name…"
          value={service}
          onChange={(e) => setService(e.target.value)}
          className="border rounded px-3 py-2 text-sm w-48"
        />
        <input
          type="number"
          placeholder="Min duration (ms)"
          value={minDurationMs || ""}
          onChange={(e) => setMinDurationMs(Number(e.target.value))}
          className="border rounded px-3 py-2 text-sm w-40"
        />
        <button
          onClick={() => setSubmitted((s) => !s)}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          Search
        </button>
      </div>

      {isFetching && <p className="text-sm text-gray-500">Searching traces…</p>}
      {!isFetching && submitted && traces.length === 0 && (
        <p className="text-sm text-gray-500">No traces found matching the query.</p>
      )}

      {traces.length > 0 && (
        <table className="min-w-full text-sm">
          <thead className="bg-gray-50">
            <tr>
              {["Trace ID", "Service", "Operation", "Duration", "Started", "Status"].map((h) => (
                <th key={h} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {traces.map((t) => (
              <tr key={t.traceId} className={t.status === "ERROR" ? "bg-red-50" : "hover:bg-gray-50"}>
                <td className="px-3 py-2 font-mono text-xs text-blue-600 hover:underline cursor-pointer">
                  <a href={`/jaeger/trace/${t.traceId}`} target="_blank" rel="noreferrer">{t.traceId.slice(0, 12)}…</a>
                </td>
                <td className="px-3 py-2">{t.service}</td>
                <td className="px-3 py-2">{t.operation}</td>
                <td className="px-3 py-2">{t.durationMs}ms</td>
                <td className="px-3 py-2 text-gray-400 text-xs">{new Date(t.startedAt).toLocaleString()}</td>
                <td className="px-3 py-2">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium
                    ${t.status === "OK" ? "bg-green-100 text-green-700" : "bg-red-100 text-red-700"}`}>
                    {t.status}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ── Logs panel ────────────────────────────────────────────────────────────────

function LogsPanel() {
  const [query, setQuery] = useState("");
  const [service, setService] = useState("");
  const [level, setLevel] = useState("");
  const [submitted, setSubmitted] = useState(false);

  const { data: logs = [], isFetching } = useQuery({
    queryKey: ["logs", query, service, level, submitted],
    queryFn: () => api.searchLogs(query, service, level),
    enabled: submitted,
  });

  const levelColor: Record<LogEntry["level"], string> = {
    DEBUG: "text-gray-400",
    INFO: "text-blue-600",
    WARN: "text-amber-600",
    ERROR: "text-red-600",
    FATAL: "text-red-900 font-bold",
  };

  return (
    <div>
      <div className="flex gap-3 mb-4">
        <input
          type="text"
          placeholder='Log search (e.g. "NullPointerException")'
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="border rounded px-3 py-2 text-sm flex-1"
        />
        <input
          type="text"
          placeholder="Service"
          value={service}
          onChange={(e) => setService(e.target.value)}
          className="border rounded px-3 py-2 text-sm w-36"
        />
        <select
          value={level}
          onChange={(e) => setLevel(e.target.value)}
          className="border rounded px-3 py-2 text-sm"
        >
          <option value="">All levels</option>
          {["DEBUG", "INFO", "WARN", "ERROR", "FATAL"].map((l) => (
            <option key={l} value={l}>{l}</option>
          ))}
        </select>
        <button
          onClick={() => setSubmitted((s) => !s)}
          className="px-4 py-2 text-sm bg-blue-600 text-white rounded hover:bg-blue-700"
        >
          Search
        </button>
      </div>

      {isFetching && <p className="text-sm text-gray-500">Searching logs…</p>}

      {logs.length > 0 && (
        <div className="font-mono text-xs space-y-0.5 bg-gray-900 text-gray-300 rounded p-4 max-h-[600px] overflow-y-auto">
          {logs.map((log, i) => (
            <div key={i} className="flex gap-3">
              <span className="text-gray-500 shrink-0">{new Date(log.timestamp).toISOString()}</span>
              <span className={`w-12 shrink-0 ${levelColor[log.level]}`}>{log.level}</span>
              <span className="text-gray-400 shrink-0">[{log.service}]</span>
              <span className="break-all">{log.message}</span>
              {log.traceId && <span className="text-blue-400 shrink-0">trace:{log.traceId.slice(0, 8)}</span>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function ObservabilityHubPage() {
  const [tab, setTab] = useState<TabId>("DASHBOARDS");

  const TABS: Array<{ id: TabId; label: string }> = [
    { id: "DASHBOARDS", label: "Dashboards" },
    { id: "ALERTS", label: "Alert Rules" },
    { id: "TRACES", label: "Distributed Traces" },
    { id: "LOGS", label: "Log Search" },
  ];

  return (
    <div className="p-6">
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Observability Hub</h1>
        <p className="text-sm text-gray-500">
          Grafana dashboards, alert management, Jaeger traces, and Loki log search — all in one place
        </p>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 border-b mb-6">
        {TABS.map((t) => (
          <button
            key={t.id}
            onClick={() => setTab(t.id)}
            className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors
              ${tab === t.id ? "border-blue-600 text-blue-700" : "border-transparent text-gray-500 hover:text-gray-700"}`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {tab === "DASHBOARDS" && <DashboardsPanel />}
      {tab === "ALERTS" && <AlertsPanel />}
      {tab === "TRACES" && <TracesPanel />}
      {tab === "LOGS" && <LogsPanel />}
    </div>
  );
}
