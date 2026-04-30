/**
 * Breadcrumbs component for navigation hierarchy
 *
 * Provides breadcrumb navigation with ARIA support and keyboard accessibility.
 *
 * Designed for cross-product reuse. Can be extracted to @ghatana/navigation-ui/**
 * Breadcrumbs — hierarchical navigation with tenant context.
 *
 * @doc.type component
 * @doc.purpose Breadcrumb navigation component
 * @doc.layer frontend
 */
/* eslint-disable ghatana/no-duplicate-utilities */
import React from 'react';
import { ChevronRight, Home } from 'lucide-react';

/**
 * Breadcrumb item
 */
export interface BreadcrumbItem {
  label: string;
  href?: string;
  onClick?: () => void;
}

/**
 * Breadcrumbs component props
 */
interface BreadcrumbsProps {
  /**
   * Breadcrumb items
   */
  items: BreadcrumbItem[];
  /**
   * Optional: Custom home label
   */
  homeLabel?: string;
  /**
   * Optional: Show home icon
   */
  showHome?: boolean;
  /**
   * Optional: Custom separator
   */
  separator?: React.ReactNode;
  className?: string;
}

/**
 * Breadcrumbs component
 *
 * Displays a breadcrumb navigation trail with ARIA landmarks
 * and keyboard navigation support.
 */
export const Breadcrumbs: React.FC<BreadcrumbsProps> = ({
  items,
  homeLabel = 'Home',
  showHome = true,
  separator = <ChevronRight className="h-4 w-4" />,
  className = '',
}) => {
  const allItems = showHome
    ? [{ label: homeLabel, href: '/' }, ...items]
    : items;

  return (
    <nav
      aria-label="Breadcrumb"
      className={cn('flex items-center gap-2 text-sm', className)}
    >
      <ol className="flex items-center gap-2" role="list">
        {allItems.map((item, index) => {
          const isLast = index === allItems.length - 1;
          
          return (
            <li key={index} className="flex items-center gap-2" role="listitem">
              {index > 0 && (
                <span
                  className="text-gray-400"
                  aria-hidden="true"
                >
                  {separator}
                </span>
              )}
              
              {isLast ? (
                <span
                  className="font-medium text-gray-900 dark:text-white"
                  aria-current="page"
                >
                  {showHome && index === 0 ? <Home className="h-4 w-4 inline" /> : null}
                  {showHome && index === 0 ? ' ' : ''}
                  {item.label}
                </span>
              ) : (
                <a
                  href={item.href}
                  onClick={item.onClick}
                  className="text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors focus:outline-none focus:ring-2 focus:ring-primary-500 rounded"
                >
                  {showHome && index === 0 ? <Home className="h-4 w-4 inline" /> : null}
                  {showHome && index === 0 ? ' ' : ''}
                  {item.label}
                </a>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
};

/**
 * Utility function to combine class names
 */
function cn(...classes: (string | undefined | boolean)[]): string | undefined {
  const filtered = classes.filter(Boolean) as string[];
  return filtered.length > 0 ? filtered.join(' ') : undefined;
}
