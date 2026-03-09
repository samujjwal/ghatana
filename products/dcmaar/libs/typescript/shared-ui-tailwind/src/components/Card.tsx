import React from 'react';
import type { CardVariant } from '@ghatana/dcmaar-shared-ui-core';

export interface CardProps {
  title?: string;
  description?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  headerActions?: React.ReactNode;
  className?: string;
  variant?: CardVariant;
}

export const Card: React.FC<CardProps> = ({
  title,
  description,
  children,
  footer,
  headerActions,
  className = '',
  variant = 'default',
}) => {
  const cardClass =
    variant === 'solid'
      ? 'bg-gray-100 border border-gray-300'
      : 'bg-white border border-gray-200 shadow-sm';

  return (
    <div className={`rounded-lg ${cardClass} ${className}`}>
      {(title || description || headerActions) && (
        <div className="px-6 py-4 border-b border-gray-200">
          <div className="flex items-start justify-between gap-4">
            <div className="flex-1 min-w-0">
              {title && <h2 className="text-lg font-bold text-gray-900">{title}</h2>}
              {description && <p className="text-sm text-gray-600 mt-1">{description}</p>}
            </div>
            {headerActions && (
              <div className="flex items-center gap-2 flex-shrink-0">
                {headerActions}
              </div>
            )}
          </div>
        </div>
      )}

      <div className="p-6">{children}</div>

      {footer && (
        <div className="px-6 py-4 border-t border-gray-200 bg-gray-50 rounded-b-lg">
          {footer}
        </div>
      )}
    </div>
  );
};
