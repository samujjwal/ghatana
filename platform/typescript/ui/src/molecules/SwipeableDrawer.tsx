import React, { useCallback, useEffect, useRef, useState } from 'react';
import { cn } from '@ghatana/utils';
import { transitions } from '@ghatana/tokens';

import { Drawer, type DrawerProps } from '../molecules/Drawer';
import { useSwipeGesture } from '../hooks/useSwipeGesture';
import { sxToStyle, type SxProps } from '../utils/sx';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

export interface SwipeableDrawerProps extends Omit<DrawerProps, 'className'> {
  /**
   * Minimum swipe distance (px) to close the drawer.
   * @default 60
   */
  swipeThreshold?: number;

  /**
   * Width (px) of the invisible edge area that accepts swipe-to-open gestures.
   * Set to 0 to disable swipe-to-open.
   * @default 20
   */
  swipeAreaWidth?: number;

  /**
   * Callback fired when swipe-to-open is triggered.
   * Must typically call `onOpen()` or `setIsOpen(true)`.
   */
  onOpen?: () => void;

  /** Disable swipe gestures entirely. */
  disableSwipe?: boolean;

  /** MUI-compatible sx prop */
  sx?: SxProps;

  /** Additional CSS classes */
  className?: string;
}

/**
 * SwipeableDrawer wraps the base Drawer with touch-gesture support.
 *
 * - **Swipe-to-close**: swipe in the direction the drawer opened from.
 * - **Swipe-to-open**: swipe from the edge of the screen (configurable area).
 *
 * @example
 * ```tsx
 * <SwipeableDrawer
 *   isOpen={open}
 *   onClose={() => setOpen(false)}
 *   onOpen={() => setOpen(true)}
 *   position="bottom"
 * >
 *   <div className="p-4">Sheet content</div>
 * </SwipeableDrawer>
 * ```
 */
export function SwipeableDrawer({
  isOpen,
  onClose,
  onOpen,
  position = 'bottom',
  swipeThreshold = 60,
  swipeAreaWidth = 20,
  disableSwipe = false,
  children,
  className,
  sx,
  ...rest
}: SwipeableDrawerProps) {
  const contentRef = useRef<HTMLDivElement>(null);
  const [translatePx, setTranslatePx] = useState(0);

  // Close-direction mapping
  const closeSwipeDirection = (
    { left: 'left', right: 'right', top: 'up', bottom: 'down' } as const
  )[position];

  // Open-direction is the opposite
  const openSwipeDirection = (
    { left: 'right', right: 'left', top: 'down', bottom: 'up' } as const
  )[position];

  // Swipe-to-close inside the drawer
  const { ref: swipeRef } = useSwipeGesture(
    {
      onSwipe: (dir) => {
        if (dir === closeSwipeDirection) {
          onClose();
        }
      },
      onSwipeMove: (dx, dy) => {
        if (disableSwipe) return;
        const delta = position === 'left' || position === 'right' ? dx : dy;
        const isClosing =
          (position === 'left' && delta < 0) ||
          (position === 'right' && delta > 0) ||
          (position === 'top' && delta < 0) ||
          (position === 'bottom' && delta > 0);
        if (isClosing) {
          setTranslatePx(delta);
        }
      },
      onSwipeEnd: () => setTranslatePx(0),
      threshold: swipeThreshold,
      enabled: isOpen && !disableSwipe,
    },
  );

  // Reset translate when closing
  useEffect(() => {
    if (!isOpen) setTranslatePx(0);
  }, [isOpen]);

  // Swipe-to-open edge detection (invisible touch area)
  useEffect(() => {
    if (isOpen || disableSwipe || swipeAreaWidth <= 0 || !onOpen) return;

    let startX = 0;
    let startY = 0;

    const handleStart = (e: TouchEvent) => {
      const { clientX, clientY } = e.touches[0];
      const { innerWidth, innerHeight } = window;

      const inEdge =
        (position === 'left' && clientX <= swipeAreaWidth) ||
        (position === 'right' && clientX >= innerWidth - swipeAreaWidth) ||
        (position === 'top' && clientY <= swipeAreaWidth) ||
        (position === 'bottom' && clientY >= innerHeight - swipeAreaWidth);

      if (inEdge) {
        startX = clientX;
        startY = clientY;
      }
    };

    const handleEnd = (e: TouchEvent) => {
      if (startX === 0 && startY === 0) return;
      const { clientX, clientY } = e.changedTouches[0];
      const dx = clientX - startX;
      const dy = clientY - startY;

      const triggered =
        (position === 'left' && dx > swipeThreshold) ||
        (position === 'right' && dx < -swipeThreshold) ||
        (position === 'top' && dy > swipeThreshold) ||
        (position === 'bottom' && dy < -swipeThreshold);

      if (triggered) onOpen();
      startX = 0;
      startY = 0;
    };

    document.addEventListener('touchstart', handleStart, { passive: true });
    document.addEventListener('touchend', handleEnd, { passive: true });

    return () => {
      document.removeEventListener('touchstart', handleStart);
      document.removeEventListener('touchend', handleEnd);
    };
  }, [isOpen, disableSwipe, swipeAreaWidth, swipeThreshold, position, onOpen]);

  // Compute inline transform for interactive swipe feedback
  const transformAxis = position === 'left' || position === 'right' ? 'X' : 'Y';
  const needsTransform = translatePx !== 0;
  const transformStyle: React.CSSProperties = needsTransform
    ? {
        transform: `translate${transformAxis}(${translatePx}px)`,
        transition: 'none',
      }
    : {};

  return (
    <Drawer
      isOpen={isOpen}
      onClose={onClose}
      position={position}
      className={cn('gh-swipeable-drawer', className)}
      {...rest}
    >
      <div
        ref={(node) => {
          // merge refs
          (contentRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
          if (typeof swipeRef === 'function') swipeRef(node);
          else if (swipeRef) (swipeRef as React.MutableRefObject<HTMLDivElement | null>).current = node;
        }}
        style={{
          height: '100%',
          ...transformStyle,
          ...(sxToStyle(sx) ?? {}),
        }}
      >
        {/* Swipe handle / drag indicator for bottom sheets */}
        {position === 'bottom' && (
          <div
            style={{
              display: 'flex',
              justifyContent: 'center',
              padding: '8px 0 4px',
              cursor: 'grab',
            }}
            aria-hidden
          >
            <div
              style={{
                width: '32px',
                height: '4px',
                borderRadius: '2px',
                background: 'rgba(128,128,128,0.4)',
              }}
            />
          </div>
        )}
        {children}
      </div>
    </Drawer>
  );
}

SwipeableDrawer.displayName = 'SwipeableDrawer';
