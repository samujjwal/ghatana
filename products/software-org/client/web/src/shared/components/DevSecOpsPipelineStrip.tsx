import type { DevSecOpsPhaseId } from '@/config/devsecopsEngineerFlow';
import { DEVSECOPS_PHASE_LABELS } from '@/config/devsecopsEngineerFlow';

interface DevSecOpsPipelineStripProps {
    /** Ordered list of DevSecOps phases to render. */
    phases: DevSecOpsPhaseId[];
    /** Currently active phase for the item/story. */
    currentPhaseId?: DevSecOpsPhaseId;
    className?: string;
}

export function DevSecOpsPipelineStrip({ phases, currentPhaseId, className, onPhaseClick }: DevSecOpsPipelineStripProps & { onPhaseClick?: (phaseId: DevSecOpsPhaseId) => void }) {
    const currentIndex = currentPhaseId ? phases.indexOf(currentPhaseId) : -1;
    const containerClassName = `flex items-center gap-2 text-xs ${className ?? ''}`.trim();

    return (
        <div className={containerClassName}>
            {phases.map((phaseId, index) => {
                const isCompleted = currentIndex !== -1 && index < currentIndex;
                const isCurrent = currentIndex !== -1 && index === currentIndex;
                const isClickable = typeof onPhaseClick === 'function';

                let pillClasses = 'inline-flex items-center gap-2 rounded-full px-3 py-1 whitespace-nowrap transition-colors';
                let dotClasses = 'inline-flex h-2 w-2 rounded-full';

                if (isClickable) {
                    pillClasses += ' cursor-pointer hover:shadow-sm hover:bg-slate-100 dark:hover:bg-slate-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-1 focus-visible:ring-indigo-500';
                }

                if (isCurrent) {
                    pillClasses += ' bg-indigo-600 text-white';
                    dotClasses += ' bg-indigo-300';
                } else if (isCompleted) {
                    pillClasses += ' bg-emerald-100 text-emerald-800 dark:bg-emerald-900/30 dark:text-emerald-300';
                    dotClasses += ' bg-emerald-500';
                } else {
                    pillClasses += ' bg-slate-100 text-slate-600 dark:bg-neutral-800 dark:text-neutral-300';
                    dotClasses += ' bg-slate-400';
                }

                return (
                    <div key={phaseId} className="flex items-center gap-2 flex-1 min-w-0">
                        {isClickable ? (
                            <button
                                type="button"
                                onClick={() => onPhaseClick?.(phaseId)}
                                className={pillClasses}
                            >
                                <span className={dotClasses} />
                                <span>{DEVSECOPS_PHASE_LABELS[phaseId]}</span>
                            </button>
                        ) : (
                            <div className={pillClasses}>
                                <span className={dotClasses} />
                                <span>{DEVSECOPS_PHASE_LABELS[phaseId]}</span>
                            </div>
                        )}
                        {index < phases.length - 1 && (
                            <div className="flex-1 h-px bg-slate-200 dark:bg-neutral-700" />
                        )}
                    </div>
                );
            })}
        </div>
    );
}
