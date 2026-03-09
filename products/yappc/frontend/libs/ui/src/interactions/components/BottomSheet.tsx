/**
 * BottomSheet Component
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 * 
 * Mobile-optimized bottom sheet with snap points and swipe gestures.
 * Touch-friendly with smooth animations and haptic-like feedback.
 * 
 * @module interactions/components/BottomSheet
 */

import { motion, useMotionValue, useTransform, AnimatePresence, type PanInfo } from 'framer-motion';
import { useState, useEffect, useCallback, useRef, type FC } from 'react';

import type { BottomSheetProps, SnapPoint } from '../types';

// ============================================================================
// Utilities
// ============================================================================

/**
 * Calculate snap point in pixels from viewport height
 */
function calculateSnapPoint(snapPoint: SnapPoint, viewportHeight: number): number {
    if (typeof snapPoint === 'number') {
        return snapPoint;
    }

    // Parse percentage string (e.g., "50%" => 0.5 * viewportHeight)
    const percentage = parseFloat(snapPoint) / 100;
    return percentage * viewportHeight;
}

/**
 * Find nearest snap point to current position
 */
function findNearestSnapPoint(
    currentY: number,
    snapPoints: number[],
    velocity: number
): number {
    // If velocity is significant, snap in direction of velocity
    if (Math.abs(velocity) > 500) {
        if (velocity > 0) {
            // Dragging down - find next lower snap point
            const lower = snapPoints.filter(point => point > currentY);
            if (lower.length > 0) {
                return Math.min(...lower);
            }
        } else {
            // Dragging up - find next higher snap point
            const higher = snapPoints.filter(point => point < currentY);
            if (higher.length > 0) {
                return Math.max(...higher);
            }
        }
    }

    // Otherwise, find closest snap point
    let nearestPoint = snapPoints[0];
    let minDistance = Math.abs(currentY - snapPoints[0]);

    for (const point of snapPoints) {
        const distance = Math.abs(currentY - point);
        if (distance < minDistance) {
            minDistance = distance;
            nearestPoint = point;
        }
    }

    return nearestPoint;
}

// ============================================================================
// BottomSheet Component
// ============================================================================

/**
 * Bottom sheet component with snap points
 * 
 * @example
 * ```tsx
 * const [isOpen, setIsOpen] = useState(false);
 * 
 * return (
 *   <>
 *     <button onClick={() => setIsOpen(true)}>Open</button>
 *     <BottomSheet
 *       open={isOpen}
 *       onClose={() => setIsOpen(false)}
 *       snapPoints={['25%', '50%', '90%']}
 *       initialSnap={1}
 *     >
 *       <h2>Bottom Sheet</h2>
 *       <p>Swipe to adjust height or dismiss.</p>
 *     </BottomSheet>
 *   </>
 * );
 * ```
 */
export const BottomSheet: FC<BottomSheetProps> = ({
    open,
    onClose,
    snapPoints = ['50%', '90%'],
    defaultSnap = 0,
    children,
    closeOnBackdrop = true,
    swipeToClose = true,
    showHandle = true,
    onSnapPointChange,
}) => {
    const [currentSnapIndex, setCurrentSnapIndex] = useState(defaultSnap);
    const [viewportHeight, setViewportHeight] = useState(
        typeof window !== 'undefined' ? window.innerHeight : 0
    );
    const contentRef = useRef<HTMLDivElement>(null);

    // Calculate snap points in pixels
    const snapPointsPx = snapPoints.map(point => {
        if (typeof point === 'number') {
            // If < 1, treat as fraction of viewport height
            if (point < 1) {
                return point * viewportHeight;
            }
            // Otherwise, treat as pixel value
            return point;
        }
        // Parse percentage string (e.g., "50%" => 0.5 * viewportHeight)
        const percentage = parseFloat(point) / 100;
        return percentage * viewportHeight;
    });

    // Motion values for drag
    const y = useMotionValue(0);
    const opacity = useTransform(y, [0, 300], [1, 0]);

    // Update viewport height on resize
    useEffect(() => {
        if (typeof window === 'undefined') return;

        const handleResize = () => {
            setViewportHeight(window.innerHeight);
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, []);

    // Reset snap on open
    useEffect(() => {
        if (open) {
            setCurrentSnapIndex(defaultSnap);
            y.set(0);
        }
    }, [open, defaultSnap, y]);

    // Lock body scroll
    useEffect(() => {
        if (!open) return;

        const originalOverflow = document.body.style.overflow;
        const originalPaddingRight = document.body.style.paddingRight;

        // Calculate scrollbar width
        const scrollbarWidth = window.innerWidth - document.documentElement.clientWidth;

        document.body.style.overflow = 'hidden';
        if (scrollbarWidth > 0) {
            document.body.style.paddingRight = `${scrollbarWidth}px`;
        }

        return () => {
            document.body.style.overflow = originalOverflow;
            document.body.style.paddingRight = originalPaddingRight;
        };
    }, [open]);

    // Handle backdrop click
    const handleBackdropClick = useCallback(() => {
        if (closeOnBackdrop) {
            onClose();
        }
    }, [closeOnBackdrop, onClose]);

    // Handle drag end
    const handleDragEnd = useCallback(
        (_event: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
            const currentY = info.offset.y;
            const velocity = info.velocity.y;

            // Check if should close
            if (swipeToClose && currentY > 150 && velocity > 0) {
                onClose();
                return;
            }

            // Find nearest snap point
            const targetSnap = findNearestSnapPoint(currentY, snapPointsPx, velocity);
            const newSnapIndex = snapPointsPx.indexOf(targetSnap);

            setCurrentSnapIndex(newSnapIndex);
            onSnapPointChange?.(newSnapIndex);

            // Animate to snap point
            y.set(0);
        },
        [swipeToClose, snapPointsPx, y, onClose, onSnapPointChange]
    );

    // Calculate current height based on snap point
    const currentHeight = snapPointsPx[currentSnapIndex] || snapPointsPx[0];

    if (!open) {
        return null;
    }

    return (
        <div className="fixed inset-0 z-[1400] pointer-events-none">
            <AnimatePresence mode="wait">
                {open && (
                    <>
                        {/* Backdrop */}
                        <motion.div
                            className="absolute inset-0 bg-black/50 pointer-events-auto"
                            initial={{ opacity: 0 }}
                            animate={{ opacity: 1 }}
                            exit={{ opacity: 0 }}
                            transition={{ duration: 0.2 }}
                            onClick={handleBackdropClick}
                            style={{ opacity }}
                            aria-hidden="true"
                        />

                        {/* Bottom Sheet */}
                        <motion.div
                            className="absolute left-0 right-0 bottom-0 bg-white dark:bg-gray-900 rounded-t-2xl shadow-2xl overflow-hidden flex flex-col pointer-events-auto max-h-[90vh] touch-none"
                            style={{
                                y,
                                height: currentHeight,
                            }}
                            drag="y"
                            dragConstraints={{ top: 0, bottom: 0 }}
                            dragElastic={0.2}
                            onDragEnd={handleDragEnd}
                            initial={{ y: currentHeight }}
                            animate={{ y: 0 }}
                            exit={{ y: currentHeight }}
                            transition={{
                                type: 'spring',
                                stiffness: 300,
                                damping: 30,
                            }}
                            role="dialog"
                            aria-modal="true"
                        >
                            {showHandle && (
                                <div className="w-8 h-1 bg-gray-300 dark:bg-gray-600 rounded-sm mx-auto my-2 shrink-0 cursor-grab active:cursor-grabbing" />
                            )}

                            <div ref={contentRef} className="flex-1 overflow-y-auto overflow-x-hidden [-webkit-overflow-scrolling:touch]">
                                {children}
                            </div>
                        </motion.div>
                    </>
                )}
            </AnimatePresence>
        </div>
    );
};

BottomSheet.displayName = 'BottomSheet';
