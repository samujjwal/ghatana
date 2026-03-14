/**
 * Owner Dashboard Component
 *
 * Executive dashboard for Owner persona showing org-wide KPIs,
 * staffing overview, initiatives, and quick actions.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Grid, Card, Box, Stack, Button } from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock data for dashboard
 * TODO: Replace with real API calls
 */
const mockOrgHealth = {
  departments: 10,
  teams: 45,
  employees: 320,
  vacantRoles: 8,
};

const mockStaffing = {
  optimal: 5,
  highLoad: 3,
  overloaded: 2,
};

const mockInitiatives = {
  inProgress: 8,
  atRisk: 3,
  completedThisMonth: 2,
};

const mockStats = [
  { label: 'Avg Team Velocity', value: '85%', change: '+5%', trend: 'up' as const },
  { label: 'Quality Score', value: '92%', change: '0%', trend: 'stable' as const },
  { label: 'Employee Satisfaction', value: '4.2/5', change: '+0.2', trend: 'up' as const },
  { label: 'Time to Fill Roles', value: '14 days', change: '-3', trend: 'up' as const },
];

export interface OwnerDashboardProps {
  /** Optional: Override default data fetch */
  data?: any;
}

/**
 * Owner Dashboard Component
 *
 * Main dashboard for organization owners showing high-level
 * metrics, staffing status, and strategic initiatives.
 *
 * @example
 * ```tsx
 * <OwnerLayout>
 *   <OwnerDashboard />
 * </OwnerLayout>
 * ```
 */
export function OwnerDashboard({ data }: OwnerDashboardProps = {}) {
  const { persona } = usePersona();

  return (
    <Box className="p-6 space-y-6">
      {/* Welcome Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
          Welcome back, {persona?.name || 'Executive'}
        </h1>
        <p className="text-slate-600 dark:text-neutral-400 mt-2">
          Here's your organization overview for today
        </p>
      </div>

      {/* Top KPI Cards */}
      <Grid columns={4} gap={4}>
        <KpiCard
          label="Departments"
          value={mockOrgHealth.departments}
          description="Active departments"
          status="healthy"
        />

        <KpiCard
          label="Teams"
          value={mockOrgHealth.teams}
          trend={{ direction: 'up', value: 2 }}
          description="Across all departments"
          status="healthy"
        />

        <KpiCard
          label="Employees"
          value={mockOrgHealth.employees}
          trend={{ direction: 'up', value: 12 }}
          description="Total headcount"
          status="healthy"
        />

        <KpiCard
          label="Open Roles"
          value={mockOrgHealth.vacantRoles}
          trend={{ direction: 'down', value: 3 }}
          description="Vacant positions"
          status="warning"
        />
      </Grid>

      {/* Staffing & Initiatives Row */}
      <Grid columns={2} gap={4}>
        {/* Staffing Heatmap */}
        <Card title="Staffing Heatmap" subtitle="Team capacity status">
          <Stack spacing={3} className="py-2">
            <div className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-600/30 rounded-lg border border-green-200 dark:border-green-800">
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                <span className="font-medium text-slate-900 dark:text-neutral-200">Optimal Capacity</span>
              </div>
              <span className="text-2xl font-bold text-green-700 dark:text-green-400">
                {mockStaffing.optimal} teams
              </span>
            </div>

            <div className="flex items-center justify-between p-3 bg-yellow-50 dark:bg-orange-600/30 rounded-lg border border-yellow-200 dark:border-yellow-800">
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 bg-yellow-500 rounded-full"></div>
                <span className="font-medium text-slate-900 dark:text-neutral-200">High Load</span>
              </div>
              <span className="text-2xl font-bold text-yellow-700 dark:text-yellow-400">
                {mockStaffing.highLoad} teams
              </span>
            </div>

            <div className="flex items-center justify-between p-3 bg-red-50 dark:bg-rose-600/30 rounded-lg border border-red-200 dark:border-red-800">
              <div className="flex items-center gap-3">
                <div className="w-3 h-3 bg-red-500 rounded-full"></div>
                <span className="font-medium text-slate-900 dark:text-neutral-200">Overloaded</span>
              </div>
              <span className="text-2xl font-bold text-red-700 dark:text-rose-400">
                {mockStaffing.overloaded} teams
              </span>
            </div>
          </Stack>

          <div className="mt-4 pt-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View Detailed Heatmap →
            </Button>
          </div>
        </Card>

        {/* Active Initiatives */}
        <Card title="Active Initiatives" subtitle="Strategic projects">
          <Stack spacing={3} className="py-2">
            <div className="flex items-center justify-between p-3 bg-blue-50 dark:bg-indigo-600/30 rounded-lg">
              <span className="text-slate-700 dark:text-neutral-300">In Progress</span>
              <span className="text-2xl font-bold text-blue-700 dark:text-indigo-400">
                {mockInitiatives.inProgress}
              </span>
            </div>

            <div className="flex items-center justify-between p-3 bg-orange-50 dark:bg-orange-500/10 rounded-lg">
              <span className="text-slate-700 dark:text-neutral-300">At Risk</span>
              <span className="text-2xl font-bold text-orange-700 dark:text-orange-400">
                {mockInitiatives.atRisk}
              </span>
            </div>

            <div className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-600/30 rounded-lg">
              <span className="text-slate-700 dark:text-neutral-300">Completed This Month</span>
              <span className="text-2xl font-bold text-green-700 dark:text-green-400">
                {mockInitiatives.completedThisMonth}
              </span>
            </div>
          </Stack>

          <div className="mt-4 pt-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View All Initiatives →
            </Button>
          </div>
        </Card>
      </Grid>

      {/* Organization Stats */}
      <Card title="Organization Performance" subtitle="Key metrics">
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4 p-4">
          {mockStats.map((stat, index) => (
            <div key={index} className="bg-slate-50 dark:bg-neutral-800 rounded-lg p-4">
              <div className="text-sm text-slate-600 dark:text-neutral-400 mb-1">{stat.label}</div>
              <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{stat.value}</div>
              <div className={`text-sm mt-1 ${stat.trend === 'up' ? 'text-green-600' :
                  stat.trend === 'stable' ? 'text-slate-500' : 'text-red-600'
                }`}>
                {stat.change}
              </div>
            </div>
          ))}
        </div>
      </Card>

      {/* Risk & Dependencies and Quick Actions */}
      <Grid columns={3} gap={4}>
        {/* Risk Dashboard */}
        <Card title="Risks & Blockers" subtitle="Requiring attention" className="col-span-2">
          <Stack spacing={2}>
            <div className="p-3 border-l-4 border-red-500 bg-red-50 dark:bg-rose-600/30 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <h4 className="font-semibold text-slate-900 dark:text-neutral-200">Product Launch Delayed</h4>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Engineering capacity issue - 2 weeks behind schedule
                  </p>
                </div>
                <span className="px-2 py-1 bg-red-100 text-red-700 text-xs font-medium rounded">
                  HIGH
                </span>
              </div>
            </div>

            <div className="p-3 border-l-4 border-yellow-500 bg-yellow-50 dark:bg-orange-600/30 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <h4 className="font-semibold text-slate-900 dark:text-neutral-200">Q2 Resource Planning</h4>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Need to finalize hiring plan for next quarter
                  </p>
                </div>
                <span className="px-2 py-1 bg-yellow-100 text-yellow-700 text-xs font-medium rounded">
                  MEDIUM
                </span>
              </div>
            </div>

            <div className="p-3 border-l-4 border-orange-500 bg-orange-50 dark:bg-orange-500/10 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <h4 className="font-semibold text-slate-900 dark:text-neutral-200">Sales Restructure Pending</h4>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Approval needed for proposed sales team reorganization
                  </p>
                </div>
                <span className="px-2 py-1 bg-orange-100 text-orange-700 text-xs font-medium rounded">
                  MEDIUM
                </span>
              </div>
            </div>
          </Stack>

          <div className="mt-4 pt-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View All Risks →
            </Button>
          </div>
        </Card>

        {/* Quick Actions */}
        <Card title="Quick Actions">
          <Stack spacing={2}>
            <Button variant="primary" size="md" fullWidth className="justify-start">
              <span className="mr-2">✓</span>
              Approve Requests (3)
            </Button>

            <Button variant="outline" size="md" fullWidth className="justify-start">
              <span className="mr-2">📝</span>
              Review Budget
            </Button>

            <Button variant="outline" size="md" fullWidth className="justify-start">
              <span className="mr-2">🏗️</span>
              Initiate Restructure
            </Button>

            <Button variant="outline" size="md" fullWidth className="justify-start">
              <span className="mr-2">📊</span>
              Set Directive
            </Button>

            <Button variant="outline" size="md" fullWidth className="justify-start">
              <span className="mr-2">📈</span>
              View Reports
            </Button>
          </Stack>
        </Card>
      </Grid>

      {/* Recent Activity (Optional) */}
      <Card title="Recent Activity" subtitle="Last 24 hours">
        <Stack spacing={2}>
          <div className="flex items-start gap-3 p-2">
            <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
            <div className="flex-1">
              <p className="text-sm text-slate-900 dark:text-neutral-200">
                <strong>Engineering Team</strong> completed sprint review
              </p>
              <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">2 hours ago</p>
            </div>
          </div>

          <div className="flex items-start gap-3 p-2">
            <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
            <div className="flex-1">
              <p className="text-sm text-slate-900 dark:text-neutral-200">
                <strong>Sarah Manager</strong> submitted restructure proposal for QA team
              </p>
              <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">5 hours ago</p>
            </div>
          </div>

          <div className="flex items-start gap-3 p-2">
            <div className="w-2 h-2 bg-purple-500 rounded-full mt-2"></div>
            <div className="flex-1">
              <p className="text-sm text-slate-900 dark:text-neutral-200">
                <strong>Finance Department</strong> updated Q2 budget forecast
              </p>
              <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">Yesterday</p>
            </div>
          </div>
        </Stack>
      </Card>
    </Box>
  );
}
