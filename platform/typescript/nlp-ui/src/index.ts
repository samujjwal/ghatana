/**
 * @ghatana/nlp-ui — NLP UI platform stub.
 *
 * Stub implementation until the full platform package is built.
 *
 * @doc.type module
 * @doc.purpose Natural language query input component and hook
 * @doc.layer platform
 * @doc.pattern Library
 */

import React from 'react';

// ── Types ──────────────────────────────────────────────────────────────────────

export interface NLQInputProps {
  onQuery: (query: string) => void;
  placeholder?: string;
  disabled?: boolean;
  className?: string;
}

export interface ParsedNLQ {
  original: string;
  intent?: string;
  entities: Record<string, string>;
}

export interface UseNLQParseReturn {
  parse: (text: string) => Promise<ParsedNLQ>;
  isLoading: boolean;
  error: Error | null;
}

// ── Components ─────────────────────────────────────────────────────────────────

export const NLQInput: React.FC<NLQInputProps> = ({ onQuery, placeholder, disabled, className }) =>
  React.createElement('input', {
    type: 'text',
    placeholder: placeholder ?? 'Ask a question...',
    disabled,
    className,
    onKeyDown: (e: React.KeyboardEvent<HTMLInputElement>) => {
      if (e.key === 'Enter') {
        onQuery((e.target as HTMLInputElement).value);
      }
    },
  });

// ── Hooks ──────────────────────────────────────────────────────────────────────

export function useNLQParse(): UseNLQParseReturn {
  return {
    parse: async (text: string): Promise<ParsedNLQ> => ({
      original: text,
      entities: {},
    }),
    isLoading: false,
    error: null,
  };
}
