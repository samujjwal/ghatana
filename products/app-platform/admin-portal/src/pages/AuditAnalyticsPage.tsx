/**
 * STORY-K13-011: Audit Analytics and Anomaly View
 * Heatmap by hour/day, top actors by event count, anomaly score per actor,
 * unusual access pattern detection (off-hours, RESTRICTED data, high rate from same actor).
 */

import React, { useState } from "react";
import { useQuery } from "@tanstack/react-query";

// ── Types ─────────────────────────────────────────────────────────────────────

interface ActorStat {
  actorId: string;
  actorName: string;
  eventCount: number;
  anomalyScore: number;    // 0–100
  lastSeen: string;
  flags: Array<"OFF_HOURS" | "RESTRICTED_DATA" | "HIGH_RATE" | "UNUSUAL_PATTERN">;
}

interface HeatmapCell {
  hour: number;   // 0–23
  dayOfWeek: number;  // 0 (Mon) – 6 (Sun)
  count: number;
}

interface AnomalyAlert {
  alertId: string;
  actorId: string;
  actorName: string;
  type: "HIGH_RATE" | "OFF_HOURS_RESTRICTED" | "RESTRICTED_DATA_ACCESS" | "UNUSUAL_PATTERN";
  description: string;
  occurredAt: string;
  severity: "LOW" | "MEDIUM" | "HIGH";
}

// ── API ───────────────────────────────────────────────────────────────────────

const api = {
  getActorStats: async (days: number): Promise<ActorStat[]> => {
    const r = await fetch(`/api/admin/audit/analytics/actors?days=${days}`);
    if (!r.ok) throw new Error("Failed to fetch actor stats");
    return r.json();
  },
  getHeatmap: async (days: number): Promise<HeatmapCell[]> => {
    const r = await fetch(`/api/admin/audit/analytics/heatmap?days=${days}`);
    if (!r.ok) throw new Error("Failed to fetch heatmap");
    return r.json();
  },
  getAnomalies: async (): Promise<AnomalyAlert[]> => {
    const r = await fetch("/api/admin/audit/analytics/anomalies");
    if (!r.ok) throw new Error("Failed to fetch anomalies");
    return r.json();
  },
};

// ── Heat Map ──────────────────────────────────────────────────────────────────

const HOURS = Array.from({ length: 24 }, (_, i) => i);
const DAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

function AuditHeatmap({ cells }: { cells: HeatmapCell[] }) {
  const maxCount = Math.max(...cells.map((c) => c.count), 1);

  const getCount = (day: number, hour: number) =>
    cells.find((c) => c.dayOfWeek === day && c.hour === hour)?.count ?? 0;

  const intensity = (count: number) => {
    const ratio = count / maxCount;
    if (ratio === 0) return "bg-gray-100";
    if (ratio < 0.2) return "bg-blue-100";
    if (ratio < 0.4) return "bg-blue-200";
    if (ratio < 0.6) return "bg-blue-400";
    if (ratio < 0.8) return "bg-blue-600";
    return "bg-blue-800";
  };

  return (
    <div className="overflow-x-auto">
      <div className="inline-block">
        {/* Hour labels */}
        <div className="flex ml-10 mb-1">
          {HOURS.filter((h) => h % 3 === 0).map((h) => (
            <div key={h} className="w-[84px] text-xs text-gray-400 text-center">{h}:00</div>
          ))}
        </div>

        {DAYS.map((day, dayIdx) => (
          <div key={day} className="flex items-center mb-1">
            <span className="w-10 text-xs text-gray-500 pr-1 text-right">{day}</span>
            {HOURS.map((hour) => {
              const count = getCount(dayIdx, hour);
              return (
                <div
                  key={hour}
                  title={`${day} ${hour}:00 — ${count} events`}
                  className={`w-7 h-7 rounded-sm mx-0.5 ${intensity(count)} cursor-default transition-opacity hover:opacity-70`}
                />
              );
            })}
          </div>
        ))}

        <div className="flex items-center gap-2 mt-2 ml-10">
          <span className="text-xs text-gray-400">Lower</span>
          {["bg-gray-100", "bg-blue-100", "bg-blue-200", "bg-blue-400", "bg-blue-600", "bg-blue-800"].map((c) => (
            <div key={c} className={`w-5 h-5 rounded-sm ${c}`} />
          ))}
          <span className="text-xs text-gray-400">Higher</span>
        </div>
      </div>
    </div>
  );
}

// ── Anomaly score bar ─────────────────────────────────────────────────────────

function AnomalyScoreBar({ score }: { score: number }) {
  const color = score >= 70 ? "bg-red-500" : score >= 40 ? "bg-amber-400" : "bg-green-400";
  const label = score >= 70 ? "HIGH" : score >= 40 ? "MEDIUM" : "LOW";
  const labelColor = score >= 70 ? "text-red-700" : score >= 40 ? "text-amber-700" : "text-green-700";
  return (
    <div className="flex items-center gap-2">
      <div className="flex-1 h-1.5 bg-gray-200 rounded-full">
        <div className={`h-full ${color} rounded-full`} style={{ width: `${score}%` }} />
      </div>
      <span className={`text-xs font-medium w-14 ${labelColor}`}>{label} {score}</span>
    </div>
  );
}

// ── Flag badge ────────────────────────────────────────────────────────────────

function FlagBadge({ flag }: { flag: ActorStat["flags"][number] }) {
  const map: Record<typeof flag, string> = {
    OFF_HOURS: "bg-purple-100 text-purple-700",
    RESTRICTED_DATA: "bg-red-100 text-red-700",
    HIGH_RATE: "bg-amber-100 text-amber-700",
    UNUSUAL_PATTERN: "bg-orange-100 text-orange-700",
  };
  const label: Record<typeof flag, string> = {
    OFF_HOURS: "Off-hours",
    RESTRICTED_DATA: "Restricted data",
    HIGH_RATE: "High rate",
    UNUSUAL_PATTERN: "Unusual",
  };
  return (
    <span className={`px-1.5 py-0.5 rounded text-xs font-medium ${map[flag]}`}>
      {label[flag]}
    </span>
  );
}

// ── Main page ─────────────────────────────────────────────────────────────────

type DayRange = 7 | 30 | 90;

export default function AuditAnalyticsPage() {
  const [days, setDays] = useState<DayRange>(30);

  const { data: actors = [], isLoading: actorsLoading } = useQuery({
    queryKey: ["audit-actors", days],
    queryFn: () => api.getActorStats(days),
    refetchInterval: 60000,
  });

  const { data: heatmap = [] } = useQuery({
    queryKey: ["audit-heatmap", days],
    queryFn: () => api.getHeatmap(days),
  });

  const { data: anomalies = [] } = useQuery({
    queryKey: ["audit-anomalies"],
    queryFn: api.getAnomalies,
    refetchInterval: 60000,
  });

  const topActors = [...actors].sort((a, b) => b.eventCount - a.eventCount).slice(0, 10);
  const highRiskActors = actors.filter((a) => a.anomalyScore >= 70);

  const severityColor: Record<AnomalyAlert["severity"], string> = {
    LOW: "bg-gray-100 text-gray-600",
    MEDIUM: "bg-amber-100 text-amber-700",
    HIGH: "bg-red-100 text-red-700",
  };

  return (
    <div className="p-6 space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Audit Analytics</h1>
          <p className="text-sm text-gray-500">Access patterns, anomaly detection, and actor analytics</p>
        </div>
        <div className="flex gap-1">
          {([7, 30, 90] as DayRange[]).map((d) => (
            <button
              key={d}
              onClick={() => setDays(d)}
              className={`px-3 py-1.5 text-xs rounded border
                ${days === d ? "bg-blue-600 text-white border-blue-600" : "text-gray-600 border-gray-300 hover:bg-gray-50"}`}
            >
              {d}d
            </button>
          ))}
        </div>
      </div>

      {/* KPI summary */}
      <div className="grid grid-cols-4 gap-4">
        {[
          { label: "Total Actors", value: actors.length },
          { label: "High-Risk Actors", value: highRiskActors.length, danger: highRiskActors.length > 0 },
          { label: "Active Anomaly Alerts", value: anomalies.length, danger: anomalies.length > 0 },
          { label: "Total Audit Events", value: actors.reduce((s, a) => s + a.eventCount, 0).toLocaleString() },
        ].map(({ label, value, danger }) => (
          <div key={label} className={`border rounded-lg p-4 shadow-sm ${danger ? "border-red-300 bg-red-50" : "bg-white"}`}>
            <p className="text-xs text-gray-500">{label}</p>
            <p className={`text-2xl font-bold ${danger ? "text-red-700" : "text-gray-800"}`}>{value}</p>
          </div>
        ))}
      </div>

      {/* Heatmap */}
      <div className="bg-white border rounded-lg p-5 shadow-sm">
        <h2 className="text-base font-semibold mb-4">Event Frequency Heatmap</h2>
        <AuditHeatmap cells={heatmap} />
        <p className="text-xs text-gray-400 mt-3">Hour of day (UTC) vs. day of week. Last {days} days.</p>
      </div>

      {/* Anomaly alerts */}
      {anomalies.length > 0 && (
        <div className="bg-white border border-red-200 rounded-lg p-5 shadow-sm">
          <h2 className="text-base font-semibold mb-4 text-red-700">Active Anomaly Alerts</h2>
          <div className="space-y-2">
            {anomalies.map((a) => (
              <div key={a.alertId} className="flex items-start justify-between gap-4 p-3 rounded border border-gray-100">
                <div className="flex-1">
                  <div className="flex items-center gap-2 mb-0.5">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${severityColor[a.severity]}`}>
                      {a.severity}
                    </span>
                    <span className="text-sm font-medium">{a.actorName}</span>
                  </div>
                  <p className="text-xs text-gray-600">{a.description}</p>
                  <p className="text-xs text-gray-400 mt-0.5">{new Date(a.occurredAt).toLocaleString()}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Top actors table */}
      <div className="bg-white border rounded-lg p-5 shadow-sm">
        <h2 className="text-base font-semibold mb-4">Top Actors by Event Count</h2>
        {actorsLoading ? (
          <p className="text-sm text-gray-500">Loading…</p>
        ) : (
          <table className="min-w-full text-sm">
            <thead className="bg-gray-50">
              <tr>
                {["Actor", "Event Count", "Anomaly Score", "Flags", "Last Seen"].map((h) => (
                  <th key={h} className="px-3 py-2 text-left text-xs font-medium text-gray-500 uppercase">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {topActors.map((a) => (
                <tr key={a.actorId} className={a.anomalyScore >= 70 ? "bg-red-50" : "hover:bg-gray-50"}>
                  <td className="px-3 py-2 font-medium">{a.actorName}</td>
                  <td className="px-3 py-2">{a.eventCount.toLocaleString()}</td>
                  <td className="px-3 py-2 w-48">
                    <AnomalyScoreBar score={a.anomalyScore} />
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex flex-wrap gap-1">
                      {a.flags.map((f) => <FlagBadge key={f} flag={f} />)}
                    </div>
                  </td>
                  <td className="px-3 py-2 text-gray-500 text-xs">{new Date(a.lastSeen).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
