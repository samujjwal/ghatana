/**
 * Contextual AI Suggestions Component
 *
 * Displays inline contextual AI suggestions based on current user context and actions.
 * Provides next-best-action recommendations and intelligent task prioritization.
 *
 * @doc.type component
 * @doc.purpose Inline contextual AI suggestions
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { useState, useCallback, ReactNode } from 'react';
import { Sparkles as AIIcon, ChevronRight, X as DismissIcon, Lightbulb as SuggestionIcon, AlertTriangle as WarningIcon, CheckCircle as SuccessIcon, Clock as PendingIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent, CardActions } from '@ghatana/design-system';

// ============================================================================
// Types
// ============================================================================

export interface ContextualSuggestion {
  id: string;
  type: 'action' | 'warning' | 'info' | 'success' | 'pending';
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  confidence: number; // 0-1
  priority: 'low' | 'medium' | 'high' | 'critical';
  context: string;
}

export interface ContextualSuggestionsProps {
  suggestions: ContextualSuggestion[];
  position?: 'top' | 'bottom' | 'inline';
  maxSuggestions?: number;
  onDismiss?: (id: string) => void;
  onDismissAll?: () => void;
  className?: string;
}

// ============================================================================
// Suggestion Icon Mapping
// ============================================================================

const getSuggestionIcon = (type: ContextualSuggestion['type']) => {
  switch (type) {
    case 'action':
      return <AIIcon className="w-4 h-4" />;
    case 'warning':
      return <WarningIcon className="w-4 h-4" />;
    case 'info':
      return <SuggestionIcon className="w-4 h-4" />;
    case 'success':
      return <SuccessIcon className="w-4 h-4" />;
    case 'pending':
      return <PendingIcon className="w-4 h-4" />;
    default:
      return <SuggestionIcon className="w-4 h-4" />;
  }
};

const getSuggestionColor = (type: ContextualSuggestion['type']) => {
  switch (type) {
    case 'action':
      return 'text-blue-600 dark:text-blue-400';
    case 'warning':
      return 'text-orange-600 dark:text-orange-400';
    case 'info':
      return 'text-purple-600 dark:text-purple-400';
    case 'success':
      return 'text-green-600 dark:text-green-400';
    case 'pending':
      return 'text-gray-600 dark:text-gray-400';
    default:
      return 'text-gray-600 dark:text-gray-400';
  }
};

const getPriorityColor = (priority: ContextualSuggestion['priority']) => {
  switch (priority) {
    case 'critical':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-300';
    case 'high':
      return 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300';
    case 'medium':
      return 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-300';
    case 'low':
      return 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-300';
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-300';
  }
};

// ============================================================================
// Contextual Suggestions Component
// ============================================================================

/**
 * Contextual AI Suggestions Component
 */
export function ContextualSuggestions({
  suggestions,
  position = 'inline',
  maxSuggestions = 3,
  onDismiss,
  onDismissAll,
  className = '',
}: ContextualSuggestionsProps): ReactNode {
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const filteredSuggestions = suggestions
    .filter(s => !dismissedIds.has(s.id))
    .sort((a, b) => {
      const priorityOrder = { critical: 4, high: 3, medium: 2, low: 1 };
      return priorityOrder[b.priority] - priorityOrder[a.priority];
    })
    .slice(0, maxSuggestions);

  const handleDismiss = useCallback((id: string) => {
    setDismissedIds(prev => new Set(prev).add(id));
    onDismiss?.(id);
  }, [onDismiss]);

  const handleDismissAll = useCallback(() => {
    setDismissedIds(new Set(filteredSuggestions.map(s => s.id)));
    onDismissAll?.();
  }, [filteredSuggestions, onDismissAll]);

  const handleAction = useCallback((suggestion: ContextualSuggestion) => {
    suggestion.onAction?.();
    handleDismiss(suggestion.id);
  }, [handleDismiss]);

  if (filteredSuggestions.length === 0) {
    return null;
  }

  return (
    <div className={`space-y-2 ${className}`}>
      {/* Header */}
      <div className="flex items-center justify-between mb-2">
        <div className="flex items-center gap-2">
          <AIIcon className="w-4 h-4 text-blue-600 dark:text-blue-400" />
          <Typography className="font-medium text-sm text-blue-900 dark:text-blue-100">
            AI Suggestions
          </Typography>
          <Chip
            size="sm"
            label={filteredSuggestions.length}
            className="text-xs"
          />
        </div>
        {filteredSuggestions.length > 1 && (
          <Button
            size="sm"
            variant="text"
            onClick={handleDismissAll}
            className="text-xs text-gray-500"
          >
            Dismiss All
          </Button>
        )}
      </div>

      {/* Suggestions */}
      {filteredSuggestions.map(suggestion => (
        <Card
          key={suggestion.id}
          variant="outlined"
          className={`transition-all duration-200 ${
            expandedId === suggestion.id ? 'ring-2 ring-blue-500' : 'hover:shadow-md'
          }`}
          onClick={() => setExpandedId(expandedId === suggestion.id ? null : suggestion.id)}
        >
          <CardContent className="p-3">
            <div className="flex items-start gap-3">
              {/* Icon */}
              <div className={`flex-shrink-0 mt-0.5 ${getSuggestionColor(suggestion.type)}`}>
                {getSuggestionIcon(suggestion.type)}
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <Typography className="font-medium text-sm truncate">
                    {suggestion.title}
                  </Typography>
                  <Chip
                    size="sm"
                    label={suggestion.priority}
                    className={`text-xs ${getPriorityColor(suggestion.priority)}`}
                  />
                </div>
                <Typography className="text-xs text-gray-600 dark:text-gray-400 line-clamp-2">
                  {suggestion.description}
                </Typography>

                {/* Confidence indicator */}
                <div className="flex items-center gap-2 mt-2">
                  <div className="flex-1 h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
                    <div
                      className="h-full bg-blue-500 transition-all duration-300"
                      style={{ width: `${suggestion.confidence * 100}%` }}
                    />
                  </div>
                  <Typography className="text-xs text-gray-500">
                    {Math.round(suggestion.confidence * 100)}%
                  </Typography>
                </div>

                {/* Expanded details */}
                {expandedId === suggestion.id && (
                  <div className="mt-2 pt-2 border-t border-gray-200 dark:border-gray-700">
                    <Typography className="text-xs text-gray-500">
                      Context: {suggestion.context}
                    </Typography>
                  </div>
                )}
              </div>

              {/* Dismiss button */}
              <Button
                size="sm"
                variant="text"
                onClick={(e) => {
                  e.stopPropagation();
                  handleDismiss(suggestion.id);
                }}
                className="flex-shrink-0 text-gray-400 hover:text-gray-600"
              >
                <DismissIcon className="w-4 h-4" />
              </Button>
            </div>
          </CardContent>

          {/* Actions */}
          {suggestion.actionLabel && suggestion.onAction && (
            <CardActions className="px-3 pb-3 pt-0">
              <Button
                size="sm"
                onClick={(e) => {
                  e.stopPropagation();
                  handleAction(suggestion);
                }}
                endIcon={<ChevronRight className="w-4 h-4" />}
                className="text-xs"
              >
                {suggestion.actionLabel}
              </Button>
            </CardActions>
          )}
        </Card>
      ))}
    </div>
  );
}
