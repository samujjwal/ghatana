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
      return 'text-info-color dark:text-info-color';
    case 'warning':
      return 'text-warning-color dark:text-warning-color';
    case 'info':
      return 'text-info-color dark:text-info-color';
    case 'success':
      return 'text-success-color dark:text-success-color';
    case 'pending':
      return 'text-fg-muted dark:text-fg-muted';
    default:
      return 'text-fg-muted dark:text-fg-muted';
  }
};

const getPriorityColor = (priority: ContextualSuggestion['priority']) => {
  switch (priority) {
    case 'critical':
      return 'bg-destructive-bg text-destructive dark:bg-destructive-bg/30 dark:text-destructive';
    case 'high':
      return 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color';
    case 'medium':
      return 'bg-warning-bg text-warning-color dark:bg-warning-bg/30 dark:text-warning-color';
    case 'low':
      return 'bg-surface-muted text-fg dark:bg-surface/30 dark:text-fg-muted';
    default:
      return 'bg-surface-muted text-fg dark:bg-surface/30 dark:text-fg-muted';
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
          <AIIcon className="w-4 h-4 text-info-color dark:text-info-color" />
          <Typography className="font-medium text-sm text-info-color dark:text-info-color">
            Suggested Improvements
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
            className="text-xs text-fg-muted"
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
                <Typography className="text-xs text-fg-muted dark:text-fg-muted line-clamp-2">
                  {suggestion.description}
                </Typography>

                {/* Confidence indicator */}
                <div className="flex items-center gap-2 mt-2">
                  <div className="flex-1 h-1.5 bg-surface-muted dark:bg-surface-muted rounded-full overflow-hidden">
                    <div
                      className="h-full bg-info-bg transition-all duration-300"
                      style={{ width: `${suggestion.confidence * 100}%` }}
                    />
                  </div>
                  <Typography className="text-xs text-fg-muted">
                    {Math.round(suggestion.confidence * 100)}%
                  </Typography>
                </div>

                {/* Expanded details */}
                {expandedId === suggestion.id && (
                  <div className="mt-2 pt-2 border-t border-border dark:border-border">
                    <Typography className="text-xs text-fg-muted">
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
                className="flex-shrink-0 text-fg-muted hover:text-fg-muted"
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
