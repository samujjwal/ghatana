/**
 * Mutation-scoped idempotency keys for user intents.
 *
 * @doc.type hook
 * @doc.purpose Preserve write idempotency keys across retries for the same semantic user intent
 * @doc.layer frontend
 */

import { useCallback, useRef } from 'react';

type IntentPart = unknown;

function stableStringify(value: IntentPart): string {
  if (value === null || typeof value !== 'object') {
    return JSON.stringify(value);
  }

  if (Array.isArray(value)) {
    return `[${value.map((item) => stableStringify(item)).join(',')}]`;
  }

  const record = value as Record<string, unknown>;
  return `{${Object.keys(record)
    .sort()
    .map((key) => `${JSON.stringify(key)}:${stableStringify(record[key])}`)
    .join(',')}}`;
}

function buildIntentKey(namespace: string, parts: readonly IntentPart[]): string {
  return `${namespace}:${parts.map((part) => stableStringify(part)).join('|')}`;
}

export function useIdempotencyKeys(namespace: string): {
  getIdempotencyKey: (parts: readonly IntentPart[]) => string;
  clearIdempotencyKey: (parts: readonly IntentPart[]) => void;
} {
  const keys = useRef<Map<string, string>>(new Map());

  const getIdempotencyKey = useCallback((parts: readonly IntentPart[]) => {
    const intentKey = buildIntentKey(namespace, parts);
    const existing = keys.current.get(intentKey);
    if (existing) {
      return existing;
    }

    const next = crypto.randomUUID();
    keys.current.set(intentKey, next);
    return next;
  }, [namespace]);

  const clearIdempotencyKey = useCallback((parts: readonly IntentPart[]) => {
    keys.current.delete(buildIntentKey(namespace, parts));
  }, [namespace]);

  return { getIdempotencyKey, clearIdempotencyKey };
}
