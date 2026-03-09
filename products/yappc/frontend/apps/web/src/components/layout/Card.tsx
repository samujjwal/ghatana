/**
 * Card Component
 * 
 * Consistent card styling for content sections.
 * Use this component for all card-like containers across routes.
 * 
 * @doc.type component
 * @doc.purpose Consistent card container
 * @doc.layer product
 * @doc.pattern Presentational Component
 */

import { ReactNode, forwardRef, HTMLAttributes } from 'react';

interface CardProps extends HTMLAttributes<HTMLDivElement> {
    /** Card content */
    children: ReactNode;
    /** Optional padding override */
    padding?: 'none' | 'sm' | 'md' | 'lg';
    /** Hover effect */
    hoverable?: boolean;
    /** Optional className for custom styling */
    className?: string;
}

const paddingClasses = {
    none: '',
    sm: 'p-4',
    md: 'p-6',
    lg: 'p-8',
};

export const Card = forwardRef<HTMLDivElement, CardProps>(
    ({ children, padding = 'md', hoverable = false, className = '', ...props }, ref) => {
        const hoverClasses = hoverable
            ? 'transition-all duration-200 hover:shadow-lg hover:border-primary-300 cursor-pointer'
            : '';

        return (
            <div
                ref={ref}
                className={`bg-bg-paper rounded-xl border border-divider ${paddingClasses[padding]} ${hoverClasses} ${className}`}
                {...props}
            >
                {children}
            </div>
        );
    }
);

Card.displayName = 'Card';

interface CardHeaderProps {
    /** Header title */
    title: string;
    /** Optional subtitle */
    subtitle?: string;
    /** Optional action buttons (right side) */
    actions?: ReactNode;
    /** Optional className */
    className?: string;
}

export function CardHeader({ title, subtitle, actions, className = '' }: CardHeaderProps) {
    return (
        <div className={`flex items-start justify-between gap-4 mb-4 ${className}`}>
            <div>
                <h3 className="text-lg font-semibold text-text-primary">{title}</h3>
                {subtitle && (
                    <p className="text-sm text-text-secondary mt-1">{subtitle}</p>
                )}
            </div>
            {actions && <div className="flex items-center gap-2">{actions}</div>}
        </div>
    );
}

interface CardBodyProps {
    children: ReactNode;
    className?: string;
}

export function CardBody({ children, className = '' }: CardBodyProps) {
    return <div className={className}>{children}</div>;
}

interface CardFooterProps {
    children: ReactNode;
    className?: string;
}

export function CardFooter({ children, className = '' }: CardFooterProps) {
    return (
        <div className={`mt-4 pt-4 border-t border-divider ${className}`}>
            {children}
        </div>
    );
}

export default Card;
