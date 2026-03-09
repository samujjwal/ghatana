import React from 'react';

export interface InputProps {
    label?: string;
    placeholder?: string;
    value?: string;
    onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void;
    onBlur?: (e: React.FocusEvent<HTMLInputElement>) => void;
    type?: string;
    size?: 'sm' | 'md' | 'lg';
    error?: string;
    disabled?: boolean;
    required?: boolean;
    icon?: React.ReactNode;
    helperText?: string;
    className?: string;
    name?: string;
    id?: string;
}

export const Input: React.FC<InputProps> = ({
    label,
    placeholder,
    value,
    onChange,
    onBlur,
    type = 'text',
    size = 'md',
    error,
    disabled = false,
    required = false,
    icon,
    helperText,
    className = '',
    name,
    id,
}) => {
    const sizeClass = size === 'sm' ? 'input-sm' : size === 'lg' ? 'input-lg' : '';
    const inputId = id || name;

    return (
        <div className={`flex flex-col gap-1 ${className}`}>
            {label && (
                <label
                    htmlFor={inputId}
                    className="text-sm font-medium text-gray-900"
                >
                    {label}
                    {required && <span className="text-red-500 ml-1">*</span>}
                </label>
            )}

            <div className="relative flex items-center">
                {icon && (
                    <div className="absolute left-3 text-gray-500 pointer-events-none">
                        {icon}
                    </div>
                )}

                <input
                    id={inputId}
                    type={type}
                    placeholder={placeholder}
                    value={value}
                    onChange={onChange}
                    onBlur={onBlur}
                    disabled={disabled}
                    name={name}
                    className={`input-base ${sizeClass} ${icon ? 'pl-10' : ''} ${error ? 'border-red-500 focus:ring-red-200' : ''
                        }`}
                />
            </div>

            {error && <p className="text-sm text-red-600">{error}</p>}
            {helperText && !error && <p className="text-sm text-gray-500">{helperText}</p>}
        </div>
    );
};
