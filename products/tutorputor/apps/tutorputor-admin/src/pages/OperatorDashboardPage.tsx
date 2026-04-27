/**
 * Operator Dashboard Page
 *
 * Persona-specific dashboard for platform operators. Surfaces system-wide
 * health signals, tenant activity, job-queue status, and operational
 * alerts — focused on the Operator / SRE workflow.
 *
 * @doc.type component
 * @doc.purpose Persona dashboard for platform operators
 * @doc.layer product
 * @doc.pattern Page
 */

import { useQuery } from "@tanstack/react-query";
import { Card } from "../components/ui";
import { Spinner } from "@ghatana/design-system";

interface PlatformHealth {
  api: "healthy" | "degraded" | "down";
  database: "healthy" | "degraded" | "down";
  redis: "healthy" | "degraded" | "down";
  contentWorker: "healthy" | "degraded" | "down";
  llmGateway: "healthy" | "degraded" | "down";
}

interface QueueStats {
  queueName: string;
  waiting: number;
  active: number;
  failed: number;
  completed: number;
}

interface TenantActivity {
  tenantId: string;
  tenantName: string;
  activeUsers: number;
  requestsLastHour: number;
  errorRate: number;
}

interface OperatorAlert {
  id: string;
  level: "critical" | "warning" | "info";
  message: string;
  timestamp: string;
}

async function fetchPlatformHealth(): Promise<PlatformHealth> {
  const res = await fetch("/api/v1/admin/observability/health", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch platform health");
  return res.json() as Promise<PlatformHealth>;
}

async function fetchQueueStats(): Promise<QueueStats[]> {
  const res = await fetch("/api/v1/admin/observability/queues", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch queue stats");
  const data = await res.json() as { queues: QueueStats[] };
  return data.queues;
}

async function fetchTenantActivity(): Promise<TenantActivity[]> {
  const res = await fetch("/api/v1/admin/tenants/activity?limit=10", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch tenant activity");
  const data = await res.json() as { tenants: TenantActivity[] };
  return data.tenants;
}

async function fetchOperatorAlerts(): Promise<OperatorAlert[]> {
  const res = await fetch("/api/v1/admin/observability/alerts?limit=20", {
    credentials: "include",
  });
  if (!res.ok) throw new Error("Failed to fetch operator alerts");
  const data = await res.json() as { alerts: OperatorAlert[] };
  return data.alerts;
}

type HealthStatus = PlatformHealth[keyof PlatformHealth];

function healthDot(status: HealthStatus): React.ReactElement {
  const colors: Record<HealthStatus, string> = {
    healthy: "bg-green-500",
    degraded: "bg-yellow-400",
    down: "bg-red-600",
  };
  return (
    <span
      className={`inline-block h-2.5 w-2.5 rounded-full ${colors[status]}`}
    />
  );
}

function alertLevelClass(level: OperatorAlert["level"]): string {
  switch (level) {
    case "critical":
      return "border-red-300 bg-red-50 text-red-800";
    case "warning":
      return "border-yellow-300 bg-yellow-50 text-yellow-800";
    case "info":
      return "border-blue-200 bg-blue-50 text-blue-800";
  }
}

export function OperatorDashboardPage(): React.ReactElement {
  const {
    data: health,
    isLoading: healthLoading,
    isError: healthError,
  } = useQuery<PlatformHealth>({
    queryKey: ["operator", "health"],
    queryFn: fetchPlatformHealth,
    refetchInterval: 30_000,
  });

  const {
    data: queues,
    isLoading: queuesLoading,
    isError: queuesError,
  } = useQuery<QueueStats[]>({
    queryKey: ["operator", "queues"],
    queryFn: fetchQueueStats,
    refetchInterval: 15_000,
  });

  const {
    data: tenants,
    isLoading: tenantsLoading,
    isError: tenantsError,
  } = useQuery<TenantActivity[]>({
    queryKey: ["operator", "tenants", "activity"],
    queryFn: fetchTenantActivity,
    refetchInterval: 60_000,
  });

  const {
    data: alerts,
    isLoading: alertsLoading,
    isError: alertsError,
  } = useQuery<OperatorAlert[]>({
    queryKey: ["operator", "alerts"],
    queryFn: fetchOperatorAlerts,
    refetchInterval: 15_000,
  });

  return (
    <div className="space-y-8 p-6">
      <header>
        <h1 className="text-2xl font-bold text-gray-900">
          Operator Dashboard
        </h1>
        <p className="mt-1 text-sm text-gray-500">
          Platform-wide health, queue status, tenant activity, and alerts.
        </p>
      </header>

      {/* ── Platform Health ──────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Platform Health
        </h2>
        {healthLoading && <Spinner />}
        {healthError && (
          <p className="text-sm text-red-600">Failed to load health status.</p>
        )}
        {health && (
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
            {(
              [
                ["API", health.api],
                ["Database", health.database],
                ["Redis", health.redis],
                ["Content Worker", health.contentWorker],
                ["LLM Gateway", health.llmGateway],
              ] as [string, HealthStatus][]
            ).map(([label, status]) => (
              <Card
                key={label}
                className="flex items-center gap-3 p-4"
              >
                {healthDot(status)}
                <div>
                  <p className="text-xs text-gray-500">{label}</p>
                  <p className="text-sm font-medium capitalize text-gray-800">
                    {status}
                  </p>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      {/* ── Queue Stats ──────────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Job Queues
        </h2>
        {queuesLoading && <Spinner />}
        {queuesError && (
          <p className="text-sm text-red-600">Failed to load queue stats.</p>
        )}
        {queues && queues.length === 0 && (
          <p className="text-sm text-gray-500">No queues reported.</p>
        )}
        {queues && queues.length > 0 && (
          <div className="overflow-hidden rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  {["Queue", "Waiting", "Active", "Failed", "Completed"].map(
                    (h) => (
                      <th
                        key={h}
                        className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500"
                      >
                        {h}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white">
                {queues.map((q) => (
                  <tr key={q.queueName}>
                    <td className="px-4 py-3 text-sm font-medium text-gray-800">
                      {q.queueName}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {q.waiting}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {q.active}
                    </td>
                    <td
                      className={`px-4 py-3 text-sm ${q.failed > 0 ? "font-semibold text-red-700" : "text-gray-600"}`}
                    >
                      {q.failed}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {q.completed}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── Tenant Activity ──────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Top Active Tenants (last hour)
        </h2>
        {tenantsLoading && <Spinner />}
        {tenantsError && (
          <p className="text-sm text-red-600">
            Failed to load tenant activity.
          </p>
        )}
        {tenants && tenants.length === 0 && (
          <p className="text-sm text-gray-500">No tenant activity.</p>
        )}
        {tenants && tenants.length > 0 && (
          <div className="overflow-hidden rounded-lg border border-gray-200">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  {["Tenant", "Active Users", "Requests / hr", "Error Rate"].map(
                    (h) => (
                      <th
                        key={h}
                        className="px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-gray-500"
                      >
                        {h}
                      </th>
                    ),
                  )}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white">
                {tenants.map((t) => (
                  <tr key={t.tenantId}>
                    <td className="px-4 py-3 text-sm font-medium text-gray-800">
                      {t.tenantName}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {t.activeUsers}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">
                      {t.requestsLastHour}
                    </td>
                    <td
                      className={`px-4 py-3 text-sm ${t.errorRate > 5 ? "font-semibold text-red-700" : "text-gray-600"}`}
                    >
                      {t.errorRate.toFixed(1)}%
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* ── Operator Alerts ──────────────────────────────────────────────── */}
      <section>
        <h2 className="mb-4 text-lg font-semibold text-gray-800">
          Active Alerts
        </h2>
        {alertsLoading && <Spinner />}
        {alertsError && (
          <p className="text-sm text-red-600">Failed to load alerts.</p>
        )}
        {alerts && alerts.length === 0 && (
          <p className="text-sm text-green-700">No active alerts.</p>
        )}
        {alerts && alerts.length > 0 && (
          <ul className="space-y-2">
            {alerts.map((alert) => (
              <li
                key={alert.id}
                className={`flex items-center justify-between rounded-md border px-4 py-3 ${alertLevelClass(alert.level)}`}
              >
                <div>
                  <span className="text-xs font-medium uppercase tracking-wide">
                    {alert.level}
                  </span>
                  <p className="mt-0.5 text-sm">{alert.message}</p>
                </div>
                <time className="text-xs opacity-75">{alert.timestamp}</time>
              </li>
            ))}
          </ul>
        )}
      </section>
    </div>
  );
}
