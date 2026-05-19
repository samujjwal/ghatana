/**
 * Canvas Status Bar
 * 
 * Bottom status bar showing phase progress and tech stack.
 * Provides unobtrusive phase navigation and technology context.
 * 
 * Layout Options:
 * - Enhanced: Full phase progress bar with labels and overall progress
 * - Compact: Phase dots with current phase label
 * 
 * @doc.type component
 * @doc.purpose Phase progress and tech stack display
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useMemo, useState } from 'react';
import { Tooltip, IconButton } from '@ghatana/design-system';
import { ChevronsUpDown as UnfoldMoreOutlined, ChevronsDownUp as UnfoldLessOutlined } from 'lucide-react';

import { LifecyclePhase } from '../../types/lifecycle';
import { STATUS_BAR, TRANSITIONS, RADIUS, Z_INDEX } from '../../styles/design-tokens';
import { Button } from '../ui/Button';
import { PhaseProgressBar } from './PhaseProgressBar';

// ============================================================================
// Types
// ============================================================================

export interface PhaseInfo {
    phase: LifecyclePhase;
    progress: number;
    status: 'pending' | 'in-progress' | 'completed';
}

export interface TechnologyInfo {
    id: string;
    name: string;
    icon?: React.ReactNode;
    color?: string;
}

export interface CanvasStatusBarProps {
    /** All phases with progress info */
    phases: PhaseInfo[];
    /** Current active phase */
    currentPhase: LifecyclePhase;
    /** Callback when a phase is clicked */
    onPhaseClick?: (phase: LifecyclePhase) => void;
    /** Technologies in use */
    technologies: TechnologyInfo[];
    /** Maximum technologies to show before "+N more" */
    maxVisibleTech?: number;
    /** Default view mode */
    defaultExpanded?: boolean;
    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// Constants
// ============================================================================

const PHASE_LABELS: Record<LifecyclePhase, string> = {
    [LifecyclePhase.INTENT]: 'Intent',
    [LifecyclePhase.SHAPE]: 'Shape',
    [LifecyclePhase.VALIDATE]: 'Validate',
    [LifecyclePhase.GENERATE]: 'Generate',
    [LifecyclePhase.RUN]: 'Run',
    [LifecyclePhase.OBSERVE]: 'Observe',
    [LifecyclePhase.IMPROVE]: 'Improve',
};

const PHASE_ORDER: LifecyclePhase[] = [
    LifecyclePhase.INTENT,
    LifecyclePhase.SHAPE,
    LifecyclePhase.VALIDATE,
    LifecyclePhase.GENERATE,
    LifecyclePhase.RUN,
    LifecyclePhase.OBSERVE,
    LifecyclePhase.IMPROVE,
];

// ============================================================================
// Sub-components
// ============================================================================

interface PhaseDotProps {
    phase: LifecyclePhase;
    status: 'pending' | 'in-progress' | 'completed';
    isCurrent: boolean;
    progress: number;
    onClick?: () => void;
}

function PhaseDot({ phase, status, isCurrent, progress, onClick }: PhaseDotProps) {
    const dotClass = useMemo(() => {
        const base = `${TRANSITIONS.fast} rounded-full cursor-pointer hover:scale-110`;

        if (isCurrent) {
            return `${base} w-3.5 h-3.5 bg-info-color ring-4 ring-info-border ring-offset-2 shadow-lg scale-125`;
        }

        if (status === 'completed') {
            return `${base} w-2 h-2 bg-success-bg opacity-60 scale-90`;
        }

        return `${base} w-2 h-2 bg-surface-muted dark:bg-surface-muted opacity-40 scale-85`;
    }, [isCurrent, status]);

    return (
        <Tooltip title={`${PHASE_LABELS[phase]}: ${progress}%`}>
            <Button
                variant="ghost"
                size="small"
                onClick={onClick}
                className={`${dotClass} min-h-0 p-0 text-transparent`}
                aria-label={`Go to ${PHASE_LABELS[phase]} phase`}
            >
                <span className="sr-only">{PHASE_LABELS[phase]}</span>
            </Button>
        </Tooltip>
    );
}

interface TechBadgeProps {
    tech: TechnologyInfo;
}

function TechBadge({ tech }: TechBadgeProps) {
    return (
        <span
            className={`
                inline-flex items-center gap-1 px-2 py-0.5
                text-xs font-medium
                bg-surface-muted dark:bg-surface-muted text-text-secondary
                ${RADIUS.button} ${TRANSITIONS.fast}
                hover:bg-surface-muted dark:hover:bg-surface-muted
            `}
            style={tech.color ? { backgroundColor: `${tech.color}20`, color: tech.color } : undefined}
        >
            {tech.icon && <span className="w-3 h-3">{tech.icon}</span>}
            {tech.name}
        </span>
    );
}

// ============================================================================
// Main Component
// ============================================================================
// Main Component
// ============================================================================

/**
 * Canvas Status Bar
 * 
 * Minimal bottom bar showing phase progress and tech stack.
 * Collapsed by default for maximum canvas space.
 */
export function CanvasStatusBar({
    phases,
    currentPhase,
    onPhaseClick,
    technologies,
    maxVisibleTech = 3,
    defaultExpanded = false,
    className = '',
}: CanvasStatusBarProps) {
    const [isExpanded, setIsExpanded] = useState(defaultExpanded);

    // Get phase info by phase
    const getPhaseInfo = (phase: LifecyclePhase): PhaseInfo => {
        return phases.find(p => p.phase === phase) || {
            phase,
            progress: 0,
            status: 'pending' as const,
        };
    };

    // Visible technologies
    const visibleTech = technologies.slice(0, maxVisibleTech);
    const hiddenTechCount = Math.max(0, technologies.length - maxVisibleTech);

    return (
        <div
            className={`
                px-3 py-1.5 flex items-center justify-between
                border-t border-divider bg-bg-paper/95 backdrop-blur-sm
                ${TRANSITIONS.default}
                ${className}
            `}
            style={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                zIndex: Z_INDEX.controls,
                height: isExpanded ? 'auto' : '32px',
                maxHeight: isExpanded ? '120px' : '32px',
            }}
            data-testid="canvas-status-bar"
        >
            {isExpanded ? (
                /* Expanded View: Enhanced Progress Bar */
                <div className="w-full">
                    <div className="flex items-start justify-between gap-4">
                        {/* Left: Enhanced Phase Progress */}
                        <div className="flex-1">
                            <PhaseProgressBar
                                phases={phases}
                                currentPhase={currentPhase}
                                onPhaseClick={onPhaseClick}
                                variant="full"
                            />
                        </div>

                        {/* Right: Controls */}
                        <div className="flex items-center gap-2">
                            {/* Tech Stack */}
                            <div className="flex items-center gap-1.5">
                                {visibleTech.map((tech) => (
                                    <TechBadge key={tech.id} tech={tech} />
                                ))}
                                {hiddenTechCount > 0 && (
                                    <Tooltip
                                        title={technologies.slice(maxVisibleTech).map(t => t.name).join(', ')}
                                    >
                                        <span className="text-xs text-text-secondary cursor-default">
                                            +{hiddenTechCount}
                                        </span>
                                    </Tooltip>
                                )}
                            </div>

                            {/* Collapse Button */}
                            <Tooltip title="Collapse progress bar">
                                <IconButton
                                    size="small"
                                    onClick={() => setIsExpanded(false)}
                                    aria-label="Collapse progress bar"
                                >
                                    <UnfoldLessOutlined size={16} />
                                </IconButton>
                            </Tooltip>
                        </div>
                    </div>
                </div>
            ) : (
                /* Compact View: Phase Dots */
                <div className="flex items-center justify-between w-full">
                    {/* Left: Phase Progress */}
                    <div className="flex items-center gap-1.5">
                        {/* Phase Dots */}
                        <div className="flex items-center gap-1">
                            {PHASE_ORDER.map((phase) => {
                                const info = getPhaseInfo(phase);
                                return (
                                    <PhaseDot
                                        key={phase}
                                        phase={phase}
                                        status={info.status}
                                        isCurrent={phase === currentPhase}
                                        progress={info.progress}
                                        onClick={() => onPhaseClick?.(phase)}
                                    />
                                );
                            })}
                        </div>

                        {/* Current Phase Label */}
                        <span className="ml-1.5 text-xs font-medium text-text-secondary uppercase tracking-wide">
                            {PHASE_LABELS[currentPhase]}
                        </span>

                        {/* Expand Button */}
                        <Tooltip title="Show details">
                            <IconButton
                                size="small"
                                onClick={() => setIsExpanded(true)}
                                aria-label="Show details"
                                className="ml-1 p-1"
                            >
                                <UnfoldMoreOutlined className="text-base" />
                            </IconButton>
                        </Tooltip>
                    </div>

                    {/* Right: Tech Stack (minimal) */}
                    <div className="flex items-center gap-1">
                        {visibleTech.map((tech) => (
                            <TechBadge key={tech.id} tech={tech} />
                        ))}
                        {hiddenTechCount > 0 && (
                            <Tooltip
                                title={technologies.slice(maxVisibleTech).map(t => t.name).join(', ')}
                            >
                                <span className="text-xs text-text-secondary cursor-default">
                                    +{hiddenTechCount}
                                </span>
                            </Tooltip>
                        )}
                    </div>
                </div>
            )}
        </div>
    );
}

// Export sub-components
export { PhaseDot, TechBadge };
