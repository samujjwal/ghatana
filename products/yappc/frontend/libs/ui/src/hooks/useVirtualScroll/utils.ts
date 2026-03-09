/**
 * Virtual Scrolling Utilities
 * Helper functions for virtual scroll calculations
 */

import type { VirtualScrollRange } from './types';

/**
 * Virtual scroll calculation utilities
 */
export class VirtualScrollUtils {
  /**
   * Calculate the total height of all items
   * @param itemCount - Total number of items
   * @param itemHeight - Height of each item (can be function for variable heights)
   * @returns Total height in pixels
   */
  static calculateTotalHeight(
    itemCount: number,
    itemHeight: number | ((index: number) => number)
  ): number {
    if (typeof itemHeight === 'number') {
      return itemCount * itemHeight;
    }

    let total = 0;
    for (let i = 0; i < itemCount; i++) {
      total += itemHeight(i);
    }
    return total;
  }

  /**
   * Calculate the visible range based on scroll position
   * @param scrollTop - Current scroll position
   * @param containerHeight - Height of the container
   * @param itemHeight - Height of each item
   * @param itemCount - Total number of items
   * @param overscan - Number of items to render outside visible range
   * @returns Virtual scroll range
   */
  static calculateRange(
    scrollTop: number,
    containerHeight: number,
    itemHeight: number | ((index: number) => number),
    itemCount: number,
    overscan: number = 3
  ): VirtualScrollRange {
    const isFixedHeight = typeof itemHeight === 'number';

    if (isFixedHeight) {
      const itemHeightNum = itemHeight as number;
      const startIndex = Math.max(0, Math.floor(scrollTop / itemHeightNum) - overscan);
      const endIndex = Math.min(
        itemCount - 1,
        Math.ceil((scrollTop + containerHeight) / itemHeightNum) + overscan
      );
      const offset = startIndex * itemHeightNum;

      return { startIndex, endIndex, offset };
    }

    // Variable height calculation
    const itemHeightFn = itemHeight as (index: number) => number;
    let accumulatedHeight = 0;
    let startIndex = 0;
    let endIndex = itemCount - 1;

    for (let i = 0; i < itemCount; i++) {
      const itemH = itemHeightFn(i);
      if (accumulatedHeight + itemH >= scrollTop && startIndex === 0) {
        startIndex = Math.max(0, i - overscan);
      }
      if (accumulatedHeight >= scrollTop + containerHeight) {
        endIndex = Math.min(itemCount - 1, i + overscan);
        break;
      }
      accumulatedHeight += itemH;
    }

    // Calculate offset for start index
    let offset = 0;
    for (let i = 0; i < startIndex; i++) {
      offset += itemHeightFn(i);
    }

    return { startIndex, endIndex, offset };
  }

  /**
   * Calculate scroll offset for a specific index
   * @param index - Item index to scroll to
   * @param itemHeight - Height of each item
   * @returns Scroll offset in pixels
   */
  static calculateOffsetForIndex(
    index: number,
    itemHeight: number | ((index: number) => number)
  ): number {
    if (typeof itemHeight === 'number') {
      return index * itemHeight;
    }

    let offset = 0;
    const itemHeightFn = itemHeight as (index: number) => number;
    for (let i = 0; i < index; i++) {
      offset += itemHeightFn(i);
    }
    return offset;
  }

  /**
   * Calculate the number of visible items
   * @param containerHeight - Height of the container
   * @param itemHeight - Height of each item
   * @returns Number of visible items
   */
  static calculateVisibleCount(
    containerHeight: number,
    itemHeight: number | ((index: number) => number)
  ): number {
    if (typeof itemHeight === 'number') {
      return Math.ceil(containerHeight / itemHeight);
    }

    // For variable heights, estimate based on average
    return Math.ceil(containerHeight / 50); // Default estimate
  }

  /**
   * Clamp scroll position within valid range
   * @param scrollTop - Current scroll position
   * @param maxScroll - Maximum scroll position
   * @returns Clamped scroll position
   */
  static clampScrollPosition(scrollTop: number, maxScroll: number): number {
    return Math.max(0, Math.min(scrollTop, maxScroll));
  }

  /**
   * Calculate max scroll position
   * @param totalHeight - Total height of all items
   * @param containerHeight - Height of the container
   * @returns Maximum scroll position
   */
  static calculateMaxScroll(totalHeight: number, containerHeight: number): number {
    return Math.max(0, totalHeight - containerHeight);
  }
}
