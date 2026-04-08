/**
 * Agent Monitor Component
 *
 * Displays real-time multi-agent orchestration status, coordination state,
 * and performance metrics. Follows RecommendationCard composition pattern.
 *
 * @doc.type component
 * @doc.purpose Multi-agent monitoring and orchestration UI
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { type ReactNode } from 'react';
import {
  Bot,
  Activity,
  CheckCircle,
  AlertTriangle,
  XCircle,
  Clock,
  Zap,
  RefreshCw,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent } from '@ghatana/design-system';

// ============================================================================
// Types
// ============================================================================

export type AgentStatus = 'idle' | 'running' | 'completed' | 'failed' | 'waiting';

export interface AgentInfo {
  id: string;
  name: string;
  type: string;
  status: AgentStatus;
  confidence: number;
  currentTask?: string;
  completedTasks: number;
  totalTasks: number;
  lastActivity: string;
  metrics: {
    avgResponseTime: number;
    successRate: number;
    tokensUsed: number;
  };
}

export interface AgentConflict {
  id: string;
  agentIds: [string, string];
  description: string;
  severity: 'low' | 'medium' | 'high';
  resolvedAt?: string;
}

export interface OrchestrationState {
  agents: AgentInfo[];
  conflicts: AgentConflict[];
  overallProgress: number;
  startedAt: string;
  estimatedCompletion?: string;
}

export interface AgentMonitorProps {
  orchestration: OrchestrationState;
  onRetryAgent?: (agentId: string) => void;
  onResolveConflict?: (conflictId: string) => void;
  onStopAgent?: (agentId: string) => void;
  className?: string;
}

// ============================================================================
// Sub-components
// ============================================================================

interface AgentStatusBadgeProps {
  status: AgentStatus;
}

const AgentStatusBadge: React.FC<AgentStatusBadgeProps> = ({ status }) => {
  const config: Record<AgentStatus, { icon: ReactNode; color: string; label: string }> = {
    idle: { icon: <Clock className="w-3.5 h-3.5" />, color: 'text-gray-500', label: 'Idle' },
    running: { icon: <Activity className="w-3.5 h-3.5" />, color: 'text-blue-600', label: 'Running' },
    completed: { icon: <CheckCircle className="w-3.5 h-3.5" />, color: 'text-green-600', label: 'Done' },
    failed: { icon: <XCircle className="w-3.5 h-3.5" />, color: 'text-red-600', label: 'Failed' },
    waiting: { icon: <Clock className="w-3.5 h-3.5" />, color: 'text-orange-500', label: 'Waiting' },
  };

  const { icon, color, label } = config[status];

  return (
    <span className={`inline-flex items-center gap-1 text-xs font-medium ${color}`}>
      {icon}
      {label}
    </span>
  );
};

interface AgentCardProps {
  agent: AgentInfo;
  onRetry?: () => void;
  onStop?: () => void;
}

const AgentCard: React.FC<AgentCardProps> = ({ agent, onRetry, onStop }) => {
  const progress = agent.totalTasks > 0
    ? Math.round((agent.completedTasks / agent.totalTasks) * 100)
    : 0;

  return (
    <Card className="mb-2">
      <CardContent className="p-3">
        <Box className="flex items-start justify-between mb-2">
          <Box className="flex items-center gap-2">
            <Bot className="w-4 h-4 text-purple-600" />
            <Typography className="font-medium text-sm">{agent.name}</Typography>
          </Box>
          <AgentStatusBadge status={agent.status} />
        </Box>

        {agent.currentTask && (
          <Typography className="text-xs text-gray-500 mb-2 truncate">
            {agent.currentTask}
          </Typography>
        )}

        <Box className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-1.5 mb-2">
          <Box
            className="bg-purple-600 h-1.5 rounded-full transition-all"
            style={{ width: `${progress}%` }}
          />
        </Box>

        <Box className="flex items-center justify-between">
          <Typography className="text-xs text-gray-500">
            {agent.completedTasks}/{agent.totalTasks} tasks · {Math.round(agent.confidence * 100)}% conf
          </Typography>
          <Box className="flex gap-1">
            {agent.status === 'failed' && onRetry && (
              <Button size="sm" variant="text" onClick={onRetry}>
                <RefreshCw className="w-3.5 h-3.5" />
              </Button>
            )}
            {agent.status === 'running' && onStop && (
              <Button size="sm" variant="text" onClick={onStop}>
                <XCircle className="w-3.5 h-3.5" />
              </Button>
            )}
          </Box>
        </Box>

        <Box className="flex gap-3 mt-2 text-xs text-gray-400">
          <span>{agent.metrics.avgResponseTime}ms avg</span>
          <span>{Math.round(agent.metrics.successRate * 100)}% success</span>
          <span>{agent.metrics.tokensUsed} tokens</span>
        </Box>
      </CardContent>
    </Card>
  );
};

interface ConflictCardProps {
  conflict: AgentConflict;
  agentNames: Map<string, string>;
  onResolve?: () => void;
}

const ConflictCard: React.FC<ConflictCardProps> = ({ conflict, agentNames, onResolve }) => {
  const severityColor: Record<string, string> = {
    low: 'border-yellow-300 bg-yellow-50 dark:bg-yellow-900/20',
    medium: 'border-orange-300 bg-orange-50 dark:bg-orange-900/20',
    high: 'border-red-300 bg-red-50 dark:bg-red-900/20',
  };

  return (
    <Card className={`mb-2 border-l-4 ${severityColor[conflict.severity]}`}>
      <CardContent className="p-3">
        <Box className="flex items-start justify-between">
          <Box>
            <Box className="flex items-center gap-1 mb-1">
              <AlertTriangle className="w-3.5 h-3.5 text-orange-500" />
              <Typography className="text-xs font-medium">
                {agentNames.get(conflict.agentIds[0]) ?? conflict.agentIds[0]} ↔{' '}
                {agentNames.get(conflict.agentIds[1]) ?? conflict.agentIds[1]}
              </Typography>
            </Box>
            <Typography className="text-xs text-gray-600 dark:text-gray-400">
              {conflict.description}
            </Typography>
          </Box>
          {!conflict.resolvedAt && onResolve && (
            <Button size="sm" variant="outlined" onClick={onResolve}>
              Resolve
            </Button>
          )}
        </Box>
      </CardContent>
    </Card>
  );
};

// ============================================================================
// Main Component
// ============================================================================

export const AgentMonitor: React.FC<AgentMonitorProps> = ({
  orchestration,
  onRetryAgent,
  onResolveConflict,
  onStopAgent,
  className = '',
}) => {
  const { agents, conflicts, overallProgress } = orchestration;

  const agentNames = new Map(agents.map((a) => [a.id, a.name]));
  const activeConflicts = conflicts.filter((c) => !c.resolvedAt);
  const runningAgents = agents.filter((a) => a.status === 'running').length;
  const failedAgents = agents.filter((a) => a.status === 'failed').length;

  return (
    <Box className={`space-y-4 ${className}`}>
      {/* Header */}
      <Box className="flex items-center justify-between">
        <Box className="flex items-center gap-2">
          <Zap className="w-5 h-5 text-purple-600" />
          <Typography className="font-semibold">Agent Orchestration</Typography>
        </Box>
        <Typography className="text-sm text-gray-500">
          {runningAgents} active · {failedAgents > 0 ? `${failedAgents} failed · ` : ''}{Math.round(overallProgress)}%
        </Typography>
      </Box>

      {/* Overall Progress */}
      <Box className="w-full bg-gray-200 dark:bg-gray-700 rounded-full h-2">
        <Box
          className="bg-purple-600 h-2 rounded-full transition-all"
          style={{ width: `${overallProgress}%` }}
        />
      </Box>

      {/* Conflicts */}
      {activeConflicts.length > 0 && (
        <Box>
          <Typography className="text-xs font-medium text-gray-500 mb-2">
            Conflicts ({activeConflicts.length})
          </Typography>
          {activeConflicts.map((conflict) => (
            <ConflictCard
              key={conflict.id}
              conflict={conflict}
              agentNames={agentNames}
              onResolve={onResolveConflict ? () => onResolveConflict(conflict.id) : undefined}
            />
          ))}
        </Box>
      )}

      {/* Agents */}
      <Box>
        <Typography className="text-xs font-medium text-gray-500 mb-2">
          Agents ({agents.length})
        </Typography>
        {agents.map((agent) => (
          <AgentCard
            key={agent.id}
            agent={agent}
            onRetry={onRetryAgent ? () => onRetryAgent(agent.id) : undefined}
            onStop={onStopAgent ? () => onStopAgent(agent.id) : undefined}
          />
        ))}
      </Box>
    </Box>
  );
};

export default AgentMonitor;
