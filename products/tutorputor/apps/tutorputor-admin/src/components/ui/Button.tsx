import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
    children?: React.ReactNode;
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
    ({ children, className, ...props }, ref) => (
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
