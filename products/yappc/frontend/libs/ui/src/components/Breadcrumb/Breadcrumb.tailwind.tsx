import * as React from 'react';

import { cn } from '../../utils/cn';

/**
 *
 */
export interface BreadcrumbItemType {
  /** Breadcrumb label */
  label: string;
  /** Breadcrumb link */
  href?: string;
  /** Click handler */
  onClick?: () => void;
  /** Custom icon */
  icon?: React.ReactNode;
  /** Disabled state */
  disabled?: boolean;
}

/**
 *
 */
export interface BreadcrumbProps {
  /** Breadcrumb items */
  items: BreadcrumbItemType[];
  /** Separator between items */
  separator?: React.ReactNode;
  /** Maximum items to show before collapsing */
  maxItems?: number;
  /** Size variant */
  size?: 'small' | 'medium' | 'large';
  /** Show home icon on first item */
  showHomeIcon?: boolean;
  /** className for root element */
  className?: string;
}

/**
 * Breadcrumb component for navigation hierarchy
 * 
 * Pure Tailwind CSS implementation with collapse functionality.
 * 
 * @example
 * ```tsx
 * <Breadcrumb
 *   items={[
 *     { label: 'Home', href: '/' },
 *     { label: 'Products', href: '/products' },
 *     { label: 'Electronics', href: '/products/electronics' },
 *     { label: 'Laptops' }
 *   ]}
 *   maxItems={3}
 * />
 * ```
 */
export const Breadcrumb = React.forwardRef<HTMLElement, BreadcrumbProps>(
  (
    {
      items,
      separator = (
        <svg
          width="16"
          height="16"
          viewBox="0 0 16 16"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
          className="text-grey-400 dark:text-grey-500"
        >
          <path
            d="M6 12L10 8L6 4"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      ),
      maxItems,
      size = 'medium',
      showHomeIcon = false,
      className,
    },
    ref
  ) => {
    const [expanded, setExpanded] = React.useState(false);

    // Collapse items if maxItems is set
    const displayItems = React.useMemo(() => {
      if (!maxItems || items.length <= maxItems || expanded) {
        return items;
      }

      // Show first item, ellipsis, and last (maxItems - 2) items
      const itemsToShow = maxItems - 2; // Account for first item and ellipsis
      return [
        items[0],
        {
          label: '...',
          onClick: () => setExpanded(true),
        } as BreadcrumbItemType,
        ...items.slice(items.length - itemsToShow),
      ];
    }, [items, maxItems, expanded]);

    // Size-based classes
    const sizeClasses = {
      small: {
        text: 'text-xs',
        padding: 'px-2 py-1',
        gap: 'gap-1',
        icon: 'w-3 h-3',
      },
      medium: {
        text: 'text-sm',
        padding: 'px-2 py-1',
        gap: 'gap-2',
        icon: 'w-4 h-4',
      },
      large: {
        text: 'text-base',
        padding: 'px-3 py-1.5',
        gap: 'gap-2.5',
        icon: 'w-5 h-5',
      },
    };

    const currentSize = sizeClasses[size];

    // Home icon SVG
    const homeIcon = (
      <svg
        width="16"
        height="16"
        viewBox="0 0 16 16"
        fill="none"
        xmlns="http://www.w3.org/2000/svg"
        className={cn(currentSize.icon, 'flex-shrink-0')}
      >
        <path
          d="M2 6L8 2L14 6V13C14 13.5304 13.7893 14.0391 13.4142 14.4142C13.0391 14.7893 12.5304 15 12 15H4C3.46957 15 2.96086 14.7893 2.58579 14.4142C2.21071 14.0391 2 13.5304 2 13V6Z"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M6 15V8H10V15"
          stroke="currentColor"
          strokeWidth="1.5"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
    );

    return (
      <nav ref={ref} aria-label="Breadcrumb" className={cn('w-full', className)}>
        <ol className={cn('flex items-center flex-wrap', currentSize.gap, currentSize.text)}>
          {displayItems.map((item, index) => {
            const isLast = index === displayItems.length - 1;
            const isLink = (item.href || item.onClick) && !item.disabled && !isLast;
            const isFirst = index === 0;

            return (
              <li key={`${item.label}-${index}`} className="flex items-center gap-2">
                {/* Item content */}
                <div className="flex items-center gap-1.5">
                  {/* Icon */}
                  {isFirst && showHomeIcon ? (
                    homeIcon
                  ) : item.icon ? (
                    <span className={cn(currentSize.icon, 'flex-shrink-0')}>{item.icon}</span>
                  ) : null}

                  {/* Link or text */}
                  {isLink ? (
                    item.href ? (
                      <a
                        href={item.href}
                        className={cn(
                          'text-primary-600 dark:text-primary-400',
                          'hover:text-primary-700 dark:hover:text-primary-300',
                          'hover:underline',
                          'transition-colors',
                          currentSize.padding,
                          'rounded-md',
                          'focus:outline-none focus:ring-2 focus:ring-primary-500/20'
                        )}
                      >
                        {item.label}
                      </a>
                    ) : (
                      <button
                        type="button"
                        onClick={item.onClick}
                        disabled={item.disabled}
                        className={cn(
                          'text-primary-600 dark:text-primary-400',
                          'hover:text-primary-700 dark:hover:text-primary-300',
                          'hover:underline',
                          'transition-colors',
                          currentSize.padding,
                          'rounded-md',
                          'focus:outline-none focus:ring-2 focus:ring-primary-500/20',
                          'disabled:opacity-50 disabled:cursor-not-allowed'
                        )}
                      >
                        {item.label}
                      </button>
                    )
                  ) : (
                    <span
                      className={cn(
                        isLast
                          ? 'text-grey-900 dark:text-grey-100 font-medium'
                          : 'text-grey-600 dark:text-grey-400',
                        item.disabled && 'opacity-50',
                        currentSize.padding
                      )}
                      aria-current={isLast ? 'page' : undefined}
                    >
                      {item.label}
                    </span>
                  )}
                </div>

                {/* Separator */}
                {!isLast && (
                  <span
                    className="flex-shrink-0 select-none"
                    aria-hidden="true"
                    role="presentation"
                  >
                    {separator}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      </nav>
    );
  }
);

Breadcrumb.displayName = 'Breadcrumb';
