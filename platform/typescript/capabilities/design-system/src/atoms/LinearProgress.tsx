import React from 'react';
import { sxToStyle } from '../utils/sx';

import { Progress } from './Progress';

export interface LinearProgressProps {
    /** Progress variant */
    variant?: 'determinate' | 'indeterminate';
    /** Progress value (0-100) when determinate */
    value?: number;
    /** Color variant */
    color?: 'primary' | 'secondary' | 'success' | 'warning' | 'error' | 'info' | 'default';
    /** Additional class name */
    className?: string;

    /** Minimal MUI-like style prop. Supports spacing shorthands. */
    sx?: unknown;
    /** Optional wrapper styles. */
    style?: React.CSSProperties;
}

export const LinearProgress: React.FC<LinearProgressProps> = ({
    variant = 'indeterminate',
    value = 0,
    color = 'primary',
    className,
    sx,
    style,
}) => {
    const normalizedColor = color === 'info' || color === 'default' || color === 'secondary' ? 'primary' : color;

    const wrapperStyle: React.CSSProperties = {
        ...sxToStyle(sx),
        ...style,
    };

    return (
        <div style={wrapperStyle} className={className}>
            <Progress
                variant="linear"
                value={value}
                indeterminate={variant !== 'determinate'}
                color={normalizedColor}
            />
        </div>
    );
};

LinearProgress.displayName = 'LinearProgress';
