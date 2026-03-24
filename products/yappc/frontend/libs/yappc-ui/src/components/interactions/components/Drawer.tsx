/**
 * Drawer Component
 * Phase 5: Days 45-46 - Advanced Interaction Patterns
 * 
 * Side panel drawer with smooth animations.
 * Supports multiple anchor positions and variants (temporary, persistent, permanent).
 * 
 * @module interactions/components/Drawer
 */

import { motion, AnimatePresence, type PanInfo } from 'framer-motion';
import { useEffect, useCallback, useRef, type FC } from 'react';

import type { DrawerProps, DrawerAnchor, DrawerVariant } from '../types';

// ============================================================================
// Helper Components with Tailwind
// ============================================================================

/** Get position/size styles for the drawer paper based on anchor */
function getDrawerPaperStyle(
    anchor: DrawerAnchor,
    width?: string | number,
    height?: string | number,
): React.CSSProperties {
    const isHorizontal = anchor === 'left' || anchor === 'right';
    const base: React.CSSProperties = {
        position: 'absolute',
        display: 'flex',
        flexDirection: 'column',
        overflowY: 'auto',
        pointerEvents: 'auto',
    };

    if (anchor === 'left') {
        return { ...base, top: 0, left: 0, bottom: 0, width: width || 280 };
    } else if (anchor === 'right') {
        return { ...base, top: 0, right: 0, bottom: 0, width: width || 280 };
    } else if (anchor === 'top') {
        return { ...base, top: 0, left: 0, right: 0, height: height || 280 };
    } else {
        return { ...base, bottom: 0, left: 0, right: 0, height: height || 280 };
    }
}

// ============================================================================
// Animation Variants
// ============================================================================

/**
 *
 */
function getDrawerVariants(anchor: DrawerAnchor) {
    const variants = {
        left: {
            hidden: { x: '-100%' },
            visible: { x: 0 },
            exit: { x: '-100%' },
        },
        right: {
            hidden: { x: '100%' },
            visible: { x: 0 },
            exit: { x: '100%' },
        },
        top: {
            hidden: { y: '-100%' },
            visible: { y: 0 },
            exit: { y: '-100%' },
        },
        bottom: {
            hidden: { y: '100%' },
            visible: { y: 0 },
            exit: { y: '100%' },
        },
    };

    return variants[anchor];
}

const backdropVariants = {
    hidden: { opacity: 0 },
    visible: { opacity: 1 },
    exit: { opacity: 0 },
};

// ============================================================================
// Drawer Component
// ============================================================================

/**
 * Drawer component with smooth animations
 * 
 * @example
 * ```tsx
 * const [isOpen, setIsOpen] = useState(false);
 * 
 * return (
 *   <>
 *     <button onClick={() => setIsOpen(true)}>Open Drawer</button>
 *     <Drawer
 *       open={isOpen}
 *       onClose={() => setIsOpen(false)}
 *       anchor="left"
 *     >
 *       <h2>Drawer Content</h2>
 *       <p>This is the drawer content.</p>
 *     </Drawer>
 *   </>
 * );
 * ```
 */
export const InteractionDrawer: FC<DrawerProps> = ({
    open,
    onClose,
    anchor = 'left',
    variant = 'temporary',
    width,
    height,
    children,
    closeOnBackdrop = variant === 'temporary',
    closeOnEscape = true,
}) => {
    const paperRef = useRef<HTMLDivElement>(null);
    const showBackdrop = variant === 'temporary' && open;
    const isPermanent = variant === 'permanent';

    // Handle Escape key
    useEffect(() => {
        if (!open || !closeOnEscape) return;

        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape') {
                onClose();
            }
        };

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [open, closeOnEscape, onClose]);

    // Handle backdrop click
    const handleBackdropClick = useCallback(() => {
        if (closeOnBackdrop) {
            onClose();
        }
    }, [closeOnBackdrop, onClose]);

    // Lock body scroll for temporary drawer
    useEffect(() => {
        if (!open || variant !== 'temporary') return;

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
    }, [open, variant]);

    // Swipe to close gesture (for touch devices)
    const handleDrag = useCallback(
        (event: MouseEvent | TouchEvent | PointerEvent, info: PanInfo) => {
            const threshold = 100; // pixels

            if (anchor === 'left' && info.offset.x < -threshold) {
                onClose();
            } else if (anchor === 'right' && info.offset.x > threshold) {
                onClose();
            } else if (anchor === 'top' && info.offset.y < -threshold) {
                onClose();
            } else if (anchor === 'bottom' && info.offset.y > threshold) {
                onClose();
            }
        },
        [anchor, onClose]
    );

    // Permanent variant renders without portal or animation
    if (isPermanent) {
        return (
            <div className="relative pointer-events-none">
                <motion.div
                    ref={paperRef}
                    className="bg-white dark:bg-gray-900 shadow-2xl pointer-events-auto"
                    style={getDrawerPaperStyle(anchor, width, height)}
                >
                    {children}
                </motion.div>
            </div>
        );
    }

    const rootClassName = variant === 'permanent'
        ? 'relative pointer-events-none'
        : 'fixed inset-0 z-[1300] pointer-events-none';

    // Temporary and persistent variants with animations
    return (
        <div className={rootClassName}>
            <AnimatePresence mode="wait">
                {open && (
                    <>
                        {showBackdrop && (
                            <motion.div
                                className="absolute inset-0 bg-black/50 pointer-events-auto"
                                variants={backdropVariants}
                                initial="hidden"
                                animate="visible"
                                exit="exit"
                                transition={{ duration: 0.2 }}
                                onClick={handleBackdropClick}
                                aria-hidden="true"
                            />
                        )}

                        <motion.div
                            ref={paperRef}
                            className="bg-white dark:bg-gray-900 shadow-2xl"
                            style={getDrawerPaperStyle(anchor, width, height)}
                            variants={getDrawerVariants(anchor)}
                            initial="hidden"
                            animate="visible"
                            exit="exit"
                            transition={{
                                type: 'spring',
                                stiffness: 300,
                                damping: 30,
                            }}
                            drag={variant === 'temporary' ? (anchor === 'left' || anchor === 'right' ? 'x' : 'y') : false}
                            dragConstraints={anchor === 'left' || anchor === 'right' ? { left: 0, right: 0 } : { top: 0, bottom: 0 }}
                            dragElastic={0.2}
                            onDragEnd={handleDrag}
                            role="dialog"
                            aria-modal={variant === 'temporary'}
                        >
                            {children}
                        </motion.div>
                    </>
                )}
            </AnimatePresence>
        </div>
    );
};

InteractionDrawer.displayName = 'InteractionDrawer';

