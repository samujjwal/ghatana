/**
 * Performance Dashboard
 *
 * Admin dashboard for monitoring system performance:
 * - API latency metrics
 * - Database query performance
 * - Error rates
 * - Throughput visualization
 * - Alert status
 *
 * @doc.type component
 * @doc.purpose Monitor and visualize system performance metrics
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState, useEffect } from "react";
import {
  Activity,
  AlertTriangle,
  Clock,
  Database,
  Server,
  TrendingUp,
  TrendingDown,
  Zap,
  RefreshCw,
  Download,
} from "lucide-react";
import { Card, Badge, Button, Select } from "@ghatana/design-system";

interface PerformanceMetrics {
  timestamp: Date;
  totalRequests: number;
  errorRate: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  databaseQueriesPerSecond: number;
  slowQueriesCount: number;
  activeConnections: number;
  cpuUsage: number;
  memoryUsage: number;
}

interface EndpointMetric {
  endpoint: string;
  method: string;
  requestCount: number;
  errorCount: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
}

interface SlowQuery {
  query: string;
  durationMs: number;
  timestamp: Date;
  source: string;
}

interface Alert {
  id: string;
  severity: "warning" | "critical" | "info";
  message: string;
  timestamp: Date;
  acknowledged: boolean;
}

export function PerformanceDashboard() {
  const [timeRange, setTimeRange] = useState("1h");
  const [metrics, setMetrics] = useState<PerformanceMetrics | null>(null);
  const [endpoints, setEndpoints] = useState<EndpointMetric[]>([]);
  const [slowQueries, setSlowQueries] = useState<SlowQuery[]>([]);
  const [alerts, setAlerts] = useState<Alert[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(true);

  const fetchData = async () => {
    setIsLoading(true);
    try {
      // In production, these would be real API calls
      const mockMetrics: PerformanceMetrics = {
        timestamp: new Date(),
        totalRequests: 15420,
        errorRate: 0.02,
        avgLatencyMs: 145,
        p95LatencyMs: 320,
        p99LatencyMs: 580,
        databaseQueriesPerSecond: 245,
        slowQueriesCount: 3,
        activeConnections: 142,
        cpuUsage: 45,
        memoryUsage: 68,
      };

      const mockEndpoints: EndpointMetric[] = [
        {
          endpoint: "/api/content/search",
          method: "GET",
          requestCount: 5234,
          errorCount: 12,
          avgLatencyMs: 89,
          p95LatencyMs: 245,
          p99LatencyMs: 450,
        },
        {
          endpoint: "/api/assessment/submit",
          method: "POST",
          requestCount: 3421,
          errorCount: 45,
          avgLatencyMs: 234,
          p95LatencyMs: 520,
          p99LatencyMs: 890,
        },
        {
          endpoint: "/api/generation/create",
          method: "POST",
          requestCount: 890,
          errorCount: 23,
          avgLatencyMs: 1234,
          p95LatencyMs: 2500,
          p99LatencyMs: 4500,
        },
      ];

      const mockSlowQueries: SlowQuery[] = [
        {
          query: 'SELECT * FROM "ContentAsset" WHERE "tenantId" = $1',
          durationMs: 2450,
          timestamp: new Date(Date.now() - 300000),
          source: "content-service.ts:142",
        },
        {
          query: 'SELECT COUNT(*) FROM "Enrollment" GROUP BY "moduleId"',
          durationMs: 1890,
          timestamp: new Date(Date.now() - 600000),
          source: "analytics-service.ts:89",
        },
      ];

      const mockAlerts: Alert[] = [
        {
          id: "1",
          severity: "warning",
          message: "High P99 latency on /api/generation/create",
          timestamp: new Date(Date.now() - 900000),
          acknowledged: false,
        },
        {
          id: "2",
          severity: "critical",
          message: "Database connection pool at 90% capacity",
          timestamp: new Date(Date.now() - 1800000),
          acknowledged: true,
        },
      ];

      setMetrics(mockMetrics);
      setEndpoints(mockEndpoints);
      setSlowQueries(mockSlowQueries);
      setAlerts(mockAlerts);
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();

    if (autoRefresh) {
      const interval = setInterval(fetchData, 30000);
      return () => clearInterval(interval);
    }
  }, [timeRange, autoRefresh]);

  const exportMetrics = () => {
    const data = {
      metrics,
      endpoints,
      slowQueries,
      exportedAt: new Date().toISOString(),
    };
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `performance-metrics-${new Date().toISOString()}.json`;
    a.click();
  };

  const getHealthStatus = () => {
    if (!metrics) return "unknown";
    if (metrics.errorRate > 0.05 || metrics.p99LatencyMs > 1000) return "critical";
    if (metrics.errorRate > 0.02 || metrics.p95LatencyMs > 500) return "warning";
    return "healthy";
  };

  const healthStatus = getHealthStatus();

  return (
    <div className="space-y-6 p-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Performance Dashboard</h1>
          <p className="text-gray-500 mt-1">
            Monitor system health and performance metrics
          </p>
        </div>
        <div className="flex items-center gap-3">
          <Select value={timeRange} onValueChange={setTimeRange}>
            <option value="1h">Last 1 hour</option>
            <option value="6h">Last 6 hours</option>
            <option value="24h">Last 24 hours</option>
            <option value="7d">Last 7 days</option>
          </Select>
          <Button
            variant="outline"
            onClick={() => setAutoRefresh(!autoRefresh)}
          >
            {autoRefresh ? "Auto-refresh ON" : "Auto-refresh OFF"}
          </Button>
          <Button variant="outline" onClick={exportMetrics}>
            <Download className="w-4 h-4 mr-1" />
            Export
          </Button>
          <Button variant="outline" onClick={fetchData} disabled={isLoading}>
            <RefreshCw className={`w-4 h-4 ${isLoading ? "animate-spin" : ""}`} />
          </Button>
        </div>
      </div>

      {/* Health Status */}
      <Card className={`p-4 border-l-4 ${
        healthStatus === "healthy" ? "border-l-green-500 bg-green-50" :
        healthStatus === "warning" ? "border-l-yellow-500 bg-yellow-50" :
        healthStatus === "critical" ? "border-l-red-500 bg-red-50" :
        "border-l-gray-500"
      }`}>
        <div className="flex items-center gap-3">
          <Activity className={`w-6 h-6 ${
            healthStatus === "healthy" ? "text-green-600" :
            healthStatus === "warning" ? "text-yellow-600" :
            healthStatus === "critical" ? "text-red-600" :
            "text-gray-600"
          }`} />
          <div>
            <h2 className="font-semibold capitalize">{healthStatus}</h2>
            <p className="text-sm text-gray-600">
              {healthStatus === "healthy" && "All systems operating normally"}
              {healthStatus === "warning" && "Performance degradation detected"}
              {healthStatus === "critical" && "Critical issues require attention"}
            </p>
          </div>
        </div>
      </Card>

      {/* Key Metrics Grid */}
      {metrics && (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard
            title="Total Requests"
            value={metrics.totalRequests.toLocaleString()}
            icon={<Zap className="w-5 h-5" />}
            trend="up"
            trendValue="12%"
          />
          <MetricCard
            title="Avg Latency"
            value={`${metrics.avgLatencyMs}ms`}
            icon={<Clock className="w-5 h-5" />}
            trend={metrics.avgLatencyMs < 200 ? "down" : "up"}
            trendValue="8%"
            warning={metrics.avgLatencyMs > 200}
          />
          <MetricCard
            title="Error Rate"
            value={`${(metrics.errorRate * 100).toFixed(2)}%`}
            icon={<AlertTriangle className="w-5 h-5" />}
            trend={metrics.errorRate < 0.01 ? "down" : "up"}
            trendValue="0.5%"
            warning={metrics.errorRate > 0.05}
            critical={metrics.errorRate > 0.1}
          />
          <MetricCard
            title="Active Connections"
            value={metrics.activeConnections.toString()}
            icon={<Server className="w-5 h-5" />}
            trend="stable"
          />
        </div>
      )}

      {/* Latency Percentiles */}
      {metrics && (
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4">Latency Percentiles</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <LatencyBar
              label="P50 (Median)"
              value={metrics.avgLatencyMs}
              max={1000}
              color="green"
            />
            <LatencyBar
              label="P95"
              value={metrics.p95LatencyMs}
              max={1000}
              color="yellow"
            />
            <LatencyBar
              label="P99"
              value={metrics.p99LatencyMs}
              max={1000}
              color="red"
            />
          </div>
        </Card>
      )}

      {/* Endpoint Performance */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4">Endpoint Performance</h3>
        <div className="overflow-x-auto">
          <table className="w-full">
            <thead>
              <tr className="border-b">
                <th className="text-left py-2 text-sm font-medium text-gray-600">Endpoint</th>
                <th className="text-right py-2 text-sm font-medium text-gray-600">Requests</th>
                <th className="text-right py-2 text-sm font-medium text-gray-600">Errors</th>
                <th className="text-right py-2 text-sm font-medium text-gray-600">Avg Latency</th>
                <th className="text-right py-2 text-sm font-medium text-gray-600">P95</th>
                <th className="text-right py-2 text-sm font-medium text-gray-600">P99</th>
              </tr>
            </thead>
            <tbody>
              {endpoints.map((endpoint, i) => (
                <tr key={i} className="border-b last:border-0">
                  <td className="py-3">
                    <Badge variant="outline" className="mr-2">{endpoint.method}</Badge>
                    {endpoint.endpoint}
                  </td>
                  <td className="text-right py-3">{endpoint.requestCount.toLocaleString()}</td>
                  <td className={`text-right py-3 ${endpoint.errorCount > 0 ? "text-red-600" : ""}`}>
                    {endpoint.errorCount}
                  </td>
                  <td className="text-right py-3">{endpoint.avgLatencyMs}ms</td>
                  <td className="text-right py-3">{endpoint.p95LatencyMs}ms</td>
                  <td className="text-right py-3">{endpoint.p99LatencyMs}ms</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>

      {/* Slow Queries */}
      <Card className="p-6">
        <div className="flex items-center gap-2 mb-4">
          <Database className="w-5 h-5 text-yellow-500" />
          <h3 className="text-lg font-semibold">Slow Queries</h3>
          {slowQueries.length > 0 && (
            <Badge variant="destructive">{slowQueries.length}</Badge>
          )}
        </div>
        {slowQueries.length === 0 ? (
          <p className="text-gray-500">No slow queries detected</p>
        ) : (
          <div className="space-y-3">
            {slowQueries.map((query, i) => (
              <div key={i} className="bg-yellow-50 border border-yellow-200 rounded-md p-3">
                <div className="flex items-center justify-between mb-2">
                  <Badge variant="outline" className="text-red-600">
                    {query.durationMs}ms
                  </Badge>
                  <span className="text-xs text-gray-500">{query.source}</span>
                </div>
                <code className="text-sm text-gray-700 block">{query.query}</code>
              </div>
            ))}
          </div>
        )}
      </Card>

      {/* Alerts */}
      <Card className="p-6">
        <div className="flex items-center gap-2 mb-4">
          <AlertTriangle className="w-5 h-5 text-orange-500" />
          <h3 className="text-lg font-semibold">Active Alerts</h3>
          {alerts.filter(a => !a.acknowledged).length > 0 && (
            <Badge variant="destructive">
              {alerts.filter(a => !a.acknowledged).length} unacknowledged
            </Badge>
          )}
        </div>
        <div className="space-y-2">
          {alerts.map((alert) => (
            <div
              key={alert.id}
              className={`p-3 rounded-md border ${
                alert.severity === "critical"
                  ? "bg-red-50 border-red-200"
                  : alert.severity === "warning"
                  ? "bg-yellow-50 border-yellow-200"
                  : "bg-blue-50 border-blue-200"
              }`}
            >
              <div className="flex items-start justify-between">
                <div>
                  <Badge
                    className={`text-xs ${
                      alert.severity === "critical"
                        ? "bg-red-100 text-red-800"
                        : alert.severity === "warning"
                        ? "bg-yellow-100 text-yellow-800"
                        : "bg-blue-100 text-blue-800"
                    }`}
                  >
                    {alert.severity}
                  </Badge>
                  <p className="mt-1 text-sm">{alert.message}</p>
                </div>
                <span className="text-xs text-gray-500">
                  {alert.timestamp.toLocaleTimeString()}
                </span>
              </div>
            </div>
          ))}
        </div>
      </Card>
    </div>
  );
}

function MetricCard({
  title,
  value,
  icon,
  trend,
  trendValue,
  warning,
  critical,
}: {
  title: string;
  value: string;
  icon: React.ReactNode;
  trend?: "up" | "down" | "stable";
  trendValue?: string;
  warning?: boolean;
  critical?: boolean;
}) {
  return (
    <Card className={`p-4 ${critical ? "border-red-300" : warning ? "border-yellow-300" : ""}`}>
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm text-gray-500">{title}</p>
          <p className="text-2xl font-bold mt-1">{value}</p>
          {trend && trendValue && (
            <div className={`flex items-center gap-1 mt-1 text-sm ${
              trend === "up" ? "text-green-600" :
              trend === "down" ? "text-red-600" :
              "text-gray-500"
            }`}>
              {trend === "up" ? <TrendingUp className="w-4 h-4" /> :
               trend === "down" ? <TrendingDown className="w-4 h-4" /> : null}
              <span>{trendValue}</span>
            </div>
          )}
        </div>
        <div className="p-2 bg-gray-100 rounded-lg">
          {icon}
        </div>
      </div>
    </Card>
  );
}

function LatencyBar({
  label,
  value,
  max,
  color,
}: {
  label: string;
  value: number;
  max: number;
  color: "green" | "yellow" | "red";
}) {
  const percentage = Math.min((value / max) * 100, 100);
  const colorClass = {
    green: "bg-green-500",
    yellow: "bg-yellow-500",
    red: "bg-red-500",
  }[color];

  return (
    <div>
      <div className="flex items-center justify-between mb-1">
        <span className="text-sm font-medium">{label}</span>
        <span className="text-sm text-gray-600">{value}ms</span>
      </div>
      <div className="w-full bg-gray-200 rounded-full h-2">
        <div
          className={`h-2 rounded-full ${colorClass}`}
          style={{ width: `${percentage}%` }}
        />
      </div>
    </div>
  );
}
