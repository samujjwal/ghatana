import React, { useCallback, useState } from 'react';
import { useSwipeGesture, type SwipeDirection } from '../../hooks/useSwipeGesture';
import { useReducedMotion } from '../../hooks/useReducedMotion';

export interface SwipeableCardProps {
  /** Content to render inside the card */
  children: React.ReactNode;
  /** Called when card is swiped left (dismiss/delete action) */
  onSwipeLeft?: () => void;
  /** Called when card is swiped right (approve/archive action) */
  onSwipeRight?: () => void;
  /** Threshold percentage of card width to trigger action (default: 0.3 = 30%) */
  threshold?: number;
  /** Content to show when swiping left */
  leftActionContent?: React.ReactNode;
  /** Content to show when swiping right */
  rightActionContent?: React.ReactNode;
  /** Background color for left swipe action (default: red) */
  leftActionColor?: string;
  /** Background color for right swipe action (default: green) */
  rightActionColor?: string;
  /** Whether to allow swiping (default: true) */
  enabled?: boolean;
  /** Additional class name */
  className?: string;
}

/**
 * A card component that supports swipe gestures for mobile interactions.
 * Commonly used for list items that can be dismissed or actioned via swipe.
 * 
 * @example
 * ```tsx
 * <SwipeableCard
 *   onSwipeLeft={() => deleteItem(id)}
 *   onSwipeRight={() => archiveItem(id)}
 *   leftActionContent={<Trash2 className="w-6 h-6 text-white" />}
 *   rightActionContent={<Archive className="w-6 h-6 text-white" />}
 * >
 *   <div className="p-4">Item content</div>
 * </SwipeableCard>
 * ```
 */
export function SwipeableCard({
  children,
  onSwipeLeft,
  onSwipeRight,
  threshold = 0.3,
  leftActionContent,
  rightActionContent,
  leftActionColor = 'rgb(239, 68, 68)', // red-500
  rightActionColor = 'rgb(34, 197, 94)', // green-500
  enabled = true,
  className = '',
}: SwipeableCardProps) {
  const prefersReducedMotion = useReducedMotion();
  const [cardWidth, setCardWidth] = useState(0);
  const [isAnimatingOut, setIsAnimatingOut] = useState(false);
  const [exitDirection, setExitDirection] = useState<SwipeDirection | null>(null);

  const thresholdPx = cardWidth * threshold;

  const handleSwipeLeft = useCallback(() => {
    if (!onSwipeLeft || prefersReducedMotion) {
      onSwipeLeft?.();
      return;
    }
    
    setIsAnimatingOut(true);
    setExitDirection('left');
    
    // Wait for animation before calling callback
    setTimeout(() => {
      onSwipeLeft();
      setIsAnimatingOut(false);
      setExitDirection(null);
    }, 200);
  }, [onSwipeLeft, prefersReducedMotion]);

  const handleSwipeRight = useCallback(() => {
    if (!onSwipeRight || prefersReducedMotion) {
      onSwipeRight?.();
      return;
    }
    
    setIsAnimatingOut(true);
    setExitDirection('right');
    
    setTimeout(() => {
      onSwipeRight();
      setIsAnimatingOut(false);
      setExitDirection(null);
    }, 200);
  }, [onSwipeRight, prefersReducedMotion]);

  const { ref, state } = useSwipeGesture<HTMLDivElement>({
    onSwipeLeft: handleSwipeLeft,
    onSwipeRight: handleSwipeRight,
  }, {
    threshold: thresholdPx > 0 ? thresholdPx : 50,
    enabled: enabled && !isAnimatingOut,
    onSwipeStart: () => {
      if (ref.current) {
        setCardWidth(ref.current.offsetWidth);
      }
    },
  });

  // Calculate progress percentage
  const progress = cardWidth > 0 ? Math.min(Math.abs(state.deltaX) / (cardWidth * threshold), 1) : 0;
  
  // Determine action background color based on swipe direction
  const actionColor = state.deltaX > 0 ? rightActionColor : leftActionColor;
  const actionContent = state.deltaX > 0 ? rightActionContent : leftActionContent;

  // Calculate transform for exit animation
  const getTransform = () => {
    if (isAnimatingOut) {
      return exitDirection === 'left' 
        ? 'translateX(-100%)' 
        : 'translateX(100%)';
    }
    return `translateX(${state.deltaX}px)`;
  };

  // Transition only when not actively swiping
  const transition = !state.isSwiping || isAnimatingOut
    ? 'transform 200ms ease-out, opacity 200ms ease-out'
    : 'none';

  return (
    <div 
      className={`relative overflow-hidden rounded-lg ${className}`}
      style={{
        touchAction: enabled ? 'pan-y' : 'auto',
      }}
    >
      {/* Action background */}
      {(state.isSwiping || isAnimatingOut) && (
        <div
          className="absolute inset-0 flex items-center justify-center"
          style={{
            backgroundColor: actionColor,
            opacity: progress,
            justifyContent: state.deltaX > 0 ? 'flex-start' : 'flex-end',
            paddingLeft: state.deltaX > 0 ? '1rem' : 0,
            paddingRight: state.deltaX < 0 ? '1rem' : 0,
          }}
          aria-hidden="true"
        >
          <div
            className="transition-transform"
            style={{
              transform: `scale(${0.5 + progress * 0.5})`,
            }}
          >
            {actionContent}
          </div>
        </div>
      )}

      {/* Card content */}
      <div
        ref={ref}
        className="relative bg-white dark:bg-gray-800"
        style={{
          transform: getTransform(),
          transition: prefersReducedMotion ? 'none' : transition,
          opacity: isAnimatingOut ? 0 : 1,
        }}
      >
        {children}
      </div>
    </div>
  );
}

export default SwipeableCard;
