/**
 * Toast Notification Component
 * 
 * Provides toast notifications using sonner library.
 * Integrates with the centralized theme for consistent styling.
 * 
 * @doc.type component
 * @doc.purpose Toast notifications
 * @doc.layer frontend
 * @doc.pattern Utility Component
 */

import React from 'react';
import { Toaster, toast as sonnerToast } from 'sonner';
import { CheckCircle2, XCircle, AlertTriangle, Info, Loader2 } from 'lucide-react';

/**
 * Toast options
 */
export interface ToastOptions {
    duration?: number;
    description?: string;
    action?: {
        label: string;
        onClick: () => void;
    };
}

/**
 * Toast API
 */
export const toast = {
    /**
     * Show success toast
     */
    success: (message: string, options?: ToastOptions) => {
        sonnerToast.success(message, {
            duration: options?.duration ?? 4000,
            description: options?.description,
            action: options?.action,
            icon: <CheckCircle2 className="h-5 w-5 text-green-500" />,
        });
    },

    /**
     * Show error toast
     */
    error: (message: string, options?: ToastOptions) => {
        sonnerToast.error(message, {
            duration: options?.duration ?? 5000,
            description: options?.description,
            action: options?.action,
            icon: <XCircle className="h-5 w-5 text-red-500" />,
        });
    },

    /**
     * Show warning toast
     */
    warning: (message: string, options?: ToastOptions) => {
        sonnerToast.warning(message, {
            duration: options?.duration ?? 4000,
            description: options?.description,
            action: options?.action,
            icon: <AlertTriangle className="h-5 w-5 text-yellow-500" />,
        });
    },

    /**
     * Show info toast
     */
    info: (message: string, options?: ToastOptions) => {
        sonnerToast.info(message, {
            duration: options?.duration ?? 4000,
            description: options?.description,
            action: options?.action,
            icon: <Info className="h-5 w-5 text-blue-500" />,
        });
    },

    /**
     * Show loading toast (returns dismiss function)
     */
    loading: (message: string) => {
        return sonnerToast.loading(message, {
            icon: <Loader2 className="h-5 w-5 text-blue-500 animate-spin" />,
        });
    },

    /**
     * Dismiss a specific toast or all toasts
     */
    dismiss: (toastId?: string | number) => {
        sonnerToast.dismiss(toastId);
    },

    /**
     * Show promise toast
     */
    promise: <T,>(
        promise: Promise<T>,
        messages: {
            loading: string;
            success: string | ((data: T) => string);
            error: string | ((error: Error) => string);
        }
    ) => {
        return sonnerToast.promise(promise, messages);
    },
};

/**
 * Toast Provider Component
 * 
 * Add this to your app root to enable toast notifications.
 * 
 * @example
 * ```tsx
 * function App() {
 *   return (
 *     <>
 *       <ToastProvider />
 *       <YourApp />
 *     </>
 *   );
 * }
 * ```
 */
export function ToastProvider(): React.ReactElement {
    return (
        <Toaster
            position="bottom-right"
            expand={false}
            richColors
            closeButton
            toastOptions={{
                classNames: {
                    toast: 'bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700',
                    title: 'text-gray-900 dark:text-white',
                    description: 'text-gray-500 dark:text-gray-400',
                    actionButton: 'bg-blue-600 text-white',
                    cancelButton: 'bg-gray-100 dark:bg-gray-700 text-gray-900 dark:text-white',
                    closeButton: 'bg-gray-100 dark:bg-gray-700',
                },
            }}
        />
    );
}

export default ToastProvider;
