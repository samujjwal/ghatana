import React from 'react';

/**
 * AI Suggestion interface.
 */
export interface AISuggestion {
    id: string;
    title: string;
    description: string;
    eventData: Record<string, unknown>;
    confidence: number;
    category: string;
    explanation: string;
}

/**
 * AI Panel Props interface.
 */
export interface AIPanelProps {
    suggestions: AISuggestion[];
    onSuggestionSelect?: (suggestion: AISuggestion) => void;
    isLoading?: boolean;
    onGenerateSuggestions?: () => void;
    error?: string;
}

/**
 * AI Panel - Displays AI-generated event suggestions.
 *
 * <p><b>Purpose</b><br>
 * Shows intelligent suggestions for test events based on patterns and best practices.
 *
 * <p><b>Features</b><br>
 * - Confidence-scored suggestions
 * - Category grouping
 * - Detailed explanation of each suggestion
 * - Quick-apply buttons
 * - Generate new suggestions
 * - Loading states
 * - Error handling
 * - Dark mode support
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <AIPanel 
 *   suggestions={aiSuggestions}
 *   onSuggestionSelect={handleSelect}
 *   onGenerateSuggestions={handleGenerate}
 *   isLoading={false}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose AI-powered event suggestions
 * @doc.layer product
 * @doc.pattern Organism
 */
export const AIPanel = React.memo(
    ({
        suggestions,
        onSuggestionSelect,
        isLoading,
        onGenerateSuggestions,
        error,
    }: AIPanelProps) => {
        // Group suggestions by category
        const groupedSuggestions = React.useMemo(() => {
            const groups: Record<string, AISuggestion[]> = {};
            suggestions.forEach((suggestion) => {
                if (!groups[suggestion.category]) {
                    groups[suggestion.category] = [];
                }
                groups[suggestion.category].push(suggestion);
            });
            return groups;
        }, [suggestions]);

        const getConfidenceColor = (confidence: number) => {
            if (confidence >= 0.8) return 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-300';
            if (confidence >= 0.6) return 'bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-300';
            return 'bg-orange-100 text-orange-800 dark:bg-orange-900/30 dark:text-orange-300';
        };

        const getConfidenceLabel = (confidence: number) => {
            if (confidence >= 0.8) return 'High confidence';
            if (confidence >= 0.6) return 'Medium confidence';
            return 'Low confidence';
        };

        if (error) {
            return (
                <div className="rounded-lg border border-red-200 bg-red-50 p-4 dark:border-red-800 dark:bg-rose-600/30">
                    <p className="text-sm font-medium text-red-800 dark:text-red-300">
                        ✕ Error loading suggestions
                    </p>
                    <p className="text-xs text-red-700 dark:text-rose-400 mt-1">{error}</p>
                    {onGenerateSuggestions && (
                        <button
                            onClick={onGenerateSuggestions}
                            className="mt-3 px-3 py-1.5 rounded-md bg-red-600 text-white text-sm font-medium hover:bg-red-700 transition-colors"
                        >
                            Retry
                        </button>
                    )}
                </div>
            );
        }

        return (
            <div className="rounded-lg border border-slate-200 bg-white p-6 dark:border-neutral-600 dark:bg-slate-900">
                {/* Header */}
                <div className="flex items-center justify-between mb-6">
                    <h3 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 flex items-center gap-2">
                        <span>✨</span>
                        <span>AI Suggestions</span>
                    </h3>
                    {onGenerateSuggestions && (
                        <button
                            onClick={onGenerateSuggestions}
                            disabled={isLoading}
                            className="px-3 py-1.5 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed dark:hover:bg-blue-500 transition-colors"
                            aria-busy={isLoading}
                            aria-label="Generate AI suggestions"
                        >
                            {isLoading ? 'Generating...' : 'Generate'}
                        </button>
                    )}
                </div>

                {/* Loading state */}
                {isLoading && (
                    <div className="space-y-4">
                        {Array.from({ length: 3 }).map((_, i) => (
                            <div key={i} className="h-24 bg-slate-200 dark:bg-neutral-700 rounded animate-pulse" />
                        ))}
                    </div>
                )}

                {/* Empty state */}
                {!isLoading && suggestions.length === 0 && (
                    <div className="py-8 text-center">
                        <p className="text-slate-600 dark:text-neutral-400 mb-4">
                            No suggestions yet. Generate some AI-powered event suggestions to get started.
                        </p>
                        {onGenerateSuggestions && (
                            <button
                                onClick={onGenerateSuggestions}
                                className="px-4 py-2 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 dark:hover:bg-blue-500 transition-colors"
                            >
                                Generate Suggestions
                            </button>
                        )}
                    </div>
                )}

                {/* Suggestions grouped by category */}
                {!isLoading &&
                    suggestions.length > 0 &&
                    Object.entries(groupedSuggestions).map(([category, categorySuggestions]) => (
                        <div key={category} className="mb-6">
                            <h4 className="text-sm font-semibold text-slate-700 dark:text-neutral-300 mb-3 uppercase tracking-wide">
                                {category}
                            </h4>
                            <div className="space-y-3">
                                {categorySuggestions.map((suggestion) => (
                                    <div
                                        key={suggestion.id}
                                        className="rounded-lg border border-slate-200 bg-slate-50 p-4 dark:border-neutral-600 dark:bg-neutral-800 hover:shadow-md transition-shadow"
                                    >
                                        <div className="flex items-start justify-between mb-2">
                                            <div className="flex-1">
                                                <h5 className="font-semibold text-slate-900 dark:text-neutral-100">
                                                    {suggestion.title}
                                                </h5>
                                                <p className="text-sm text-slate-600 dark:text-neutral-400 mt-1">
                                                    {suggestion.description}
                                                </p>
                                            </div>
                                            <span
                                                className={`inline-flex items-center gap-1 px-2.5 py-1 rounded-full text-xs font-semibold whitespace-nowrap ml-2 ${getConfidenceColor(
                                                    suggestion.confidence
                                                )}`}
                                            >
                                                {(suggestion.confidence * 100).toFixed(0)}%
                                            </span>
                                        </div>

                                        {/* Explanation */}
                                        <div className="mb-3 p-2 rounded bg-blue-50 dark:bg-indigo-600/30 border border-blue-200 dark:border-blue-800">
                                            <p className="text-xs text-blue-900 dark:text-blue-100">
                                                <strong>Why:</strong> {suggestion.explanation}
                                            </p>
                                        </div>

                                        {/* Event data preview */}
                                        <div className="mb-3 p-2 rounded bg-slate-100 dark:bg-neutral-700 border border-slate-300 dark:border-neutral-600">
                                            <p className="text-xs font-mono text-slate-700 dark:text-neutral-300 mb-1">
                                                Event data:
                                            </p>
                                            <pre className="text-xs text-slate-600 dark:text-neutral-400 overflow-x-auto">
                                                {JSON.stringify(suggestion.eventData, null, 2).slice(0, 200)}...
                                            </pre>
                                        </div>

                                        {/* Action button */}
                                        {onSuggestionSelect && (
                                            <button
                                                onClick={() => onSuggestionSelect(suggestion)}
                                                className="w-full py-2 px-3 rounded-md text-sm font-medium bg-blue-600 text-white hover:bg-blue-700 dark:hover:bg-blue-500 transition-colors"
                                                aria-label={`Select suggestion: ${suggestion.title}`}
                                            >
                                                Use This Event
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        </div>
                    ))}

                {/* Confidence note */}
                {!isLoading && suggestions.length > 0 && (
                    <div className="pt-4 border-t border-slate-200 dark:border-neutral-600">
                        <p className="text-xs text-slate-600 dark:text-neutral-400">
                            💡 Confidence levels: <strong>80%+</strong> = High, <strong>60-79%</strong> = Medium, <strong>&lt;60%</strong> = Low
                        </p>
                    </div>
                )}
            </div>
        );
    }
);

AIPanel.displayName = 'AIPanel';

export default AIPanel;
