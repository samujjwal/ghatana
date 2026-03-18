/**
 * StandardDrawer Component
 *
 * Unified drawer/side panel component with consistent styling and behavior.
 * Used for forms, details panels, and contextual actions.
 *
 * Features:
 * - Slides in from right (default) or left
 * - Backdrop overlay
 * - Focus trap
 * - Keyboard navigation (Escape to close)
 * - Smooth animations
 * - Loading states
 * - Flexible sizing (sm, md, lg, xl, full)
 * - Header/Footer slots
 * - Scrollable content
 *
 * @doc.type component
 * @doc.purpose Standardized drawer/side panel
 * @doc.layer infrastructure
 * @doc.pattern Drawer Component
 *
 * @example
 * ```tsx
 * const drawer = useDialog({
 *   onConfirm: async (data) => {
 *     await saveSettings(data);
 *   }
 * });
 *
 * <StandardDrawer
 *   {...drawer.props}
 *   title="Settings"
 *   size="md"
 *   side="right"
 *   actions={
 *     <>
 *       <Button variant="outlined" onClick={drawer.cancel}>Cancel</Button>
 *       <Button onClick={drawer.confirm} loading={drawer.isLoading}>Save</Button>
 *     </>
 *   }
 * >
 *   <SettingsForm />
 * </StandardDrawer>
 * ```
 */

import { useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { X as Close } from 'lucide-react';
import { TRANSITIONS, Z_INDEX } from '../../styles/design-tokens';

export interface StandardDrawerProps {
    /** Whether drawer is open */
    isOpen: boolean;

    /** Callback when drawer should close */
    onClose: () => void;

    /** Drawer title */
    title?: string;

    /** Drawer content */
    children: React.ReactNode;

    /** Footer actions (buttons, etc) */
    actions?: React.ReactNode;

    /** Size variant */
    size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';

    /** Which side to slide from */
    side?: 'left' | 'right';

    /** Whether to show close button */
    showCloseButton?: boolean;

    /** Whether to close on overlay click */
    closeOnOverlayClick?: boolean;

    /** Whether to close on Escape key */
    closeOnEscape?: boolean;

    /** Loading state (disables close) */
    isLoading?: boolean;

    /** Error message to display */
    error?: Error | null;

    /** Additional CSS classes */
    className?: string;

    /** Test ID for testing */
    testId?: string;
}

const SIZE_CLASSES = {
    sm: 'w-80',
    md: 'w-96',
    lg: 'w-[32rem]',
    xl: 'w-[40rem]',
    full: 'w-full',
};

/**
 * StandardDrawer Component
 */
export function StandardDrawer({
    isOpen,
    onClose,
    title,
    children,
    actions,
    size = 'md',
    side = 'right',
    showCloseButton = true,
    closeOnOverlayClick = true,
    closeOnEscape = true,
    isLoading = false,
    error,
    className = '',
    testId = 'standard-drawer',
}: StandardDrawerProps) {
    const drawerRef = useRef<HTMLDivElement>(null);
    const previousActiveElement = useRef<HTMLElement | null>(null);

    // Focusable element selector for focus trap
    const FOCUSABLE_SELECTOR = [
        'a[href]',
        'button:not([disabled])',
        'input:not([disabled])',
        'select:not([disabled])',
        'textarea:not([disabled])',
        '[tabindex]:not([tabindex="-1"])',
    ].join(', ');

    // Get all focusable elements in the drawer
    const getFocusableElements = useCallback((): HTMLElement[] => {
        if (!drawerRef.current) return [];
        const elements = drawerRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
        return Array.from(elements).filter((el) => {
            const style = window.getComputedStyle(el);
            return style.display !== 'none' && style.visibility !== 'hidden';
        });
    }, []);

    // Handle Escape key and Tab focus trap
    useEffect(() => {
        if (!isOpen) return;

        const handleKeyDown = (e: KeyboardEvent) => {
            // Handle Escape key
            if (e.key === 'Escape' && closeOnEscape && !isLoading) {
                onClose();
                return;
            }

            // Handle Tab key for focus trap
            if (e.key === 'Tab') {
                const focusableElements = getFocusableElements();
                if (focusableElements.length === 0) return;

                const firstElement = focusableElements[0];
                const lastElement = focusableElements[focusableElements.length - 1];
                const activeElement = document.activeElement;

                // Shift + Tab at first element -> go to last
                if (e.shiftKey && activeElement === firstElement) {
                    e.preventDefault();
                    lastElement?.focus();
                }
                // Tab at last element -> go to first
                else if (!e.shiftKey && activeElement === lastElement) {
                    e.preventDefault();
                    firstElement?.focus();
                }
                // If focus is outside drawer, move it inside
                else if (!drawerRef.current?.contains(activeElement)) {
                    e.preventDefault();
                    firstElement?.focus();
                }
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, closeOnEscape, isLoading, onClose, getFocusableElements]);

    // Focus management - focus first focusable element when drawer opens
    useEffect(() => {
        if (isOpen) {
            previousActiveElement.current = document.activeElement as HTMLElement;
            // Focus the first focusable element in the drawer after a short delay
            const timer = setTimeout(() => {
                const focusableElements = getFocusableElements();
                if (focusableElements.length > 0) {
                    focusableElements[0].focus();
                } else {
                    drawerRef.current?.focus();
                }
            }, 0);
            return () => clearTimeout(timer);
        } else {
            // Return focus to previously focused element when drawer closes
            previousActiveElement.current?.focus();
        }
    }, [isOpen, getFocusableElements]);

    // Prevent body scroll when drawer is open
    useEffect(() => {
        if (isOpen) {
            document.body.style.overflow = 'hidden';
        } else {
            document.body.style.overflow = '';
        }

        return () => {
            document.body.style.overflow = '';
        };
    }, [isOpen]);

    if (!isOpen) return null;

    const handleOverlayClick = (e: React.MouseEvent) => {
        if (e.target === e.currentTarget && closeOnOverlayClick && !isLoading) {
            onClose();
        }
    };

    const slideAnimationClass =
        side === 'right'
            ? 'animate-slide-in-right'
            : 'animate-slide-in-left';

    const positionClass = side === 'right' ? 'right-0' : 'left-0';

    const drawer = (
        <div
            className="fixed inset-0"
            style={{ zIndex: Z_INDEX.modal }}
            data-testid={testId}
            onClick={handleOverlayClick}
        >
            {/* Backdrop */}
            <div
                className={`
          absolute inset-0 bg-black/50 backdrop-blur-sm
          animate-fade-in
        `}
                aria-hidden="true"
            />

            {/* Drawer Content */}
            <div
                ref={drawerRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={title ? `${testId}-title` : undefined}
                tabIndex={-1}
                className={`
          absolute top-0 ${positionClass} h-full
          ${SIZE_CLASSES[size]}
          bg-bg-paper shadow-2xl
          flex flex-col
          ${slideAnimationClass}
          ${TRANSITIONS.default}
          ${className}
        `}
            >
                {/* Header */}
                {(title || showCloseButton) && (
                    <div className="flex items-center justify-between px-6 py-4 border-b border-divider flex-shrink-0">
                        {title && (
                            <h2
                                id={`${testId}-title`}
                                className="text-lg font-semibold text-text-primary"
                            >
                                {title}
                            </h2>
                        )}
                        {showCloseButton && (
                            <button
                                onClick={onClose}
                                disabled={isLoading}
                                className={`
                  min-w-[44px] min-h-[44px] p-2.5 rounded-md text-text-secondary
                  hover:bg-grey-100 dark:hover:bg-grey-800
                  focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
                  ${TRANSITIONS.fast}
                  ${isLoading ? 'opacity-50 cursor-not-allowed' : ''}
                `}
                                aria-label="Close drawer"
                            >
                                <Close className="w-5 h-5" />
                            </button>
                        )}
                    </div>
                )}

                {/* Content */}
                <div className="flex-1 overflow-y-auto px-6 py-4">
                    {error && (
                        <div className="mb-4 p-3 bg-error-50 dark:bg-error-900/20 border border-error-200 dark:border-error-800 rounded-md">
                            <p className="text-sm text-error-700 dark:text-error-300">
                                {error.message}
                            </p>
                        </div>
                    )}
                    {children}
                </div>

                {/* Footer */}
                {actions && (
                    <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-divider flex-shrink-0">
                        {actions}
                    </div>
                )}

                {/* Loading Overlay */}
                {isLoading && (
                    <div className="absolute inset-0 bg-bg-paper/80 backdrop-blur-sm flex items-center justify-center">
                        <div className="flex flex-col items-center gap-2">
                            <div className="w-8 h-8 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
                            <p className="text-sm text-text-secondary">Processing...</p>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );

    return createPortal(drawer, document.body);
}

/**
 * FormDrawer - Pre-configured drawer for forms
 *
 * @example
 * ```tsx
 * const form = useDialog({
 *   onConfirm: async (data) => {
 *     await saveForm(data);
 *   }
 * });
 *
 * <FormDrawer
 *   {...form.props}
 *   title="Edit Profile"
 *   submitText="Save Changes"
 *   onSubmit={form.confirm}
 *   onCancel={form.cancel}
 * >
 *   <FormFields />
 * </FormDrawer>
 * ```
 */
export interface FormDrawerProps {
    isOpen: boolean;
    onClose: () => void;
    onSubmit: () => void;
    onCancel?: () => void;
    title: string;
    children: React.ReactNode;
    submitText?: string;
    cancelText?: string;
    size?: StandardDrawerProps['size'];
    side?: StandardDrawerProps['side'];
    isLoading?: boolean;
    error?: Error | null;
}

export function FormDrawer({
    isOpen,
    onClose,
    onSubmit,
    onCancel,
    title,
    children,
    submitText = 'Save',
    cancelText = 'Cancel',
    size = 'md',
    side = 'right',
    isLoading,
    error,
}: FormDrawerProps) {
    const handleCancel = () => {
        onCancel?.();
        onClose();
    };

    return (
        <StandardDrawer
            isOpen={isOpen}
            onClose={onClose}
            title={title}
            size={size}
            side={side}
            isLoading={isLoading}
            error={error}
            actions={
                <>
                    <button
                        onClick={handleCancel}
                        disabled={isLoading}
                        className={`
              min-h-[44px] px-4 py-2 rounded-md text-sm font-medium
              border border-divider text-text-primary
              hover:bg-grey-100 dark:hover:bg-grey-800
              focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
              ${TRANSITIONS.fast}
              ${isLoading ? 'opacity-50 cursor-not-allowed' : ''}
            `}
                    >
                        {cancelText}
                    </button>
                    <button
                        onClick={onSubmit}
                        disabled={isLoading}
                        className={`
              min-h-[44px] px-4 py-2 rounded-md text-sm font-medium
              bg-primary-600 hover:bg-primary-700 text-white
              focus:outline-none focus:ring-2 focus:ring-primary-500 focus:ring-offset-2
              ${TRANSITIONS.fast}
              ${isLoading ? 'opacity-50 cursor-not-allowed' : ''}
            `}
                    >
                        {submitText}
                    </button>
                </>
            }
        >
            {children}
        </StandardDrawer>
    );
}
