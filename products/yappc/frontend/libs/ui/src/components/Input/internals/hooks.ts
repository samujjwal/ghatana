import { useState, useEffect, useRef, useCallback } from 'react';

import type { AutocompleteOption } from './types';

/**
 * Internal hook for managing Input component's masked value state and mask-related operations.
 *
 * @internal
 */
export function useMaskState(initialValue: string) {
  const [maskedValue, setMaskedValue] = useState(initialValue);

  return { maskedValue, setMaskedValue };
}

/**
 * Internal hook for managing Input component's password visibility toggle state.
 *
 * @internal
 */
export function usePasswordVisibility() {
  const [showPassword, setShowPassword] = useState(false);

  const togglePasswordVisibility = useCallback(() => {
    setShowPassword((prev) => !prev);
  }, []);

  return { showPassword, setShowPassword, togglePasswordVisibility };
}

/**
 * Internal hook for managing autocomplete options visibility and filtering state.
 *
 * @internal
 */
export function useAutocompleteState() {
  const [showOptions, setShowOptions] = useState(false);
  const [filteredOptions, setFilteredOptions] = useState<AutocompleteOption[]>(
    []
  );
  const [activeOptionIndex, setActiveOptionIndex] = useState(-1);

  const selectOption = useCallback(
    (index: number) => {
      if (index >= 0 && index < filteredOptions.length) {
        // eslint-disable-next-line security/detect-object-injection
        return filteredOptions[index];
      }
      return undefined;
    },
    [filteredOptions]
  );

  return {
    showOptions,
    setShowOptions,
    filteredOptions,
    setFilteredOptions,
    activeOptionIndex,
    setActiveOptionIndex,
    selectOption,
  };
}

/**
 * Internal hook for managing screen reader announcements.
 *
 * Clears announcement after screen reader has time to read it.
 *
 * @internal
 */
export function useScreenReaderAnnouncement(
  shouldAnnounce: boolean,
  clearDelayMs: number = 1000
) {
  const [announcement, setAnnouncement] = useState('');

  const announce = useCallback(
    (message: string) => {
      if (shouldAnnounce) {
        setAnnouncement(message);
        // Clear announcement after screen reader has had time to read it
        setTimeout(() => setAnnouncement(''), clearDelayMs);
      }
    },
    [shouldAnnounce, clearDelayMs]
  );

  return { announcement, announce };
}

/**
 * Internal hook for detecting system theme preference.
 *
 * Listens for changes to prefers-color-scheme media query.
 *
 * @internal
 */
export function useSystemTheme(enabled: boolean) {
  const [systemTheme, setSystemTheme] = useState<'light' | 'dark'>('light');

  useEffect(() => {
    if (!enabled) return;

    // Check if user prefers dark mode
    const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');

    // Set initial theme
    setSystemTheme(mediaQuery.matches ? 'dark' : 'light');

    // Listen for changes
    const listener = (e: MediaQueryListEvent) => {
      setSystemTheme(e.matches ? 'dark' : 'light');
    };

    mediaQuery.addEventListener('change', listener);

    return () => {
      mediaQuery.removeEventListener('change', listener);
    };
  }, [enabled]);

  return systemTheme;
}

/**
 * Internal hook for detecting mobile viewport size.
 *
 * Listens for window resize events to detect mobile vs desktop.
 *
 * @param mobileBreakpoint - Breakpoint width in pixels (default: 768)
 * @param enabled - Whether to enable mobile detection
 *
 * @internal
 */
export function useMobileViewport(
  enabled: boolean,
  mobileBreakpoint: number = 768
) {
  const [isMobileViewport, setIsMobileViewport] = useState(false);

  useEffect(() => {
    if (!enabled) return;

    const checkViewport = () => {
      setIsMobileViewport(window.innerWidth < mobileBreakpoint);
    };

    // Check initially
    checkViewport();

    // Add resize listener
    window.addEventListener('resize', checkViewport);

    return () => {
      window.removeEventListener('resize', checkViewport);
    };
  }, [enabled, mobileBreakpoint]);

  return isMobileViewport;
}

/**
 * Internal hook for managing debounced change handler.
 *
 * Clears previous timeout before setting a new one.
 *
 * @internal
 */
export function useDebouncedChange(
  onDebouncedChange: ((value: string) => void) | undefined,
  delayMs: number
) {
  const debounceTimerRef = useRef<NodeJS.Timeout>();

  const debouncedChangeHandler = useCallback(
    (value: string) => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }

      debounceTimerRef.current = setTimeout(() => {
        onDebouncedChange?.(value);
      }, delayMs);
    },
    [delayMs, onDebouncedChange]
  );

  // Clean up debounce timer on unmount
  useEffect(() => {
    return () => {
      if (debounceTimerRef.current) {
        clearTimeout(debounceTimerRef.current);
      }
    };
  }, []);

  return debouncedChangeHandler;
}

/**
 * Internal hook for managing input ref that works with forwardRef.
 *
 * @internal
 */
export function useInputRef() {
  const internalRef = useRef<HTMLInputElement | null>(null);

  return internalRef;
}
