import { memo, useState } from 'react';
import { useNLQuery } from '../hooks/useNLQuery';

/**
 * Natural language query interface for dashboard analytics.
 *
 * <p><b>Purpose</b><br>
 * Provides conversational interface for exploring dashboard metrics.
 * Users ask questions in plain English and receive AI-powered analysis.
 *
 * <p><b>Features</b><br>
 * - Natural language input field
 * - Query suggestions (recent & popular)
 * - Analysis results with confidence score
 * - Related queries for exploration
 * - Keyboard shortcut (⌘+?) to focus
 *
 * <p><b>Interactions</b><br>
 * - Type question or select from suggestions
 * - View AI analysis and recommended metrics
 * - Click related queries to explore
 * - Save frequently asked questions
 *
 * @doc.type component
 * @doc.purpose Natural language analytics interface
 * @doc.layer product
 * @doc.pattern Molecule
 */
interface NaturalLanguageQueryProps {
    onQuerySelect?: (query: string) => void;
    onMetricsSelect?: (metrics: string[]) => void;
}

export const NaturalLanguageQuery = memo(function NaturalLanguageQuery({
    onQuerySelect,
    onMetricsSelect,
}: NaturalLanguageQueryProps) {
    const [inputValue, setInputValue] = useState('');
    const [submittedQuery, setSubmittedQuery] = useState<string | null>(null);
    const [savedQueries, setSavedQueries] = useState<string[]>([
        'Why is MTTR increasing?',
        'Show deployment trends',
        'What is the incident rate?',
    ]);

    const { data: result, isLoading, error } = useNLQuery(submittedQuery);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (inputValue.trim()) {
            setSubmittedQuery(inputValue.trim());
            onQuerySelect?.(inputValue.trim());
        }
    };

    const handleSuggestedQuery = (query: string) => {
        setInputValue(query);
        setSubmittedQuery(query);
        onQuerySelect?.(query);
    };

    const handleSaveQuery = () => {
        if (inputValue.trim() && !savedQueries.includes(inputValue.trim())) {
            setSavedQueries([...savedQueries, inputValue.trim()]);
        }
    };

    const handleSelectMetrics = (metrics: string[]) => {
        onMetricsSelect?.(metrics);
    };

    return (
        <div className="space-y-4 rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-slate-900">
            {/* Header */}
            <div className="flex items-center gap-2">
                <span className="text-lg">🤖</span>
                <h3 className="text-sm font-semibold text-slate-900 dark:text-neutral-100">
                    Ask AI Analyst
                </h3>
                <span className="ml-auto text-xs text-slate-500 dark:text-neutral-400">
                    ⌘+?
                </span>
            </div>

            {/* Input */}
            <form onSubmit={handleSubmit} className="space-y-2">
                <div className="flex gap-2">
                    <input
                        type="text"
                        value={inputValue}
                        onChange={(e) => setInputValue(e.target.value)}
                        placeholder="Ask about metrics, trends, or incidents..."
                        className="flex-1 rounded border border-slate-300 px-3 py-2 text-sm placeholder-slate-500 focus:border-blue-500 focus:outline-none dark:border-neutral-600 dark:bg-neutral-800 dark:text-neutral-100 dark:placeholder-slate-400"
                        autoComplete="off"
                    />
                    <button
                        type="submit"
                        disabled={isLoading || !inputValue.trim()}
                        className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white hover:bg-blue-700 disabled:opacity-50 dark:bg-blue-700 dark:hover:bg-blue-800"
                    >
                        {isLoading ? '⏳' : '→'}
                    </button>
                </div>

                {/* Suggestions or Saved Queries */}
                {!submittedQuery && (
                    <div className="flex flex-wrap gap-1">
                        {savedQueries.map((query) => (
                            <button
                                key={query}
                                type="button"
                                onClick={() => handleSuggestedQuery(query)}
                                className="rounded bg-slate-100 px-2 py-1 text-xs text-slate-700 hover:bg-slate-200 dark:bg-neutral-800 dark:text-neutral-300 dark:hover:bg-slate-700"
                            >
                                {query}
                            </button>
                        ))}
                    </div>
                )}
            </form>

            {/* Results */}
            {error && (
                <div className="rounded bg-red-50 p-3 text-sm text-red-700 dark:bg-rose-600/30 dark:text-rose-400">
                    Error: {error instanceof Error ? error.message : 'Failed to analyze query'}
                </div>
            )}

            {isLoading && (
                <div className="flex items-center gap-2 text-sm text-slate-600 dark:text-neutral-400">
                    <span className="inline-block animate-spin">⏳</span>
                    Analyzing your question...
                </div>
            )}

            {result && !isLoading && (
                <div className="space-y-3">
                    {/* Analysis */}
                    <div className="space-y-2">
                        <div className="flex items-center justify-between">
                            <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                Analysis
                            </span>
                            <div className="flex items-center gap-1">
                                <div className="h-2 w-12 rounded-full bg-slate-200 dark:bg-neutral-700">
                                    <div
                                        className="h-full rounded-full bg-green-500"
                                        style={{ width: `${result.confidence * 100}%` }}
                                    />
                                </div>
                                <span className="text-xs text-slate-500 dark:text-neutral-400">
                                    {Math.round(result.confidence * 100)}%
                                </span>
                            </div>
                        </div>
                        <p className="text-sm text-slate-700 dark:text-neutral-300">
                            {result.analysis}
                        </p>
                    </div>

                    {/* Suggested Metrics */}
                    {result.suggestedMetrics.length > 0 && (
                        <div className="space-y-2">
                            <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                Suggested Metrics
                            </span>
                            <button
                                onClick={() => handleSelectMetrics(result.suggestedMetrics)}
                                className="inline-flex flex-wrap gap-1"
                            >
                                {result.suggestedMetrics.map((metric) => (
                                    <span
                                        key={metric}
                                        className="rounded bg-blue-50 px-2 py-1 text-xs text-blue-700 hover:bg-blue-100 dark:bg-blue-900/30 dark:text-indigo-400 dark:hover:bg-blue-900/50"
                                    >
                                        {metric}
                                    </span>
                                ))}
                            </button>
                        </div>
                    )}

                    {/* Recommendations */}
                    {result.recommendations.length > 0 && (
                        <div className="space-y-2">
                            <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                Recommendations
                            </span>
                            <ul className="space-y-1">
                                {result.recommendations.map((rec, i) => (
                                    <li
                                        key={i}
                                        className="flex gap-2 text-xs text-slate-600 dark:text-neutral-400"
                                    >
                                        <span>•</span>
                                        <span>{rec}</span>
                                    </li>
                                ))}
                            </ul>
                        </div>
                    )}

                    {/* Related Queries */}
                    {result.relatedQueries.length > 0 && (
                        <div className="space-y-2">
                            <span className="text-xs font-medium text-slate-700 dark:text-neutral-300">
                                Related Queries
                            </span>
                            <div className="flex flex-wrap gap-1">
                                {result.relatedQueries.map((query) => (
                                    <button
                                        key={query}
                                        onClick={() => handleSuggestedQuery(query)}
                                        className="rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 dark:border-neutral-600 dark:text-neutral-400 dark:hover:bg-slate-800"
                                    >
                                        {query}
                                    </button>
                                ))}
                            </div>
                        </div>
                    )}

                    {/* Save Query */}
                    <button
                        onClick={handleSaveQuery}
                        className="w-full rounded border border-slate-300 px-2 py-1 text-xs text-slate-600 hover:bg-slate-50 dark:border-neutral-600 dark:text-neutral-400 dark:hover:bg-slate-800"
                    >
                        ⭐ Save this question
                    </button>
                </div>
            )}
        </div>
    );
});
