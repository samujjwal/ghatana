/**
 * Phase Context Panel Component
 * 
 * Displays phase-specific guidance, quality checks, and suggested actions.
 * Provides contextual help to guide users through each lifecycle phase.
 * 
 * @doc.type component
 * @doc.purpose Phase-specific guidance display
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useEffect } from 'react';
import { Info, CheckCircle, AlertTriangle as Warning, Lightbulb, ChevronDown as ExpandMore, ChevronUp as ExpandLess } from 'lucide-react';
import type { LifecyclePhase } from '@/shared/types/lifecycle';
import type { LifecycleArtifactKind } from '@/shared/types/lifecycle-artifacts';
import {
    getPhasePrompt,
    getNextAction,
    isPhaseReady,
    generatePhaseAISuggestions,
    type PhaseAISuggestion,
} from '../../services/ai/PhaseAIPromptService';

export interface PhaseContextPanelProps {
    phase: LifecyclePhase;
    existingArtifacts: LifecycleArtifactKind[];
    onActionClick?: (action: string, artifactKind?: LifecycleArtifactKind) => void;
}

/**
 * Phase Context Panel Component
 * 
 * Shows phase-specific guidance and AI-powered suggestions.
 */
export function PhaseContextPanel({
    phase,
    existingArtifacts,
    onActionClick,
}: PhaseContextPanelProps) {
    const [expanded, setExpanded] = useState(true);
    const [suggestions, setSuggestions] = useState<PhaseAISuggestion[]>([]);
    const [loadingSuggestions, setLoadingSuggestions] = useState(false);

    const phasePrompt = getPhasePrompt(phase);
    const nextAction = getNextAction(phase, existingArtifacts);
    const readiness = isPhaseReady(phase, existingArtifacts);

    useEffect(() => {
        async function loadSuggestions() {
            setLoadingSuggestions(true);
            try {
                const result = await generatePhaseAISuggestions({
                    phase,
                    existingArtifacts,
                });
                setSuggestions(result);
            } catch (err) {
                console.error('Failed to load phase suggestions:', err);
            } finally {
                setLoadingSuggestions(false);
            }
        }
        loadSuggestions();
    }, [phase, existingArtifacts]);

    const progressPercentage = Math.round(
        (existingArtifacts.length / phasePrompt.artifactFocus.length) * 100
    );

    return (
        <div className="bg-bg-paper border border-divider rounded-lg overflow-hidden">
            {/* Header */}
            <button
                onClick={() => setExpanded(!expanded)}
                className="w-full flex items-center justify-between p-4 hover:bg-grey-50 dark:hover:bg-grey-800/50 transition-colors"
            >
                <div className="flex items-center gap-3">
                    <Info className="text-primary-500" />
                    <div className="text-left">
                        <h3 className="font-semibold text-text-primary">
                            {phasePrompt.title} Phase Guide
                        </h3>
                        <p className="text-sm text-text-secondary">
                            {phasePrompt.description}
                        </p>
                    </div>
                </div>
                {expanded ? <ExpandLess /> : <ExpandMore />}
            </button>

            {expanded && (
                <div className="p-4 pt-0 space-y-4">
                    {/* Progress */}
                    <div className="space-y-2">
                        <div className="flex items-center justify-between text-sm">
                            <span className="text-text-secondary">Phase Completion</span>
                            <span className="font-medium text-text-primary">{progressPercentage}%</span>
                        </div>
                        <div className="w-full bg-grey-200 dark:bg-grey-700 rounded-full h-2">
                            <div
                                className="bg-primary-500 h-2 rounded-full transition-all duration-300"
                                style={{ width: `${progressPercentage}%` }}
                            />
                        </div>
                        {readiness.ready ? (
                            <div className="flex items-center gap-2 text-sm text-success-600">
                                <CheckCircle className="w-4 h-4" />
                                <span>Ready for next phase</span>
                            </div>
                        ) : (
                            <div className="flex items-center gap-2 text-sm text-warning-600">
                                <Warning className="w-4 h-4" />
                                <span>{readiness.reason}</span>
                            </div>
                        )}
                    </div>

                    {/* Next Action */}
                    <div className="bg-primary-50 dark:bg-primary-900/20 border border-primary-200 dark:border-primary-800 rounded-lg p-3">
                        <div className="flex items-start gap-2">
                            <Lightbulb className="text-primary-600 w-5 h-5 mt-0.5" />
                            <div className="flex-1">
                                <div className="text-sm font-medium text-primary-900 dark:text-primary-100">
                                    Next Suggested Action
                                </div>
                                <div className="text-sm text-primary-700 dark:text-primary-300 mt-1">
                                    {nextAction}
                                </div>
                            </div>
                        </div>
                    </div>

                    {/* AI Suggestions */}
                    {loadingSuggestions ? (
                        <div className="text-center text-sm text-text-secondary py-4">
                            Loading AI suggestions...
                        </div>
                    ) : suggestions.length > 0 ? (
                        <div className="space-y-2">
                            <h4 className="text-sm font-semibold text-text-primary">
                                AI Recommendations
                            </h4>
                            {suggestions.slice(0, 3).map((suggestion, idx) => (
                                <button
                                    key={idx}
                                    onClick={() => onActionClick?.(suggestion.title, suggestion.artifactKind)}
                                    className="w-full text-left p-3 rounded-lg border border-divider bg-bg-default hover:bg-grey-50 dark:hover:bg-grey-800/50 transition-colors"
                                >
                                    <div className="flex items-start gap-3">
                                        <div className={`
                                            w-2 h-2 rounded-full mt-2
                                            ${suggestion.priority === 'high' ? 'bg-error-500' : ''}
                                            ${suggestion.priority === 'medium' ? 'bg-warning-500' : ''}
                                            ${suggestion.priority === 'low' ? 'bg-success-500' : ''}
                                        `} />
                                        <div className="flex-1 min-w-0">
                                            <div className="text-sm font-medium text-text-primary">
                                                {suggestion.title}
                                            </div>
                                            <div className="text-xs text-text-secondary mt-1">
                                                {suggestion.description}
                                            </div>
                                            {suggestion.estimatedEffort && (
                                                <div className="text-xs text-text-tertiary mt-1">
                                                    ⏱️ {suggestion.estimatedEffort}
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                </button>
                            ))}
                        </div>
                    ) : null}

                    {/* Quality Checks */}
                    <div className="space-y-2">
                        <h4 className="text-sm font-semibold text-text-primary">
                            Quality Checklist
                        </h4>
                        <div className="space-y-1">
                            {phasePrompt.qualityChecks.map((check, idx) => (
                                <div key={idx} className="flex items-start gap-2 text-sm">
                                    <CheckCircle className="w-4 h-4 text-text-tertiary mt-0.5" />
                                    <span className="text-text-secondary">{check}</span>
                                </div>
                            ))}
                        </div>
                    </div>

                    {/* Common Pitfalls */}
                    <div className="space-y-2">
                        <h4 className="text-sm font-semibold text-text-primary flex items-center gap-2">
                            <Warning className="w-4 h-4 text-warning-500" />
                            Common Pitfalls
                        </h4>
                        <div className="space-y-1">
                            {phasePrompt.commonPitfalls.map((pitfall, idx) => (
                                <div key={idx} className="flex items-start gap-2 text-sm">
                                    <span className="text-text-tertiary">•</span>
                                    <span className="text-text-secondary">{pitfall}</span>
                                </div>
                            ))}
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}
