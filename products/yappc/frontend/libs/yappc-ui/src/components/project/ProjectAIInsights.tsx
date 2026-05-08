/**
 * ProjectAIInsights
 *
 * Displays AI-generated insights for a project with dismiss/action capabilities.
 *
 * @doc.type component
 * @doc.purpose Display AI insights panel for a project
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import {
  BrainCircuit as BrainIcon,
  Lightbulb as LightbulbIcon,
  X as CloseIcon,
} from 'lucide-react';
import React from 'react';

import type { AIInsight } from 'yappc-state/aiAtoms';

type SeverityTone = 'critical' | 'warning' | 'info' | 'success';

const SEVERITY_TONE_BY_VALUE: Record<string, SeverityTone> = {
  critical: 'critical',
  high: 'critical',
  error: 'critical',
  warning: 'warning',
  medium: 'warning',
  low: 'info',
  info: 'info',
  success: 'success',
};

const SEVERITY_CLASSES: Record<SeverityTone, string> = {
  critical: 'border-red-200 bg-red-50 text-red-900',
  warning: 'border-amber-200 bg-amber-50 text-amber-900',
  info: 'border-blue-200 bg-blue-50 text-blue-900',
  success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
};

export interface ProjectAIInsightsProps {
  insights: AIInsight[];
  isLoading?: boolean;
  onDismiss?: (insightId: string) => void;
  onRefresh?: () => void;
  className?: string;
}

function getSeverityTone(severity: string): SeverityTone {
  return SEVERITY_TONE_BY_VALUE[severity.toLowerCase()] ?? 'info';
}

/**
 * Panel that shows AI-generated project insights.
 */
export const ProjectAIInsights: React.FC<ProjectAIInsightsProps> = ({
  insights,
  isLoading = false,
  onDismiss,
  onRefresh,
  className,
}) => {
  if (isLoading) {
    return (
      <div className="flex items-center justify-center gap-2 py-4 text-sm text-slate-600">
        <span
          className="h-5 w-5 animate-spin rounded-full border-2 border-slate-200 border-t-blue-600"
          aria-hidden="true"
        />
        <span>Analyzing project...</span>
      </div>
    );
  }

  if (insights.length === 0) {
    return (
      <div
        className={`flex flex-col items-center gap-2 py-6 text-center text-sm text-slate-500 ${className ?? ''}`}
      >
        <BrainIcon size={32} opacity={0.45} aria-hidden="true" />
        <p>No insights yet.</p>
        {onRefresh && (
          <button
            type="button"
            className="rounded-md px-2 py-1 text-sm font-medium text-blue-700 hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={onRefresh}
          >
            Analyze now
          </button>
        )}
      </div>
    );
  }

  return (
    <section className={`flex flex-col gap-3 ${className ?? ''}`}>
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <LightbulbIcon size={16} aria-hidden="true" />
          <h2 className="text-sm font-semibold text-slate-900">
            AI Insights ({insights.length})
          </h2>
        </div>
        {onRefresh && (
          <button
            type="button"
            className="rounded-md px-2 py-1 text-sm font-medium text-blue-700 hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-blue-500"
            onClick={onRefresh}
          >
            Refresh
          </button>
        )}
      </div>

      <div className="border-t border-slate-200" />

      {insights.map((insight) => {
        const tone = getSeverityTone(insight.severity);
        return (
          <article
            key={insight.id}
            className={`relative rounded-lg border p-3 pr-10 ${SEVERITY_CLASSES[tone]}`}
          >
            {onDismiss && (
              <button
                type="button"
                aria-label={`Dismiss insight ${insight.title}`}
                title="Dismiss"
                className="absolute right-2 top-2 inline-flex h-7 w-7 items-center justify-center rounded-full hover:bg-white/70 focus:outline-none focus:ring-2 focus:ring-blue-500"
                onClick={() => onDismiss(insight.id)}
              >
                <CloseIcon size={14} aria-hidden="true" />
              </button>
            )}

            <div className="flex flex-wrap items-center gap-2">
              <h3 className="text-sm font-semibold">{insight.title}</h3>
              <span className="rounded-full bg-white/70 px-2 py-0.5 text-[0.68rem] font-medium uppercase tracking-wide">
                {insight.category}
              </span>
              <span className="rounded-full border border-current/20 px-2 py-0.5 text-[0.68rem] font-medium">
                {Math.round(insight.confidence * 100)}%
              </span>
            </div>

            <p className="mt-2 text-sm leading-5">{insight.description}</p>

            {insight.actionItems.length > 0 && (
              <div className="mt-2">
                <p className="text-xs font-semibold">Action items:</p>
                <ul className="mt-1 list-disc space-y-0.5 pl-5 text-xs">
                  {insight.actionItems.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              </div>
            )}
          </article>
        );
      })}
    </section>
  );
};
