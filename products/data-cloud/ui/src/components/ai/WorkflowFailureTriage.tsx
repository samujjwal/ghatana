/**
 * Workflow Failure Triage Component
 *
 * Displays workflow failure analysis with probable cause and suggested fixes.
 * AI/ML #6 frontend scaffolding — integrates with failure analysis API.
 *
 * @doc.type component
 * @doc.purpose Workflow failure triage and next-best-action suggestions
 * @doc.layer frontend
 */

import React, { useState } from 'react';
import { AlertTriangle, Wrench, ChevronDown, ChevronRight } from 'lucide-react';
import { cn, cardStyles, textStyles } from '../../lib/theme';
import { AIConfidenceIndicator, type AIConfidence } from './AIConfidenceIndicator';

interface FailureAnalysis {
  id: string;
  probableCause: string;
  confidence: AIConfidence;
  suggestedFixes: { id: string; description: string; steps: string[] }[];
}

interface WorkflowFailureTriageProps {
  analysis?: FailureAnalysis;
  className?: string;
}

/**
 * Workflow Failure Triage Panel
 */
export function WorkflowFailureTriage({ analysis, className }: WorkflowFailureTriageProps): React.ReactElement {
  const [expandedFix, setExpandedFix] = useState<string | null>(null);

  if (!analysis) {
    return (
      <div className={cn(cardStyles.base, cardStyles.padded, className)} data-testid="failure-triage-empty">
        <div className="flex items-center gap-2 text-gray-500">
          <AlertTriangle className="h-4 w-4" />
          <p className="text-sm">No failure analysis available for this workflow.</p>
        </div>
      </div>
    );
  }

  return (
    <div className={cn(cardStyles.base, className)} data-testid="failure-triage-panel">
      <div className={cn(cardStyles.header, 'flex items-center gap-3')}>        <AlertTriangle className="h-5 w-5 text-red-500" />
        <div className="flex-1">
          <h3 className={textStyles.h4}>Failure Analysis</h3>
          <p className={textStyles.xs}>Probable cause and recommended fixes</p>
        </div>
        <AIConfidenceIndicator confidence={analysis.confidence} showLabel />
      </div>

      <div className="p-4 space-y-4">
        <div className="rounded-lg bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 p-3">
          <p className="text-sm font-medium text-red-800 dark:text-red-300 mb-1">Probable Cause</p>
          <p className="text-sm text-red-700 dark:text-red-400">{analysis.probableCause}</p>
        </div>

        <div>
          <p className="text-sm font-medium text-gray-900 dark:text-gray-100 mb-2">Suggested Fixes</p>
          <div className="space-y-2">
            {analysis.suggestedFixes.map((fix) => (
              <div
                key={fix.id}
                className="rounded-lg border border-gray-200 dark:border-gray-700 overflow-hidden"
              >
                <button
                  onClick={() => setExpandedFix(expandedFix === fix.id ? null : fix.id)}
                  className="w-full flex items-center justify-between p-3 text-left hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                >
                  <div className="flex items-center gap-2">
                    <Wrench className="h-4 w-4 text-blue-500" />
                    <span className="text-sm text-gray-900 dark:text-gray-100">{fix.description}</span>
                  </div>
                  {expandedFix === fix.id ? (
                    <ChevronDown className="h-4 w-4 text-gray-400" />
                  ) : (
                    <ChevronRight className="h-4 w-4 text-gray-400" />
                  )}
                </button>
                {expandedFix === fix.id && (
                  <div className="px-3 pb-3">
                    <ol className="ml-8 text-sm text-gray-600 dark:text-gray-400 space-y-1 list-decimal">
                      {fix.steps.map((step, i) => (
                        <li key={i}>{step}</li>
                      ))}
                    </ol>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default WorkflowFailureTriage;
