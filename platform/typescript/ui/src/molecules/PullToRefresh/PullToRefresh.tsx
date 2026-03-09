import React, { useState, useCallback, useRef, useEffect } from 'react';
import { useReducedMotion } from '../../hooks/useReducedMotion';
import { Loader2 } from 'lucide-react';

export interface PullToRefreshProps {
  /** Callback when refresh is triggered */
  onRefresh: () => Promise<void>;
  /** Content to render */
  children: React.ReactNode;
  /** Distance in pixels to pull before triggering refresh (default: 80) */
  pullThreshold?: number;
  /** Maximum pull distance (default: 120) */
  maxPullDistance?: number;
  /** Whether pull to refresh is enabled (default: true) */
  enabled?: boolean;
  /** Custom refresh indicator */
  refreshIndicator?: React.ReactNode;
  /** Text to show when pulling */
  pullText?: string;
  /** Text to show when release will trigger refresh */
  releaseText?: string;
  /** Text to show while refreshing */
  refreshingText?: string;
  /** Additional class name for the container */
  className?: string;
}

type PullState = 'idle' | 'pulling' | 'ready' | 'refreshing';

/**
 * Pull-to-refresh component for mobile-like refresh interaction.
 * Works with touch devices and provides visual feedback during pull.
 * 
 * @example
 * ```tsx
 * <PullToRefresh onRefresh={async () => await refetch()}>
 *   <div className="space-y-4">
 *     {items.map(item => <ItemCard key={item.id} {...item} />)}
 *   </div>
 * </PullToRefresh>
 * ```
 */
export function PullToRefresh({
  onRefresh,
  children,
  pullThreshold = 80,
  maxPullDistance = 120,
  enabled = true,
  refreshIndicator,
  pullText = 'Pull to refresh',
  releaseText = 'Release to refresh',
  refreshingText = 'Refreshing...',
  className = '',
}: PullToRefreshProps) {
  const prefersReducedMotion = useReducedMotion();
  const [pullState, setPullState] = useState<PullState>('idle');
  const [pullDistance, setPullDistance] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
  const startY = useRef<number>(0);
  const currentY = useRef<number>(0);

  const handleTouchStart = useCallback((e: TouchEvent) => {
    if (!enabled || pullState === 'refreshing') return;
    
    // Only activate if scrolled to top
    const container = containerRef.current;
    if (container && container.scrollTop > 0) return;

    startY.current = e.touches[0].clientY;
    currentY.current = startY.current;
  }, [enabled, pullState]);

  const handleTouchMove = useCallback((e: TouchEvent) => {
    if (!enabled || pullState === 'refreshing') return;
    if (startY.current === 0) return;

    currentY.current = e.touches[0].clientY;
    const deltaY = currentY.current - startY.current;

    // Only handle downward pull
    if (deltaY <= 0) {
      setPullDistance(0);
      setPullState('idle');
      return;
    }

    // Apply resistance to make it feel natural
    const resistance = 0.5;
    const adjustedDistance = Math.min(deltaY * resistance, maxPullDistance);
    
    setPullDistance(adjustedDistance);
    setPullState(adjustedDistance >= pullThreshold ? 'ready' : 'pulling');

    // Prevent default scroll when pulling
    if (adjustedDistance > 0) {
      e.preventDefault();
    }
  }, [enabled, pullState, maxPullDistance, pullThreshold]);

  const handleTouchEnd = useCallback(async () => {
    if (!enabled || pullState === 'refreshing') return;

    if (pullState === 'ready') {
      setPullState('refreshing');
      setPullDistance(pullThreshold);
      
      try {
        await onRefresh();
      } finally {
        setPullState('idle');
        setPullDistance(0);
      }
    } else {
      setPullState('idle');
      setPullDistance(0);
    }

    startY.current = 0;
    currentY.current = 0;
  }, [enabled, pullState, pullThreshold, onRefresh]);

  useEffect(() => {
    const container = containerRef.current;
    if (!container || !enabled) return;

    container.addEventListener('touchstart', handleTouchStart, { passive: true });
    container.addEventListener('touchmove', handleTouchMove, { passive: false });
    container.addEventListener('touchend', handleTouchEnd);
    container.addEventListener('touchcancel', handleTouchEnd);

    return () => {
      container.removeEventListener('touchstart', handleTouchStart);
      container.removeEventListener('touchmove', handleTouchMove);
      container.removeEventListener('touchend', handleTouchEnd);
      container.removeEventListener('touchcancel', handleTouchEnd);
    };
  }, [enabled, handleTouchStart, handleTouchMove, handleTouchEnd]);

  // Calculate progress percentage
  const progress = Math.min(pullDistance / pullThreshold, 1);

  // Determine status text
  const statusText = pullState === 'refreshing' 
    ? refreshingText 
    : pullState === 'ready' 
      ? releaseText 
      : pullText;

  // Default refresh indicator
  const defaultIndicator = (
    <div className="flex flex-col items-center gap-2">
      <div
        className={`transition-transform ${pullState === 'refreshing' ? 'animate-spin' : ''}`}
        style={{
          transform: pullState !== 'refreshing' 
            ? `rotate(${progress * 180}deg)` 
            : undefined,
        }}
      >
        <Loader2 className="w-6 h-6 text-primary-500" />
      </div>
      <span className="text-sm text-gray-500 dark:text-gray-400">
        {statusText}
      </span>
    </div>
  );

  return (
    <div
      ref={containerRef}
      className={`relative overflow-auto ${className}`}
      style={{ touchAction: enabled ? 'pan-x pan-down' : 'auto' }}
    >
      {/* Pull indicator */}
      <div
        className="absolute left-0 right-0 flex justify-center overflow-hidden transition-all"
        style={{
          height: pullDistance > 0 ? `${pullDistance}px` : 0,
          opacity: progress,
          transition: prefersReducedMotion || pullState === 'pulling' 
            ? 'none' 
            : 'height 200ms ease-out, opacity 200ms ease-out',
        }}
        aria-hidden="true"
      >
        <div
          className="flex items-center justify-center py-4"
          style={{
            transform: `translateY(${Math.max(0, pullDistance - 60)}px)`,
          }}
        >
          {refreshIndicator || defaultIndicator}
        </div>
      </div>

      {/* Content with transform */}
      <div
        style={{
          transform: `translateY(${pullDistance}px)`,
          transition: prefersReducedMotion || pullState === 'pulling'
            ? 'none'
            : 'transform 200ms ease-out',
        }}
      >
        {children}
      </div>
    </div>
  );
}

export default PullToRefresh;
