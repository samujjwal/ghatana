import React, { useEffect } from 'react';
import { clsx } from 'clsx';

export interface DrawerProps {
    /**
     * Whether the drawer is open
     */
    isOpen?: boolean;
    /**
     * Alias for isOpen (MUI-compatible)
     */
    open?: boolean;
    /**
     * Callback when drawer should close
     */
    onClose: () => void;
    /**
     * Drawer content
     */
    children: React.ReactNode;
    /**
     * Drawer title
     */
    title?: string;
    /**
     * Position of drawer
     * @default 'right'
     */
    position?: 'left' | 'right' | 'top' | 'bottom';
    /**
     * Alias for position (MUI-compatible)
     */
    anchor?: 'left' | 'right' | 'top' | 'bottom';
    /**
     * Size of drawer
     * @default 'md'
     */
    size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
    /**
     * Whether to show overlay backdrop
     * @default true
     */
    showOverlay?: boolean;
    /**
     * Whether clicking overlay closes drawer
     * @default true
     */
    closeOnOverlayClick?: boolean;
    /**
     * Additional CSS classes
     */
    className?: string;
    /**
     * MUI-compatible PaperProps (className/style extracted)
     */
    PaperProps?: { sx?: React.CSSProperties; className?: string; style?: React.CSSProperties };
}

/**
 * Drawer component for sliding panel overlays.
 *
 * @example
 * ```tsx
 * <Drawer
 *   isOpen={isOpen}
 *   onClose={() => setIsOpen(false)}
 *   title="Settings"
 *   position="right"
 *   size="md"
 * >
 *   <div className="p-4">
 *     <p>Drawer content goes here</p>
 *   </div>
 * </Drawer>
 * ```
 */
export const Drawer: React.FC<DrawerProps> = ({
    isOpen: isOpenProp,
    open: openProp,
    onClose,
    children,
    title,
    position: positionProp,
    anchor,
    size = 'md',
    showOverlay = true,
    closeOnOverlayClick = true,
    className,
    PaperProps,
}) => {
    const isOpen = isOpenProp ?? openProp ?? false;
    const position = positionProp ?? anchor ?? 'right';
    // Lock body scroll when drawer is open
    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
            return () => {
                document.body.style.overflow = 'unset';
            };
        }
    }, [isOpen]);

    // Handle escape key
    useEffect(() => {
        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && isOpen) {
                onClose();
            }
        };

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen, onClose]);

    const sizeClasses = {
        left: {
            sm: 'w-64',
            md: 'w-80',
            lg: 'w-96',
            xl: 'w-[32rem]',
            full: 'w-full',
        },
        right: {
            sm: 'w-64',
            md: 'w-80',
            lg: 'w-96',
            xl: 'w-[32rem]',
            full: 'w-full',
        },
        top: {
            sm: 'h-64',
            md: 'h-80',
            lg: 'h-96',
            xl: 'h-[32rem]',
            full: 'h-full',
        },
        bottom: {
            sm: 'h-64',
            md: 'h-80',
            lg: 'h-96',
            xl: 'h-[32rem]',
            full: 'h-full',
        },
    };

    const positionClasses = {
        left: 'left-0 top-0 h-full',
        right: 'right-0 top-0 h-full',
        top: 'top-0 left-0 w-full',
        bottom: 'bottom-0 left-0 w-full',
    };

    const slideClasses = {
        left: isOpen ? 'translate-x-0' : '-translate-x-full',
        right: isOpen ? 'translate-x-0' : 'translate-x-full',
        top: isOpen ? 'translate-y-0' : '-translate-y-full',
        bottom: isOpen ? 'translate-y-0' : 'translate-y-full',
    };

    if (!isOpen && !showOverlay) return null;

    return (
        <>
            {/* Overlay */}
            {showOverlay && (
                <div
                    className={clsx(
                        'fixed inset-0 bg-black transition-opacity z-40',
                        isOpen ? 'opacity-50' : 'opacity-0 pointer-events-none'
                    )}
                    onClick={closeOnOverlayClick ? onClose : undefined}
                    aria-hidden="true"
                />
            )}

            {/* Drawer */}
            <div
                className={clsx(
                    'fixed bg-white shadow-xl z-50 overflow-y-auto',
                    'transition-transform duration-300 ease-in-out',
                    positionClasses[position],
                    sizeClasses[position][size],
                    slideClasses[position],
                    className
                )}
                role="dialog"
                aria-modal="true"
                aria-labelledby={title ? 'drawer-title' : undefined}
            >
                {title && (
                    <div className="sticky top-0 bg-white border-b border-gray-200 px-6 py-4 z-10">
                        <div className="flex items-center justify-between">
                            <h2
                                id="drawer-title"
                                className="text-lg font-semibold text-gray-900"
                            >
                                {title}
                            </h2>
                            <button
                                onClick={onClose}
                                className="text-gray-400 hover:text-gray-600 focus:outline-none focus:ring-2 focus:ring-primary-500 rounded"
                                aria-label="Close drawer"
                            >
                                <svg
                                    className="h-6 w-6"
                                    fill="none"
                                    viewBox="0 0 24 24"
                                    stroke="currentColor"
                                >
                                    <path
                                        strokeLinecap="round"
                                        strokeLinejoin="round"
                                        strokeWidth={2}
                                        d="M6 18L18 6M6 6l12 12"
                                    />
                                </svg>
                            </button>
                        </div>
                    </div>
                )}
                <div className="p-6">{children}</div>
            </div>
        </>
    );
};

Drawer.displayName = 'Drawer';
