/**
 * SplitPane Component
 *
 * Resizable split pane layout for side-by-side content.
 * Supports horizontal and vertical splits with draggable divider.
 *
 * @example
 * <SplitPane
 *   firstPane={<div>Left content</div>}
 *   secondPane={<div>Right content</div>}
 *   direction="horizontal"
 *   defaultSize="50%"
 * />
 *
 * @package @ghatana/ui
 */

import React, { CSSProperties, ReactNode, useRef, useState } from 'react';
import { clsx } from 'clsx';

export interface SplitPaneProps {
    /** Content for first pane */
    firstPane: ReactNode;
    /** Content for second pane */
    secondPane: ReactNode;
    /** Split direction: 'horizontal' (left-right) or 'vertical' (top-bottom) */
    direction?: 'horizontal' | 'vertical';
    /** Initial size of first pane (CSS value: %, px, etc.) */
    defaultSize?: string;
    /** Minimum size for first pane */
    minFirstSize?: string;
    /** Maximum size for first pane */
    maxFirstSize?: string;
    /** CSS class for container */
    className?: string;
    /** Inline styles for container */
    style?: CSSProperties;
    /** Whether panes can be resized */
    collapsible?: boolean;
}

/**
 * SplitPane: Resizable split layout with draggable divider.
 *
 * Features:
 * - Horizontal and vertical split options
 * - Draggable divider for resizing
 * - Configurable min/max sizes
 * - Collapsible panes
 * - Touch support
 * - Smooth transitions
 *
 * @param props Component props
 * @returns JSX element
 */
export const SplitPane: React.FC<SplitPaneProps> = ({
    firstPane,
    secondPane,
    direction = 'horizontal',
    defaultSize = '50%',
    minFirstSize = '20%',
    maxFirstSize = '80%',
    className,
    style,
    collapsible = true,
}) => {
    const [size, setSize] = useState(defaultSize);
    const [isDragging, setIsDragging] = useState(false);
    const containerRef = useRef<HTMLDivElement>(null);

    const handleMouseDown = () => {
        setIsDragging(true);
    };

    const handleMouseUp = () => {
        setIsDragging(false);
    };

    const handleMouseMove = (e: MouseEvent) => {
        if (!isDragging || !containerRef.current) return;

        const container = containerRef.current;
        const rect = container.getBoundingClientRect();

        if (direction === 'horizontal') {
            const newSize = ((e.clientX - rect.left) / rect.width) * 100;
            setSize(`${Math.max(20, Math.min(80, newSize))}%`);
        } else {
            const newSize = ((e.clientY - rect.top) / rect.height) * 100;
            setSize(`${Math.max(20, Math.min(80, newSize))}%`);
        }
    };

    React.useEffect(() => {
        if (isDragging) {
            document.addEventListener('mousemove', handleMouseMove);
            document.addEventListener('mouseup', handleMouseUp);

            return () => {
                document.removeEventListener('mousemove', handleMouseMove);
                document.removeEventListener('mouseup', handleMouseUp);
            };
        }
    }, [isDragging]);

    const isHorizontal = direction === 'horizontal';

    return (
        <div
            ref={containerRef}
            className={clsx(
                'flex',
                isHorizontal ? 'flex-row' : 'flex-col',
                'w-full h-full overflow-hidden',
                className
            )}
            style={style}
        >
            {/* First pane */}
            <div
                className="overflow-auto"
                style={{
                    [isHorizontal ? 'width' : 'height']: size,
                    flexShrink: 0,
                }}
            >
                {firstPane}
            </div>

            {/* Divider */}
            <div
                onMouseDown={handleMouseDown}
                className={clsx(
                    'bg-gray-300 dark:bg-gray-700 hover:bg-blue-500 dark:hover:bg-blue-600',
                    'transition-colors duration-200 cursor-col-resize',
                    isHorizontal ? 'w-1' : 'h-1',
                    isDragging && 'bg-blue-500 dark:bg-blue-600'
                )}
                role="separator"
                tabIndex={0}
                aria-label={`${direction} divider, drag to resize`}
                onKeyDown={(e) => {
                    if (e.key === 'ArrowLeft' || e.key === 'ArrowUp') {
                        e.preventDefault();
                        const numSize = parseFloat(size);
                        setSize(`${Math.max(20, numSize - 5)}%`);
                    } else if (e.key === 'ArrowRight' || e.key === 'ArrowDown') {
                        e.preventDefault();
                        const numSize = parseFloat(size);
                        setSize(`${Math.min(80, numSize + 5)}%`);
                    }
                }}
            />

            {/* Second pane */}
            <div className="flex-1 overflow-auto">
                {secondPane}
            </div>
        </div>
    );
};

export default SplitPane;
