/**
 * Executive Dashboard Component
 *
 * Dashboard for Executive persona (CTO, CPO, etc.) showing strategic KPIs,
 * department performance, initiatives, and escalations.
 *
 * @package @ghatana/software-org-web
 */

import { Grid, Card, Box, Stack, Button } from '@ghatana/design-system';
import { KpiCard } from '@/shared/components/org';
import { usePersona } from '@/hooks/usePersona';

/**
 * Mock data for executive dashboard
 * TODO: Replace with real API calls
 */
const mockOrgMetrics = {
    departments: 10,
    totalHeadcount: 320,
    openPositions: 12,
    budgetUtilization: 78,
};

const mockDepartmentPerformance = [
    { id: 'eng', name: 'Engineering', velocity: 92, quality: 95, headcount: 120, status: 'healthy' },
    { id: 'product', name: 'Product', velocity: 88, quality: 90, headcount: 45, status: 'healthy' },
    { id: 'design', name: 'Design', velocity: 85, quality: 92, headcount: 25, status: 'healthy' },
    { id: 'qa', name: 'QA', velocity: 78, quality: 88, headcount: 30, status: 'warning' },
    { id: 'devops', name: 'DevOps', velocity: 90, quality: 94, headcount: 20, status: 'healthy' },
];

const mockStrategicInitiatives = [
    { id: 'i1', name: 'Platform Modernization', progress: 65, status: 'on-track', owner: 'Engineering' },
    { id: 'i2', name: 'AI Integration', progress: 40, status: 'at-risk', owner: 'Engineering' },
    { id: 'i3', name: 'Security Hardening', progress: 80, status: 'on-track', owner: 'DevOps' },
    { id: 'i4', name: 'Mobile App Launch', progress: 55, status: 'on-track', owner: 'Product' },
];

const mockEscalations = [
    { id: 'e1', title: 'Critical infrastructure issue', severity: 'high', department: 'DevOps', age: '2h' },
    { id: 'e2', title: 'Budget overrun in Q4', severity: 'medium', department: 'Product', age: '1d' },
];

const mockPendingApprovals = [
    { id: 'a1', type: 'Headcount Request', department: 'Engineering', requestor: 'Architect Lead' },
    { id: 'a2', type: 'Budget Reallocation', department: 'Product', requestor: 'Product Manager' },
    { id: 'a3', type: 'Restructure Proposal', department: 'QA', requestor: 'QA Lead' },
];

/**
 * Executive Dashboard Component
 *
 * Main dashboard for executives (CTO, CPO, etc.) showing strategic
 * metrics, department performance, and escalations.
 *
 * @example
 * ```tsx
 * <ExecutiveLayout>
 *   <ExecutiveDashboard />
 * </ExecutiveLayout>
 * ```
 */
export function ExecutiveDashboard() {
    const { persona } = usePersona();

    return (
        <Box className="p-6 space-y-6">
            {/* Welcome Header */}
            <div className="mb-8">
                <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">
                    Welcome back, {persona?.name || 'Executive'}
                </h1>
                <p className="text-slate-600 dark:text-neutral-400 mt-2">
                    Here's your strategic overview for today
                </p>
            </div>

            {/* Top KPI Cards */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Departments"
                    value={mockOrgMetrics.departments}
                    description="Active departments"
                    status="healthy"
                />

                <KpiCard
                    label="Total Headcount"
                    value={mockOrgMetrics.totalHeadcount}
                    trend={{ direction: 'up', value: 8 }}
                    description="Across all departments"
                    status="healthy"
                />

                <KpiCard
                    label="Open Positions"
                    value={mockOrgMetrics.openPositions}
                    trend={{ direction: 'down', value: 3 }}
                    description="Hiring pipeline"
                    status="warning"
                />

                <KpiCard
                    label="Budget Utilization"
                    value={`${mockOrgMetrics.budgetUtilization}%`}
                    trend={{ direction: 'up', value: 5 }}
                    description="YTD spend"
                    status="healthy"
                />
            </Grid>

            {/* Department Performance & Initiatives */}
            <Grid columns={2} gap={4}>
                {/* Department Performance */}
                <Card title="Department Performance" subtitle="Key metrics by department">
                    <Box className="p-4">
                        <Stack gap="md">
                            {mockDepartmentPerformance.map((dept) => (
                                <div key={dept.id} className="flex items-center gap-4 p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg">
                                    <div className="flex-1">
                                        <p className="font-medium text-slate-900 dark:text-neutral-200">{dept.name}</p>
                                        <p className="text-sm text-slate-600 dark:text-neutral-400">{dept.headcount} members</p>
                                    </div>
                                    <div className="flex items-center gap-4">
                                        <div className="text-center">
                                            <p className="text-sm text-slate-500 dark:text-neutral-400">Velocity</p>
                                            <p className={`font-semibold ${dept.velocity >= 85 ? 'text-green-600' : 'text-yellow-600'}`}>
                                                {dept.velocity}%
                                            </p>
                                        </div>
                                        <div className="text-center">
                                            <p className="text-sm text-slate-500 dark:text-neutral-400">Quality</p>
                                            <p className={`font-semibold ${dept.quality >= 90 ? 'text-green-600' : 'text-yellow-600'}`}>
                                                {dept.quality}%
                                            </p>
                                        </div>
                                        <span className={`px-2 py-1 text-xs rounded-full ${dept.status === 'healthy' ? 'bg-green-100 text-green-800' : 'bg-yellow-100 text-yellow-800'
                                            }`}>
                                            {dept.status}
                                        </span>
                                    </div>
                                </div>
                            ))}
                        </Stack>
                    </Box>
                    <Box className="p-4 border-t">
                        <Button variant="outline" size="sm" fullWidth>
                            View All Departments
                        </Button>
                    </Box>
                </Card>

                {/* Strategic Initiatives */}
                <Card title="Strategic Initiatives" subtitle="Active initiatives progress">
                    <Box className="p-4">
                        <Stack gap="md">
                            {mockStrategicInitiatives.map((initiative) => (
                                <div key={initiative.id} className="p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg">
                                    <div className="flex items-center justify-between mb-2">
                                        <p className="font-medium text-slate-900 dark:text-neutral-200">{initiative.name}</p>
                                        <span className={`px-2 py-1 text-xs rounded-full ${initiative.status === 'on-track'
                                            ? 'bg-green-100 text-green-800'
                                            : 'bg-red-100 text-red-800'
                                            }`}>
                                            {initiative.status}
                                        </span>
                                    </div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <div className="flex-1 bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                                            <div
                                                className={`h-2 rounded-full ${initiative.status === 'on-track' ? 'bg-green-500' : 'bg-red-500'
                                                    }`}
                                                style={{ width: `${initiative.progress}%` }}
                                            />
                                        </div>
                                        <span className="text-sm font-medium text-slate-700 dark:text-neutral-300">
                                            {initiative.progress}%
                                        </span>
                                    </div>
                                    <p className="text-xs text-slate-500 dark:text-neutral-400">Owner: {initiative.owner}</p>
                                </div>
                            ))}
                        </Stack>
                    </Box>
                    <Box className="p-4 border-t">
                        <Button variant="outline" size="sm" fullWidth>
                            View All Initiatives
                        </Button>
                    </Box>
                </Card>
            </Grid>

            {/* Escalations & Approvals */}
            <Grid columns={2} gap={4}>
                {/* Escalations */}
                <Card title="Escalations" subtitle="Issues requiring attention">
                    <Box className="p-4">
                        {mockEscalations.length > 0 ? (
                            <Stack gap="md">
                                {mockEscalations.map((escalation) => (
                                    <div key={escalation.id} className="flex items-center gap-4 p-3 border border-red-200 dark:border-red-800 bg-red-50 dark:bg-rose-600/30 rounded-lg">
                                        <div className={`w-3 h-3 rounded-full ${escalation.severity === 'high' ? 'bg-red-500' : 'bg-yellow-500'
                                            }`} />
                                        <div className="flex-1">
                                            <p className="font-medium text-slate-900 dark:text-neutral-200">{escalation.title}</p>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400">{escalation.department} - {escalation.age} ago</p>
                                        </div>
                                        <Button variant="outline" size="sm">
                                            Review
                                        </Button>
                                    </div>
                                ))}
                            </Stack>
                        ) : (
                            <p className="text-slate-500 dark:text-neutral-400 text-center py-4">No active escalations</p>
                        )}
                    </Box>
                </Card>

                {/* Pending Approvals */}
                <Card title="Pending Approvals" subtitle="Requests awaiting your decision">
                    <Box className="p-4">
                        {mockPendingApprovals.length > 0 ? (
                            <Stack gap="md">
                                {mockPendingApprovals.map((approval) => (
                                    <div key={approval.id} className="flex items-center gap-4 p-3 bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800 rounded-lg">
                                        <div className="flex-1">
                                            <p className="font-medium text-slate-900 dark:text-neutral-200">{approval.type}</p>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400">
                                                {approval.department} - {approval.requestor}
                                            </p>
                                        </div>
                                        <div className="flex gap-2">
                                            <Button variant="outline" size="sm">
                                                Reject
                                            </Button>
                                            <Button variant="solid" tone="primary" size="sm">
                                                Approve
                                            </Button>
                                        </div>
                                    </div>
                                ))}
                            </Stack>
                        ) : (
                            <p className="text-slate-500 dark:text-neutral-400 text-center py-4">No pending approvals</p>
                        )}
                    </Box>
                    <Box className="p-4 border-t">
                        <Button variant="outline" size="sm" fullWidth>
                            View All Approvals
                        </Button>
                    </Box>
                </Card>
            </Grid>
        </Box>
    );
}
