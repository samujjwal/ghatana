/**
 * CommandInput Component
 *
 * Central AI command interface for the entire application.
 * Handles natural language input for creating, modifying, and navigating.
 *
 * @doc.type component
 * @doc.purpose AI command input interface
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { useState, useCallback, useRef, useEffect } from 'react';
import { Sparkles as AutoAwesome, Send, X as Close } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface CommandInputProps {
    /** Size variant */
    size?: 'sm' | 'md' | 'lg';
    /** Visual variant */
    variant?: 'default' | 'floating' | 'inline';
    /** Placeholder text */
    placeholder?: string;
    /** Auto focus on mount */
    autoFocus?: boolean;
    /** Processing state */
    isProcessing?: boolean;
    /** Current value (controlled) */
    value?: string;
    /** Value change handler (controlled) */
    onChange?: (value: string) => void;
    /** Submit handler */
    onSubmit: (intent: string) => void;
    /** Cancel handler */
    onCancel?: () => void;
    /** Additional CSS classes */
    className?: string;
}

// ============================================================================
// Component
// ============================================================================

export function CommandInput({
    size = 'lg',
    variant = 'default',
    placeholder = "Describe what you want to build...",
    autoFocus = true,
    isProcessing = false,
    value: controlledValue,
    onChange: controlledOnChange,
    onSubmit,
    onCancel,
    className = '',
}: CommandInputProps) {
    // Internal state for uncontrolled mode
    const [internalValue, setInternalValue] = useState('');
    const inputRef = useRef<HTMLInputElement>(null);

    // Use controlled or uncontrolled value
    const value = controlledValue !== undefined ? controlledValue : internalValue;
    const handleChange = useCallback((newValue: string) => {
        if (controlledOnChange) {
            controlledOnChange(newValue);
        } else {
            setInternalValue(newValue);
        }
    }, [controlledOnChange]);

    // Focus on mount
    useEffect(() => {
        if (autoFocus && inputRef.current) {
            inputRef.current.focus();
        }
    }, [autoFocus]);

    // Handle submit
    const handleSubmit = useCallback((e?: React.FormEvent) => {
        e?.preventDefault();
        const trimmed = value.trim();
        if (trimmed && !isProcessing) {
            onSubmit(trimmed);
        }
    }, [value, isProcessing, onSubmit]);

    // Handle key events
    const handleKeyDown = useCallback((e: React.KeyboardEvent) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            handleSubmit();
        }
        if (e.key === 'Escape' && onCancel) {
            onCancel();
        }
    }, [handleSubmit, onCancel]);

    // Size classes
    const sizeClasses = {
        sm: 'py-2 px-3 text-sm',
        md: 'py-3 px-4 text-base',
        lg: 'py-4 px-5 text-lg',
    };

    // Variant classes
    const variantClasses = {
        default: 'bg-bg-paper border border-divider',
        floating: 'bg-bg-paper shadow-xl border border-divider',
        inline: 'bg-transparent border-b border-divider',
    };

    return (
        <form onSubmit={handleSubmit} className={`relative ${className}`}>
            <div className={`
                flex items-center gap-3 rounded-xl transition-all duration-200
                focus-within:ring-2 focus-within:ring-primary-500 focus-within:border-primary-500
                ${sizeClasses[size]}
                ${variantClasses[variant]}
            `}>
                {/* AI Icon */}
                <AutoAwesome className={`
                    flex-shrink-0 text-primary-500
                    ${isProcessing ? 'animate-pulse' : ''}
                    ${size === 'lg' ? 'w-6 h-6' : size === 'md' ? 'w-5 h-5' : 'w-4 h-4'}
                `} />

                {/* Input */}
                <input
                    ref={inputRef}
                    type="text"
                    value={value}
                    onChange={(e) => handleChange(e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={placeholder}
                    disabled={isProcessing}
                    className={`
                        flex-1 bg-transparent border-none outline-none
                        text-text-primary placeholder:text-text-secondary
                        disabled:opacity-50 disabled:cursor-not-allowed
                    `}
                    aria-label="AI command input"
                />

                {/* Action Buttons */}
                <div className="flex items-center gap-2">
                    {value && !isProcessing && (
                        <button
                            type="button"
                            onClick={() => handleChange('')}
                            className="p-1 rounded-full hover:bg-grey-100 dark:hover:bg-grey-800 text-text-secondary transition-colors"
                            aria-label="Clear input"
                        >
                            <Close className="w-4 h-4" />
                        </button>
                    )}

                    <button
                        type="submit"
                        disabled={!value.trim() || isProcessing}
                        className={`
                            p-2 rounded-lg transition-all duration-200
                            ${value.trim() && !isProcessing
                                ? 'bg-primary-600 text-white hover:bg-primary-700 cursor-pointer'
                                : 'bg-grey-200 dark:bg-grey-700 text-grey-400 cursor-not-allowed'
                            }
                        `}
                        aria-label="Submit command"
                    >
                        {isProcessing ? (
                            <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                        ) : (
                            <Send className="w-5 h-5" />
                        )}
                    </button>
                </div>
            </div>

            {/* Processing indicator */}
            {isProcessing && (
                <div className="absolute -bottom-6 left-0 right-0 text-center">
                    <span className="text-sm text-text-secondary animate-pulse">
                        AI is thinking...
                    </span>
                </div>
            )}
        </form>
    );
}

export default CommandInput;
