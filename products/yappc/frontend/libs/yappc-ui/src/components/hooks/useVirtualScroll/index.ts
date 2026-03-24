/**
 * useVirtualScroll Hook
 * Efficient rendering of large lists using virtual scrolling
 *
 * @example
 * ```tsx
 * const { state, containerRef, innerRef, scrollToIndex } = useVirtualScroll({
 *   containerHeight: 600,
 *   itemHeight: 50,
 *   overscan: 5,
 * });
 *
 * return (
 *   <div ref={containerRef} style={{ height: 600, overflow: 'auto' }}>
 *     <div ref={innerRef} style={{ height: state.totalHeight }}>
 *       {items.slice(state.range.startIndex, state.range.endIndex + 1).map((item) => (
 *         <div key={item.id} style={{ transform: `translateY(${state.range.offset}px)` }}>
 *           {item.content}
 *         </div>
 *       ))}
 *     </div>
 *   </div>
 * );
 * ```
 */

import { useRef, useState, useCallback, useEffect } from 'react';

import { VirtualScrollUtils } from './utils';

import type { VirtualScrollOptions, VirtualScrollResult, VirtualScrollState } from './types';

/**
 * Hook for efficient rendering of large lists using virtual scrolling
 * @param options - Virtual scroll configuration options
 * @returns Virtual scroll result with state and utility functions
 */
export function useVirtualScroll(options: VirtualScrollOptions): VirtualScrollResult {
  const {
    containerHeight,
    itemHeight,
    overscan = 3,
    onScroll,
    debug = false,
  } = options;

  const containerRef = useRef<HTMLDivElement>(null);
  const innerRef = useRef<HTMLDivElement>(null);
  const [scrollTop, setScrollTop] = useState(0);
  const [itemCount, setItemCount] = useState(0);

  // Calculate total height
  const totalHeight = VirtualScrollUtils.calculateTotalHeight(itemCount, itemHeight);

  // Calculate visible range
  const range = VirtualScrollUtils.calculateRange(
    scrollTop,
    containerHeight,
    itemHeight,
    itemCount,
    overscan
  );

  // Calculate visible count
  const visibleCount = VirtualScrollUtils.calculateVisibleCount(containerHeight, itemHeight);

  // Current state
  const state: VirtualScrollState = {
    range,
    totalHeight,
    visibleCount,
    scrollTop,
  };

  /**
   * Handle scroll event
   */
  const handleScroll = useCallback(
    (event: Event) => {
      const target = event.target as HTMLDivElement;
      const newScrollTop = target.scrollTop;
      setScrollTop(newScrollTop);

      if (debug) {
        console.warn('[VirtualScroll] Scroll:', { scrollTop: newScrollTop, range });
      }

      if (onScroll) {
        onScroll(range);
      }
    },
    [range, debug, onScroll]
  );

  /**
   * Scroll to specific offset
   */
  const scrollToOffset = useCallback((offset: number) => {
    if (containerRef.current) {
      const maxScroll = VirtualScrollUtils.calculateMaxScroll(totalHeight, containerHeight);
      const clampedOffset = VirtualScrollUtils.clampScrollPosition(offset, maxScroll);
      containerRef.current.scrollTop = clampedOffset;
    }
  }, [totalHeight, containerHeight]);

  /**
   * Scroll to specific index
   */
  const scrollToIndex = useCallback(
    (index: number) => {
      const offset = VirtualScrollUtils.calculateOffsetForIndex(index, itemHeight);
      scrollToOffset(offset);
    },
    [itemHeight, scrollToOffset]
  );

  /**
   * Reset scroll position
   */
  const resetScroll = useCallback(() => {
    if (containerRef.current) {
      containerRef.current.scrollTop = 0;
    }
  }, []);

  /**
   * Attach scroll listener
   */
  useEffect(() => {
    const container = containerRef.current;
    if (container) {
      container.addEventListener('scroll', handleScroll);
      return () => container.removeEventListener('scroll', handleScroll);
    }
  }, [handleScroll]);

  /**
   * Update item count from DOM
   */
  useEffect(() => {
    if (innerRef.current) {
      const count = innerRef.current.children.length;
      setItemCount(count);
    }
  }, []);

  return {
    state,
    containerRef: containerRef as React.RefObject<HTMLDivElement>,
    innerRef: innerRef as React.RefObject<HTMLDivElement>,
    scrollToIndex,
    scrollToOffset,
    resetScroll,
  };
}
