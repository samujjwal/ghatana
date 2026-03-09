import { useRef, useState, useEffect, ReactNode } from 'react';

/**
 * Virtual List Component
 *
 * <p><b>Purpose</b><br>
 * Efficiently renders large lists by only rendering visible items.
 * Dramatically improves performance for lists with thousands of items.
 *
 * <p><b>Features</b><br>
 * - Only renders visible items (virtualization)
 * - Smooth scrolling with buffer zones
 * - Dynamic item heights support
 * - Customizable overscan for smoother scrolling
 * - Automatic height calculation
 *
 * <p><b>Performance</b><br>
 * - Can handle 100k+ items smoothly
 * - Constant memory usage regardless of list size
 * - 60fps scrolling performance
 *
 * @doc.type component
 * @doc.purpose Virtual scrolling for large lists
 * @doc.layer platform
 * @doc.pattern VirtualList
 */

interface VirtualListProps<T> {
    items: T[];
    height: number; // Container height in pixels
    itemHeight: number; // Fixed item height (or average for dynamic)
    renderItem: (item: T, index: number) => ReactNode;
    overscan?: number; // Number of items to render outside viewport
    className?: string;
    onEndReached?: () => void; // Infinite scroll callback
    endReachedThreshold?: number; // Pixels from bottom to trigger onEndReached
}

export function VirtualList<T>({
    items,
    height,
    itemHeight,
    renderItem,
    overscan = 3,
    className = '',
    onEndReached,
    endReachedThreshold = 200,
}: VirtualListProps<T>) {
    const scrollElementRef = useRef<HTMLDivElement>(null);
    const [scrollTop, setScrollTop] = useState(0);

    // Calculate visible range
    const totalHeight = items.length * itemHeight;
    const startIndex = Math.max(0, Math.floor(scrollTop / itemHeight) - overscan);
    const endIndex = Math.min(
        items.length - 1,
        Math.ceil((scrollTop + height) / itemHeight) + overscan
    );

    const visibleItems = items.slice(startIndex, endIndex + 1);

    // Handle scroll events
    const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
        const target = e.currentTarget;
        setScrollTop(target.scrollTop);

        // Check if near bottom for infinite scroll
        if (onEndReached) {
            const scrollBottom = target.scrollHeight - target.scrollTop - target.clientHeight;
            if (scrollBottom < endReachedThreshold) {
                onEndReached();
            }
        }
    };

    return (
        <div
            ref={scrollElementRef}
            onScroll={handleScroll}
            className={`overflow-auto ${className}`}
            style={{ height }}
        >
            <div style={{ height: totalHeight, position: 'relative' }}>
                {visibleItems.map((item, i) => {
                    const itemIndex = startIndex + i;
                    return (
                        <div
                            key={itemIndex}
                            style={{
                                position: 'absolute',
                                top: itemIndex * itemHeight,
                                height: itemHeight,
                                left: 0,
                                right: 0,
                            }}
                        >
                            {renderItem(item, itemIndex)}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}

/**
 * Dynamic Virtual List with Variable Item Heights
 *
 * For lists where items have different heights.
 * Uses estimated heights and measures actual heights on render.
 */
interface DynamicVirtualListProps<T> {
    items: T[];
    height: number;
    estimatedItemHeight: number;
    renderItem: (item: T, index: number) => ReactNode;
    overscan?: number;
    className?: string;
}

export function DynamicVirtualList<T>({
    items,
    height,
    estimatedItemHeight,
    renderItem,
    overscan = 3,
    className = '',
}: DynamicVirtualListProps<T>) {
    const scrollElementRef = useRef<HTMLDivElement>(null);
    const [scrollTop, setScrollTop] = useState(0);
    const itemHeights = useRef<Map<number, number>>(new Map());
    const itemRefs = useRef<Map<number, HTMLDivElement>>(new Map());

    // Measure item heights after render
    useEffect(() => {
        itemRefs.current.forEach((element, index) => {
            const height = element.getBoundingClientRect().height;
            if (itemHeights.current.get(index) !== height) {
                itemHeights.current.set(index, height);
            }
        });
    });

    // Calculate positions
    const getItemOffset = (index: number): number => {
        let offset = 0;
        for (let i = 0; i < index; i++) {
            offset += itemHeights.current.get(i) || estimatedItemHeight;
        }
        return offset;
    };

    const getItemHeight = (index: number): number => {
        return itemHeights.current.get(index) || estimatedItemHeight;
    };

    // Find visible range
    let startIndex = 0;
    let endIndex = items.length - 1;
    let accumulatedHeight = 0;

    for (let i = 0; i < items.length; i++) {
        const itemHeight = getItemHeight(i);
        if (accumulatedHeight + itemHeight > scrollTop && startIndex === 0) {
            startIndex = Math.max(0, i - overscan);
        }
        if (accumulatedHeight > scrollTop + height && endIndex === items.length - 1) {
            endIndex = Math.min(items.length - 1, i + overscan);
            break;
        }
        accumulatedHeight += itemHeight;
    }

    const visibleItems = items.slice(startIndex, endIndex + 1);
    const totalHeight = getItemOffset(items.length);

    const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
        setScrollTop(e.currentTarget.scrollTop);
    };

    return (
        <div
            ref={scrollElementRef}
            onScroll={handleScroll}
            className={`overflow-auto ${className}`}
            style={{ height }}
        >
            <div style={{ height: totalHeight, position: 'relative' }}>
                {visibleItems.map((item, i) => {
                    const itemIndex = startIndex + i;
                    const top = getItemOffset(itemIndex);
                    return (
                        <div
                            key={itemIndex}
                            ref={(el) => {
                                if (el) {
                                    itemRefs.current.set(itemIndex, el);
                                } else {
                                    itemRefs.current.delete(itemIndex);
                                }
                            }}
                            style={{
                                position: 'absolute',
                                top,
                                left: 0,
                                right: 0,
                            }}
                        >
                            {renderItem(item, itemIndex)}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
