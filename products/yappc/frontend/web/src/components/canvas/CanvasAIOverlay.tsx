/**
 * Canvas AI Overlay Component
 *
 * Displays AI-powered suggestions and ghost nodes directly on the canvas.
 * Provides inline contextual suggestions without requiring a separate panel.
 *
 * @doc.type component
 * @doc.purpose Inline AI suggestions on canvas
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import { Sparkles as AIIcon, X as CloseIcon, ChevronDown as ExpandIcon, ChevronUp as CollapseIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent } from '@ghatana/design-system';
import { ContextualSuggestions } from '../ai/ContextualSuggestions';
import { NextBestAction } from '../ai/NextBestAction';
import type { ContextualSuggestion } from '../ai/ContextualSuggestions';
import type { NextAction } from '../ai/NextBestAction';

// ============================================================================
// Types
// ============================================================================

export interface CanvasAIOverlayProps {
  suggestions: ContextualSuggestion[];
  nextAction: NextAction | null;
  position?: 'top-left' | 'top-right' | 'bottom-left' | 'bottom-right';
  collapsed?: boolean;
  onDismissSuggestion?: (id: string) => void;
  onDismissNextAction?: () => void;
  onToggleCollapse?: () => void;
  className?: string;
}

// ============================================================================
// Position Styles
// ============================================================================

const getPositionStyles = (position: CanvasAIOverlayProps['position']) => {
  switch (position) {
    case 'top-left':
      return 'top-4 left-4';
    case 'top-right':
      return 'top-4 right-4';
    case 'bottom-left':
      return 'bottom-4 left-4';
    case 'bottom-right':
      return 'bottom-4 right-4';
    default:
      return 'top-4 right-4';
  }
};

// ============================================================================
// Canvas AI Overlay Component
// ============================================================================

/**
 * Canvas AI Overlay Component
 */
export function CanvasAIOverlay({
  suggestions,
  nextAction,
  position = 'top-right',
  collapsed = false,
  onDismissSuggestion,
  onDismissNextAction,
  onToggleCollapse,
  className = '',
}: CanvasAIOverlayProps): ReactNode {
  const [internalCollapsed, setInternalCollapsed] = useState(collapsed);
  const [showNextAction, setShowNextAction] = useState(true);

  const handleToggleCollapse = useCallback(() => {
    setInternalCollapsed(prev => !prev);
    onToggleCollapse?.();
  }, [onToggleCollapse]);

  const handleDismissNextAction = useCallback(() => {
    setShowNextAction(false);
    onDismissNextAction?.();
  }, [onDismissNextAction]);

  if (internalCollapsed) {
    return (
      <div className={`fixed ${getPositionStyles(position)} z-40 ${className}`}>
        <Button
          size="sm"
          onClick={handleToggleCollapse}
          startIcon={<AIIcon className="w-4 h-4" />}
          className="bg-info-color hover:bg-info-bg text-white shadow-lg"
        >
          Suggested Improvements
          <Chip
            size="sm"
            label={suggestions.length + (showNextAction && nextAction ? 1 : 0)}
            className="ml-2 text-xs"
          />
        </Button>
      </div>
    );
  }

  return (
    <div className={`fixed ${getPositionStyles(position)} z-40 w-80 max-h-[80vh] overflow-y-auto ${className}`}>
      <Card className="shadow-xl border-2 border-info-border dark:border-info-border">
        <CardContent className="p-4">
          {/* Header */}
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <AIIcon className="w-5 h-5 text-info-color dark:text-info-color" />
              <Typography className="font-bold text-sm">
                Guided Assistant
              </Typography>
            </div>
            <div className="flex items-center gap-1">
              <Button
                size="sm"
                variant="text"
                onClick={handleToggleCollapse}
                className="text-fg-muted hover:text-fg-muted"
              >
                <CollapseIcon className="w-4 h-4" />
              </Button>
              <Button
                size="sm"
                variant="text"
                onClick={handleToggleCollapse}
                className="text-fg-muted hover:text-fg-muted"
              >
                <CloseIcon className="w-4 h-4" />
              </Button>
            </div>
          </div>

          {/* Next Action */}
          {showNextAction && nextAction && (
            <div className="mb-4">
              <NextBestAction
                action={nextAction}
                position="inline"
                showImpact
                showEstimatedTime
              />
              <Button
                size="sm"
                variant="text"
                onClick={handleDismissNextAction}
                className="w-full mt-2 text-xs text-fg-muted"
              >
                Dismiss
              </Button>
            </div>
          )}

          {/* Contextual Suggestions */}
          <ContextualSuggestions
            suggestions={suggestions}
            position="inline"
            maxSuggestions={3}
            onDismiss={onDismissSuggestion}
          />
        </CardContent>
      </Card>
    </div>
  );
}
