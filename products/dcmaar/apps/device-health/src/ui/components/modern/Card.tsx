import React from 'react';

interface CardProps {
    title?: string;
    description?: string;
    children: React.ReactNode;
    footer?: React.ReactNode;
    className?: string;
    variant?: 'default' | 'solid';
}

export const Card: React.FC<CardProps> = ({
    title,
    description,
    children,
    footer,
    className = '',
    variant = 'default',
}) => {
    const cardClass = variant === 'solid' ? 'card-solid' : 'card-base';

    return (
        <div className={`${cardClass} ${className}`}>
            {(title || description) && (
                <div className="px-6 py-4 border-b border-gray-200">
                    {title && <h2 className="text-lg font-bold text-gray-900">{title}</h2>}
                    {description && <p className="text-sm text-gray-600 mt-1">{description}</p>}
                </div>
            )}

            <div className="p-6">
                {children}
            </div>

            {footer && (
                <div className="px-6 py-4 border-t border-gray-200 bg-gray-50 rounded-b-lg">
                    {footer}
                </div>
            )}
        </div>
    );
};
