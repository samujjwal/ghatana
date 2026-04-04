import React from 'react';

interface TextFieldProps extends React.InputHTMLAttributes<HTMLInputElement> { }

export const TextField = React.forwardRef<HTMLInputElement, TextFieldProps>(
    ({ className, ...props }, ref) => (
        <input
            ref={ref}
            className={`px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring focus:ring-blue-500 ${className || ''}`}
            {...props}
        />
    )
);

TextField.displayName = 'TextField';
