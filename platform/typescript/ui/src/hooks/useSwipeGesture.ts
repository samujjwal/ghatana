import { useCallback, useEffect, useRef, useState } from 'react';

export type SwipeDirection = 'left' | 'right' | 'up' | 'down';

export interface SwipeGestureOptions {
  /** Minimum distance in pixels to trigger a swipe (default: 50) */
  threshold?: number;
  /** Maximum time in ms for the swipe gesture (default: 300) */
  maxTime?: number;
  /** Enable/disable the gesture (default: true) */
  enabled?: boolean;
  /** Prevent default touch behavior (default: false) */
  preventDefault?: boolean;
  /** Called when swipe starts */
  onSwipeStart?: () => void;
  /** Called during swipe with progress */
  onSwipeMove?: (deltaX: number, deltaY: number) => void;
  /** Called when swipe ends */
  onSwipeEnd?: () => void;
}

export interface SwipeCallbacks {
  onSwipeLeft?: () => void;
  onSwipeRight?: () => void;
  onSwipeUp?: () => void;
  onSwipeDown?: () => void;
  onSwipe?: (direction: SwipeDirection) => void;
}

export interface SwipeState {
  /** Whether a swipe is currently in progress */
  isSwiping: boolean;
  /** Current horizontal delta from start position */
  deltaX: number;
  /** Current vertical delta from start position */
  deltaY: number;
  /** The detected swipe direction (null if not yet determined) */
  direction: SwipeDirection | null;
}

/**
 * Hook for detecting swipe gestures on touch-enabled devices
 * 
 * @example
 * ```tsx
 * function SwipeableComponent() {
 *   const { ref, state } = useSwipeGesture({
 *     onSwipeLeft: () => navigateNext(),
 *     onSwipeRight: () => navigatePrevious(),
 *     threshold: 100,
 *   });
 * 
 *   return (
 *     <div ref={ref} style={{ transform: `translateX(${state.deltaX}px)` }}>
 *       Swipe me!
 *     </div>
 *   );
 * }
 * ```
 */
export function useSwipeGesture<T extends HTMLElement = HTMLDivElement>(
  callbacks: SwipeCallbacks = {},
  options: SwipeGestureOptions = {}
): {
  ref: React.RefObject<T | null>;
  state: SwipeState;
  reset: () => void;
} {
  const {
    threshold = 50,
    maxTime = 300,
    enabled = true,
    preventDefault = false,
    onSwipeStart,
    onSwipeMove,
    onSwipeEnd,
  } = options;

  const { onSwipeLeft, onSwipeRight, onSwipeUp, onSwipeDown, onSwipe } = callbacks;

  const ref = useRef<T>(null);
  const startRef = useRef<{ x: number; y: number; time: number } | null>(null);
  
  const [state, setState] = useState<SwipeState>({
    isSwiping: false,
    deltaX: 0,
    deltaY: 0,
    direction: null,
  });

  const reset = useCallback(() => {
    setState({
      isSwiping: false,
      deltaX: 0,
      deltaY: 0,
      direction: null,
    });
    startRef.current = null;
  }, []);

  const getDirection = useCallback((deltaX: number, deltaY: number): SwipeDirection | null => {
    const absX = Math.abs(deltaX);
    const absY = Math.abs(deltaY);

    // Must exceed threshold
    if (absX < threshold && absY < threshold) {
      return null;
    }

    // Determine primary direction
    if (absX > absY) {
      return deltaX > 0 ? 'right' : 'left';
    } else {
      return deltaY > 0 ? 'down' : 'up';
    }
  }, [threshold]);

  useEffect(() => {
    if (!enabled) return;

    const element = ref.current;
    if (!element) return;

    const handleTouchStart = (e: TouchEvent) => {
      if (preventDefault) {
        e.preventDefault();
      }

      const touch = e.touches[0];
      startRef.current = {
        x: touch.clientX,
        y: touch.clientY,
        time: Date.now(),
      };

      setState({
        isSwiping: true,
        deltaX: 0,
        deltaY: 0,
        direction: null,
      });

      onSwipeStart?.();
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (!startRef.current) return;

      if (preventDefault) {
        e.preventDefault();
      }

      const touch = e.touches[0];
      const deltaX = touch.clientX - startRef.current.x;
      const deltaY = touch.clientY - startRef.current.y;
      const direction = getDirection(deltaX, deltaY);

      setState({
        isSwiping: true,
        deltaX,
        deltaY,
        direction,
      });

      onSwipeMove?.(deltaX, deltaY);
    };

    const handleTouchEnd = () => {
      if (!startRef.current) return;

      const elapsed = Date.now() - startRef.current.time;
      const { direction } = state;

      // Check if this was a valid swipe
      const isValidSwipe = elapsed <= maxTime && direction !== null;

      if (isValidSwipe && direction) {
        // Call specific direction callback
        switch (direction) {
          case 'left':
            onSwipeLeft?.();
            break;
          case 'right':
            onSwipeRight?.();
            break;
          case 'up':
            onSwipeUp?.();
            break;
          case 'down':
            onSwipeDown?.();
            break;
        }

        // Call generic swipe callback
        onSwipe?.(direction);
      }

      onSwipeEnd?.();
      reset();
    };

    const handleTouchCancel = () => {
      onSwipeEnd?.();
      reset();
    };

    element.addEventListener('touchstart', handleTouchStart, { passive: !preventDefault });
    element.addEventListener('touchmove', handleTouchMove, { passive: !preventDefault });
    element.addEventListener('touchend', handleTouchEnd);
    element.addEventListener('touchcancel', handleTouchCancel);

    return () => {
      element.removeEventListener('touchstart', handleTouchStart);
      element.removeEventListener('touchmove', handleTouchMove);
      element.removeEventListener('touchend', handleTouchEnd);
      element.removeEventListener('touchcancel', handleTouchCancel);
    };
  }, [
    enabled,
    preventDefault,
    threshold,
    maxTime,
    state,
    getDirection,
    reset,
    onSwipeStart,
    onSwipeMove,
    onSwipeEnd,
    onSwipeLeft,
    onSwipeRight,
    onSwipeUp,
    onSwipeDown,
    onSwipe,
  ]);

  return { ref, state, reset };
}

/**
 * Hook for detecting horizontal swipes (left/right navigation)
 * Simplified version of useSwipeGesture for common use case
 */
export function useHorizontalSwipe<T extends HTMLElement = HTMLDivElement>(
  onSwipeLeft?: () => void,
  onSwipeRight?: () => void,
  options: Omit<SwipeGestureOptions, 'preventDefault'> = {}
) {
  return useSwipeGesture<T>(
    { onSwipeLeft, onSwipeRight },
    { ...options, preventDefault: false }
  );
}

/**
 * Hook for detecting vertical swipes (scroll-like gestures)
 * Simplified version of useSwipeGesture for common use case
 */
export function useVerticalSwipe<T extends HTMLElement = HTMLDivElement>(
  onSwipeUp?: () => void,
  onSwipeDown?: () => void,
  options: Omit<SwipeGestureOptions, 'preventDefault'> = {}
) {
  return useSwipeGesture<T>(
    { onSwipeUp, onSwipeDown },
    { ...options, preventDefault: false }
  );
}
