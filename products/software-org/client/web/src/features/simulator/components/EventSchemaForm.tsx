import React from 'react';

/**
 * Field schema interface.
 */
export interface FieldSchema {
    name: string;
    type: 'string' | 'number' | 'boolean' | 'date' | 'array' | 'object';
    required: boolean;
    description?: string;
    enum?: string[];
    default?: unknown;
}

/**
 * Event schema interface.
 */
export interface EventSchema {
    id: string;
    name: string;
    version: string;
    description?: string;
    fields: FieldSchema[];
    examples?: Record<string, unknown>[];
}

/**
 * Event Schema Form Props interface.
 */
export interface EventSchemaFormProps {
    schema: EventSchema;
    onSubmit?: (data: Record<string, unknown>) => void;
    isSubmitting?: boolean;
    defaultValues?: Record<string, unknown>;
}

/**
 * Event Schema Form - Dynamic form generator from event schema.
 *
 * <p><b>Purpose</b><br>
 * Generates a form UI from an event schema definition with field validation and type coercion.
 *
 * <p><b>Features</b><br>
 * - Dynamic field rendering based on schema
 * - Type-specific input components (text, number, date, select, etc.)
 * - Field validation (required, enum)
 * - Default value population
 * - Submit button with loading state
 * - Error messages
 * - Dark mode support
 * - Accessibility-first (ARIA labels, keyboard nav)
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * <EventSchemaForm 
 *   schema={eventSchema}
 *   onSubmit={handleFormSubmit}
 *   isSubmitting={false}
 * />
 * }</pre>
 *
 * @doc.type component
 * @doc.purpose Dynamic event form generator
 * @doc.layer product
 * @doc.pattern Organism
 */
export const EventSchemaForm = React.memo(
    ({ schema, onSubmit, isSubmitting, defaultValues }: EventSchemaFormProps) => {
        const [formData, setFormData] = React.useState<Record<string, unknown>>(
            defaultValues || {}
        );
        const [errors, setErrors] = React.useState<Record<string, string>>({});

        const handleFieldChange = (fieldName: string, value: unknown) => {
            setFormData((prev) => ({ ...prev, [fieldName]: value }));
            // Clear error for this field when user starts typing
            if (errors[fieldName]) {
                setErrors((prev) => {
                    const newErrors = { ...prev };
                    delete newErrors[fieldName];
                    return newErrors;
                });
            }
        };

        const handleSubmit = (e: React.FormEvent) => {
            e.preventDefault();

            // Validate
            const newErrors: Record<string, string> = {};
            schema.fields.forEach((field) => {
                if (field.required && !formData[field.name]) {
                    newErrors[field.name] = `${field.name} is required`;
                }
            });

            if (Object.keys(newErrors).length > 0) {
                setErrors(newErrors);
                return;
            }

            onSubmit?.(formData);
        };

        const renderField = (field: FieldSchema) => {
            const value = formData[field.name] ?? '';
            const error = errors[field.name];

            const inputClasses = `w-full px-3 py-2 rounded-md border transition-colors ${error
                    ? 'border-red-300 bg-red-50 dark:border-red-700 dark:bg-rose-600/30 focus:ring-red-500'
                    : 'border-slate-300 bg-white dark:border-neutral-600 dark:bg-neutral-800 focus:ring-blue-500'
                } focus:ring-2 focus:outline-none text-slate-900 dark:text-neutral-100 placeholder-slate-500 dark:placeholder-slate-400`;

            switch (field.type) {
                case 'string':
                    if (field.enum) {
                        return (
                            <select
                                value={value as string}
                                onChange={(e) => handleFieldChange(field.name, e.target.value)}
                                className={inputClasses}
                                aria-label={field.name}
                                aria-required={field.required}
                                aria-invalid={!!error}
                            >
                                <option value="">Select {field.name}...</option>
                                {field.enum.map((option) => (
                                    <option key={option} value={option}>
                                        {option}
                                    </option>
                                ))}
                            </select>
                        );
                    }
                    return (
                        <input
                            type="text"
                            value={value as string}
                            onChange={(e) => handleFieldChange(field.name, e.target.value)}
                            placeholder={field.default?.toString() || `Enter ${field.name}`}
                            className={inputClasses}
                            aria-label={field.name}
                            aria-required={field.required}
                            aria-invalid={!!error}
                            aria-describedby={error ? `${field.name}-error` : undefined}
                        />
                    );

                case 'number':
                    return (
                        <input
                            type="number"
                            value={value as number}
                            onChange={(e) => handleFieldChange(field.name, e.target.valueAsNumber)}
                            placeholder={field.default?.toString() || `Enter ${field.name}`}
                            className={inputClasses}
                            aria-label={field.name}
                            aria-required={field.required}
                            aria-invalid={!!error}
                            aria-describedby={error ? `${field.name}-error` : undefined}
                        />
                    );

                case 'boolean':
                    return (
                        <div className="flex items-center gap-2">
                            <input
                                type="checkbox"
                                checked={value as boolean}
                                onChange={(e) => handleFieldChange(field.name, e.target.checked)}
                                className="w-4 h-4 rounded border-slate-300 text-blue-600 focus:ring-2 focus:ring-blue-500 dark:border-neutral-600 dark:bg-neutral-700"
                                aria-label={field.name}
                                aria-required={field.required}
                                aria-invalid={!!error}
                            />
                            <label className="text-sm text-slate-700 dark:text-neutral-300">
                                {field.description || field.name}
                            </label>
                        </div>
                    );

                case 'date':
                    return (
                        <input
                            type="datetime-local"
                            value={value as string}
                            onChange={(e) => handleFieldChange(field.name, e.target.value)}
                            className={inputClasses}
                            aria-label={field.name}
                            aria-required={field.required}
                            aria-invalid={!!error}
                            aria-describedby={error ? `${field.name}-error` : undefined}
                        />
                    );

                case 'array':
                case 'object':
                    return (
                        <textarea
                            value={typeof value === 'string' ? value : JSON.stringify(value, null, 2)}
                            onChange={(e) => {
                                try {
                                    const parsed = JSON.parse(e.target.value);
                                    handleFieldChange(field.name, parsed);
                                } catch {
                                    handleFieldChange(field.name, e.target.value);
                                }
                            }}
                            placeholder={`Enter JSON ${field.type}`}
                            className={`${inputClasses} font-mono text-sm`}
                            rows={4}
                            aria-label={field.name}
                            aria-required={field.required}
                            aria-invalid={!!error}
                            aria-describedby={error ? `${field.name}-error` : undefined}
                        />
                    );

                default:
                    return null;
            }
        };

        return (
            <form onSubmit={handleSubmit} className="space-y-6">
                <div>
                    <h2 className="text-lg font-semibold text-slate-900 dark:text-neutral-100 mb-2">
                        {schema.name}
                    </h2>
                    {schema.description && (
                        <p className="text-sm text-slate-600 dark:text-neutral-400">
                            {schema.description}
                        </p>
                    )}
                    <p className="text-xs text-slate-500 dark:text-slate-500 dark:text-neutral-400 mt-1">
                        Version: {schema.version}
                    </p>
                </div>

                <div className="space-y-4">
                    {schema.fields.map((field) => (
                        <div key={field.name}>
                            <label
                                htmlFor={field.name}
                                className="block text-sm font-medium text-slate-900 dark:text-neutral-100 mb-1"
                            >
                                {field.name}
                                {field.required && (
                                    <span className="text-red-600 dark:text-rose-400 ml-1">*</span>
                                )}
                            </label>
                            {field.description && (
                                <p className="text-xs text-slate-500 dark:text-neutral-400 mb-1.5">
                                    {field.description}
                                </p>
                            )}
                            {renderField(field)}
                            {errors[field.name] && (
                                <p
                                    id={`${field.name}-error`}
                                    className="text-sm text-red-600 dark:text-rose-400 mt-1"
                                    role="alert"
                                >
                                    {errors[field.name]}
                                </p>
                            )}
                        </div>
                    ))}
                </div>

                <button
                    type="submit"
                    disabled={isSubmitting}
                    className="w-full py-2.5 px-4 rounded-md bg-blue-600 text-white font-medium hover:bg-blue-700 dark:hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                    aria-busy={isSubmitting}
                >
                    {isSubmitting ? 'Submitting...' : 'Submit Event'}
                </button>
            </form>
        );
    }
);

EventSchemaForm.displayName = 'EventSchemaForm';

export default EventSchemaForm;
