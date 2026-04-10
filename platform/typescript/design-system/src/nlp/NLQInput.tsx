/**
 * NLQInput - Generic Natural Language Query input component
 *
 * @doc.type component
 * @doc.purpose Allow users to query using natural language
 * @doc.layer platform
 * @doc.pattern NLP Component
 */

import React, { useState, useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Send, Sparkles, Loader2 } from 'lucide-react';
import { VoiceInput } from '../voice';
import { useConsent } from '../privacy';

/**
 * NLP parse response
 */
export interface NLPResponse {
  intent: string;
  entities: Record<string, string>;
  confidence: number;
  query: string;
}

/**
 * NLQ input props
 */
export interface NLQInputProps {
  onQuery: (query: string, intent?: NLPResponse) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  enableVoice?: boolean;
}

/**
 * Use NLQ parse hook
 */
export function useNLQParse(): ReturnType<typeof useMutation<NLPResponse, Error, string>> {
  return useMutation<NLPResponse, Error, string>({
    mutationFn: async (query: string) => {
      const response = await fetch('/api/v1/nlp/parse', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query }),
      });

      if (!response.ok) {
        throw new Error('Failed to parse query');
      }

      return (await response.json()) as NLPResponse;
    },
  });
}

/**
 * NLQ Input Component
 */
export function NLQInput({
  onQuery,
  placeholder = 'Ask a question...',
  disabled = false,
  className = '',
  enableVoice = true,
}: NLQInputProps): React.ReactElement {
  const [query, setQuery] = useState('');
  const { consentGranted } = useConsent('voice_processing');
  const { mutate, isPending } = useNLQParse();

  const handleSubmit = useCallback(
    (e: React.FormEvent<HTMLFormElement>): void => {
      e.preventDefault();
      if (query.trim() && !disabled) {
        mutate(query, {
          onSuccess: (response) => {
            onQuery(query, response);
          },
          onError: () => {
            onQuery(query); // Fallback to raw query
          },
        });
      }
    },
    [query, disabled, mutate, onQuery],
  );

  const handleVoiceTranscript = useCallback((transcript: string): void => {
    setQuery(transcript);
  }, []);

  return (
    <div className={`w-full max-w-2xl ${className}`}>
      <form onSubmit={handleSubmit} className="flex space-x-2">
        <div className="flex-1 relative">
          <input
            type="text"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={placeholder}
            disabled={disabled}
            className="w-full px-4 py-2 pr-10 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500"
          />

          {query && (
            <button
              type="button"
              onClick={() => setQuery('')}
              className="absolute right-2 top-1/2 transform -translate-y-1/2 text-gray-400 hover:text-gray-600"
            >
              ×
            </button>
          )}
        </div>

        <button
          type="submit"
          disabled={!query.trim() || disabled || isPending}
          aria-label={isPending ? 'Submitting...' : 'Submit'}
          className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed flex items-center space-x-2"
        >
          {isPending ? (
            <Loader2 size={16} className="animate-spin" aria-label="Loading" />
          ) : (
            <Send size={16} />
          )}
          <span>Submit</span>
        </button>
      </form>

      {enableVoice && consentGranted && (
        <div className="mt-2">
          <VoiceInput
            onTranscript={handleVoiceTranscript}
            placeholder="Or use voice input"
            disabled={disabled}
          />
        </div>
      )}

      {query && (
        <div className="mt-2 text-sm text-gray-600 flex items-center space-x-1">
          <Sparkles size={14} />
          <span>AI will help interpret your query</span>
        </div>
      )}
    </div>
  );
}
