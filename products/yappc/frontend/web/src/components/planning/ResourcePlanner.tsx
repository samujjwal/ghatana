/**
 * Resource Planner Component
 *
 * Displays AI-powered resource allocation and capacity planning.
 * Provides team assignment suggestions and workload visualization.
 *
 * @doc.type component
 * @doc.purpose Resource allocation and capacity planning UI
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import {
  Users as TeamIcon,
  BarChart3 as ChartIcon,
  AlertTriangle as WarningIcon,
  CheckCircle as SuccessIcon,
  Clock as TimeIcon,
  User as UserIcon,
} from 'lucide-react';
import { Typography, Button, Box, Card, CardContent, CardActions, Chip, Progress } from '@ghatana/design-system';
import { useResourcePrediction, useResourceUtilization } from '../../hooks/useResourcePrediction';
import type { TeamMember, TaskRequirement } from '../../services/ai/ResourceAllocationService';

// ============================================================================
// Types
// ============================================================================

export interface ResourcePlannerProps {
  tasks: TaskRequirement[];
  teamMembers: TeamMember[];
  onAssignment?: (taskId: string, memberId: string) => void;
  className?: string;
}

// ============================================================================
// Resource Planner Component
// ============================================================================

/**
 * Resource Planner Component
 */
export function ResourcePlanner({ tasks, teamMembers, onAssignment, className = '' }: ResourcePlannerProps): ReactNode {
  const [selectedTaskId, setSelectedTaskId] = useState<string | null>(null);
  const [showCapacityPlan, setShowCapacityPlan] = useState(true);

  const { allocations, capacityPlan, isLoading, allocate } = useResourcePrediction(tasks, teamMembers);
  const { averageUtilization, overloadedMembers, underutilizedMembers } = useResourceUtilization(teamMembers);

  const handleAutoAllocate = useCallback(async () => {
    await allocate({ tasks, teamMembers });
  }, [tasks, teamMembers, allocate]);

  const getMemberName = (memberId: string): string => {
    const member = teamMembers.find(m => m.id === memberId);
    return member?.name || 'Unknown';
  };

  const getRiskColor = (risk: 'low' | 'medium' | 'high'): string => {
    switch (risk) {
      case 'low':
        return 'text-green-600 dark:text-green-400';
      case 'medium':
        return 'text-orange-600 dark:text-orange-400';
      case 'high':
        return 'text-red-600 dark:text-red-400';
    }
  };

  const getRiskBgColor = (risk: 'low' | 'medium' | 'high'): string => {
    switch (risk) {
      case 'low':
        return 'bg-green-100 dark:bg-green-900/20';
      case 'medium':
        return 'bg-orange-100 dark:bg-orange-900/20';
      case 'high':
        return 'bg-red-100 dark:bg-red-900/20';
    }
  };

  return (
    <div className={`space-y-6 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <TeamIcon className="w-6 h-6 text-blue-600 dark:text-blue-400" />
          <Typography className="font-semibold text-lg">Resource Planner</Typography>
        </div>
        <Button size="sm" onClick={handleAutoAllocate} disabled={isLoading}>
          Auto-Allocate
        </Button>
      </div>

      {/* Capacity Overview */}
      {capacityPlan && showCapacityPlan && (
        <Card>
          <CardContent className="p-4">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-2">
                <ChartIcon className="w-5 h-5 text-blue-600 dark:text-blue-400" />
                <Typography className="font-semibold">Capacity Overview</Typography>
              </div>
              <Button size="sm" variant="text" onClick={() => setShowCapacityPlan(false)}>
                Hide
              </Button>
            </div>

            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-4">
              <Box>
                <Typography className="text-xs text-gray-500">Total Capacity</Typography>
                <Typography className="font-semibold">{capacityPlan.totalCapacity}h</Typography>
              </Box>
              <Box>
                <Typography className="text-xs text-gray-500">Allocated</Typography>
                <Typography className="font-semibold">{capacityPlan.allocated}h</Typography>
              </Box>
              <Box>
                <Typography className="text-xs text-gray-500">Available</Typography>
                <Typography className="font-semibold">{capacityPlan.available}h</Typography>
              </Box>
              <Box>
                <Typography className="text-xs text-gray-500">Utilization</Typography>
                <Typography className="font-semibold">{(capacityPlan.utilizationRate * 100).toFixed(0)}%</Typography>
              </Box>
            </div>

            <div className="mb-4">
              <div className="flex items-center justify-between mb-1">
                <Typography className="text-sm">Utilization Rate</Typography>
                <Typography className="text-sm">{(capacityPlan.utilizationRate * 100).toFixed(0)}%</Typography>
              </div>
              <Progress value={capacityPlan.utilizationRate * 100} />
            </div>

            <div className={`flex items-center gap-2 p-3 rounded-lg ${getRiskBgColor(capacityPlan.overloadRisk)}`}>
              {capacityPlan.overloadRisk === 'high' ? (
                <WarningIcon className={`w-5 h-5 ${getRiskColor(capacityPlan.overloadRisk)}`} />
              ) : (
                <SuccessIcon className={`w-5 h-5 ${getRiskColor(capacityPlan.overloadRisk)}`} />
              )}
              <Typography className={`text-sm font-medium ${getRiskColor(capacityPlan.overloadRisk)}`}>
                Overload Risk: {capacityPlan.overloadRisk.toUpperCase()}
              </Typography>
            </div>

            {capacityPlan.recommendations.length > 0 && (
              <div className="mt-4 space-y-2">
                <Typography className="text-sm font-medium">Recommendations</Typography>
                {capacityPlan.recommendations.map((rec, idx) => (
                  <Typography key={idx} className="text-xs text-gray-600 dark:text-gray-400">
                    • {rec}
                  </Typography>
                ))}
              </div>
            )}
          </CardContent>
        </Card>
      )}

      {!showCapacityPlan && (
        <Button size="sm" variant="outlined" onClick={() => setShowCapacityPlan(true)}>
          Show Capacity Plan
        </Button>
      )}

      {/* Team Utilization */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 mb-4">
            <UserIcon className="w-5 h-5 text-blue-600 dark:text-blue-400" />
            <Typography className="font-semibold">Team Utilization</Typography>
            <Chip size="sm" label={`Avg: ${(averageUtilization * 100).toFixed(0)}%`} />
          </div>

          <div className="space-y-3">
            {teamMembers.map(member => (
              <div key={member.id} className="space-y-1">
                <div className="flex items-center justify-between">
                  <Typography className="text-sm font-medium">{member.name}</Typography>
                  <Typography className="text-xs text-gray-500">{member.currentWorkload}%</Typography>
                </div>
                <Progress value={member.currentWorkload} />
              </div>
            ))}
          </div>

          {overloadedMembers.length > 0 && (
            <div className="mt-4 p-3 bg-red-50 dark:bg-red-900/20 rounded-lg">
              <Typography className="text-sm font-medium text-red-600 dark:text-red-400">
                Overloaded: {overloadedMembers.length} member{overloadedMembers.length !== 1 ? 's' : ''}
              </Typography>
            </div>
          )}

          {underutilizedMembers.length > 0 && (
            <div className="mt-4 p-3 bg-blue-50 dark:bg-blue-900/20 rounded-lg">
              <Typography className="text-sm font-medium text-blue-600 dark:text-blue-400">
                Underutilized: {underutilizedMembers.length} member{underutilizedMembers.length !== 1 ? 's' : ''}
              </Typography>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Task Allocations */}
      <Card>
        <CardContent className="p-4">
          <div className="flex items-center gap-2 mb-4">
            <TeamIcon className="w-5 h-5 text-blue-600 dark:text-blue-400" />
            <Typography className="font-semibold">Task Allocations</Typography>
          </div>

          {allocations.length === 0 ? (
            <Typography className="text-sm text-gray-500">No allocations yet. Click "Auto-Allocate" to generate suggestions.</Typography>
          ) : (
            <div className="space-y-3">
              {allocations.map(allocation => {
                const task = tasks.find(t => t.taskId === allocation.taskId);
                if (!task) return null;

                return (
                  <div
                    key={allocation.taskId}
                    className={`p-3 border rounded-lg ${
                      selectedTaskId === allocation.taskId ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20' : 'border-gray-200 dark:border-gray-700'
                    }`}
                    onClick={() => setSelectedTaskId(allocation.taskId)}
                  >
                    <div className="flex items-start justify-between mb-2">
                      <div className="flex-1">
                        <Typography className="font-medium text-sm">{task.title}</Typography>
                        <Typography className="text-xs text-gray-500">
                          {task.estimatedHours}h • {task.priority}
                        </Typography>
                      </div>
                      <Chip size="sm" label={`${(allocation.confidence * 100).toFixed(0)}%`} />
                    </div>

                    <div className="flex items-center gap-2 mb-2">
                      <UserIcon className="w-4 h-4 text-gray-400" />
                      <Typography className="text-sm">{getMemberName(allocation.assignedMemberId)}</Typography>
                    </div>

                    <Typography className="text-xs text-gray-500">{allocation.reasoning}</Typography>

                    {onAssignment && (
                      <CardActions className="p-0 mt-2">
                        <Button
                          size="sm"
                          onClick={() => onAssignment(allocation.taskId, allocation.assignedMemberId)}
                        >
                          Confirm Assignment
                        </Button>
                      </CardActions>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
