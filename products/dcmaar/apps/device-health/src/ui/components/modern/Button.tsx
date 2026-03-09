import React from 'react';
import './Button.css';

interface ButtonProps {
    variant?: 'primary' | 'secondary' | 'tertiary' | 'danger' | 'ghost';
    size?: 'sm' | 'md' | 'lg';
    disabled?: boolean;
    loading?: boolean;
    onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
    children: React.ReactNode;
    icon?: React.ReactNode;
    className?: string;
    type?: 'button' | 'submit' | 'reset';
    title?: string;
}

export const Button: React.FC<ButtonProps> = ({
    variant = 'primary',
    size = 'md',
    disabled = false,
    loading = false,
    onClick,
    children,
    icon,
    className = '',
    type = 'button',
    title,
}) => {
    const variantClasses = {
        primary: 'btn-primary',
        secondary: 'btn-secondary',
        tertiary: 'btn-tertiary',
        danger: 'btn-danger',
        ghost: 'btn-ghost',
    };

    const sizeClasses = {
        sm: 'btn-sm',
        md: 'btn-md',
        lg: 'btn-lg',
    };

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled || loading}
            className={`btn-base ${variantClasses[variant]} ${sizeClasses[size]} ${className}`}
            title={title}
        >
            {loading && <span className="animate-spin">⊙</span>}
            {icon && !loading && <span className="button-icon">{icon}</span>}
            {children}
        </button>
    );
};
