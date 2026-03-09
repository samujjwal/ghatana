/**
 * VelocityChartsPage
 *
 * @description Velocity and burndown charts dashboard with sprint metrics,
 * team performance analytics, and trend visualizations.
 *
 * @doc.phase 3
 * @doc.route /projects/:projectId/velocity
 */

import React, { useCallback, useMemo, useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { VelocityChart } from '@ghatana/yappc-ui';
import { BurndownChart } from '@ghatana/yappc-ui';
import { Spinner as LoadingSpinner } from '@ghatana/ui';
import { ErrorBoundary } from '@ghatana/ui';

// ============================================================================
// Types
// ============================================================================

interface SprintSummary {
  id: string;
  name: string;
  number: number;
  startDate: string;
  endDate: string;
  committedPoints: number;
  completedPoints: number;
  storiesCommitted: number;
  storiesCompleted: number;
  isActive?: boolean;
}

interface BurndownDataPoint {
  date: string;
  ideal: number;
  actual: number;
  scope: number;
}

interface VelocityData {
  sprints: SprintSummary[];
  averageVelocity: number;
  trend: number; // percentage change
}

interface TeamMetrics {
  averageCycleTime: number; // days
  averageLeadTime: number; // days
  throughput: number; // stories per sprint
  bugEscapeRate: number; // percentage
  defectDensity: number; // bugs per story point
  predictability: number; // percentage
}

interface DistributionData {
  label: string;
  count: number;
  percentage: number;
  color: string;
}

// ============================================================================
// API Functions
// ============================================================================

const fetchVelocityData = async (projectId: string): Promise<VelocityData> => {
  const response = await fetch(`/api/projects/${projectId}/velocity`);
  if (!response.ok) throw new Error('Failed to fetch velocity data');
  return response.json();
};

const fetchBurndownData = async (
  projectId: string,
  sprintId: string
): Promise<BurndownDataPoint[]> => {
  const response = await fetch(
    `/api/projects/${projectId}/sprints/${sprintId}/burndown`
  );
  if (!response.ok) throw new Error('Failed to fetch burndown data');
  return response.json();
};

const fetchTeamMetrics = async (projectId: string): Promise<TeamMetrics> => {
  const response = await fetch(`/api/projects/${projectId}/team-metrics`);
  if (!response.ok) throw new Error('Failed to fetch team metrics');
  return response.json();
};

const fetchStoryDistribution = async (projectId: string): Promise<DistributionData[]> => {
  const response = await fetch(`/api/projects/${projectId}/story-distribution`);
  if (!response.ok) throw new Error('Failed to fetch distribution');
  return response.json();
};

// ============================================================================
// Utility Functions
// ============================================================================

const formatNumber = (num: number, decimals = 1): string => {
  if (Number.isInteger(num)) return num.toString();
  return num.toFixed(decimals);
};

const getTrendIcon = (trend: number): string => {
  if (trend > 0) return '📈';
  if (trend < 0) return '📉';
  return '➡️';
};

const getTrendColor = (trend: number): string => {
  if (trend > 0) return '#10B981';
  if (trend < 0) return '#EF4444';
  return '#6B7280';
};

// ============================================================================
// Sub-Components
// ============================================================================

interface MetricCardProps {
  label: string;
  value: string | number;
  unit?: string;
  trend?: number;
  icon?: string;
  description?: string;
}

const MetricCard: React.FC<MetricCardProps> = ({
  label,
  value,
  unit,
  trend,
  icon,
  description,
}) => (
  <div className="metric-card">
    {icon && <span className="metric-icon">{icon}</span>}
    <span className="metric-value">
      {value}
      {unit && <span className="metric-unit">{unit}</span>}
    </span>
    <span className="metric-label">{label}</span>
    {trend !== undefined && (
      <span className="metric-trend" style={{ color: getTrendColor(trend) }}>
        {getTrendIcon(trend)} {Math.abs(trend)}%
      </span>
    )}
    {description && <span className="metric-description">{description}</span>}
  </div>
);

interface DistributionBarProps {
  data: DistributionData[];
  title: string;
}

const DistributionBar: React.FC<DistributionBarProps> = ({ data, title }) => (
  <div className="distribution-bar">
    <h4 className="distribution-title">{title}</h4>
    <div className="distribution-track">
      {data.map((item) => (
        <div
          key={item.label}
          className="distribution-segment"
          style={{
            width: `${item.percentage}%`,
            background: item.color,
          }}
          title={`${item.label}: ${item.count} (${item.percentage}%)`}
        />
      ))}
    </div>
    <div className="distribution-legend">
      {data.map((item) => (
        <div key={item.label} className="legend-item">
          <span className="legend-dot" style={{ background: item.color }} />
          <span className="legend-label">{item.label}</span>
          <span className="legend-value">{item.count}</span>
        </div>
      ))}
    </div>
  </div>
);

interface SprintTableProps {
  sprints: SprintSummary[];
  onSprintSelect: (sprintId: string) => void;
  selectedSprintId: string | null;
}

const SprintTable: React.FC<SprintTableProps> = ({
  sprints,
  onSprintSelect,
  selectedSprintId,
}) => (
  <div className="sprint-table">
    <table>
      <thead>
        <tr>
          <th>Sprint</th>
          <th>Dates</th>
          <th>Committed</th>
          <th>Completed</th>
          <th>Accuracy</th>
        </tr>
      </thead>
      <tbody>
        {sprints.map((sprint) => {
          const accuracy =
            sprint.committedPoints > 0
              ? Math.round((sprint.completedPoints / sprint.committedPoints) * 100)
              : 0;
          const isSelected = selectedSprintId === sprint.id;
          return (
            <tr
              key={sprint.id}
              className={`sprint-row ${isSelected ? 'sprint-row--selected' : ''} ${sprint.isActive ? 'sprint-row--active' : ''}`}
              onClick={() => onSprintSelect(sprint.id)}
            >
              <td className="sprint-name">
                {sprint.name}
                {sprint.isActive && <span className="active-badge">Current</span>}
              </td>
              <td className="sprint-dates">
                {new Date(sprint.startDate).toLocaleDateString()} -{' '}
                {new Date(sprint.endDate).toLocaleDateString()}
              </td>
              <td className="sprint-committed">{sprint.committedPoints} pts</td>
              <td className="sprint-completed">{sprint.completedPoints} pts</td>
              <td className="sprint-accuracy">
                <span
                  className="accuracy-badge"
                  data-level={accuracy >= 90 ? 'high' : accuracy >= 70 ? 'medium' : 'low'}
                >
                  {accuracy}%
                </span>
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  </div>
);

// ============================================================================
// Main Component
// ============================================================================

export const VelocityChartsPage: React.FC = () => {
  const { projectId } = useParams<{ projectId: string }>();

  // State
  const [velocityData, setVelocityData] = useState<VelocityData | null>(null);
  const [burndownData, setBurndownData] = useState<BurndownDataPoint[]>([]);
  const [teamMetrics, setTeamMetrics] = useState<TeamMetrics | null>(null);
  const [distribution, setDistribution] = useState<DistributionData[]>([]);
  const [selectedSprintId, setSelectedSprintId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [chartView, setChartView] = useState<'velocity' | 'burndown'>('velocity');

  // Load initial data
  useEffect(() => {
    const loadData = async () => {
      if (!projectId) return;

      setLoading(true);
      setError(null);

      try {
        const [velocity, metrics, dist] = await Promise.all([
          fetchVelocityData(projectId),
          fetchTeamMetrics(projectId),
          fetchStoryDistribution(projectId),
        ]);

        setVelocityData(velocity);
        setTeamMetrics(metrics);
        setDistribution(dist);

        // Auto-select active sprint or most recent
        const activeSprint = velocity.sprints.find((s) => s.isActive);
        if (activeSprint) {
          setSelectedSprintId(activeSprint.id);
        } else if (velocity.sprints.length > 0) {
          setSelectedSprintId(velocity.sprints[0].id);
        }
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load data');
      } finally {
        setLoading(false);
      }
    };

    loadData();
  }, [projectId]);

  // Load burndown data when sprint changes
  useEffect(() => {
    const loadBurndown = async () => {
      if (!projectId || !selectedSprintId) return;

      try {
        const burndown = await fetchBurndownData(projectId, selectedSprintId);
        setBurndownData(burndown);
      } catch (err) {
        console.error('Failed to load burndown:', err);
        setBurndownData([]);
      }
    };

    loadBurndown();
  }, [projectId, selectedSprintId]);

  // Calculate derived metrics
  const completionRate = useMemo(() => {
    if (!velocityData?.sprints.length) return 0;
    const totalCommitted = velocityData.sprints.reduce(
      (sum, s) => sum + s.committedPoints,
      0
    );
    const totalCompleted = velocityData.sprints.reduce(
      (sum, s) => sum + s.completedPoints,
      0
    );
    return totalCommitted > 0 ? Math.round((totalCompleted / totalCommitted) * 100) : 0;
  }, [velocityData]);

  const selectedSprint = useMemo(() => {
    if (!velocityData || !selectedSprintId) return null;
    return velocityData.sprints.find((s) => s.id === selectedSprintId) || null;
  }, [velocityData, selectedSprintId]);

  // Handlers
  const handleSprintSelect = useCallback((sprintId: string) => {
    setSelectedSprintId(sprintId);
  }, []);

  if (loading) {
    return (
      <div className="velocity-page velocity-page--loading">
        <LoadingSpinner message="Loading metrics..." />
      </div>
    );
  }

  if (error || !velocityData) {
    return (
      <div className="velocity-page velocity-page--error">
        <div className="error-container">
          <h2>Failed to load metrics</h2>
          <p>{error || 'Data not found'}</p>
          <button onClick={() => window.location.reload()}>Retry</button>
        </div>
      </div>
    );
  }

  return (
    <ErrorBoundary>
      <div className="velocity-page">
        {/* Header */}
        <header className="page-header">
          <h1 className="page-title">📊 Velocity & Metrics</h1>
        </header>

        {/* Key Metrics */}
        <section className="metrics-section">
          <div className="metrics-grid">
            <MetricCard
              label="Average Velocity"
              value={formatNumber(velocityData.averageVelocity)}
              unit=" pts"
              trend={velocityData.trend}
              icon="🚀"
            />
            <MetricCard
              label="Completion Rate"
              value={completionRate}
              unit="%"
              icon="✅"
            />
            {teamMetrics && (
              <>
                <MetricCard
                  label="Avg Cycle Time"
                  value={formatNumber(teamMetrics.averageCycleTime)}
                  unit=" days"
                  icon="⏱️"
                  description="Time to complete a story"
                />
                <MetricCard
                  label="Predictability"
                  value={formatNumber(teamMetrics.predictability)}
                  unit="%"
                  icon="🎯"
                  description="Commitment accuracy"
                />
                <MetricCard
                  label="Throughput"
                  value={formatNumber(teamMetrics.throughput)}
                  unit="/sprint"
                  icon="📈"
                  description="Stories per sprint"
                />
                <MetricCard
                  label="Bug Escape Rate"
                  value={formatNumber(teamMetrics.bugEscapeRate)}
                  unit="%"
                  icon="🐛"
                  description="Post-release defects"
                />
              </>
            )}
          </div>
        </section>

        {/* Chart Toggle */}
        <div className="chart-toggle">
          <button
            type="button"
            className={`toggle-btn ${chartView === 'velocity' ? 'toggle-btn--active' : ''}`}
            onClick={() => setChartView('velocity')}
          >
            Velocity Chart
          </button>
          <button
            type="button"
            className={`toggle-btn ${chartView === 'burndown' ? 'toggle-btn--active' : ''}`}
            onClick={() => setChartView('burndown')}
          >
            Burndown Chart
          </button>
        </div>

        {/* Charts */}
        <section className="charts-section">
          {chartView === 'velocity' ? (
            <div className="chart-container">
              <div className="chart-header">
                <h2 className="chart-title">Sprint Velocity</h2>
                <span className="chart-subtitle">
                  Points completed per sprint (last {velocityData.sprints.length} sprints)
                </span>
              </div>
              <VelocityChart
                data={velocityData.sprints.map((s) => ({
                  sprintName: s.name,
                  committed: s.committedPoints,
                  completed: s.completedPoints,
                }))}
                averageVelocity={velocityData.averageVelocity}
                height={350}
              />
            </div>
          ) : (
            <div className="chart-container">
              <div className="chart-header">
                <h2 className="chart-title">
                  Sprint Burndown {selectedSprint && `- ${selectedSprint.name}`}
                </h2>
                <span className="chart-subtitle">
                  Remaining work vs ideal trajectory
                </span>
              </div>
              {selectedSprint && burndownData.length > 0 ? (
                <BurndownChart
                  data={burndownData}
                  totalPoints={selectedSprint.committedPoints}
                  height={350}
                />
              ) : (
                <div className="chart-empty">
                  <p>Select a sprint to view burndown</p>
                </div>
              )}
            </div>
          )}
        </section>

        {/* Distribution */}
        {distribution.length > 0 && (
          <section className="distribution-section">
            <DistributionBar data={distribution} title="Story Type Distribution" />
          </section>
        )}

        {/* Sprint History Table */}
        <section className="history-section">
          <h2 className="section-title">Sprint History</h2>
          <SprintTable
            sprints={velocityData.sprints}
            onSprintSelect={handleSprintSelect}
            selectedSprintId={selectedSprintId}
          />
        </section>

        {/* CSS-in-JS Styles */}
        <style>{`
          .velocity-page {
            min-height: 100vh;
            background: #F9FAFB;
            padding: 1.5rem 2rem;
          }

          .velocity-page--loading,
          .velocity-page--error {
            display: flex;
            align-items: center;
            justify-content: center;
          }

          .error-container {
            text-align: center;
            padding: 2rem;
          }

          .error-container h2 {
            margin: 0 0 0.5rem;
            color: #111827;
          }

          .error-container p {
            margin: 0 0 1rem;
            color: #6B7280;
          }

          .error-container button {
            padding: 0.5rem 1rem;
            background: #3B82F6;
            color: #fff;
            border: none;
            border-radius: 6px;
            cursor: pointer;
          }

          .page-header {
            margin-bottom: 1.5rem;
          }

          .page-title {
            margin: 0;
            font-size: 1.5rem;
            font-weight: 700;
            color: #111827;
          }

          .metrics-section {
            margin-bottom: 1.5rem;
          }

          .metrics-grid {
            display: grid;
            grid-template-columns: repeat(6, 1fr);
            gap: 1rem;
          }

          .metric-card {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            padding: 1rem;
            display: flex;
            flex-direction: column;
            align-items: center;
            text-align: center;
          }

          .metric-icon {
            font-size: 1.5rem;
            margin-bottom: 0.5rem;
          }

          .metric-value {
            font-size: 1.5rem;
            font-weight: 700;
            color: #111827;
          }

          .metric-unit {
            font-size: 0.875rem;
            font-weight: 400;
            color: #6B7280;
          }

          .metric-label {
            font-size: 0.8125rem;
            font-weight: 500;
            color: #6B7280;
            margin-top: 0.25rem;
          }

          .metric-trend {
            font-size: 0.75rem;
            font-weight: 600;
            margin-top: 0.25rem;
          }

          .metric-description {
            font-size: 0.6875rem;
            color: #9CA3AF;
            margin-top: 0.25rem;
          }

          .chart-toggle {
            display: flex;
            gap: 0.5rem;
            margin-bottom: 1rem;
          }

          .toggle-btn {
            padding: 0.5rem 1.25rem;
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 8px;
            font-size: 0.875rem;
            font-weight: 500;
            color: #6B7280;
            cursor: pointer;
            transition: all 0.15s ease;
          }

          .toggle-btn:hover {
            background: #F9FAFB;
            color: #111827;
          }

          .toggle-btn--active {
            background: #3B82F6;
            border-color: #3B82F6;
            color: #fff;
          }

          .toggle-btn--active:hover {
            background: #2563EB;
            color: #fff;
          }

          .charts-section {
            margin-bottom: 1.5rem;
          }

          .chart-container {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            padding: 1.5rem;
          }

          .chart-header {
            margin-bottom: 1rem;
          }

          .chart-title {
            margin: 0 0 0.25rem;
            font-size: 1rem;
            font-weight: 600;
            color: #111827;
          }

          .chart-subtitle {
            font-size: 0.8125rem;
            color: #6B7280;
          }

          .chart-empty {
            display: flex;
            align-items: center;
            justify-content: center;
            height: 350px;
            color: #6B7280;
          }

          .distribution-section {
            margin-bottom: 1.5rem;
          }

          .distribution-bar {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            padding: 1.5rem;
          }

          .distribution-title {
            margin: 0 0 1rem;
            font-size: 1rem;
            font-weight: 600;
            color: #111827;
          }

          .distribution-track {
            display: flex;
            height: 24px;
            border-radius: 12px;
            overflow: hidden;
            margin-bottom: 0.75rem;
          }

          .distribution-segment {
            min-width: 4px;
            transition: all 0.2s ease;
          }

          .distribution-segment:hover {
            opacity: 0.8;
          }

          .distribution-legend {
            display: flex;
            flex-wrap: wrap;
            gap: 1rem;
          }

          .legend-item {
            display: flex;
            align-items: center;
            gap: 0.5rem;
            font-size: 0.8125rem;
          }

          .legend-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
          }

          .legend-label {
            color: #374151;
          }

          .legend-value {
            color: #9CA3AF;
          }

          .history-section {
            background: #fff;
            border: 1px solid #E5E7EB;
            border-radius: 12px;
            padding: 1.5rem;
          }

          .section-title {
            margin: 0 0 1rem;
            font-size: 1rem;
            font-weight: 600;
            color: #111827;
          }

          .sprint-table {
            overflow-x: auto;
          }

          .sprint-table table {
            width: 100%;
            border-collapse: collapse;
          }

          .sprint-table th,
          .sprint-table td {
            padding: 0.75rem 1rem;
            text-align: left;
            border-bottom: 1px solid #E5E7EB;
          }

          .sprint-table th {
            font-size: 0.75rem;
            font-weight: 600;
            color: #6B7280;
            text-transform: uppercase;
            letter-spacing: 0.05em;
          }

          .sprint-table td {
            font-size: 0.875rem;
            color: #374151;
          }

          .sprint-row {
            cursor: pointer;
            transition: background 0.15s ease;
          }

          .sprint-row:hover {
            background: #F9FAFB;
          }

          .sprint-row--selected {
            background: #EFF6FF;
          }

          .sprint-row--active {
            font-weight: 600;
          }

          .sprint-name {
            display: flex;
            align-items: center;
            gap: 0.5rem;
          }

          .active-badge {
            padding: 0.125rem 0.5rem;
            background: #3B82F6;
            color: #fff;
            font-size: 0.6875rem;
            font-weight: 600;
            border-radius: 20px;
          }

          .sprint-dates {
            color: #6B7280;
          }

          .accuracy-badge {
            padding: 0.25rem 0.5rem;
            border-radius: 6px;
            font-size: 0.75rem;
            font-weight: 600;
          }

          .accuracy-badge[data-level="high"] {
            background: #D1FAE5;
            color: #065F46;
          }

          .accuracy-badge[data-level="medium"] {
            background: #FEF3C7;
            color: #92400E;
          }

          .accuracy-badge[data-level="low"] {
            background: #FEE2E2;
            color: #991B1B;
          }

          @media (max-width: 1200px) {
            .metrics-grid {
              grid-template-columns: repeat(3, 1fr);
            }
          }

          @media (max-width: 768px) {
            .metrics-grid {
              grid-template-columns: repeat(2, 1fr);
            }

            .chart-toggle {
              flex-wrap: wrap;
            }
          }
        `}</style>
      </div>
    </ErrorBoundary>
  );
};

VelocityChartsPage.displayName = 'VelocityChartsPage';

export default VelocityChartsPage;
