import * as React from 'react';

export interface InputAdornmentProps extends React.HTMLAttributes<HTMLSpanElement> {
    position?: 'start' | 'end';
}

export const InputAdornment: React.FC<InputAdornmentProps> = ({
    position = 'start',
    className,
    style,
    children,
    ...rest
}) => {
    return (
        <span
            className={className}
            style={{
                display: 'inline-flex',
                alignItems: 'center',
                ...(position === 'start' ? { marginRight: '0.25rem' } : { marginLeft: '0.25rem' }),
                ...style,
            }}
            {...rest}
        >
            {children}
        </span>
    );
};

InputAdornment.displayName = 'InputAdornment';
