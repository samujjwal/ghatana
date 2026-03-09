/**
 * @fileoverview Action Button Component
 *
 * Reusable button component with consistent styling for actions
 * across Popup, Dashboard, and Settings.
 */

import React from 'react';

export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost';
export type ButtonSize = 'sm' | 'md' | 'lg';

export interface ActionButtonProps {
    children: React.ReactNode;
    variant?: ButtonVariant;
    size?: ButtonSize;
    disabled?: boolean;
    loading?: boolean;
    fullWidth?: boolean;
    onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
    type?: 'button' | 'submit' | 'reset';
    className?: string;
}

const VARIANT_STYLES: Record<ButtonVariant, string> = {
    primary: 'bg-blue-600 text-white hover:bg-blue-700 border-blue-600',
    secondary: 'bg-white text-gray-700 hover:bg-gray-50 border-gray-300',
    danger: 'bg-red-600 text-white hover:bg-red-700 border-red-600',
    ghost: 'bg-transparent text-gray-600 hover:bg-gray-100 border-transparent',
};

const SIZE_STYLES: Record<ButtonSize, string> = {
    sm: 'px-2 py-1 text-xs',
    md: 'px-3 py-1.5 text-sm',
    lg: 'px-4 py-2 text-base',
};

export function ActionButton({
    children,
    variant = 'primary',
    size = 'md',
    disabled = false,
    loading = false,
    fullWidth = false,
    onClick,
    type = 'button',
    className = '',
}: ActionButtonProps) {
    const baseStyles = 'inline-flex items-center justify-center gap-1.5 font-medium rounded-md border transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-1 disabled:opacity-50 disabled:cursor-not-allowed';

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled || loading}
            className={`${baseStyles} ${VARIANT_STYLES[variant]} ${SIZE_STYLES[size]} ${fullWidth ? 'w-full' : ''} ${className}`}
        >
            {loading && (
                <span className="animate-spin h-3 w-3 border-2 border-current border-t-transparent rounded-full" />
            )}
            {children}
        </button>
    );
}

// ============================================================================
// Icon Button (for compact actions like close, remove)
// ============================================================================

export interface IconButtonProps {
    icon: React.ReactNode;
    label: string;
    variant?: ButtonVariant;
    size?: ButtonSize;
    disabled?: boolean;
    onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
}

export function IconButton({
    icon,
    label,
    variant = 'ghost',
    size = 'sm',
    disabled = false,
    onClick,
}: IconButtonProps) {
    const sizeClasses = {
        sm: 'p-1',
        md: 'p-1.5',
        lg: 'p-2',
    };

    return (
        <button
            type="button"
            onClick={onClick}
            disabled={disabled}
            aria-label={label}
            title={label}
            className={`inline-flex items-center justify-center rounded-md transition-colors focus:outline-none focus:ring-2 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed ${VARIANT_STYLES[variant]} ${sizeClasses[size]}`}
        >
            {icon}
        </button>
    );
}

// ============================================================================
// Button Group (for grouped actions like filter pills)
// ============================================================================

export interface ButtonGroupProps {
    children: React.ReactNode;
    className?: string;
}

export function ButtonGroup({ children, className = '' }: ButtonGroupProps) {
    return (
        <div className={`inline-flex rounded-full bg-gray-100 p-1 ${className}`}>
            {children}
        </div>
    );
}

export interface ButtonGroupItemProps {
    children: React.ReactNode;
    active?: boolean;
    onClick?: () => void;
}

export function ButtonGroupItem({ children, active = false, onClick }: ButtonGroupItemProps) {
    return (
        <button
            type="button"
            onClick={onClick}
            className={`px-3 py-1 text-xs rounded-full transition-colors ${active
                    ? 'bg-white shadow text-gray-900'
                    : 'text-gray-600 hover:text-gray-800'
                }`}
        >
            {children}
        </button>
    );
}
