/**
 * Optimized Workflows Page
 *
 * Full-featured Workflows Page with progressive loading and performance optimizations.
 * Maintains all original features while improving load times.
 *
 * @doc.type page
 * @doc.purpose Feature-complete workflows page with optimized loading
 * @doc.layer frontend
 */

import React, { useState, useMemo } from 'react';
import { Link, useNavigate } from 'react-router';
import { useQuery } from '@tanstack/react-query';
import { getCapabilitySignal, useCapabilityRegistry } from '../api/capabilities.service';
import {
    Play,
    Pause,
    Square,
    Clock,
    XCircle,
    Plus,
    MoreVertical,
    Workflow as WorkflowIcon,
    Loader2,
    Filter,
    Search,
    RefreshCw,
    Sparkles,
    AlertTriangle,
    TrendingUp,
    Zap,
    Shield,
    BarChart2,
    ChevronDown,
    ChevronRight,
    CheckCircle2,
    ArrowRight,
} from 'lucide-react';
import { getPipelineOptimisationHints, aiQueryKeys, type PipelineOptimisationHint } from '../lib/api/ai';
import {
    WORKFLOW_HINTS_DEGRADED_DETAIL,
    WORKFLOW_HINTS_DEGRADED_TITLE,
    WORKFLOW_HINTS_UNAVAILABLE_DETAIL,
    WORKFLOW_HINTS_UNAVAILABLE_TITLE,
} from '../lib/runtime-boundaries';
import { cn } from '../lib/theme';
import { workflowsApi, type Workflow } from '../lib/api/workflows';

/**
 * Status icon mapping — maps API status to icon
 */
const getStatusIcon = (status: Workflow['status']) => {
    switch (status) {
        case 'active':   return <Play   className="h-4 w-4 text-green-500" />;
        case 'paused':   return <Pause  className="h-4 w-4 text-yellow-500" />;
        case 'archived': return <Square className="h-4 w-4 text-gray-500" />;
        case 'draft':    return <Clock  className="h-4 w-4 text-blue-400" />;
        // no-op to satisfy exhaustive check
        default:         return <Clock  className="h-4 w-4 text-gray-400" />;
    }
};

/**
 * Status color mapping
 */
const getStatusColor = (status: Workflow['status']) => {
    switch (status) {
        case 'active':   return 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300';
        case 'paused':   return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300';
        case 'archived': return 'bg-gray-100 text-gray-700 dark:bg-gray-900 dark:text-gray-300';
        case 'draft':    return 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300';
        default:         return 'bg-gray-100 text-gray-700';
    }
};

/**
 * Workflow Actions Component
 */
function WorkflowActions({ workflow }: { workflow: Workflow }) {
    const [showActions, setShowActions] = useState(false);

    return (
        <div className="relative">
            <button
                onClick={() => setShowActions(!showActions)}
                className="p-1.5 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
            >
                <MoreVertical className="h-4 w-4 text-gray-400" />
            </button>

            {showActions && (
                <div className="absolute right-0 top-8 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg z-10 min-w-[150px]">
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        Run Now
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        Edit
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700">
                        View Logs
                    </button>
                    <button className="w-full text-left px-3 py-2 text-sm hover:bg-gray-100 dark:hover:bg-gray-700 text-red-600">
                        Delete
                    </button>
                </div>
            )}
        </div>
    );
}

function formatRunTime(timestamp?: string): string {
    if (!timestamp) {
        return 'No runs yet';
    }

    return new Date(timestamp).toLocaleString();
}

function getWorkflowFocusLabel(workflow: Workflow): {
    title: string;
    description: string;
    tone: 'attention' | 'healthy' | 'draft';
} {
    if (workflow.status === 'paused') {
        return {
            title: 'Resume or archive',
            description: 'This pipeline is paused. Decide whether it should resume or leave the active queue.',
            tone: 'attention',
        };
    }

    if (workflow.status === 'draft') {
        return {
            title: 'Finish the first run',
            description: workflow.lastExecutedAt
                ? 'Review the draft and promote it when the current flow is ready.'
                : 'This draft has not been run yet. Validate the outcome and schedule before promotion.',
            tone: 'draft',
        };
    }

    if (workflow.status === 'archived') {
        return {
            title: 'Reference only',
            description: 'Keep this pipeline archived unless a previous flow needs to be restored or compared.',
            tone: 'healthy',
        };
    }

    return {
        title: workflow.lastExecutedAt ? 'Check the latest outcome' : 'Run the first execution',
        description: workflow.lastExecutedAt
            ? 'Active pipeline. Confirm the most recent run completed the intended outcome.'
            : 'Active pipeline with no recorded execution yet. Trigger an initial run before broad rollout.',
        tone: workflow.lastExecutedAt ? 'healthy' : 'attention',
    };
}

function getWorkflowToneClass(tone: 'attention' | 'healthy' | 'draft'): string {
    if (tone === 'attention') {
        return 'border-amber-200 bg-amber-50 dark:border-amber-800 dark:bg-amber-950/30';
    }

    if (tone === 'draft') {
        return 'border-blue-200 bg-blue-50 dark:border-blue-800 dark:bg-blue-950/30';
    }

    return 'border-green-200 bg-green-50 dark:border-green-800 dark:bg-green-950/30';
}

function getWorkflowToneIcon(tone: 'attention' | 'healthy' | 'draft'): React.ReactElement {
    if (tone === 'attention') {
        return <AlertTriangle className="h-4 w-4 text-amber-500" />;
    }

    if (tone === 'draft') {
        return <Clock className="h-4 w-4 text-blue-500" />;
    }

    return <CheckCircle2 className="h-4 w-4 text-green-500" />;
}

// ── Hint type icon mapping ─────────────────────────────────────────────────
function HintTypeIcon({ type }: { type: PipelineOptimisationHint['type'] }) {
    switch (type) {
        case 'performance':     return <Zap       className="h-4 w-4 text-yellow-500" />;
        case 'parallelisation': return <TrendingUp className="h-4 w-4 text-blue-500" />;
        case 'error_handling':  return <Shield    className="h-4 w-4 text-red-500" />;
        case 'data_quality':    return <AlertTriangle className="h-4 w-4 text-amber-500" />;
        case 'cost':            return <BarChart2 className="h-4 w-4 text-green-500" />;
        default:                return <Sparkles  className="h-4 w-4 text-purple-500" />;
    }
}

/**
 * AI Pipeline Optimisation Hints panel.
 *
 * Fetches and renders AI-generated hints for the selected pipeline workflow
 * by calling POST /api/v1/pipelines/:pipelineId/optimise-hint.
 *
 * DC-E5-S1 — AI Journey #4.
 */
function PipelineAiHintsPanel({ pipelineId }: { pipelineId: string }) {
    const { data: capabilityRegistry } = useCapabilityRegistry();
    const aiAssistCapability = getCapabilitySignal(capabilityRegistry?.capabilities, ['ai.assist', 'ai_assist', 'assist']);
    const { data, isLoading, isError } = useQuery({
        queryKey: aiQueryKeys.pipelineHints(pipelineId),
        queryFn: () => getPipelineOptimisationHints(pipelineId),
        staleTime: 5 * 60 * 1_000,
        retry: false,
        refetchOnWindowFocus: false,
        enabled: aiAssistCapability?.status !== 'unavailable',
    });

    const hints = data?.data?.hints ?? [];

    if (aiAssistCapability?.status === 'unavailable') {
        return (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <div className="flex items-center gap-2 font-medium">
                    <AlertTriangle className="h-4 w-4" />
                    {WORKFLOW_HINTS_UNAVAILABLE_TITLE}
                </div>
                <p className="mt-1">
                    {WORKFLOW_HINTS_UNAVAILABLE_DETAIL}
                </p>
            </div>
        );
    }

    if (aiAssistCapability?.status === 'degraded') {
        return (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <div className="flex items-center gap-2 font-medium">
                    <AlertTriangle className="h-4 w-4" />
                    {WORKFLOW_HINTS_DEGRADED_TITLE}
                </div>
                <p className="mt-1">
                    {aiAssistCapability.detail ?? WORKFLOW_HINTS_DEGRADED_DETAIL}
                </p>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="rounded-lg bg-purple-50 dark:bg-purple-950 border border-purple-200 dark:border-purple-800 p-3">
                <div className="flex items-center gap-2 text-sm text-purple-600 dark:text-purple-400">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    Analysing pipeline with AI…
                </div>
            </div>
        );
    }

    if (isError || hints.length === 0) {
        return null; // Fail silently — hints are advisory, not critical
    }

    return (
        <div>
            <h3 className="flex items-center gap-1.5 text-sm font-medium text-purple-700 dark:text-purple-300 mb-2">
                <Sparkles className="h-4 w-4" />
                Inline AI Recommendations
                {data?.data?.hints.some(h => h.fallback) && (
                    <span className="text-xs bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300 px-1.5 py-0.5 rounded ml-1">
                        estimated
                    </span>
                )}
            </h3>
            <div className="rounded-lg bg-purple-50 dark:bg-purple-950 border border-purple-200 dark:border-purple-800 divide-y divide-purple-100 dark:divide-purple-900">
                {hints.map((hint, i) => (
                    <div key={i} className="px-3 py-2.5">
                        <div className="flex items-start gap-2">
                            <div className="mt-0.5 shrink-0">
                                <HintTypeIcon type={hint.type} />
                            </div>
                            <div className="min-w-0 flex-1">
                                <div className="flex items-center gap-2 flex-wrap">
                                    <span className="text-sm font-medium text-gray-900 dark:text-white">
                                        {hint.title}
                                    </span>
                                    <span className={cn(
                                        'text-xs font-medium px-1.5 py-0.5 rounded',
                                        hint.impact === 'high'   ? 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300' :
                                        hint.impact === 'medium' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300' :
                                                                   'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
                                    )}>
                                        {hint.impact} impact
                                    </span>
                                    <span className="text-xs text-gray-400">
                                        {Math.round(hint.confidence * 100)}% confidence
                                    </span>
                                </div>
                                <p className="text-xs text-gray-600 dark:text-gray-400 mt-0.5">
                                    {hint.description}
                                </p>
                            </div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

/**
 * Optimized Workflows Page Component
 */
export function WorkflowsPage() {
    const navigate = useNavigate();
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [selectedWorkflow, setSelectedWorkflow] = useState<Workflow | null>(null);
    const [showAdvancedDetails, setShowAdvancedDetails] = useState(false);

    const { data: workflowsPage, isLoading, refetch } = useQuery({
        queryKey: ['workflows', searchQuery, statusFilter],
        queryFn: () => workflowsApi.list({
            search: searchQuery || undefined,
            status: statusFilter !== 'all' ? statusFilter as Workflow['status'] : undefined,
            pageSize: 50,
        }),
        staleTime: 60_000,
    });

    const workflows = workflowsPage?.items ?? [];
    const visibleWorkflows = workflows.slice(0, 12);
    const workflowsNeedingAttention = workflows.filter((workflow) => workflow.status === 'paused' || (workflow.status === 'draft' && !workflow.lastExecutedAt)).length;
    const workflowsScheduled = workflows.filter((workflow) => workflow.schedule != null && workflow.schedule !== '').length;
    const workflowsRecentlyRun = workflows.filter((workflow) => workflow.lastExecutedAt != null).length;

    const stats = useMemo(() => ({
        total: workflowsPage?.total ?? workflows.length,
        active:   workflows.filter(w => w.status === 'active').length,
        paused:   workflows.filter(w => w.status === 'paused').length,
        draft:    workflows.filter(w => w.status === 'draft').length,
        archived: workflows.filter(w => w.status === 'archived').length,
    }), [workflows, workflowsPage]);

    const statusOptions = [
        { value: 'all',      label: 'All Workflows' },
        { value: 'active',   label: 'Active' },
        { value: 'paused',   label: 'Paused' },
        { value: 'draft',    label: 'Draft' },
        { value: 'archived', label: 'Archived' },
    ];

    return (
        <div className="p-6" data-testid="workflows-page">
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                        Workflows
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        Focus on what needs a run, what is stalled, and what should happen next.
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <button
                        onClick={() => refetch()}
                        className="p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg text-gray-500"
                        title="Refresh"
                    >
                        <RefreshCw className="h-4 w-4" />
                    </button>
                    <Link
                        to="/pipelines/new"
                        data-testid="create-pipeline-button"
                        className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        New Pipeline
                    </Link>
                </div>
            </div>

            <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-900 dark:bg-blue-950/30" data-testid="workflow-outcome-banner">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">Outcome-First View</p>
                        <h2 className="mt-1 text-lg font-semibold text-gray-900 dark:text-white">Keep the list about outcomes, not pipeline internals</h2>
                        <p className="mt-1 text-sm text-blue-900/80 dark:text-blue-100/80">
                            Review the most important next action for each workflow here, then open the advanced editor only when the flow itself needs deeper changes.
                        </p>
                    </div>
                    <button
                        type="button"
                        onClick={() => navigate('/pipelines/new')}
                        className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
                    >
                        Start a new pipeline
                        <ArrowRight className="h-4 w-4" />
                    </button>
                </div>
            </div>

            {/* Stats Cards */}
            <div className="grid grid-cols-2 md:grid-cols-5 gap-4 mb-6">
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <WorkflowIcon className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Total</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.total}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-amber-100 dark:bg-amber-900 rounded-lg">
                            <AlertTriangle className="h-5 w-5 text-amber-600 dark:text-amber-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Needs Attention</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{workflowsNeedingAttention}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-green-100 dark:bg-green-900 rounded-lg">
                            <Play className="h-5 w-5 text-green-600 dark:text-green-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Recently Run</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{workflowsRecentlyRun}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-yellow-100 dark:bg-yellow-900 rounded-lg">
                            <Pause className="h-5 w-5 text-yellow-600 dark:text-yellow-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Scheduled</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{workflowsScheduled}</p>
                        </div>
                    </div>
                </div>
                <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg p-4">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-blue-100 dark:bg-blue-900 rounded-lg">
                            <Clock className="h-5 w-5 text-blue-600 dark:text-blue-400" />
                        </div>
                        <div>
                            <p className="text-sm text-gray-600 dark:text-gray-400">Draft / Archived</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.draft + stats.archived}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Filters */}
            <div className="flex items-center gap-4 mb-6">
                <div className="flex-1 relative">
                    <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 text-gray-400 h-4 w-4" />
                    <input
                        type="text"
                        placeholder="Search workflows by outcome, schedule, or owner..."
                        value={searchQuery}
                        onChange={(e) => setSearchQuery(e.target.value)}
                        className="w-full pl-10 pr-4 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white"
                    />
                </div>
                <div className="flex items-center gap-2">
                    <Filter className="h-4 w-4 text-gray-500" />
                    <select
                        data-testid="workflow-status-filter"
                        value={statusFilter}
                        onChange={(e) => setStatusFilter(e.target.value)}
                        className="px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-white text-sm"
                    >
                        {statusOptions.map(option => (
                            <option key={option.value} value={option.value}>
                                {option.label}
                            </option>
                        ))}
                    </select>
                </div>
            </div>

            {/* Pipelines list */}
            <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
                {isLoading ? (
                    <div className="flex items-center justify-center p-8">
                        <div className="flex items-center gap-2 text-gray-500">
                            <Loader2 className="h-4 w-4 animate-spin" />
                            <span className="text-sm">Loading pipelines...</span>
                        </div>
                    </div>
                ) : workflows.length === 0 ? (
                    <div className="text-center py-12">
                        <WorkflowIcon className="h-12 w-12 text-gray-400 mx-auto mb-4" />
                        <h3 className="text-lg font-medium text-gray-900 dark:text-white mb-2">
                            No workflows found
                        </h3>
                        <p className="text-gray-600 dark:text-gray-400">
                            {searchQuery || statusFilter !== 'all'
                                ? 'Try adjusting your filters'
                                : 'Create your first workflow to get started'}
                        </p>
                    </div>
                ) : (
                    <div className="divide-y divide-gray-200 dark:divide-gray-700">
                        {visibleWorkflows.map((workflow) => {
                            const focus = getWorkflowFocusLabel(workflow);

                            return (
                                <div
                                    key={workflow.id}
                                    className="p-5 transition-colors hover:bg-gray-50 dark:hover:bg-gray-700/40"
                                >
                                    <div className="flex items-start justify-between gap-4">
                                        <button
                                            type="button"
                                            data-testid="workflow-item"
                                            onClick={() => {
                                                setSelectedWorkflow(workflow);
                                                setShowAdvancedDetails(false);
                                            }}
                                            className="flex-1 text-left"
                                        >
                                            <div className="flex items-center gap-3">
                                                <div className="rounded-lg bg-blue-100 p-2 dark:bg-blue-900 shrink-0">
                                                    <WorkflowIcon className="h-4 w-4 text-blue-600 dark:text-blue-300" />
                                                </div>
                                                <div className="min-w-0">
                                                    <div className="flex flex-wrap items-center gap-2">
                                                        <h3 className="text-base font-semibold text-gray-900 dark:text-white">{workflow.name}</h3>
                                                        <span className={cn('px-2 py-1 rounded-full text-xs font-medium capitalize', getStatusColor(workflow.status))}>
                                                            {workflow.status}
                                                        </span>
                                                    </div>
                                                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                                                        {workflow.description || 'No pipeline summary provided yet.'}
                                                    </p>
                                                </div>
                                            </div>
                                        </button>
                                        <div onClick={(e) => e.stopPropagation()}>
                                            <WorkflowActions workflow={workflow} />
                                        </div>
                                    </div>

                                    <div className="mt-4 grid gap-3 md:grid-cols-[minmax(0,1.6fr)_minmax(0,1fr)]">
                                        <div className={cn('rounded-xl border px-4 py-3', getWorkflowToneClass(focus.tone))}>
                                            <div className="flex items-center gap-2 text-sm font-medium text-gray-900 dark:text-white">
                                                {getWorkflowToneIcon(focus.tone)}
                                                {focus.title}
                                            </div>
                                            <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">{focus.description}</p>
                                        </div>

                                        <div className="rounded-xl border border-gray-200 bg-gray-50 px-4 py-3 dark:border-gray-700 dark:bg-gray-900/40">
                                            <div className="grid grid-cols-2 gap-3 text-sm">
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Last run</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{formatRunTime(workflow.lastExecutedAt)}</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Schedule</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{workflow.schedule ?? 'Manual'}</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Flow size</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{workflow.nodes.length} steps / {workflow.edges.length} links</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">Owner</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{workflow.createdBy}</p>
                                                </div>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="mt-4 flex flex-wrap items-center justify-between gap-3">
                                        <div className="flex flex-wrap gap-2">
                                            {workflow.tags.slice(0, 3).map((tag) => (
                                                <span key={tag} className="rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-600 dark:bg-gray-700 dark:text-gray-300">
                                                    {tag}
                                                </span>
                                            ))}
                                            {workflow.tags.length === 0 && (
                                                <span className="text-xs text-gray-400">No tags yet</span>
                                            )}
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <button
                                                type="button"
                                                data-testid={`advanced-editor-${workflow.id}`}
                                                onClick={() => navigate(`/pipelines/${workflow.id}`)}
                                                className="inline-flex items-center gap-2 rounded-lg border border-gray-300 px-3 py-2 text-sm font-medium text-gray-700 hover:bg-gray-100 dark:border-gray-600 dark:text-gray-200 dark:hover:bg-gray-700"
                                            >
                                                Advanced editor
                                                <ArrowRight className="h-4 w-4" />
                                            </button>
                                            <button
                                                type="button"
                                                data-testid={`review-pipeline-${workflow.id}`}
                                                onClick={() => {
                                                    setSelectedWorkflow(workflow);
                                                    setShowAdvancedDetails(false);
                                                }}
                                                className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-3 py-2 text-sm font-medium text-white hover:bg-blue-700"
                                            >
                                                Review pipeline
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* Workflow Detail Modal */}
            {selectedWorkflow && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-2xl w-full mx-4" data-testid="workflow-review-modal">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                                {selectedWorkflow.name}
                            </h2>
                            <button
                                onClick={() => setSelectedWorkflow(null)}
                                className="p-1 hover:bg-gray-100 dark:hover:bg-gray-700 rounded"
                            >
                                <XCircle className="h-5 w-5 text-gray-400" />
                            </button>
                        </div>

                        <div className="space-y-4">
                            <div className={cn('rounded-xl border px-4 py-3', getWorkflowToneClass(getWorkflowFocusLabel(selectedWorkflow).tone))}>
                                <div className="flex items-center gap-2 text-sm font-medium text-gray-900 dark:text-white">
                                    {getWorkflowToneIcon(getWorkflowFocusLabel(selectedWorkflow).tone)}
                                    {getWorkflowFocusLabel(selectedWorkflow).title}
                                </div>
                                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                                    {getWorkflowFocusLabel(selectedWorkflow).description}
                                </p>
                            </div>

                            {selectedWorkflow.description && (
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Outcome summary</h3>
                                    <p className="text-gray-900 dark:text-white">{selectedWorkflow.description}</p>
                                </div>
                            )}

                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Status</h3>
                                    <div className="flex items-center gap-2">
                                        {getStatusIcon(selectedWorkflow.status)}
                                        <span className={cn('px-2 py-1 rounded-full text-xs font-medium capitalize', getStatusColor(selectedWorkflow.status))}>
                                            {selectedWorkflow.status}
                                        </span>
                                    </div>
                                </div>
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Latest run</h3>
                                    <p className="text-gray-900 dark:text-white text-sm">{formatRunTime(selectedWorkflow.lastExecutedAt)}</p>
                                </div>
                            </div>

                            <button
                                type="button"
                                data-testid="workflow-advanced-toggle"
                                onClick={() => setShowAdvancedDetails((current) => !current)}
                                className="inline-flex items-center gap-2 text-sm font-medium text-blue-600 hover:text-blue-700 dark:text-blue-400 dark:hover:text-blue-300"
                            >
                                {showAdvancedDetails ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                                {showAdvancedDetails ? 'Hide pipeline details' : 'Show pipeline details'}
                            </button>

                            {showAdvancedDetails && (
                                <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Schedule</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.schedule ?? 'Manual'}</p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Created</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">
                                                {new Date(selectedWorkflow.createdAt).toLocaleDateString()}
                                            </p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Flow size</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.nodes.length} steps / {selectedWorkflow.edges.length} links</p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Owner</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.createdBy}</p>
                                        </div>
                                    </div>

                                    {selectedWorkflow.tags && selectedWorkflow.tags.length > 0 && (
                                        <div className="mt-4">
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">Tags</h3>
                                            <div className="flex gap-2 flex-wrap">
                                                {selectedWorkflow.tags.map((tag: string) => (
                                                    <span key={tag} className="bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 px-3 py-1 rounded-full text-sm">
                                                        {tag}
                                                    </span>
                                                ))}
                                            </div>
                                        </div>
                                    )}

                                    <div className="mt-4">
                                        <PipelineAiHintsPanel pipelineId={selectedWorkflow.id} />
                                    </div>
                                </div>
                            )}

                            <div className="flex gap-2 pt-4">
                                <button className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
                                    <Play className="h-4 w-4" />
                                    Run Now
                                </button>
                                <button className="flex items-center gap-2 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600">
                                    <Pause className="h-4 w-4" />
                                    Pause
                                </button>
                                <button
                                    onClick={() => navigate(`/pipelines/${selectedWorkflow.id}`)}
                                    className="flex items-center gap-2 px-4 py-2 bg-gray-200 dark:bg-gray-700 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-300 dark:hover:bg-gray-600"
                                >
                                    Advanced editor
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default WorkflowsPage;
