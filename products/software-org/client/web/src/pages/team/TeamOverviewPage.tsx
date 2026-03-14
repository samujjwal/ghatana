/**
 * Team Overview Page
 *
 * Detailed view of a specific team showing members,
 * workload, metrics, and team management tools.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import { Grid, Card, Box, Stack, Button, DataGrid, Chip } from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';
import { useParams, useNavigate } from 'react-router';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock team data
 */
const mockTeam = {
  id: 'team-backend',
  name: 'Backend Engineering Team',
  description: 'Core backend services and APIs',
  departmentId: 'dept-eng',
  departmentName: 'Engineering',
  manager: {
    id: 'manager-1',
    name: 'Sarah Manager',
    email: 'sarah@example.com',
  },
  members: [
    {
      id: '1',
      name: 'Alice Johnson',
      role: 'Senior Engineer',
      email: 'alice@example.com',
      workload: 95,
      status: 'high-load',
      activeTasks: 8,
    },
    {
      id: '2',
      name: 'Bob Smith',
      role: 'Engineer II',
      email: 'bob@example.com',
      workload: 75,
      status: 'optimal',
      activeTasks: 5,
    },
    {
      id: '3',
      name: 'Carol Williams',
      role: 'Engineer I',
      email: 'carol@example.com',
      workload: 60,
      status: 'optimal',
      activeTasks: 3,
    },
  ],
  metrics: {
    size: 12,
    capacity: 85,
    velocity: 92,
    satisfaction: 4.2,
  },
};

export function TeamOverviewPage() {
  const { teamId } = useParams();
  const navigate = useNavigate();
  const { canRestructure, isManager } = usePersona();
  const [selectedMember, setSelectedMember] = useState<string | null>(null);

  const getWorkloadColor = (workload: number) => {
    if (workload >= 90) return 'error';
    if (workload >= 75) return 'warning';
    return 'success';
  };

  const columns = [
    {
      field: 'name',
      headerName: 'Name',
      width: 200,
      renderCell: (params: any) => (
        <div>
          <p className="font-medium text-slate-900 dark:text-neutral-100">{params.row.name}</p>
          <p className="text-xs text-slate-500 dark:text-neutral-400">{params.row.role}</p>
        </div>
      ),
    },
    {
      field: 'workload',
      headerName: 'Workload',
      width: 150,
      renderCell: (params: any) => (
        <div className="w-full">
          <div className="flex items-center gap-2">
            <div className="flex-1 bg-slate-200 rounded-full h-2">
              <div
                className={`h-2 rounded-full ${params.row.status === 'high-load'
                  ? 'bg-red-500'
                  : 'bg-green-500'
                  }`}
                style={{ width: `${params.row.workload}%` }}
              ></div>
            </div>
            <span className="text-sm font-medium">{params.row.workload}%</span>
          </div>
        </div>
      ),
    },
    {
      field: 'activeTasks',
      headerName: 'Tasks',
      width: 100,
      renderCell: (params: any) => (
        <Chip label={params.row.activeTasks} size="small" variant="outlined" />
      ),
    },
    {
      field: 'status',
      headerName: 'Status',
      width: 120,
      renderCell: (params: any) => (
        <Chip
          label={params.row.status.replace('-', ' ')}
          color={getWorkloadColor(params.row.workload)}
          size="small"
        />
      ),
    },
  ];

  return (
    <Box className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <Button variant="ghost" size="sm" onClick={() => navigate(-1)}>
              ← Back
            </Button>
            <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
              {mockTeam.name}
            </h1>
          </div>
          <p className="text-slate-600 dark:text-neutral-400">{mockTeam.description}</p>
          <p className="text-sm text-slate-500 dark:text-neutral-400 mt-1">
            Department: {mockTeam.departmentName} • Manager:{' '}
            {mockTeam.manager.name}
          </p>
        </div>

        {isManager && (
          <Stack direction="row" spacing={2}>
            <Button variant="outline" size="md">
              Edit Team
            </Button>
            {canRestructure() && (
              <Button variant="primary" size="md">
                Propose Changes
              </Button>
            )}
          </Stack>
        )}
      </div>

      {/* Team KPIs */}
      <Grid columns={4} gap={4}>
        <KpiCard
          title="Team Size"
          value={mockTeam.metrics.size}
          description="Active members"
          icon="groups"
        />

        <KpiCard
          title="Capacity"
          value={`${mockTeam.metrics.capacity}%`}
          trend="up"
          description="Team utilization"
          icon="speed"
          trendValue="+5%"
        />

        <KpiCard
          title="Velocity"
          value={`${mockTeam.metrics.velocity}%`}
          trend="up"
          description="Sprint completion"
          icon="trending_up"
          trendValue="+8%"
        />

        <KpiCard
          title="Satisfaction"
          value={mockTeam.metrics.satisfaction}
          trend="stable"
          description="Team morale"
          icon="sentiment_satisfied"
        />
      </Grid>

      {/* Team Members */}
      <Card title="Team Members" subtitle={`${mockTeam.members.length} members`}>
        <Box className="p-4">
          <DataGrid
            rows={mockTeam.members}
            columns={columns}
            pageSize={10}
            onRowClick={(params) => setSelectedMember(params.row.id)}
          />
        </Box>
      </Card>

      {/* Team Stats & Actions */}
      <Grid columns={2} gap={4}>
        {/* Workload Distribution */}
        <Card title="Workload Distribution">
          <Box className="p-4">
            <Stack spacing={3}>
              <div className="p-3 bg-green-50 rounded-lg">
                <div className="flex justify-between items-center mb-1">
                  <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                    Optimal Load
                  </span>
                  <span className="text-2xl font-bold text-green-700">8</span>
                </div>
                <p className="text-xs text-slate-600 dark:text-neutral-400">Members at ideal capacity</p>
              </div>

              <div className="p-3 bg-yellow-50 rounded-lg">
                <div className="flex justify-between items-center mb-1">
                  <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                    High Load
                  </span>
                  <span className="text-2xl font-bold text-yellow-700">3</span>
                </div>
                <p className="text-xs text-slate-600 dark:text-neutral-400">
                  Members need workload review
                </p>
              </div>

              <div className="p-3 bg-red-50 rounded-lg">
                <div className="flex justify-between items-center mb-1">
                  <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                    Overloaded
                  </span>
                  <span className="text-2xl font-bold text-red-700">1</span>
                </div>
                <p className="text-xs text-slate-600 dark:text-neutral-400">
                  Requires immediate attention
                </p>
              </div>
            </Stack>
          </Box>
        </Card>

        {/* Quick Actions */}
        <Card title="Quick Actions">
          <Box className="p-4">
            <Stack spacing={2}>
              <Button variant="primary" size="md" fullWidth>
                Add Team Member
              </Button>
              <Button variant="outline" size="md" fullWidth>
                Schedule Team Meeting
              </Button>
              <Button variant="outline" size="md" fullWidth>
                View Team Calendar
              </Button>
              <Button variant="outline" size="md" fullWidth>
                Export Team Report
              </Button>
              {canRestructure() && (
                <Button
                  variant="outline"
                  size="md"
                  fullWidth
                  onClick={() => navigate('/org/restructure')}
                >
                  Restructure Team
                </Button>
              )}
            </Stack>
          </Box>
        </Card>
      </Grid>

      {/* Recent Activity */}
      <Card title="Recent Activity" subtitle="Last 7 days">
        <Box className="p-4">
          <Stack spacing={2}>
            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  Sprint 23 completed with 95% velocity
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">2 days ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  Alice Johnson promoted to Senior Engineer
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">5 days ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-purple-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-100">
                  Team size increased from 10 to 12 members
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">1 week ago</p>
              </div>
            </div>
          </Stack>
        </Box>
      </Card>
    </Box>
  );
}
