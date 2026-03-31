/**
 * @fileoverview Mobile Touch Interaction Handler
 * Fixes touch interactions for canvas on mobile devices
 * 
 * @doc.type hook
 * @doc.purpose Enable proper touch gestures on mobile canvas
 * @doc.layer presentation
 * @doc.pattern MobileOptimization
 */

import { useCallback, useRef, useEffect } from 'react';

// ============================================================================
// Types
// ============================================================================

export interface TouchPoint {
  x: number;
  y: number;
  id: number;
  pressure?: number;
}

export interface TouchGesture {
  type: 'pan' | 'pinch' | 'tap' | 'double-tap' | 'long-press' | 'swipe';
  startPoint: TouchPoint;
  currentPoint: TouchPoint;
  deltaX: number;
  deltaY: number;
  scale?: number;
  rotation?: number;
  velocity: { x: number; y: number };
}

export interface TouchConfig {
  onPan?: (gesture: TouchGesture) => void;
  onPinch?: (gesture: TouchGesture) => void;
  onTap?: (point: TouchPoint) => void;
  onDoubleTap?: (point: TouchPoint) => void;
  onLongPress?: (point: TouchPoint) => void;
  onSwipe?: (gesture: TouchGesture) => void;
  longPressDelay?: number;
  doubleTapDelay?: number;
  swipeThreshold?: number;
}

// ============================================================================
// Hook: useMobileTouch
// ============================================================================

/**
 * Mobile Touch Handler Hook
 * @doc.purpose Handle mobile touch gestures for canvas interaction
 * 
 * Features:
 * - Pan gesture with momentum
 * - Pinch zoom with scale detection
 * - Double-tap to zoom
 * - Long press for context menu
 * - Swipe detection
 * - Touch point tracking
 */
export function useMobileTouch(config: TouchConfig) {
  const {
    onPan,
    onPinch,
    onTap,
    onDoubleTap,
    onLongPress,
    onSwipe,
    longPressDelay = 500,
    doubleTapDelay = 300,
    swipeThreshold = 50,
  } = config;

  const touchStartRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const lastTapRef = useRef<{ x: number; y: number; time: number } | null>(null);
  const longPressTimerRef = useRef<NodeJS.Timeout | null>(null);
  const activeTouchesRef = useRef<Map<number, TouchPoint>>(new Map());
  const pinchStartRef = useRef<{ distance: number; scale: number } | null>(null);
  const isPanningRef = useRef(false);
  const isPinchingRef = useRef(false);

  // Clear long press timer
  const clearLongPressTimer = useCallback(() => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerTimerRef.current);
      longPressTimerRef.current = null;
    }
  }, []);

  // Calculate distance between two touch points
  const getDistance = useCallback((p1: TouchPoint, p2: TouchPoint): number => {
    const dx = p1.x - p2.x;
    const dy = p1.y - p2.y;
    return Math.sqrt(dx * dx + dy * dy);
  }, []);

  // Get center point between two touches
  const getCenter = useCallback((p1: TouchPoint, p2: TouchPoint): TouchPoint => {
    return {
      x: (p1.x + p2.x) / 2,
      y: (p1.y + p2.y) / 2,
      id: -1,
    };
  }, []);

  // Handle touch start
  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    e.preventDefault();
    
    const touches = e.touches;
    const now = Date.now();

    // Store active touches
    for (let i = 0; i < touches.length; i++) {
      const touch = touches[i];
      activeTouchesRef.current.set(touch.identifier, {
        x: touch.clientX,
        y: touch.clientY,
        id: touch.identifier,
        pressure: touch.force || 0.5,
      });
    }

    // Single touch
    if (touches.length === 1) {
      const touch = touches[0];
      touchStartRef.current = {
        x: touch.clientX,
        y: touch.clientY,
        time: now,
      };

      // Check for double tap
      if (lastTapRef.current) {
        const timeDiff = now - lastTapRef.current.time;
        const distance = Math.sqrt(
          Math.pow(touch.clientX - lastTapRef.current.x, 2) +
          Math.pow(touch.clientY - lastTapRef.current.y, 2)
        );

        if (timeDiff < doubleTapDelay && distance < 20) {
          // Double tap detected
          onDoubleTap?.({
            x: touch.clientX,
            y: touch.clientY,
            id: touch.identifier,
          });
          lastTapRef.current = null;
          clearLongPressTimer();
          return;
        }
      }

      // Store for potential double tap
      lastTapRef.current = {
        x: touch.clientX,
        y: touch.clientY,
        time: now,
      };

      // Start long press timer
      longPressTimerRef.current = setTimeout(() => {
        onLongPress?.({
          x: touch.clientX,
          y: touch.clientY,
          id: touch.identifier,
        });
      }, longPressDelay);
    }

    // Two finger pinch start
    if (touches.length === 2) {
      isPinchingRef.current = true;
      const touch1 = activeTouchesRef.current.get(touches[0].identifier)!;
      const touch2 = activeTouchesRef.current.get(touches[1].identifier)!;
      const distance = getDistance(touch1, touch2);
      
      pinchStartRef.current = {
        distance,
        scale: 1,
      };
    }
  }, [onDoubleTap, onLongPress, doubleTapDelay, longPressDelay, getDistance]);

  // Handle touch move
  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    e.preventDefault();

    const touches = e.touches;
    const now = Date.now();

    // Update active touches
    for (let i = 0; i < touches.length; i++) {
      const touch = touches[i];
      if (activeTouchesRef.current.has(touch.identifier)) {
        activeTouchesRef.current.set(touch.identifier, {
          x: touch.clientX,
          y: touch.clientY,
          id: touch.identifier,
          pressure: touch.force || 0.5,
        });
      }
    }

    // Single touch pan
    if (touches.length === 1 && touchStartRef.current && !isPinchingRef.current) {
      const touch = touches[0];
      const deltaX = touch.clientX - touchStartRef.current.x;
      const deltaY = touch.clientY - touchStartRef.current.y;
      const distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);

      // Start panning after threshold
      if (distance > 5) {
        isPanningRef.current = true;
        clearLongPressTimer();

        onPan?.({
          type: 'pan',
          startPoint: {
            x: touchStartRef.current.x,
            y: touchStartRef.current.y,
            id: touch.identifier,
          },
          currentPoint: {
            x: touch.clientX,
            y: touch.clientY,
            id: touch.identifier,
          },
          deltaX,
          deltaY,
          velocity: {
            x: deltaX / (now - touchStartRef.current.time),
            y: deltaY / (now - touchStartRef.current.time),
          },
        });
      }
    }

    // Two finger pinch
    if (touches.length === 2 && pinchStartRef.current) {
      const touch1 = activeTouchesRef.current.get(touches[0].identifier)!;
      const touch2 = activeTouchesRef.current.get(touches[1].identifier)!;
      const currentDistance = getDistance(touch1, touch2);
      const scale = currentDistance / pinchStartRef.current.distance;
      const center = getCenter(touch1, touch2);

      pinchStartRef.current.scale = scale;

      onPinch?.({
        type: 'pinch',
        startPoint: center,
        currentPoint: center,
        deltaX: 0,
        deltaY: 0,
        scale,
        velocity: { x: 0, y: 0 },
      });
    }
  }, [onPan, onPinch, getDistance, getCenter, clearLongPressTimer]);

  // Handle touch end
  const handleTouchEnd = useCallback((e: React.TouchEvent) => {
    const touches = e.touches;
    const changedTouches = e.changedTouches;
    const now = Date.now();

    // Remove ended touches
    for (let i = 0; i < changedTouches.length; i++) {
      activeTouchesRef.current.delete(changedTouches[i].identifier);
    }

    // Check for tap (if not panning and quick release)
    if (touches.length === 0 && touchStartRef.current && !isPanningRef.current && !isPinchingRef.current) {
      const duration = now - touchStartRef.current.time;
      
      if (duration < longPressDelay) {
        onTap?.({
          x: touchStartRef.current.x,
          y: touchStartRef.current.y,
          id: changedTouches[0]?.identifier || 0,
        });
      }
    }

    // Check for swipe (if panning and quick release with velocity)
    if (isPanningRef.current && touchStartRef.current) {
      const lastTouch = changedTouches[0];
      if (lastTouch) {
        const deltaX = lastTouch.clientX - touchStartRef.current.x;
        const deltaY = lastTouch.clientY - touchStartRef.current.y;
        const duration = now - touchStartRef.current.time;
        const velocity = Math.sqrt(deltaX * deltaX + deltaY * deltaY) / duration;

        if (velocity > 0.5 && (Math.abs(deltaX) > swipeThreshold || Math.abs(deltaY) > swipeThreshold)) {
          onSwipe?.({
            type: 'swipe',
            startPoint: {
              x: touchStartRef.current.x,
              y: touchStartRef.current.y,
              id: lastTouch.identifier,
            },
            currentPoint: {
              x: lastTouch.clientX,
              y: lastTouch.clientY,
              id: lastTouch.identifier,
            },
            deltaX,
            deltaY,
            velocity: {
              x: deltaX / duration,
              y: deltaY / duration,
            },
          });
        }
      }
    }

    // Reset state
    if (touches.length === 0) {
      touchStartRef.current = null;
      isPanningRef.current = false;
      isPinchingRef.current = false;
      pinchStartRef.current = null;
      clearLongPressTimer();
    }
  }, [onTap, onSwipe, longPressDelay, swipeThreshold, clearLongPressTimer]);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      clearLongPressTimer();
    };
  }, [clearLongPressTimer]);

  return {
    onTouchStart: handleTouchStart,
    onTouchMove: handleTouchMove,
    onTouchEnd: handleTouchEnd,
    isPanning: () => isPanningRef.current,
    isPinching: () => isPinchingRef.current,
  };
}

// ============================================================================
// Component: MobileCanvas
// ============================================================================

import React, { useRef, useEffect, useState } from 'react';

interface MobileCanvasProps {
  children: React.ReactNode;
  onPan?: (gesture: TouchGesture) => void;
  onPinch?: (gesture: TouchGesture) => void;
  onTap?: (point: TouchPoint) => void;
  onDoubleTap?: (point: TouchPoint) => void;
  onLongPress?: (point: TouchPoint) => void;
  onSwipe?: (gesture: TouchGesture) => void;
  className?: string;
  style?: React.CSSProperties;
  enableTouchAction?: boolean;
}

/**
 * Mobile Canvas Component
 * @doc.purpose Canvas container with mobile touch support
 * 
 * Features:
 * - Touch action CSS for smooth gestures
 * - Prevent default browser touch behaviors
 * - Touch point visualization (optional)
 */
export const MobileCanvas: React.FC<MobileCanvasProps> = ({
  children,
  onPan,
  onPinch,
  onTap,
  onDoubleTap,
  onLongPress,
  onSwipe,
  className,
  style,
  enableTouchAction = true,
}) => {
  const canvasRef = useRef<HTMLDivElement>(null);
  const [touchPoints, setTouchPoints] = useState<TouchPoint[]>([]);

  const touchHandlers = useMobileTouch({
    onPan,
    onPinch,
    onTap,
    onDoubleTap,
    onLongPress,
    onSwipe,
  });

  // Prevent default touch behaviors that interfere with canvas
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const preventDefault = (e: Event) => {
      // Prevent scrolling when touching canvas
      if (e.type === 'touchmove' && (e as TouchEvent).touches.length > 1) {
        e.preventDefault();
      }
    };

    canvas.addEventListener('touchstart', preventDefault, { passive: false });
    canvas.addEventListener('touchmove', preventDefault, { passive: false });

    return () => {
      canvas.removeEventListener('touchstart', preventDefault);
      canvas.removeEventListener('touchmove', preventDefault);
    };
  }, []);

  return (
    <div
      ref={canvasRef}
      className={className}
      style={{
        touchAction: enableTouchAction ? 'none' : 'auto',
        WebkitTouchCallout: 'none',
        WebkitUserSelect: 'none',
        userSelect: 'none',
        overscrollBehavior: 'none',
        ...style,
      }}
      {...touchHandlers}
    >
      {children}
      
      {/* Debug touch points */}
      {process.env.NODE_ENV === 'development' && touchPoints.length > 0 && (
        <div style={{ position: 'absolute', top: 0, left: 0, pointerEvents: 'none' }}>
          {touchPoints.map((point) => (
            <div
              key={point.id}
              style={{
                position: 'absolute',
                left: point.x - 10,
                top: point.y - 10,
                width: 20,
                height: 20,
                borderRadius: '50%',
                background: 'rgba(255, 0, 0, 0.5)',
                border: '2px solid red',
              }}
            />
          ))}
        </div>
      )}
    </div>
  );
};

export default useMobileTouch;
