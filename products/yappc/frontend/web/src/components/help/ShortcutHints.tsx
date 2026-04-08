/**
 * Shortcut Hints Component
 *
 * Displays contextual keyboard shortcut hints based on current context
 * and user learning progress.
 *
 * @doc.type component
 * @doc.purpose Contextual keyboard shortcut hints
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useState, useCallback } from 'react';
import { Keyboard as KeyboardIcon, X as CloseIcon, Lightbulb as TipIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent } from '@ghatana/design-system';
import { useShortcutLearning } from '../../hooks/useShortcutLearning';
import type { Shortcut } from '../../hooks/useShortcutLearning';

// ============================================================================
// Types
// ============================================================================

export interface ShortcutHintsProps {
  shortcuts: Shortcut[];
  context?: string;
  maxHints?: number;
  position?: 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left';
  onDismiss?: () => void;
  onLearn?: (shortcutId: string) => void;
  className?: string;
}

// ============================================================================
// Position Styles
// ============================================================================

const getPositionStyles = (position: ShortcutHintsProps['position']) => {
  switch (position) {
    case 'bottom-right':
      return 'fixed bottom-4 right-4';
    case 'bottom-left':
      return 'fixed bottom-4 left-4';
    case 'top-right':
      return 'fixed top-4 right-4';
    case 'top-left':
      return 'fixed top-4 left-4';
    default:
      return 'fixed bottom-4 right-4';
  }
};

// ============================================================================
// Shortcut Hints Component
// ============================================================================

/**
 * Shortcut Hints Component
 */
export function ShortcutHints({
  shortcuts,
  context,
  maxHints = 3,
  position = 'bottom-right',
  onDismiss,
  onLearn,
  className = '',
}: ShortcutHintsProps): ReactNode {
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());
  const [expanded, setExpanded] = useState(false);

  const {
    getUnlearnedShortcuts,
    markShortcutLearned,
    getShortcutById,
  } = useShortcutLearning({
    shortcuts,
    showHintsAfter: 3,
  });

  const unlearnedShortcuts = getUnlearnedShortcuts(context).slice(0, maxHints);
  const filteredHints = unlearnedShortcuts.filter(s => !dismissedIds.has(s.keys.join('+')));

  const handleDismiss = useCallback((shortcutId: string) => {
    setDismissedIds(prev => new Set(prev).add(shortcutId));
  }, []);

  const handleLearn = useCallback((shortcutId: string) => {
    markShortcutLearned(shortcutId);
    onLearn?.(shortcutId);
    handleDismiss(shortcutId);
  }, [markShortcutLearned, onLearn, handleDismiss]);

  if (filteredHints.length === 0) {
    return null;
  }

  return (
    <div className={`${getPositionStyles(position)} z-40 ${className}`}>
      <Card className="shadow-lg border-2 border-blue-200 dark:border-blue-800 bg-gradient-to-br from-blue-50 to-purple-50 dark:from-blue-900/20 dark:to-purple-900/20">
        <CardContent className="p-4">
          {/* Header */}
          <div className="flex items-center justify-between mb-3">
            <div className="flex items-center gap-2">
              <TipIcon className="w-5 h-5 text-blue-600 dark:text-blue-400" />
              <Typography className="font-bold text-sm text-blue-900 dark:text-blue-100">
                Keyboard Shortcut Tips
              </Typography>
            </div>
            <div className="flex items-center gap-1">
              {onDismiss && (
                <Button
                  size="sm"
                  variant="text"
                  onClick={onDismiss}
                  className="text-gray-400 hover:text-gray-600"
                >
                  <CloseIcon className="w-4 h-4" />
                </Button>
              )}
            </div>
          </div>

          {/* Hints */}
          {expanded ? (
            <div className="space-y-2">
              {unlearnedShortcuts.map((shortcut) => {
                const shortcutId = shortcut.keys.join('+');
                if (dismissedIds.has(shortcutId)) return null;

                return (
                  <div
                    key={shortcutId}
                    className="flex items-center justify-between p-2 bg-white dark:bg-gray-800 rounded-md"
                  >
                    <div className="flex items-center gap-3 flex-1">
                      <div className="flex gap-1">
                        {shortcut.keys.map((key, idx) => (
                          <kbd
                            key={idx}
                            className="px-2 py-1 text-xs font-mono bg-gray-100 dark:bg-gray-700 rounded border border-gray-300 dark:border-gray-600"
                          >
                            {key}
                          </kbd>
                        ))}
                      </div>
                      <Typography className="text-xs text-gray-600 dark:text-gray-400">
                        {shortcut.description}
                      </Typography>
                    </div>
                    <Button
                      size="sm"
                      onClick={() => handleLearn(shortcutId)}
                      className="text-xs"
                    >
                      Got it
                    </Button>
                  </div>
                );
              })}
            </div>
          ) : (
            <div className="space-y-2">
              {filteredHints.map((shortcut) => {
                const shortcutId = shortcut.keys.join('+');

                return (
                  <div
                    key={shortcutId}
                    className="flex items-center justify-between p-2 bg-white dark:bg-gray-800 rounded-md"
                  >
                    <div className="flex items-center gap-3 flex-1">
                      <div className="flex gap-1">
                        {shortcut.keys.map((key, idx) => (
                          <kbd
                            key={idx}
                            className="px-2 py-1 text-xs font-mono bg-gray-100 dark:bg-gray-700 rounded border border-gray-300 dark:border-gray-600"
                          >
                            {key}
                          </kbd>
                        ))}
                      </div>
                      <Typography className="text-xs text-gray-600 dark:text-gray-400">
                        {shortcut.description}
                      </Typography>
                    </div>
                    <div className="flex items-center gap-1">
                      <Button
                        size="sm"
                        variant="text"
                        onClick={() => handleLearn(shortcutId)}
                        className="text-xs text-blue-600 dark:text-blue-400"
                      >
                        Got it
                      </Button>
                      <Button
                        size="sm"
                        variant="text"
                        onClick={() => handleDismiss(shortcutId)}
                        className="text-gray-400 hover:text-gray-600"
                      >
                        <CloseIcon className="w-4 h-4" />
                      </Button>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Expand/Collapse */}
          {unlearnedShortcuts.length > maxHints && (
            <Button
              size="sm"
              variant="text"
              onClick={() => setExpanded(!expanded)}
              className="w-full mt-2 text-xs text-gray-500"
            >
              {expanded ? 'Show less' : `Show ${unlearnedShortcuts.length - maxHints} more tips`}
            </Button>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
