/**
 * Navigation Context Component
 *
 * Provides contextual navigation hints and progressive disclosure
 * based on user context and experience level.
 *
 * @doc.type component
 * @doc.purpose Contextual navigation hints
 * @doc.layer product
 * @doc.pattern React Component
 */

import React, { ReactNode, createContext, useContext, useState, useCallback } from 'react';
import { Info as InfoIcon, ChevronRight as ArrowIcon, X as CloseIcon } from 'lucide-react';
import { Typography, Button, Chip, Box, Card, CardContent } from '@ghatana/design-system';

// ============================================================================
// Types
// ============================================================================

export interface NavigationHint {
  id: string;
  title: string;
  description: string;
  type: 'tip' | 'warning' | 'info' | 'suggestion';
  priority: 'low' | 'medium' | 'high';
  dismissible?: boolean;
  actionLabel?: string;
  onAction?: () => void;
}

export interface NavigationContextValue {
  hints: NavigationHint[];
  addHint: (hint: NavigationHint) => void;
  removeHint: (id: string) => void;
  clearHints: () => void;
  dismissHint: (id: string) => void;
  isHintVisible: (id: string) => boolean;
}

const NavigationContext = createContext<NavigationContextValue | undefined>(undefined);

// ============================================================================
// Context Provider
// ============================================================================

interface NavigationContextProviderProps {
  children: ReactNode;
  maxHints?: number;
}

export function NavigationContextProvider({
  children,
  maxHints = 3,
}: NavigationContextProviderProps): ReactNode {
  const [hints, setHints] = useState<NavigationHint[]>([]);
  const [dismissedIds, setDismissedIds] = useState<Set<string>>(new Set());

  const addHint = useCallback((hint: NavigationHint) => {
    setHints(prev => {
      // Don't add if already exists
      if (prev.some(h => h.id === hint.id)) return prev;
      
      // Don't add if dismissed
      if (dismissedIds.has(hint.id)) return prev;
      
      // Sort by priority and limit
      const withNew = [...prev, hint];
      const priorityOrder = { high: 3, medium: 2, low: 1 };
      return withNew
        .sort((a, b) => priorityOrder[b.priority] - priorityOrder[a.priority])
        .slice(0, maxHints);
    });
  }, [dismissedIds, maxHints]);

  const removeHint = useCallback((id: string) => {
    setHints(prev => prev.filter(h => h.id !== id));
  }, []);

  const clearHints = useCallback(() => {
    setHints([]);
  }, []);

  const dismissHint = useCallback((id: string) => {
    setDismissedIds(prev => new Set(prev).add(id));
    removeHint(id);
  }, [removeHint]);

  const isHintVisible = useCallback((id: string) => {
    return !dismissedIds.has(id) && hints.some(h => h.id === id);
  }, [dismissedIds, hints]);

  const value: NavigationContextValue = {
    hints,
    addHint,
    removeHint,
    clearHints,
    dismissHint,
    isHintVisible,
  };

  return (
    <NavigationContext.Provider value={value}>
      {children}
    </NavigationContext.Provider>
  );
}

export function useNavigationContext(): NavigationContextValue {
  const context = useContext(NavigationContext);
  if (!context) {
    throw new Error('useNavigationContext must be used within NavigationContextProvider');
  }
  return context;
}

// ============================================================================
// Navigation Hints Display Component
// ============================================================================

interface NavigationHintsDisplayProps {
  position?: 'top' | 'bottom' | 'inline';
  className?: string;
}

export function NavigationHintsDisplay({
  position = 'top',
  className = '',
}: NavigationHintsDisplayProps): ReactNode {
  const { hints, dismissHint } = useNavigationContext();

  if (hints.length === 0) {
    return null;
  }

  const getHintColor = (type: NavigationHint['type']) => {
    switch (type) {
      case 'tip':
        return 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800';
      case 'warning':
        return 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800';
      case 'info':
        return 'bg-purple-50 dark:bg-purple-900/20 border-purple-200 dark:border-purple-800';
      case 'suggestion':
        return 'bg-green-50 dark:bg-green-900/20 border-green-200 dark:border-green-800';
      default:
        return 'bg-gray-50 dark:bg-gray-900/20 border-gray-200 dark:border-gray-800';
    }
  };

  const getHintIcon = (type: NavigationHint['type']) => {
    return <InfoIcon className="w-4 h-4" />;
  };

  return (
    <div className={`space-y-2 ${className}`}>
      {hints.map(hint => (
        <Card
          key={hint.id}
          variant="outlined"
          className={`${getHintColor(hint.type)} border`}
        >
          <CardContent className="p-3">
            <div className="flex items-start gap-3">
              <div className="flex-shrink-0 mt-0.5 text-current">
                {getHintIcon(hint.type)}
              </div>
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-1">
                  <Typography className="font-medium text-sm">
                    {hint.title}
                  </Typography>
                  <Chip
                    size="sm"
                    label={hint.priority}
                    className="text-xs"
                  />
                </div>
                <Typography className="text-xs opacity-80">
                  {hint.description}
                </Typography>
              </div>
              {hint.dismissible !== false && (
                <Button
                  size="sm"
                  variant="text"
                  onClick={() => dismissHint(hint.id)}
                  className="flex-shrink-0 text-current opacity-60 hover:opacity-100"
                >
                  <CloseIcon className="w-4 h-4" />
                </Button>
              )}
            </div>
            {hint.actionLabel && hint.onAction && (
              <Button
                size="sm"
                onClick={hint.onAction}
                endIcon={<ArrowIcon className="w-4 h-4" />}
                className="mt-2 text-xs"
              >
                {hint.actionLabel}
              </Button>
            )}
          </CardContent>
        </Card>
      ))}
    </div>
  );
}
