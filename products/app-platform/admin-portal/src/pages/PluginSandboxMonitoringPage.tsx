/**
 * STORY-K13-009: Plugin Sandbox Monitoring UI
 * Real-time sandbox metrics per plugin: CPU, memory, execution histogram, syscall violations, network attempts, error rate.
 * Time-series charts for 1h / 6h / 24h with violation log and SLA compliance.
 */

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type TimeRange = "1h" | "6h" | "24h";

interface PluginSandboxSummary {
  pluginId: string;
  pluginName: string;
  tier: "T1" | "T2" | "T3";
  cpuPercent: number;
  memoryMb: number;
  memoryLimitMb: number;
  errorRate: number;            // fraction 0–1
  p99ExecutionMs: number;
  slaLimitMs: number;
  violationCount: number;
  status: "OK" | "WARNING" | "CRITICAL";
}

interface TimeSeriesPoint {
  ts: string;     // ISO datetime
  value: number;
}

interface SandboxMetrics {
  pluginId: string;
  pluginName: string;
  timeRange: TimeRange;
  cpuSeries: TimeSeriesPoint[];
  memorySeries: TimeSeriesPoint[];
  execTimeSeries: TimeSeriesPoint[];
  errorRateSeries: TimeSeriesPoint[];
}

interface SandboxViolation {
  violationId: string;
  pluginId: string;
  pluginName: string;
  violationType: "NETWORK_ACCESS" | "FILESYSTEM_ACCESS" | "SYSCALL_DENIED" | "MEMORY_EXCEEDED" | "CPU_EXCEEDED" | "TIMEOUT";
  details: string;
  occurredAt: string;
  severity: "LOW" | "MEDIUM" | "HIGH";
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  listSummaries: async (): Promise<PluginSandboxSummary[]> => {
    const r = await fetch("/api/admin/plugins/sandbox/summaries");
    if (!r.ok) throw new Error("Failed to fetch sandbox summaries");
    return r.json();
  },
  getMetrics: async (pluginId: string, range: TimeRange): Promise<SandboxMetrics> => {
    const r = await fetch(`/api/admin/plugins/sandbox/${pluginId}/metrics?range=${range}`);
    if (!r.ok) throw new Error("Failed to fetch metrics");
    return r.json();
  },
  listViolations: async (pluginId?: string): Promise<SandboxViolation[]> => {
    const url = pluginId
      ? `/api/admin/plugins/sandbox/violations?pluginId=${pluginId}`
      : "/api/admin/plugins/sandbox/violations";
    const r = await fetch(url);
    if (!r.ok) throw new Error("Failed to fetch violations");
    return r.json();
  },
};

// ── Sparkline (SVG bar chart) ─────────────────────────────────────────────────

function Sparkline({ data, color = "#3b82f6", height = 40 }: {
  data: TimeSeriesPoint[];
  color?: string;
  height?: number;
}) {
  if (data.length < 2) return <div style={{ height }} className="bg-gray-50 rounded" />;
  const values = data.map((d) => d.value);
  const max = Math.max(...values, 1);
  const width = 200;
  const pts = data.map((d, i) => {
    const x = (i / (data.length - 1)) * width;
    const y = height - (d.value / max) * height;
    return `${x},${y}`;
  });
  const area = `0,${height} ${pts.join(" ")} ${width},${height}`;

  return (
    <svg width={width} height={height} className="w-full">
      <polyline
        points={area}
        fill={`${color}20`}
        stroke="none"
      />
      <polyline
        points={pts.join(" ")}
        fill="none"
        stroke={color}
        strokeWidth="1.5"
      />
    </svg>
  );
}

// ── Usage bar ─────────────────────────────────────────────────────────────────

function UsageBar({ value, limit, unit }: { value: number; limit: number; unit: string }) {
  const pct = Math.min((value / limit) * 100, 100);
  const color = pct >= 90 ? "bg-red-500" : pct >= 70 ? "bg-amber-400" : "bg-green-500";
  return (
    <div>
      <div className="flex justify-between text-xs text-gray-500 mb-1">
        <span>{value.toFixed(1)} {unit}</span>
        <span>/ {limit} {unit}</span>
      </div>
      <div className="h-1.5 bg-gray-200 rounded-full overflow-hidden">
        <div className={`h-full ${color} rounded-full`} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

// ── Metric detail drawer ──────────────────────────────────────────────────────

function MetricDrawer({
  pluginId,
  pluginName,
  onClose,
}: {
  pluginId: string;
  pluginName: string;
  onClose: () => void;
}) {
  const [range, setRange] = useState<TimeRange>("1h");

  const { data: metrics } = useQuery({
    queryKey: ["sandbox-metrics", pluginId, range],
    queryFn: () => api.getMetrics(pluginId, range),
    refetchInterval: 30000,
  });

  const { data: violations = [] } = useQuery({
    queryKey: ["violations", pluginId],
    queryFn: () => api.listViolations(pluginId),
    refetchInterval: 30000,
  });

  const slaLimitMs = metrics?.slaLimitMs ?? 0;

  return (
    <div className="fixed inset-y-0 right-0 w-[540px] bg-white shadow-2xl overflow-y-auto z-40">
      <div className="sticky top-0 bg-white border-b px-5 py-4 flex items-center justify-between">
        <div>
          <p className="font-semibold">{pluginName}</p>
          <p className="text-xs text-gray-500">Sandbox Metrics</p>
        </div>
        <button onClick={onClose} className="text-gray-400 hover:text-gray-600 text-xl">✕</button>
      </div>

      {metrics && (
        <div className="p-5 space-y-6">
          {/* Time range selector */}
          <div className="flex gap-1">
            {(["1h", "6h", "24h"] as TimeRange[]).map((r) => (
              <button
                key={r}
                onClick={() => setRange(r)}
                className={`px-3 py-1 text-xs rounded border
                  ${range === r ? "bg-blue-600 text-white border-blue-600" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}
              >
                {r}
              </button>
            ))}
          </div>

          {/* Charts */}
          {[
            { label: "CPU Usage (%)", series: metrics.cpuSeries, color: "#3b82f6" },
            { label: "Memory (MB)", series: metrics.memorySeries, color: "#8b5cf6" },
            { label: "Execution Time (ms)", series: metrics.execTimeSeries, color: "#f59e0b" },
            { label: "Error Rate (%)", series: metrics.errorRateSeries, color: "#ef4444" },
          ].map(({ label, series, color }) => (
            <div key={label}>
              <p className="text-xs font-medium text-gray-600 mb-1">{label}</p>
              <Sparkline data={series} color={color} />
            </div>
          ))}

          {/* SLA compliance */}
          {slaLimitMs > 0 && (
            <div className="p-3 border rounded">
              <p className="text-xs font-medium text-gray-600 mb-1">SLA Compliance</p>
              <p className="text-xs text-gray-500">
                SLA target: p99 execution ≤ {slaLimitMs}ms
              </p>
            </div>
          )}
        </div>
      )}

      {/* Violation log */}
      <div className="px-5 pb-5">
        <h3 className="text-sm font-semibold mb-2">Violation Log</h3>
        {violations.length === 0 ? (
          <p className="text-xs text-gray-500">No violations recorded.</p>
        ) : (
          <div className="space-y-2">
            {violations.map((v) => (
              <div key={v.violationId} className={`p-2 rounded text-xs border
                ${v.severity === "HIGH" ? "bg-red-50 border-red-200" :
                  v.severity === "MEDIUM" ? "bg-amber-50 border-amber-200" : "bg-gray-50 border-gray-200"}`}>
                <div className="flex items-center justify-between">
                  <span className="font-medium">{v.violationType.replace(/_/g, " ")}</span>
                  <span className={`px-1.5 py-0.5 rounded font-medium
                    ${v.severity === "HIGH" ? "bg-red-200 text-red-800" :
                      v.severity === "MEDIUM" ? "bg-amber-200 text-amber-800" : "bg-gray-200 text-gray-700"}`}>
                    {v.severity}
                  </span>
                </div>
                <p className="mt-1 text-gray-600">{v.details}</p>
                <p className="mt-0.5 text-gray-400">{new Date(v.occurredAt).toLocaleString()}</p>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function PluginSandboxMonitoringPage() {
  const [selectedPlugin, setSelectedPlugin] = useState<{ pluginId: string; pluginName: string } | null>(null);
  const [tierFilter, setTierFilter] = useState<"ALL" | "T1" | "T2" | "T3">("ALL");

  const { data: summaries = [], isLoading } = useQuery({
    queryKey: ["sandbox-summaries"],
    queryFn: api.listSummaries,
    refetchInterval: 15000,
  });

  const { data: violations = [] } = useQuery({
    queryKey: ["violations", null],
    queryFn: () => api.listViolations(),
    refetchInterval: 30000,
  });

  const filtered = summaries.filter((s) => tierFilter === "ALL" || s.tier === tierFilter);

  const statusColor: Record<PluginSandboxSummary["status"], string> = {
    OK: "text-green-500",
    WARNING: "text-amber-500",
    CRITICAL: "text-red-500",
  };
  const statusDot: Record<PluginSandboxSummary["status"], string> = {
    OK: "bg-green-400",
    WARNING: "bg-amber-400",
    CRITICAL: "bg-red-500 animate-pulse",
  };

  return (
    <div className="p-6">
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Plugin Sandbox Monitoring</h1>
          <p className="text-sm text-gray-500">Real-time CPU, memory, execution, and violation tracking per plugin</p>
        </div>
        <div className="flex gap-2">
          {(["ALL", "T1", "T2", "T3"] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTierFilter(t)}
              className={`px-3 py-1.5 text-xs rounded border
                ${tierFilter === t ? "bg-gray-800 text-white border-gray-800" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}
            >
              {t}
            </button>
          ))}
        </div>
      </div>

      {/* Summary stats */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        {[
          { label: "Total Plugins", value: summaries.length, color: "text-gray-800" },
          { label: "OK", value: summaries.filter((s) => s.status === "OK").length, color: "text-green-600" },
          { label: "Warning", value: summaries.filter((s) => s.status === "WARNING").length, color: "text-amber-600" },
          { label: "Recent Violations", value: violations.length, color: "text-red-600" },
        ].map(({ label, value, color }) => (
          <div key={label} className="bg-white border rounded-lg p-4 shadow-sm">
            <p className="text-xs text-gray-500">{label}</p>
            <p className={`text-2xl font-bold ${color}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* Plugin cards grid */}
      {isLoading ? (
        <p className="text-sm text-gray-500">Loading sandbox data…</p>
      ) : filtered.length === 0 ? (
        <p className="text-sm text-gray-500">No plugins match the selected filter.</p>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map((s) => (
            <div
              key={s.pluginId}
              className="bg-white border rounded-lg p-4 shadow-sm hover:shadow-md transition-shadow cursor-pointer"
              onClick={() => setSelectedPlugin({ pluginId: s.pluginId, pluginName: s.pluginName })}
            >
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <div className={`w-2 h-2 rounded-full ${statusDot[s.status]}`} />
                  <span className="font-medium text-sm">{s.pluginName}</span>
                </div>
                <span className={`px-2 py-0.5 rounded text-xs font-medium
                  ${s.tier === "T1" ? "bg-blue-100 text-blue-700" :
                    s.tier === "T2" ? "bg-teal-100 text-teal-700" : "bg-purple-100 text-purple-700"}`}>
                  {s.tier}
                </span>
              </div>

              <div className="space-y-3">
                <div>
                  <p className="text-xs text-gray-500 mb-1">CPU</p>
                  <UsageBar value={s.cpuPercent} limit={100} unit="%" />
                </div>
                <div>
                  <p className="text-xs text-gray-500 mb-1">Memory</p>
                  <UsageBar value={s.memoryMb} limit={s.memoryLimitMb} unit="MB" />
                </div>
              </div>

              <div className="mt-3 flex items-center justify-between text-xs text-gray-500">
                <span>p99: {s.p99ExecutionMs}ms / {s.slaLimitMs}ms SLA</span>
                {s.violationCount > 0 && (
                  <span className="text-red-600 font-medium">{s.violationCount} violations</span>
                )}
                {s.errorRate > 0 && (
                  <span className={s.errorRate > 0.05 ? "text-red-600" : "text-amber-600"}>
                    {(s.errorRate * 100).toFixed(1)}% err
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
      )}

      {selectedPlugin && (
        <MetricDrawer
          pluginId={selectedPlugin.pluginId}
          pluginName={selectedPlugin.pluginName}
          onClose={() => setSelectedPlugin(null)}
        />
      )}
    </div>
  );
}
