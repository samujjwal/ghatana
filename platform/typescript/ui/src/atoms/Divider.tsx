import React from 'react';
import { clsx } from 'clsx';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface DividerProps {
    /**
     * Divider orientation
     * @default 'horizontal'
     */
    orientation?: 'horizontal' | 'vertical';
    /**
     * Optional label to display in the divider
     */
    label?: string;
    /**
     * Label position
     * @default 'center'
     */
    labelPosition?: 'left' | 'center' | 'right';
    /**
     * Visual thickness
     * @default 'thin'
     */
    thickness?: 'thin' | 'medium' | 'thick';
    /**
     * Color variant
     * @default 'default'
     */
    variant?: 'default' | 'light' | 'dark';
    /**
     * Additional CSS classes
     */
    className?: string;

    /** Minimal MUI-like style prop. */
    sx?: SxProps;

    /** Inline style overrides. */
    style?: React.CSSProperties;
}

/**
 * Divider component for visual separation of content.
 *
 * @example
 * ```tsx
 * <Divider label="OR" labelPosition="center" />
 * <Divider orientation="vertical" className="h-20" />
 * ```
 */
export const Divider: React.FC<DividerProps> = ({
    orientation = 'horizontal',
    label,
    labelPosition = 'center',
    thickness = 'thin',
    variant = 'default',
    className,
    sx,
    style,
}) => {
    const thicknessClasses = {
        thin: 'border-t',
        medium: 'border-t-2',
        thick: 'border-t-4',
    };

    const variantClasses = {
        default: 'border-gray-200',
        light: 'border-gray-100',
        dark: 'border-gray-300',
    };

    if (orientation === 'vertical') {
        return (
            <div
                className={clsx(
                    'border-l',
                    thickness === 'medium' && 'border-l-2',
                    thickness === 'thick' && 'border-l-4',
                    variantClasses[variant],
                    className
                )}
                style={{ ...sxToStyle(sx), ...style }}
                role="separator"
                aria-orientation="vertical"
            />
        );
    }

    if (!label) {
        return (
            <hr
                className={clsx(
                    thicknessClasses[thickness],
                    variantClasses[variant],
                    className
                )}
                style={{ ...sxToStyle(sx), ...style }}
                role="separator"
            />
        );
    }

    const positionClasses = {
        left: 'justify-start',
        center: 'justify-center',
        right: 'justify-end',
    };

    return (
        <div
            className={clsx('relative flex items-center', className)}
            style={{ ...sxToStyle(sx), ...style }}
            role="separator"
            aria-label={label}
        >
            <div
                className={clsx(
                    'flex-1',
                    thicknessClasses[thickness],
                    variantClasses[variant],
                    labelPosition === 'left' && 'max-w-[3rem]',
                    labelPosition === 'right' && 'order-2'
                )}
            />
            <span
                className={clsx(
                    'px-3 text-sm text-gray-500 bg-white',
                    labelPosition === 'center' && 'mx-3'
                )}
            >
                {label}
            </span>
            <div
                className={clsx(
                    'flex-1',
                    thicknessClasses[thickness],
                    variantClasses[variant],
                    labelPosition === 'right' && 'max-w-[3rem]',
                    labelPosition === 'left' && 'order-2'
                )}
            />
        </div>
    );
};

Divider.displayName = 'Divider';
