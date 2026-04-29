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

interface HeadingProps extends React.HTMLAttributes<HTMLHeadingElement> {
    as?: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6';
    level?: 1 | 2 | 3 | 4 | 5 | 6;
    size?: 'sm' | 'md' | 'lg' | 'xl';
}

export function Heading({
    as,
    level,
    size = 'md',
    className = '',
    children,
    ...props
}: HeadingProps) {
    const tag: 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6' = as ?? (level ? (`h${level}` as 'h1' | 'h2' | 'h3' | 'h4' | 'h5' | 'h6') : 'h2');
    const sizeClasses: Record<'sm' | 'md' | 'lg' | 'xl', string> = {
        sm: 'text-xl',
        md: 'text-2xl',
        lg: 'text-3xl',
        xl: 'text-4xl'
    };

    return React.createElement(tag, {
        className: `${sizeClasses[size]} font-bold ${className}`,
        ...props,
        children
    });
}
