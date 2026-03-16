/**
 * STORY-K13-013: SLA and KPI Dashboard
 * Platform SLA metrics: API availability, p50/p95/p99 latency per service, error rate,
 * event throughput, settlement success rate, reconciliation compliance.
 * Traffic lights per target. Historical SLA compliance (30/90 days). Monthly report generation.
 */

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

type HistoryRange = 30 | 90;

interface ServiceKpi {
  service: string;
  availability: number;    // 0–100 (%)
  p50Ms: number;
  p95Ms: number;
  p99Ms: number;
  errorRate: number;       // 0–1 fraction
  slaTargetAvailability: number;
  slaTargetP99Ms: number;
  slaTargetErrorRate: number;
}

interface PlatformKpi {
  metricName: string;
  metricValue: number;
  unit: string;
  targetValue: number;
  compliancePct: number;   // 0–100
  trend: "UP" | "DOWN" | "FLAT";
}

interface SlaSummary {
  overallAvailability: number;
  settlementSuccessRate: number;
  eventThroughputPerSec: number;
  reconciliationCompliancePct: number;
  slaCompliancePct30d: number;
  slaCompliancePct90d: number;
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  getSlaSummary: async (): Promise<SlaSummary> => {
    const r = await fetch("/api/admin/sla/summary");
    if (!r.ok) throw new Error("Failed to fetch SLA summary");
    return r.json();
  },
  getServiceKpis: async (): Promise<ServiceKpi[]> => {
    const r = await fetch("/api/admin/sla/services");
    if (!r.ok) throw new Error("Failed to fetch service KPIs");
    return r.json();
  },
  getPlatformKpis: async (): Promise<PlatformKpi[]> => {
    const r = await fetch("/api/admin/sla/platform");
    if (!r.ok) throw new Error("Failed to fetch platform KPIs");
    return r.json();
  },
  exportMonthlyReport: async (): Promise<void> => {
    const r = await fetch("/api/admin/sla/report/monthly");
    if (!r.ok) throw new Error("Failed to generate report");
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `sla-report-${new Date().toISOString().slice(0, 7)}.pdf`;
    a.click();
    URL.revokeObjectURL(url);
  },
};

// ── Traffic light ─────────────────────────────────────────────────────────────

function TrafficLight({ value, target, higherIsBetter = true }: {
  value: number;
  target: number;
  higherIsBetter?: boolean;
}) {
  const passing = higherIsBetter ? value >= target : value <= target;
  const near = higherIsBetter ? value >= target * 0.95 : value <= target * 1.05;
  const color = passing ? "bg-green-500" : near ? "bg-amber-400" : "bg-red-500";
  return (
    <div className="flex items-center gap-1.5">
      <div className={`w-3 h-3 rounded-full ${color}`} />
      <span className={`text-xs font-medium ${passing ? "text-green-700" : near ? "text-amber-700" : "text-red-700"}`}>
        {passing ? "MET" : near ? "AT RISK" : "BREACH"}
      </span>
    </div>
  );
}

// ── Trend arrow ───────────────────────────────────────────────────────────────

function TrendArrow({ trend }: { trend: PlatformKpi["trend"] }) {
  if (trend === "UP") return <span className="text-green-600 text-sm">▲</span>;
  if (trend === "DOWN") return <span className="text-red-600 text-sm">▼</span>;
  return <span className="text-gray-400 text-sm">—</span>;
}

// ── SLA compliance ring ───────────────────────────────────────────────────────

function SlaRing({ pct, label }: { pct: number; label: string }) {
  const radius = 36;
  const circumference = 2 * Math.PI * radius;
  const stroke = circumference - (pct / 100) * circumference;
  const color = pct >= 99.9 ? "#22c55e" : pct >= 99 ? "#f59e0b" : "#ef4444";

  return (
    <div className="flex flex-col items-center">
      <svg width={90} height={90} className="-rotate-90">
        <circle cx={45} cy={45} r={radius} fill="none" stroke="#e5e7eb" strokeWidth={7} />
        <circle
          cx={45} cy={45} r={radius}
          fill="none"
          stroke={color}
          strokeWidth={7}
          strokeDasharray={circumference}
          strokeDashoffset={stroke}
          strokeLinecap="round"
        />
      </svg>
      <div className="text-center -mt-[70px]">
        <span className="text-lg font-bold" style={{ color }}>{pct.toFixed(2)}%</span>
      </div>
      <div className="mt-[40px] text-xs text-gray-500">{label}</div>
    </div>
  );
}

// ── Executive summary band ─────────────────────────────────────────────────────

function ExecutiveSummary({ summary }: { summary: SlaSummary }) {
  const overallOk = summary.overallAvailability >= 99.9 && summary.slaCompliancePct30d >= 99;
  return (
    <div className={`border rounded-lg p-5 ${overallOk ? "bg-green-50 border-green-300" : "bg-amber-50 border-amber-300"}`}>
      <h2 className="text-base font-semibold mb-1">Platform Status (Executive Summary)</h2>
      <p className={`text-2xl font-bold ${overallOk ? "text-green-700" : "text-amber-700"}`}>
        {overallOk ? "All Targets Met" : "Some Targets At Risk"}
      </p>
      <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mt-4">
        {[
          { label: "API Availability", value: `${summary.overallAvailability.toFixed(3)}%` },
          { label: "Settlement Success", value: `${(summary.settlementSuccessRate * 100).toFixed(2)}%` },
          { label: "Event Throughput", value: `${summary.eventThroughputPerSec.toLocaleString()}/s` },
          { label: "Recon Compliance", value: `${summary.reconciliationCompliancePct.toFixed(1)}%` },
        ].map(({ label, value }) => (
          <div key={label}>
            <p className="text-xs text-gray-500">{label}</p>
            <p className="text-lg font-semibold text-gray-800">{value}</p>
          </div>
        ))}
      </div>
    </div>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function SlaKpiDashboardPage() {
  const [historyRange, setHistoryRange] = useState<HistoryRange>(30);
  const [isExporting, setIsExporting] = useState(false);

  const { data: summary } = useQuery({
    queryKey: ["sla-summary"],
    queryFn: api.getSlaSummary,
    refetchInterval: 60000,
  });

  const { data: serviceKpis = [], isLoading: svLoading } = useQuery({
    queryKey: ["service-kpis"],
    queryFn: api.getServiceKpis,
    refetchInterval: 60000,
  });

  const { data: platformKpis = [] } = useQuery({
    queryKey: ["platform-kpis"],
    queryFn: api.getPlatformKpis,
    refetchInterval: 60000,
  });

  const handleExport = async () => {
    setIsExporting(true);
    try { await api.exportMonthlyReport(); } finally { setIsExporting(false); }
  };

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">SLA & KPI Dashboard</h1>
          <p className="text-sm text-gray-500">Platform reliability metrics and SLA compliance tracking</p>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex gap-1">
            {([30, 90] as HistoryRange[]).map((d) => (
              <button
                key={d}
                onClick={() => setHistoryRange(d)}
                className={`px-3 py-1.5 text-xs rounded border
                  ${historyRange === d ? "bg-blue-600 text-white border-blue-600" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}
              >
                {d}d
              </button>
            ))}
          </div>
          <button
            onClick={handleExport}
            disabled={isExporting}
            className="px-4 py-2 text-sm border rounded text-gray-600 hover:bg-gray-50 disabled:opacity-50"
          >
            {isExporting ? "Generating…" : "Export Monthly Report"}
          </button>
        </div>
      </div>

      {summary && <ExecutiveSummary summary={summary} />}

      {/* SLA compliance rings */}
      {summary && (
        <div className="bg-white border rounded-lg p-5 shadow-sm">
          <h2 className="text-base font-semibold mb-6">Historical SLA Compliance</h2>
          <div className="flex justify-around">
            <SlaRing pct={summary.slaCompliancePct30d} label="Last 30 Days" />
            <SlaRing pct={summary.slaCompliancePct90d} label="Last 90 Days" />
          </div>
        </div>
      )}

      {/* Platform KPIs */}
      <div className="bg-white border rounded-lg p-5 shadow-sm">
        <h2 className="text-base font-semibold mb-4">Platform KPIs</h2>
        <div className="grid grid-cols-2 md:grid-cols-3 xl:grid-cols-4 gap-4">
          {platformKpis.map((kpi) => (
            <div key={kpi.metricName} className="border rounded-lg p-3">
              <p className="text-xs text-gray-500 mb-1">{kpi.metricName}</p>
              <div className="flex items-center gap-2 mb-2">
                <span className="text-xl font-bold text-gray-800">
                  {kpi.metricValue.toLocaleString()}
                </span>
                <span className="text-xs text-gray-400">{kpi.unit}</span>
                <TrendArrow trend={kpi.trend} />
              </div>
              <TrafficLight
                value={kpi.metricValue}
                target={kpi.targetValue}
                higherIsBetter={kpi.unit !== "ms" && kpi.unit !== "%err"}
              />
              <p className="text-xs text-gray-400 mt-1">Target: {kpi.targetValue} {kpi.unit}</p>
            </div>
          ))}
        </div>
      </div>

      {/* Per-service SLA table */}
      <div className="bg-white border rounded-lg p-5 shadow-sm">
        <h2 className="text-base font-semibold mb-4">Service-Level SLA</h2>
        {svLoading ? (
          <p className="text-sm text-gray-500">Loading…</p>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full text-sm">
              <thead className="bg-gray-50">
                <tr>
                  {["Service", "Availability", "p50", "p95", "p99", "Error Rate", "Availability SLA", "p99 SLA", "Error SLA"].map((h) => (
                    <th key={h} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase whitespace-nowrap">{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {serviceKpis.map((svc) => {
                  const availOk = svc.availability >= svc.slaTargetAvailability;
                  const p99Ok = svc.p99Ms <= svc.slaTargetP99Ms;
                  const errOk = svc.errorRate <= svc.slaTargetErrorRate;
                  const anyBreach = !availOk || !p99Ok || !errOk;
                  return (
                    <tr key={svc.service} className={anyBreach ? "bg-red-50" : "hover:bg-gray-50"}>
                      <td className="px-3 py-2 font-medium">{svc.service}</td>
                      <td className="px-3 py-2">{svc.availability.toFixed(3)}%</td>
                      <td className="px-3 py-2">{svc.p50Ms}ms</td>
                      <td className="px-3 py-2">{svc.p95Ms}ms</td>
                      <td className={`px-3 py-2 font-medium ${p99Ok ? "text-gray-700" : "text-red-700"}`}>{svc.p99Ms}ms</td>
                      <td className={`px-3 py-2 ${errOk ? "text-gray-700" : "text-red-700"}`}>
                        {(svc.errorRate * 100).toFixed(3)}%
                      </td>
                      <td className="px-3 py-2">
                        <TrafficLight value={svc.availability} target={svc.slaTargetAvailability} />
                      </td>
                      <td className="px-3 py-2">
                        <TrafficLight value={svc.p99Ms} target={svc.slaTargetP99Ms} higherIsBetter={false} />
                      </td>
                      <td className="px-3 py-2">
                        <TrafficLight value={svc.errorRate} target={svc.slaTargetErrorRate} higherIsBetter={false} />
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
