/**
 * Form Field Components
 *
 * Consistent form field components for Admin forms.
 * Includes input, textarea, select, and checkbox.
 *
 * @doc.type component
 * @doc.section ADMIN
 */

import { AlertCircle } from 'lucide-react';

interface FormFieldProps {
    label: string;
    name: string;
    error?: string;
    required?: boolean;
    helpText?: string;
    children: React.ReactNode;
}

export function FormField({ label, name, error, required, helpText, children }: FormFieldProps) {
    return (
        <div className="space-y-2">
            <label htmlFor={name} className="block text-sm font-medium text-gray-700 dark:text-gray-300">
                {label}
                {required && <span className="text-red-500 ml-1">*</span>}
            </label>
            {children}
            {helpText && !error && (
                <p className="text-xs text-gray-500 dark:text-gray-400">{helpText}</p>
            )}
            {error && (
                <div className="flex items-center gap-1 text-xs text-red-600 dark:text-red-400">
                    <AlertCircle className="h-3 w-3" />
                    <span>{error}</span>
                </div>
            )}
        </div>
    );
}

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
    error?: boolean;
}

export function Input({ error, className = '', ...props }: InputProps) {
    return (
        <input
            className={`w-full px-4 py-2 border ${error
                    ? 'border-red-300 dark:border-red-600'
                    : 'border-gray-300 dark:border-slate-600'
                } rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 ${error ? 'focus:ring-red-500' : 'focus:ring-blue-500'
                } disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
            {...props}
        />
    );
}

interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
    error?: boolean;
}

export function Textarea({ error, className = '', ...props }: TextareaProps) {
    return (
        <textarea
            className={`w-full px-4 py-2 border ${error
                    ? 'border-red-300 dark:border-red-600'
                    : 'border-gray-300 dark:border-slate-600'
                } rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:outline-none focus:ring-2 ${error ? 'focus:ring-red-500' : 'focus:ring-blue-500'
                } disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
            {...props}
        />
    );
}

interface SelectProps extends React.SelectHTMLAttributes<HTMLSelectElement> {
    error?: boolean;
    options?: Array<{ value: string; label: string }>;
    children?: React.ReactNode;
}

export function Select({ error, options, className = '', children, ...props }: SelectProps) {
    return (
        <select
            className={`w-full px-4 py-2 border ${error
                    ? 'border-red-300 dark:border-red-600'
                    : 'border-gray-300 dark:border-slate-600'
                } rounded-lg bg-white dark:bg-slate-800 text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 ${error ? 'focus:ring-red-500' : 'focus:ring-blue-500'
                } disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
            {...props}
        >
            {options
                ? options.map((option) => (
                    <option key={option.value} value={option.value}>
                        {option.label}
                    </option>
                ))
                : children}
        </select>
    );
}

interface CheckboxProps extends React.InputHTMLAttributes<HTMLInputElement> {
    label: string;
}

export function Checkbox({ label, className = '', ...props }: CheckboxProps) {
    return (
        <label className="flex items-center gap-2 cursor-pointer">
            <input
                type="checkbox"
                className={`h-4 w-4 rounded border-gray-300 dark:border-slate-600 text-blue-600 focus:ring-blue-500 disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
                {...props}
            />
            <span className="text-sm text-gray-700 dark:text-gray-300">{label}</span>
        </label>
    );
}
