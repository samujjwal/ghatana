/**
 * Toast Notification System
 *
 * <p><b>Purpose</b><br>
 * Lightweight toast notification system using Jotai for state management.
 * No external dependencies, fully customizable, accessible.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { useToast } from '@/lib/toast';
 *
 * const { showToast } = useToast();
 *
 * showToast({
 *   type: 'success',
 *   message: 'Feature unpinned successfully!',
 *   duration: 3000,
 * });
 * }</pre>
 *
 * @doc.type utility
 * @doc.purpose Toast notification system
 * @doc.layer product
 * @doc.pattern Service + Hook
 */

import { atom, useAtom, useSetAtom } from 'jotai';
import { clsx } from 'clsx';

/**
 * Toast types
 */
export type ToastType = 'success' | 'error' | 'warning' | 'info';

/**
 * Toast item structure
 */
export interface Toast {
    id: string;
    type: ToastType;
    message: string;
    duration?: number;
    action?: {
        label: string;
        onClick: () => void;
    };
}

/**
 * Toast configuration options
 */
export interface ToastOptions {
    type: ToastType;
    message: string;
    duration?: number;
    action?: {
        label: string;
        onClick: () => void;
    };
}

/**
 * Jotai atom for toast list
 */
const toastsAtom = atom<Toast[]>([]);

/**
 * Generate unique toast ID
 */
function generateToastId(): string {
    return `toast-${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
}

/**
 * Add toast action
 */
const addToastAtom = atom(
    null,
    (get, set, options: ToastOptions) => {
        const newToast: Toast = {
            id: generateToastId(),
            type: options.type,
            message: options.message,
            duration: options.duration ?? 4000,
            action: options.action,
        };

        set(toastsAtom, [...get(toastsAtom), newToast]);

        // Auto-remove after duration
        if (newToast.duration && newToast.duration > 0) {
            setTimeout(() => {
                set(removeToastAtom, newToast.id);
            }, newToast.duration);
        }

        return newToast.id;
    }
);

/**
 * Remove toast action
 */
const removeToastAtom = atom(
    null,
    (get, set, toastId: string) => {
        set(toastsAtom, get(toastsAtom).filter(t => t.id !== toastId));
    }
);

/**
 * Hook for using toast notifications
 */
export function useToast() {
    const addToast = useSetAtom(addToastAtom);
    const removeToast = useSetAtom(removeToastAtom);

    const showToast = (options: ToastOptions) => {
        return addToast(options);
    };

    const showSuccess = (message: string, duration?: number) => {
        return addToast({ type: 'success', message, duration });
    };

    const showError = (message: string, duration?: number) => {
        return addToast({ type: 'error', message, duration });
    };

    const showWarning = (message: string, duration?: number) => {
        return addToast({ type: 'warning', message, duration });
    };

    const showInfo = (message: string, duration?: number) => {
        return addToast({ type: 'info', message, duration });
    };

    return {
        showToast,
        showSuccess,
        showError,
        showWarning,
        showInfo,
        dismiss: removeToast,
    };
}

/**
 * Toast icon component
 */
function ToastIcon({ type }: { type: ToastType }) {
    switch (type) {
        case 'success':
            return (
                <svg className="w-5 h-5 text-green-600 dark:text-green-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
                </svg>
            );
        case 'error':
            return (
                <svg className="w-5 h-5 text-red-600 dark:text-rose-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
            );
        case 'warning':
            return (
                <svg className="w-5 h-5 text-yellow-600 dark:text-yellow-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                </svg>
            );
        case 'info':
            return (
                <svg className="w-5 h-5 text-blue-600 dark:text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
            );
    }
}

/**
 * Single toast component
 */
function ToastItem({ toast, onDismiss }: { toast: Toast; onDismiss: () => void }) {
    const bgColors = {
        success: 'bg-green-50 dark:bg-green-600/30 border-green-200 dark:border-green-800',
        error: 'bg-red-50 dark:bg-rose-600/30 border-red-200 dark:border-red-800',
        warning: 'bg-yellow-50 dark:bg-orange-600/30 border-yellow-200 dark:border-yellow-800',
        info: 'bg-blue-50 dark:bg-indigo-600/30 border-blue-200 dark:border-blue-800',
    };

    const textColors = {
        success: 'text-green-900 dark:text-green-100',
        error: 'text-red-900 dark:text-red-100',
        warning: 'text-yellow-900 dark:text-yellow-100',
        info: 'text-blue-900 dark:text-blue-100',
    };

    return (
        <div
            className={clsx(
                'flex items-center gap-3 p-4 rounded-lg border shadow-lg',
                'animate-slide-up',
                bgColors[toast.type]
            )}
            role="alert"
            aria-live="polite"
        >
            <ToastIcon type={toast.type} />

            <p className={clsx('flex-1 text-sm font-medium', textColors[toast.type])}>
                {toast.message}
            </p>

            {toast.action && (
                <button
                    onClick={() => {
                        toast.action?.onClick();
                        onDismiss();
                    }}
                    className={clsx(
                        'text-sm font-medium underline hover:no-underline',
                        textColors[toast.type]
                    )}
                >
                    {toast.action.label}
                </button>
            )}

            <button
                onClick={onDismiss}
                className={clsx(
                    'p-1 rounded hover:bg-black/5 dark:hover:bg-white/5 transition-colors',
                    textColors[toast.type]
                )}
                aria-label="Dismiss notification"
            >
                <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                </svg>
            </button>
        </div>
    );
}

/**
 * Toast container component (should be placed at root level)
 */
export function ToastContainer() {
    const [toasts] = useAtom(toastsAtom);
    const removeToast = useSetAtom(removeToastAtom);

    if (toasts.length === 0) {
        return null;
    }

    return (
        <div
            className="fixed bottom-4 right-4 z-50 flex flex-col gap-2 max-w-sm w-full pointer-events-none"
            aria-live="polite"
            aria-atomic="true"
        >
            {toasts.map(toast => (
                <div key={toast.id} className="pointer-events-auto">
                    <ToastItem
                        toast={toast}
                        onDismiss={() => removeToast(toast.id)}
                    />
                </div>
            ))}
        </div>
    );
}

/**
 * Toast utilities for programmatic usage
 */
export const toast = {
    success: (message: string, _duration?: number) => {
        // This is a helper for non-hook contexts
        console.log('[Toast]', 'success:', message);
    },
    error: (message: string, _duration?: number) => {
        console.log('[Toast]', 'error:', message);
    },
    warning: (message: string, _duration?: number) => {
        console.log('[Toast]', 'warning:', message);
    },
    info: (message: string, _duration?: number) => {
        console.log('[Toast]', 'info:', message);
    },
};
