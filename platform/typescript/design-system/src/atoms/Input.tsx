/**
 * TextField / Input Component
 * 
 * A text input component for the Ghatana platform.
 * Includes proper label associations for accessibility (WCAG 2.1 AA).
 * 
 * @doc.type component
 * @doc.purpose Text input field
 * @doc.layer shared
 */

import React, { useId } from 'react';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface InputAdornmentLike {
    position?: 'start' | 'end';
    children?: React.ReactNode;
}

export interface TextFieldProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
    label?: string;
    /** Error state; accepts boolean for MUI-like usage, or a string message. */
    error?: boolean | string;
    helperText?: string;
    description?: string;
    /** Legacy prop (kept for compatibility). This component is full-width by default. */
    fullWidth?: boolean;

    /** MUI-like select mode. When true, renders a native <select>. */
    select?: boolean;
    /** Props forwarded to the <select> when `select` is true. */
    SelectProps?: React.SelectHTMLAttributes<HTMLSelectElement> & {
        /** MUI-like flag; accepted for compatibility (native select is always used here). */
        native?: boolean;
    };
    /** Options / children for select mode. */
    children?: React.ReactNode;

    /** MUI-like multiline. When true, renders a <textarea>. */
    multiline?: boolean;
    /** Rows for multiline textarea. */
    rows?: number;

    /** MUI-like maxRows (accepted for compatibility; ignored for native textarea). */
    maxRows?: number;

    /** MUI-like input adornments. */
    InputProps?: {
        startAdornment?: React.ReactNode;
        endAdornment?: React.ReactNode;
        /** MUI-like nested inputProps (forwarded to the underlying <input>). */
        inputProps?: React.InputHTMLAttributes<HTMLInputElement>;
    };

    /** MUI-like label props. Accepted for compatibility; `shrink` is ignored here. */
    InputLabelProps?: React.LabelHTMLAttributes<HTMLLabelElement> & { shrink?: boolean };

    /** MUI-like native input props forwarded to the underlying control. */
    inputProps?: React.InputHTMLAttributes<HTMLInputElement>;

    /** MUI-like size; accepted for compatibility. */
    size?: 'small' | 'medium';

    /** MUI-compatible visual variant (ignored; provided for compatibility). */
    variant?: 'standard' | 'filled' | 'outlined';

    /** MUI-compatible alias for the underlying control ref. */
    inputRef?: React.Ref<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>;

    /** Minimal MUI-like style prop (applied to outer container). */
    sx?: SxProps;
}

export function TextField({
    label,
    error,
    helperText,
    description,
    fullWidth: _fullWidth,
    className = '',
    select = false,
    SelectProps,
    InputProps,
    InputLabelProps,
    inputProps,
    sx,
    children,
    multiline = false,
    rows,
    size: _size,
    variant: _variant,
    inputRef,
    id: providedId,
    'aria-describedby': ariaDescribedBy,
    ...props
}: TextFieldProps) {
    // Generate unique IDs for accessibility
    const generatedId = useId();
    const inputId = providedId || generatedId;
    const helperId = `${inputId}-helper`;
    const errorId = `${inputId}-error`;
    const descriptionId = `${inputId}-description`;

    const hasError = Boolean(error);
    const hasErrorMessage = typeof error === 'string' && error.length > 0;

    // Build aria-describedby from available descriptions
    const describedByIds = [
        ariaDescribedBy,
        hasErrorMessage ? errorId : null,
        helperText ? helperId : null,
        description ? descriptionId : null,
    ].filter(Boolean).join(' ') || undefined;

    const baseControlClass = `w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 ${hasError ? 'border-red-500' : ''}`;
    const resolvedInputProps = inputProps ?? InputProps?.inputProps;

    return (
        <div className="w-full" style={sxToStyle(sx)}>
            {label && (
                <label
                    htmlFor={inputId}
                    className="block text-sm font-medium mb-1"
                    {...InputLabelProps}
                >
                    {label}
                </label>
            )}
            {InputProps?.startAdornment || InputProps?.endAdornment ? (
                <div className={`flex items-center gap-2 ${baseControlClass} ${className}`}>
                    {InputProps?.startAdornment ? (
                        <span className="inline-flex items-center text-gray-500">{InputProps.startAdornment}</span>
                    ) : null}
                    {select ? (
                        <select
                            id={inputId}
                            aria-invalid={hasError}
                            aria-describedby={describedByIds}
                            className="flex-1 bg-transparent outline-none"
                            ref={inputRef as React.Ref<HTMLSelectElement>}
                            {...(props as unknown as React.SelectHTMLAttributes<HTMLSelectElement>)}
                            {...SelectProps}
                        >
                            {children}
                        </select>
                    ) : multiline ? (
                        <textarea
                            id={inputId}
                            aria-invalid={hasError}
                            aria-describedby={describedByIds}
                            className="flex-1 bg-transparent outline-none"
                            rows={rows}
                            ref={inputRef as React.Ref<HTMLTextAreaElement>}
                            {...(props as unknown as React.TextareaHTMLAttributes<HTMLTextAreaElement>)}
                        />
                    ) : (
                        <input
                            id={inputId}
                            aria-invalid={hasError}
                            aria-describedby={describedByIds}
                            className="flex-1 bg-transparent outline-none"
                            ref={inputRef as React.Ref<HTMLInputElement>}
                            {...resolvedInputProps}
                            {...props}
                        />
                    )}
                    {InputProps?.endAdornment ? (
                        <span className="inline-flex items-center text-gray-500">{InputProps.endAdornment}</span>
                    ) : null}
                </div>
            ) : select ? (
                <select
                    id={inputId}
                    aria-invalid={hasError}
                    aria-describedby={describedByIds}
                    className={`${baseControlClass} ${className}`}
                    ref={inputRef as React.Ref<HTMLSelectElement>}
                    {...(props as unknown as React.SelectHTMLAttributes<HTMLSelectElement>)}
                    {...SelectProps}
                >
                    {children}
                </select>
            ) : multiline ? (
                <textarea
                    id={inputId}
                    aria-invalid={hasError}
                    aria-describedby={describedByIds}
                    className={`${baseControlClass} ${className}`}
                    rows={rows}
                    ref={inputRef as React.Ref<HTMLTextAreaElement>}
                    {...(props as unknown as React.TextareaHTMLAttributes<HTMLTextAreaElement>)}
                />
            ) : (
                <input
                    id={inputId}
                    aria-invalid={hasError}
                    aria-describedby={describedByIds}
                    className={`${baseControlClass} ${className}`}
                    ref={inputRef as React.Ref<HTMLInputElement>}
                    {...resolvedInputProps}
                    {...props}
                />
            )}
            {hasErrorMessage ? (
                <p id={errorId} className="text-red-500 text-sm mt-1" role="alert">{error}</p>
            ) : null}
            {helperText && <p id={helperId} className="text-gray-500 text-sm mt-1">{helperText}</p>}
            {description && <p id={descriptionId} className="text-gray-500 text-sm mt-1">{description}</p>}
        </div>
    );
}

// Alias for common naming convention
export const Input = TextField;
