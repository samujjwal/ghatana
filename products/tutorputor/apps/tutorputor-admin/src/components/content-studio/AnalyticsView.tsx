/**
 * Analytics View
 *
 * Analytics view for the Unified Content Studio.
 *
 * @doc.type component
 * @doc.purpose Analytics for content studio
 * @doc.layer product
 * @doc.pattern View Component
 */

import {
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from "recharts";

export interface ContentCreationStats {
  totalContent: number;
  byType: {
    examples: number;
    simulations: number;
    animations: number;
    assessments: number;
    explanations: number;
  };
  byDomain: {
    physics: number;
    chemistry: number;
    biology: number;
    mathematics: number;
    computerScience: number;
    economics: number;
    medicine: number;
    general: number;
  };
  qualityDistribution: {
    excellent: number;
    good: number;
    fair: number;
    poor: number;
  };
}

export interface AnalyticsViewProps {
  contentStats: ContentCreationStats | null;
}

export function AnalyticsView({ contentStats }: AnalyticsViewProps) {
  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Content by Domain */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Content by Domain
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <BarChart
              data={
                contentStats
                  ? Object.entries(contentStats.byDomain).map(
                      ([domain, count]) => ({
                        domain:
                          domain.charAt(0).toUpperCase() + domain.slice(1),
                        count,
                      }),
                    )
                  : []
              }
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="domain" />
              <YAxis />
              <Tooltip />
              <Bar dataKey="count" fill="#8b5cf6" />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Quality Distribution */}
        <div className="bg-white dark:bg-gray-800 rounded-lg shadow-sm border border-gray-200 dark:border-gray-700 p-6">
          <h3 className="text-lg font-semibold text-gray-900 dark:text-white mb-4">
            Quality Distribution
          </h3>
          <ResponsiveContainer width="100%" height={300}>
            <PieChart>
              <Pie
                data={
                  contentStats
                    ? [
                        {
                          name: "Excellent",
                          value: contentStats.qualityDistribution.excellent,
                        },
                        {
                          name: "Good",
                          value: contentStats.qualityDistribution.good,
                        },
                        {
                          name: "Fair",
                          value: contentStats.qualityDistribution.fair,
                        },
                        {
                          name: "Poor",
                          value: contentStats.qualityDistribution.poor,
                        },
                      ]
                    : []
                }
                cx="50%"
                cy="50%"
                labelLine={false}
                label={({ name, percent }) =>
                  `${name} ${percent ? (percent * 100).toFixed(0) : 0}%`
                }
                outerRadius={80}
                fill="#8884d8"
                dataKey="value"
              >
                <Cell fill="#10b981" />
                <Cell fill="#3b82f6" />
                <Cell fill="#f59e0b" />
                <Cell fill="#ef4444" />
              </Pie>
              <Tooltip />
            </PieChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}
