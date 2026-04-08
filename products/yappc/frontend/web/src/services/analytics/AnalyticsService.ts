/**
 * Analytics Service
 *
 * Aggregates project, team, and lifecycle metrics for reporting dashboards.
 * All computations are pure functions operating on typed inputs.
 *
 * @doc.type service
 * @doc.purpose Analytics aggregation and report generation
 * @doc.layer product
 * @doc.pattern Service Layer
 */

// ============================================================================
// Types
// ============================================================================

export interface TimeSeriesPoint {
  date: string;      // ISO date string
  value: number;
}

export interface MetricSummary {
  current: number;
  previous: number;
  change: number;     // percentage change
  trend: 'up' | 'down' | 'flat';
}

export interface ProjectMetrics {
  projectId: string;
  name: string;
  completionRate: number;
  taskCount: number;
  openIssues: number;
  avgCycleTime: number;   // days
  healthScore: number;    // 0-100
}

export interface TeamMetrics {
  memberId: string;
  name: string;
  tasksCompleted: number;
  avgResponseTime: number;
  utilisation: number;     // 0-1
}

export interface LifecycleMetrics {
  phase: string;
  avgDuration: number;     // days
  successRate: number;     // 0-1
  bottleneckScore: number; // 0-1, higher = more bottleneck
}

export interface AnalyticsReport {
  period: { start: string; end: string };
  summary: {
    totalProjects: MetricSummary;
    totalTasks: MetricSummary;
    avgHealthScore: MetricSummary;
    teamUtilisation: MetricSummary;
  };
  projectMetrics: ProjectMetrics[];
  teamMetrics: TeamMetrics[];
  lifecycleMetrics: LifecycleMetrics[];
  timeSeries: {
    tasksCompleted: TimeSeriesPoint[];
    healthScore: TimeSeriesPoint[];
  };
}

export interface AnalyticsRequest {
  projectIds?: string[];
  startDate: string;
  endDate: string;
}

// ============================================================================
// Metric Helpers
// ============================================================================

export function calculateChange(current: number, previous: number): MetricSummary {
  const change = previous === 0 ? 0 : ((current - previous) / previous) * 100;
  const trend: MetricSummary['trend'] =
    Math.abs(change) < 1 ? 'flat' : change > 0 ? 'up' : 'down';

  return { current, previous, change: Math.round(change * 10) / 10, trend };
}

export function calculateHealthScore(
  completionRate: number,
  openIssues: number,
  avgCycleTime: number,
): number {
  const completionScore = completionRate * 40;
  const issueScore = Math.max(0, 30 - openIssues * 3);
  const cycleScore = Math.max(0, 30 - avgCycleTime * 2);
  return Math.min(100, Math.round(completionScore + issueScore + cycleScore));
}

export function identifyBottleneck(lifecycleMetrics: LifecycleMetrics[]): string | null {
  if (lifecycleMetrics.length === 0) return null;
  const worst = [...lifecycleMetrics].sort((a, b) => b.bottleneckScore - a.bottleneckScore)[0];
  return worst.bottleneckScore > 0.5 ? worst.phase : null;
}

// ============================================================================
// Report Generation
// ============================================================================

export async function generateReport(request: AnalyticsRequest): Promise<AnalyticsReport> {
  // In production this would call the backend; here we build the shape.
  const { startDate, endDate } = request;

  return {
    period: { start: startDate, end: endDate },
    summary: {
      totalProjects: calculateChange(0, 0),
      totalTasks: calculateChange(0, 0),
      avgHealthScore: calculateChange(0, 0),
      teamUtilisation: calculateChange(0, 0),
    },
    projectMetrics: [],
    teamMetrics: [],
    lifecycleMetrics: [],
    timeSeries: {
      tasksCompleted: [],
      healthScore: [],
    },
  };
}

export function aggregateProjectMetrics(projects: ProjectMetrics[]): MetricSummary {
  const avg = projects.length > 0
    ? projects.reduce((s, p) => s + p.healthScore, 0) / projects.length
    : 0;
  return calculateChange(avg, 0);
}

export function aggregateTeamUtilisation(team: TeamMetrics[]): number {
  if (team.length === 0) return 0;
  return team.reduce((s, t) => s + t.utilisation, 0) / team.length;
}
