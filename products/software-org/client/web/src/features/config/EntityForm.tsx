/**
 * Unified Entity Form Component
 *
 * A dynamic form generator that renders fields based on EntityTypeDefinition.
 * Uses basic Tailwind styling for consistency.
 *
 * @doc.type component
 * @doc.purpose Dynamic form generation from entity schemas
 * @doc.layer product
 * @doc.pattern Form
 */

import React, { useState, useCallback, useMemo } from 'react';
import { useForm, Controller, type FieldError } from 'react-hook-form';
import {
    type EntityTypeDefinition,
    type EntityField,
    type FieldOption,
} from './entity-registry';
import { X, HelpCircle, Loader2, Plus, Save, RotateCcw } from 'lucide-react';

// ============================================================================
// Types
// ============================================================================

export interface EntityFormProps {
    /** Entity type definition */
    entityType: EntityTypeDefinition;
    /** Initial values (for edit mode) */
    initialValues?: Record<string, unknown>;
    /** Dynamic options for select fields */
    dynamicOptions?: Record<string, FieldOption[]>;
    /** Submit handler */
    onSubmit: (data: Record<string, unknown>) => Promise<void>;
    /** Cancel handler */
    onCancel?: () => void;
    /** Is form in loading state */
    isLoading?: boolean;
    /** Mode: create or edit */
    mode?: 'create' | 'edit';
    /** Custom submit button text */
    submitText?: string;
    /** Show cancel button */
    showCancel?: boolean;
    /** Compact mode for drawers */
    compact?: boolean;
}

// ============================================================================
// Helper Components
// ============================================================================

interface FieldWrapperProps {
    field: EntityField;
    error?: FieldError;
    children: React.ReactNode;
}

function FieldWrapper({ field, error, children }: FieldWrapperProps) {
    return (
        <div className="space-y-1.5">
            <div className="flex items-center gap-2">
                <label
                    htmlFor={field.key}
                    className="block text-sm font-medium text-gray-700 dark:text-gray-300"
                >
                    {field.label}
                    {field.required && <span className="text-red-500 ml-0.5">*</span>}
                </label>
                {field.helpText && (
                    <div className="group relative">
                        <HelpCircle className="h-3.5 w-3.5 text-gray-400 cursor-help" />
                        <div className="hidden group-hover:block absolute left-0 top-6 z-10 w-48 p-2 bg-gray-900 text-white text-xs rounded-lg shadow-lg">
                            {field.helpText}
                        </div>
                    </div>
                )}
            </div>
            {children}
            {error && (
                <p className="text-xs text-red-600 dark:text-red-400">{error.message}</p>
            )}
        </div>
    );
}

// Tag Input Component
interface TagInputProps {
    value: string[];
    onChange: (value: string[]) => void;
    placeholder?: string;
    disabled?: boolean;
}

function TagInput({ value = [], onChange, placeholder, disabled }: TagInputProps) {
    const [inputValue, setInputValue] = useState('');

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            const newTag = inputValue.trim();
            if (newTag && !value.includes(newTag)) {
                onChange([...value, newTag]);
                setInputValue('');
            }
        } else if (e.key === 'Backspace' && !inputValue && value.length > 0) {
            onChange(value.slice(0, -1));
        }
    };

    const removeTag = (tagToRemove: string) => {
        onChange(value.filter((tag) => tag !== tagToRemove));
    };

    return (
        <div className="flex flex-wrap items-center gap-1.5 p-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 min-h-[42px]">
            {value.map((tag) => (
                <span
                    key={tag}
                    className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 text-sm rounded-full"
                >
                    {tag}
                    {!disabled && (
                        <button
                            type="button"
                            onClick={() => removeTag(tag)}
                            className="hover:text-blue-600 dark:hover:text-blue-100"
                        >
                            <X className="h-3 w-3" />
                        </button>
                    )}
                </span>
            ))}
            <input
                type="text"
                value={inputValue}
                onChange={(e: React.ChangeEvent<HTMLInputElement>) => setInputValue(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder={value.length === 0 ? placeholder : ''}
                disabled={disabled}
                className="flex-1 min-w-[100px] bg-transparent border-none outline-none text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400"
            />
        </div>
    );
}

// ============================================================================
// Main Form Component
// ============================================================================

export function EntityForm({
    entityType,
    initialValues = {},
    dynamicOptions = {},
    onSubmit,
    onCancel,
    isLoading = false,
    mode = 'create',
    submitText,
    showCancel = true,
}: EntityFormProps) {
    const {
        register,
        control,
        handleSubmit,
        reset,
        formState: { errors, isDirty },
    } = useForm({
        defaultValues: initialValues,
    });

    // Group fields by their group property
    const groupedFields = useMemo(() => {
        const groups: Record<string, EntityField[]> = {};
        const ungrouped: EntityField[] = [];

        entityType.fields.forEach((field) => {
            if (field.group) {
                if (!groups[field.group]) {
                    groups[field.group] = [];
                }
                groups[field.group].push(field);
            } else {
                ungrouped.push(field);
            }
        });

        // Sort fields within each group by order
        Object.values(groups).forEach((groupFields) => {
            groupFields.sort((a, b) => (a.order || 0) - (b.order || 0));
        });

        return { groups, ungrouped };
    }, [entityType.fields]);

    // Get options for a field
    const getFieldOptions = useCallback(
        (field: EntityField): FieldOption[] => {
            if (field.optionsFrom && dynamicOptions[field.optionsFrom]) {
                return dynamicOptions[field.optionsFrom];
            }
            return field.options || [];
        },
        [dynamicOptions]
    );

    // Render a single field
    const renderField = (field: EntityField) => {
        const error = errors[field.key] as FieldError | undefined;
        const options = getFieldOptions(field);

        switch (field.type) {
            case 'text':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <input
                            type="text"
                            id={field.key}
                            placeholder={field.placeholder}
                            disabled={isLoading}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
                            {...register(field.key, {
                                required: field.required ? `${field.label} is required` : false,
                                pattern: field.validation?.pattern
                                    ? {
                                        value: new RegExp(field.validation.pattern),
                                        message: field.validation.patternMessage || 'Invalid format',
                                    }
                                    : undefined,
                            })}
                        />
                    </FieldWrapper>
                );

            case 'textarea':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <textarea
                            id={field.key}
                            placeholder={field.placeholder}
                            disabled={isLoading}
                            rows={4}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 resize-none"
                            {...register(field.key, {
                                required: field.required ? `${field.label} is required` : false,
                            })}
                        />
                    </FieldWrapper>
                );

            case 'number':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            rules={{
                                required: field.required ? `${field.label} is required` : false,
                                min: field.validation?.min
                                    ? { value: field.validation.min, message: `Minimum value is ${field.validation.min}` }
                                    : undefined,
                                max: field.validation?.max
                                    ? { value: field.validation.max, message: `Maximum value is ${field.validation.max}` }
                                    : undefined,
                            }}
                            render={({ field: formField }) => (
                                <input
                                    type="number"
                                    id={field.key}
                                    placeholder={field.placeholder}
                                    disabled={isLoading}
                                    value={String(formField.value ?? '')}
                                    onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                        formField.onChange(e.target.value ? parseFloat(e.target.value) : undefined)
                                    }
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
                                />
                            )}
                        />
                    </FieldWrapper>
                );

            case 'select':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            rules={{ required: field.required ? `${field.label} is required` : false }}
                            render={({ field: formField }) => (
                                <select
                                    id={field.key}
                                    disabled={isLoading}
                                    value={String(formField.value ?? '')}
                                    onChange={formField.onChange}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
                                >
                                    <option value="">{field.placeholder || `Select ${field.label}`}</option>
                                    {options.map((option) => (
                                        <option key={option.value} value={option.value}>
                                            {option.label}
                                        </option>
                                    ))}
                                </select>
                            )}
                        />
                    </FieldWrapper>
                );

            case 'multiselect':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            rules={{ required: field.required ? `${field.label} is required` : false }}
                            render={({ field: formField }) => {
                                const selectedValues: string[] = Array.isArray(formField.value) ? formField.value : [];
                                return (
                                    <div className="space-y-2">
                                        <div className="flex flex-wrap gap-1.5">
                                            {selectedValues.map((val: string) => {
                                                const option = options.find((o) => o.value === val);
                                                return (
                                                    <span
                                                        key={val}
                                                        className="inline-flex items-center gap-1 px-2 py-0.5 bg-blue-100 dark:bg-blue-900/30 text-blue-800 dark:text-blue-300 text-sm rounded-full"
                                                    >
                                                        {option?.label || val}
                                                        <button
                                                            type="button"
                                                            onClick={() =>
                                                                formField.onChange(
                                                                    selectedValues.filter((v: string) => v !== val)
                                                                )
                                                            }
                                                            className="hover:text-blue-600 dark:hover:text-blue-100"
                                                        >
                                                            <X className="h-3 w-3" />
                                                        </button>
                                                    </span>
                                                );
                                            })}
                                        </div>
                                        <select
                                            disabled={isLoading}
                                            value=""
                                            onChange={(e: React.ChangeEvent<HTMLSelectElement>) => {
                                                const newValue = e.target.value;
                                                if (newValue && !selectedValues.includes(newValue)) {
                                                    formField.onChange([...selectedValues, newValue]);
                                                }
                                            }}
                                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
                                        >
                                            <option value="">Add {field.label}...</option>
                                            {options
                                                .filter((o) => !selectedValues.includes(o.value))
                                                .map((option) => (
                                                    <option key={option.value} value={option.value}>
                                                        {option.label}
                                                    </option>
                                                ))}
                                        </select>
                                    </div>
                                );
                            }}
                        />
                    </FieldWrapper>
                );

            case 'tags':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            rules={{ required: field.required ? `${field.label} is required` : false }}
                            render={({ field: formField }) => (
                                <TagInput
                                    value={Array.isArray(formField.value) ? formField.value : []}
                                    onChange={formField.onChange}
                                    placeholder={field.placeholder}
                                    disabled={isLoading}
                                />
                            )}
                        />
                    </FieldWrapper>
                );

            case 'boolean':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            render={({ field: formField }) => (
                                <label className="flex items-center gap-3 cursor-pointer">
                                    <input
                                        type="checkbox"
                                        id={field.key}
                                        disabled={isLoading}
                                        checked={Boolean(formField.value)}
                                        onChange={(e: React.ChangeEvent<HTMLInputElement>) =>
                                            formField.onChange(e.target.checked)
                                        }
                                        className="w-5 h-5 rounded border-gray-300 text-blue-600 focus:ring-blue-500"
                                    />
                                    <span className="text-sm text-gray-600 dark:text-gray-400">
                                        {field.placeholder || 'Enable'}
                                    </span>
                                </label>
                            )}
                        />
                    </FieldWrapper>
                );

            case 'json':
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <Controller
                            name={field.key}
                            control={control}
                            rules={{
                                required: field.required ? `${field.label} is required` : false,
                                validate: (value) => {
                                    if (!value) return true;
                                    try {
                                        if (typeof value === 'string') {
                                            JSON.parse(value);
                                        }
                                        return true;
                                    } catch {
                                        return 'Invalid JSON format';
                                    }
                                },
                            }}
                            render={({ field: formField }) => (
                                <textarea
                                    id={field.key}
                                    placeholder={field.placeholder || '{\n  "key": "value"\n}'}
                                    disabled={isLoading}
                                    rows={6}
                                    value={
                                        typeof formField.value === 'object' && formField.value !== null
                                            ? JSON.stringify(formField.value, null, 2)
                                            : String(formField.value || '')
                                    }
                                    onChange={(e: React.ChangeEvent<HTMLTextAreaElement>) => {
                                        try {
                                            const parsed = JSON.parse(e.target.value);
                                            formField.onChange(parsed);
                                        } catch {
                                            formField.onChange(e.target.value);
                                        }
                                    }}
                                    className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50 font-mono text-sm"
                                />
                            )}
                        />
                    </FieldWrapper>
                );

            default:
                return (
                    <FieldWrapper key={field.key} field={field} error={error}>
                        <input
                            type="text"
                            id={field.key}
                            placeholder={field.placeholder}
                            disabled={isLoading}
                            className="w-full px-3 py-2 border border-gray-300 dark:border-gray-600 rounded-lg bg-white dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 focus:ring-2 focus:ring-blue-500 focus:border-transparent disabled:opacity-50"
                            {...register(field.key)}
                        />
                    </FieldWrapper>
                );
        }
    };

    // Handle form submission
    const onFormSubmit = handleSubmit(async (data) => {
        try {
            await onSubmit(data);
        } catch (error) {
            console.error('Form submission error:', error);
        }
    });

    const buttonText = submitText || (mode === 'create' ? `Create ${entityType.name}` : 'Save Changes');

    return (
        <form onSubmit={onFormSubmit} className="space-y-6">
            {/* Ungrouped fields first */}
            {groupedFields.ungrouped.length > 0 && (
                <div className="space-y-4">
                    {groupedFields.ungrouped.map((field) => renderField(field))}
                </div>
            )}

            {/* Grouped fields */}
            {entityType.fieldGroups?.map((group) => {
                const fields = groupedFields.groups[group.id];
                if (!fields || fields.length === 0) return null;

                return (
                    <div key={group.id} className="space-y-4">
                        <div className="border-b border-gray-200 dark:border-gray-700 pb-2">
                            <h3 className="text-lg font-medium text-gray-900 dark:text-gray-100">
                                {group.label}
                            </h3>
                            {group.description && (
                                <p className="text-sm text-gray-500 dark:text-gray-400">
                                    {group.description}
                                </p>
                            )}
                        </div>
                        <div className="grid gap-4 sm:grid-cols-2">
                            {fields.map((field) => renderField(field))}
                        </div>
                    </div>
                );
            })}

            {/* Form Actions */}
            <div className="flex items-center justify-end gap-3 pt-4 border-t border-gray-200 dark:border-gray-700">
                {showCancel && onCancel && (
                    <button
                        type="button"
                        onClick={onCancel}
                        disabled={isLoading}
                        className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-50"
                    >
                        Cancel
                    </button>
                )}
                {isDirty && (
                    <button
                        type="button"
                        onClick={() => reset()}
                        disabled={isLoading}
                        className="px-4 py-2 text-gray-700 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg transition-colors disabled:opacity-50 flex items-center gap-2"
                    >
                        <RotateCcw className="h-4 w-4" />
                        Reset
                    </button>
                )}
                <button
                    type="submit"
                    disabled={isLoading}
                    className="px-4 py-2 bg-blue-600 text-white hover:bg-blue-700 rounded-lg transition-colors disabled:opacity-50 flex items-center gap-2"
                >
                    {isLoading ? (
                        <Loader2 className="h-4 w-4 animate-spin" />
                    ) : mode === 'create' ? (
                        <Plus className="h-4 w-4" />
                    ) : (
                        <Save className="h-4 w-4" />
                    )}
                    {buttonText}
                </button>
            </div>
        </form>
    );
}
