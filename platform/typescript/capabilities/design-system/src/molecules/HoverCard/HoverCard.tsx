import { ReactNode, useState, useRef, useEffect } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion';

export interface HoverCardProps {
  /** Trigger element */
  trigger: ReactNode;
  /** Card content */
  children: ReactNode;
  /** Side to show card */
  side?: 'top' | 'bottom' | 'left' | 'right';
  /** Alignment */
  align?: 'start' | 'center' | 'end';
  /** Delay before showing (ms) */
  openDelay?: number;
  /** Delay before hiding (ms) */
  closeDelay?: number;
  /** Custom class for card */
  className?: string;
  /** Disable hover (for touch devices) */
  disabled?: boolean;
}

/**
 * Hover Card Component
 * 
 * Shows additional content on hover with smooth animations.
 * Respects reduced motion preference.
 * 
 * @doc.type component
 * @doc.purpose Hover-triggered content card
 * @doc.layer core
 * @doc.pattern Interaction Component
 * 
 * @example
 * ```tsx
 * <HoverCard
 *   trigger={<span>Hover me</span>}
 *   side="bottom"
 * >
 *   <div className="p-4">
 *     <h4>Card Title</h4>
 *     <p>Card content...</p>
 *   </div>
 * </HoverCard>
 * ```
 */
export function HoverCard({
  trigger,
  children,
  side = 'bottom',
  align = 'center',
  openDelay = 200,
  closeDelay = 150,
  className = '',
  disabled = false,
}: HoverCardProps) {
  const [isOpen, setIsOpen] = useState(false);
  const [position, setPosition] = useState({ top: 0, left: 0 });
  const triggerRef = useRef<HTMLDivElement>(null);
  const cardRef = useRef<HTMLDivElement>(null);
  const openTimerRef = useRef<number | null>(null);
  const closeTimerRef = useRef<number | null>(null);
  const prefersReducedMotion = useReducedMotion();

  const clearTimers = () => {
    if (openTimerRef.current) {
      clearTimeout(openTimerRef.current);
      openTimerRef.current = null;
    }
    if (closeTimerRef.current) {
      clearTimeout(closeTimerRef.current);
      closeTimerRef.current = null;
    }
  };

  const handleMouseEnter = () => {
    if (disabled) return;
    clearTimers();
    openTimerRef.current = window.setTimeout(() => {
      setIsOpen(true);
    }, prefersReducedMotion ? 0 : openDelay);
  };

  const handleMouseLeave = () => {
    clearTimers();
    closeTimerRef.current = window.setTimeout(() => {
      setIsOpen(false);
    }, prefersReducedMotion ? 0 : closeDelay);
  };

  useEffect(() => {
    if (isOpen && triggerRef.current && cardRef.current) {
      const triggerRect = triggerRef.current.getBoundingClientRect();
      const cardRect = cardRef.current.getBoundingClientRect();
      const spacing = 8;

      let top = 0;
      let left = 0;

      // Calculate position based on side
      switch (side) {
        case 'top':
          top = triggerRect.top - cardRect.height - spacing;
          break;
        case 'bottom':
          top = triggerRect.bottom + spacing;
          break;
        case 'left':
          left = triggerRect.left - cardRect.width - spacing;
          top = triggerRect.top;
          break;
        case 'right':
          left = triggerRect.right + spacing;
          top = triggerRect.top;
          break;
      }

      // Calculate alignment
      if (side === 'top' || side === 'bottom') {
        switch (align) {
          case 'start':
            left = triggerRect.left;
            break;
          case 'center':
            left = triggerRect.left + triggerRect.width / 2 - cardRect.width / 2;
            break;
          case 'end':
            left = triggerRect.right - cardRect.width;
            break;
        }
      } else {
        switch (align) {
          case 'start':
            top = triggerRect.top;
            break;
          case 'center':
            top = triggerRect.top + triggerRect.height / 2 - cardRect.height / 2;
            break;
          case 'end':
            top = triggerRect.bottom - cardRect.height;
            break;
        }
      }

      // Ensure card stays within viewport
      const viewportWidth = window.innerWidth;
      const viewportHeight = window.innerHeight;

      if (left < 8) left = 8;
      if (left + cardRect.width > viewportWidth - 8) {
        left = viewportWidth - cardRect.width - 8;
      }
      if (top < 8) top = 8;
      if (top + cardRect.height > viewportHeight - 8) {
        top = viewportHeight - cardRect.height - 8;
      }

      setPosition({ top, left });
    }
  }, [isOpen, side, align]);

  useEffect(() => {
    return clearTimers;
  }, []);

  const animationClass = prefersReducedMotion
    ? ''
    : 'animate-fadeIn';

  return (
    <>
      {/* Trigger */}
      <div
        ref={triggerRef}
        onMouseEnter={handleMouseEnter}
        onMouseLeave={handleMouseLeave}
        onFocus={handleMouseEnter}
        onBlur={handleMouseLeave}
        className="inline-block"
      >
        {trigger}
      </div>

      {/* Card */}
      {isOpen && (
        <div
          ref={cardRef}
          onMouseEnter={handleMouseEnter}
          onMouseLeave={handleMouseLeave}
          className={`fixed z-50 bg-white dark:bg-gray-800 rounded-lg shadow-lg border border-gray-200 dark:border-gray-700 ${animationClass} ${className}`}
          style={{
            top: `${position.top}px`,
            left: `${position.left}px`,
          }}
          role="tooltip"
        >
          {children}
        </div>
      )}
    </>
  );
}

export default HoverCard;
