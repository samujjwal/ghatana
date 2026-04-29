import React from 'react';

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    children?: React.ReactNode;
    variant?: 'default' | 'primary' | 'secondary' | 'destructive' | 'outline' | 'outlined' | 'ghost' | 'link' | 'solid' | 'text' | 'contained' | string;
    size?: 'sm' | 'md' | 'lg' | 'small' | 'medium' | 'large' | string;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
    ({ children, className, variant, size, ...props }, ref) => (
        <button
            ref={ref}
            className={`px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 ${className || ''}`}
            {...props}
        >
            {children}
        </button>
    )
);

Button.displayName = 'Button';
