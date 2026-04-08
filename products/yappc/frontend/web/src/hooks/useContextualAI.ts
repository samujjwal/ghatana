/**
 * Contextual AI Hook
 *
 * Provides AI-powered contextual suggestions and next-best-action recommendations
 * based on current user context, actions, and workflow state.
 *
 * @doc.type hook
 * @doc.purpose Contextual AI suggestions and next-best-action
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useCallback, useEffect, useMemo } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { ContextualSuggestion } from '../components/ai/ContextualSuggestions';
import type { NextAction } from '../components/ai/NextBestAction';

// ============================================================================
// Types
// ============================================================================

export interface ContextualAIContext {
  userId?: string;
  projectId?: string;
  currentPath?: string;
  currentAction?: string;
  recentActions?: string[];
  metadata?: Record<string, unknown>;
}

export interface UseContextualAIOptions {
  context: ContextualAIContext;
  enabled?: boolean;
  debounceMs?: number;
}

export interface UseContextualAIResult {
  suggestions: ContextualSuggestion[];
  nextAction: NextAction | null;
  isLoading: boolean;
  error: Error | null;
  refresh: () => void;
  dismissSuggestion: (id: string) => void;
  dismissNextAction: () => void;
  acceptSuggestion: (id: string) => void;
  acceptNextAction: () => void;
}

// ============================================================================
// Mock AI Service (to be replaced with real AI endpoint)
// ============================================================================

async function generateContextualSuggestions(
  context: ContextualAIContext
): Promise<ContextualSuggestion[]> {
  // Mock implementation - replace with real AI service call
  const suggestions: ContextualSuggestion[] = [];

  // Context-aware suggestions based on current path
  if (context.currentPath?.includes('dashboard')) {
    suggestions.push({
      id: 'sugg-1',
      type: 'action',
      title: 'Review pending tasks',
      description: 'You have 3 high-priority tasks awaiting review',
      actionLabel: 'View Tasks',
      confidence: 0.85,
      priority: 'high',
      context: 'Dashboard view with pending tasks',
      onAction: () => console.log('Navigate to tasks'),
    });
  }

  if (context.currentPath?.includes('canvas')) {
    suggestions.push({
      id: 'sugg-2',
      type: 'info',
      title: 'Consider adding a connection',
      description: 'These nodes could benefit from a data flow connection',
      actionLabel: 'Add Connection',
      confidence: 0.72,
      priority: 'medium',
      context: 'Canvas with unconnected nodes',
      onAction: () => console.log('Add connection'),
    });
  }

  // Recent action-based suggestions
  if (context.recentActions?.includes('create-project')) {
    suggestions.push({
      id: 'sugg-3',
      type: 'success',
      title: 'Project created successfully',
      description: 'Consider adding team members to your new project',
      actionLabel: 'Add Members',
      confidence: 0.9,
      priority: 'medium',
      context: 'After project creation',
      onAction: () => console.log('Add team members'),
    });
  }

  return suggestions;
}

async function generateNextAction(
  context: ContextualAIContext
): Promise<NextAction | null> {
  // Mock implementation - replace with real AI service call
  if (context.currentPath?.includes('dashboard')) {
    return {
      id: 'action-1',
      title: 'Approve pending task',
      description: 'Task "Implement authentication" is ready for approval',
      type: 'immediate',
      impact: 'high',
      estimatedTime: '2 min',
      onAction: () => console.log('Approve task'),
      onDismiss: () => console.log('Dismiss'),
    };
  }

  if (context.currentPath?.includes('canvas')) {
    return {
      id: 'action-2',
      title: 'Validate canvas structure',
      description: 'Run validation to ensure all connections are valid',
      type: 'recommended',
      impact: 'medium',
      estimatedTime: '5 min',
      onAction: () => console.log('Validate canvas'),
      onDismiss: () => console.log('Dismiss'),
    };
  }

  return null;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useContextualAI({
  context,
  enabled = true,
  debounceMs = 500,
}: UseContextualAIOptions): UseContextualAIResult {
  const queryClient = useQueryClient();
  const [dismissedSuggestions, setDismissedSuggestions] = useState<Set<string>>(new Set());
  const [dismissedNextAction, setDismissedNextAction] = useState(false);

  // Query for suggestions
  const {
    data: suggestions = [],
    isLoading: isLoadingSuggestions,
    error: suggestionsError,
    refetch: refetchSuggestions,
  } = useQuery({
    queryKey: ['contextual-suggestions', context],
    queryFn: () => generateContextualSuggestions(context),
    enabled,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  // Query for next action
  const {
    data: nextAction,
    isLoading: isLoadingNextAction,
    error: nextActionError,
    refetch: refetchNextAction,
  } = useQuery({
    queryKey: ['next-action', context],
    queryFn: () => generateNextAction(context),
    enabled,
    staleTime: 1 * 60 * 1000, // 1 minute
  });

  // Filter out dismissed suggestions
  const filteredSuggestions = useMemo(
    () => suggestions.filter(s => !dismissedSuggestions.has(s.id)),
    [suggestions, dismissedSuggestions]
  );

  // Dismiss suggestion
  const dismissSuggestion = useCallback((id: string) => {
    setDismissedSuggestions(prev => new Set(prev).add(id));
  }, []);

  // Dismiss next action
  const dismissNextActionHandler = useCallback(() => {
    setDismissedNextAction(true);
  }, []);

  // Accept suggestion
  const acceptSuggestion = useCallback((id: string) => {
    const suggestion = suggestions.find(s => s.id === id);
    if (suggestion?.onAction) {
      suggestion.onAction();
      dismissSuggestion(id);
    }
  }, [suggestions, dismissSuggestion]);

  // Accept next action
  const acceptNextAction = useCallback(() => {
    if (nextAction?.onAction) {
      nextAction.onAction();
      dismissNextActionHandler();
    }
  }, [nextAction, dismissNextActionHandler]);

  // Refresh all
  const refresh = useCallback(() => {
    refetchSuggestions();
    refetchNextAction();
  }, [refetchSuggestions, refetchNextAction]);

  // Auto-refresh on context change
  useEffect(() => {
    if (enabled) {
      const timer = setTimeout(() => {
        refresh();
      }, debounceMs);

      return () => clearTimeout(timer);
    }
  }, [context, enabled, debounceMs, refresh]);

  return {
    suggestions: filteredSuggestions,
    nextAction: dismissedNextAction ? null : (nextAction ?? null),
    isLoading: isLoadingSuggestions || isLoadingNextAction,
    error: suggestionsError || nextActionError,
    refresh,
    dismissSuggestion,
    dismissNextAction: dismissNextActionHandler,
    acceptSuggestion,
    acceptNextAction,
  };
}
