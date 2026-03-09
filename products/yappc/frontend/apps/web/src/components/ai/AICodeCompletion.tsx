import React, { useState, useEffect, useCallback } from 'react';
import { cn } from '@/lib/utils';

/**
 * AI Code Completion component.
 *
 * Copilot-style inline code suggestions.
 * Shows AI-generated code completions as ghost text.
 *
 * @doc.type component
 * @doc.purpose AI-powered code completion
 * @doc.layer ui
 */

export interface CodeCompletion {
  id: string;
  text: string;
  confidence: number;
  startLine: number;
  startColumn: number;
  endLine: number;
  endColumn: number;
}

export interface AICodeCompletionProps {
  completion: CodeCompletion | null;
  onAccept?: (completion: CodeCompletion) => void;
  onReject?: () => void;
  onRequestNext?: () => void;
  className?: string;
}

export function AICodeCompletion({
  completion,
  onAccept,
  onReject,
  onRequestNext,
  className,
}: AICodeCompletionProps) {
  const [isVisible, setIsVisible] = useState(false);

  useEffect(() => {
    setIsVisible(!!completion);
  }, [completion]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (!completion) return;

      // Tab to accept
      if (e.key === 'Tab' && isVisible) {
        e.preventDefault();
        onAccept?.(completion);
        setIsVisible(false);
      }

      // Escape to reject
      if (e.key === 'Escape' && isVisible) {
        e.preventDefault();
        onReject?.();
        setIsVisible(false);
      }

      // Alt+] for next suggestion
      if (e.altKey && e.key === ']' && isVisible) {
        e.preventDefault();
        onRequestNext?.();
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [completion, isVisible, onAccept, onReject, onRequestNext]);

  if (!isVisible || !completion) {
    return null;
  }

  return (
    <div className={cn('relative inline-block', className)}>
      {/* Ghost text suggestion */}
      <span className="text-gray-400 dark:text-gray-600 italic">
        {completion.text}
      </span>

      {/* Hint tooltip */}
      <div className="absolute -top-8 left-0 bg-gray-900 dark:bg-gray-100 text-white dark:text-gray-900 text-xs px-2 py-1 rounded shadow-lg whitespace-nowrap">
        Press{' '}
        <kbd className="px-1 bg-gray-700 dark:bg-gray-300 rounded">Tab</kbd> to
        accept
      </div>
    </div>
  );
}

/**
 * Hook for managing AI code completion.
 *
 * @doc.type hook
 * @doc.purpose AI code completion state management
 */
export function useAICodeCompletion(
  options: {
    enabled?: boolean;
    debounceMs?: number;
    fetchCompletion?: (context: CodeContext) => Promise<CodeCompletion | null>;
  } = {}
) {
  const { enabled = true, debounceMs = 300, fetchCompletion } = options;

  const [completion, setCompletion] = useState<CodeCompletion | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [context, setContext] = useState<CodeContext | null>(null);

  const requestCompletion = useCallback(
    async (newContext: CodeContext) => {
      if (!enabled || !fetchCompletion) return;

      setContext(newContext);
      setIsLoading(true);

      try {
        const result = await fetchCompletion(newContext);
        setCompletion(result);
      } catch (error) {
        console.error('Failed to fetch code completion:', error);
        setCompletion(null);
      } finally {
        setIsLoading(false);
      }
    },
    [enabled, fetchCompletion]
  );

  const debouncedRequestCompletion = useCallback(
    (newContext: CodeContext) => {
      const timer = setTimeout(() => {
        requestCompletion(newContext);
      }, debounceMs);

      return () => clearTimeout(timer);
    },
    [requestCompletion, debounceMs]
  );

  const handleAccept = (acceptedCompletion: CodeCompletion) => {
    console.log('Accepted completion:', acceptedCompletion);
    setCompletion(null);
  };

  const handleReject = () => {
    setCompletion(null);
  };

  const handleRequestNext = () => {
    if (context) {
      requestCompletion(context);
    }
  };

  return {
    completion,
    isLoading,
    requestCompletion: debouncedRequestCompletion,
    handleAccept,
    handleReject,
    handleRequestNext,
  };
}

/**
 * Code context for AI completion.
 */
export interface CodeContext {
  code: string;
  language: string;
  cursorLine: number;
  cursorColumn: number;
  fileName?: string;
}
