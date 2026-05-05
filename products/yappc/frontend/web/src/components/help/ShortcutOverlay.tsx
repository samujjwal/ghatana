/**
 * Shortcut Overlay Component
 *
 * Displays an overlay showing keyboard shortcuts when the user
 * presses a key, helping them learn shortcuts through discovery.
 *
 * @doc.type component
 * @doc.purpose Keyboard shortcut discovery overlay
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, useEffect, useState, useCallback } from 'react';
import { Keyboard as KeyboardIcon, X as CloseIcon } from 'lucide-react';
import { Typography, Button, Box, Card, CardContent, Chip } from '@ghatana/design-system';
import { useShortcutLearning } from '../../hooks/useShortcutLearning';
import type { Shortcut } from '../../hooks/useShortcutLearning';

// ============================================================================
// Types
// ============================================================================

export interface ShortcutOverlayProps {
  shortcuts: Shortcut[];
  context?: string;
  triggerKey?: string; // Key that triggers the overlay (e.g., '?')
  duration?: number; // How long to show the overlay (ms)
  onDismiss?: () => void;
  className?: string;
}

// ============================================================================
// Shortcut Overlay Component
// ============================================================================

/**
 * Shortcut Overlay Component
 */
export function ShortcutOverlay({
  shortcuts,
  context,
  triggerKey = '?',
  duration = 3000,
  onDismiss,
  className = '',
}: ShortcutOverlayProps): ReactNode {
  const [visible, setVisible] = useState(false);
  const [activeShortcut, setActiveShortcut] = useState<Shortcut | null>(null);

  const {
    getShortcutById,
    markShortcutLearned,
    incrementUsage,
  } = useShortcutLearning({
    shortcuts,
    showHintsAfter: 3,
  });

  // Listen for key press to show overlay
  useEffect(() => {
    const handleKeyPress = (e: KeyboardEvent) => {
      // Check if trigger key was pressed
      if (e.key === triggerKey) {
        e.preventDefault();
        
        // Find context-relevant shortcuts
        const contextShortcuts = context
          ? shortcuts.filter(s => s.context === context)
          : shortcuts;

        if (contextShortcuts.length > 0) {
          setActiveShortcut(contextShortcuts[0]);
          setVisible(true);

          // Auto-hide after duration
          const timer = setTimeout(() => {
            setVisible(false);
          }, duration);

          return () => clearTimeout(timer);
        }
      }

      // Track usage of shortcuts
      const pressedKey = e.key.toLowerCase();
      const matchingShortcut = shortcuts.find(s =>
        s.keys.some(k => k.toLowerCase() === pressedKey)
      );

      if (matchingShortcut) {
        incrementUsage(matchingShortcut.keys.join('+'));
      }
    };

    window.addEventListener('keydown', handleKeyPress);
    return () => window.removeEventListener('keydown', handleKeyPress);
  }, [shortcuts, context, triggerKey, duration, incrementUsage]);

  const handleDismiss = useCallback(() => {
    setVisible(false);
    if (activeShortcut) {
      markShortcutLearned(activeShortcut.keys.join('+'));
    }
    onDismiss?.();
  }, [activeShortcut, markShortcutLearned, onDismiss]);

  if (!visible || !activeShortcut) {
    return null;
  }

  return (
    <div className={`fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm ${className}`}>
      <Card className="max-w-md w-full mx-4 shadow-2xl">
        <CardContent className="p-6">
          {/* Header */}
          <div className="flex items-center justify-between mb-4">
            <div className="flex items-center gap-2">
              <KeyboardIcon className="w-6 h-6 text-info-color dark:text-info-color" />
              <Typography className="font-bold text-lg">
                Keyboard Shortcut
              </Typography>
            </div>
            <Button
              size="sm"
              variant="text"
              onClick={handleDismiss}
              className="text-fg-muted hover:text-fg-muted"
            >
              <CloseIcon className="w-5 h-5" />
            </Button>
          </div>

          {/* Shortcut Display */}
          <div className="bg-surface-muted dark:bg-surface rounded-lg p-4 mb-4">
            <div className="flex items-center justify-center gap-2 mb-2">
              {activeShortcut.keys.map((key, idx) => (
                <kbd
                  key={idx}
                  className="px-4 py-2 text-lg font-mono bg-white dark:bg-surface-muted rounded-lg border-2 border-border dark:border-border shadow-sm"
                >
                  {key}
                </kbd>
              ))}
            </div>
            <Typography className="text-center text-sm text-fg-muted dark:text-fg-muted">
              {activeShortcut.description}
            </Typography>
          </div>

          {/* Category */}
          <div className="flex items-center gap-2 mb-4">
            <Chip
              size="sm"
              label={activeShortcut.category}
              className="text-xs"
            />
            {activeShortcut.context && (
              <Chip
                size="sm"
                label={activeShortcut.context}
                variant="outlined"
                className="text-xs"
              />
            )}
          </div>

          {/* Action */}
          <Button
            size="sm"
            onClick={handleDismiss}
            className="w-full"
          >
            Got it, I'll remember this
          </Button>
        </CardContent>
      </Card>
    </div>
  );
}
