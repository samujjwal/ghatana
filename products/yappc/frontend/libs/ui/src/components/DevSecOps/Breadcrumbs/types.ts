/**
 * Breadcrumbs Component Types
 *
 * @module DevSecOps/Breadcrumbs/types
 */

/**
 * Breadcrumb item configuration
 *
 * @property label - Display text for the breadcrumb
 * @property href - Optional URL for navigation
 * @property onClick - Optional click handler
 */
export interface BreadcrumbItem {
  /** Display text */
  label: string;

  /** Navigation URL */
  href?: string;

  /** Click handler */
  onClick?: () => void;
}

/**
 * Props for the Breadcrumbs component
 *
 * @property items - Array of breadcrumb items
 * @property maxItems - Maximum number of items to display before collapsing
 * @property separator - Custom separator character
 *
 * @example
 * ```typescript
 * <Breadcrumbs
 *   items={[
 *     { label: 'Home', href: '/' },
 *     { label: 'DevSecOps', href: '/devsecops' },
 *     { label: 'Planning Phase' }
 *   ]}
 * />
 * ```
 */
export interface BreadcrumbsProps {
  /** Breadcrumb items */
  items: BreadcrumbItem[];

  /** Maximum items before collapse */
  maxItems?: number;

  /** Custom separator */
  separator?: string | React.ReactNode;
}
