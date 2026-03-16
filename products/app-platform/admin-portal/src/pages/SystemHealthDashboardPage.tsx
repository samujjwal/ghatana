import React, { useEffect, useState } from "react";
import { useQuery } from "@tanstack/react-query";

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface ServiceHealth {
  serviceId: string;
  serviceName: string;
  status: "UP" | "DOWN" | "DEGRADED" | "UNKNOWN";
  responseTimeMs: number;
  uptimePercent: number;
  lastCheckedAt: string;
  version: string;
  instanceCount: number;
}

interface PlatformMetric {
  name: string;
  value: number;
  unit: string;
  trend: "UP" | "DOWN" | "STABLE";
}

interface HealthSummary {
  healthy: number;
  degraded: number;
  down: number;
  unknown: number;
  overallStatus: "HEALTHY" | "DEGRADED" | "CRITICAL";
  services: ServiceHealth[];
  metrics: PlatformMetric[];
  asOf: string;
}

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

async function fetchHealthSummary(): Promise<HealthSummary> {
  const res = await fetch("/api/admin/health");
  if (!res.ok) throw new Error("Failed to fetch health data");
  return res.json();
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const STATUS_STYLES: Record<ServiceHealth["status"], string> = {
  UP:       "bg-green-100  text-green-700  border-green-200",
  DEGRADED: "bg-yellow-100 text-yellow-700 border-yellow-200",
  DOWN:     "bg-red-100    text-red-700    border-red-200",
  UNKNOWN:  "bg-gray-100   text-gray-600   border-gray-200",
};

const STATUS_DOT: Record<ServiceHealth["status"], string> = {
  UP:       "bg-green-500",
  DEGRADED: "bg-yellow-500",
  DOWN:     "bg-red-500",
  UNKNOWN:  "bg-gray-400",
};

function TrendArrow({ trend }: { trend: PlatformMetric["trend"] }) {
  if (trend === "UP")     return <span className="text-green-500 text-xs">▲</span>;
  if (trend === "DOWN")   return <span className="text-red-500   text-xs">▼</span>;
  return <span className="text-gray-400 text-xs">—</span>;
}

// ---------------------------------------------------------------------------
// Summary cards
// ---------------------------------------------------------------------------

function SummaryCard({ label, count, color }: { label: string; count: number; color: string }) {
  return (
    <div className={`rounded-xl border p-4 shadow-sm ${color}`}>
      <p className="text-2xl font-bold">{count}</p>
      <p className="text-sm mt-1">{label}</p>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Service row
// ---------------------------------------------------------------------------

function ServiceRow({ svc }: { svc: ServiceHealth }) {
  return (
    <tr className="hover:bg-gray-50">
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className={`h-2 w-2 rounded-full ${STATUS_DOT[svc.status]}`} />
          <span className="font-medium text-sm text-gray-800">{svc.serviceName}</span>
          <span className="font-mono text-xs text-gray-400">{svc.version}</span>
        </div>
      </td>
      <td className="px-4 py-3">
        <span className={`text-xs font-semibold px-2 py-0.5 rounded-full border ${STATUS_STYLES[svc.status]}`}>
          {svc.status}
        </span>
      </td>
      <td className="px-4 py-3 text-sm text-gray-600">{svc.responseTimeMs} ms</td>
      <td className="px-4 py-3">
        <div className="flex items-center gap-2">
          <div className="w-20 bg-gray-200 rounded-full h-1.5">
            <div
              className={`h-1.5 rounded-full ${svc.uptimePercent >= 99 ? "bg-green-500" : svc.uptimePercent >= 95 ? "bg-yellow-500" : "bg-red-500"}`}
              style={{ width: `${svc.uptimePercent}%` }}
            />
          </div>
          <span className="text-xs text-gray-500">{svc.uptimePercent.toFixed(2)}%</span>
        </div>
      </td>
      <td className="px-4 py-3 text-sm text-gray-500">{svc.instanceCount} inst.</td>
      <td className="px-4 py-3 text-xs text-gray-400">
        {new Date(svc.lastCheckedAt).toLocaleTimeString()}
      </td>
    </tr>
  );
}

// ---------------------------------------------------------------------------
// Overall status banner
// ---------------------------------------------------------------------------

function OverallStatusBanner({ status }: { status: HealthSummary["overallStatus"] }) {
  const configs = {
    HEALTHY:  { bg: "bg-green-50  border-green-200",  text: "text-green-700",  label: "All Systems Operational" },
    DEGRADED: { bg: "bg-yellow-50 border-yellow-200", text: "text-yellow-700", label: "Some services are degraded" },
    CRITICAL: { bg: "bg-red-50    border-red-200",    text: "text-red-700",    label: "Critical issues detected" },
  };
  const cfg = configs[status];
  return (
    <div className={`rounded-xl border px-4 py-3 flex items-center gap-3 ${cfg.bg}`}>
      <div className={`h-3 w-3 rounded-full ${status === "HEALTHY" ? "bg-green-500" : status === "DEGRADED" ? "bg-yellow-500" : "bg-red-500"} animate-pulse`} />
      <span className={`font-semibold ${cfg.text}`}>{cfg.label}</span>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main page
// ---------------------------------------------------------------------------

export default function SystemHealthDashboardPage() {
  const [autoRefresh, setAutoRefresh] = useState(true);

  const { data, isLoading, error, dataUpdatedAt, refetch } = useQuery({
    queryKey: ["health-summary"],
    queryFn: fetchHealthSummary,
    refetchInterval: autoRefresh ? 30_000 : false,
  });

  // Countdown timer for visual feedback
  const [countdown, setCountdown] = useState(30);
  useEffect(() => {
    if (!autoRefresh) { setCountdown(30); return; }
    setCountdown(30);
    const id = setInterval(() => setCountdown((c) => (c <= 1 ? 30 : c - 1)), 1000);
    return () => clearInterval(id);
  }, [autoRefresh, dataUpdatedAt]);

  if (isLoading) {
    return <div className="flex items-center justify-center h-64 text-gray-400">Loading health data…</div>;
  }

  if (error || !data) {
    return <div className="rounded-lg bg-red-50 border border-red-200 p-4 text-red-700 text-sm">Failed to load health data.</div>;
  }

  return (
    <div className="space-y-5">
      {/* Auto-refresh control */}
      <div className="flex items-center justify-between">
        <OverallStatusBanner status={data.overallStatus} />
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-400">
            {autoRefresh ? `Refreshes in ${countdown}s` : "Auto-refresh off"}
          </span>
          <label className="flex items-center gap-1.5 cursor-pointer">
            <input
              type="checkbox" checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="h-4 w-4 accent-blue-600"
            />
            <span className="text-sm text-gray-600">Auto-refresh (30s)</span>
          </label>
          <button onClick={() => refetch()}
            className="px-3 py-1.5 text-xs border border-gray-300 rounded-lg hover:bg-gray-50">
            Refresh now
          </button>
        </div>
      </div>

      {/* Summary cards */}
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
        <SummaryCard label="Healthy" count={data.healthy} color="bg-green-50 border-green-200 text-green-700" />
        <SummaryCard label="Degraded" count={data.degraded} color="bg-yellow-50 border-yellow-200 text-yellow-700" />
        <SummaryCard label="Down" count={data.down} color="bg-red-50 border-red-200 text-red-700" />
        <SummaryCard label="Unknown" count={data.unknown} color="bg-gray-50 border-gray-200 text-gray-600" />
      </div>

      {/* Platform metrics */}
      {data.metrics.length > 0 && (
        <div className="rounded-xl border border-gray-200 bg-white shadow-sm p-4">
          <h3 className="text-sm font-semibold text-gray-700 mb-3">Platform Metrics</h3>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4">
            {data.metrics.map((m) => (
              <div key={m.name} className="text-center">
                <div className="flex items-baseline justify-center gap-1">
                  <span className="text-xl font-bold text-gray-800">{m.value.toLocaleString()}</span>
                  <span className="text-xs text-gray-400">{m.unit}</span>
                  <TrendArrow trend={m.trend} />
                </div>
                <p className="text-xs text-gray-500 mt-0.5">{m.name}</p>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Service table */}
      <div className="rounded-xl border border-gray-200 bg-white shadow-sm overflow-hidden">
        <div className="px-4 py-3 border-b border-gray-100">
          <h3 className="text-sm font-semibold text-gray-700">Services ({data.services.length})</h3>
        </div>
        <table className="min-w-full">
          <thead className="bg-gray-50">
            <tr>
              {["Service", "Status", "Response", "Uptime", "Instances", "Checked"].map((h) => (
                <th key={h} className="text-left px-4 py-2 text-xs font-semibold text-gray-500 uppercase">{h}</th>
              ))}
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {data.services.map((svc) => <ServiceRow key={svc.serviceId} svc={svc} />)}
          </tbody>
        </table>
      </div>

      <p className="text-xs text-gray-400 text-right">Last updated: {new Date(data.asOf).toLocaleTimeString()}</p>
    </div>
  );
}
