/**
 * Base Card Component
 * 
 * Reusable card component with optional title and actions.
 * Uses centralized theme styles for consistency.
 * 
 * @doc.type component
 * @doc.purpose Base card container
 * @doc.layer frontend
 * @doc.pattern Presentational Component
 */

import React from 'react';
import { cn, cardStyles, textStyles } from '../../lib/theme';

interface BaseCardProps {
  /** Optional card title */
  title?: string;
  /** Optional subtitle under the title */
  subtitle?: string;
  /** Optional leading icon in the header */
  icon?: React.ReactNode;
  /** Card content */
  children: React.ReactNode;
  /** Additional CSS classes */
  className?: string;
  /** Optional action elements (buttons, etc.) */
  actions?: React.ReactNode;
}

/**
 * Base card component with theme support.
 * 
 * @example
 * ```tsx
 * <BaseCard title="My Card" actions={<Button>Action</Button>}>
 *   <p>Card content</p>
 * </BaseCard>
 * ```
 */
export const BaseCard: React.FC<BaseCardProps> = ({
  title,
  subtitle,
  icon,
  children,
  className = '',
  actions
}) => (
  <div className={cn(cardStyles.base, cardStyles.padded, className)}>
    {(title || actions) && (
      <div className="flex items-center justify-between mb-4">
        <div className="min-w-0">
          {title && (
            <div className="flex items-center gap-2">
              {icon ? <span className="text-gray-500 dark:text-gray-400">{icon}</span> : null}
              <h3 className={textStyles.h3}>{title}</h3>
            </div>
          )}
          {subtitle ? <p className={textStyles.small}>{subtitle}</p> : null}
        </div>
        {actions && <div>{actions}</div>}
      </div>
    )}
    {children}
  </div>
);

export default BaseCard;
