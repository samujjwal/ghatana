import React, { useState } from 'react';

interface ModalProps extends React.HTMLAttributes<HTMLDivElement> {
    open?: boolean;
    isOpen?: boolean;
    onOpenChange?: (open: boolean) => void;
    onClose?: () => void;
    size?: string;
}

export function Modal({
    open = false,
    isOpen,
    onOpenChange,
    onClose,
    size = 'md',
    className = '',
    children,
    ...props
}: ModalProps) {
    const isModalOpen = open || isOpen;

    if (!isModalOpen) return null;

    const handleClose = () => {
        onOpenChange?.(false);
        onClose?.();
    };

    const sizeClasses = {
        sm: 'max-w-sm',
        md: 'max-w-md',
        lg: 'max-w-lg',
        xl: 'max-w-xl'
    };

    return (
        <>
            <div
                className="fixed inset-0 bg-black bg-opacity-50 z-40"
                onClick={handleClose}
            />
            <div
                className={`fixed top-1/2 left-1/2 transform -translate-x-1/2 -translate-y-1/2 bg-white rounded-lg shadow-lg z-50 w-full max-h-[90vh] overflow-auto ${sizeClasses[size]} ${className}`}
                {...props}
            >
                {children}
            </div>
        </>
    );
}

export function ModalHeader({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
    return (
        <div className={`px-6 py-4 border-b border-gray-200 ${className}`} {...props}>
            {children}
        </div>
    );
}

export function ModalBody({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
    return (
        <div className={`px-6 py-4 ${className}`} {...props}>
            {children}
        </div>
    );
}

export function ModalFooter({ className = '', children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
    return (
        <div className={`px-6 py-4 border-t border-gray-200 flex justify-end gap-2 ${className}`} {...props}>
            {children}
        </div>
    );
}