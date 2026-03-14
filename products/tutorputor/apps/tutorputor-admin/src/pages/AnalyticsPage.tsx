import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { Card } from "../components/ui";
import {
  Button,
  Spinner,
  ResponsiveTable,
  type TableColumn,
  PullToRefresh,
} from "@ghatana/design-system";
import { LineChart, BarChart, PieChart, AreaChart } from "@ghatana/charts";
import { useAuth } from "../hooks/useAuth";
import { ExternalLink, Star } from "lucide-react";

type DateRange = "7d" | "30d" | "90d" | "1y";
type MetricTab = "engagement" | "content" | "users" | "performance";

interface EngagementMetrics {
  dailyActiveUsers: { date: string; count: number }[];
  weeklyActiveUsers: { date: string; count: number }[];
  averageSessionDuration: number;
  averageModulesPerSession: number;
  completionRate: number;
  retentionRate: number;
}

interface ContentMetrics {
  totalModules: number;
  publishedModules: number;
  totalLessons: number;
  totalQuizzes: number;
  modulesByCategory: { category: string; count: number }[];
  topModules: {
    id: string;
    title: string;
    completions: number;
    rating: number;
  }[];
  averageRating: number;
}

interface UserMetrics {
  totalUsers: number;
  activeUsers: number;
  newUsersThisPeriod: number;
  usersByRole: { role: string; count: number }[];
  userGrowth: { date: string; count: number }[];
  topActiveUsers: { id: string; name: string; lessonsCompleted: number }[];
}

interface PerformanceMetrics {
  averageLoadTime: number;
  errorRate: number;
  apiLatencyP50: number;
  apiLatencyP99: number;
  syncSuccessRate: number;
  offlineUsagePercent: number;
}

export function AnalyticsPage() {
  const { tenantId } = useAuth();
  const queryClient = useQueryClient();
  const [dateRange, setDateRange] = useState<DateRange>("30d");
  const [activeTab, setActiveTab] = useState<MetricTab>("engagement");

  const { data: engagement, isLoading: loadingEngagement } = useQuery({
    queryKey: ["analytics", "engagement", tenantId, dateRange],
    queryFn: async () => {
      const res = await fetch(
        `/admin/api/v1/analytics/engagement?range=${dateRange}`,
      );
      if (!res.ok) throw new Error("Failed to fetch engagement metrics");
      return res.json() as Promise<EngagementMetrics>;
    },
    enabled: activeTab === "engagement",
  });

  const { data: content, isLoading: loadingContent } = useQuery({
    queryKey: ["analytics", "content", tenantId, dateRange],
    queryFn: async () => {
      const res = await fetch(
        `/admin/api/v1/analytics/content?range=${dateRange}`,
      );
      if (!res.ok) throw new Error("Failed to fetch content metrics");
      return res.json() as Promise<ContentMetrics>;
    },
    enabled: activeTab === "content",
  });

  const { data: users, isLoading: loadingUsers } = useQuery({
    queryKey: ["analytics", "users", tenantId, dateRange],
    queryFn: async () => {
      const res = await fetch(
        `/admin/api/v1/analytics/users?range=${dateRange}`,
      );
      if (!res.ok) throw new Error("Failed to fetch user metrics");
      return res.json() as Promise<UserMetrics>;
    },
    enabled: activeTab === "users",
  });

  const { data: performance, isLoading: loadingPerformance } = useQuery({
    queryKey: ["analytics", "performance", tenantId, dateRange],
    queryFn: async () => {
      const res = await fetch(
        `/admin/api/v1/analytics/performance?range=${dateRange}`,
      );
      if (!res.ok) throw new Error("Failed to fetch performance metrics");
      return res.json() as Promise<PerformanceMetrics>;
    },
    enabled: activeTab === "performance",
  });

  const isLoading =
    loadingEngagement || loadingContent || loadingUsers || loadingPerformance;

  const handleRefresh = async () => {
    switch (activeTab) {
      case "engagement":
        await queryClient.fetchQuery({
          queryKey: ["analytics", "engagement", tenantId, dateRange],
        });
        break;
      case "content":
        await queryClient.fetchQuery({
          queryKey: ["analytics", "content", tenantId, dateRange],
        });
        break;
      case "users":
        await queryClient.fetchQuery({
          queryKey: ["analytics", "users", tenantId, dateRange],
        });
        break;
      case "performance":
        await queryClient.fetchQuery({
          queryKey: ["analytics", "performance", tenantId, dateRange],
        });
        break;
    }
  };

  const tabs: { id: MetricTab; label: string; icon: React.ReactNode }[] = [
    {
      id: "engagement",
      label: "Engagement",
      icon: (
        <svg
          className="w-5 h-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6"
          />
        </svg>
      ),
    },
    {
      id: "content",
      label: "Content",
      icon: (
        <svg
          className="w-5 h-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
          />
        </svg>
      ),
    },
    {
      id: "users",
      label: "Users",
      icon: (
        <svg
          className="w-5 h-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M12 4.354a4 4 0 110 5.292M15 21H3v-1a6 6 0 0112 0v1zm0 0h6v-1a6 6 0 00-9-5.197M13 7a4 4 0 11-8 0 4 4 0 018 0z"
          />
        </svg>
      ),
    },
    {
      id: "performance",
      label: "Performance",
      icon: (
        <svg
          className="w-5 h-5"
          fill="none"
          viewBox="0 0 24 24"
          stroke="currentColor"
        >
          <path
            strokeLinecap="round"
            strokeLinejoin="round"
            strokeWidth={2}
            d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z"
          />
        </svg>
      ),
    },
  ];

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-white">
            Analytics
          </h1>
          <p className="text-gray-600 dark:text-gray-400">
            Detailed insights into platform usage and performance
          </p>
        </div>

        {/* Date Range Selector */}
        <div className="flex gap-2">
          {(["7d", "30d", "90d", "1y"] as DateRange[]).map((range) => (
            <Button
              key={range}
              variant={dateRange === range ? "default" : "outline"}
              size="sm"
              onClick={() => setDateRange(range)}
            >
              {range === "7d"
                ? "7 Days"
                : range === "30d"
                  ? "30 Days"
                  : range === "90d"
                    ? "90 Days"
                    : "1 Year"}
            </Button>
          ))}
        </div>
      </div>

      {/* Tabs */}
      <div className="border-b border-gray-200 dark:border-gray-700">
        <nav className="flex gap-4">
          {tabs.map((tab) => (
            <button
              key={tab.id}
              onClick={() => setActiveTab(tab.id)}
              className={`flex items-center gap-2 px-4 py-3 border-b-2 transition-colors ${
                activeTab === tab.id
                  ? "border-blue-500 text-blue-600 dark:text-blue-400"
                  : "border-transparent text-gray-500 hover:text-gray-700 dark:hover:text-gray-300"
              }`}
            >
              {tab.icon}
              {tab.label}
            </button>
          ))}
        </nav>
      </div>

      {/* Content */}
      <PullToRefresh onRefresh={handleRefresh}>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <Spinner size="lg" />
          </div>
        ) : (
          <>
            {/* Engagement Tab */}
            {activeTab === "engagement" && engagement && (
              <EngagementMetricsView data={engagement} />
            )}

            {/* Content Tab */}
            {activeTab === "content" && content && (
              <ContentMetricsView data={content} />
            )}

            {/* Users Tab */}
            {activeTab === "users" && users && <UserMetricsView data={users} />}

            {/* Performance Tab */}
            {activeTab === "performance" && performance && (
              <PerformanceMetricsView data={performance} />
            )}
          </>
        )}
      </PullToRefresh>
    </div>
  );
}

function StatCard({
  title,
  value,
  subtitle,
  trend,
  icon,
}: {
  title: string;
  value: string | number;
  subtitle?: string;
  trend?: { value: number; positive: boolean };
  icon?: React.ReactNode;
}) {
  return (
    <Card className="p-6">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
            {title}
          </p>
          <p className="mt-2 text-3xl font-bold text-gray-900 dark:text-white">
            {value}
          </p>
          {subtitle && <p className="mt-1 text-sm text-gray-500">{subtitle}</p>}
          {trend && (
            <p
              className={`mt-1 text-sm ${
                trend.positive ? "text-green-600" : "text-red-600"
              }`}
            >
              {trend.positive ? "↑" : "↓"} {Math.abs(trend.value)}%
            </p>
          )}
        </div>
        {icon && (
          <div className="p-3 bg-blue-100 dark:bg-blue-900/30 rounded-lg">
            {icon}
          </div>
        )}
      </div>
    </Card>
  );
}

function EngagementMetricsView({ data }: { data: EngagementMetrics }) {
  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard
          title="Avg Session Duration"
          value={`${Math.round(data.averageSessionDuration / 60)}min`}
          icon={
            <svg
              className="w-6 h-6 text-blue-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }
        />
        <StatCard
          title="Modules/Session"
          value={data.averageModulesPerSession?.toFixed(1) || "0.0"}
          icon={
            <svg
              className="w-6 h-6 text-blue-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10"
              />
            </svg>
          }
        />
        <StatCard
          title="Completion Rate"
          value={`${data.completionRate}%`}
          icon={
            <svg
              className="w-6 h-6 text-blue-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z"
              />
            </svg>
          }
        />
        <StatCard
          title="Retention Rate"
          value={`${data.retentionRate}%`}
          icon={
            <svg
              className="w-6 h-6 text-blue-600"
              fill="none"
              viewBox="0 0 24 24"
              stroke="currentColor"
            >
              <path
                strokeLinecap="round"
                strokeLinejoin="round"
                strokeWidth={2}
                d="M17 20h5v-2a3 3 0 00-5.356-1.857M17 20H7m10 0v-2c0-.656-.126-1.283-.356-1.857M7 20H2v-2a3 3 0 015.356-1.857M7 20v-2c0-.656.126-1.283.356-1.857m0 0a5.002 5.002 0 019.288 0M15 7a3 3 0 11-6 0 3 3 0 016 0zm6 3a2 2 0 11-4 0 2 2 0 014 0zM7 10a2 2 0 11-4 0 2 2 0 014 0z"
              />
            </svg>
          }
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Daily Active Users
          </h3>
          <div className="h-64">
            <AreaChart
              data={data.dailyActiveUsers}
              xField="date"
              yField="count"
              color="#3b82f6"
            />
          </div>
        </Card>

        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Weekly Active Users
          </h3>
          <div className="h-64">
            <LineChart
              data={data.weeklyActiveUsers}
              xField="date"
              yField="count"
              color="#10b981"
            />
          </div>
        </Card>
      </div>
    </div>
  );
}

function ContentMetricsView({ data }: { data: ContentMetrics }) {
  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Total Modules" value={data.totalModules} />
        <StatCard title="Published" value={data.publishedModules} />
        <StatCard title="Total Lessons" value={data.totalLessons} />
        <StatCard
          title="Avg Rating"
          value={data.averageRating.toFixed(1)}
          subtitle="out of 5"
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Modules by Category
          </h3>
          <div className="h-64">
            <PieChart
              data={data.modulesByCategory}
              nameField="category"
              valueField="count"
            />
          </div>
        </Card>

        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Top Modules
          </h3>
          <div className="h-64">
            <BarChart
              data={data.topModules.slice(0, 5)}
              xField="title"
              yField="completions"
              color="#8b5cf6"
            />
          </div>
        </Card>
      </div>

      {/* Top Modules Table */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
          Top Performing Modules
        </h3>
        <ResponsiveTable
          data={data.topModules}
          getRowKey={(module) => module.id}
          columns={[
            {
              header: "Module",
              accessor: (module) => (
                <a
                  href={`/cms?moduleId=${module.id}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1"
                >
                  {module.title}
                  <ExternalLink className="w-4 h-4" />
                </a>
              ),
            },
            {
              header: "Completions",
              accessor: "completions",
              hideOnMobile: true,
            },
            {
              header: "Rating",
              accessor: (module) => (
                <span className="inline-flex items-center gap-1">
                  <Star className="w-4 h-4 text-yellow-500 fill-yellow-500" />
                  {module.rating.toFixed(1)}
                </span>
              ),
            },
          ]}
          mobileCardRenderer={(module) => (
            <div className="space-y-2">
              <a
                href={`/cms?moduleId=${module.id}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-1 font-medium"
              >
                {module.title}
                <ExternalLink className="w-4 h-4" />
              </a>
              <div className="flex justify-between text-sm">
                <span className="text-gray-500">
                  Completions: {module.completions}
                </span>
                <span className="inline-flex items-center gap-1">
                  <Star className="w-4 h-4 text-yellow-500 fill-yellow-500" />
                  {module.rating.toFixed(1)}
                </span>
              </div>
            </div>
          )}
        />
      </Card>
    </div>
  );
}

function UserMetricsView({ data }: { data: UserMetrics }) {
  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard title="Total Users" value={data.totalUsers} />
        <StatCard title="Active Users" value={data.activeUsers} />
        <StatCard
          title="New This Period"
          value={`+${data.newUsersThisPeriod}`}
        />
        <StatCard
          title="Active Rate"
          value={`${((data.activeUsers / data.totalUsers) * 100).toFixed(1)}%`}
        />
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            Users by Role
          </h3>
          <div className="h-64">
            <PieChart
              data={data.usersByRole}
              nameField="role"
              valueField="count"
            />
          </div>
        </Card>

        <Card className="p-6">
          <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
            User Growth
          </h3>
          <div className="h-64">
            <AreaChart
              data={data.userGrowth}
              xField="date"
              yField="count"
              color="#f59e0b"
            />
          </div>
        </Card>
      </div>

      {/* Top Active Users */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
          Most Active Users
        </h3>
        <ResponsiveTable
          data={data.topActiveUsers}
          getRowKey={(user) => user.id}
          columns={[
            {
              header: "User",
              accessor: "name",
            },
            {
              header: "Lessons Completed",
              accessor: "lessonsCompleted",
            },
          ]}
          mobileCardRenderer={(user) => (
            <div className="flex justify-between items-center">
              <span className="font-medium text-gray-900 dark:text-white">
                {user.name}
              </span>
              <span className="text-sm text-gray-500">
                {user.lessonsCompleted} lessons
              </span>
            </div>
          )}
        />
      </Card>
    </div>
  );
}

function PerformanceMetricsView({ data }: { data: PerformanceMetrics }) {
  const getStatusColor = (value: number, thresholds: [number, number]) => {
    if (value <= thresholds[0]) return "text-green-600";
    if (value <= thresholds[1]) return "text-yellow-600";
    return "text-red-600";
  };

  return (
    <div className="space-y-6">
      {/* Stats Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        <Card className="p-6">
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
            Average Load Time
          </p>
          <p
            className={`mt-2 text-3xl font-bold ${getStatusColor(data.averageLoadTime, [1000, 2000])}`}
          >
            {data.averageLoadTime}ms
          </p>
          <p className="mt-1 text-sm text-gray-500">
            {data.averageLoadTime < 1000
              ? "Excellent"
              : data.averageLoadTime < 2000
                ? "Good"
                : "Needs Improvement"}
          </p>
        </Card>

        <Card className="p-6">
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
            Error Rate
          </p>
          <p
            className={`mt-2 text-3xl font-bold ${getStatusColor(data.errorRate, [1, 5])}`}
          >
            {data.errorRate}%
          </p>
          <p className="mt-1 text-sm text-gray-500">
            {data.errorRate < 1
              ? "Excellent"
              : data.errorRate < 5
                ? "Acceptable"
                : "High"}
          </p>
        </Card>

        <Card className="p-6">
          <p className="text-sm font-medium text-gray-500 dark:text-gray-400">
            Sync Success Rate
          </p>
          <p
            className={`mt-2 text-3xl font-bold ${data.syncSuccessRate >= 99 ? "text-green-600" : data.syncSuccessRate >= 95 ? "text-yellow-600" : "text-red-600"}`}
          >
            {data.syncSuccessRate}%
          </p>
        </Card>
      </div>

      {/* API Latency */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
          API Latency
        </h3>
        <div className="grid grid-cols-2 gap-8">
          <div>
            <p className="text-sm text-gray-500">P50 (Median)</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {data.apiLatencyP50}ms
            </p>
          </div>
          <div>
            <p className="text-sm text-gray-500">P99 (Worst 1%)</p>
            <p className="text-2xl font-bold text-gray-900 dark:text-white">
              {data.apiLatencyP99}ms
            </p>
          </div>
        </div>
      </Card>

      {/* Offline Usage */}
      <Card className="p-6">
        <h3 className="text-lg font-semibold mb-4 text-gray-900 dark:text-white">
          Offline Usage
        </h3>
        <div className="flex items-center gap-4">
          <div className="flex-1">
            <div className="h-4 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className="h-full bg-blue-500 rounded-full"
                style={{ width: `${data.offlineUsagePercent}%` }}
              />
            </div>
          </div>
          <p className="text-lg font-semibold text-gray-900 dark:text-white">
            {data.offlineUsagePercent}%
          </p>
        </div>
        <p className="mt-2 text-sm text-gray-500">
          Percentage of sessions with offline content access
        </p>
      </Card>
    </div>
  );
}
