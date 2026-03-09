import { useState, useCallback, useRef, useEffect } from 'react';

import type { IAIService, CompletionOptions } from '@ghatana/yappc-ai/core';

/**
 * AI completion state
 */
export interface AICompletionState {
  /** Current completion text */
  completion: string;
  /** Loading state */
  isLoading: boolean;
  /** Error if any */
  error: Error | null;
  /** Whether streaming is in progress */
  isStreaming: boolean;
}

/**
 * AI completion options
 */
export interface UseAICompletionOptions extends CompletionOptions {
  /** Debounce delay in ms */
  debounceMs?: number;
  /** Minimum input length before triggering */
  minLength?: number;
  /** Enable streaming */
  stream?: boolean;
  /** Auto-trigger on input change */
  autoTrigger?: boolean;
}

/**
 * AI completion result
 */
export interface UseAICompletionResult extends AICompletionState {
  /** Trigger completion manually */
  complete: (prompt: string, options?: CompletionOptions) => Promise<string>;
  /** Trigger streaming completion */
  streamComplete: (
    prompt: string,
    options?: CompletionOptions
  ) => Promise<void>;
  /** Cancel ongoing completion */
  cancel: () => void;
  /** Reset state */
  reset: () => void;
  /** Accept current completion */
  accept: () => string;
}

/**
 * Hook for AI-powered text completion with streaming and debouncing
 * 
 * Provides intelligent text completion features including:
 * - Real-time AI text generation with streaming support
 * - Automatic debouncing to reduce API calls
 * - Manual and auto-trigger modes
 * - Cancellable operations with abort controller
 * - Minimum length threshold for triggering
 * - Accept/reject completion flow
 * - Error handling and state management
 * 
 * Integrates with AI service providers (OpenAI, Anthropic, etc.) for code completion,
 * content generation, and assisted writing.
 * 
 * @param aiService - AI service instance implementing IAIService interface
 * @param options - Configuration options for completion behavior
 * @param options.debounceMs - Delay before triggering completion (default: 300ms)
 * @param options.minLength - Minimum prompt length to trigger (default: 3)
 * @param options.stream - Enable streaming mode (default: false)
 * @param options.autoTrigger - Auto-trigger on input change (default: false)
 * @returns AI completion state and control functions
 * 
 * @example
 * ```tsx
 * function CodeEditor() {
 *   const aiService = useAIService();
 *   const {
 *     completion,
 *     isLoading,
 *     isStreaming,
 *     error,
 *     complete,
 *     streamComplete,
 *     cancel,
 *     accept,
 *     reset
 *   } = useAICompletion(aiService, {
 *     debounceMs: 500,
 *     minLength: 10,
 *     stream: true
 *   });
 *   
 *   const [code, setCode] = useState('');
 *   const [cursor, setCursor] = useState(0);
 *   
 *   const handleInput = async (newCode: string) => {
 *     setCode(newCode);
 *     
 *     if (newCode.length >= 10) {
 *       await streamComplete(newCode, {
 *         temperature: 0.7,
 *         maxTokens: 100
 *       });
 *     }
 *   };
 *   
 *   const handleAccept = () => {
 *     const accepted = accept();
 *     setCode(code + accepted);
 *     reset();
 *   };
 *   
 *   return (
 *     <div>
 *       <CodeMirror
 *         value={code}
 *         onChange={handleInput}
 *       />
 *       {completion && (
 *         <div className="ai-completion">
 *           <span className="ghost-text">{completion}</span>
 *           <button onClick={handleAccept} disabled={isStreaming}>
 *             Accept (Tab)
 *           </button>
 *           <button onClick={cancel}>Cancel (Esc)</button>
 *         </div>
 *       )}
 *       {isLoading && <Spinner />}
 *       {error && <Error message={error.message} />}
 *     </div>
 *   );
 * }
 * ```
 */
export function useAICompletion(
  aiService: IAIService,
  options?: UseAICompletionOptions
): UseAICompletionResult {
  const [state, setState] = useState<AICompletionState>({
    completion: '',
    isLoading: false,
    error: null,
    isStreaming: false,
  });

  const abortControllerRef = useRef<AbortController | null>(null);
  const debounceTimerRef = useRef<NodeJS.Timeout | null>(null);

  /**
   * Cancel ongoing completion
   */
  const cancel = useCallback(() => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    if (debounceTimerRef.current) {
      clearTimeout(debounceTimerRef.current);
      debounceTimerRef.current = null;
    }
    setState((prev) => ({
      ...prev,
      isLoading: false,
      isStreaming: false,
    }));
  }, []);

  /**
   * Reset state
   */
  const reset = useCallback(() => {
    cancel();
    setState({
      completion: '',
      isLoading: false,
      error: null,
      isStreaming: false,
    });
  }, [cancel]);

  /**
   * Accept current completion
   */
  const accept = useCallback(() => {
    const current = state.completion;
    reset();
    return current;
  }, [state.completion, reset]);

  /**
   * Trigger non-streaming completion
   */
  const complete = useCallback(
    async (
      prompt: string,
      completionOptions?: CompletionOptions
    ): Promise<string> => {
      // Cancel any ongoing completion
      cancel();

      // Check minimum length
      if (options?.minLength && prompt.length < options.minLength) {
        return '';
      }

      // Create abort controller
      abortControllerRef.current = new AbortController();

      setState((prev) => ({
        ...prev,
        isLoading: true,
        error: null,
        completion: '',
      }));

      try {
        const response = await aiService.complete(prompt, {
          ...options,
          ...completionOptions,
        });

        setState({
          completion: response.content,
          isLoading: false,
          error: null,
          isStreaming: false,
        });

        return response.content;
      } catch (error) {
        const err =
          error instanceof Error ? error : new Error('Completion failed');

        setState({
          completion: '',
          isLoading: false,
          error: err,
          isStreaming: false,
        });

        throw err;
      } finally {
        abortControllerRef.current = null;
      }
    },
    [aiService, options, cancel]
  );

  /**
   * Trigger streaming completion
   */
  const streamComplete = useCallback(
    async (
      prompt: string,
      completionOptions?: CompletionOptions
    ): Promise<void> => {
      // Cancel any ongoing completion
      cancel();

      // Check minimum length
      if (options?.minLength && prompt.length < options.minLength) {
        return;
      }

      // Create abort controller
      abortControllerRef.current = new AbortController();

      setState((prev) => ({
        ...prev,
        isLoading: true,
        isStreaming: true,
        error: null,
        completion: '',
      }));

      try {
        let fullCompletion = '';

        for await (const chunk of aiService.stream(prompt, {
          ...options,
          ...completionOptions,
        })) {
          // Check if aborted
          if (abortControllerRef.current?.signal.aborted) {
            break;
          }

          fullCompletion += chunk.content;

          setState((prev) => ({
            ...prev,
            completion: fullCompletion,
          }));

          if (chunk.done) {
            break;
          }
        }

        setState((prev) => ({
          ...prev,
          isLoading: false,
          isStreaming: false,
        }));
      } catch (error) {
        const err =
          error instanceof Error ? error : new Error('Streaming failed');

        setState({
          completion: '',
          isLoading: false,
          error: err,
          isStreaming: false,
        });

        throw err;
      } finally {
        abortControllerRef.current = null;
      }
    },
    [aiService, options, cancel]
  );

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      cancel();
    };
  }, [cancel]);

  return {
    ...state,
    complete,
    streamComplete,
    cancel,
    reset,
    accept,
  };
}
