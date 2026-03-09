import React from 'react';

export interface SelectOption {
    label: string;
    value: string | number;
}

export interface SelectProps {
    label?: string;
    options: SelectOption[];
    value?: string | number;
    onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
    onBlur?: (e: React.FocusEvent<HTMLSelectElement>) => void;
    error?: string;
    disabled?: boolean;
    required?: boolean;
    placeholder?: string;
    size?: 'sm' | 'md' | 'lg';
    helperText?: string;
    className?: string;
    name?: string;
    id?: string;
}

export const Select: React.FC<SelectProps> = ({
    label,
    options,
    value,
    onChange,
    onBlur,
    error,
    disabled = false,
    required = false,
    placeholder,
    size = 'md',
    helperText,
    className = '',
    name,
    id,
}) => {
    const sizeClass = size === 'sm' ? 'input-sm' : size === 'lg' ? 'input-lg' : '';
    const selectId = id || name;

    return (
        <div className={`flex flex-col gap-1 ${className}`}>
            {label && (
                <label
                    htmlFor={selectId}
                    className="text-sm font-medium text-gray-900"
                >
                    {label}
                    {required && <span className="text-red-500 ml-1">*</span>}
                </label>
            )}

            <select
                id={selectId}
                value={value}
                onChange={onChange}
                onBlur={onBlur}
                disabled={disabled}
                name={name}
                className={`input-base ${sizeClass} ${error ? 'border-red-500 focus:ring-red-200' : ''
                    }`}
            >
                {placeholder && (
                    <option value="" disabled>
                        {placeholder}
                    </option>
                )}
                {options.map(option => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))}
            </select>

            {error && <p className="text-sm text-red-600">{error}</p>}
            {helperText && !error && <p className="text-sm text-gray-500">{helperText}</p>}
        </div>
    );
};
