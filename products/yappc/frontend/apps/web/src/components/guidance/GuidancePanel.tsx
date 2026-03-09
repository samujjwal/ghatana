/**
 * Guidance Panel Component
 * 
 * Collapsible panel showing phase-specific guidance, tips, and progress.
 * Integrates with WorkflowContext for context-aware guidance.
 * 
 * @doc.type component
 * @doc.purpose User guidance and progress tracking
 * @doc.layer product
 * @doc.pattern Panel Component
 */

import React, { useCallback, useMemo, useState } from 'react';
import { ChevronLeft as CollapseIcon, ChevronRight as ExpandIcon, CheckCircle as CompletedIcon, Circle as PendingIcon, Lightbulb as TipIcon, X as CloseIcon, HelpCircle as HelpIcon, GraduationCap as LearnIcon, PlayCircle as VideoIcon, Newspaper as DocIcon, Sparkles as AiIcon, RefreshCw as ResetIcon } from 'lucide-react';
import { Tooltip, Collapse, IconButton } from '@ghatana/ui';

import { useGuidanceContext, usePhaseContext } from '../../context/WorkflowContextProvider';
import { LifecyclePhase, PHASE_LABELS, PHASE_DESCRIPTIONS } from '../../types/lifecycle';

// ============================================================================
// Types
// ============================================================================

export interface GuidancePanelProps {
    /** Default collapsed state */
    defaultCollapsed?: boolean;
    /** Position in the layout */
    position?: 'left' | 'right';
    /** Additional class names */
    className?: string;
    /** Callback when panel is toggled */
    onToggle?: (collapsed: boolean) => void;
}

interface GuidanceStepItemProps {
    step: {
        id: string;
        title: string;
        description: string;
    };
    isCompleted: boolean;
    onComplete: () => void;
}

interface TipItemProps {
    tip: string;
    onDismiss: () => void;
}

interface ResourceLinkProps {
    type: 'doc' | 'video' | 'learn';
    title: string;
    href: string;
}

// ============================================================================
// Constants
// ============================================================================

/**
 * Resources per phase (documentation, videos, tutorials)
 */
const PHASE_RESOURCES: Record<LifecyclePhase, { type: 'doc' | 'video' | 'learn'; title: string; href: string }[]> = {
    [LifecyclePhase.INTENT]: [
        { type: 'doc', title: 'Writing Effective App Descriptions', href: '/docs/intent-guide' },
        { type: 'video', title: 'Intent Phase Tutorial', href: '/learn/intent-video' },
        { type: 'learn', title: 'AI Prompt Engineering Basics', href: '/learn/prompt-basics' },
    ],
    [LifecyclePhase.SHAPE]: [
        { type: 'doc', title: 'Canvas Layout Guide', href: '/docs/canvas-guide' },
        { type: 'video', title: 'Building Your First Screen', href: '/learn/canvas-video' },
        { type: 'learn', title: 'Component Library Overview', href: '/learn/components' },
    ],
    [LifecyclePhase.VALIDATE]: [
        { type: 'doc', title: 'Validation Best Practices', href: '/docs/validation-guide' },
        { type: 'video', title: 'Testing Your Design', href: '/learn/validation-video' },
    ],
    [LifecyclePhase.GENERATE]: [
        { type: 'doc', title: 'Code Generation Options', href: '/docs/generate-guide' },
        { type: 'video', title: 'Understanding Generated Code', href: '/learn/generate-video' },
    ],
    [LifecyclePhase.RUN]: [
        { type: 'doc', title: 'Preview & Deployment Guide', href: '/docs/run-guide' },
        { type: 'video', title: 'Running Your App', href: '/learn/run-video' },
    ],
    [LifecyclePhase.OBSERVE]: [
        { type: 'doc', title: 'Analytics Dashboard Guide', href: '/docs/observe-guide' },
        { type: 'video', title: 'Understanding Metrics', href: '/learn/observe-video' },
    ],
    [LifecyclePhase.IMPROVE]: [
        { type: 'doc', title: 'Iteration Strategies', href: '/docs/improve-guide' },
        { type: 'video', title: 'Continuous Improvement', href: '/learn/improve-video' },
    ],
};

// ============================================================================
// Sub-components
// ============================================================================

/**
 * Individual guidance step
 */
const GuidanceStepItem: React.FC<GuidanceStepItemProps> = ({
    step,
    isCompleted,
    onComplete,
}) => {
    return (
        <div
            className={`
                flex items-start gap-3 p-3 rounded-lg transition-all duration-200
                ${isCompleted
                    ? 'bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800'
                    : 'bg-white dark:bg-grey-800 border border-grey-200 dark:border-grey-700 hover:border-blue-300 dark:hover:border-blue-700'}
            `}
        >
            {/* Completion Toggle */}
            <button
                type="button"
                onClick={onComplete}
                className={`
                    flex-shrink-0 w-5 h-5 mt-0.5 rounded-full transition-colors
                    ${isCompleted
                        ? 'text-green-500 dark:text-green-400'
                        : 'text-grey-400 dark:text-grey-500 hover:text-blue-500 dark:hover:text-blue-400'}
                `}
                aria-label={isCompleted ? 'Mark as incomplete' : 'Mark as complete'}
            >
                {isCompleted ? <CompletedIcon /> : <PendingIcon />}
            </button>

            {/* Content */}
            <div className="flex-1 min-w-0">
                <h4 className={`
                    text-sm font-medium
                    ${isCompleted ? 'text-grey-500 dark:text-grey-400 line-through' : 'text-grey-900 dark:text-grey-100'}
                `}>
                    {step.title}
                </h4>
                <p className="text-xs text-grey-500 dark:text-grey-400 mt-0.5">
                    {step.description}
                </p>
            </div>
        </div>
    );
};

/**
 * Tip item with dismiss
 */
const TipItem: React.FC<TipItemProps> = ({ tip, onDismiss }) => {
    return (
        <div className="relative flex items-start gap-2 p-3 bg-amber-50 dark:bg-amber-900/20 rounded-lg border border-amber-200 dark:border-amber-800">
            <TipIcon className="flex-shrink-0 w-4 h-4 text-amber-500 mt-0.5" />
            <p className="flex-1 text-xs text-amber-800 dark:text-amber-200 pr-6">
                {tip}
            </p>
            <button
                type="button"
                onClick={onDismiss}
                className="absolute top-2 right-2 p-0.5 text-amber-500 hover:text-amber-700 dark:hover:text-amber-300 rounded"
                aria-label="Dismiss tip"
            >
                <CloseIcon className="w-3.5 h-3.5" />
            </button>
        </div>
    );
};

/**
 * Resource link
 */
const ResourceLink: React.FC<ResourceLinkProps> = ({ type, title, href }) => {
    const icons = {
        doc: DocIcon,
        video: VideoIcon,
        learn: LearnIcon,
    };
    const Icon = icons[type];

    return (
        <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            className="flex items-center gap-2 p-2 text-sm text-grey-700 dark:text-grey-300 hover:bg-grey-100 dark:hover:bg-grey-700 rounded-lg transition-colors"
        >
            <Icon className="w-4 h-4 text-grey-500" />
            <span className="truncate">{title}</span>
        </a>
    );
};

/**
 * Progress bar
 */
const ProgressBar: React.FC<{ completed: number; total: number }> = ({ completed, total }) => {
    const percentage = total > 0 ? Math.round((completed / total) * 100) : 0;

    return (
        <div className="space-y-1">
            <div className="flex justify-between text-xs">
                <span className="text-grey-600 dark:text-grey-400">Progress</span>
                <span className="font-medium text-grey-900 dark:text-grey-100">{percentage}%</span>
            </div>
            <div className="h-2 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                <div
                    className="h-full bg-gradient-to-r from-blue-500 to-blue-600 rounded-full transition-all duration-500"
                    style={{ width: `${percentage}%` }}
                />
            </div>
            <p className="text-xs text-grey-500 dark:text-grey-400">
                {completed} of {total} steps completed
            </p>
        </div>
    );
};

// ============================================================================
// Main Component
// ============================================================================

/**
 * Guidance Panel
 * 
 * A context-aware guidance panel that shows phase-specific steps, tips, and resources.
 * 
 * @example
 * ```tsx
 * <GuidancePanel 
 *   position="left"
 *   defaultCollapsed={false}
 *   onToggle={(collapsed) => console.log('Panel:', collapsed ? 'collapsed' : 'expanded')}
 * />
 * ```
 */
export const GuidancePanel: React.FC<GuidancePanelProps> = ({
    defaultCollapsed = false,
    position = 'left',
    className = '',
    onToggle,
}) => {
    const [isCollapsed, setIsCollapsed] = useState(defaultCollapsed);
    const [dismissedTipIds, setDismissedTipIds] = useState<string[]>([]);
    const { currentPhase } = usePhaseContext();
    const {
        currentPhaseSteps,
        tips,
        completedSteps,
        completeStep,
        dismissTip,
        resetGuidance,
    } = useGuidanceContext();

    // Calculate progress
    const totalSteps = currentPhaseSteps.length;
    const completedCount = currentPhaseSteps.filter((s: { id: string }) => completedSteps.includes(s.id)).length;

    // Filter tips (exclude dismissed)
    const visibleTips = useMemo(() => {
        return tips.filter((tip: string) => !dismissedTipIds.includes(`${currentPhase}-${tip}`));
    }, [tips, dismissedTipIds, currentPhase]);

    // Handle dismiss tip
    const handleDismissTip = useCallback((tipId: string) => {
        setDismissedTipIds(prev => [...prev, tipId]);
        dismissTip(tipId);
    }, [dismissTip]);

    // Handle reset
    const handleReset = useCallback(() => {
        setDismissedTipIds([]);
        resetGuidance();
    }, [resetGuidance]);

    // Get resources for current phase
    const resources = currentPhase ? PHASE_RESOURCES[currentPhase as LifecyclePhase] || [] : [];

    // Toggle handler
    const handleToggle = useCallback(() => {
        const newState = !isCollapsed;
        setIsCollapsed(newState);
        onToggle?.(newState);
    }, [isCollapsed, onToggle]);

    // Collapsed view (just a toggle button)
    if (isCollapsed) {
        return (
            <div className={`flex flex-col items-center py-4 ${className}`}>
                <Tooltip title="Show guidance" placement={position === 'left' ? 'right' : 'left'}>
                    <IconButton
                        onClick={handleToggle}
                        className="bg-white dark:bg-grey-800 shadow-md"
                        size="sm"
                    >
                        {position === 'left' ? <ExpandIcon /> : <CollapseIcon />}
                    </IconButton>
                </Tooltip>

                {/* Mini progress indicator */}
                <div className="mt-4 flex flex-col items-center gap-1">
                    <div className="w-2 h-16 bg-grey-200 dark:bg-grey-700 rounded-full overflow-hidden">
                        <div
                            className="w-full bg-blue-500 rounded-full transition-all duration-500"
                            style={{ height: `${(completedCount / totalSteps) * 100}%` }}
                        />
                    </div>
                    <span className="text-xs text-grey-500">{completedCount}/{totalSteps}</span>
                </div>

                {/* AI Assistant Quick Access */}
                <Tooltip title="AI Assistant" placement={position === 'left' ? 'right' : 'left'}>
                    <IconButton className="mt-4" size="sm">
                        <AiIcon className="text-blue-500" />
                    </IconButton>
                </Tooltip>
            </div>
        );
    }

    // Expanded view
    return (
        <aside
            className={`
                flex flex-col w-72 h-full bg-grey-50 dark:bg-grey-900 
                border-grey-200 dark:border-grey-800
                ${position === 'left' ? 'border-r' : 'border-l'}
                ${className}
            `}
            aria-label="Guidance panel"
        >
            {/* Header */}
            <header className="flex items-center justify-between p-4 border-b border-grey-200 dark:border-grey-800">
                <div className="flex items-center gap-2">
                    <HelpIcon className="w-5 h-5 text-blue-500" />
                    <h2 className="text-sm font-semibold text-grey-900 dark:text-grey-100">
                        Guidance
                    </h2>
                </div>
                <div className="flex items-center gap-1">
                    <Tooltip title="Reset progress">
                        <IconButton onClick={handleReset} size="sm">
                            <ResetIcon className="w-4 h-4" />
                        </IconButton>
                    </Tooltip>
                    <Tooltip title="Collapse panel">
                        <IconButton onClick={handleToggle} size="sm">
                            {position === 'left' ? <CollapseIcon /> : <ExpandIcon />}
                        </IconButton>
                    </Tooltip>
                </div>
            </header>

            {/* Current Phase Info */}
            {currentPhase && (
                <div className="p-4 border-b border-grey-200 dark:border-grey-800">
                    <h3 className="text-xs font-semibold text-grey-500 dark:text-grey-400 uppercase tracking-wider mb-1">
                        Current Phase
                    </h3>
                    <p className="text-base font-medium text-grey-900 dark:text-grey-100">
                        {PHASE_LABELS[currentPhase as LifecyclePhase]}
                    </p>
                    <p className="text-xs text-grey-500 dark:text-grey-400 mt-1">
                        {PHASE_DESCRIPTIONS[currentPhase as LifecyclePhase]}
                    </p>
                </div>
            )}

            {/* Scrollable Content */}
            <div className="flex-1 overflow-y-auto">
                {/* Progress */}
                <div className="p-4 border-b border-grey-200 dark:border-grey-800">
                    <ProgressBar completed={completedCount} total={totalSteps} />
                </div>

                {/* Tips */}
                <Collapse in={visibleTips.length > 0}>
                    <div className="p-4 border-b border-grey-200 dark:border-grey-800 space-y-2">
                        <h3 className="text-xs font-semibold text-grey-500 dark:text-grey-400 uppercase tracking-wider mb-2">
                            Tips
                        </h3>
                        {visibleTips.map((tip: string, index: number) => (
                            <TipItem
                                key={`${currentPhase}-tip-${index}`}
                                tip={tip}
                                onDismiss={() => handleDismissTip(`${currentPhase}-${tip}`)}
                            />
                        ))}
                    </div>
                </Collapse>

                {/* Steps */}
                <div className="p-4 border-b border-grey-200 dark:border-grey-800 space-y-2">
                    <h3 className="text-xs font-semibold text-grey-500 dark:text-grey-400 uppercase tracking-wider mb-2">
                        Steps
                    </h3>
                    {currentPhaseSteps.map((step: { id: string; title: string; description: string }) => (
                        <GuidanceStepItem
                            key={step.id}
                            step={step}
                            isCompleted={completedSteps.includes(step.id)}
                            onComplete={() => completeStep(step.id)}
                        />
                    ))}
                </div>

                {/* Resources */}
                {resources.length > 0 && (
                    <div className="p-4 space-y-1">
                        <h3 className="text-xs font-semibold text-grey-500 dark:text-grey-400 uppercase tracking-wider mb-2">
                            Learn More
                        </h3>
                        {resources.map((resource: { type: 'doc' | 'video' | 'learn'; title: string; href: string }) => (
                            <ResourceLink
                                key={resource.href}
                                type={resource.type}
                                title={resource.title}
                                href={resource.href}
                            />
                        ))}
                    </div>
                )}
            </div>

            {/* Footer - AI Assistant Quick Access */}
            <footer className="p-4 border-t border-grey-200 dark:border-grey-800">
                <button
                    type="button"
                    className="w-full flex items-center justify-center gap-2 px-4 py-2 bg-gradient-to-r from-blue-500 to-blue-600 hover:from-blue-600 hover:to-blue-700 text-white rounded-lg transition-all shadow-sm hover:shadow"
                >
                    <AiIcon className="w-4 h-4" />
                    <span className="text-sm font-medium">Ask AI Assistant</span>
                </button>
            </footer>
        </aside>
    );
};

export default GuidancePanel;
