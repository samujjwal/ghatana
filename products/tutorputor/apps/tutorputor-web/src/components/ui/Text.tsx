import React from 'react';

interface TextProps extends React.HTMLAttributes<any> {
    as?: 'p' | 'span' | 'div' | 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
    size?: 'sm' | 'md' | 'lg';
}

export function Text({
    as: Component = 'p',
    size = 'md',
    className = '',
    children,
    ...props
}: TextProps) {
    const sizeClasses = {
        sm: 'text-sm',
        md: 'text-base',
        lg: 'text-lg'
    };

    return React.createElement(Component, {
        className: `${sizeClasses[size]} ${className}`,
        ...props,
        children
    });
}

export function Heading({
    as = 'h1',
    size = 'md',
    className = '',
    children,
    ...props
}: any) {
    const sizeClasses = {
        sm: 'text-xl',
        md: 'text-2xl',
        lg: 'text-3xl',
        xl: 'text-4xl'
    };

    return React.createElement(as, {
        className: `${sizeClasses[size]} font-bold ${className}`,
        ...props,
        children
    });
}
