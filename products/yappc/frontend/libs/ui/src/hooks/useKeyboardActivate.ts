import { useCallback, type MutableRefObject } from 'react';

/**
 * Hook to provide keyboard activation (Enter/Space) for a focusable element.
 * Accepts an optional ref to the element and returns an onKeyDown handler.
 */
export default function useKeyboardActivate<
  T extends HTMLElement = HTMLElement,
>(
  ref?: MutableRefObject<T | null> | null
): {
  onKeyDown: (e: React.KeyboardEvent<T>) => void;
} {
  const onKeyDown = useCallback(
    (e: React.KeyboardEvent<T>) => {
      if (e.key === 'Enter' || e.key === ' ') {
        const target =
          (ref && 'current' in ref && ref.current) ||
          (e.currentTarget as unknown as T);
        if (
          target &&
          typeof (target as unknown as { click?: unknown }).click === 'function'
        ) {
          (target as unknown as { click: () => void }).click();
        }
      }
    },
    [ref]
  );

  return { onKeyDown };
}
