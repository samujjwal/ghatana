/**
 * Strategic Planning Component
 *
 * VP-level strategic planning component with OKR tracking, quarterly/annual
 * initiatives, resource allocation planning, and timeline visualization.
 *
 * @package @ghatana/software-org-web
 */

import React, { useState } from 'react';
import {
    Grid,
    Card,
    KpiCard,
    Box,
    Chip,
    LinearProgress,
    Tabs,
    Tab,
    Alert,
    Button,
    Typography,
    Stack,
} from '@ghatana/ui';

/**
 * OKR (Objective and Key Result) data structure
 */
export interface OKR {
    id: string;
    objective: string;
    quarter: string; // e.g., "Q1 2025"
    departmentId: string;
    departmentName: string;
    owner: string;
    status: 'on-track' | 'at-risk' | 'off-track';
    progress: number; // 0-100
    keyResults: KeyResult[];
}

/**
 * Key Result data structure
 */
export interface KeyResult {
    id: string;
    description: string;
    target: number;
    current: number;
    unit: string;
    status: 'completed' | 'on-track' | 'at-risk' | 'not-started';
}

/**
 * Strategic Initiative data structure
 */
export interface Initiative {
    id: string;
    name: string;
    description: string;
    quarter: string;
    departmentIds: string[];
    departmentNames: string[];
    priority: 'critical' | 'high' | 'medium' | 'low';
    status: 'planning' | 'in-progress' | 'completed' | 'on-hold';
    progress: number; // 0-100
    startDate: string;
    targetDate: string;
    owner: string;
    resourceAllocation: ResourceAllocation[];
    milestones: Milestone[];
}

/**
 * Resource Allocation data structure
 */
export interface ResourceAllocation {
    departmentId: string;
    departmentName: string;
    allocatedHeadcount: number;
    allocatedBudget: number;
    utilization: number; // 0-100
}

/**
 * Milestone data structure
 */
export interface Milestone {
    id: string;
    name: string;
    targetDate: string;
    status: 'completed' | 'in-progress' | 'upcoming' | 'delayed';
    completionDate?: string;
}

/**
 * Planning Metrics aggregation
 */
export interface PlanningMetrics {
    activeOKRs: number;
    completedOKRs: number;
    activeInitiatives: number;
    completedInitiatives: number;
    avgOKRProgress: number;
    avgInitiativeProgress: number;
    totalAllocatedBudget: number;
    totalAllocatedHeadcount: number;
}

/**
 * Strategic Planning Props
 */
export interface StrategicPlanningProps {
    /** Planning metrics aggregation */
    planningMetrics: PlanningMetrics;
    /** List of OKRs */
    okrs: OKR[];
    /** List of strategic initiatives */
    initiatives: Initiative[];
    /** Current quarter (e.g., "Q1 2025") */
    currentQuarter: string;
    /** Callback when OKR is selected */
    onOKRClick?: (okrId: string) => void;
    /** Callback when initiative is selected */
    onInitiativeClick?: (initiativeId: string) => void;
    /** Callback when create new OKR is clicked */
    onCreateOKR?: () => void;
    /** Callback when create new initiative is clicked */
    onCreateInitiative?: () => void;
    /** Callback when export plan is clicked */
    onExportPlan?: () => void;
}

/**
 * Strategic Planning Component
 *
 * Provides VP-level strategic planning visibility with:
 * - Planning KPIs (active/completed OKRs, initiatives)
 * - OKR tracking with key results progress
 * - Initiative management with resource allocation
 * - Timeline visualization with milestones
 * - Tab-based navigation (OKRs, Initiatives, Timeline)
 *
 * Reuses @ghatana/ui components:
 * - KpiCard (4 planning KPIs)
 * - Grid (responsive layouts)
 * - Card (OKR cards, initiative cards)
 * - Chip (status, priority indicators)
 * - LinearProgress (OKR progress, initiative progress, key result progress)
 * - Tabs (OKRs/Initiatives/Timeline navigation)
 * - Alert (warnings, empty states)
 *
 * @example
 * ```tsx
 * <StrategicPlanning
 *   planningMetrics={metrics}
 *   okrs={okrList}
 *   initiatives={initiativeList}
 *   currentQuarter="Q1 2025"
 *   onOKRClick={(id) => navigate(`/vp/okrs/${id}`)}
 *   onCreateOKR={() => openOKRModal()}
 * />
 * ```
 */
export const StrategicPlanning: React.FC<StrategicPlanningProps> = ({
    planningMetrics,
    okrs,
    initiatives,
    currentQuarter,
    onOKRClick,
    onInitiativeClick,
    onCreateOKR,
    onCreateInitiative,
    onExportPlan,
}) => {
    const [selectedTab, setSelectedTab] = useState<'okrs' | 'initiatives' | 'timeline'>('okrs');

    // Get status color for OKRs
    const getOKRStatusColor = (status: 'on-track' | 'at-risk' | 'off-track'): 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'on-track':
                return 'success';
            case 'at-risk':
                return 'warning';
            case 'off-track':
                return 'error';
        }
    };

    // Get status color for key results
    const getKeyResultStatusColor = (status: 'completed' | 'on-track' | 'at-risk' | 'not-started'): 'success' | 'warning' | 'error' | 'default' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'on-track':
                return 'success';
            case 'at-risk':
                return 'warning';
            case 'not-started':
                return 'default';
        }
    };

    // Get priority color for initiatives
    const getPriorityColor = (priority: 'critical' | 'high' | 'medium' | 'low'): 'error' | 'warning' | 'default' => {
        switch (priority) {
            case 'critical':
                return 'error';
            case 'high':
                return 'error';
            case 'medium':
                return 'warning';
            case 'low':
                return 'default';
        }
    };

    // Get initiative status color
    const getInitiativeStatusColor = (status: 'planning' | 'in-progress' | 'completed' | 'on-hold'): 'default' | 'success' | 'warning' | 'error' => {
        switch (status) {
            case 'planning':
                return 'default';
            case 'in-progress':
                return 'warning';
            case 'completed':
                return 'success';
            case 'on-hold':
                return 'error';
        }
    };

    // Get milestone status color
    const getMilestoneStatusColor = (status: 'completed' | 'in-progress' | 'upcoming' | 'delayed'): 'success' | 'warning' | 'default' | 'error' => {
        switch (status) {
            case 'completed':
                return 'success';
            case 'in-progress':
                return 'warning';
            case 'upcoming':
                return 'default';
            case 'delayed':
                return 'error';
        }
    };

    // Format currency
    const formatCurrency = (amount: number): string => {
        return new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            minimumFractionDigits: 0,
            maximumFractionDigits: 0,
        }).format(amount);
    };

    // Group initiatives by quarter for timeline
    const initiativesByQuarter = initiatives.reduce((acc, initiative) => {
        if (!acc[initiative.quarter]) {
            acc[initiative.quarter] = [];
        }
        acc[initiative.quarter].push(initiative);
        return acc;
    }, {} as Record<string, Initiative[]>);

    return (
        <Box className="space-y-6">
            {/* Header */}
            <Box className="flex items-center justify-between">
                <Box>
                    <Typography variant="h4" className="text-slate-900 dark:text-neutral-100">
                        Strategic Planning
                    </Typography>
                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                        OKRs, initiatives, and resource planning for {currentQuarter}
                    </Typography>
                </Box>
                <Stack direction="row" spacing={2}>
                    {onExportPlan && (
                        <Button variant="outline" size="md" onClick={onExportPlan}>
                            Export Plan
                        </Button>
                    )}
                </Stack>
            </Box>

            {/* Planning KPIs */}
            <Grid columns={4} gap={4}>
                <KpiCard
                    label="Active OKRs"
                    value={planningMetrics.activeOKRs}
                    description={`${planningMetrics.completedOKRs} completed`}
                    status="healthy"
                />

                <KpiCard
                    label="OKR Progress"
                    value={`${Math.round(planningMetrics.avgOKRProgress)}%`}
                    description="Average across all OKRs"
                    status={planningMetrics.avgOKRProgress >= 70 ? 'healthy' : 'warning'}
                />

                <KpiCard
                    label="Active Initiatives"
                    value={planningMetrics.activeInitiatives}
                    description={`${planningMetrics.completedInitiatives} completed`}
                    status="healthy"
                />

                <KpiCard
                    label="Initiative Progress"
                    value={`${Math.round(planningMetrics.avgInitiativeProgress)}%`}
                    description="Average across all initiatives"
                    status={planningMetrics.avgInitiativeProgress >= 70 ? 'healthy' : 'warning'}
                />
            </Grid>

            {/* Tabs Navigation */}
            <Card>
                <Tabs value={selectedTab} onChange={(_, value) => setSelectedTab(value)}>
                    <Tab label={`OKRs (${okrs.length})`} value="okrs" />
                    <Tab label={`Initiatives (${initiatives.length})`} value="initiatives" />
                    <Tab label="Timeline" value="timeline" />
                </Tabs>

                {/* OKRs Tab */}
                {selectedTab === 'okrs' && (
                    <Box className="p-4">
                        <Box className="flex items-center justify-between mb-4">
                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                Objectives & Key Results
                            </Typography>
                            {onCreateOKR && (
                                <Button variant="primary" size="sm" onClick={onCreateOKR}>
                                    New OKR
                                </Button>
                            )}
                        </Box>

                        {okrs.length === 0 ? (
                            <Alert severity="info">
                                No OKRs found for {currentQuarter}
                            </Alert>
                        ) : (
                            <Stack spacing={3}>
                                {okrs.map((okr) => (
                                    <Card
                                        key={okr.id}
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onOKRClick?.(okr.id)}
                                    >
                                        <Box className="p-4">
                                            {/* OKR Header */}
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box className="flex-1">
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {okr.objective}
                                                        </Typography>
                                                        <Chip
                                                            label={okr.status}
                                                            color={getOKRStatusColor(okr.status)}
                                                            size="small"
                                                        />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        {okr.departmentName} • {okr.quarter} • Owner: {okr.owner}
                                                    </Typography>
                                                </Box>
                                            </Box>

                                            {/* Overall Progress */}
                                            <Box className="mb-3">
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Overall Progress
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {okr.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={okr.progress}
                                                    color={getOKRStatusColor(okr.status)}
                                                />
                                            </Box>

                                            {/* Key Results */}
                                            <Box>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                                    Key Results ({okr.keyResults.length})
                                                </Typography>
                                                <Stack spacing={2}>
                                                    {okr.keyResults.map((kr) => (
                                                        <Box key={kr.id} className="p-3 bg-slate-50 dark:bg-neutral-800 rounded-lg">
                                                            <Box className="flex items-start justify-between mb-2">
                                                                <Typography variant="body2" className="flex-1 text-slate-900 dark:text-neutral-100">
                                                                    {kr.description}
                                                                </Typography>
                                                                <Chip
                                                                    label={kr.status}
                                                                    color={getKeyResultStatusColor(kr.status)}
                                                                    size="small"
                                                                />
                                                            </Box>
                                                            <Box className="flex items-center gap-2">
                                                                <LinearProgress
                                                                    variant="determinate"
                                                                    value={(kr.current / kr.target) * 100}
                                                                    color={getKeyResultStatusColor(kr.status)}
                                                                    className="flex-1"
                                                                />
                                                                <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 min-w-[100px] text-right">
                                                                    {kr.current} / {kr.target} {kr.unit}
                                                                </Typography>
                                                            </Box>
                                                        </Box>
                                                    ))}
                                                </Stack>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Initiatives Tab */}
                {selectedTab === 'initiatives' && (
                    <Box className="p-4">
                        <Box className="flex items-center justify-between mb-4">
                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                Strategic Initiatives
                            </Typography>
                            {onCreateInitiative && (
                                <Button variant="primary" size="sm" onClick={onCreateInitiative}>
                                    New Initiative
                                </Button>
                            )}
                        </Box>

                        {initiatives.length === 0 ? (
                            <Alert severity="info">
                                No initiatives found for {currentQuarter}
                            </Alert>
                        ) : (
                            <Stack spacing={3}>
                                {initiatives.map((initiative) => (
                                    <Card
                                        key={initiative.id}
                                        className="cursor-pointer hover:shadow-md transition-shadow"
                                        onClick={() => onInitiativeClick?.(initiative.id)}
                                    >
                                        <Box className="p-4">
                                            {/* Initiative Header */}
                                            <Box className="flex items-start justify-between mb-3">
                                                <Box className="flex-1">
                                                    <Box className="flex items-center gap-2 mb-1">
                                                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                            {initiative.name}
                                                        </Typography>
                                                        <Chip
                                                            label={initiative.priority}
                                                            color={getPriorityColor(initiative.priority)}
                                                            size="small"
                                                        />
                                                        <Chip
                                                            label={initiative.status}
                                                            color={getInitiativeStatusColor(initiative.status)}
                                                            size="small"
                                                        />
                                                    </Box>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mb-1">
                                                        {initiative.description}
                                                    </Typography>
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Owner: {initiative.owner} • {initiative.quarter}
                                                    </Typography>
                                                </Box>
                                            </Box>

                                            {/* Progress */}
                                            <Box className="mb-3">
                                                <Box className="flex items-center justify-between mb-1">
                                                    <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                        Progress
                                                    </Typography>
                                                    <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                        {initiative.progress}%
                                                    </Typography>
                                                </Box>
                                                <LinearProgress
                                                    variant="determinate"
                                                    value={initiative.progress}
                                                    color={getInitiativeStatusColor(initiative.status)}
                                                />
                                            </Box>

                                            {/* Resource Allocation */}
                                            <Box className="mb-3">
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                                    Resource Allocation
                                                </Typography>
                                                <Grid columns={initiative.resourceAllocation.length} gap={2}>
                                                    {initiative.resourceAllocation.map((allocation) => (
                                                        <Box key={allocation.departmentId} className="p-2 bg-slate-50 dark:bg-neutral-800 rounded">
                                                            <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                {allocation.departmentName}
                                                            </Typography>
                                                            <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100">
                                                                {allocation.allocatedHeadcount} people
                                                            </Typography>
                                                            <Typography variant="caption" className="text-slate-600 dark:text-neutral-400">
                                                                {formatCurrency(allocation.allocatedBudget)}
                                                            </Typography>
                                                        </Box>
                                                    ))}
                                                </Grid>
                                            </Box>

                                            {/* Milestones */}
                                            <Box>
                                                <Typography variant="body2" className="font-medium text-slate-900 dark:text-neutral-100 mb-2">
                                                    Milestones ({initiative.milestones.filter(m => m.status === 'completed').length}/{initiative.milestones.length})
                                                </Typography>
                                                <Box className="flex flex-wrap gap-2">
                                                    {initiative.milestones.map((milestone) => (
                                                        <Chip
                                                            key={milestone.id}
                                                            label={milestone.name}
                                                            color={getMilestoneStatusColor(milestone.status)}
                                                            size="small"
                                                        />
                                                    ))}
                                                </Box>
                                            </Box>
                                        </Box>
                                    </Card>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}

                {/* Timeline Tab */}
                {selectedTab === 'timeline' && (
                    <Box className="p-4">
                        <Typography variant="h6" className="text-slate-900 dark:text-neutral-100 mb-4">
                            Quarterly Timeline
                        </Typography>

                        {Object.keys(initiativesByQuarter).length === 0 ? (
                            <Alert severity="info">
                                No initiatives scheduled
                            </Alert>
                        ) : (
                            <Stack spacing={4}>
                                {Object.entries(initiativesByQuarter).map(([quarter, quarterInitiatives]) => (
                                    <Box key={quarter}>
                                        <Box className="flex items-center gap-3 mb-3">
                                            <Typography variant="h6" className="text-slate-900 dark:text-neutral-100">
                                                {quarter}
                                            </Typography>
                                            {quarter === currentQuarter && (
                                                <Chip label="Current" color="success" size="small" />
                                            )}
                                        </Box>

                                        <Stack spacing={2}>
                                            {quarterInitiatives.map((initiative) => (
                                                <Card
                                                    key={initiative.id}
                                                    className="cursor-pointer hover:shadow-sm transition-shadow"
                                                    onClick={() => onInitiativeClick?.(initiative.id)}
                                                >
                                                    <Box className="p-3">
                                                        <Box className="flex items-center justify-between">
                                                            <Box className="flex items-center gap-2 flex-1">
                                                                <Typography variant="body1" className="text-slate-900 dark:text-neutral-100">
                                                                    {initiative.name}
                                                                </Typography>
                                                                <Chip
                                                                    label={initiative.priority}
                                                                    color={getPriorityColor(initiative.priority)}
                                                                    size="small"
                                                                />
                                                                <Chip
                                                                    label={initiative.status}
                                                                    color={getInitiativeStatusColor(initiative.status)}
                                                                    size="small"
                                                                />
                                                            </Box>
                                                            <Typography variant="body2" className="text-slate-600 dark:text-neutral-400">
                                                                {initiative.progress}% complete
                                                            </Typography>
                                                        </Box>

                                                        <Typography variant="body2" className="text-slate-600 dark:text-neutral-400 mt-1">
                                                            {new Date(initiative.startDate).toLocaleDateString()} - {new Date(initiative.targetDate).toLocaleDateString()} • {initiative.departmentNames.join(', ')}
                                                        </Typography>

                                                        <LinearProgress
                                                            variant="determinate"
                                                            value={initiative.progress}
                                                            color={getInitiativeStatusColor(initiative.status)}
                                                            className="mt-2"
                                                        />
                                                    </Box>
                                                </Card>
                                            ))}
                                        </Stack>
                                    </Box>
                                ))}
                            </Stack>
                        )}
                    </Box>
                )}
            </Card>
        </Box>
    );
};

/**
 * Mock data for development/testing
 */
export const mockStrategicPlanningData = {
    planningMetrics: {
        activeOKRs: 8,
        completedOKRs: 5,
        activeInitiatives: 6,
        completedInitiatives: 3,
        avgOKRProgress: 72,
        avgInitiativeProgress: 68,
        totalAllocatedBudget: 5000000,
        totalAllocatedHeadcount: 85,
    } as PlanningMetrics,

    okrs: [
        {
            id: 'okr-1',
            objective: 'Achieve 99.9% Platform Uptime',
            quarter: 'Q1 2025',
            departmentId: 'dept-devops',
            departmentName: 'DevOps',
            owner: 'Mike Lead',
            status: 'on-track',
            progress: 85,
            keyResults: [
                { id: 'kr-1-1', description: 'Reduce MTTR to under 1 hour', target: 1, current: 1.2, unit: 'hours', status: 'at-risk' },
                { id: 'kr-1-2', description: 'Zero critical incidents', target: 0, current: 0, unit: 'incidents', status: 'completed' },
                { id: 'kr-1-3', description: 'Achieve 99.95% uptime', target: 99.95, current: 99.92, unit: '%', status: 'on-track' },
            ],
        },
        {
            id: 'okr-2',
            objective: 'Increase Feature Velocity by 30%',
            quarter: 'Q1 2025',
            departmentId: 'dept-eng',
            departmentName: 'Engineering',
            owner: 'John Director',
            status: 'at-risk',
            progress: 60,
            keyResults: [
                { id: 'kr-2-1', description: 'Deploy 50 features per quarter', target: 50, current: 35, unit: 'features', status: 'at-risk' },
                { id: 'kr-2-2', description: 'Reduce lead time to 24 hours', target: 24, current: 28, unit: 'hours', status: 'at-risk' },
                { id: 'kr-2-3', description: 'Maintain code coverage above 85%', target: 85, current: 88, unit: '%', status: 'on-track' },
            ],
        },
    ] as OKR[],

    initiatives: [
        {
            id: 'init-1',
            name: 'Platform Modernization',
            description: 'Migrate legacy services to cloud-native architecture',
            quarter: 'Q1 2025',
            departmentIds: ['dept-eng', 'dept-devops'],
            departmentNames: ['Engineering', 'DevOps'],
            priority: 'critical',
            status: 'in-progress',
            progress: 75,
            startDate: '2025-01-01',
            targetDate: '2025-06-30',
            owner: 'John Director',
            resourceAllocation: [
                { departmentId: 'dept-eng', departmentName: 'Engineering', allocatedHeadcount: 25, allocatedBudget: 1500000, utilization: 80 },
                { departmentId: 'dept-devops', departmentName: 'DevOps', allocatedHeadcount: 10, allocatedBudget: 800000, utilization: 85 },
            ],
            milestones: [
                { id: 'ms-1-1', name: 'Architecture Design', targetDate: '2025-02-01', status: 'completed', completionDate: '2025-01-28' },
                { id: 'ms-1-2', name: 'Migration Phase 1', targetDate: '2025-03-15', status: 'in-progress' },
                { id: 'ms-1-3', name: 'Migration Phase 2', targetDate: '2025-05-01', status: 'upcoming' },
                { id: 'ms-1-4', name: 'Final Cutover', targetDate: '2025-06-30', status: 'upcoming' },
            ],
        },
        {
            id: 'init-2',
            name: 'Mobile App Redesign',
            description: 'Complete redesign of mobile application with new UX',
            quarter: 'Q1 2025',
            departmentIds: ['dept-product', 'dept-design'],
            departmentNames: ['Product', 'Design'],
            priority: 'high',
            status: 'in-progress',
            progress: 45,
            startDate: '2025-02-01',
            targetDate: '2025-05-31',
            owner: 'Sarah Manager',
            resourceAllocation: [
                { departmentId: 'dept-product', departmentName: 'Product', allocatedHeadcount: 8, allocatedBudget: 600000, utilization: 75 },
                { departmentId: 'dept-design', departmentName: 'Design', allocatedHeadcount: 12, allocatedBudget: 900000, utilization: 90 },
            ],
            milestones: [
                { id: 'ms-2-1', name: 'UX Research', targetDate: '2025-02-15', status: 'completed', completionDate: '2025-02-12' },
                { id: 'ms-2-2', name: 'Design System', targetDate: '2025-03-01', status: 'in-progress' },
                { id: 'ms-2-3', name: 'Prototype', targetDate: '2025-04-01', status: 'upcoming' },
                { id: 'ms-2-4', name: 'Launch', targetDate: '2025-05-31', status: 'upcoming' },
            ],
        },
        {
            id: 'init-3',
            name: 'Test Automation Framework',
            description: 'Build comprehensive test automation framework',
            quarter: 'Q2 2025',
            departmentIds: ['dept-qa'],
            departmentNames: ['QA'],
            priority: 'medium',
            status: 'planning',
            progress: 15,
            startDate: '2025-04-01',
            targetDate: '2025-09-30',
            owner: 'Lisa Tester',
            resourceAllocation: [
                { departmentId: 'dept-qa', departmentName: 'QA', allocatedHeadcount: 15, allocatedBudget: 1200000, utilization: 60 },
            ],
            milestones: [
                { id: 'ms-3-1', name: 'Tool Selection', targetDate: '2025-04-15', status: 'upcoming' },
                { id: 'ms-3-2', name: 'Framework Setup', targetDate: '2025-06-01', status: 'upcoming' },
                { id: 'ms-3-3', name: 'Rollout Phase 1', targetDate: '2025-08-01', status: 'upcoming' },
            ],
        },
    ] as Initiative[],

    currentQuarter: 'Q1 2025',
};
