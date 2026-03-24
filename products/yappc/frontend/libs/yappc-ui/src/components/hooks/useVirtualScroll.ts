/**
 * Re-export wrapper for useVirtualScroll hook
 * Maintains backward compatibility with original import path
 */

export { useVirtualScroll } from './useVirtualScroll/index';
export type {
  VirtualScrollItem,
  VirtualScrollRange,
  VirtualScrollOptions,
  VirtualScrollState,
  VirtualScrollResult,
} from './useVirtualScroll/types';
