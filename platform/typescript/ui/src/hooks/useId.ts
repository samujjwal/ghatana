import * as React from 'react';

let globalId = 0;

/**
 * Generates a deterministic unique id on both server and client.
 * Uses React.useId when available and falls back to an incrementing counter.
 */
export function useId(prefix = 'gh-id'): string {
  const reactId = (typeof React.useId === 'function') ? React.useId() : undefined;

  const [fallbackId] = React.useState(() => {
    globalId += 1;
    return `${prefix}-${globalId}`;
  });

  return reactId ?? fallbackId;
}
