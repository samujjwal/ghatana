/**
 * Dialog Component
 * 
 * Confirmation and alert dialogs with standardized patterns.
 * Built on top of the Modal component for specific use cases.
 * 
 * @doc.type component
 * @doc.purpose Standardized dialogs for confirmations and alerts
 * @doc.layer ui
 * @doc.pattern Composition Component
 */

import React from 'react';
import { Modal } from './Modal';
import { Button } from './Button';
import type { ReactNode } from 'react';

// ============================================================================
// Confirmation Dialog
// ============================================================================

interface ConfirmationDialogProps {
    /** Open state */
    open: boolean;
    /** On close handler */
    onClose: () => void;
    /** On confirm handler */
    onConfirm: () => void;
    /** Title */
    title: string;
    /** Message */
    message: string;
    /** Confirm button text */
    confirmText?: string;
    /** Cancel button text */
    cancelText?: string;
    /** Confirm button variant */
    confirmVariant?: 'primary' | 'danger';
    /** Loading state */
    loading?: boolean;
    /** Icon */
    icon?: ReactNode;
}

/**
 * Confirmation Dialog for user confirmations
 */
export const ConfirmationDialog: React.FC<ConfirmationDialogProps> = ({
    open,
    onClose,
    onConfirm,
    title,
    message,
    confirmText = 'Confirm',
    cancelText = 'Cancel',
    confirmVariant = 'primary',
    loading = false,
    icon,
}) => {
    const handleConfirm = () => {
        onConfirm();
    };

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={title}
            size="sm"
            closeOnOverlayClick={!loading}
            closeOnEscape={!loading}
        >
            <div className="text-center">
                {icon && (
                    <div className="flex justify-center mb-4">
                        <div className="p-3 bg-warning-light rounded-full text-warning">
                            {icon}
                        </div>
                    </div>
                )}

                <p className="text-muted mb-6">
                    {message}
                </p>

                <div className="flex gap-3 justify-center">
                    <Button
                        variant="outline"
                        onClick={onClose}
                        disabled={loading}
                    >
                        {cancelText}
                    </Button>
                    <Button
                        variant={confirmVariant}
                        onClick={handleConfirm}
                        loading={loading}
                        disabled={loading}
                    >
                        {confirmText}
                    </Button>
                </div>
            </div>
        </Modal>
    );
};

// ============================================================================
// Alert Dialog
// ============================================================================

interface AlertDialogProps {
    /** Open state */
    open: boolean;
    /** On close handler */
    onClose: () => void;
    /** Title */
    title: string;
    /** Message */
    message: string;
    /** Action button text */
    actionText?: string;
    /** Alert type */
    type?: 'info' | 'success' | 'warning' | 'error';
    /** Icon */
    icon?: ReactNode;
}

/**
 * Alert Dialog for important messages and notifications
 */
export const AlertDialog: React.FC<AlertDialogProps> = ({
    open,
    onClose,
    title,
    message,
    actionText = 'OK',
    type = 'info',
    icon,
}) => {
    const typeColors = {
        info: 'bg-info-light text-info',
        success: 'bg-success-light text-success',
        warning: 'bg-warning-light text-warning',
        error: 'bg-error-light text-error',
    };

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={title}
            size="sm"
        >
            <div className="text-center">
                {icon && (
                    <div className="flex justify-center mb-4">
                        <div className={`p-3 rounded-full ${typeColors[type]}`}>
                            {icon}
                        </div>
                    </div>
                )}

                <p className="text-muted mb-6">
                    {message}
                </p>

                <Button
                    variant="primary"
                    onClick={onClose}
                    className="w-full"
                >
                    {actionText}
                </Button>
            </div>
        </Modal>
    );
};

// ============================================================================
// Form Dialog
// ============================================================================

interface FormDialogProps {
    /** Open state */
    open: boolean;
    /** On close handler */
    onClose: () => void;
    /** On submit handler */
    onSubmit: (data: unknown) => void;
    /** Title */
    title: string;
    /** Form content */
    children: ReactNode;
    /** Submit button text */
    submitText?: string;
    /** Cancel button text */
    cancelText?: string;
    /** Loading state */
    loading?: boolean;
    /** Disable submit button */
    disabled?: boolean;
}

/**
 * Form Dialog for user input and data collection
 */
export const FormDialog: React.FC<FormDialogProps> = ({
    open,
    onClose,
    onSubmit,
    title,
    children,
    submitText = 'Submit',
    cancelText = 'Cancel',
    loading = false,
    disabled = false,
}) => {
    const handleSubmit = (event: React.FormEvent) => {
        event.preventDefault();
        const formData = new FormData(event.currentTarget as HTMLFormElement);
        const data = Object.fromEntries(formData.entries());
        onSubmit(data);
    };

    return (
        <Modal
            open={open}
            onClose={onClose}
            title={title}
            size="md"
            closeOnOverlayClick={!loading}
            closeOnEscape={!loading}
        >
            <form onSubmit={handleSubmit} className="space-y-4">
                {children}

                <div className="flex gap-3 justify-end pt-4 border-t border-surface">
                    <Button
                        type="button"
                        variant="outline"
                        onClick={onClose}
                        disabled={loading}
                    >
                        {cancelText}
                    </Button>
                    <Button
                        type="submit"
                        loading={loading}
                        disabled={disabled || loading}
                    >
                        {submitText}
                    </Button>
                </div>
            </form>
        </Modal>
    );
};

export default {
    ConfirmationDialog,
    AlertDialog,
    FormDialog,
};
