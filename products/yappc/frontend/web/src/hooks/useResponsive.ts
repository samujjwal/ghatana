/**
 * Responsive Hook
 *
 * Provides responsive design utilities for mobile-first approach.
 * Handles breakpoint detection, touch detection, and orientation changes.
 *
 * @doc.type hook
 * @doc.purpose Mobile-first responsive design utilities
 * @doc.layer product
 * @doc.pattern Custom Hook
 */

import { useState, useEffect, useCallback, useRef } from 'react';

// ============================================================================
// Breakpoints
// ============================================================================

export const breakpoints = {
  xs: 0,
  sm: 640,
  md: 768,
  lg: 1024,
  xl: 1280,
  '2xl': 1536,
} as const;

export type Breakpoint = keyof typeof breakpoints;

// ============================================================================
// Types
// ============================================================================

export interface UseResponsiveResult {
  // Breakpoint detection
  isXs: boolean;
  isSm: boolean;
  isMd: boolean;
  isLg: boolean;
  isXl: boolean;
  is2xl: boolean;
  currentBreakpoint: Breakpoint;

  // Device type
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;

  // Touch detection
  isTouch: boolean;
  hasHover: boolean;

  // Orientation
  isPortrait: boolean;
  isLandscape: boolean;

  // Viewport
  viewportWidth: number;
  viewportHeight: number;
}

// ============================================================================
// Media Query Helpers
// ============================================================================

function getBreakpoint(width: number): Breakpoint {
  if (width < breakpoints.sm) return 'xs';
  if (width < breakpoints.md) return 'sm';
  if (width < breakpoints.lg) return 'md';
  if (width < breakpoints.xl) return 'lg';
  if (width < breakpoints['2xl']) return 'xl';
  return '2xl';
}

function isTouchDevice(): boolean {
  return (
    'ontouchstart' in window ||
    navigator.maxTouchPoints > 0 ||
    (navigator as unknown as { msMaxTouchPoints?: number }).msMaxTouchPoints! > 0
  );
}

function hasHoverCapability(): boolean {
  return window.matchMedia('(hover: hover)').matches;
}

// ============================================================================
// Hook Implementation
// ============================================================================

export function useResponsive(): UseResponsiveResult {
  const [viewportWidth, setViewportWidth] = useState(window.innerWidth);
  const [viewportHeight, setViewportHeight] = useState(window.innerHeight);
  const [isTouch, setIsTouch] = useState(isTouchDevice());
  const [hasHover, setHasHover] = useState(hasHoverCapability());

  // Handle resize
  useEffect(() => {
    const handleResize = () => {
      setViewportWidth(window.innerWidth);
      setViewportHeight(window.innerHeight);
    };

    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // Handle touch capability changes
  useEffect(() => {
    const handleTouchChange = () => setIsTouch(isTouchDevice());
    const handleHoverChange = () => setHasHover(hasHoverCapability());

    window.addEventListener('touchstart', handleTouchChange, { once: true });
    window.matchMedia('(hover: hover)').addEventListener('change', handleHoverChange);

    return () => {
      window.matchMedia('(hover: hover)').removeEventListener('change', handleHoverChange);
    };
  }, []);

  const currentBreakpoint = getBreakpoint(viewportWidth);
  const isPortrait = viewportHeight > viewportWidth;
  const isLandscape = !isPortrait;

  return {
    // Breakpoint detection
    isXs: viewportWidth < breakpoints.sm,
    isSm: viewportWidth >= breakpoints.sm && viewportWidth < breakpoints.md,
    isMd: viewportWidth >= breakpoints.md && viewportWidth < breakpoints.lg,
    isLg: viewportWidth >= breakpoints.lg && viewportWidth < breakpoints.xl,
    isXl: viewportWidth >= breakpoints.xl && viewportWidth < breakpoints['2xl'],
    is2xl: viewportWidth >= breakpoints['2xl'],
    currentBreakpoint,

    // Device type
    isMobile: viewportWidth < breakpoints.md,
    isTablet: viewportWidth >= breakpoints.md && viewportWidth < breakpoints.lg,
    isDesktop: viewportWidth >= breakpoints.lg,

    // Touch detection
    isTouch,
    hasHover,

    // Orientation
    isPortrait,
    isLandscape,

    // Viewport
    viewportWidth,
    viewportHeight,
  };
}

// ============================================================================
// Media Query Hook
// ============================================================================

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() => window.matchMedia(query).matches);

  useEffect(() => {
    const mediaQuery = window.matchMedia(query);
    const handleChange = (event: MediaQueryListEvent) => setMatches(event.matches);

    mediaQuery.addEventListener('change', handleChange);
    return () => mediaQuery.removeEventListener('change', handleChange);
  }, [query]);

  return matches;
}

// ============================================================================
// Breakpoint Hook
// ============================================================================

export function useBreakpoint(breakpoint: Breakpoint): boolean {
  const { currentBreakpoint } = useResponsive();
  const breakpointOrder: Breakpoint[] = ['xs', 'sm', 'md', 'lg', 'xl', '2xl'];
  const currentIndex = breakpointOrder.indexOf(currentBreakpoint);
  const targetIndex = breakpointOrder.indexOf(breakpoint);
  return currentIndex >= targetIndex;
}

// ============================================================================
// Touch Gesture Hook
// ============================================================================

export interface UseTouchGesturesOptions {
  onSwipeLeft?: () => void;
  onSwipeRight?: () => void;
  onSwipeUp?: () => void;
  onSwipeDown?: () => void;
  onTap?: () => void;
  onLongPress?: () => void;
  swipeThreshold?: number;
  longPressDelay?: number;
}

export function useTouchGestures(options: UseTouchGesturesOptions = {}) {
  const {
    onSwipeLeft,
    onSwipeRight,
    onSwipeUp,
    onSwipeDown,
    onTap,
    onLongPress,
    swipeThreshold = 50,
    longPressDelay = 500,
  } = options;

  const touchStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const longPressTimerRef = useRef<NodeJS.Timeout | null>(null);

  const handleTouchStart = useCallback((e: TouchEvent) => {
    const touch = e.touches[0];
    touchStartRef.current = {
      x: touch.clientX,
      y: touch.clientY,
      time: Date.now(),
    };

    // Start long press timer
    if (onLongPress) {
      longPressTimerRef.current = setTimeout(() => {
        onLongPress();
      }, longPressDelay);
    }
  }, [onLongPress, longPressDelay]);

  const handleTouchEnd = useCallback((e: TouchEvent) => {
    // Clear long press timer
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }

    if (!touchStartRef.current) return;

    const touch = e.changedTouches[0];
    const deltaX = touch.clientX - touchStartRef.current.x;
    const deltaY = touch.clientY - touchStartRef.current.y;
    const deltaTime = Date.now() - touchStartRef.current.time;

    // Check for tap (short duration, minimal movement)
    if (onTap && deltaTime < 300 && Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
      onTap();
    }

    // Check for swipes
    if (Math.abs(deltaX) > Math.abs(deltaY)) {
      // Horizontal swipe
      if (Math.abs(deltaX) > swipeThreshold) {
        if (deltaX > 0 && onSwipeRight) {
          onSwipeRight();
        } else if (deltaX < 0 && onSwipeLeft) {
          onSwipeLeft();
        }
      }
    } else {
      // Vertical swipe
      if (Math.abs(deltaY) > swipeThreshold) {
        if (deltaY > 0 && onSwipeDown) {
          onSwipeDown();
        } else if (deltaY < 0 && onSwipeUp) {
          onSwipeUp();
        }
      }
    }

    touchStartRef.current = null;
  }, [onSwipeLeft, onSwipeRight, onSwipeUp, onSwipeDown, onTap, swipeThreshold]);

  const handleTouchCancel = useCallback(() => {
    // Clear long press timer
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
    touchStartRef.current = null;
  }, []);

  return {
    onTouchStart: handleTouchStart,
    onTouchEnd: handleTouchEnd,
    onTouchCancel: handleTouchCancel,
  };
}
