/**
 * NLQInput — Natural Language Query input component.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/nlp-ui
 * after validation in AEP and Data Cloud.
 *
 * @doc.type component
 * @doc.purpose Allow users to query using natural language
 * @doc.layer frontend
 * @doc.pattern NLP Component
 */

import React, { useState, useCallback } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Send, Sparkles, Loader2 } from 'lucide-react';
import { VoiceInput } from '../voice/VoiceInput';
import { ConsentManager, useConsent } from '../privacy/ConsentManager';
import { parseNlQuery, type NlqParseResult } from '@/api/aep.api';

/**
 * NLP parse response schema
 */
const NLP_PARSE_RESPONSE_SCHEMA = {
  intent: 'string',
  entities: 'array',
  confidence: 'number',
  query: 'string',
} as const;

/**
 * NLP parse response type
 */
export interface NLPParseResponse {
  intent: string;
  entities: Array<{ type: string; value: string; confidence: number }>;
  confidence: number;
  query: string;
}

/**
 * NLQInput component props
 */
interface NLQInputProps {
  onQuery: (query: string, intent?: string, entities?: Array<{ type: string; value: string; confidence: number }>) => void;
  placeholder?: string;
  className?: string;
  disabled?: boolean;
  /**
   * Optional: Custom endpoint for NLP parsing
   */
  endpoint?: string;
  /**
   * Optional: Minimum confidence threshold to use intent
   */
  confidenceThreshold?: number;
  /**
   * Optional: Show confidence score
   */
  showConfidence?: boolean;
  /**
   * Optional: Callback when NLP parsing fails
   */
  onParseError?: (error: Error) => void;
}

/**
 * NLQInput component
 *
 * Provides natural language query input with voice support and NLP
 * intent parsing. Parses user queries to extract intent and entities,
 * then invokes the onQuery callback with the parsed results.
 */
export const NLQInput: React.FC<NLQInputProps> = ({
  onQuery,
  placeholder = 'Ask anything...',
  className = '',
  disabled = false,
  endpoint = '/api/v1/nlp/parse',
  confidenceThreshold = 0.5,
  showConfidence = false,
  onParseError,
}) => {
  const [query, setQuery] = useState('');
  const { consentGranted } = useConsent('ai_suggestions');
  
  const { mutate: parseIntent, isPending } = useMutation({
    mutationFn: (text: string): Promise<NlqParseResult> => parseNlQuery(text),
    onSuccess: (data) => {
      if (data.confidence >= confidenceThreshold) {
        onQuery(query, data.intent, data.entities as Array<{ type: string; value: string; confidence: number }>);
      } else {
        onQuery(query);
      }
    },
    onError: (error) => {
      onQuery(query);
      if (onParseError) {
        onParseError(error instanceof Error ? error : new Error('NLP parse failed'));
      }
    },
  });

  const handleSubmit = useCallback(() => {
    if (!query.trim() || isPending) return;

    // If AI suggestions consent not granted, use raw query
    if (!consentGranted) {
      onQuery(query);
      return;
    }

    // Try to parse intent
    parseIntent(query);
  }, [query, isPending, consentGranted, onQuery, parseIntent, confidenceThreshold]);

  const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  }, [handleSubmit]);

  return (
    <div className={`flex gap-2 ${className}`} onKeyDown={handleKeyDown}>
      <div className="flex-1 relative">
        <VoiceInput
          value={query}
          onChange={setQuery}
          placeholder={placeholder}
          disabled={disabled}
          className="w-full"
        />
        {query && (
          <div className="absolute right-10 top-1/2 -translate-y-1/2">
            {isPending ? (
              <Loader2 className="h-4 w-4 text-gray-400 animate-spin" />
            ) : (
              <Sparkles className="h-4 w-4 text-indigo-400" />
            )}
          </div>
        )}
      </div>
      <button
        type="button"
        onClick={handleSubmit}
        disabled={disabled || isPending || !query.trim()}
        className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors flex items-center gap-2"
        aria-label="Submit query"
      >
        {isPending ? (
          <>
            <Loader2 className="h-4 w-4 animate-spin" />
            <span className="sr-only">Processing</span>
          </>
        ) : (
          <>
            <Send className="h-4 w-4" />
            <span className="sr-only">Submit</span>
          </>
        )}
      </button>
    </div>
  );
};

/**
 * Hook for using NLQ parsing directly
 */
export function useNLQParse() {
  const { mutateAsync: parseIntent, isPending } = useMutation({
    mutationFn: (text: string): Promise<NlqParseResult> => parseNlQuery(text),
  });

  return {
    parse: parseIntent,
    isPending,
  };
}
