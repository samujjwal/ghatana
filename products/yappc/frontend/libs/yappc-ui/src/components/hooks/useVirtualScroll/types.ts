/**
 * Virtual Scrolling Hook Types
 * Type definitions for virtual scrolling functionality
 */

/**
 * Virtual scroll item interface
 * Represents a single item in a virtualized list
 */
export interface VirtualScrollItem {
  /** Unique identifier for the item */
  id: string | number;
  /** Item height in pixels */
  height: number;
}

/**
 * Virtual scroll range interface
 * Defines the visible range of items
 */
export interface VirtualScrollRange {
  /** Index of the first visible item */
  startIndex: number;
  /** Index of the last visible item */
  endIndex: number;
  /** Offset from the top of the container in pixels */
  offset: number;
}

/**
 * Virtual scroll options interface
 * Configuration options for virtual scrolling
 */
export interface VirtualScrollOptions {
  /** Height of the container in pixels */
  containerHeight: number;
  /** Height of each item in pixels (can be variable) */
  itemHeight: number | ((index: number) => number);
  /** Number of items to render outside the visible range (buffer) */
  overscan?: number;
  /** Callback when scroll position changes */
  onScroll?: (range: VirtualScrollRange) => void;
  /** Enable debug logging */
  debug?: boolean;
}

/**
 * Virtual scroll state interface
 * Current state of the virtual scroll
 */
export interface VirtualScrollState {
  /** Current visible range */
  range: VirtualScrollRange;
  /** Total height of all items */
  totalHeight: number;
  /** Number of visible items */
  visibleCount: number;
  /** Current scroll position */
  scrollTop: number;
}

/**
 * Virtual scroll result interface
 * Return value from useVirtualScroll hook
 */
export interface VirtualScrollResult {
  /** Current virtual scroll state */
  state: VirtualScrollState;
  /** Ref to attach to the scroll container */
  containerRef: React.RefObject<HTMLDivElement>;
  /** Ref to attach to the inner content wrapper */
  innerRef: React.RefObject<HTMLDivElement>;
  /** Function to scroll to a specific index */
  scrollToIndex: (index: number) => void;
  /** Function to scroll to a specific offset */
  scrollToOffset: (offset: number) => void;
  /** Function to reset scroll position */
  resetScroll: () => void;
}
