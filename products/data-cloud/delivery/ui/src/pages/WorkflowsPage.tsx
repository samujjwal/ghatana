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
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Button, IconButton } from '@ghatana/design-system';
import { getSurfaceSignal, useSurfaceRegistry } from '../api/surfaces.service';
import {
    Play,
    Pause,
    Square,
    Clock,
    XCircle,
    Plus,
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
    MoreVertical,
    Pencil,
    Trash2,
} from 'lucide-react';
import { SearchFilterBar } from '../components/common/SearchFilterBar';
import { LoadingState, EmptyState, NotFoundState } from '../components/common/AsyncStates';
import { TrustBadge } from '../components/governance/TrustSignal';
import { getPipelineOptimisationHints, aiQueryKeys, type PipelineOptimisationHint } from '../api/ai-operations.service';
import { aiOperationsService } from '../api/ai-operations.service';
import { UnsupportedRuntimeBoundaryError } from '../lib/runtime-boundaries';
import {
    WORKFLOW_HINTS_DEGRADED_DETAIL,
    WORKFLOW_HINTS_UNAVAILABLE_DETAIL,
} from '../lib/runtime-boundaries';
import { cn } from '../lib/theme';
import { workflowsApi, type Workflow } from '../lib/api/workflows';

/**
 * Status icon mapping — maps API status to icon
 */
const getStatusIcon = (status: Workflow['status']) => {
    switch (status) {
        case 'active': return <Play className="h-4 w-4 text-green-500" />;
        case 'paused': return <Pause className="h-4 w-4 text-yellow-500" />;
        case 'archived': return <Square className="h-4 w-4 text-gray-500" />;
        case 'draft': return <Clock className="h-4 w-4 text-blue-400" />;
        // no-op to satisfy exhaustive check
        default: return <Clock className="h-4 w-4 text-gray-400" />;
    }
};

/**
 * Status color mapping
 */
const getStatusColor = (status: Workflow['status']) => {
    switch (status) {
        case 'active': return 'bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300';
        case 'paused': return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900 dark:text-yellow-300';
        case 'archived': return 'bg-gray-100 text-gray-700 dark:bg-gray-900 dark:text-gray-300';
        case 'draft': return 'bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300';
        default: return 'bg-gray-100 text-gray-700';
    }
};

function getWorkflowStatusLabel(status: Workflow['status'], t: (key: string) => string): string {
    const translate = (key: string, fallback: string) => {
        const value = t(key);
        return value === key ? fallback : value;
    };

    switch (status) {
        case 'active':
            return translate('workflows.statusActive', 'Active');
        case 'paused':
            return translate('workflows.statusPaused', 'Paused');
        case 'draft':
            return translate('workflows.statusDraft', 'Draft');
        case 'archived':
            return translate('workflows.statusArchived', 'Archived');
        default:
            return status;
    }
}

function getExecutionStatusLabel(
    status: Workflow['lastExecutionStatus'],
    t: (key: string) => string,
): string {
    const translate = (key: string, fallback: string) => {
        const value = t(key);
        return value === key ? fallback : value;
    };

    switch (status) {
        case 'completed':
            return translate('workflows.executionCompleted', 'Completed');
        case 'failed':
            return translate('workflows.executionFailed', 'Failed');
        case 'running':
            return translate('workflows.executionRunning', 'Running');
        case 'cancelled':
            return translate('workflows.executionCancelled', 'Cancelled');
        case 'pending':
            return translate('workflows.executionPending', 'Pending');
        default:
            return String(status ?? '');
    }
}

function formatRunTime(timestamp: string | undefined, t: (key: string) => string): string {
    if (!timestamp) {
        return t('workflows.noRunsYet');
    }

    return new Date(timestamp).toLocaleString();
}

function getWorkflowFocusLabel(workflow: Workflow, t: (key: string) => string): {
    title: string;
    description: string;
    tone: 'attention' | 'healthy' | 'draft';
} {
    if (workflow.status === 'paused') {
        return {
            title: t('workflows.resumeOrArchive'),
            description: t('workflows.resumeOrArchiveDesc'),
            tone: 'attention',
        };
    }

    if (workflow.status === 'draft') {
        return {
            title: t('workflows.finishFirstRun'),
            description: workflow.lastExecutedAt
                ? t('workflows.finishFirstRunDescExecuted')
                : t('workflows.finishFirstRunDescNotExecuted'),
            tone: 'draft',
        };
    }

    if (workflow.status === 'archived') {
        return {
            title: t('workflows.referenceOnly'),
            description: t('workflows.referenceOnlyDesc'),
            tone: 'healthy',
        };
    }

    return {
        title: workflow.lastExecutedAt ? t('workflows.checkLatestOutcome') : t('workflows.runFirstExecution'),
        description: workflow.lastExecutedAt
            ? t('workflows.checkLatestOutcomeDesc')
            : t('workflows.runFirstExecutionDesc'),
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
        case 'performance': return <Zap className="h-4 w-4 text-yellow-500" />;
        case 'parallelisation': return <TrendingUp className="h-4 w-4 text-blue-500" />;
        case 'error_handling': return <Shield className="h-4 w-4 text-red-500" />;
        case 'data_quality': return <AlertTriangle className="h-4 w-4 text-amber-500" />;
        case 'cost': return <BarChart2 className="h-4 w-4 text-green-500" />;
        default: return <Sparkles className="h-4 w-4 text-purple-500" />;
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
    const { t } = useTranslation();
    const { data: surfaceRegistry } = useSurfaceRegistry();
    const aiAssistCapability = getSurfaceSignal(surfaceRegistry?.surfaces, ['ai.assist', 'ai_assist', 'assist']);
    const { data, isLoading, isError } = useQuery({
        queryKey: aiQueryKeys.pipelineHints(pipelineId),
        queryFn: () => getPipelineOptimisationHints(pipelineId),
        staleTime: 5 * 60 * 1_000,
        retry: false,
        refetchOnWindowFocus: false,
        enabled: aiAssistCapability?.status !== 'UNAVAILABLE',
    });

    // AI operations workflow advisories — backend-first, boundary-aware.
    const { data: advisoryData } = useQuery({
        queryKey: ['ai', 'advisories', 'workflows', pipelineId],
        queryFn: () => aiOperationsService.getWorkflowAdvisories(pipelineId),
        staleTime: 5 * 60 * 1_000,
        retry: false,
        refetchOnWindowFocus: false,
        enabled: aiAssistCapability?.status !== 'UNAVAILABLE',
    });
    const advisories = advisoryData?.advisories ?? [];

    const hints = data?.data?.hints ?? [];

    if (aiAssistCapability?.status === 'UNAVAILABLE') {
        return (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <div className="flex items-center gap-2 font-medium">
                    <AlertTriangle className="h-4 w-4" />
                    {t('workflows.aiUnavailable')}
                </div>
                <p className="mt-1">
                    {WORKFLOW_HINTS_UNAVAILABLE_DETAIL || t('workflows.aiUnavailableDesc')}
                </p>
            </div>
        );
    }

    if (aiAssistCapability?.status === 'DEGRADED') {
        return (
            <div className="rounded-lg border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-800 dark:bg-amber-950/40 dark:text-amber-200">
                <div className="flex items-center gap-2 font-medium">
                    <AlertTriangle className="h-4 w-4" />
                    {t('workflows.aiDegraded')}
                </div>
                <p className="mt-1">
                    {aiAssistCapability.detail ?? WORKFLOW_HINTS_DEGRADED_DETAIL ?? t('workflows.aiDegradedDesc')}
                </p>
            </div>
        );
    }

    if (isLoading) {
        return (
            <div className="rounded-lg bg-purple-50 dark:bg-purple-950 border border-purple-200 dark:border-purple-800 p-3">
                <div className="flex items-center gap-2 text-sm text-purple-600 dark:text-purple-400">
                    <Loader2 className="h-4 w-4 animate-spin" />
                    {t('workflows.analyzingPipeline')}
                </div>
            </div>
        );
    }

    if (isError || hints.length === 0) {
        return null; // Hints are advisory only; an empty result does not block the page.
    }

    // Render advisories from ai-operations.service below the hints list when available.
    const advisoriesSection = advisories.length > 0 ? (
        <div className="mt-3">
            <h3 className="flex items-center gap-1.5 text-sm font-medium text-blue-700 dark:text-blue-300 mb-2">
                <Zap className="h-4 w-4" />
                {t('workflows.operationalAdvisories')}
            </h3>
            <div className="rounded-lg bg-blue-50 dark:bg-blue-950 border border-blue-200 dark:border-blue-800 divide-y divide-blue-100 dark:divide-blue-900">
                {advisories.slice(0, 3).map((advisory) => (
                    <div key={advisory.id} className="px-3 py-2.5">
                        <div className="flex items-start gap-2">
                            <span className={cn(
                                'mt-0.5 shrink-0 text-xs font-medium px-1.5 py-0.5 rounded uppercase',
                                advisory.priority === 'critical' ? 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300' :
                                    advisory.priority === 'high' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300' :
                                        'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
                            )}>
                                {advisory.priority === 'critical'
                                    ? t('workflows.priorityCritical')
                                    : advisory.priority === 'high'
                                        ? t('workflows.priorityHigh')
                                        : advisory.priority === 'medium'
                                            ? t('workflows.priorityMedium')
                                            : t('workflows.priorityLow')}
                            </span>
                            <div className="min-w-0 flex-1">
                                <p className="text-sm font-medium text-gray-900 dark:text-white">{advisory.title}</p>
                                <p className="text-xs text-gray-600 dark:text-gray-400 mt-0.5">{advisory.description}</p>
                            </div>
                            <span className="shrink-0 text-xs text-gray-400">
                                {t('workflows.confidencePercent', { value: Math.round(advisory.confidence * 100) })}
                            </span>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    ) : null;

    return (
        <div>
            <h3 className="flex items-center gap-1.5 text-sm font-medium text-purple-700 dark:text-purple-300 mb-2">
                <Sparkles className="h-4 w-4" />
                {t('workflows.inlineAIRecommendations')}
                {data?.data?.hints.some(h => h.fallback) && (
                    <span className="text-xs bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300 px-1.5 py-0.5 rounded ml-1">
                        {t('workflows.estimated')}
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
                                        hint.impact === 'high' ? 'bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300' :
                                            hint.impact === 'medium' ? 'bg-amber-100 text-amber-700 dark:bg-amber-900 dark:text-amber-300' :
                                                'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
                                    )}>
                                        {hint.impact === 'high'
                                            ? t('workflows.highImpact')
                                            : hint.impact === 'medium'
                                                ? t('workflows.mediumImpact')
                                                : t('workflows.lowImpact')}
                                    </span>
                                    <span className="text-xs text-gray-400">
                                        {t('workflows.confidencePercent', { value: Math.round(hint.confidence * 100) })}
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
            {advisoriesSection}
        </div>
    );
}

/**
 * Per-workflow action menu — Run Now, Edit, View Logs, Delete.
 *
 * All actions are wired to real pipeline API calls.
 */
function WorkflowActions({ workflow }: { workflow: Workflow }) {
    const { t } = useTranslation();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [menuOpen, setMenuOpen] = useState(false);

    const runMutation = useMutation({
        mutationFn: () => workflowsApi.execute(workflow.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['workflows'] });
            setMenuOpen(false);
        },
    });

    const deleteMutation = useMutation({
        mutationFn: () => workflowsApi.delete(workflow.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['workflows'] });
            setMenuOpen(false);
        },
    });

    const handleDelete = () => {
        setMenuOpen(false);
        if (window.confirm(t('workflows.deleteWorkflowConfirm', { name: workflow.name }))) {
            deleteMutation.mutate();
        }
    };

    return (
        <div className="relative" data-testid={`workflow-actions-${workflow.id}`}>
            <IconButton
                variant="ghost"
                tone="neutral"
                icon={<MoreVertical className="h-4 w-4" />}
                label={t('workflows.workflowActions')}
                onClick={() => setMenuOpen((prev) => !prev)}
            />
            {menuOpen && (
                <div
                    role="menu"
                    className="absolute right-0 z-10 mt-1 w-44 rounded-lg border border-gray-200 bg-white shadow-lg dark:border-gray-700 dark:bg-gray-800"
                >
                    <button
                        type="button"
                        role="menuitem"
                        disabled={runMutation.isPending}
                        onClick={() => { runMutation.mutate(); }}
                        className="flex w-full items-center gap-2 rounded-t-lg px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 disabled:cursor-not-allowed disabled:opacity-50 dark:text-gray-300 dark:hover:bg-gray-700"
                    >
                        <Play className="h-3.5 w-3.5 text-green-500" />
                        {t('workflows.runNow')}
                    </button>
                    <button
                        type="button"
                        role="menuitem"
                        onClick={() => { setMenuOpen(false); navigate(`/pipelines/${workflow.id}`); }}
                        className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
                    >
                        <Pencil className="h-3.5 w-3.5" />
                        {t('workflows.edit')}
                    </button>
                    <button
                        type="button"
                        role="menuitem"
                        onClick={() => { setMenuOpen(false); navigate(`/pipelines/${workflow.id}/executions`); }}
                        className="flex w-full items-center gap-2 px-3 py-2 text-sm text-gray-700 hover:bg-gray-100 dark:text-gray-300 dark:hover:bg-gray-700"
                    >
                        <BarChart2 className="h-3.5 w-3.5" />
                        {t('workflows.viewLogs')}
                    </button>
                    <button
                        type="button"
                        role="menuitem"
                        disabled={deleteMutation.isPending}
                        onClick={handleDelete}
                        className="flex w-full items-center gap-2 rounded-b-lg px-3 py-2 text-sm text-red-600 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-50 dark:text-red-400 dark:hover:bg-red-950/30"
                    >
                        <Trash2 className="h-3.5 w-3.5" />
                        {t('workflows.delete')}
                    </button>
                </div>
            )}
        </div>
    );
}

/**
 * Optimized Workflows Page Component
 */
export function WorkflowsPage() {
    const { t } = useTranslation();
    const translateOrFallback = (key: string, fallback: string) => {
        const value = t(key);
        return value === key ? fallback : value;
    };
    const navigate = useNavigate();
    const [searchQuery, setSearchQuery] = useState('');
    const [statusFilter, setStatusFilter] = useState<string>('all');
    const [selectedWorkflow, setSelectedWorkflow] = useState<Workflow | null>(null);
    const [showAdvancedDetails, setShowAdvancedDetails] = useState(false);
    // DC-UX-017: Pagination state — replaces hard slice(0, 12)
    const [currentPage, setCurrentPage] = useState(1);
    const PAGE_SIZE = 20;

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
    // DC-UX-017: Show all workflows with pagination instead of a silent slice(0, 12)
    const totalWorkflows = workflowsPage?.total ?? workflows.length;
    const totalPages = Math.max(1, Math.ceil(workflows.length / PAGE_SIZE));
    const visibleWorkflows = workflows.slice((currentPage - 1) * PAGE_SIZE, currentPage * PAGE_SIZE);
    const workflowsNeedingAttention = workflows.filter((workflow) => workflow.status === 'paused' || (workflow.status === 'draft' && !workflow.lastExecutedAt)).length;
    const workflowsScheduled = workflows.filter((workflow) => workflow.schedule != null && workflow.schedule !== '').length;
    const workflowsRecentlyRun = workflows.filter((workflow) => workflow.lastExecutedAt != null).length;

    const stats = useMemo(() => ({
        total: workflowsPage?.total ?? workflows.length,
        active: workflows.filter(w => w.status === 'active').length,
        paused: workflows.filter(w => w.status === 'paused').length,
        draft: workflows.filter(w => w.status === 'draft').length,
        archived: workflows.filter(w => w.status === 'archived').length,
    }), [workflows, workflowsPage]);

    const statusOptions = [
        { value: 'all', label: t('workflows.statusAll') },
        { value: 'active', label: t('workflows.statusActive') },
        { value: 'paused', label: t('workflows.statusPaused') },
        { value: 'draft', label: t('workflows.statusDraft') },
        { value: 'archived', label: t('workflows.statusArchived') },
    ];

    return (
        <section className="p-6" data-testid="workflows-page" aria-label={translateOrFallback('workflows.title', 'Workflows')}>
            {/* Header */}
            <div className="flex items-center justify-between mb-6">
                <div>
                    <h1 className="text-2xl font-bold text-gray-900 dark:text-white mb-2">
                        {t('workflows.title')}
                    </h1>
                    <p className="text-gray-600 dark:text-gray-400">
                        {t('workflows.subtitle')}
                    </p>
                </div>
                <div className="flex items-center gap-2">
                    <IconButton
                        variant="ghost"
                        tone="neutral"
                        icon={<RefreshCw className="h-4 w-4" />}
                        label={t('workflows.refreshWorkflows')}
                        onClick={() => void refetch()}
                    />
                    {/* DC-UX-020: single creation entry point — SmartWorkflowBuilder at /workflows/new */}
                    <Link
                        to="/workflows/new"
                        data-testid="create-pipeline-button"
                        className="flex items-center gap-2 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
                    >
                        <Plus className="h-4 w-4" />
                        {t('workflows.newPipeline')}
                    </Link>
                </div>
            </div>

            <div className="mb-6 rounded-xl border border-blue-200 bg-blue-50 p-4 dark:border-blue-900 dark:bg-blue-950/30" data-testid="workflow-outcome-banner">
                <div className="flex items-start justify-between gap-4">
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700 dark:text-blue-300">{t('workflows.outcomeFirstView')}</p>
                        <h2 className="mt-1 text-lg font-semibold text-gray-900 dark:text-white">{t('workflows.outcomeFirstHeading')}</h2>
                        <p className="mt-1 text-sm text-blue-900/80 dark:text-blue-100/80">
                            {t('workflows.outcomeFirstDescription')}
                        </p>
                    </div>
                    <Button
                        variant="solid"
                        type="button"
                        onClick={() => navigate('/workflows/new')}
                        leadingIcon={<ArrowRight className="h-4 w-4" />}
                    >
                        {t('workflows.startNewPipeline')}
                    </Button>
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">{t('workflows.statsTotal')}</p>
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">{t('workflows.statsNeedsAttention')}</p>
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">{t('workflows.statsRecentlyRun')}</p>
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">{t('workflows.statsScheduled')}</p>
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
                            <p className="text-sm text-gray-600 dark:text-gray-400">{t('workflows.statsDraftArchived')}</p>
                            <p className="text-xl font-bold text-gray-900 dark:text-white">{stats.draft + stats.archived}</p>
                        </div>
                    </div>
                </div>
            </div>

            {/* Filters */}
            <div className="mb-6">
                <SearchFilterBar
                    searchQuery={searchQuery}
                    onSearchChange={setSearchQuery}
                    searchPlaceholder={t('workflows.searchPlaceholder')}
                    filters={[
                        {
                            id: 'workflow-status-filter',
                            label: t('workflows.statusFilter'),
                            value: statusFilter,
                            options: statusOptions,
                            onChange: setStatusFilter,
                        },
                    ]}
                    hasActiveFilters={searchQuery.length > 0 || statusFilter !== 'all'}
                    onClear={() => {
                        setSearchQuery('');
                        setStatusFilter('all');
                    }}
                />
            </div>

            {/* Pipelines list */}
            <div className="bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-lg">
                {isLoading ? (
                    <LoadingState message={t('loading.pipelines')} />
                ) : workflows.length === 0 ? (
                    <EmptyState
                        title={t('workflows.noWorkflowsFound')}
                        description={
                            searchQuery || statusFilter !== 'all'
                                ? t('workflows.tryAdjustingFilters')
                                : t('workflows.createFirstWorkflow')
                        }
                        icon={<WorkflowIcon className="h-12 w-12 text-gray-400 mb-4" />}
                    />
                ) : (
                    <div className="divide-y divide-gray-200 dark:divide-gray-700">
                        {visibleWorkflows.map((workflow) => {
                            const focus = getWorkflowFocusLabel(workflow, t);

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
                                                        <span className={cn('px-2 py-1 rounded-full text-xs font-medium', getStatusColor(workflow.status))}>
                                                            {getWorkflowStatusLabel(workflow.status, t)}
                                                        </span>
                                                    </div>
                                                    <p className="mt-1 text-sm text-gray-500 dark:text-gray-400 line-clamp-2">
                                                        {workflow.description || t('workflows.noSummaryYet')}
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
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">{t('workflows.lastRun')}</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{formatRunTime(workflow.lastExecutedAt, t)}</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">{t('workflows.schedule')}</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{workflow.schedule ?? t('workflows.manual')}</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">{t('workflows.flowSize')}</p>
                                                    <p className="mt-1 font-medium text-gray-900 dark:text-white">{t('workflows.stepsLinks', { steps: workflow.nodes.length, links: workflow.edges.length })}</p>
                                                </div>
                                                <div>
                                                    <p className="text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">{t('workflows.owner')}</p>
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
                                                <span className="text-xs text-gray-400">{t('workflows.noTagsYet')}</span>
                                            )}
                                        </div>
                                        <div className="flex items-center gap-2">
                                            <Button
                                                type="button"
                                                variant="outline"
                                                size="sm"
                                                trailingIcon={<ArrowRight className="h-4 w-4" />}
                                                data-testid={`advanced-editor-${workflow.id}`}
                                                onClick={() => navigate(`/pipelines/${workflow.id}`)}
                                            >
                                                {translateOrFallback('workflows.advancedEditor', 'Advanced editor')}
                                            </Button>
                                            <Button
                                                type="button"
                                                variant="solid"
                                                size="sm"
                                                data-testid={`review-pipeline-${workflow.id}`}
                                                onClick={() => {
                                                    setSelectedWorkflow(workflow);
                                                    setShowAdvancedDetails(false);
                                                }}
                                            >
                                                {t('workflows.reviewPipeline')}
                                            </Button>
                                        </div>
                                    </div>
                                </div>
                            );
                        })}
                    </div>
                )}
            </div>

            {/* DC-UX-017: Pagination controls — replaces silent slice(0, 12) */}
            {workflows.length > PAGE_SIZE && (
                <div className="mt-4 flex items-center justify-between text-sm text-gray-600 dark:text-gray-400">
                    <span>
                        {t('workflows.showingPagination', {
                            start: ((currentPage - 1) * PAGE_SIZE) + 1,
                            end: Math.min(currentPage * PAGE_SIZE, workflows.length),
                            count: workflows.length,
                        })}
                    </span>
                    <div className="flex items-center gap-2">
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
                            disabled={currentPage === 1}
                        >
                            {t('workflows.previous')}
                        </Button>
                        <span className="px-2">{t('workflows.pageOf', { page: currentPage, total: totalPages })}</span>
                        <Button
                            variant="outline"
                            size="sm"
                            onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
                            disabled={currentPage === totalPages}
                        >
                            {t('workflows.next')}
                        </Button>
                    </div>
                </div>
            )}

            {/* Workflow Detail Modal */}
            {selectedWorkflow && (
                <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
                    <div className="bg-white dark:bg-gray-800 rounded-lg p-6 max-w-2xl w-full mx-4" data-testid="workflow-review-modal">
                        <div className="flex items-center justify-between mb-4">
                            <h2 className="text-xl font-bold text-gray-900 dark:text-white">
                                {selectedWorkflow.name}
                            </h2>
                            <IconButton
                                variant="ghost"
                                tone="neutral"
                                icon={<XCircle className="h-5 w-5" />}
                                label={t('workflows.closeWorkflowDetail')}
                                onClick={() => setSelectedWorkflow(null)}
                            />
                        </div>

                        <div className="space-y-4">
                            <div className={cn('rounded-xl border px-4 py-3', getWorkflowToneClass(getWorkflowFocusLabel(selectedWorkflow, t).tone))}>
                                <div className="flex items-center gap-2 text-sm font-medium text-gray-900 dark:text-white">
                                    {getWorkflowToneIcon(getWorkflowFocusLabel(selectedWorkflow, t).tone)}
                                    {getWorkflowFocusLabel(selectedWorkflow, t).title}
                                </div>
                                <p className="mt-1 text-sm text-gray-600 dark:text-gray-300">
                                    {getWorkflowFocusLabel(selectedWorkflow, t).description}
                                </p>
                            </div>

                            {selectedWorkflow.description && (
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.outcomeSummary')}</h3>
                                    <p className="text-gray-900 dark:text-white">{selectedWorkflow.description}</p>
                                </div>
                            )}

                            {/* Trust Signals — pipeline policy impact */}
                            <div className="rounded-lg border border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-900/30 p-3 flex items-center gap-3 flex-wrap" data-testid="pipeline-trust-signals">
                                <div className="flex items-center gap-2 text-xs text-gray-600 dark:text-gray-400">
                                    <Shield className="h-4 w-4 text-blue-500" />
                                    <span className="font-medium">{t('workflows.policyImpact')}</span>
                                </div>
                                <TrustBadge status="compliant" label={t('workflows.trustTenantScopedMovement')} />
                                {selectedWorkflow.nodes.some((n: Workflow['nodes'][number]) => n.type === 'sink') && (
                                    <TrustBadge status="pending-review" label={t('workflows.trustExternalSink')} />
                                )}
                                {selectedWorkflow.nodes.length > 5 && (
                                    <TrustBadge status="warning" label={t('workflows.trustComplexFlow', { steps: selectedWorkflow.nodes.length })} />
                                )}
                            </div>

                            {/* DC-UX-019: Execution visibility — show outcome + duration next to timestamp */}
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.statusFilter')}</h3>
                                    <div className="flex items-center gap-2">
                                        {getStatusIcon(selectedWorkflow.status)}
                                        <span className={cn('px-2 py-1 rounded-full text-xs font-medium capitalize', getStatusColor(selectedWorkflow.status))}>
                                            {selectedWorkflow.status}
                                        </span>
                                    </div>
                                </div>
                                <div>
                                    <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.latestRun')}</h3>
                                    <p className="text-gray-900 dark:text-white text-sm">{formatRunTime(selectedWorkflow.lastExecutedAt, t)}</p>
                                    {selectedWorkflow.lastExecutionStatus && (
                                        <p className={cn(
                                            'mt-1 text-xs font-medium capitalize',
                                            selectedWorkflow.lastExecutionStatus === 'completed' && 'text-green-600 dark:text-green-400',
                                            selectedWorkflow.lastExecutionStatus === 'failed' && 'text-red-600 dark:text-red-400',
                                            selectedWorkflow.lastExecutionStatus === 'running' && 'text-blue-600 dark:text-blue-400',
                                            selectedWorkflow.lastExecutionStatus === 'cancelled' && 'text-gray-500',
                                            selectedWorkflow.lastExecutionStatus === 'pending' && 'text-amber-600 dark:text-amber-400',
                                        )}>
                                            {getExecutionStatusLabel(selectedWorkflow.lastExecutionStatus, t)}
                                            {selectedWorkflow.lastExecutionDuration != null && ` · ${(selectedWorkflow.lastExecutionDuration / 1000).toFixed(1)}s`}
                                        </p>
                                    )}
                                    {!selectedWorkflow.lastExecutionStatus && selectedWorkflow.lastExecutedAt && (
                                        <p className="mt-1 text-xs text-gray-400">{t('workflows.outcomeNotAvailable')}</p>
                                    )}
                                </div>
                            </div>

                            <Button
                                type="button"
                                variant="link"
                                data-testid="workflow-advanced-toggle"
                                leadingIcon={showAdvancedDetails ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
                                onClick={() => setShowAdvancedDetails((current) => !current)}
                            >
                                {showAdvancedDetails ? t('workflows.hidePipelineDetails') : t('workflows.showPipelineDetails')}
                            </Button>

                            {showAdvancedDetails && (
                                <div className="rounded-xl border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900/40">
                                    <div className="grid grid-cols-2 gap-4">
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.schedule')}</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.schedule ?? t('workflows.manual')}</p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.created')}</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">
                                                {new Date(selectedWorkflow.createdAt).toLocaleDateString()}
                                            </p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.flowSize')}</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{t('workflows.stepsLinks', { steps: selectedWorkflow.nodes.length, links: selectedWorkflow.edges.length })}</p>
                                        </div>
                                        <div>
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.owner')}</h3>
                                            <p className="text-gray-900 dark:text-white text-sm">{selectedWorkflow.createdBy}</p>
                                        </div>
                                    </div>

                                    {selectedWorkflow.tags && selectedWorkflow.tags.length > 0 && (
                                        <div className="mt-4">
                                            <h3 className="text-sm font-medium text-gray-600 dark:text-gray-400 mb-2">{t('workflows.tags')}</h3>
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
                                <Button
                                    variant="outline"
                                    onClick={() => navigate(`/pipelines/${selectedWorkflow.id}`)}
                                >
                                    {translateOrFallback('workflows.advancedEditor', 'Advanced editor')}
                                </Button>
                            </div>
                        </div>
                    </div>
                </div>
            )}
        </section>
    );
}

export default WorkflowsPage;
