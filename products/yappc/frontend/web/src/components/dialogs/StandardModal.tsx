/**
 * StandardModal Component
 *
 * Unified modal component with consistent styling and behavior.
 * Replaces ad-hoc dialog implementations across the app.
 *
 * Features:
 * - Centered overlay modal
 * - Backdrop blur
 * - Focus trap (WCAG 2.1 Level A compliant)
 * - Keyboard navigation (Escape to close)
 * - Smooth animations
 * - Loading states
 * - Flexible sizing (sm, md, lg, xl, full)
 * - Header/Footer slots
 * - Scrollable content
 * - Touch target compliant buttons (44px minimum)
 *
 * @doc.type component
 * @doc.purpose Standardized modal dialog
 * @doc.layer infrastructure
 * @doc.pattern Modal Component
 *
 * @example
 * ```tsx
 * const dialog = useDialog({
 *   onConfirm: async () => {
 *     await saveData();
 *   }
 * });
 *
 * <StandardModal
 *   {...dialog.props}
 *   title="Edit Profile"
 *   size="md"
 *   actions={
 *     <>
 *       <Button variant="outlined" onClick={dialog.cancel}>Cancel</Button>
 *       <Button onClick={dialog.confirm} loading={dialog.isLoading}>Save</Button>
 *     </>
 *   }
 * >
 *   <form>...</form>
 * </StandardModal>
 * ```
 */

import { useEffect, useRef, useCallback } from 'react';
import { createPortal } from 'react-dom';
import { X as Close } from 'lucide-react';
import { TRANSITIONS, RADIUS, Z_INDEX } from '../../styles/design-tokens';

export interface StandardModalProps {
    /** Whether modal is open */
    isOpen: boolean;

    /** Callback when modal should close */
    onClose: () => void;

    /** Modal title */
    title?: string;

    /** Modal content */
    children: React.ReactNode;

    /** Footer actions (buttons, etc) */
    actions?: React.ReactNode;

    /** Size variant */
    size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';

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
    sm: 'max-w-sm',
    md: 'max-w-md',
    lg: 'max-w-lg',
    xl: 'max-w-xl',
    full: 'max-w-full mx-4',
};

/**
 * StandardModal Component
 */
export function StandardModal({
    isOpen,
    onClose,
    title,
    children,
    actions,
    size = 'md',
    showCloseButton = true,
    closeOnOverlayClick = true,
    closeOnEscape = true,
    isLoading = false,
    error,
    className = '',
    testId = 'standard-modal',
}: StandardModalProps) {
    const modalRef = useRef<HTMLDivElement>(null);
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

    // Get all focusable elements in the modal
    const getFocusableElements = useCallback((): HTMLElement[] => {
        if (!modalRef.current) return [];
        const elements = modalRef.current.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR);
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
                // If focus is outside modal, move it inside
                else if (!modalRef.current?.contains(activeElement)) {
                    e.preventDefault();
                    firstElement?.focus();
                }
            }
        };

        document.addEventListener('keydown', handleKeyDown);
        return () => document.removeEventListener('keydown', handleKeyDown);
    }, [isOpen, closeOnEscape, isLoading, onClose, getFocusableElements]);

    // Focus management - focus first focusable element when modal opens
    useEffect(() => {
        if (isOpen) {
            previousActiveElement.current = document.activeElement as HTMLElement;
            // Focus the first focusable element in the modal after a short delay
            const timer = setTimeout(() => {
                const focusableElements = getFocusableElements();
                if (focusableElements.length > 0) {
                    focusableElements[0].focus();
                } else {
                    modalRef.current?.focus();
                }
            }, 0);
            return () => clearTimeout(timer);
        } else {
            // Return focus to previously focused element when modal closes
            previousActiveElement.current?.focus();
        }
    }, [isOpen, getFocusableElements]);

    // Prevent body scroll when modal is open
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

    const modal = (
        <div
            className="fixed inset-0 flex items-center justify-center"
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

            {/* Modal Content */}
            <div
                ref={modalRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby={title ? `${testId}-title` : undefined}
                tabIndex={-1}
                className={`
          relative w-full ${SIZE_CLASSES[size]}
          bg-bg-paper rounded-xl shadow-2xl
          flex flex-col max-h-[90vh]
          animate-scale-in
          ${TRANSITIONS.default}
          ${className}
        `}
            >
                {/* Header */}
                {(title || showCloseButton) && (
                    <div className="flex items-center justify-between px-6 py-4 border-b border-divider">
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
                                aria-label="Close dialog"
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
                    <div className="flex items-center justify-end gap-2 px-6 py-4 border-t border-divider">
                        {actions}
                    </div>
                )}

                {/* Loading Overlay */}
                {isLoading && (
                    <div className="absolute inset-0 bg-bg-paper/80 backdrop-blur-sm flex items-center justify-center rounded-lg">
                        <div className="flex flex-col items-center gap-2">
                            <div className="w-8 h-8 border-4 border-primary-200 border-t-primary-600 rounded-full animate-spin" />
                            <p className="text-sm text-text-secondary">Processing...</p>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );

    return createPortal(modal, document.body);
}

/**
 * ConfirmDialog - Pre-configured confirmation dialog
 *
 * @example
 * ```tsx
 * const confirm = useDialog({ onConfirm: deleteItem });
 *
 * <ConfirmDialog
 *   {...confirm.props}
 *   title="Delete Item"
 *   message="Are you sure you want to delete this item?"
 *   confirmText="Delete"
 *   confirmVariant="danger"
 *   onConfirm={confirm.confirm}
 *   onCancel={confirm.cancel}
 * />
 * ```
 */
export interface ConfirmDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => void;
    onCancel?: () => void;
    title: string;
    message: string;
    confirmText?: string;
    cancelText?: string;
    confirmVariant?: 'primary' | 'danger';
    isLoading?: boolean;
}

export function ConfirmDialog({
    isOpen,
    onClose,
    onConfirm,
    onCancel,
    title,
    message,
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    confirmVariant = 'primary',
    isLoading,
}: ConfirmDialogProps) {
    const handleCancel = () => {
        onCancel?.();
        onClose();
    };

    const confirmButtonClass =
        confirmVariant === 'danger'
            ? 'bg-error-600 hover:bg-error-700 text-white'
            : 'bg-primary-600 hover:bg-primary-700 text-white';

    return (
        <StandardModal
            isOpen={isOpen}
            onClose={onClose}
            title={title}
            size="sm"
            isLoading={isLoading}
            actions={
                <>
                    <button
                        onClick={handleCancel}
                        disabled={isLoading}
                        className={`
              px-4 py-2 rounded-md text-sm font-medium
              border border-divider text-text-primary
              hover:bg-grey-100 dark:hover:bg-grey-800
              ${TRANSITIONS.fast}
              ${isLoading ? 'opacity-50 cursor-not-allowed' : ''}
            `}
                    >
                        {cancelText}
                    </button>
                    <button
                        onClick={onConfirm}
                        disabled={isLoading}
                        className={`
              px-4 py-2 rounded-md text-sm font-medium
              ${confirmButtonClass}
              ${TRANSITIONS.fast}
              ${isLoading ? 'opacity-50 cursor-not-allowed' : ''}
            `}
                    >
                        {confirmText}
                    </button>
                </>
            }
        >
            <p className="text-text-secondary">{message}</p>
        </StandardModal>
    );
}
