/**
 * Lifecycle Explorer Route
 *
 * Provides an interactive view of the canonical lifecycle phases, artifacts, and transitions.
 * Accessible from the project's main navigation.
 *
 * @doc.type route
 * @doc.purpose Lifecycle phase and artifact navigator
 * @doc.layer product
 * @doc.pattern Route Component
 */

import React from 'react';
import { useParams } from 'react-router';
import { useAuth } from '../../../hooks/useAuth';
import type { LifecycleReviewStatusContract } from '@/contracts/workspace-project';
import { LifecycleExplorer } from '../../../components/lifecycle';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { usePhaseGates } from '../../../services/canvas/lifecycle/PhaseGateService';
import {
    useAIInsights,
    useAIRecommendations,
    useApplyLifecycleAutomationPlan,
    useExecuteTask,
    useLifecycleAutomationPlan,
    useNextBestTask,
    useReadinessAnomalies,
} from '../../../hooks/useLifecycleData';
import { useRequirementOrchestration } from '../../../hooks/useRequirementOrchestration';
import { useAgentRunStream } from '../../../hooks/useAgentRunStream';
import { useLifecycleTransition } from '../../../hooks/useLifecycleTransition';
import { FOW_STAGE_LABELS, getFOWStageForPhase } from '../../../types/fow-stages';
import { LIFECYCLE_PHASE } from '../../../types/lifecycle';
import { PHASE_GATES } from '../../../shared/types/phase-gates';

type RequirementPriority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
type RequirementStatus =
    | 'DRAFT'
    | 'SUBMITTED'
    | 'IN_REVIEW'
    | 'APPROVED'
    | 'REJECTED'
    | 'IMPLEMENTED';

interface RequirementVersion {
    id: string;
    version: number;
    summary: string;
    createdBy: string;
    createdAt: string;
}

interface RequirementRecord {
    id: string;
    title: string;
    description: string;
    priority: RequirementPriority;
    status: RequirementStatus;
    tags?: string[];
    createdAt: string;
    updatedAt: string;
    versions: RequirementVersion[];
}

type ApprovalDecisionStatus =
    | 'PENDING'
    | 'APPROVED'
    | 'REJECTED'
    | 'CHANGES_REQUESTED'
    | 'EXPIRED';

interface ApprovalRecord {
    id: string;
    projectId: string;
    requirementId?: string;
    requestedAction: string;
    status: ApprovalDecisionStatus;
    requesterId: string;
    reviewerId?: string;
    createdAt: string;
    reviewedAt?: string;
    decisionReason?: string;
}

type AgentRunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

interface AgentRunRecord {
    id: string;
    agentName: string;
    status: AgentRunStatus;
    stage: string;
    retryCount: number;
    createdAt: string;
    startedAt?: string;
    completedAt?: string;
    errorMessage?: string;
}

type AuditTimelineLevel = 'INFO' | 'WARN' | 'ERROR';

interface AuditTimelineEntry {
    id: string;
    title: string;
    description?: string;
    actor: string;
    level: AuditTimelineLevel;
    createdAt: string;
}

const RequirementLifecycleBoardClient = React.lazy(async () => {
    const module = await import('../../../components/requirements');
    return { default: module.RequirementLifecycleBoard };
});

const ApprovalInboxClient = React.lazy(async () => {
    const module = await import('../../../components/approvals/ApprovalInbox');
    return { default: module.ApprovalInbox };
});

const ApprovalDetailClient = React.lazy(async () => {
    const module = await import('../../../components/approvals/ApprovalDetail');
    return { default: module.ApprovalDetail };
});

const AgentRunViewerClient = React.lazy(async () => {
    const module = await import('../../../components/agents/AgentRunViewer');
    return { default: module.AgentRunViewer };
});

const AuditTimelineClient = React.lazy(async () => {
    const module = await import('../../../components/audit/AuditTimeline');
    return { default: module.AuditTimeline };
});

const getAnomalySeverityBadgeClass = function (severity = '') {
    switch (String(severity).toUpperCase()) {
        case 'CRITICAL':
            return 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-200';
        case 'WARNING':
            return 'bg-amber-100 text-amber-800 dark:bg-amber-900/30 dark:text-amber-200';
        default:
            return 'bg-slate-100 text-slate-800 dark:bg-slate-900/30 dark:text-slate-200';
    }
};

const formatMetricLabel = function (metric = '') {
    return String(metric)
        .split(/[_-]+/)
        .filter(Boolean)
        .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
        .join(' ');
};

const buildPhaseSummary = function (
    params = {
        stageLabel: '',
        criticalAnomalyCount: 0,
        topRecommendationTitle: '',
        topInsightTitle: '',
    }
) {
    const {
        stageLabel,
        criticalAnomalyCount,
        topRecommendationTitle,
        topInsightTitle,
    } = params;
    if (criticalAnomalyCount > 0) {
        return {
            headline: `${stageLabel} readiness is under active risk.`,
            detail: `${criticalAnomalyCount} critical anomaly signal${criticalAnomalyCount === 1 ? '' : 's'} should be resolved before promotion decisions.`,
        };
    }

    if (topRecommendationTitle && topInsightTitle) {
        return {
            headline: `${stageLabel} is actionable with clear next moves.`,
            detail: `${topRecommendationTitle} is the current priority, while ${topInsightTitle.toLowerCase()} remains the main evidence to review.`,
        };
    }

    if (topRecommendationTitle) {
        return {
            headline: `${stageLabel} has a recommended next action.`,
            detail: topRecommendationTitle,
        };
    }

    if (topInsightTitle) {
        return {
            headline: `${stageLabel} has fresh lifecycle evidence to review.`,
            detail: topInsightTitle,
        };
    }

    return {
        headline: `${stageLabel} has limited signal coverage.`,
        detail:
            'Capture more lifecycle evidence to generate stronger recommendations and readiness guidance.',
    };
};

function buildSeededRequirements(params: {
    recommendations: Array<{
        id: string;
        title: string;
        description: string;
        priority: string;
    }>;
    projectId: string;
}): RequirementRecord[] {
    const { recommendations, projectId } = params;
    if (recommendations.length === 0) {
        return [
            {
                id: `${projectId}-req-bootstrap`,
                title: 'Establish lifecycle baseline requirement',
                description:
                    'Capture foundational lifecycle constraints and acceptance criteria before guided promotion.',
                priority: 'MEDIUM',
                status: 'DRAFT',
                tags: ['lifecycle', 'baseline'],
                createdAt: new Date().toISOString(),
                updatedAt: new Date().toISOString(),
                versions: [
                    {
                        id: `${projectId}-req-bootstrap-v1`,
                        version: 1,
                        summary: 'Initial draft captured from lifecycle route',
                        createdBy: 'system:lifecycle-route',
                        createdAt: new Date().toISOString(),
                    },
                ],
            },
        ];
    }

    return recommendations.slice(0, 3).map((recommendation, index) => {
        const status: RequirementRecord['status'] =
            index === 0 ? 'IN_REVIEW' : index === 1 ? 'SUBMITTED' : 'DRAFT';
        const priority: RequirementRecord['priority'] =
            recommendation.priority === 'HIGH' ? 'HIGH' : 'MEDIUM';
        const now = new Date(Date.now() - index * 15 * 60 * 1000).toISOString();

        return {
            id: `${projectId}-req-${recommendation.id}`,
            title: recommendation.title,
            description: recommendation.description,
            priority,
            status,
            tags: ['ai-assisted', 'lifecycle'],
            createdAt: now,
            updatedAt: now,
            versions: [
                {
                    id: `${recommendation.id}-v1`,
                    version: 1,
                    summary: 'Draft generated from lifecycle recommendation',
                    createdBy: 'agent:lifecycle-recommender',
                    createdAt: now,
                },
            ],
        };
    });
}

function buildSeededApprovals(params: {
    projectId: string;
    requirements: RequirementRecord[];
}): ApprovalRecord[] {
    const { projectId, requirements } = params;
    if (requirements.length === 0) {
        return [];
    }

    const firstRequirement = requirements[0];
    const createdAt = new Date().toISOString();

    return [
        {
            id: `${projectId}-approval-1`,
            projectId,
            requirementId: firstRequirement.id,
            requestedAction: `Approve lifecycle requirement: ${firstRequirement.title}`,
            status: 'PENDING',
            requesterId: 'agent:policy-guardian',
            createdAt,
        },
        {
            id: `${projectId}-approval-2`,
            projectId,
            requirementId: requirements[1]?.id,
            requestedAction: 'Review readiness risk tolerance adjustment',
            status: 'APPROVED',
            requesterId: 'agent:readiness-analyzer',
            reviewerId: 'reviewer:ops-lead',
            createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
            reviewedAt: new Date(Date.now() - 30 * 60 * 1000).toISOString(),
            decisionReason: 'Risk profile aligns with release gate policy.',
        },
    ];
}

function buildSeededRuns(projectId: string): AgentRunRecord[] {
    const now = Date.now();
    return [
        {
            id: `${projectId}-run-1`,
            agentName: 'LifecyclePlannerAgent',
            status: 'RUNNING',
            stage: 'GENERATE',
            retryCount: 0,
            createdAt: new Date(now - 8 * 60 * 1000).toISOString(),
            startedAt: new Date(now - 7 * 60 * 1000).toISOString(),
        },
        {
            id: `${projectId}-run-2`,
            agentName: 'ComplianceGuardAgent',
            status: 'FAILED',
            stage: 'VALIDATE',
            retryCount: 1,
            createdAt: new Date(now - 22 * 60 * 1000).toISOString(),
            startedAt: new Date(now - 20 * 60 * 1000).toISOString(),
            completedAt: new Date(now - 19 * 60 * 1000).toISOString(),
            errorMessage: 'Policy threshold exceeded for production promotion.',
        },
    ];
}

function buildReviewStatus(params: {
    canAutoAdvance?: boolean;
    criticalAnomalyCount: number;
    topRecommendationConfidence?: number;
}): { status: LifecycleReviewStatusContract; label: string; detail: string } {
    const { canAutoAdvance, criticalAnomalyCount, topRecommendationConfidence = 0 } = params;

    if (canAutoAdvance && criticalAnomalyCount === 0 && topRecommendationConfidence >= 0.85) {
        return {
            status: 'ready-for-guided-apply',
            label: 'Guided apply eligible',
            detail: 'Recommendations at or above 85% confidence with no critical anomalies can use the guided apply path.',
        };
    }

    return {
        status: 'review-required',
        label: 'Review required',
        detail: 'Signals below 85% confidence or any critical anomaly stay in a human review path.',
    };
}

export default function Component() {
    const createElement = React.createElement;
    const { projectId } = useParams();
    const resolvedProjectId = projectId ?? '__missing-project__';
    const { hasPermission } = useAuth();
    const isAuthorizedApprover = hasPermission('approvals:decide');
    const { currentPhase, gateStatuses, canTransition } = usePhaseGates(resolvedProjectId);
    const currentStage = getFOWStageForPhase(currentPhase);

    // Derive gate-blocked state for the outgoing transition (C-Y13)
    const nextPhase = React.useMemo(() => {
        const idx = LIFECYCLE_PHASE.indexOf(currentPhase);
        return idx >= 0 && idx < LIFECYCLE_PHASE.length - 1 ? LIFECYCLE_PHASE[idx + 1] : undefined;
    }, [currentPhase]);
    const outgoingGate = React.useMemo(
        () => (nextPhase ? PHASE_GATES.find((g) => g.fromPhase === currentPhase && g.toPhase === nextPhase) : undefined),
        [currentPhase, nextPhase]
    );
    const outgoingGateStatus = outgoingGate ? gateStatuses[outgoingGate.id] : undefined;
    const isGateBlocked =
        outgoingGateStatus?.status === 'blocked' || outgoingGateStatus?.status === 'failed';
    const gateBlockReason = isGateBlocked ? outgoingGateStatus?.blockedReason : undefined;
    const [decisionDetailVisible, setDecisionDetailVisible] = React.useState(false);
    const [clientPanelsReady, setClientPanelsReady] = React.useState(false);

    React.useEffect(() => {
        setClientPanelsReady(true);
    }, []);

    const { data: recommendations = [] } = useAIRecommendations(resolvedProjectId, {
        phase: currentPhase,
        flowStage: currentStage,
        recentActivity: ['lifecycle-route'],
    });
    const { data: insights = [] } = useAIInsights(resolvedProjectId);
    const { data: readinessAnomalies = [] } = useReadinessAnomalies(resolvedProjectId);
    const { data: nextTask } = useNextBestTask(resolvedProjectId, currentPhase);
    const executeTask = useExecuteTask();
    const { data: automationPlan } = useLifecycleAutomationPlan(resolvedProjectId, currentPhase);
    const applyAutomationPlan = useApplyLifecycleAutomationPlan();
    const { submitApproved: submitOrchestration } = useRequirementOrchestration();

    const {
        handleApprovalTransition,
        handleOneClickApproval,
        handleAutomationClick,
        clearFeedback,
        automationFeedback,
        isTransitioning,
    } = useLifecycleTransition({
        projectId: resolvedProjectId,
        submitOrchestration,
        applyAutomationPlan,
        executeTask,
        currentPhase,
    });

    if (!projectId) {
        return createElement(
            'div',
            { className: 'flex min-h-screen items-center justify-center' },
            createElement(
                'div',
                { className: 'text-center' },
                createElement(
                    'h1',
                    { className: 'mb-2 text-2xl font-bold text-text-primary' },
                    'Project Not Found'
                ),
                createElement(
                    'p',
                    { className: 'text-text-secondary' },
                    'Please select a project first.'
                )
            )
        );
    }

    const topRecommendations = React.useMemo(
        () => recommendations.slice(0, 3),
        [recommendations]
    );
    const topInsights = React.useMemo(
        () => insights.slice(0, 3),
        [insights]
    );
    const activeReadinessAnomalies = React.useMemo(
        () =>
            readinessAnomalies
                .filter((alert) => String(alert.status).toUpperCase() !== 'RESOLVED')
                .slice(0, 3),
        [readinessAnomalies]
    );
    const criticalAnomalyCount = React.useMemo(
        () =>
            activeReadinessAnomalies.filter(
                (alert) => String(alert.severity).toUpperCase() === 'CRITICAL'
            ).length,
        [activeReadinessAnomalies]
    );
    const phaseSummary = buildPhaseSummary({
        stageLabel: FOW_STAGE_LABELS[currentStage],
        criticalAnomalyCount,
        topRecommendationTitle: topRecommendations[0]?.title,
        topInsightTitle: topInsights[0]?.title,
    });

    const requirementRecords = React.useMemo(
        () =>
            buildSeededRequirements({
                recommendations: topRecommendations,
                projectId: resolvedProjectId,
            }),
        [topRecommendations, resolvedProjectId]
    );

    const seededApprovals = React.useMemo(
        () =>
            buildSeededApprovals({
                projectId: resolvedProjectId,
                requirements: requirementRecords,
            }),
        [resolvedProjectId, requirementRecords]
    );

    const [approvalRecords, setApprovalRecords] = React.useState<ApprovalRecord[]>(
        seededApprovals
    );

    React.useEffect(() => {
        setApprovalRecords(seededApprovals);
    }, [seededApprovals]);

    const [selectedApprovalId, setSelectedApprovalId] = React.useState<string | undefined>(
        seededApprovals[0]?.id
    );

    React.useEffect(() => {
        if (
            selectedApprovalId &&
            approvalRecords.some((approval) => approval.id === selectedApprovalId)
        ) {
            return;
        }
        setSelectedApprovalId(approvalRecords[0]?.id);
    }, [approvalRecords, selectedApprovalId]);

    const selectedApproval = React.useMemo(
        () => approvalRecords.find((approval) => approval.id === selectedApprovalId),
        [approvalRecords, selectedApprovalId]
    );

    const { runs: agentRuns, setRuns: setAgentRuns } = useAgentRunStream(
        resolvedProjectId,
        { seededRuns: buildSeededRuns(resolvedProjectId) }
    );

    const auditEntries = React.useMemo<AuditTimelineEntry[]>(() => {
        const insightEntries: AuditTimelineEntry[] = topInsights.map((insight, index) => ({
            id: `audit-insight-${insight.id}`,
            title: insight.title,
            description: insight.description ?? 'Lifecycle insight generated by AI pipeline',
            actor:
                typeof insight.metadata?.source === 'string'
                    ? insight.metadata.source
                    : 'agent:lifecycle-insights',
            level: index === 0 ? 'INFO' : 'WARN',
            createdAt: new Date(insight.timestamp).toISOString(),
        }));

        const anomalyEntries: AuditTimelineEntry[] = activeReadinessAnomalies.map((anomaly) => ({
            id: `audit-anomaly-${anomaly.id}`,
            title: `Readiness anomaly: ${formatMetricLabel(anomaly.metric)}`,
            description: anomaly.message,
            actor: 'agent:readiness-monitor',
            level: String(anomaly.severity).toUpperCase() === 'CRITICAL' ? 'ERROR' : 'WARN',
            createdAt: new Date().toISOString(),
        }));

        const combined = [...anomalyEntries, ...insightEntries];
        if (combined.length > 0) {
            return combined;
        }

        return [
            {
                id: 'audit-bootstrap-entry',
                title: 'Lifecycle route initialized',
                description:
                    'The route is ready to capture requirement approvals and agent run telemetry.',
                actor: 'system:lifecycle-route',
                level: 'INFO',
                createdAt: new Date().toISOString(),
            },
        ];
    }, [topInsights, activeReadinessAnomalies]);

    const policyDecisions = React.useMemo(() => {
        if (activeReadinessAnomalies.length === 0) {
            return [
                {
                    id: 'policy-default-review',
                    status: 'REQUIRES_REVIEW' as const,
                    reason: 'No readiness anomalies detected. Keep human review enabled by default.',
                    evaluatedAt: new Date().toISOString(),
                },
            ];
        }

        return activeReadinessAnomalies.slice(0, 3).map((anomaly) => {
            const severity = String(anomaly.severity).toUpperCase();
            return {
                id: `policy-${anomaly.id}`,
                status:
                    severity === 'CRITICAL'
                        ? ('BLOCKED' as const)
                        : ('REQUIRES_REVIEW' as const),
                reason: anomaly.message,
                evaluatedAt: new Date().toISOString(),
            };
        });
    }, [activeReadinessAnomalies]);

    const handleApprovalTransitionLocal = React.useCallback(
        (approvalId: string, nextStatus: ApprovalRecord['status']) => {
            handleApprovalTransition(approvalRecords, setApprovalRecords, approvalId, nextStatus);
        },
        [approvalRecords, handleApprovalTransition]
    );

    const handleAutomationClickLocal = () => {
        if (!nextTask) {
            return;
        }
        void handleAutomationClick(nextTask.id, nextTask.title);
    };

    const handleRetryRun = React.useCallback(
        (runId: string) => {
            setAgentRuns((currentRuns) =>
                currentRuns.map((run) =>
                    run.id === runId
                        ? {
                              ...run,
                              status: 'QUEUED',
                              retryCount: run.retryCount + 1,
                              startedAt: undefined,
                              completedAt: undefined,
                              errorMessage: undefined,
                          }
                        : run
                )
            );
        },
        [setAgentRuns]
    );


    const recommendationsContent =
        topRecommendations.length === 0
            ? createElement(
                    'p',
                    {
                        className:
                            'rounded-xl border border-dashed border-divider px-4 py-4 text-sm text-text-secondary',
                    },
                    'No recommendations are available yet for this project.'
                )
            : createElement(
                    'div',
                    { className: 'space-y-3' },
                    ...topRecommendations.map((recommendation) =>
                        createElement(
                            'article',
                            {
                                key: recommendation.id,
                                className: 'rounded-xl border border-divider px-4 py-4',
                            },
                            createElement(
                                'div',
                                { className: 'flex items-start justify-between gap-4' },
                                createElement(
                                    'div',
                                    null,
                                    createElement(
                                        'p',
                                        { className: 'text-sm font-semibold text-text-primary' },
                                        recommendation.title
                                    ),
                                    createElement(
                                        'p',
                                        { className: 'mt-1 text-sm text-text-secondary' },
                                        recommendation.description
                                    )
                                ),
                                createElement(
                                    'span',
                                    {
                                        className:
                                            'rounded-full bg-amber-100 px-2 py-1 text-[11px] font-semibold uppercase tracking-wide text-amber-800 dark:bg-amber-900/30 dark:text-amber-200',
                                    },
                                    recommendation.priority
                                )
                            ),
                            createElement(
                                'div',
                                { className: 'mt-3 flex flex-wrap gap-3 text-xs text-text-secondary' },
                                createElement(
                                    'span',
                                    null,
                                    `${Math.round(recommendation.confidence * 100)}% confidence`
                                ),
                                createElement('span', null, recommendation.persona),
                                createElement('span', null, recommendation.type)
                            )
                        )
                    )
                );

    const readinessContent =
        activeReadinessAnomalies.length === 0
            ? createElement(
                    'p',
                    {
                        className:
                            'rounded-xl border border-dashed border-divider px-4 py-4 text-sm text-text-secondary',
                    },
                    'No readiness anomalies are currently blocking promotion decisions.'
                )
            : createElement(
                    'div',
                    { className: 'space-y-3' },
                    ...activeReadinessAnomalies.map((alert) =>
                        createElement(
                            'article',
                            {
                                key: alert.id,
                                className: 'rounded-xl border border-divider px-4 py-4',
                                'data-testid': 'readiness-anomaly-alert',
                            },
                            createElement(
                                'div',
                                { className: 'flex items-start justify-between gap-4' },
                                createElement(
                                    'div',
                                    null,
                                    createElement(
                                        'p',
                                        { className: 'text-sm font-semibold text-text-primary' },
                                        formatMetricLabel(alert.metric)
                                    ),
                                    createElement(
                                        'p',
                                        { className: 'mt-1 text-sm text-text-secondary' },
                                        alert.message
                                    )
                                ),
                                createElement(
                                    'span',
                                    {
                                        className: `rounded-full px-2 py-1 text-[11px] font-semibold uppercase tracking-wide ${getAnomalySeverityBadgeClass(alert.severity)}`,
                                    },
                                    alert.severity
                                )
                            ),
                            createElement(
                                'div',
                                { className: 'mt-3 flex flex-wrap gap-3 text-xs text-text-secondary' },
                                createElement('span', null, `Deviation ${alert.deviation}%`),
                                createElement('span', null, `Expected ${alert.expectedValue}`),
                                createElement('span', null, `Actual ${alert.actualValue}`)
                            )
                        )
                    )
                );

    const evidenceContent =
        topInsights.length === 0
            ? createElement(
                    'p',
                    {
                        className:
                            'rounded-xl border border-dashed border-divider px-4 py-4 text-sm text-text-secondary',
                    },
                    'No lifecycle insight evidence has been recorded yet.'
                )
            : createElement(
                    'div',
                    { className: 'space-y-3' },
                    ...topInsights.map((insight) =>
                        createElement(
                            'article',
                            {
                                key: insight.id,
                                className: 'rounded-xl border border-divider px-4 py-4',
                            },
                            createElement(
                                'div',
                                { className: 'flex items-start gap-3' },
                                createElement(
                                    'div',
                                    {
                                        className:
                                            'flex h-8 w-8 items-center justify-center rounded-full bg-primary-50 text-xs font-semibold text-primary-600 dark:bg-primary-900/30 dark:text-primary-300',
                                    },
                                    'IN'
                                ),
                                createElement(
                                    'div',
                                    null,
                                    createElement(
                                        'p',
                                        { className: 'text-sm font-semibold text-text-primary' },
                                        insight.title
                                    ),
                                    createElement(
                                        'p',
                                        { className: 'mt-1 text-sm text-text-secondary' },
                                        insight.description ?? 'No description provided'
                                    ),
                                    createElement(
                                        'div',
                                        { className: 'mt-3 flex flex-wrap gap-3 text-xs text-text-secondary' },
                                        createElement('span', null, insight.type),
                                        createElement('span', null, FOW_STAGE_LABELS[insight.flowStage]),
                                        createElement('span', null, String(insight.timestamp))
                                    )
                                )
                            )
                        )
                    )
                );

    const automationContent = nextTask
        ? createElement(
                'div',
                { className: 'space-y-4' },
                createElement(
                    'div',
                    { className: 'rounded-xl border border-divider px-4 py-4' },
                    createElement(
                        'p',
                        { className: 'text-sm font-semibold text-text-primary' },
                        nextTask.title
                    ),
                    createElement(
                        'p',
                        { className: 'mt-1 text-sm text-text-secondary' },
                        nextTask.description
                    ),
                    createElement(
                        'div',
                        { className: 'mt-3 flex flex-wrap gap-3 text-xs text-text-secondary' },
                        createElement('span', null, `Persona: ${nextTask.persona}`),
                        createElement('span', null, `Priority: ${nextTask.priority}`),
                        createElement('span', null, `Status: ${nextTask.status}`)
                    ),
                    createElement(
                        'button',
                        {
                            type: 'button',
                            className:
                                'mt-4 rounded-lg bg-primary-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-primary-700 disabled:cursor-not-allowed disabled:opacity-50',
                            disabled: executeTask.isPending,
                            'data-testid': 'workflow-automation-trigger',
                            onClick: () => void handleAutomationClick(nextTask.id, nextTask.title),
                        },
                        executeTask.isPending ? 'Starting suggested task…' : 'Start suggested task'
                    ),
                    automationFeedback
                        ? createElement(
                                'p',
                                {
                                    className: 'mt-3 text-xs text-text-secondary',
                                    'data-testid': 'workflow-automation-feedback',
                                },
                                automationFeedback
                            )
                        : null
                )
            )
        : createElement(
                'p',
                {
                    className:
                        'rounded-xl border border-dashed border-divider px-4 py-4 text-sm text-text-secondary',
                },
                'No backed lifecycle task is ready for this stage yet.'
            );

    const decisionSupportContent = automationPlan
        ? createElement(
                'div',
                { className: 'space-y-4' },
                createElement(
                    'div',
                    { className: 'rounded-xl border border-divider px-4 py-4' },
                    createElement(
                        'div',
                        { className: 'flex flex-wrap items-center justify-between gap-3' },
                        createElement(
                            'p',
                            { className: 'text-sm font-semibold text-text-primary' },
                            'Decision defaults'
                        ),
                        createElement(
                            'button',
                            {
                                type: 'button',
                                className:
                                    'rounded-lg border border-divider px-3 py-1.5 text-xs font-medium text-text-secondary hover:bg-bg-subtle',
                                onClick: () => setDecisionDetailVisible((currentValue) => !currentValue),
                                'data-testid': 'decision-support-toggle',
                            },
                            decisionDetailVisible ? 'Hide details' : 'Show details'
                        )
                    ),
                    createElement(
                        'div',
                        { className: 'mt-3 grid gap-2 text-xs text-text-secondary sm:grid-cols-2' },
                        createElement('span', null, `Approval: ${automationPlan.decisionSupport.defaults.approvalMode}`),
                        createElement('span', null, `Risk: ${automationPlan.decisionSupport.defaults.riskTolerance}`),
                        createElement('span', null, `Validation: ${automationPlan.decisionSupport.defaults.validationDepth}`),
                        createElement('span', null, `Owner: ${automationPlan.decisionSupport.defaults.ownerRole}`)
                    ),
                    decisionDetailVisible
                        ? createElement(
                                'div',
                                { className: 'mt-4 space-y-2' },
                                ...automationPlan.decisionSupport.suggestions.map((suggestion) =>
                                    createElement(
                                        'article',
                                        {
                                            key: suggestion.id,
                                            className: 'rounded-lg border border-divider px-3 py-3',
                                        },
                                        createElement(
                                            'p',
                                            { className: 'text-sm font-medium text-text-primary' },
                                            suggestion.title
                                        ),
                                        createElement(
                                            'p',
                                            { className: 'mt-1 text-xs text-text-secondary' },
                                            suggestion.reasoning
                                        )
                                    )
                                )
                            )
                        : null,
                    createElement(
                        'div',
                        { className: 'mt-4 flex flex-wrap gap-3' },
                        createElement(
                            'button',
                            {
                                type: 'button',
                                className:
                                    'rounded-lg bg-emerald-600 px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-emerald-700 disabled:cursor-not-allowed disabled:opacity-50',
                                disabled: isTransitioning || !automationPlan.canAutoAdvance || isGateBlocked,
                                onClick: handleOneClickApproval,
                                'data-testid': 'ai-one-click-approval-trigger',
                                title: gateBlockReason || undefined,
                            },
                            applyAutomationPlan.isPending
                                ? 'Applying guided promotion…'
                                : isGateBlocked
                                    ? 'Gate blocked: resolve requirements first'
                                    : 'Apply guided promotion'
                        ),
                        createElement(
                            'span',
                            { className: 'self-center text-xs text-text-secondary' },
                            `Readiness ${automationPlan.readiness}%`
                        )
                    )
                )
            )
        : createElement(
                'p',
                {
                    className:
                        'rounded-xl border border-dashed border-divider px-4 py-4 text-sm text-text-secondary',
                },
                'Decision support will appear after lifecycle signals are available.'
            );

    const reviewStatus = buildReviewStatus({
        canAutoAdvance: automationPlan?.canAutoAdvance,
        criticalAnomalyCount,
        topRecommendationConfidence: topRecommendations[0]?.confidence,
    });

    return createElement(
        'div',
        { className: 'h-full overflow-auto bg-bg-default' },
        createElement(LifecycleExplorer, { projectId }),
        createElement(
            'section',
            {
                className: 'mx-auto mt-8 flex w-full max-w-6xl flex-col gap-6 px-6 pb-8',
                'data-testid': 'lifecycle-insights-section',
            },
            createElement(
                'div',
                { className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm' },
                createElement(
                    'div',
                    { className: 'flex flex-col gap-2 md:flex-row md:items-start md:justify-between' },
                    createElement(
                        'div',
                        null,
                        createElement(
                            'p',
                            { className: 'text-sm font-semibold uppercase tracking-[0.18em] text-primary-600' },
                            'Lifecycle Summary'
                        ),
                        createElement(
                            'h2',
                            { className: 'mt-2 text-2xl font-semibold text-text-primary' },
                            phaseSummary.headline
                        ),
                        createElement(
                            'p',
                            { className: 'mt-2 max-w-2xl text-sm text-text-secondary' },
                            phaseSummary.detail
                        )
                    ),
                    createElement(
                        'div',
                        {
                            className:
                                'rounded-xl bg-primary-50 px-4 py-3 text-sm text-primary-800 dark:bg-primary-900/30 dark:text-primary-200',
                        },
                        createElement('div', { className: 'font-semibold' }, 'Current stage'),
                        createElement('div', null, FOW_STAGE_LABELS[currentStage])
                    )
                )
            ),
            createElement(
                'section',
                {
                    className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                    'data-testid': 'lifecycle-summary-status-card',
                },
                createElement(
                    'div',
                    { className: 'flex items-start gap-3' },
                    createElement(
                        'div',
                        {
                            className:
                                'flex h-10 w-10 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-300',
                        },
                        'ST'
                    ),
                    createElement(
                        'div',
                        null,
                        createElement(
                            'h3',
                            { className: 'text-lg font-semibold text-text-primary' },
                            'Stage readiness'
                        ),
                        createElement(
                            'p',
                            { className: 'mt-1 text-sm text-text-secondary', 'data-testid': 'decision-review-threshold' },
                            `Review status: ${reviewStatus.label}. ${reviewStatus.detail}`
                        )
                    )
                )
            ),
            createElement(
                'div',
                { className: 'grid gap-6 lg:grid-cols-2' },
                createElement(
                    'div',
                    { className: 'space-y-6' },
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-readiness-anomalies-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-red-50 text-sm font-semibold text-red-600 dark:bg-red-900/30 dark:text-red-300',
                                },
                                'AL'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Readiness anomalies'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Platform anomalies that should influence promotion and deployment decisions.'
                                )
                            )
                        ),
                        readinessContent
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-workflow-automation-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-violet-50 text-sm font-semibold text-violet-600 dark:bg-violet-900/30 dark:text-violet-300',
                                },
                                'WF'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Suggested task'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Run the next backed lifecycle task directly from the active phase surface.'
                                )
                            )
                        ),
                        automationContent
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-recommendations-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-amber-50 text-sm font-semibold text-amber-600 dark:bg-amber-900/30 dark:text-amber-300',
                                },
                                'NX'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Recommended next steps'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Evidence-based actions for the active lifecycle phase.'
                                )
                            )
                        ),
                        recommendationsContent
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-decision-support-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-cyan-50 text-sm font-semibold text-cyan-600 dark:bg-cyan-900/30 dark:text-cyan-300',
                                },
                                'DS'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Decision support'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Evidence-based defaults with explicit review thresholds and progressive disclosure.'
                                )
                            )
                        ),
                        decisionSupportContent
                    )
                ),
                createElement(
                    'div',
                    { className: 'space-y-6' },
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-requirements-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-indigo-50 text-sm font-semibold text-indigo-600 dark:bg-indigo-900/30 dark:text-indigo-300',
                                },
                                'RQ'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Requirement lifecycle'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Track requirement versions and readiness before approvals.'
                                )
                            )
                        ),
                        clientPanelsReady
                            ? createElement(
                                  React.Suspense,
                                  { fallback: createElement('p', { className: 'text-sm text-text-secondary' }, 'Loading requirement lifecycle…') },
                                  createElement(RequirementLifecycleBoardClient, {
                                      requirements: requirementRecords,
                                  })
                              )
                            : createElement('p', { className: 'text-sm text-text-secondary' }, 'Initializing requirement lifecycle…')
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-approval-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-emerald-50 text-sm font-semibold text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-300',
                                },
                                'AP'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Approval and governance'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Decide requirement changes with explicit policy context and audit evidence.'
                                )
                            )
                        ),
                        createElement(
                            'div',
                            { className: 'grid gap-4 xl:grid-cols-2' },
                            clientPanelsReady
                                ? createElement(
                                      React.Suspense,
                                      { fallback: createElement('p', { className: 'text-sm text-text-secondary' }, 'Loading approval inbox…') },
                                      createElement(ApprovalInboxClient, {
                                          approvals: approvalRecords,
                                          selectedApprovalId,
                                          onSelectApproval: setSelectedApprovalId,
                                          onApprove: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'APPROVED'),
                                          onReject: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'REJECTED'),
                                          onRequestChanges: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'CHANGES_REQUESTED'),
                                      })
                                  )
                                : createElement('p', { className: 'text-sm text-text-secondary' }, 'Initializing approval inbox…'),
                            clientPanelsReady && selectedApproval
                                ? createElement(
                                      React.Suspense,
                                      { fallback: createElement('p', { className: 'text-sm text-text-secondary' }, 'Loading approval detail…') },
                                      createElement(ApprovalDetailClient, {
                                          approval: selectedApproval,
                                          aiSummary:
                                              'Lifecycle policy guard evaluated project signals and recommends explicit reviewer confirmation.',
                                          confidence: 0.86,
                                          originalContent: 'Current requirement version is eligible for staged review.',
                                          proposedContent:
                                              'Proposed change aligns with readiness guidance and can be promoted after approval.',
                                          policyDecisions,
                                          isAuthorizedApprover,
                                          onApprove: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'APPROVED'),
                                          onReject: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'REJECTED'),
                                          onRequestChanges: (approvalId: string) =>
                                              handleApprovalTransitionLocal(approvalId, 'CHANGES_REQUESTED'),
                                      })
                                  )
                                : null
                        )
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-evidence-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-sky-50 text-sm font-semibold text-sky-600 dark:bg-sky-900/30 dark:text-sky-300',
                                },
                                'EV'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Observed evidence'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Recent lifecycle insights and audit evidence for this project.'
                                )
                            )
                        ),
                        evidenceContent
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-agent-run-visibility-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-slate-100 text-sm font-semibold text-slate-700 dark:bg-slate-800 dark:text-slate-200',
                                },
                                'AR'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Agent execution visibility'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Inspect stage-level execution progress and retry failed runs.'
                                )
                            )
                        ),
                        clientPanelsReady
                            ? createElement(
                                  React.Suspense,
                                  { fallback: createElement('p', { className: 'text-sm text-text-secondary' }, 'Loading run visibility…') },
                                  createElement(AgentRunViewerClient, {
                                      runs: agentRuns,
                                      onRetryRun: handleRetryRun,
                                  })
                              )
                            : createElement('p', { className: 'text-sm text-text-secondary' }, 'Initializing run visibility…')
                    ),
                    createElement(
                        'section',
                        {
                            className: 'rounded-2xl border border-divider bg-bg-paper p-6 shadow-sm',
                            'data-testid': 'lifecycle-audit-timeline-card',
                        },
                        createElement(
                            'div',
                            { className: 'mb-4 flex items-center gap-3' },
                            createElement(
                                'div',
                                {
                                    className:
                                        'flex h-10 w-10 items-center justify-center rounded-full bg-orange-50 text-sm font-semibold text-orange-600 dark:bg-orange-900/30 dark:text-orange-300',
                                },
                                'AT'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'Audit timeline'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Timeline of readiness events, policy decisions, and lifecycle evidence.'
                                )
                            )
                        ),
                        clientPanelsReady
                            ? createElement(
                                  React.Suspense,
                                  { fallback: createElement('p', { className: 'text-sm text-text-secondary' }, 'Loading audit timeline…') },
                                  createElement(AuditTimelineClient, {
                                      entries: auditEntries,
                                  })
                              )
                            : createElement('p', { className: 'text-sm text-text-secondary' }, 'Initializing audit timeline…')
                    )
                )
            )
        )
    );
}

export function ErrorBoundary() {
    return React.createElement(RouteErrorBoundary, {
        title: 'Lifecycle Explorer Error',
        message: 'Unable to load the lifecycle explorer. Please try refreshing the page.',
    });
}
