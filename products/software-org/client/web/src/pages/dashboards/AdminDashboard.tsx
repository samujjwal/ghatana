/**
 * Admin Dashboard Component
 *
 * System administration dashboard showing system health,
 * data integrity, and administrative tools.
 *
 * @package @ghatana/software-org-web
 */

import React from 'react';
import { Grid, Card, Box, Stack, Button } from '@ghatana/ui';
import { KpiCard } from '@/shared/components/org';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock data for admin dashboard
 */
const mockSystemHealth = {
  status: 'healthy',
  uptime: '99.98%',
  activeUsers: 285,
  apiRequests: 12450,
};

const mockDataIntegrity = {
  totalRecords: 15234,
  issues: 3,
  lastCheck: '2 hours ago',
};

export function AdminDashboard() {
  const { persona } = usePersona();

  return (
    <Box className="p-6 space-y-6">
      {/* Welcome Header */}
      <div className="mb-8">
        <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
          Welcome back, {persona?.name || 'Admin'}
        </h1>
        <p className="text-slate-600 dark:text-neutral-400 mt-2">System administration overview</p>
      </div>

      {/* Top KPI Cards */}
      <Grid columns={4} gap={4}>
        <KpiCard
          label="System Status"
          value="Healthy"
          description="All systems operational"
          status="healthy"
        />

        <KpiCard
          label="Uptime"
          value={mockSystemHealth.uptime}
          description="Last 30 days"
          trend={{ direction: 'up', value: 0.5 }}
          status="healthy"
        />

        <KpiCard
          label="Active Users"
          value={mockSystemHealth.activeUsers}
          description="Currently online"
          status="healthy"
        />

        <KpiCard
          label="API Requests"
          value="12.5K"
          description="Last hour"
          trend={{ direction: 'up', value: 2 }}
          status="healthy"
        />
      </Grid>

      {/* System Health & Data Integrity */}
      <Grid columns={2} gap={4}>
        {/* System Health */}
        <Card title="System Health" subtitle="Service status">
          <Box className="p-4">
            <Stack spacing={3}>
              <div className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-600/30 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                  <div>
                    <p className="font-medium text-slate-900 dark:text-neutral-200">Web Service</p>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                      Response time: 45ms
                    </p>
                  </div>
                </div>
                <span className="text-green-700 dark:text-green-400 font-semibold">✓</span>
              </div>

              <div className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-600/30 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                  <div>
                    <p className="font-medium text-slate-900 dark:text-neutral-200">Database</p>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                      Connections: 45/100
                    </p>
                  </div>
                </div>
                <span className="text-green-700 dark:text-green-400 font-semibold">✓</span>
              </div>

              <div className="flex items-center justify-between p-3 bg-green-50 dark:bg-green-600/30 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 bg-green-500 rounded-full"></div>
                  <div>
                    <p className="font-medium text-slate-900 dark:text-neutral-200">Cache Service</p>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">Hit rate: 94%</p>
                  </div>
                </div>
                <span className="text-green-700 dark:text-green-400 font-semibold">✓</span>
              </div>

              <div className="flex items-center justify-between p-3 bg-yellow-50 dark:bg-orange-600/30 rounded-lg">
                <div className="flex items-center gap-3">
                  <div className="w-3 h-3 bg-yellow-500 rounded-full"></div>
                  <div>
                    <p className="font-medium text-slate-900 dark:text-neutral-200">
                      Background Jobs
                    </p>
                    <p className="text-xs text-slate-600 dark:text-neutral-400">
                      Queue: 23 pending
                    </p>
                  </div>
                </div>
                <span className="text-yellow-700 dark:text-yellow-400 font-semibold">⚠</span>
              </div>
            </Stack>
          </Box>

          <Box className="p-4 border-t">
            <Button variant="outline" size="sm" fullWidth>
              View Detailed Metrics →
            </Button>
          </Box>
        </Card>

        {/* Data Integrity */}
        <Card title="Data Integrity" subtitle="Validation status">
          <Box className="p-4">
            <Stack spacing={3}>
              <div className="p-3 bg-green-50 dark:bg-green-600/30 rounded-lg">
                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Total Records</p>
                <p className="text-2xl font-bold text-green-700 dark:text-green-400">
                  {mockDataIntegrity.totalRecords.toLocaleString()}
                </p>
              </div>

              <div className="p-3 bg-orange-50 dark:bg-orange-500/10 rounded-lg">
                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Issues Found</p>
                <p className="text-2xl font-bold text-orange-700 dark:text-orange-400">
                  {mockDataIntegrity.issues}
                </p>
                <p className="text-xs text-orange-600 dark:text-orange-400 mt-1">
                  Requires attention
                </p>
              </div>

              <div className="p-3 bg-blue-50 dark:bg-indigo-600/30 rounded-lg">
                <p className="text-sm text-slate-600 dark:text-neutral-400 mb-1">Last Check</p>
                <p className="text-lg font-semibold text-blue-700 dark:text-indigo-400">
                  {mockDataIntegrity.lastCheck}
                </p>
              </div>
            </Stack>
          </Box>

          <Box className="p-4 border-t">
            <Stack spacing={2}>
              <Button variant="primary" size="sm" fullWidth>
                Run Integrity Check
              </Button>
              <Button variant="outline" size="sm" fullWidth>
                View Issues →
              </Button>
            </Stack>
          </Box>
        </Card>
      </Grid>

      {/* Pending Actions */}
      <Card title="Pending Actions" subtitle="Requires admin attention">
        <Box className="p-4">
          <Stack spacing={2}>
            <div className="p-3 bg-red-50 dark:bg-rose-600/30 border-l-4 border-red-500 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-semibold text-slate-900 dark:text-neutral-200">
                    3 access requests pending
                  </p>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    New users awaiting access approval
                  </p>
                </div>
                <Button variant="primary" size="sm">
                  Review
                </Button>
              </div>
            </div>

            <div className="p-3 bg-yellow-50 dark:bg-orange-600/30 border-l-4 border-yellow-500 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-semibold text-slate-900 dark:text-neutral-200">
                    Database backup due
                  </p>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Weekly backup scheduled for today
                  </p>
                </div>
                <Button variant="outline" size="sm">
                  Start
                </Button>
              </div>
            </div>

            <div className="p-3 bg-blue-50 dark:bg-indigo-600/30 border-l-4 border-blue-500 rounded">
              <div className="flex items-start justify-between">
                <div>
                  <p className="font-semibold text-slate-900 dark:text-neutral-200">
                    System update available
                  </p>
                  <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                    Version 2.5.0 ready to deploy
                  </p>
                </div>
                <Button variant="outline" size="sm">
                  View
                </Button>
              </div>
            </div>
          </Stack>
        </Box>
      </Card>

      {/* Quick Admin Tools */}
      <Card title="Quick Tools">
        <Box className="p-4">
          <Grid columns={4} gap={3}>
            <Button variant="outline" size="md" fullWidth>
              User Management
            </Button>
            <Button variant="outline" size="md" fullWidth>
              System Config
            </Button>
            <Button variant="outline" size="md" fullWidth>
              Audit Log
            </Button>
            <Button variant="outline" size="md" fullWidth>
              Backup Data
            </Button>
          </Grid>
        </Box>
      </Card>

      {/* Recent Activity */}
      <Card title="System Activity" subtitle="Last 24 hours">
        <Box className="p-4">
          <Stack spacing={2}>
            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-green-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  System backup completed successfully
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">1 hour ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-blue-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  3 new users added to system
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">3 hours ago</p>
              </div>
            </div>

            <div className="flex items-start gap-3 p-2">
              <div className="w-2 h-2 bg-orange-500 rounded-full mt-2"></div>
              <div className="flex-1">
                <p className="text-sm text-slate-900 dark:text-neutral-200">
                  Data integrity check found 3 issues
                </p>
                <p className="text-xs text-slate-500 dark:text-neutral-400 mt-1">5 hours ago</p>
              </div>
            </div>
          </Stack>
        </Box>
      </Card>
    </Box>
  );
}

