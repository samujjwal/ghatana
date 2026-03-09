/**
 * Department Overview Page
 *
 * Comprehensive view of a department showing teams,
 * metrics, and department management tools.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Grid, Card, KpiCard, Box, Stack, Button } from '@ghatana/ui';
import { useParams, useNavigate } from 'react-router';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock department data
 */
const mockDepartment = {
  id: 'dept-eng',
  name: 'Engineering Department',
  description: 'Product development and technical infrastructure',
  head: {
    id: 'director-1',
    name: 'John Director',
    email: 'john@example.com',
  },
  teams: [
    {
      id: 'team-backend',
      name: 'Backend Team',
      size: 12,
      capacity: 85,
      status: 'optimal',
      manager: 'Sarah Manager',
    },
    {
      id: 'team-frontend',
      name: 'Frontend Team',
      size: 10,
      capacity: 92,
      status: 'high-load',
      manager: 'Mike Lead',
    },
    {
      id: 'team-devops',
      name: 'DevOps Team',
      size: 6,
      capacity: 78,
      status: 'optimal',
      manager: 'Lisa Ops',
    },
    {
      id: 'team-qa',
      name: 'QA Team',
      size: 8,
      capacity: 88,
      status: 'optimal',
      manager: 'Tom Quality',
    },
  ],
  metrics: {
    totalHeadcount: 150,
    teamCount: 8,
    avgVelocity: 88,
    avgSatisfaction: 4.3,
    openPositions: 5,
  },
};

export function DepartmentOverviewPage() {
  const { departmentId } = useParams();
  const navigate = useNavigate();
  const { canRestructure, isOwner, isAdmin } = usePersona();

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'optimal':
        return 'success';
      case 'high-load':
        return 'warning';
      case 'overloaded':
        return 'error';
      default:
        return 'default';
    }
  };

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <Button variant="ghost" size="sm" onClick={() => navigate('/org/overview')}>
              ← Back to Org
            </Button>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
              {mockDepartment.name}
            </h1>
          </div>
          <p className="text-slate-600 dark:text-neutral-400">{mockDepartment.description}</p>
          <p className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
            Head: {mockDepartment.head.name}
          </p>
        </div>

        {(isOwner || isAdmin) && (
          <Stack direction="row" spacing={2}>
            <Button variant="outline" size="md">
              Edit Department
            </Button>
            {canRestructure() && (
              <Button variant="primary" size="md">
                Propose Restructure
              </Button>
            )}
          </Stack>
        )}
      </div>

      {/* Department KPIs */}
      <Grid columns={5} gap={4}>
        <KpiCard
          title="Headcount"
          value={mockDepartment.metrics.totalHeadcount}
          description="Total employees"
          icon="people"
          trend="up"
          trendValue="+12"
        />

        <KpiCard
          title="Teams"
          value={mockDepartment.metrics.teamCount}
          description="Active teams"
          icon="groups"
        />

        <KpiCard
          title="Avg Velocity"
          value={`${mockDepartment.metrics.avgVelocity}%`}
          description="Department average"
          icon="trending_up"
          trend="up"
          trendValue="+3%"
        />

        <KpiCard
          title="Satisfaction"
          value={mockDepartment.metrics.avgSatisfaction}
          description="Employee morale"
          icon="sentiment_satisfied"
          trend="stable"
        />

        <KpiCard
          title="Open Roles"
          value={mockDepartment.metrics.openPositions}
          description="Recruiting"
          icon="work_outline"
          trend="down"
          trendValue="-2"
          trendDirection="positive"
        />
      </Grid>

      {/* Teams Grid */}
      <Card title="Teams" subtitle={`${mockDepartment.teams.length} teams`}>
        <Box className="p-4">
          <Grid columns={2} gap={4}>
            {mockDepartment.teams.map((team) => (
              <Card
                key={team.id}
                className="cursor-pointer hover:shadow-md transition-all"
                onClick={() => navigate(`/team/${team.id}`)}
              >
                <Box className="p-4">
                  <div className="flex items-start justify-between mb-3">
                    <div>
                      <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100">
                        {team.name}
                      </h3>
                      <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                        Manager: {team.manager}
                      </p>
                    </div>
                    <Chip
                      label={team.status}
                      color={getStatusColor(team.status)}
                      size="small"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div className="p-2 bg-slate-50 rounded">
                      <p className="text-xs text-slate-600 dark:text-neutral-400">Size</p>
                      <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {team.size}
                      </p>
                    </div>

                    <div className="p-2 bg-slate-50 rounded">
                      <p className="text-xs text-slate-600 dark:text-neutral-400">Capacity</p>
                      <p className="text-lg font-bold text-slate-900 dark:text-neutral-100">
                        {team.capacity}%
                      </p>
                    </div>
                  </div>
                </Box>
              </Card>
            ))}
          </Grid>
        </Box>

        {(isOwner || isAdmin) && (
          <Box className="p-4 border-t">
            <Button variant="primary" size="sm" fullWidth>
              Create New Team
            </Button>
          </Box>
        )}
      </Card>

      {/* Department Stats */}
      <Grid columns={3} gap={4}>
        {/* Team Status Breakdown */}
        <Card title="Team Status">
          <Box className="p-4">
            <Stack spacing={2}>
              <div className="flex justify-between items-center p-3 bg-green-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  Optimal
                </span>
                <span className="text-2xl font-bold text-green-700">6</span>
              </div>

              <div className="flex justify-between items-center p-3 bg-yellow-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  High Load
                </span>
                <span className="text-2xl font-bold text-yellow-700">2</span>
              </div>

              <div className="flex justify-between items-center p-3 bg-red-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  Overloaded
                </span>
                <span className="text-2xl font-bold text-red-700">0</span>
              </div>
            </Stack>
          </Box>
        </Card>

        {/* Hiring Pipeline */}
        <Card title="Hiring Pipeline">
          <Box className="p-4">
            <Stack spacing={2}>
              <div className="flex justify-between items-center p-3 bg-blue-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  Open Positions
                </span>
                <span className="text-2xl font-bold text-blue-700">5</span>
              </div>

              <div className="flex justify-between items-center p-3 bg-purple-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  In Progress
                </span>
                <span className="text-2xl font-bold text-purple-700">8</span>
              </div>

              <div className="flex justify-between items-center p-3 bg-green-50 rounded-lg">
                <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                  Offers Out
                </span>
                <span className="text-2xl font-bold text-green-700">3</span>
              </div>
            </Stack>
          </Box>
        </Card>

        {/* Quick Actions */}
        <Card title="Quick Actions">
          <Box className="p-4">
            <Stack spacing={2}>
              <Button variant="outline" size="sm" fullWidth>
                Department Report
              </Button>
              <Button variant="outline" size="sm" fullWidth>
                Budget Overview
              </Button>
              <Button variant="outline" size="sm" fullWidth>
                Headcount Plan
              </Button>
              <Button variant="outline" size="sm" fullWidth>
                Performance Review
              </Button>
            </Stack>
          </Box>
        </Card>
      </Grid>

      {/* Recent Department Activity */}
      <Card title="Recent Activity" subtitle="Last 30 days">
        <Box className="p-4">
          <Stack spacing={2}>
            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  QA Team completed sprint with 98% test coverage
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">3 days ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  3 new engineers onboarded to Backend Team
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">1 week ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-purple-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  Department budget approved for Q2 hiring
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">2 weeks ago</p>
              </div>
            </div>
          </Stack>
        </Box>
      </Card>
    </Box>
  );
}

