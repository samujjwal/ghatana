/**
 * SimulationAnalyticsPanel Component
 *
 * Dashboard panel for instructors showing simulation engagement metrics,
 * student progress, and learning patterns.
 *
 * @doc.type component
 * @doc.purpose Display simulation analytics for instructors
 * @doc.layer product
 * @doc.pattern Component
 */

import React, { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { Card, Button } from "@ghatana/design-system";

// =============================================================================
// Local Types (avoiding external contract dependencies)
// =============================================================================

export type TenantId = string;
export type ClassroomId = string;
export type SimulationId = string;
export type SimulationDomain =
  | "CS_DISCRETE"
  | "PHYSICS"
  | "ECONOMICS"
  | "CHEMISTRY"
  | "BIOLOGY"
  | "MEDICINE"
  | "ENGINEERING"
  | "MATHEMATICS";

// =============================================================================
// Component Types
// =============================================================================

export interface SimulationAnalyticsPanelProps {
  /** Tenant ID */
  tenantId: TenantId;
  /** Classroom ID */
  classroomId: ClassroomId;
  /** Optional simulation ID to focus on */
  simulationId?: SimulationId;
  /** Title override */
  title?: string;
}

interface ClassroomSimulationMetrics {
  simulationId: SimulationId;
  simulationTitle: string;
  domain: SimulationDomain;
  totalStudents: number;
  studentsEngaged: number;
  engagementRate: number;
  avgTimeSpentMinutes: number;
  completionRate: number;
  avgCompletionTime: number;
}

interface StudentSimulationProgress {
  userId: string;
  displayName: string;
  simulationsCompleted: number;
  totalTimeMinutes: number;
  lastActivityAt: string;
  riskIndicators: string[];
}

interface DomainBreakdown {
  domain: SimulationDomain;
  sessionCount: number;
  completionRate: number;
  avgTimeMinutes: number;
}

interface LearningPattern {
  pattern: string;
  studentCount: number;
  description: string;
  color: string;
}

// =============================================================================
// API Hooks
// =============================================================================

function useClassroomSimulationMetrics(tenantId: TenantId, classroomId: ClassroomId) {
  return useQuery({
    queryKey: ["simulation-analytics", "classroom", tenantId, classroomId],
    queryFn: async (): Promise<ClassroomSimulationMetrics[]> => {
      const res = await fetch(
        `/api/analytics/simulations/classroom/${classroomId}`
      );
      if (!res.ok) throw new Error("Failed to fetch simulation metrics");
      return res.json();
    },
    staleTime: 60000,
  });
}

function useStudentSimulationProgress(tenantId: TenantId, classroomId: ClassroomId) {
  return useQuery({
    queryKey: ["simulation-analytics", "students", tenantId, classroomId],
    queryFn: async (): Promise<StudentSimulationProgress[]> => {
      const res = await fetch(
        `/api/analytics/simulations/classroom/${classroomId}/students`
      );
      if (!res.ok) throw new Error("Failed to fetch student progress");
      return res.json();
    },
    staleTime: 60000,
  });
}

function useDomainBreakdown(tenantId: TenantId, classroomId: ClassroomId) {
  return useQuery({
    queryKey: ["simulation-analytics", "domains", tenantId, classroomId],
    queryFn: async (): Promise<DomainBreakdown[]> => {
      const res = await fetch(
        `/api/analytics/simulations/classroom/${classroomId}/domains`
      );
      if (!res.ok) throw new Error("Failed to fetch domain breakdown");
      return res.json();
    },
    staleTime: 60000,
  });
}

function useLearningPatterns(tenantId: TenantId, classroomId: ClassroomId) {
  return useQuery({
    queryKey: ["simulation-analytics", "patterns", tenantId, classroomId],
    queryFn: async (): Promise<LearningPattern[]> => {
      const res = await fetch(
        `/api/analytics/simulations/classroom/${classroomId}/patterns`
      );
      if (!res.ok) throw new Error("Failed to fetch learning patterns");
      return res.json();
    },
    staleTime: 60000,
  });
}

// =============================================================================
// Sub-components
// =============================================================================

/**
 * Summary metrics cards.
 */
const MetricsSummary: React.FC<{
  metrics: ClassroomSimulationMetrics[];
  students: StudentSimulationProgress[];
}> = ({ metrics, students }) => {
  const totalEngaged = new Set(students.filter((s) => s.simulationsCompleted > 0)).size;
  const totalStudents = students.length;
  const avgCompletionRate =
    metrics.length > 0
      ? metrics.reduce((sum, m) => sum + m.completionRate, 0) / metrics.length
      : 0;
  const atRiskCount = students.filter((s) => s.riskIndicators.length > 0).length;

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
      <MetricCard
        label="Students Engaged"
        value={`${totalEngaged} / ${totalStudents}`}
        subtext={`${Math.round((totalEngaged / Math.max(totalStudents, 1)) * 100)}% engagement`}
        color="blue"
      />
      <MetricCard
        label="Simulations Active"
        value={metrics.length.toString()}
        subtext="across all domains"
        color="green"
      />
      <MetricCard
        label="Avg Completion Rate"
        value={`${Math.round(avgCompletionRate * 100)}%`}
        subtext="of started simulations"
        color="purple"
      />
      <MetricCard
        label="At-Risk Students"
        value={atRiskCount.toString()}
        subtext="need attention"
        color={atRiskCount > 0 ? "red" : "green"}
      />
    </div>
  );
};

/**
 * Single metric card.
 */
const MetricCard: React.FC<{
  label: string;
  value: string;
  subtext: string;
  color: "blue" | "green" | "purple" | "red" | "yellow";
}> = ({ label, value, subtext, color }) => {
  const colorClasses = {
    blue: "bg-blue-50 dark:bg-blue-900/20 text-blue-600 dark:text-blue-400",
    green: "bg-green-50 dark:bg-green-900/20 text-green-600 dark:text-green-400",
    purple: "bg-purple-50 dark:bg-purple-900/20 text-purple-600 dark:text-purple-400",
    red: "bg-red-50 dark:bg-red-900/20 text-red-600 dark:text-red-400",
    yellow: "bg-yellow-50 dark:bg-yellow-900/20 text-yellow-600 dark:text-yellow-400",
  };

  return (
    <Card className="p-4">
      <div className="text-sm text-gray-600 dark:text-gray-400 mb-1">{label}</div>
      <div className={`text-2xl font-bold ${colorClasses[color]}`}>{value}</div>
      <div className="text-xs text-gray-500 dark:text-gray-500 mt-1">{subtext}</div>
    </Card>
  );
};

/**
 * Simulation engagement table.
 */
const SimulationEngagementTable: React.FC<{
  metrics: ClassroomSimulationMetrics[];
}> = ({ metrics }) => {
  const getDomainIcon = (domain: SimulationDomain) => {
    const icons: Record<SimulationDomain, string> = {
      PHYSICS: "⚛️",
      CHEMISTRY: "🧪",
      BIOLOGY: "🧬",
      MEDICINE: "💊",
      ECONOMICS: "📈",
      CS_DISCRETE: "🔢",
      ENGINEERING: "⚙️",
      MATHEMATICS: "📐",
    };
    return icons[domain] ?? "📊";
  };

  return (
    <Card className="mb-6">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <h3 className="font-semibold text-gray-900 dark:text-gray-100">
          Simulation Engagement
        </h3>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full">
          <thead className="bg-gray-50 dark:bg-gray-800">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                Simulation
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                Engaged
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                Completion
              </th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wider">
                Avg Time
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-200 dark:divide-gray-700">
            {metrics.map((m) => (
              <tr key={m.simulationId} className="hover:bg-gray-50 dark:hover:bg-gray-800/50">
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <span className="text-xl">{getDomainIcon(m.domain)}</span>
                    <div>
                      <div className="font-medium text-gray-900 dark:text-gray-100">
                        {m.simulationTitle}
                      </div>
                      <div className="text-xs text-gray-500 dark:text-gray-400">
                        {m.domain}
                      </div>
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-2">
                    <div className="w-20 h-2 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                      <div
                        className="h-full bg-blue-500"
                        style={{ width: `${m.engagementRate * 100}%` }}
                      />
                    </div>
                    <span className="text-sm text-gray-600 dark:text-gray-400">
                      {m.studentsEngaged}/{m.totalStudents}
                    </span>
                  </div>
                </td>
                <td className="px-4 py-3">
                  <span
                    className={`
                      inline-flex px-2 py-1 text-xs font-medium rounded-full
                      ${
                        m.completionRate >= 0.8
                          ? "bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-200"
                          : m.completionRate >= 0.5
                            ? "bg-yellow-100 dark:bg-yellow-900/30 text-yellow-800 dark:text-yellow-200"
                            : "bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-200"
                      }
                    `}
                  >
                    {Math.round(m.completionRate * 100)}%
                  </span>
                </td>
                <td className="px-4 py-3 text-sm text-gray-600 dark:text-gray-400">
                  {m.avgTimeSpentMinutes.toFixed(1)} min
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </Card>
  );
};

/**
 * At-risk students alert.
 */
const AtRiskStudentsAlert: React.FC<{
  students: StudentSimulationProgress[];
}> = ({ students }) => {
  const atRisk = students.filter((s) => s.riskIndicators.length > 0);

  if (atRisk.length === 0) {
    return (
      <Card className="mb-6 p-4 bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800">
        <div className="flex items-center gap-2 text-green-800 dark:text-green-200">
          <span className="text-xl">✅</span>
          <span className="font-medium">All students are on track</span>
        </div>
      </Card>
    );
  }

  return (
    <Card className="mb-6 border-red-200 dark:border-red-800">
      <div className="p-4 bg-red-50 dark:bg-red-900/20 border-b border-red-200 dark:border-red-800">
        <div className="flex items-center gap-2 text-red-800 dark:text-red-200">
          <span className="text-xl">⚠️</span>
          <span className="font-medium">{atRisk.length} students need attention</span>
        </div>
      </div>
      <div className="p-4 space-y-3">
        {atRisk.slice(0, 5).map((student) => (
          <div
            key={student.userId}
            className="flex items-center justify-between p-3 rounded-lg bg-gray-50 dark:bg-gray-800"
          >
            <div>
              <div className="font-medium text-gray-900 dark:text-gray-100">
                {student.displayName}
              </div>
              <div className="text-xs text-gray-500 dark:text-gray-400">
                {student.riskIndicators.join(" • ")}
              </div>
            </div>
            <Button size="sm" variant="outline">
              View Details
            </Button>
          </div>
        ))}
        {atRisk.length > 5 && (
          <div className="text-center text-sm text-gray-500 dark:text-gray-400">
            +{atRisk.length - 5} more students
          </div>
        )}
      </div>
    </Card>
  );
};

/**
 * Domain breakdown chart.
 */
const DomainBreakdownChart: React.FC<{
  data: DomainBreakdown[];
}> = ({ data }) => {
  const maxSessions = Math.max(...data.map((d) => d.sessionCount), 1);

  const domainColors: Record<SimulationDomain, string> = {
    PHYSICS: "bg-blue-500",
    CHEMISTRY: "bg-green-500",
    BIOLOGY: "bg-purple-500",
    MEDICINE: "bg-red-500",
    ECONOMICS: "bg-yellow-500",
    CS_DISCRETE: "bg-indigo-500",
    ENGINEERING: "bg-orange-500",
    MATHEMATICS: "bg-pink-500",
  };

  return (
    <Card className="mb-6">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <h3 className="font-semibold text-gray-900 dark:text-gray-100">
          Domain Breakdown
        </h3>
      </div>
      <div className="p-4 space-y-4">
        {data.map((d) => (
          <div key={d.domain} className="space-y-1">
            <div className="flex justify-between text-sm">
              <span className="font-medium text-gray-700 dark:text-gray-300">
                {d.domain}
              </span>
              <span className="text-gray-500 dark:text-gray-400">
                {d.sessionCount} sessions
              </span>
            </div>
            <div className="h-3 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className={`h-full ${domainColors[d.domain]} rounded-full transition-all`}
                style={{ width: `${(d.sessionCount / maxSessions) * 100}%` }}
              />
            </div>
            <div className="flex justify-between text-xs text-gray-500 dark:text-gray-400">
              <span>{Math.round(d.completionRate * 100)}% completion</span>
              <span>{d.avgTimeMinutes.toFixed(1)} min avg</span>
            </div>
          </div>
        ))}
      </div>
    </Card>
  );
};

/**
 * Learning patterns visualization.
 */
const LearningPatternsChart: React.FC<{
  patterns: LearningPattern[];
}> = ({ patterns }) => {
  const total = patterns.reduce((sum, p) => sum + p.studentCount, 0);

  return (
    <Card className="mb-6">
      <div className="p-4 border-b border-gray-200 dark:border-gray-700">
        <h3 className="font-semibold text-gray-900 dark:text-gray-100">
          Learning Patterns
        </h3>
      </div>
      <div className="p-4">
        {/* Stacked bar */}
        <div className="h-8 flex rounded-lg overflow-hidden mb-4">
          {patterns.map((p, i) => (
            <div
              key={p.pattern}
              className="transition-all"
              style={{
                width: `${(p.studentCount / Math.max(total, 1)) * 100}%`,
                backgroundColor: p.color || ["#3B82F6", "#10B981", "#F59E0B", "#EF4444"][i % 4],
              }}
              title={`${p.pattern}: ${p.studentCount} students`}
            />
          ))}
        </div>

        {/* Legend */}
        <div className="space-y-2">
          {patterns.map((p, i) => (
            <div key={p.pattern} className="flex items-center gap-3">
              <div
                className="w-3 h-3 rounded-full"
                style={{
                  backgroundColor: p.color || ["#3B82F6", "#10B981", "#F59E0B", "#EF4444"][i % 4],
                }}
              />
              <div className="flex-1">
                <div className="flex justify-between">
                  <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
                    {p.pattern.replace(/_/g, " ").replace(/\b\w/g, (l) => l.toUpperCase())}
                  </span>
                  <span className="text-sm text-gray-500 dark:text-gray-400">
                    {p.studentCount} students ({Math.round((p.studentCount / Math.max(total, 1)) * 100)}%)
                  </span>
                </div>
                <div className="text-xs text-gray-500 dark:text-gray-400">
                  {p.description}
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>
    </Card>
  );
};

// =============================================================================
// Main Component
// =============================================================================

export const SimulationAnalyticsPanel: React.FC<SimulationAnalyticsPanelProps> = ({
  tenantId,
  classroomId,
  simulationId,
  title = "Simulation Analytics",
}) => {
  const { data: metrics = [], isLoading: metricsLoading } = useClassroomSimulationMetrics(
    tenantId,
    classroomId
  );
  const { data: students = [], isLoading: studentsLoading } = useStudentSimulationProgress(
    tenantId,
    classroomId
  );
  const { data: domains = [], isLoading: domainsLoading } = useDomainBreakdown(
    tenantId,
    classroomId
  );
  const { data: patterns = [], isLoading: patternsLoading } = useLearningPatterns(
    tenantId,
    classroomId
  );

  const isLoading = metricsLoading || studentsLoading || domainsLoading || patternsLoading;

  // Filter by simulation if specified
  const filteredMetrics = useMemo(() => {
    if (!simulationId) return metrics;
    return metrics.filter((m) => m.simulationId === simulationId);
  }, [metrics, simulationId]);

  if (isLoading) {
    return (
      <div className="animate-pulse space-y-4">
        <div className="h-24 bg-gray-200 dark:bg-gray-700 rounded-lg" />
        <div className="h-64 bg-gray-200 dark:bg-gray-700 rounded-lg" />
        <div className="h-48 bg-gray-200 dark:bg-gray-700 rounded-lg" />
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-bold text-gray-900 dark:text-gray-100">{title}</h2>
        <Button variant="outline" size="sm">
          <DownloadIcon className="h-4 w-4 mr-2" />
          Export Report
        </Button>
      </div>

      {/* Summary metrics */}
      <MetricsSummary metrics={filteredMetrics} students={students} />

      {/* At-risk students */}
      <AtRiskStudentsAlert students={students} />

      {/* Two column layout */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Domain breakdown */}
        <DomainBreakdownChart data={domains} />

        {/* Learning patterns */}
        <LearningPatternsChart patterns={patterns} />
      </div>

      {/* Simulation engagement table */}
      <SimulationEngagementTable metrics={filteredMetrics} />
    </div>
  );
};

// =============================================================================
// Icons
// =============================================================================

const DownloadIcon: React.FC<{ className?: string }> = ({ className }) => (
  <svg className={className} fill="none" viewBox="0 0 24 24" stroke="currentColor">
    <path
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth={2}
      d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4"
    />
  </svg>
);

export default SimulationAnalyticsPanel;
