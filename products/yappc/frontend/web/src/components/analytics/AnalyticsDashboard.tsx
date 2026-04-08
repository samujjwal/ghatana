/**
 * Analytics Dashboard Component
 *
 * Displays project, team, and lifecycle metrics with trend indicators.
 * Follows the RecommendationCard composition pattern from dcmaar.
 *
 * @doc.type component
 * @doc.purpose Comprehensive analytics dashboard
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { type ReactNode } from 'react';
import {
  BarChart3,
  TrendingUp,
  TrendingDown,
  Minus,
  Users,
  FolderKanban,
  AlertTriangle,
  Activity,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent } from '@ghatana/design-system';
import type {
  AnalyticsReport,
  MetricSummary,
  ProjectMetrics,
  TeamMetrics,
  LifecycleMetrics,
} from '../../services/analytics/AnalyticsService';

// ============================================================================
// Types
// ============================================================================

export interface AnalyticsDashboardProps {
  report: AnalyticsReport;
  bottleneck: string | null;
  onProjectClick?: (projectId: string) => void;
  onRefresh?: () => void;
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

interface TrendIconProps {
  trend: MetricSummary['trend'];
}

const TrendIcon: React.FC<TrendIconProps> = ({ trend }) => {
  if (trend === 'up') return <TrendingUp className="w-4 h-4 text-green-600" />;
  if (trend === 'down') return <TrendingDown className="w-4 h-4 text-red-600" />;
  return <Minus className="w-4 h-4 text-gray-400" />;
};

interface MetricCardProps {
  icon: ReactNode;
  label: string;
  metric: MetricSummary;
  format?: (v: number) => string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  icon,
  label,
  metric,
  format = (v) => String(Math.round(v)),
}) => (
  <Card>
    <CardContent className="p-4">
      <Box className="flex items-center gap-2 mb-2 text-gray-500">
        {icon}
        <Typography className="text-xs font-medium uppercase tracking-wide">{label}</Typography>
      </Box>
      <Box className="flex items-end justify-between">
        <Typography className="text-2xl font-bold">{format(metric.current)}</Typography>
        <Box className="flex items-center gap-1">
          <TrendIcon trend={metric.trend} />
          <Typography
            className={`text-xs font-medium ${
              metric.trend === 'up'
                ? 'text-green-600'
                : metric.trend === 'down'
                  ? 'text-red-600'
                  : 'text-gray-400'
            }`}
          >
            {metric.change > 0 ? '+' : ''}{metric.change}%
          </Typography>
        </Box>
      </Box>
    </CardContent>
  </Card>
);

interface ProjectRowProps {
  project: ProjectMetrics;
  onClick?: () => void;
}

const ProjectRow: React.FC<ProjectRowProps> = ({ project, onClick }) => {
  const healthColor =
    project.healthScore >= 70
      ? 'bg-green-500'
      : project.healthScore >= 40
        ? 'bg-orange-500'
        : 'bg-red-500';

  return (
    <Box
      className="flex items-center justify-between py-2 px-3 hover:bg-gray-50 dark:hover:bg-gray-800 rounded cursor-pointer"
      onClick={onClick}
    >
      <Box className="flex items-center gap-3 min-w-0">
        <Box className={`w-2 h-2 rounded-full ${healthColor}`} />
        <Typography className="text-sm font-medium truncate">{project.name}</Typography>
      </Box>
      <Box className="flex items-center gap-4 text-xs text-gray-500">
        <span>{Math.round(project.completionRate * 100)}%</span>
        <span>{project.openIssues} issues</span>
        <span>{project.avgCycleTime}d cycle</span>
        <span className="font-medium">{project.healthScore}</span>
      </Box>
    </Box>
  );
};

interface TeamRowProps {
  member: TeamMetrics;
}

const TeamRow: React.FC<TeamRowProps> = ({ member }) => {
  const utilColor =
    member.utilisation >= 0.85
      ? 'text-red-600'
      : member.utilisation >= 0.6
        ? 'text-green-600'
        : 'text-orange-500';

  return (
    <Box className="flex items-center justify-between py-2 px-3">
      <Typography className="text-sm">{member.name}</Typography>
      <Box className="flex items-center gap-4 text-xs text-gray-500">
        <span>{member.tasksCompleted} done</span>
        <span>{member.avgResponseTime}h resp</span>
        <span className={`font-medium ${utilColor}`}>
          {Math.round(member.utilisation * 100)}%
        </span>
      </Box>
    </Box>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const AnalyticsDashboard: React.FC<AnalyticsDashboardProps> = ({
  report,
  bottleneck,
  onProjectClick,
  onRefresh,
  className = '',
}) => {
  const { summary, projectMetrics, teamMetrics, lifecycleMetrics } = report;

  return (
    <Box className={`space-y-6 ${className}`}>
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <BarChart3 className="w-5 h-5 text-blue-600" />
          <Typography className="font-semibold text-lg">Analytics</Typography>
        </Box>
        {onRefresh && (
          <Button size="sm" variant="outlined" onClick={onRefresh}>
            Refresh
          </Button>
        )}
      </Box>

      {/* Summary Cards */}
      <Box className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          icon={<FolderKanban className="w-4 h-4" />}
          label="Projects"
          metric={summary.totalProjects}
        />
        <MetricCard
          icon={<Activity className="w-4 h-4" />}
          label="Tasks"
          metric={summary.totalTasks}
        />
        <MetricCard
          icon={<TrendingUp className="w-4 h-4" />}
          label="Health"
          metric={summary.avgHealthScore}
        />
        <MetricCard
          icon={<Users className="w-4 h-4" />}
          label="Utilisation"
          metric={summary.teamUtilisation}
          format={(v) => `${Math.round(v)}%`}
        />
      </Box>

      {/* Bottleneck Alert */}
      {bottleneck && (
        <Card className="border-l-4 border-orange-400 bg-orange-50 dark:bg-orange-900/20">
          <CardContent className="p-3 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4 text-orange-500" />
            <Typography className="text-sm">
              Bottleneck detected in <strong>{bottleneck}</strong> phase
            </Typography>
          </CardContent>
        </Card>
      )}

      {/* Projects Table */}
      {projectMetrics.length > 0 && (
        <Box>
          <Typography className="text-xs font-medium text-gray-500 mb-2">
            Projects ({projectMetrics.length})
          </Typography>
          <Card>
            <CardContent className="p-1">
              {projectMetrics.map((p) => (
                <ProjectRow
                  key={p.projectId}
                  project={p}
                  onClick={onProjectClick ? () => onProjectClick(p.projectId) : undefined}
                />
              ))}
            </CardContent>
          </Card>
        </Box>
      )}

      {/* Team Table */}
      {teamMetrics.length > 0 && (
        <Box>
          <Typography className="text-xs font-medium text-gray-500 mb-2">
            Team ({teamMetrics.length})
          </Typography>
          <Card>
            <CardContent className="p-1">
              {teamMetrics.map((m) => (
                <TeamRow key={m.memberId} member={m} />
              ))}
            </CardContent>
          </Card>
        </Box>
      )}
    </Box>
  );
};

export default AnalyticsDashboard;
