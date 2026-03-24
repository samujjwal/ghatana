import { useCallback, useEffect, useRef, useState } from 'react';

import { fetchAllSuggestions } from './SmartSuggestions/utils';

import type { Suggestion, SuggestionType } from './SmartSuggestions/types';

/**
 * Hook options for fetching AI suggestions.
 *
 * This interface describes the inputs used by `useSuggestions`. Keep types
 * lightweight to avoid creating cross-package type reference cycles.
 */
export interface UseSuggestionsOptions {
  aiService: unknown;
  types: SuggestionType[];
  context: string;
  selection?: string;
  maxPerType?: number;
  minConfidence?: number;
  completionOptions?: Record<string, unknown>;
  autoGenerate?: boolean;
}

/**
 * React hook that fetches AI-generated suggestions for given types and context.
 *
 * Returns current suggestions, loading/error state, and controls to refresh or
 * generate suggestions on demand. Designed to be small and testable.
 */
export function useSuggestions(options: UseSuggestionsOptions) {
  const {
    aiService,
    types,
    context,
    selection = '',
    maxPerType = 3,
    minConfidence = 0.5,
    completionOptions = {},
    autoGenerate = true,
  } = options;

  const [suggestions, setSuggestions] = useState<Suggestion[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const inFlightRef = useRef(false);
  const mountedRef = useRef(true);

  useEffect(() => {
    mountedRef.current = true;
    return () => {
      mountedRef.current = false;
    };
  }, []);

  const generate = useCallback(async () => {
    if (inFlightRef.current) return;
    if (!context && !selection) {
      if (mountedRef.current) setError('No context or selection provided');
      return;
    }

    inFlightRef.current = true;
    if (mountedRef.current) {
      setIsLoading(true);
      setError(null);
    }

    try {
      const results = await fetchAllSuggestions(
        aiService,
        types,
        context,
        selection,
        maxPerType,
        minConfidence,
        completionOptions
      );
      if (mountedRef.current) setSuggestions(results);
    } catch (err) {
      if (mountedRef.current)
        setError(
          err instanceof Error ? err.message : 'Failed to fetch suggestions'
        );
    } finally {
      inFlightRef.current = false;
      if (mountedRef.current) setIsLoading(false);
    }
  }, [
    aiService,
    types,
    context,
    selection,
    maxPerType,
    minConfidence,
    completionOptions,
  ]);

  useEffect(() => {
    if (!autoGenerate) return;

    // If there's no context/selection, set error synchronously so callers (tests)
    // that expect immediate feedback can observe it without awaiting async tasks.
    if (!context && !selection) {
      setError('No context or selection provided');
      setSuggestions([]);
      setIsLoading(false);
      return;
    }

    // show loading immediately, then perform generation asynchronously
    setIsLoading(true);
    queueMicrotask(() => {
      void generate();
    });
  }, [autoGenerate, generate, context, selection]);

  const refresh = useCallback(() => {
    void generate();
  }, [generate]);

  return {
    suggestions,
    isLoading,
    error,
    refresh,
    generate,
    setSuggestions,
  } as const;
}
