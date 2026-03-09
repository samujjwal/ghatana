/**
 * @ghatana/yappc-ide - Virtual Scrolling Utility
 * 
 * High-performance virtual scrolling for large lists and trees.
 * Optimized for file trees with 10K+ items.
 * 
 * @doc.type module
 * @doc.purpose Virtual scrolling utility for large datasets
 * @doc.layer product
 * @doc.pattern Utility Functions
 */

import React, { useState, useMemo, useCallback } from 'react';

/**
 * Virtual scroll configuration
 */
export interface VirtualScrollConfig {
  itemHeight: number;
  containerHeight: number;
  overscan: number;
  estimatedTotalHeight?: number;
}

/**
 * Virtual scroll item
 */
export interface VirtualItem {
  index: number;
  start: number;
  end: number;
  size: number;
  data: unknown;
}

/**
 * Virtual scroll state
 */
export interface VirtualScrollState {
  items: VirtualItem[];
  totalHeight: number;
  startIndex: number;
  endIndex: number;
  offsetY: number;
}

/**
 * Calculate virtual scroll state
 */
export function calculateVirtualScroll(
  scrollTop: number,
  config: VirtualScrollConfig,
  totalItems: number,
  getItemHeight?: (index: number) => number
): VirtualScrollState {
  const { itemHeight, containerHeight, overscan = 5 } = config;

  // Calculate visible range
  const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
  const endIndex = Math.min(
    totalItems - 1,
    Math.ceil((scrollTop + containerHeight) / itemHeight) + overscan
  );

  // Generate visible items
  const items: VirtualItem[] = [];
  let currentTop = startIndex * itemHeight;

  for (let i = startIndex; i <= endIndex; i++) {
    const height = getItemHeight ? getItemHeight(i) : itemHeight;

    items.push({
      index: i,
      start: currentTop,
      end: currentTop + height,
      size: height,
      data: null, // Will be populated by component
    });

    currentTop += height;
  }

  return {
    items,
    totalHeight: totalItems * itemHeight,
    startIndex,
    endIndex,
    offsetY: startIndex * itemHeight,
  };
}

/**
 * Virtual scroll hook for React
 */
export function useVirtualScroll(
  config: VirtualScrollConfig,
  totalItems: number,
  getItemHeight?: (index: number) => number
) {
  const [scrollTop, setScrollTop] = useState(0);

  const virtualState = useMemo(() => {
    return calculateVirtualScroll(scrollTop, config, totalItems, getItemHeight);
  }, [scrollTop, config, totalItems, getItemHeight]);

  const handleScroll = useCallback((event: React.UIEvent<HTMLDivElement>) => {
    setScrollTop(event.currentTarget.scrollTop);
  }, []);

  return {
    ...virtualState,
    handleScroll,
    scrollTop,
  };
}

/**
 * Tree virtual scrolling utilities
 */
export interface TreeNode {
  id: string;
  children: TreeNode[];
  expanded: boolean;
  depth: number;
  visible: boolean;
  data: unknown;
  parentId?: string;
}

/**
 * Flatten tree for virtual scrolling
 */
export function flattenTree(
  nodes: TreeNode[],
  expanded: Set<string> = new Set()
): TreeNode[] {
  const result: TreeNode[] = [];

  function traverse(nodes: TreeNode[], depth: number = 0) {
    for (const node of nodes) {
      const isExpanded = expanded.has(node.id);
      const isVisible = depth === 0 || expanded.has(node.parentId || '');

      if (isVisible) {
        result.push({
          ...node,
          depth,
          expanded: isExpanded,
          visible: true,
        });
      }

      if (isExpanded && node.children.length > 0) {
        traverse(node.children, depth + 1);
      }
    }
  }

  traverse(nodes);
  return result;
}

/**
 * Dynamic virtual scroll for variable height items
 */
export class DynamicVirtualScroll {
  private itemHeights: number[] = [];
  private itemPositions: number[] = [];
  private totalHeight = 0;

  constructor(private defaultHeight: number) { }

  /**
   * Set item height
   */
  setItemHeight(index: number, height: number) {
    const oldHeight = this.itemHeights[index] || this.defaultHeight;
    const heightDiff = height - oldHeight;

    this.itemHeights[index] = height;
    this.totalHeight += heightDiff;

    // Update positions for items after this one
    for (let i = index + 1; i < this.itemPositions.length; i++) {
      this.itemPositions[i] += heightDiff;
    }
  }

  /**
   * Get item position
   */
  getItemPosition(index: number): number {
    if (this.itemPositions[index] === undefined) {
      // Calculate position on demand
      let position = 0;
      for (let i = 0; i < index; i++) {
        position += this.itemHeights[i] || this.defaultHeight;
      }
      this.itemPositions[index] = position;
    }
    return this.itemPositions[index];
  }

  /**
   * Get item height if known
   */
  getItemHeight(index: number): number | undefined {
    return this.itemHeights[index];
  }

  /**
   * Get visible range
   */
  getVisibleRange(
    scrollTop: number,
    containerHeight: number,
    overscan: number = 5
  ): { start: number; end: number } {
    let start = 0;
    let end = this.itemHeights.length - 1;

    // Binary search for start index
    let left = 0;
    let right = this.itemHeights.length - 1;
    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      const position = this.getItemPosition(mid);

      if (position < scrollTop) {
        left = mid + 1;
      } else {
        right = mid - 1;
      }
    }

    start = Math.max(0, left - overscan);

    // Find end index
    const viewportBottom = scrollTop + containerHeight;
    left = start;
    right = this.itemHeights.length - 1;

    while (left <= right) {
      const mid = Math.floor((left + right) / 2);
      const position = this.getItemPosition(mid);

      if (position < viewportBottom) {
        left = mid + 1;
      } else {
        right = mid - 1;
      }
    }

    end = Math.min(this.itemHeights.length - 1, left + overscan);

    return { start, end };
  }

  /**
   * Get total height
   */
  getTotalHeight(): number {
    if (this.totalHeight === 0 && this.itemHeights.length === 0) {
      return 0;
    }

    if (this.totalHeight === 0) {
      this.totalHeight = this.itemHeights.reduce(
        (sum, height) => sum + (height || this.defaultHeight),
        0
      );
    }

    return this.totalHeight;
  }
}

/**
 * React hook for dynamic virtual scrolling
 */
export function useDynamicVirtualScroll(
  defaultHeight: number,
  containerHeight: number,
  _totalItems: number
) {
  const [scrollTop, setScrollTop] = useState(0);
  const virtualScroll = useMemo(() => {
    return new DynamicVirtualScroll(defaultHeight);
  }, [defaultHeight]);

  const visibleRange = useMemo(() => {
    return virtualScroll.getVisibleRange(scrollTop, containerHeight);
  }, [virtualScroll, scrollTop, containerHeight]);

  const items = useMemo(() => {
    const result: VirtualItem[] = [];

    for (let i = visibleRange.start; i <= visibleRange.end; i++) {
      result.push({
        index: i,
        start: virtualScroll.getItemPosition(i),
        end: 0, // Will be calculated when height is known
        size: virtualScroll.getItemHeight(i) || defaultHeight,
        data: null,
      });
    }

    return result;
  }, [visibleRange, virtualScroll, defaultHeight]);

  const handleScroll = useCallback((event: React.UIEvent<HTMLDivElement>) => {
    setScrollTop(event.currentTarget.scrollTop);
  }, []);

  const setItemHeight = useCallback((index: number, height: number) => {
    virtualScroll.setItemHeight(index, height);
  }, [virtualScroll]);

  return {
    items,
    totalHeight: virtualScroll.getTotalHeight(),
    handleScroll,
    setItemHeight,
    scrollTop,
  };
}
