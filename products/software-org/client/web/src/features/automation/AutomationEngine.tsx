import { useState, useMemo } from 'react';
import { useSearchParams, useNavigate, Link } from 'react-router';
import { Badge } from '@/components/ui';
import { ENGINEER_DEVSECOPS_FLOW, DEVSECOPS_PHASE_LABELS, resolveDevSecOpsRoute, type DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import { DevSecOpsPipelineStrip } from '@/shared/components';
import { getEngineerPhaseRouteForStory } from '@/lib/devsecops/getEngineerPhaseRouteForStory';

/**
 * Automation Engine
 *
 * <p><b>Purpose</b><br>
 * Control center for workflow automation where users create, monitor, and tune
 * workflows, triggers, and execution history for end-to-end automation management.
 *
 * <p><b>Features</b><br>
 * - Overall automation health dashboard (active workflows, success rate, executions)
 * - Workflow templates library with create/edit actions
 * - Current execution monitoring with step tracking
 * - Workflow triggers management (event-based and time-based)
 * - Execution statistics and history
 * - Workflow creation and customization
 * - Execution cancel and retry actions
 * - Story-based filtering via storyId query param (Engineer Flow Phase 3)
 *
 * <p><b>Specs</b><br>
 * See web-page-specs/16_automation_engine.md for complete specification.
 *
 * <p><b>Mock Data</b><br>
 * All data is currently mocked. Integrate with API at `/api/v1/automation`
 * for real workflow management and execution tracking.
 *
 * @doc.type component
 * @doc.purpose Workflow automation orchestration and control center
 * @doc.layer product
 * @doc.pattern Page
 */

interface WorkflowTemplate {
    id: string;
    name: string;
    description: string;
    status: 'active' | 'paused' | 'draft';
    totalExecutions: number;
    successRate: number;
    lastRun?: string;
}

interface Execution {
    id: string;
    workflowId: string;
    status: 'running' | 'success' | 'failed' | 'pending';
    startTime: string;
    endTime?: string;
    duration?: number;
    currentStep?: number;
    totalSteps?: number;
}

interface WorkflowStats {
    activeWorkflows: number;
    totalWorkflows: number;
    executionsLast7Days: number;
    averageSuccessRate: number;
    averageDuration: number;
}

/**
 * Pipeline mock data associated with story IDs (for Engineer Flow)
 */
interface StoryPipeline {
    id: string;
    storyId: string;
    name: string;
    status: 'passed' | 'failed' | 'running' | 'pending';
    branch: string;
    commit: string;
    startedAt: string;
    completedAt?: string;
    duration?: number;
}

const STORY_PIPELINES: StoryPipeline[] = [
    {
        id: 'pipe-001',
        storyId: 'WI-1234',
        name: 'auth-service-ci',
        status: 'passed',
        branch: 'feature/WI-1234-user-auth',
        commit: 'abc1234',
        startedAt: new Date(Date.now() - 3600000).toISOString(),
        completedAt: new Date(Date.now() - 3300000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-001b',
        storyId: 'WI-1234',
        name: 'auth-service-integration-tests',
        status: 'passed',
        branch: 'feature/WI-1234-user-auth',
        commit: 'abc1234',
        startedAt: new Date(Date.now() - 3300000).toISOString(),
        completedAt: new Date(Date.now() - 3000000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-002',
        storyId: 'WI-1235',
        name: 'payment-service-ci',
        status: 'passed',
        branch: 'fix/WI-1235-payment-timeout',
        commit: 'def5678',
        startedAt: new Date(Date.now() - 1800000).toISOString(),
        completedAt: new Date(Date.now() - 1500000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-002b',
        storyId: 'WI-1235',
        name: 'payment-service-e2e',
        status: 'passed',
        branch: 'fix/WI-1235-payment-timeout',
        commit: 'def5678',
        startedAt: new Date(Date.now() - 1500000).toISOString(),
        completedAt: new Date(Date.now() - 1200000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-003',
        storyId: 'WI-1236',
        name: 'notification-service-ci',
        status: 'passed',
        branch: 'feature/WI-1236-realtime-notifications',
        commit: 'ghi9012',
        startedAt: new Date(Date.now() - 7200000).toISOString(),
        completedAt: new Date(Date.now() - 6900000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-004',
        storyId: 'WI-1237',
        name: 'product-service-ci',
        status: 'passed',
        branch: 'perf/WI-1237-search-optimization',
        commit: 'jkl3456',
        startedAt: new Date(Date.now() - 86400000).toISOString(),
        completedAt: new Date(Date.now() - 86100000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-004b',
        storyId: 'WI-1237',
        name: 'product-service-load-test',
        status: 'passed',
        branch: 'perf/WI-1237-search-optimization',
        commit: 'jkl3456',
        startedAt: new Date(Date.now() - 86100000).toISOString(),
        completedAt: new Date(Date.now() - 85500000).toISOString(),
        duration: 600,
    },
    {
        id: 'pipe-005',
        storyId: 'WI-1238',
        name: 'api-gateway-ci',
        status: 'passed',
        branch: 'feature/WI-1238-rate-limiting',
        commit: 'mno7890',
        startedAt: new Date(Date.now() - 172800000).toISOString(),
        completedAt: new Date(Date.now() - 172500000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-005b',
        storyId: 'WI-1238',
        name: 'api-gateway-security-scan',
        status: 'passed',
        branch: 'feature/WI-1238-rate-limiting',
        commit: 'mno7890',
        startedAt: new Date(Date.now() - 172500000).toISOString(),
        completedAt: new Date(Date.now() - 172200000).toISOString(),
        duration: 300,
    },
    // WI-1239: FAILED CI scenario
    {
        id: 'pipe-006',
        storyId: 'WI-1239',
        name: 'graphql-gateway-ci',
        status: 'failed',
        branch: 'feature/WI-1239-graphql-subscriptions',
        commit: 'pqr1234',
        startedAt: new Date(Date.now() - 900000).toISOString(),
        completedAt: new Date(Date.now() - 600000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-006b',
        storyId: 'WI-1239',
        name: 'graphql-gateway-unit-tests',
        status: 'passed',
        branch: 'feature/WI-1239-graphql-subscriptions',
        commit: 'pqr1234',
        startedAt: new Date(Date.now() - 1200000).toISOString(),
        completedAt: new Date(Date.now() - 900000).toISOString(),
        duration: 300,
    },
    {
        id: 'pipe-006c',
        storyId: 'WI-1239',
        name: 'graphql-gateway-e2e',
        status: 'running',
        branch: 'feature/WI-1239-graphql-subscriptions',
        commit: 'pqr1234',
        startedAt: new Date(Date.now() - 300000).toISOString(),
    },
    // WI-1240: Warning staging scenario
    {
        id: 'pipe-007',
        storyId: 'WI-1240',
        name: 'audit-service-ci',
        status: 'passed',
        branch: 'feature/WI-1240-activity-logging',
        commit: 'stu5678',
        startedAt: new Date(Date.now() - 259200000).toISOString(),
        completedAt: new Date(Date.now() - 258900000).toISOString(),
        duration: 300,
    },
];

export function AutomationEngine() {
    const [searchParams] = useSearchParams();
    const navigate = useNavigate();
    const storyId = searchParams.get('storyId');

    const [selectedWorkflow, setSelectedWorkflow] = useState<string>('wf-1');
    const [showCreateForm, setShowCreateForm] = useState(false);
    const [expandedExecution, setExpandedExecution] = useState<string | null>(null);

    const verifyStep = ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === 'engineer-verify-ci');
    const verifyPhaseLabel = verifyStep ? DEVSECOPS_PHASE_LABELS[verifyStep.phaseId] : DEVSECOPS_PHASE_LABELS.verify;
    const verifyNextStep = verifyStep?.nextStepId
        ? ENGINEER_DEVSECOPS_FLOW.steps.find(step => step.stepId === verifyStep.nextStepId)
        : undefined;
    const nextStepRoute = storyId && verifyNextStep
        ? resolveDevSecOpsRoute(verifyNextStep.route, { storyId })
        : storyId
            ? `/work-items/${storyId}/review`
            : undefined;

    // Filter pipelines by storyId if provided
    const storyPipelines = useMemo(() => {
        if (!storyId) return [];
        return STORY_PIPELINES.filter(p => p.storyId === storyId);
    }, [storyId]);

    // Check if all pipelines for this story are green
    const allPipelinesGreen = useMemo(() => {
        if (!storyId || storyPipelines.length === 0) return false;
        return storyPipelines.every(p => p.status === 'passed');
    }, [storyId, storyPipelines]);

    const stats: WorkflowStats = {
        activeWorkflows: 12,
        totalWorkflows: 18,
        executionsLast7Days: 1247,
        averageSuccessRate: 94.2,
        averageDuration: 45,
    };

    const workflowTemplates: WorkflowTemplate[] = [
        {
            id: 'wf-1',
            name: 'Auto-Rollback Failed Deployment',
            description: 'Automatically rollback deployment if health checks fail',
            status: 'active',
            totalExecutions: 324,
            successRate: 96,
            lastRun: new Date(Date.now() - 3600000).toISOString(),
        },
        {
            id: 'wf-2',
            name: 'Emergency Scaling Response',
            description: 'Scale resources when CPU usage exceeds 85% for 5+ minutes',
            status: 'active',
            totalExecutions: 89,
            successRate: 99,
            lastRun: new Date(Date.now() - 7200000).toISOString(),
        },
        {
            id: 'wf-3',
            name: 'Daily Backup & Verification',
            description: 'Scheduled daily database backup with integrity checks',
            status: 'active',
            totalExecutions: 187,
            successRate: 100,
            lastRun: new Date(Date.now() - 86400000).toISOString(),
        },
        {
            id: 'wf-4',
            name: 'Security Patch Auto-Apply',
            description: 'Apply security patches during maintenance window',
            status: 'paused',
            totalExecutions: 42,
            successRate: 95,
            lastRun: new Date(Date.now() - 604800000).toISOString(),
        },
    ];

    const currentExecution: Execution = {
        id: 'exec-latest',
        workflowId: selectedWorkflow,
        status: 'running',
        startTime: new Date(Date.now() - 120000).toISOString(),
        currentStep: 3,
        totalSteps: 8,
    };

    const executionHistory: Execution[] = [
        {
            id: 'exec-1',
            workflowId: selectedWorkflow,
            status: 'success',
            startTime: new Date(Date.now() - 3600000).toISOString(),
            endTime: new Date(Date.now() - 3540000).toISOString(),
            duration: 60,
        },
        {
            id: 'exec-2',
            workflowId: selectedWorkflow,
            status: 'success',
            startTime: new Date(Date.now() - 7200000).toISOString(),
            endTime: new Date(Date.now() - 7140000).toISOString(),
            duration: 60,
        },
        {
            id: 'exec-3',
            workflowId: selectedWorkflow,
            status: 'failed',
            startTime: new Date(Date.now() - 10800000).toISOString(),
            endTime: new Date(Date.now() - 10740000).toISOString(),
            duration: 60,
        },
        {
            id: 'exec-4',
            workflowId: selectedWorkflow,
            status: 'success',
            startTime: new Date(Date.now() - 86400000).toISOString(),
            endTime: new Date(Date.now() - 86340000).toISOString(),
            duration: 60,
        },
    ];

    const selectedTemplate = workflowTemplates.find(w => w.id === selectedWorkflow);
    const triggers = [
        {
            type: 'Event-based',
            description: 'Triggered on deployment failure event',
            condition: 'deployment.failed',
        },
        {
            type: 'Time-based',
            description: 'Runs every day at 02:00 UTC',
            condition: 'cron: 0 2 * * *',
        },
    ];

    const getStatusColor = (status: string) => {
        if (status === 'success') return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
        if (status === 'failed') return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-rose-400';
        if (status === 'running') return 'bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-indigo-400';
        return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
    };

    const getWorkflowStatusColor = (status: string) => {
        if (status === 'active') return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400';
        if (status === 'paused') return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400';
        return 'bg-slate-100 text-slate-800 dark:bg-slate-900/30 dark:text-neutral-400';
    };

    const handlePhaseClick = (phaseId: DevSecOpsPhaseId) => {
        const targetRoute = getEngineerPhaseRouteForStory(phaseId, storyId || undefined);
        if (!targetRoute) return;
        navigate(targetRoute);
    };

    return (
        <div className="space-y-6">
            {/* Story Context Banner (when storyId is present) */}
            {storyId && (
                <div className="bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800 rounded-lg p-4">
                    <div className="flex items-center justify-between">
                        <div>
                            <div className="flex items-center gap-2">
                                <span className="text-lg">📋</span>
                                <h2 className="font-semibold text-blue-900 dark:text-blue-100">
                                    CI Pipelines for Story {storyId}
                                </h2>
                            </div>
                            <p className="text-sm text-blue-700 dark:text-blue-300 mt-1">
                                {storyPipelines.length > 0
                                    ? `Showing ${storyPipelines.length} pipeline(s) associated with this story.`
                                    : 'No pipelines found for this story.'}
                            </p>
                            <p className="text-xs text-blue-700 dark:text-blue-300 mt-1">
                                Phase: {verifyPhaseLabel}
                                {verifyNextStep && ` • Next: ${verifyNextStep.label}`}
                            </p>
                        </div>
                        <div className="flex items-center gap-3">
                            <Link
                                to={`/work-items/${storyId}`}
                                className="text-sm text-blue-600 dark:text-indigo-400 hover:underline"
                            >
                                ← Back to Story
                            </Link>
                            {allPipelinesGreen && (
                                <button
                                    onClick={() => nextStepRoute && navigate(nextStepRoute)}
                                    className="px-4 py-2 bg-green-600 hover:bg-green-700 text-white rounded-lg font-medium transition flex items-center gap-2"
                                >
                                    <span>✅</span>
                                    CI all green → Go to review
                                </button>
                            )}
                        </div>
                    </div>
                    <div className="mt-3">
                        <DevSecOpsPipelineStrip
                            phases={ENGINEER_DEVSECOPS_FLOW.phases}
                            currentPhaseId={verifyStep?.phaseId ?? 'verify'}
                            onPhaseClick={handlePhaseClick}
                        />
                    </div>

                    {/* Story Pipelines List */}
                    {storyPipelines.length > 0 && (
                        <div className="mt-4 space-y-2">
                            {storyPipelines.map(pipeline => (
                                <div
                                    key={pipeline.id}
                                    className="flex items-center justify-between bg-white dark:bg-neutral-800 rounded-lg border border-blue-100 dark:border-neutral-600 p-3"
                                >
                                    <div className="flex items-center gap-3">
                                        <Badge className={getStatusColor(pipeline.status)}>
                                            {pipeline.status === 'passed' ? '✅' : pipeline.status === 'failed' ? '❌' : pipeline.status === 'running' ? '⏳' : '⏸️'} {pipeline.status}
                                        </Badge>
                                        <div>
                                            <div className="font-medium text-slate-900 dark:text-neutral-100">{pipeline.name}</div>
                                            <div className="text-xs text-slate-500 dark:text-neutral-400">
                                                {pipeline.branch} • {pipeline.commit.slice(0, 7)}
                                            </div>
                                        </div>
                                    </div>
                                    <div className="text-right">
                                        {pipeline.duration && (
                                            <div className="text-sm text-slate-600 dark:text-neutral-400">
                                                {Math.round(pipeline.duration / 60)}m {pipeline.duration % 60}s
                                            </div>
                                        )}
                                        <button className="text-xs text-blue-600 dark:text-indigo-400 hover:underline mt-1">
                                            View Logs
                                        </button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}
                </div>
            )}

            {/* Header */}
            <div className="flex items-start justify-between">
                <div>
                    <h1 className="text-3xl font-bold text-slate-900 dark:text-neutral-100">Automation Engine</h1>
                    <p className="text-slate-600 dark:text-neutral-400 mt-1">Manage workflows, triggers and automated run history</p>
                </div>
                <button
                    onClick={() => setShowCreateForm(!showCreateForm)}
                    className="px-4 py-2 bg-blue-600 hover:bg-blue-700 text-white rounded-lg font-medium transition"
                >
                    + Create Workflow
                </button>
            </div>

            {/* Stats Row */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Active Workflows</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">
                        {stats.activeWorkflows}/{stats.totalWorkflows}
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Last 7 Days</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">
                        {stats.executionsLast7Days.toLocaleString()}
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Success Rate</div>
                    <div className="text-3xl font-bold text-green-600 dark:text-green-400 mt-2">
                        {stats.averageSuccessRate}%
                    </div>
                </div>
                <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-4">
                    <div className="text-sm text-slate-600 dark:text-neutral-400">Avg Duration</div>
                    <div className="text-3xl font-bold text-slate-900 dark:text-neutral-100 mt-2">
                        {stats.averageDuration}s
                    </div>
                </div>
            </div>

            {/* Main Grid */}
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                {/* Left Column - Workflow Templates & Current Execution */}
                <div className="lg:col-span-2 space-y-6">
                    {/* Workflow Templates */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Workflow Templates</h2>
                        <div className="space-y-3">
                            {workflowTemplates.map(workflow => (
                                <div
                                    key={workflow.id}
                                    onClick={() => setSelectedWorkflow(workflow.id)}
                                    className={`p-4 rounded-lg border cursor-pointer transition ${selectedWorkflow === workflow.id
                                        ? 'border-blue-500 bg-blue-50 dark:bg-indigo-600/30'
                                        : 'border-slate-200 dark:border-slate-800 hover:border-slate-300 dark:hover:border-slate-700'
                                        }`}
                                >
                                    <div className="flex items-start justify-between mb-2">
                                        <div>
                                            <h3 className="font-medium text-slate-900 dark:text-neutral-100">{workflow.name}</h3>
                                            <p className="text-sm text-slate-600 dark:text-neutral-400">{workflow.description}</p>
                                        </div>
                                        <Badge className={getWorkflowStatusColor(workflow.status)}>
                                            {workflow.status}
                                        </Badge>
                                    </div>
                                    <div className="flex items-center gap-4 text-sm text-slate-600 dark:text-neutral-400">
                                        <span>{workflow.totalExecutions} executions</span>
                                        <span className="text-green-600 dark:text-green-400">{workflow.successRate}% success</span>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Current Execution Monitor */}
                    {selectedTemplate && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                            <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">
                                Current Execution – {selectedTemplate.name}
                            </h2>
                            {currentExecution.workflowId === selectedWorkflow ? (
                                <div className="space-y-4">
                                    <div className="flex items-center gap-4">
                                        <Badge className={getStatusColor(currentExecution.status)}>
                                            {currentExecution.status}
                                        </Badge>
                                        <span className="text-sm text-slate-600 dark:text-neutral-400">
                                            Step {currentExecution.currentStep}/{currentExecution.totalSteps}
                                        </span>
                                    </div>
                                    <div className="w-full bg-slate-200 dark:bg-neutral-700 rounded-full h-2">
                                        <div
                                            className="bg-blue-600 h-2 rounded-full transition-all"
                                            style={{ width: `${((currentExecution.currentStep || 0) / (currentExecution.totalSteps || 1)) * 100}%` }}
                                        ></div>
                                    </div>
                                    <div className="flex gap-2">
                                        <button className="px-3 py-1 text-sm bg-red-600 hover:bg-red-700 text-white rounded transition">
                                            Cancel
                                        </button>
                                        <button className="px-3 py-1 text-sm bg-slate-600 hover:bg-slate-700 text-white rounded transition">
                                            View Logs
                                        </button>
                                    </div>
                                </div>
                            ) : (
                                <div className="text-center py-6 text-slate-500 dark:text-slate-500">
                                    No current execution
                                </div>
                            )}
                        </div>
                    )}
                </div>

                {/* Right Column - Triggers & Stats & History */}
                <div className="space-y-6">
                    {/* Triggers */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Triggers</h3>
                        <div className="space-y-3">
                            {triggers.map((trigger, idx) => (
                                <div key={idx} className="p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-600">
                                    <div className="text-sm font-medium text-slate-900 dark:text-neutral-100">{trigger.type}</div>
                                    <p className="text-xs text-slate-600 dark:text-neutral-400 mt-1">{trigger.description}</p>
                                    <code className="text-xs bg-slate-900 text-slate-100 dark:bg-slate-950 p-1 rounded mt-2 block overflow-x-auto">
                                        {trigger.condition}
                                    </code>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Workflow Statistics */}
                    {selectedTemplate && (
                        <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                            <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">Statistics</h3>
                            <div className="space-y-3 text-sm">
                                <div>
                                    <div className="text-slate-600 dark:text-neutral-400">Total Executions</div>
                                    <div className="text-2xl font-bold text-slate-900 dark:text-neutral-100">{selectedTemplate.totalExecutions}</div>
                                </div>
                                <div>
                                    <div className="text-slate-600 dark:text-neutral-400">Success Rate</div>
                                    <div className="text-2xl font-bold text-green-600 dark:text-green-400">{selectedTemplate.successRate}%</div>
                                </div>
                            </div>
                        </div>
                    )}

                    {/* Execution History */}
                    <div className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-800 p-6">
                        <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-4">History</h3>
                        <div className="space-y-2 max-h-64 overflow-y-auto">
                            {executionHistory.map(exec => (
                                <div
                                    key={exec.id}
                                    onClick={() => setExpandedExecution(expandedExecution === exec.id ? null : exec.id)}
                                    className="p-3 bg-slate-50 dark:bg-neutral-800 rounded border border-slate-200 dark:border-neutral-600 cursor-pointer hover:border-slate-300 dark:hover:border-slate-600 transition"
                                >
                                    <div className="flex items-center justify-between gap-2">
                                        <Badge className={getStatusColor(exec.status)}>
                                            {exec.status}
                                        </Badge>
                                        {exec.duration && (
                                            <span className="text-xs text-slate-600 dark:text-neutral-400">{exec.duration}s</span>
                                        )}
                                    </div>
                                    {expandedExecution === exec.id && (
                                        <div className="mt-2 text-xs text-slate-600 dark:text-neutral-400">
                                            <p>{new Date(exec.startTime).toLocaleString()}</p>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}

export default AutomationEngine;
