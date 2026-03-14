import React, { useState, useRef, useEffect } from 'react';
import { clsx } from 'clsx';

export interface DropdownOption {
    value: string;
    label: string;
    icon?: React.ReactNode;
    disabled?: boolean;
    danger?: boolean;
}

export interface DropdownProps {
    /**
     * Trigger element (usually a button)
     */
    trigger: React.ReactNode;
    /**
     * Dropdown options
     */
    options: DropdownOption[];
    /**
     * Selection handler
     */
    onSelect?: (value: string) => void;
    /**
     * Dropdown position relative to trigger
     * @default 'bottom-end'
     */
    placement?: 'bottom-start' | 'bottom-end' | 'top-start' | 'top-end';
    /**
     * Whether dropdown is disabled
     */
    disabled?: boolean;
    /**
     * Additional CSS classes
     */
    className?: string;
}

/**
 * Dropdown component for contextual menus and action lists.
 *
 * @example
 * ```tsx
 * <Dropdown
 *   trigger={<Button>Actions</Button>}
 *   options={[
 *     { value: 'edit', label: 'Edit', icon: <EditIcon /> },
 *     { value: 'delete', label: 'Delete', icon: <DeleteIcon />, danger: true },
 *   ]}
 *   onSelect={(value) => handleAction(value)}
 * />
 * ```
 */
export const Dropdown: React.FC<DropdownProps> = ({
    trigger,
    options,
    onSelect,
    placement = 'bottom-end',
    disabled = false,
    className,
}) => {
    const [isOpen, setIsOpen] = useState(false);
    const dropdownRef = useRef<HTMLDivElement>(null);

    // Close on outside click
    useEffect(() => {
        const handleClickOutside = (event: MouseEvent) => {
            if (
                dropdownRef.current &&
                !dropdownRef.current.contains(event.target as Node)
            ) {
                setIsOpen(false);
            }
        };

        if (isOpen) {
            document.addEventListener('mousedown', handleClickOutside);
            return () =>
                document.removeEventListener('mousedown', handleClickOutside);
        }
    }, [isOpen]);

    // Handle escape key
    useEffect(() => {
        const handleEscape = (e: KeyboardEvent) => {
            if (e.key === 'Escape' && isOpen) {
                setIsOpen(false);
            }
        };

        document.addEventListener('keydown', handleEscape);
        return () => document.removeEventListener('keydown', handleEscape);
    }, [isOpen]);

    const handleSelect = (value: string, optionDisabled?: boolean) => {
        if (optionDisabled) return;

        if (onSelect) {
            onSelect(value);
        }
        setIsOpen(false);
    };

    const placementClasses = {
        'bottom-start': 'top-full left-0 mt-2',
        'bottom-end': 'top-full right-0 mt-2',
        'top-start': 'bottom-full left-0 mb-2',
        'top-end': 'bottom-full right-0 mb-2',
    };

    return (
        <div
            ref={dropdownRef}
            className={clsx('relative inline-block', className)}
        >
            {/* Trigger */}
            <div
                onClick={() => !disabled && setIsOpen(!isOpen)}
                className={clsx(disabled && 'opacity-50 cursor-not-allowed')}
            >
                {trigger}
            </div>

            {/* Dropdown Menu */}
            {isOpen && !disabled && (
                <div
                    className={clsx(
                        'absolute z-50 min-w-[12rem] bg-white rounded-lg shadow-lg border border-gray-200',
                        'py-1 focus:outline-none',
                        placementClasses[placement]
                    )}
                    role="menu"
                    aria-orientation="vertical"
                >
                    {options.map((option, index) => (
                        <button
                            key={option.value}
                            onClick={() => handleSelect(option.value, option.disabled)}
                            disabled={option.disabled}
                            className={clsx(
                                'w-full text-left px-4 py-2 text-sm flex items-center gap-3',
                                'transition-colors duration-150',
                                option.disabled
                                    ? 'text-gray-400 cursor-not-allowed'
                                    : option.danger
                                        ? 'text-error-700 hover:bg-error-50'
                                        : 'text-gray-700 hover:bg-gray-100',
                                'focus:outline-none focus:bg-gray-100'
                            )}
                            role="menuitem"
                            tabIndex={option.disabled ? -1 : 0}
                        >
                            {option.icon && (
                                <span className="flex-shrink-0">{option.icon}</span>
                            )}
                            <span>{option.label}</span>
                        </button>
                    ))}
                </div>
            )}
        </div>
    );
};

Dropdown.displayName = 'Dropdown';
