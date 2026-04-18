/**
 * Lifecycle Explorer Route
 *
 * Provides an interactive view of all 7 lifecycle phases, artifacts, and transitions.
 * Accessible from the project's main navigation.
 *
 * @doc.type route
 * @doc.purpose Lifecycle phase and artifact navigator
 * @doc.layer product
/**
 * Lifecycle Explorer Route
 *
 * Provides an interactive view of all 7 lifecycle phases, artifacts, and transitions.
 * Accessible from the project's main navigation.
 *
 * @doc.type route
 * @doc.purpose Lifecycle phase and artifact navigator
 * @doc.layer product
 * @doc.pattern Route Component
 */

import React from 'react';
import { useParams } from 'react-router';
import { LifecycleExplorer } from '../../../components/lifecycle';
import { RouteErrorBoundary } from '../../../components/route/ErrorBoundary';
import { usePhaseGates } from '../../../services/canvas/lifecycle';
import {
    useAIInsights,
    useAIRecommendations,
    useApplyLifecycleAutomationPlan,
    useExecuteTask,
    useLifecycleAutomationPlan,
    useNextBestTask,
    useReadinessAnomalies,
} from '../../../hooks/useLifecycleData';
import { FOW_STAGE_LABELS, getFOWStageForPhase } from '../../../types/fow-stages';

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
            headline: `AI phase summary: ${stageLabel} readiness is under active risk.`,
            detail: `${criticalAnomalyCount} critical anomaly signal${criticalAnomalyCount === 1 ? '' : 's'} should be resolved before promotion decisions.`,
        };
    }

    if (topRecommendationTitle && topInsightTitle) {
        return {
            headline: `AI phase summary: ${stageLabel} is actionable with clear next moves.`,
            detail: `${topRecommendationTitle} is the current priority, while ${topInsightTitle.toLowerCase()} remains the main evidence to review.`,
        };
    }

    if (topRecommendationTitle) {
        return {
            headline: `AI phase summary: ${stageLabel} has a recommended next action.`,
            detail: topRecommendationTitle,
        };
    }

    if (topInsightTitle) {
        return {
            headline: `AI phase summary: ${stageLabel} has fresh lifecycle evidence to review.`,
            detail: topInsightTitle,
        };
    }

    return {
        headline: `AI phase summary: ${stageLabel} has limited signal coverage.`,
        detail:
            'Capture more lifecycle evidence to generate stronger recommendations and readiness guidance.',
    };
};

export default function Component() {
    const createElement = React.createElement;
    const { projectId } = useParams();
    const resolvedProjectId = projectId ?? '__missing-project__';
    const { currentPhase } = usePhaseGates(resolvedProjectId);
    const currentStage = getFOWStageForPhase(currentPhase);
    const [automationFeedback, setAutomationFeedback] = React.useState('');
    const [decisionDetailVisible, setDecisionDetailVisible] = React.useState(false);

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

    const topRecommendations = recommendations.slice(0, 3);
    const topInsights = insights.slice(0, 3);
    const activeReadinessAnomalies = readinessAnomalies
        .filter((alert) => String(alert.status).toUpperCase() !== 'RESOLVED')
        .slice(0, 3);
    const criticalAnomalyCount = activeReadinessAnomalies.filter(
        (alert) => String(alert.severity).toUpperCase() === 'CRITICAL'
    ).length;
    const phaseSummary = buildPhaseSummary({
        stageLabel: FOW_STAGE_LABELS[currentStage],
        criticalAnomalyCount,
        topRecommendationTitle: topRecommendations[0]?.title,
        topInsightTitle: topInsights[0]?.title,
    });

    const handleAutomationClick = () => {
        if (!nextTask) {
            return;
        }

        void (async () => {
            try {
                const result = await executeTask.mutateAsync({
                    taskId: nextTask.id,
                    input: {
                        projectId: resolvedProjectId,
                        source: 'lifecycle-route',
                        phase: currentPhase,
                    },
                });

                // Handle queued status when CI/CD adapter is not connected
                if (result && typeof result === 'object' && 'status' in result && result.status === 'queued') {
                    const message = 'message' in result && typeof result.message === 'string'
                        ? result.message
                        : 'Task queued for execution. CI/CD adapter not yet connected.';
                    setAutomationFeedback(message);
                } else {
                    setAutomationFeedback(`Automation started for ${nextTask.title}.`);
                }
            } catch (error) {
                setAutomationFeedback(
                    error instanceof Error
                        ? error.message
                        : 'Unable to start workflow automation.'
                );
            }
        })();
    };

    const handleOneClickApproval = () => {
        void (async () => {
            try {
                const result = await applyAutomationPlan.mutateAsync({
                    projectId: resolvedProjectId,
                    request: {
                        phase: currentPhase,
                        oneClickApprove: true,
                        reason: 'Applied from lifecycle route decision support panel',
                    },
                });

                if (result.execution?.transitioned) {
                    setAutomationFeedback(
                        `AI one-click approval transitioned ${result.execution.previousPhase} to ${result.execution.currentPhase}.`
                    );
                } else if (result.canAutoAdvance) {
                    setAutomationFeedback('Automation plan refreshed. Project is ready for one-click promotion.');
                } else {
                    setAutomationFeedback('Automation plan refreshed. Resolve blockers before one-click promotion.');
                }
            } catch (error) {
                setAutomationFeedback(
                    error instanceof Error
                        ? error.message
                        : 'Unable to apply one-click lifecycle approval.'
                );
            }
        })();
    };

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
                            onClick: handleAutomationClick,
                        },
                        executeTask.isPending ? 'Starting automation…' : 'Start automation'
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
                'No workflow automation is ready for this lifecycle stage yet.'
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
                            'AI decision defaults'
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
                                disabled: applyAutomationPlan.isPending || !automationPlan.canAutoAdvance,
                                onClick: handleOneClickApproval,
                                'data-testid': 'ai-one-click-approval-trigger',
                            },
                            applyAutomationPlan.isPending
                                ? 'Applying one-click approval…'
                                : 'Apply one-click approval'
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
                            'Learn And Evolve'
                        ),
                        createElement(
                            'h2',
                            { className: 'mt-2 text-2xl font-semibold text-text-primary' },
                            'Signals for the current lifecycle stage'
                        ),
                        createElement(
                            'p',
                            { className: 'mt-2 max-w-2xl text-sm text-text-secondary' },
                            'Recommendations and evidence are surfaced directly in the lifecycle route so the Improve and Observe phases are visible in the live product flow.'
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
                    'data-testid': 'lifecycle-phase-summary-card',
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
                        'AI'
                    ),
                    createElement(
                        'div',
                        null,
                        createElement(
                            'h3',
                            { className: 'text-lg font-semibold text-text-primary' },
                            'AI phase summary'
                        ),
                        createElement(
                            'p',
                            { className: 'mt-1 text-sm font-medium text-text-primary' },
                            phaseSummary.headline
                        ),
                        createElement(
                            'p',
                            { className: 'mt-2 text-sm text-text-secondary' },
                            phaseSummary.detail
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
                                    'Workflow automation'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Run the next best lifecycle task directly from the active phase surface.'
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
                                'AI'
                            ),
                            createElement(
                                'div',
                                null,
                                createElement(
                                    'h3',
                                    { className: 'text-lg font-semibold text-text-primary' },
                                    'AI recommendations'
                                ),
                                createElement(
                                    'p',
                                    { className: 'text-sm text-text-secondary' },
                                    'Next best actions for the active lifecycle phase.'
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
                                    'AI-generated defaults and progressive disclosure to reduce manual decision burden.'
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
                                    'Recent AI insights and audit evidence for this project.'
                                )
                            )
                        ),
                        evidenceContent
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
