/**
 * Manager Dashboard Component
 *
 * Dashboard for Manager persona showing team metrics,
 * workload distribution, blockers, and team management tools.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Grid, Card, Box, Stack, Button, DataGrid } from '@ghatana/ui';
import { KpiCard } from '@/shared/components/org';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock data for manager dashboard
 */
const mockTeamData = {
  teamName: 'Backend Engineering Team',
  teamSize: 12,
  capacity: 85,
  velocity: 88,
  blockers: 2,
};

const mockTeamMembers = [
  {
    id: '1',
    name: 'Alice Johnson',
    role: 'Senior Engineer',
    workload: 95,
    status: 'high-load',
  },
  {
    id: '2',
    name: 'Bob Smith',
    role: 'Engineer II',
    workload: 75,
    status: 'optimal',
  },
  {
    id: '3',
    name: 'Carol Williams',
    role: 'Engineer I',
    workload: 60,
    status: 'optimal',
  },
];

const mockBlockers = [
  {
    id: 'b1',
    title: 'API rate limit issues',
    severity: 'high',
    assignee: 'Alice Johnson',
    days: 3,
  },
  {
    id: 'b2',
    title: 'Database migration pending',
    severity: 'medium',
    assignee: 'Bob Smith',
    days: 5,
  },
];

export function ManagerDashboard() {
  const { persona } = usePersona();

  return (
    <Box className="p-6 space-y-6">
      {/* Welcome Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
          Welcome back, {persona?.name || 'Manager'}
        </h1>
        <p className="text-slate-600 dark:text-neutral-400 mt-2">
          Managing {mockTeamData.teamName}
        </p>
      </div>

      {/* Top KPI Cards */}
      <Grid columns={4} gap={4}>
        <KpiCard
          label="Team Size"
          value={mockTeamData.teamSize}
          description="Active members"
          status="healthy"
        />

        <KpiCard
          label="Capacity"
          value={`${mockTeamData.capacity}%`}
          trend={{ direction: 'up', value: 5 }}
          description="Team utilization"
          status="healthy"
        />

        <KpiCard
          label="Velocity"
          value={`${mockTeamData.velocity}%`}
          trend={{ direction: 'up', value: 8 }}
          description="Sprint completion"
          status="healthy"
        />

        <KpiCard
          label="Blockers"
          value={mockTeamData.blockers}
          trend={{ direction: 'down', value: 1 }}
          description="Active blockers"
          status="warning"
        />
      </Grid>

      {/* Team Workload & Blockers */}
      <Grid columns={2} gap={4}>
        {/* Team Workload */}
        <Card title="Team Workload" subtitle="Current assignments">
          <Box className="p-4">
            <Stack spacing={3}>
              {mockTeamMembers.map((member) => (
                <div key={member.id} className="flex items-center gap-4">
                  <div className="flex-1">
                    <p className="font-medium text-slate-900 dark:text-neutral-200">{member.name}</p>
                    <p className="text-sm text-slate-600 dark:text-neutral-400">{member.role}</p>
                  </div>
                  <div className="flex-1">
                    <div className="flex items-center gap-2">
                      <div className="flex-1 bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                        <div
                          className={`h-2 rounded-full ${member.status === 'high-load'
                              ? 'bg-red-500'
                              : 'bg-green-500'
                            }`}
                          style={{ width: `${member.workload}%` }}
                        ></div>
                      </div>
                      <span className="text-sm font-medium text-slate-900 dark:text-neutral-200">
                        {member.workload}%
                      </span>
                    </div>
                  </div>
                </div>
              ))}
            </Stack>
          </Box>

          <Box className="p-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View Detailed Workload →
            </Button>
          </Box>
        </Card>

        {/* Active Blockers */}
        <Card title="Active Blockers" subtitle="Requiring attention">
          <Box className="p-4">
            <Stack spacing={3}>
              {mockBlockers.map((blocker) => (
                <div
                  key={blocker.id}
                  className={`p-3 rounded-lg border ${blocker.severity === 'high'
                      ? 'bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800'
                      : 'bg-yellow-50 dark:bg-orange-600/30 border-yellow-200 dark:border-yellow-800'
                    }`}
                >
                  <div className="flex items-start justify-between mb-2">
                    <h4 className="font-semibold text-slate-900 dark:text-neutral-200">
                      {blocker.title}
                    </h4>
                    <span
                      className={`px-2 py-1 text-xs font-medium rounded ${blocker.severity === 'high'
                          ? 'bg-red-100 text-red-700'
                          : 'bg-yellow-100 text-yellow-700'
                        }`}
                    >
                      {blocker.severity.toUpperCase()}
                    </span>
                  </div>
                  <p className="text-sm text-slate-600 dark:text-neutral-400">
                    Assigned: {blocker.assignee}
                  </p>
                  <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">
                    Blocked for {blocker.days} days
                  </p>
                </div>
              ))}
            </Stack>
          </Box>

          <Box className="p-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View All Blockers →
            </Button>
          </Box>
        </Card>
      </Grid>

      {/* Quick Actions */}
      <Card title="Quick Actions">
        <Box className="p-4">
          <Grid columns={4} gap={3}>
            <Button variant="primary" size="md" fullWidth>
              1:1 Schedule
            </Button>
            <Button variant="outline" size="md" fullWidth>
              Team Review
            </Button>
            <Button variant="outline" size="md" fullWidth>
              Request Resources
            </Button>
            <Button variant="outline" size="md" fullWidth>
              Propose Restructure
            </Button>
          </Grid>
        </Box>
      </Card>

      {/* Recent Activity */}
      <Card title="Team Activity" subtitle="Last 24 hours">
        <Box className="p-4">
          <Stack spacing={2}>
            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  <strong>Alice Johnson</strong> completed Sprint 23 tasks
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">2 hours ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  <strong>Bob Smith</strong> flagged API rate limit blocker
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">5 hours ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-purple-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  Team completed sprint retrospective
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Yesterday</p>
              </div>
            </div>
          </Stack>
        </Box>
      </Card>
    </Box>
  );
}

