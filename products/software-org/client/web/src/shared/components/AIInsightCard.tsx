import React from 'react';

/**
 * AIInsightCard - AI copilot recommendations with confidence scoring.
 *
 * <p><b>Features</b><br>
 * - Insight text with reasoning
 * - Confidence score (0-100%)
 * - Approval buttons (Approve/Defer)
 * - Expandable reasoning drawer
 *
 * @doc.type component
 * @doc.purpose AI recommendation card
 * @doc.layer product
 * @doc.pattern Organism
 */
export const AIInsightCard = React.memo(() => {
    const [expanded, setExpanded] = React.useState(false);

    const insight = {
        text: 'QA pipeline shows 15% flakiness in auth tests',
        confidence: 92,
        status: 'pending',
        reasoning: 'Analyzed test execution logs from last 14 days. Found 23 auth test failures out of 152 runs (15.1%). Confidence: 92% based on consistent pattern across test environments.',
        recommendations: [
            { action: 'Quarantine', description: 'Mark auth tests as quarantined' },
            { action: 'Root Cause', description: 'Start root cause analysis' },
        ],
    };

    return (
        <div className="rounded-lg border border-slate-200 bg-white p-4 dark:border-neutral-600 dark:bg-neutral-800">
            <div className="mb-4 flex items-center gap-2">
                <span className="text-lg">🤖</span>
                <h3 className="font-semibold text-slate-900 dark:text-neutral-100">AI Copilot</h3>
            </div>

            <div className="mb-3 text-sm text-slate-700 dark:text-neutral-300">
                {insight.text}
            </div>

            <div className="mb-4 flex items-center gap-2">
                <div className="text-xs font-medium text-slate-600 dark:text-neutral-400">
                    Confidence: {insight.confidence}%
                </div>
                <div className="h-1.5 w-16 rounded-full bg-slate-200 dark:bg-neutral-700">
                    <div
                        className="h-full rounded-full bg-green-500"
                        style={{ width: `${insight.confidence}%` }}
                    />
                </div>
            </div>

            <div className="mb-4">
                <button
                    onClick={() => setExpanded(!expanded)}
                    className="text-sm text-blue-600 hover:underline dark:text-indigo-400"
                >
                    {expanded ? 'Hide' : 'Show'} Reasoning
                </button>
                {expanded && (
                    <div className="mt-2 rounded bg-slate-50 p-3 text-xs text-slate-700 dark:bg-slate-900 dark:text-neutral-300">
                        {insight.reasoning}
                    </div>
                )}
            </div>

            <div className="flex gap-2">
                <button className="flex-1 rounded bg-green-600 px-3 py-2 text-sm font-medium text-white hover:bg-green-700 dark:bg-green-700 dark:hover:bg-green-600">
                    Approve
                </button>
                <button className="flex-1 rounded border border-slate-300 px-3 py-2 text-sm font-medium text-slate-700 hover:bg-slate-50 dark:border-neutral-600 dark:text-neutral-300 dark:hover:bg-slate-700">
                    Defer
                </button>
            </div>
        </div>
    );
});

AIInsightCard.displayName = 'AIInsightCard';

export default AIInsightCard;
