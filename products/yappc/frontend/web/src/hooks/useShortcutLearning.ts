/**
 * Shortcut Learning Hook
 *
 * Tracks which keyboard shortcuts the user has learned and provides
 * contextual hints for unlearned shortcuts.
 *
 * @doc.type hook
 * @doc.purpose Keyboard shortcut learning system
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface Shortcut {
  keys: string[];
  description: string;
  category: string;
  context?: string; // Where this shortcut is relevant
  priority?: 'essential' | 'helpful' | 'optional';
}

export interface LearnedShortcut {
  shortcutId: string;
  learnedAt: number;
  usageCount: number;
}

export interface UseShortcutLearningOptions {
  shortcuts: Shortcut[];
  storageKey?: string;
  showHintsAfter?: number; // Show hints after N interactions
}

export interface UseShortcutLearningResult {
  learnedShortcuts: Set<string>;
  isShortcutLearned: (shortcutId: string) => boolean;
  markShortcutLearned: (shortcutId: string) => void;
  incrementUsage: (shortcutId: string) => void;
  getUnlearnedShortcuts: (context?: string) => Shortcut[];
  getShortcutHint: (shortcutId: string) => string | null;
  resetLearning: () => void;
  getShortcutById: (shortcutId: string) => Shortcut | undefined;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useShortcutLearning({
  shortcuts,
  storageKey = 'shortcut-learning',
  showHintsAfter = 3,
}: UseShortcutLearningOptions): UseShortcutLearningResult {
  const [learnedShortcuts, setLearnedShortcuts] = useState<Set<string>>(new Set());
  const [usageCounts, setUsageCounts] = useState<Record<string, number>>({});

  // Load learned shortcuts from localStorage
  useEffect(() => {
    try {
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        const data = JSON.parse(stored);
        setLearnedShortcuts(new Set(data.learned || []));
        setUsageCounts(data.usage || {});
      }
    } catch {
      // Ignore storage errors
    }
  }, [storageKey]);

  // Persist learned shortcuts to localStorage
  useEffect(() => {
    try {
      const data = {
        learned: Array.from(learnedShortcuts),
        usage: usageCounts,
      };
      localStorage.setItem(storageKey, JSON.stringify(data));
    } catch {
      // Ignore storage errors
    }
  }, [learnedShortcuts, usageCounts, storageKey]);

  // Generate shortcut ID from keys
  const getShortcutId = useCallback((shortcut: Shortcut): string => {
    return shortcut.keys.join('+');
  }, []);

  // Check if a shortcut is learned
  const isShortcutLearned = useCallback(
    (shortcutId: string): boolean => {
      return learnedShortcuts.has(shortcutId);
    },
    [learnedShortcuts]
  );

  // Mark a shortcut as learned
  const markShortcutLearned = useCallback((shortcutId: string) => {
    setLearnedShortcuts(prev => new Set(prev).add(shortcutId));
  }, []);

  // Increment usage count for a shortcut
  const incrementUsage = useCallback((shortcutId: string) => {
    setUsageCounts(prev => ({
      ...prev,
      [shortcutId]: (prev[shortcutId] || 0) + 1,
    }));
  }, []);

  // Get unlearned shortcuts for a given context
  const getUnlearnedShortcuts = useCallback(
    (context?: string): Shortcut[] => {
      return shortcuts
        .filter(shortcut => {
          const shortcutId = getShortcutId(shortcut);
          
          // Skip if already learned
          if (isShortcutLearned(shortcutId)) return false;
          
          // Filter by context if provided
          if (context && shortcut.context && shortcut.context !== context) {
            return false;
          }
          
          // Show hints after threshold
          const usageCount = usageCounts[shortcutId] || 0;
          return usageCount >= showHintsAfter;
        })
        .sort((a, b) => {
          // Sort by priority
          const priorityOrder = { essential: 3, helpful: 2, optional: 1 };
          return priorityOrder[b.priority || 'optional'] - priorityOrder[a.priority || 'optional'];
        });
    },
    [shortcuts, getShortcutId, isShortcutLearned, usageCounts, showHintsAfter]
  );

  // Get hint for a specific shortcut
  const getShortcutHint = useCallback((shortcutId: string): string | null => {
    const shortcut = shortcuts.find(s => getShortcutId(s) === shortcutId);
    if (!shortcut || isShortcutLearned(shortcutId)) return null;

    const usageCount = usageCounts[shortcutId] || 0;
    if (usageCount < showHintsAfter) return null;

    return `Tip: ${shortcut.description} (${shortcut.keys.join(' + ')})`;
  }, [shortcuts, getShortcutId, isShortcutLearned, usageCounts, showHintsAfter]);

  // Reset all learning data
  const resetLearning = useCallback(() => {
    setLearnedShortcuts(new Set());
    setUsageCounts({});
  }, []);

  // Get shortcut by ID
  const getShortcutById = useCallback(
    (shortcutId: string): Shortcut | undefined => {
      return shortcuts.find(s => getShortcutId(s) === shortcutId);
    },
    [shortcuts, getShortcutId]
  );

  return {
    learnedShortcuts,
    isShortcutLearned,
    markShortcutLearned,
    incrementUsage,
    getUnlearnedShortcuts,
    getShortcutHint,
    resetLearning,
    getShortcutById,
  };
}
