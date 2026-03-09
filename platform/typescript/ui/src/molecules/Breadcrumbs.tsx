import * as React from 'react';

export interface BreadcrumbsProps extends React.HTMLAttributes<HTMLElement> {
  /** Separator between breadcrumb items @default '/' */
  separator?: React.ReactNode;
  /** Max items before showing ellipsis @default 8 */
  maxItems?: number;
  /** Expand label for collapsed items */
  expandText?: string;
  /** Additional CSS classes */
  className?: string;
  /** Breadcrumb children (links, text nodes, etc.) */
  children: React.ReactNode;
}

/**
 * Breadcrumbs navigation component — MUI-compatible children-based API.
 * Uses Tailwind CSS for styling.
 *
 * @example
 * ```tsx
 * <Breadcrumbs>
 *   <Link href="/">Home</Link>
 *   <Link href="/projects">Projects</Link>
 *   <span>Current Page</span>
 * </Breadcrumbs>
 * ```
 */
export const Breadcrumbs = React.forwardRef<HTMLElement, BreadcrumbsProps>(
  (
    {
      separator = '/',
      maxItems = 8,
      expandText = '…',
      className,
      children,
      ...rest
    },
    ref,
  ) => {
    const [expanded, setExpanded] = React.useState(false);
    const items = React.Children.toArray(children).filter(Boolean);

    let visibleItems = items;
    if (!expanded && items.length > maxItems) {
      const first = items.slice(0, 1);
      const last = items.slice(-(maxItems - 1));
      visibleItems = [
        ...first,
        <button
          key="__breadcrumb-expand__"
          type="button"
          onClick={() => setExpanded(true)}
          className="cursor-pointer text-neutral-500 hover:text-neutral-700 dark:text-neutral-400 dark:hover:text-neutral-200"
          aria-label={expandText}
        >
          {expandText}
        </button>,
        ...last,
      ];
    }

    return (
      <nav ref={ref} aria-label="breadcrumb" className={className} {...rest}>
        <ol className="flex flex-wrap items-center gap-1 text-sm text-neutral-600 dark:text-neutral-300">
          {visibleItems.map((item, index) => (
            <li key={index} className="inline-flex items-center gap-1">
              {index > 0 && (
                <span
                  aria-hidden="true"
                  className="mx-1 select-none text-neutral-400 dark:text-neutral-500"
                >
                  {separator}
                </span>
              )}
              <span className={index === visibleItems.length - 1 ? 'font-medium text-neutral-900 dark:text-neutral-100' : ''}>
                {item}
              </span>
            </li>
          ))}
        </ol>
      </nav>
    );
  },
);

Breadcrumbs.displayName = 'Breadcrumbs';
