import React from 'react';
import { tokens } from '@ghatana/tokens';
import { sxToStyle, type SxProps } from '../utils/sx';

export interface FormControlLabelProps extends React.HTMLAttributes<HTMLLabelElement> {
    control: React.ReactElement<{ disabled?: boolean }>;
    label: React.ReactNode;
    value?: unknown;
    disabled?: boolean;
    sx?: SxProps;
}

export const FormControlLabel: React.FC<FormControlLabelProps> = ({
    control,
    label,
    disabled,
    sx,
    style,
    ...props
}) => {
    return (
        <label
            {...props}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: tokens.spacing[2],
                cursor: disabled ? 'not-allowed' : 'pointer',
                opacity: disabled ? 0.6 : 1,
                ...sxToStyle(sx),
                ...style,
            }}
        >
            {React.cloneElement(control, { disabled: disabled ?? control.props.disabled })}
            <span>{label}</span>
        </label>
    );
};
