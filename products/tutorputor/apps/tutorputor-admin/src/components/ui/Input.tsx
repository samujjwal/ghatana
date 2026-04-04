import React from 'react';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> { }

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
    ({ className, ...props }, ref) => (
        <input
            ref={ref}
            className={`px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring focus:ring-blue-500 ${className || ''}`}
            {...props}
        />
    )
);

Input.displayName = 'Input';
