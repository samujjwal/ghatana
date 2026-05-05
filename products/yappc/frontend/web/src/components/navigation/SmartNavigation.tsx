/**
 * Smart Navigation Component
 *
 * AI-powered navigation suggestions based on user context and behavior.
 * Provides intelligent navigation recommendations and smart content discovery.
 *
 * @doc.type component
 * @doc.purpose AI-powered navigation suggestions
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import { useNavigate } from 'react-router';
import { Compass as NavigationIcon, ArrowRight as ArrowIcon, TrendingUp as TrendingIcon, Clock as TimeIcon, Star as FavoriteIcon, History as HistoryIcon, Sparkles as AIIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent, CardActions } from '@ghatana/design-system';

// ============================================================================
// Types
// ============================================================================

export interface NavigationSuggestion {
  id: string;
  title: string;
  path: string;
  type: 'frequent' | 'recent' | 'recommended' | 'ai-suggested';
  description?: string;
  score?: number;
  metadata?: Record<string, unknown>;
}

export interface SmartNavigationProps {
  suggestions: NavigationSuggestion[];
  maxSuggestions?: number;
  showType?: boolean;
  showScore?: boolean;
  onNavigate?: (path: string) => void;
  onDismiss?: (id: string) => void;
  className?: string;
}

// ============================================================================
// Icon Mapping
// ============================================================================

const getSuggestionIcon = (type: NavigationSuggestion['type']) => {
  switch (type) {
    case 'frequent':
      return <TrendingIcon className="w-4 h-4" />;
    case 'recent':
      return <TimeIcon className="w-4 h-4" />;
    case 'recommended':
      return <FavoriteIcon className="w-4 h-4" />;
    case 'ai-suggested':
      return <AIIcon className="w-4 h-4" />;
    default:
      return <NavigationIcon className="w-4 h-4" />;
  }
};

const getSuggestionColor = (type: NavigationSuggestion['type']) => {
  switch (type) {
    case 'frequent':
      return 'text-info-color dark:text-info-color bg-info-bg dark:bg-info-bg/20';
    case 'recent':
      return 'text-info-color dark:text-info-color bg-info-bg dark:bg-info-bg/20';
    case 'recommended':
      return 'text-success-color dark:text-success-color bg-success-bg dark:bg-success-bg/20';
    case 'ai-suggested':
      return 'text-warning-color dark:text-warning-color bg-warning-bg dark:bg-warning-bg/20';
    default:
      return 'text-fg-muted dark:text-fg-muted bg-surface-muted dark:bg-surface/20';
  }
};

const getSuggestionLabel = (type: NavigationSuggestion['type']) => {
  switch (type) {
    case 'frequent':
      return 'Frequent';
    case 'recent':
      return 'Recent';
    case 'recommended':
      return 'Recommended';
    case 'ai-suggested':
      return 'AI Suggested';
    default:
      return 'Suggested';
  }
};

// ============================================================================
// Smart Navigation Component
// ============================================================================

/**
 * Smart Navigation Component
 */
export function SmartNavigation({
  suggestions,
  maxSuggestions = 5,
  showType = true,
  showScore = false,
  onNavigate,
  onDismiss,
  className = '',
}: SmartNavigationProps): ReactNode {
  const navigate = useNavigate();
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());

  const filteredSuggestions = suggestions
    .filter(s => !dismissedIds.has(s.id))
    .slice(0, maxSuggestions);

  const handleNavigate = useCallback(
    (suggestion: NavigationSuggestion) => {
      onNavigate?.(suggestion.path);
      navigate(suggestion.path);
    },
    [onNavigate, navigate]
  );

  const handleDismiss = useCallback((id: string) => {
    setDismissedIds(prev => new Set(prev).add(id));
    onDismiss?.(id);
  }, [onDismiss]);

  if (filteredSuggestions.length === 0) {
    return null;
  }

  return (
    <div className={`space-y-2 ${className}`}>
      {/* Header */}
      <div className="flex items-center gap-2 mb-3">
        <NavigationIcon className="w-5 h-5 text-info-color dark:text-info-color" />
        <Typography className="font-bold text-sm">
          Quick Navigation
        </Typography>
        <Chip
          size="sm"
          label={filteredSuggestions.length}
          className="text-xs"
        />
      </div>

      {/* Suggestions */}
      {filteredSuggestions.map((suggestion) => (
        <Card
          key={suggestion.id}
          variant="outlined"
          className="hover:shadow-md transition-shadow"
        >
          <CardContent className="p-3">
            <div className="flex items-start gap-3">
              {/* Icon */}
              <div className={`flex-shrink-0 p-2 rounded-md ${getSuggestionColor(suggestion.type)}`}>
                {getSuggestionIcon(suggestion.type)}
              </div>

              {/* Content */}
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <Typography className="font-medium text-sm truncate">
                    {suggestion.title}
                  </Typography>
                  {showType && (
                    <Chip
                      size="sm"
                      label={getSuggestionLabel(suggestion.type)}
                      className={`text-xs ${getSuggestionColor(suggestion.type)}`}
                    />
                  )}
                </div>

                {suggestion.description && (
                  <Typography className="text-xs text-fg-muted dark:text-fg-muted line-clamp-2">
                    {suggestion.description}
                  </Typography>
                )}

                {/* Score */}
                {showScore && suggestion.score !== undefined && (
                  <div className="flex items-center gap-2 mt-1">
                    <div className="flex-1 h-1.5 bg-surface-muted dark:bg-surface-muted rounded-full overflow-hidden">
                      <div
                        className="h-full bg-info-bg"
                        style={{ width: `${suggestion.score * 100}%` }}
                      />
                    </div>
                    <Typography className="text-xs text-fg-muted">
                      {Math.round(suggestion.score * 100)}%
                    </Typography>
                  </div>
                )}
              </div>

              {/* Dismiss Button */}
              <Button
                size="sm"
                variant="text"
                onClick={() => handleDismiss(suggestion.id)}
                className="flex-shrink-0 text-fg-muted hover:text-fg-muted"
              >
                ×
              </Button>
            </div>
          </CardContent>

          {/* Actions */}
          <CardActions className="px-3 pb-3 pt-0">
            <Button
              size="sm"
              onClick={() => handleNavigate(suggestion)}
              endIcon={<ArrowIcon className="w-4 h-4" />}
              className="text-xs"
            >
              Navigate
            </Button>
          </CardActions>
        </Card>
      ))}
    </div>
  );
}
