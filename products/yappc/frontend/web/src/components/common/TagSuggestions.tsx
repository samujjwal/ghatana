/**
 * Tag Suggestions Component
 *
 * Displays AI-powered tag suggestions with accept/reject actions.
 * Provides confidence scores and category indicators.
 *
 * @doc.type component
 * @doc.purpose AI-powered tag suggestions display
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState } from 'react';
import { Sparkles as AIIcon, Check as AcceptIcon, X as RejectIcon } from 'lucide-react';
import { Typography, Button, Chip, Card, CardContent } from '@ghatana/design-system';
import type { TagSuggestion } from '../../services/ai/ClassificationService';

// ============================================================================
// Types
// ============================================================================

export interface TagSuggestionsProps {
  suggestions: TagSuggestion[];
  onAccept?: (tag: string) => void;
  onReject?: (tag: string) => void;
  onAcceptAll?: () => void;
  maxSuggestions?: number;
  showCategory?: boolean;
  showReason?: boolean;
  className?: string;
}

// ============================================================================
// Tag Suggestion Item Component
// ============================================================================

interface TagSuggestionItemProps {
  suggestion: TagSuggestion;
  onAccept: () => void;
  onReject: () => void;
  showCategory: boolean;
  showReason: boolean;
}

function TagSuggestionItem({
  suggestion,
  onAccept,
  onReject,
  showCategory,
  showReason,
}: TagSuggestionItemProps): ReactNode {
  const getCategoryColor = (category: TagSuggestion['category']) => {
    switch (category) {
      case 'domain':
        return 'bg-info-bg text-info-color';
      case 'technology':
        return 'bg-surface-muted text-fg';
      case 'priority':
        return 'bg-warning-bg text-warning-color';
      case 'status':
        return 'bg-success-bg text-success-color';
      default:
        return 'bg-muted text-muted-foreground';
    }
  };

  return (
    <div className="flex items-center gap-3 p-2 bg-muted/40 rounded-md hover:bg-muted/70 transition-colors">
      {/* Tag and confidence */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2 mb-1">
          <Chip size="sm" label={suggestion.tag} className="text-xs" />
          {showCategory && (
            <Chip
              size="sm"
              label={suggestion.category}
              className={`text-xs ${getCategoryColor(suggestion.category)}`}
            />
          )}
        </div>
        {showReason && suggestion.reason && (
          <Typography className="text-xs text-muted-foreground">
            {suggestion.reason}
          </Typography>
        )}
        <div className="flex items-center gap-2 mt-1">
          <div className="flex-1 h-1.5 bg-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-info-color"
              style={{ width: `${suggestion.confidence * 100}%` }}
            />
          </div>
          <Typography className="text-xs text-muted-foreground">
            {Math.round(suggestion.confidence * 100)}%
          </Typography>
        </div>
      </div>

      {/* Actions */}
      <div className="flex items-center gap-1">
        <Button
          size="sm"
          variant="text"
          onClick={onAccept}
          className="text-success-color"
        >
          <AcceptIcon className="w-4 h-4" />
        </Button>
        <Button
          size="sm"
          variant="text"
          onClick={onReject}
          className="text-destructive"
        >
          <RejectIcon className="w-4 h-4" />
        </Button>
      </div>
    </div>
  );
}

// ============================================================================
// Tag Suggestions Component
// ============================================================================

/**
 * Tag Suggestions Component
 */
export function TagSuggestions({
  suggestions,
  onAccept,
  onReject,
  onAcceptAll,
  maxSuggestions = 5,
  showCategory = true,
  showReason = true,
  className = '',
}: TagSuggestionsProps): ReactNode {
  const [expanded, setExpanded] = useState(false);

  const filteredSuggestions = suggestions.slice(0, expanded ? suggestions.length : maxSuggestions);

  if (filteredSuggestions.length === 0) {
    return null;
  }

  return (
    <Card variant="outlined" className={`border-info-border ${className}`}>
      <CardContent className="p-4">
        {/* Header */}
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <AIIcon className="w-4 h-4 text-info-color" />
            <Typography className="font-medium text-sm text-info-color">
              Suggested Tags
            </Typography>
            <Chip size="sm" label={filteredSuggestions.length} className="text-xs" />
          </div>
          {onAcceptAll && suggestions.length > 1 && (
            <Button
              size="sm"
              variant="text"
              onClick={onAcceptAll}
              className="text-xs text-info-color"
            >
              Accept All
            </Button>
          )}
        </div>

        {/* Suggestions */}
        <div className="space-y-2">
          {filteredSuggestions.map((suggestion) => (
            <TagSuggestionItem
              key={suggestion.tag}
              suggestion={suggestion}
              onAccept={() => onAccept?.(suggestion.tag)}
              onReject={() => onReject?.(suggestion.tag)}
              showCategory={showCategory}
              showReason={showReason}
            />
          ))}
        </div>

        {/* Expand/Collapse */}
        {suggestions.length > maxSuggestions && (
          <Button
            size="sm"
            variant="text"
            onClick={() => setExpanded(!expanded)}
            className="w-full mt-2 text-xs text-muted-foreground"
          >
            {expanded ? 'Show less' : `Show ${suggestions.length - maxSuggestions} more`}
          </Button>
        )}
      </CardContent>
    </Card>
  );
}
