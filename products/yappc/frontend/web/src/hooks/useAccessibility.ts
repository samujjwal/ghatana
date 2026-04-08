/**
 * Accessibility Hook
 *
 * React hook for managing accessibility features including:
 * - Focus management
 * - Keyboard navigation
 * - Screen reader announcements
 * - Reduced motion preferences
 *
 * @doc.type hook
 * @doc.purpose Accessibility features management
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useEffect, useRef, useState, useCallback } from 'react';
import type { CSSProperties } from 'react';
import {
  trapFocus,
  focusFirstElement,
  focusLastElement,
  restoreFocus,
  announceToScreenReader,
  isScreenReaderActive,
  prefersReducedMotion,
  getAnimationDuration,
  visuallyHiddenStyles,
} from '../lib/accessibility';

// ============================================================================
// Types
// ============================================================================

export interface UseAccessibilityOptions {
  enableFocusTrap?: boolean;
  enableAnnouncements?: boolean;
  respectReducedMotion?: boolean;
}

export interface UseAccessibilityResult {
  // Focus management
  trapFocus: (container: HTMLElement | null) => () => void;
  focusFirst: (container: HTMLElement | null) => boolean;
  focusLast: (container: HTMLElement | null) => boolean;
  saveFocus: () => void;
  restoreFocus: () => void;

  // Screen reader
  announce: (message: string, politeness?: 'polite' | 'assertive') => void;
  isScreenReaderActive: boolean;

  // Reduced motion
  prefersReducedMotion: boolean;
  getAnimationDuration: (normalDuration: number) => number;

  // Utilities
  visuallyHiddenStyles: CSSProperties;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useAccessibility(options: UseAccessibilityOptions = {}): UseAccessibilityResult {
  const {
    enableFocusTrap = true,
    enableAnnouncements = true,
    respectReducedMotion = true,
  } = options;

  const savedFocusRef = useRef<HTMLElement | null>(null);
  const [screenReaderActive, setScreenReaderActive] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);

  // Monitor screen reader and reduced motion preferences
  useEffect(() => {
    setScreenReaderActive(isScreenReaderActive());
    setReducedMotion(prefersReducedMotion());

    const screenReaderQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');

    const handleScreenReaderChange = () => setScreenReaderActive(isScreenReaderActive());
    const handleReducedMotionChange = () => setReducedMotion(prefersReducedMotion());

    screenReaderQuery.addEventListener('change', handleScreenReaderChange);
    reducedMotionQuery.addEventListener('change', handleReducedMotionChange);

    return () => {
      screenReaderQuery.removeEventListener('change', handleScreenReaderChange);
      reducedMotionQuery.removeEventListener('change', handleReducedMotionChange);
    };
  }, []);

  // Focus trap
  const trapFocusCallback = useCallback((container: HTMLElement | null) => {
    if (!container || !enableFocusTrap) return () => {};
    return trapFocus(container);
  }, [enableFocusTrap]);

  // Focus first element
  const focusFirstCallback = useCallback((container: HTMLElement | null) => {
    if (!container) return false;
    return focusFirstElement(container);
  }, []);

  // Focus last element
  const focusLastCallback = useCallback((container: HTMLElement | null) => {
    if (!container) return false;
    return focusLastElement(container);
  }, []);

  // Save current focus
  const saveFocus = useCallback(() => {
    savedFocusRef.current = document.activeElement as HTMLElement;
  }, []);

  // Restore saved focus
  const restoreFocusCallback = useCallback(() => {
    restoreFocus(savedFocusRef.current);
  }, []);

  // Announce to screen reader
  const announceCallback = useCallback((message: string, politeness: 'polite' | 'assertive' = 'polite') => {
    if (enableAnnouncements) {
      announceToScreenReader(message, politeness);
    }
  }, [enableAnnouncements]);

  // Get animation duration respecting reduced motion
  const getAnimationDurationCallback = useCallback((normalDuration: number) => {
    return respectReducedMotion ? getAnimationDuration(normalDuration) : normalDuration;
  }, [respectReducedMotion]);

  return {
    // Focus management
    trapFocus: trapFocusCallback,
    focusFirst: focusFirstCallback,
    focusLast: focusLastCallback,
    saveFocus,
    restoreFocus: restoreFocusCallback,

    // Screen reader
    announce: announceCallback,
    isScreenReaderActive: screenReaderActive,

    // Reduced motion
    prefersReducedMotion: reducedMotion,
    getAnimationDuration: getAnimationDurationCallback,

    // Utilities
    visuallyHiddenStyles,
  };
}

// ============================================================================
// Focus Management Hook
// ============================================================================

export interface UseFocusTrapOptions {
  enabled?: boolean;
  restoreFocusOnUnmount?: boolean;
}

export function useFocusTrap(containerRef: React.RefObject<HTMLElement>, options: UseFocusTrapOptions = {}) {
  const { enabled = true, restoreFocusOnUnmount = true } = options;
  const savedFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !enabled) return;

    // Save current focus
    savedFocusRef.current = document.activeElement as HTMLElement;

    // Set up focus trap
    const cleanup = trapFocus(container);

    // Focus first element
    focusFirstElement(container);

    return () => {
      cleanup();
      // Restore focus on unmount
      if (restoreFocusOnUnmount && savedFocusRef.current) {
        restoreFocus(savedFocusRef.current);
      }
    };
  }, [containerRef, enabled, restoreFocusOnUnmount]);
}

// ============================================================================
// Skip Link Hook
// ============================================================================

export function useSkipLink(targetId: string) {
  const handleClick = useCallback(() => {
    const target = document.getElementById(targetId);
    if (target) {
      target.focus();
      target.scrollIntoView({ behavior: 'smooth' });
    }
  }, [targetId]);

  return { handleClick };
}
