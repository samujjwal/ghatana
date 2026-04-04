import React from 'react';

interface CheckboxProps extends React.InputHTMLAttributes<HTMLInputElement> {
    label?: string;
}

export const Checkbox = React.forwardRef<HTMLInputElement, CheckboxProps>(
    ({ className, label, ...props }, ref) => (
        <label className="flex items-center">
            <input
                ref={ref}
                type="checkbox"
                className={`w-4 h-4 rounded border-gray-300 focus:ring-blue-500 ${className || ''}`}
                {...props}
            />
            {label && <span className="ml-2">{label}</span>}
        </label>
    )
);

Checkbox.displayName = 'Checkbox';
