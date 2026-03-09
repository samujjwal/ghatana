import React from 'react';

export interface ToggleProps {
    label?: string;
    checked?: boolean;
    onChange?: (checked: boolean) => void;
    disabled?: boolean;
    className?: string;
    id?: string;
    description?: string;
}

export const Toggle: React.FC<ToggleProps> = ({
    label,
    checked = false,
    onChange,
    disabled = false,
    className = '',
    id,
    description,
}) => {
    return (
        <div className={`flex items-center gap-3 ${className}`}>
            <button
                id={id}
                role="switch"
                aria-checked={checked}
                onClick={() => !disabled && onChange?.(!checked)}
                disabled={disabled}
                className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors ${checked
                        ? 'bg-blue-500'
                        : 'bg-gray-300'
                    } ${disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer'}`}
            >
                <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform ${checked ? 'translate-x-6' : 'translate-x-1'
                        }`}
                />
            </button>

            {label && (
                <div className="flex flex-col gap-1">
                    <label
                        htmlFor={id}
                        className="text-sm font-medium text-gray-900 cursor-pointer"
                    >
                        {label}
                    </label>
                    {description && (
                        <p className="text-xs text-gray-500">{description}</p>
                    )}
                </div>
            )}
        </div>
    );
};
