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
import { useQuery, useMutation } from '@tanstack/react-query';
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
// Phase inference
// ============================================================================

type SuggestionPhase =
  | 'INTENT'
  | 'SHAPE'
  | 'VALIDATE'
  | 'GENERATE'
  | 'RUN'
  | 'OBSERVE'
  | 'LEARN'
  | 'EVOLVE';

function inferPhase(currentPath: string | undefined): SuggestionPhase {
  if (!currentPath) return 'INTENT';
  if (currentPath.includes('/shape')) return 'SHAPE';
  if (currentPath.includes('/validate')) return 'VALIDATE';
  if (currentPath.includes('/generate')) return 'GENERATE';
  if (currentPath.includes('/run')) return 'RUN';
  if (currentPath.includes('/observe')) return 'OBSERVE';
  if (currentPath.includes('/learn')) return 'LEARN';
  if (currentPath.includes('/evolve')) return 'EVOLVE';
  return 'INTENT';
}

// ============================================================================
// API response types (mirrors openapi.ts Suggestion schema)
// ============================================================================

interface ApiSuggestion {
  id?: string;
  projectId?: string;
  phase?: string;
  type?: 'REQUIREMENT' | 'DESIGN' | 'TEST' | 'RISK' | 'ACTION';
  text?: string;
  generatedAt?: string;
}

interface SuggestionsApiResult {
  suggestions: ContextualSuggestion[];
  nextAction: NextAction | null;
}

// ============================================================================
// Mapping helpers
// ============================================================================

function mapSuggestionType(type: ApiSuggestion['type']): ContextualSuggestion['type'] {
  switch (type) {
    case 'ACTION':
    case 'DESIGN':
      return 'action';
    case 'RISK':
    case 'TEST':
      return 'warning';
    default:
      return 'info';
  }
}

function mapSuggestionPriority(type: ApiSuggestion['type']): ContextualSuggestion['priority'] {
  return type === 'RISK' || type === 'ACTION' ? 'high' : 'medium';
}

function extractTitle(text: string, maxLen = 80): string {
  const dot = text.indexOf('.');
  const candidate = dot > 0 && dot < maxLen ? text.slice(0, dot) : text.slice(0, maxLen);
  return candidate.trim() || 'AI Suggestion';
}

function apiSuggestionToContextual(s: ApiSuggestion): ContextualSuggestion {
  const text = s.text ?? '';
  const dot = text.indexOf('.');
  const title = extractTitle(text);
  const description = dot > 0 ? text.slice(dot + 1).trim() : text;
  return {
    id: s.id ?? crypto.randomUUID(),
    type: mapSuggestionType(s.type),
    title,
    description: description || text,
    confidence: 0.75,
    priority: mapSuggestionPriority(s.type),
    context: s.phase ?? '',
  };
}

function apiSuggestionToNextAction(s: ApiSuggestion): NextAction {
  const text = s.text ?? '';
  const dot = text.indexOf('.');
  const title = extractTitle(text, 80);
  const description = dot > 0 ? text.slice(dot + 1).trim() : text;
  return {
    id: s.id ?? crypto.randomUUID(),
    title,
    description: description || text,
    type: 'recommended',
    impact: 'medium',
    onAction: () => { /* action execution delegated to the UI layer */ },
    metadata: { phase: s.phase, generatedAt: s.generatedAt },
  };
}

// ============================================================================
// Real API calls
// ============================================================================

async function fetchAISuggestions(
  projectId: string,
  context: ContextualAIContext
): Promise<SuggestionsApiResult> {
  const phase = inferPhase(context.currentPath);
  const response = await fetch(
    `/api/v1/projects/${encodeURIComponent(projectId)}/suggestions`,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        phase,
        context: {
          currentPath: context.currentPath,
          currentAction: context.currentAction,
          recentActions: context.recentActions,
          ...context.metadata,
        },
      }),
    }
  );
  if (!response.ok) {
    throw new Error(`Suggestions API responded with ${response.status}`);
  }
  const raw: unknown = await response.json();
  const items: ApiSuggestion[] = Array.isArray(raw) ? (raw as ApiSuggestion[]) : [];

  const suggestions = items
    .filter((s) => s.type !== 'ACTION')
    .map(apiSuggestionToContextual);

  const actionItem = items.find((s) => s.type === 'ACTION');
  const nextAction = actionItem != null ? apiSuggestionToNextAction(actionItem) : null;

  return { suggestions, nextAction };
}

async function callDismissSuggestionApi(
  projectId: string,
  suggestionId: string
): Promise<void> {
  const response = await fetch(
    `/api/v1/projects/${encodeURIComponent(projectId)}/suggestions/${encodeURIComponent(suggestionId)}`,
    { method: 'DELETE' }
  );
  // 404 is acceptable — suggestion may have already been removed server-side
  if (!response.ok && response.status !== 404) {
    throw new Error(`Dismiss API responded with ${response.status}`);
  }
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useContextualAI({
  context,
  enabled = true,
  debounceMs = 500,
}: UseContextualAIOptions): UseContextualAIResult {
  const [dismissedSuggestions, setDismissedSuggestions] = useState<Set<string>>(new Set());
  const [dismissedNextAction, setDismissedNextAction] = useState(false);

  const projectId = context.projectId;
  const isQueryEnabled = enabled && !!projectId;

  // Single query for both suggestions and next action
  const {
    data,
    isLoading,
    error,
    refetch,
  } = useQuery({
    queryKey: ['ai-suggestions', projectId, context.currentPath, context.currentAction],
    queryFn: () => fetchAISuggestions(projectId!, context),
    enabled: isQueryEnabled,
    staleTime: 2 * 60 * 1000, // 2 minutes
  });

  const suggestions = data?.suggestions ?? [];
  const nextAction = data?.nextAction ?? null;

  // Mutation for server-side dismissal
  const dismissMutation = useMutation({
    mutationFn: ({ suggestionId }: { suggestionId: string }) =>
      projectId != null
        ? callDismissSuggestionApi(projectId, suggestionId)
        : Promise.resolve(),
  });

  // Filter out dismissed suggestions
  const filteredSuggestions = useMemo(
    () => suggestions.filter(s => !dismissedSuggestions.has(s.id)),
    [suggestions, dismissedSuggestions]
  );

  // Dismiss suggestion
  const dismissSuggestion = useCallback((id: string) => {
    setDismissedSuggestions(prev => new Set(prev).add(id));
    dismissMutation.mutate({ suggestionId: id });
  }, [dismissMutation]);

  // Dismiss next action
  const dismissNextActionHandler = useCallback(() => {
    setDismissedNextAction(true);
  }, []);

  // Accept suggestion
  const acceptSuggestion = useCallback((id: string) => {
    const suggestion = suggestions.find(s => s.id === id);
    if (suggestion?.onAction) {
      suggestion.onAction();
    }
    dismissSuggestion(id);
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
    refetch();
  }, [refetch]);

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
    isLoading,
    error: error instanceof Error ? error : null,
    refresh,
    dismissSuggestion,
    dismissNextAction: dismissNextActionHandler,
    acceptSuggestion,
    acceptNextAction,
  };
}
