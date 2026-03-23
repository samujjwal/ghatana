import React from 'react';
import { clsx } from 'clsx';
import { Radio, type RadioProps } from '../atoms/Radio';

export interface RadioGroupOption {
    value: string;
    label: string;
    description?: string;
    disabled?: boolean;
}

export interface RadioGroupProps extends Omit<React.HTMLAttributes<HTMLDivElement>, 'onChange'> {
    /** Group name for radio inputs (recommended for children API). */
    name?: string;

    /** Options-based API. When `children` is provided, this is ignored. */
    options?: RadioGroupOption[];

    /** Controlled value (used by options-based API; children API is best-effort). */
    value?: string;

    /**
     * Change handler.
     * - MUI-like: `(event, value) => void`
     * - Legacy: `(value) => void`
     */
    onChange?:
    | ((value: string) => void)
    | ((event: React.ChangeEvent<HTMLInputElement>, value: string) => void);

    /** Group label */
    label?: string;

    /** Error message */
    error?: string;

    /** Layout direction (legacy) */
    orientation?: 'vertical' | 'horizontal';

    /** MUI-like layout flag */
    row?: boolean;

    /** Visual size (options-based API only) */
    size?: RadioProps['size'];

    /** Whether the entire group is disabled */
    disabled?: boolean;
}

export const RadioGroup: React.FC<RadioGroupProps> = ({
    name,
    options,
    value,
    onChange,
    label,
    error,
    orientation = 'vertical',
    row,
    size = 'md',
    disabled = false,
    className,
    children,
    ...divProps
}) => {
    const resolvedName = name ?? 'radio-group';
    const groupId = `radio-group-${resolvedName}`;
    const resolvedOrientation = row ? 'horizontal' : orientation;

    const handleChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const nextValue = event.target.value;
        if (!onChange) return;

        if (onChange.length >= 2) {
            (onChange as (event: React.ChangeEvent<HTMLInputElement>, value: string) => void)(event, nextValue);
            return;
        }

        (onChange as (value: string) => void)(nextValue);
    };

    return (
        <fieldset className={clsx('space-y-2', className)} aria-describedby={error ? `${groupId}-error` : undefined}>
            {label ? (
                <legend className="text-sm font-medium text-gray-700 mb-3">{label}</legend>
            ) : null}

            <div
                {...divProps}
                className={clsx(
                    resolvedOrientation === 'horizontal' ? 'flex flex-wrap gap-4' : 'space-y-3'
                )}
                role="radiogroup"
                aria-labelledby={label ? groupId : undefined}
                onChange={handleChange as unknown as React.FormEventHandler<HTMLDivElement>}
                aria-disabled={disabled || undefined}
            >
                {children
                    ? children
                    : (options ?? []).map((option) => (
                        <Radio
                            key={option.value}
                            name={resolvedName}
                            value={option.value}
                            label={option.label}
                            description={option.description}
                            checked={value === option.value}
                            onChange={handleChange}
                            disabled={disabled || option.disabled}
                            size={size}
                            error={value === option.value ? error : undefined}
                        />
                    ))}
            </div>

            {error ? (
                <p id={`${groupId}-error`} className="mt-2 text-sm text-error-600" role="alert">
                    {error}
                </p>
            ) : null}
        </fieldset>
    );
};

RadioGroup.displayName = 'RadioGroup';

