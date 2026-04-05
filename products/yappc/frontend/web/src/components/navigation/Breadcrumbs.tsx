/**
 * Breadcrumbs Component
 *
 * Displays navigation breadcrumbs sourced from the breadcrumb Jotai atom.
 * Supports optional home button, truncation, and custom styling.
 *
 * @doc.type component
 * @doc.purpose Navigation breadcrumb trail with Jotai state integration
 * @doc.layer product
 * @doc.pattern Navigation Component
 */

import { useCallback } from 'react';
import { useNavigate } from 'react-router';
import { useAtomValue } from 'jotai';

import { breadcrumbItemsAtom } from '../../state/atoms/breadcrumbAtom';

export interface BreadcrumbsProps {
  /** Whether to show the home icon at the start */
  showHome?: boolean;
  /** Maximum items before truncating. Shows first + '...' + last (maxItems-1). */
  maxItems?: number;
  /** Additional CSS class applied to the nav container */
  className?: string;
}

interface BreadcrumbEntry {
  id?: string;
  label: string;
  href?: string;
  icon?: string;
  disabled?: boolean;
}

function buildVisibleItems(
  items: BreadcrumbEntry[],
  maxItems: number | undefined,
): Array<BreadcrumbEntry | '...'> {
  if (maxItems === undefined || items.length <= maxItems) {
    return items;
  }
  const tail = items.slice(-(maxItems - 1));
  return [items[0] as BreadcrumbEntry, '...', ...tail];
}

export function Breadcrumbs({ showHome = false, maxItems, className }: BreadcrumbsProps) {
  const navigate = useNavigate();
  const breadcrumbs = useAtomValue(breadcrumbItemsAtom) as BreadcrumbEntry[];

  const handleHomeClick = useCallback(() => {
    navigate('/dashboard');
  }, [navigate]);

  const handleItemClick = useCallback(
    (href: string | undefined) => {
      if (href !== undefined) {
        navigate(href);
      }
    },
    [navigate],
  );

  if (breadcrumbs.length === 0 && !showHome) {
    return null;
  }

  const visibleItems = buildVisibleItems(breadcrumbs, maxItems);

  return (
    <nav aria-label="Breadcrumb" className={className}>
      {showHome && (
        <button
          aria-label="Home"
          type="button"
          onClick={handleHomeClick}
          className="hover:text-gray-900"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="currentColor"
            aria-hidden="true"
          >
            <path d="M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z" />
          </svg>
        </button>
      )}
      {visibleItems.map((item, index) => {
        if (item === '...') {
          return (
            <span key="ellipsis" aria-hidden="true">
              ...
            </span>
          );
        }

        const isLast = index === visibleItems.length - 1;
        const itemKey = item.id !== undefined ? item.id : `crumb-${index}`;

        if (isLast) {
          return (
            <button
              key={itemKey}
              type="button"
              disabled
              aria-current="page"
              className="font-medium"
            >
              {item.label}
            </button>
          );
        }

        return (
          <button
            key={itemKey}
            type="button"
            onClick={() => handleItemClick(item.href)}
            className="hover:text-gray-900"
          >
            {item.label}
          </button>
        );
      })}
    </nav>
  );
}
