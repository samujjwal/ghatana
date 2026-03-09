import React from 'react';
import type { ButtonVariant, ComponentSize } from '@ghatana/dcmaar-shared-ui-core';

export interface ButtonProps {
  variant?: ButtonVariant;
  size?: ComponentSize;
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
    primary: 'bg-primary-500 text-white hover:bg-primary-600 active:bg-primary-700 shadow-sm',
    secondary: 'bg-white border border-primary-500 text-primary-500 hover:bg-primary-50',
    tertiary: 'bg-transparent text-primary-500 hover:bg-primary-50',
    danger: 'bg-error-500 text-white hover:bg-error-600 active:bg-error-700 shadow-sm',
    ghost: 'bg-transparent text-gray-700 hover:bg-gray-100',
  };

  const sizeClasses = {
    sm: 'px-3 py-1.5 text-sm',
    md: 'px-4 py-2 text-base',
    lg: 'px-6 py-3 text-lg',
  };

  return (
    <button
      type={type}
      onClick={onClick}
      disabled={disabled || loading}
      className={`
        inline-flex items-center justify-center gap-2
        font-medium rounded-lg transition-all
        disabled:opacity-50 disabled:cursor-not-allowed
        ${variantClasses[variant]}
        ${sizeClasses[size]}
        ${className}
      `}
      title={title}
    >
      {loading && <span className="animate-spin">⊙</span>}
      {icon && !loading && <span>{icon}</span>}
      {children}
    </button>
  );
};
