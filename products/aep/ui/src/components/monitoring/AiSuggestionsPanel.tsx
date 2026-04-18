/**
 * AiSuggestionsPanel — AI-powered suggestions for pipeline operations.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/monitoring-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type component
 * @doc.purpose Show AI-suggested actions based on anomaly detection
 * @doc.layer frontend
 * @doc.pattern AI Component
 */

import React from 'react';
import { useQuery } from '@tanstack/react-query';
import { AlertTriangle, TrendingUp, Zap, X, CheckCircle } from 'lucide-react';
import { isFeatureEnabled } from '@/lib/feature-flags';
import { RBACGuard } from '../security/RBACGuard';

/**
 * AI suggestion type
 */
export interface AiSuggestion {
  id: string;
  type: 'anomaly' | 'optimization' | 'warning' | 'recommendation';
  severity: 'low' | 'medium' | 'high' | 'critical';
  message: string;
  action?: {
    label: string;
    handler: () => void;
  };
  resourceId?: string;
  resourceType?: 'pipeline' | 'run' | 'agent' | 'policy';
  confidence?: number;
}

/**
 * AiSuggestionsPanel component props
 */
interface AiSuggestionsPanelProps {
  tenantId: string;
  onSuggestionApply?: (suggestion: AiSuggestion) => void;
  onSuggestionDismiss?: (suggestionId: string) => void;
  /**
   * Optional: Maximum number of suggestions to show
   */
  maxSuggestions?: number;
  className?: string;
}

/**
 * AiSuggestionsPanel component
 *
 * Displays AI-powered suggestions for pipeline operations based on
 * anomaly detection and pattern analysis. Suggestions can include
 * optimization hints, anomaly alerts, and actionable recommendations.
 */
export const AiSuggestionsPanel: React.FC<AiSuggestionsPanelProps> = ({
  tenantId,
  onSuggestionApply,
  onSuggestionDismiss,
  maxSuggestions = 5,
  className = '',
}) => {
  const { data: suggestions, isLoading, error } = useQuery({
    queryKey: ['aep', 'ai-suggestions', tenantId],
    queryFn: async (): Promise<AiSuggestion[]> => {
      const response = await fetch(`/api/v1/ai/suggestions?tenantId=${encodeURIComponent(tenantId)}`);
      if (!response.ok) {
        throw new Error(`Failed to fetch AI suggestions: ${response.statusText}`);
      }
      const body: unknown = await response.json();
      if (body !== null && typeof body === 'object' && 'suggestions' in body && Array.isArray((body as Record<string, unknown>).suggestions)) {
        return (body as { suggestions: AiSuggestion[] }).suggestions;
      }
      return Array.isArray(body) ? (body as AiSuggestion[]) : [];
    },
    enabled: isFeatureEnabled('AI_SUGGESTIONS'),
    staleTime: 60_000,
    refetchInterval: 5 * 60_000,
  });

  if (!isFeatureEnabled('AI_SUGGESTIONS')) {
    return null;
  }

  if (isLoading) {
    return (
      <div className={`bg-indigo-50 dark:bg-indigo-950 border border-indigo-200 dark:border-indigo-800 rounded-lg p-4 ${className}`}>
        <div className="flex items-center gap-2 text-sm text-indigo-600 dark:text-indigo-400">
          <div className="animate-spin h-4 w-4 border-2 border-indigo-600 border-t-transparent rounded-full" />
          Loading AI suggestions...
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className={`bg-amber-50 dark:bg-amber-950 border border-amber-200 dark:border-amber-800 rounded-lg p-3 ${className}`}>
        <p className="text-xs text-amber-700 dark:text-amber-300 flex items-center gap-2">
          <AlertTriangle className="h-3.5 w-3.5 flex-shrink-0" />
          AI suggestions unavailable — {error instanceof Error ? error.message : 'unknown error'}
        </p>
      </div>
    );
  }

  if (!suggestions?.length) {
    return null;
  }

  const displaySuggestions = suggestions.slice(0, maxSuggestions);

  const getSeverityColor = (severity: AiSuggestion['severity']): string => {
    switch (severity) {
      case 'critical':
        return 'bg-red-100 text-red-700 border-red-200 dark:bg-red-900 dark:text-red-300 dark:border-red-800';
      case 'high':
        return 'bg-orange-100 text-orange-700 border-orange-200 dark:bg-orange-900 dark:text-orange-300 dark:border-orange-800';
      case 'medium':
        return 'bg-amber-100 text-amber-700 border-amber-200 dark:bg-amber-900 dark:text-amber-300 dark:border-amber-800';
      case 'low':
        return 'bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900 dark:text-blue-300 dark:border-blue-800';
    }
  };

  const getTypeIcon = (type: AiSuggestion['type']) => {
    switch (type) {
      case 'anomaly':
        return AlertTriangle;
      case 'optimization':
        return TrendingUp;
      case 'warning':
        return Zap;
      case 'recommendation':
        return CheckCircle;
    }
  };

  return (
    <div className={`bg-indigo-50 dark:bg-indigo-950 border border-indigo-200 dark:border-indigo-800 rounded-lg p-4 ${className}`}>
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-indigo-900 dark:text-indigo-100 flex items-center gap-2">
          <Zap className="h-4 w-4" />
          AI Suggestions
        </h3>
        {suggestions.length > maxSuggestions && (
          <span className="text-xs text-indigo-600 dark:text-indigo-400">
            +{suggestions.length - maxSuggestions} more
          </span>
        )}
      </div>
      <div className="space-y-2">
        {displaySuggestions.map((suggestion) => {
          const Icon = getTypeIcon(suggestion.type);
          return (
            <div
              key={suggestion.id}
              className={`flex items-start gap-3 p-3 rounded-md border ${getSeverityColor(suggestion.severity)}`}
            >
              <Icon className="h-4 w-4 mt-0.5 flex-shrink-0" />
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">{suggestion.message}</p>
                {suggestion.confidence && (
                  <p className="text-xs opacity-75 mt-1">
                    Confidence: {(suggestion.confidence * 100).toFixed(0)}%
                  </p>
                )}
                {suggestion.action && (
                  <RBACGuard permission="write:pipeline" resource={suggestion.resourceId} action="write">
                    <button
                      onClick={() => {
                        suggestion.action?.handler();
                        onSuggestionApply?.(suggestion);
                      }}
                      className="mt-2 text-xs font-medium underline hover:opacity-75"
                    >
                      {suggestion.action.label}
                    </button>
                  </RBACGuard>
                )}
              </div>
              {onSuggestionDismiss && (
                <button
                  onClick={() => onSuggestionDismiss(suggestion.id)}
                  className="flex-shrink-0 opacity-50 hover:opacity-100 transition-opacity"
                  aria-label="Dismiss suggestion"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

/**
 * Hook for using AI suggestions
 */
export function useAiSuggestions(tenantId: string) {
  const { data: suggestions, isLoading, error, refetch } = useQuery({
    queryKey: ['aep', 'ai-suggestions', tenantId],
    queryFn: async (): Promise<AiSuggestion[]> => {
      const response = await fetch(`/api/v1/ai/suggestions?tenantId=${encodeURIComponent(tenantId)}`);
      if (!response.ok) {
        throw new Error(`Failed to fetch AI suggestions: ${response.statusText}`);
      }
      const body: unknown = await response.json();
      if (body !== null && typeof body === 'object' && 'suggestions' in body && Array.isArray((body as Record<string, unknown>).suggestions)) {
        return (body as { suggestions: AiSuggestion[] }).suggestions;
      }
      return Array.isArray(body) ? (body as AiSuggestion[]) : [];
    },
    enabled: isFeatureEnabled('AI_SUGGESTIONS'),
    staleTime: 60_000,
  });

  return {
    suggestions,
    isLoading,
    error,
    refetch,
  };
}
