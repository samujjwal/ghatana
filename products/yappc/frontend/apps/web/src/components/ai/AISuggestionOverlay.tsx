import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';

/**
 * AI Suggestion Overlay component.
 *
 * Shows AI suggestions when hovering over editable elements.
 * Non-intrusive, appears after hover delay.
 *
 * @doc.type component
 * @doc.purpose Contextual AI suggestions on hover
 * @doc.layer ui
 */

export interface AISuggestion {
  id: string;
  title: string;
  description: string;
  confidence: number;
  action: () => void;
  type: 'enhancement' | 'fix' | 'optimization' | 'alternative';
}

export interface AISuggestionOverlayProps {
  suggestions: AISuggestion[];
  position?: { x: number; y: number };
  onSuggestionAccept?: (suggestion: AISuggestion) => void;
  onDismiss?: () => void;
  className?: string;
}

function SuggestionTypeIcon({ type }: { type: AISuggestion['type'] }) {
  const config = {
    enhancement: { icon: '✨', color: 'text-purple-500' },
    fix: { icon: '🔧', color: 'text-red-500' },
    optimization: { icon: '⚡', color: 'text-yellow-500' },
    alternative: { icon: '💡', color: 'text-blue-500' },
  };

  const { icon, color } = config[type];
  return <span className={cn('text-sm', color)}>{icon}</span>;
}

function ConfidenceBadge({ confidence }: { confidence: number }) {
  const level =
    confidence >= 0.8 ? 'high' : confidence >= 0.5 ? 'medium' : 'low';
  const config = {
    high: {
      label: 'High',
      color:
        'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400',
    },
    medium: {
      label: 'Med',
      color:
        'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
    },
    low: {
      label: 'Low',
      color: 'bg-gray-100 text-gray-700 dark:bg-gray-900/30 dark:text-gray-400',
    },
  };

  const { label, color } = config[level];
  return (
    <span className={cn('text-xs px-1.5 py-0.5 rounded', color)}>{label}</span>
  );
}

function SuggestionItem({
  suggestion,
  onAccept,
}: {
  suggestion: AISuggestion;
  onAccept?: (suggestion: AISuggestion) => void;
}) {
  return (
    <div
      className={cn(
        'p-3 border-b border-gray-200 dark:border-gray-700 last:border-b-0',
        'hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors cursor-pointer'
      )}
      onClick={() => onAccept?.(suggestion)}
    >
      <div className="flex items-start gap-2 mb-1">
        <SuggestionTypeIcon type={suggestion.type} />
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 mb-1">
            <span className="text-sm font-medium text-gray-900 dark:text-gray-100">
              {suggestion.title}
            </span>
            <ConfidenceBadge confidence={suggestion.confidence} />
          </div>
          <p className="text-xs text-gray-600 dark:text-gray-400">
            {suggestion.description}
          </p>
        </div>
      </div>
    </div>
  );
}

export function AISuggestionOverlay({
  suggestions,
  position,
  onSuggestionAccept,
  onDismiss,
  className,
}: AISuggestionOverlayProps) {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    if (suggestions.length > 0) {
      const timer = setTimeout(() => setIsVisible(true), 100);
      return () => clearTimeout(timer);
    } else {
      setIsVisible(false);
    }
  }, [suggestions]);

  useEffect(() => {
    const handleEscape = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onDismiss?.();
      }
    };

    window.addEventListener('keydown', handleEscape);
    return () => window.removeEventListener('keydown', handleEscape);
  }, [onDismiss]);

  if (!isVisible || suggestions.length === 0) {
    return null;
  }

  const style = position
    ? {
        position: 'absolute' as const,
        left: `${position.x}px`,
        top: `${position.y}px`,
      }
    : {};

  return (
    <>
      {/* Backdrop */}
      <div className="fixed inset-0 z-40" onClick={onDismiss} />

      {/* Overlay */}
      <div
        className={cn(
          'fixed z-50 w-80 bg-white dark:bg-gray-900',
          'border border-gray-200 dark:border-gray-700',
          'rounded-lg shadow-xl',
          'animate-in fade-in slide-in-from-top-2 duration-200',
          className
        )}
        style={style}
      >
        {/* Header */}
        <div className="px-3 py-2 border-b border-gray-200 dark:border-gray-700 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="text-sm font-semibold text-gray-900 dark:text-gray-100">
              AI Suggestions
            </span>
            <span className="text-xs text-gray-500 dark:text-gray-500">
              {suggestions.length}
            </span>
          </div>
          <button
            onClick={onDismiss}
            className="text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 text-sm"
          >
            ✕
          </button>
        </div>

        {/* Suggestions List */}
        <div className="max-h-96 overflow-y-auto">
          {suggestions.map((suggestion) => (
            <SuggestionItem
              key={suggestion.id}
              suggestion={suggestion}
              onAccept={onSuggestionAccept}
            />
          ))}
        </div>

        {/* Footer */}
        <div className="px-3 py-2 border-t border-gray-200 dark:border-gray-700 text-xs text-gray-500 dark:text-gray-500">
          Press{' '}
          <kbd className="px-1 py-0.5 bg-gray-100 dark:bg-gray-800 rounded">
            Esc
          </kbd>{' '}
          to dismiss
        </div>
      </div>
    </>
  );
}

/**
 * Hook for managing AI suggestions for an element.
 *
 * @doc.type hook
 * @doc.purpose AI suggestion state management with debounced fetching
 */
export function useAISuggestion<T>(
  element: T | null,
  options: {
    enabled?: boolean;
    hoverDelay?: number;
    fetchSuggestions?: (element: T) => Promise<AISuggestion[]>;
  } = {}
) {
  const { enabled = true, hoverDelay = 500, fetchSuggestions } = options;

  const [suggestions, setSuggestions] = useState<AISuggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isVisible, setIsVisible] = useState(false);
  const [position, setPosition] = useState<
    { x: number; y: number } | undefined
  >();

  useEffect(() => {
    if (!enabled || !element || !fetchSuggestions) {
      setSuggestions([]);
      return;
    }

    const timer = setTimeout(async () => {
      setIsLoading(true);
      try {
        const aiSuggestions = await fetchSuggestions(element);
        setSuggestions(aiSuggestions);
      } catch (error) {
        console.error('Failed to fetch AI suggestions:', error);
        setSuggestions([]);
      } finally {
        setIsLoading(false);
      }
    }, hoverDelay);

    return () => clearTimeout(timer);
  }, [element, enabled, hoverDelay, fetchSuggestions]);

  const handleMouseEnter = (e: React.MouseEvent) => {
    setIsVisible(true);
    setPosition({ x: e.clientX + 10, y: e.clientY + 10 });
  };

  const handleMouseLeave = () => {
    setIsVisible(false);
  };

  const handleDismiss = () => {
    setIsVisible(false);
  };

  const handleSuggestionAccept = (suggestion: AISuggestion) => {
    suggestion.action();
    setIsVisible(false);
  };

  return {
    suggestions,
    isLoading,
    isVisible,
    position,
    handleMouseEnter,
    handleMouseLeave,
    handleDismiss,
    handleSuggestionAccept,
  };
}
