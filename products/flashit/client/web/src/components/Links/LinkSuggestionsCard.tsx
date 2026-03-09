/**
 * LinkSuggestionsCard - Display AI-powered link suggestions for a moment
 * Phase 1 Week 11: Linking & Temporal Arcs (Day 55)
 */

import { useState } from 'react';
import { useLinkSuggestions, useCreateMomentLink } from '../../hooks/use-api';
import {
  Sparkles,
  Link2,
  Check,
  X,
  Loader2,
  RefreshCw,
  ChevronDown,
  ChevronUp,
  Lightbulb,
} from 'lucide-react';
import { format, parseISO } from 'date-fns';

export interface LinkSuggestionsCardProps {
  momentId: string;
  existingLinkIds?: string[];
  onLinkCreated?: (linkId: string) => void;
}

interface Suggestion {
  id: string;
  targetMomentId: string;
  targetMoment: {
    id: string;
    contentText: string;
    capturedAt: string;
  };
  suggestedLinkType: string;
  confidence: number;
  reason: string;
}

// Link type labels for display
const LINK_TYPE_LABELS: Record<string, string> = {
  related: 'Related',
  follows: 'Follows',
  precedes: 'Precedes',
  references: 'References',
  causes: 'Causes',
  similar: 'Similar',
  contradicts: 'Contradicts',
  elaborates: 'Elaborates',
  summarizes: 'Summarizes',
};

// Confidence level colors
const getConfidenceColor = (confidence: number): string => {
  if (confidence >= 0.8) return 'text-green-600 bg-green-100';
  if (confidence >= 0.6) return 'text-yellow-600 bg-yellow-100';
  return 'text-gray-600 bg-gray-100';
};

const getConfidenceLabel = (confidence: number): string => {
  if (confidence >= 0.8) return 'High';
  if (confidence >= 0.6) return 'Medium';
  return 'Low';
};

export default function LinkSuggestionsCard({
  momentId,
  existingLinkIds = [],
  onLinkCreated,
}: LinkSuggestionsCardProps) {
  const [isExpanded, setIsExpanded] = useState(true);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
  const [creatingLinkFor, setCreatingLinkFor] = useState<string | null>(null);

  const {
    data: suggestions,
    isLoading,
    refetch,
    isRefetching,
  } = useLinkSuggestions(momentId, {
    limit: 5,
    threshold: 0.5,
  });

  const createLinkMutation = useCreateMomentLink();

  // Filter out already linked and dismissed suggestions
  const filteredSuggestions = (suggestions as Suggestion[] | undefined)?.filter(
    (s) => !existingLinkIds.includes(s.targetMomentId) && !dismissedIds.has(s.id)
  ) || [];

  const handleCreateLink = async (suggestion: Suggestion) => {
    setCreatingLinkFor(suggestion.id);
    try {
      const result = await createLinkMutation.mutateAsync({
        momentId,
        data: {
          targetMomentId: suggestion.targetMomentId,
          linkType: suggestion.suggestedLinkType,
          metadata: {
            aiSuggested: true,
            confidence: suggestion.confidence,
            reason: suggestion.reason,
          },
        },
      });
      onLinkCreated?.(result.id);
      setDismissedIds((prev) => new Set([...prev, suggestion.id]));
    } catch (error) {
      console.error('Failed to create link:', error);
    } finally {
      setCreatingLinkFor(null);
    }
  };

  const handleDismiss = (suggestionId: string) => {
    setDismissedIds((prev) => new Set([...prev, suggestionId]));
  };

  if (isLoading) {
    return (
      <div className="rounded-lg border border-gray-200 bg-gradient-to-r from-purple-50 to-indigo-50 p-4">
        <div className="flex items-center gap-2">
          <Loader2 className="h-4 w-4 animate-spin text-purple-500" />
          <span className="text-sm text-purple-600">Finding related moments...</span>
        </div>
      </div>
    );
  }

  if (filteredSuggestions.length === 0) {
    return (
      <div className="rounded-lg border border-dashed border-gray-200 bg-gray-50 p-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2 text-gray-500">
            <Lightbulb className="h-4 w-4" />
            <span className="text-sm">No link suggestions available</span>
          </div>
          <button
            onClick={() => refetch()}
            disabled={isRefetching}
            className="flex items-center gap-1 text-xs text-gray-500 hover:text-gray-700"
          >
            <RefreshCw className={`h-3 w-3 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="rounded-lg border border-purple-200 bg-gradient-to-r from-purple-50 to-indigo-50">
      {/* Header */}
      <button
        onClick={() => setIsExpanded(!isExpanded)}
        className="flex w-full items-center justify-between px-4 py-3"
      >
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 text-purple-500" />
          <span className="font-medium text-purple-800">
            AI Link Suggestions
          </span>
          <span className="rounded-full bg-purple-200 px-2 py-0.5 text-xs font-medium text-purple-700">
            {filteredSuggestions.length}
          </span>
        </div>
        <div className="flex items-center gap-2">
          <button
            onClick={(e) => {
              e.stopPropagation();
              refetch();
            }}
            disabled={isRefetching}
            className="rounded p-1 text-purple-500 hover:bg-purple-100"
          >
            <RefreshCw className={`h-4 w-4 ${isRefetching ? 'animate-spin' : ''}`} />
          </button>
          {isExpanded ? (
            <ChevronUp className="h-4 w-4 text-purple-500" />
          ) : (
            <ChevronDown className="h-4 w-4 text-purple-500" />
          )}
        </div>
      </button>

      {/* Suggestions List */}
      {isExpanded && (
        <div className="space-y-2 px-4 pb-4">
          {filteredSuggestions.map((suggestion) => (
            <div
              key={suggestion.id}
              className="rounded-lg border border-white bg-white p-3 shadow-sm"
            >
              <div className="flex items-start justify-between gap-3">
                <div className="min-w-0 flex-1">
                  {/* Target moment preview */}
                  <p className="line-clamp-2 text-sm text-gray-700">
                    {suggestion.targetMoment.contentText}
                  </p>

                  {/* Metadata row */}
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    {/* Link type badge */}
                    <span className="inline-flex items-center gap-1 rounded-full bg-indigo-100 px-2 py-0.5 text-xs font-medium text-indigo-700">
                      <Link2 className="h-3 w-3" />
                      {LINK_TYPE_LABELS[suggestion.suggestedLinkType] || suggestion.suggestedLinkType}
                    </span>

                    {/* Confidence badge */}
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${getConfidenceColor(
                        suggestion.confidence
                      )}`}
                    >
                      {getConfidenceLabel(suggestion.confidence)} ({Math.round(suggestion.confidence * 100)}%)
                    </span>

                    {/* Date */}
                    <span className="text-xs text-gray-400">
                      {format(parseISO(suggestion.targetMoment.capturedAt), 'MMM d, yyyy')}
                    </span>
                  </div>

                  {/* Reason */}
                  {suggestion.reason && (
                    <p className="mt-2 text-xs italic text-gray-500">
                      "{suggestion.reason}"
                    </p>
                  )}
                </div>

                {/* Actions */}
                <div className="flex flex-shrink-0 items-center gap-1">
                  <button
                    onClick={() => handleCreateLink(suggestion)}
                    disabled={creatingLinkFor === suggestion.id}
                    className="rounded-lg bg-purple-600 p-2 text-white transition-colors hover:bg-purple-700 disabled:opacity-50"
                    title="Create this link"
                  >
                    {creatingLinkFor === suggestion.id ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Check className="h-4 w-4" />
                    )}
                  </button>
                  <button
                    onClick={() => handleDismiss(suggestion.id)}
                    className="rounded-lg border border-gray-200 bg-white p-2 text-gray-400 transition-colors hover:bg-gray-50 hover:text-gray-600"
                    title="Dismiss suggestion"
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
