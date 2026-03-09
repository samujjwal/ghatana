/**
 * Confirmation Dialog Component
 *
 * Modal dialog for confirmation prompts in Admin flows.
 * Used for destructive actions and important decisions.
 *
 * @doc.type component
 * @doc.section ADMIN
 */

import type { ReactNode } from 'react';

import { AlertTriangle, CheckCircle, Info, XCircle } from 'lucide-react';

interface ConfirmDialogProps {
    isOpen: boolean;
    onClose: () => void;
    onConfirm: () => void | Promise<void>;
    title: string;
    message: ReactNode;
    variant?: 'danger' | 'warning' | 'info' | 'success';
    /** Back-compat alias for `variant` used by some call-sites. */
    confirmVariant?: 'danger' | 'warning' | 'info' | 'success';
    confirmText?: string;
    cancelText?: string;
    isLoading?: boolean;
    /** Optional external disable flag (in addition to isLoading). */
    disabled?: boolean;
}

export function ConfirmDialog({
    isOpen,
    onClose,
    onConfirm,
    title,
    message,
    variant: variantProp,
    confirmVariant,
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    isLoading = false,
    disabled = false,
}: ConfirmDialogProps) {
    if (!isOpen) return null;

    const variant = confirmVariant ?? variantProp ?? 'warning';

    const icons = {
        danger: XCircle,
        warning: AlertTriangle,
        info: Info,
        success: CheckCircle,
    };

    const Icon = icons[variant];

    const iconColors = {
        danger: 'text-red-600 dark:text-red-400 bg-red-100 dark:bg-red-900/30',
        warning: 'text-amber-600 dark:text-amber-400 bg-amber-100 dark:bg-amber-900/30',
        info: 'text-blue-600 dark:text-blue-400 bg-blue-100 dark:bg-blue-900/30',
        success: 'text-green-600 dark:text-green-400 bg-green-100 dark:bg-green-900/30',
    };

    const confirmButtonColors = {
        danger: 'bg-red-600 hover:bg-red-700 focus:ring-red-500',
        warning: 'bg-amber-600 hover:bg-amber-700 focus:ring-amber-500',
        info: 'bg-blue-600 hover:bg-blue-700 focus:ring-blue-500',
        success: 'bg-green-600 hover:bg-green-700 focus:ring-green-500',
    };

    return (
        <>
            {/* Backdrop */}
            <div
                className="fixed inset-0 bg-black/50 z-50 transition-opacity"
                onClick={onClose}
                aria-hidden="true"
            />

            {/* Dialog */}
            <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
                <div
                    className="bg-white dark:bg-slate-800 rounded-xl shadow-xl max-w-md w-full p-6"
                    role="dialog"
                    aria-modal="true"
                    aria-labelledby="dialog-title"
                >
                    {/* Icon */}
                    <div className={`mx-auto flex h-12 w-12 items-center justify-center rounded-full ${iconColors[variant]} mb-4`}>
                        <Icon className="h-6 w-6" />
                    </div>

                    {/* Title */}
                    <h3 id="dialog-title" className="text-lg font-semibold text-gray-900 dark:text-white text-center mb-2">
                        {title}
                    </h3>

                    {/* Message */}
                    <div className="text-sm text-gray-600 dark:text-gray-400 text-center mb-6">
                        {message}
                    </div>

                    {/* Actions */}
                    <div className="flex gap-3">
                        <button
                            onClick={onClose}
                            disabled={isLoading || disabled}
                            className="flex-1 px-4 py-2 border border-gray-300 dark:border-slate-600 text-gray-700 dark:text-gray-300 rounded-lg hover:bg-gray-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {cancelText}
                        </button>
                        <button
                            onClick={onConfirm}
                            disabled={isLoading || disabled}
                            className={`flex-1 px-4 py-2 text-white rounded-lg ${confirmButtonColors[variant]} disabled:opacity-50 disabled:cursor-not-allowed focus:outline-none focus:ring-2 focus:ring-offset-2`}
                        >
                            {isLoading ? 'Processing...' : confirmText}
                        </button>
                    </div>
                </div>
            </div>
        </>
    );
}
