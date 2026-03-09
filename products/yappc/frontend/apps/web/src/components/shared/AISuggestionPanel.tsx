/**
 * AI Suggestion Panel
 * 
 * Reusable component for displaying AI-powered suggestions.
 * Can be embedded in any phase/artifact context.
 * 
 * @doc.type component
 * @doc.purpose AI suggestions display
 * @doc.layer product
 * @doc.pattern Generic Component
 */

import React, { useState, useEffect } from 'react';
import { Lightbulb, Plus as Add, X as Close, TrendingUp } from 'lucide-react';
import {
    generateArtifactSuggestions,
    getRecommendedArtifacts,
    type ArtifactSuggestion,
    type SuggestionContext,
} from '../../services/ai';

export interface AISuggestionPanelProps {
    context: SuggestionContext;
    onAccept?: (suggestion: ArtifactSuggestion) => void;
    onDismiss?: (suggestionId: string) => void;
    compact?: boolean;
}

export const AISuggestionPanel: React.FC<AISuggestionPanelProps> = ({
    context,
    onAccept,
    onDismiss,
    compact = false,
}) => {
    const [suggestions, setSuggestions] = useState<ArtifactSuggestion[]>([]);
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isExpanded, setIsExpanded] = useState(!compact);

    // Get recommended artifacts
    const existingKinds = context.existingArtifacts.map(a => a.kind);
    const recommendedKinds = getRecommendedArtifacts(context.currentPhase, existingKinds);

    useEffect(() => {
        if (recommendedKinds.length > 0 && isExpanded) {
            loadSuggestions();
        }
    }, [isExpanded, recommendedKinds.length]);

    const loadSuggestions = async () => {
        setLoading(true);
        setError(null);
        try {
            const results = await generateArtifactSuggestions(context, recommendedKinds);
            setSuggestions(results);
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Failed to load suggestions');
        } finally {
            setLoading(false);
        }
    };

    const getConfidenceColor = (confidence: number) => {
        if (confidence >= 80) return 'text-green-600 bg-green-50';
        if (confidence >= 60) return 'text-amber-600 bg-amber-50';
        return 'text-grey-600 bg-grey-50';
    };

    if (recommendedKinds.length === 0) {
        return null; // No suggestions needed
    }

    return (
        <div className="border border-divider rounded-lg bg-gradient-to-br from-purple-50 to-blue-50 dark:from-purple-900/20 dark:to-blue-900/20">
            {/* Header */}
            <button
                onClick={() => setIsExpanded(!isExpanded)}
                className="w-full flex items-center justify-between px-4 py-3 hover:bg-white/50 dark:hover:bg-black/20 transition-colors rounded-t-lg"
            >
                <div className="flex items-center gap-2">
                    <Lightbulb className="w-5 h-5 text-amber-600" />
                    <span className="text-sm font-medium text-text-primary">AI Suggestions</span>
                    {recommendedKinds.length > 0 && (
                        <span className="px-2 py-0.5 text-xs font-medium bg-amber-600 text-white rounded-full">
                            {recommendedKinds.length}
                        </span>
                    )}
                </div>
                <span className="text-xs text-text-secondary">
                    {isExpanded ? '▲' : '▼'}
                </span>
            </button>

            {/* Content */}
            {isExpanded && (
                <div className="px-4 pb-4 space-y-3">
                    {loading && (
                        <div className="text-center py-6 text-sm text-text-secondary">
                            <div className="animate-pulse">Generating suggestions...</div>
                        </div>
                    )}

                    {error && (
                        <div className="p-3 bg-red-50 border border-red-200 rounded text-sm text-red-700">
                            {error}
                            <button
                                onClick={loadSuggestions}
                                className="ml-2 text-xs underline hover:no-underline"
                            >
                                Retry
                            </button>
                        </div>
                    )}

                    {!loading && !error && suggestions.length === 0 && (
                        <div className="text-center py-6">
                            <div className="text-sm text-text-secondary mb-2">
                                No suggestions available yet
                            </div>
                            <button
                                onClick={loadSuggestions}
                                className="text-sm text-primary-600 hover:underline"
                            >
                                Generate suggestions
                            </button>
                        </div>
                    )}

                    {suggestions.map((suggestion) => (
                        <div
                            key={suggestion.id}
                            className="p-3 bg-white dark:bg-grey-800 border border-divider rounded-lg space-y-2"
                        >
                            <div className="flex items-start justify-between gap-2">
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2 mb-1">
                                        <h4 className="text-sm font-semibold text-text-primary truncate">
                                            {suggestion.title}
                                        </h4>
                                        <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${getConfidenceColor(suggestion.confidence)}`}>
                                            {suggestion.confidence}%
                                        </span>
                                    </div>
                                    <p className="text-xs text-text-secondary line-clamp-2">
                                        {suggestion.summary}
                                    </p>
                                </div>
                                {onDismiss && (
                                    <button
                                        onClick={() => onDismiss(suggestion.id)}
                                        className="p-1 hover:bg-grey-100 rounded transition-colors flex-shrink-0"
                                    >
                                        <Close className="w-4 h-4 text-text-secondary" />
                                    </button>
                                )}
                            </div>

                            {!compact && (
                                <div className="pl-3 border-l-2 border-purple-200 text-xs text-text-secondary">
                                    <div className="flex items-start gap-1">
                                        <TrendingUp className="w-3 h-3 mt-0.5 flex-shrink-0" />
                                        <span>{suggestion.reasoning}</span>
                                    </div>
                                </div>
                            )}

                            {onAccept && (
                                <button
                                    onClick={() => onAccept(suggestion)}
                                    className="w-full flex items-center justify-center gap-1 px-3 py-1.5 text-sm bg-primary-600 text-white rounded hover:bg-primary-700 transition-colors"
                                >
                                    <Add className="w-4 h-4" />
                                    Create from suggestion
                                </button>
                            )}
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
};
