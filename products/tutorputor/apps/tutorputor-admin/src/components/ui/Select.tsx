import React from 'react';

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
    children?: React.ReactNode;
}

export const Select = React.forwardRef<HTMLSelectElement, SelectProps>(
    ({ className, children, ...props }, ref) => (
        <select
            ref={ref}
            className={`px-3 py-2 border border-gray-300 rounded focus:outline-none focus:ring focus:ring-blue-500 ${className || ''}`}
            {...props}
        >
            {children}
        </select>
    )
);

Select.displayName = 'Select';
