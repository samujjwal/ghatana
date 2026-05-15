import React from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface FormControlProps extends React.HTMLAttributes<HTMLDivElement> {
    component?: React.ElementType;
    fullWidth?: boolean;
    error?: boolean;
    disabled?: boolean;
    required?: boolean;
    margin?: 'none' | 'dense' | 'normal';
    size?: 'small' | 'medium';
    variant?: 'outlined' | 'filled' | 'standard';
    sx?: SxProps;
}

export const FormControl: React.FC<FormControlProps> = ({
    component: Component = 'div',
    fullWidth,
    error,
    disabled,
    margin,
    sx,
    style,
    children,
    ...props
}) => {
    const marginTop =
        margin === 'dense' ? tokens.spacing[1] : margin === 'normal' ? tokens.spacing[2] : undefined;
    const marginBottom =
        margin === 'dense' ? tokens.spacing[1] : margin === 'normal' ? tokens.spacing[2] : undefined;

    return (
        <Component
            {...(props as React.HTMLAttributes<HTMLElement>)}
            aria-invalid={error || undefined}
            data-disabled={disabled || undefined}
            style={{
                width: fullWidth ? '100%' : undefined,
                marginTop,
                marginBottom,
                ...sxToStyle(sx),
                ...style,
            }}
        >
            {children}
        </Component>
    );
};

export interface FormLabelProps extends React.HTMLAttributes<HTMLElement> {
    component?: React.ElementType;
    sx?: SxProps;
}

export const FormLabel: React.FC<FormLabelProps> = ({
    component: Component = 'label',
    sx,
    style,
    children,
    ...props
}) => (
    <Component
        {...(props as React.HTMLAttributes<HTMLElement>)}
        style={{
            display: 'block',
            fontWeight: tokens.typography.fontWeight.medium,
            color: 'rgba(0, 0, 0, 0.87)',
            ...sxToStyle(sx),
            ...style,
        }}
    >
        {children}
    </Component>
);
