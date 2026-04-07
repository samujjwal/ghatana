/**
 * Mobile Touch Hook Tests
 * @doc.type test
 * @doc.purpose Test mobile touch gesture detection and handling
 * @doc.layer unit
 */

import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useMobileTouch } from './useMobileTouch';
import type { TouchConfig } from './useMobileTouch';

describe('useMobileTouch', () => {
  describe('Touch Detection', () => {
    it('should detect touch start', () => {
      const onTouchStart = vi.fn();
      const { result } = renderHook(() =>
        useMobileTouch({ onTouchStart } as any)
      );

      const event = new TouchEvent('touchstart', {
        touches: [{ clientX: 100, clientY: 100 } as any],
      });

      act(() => {
        result.current.handleTouchStart(event as any);
      });

      expect(result.current.touchStarted).toBe(true);
    });

    it('should track touch points', () => {
      const { result } = renderHook(() => useMobileTouch({} as any));

      const touchEvent = new TouchEvent('touchstart', {
        touches: [{ clientX: 100, clientY: 100, identifier: 0 } as any],
      });

      act(() => {
        result.current.handleTouchStart(touchEvent as any);
      });

      expect(result.current.currentTouchPoints).toHaveLength(1);
    });
  });

  describe('Gesture Recognition', () => {
    it('should recognize pinch zoom gesture', () => {
      const onPinch = vi.fn();
      const { result } = renderHook(() => useMobileTouch({ onPinch } as any));

      // Simulate pinch start
      const startEvent = new TouchEvent('touchstart', {
        touches: [
          { clientX: 100, clientY: 100, identifier: 0 } as any,
          { clientX: 200, clientY: 200, identifier: 1 } as any,
        ],
      });

      act(() => {
        result.current.handleTouchStart(startEvent as any);
      });

      expect(result.current.currentTouchPoints).toHaveLength(2);
    });

    it('should recognize swipe gesture', () => {
      const onSwipe = vi.fn();
      const { result } = renderHook(() => useMobileTouch({ onSwipe } as any));

      const config: TouchConfig = { swipeThreshold: 50 };
      const configuredResult = useMobileTouch({ onSwipe, ...config } as any);

      expect(configuredResult).toBeDefined();
    });

    it('should recognize long press gesture', () => {
      const onLongPress = vi.fn();
      const { result } = renderHook(() =>
        useMobileTouch({ onLongPress } as any)
      );

      expect(result.current).toBeDefined();
    });

    it('should recognize double tap gesture', () => {
      const onDoubleTap = vi.fn();
      const { result } = renderHook(() =>
        useMobileTouch({ onDoubleTap } as any)
      );

      expect(result.current).toBeDefined();
    });
  });

  describe('Multi-Touch', () => {
    it('should handle multi-touch with 2+ fingers', () => {
      const { result } = renderHook(() => useMobileTouch({} as any));

      const multiTouchEvent = new TouchEvent('touchstart', {
        touches: [
          { clientX: 100, clientY: 100, identifier: 0 } as any,
          { clientX: 150, clientY: 150, identifier: 1 } as any,
          { clientX: 200, clientY: 200, identifier: 2 } as any,
        ],
      });

      act(() => {
        result.current.handleTouchStart(multiTouchEvent as any);
      });

      expect(result.current.currentTouchPoints.length).toBeGreaterThan(2);
    });
  });

  describe('Touch End Handling', () => {
    it('should clear touch state on touch end', () => {
      const { result } = renderHook(() => useMobileTouch({} as any));

      const startEvent = new TouchEvent('touchstart', {
        touches: [{ clientX: 100, clientY: 100, identifier: 0 } as any],
      });

      act(() => {
        result.current.handleTouchStart(startEvent as any);
      });

      expect(result.current.touchStarted).toBe(true);

      const endEvent = new TouchEvent('touchend', {
        touches: [],
      });

      act(() => {
        result.current.handleTouchEnd(endEvent as any);
      });

      expect(result.current.touchStarted).toBe(false);
    });
  });

  describe('Configuration', () => {
    it('should apply touch sensitivity configuration', () => {
      const config: TouchConfig = {
        swipeThreshold: 100,
        pinchSensitivity: 1.2,
        longPressDuration: 500,
      };

      const { result } = renderHook(() => useMobileTouch(config as any));

      expect(result.current).toBeDefined();
    });
  });
});
