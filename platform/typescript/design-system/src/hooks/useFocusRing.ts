import * as React from 'react';

export interface UseFocusRingOptions<T extends HTMLElement> {
  /**
   * Optional focus handler composed with hook.
   */
  onFocus?: React.FocusEventHandler<T>;

  /**
   * Optional blur handler composed with hook.
   */
  onBlur?: React.FocusEventHandler<T>;
}

export interface UseFocusRingResult<T extends HTMLElement> {
  /**
   * Props to spread on the focusable element.
   */
  focusProps: {
    onFocus: React.FocusEventHandler<T>;
    onBlur: React.FocusEventHandler<T>;
  };

  /**
   * Whether the element is currently focused.
   */
  isFocused: boolean;

  /**
   * Whether the focus should be rendered as a visible focus ring.
   */
  isFocusVisible: boolean;
}

/**
 * useFocusRing
 *
 * Accessibly detects focus visibility to drive focus ring styling.
 * Works in both modern browsers (using :focus-visible) and older ones.
 */
export function useFocusRing<T extends HTMLElement>(
  options: UseFocusRingOptions<T> = {}
): UseFocusRingResult<T> {
  const { onFocus, onBlur } = options;
  const [isFocused, setIsFocused] = React.useState(false);
  const [isFocusVisible, setIsFocusVisible] = React.useState(false);

  const handleFocus = React.useCallback<React.FocusEventHandler<T>>(
    (event) => {
      setIsFocused(true);

      // Modern browsers expose :focus-visible via matches
      const target = event.target as HTMLElement;
      if (target && typeof target.matches === 'function') {
        setIsFocusVisible(target.matches(':focus-visible'));
      } else {
        // Fallback for older browsers – assume keyboard focus
        setIsFocusVisible(true);
      }

      onFocus?.(event);
    },
    [onFocus]
  );

  const handleBlur = React.useCallback<React.FocusEventHandler<T>>(
    (event) => {
      setIsFocused(false);
      setIsFocusVisible(false);
      onBlur?.(event);
    },
    [onBlur]
  );

  return {
    focusProps: {
      onFocus: handleFocus,
      onBlur: handleBlur,
    },
    isFocused,
    isFocusVisible,
  };
}
