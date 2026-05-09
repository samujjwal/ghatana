/**
 * AI Quality Dashboard Component
 *
 * Displays AI quality metrics, confidence scores, and provider health.
 *
 * @doc.type component
 * @doc.purpose AI quality telemetry dashboard
 * @doc.layer product
 * @doc.pattern React Component
 */

import React from 'react';
import {
  Activity,
  TrendingUp,
  AlertTriangle,
  CheckCircle,
  XCircle,
  Clock,
  Database,
  RefreshCw,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent } from '@ghatana/design-system';
import { useAIQuality } from '../../hooks/useAIQuality';
import type { QualitySummary, ModelProvider } from '../../services/ai/types';

// ============================================================================
// Types
// ============================================================================

export interface AIQualityDashboardProps {
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

interface MetricCardProps {
  icon: React.ReactNode;
  label: string;
  value: string | number;
  subtext?: string;
  trend?: 'up' | 'down' | 'neutral';
}

const MetricCard: React.FC<MetricCardProps> = ({ icon, label, value, subtext, trend }) => (
  <Card>
    <CardContent className="p-4">
      <Box className="flex items-center gap-2 mb-2 text-fg-muted">
        {icon}
        <Typography className="text-xs font-medium uppercase tracking-wide">{label}</Typography>
      </Box>
      <Box className="flex items-end justify-between">
        <Typography className="text-2xl font-bold">{value}</Typography>
        {trend && (
          <Box
            className={`text-xs ${
              trend === 'up' ? 'text-success-color' : trend === 'down' ? 'text-destructive' : 'text-fg-muted'
            }`}
          >
            {trend === 'up' ? '↑' : trend === 'down' ? '↓' : '→'}
          </Box>
        )}
      </Box>
      {subtext && (
        <Typography className="text-xs text-fg-muted mt-1">{subtext}</Typography>
      )}
    </CardContent>
  </Card>
);

interface ProviderHealthCardProps {
  provider: ModelProvider;
  healthy: boolean;
  score: number;
  requestCount: number;
}

const ProviderHealthCard: React.FC<ProviderHealthCardProps> = ({
  provider,
  healthy,
  score,
  requestCount,
}) => {
  const providerNames: Record<ModelProvider, string> = {
    openai: 'OpenAI',
    anthropic: 'Anthropic',
    azure: 'Azure OpenAI',
    local: 'Local Model',
  };

  return (
    <Card className="mb-2">
      <CardContent className="p-3">
        <Box className="flex items-center justify-between">
          <Box className="flex items-center gap-2">
            {healthy ? (
              <CheckCircle className="w-4 h-4 text-success-color" />
            ) : (
              <XCircle className="w-4 h-4 text-destructive" />
            )}
            <Typography className="font-medium text-sm">{providerNames[provider]}</Typography>
          </Box>
          <Box className="text-right">
            <Typography className={`text-sm font-medium ${healthy ? 'text-success-color' : 'text-destructive'}`}>
              {Math.round(score * 100)}%
            </Typography>
            <Typography className="text-xs text-fg-muted">{requestCount} reqs</Typography>
          </Box>
        </Box>
      </CardContent>
    </Card>
  );
};

interface ErrorBreakdownProps {
  breakdown: Record<string, number>;
}

const ErrorBreakdown: React.FC<ErrorBreakdownProps> = ({ breakdown }) => {
  const entries = Object.entries(breakdown).filter(([, count]) => count > 0);

  if (entries.length === 0) {
    return (
      <Typography className="text-sm text-fg-muted text-center py-4">
        No errors in the last 24 hours
      </Typography>
    );
  }

  return (
    <Box className="space-y-2">
      {entries.map(([code, count]) => (
        <Box key={code} className="flex items-center justify-between py-1">
          <Box className="flex items-center gap-2">
            <AlertTriangle className="w-3.5 h-3.5 text-warning-color" />
            <Typography className="text-sm">{code}</Typography>
          </Box>
          <Typography className="text-sm font-medium">{count}</Typography>
        </Box>
      ))}
    </Box>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const AIQualityDashboard: React.FC<AIQualityDashboardProps> = ({ className = '' }) => {
  const { summary, getProviderHealth, resetMetrics } = useAIQuality();

  if (!summary) {
    return (
      <Box className={`space-y-4 ${className}`}>
        <Box className="flex items-center justify-between">
          <Box className="flex items-center gap-2">
            <Activity className="w-5 h-5 text-info-color" />
            <Typography className="font-semibold text-lg">Quality Insights</Typography>
          </Box>
        </Box>
        <Typography className="text-sm text-fg-muted text-center py-8">
          No quality insights data available yet. Metrics will appear after guidance requests are made.
        </Typography>
      </Box>
    );
  }

  const providers: ModelProvider[] = ['openai', 'anthropic', 'azure', 'local'];

  return (
    <Box className={`space-y-6 ${className}`}>
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-info-color" />
          <Typography className="font-semibold text-lg">Quality Insights</Typography>
        </Box>
        <Button size="sm" variant="text" onClick={resetMetrics}>
          <RefreshCw className="w-4 h-4" />
        </Button>
      </Box>

      {/* Summary Cards */}
      <Box className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <MetricCard
          icon={<TrendingUp className="w-4 h-4" />}
          label="Confidence"
          value={`${Math.round(summary.averageConfidence * 100)}%`}
          subtext={`${summary.successfulRequests} successful`}
          trend={summary.averageConfidence > 0.8 ? 'up' : 'neutral'}
        />
        <MetricCard
          icon={<Clock className="w-4 h-4" />}
          label="Avg Latency"
          value={`${Math.round(summary.averageLatencyMs)}ms`}
          subtext={`${summary.totalRequests} total requests`}
          trend={summary.averageLatencyMs < 2000 ? 'up' : 'down'}
        />
        <MetricCard
          icon={<Database className="w-4 h-4" />}
          label="Cache Hit Rate"
          value={`${Math.round(summary.cacheHitRate * 100)}%`}
          subtext="Last 24 hours"
        />
        <MetricCard
          icon={<AlertTriangle className="w-4 h-4" />}
          label="Fallback Usage"
          value={`${Math.round(summary.fallbackUsageRate * 100)}%`}
          subtext={`${summary.failedRequests} failed requests`}
          trend={summary.fallbackUsageRate > 0.1 ? 'down' : 'up'}
        />
      </Box>

      {/* Provider Health */}
      <Box>
        <Typography className="text-xs font-medium text-fg-muted mb-2">
          Provider Health (Last Hour)
        </Typography>
        <Card>
          <CardContent className="p-2">
            {providers.map((provider) => {
              const health = getProviderHealth(provider);
              const count = summary.providerDistribution[provider] || 0;
              return (
                <ProviderHealthCard
                  key={provider}
                  provider={provider}
                  healthy={health.healthy}
                  score={health.score}
                  requestCount={count}
                />
              );
            })}
          </CardContent>
        </Card>
      </Box>

      {/* Error Breakdown */}
      {summary.failedRequests > 0 && (
        <Box>
          <Typography className="text-xs font-medium text-fg-muted mb-2">
            Error Breakdown (Last 24 Hours)
          </Typography>
          <Card>
            <CardContent className="p-3">
              <ErrorBreakdown breakdown={summary.errorBreakdown} />
            </CardContent>
          </Card>
        </Box>
      )}
    </Box>
  );
};

export default AIQualityDashboard;
