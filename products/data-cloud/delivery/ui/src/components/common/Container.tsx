/**
 * Container Component
 * 
 * Responsive container for consistent content width and padding.
 * 
 * @doc.type component
 * @doc.purpose Content container with responsive sizing
 * @doc.layer common
 */

import React from 'react';
import { cn } from '../../lib/theme';

interface ContainerProps {
    children: React.ReactNode;
    size?: 'sm' | 'md' | 'lg' | 'xl' | 'full';
    className?: string;
}

/**
 * Container sizes mapping
 */
const sizeClasses = {
    sm: 'max-w-2xl',
    md: 'max-w-4xl',
    lg: 'max-w-6xl',
    xl: 'max-w-7xl',
    full: 'max-w-full',
};

/**
 * Container Component
 * 
 * Provides consistent horizontal padding and max-width constraints.
 * 
 * @example
 * ```tsx
 * <Container size="lg">
 *   <h1>Page Title</h1>
 *   <p>Content goes here</p>
 * </Container>
 * ```
 */
export function Container({
    children,
    size = 'lg',
    className
}: ContainerProps): React.ReactElement {
    return (
        <div
            className={cn(
                'mx-auto px-4 box-content',
                sizeClasses[size],
                className
            )}
        >
            {children}
        </div>
    );
}

export default Container;
