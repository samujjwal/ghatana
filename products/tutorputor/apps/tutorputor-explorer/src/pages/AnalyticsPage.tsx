import { BarChart2, TrendingUp, TrendingDown, RefreshCw, Loader2 } from "lucide-react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  PieChart,
  Pie,
  Cell,
  Legend,
  ResponsiveContainer,
} from "recharts";
import type { ContentMetrics } from "@/types/content";
import { useContentMetrics } from "@/hooks/useContent";

const STATUS_COLORS: Record<string, string> = {
  draft: "#94a3b8",
  pending_review: "#f59e0b",
  published: "#22c55e",
  archived: "#64748b",
  rejected: "#ef4444",
};

const TYPE_COLORS = [
  "#6366f1", "#8b5cf6", "#a78bfa", "#c4b5fd", "#e9d5ff", "#ddd6fe", "#818cf8",
];

function StatCard({
  label,
  value,
  delta,
  prefix,
  suffix,
}: {
  label: string;
  value: number | string;
  delta?: number;
  prefix?: string;
  suffix?: string;
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <p className="text-xs font-medium text-muted-foreground">{label}</p>
      <p className="mt-1 text-2xl font-bold">
        {prefix}{value}{suffix}
      </p>
      {delta !== undefined && (
        <div className={`mt-1 flex items-center gap-1 text-xs ${delta >= 0 ? "text-green-600" : "text-destructive"}`}>
          {delta >= 0 ? (
            <TrendingUp className="h-3.5 w-3.5" aria-hidden />
          ) : (
            <TrendingDown className="h-3.5 w-3.5" aria-hidden />
          )}
          {Math.abs(delta)}% vs last period
        </div>
      )}
    </div>
  );
}

function ByTypeChart({ metrics }: { metrics: ContentMetrics }) {
  const data = Object.entries(metrics.contentByType).map(([name, count]) => ({
    name: name.charAt(0).toUpperCase() + name.slice(1),
    count,
  }));
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <h2 className="mb-4 text-sm font-semibold">Content by Type</h2>
      <ResponsiveContainer width="100%" height={220}>
        <BarChart data={data} barSize={28}>
          <XAxis dataKey="name" tick={{ fontSize: 11 }} />
          <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
          <Tooltip
            contentStyle={{ fontSize: 12, borderRadius: 8 }}
            cursor={{ fill: "rgba(99,102,241,0.08)" }}
          />
          <Bar dataKey="count" radius={[4, 4, 0, 0]}>
            {data.map((_, i) => (
              <Cell key={i} fill={TYPE_COLORS[i % TYPE_COLORS.length]} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function ByStatusChart({ metrics }: { metrics: ContentMetrics }) {
  const data = Object.entries(metrics.contentByStatus).map(([name, value]) => ({
    name: name.replace("_", " "),
    value,
  }));
  return (
    <div className="rounded-lg border border-border bg-card p-4">
      <h2 className="mb-4 text-sm font-semibold">Status Breakdown</h2>
      <ResponsiveContainer width="100%" height={220}>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            cx="50%"
            cy="50%"
            outerRadius={80}
            label={({ name, percent }: { name: string; percent: number }) =>
              `${name} ${Math.round(percent * 100)}%`
            }
            labelLine={false}
          >
            {data.map((entry) => (
              <Cell
                key={entry.name}
                fill={STATUS_COLORS[entry.name.replace(" ", "_")] ?? "#94a3b8"}
              />
            ))}
          </Pie>
          <Legend wrapperStyle={{ fontSize: 11 }} />
          <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} />
        </PieChart>
      </ResponsiveContainer>
    </div>
  );
}

function RecentGenerationsChart({ metrics }: { metrics: ContentMetrics }) {
  if (!metrics.recentGenerations?.length) return null;
  return (
    <div className="rounded-lg border border-border bg-card p-4 lg:col-span-2">
      <h2 className="mb-4 text-sm font-semibold">Recent Generations (last 7 days)</h2>
      <ResponsiveContainer width="100%" height={180}>
        <BarChart data={metrics.recentGenerations} barSize={24}>
          <XAxis dataKey="date" tick={{ fontSize: 10 }} />
          <YAxis tick={{ fontSize: 10 }} allowDecimals={false} />
          <Tooltip contentStyle={{ fontSize: 12, borderRadius: 8 }} cursor={{ fill: "rgba(99,102,241,0.08)" }} />
          <Bar dataKey="count" fill="#6366f1" radius={[3, 3, 0, 0]} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

export function AnalyticsPage() {
  const { data: metrics, isLoading, error, refetch, isFetching } = useContentMetrics();

  return (
    <div className="flex flex-1 flex-col overflow-y-auto p-6">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-2">
          <BarChart2 className="h-5 w-5 text-primary" aria-hidden />
          <h1 className="text-lg font-semibold">Analytics</h1>
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-1.5 rounded-md border border-border px-3 py-1.5 text-xs hover:bg-muted disabled:opacity-50"
        >
          <RefreshCw className={`h-3.5 w-3.5 ${isFetching ? "animate-spin" : ""}`} aria-hidden />
          Refresh
        </button>
      </div>

      {isLoading ? (
        <div className="flex h-60 items-center justify-center">
          <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        </div>
      ) : error ? (
        <p className="text-sm text-destructive">
          {(error as Error)?.message ?? "Failed to load metrics."}
        </p>
      ) : !metrics ? null : (
        <div className="space-y-6">
          {/* Stat cards */}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
            <StatCard label="Total Content" value={metrics.totalItems} />
            <StatCard label="Published" value={metrics.publishedCount} />
            <StatCard
              label="Avg Quality Score"
              value={Math.round(metrics.avgQualityScore * 100)}
              suffix="/100"
            />
            <StatCard
              label="AI Generated"
              value={Math.round(metrics.aiGeneratedPercentage)}
              suffix="%"
            />
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
            <ByTypeChart metrics={metrics} />
            <ByStatusChart metrics={metrics} />
            <RecentGenerationsChart metrics={metrics} />
          </div>
        </div>
      )}
    </div>
  );
}
