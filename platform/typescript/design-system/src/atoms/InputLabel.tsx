import React from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface InputLabelProps extends React.LabelHTMLAttributes<HTMLLabelElement> {
    shrink?: boolean;
    error?: boolean;
    disabled?: boolean;
    required?: boolean;
    variant?: 'outlined' | 'filled' | 'standard';
    sx?: SxProps;
}

export const InputLabel: React.FC<InputLabelProps> = ({
    error,
    disabled,
    required,
    sx,
    style,
    children,
    ...props
}) => {
    return (
        <label
            {...props}
            data-error={error || undefined}
            data-disabled={disabled || undefined}
            style={{
                display: 'block',
                marginBottom: tokens.spacing[1],
                fontSize: tokens.typography.fontSize.sm,
                fontWeight: tokens.typography.fontWeight.medium,
                color: disabled
                    ? tokens.colors.neutral[400]
                    : error
                        ? tokens.colors.error[600]
                        : tokens.colors.neutral[700],
                ...sxToStyle(sx),
                ...style,
            }}
        >
            {children}
            {required && (
                <span style={{ color: tokens.colors.error[500], marginLeft: tokens.spacing[1] }}>*</span>
            )}
        </label>
    );
};
